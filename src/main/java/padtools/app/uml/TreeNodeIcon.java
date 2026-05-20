package padtools.app.uml;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * ツリーセル横に表示する小さな形状アイコン。
 *
 * <p><b>形状でカテゴリを識別する</b>設計になっている:
 * <ul>
 *   <li>六角形 (HEXAGON) — 構造: Module / Package</li>
 *   <li>四角 (SQUARE) — Java 型: Class / Interface / Enum / Annotation / AIDL</li>
 *   <li>円 (CIRCLE) — メソッド・図種: Method / Sequence / Activity</li>
 *   <li>角丸四角 (ROUNDED_RECT) — Android: Manifest / コンポーネント / Permission / Feature</li>
 * </ul>
 * 同カテゴリ内の区別には色を使う。</p>
 */
public final class TreeNodeIcon implements Icon {

    public enum Shape {
        CIRCLE, SQUARE, HEXAGON, ROUNDED_RECT
    }

    // ── 構造ノード (HEXAGON) ─────────────────────────────────────
    /** モジュールノード: スチールブルーの六角形 */
    public static final TreeNodeIcon MODULE =
            new TreeNodeIcon(Shape.HEXAGON, new Color(0x546E7A), 13);
    /** パッケージノード: 茶色の六角形 */
    public static final TreeNodeIcon PACKAGE =
            new TreeNodeIcon(Shape.HEXAGON, new Color(0x795548), 13);

    // ── Java 型ノード (SQUARE) ───────────────────────────────────
    /** クラス (C): 青い四角 */
    public static final TreeNodeIcon CLASS =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x1565C0), 11);
    /** インターフェース (I): 緑の四角 */
    public static final TreeNodeIcon INTERFACE =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x2E7D32), 11);
    /** 列挙型 (E): 紫の四角 */
    public static final TreeNodeIcon ENUM =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x6A1B9A), 11);
    /** アノテーション (@): オレンジの四角 */
    public static final TreeNodeIcon ANNOTATION =
            new TreeNodeIcon(Shape.SQUARE, new Color(0xE65100), 11);
    /** AIDL インターフェース: ティールの四角 */
    public static final TreeNodeIcon AIDL =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x00695C), 11);

    // ── メソッド / 図種ノード (CIRCLE) ──────────────────────────
    /** メソッド: 青灰色の小さい円 */
    public static final TreeNodeIcon METHOD =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x607D8B), 9);
    /** シーケンス図: 赤い円 */
    public static final TreeNodeIcon SEQUENCE =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0xE53935), 12);
    /** アクティビティ図: 青い円 */
    public static final TreeNodeIcon ACTIVITY =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x1E88E5), 12);

    // ── Android / Manifest (ROUNDED_RECT) ───────────────────────
    /** AndroidManifest.xml ノード: 緑の角丸四角 */
    public static final TreeNodeIcon MANIFEST =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x2E7D32), 13);
    /** コンポーネントグループ (Activities/Services/...): 青灰色の角丸四角 */
    public static final TreeNodeIcon COMPONENT_GROUP =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x546E7A), 11);
    /** Android Activity コンポーネント: オレンジの角丸四角 */
    public static final TreeNodeIcon COMPONENT_ACTIVITY =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xF57C00), 11);
    /** Android Service コンポーネント: インディゴの角丸四角 */
    public static final TreeNodeIcon COMPONENT_SERVICE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x3949AB), 11);
    /** Android BroadcastReceiver コンポーネント: 紫の角丸四角 */
    public static final TreeNodeIcon COMPONENT_RECEIVER =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x7B1FA2), 11);
    /** Android ContentProvider コンポーネント: 緑の角丸四角 */
    public static final TreeNodeIcon COMPONENT_PROVIDER =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x388E3C), 11);
    /** uses-permission ノード: 赤の角丸四角 */
    public static final TreeNodeIcon PERMISSION =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xC62828), 11);
    /** uses-feature ノード: 黄色の角丸四角 */
    public static final TreeNodeIcon FEATURE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0xF9A825), 11);

    // ─────────────────────────────────────────────────────────────

    private final Shape shape;
    private final Color color;
    private final int size;

    public TreeNodeIcon(Shape shape, Color color, int size) {
        this.shape = shape;
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
            switch (shape) {
                case CIRCLE:
                    g2.fillOval(x, y, size, size);
                    break;
                case SQUARE:
                    g2.fillRect(x, y, size, size);
                    break;
                case ROUNDED_RECT:
                    g2.fillRoundRect(x, y, size, size, 5, 5);
                    break;
                case HEXAGON:
                    g2.fillPolygon(hexagonPoints(x, y, size, true),
                                   hexagonPoints(x, y, size, false), 6);
                    break;
            }
        } finally {
            g2.dispose();
        }
    }

    /** 六角形 (pointy-top) の頂点座標を返す。forX=true で x 配列、false で y 配列。 */
    private static int[] hexagonPoints(int ox, int oy, int size, boolean forX) {
        int cx = ox + size / 2;
        int cy = oy + size / 2;
        int r  = size / 2;
        int[] coords = new int[6];
        for (int i = 0; i < 6; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 3;
            coords[i] = forX
                    ? (int) Math.round(cx + r * Math.cos(angle))
                    : (int) Math.round(cy + r * Math.sin(angle));
        }
        return coords;
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }
}
