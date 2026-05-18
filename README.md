PadTools 1.5
======================================

概要
------------------------------------------------
PadTools はPAD図を活用することを目的として作成された、PAD作成ツールです。
思考を止めず記述できることを目指しています。

1.5 では Java / Android (Gradle + AndroidManifest.xml + AIDL) ソースを入力として、
PAD 図・UML クラス図・UML シーケンス図・Android コンポーネント図・Gradle 依存グラフ・
Markdown サマリーを自動生成する機能を追加しました。

1.6 では PlantUML を同梱し、UML 系出力 (クラス図 / シーケンス図 / コンポーネント図 /
Gradle 依存グラフ) を `-o foo.svg` で直接 SVG として書き出せるようにしました。
`--all` も既定で SVG を出力します (`.puml` / `graphviz` の別途インストール不要)。

動作環境
------------------------------------------------
* Java 17 以上 (JRE / JDK)
* 動作確認: Windows / macOS / Linux

UML 系の SVG 出力はツール本体で完結します (PlantUML 同梱)。PNG / PDF 化や別レイアウト
を試したい場合は `.puml` で書き出して別途 [PlantUML](https://plantuml.com/) を使ってください。

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
# SVG として直接書き出し (PlantUML 同梱、追加インストール不要)
java -jar PadTools.jar -c -o my.svg MyClass.java

# プロジェクト全体 (manifest を自動検出して <<Activity>> 等を付与)
java -jar PadTools.jar -c -J -o app.svg ~/AndroidStudioProjects/MyApp

# 編集したい場合は .puml で書き出す (従来通り)
java -jar PadTools.jar -c -J -o app.puml ~/AndroidStudioProjects/MyApp

# manifest 自動マージを無効化
java -jar PadTools.jar -c -J --no-manifest-merge -o app.svg ~/AndroidStudioProjects/MyApp
```

可視性 `+ - # ~`、`{static}` / `{abstract}` / `{final}` 修飾、継承 `<|--` / 実装 `<|..` / 利用 `-->` 関係、
AAOS パターン (`<<CarManager>>` 等)、AndroidManifest 連携 (`<<Activity>>` `<<Service>>` 等)、
凡例ブロックがデフォルトで出力されます。

JavaDoc / 直前コメント・メンバーアノテーション (`@Nullable` 等)・enum 定数も
デフォルトで描画されます。コメントは既定でインライン形式 (`.. text ..` セパレータ) で、
クラス本体の中に埋め込まれます。

```sh
# コメントを吹き出し (note) 形式に切替
java -jar PadTools.jar -c --comment-style note -o my.svg MyClass.java

# 個別に抑制 (それぞれ単独で指定可能)
java -jar PadTools.jar -c --no-comments --no-annotations \
    --no-enum-constants --no-final -o my.svg MyClass.java
```

### UML シーケンス図 (PlantUML)

```sh
# Class.method を起点に呼び出し関係を抽出 (SVG)
java -jar PadTools.jar -q "MainActivity.onCreate" -o seq.svg MainActivity.java

# プロジェクト全体から特定メソッド (SVG)
java -jar PadTools.jar -q "CarPowerManager.setListener" -o seq.svg ~/AOSP/Car

# .puml で書き出す (編集用)
java -jar PadTools.jar -q "MainActivity.onCreate" -o seq.puml MainActivity.java

# 起点メソッドの候補一覧を表示 (fzf 等と組み合わせて選択)
java -jar PadTools.jar --list-methods ~/AOSP/Car

# 再帰トレースの深さを変更 (デフォルト 5、0 = 無制限)
java -jar PadTools.jar -q "MainActivity.onCreate" --seq-depth 10 -o seq.svg ~/AndroidStudioProjects/MyApp

# Android ライフサイクル (Activity/Service/Receiver/Provider) を起点とする
# シーケンス図を .puml + .svg 両方で一括出力 (-Q / --sequence-diagrams)
java -jar PadTools.jar -Q -o ./seq-out ~/AndroidStudioProjects/MyApp
```

メソッド本体内の `receiver.method()` 呼び出しを participant 間の同期メッセージとして描画。
フィールド型からの receiver 解決にも対応 (例: `mService.foo()` → `IService.foo()`)。
さらに以下も自動で扱います:

- **多段トレース**: 呼び出し先メソッドが入力ソースに含まれていれば、`--seq-depth` 上限まで
  本体に潜って展開。サイクル検出で再帰呼び出しは `note` で打ち切り。
- **制御構造**: `if/else` → `alt` / `else`、単一分岐 `if` → `opt`、`while`/`for`/`do-while`
  → `loop`、`switch` → `alt` (case 列)、`try/catch/finally` → `group/else catch/else finally`、
  `synchronized` → `critical`。

エディタからは「シーケンス図を生成」(Ctrl+Q) で起動すると、入力を解析して
**メソッド候補リスト (絞り込み付き)** から起点を選択するダイアログが開きます。

### Android コンポーネント図 (PlantUML)

```sh
# SVG (推奨)
java -jar PadTools.jar -d -o components.svg ~/AndroidStudioProjects/MyApp

# 編集用 .puml
java -jar PadTools.jar -d -o components.puml ~/AndroidStudioProjects/MyApp
```

AndroidManifest.xml の `<activity>` / `<service>` / `<receiver>` / `<provider>` を
ステレオタイプ付きコンポーネントとして表示。`exported=true` は色付け、ランチャー Activity は
強調表示、intent-filter の action ノードからの矢印で関係を可視化。uses-permission も
別パッケージに表示。

### Gradle 依存グラフ (PlantUML)

```sh
# SVG (推奨)
java -jar PadTools.jar -G -o deps.svg ~/AndroidStudioProjects/MyApp

# 編集用 .puml
java -jar PadTools.jar -G -o deps.puml ~/AndroidStudioProjects/MyApp
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

### 一括出力 (All-in-one)

```sh
# プロジェクトを指定して全種類を出力ディレクトリに書き出す (SVG + md)
java -jar PadTools.jar --all -o ./out ~/AndroidStudioProjects/MyApp
```

出力ディレクトリ (`-o` で指定、無ければ自動作成) には以下が生成されます:

| ファイル | 内容 |
|---|---|
| `summary.md` | Markdown プロジェクトサマリー |
| `class-diagram.svg` | UML クラス図 (manifest 自動マージ) |
| `component-diagram.svg` | Android コンポーネント図 |
| `dependency-graph.svg` | Gradle 依存グラフ |
| `pad.svg` | Java → PAD 図 (統合) |
| `methods.txt` | シーケンス図の起点候補一覧 (`Class.method`) |
| `sequence-diagrams/` | Android ライフサイクル (Activity の `onCreate`/`onResume` 等、Service の `onStartCommand` 等) を起点としたシーケンス図群 (起点ごとに `.puml` と `.svg` を併出力) |

SVG は同梱ライブラリのみで描画するため、PlantUML や graphviz の追加インストールは不要です。
ライフサイクル外のメソッドからシーケンス図を作りたい場合は `methods.txt` を参考に
`-q Class.method -o seq.svg` で個別生成できます。

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
* **シーケンス図を一括出力 (ライフサイクル)** — Android プロジェクトを選んで、Activity/Service 等のライフサイクル起点シーケンス図を `.puml` + `.svg` で書き出す
* **Android コンポーネント図を生成** (Ctrl+D) — Android プロジェクトから
* **Gradle 依存グラフを生成** — Android プロジェクトから
* **プロジェクトサマリーを生成** — Markdown レポート
* **プロジェクト全体を一括出力 (All-in-one)** — プロジェクトと出力先を選択して 5 種類の成果物を書き出す

### CLI オプション一覧

| オプション | 説明 |
|---|---|
| `-o FILE` | 出力先ファイル (拡張子で形式判別: `.spd` / `.png` / `.svg` / `.pdf` / `.puml` / `.md` / `.txt`)。UML 系 (`-c` / `-q` / `-d` / `-G`) も `.svg` で書ける |
| `-s SCALE` | 画像出力の拡大率 (例: `2.0`) |
| `-j` / `--java` | 入力を Java ソースとして扱う |
| `-J` / `--java-project` | 入力を Gradle/Android プロジェクトディレクトリとして扱う |
| `-c` / `--class-diagram` | PlantUML クラス図を生成 |
| `-q CLASS.METHOD` / `--sequence-diagram CLASS.METHOD` | PlantUML シーケンス図を生成 |
| `-Q` / `--sequence-diagrams` | Android プロジェクトのライフサイクル起点シーケンス図を `-o` ディレクトリへ一括出力 (`.puml` + `.svg`) |
| `-d` / `--component-diagram` | PlantUML Android コンポーネント図を生成 |
| `-G` / `--dependency-graph` | PlantUML Gradle 依存グラフを生成 |
| `-g` / `--gradle` | Gradle ファイル単体を Markdown サマリー化 |
| `-m` / `--manifest` | AndroidManifest.xml 単体を Markdown サマリー化 |
| `--summary` | プロジェクト全体を Markdown サマリー化 |
| `-A` / `--all` | 5 種類すべての成果物を `-o` で指定したディレクトリへ一括出力 |
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
* 同梱 PlantUML での SVG 描画は Smetana レイアウトを自動指定するため、Graphviz 不要で
  動作します。Smetana が苦手な巨大グラフは `-o foo.puml` で書き出して、Graphviz を
  別途インストールした上で `plantuml -tsvg foo.puml` を試してください。

ビルド (開発者向け)
------------------------------------------------

リポジトリ同梱の Gradle Wrapper (`./gradlew`) を使うと、ローカルに Gradle が
入っていなくても適切なバージョン (9.4.1) を自動取得してビルドできます。
必要環境は Java 17 以上のみです。

```sh
./gradlew check        # テスト + Checkstyle
./gradlew jar          # build/libs/PadTools.jar (依存内包の単体実行可能 jar) を生成
./gradlew makeZip      # 配布用 zip (jar + 起動補助スクリプト) を生成
```

生成された `build/libs/PadTools.jar` は依存ライブラリ (Apache Batik 等)
を同梱した fat jar なので、`java -jar PadTools.jar` を単独で実行できます。
別途 `libs/` ディレクトリを置く必要はありません。

Gradle 8.x (8.14+) でも同じ `build.gradle` で動作します。

ライセンス
------------------------------------------------
    Copyright (c) 2015-2018 naou

    Released under the MIT license(http://opensource.org/licenses/mit-license)

リンク
------------------------------------------------
* GitHub [https://github.com/knaou/padtools](https://github.com/knaou/padtools)
* 公開サイト [https://knaou.github.io/padtools/](https://knaou.github.io/padtools/)
    * SPD 構文・GUI の利用法は公開サイトを参照
