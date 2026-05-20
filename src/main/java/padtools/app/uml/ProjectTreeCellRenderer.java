package padtools.app.uml;

import padtools.core.formats.android.AndroidComponentInfo;
import padtools.core.formats.uml.JavaClassInfo;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * {@link ProjectTreePanel} 専用のセルレンダラ。
 *
 * <p>ノード種別ごとに {@link TreeNodeIcon} から適切なアイコンを割り当てる:
 * <ul>
 *   <li>構造: Module (青灰色の角丸四角) / Package (茶色の角丸四角)</li>
 *   <li>Java 型: Class=青い四角 / Interface=緑の円 / Enum=紫の菱形 /
 *       Annotation=オレンジの菱形 / AIDL=ティールの四角</li>
 *   <li>メソッド: 青灰色の小さい円</li>
 *   <li>図種リーフ: Sequence=赤い円 / Activity=青い円</li>
 *   <li>Manifest 系: Manifest=緑の角丸四角 / グループ=青灰色の角丸四角 /
 *       Activity=オレンジ / Service=インディゴ / Receiver=紫 / Provider=緑 /
 *       Permission=赤い三角 / Feature=黄の菱形</li>
 * </ul>
 * </p>
 */
public final class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                   boolean sel, boolean expanded,
                                                   boolean leaf, int row,
                                                   boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (!(value instanceof DefaultMutableTreeNode)) {
            return this;
        }
        Object user = ((DefaultMutableTreeNode) value).getUserObject();

        if (user instanceof ProjectTreePanel.ModuleEntry) {
            setIcon(TreeNodeIcon.MODULE);
        } else if (user instanceof ProjectTreePanel.PackageEntry) {
            setIcon(TreeNodeIcon.PACKAGE);
        } else if (user instanceof ProjectTreePanel.ClassEntry) {
            setIcon(classIcon(((ProjectTreePanel.ClassEntry) user).info));
        } else if (user instanceof ProjectTreePanel.MethodEntry) {
            setIcon(TreeNodeIcon.METHOD);
        } else if (user instanceof ProjectTreePanel.MethodDiagramEntry) {
            setIcon(diagramIcon(((ProjectTreePanel.MethodDiagramEntry) user).kind));
        } else if (user instanceof ProjectTreePanel.ManifestEntry) {
            setIcon(TreeNodeIcon.MANIFEST);
        } else if (user instanceof ProjectTreePanel.ComponentGroupEntry) {
            setIcon(TreeNodeIcon.COMPONENT_GROUP);
        } else if (user instanceof ProjectTreePanel.ComponentEntry) {
            setIcon(componentIcon(((ProjectTreePanel.ComponentEntry) user).info.getKind()));
        } else if (user instanceof ProjectTreePanel.PermissionEntry) {
            setIcon(TreeNodeIcon.PERMISSION);
        } else if (user instanceof ProjectTreePanel.FeatureEntry) {
            setIcon(TreeNodeIcon.FEATURE);
        }

        return this;
    }

    private static TreeNodeIcon classIcon(JavaClassInfo info) {
        switch (info.getKind()) {
            case INTERFACE:      return TreeNodeIcon.INTERFACE;
            case ENUM:           return TreeNodeIcon.ENUM;
            case ANNOTATION:     return TreeNodeIcon.ANNOTATION;
            case AIDL_INTERFACE: return TreeNodeIcon.AIDL;
            default:             return TreeNodeIcon.CLASS;
        }
    }

    private static TreeNodeIcon diagramIcon(DiagramKind kind) {
        switch (kind) {
            case SEQUENCE: return TreeNodeIcon.SEQUENCE;
            case ACTIVITY: return TreeNodeIcon.ACTIVITY;
            default:       return TreeNodeIcon.METHOD;
        }
    }

    private static TreeNodeIcon componentIcon(AndroidComponentInfo.Kind kind) {
        switch (kind) {
            case ACTIVITY: return TreeNodeIcon.COMPONENT_ACTIVITY;
            case SERVICE:  return TreeNodeIcon.COMPONENT_SERVICE;
            case RECEIVER: return TreeNodeIcon.COMPONENT_RECEIVER;
            case PROVIDER: return TreeNodeIcon.COMPONENT_PROVIDER;
            default:       return TreeNodeIcon.COMPONENT_GROUP;
        }
    }
}
