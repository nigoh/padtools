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

        // 0. gradle/libs.versions.toml があれば先に読み込み、後段のパースで参照する
        VersionCatalog catalog = loadVersionCatalog(projectRoot, l);

        // 1. settings.gradle (root) を先にパースしてサブプロジェクト構造を取得
        for (File f : files) {
            String lower = f.getName().toLowerCase();
            if (lower.equals("settings.gradle") || lower.equals("settings.gradle.kts")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                GradleProjectInfo settings = GradleScriptParser.parse(content,
                        f.getName(), l, catalog);
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
                    info = GradleScriptParser.parse(content, f.getName(), l, catalog);
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
                info.setSourceSet(inferSourceSet(f));
                analysis.getManifestsByModule()
                        .computeIfAbsent(moduleName, k -> new ArrayList<>())
                        .add(info);
            }
        }

        // モダンな AndroidManifest は package 属性を持たないことがある (build.gradle の
        // namespace で代用される)。同モジュールの gradle.namespace を反映してコンポーネント
        // FQN を再解決する。
        backfillPackageFromGradle(analysis);

        int gradleCount = analysis.getGradleByModule().size();
        int manifestCount = analysis.allManifests().size();
        l.onError(null, -1,
                "android analyzer: " + gradleCount + " gradle file(s), "
                        + manifestCount + " manifest(s)");

        return analysis;
    }

    private static void backfillPackageFromGradle(AndroidProjectAnalysis analysis) {
        for (java.util.Map.Entry<String, java.util.List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            GradleProjectInfo gradle = analysis.getGradleByModule().get(e.getKey());
            if (gradle == null || gradle.getNamespace() == null) {
                continue;
            }
            String ns = gradle.getNamespace();
            for (AndroidManifestInfo info : e.getValue()) {
                if (info.getPackageName() == null || info.getPackageName().isEmpty()) {
                    info.setPackageName(ns);
                    // 既に保持しているコンポーネントの FQN を再解決
                    rewriteFqn(info);
                }
            }
        }
    }

    private static void rewriteFqn(AndroidManifestInfo info) {
        if (info.getApplicationClass() != null
                && info.getApplicationClass().startsWith(".")) {
            info.setApplicationClass(info.getPackageName() + info.getApplicationClass());
        }
        for (AndroidComponentInfo c : info.allComponents()) {
            String n = c.getName();
            if (n == null) {
                continue;
            }
            if (n.startsWith(".")) {
                c.setName(info.getPackageName() + n);
            } else if (!n.contains(".")) {
                c.setName(info.getPackageName() + "." + n);
            }
        }
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
     * プロジェクトルートの {@code gradle/libs.versions.toml} を読み込み、
     * {@link VersionCatalog} に変換する。存在しなければ null。
     */
    static VersionCatalog loadVersionCatalog(File projectRoot, ErrorListener l) {
        File toml = new File(projectRoot, "gradle/libs.versions.toml");
        if (!toml.isFile()) {
            return null;
        }
        String content = safeRead(toml, l);
        if (content == null) {
            return null;
        }
        VersionCatalog catalog = VersionCatalogParser.parse(content, l);
        l.onError(null, -1,
                "version catalog: " + catalog.getVersions().size() + " versions, "
                        + catalog.getLibraries().size() + " libraries, "
                        + catalog.getPlugins().size() + " plugins");
        return catalog;
    }

    /**
     * AndroidManifest.xml のパスから所属する Gradle ソースセット名を推定する。
     * {@code src/<name>/AndroidManifest.xml} の {@code <name>} (main/debug/release/flavor)。
     * 該当しなければ {@code "main"}。
     */
    static String inferSourceSet(File manifestFile) {
        File parent = manifestFile.getParentFile();
        if (parent == null) {
            return "main";
        }
        File grand = parent.getParentFile();
        if (grand != null && "src".equals(grand.getName())) {
            return parent.getName();
        }
        return "main";
    }

    /**
     * ファイルのプロジェクトルートからの相対位置からモジュール名を推定する。
     * 例: {@code /root/app/build.gradle} → {@code app}、
     * {@code /root/core/common/build.gradle} → {@code core:common}、
     * {@code /root/build.gradle} → {@code :root}、
     * {@code /root/app/src/main/AndroidManifest.xml} → {@code app}
     * (src/main 以下はモジュール内のソースセットなのでモジュール名から除外)。
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
                // ファイル名を除いた親ディレクトリのパスを取り出す
                int lastSep = rel.lastIndexOf(File.separatorChar);
                if (lastSep < 0) {
                    return ":root";
                }
                String dir = rel.substring(0, lastSep);
                // src/main, src/debug, src/<flavor> 以降を取り除く
                int srcIdx = dir.indexOf(File.separator + "src" + File.separator);
                if (srcIdx >= 0) {
                    dir = dir.substring(0, srcIdx);
                } else if (dir.equals("src") || dir.startsWith("src" + File.separator)) {
                    dir = "";
                }
                if (dir.isEmpty()) {
                    return ":root";
                }
                return dir.replace(File.separatorChar, ':');
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
