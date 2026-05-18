package padtools.app.uml;

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
        UmlMainFrame.launch(initialProject);
    }
}
