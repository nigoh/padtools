package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * クラス情報 ({@link JavaClassInfo}) のリストから PlantUML 形式のクラス図テキストを生成する。
 *
 * <p>{@link Options} で出力する要素 (継承線、利用関係、可視性記号、AAOS マーカー)
 * を切り替えられる。</p>
 */
public final class PlantUmlClassDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean showVisibility = true;
        public boolean showInheritance = true;
        public boolean showUsageRelations = true;
        public boolean showFields = true;
        public boolean showMethods = true;
        public boolean groupByPackage = true;
        public boolean markAaosCategories = true;
        /** 利用関係を出すフィールド型の最大要素数 (1 クラスあたり)。多すぎる場合に抑制。 */
        public int maxUsagePerClass = 30;
        /** タイトル文字列 (null で省略)。 */
        public String title;
    }

    private static final Pattern PRIMITIVE_OR_BUILTIN = Pattern.compile(
            "^(void|boolean|byte|char|short|int|long|float|double"
                    + "|String|Object|CharSequence|Number"
                    + "|Integer|Long|Short|Byte|Float|Double|Boolean|Character"
                    + "|Class|Map|List|Set|Collection|Iterable|Iterator|Queue"
                    + "|HashMap|ArrayList|LinkedList|HashSet|LinkedHashMap)$");

    /** デフォルト Options で生成。 */
    public static String generate(List<JavaClassInfo> classes) {
        return generate(classes, null);
    }

    /** オプション付き生成。 */
    public static String generate(List<JavaClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }
        out.append("skinparam classAttributeIconSize 0\n");
        Set<String> knownNames = new HashSet<>();
        for (JavaClassInfo c : classes) {
            knownNames.add(c.getQualifiedName());
            knownNames.add(c.getSimpleName());
        }

        if (o.groupByPackage) {
            Map<String, List<JavaClassInfo>> byPkg = new LinkedHashMap<>();
            for (JavaClassInfo c : classes) {
                byPkg.computeIfAbsent(
                        c.getPackageName() == null ? "" : c.getPackageName(),
                        k -> new ArrayList<>()).add(c);
            }
            for (Map.Entry<String, List<JavaClassInfo>> e : byPkg.entrySet()) {
                String pkg = e.getKey();
                if (pkg.isEmpty()) {
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "");
                    }
                } else {
                    out.append("package \"").append(pkg).append("\" {\n");
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "  ");
                    }
                    out.append("}\n");
                }
            }
        } else {
            for (JavaClassInfo c : classes) {
                emitClass(out, c, o, "");
            }
        }

        // 関係線
        if (o.showInheritance) {
            for (JavaClassInfo c : classes) {
                emitInheritance(out, c);
            }
        }
        if (o.showUsageRelations) {
            for (JavaClassInfo c : classes) {
                emitUsage(out, c, knownNames, o);
            }
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitClass(StringBuilder out, JavaClassInfo c,
                                  Options o, String indent) {
        String kw = classKeyword(c);
        String stereo = stereotype(c, o);
        out.append(indent).append(kw).append(' ');
        out.append(quoteId(displayId(c)));
        if (!stereo.isEmpty()) {
            out.append(' ').append(stereo);
        }
        out.append(" {\n");
        if (o.showFields) {
            for (JavaFieldInfo f : c.getFields()) {
                emitField(out, f, o, indent + "  ");
            }
        }
        if (o.showMethods) {
            for (JavaMethodInfo m : c.getMethods()) {
                emitMethod(out, m, o, indent + "  ");
            }
        }
        out.append(indent).append("}\n");
    }

    private static String classKeyword(JavaClassInfo c) {
        switch (c.getKind()) {
            case INTERFACE: return "interface";
            case ENUM: return "enum";
            case ANNOTATION: return "annotation";
            case AIDL_INTERFACE: return "interface";
            case CLASS:
            default:
                return c.isAbstract() ? "abstract class" : "class";
        }
    }

    private static String stereotype(JavaClassInfo c, Options o) {
        List<String> parts = new ArrayList<>();
        if (o.markAaosCategories) {
            String cat = c.getAaosCategory();
            if (cat == null) {
                cat = AaosPattern.categorize(c);
            }
            if (cat != null) {
                parts.add(cat);
            }
        }
        if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            parts.add("aidl");
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append("<<").append(p).append(">>");
        }
        return sb.toString();
    }

    private static String displayId(JavaClassInfo c) {
        return c.getQualifiedName();
    }

    private static String quoteId(String id) {
        return "\"" + id.replace("\"", "\\\"") + "\"";
    }

    private static void emitField(StringBuilder out, JavaFieldInfo f,
                                   Options o, String indent) {
        out.append(indent);
        if (o.showVisibility) {
            out.append(f.getVisibility().mark());
        }
        if (f.isStatic()) {
            out.append("{static} ");
        }
        if (f.getName() != null && !f.getName().isEmpty()) {
            out.append(f.getName());
        }
        if (f.getType() != null && !f.getType().isEmpty()) {
            out.append(": ").append(f.getType());
        }
        out.append('\n');
    }

    private static void emitMethod(StringBuilder out, JavaMethodInfo m,
                                    Options o, String indent) {
        out.append(indent);
        if (o.showVisibility) {
            out.append(m.getVisibility().mark());
        }
        if (m.isStatic()) {
            out.append("{static} ");
        }
        if (m.isAbstract()) {
            out.append("{abstract} ");
        }
        out.append(m.getName() == null ? "" : m.getName()).append('(');
        for (int i = 0; i < m.getParameterTypes().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            String type = m.getParameterTypes().get(i);
            String name = i < m.getParameterNames().size()
                    ? m.getParameterNames().get(i) : "";
            if (name != null && !name.isEmpty()) {
                out.append(name).append(": ");
            }
            out.append(type == null ? "?" : type);
        }
        out.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            out.append(": ").append(m.getReturnType());
        }
        out.append('\n');
    }

    private static void emitInheritance(StringBuilder out, JavaClassInfo c) {
        String me = quoteId(c.getQualifiedName());
        if (c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
            String parent = simplifyTypeRef(c.getSuperClass());
            out.append(quoteId(parent)).append(" <|-- ").append(me).append('\n');
        }
        for (String iface : c.getInterfaces()) {
            String parent = simplifyTypeRef(iface);
            out.append(quoteId(parent)).append(" <|.. ").append(me).append('\n');
        }
    }

    private static void emitUsage(StringBuilder out, JavaClassInfo c,
                                   Set<String> known, Options o) {
        String me = quoteId(c.getQualifiedName());
        Set<String> emitted = new LinkedHashSet<>();
        int count = 0;
        for (JavaFieldInfo f : c.getFields()) {
            if (count >= o.maxUsagePerClass) {
                break;
            }
            String target = pickUsageTarget(f.getType(), known);
            if (target == null || target.equals(c.getQualifiedName())
                    || target.equals(c.getSimpleName())) {
                continue;
            }
            if (emitted.add(target)) {
                out.append(me).append(" --> ").append(quoteId(target)).append('\n');
                count++;
            }
        }
    }

    /** 型参照 (たとえば {@code Map<String, Foo>}) から、利用対象となるユーザ定義型を推定する。 */
    private static String pickUsageTarget(String type, Set<String> known) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        String t = type.replaceAll("\\[\\]", "").trim();
        // 一番外側のジェネリックがあれば、その引数を再帰的に検索
        int lt = t.indexOf('<');
        if (lt >= 0) {
            int gt = t.lastIndexOf('>');
            String inner = (gt > lt) ? t.substring(lt + 1, gt) : "";
            String outer = t.substring(0, lt).trim();
            String tgt = matchKnown(outer, known);
            if (tgt != null) {
                return tgt;
            }
            for (String part : splitTopLevelCsv(inner)) {
                String r = pickUsageTarget(part.trim(), known);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        return matchKnown(t, known);
    }

    private static String matchKnown(String name, Set<String> known) {
        if (PRIMITIVE_OR_BUILTIN.matcher(name).matches()) {
            return null;
        }
        if (known.contains(name)) {
            return name;
        }
        for (String k : known) {
            if (k.endsWith("." + name)) {
                return k;
            }
        }
        return null;
    }

    private static String simplifyTypeRef(String t) {
        if (t == null) {
            return "";
        }
        // ジェネリクスを除く
        int lt = t.indexOf('<');
        return (lt >= 0 ? t.substring(0, lt) : t).trim();
    }

    private static List<String> splitTopLevelCsv(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private PlantUmlClassDiagram() {
    }
}
