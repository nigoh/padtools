package padtools.app.uml;

import org.junit.Test;
import padtools.core.formats.android.AndroidComponentInfo;
import padtools.core.formats.android.AndroidManifestInfo;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.GradleDependency;
import padtools.core.formats.android.GradleProjectInfo;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaStructureExtractor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DiagramService} の単体テスト。各図種で PlantUML テキストが生成されること、
 * {@code @startuml} / {@code @enduml} が含まれることを検証する。
 */
public class DiagramServiceTest {

    private List<JavaClassInfo> sampleClasses() {
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo { void run() { Bar b; } }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        return infos;
    }

    private AndroidProjectAnalysis sampleAnalysis() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        GradleProjectInfo g = new GradleProjectInfo();
        g.setModuleName("app");
        g.getDependencies().add(
                new GradleDependency("implementation", "com.example:lib:1.0"));
        a.getGradleByModule().put("app", g);

        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.example.app");
        AndroidComponentInfo act = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, ".MainActivity");
        m.getActivities().add(act);
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        return a;
    }

    @Test
    public void testClassDiagram() {
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.CLASS),
                sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("com.a") || puml.contains("Foo"));
    }

    @Test
    public void testPackageDiagram() {
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.PACKAGE),
                sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("package \"com.a"));
        assertTrue(puml, puml.contains("package \"com.b"));
    }

    @Test
    public void testSequenceDiagram() {
        DiagramRequest req = new DiagramRequest(
                DiagramKind.SEQUENCE, "com.a.Foo", "run", true);
        String puml = DiagramService.generatePuml(
                req, sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSequenceDiagramRequiresEntry() {
        try {
            DiagramService.generatePuml(
                    new DiagramRequest(DiagramKind.SEQUENCE),
                    sampleAnalysis(), sampleClasses());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    @Test
    public void testComponentDiagram() {
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.COMPONENT),
                sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testDependencyDiagram() {
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.DEPENDENCY),
                sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRequest() {
        DiagramService.generatePuml(null, sampleAnalysis(), sampleClasses());
    }

    @Test(expected = IllegalStateException.class)
    public void testCacheNotLoaded() {
        DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.CLASS), new ProjectAnalysisCache());
    }
}
