from django.db import migrations


JAVA_QUESTIONS = [
    {
        "category": "Java",
        "text": "Javaでクラスを継承するときに使うキーワードはどれですか？",
        "choices": ["extends", "implements", "inherits", "super"],
        "answer": "extends",
        "explanation": "'extends' がクラス継承に使うキーワードです。'implements' はインターフェースの実装に使います。",
    },
    {
        "category": "Java",
        "text": "Javaのエントリーポイントとなる main メソッドの正しいシグネチャはどれですか？",
        "choices": [
            "public static void main(String[] args)",
            "public void main(String[] args)",
            "static void main(String args)",
            "public static int main(String[] args)",
        ],
        "answer": "public static void main(String[] args)",
        "explanation": "JVMから呼び出されるため public static が必須で、戻り値は void、引数は String[] args です。",
    },
    {
        "category": "Java",
        "text": "Javaで不要になったオブジェクトのメモリを自動的に解放するしくみはどれですか？",
        "choices": ["ガベージコレクタ", "デストラクタ", "finalメソッド", "disposeメソッド"],
        "answer": "ガベージコレクタ",
        "explanation": "Javaにはガベージコレクタ（GC）が組み込まれており、参照されなくなったオブジェクトのメモリを自動解放します。",
    },
]

SAA_QUESTIONS = [
    {
        "category": "SAA",
        "text": "Amazon S3 のデフォルトストレージクラスはどれですか？",
        "choices": ["S3 Standard", "S3 Standard-IA", "S3 Glacier", "S3 Intelligent-Tiering"],
        "answer": "S3 Standard",
        "explanation": "S3 Standard は高耐久性（99.999999999%）と高可用性（99.99%）を提供するデフォルトのストレージクラスです。",
    },
    {
        "category": "SAA",
        "text": "AWS でスケーラブルな仮想サーバーを提供するサービスはどれですか？",
        "choices": ["Amazon EC2", "Amazon S3", "Amazon RDS", "AWS Lambda"],
        "answer": "Amazon EC2",
        "explanation": "EC2（Elastic Compute Cloud）はAWSの仮想サーバーサービスです。インスタンスタイプを選択してコンピューティングリソースをスケールできます。",
    },
    {
        "category": "SAA",
        "text": "複数のアベイラビリティーゾーンにトラフィックを自動分散し、高可用性を実現するAWSサービスはどれですか？",
        "choices": ["Elastic Load Balancing", "Amazon Route 53", "Amazon CloudFront", "Amazon API Gateway"],
        "answer": "Elastic Load Balancing",
        "explanation": "ELB（Elastic Load Balancing）は複数のAZにまたがってリクエストを分散し、単一障害点をなくして高可用性を実現します。",
    },
]


def insert_questions(apps, schema_editor):
    Question = apps.get_model('quiz', 'Question')
    for q in JAVA_QUESTIONS + SAA_QUESTIONS:
        Question.objects.create(**q)


def remove_questions(apps, schema_editor):
    Question = apps.get_model('quiz', 'Question')
    Question.objects.filter(category__in=['Java', 'SAA']).delete()


class Migration(migrations.Migration):

    dependencies = [
        ('quiz', '0009_userprofile_last_category'),
    ]

    operations = [
        migrations.RunPython(insert_questions, remove_questions),
    ]
