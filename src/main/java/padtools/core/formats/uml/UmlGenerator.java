package padtools.core.formats.uml;

import padtools.core.formats.android.AndroidComponentInfo;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java/AIDL ソースまたはディレクトリから PlantUML クラス図 / シーケンス図を生成する高レベル API。
 *
 * <p>AAOS 固有のパターン認識・AIDL のパースを行い、複数ファイルから一貫したダイアグラムを生成する。</p>
 */
public final class UmlGenerator {

    private UmlGenerator() {
    }

    /** 入力ファイル (.java または .aidl) 1 つから ClassInfo リストを抽出する。 */
    public static List<JavaClassInfo> extractFromSource(String source, String fileName) {
        return extractFromSource(source, fileName, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extractFromSource(String source, String fileName,
                                                         ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        ErrorListener wrapped = (listener == null) ? ErrorListener.silent()
                : wrapWithSource(listener, fileName);
        List<JavaClassInfo> infos;
        if (fileName != null && fileName.toLowerCase().endsWith(".aidl")) {
            infos = new ArrayList<>(AidlParser.parse(source, wrapped));
        } else {
            infos = new ArrayList<>(JavaStructureExtractor.extract(source, wrapped));
        }
        for (JavaClassInfo info : infos) {
            String cat = AaosPattern.categorize(info);
            if (cat != null) {
                info.setAaosCategory(cat);
            }
        }
        return infos;
    }

    private static ErrorListener wrapWithSource(ErrorListener inner, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return inner;
        }
        return (source, line, message) ->
                inner.onError(source != null && !source.isEmpty() ? source : fileName,
                        line, message);
    }

    /** Gradle プロジェクト全体を走査して全 ClassInfo を集める。 */
    public static List<JavaClassInfo> extractFromProject(File root) throws IOException {
        return extractFromProject(root, null, null);
    }

    /** オプション付きプロジェクトスキャン。 */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts)
            throws IOException {
        return extractFromProject(root, opts, null);
    }

    /** オプション + エラーリスナー付き。個別ファイルの読込失敗は listener に通知して継続する。 */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts,
                                                          ErrorListener listener)
            throws IOException {
        return extractFromProject(root, opts, listener, true);
    }

    /**
     * manifest 自動マージ制御つきプロジェクト走査。
     * @param mergeManifest true なら同プロジェクト直下の AndroidManifest.xml を自動検出し、
     *                       対応する JavaClassInfo に {@code androidComponentType} を反映
     */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts,
                                                          ErrorListener listener,
                                                          boolean mergeManifest)
            throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        AndroidProjectScanner.Options scanOpts = (opts != null) ? opts
                : new AndroidProjectScanner.Options();
        scanOpts.includeAidl = true;
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<File> files = AndroidProjectScanner.scan(root, scanOpts);
        List<JavaClassInfo> all = new ArrayList<>();
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".aidl")) {
                continue;
            }
            String src;
            try {
                src = AndroidProjectScanner.readFile(f);
            } catch (IOException ex) {
                l.onError(f.getName(), -1, "read failed: " + ex.getMessage());
                continue;
            }
            try {
                all.addAll(extractFromSource(src, f.getName(), l));
            } catch (RuntimeException ex) {
                l.onError(f.getName(), -1, "parse failed: " + ex.getMessage());
            }
        }
        if (mergeManifest && root.isDirectory()) {
            mergeManifestInto(all, root, l);
        }
        return all;
    }

    /**
     * 指定ディレクトリ配下の AndroidManifest.xml を解析し、対応するクラスに
     * {@code androidComponentType} を反映する。
     */
    public static void mergeManifestInto(List<JavaClassInfo> classes, File root,
                                          ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        try {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(root, l);
            for (AndroidComponentInfo c : analysis.allComponents()) {
                String fqn = c.getName();
                if (fqn == null || fqn.isEmpty()) {
                    continue;
                }
                for (JavaClassInfo cls : classes) {
                    if (matches(cls, fqn)) {
                        cls.setAndroidComponentType(c.getKind().label());
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            l.onError(null, -1, "manifest merge failed: " + ex.getMessage());
        }
    }

    private static boolean matches(JavaClassInfo cls, String fqn) {
        if (cls.getQualifiedName().equals(fqn)) {
            return true;
        }
        // simple name match (manifest 側で package が省略されている場合の救済)
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            String simple = fqn.substring(dot + 1);
            return cls.getSimpleName().equals(simple);
        }
        return cls.getSimpleName().equals(fqn);
    }

    /** クラス図 PlantUML テキストを Java ソース文字列 1 つから生成。 */
    public static String classDiagram(String source) {
        return PlantUmlClassDiagram.generate(extractFromSource(source, null));
    }

    /** クラス図 PlantUML テキストをプロジェクトから生成。 */
    public static String classDiagramFromProject(File root) throws IOException {
        return PlantUmlClassDiagram.generate(extractFromProject(root));
    }

    /** Java ソース内の特定メソッドからシーケンス図を生成。 */
    public static String sequenceDiagram(String source, String entryClass, String entryMethod) {
        return PlantUmlSequenceDiagram.generate(
                extractFromSource(source, null), entryClass, entryMethod, null);
    }

    /** プロジェクト内の特定メソッドからシーケンス図を生成。 */
    public static String sequenceDiagramFromProject(File root,
                                                     String entryClass,
                                                     String entryMethod) throws IOException {
        return PlantUmlSequenceDiagram.generate(
                extractFromProject(root), entryClass, entryMethod, null);
    }
}
