package padtools;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Main CLI の Java インポート機能 (--java / --java-project) を検証する。
 */
public class MainCliTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
    private PrintStream origOut;
    private PrintStream origErr;
    private InputStream origIn;

    @Before
    public void redirectIo() {
        origOut = System.out;
        origErr = System.err;
        origIn = System.in;
        System.setOut(new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderrBuf, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreIo() {
        System.setOut(origOut);
        System.setErr(origErr);
        System.setIn(origIn);
    }

    private String stdout() {
        return stdoutBuf.toString(StandardCharsets.UTF_8);
    }

    private void writeFile(File f, String content) throws Exception {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testJavaFileToSpdOnStdout() throws Exception {
        File javaFile = tmp.newFile("Hello.java");
        writeFile(javaFile, "class Hello { void m() { greet(); } }");
        File outSpd = new File(tmp.getRoot(), "out.spd");
        // -j with -o (write to SPD file)
        Main.main(new String[]{
                "padtools", "-j", "-o", outSpd.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String spd = new String(Files.readAllBytes(outSpd.toPath()), StandardCharsets.UTF_8);
        assertTrue(spd, spd.contains(":terminal Hello.m()"));
        assertTrue(spd, spd.contains("greet()"));
    }

    @Test
    public void testJavaFileToStdoutWhenNoOutput() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile, "class Foo { int bar() { return 42; } }");
        Main.main(new String[]{
                "padtools", "-j", javaFile.getAbsolutePath()});
        String out = stdout();
        assertTrue(out, out.contains("Foo.bar()"));
        assertTrue(out, out.contains("return 42"));
    }

    @Test
    public void testJavaFileToTxtOutput() throws Exception {
        File javaFile = tmp.newFile("Bar.java");
        writeFile(javaFile, "class Bar { void run() { go(); } }");
        File outTxt = new File(tmp.getRoot(), "bar.txt");
        Main.main(new String[]{
                "padtools", "-j", "-o", outTxt.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String text = new String(Files.readAllBytes(outTxt.toPath()), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("Bar.run()"));
    }

    @Test
    public void testJavaProjectMode() throws Exception {
        // src/main/java/com/A.java と B.java を作る
        File pkg = new File(tmp.getRoot(), "proj/src/main/java/com");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "A.java"), "package com; class A { void a() { x(); } }");
        writeFile(new File(pkg, "B.java"), "package com; class B { void b() { y(); } }");

        File outSpd = new File(tmp.getRoot(), "project.spd");
        Main.main(new String[]{
                "padtools", "-J", "-o", outSpd.getAbsolutePath(),
                new File(tmp.getRoot(), "proj").getAbsolutePath()});
        String spd = new String(Files.readAllBytes(outSpd.toPath()), StandardCharsets.UTF_8);
        assertTrue(spd, spd.contains("A.a()"));
        assertTrue(spd, spd.contains("B.b()"));
        // ヘッダコメント
        assertTrue(spd, spd.contains("# === A.java ==="));
        assertTrue(spd, spd.contains("# === B.java ==="));
    }

    @Test
    public void testJavaToPngImage() throws Exception {
        File javaFile = tmp.newFile("Img.java");
        writeFile(javaFile, "class Img { void m() { if (x) a(); else b(); } }");
        File outPng = new File(tmp.getRoot(), "img.png");
        Main.main(new String[]{
                "padtools", "-j", "-o", outPng.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        assertTrue("PNG file must exist", outPng.exists());
        assertTrue("PNG file must have content", outPng.length() > 0);
    }

    @Test
    public void testJavaFromStdin() throws Exception {
        String javaSrc = "class S { void m() { do_it(); } }";
        System.setIn(new ByteArrayInputStream(javaSrc.getBytes(StandardCharsets.UTF_8)));
        Main.main(new String[]{"padtools", "-j"});
        String out = stdout();
        assertTrue(out, out.contains("S.m()"));
        assertTrue(out, out.contains("do_it()"));
    }
}
