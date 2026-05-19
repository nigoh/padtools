Change log
=============

Unreleased
--------

* **「関数を変数として設定するメンバー変数」の解析範囲を拡張** (`JavaStructureExtractor` / `JavaMethodInfo.Call` / `PlantUmlClassDiagram`)
    * これまで匿名クラス/ラムダによるフィールド初期化子のみ inline 解析していたのを、以下のパターンにも拡張
        * メソッド参照 (`Runnable r = Foo::bar;` / `a.b.c::method`) を `inlineMethods` に取り込む
        * コンストラクタや任意メソッド内の `this.field = new Listener() {...}` / `this.field = () -> ...` 形式の代入を捕捉し、対応するフィールドの `inlineMethods` に紐づける (宣言順より前で代入されても遅延解決パスでマッチ)
        * メソッド本体内の `view.setOnXxxListener(new ... {...})` や `view.setOnXxxListener(v -> ...)` を `JavaMethodInfo.Call.inlineMethods` (新規) に取り込み、リスナー登録呼び出しのコールバック本体を構造化保持する
        * 未知の SAM 型でも `Listener` / `Handler` / `Callback` / `Observer` / `Action` サフィックスをヒューリスティクスで剥がして SAM メソッド名を推定 (`MyCustomListener` → `myCustom`)
    * クラス図に `showInlineFunctions`（既定 true）を追加し、本機能で捕捉した inline メソッドを `.. fieldName: Type ..` 区切り線の下に列挙
    * シーケンス図側は既存の `findInlineMethod` が `JavaFieldInfo.inlineMethods` を参照するため、新たに取り込んだ代入/メソッド参照もそのまま展開対象になる
    * `parseLambdaExpressionBody` が呼び出し引数中の expression-bodied ラムダで外側 `)` / `,` を食い潰していた不具合を併せて修正
    * 目的: Android / Java で頻出する「フィールドにリスナーをセットする」「コンストラクタでハンドラを差し込む」「`setOnClickListener(v -> ...)` を書く」コードがクラス図・シーケンス図でブラックボックスにならないようにする

* **共通クラス図 (Common Classes Diagram) を追加 + GUI ツールバー導入** (`PlantUmlCommonClassesDiagram` 新規 / `DiagramKind.COMMON` / `DiagramService` / `UmlMainFrame`)
    * 新図種「Common Classes」: プロジェクト内のクラス群を走査し、他クラスから参照される回数 (fan-in) が多い「共通 (= 使い回されている) クラス」を上位 N 件 (既定 20) でハイライト表示する。参照種別は `extends` / `implements` / フィールド型 / メソッド引数型 / 戻り値型を集計し、自己参照は除外、外部ライブラリ (`java.*` / `android.*` / `kotlin.*` 等 + `Origin.EXTERNAL_JAR/MISSING_JAR`) も既定で集計対象外
    * 各ハブクラスは `<<common>>` ステレオタイプ + 黄背景で強調し、ラベルに `N refs` を併記。参照元クラスは破線矢印 `referrer ..> hub : uses` で接続 (`referrersPerClass` で上限制御、既定 5)
    * `DiagramService` に `case COMMON` を追加し、既存の `DiagramScope` フィルタ (パッケージ / モジュール / 正規表現 / seed+hop) と詳細昇格 (`ClassIndex.detail`) を流用
    * **ウィンドウ上部にツールバーを新設**: 既存メニューとショートカットは維持したまま、頻用操作をボタンとして可視化
        * 上段 (Action ツールバー): `Open` / `Save` / `Refresh` / `Back` / `Search` / `Scope` / `Clear Scope` / `Zoom In` / `Zoom Out` / `100%` / `Fit`
        * 下段 (Diagram トグル): `Class` / `Package` / `Sequence` / `Activity` / `Common` / `Component` / `Dependency` / `Manifest` / `Layout` を `JToggleButton` + `ButtonGroup` で配置し、Diagram メニューのラジオ選択と双方向同期 (`ItemListener` 経由)
        * `Sequence` / `Activity` / `Layout` ボタンは追加入力が未指定なら起点選択ダイアログを自動で開く
    * テスト: `PlantUmlCommonClassesDiagramTest` (10 ケース: fan-in 集計 / minReferences フィルタ / interface 実装 / 自己参照除外 / 外部ライブラリ除外 / topN 上限) と `DiagramServiceTest.testCommonClassesDiagram` を追加
    * 目的: AOSP 級プロジェクトでも「実際に共有されている中核クラス」を一目で把握できるようにし、頻用操作をメニュー潜り無しでクリックひとつで起動できるようにする

* **クラス・メソッド・フィールド横断検索ダイアログを追加** (`EntitySearchDialog` 新規 / `UmlMainFrame`)
    * Diagram メニュー → `Search Entities...` (アクセラレータ `Ctrl+Shift+F`) で開くモーダル。クラス・メソッド・フィールドを 1 つの部分一致検索で横断的に絞り込み、Kind チェックボックスで種別を ON/OFF できる
    * 選択結果に応じて: クラス → seed+1hop でクラス図に切り替え / メソッド → シーケンス図を生成 / フィールド → 所属クラス seed+1hop でクラス図 (フィールド型まで含む)
    * フィールドが匿名クラス/ラムダの初期化子を持つ場合は `[+inline]` バッジで可視化
    * ヘッドレス環境 (CI) でも検証できるよう、`EntitySearchDialog.filter(classes, query)` の静的ヘルパを提供 (Swing インスタンスを生成しない)
    * 目的: AOSP 級プロジェクトでも目的のクラス/メソッド/フィールドへ即座にジャンプし、対応する UML を表示できるようにする
* **フィールド宣言時の匿名クラス/ラムダ本体をシーケンス図に展開** (`JavaStructureExtractor` / `JavaFieldInfo` / `PlantUmlSequenceDiagram`)
    * これまで `parseFieldDecl` は `=` の後を `skipUntilSemicolonRespectingBlocks()` で捨てており、`private OnClickListener l = new OnClickListener() { onClick() { ... } };` の本体が解析対象外だった
    * `JavaFieldInfo.inlineMethods` を追加し、匿名クラス本体を `parseClassBody` で再帰解析して取り込む。ラムダ (`() -> body` / `args -> expr`) は SAM 名 (Runnable→run, OnClickListener→onClick, Consumer→accept など 20 種) を組み込みマップで解決、未知の SAM は `<inline>` で fallback
    * `PlantUmlSequenceDiagram.emitCall` の `nextCls == null` フォールバックに `findInlineMethod` を追加。フィールド経由の呼び出し (例: `listener.onClick()`) に対し、解決済みフィールド型の participant をアクティベートしつつ inline body を walk する
    * expression-bodied ラムダ専用の `parseLambdaExpressionBody` を追加 (フィールド終端 `;` を消費しないため安全)
    * 目的: Android UI コードで頻出するリスナー登録パターンが、シーケンス図でブラックボックスにならないようにする
* **依存 JAR/AAR の外部クラスを participant に表示** (`DependencyJarIndex` / `ExternalClassReader` 新規、ASM 9.7 を追加)
    * Gradle 依存宣言 (`implementation 'androidx.appcompat:appcompat:1.7.0'`) から `~/.gradle/caches/modules-2/files-2.1/...` および `~/.m2/repository/...` を再帰探索して JAR/AAR を発見。AAR は内部 `classes.jar` をメモリ展開
    * ASM `ClassReader` (SKIP_CODE / SKIP_DEBUG / SKIP_FRAMES) で各 `.class` のヘッダ (FQN・superclass・interfaces・public methods・public fields) を取り出して `JavaClassInfo` に変換。`Origin.EXTERNAL_JAR` で印付け
    * 起動時には ZipEntry 名のカタログだけを構築し、実際の `.class` 読み込みは `resolve(name)` 呼び出し時の lazy 評価。`ConcurrentHashMap` で並列パース耐性
    * シーケンス図/クラス図で外部クラスは `<<external>>` ステレオタイプ、解決できなかった依存先は `<<missing>> #LightYellow` 警告マーカーで描画 (`PlantUmlSequenceDiagram` / `PlantUmlClassDiagram.stereotype` / `PlantUmlClassLegend.emitOrigins`)
    * `UmlMainFrame` のステータスバーに「N dependency(ies) not resolved」を追記し、JAR が見つからない原因を可視化
    * `PlantUmlSequenceDiagram` 本体のサイズ縮小のため、コメント note 出力を `PlantUmlSequenceComments` に切り出し
    * 目的: `extends AppCompatActivity` のような外部 SDK クラスがクラス図/シーケンス図上で「不在」にならないようにし、依存 JAR が無いプロジェクトでは明示的に警告マーカーで知らせる
* **CLI / GUI: PlantUML レンダリング失敗を検出して救出 puml にフォールバック** (`PlantUmlRenderer` / `Main` / `UmlMainFrame`)
    * `PlantUmlRenderer.renderSvg` がレンダリング結果に同梱 PlantUML のフォールバック マーカー (`"An error has occured"` / `"I love it when a plan comes together"`) を検出した場合、新例外 `PlantUmlRenderFailedException` を投げる。これまでは PlantUML 内部で Smetana の `IllegalStateException` が握り潰され、エラー画像 SVG がそのまま `dependency-graph.svg` 等として保存されていた
    * **CLI `--all`**: 各図 (component / manifest / deeplink / dependency / class) のレンダ失敗を個別に補足し、`<name>.puml` を同じ出力ディレクトリに書き出し、`[padtools]     -> X.svg FAILED: ...` を stderr に出して次の図に進む。`--all` 全体は他の図の出力を続行
    * **CLI 単体 (`-c` / `-d` / `-G` / `-M` / `-D`)**: SVG レンダ失敗時に隣接 `.puml` を書き出し、終了コード 2 で終了
    * **GUI `UmlMainFrame`**: SwingWorker のレンダ失敗時、これまでの巨大な base64 を含む `JOptionPane` モーダル ダイアログを廃止。ステータス バーに `<Diagram>: rendering failed — PlantUML layout error (Smetana). See 'PlantUML Source' tab.` を出し、`PlantUML Source` タブに raw puml を残し、Preview ペインをクリアする
    * **Smetana の stderr ノイズ抑制**: `UNSURE_ABOUT: safe_list_append(...)` 等の同梱 Smetana が直接 `System.err` に書くデバッグ ログを、`renderSvg` 呼び出し中だけ捨てるラッパを追加。`-v` (`--verbose`) で抑制を解除可能
    * 目的: `car_app_library` 規模 (~26 ノード) の Gradle 依存グラフで Smetana が落ちた際に、ユーザに壊れた SVG を掴ませず、再レンダリング可能な PUML テキストを救出経路として残す。GUI でも意味のないモーダル ダイアログを廃止して UX を改善する

* **CLI 引数パーサが先頭引数を消費していたバグを修正** (`Main` / `bundle/PadTools.sh`)
    * 旧 `optParser.parse(args, 1)` が常に `args[0]` をスキップしていたため、`java -jar PadTools.jar -c -o out.svg in.java` のように README 記載どおりに直接呼ぶと `-c` が黙って消費されて GUI モードに落ちていた。同梱 `bundle/PadTools.sh` が `java -jar PadTools.jar -- $@` で `--` を args[0] に注入する設計に依存していた
    * `Main.java` の `parse(args, 1)` を `parse(args)` に変更。`bundle/PadTools.sh` を `java -jar "$DIR/PadTools.jar" "$@"` に変更し、`--` 注入を撤去 (`OptionParser.parse` の raw モードに意図せず入って後続オプションが全て位置引数化する事故を防止)
    * `MainCliTest.java` 全 11 箇所の先頭ダミー `"padtools"` を除去
    * 目的: README の CLI 例がドキュメントどおりに動くようにする

* **左ペインのプロジェクトツリーが左クリックに反応するように修正** (`ProjectTreePanel` / `UmlMainFrame`)
    * これまで `ProjectTreePanel.notifySelection()` はメソッド / クラス / Manifest / Component に対しては該当ハンドラを発火していたが、`UmlMainFrame` 側でクラス用ハンドラ (`setOnClassSelected`) を登録しておらず、また**パッケージ / モジュール** ノードは左クリックでは何も発火しない構造 (`onClassSelected(null)` に落ちるだけ) だった。結果として「左側のリストをクリックしても何も反応しない」状態だった
    * **クラスノードのクリック**: 当該クラスを `seed` + `neighborHops=1` のスコープでクラス図に切り替える (`UmlMainFrame.onTreeClassSelected`)
    * **パッケージノードのクリック**: 既存の右クリックメニュー経由のドリルダウンと同じ「該当パッケージにスコープしたクラス図」を左クリックでも開けるようにする
    * **モジュールノードのクリック**: `setOnModuleSelected` ハンドラを新設し、当該モジュールに含まれるクラスだけに絞ったクラス図に切り替える (`UmlMainFrame.onTreeModuleSelected`)
    * **メソッドノード選択時の二重発火を除去**: `MethodEntry` 経路で `onClassSelected` が併発呼び出しされていたため、`setOnClassSelected` を登録すると method クリック直後にシーケンス図がクラス図で上書きされる衝突があった。Method ノードは `onMethodSelected` のみ発火する設計に整理
    * **モジュール自動展開バグの修正**: `populate()` 完了時、`tree.expandRow(i)` を `i < root.getChildCount()` で回していたためルート行 (row 0) しか展開対象にならず、モジュールが常に折りたたまれた状態で表示されていた。`TreePath` 経由で各モジュールノードを明示的に展開するよう修正
    * **回帰防止テスト**: `UmlMainFrameSwingTest.testTreeNodeClickFiresScopeChange` で module / package / class それぞれのクリックがステータスバーに `Scope: ...` を出すところまでを GUI 経由で検証
    * 目的: ユーザーが左ペインの項目を選んだときに、それに対応した範囲のクラス図 (または既存のシーケンス図 / Manifest 図) へ自然に切り替わるようにし、「クリックしても無反応」という UX バグを解消する

* **Android 14 / 15 manifest 属性への対応** (`AndroidPropertyInfo` / `ForegroundServiceTypeCatalog` / `AndroidManifestInfo` 拡張)
    * **`<property>` 要素のパース** (Android 12+): application / activity / service / receiver / provider 配下の `<property android:name=... value=.../resource=.../>` を `AndroidPropertyInfo` で保持。`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 等の Android 14 必須 property を見逃さない
    * **`foregroundServiceType` カタログ化** (`ForegroundServiceTypeCatalog`): Android 10 (`dataSync` / `mediaPlayback` / `phoneCall` / `location` / `connectedDevice` / `mediaProjection`), Android 11 (`camera` / `microphone`), Android 14 (`health` / `remoteMessaging` / `shortService` / `specialUse` / `systemExempted`), Android 15 (`mediaProcessing`) の全 14 種を最小 API レベル + 対応 `FOREGROUND_SERVICE_*` permission とともに保持
    * **`|` 連結値の分解と API レベル算出**: `"dataSync|shortService"` のような連結値を分解し、構成種別の最大 API レベルを返す
    * **FOREGROUND_SERVICE permission の整合チェック**: Markdown サマリーに `Foreground Service Types` 表を追加し、各 service の foregroundServiceType に必要な permission が `<uses-permission>` 宣言されているかを yes / **MISSING** で表示
    * **Application 新属性の抽出**: `usesCleartextTraffic` / `networkSecurityConfig` / `enableOnBackInvokedCallback` (Android 13+ Predictive Back) / `localeConfig` (Android 13+) / `dataExtractionRules` (Android 12+) / `hardwareAccelerated` / `largeHeap` / `appCategory` を `AndroidManifestInfo` で保持
    * **Manifest 図への反映**: Application ノードに上記新属性を行追加。Service ノードの `fgType` 表示に `(API 35+)` のような Android 要求レベルラベルを付与。`FOREGROUND_SERVICE_*` permission は別ステレオタイプ `<<fgs>>` で橙色枠に強調
    * **Markdown サマリーへの反映**: `Application attributes (Android 12+/13+/14+)` / `Application properties` / `Foreground Service Types (Android 14+/15+)` の 3 セクションを追加
* **AndroidManifest 解析の濃厚化 (uses-sdk / 独自 permission / activity-alias / foregroundServiceType / Deep Link)**
    * **`<uses-sdk>` のパース**: `AndroidManifestInfo.minSdkVersion / targetSdkVersion / maxSdkVersion` を新設し、`AndroidManifestParser.parseUsesSdk` が読み込む。Manifest 図の Application ノードと Markdown サマリーにも反映
    * **独自 `<permission>` 宣言の保持** (`AndroidCustomPermission`)
        * アプリ自身が宣言する `<permission>` を `name / protectionLevel / permissionGroup / label / description` で保持
        * `AndroidManifestInfo.getCustomPermissions()` で取得可能。相対名は package を前置して FQN 解決
        * Markdown サマリーに `Custom Permissions (declared by app)` セクションを追加
    * **`<activity-alias>` の `targetActivity` 解決**
        * `AndroidComponentInfo.targetActivity` フィールドを追加。alias は通常 Activity と同じリストに入りつつ、`isActivityAlias()` で区別可能
        * Manifest 図のコンポーネントノードに `→ TargetActivity` の補助表示と `<<alias>>` ステレオタイプを付与
        * Markdown サマリーに `*(alias → ...)*` 表記
    * **`<service>` の `foregroundServiceType` 抽出**
        * Android 14 以降の foreground service で必須化された属性を `AndroidComponentInfo.foregroundServiceType` で保持
        * Manifest 図のコンポーネントノードに `fgType: dataSync|...` を補助表示。Markdown サマリーにも `*(foregroundServiceType: ...)*` 表記
    * **`<intent-filter>` の Deep Link 属性拡張** (`AndroidDataSpec`)
        * `<data>` 要素を 1 つずつ `AndroidDataSpec` に保持 (`scheme / host / port / path / pathPrefix / pathPattern / pathSuffix / pathAdvancedPattern / mimeType`)
        * `AndroidIntentFilter` に `autoVerify` / `order` / `dataSpecs` / `isViewDeepLink()` を追加
        * 既存の `dataSchemes` / `dataMimeTypes` は互換のため並行で埋める
        * `AndroidDataSpec.toDeepLinkUri()` が `scheme://host[:port]/path` 形式の URI を組み立て
* **新規 UML 図種: Deep Link 図** (`PlantUmlDeepLinkDiagram`)
    * `action.VIEW + category.BROWSABLE` を持つ Activity の URI 入口を 1 枚に可視化
    * scheme でグルーピング: `Web (http/https) — App Links` / `Custom scheme: <scheme>://` / `MIME-only`
    * `autoVerify="true"` の intent-filter は `<<autoVerify>>` ステレオタイプと矢印ラベルで強調 (App Links 候補)
    * CLI: `-D` / `--deeplink-diagram` で生成可能。`--all` の出力に `deeplink-diagram.svg` を追加 (出力数 7 → 8)
* **クラス図プレビューの右クリックからシーケンス図へ** (`PlantUmlClassDiagram.Options.interactiveLinks` / `PlantUmlSvgRenderer.LinkArea` / `SvgPreviewPanel.setOnLinkPopup`)
    * GUI でクラス図を表示中、クラスの枠を右クリックすると、そのクラスのメソッド一覧が `JPopupMenu` で開く。メソッドを選ぶと既存の `sequenceEntry` 経路でシーケンス図に置き換わる
    * 仕組み: クラス図生成時に各クラスへ `[[padtools://class/<FQN>]]` を埋め込み、PlantUML が SVG に出力する `<a xlink:href>` 領域を `PlantUmlSvgRenderer` が抽出して `RenderedSvg.getLinkAreas()` で返す。`SvgPreviewPanel` は右クリック (`isPopupTrigger`) でその領域をヒットテストし、ヒットすればリスナを発火する
    * URL 埋め込みは GUI プレビュー描画時 (`DiagramRequest.isInteractiveLinks() == true`) のみ。CLI / `Save Diagram As...` / `--per-folder` などの出力には影響しない
    * 抽象メソッドはツリー側と同じく除外する。メソッドラベルは `name(paramType, ...)` 形式
* **フォルダ単位のクラス図一括出力** (`PerFolderClassDiagrams`)
    * プロジェクトを再帰スキャンし、ソースファイルを直接含む各フォルダごとに 1 枚ずつクラス図 (`classes.puml` + `classes.svg`) を生成。出力ディレクトリ配下に元の相対パス階層を維持して書き出す
    * 大規模プロジェクトで「全クラス 1 枚絵」が読めない/レンダリングが重い問題を緩和。`ClassIndex.source(qn)` でクラスをソース配置フォルダごとに分割する
    * CLI: `-P` / `--per-folder` を `-c` と併用し、`-o <output_dir>` でルート出力先を指定 (例: `java -jar PadTools.jar -- -c --per-folder -o ./out ~/AndroidStudioProjects/MyApp`)
    * GUI: File メニューに「Export Class Diagrams Per Folder…」を追加。ロード済みプロジェクトに対して出力先を選ぶだけで実行でき、進捗バーと完了ダイアログを表示
    * `--no-legend` / `--no-comments` / `--jetpack` 等の既存表示オーバーライドはそのまま尊重 (`UmlOverrides.applyTo` 経由)
* **新規 UML 図種: Manifest 図** (`PlantUmlManifestDiagram`)
    * AndroidManifest.xml の `<application>` 属性 (package / class / theme / debuggable / allowBackup / meta-data) を中央の `<<application>>` ノードに据え、配下に Activity / Service / Receiver / Provider を種別ごとにグループ化して所属関係 (`*--`) を描く
    * 周辺に `uses-permission` / `uses-feature` を別パッケージで配置し、launcher Activity と `exported=true` は視覚的に強調 (色 / ステレオタイプ)
    * 同モジュール内に複数 manifest (main + debug + flavor 等) がある場合は sourceSet ごとに別 Application ノードを描画し、`<<src:debug>>` 等のステレオタイプを付与
    * CLI: `-M` / `--manifest-diagram` で生成可能。`--all` の出力に `manifest-diagram.svg` を追加 (出力数 6 → 7)
    * GUI: Diagram メニューに「Manifest Diagram」を追加。左ペインのプロジェクトツリーにモジュール直下の `[manifest] AndroidManifest.xml` ノードを表示し、Activities / Services / Receivers / Providers / Permissions / Features を展開可能に。Manifest 系ノードを選択すると Manifest 図に自動切替
    * 右ペインに `Manifest Summary` タブを新設し、`TextSummaryReport.toManifestMarkdown` で AndroidManifest のみに絞った Markdown サマリーを表示
* **AOSP 級プロジェクト対応 (Large project readability)** — 数万クラス規模でも「読み込めて」「図として読める」ようにパイプライン全体を刷新
    * **並列スキャン + 並列パース** (`AndroidProjectScanner`, `UmlGenerator`)
        * `AndroidProjectScanner.walk` を `Files.walkFileTree` ベースに置き換え、深い再帰でも安定動作
        * `UmlGenerator.extractFromProjectDetailed` を専用 ExecutorService (CPU - 1 並列) で並列化
        * `Options.maxFiles` で取り込み上限、`Options.cancelToken` で途中中断、`Options.useAospDefaults` で `prebuilts/.repo/out-soong/test_mapping/.cache` を追加除外
    * **進捗 + キャンセル ユーティリティ** (`padtools.util.ProgressListener`, `padtools.util.CancelToken`)
        * `silent() / console() / throttled(delegate, ms)` ファクトリで GUI/CLI のどちらでも使える
        * `UmlMainFrame` のステータスバーに `JProgressBar` を追加し、`File → Cancel Loading` で進行中の解析を中断可能
    * **Stage A / Stage B 二相パース** (`JavaStructureExtractor.extractHeadersOnly`, `ClassIndex`)
        * ヘッダ (パッケージ / 名前 / kind / modifiers / super / interfaces / アノテーション) のみで全件保持し、必要なクラスだけ `ClassIndex.detail(qn)` で詳細にフルパースする
        * 50,000 クラスでも数十 MB 程度に収まる想定
    * **ツリーの遅延展開** (`ProjectTreePanel`)
        * モジュール → パッケージ ノードまでだけ初期構築。パッケージ展開時にクラス、クラス展開時にメソッドを生成
        * Gradle 解析結果 (`AndroidProjectAnalyzer.inferModuleName`) と紐付け、`(other)` 集約を解消
        * パッケージノード右クリック → `Show class diagram of this package` でクラス図にドリルダウン
    * **DiagramScope による表示範囲指定** (`DiagramScope`, `DiagramScopeDialog`, `DiagramService.applyScope`)
        * パッケージ前方一致 / モジュール / 正規表現 / シード+N hop / 最大クラス数 を組み合わせて絞り込み
        * Diagram メニュー: `Scope...` で編集、`Clear Scope` で解除
        * 絞り込みで件数が減ったり `maxClasses` で切り詰められたら `footer` 行に警告を出す
    * **永続ディスクキャッシュ** (`PersistentAnalysisCache`)
        * `~/.padtools/cache/<hash>/` に Stage A ヘッダ + ソースパス + モジュール紐付けを保存
        * キャッシュキー = プロジェクトルート + (path/mtime/size) 列の SHA-256。ファイルが 1 件でも変われば別ディレクトリで自動無効化
        * `lazyDetails=true` + `useDiskCache=true` (デフォルト) で利用
    * **追加テスト**: `AndroidProjectScannerScaleTest`, `UmlGeneratorParallelTest`, `ClassIndexTest`, `DiagramScopeTest`, `DiagramServiceScopeTest`, `JavaClassInfoCodecTest`, `PersistentAnalysisCacheTest`, `CacheKeyTest`, `CancelTokenTest`, `ProgressListenerTest`, `SyntheticAospScaleTest` (`-DrunPerfTests=true` でのみ実行)
    * **ブラウザ E2E / Swing GUI テスト** (`com.microsoft.playwright:playwright` + `org.assertj:assertj-swing-junit`)
        * `PlantUmlSvgPlaywrightTest` — 生成 SVG を Chromium (Playwright) でレンダリングし、クラス名がページに現れること・スコープ適用時にクラスが消えることを検証。`build/playwright/class-diagram.png` に PNG スクリーンショットを保存
        * `UmlMainFrameSwingTest` — `UmlMainFrame` を AssertJ-Swing で起動し、最小プロジェクトをロードしてツリーが構築されることを検証
        * ヘッドレス CI/サンドボックスでは `Assume.assumeNoException` (Playwright) / `Assume.assumeFalse(isHeadless())` (Swing) で自動 skip。DISPLAY が無ければ `xvfb-run -a ./gradlew test` でラップ
* **シーケンス図のプロジェクト内クラス色付け** (`PlantUmlSequenceDiagram`)
    * 入力 `classes` に含まれる解析済みクラス (= プロジェクト内の独自クラス) の participant を `#LightSkyBlue` で背景塗りつぶしし、外部ライブラリやシステムクラスと視覚的に区別できるようにした
    * `Options.highlightProjectClasses` で機能の ON/OFF、`Options.projectClassColor` で色を変更できる (空文字を指定すれば従来通り色なし)
    * 凡例ブロックにも独自クラスを示す色サンプル行を追加し、図の読み手が一目で判別できるようにした
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
