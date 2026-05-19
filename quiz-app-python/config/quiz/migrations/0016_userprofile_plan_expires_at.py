from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0015_userprofile_minigems'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='plan_expires_at',
            field=models.DateTimeField(blank=True, null=True),
        ),
    ]
