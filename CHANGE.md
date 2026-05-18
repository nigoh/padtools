Change log
=============

1.6
--------

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
