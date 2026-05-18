package padtools.app.uml;

/**
 * GUI から {@link DiagramService} に渡す、図種とスコープを束ねた不変リクエスト。
 *
 * <p>{@link DiagramKind#SEQUENCE} の場合のみ {@link #sequenceEntryClass} と
 * {@link #sequenceEntryMethod} を使用する。それ以外の図種ではこれらの値は無視される。</p>
 */
public final class DiagramRequest {

    private final DiagramKind kind;
    private final String sequenceEntryClass;
    private final String sequenceEntryMethod;
    private final boolean includeLegend;

    public DiagramRequest(DiagramKind kind) {
        this(kind, null, null, true);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        this.kind = kind;
        this.sequenceEntryClass = sequenceEntryClass;
        this.sequenceEntryMethod = sequenceEntryMethod;
        this.includeLegend = includeLegend;
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
}
