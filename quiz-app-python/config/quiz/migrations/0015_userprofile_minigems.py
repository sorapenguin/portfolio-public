from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0014_userprofile_plan'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='mini_gems',
            field=models.IntegerField(default=0),
        ),
        migrations.AddField(
            model_name='userprofile',
            name='mini_gems_last_claimed',
            field=models.DateTimeField(blank=True, null=True),
        ),
    ]
