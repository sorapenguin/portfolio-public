# ⚠️ このディレクトリはアーカイブです

## 概要

このディレクトリ（`phpspringECSite/www/htdocs/ec_site/api/`）は  
**Spring Boot 3 + Java 21** で実装した EC サイト REST API の旧バージョンです。

## 現在の運用対象

| | パス | 技術スタック |
|---|---|---|
| **現役（デプロイ対象）** | `EcApi/` | Ktor 3.1 + Kotlin + Exposed |
| **アーカイブ（参照のみ）** | ここ | Spring Boot 3.3 + Java 21 + JPA |

## なぜ残してあるか

- Spring Boot 版の実装を移植元として参照するため
- JPA アノテーション・Flyway マイグレーション（`V1__init.sql`）は DB スキーマの参照用として有効
- git 履歴を壊さないため

## 注意

- **このディレクトリのコードはデプロイしないこと**
- `docker-compose.dev.yml` の `ec-api` サービスには `profiles: [archived]` が設定されており、
  通常の `docker compose up` では起動しない
- 新機能・バグ修正はすべて `EcApi/` に対して行うこと
