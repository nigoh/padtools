package padtools.app.uml;

import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaClassInfoCodec;
import padtools.util.CacheKey;
import padtools.util.ProgressListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * プロジェクト解析結果 (Stage A ヘッダ + ソースファイルパス + モジュール紐付け) を
 * ディスクに永続化する軽量キャッシュ。
 *
 * <p>配置: {@code ~/.padtools/cache/<shortHash>/}</p>
 *
 * <p>ファイル:</p>
 * <ul>
 *   <li>{@code manifest.txt} — cacheVersion + 生成日時 + クラス数 + フルキー</li>
 *   <li>{@code classes.tsv}  — 1 行 1 クラスの Stage A 情報 ({@link JavaClassInfoCodec})</li>
 *   <li>{@code sources.tsv}  — {@code qn TAB sourcePath} (Stage B 昇格時の再パース元)</li>
 *   <li>{@code modules.tsv}  — {@code qn TAB moduleName}</li>
 * </ul>
 *
 * <p>キャッシュキーはプロジェクトルートと走査済みファイルの (path/mtime/size) ハッシュ。
 * 1 件でも変更があると別ディレクトリが使われるため、自動的に無効化される。</p>
 */
public final class PersistentAnalysisCache {

    public static final String CACHE_VERSION = "v1";

    private final File baseDir;

    public PersistentAnalysisCache() {
        this(defaultBaseDir());
    }

    public PersistentAnalysisCache(File baseDir) {
        this.baseDir = baseDir;
    }

    /** デフォルト配置先 ({@code ~/.padtools/cache} 等)。 */
    public static File defaultBaseDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isEmpty()) {
                return new File(local, "PadTools/cache");
            }
        }
        String home = System.getProperty("user.home", ".");
        return new File(home, ".padtools/cache");
    }

    /** ロード結果。 */
    public static final class Snapshot {
        private final List<JavaClassInfo> classes;
        private final ClassIndex index;
        private final String cacheKey;

        public Snapshot(List<JavaClassInfo> classes, ClassIndex index, String cacheKey) {
            this.classes = classes;
            this.index = index;
            this.cacheKey = cacheKey;
        }

        public List<JavaClassInfo> getClasses() {
            return classes;
        }

        public ClassIndex getIndex() {
            return index;
        }

        public String getCacheKey() {
            return cacheKey;
        }
    }

    /**
     * 指定プロジェクトの解析結果をディスクから復元する。
     *
     * <p>事前にプロジェクトルート + 同条件で集めた files から
     * {@link CacheKey#compute} でハッシュを再計算し、ディレクトリに格納された
     * manifest と一致するときだけ Hit。それ以外は {@link Optional#empty()}。</p>
     */
    public Optional<Snapshot> load(File projectRoot, AndroidProjectScanner.Options scanOpts,
                                    ProgressListener progress) {
        ProgressListener prog = progress != null ? progress : ProgressListener.silent();
        prog.onProgress(0, -1, "Probing cache...");
        List<File> files = AndroidProjectScanner.scan(projectRoot, scanOpts);
        String fullKey = CacheKey.compute(projectRoot, files);
        File dir = directoryFor(fullKey);
        if (!dir.isDirectory()) {
            return Optional.empty();
        }
        File manifest = new File(dir, "manifest.txt");
        File classesFile = new File(dir, "classes.tsv");
        File sourcesFile = new File(dir, "sources.tsv");
        File modulesFile = new File(dir, "modules.tsv");
        if (!manifest.isFile() || !classesFile.isFile()) {
            return Optional.empty();
        }
        try {
            String[] header = readLines(manifest).toArray(new String[0]);
            String version = null;
            String storedKey = null;
            for (String line : header) {
                if (line.startsWith("cacheVersion=")) {
                    version = line.substring("cacheVersion=".length()).trim();
                } else if (line.startsWith("cacheKey=")) {
                    storedKey = line.substring("cacheKey=".length()).trim();
                }
            }
            if (!CACHE_VERSION.equals(version) || !fullKey.equals(storedKey)) {
                return Optional.empty();
            }
            List<JavaClassInfo> classes = new ArrayList<>();
            ClassIndex index = new ClassIndex();
            // 1. classes.tsv をロード
            for (String line : readLines(classesFile)) {
                JavaClassInfo c = JavaClassInfoCodec.decodeHeader(line);
                if (c != null) {
                    classes.add(c);
                }
            }
            // 2. sources.tsv (qn TAB path)
            java.util.Map<String, File> sources = new java.util.HashMap<>();
            if (sourcesFile.isFile()) {
                for (String line : readLines(sourcesFile)) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        sources.put(line.substring(0, tab), new File(line.substring(tab + 1)));
                    }
                }
            }
            // 3. modules.tsv (qn TAB module)
            java.util.Map<String, String> modules = new java.util.HashMap<>();
            if (modulesFile.isFile()) {
                for (String line : readLines(modulesFile)) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        modules.put(line.substring(0, tab), line.substring(tab + 1));
                    }
                }
            }
            // 4. index に登録
            for (JavaClassInfo c : classes) {
                String qn = c.getQualifiedName();
                index.put(c, sources.get(qn), modules.get(qn));
            }
            prog.onProgress(classes.size(), classes.size(), "Loaded from cache");
            return Optional.of(new Snapshot(classes, index, fullKey));
        } catch (IOException ex) {
            // 破損していたら無効化扱い
            return Optional.empty();
        }
    }

    /**
     * 解析結果をディスクに保存する。既存ファイルは上書きする。
     *
     * @param cacheKey {@link CacheKey#compute} の結果。Hit 判定に使われる
     */
    public void save(String cacheKey, List<JavaClassInfo> classes, ClassIndex index)
            throws IOException {
        if (cacheKey == null || cacheKey.isEmpty()) {
            return;
        }
        File dir = directoryFor(cacheKey);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create cache dir: " + dir);
        }
        // manifest
        try (BufferedWriter w = newWriter(new File(dir, "manifest.txt"))) {
            w.write("cacheVersion=" + CACHE_VERSION);
            w.newLine();
            w.write("cacheKey=" + cacheKey);
            w.newLine();
            w.write("classCount=" + (classes != null ? classes.size() : 0));
            w.newLine();
            w.write("createdAt=" + System.currentTimeMillis());
            w.newLine();
        }
        // classes
        try (BufferedWriter w = newWriter(new File(dir, "classes.tsv"))) {
            if (classes != null) {
                for (JavaClassInfo c : classes) {
                    w.write(JavaClassInfoCodec.encodeHeader(c));
                    w.newLine();
                }
            }
        }
        // sources & modules
        if (index != null) {
            try (BufferedWriter ws = newWriter(new File(dir, "sources.tsv"));
                 BufferedWriter wm = newWriter(new File(dir, "modules.tsv"))) {
                for (String qn : index.qualifiedNames()) {
                    File src = index.source(qn).orElse(null);
                    if (src != null) {
                        ws.write(qn);
                        ws.write('\t');
                        ws.write(src.getAbsolutePath());
                        ws.newLine();
                    }
                    String mod = index.module(qn).orElse(null);
                    if (mod != null) {
                        wm.write(qn);
                        wm.write('\t');
                        wm.write(mod);
                        wm.newLine();
                    }
                }
            }
        }
    }

    /** 指定プロジェクトのキャッシュを削除 (再解析を強制したいとき)。 */
    public void invalidate(File projectRoot, AndroidProjectScanner.Options scanOpts) {
        List<File> files = AndroidProjectScanner.scan(projectRoot, scanOpts);
        String key = CacheKey.compute(projectRoot, files);
        File dir = directoryFor(key);
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
            dir.delete();
        }
    }

    File directoryFor(String fullKey) {
        return new File(baseDir, CacheKey.shortId(fullKey));
    }

    private static BufferedWriter newWriter(File f) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8));
    }

    private static List<String> readLines(File f) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
        }
        return out;
    }
}
