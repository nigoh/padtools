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

    /** メソッド開始位置検出パターン (大まかな抽出)。 */
    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile(
            "(?:public|protected|private|static|final|synchronized|abstract|@\\w+\\s*)*\\s*"
                    + "[A-Za-z_$<][A-Za-z0-9_$<>,\\s\\[\\]\\?]*\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^)]*\\)\\s*(?:throws[^{]*)?\\{");

    /** プロジェクト全体をスキャンして全画面遷移を抽出する。 */
    public List<ScreenTransition> analyzeProject(File projectRoot) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeAidl = false;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        List<ScreenTransition> all = new ArrayList<>();
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java")) continue;
            try {
                String src = AndroidProjectScanner.readFile(f);
                all.addAll(analyzeSource(src, f.getPath()));
            } catch (IOException ex) {
                // skip unreadable
            }
        }
        return all;
    }

    /** 単一ソースから画面遷移を抽出する (テスト用)。 */
    public List<ScreenTransition> analyzeSource(String src, String filePath) {
        List<ScreenTransition> out = new ArrayList<>();
        if (src == null || src.isEmpty()) return out;
        String packageName = readPackage(src);
        String className = readPrimaryClassName(src);
        String fqn = packageName.isEmpty() ? className : packageName + "." + className;

        // メソッド開始位置を集める
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

        // 各 Intent パターンを走査
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                NEW_INTENT, ScreenTransition.Kind.START_ACTIVITY);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                SET_CLASS, ScreenTransition.Kind.SET_CLASS);
        addMatches(src, filePath, fqn, methodSpans, methodNames, out,
                SET_CLASS_NAME, ScreenTransition.Kind.SET_CLASS);

        // startActivityForResult が近接にあれば Kind を昇格 (簡易検出)
        promoteForResultTransitions(src, out);
        return out;
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
     * START_ACTIVITY として記録された遷移のうち、近接 (前後 200 文字以内) に
     * {@code startActivityForResult} があれば Kind を昇格させる。
     */
    private static void promoteForResultTransitions(String src, List<ScreenTransition> out) {
        for (int i = 0; i < out.size(); i++) {
            ScreenTransition t = out.get(i);
            if (t.getKind() != ScreenTransition.Kind.START_ACTIVITY) continue;
            int center = lineToOffset(src, t.getLineHint());
            if (center < 0) continue;
            int from = Math.max(0, center - 200);
            int to = Math.min(src.length(), center + 200);
            String window = src.substring(from, to);
            if (window.contains("startActivityForResult")
                    || window.contains("registerForActivityResult")) {
                out.set(i, new ScreenTransition(t.getFromFqn(), t.getFromMethod(),
                        t.getTargetClassName(), t.getFile(), t.getLineHint(),
                        ScreenTransition.Kind.START_FOR_RESULT));
            }
        }
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
        Matcher m = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;",
                Pattern.MULTILINE).matcher(src);
        return m.find() ? m.group(1) : "";
    }

    private static String readPrimaryClassName(String src) {
        Matcher m = Pattern.compile(
                "(?:public\\s+)?(?:final\\s+|abstract\\s+)?"
                        + "(?:class|interface|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(src);
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
