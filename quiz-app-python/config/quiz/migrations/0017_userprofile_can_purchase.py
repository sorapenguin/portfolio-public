from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0016_userprofile_plan_expires_at'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='can_purchase',
            field=models.BooleanField(default=False),
        ),
    ]
