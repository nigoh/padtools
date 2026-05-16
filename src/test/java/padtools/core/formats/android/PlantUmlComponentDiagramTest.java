package padtools.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlComponentDiagram のユニットテスト。
 */
public class PlantUmlComponentDiagramTest {

    private static AndroidProjectAnalysis buildAnalysis() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");

        AndroidComponentInfo activity = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MainActivity");
        activity.setExported(true);
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.getActions().add("android.intent.action.MAIN");
        f.getCategories().add("android.intent.category.LAUNCHER");
        activity.getIntentFilters().add(f);
        m.getActivities().add(activity);

        AndroidComponentInfo service = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.x.PushService");
        m.getServices().add(service);

        AndroidPermissionInfo perm = new AndroidPermissionInfo("android.permission.INTERNET");
        m.getPermissions().add(perm);

        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        return a;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        PlantUmlComponentDiagram.generate(null);
    }

    @Test
    public void testEmptyAnalysis() {
        String puml = PlantUmlComponentDiagram.generate(new AndroidProjectAnalysis());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("no manifest components found"));
    }

    @Test
    public void testActivityAndService() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("component \"com.x.MainActivity\""));
        assertTrue(puml, puml.contains("<<Activity>>"));
        assertTrue(puml, puml.contains("component \"com.x.PushService\""));
        assertTrue(puml, puml.contains("<<Service>>"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testLauncherStereotype() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("<<launcher>>"));
    }

    @Test
    public void testExportedHighlighted() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("#LightYellow"));
    }

    @Test
    public void testIntentFilterAction() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        // action MAIN がノード化されて矢印が引かれる
        assertTrue(puml, puml.contains("MAIN"));
        assertTrue(puml, puml.contains("<<action>>"));
    }

    @Test
    public void testPermissionsRendered() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"permissions\""));
        assertTrue(puml, puml.contains("INTERNET"));
    }

    @Test
    public void testGroupByModule() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"app\""));
    }

    @Test
    public void testGroupByModuleDisabled() {
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        o.groupByModule = false;
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("package \"app\" {"));
    }

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("legend right"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlComponentDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("legend right"));
    }
}
