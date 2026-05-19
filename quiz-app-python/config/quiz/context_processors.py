from django.utils import timezone
from django.conf import settings as django_settings
from .services import get_or_create_profile, get_level_info, STAMINA_MAX, STAMINA_RECOVERY_MINUTES


def user_profile(request):
    from .models import Question

    # 設定の固定リストを基準に、DB にだけ存在する追加カテゴリを末尾に結合
    predefined = list(django_settings.QUIZ_CATEGORIES)
    db_cats = list(Question.objects.values_list('category', flat=True).distinct())
    extra = [c for c in db_cats if c not in predefined]
    quiz_categories = predefined + extra

    if not request.user.is_authenticated:
        return {'quiz_categories': quiz_categories, 'current_category': 'python'}

    profile = get_or_create_profile(request.user)

    # 期限切れチェック：PLAN_LIMITED が期限を過ぎていれば無料プランへ
    from .models import PLAN_LIMITED, PLAN_FREE
    if profile.plan == PLAN_LIMITED and profile.plan_expires_at and profile.plan_expires_at < timezone.now():
        from .models import UserProfile
        UserProfile.objects.filter(user=request.user).update(plan=PLAN_FREE, plan_expires_at=None)
        profile.plan = PLAN_FREE
        profile.plan_expires_at = None

    level_info = get_level_info(profile.xp)
    stamina_pct = round(profile.stamina / STAMINA_MAX * 100)

    # 全回復まであと何分か（満タン時は非表示用に None）
    if profile.stamina < STAMINA_MAX:
        elapsed_seconds = (timezone.now() - profile.stamina_updated_at).total_seconds()
        seconds_per_point = STAMINA_RECOVERY_MINUTES * 60
        deficit = STAMINA_MAX - profile.stamina
        remaining_for_next = seconds_per_point - (elapsed_seconds % seconds_per_point)
        total_seconds = remaining_for_next + (deficit - 1) * seconds_per_point
        full_recovery_min = max(1, -(-int(total_seconds) // 60))  # ceiling division
    else:
        full_recovery_min = None

    return {
        'profile': profile,
        'level_info': level_info,
        'stamina_max': STAMINA_MAX,
        'stamina_pct': stamina_pct,
        'stamina_low': profile.stamina <= 20,
        'full_recovery_min': full_recovery_min,
        'quiz_categories': quiz_categories,
        'current_category': profile.last_category,
        'user_is_premium': profile.is_premium,
    }
