package padtools.core.formats.android;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidProjectAnalyzer のユニットテスト。
 *
 * <p>{@link TemporaryFolder} で Android Gradle プロジェクト構造を模して、
 * settings.gradle / モジュール毎の build.gradle / AndroidManifest.xml が
 * 解析対象になることを検証する。</p>
 */
public class AndroidProjectAnalyzerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private File appDir;
    private File libDir;

    @Before
    public void setupProject() throws IOException {
        root = tmp.newFolder("MyApp");
        write(new File(root, "settings.gradle"),
                "include ':app', ':lib'\n");
        write(new File(root, "build.gradle"),
                "// project-level\n");

        appDir = new File(root, "app");
        assertTrue(appDir.mkdirs());
        write(new File(appDir, "build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android {\n"
                        + "  namespace 'com.example.app'\n"
                        + "  compileSdk 34\n"
                        + "  defaultConfig {\n"
                        + "    applicationId 'com.example.app'\n"
                        + "    minSdk 24\n"
                        + "    targetSdk 34\n"
                        + "  }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  implementation project(':lib')\n"
                        + "  implementation 'androidx.appcompat:appcompat:1.6.1'\n"
                        + "}\n");

        File appManifestDir = new File(appDir, "src/main");
        assertTrue(appManifestDir.mkdirs());
        write(new File(appManifestDir, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.app\">\n"
                        + "  <uses-permission android:name=\"android.permission.INTERNET\"/>\n"
                        + "  <application android:name=\".App\">\n"
                        + "    <activity android:name=\".MainActivity\""
                        + " android:exported=\"true\">\n"
                        + "      <intent-filter>\n"
                        + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
                        + "        <category android:name="
                        + "\"android.intent.category.LAUNCHER\"/>\n"
                        + "      </intent-filter>\n"
                        + "    </activity>\n"
                        + "    <service android:name=\".PushService\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n");

        libDir = new File(root, "lib");
        assertTrue(libDir.mkdirs());
        write(new File(libDir, "build.gradle"),
                "plugins { id 'com.android.library' }\n"
                        + "android {\n"
                        + "  namespace 'com.example.lib'\n"
                        + "  compileSdk 34\n"
                        + "}\n");

        File libManifestDir = new File(libDir, "src/main");
        assertTrue(libManifestDir.mkdirs());
        write(new File(libManifestDir, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.lib\">\n"
                        + "  <application/>\n"
                        + "</manifest>\n");
    }

    private static void write(File f, String content) throws IOException {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRoot() throws IOException {
        AndroidProjectAnalyzer.analyze(null);
    }

    @Test
    public void testFindsAllGradleAndManifest() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        assertNotNull(a.getRootSettings());
        assertTrue(a.getRootSettings().getSubprojects().contains("app"));
        assertTrue(a.getGradleByModule().containsKey("app"));
        assertTrue(a.getGradleByModule().containsKey("lib"));
        assertEquals(2, a.allManifests().size());
    }

    @Test
    public void testGradleParsedCorrectly() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        GradleProjectInfo app = a.getGradleByModule().get("app");
        assertEquals("com.example.app", app.getApplicationId());
        assertEquals(Integer.valueOf(24), app.getMinSdk());
        assertTrue(app.isAndroidApplication());
        GradleProjectInfo lib = a.getGradleByModule().get("lib");
        assertTrue(lib.isAndroidLibrary());
    }

    @Test
    public void testManifestParsedCorrectly() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        boolean foundActivity = false;
        boolean foundService = false;
        for (AndroidComponentInfo c : a.allComponents()) {
            if ("com.example.app.MainActivity".equals(c.getName())) {
                foundActivity = true;
                assertTrue(c.isLauncher());
            }
            if ("com.example.app.PushService".equals(c.getName())) {
                foundService = true;
            }
        }
        assertTrue(foundActivity);
        assertTrue(foundService);
    }

    @Test
    public void testListenerSummary() throws IOException {
        List<String> log = new ArrayList<>();
        AndroidProjectAnalyzer.analyze(root, ErrorListener.collecting(log));
        boolean summary = false;
        for (String s : log) {
            if (s.contains("android analyzer")) {
                summary = true;
            }
        }
        assertTrue("expected analyzer summary: " + log, summary);
    }

    @Test
    public void testFindComponentByFqn() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        AndroidComponentInfo c = a.findComponentByFqn("com.example.app.MainActivity");
        assertNotNull(c);
        assertEquals(AndroidComponentInfo.Kind.ACTIVITY, c.getKind());
        assertNull(a.findComponentByFqn("nope.NotFound"));
    }

    @Test
    public void testInferModuleNameForRoot() {
        String name = AndroidProjectAnalyzer.inferModuleName(root,
                new File(root, "build.gradle"));
        assertEquals(":root", name);
    }
}
