// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.File;

/**
 * UML 専用 GUI のエントリポイント。
 *
 * <p>{@link padtools.Main} の既定 (引数なし) 起動経路、もしくは
 * {@code --ui=uml} 隠しフラグ経由から呼び出される。EDT で
 * {@link UmlMainFrame} を生成して表示する。</p>
 */
public final class UmlApp {

    private UmlApp() {
    }

    /** UML GUI を起動する。{@code initialProject} は null 可。 */
    public static void launch(File initialProject) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException
                 | InstantiationException | IllegalAccessException ex) {
            // システム L&F が使えない環境では既定 L&F のまま続行
        }
        launchWithSplash(initialProject);
    }

    /** 起動スプラッシュ (GIF) を中央表示しつつメインウィンドウを起動する。 */
    private static void launchWithSplash(File initialProject) {
        // 起動直後に一瞬で消えないよう、スプラッシュの最低表示時間を確保する。
        final int minSplashMillis = 900;
        SwingUtilities.invokeLater(() -> {
            SplashWindow splash = SplashWindow.display();
            UmlMainFrame frame = new UmlMainFrame(initialProject);
            frame.setVisible(true);
            Timer timer = new Timer(minSplashMillis, e -> splash.close());
            timer.setRepeats(false);
            timer.start();
        });
    }
}
