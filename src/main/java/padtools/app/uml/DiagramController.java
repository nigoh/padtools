package padtools.app.uml;

import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.core.formats.uml.JavaClassInfo;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JTabbedPane;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 図種の切り替え・ツリー選択ハンドラ・ダイアログ操作など図制御ロジックを集約するコントローラ。
 *
 * <p>UI の構築は行わず、状態遷移と UI 同期のみを担当する。</p>
 */
public final class DiagramController {

    // package-private: 補助クラス DiagramEntryDialogs が参照する。
    final DiagramState state;
    private final Supplier<ProjectAnalysisCache> cacheSupplier;
    final EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    private final EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private final ProjectTreePanel treePanel;
    private final JTabbedPane mainTabs;
    private final DiagramTabPane tabPane;
    final javax.swing.JLabel statusLabel;
    final java.awt.Frame parentFrame;
    final Runnable refreshDiagram;
    private final Consumer<DiagramKind> onKindChanged;
    private final DiagramEntryDialogs entryDialogs;

    /** package-private — UmlMainFrame がミラー同期するために読む。 */
    DiagramKind currentKind = DiagramKind.CLASS;

    public DiagramController(DiagramControllerDeps deps) {
        this.state = deps.state;
        this.cacheSupplier = deps.cacheSupplier;
        this.diagramItems = deps.diagramItems;
        this.diagramToggles = deps.diagramToggles;
        this.treePanel = deps.treePanel;
        this.mainTabs = deps.mainTabs;
        this.tabPane = deps.tabPane;
        this.statusLabel = deps.statusLabel;
        this.parentFrame = deps.parentFrame;
        this.refreshDiagram = deps.refreshDiagram;
        this.onKindChanged = deps.onKindChanged;
        this.entryDialogs = new DiagramEntryDialogs(this);
    }

    ProjectAnalysisCache cache() {
        return cacheSupplier.get();
    }

    /** currentKind への書き込みはすべてこのメソッド経由で行い、呼び出し元へ通知する。 */
    void setCurrentKind(DiagramKind kind) {
        currentKind = kind;
        onKindChanged.accept(kind);
    }

    // -------------------------------------------------------------------------
    // ツリー選択ハンドラ
    // -------------------------------------------------------------------------

    public void onTreePackageSelected(String pkg) {
        if (pkg == null || pkg.isEmpty() || "(default)".equals(pkg)) {
            return;
        }
        state.currentScope = DiagramScope.builder().includePackage(pkg).build();
        setCurrentKind(DiagramKind.CLASS);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        statusLabel.setText("Scope: package " + pkg);
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_PACKAGE);
        showHomeTab();
        refreshDiagram.run();
    }

    /**
     * 左ペインのツリーでクラスノードが選択された際のハンドラ。
     * クラス図モードへ切り替え、当該クラスを seed として 1 ホップ近傍に絞ったスコープで再描画する。
     */
    public void onTreeClassSelected(JavaClassInfo cls) {
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
        setCurrentKind(DiagramKind.CLASS);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        statusLabel.setText("Scope: class " + cls.getSimpleName() + " (+1 hop)");
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_JAVA_TYPE);
        showHomeTab();
        refreshDiagram.run();
    }

    /**
     * 左ペインのツリーでモジュールノードが選択された際のハンドラ。
     * クラス図モードへ切り替え、当該モジュールに含まれるクラスだけに絞って再描画する。
     * <p>"(other)" のようなプレースホルダ名は無視する。</p>
     */
    public void onTreeModuleSelected(String module) {
        if (module == null || module.isEmpty() || "(other)".equals(module)) {
            return;
        }
        state.currentScope = DiagramScope.builder().includeModule(module).build();
        setCurrentKind(DiagramKind.CLASS);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        statusLabel.setText("Scope: module " + module);
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_MODULE);
        showHomeTab();
        refreshDiagram.run();
    }

    public void openScopeDialog() {
        if (!cache().isLoaded()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> packages = new java.util.TreeSet<>();
        java.util.Set<String> modules = new java.util.TreeSet<>(cache().getClassToModule().values());
        for (padtools.core.formats.uml.JavaClassInfo c : cache().getClasses()) {
            String p = c.getPackageName();
            if (p != null && !p.isEmpty()) {
                packages.add(p);
            }
        }
        DiagramScopeDialog dlg = new DiagramScopeDialog(parentFrame,
                List.copyOf(packages), List.copyOf(modules), state.currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        if (picked != null) {
            state.currentScope = picked.isEmpty() ? null : picked;
            refreshDiagram.run();
        }
    }

    /**
     * 左ペインのツリーで Manifest ノード (または配下の Permissions / Features
     * グループ) が選択されたら Manifest 図モードへ切り替える。
     */
    public void onTreeManifestSelected(padtools.core.formats.android.AndroidManifestInfo m) {
        switchToManifestDiagram();
    }

    /**
     * 左ペインのツリーで Manifest 配下の個別コンポーネントが選択されたら
     * Manifest 図モードへ切り替える (将来的に該当ノードへ強調表示を入れる余地)。
     */
    public void onTreeComponentSelected(
            padtools.core.formats.android.AndroidComponentInfo c) {
        switchToManifestDiagram();
    }

    private void switchToManifestDiagram() {
        setCurrentKind(DiagramKind.MANIFEST);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.MANIFEST);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_ANDROID);
        showHomeTab();
        refreshDiagram.run();
    }

    /**
     * 左ペインのツリーでメソッドが選択された際のハンドラ。
     * シーケンス図モードへ切り替え、かつ Activity / CallGraph 起点も同じメソッドに同期する。
     * これにより、ツールバーの Sequence / Activity / Call Graph ボタンを押すだけで
     * ダイアログを開かずに切り替えられる。
     */
    public void onTreeMethodSelected(ProjectTreePanel.MethodSelection sel) {
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
    public void setAllMethodEntries(String entry) {
        state.setAllMethodEntries(entry);
    }

    /** {@code Class.method} 起点をセットしてシーケンス図モードへ切り替える。 */
    private void switchToSequenceDiagram(String entry) {
        state.sequenceEntry = entry;
        setCurrentKind(DiagramKind.SEQUENCE);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.SEQUENCE);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram.run();
    }

    /**
     * 左ペインで中クリックされたノードを「新しいタブ」として開くハンドラ。
     * 機能 2 (動的タブ) で {@link DiagramTabPane} に委譲するが、現状は単に
     * その図種に切り替える (タブ追加は別実装)。
     */
    public void onTreeOpenInNewTab(TreeNodeOpenRequest req) {
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
    public void applyOpenRequest(TreeNodeOpenRequest req) {
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
    public void onTreeActivityMethodSelected(ProjectTreePanel.MethodSelection sel) {
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
        setCurrentKind(DiagramKind.ACTIVITY);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.ACTIVITY);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram.run();
    }

    /** {@code Class.method} 起点をセットしてコールグラフモードへ切り替える。 */
    private void switchToCallGraphDiagram(String entry) {
        state.callGraphEntry = entry;
        setCurrentKind(DiagramKind.CALLGRAPH);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CALLGRAPH);
        if (item != null) {
            item.setSelected(true);
        }
        updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        showHomeTab();
        refreshDiagram.run();
    }

    // -------------------------------------------------------------------------
    // プレビューリンクハンドラ
    // -------------------------------------------------------------------------

    /**
     * クラス図プレビュー上で左クリックされたとき:
     * - {@code padtools://method/<FQN>#<method>} リンクならシーケンス/アクティビティ図選択メニュー
     * - {@code padtools://class/<FQN>} リンクならドリルダウン
     */
    public void onPreviewLinkClick(LinkArea link, MouseEvent event) {
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
        setCurrentKind(DiagramKind.CLASS);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        statusLabel.setText("Drill-down: " + fqn);
        treePanel.selectClassNode(fqn);
        refreshDiagram.run();
    }

    /** href 文字列から {@code padtools://class/<FQN>} の FQN 部分を取り出す。 */
    public static String parseClassFqnFromHref(String href) {
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

    // -------------------------------------------------------------------------
    // 図種切替・UI 同期
    // -------------------------------------------------------------------------

    /**
     * ノード選択に応じてツールバーの図種ボタンを有効/無効化する。
     *
     * <p>{@code allowed} に含まれない図種のボタンは押下不可になる。
     * 現在の選択図種が無効になった場合、{@code allowed} の先頭図種に自動切替する。</p>
     */
    public void updateAvailableDiagrams(EnumSet<DiagramKind> allowed) {
        for (java.util.Map.Entry<DiagramKind, JToggleButton> e : diagramToggles.entrySet()) {
            e.getValue().setVisible(allowed.contains(e.getKey()));
        }
        if (!allowed.contains(currentKind)) {
            DiagramKind fallback = allowed.iterator().next();
            setCurrentKind(fallback);
            JToggleButton btn = diagramToggles.get(fallback);
            if (btn != null) {
                btn.setSelected(true);
            }
            JRadioButtonMenuItem item = diagramItems.get(fallback);
            if (item != null) {
                item.setSelected(true);
            }
            refreshDiagram.run();
        }
    }

    /** ツリーノードの左クリック後に Home タブ (index 0) を前面に出す。 */
    private void showHomeTab() {
        if (mainTabs != null && mainTabs.getTabCount() > 0 && mainTabs.getSelectedIndex() != 0) {
            mainTabs.setSelectedIndex(0);
        }
    }

    /**
     * ツールバーの図種ボタンが押されたときのハンドラ。
     * 図種に応じて必要な追加入力ダイアログを開く (シーケンス起点未指定など)。
     */
    public void selectDiagramKind(DiagramKind kind) {
        setCurrentKind(kind);
        JRadioButtonMenuItem item = diagramItems.get(kind);
        if (item != null) {
            item.setSelected(true);
        }
        // SEQUENCE/ACTIVITY/LAYOUT は追加入力が必要。未指定なら入力ダイアログを誘導する。
        if (kind == DiagramKind.SEQUENCE
                && (state.sequenceEntry == null || state.sequenceEntry.isEmpty())) {
            if (cache().isLoaded()) {
                pickSequenceEntry();
            } else {
                refreshDiagram.run();
            }
            return;
        }
        if (kind == DiagramKind.ACTIVITY
                && (state.activityEntry == null || state.activityEntry.isEmpty())) {
            if (cache().isLoaded()) {
                pickActivityEntry();
            } else {
                refreshDiagram.run();
            }
            return;
        }
        if (kind == DiagramKind.CALLGRAPH
                && (state.callGraphEntry == null || state.callGraphEntry.isEmpty())) {
            if (cache().isLoaded()) {
                pickCallGraphEntry();
            } else {
                refreshDiagram.run();
            }
            return;
        }
        if (kind == DiagramKind.LAYOUT
                && (state.currentLayoutKey == null || state.currentLayoutKey.isEmpty())) {
            if (cache().isLoaded()) {
                pickLayoutFile();
            } else {
                refreshDiagram.run();
            }
            return;
        }
        if (kind == DiagramKind.NAVIGATION
                && (state.currentNavigationKey == null || state.currentNavigationKey.isEmpty())) {
            if (cache().isLoaded()) {
                pickNavigationGraph();
            } else {
                refreshDiagram.run();
            }
            return;
        }
        refreshDiagram.run();
    }

    /**
     * ツールバーのトグルボタン側で現在の図種を反映する。メニュー (ラジオボタン) と
     * 双方向に同期するため、各 onTree*Selected / drillDown 等で
     * {@code diagramItems} を更新する場所で同時に呼ぶ。
     */
    public void syncDiagramToggle(DiagramKind kind) {
        JToggleButton b = diagramToggles.get(kind);
        if (b != null && !b.isSelected()) {
            b.setSelected(true);
        }
    }

    // -------------------------------------------------------------------------
    // エンティティ検索・スコープ操作
    // -------------------------------------------------------------------------

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
    public void openEntitySearch() {
        if (!cache().isLoaded()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        EntitySearchDialog dlg = new EntitySearchDialog(parentFrame, cache().getClasses());
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(parentFrame,
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
                menu.show(parentFrame, parentFrame.getWidth() / 2, parentFrame.getHeight() / 2);
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
    public void scopeToClass(String fqn, String label) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        state.currentScope = DiagramScope.builder().seed(fqn).neighborHops(1).build();
        setCurrentKind(DiagramKind.CLASS);
        JRadioButtonMenuItem item = diagramItems.get(DiagramKind.CLASS);
        if (item != null) {
            item.setSelected(true);
        }
        statusLabel.setText("Scope: " + label + " (+1 hop)");
        treePanel.selectClassNode(fqn);
        refreshDiagram.run();
    }

    /**
     * "SimpleClass.method" 形式のエントリからクラスの FQN を検索し、
     * 左ツリーパネルの対応するメソッドノードを選択する。
     */
    public void syncTreeToMethodByEntry(String entry) {
        if (entry == null || !cache().isLoaded()) {
            return;
        }
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String simpleName = entry.substring(0, dot);
        String methodName = entry.substring(dot + 1);
        for (padtools.core.formats.uml.JavaClassInfo c : cache().getClasses()) {
            if (simpleName.equals(c.getSimpleName())) {
                treePanel.selectMethodNode(c.getQualifiedName(), methodName);
                return;
            }
        }
    }

    public static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) {
            return "";
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }

    // -------------------------------------------------------------------------
    // エントリ選択ダイアログ
    // -------------------------------------------------------------------------
    // エントリ選択ダイアログ (DiagramEntryDialogs へ委譲)
    // -------------------------------------------------------------------------

    public void pickSequenceEntry() {
        entryDialogs.pickSequenceEntry();
    }

    public void openParticipantFilterDialog() {
        entryDialogs.openParticipantFilterDialog();
    }

    public void pickActivityEntry() {
        entryDialogs.pickActivityEntry();
    }

    public void pickCallGraphEntry() {
        entryDialogs.pickCallGraphEntry();
    }

    public void pickLayoutFile() {
        entryDialogs.pickLayoutFile();
    }

    public void pickNavigationGraph() {
        entryDialogs.pickNavigationGraph();
    }

    // -------------------------------------------------------------------------
    // DiagramRequest ビルダ (public — refreshDiagramNow から呼ばれる)
    // -------------------------------------------------------------------------

    public DiagramRequest buildSequenceRequest(String entry) {
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

    public DiagramRequest buildActivityRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Activity entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forActivity(
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }

    public DiagramRequest buildCallGraphRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Call graph entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forCallGraph(
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }
}
