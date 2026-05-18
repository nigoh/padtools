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

    @Test
    public void testLinkAreasEmptyWhenNoLinks() throws IOException {
        String puml = "@startuml\nclass Foo\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        assertNotNull("getLinkAreas should never be null", r.getLinkAreas());
        assertTrue("should be empty when no [[url]] embedded",
                r.getLinkAreas().isEmpty());
    }

    @Test
    public void testLinkAreasExtractedFromPlantUmlUrl() throws IOException {
        String puml = "@startuml\n"
                + "class Foo [[padtools://class/com.example.Foo]]\n"
                + "class Bar [[padtools://class/com.example.Bar]]\n"
                + "Foo --> Bar\n"
                + "@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        java.util.List<PlantUmlSvgRenderer.LinkArea> areas = r.getLinkAreas();
        assertNotNull(areas);
        assertTrue("should have at least two link areas, got " + areas.size(),
                areas.size() >= 2);
        boolean foo = false;
        boolean bar = false;
        for (PlantUmlSvgRenderer.LinkArea a : areas) {
            if ("padtools://class/com.example.Foo".equals(a.getHref())) {
                foo = true;
                assertTrue(a.getWidth() > 0);
                assertTrue(a.getHeight() > 0);
            }
            if ("padtools://class/com.example.Bar".equals(a.getHref())) {
                bar = true;
            }
        }
        assertTrue("Foo link area not found", foo);
        assertTrue("Bar link area not found", bar);
    }
}
