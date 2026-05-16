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
        /** 凡例ブロックをダイアグラム右に追加する。 */
        public boolean includeLegend = true;
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
        // クラスごとに一意のエイリアスを発行する。PlantUML は "a.b.c" 形式の識別子を
        // ネスト/名前空間として解釈してしまうため、引用符付き名 + as エイリアスで切り離す。
        Set<String> knownNames = new HashSet<>();
        java.util.Map<String, String> aliasByQn = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> qnBySimple = new java.util.HashMap<>();
        int aliasSeq = 0;
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            knownNames.add(qn);
            aliasByQn.put(qn, "C" + (aliasSeq++));
            qnBySimple.putIfAbsent(c.getSimpleName(), qn);
            if (c.getEnclosingClass() != null && !c.getEnclosingClass().isEmpty()) {
                qnBySimple.putIfAbsent(
                        c.getEnclosingClass() + "." + c.getSimpleName(), qn);
            }
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
                        emitClass(out, c, o, "", aliasByQn);
                    }
                } else {
                    out.append("package \"").append(pkg).append("\" {\n");
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "  ", aliasByQn);
                    }
                    out.append("}\n");
                }
            }
        } else {
            for (JavaClassInfo c : classes) {
                emitClass(out, c, o, "", aliasByQn);
            }
        }

        // 関係線
        if (o.showInheritance) {
            for (JavaClassInfo c : classes) {
                emitInheritance(out, c, aliasByQn, qnBySimple);
            }
        }
        if (o.showUsageRelations) {
            for (JavaClassInfo c : classes) {
                emitUsage(out, c, knownNames, aliasByQn, qnBySimple, o);
            }
        }
        if (o.includeLegend) {
            emitLegend(out, classes, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * ダイアグラム末尾に凡例ブロックを書き出す。実際に出現するステレオタイプや関係のみを
     * 列挙して、不要な行を増やさないようにする。
     */
    private static void emitLegend(StringBuilder out, List<JavaClassInfo> classes,
                                    Options o) {
        // 利用関係を実際に発行するかは emit ロジックと同じフィルタで判定
        Set<String> known = new HashSet<>();
        for (JavaClassInfo c : classes) {
            known.add(c.getQualifiedName());
        }
        // 出現するステレオタイプを収集
        Set<String> stereos = new LinkedHashSet<>();
        boolean hasAbstractClass = false;
        boolean hasInterface = false;
        boolean hasEnum = false;
        boolean hasAnnotation = false;
        boolean hasStatic = false;
        boolean hasAbstractMember = false;
        boolean hasInheritance = false;
        boolean hasImplements = false;
        boolean hasUsage = false;
        for (JavaClassInfo c : classes) {
            if (o.markAaosCategories) {
                String cat = c.getAaosCategory();
                if (cat == null) {
                    cat = AaosPattern.categorize(c);
                }
                if (cat != null) {
                    stereos.add(cat);
                }
            }
            if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
                stereos.add("aidl");
            }
            switch (c.getKind()) {
                case INTERFACE:
                case AIDL_INTERFACE:
                    hasInterface = true;
                    break;
                case ENUM:
                    hasEnum = true;
                    break;
                case ANNOTATION:
                    hasAnnotation = true;
                    break;
                case CLASS:
                default:
                    if (c.isAbstract()) {
                        hasAbstractClass = true;
                    }
                    break;
            }
            if (c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
                hasInheritance = true;
            }
            if (!c.getInterfaces().isEmpty()) {
                hasImplements = true;
            }
            for (JavaFieldInfo f : c.getFields()) {
                if (f.isStatic()) {
                    hasStatic = true;
                }
                // emitUsage と同じ条件で「実際に矢印を引くか」を判定する
                if (!hasUsage) {
                    String tgt = pickUsageTarget(f.getType(), known);
                    if (tgt != null && !tgt.equals(c.getQualifiedName())
                            && !tgt.equals(c.getSimpleName())) {
                        hasUsage = true;
                    }
                }
            }
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.isStatic()) {
                    hasStatic = true;
                }
                if (m.isAbstract()) {
                    hasAbstractMember = true;
                }
            }
        }

        out.append("legend right\n");
        if (o.showVisibility) {
            out.append("== 可視性 ==\n");
            out.append("+ public\n");
            out.append("- private\n");
            out.append("# protected\n");
            out.append("~ package-private\n");
        }
        if (hasStatic || hasAbstractMember) {
            out.append("== メンバー修飾 ==\n");
            if (hasStatic) {
                out.append("{static}    静的 (static)\n");
            }
            if (hasAbstractMember) {
                out.append("{abstract}  抽象 (abstract)\n");
            }
        }
        if (hasAbstractClass || hasInterface || hasEnum || hasAnnotation) {
            out.append("== クラス種別 ==\n");
            out.append("class        通常クラス\n");
            if (hasAbstractClass) {
                out.append("abstract     抽象クラス\n");
            }
            if (hasInterface) {
                out.append("interface    インタフェース\n");
            }
            if (hasEnum) {
                out.append("enum         列挙型\n");
            }
            if (hasAnnotation) {
                out.append("annotation   アノテーション型\n");
            }
        }
        if (!stereos.isEmpty()) {
            out.append("== AAOS ステレオタイプ ==\n");
            for (String s : stereos) {
                out.append("<<").append(s).append(">> ").append(stereoDesc(s)).append('\n');
            }
        }
        Set<String> androidStereos = new LinkedHashSet<>();
        for (JavaClassInfo c : classes) {
            String t = c.getAndroidComponentType();
            if (t != null && !t.isEmpty()) {
                androidStereos.add(t);
            }
        }
        if (!androidStereos.isEmpty()) {
            out.append("== Android コンポーネント ==\n");
            for (String s : androidStereos) {
                out.append("<<").append(s).append(">> ").append(androidStereoDesc(s)).append('\n');
            }
        }
        boolean anyRelation = (o.showInheritance && (hasInheritance || hasImplements))
                || (o.showUsageRelations && hasUsage);
        if (anyRelation) {
            out.append("== 関係 ==\n");
            if (o.showInheritance && hasInheritance) {
                out.append("A <|-- B  : B extends A (継承)\n");
            }
            if (o.showInheritance && hasImplements) {
                out.append("A <|.. B  : B implements A (実装)\n");
            }
            if (o.showUsageRelations && hasUsage) {
                out.append("A --> B   : A uses B (利用関係)\n");
            }
        }
        out.append("endlegend\n");
    }

    private static String stereoDesc(String stereo) {
        switch (stereo) {
            case "CarManager": return "AAOS の Car*Manager クラス";
            case "CarService": return "AAOS の Car*Service クラス";
            case "ICarInterface": return "ICar* 命名規約の AIDL 派生インタフェース";
            case "AIDL": return "AIDL ファイル由来のインタフェース";
            case "AaosApi": return "@AddedIn 等の AAOS API アノテーション付きクラス";
            case "aidl": return "AIDL 由来 (補助)";
            default: return stereo;
        }
    }

    private static String androidStereoDesc(String stereo) {
        switch (stereo) {
            case "Activity": return "AndroidManifest.xml の <activity>";
            case "Service": return "AndroidManifest.xml の <service>";
            case "BroadcastReceiver": return "AndroidManifest.xml の <receiver>";
            case "ContentProvider": return "AndroidManifest.xml の <provider>";
            default: return stereo;
        }
    }

    private static void emitClass(StringBuilder out, JavaClassInfo c,
                                  Options o, String indent,
                                  java.util.Map<String, String> aliasByQn) {
        String kw = classKeyword(c);
        String stereo = stereotype(c, o);
        out.append(indent).append(kw).append(' ');
        out.append(quoteId(displayId(c)));
        String alias = aliasByQn.get(c.getQualifiedName());
        if (alias != null) {
            out.append(" as ").append(alias);
        }
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
        if (c.getAndroidComponentType() != null && !c.getAndroidComponentType().isEmpty()) {
            parts.add(c.getAndroidComponentType());
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

    private static void emitInheritance(StringBuilder out, JavaClassInfo c,
                                         java.util.Map<String, String> aliasByQn,
                                         java.util.Map<String, String> qnBySimple) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        if (c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
            String parent = relationId(simplifyTypeRef(c.getSuperClass()), aliasByQn, qnBySimple);
            out.append(parent).append(" <|-- ").append(me).append('\n');
        }
        for (String iface : c.getInterfaces()) {
            String parent = relationId(simplifyTypeRef(iface), aliasByQn, qnBySimple);
            out.append(parent).append(" <|.. ").append(me).append('\n');
        }
    }

    private static void emitUsage(StringBuilder out, JavaClassInfo c,
                                   Set<String> known,
                                   java.util.Map<String, String> aliasByQn,
                                   java.util.Map<String, String> qnBySimple,
                                   Options o) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
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
            String tid = relationId(target, aliasByQn, qnBySimple);
            // 自己参照スキップ
            if (tid.equals(me)) {
                continue;
            }
            if (emitted.add(tid)) {
                out.append(me).append(" --> ").append(tid).append('\n');
                count++;
            }
        }
    }

    /**
     * 関係性の片端の識別子を返す。
     * - 完全修飾名で既知ならそのエイリアス
     * - 単純名で既知なら対応する完全修飾名のエイリアス
     * - 既知ではないなら引用符付き名 (PlantUML が暗黙生成)
     */
    private static String relationId(String typeRef,
                                      java.util.Map<String, String> aliasByQn,
                                      java.util.Map<String, String> qnBySimple) {
        if (typeRef == null || typeRef.isEmpty()) {
            return "\"?\"";
        }
        String alias = aliasByQn.get(typeRef);
        if (alias != null) {
            return alias;
        }
        String qn = qnBySimple.get(typeRef);
        if (qn != null) {
            String a = aliasByQn.get(qn);
            if (a != null) {
                return a;
            }
        }
        // 未定義: PlantUML に暗黙作成させる。"a.b.C" 形式は namespace 扱いされうるため
        // 末尾の単純名のみを使う。
        String simple = typeRef;
        int lastDot = simple.lastIndexOf('.');
        if (lastDot >= 0) {
            simple = simple.substring(lastDot + 1);
        }
        return quoteId(simple);
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
