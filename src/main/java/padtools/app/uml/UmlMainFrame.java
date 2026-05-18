package padtools.app.uml;

import padtools.Main;
import padtools.Setting;
import padtools.util.ErrorListener;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * UML 専用のメインウィンドウ。
 *
 * <p>左ペインに {@link ProjectTreePanel}、右ペインに {@link SvgPreviewPanel}
 * と {@link PumlSourcePanel} をタブで切り替え表示する。
 * メニューから図種選択・ズーム操作・エクスポートを行える。</p>
 *
 * <p>図の生成と PNG レンダリングは {@link SwingWorker} でバックグラウンド実行し、
 * 図種/オプション変更時の再描画は {@link Timer} で 300ms デバウンスする。</p>
 */
public class UmlMainFrame extends JFrame {

    private static final String WINDOW_TITLE = "PadTools UML";
    private static final int MENU_MASK = computeMenuShortcutMask();

    private static int computeMenuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ex) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    private final ProjectAnalysisCache cache = new ProjectAnalysisCache();
    private final ProjectTreePanel treePanel = new ProjectTreePanel();
    private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
    private final PumlSourcePanel sourcePanel = new PumlSourcePanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("100%");
    private final ButtonGroup diagramGroup = new ButtonGroup();
    private final java.util.EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems
            = new java.util.EnumMap<>(DiagramKind.class);

    private final Timer refreshTimer = new Timer(300, e -> refreshDiagramNow());

    private DiagramKind currentKind = DiagramKind.CLASS;
    private String currentPuml;
    /** 現在選択されているシーケンス図起点 ({@code Class.method})。null なら未設定。 */
    private String sequenceEntry;

    public UmlMainFrame(File initialProject) {
        super(WINDOW_TITLE);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
                dispose();
            }
        });

        refreshTimer.setRepeats(false);
        previewPanel.setZoomChangeListener(this::updateZoomLabel);
        treePanel.setOnMethodSelected(this::onTreeMethodSelected);

        setJMenuBar(buildMenuBar());

        // 右側: プレビュー (画像) と PlantUML ソースのタブ
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.addTab("Preview", new JScrollPane(previewPanel));
        rightTabs.addTab("PlantUML Source", sourcePanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treePanel, rightTabs);
        split.setResizeWeight(0.22);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);

        add(buildStatusBar(), BorderLayout.SOUTH);

        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1200;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 800;
        setPreferredSize(new Dimension(w, h));
        pack();
        restoreWindowLocation(setting);

        if (initialProject != null && initialProject.isDirectory()) {
            SwingUtilities.invokeLater(() -> loadProject(initialProject));
        }
    }

    // --- メニュー -------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(buildFileMenu());
        bar.add(buildDiagramMenu());
        bar.add(buildViewMenu());
        bar.add(buildHelpMenu());
        return bar;
    }

    private JMenu buildFileMenu() {
        JMenu m = new JMenu("File");
        m.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem("Open Project...");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_MASK));
        open.addActionListener(e -> chooseProject());
        JMenuItem save = new JMenuItem("Save Diagram As...");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU_MASK));
        save.addActionListener(e -> chooseAndExport());
        JMenuItem refresh = new JMenuItem("Refresh");
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> refreshDiagram());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            saveWindowState();
            dispose();
        });
        m.add(open);
        m.add(save);
        m.addSeparator();
        m.add(refresh);
        m.addSeparator();
        m.add(exit);
        return m;
    }

    private JMenu buildDiagramMenu() {
        JMenu m = new JMenu("Diagram");
        m.setMnemonic(KeyEvent.VK_D);
        for (DiagramKind k : DiagramKind.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(k.getDisplayName());
            if (k == DiagramKind.CLASS) {
                item.setSelected(true);
            }
            item.addActionListener(e -> {
                currentKind = k;
                refreshDiagram();
            });
            diagramGroup.add(item);
            diagramItems.put(k, item);
            m.add(item);
        }
        m.addSeparator();
        JMenuItem pickEntry = new JMenuItem("Choose Sequence Entry...");
        pickEntry.addActionListener(e -> pickSequenceEntry());
        m.add(pickEntry);
        return m;
    }

    private JMenu buildViewMenu() {
        JMenu m = new JMenu("View");
        m.setMnemonic(KeyEvent.VK_V);
        JMenuItem zoomIn = new JMenuItem("Zoom In");
        zoomIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_MASK));
        zoomIn.addActionListener(e -> previewPanel.zoomIn());
        JMenuItem zoomOut = new JMenuItem("Zoom Out");
        zoomOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, MENU_MASK));
        zoomOut.addActionListener(e -> previewPanel.zoomOut());
        JMenuItem zoomReset = new JMenuItem("Zoom 100%");
        zoomReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, MENU_MASK));
        zoomReset.addActionListener(e -> previewPanel.zoomReset());
        JMenuItem zoomFit = new JMenuItem("Zoom to Fit");
        zoomFit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_MASK));
        zoomFit.addActionListener(e -> previewPanel.zoomToFit());
        m.add(zoomIn);
        m.add(zoomOut);
        m.add(zoomReset);
        m.add(zoomFit);
        return m;
    }

    private JMenu buildHelpMenu() {
        JMenu m = new JMenu("Help");
        m.setMnemonic(KeyEvent.VK_H);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "PadTools UML\n\n"
                        + "Java + Android + Gradle UML diagram generator.\n"
                        + "Bundled PlantUML for rendering.",
                "About PadTools UML",
                JOptionPane.INFORMATION_MESSAGE));
        m.add(about);
        return m;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(status, BorderLayout.CENTER);
        bar.add(zoomLabel, BorderLayout.EAST);
        return bar;
    }

    // --- イベント処理 ---------------------------------------------------------

    private void chooseProject() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Open Android / Gradle project");
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            loadProject(fc.getSelectedFile());
        }
    }

    private void loadProject(File root) {
        status.setText("Analyzing " + root.getName() + " ...");
        treePanel.clear();
        new SwingWorker<Void, Void>() {
            private Throwable error;

            @Override
            protected Void doInBackground() {
                try {
                    cache.clear();
                    cache.load(root, ErrorListener.silent());
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to analyze project: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                    return;
                }
                treePanel.populate(cache.getAnalysis(), cache.getClasses(),
                        root.getName());
                sequenceEntry = null;
                status.setText("Analyzed " + cache.getClasses().size() + " class(es)"
                        + " from " + root.getAbsolutePath());
                refreshDiagram();
            }
        }.execute();
    }

    /**
     * 左ペインのツリーでメソッドが選択された際のハンドラ。
     * シーケンス図モードへ切り替えてその起点メソッドで再描画する。
     */
    private void onTreeMethodSelected(ProjectTreePanel.MethodSelection sel) {
        if (sel == null) {
            return;
        }
        sequenceEntry = sel.getEntry();
        currentKind = DiagramKind.SEQUENCE;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
        if (item != null) {
            item.setSelected(true);
        }
        refreshDiagram();
    }

    private void pickSequenceEntry() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(this, cache.getClasses());
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No methods found in this project.",
                    "Sequence diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            sequenceEntry = picked;
            currentKind = DiagramKind.SEQUENCE;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
            if (item != null) {
                item.setSelected(true);
            }
            refreshDiagram();
        }
    }

    private void refreshDiagram() {
        refreshTimer.restart();
    }

    private void refreshDiagramNow() {
        if (!cache.isLoaded()) {
            return;
        }
        final DiagramKind kind = currentKind;
        if (kind == DiagramKind.SEQUENCE
                && (sequenceEntry == null || sequenceEntry.isEmpty())) {
            previewPanel.setImage(null);
            sourcePanel.setText("");
            status.setText("Choose a sequence entry from Diagram menu.");
            return;
        }
        status.setText("Rendering " + kind.getDisplayName() + " ...");
        final String entry = sequenceEntry;
        new SwingWorker<RenderResult, Void>() {
            private Throwable error;

            @Override
            protected RenderResult doInBackground() {
                try {
                    DiagramRequest req = (kind == DiagramKind.SEQUENCE && entry != null)
                            ? buildSequenceRequest(entry)
                            : new DiagramRequest(kind);
                    String puml = DiagramService.generatePuml(req, cache);
                    BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
                    return new RenderResult(puml, img);
                } catch (Throwable ex) {
                    error = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to render: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                    return;
                }
                try {
                    RenderResult r = get();
                    if (r == null || r.image == null) {
                        previewPanel.setImage(null);
                        sourcePanel.setText(r != null ? r.puml : "");
                        status.setText(kind.getDisplayName() + ": (no image)");
                        return;
                    }
                    currentPuml = r.puml;
                    previewPanel.setImage(r.image);
                    sourcePanel.setText(r.puml);
                    status.setText(kind.getDisplayName() + " rendered ("
                            + r.image.getWidth() + "x" + r.image.getHeight() + ")");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to display: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private DiagramRequest buildSequenceRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Sequence entry must be in 'Class.method' format: " + entry);
        }
        return new DiagramRequest(DiagramKind.SEQUENCE,
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }

    private void chooseAndExport() {
        if (currentPuml == null || currentPuml.isEmpty()) {
            JOptionPane.showMessageDialog(this,
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
        int r = fc.showSaveDialog(this);
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
            UmlExporter.export(fmt, chosen, currentPuml, previewPanel.getImage());
            status.setText("Saved: " + chosen.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- 状態管理 -------------------------------------------------------------

    private void updateZoomLabel() {
        int pct = (int) Math.round(previewPanel.getZoomLevel() * 100);
        zoomLabel.setText(pct + "%");
    }

    private void restoreWindowLocation(Setting setting) {
        if (setting.getWindowX() >= 0 && setting.getWindowY() >= 0) {
            setLocation(setting.getWindowX(), setting.getWindowY());
        } else {
            setLocationRelativeTo(null);
        }
    }

    private void saveWindowState() {
        try {
            Setting setting = Main.getSetting();
            setting.setWindowX(getX());
            setting.setWindowY(getY());
            setting.setWindowWidth(getWidth());
            setting.setWindowHeight(getHeight());
            Main.saveSetting();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
    }

    private static final class RenderResult {
        final String puml;
        final BufferedImage image;

        RenderResult(String puml, BufferedImage image) {
            this.puml = puml;
            this.image = image;
        }
    }

    /** GUI を EDT で起動する。 */
    public static void launch(File initialProject) {
        SwingUtilities.invokeLater(() -> {
            UmlMainFrame f = new UmlMainFrame(initialProject);
            f.setVisible(true);
        });
    }
}
