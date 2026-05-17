PadTools 1.5
======================================

概要
------------------------------------------------
PadTools はPAD図を活用することを目的として作成された、PAD作成ツールです。
思考を止めず記述できることを目指しています。

1.5 では Java / Android (Gradle + AndroidManifest.xml + AIDL) ソースを入力として、
PAD 図・UML クラス図・UML シーケンス図・Android コンポーネント図・Gradle 依存グラフ・
Markdown サマリーを自動生成する機能を追加しました。

動作環境
------------------------------------------------
* Java 11 以上 (JRE / JDK)
* 動作確認: Windows / macOS / Linux

PlantUML 形式の出力 (`.puml`) は別途 [PlantUML](https://plantuml.com/) で PNG / SVG / PDF 化できます。

使い方
------------------------------------------------

### 起動

```sh
# GUI エディタを起動
java -jar PadTools.jar

# 既存の SPD ファイルを開いて起動
java -jar PadTools.jar pad.spd
```

GUI エディタの基本操作・SPD 構文は公開サイト (https://knaou.github.io/padtools/) を参照してください。

### SPD から画像出力 (従来機能)

```sh
# PNG 出力
java -jar PadTools.jar -o pad.png pad.spd

# SVG / PDF
java -jar PadTools.jar -o pad.svg pad.spd
java -jar PadTools.jar -o pad.pdf pad.spd

# 拡大率指定
java -jar PadTools.jar -s 2.0 -o pad.png pad.spd
```

### Java ソースから PAD 図を生成 (1.5 新機能)

```sh
# Java ファイルを SPD に変換 (標準出力)
java -jar PadTools.jar -j MyActivity.java

# SPD ファイルに保存
java -jar PadTools.jar -j -o out.spd MyActivity.java

# Java ファイルから直接 PNG / SVG / PDF を生成
java -jar PadTools.jar -j -o pad.png MyActivity.java

# 標準入力から
cat MyActivity.java | java -jar PadTools.jar -j -o pad.png
```

対応する Java 構文: `if/else`、`while`、`do-while`、`for` (古典 / 拡張)、`switch/case`、
`try/catch/finally` (try-with-resources 含む)、`return` / `throw` / `break` / `continue`、
`synchronized`、ラベル文。

### Gradle プロジェクト全体を PAD 化

```sh
# プロジェクトディレクトリ配下の全 .java を再帰的に変換
java -jar PadTools.jar -J -o all.spd ~/AndroidStudioProjects/MyApp

# 警告を含めて表示
java -jar PadTools.jar -J -v -o all.spd ~/AndroidStudioProjects/MyApp
```

自動除外: `build/`, `.gradle/`, `.idea/`, `.git/`, `out/`, `bin/`, `node_modules/` 等。
テストソース (`src/test/`, `src/androidTest/`) はデフォルト除外、必要なら走査オプションで含められます。

### UML クラス図 (PlantUML)

```sh
# 単一 Java/AIDL ファイル
java -jar PadTools.jar -c MyClass.java > my.puml

# プロジェクト全体 (manifest を自動検出して <<Activity>> 等を付与)
java -jar PadTools.jar -c -J -o app.puml ~/AndroidStudioProjects/MyApp

# manifest 自動マージを無効化
java -jar PadTools.jar -c -J --no-manifest-merge -o app.puml ~/AndroidStudioProjects/MyApp

# PlantUML で画像化
plantuml -tpng app.puml   # app.png が生成される
```

可視性 `+ - # ~`、`{static}` / `{abstract}` 修飾、継承 `<|--` / 実装 `<|..` / 利用 `-->` 関係、
AAOS パターン (`<<CarManager>>` 等)、AndroidManifest 連携 (`<<Activity>>` `<<Service>>` 等)、
凡例ブロックがデフォルトで出力されます。

### UML シーケンス図 (PlantUML)

```sh
# Class.method を起点に呼び出し関係を抽出
java -jar PadTools.jar -q "MainActivity.onCreate" -o seq.puml MainActivity.java

# プロジェクト全体から特定メソッド
java -jar PadTools.jar -q "CarPowerManager.setListener" -o seq.puml ~/AOSP/Car
```

メソッド本体内の `receiver.method()` 呼び出しを participant 間の同期メッセージとして描画。
フィールド型からの receiver 解決にも対応 (例: `mService.foo()` → `IService.foo()`)。

### Android コンポーネント図 (PlantUML)

```sh
java -jar PadTools.jar -d -o components.puml ~/AndroidStudioProjects/MyApp
plantuml -tpng components.puml
```

AndroidManifest.xml の `<activity>` / `<service>` / `<receiver>` / `<provider>` を
ステレオタイプ付きコンポーネントとして表示。`exported=true` は色付け、ランチャー Activity は
強調表示、intent-filter の action ノードからの矢印で関係を可視化。uses-permission も
別パッケージに表示。

### Gradle 依存グラフ (PlantUML)

```sh
java -jar PadTools.jar -G -o deps.puml ~/AndroidStudioProjects/MyApp
plantuml -tpng deps.puml
```

モジュール (`<<application>>` / `<<library>>` / `<<module>>`) とその間の `project(':x')` 依存、
外部 Maven ライブラリ (`<<external>>`) を矢印で表示。`gradle/libs.versions.toml` を自動検出して
`libs.X.Y` 参照を実 notation (`group:name:version`) に解決します。

### Markdown プロジェクトサマリー

```sh
java -jar PadTools.jar --summary -o report.md ~/AndroidStudioProjects/MyApp
```

以下を 1 つの Markdown レポートにまとめます:
- モジュール構成 (settings.gradle の include)
- 各モジュールの applicationId / namespace / compileSdk / minSdk / targetSdk / versionCode / versionName / plugins
- buildTypes / productFlavors / signingConfigs (機密値は伏字)
- 依存ライブラリ一覧
- AndroidManifest コンポーネント (Activity / Service / Receiver / Provider と intent-filter)
- uses-permission / uses-feature

### Gradle / Manifest 単体解析

```sh
# build.gradle 1 つを Markdown サマリーに
java -jar PadTools.jar -g -o gradle.md ./app/build.gradle

# AndroidManifest.xml 1 つを Markdown サマリーに
java -jar PadTools.jar -m -o manifest.md ./app/src/main/AndroidManifest.xml
```

### エディタからの操作

ファイルメニューから以下を呼び出せます:

* **Java からインポート** (Ctrl+J) — `.java` を開いて SPD を編集領域に流し込む
* **Gradle プロジェクトから作成** — プロジェクトディレクトリを選択して PAD 化
* **クラス図を生成** — Java/AIDL ファイルまたはディレクトリから PlantUML クラス図
* **シーケンス図を生成** — Class.method を入力して PlantUML シーケンス図
* **Android コンポーネント図を生成** (Ctrl+D) — Android プロジェクトから
* **Gradle 依存グラフを生成** — Android プロジェクトから
* **プロジェクトサマリーを生成** — Markdown レポート

### CLI オプション一覧

| オプション | 説明 |
|---|---|
| `-o FILE` | 出力先ファイル (拡張子で形式判別: `.spd` / `.png` / `.svg` / `.pdf` / `.puml` / `.md` / `.txt`) |
| `-s SCALE` | 画像出力の拡大率 (例: `2.0`) |
| `-j` / `--java` | 入力を Java ソースとして扱う |
| `-J` / `--java-project` | 入力を Gradle/Android プロジェクトディレクトリとして扱う |
| `-c` / `--class-diagram` | PlantUML クラス図を生成 |
| `-q CLASS.METHOD` / `--sequence-diagram CLASS.METHOD` | PlantUML シーケンス図を生成 |
| `-d` / `--component-diagram` | PlantUML Android コンポーネント図を生成 |
| `-G` / `--dependency-graph` | PlantUML Gradle 依存グラフを生成 |
| `-g` / `--gradle` | Gradle ファイル単体を Markdown サマリー化 |
| `-m` / `--manifest` | AndroidManifest.xml 単体を Markdown サマリー化 |
| `--summary` | プロジェクト全体を Markdown サマリー化 |
| `--no-manifest-merge` | `-c -J` 時の manifest 自動マージを無効化 |
| `-L` / `--legend` | 凡例を強制 ON (PAD 図は既定 OFF) |
| `--no-legend` | 凡例を強制 OFF (UML 図は既定 ON) |
| `-v` / `--verbose` | パーサ警告と処理サマリーを stderr に出す |
| `-h` / `--help` | ヘルプを表示 |

### 既知の制約

* Java パーサは制御フローと型の抽出を best-effort で行う簡易実装です。
  Kotlin / Java 21+ の新構文 (record, sealed, pattern matching の一部) は完全には扱えません。
* Gradle DSL は `android { ... }` 直下の代表的な宣言と `dependencies` ブロックを正規表現で
  抽出します。動的構文 (関数呼び出し / 条件分岐) は best-effort 扱いです。
* AIDL は `interface` 宣言とそのメソッドを抽出します。`parcelable` 前方宣言は読み飛ばします。
* PlantUML のキャンバスサイズ上限 (既定 4096×4096) を超える巨大なクラス図は切り詰められる
  ことがあります。これは PlantUML 側の制約で、本ツールのテキスト出力自体は完全です。

ライセンス
------------------------------------------------
    Copyright (c) 2015-2018 naou

    Released under the MIT license(http://opensource.org/licenses/mit-license)

リンク
------------------------------------------------
* GitHub [https://github.com/knaou/padtools](https://github.com/knaou/padtools)
* 公開サイト [https://knaou.github.io/padtools/](https://knaou.github.io/padtools/)
    * SPD 構文・GUI の利用法は公開サイトを参照
