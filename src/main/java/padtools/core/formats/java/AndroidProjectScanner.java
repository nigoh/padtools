package padtools.core.formats.java;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android/通常の Gradle プロジェクトディレクトリから Java ソースファイルを再帰的に列挙する。
 *
 * <p>標準的な Gradle プロジェクトレイアウト (src/main/java) と Android プロジェクトレイアウト
 * (app/src/main/java, モジュール/src/main/java) を考慮した収集を行う。</p>
 *
 * <p>主な利用方法:</p>
 * <pre>{@code
 *   List<File> javaFiles = AndroidProjectScanner.scan(new File("/path/to/MyApp"));
 *   StringBuilder sb = new StringBuilder();
 *   for (File f : javaFiles) {
 *       String src = AndroidProjectScanner.readFile(f);
 *       sb.append(JavaSourceConverter.convert(src));
 *       sb.append("\n");
 *   }
 * }</pre>
 */
public final class AndroidProjectScanner {

    /** 除外するディレクトリ名 (デフォルト)。 */
    public static final Set<String> DEFAULT_EXCLUDED_DIRS;
    static {
        Set<String> s = new HashSet<>(Arrays.asList(
                "build", ".gradle", ".idea", ".git", "out", "bin",
                "node_modules", ".kotlin", "captures", ".cxx"));
        DEFAULT_EXCLUDED_DIRS = Collections.unmodifiableSet(s);
    }

    /** スキャンオプション。 */
    public static class Options {
        /** テストソース (src/test/java, src/androidTest/java) を含める。 */
        public boolean includeTests = false;
        /** Kotlin ファイルも含める (現状は変換非対応のため、ファイルリストのみ)。 */
        public boolean includeKotlin = false;
        /** AIDL (.aidl) ファイルも含める。 */
        public boolean includeAidl = false;
        /** 除外ディレクトリ名のセット。 */
        public Set<String> excludedDirs = DEFAULT_EXCLUDED_DIRS;
        /** 最大再帰深さ (負値で無制限)。 */
        public int maxDepth = -1;
    }

    /** デフォルト Options でスキャン。 */
    public static List<File> scan(File root) {
        return scan(root, null);
    }

    /** オプション付きスキャン。 */
    public static List<File> scan(File root, Options opts) {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        Options o = (opts == null) ? new Options() : opts;
        List<File> result = new ArrayList<>();
        if (!root.exists()) {
            return result;
        }
        if (root.isFile()) {
            if (accept(root, o)) {
                result.add(root);
            }
            return result;
        }
        walk(root, o, 0, result);
        Collections.sort(result);
        return result;
    }

    private static void walk(File dir, Options opts, int depth, List<File> result) {
        if (opts.maxDepth >= 0 && depth > opts.maxDepth) {
            return;
        }
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File f : entries) {
            if (f.isDirectory()) {
                if (shouldExcludeDir(f, opts)) {
                    continue;
                }
                walk(f, opts, depth + 1, result);
            } else if (f.isFile()) {
                if (accept(f, opts)) {
                    result.add(f);
                }
            }
        }
    }

    private static boolean shouldExcludeDir(File dir, Options opts) {
        String name = dir.getName();
        if (opts.excludedDirs != null && opts.excludedDirs.contains(name)) {
            return true;
        }
        if (!opts.includeTests && isTestDir(dir)) {
            return true;
        }
        return false;
    }

    private static boolean isTestDir(File dir) {
        // src/test/... または src/androidTest/... のディレクトリを判定
        String name = dir.getName();
        if (!"test".equals(name) && !"androidTest".equals(name)) {
            return false;
        }
        File parent = dir.getParentFile();
        return parent != null && "src".equals(parent.getName());
    }

    private static boolean accept(File f, Options opts) {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".java")) {
            return true;
        }
        if (opts.includeKotlin && (name.endsWith(".kt") || name.endsWith(".kts"))) {
            return true;
        }
        if (opts.includeAidl && name.endsWith(".aidl")) {
            return true;
        }
        return false;
    }

    /** ファイルを UTF-8 で読み込んで文字列として返す。 */
    public static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * プロジェクトディレクトリをスキャンし、全 Java ソースを SPD 形式に変換した文字列を返す。
     * @param projectRoot プロジェクトルート (もしくは単一の .java ファイル)
     * @param scanOpts スキャンオプション (null 可)
     * @param convOpts 変換オプション (null 可)
     * @return SPD 文字列
     * @throws IOException ファイル読込エラー
     */
    public static String convertProject(File projectRoot,
                                        Options scanOpts,
                                        JavaSourceConverter.Options convOpts) throws IOException {
        List<File> files = scan(projectRoot, scanOpts);
        StringBuilder out = new StringBuilder();
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".java")) {
                continue;
            }
            String src = readFile(f);
            String spd = JavaSourceConverter.convert(src, convOpts);
            if (spd.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("# === ").append(f.getName()).append(" ===\n");
            out.append(spd);
        }
        return out.toString();
    }

    private AndroidProjectScanner() {
        // ユーティリティクラス
    }
}
