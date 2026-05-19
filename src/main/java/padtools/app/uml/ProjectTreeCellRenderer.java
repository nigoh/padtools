package padtools.app.uml;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * {@link ProjectTreePanel} 専用のセルレンダラ。
 *
 * <p>{@link ProjectTreePanel.MethodDiagramEntry} の図種に応じて
 * {@link DiagramDotIcon#SEQUENCE} / {@link DiagramDotIcon#ACTIVITY} を表示する。
 * それ以外のノードはデフォルトレンダリングのまま。</p>
 */
public final class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                   boolean sel, boolean expanded,
                                                   boolean leaf, int row,
                                                   boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            if (user instanceof ProjectTreePanel.MethodDiagramEntry) {
                ProjectTreePanel.MethodDiagramEntry e =
                        (ProjectTreePanel.MethodDiagramEntry) user;
                switch (e.kind) {
                    case SEQUENCE:
                        setIcon(DiagramDotIcon.SEQUENCE);
                        break;
                    case ACTIVITY:
                        setIcon(DiagramDotIcon.ACTIVITY);
                        break;
                    default:
                        // 他図種は今のところリーフ化しない
                        break;
                }
            }
        }
        return this;
    }
}
