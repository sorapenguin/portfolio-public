from unittest.mock import patch

from django.test import TestCase, Client
from django.contrib.auth.models import User
from django.utils import timezone

from quiz.models import Question, UserProfile, QuizSession, Answer, XPLog
from quiz.services import (
    get_level_info,
    get_or_create_profile,
    add_xp,
    deduct_stamina,
    create_quiz_session,
    get_quiz_session,
    STAMINA_MAX,
    LEVEL_THRESHOLDS,
)


def make_question(category="python", text="Q?", choices=None, answer="A"):
    if choices is None:
        choices = ["A", "B", "C", "D"]
    return Question.objects.create(
        category=category, text=text, choices=choices, answer=answer
    )


class TestGetLevelInfo(TestCase):
    def test_xp_zero_is_level_1(self):
        info = get_level_info(0)
        self.assertEqual(info["level"], 1)
        self.assertEqual(info["progress_pct"], 0)

    def test_xp_at_threshold_advances_level(self):
        info = get_level_info(LEVEL_THRESHOLDS[1])  # 100 XP → level 2
        self.assertEqual(info["level"], 2)

    def test_max_level_does_not_exceed_cap(self):
        info = get_level_info(9999)
        self.assertEqual(info["level"], len(LEVEL_THRESHOLDS))
        self.assertTrue(info["is_max_level"])

    def test_progress_pct_midpoint(self):
        # level 1: 0〜100 XP、50 XP なら 50%
        info = get_level_info(50)
        self.assertEqual(info["level"], 1)
        self.assertEqual(info["progress_pct"], 50)


class TestStaminaService(TestCase):
    def setUp(self):
        self.user = User.objects.create_user("stamina_user", password="pass")

    def test_deduct_stamina_succeeds_with_enough(self):
        profile = get_or_create_profile(self.user)
        profile.stamina = 10
        profile.save()
        result = deduct_stamina(self.user, 5)
        self.assertTrue(result)
        profile.refresh_from_db()
        self.assertEqual(profile.stamina, 5)

    def test_deduct_stamina_fails_when_insufficient(self):
        profile = get_or_create_profile(self.user)
        profile.stamina = 3
        profile.save()
        result = deduct_stamina(self.user, 5)
        self.assertFalse(result)
        profile.refresh_from_db()
        self.assertEqual(profile.stamina, 3)  # 変化なし

    def test_deduct_stamina_clamps_to_zero(self):
        profile = get_or_create_profile(self.user)
        profile.stamina = 5
        profile.save()
        result = deduct_stamina(self.user, 5)
        self.assertTrue(result)
        profile.refresh_from_db()
        self.assertGreaterEqual(profile.stamina, 0)


class TestAddXp(TestCase):
    def setUp(self):
        self.user = User.objects.create_user("xp_user", password="pass")

    def test_add_xp_increments_profile(self):
        profile = get_or_create_profile(self.user)
        initial_xp = profile.xp
        add_xp(self.user, 10)
        profile.refresh_from_db()
        self.assertEqual(profile.xp, initial_xp + 10)

    def test_add_xp_creates_xp_log(self):
        add_xp(self.user, 20)
        log = XPLog.objects.filter(user=self.user).first()
        self.assertIsNotNone(log)
        self.assertGreaterEqual(log.amount, 20)

    def test_add_xp_level_up_grants_gem(self):
        # 0 XP から 100 XP 追加するとレベル2に上がりジェムが付く
        profile = get_or_create_profile(self.user)
        profile.xp = 0
        profile.save()
        initial_gems = profile.gems
        add_xp(self.user, LEVEL_THRESHOLDS[1])  # level 2 のしきい値分追加
        profile.refresh_from_db()
        self.assertGreater(profile.gems, initial_gems)


class TestLoginView(TestCase):
    def test_login_page_renders(self):
        response = self.client.get("/login/")
        self.assertEqual(response.status_code, 200)

    def test_portfolio_login_redirects_to_top(self):
        response = self.client.post("/login/", {"login_type": "portfolio"})
        self.assertRedirects(response, "/", fetch_redirect_response=False)

    def test_portfolio_user_is_created(self):
        self.client.post("/login/", {"login_type": "portfolio"})
        self.assertTrue(User.objects.filter(username="portfolio").exists())

    def test_regular_login_success(self):
        User.objects.create_user("alice", password="secret123")
        response = self.client.post("/login/", {
            "login_type": "regular",
            "username": "alice",
            "password": "secret123",
        })
        self.assertRedirects(response, "/", fetch_redirect_response=False)

    def test_regular_login_wrong_password(self):
        User.objects.create_user("bob", password="correct")
        response = self.client.post("/login/", {
            "login_type": "regular",
            "username": "bob",
            "password": "wrong",
        })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "パスワードが違います")

    def test_logout_redirects_to_login(self):
        User.objects.create_user("charlie", password="pass")
        self.client.login(username="charlie", password="pass")
        response = self.client.get("/logout/")
        self.assertRedirects(response, "/login/", fetch_redirect_response=False)


class TestQuizFlow(TestCase):
    """ログイン → カテゴリ選択 → 問題回答 → 結果 の一連のフロー"""

    def setUp(self):
        self.user = User.objects.create_user("quizuser", password="pass")
        self.client.login(username="quizuser", password="pass")
        self.profile = get_or_create_profile(self.user)
        self.profile.stamina = STAMINA_MAX
        self.profile.save()

        self.q = make_question(
            category="python",
            text="Pythonのリストを作る構文は？",
            choices=["[]", "{}", "()", "<>"],
            answer="[]",
        )

    def _start_quiz(self):
        """クイズを開始して quiz_id と question_id を返す。"""
        response = self.client.get(
            "/python/start/", {"count": "1", "filter": "未出題"}
        )
        self.assertEqual(response.status_code, 302)
        location = response["Location"]
        # /question/123/?quiz_id=xxxx
        parts = location.split("quiz_id=")
        quiz_id = parts[1]
        q_id = int(location.split("/question/")[1].split("/")[0])
        return quiz_id, q_id

    def test_category_index_renders(self):
        response = self.client.get("/python/")
        self.assertEqual(response.status_code, 200)

    def test_start_creates_session(self):
        quiz_id, _ = self._start_quiz()
        session = get_quiz_session(quiz_id)
        self.assertIsNotNone(session)
        self.assertEqual(int(session["current_index"]), 0)

    def test_start_deducts_stamina(self):
        self._start_quiz()
        self.profile.refresh_from_db()
        self.assertLess(self.profile.stamina, STAMINA_MAX)

    def test_question_page_renders(self):
        quiz_id, q_id = self._start_quiz()
        response = self.client.get(f"/question/{q_id}/", {"quiz_id": quiz_id})
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, self.q.text)

    def test_answer_check_shows_correct_result(self):
        quiz_id, q_id = self._start_quiz()
        response = self.client.post("/answer/", {
            "quiz_id": quiz_id,
            "question_id": q_id,
            "answer": "[]",
            "action": "check",
        })
        self.assertEqual(response.status_code, 200)

    def test_answer_next_correct_adds_xp(self):
        quiz_id, q_id = self._start_quiz()
        self.profile.refresh_from_db()
        xp_before = self.profile.xp
        self.client.post("/answer/", {
            "quiz_id": quiz_id,
            "question_id": q_id,
            "answer": "[]",   # 正解
            "action": "next",
        })
        self.profile.refresh_from_db()
        self.assertGreater(self.profile.xp, xp_before)

    def test_answer_next_wrong_no_xp(self):
        quiz_id, q_id = self._start_quiz()
        self.profile.refresh_from_db()
        xp_before = self.profile.xp
        self.client.post("/answer/", {
            "quiz_id": quiz_id,
            "question_id": q_id,
            "answer": "{}",   # 不正解
            "action": "next",
        })
        self.profile.refresh_from_db()
        self.assertEqual(self.profile.xp, xp_before)

    def test_answer_next_final_question_redirects_to_result(self):
        """1問クイズで次へ進むと /result/ にリダイレクトされる。"""
        quiz_id, q_id = self._start_quiz()
        response = self.client.post("/answer/", {
            "quiz_id": quiz_id,
            "question_id": q_id,
            "answer": "[]",
            "action": "next",
        })
        self.assertRedirects(
            response,
            f"/result/?quiz_id={quiz_id}",
            fetch_redirect_response=False,
        )

    def test_result_page_renders_with_score(self):
        quiz_id, q_id = self._start_quiz()
        self.client.post("/answer/", {
            "quiz_id": quiz_id,
            "question_id": q_id,
            "answer": "[]",
            "action": "next",
        })
        response = self.client.get("/result/", {"quiz_id": quiz_id})
        self.assertEqual(response.status_code, 200)

    def test_no_stamina_redirects_gracefully(self):
        self.profile.stamina = 0
        self.profile.save()
        response = self.client.get(
            "/python/start/", {"count": "1", "filter": "未出題"}
        )
        self.assertRedirects(
            response,
            "/python/?no_stamina=1",
            fetch_redirect_response=False,
        )


class TestGamification(TestCase):
    def setUp(self):
        self.user = User.objects.create_user("gemuser", password="pass")
        self.client.login(username="gemuser", password="pass")
        self.profile = get_or_create_profile(self.user)

    def test_claim_mini_gems_first_time(self):
        response = self.client.post("/mini-gem/claim/")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["ok"])
        self.assertEqual(data["mini_gems"], 5)

    def test_claim_mini_gems_too_soon(self):
        self.client.post("/mini-gem/claim/")  # 1回目
        response = self.client.post("/mini-gem/claim/")  # すぐ2回目
        data = response.json()
        self.assertFalse(data["ok"])
        self.assertIn("seconds_left", data)

    def test_exchange_mini_gems_insufficient(self):
        response = self.client.post("/mini-gem/exchange/")
        data = response.json()
        self.assertFalse(data["ok"])

    def test_exchange_mini_gems_success(self):
        UserProfile.objects.filter(user=self.user).update(mini_gems=100)
        response = self.client.post("/mini-gem/exchange/")
        data = response.json()
        self.assertTrue(data["ok"])
        self.profile.refresh_from_db()
        self.assertEqual(self.profile.mini_gems, 0)
        self.assertEqual(self.profile.gems, 1)

    def test_purchase_confirm_blocked_for_regular_user(self):
        # 一般ユーザーは can_purchase=False なので拒否される
        response = self.client.post("/purchase/confirm/", {"plan_type": "gem1"})
        data = response.json()
        self.assertFalse(data["ok"])
        self.assertEqual(data["code"], "not_allowed")

    def test_purchase_confirm_allowed_for_portfolio_user(self):
        UserProfile.objects.filter(user=self.user).update(can_purchase=True, gems=0)
        response = self.client.post("/purchase/confirm/", {"plan_type": "gem1"})
        data = response.json()
        self.assertTrue(data["ok"])
        self.profile.refresh_from_db()
        self.assertEqual(self.profile.gems, 1)


class TestRegistration(TestCase):
    def test_register_page_renders(self):
        response = self.client.get("/register/")
        self.assertEqual(response.status_code, 200)

    def test_register_sets_session_and_redirects_to_verify(self):
        with patch("quiz.views._verify_recaptcha", return_value=True):
            response = self.client.post("/register/", {
                "username": "newuser",
                "email": "new@example.com",
                "password": "strongpass1",
                "password2": "strongpass1",
                "g-recaptcha-response": "dummy",
            })
        self.assertRedirects(response, "/register/verify/", fetch_redirect_response=False)

    def test_register_duplicate_username_shows_error(self):
        User.objects.create_user("existing", email="a@b.com", password="pass")
        response = self.client.post("/register/", {
            "username": "existing",
            "email": "other@example.com",
            "password": "strongpass1",
            "password2": "strongpass1",
            "g-recaptcha-response": "dummy",
        })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "すでに使われています")

    def test_register_mismatched_passwords_shows_error(self):
        response = self.client.post("/register/", {
            "username": "newuser2",
            "email": "new2@example.com",
            "password": "pass1234",
            "password2": "different",
            "g-recaptcha-response": "dummy",
        })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "パスワードが一致しません")
