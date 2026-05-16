package padtools.editor;

import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.java.JavaSourceConverter;
import padtools.util.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;

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
            return JavaSourceConverter.convert(src);
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
            String spd = AndroidProjectScanner.convertProject(dir, null, null);
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

    private void showError(String detail) {
        JOptionPane.showMessageDialog(parent,
                Messages.get("dialog.importJava.failed") + "\n" + detail,
                Messages.get("dialog.openFailed"),
                JOptionPane.ERROR_MESSAGE);
    }
}
