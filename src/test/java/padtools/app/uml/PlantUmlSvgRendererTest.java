package padtools.app.uml;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlSvgRenderer} のユニットテスト。
 * 同梱 PlantUML が SVG 出力経路を通り、Batik で GraphicsNode 化できることを確認する。
 */
public class PlantUmlSvgRendererTest {

    @Test
    public void testSimpleClassDiagramRenders() throws IOException {
        String puml = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull("RenderedSvg should not be null", r);
        assertNotNull("GraphicsNode should not be null", r.getRoot());
        assertTrue("width > 0", r.getWidth() > 0);
        assertTrue("height > 0", r.getHeight() > 0);
        assertTrue("svg xml should contain <svg",
                r.getSvgXml() != null && r.getSvgXml().contains("<svg"));
    }

    @Test
    public void testSimpleSequenceDiagramRenders() throws IOException {
        String puml = "@startuml\nA -> B: hello\nB -> A: world\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        assertNotNull(r.getRoot());
        assertTrue(r.getWidth() > 0);
        assertTrue(r.getHeight() > 0);
    }

    @Test
    public void testNullPumlRejected() {
        try {
            PlantUmlSvgRenderer.render(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        } catch (IOException ex) {
            fail("Unexpected IOException: " + ex.getMessage());
        }
    }
}
