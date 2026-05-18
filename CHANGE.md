Change log
=============

Unreleased
--------

* **クラス図コメントの色付け** (`PlantUmlClassDiagram`)
    * インラインコメント (`.. text ..`) を `<color:#008800>...</color>` で囲み、クラス本体のメンバーと視覚的に区別できるようにした
    * NOTE スタイルでは `skinparam noteBorderColor` / `skinparam noteFontColor` を自動付与し、注釈ブロックの枠線と文字色を同色に揃える
    * `Options.commentColor` で色を変更でき、空文字を指定すれば従来通り色なしで出力する
* **GUI プレビューをベクター SVG 化** (`PlantUmlSvgRenderer` + `SvgPreviewPanel`)
    * PlantUML 出力を PNG ではなく SVG として描画し、Apache Batik (`batik-bridge`)
      で `GraphicsNode` に変換して `SvgPreviewPanel` 上で直接ペイントする
    * PlantUML の PNG キャンバス 4096x4096 制約に縛られなくなり、巨大な
      クラス図でも切り詰められずに表示できる
    * ズーム時もアンチエイリアスを保ったまま再描画される
    * PNG エクスポートは保存時に `PlantUmlImageRenderer` で再生成する経路に変更
      (プレビューは常にベクターのみを保持)

2.0 (UML-only pivot)
--------

* **PadTools を「Java + Android + Gradle 特化の UML ツール」へ完全転換**
    * 旧 PAD (Problem Analysis Diagram) GUI / SPD パーサ / Java→PAD 変換器を全削除 (約 9.7k LoC 減)
    * 新規 UML 専用 Swing GUI を導入 (`padtools.app.uml.*`)
        * メニュー: File (Open Project / Save Diagram As... / Exit) / Diagram (5 図種ラジオ + シーケンス図起点選択) / View (Zoom In/Out/Reset/Fit) / Help
        * 左ペイン: プロジェクトのモジュール / パッケージ / クラス ツリー (`ProjectTreePanel`)
        * 右ペイン: タブ式の Preview (ズーム/パン付き `SvgPreviewPanel`) と PlantUML Source (`PumlSourcePanel`)
        * ステータスバーにズーム倍率と解析サマリを表示
    * 起動: 引数なし `java -jar PadTools.jar` で UML GUI が直接起動
        * プロジェクトディレクトリを引数で渡せば初期解析
        * 旧 `-j` / `-J` / `-s` (Java→PAD) は廃止
* **新規 UML 図種: パッケージ図** (`PlantUmlPackageDiagram`)
    * パッケージごとのクラス数をボックスで表示し、継承 / 実装 / フィールド型を経由したパッケージ間の参照矢印を集約
* **シーケンス図起点選択ダイアログ**
    * 候補メソッド一覧 + サブストリング絞り込みフィールド
    * Diagram → Choose Sequence Entry... から呼び出し
* **PNG 直接プレビュー**
    * 同梱 PlantUML の PNG 出力経由で `BufferedImage` 化 (Apache Batik を経由しない)
    * SVG エクスポート時のみ `PlantUmlRenderer.renderSvg` を呼ぶ
* **エクスポート機能** (`UmlExporter`)
    * SVG / PNG / PUML の各形式に対応した一元的な保存 API
    * File → Save Diagram As... から拡張子フィルタで切替
* **CLI 整理**
    * 残オプション: `-c -q -d -G -g -m -A -Q --summary --list-methods --seq-depth` ほか UML 系すべて
    * `--all` の `pad.svg` ステップを廃止し 7 → 6 ステップに

1.7
--------

* エディタの「シーケンス図を生成」を `.puml + .svg` のファイル出力に変更
    * 従来はエディタ本文に PlantUML テキストを流し込んでいたが、SPD パーサで描画できず実用しづらかった
    * 起点メソッド選択後に保存ダイアログを出し、`.puml` と同名の `.svg` を同一ディレクトリに書き出す
    * メニュー表記を `シーケンス図を出力 (.puml + .svg)` に変更し、一括出力版と挙動を統一
* クラス図の表現力を強化 (`-c`)
    * **JavaDoc / 直前コメントの取り込み**
        * `/** ... */` ・連続する `// ...` を、直後のクラス/フィールド/メソッドに割り当て
        * 既定はインライン表示 (`.. text ..` セパレータ) で先頭 1 行を出す
        * `--comment-style note` で `note top of` / `note right of Cls::member` 方式に切替
        * `--no-comments` でコメント出力を抑制
    * **enum 定数の表示**
        * `enum E { A, B, C }` の定数を本体内に列挙
        * メンバーが共存する場合は PlantUML の `--` 区切りを自動挿入
        * `--no-enum-constants` で抑制
    * **アノテーション表示**
        * フィールド/メソッドのアノテーション (`@Nullable`, `@Deprecated` 等) を可視化
        * ノイズになりがちな `@Override` / `@SuppressWarnings` は既定で非表示
        * `--no-annotations` で完全に抑制
    * **`final` フィールドのマーキング**
        * `{final}` マーカーを PlantUML 出力に追加
        * `--no-final` で抑制
    * **凡例の自動拡張**
        * 上記が出現したダイアグラムには「メンバー修飾」「注釈」セクションを自動追加

1.6
--------

* PlantUML シーケンス図の出力機能を強化
    * `-Q` / `--sequence-diagrams` を追加: Android プロジェクトを入力に、Activity/Service/Receiver/Provider のライフサイクル起点シーケンス図を `.puml` と `.svg` の両方で `-o` ディレクトリへ一括出力
    * `--all` の `sequence-diagrams/` も `.puml` と `.svg` を併出力 (従来は SVG のみ)
    * エディタの「シーケンス図を一括出力 (ライフサイクル, .puml + .svg)」メニューから GUI でも実行可能
* UML 系 (クラス図 / シーケンス図 / コンポーネント図 / Gradle 依存グラフ) を
  SVG として直接書き出せるようにした
    * PlantUML (`net.sourceforge.plantuml:plantuml`) を同梱
    * `-o foo.svg` を指定するとツール単体で SVG を出力
    * `--all` の既定成果物を `.svg` に変更
      (`class-diagram.svg` / `component-diagram.svg` / `dependency-graph.svg` /
       `pad.svg` + `summary.md`)
    * Graphviz/dot を必要としないよう Smetana レイアウトを自動指定
      (`!pragma layout smetana` を `@startuml` 直後に自動挿入)
    * 従来の `.puml` テキスト出力は互換維持 (拡張子で切替)
* シーケンス図 (`-q`) を強化
    * 多段トレース: 呼び出し先メソッドが入力ソースに含まれていれば本体に再帰的に潜って展開 (デフォルト深さ 5、`--seq-depth N` で調整、0 で無制限)。サイクル検出付き
    * 制御構造: `if/else` → `alt/else`、単一分岐 `if` → `opt`、`while`/`for`/`do-while` → `loop`、`switch` → `alt` (case 列)、`try/catch/finally` → `group/else catch/else finally`、`synchronized` → `critical`
    * `--list-methods` オプションを追加: 入力ソース内のメソッドを `Class.method` 形式で列挙 (fzf 等で起点選択する用)
    * GUI のシーケンス図生成ダイアログを、テキスト入力から **候補リスト + 絞り込みフィールド** に変更
    * `--all` の出力に `methods.txt` (候補一覧) と `sequence-diagrams/` (Activity/Service ライフサイクル起点のシーケンス図群) を追加
* 動作対象 Java を 17 以上に引き上げ
    * `sourceCompatibility` / `targetCompatibility` を 17 に変更
    * Apache Batik を 1.14 → 1.17 へ更新 (Java 17 互換性問題 BATIK-1260 解消)
    * Checkstyle を 10.12.5 → 10.21.1 へ更新
    * 未使用依存 (`org.jfree:jfreesvg`) を除去
* ビルドシステムを Gradle 8.x / 9.x 両対応に
    * `plugins {}` ブロック + `java {}` ブロック方式へ書き換え
    * Task の lazy registration (`tasks.register`) へ移行
    * Gradle Wrapper (9.4.1) をリポジトリ同梱
* `PadTools.jar` を fat jar 化
    * 依存ライブラリ (Batik 等) を jar 内に同梱し、`java -jar PadTools.jar` 単独で動作可能に
    * 配布 zip も `libs/` ディレクトリ無しのフラット構成に変更

1.5
--------

* Java / Android ソースを入力とした自動図生成機能を追加
    * `-j` / `-J`: Java ソース / Gradle プロジェクトから PAD 図を生成
    * `-c`: Java/AIDL から PlantUML クラス図を生成
        * AAOS パターン (`<<CarManager>>` 等) 認識
        * AndroidManifest.xml 自動マージ (`<<Activity>>` 等)
    * `-q`: 指定メソッドから PlantUML シーケンス図を生成
    * `-d`: AndroidManifest.xml から PlantUML コンポーネント図を生成
    * `-G`: build.gradle / settings.gradle から PlantUML Gradle 依存グラフを生成
    * `-g` / `-m` / `--summary`: Gradle / Manifest / プロジェクト全体の Markdown サマリー
* Gradle Version Catalog (`gradle/libs.versions.toml`) 自動解析
    * `alias(libs.plugins.X)` を正規プラグイン ID に解決
    * `implementation(libs.X.Y)` を実 notation に解決
    * `libs.versions.X.get().toInt()` を整数値に解決
* AIDL ファイル (`.aidl`) パース対応
* エディタにファイルメニュー追加
    * Java からインポート、クラス図/シーケンス図/コンポーネント図/依存グラフ/サマリー生成
* `-v` / `--verbose` でパーサ警告を stderr に出力
* 凡例ブロック追加 (`-L` で PAD 図 ON、`--no-legend` で UML 図 OFF)

1.4
--------

* 利用ライブラリのバージョンをアップデート

* bugfix
    * SVG出力のバグを修正
    * 前提Javaバージョンを 1.8 に変更(ドキュメント上は1.8前提としていたが、一部設定などが1.7となっていた)

1.3
--------

* フォント及び色の指定機能の実装(PR:https://github.com/knaou/padtools/pull/5)
* bugfix
    * https://github.com/knaou/padtools/pull/5

1.2
--------

* SVG 形式で出力する機能の実装
    * Apache Batik を利用

1.1
--------

* 簡単なリファクタリング
* いくつかのbugfix
* 設定ファイルのサポート
    * ツールバー無効化
    * 「保存」メニュー・ボタンの無効化
* タイトルの改善
    * 新規の場合は NEW, ファイルと紐付いている場合はファイル名を表示
    * 保存すべき変更点がある場合は、タイトルに「*」(アスタリスク)を付与
* Win/Unix系OSのためのラッパを用意
    * Win向けには exe (GUI版とconsole版）(Launch4j を利用)
    * Unix系向けには shスクリプト
* エディタ部分で右クリックメニューを有効化
* 新規作成や保存にショートカットキーを割当
* エディやコンバータエントリポイントを統合化
    * -o オプションを使用すると、エディタを起動せず変換（コンバート）のみを行う
    * 例)
        * PadTools_consoiile.exe -- -o pad.png -s 2 pad.spd
        * PadTools.sh -o pad.png -s 2 pad.spd


1.0
---------

* 初期バージョン
* PAD図描画に関する基本機能の提供
