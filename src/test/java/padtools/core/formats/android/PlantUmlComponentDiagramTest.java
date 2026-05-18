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

    @Test
    public void testDeduplicatesAcrossManifests() {
        // 同モジュールの main と debug に同じ FQN の Activity がある場合、
        // component 宣言は 1 回だけにする (PlantUML のエラー回避)
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo mainM = new AndroidManifestInfo();
        mainM.setPackageName("com.x");
        mainM.setSourceSet("main");
        mainM.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MainActivity"));
        AndroidManifestInfo debugM = new AndroidManifestInfo();
        debugM.setPackageName("com.x");
        debugM.setSourceSet("debug");
        debugM.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MainActivity"));
        debugM.getReceivers().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "com.x.DebugReceiver"));
        java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
        list.add(mainM);
        list.add(debugM);
        a.getManifestsByModule().put("app", list);

        String puml = PlantUmlComponentDiagram.generate(a);
        // MainActivity の component 宣言は 1 つだけ
        int count = puml.split("component \"com.x.MainActivity\"").length - 1;
        assertEquals("MainActivity should be declared once", 1, count);
        // DebugReceiver は debug sourceSet ステレオタイプ付き
        assertTrue(puml, puml.contains("com.x.DebugReceiver"));
        assertTrue(puml, puml.contains("<<src:debug>>"));
    }

    @Test
    public void testMainSourceSetHasNoStereotype() {
        // main の sourceSet は <<src:main>> を付けない (ノイズ削減)
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("p");
        m.setSourceSet("main");
        m.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "p.A"));
        java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        String puml = PlantUmlComponentDiagram.generate(a);
        assertFalse(puml, puml.contains("<<src:main>>"));
    }

    @Test
    public void testSepolicyDomainsAreRendered() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        SepolicyInfo te = SepolicyTeParser.parse(
                "type carservice, domain;\n"
                        + "type vehicle_hal_server, domain;\n"
                        + "allow carservice vehicle_hal_server:binder call;\n",
                "carservice.te");
        a.getSepolicies().add(te);
        String puml = PlantUmlComponentDiagram.generate(a);
        assertTrue(puml, puml.contains("package \"sepolicy\""));
        assertTrue(puml, puml.contains("<<domain>>"));
        assertTrue(puml, puml.contains("<<allow>>"));
        assertTrue(puml, puml.contains("carservice"));
        assertTrue(puml, puml.contains("vehicle_hal_server"));
    }

    @Test
    public void testSepolicyDomainsCanBeDisabled() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        SepolicyInfo te = SepolicyTeParser.parse(
                "type carservice, domain;\n"
                        + "allow carservice vehicle_hal:binder call;\n",
                "carservice.te");
        a.getSepolicies().add(te);
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        o.showSepolicyDomains = false;
        String puml = PlantUmlComponentDiagram.generate(a, o);
        assertFalse(puml, puml.contains("<<domain>>"));
        assertFalse(puml, puml.contains("<<allow>>"));
    }
}
