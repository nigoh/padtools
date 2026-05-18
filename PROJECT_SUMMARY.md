PadTools プロジェクトサマリー
================================

## 1. これは何？

**PadTools 2.0** は、Java / Android (Gradle + AndroidManifest.xml + AIDL) プロジェクト
を解析し、PlantUML 経由で **UML 図を生成する Swing ベースの対話型ツール**です。
GUI と CLI の両方を備え、PlantUML を同梱しているため Graphviz の追加インストールは
不要です。

* バージョン: 2.0 (旧 1.x の PAD/SPD 機能は廃止し、UML 専用ツールへ完全転換)
* ライセンス: MIT (同梱 PlantUML は GPLv3)
* 必要環境: Java 17 以上 (Windows / macOS / Linux)

## 2. 主な機能

| 区分 | 内容 |
|---|---|
| UML クラス図 | パッケージ単位グループ化 / 継承・実装・利用関係 / AAOS パターン / Manifest 連携 (`<<Activity>>` 等) / JavaDoc・アノテーション・enum 定数 |
| パッケージ図 | パッケージごとのクラス数と参照関係を集約 |
| シーケンス図 | `Class.method` を起点に多段トレース、`if/while/switch/try` を `alt/loop/group` に変換 |
| コンポーネント図 | AndroidManifest の Activity / Service / Receiver / Provider、`exported`、ランチャー強調、uses-permission |
| Manifest 図 | `<application>` 属性 (package / class / theme / debuggable / allowBackup / meta-data) を中央に、配下に Activity / Service / Receiver / Provider をグループ化、周辺に uses-permission / uses-feature |
| Gradle 依存図 | モジュール間 `project(':x')` 依存と Maven ライブラリ、`libs.versions.toml` 解決 |
| GUI プレビュー | Apache Batik による **ベクター SVG** プレビュー (PNG 4096×4096 制約の影響を受けない) |
| エクスポート | SVG / PNG / PUML / Markdown サマリーを出力、`--all` で一括出力 |

## 3. プロジェクト構成 (ソース)

```
src/main/java/padtools/
├── Main.java                   # CLI / GUI のエントリポイント
├── app/uml/                    # UML 専用 Swing GUI (UmlMainFrame, SvgPreviewPanel ほか)
├── core/formats/
│   ├── java/                   # Java/AIDL 字句解析、プロジェクトスキャナ
│   ├── uml/                    # PlantUML 生成器 (クラス / シーケンス / パッケージ図)、AST 抽出
│   └── android/                # Gradle スクリプト / Manifest / Version Catalog のパーサと
│                               #   コンポーネント図 / 依存グラフ生成器
└── util/                       # OptionParser / Messages / PathUtil 等
```

* 本体: Java 61 ファイル / 約 10.7k LoC
* テスト: JUnit 4 で 30 ファイル / 約 4.9k LoC (約 360 テストケース)

## 4. ビルドと実行

```sh
./gradlew test      # ユニットテスト
./gradlew check     # テスト + Checkstyle (maxWarnings = 0)
./gradlew jar       # build/libs/PadTools.jar (依存内包 fat jar)
./gradlew makeZip   # 起動補助スクリプト付き配布 zip
```

```sh
# GUI 起動 (引数なしで UML GUI が立ち上がる)
java -jar PadTools.jar [プロジェクトディレクトリ]

# CLI で全成果物を一括出力 (summary.md + 各 SVG + ライフサイクル sequence diagrams)
java -jar PadTools.jar --all -o ./out ~/AndroidStudioProjects/MyApp
```

## 5. 主要な依存ライブラリ

| ライブラリ | 用途 |
|---|---|
| PlantUML 1.2026.2 | UML 描画 (Smetana レイアウトを自動指定し Graphviz 不要) |
| Apache Batik 1.17 (svggen / dom / bridge) | SVG をベクターのまま Swing にレンダリング |
| JFontChooser 1.0.5-3 | フォント選択ダイアログ |
| JUnit 4.13.2 | テストフレームワーク |
| Checkstyle 10.21.1 | 静的解析 |

## 6. リンク

* GitHub: <https://github.com/nigoh/padtools>
* 詳細: [README.md](./README.md)
* 変更履歴: [CHANGE.md](./CHANGE.md)
