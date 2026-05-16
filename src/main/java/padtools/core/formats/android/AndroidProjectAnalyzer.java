package padtools.core.formats.android;

import padtools.core.formats.java.AndroidProjectScanner;
import padtools.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Android Gradle プロジェクトを走査し、{@link AndroidProjectAnalysis} を返すファサード。
 *
 * <p>{@link AndroidProjectScanner} を内部で再利用し、{@code includeGradle = true} かつ
 * {@code includeManifest = true} でスキャンする。settings.gradle を最初にパースして
 * サブプロジェクト構造を把握し、その後各モジュールの build.gradle と AndroidManifest.xml
 * を読み込む。</p>
 */
public final class AndroidProjectAnalyzer {

    /** デフォルト (silent) リスナーで解析。 */
    public static AndroidProjectAnalysis analyze(File projectRoot) throws IOException {
        return analyze(projectRoot, null);
    }

    /** リスナー付き解析。個別ファイルの IO 失敗は listener に通知して継続。 */
    public static AndroidProjectAnalysis analyze(File projectRoot,
                                                  ErrorListener listener) throws IOException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeGradle = true;
        opts.includeManifest = true;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);

        // 1. settings.gradle (root) を先にパースしてサブプロジェクト構造を取得
        for (File f : files) {
            String lower = f.getName().toLowerCase();
            if (lower.equals("settings.gradle") || lower.equals("settings.gradle.kts")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                GradleProjectInfo settings = GradleScriptParser.parse(content, f.getName(), l);
                settings.setModuleName(":root");
                analysis.setRootSettings(settings);
            }
        }

        // 2. 各 build.gradle と manifest をパース
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.equals("settings.gradle") || name.equals("settings.gradle.kts")) {
                continue;
            }
            String moduleName = inferModuleName(projectRoot, f);
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                GradleProjectInfo info;
                try {
                    info = GradleScriptParser.parse(content, f.getName(), l);
                } catch (RuntimeException ex) {
                    l.onError(f.getName(), -1, "gradle parse failed: " + ex.getMessage());
                    continue;
                }
                info.setModuleName(moduleName);
                analysis.getGradleByModule().put(moduleName, info);
            } else if (name.equals("androidmanifest.xml")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                AndroidManifestInfo info;
                try {
                    info = AndroidManifestParser.parse(content, l);
                } catch (RuntimeException ex) {
                    l.onError(f.getName(), -1, "manifest parse failed: " + ex.getMessage());
                    continue;
                }
                analysis.getManifestsByModule()
                        .computeIfAbsent(moduleName, k -> new ArrayList<>())
                        .add(info);
            }
        }

        int gradleCount = analysis.getGradleByModule().size();
        int manifestCount = analysis.allManifests().size();
        l.onError(null, -1,
                "android analyzer: " + gradleCount + " gradle file(s), "
                        + manifestCount + " manifest(s)");

        return analysis;
    }

    private static String safeRead(File f, ErrorListener l) {
        try {
            return AndroidProjectScanner.readFile(f);
        } catch (IOException ex) {
            l.onError(f.getName(), -1, "read failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * ファイルのプロジェクトルートからの相対位置からモジュール名を推定する。
     * 例: {@code /root/app/build.gradle} → {@code app}、
     * {@code /root/build.gradle} → {@code :root}。
     */
    static String inferModuleName(File root, File f) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = f.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String rel = filePath.substring(rootPath.length());
                if (rel.startsWith(File.separator)) {
                    rel = rel.substring(1);
                }
                int sep = rel.indexOf(File.separatorChar);
                if (sep < 0) {
                    return ":root";
                }
                return rel.substring(0, sep);
            }
        } catch (IOException ignore) {
            // フォールバック
        }
        File parent = f.getParentFile();
        return parent == null ? ":root" : parent.getName();
    }

    private AndroidProjectAnalyzer() {
    }
}
