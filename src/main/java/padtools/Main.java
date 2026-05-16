package padtools;

import padtools.converter.Converter;
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

        final OptionParser optParser = new OptionParser(new Option[]{
                optHelp, optOut, optScale, optJava, optJavaProject,
                optClassDiagram, optSequenceDiagram, optVerbose});

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
            file_in = new File(optParser.getArguments().getFirst());
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
        if (optClassDiagram.isSet() || optSequenceDiagram.isSet()) {
            handleUmlInput(file_in, file_out,
                    optClassDiagram.isSet(),
                    optSequenceDiagram.isSet()
                            ? optSequenceDiagram.getArguments().getLast() : null,
                    listener);
            return;
        }
        if (optJava.isSet() || optJavaProject.isSet()) {
            handleJavaInput(file_in, file_out, scale, optJavaProject.isSet(), listener);
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
                                         ErrorListener listener) throws IOException {
        String spd;
        if (projectMode) {
            if (fileIn == null) {
                System.err.println("Project mode requires a directory argument.");
                System.exit(1);
                return;
            }
            spd = AndroidProjectScanner.convertProject(fileIn, null, null, listener);
        } else {
            String src;
            if (fileIn == null) {
                src = readAll(System.in);
            } else {
                src = AndroidProjectScanner.readFile(fileIn);
            }
            spd = JavaSourceConverter.convert(src, null, listener);
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
                                        ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("UML generation requires an input file or directory.");
            System.exit(1);
            return;
        }
        String spec = sequenceEntry == null ? "" : sequenceEntry.trim();
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos;
        if (fileIn.isDirectory()) {
            infos = UmlGenerator.extractFromProject(fileIn, null, listener);
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
            output = padtools.core.formats.uml.PlantUmlSequenceDiagram.generate(
                    infos, entryClass, entryMethod, null);
        } else if (classDiagram) {
            output = padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos);
        } else {
            output = padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos);
        }
        writeText(fileOut, output);
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
        System.err.println("  input: SPD by default, or Java/AIDL/dir with -j/-J/-c/-q.");
    }
}
