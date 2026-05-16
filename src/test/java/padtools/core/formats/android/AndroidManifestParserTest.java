package padtools.core.formats.android;

import org.junit.Test;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidManifestParser のユニットテスト。
 */
public class AndroidManifestParserTest {

    private static final String NS = "xmlns:android=\"http://schemas.android.com/apk/res/android\"";

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        AndroidManifestParser.parse(null);
    }

    @Test
    public void testEmpty() {
        List<String> errors = new ArrayList<>();
        AndroidManifestInfo info = AndroidManifestParser.parse(
                "", ErrorListener.collecting(errors));
        assertNotNull(info);
        assertTrue("expected parse error log: " + errors, !errors.isEmpty());
    }

    @Test
    public void testNonManifestRoot() {
        List<String> errors = new ArrayList<>();
        AndroidManifestInfo info = AndroidManifestParser.parse(
                "<foo " + NS + "/>", ErrorListener.collecting(errors));
        assertNotNull(info);
        boolean found = false;
        for (String e : errors) {
            if (e.contains("not <manifest>")) {
                found = true;
            }
        }
        assertTrue("expected 'not <manifest>' error", found);
    }

    @Test
    public void testBasicActivity() {
        String xml =
                "<manifest " + NS + " package=\"com.example.app\">\n"
                        + "  <application android:name=\".App\">\n"
                        + "    <activity android:name=\".MainActivity\""
                        + " android:exported=\"true\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("com.example.app", info.getPackageName());
        assertEquals("com.example.app.App", info.getApplicationClass());
        assertEquals(1, info.getActivities().size());
        AndroidComponentInfo a = info.getActivities().get(0);
        assertEquals(AndroidComponentInfo.Kind.ACTIVITY, a.getKind());
        assertEquals("com.example.app.MainActivity", a.getName());
        assertEquals(Boolean.TRUE, a.getExported());
    }

    @Test
    public void testServiceAndReceiverAndProvider() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <application>\n"
                        + "    <service android:name=\".S\"/>\n"
                        + "    <receiver android:name=\".R\"/>\n"
                        + "    <provider android:name=\".P\" android:authorities=\"p.auth\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(1, info.getServices().size());
        assertEquals(1, info.getReceivers().size());
        assertEquals(1, info.getProviders().size());
        assertEquals(AndroidComponentInfo.Kind.SERVICE,
                info.getServices().get(0).getKind());
        assertEquals("p.auth", info.getProviders().get(0).getAuthorities());
    }

    @Test
    public void testIntentFilter() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <application>\n"
                        + "    <activity android:name=\".M\">\n"
                        + "      <intent-filter>\n"
                        + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
                        + "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                        + "      </intent-filter>\n"
                        + "    </activity>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        AndroidComponentInfo a = info.getActivities().get(0);
        assertEquals(1, a.getIntentFilters().size());
        AndroidIntentFilter f = a.getIntentFilters().get(0);
        assertTrue(f.getActions().contains("android.intent.action.MAIN"));
        assertTrue(f.getCategories().contains("android.intent.category.LAUNCHER"));
        assertTrue(f.isLauncher());
        assertTrue(a.isLauncher());
    }

    @Test
    public void testUsesPermissionAndFeature() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <uses-permission android:name=\"android.permission.INTERNET\"/>\n"
                        + "  <uses-permission android:name=\"android.permission.CAMERA\""
                        + " android:maxSdkVersion=\"28\"/>\n"
                        + "  <uses-feature android:name=\"android.hardware.camera\"/>\n"
                        + "  <application/>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(2, info.getPermissions().size());
        assertEquals("android.permission.INTERNET",
                info.getPermissions().get(0).getName());
        assertEquals("INTERNET", info.getPermissions().get(0).getShortName());
        assertEquals(Integer.valueOf(28),
                info.getPermissions().get(1).getMaxSdkVersion());
        assertTrue(info.getFeatures().contains("android.hardware.camera"));
    }

    @Test
    public void testMetaData() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <application>\n"
                        + "    <meta-data android:name=\"key1\" android:value=\"v1\"/>\n"
                        + "    <activity android:name=\".A\">\n"
                        + "      <meta-data android:name=\"per\" android:value=\"act\"/>\n"
                        + "    </activity>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("v1", info.getApplicationMetaData().get("key1"));
        assertEquals("act", info.getActivities().get(0).getMetaData().get("per"));
    }

    @Test
    public void testFqnResolutionFullyQualified() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <application>\n"
                        + "    <activity android:name=\"com.other.A\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        // 既に完全修飾なら package を前置しない
        assertEquals("com.other.A", info.getActivities().get(0).getName());
    }

    @Test
    public void testInvalidXmlReportsError() {
        List<String> errors = new ArrayList<>();
        AndroidManifestInfo info = AndroidManifestParser.parse(
                "<manifest" + NS + " package=\"p\"<bad>", ErrorListener.collecting(errors));
        assertNotNull(info);
        assertFalse("expected error log", errors.isEmpty());
    }

    @Test
    public void testDoctypeBlocked() {
        // XXE 防御: DOCTYPE は拒否される
        List<String> errors = new ArrayList<>();
        String xml = "<!DOCTYPE manifest [<!ENTITY x \"y\">]>\n"
                + "<manifest " + NS + " package=\"p\"/>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml,
                ErrorListener.collecting(errors));
        assertNotNull(info);
        assertFalse("expected doctype block error", errors.isEmpty());
    }
}
