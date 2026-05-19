from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0011_autoplay_fields'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='gems',
            field=models.IntegerField(default=0),
        ),
        migrations.AddField(
            model_name='userprofile',
            name='unlocked_questions',
            field=models.JSONField(default=dict),
        ),
    ]
