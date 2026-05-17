package padtools.editor;

import org.junit.Test;

import java.awt.event.InputEvent;
import java.lang.reflect.Field;

import static org.junit.Assert.assertTrue;

/**
 * MENU_MASK の初期化が HeadlessException で死なないことを確認する。
 * ヘッドレスでもクラスをロードできる必要がある (CLI 経由で MainFrame クラスが
 * ロードされる可能性があるため)。
 */
public class MenuMaskHeadlessTest {

    /** MainFrame をクラスロードしても HeadlessException が出ない */
    @Test
    public void testMainFrameClassLoadDoesNotThrow() throws Exception {
        Class<?> cls = Class.forName("padtools.editor.MainFrame");
        Field f = cls.getDeclaredField("MENU_MASK");
        f.setAccessible(true);
        int mask = f.getInt(null);
        // Win/Linux なら CTRL_DOWN_MASK、macOS なら META_DOWN_MASK
        assertTrue("MENU_MASK should be Ctrl or Meta",
                mask == InputEvent.CTRL_DOWN_MASK || mask == InputEvent.META_DOWN_MASK);
    }

    /** SPDEditor も同じく */
    @Test
    public void testSPDEditorClassLoadDoesNotThrow() throws Exception {
        Class<?> cls = Class.forName("padtools.editor.SPDEditor");
        Field f = cls.getDeclaredField("MENU_MASK");
        f.setAccessible(true);
        int mask = f.getInt(null);
        assertTrue("MENU_MASK should be Ctrl or Meta",
                mask == InputEvent.CTRL_DOWN_MASK || mask == InputEvent.META_DOWN_MASK);
    }
}
