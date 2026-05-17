package padtools;

import padtools.converter.Converter;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.android.AndroidManifestInfo;
import padtools.core.formats.android.AndroidManifestParser;
import padtools.core.formats.android.GradleProjectInfo;
import padtools.core.formats.android.GradleScriptParser;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.java.JavaSourceConverter;
import padtools.core.formats.uml.UmlGenerator;
import padtools.editor.Editor;
import padtools.util.ErrorListener;
import padtools.util.Option;
import padtools.util.OptionParser;
import padtools.util.Messages;
import padtools.util.UnknownOptionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * エントリポイントクラス
 */
public class Main {

    /**
     * Settingを取得する（SettingManager経由）
     */
    public static Setting getSetting() {
        return SettingManager.getInstance().getSetting();
    }

    /**
     * Settingを保存する（SettingManager経由）
     */
    public static void saveSetting() {
        SettingManager.getInstance().save();
    }

    /** 指定パスが存在する/読める File を返す。問題があれば stderr に出して System.exit(1)。 */
    private static File requireReadable(File f) {
        if (f == null) {
            return null;
        }
        if (!f.exists()) {
            System.err.println("Input not found: " + f.getPath());
            System.exit(1);
        }
        if (!f.canRead()) {
            System.err.println("Cannot read: " + f.getPath());
            System.exit(1);
        }
        return f;
    }

    /**
     * エントリポイント
     * @param args 引数
     */
    public static void main(String[] args) throws IOException {
        // SettingManager を初期化
        SettingManager.initialize();

        //オプション定義
        final Option optHelp = new Option("h", "help", false);
        final Option optOut = new Option("o", "output", true);
        final Option optScale = new Option("s", "scale", true);
        final Option optJava = new Option("j", "java", false);
        final Option optJavaProject = new Option("J", "java-project", false);
        final Option optClassDiagram = new Option("c", "class-diagram", false);
        final Option optSequenceDiagram = new Option("q", "sequence-diagram", true);
        final Option optVerbose = new Option("v", "verbose", false);
        final Option optLegend = new Option("L", "legend", false);
        final Option optNoLegend = new Option(null, "no-legend", false);
        final Option optGradle = new Option("g", "gradle", false);
        final Option optManifest = new Option("m", "manifest", false);
        final Option optComponent = new Option("d", "component-diagram", false);
        final Option optDepGraph = new Option("G", "dependency-graph", false);
        final Option optSummary = new Option(null, "summary", false);
        final Option optAll = new Option("A", "all", false);
        final Option optNoManifestMerge = new Option(null, "no-manifest-merge", false);

        final OptionParser optParser = new OptionParser(new Option[]{
                optHelp, optOut, optScale, optJava, optJavaProject,
                optClassDiagram, optSequenceDiagram, optVerbose,
                optLegend, optNoLegend,
                optGradle, optManifest, optComponent, optDepGraph, optSummary,
                optAll, optNoManifestMerge});

        try {
            optParser.parse(args, 1);
        } catch (UnknownOptionException ex) {
            System.err.println("Unknown option: " + ex.getOption());
            System.exit(1);
        }

        if (optHelp.isSet()) {
            printUsage();
            System.exit(1);
        }

        File file_in;
        if (optParser.getArguments().isEmpty()) {
            file_in = null;
        } else {
            file_in = requireReadable(new File(optParser.getArguments().getFirst()));
        }

        File file_out;
        if (optOut.getArguments().isEmpty()) {
            file_out = null;
        } else {
            file_out = new File(optOut.getArguments().getLast());
        }

        Double scale;
        if (optScale.getArguments().isEmpty()) {
            scale = null;
        } else {
            try {
                scale = Double.parseDouble(optScale.getArguments().getLast());
            } catch (NumberFormatException ex) {
                System.err.println(Messages.get("error.invalidScale"));
                System.exit(1);
                return;
            }
        }

        ErrorListener listener = optVerbose.isSet()
                ? ErrorListener.stderr() : ErrorListener.silent();
        // UML はデフォルトで凡例 ON、PAD は OFF。明示的な --legend / --no-legend で上書き可。
        Boolean legendOverride = null;
        if (optLegend.isSet()) {
            legendOverride = Boolean.TRUE;
        } else if (optNoLegend.isSet()) {
            legendOverride = Boolean.FALSE;
        }
        boolean mergeManifest = !optNoManifestMerge.isSet();
        if (optAll.isSet()) {
            handleAll(file_in, file_out, listener, legendOverride, mergeManifest);
            return;
        }
        if (optGradle.isSet()) {
            handleGradleInput(file_in, file_out, listener);
            return;
        }
        if (optManifest.isSet()) {
            handleManifestInput(file_in, file_out, listener);
            return;
        }
        if (optComponent.isSet()) {
            handleComponentDiagram(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optDepGraph.isSet()) {
            handleDependencyGraph(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optSummary.isSet()) {
            handleSummary(file_in, file_out, listener);
            return;
        }
        if (optClassDiagram.isSet() || optSequenceDiagram.isSet()) {
            handleUmlInput(file_in, file_out,
                    optClassDiagram.isSet(),
                    optSequenceDiagram.isSet()
                            ? optSequenceDiagram.getArguments().getLast() : null,
                    listener, legendOverride, mergeManifest);
            return;
        }
        if (optJava.isSet() || optJavaProject.isSet()) {
            handleJavaInput(file_in, file_out, scale, optJavaProject.isSet(),
                    listener, legendOverride);
            return;
        }

        if (file_out == null) {
            Editor.openEditor(file_in);
        } else {
            Converter.convert(file_in, file_out, scale);
        }
    }

    /**
     * Java ソースを入力とした PAD 生成処理。
     * @param fileIn 入力ファイル (.java) またはプロジェクトディレクトリ。null の場合は標準入力
     * @param fileOut 出力先 (.spd または .png/.svg/.pdf)。null なら標準出力に SPD を書く
     * @param scale 画像スケール
     * @param projectMode true ならプロジェクトディレクトリ走査モード
     * @param listener エラーリスナー (verbose 時に stderr へ出す)
     */
    private static void handleJavaInput(File fileIn, File fileOut, Double scale,
                                         boolean projectMode,
                                         ErrorListener listener,
                                         Boolean legendOverride) throws IOException {
        JavaSourceConverter.Options convOpts = new JavaSourceConverter.Options();
        if (Boolean.TRUE.equals(legendOverride)) {
            convOpts.includeLegend = true;
        }
        String spd;
        if (projectMode) {
            if (fileIn == null) {
                System.err.println("Project mode requires a directory argument.");
                System.exit(1);
                return;
            }
            spd = AndroidProjectScanner.convertProject(fileIn, null, convOpts, listener);
        } else {
            String src;
            if (fileIn == null) {
                src = readAll(System.in);
            } else {
                src = AndroidProjectScanner.readFile(fileIn);
            }
            spd = JavaSourceConverter.convert(src, convOpts, listener);
        }

        String outName = fileOut != null ? fileOut.getName().toLowerCase() : "";
        if (fileOut == null || outName.endsWith(".spd") || outName.endsWith(".txt")) {
            writeText(fileOut, spd);
            return;
        }

        // 画像出力の場合は SPD をいったん一時ファイルへ書き、Converter で変換する
        File tmp = File.createTempFile("padtools-java-", ".spd");
        try {
            writeText(tmp, spd);
            Converter.convert(tmp, fileOut, scale);
        } finally {
            if (!tmp.delete()) {
                tmp.deleteOnExit();
            }
        }
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void writeText(File f, String content) throws IOException {
        if (f == null) {
            // System.out の既定エンコーディングに依存しないよう UTF-8 で明示出力
            System.out.write(content.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /**
     * Java/AIDL ソースから PlantUML (クラス図 / シーケンス図) を生成。
     * @param fileIn 入力ファイルまたはディレクトリ
     * @param fileOut 出力ファイル (.puml/.plantuml/.txt)。null なら標準出力
     * @param classDiagram true でクラス図モード
     * @param sequenceEntry "Class.method" 形式のエントリ。null/空ならシーケンス図モードを無効化
     */
    private static void handleUmlInput(File fileIn, File fileOut,
                                        boolean classDiagram,
                                        String sequenceEntry,
                                        ErrorListener listener,
                                        Boolean legendOverride,
                                        boolean mergeManifest) throws IOException {
        if (fileIn == null) {
            System.err.println("UML generation requires an input file or directory.");
            System.exit(1);
            return;
        }
        String spec = sequenceEntry == null ? "" : sequenceEntry.trim();
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos;
        if (fileIn.isDirectory()) {
            infos = UmlGenerator.extractFromProject(fileIn, null, listener, mergeManifest);
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
        }

        String output;
        if (sequenceEntry != null && !spec.isEmpty()) {
            int dot = spec.lastIndexOf('.');
            if (dot < 0) {
                System.err.println("Sequence entry must be in 'Class.method' format: " + spec);
                System.exit(1);
                return;
            }
            String entryClass = spec.substring(0, dot);
            String entryMethod = spec.substring(dot + 1);
            padtools.core.formats.uml.PlantUmlSequenceDiagram.Options sqOpts
                    = new padtools.core.formats.uml.PlantUmlSequenceDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                sqOpts.includeLegend = false;
            }
            output = padtools.core.formats.uml.PlantUmlSequenceDiagram.generate(
                    infos, entryClass, entryMethod, sqOpts);
        } else {
            padtools.core.formats.uml.PlantUmlClassDiagram.Options clOpts
                    = new padtools.core.formats.uml.PlantUmlClassDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                clOpts.includeLegend = false;
            }
            output = padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos, clOpts);
        }
        writeText(fileOut, output);
    }

    /** {@code --gradle}: 単一 build.gradle (もしくはディレクトリ) を Markdown サマリーに変換。 */
    private static void handleGradleInput(File fileIn, File fileOut,
                                            ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("Gradle mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            GradleProjectInfo info = GradleScriptParser.parse(content, fileIn.getName(), listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            fake.getGradleByModule().put(fileIn.getName(), info);
            writeText(fileOut, TextSummaryReport.toMarkdown(fake));
        }
    }

    /** {@code --manifest}: 単一 AndroidManifest.xml (もしくはディレクトリ) を Markdown サマリーに変換。 */
    private static void handleManifestInput(File fileIn, File fileOut,
                                              ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("Manifest mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            fake.getManifestsByModule().put(fileIn.getName(), list);
            writeText(fileOut, TextSummaryReport.toMarkdown(fake));
        }
    }

    /** {@code --component-diagram}: コンポーネント図 PlantUML を生成。 */
    private static void handleComponentDiagram(File fileIn, File fileOut,
                                                 ErrorListener listener,
                                                 Boolean legendOverride) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Component diagram requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeText(fileOut, PlantUmlComponentDiagram.generate(analysis, o));
    }

    /** {@code --dependency-graph}: Gradle 依存グラフ PlantUML を生成。 */
    private static void handleDependencyGraph(File fileIn, File fileOut,
                                                ErrorListener listener,
                                                Boolean legendOverride) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Dependency graph requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeText(fileOut, PlantUmlGradleDependencyGraph.generate(analysis, o));
    }

    /** {@code --summary}: プロジェクト全体の Markdown サマリーを生成。 */
    private static void handleSummary(File fileIn, File fileOut,
                                        ErrorListener listener) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Summary requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
    }

    /**
     * {@code --all}: プロジェクトディレクトリを入力に、5 種類の成果物を出力ディレクトリへ一括書き出し。
     * <ul>
     *   <li>{@code summary.md} - Markdown プロジェクトサマリー</li>
     *   <li>{@code class-diagram.puml} - PlantUML クラス図 (manifest 自動マージ)</li>
     *   <li>{@code component-diagram.puml} - PlantUML Android コンポーネント図</li>
     *   <li>{@code dependency-graph.puml} - PlantUML Gradle 依存グラフ</li>
     *   <li>{@code pad.spd} - Java→SPD (PAD) 統合出力</li>
     * </ul>
     * <p>シーケンス図は起点メソッド指定が必要なため {@code --all} には含めない (個別に {@code -q} を使う)。</p>
     */
    private static void handleAll(File fileIn, File fileOut,
                                    ErrorListener listener,
                                    Boolean legendOverride,
                                    boolean mergeManifest) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--all requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--all requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory when --all is set: " + fileOut);
            System.exit(1);
            return;
        }

        // プロジェクト解析を 1 回だけ実行して再利用する
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);

        // 1) Markdown サマリー
        File summaryFile = new File(fileOut, "summary.md");
        writeText(summaryFile, TextSummaryReport.toMarkdown(analysis));
        listener.onError(null, -1, "wrote " + summaryFile.getPath());

        // 2) コンポーネント図
        PlantUmlComponentDiagram.Options compOpts = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            compOpts.includeLegend = false;
        }
        File compFile = new File(fileOut, "component-diagram.puml");
        writeText(compFile, PlantUmlComponentDiagram.generate(analysis, compOpts));
        listener.onError(null, -1, "wrote " + compFile.getPath());

        // 3) 依存グラフ
        PlantUmlGradleDependencyGraph.Options depOpts = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            depOpts.includeLegend = false;
        }
        File depFile = new File(fileOut, "dependency-graph.puml");
        writeText(depFile, PlantUmlGradleDependencyGraph.generate(analysis, depOpts));
        listener.onError(null, -1, "wrote " + depFile.getPath());

        // 4) クラス図 (UmlGenerator は内部で再走査するが、manifest 連携のため別経路)
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, null, listener, mergeManifest);
        padtools.core.formats.uml.PlantUmlClassDiagram.Options clsOpts =
                new padtools.core.formats.uml.PlantUmlClassDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            clsOpts.includeLegend = false;
        }
        File clsFile = new File(fileOut, "class-diagram.puml");
        writeText(clsFile, padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos, clsOpts));
        listener.onError(null, -1, "wrote " + clsFile.getPath());

        // 5) Java→PAD
        JavaSourceConverter.Options convOpts = new JavaSourceConverter.Options();
        if (Boolean.TRUE.equals(legendOverride)) {
            convOpts.includeLegend = true;
        }
        String spd = AndroidProjectScanner.convertProject(fileIn, null, convOpts, listener);
        File spdFile = new File(fileOut, "pad.spd");
        writeText(spdFile, spd);
        listener.onError(null, -1, "wrote " + spdFile.getPath());
    }

    private static void printUsage() {
        System.err.println("Arguments: [-o file] [-s scale] [-j|-J|-c|-q M] [-v] [-h] [input]");
        System.err.println("  -o file: Save to file (spd/png/svg/pdf/puml).");
        System.err.println("  -s scale: Image scale (available when result is image).");
        System.err.println("  -h: Show this help.");
        System.err.println("  -j --java: Treat input as a Java source file.");
        System.err.println("  -J --java-project: Gradle/Android project directory.");
        System.err.println("  -c --class-diagram: Output PlantUML class diagram.");
        System.err.println("  -q --sequence-diagram Class.method: PlantUML sequence diagram.");
        System.err.println("  -v --verbose: Emit per-file warnings and summary to stderr.");
        System.err.println("  -L --legend: Force include legend (PAD: opt-in).");
        System.err.println("  --no-legend: Force exclude legend (UML: opt-out).");
        System.err.println("  -g --gradle: Output Markdown summary from build.gradle.");
        System.err.println("  -m --manifest: Output Markdown summary from AndroidManifest.xml.");
        System.err.println("  -d --component-diagram: PlantUML Android component diagram.");
        System.err.println("  -G --dependency-graph: PlantUML Gradle dependency graph.");
        System.err.println("  --summary: Full project Markdown summary (dir).");
        System.err.println("  -A --all: Output ALL artifacts (summary/component/deps/class/pad) "
                + "to the directory specified by -o.");
        System.err.println("  --no-manifest-merge: Disable manifest auto-merge in class diagram.");
        System.err.println("  input: SPD by default, or Java/AIDL/dir with -j/-J/-c/-q/-g/-m/-d/-G/--summary/-A.");
    }
}
