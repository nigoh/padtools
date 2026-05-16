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

    @Test
    public void testClassDiagramCli() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile, "package x; class Foo { Bar b; void m() {} } class Bar {}");
        File outPuml = new File(tmp.getRoot(), "foo.puml");
        Main.main(new String[]{"padtools", "-c", "-o", outPuml.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("class \"x.Foo\""));
        assertTrue(puml, puml.contains("class \"x.Bar\""));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSequenceDiagramCli() throws Exception {
        File javaFile = tmp.newFile("Bar.java");
        writeFile(javaFile, "class Bar { Service s; void run() { s.go(); } }");
        File outPuml = new File(tmp.getRoot(), "bar.puml");
        Main.main(new String[]{"padtools", "-q", "Bar.run", "-o", outPuml.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("Caller -> Bar: run()"));
        assertTrue(puml, puml.contains("Bar -> Service: go()"));
    }

    @Test
    public void testVerboseFlagEmitsToStderr() throws Exception {
        File pkg = new File(tmp.getRoot(), "vp/src/main/java/p");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "A.java"), "package p; class A { void f() {} }");
        File outSpd = new File(tmp.getRoot(), "verbose.spd");
        Main.main(new String[]{
                "padtools", "-J", "-v", "-o", outSpd.getAbsolutePath(),
                new File(tmp.getRoot(), "vp").getAbsolutePath()});
        String err = stderrBuf.toString(StandardCharsets.UTF_8);
        // verbose 時は "processed N java file(s)" のサマリが stderr に出る
        assertTrue("expected processed summary in stderr: " + err,
                err.contains("processed") && err.contains("file"));
    }

    @Test
    public void testNonVerboseSuppressesLogs() throws Exception {
        File pkg = new File(tmp.getRoot(), "np/src/main/java/p");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "A.java"), "package p; class A { void f() {} }");
        File outSpd = new File(tmp.getRoot(), "quiet.spd");
        Main.main(new String[]{
                "padtools", "-J", "-o", outSpd.getAbsolutePath(),
                new File(tmp.getRoot(), "np").getAbsolutePath()});
        String err = stderrBuf.toString(StandardCharsets.UTF_8);
        // -v 無しのときは "processed" サマリが出ない
        assertFalse("did not expect summary without -v: " + err,
                err.contains("processed"));
    }

    @Test
    public void testClassDiagramCliWithAidl() throws Exception {
        File aidlFile = tmp.newFile("ICar.aidl");
        writeFile(aidlFile, "package android.car; interface ICar { int getVersion(); }");
        File outPuml = new File(tmp.getRoot(), "car.puml");
        Main.main(new String[]{"padtools", "-c", "-o", outPuml.getAbsolutePath(),
                aidlFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("<<AIDL>>"));
        assertTrue(puml, puml.contains("interface \"android.car.ICar\""));
        assertTrue(puml, puml.contains("getVersion(): int"));
    }
}
