package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SELinux ポリシーの {@code .te} ファイル簡易パーサ。
 *
 * <p>AOSP/AAOS の sepolicy ファイルを完全解釈するわけではなく、コンポーネント図および
 * MultiUser ロール分離レポートで必要となる以下の情報のみ抽出する:</p>
 * <ul>
 *   <li>{@code type <name>, <attr1>, <attr2>, ...;} — 型宣言と属性</li>
 *   <li>{@code allow <src> <tgt>:<class> <perm>;} もしくはブレース版
 *       {@code allow <src> <tgt>:<class> { <perm1> <perm2> ... };}</li>
 *   <li>{@code neverallow ...}, {@code dontaudit ...}, {@code auditallow ...} (同形式)</li>
 *   <li>{@code type_transition <src> <tgt>:<class> <newtype>;}</li>
 * </ul>
 *
 * <p>m4 マクロ、属性宣言 ({@code attribute}, {@code attribute_role}), 条件式
 * ({@code if/else}), permissive 宣言などは現状スキップする。</p>
 */
public final class SepolicyTeParser {

    private static final Pattern TYPE_DECL = Pattern.compile(
            "(?m)^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*((?:,\\s*[A-Za-z_][A-Za-z0-9_]*\\s*)*);");

    private static final Pattern RULE_HEAD = Pattern.compile(
            "(?m)^\\s*(allow|neverallow|dontaudit|auditallow)\\s+");

    private static final Pattern TYPE_TRANSITION = Pattern.compile(
            "(?m)^\\s*type_transition\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;");

    private SepolicyTeParser() {
    }

    /** {@code te} ソースをパースして {@link SepolicyInfo} を返す。 */
    public static SepolicyInfo parse(String source, String filePath) {
        SepolicyInfo info = new SepolicyInfo(filePath);
        if (source == null || source.isEmpty()) {
            return info;
        }
        String src = stripComments(source);
        parseTypes(src, info);
        parseTransitions(src, info);
        parseRules(src, info);
        return info;
    }

    private static void parseTypes(String src, SepolicyInfo info) {
        Matcher m = TYPE_DECL.matcher(src);
        while (m.find()) {
            String name = m.group(1);
            String attrs = m.group(2);
            List<String> attributes = new ArrayList<>();
            if (attrs != null && !attrs.isEmpty()) {
                for (String a : attrs.split(",")) {
                    String t = a.trim();
                    if (!t.isEmpty()) {
                        attributes.add(t);
                    }
                }
            }
            info.getTypes().add(new SepolicyType(name, attributes));
        }
    }

    private static void parseTransitions(String src, SepolicyInfo info) {
        Matcher m = TYPE_TRANSITION.matcher(src);
        while (m.find()) {
            info.getTransitions().add(new SepolicyTransition(
                    m.group(1), m.group(2), m.group(3), m.group(4)));
        }
    }

    private static void parseRules(String src, SepolicyInfo info) {
        Matcher m = RULE_HEAD.matcher(src);
        while (m.find()) {
            String ruleType = m.group(1);
            int from = m.end();
            int end = findStatementEnd(src, from);
            if (end < 0) {
                break;
            }
            String body = src.substring(from, end).trim();
            SepolicyRule rule = parseRuleBody(ruleType, body);
            if (rule == null) {
                continue;
            }
            if ("allow".equals(ruleType)) {
                info.getAllowRules().add(rule);
            } else if ("neverallow".equals(ruleType)) {
                info.getNeverallowRules().add(rule);
            }
            // dontaudit / auditallow は MVP の対象外 (保持はしない)
        }
    }

    /**
     * ルール本体 ({@code "carservice vehicle_hal:binder { call transfer };"} 形式から
     * 末尾 {@code ;} を除いたもの) をパースして {@link SepolicyRule} に変換する。
     */
    static SepolicyRule parseRuleBody(String ruleType, String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        String[] colonParts = body.split(":", 2);
        if (colonParts.length < 2) {
            return null;
        }
        String[] left = colonParts[0].trim().split("\\s+");
        if (left.length < 2) {
            return null;
        }
        String source = left[0];
        String target = left[1];
        String right = colonParts[1].trim();
        // class { perm1 perm2 } の形式
        int braceStart = right.indexOf('{');
        String objectClass;
        List<String> perms = new ArrayList<>();
        if (braceStart < 0) {
            // class perm
            String[] tokens = right.split("\\s+");
            if (tokens.length < 2) {
                return null;
            }
            objectClass = tokens[0];
            perms.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        } else {
            objectClass = right.substring(0, braceStart).trim();
            int braceEnd = right.indexOf('}', braceStart);
            if (braceEnd < 0) {
                return null;
            }
            String inner = right.substring(braceStart + 1, braceEnd).trim();
            for (String p : inner.split("\\s+")) {
                if (!p.isEmpty()) {
                    perms.add(p);
                }
            }
        }
        return new SepolicyRule(ruleType, source, target, objectClass, perms);
    }

    /**
     * 現在位置から {@code ;} 終端までの位置を返す ({@code ;} の前のインデックス)。
     * ブレース内のセミコロンは無視する (このパーサは sepolicy のブレースはルール 1 つにつき
     * 1 階層のみ想定)。
     */
    private static int findStatementEnd(String src, int from) {
        int brace = 0;
        int i = from;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '{') brace++;
            else if (c == '}' && brace > 0) brace--;
            else if (c == ';' && brace == 0) return i;
            i++;
        }
        return -1;
    }

    /**
     * sepolicy のコメント ({@code # ... \n} と {@code /* ... *&#47;}) を除去する。
     */
    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '#') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
