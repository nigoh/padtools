package padtools.app.uml;

/**
 * GUI で選択可能な UML 図種。
 *
 * <p>各図種は {@link DiagramService} 内で対応する PlantUML 生成器
 * ({@link padtools.core.formats.uml.PlantUmlClassDiagram} 等) にディスパッチされる。</p>
 */
public enum DiagramKind {
    /** クラス図 — Java/AIDL ソースから抽出した型情報を表示。 */
    CLASS("Class Diagram"),
    /** パッケージ図 — パッケージ単位の依存関係。 */
    PACKAGE("Package Diagram"),
    /** シーケンス図 — 任意の {@code Class.method} を起点とした呼び出しトレース。 */
    SEQUENCE("Sequence Diagram"),
    /** アクティビティ図 — 任意の {@code Class.method} 内部の制御フロー (if/while/return など)。 */
    ACTIVITY("Activity Diagram"),
    /** コンポーネント図 — AndroidManifest / Gradle から推定したコンポーネント関係。 */
    COMPONENT("Component Diagram"),
    /** Gradle 依存図 — モジュール間 / ライブラリ依存。 */
    DEPENDENCY("Dependency Graph"),
    /** Manifest 図 — AndroidManifest.xml のアプリ構造 (Application + 配下コンポーネント) を可視化。 */
    MANIFEST("Manifest Diagram"),
    /** Layout 図 — res/layout XML 1 つの View 階層を可視化。 */
    LAYOUT("Layout View Hierarchy"),
    /** 共通クラス図 — 他クラスから参照される回数 (fan-in) が多いクラスを上位 N 件表示。 */
    COMMON("Common Classes"),
    /** Navigation 図 — res/navigation/*.xml の画面遷移を State 図として可視化。 */
    NAVIGATION("Navigation Graph"),
    /** モジュール依存グラフ — module-info.java の requires/exports/opens を可視化。 */
    MODULE("Module Diagram"),
    /** 継承図 — extends/implements 階層のみをツリー状に表示。クラス名・型種別のみ出力。 */
    INHERITANCE("Inheritance Diagram");

    private final String displayName;

    DiagramKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
