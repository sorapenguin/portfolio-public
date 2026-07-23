# portfolio-public

就職活動用コードスナップショット集です。

継続的に同期・更新される開発リポジトリではありません。各プロジェクトの特定時点のコードを抽出したものです。未完成・試作段階のコードを含みます。本番サービスのバックエンドコード・認証情報・インフラ設定は含みません。

開発には Claude Code / Codex などの AI 支援ツールを活用しています。

---

## プロジェクト一覧

### Android アプリ（Kotlin）

| プロジェクト | 技術 | 概要 |
|---|---|---|
| [IdleGame](./IdleGame) | Kotlin / Room / Retrofit / Jetpack Compose | 放置系 RPG。マルチフレーバービルド構成（ストア版 / ポートフォリオ版） |
| [HoshiPost](./HoshiPost) | Kotlin / Jetpack Compose / Canvas | 線つなぎ配達ルートパズル。Canvas による独自描画 |
| [Nonogram](./Nonogram) | Kotlin / Jetpack Compose | ノノグラムパズルアプリ |
| [PixelArtApp](./PixelArtApp) | Kotlin / Compose / Hilt / Retrofit / Room | ピクセルアートパズル。GameAPI と連携 |

### KorGE ゲーム（Kotlin Multiplatform）

| プロジェクト | 技術 | 概要 |
|---|---|---|
| [SkyIsland](./SkyIsland) | Kotlin / KorGE 6.0 | ターン制ローグライク。3フロア・スキル・装備実装済み |
| [IslandDevKot](./IslandDevKot) | Kotlin / KorGE 6.0 | 島開拓ゲーム。A\* 経路探索・クラフトシステム実装 |
| [StarSaga](./StarSaga) | Kotlin / KorGE 6.0 | 2D アドベンチャー試作。カメラ・マップ・depth sort |
| [StarTerra](./StarTerra) | Kotlin / KorGE | 2.5D 箱庭ゲーム設計フェーズ |

### Unity ゲーム（C#）

| プロジェクト | 技術 | 概要 |
|---|---|---|
| [IdleMine](./IdleMine) | Unity / C# | 放置系採掘ゲーム試作 |

### Web フロントエンド

| プロジェクト | 技術 | 概要 |
|---|---|---|
| [puzzle-web](./puzzle-web) | Vanilla JS / HTML / CSS | ノノグラム・ぬりかべ・カックロ・ピクセルアートの 4 コンテンツ統合パズルサイト（本番稼働中） |
| [StellarRiseWeb](./StellarRiseWeb) | TypeScript / Vite | 放置 RPG の Web 版フロントエンド。Web Workers でオフライン計算 |
| [JET](./JET) | React 19 / TypeScript / Vite | Java コードトレース学習アプリ。段階的ヒント・LocalStorage 状態管理 |

### Web バックエンド / フルスタック

| プロジェクト | 技術 | 概要 |
|---|---|---|
| [InfraLab](./InfraLab) | C# / ASP.NET Core 10 / Blazor WASM / PostgreSQL | インフラ障害の調査・診断・復旧を状態遷移で学ぶシナリオ型学習アプリ。サーバー権威の状態機械・楽観ロック・冪等性制御・バックアップ / リストア / ロールバック運用スクリプト一式 |
| [GoLab](./GoLab) | Go（標準ライブラリのみ） | DNS→CDN→WAF→LB→リバースプロキシ→アプリ→DB のリクエスト経路を決定論的シミュレーションで学ぶツール。仮想時間・SSRF 回避方針を実装 |
| [Calendo](./Calendo) | C# / ASP.NET Core / SignalR / EF Core / PostgreSQL | 複数ユーザー対応のリアルタイム共有カレンダー |

---

## 補足

- 各プロジェクトの `local.properties`・`.env`・ビルド成果物は含まれません
- `puzzle-web/js/config.js` の API URL はダミー値に置き換えています
- InfraLab / GoLab は開発中のプロトタイプです。両者とも本番 VPS デプロイ前の段階で、記載の運用スクリプト・デプロイ構成は設計・検証用のサンプルです
- スナップショット日: 2026-07-23（InfraLab は更新、GoLab は新規追加）
