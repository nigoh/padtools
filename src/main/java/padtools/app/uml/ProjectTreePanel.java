package padtools.app.uml;

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
    private java.util.function.Consumer<String> onPackageSelected;

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
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public void setOnClassSelected(java.util.function.Consumer<JavaClassInfo> listener) {
        this.onClassSelected = listener;
    }

    public void setOnMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onMethodSelected = listener;
    }

    /** パッケージノード右クリック → ドリルダウン用ハンドラ。 */
    public void setOnPackageSelected(java.util.function.Consumer<String> listener) {
        this.onPackageSelected = listener;
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
        for (Map.Entry<String, Map<String, List<JavaClassInfo>>> me : byModule.entrySet()) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                    new ModuleEntry(me.getKey()));
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
        // モジュールノードを開いておく
        for (int i = 0; i < root.getChildCount(); i++) {
            tree.expandRow(i);
        }
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
            if (u instanceof MethodEntry) {
                MethodEntry me = (MethodEntry) u;
                if (onMethodSelected != null) {
                    onMethodSelected.accept(new MethodSelection(me.owner, me.method));
                }
                if (onClassSelected != null) {
                    onClassSelected.accept(me.owner);
                }
                return;
            }
            if (u instanceof ClassEntry) {
                if (onClassSelected != null) {
                    onClassSelected.accept(((ClassEntry) u).info);
                }
                return;
            }
        }
        if (onClassSelected != null) {
            onClassSelected.accept(null);
        }
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
            classNode.add(new DefaultMutableTreeNode(new MethodEntry(c, m)));
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
    private static final class ModuleEntry {
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
    private static final class PackageEntry {
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
    private static final class ClassEntry {
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
    private static final class MethodEntry {
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

    /** 遅延構築前のダミー子ノード値。「+」表示を出すためだけに使う。 */
    private static final class LazyPlaceholder {
        static final LazyPlaceholder INSTANCE = new LazyPlaceholder();

        @Override
        public String toString() {
            return "...";
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
