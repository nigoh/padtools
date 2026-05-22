package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 全クラスの「純粋なメンバー一覧」を CSV で出力する。
 *
 * <p>{@link MethodUsageReport} が利用側・実行条件付きの「関数使用マップ」を出すのに対し、
 * こちらは各クラスのフィールド・メソッド・enum 定数（＝メンバー）だけを 1 メンバー=1 行で
 * 素直に列挙する。クラスは <strong>単純名</strong>（{@link JavaClassInfo#getSimpleName()}）で
 * 出力し、FQN や URI は使わない（パッケージは別カラムに分離する）。表計算ソフトへの取込を想定。</p>
 *
 * <p>カラム: {@code class,package,kind,member,visibility,name,type,params,modifiers}</p>
 */
public final class ClassMemberReport {

    private static final String HEADER =
            "class,package,kind,member,visibility,name,type,params,modifiers";

    private ClassMemberReport() {
    }

    /** 全クラスのメンバー一覧（単純名・1 メンバー=1 行）を CSV で返す。 */
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
        out.append(HEADER).append('\n');
        for (JavaClassInfo c : sorted) {
            appendClass(out, c);
        }
        return out.toString();
    }

    private static void appendClass(StringBuilder out, JavaClassInfo c) {
        String simpleName = nz(c.getSimpleName());
        String pkg = nz(c.getPackageName());
        String kind = kindLabel(c.getKind());
        boolean emitted = false;

        if (c.getKind() == JavaClassInfo.Kind.ENUM) {
            for (String constant : c.getEnumConstants()) {
                line(out, simpleName, pkg, kind, "enum-constant",
                        "public", nz(constant), "", "", "");
                emitted = true;
            }
        }
        for (JavaFieldInfo f : c.getFields()) {
            line(out, simpleName, pkg, kind, "field",
                    visibility(f.getVisibility()), nz(f.getName()),
                    nz(f.getType()), "", fieldModifiers(f));
            emitted = true;
        }
        for (JavaMethodInfo m : c.getMethods()) {
            String type = m.isConstructor() ? "" : nz(m.getReturnType());
            line(out, simpleName, pkg, kind, "method",
                    visibility(m.getVisibility()), nz(m.getName()),
                    type, params(m), methodModifiers(m));
            emitted = true;
        }
        if (!emitted) {
            // メンバーを持たないクラスも「全クラス」として 1 行残す。
            line(out, simpleName, pkg, kind, "(none)", "", "", "", "", "");
        }
    }

    /** 各セルを CSV エスケープしてカンマ連結し、1 行として追記する。 */
    private static void line(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(csv(cells[i]));
        }
        out.append('\n');
    }

    /** メソッド引数を {@code name: type, name: type} 形式で連結する。 */
    private static String params(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private static String fieldModifiers(JavaFieldInfo f) {
        StringBuilder sb = new StringBuilder();
        if (f.isStatic()) {
            sb.append("static");
        }
        if (f.isFinal()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("final");
        }
        return sb.toString();
    }

    private static String methodModifiers(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        if (m.isStatic()) {
            sb.append("static");
        }
        if (m.isAbstract()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("abstract");
        }
        return sb.toString();
    }

    private static String visibility(Visibility v) {
        return (v == null ? Visibility.PACKAGE : v).name().toLowerCase(Locale.ROOT);
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

    private static String csv(String s) {
        String v = nz(s);
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
