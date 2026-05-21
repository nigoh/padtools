package padtools.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.core.formats.android.AndroidComponentInfo;
import padtools.core.formats.android.AndroidLayoutInfo;
import padtools.core.formats.android.AndroidLayoutParser;
import padtools.core.formats.android.AndroidManifestInfo;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.GradleDependency;
import padtools.core.formats.android.GradleProjectInfo;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaStructureExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DiagramService} の単体テスト。各図種で PlantUML テキストが生成されること、
 * {@code @startuml} / {@code @enduml} が含まれることを検証する。
 */
public class DiagramServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testScreenFlowDiagramFromCache() throws java.io.IOException {
        File pkg = new File(tmp.getRoot(), "src/x");
        assertTrue(pkg.mkdirs());
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(new File(pkg, "StartScreen.java")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            w.write("package x; public class StartScreen {"
                    + " void onClickItem() {"
                    + " getScreenManager().push(new DetailScreen(getCarContext())); } }");
        }
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(tmp.getRoot(), padtools.util.ErrorListener.silent());
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.SCREEN_FLOW), cache);
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("StartScreen"));
        assertTrue(puml, puml.contains("DetailScreen"));
    }

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

    @Test
    public void testCommonClassesDiagram() {
        // 共通クラス図: 3 つのクラスが Util を参照する場面で生成が成立すること
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class Util {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class A { Util u; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class B { Util u; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class C { Util u; }"));
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.COMMON),
                sampleAnalysis(), infos);
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("<<common>>"));
    }

    @Test
    public void testManifestDiagram() {
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.MANIFEST),
                sampleAnalysis(), sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    // --- LAYOUT 図 (新機能) ---

    private AndroidProjectAnalysis analysisWithLayout() {
        AndroidProjectAnalysis a = sampleAnalysis();
        AndroidLayoutInfo layout = AndroidLayoutParser.parse(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " android:id=\"@+id/root\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\">"
                        + "<TextView android:id=\"@+id/t\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\""
                        + " android:text=\"hi\"/>"
                        + "</LinearLayout>");
        layout.setModuleName("app");
        layout.setSourceSet("main");
        layout.setConfigQualifier("");
        layout.setFileName("activity_main.xml");
        List<AndroidLayoutInfo> list = new ArrayList<>();
        list.add(layout);
        a.getLayoutsByModule().put("app", list);
        return a;
    }

    @Test
    public void testLayoutDiagram() {
        AndroidProjectAnalysis a = analysisWithLayout();
        DiagramRequest req = DiagramRequest.forLayout(
                "app::main::::activity_main.xml", true);
        String puml = DiagramService.generatePuml(req, a, sampleClasses());
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("LinearLayout"));
        assertTrue(puml, puml.contains("TextView"));
        assertTrue(puml, puml.contains("id: root"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLayoutDiagramRequiresKey() {
        AndroidProjectAnalysis a = analysisWithLayout();
        DiagramService.generatePuml(new DiagramRequest(DiagramKind.LAYOUT),
                a, sampleClasses());
    }

    @Test
    public void testInheritanceDiagram() {
        List<JavaClassInfo> classes = new ArrayList<>();
        classes.addAll(JavaStructureExtractor.extract(
                "package com.a; interface Runnable { void run(); }"));
        classes.addAll(JavaStructureExtractor.extract(
                "package com.a; class Animal { void breathe() {} }"));
        classes.addAll(JavaStructureExtractor.extract(
                "package com.a; class Dog extends Animal implements Runnable {"
                        + " public void run() {} }"));
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.INHERITANCE),
                sampleAnalysis(), classes);
        assertNotNull(puml);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("top to bottom direction"));
        assertTrue(puml, puml.contains("<|--"));   // extends
        assertTrue(puml, puml.contains("<|.."));   // implements
        assertFalse(puml, puml.contains("breathe")); // メソッドは出ない
        assertFalse(puml, puml.contains(" --> "));   // 利用関係は出ない
    }

    @Test
    public void testLayoutDiagramWithUnknownKeyFails() {
        AndroidProjectAnalysis a = analysisWithLayout();
        try {
            DiagramService.generatePuml(
                    DiagramRequest.forLayout("nope::::::missing.xml", true),
                    a, sampleClasses());
            fail("Expected IllegalArgumentException for unknown layout key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("Layout not found"));
        }
    }
}
