package padtools.editor;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.java.JavaSourceConverter;
import padtools.core.formats.uml.PlantUmlClassDiagram;
import padtools.core.formats.uml.PlantUmlSequenceDiagram;
import padtools.core.formats.uml.UmlGenerator;
import padtools.util.ErrorListener;
import padtools.util.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java ソース・Gradle プロジェクトから SPD テキストを生成するエディタ統合ヘルパ。
 */
public class JavaImporter {

    private final Component parent;

    public JavaImporter(Component parent) {
        this.parent = parent;
    }

    /**
     * Java ファイルを選択ダイアログで開き、SPD 文字列を返す。
     * キャンセル/失敗時は null。
     */
    public String chooseFileAndConvert() {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.importJava.title"));
        fc.setFileFilter(new FileNameExtensionFilter("Java source(*.java)", "java"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = fc.getSelectedFile();
        try {
            String src = AndroidProjectScanner.readFile(file);
            List<String> warnings = new ArrayList<>();
            String result = JavaSourceConverter.convert(src, null,
                    ErrorListener.collecting(warnings));
            showWarnings(warnings);
            return result;
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /**
     * プロジェクトディレクトリを選択し、SPD 文字列を返す。
     * キャンセル/失敗時は null、Java ファイルが見つからなければ null。
     */
    public String chooseProjectAndConvert() {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.importJavaProject.title"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File dir = fc.getSelectedFile();
        try {
            List<String> warnings = new ArrayList<>();
            String spd = AndroidProjectScanner.convertProject(dir, null, null,
                    ErrorListener.collecting(warnings));
            showWarnings(warnings);
            if (spd.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        Messages.get("dialog.importJava.noFiles"),
                        Messages.get("dialog.openFailed"),
                        JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return spd;
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /**
     * Java/AIDL ファイルまたはディレクトリを選択して PlantUML クラス図テキストを返す。
     */
    public String chooseAndGenerateClassDiagram() {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.classDiagram.title"));
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("Java/AIDL(*.java, *.aidl)",
                "java", "aidl"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File f = fc.getSelectedFile();
        try {
            List<String> warnings = new ArrayList<>();
            ErrorListener l = ErrorListener.collecting(warnings);
            String result;
            if (f.isDirectory()) {
                result = PlantUmlClassDiagram.generate(
                        UmlGenerator.extractFromProject(f, null, l));
            } else {
                String src = AndroidProjectScanner.readFile(f);
                result = PlantUmlClassDiagram.generate(
                        UmlGenerator.extractFromSource(src, f.getName(), l));
            }
            showWarnings(warnings);
            return result;
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /**
     * Java ファイルを選択し、指定された Class.method からシーケンス図 PlantUML を生成する。
     * entry が null/空ならダイアログで入力する。
     */
    public String chooseAndGenerateSequenceDiagram(String entry) {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.sequenceDiagram.title"));
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("Java(*.java)", "java"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        String e = entry;
        if (e == null || e.trim().isEmpty()) {
            e = JOptionPane.showInputDialog(parent,
                    Messages.get("dialog.sequenceDiagram.entryPrompt"),
                    "Class.method");
            if (e == null || e.trim().isEmpty()) {
                return null;
            }
        }
        int dot = e.lastIndexOf('.');
        if (dot < 0) {
            showError(Messages.get("dialog.sequenceDiagram.invalidEntry"));
            return null;
        }
        String entryClass = e.substring(0, dot);
        String entryMethod = e.substring(dot + 1);
        File f = fc.getSelectedFile();
        try {
            List<String> warnings = new ArrayList<>();
            ErrorListener l = ErrorListener.collecting(warnings);
            java.util.List<padtools.core.formats.uml.JavaClassInfo> infos;
            if (f.isDirectory()) {
                infos = UmlGenerator.extractFromProject(f, null, l);
            } else {
                String src = AndroidProjectScanner.readFile(f);
                infos = UmlGenerator.extractFromSource(src, f.getName(), l);
            }
            String result = PlantUmlSequenceDiagram.generate(infos, entryClass, entryMethod, null);
            showWarnings(warnings);
            return result;
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /** プロジェクトディレクトリを選択し、Android コンポーネント図 PlantUML を生成。 */
    public String chooseAndGenerateComponentDiagram() {
        return chooseProjectAndGenerate("dialog.componentDiagram.title", true);
    }

    /** プロジェクトディレクトリを選択し、Gradle 依存グラフ PlantUML を生成。 */
    public String chooseAndGenerateDependencyGraph() {
        return chooseProjectAndGenerate("dialog.dependencyGraph.title", false);
    }

    /** プロジェクトディレクトリを選択し、Markdown サマリーを生成。 */
    public String chooseAndGenerateSummary() {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.summary.title"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            List<String> warnings = new ArrayList<>();
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(
                    fc.getSelectedFile(), ErrorListener.collecting(warnings));
            showWarnings(warnings);
            return TextSummaryReport.toMarkdown(analysis);
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /**
     * プロジェクトディレクトリと出力先ディレクトリを選択し、5 種類の成果物を一括書き出す。
     * 戻り値は書き出した先のパス (成功時) もしくは null。
     */
    public java.io.File chooseProjectAndGenerateAll() {
        JFileChooser src = new JFileChooser(".");
        src.setDialogTitle(Messages.get("dialog.allInOne.srcTitle"));
        src.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (src.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        JFileChooser dst = new JFileChooser(".");
        dst.setDialogTitle(Messages.get("dialog.allInOne.dstTitle"));
        dst.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (dst.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        java.io.File srcDir = src.getSelectedFile();
        java.io.File dstDir = dst.getSelectedFile();
        if (!dstDir.exists() && !dstDir.mkdirs()) {
            showError("Cannot create directory: " + dstDir);
            return null;
        }
        List<String> warnings = new ArrayList<>();
        ErrorListener l = ErrorListener.collecting(warnings);
        try {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(srcDir, l);
            writeIfMissing(new java.io.File(dstDir, "summary.md"),
                    padtools.core.formats.android.TextSummaryReport.toMarkdown(analysis));
            writeIfMissing(new java.io.File(dstDir, "component-diagram.puml"),
                    PlantUmlComponentDiagram.generate(analysis));
            writeIfMissing(new java.io.File(dstDir, "dependency-graph.puml"),
                    padtools.core.formats.android.PlantUmlGradleDependencyGraph.generate(analysis));
            java.util.List<padtools.core.formats.uml.JavaClassInfo> infos =
                    UmlGenerator.extractFromProject(srcDir, null, l, true);
            writeIfMissing(new java.io.File(dstDir, "class-diagram.puml"),
                    PlantUmlClassDiagram.generate(infos));
            String spd = AndroidProjectScanner.convertProject(srcDir, null, null, l);
            writeIfMissing(new java.io.File(dstDir, "pad.spd"), spd);
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
        showWarnings(warnings);
        return dstDir;
    }

    private static void writeIfMissing(java.io.File f, String content) throws IOException {
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private String chooseProjectAndGenerate(String titleKey, boolean component) {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get(titleKey));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            List<String> warnings = new ArrayList<>();
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(
                    fc.getSelectedFile(), ErrorListener.collecting(warnings));
            showWarnings(warnings);
            return component
                    ? PlantUmlComponentDiagram.generate(analysis)
                    : PlantUmlGradleDependencyGraph.generate(analysis);
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    private void showError(String detail) {
        JOptionPane.showMessageDialog(parent,
                Messages.get("dialog.importJava.failed") + "\n" + detail,
                Messages.get("dialog.openFailed"),
                JOptionPane.ERROR_MESSAGE);
    }

    /** 警告/エラーがあれば 1 つのダイアログにまとめて表示する。 */
    private void showWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : warnings) {
            sb.append(w).append('\n');
        }
        JTextArea area = new JTextArea(sb.toString(), 12, 60);
        area.setEditable(false);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(560, 240));
        JOptionPane.showMessageDialog(parent, sp,
                Messages.get("dialog.importJava.warnings"),
                JOptionPane.INFORMATION_MESSAGE);
    }
}
