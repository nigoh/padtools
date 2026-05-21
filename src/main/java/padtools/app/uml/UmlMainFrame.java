package padtools.app.uml;

import padtools.Main;
import padtools.Setting;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.uml.DiagramStyle;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.util.CancelToken;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.io.File;
import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
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
    private final ReferenceIndexCache refIndexCache = new ReferenceIndexCache(cache);
    private final ProjectTreePanel treePanel = new ProjectTreePanel();
    private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
    private final PumlSourcePanel sourcePanel = new PumlSourcePanel();
    private final ManifestSummaryPanel manifestSummaryPanel = new ManifestSummaryPanel();
    private final padtools.app.uml.explore.ImpactExplorerPanel impactPanel
            = new padtools.app.uml.explore.ImpactExplorerPanel(refIndexCache);
    private final padtools.app.uml.explore.ReverseReferencePanel referencesPanel
            = new padtools.app.uml.explore.ReverseReferencePanel(refIndexCache);
    private final padtools.app.uml.explore.FuncDiffPanel funcDiffPanel
            = new padtools.app.uml.explore.FuncDiffPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("100%");
    private final JProgressBar loadProgress = new JProgressBar();
    private JMenuItem cancelLoadingItem;
    private ButtonGroup diagramGroup;
    private java.util.EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    /** ツールバー上の「図種切替」トグルボタン。メニュー側ラジオと選択状態を同期する。 */
    private java.util.EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private ButtonGroup themeGroup;
    private java.util.Map<String, JRadioButtonMenuItem> themeItems;

    private final Timer refreshTimer = new Timer(300, e -> refreshDiagramNow());

    /** タブマネージャ。 */
    private DiagramTabPane tabPane;
    /** 右側のフラットタブバー (Home / 動的タブ / Manifest / Impact / References)。 */
    private JTabbedPane mainTabs;

    /** 全可変状態。テスト互換のため currentKind のみ UmlMainFrame に直接残す。 */
    private final DiagramState state = new DiagramState();

    DiagramKind currentKind = DiagramKind.CLASS;
    /** 現在ロード中のプロジェクトルート。null なら未ロード。 */
    private File currentProjectRoot;
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

        wirePanelListeners();
        buildMenuBar();
        buildCenterTabs();
        buildToolBar();
        controller = createDiagramController();
        add(buildStatusBar(), BorderLayout.SOUTH);
        applyInitialWindowSize();
        initPersistorsAndLoader();

        if (initialProject != null && initialProject.isDirectory()) {
            SwingUtilities.invokeLater(() -> loadProject(initialProject));
        }
    }

    /** previewPanel / treePanel / 進捗バー等のイベントリスナを配線する。 */
    private void wirePanelListeners() {
        refreshTimer.setRepeats(false);
        previewPanel.setZoomChangeListener(this::updateZoomLabel);
        previewPanel.setOnLinkPopup(this::onPreviewLinkPopup);
        previewPanel.setOnLinkClick((link, ev) -> controller.onPreviewLinkClick(link, ev));
        previewPanel.setCopyFeedbackListener(msg -> status.setText(msg));
        treePanel.setOnMethodSelected(sel -> controller.onTreeMethodSelected(sel));
        treePanel.setOnActivityMethodSelected(sel -> controller.onTreeActivityMethodSelected(sel));
        treePanel.setOnClassSelected(cls -> controller.onTreeClassSelected(cls));
        treePanel.setOnPackageSelected(pkg -> controller.onTreePackageSelected(pkg));
        treePanel.setOnModuleSelected(mod -> controller.onTreeModuleSelected(mod));
        treePanel.setOnManifestSelected(m -> controller.onTreeManifestSelected(m));
        treePanel.setOnComponentSelected(c -> controller.onTreeComponentSelected(c));
        treePanel.setOnOpenInNewTab(req -> controller.onTreeOpenInNewTab(req));

        loadProgress.setStringPainted(true);
        loadProgress.setVisible(false);
        loadProgress.setPreferredSize(new Dimension(200, 16));
    }

    /** メニューバーを構築して各メニュー項目フィールドへ反映する。 */
    private void buildMenuBar() {
        MenuBarBuilder.Callbacks mcb = new MenuBarBuilder.Callbacks();
        mcb.chooseProject = this::chooseProject;
        mcb.chooseAndExport = this::chooseAndExport;
        mcb.exportClassDiagramsPerFolder = this::exportClassDiagramsPerFolder;
        mcb.refreshDiagram = this::refreshDiagram;
        mcb.cancelLoading = () -> {
            if (loadingCancelToken != null) {
                loadingCancelToken.cancel();
                status.setText("Cancelling...");
            }
        };
        mcb.exitApp = () -> { saveWindowState(); dispose(); };
        mcb.loadProject = this::loadProject;
        mcb.openEntitySearch = () -> controller.openEntitySearch();
        mcb.pickSequenceEntry = () -> controller.pickSequenceEntry();
        mcb.openParticipantFilterDialog = () -> controller.openParticipantFilterDialog();
        mcb.clearSequenceParticipants = () -> {
            if (!state.sequenceHiddenParticipants.isEmpty()) {
                state.sequenceHiddenParticipants.clear();
                status.setText("Cleared sequence participant filter.");
                refreshDiagram();
            }
        };
        mcb.pickActivityEntry = () -> controller.pickActivityEntry();
        mcb.pickLayoutFile = () -> controller.pickLayoutFile();
        mcb.pickNavigationGraph = () -> controller.pickNavigationGraph();
        mcb.applyPreset = this::applyPreset;
        mcb.openScopeDialog = () -> controller.openScopeDialog();
        mcb.clearScope = () -> { state.currentScope = null; refreshDiagram(); };
        mcb.selectDiagramKindFromMenu = k -> controller.selectDiagramKind(k);
        mcb.syncDiagramToggle = k -> controller.syncDiagramToggle(k);
        mcb.applyTheme = this::applyTheme;
        mcb.openStyleSettings = this::openStyleSettings;
        mcb.zoomIn = previewPanel::zoomIn;
        mcb.zoomOut = previewPanel::zoomOut;
        mcb.zoomReset = previewPanel::zoomReset;
        mcb.zoomToFit = previewPanel::zoomToFit;
        MenuBarBuilder.Result menuResult =
                new MenuBarBuilder(DiagramKind.CLASS, MENU_MASK, mcb, this).build();
        cancelLoadingItem = menuResult.cancelLoadingItem;
        diagramItems = menuResult.diagramItems;
        diagramGroup = menuResult.diagramGroup;
        themeItems = menuResult.themeItems;
        themeGroup = menuResult.themeGroup;
        setJMenuBar(menuResult.menuBar);
    }

    /** 中央のツリー + タブ (Home/Manifest/Impact/References/Func Diff) を構築する。 */
    private void buildCenterTabs() {
        // 右側: 1 層のフラットタブバー
        // [Home] [動的タブ…] [Manifest] [Impact] [References]
        mainTabs = new JTabbedPane(JTabbedPane.TOP);

        // Home タブ: 共有の previewPanel / sourcePanel を JSplitPane で上下に配置
        JSplitPane homeSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(previewPanel), sourcePanel);
        homeSplit.setResizeWeight(0.85);
        mainTabs.addTab("Home", homeSplit);

        // ユーティリティタブ (固定・末尾 4 本)
        mainTabs.addTab("Manifest", manifestSummaryPanel);
        mainTabs.addTab("Impact", impactPanel);
        mainTabs.addTab("References", referencesPanel);
        mainTabs.addTab("Func Diff", funcDiffPanel);

        // 動的タブマネージャ (fixedSuffix=4 で Manifest/Impact/References/Func Diff の手前に挿入)
        tabPane = new DiagramTabPane(mainTabs, 4, cache, status::setText);

        // Home タブが表示された後に divider 位置を相対値でセット
        mainTabs.addChangeListener(ev -> {
            if (mainTabs.getSelectedIndex() == 0) {
                javax.swing.SwingUtilities.invokeLater(
                        () -> homeSplit.setDividerLocation(0.85));
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treePanel, mainTabs);
        split.setResizeWeight(0.22);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);
    }

    /** 上部ツールバーを構築する。 */
    private void buildToolBar() {
        ToolBarBuilder.Callbacks tcb = new ToolBarBuilder.Callbacks();
        tcb.chooseProject = this::chooseProject;
        tcb.chooseAndExport = this::chooseAndExport;
        tcb.refreshDiagram = this::refreshDiagram;
        tcb.openEntitySearch = () -> controller.openEntitySearch();
        tcb.selectDiagramKind = k -> controller.selectDiagramKind(k);
        ToolBarBuilder.Result toolBarResult =
                new ToolBarBuilder(DiagramKind.CLASS, tcb).build();
        diagramToggles = toolBarResult.diagramToggles;
        add(toolBarResult.toolBarPanel, BorderLayout.NORTH);
    }

    /** 保存済みウィンドウサイズ・位置を適用して pack する。 */
    private void applyInitialWindowSize() {
        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1200;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 800;
        setPreferredSize(new Dimension(w, h));
        pack();
        restoreWindowLocation(setting);
    }

    /** プロジェクト設定の永続化担当とプロジェクトローダを生成する。 */
    private void initPersistorsAndLoader() {
        settingsPersistor = new ProjectSettingsPersistor(
                Main::getSetting,
                Main::saveSetting,
                () -> syncThemeMenuSelection(PlantUmlRenderer.getStyle()));

        projectLoader = new ProjectLoader(
                cache, refIndexCache, state, treePanel, manifestSummaryPanel,
                loadProgress, cancelLoadingItem, status, this,
                token -> loadingCancelToken = token,
                root -> currentProjectRoot = root,
                root -> {
                    persistAndRestoreProjectSettings(root);
                    updateManifestSummary();
                    refreshDiagram();
                });
    }

    private ProjectLoader projectLoader;
    private ProjectSettingsPersistor settingsPersistor;
    private DiagramController controller;

    /** 図制御コントローラを必要な状態・UI 参照・コールバックを束ねて生成する。 */
    private DiagramController createDiagramController() {
        DiagramControllerDeps deps = new DiagramControllerDeps();
        deps.state = state;
        deps.cacheSupplier = () -> cache;
        deps.diagramItems = diagramItems;
        deps.diagramToggles = diagramToggles;
        deps.treePanel = treePanel;
        deps.mainTabs = mainTabs;
        deps.tabPane = tabPane;
        deps.statusLabel = status;
        deps.parentFrame = this;
        deps.refreshDiagram = this::refreshDiagram;
        deps.onKindChanged = kind -> this.currentKind = kind;
        return new DiagramController(deps);
    }

    // --- スタイル / プリセット ------------------------------------------------

    /**
     * 指定された {@link DiagramPreset} を現在のスコープに適用して再描画する。
     * 既存スコープのフィルタ設定 (パッケージ・seed 等) は維持し、表示密度関連の
     * 項目だけプリセットで書き換える。
     */
    private void applyPreset(DiagramPreset p) {
        DiagramScope.Builder b = state.currentScope != null
                ? state.currentScope.toBuilder()
                : DiagramScope.builder();
        p.applyTo(b);
        state.currentScope = b.build();
        status.setText("Preset: " + p.getDisplayName());
        refreshDiagram();
    }

    private void applyTheme(String theme) {
        DiagramStyle next = PlantUmlRenderer.getStyle().copy();
        next.setTheme(theme);
        applyStyle(next);
    }

    private void openStyleSettings() {
        padtools.Setting setting = Main.getSetting();
        boolean curShow = setting != null && setting.isSequenceShowComments();
        padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle curStyle =
                setting != null && "NOTE".equalsIgnoreCase(setting.getSequenceCommentStyle())
                        ? padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.NOTE
                        : padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
        padtools.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement curPlacement =
                setting != null && "PARTICIPANT_TOP".equalsIgnoreCase(
                        setting.getSequenceCommentPlacement())
                        ? padtools.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        : padtools.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        boolean curQualify = setting == null || setting.isSequenceQualifyMethodNames();
        StyleSettingsDialog.ClassDiagramPrefs curClass = setting != null
                ? new StyleSettingsDialog.ClassDiagramPrefs(
                        setting.isClassDiagramShowFields(),
                        setting.isClassDiagramShowMethods(),
                        setting.isClassDiagramShowAnnotations(),
                        setting.isClassDiagramPublicOnly(),
                        setting.isClassDiagramExcludeExternal(),
                        setting.getClassDiagramCommentMaxLength(),
                        StyleSettingsDialog.ClassDiagramPrefs.parseCsv(
                                setting.getClassDiagramHiddenAnnotations()))
                : StyleSettingsDialog.ClassDiagramPrefs.defaults();
        int curCallGraphDepth = setting != null ? setting.getCallGraphMaxDepth() : 4;
        StyleSettingsDialog.Result edited = StyleSettingsDialog.showDialog(
                this, PlantUmlRenderer.getStyle(), curShow, curStyle,
                curPlacement, curQualify, curClass, curCallGraphDepth);
        if (edited != null) {
            applyStyleSettings(edited);
        }
    }

    /** Style ダイアログ結果 (Style + シーケンス図設定 + クラス図設定 + コールグラフ設定) を反映する。 */
    private void applyStyleSettings(StyleSettingsDialog.Result r) {
        try {
            padtools.Setting setting = Main.getSetting();
            if (setting != null) {
                setting.setSequenceShowComments(r.sequenceShowComments);
                setting.setSequenceCommentStyle(r.sequenceCommentStyle.name());
                setting.setSequenceCommentPlacement(r.sequenceCommentPlacement.name());
                setting.setSequenceQualifyMethodNames(r.sequenceQualifyMethodNames);
                setting.setCallGraphMaxDepth(r.callGraphMaxDepth);
                if (r.classDiagram != null) {
                    StyleSettingsDialog.ClassDiagramPrefs cp = r.classDiagram;
                    setting.setClassDiagramShowFields(cp.showFields);
                    setting.setClassDiagramShowMethods(cp.showMethods);
                    setting.setClassDiagramShowAnnotations(cp.showAnnotations);
                    setting.setClassDiagramPublicOnly(cp.publicOnly);
                    setting.setClassDiagramExcludeExternal(cp.excludeExternal);
                    setting.setClassDiagramCommentMaxLength(cp.commentMaxLength);
                    setting.setClassDiagramHiddenAnnotations(cp.hiddenAnnotationsCsv());
                }
            }
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        saveCurrentProjectSettings();
        applyStyle(r.style);
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
        saveCurrentProjectSettings();
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
        java.util.List<padtools.ProjectRecord> records;
        try {
            records = padtools.ProjectRepository.getInstance().listRecent(10);
        } catch (RuntimeException ex) {
            records = java.util.Collections.emptyList();
        }
        File chosen = OpenProjectDialog.show(this, records);
        if (chosen != null) {
            loadProject(chosen);
        }
    }

    private void loadProject(File root) {
        projectLoader.start(root);
    }

    /**
     * 現在ロード中のプロジェクトを再帰的に走査し、ソースファイルを含むフォルダごとに
     * 1 枚ずつ PlantUML クラス図 ({@code classes.puml} + {@code classes.svg}) を
     * 出力する。実処理は {@link PerFolderExporter} に委譲。
     */
    private void exportClassDiagramsPerFolder() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File projectRoot = cache.getProjectRoot();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Loaded project root is unavailable.",
                    "No project", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PerFolderExporter.choose(this, projectRoot,
                cache.getClasses(), cache.getIndex(),
                loadProgress, status);
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
     * プレビュー上で右クリックされたとき、図のエクスポートポップアップを表示する。
     * {@code link} はヒットしたリンク領域 (null なら非リンク領域でのクリック)。
     */
    private void onPreviewLinkPopup(LinkArea link, MouseEvent event) {
        if (event == null) {
            return;
        }
        JPopupMenu popup = buildExportPopup();
        popup.show(event.getComponent(), event.getX(), event.getY());
    }

    /** 右クリックエクスポートポップアップを構築する (SVG / PNG / PUML 保存 + SVG コピー)。 */
    private JPopupMenu buildExportPopup() {
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
            JOptionPane.showMessageDialog(this,
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
        int r = fc.showSaveDialog(this);
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
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 現在の SVG XML 全体をクリップボードへコピーする。 */
    private void copySvgToClipboard() {
        if (state.currentSvgXml == null || state.currentSvgXml.isEmpty()) {
            JOptionPane.showMessageDialog(this,
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
            JOptionPane.showMessageDialog(this,
                    "Failed to copy SVG: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ステータス バー表示用に長文を短縮する。Batik 等が data: URI を本文に含めて
     * 投げてくる超長文メッセージをそのまま流すと UI が読めなくなるため、
     * 改行とスペースで折り返した先頭 {@code max} 文字に省略記号を付けて返す。
     */
    static String truncateForStatus(String msg, int max) {
        if (msg == null) {
            return "";
        }
        // 改行・連続空白を 1 スペースに畳んでから長さで切る
        String oneLine = msg.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, Math.max(0, max - 1)) + "…";
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
                && (state.sequenceEntry == null || state.sequenceEntry.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a sequence entry from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.ACTIVITY
                && (state.activityEntry == null || state.activityEntry.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose an activity method from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.LAYOUT
                && (state.currentLayoutKey == null || state.currentLayoutKey.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a layout file from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.NAVIGATION
                && (state.currentNavigationKey == null || state.currentNavigationKey.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a navigation file from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.CALLGRAPH
                && (state.callGraphEntry == null || state.callGraphEntry.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a call graph entry from Diagram menu.");
            return;
        }
        status.setText("Rendering " + kind.getDisplayName() + " ...");
        loadProgress.setString("Rendering...");
        loadProgress.setIndeterminate(true);
        loadProgress.setVisible(true);
        final String entry = state.sequenceEntry;
        final String activity = state.activityEntry;
        final String callGraph = state.callGraphEntry;
        final String layoutKey = state.currentLayoutKey;
        final String navigationKey = state.currentNavigationKey;
        final DiagramScope scope = state.currentScope;
        new SwingWorker<RenderResult, Void>() {
            private Throwable error;
            // レンダ前にキャプチャしておく puml。例外発生時も PlantUML Source タブに
            // 残せるようにするための退避先。
            private String pumlOnError;

            @Override
            protected RenderResult doInBackground() {
                try {
                    boolean wantLinks = (kind == DiagramKind.CLASS
                            || kind == DiagramKind.INHERITANCE);
                    DiagramRequest req;
                    if (kind == DiagramKind.SEQUENCE && entry != null) {
                        req = controller.buildSequenceRequest(entry);
                    } else if (kind == DiagramKind.ACTIVITY && activity != null) {
                        req = controller.buildActivityRequest(activity);
                    } else if (kind == DiagramKind.CALLGRAPH && callGraph != null) {
                        req = controller.buildCallGraphRequest(callGraph);
                    } else if (kind == DiagramKind.LAYOUT && layoutKey != null) {
                        req = DiagramRequest.forLayout(layoutKey, true);
                    } else if (kind == DiagramKind.NAVIGATION && navigationKey != null) {
                        req = DiagramRequest.forNavigationGraph(navigationKey, true);
                    } else {
                        req = new DiagramRequest(kind, null, null, true, scope, wantLinks);
                    }
                    String puml = DiagramService.generatePuml(req, cache);
                    pumlOnError = puml;
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
                loadProgress.setVisible(false);
                loadProgress.setIndeterminate(false);
                loadProgress.setString(null);
                if (error != null) {
                    // モーダル ダイアログは出さず、ステータス バー + PlantUML Source タブで
                    // 通知する。Smetana のフォールバック SVG (Batik が読めない巨大 base64) を
                    // ダイアログに出すと使い物にならないため、UX 優先で握り潰す。
                    previewPanel.setSvgGraphicsNode(null, 0, 0);
                    if (pumlOnError != null) {
                        sourcePanel.setText(pumlOnError);
                    }
                    String reason;
                    if (error instanceof padtools.core.formats.uml.PlantUmlRenderFailedException) {
                        reason = "PlantUML layout error (Smetana)";
                    } else {
                        String m = error.getMessage();
                        reason = m == null ? error.getClass().getSimpleName()
                                : truncateForStatus(m, 120);
                    }
                    status.setText(kind.getDisplayName()
                            + ": rendering failed -- " + reason
                            + ". See 'PlantUML Source' tab.");
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
                    state.currentPuml = r.puml;
                    state.currentSvgXml = r.svg.getSvgXml();
                    previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                            r.svg.getWidth(), r.svg.getHeight());
                    previewPanel.setLinkAreas(r.svg.getLinkAreas());
                    previewPanel.setTextItems(r.svg.getTextItems());
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

    private void chooseAndExport() {
        if (state.currentPuml == null || state.currentPuml.isEmpty()) {
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
                pngImage = PlantUmlImageRenderer.toBufferedImage(state.currentPuml);
            }
            UmlExporter.export(fmt, chosen, state.currentPuml, pngImage);
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

    /**
     * プロジェクトロード成功時: リポジトリに登録し、保存済み設定があれば復元する。
     * 初回ロードは設定がないため何も変わらない。
     */
    private void persistAndRestoreProjectSettings(File root) {
        settingsPersistor.restoreAndPersist(root);
    }

    private void saveCurrentProjectSettings() {
        settingsPersistor.saveCurrentProjectSettings(currentProjectRoot);
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
