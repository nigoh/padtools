package padtools.editor;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.PADModel;
import padtools.core.view.BufferedView;
import padtools.core.view.Model2View;

/**
 * PAD図のプレビューを表示するパネル。ズーム・パン機能付き。
 */
public class PreviewPanel extends JPanel {
    private static final double ZOOM_MIN = 0.25;
    private static final double ZOOM_MAX = 4.0;
    private static final double WHEEL_ZOOM_FACTOR = 1.1;

    private BufferedView view = null;
    private final Model2View model2View;

    private double zoomLevel = 1.0;

    private double viewWidth = 0;
    private double viewHeight = 0;

    private Point dragStart = null;
    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private final Cursor grabCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    private final Cursor defaultCursor = Cursor.getDefaultCursor();

    private Runnable zoomChangeListener;

    public PreviewPanel(Model2View model2View) {
        this.model2View = model2View;
        setBackground(new Color(0xF7, 0xF7, 0xF7));
        setupZoomPan();
    }

    public void setZoomChangeListener(Runnable listener) {
        this.zoomChangeListener = listener;
    }

    private void notifyZoomChange() {
        if (zoomChangeListener != null) {
            zoomChangeListener.run();
        }
    }

    private void setupZoomPan() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                boolean zoomModifier = (e.getModifiersEx()
                        & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
                if (!zoomModifier) {
                    // 親のスクロールに委譲する（通常のスクロール挙動）
                    JScrollPane sp = getParentScrollPane();
                    if (sp != null) {
                        sp.dispatchEvent(SwingUtilities.convertMouseEvent(PreviewPanel.this, e, sp));
                    }
                    return;
                }
                double factor = e.getWheelRotation() < 0 ? WHEEL_ZOOM_FACTOR : 1.0 / WHEEL_ZOOM_FACTOR;
                zoomAt(e.getPoint(), zoomLevel * factor);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)
                        || (SwingUtilities.isLeftMouseButton(e) && view != null)) {
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
                if (sp != null) {
                    JViewport vp = sp.getViewport();
                    Point pos = vp.getViewPosition();
                    pos.translate(dragStart.x - e.getX(), dragStart.y - e.getY());
                    Dimension viewSize = getSize();
                    Dimension extent = vp.getExtentSize();
                    pos.x = Math.max(0, Math.min(pos.x, viewSize.width - extent.width));
                    pos.y = Math.max(0, Math.min(pos.y, viewSize.height - extent.height));
                    vp.setViewPosition(pos);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                setCursor(view != null ? handCursor : defaultCursor);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(view != null ? handCursor : defaultCursor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (dragStart == null) {
                    setCursor(defaultCursor);
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    private JScrollPane getParentScrollPane() {
        return (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    }

    /**
     * パネル座標 anchor が画面上で動かないようにスクロール位置を補正しつつズームを変更する。
     */
    private void zoomAt(Point anchor, double targetZoom) {
        double newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, targetZoom));
        if (Math.abs(newZoom - zoomLevel) < 1e-6) {
            return;
        }
        double ratio = newZoom / zoomLevel;
        zoomLevel = newZoom;
        updatePreferredSize();

        JScrollPane sp = getParentScrollPane();
        if (sp != null && anchor != null) {
            JViewport vp = sp.getViewport();
            Point pos = vp.getViewPosition();
            // anchor はパネル座標。倍率変更後 anchor は (anchor * ratio) に移る。
            // ビューポート内の相対位置を維持するため、ビューポート位置を (ratio - 1) 分シフトする。
            int newX = (int) Math.round(anchor.x * (ratio - 1) + pos.x);
            int newY = (int) Math.round(anchor.y * (ratio - 1) + pos.y);
            Dimension viewSize = getPreferredSize();
            Dimension extent = vp.getExtentSize();
            newX = Math.max(0, Math.min(newX, Math.max(0, viewSize.width - extent.width)));
            newY = Math.max(0, Math.min(newY, Math.max(0, viewSize.height - extent.height)));
            vp.setViewPosition(new Point(newX, newY));
        }
        notifyZoomChange();
    }

    @Override
    protected void paintComponent(Graphics grphcs) {
        super.paintComponent(grphcs);
        Graphics2D g = (Graphics2D) grphcs;
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (view != null) {
            AffineTransform old = g.getTransform();
            g.scale(zoomLevel, zoomLevel);
            view.draw(g, new Point2D.Double());
            g.setTransform(old);
        }
    }

    /**
     * SPDテキストを解析し、プレビューを更新する。
     */
    public void refresh(String spdText, SPDEditor editor, JList<String> messageList, Graphics2D graphics) {
        ((DefaultListModel<String>) messageList.getModel()).removeAllElements();
        editor.refreshHighlight();

        final PADModel model = SPDParser.parse(spdText, new ParseErrorReceiver() {
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                ((DefaultListModel<String>) messageList.getModel()).addElement(String.format("line %d, %s", lineNo + 1, err.getUserMessage()));
                editor.setErrorLine(lineNo);
                return true;
            }
        });

        view = new BufferedView(model2View.toView(model), true);
        editor.setEdited(false);

        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        try {
            Point2D.Double sz = view.getSize(tempGraphics);
            viewWidth = sz.x;
            viewHeight = sz.y;
        } finally {
            tempGraphics.dispose();
            tempImage.flush();
        }
        updatePreferredSize();
        if (view != null) {
            setCursor(handCursor);
        }
    }

    private void updatePreferredSize() {
        int w = (int) Math.ceil(viewWidth * zoomLevel);
        int h = (int) Math.ceil(viewHeight * zoomLevel);
        Dimension d = new Dimension(Math.max(w, 1), Math.max(h, 1));
        setPreferredSize(d);
        setSize(d);
        revalidate();
        repaint();
    }

    public BufferedView getBufferedView() {
        return view;
    }

    public Rectangle getViewBounds() {
        return getBounds();
    }

    // --- ズーム制御 ---

    public double getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(double level) {
        zoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, level));
        updatePreferredSize();
        notifyZoomChange();
    }

    public void adjustZoom(double delta) {
        Point anchor = viewportCenter();
        zoomAt(anchor, zoomLevel + delta);
    }

    public void zoomToFit() {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        JScrollPane scrollParent = getParentScrollPane();
        if (scrollParent != null) {
            Dimension vpSize = scrollParent.getViewport().getSize();
            double scaleX = vpSize.getWidth() / viewWidth;
            double scaleY = vpSize.getHeight() / viewHeight;
            zoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.min(scaleX, scaleY)));
        } else {
            zoomLevel = 1.0;
        }
        updatePreferredSize();
        JScrollPane sp = getParentScrollPane();
        if (sp != null) {
            sp.getViewport().setViewPosition(new Point(0, 0));
        }
        notifyZoomChange();
    }

    private Point viewportCenter() {
        JScrollPane sp = getParentScrollPane();
        if (sp == null) {
            return new Point(getWidth() / 2, getHeight() / 2);
        }
        JViewport vp = sp.getViewport();
        Point pos = vp.getViewPosition();
        Dimension extent = vp.getExtentSize();
        return new Point(pos.x + extent.width / 2, pos.y + extent.height / 2);
    }
}
