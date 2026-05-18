package padtools.app.uml;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.Visibility;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
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
 * {@link AndroidProjectAnalysis#getGradleByModule()} を起点とし、Gradle 解析で
 * 見つからなかったクラスは <em>(other)</em> モジュール下に集約する。
 * メソッドノードは抽象メソッドを除いた、シーケンス図起点として使えるものだけを並べる。</p>
 *
 * <p>選択イベントは {@link #setOnClassSelected(java.util.function.Consumer)} と
 * {@link #setOnMethodSelected(java.util.function.Consumer)} で受け取れる。
 * クラス選択時はクラス情報のみ、メソッド選択時は {@link MethodSelection}
 * (クラス + メソッド) を通知し、呼び出し側でシーケンス図の起点切り替えに利用する。</p>
 */
public class ProjectTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode root;
    private java.util.function.Consumer<JavaClassInfo> onClassSelected;
    private java.util.function.Consumer<MethodSelection> onMethodSelected;

    public ProjectTreePanel() {
        super(new BorderLayout());
        root = new DefaultMutableTreeNode("(no project)");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> notifySelection());
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public void setOnClassSelected(java.util.function.Consumer<JavaClassInfo> listener) {
        this.onClassSelected = listener;
    }

    public void setOnMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onMethodSelected = listener;
    }

    /** 解析結果からツリーを再構築する。 */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName) {
        root.setUserObject(projectName != null ? projectName : "(project)");
        root.removeAllChildren();
        // モジュール → パッケージ → クラスを集計
        Map<String, Map<String, List<JavaClassInfo>>> byModule = groupByModule(analysis, classes);
        for (Map.Entry<String, Map<String, List<JavaClassInfo>>> me : byModule.entrySet()) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                    new ModuleEntry(me.getKey()));
            for (Map.Entry<String, List<JavaClassInfo>> pe : me.getValue().entrySet()) {
                DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(
                        new PackageEntry(pe.getKey(), pe.getValue().size()));
                for (JavaClassInfo c : pe.getValue()) {
                    DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                            new ClassEntry(c));
                    addMethodNodes(classNode, c);
                    pkgNode.add(classNode);
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

    private static void addMethodNodes(DefaultMutableTreeNode classNode, JavaClassInfo c) {
        for (JavaMethodInfo m : c.getMethods()) {
            // 抽象メソッドはシーケンス図起点にならないので除外
            if (m.isAbstract()) {
                continue;
            }
            classNode.add(new DefaultMutableTreeNode(new MethodEntry(c, m)));
        }
    }

    private static Map<String, Map<String, List<JavaClassInfo>>> groupByModule(
            AndroidProjectAnalysis analysis, List<JavaClassInfo> classes) {
        Map<String, Map<String, List<JavaClassInfo>>> out = new LinkedHashMap<>();
        // 解析が無いか空ならクラスのみのフラット集計に fallback
        Map<String, String> packageToModule = new LinkedHashMap<>();
        if (analysis != null) {
            for (String mod : analysis.getGradleByModule().keySet()) {
                out.computeIfAbsent(mod, k -> new TreeMap<>());
            }
        }
        for (JavaClassInfo c : classes) {
            String mod = packageToModule.getOrDefault(c.getPackageName(), "(other)");
            // 解析からモジュール推定する手段が現状 1:1 で無いため、すべて (other) に入れる
            // (クラスとモジュールの紐付けは UmlGenerator 側で行われていない)。
            out.computeIfAbsent(mod, k -> new TreeMap<>())
                    .computeIfAbsent(c.getPackageName().isEmpty()
                            ? "(default)" : c.getPackageName(),
                            k -> new java.util.ArrayList<>())
                    .add(c);
        }
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

    /** パッケージ名 + 含まれるクラス数を保持するノード値。 */
    private static final class PackageEntry {
        final String name;
        final int count;

        PackageEntry(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    /** クラス情報を保持するノード値。表示はシンプル名のみ。 */
    private static final class ClassEntry {
        final JavaClassInfo info;

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
