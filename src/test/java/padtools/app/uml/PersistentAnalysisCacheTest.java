package padtools.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.util.CacheKey;
import padtools.util.ProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * PersistentAnalysisCache の save/load 往復、無効化、破損 manifest fallback テスト。
 */
public class PersistentAnalysisCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /** プロジェクト風: src/main/java/com/example/Foo.java */
    private File makeProject() throws IOException {
        File root = tmp.newFolder("Proj");
        File dir = new File(root, "src/main/java/com/example");
        assertTrue(dir.mkdirs());
        writeFile(new File(dir, "Foo.java"),
                "package com.example; public class Foo {}");
        writeFile(new File(dir, "Bar.java"),
                "package com.example; public class Bar {}");
        return root;
    }

    private PersistentAnalysisCache newCache() throws IOException {
        File base = tmp.newFolder("cache-base");
        return new PersistentAnalysisCache(base);
    }

    @Test
    public void testSaveAndLoadRoundTrip() throws IOException {
        File root = makeProject();
        PersistentAnalysisCache disk = newCache();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        List<File> files = AndroidProjectScanner.scan(root, opts);
        String key = CacheKey.compute(root, files);

        List<JavaClassInfo> classes = new ArrayList<>();
        ClassIndex index = new ClassIndex();
        for (File f : files) {
            JavaClassInfo c = new JavaClassInfo();
            c.setPackageName("com.example");
            c.setSimpleName(stripJava(f.getName()));
            c.setKind(JavaClassInfo.Kind.CLASS);
            c.setDetailed(false);
            classes.add(c);
            index.put(c, f, ":app");
        }
        disk.save(key, classes, index);

        Optional<PersistentAnalysisCache.Snapshot> snap = disk.load(root, opts,
                ProgressListener.silent());
        assertTrue("初回保存後にロード Hit するはず", snap.isPresent());
        assertEquals(2, snap.get().getClasses().size());
        assertEquals(":app", snap.get().getIndex().module("com.example.Foo").orElse(null));
        assertEquals("com.example.Foo",
                snap.get().getIndex().source("com.example.Foo").get() != null
                        ? "com.example.Foo" : null);
    }

    @Test
    public void testLoadMissReturnsEmpty() throws IOException {
        File root = makeProject();
        PersistentAnalysisCache disk = newCache();
        // 保存してない状態でロード → empty
        Optional<PersistentAnalysisCache.Snapshot> snap = disk.load(root,
                new AndroidProjectScanner.Options(), ProgressListener.silent());
        assertFalse(snap.isPresent());
    }

    @Test
    public void testFileChangeInvalidatesCache() throws IOException {
        File root = makeProject();
        PersistentAnalysisCache disk = newCache();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        List<File> files = AndroidProjectScanner.scan(root, opts);
        String key = CacheKey.compute(root, files);
        disk.save(key, new ArrayList<>(), new ClassIndex());

        // 1 件ファイルを追加 → ハッシュ変化 → load miss
        File extra = new File(root, "src/main/java/com/example/Baz.java");
        writeFile(extra, "package com.example; public class Baz {}");
        Optional<PersistentAnalysisCache.Snapshot> snap = disk.load(root, opts,
                ProgressListener.silent());
        assertFalse("ファイルが増えたら別キーになりミス", snap.isPresent());
    }

    @Test
    public void testCorruptManifestFallsBack() throws IOException {
        File root = makeProject();
        PersistentAnalysisCache disk = newCache();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        List<File> files = AndroidProjectScanner.scan(root, opts);
        String key = CacheKey.compute(root, files);
        disk.save(key, new ArrayList<>(), new ClassIndex());

        // manifest.txt を破壊
        File dir = disk.directoryFor(key);
        File manifest = new File(dir, "manifest.txt");
        writeFile(manifest, "garbage\n");

        Optional<PersistentAnalysisCache.Snapshot> snap = disk.load(root, opts,
                ProgressListener.silent());
        assertFalse("破損 manifest はミス扱い", snap.isPresent());
    }

    @Test
    public void testInvalidateRemovesCache() throws IOException {
        File root = makeProject();
        PersistentAnalysisCache disk = newCache();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        List<File> files = AndroidProjectScanner.scan(root, opts);
        String key = CacheKey.compute(root, files);
        disk.save(key, new ArrayList<>(), new ClassIndex());

        disk.invalidate(root, opts);
        File dir = disk.directoryFor(key);
        assertFalse(dir.exists());
    }

    private static String stripJava(String n) {
        return n.endsWith(".java") ? n.substring(0, n.length() - 5) : n;
    }
}
