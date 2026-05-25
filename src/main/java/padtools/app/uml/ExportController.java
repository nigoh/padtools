package padtools.app.uml;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 図のエクスポート (SVG/PNG/PlantUML 保存・SVG クリップボードコピー・保存ダイアログ) を担う補助クラス。
 * 親フレーム・図の状態 (現在の PlantUML/SVG) ・ステータス表示先のみに依存する。
 */
final class ExportController {

    private final Frame parent;
    private final DiagramState state;
    private final JLabel status;

    ExportController(Frame parent, DiagramState state, JLabel status) {
        this.parent = parent;
        this.state = state;
        this.status = status;
    }

    /** 右クリックエクスポートポップアップを構築する (SVG / PNG / PUML 保存 + SVG コピー)。 */
    public JPopupMenu buildExportPopup() {
        JPopupMenu popup = new JPopupMenu("Export");
        JMenuItem saveSvg = new JMenuItem("Save as SVG...");
        saveSvg.addActionListener(e -> exportAs(UmlExporter.Format.SVG));
        popup.add(saveSvg);
        JMenuItem savePng = new JMenuItem("Save as PNG...");
        savePng.addActionListener(e -> exportAs(UmlExporter.Format.PNG));
        popup.add(savePng);
        JMenuItem savePuml = new JMenuItem("Save as PlantUML...");
        savePuml.addActionListener(e -> exportAs(UmlExporter.Format.PUML));
        popup.add(savePuml);
        popup.addSeparator();
        JMenuItem copySvg = new JMenuItem("Copy SVG to Clipboard");
        copySvg.addActionListener(e -> copySvgToClipboard());
        popup.add(copySvg);
        return popup;
    }

    /** 指定フォーマットで保存ダイアログを開きエクスポートする。 */
    private void exportAs(UmlExporter.Format fmt) {
        if (state.currentPuml == null || state.currentPuml.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No diagram to export yet.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String ext;
        String filterDesc;
        switch (fmt) {
            case SVG:  ext = "svg"; filterDesc = "SVG (*.svg)"; break;
            case PNG:  ext = "png"; filterDesc = "PNG (*.png)"; break;
            default:   ext = "puml"; filterDesc = "PlantUML source (*.puml)"; break;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save diagram as " + ext.toUpperCase());
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter(filterDesc, ext));
        int r = fc.showSaveDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)) {
            chosen = new File(chosen.getAbsolutePath() + "." + ext);
        }
        try {
            BufferedImage pngImage = null;
            if (fmt == UmlExporter.Format.PNG) {
                pngImage = PlantUmlImageRenderer.toBufferedImage(state.currentPuml);
            }
            UmlExporter.export(fmt, chosen, state.currentPuml, pngImage);
            status.setText("Saved: " + chosen.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 現在の SVG XML 全体をクリップボードへコピーする。 */
    private void copySvgToClipboard() {
        if (state.currentSvgXml == null || state.currentSvgXml.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No SVG to copy.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(state.currentSvgXml);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(sel, null);
            status.setText("SVG copied to clipboard ("
                    + state.currentSvgXml.length() + " chars)");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to copy SVG: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void chooseAndExport() {
        if (state.currentPuml == null || state.currentPuml.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No diagram to export yet.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save diagram as");
        fc.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter svg = new FileNameExtensionFilter("SVG (*.svg)", "svg");
        FileNameExtensionFilter png = new FileNameExtensionFilter("PNG (*.png)", "png");
        FileNameExtensionFilter puml = new FileNameExtensionFilter(
                "PlantUML source (*.puml)", "puml");
        fc.addChoosableFileFilter(svg);
        fc.addChoosableFileFilter(png);
        fc.addChoosableFileFilter(puml);
        fc.setFileFilter(svg);
        int r = fc.showSaveDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        UmlExporter.Format fmt = UmlExporter.Format.fromFileName(chosen.getName());
        if (fmt == null) {
            // フィルタ選択に応じて拡張子を補完
            String ext = "svg";
            if (fc.getFileFilter() == png) {
                ext = "png";
            } else if (fc.getFileFilter() == puml) {
                ext = "puml";
            }
            chosen = new File(chosen.getAbsolutePath() + "." + ext);
            fmt = UmlExporter.Format.fromFileName(chosen.getName());
        }
        try {
            BufferedImage pngImage = null;
            if (fmt == UmlExporter.Format.PNG) {
                // プレビューはベクター SVG なので PNG エクスポート時にだけ
                // 同じ PlantUML テキストから PNG をレンダリングする。
                pngImage = PlantUmlImageRenderer.toBufferedImage(state.currentPuml);
            }
            UmlExporter.export(fmt, chosen, state.currentPuml, pngImage);
            status.setText("Saved: " + chosen.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 関数一覧を Markdown テーブル / CSV のいずれかで保存する。
     * 選択フィルタ（または入力した拡張子）が {@code .csv} なら CSV を、それ以外は Markdown を書き出す。
     */
    public void exportFunctionList(String markdown, String csv, String dialogTitle) {
        if ((markdown == null || markdown.isEmpty()) && (csv == null || csv.isEmpty())) {
            JOptionPane.showMessageDialog(parent, "No content to export.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(dialogTitle);
        fc.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter mdFilter =
                new FileNameExtensionFilter("Markdown table (*.md)", "md");
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV (*.csv)", "csv");
        fc.addChoosableFileFilter(mdFilter);
        fc.addChoosableFileFilter(csvFilter);
        fc.setFileFilter(mdFilter);
        int r = fc.showSaveDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        String lower = chosen.getName().toLowerCase(java.util.Locale.ROOT);
        boolean asCsv = lower.endsWith(".csv")
                || (!lower.endsWith(".md") && fc.getFileFilter() == csvFilter);
        String ext = asCsv ? "csv" : "md";
        if (!lower.endsWith("." + ext)) {
            chosen = new File(chosen.getAbsolutePath() + "." + ext);
        }
        try {
            padtools.app.cli.CliOutput.writeText(chosen, asCsv ? csv : markdown);
            status.setText("Saved: " + chosen.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 全クラスのメンバー解析結果を Excel (.xlsx) ワークブックとして保存する。 */
    public void exportMemberWorkbook(
            java.util.List<padtools.core.formats.uml.JavaClassInfo> classes, String dialogTitle) {
        if (classes == null || classes.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No content to export.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(dialogTitle);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx"));
        int r = fc.showSaveDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            chosen = new File(chosen.getAbsolutePath() + ".xlsx");
        }
        try (java.io.OutputStream os = new java.io.FileOutputStream(chosen)) {
            padtools.core.formats.uml.MemberWorkbookExporter.write(classes, os);
            status.setText("Saved: " + chosen.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
