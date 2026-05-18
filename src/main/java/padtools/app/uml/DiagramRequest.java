package padtools.app.uml;

/**
 * GUI から {@link DiagramService} に渡す、図種とスコープを束ねた不変リクエスト。
 *
 * <p>{@link DiagramKind#SEQUENCE} の場合のみ {@link #sequenceEntryClass} と
 * {@link #sequenceEntryMethod} を使用する。{@link DiagramKind#LAYOUT} の場合のみ
 * {@link #layoutKey} を使用する。それ以外の図種ではこれらの値は無視される。</p>
 *
 * <p>大規模プロジェクトの可読性確保のため、{@link DiagramScope} で表示クラスを
 * 絞り込める。null/未指定は「全件」を意味する。</p>
 */
public final class DiagramRequest {

    private final DiagramKind kind;
    private final String sequenceEntryClass;
    private final String sequenceEntryMethod;
    private final boolean includeLegend;
    private final DiagramScope scope;
    private final boolean interactiveLinks;
    private final String layoutKey;

    public DiagramRequest(DiagramKind kind) {
        this(kind, null, null, true, null, false, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, null, false, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, scope, false, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope, boolean interactiveLinks) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, scope,
                interactiveLinks, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope, boolean interactiveLinks,
                          String layoutKey) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        this.kind = kind;
        this.sequenceEntryClass = sequenceEntryClass;
        this.sequenceEntryMethod = sequenceEntryMethod;
        this.includeLegend = includeLegend;
        this.scope = scope;
        this.interactiveLinks = interactiveLinks;
        this.layoutKey = layoutKey;
    }

    /** LAYOUT 図用のショートカットコンストラクタ。 */
    public static DiagramRequest forLayout(String layoutKey, boolean includeLegend) {
        return new DiagramRequest(DiagramKind.LAYOUT, null, null, includeLegend,
                null, false, layoutKey);
    }

    public DiagramKind getKind() {
        return kind;
    }

    public String getSequenceEntryClass() {
        return sequenceEntryClass;
    }

    public String getSequenceEntryMethod() {
        return sequenceEntryMethod;
    }

    public boolean isIncludeLegend() {
        return includeLegend;
    }

    public DiagramScope getScope() {
        return scope;
    }

    /**
     * クラス図の各クラスに {@code [[padtools://class/<FQN>]]} を埋め込むか。
     * GUI プレビューで右クリック→メソッド一覧のヒットテストに使うときだけ true にする。
     */
    public boolean isInteractiveLinks() {
        return interactiveLinks;
    }

    /**
     * LAYOUT 図でターゲットとする {@link padtools.core.formats.android.AndroidLayoutInfo}
     * のキー (形式: {@code moduleName::sourceSet::qualifier::fileName})。
     * LAYOUT 図以外では null。
     */
    public String getLayoutKey() {
        return layoutKey;
    }
}
