package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 全クラスの「純粋なメンバー一覧」をプレーンテキストで出力する。
 *
 * <p>{@link MethodUsageReport} が利用側・実行条件付きの「関数使用マップ」を出すのに対し、
 * こちらは各クラスのフィールド・メソッド（＝メンバー）だけを素直に列挙する。クラスは
 * <strong>単純名</strong>（{@link JavaClassInfo#getSimpleName()}）を見出しにし、FQN や URI は
 * 使わない。等幅フォントの読み取り専用ビュー（GUI の Members タブ）で表示する用途。</p>
 */
public final class ClassMemberReport {

    private static final String SEPARATOR =
            "──────────────────────────────────────────────";

    private ClassMemberReport() {
    }

    /** 全クラスのメンバー一覧（単純名見出し）をプレーンテキストで返す。 */
    public static String render(List<JavaClassInfo> classes) {
        List<JavaClassInfo> sorted = new ArrayList<>();
        if (classes != null) {
            for (JavaClassInfo c : classes) {
                if (c != null && c.getKind() != JavaClassInfo.Kind.MODULE) {
                    sorted.add(c);
                }
            }
        }
        sorted.sort(Comparator
                .comparing((JavaClassInfo c) -> nz(c.getSimpleName()))
                .thenComparing(c -> nz(c.getPackageName())));

        StringBuilder out = new StringBuilder();
        out.append("全クラスのメンバー一覧 (Class members)\n");
        out.append("クラス数: ").append(sorted.size()).append('\n');
        for (JavaClassInfo c : sorted) {
            out.append('\n');
            appendClass(out, c);
        }
        return out.toString();
    }

    private static void appendClass(StringBuilder out, JavaClassInfo c) {
        out.append(SEPARATOR).append('\n');
        out.append(nz(c.getSimpleName()))
                .append("  [").append(kindLabel(c.getKind())).append(']');
        String pkg = nz(c.getPackageName());
        if (!pkg.isEmpty()) {
            out.append("    (").append(pkg).append(')');
        }
        out.append('\n');

        boolean hasEnum = c.getKind() == JavaClassInfo.Kind.ENUM
                && !c.getEnumConstants().isEmpty();
        if (hasEnum) {
            out.append("  列挙定数 (").append(c.getEnumConstants().size()).append("): ")
                    .append(String.join(", ", c.getEnumConstants())).append('\n');
        }

        List<JavaFieldInfo> fields = c.getFields();
        if (!fields.isEmpty()) {
            out.append("  フィールド (").append(fields.size()).append("):\n");
            for (JavaFieldInfo f : fields) {
                out.append("    ").append(fieldSignature(f)).append('\n');
            }
        }

        List<JavaMethodInfo> methods = c.getMethods();
        if (!methods.isEmpty()) {
            out.append("  メソッド (").append(methods.size()).append("):\n");
            for (JavaMethodInfo m : methods) {
                out.append("    ").append(methodSignature(m)).append('\n');
            }
        }

        if (!hasEnum && fields.isEmpty() && methods.isEmpty()) {
            out.append("  (メンバーなし)\n");
        }
    }

    /** フィールド署名を {@code <可視性> name: Type {static} {final}} で整形。 */
    private static String fieldSignature(JavaFieldInfo f) {
        StringBuilder sb = new StringBuilder();
        sb.append(mark(f.getVisibility())).append(' ').append(nz(f.getName()));
        if (f.getType() != null && !f.getType().isEmpty()) {
            sb.append(": ").append(f.getType());
        }
        if (f.isStatic()) {
            sb.append(" {static}");
        }
        if (f.isFinal()) {
            sb.append(" {final}");
        }
        return sb.toString();
    }

    /** メソッド署名を {@code <可視性> [static] [abstract] name(name: type, ...): returnType} で整形。 */
    private static String methodSignature(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append(mark(m.getVisibility())).append(' ');
        if (m.isStatic()) {
            sb.append("static ");
        }
        if (m.isAbstract()) {
            sb.append("abstract ");
        }
        sb.append(nz(m.getName())).append('(');
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = i < names.size() ? names.get(i) : "";
            if (name != null && !name.isEmpty()) {
                sb.append(name).append(": ");
            }
            sb.append(types.get(i) == null ? "?" : types.get(i));
        }
        sb.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            sb.append(": ").append(m.getReturnType());
        }
        return sb.toString();
    }

    private static String mark(Visibility v) {
        return v == null ? Visibility.PACKAGE.mark() : v.mark();
    }

    private static String kindLabel(JavaClassInfo.Kind k) {
        if (k == null) {
            return "class";
        }
        switch (k) {
            case INTERFACE:
                return "interface";
            case ENUM:
                return "enum";
            case ANNOTATION:
                return "@interface";
            case AIDL_INTERFACE:
                return "aidl";
            case RECORD:
                return "record";
            default:
                return "class";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
