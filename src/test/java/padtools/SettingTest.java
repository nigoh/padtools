package padtools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Setting クラスのユニットテスト。
 *
 * <p>2.0 で PAD 用のフィールド (フォント / 色 / ツールバー設定) は削除済み。
 * 現在保持しているのはウィンドウ位置・サイズと分割ペイン位置のみ。</p>
 */
public class SettingTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDefaultWindowSize() {
        Setting setting = new Setting();
        assertEquals(1200, setting.getWindowWidth());
        assertEquals(800, setting.getWindowHeight());
        assertEquals(-1, setting.getWindowX());
        assertEquals(-1, setting.getWindowY());
    }

    @Test
    public void testWindowStateProperties() {
        Setting setting = new Setting();
        setting.setWindowX(100);
        setting.setWindowY(200);
        setting.setWindowWidth(1024);
        setting.setWindowHeight(768);
        setting.setMainSplitLocation(300);
        setting.setLeftSplitLocation(400);

        assertEquals(100, setting.getWindowX());
        assertEquals(200, setting.getWindowY());
        assertEquals(1024, setting.getWindowWidth());
        assertEquals(768, setting.getWindowHeight());
        assertEquals(300, setting.getMainSplitLocation());
        assertEquals(400, setting.getLeftSplitLocation());
    }

    @Test
    public void testSaveAndLoad() throws IOException {
        Setting original = new Setting();
        original.setWindowX(123);
        original.setWindowY(456);
        original.setWindowWidth(1024);
        original.setWindowHeight(768);
        original.setMainSplitLocation(280);
        original.setLeftSplitLocation(150);

        File file = tempFolder.newFile("settings.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals(123, loaded.getWindowX());
        assertEquals(456, loaded.getWindowY());
        assertEquals(1024, loaded.getWindowWidth());
        assertEquals(768, loaded.getWindowHeight());
        assertEquals(280, loaded.getMainSplitLocation());
        assertEquals(150, loaded.getLeftSplitLocation());
    }

    @Test
    public void testLoadIgnoresLegacyPadKeys() throws IOException {
        // 旧バージョンの XML に存在した PAD 専用キーを書き出して、読み込み時に
        // 無視されることを確認する (互換性: 既存ユーザの設定ファイルが壊れない)。
        File file = tempFolder.newFile("legacy.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"disableSaveMenu\">true</entry>"
                + "<entry key=\"editorFont.name\">Monospaced</entry>"
                + "<entry key=\"viewColor.rgb\">-16777216</entry>"
                + "<entry key=\"windowWidth\">999</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        // PAD 関連キーは無視され、ウィンドウ幅だけ反映される
        assertEquals(999, loaded.getWindowWidth());
    }
}
