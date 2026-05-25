// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import org.apache.batik.gvt.GraphicsNode;
import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.app.uml.PlantUmlSvgRenderer.SvgTextItem;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * PlantUML のレンダリング結果をズーム・パン対応で表示するパネル。
 *
 * <p>ベクター SVG ({@link GraphicsNode}) とラスタ {@link BufferedImage} の
 * どちらも表示できる。SVG モード ({@link #setSvgGraphicsNode}) では PlantUML の
 * 4096x4096 PNG キャンバス上限を回避でき、巨大な図でも切り詰められない。
 * 互換のため画像モード ({@link #setImage}) も維持している。</p>
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
    private GraphicsNode svgNode;
    private double svgWidth;
    private double svgHeight;
    private double zoomLevel = 1.0;

    private Point dragStart;
    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private final Cursor grabCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    private final Cursor defaultCursor = Cursor.getDefaultCursor();

    private Runnable zoomChangeListener;

    /** SVG 内のクリック可能リンク領域 ({@link PlantUmlSvgRenderer.LinkArea})。 */
    private List<LinkArea> linkAreas = Collections.emptyList();
    /** 右クリックでリンク領域にヒットしたときに呼ばれるリスナ。 */
    private BiConsumer<LinkArea, MouseEvent> linkPopupListener;
    /** 左クリックでリンク領域にヒットしたときに呼ばれるリスナ (ドリルダウン用)。 */
    private BiConsumer<LinkArea, MouseEvent> linkClickListener;

    /** SVG 内の全テキスト要素 (内容 + SVG 座標)。ラバーバンド選択のヒットテスト用。 */
    private List<SvgTextItem> svgTexts = Collections.emptyList();
    /** ラバーバンド選択の開始点 (パネル座標)。Alt+ドラッグで設定される。 */
    private Point selectionAnchor;
    /** ラバーバンド選択の現在終端 (パネル座標)。mouseDragged で更新される。 */
    private Point selectionCurrent;
    /** テキストをクリップボードへコピーしたときの通知リスナ (ステータスバー更新用)。 */
    private Consumer<String> copyFeedbackListener;

    public SvgPreviewPanel() {
        setBackground(new Color(0xF7, 0xF7, 0xF7));
        setupMouseHandlers();
        setupKeyBindings();
    }

    /**
     * SVG テキスト要素リストを設定する。ラバーバンド選択のヒットテストに使う。
     * {@code null} または空リストでクリア。
     */
    public void setTextItems(List<SvgTextItem> items) {
        this.svgTexts = (items == null || items.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * テキストをクリップボードへコピーしたときに呼ばれるリスナを設定する。
     * 引数は「コピーしたアイテム数」等のステータスメッセージ。{@code null} で解除。
     */
    public void setCopyFeedbackListener(Consumer<String> listener) {
        this.copyFeedbackListener = listener;
    }

    public void setZoomChangeListener(Runnable listener) {
        this.zoomChangeListener = listener;
    }

    private void notifyZoomChange() {
        if (zoomChangeListener != null) {
            zoomChangeListener.run();
        }
    }

    /**
     * ベクター SVG ({@link GraphicsNode}) を表示する。倍率は維持される。
     * {@code node} が {@code null} なら表示をクリアする。
     */
    public void setSvgGraphicsNode(GraphicsNode node, double width, double height) {
        this.svgNode = node;
        if (node == null) {
            this.svgWidth = 0;
            this.svgHeight = 0;
        } else {
            this.svgWidth = Math.max(1, width);
            this.svgHeight = Math.max(1, height);
        }
        // 同時に画像モードもクリアし、表示内容を一意にする
        this.image = null;
        // 旧 SVG の領域情報は無効化する (差し替え後の正しい値は別途 setLinkAreas で再設定)
        this.linkAreas = Collections.emptyList();
        this.svgTexts = Collections.emptyList();
        this.selectionAnchor = null;
        this.selectionCurrent = null;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    /**
     * SVG 内のクリック可能領域 ({@code [[url]]} 由来) を設定する。
     * {@code null} または空リストでクリア。
     */
    public void setLinkAreas(List<LinkArea> areas) {
        this.linkAreas = (areas == null || areas.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(areas));
        // リンク領域が空になった場合は人差し指アイコンを残さない
        if (this.linkAreas.isEmpty() && dragStart == null) {
            setCursor(defaultCursor);
        }
    }

    /** 現在保持しているリンク領域のリスト (never null)。 */
    public List<LinkArea> getLinkAreas() {
        return linkAreas;
    }

    /**
     * 右クリックでリンク領域にヒットしたときに呼ばれるリスナを登録する。
     * 第 1 引数はヒットした {@link LinkArea}、第 2 引数は元の {@link MouseEvent}。
     * {@code null} で解除。
     */
    public void setOnLinkPopup(BiConsumer<LinkArea, MouseEvent> listener) {
        this.linkPopupListener = listener;
    }

    /**
     * SVG リンク領域 ({@link LinkArea}) を左クリックしたときに呼ばれるリスナを設定する。
     * {@code null} で解除。ドリルダウン用 (左クリック = リンク先クラスへの遷移)。
     */
    public void setOnLinkClick(BiConsumer<LinkArea, MouseEvent> listener) {
        this.linkClickListener = listener;
    }

    /**
     * 指定したパネル座標で表示すべきカーソルを返す。
     * リンク領域 ({@link LinkArea}) 上では人差し指 (HAND_CURSOR)、
     * それ以外ではデフォルトカーソル。コンテンツ未表示時もデフォルト。
     */
    private Cursor cursorForPosition(Point p) {
        if (!hasContent()) {
            return defaultCursor;
        }
        if (linkAreas.isEmpty() || p == null) {
            return defaultCursor;
        }
        return hitTestLink(p) != null ? handCursor : defaultCursor;
    }

    /**
     * パネル座標 {@code p} に対応する {@link LinkArea} を返す。ヒットしなければ {@code null}。
     * SVG 座標系 = パネル座標 / zoomLevel。
     */
    LinkArea hitTestLink(Point p) {
        if (linkAreas.isEmpty() || zoomLevel <= 0) {
            return null;
        }
        double sx = p.x / zoomLevel;
        double sy = p.y / zoomLevel;
        // 後勝ち (内側の小さい矩形が後に追加されているとは限らないので) →
        // 最も内側に見えるよう、面積が小さい矩形を優先する
        LinkArea best = null;
        double bestArea = Double.POSITIVE_INFINITY;
        for (LinkArea a : linkAreas) {
            if (!a.contains(sx, sy)) {
                continue;
            }
            double area = a.getWidth() * a.getHeight();
            if (area < bestArea) {
                bestArea = area;
                best = a;
            }
        }
        return best;
    }

    public GraphicsNode getSvgGraphicsNode() {
        return svgNode;
    }

    /** 表示する画像 (PNG など) を差し替える。倍率は維持される。 */
    public void setImage(BufferedImage img) {
        this.image = img;
        // SVG モードと同居しないようクリア
        this.svgNode = null;
        this.svgWidth = 0;
        this.svgHeight = 0;
        this.linkAreas = Collections.emptyList();
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
        double iw = contentWidth();
        double ih = contentHeight();
        if (iw <= 0 || ih <= 0) {
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
        double zx = extent.width / iw;
        double zy = extent.height / ih;
        setZoomLevel(Math.min(zx, zy));
    }

    private double contentWidth() {
        if (svgNode != null) {
            return svgWidth;
        }
        if (image != null) {
            return image.getWidth();
        }
        return 0;
    }

    private double contentHeight() {
        if (svgNode != null) {
            return svgHeight;
        }
        if (image != null) {
            return image.getHeight();
        }
        return 0;
    }

    private boolean hasContent() {
        return svgNode != null || image != null;
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
        double cw = contentWidth();
        double ch = contentHeight();
        if (cw <= 0 || ch <= 0) {
            setPreferredSize(new Dimension(0, 0));
            return;
        }
        int w = (int) Math.max(1, cw * zoomLevel);
        int h = (int) Math.max(1, ch * zoomLevel);
        setPreferredSize(new Dimension(w, h));
    }

    /** マウスポインタ位置を画面上の同じ点に保ったままズームする。 */
    private void zoomAt(Point screenPos, double newZoom) {
        if (!hasContent()) {
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
                if (!hasContent()) {
                    return;
                }
                if (maybeFireLinkPopup(e)) {
                    return;
                }
                // Alt+左クリック = ラバーバンド選択モード
                if (SwingUtilities.isLeftMouseButton(e)
                        && (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                    selectionAnchor = e.getPoint();
                    selectionCurrent = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }
                if (maybeFireLinkClick(e)) {
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e)
                        || SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                    setCursor(grabCursor);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (dragStart != null) {
                    return;
                }
                setCursor(cursorForPosition(e.getPoint()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // ラバーバンド選択中
                if (selectionAnchor != null) {
                    selectionCurrent = e.getPoint();
                    repaint();
                    return;
                }
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
                // ラバーバンド選択確定
                if (selectionAnchor != null) {
                    selectionCurrent = e.getPoint();
                    collectAndCopyText(selectionAnchor, selectionCurrent);
                    selectionAnchor = null;
                    selectionCurrent = null;
                    repaint();
                    setCursor(cursorForPosition(e.getPoint()));
                    return;
                }
                dragStart = null;
                setCursor(cursorForPosition(e.getPoint()));
                maybeFireLinkPopup(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(cursorForPosition(e.getPoint()));
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

    /**
     * 右クリック (ポップアップトリガ) なら登録済みリスナを発火する。
     * リンク領域上なら対応する {@link LinkArea} を、それ以外は {@code null} を渡す。
     * Linux は {@code mousePressed}、Windows/Mac は {@code mouseReleased} 側で
     * isPopupTrigger が true になるため両方から呼ぶ前提。
     *
     * @return リンクポップアップを発火した場合 true (呼び出し側はそれ以降の処理を抑制してよい)
     */
    private boolean maybeFireLinkPopup(MouseEvent e) {
        if (!e.isPopupTrigger() || linkPopupListener == null) {
            return false;
        }
        LinkArea hit = linkAreas.isEmpty() ? null : hitTestLink(e.getPoint());
        linkPopupListener.accept(hit, e);
        return true;
    }

    /**
     * 左クリックがリンク領域内なら登録済みリスナを発火し、ドラッグ開始を抑止する。
     * @return リンククリックを発火した場合 true
     */
    private boolean maybeFireLinkClick(MouseEvent e) {
        if (e.isPopupTrigger()
                || linkClickListener == null
                || linkAreas.isEmpty()
                || !SwingUtilities.isLeftMouseButton(e)) {
            return false;
        }
        LinkArea hit = hitTestLink(e.getPoint());
        if (hit == null) {
            return false;
        }
        linkClickListener.accept(hit, e);
        return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (svgNode != null) {
                g2.scale(zoomLevel, zoomLevel);
                svgNode.paint(g2);
            } else if (image != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                AffineTransform tx = AffineTransform.getScaleInstance(zoomLevel, zoomLevel);
                g2.drawImage(image, tx, null);
            }
            // ラバーバンド選択矩形を最前面に描画
            if (selectionAnchor != null && selectionCurrent != null) {
                int rx = Math.min(selectionAnchor.x, selectionCurrent.x);
                int ry = Math.min(selectionAnchor.y, selectionCurrent.y);
                int rw = Math.abs(selectionCurrent.x - selectionAnchor.x);
                int rh = Math.abs(selectionCurrent.y - selectionAnchor.y);
                // スケール変換を元に戻してパネル座標で描く
                g2.setTransform(new AffineTransform());
                g2.setColor(new Color(30, 100, 255, 40));
                g2.fillRect(rx, ry, rw, rh);
                g2.setColor(new Color(30, 100, 255, 200));
                float[] dash = {4f, 4f};
                Stroke oldStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10, dash, 0));
                g2.drawRect(rx, ry, rw, rh);
                g2.setStroke(oldStroke);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * ラバーバンド矩形 (パネル座標) に含まれる SVG テキスト要素を収集してクリップボードへコピーする。
     * テキストは上から下・左から右の順にソートして改行区切りで結合する。
     */
    private void collectAndCopyText(Point anchor, Point current) {
        if (svgTexts.isEmpty() || zoomLevel <= 0) {
            return;
        }
        // パネル座標 → SVG 座標変換
        double x1 = Math.min(anchor.x, current.x) / zoomLevel;
        double y1 = Math.min(anchor.y, current.y) / zoomLevel;
        double x2 = Math.max(anchor.x, current.x) / zoomLevel;
        double y2 = Math.max(anchor.y, current.y) / zoomLevel;
        Rectangle2D svgSel = new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);

        List<SvgTextItem> hits = new ArrayList<>();
        for (SvgTextItem item : svgTexts) {
            if (svgSel.contains(item.getX(), item.getY())) {
                hits.add(item);
            }
        }
        if (hits.isEmpty()) {
            return;
        }
        // 上→下、同じ行なら左→右でソート
        hits.sort((a, b) -> {
            double dy = a.getY() - b.getY();
            if (Math.abs(dy) > 4) {
                return dy < 0 ? -1 : 1;
            }
            double dx = a.getX() - b.getX();
            return dx < 0 ? -1 : (dx > 0 ? 1 : 0);
        });
        StringBuilder sb = new StringBuilder();
        for (SvgTextItem item : hits) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(item.getText());
        }
        copyToClipboard(sb.toString(), hits.size() + " items copied");
    }

    /** 全 SVG テキストをクリップボードへコピーする (Ctrl+A 相当)。 */
    private void copyAllText() {
        if (svgTexts.isEmpty()) {
            return;
        }
        List<SvgTextItem> sorted = new ArrayList<>(svgTexts);
        sorted.sort((a, b) -> {
            double dy = a.getY() - b.getY();
            if (Math.abs(dy) > 4) {
                return dy < 0 ? -1 : 1;
            }
            double dx = a.getX() - b.getX();
            return dx < 0 ? -1 : (dx > 0 ? 1 : 0);
        });
        StringBuilder sb = new StringBuilder();
        for (SvgTextItem item : sorted) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(item.getText());
        }
        copyToClipboard(sb.toString(), "All text copied (" + sorted.size() + " items)");
    }

    private void copyToClipboard(String text, String feedback) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            if (copyFeedbackListener != null) {
                copyFeedbackListener.accept(feedback);
            }
        } catch (IllegalStateException ignored) {
            // クリップボードが使用中の場合は無視
        }
    }

    /** Ctrl+A = 全テキストコピーのキーバインドを設定する。 */
    private void setupKeyBindings() {
        setFocusable(true);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
                "copyAllText");
        getActionMap().put("copyAllText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyAllText();
            }
        });
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
