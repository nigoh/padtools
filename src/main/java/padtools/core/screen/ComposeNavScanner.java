package padtools.core.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jetpack Compose Navigation の遷移を Kotlin ソースから検出する。
 *
 * <p>検出パターン:</p>
 * <ul>
 *   <li>{@code NavHost(...) { ... }} ブロック内の {@code composable("route") { ... }}
 *       宣言 → "route" を画面ノードとして登録</li>
 *   <li>{@code navController.navigate("route")} 形式の呼び出し → 呼び出し元 Composable から
 *       "route" への遷移エッジを作成</li>
 *   <li>{@code popBackStack()} は遷移として記録しない (戻り操作)</li>
 * </ul>
 *
 * <p>呼び出し元の Composable 関数名は {@code @Composable fun Name(...)} を走査して特定。
 * navigate 呼び出しの位置を囲む最も内側の Composable 関数本体が caller となる。</p>
 *
 * <p>Intent ベース遷移検出 ({@link IntentNavigationDetector}) と独立に動作するので、
 * Compose と View ベース Activity 起動が混在するアプリでも両方の遷移をまとめて出せる。</p>
 */
public final class ComposeNavScanner {

    /** {@code composable("route") { ... }} - グループ 1: route 文字列。 */
    private static final Pattern COMPOSABLE_ROUTE = Pattern.compile(
            "\\bcomposable\\s*\\(\\s*(?:route\\s*=\\s*)?\"([^\"]+)\"");

    /** {@code navController.navigate("route")} - グループ 1: route 文字列。 */
    private static final Pattern NAVIGATE_CALL = Pattern.compile(
            "\\b(?:[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*navigate\\s*\\(\\s*\"([^\"]+)\"");

    /** {@code @Composable fun Name(...)} - グループ 1: composable 関数名。 */
    private static final Pattern COMPOSABLE_FUN = Pattern.compile(
            "@Composable[\\s\\n]+(?:public\\s+|private\\s+|internal\\s+)?fun\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

    /** {@code NavHost(navController, startDestination = "home") { ... }} - グループ 1: 開始 route。 */
    private static final Pattern NAVHOST_START_DEST = Pattern.compile(
            "\\bNavHost\\s*\\([^)]*startDestination\\s*=\\s*\"([^\"]+)\"");

    /** Compose 遷移走査結果。 */
    public static final class Result {
        private final List<ScreenTransition> transitions = new ArrayList<>();
        private final List<String> routes = new ArrayList<>();
        private String startDestination = "";

        public List<ScreenTransition> getTransitions() { return transitions; }
        /** {@code composable("...")} で宣言された全 route。 */
        public List<String> getRoutes() { return routes; }
        /** {@code NavHost(startDestination = "...")} で指定された初期 route。 */
        public String getStartDestination() { return startDestination; }
    }

    /** 1 つの Kotlin ソースから Compose 遷移情報を抽出する。 */
    public Result analyzeSource(String src, String filePath) {
        Result r = new Result();
        if (src == null || src.isEmpty()) return r;

        // start destination
        Matcher nm = NAVHOST_START_DEST.matcher(src);
        if (nm.find()) {
            r.startDestination = nm.group(1);
        }

        // 全 composable("route") を登録
        Matcher cm = COMPOSABLE_ROUTE.matcher(src);
        while (cm.find()) {
            String route = cm.group(1);
            if (!r.routes.contains(route)) {
                r.routes.add(route);
            }
        }

        // Composable 関数の span を収集 (呼び出し元の判定用)
        List<int[]> funSpans = new ArrayList<>();
        List<String> funNames = new ArrayList<>();
        Matcher fm = COMPOSABLE_FUN.matcher(src);
        while (fm.find()) {
            int parenStart = src.indexOf('(', fm.end() - 1);
            if (parenStart < 0) continue;
            int parenEnd = matchParen(src, parenStart);
            int braceStart = src.indexOf('{', parenEnd);
            if (braceStart < 0) continue;
            int braceEnd = matchBrace(src, braceStart);
            if (braceEnd > braceStart) {
                funSpans.add(new int[]{braceStart, braceEnd});
                funNames.add(fm.group(1));
            }
        }

        // composable("route") { ... } の本体 span も追加 (composable ラムダ内の navigate 用)
        Map<int[], String> composableSpanRoute = new LinkedHashMap<>();
        Matcher cm2 = COMPOSABLE_ROUTE.matcher(src);
        while (cm2.find()) {
            String route = cm2.group(1);
            // ) の次の { から本体開始
            int after = cm2.end();
            int closeParen = src.indexOf(')', after);
            if (closeParen < 0) continue;
            int braceStart = src.indexOf('{', closeParen);
            if (braceStart < 0) continue;
            int braceEnd = matchBrace(src, braceStart);
            if (braceEnd > braceStart) {
                int[] span = new int[]{braceStart, braceEnd};
                composableSpanRoute.put(span, route);
            }
        }

        // 呼び出し元クラスの FQN を推定 (package + 最初に出る class/object)
        String fqn = inferFqn(src);

        // navigate("route") を走査
        Matcher gm = NAVIGATE_CALL.matcher(src);
        while (gm.find()) {
            String targetRoute = gm.group(1);
            int line = lineOf(src, gm.start());
            // 呼び出し元の判定:
            // 1. composable("route") { ... } ブロック内なら、その route が "from"
            // 2. そうでなければ最も内側の @Composable fun 名を "from"
            String fromLabel = "";
            for (Map.Entry<int[], String> e : composableSpanRoute.entrySet()) {
                int[] sp = e.getKey();
                if (gm.start() >= sp[0] && gm.start() <= sp[1]) {
                    fromLabel = e.getValue();
                }
            }
            if (fromLabel.isEmpty()) {
                int[] enclosing = innermostSpan(gm.start(), funSpans);
                if (enclosing != null) {
                    int idx = funSpans.indexOf(enclosing);
                    fromLabel = funNames.get(idx);
                }
            }
            if (fromLabel.isEmpty()) {
                fromLabel = "(unknown)";
            }
            r.transitions.add(new ScreenTransition(
                    fqn, fromLabel, targetRoute,
                    filePath, line, ScreenTransition.Kind.START_ACTIVITY));
        }
        return r;
    }

    private static String inferFqn(String src) {
        Matcher pm = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;?\\s*$")
                .matcher(src);
        String pkg = pm.find() ? pm.group(1) : "";
        Matcher cm = Pattern.compile(
                "(?:public|protected|private|internal|final|abstract|open|sealed|data|inner)?"
                        + "\\s*(?:enum\\s+|annotation\\s+)?(?:class|interface|object)"
                        + "\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(src);
        String name = cm.find() ? cm.group(1) : "NavGraph";
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    private static int[] innermostSpan(int offset, List<int[]> spans) {
        int[] best = null;
        int bestLen = Integer.MAX_VALUE;
        for (int[] s : spans) {
            if (offset >= s[0] && offset <= s[1]) {
                int len = s[1] - s[0];
                if (len < bestLen) {
                    bestLen = len;
                    best = s;
                }
            }
        }
        return best;
    }

    private static int matchBrace(String src, int open) {
        return matchBalance(src, open, '{', '}');
    }

    private static int matchParen(String src, int open) {
        return matchBalance(src, open, '(', ')');
    }

    private static int matchBalance(String src, int open, char openCh, char closeCh) {
        if (open < 0 || open >= src.length() || src.charAt(open) != openCh) return open;
        int depth = 1;
        boolean inString = false;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < src.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == openCh) depth++;
            else if (c == closeCh) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
