from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0008_question_category'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='last_category',
            field=models.CharField(default='python', max_length=100),
        ),
    ]
