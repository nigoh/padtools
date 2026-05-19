package padtools.app.cli;

import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.UmlGenerator;
import padtools.core.formats.uml.db.DbBootstrap;
import padtools.core.formats.uml.db.GradleProjectScope;
import padtools.core.formats.uml.db.IncrementalScanner;
import padtools.core.formats.uml.db.IndexDatabase;
import padtools.core.formats.uml.db.IndexWriter;
import padtools.core.formats.uml.db.dao.FilesDao;
import padtools.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code java -jar PadTools.jar index <root>} サブコマンドの実装。
 *
 * <p>事前フルスキャンに加えて、2 回目以降はファイル mtime/size 差分で
 * 変更されたファイルだけを再パースして DB に流し込む (incremental 更新)。</p>
 *
 * <p>呼び出し例:</p>
 * <pre>
 *   java -jar PadTools.jar index /path/to/Car
 *   java -jar PadTools.jar index /path/to/Car --db /tmp/x.db
 * </pre>
 *
 * <p>本コマンドは Java ファイルのみ index する (.aidl / AndroidManifest.xml / .gradle は
 * 後続 PR で対応)。プロジェクトに {@code settings.gradle} がある場合は
 * {@link GradleProjectScope} を使って解析対象を絞り、無い場合はルート以下を全走査する。</p>
 */
public final class IndexCommand {

    private static final String TOOL_VERSION = "padtools-index-cmd";

    /** コマンド結果。stderr 報告用。 */
    public static final class Result {
        public final int filesScanned;
        public final int filesAdded;
        public final int filesModified;
        public final int filesUnchanged;
        public final int filesDeleted;
        public final long elapsedMs;
        public final File dbFile;

        Result(int filesScanned, int filesAdded, int filesModified, int filesUnchanged,
                int filesDeleted, long elapsedMs, File dbFile) {
            this.filesScanned = filesScanned;
            this.filesAdded = filesAdded;
            this.filesModified = filesModified;
            this.filesUnchanged = filesUnchanged;
            this.filesDeleted = filesDeleted;
            this.elapsedMs = elapsedMs;
            this.dbFile = dbFile;
        }
    }

    private IndexCommand() {
    }

    /**
     * {@code [index, <root>, ...]} を受け取り index を実行する。
     *
     * @param args     {@code main(String[])} に渡された配列全体
     * @param listener 警告のリスナー
     * @return exit code (成功 = 0)
     */
    public static int execute(String[] args, ErrorListener listener) throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        File root = null;
        File explicitDb = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--db".equals(a) || "-d".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("error: --db requires a path");
                    return 2;
                }
                explicitDb = new File(args[++i]);
            } else if (a.startsWith("--db=")) {
                explicitDb = new File(a.substring("--db=".length()));
            } else if (root == null) {
                root = new File(a);
            } else {
                System.err.println("error: unexpected argument: " + a);
                return 2;
            }
        }
        if (root == null) {
            System.err.println("usage: index <projectRoot> [--db <path>]");
            return 2;
        }
        if (!root.isDirectory()) {
            System.err.println("error: not a directory: " + root);
            return 2;
        }

        File dbFile = explicitDb != null
                ? explicitDb
                : DbBootstrap.resolveDbFile(root);
        Result result = run(root, dbFile, l);
        System.err.println("[padtools] index complete: db=" + result.dbFile
                + " files=" + result.filesScanned
                + " (+" + result.filesAdded
                + " ~" + result.filesModified
                + " =" + result.filesUnchanged
                + " -" + result.filesDeleted + ")"
                + " elapsed=" + result.elapsedMs + "ms");
        return 0;
    }

    /** プログラム的に呼べる主処理。テスト・組み込み用。 */
    public static Result run(File projectRoot, File dbFile, ErrorListener listener)
            throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        long start = System.currentTimeMillis();

        List<File> targets = listJavaFiles(projectRoot, l);

        ensureParent(dbFile);
        int added = 0;
        int modified = 0;
        int unchanged = 0;
        int deleted = 0;
        try (IndexDatabase db = IndexDatabase.openOrCreate(
                dbFile, projectRoot.getAbsolutePath(), TOOL_VERSION)) {
            IncrementalScanner.DiffResult diff = IncrementalScanner.diff(
                    db.connection(), projectRoot, IndexWriter.KIND_JAVA, targets);
            added = diff.getAdded().size();
            modified = diff.getModified().size();
            unchanged = diff.getUnchanged().size();
            deleted = diff.getDeletedPaths().size();

            IndexWriter writer = new IndexWriter(db.connection());

            // 削除されたファイルを DB から落とす (CASCADE で子行も)
            for (String path : diff.getDeletedPaths()) {
                try {
                    deleteFileRow(db, path);
                } catch (SQLException ex) {
                    l.onError(path, -1, "failed to delete row: " + ex.getMessage());
                }
            }

            // 追加/変更されたファイルを再パース → upsert
            for (File f : diff.getStale()) {
                ingestFile(writer, projectRoot, f, l);
            }
        } catch (SQLException ex) {
            throw new IOException("Index failed: " + ex.getMessage(), ex);
        }
        long elapsed = System.currentTimeMillis() - start;
        return new Result(targets.size(), added, modified, unchanged, deleted, elapsed, dbFile);
    }

    /** {@link GradleProjectScope} 経由で対象パスを決定し、Java ファイルだけ列挙する。 */
    static List<File> listJavaFiles(File projectRoot, ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        GradleProjectScope.Scope scope = GradleProjectScope.resolve(projectRoot, l);

        if (scope.isFallback()) {
            // settings.gradle 無し: ルート全走査 (既存 AndroidProjectScanner を流用)
            AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
            opts.includeAidl = false;
            opts.includeKotlin = false;
            return new ArrayList<>(AndroidProjectScanner.scan(projectRoot, opts));
        }

        // Gradle 推定: モジュールごとの src/*/java ディレクトリだけを走査
        Set<File> out = new LinkedHashSet<>();
        for (GradleProjectScope.ScopePath sp : scope.getPaths()) {
            if (sp.getKind() != GradleProjectScope.ScopePath.Kind.JAVA) {
                continue;
            }
            walkJava(sp.getPath(), out);
        }
        return new ArrayList<>(out);
    }

    private static void walkJava(File dir, Set<File> sink) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                walkJava(f, sink);
            } else if (f.isFile() && f.getName().endsWith(".java")) {
                sink.add(f);
            }
        }
    }

    private static void ingestFile(IndexWriter writer, File projectRoot, File source,
            ErrorListener listener) {
        String relPath = IncrementalScanner.relativize(projectRoot, source);
        long mtime = source.lastModified();
        long size = source.length();
        List<JavaClassInfo> classes = Collections.emptyList();
        String parseError = null;
        try {
            String src = AndroidProjectScanner.readFile(source);
            classes = UmlGenerator.extractFromSource(src, source.getName(), listener);
        } catch (IOException ex) {
            parseError = "read failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            parseError = "parse failed: " + ex.getMessage();
        }
        try {
            writer.upsertFile(relPath, IndexWriter.KIND_JAVA, mtime, size,
                    null, null, classes, parseError);
        } catch (SQLException ex) {
            listener.onError(relPath, -1, "upsert failed: " + ex.getMessage());
        }
    }

    private static void deleteFileRow(IndexDatabase db, String path) throws SQLException {
        FilesDao.delete(db.connection(), path);
    }

    private static void ensureParent(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + parent);
        }
    }
}
