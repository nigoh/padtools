package padtools;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Setting クラスのユニットテスト。
 */
public class SettingTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDefaultValues() {
        Setting setting = new Setting();
        assertFalse(setting.isDisableSaveMenu());
        assertFalse(setting.isDisableToolbar());
        assertNotNull(setting.getEditorFont());
        assertNotNull(setting.getViewFont());
        assertNotNull(setting.getViewColor());
    }

    @Test
    public void testSettersAndGetters() {
        Setting setting = new Setting();
        setting.setDisableSaveMenu(true);
        assertTrue(setting.isDisableSaveMenu());

        setting.setDisableToolbar(true);
        assertTrue(setting.isDisableToolbar());

        Font font = new Font("Monospaced", Font.BOLD, 16);
        setting.setEditorFont(font);
        assertEquals(font, setting.getEditorFont());

        Color color = Color.RED;
        setting.setViewColor(color);
        assertEquals(color, setting.getViewColor());
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
        original.setDisableSaveMenu(true);
        original.setWindowWidth(1024);
        original.setWindowHeight(768);

        File file = tempFolder.newFile("settings.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertTrue(loaded.isDisableSaveMenu());
        assertEquals(1024, loaded.getWindowWidth());
        assertEquals(768, loaded.getWindowHeight());
    }
}
