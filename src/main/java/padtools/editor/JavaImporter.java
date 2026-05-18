package padtools.editor;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.java.JavaSourceConverter;
import padtools.core.formats.uml.LifecycleSequenceDiagrams;
import padtools.core.formats.uml.PlantUmlClassDiagram;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.core.formats.uml.PlantUmlSequenceDiagram;
import padtools.core.formats.uml.UmlGenerator;
import padtools.util.ErrorListener;
import padtools.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * entry が null/空なら、入力ソースを解析して候補リストから選ばせる
     * (前段の "Class.method を手入力" ダイアログから置き換え)。
     */
    public String chooseAndGenerateSequenceDiagram(String entry) {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(Messages.get("dialog.sequenceDiagram.title"));
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("Java(*.java)", "java"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
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
            String chosen = entry;
            if (chosen == null || chosen.trim().isEmpty()) {
                java.util.List<PlantUmlSequenceDiagram.Candidate> candidates =
                        PlantUmlSequenceDiagram.listCandidates(infos);
                if (candidates.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            Messages.get("dialog.sequenceDiagram.noMethods"),
                            Messages.get("dialog.openFailed"),
                            JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                chosen = pickSequenceEntry(candidates);
                if (chosen == null) {
                    return null;
                }
            }
            int dot = chosen.lastIndexOf('.');
            if (dot < 0) {
                showError(Messages.get("dialog.sequenceDiagram.invalidEntry"));
                return null;
            }
            String entryClass = chosen.substring(0, dot);
            String entryMethod = chosen.substring(dot + 1);
            String result = PlantUmlSequenceDiagram.generate(infos, entryClass, entryMethod, null);
            showWarnings(warnings);
            return result;
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    /**
     * 候補リストから 1 件を選ばせるダイアログ。テキストフィールドで絞り込み可能。
     * OK / キャンセル / Enter / Esc で操作。
     * @return 選んだ {@code Class.method}、キャンセル時は null
     */
    private String pickSequenceEntry(List<PlantUmlSequenceDiagram.Candidate> candidates) {
        DefaultListModel<String> model = new DefaultListModel<>();
        List<String> all = new ArrayList<>();
        for (PlantUmlSequenceDiagram.Candidate c : candidates) {
            String row = c.getEntry()
                    + "    [" + c.callCount + " call" + (c.callCount == 1 ? "" : "s")
                    + ", " + c.visibility.name().toLowerCase(Locale.ROOT) + "]";
            all.add(row);
            model.addElement(row);
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(15);

        JTextField filter = new JTextField();
        filter.setToolTipText(Messages.get("dialog.sequenceDiagram.filterTip"));
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter(); }
            @Override public void removeUpdate(DocumentEvent e) { refilter(); }
            @Override public void changedUpdate(DocumentEvent e) { refilter(); }
            private void refilter() {
                String q = filter.getText().trim().toLowerCase(Locale.ROOT);
                model.clear();
                for (String row : all) {
                    if (q.isEmpty() || row.toLowerCase(Locale.ROOT).contains(q)) {
                        model.addElement(row);
                    }
                }
                if (!model.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }
        });
        // Enter で確定、Esc でキャンセル: JOptionPane 側がハンドルするので
        // 個別キーバインドは下方向キーをフィルタ→リストへ送るのみ
        filter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && model.getSize() > 0) {
                    int i = Math.min(list.getSelectedIndex() + 1, model.getSize() - 1);
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP && model.getSize() > 0) {
                    int i = Math.max(list.getSelectedIndex() - 1, 0);
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                    e.consume();
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel header = new JLabel(Messages.get("dialog.sequenceDiagram.pickPrompt"));
        panel.add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(filter, BorderLayout.NORTH);
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(560, 420));

        int ret = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("dialog.sequenceDiagram.pickTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ret != JOptionPane.OK_OPTION) {
            return null;
        }
        String selected = list.getSelectedValue();
        if (selected == null) {
            return null;
        }
        // "Class.method    [N calls, public]" から先頭の Class.method 部分のみ取り出す
        int sp = selected.indexOf(' ');
        return sp >= 0 ? selected.substring(0, sp) : selected;
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

            // シーケンス図の起点候補一覧
            java.util.List<PlantUmlSequenceDiagram.Candidate> candidates =
                    PlantUmlSequenceDiagram.listCandidates(infos);
            StringBuilder methodsBuf = new StringBuilder();
            for (PlantUmlSequenceDiagram.Candidate c : candidates) {
                methodsBuf.append(c.getEntry())
                        .append("\t(").append(c.callCount).append(" call")
                        .append(c.callCount == 1 ? "" : "s")
                        .append(", ").append(c.visibility.name().toLowerCase(Locale.ROOT)).append(")\n");
            }
            writeIfMissing(new java.io.File(dstDir, "methods.txt"), methodsBuf.toString());

            // Android ライフサイクルメソッド起点のシーケンス図 (.puml + .svg を併出力)
            java.io.File seqDir = new java.io.File(dstDir, "sequence-diagrams");
            if (seqDir.exists() || seqDir.mkdirs()) {
                for (LifecycleSequenceDiagrams.Entry e :
                        LifecycleSequenceDiagrams.generateAll(infos, null)) {
                    writeIfMissing(new java.io.File(seqDir, e.baseName() + ".puml"), e.puml);
                    PlantUmlRenderer.renderSvg(e.puml,
                            new java.io.File(seqDir, e.baseName() + ".svg"));
                }
            }
        } catch (IOException ex) {
            showError(ex.getMessage());
            return null;
        }
        showWarnings(warnings);
        return dstDir;
    }

    /**
     * Android プロジェクトディレクトリと出力先ディレクトリを選択し、
     * Activity/Service/Receiver/Provider のライフサイクルメソッドを起点とする
     * PlantUML シーケンス図を {@code Class.method.puml} と {@code Class.method.svg} の
     * 両方で一括出力する。戻り値は書き出した先のパス (成功時) もしくは null。
     */
    public java.io.File chooseProjectAndGenerateSequenceDiagrams() {
        JFileChooser src = new JFileChooser(".");
        src.setDialogTitle(Messages.get("dialog.sequenceDiagramAll.srcTitle"));
        src.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (src.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        JFileChooser dst = new JFileChooser(".");
        dst.setDialogTitle(Messages.get("dialog.sequenceDiagramAll.dstTitle"));
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
            java.util.List<padtools.core.formats.uml.JavaClassInfo> infos =
                    UmlGenerator.extractFromProject(srcDir, null, l, true);
            java.util.List<LifecycleSequenceDiagrams.Entry> entries =
                    LifecycleSequenceDiagrams.generateAll(infos, null);
            if (entries.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        Messages.get("dialog.sequenceDiagramAll.noEntries"),
                        Messages.get("menu.file.sequenceDiagramAll"),
                        JOptionPane.WARNING_MESSAGE);
                return null;
            }
            for (LifecycleSequenceDiagrams.Entry e : entries) {
                writeIfMissing(new java.io.File(dstDir, e.baseName() + ".puml"), e.puml);
                PlantUmlRenderer.renderSvg(e.puml,
                        new java.io.File(dstDir, e.baseName() + ".svg"));
            }
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
