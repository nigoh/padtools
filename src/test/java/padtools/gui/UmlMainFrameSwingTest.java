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

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
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

    /**
     * 左ペインのツリーで「クラス / パッケージ / モジュール」ノードをクリックしたとき、
     * 何かしらのフィードバック (status 文言の更新) が起きることを確認する回帰テスト。
     *
     * <p>従来は class / package / module ノードへの左クリックがどこにも繋がっておらず
     * 「何も反応しない」状態だった。最低限「Scope: ..." がステータスバーに出る」までを
     * GUI 経由で検証する。</p>
     */
    @Test
    public void testTreeNodeClickFiresScopeChange()
            throws IOException, InterruptedException, ReflectiveOperationException {
        File project = makeTinyProject();

        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(project));
        window = new FrameFixture(frame);
        window.show();

        JTreeFixture tree = window.tree();
        // ロード完了 (ルート以下 2 行以上展開済み) を待つ
        long deadline = System.currentTimeMillis() + 30_000;
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

        JLabel status = getPrivate(frame, "status");

        // モジュールノード ([module] ...) を選択 → "Scope: module ..." がステータスに出るはず
        TreePath modulePath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "[module]"));
        assertNotNull("module ノードが見つからない", modulePath);
        clickPath(tree, modulePath);
        assertTrue("モジュールクリックで status が更新されない: " + status.getText(),
                waitForStatusContains(status, "Scope: module", 5_000));

        // パッケージノード (com.demo (...)) を選択 → "Scope: package com.demo" になるはず
        TreePath packagePath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "com.demo"));
        assertNotNull("package ノードが見つからない", packagePath);
        clickPath(tree, packagePath);
        assertTrue("パッケージクリックで status が更新されない: " + status.getText(),
                waitForStatusContains(status, "Scope: package com.demo", 5_000));

        // パッケージを展開してクラスノードを露出させた上でクリック
        GuiActionRunner.execute(() -> tree.target().expandPath(packagePath));
        TreePath classPath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "[C] Foo"));
        assertNotNull("class ノード [C] Foo が見つからない", classPath);
        clickPath(tree, classPath);
        assertTrue("クラスクリックで status が更新されない: " + status.getText(),
                waitForStatusContains(status, "Scope: class Foo", 5_000));
    }

    /** Swing ツリーで「先頭が prefix で始まるノード」最初のひとつへの TreePath を返す。 */
    private static TreePath findPathByPrefix(JTree tree, String prefix) {
        TreeModel model = tree.getModel();
        Object root = model.getRoot();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (p == null) {
                continue;
            }
            Object last = p.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode) {
                Object u = ((DefaultMutableTreeNode) last).getUserObject();
                String text = String.valueOf(u);
                if (text.startsWith(prefix)) {
                    return p;
                }
            }
        }
        // 行未生成のサブツリーは expand してから再探索 (深さ 1 段だけ)
        for (int i = 0; i < model.getChildCount(root); i++) {
            tree.expandRow(i);
        }
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (p == null) {
                continue;
            }
            Object last = p.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode) {
                String text = String.valueOf(
                        ((DefaultMutableTreeNode) last).getUserObject());
                if (text.startsWith(prefix)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** EDT で TreePath を選択する (TreeSelectionListener を発火させるため)。 */
    private static void clickPath(JTreeFixture treeFixture, TreePath path) {
        GuiActionRunner.execute(() -> {
            treeFixture.target().setSelectionPath(path);
        });
    }

    /** status JLabel に指定文字列が含まれるまで待ち合わせる。 */
    private static boolean waitForStatusContains(JLabel status, String needle,
                                                  long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String s = GuiActionRunner.execute(status::getText);
            if (s != null && s.contains(needle)) {
                return true;
            }
            Thread.sleep(80);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivate(Object target, String name)
            throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
