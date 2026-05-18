PadTools 2.0 — Java + Android + Gradle UML Tool
================================================

概要
------------------------------------------------
PadTools は **Java / Android (Gradle + AndroidManifest.xml + AIDL) プロジェクトから
UML 図を生成する Swing ベースの対話型ツール**です。同梱の PlantUML で描画するため、
Graphviz / PlantUML の追加インストールは不要です。

本プロジェクトは **MIT ライセンスで公開されているオープンソースソフトウェア (OSS)** です。
ソースコードは GitHub ([nigoh/padtools](https://github.com/nigoh/padtools)) で公開しており、
誰でも自由に利用・改変・再配布できます。バグ報告や Pull Request も歓迎します。

対応する UML 図種:

| 図種 | 説明 |
|---|---|
| クラス図 | パッケージ単位でグループ化、継承 / 実装 / 利用関係、AAOS パターン (`<<CarManager>>`)、AndroidManifest 連携 (`<<Activity>>` 等)、JavaDoc / アノテーション / enum 定数 |
| パッケージ図 | パッケージごとのクラス数と参照関係 (継承 / 実装 / フィールド型) を集約 |
| シーケンス図 | `Class.method` を起点に呼び出しを多段トレース、制御構造 (`if/while/switch/try`) を `alt/loop/group` で表現 |
| コンポーネント図 | AndroidManifest の Activity / Service / Receiver / Provider、`exported` 属性、ランチャー強調、uses-permission |
| Gradle 依存図 | モジュール間 `project(':x')` 依存と外部 Maven ライブラリ、`libs.versions.toml` 解決 |

> 1.7 までは PAD (Problem Analysis Diagram) ツールでしたが、2.0 で
> Java + Android + Gradle 特化の UML ツールに完全転換しました。旧 PAD / SPD 機能は廃止されています。

動作環境
------------------------------------------------
* Java 17 以上 (JRE / JDK)
* 動作確認: Windows / macOS / Linux

使い方
------------------------------------------------

### GUI を起動

```sh
# UML GUI を起動
java -jar PadTools.jar

# 引数にプロジェクトディレクトリを指定すると起動時に解析される
java -jar PadTools.jar ~/AndroidStudioProjects/MyApp
```

GUI の操作:

* **File → Open Project...** (Ctrl+O) — Android / Gradle プロジェクトのルートディレクトリを選択
* **Diagram メニュー** — クラス図 / パッケージ図 / シーケンス図 / コンポーネント図 / 依存図 のラジオ選択
* **Diagram → Choose Sequence Entry...** — シーケンス図の起点 `Class.method` を絞り込みリストから選択
* **View → Zoom In / Out / 100% / Fit** (Ctrl+= / Ctrl+- / Ctrl+0 / Ctrl+F) — プレビューズーム。Ctrl+ホイールでもズーム、左ドラッグでパン
* **右ペイン**: Preview タブ (画像) と PlantUML Source タブ (生成テキスト) を切替
* **File → Save Diagram As...** (Ctrl+S) — SVG / PNG / PUML 形式で保存

### CLI から個別図を生成

```sh
# UML クラス図 (SVG として直接書き出し)
java -jar PadTools.jar -c -o my.svg MyClass.java

# プロジェクト全体 (manifest を自動検出して <<Activity>> 等を付与)
java -jar PadTools.jar -c -o app.svg ~/AndroidStudioProjects/MyApp

# シーケンス図 (Class.method を起点に呼び出しを多段トレース)
java -jar PadTools.jar -q "MainActivity.onCreate" -o seq.svg MainActivity.java

# 起点メソッドの候補一覧 (fzf 等と組み合わせて選択)
java -jar PadTools.jar --list-methods ~/AOSP/Car

# Android ライフサイクル起点のシーケンス図を一括出力 (.puml + .svg)
java -jar PadTools.jar -Q -o ./seq-out ~/AndroidStudioProjects/MyApp

# コンポーネント図 / 依存グラフ
java -jar PadTools.jar -d -o components.svg ~/AndroidStudioProjects/MyApp
java -jar PadTools.jar -G -o deps.svg ~/AndroidStudioProjects/MyApp

# Markdown プロジェクトサマリー
java -jar PadTools.jar --summary -o report.md ~/AndroidStudioProjects/MyApp

# 全種類を一括出力 (summary + 各 SVG + ライフサイクル シーケンス図群)
java -jar PadTools.jar --all -o ./out ~/AndroidStudioProjects/MyApp
```

`--all` の出力:

| ファイル | 内容 |
|---|---|
| `summary.md` | Markdown プロジェクトサマリー |
| `class-diagram.svg` | UML クラス図 (manifest 自動マージ) |
| `component-diagram.svg` | Android コンポーネント図 |
| `dependency-graph.svg` | Gradle 依存グラフ |
| `methods.txt` | シーケンス図の起点候補一覧 (`Class.method`) |
| `sequence-diagrams/` | Android ライフサイクル起点のシーケンス図群 (`Class.method.puml` + `.svg` を併出力) |

### CLI オプション一覧

| オプション | 説明 |
|---|---|
| `-o FILE` | 出力先ファイル (拡張子で形式判別: `.svg` / `.puml` / `.md` / `.txt`) |
| `-c` / `--class-diagram` | PlantUML クラス図を生成 |
| `-q CLASS.METHOD` / `--sequence-diagram CLASS.METHOD` | PlantUML シーケンス図を生成 |
| `-Q` / `--sequence-diagrams` | Android プロジェクトのライフサイクル起点シーケンス図を `-o` ディレクトリへ一括出力 (`.puml` + `.svg`) |
| `-d` / `--component-diagram` | PlantUML Android コンポーネント図を生成 |
| `-G` / `--dependency-graph` | PlantUML Gradle 依存グラフを生成 |
| `-g` / `--gradle` | Gradle ファイル単体を Markdown サマリー化 |
| `-m` / `--manifest` | AndroidManifest.xml 単体を Markdown サマリー化 |
| `--summary` | プロジェクト全体を Markdown サマリー化 |
| `-A` / `--all` | すべての成果物を `-o` で指定したディレクトリへ一括出力 |
| `--no-manifest-merge` | `-c` のプロジェクト指定時の manifest 自動マージを無効化 |
| `--list-methods` | 起点メソッド (`Class.method`) の候補一覧を出力 |
| `--seq-depth N` | シーケンス図の再帰トレース深さ (既定 5、0 で無制限) |
| `--no-comments` / `--comment-style note` | JavaDoc 表示を抑制 / 吹き出し形式に切替 |
| `--no-annotations` / `--no-enum-constants` / `--no-final` | クラス図の各装飾を抑制 |
| `-L` / `--legend` / `--no-legend` | 凡例の強制 ON/OFF (既定 ON) |
| `-v` / `--verbose` | パーサ警告と処理サマリーを stderr に出す |
| `-h` / `--help` | ヘルプを表示 |

入力ファイルとして渡せるもの:

* `.java` / `.aidl` ファイル
* Gradle / Android プロジェクトのルートディレクトリ (`build.gradle` / `settings.gradle` / `gradle/libs.versions.toml` を自動検出)
* Kotlin DSL (`*.kts`) もパース対象

自動除外ディレクトリ: `build/`, `.gradle/`, `.idea/`, `.git/`, `out/`, `bin/`, `node_modules/`, `.kotlin/`, `captures/`, `.cxx/`。
テストソース (`src/test/`, `src/androidTest/`) はデフォルト除外。

既知の制約
------------------------------------------------

* Java パーサは制御フローと型の抽出を best-effort で行う簡易実装です。
  Kotlin / Java 21+ の新構文 (record, sealed, pattern matching の一部) は完全には扱えません。
* Gradle DSL は `android { ... }` 直下の代表的な宣言と `dependencies` ブロックを正規表現で
  抽出します。動的構文 (関数呼び出し / 条件分岐) は best-effort 扱いです。
* AIDL は `interface` 宣言とそのメソッドを抽出します。`parcelable` 前方宣言は読み飛ばします。
* PlantUML のキャンバスサイズ上限 (既定 4096×4096) は PNG 出力にのみ作用します。
  GUI プレビューおよび SVG 出力はベクターで描画されるため、この上限の影響を受けません。
  PNG エクスポート時に巨大な図が切り詰められる場合は、SVG エクスポートを利用してください。
* 同梱 PlantUML での SVG 描画は Smetana レイアウトを自動指定するため、Graphviz 不要で
  動作します。Smetana が苦手な巨大グラフは `-o foo.puml` で書き出して、Graphviz を
  別途インストールした上で `plantuml -tsvg foo.puml` を試してください。

ビルド (開発者向け)
------------------------------------------------

リポジトリ同梱の Gradle Wrapper (`./gradlew`) を使うと、ローカルに Gradle が
入っていなくても適切なバージョン (9.4.1) を自動取得してビルドできます。
必要環境は Java 17 以上のみです。

```sh
./gradlew test         # ユニットテスト (約 360 件)
./gradlew check        # テスト + Checkstyle
./gradlew jar          # build/libs/PadTools.jar (依存内包の単体実行可能 jar) を生成
./gradlew makeZip      # 配布用 zip (jar + 起動補助スクリプト) を生成
```

生成された `build/libs/PadTools.jar` は依存ライブラリ (PlantUML / Apache Batik 等)
を同梱した fat jar なので、`java -jar PadTools.jar` を単独で実行できます。
別途 `libs/` ディレクトリを置く必要はありません。

主要パッケージ:

* `padtools.app.uml` — UML 専用 Swing GUI (`UmlMainFrame` / `SvgPreviewPanel` 等)
* `padtools.core.formats.uml` — クラス図 / シーケンス図 / パッケージ図の PlantUML 生成器、Java AST 抽出器
* `padtools.core.formats.android` — Gradle スクリプト / AndroidManifest / Version Catalog のパーサとコンポーネント図 / 依存グラフ生成器
* `padtools.core.formats.java` — Java/AIDL 字句解析、プロジェクトスキャナ

ライセンス
------------------------------------------------
PadTools は **オープンソースソフトウェア** として MIT ライセンスのもとで公開されています。

    Copyright (c) 2015-2026 naou and contributors

    Released under the MIT license (http://opensource.org/licenses/mit-license)

MIT ライセンスは [OSI (Open Source Initiative)](https://opensource.org/licenses/MIT) が
承認したオープンソースライセンスで、商用・非商用を問わず自由に利用・改変・再配布が可能です
(著作権表示とライセンス文の保持が条件)。

なお、同梱の PlantUML は GPLv3 ライセンスです。

リンク
------------------------------------------------
* GitHub [https://github.com/nigoh/padtools](https://github.com/nigoh/padtools)
