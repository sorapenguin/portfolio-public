from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0003_answer_user_fk'),
    ]

    operations = [
        migrations.AddField(
            model_name='question',
            name='explanation',
            field=models.TextField(blank=True, default=''),
        ),
    ]
