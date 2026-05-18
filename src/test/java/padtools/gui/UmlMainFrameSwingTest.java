package padtools.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.SettingManager;
import padtools.app.uml.UmlMainFrame;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

/**
 * UmlMainFrame (Swing) のスモークテスト。
 *
 * <p>AssertJ-Swing で Robot 経由でメインウィンドウを起動し、最小プロジェクトを
 * 開いてツリーが構築されるところまでを検証する。ヘッドレス環境
 * ({@link GraphicsEnvironment#isHeadless()}) では Robot が動かないため
 * テスト全体をスキップする。CI で実行する場合は xvfb-run でラップするか、
 * 仮想ディスプレイを起動した状態で {@code DISPLAY} を設定すること。</p>
 *
 * <p>テストの主目的は「ロードパイプライン (ProjectAnalysisCache + ProjectTreePanel)
 * が EDT 上で完走する」回帰検出。GUI の見た目を 1 ピクセルずつ検証する
 * 性質のものではない。</p>
 */
public class UmlMainFrameSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FrameFixture window;

    @Before
    public void setup() {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        // UmlMainFrame は Main.getSetting() 経由で SettingManager にアクセスするため、
        // テスト単独で構築する場合は明示的に初期化しておく必要がある。
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @After
    public void teardown() {
        if (window != null) {
            window.cleanUp();
        }
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /** Foo / Bar 2 クラスを持つ最小プロジェクトを生成。 */
    private File makeTinyProject() throws IOException {
        File root = tmp.newFolder("Tiny");
        File pkg = new File(root, "src/main/java/com/demo");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "Foo.java"),
                "package com.demo; public class Foo { public void hello() {} }");
        writeFile(new File(pkg, "Bar.java"),
                "package com.demo; public class Bar extends Foo {}");
        return root;
    }

    @Test
    public void testMainFrameOpensAndLoadsProject() throws IOException, InterruptedException {
        File project = makeTinyProject();

        // EDT で UmlMainFrame を組み立て、初期プロジェクトを渡して構築する
        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(project));
        window = new FrameFixture(frame);
        window.show();

        // 非同期 SwingWorker でロードしているので最大 30 秒待つ
        long deadline = System.currentTimeMillis() + 30_000;
        JTreeFixture tree = window.tree();
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            int rows = GuiActionRunner.execute(() -> tree.target().getRowCount());
            if (rows >= 2) {
                ready = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue("プロジェクトツリーが構築されたはず", ready);
    }
}
