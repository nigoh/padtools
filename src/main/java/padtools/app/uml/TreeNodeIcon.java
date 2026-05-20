package padtools.app.uml;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * ツリーセル横に表示する小さなアイコン。
 *
 * <p><b>形状でカテゴリを識別する</b>設計:
 * <ul>
 *   <li>六角形 (HEXAGON) — 構造: Module / Package</li>
 *   <li>文字バッジ (BADGE) — Java 型: C=Class / I=Interface / E=Enum / @=Annotation / A=AIDL</li>
 *   <li>円 (CIRCLE) — メソッド・図種: Method / Sequence / Activity</li>
 *   <li>角丸四角 (ROUNDED_RECT) — Android: Manifest / コンポーネント / Permission / Feature</li>
 * </ul>
 * 同カテゴリ内の区別には色を使う。</p>
 */
public final class TreeNodeIcon implements Icon {

    private enum Shape { CIRCLE, BADGE, HEXAGON, ROUNDED_RECT }

    // ── 構造ノード (HEXAGON) ─────────────────────────────────────
    /** モジュールノード: スチールブルーの六角形 */
    public static final TreeNodeIcon MODULE =
            new TreeNodeIcon(Shape.HEXAGON, new Color(0x546E7A), null, 13);
    /** パッケージノード: 茶色の六角形 */
    public static final TreeNodeIcon PACKAGE =
            new TreeNodeIcon(Shape.HEXAGON, new Color(0x795548), null, 13);

    // ── Java 型ノード (BADGE: 色背景 + 白い 1 文字) ─────────────
    /** クラス: 青バッジ "C" */
    public static final TreeNodeIcon CLASS =
            new TreeNodeIcon(Shape.BADGE, new Color(0x1565C0), "C", 13);
    /** インターフェース: 緑バッジ "I" */
    public static final TreeNodeIcon INTERFACE =
            new TreeNodeIcon(Shape.BADGE, new Color(0x2E7D32), "I", 13);
    /** 列挙型: 紫バッジ "E" */
    public static final TreeNodeIcon ENUM =
            new TreeNodeIcon(Shape.BADGE, new Color(0x6A1B9A), "E", 13);
    /** アノテーション: オレンジバッジ "@" */
    public static final TreeNodeIcon ANNOTATION =
            new TreeNodeIcon(Shape.BADGE, new Color(0xE65100), "@", 13);
    /** AIDL インターフェース: ティールバッジ "A" */
    public static final TreeNodeIcon AIDL =
            new TreeNodeIcon(Shape.BADGE, new Color(0x00695C), "A", 13);

    // ── メソッド / 図種ノード (CIRCLE) ──────────────────────────
    /** メソッド: 青灰色の小さい円 */
    public static final TreeNodeIcon METHOD =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x607D8B), null, 9);
    /** シーケンス図: 赤い円 */
    public static final TreeNodeIcon SEQUENCE =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0xE53935), null, 12);
    /** アクティビティ図: 青い円 */
    public static final TreeNodeIcon ACTIVITY =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x1E88E5), null, 12);

    // ── Android / Manifest (ROUNDED_RECT) ───────────────────────
    /** AndroidManifest.xml ノード: 緑の角丸四角 */
    public static final TreeNodeIcon MANIFEST =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x2E7D32), null, 13);
    /** コンポーネントグループ: 青灰色の角丸四角 */
    public static final TreeNodeIcon COMPONENT_GROUP =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x546E7A), null, 11);
    /** Android Activity コンポーネント: オレンジの角丸四角 */
    public static final TreeNodeIcon COMPONENT_ACTIVITY =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xF57C00), null, 11);
    /** Android Service コンポーネント: インディゴの角丸四角 */
    public static final TreeNodeIcon COMPONENT_SERVICE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x3949AB), null, 11);
    /** Android BroadcastReceiver: 紫の角丸四角 */
    public static final TreeNodeIcon COMPONENT_RECEIVER =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x7B1FA2), null, 11);
    /** Android ContentProvider: 緑の角丸四角 */
    public static final TreeNodeIcon COMPONENT_PROVIDER =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x388E3C), null, 11);
    /** uses-permission: 赤の角丸四角 */
    public static final TreeNodeIcon PERMISSION =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xC62828), null, 11);
    /** uses-feature: 黄色の角丸四角 */
    public static final TreeNodeIcon FEATURE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xF9A825), null, 11);

    // ─────────────────────────────────────────────────────────────

    private static final Color BADGE_TEXT = Color.WHITE;

    private final Shape shape;
    private final Color color;
    private final String letter; // BADGE 専用
    private final int size;

    private TreeNodeIcon(Shape shape, Color color, String letter, int size) {
        this.shape  = shape;
        this.color  = color;
        this.letter = letter;
        this.size   = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            switch (shape) {
                case CIRCLE:
                    g2.fillOval(x, y, size, size);
                    break;
                case ROUNDED_RECT:
                    g2.fillRoundRect(x, y, size, size, 5, 5);
                    break;
                case HEXAGON:
                    g2.fillPolygon(hexPoints(x, y, true), hexPoints(x, y, false), 6);
                    break;
                case BADGE:
                    g2.fillRoundRect(x, y, size, size, 4, 4);
                    if (letter != null) {
                        Font font = new Font(Font.SANS_SERIF, Font.BOLD, size - 4);
                        g2.setFont(font);
                        FontMetrics fm = g2.getFontMetrics();
                        int tx = x + (size - fm.stringWidth(letter)) / 2;
                        int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2 - 1;
                        g2.setColor(BADGE_TEXT);
                        g2.drawString(letter, tx, ty);
                    }
                    break;
            }
        } finally {
            g2.dispose();
        }
    }

    /** 六角形 (pointy-top) の頂点座標。forX=true で x 配列、false で y 配列。 */
    private int[] hexPoints(int ox, int oy, boolean forX) {
        int cx = ox + size / 2;
        int cy = oy + size / 2;
        int r  = size / 2;
        int[] coords = new int[6];
        for (int i = 0; i < 6; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 3;
            coords[i] = forX
                    ? (int) Math.round(cx + r * Math.cos(a))
                    : (int) Math.round(cy + r * Math.sin(a));
        }
        return coords;
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }
}
