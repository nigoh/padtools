package padtools.app.uml;

import padtools.Main;
import padtools.Setting;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.uml.DiagramStyle;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.util.CancelToken;
import padtools.util.ErrorListener;
import padtools.util.ProgressListener;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
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
import javax.swing.JToolBar;
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
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;

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
    /** ツールバー上の「図種切替」トグルボタン。メニュー側ラジオと選択状態を同期する。 */
    private final java.util.EnumMap<DiagramKind, JToggleButton> diagramToggles
            = new java.util.EnumMap<>(DiagramKind.class);
    private final ButtonGroup diagramToolbarGroup = new ButtonGroup();
    private final ButtonGroup themeGroup = new ButtonGroup();
    private final java.util.Map<String, JRadioButtonMenuItem> themeItems
            = new java.util.LinkedHashMap<>();

    private final Timer refreshTimer = new Timer(300, e -> refreshDiagramNow());

    /** 動的タブ管理 (機能 2)。null の場合はタブ機能未配線。 */
    private DiagramTabPane tabPane;
    /** 右側の最上位タブ (Preview / Source / Manifest / Tabs)。 */
    private JTabbedPane rightTabs;
    /** rightTabs 内での "Tabs" タブのインデックス。-1 なら未配線。 */
    private int dynamicTabsIndex = -1;

    private DiagramKind currentKind = DiagramKind.CLASS;
    private String currentPuml;
    /** 現在選択されているシーケンス図起点 ({@code Class.method})。null なら未設定。 */
    private String sequenceEntry;
    /** 現在選択されているアクティビティ図起点 ({@code Class.method})。null なら未設定。 */
    private String activityEntry;
    /** 現在選択されている Layout 図のキー ({@link AndroidLayoutInfo#getKey()})。null なら未設定。 */
    private String currentLayoutKey;
    /** クラス図の現在の絞り込みスコープ。null なら全件表示。 */
    private DiagramScope currentScope;
    /** ドリルダウン履歴 (戻る用)。null スコープは {@link #SCOPE_NULL_MARKER} で保持。 */
    private final java.util.Deque<DiagramScope> scopeHistory = new java.util.ArrayDeque<>();
    /** Back の対になる進む履歴。Back を実行したときに現在スコープがここに積まれる。 */
    private final java.util.Deque<DiagramScope> forwardHistory = new java.util.ArrayDeque<>();
    /** null スコープを Deque に保持するためのセンチネル (Deque は null 要素不可)。 */
    private static final DiagramScope SCOPE_NULL_MARKER = DiagramScope.builder().build();
    /** 進行中のロード処理のキャンセル用 (null ならロード中ではない)。 */
    private CancelToken loadingCancelToken;
    /**
     * シーケンス図で隠す participant 名 (空ならフィルタ無し)。
     * {@link SequenceParticipantFilterDialog} で設定する。
     */
    private final java.util.Set<String> sequenceHiddenParticipants = new java.util.LinkedHashSet<>();

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

        setJMenuBar(buildMenuBar());

        // 右側: プレビュー (ベクター SVG) と PlantUML ソース / Manifest Summary のタブ
        // 動的タブ機能 (機能 2) は "Tabs" タブ内に DiagramTabPane を配置する。
        // この設計だと既存ビュー (Preview/Source/Manifest) には触らずに済むため、
        // メソッド差し替えやズーム連動などのリグレッションを最小化できる。
        tabPane = new DiagramTabPane(cache, status::setText,
                previewPanel, sourcePanel, e -> chooseAndExport());
        rightTabs = new JTabbedPane();
        rightTabs.addTab("Preview", tabPane);
        rightTabs.addTab("Manifest Summary", manifestSummaryPanel);
        dynamicTabsIndex = rightTabs.indexOfComponent(tabPane);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treePanel, rightTabs);
        split.setResizeWeight(0.22);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);

        add(buildToolBarPanel(), BorderLayout.NORTH);
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
        JMenuItem perFolder = new JMenuItem("Export Class Diagrams Per Folder...");
        perFolder.addActionListener(e -> exportClassDiagramsPerFolder());
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
        m.add(perFolder);
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
            // メニュー側で選択された時に、ツールバーのトグルボタンも同じ状態に揃える。
            // ActionListener ではなく PropertyChangeListener にしておくと、API 経由で
            // setSelected された場合 (左ペインや drill-down) も同期される。
            final DiagramKind kind = k;
            item.addItemListener(ev -> {
                if (((AbstractButton) ev.getSource()).isSelected()) {
                    syncDiagramToggle(kind);
                }
            });
            diagramGroup.add(item);
            diagramItems.put(k, item);
            m.add(item);
        }
        m.addSeparator();
        JMenuItem search = new JMenuItem("Search Entities...");
        // Ctrl+F は Zoom to Fit が既に使っているため Ctrl+Shift+F に割り当て
        search.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                MENU_MASK | InputEvent.SHIFT_DOWN_MASK));
        search.addActionListener(e -> openEntitySearch());
        m.add(search);
        JMenuItem pickEntry = new JMenuItem("Choose Sequence Entry...");
        pickEntry.addActionListener(e -> pickSequenceEntry());
        m.add(pickEntry);
        JMenuItem filterParticipants = new JMenuItem("Filter Sequence Participants...");
        filterParticipants.addActionListener(e -> openParticipantFilterDialog());
        m.add(filterParticipants);
        JMenuItem clearParticipantFilter =
                new JMenuItem("Clear Sequence Participant Filter");
        clearParticipantFilter.addActionListener(e -> {
            if (!sequenceHiddenParticipants.isEmpty()) {
                sequenceHiddenParticipants.clear();
                status.setText("Cleared sequence participant filter.");
                refreshDiagram();
            }
        });
        m.add(clearParticipantFilter);
        JMenuItem pickActivity = new JMenuItem("Choose Activity Method...");
        pickActivity.addActionListener(e -> pickActivityEntry());
        m.add(pickActivity);
        JMenuItem pickLayout = new JMenuItem("Choose Layout File...");
        pickLayout.addActionListener(e -> pickLayoutFile());
        m.add(pickLayout);
        m.addSeparator();
        m.add(buildPresetSubMenu());
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

    /** クラス図の表示密度プリセット ({@link DiagramPreset}) を切り替えるサブメニュー。 */
    private JMenu buildPresetSubMenu() {
        JMenu sub = new JMenu("Preset");
        sub.setToolTipText("Switch class diagram density "
                + "(Minimal / Balanced / Detailed)");
        int seq = 1;
        for (DiagramPreset p : DiagramPreset.values()) {
            if (p == DiagramPreset.CUSTOM) {
                continue;
            }
            JMenuItem mi = new JMenuItem(p.getDisplayName());
            // Ctrl+1 = Minimal, Ctrl+2 = Balanced, Ctrl+3 = Detailed
            int keyCode;
            switch (seq) {
                case 1: keyCode = KeyEvent.VK_1; break;
                case 2: keyCode = KeyEvent.VK_2; break;
                case 3: keyCode = KeyEvent.VK_3; break;
                default: keyCode = KeyEvent.VK_UNDEFINED;
            }
            if (keyCode != KeyEvent.VK_UNDEFINED) {
                mi.setAccelerator(KeyStroke.getKeyStroke(keyCode, MENU_MASK));
            }
            seq++;
            final DiagramPreset preset = p;
            mi.addActionListener(e -> applyPreset(preset));
            sub.add(mi);
        }
        return sub;
    }

    /**
     * 指定された {@link DiagramPreset} を現在のスコープに適用して再描画する。
     * 既存スコープのフィルタ設定 (パッケージ・seed 等) は維持し、表示密度関連の
     * 項目だけプリセットで書き換える。
     */
    private void applyPreset(DiagramPreset p) {
        DiagramScope.Builder b = currentScope != null
                ? currentScope.toBuilder()
                : DiagramScope.builder();
        p.applyTo(b);
        currentScope = b.build();
        status.setText("Preset: " + p.getDisplayName());
        refreshDiagram();
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
        m.addSeparator();
        JMenuItem back = new JMenuItem("Back");
        back.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,
                MENU_MASK | InputEvent.ALT_DOWN_MASK));
        back.addActionListener(e -> popScopeHistory());
        m.add(back);
        JMenuItem forward = new JMenuItem("Forward");
        forward.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,
                MENU_MASK | InputEvent.ALT_DOWN_MASK));
        forward.addActionListener(e -> popForwardHistory());
        m.add(forward);
        return m;
    }

    /** Deque に保存可能な値に変換する (null → センチネル)。 */
    private static DiagramScope encodeScope(DiagramScope scope) {
        return scope == null ? SCOPE_NULL_MARKER : scope;
    }

    /** Deque から取り出した値を元のスコープ (null 可) に戻す。 */
    private static DiagramScope decodeScope(DiagramScope scope) {
        return scope == SCOPE_NULL_MARKER ? null : scope;
    }

    /** ドリルダウン履歴を 1 段戻す。空のときは無視。 */
    private void popScopeHistory() {
        if (scopeHistory.isEmpty()) {
            status.setText("No drill-down history.");
            return;
        }
        forwardHistory.push(encodeScope(currentScope));
        currentScope = decodeScope(scopeHistory.pop());
        status.setText("Back to previous scope.");
        refreshDiagram();
    }

    /** Back で積まれた forward 履歴を 1 段進める。空のときは無視。 */
    private void popForwardHistory() {
        if (forwardHistory.isEmpty()) {
            status.setText("No forward history.");
            return;
        }
        scopeHistory.push(encodeScope(currentScope));
        currentScope = decodeScope(forwardHistory.pop());
        status.setText("Forward to next scope.");
        refreshDiagram();
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
        StyleSettingsDialog.Result edited = StyleSettingsDialog.showDialog(
                this, PlantUmlRenderer.getStyle(), curShow, curStyle,
                curPlacement, curQualify, curClass);
        if (edited != null) {
            applyStyleSettings(edited);
        }
    }

    /** Style ダイアログ結果 (Style + シーケンス図設定 + クラス図設定) を反映する。 */
    private void applyStyleSettings(StyleSettingsDialog.Result r) {
        try {
            padtools.Setting setting = Main.getSetting();
            if (setting != null) {
                setting.setSequenceShowComments(r.sequenceShowComments);
                setting.setSequenceCommentStyle(r.sequenceCommentStyle.name());
                setting.setSequenceCommentPlacement(r.sequenceCommentPlacement.name());
                setting.setSequenceQualifyMethodNames(r.sequenceQualifyMethodNames);
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
        JMenuItem usage = new JMenuItem("Usage");
        usage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        usage.addActionListener(e -> showUsageDialog());
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "PadTools UML\n\n"
                        + "Java + Android + Gradle UML diagram generator.\n"
                        + "Bundled PlantUML for rendering.",
                "About PadTools UML",
                JOptionPane.INFORMATION_MESSAGE));
        m.add(usage);
        m.addSeparator();
        m.add(about);
        return m;
    }

    /** 使い方ダイアログを表示する (Help > Usage / F1)。 */
    private void showUsageDialog() {
        String mod = MENU_MASK == InputEvent.META_DOWN_MASK ? "Cmd" : "Ctrl";
        String text =
                "PadTools UML — 使い方 (Usage)\n"
                        + "\n"
                        + "■ プロジェクトを開く (Open Project)\n"
                        + "  File > Open Project... (" + mod + "+O)\n"
                        + "    Gradle / Java プロジェクトのルートディレクトリを指定すると、\n"
                        + "    左ペインのツリーにモジュール・パッケージ・クラスが表示されます。\n"
                        + "\n"
                        + "■ 図種を切り替える (Diagram)\n"
                        + "  Diagram メニューから Class / Sequence / Activity / Common / Layout などを選択。\n"
                        + "  ウィンドウ上部のツールバーのトグルボタンでも同じ切替ができます。\n"
                        + "  Common (共通クラス図) は他クラスから参照される回数 (fan-in) が多い\n"
                        + "  「使い回されているクラス」上位 N 件をハイライト表示します。\n"
                        + "  シーケンス図やアクティビティ図は起点メソッドの指定が必要です\n"
                        + "  (Diagram > Choose Sequence Entry... / Choose Activity Method...)。\n"
                        + "\n"
                        + "■ 左ペインのツリー操作\n"
                        + "  - クラスやメソッドを選択すると、対応する図に絞り込み表示します。\n"
                        + "  - パッケージ / モジュール選択でスコープを切り替えられます。\n"
                        + "\n"
                        + "■ プレビュー (右ペイン) の操作\n"
                        + "  - 左ドラッグ / 中ボタンドラッグ: パン (画面移動)\n"
                        + "  - " + mod + " + マウスホイール: ポインタ位置を基点にズームイン/アウト\n"
                        + "  - マウスホイールのみ: 縦スクロール\n"
                        + "  - View > Zoom In / Out / 100% / Fit (" + mod + "+= / " + mod + "+- / " + mod + "+0 / " + mod + "+F)\n"
                        + "\n"
                        + "■ ドリルダウン (図中のクリック可能要素)\n"
                        + "  - 図中のクラス名やメソッド名のうち、人差し指 (👆) アイコンが\n"
                        + "    表示される箇所はクリックで詳細表示に切り替わります。\n"
                        + "    ※ アイコンが出ない箇所はクリック対象ではありません。\n"
                        + "  - 右クリックでポップアップメニュー (関連図への遷移など)。\n"
                        + "  - View > Back (Alt+←) で 1 段戻る。\n"
                        + "\n"
                        + "■ 絞り込み / 検索\n"
                        + "  - Diagram > Search Entities... (" + mod + "+Shift+F): クラス/メソッドを検索。\n"
                        + "  - Diagram > Scope...: 表示範囲 (パッケージ等) を細かく指定。\n"
                        + "  - Diagram > Filter Sequence Participants...: シーケンス図の登場人物を隠す。\n"
                        + "  - Diagram > Preset > Minimal / Balanced / Detailed\n"
                        + "    (" + mod + "+1 / " + mod + "+2 / " + mod + "+3): クラス図の表示密度を切替。\n"
                        + "\n"
                        + "■ 再描画 / キャンセル\n"
                        + "  - File > Refresh (F5): 現在の図を再生成。\n"
                        + "  - File > Cancel Loading: 重い解析を途中で中断。\n"
                        + "\n"
                        + "■ エクスポート (画像保存)\n"
                        + "  - File > Save Diagram As... (" + mod + "+S): PNG / SVG / PUML で保存。\n"
                        + "  - File > Export Class Diagrams Per Folder...: フォルダ単位で一括出力。\n"
                        + "\n"
                        + "■ スタイル (見た目)\n"
                        + "  - Style メニュー: テーマ切替・詳細スタイル設定。\n"
                        + "\n"
                        + "■ ヒント\n"
                        + "  - 右側タブの \"PlantUML Source\" で生成された .puml を確認できます。\n"
                        + "  - Android プロジェクトでは \"Manifest Summary\" タブで概要を確認可能。";

        javax.swing.JTextArea area = new javax.swing.JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, area.getFont().getSize()));
        area.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(640, 520));
        JOptionPane.showMessageDialog(this, sp,
                "Usage — PadTools UML", JOptionPane.INFORMATION_MESSAGE);
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

    // --- ツールバー -----------------------------------------------------------

    /**
     * ウィンドウ上部のツールバーを構築する。
     *
     * <p>2 段構成:
     * <ol>
     *   <li>上段: ファイル / 表示 / 検索など頻用操作のボタン群
     *       (Open / Save / Refresh / Back / Search / Scope / Zoom 各種)。
     *       すべてのアクションはメニュー側と同じハンドラを呼び出し、
     *       メニュー側で設定したショートカットも引き続き使用可能。</li>
     *   <li>下段: 図種切替のトグルボタン (Class / Package / Sequence / Activity /
     *       Common / Component / Dependency / Manifest / Layout)。
     *       Diagram メニューのラジオ選択と双方向に同期する。</li>
     * </ol>
     * </p>
     */
    private JComponent buildToolBarPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 0));
        container.add(buildActionToolBar(), BorderLayout.NORTH);
        container.add(buildDiagramKindToolBar(), BorderLayout.SOUTH);
        return container;
    }

    private JToolBar buildActionToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);

        bar.add(makeButton("Open", "Open project (Ctrl+O)", e -> chooseProject()));
        bar.add(makeButton("Save", "Save diagram (Ctrl+S)", e -> chooseAndExport()));
        bar.add(makeButton("Refresh", "Refresh diagram (F5)", e -> refreshDiagram()));
        bar.addSeparator();
        bar.add(makeButton("Back", "Back to previous scope (Alt+←)",
                e -> popScopeHistory()));
        bar.add(makeButton("Search", "Search entities (Ctrl+Shift+F)",
                e -> openEntitySearch()));
        bar.add(makeButton("Scope", "Edit diagram scope", e -> openScopeDialog()));
        bar.add(makeButton("Clear Scope", "Clear current scope filter", e -> {
            currentScope = null;
            refreshDiagram();
        }));
        bar.addSeparator();
        bar.add(makeButton("Zoom In", "Zoom in (Ctrl+=)", e -> previewPanel.zoomIn()));
        bar.add(makeButton("Zoom Out", "Zoom out (Ctrl+-)",
                e -> previewPanel.zoomOut()));
        bar.add(makeButton("100%", "Reset zoom (Ctrl+0)",
                e -> previewPanel.zoomReset()));
        bar.add(makeButton("Fit", "Zoom to fit (Ctrl+F)",
                e -> previewPanel.zoomToFit()));
        return bar;
    }

    private JToolBar buildDiagramKindToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(new JLabel(" Diagram: "));
        for (DiagramKind k : DiagramKind.values()) {
            JToggleButton b = new JToggleButton(toolbarLabel(k));
            b.setToolTipText(k.getDisplayName() + tooltipExtra(k));
            b.setFocusable(false);
            if (k == DiagramKind.CLASS) {
                b.setSelected(true);
            }
            final DiagramKind kind = k;
            b.addActionListener(e -> selectDiagramKind(kind));
            diagramToolbarGroup.add(b);
            diagramToggles.put(k, b);
            bar.add(b);
        }
        return bar;
    }

    /** トグルボタン用の短いラベル (ツールバー幅を抑えるため省略名)。 */
    private static String toolbarLabel(DiagramKind k) {
        switch (k) {
            case CLASS: return "Class";
            case PACKAGE: return "Package";
            case SEQUENCE: return "Sequence";
            case ACTIVITY: return "Activity";
            case COMMON: return "Common";
            case COMPONENT: return "Component";
            case DEPENDENCY: return "Dependency";
            case MANIFEST: return "Manifest";
            case LAYOUT: return "Layout";
            default: return k.getDisplayName();
        }
    }

    /** ツールチップの末尾に付ける補助説明 (必要な図種だけ案内)。 */
    private static String tooltipExtra(DiagramKind k) {
        switch (k) {
            case SEQUENCE: return " (choose entry from Diagram menu)";
            case ACTIVITY: return " (choose method from Diagram menu)";
            case LAYOUT:   return " (choose layout file from Diagram menu)";
            case COMMON:   return " — top-N most referenced classes (fan-in)";
            default: return "";
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
                && (sequenceEntry == null || sequenceEntry.isEmpty())) {
            if (cache.isLoaded()) {
                pickSequenceEntry();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.ACTIVITY
                && (activityEntry == null || activityEntry.isEmpty())) {
            if (cache.isLoaded()) {
                pickActivityEntry();
            } else {
                refreshDiagram();
            }
            return;
        }
        if (kind == DiagramKind.LAYOUT
                && (currentLayoutKey == null || currentLayoutKey.isEmpty())) {
            if (cache.isLoaded()) {
                pickLayoutFile();
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

    private static JButton makeButton(String text, String tooltip,
                                       java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.addActionListener(listener);
        return b;
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
                activityEntry = null;
                sequenceHiddenParticipants.clear();
                currentScope = null;
                updateManifestSummary();
                StringBuilder st = new StringBuilder();
                st.append("Analyzed ").append(cache.getClasses().size())
                        .append(" class(es) from ").append(root.getAbsolutePath());
                // 依存 JAR の未解決件数があれば追記 (図に <<missing>> ⚠ が出る理由を明示)
                int missing = cache.getDependencyIndex().getMissingArtifacts().size();
                if (missing > 0) {
                    st.append(" — ").append(missing).append(" dependency(ies) not resolved");
                }
                status.setText(st.toString());
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

    /**
     * 左ペインのツリーでクラスノードが選択された際のハンドラ。
     * クラス図モードへ切り替え、当該クラスを seed として 1 ホップ近傍に絞ったスコープで再描画する。
     */
    private void onTreeClassSelected(JavaClassInfo cls) {
        if (cls == null) {
            return;
        }
        String fqn = cls.getQualifiedName();
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        currentScope = DiagramScope.builder()
                .seed(fqn)
                .neighborHops(1)
                .build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: class " + cls.getSimpleName() + " (+1 hop)");
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
        currentScope = DiagramScope.builder().includeModule(module).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: module " + module);
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
        switchToSequenceDiagram(sel.getEntry());
    }

    /** {@code Class.method} 起点をセットしてシーケンス図モードへ切り替える。 */
    private void switchToSequenceDiagram(String entry) {
        sequenceEntry = entry;
        currentKind = DiagramKind.SEQUENCE;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
        if (item != null) {
            item.setSelected(true);
        }
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
            if (rightTabs != null && dynamicTabsIndex >= 0) {
                rightTabs.setSelectedIndex(dynamicTabsIndex);
            }
            return;
        }
        // フォールバック: タブパネル未配線時は現在ビューを差し替えるのみ
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
     * 左ペインのアクティビティ図リーフ (青丸) が選択された際のハンドラ。
     * アクティビティ図モードへ切り替えてその起点メソッドで再描画する。
     */
    private void onTreeActivityMethodSelected(ProjectTreePanel.MethodSelection sel) {
        if (sel == null) {
            return;
        }
        switchToActivityDiagram(sel.getEntry());
    }

    /** {@code Class.method} 起点をセットしてアクティビティ図モードへ切り替える。 */
    private void switchToActivityDiagram(String entry) {
        activityEntry = entry;
        currentKind = DiagramKind.ACTIVITY;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.ACTIVITY);
        if (item != null) {
            item.setSelected(true);
        }
        refreshDiagram();
    }

    /**
     * クラス図プレビュー上で右クリックされたとき、該当クラスのメソッド一覧を
     * {@link JPopupMenu} で表示し、選択されたメソッドを起点にシーケンス図へ切り替える。
     */
    private void onPreviewLinkClick(LinkArea link, MouseEvent event) {
        if (link == null) {
            return;
        }
        String fqn = parseClassFqnFromHref(link.getHref());
        if (fqn == null) {
            return;
        }
        drillDownToClass(fqn);
    }

    /**
     * 指定された FQN を seed として 1 ホップ近傍の詳細クラス図に遷移する。
     * 現在のスコープは履歴に push され、Back メニューで戻せる。
     */
    private void drillDownToClass(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        DiagramScope previous = currentScope;
        if (previous != null) {
            scopeHistory.push(previous);
        }
        // 新規ドリルダウン時は forward 履歴をクリア (ブラウザ動作と同じ)
        forwardHistory.clear();
        DiagramScope.Builder b = DiagramScope.builder()
                .seed(fqn).neighborHops(1);
        DiagramPreset.DETAILED.applyTo(b);
        currentScope = b.build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Drill-down: " + fqn);
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

    private void onPreviewLinkPopup(LinkArea link, MouseEvent event) {
        if (link == null || event == null) {
            return;
        }
        if (!cache.isLoaded()) {
            return;
        }
        String fqn = parseClassFqn(link.getHref());
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        JavaClassInfo cls = lookupDetailedClass(fqn);
        if (cls == null) {
            return;
        }
        JPopupMenu popup = buildMethodPopup(cls);
        if (popup.getComponentCount() == 0) {
            status.setText(cls.getSimpleName() + ": no concrete methods");
            return;
        }
        popup.show(event.getComponent(), event.getX(), event.getY());
    }

    /** {@code padtools://class/<FQN>} 形式の URL から FQN を取り出す。マッチしなければ null。 */
    private static String parseClassFqn(String href) {
        if (href == null) {
            return null;
        }
        final String prefix = "padtools://class/";
        if (!href.startsWith(prefix)) {
            return null;
        }
        return href.substring(prefix.length());
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
     * FQN から詳細展開済みの {@link JavaClassInfo} を取得する。
     * ClassIndex があれば Stage B (detail) を返し、メソッド本体まで揃った状態にする。
     */
    private JavaClassInfo lookupDetailedClass(String fqn) {
        if (cache.getIndex() != null) {
            JavaClassInfo d = cache.getIndex().detail(fqn, ErrorListener.silent());
            if (d != null) {
                return d;
            }
        }
        for (JavaClassInfo c : cache.getClasses()) {
            if (fqn.equals(c.getQualifiedName())) {
                return c;
            }
        }
        return null;
    }

    /**
     * クラスのメソッド一覧から {@link JPopupMenu} を構築する。
     * 抽象メソッドは ProjectTreePanel と同様に除外する。
     */
    private JPopupMenu buildMethodPopup(JavaClassInfo cls) {
        JPopupMenu popup = new JPopupMenu(cls.getSimpleName());
        for (JavaMethodInfo m : cls.getMethods()) {
            if (m.isAbstract()) {
                continue;
            }
            String name = m.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            JMenuItem item = new JMenuItem(formatMethodLabel(m));
            String entry = cls.getSimpleName() + "." + name;
            item.addActionListener(e -> switchToSequenceDiagram(entry));
            popup.add(item);
        }
        return popup;
    }

    /** PopupMenu 表示用のメソッドラベル: {@code name(paramType, ...)}。 */
    private static String formatMethodLabel(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append('(');
        List<String> types = m.getParameterTypes();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String t = types.get(i);
            sb.append(t == null ? "?" : t);
        }
        sb.append(')');
        return sb.toString();
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
            case METHOD:
                String simple = extractSimpleClass(result.ownerQn);
                switchToSequenceDiagram(simple + "." + result.simpleName);
                break;
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
        currentScope = DiagramScope.builder().seed(fqn).neighborHops(1).build();
        currentKind = DiagramKind.CLASS;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        status.setText("Scope: " + statusLabel + " (+1 hop)");
        refreshDiagram();
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
            sequenceHiddenParticipants.clear();
            sequenceEntry = picked;
            currentKind = DiagramKind.SEQUENCE;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
            if (item != null) {
                item.setSelected(true);
            }
            refreshDiagram();
        }
    }

    /**
     * 現在のシーケンス図起点に登場する participant をフィルタダイアログで選択できるようにする。
     * 選択結果は {@link #sequenceHiddenParticipants} に保存され、再描画時の
     * {@link DiagramRequest#getSequenceHiddenParticipants()} に渡される。
     */
    private void openParticipantFilterDialog() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (sequenceEntry == null || sequenceEntry.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Choose a sequence entry first (Diagram → Choose Sequence Entry...).",
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dot = sequenceEntry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String cls = sequenceEntry.substring(0, dot);
        String method = sequenceEntry.substring(dot + 1);
        java.util.Set<String> all =
                padtools.core.formats.uml.PlantUmlSequenceDiagram.collectParticipants(
                        cache.getClasses(), cls, method, null);
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No participants found for " + sequenceEntry,
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> picked = SequenceParticipantFilterDialog.show(
                this, sequenceEntry, all, sequenceHiddenParticipants);
        if (picked != null) {
            sequenceHiddenParticipants.clear();
            sequenceHiddenParticipants.addAll(picked);
            int total = all.size();
            int hidden = sequenceHiddenParticipants.size();
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
            activityEntry = picked;
            currentKind = DiagramKind.ACTIVITY;
            JRadioButtonMenuItem item = diagramItems.get(DiagramKind.ACTIVITY);
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
        currentLayoutKey = picked;
        currentKind = DiagramKind.LAYOUT;
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.LAYOUT);
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
                && (sequenceEntry == null || sequenceEntry.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a sequence entry from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.ACTIVITY
                && (activityEntry == null || activityEntry.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose an activity method from Diagram menu.");
            return;
        }
        if (kind == DiagramKind.LAYOUT
                && (currentLayoutKey == null || currentLayoutKey.isEmpty())) {
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            sourcePanel.setText("");
            status.setText("Choose a layout file from Diagram menu.");
            return;
        }
        status.setText("Rendering " + kind.getDisplayName() + " ...");
        final String entry = sequenceEntry;
        final String activity = activityEntry;
        final String layoutKey = currentLayoutKey;
        final DiagramScope scope = currentScope;
        new SwingWorker<RenderResult, Void>() {
            private Throwable error;
            // レンダ前にキャプチャしておく puml。例外発生時も PlantUML Source タブに
            // 残せるようにするための退避先。
            private String pumlOnError;

            @Override
            protected RenderResult doInBackground() {
                try {
                    boolean wantLinks = (kind == DiagramKind.CLASS);
                    DiagramRequest req;
                    if (kind == DiagramKind.SEQUENCE && entry != null) {
                        req = buildSequenceRequest(entry);
                    } else if (kind == DiagramKind.ACTIVITY && activity != null) {
                        req = buildActivityRequest(activity);
                    } else if (kind == DiagramKind.LAYOUT && layoutKey != null) {
                        req = DiagramRequest.forLayout(layoutKey, true);
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
                    currentPuml = r.puml;
                    previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                            r.svg.getWidth(), r.svg.getHeight());
                    previewPanel.setLinkAreas(r.svg.getLinkAreas());
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
        java.util.Set<String> hidden = sequenceHiddenParticipants.isEmpty()
                ? null : new java.util.LinkedHashSet<>(sequenceHiddenParticipants);
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
