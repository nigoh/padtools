package padtools.editor;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * SPDファイルの読み込み・保存を管理するクラス。
 */
public class FileManager {
    private File currentFile = null;
    private final Component parent;

    public FileManager(Component parent) {
        this.parent = parent;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    /**
     * ファイルを読み込み、内容を文字列として返す。
     */
    public String openFile(File file) {
        currentFile = file;
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringWriter sw = new StringWriter();

            String buf;
            while ((buf = br.readLine()) != null) {
                sw.append(buf);
                sw.append("\n");
            }

            return sw.toString();
        } catch (IOException ex) {
            JOptionPane.showConfirmDialog(
                    parent,
                    ex.getLocalizedMessage(),
                    "読み込み失敗",
                    JOptionPane.OK_OPTION,
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * ファイル選択ダイアログを表示し、選択されたファイルを読み込む。
     */
    public String openWithDialog() {
        JFileChooser fc = new JFileChooser(currentFile == null ? new File(".") : currentFile);
        fc.setFileFilter(new FileNameExtensionFilter("Simple Pad Description(*.spd)", "spd"));

        if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return openFile(fc.getSelectedFile());
        }
        return null;
    }

    /**
     * ファイルに保存する。
     */
    public boolean saveFile(File file, String content) {
        currentFile = file;
        try (PrintWriter ps = new PrintWriter(file, "UTF-8")) {
            ps.print(content);
            return true;
        } catch (IOException ex) {
            JOptionPane.showConfirmDialog(
                    parent,
                    ex.getLocalizedMessage(),
                    "保存失敗",
                    JOptionPane.OK_OPTION,
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 現在のファイルに保存する。ファイルが未設定の場合はsaveAsDialogを呼ぶ。
     */
    public boolean save(String content) {
        return currentFile == null ? saveWithDialog(content) : saveFile(currentFile, content);
    }

    /**
     * 名前を付けて保存ダイアログを表示する。
     */
    public boolean saveWithDialog(String content) {
        JFileChooser fc = new JFileChooser(currentFile == null ? new File(".") : currentFile);
        fc.setFileFilter(new FileNameExtensionFilter("Simple Pad Description(*.spd)", "spd"));
        fc.setSelectedFile(new File("new_document.spd"));
        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return saveFile(fc.getSelectedFile(), content);
        } else {
            return false;
        }
    }
}
