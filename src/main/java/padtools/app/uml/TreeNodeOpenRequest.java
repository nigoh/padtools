package padtools.app.uml;

import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;

/**
 * ツリーノードを「新しいタブ」として開くリクエスト。
 *
 * <p>マウス中クリック時に {@link ProjectTreePanel} が組み立て、
 * {@link DiagramTabPane#addOrFocusTab} 等に渡される。</p>
 */
public final class TreeNodeOpenRequest {

    /** 対象タイプ。 */
    public enum Target { METHOD, CLASS, PACKAGE, MODULE }

    public final Target target;
    public final DiagramKind kind;
    public final JavaClassInfo classInfo;
    public final JavaMethodInfo methodInfo;
    public final String name;

    private TreeNodeOpenRequest(Target target, DiagramKind kind,
                                 JavaClassInfo classInfo, JavaMethodInfo methodInfo,
                                 String name) {
        this.target = target;
        this.kind = kind;
        this.classInfo = classInfo;
        this.methodInfo = methodInfo;
        this.name = name;
    }

    public static TreeNodeOpenRequest method(JavaClassInfo owner, JavaMethodInfo method,
                                              DiagramKind kind) {
        return new TreeNodeOpenRequest(Target.METHOD, kind, owner, method, null);
    }

    public static TreeNodeOpenRequest classNode(JavaClassInfo c) {
        return new TreeNodeOpenRequest(Target.CLASS, DiagramKind.CLASS, c, null, null);
    }

    public static TreeNodeOpenRequest pkg(String packageName) {
        return new TreeNodeOpenRequest(Target.PACKAGE, DiagramKind.CLASS, null, null, packageName);
    }

    public static TreeNodeOpenRequest module(String moduleName) {
        return new TreeNodeOpenRequest(Target.MODULE, DiagramKind.CLASS, null, null, moduleName);
    }

    /** タブ識別用のキー (同じキーなら既存タブにフォーカスする)。 */
    public String tabKey() {
        switch (target) {
            case METHOD:
                return kind.name() + ":" + classInfo.getSimpleName() + "." + methodInfo.getName();
            case CLASS:
                return "CLASS:" + classInfo.getQualifiedName();
            case PACKAGE:
                return "PKG:" + name;
            case MODULE:
                return "MOD:" + name;
            default:
                return target.name();
        }
    }

    /** タブヘッダ表示用の短いラベル。 */
    public String displayLabel() {
        switch (target) {
            case METHOD:
                String suffix = kind == DiagramKind.ACTIVITY ? " (act)"
                        : kind == DiagramKind.CALLGRAPH ? " (cg)" : "";
                return classInfo.getSimpleName() + "." + methodInfo.getName() + suffix;
            case CLASS:
                return classInfo.getSimpleName();
            case PACKAGE:
                return shortPackage(name);
            case MODULE:
                return name;
            default:
                return target.name();
        }
    }

    /** 長いパッケージ名の末尾 2 コンポーネントだけを返す。 */
    private static String shortPackage(String pkg) {
        if (pkg == null) return "";
        String[] parts = pkg.split("\\.");
        if (parts.length <= 2) return pkg;
        return "…" + parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
