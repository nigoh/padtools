package padtools.core.formats.uml.db;

import padtools.util.CacheKey;

import java.io.File;

/**
 * SQLite ベースの解析インデックス DB の配置先を決定するヘルパー。
 *
 * <p>配置: {@code ~/.padtools/cache/<shortHash>/index.db}</p>
 *
 * <p>{@code shortHash} は <b>プロジェクトルートの canonical path のみ</b>から
 * 計算される ({@link CacheKey#computeRootOnly(File)})。
 * 既存の {@code CacheKey.compute(root, files)} はファイル差分でハッシュが変わる
 * 仕様だったが、永続インデックスでは「1 ファイル変えるたびに別ディレクトリ」に
 * なってしまい増分更新が成り立たないため、ここではルートだけからキーを作る。
 * ファイル差分は DB 内の {@code files.mtime/size} で検知する。</p>
 */
public final class DbBootstrap {

    public static final String DB_FILENAME = "index.db";

    private DbBootstrap() {
    }

    /** {@code ~/.padtools/cache} 等、OS に応じたキャッシュベースディレクトリ。 */
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

    /** 指定 root に対するキャッシュサブディレクトリ ({@code <base>/<shortHash>}) を返す。 */
    public static File resolveCacheDir(File baseDir, File projectRoot) {
        String key = CacheKey.computeRootOnly(projectRoot);
        return new File(baseDir, CacheKey.shortId(key));
    }

    /** {@code ~/.padtools/cache/<shortHash>} を返す (デフォルト base 配下)。 */
    public static File resolveCacheDir(File projectRoot) {
        return resolveCacheDir(defaultBaseDir(), projectRoot);
    }

    /** {@code <cacheDir>/index.db} のフルパスを返す。 */
    public static File resolveDbFile(File baseDir, File projectRoot) {
        return new File(resolveCacheDir(baseDir, projectRoot), DB_FILENAME);
    }

    /** デフォルト base での {@code <cacheDir>/index.db} を返す。 */
    public static File resolveDbFile(File projectRoot) {
        return new File(resolveCacheDir(projectRoot), DB_FILENAME);
    }
}
