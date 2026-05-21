package padtools.core.screen;

import padtools.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android アプリの画面遷移を Java ソースから検出する。
 *
 * <p>対象: {@code startActivity(new Intent(ctx, X.class))} および類似形式の Intent
 * 構築呼び出し。NavGraph XML (Jetpack Navigation Component) は別パイプライン
 * ({@link padtools.core.formats.android.AndroidNavigationGraphParser}) が扱う。</p>
 *
 * <p>検出パターン:</p>
 * <ol>
 *   <li>{@code new Intent(<expr>, <ClassName>.class)} — 明示 Intent 構築</li>
 *   <li>{@code .setClass(<expr>, <ClassName>.class)} — 既存 Intent の付け替え</li>
 *   <li>{@code .setClassName(<expr>, "<ClassName>")} — 文字列形式</li>
 *   <li>{@code startActivityForResult} 周辺: 上記パターンが見つかれば
 *       Kind は START_FOR_RESULT で記録</li>
 * </ol>
 *
 * <p>制約: 単純な regex 解析なので、Builder パターンや変数を介した
 * Intent ({@code Intent intent = new Intent(); intent.setClass(...)}) はそのまま
 * 1 件として記録。複数 startActivity への展開は行わない。</p>
 */
public final class IntentNavigationDetector {

    /** {@code new Intent(ctx, X.class)} を捕まえる。グループ 1: target クラス。 */
    private static final Pattern NEW_INTENT = Pattern.compile(
            "new\\s+Intent\\s*\\(\\s*[^,]+,\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$.]*)\\s*\\.\\s*class\\s*\\)");

    /** {@code .setClass(ctx, X.class)} を捕まえる。グループ 1: target クラス。 */
    private static final Pattern SET_CLASS = Pattern.compile(
            "\\.\\s*setClass\\s*\\(\\s*[^,]+,\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$.]*)\\s*\\.\\s*class\\s*\\)");

    /** {@code .setClassName(ctx, "com.x.Foo")} を捕まえる。グループ 1: target クラス文字列。 */
    private static final Pattern SET_CLASS_NAME = Pattern.compile(
            "\\.\\s*setClassName\\s*\\(\\s*[^,]+,\\s*\"([^\"]+)\"\\s*\\)");

    /**
     * Kotlin の {@code Intent(ctx, X::class.java)} 形式。グループ 1: target クラス。
     * Java の {@code new Intent(...)} と異なり {@code new} は付かない。
     */
    private static final Pattern KOTLIN_NEW_INTENT = Pattern.compile(
            "\\bIntent\\s*\\(\\s*[^,]+,\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$.]*)::class\\.java\\s*\\)");

    /** Kotlin の {@code .setClass(ctx, X::class.java)} 形式。 */
    private static final Pattern KOTLIN_SET_CLASS = Pattern.compile(
            "\\.\\s*setClass\\s*\\(\\s*[^,]+,\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$.]*)::class\\.java\\s*\\)");

    /**
     * Car App Library の {@code .push(new XxxScreen(...))} / {@code .pushForResult(new XxxScreen(...))}。
     * グループ 1: push される Screen クラス。Java/Kotlin 共通 ({@code new} の有無を許容)。
     */
    private static final Pattern SCREEN_PUSH_NEW = Pattern.compile(
            "\\.\\s*push(?:ForResult)?\\s*\\(\\s*(?:new\\s+)?"
                    + "([A-Z][A-Za-z0-9_$.]*)\\s*\\(");

    /** メソッド開始位置検出パターン (大まかな抽出)。 */
    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile(
            "(?:public|protected|private|static|final|synchronized|abstract|@\\w+\\s*)*\\s*"
                    + "[A-Za-z_$<][A-Za-z0-9_$<>,\\s\\[\\]\\?]*\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^)]*\\)\\s*(?:throws[^{]*)?\\{");

    /** Kotlin の {@code fun name(...): ReturnType { ... }} 形式。 */
    private static final Pattern KOTLIN_FUN_DECL_PATTERN = Pattern.compile(
            "\\bfun\\s+(?:<[^>]+>\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^)]*\\)"
                    + "(?:\\s*:\\s*[^{=]+)?\\s*\\{");

    /**
     * プロジェクト全体をスキャンして全画面遷移を抽出する (Java + Kotlin + Compose)。
     *
     * <p>Java/Kotlin の Intent ベース遷移と、Kotlin の Jetpack Compose NavHost
     * 宣言的遷移 ({@link ComposeNavScanner}) を統合して返す。</p>
     */
    public List<ScreenTransition> analyzeProject(File projectRoot) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeAidl = false;
        opts.includeKotlin = true;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        List<ScreenTransition> all = new ArrayList<>();
        ComposeNavScanner compose = new ComposeNavScanner();
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
            try {
                String src = AndroidProjectScanner.readFile(f);
                all.addAll(analyzeSource(src, f.getPath()));
                if (name.endsWith(".kt")) {
                    all.addAll(compose.analyzeSource(src, f.getPath()).getTransitions());
                }
            } catch (IOException ex) {
                // skip unreadable
            }
        }
        return all;
    }

    /** 単一ソースから画面遷移を抽出する (テスト用)。 */
    public List<ScreenTransition> analyzeSource(String rawSrc, String filePath) {
        List<ScreenTransition> out = new ArrayList<>();
        if (rawSrc == null || rawSrc.isEmpty()) return out;
        // コメントを空白化（長さ・改行は保持）してから走査する。
        // コメント中の "class" 等がクラス名/メソッド検出を誤らせるのを防ぐ。
        String src = blankComments(rawSrc);
        String packageName = readPackage(src);
        String className = readPrimaryClassName(src);
        String fqn = packageName.isEmpty() ? className : packageName + "." + className;

        // メソッド開始位置を集める (Java + Kotlin)
        List<int[]> methodSpans = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        Matcher mm = METHOD_DECL_PATTERN.matcher(src);
        while (mm.find()) {
            int braceStart = mm.end() - 1;
            int braceEnd = findMatchingBrace(src, braceStart);
            if (braceEnd > braceStart) {
                methodSpans.add(new int[]{braceStart, braceEnd});
                methodNames.add(mm.group(1));
            }
        }
        Matcher km = KOTLIN_FUN_DECL_PATTERN.matcher(src);
        while (km.find()) {
            int braceStart = km.end() - 1;
            int braceEnd = findMatchingBrace(src, braceStart);
            if (braceEnd > braceStart) {
                methodSpans.add(new int[]{braceStart, braceEnd});
                methodNames.add(km.group(1));
            }
        }

        // 各 Intent パターンを走査 (Java + Kotlin)
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                NEW_INTENT, ScreenTransition.Kind.START_ACTIVITY);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                SET_CLASS, ScreenTransition.Kind.SET_CLASS);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                SET_CLASS_NAME, ScreenTransition.Kind.SET_CLASS);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                KOTLIN_NEW_INTENT, ScreenTransition.Kind.START_ACTIVITY);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                KOTLIN_SET_CLASS, ScreenTransition.Kind.SET_CLASS);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                SCREEN_PUSH_NEW, ScreenTransition.Kind.SCREEN_PUSH);

        // startActivityForResult が同一メソッド内にあれば Kind を昇格
        promoteForResultTransitions(src, out, methodSpans);
        return out;
    }

    /**
     * Java/Kotlin のコメント（{@code //}, {@code /* *​/}）を同じ長さの空白に置き換える。
     * 改行は保持するので、後続の {@code lineOf} による行番号計算はそのまま使える。
     * 文字列・文字リテラルの中身は保持する（{@code setClassName(ctx, "X")} 等を壊さない）。
     */
    static String blankComments(String src) {
        char[] a = src.toCharArray();
        int n = a.length;
        boolean inLine = false;
        boolean inBlock = false;
        boolean inStr = false;
        boolean inChar = false;
        for (int i = 0; i < n; i++) {
            char c = a[i];
            char next = i + 1 < n ? a[i + 1] : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                } else {
                    a[i] = ' ';
                }
            } else if (inBlock) {
                if (c == '*' && next == '/') {
                    a[i] = ' ';
                    a[i + 1] = ' ';
                    i++;
                    inBlock = false;
                } else if (c != '\n' && c != '\r') {
                    a[i] = ' ';
                }
            } else if (inStr) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (inChar) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
            } else if (c == '/' && next == '/') {
                a[i] = ' ';
                inLine = true;
            } else if (c == '/' && next == '*') {
                a[i] = ' ';
                a[i + 1] = ' ';
                i++;
                inBlock = true;
            } else if (c == '"') {
                inStr = true;
            } else if (c == '\'') {
                inChar = true;
            }
        }
        return new String(a);
    }

    private static void addMatches(String src, String filePath, String fromFqn,
                                     List<int[]> methodSpans, List<String> methodNames,
                                     List<ScreenTransition> out, Pattern pattern,
                                     ScreenTransition.Kind kind) {
        Matcher m = pattern.matcher(src);
        while (m.find()) {
            String target = m.group(1);
            int line = lineOf(src, m.start());
            String callerMethod = enclosingMethodName(m.start(), methodSpans, methodNames);
            out.add(new ScreenTransition(fromFqn, callerMethod, target,
                    filePath, line, kind));
        }
    }

    /**
     * START_ACTIVITY として記録された遷移のうち、同じメソッド本体内に
     * {@code startActivityForResult} / {@code registerForActivityResult} があれば
     * Kind を昇格させる。メソッド外 (フィールド初期化など) や別メソッドの呼び出しは無視。
     */
    private static void promoteForResultTransitions(String src,
                                                      List<ScreenTransition> out,
                                                      List<int[]> methodSpans) {
        for (int i = 0; i < out.size(); i++) {
            ScreenTransition t = out.get(i);
            if (t.getKind() != ScreenTransition.Kind.START_ACTIVITY) continue;
            int center = lineToOffset(src, t.getLineHint());
            if (center < 0) continue;
            // この offset を囲む最小のメソッド span を探す
            int[] enclosing = innermostSpanOf(center, methodSpans);
            int from;
            int to;
            if (enclosing != null) {
                from = enclosing[0];
                to = enclosing[1];
            } else {
                // メソッド外 (フィールド初期化など): 近接 100 文字程度のみ参照
                from = Math.max(0, center - 100);
                to = Math.min(src.length(), center + 100);
            }
            String window = src.substring(from, to);
            if (window.contains("startActivityForResult")
                    || window.contains("registerForActivityResult")) {
                out.set(i, new ScreenTransition(t.getFromFqn(), t.getFromMethod(),
                        t.getTargetClassName(), t.getFile(), t.getLineHint(),
                        ScreenTransition.Kind.START_FOR_RESULT));
            }
        }
    }

    /** offset を囲む最小スパン (最も内側のメソッド) を返す。 */
    private static int[] innermostSpanOf(int offset, List<int[]> spans) {
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

    /** 遷移先クラス → 遷移元クラス Set のマップを返す (集計用)。 */
    public static Map<String, java.util.Set<String>> incomingBySimpleName(
            List<ScreenTransition> transitions) {
        Map<String, java.util.Set<String>> result = new LinkedHashMap<>();
        for (ScreenTransition t : transitions) {
            String target = t.getTargetSimpleName();
            result.computeIfAbsent(target, k -> new java.util.LinkedHashSet<>())
                    .add(t.getFromSimpleName());
        }
        return result;
    }

    private static String readPackage(String src) {
        // Java は ; で終わる、Kotlin は ; なし。両方を許容。
        Matcher m = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;?\\s*$",
                Pattern.MULTILINE).matcher(src);
        return m.find() ? m.group(1) : "";
    }

    private static String readPrimaryClassName(String src) {
        // Java + Kotlin: class / interface / object / enum / data class / sealed class 等
        Matcher m = Pattern.compile(
                "(?:public|protected|private|internal|final|abstract|open|sealed|data|inner)?"
                        + "\\s*(?:enum\\s+|annotation\\s+)?(?:class|interface|object|enum)"
                        + "\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(src);
        return m.find() ? m.group(1) : "Unknown";
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static int lineToOffset(String src, int targetLine) {
        if (targetLine <= 1) return 0;
        int line = 1;
        for (int i = 0; i < src.length(); i++) {
            if (line == targetLine) return i;
            if (src.charAt(i) == '\n') line++;
        }
        return -1;
    }

    private static String enclosingMethodName(int offset, List<int[]> spans,
                                                List<String> names) {
        String found = "";
        for (int i = 0; i < spans.size(); i++) {
            int[] sp = spans.get(i);
            if (offset >= sp[0] && offset <= sp[1]) {
                found = names.get(i);
            }
        }
        return found;
    }

    private static int findMatchingBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        int depth = 1;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }
}
