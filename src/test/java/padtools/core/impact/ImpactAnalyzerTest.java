package padtools.core.impact;

import org.junit.Test;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaStructureExtractor;
import padtools.core.refs.ReferenceIndex;
import padtools.core.refs.ReferenceIndexBuilder;
import padtools.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ImpactAnalyzer} の BFS 推移閉包と影響度スコア検証。
 */
public class ImpactAnalyzerTest {

    private static List<JavaClassInfo> parse(String... sources) {
        java.util.List<JavaClassInfo> all = new java.util.ArrayList<>();
        for (String src : sources) {
            all.addAll(JavaStructureExtractor.extract(src, ErrorListener.silent()));
        }
        return all;
    }

    private static ReferenceIndex buildIndex(List<JavaClassInfo> classes) {
        ClassIndex idx = new ClassIndex();
        for (JavaClassInfo c : classes) {
            idx.put(c, null, null);
        }
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);
        return refIdx;
    }

    @Test
    public void directCallerIsLayerOne() {
        String target = "package com.foo;\n"
                + "public class Target {\n"
                + "  public void hit() {}\n"
                + "}\n";
        String caller = "package com.foo;\n"
                + "public class Caller {\n"
                + "  private Target t;\n"
                + "  void run() { t.hit(); }\n"
                + "}\n";
        ReferenceIndex idx = buildIndex(parse(target, caller));
        ImpactGraph g = new ImpactAnalyzer(idx).analyzeMethod("com.foo.Target", "hit", 3);

        ImpactGraph.Node directNode = findNode(g, "com.foo.Caller");
        assertNotNull("Caller should appear in impact graph", directNode);
        assertEquals(1, directNode.getLayer());
        assertTrue("Caller layer 1 should be HIGH risk",
                directNode.getBreakageRisk().equals("HIGH"));
        assertTrue(g.directCallerCount() >= 1);
    }

    @Test
    public void transitiveCallerIsLayerTwo() {
        String target = "package com.foo;\n"
                + "public class Target {\n"
                + "  public void hit() {}\n"
                + "}\n";
        String mid = "package com.foo;\n"
                + "public class Mid {\n"
                + "  private Target t;\n"
                + "  public void delegate() { t.hit(); }\n"
                + "}\n";
        String top = "package com.foo;\n"
                + "public class Top {\n"
                + "  private Mid m;\n"
                + "  void run() { m.delegate(); }\n"
                + "}\n";
        ReferenceIndex idx = buildIndex(parse(target, mid, top));
        ImpactGraph g = new ImpactAnalyzer(idx).analyzeMethod("com.foo.Target", "hit", 3);

        ImpactGraph.Node midNode = findNode(g, "com.foo.Mid");
        assertNotNull(midNode);
        assertEquals(1, midNode.getLayer());

        ImpactGraph.Node topNode = findNode(g, "com.foo.Top");
        assertNotNull("Top must reach Target transitively", topNode);
        assertTrue("Top should be at layer 2+", topNode.getLayer() >= 2);
    }

    @Test
    public void depthLimitStopsTransitive() {
        String target = "package com.foo;\n"
                + "public class Target { public void hit() {} }\n";
        String mid = "package com.foo;\n"
                + "public class Mid { private Target t; public void delegate() { t.hit(); } }\n";
        String top = "package com.foo;\n"
                + "public class Top { private Mid m; void run() { m.delegate(); } }\n";
        ReferenceIndex idx = buildIndex(parse(target, mid, top));
        ImpactGraph g = new ImpactAnalyzer(idx).analyzeMethod("com.foo.Target", "hit", 1);

        // depth=1 なので Top は到達しない
        ImpactGraph.Node topNode = findNode(g, "com.foo.Top");
        org.junit.Assert.assertNull("Top must not appear at depth 1", topNode);
        ImpactGraph.Node midNode = findNode(g, "com.foo.Mid");
        assertNotNull(midNode);
    }

    @Test
    public void markdownOutputContainsTarget() {
        String target = "package com.foo;\n"
                + "public class Target { public void hit() {} }\n";
        String caller = "package com.foo;\n"
                + "public class Caller { private Target t; void run() { t.hit(); } }\n";
        ReferenceIndex idx = buildIndex(parse(target, caller));
        ImpactGraph g = new ImpactAnalyzer(idx).analyzeMethod("com.foo.Target", "hit", 3);
        String md = MarkdownImpactReport.render(g);
        assertTrue(md.contains("com.foo.Target.hit"));
        assertTrue(md.contains("com.foo.Caller"));
        assertTrue(md.contains("Direct callers"));
    }

    @Test
    public void plantUmlOutputContainsHeader() {
        ImpactGraph g = new ImpactGraph("com.foo.Target.hit");
        g.addNode("com.foo.Target.hit", 0, 1.0, "TARGET");
        g.addNode("com.foo.Caller", 1, 0.5, "DIRECT_CALL");
        g.addEdge("com.foo.Caller", "com.foo.Target.hit", "CALL", "run", "", -1);
        String puml = PlantUmlImpactDiagram.render(g);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("com.foo.Caller"));
    }

    private static ImpactGraph.Node findNode(ImpactGraph g, String id) {
        for (ImpactGraph.Node n : g.nodes()) {
            if (id.equals(n.getId())) {
                return n;
            }
        }
        return null;
    }
}
