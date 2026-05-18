package padtools.app.uml;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.UmlGenerator;
import padtools.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 開いているプロジェクトの解析結果 (Android プロジェクト解析 + UML 用クラス情報)
 * をプロジェクトルート単位でキャッシュする。
 *
 * <p>図種を切り替えるたびに再解析するのを避けるため、{@link #load(File, ErrorListener)}
 * は同じプロジェクトルートに対して 1 回だけ解析を実行し、それ以降は
 * メモ化された結果を {@link #getAnalysis()} / {@link #getClasses()} で返す。</p>
 */
public final class ProjectAnalysisCache {

    private File projectRoot;
    private AndroidProjectAnalysis analysis;
    private List<JavaClassInfo> classes = Collections.emptyList();

    /**
     * プロジェクトを解析してキャッシュする。すでに同じルートで解析済みなら何もしない。
     *
     * @param root     プロジェクトルート (Gradle / Android プロジェクトのトップ)
     * @param listener 解析中の警告を受け取るリスナー。null なら silent。
     */
    public void load(File root, ErrorListener listener) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        if (projectRoot != null && projectRoot.equals(root)) {
            return;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root, l);
        List<JavaClassInfo> cs = UmlGenerator.extractFromProject(root, null, l, true);
        this.projectRoot = root;
        this.analysis = a;
        this.classes = cs != null ? cs : Collections.emptyList();
    }

    /** キャッシュをクリアする (プロジェクトを閉じたとき等)。 */
    public void clear() {
        projectRoot = null;
        analysis = null;
        classes = Collections.emptyList();
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

    public boolean isLoaded() {
        return projectRoot != null;
    }
}
