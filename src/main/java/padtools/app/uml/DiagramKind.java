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
    /** コンポーネント図 — AndroidManifest / Gradle から推定したコンポーネント関係。 */
    COMPONENT("Component Diagram"),
    /** Gradle 依存図 — モジュール間 / ライブラリ依存。 */
    DEPENDENCY("Dependency Graph"),
    /** Manifest 図 — AndroidManifest.xml のアプリ構造 (Application + 配下コンポーネント) を可視化。 */
    MANIFEST("Manifest Diagram");

    private final String displayName;

    DiagramKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
