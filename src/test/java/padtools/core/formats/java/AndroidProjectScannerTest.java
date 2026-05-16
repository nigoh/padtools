package padtools.core.formats.java;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidProjectScanner のユニットテスト。
 *
 * <p>JUnit 4 の TemporaryFolder で Android Gradle プロジェクトを模した
 * ディレクトリ構造を組み立て、スキャンが期待通りに動作することを検証する。</p>
 */
public class AndroidProjectScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;

    @Before
    public void setupProject() throws IOException {
        root = tmp.newFolder("MyApp");
        // 標準 Android Gradle レイアウト
        File mainJava = new File(root, "app/src/main/java/com/example/app");
        assertTrue(mainJava.mkdirs());
        writeFile(new File(mainJava, "MainActivity.java"),
                "package com.example.app;\n"
                        + "public class MainActivity { void onCreate() {} }");
        writeFile(new File(mainJava, "Util.java"),
                "package com.example.app;\n"
                        + "public class Util { static int x() { return 1; } }");

        File testJava = new File(root, "app/src/test/java/com/example/app");
        assertTrue(testJava.mkdirs());
        writeFile(new File(testJava, "MainActivityTest.java"),
                "package com.example.app;\n"
                        + "public class MainActivityTest { void t() {} }");

        File androidTest = new File(root, "app/src/androidTest/java/com/example/app");
        assertTrue(androidTest.mkdirs());
        writeFile(new File(androidTest, "UiTest.java"),
                "package com.example.app;\n"
                        + "public class UiTest { void t() {} }");

        // 除外されるディレクトリ
        File buildDir = new File(root, "app/build/generated/source");
        assertTrue(buildDir.mkdirs());
        writeFile(new File(buildDir, "Generated.java"),
                "public class Generated {}");

        File gradleDir = new File(root, ".gradle/some/path");
        assertTrue(gradleDir.mkdirs());
        writeFile(new File(gradleDir, "Cached.java"), "// junk");

        File idea = new File(root, ".idea");
        assertTrue(idea.mkdirs());
        writeFile(new File(idea, "WontScan.java"), "// junk");

        // 非 Java ファイル
        writeFile(new File(mainJava, "Notes.kt"), "fun foo() {}");
        writeFile(new File(mainJava, "strings.xml"), "<x/>");
    }

    @After
    public void teardown() {
        // TemporaryFolder が自動削除する
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private static String basename(File f) {
        return f.getName();
    }

    // --- 入力バリデーション ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullRoot() {
        AndroidProjectScanner.scan(null);
    }

    @Test
    public void testNonExistentRoot() {
        List<File> result = AndroidProjectScanner.scan(new File("/does-not-exist-xyz"));
        assertTrue(result.isEmpty());
    }

    // --- 基本動作 ---

    @Test
    public void testDefaultScanFindsMainJavaOnly() {
        List<File> files = AndroidProjectScanner.scan(root);
        assertEquals("expected 2 main java files, got: " + files, 2, files.size());
        for (File f : files) {
            String path = f.getPath().replace(File.separatorChar, '/');
            assertTrue(path, path.contains("src/main/java"));
            assertTrue(path, path.endsWith(".java"));
        }
    }

    @Test
    public void testExcludeBuildAndIdeFolders() {
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            String path = f.getPath().replace(File.separatorChar, '/');
            assertFalse("build dir not excluded: " + path, path.contains("/build/"));
            assertFalse("gradle dir not excluded: " + path, path.contains("/.gradle/"));
            assertFalse("idea dir not excluded: " + path, path.contains("/.idea/"));
        }
    }

    @Test
    public void testIncludeTests() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeTests = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        // main 2 + test 1 + androidTest 1 = 4
        assertEquals("expected 4 files, got: " + files, 4, files.size());
        boolean foundTest = false;
        boolean foundAndroidTest = false;
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            if (p.contains("/src/test/")) {
                foundTest = true;
            }
            if (p.contains("/src/androidTest/")) {
                foundAndroidTest = true;
            }
        }
        assertTrue(foundTest);
        assertTrue(foundAndroidTest);
    }

    @Test
    public void testIncludeKotlin() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeKotlin = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        boolean foundKt = false;
        for (File f : files) {
            if (f.getName().endsWith(".kt")) {
                foundKt = true;
            }
        }
        assertTrue("expected at least one .kt file", foundKt);
    }

    @Test
    public void testCustomExcludedDirs() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.excludedDirs = new HashSet<>(AndroidProjectScanner.DEFAULT_EXCLUDED_DIRS);
        o.excludedDirs.add("app");
        List<File> files = AndroidProjectScanner.scan(root, o);
        // "app" を追加除外したので main の Java ファイルも見つからない
        assertTrue("expected no files when app/ excluded, got: " + files,
                files.isEmpty());
    }

    @Test
    public void testEmptyExcludedDirsScansEverything() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.excludedDirs = new HashSet<>();
        List<File> files = AndroidProjectScanner.scan(root, o);
        // 除外がないので build/.gradle/.idea 配下も含まれる
        boolean foundBuild = false;
        boolean foundGradleDir = false;
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            if (p.contains("/build/")) {
                foundBuild = true;
            }
            if (p.contains("/.gradle/")) {
                foundGradleDir = true;
            }
        }
        assertTrue("expected build/ to be included: " + files, foundBuild);
        assertTrue("expected .gradle/ to be included: " + files, foundGradleDir);
    }

    @Test
    public void testMaxDepth() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.maxDepth = 1;
        List<File> files = AndroidProjectScanner.scan(root, o);
        // 深さ 1 では app/src/... に到達できない
        assertTrue("expected no files with maxDepth=1", files.isEmpty());
    }

    @Test
    public void testScanSingleFile() throws IOException {
        File single = tmp.newFile("OneShot.java");
        writeFile(single, "class C {}");
        List<File> files = AndroidProjectScanner.scan(single);
        assertEquals(1, files.size());
        assertEquals(single, files.get(0));
    }

    @Test
    public void testScanSingleNonJavaFile() throws IOException {
        File single = tmp.newFile("Notes.txt");
        writeFile(single, "ignore");
        List<File> files = AndroidProjectScanner.scan(single);
        assertTrue(files.isEmpty());
    }

    @Test
    public void testReadFileUtf8() throws IOException {
        File f = tmp.newFile("Hello.java");
        writeFile(f, "class A { /* 日本語コメント */ }");
        String content = AndroidProjectScanner.readFile(f);
        assertTrue(content.contains("日本語コメント"));
    }

    // --- プロジェクト一括変換 ---

    @Test
    public void testConvertProject() throws IOException {
        String spd = AndroidProjectScanner.convertProject(root, null, null);
        assertTrue(spd, spd.contains("MainActivity.onCreate()"));
        assertTrue(spd, spd.contains("Util.x()"));
        // 各ファイルが見出しコメント付きで出力されること
        assertTrue(spd, spd.contains("# === MainActivity.java ==="));
        assertTrue(spd, spd.contains("# === Util.java ==="));
        // テストは除外
        assertFalse(spd, spd.contains("MainActivityTest"));
    }

    @Test
    public void testConvertProjectIncludingTests() throws IOException {
        AndroidProjectScanner.Options s = new AndroidProjectScanner.Options();
        s.includeTests = true;
        String spd = AndroidProjectScanner.convertProject(root, s, null);
        assertTrue(spd, spd.contains("MainActivityTest"));
        assertTrue(spd, spd.contains("UiTest"));
    }

    @Test
    public void testConvertProjectSingleFile() throws IOException {
        File mainJava = new File(root, "app/src/main/java/com/example/app/MainActivity.java");
        String spd = AndroidProjectScanner.convertProject(mainJava, null, null);
        assertTrue(spd, spd.contains("MainActivity.onCreate()"));
    }

    @Test
    public void testFilesAreSorted() {
        List<File> files = AndroidProjectScanner.scan(root);
        for (int i = 1; i < files.size(); i++) {
            assertTrue("files not sorted: " + files.get(i - 1) + " vs " + files.get(i),
                    files.get(i - 1).compareTo(files.get(i)) <= 0);
        }
    }
}
