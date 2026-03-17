package padtools.core.view;

import org.junit.Test;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.PADModel;

import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

/**
 * View2Image のユニットテスト。
 */
public class View2ImageTest {

    private View createViewFromSPD(String spd) {
        PADModel model = SPDParser.parse(spd, null);
        Model2View m2v = new Model2View();
        return m2v.toView(model);
    }

    @Test
    public void testToImageBasic() {
        View view = createViewFromSPD(":terminal START\n処理A\n:terminal END");
        BufferedImage img = View2Image.toImage(view, 1.0);

        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    public void testToImageScale() {
        View view = createViewFromSPD("処理A");
        BufferedImage img1 = View2Image.toImage(view, 1.0);
        BufferedImage img2 = View2Image.toImage(view, 2.0);

        assertNotNull(img1);
        assertNotNull(img2);
        // 2倍スケールは約2倍のサイズ
        assertTrue(img2.getWidth() >= img1.getWidth() * 1.8);
        assertTrue(img2.getHeight() >= img1.getHeight() * 1.8);
    }

    @Test
    public void testToImageEmptyModel() {
        View view = createViewFromSPD("");
        BufferedImage img = View2Image.toImage(view, 1.0);

        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    public void testToImageComplexDiagram() {
        String spd = ":terminal START\n" +
                ":if condition\n" +
                "\t:while loop\n" +
                "\t\tprocess\n" +
                ":else\n" +
                "\t:switch val\n" +
                "\t:case A\n" +
                "\t\tA\n" +
                "\t:case B\n" +
                "\t\tB\n" +
                ":terminal END";
        View view = createViewFromSPD(spd);
        BufferedImage img = View2Image.toImage(view, 1.0);

        assertNotNull(img);
        assertTrue(img.getWidth() > 50);
        assertTrue(img.getHeight() > 50);
    }
}
