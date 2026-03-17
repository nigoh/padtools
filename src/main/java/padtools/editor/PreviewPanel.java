package padtools.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
    private BufferedView view = null;
    private final Model2View model2View;

    // ズーム・パン
    private double zoomLevel = 1.0;
    private double panX = 0;
    private double panY = 0;
    private Point dragStart = null;

    // 元の図のサイズ（ズーム前）
    private double viewWidth = 0;
    private double viewHeight = 0;

    public PreviewPanel(Model2View model2View) {
        this.model2View = model2View;
        setupZoomPan();
    }

    private void setupZoomPan() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double delta = e.getWheelRotation() < 0 ? 0.1 : -0.1;
                double newZoom = Math.max(0.25, Math.min(4.0, zoomLevel + delta));
                if (newZoom != zoomLevel) {
                    zoomLevel = newZoom;
                    updatePreferredSize();
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2
                        || (e.getButton() == MouseEvent.BUTTON1 && e.isControlDown())) {
                    dragStart = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    panX += e.getX() - dragStart.x;
                    panY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    @Override
    protected void paintComponent(Graphics grphcs) {
        Graphics2D g = (Graphics2D) grphcs;
        Dimension s = getSize();
        g.setPaint(new GradientPaint(new Point(), Color.white, new Point(s.width, s.height), Color.lightGray));
        g.fillRect(0, 0, s.width, s.height);
        if (view != null) {
            AffineTransform old = g.getTransform();
            g.translate(panX, panY);
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

        Point2D.Double sz = view.getSize(graphics);
        viewWidth = sz.x;
        viewHeight = sz.y;
        updatePreferredSize();
    }

    private void updatePreferredSize() {
        int w = (int) (viewWidth * zoomLevel + panX);
        int h = (int) (viewHeight * zoomLevel + panY);
        Dimension d = new Dimension(Math.max(w, 1), Math.max(h, 1));
        setSize(d);
        setPreferredSize(d);
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
        zoomLevel = Math.max(0.25, Math.min(4.0, level));
        panX = 0;
        panY = 0;
        updatePreferredSize();
    }

    public void adjustZoom(double delta) {
        setZoomLevel(zoomLevel + delta);
    }

    public void zoomToFit() {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        JScrollPane scrollParent = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scrollParent != null) {
            Dimension vpSize = scrollParent.getViewport().getSize();
            double scaleX = vpSize.getWidth() / viewWidth;
            double scaleY = vpSize.getHeight() / viewHeight;
            zoomLevel = Math.max(0.25, Math.min(4.0, Math.min(scaleX, scaleY)));
        } else {
            zoomLevel = 1.0;
        }
        panX = 0;
        panY = 0;
        updatePreferredSize();
    }
}
