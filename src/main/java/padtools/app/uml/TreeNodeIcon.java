package padtools.app.uml;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * ツリーセル横に表示する小さな形状アイコン。
 *
 * <p>カテゴリ別に形状と色を変えることでノード種別を視覚的に区別する:
 * <ul>
 *   <li>構造 (Module / Package): 角丸四角</li>
 *   <li>Java 型 (Class / Interface / Enum / Annotation / AIDL): 四角・円・菱形</li>
 *   <li>メソッド: 小さい円</li>
 *   <li>図種 (Sequence / Activity): 塗りつぶし円</li>
 *   <li>Android (Manifest / コンポーネント / Permission / Feature): 各種形状</li>
 * </ul>
 * </p>
 */
public final class TreeNodeIcon implements Icon {

    public enum Shape {
        CIRCLE, SQUARE, DIAMOND, ROUNDED_RECT, TRIANGLE
    }

    // ── 構造ノード ──────────────────────────────────────────────
    /** モジュールノード: 青灰色の角丸四角 */
    public static final TreeNodeIcon MODULE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x546E7A), 13);
    /** パッケージノード: 茶色の角丸四角 */
    public static final TreeNodeIcon PACKAGE =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x795548), 13);

    // ── Java 型ノード ────────────────────────────────────────────
    /** クラス (C): 青い四角 */
    public static final TreeNodeIcon CLASS =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x1565C0), 11);
    /** インターフェース (I): 緑の円 */
    public static final TreeNodeIcon INTERFACE =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x2E7D32), 11);
    /** 列挙型 (E): 紫の菱形 */
    public static final TreeNodeIcon ENUM =
            new TreeNodeIcon(Shape.DIAMOND, new Color(0x6A1B9A), 11);
    /** アノテーション (@): オレンジの菱形 */
    public static final TreeNodeIcon ANNOTATION =
            new TreeNodeIcon(Shape.DIAMOND, new Color(0xE65100), 11);
    /** AIDL インターフェース: ティールの四角 */
    public static final TreeNodeIcon AIDL =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x00695C), 11);

    // ── メソッドノード ────────────────────────────────────────────
    /** メソッド: 青灰色の小さい円 */
    public static final TreeNodeIcon METHOD =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x607D8B), 9);

    // ── 図種リーフ ─────────────────────────────────────────────
    /** シーケンス図: 赤い円 */
    public static final TreeNodeIcon SEQUENCE =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0xE53935), 12);
    /** アクティビティ図: 青い円 */
    public static final TreeNodeIcon ACTIVITY =
            new TreeNodeIcon(Shape.CIRCLE, new Color(0x1E88E5), 12);

    // ── Android / Manifest ────────────────────────────────────────
    /** AndroidManifest.xml ノード: 緑の角丸四角 */
    public static final TreeNodeIcon MANIFEST =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x2E7D32), 13);
    /** コンポーネントグループ (Activities/Services/...): 青灰色の角丸四角 */
    public static final TreeNodeIcon COMPONENT_GROUP =
            new TreeNodeIcon(Shape.ROUNDED_RECT, new Color(0x546E7A), 11);
    /** Android Activity コンポーネント: オレンジの四角 */
    public static final TreeNodeIcon COMPONENT_ACTIVITY =
            new TreeNodeIcon(Shape.SQUARE, new Color(0xF57C00), 11);
    /** Android Service コンポーネント: インディゴの四角 */
    public static final TreeNodeIcon COMPONENT_SERVICE =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x3949AB), 11);
    /** Android BroadcastReceiver コンポーネント: 紫の四角 */
    public static final TreeNodeIcon COMPONENT_RECEIVER =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x7B1FA2), 11);
    /** Android ContentProvider コンポーネント: 緑の四角 */
    public static final TreeNodeIcon COMPONENT_PROVIDER =
            new TreeNodeIcon(Shape.SQUARE, new Color(0x388E3C), 11);
    /** uses-permission ノード: 赤い三角 */
    public static final TreeNodeIcon PERMISSION =
            new TreeNodeIcon(Shape.TRIANGLE, new Color(0xC62828), 11);
    /** uses-feature ノード: 黄色の菱形 */
    public static final TreeNodeIcon FEATURE =
            new TreeNodeIcon(Shape.DIAMOND, new Color(0xF9A825), 11);

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
                    g2.fillRoundRect(x, y, size, size, 4, 4);
                    break;
                case DIAMOND: {
                    int cx = x + size / 2;
                    int cy = y + size / 2;
                    int r = size / 2;
                    int[] xs = { cx, cx + r, cx, cx - r };
                    int[] ys = { cy - r, cy, cy + r, cy };
                    g2.fillPolygon(xs, ys, 4);
                    break;
                }
                case TRIANGLE: {
                    int[] xs = { x + size / 2, x + size, x };
                    int[] ys = { y, y + size, y + size };
                    g2.fillPolygon(xs, ys, 3);
                    break;
                }
            }
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
