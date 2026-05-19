# Quiz App (Django + Docker + PostgreSQL)

## 概要

Djangoで作成したクイズアプリをDocker環境で動作させ、VPS上で本番運用しています。

ローカル開発から本番デプロイまで Docker Compose で管理できる構成です。

---

## 使用技術

### アプリケーション

* Python 3.12
* Django
* PostgreSQL 16

### インフラ / 開発環境

* Docker / Docker Compose
* Nginx（本番：リバースプロキシ）
* VPS（本番環境）

### 主な機能

* カテゴリ別クイズ（Python / AWS / Linux など）
* ユーザー認証（ログイン / マイページ）
* ジェム報酬システム（回答・ログインボーナス）
* reCAPTCHA v2 対応（任意）

---

## アーキテクチャ

**ローカル開発**

```
[ Browser ]
     ↓
[ Django (Docker: web) ] :8000
     ↓
[ PostgreSQL (Docker: db) ]
```

**本番 (VPS)**

```
[ Browser ]
     ↓
[ Nginx ]
     ↓
[ Django (Docker: quiz_web) ]
     ↓
[ PostgreSQL ]
```

---

## セットアップ方法

### 1. リポジトリをクローン

```bash
git clone <your-repo-url>
cd quiz-app-python/config
```

### 2. 環境変数を設定

```bash
cp .env.example .env
# .env を編集して各値を設定
```

`.env` の主な項目：

| 変数 | 説明 |
|---|---|
| `SECRET_KEY` | Django シークレットキー（必須） |
| `DEBUG` | 本番は `False` |
| `DB_PASSWORD` | PostgreSQL パスワード |
| `ADMIN_PANEL_PASSWORD` | 管理パネル用パスワード |
| `RECAPTCHA_PUBLIC_KEY` | reCAPTCHA 公開鍵（省略可） |
| `RECAPTCHA_PRIVATE_KEY` | reCAPTCHA 秘密鍵（省略可） |

### 3. Docker 起動（ローカル開発）

```bash
docker compose up --build
```

`docker-compose.override.yml` が自動的に読み込まれ、DB コンテナとポートマッピングが有効になります。

### 4. アクセス

```
http://localhost:8000
```

---

## 本番デプロイ

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

本番では `app-network`（external）を事前に作成しておく必要があります：

```bash
docker network create app-network
```

---

## ディレクトリ構成

```
config/
├── docker-compose.yml          # ベース設定
├── docker-compose.override.yml # ローカル開発用
├── docker-compose.prod.yml     # 本番用オーバーライド
├── Dockerfile
├── .env.example
├── quiz/                       # Djangoアプリ
│   ├── views.py
│   ├── models.py
│   └── templates/
└── manage.py
```

---

## 工夫したポイント

* `docker-compose.override.yml` でローカル・本番の設定を分離
* WhiteNoise で静的ファイルを本番配信
* reCAPTCHA は環境変数未設定で自動的に無効化（開発時の手間を省略）
* ジェム報酬によるユーザーエンゲージメント設計

---

## 今後の改善予定

* CI/CD 導入（GitHub Actions）
* テストカバレッジ向上

---

## 作者

* sorapenguin
* 目的: バックエンド・インフラスキル習得のための学習プロジェクト
