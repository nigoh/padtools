package padtools.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * {@link BufferedImage} (PlantUML レンダリング結果) をズーム・パン対応で表示するパネル。
 *
 * <p>操作:</p>
 * <ul>
 *   <li>Ctrl + ホイール: ポインタ位置を基点にズームイン/アウト</li>
 *   <li>左ドラッグ / 中ボタンドラッグ: パン</li>
 *   <li>ホイールのみ (Ctrl 無し): 親 {@link JScrollPane} のスクロールに委譲</li>
 * </ul>
 *
 * <p>ズーム範囲は {@value #ZOOM_MIN} 〜 {@value #ZOOM_MAX}。倍率変更は
 * {@link #setZoomChangeListener(Runnable)} で通知できる。</p>
 */
public class SvgPreviewPanel extends JPanel {

    private static final double ZOOM_MIN = 0.1;
    private static final double ZOOM_MAX = 8.0;
    private static final double WHEEL_ZOOM_FACTOR = 1.1;

    private BufferedImage image;
    private double zoomLevel = 1.0;

    private Point dragStart;
    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private final Cursor grabCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    private final Cursor defaultCursor = Cursor.getDefaultCursor();

    private Runnable zoomChangeListener;

    public SvgPreviewPanel() {
        setBackground(new Color(0xF7, 0xF7, 0xF7));
        setupMouseHandlers();
    }

    public void setZoomChangeListener(Runnable listener) {
        this.zoomChangeListener = listener;
    }

    private void notifyZoomChange() {
        if (zoomChangeListener != null) {
            zoomChangeListener.run();
        }
    }

    /** 表示する画像を差し替え、初回はサイズに合わせてフィットさせる。 */
    public void setImage(BufferedImage img) {
        this.image = img;
        if (img == null) {
            setPreferredSize(new Dimension(0, 0));
            revalidate();
            repaint();
            return;
        }
        // 倍率はそのまま維持 (連続更新でズーム位置を保つ)
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    public double getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(double zoom) {
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));
        if (Math.abs(clamped - zoomLevel) < 1e-6) {
            return;
        }
        zoomLevel = clamped;
        updatePreferredSize();
        revalidate();
        repaint();
        notifyZoomChange();
    }

    public void zoomIn() {
        setZoomLevel(zoomLevel * WHEEL_ZOOM_FACTOR);
    }

    public void zoomOut() {
        setZoomLevel(zoomLevel / WHEEL_ZOOM_FACTOR);
    }

    public void zoomReset() {
        setZoomLevel(1.0);
    }

    /** ビューポートに収まるように倍率を調整する。 */
    public void zoomToFit() {
        if (image == null) {
            return;
        }
        JViewport vp = getParentViewport();
        if (vp == null) {
            return;
        }
        Dimension extent = vp.getExtentSize();
        if (extent.width <= 0 || extent.height <= 0) {
            return;
        }
        double zx = (double) extent.width / image.getWidth();
        double zy = (double) extent.height / image.getHeight();
        setZoomLevel(Math.min(zx, zy));
    }

    private JViewport getParentViewport() {
        java.awt.Container p = getParent();
        if (p instanceof JViewport) {
            return (JViewport) p;
        }
        return null;
    }

    private JScrollPane getParentScrollPane() {
        JViewport vp = getParentViewport();
        if (vp == null) {
            return null;
        }
        java.awt.Container pp = vp.getParent();
        if (pp instanceof JScrollPane) {
            return (JScrollPane) pp;
        }
        return null;
    }

    private void updatePreferredSize() {
        if (image == null) {
            setPreferredSize(new Dimension(0, 0));
            return;
        }
        int w = (int) Math.max(1, image.getWidth() * zoomLevel);
        int h = (int) Math.max(1, image.getHeight() * zoomLevel);
        setPreferredSize(new Dimension(w, h));
    }

    /** マウスポインタ位置を画面上の同じ点に保ったままズームする。 */
    private void zoomAt(Point screenPos, double newZoom) {
        if (image == null) {
            return;
        }
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));
        if (Math.abs(clamped - zoomLevel) < 1e-6) {
            return;
        }
        JScrollPane sp = getParentScrollPane();
        JViewport vp = sp != null ? sp.getViewport() : null;
        Point oldViewPos = vp != null ? vp.getViewPosition() : new Point(0, 0);
        // パネル座標系 = ビューポート位置 + マウス位置 (パネル内)
        double panelX = screenPos.x;
        double panelY = screenPos.y;
        double imgX = panelX / zoomLevel;
        double imgY = panelY / zoomLevel;
        zoomLevel = clamped;
        updatePreferredSize();
        revalidate();
        repaint();
        notifyZoomChange();
        if (vp != null) {
            // 同じ画像座標がマウス位置にくるようビュー位置を調整
            int newViewX = (int) Math.round(imgX * zoomLevel - (panelX - oldViewPos.x));
            int newViewY = (int) Math.round(imgY * zoomLevel - (panelY - oldViewPos.y));
            Dimension size = getPreferredSize();
            Dimension extent = vp.getExtentSize();
            int maxX = Math.max(0, size.width - extent.width);
            int maxY = Math.max(0, size.height - extent.height);
            newViewX = Math.max(0, Math.min(maxX, newViewX));
            newViewY = Math.max(0, Math.min(maxY, newViewY));
            final int fx = newViewX;
            final int fy = newViewY;
            SwingUtilities.invokeLater(() -> vp.setViewPosition(new Point(fx, fy)));
        }
    }

    private void setupMouseHandlers() {
        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                boolean zoomModifier = (e.getModifiersEx()
                        & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
                if (!zoomModifier) {
                    // 親のスクロールに委譲
                    JScrollPane sp = getParentScrollPane();
                    if (sp != null) {
                        sp.dispatchEvent(SwingUtilities.convertMouseEvent(
                                SvgPreviewPanel.this, e, sp));
                    }
                    return;
                }
                double factor = e.getWheelRotation() < 0
                        ? WHEEL_ZOOM_FACTOR : 1.0 / WHEEL_ZOOM_FACTOR;
                zoomAt(e.getPoint(), zoomLevel * factor);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (image == null) {
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e)
                        || SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                    setCursor(grabCursor);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                JScrollPane sp = getParentScrollPane();
                if (sp == null) {
                    return;
                }
                JViewport vp = sp.getViewport();
                Point view = vp.getViewPosition();
                int dx = dragStart.x - e.getX();
                int dy = dragStart.y - e.getY();
                Dimension size = getPreferredSize();
                Dimension extent = vp.getExtentSize();
                int maxX = Math.max(0, size.width - extent.width);
                int maxY = Math.max(0, size.height - extent.height);
                int newX = Math.max(0, Math.min(maxX, view.x + dx));
                int newY = Math.max(0, Math.min(maxY, view.y + dy));
                vp.setViewPosition(new Point(newX, newY));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                setCursor(image != null ? handCursor : defaultCursor);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(image != null ? handCursor : defaultCursor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(defaultCursor);
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform tx = AffineTransform.getScaleInstance(zoomLevel, zoomLevel);
            g2.drawImage(image, tx, null);
        } finally {
            g2.dispose();
        }
    }

    /** 表示領域 (画面上の見えている範囲) のサイズ。 */
    public Rectangle getVisibleRect() {
        JViewport vp = getParentViewport();
        if (vp == null) {
            return new Rectangle(0, 0, getWidth(), getHeight());
        }
        Point pos = vp.getViewPosition();
        Dimension extent = vp.getExtentSize();
        return new Rectangle(pos.x, pos.y, extent.width, extent.height);
    }
}
