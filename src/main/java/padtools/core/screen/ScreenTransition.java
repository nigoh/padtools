package padtools.core.screen;

import java.util.Objects;

/**
 * 画面遷移 1 件分の情報 (caller → target Activity)。
 *
 * <p>{@link IntentNavigationDetector} が検出した {@code startActivity},
 * {@code startActivityForResult}, {@code Intent.setClass} などの呼び出しを
 * 1 件の {@code ScreenTransition} として表す。</p>
 */
public final class ScreenTransition {

    /** 遷移の発生種別。 */
    public enum Kind {
        /** {@code startActivity(new Intent(ctx, X.class))}。 */
        START_ACTIVITY,
        /** {@code startActivityForResult(...)}。 */
        START_FOR_RESULT,
        /** {@code Intent.setClass(...)} 経由 (後で startActivity される想定)。 */
        SET_CLASS,
        /** その他の Intent 構築。 */
        OTHER
    }

    private final String fromFqn;
    private final String fromMethod;
    private final String targetClassName;
    private final String file;
    private final int lineHint;
    private final Kind kind;

    public ScreenTransition(String fromFqn, String fromMethod, String targetClassName,
                              String file, int lineHint, Kind kind) {
        this.fromFqn = nz(fromFqn);
        this.fromMethod = nz(fromMethod);
        this.targetClassName = nz(targetClassName);
        this.file = nz(file);
        this.lineHint = lineHint;
        this.kind = kind == null ? Kind.OTHER : kind;
    }

    public String getFromFqn() { return fromFqn; }
    public String getFromMethod() { return fromMethod; }
    /** 遷移先のクラス名 (単純名もしくは FQN。ソース表現のまま)。 */
    public String getTargetClassName() { return targetClassName; }
    public String getFile() { return file; }
    public int getLineHint() { return lineHint; }
    public Kind getKind() { return kind; }

    /** 遷移先の単純名 ({@code com.x.Foo} → {@code Foo})。 */
    public String getTargetSimpleName() {
        int dot = targetClassName.lastIndexOf('.');
        return dot < 0 ? targetClassName : targetClassName.substring(dot + 1);
    }

    /** 遷移元の単純名。 */
    public String getFromSimpleName() {
        int dot = fromFqn.lastIndexOf('.');
        return dot < 0 ? fromFqn : fromFqn.substring(dot + 1);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenTransition)) return false;
        ScreenTransition that = (ScreenTransition) o;
        return lineHint == that.lineHint
                && Objects.equals(fromFqn, that.fromFqn)
                && Objects.equals(fromMethod, that.fromMethod)
                && Objects.equals(targetClassName, that.targetClassName)
                && Objects.equals(file, that.file)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromFqn, fromMethod, targetClassName, file, lineHint, kind);
    }

    @Override
    public String toString() {
        return kind + ": " + fromFqn + "." + fromMethod + " -> " + targetClassName;
    }
}
