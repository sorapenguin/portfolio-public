from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0007_userprofile_stamina_updated_at'),
    ]

    operations = [
        migrations.AddField(
            model_name='question',
            name='category',
            field=models.CharField(default='python', max_length=100),
        ),
        migrations.AlterModelOptions(
            name='question',
            options={'ordering': ['category', 'id']},
        ),
    ]
