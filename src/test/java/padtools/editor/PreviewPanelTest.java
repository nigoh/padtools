package padtools.editor;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

import padtools.core.view.Model2View;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PreviewPanel のズーム範囲と通知ハンドリングを検証する。
 */
public class PreviewPanelTest {

    @Before
    public void skipIfHeadless() {
        Assume.assumeFalse("Skipping GUI test in headless env",
                GraphicsEnvironment.isHeadless());
    }

    private PreviewPanel newPanel() throws Exception {
        PreviewPanel[] holder = new PreviewPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new PreviewPanel(new Model2View()));
        return holder[0];
    }

    /** ズーム下限 (0.25) を下回らない */
    @Test
    public void testZoomLowerBound() throws Exception {
        PreviewPanel panel = newPanel();
        SwingUtilities.invokeAndWait(() -> panel.setZoomLevel(0.05));
        assertEquals(0.25, panel.getZoomLevel(), 1e-9);
    }

    /** ズーム上限 (4.0) を超えない */
    @Test
    public void testZoomUpperBound() throws Exception {
        PreviewPanel panel = newPanel();
        SwingUtilities.invokeAndWait(() -> panel.setZoomLevel(10.0));
        assertEquals(4.0, panel.getZoomLevel(), 1e-9);
    }

    /** ZoomChangeListener がズーム変更時に呼ばれる */
    @Test
    public void testZoomChangeListenerFires() throws Exception {
        PreviewPanel panel = newPanel();
        int[] counter = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            panel.setZoomChangeListener(() -> counter[0]++);
            panel.setZoomLevel(1.5);
            panel.setZoomLevel(2.0);
        });
        assertTrue("listener fired at least twice", counter[0] >= 2);
    }

    /** adjustZoom も上下限内にクリップされる */
    @Test
    public void testAdjustZoomClipped() throws Exception {
        PreviewPanel panel = newPanel();
        SwingUtilities.invokeAndWait(() -> {
            panel.setZoomLevel(1.0);
            panel.adjustZoom(100.0);   // 上限へ
        });
        assertEquals(4.0, panel.getZoomLevel(), 1e-9);

        SwingUtilities.invokeAndWait(() -> panel.adjustZoom(-100.0));
        assertEquals(0.25, panel.getZoomLevel(), 1e-9);
    }
}
