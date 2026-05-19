from django.db import models
from django.conf import settings

PLAN_FREE = 'free'
PLAN_LIMITED = 'limited'
PLAN_LIFETIME = 'lifetime'
PLAN_ADMIN_PLAN = 'admin'

PLAN_CHOICES = [
    (PLAN_FREE,       '無料'),
    (PLAN_LIMITED,    '期間限定課金'),
    (PLAN_LIFETIME,   '永久課金'),
    (PLAN_ADMIN_PLAN, '管理者'),
]


class Question(models.Model):
    category = models.CharField(max_length=100, default="python")
    text = models.CharField(max_length=500)
    choices = models.JSONField()
    answer = models.CharField(max_length=255)
    explanation = models.TextField(blank=True, default="")

    class Meta:
        ordering = ['category', 'id']


class QuizSession(models.Model):
    quiz_id = models.CharField(max_length=36, primary_key=True)
    question_ids = models.JSONField()
    current_index = models.IntegerField(default=0)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
    )
    created_at = models.DateTimeField(auto_now_add=True)
    is_autoplay = models.BooleanField(default=False)


class UserProfile(models.Model):
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='quiz_profile',
    )
    stamina = models.IntegerField(default=100)
    stamina_updated_at = models.DateTimeField(auto_now_add=True)
    xp = models.IntegerField(default=0)
    last_category = models.CharField(max_length=100, default='python')
    autoplay_enabled = models.BooleanField(default=True)
    autoplay_check_sec = models.IntegerField(default=3)
    autoplay_next_sec = models.IntegerField(default=2)
    gems = models.IntegerField(default=0)
    mini_gems = models.IntegerField(default=0)
    mini_gems_last_claimed = models.DateTimeField(null=True, blank=True)
    unlocked_questions = models.JSONField(default=dict)
    plan = models.CharField(max_length=20, choices=PLAN_CHOICES, default=PLAN_FREE)
    plan_expires_at = models.DateTimeField(null=True, blank=True)
    # 課金操作を許可するフラグ。管理者が個別ユーザーごとに切り替える想定。
    # ポートフォリオデモアカウントのみ True に設定し、一般登録ユーザーは False のまま。
    can_purchase = models.BooleanField(default=False)

    def __str__(self):
        return f"{self.user.username} (Lv {self.level})"

    @property
    def is_premium(self):
        return self.plan in (PLAN_LIMITED, PLAN_LIFETIME, PLAN_ADMIN_PLAN)

    @property
    def level(self):
        from quiz.services import get_level_info
        return get_level_info(self.xp)['level']


class XPLog(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
    )
    date = models.DateField()
    amount = models.IntegerField(default=0)

    class Meta:
        unique_together = ('user', 'date')
        ordering = ['-date']


class Answer(models.Model):
    quiz_id = models.CharField(max_length=36)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
    )
    question_id = models.IntegerField()
    answer = models.CharField(max_length=255)
    is_correct = models.BooleanField(default=False)
    is_autoplay = models.BooleanField(default=False)

    class Meta:
        ordering = ['question_id']
