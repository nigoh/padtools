package padtools.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.SettingManager;
import padtools.app.uml.DiagramKind;
import padtools.app.uml.TreeNodeOpenRequest;
import padtools.app.uml.UmlMainFrame;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;

import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 動的タブ機能の E2E 統合テスト。
 *
 * <p>ツリーノードをタブとして開く ({@code addOrFocusTab}) → タブにフォーカスが移ると
 * 左ツリーの由来ノードがハイライトされる (タブ ↔ リスト連動)、同一ノードの再オープンは
 * 既存タブにフォーカスするだけ (重複なし)、という挙動を実フレームで検証する。</p>
 *
 * <p>ヘッドレス環境では {@link Assume} でスキップ。{@code xvfb-run -a ./gradlew test} で実行。</p>
 */
public class UmlMainFrameTabLinkageIT {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private UmlMainFrame frame;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @After
    public void teardown() {
        if (frame != null) {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private File buildProject() throws IOException {
        File root = tmp.newFolder("TabSample");
        write(new File(root, "settings.gradle"), "rootProject.name = 'TabSample'\n");
        File pkg = new File(root, "src/main/java/com/demo");
        write(new File(pkg, "Foo.java"),
                "package com.demo; public class Foo { public void hello() {} }");
        write(new File(pkg, "Bar.java"),
                "package com.demo; public class Bar extends Foo {}");
        return root;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    private void openInNewTab(Object controller, TreeNodeOpenRequest req) throws Exception {
        Method m = controller.getClass().getMethod("onTreeOpenInNewTab", TreeNodeOpenRequest.class);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(controller, req);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static String selectionLabel(JTree tree) {
        return GuiActionRunner.execute(() -> {
            TreePath p = tree.getSelectionPath();
            return p == null ? "(none)" : String.valueOf(p.getLastPathComponent());
        });
    }

    private void selectKind(Object controller, DiagramKind kind) throws Exception {
        Method m = controller.getClass().getMethod("selectDiagramKind", DiagramKind.class);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(controller, kind);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static String focusedTitle(JTabbedPane tabs) {
        return GuiActionRunner.execute(() -> {
            int i = tabs.getSelectedIndex();
            return i < 0 ? "(none)" : tabs.getTitleAt(i);
        });
    }

    @Test
    public void tabFocusHighlightsSourceNodeAndDedupes() throws Exception {
        File project = buildProject();
        frame = GuiActionRunner.execute(() -> {
            UmlMainFrame f = new UmlMainFrame(project);
            f.setSize(1200, 800);
            f.setVisible(true);
            return f;
        });

        Object cache = field(frame, "cache");
        Method isLoaded = cache.getClass().getMethod("isLoaded");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline
                && !(Boolean) isLoaded.invoke(cache)) {
            Thread.sleep(150);
        }
        assertTrue("project should load", (Boolean) isLoaded.invoke(cache));
        Thread.sleep(500);

        Object controller = field(frame, "controller");
        JTabbedPane mainTabs = field(frame, "mainTabs");
        Object treePanel = field(frame, "treePanel");
        JTree tree = field(treePanel, "tree");
        Object tabPane = field(frame, "tabPane");
        Map<?, ?> openTabs = field(tabPane, "openTabs");

        @SuppressWarnings("unchecked")
        List<JavaClassInfo> classes = (List<JavaClassInfo>) cache.getClass()
                .getMethod("getClasses").invoke(cache);
        JavaClassInfo foo = classes.stream()
                .filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        JavaClassInfo bar = classes.stream()
                .filter(c -> "Bar".equals(c.getSimpleName())).findFirst().orElseThrow();

        // 起動直後に既定タブ (Common) が 1 枚開いているため、ここを基準に増分で検証する。
        int baseTabs = GuiActionRunner.execute(mainTabs::getTabCount);
        int baseOpen = openTabs.size();

        // --- open Foo as a tab; it is auto-focused -> tree should highlight Foo ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(foo));
        Thread.sleep(800);
        assertEquals("one more dynamic tab expected", baseOpen + 1, openTabs.size());
        assertEquals("tab count should grow by one",
                baseTabs + 1, (int) GuiActionRunner.execute(mainTabs::getTabCount));
        assertTrue("focusing Foo tab should highlight Foo node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Foo"));

        // --- open Bar as a tab; focus moves to Bar -> tree should highlight Bar ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(bar));
        Thread.sleep(800);
        assertEquals(baseOpen + 2, openTabs.size());
        assertTrue("focusing Bar tab should highlight Bar node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Bar"));

        // --- reopen Foo: existing tab is re-focused, no new tab, tree back to Foo ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(foo));
        Thread.sleep(800);
        assertEquals("reopening Foo must not create a new tab", baseOpen + 2, openTabs.size());
        assertTrue("re-focusing Foo tab should highlight Foo node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Foo"));
    }

    @Test
    public void toolbarActsOnFocusedMethodTab() throws Exception {
        File project = buildProject();
        frame = GuiActionRunner.execute(() -> {
            UmlMainFrame f = new UmlMainFrame(project);
            f.setSize(1200, 800);
            f.setVisible(true);
            return f;
        });

        Object cache = field(frame, "cache");
        Method isLoaded = cache.getClass().getMethod("isLoaded");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && !(Boolean) isLoaded.invoke(cache)) {
            Thread.sleep(150);
        }
        assertTrue((Boolean) isLoaded.invoke(cache));
        Thread.sleep(500);

        Object controller = field(frame, "controller");
        JTabbedPane mainTabs = field(frame, "mainTabs");
        Object tabPane = field(frame, "tabPane");
        Map<?, ?> openTabs = field(tabPane, "openTabs");

        @SuppressWarnings("unchecked")
        List<JavaClassInfo> classes = (List<JavaClassInfo>) cache.getClass()
                .getMethod("getClasses").invoke(cache);
        JavaClassInfo foo = classes.stream()
                .filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        JavaMethodInfo hello = foo.getMethods().get(0);

        // 起動直後に既定タブ (Common) が 1 枚開いているため、ここを基準に増分で検証する。
        int baseOpen = openTabs.size();

        // open Foo.hello as a sequence tab
        openInNewTab(controller, TreeNodeOpenRequest.method(foo, hello, DiagramKind.SEQUENCE));
        Thread.sleep(800);
        assertEquals(baseOpen + 1, openTabs.size());

        // with the method tab focused, the toolbar opens the same method's other diagrams
        selectKind(controller, DiagramKind.ACTIVITY);
        Thread.sleep(800);
        assertEquals("Activity should open as a new tab", baseOpen + 2, openTabs.size());
        assertTrue("focused tab should be the activity view, got " + focusedTitle(mainTabs),
                focusedTitle(mainTabs).contains("(act)"));

        selectKind(controller, DiagramKind.CALLGRAPH);
        Thread.sleep(800);
        assertEquals("Call graph should open as a new tab", baseOpen + 3, openTabs.size());
        assertTrue("focused tab should be the call-graph view, got " + focusedTitle(mainTabs),
                focusedTitle(mainTabs).contains("(cg)"));

        // re-selecting an already-open kind just focuses the existing tab (dedupe)
        selectKind(controller, DiagramKind.ACTIVITY);
        Thread.sleep(600);
        assertEquals("re-selecting Activity must not create a new tab", baseOpen + 3, openTabs.size());
        assertTrue(focusedTitle(mainTabs).contains("(act)"));
    }
}
