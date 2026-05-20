package padtools.app.uml;

import padtools.core.formats.android.AndroidComponentInfo;
import padtools.core.formats.android.AndroidManifestInfo;
import padtools.core.formats.android.AndroidPermissionInfo;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.Visibility;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * プロジェクトのモジュール/パッケージ/クラス/メソッド階層を表示する {@link JTree}。
 *
 * <p>{@link ProjectAnalysisCache} の解析結果から
 * <code>(プロジェクトルート)</code> → <code>module</code> →
 * <code>package</code> → <code>class</code> → <code>method</code>
 * のノードツリーを構築する。モジュール情報は
 * {@link AndroidProjectAnalysis#getGradleByModule()} と
 * {@link ClassIndex#moduleMap()} を組み合わせて紐付け、見つからなかったクラスは
 * <em>(other)</em> モジュール下に集約する。</p>
 *
 * <p>AOSP 級でも初期表示が固まらないよう、子ノードは遅延構築する:
 * <ul>
 *   <li>初期: モジュール → パッケージ ノードまで構築</li>
 *   <li>パッケージ展開時: 該当パッケージのクラスノードを追加</li>
 *   <li>クラス展開時: メソッドノードを追加</li>
 * </ul>
 * 抽象メソッドはシーケンス図起点にならないので除外する。</p>
 *
 * <p>選択イベントは {@link #setOnClassSelected(java.util.function.Consumer)} ・
 * {@link #setOnMethodSelected(java.util.function.Consumer)} ・
 * {@link #setOnPackageSelected(java.util.function.Consumer)} で受け取れる。
 * パッケージ右クリックメニューからクラス図ドリルダウンを発火する。</p>
 */
public class ProjectTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode root;
    private java.util.function.Consumer<JavaClassInfo> onClassSelected;
    private java.util.function.Consumer<MethodSelection> onMethodSelected;
    private java.util.function.Consumer<MethodSelection> onActivityMethodSelected;
    private java.util.function.Consumer<String> onPackageSelected;
    private java.util.function.Consumer<String> onModuleSelected;
    private java.util.function.Consumer<AndroidManifestInfo> onManifestSelected;
    private java.util.function.Consumer<AndroidComponentInfo> onComponentSelected;
    private java.util.function.Consumer<TreeNodeOpenRequest> onOpenInNewTab;

    public ProjectTreePanel() {
        super(new BorderLayout());
        root = new DefaultMutableTreeNode("(no project)");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> notifySelection());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                Object last = event.getPath().getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode) {
                    expandLazy((DefaultMutableTreeNode) last);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // no-op: 一度構築した子ノードは破棄せず保持
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
                // 中クリックは mousePressed で捕捉する (プラットフォーム互換性のため)
                if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                    maybeOpenInNewTab(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // ダブルクリックで新しいタブに開く (左ボタンのみ)
                if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    maybeOpenInNewTab(e);
                }
            }
        });
        tree.setCellRenderer(new ProjectTreeCellRenderer());
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(new TreeIconLegendPanel(), BorderLayout.SOUTH);
    }

    public void setOnClassSelected(java.util.function.Consumer<JavaClassInfo> listener) {
        this.onClassSelected = listener;
    }

    public void setOnMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onMethodSelected = listener;
    }

    /**
     * メソッド配下のアクティビティ図リーフが選択されたときのハンドラ。
     * 受け取り側はアクティビティ図モードへ切り替える。
     */
    public void setOnActivityMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onActivityMethodSelected = listener;
    }

    /**
     * パッケージノードのクリック (左クリック選択 / 右クリックメニュー両方) で発火する
     * ドリルダウン用ハンドラ。 受け取り側は当該パッケージにスコープしたクラス図への
     * 切り替えなどを行う。
     */
    public void setOnPackageSelected(java.util.function.Consumer<String> listener) {
        this.onPackageSelected = listener;
    }

    /** モジュールノード選択時のハンドラ。受け取り側は当該モジュールにスコープした図への切替などを行う。 */
    public void setOnModuleSelected(java.util.function.Consumer<String> listener) {
        this.onModuleSelected = listener;
    }

    /** Manifest ノード選択時のコールバック。Manifest 図への切り替え等に使う。 */
    public void setOnManifestSelected(java.util.function.Consumer<AndroidManifestInfo> listener) {
        this.onManifestSelected = listener;
    }

    /** Manifest 配下の Activity / Service / Receiver / Provider ノード選択時のコールバック。 */
    public void setOnComponentSelected(java.util.function.Consumer<AndroidComponentInfo> listener) {
        this.onComponentSelected = listener;
    }

    /**
     * マウス中クリックで「新しいタブとして開く」操作を受けるハンドラ。
     * 受け取り側は {@link DiagramTabPane#addOrFocusTab} などを呼び出す。
     */
    public void setOnOpenInNewTab(java.util.function.Consumer<TreeNodeOpenRequest> listener) {
        this.onOpenInNewTab = listener;
    }

    /** モジュール紐付けなしの簡易 populate (既存呼び出しの互換維持)。 */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName) {
        populate(analysis, classes, projectName, null);
    }

    /**
     * 解析結果からツリーを再構築する。
     *
     * <p>classToModule が与えられた場合、各クラスはそのモジュール下に配置される。
     * null/空の場合は全クラスを <code>(other)</code> に集約する。</p>
     */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName, Map<String, String> classToModule) {
        root.setUserObject(projectName != null ? projectName : "(project)");
        root.removeAllChildren();
        // モジュール → パッケージ → クラスを集計
        Map<String, Map<String, List<JavaClassInfo>>> byModule
                = groupByModule(analysis, classes, classToModule);
        // どのモジュールに manifest があるかは analysis から取り出す
        Map<String, List<AndroidManifestInfo>> manifestsByModule = analysis != null
                ? analysis.getManifestsByModule() : java.util.Collections.emptyMap();
        // クラスが何もないモジュールでも manifest があれば表示する
        for (String mod : manifestsByModule.keySet()) {
            byModule.computeIfAbsent(mod, k -> new TreeMap<>());
        }
        for (Map.Entry<String, Map<String, List<JavaClassInfo>>> me : byModule.entrySet()) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                    new ModuleEntry(me.getKey()));
            // Manifest を先頭に出す (アプリ視点で目立ちやすいように)
            List<AndroidManifestInfo> manifests = manifestsByModule.get(me.getKey());
            if (manifests != null) {
                for (AndroidManifestInfo m : manifests) {
                    moduleNode.add(buildManifestNode(m));
                }
            }
            for (Map.Entry<String, List<JavaClassInfo>> pe : me.getValue().entrySet()) {
                PackageEntry pkgEntry = new PackageEntry(pe.getKey(), pe.getValue().size(),
                        pe.getValue());
                DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(pkgEntry);
                // クラス子ノードは遅延構築 (TreeWillExpandListener で生成)
                // ただし「展開可能」と見せるためのダミー子を 1 件入れる
                if (!pe.getValue().isEmpty()) {
                    pkgNode.add(new DefaultMutableTreeNode(LazyPlaceholder.INSTANCE));
                }
                moduleNode.add(pkgNode);
            }
            root.add(moduleNode);
        }
        model.reload(root);
        // モジュールノードは開いた状態で見せておく (パッケージ一覧がすぐ見えるように)。
        // row index ではなく TreePath で展開しないとルートと取り違える。
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode mod = (DefaultMutableTreeNode) root.getChildAt(i);
            tree.expandPath(new TreePath(mod.getPath()));
        }
    }

    /**
     * Manifest 1 件分のサブツリーを構築。
     *
     * <p>{@code [manifest] sourceSet} → Activities / Services / Receivers / Providers /
     * Permissions / Features の順で展開可能なノードを並べる。</p>
     */
    private static DefaultMutableTreeNode buildManifestNode(AndroidManifestInfo m) {
        DefaultMutableTreeNode mnode = new DefaultMutableTreeNode(new ManifestEntry(m));
        addComponentGroup(mnode, "Activities", m.getActivities());
        addComponentGroup(mnode, "Services", m.getServices());
        addComponentGroup(mnode, "Receivers", m.getReceivers());
        addComponentGroup(mnode, "Providers", m.getProviders());
        if (!m.getPermissions().isEmpty()) {
            DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                    new ComponentGroupEntry("Permissions", m.getPermissions().size()));
            for (AndroidPermissionInfo p : m.getPermissions()) {
                g.add(new DefaultMutableTreeNode(new PermissionEntry(p)));
            }
            mnode.add(g);
        }
        if (!m.getFeatures().isEmpty()) {
            DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                    new ComponentGroupEntry("Features", m.getFeatures().size()));
            for (String f : m.getFeatures()) {
                g.add(new DefaultMutableTreeNode(new FeatureEntry(f)));
            }
            mnode.add(g);
        }
        return mnode;
    }

    private static void addComponentGroup(DefaultMutableTreeNode mnode, String label,
                                           List<AndroidComponentInfo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                new ComponentGroupEntry(label, list.size()));
        for (AndroidComponentInfo c : list) {
            g.add(new DefaultMutableTreeNode(new ComponentEntry(c)));
        }
        mnode.add(g);
    }

    /** ツリーをクリアして空状態に戻す。 */
    public void clear() {
        root.setUserObject("(no project)");
        root.removeAllChildren();
        model.reload(root);
    }

    private void notifySelection() {
        Object last = tree.getLastSelectedPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
            Object u = ((DefaultMutableTreeNode) last).getUserObject();
            if (u instanceof MethodDiagramEntry) {
                MethodDiagramEntry mde = (MethodDiagramEntry) u;
                MethodSelection sel = new MethodSelection(mde.owner, mde.method);
                if (mde.kind == DiagramKind.ACTIVITY) {
                    if (onActivityMethodSelected != null) {
                        onActivityMethodSelected.accept(sel);
                    }
                } else {
                    if (onMethodSelected != null) {
                        onMethodSelected.accept(sel);
                    }
                }
                return;
            }
            if (u instanceof MethodEntry) {
                // メソッド自体のクリックは従来通りシーケンス図に切り替え (後方互換)
                MethodEntry me = (MethodEntry) u;
                if (onMethodSelected != null) {
                    onMethodSelected.accept(new MethodSelection(me.owner, me.method));
                }
                return;
            }
            if (u instanceof ClassEntry) {
                if (onClassSelected != null) {
                    onClassSelected.accept(((ClassEntry) u).info);
                }
                return;
            }
            if (u instanceof PackageEntry) {
                if (onPackageSelected != null) {
                    onPackageSelected.accept(((PackageEntry) u).name);
                }
                return;
            }
            if (u instanceof ModuleEntry) {
                if (onModuleSelected != null) {
                    onModuleSelected.accept(((ModuleEntry) u).name);
                }
                return;
            }
            if (u instanceof ManifestEntry) {
                if (onManifestSelected != null) {
                    onManifestSelected.accept(((ManifestEntry) u).info);
                }
                return;
            }
            if (u instanceof ComponentEntry) {
                if (onComponentSelected != null) {
                    onComponentSelected.accept(((ComponentEntry) u).info);
                }
                return;
            }
            // ComponentGroupEntry / PermissionEntry / FeatureEntry も Manifest 図に切替
            if (u instanceof ComponentGroupEntry || u instanceof PermissionEntry
                    || u instanceof FeatureEntry) {
                if (onManifestSelected != null) {
                    onManifestSelected.accept(null);
                }
                return;
            }
        }
        if (onClassSelected != null) {
            onClassSelected.accept(null);
        }
    }

    /**
     * マウス中クリック (ホイール押し込み) で当該ノードを「新しいタブで開く」リクエストを発火する。
     * 受け手 (機能 2 で配線) は {@link #setOnOpenInNewTab(java.util.function.Consumer)} で設定する。
     * 中クリックはツリーの選択を変えない (Web ブラウザの挙動に合わせる)。
     */
    private void maybeOpenInNewTab(MouseEvent e) {
        if (onOpenInNewTab == null) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }
        TreeNodeOpenRequest req = buildOpenRequest(((DefaultMutableTreeNode) last).getUserObject());
        if (req != null) {
            onOpenInNewTab.accept(req);
        }
    }

    /** ツリーのユーザーオブジェクトから「新しいタブで開く」リクエストを作る。 */
    private TreeNodeOpenRequest buildOpenRequest(Object u) {
        if (u instanceof MethodDiagramEntry) {
            MethodDiagramEntry mde = (MethodDiagramEntry) u;
            return TreeNodeOpenRequest.method(mde.owner, mde.method, mde.kind);
        }
        if (u instanceof MethodEntry) {
            MethodEntry me = (MethodEntry) u;
            return TreeNodeOpenRequest.method(me.owner, me.method, DiagramKind.SEQUENCE);
        }
        if (u instanceof ClassEntry) {
            return TreeNodeOpenRequest.classNode(((ClassEntry) u).info);
        }
        if (u instanceof PackageEntry) {
            return TreeNodeOpenRequest.pkg(((PackageEntry) u).name);
        }
        if (u instanceof ModuleEntry) {
            return TreeNodeOpenRequest.module(((ModuleEntry) u).name);
        }
        return null;
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object u = ((DefaultMutableTreeNode) last).getUserObject();
        if (u instanceof PackageEntry && onPackageSelected != null) {
            final String pkgName = ((PackageEntry) u).name;
            JPopupMenu menu = new JPopupMenu();
            JMenuItem drill = new JMenuItem("Show class diagram of this package");
            drill.addActionListener(ev -> onPackageSelected.accept(pkgName));
            menu.add(drill);
            tree.setSelectionPath(path);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /** 遅延展開: パッケージ → クラスノード、クラス → メソッドノードを生成する。 */
    private void expandLazy(DefaultMutableTreeNode node) {
        Object u = node.getUserObject();
        if (u instanceof PackageEntry) {
            PackageEntry pe = (PackageEntry) u;
            if (pe.expanded) {
                return;
            }
            pe.expanded = true;
            node.removeAllChildren();
            for (JavaClassInfo c : pe.classes) {
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassEntry(c));
                // クラス展開時にメソッドを構築するためダミー子を 1 件入れる
                if (hasConcreteMethods(c)) {
                    classNode.add(new DefaultMutableTreeNode(LazyPlaceholder.INSTANCE));
                }
                node.add(classNode);
            }
            model.nodeStructureChanged(node);
        } else if (u instanceof ClassEntry) {
            ClassEntry ce = (ClassEntry) u;
            if (ce.expanded) {
                return;
            }
            ce.expanded = true;
            node.removeAllChildren();
            addMethodNodes(node, ce.info);
            model.nodeStructureChanged(node);
        }
    }

    private static boolean hasConcreteMethods(JavaClassInfo c) {
        if (c.getMethods() == null) {
            return false;
        }
        for (JavaMethodInfo m : c.getMethods()) {
            if (!m.isAbstract()) {
                return true;
            }
        }
        return false;
    }

    private static void addMethodNodes(DefaultMutableTreeNode classNode, JavaClassInfo c) {
        for (JavaMethodInfo m : c.getMethods()) {
            if (m.isAbstract()) {
                continue;
            }
            DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(new MethodEntry(c, m));
            // シーケンス図 (赤丸) / アクティビティ図 (青丸) のリーフを生やす
            methodNode.add(new DefaultMutableTreeNode(
                    new MethodDiagramEntry(c, m, DiagramKind.SEQUENCE)));
            methodNode.add(new DefaultMutableTreeNode(
                    new MethodDiagramEntry(c, m, DiagramKind.ACTIVITY)));
            classNode.add(methodNode);
        }
    }

    private static Map<String, Map<String, List<JavaClassInfo>>> groupByModule(
            AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
            Map<String, String> classToModule) {
        Map<String, Map<String, List<JavaClassInfo>>> out = new LinkedHashMap<>();
        if (analysis != null) {
            for (String mod : analysis.getGradleByModule().keySet()) {
                out.computeIfAbsent(mod, k -> new TreeMap<>());
            }
        }
        Map<String, String> map = classToModule != null ? classToModule : java.util.Collections.emptyMap();
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            String mod = map.getOrDefault(qn, "(other)");
            String pkg = c.getPackageName() == null || c.getPackageName().isEmpty()
                    ? "(default)" : c.getPackageName();
            out.computeIfAbsent(mod, k -> new TreeMap<>())
                    .computeIfAbsent(pkg, k -> new java.util.ArrayList<>())
                    .add(c);
        }
        // 空のモジュールエントリは除去
        out.entrySet().removeIf(e -> e.getValue().isEmpty());
        return out;
    }

    /** モジュール名を保持するノード値。 */
    static final class ModuleEntry {
        final String name;

        ModuleEntry(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "[module] " + name;
        }
    }

    /** パッケージ情報。expanded フラグと、遅延構築用に対象クラス一覧を保持する。 */
    static final class PackageEntry {
        final String name;
        final int count;
        final List<JavaClassInfo> classes;
        boolean expanded;

        PackageEntry(String name, int count, List<JavaClassInfo> classes) {
            this.name = name;
            this.count = count;
            this.classes = classes;
        }

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    /** クラス情報を保持するノード値。表示はシンプル名のみ。 */
    static final class ClassEntry {
        final JavaClassInfo info;
        boolean expanded;

        ClassEntry(JavaClassInfo info) {
            this.info = info;
        }

        @Override
        public String toString() {
            String kind;
            switch (info.getKind()) {
                case INTERFACE: kind = "I"; break;
                case ENUM: kind = "E"; break;
                case ANNOTATION: kind = "A"; break;
                case AIDL_INTERFACE: kind = "AIDL"; break;
                default: kind = "C"; break;
            }
            return "[" + kind + "] " + info.getSimpleName();
        }
    }

    /** メソッド情報を保持するノード値。シーケンス図起点として使える。 */
    static final class MethodEntry {
        final JavaClassInfo owner;
        final JavaMethodInfo method;

        MethodEntry(JavaClassInfo owner, JavaMethodInfo method) {
            this.owner = owner;
            this.method = method;
        }

        @Override
        public String toString() {
            Visibility v = method.getVisibility();
            String mark = v == null ? "~" : v.mark();
            return mark + " " + method.getName() + "()";
        }
    }

    /**
     * メソッドノードの子として並ぶ「図種別リーフ」ノード値。
     * {@link DiagramKind#SEQUENCE} (赤丸) と {@link DiagramKind#ACTIVITY} (青丸) の
     * 2 種類を {@link #addMethodNodes} で生成し、{@link ProjectTreeCellRenderer} が
     * 該当アイコンを描画する。
     */
    static final class MethodDiagramEntry {
        final JavaClassInfo owner;
        final JavaMethodInfo method;
        final DiagramKind kind;

        MethodDiagramEntry(JavaClassInfo owner, JavaMethodInfo method, DiagramKind kind) {
            this.owner = owner;
            this.method = method;
            this.kind = kind;
        }

        @Override
        public String toString() {
            switch (kind) {
                case SEQUENCE:
                    return "sequence";
                case ACTIVITY:
                    return "activity";
                default:
                    return kind.name().toLowerCase(java.util.Locale.ROOT);
            }
        }
    }

    /** 遅延構築前のダミー子ノード値。「+」表示を出すためだけに使う。 */
    private static final class LazyPlaceholder {
        static final LazyPlaceholder INSTANCE = new LazyPlaceholder();

        @Override
        public String toString() {
            return "...";
        }
    }

    /** AndroidManifest.xml 単位のノード値。表示は sourceSet とパッケージ名。 */
    static final class ManifestEntry {
        final AndroidManifestInfo info;

        ManifestEntry(AndroidManifestInfo info) {
            this.info = info;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[manifest] AndroidManifest.xml");
            if (info.getSourceSet() != null) {
                sb.append(" (").append(info.getSourceSet()).append(")");
            }
            if (info.getPackageName() != null && !info.getPackageName().isEmpty()) {
                sb.append(" — ").append(info.getPackageName());
            }
            return sb.toString();
        }
    }

    /** Activities/Services/Receivers/Providers/Permissions/Features のグループ見出し。 */
    static final class ComponentGroupEntry {
        final String label;
        final int count;

        ComponentGroupEntry(String label, int count) {
            this.label = label;
            this.count = count;
        }

        @Override
        public String toString() {
            return label + " (" + count + ")";
        }
    }

    /** Manifest 配下の Activity / Service / Receiver / Provider ノード値。 */
    static final class ComponentEntry {
        final AndroidComponentInfo info;

        ComponentEntry(AndroidComponentInfo info) {
            this.info = info;
        }

        @Override
        public String toString() {
            String name = info.getName() == null || info.getName().isEmpty()
                    ? "(unnamed)" : info.getName();
            int dot = name.lastIndexOf('.');
            String shortName = dot >= 0 ? name.substring(dot + 1) : name;
            StringBuilder sb = new StringBuilder();
            sb.append(kindBadge(info.getKind())).append(' ').append(shortName);
            if (info.isLauncher()) {
                sb.append(" [launcher]");
            }
            if (Boolean.TRUE.equals(info.getExported())) {
                sb.append(" [exported]");
            }
            return sb.toString();
        }

        private static String kindBadge(AndroidComponentInfo.Kind k) {
            switch (k) {
                case ACTIVITY: return "[A]";
                case SERVICE: return "[S]";
                case RECEIVER: return "[R]";
                case PROVIDER: return "[P]";
                default: return "[?]";
            }
        }
    }

    /** uses-permission ノード値。 */
    static final class PermissionEntry {
        final AndroidPermissionInfo info;

        PermissionEntry(AndroidPermissionInfo info) {
            this.info = info;
        }

        @Override
        public String toString() {
            return info.getShortName();
        }
    }

    /** uses-feature ノード値。 */
    static final class FeatureEntry {
        final String name;

        FeatureEntry(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        }
    }

    /**
     * メソッドノード選択時のコールバックに渡される値。
     * シーケンス図起点として {@code owner.getSimpleName() + "." + method.getName()} を組み立てれば良い。
     */
    public static final class MethodSelection {
        private final JavaClassInfo owner;
        private final JavaMethodInfo method;

        public MethodSelection(JavaClassInfo owner, JavaMethodInfo method) {
            this.owner = owner;
            this.method = method;
        }

        public JavaClassInfo getOwner() {
            return owner;
        }

        public JavaMethodInfo getMethod() {
            return method;
        }

        /** {@code "Class.method"} 形式のシーケンス図起点文字列。 */
        public String getEntry() {
            return owner.getSimpleName() + "." + method.getName();
        }
    }
}
