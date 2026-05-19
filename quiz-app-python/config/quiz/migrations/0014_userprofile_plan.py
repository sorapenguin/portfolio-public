from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0013_xplog'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='plan',
            field=models.CharField(
                choices=[
                    ('free', '無料'),
                    ('limited', '期間限定課金'),
                    ('lifetime', '永久課金'),
                    ('admin', '管理者'),
                ],
                default='free',
                max_length=20,
            ),
        ),
    ]
