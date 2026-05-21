package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * クラス図の関係線 (継承/実装/利用) の出力と、型参照から利用対象クラスを推定する
 * 補助ロジックを集約する。{@link PlantUmlClassDiagram} 本体から関係性の解決を切り離す。
 * 型参照ユーティリティ ({@link #pickUsageTarget}) は他の図生成器からも再利用される。
 */
final class PlantUmlClassRelations {

    private PlantUmlClassRelations() {
    }

    static void emitInheritance(StringBuilder out, JavaClassInfo c,
                                         PlantUmlClassDiagram.Options o,
                                         java.util.Map<String, String> aliasByQn,
                                         java.util.Map<String, String> qnBySimple) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        if (o.showInheritance
                && c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
            String parent = relationId(simplifyTypeRef(c.getSuperClass()), aliasByQn, qnBySimple);
            out.append(parent).append(" <|-- ").append(me).append('\n');
        }
        if (o.showImplementations) {
            for (String iface : c.getInterfaces()) {
                String parent = relationId(simplifyTypeRef(iface), aliasByQn, qnBySimple);
                out.append(parent).append(" <|.. ").append(me).append('\n');
            }
        }
    }

    /**
     * topToBottomDirection 時に同一親を持つ兄弟ノードが横に広がりすぎるのを防ぐため、
     * {@link Options#maxSiblingsPerRow} 個ごとに隠しリンク ({@code -[hidden]->}) を挿入する。
     *
     * <p>グループ末尾 → 次グループ先頭 に hidden リンクを打つことで、
     * Graphviz/Smetana のランク割り当てが次グループを強制的に下のランクに押し出す。</p>
     */
    static void emitSiblingWrapHints(
            StringBuilder out, List<JavaClassInfo> classes,
            java.util.Map<String, String> aliasByQn,
            java.util.Map<String, String> qnBySimple,
            PlantUmlClassDiagram.Options o) {
        // parent alias → 子エイリアスの順序リスト (extends のみ追跡)
        java.util.Map<String, List<String>> childrenByParent = new java.util.LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (!o.showInheritance) break;
            if (c.getSuperClass() == null || c.getSuperClass().isEmpty()) continue;
            String childAlias = aliasByQn.get(c.getQualifiedName());
            if (childAlias == null) continue;
            String parentId = relationId(simplifyTypeRef(c.getSuperClass()), aliasByQn, qnBySimple);
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childAlias);
        }
        for (List<String> siblings : childrenByParent.values()) {
            if (siblings.size() <= o.maxSiblingsPerRow) continue;
            int n = o.maxSiblingsPerRow;
            // グループ境界ごとに hidden リンクを打つ
            for (int i = n; i < siblings.size(); i += n) {
                out.append(siblings.get(i - 1)).append(" -[hidden]-> ")
                   .append(siblings.get(i)).append('\n');
            }
        }
    }

    static void emitUsage(StringBuilder out, JavaClassInfo c,
                                   Set<String> known,
                                   java.util.Map<String, String> aliasByQn,
                                   java.util.Map<String, String> qnBySimple,
                                   PlantUmlClassDiagram.Options o) {
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
        return PlantUmlClassDiagram.quoteId(simple);
    }

    /** 型参照 (たとえば {@code Map<String, Foo>}) から、利用対象となるユーザ定義型を推定する。 */
    static String pickUsageTarget(String type, Set<String> known) {
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
        if (PlantUmlClassDiagram.PRIMITIVE_OR_BUILTIN.matcher(name).matches()) {
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
}
