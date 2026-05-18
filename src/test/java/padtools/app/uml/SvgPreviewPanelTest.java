package padtools.app.uml;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link SvgPreviewPanel} の基本動作を検証する。
 *
 * <p>レンダリングそのものはディスプレイなしで再現困難なため、
 * 状態管理 (画像セット / ズーム値の範囲) のみ単体テストする。</p>
 */
public class SvgPreviewPanelTest {

    @Test
    public void testSetImage() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        BufferedImage img = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        p.setImage(img);
        assertSame(img, p.getImage());
    }

    @Test
    public void testClearImage() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        p.setImage(null);
        assertNull(p.getImage());
    }

    @Test
    public void testZoomDefaults() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        assertEquals(1.0, p.getZoomLevel(), 1e-9);
    }

    @Test
    public void testZoomReset() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        p.setZoomLevel(2.5);
        assertEquals(2.5, p.getZoomLevel(), 1e-9);
        p.zoomReset();
        assertEquals(1.0, p.getZoomLevel(), 1e-9);
    }

    @Test
    public void testZoomClamped() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        p.setZoomLevel(100.0);
        assertTrue("zoom should be clamped: " + p.getZoomLevel(),
                p.getZoomLevel() <= 8.0 + 1e-9);
        p.setZoomLevel(0.001);
        assertTrue("zoom should be clamped: " + p.getZoomLevel(),
                p.getZoomLevel() >= 0.1 - 1e-9);
    }

    @Test
    public void testZoomChangeListenerInvoked() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        int[] count = new int[]{0};
        p.setZoomChangeListener(() -> count[0]++);
        p.setZoomLevel(2.0);
        assertEquals(1, count[0]);
    }
}
