// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * ツリーセル横に表示する小さな色付き丸アイコン。
 *
 * <p>シーケンス図リーフは {@link #SEQUENCE} (赤)、アクティビティ図リーフは
 * {@link #ACTIVITY} (青) を使う。{@link ProjectTreeCellRenderer} から
 * {@code setIcon()} で渡される。</p>
 */
public final class DiagramDotIcon implements Icon {

    /** シーケンス図リーフ用の赤丸アイコン。 */
    public static final DiagramDotIcon SEQUENCE = new DiagramDotIcon(new Color(0xE53935), 12);
    /** アクティビティ図リーフ用の青丸アイコン。 */
    public static final DiagramDotIcon ACTIVITY = new DiagramDotIcon(new Color(0x1E88E5), 12);

    private final Color color;
    private final int size;

    public DiagramDotIcon(Color color, int size) {
        this.color = color;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y, size, size);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
