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
 * <p>素のメンバー属性に加えて、構造把握（どこで定義され・何に依存し・どんな継承関係や役割を持つか）
 * 向けのカラムを末尾に付ける。これらは ISO/IEC 25010 の「保守性 &gt; 解析性 / モジュール性」に対応する。</p>
 *
 * <p>カラム: {@code class,package,kind,member,visibility,name,type,params,modifiers,}
 * {@code enclosing,extends,implements,line,annotations,overrides,calls,callees}</p>
 *
 * <p>{@code line}/{@code calls}/{@code callees} はメソッド本体を解析する FULL パースでのみ実値になる
 * (GUI の Members タブは FULL)。ヘッダのみ解析モードでは空/0 になる。</p>
 */
public final class ClassMemberReport {

    private static final String HEADER =
            "class,package,kind,member,visibility,name,type,params,modifiers,"
            + "enclosing,extends,implements,line,annotations,overrides,calls,callees";

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
        // クラス系列の構造カラム (全行で同じ値を反復出力する denormalized 形式)。
        String enclosing = simple(c.getEnclosingClass());
        String ext = simple(c.getSuperClass());
        String impl = joinSimpleNames(c.getInterfaces());
        boolean emitted = false;

        if (c.getKind() == JavaClassInfo.Kind.ENUM) {
            for (String constant : c.getEnumConstants()) {
                line(out, simpleName, pkg, kind, "enum-constant",
                        "public", nz(constant), "", "", "",
                        enclosing, ext, impl, "", "", "", "", "");
                emitted = true;
            }
        }
        for (JavaFieldInfo f : c.getFields()) {
            line(out, simpleName, pkg, kind, "field",
                    visibility(f.getVisibility()), nz(f.getName()),
                    nz(f.getType()), "", fieldModifiers(f),
                    enclosing, ext, impl, "", joinAnnotations(f.getAnnotations()),
                    "", "", "");
            emitted = true;
        }
        for (JavaMethodInfo m : c.getMethods()) {
            String type = m.isConstructor() ? "" : nz(m.getReturnType());
            line(out, simpleName, pkg, kind, "method",
                    visibility(m.getVisibility()), nz(m.getName()),
                    type, params(m), methodModifiers(m),
                    enclosing, ext, impl, lineNo(m), joinAnnotations(m.getAnnotations()),
                    overrides(m), Integer.toString(m.getCalls().size()), callees(m));
            emitted = true;
        }
        if (!emitted) {
            // メンバーを持たないクラスも「全クラス」として 1 行残す。
            line(out, simpleName, pkg, kind, "(none)", "", "", "", "", "",
                    enclosing, ext, impl, "", "", "", "", "");
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

    /** メソッド宣言開始行。未取得 (-1) は空文字。 */
    private static String lineNo(JavaMethodInfo m) {
        int ln = m.getStartLine();
        return ln > 0 ? Integer.toString(ln) : "";
    }

    /**
     * {@code @Override} の有無を {@code yes/no} で返す。
     * アノテーションは先頭 {@code @} を除いた文字列で保持されるため simple 名で照合する。
     * 注: 注釈の無いインタフェース実装は拾わない近似 (シグネチャ照合はしない)。
     */
    private static String overrides(JavaMethodInfo m) {
        for (String a : m.getAnnotations()) {
            String head = nz(a);
            int paren = head.indexOf('(');
            if (paren >= 0) {
                head = head.substring(0, paren);
            }
            if (simple(head).equals("Override")) {
                return "yes";
            }
        }
        return "no";
    }

    /**
     * 呼び出し先クラス (fan-out の宛先) を出現順 distinct で {@code "; "} 連結する。
     * シンボル解決済みの宣言型 FQN を優先し、未解決なら receiver 文字列にフォールバックする。
     * {@code this}/{@code super} は外部呼び出し先ではないため除外する。
     */
    private static String callees(JavaMethodInfo m) {
        List<String> seen = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (JavaMethodInfo.Call call : m.getCalls()) {
            String owner = nz(call.getResolvedOwnerFqn());
            if (owner.isEmpty()) {
                owner = nz(call.getReceiver());
            }
            String s = simple(owner);
            if (s.isEmpty() || s.equals("this") || s.equals("super") || seen.contains(s)) {
                continue;
            }
            seen.add(s);
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** 型名リストを simple 名・出現順 distinct で {@code "; "} 連結する (implements 用)。 */
    private static String joinSimpleNames(List<String> names) {
        if (names == null) {
            return "";
        }
        List<String> seen = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            String s = simple(n);
            if (s.isEmpty() || seen.contains(s)) {
                continue;
            }
            seen.add(s);
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** アノテーション文字列 (先頭 {@code @} 無し) を {@code "; "} 連結する。 */
    private static String joinAnnotations(List<String> annotations) {
        if (annotations == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String a : annotations) {
            String v = nz(a).trim();
            if (v.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /** FQN / ジェネリクス付き型名から simple 名を取り出す ({@code a.b.C<D>} -&gt; {@code C})。 */
    private static String simple(String name) {
        String v = nz(name).trim();
        int lt = v.indexOf('<');
        if (lt >= 0) {
            v = v.substring(0, lt);
        }
        int dot = v.lastIndexOf('.');
        if (dot >= 0) {
            v = v.substring(dot + 1);
        }
        return v;
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
