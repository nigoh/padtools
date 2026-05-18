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

    @Test
    public void testUsesSdk() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <uses-sdk android:minSdkVersion=\"21\""
                        + " android:targetSdkVersion=\"34\""
                        + " android:maxSdkVersion=\"35\"/>\n"
                        + "  <application/>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(Integer.valueOf(21), info.getMinSdkVersion());
        assertEquals(Integer.valueOf(34), info.getTargetSdkVersion());
        assertEquals(Integer.valueOf(35), info.getMaxSdkVersion());
    }

    @Test
    public void testUsesSdkAbsent() {
        String xml = "<manifest " + NS + " package=\"p\"><application/></manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertNull(info.getMinSdkVersion());
        assertNull(info.getTargetSdkVersion());
        assertNull(info.getMaxSdkVersion());
    }

    @Test
    public void testCustomPermission() {
        String xml =
                "<manifest " + NS + " package=\"com.x\">\n"
                        + "  <permission android:name=\".permission.READ_SECRET\""
                        + " android:protectionLevel=\"signature\""
                        + " android:permissionGroup=\"com.x.group.SECRET\"/>\n"
                        + "  <permission android:name=\"com.x.permission.WRITE_SECRET\""
                        + " android:protectionLevel=\"dangerous\"/>\n"
                        + "  <application/>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(2, info.getCustomPermissions().size());
        AndroidCustomPermission p0 = info.getCustomPermissions().get(0);
        // 相対名は package を前置して FQN に解決される
        assertEquals("com.x.permission.READ_SECRET", p0.getName());
        assertEquals("signature", p0.getProtectionLevel());
        assertEquals("com.x.group.SECRET", p0.getPermissionGroup());
        assertEquals("READ_SECRET", p0.getShortName());
        AndroidCustomPermission p1 = info.getCustomPermissions().get(1);
        assertEquals("com.x.permission.WRITE_SECRET", p1.getName());
        assertEquals("dangerous", p1.getProtectionLevel());
    }

    @Test
    public void testActivityAlias() {
        String xml =
                "<manifest " + NS + " package=\"com.x\">\n"
                        + "  <application>\n"
                        + "    <activity android:name=\".MainActivity\"/>\n"
                        + "    <activity-alias android:name=\".LegacyEntry\""
                        + " android:targetActivity=\".MainActivity\""
                        + " android:exported=\"true\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(2, info.getActivities().size());
        AndroidComponentInfo main = info.getActivities().get(0);
        AndroidComponentInfo alias = info.getActivities().get(1);
        assertFalse(main.isActivityAlias());
        assertTrue(alias.isActivityAlias());
        assertEquals("com.x.LegacyEntry", alias.getName());
        assertEquals("com.x.MainActivity", alias.getTargetActivity());
        assertEquals(Boolean.TRUE, alias.getExported());
    }

    @Test
    public void testForegroundServiceType() {
        String xml =
                "<manifest " + NS + " package=\"p\">\n"
                        + "  <application>\n"
                        + "    <service android:name=\".UploadService\""
                        + " android:foregroundServiceType=\"dataSync|connectedDevice\"/>\n"
                        + "    <service android:name=\".PlainService\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("dataSync|connectedDevice",
                info.getServices().get(0).getForegroundServiceType());
        assertNull(info.getServices().get(1).getForegroundServiceType());
    }

    @Test
    public void testIntentFilterDeepLink() {
        String xml =
                "<manifest " + NS + " package=\"com.x\">\n"
                        + "  <application>\n"
                        + "    <activity android:name=\".DeepLinkActivity\">\n"
                        + "      <intent-filter android:autoVerify=\"true\""
                        + " android:order=\"100\">\n"
                        + "        <action android:name=\"android.intent.action.VIEW\"/>\n"
                        + "        <category android:name=\"android.intent.category.DEFAULT\"/>\n"
                        + "        <category android:name=\"android.intent.category.BROWSABLE\"/>\n"
                        + "        <data android:scheme=\"https\""
                        + " android:host=\"example.com\""
                        + " android:pathPrefix=\"/share\"/>\n"
                        + "        <data android:scheme=\"myapp\""
                        + " android:host=\"open\"/>\n"
                        + "      </intent-filter>\n"
                        + "    </activity>\n"
                        + "  </application>\n"
                        + "</manifest>\n";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        AndroidComponentInfo a = info.getActivities().get(0);
        AndroidIntentFilter f = a.getIntentFilters().get(0);
        assertEquals(Boolean.TRUE, f.getAutoVerify());
        assertEquals(Integer.valueOf(100), f.getOrder());
        assertTrue(f.isViewDeepLink());
        assertEquals(2, f.getDataSpecs().size());
        AndroidDataSpec d0 = f.getDataSpecs().get(0);
        assertEquals("https", d0.getScheme());
        assertEquals("example.com", d0.getHost());
        assertEquals("/share", d0.getPathPrefix());
        assertEquals("https://example.com/share*", d0.toDeepLinkUri());
        AndroidDataSpec d1 = f.getDataSpecs().get(1);
        assertEquals("myapp", d1.getScheme());
        assertEquals("open", d1.getHost());
        assertEquals("myapp://open", d1.toDeepLinkUri());
        // 既存 API も埋まっている (互換)
        assertTrue(f.getDataSchemes().contains("https"));
        assertTrue(f.getDataSchemes().contains("myapp"));
    }
}
