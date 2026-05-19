# EC サイト（ポートフォリオ）

PHP + Spring Boot + PostgreSQL で構築したECサイトのポートフォリオ作品です。

## 技術スタック

| レイヤー | 技術 |
|---|---|
| フロントエンド | PHP (テンプレート)、HTML/CSS/JavaScript |
| バックエンド API | Spring Boot (Java) |
| データベース | PostgreSQL 16 |
| Web サーバー | Nginx |
| コンテナ | Docker / Docker Compose |

## 主な機能

- 商品一覧・商品詳細表示
- カート・購入フロー
- ユーザー登録・ログイン（JWT 認証）
- 管理者画面（商品管理・注文管理・クーポン管理）
- お気に入り機能・注文履歴
- 管理者 2FA 認証

## 起動方法

```bash
# コンテナをビルド・起動（初回は数分かかります）
docker compose up --build -d

# ブラウザでアクセス
# http://localhost:8004
```

## デモアカウント

> **注意:** 以下はポートフォリオ閲覧用のデモアカウントです。  
> 本番環境では必ず環境変数で認証情報を上書きしてください。

| 権限 | ユーザー名 | パスワード |
|---|---|---|
| 管理者 | `ec_admin` | `ec_admin` |
| 一般ユーザー | `ec_user` | `password123` |

ログイン画面のデモボタンからワンクリックでログインできます。

## 環境変数（本番運用時）

`docker-compose.yml` の以下の値を環境変数で上書きしてください。

| 変数名 | デフォルト（デモ用） | 説明 |
|---|---|---|
| `POSTGRES_PASSWORD` | `ec_pass` | DB パスワード |
| `SPRING_DATASOURCE_PASSWORD` | `ec_pass` | API の DB 接続パスワード |
| `JWT_SECRET` | `ec-site-jwt-secret-key-...` | JWT 署名キー（256bit 以上） |

## プロジェクト構成

```
ec_site/
├── config/          # DB接続設定
├── controller/      # PHPコントローラー
├── model/           # PHPモデル（DB操作）
├── view/            # PHPビュー（HTMLテンプレート）
├── js/              # フロントエンドJS
├── uploads/         # 商品画像
├── api/             # Spring Boot API
│   └── src/main/java/com/ec/api/
├── docker/          # Docker設定・初期化SQL
├── docker-compose.yml
└── Dockerfile
```
