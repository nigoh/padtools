package padtools.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * MultiUserRoleReport のユニットテスト。
 */
public class MultiUserRoleReportTest {

    @Test
    public void testNullAnalysis() {
        String md = MultiUserRoleReport.generateMarkdown(null);
        assertTrue(md, md.contains("no analysis"));
    }

    @Test
    public void testEmptyAnalysisGeneratesValidMarkdown() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        String md = MultiUserRoleReport.generateMarkdown(a);
        assertTrue(md, md.contains("# AAOS MultiUser ロール分離レポート"));
        assertTrue(md, md.contains("Manifest 由来"));
    }

    @Test
    public void testCarPermissionAggregation() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.example.car");
        m.getPermissions().add(new AndroidPermissionInfo("android.car.permission.CAR_SPEED"));
        m.getPermissions().add(new AndroidPermissionInfo("android.permission.INTERNET"));
        java.util.List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);

        String md = MultiUserRoleReport.generateMarkdown(a);
        assertTrue(md, md.contains("android.car.permission.CAR_SPEED"));
        assertTrue(md, md.contains("com.example.car"));
        // 非 car permission は集計対象外
        assertFalse(md, md.contains("android.permission.INTERNET"));
    }

    @Test
    public void testSepolicyCarRuleAggregation() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        SepolicyInfo te = SepolicyTeParser.parse(
                "type carservice, domain;\n"
                        + "type vehicle_hal_server, domain;\n"
                        + "allow carservice vehicle_hal_server:binder call;\n"
                        + "allow init zygote:binder call;\n", // car 関連でないので含まれない
                "carservice.te");
        a.getSepolicies().add(te);
        String md = MultiUserRoleReport.generateMarkdown(a);
        assertTrue(md, md.contains("SELinux allow ルール"));
        assertTrue(md, md.contains("carservice"));
        assertTrue(md, md.contains("vehicle_hal_server"));
        // 無関係なルールは含まれない
        assertFalse(md, md.contains("zygote"));
    }

    @Test
    public void testTransitionSectionAppears() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        SepolicyInfo te = SepolicyTeParser.parse(
                "type_transition init carservice_exec:process carservice;\n",
                "init.te");
        a.getSepolicies().add(te);
        String md = MultiUserRoleReport.generateMarkdown(a);
        assertTrue(md, md.contains("ドメイン遷移"));
        assertTrue(md, md.contains("init"));
        assertTrue(md, md.contains("carservice"));
    }
}
