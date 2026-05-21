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
import java.util.EnumSet;
import java.util.List;

import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import padtools.core.formats.uml.JavaClassInfo;

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

        refreshTimer.setRepeats(false);
        previewPanel.setZoomChangeListener(this::updateZoomLabel);
        previewPanel.setOnLinkPopup(this::onPreviewLinkPopup);
        previewPanel.setOnLinkClick(this::onPreviewLinkClick);
        previewPanel.setCopyFeedbackListener(msg -> status.setText(msg));
        treePanel.setOnMethodSelected(this::onTreeMethodSelected);
        treePanel.setOnActivityMethodSelected(this::onTreeActivityMethodSelected);
        treePanel.setOnClassSelected(this::onTreeClassSelected);
        treePanel.setOnPackageSelected(this::onTreePackageSelected);
        treePanel.setOnModuleSelected(this::onTreeModuleSelected);
        treePanel.setOnManifestSelected(this::onTreeManifestSelected);
        treePanel.setOnComponentSelected(this::onTreeComponentSelected);
        treePanel.setOnOpenInNewTab(this::onTreeOpenInNewTab);

        loadProgress.setStringPainted(true);
        loadProgress.setVisible(false);
        loadProgress.setPreferredSize(new Dimension(200, 16));

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
        mcb.openEntitySearch = this::openEntitySearch;
        mcb.pickSequenceEntry = this::pickSequenceEntry;
        mcb.openParticipantFilterDialog = this::openParticipantFilterDialog;
        mcb.clearSequenceParticipants = () -> {
            if (!state.sequenceHiddenParticipants.isEmpty()) {
                state.sequenceHiddenParticipants.clear();
                status.setText("Cleared sequence participant filter.");
                refreshDiagram();
            }
        };
        mcb.pickActivityEntry = this::pickActivityEntry;
        mcb.pickLayoutFile = this::pickLayoutFile;
        mcb.pickNavigationGraph = this::pickNavigationGraph;
        mcb.applyPreset = this::applyPreset;
        mcb.openScopeDialog = this::openScopeDialog;
        mcb.clearScope = () -> { state.currentScope = null; refreshDiagram(); };
        mcb.selectDiagramKindFromMenu = k -> { currentKind = k; refreshDiagram(); };
        mcb.syncDiagramToggle = this::syncDiagramToggle;
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

        // 右側: 1 層のフラットタブバー
        // [Home] [動的タブ…] [Manifest] [Impact] [References]
        mainTabs = new JTabbedPane(JTabbedPane.TOP);

        // Home タブ: 共有の previewPanel / sourcePanel を JSplitPane で上下に配置
        JSplitPane homeSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(previewPanel), sourcePanel);
        homeSplit.setResizeWeight(0.85);
        mainTabs.addTab("Home", homeSplit);

        // ユーティリティタブ (固定・末尾 3 本)
        mainTabs.addTab("Manifest", manifestSummaryPanel);
        mainTabs.addTab("Impact", impactPanel);
        mainTabs.addTab("References", referencesPanel);

        // 動的タブマネージャ (fixedSuffix=3 で Manifest/Impact/References の手前に挿入)
        tabPane = new DiagramTabPane(mainTabs, 3, cache, status::setText);

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

        ToolBarBuilder.Callbacks tcb = new ToolBarBuilder.Callbacks();
        tcb.chooseProject = this::chooseProject;
        tcb.chooseAndExport = this::chooseAndExport;
        tcb.refreshDiagram = this::refreshDiagram;
        tcb.openEntitySearch = this::openEntitySearch;
        tcb.selectDiagramKind = this::selectDiagramKind;
        ToolBarBuilder.Result toolBarResult =
                new ToolBarBuilder(DiagramKind.CLASS, tcb).build();
        diagramToggles = toolBarResult.diagramToggles;
        add(toolBarResult.toolBarPanel, BorderLayout.NORTH);
        add(buildStatusBar(), BorderLayout.SOUTH);

        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1200;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 800;
        setPreferredSize(new Dimension(w, h));
        pack();
        restoreWindowLocation(setting);

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

        if (initialProject != null && initialProject.isDirectory()) {
            SwingUtilities.invokeLater(() -> loadProject(initialProject));
        }
    }

    private ProjectLoader projectLoader;
    private ProjectSettingsPersistor settingsPersistor;

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

    /**
     * ノード選択に応じてツールバーの図種ボタンを有効/無効化する。
     *
     * <p>{@code allowed} に含まれない図種のボタンは押下不可になる。
     * 現在の選択図種が無効になった場合、{@code allowed} の先頭図種に自動切替する。</p>
     */
    private void updateAvailableDiagrams(EnumSet<DiagramKind> allowed) {
        for (java.util.Map.Entry<DiagramKind, JToggleButton> e : diagramToggles.entrySet()) {
            e.getValue().setVisible(allowed.contains(e.getKey()));
        }
        if (!allowed.contains(currentKind)) {
            DiagramKind fallback = allowed.iterator().next();
            currentKind = fallback;
            JToggleButton btn = diagramToggles.get(fallback);
            if (btn != null) {
                btn.setSelected(true);
            }
            JRadioButtonMenuItem item = diagramItems.get(fallback);
            if (item != null) {
                item.setSelected(true);
            }
            refreshDiagram();
        }
    }

    /** ツリーノードの左クリック後に Home タブ (index 0) を前面に出す。 */
    private void showHomeTab() {
        if (mainTabs != null && mainTabs.getSelectedIndex() != 0) {
            mainTabs.setSelectedIndex(0);
        }
    }

    /**
     * ツールバーの図種ボタンが押されたときのハンドラ。
     * 図種に応じて必要な追加入力ダイアログを開く (シーケンス起点未指定など)。
     */
    private void selectDiagramKind(DiagramKind kind) {
        currentKind = kind;
        JRadioButtonMenuItem item = diagramItems.get(kind);
        if (item != null) {
            item.setSelected(true);
        }
        // SEQUENCE/ACTIVITY/LAYOUT は追加入力が必要。未指定なら入力ダイアログを誘導する。
        if (kind == DiagramKind.SEQUENCE
                && (state.sequenceEntry == null || state.sequenceEntry.isEmpty())) {
            if (cache.isLoaded()) {
                pickSequenceEntry();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.ACTIVITY
                && (state.activityEntry == null || state.activityEntry.isEmpty())) {
            if (cache.isLoaded()) {
                pickActivityEntry();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.CALLGRAPH
                && (state.callGraphEntry == null || state.callGraphEntry.isEmpty())) {
            if (cache.isLoaded()) {
                pickCallGraphEntry();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.LAYOUT
                && (state.currentLayoutKey == null || state.currentLayoutKey.isEmpty())) {
            if (cache.isLoaded()) {
                pickLayoutFile();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.NAVIGATION
                && (state.currentNavigationKey == null || state.currentNavigationKey.isEmpty())) {
            if (cache.isLoaded()) {
                pickNavigationGraph();
            } else {
                refreshDiagram();
            }
            return;
        }
        refreshDiagram();
    }

    /**
     * ツールバーのトグルボタン側で現在の図種を反映する。メニュー (ラジオボタン) と
     * 双方向に同期するため、各 onTree*Selected / drillDown 等で
     * {@link #diagramItems} を更新する場所で同時に呼ぶ。
     */
    private void syncDiagramToggle(DiagramKind kind) {
        JToggleButton b = diagramToggles.get(kind);
        if (b != null && !b.isSelected()) {
            b.setSelected(true);
        }
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

    private void onTreePackageSelected(String pkg) {
        if (pkg == null || pkg.isEmpty() || "(default)".equals(pkg)) {
            return;
        }
        state.currentScope = DiagramScope.builder().includePackage(pkg).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: package " + pkg);
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_PACKAGE);
        showHomeTab();
        refreshDiagram();
    }

    /**
     * 左ペインのツリーでクラスノードが選択された際のハンドラ。
     * クラス図モードへ切り替え、当該クラスを seed として 1 ホップ近傍に絞ったスコープで再描画する。
     */
    private void onTreeClassSelected(JavaClassInfo cls) {
        if (cls == null) {
            updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_ALL);
            return;
        }
        String fqn = cls.getQualifiedName();
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        state.currentScope = DiagramScope.builder()
                .seed(fqn)
                .neighborHops(1)
                .build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: class " + cls.getSimpleName() + " (+1 hop)");
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_JAVA_TYPE);
        showHomeTab();
        refreshDiagram();
    }

    /**
     * 左ペインのツリーでモジュールノードが選択された際のハンドラ。
     * クラス図モードへ切り替え、当該モジュールに含まれるクラスだけに絞って再描画する。
     * <p>"(other)" のようなプレースホルダ名は無視する。</p>
     */
    private void onTreeModuleSelected(String module) {
        if (module == null || module.isEmpty() || "(other)".equals(module)) {
            return;
        }
        state.currentScope = DiagramScope.builder().includeModule(module).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: module " + module);
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_MODULE);
        showHomeTab();
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
                List.copyOf(packages), List.copyOf(modules), state.currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        if (picked != null) {
            state.currentScope = picked.isEmpty() ? null : picked;
            refreshDiagram();
        }
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
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_ANDROID);
        showHomeTab();
        refreshDiagram();
    }


    /**
     * 左ペインのツリーでメソッドが選択された際のハンドラ。
     * シーケンス図モードへ切り替え、かつ Activity / CallGraph 起点も同じメソッドに同期する。
     * これにより、ツールバーの Sequence / Activity / Call Graph ボタンを押すだけで
     * ダイアログを開かずに切り替えられる。
     */
    private void onTreeMethodSelected(ProjectTreePanel.MethodSelection sel) {
        if (sel == null) {
            return;
        }
        String entry = sel.getEntry();
        state.activityEntry = entry;
        state.callGraphEntry = entry;
        switchToSequenceDiagram(entry);
    }

    /**
     * sequence / activity / callgraph の 3 エントリを同じメソッドに揃える。
     * これによりツールバーボタンでどの図種へ切り替えても再入力ダイアログが出ない。
     */
    private void setAllMethodEntries(String entry) {
        state.setAllMethodEntries(entry);
    }

    /** {@code Class.method} 起点をセットしてシーケンス図モードへ切り替える。 */
    private void switchToSequenceDiagram(String entry) {
        state.sequenceEntry = entry;
        currentKind = DiagramKind.SEQUENCE;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram();
    }

    /**
     * 左ペインで中クリックされたノードを「新しいタブ」として開くハンドラ。
     * 機能 2 (動的タブ) で {@link DiagramTabPane} に委譲するが、現状は単に
     * その図種に切り替える (タブ追加は別実装)。
     */
    private void onTreeOpenInNewTab(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        if (tabPane != null) {
            tabPane.addOrFocusTab(req);
            return;
        }
        applyOpenRequest(req);
    }

    /** タブ未配線時の挙動: 現在ビューを差し替える。 */
    private void applyOpenRequest(TreeNodeOpenRequest req) {
        switch (req.target) {
            case METHOD:
                String entry = req.classInfo.getSimpleName() + "." + req.methodInfo.getName();
                if (req.kind == DiagramKind.ACTIVITY) {
                    switchToActivityDiagram(entry);
                } else {
                    switchToSequenceDiagram(entry);
                }
                break;
            case CLASS:
                onTreeClassSelected(req.classInfo);
                break;
            case PACKAGE:
                onTreePackageSelected(req.name);
                break;
            case MODULE:
                onTreeModuleSelected(req.name);
                break;
            default:
                break;
        }
    }

    /**
     * 左ペインのアクティビティ図リーフが選択された際のハンドラ (後方互換)。
     * 現在はツリーからこのハンドラを呼ぶ経路はないが、中クリック等の内部処理で
     * 引き続き利用するため残している。
     */
    private void onTreeActivityMethodSelected(ProjectTreePanel.MethodSelection sel) {
        if (sel == null) {
            return;
        }
        String entry = sel.getEntry();
        state.sequenceEntry = entry;
        state.callGraphEntry = entry;
        switchToActivityDiagram(entry);
    }

    /** {@code Class.method} 起点をセットしてアクティビティ図モードへ切り替える。 */
    private void switchToActivityDiagram(String entry) {
        state.activityEntry = entry;
        currentKind = DiagramKind.ACTIVITY;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.ACTIVITY);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram();
    }

    /** {@code Class.method} 起点をセットしてコールグラフモードへ切り替える。 */
    private void switchToCallGraphDiagram(String entry) {
        state.callGraphEntry = entry;
        currentKind = DiagramKind.CALLGRAPH;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CALLGRAPH);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram();
    }

    /**
     * クラス図プレビュー上で左クリックされたとき:
     * - {@code padtools://method/<FQN>#<method>} リンクならシーケンス/アクティビティ図選択メニュー
     * - {@code padtools://class/<FQN>} リンクならドリルダウン
     */
    private void onPreviewLinkClick(LinkArea link, MouseEvent event) {
        if (link == null) {
            return;
        }
        String href = link.getHref();
        if (href != null && href.startsWith("padtools://method/")) {
            showMethodDiagramMenu(href, event);
            return;
        }
        String fqn = parseClassFqnFromHref(href);
        if (fqn == null) {
            return;
        }
        drillDownToClass(fqn);
    }

    /**
     * メソッドリンク ({@code padtools://method/<FQN>#<methodName>}) がクリックされたとき、
     * シーケンス図またはアクティビティ図へ遷移するサブメニューを表示する。
     */
    private void showMethodDiagramMenu(String href, MouseEvent event) {
        String path = href.substring("padtools://method/".length());
        int hash = path.lastIndexOf('#');
        if (hash < 0) {
            return;
        }
        String classFqn = path.substring(0, hash);
        String methodName = path.substring(hash + 1);
        if (classFqn.isEmpty() || methodName.isEmpty()) {
            return;
        }
        String simpleName = extractSimpleClass(classFqn);
        String entry = simpleName + "." + methodName;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem seqItem = new JMenuItem("Sequence Diagram");
        seqItem.addActionListener(e -> {
            setAllMethodEntries(entry);
            switchToSequenceDiagram(entry);
            treePanel.selectMethodNode(classFqn, methodName);
        });
        menu.add(seqItem);
        JMenuItem actItem = new JMenuItem("Activity Diagram");
        actItem.addActionListener(e -> {
            setAllMethodEntries(entry);
            switchToActivityDiagram(entry);
            treePanel.selectMethodNode(classFqn, methodName);
        });
        menu.add(actItem);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    /** 指定された FQN を seed として 1 ホップ近傍の詳細クラス図に遷移する。 */
    private void drillDownToClass(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        DiagramScope.Builder b = DiagramScope.builder()
                .seed(fqn).neighborHops(1);
        DiagramPreset.DETAILED.applyTo(b);
        state.currentScope = b.build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Drill-down: " + fqn);
        treePanel.selectClassNode(fqn);
        refreshDiagram();
    }

    /** href 文字列から {@code padtools://class/<FQN>} の FQN 部分を取り出す。 */
    private static String parseClassFqnFromHref(String href) {
        if (href == null) {
            return null;
        }
        final String prefix = "padtools://class/";
        if (href.startsWith(prefix)) {
            String s = href.substring(prefix.length()).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
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

    /**
     * クラス・メソッド・フィールドの横断検索ダイアログを開き、結果に応じて
     * クラス図 / シーケンス図に切り替える。
     *
     * <ul>
     *   <li>CLASS 選択 → クラス図モードへ切り替え、当該クラスを seed に 1 ホップでスコープ</li>
     *   <li>METHOD 選択 → 当該メソッドを起点とするシーケンス図モード</li>
     *   <li>FIELD 選択 → 所属クラスを seed に 1 ホップしたクラス図 (フィールド型まで含む)</li>
     * </ul>
     */
    private void openEntitySearch() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        EntitySearchDialog dlg = new EntitySearchDialog(this, cache.getClasses());
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No entities found in this project.",
                    "Search", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        EntitySearchDialog.Entry result = dlg.getResult();
        if (result == null) {
            return;
        }
        if (dlg.isDrillDownRequested()) {
            // Drill-down ボタンを押された場合は kind に関係なく ownerQn のクラスへ
            // DETAILED プリセットで遷移する。
            drillDownToClass(result.ownerQn);
            return;
        }
        switch (result.kind) {
            case CLASS:
                scopeToClass(result.ownerQn, result.simpleName);
                break;
            case METHOD: {
                String simple = extractSimpleClass(result.ownerQn);
                String methodEntry = simple + "." + result.simpleName;
                String ownerFqn = result.ownerQn;
                String methodName = result.simpleName;
                JPopupMenu menu = new JPopupMenu();
                JMenuItem seqItem = new JMenuItem("Sequence Diagram");
                seqItem.addActionListener(e -> {
                    setAllMethodEntries(methodEntry);
                    switchToSequenceDiagram(methodEntry);
                    treePanel.selectMethodNode(ownerFqn, methodName);
                });
                menu.add(seqItem);
                JMenuItem actItem = new JMenuItem("Activity Diagram");
                actItem.addActionListener(e -> {
                    setAllMethodEntries(methodEntry);
                    switchToActivityDiagram(methodEntry);
                    treePanel.selectMethodNode(ownerFqn, methodName);
                });
                menu.add(actItem);
                menu.show(this, getWidth() / 2, getHeight() / 2);
                break;
            }
            case FIELD:
                scopeToClass(result.ownerQn,
                        extractSimpleClass(result.ownerQn) + "." + result.simpleName);
                break;
            default:
                break;
        }
    }

    /** クラス図モードへ切り替え、FQN を seed として 1 ホップ近傍でスコープする。 */
    private void scopeToClass(String fqn, String statusLabel) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        state.currentScope = DiagramScope.builder().seed(fqn).neighborHops(1).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: " + statusLabel + " (+1 hop)");
        treePanel.selectClassNode(fqn);
        refreshDiagram();
    }

    /**
     * "SimpleClass.method" 形式のエントリからクラスの FQN を検索し、
     * 左ツリーパネルの対応するメソッドノードを選択する。
     */
    private void syncTreeToMethodByEntry(String entry) {
        if (entry == null || !cache.isLoaded()) {
            return;
        }
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String simpleName = entry.substring(0, dot);
        String methodName = entry.substring(dot + 1);
        for (padtools.core.formats.uml.JavaClassInfo c : cache.getClasses()) {
            if (simpleName.equals(c.getSimpleName())) {
                treePanel.selectMethodNode(c.getQualifiedName(), methodName);
                return;
            }
        }
    }

    private static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) {
            return "";
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
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
            // 起点が変わったら participant フィルタはリセットする
            // (旧起点の participant 名は新図に存在しない可能性があるため)
            state.sequenceHiddenParticipants.clear();
            state.sequenceEntry = picked;
            currentKind = DiagramKind.SEQUENCE;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
            if (item != null) {
                item.setSelected(true);
            }
            syncTreeToMethodByEntry(picked);
            refreshDiagram();
        }
    }

    /**
     * 現在のシーケンス図起点に登場する participant をフィルタダイアログで選択できるようにする。
     * 選択結果は {@link #state.sequenceHiddenParticipants} に保存され、再描画時の
     * {@link DiagramRequest#getSequenceHiddenParticipants()} に渡される。
     */
    private void openParticipantFilterDialog() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (state.sequenceEntry == null || state.sequenceEntry.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Choose a sequence entry first (Diagram → Choose Sequence Entry...).",
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dot = state.sequenceEntry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String cls = state.sequenceEntry.substring(0, dot);
        String method = state.sequenceEntry.substring(dot + 1);
        java.util.Set<String> all =
                padtools.core.formats.uml.PlantUmlSequenceDiagram.collectParticipants(
                        cache.getClasses(), cls, method, null);
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No participants found for " + state.sequenceEntry,
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> picked = SequenceParticipantFilterDialog.show(
                this, state.sequenceEntry, all, state.sequenceHiddenParticipants);
        if (picked != null) {
            state.sequenceHiddenParticipants.clear();
            state.sequenceHiddenParticipants.addAll(picked);
            int total = all.size();
            int hidden = state.sequenceHiddenParticipants.size();
            status.setText("Sequence filter: showing " + (total - hidden) + "/" + total
                    + " participants");
            refreshDiagram();
        }
    }

    /**
     * アクティビティ図用にメソッドを選択する。SequenceEntryDialog を流用し、
     * タイトルだけ「Select activity method」に差し替える。
     */
    private void pickActivityEntry() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(this, cache.getClasses());
        dlg.setTitle("Select activity method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No methods found in this project.",
                    "Activity diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            state.activityEntry = picked;
            currentKind = DiagramKind.ACTIVITY;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.ACTIVITY);
            if (item != null) {
                item.setSelected(true);
            }
            syncTreeToMethodByEntry(picked);
            refreshDiagram();
        }
    }

    private void pickCallGraphEntry() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(this, cache.getClasses());
        dlg.setTitle("Select call graph entry method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No methods found in this project.",
                    "Call graph", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            state.callGraphEntry = picked;
            currentKind = DiagramKind.CALLGRAPH;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CALLGRAPH);
            if (item != null) {
                item.setSelected(true);
            }
            refreshDiagram();
        }
    }

    private void pickLayoutFile() {
        String picked = LayoutFileChooserDialog.chooseLayoutKey(this, cache);
        if (picked == null) {
            return;
        }
        state.currentLayoutKey = picked;
        currentKind = DiagramKind.LAYOUT;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.LAYOUT);
        if (item != null) {
            item.setSelected(true);
        }
        refreshDiagram();
    }

    private void pickNavigationGraph() {
        String picked = NavigationFileChooserDialog.chooseNavigationKey(this, cache);
        if (picked == null) {
            return;
        }
        state.currentNavigationKey = picked;
        currentKind = DiagramKind.NAVIGATION;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.NAVIGATION);
        if (item != null) {
            item.setSelected(true);
        }
        refreshDiagram();
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
                        req = buildSequenceRequest(entry);
                    } else if (kind == DiagramKind.ACTIVITY && activity != null) {
                        req = buildActivityRequest(activity);
                    } else if (kind == DiagramKind.CALLGRAPH && callGraph != null) {
                        req = buildCallGraphRequest(callGraph);
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

    private DiagramRequest buildSequenceRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Sequence entry must be in 'Class.method' format: " + entry);
        }
        // 隠す participant の集合をスナップショットして DiagramRequest に渡す
        java.util.Set<String> hidden = state.sequenceHiddenParticipants.isEmpty()
                ? null : new java.util.LinkedHashSet<>(state.sequenceHiddenParticipants);
        return new DiagramRequest(DiagramKind.SEQUENCE,
                entry.substring(0, dot), entry.substring(dot + 1), true,
                null, false, null, hidden);
    }

    private DiagramRequest buildActivityRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Activity entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forActivity(
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }

    private DiagramRequest buildCallGraphRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Call graph entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forCallGraph(
                entry.substring(0, dot), entry.substring(dot + 1), true);
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
