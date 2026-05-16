package padtools.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * PlantUmlGradleDependencyGraph のユニットテスト。
 */
public class PlantUmlGradleDependencyGraphTest {

    private static AndroidProjectAnalysis build() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        GradleProjectInfo app = new GradleProjectInfo();
        app.setModuleName("app");
        app.getPlugins().add("com.android.application");
        app.getDependencies().add(new GradleDependency("implementation", "project(':lib')"));
        app.getDependencies().add(new GradleDependency("implementation",
                "androidx.appcompat:appcompat:1.6.1"));
        app.getDependencies().add(new GradleDependency("testImplementation",
                "junit:junit:4.13.2"));
        a.getGradleByModule().put("app", app);

        GradleProjectInfo lib = new GradleProjectInfo();
        lib.setModuleName("lib");
        lib.getPlugins().add("com.android.library");
        a.getGradleByModule().put("lib", lib);
        return a;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        PlantUmlGradleDependencyGraph.generate(null);
    }

    @Test
    public void testBasicGeneration() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("component \"app\""));
        assertTrue(puml, puml.contains("component \"lib\""));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testApplicationAndLibraryStereotype() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("<<application>>"));
        assertTrue(puml, puml.contains("<<library>>"));
    }

    @Test
    public void testModuleEdge() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        // 矢印 (具体的なエイリアスは可変なので "-->" の存在を確認)
        assertTrue(puml, puml.contains("-->"));
        assertTrue(puml, puml.contains("implementation"));
    }

    @Test
    public void testExternalLib() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("androidx.appcompat:appcompat"));
        assertTrue(puml, puml.contains("<<external>>"));
    }

    @Test
    public void testTestScopeExcludedByDefault() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertFalse(puml, puml.contains("junit"));
    }

    @Test
    public void testTestScopeIncluded() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeTestScopes = true;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertTrue(puml, puml.contains("junit"));
        assertTrue(puml, puml.contains("..>"));
    }

    @Test
    public void testNoExternalLibsOption() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeExternalLibs = false;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertFalse(puml, puml.contains("androidx.appcompat"));
    }

    @Test
    public void testLegendOnByDefault() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("legend right"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeLegend = false;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertFalse(puml, puml.contains("legend right"));
    }
}
