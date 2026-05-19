from django.core.management.base import BaseCommand
from quiz.models import Question

QUESTIONS = [
    # ── Python ──────────────────────────────────────────────────────────────
    {
        "category": "python",
        "text": "Python でリストの末尾に要素を追加するメソッドはどれ？",
        "choices": ["append()", "add()", "insert()", "push()"],
        "answer": "append()",
        "explanation": "append() はリストの末尾に要素を 1 つ追加します。insert(index, value) は任意の位置への挿入、add() は set 型のメソッドです。Python のリストに push() はありません。",
    },
    {
        "category": "python",
        "text": "Python で辞書のキー一覧を取得するメソッドはどれ？",
        "choices": ["keys()", "values()", "items()", "get()"],
        "answer": "keys()",
        "explanation": "keys() は辞書の全キーを返します。values() は値一覧、items() はキーと値のペア一覧、get(key) は指定キーの値を返します。",
    },
    # ── Java ────────────────────────────────────────────────────────────────
    {
        "category": "Java",
        "text": "Java でクラスを継承するときに使うキーワードはどれですか？",
        "choices": ["extends", "implements", "inherits", "super"],
        "answer": "extends",
        "explanation": "'extends' がクラス継承に使うキーワードです。'implements' はインターフェースの実装に使います。",
    },
    {
        "category": "Java",
        "text": "Java のエントリーポイントとなる main メソッドの正しいシグネチャはどれですか？",
        "choices": [
            "public static void main(String[] args)",
            "public void main(String[] args)",
            "static void main(String args)",
            "public static int main(String[] args)",
        ],
        "answer": "public static void main(String[] args)",
        "explanation": "JVM から呼び出されるため public static が必須で、戻り値は void、引数は String[] args です。",
    },
    {
        "category": "Java",
        "text": "Java で不要になったオブジェクトのメモリを自動的に解放するしくみはどれですか？",
        "choices": ["ガベージコレクタ", "デストラクタ", "final メソッド", "dispose メソッド"],
        "answer": "ガベージコレクタ",
        "explanation": "Java にはガベージコレクタ（GC）が組み込まれており、参照されなくなったオブジェクトのメモリを自動解放します。",
    },
    # ── SAA ─────────────────────────────────────────────────────────────────
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
        "explanation": "EC2（Elastic Compute Cloud）は AWS の仮想サーバーサービスです。インスタンスタイプを選択してコンピューティングリソースをスケールできます。",
    },
    {
        "category": "SAA",
        "text": "複数のアベイラビリティーゾーンにトラフィックを自動分散し、高可用性を実現する AWS サービスはどれですか？",
        "choices": ["Elastic Load Balancing", "Amazon Route 53", "Amazon CloudFront", "Amazon API Gateway"],
        "answer": "Elastic Load Balancing",
        "explanation": "ELB（Elastic Load Balancing）は複数の AZ にまたがってリクエストを分散し、単一障害点をなくして高可用性を実現します。",
    },
]


class Command(BaseCommand):
    help = "問題データを Question テーブルに投入する（重複スキップ）"

    def handle(self, *args, **options):
        created = 0
        skipped = 0
        for q in QUESTIONS:
            _, is_new = Question.objects.get_or_create(
                category=q["category"],
                text=q["text"],
                defaults={
                    "choices": q["choices"],
                    "answer": q["answer"],
                    "explanation": q["explanation"],
                },
            )
            if is_new:
                created += 1
            else:
                skipped += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"{len(QUESTIONS)} 件処理 — 新規: {created} 件 / スキップ: {skipped} 件"
            )
        )
