package padtools.app.uml;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.DependencyJarIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.UmlGenerator;
import padtools.util.CancelToken;
import padtools.util.ErrorListener;
import padtools.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 開いているプロジェクトの解析結果 (Android プロジェクト解析 + UML 用クラス情報)
 * をプロジェクトルート単位でキャッシュする。
 *
 * <p>図種を切り替えるたびに再解析するのを避けるため、{@link #load(File, ErrorListener)}
 * は同じプロジェクトルートに対して 1 回だけ解析を実行し、それ以降は
 * メモ化された結果を {@link #getAnalysis()} / {@link #getClasses()} で返す。</p>
 *
 * <p>大規模プロジェクト向けに、進捗・キャンセル・ヘッダのみロード (Stage A) を
 * 受け取る {@link #load(File, ErrorListener, ProgressListener, CancelToken, LoadOptions)}
 * を提供する。{@link ClassIndex} はモジュール紐付けとオンデマンド Stage B 昇格に使う。</p>
 */
public final class ProjectAnalysisCache {

    /** プロジェクトロードオプション。 */
    public static final class LoadOptions {
        /** 取り込みファイル数上限 (負値で無制限)。 */
        public int maxFiles = -1;
        /** Kotlin (.kt) ファイルもスキャンに含める (パースはしない)。 */
        public boolean includeKotlin = false;
        /** AOSP 級プロジェクト向けの追加除外ディレクトリを有効化する。 */
        public boolean useAospDefaults = false;
        /**
         * 詳細パース (フィールド・メソッド・呼び出し列・コメント) を遅延する。
         * true ならヘッダのみ取得し、必要なクラスだけ {@link ClassIndex#detail} で昇格させる。
         */
        public boolean lazyDetails = false;
        /** ディスクキャッシュを利用する (lazyDetails と組み合わせて意味がある)。 */
        public boolean useDiskCache = true;
    }

    private File projectRoot;
    private AndroidProjectAnalysis analysis;
    private List<JavaClassInfo> classes = Collections.emptyList();
    private ClassIndex index = new ClassIndex();
    private DependencyJarIndex dependencyIndex = new DependencyJarIndex();
    private final DiskAnalysisCache disk;

    public ProjectAnalysisCache() {
        this(new DiskAnalysisCache());
    }

    public ProjectAnalysisCache(DiskAnalysisCache disk) {
        this.disk = disk;
    }

    /**
     * プロジェクトを解析してキャッシュする。すでに同じルートで解析済みなら何もしない。
     *
     * @param root     プロジェクトルート (Gradle / Android プロジェクトのトップ)
     * @param listener 解析中の警告を受け取るリスナー。null なら silent。
     */
    public void load(File root, ErrorListener listener) throws IOException {
        load(root, listener, null, null, null);
    }

    /**
     * 進捗・キャンセル・ロードオプション付きの解析。
     *
     * @param progress 進捗リスナー (null なら silent)
     * @param cancel   キャンセルトークン (null なら NONE)
     * @param options  ロードオプション (null ならデフォルト)
     */
    public void load(File root, ErrorListener listener, ProgressListener progress,
                     CancelToken cancel, LoadOptions options) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        if (projectRoot != null && projectRoot.equals(root)) {
            return;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        ProgressListener p = progress != null ? progress : ProgressListener.silent();
        CancelToken c = cancel != null ? cancel : CancelToken.NONE;
        LoadOptions o = options != null ? options : new LoadOptions();

        p.onProgress(0, -1, "Analyzing project...");
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root, l);
        if (c.isCancelled()) {
            return;
        }

        AndroidProjectScanner.Options scanOpts = new AndroidProjectScanner.Options();
        scanOpts.maxFiles = o.maxFiles;
        scanOpts.includeKotlin = o.includeKotlin;
        scanOpts.useAospDefaults = o.useAospDefaults;
        scanOpts.cancelToken = c;
        scanOpts.includeAidl = true;

        // ディスクキャッシュは Stage A 用の永続化のみサポート。
        // lazyDetails=true でかつ Hit したら parse をスキップ。
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                Optional<DiskAnalysisCache.Snapshot> snap = disk.load(root, p);
                if (snap.isPresent()) {
                    this.projectRoot = root;
                    this.analysis = a;
                    this.classes = snap.get().getClasses();
                    this.index = snap.get().getIndex();
                    return;
                }
            } catch (RuntimeException ex) {
                l.onError(null, -1, "disk cache load failed: " + ex.getMessage());
            }
        }

        UmlGenerator.ParseMode mode = o.lazyDetails
                ? UmlGenerator.ParseMode.HEADERS_ONLY
                : UmlGenerator.ParseMode.FULL;
        UmlGenerator.ProjectParseResult result = UmlGenerator.extractFromProjectDetailed(
                root, scanOpts, l, p, c, true, mode);
        if (c.isCancelled()) {
            return;
        }
        this.projectRoot = root;
        this.analysis = a;
        this.classes = result.getClasses() != null ? result.getClasses() : Collections.emptyList();
        this.index = result.getIndex() != null ? result.getIndex() : new ClassIndex();
        this.dependencyIndex = result.getDependencyIndex() != null
                ? result.getDependencyIndex() : new DependencyJarIndex();

        // 解析成功後にディスクキャッシュを更新 (Stage A 情報を永続化)
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                disk.save(root, this.classes, this.index);
            } catch (IOException ex) {
                l.onError(null, -1, "disk cache save failed: " + ex.getMessage());
            }
        }
    }

    /** キャッシュをクリアする (プロジェクトを閉じたとき等)。 */
    public void clear() {
        projectRoot = null;
        analysis = null;
        classes = Collections.emptyList();
        index = new ClassIndex();
        dependencyIndex = new DependencyJarIndex();
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public AndroidProjectAnalysis getAnalysis() {
        return analysis;
    }

    public List<JavaClassInfo> getClasses() {
        return classes;
    }

    public ClassIndex getIndex() {
        return index;
    }

    /** 依存 JAR/AAR の遅延解決インデックス (Gradle 宣言由来)。常に非 null。 */
    public DependencyJarIndex getDependencyIndex() {
        return dependencyIndex;
    }

    /** 完全修飾名 → モジュール名のマップ (Gradle 解析由来)。 */
    public Map<String, String> getClassToModule() {
        return index.moduleMap();
    }

    public boolean isLoaded() {
        return projectRoot != null;
    }
}
