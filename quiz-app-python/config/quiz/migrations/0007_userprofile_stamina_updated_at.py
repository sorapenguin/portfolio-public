import django.utils.timezone
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0006_userprofile'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='stamina_updated_at',
            field=models.DateTimeField(auto_now_add=True, default=django.utils.timezone.now),
            preserve_default=False,
        ),
    ]
