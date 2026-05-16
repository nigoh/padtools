package padtools.core.formats.uml;

import padtools.core.formats.java.AndroidProjectScanner;

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
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        List<JavaClassInfo> infos;
        if (fileName != null && fileName.toLowerCase().endsWith(".aidl")) {
            infos = new ArrayList<>(AidlParser.parse(source));
        } else {
            infos = new ArrayList<>(JavaStructureExtractor.extract(source));
        }
        for (JavaClassInfo info : infos) {
            String cat = AaosPattern.categorize(info);
            if (cat != null) {
                info.setAaosCategory(cat);
            }
        }
        return infos;
    }

    /** Gradle プロジェクト全体を走査して全 ClassInfo を集める。 */
    public static List<JavaClassInfo> extractFromProject(File root) throws IOException {
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        return extractFromProject(root, opts);
    }

    /** オプション付きプロジェクトスキャン。 */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts)
            throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        AndroidProjectScanner.Options scanOpts = (opts != null) ? opts
                : new AndroidProjectScanner.Options();
        // AIDL もスキャン対象に
        scanOpts.includeAidl = true;
        List<File> files = AndroidProjectScanner.scan(root, scanOpts);
        List<JavaClassInfo> all = new ArrayList<>();
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".aidl")) {
                continue;
            }
            String src = AndroidProjectScanner.readFile(f);
            all.addAll(extractFromSource(src, f.getName()));
        }
        return all;
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
