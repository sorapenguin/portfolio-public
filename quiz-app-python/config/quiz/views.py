from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import login, logout
from django.contrib.auth.decorators import login_required
from django.contrib.auth.models import User
from django.conf import settings
from functools import wraps
from .services import (
    get_question,
    save_answer,
    get_score,
    get_answers_by_quiz,
    create_quiz_session,
    get_quiz_session,
    update_quiz_session,
    get_active_session_for_user,
    get_completed_sessions_for_user,
    get_last_completed_session,
    get_question_status_counts,
    get_filtered_question_ids,
    get_unlock_info,
    spend_gem_unlock,
    spend_gem_stamina,
    deduct_stamina,
    recover_stamina,
    add_xp,
    get_or_create_profile,
    XP_PER_CORRECT,
    XP_TWO_CHOICE,
    XP_PER_AUTOPLAY,
)
import random

PORTFOLIO_USERNAME = "portfolio"
PORTFOLIO_PREMIUM_USERNAME = "Premiumuser"
PORTFOLIO_PASSWORD = ""

COUNT_CHOICES = [
    ("1",   "1問"),
    ("3",   "3問"),
    ("5",   "5問"),
    ("10",  "10問"),
    ("all", "ALL"),
]
DEFAULT_COUNT = "10"

FILTER_CHOICES = [
    ("未出題", "未出題"),
    ("ミス",   "ミス"),
    ("ヒット", "ヒット"),
    ("コンボ", "コンボ"),
]
DEFAULT_FILTERS = ["未出題", "ミス"]


def login_view(request):
    if request.user.is_authenticated:
        return redirect("/")
    error = None
    if request.method == "POST":
        login_type = request.POST.get("login_type", "portfolio")

        if login_type == "portfolio":
            user, _ = User.objects.get_or_create(username=PORTFOLIO_USERNAME)
            if not user.has_usable_password():
                user.set_password(PORTFOLIO_PASSWORD)
                user.save()
            from .models import UserProfile
            UserProfile.objects.filter(user=user).update(can_purchase=True)
            login(request, user)
            active = get_active_session_for_user(user)
            if active:
                return redirect(
                    f"/question/{active['current_question_id']}/?quiz_id={active['quiz_id']}"
                )
            return redirect("/")

        elif login_type == "portfolio_premium":
            from .models import UserProfile
            user, created = User.objects.get_or_create(username=PORTFOLIO_PREMIUM_USERNAME)
            if created or not user.has_usable_password():
                user.set_password(PORTFOLIO_PASSWORD)
                user.save()
            profile = get_or_create_profile(user)
            if profile.plan != 'lifetime':
                UserProfile.objects.filter(user=user).update(plan='lifetime')
            UserProfile.objects.filter(user=user).update(can_purchase=True)
            login(request, user)
            active = get_active_session_for_user(user)
            if active:
                return redirect(
                    f"/question/{active['current_question_id']}/?quiz_id={active['quiz_id']}"
                )
            return redirect("/")

        elif login_type == "regular":
            from django.contrib.auth import authenticate as auth_authenticate
            username = request.POST.get("username", "").strip()
            password = request.POST.get("password", "")
            user = auth_authenticate(request, username=username, password=password)
            if user:
                login(request, user)
                active = get_active_session_for_user(user)
                if active:
                    return redirect(
                        f"/question/{active['current_question_id']}/?quiz_id={active['quiz_id']}"
                    )
                return redirect("/")
            error = "ユーザーIDまたはパスワードが違います"

    return render(request, "quiz/login.html", {"error": error})


def _verify_recaptcha(response_token):
    """Google reCAPTCHA v2 のトークンを検証する。
    DEBUG=True の場合はトークンの存在確認のみ（Google API 呼び出しなし）。
    """
    if settings.DEBUG:
        return bool(response_token)

    import urllib.parse
    import urllib.request as urlreq
    import json as _json
    data = urllib.parse.urlencode({
        'secret': settings.RECAPTCHA_PRIVATE_KEY,
        'response': response_token,
    }).encode()
    try:
        with urlreq.urlopen('https://www.google.com/recaptcha/api/siteverify',
                            data=data, timeout=5) as resp:
            result = _json.loads(resp.read().decode())
        return result.get('success', False)
    except Exception:
        return True  # ネットワーク障害時はブロックしない


def register_view(request):
    if request.user.is_authenticated:
        return redirect("/")
    errors = []
    if request.method == "POST":
        # reCAPTCHA 検証
        recaptcha_token = request.POST.get('g-recaptcha-response', '')
        if not recaptcha_token:
            errors.append("reCAPTCHA を完了してください")
        elif not _verify_recaptcha(recaptcha_token):
            errors.append("reCAPTCHA の確認に失敗しました。もう一度お試しください。")

        username  = request.POST.get("username", "").strip()
        email     = request.POST.get("email", "").strip()
        password  = request.POST.get("password", "")
        password2 = request.POST.get("password2", "")

        if not username:
            errors.append("ユーザーIDを入力してください")
        elif User.objects.filter(username=username).exists():
            errors.append("このユーザーIDはすでに使われています")
        if not email:
            errors.append("メールアドレスを入力してください")
        elif User.objects.filter(email=email).exists():
            errors.append("このメールアドレスはすでに登録されています")
        if len(password) < 8:
            errors.append("パスワードは8文字以上にしてください")
        elif password != password2:
            errors.append("パスワードが一致しません")

        if not errors:
            import random, time
            from django.contrib.auth.hashers import make_password

            code = str(random.randint(100000, 999999))
            request.session['reg_username'] = username
            request.session['reg_email']    = email
            request.session['reg_password'] = make_password(password)
            request.session['reg_code']     = code
            request.session['reg_expires']  = time.time() + 600  # 10分
            return redirect("/register/verify/")

    return render(request, "quiz/register.html", {
        "errors": errors,
        "recaptcha_public_key": settings.RECAPTCHA_PUBLIC_KEY,
    })


def register_verify_view(request):
    if request.user.is_authenticated:
        return redirect("/")
    if not request.session.get('reg_code'):
        return redirect("/register/")

    error = None
    if request.method == "POST":
        import time
        entered = request.POST.get("code", "").strip()

        if time.time() > request.session.get('reg_expires', 0):
            for key in ('reg_username', 'reg_email', 'reg_password', 'reg_code', 'reg_expires'):
                request.session.pop(key, None)
            return redirect("/register/?expired=1")

        if entered != request.session.get('reg_code'):
            error = "認証コードが違います。もう一度確認してください。"
        else:
            username      = request.session.pop('reg_username')
            email         = request.session.pop('reg_email')
            password_hash = request.session.pop('reg_password')
            request.session.pop('reg_code', None)
            request.session.pop('reg_expires', None)

            user = User(username=username, email=email, password=password_hash)
            user.save()
            login(request, user)
            return redirect("/")

    return render(request, "quiz/register_verify.html", {
        "email": request.session.get('reg_email', ''),
        "error": error,
        "demo_code": request.session.get('reg_code'),
    })


def logout_view(request):
    if request.user.is_authenticated and request.user.username == PORTFOLIO_USERNAME:
        from .models import UserProfile, PLAN_FREE
        UserProfile.objects.filter(user=request.user).update(plan=PLAN_FREE)
    logout(request)
    return redirect("/login/")


def top_page(request):
    from .models import Question
    from django.utils import timezone
    predefined = list(settings.QUIZ_CATEGORIES)
    db_cats = list(Question.objects.values_list('category', flat=True).distinct())
    cats = predefined + [c for c in db_cats if c not in predefined]
    category_counts = [
        (cat, Question.objects.filter(category=cat).count())
        for cat in cats
    ]
    mini_gem_seconds_left = 0
    current_category = predefined[0] if predefined else 'python'
    if request.user.is_authenticated:
        profile = get_or_create_profile(request.user)
        if profile.mini_gems_last_claimed:
            elapsed = (timezone.now() - profile.mini_gems_last_claimed).total_seconds()
            mini_gem_seconds_left = max(0, int(3600 - elapsed))
        current_category = profile.last_category or current_category
    return render(request, 'quiz/top.html', {
        'category_counts': category_counts,
        'mini_gem_seconds_left': mini_gem_seconds_left,
        'current_category': current_category,
    })


@login_required
def claim_mini_gems_view(request):
    from django.http import JsonResponse
    from django.utils import timezone
    if request.method != 'POST':
        return JsonResponse({'error': 'POST required'}, status=405)
    profile = get_or_create_profile(request.user)
    now = timezone.now()
    if profile.mini_gems_last_claimed:
        elapsed = (now - profile.mini_gems_last_claimed).total_seconds()
        if elapsed < 3600:
            seconds_left = int(3600 - elapsed)
            return JsonResponse({'ok': False, 'seconds_left': seconds_left,
                                 'mini_gems': profile.mini_gems})
    from .models import UserProfile
    UserProfile.objects.filter(user=request.user).update(
        mini_gems=profile.mini_gems + 5,
        mini_gems_last_claimed=now,
    )
    profile.refresh_from_db()
    return JsonResponse({'ok': True, 'mini_gems': profile.mini_gems, 'seconds_left': 3600})


@login_required
def exchange_mini_gems_view(request):
    from django.http import JsonResponse
    if request.method != 'POST':
        return JsonResponse({'error': 'POST required'}, status=405)
    profile = get_or_create_profile(request.user)
    if profile.mini_gems < 100:
        return JsonResponse({'ok': False, 'message': 'ミニジェムが足りません（100個必要）'})
    from .models import UserProfile
    UserProfile.objects.filter(user=request.user).update(
        mini_gems=profile.mini_gems - 100,
        gems=profile.gems + 1,
    )
    profile.refresh_from_db()
    return JsonResponse({'ok': True, 'mini_gems': profile.mini_gems, 'gems': profile.gems})


@login_required
def purchase_confirm_view(request):
    from django.http import JsonResponse
    if request.method != 'POST':
        return JsonResponse({'error': 'POST required'}, status=405)
    plan_type = request.POST.get('plan_type', '')
    from .models import UserProfile, PLAN_LIMITED
    profile = get_or_create_profile(request.user)
    if not profile.can_purchase:
        return JsonResponse({
            'ok': False,
            'code': 'not_allowed',
            'message': '購入に失敗しました（個別ユーザーには現在対応していません）',
        })
    if plan_type == 'gem1':
        UserProfile.objects.filter(user=request.user).update(gems=profile.gems + 1)
        return JsonResponse({'ok': True, 'type': 'gem', 'gems': profile.gems + 1})
    elif plan_type == 'gem6':
        UserProfile.objects.filter(user=request.user).update(gems=profile.gems + 6)
        return JsonResponse({'ok': True, 'type': 'gem', 'gems': profile.gems + 6})
    elif plan_type == 'monthly':
        from django.utils import timezone
        import datetime
        expires = timezone.now() + datetime.timedelta(days=30)
        UserProfile.objects.filter(user=request.user).update(plan=PLAN_LIMITED, plan_expires_at=expires)
        return JsonResponse({'ok': True, 'type': 'monthly'})
    return JsonResponse({'ok': False, 'message': '不明なプランです'})


def index(request):
    return redirect('/top/')


def category_index(request, category):
    from .models import UserProfile
    if request.user.is_authenticated:
        UserProfile.objects.filter(user=request.user).update(last_category=category)

    active = None
    filter_choices_display = [(val, label, 0) for val, label in FILTER_CHOICES]

    if request.user.is_authenticated:
        active = get_active_session_for_user(request.user, category=category)
        counts = get_question_status_counts(request.user, category=category)
        filter_choices_display = [
            (val, label, counts.get(val, 0)) for val, label in FILTER_CHOICES
        ]

    autoplay_check_sec = 3
    autoplay_next_sec = 2
    autoplay_enabled = True
    unlock_info = None
    if request.user.is_authenticated:
        profile = get_or_create_profile(request.user)
        autoplay_check_sec = profile.autoplay_check_sec
        autoplay_next_sec = profile.autoplay_next_sec
        autoplay_enabled = profile.autoplay_enabled
        unlock_info = get_unlock_info(request.user, category)

    return render(request, 'quiz/index.html', {
        "current_category": category,
        "active_session": active,
        "count_choices": COUNT_CHOICES,
        "default_count": DEFAULT_COUNT,
        "filter_choices_display": filter_choices_display,
        "default_filters": DEFAULT_FILTERS,
        "no_questions": request.GET.get("no_questions") == "1",
        "no_stamina": request.GET.get("no_stamina") == "1",
        "no_prev_session": request.GET.get("no_prev_session") == "1",
        "no_wrong": request.GET.get("no_wrong") == "1",
        "wrong_autoplay": request.GET.get("wrong_autoplay") == "1",
        "autoplay_check_sec": autoplay_check_sec,
        "autoplay_next_sec": autoplay_next_sec,
        "autoplay_enabled": autoplay_enabled,
        "unlock_info": unlock_info,
        "voice_rate_choices": ["0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0"],
    })


_ELAPSED_MULTIPLIERS = {"min": 1, "hour": 60, "day": 1440}


def _parse_start_params(request):
    filters = request.GET.getlist("filter") or DEFAULT_FILTERS
    try:
        min_rate = max(0, min(100, int(request.GET.get("min_rate", 0))))
    except ValueError:
        min_rate = 0
    try:
        max_rate = max(0, min(100, int(request.GET.get("max_rate", 100))))
    except ValueError:
        max_rate = 100
    try:
        elapsed_value = max(0, int(request.GET.get("elapsed_value", 0)))
    except ValueError:
        elapsed_value = 0
    elapsed_unit = request.GET.get("elapsed_unit", "min")
    elapsed_minutes = elapsed_value * _ELAPSED_MULTIPLIERS.get(elapsed_unit, 1)
    count_str = request.GET.get("count", DEFAULT_COUNT)
    return filters, min_rate, max_rate, elapsed_minutes, count_str


@login_required
def category_start(request, category):
    mode = request.GET.get("mode", "new")
    filters, min_rate, max_rate, elapsed_minutes, count_str = _parse_start_params(request)

    if mode == "same":
        last = get_last_completed_session(request.user, category=category)
        if last is None:
            return redirect(f"/{category}/?no_prev_session=1")
        question_ids = list(last.question_ids)
        random.shuffle(question_ids)

    elif mode == "wrong":
        from .models import Answer as AnswerModel
        last = get_last_completed_session(request.user, category=category)
        if last is None:
            return redirect(f"/{category}/?no_prev_session=1")
        if last.is_autoplay:
            return redirect(f"/{category}/?wrong_autoplay=1")
        wrong_ids = list(
            AnswerModel.objects.filter(
                quiz_id=last.quiz_id,
                is_correct=False,
                is_autoplay=False,
            ).values_list('question_id', flat=True).distinct()
        )
        if not wrong_ids:
            return redirect(f"/{category}/?no_wrong=1")
        question_ids = wrong_ids
        random.shuffle(question_ids)

    else:
        question_ids = get_filtered_question_ids(
            request.user, filters,
            min_rate=min_rate, max_rate=max_rate, elapsed_minutes=elapsed_minutes,
            category=category,
        )
        if not question_ids:
            return redirect(f"/{category}/?no_questions=1")
        random.shuffle(question_ids)
        if count_str != "all":
            try:
                question_ids = question_ids[:int(count_str)]
            except ValueError:
                pass

    if not deduct_stamina(request.user, len(question_ids)):
        return redirect(f"/{category}/?no_stamina=1")

    # 自動再生設定を GET パラメータから取得してプロフィールに保存
    profile = get_or_create_profile(request.user)
    is_autoplay = request.GET.get("autoplay") == "1"
    is_voice    = request.GET.get("voice") == "1"
    voice_rate  = request.GET.get("voice_rate", "1.0")
    try:
        check_sec = max(1, min(30, int(request.GET.get("check_sec", profile.autoplay_check_sec))))
    except ValueError:
        check_sec = profile.autoplay_check_sec
    try:
        next_sec = max(1, min(30, int(request.GET.get("next_sec", profile.autoplay_next_sec))))
    except ValueError:
        next_sec = profile.autoplay_next_sec

    from .models import UserProfile
    UserProfile.objects.filter(user=request.user).update(
        autoplay_enabled=is_autoplay,
        autoplay_check_sec=check_sec,
        autoplay_next_sec=next_sec,
    )
    request.session['voice_enabled'] = is_voice
    request.session['voice_rate']    = voice_rate

    quiz_id = create_quiz_session(question_ids, user=request.user, is_autoplay=is_autoplay)
    return redirect(f"/question/{question_ids[0]}/?quiz_id={quiz_id}")


@login_required
def start_quiz_page(request):
    profile = get_or_create_profile(request.user)
    qs = request.META.get('QUERY_STRING', '')
    url = f'/{profile.last_category}/start/'
    if qs:
        url += '?' + qs
    return redirect(url)


@login_required
def recover_stamina_view(request):
    if request.method == "POST":
        from django.http import JsonResponse
        from .services import STAMINA_MAX, STAMINA_RECOVERY_MINUTES
        from django.utils import timezone
        recover_stamina(request.user)
        if request.headers.get('X-Requested-With') == 'XMLHttpRequest':
            profile = get_or_create_profile(request.user)
            stamina_pct = round(profile.stamina / STAMINA_MAX * 100)
            if profile.stamina < STAMINA_MAX:
                elapsed_seconds = (timezone.now() - profile.stamina_updated_at).total_seconds()
                seconds_per_point = STAMINA_RECOVERY_MINUTES * 60
                deficit = STAMINA_MAX - profile.stamina
                remaining_for_next = seconds_per_point - (elapsed_seconds % seconds_per_point)
                total_seconds = remaining_for_next + (deficit - 1) * seconds_per_point
                full_recovery_min = max(1, -(-int(total_seconds) // 60))
            else:
                full_recovery_min = None
            return JsonResponse({
                'stamina': profile.stamina,
                'stamina_pct': stamina_pct,
                'stamina_max': STAMINA_MAX,
                'stamina_low': profile.stamina <= 20,
                'full_recovery_min': full_recovery_min,
            })
    return redirect("/")


@login_required
def use_gem_view(request):
    if request.method == "POST":
        action = request.POST.get("action")
        category = request.POST.get("category", "")
        if action == "unlock" and category:
            spend_gem_unlock(request.user, category)
        elif action == "stamina":
            spend_gem_stamina(request.user)
    return redirect(request.POST.get("next", "/"))


@login_required
def question_page(request, q_id):
    quiz_id = request.GET.get("quiz_id")
    data = get_quiz_session(quiz_id)

    current_index = int(data["current_index"])
    question = get_question(q_id)
    show_prev = current_index > 0
    is_autoplay = data.get("is_autoplay", False)

    profile = get_or_create_profile(request.user)
    is_voice   = request.session.get('voice_enabled', False)
    voice_rate = request.session.get('voice_rate', '1.0')
    return render(request, "quiz/question.html", {
        "question": question,
        "q_id": q_id,
        "quiz_id": quiz_id,
        "show_prev": show_prev,
        "is_autoplay": is_autoplay,
        "is_voice": is_voice,
        "voice_rate": voice_rate,
        "autoplay_check_sec": profile.autoplay_check_sec,
        "autoplay_next_sec": profile.autoplay_next_sec,
    })


@login_required
def answer_page(request):
    if request.method == "POST":
        quiz_id = request.POST.get("quiz_id")
        action = request.POST.get("action")

        data = get_quiz_session(quiz_id)
        question_ids = data["question_ids"]
        current_index = int(data["current_index"])

        question_id = int(request.POST.get("question_id"))
        user_answer = request.POST.get("answer")

        question = get_question(question_id)
        correct_answer = question.get("answer")

        if action == "check":
            is_correct = (user_answer == correct_answer)
            return render(request, "quiz/question.html", {
                "question": question,
                "q_id": question_id,
                "result": is_correct,
                "selected": user_answer,
                "show_prev": current_index > 0,
                "quiz_id": quiz_id,
            })

        if action == "next":
            is_session_autoplay = data.get("is_autoplay", False)
            two_choice_mode = request.POST.get("two_choice_mode") == "1"
            checked_before   = request.POST.get("checked_before") == "1"
            if is_session_autoplay:
                # 自動再生：未回答扱いで保存、1 XP 付与
                save_answer(quiz_id, request.user, question_id,
                            user_answer or "未回答", correct=False, is_autoplay=True)
                add_xp(request.user, XP_PER_AUTOPLAY)
            else:
                if not user_answer:
                    user_answer = "未回答"
                    is_correct = False
                else:
                    is_correct = (user_answer == correct_answer)
                save_answer(quiz_id, request.user, question_id, user_answer, is_correct)
                if is_correct and not checked_before:
                    xp = XP_TWO_CHOICE if two_choice_mode else XP_PER_CORRECT
                    add_xp(request.user, xp)
            current_index += 1

        if action == "prev":
            current_index -= 1

        update_quiz_session(quiz_id, question_ids, current_index)

        if current_index >= len(question_ids):
            return redirect(f"/result/?quiz_id={quiz_id}")

        next_question_id = question_ids[current_index]
        return redirect(f"/question/{next_question_id}/?quiz_id={quiz_id}")


@login_required
def history_page(request):
    quiz_id = request.GET.get("quiz_id")
    if quiz_id:
        profile = get_or_create_profile(request.user)
        if not profile.is_premium:
            category = request.GET.get("category", "")
            return redirect(f"/{category}/history/" if category else "/")
        category = request.GET.get("category", "")
        answers = get_answers_by_quiz(quiz_id)
        back_url = f"/{category}/history/" if category else "/"
        return render(request, "quiz/history.html", {
            "answers": answers,
            "quiz_id": quiz_id,
            "back_url": back_url,
        })
    # リスト表示はカテゴリ別 URL へリダイレクト
    profile = get_or_create_profile(request.user)
    return redirect(f"/{profile.last_category}/history/")


@login_required
def category_history(request, category):
    from django.core.paginator import Paginator
    sessions = get_completed_sessions_for_user(request.user, category=category)
    status_counts = get_question_status_counts(request.user, category=category)
    total_questions = sum(status_counts.values())
    paginator = Paginator(sessions, 10)
    page_number = request.GET.get("page", 1)
    page_obj = paginator.get_page(page_number)
    return render(request, "quiz/history_list.html", {
        "category": category,
        "sessions": page_obj,
        "page_obj": page_obj,
        "status_counts": status_counts,
        "total_questions": total_questions,
    })


@login_required
def result_page(request):
    quiz_id = request.GET.get("quiz_id")
    correct, total = get_score(quiz_id)
    session_data = get_quiz_session(quiz_id)
    is_autoplay = session_data.get("is_autoplay", False) if session_data else False
    return render(request, "quiz/result.html", {
        "correct": correct,
        "total": total,
        "quiz_id": quiz_id,
        "is_autoplay": is_autoplay,
    })


@login_required
def mypage_view(request):
    import json
    from datetime import date, timedelta
    from .models import XPLog, PLAN_FREE, PLAN_LIFETIME, PLAN_ADMIN_PLAN

    today = date.today()
    start = today - timedelta(weeks=53)
    xp_logs = XPLog.objects.filter(user=request.user, date__gte=start)
    xp_by_date = {str(log.date): log.amount for log in xp_logs}

    profile = get_or_create_profile(request.user)
    if profile.plan == PLAN_FREE:
        plan_label = '無料ユーザー'
        plan_badge = 'free'
    elif profile.plan in (PLAN_LIFETIME, PLAN_ADMIN_PLAN):
        plan_label = '有料ユーザー（永久会員）'
        plan_badge = 'lifetime'
    else:
        if profile.plan_expires_at:
            exp = profile.plan_expires_at
            plan_label = f'有料ユーザー（{exp.month}月{exp.day}日まで）'
        else:
            plan_label = '有料ユーザー'
        plan_badge = 'limited'

    return render(request, 'quiz/mypage.html', {
        'xp_by_date_json': json.dumps(xp_by_date),
        'plan_label': plan_label,
        'plan_badge': plan_badge,
    })


# ── 管理パネル ──────────────────────────────────────────────────────────────

def _admin_required(view_func):
    @wraps(view_func)
    def wrapper(request, *args, **kwargs):
        if not request.session.get('is_admin'):
            return redirect('/hidden-admin-HUE-node/login/')
        return view_func(request, *args, **kwargs)
    return wrapper


def admin_login_view(request):
    if request.session.get('is_admin'):
        return redirect('/hidden-admin-HUE-node/')
    error = None
    if request.method == 'POST':
        password = request.POST.get('password', '')
        if password == settings.ADMIN_PANEL_PASSWORD:
            request.session['is_admin'] = True
            return redirect('/hidden-admin-HUE-node/')
        error = 'パスワードが違います'
    return render(request, 'quiz/admin_login.html', {'error': error})


def admin_logout_view(request):
    request.session.pop('is_admin', None)
    return redirect('/hidden-admin-HUE-node/login/')


@_admin_required
def admin_dashboard(request):
    import json
    from .models import Question

    # JSON インポート処理
    import_error = None
    imported_count = None
    if request.method == 'POST' and request.FILES.get('json_file'):
        try:
            import datetime
            upload = request.FILES['json_file']
            raw = upload.read()
            original_name = upload.name
            data = json.loads(raw.decode('utf-8'))
            count = 0
            for item in data:
                _, created = Question.objects.get_or_create(
                    category=item.get('category', 'python'),
                    text=item['text'],
                    defaults={
                        'choices': item['choices'],
                        'answer': item['answer'],
                        'explanation': item.get('explanation', ''),
                    },
                )
                if created:
                    count += 1
            imported_count = count
            # インポート成功分があれば question_data フォルダに原本を保存
            if imported_count and imported_count > 0:
                from pathlib import Path as _Path
                save_dir = _Path(settings.BASE_DIR) / 'quiz' / 'question_data'
                save_dir.mkdir(exist_ok=True)
                ts = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
                safe_name = ''.join(c if c.isalnum() or c in '._-' else '_' for c in original_name)
                (save_dir / f'{ts}_{safe_name}').write_bytes(raw)
        except Exception as e:
            import_error = f'インポート失敗: {e}'

    # カテゴリ別件数
    predefined = list(settings.QUIZ_CATEGORIES)
    db_cats = list(Question.objects.values_list('category', flat=True).distinct())
    all_cats = predefined + [c for c in db_cats if c not in predefined]
    category_counts = [
        (cat, Question.objects.filter(category=cat).count())
        for cat in all_cats
        if Question.objects.filter(category=cat).exists()
    ]
    total = Question.objects.count()

    return render(request, 'quiz/admin_dashboard.html', {
        'category_counts': category_counts,
        'all_cats': all_cats,
        'total': total,
        'imported_count': imported_count,
        'import_error': import_error,
    })


@_admin_required
def admin_question_list(request, category=None):
    import urllib.parse
    from .models import Question
    from django.core.paginator import Paginator

    predefined = list(settings.QUIZ_CATEGORIES)
    db_cats = list(Question.objects.values_list('category', flat=True).distinct())
    all_cats = predefined + [c for c in db_cats if c not in predefined]

    selected_category = category or request.GET.get('category', '')
    search_text = request.GET.get('q', '').strip()

    qs = Question.objects.order_by('category', 'id')
    if selected_category:
        qs = qs.filter(category=selected_category)
    if search_text:
        qs = qs.filter(text__icontains=search_text)

    total = qs.count()
    paginator = Paginator(qs, 10)
    page_obj = paginator.get_page(request.GET.get('page', 1))

    if category:
        base_params = {'q': search_text} if search_text else {}
        qs_str = urllib.parse.urlencode(base_params)
        pagination_base = f'/hidden-admin-HUE-node/questions/{category}/?' + (qs_str + '&' if qs_str else '')
    else:
        base_params = {}
        if selected_category:
            base_params['category'] = selected_category
        if search_text:
            base_params['q'] = search_text
        qs_str = urllib.parse.urlencode(base_params)
        pagination_base = '/hidden-admin-HUE-node/questions/?' + (qs_str + '&' if qs_str else '')

    return render(request, 'quiz/admin_question_list.html', {
        'page_obj': page_obj,
        'all_categories': all_cats,
        'selected_category': selected_category,
        'search_text': search_text,
        'total': total,
        'path_category': category,
        'pagination_base': pagination_base,
    })


@_admin_required
def admin_question_edit(request, q_id):
    from .models import Question
    question = get_object_or_404(Question, id=q_id)

    if request.method == 'POST':
        question.category = request.POST.get('category', '').strip()
        question.text = request.POST.get('text', '').strip()
        choices_text = request.POST.get('choices_text', '')
        question.choices = [c.strip() for c in choices_text.splitlines() if c.strip()]
        question.answer = request.POST.get('answer', '').strip()
        question.explanation = request.POST.get('explanation', '').strip()
        question.save()
        next_url = request.POST.get('next') or f'/hidden-admin-HUE-node/questions/?category={question.category}'
        return redirect(next_url)

    choices_text = '\n'.join(question.choices) if isinstance(question.choices, list) else ''
    next_url = request.GET.get('next', f'/hidden-admin-HUE-node/questions/?category={question.category}')
    return render(request, 'quiz/admin_question_edit.html', {
        'question': question,
        'choices_text': choices_text,
        'next_url': next_url,
    })


@_admin_required
def admin_question_delete(request, q_id):
    from .models import Question
    if request.method == 'POST':
        q = Question.objects.filter(id=q_id).first()
        cat = q.category if q else ''
        Question.objects.filter(id=q_id).delete()
        next_url = request.POST.get('next') or (
            f'/hidden-admin-HUE-node/questions/?category={cat}' if cat else '/hidden-admin-HUE-node/questions/'
        )
        return redirect(next_url)
    return redirect('/hidden-admin-HUE-node/questions/')
