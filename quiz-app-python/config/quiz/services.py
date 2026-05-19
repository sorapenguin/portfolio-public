import uuid
from datetime import timedelta

STAMINA_MAX = 100
STAMINA_RECOVERY_MINUTES = 5  # 1スタミナ回復に必要な分数
XP_PER_CORRECT = 10
XP_TWO_CHOICE = 3
XP_PER_AUTOPLAY = 1
QUESTIONS_PER_GEM_UNLOCK = 10  # 1ジェムで解放できる問題数
BASE_UNLOCKED = 10              # 初期解放問題数（無料）
GEMS_PER_LEVEL_UP = 1          # レベルアップ時に獲得するジェム数
# レベルごとの累積必要XP（index + 1 がレベル番号）
LEVEL_THRESHOLDS = [0, 100, 210, 330, 460, 600, 750, 910, 1080, 1260]


def get_level_info(xp):
    """XP からレベル情報を計算して返す。"""
    level = 1
    for i, threshold in enumerate(LEVEL_THRESHOLDS):
        if xp >= threshold:
            level = i + 1
        else:
            break
    is_max = level >= len(LEVEL_THRESHOLDS)
    current_threshold = LEVEL_THRESHOLDS[level - 1]
    if is_max:
        xp_in_level = xp - current_threshold
        xp_for_next = max(xp_in_level, 1)
    else:
        next_threshold = LEVEL_THRESHOLDS[level]
        xp_in_level = xp - current_threshold
        xp_for_next = next_threshold - current_threshold
    progress_pct = min(100, round(xp_in_level / xp_for_next * 100))
    return {
        'level': level,
        'xp': xp,
        'xp_in_level': xp_in_level,
        'xp_for_next': xp_for_next,
        'progress_pct': progress_pct,
        'is_max_level': is_max,
    }


def _apply_passive_recovery(profile):
    """経過時間に応じたパッシブ回復を profile に適用する。

    5分ごとに1スタミナ回復。端数の時間は次回に持ち越すため
    stamina_updated_at を「消費したインターバルの終端」に設定する。
    変更があった場合のみ DB 保存する。
    """
    from django.utils import timezone

    if profile.stamina >= STAMINA_MAX:
        return

    elapsed_seconds = (timezone.now() - profile.stamina_updated_at).total_seconds()
    recovered = int(elapsed_seconds / 60 / STAMINA_RECOVERY_MINUTES)

    if recovered <= 0:
        return

    profile.stamina = min(STAMINA_MAX, profile.stamina + recovered)
    # 消費したインターバル分だけ updated_at を進め、端数の時間を保持する
    profile.stamina_updated_at += timedelta(minutes=recovered * STAMINA_RECOVERY_MINUTES)
    profile.save(update_fields=['stamina', 'stamina_updated_at'])


def get_unlock_info(user, category):
    """カテゴリ内で解放済みの問題数・合計問題数を返す。

    Returns: {'unlocked': N, 'total': M, 'can_unlock_more': bool}
    """
    from .models import Question
    profile = get_or_create_profile(user)
    total = Question.objects.filter(category=category).count()
    unlocked_count = profile.unlocked_questions.get(category, BASE_UNLOCKED)
    unlocked = min(unlocked_count, total)
    return {
        'unlocked': unlocked,
        'total': total,
        'can_unlock_more': unlocked_count < total,
    }


def spend_gem_unlock(user, category):
    """1ジェムを消費して指定カテゴリの問題を10問解放する。"""
    from .models import Question
    profile = get_or_create_profile(user)
    if profile.gems < 1:
        return False
    total = Question.objects.filter(category=category).count()
    current = profile.unlocked_questions.get(category, BASE_UNLOCKED)
    if current >= total:
        return False  # すでに全問解放済み
    unlocked = dict(profile.unlocked_questions)
    unlocked[category] = current + QUESTIONS_PER_GEM_UNLOCK
    profile.gems -= 1
    profile.unlocked_questions = unlocked
    profile.save(update_fields=['gems', 'unlocked_questions'])
    return True


def spend_gem_stamina(user):
    """1ジェムを消費してスタミナを全回復する。"""
    from django.utils import timezone
    profile = get_or_create_profile(user)
    if profile.gems < 1:
        return False
    profile.gems -= 1
    profile.stamina = STAMINA_MAX
    profile.stamina_updated_at = timezone.now()
    profile.save(update_fields=['gems', 'stamina', 'stamina_updated_at'])
    return True


def get_or_create_profile(user):
    from .models import UserProfile
    profile, _ = UserProfile.objects.get_or_create(user=user)
    _apply_passive_recovery(profile)
    return profile


def deduct_stamina(user, amount):
    """スタミナを amount 消費する。不足なら False を返す（消費しない）。"""
    profile = get_or_create_profile(user)  # パッシブ回復を先に適用
    if profile.stamina < amount:
        return False
    profile.stamina -= amount
    profile.save(update_fields=['stamina'])
    return True


def recover_stamina(user):
    """スタミナを最大値まで回復する（ボタン押下）。"""
    from django.utils import timezone
    profile = get_or_create_profile(user)
    profile.stamina = STAMINA_MAX
    profile.stamina_updated_at = timezone.now()  # 満タンなので今から計測開始
    profile.save(update_fields=['stamina', 'stamina_updated_at'])


def add_xp(user, amount):
    """XP を加算し、レベルアップした場合はジェムを付与して True を返す。"""
    from django.utils import timezone
    from django.db.models import F
    from .models import XPLog

    profile = get_or_create_profile(user)
    old_level = get_level_info(profile.xp)['level']
    profile.xp += amount
    profile.save(update_fields=['xp'])
    new_level = get_level_info(profile.xp)['level']
    leveled_up = new_level > old_level
    if leveled_up:
        levels_gained = new_level - old_level
        profile.gems += levels_gained * GEMS_PER_LEVEL_UP
        profile.save(update_fields=['gems'])

    today = timezone.localdate()
    log, created = XPLog.objects.get_or_create(user=user, date=today, defaults={'amount': amount})
    if not created:
        XPLog.objects.filter(pk=log.pk).update(amount=F('amount') + amount)

    return leveled_up


def load_questions():
    from .models import Question
    return list(Question.objects.values("id", "text", "choices", "answer", "explanation"))


def get_question(q_id):
    from .models import Question
    try:
        q = Question.objects.get(id=q_id)
        return {"id": q.id, "text": q.text, "choices": q.choices, "answer": q.answer, "explanation": q.explanation}
    except Question.DoesNotExist:
        return None


def create_quiz_session(question_ids, user=None, is_autoplay=False):
    from .models import QuizSession
    quiz_id = str(uuid.uuid4())
    QuizSession.objects.create(
        quiz_id=quiz_id, question_ids=question_ids, current_index=0,
        user=user, is_autoplay=is_autoplay,
    )
    return quiz_id


def get_active_session_for_user(user, category=None):
    from .models import QuizSession, Question

    category_ids = None
    if category:
        category_ids = set(Question.objects.filter(category=category).values_list('id', flat=True))

    for session in QuizSession.objects.filter(user=user).order_by('-created_at'):
        if session.current_index >= len(session.question_ids):
            continue
        if category_ids is not None and not set(session.question_ids).issubset(category_ids):
            continue
        return {
            "quiz_id": session.quiz_id,
            "current_question_id": session.question_ids[session.current_index],
            "current_index": session.current_index,
            "total": len(session.question_ids),
        }
    return None


def get_quiz_session(quiz_id):
    from .models import QuizSession
    try:
        session = QuizSession.objects.get(quiz_id=quiz_id)
        return {
            "quiz_id": session.quiz_id,
            "question_ids": session.question_ids,
            "current_index": session.current_index,
            "is_autoplay": session.is_autoplay,
        }
    except QuizSession.DoesNotExist:
        return None


def update_quiz_session(quiz_id, question_ids, current_index):
    from .models import QuizSession
    QuizSession.objects.filter(quiz_id=quiz_id).update(
        question_ids=question_ids,
        current_index=current_index,
    )


def save_answer(quiz_id, user, question_id, answer, correct=True, is_autoplay=False):
    from .models import Answer
    Answer.objects.create(
        quiz_id=quiz_id,
        user=user,
        question_id=question_id,
        answer=answer,
        is_correct=correct,
        is_autoplay=is_autoplay,
    )


def get_answers_by_quiz(quiz_id):
    from .models import Answer, Question
    answers = list(
        Answer.objects.filter(quiz_id=quiz_id)
        .order_by("question_id")
        .values("quiz_id", "user__username", "question_id", "answer", "is_correct")
    )
    question_map = {
        q.id: q
        for q in Question.objects.filter(id__in=[a['question_id'] for a in answers])
    }
    for a in answers:
        q = question_map.get(a['question_id'])
        if q:
            a['question_text'] = q.text
            a['question_choices'] = q.choices
            a['question_answer'] = q.answer
            a['question_explanation'] = q.explanation
        else:
            a['question_text'] = '(問題が見つかりません)'
            a['question_choices'] = []
            a['question_answer'] = ''
            a['question_explanation'] = ''
    return answers


def get_score(quiz_id):
    from .models import Answer
    answers = Answer.objects.filter(quiz_id=quiz_id)
    total = answers.count()
    correct_count = answers.filter(is_correct=True).count()
    return correct_count, total


def _get_question_stats(user, category=None):
    """全問題（またはカテゴリ内）の統計を {question_id: dict} で返す。

    各 dict のキー:
      status          : '未出題' / 'ミス' / 'ヒット' / 'コンボ'
      last_answered_at: 最終回答日時 (datetime | None)
      correct_rate    : 正答率 0-100 の int (未回答は None)
    """
    from .models import Question, Answer, QuizSession

    profile = get_or_create_profile(user)

    if category:
        unlocked_count = profile.unlocked_questions.get(category, BASE_UNLOCKED)
        all_ids = list(
            Question.objects.filter(category=category)
            .order_by('id')
            .values_list('id', flat=True)[:unlocked_count]
        )
    else:
        cats = list(Question.objects.values_list('category', flat=True).distinct())
        all_ids = []
        for cat in cats:
            unlocked_count = profile.unlocked_questions.get(cat, BASE_UNLOCKED)
            all_ids.extend(
                Question.objects.filter(category=cat)
                .order_by('id')
                .values_list('id', flat=True)[:unlocked_count]
            )
    # is_autoplay=True の回答はステータス計算から除外（未回答扱い）
    user_answers = list(Answer.objects.filter(user=user, question_id__in=all_ids, is_autoplay=False))

    if not user_answers:
        return {
            q_id: {'status': '未出題', 'last_answered_at': None, 'correct_rate': None}
            for q_id in all_ids
        }

    quiz_ids = list({a.quiz_id for a in user_answers})
    session_times = {
        s.quiz_id: s.created_at
        for s in QuizSession.objects.filter(quiz_id__in=quiz_ids)
    }

    history_map = {}
    for a in user_answers:
        history_map.setdefault(a.question_id, []).append(
            (session_times.get(a.quiz_id), a.is_correct)
        )

    stats = {}
    for q_id in all_ids:
        if q_id not in history_map:
            stats[q_id] = {'status': '未出題', 'last_answered_at': None, 'correct_rate': None}
            continue

        history = sorted(history_map[q_id], key=lambda x: x[0] or 0)

        consecutive = 0
        for _, is_correct in reversed(history):
            if is_correct:
                consecutive += 1
            else:
                break

        if consecutive == 0:
            status = 'ミス'
        elif consecutive == 1:
            status = 'ヒット'
        else:
            status = 'コンボ'

        total = len(history)
        correct = sum(1 for _, ok in history if ok)
        stats[q_id] = {
            'status': status,
            'last_answered_at': history[-1][0],
            'correct_rate': round(correct / total * 100),
        }

    return stats


def get_question_status_counts(user, category=None):
    """ステータスごとの問題数を {'未出題': N, 'ミス': N, 'ヒット': N, 'コンボ': N} で返す。"""
    counts = {'未出題': 0, 'ミス': 0, 'ヒット': 0, 'コンボ': 0}
    for s in _get_question_stats(user, category=category).values():
        counts[s['status']] += 1
    return counts


def get_filtered_question_ids(user, filters, min_rate=0, max_rate=100, elapsed_minutes=0, category=None):
    """条件に合致する問題IDリストを返す。

    filters        : ステータス名のリスト
    min_rate       : 正答率の下限 % (既回答問題のみ適用、未出題は通過)
    max_rate       : 正答率の上限 %
    elapsed_minutes: 前回解答からの最低経過分数 (0 = 指定なし)
    """
    from django.utils import timezone

    now = timezone.now()
    result = []

    for q_id, s in _get_question_stats(user, category=category).items():
        if s['status'] not in filters:
            continue

        # 正答率フィルター（回答済みの問題のみ適用）
        if s['correct_rate'] is not None:
            if not (min_rate <= s['correct_rate'] <= max_rate):
                continue

        # 経過時間フィルター（0 = 指定なし、未出題は常に通過）
        if elapsed_minutes > 0 and s['last_answered_at'] is not None:
            elapsed = (now - s['last_answered_at']).total_seconds() / 60
            if elapsed < elapsed_minutes:
                continue

        result.append(q_id)

    return result


def get_last_completed_session(user, category=None):
    """指定カテゴリの最後に完了したセッションオブジェクトを返す。なければ None。"""
    from .models import QuizSession, Question

    category_ids = None
    if category:
        category_ids = set(Question.objects.filter(category=category).values_list('id', flat=True))

    for s in QuizSession.objects.filter(user=user).order_by('-created_at'):
        if s.current_index < len(s.question_ids):
            continue
        if category_ids is not None and not set(s.question_ids).issubset(category_ids):
            continue
        return s
    return None


def get_completed_sessions_for_user(user, category=None):
    from .models import QuizSession, Answer, Question

    # カテゴリ指定時は対象問題IDセットを先に取得
    category_ids = None
    if category:
        category_ids = set(Question.objects.filter(category=category).values_list('id', flat=True))

    result = []
    for s in QuizSession.objects.filter(user=user).order_by('-created_at'):
        total_questions = len(s.question_ids)
        if s.current_index < total_questions:
            continue  # 未完了はスキップ

        # カテゴリフィルター：セッションの全問題が該当カテゴリに属するものだけ
        if category_ids is not None and not set(s.question_ids).issubset(category_ids):
            continue

        answers = Answer.objects.filter(quiz_id=s.quiz_id)
        answered = answers.count()
        correct = answers.filter(is_correct=True).count()
        result.append({
            "quiz_id": s.quiz_id,
            "created_at": s.created_at,
            "total": total_questions,
            "correct": correct,
            "rate": round(correct / answered * 100) if answered > 0 else 0,
        })
    return result
