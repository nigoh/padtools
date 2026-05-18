package padtools.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import padtools.SettingManager;
import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.app.uml.SvgPreviewPanel;
import padtools.app.uml.UmlMainFrame;

import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UmlMainFrame をヘッドフルで実起動し、クラス図 → 右クリックでメソッド一覧 →
 * 選択したメソッドを起点にしたシーケンス図、という対話フローを E2E で動かす統合テスト。
 *
 * <p>サンプルプロジェクトは {@code src/test/resources/samples/easypermissions/}
 * 配下に同梱した Apache 2.0 ライセンスのソースを、Gradle/Android 標準レイアウト
 * ({@code src/main/java/...}) に組み直して一時ディレクトリに展開する。</p>
 *
 * <p>ヘッドレス環境では {@link Assume} でスキップ。CI で実行する場合は
 * {@code xvfb-run -a ./gradlew test} のように仮想ディスプレイを与えること。</p>
 *
 * <p>各ステップで {@code build/playwright/gui/} 配下に PNG スクリーンショットを保存する。</p>
 * <ul>
 *   <li>{@code 01-class-diagram.png} - 初期表示のクラス図</li>
 *   <li>{@code 02-method-popup.png} - MainActivity 右クリックで開くメソッドポップアップ</li>
 *   <li>{@code 03-sequence-diagram.png} - 選択したメソッドのシーケンス図</li>
 * </ul>
 */
public class UmlMainFrameRightClickIT {

    private static File screenshotDir;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void requireDisplay() {
        Assume.assumeFalse(
                "DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
        screenshotDir = new File("build/playwright/gui");
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            throw new IllegalStateException(
                    "could not create screenshot dir: " + screenshotDir);
        }
    }

    @AfterClass
    public static void closeAllWindows() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    private static void copyResource(String resourcePath, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        try (InputStream in =
                     UmlMainFrameRightClickIT.class.getResourceAsStream(resourcePath);
             FileOutputStream out = new FileOutputStream(dest)) {
            assertNotNull("missing resource: " + resourcePath, in);
            in.transferTo(out);
        }
    }

    private static void writeFile(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /**
     * {@code src/test/resources/samples/easypermissions} の凍結コピーを、PadTools の
     * AndroidProjectScanner が認識する Gradle/Android プロジェクトレイアウトに
     * 組み直して一時ディレクトリへ書き出す。
     */
    private File buildSampleProject() throws IOException {
        File root = tmp.newFolder("EasyPermissionsSample");

        // build.gradle (Apache 2.0 サンプルそのまま)
        copyResource("/samples/easypermissions/app-build.gradle",
                new File(root, "build.gradle"));
        // settings.gradle はモジュール検出に必要
        writeFile(new File(root, "settings.gradle"),
                "rootProject.name = 'EasyPermissionsSample'\n");

        // src/main/AndroidManifest.xml
        copyResource("/samples/easypermissions/AndroidManifest.xml",
                new File(root, "src/main/AndroidManifest.xml"));

        // src/main/java/pub/devrel/easypermissions/sample/MainActivity.java
        File samplePkg = new File(root,
                "src/main/java/pub/devrel/easypermissions/sample");
        copyResource("/samples/easypermissions/MainActivity.java",
                new File(samplePkg, "MainActivity.java"));

        // src/main/java/pub/devrel/easypermissions/{EasyPermissions,...}.java
        File libPkg = new File(root, "src/main/java/pub/devrel/easypermissions");
        copyResource("/samples/easypermissions/EasyPermissions.java",
                new File(libPkg, "EasyPermissions.java"));
        copyResource("/samples/easypermissions/AfterPermissionGranted.java",
                new File(libPkg, "AfterPermissionGranted.java"));
        copyResource("/samples/easypermissions/AppSettingsDialog.java",
                new File(libPkg, "AppSettingsDialog.java"));

        // src/main/aidl/com/example/android/apis/app/IRemoteService.aidl
        File aidlPkg = new File(root,
                "src/main/aidl/com/example/android/apis/app");
        copyResource("/samples/aidl/IRemoteService.aidl",
                new File(aidlPkg, "IRemoteService.aidl"));

        return root;
    }

    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        T value = (T) f.get(target);
        return value;
    }

    /** UmlMainFrame 内の SvgPreviewPanel をフィールドアクセスで取得する。 */
    private SvgPreviewPanel previewPanelOf(UmlMainFrame frame) throws Exception {
        return getField(frame, "previewPanel");
    }

    /**
     * フレーム配下にあるすべてのウィンドウ (主フレーム + ポップアップを載せた
     * ヘビーウェイトウィンドウ) を内包する矩形を返す。スクショ範囲計算用。
     */
    private static Rectangle unionOfVisibleWindows() {
        Rectangle r = null;
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) {
                continue;
            }
            Rectangle b = w.getBounds();
            r = (r == null) ? new Rectangle(b) : r.union(b);
        }
        return r;
    }

    /** Robot でスクリーン上の現在見えているウィンドウ群をキャプチャして PNG 保存。 */
    private static void capture(String fileName) throws Exception {
        // EDT 上の描画が終わるのを待つ (短いポーズ + sync)。
        Thread.sleep(300);
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing()) {
                    w.toFront();
                }
            }
        });
        Thread.sleep(150);
        Rectangle area = unionOfVisibleWindows();
        assertNotNull("no visible window for screenshot", area);
        BufferedImage img = new Robot().createScreenCapture(area);
        File out = new File(screenshotDir, fileName);
        ImageIO.write(img, "png", out);
    }

    /** {@link JPopupMenu} を Swing のメニューセレクションマネージャから取得する。 */
    private static JPopupMenu findVisiblePopup() {
        MenuElement[] path =
                javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement e : path) {
            if (e instanceof JPopupMenu) {
                return (JPopupMenu) e;
            }
        }
        // フォールバック: 表示中のウィンドウから JPopupMenu を含むものを探す
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) {
                continue;
            }
            JPopupMenu p = findPopupInContainer(w);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static JPopupMenu findPopupInContainer(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JPopupMenu) {
                return (JPopupMenu) child;
            }
            if (child instanceof Container) {
                JPopupMenu p = findPopupInContainer((Container) child);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    @Test
    public void testClassDiagramRightClickToSequenceDiagram() throws Exception {
        File project = buildSampleProject();

        // ---------- (1) UmlMainFrame 起動 ----------
        UmlMainFrame frame = GuiActionRunner.execute(() -> {
            UmlMainFrame f = new UmlMainFrame(project);
            f.setSize(1400, 900);
            f.setLocation(0, 0);
            f.setVisible(true);
            return f;
        });

        // ---------- (2) 解析完了 + クラス図描画 + LinkAreas 反映を待つ ----------
        SvgPreviewPanel preview = previewPanelOf(frame);
        long deadline = System.currentTimeMillis() + 60_000;
        List<LinkArea> areas;
        while (true) {
            areas = preview.getLinkAreas();
            Object cache = getField(frame, "cache");
            boolean loaded = (boolean) cache.getClass()
                    .getMethod("isLoaded").invoke(cache);
            if (loaded && areas != null && !areas.isEmpty()) {
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(
                        "timeout: cache loaded=" + loaded
                                + ", linkAreas=" + (areas == null ? -1 : areas.size()));
            }
            Thread.sleep(250);
        }
        capture("01-class-diagram.png");

        // ---------- (3) MainActivity のリンク領域を特定して右クリックを発火 ----------
        LinkArea target = null;
        for (LinkArea a : areas) {
            String href = a.getHref();
            if (href != null && href.contains("MainActivity")) {
                target = a;
                break;
            }
        }
        assertNotNull("MainActivity link not found in: " + areas, target);

        final LinkArea hit = target;
        // SVG 座標 → パネル座標 (zoomLevel 倍)
        double zoom = (double) preview.getClass()
                .getMethod("getZoomLevel").invoke(preview);
        int px = (int) Math.round((hit.getX() + hit.getWidth() / 2.0) * zoom);
        int py = (int) Math.round((hit.getY() + hit.getHeight() / 2.0) * zoom);

        // ポップアップを表示する。SvgPreviewPanel#maybeFireLinkPopup 相当を
        // listenerに直接渡すと心地よい (BUTTON3/popupTrigger 判定の差異を避ける)。
        AtomicReference<JPopupMenu> popupRef = new AtomicReference<>();
        GuiActionRunner.execute(() -> {
            try {
                Field listenerField =
                        preview.getClass().getDeclaredField("linkPopupListener");
                listenerField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.function.BiConsumer<LinkArea, MouseEvent> listener =
                        (java.util.function.BiConsumer<LinkArea, MouseEvent>)
                                listenerField.get(preview);
                assertNotNull("preview has no linkPopupListener wired", listener);
                MouseEvent ev = new MouseEvent(preview, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(),
                        MouseEvent.BUTTON3_DOWN_MASK,
                        px, py, 1, true /*popupTrigger*/, MouseEvent.BUTTON3);
                listener.accept(hit, ev);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        // ---------- (4) ポップアップ表示を確認しスクショ ----------
        JPopupMenu popup = null;
        long popupDeadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < popupDeadline) {
            popup = findVisiblePopup();
            if (popup != null && popup.isShowing()) {
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull("method popup did not appear", popup);
        assertTrue("popup should be visible", popup.isShowing());
        assertTrue("popup must contain >= 1 method item",
                popup.getComponentCount() > 0);
        capture("02-method-popup.png");

        // ---------- (5) "onCreate" メニュー項目を発火 ----------
        JMenuItem onCreate = null;
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem) {
                JMenuItem mi = (JMenuItem) c;
                if (mi.getText() != null && mi.getText().startsWith("onCreate")) {
                    onCreate = mi;
                    break;
                }
            }
        }
        assertNotNull("onCreate menu item not found in popup", onCreate);
        final JMenuItem toClick = onCreate;
        GuiActionRunner.execute(() -> {
            // ポップアップを閉じてから ActionEvent を発火する
            javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            toClick.getActionListeners()[0].actionPerformed(
                    new ActionEvent(toClick, ActionEvent.ACTION_PERFORMED,
                            toClick.getActionCommand()));
        });

        // ---------- (6) シーケンス図モードへ切替→描画完了を待つ ----------
        // 注意: refreshDiagram() は 300ms の Timer 越しに SwingWorker を起動するため
        // currentPuml が "@startuml" になっても SVG はまだ前のクラス図のことがある。
        // status JLabel が "Sequence Diagram rendered" に変化するまで待ち合わせる。
        javax.swing.JLabel status = getField(frame, "status");
        long seqDeadline = System.currentTimeMillis() + 60_000;
        boolean ready = false;
        while (System.currentTimeMillis() < seqDeadline) {
            String s = GuiActionRunner.execute(status::getText);
            Object kind = getField(frame, "currentKind");
            if ("SEQUENCE".equals(kind.toString())
                    && s != null
                    && s.contains("Sequence")
                    && s.contains("rendered")) {
                ready = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue("sequence diagram render did not complete: status="
                + GuiActionRunner.execute(status::getText), ready);
        Object currentKindAfter = getField(frame, "currentKind");
        assertEquals("currentKind should be SEQUENCE",
                "SEQUENCE", currentKindAfter.toString());
        // 念のため paint 1 サイクル分待つ
        final SvgPreviewPanel previewForRepaint = preview;
        GuiActionRunner.execute(() -> previewForRepaint.paintImmediately(
                previewForRepaint.getBounds()));
        capture("03-sequence-diagram.png");

        // ---------- (7) 後片付け ----------
        GuiActionRunner.execute(frame::dispose);
    }
}
