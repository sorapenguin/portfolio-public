from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0010_sample_questions_java_saa'),
    ]

    operations = [
        # Answer: 自動再生フラグ
        migrations.AddField(
            model_name='answer',
            name='is_autoplay',
            field=models.BooleanField(default=False),
        ),
        # QuizSession: 自動再生セッションフラグ
        migrations.AddField(
            model_name='quizsession',
            name='is_autoplay',
            field=models.BooleanField(default=False),
        ),
        # UserProfile: 自動再生設定
        migrations.AddField(
            model_name='userprofile',
            name='autoplay_enabled',
            field=models.BooleanField(default=True),
        ),
        migrations.AddField(
            model_name='userprofile',
            name='autoplay_check_sec',
            field=models.IntegerField(default=3),
        ),
        migrations.AddField(
            model_name='userprofile',
            name='autoplay_next_sec',
            field=models.IntegerField(default=2),
        ),
    ]
