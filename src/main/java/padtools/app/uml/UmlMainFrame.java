package padtools.app.uml;

import padtools.Main;
import padtools.Setting;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.uml.DiagramStyle;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.util.CancelToken;
import padtools.util.ErrorListener;
import padtools.util.ProgressListener;

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
import javax.swing.JProgressBar;
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
import java.util.List;

import padtools.app.uml.PlantUmlSvgRenderer.RenderedSvg;

/**
 * UML 専用のメインウィンドウ。
 *
 * <p>左ペインに {@link ProjectTreePanel}、右ペインに {@link SvgPreviewPanel}
 * と {@link PumlSourcePanel} をタブで切り替え表示する。
 * メニューから図種選択・ズーム操作・エクスポートを行える。</p>
 *
 * <p>図の生成と SVG (ベクター) レンダリングは {@link SwingWorker} で
 * バックグラウンド実行し、図種/オプション変更時の再描画は {@link Timer} で
 * 300ms デバウンスする。PNG ラスタ化は保存時のみ行う。</p>
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
    private final ManifestSummaryPanel manifestSummaryPanel = new ManifestSummaryPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("100%");
    private final JProgressBar loadProgress = new JProgressBar();
    private final JMenuItem cancelLoadingItem = new JMenuItem("Cancel Loading");
    private final ButtonGroup diagramGroup = new ButtonGroup();
    private final java.util.EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems
            = new java.util.EnumMap<>(DiagramKind.class);
    private final ButtonGroup themeGroup = new ButtonGroup();
    private final java.util.Map<String, JRadioButtonMenuItem> themeItems
            = new java.util.LinkedHashMap<>();

    private final Timer refreshTimer = new Timer(300, e -> refreshDiagramNow());

    private DiagramKind currentKind = DiagramKind.CLASS;
    private String currentPuml;
    /** 現在選択されているシーケンス図起点 ({@code Class.method})。null なら未設定。 */
    private String sequenceEntry;
    /** クラス図の現在の絞り込みスコープ。null なら全件表示。 */
    private DiagramScope currentScope;
    /** 進行中のロード処理のキャンセル用 (null ならロード中ではない)。 */
    private CancelToken loadingCancelToken;

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
        treePanel.setOnPackageSelected(this::onTreePackageSelected);
        treePanel.setOnManifestSelected(this::onTreeManifestSelected);
        treePanel.setOnComponentSelected(this::onTreeComponentSelected);

        loadProgress.setStringPainted(true);
        loadProgress.setVisible(false);
        loadProgress.setPreferredSize(new Dimension(200, 16));

        setJMenuBar(buildMenuBar());

        // 右側: プレビュー (ベクター SVG) と PlantUML ソース / Manifest Summary のタブ
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.addTab("Preview", new JScrollPane(previewPanel));
        rightTabs.addTab("PlantUML Source", sourcePanel);
        rightTabs.addTab("Manifest Summary", manifestSummaryPanel);

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
        bar.add(buildStyleMenu());
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
        cancelLoadingItem.addActionListener(e -> {
            if (loadingCancelToken != null) {
                loadingCancelToken.cancel();
                status.setText("Cancelling...");
            }
        });
        cancelLoadingItem.setEnabled(false);
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            saveWindowState();
            dispose();
        });
        m.add(open);
        m.add(save);
        m.addSeparator();
        m.add(refresh);
        m.add(cancelLoadingItem);
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
        JMenuItem scope = new JMenuItem("Scope...");
        scope.addActionListener(e -> openScopeDialog());
        m.add(scope);
        JMenuItem clearScope = new JMenuItem("Clear Scope");
        clearScope.addActionListener(e -> {
            currentScope = null;
            refreshDiagram();
        });
        m.add(clearScope);
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

    private JMenu buildStyleMenu() {
        JMenu m = new JMenu("Style");
        m.setMnemonic(KeyEvent.VK_S);
        DiagramStyle current = PlantUmlRenderer.getStyle();
        for (String theme : StyleSettingsDialog.THEMES) {
            String label = theme.isEmpty() ? "(None)" : theme;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
            if (theme.equals(current.getTheme() == null ? "" : current.getTheme())) {
                item.setSelected(true);
            }
            item.addActionListener(e -> applyTheme(theme));
            themeGroup.add(item);
            themeItems.put(theme, item);
            m.add(item);
        }
        m.addSeparator();
        JMenuItem advanced = new JMenuItem("Style Settings...");
        advanced.addActionListener(e -> openStyleSettings());
        m.add(advanced);
        return m;
    }

    private void applyTheme(String theme) {
        DiagramStyle next = PlantUmlRenderer.getStyle().copy();
        next.setTheme(theme);
        applyStyle(next);
    }

    private void openStyleSettings() {
        DiagramStyle edited = StyleSettingsDialog.showDialog(this, PlantUmlRenderer.getStyle());
        if (edited != null) {
            applyStyle(edited);
        }
    }

    /** スタイル変更を全方位 (レンダラ / 永続化 / メニュー UI / 再描画) に反映する。 */
    private void applyStyle(DiagramStyle style) {
        PlantUmlRenderer.setStyle(style);
        try {
            Main.getSetting().setStyle(style);
            Main.saveSetting();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        syncThemeMenuSelection(style);
        refreshDiagram();
    }

    private void syncThemeMenuSelection(DiagramStyle style) {
        String theme = style.getTheme() == null ? "" : style.getTheme();
        JRadioButtonMenuItem item = themeItems.get(theme);
        if (item != null) {
            item.setSelected(true);
        } else {
            // カスタムテーマ名: ラジオ選択を外す
            themeGroup.clearSelection();
        }
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
        JPanel east = new JPanel(new BorderLayout(8, 0));
        east.add(loadProgress, BorderLayout.WEST);
        east.add(zoomLabel, BorderLayout.EAST);
        bar.add(east, BorderLayout.EAST);
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
        manifestSummaryPanel.setText("");
        loadProgress.setVisible(true);
        loadProgress.setIndeterminate(true);
        loadProgress.setString("Scanning...");
        cancelLoadingItem.setEnabled(true);
        final CancelToken cancel = new CancelToken();
        loadingCancelToken = cancel;
        // EDT に間引きながら進捗を流す。生のリスナーは複数スレッドから呼ばれる。
        final ProgressListener prog = ProgressListener.throttled(
                (done, total, message) -> SwingUtilities.invokeLater(
                        () -> updateLoadProgress(done, total, message)),
                150L);
        new SwingWorker<Void, Void>() {
            private Throwable error;
            private boolean cancelled;

            @Override
            protected Void doInBackground() {
                try {
                    cache.clear();
                    cache.load(root, ErrorListener.silent(), prog, cancel, null);
                    cancelled = cancel.isCancelled();
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                loadingCancelToken = null;
                cancelLoadingItem.setEnabled(false);
                loadProgress.setVisible(false);
                loadProgress.setIndeterminate(false);
                loadProgress.setValue(0);
                loadProgress.setString(null);
                if (error != null) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to analyze project: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                    return;
                }
                if (cancelled) {
                    status.setText("Cancelled.");
                    return;
                }
                treePanel.populate(cache.getAnalysis(), cache.getClasses(),
                        root.getName(), cache.getClassToModule());
                sequenceEntry = null;
                currentScope = null;
                updateManifestSummary();
                status.setText("Analyzed " + cache.getClasses().size() + " class(es)"
                        + " from " + root.getAbsolutePath());
                refreshDiagram();
            }
        }.execute();
    }

    private void updateLoadProgress(int done, int total, String message) {
        if (total > 0) {
            if (loadProgress.isIndeterminate()) {
                loadProgress.setIndeterminate(false);
            }
            loadProgress.setMaximum(total);
            loadProgress.setValue(Math.min(done, total));
            loadProgress.setString(done + "/" + total);
            status.setText("Analyzing " + done + "/" + total
                    + (message != null && !message.isEmpty() ? " — " + message : ""));
        } else {
            loadProgress.setIndeterminate(true);
            loadProgress.setString(message != null ? message : "Scanning...");
            if (message != null) {
                status.setText(message);
            }
        }
    }

    private void onTreePackageSelected(String pkg) {
        if (pkg == null || pkg.isEmpty() || "(default)".equals(pkg)) {
            return;
        }
        currentScope = DiagramScope.builder().includePackage(pkg).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: package " + pkg);
        refreshDiagram();
    }

    private void openScopeDialog() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> packages = new java.util.TreeSet<>();
        java.util.Set<String> modules = new java.util.TreeSet<>(cache.getClassToModule().values());
        for (padtools.core.formats.uml.JavaClassInfo c : cache.getClasses()) {
            String p = c.getPackageName();
            if (p != null && !p.isEmpty()) {
                packages.add(p);
            }
        }
        DiagramScopeDialog dlg = new DiagramScopeDialog(this,
                List.copyOf(packages), List.copyOf(modules), currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        if (picked != null) {
            currentScope = picked.isEmpty() ? null : picked;
            refreshDiagram();
        }
    }

    /** Manifest Summary タブのテキストを最新の解析結果で更新する。 */
    private void updateManifestSummary() {
        if (cache.isLoaded() && cache.getAnalysis() != null) {
            manifestSummaryPanel.setText(
                    TextSummaryReport.toManifestMarkdown(cache.getAnalysis()));
        } else {
            manifestSummaryPanel.setText("");
        }
    }

    /**
     * 左ペインのツリーで Manifest ノード (または配下の Permissions / Features
     * グループ) が選択されたら Manifest 図モードへ切り替える。
     */
    private void onTreeManifestSelected(padtools.core.formats.android.AndroidManifestInfo m) {
        switchToManifestDiagram();
    }

    /**
     * 左ペインのツリーで Manifest 配下の個別コンポーネントが選択されたら
     * Manifest 図モードへ切り替える (将来的に該当ノードへ強調表示を入れる余地)。
     */
    private void onTreeComponentSelected(
            padtools.core.formats.android.AndroidComponentInfo c) {
        switchToManifestDiagram();
    }

    private void switchToManifestDiagram() {
        currentKind = DiagramKind.MANIFEST;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.MANIFEST);
        if (item != null) {
            item.setSelected(true);
        }
        refreshDiagram();
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
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a sequence entry from Diagram menu.");
            return;
        }
        status.setText("Rendering " + kind.getDisplayName() + " ...");
        final String entry = sequenceEntry;
        final DiagramScope scope = currentScope;
        new SwingWorker<RenderResult, Void>() {
            private Throwable error;

            @Override
            protected RenderResult doInBackground() {
                try {
                    DiagramRequest req = (kind == DiagramKind.SEQUENCE && entry != null)
                            ? buildSequenceRequest(entry)
                            : new DiagramRequest(kind, null, null, true, scope);
                    String puml = DiagramService.generatePuml(req, cache);
                    // ベクター SVG として描画して、PlantUML の PNG 4096x4096
                    // キャンバス上限による切り詰めを回避する。
                    RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
                    return new RenderResult(puml, svg);
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
                    if (r == null || r.svg == null) {
                        previewPanel.setSvgGraphicsNode(null, 0, 0);
                        sourcePanel.setText(r != null ? r.puml : "");
                        status.setText(kind.getDisplayName() + ": (no diagram)");
                        return;
                    }
                    currentPuml = r.puml;
                    previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                            r.svg.getWidth(), r.svg.getHeight());
                    sourcePanel.setText(r.puml);
                    status.setText(kind.getDisplayName() + " rendered ("
                            + (int) Math.round(r.svg.getWidth()) + "x"
                            + (int) Math.round(r.svg.getHeight()) + ", SVG)");
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
            BufferedImage pngImage = null;
            if (fmt == UmlExporter.Format.PNG) {
                // プレビューはベクター SVG なので PNG エクスポート時にだけ
                // 同じ PlantUML テキストから PNG をレンダリングする。
                pngImage = PlantUmlImageRenderer.toBufferedImage(currentPuml);
            }
            UmlExporter.export(fmt, chosen, currentPuml, pngImage);
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
        final RenderedSvg svg;

        RenderResult(String puml, RenderedSvg svg) {
            this.puml = puml;
            this.svg = svg;
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
