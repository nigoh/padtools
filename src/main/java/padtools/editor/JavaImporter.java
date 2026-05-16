package padtools.editor;

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
