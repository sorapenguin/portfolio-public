"""ローカル開発用テスト設定 — PostgreSQL 不要で SQLite を使用する。"""
from config.settings import *  # noqa: F401, F403

DEBUG = True  # reCAPTCHA をスタブ動作にする（トークン存在確認のみ）

DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.sqlite3',
        'NAME': BASE_DIR / 'test_local.sqlite3',
    }
}
