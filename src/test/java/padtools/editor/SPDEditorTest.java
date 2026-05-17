package padtools.editor;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import padtools.SettingManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 操作性改善で追加した SPDEditor の挙動を検証する。
 *
 * 多くのテストが Swing コンポーネントを生成するため、ヘッドレス環境では
 * AssumeNotHeadless で除外する。Xvfb 配下では通る。
 */
public class SPDEditorTest {

    @BeforeClass
    public static void initSettings() {
        // SPDEditor は Main.getSetting() を参照するので初期化する
        SettingManager.initialize();
    }

    @Before
    public void skipIfHeadless() {
        Assume.assumeFalse("Skipping GUI test in headless env",
                GraphicsEnvironment.isHeadless());
    }

    private SPDEditor newEditor() throws Exception {
        SPDEditor[] holder = new SPDEditor[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new SPDEditor());
        return holder[0];
    }

    /** 複数行選択時の Tab がブロックインデントとして機能する */
    @Test
    public void testBlockIndentMultilineSelection() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("foo\nbar\nbaz\n");
            // 1〜3行目を選択 (caret は 3行目の末尾より手前)
            editor.setSelectionStart(0);
            editor.setSelectionEnd(editor.getDocument().getLength() - 1);
            // smartTab アクションを直接呼ぶ
            editor.getActionMap().get("smartTab").actionPerformed(
                    new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "smartTab"));
        });
        assertEquals("\tfoo\n\tbar\n\tbaz\n", editor.getText());
    }

    /** 単一行 (= 1行内のキャレットのみ) では普通に Tab 文字が挿入される */
    @Test
    public void testTabInsertsCharOnSingleLine() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("foo");
            editor.setCaretPosition(3);
            editor.getActionMap().get("smartTab").actionPerformed(
                    new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "smartTab"));
        });
        assertEquals("foo\t", editor.getText());
    }

    /** Shift+Tab で行頭のタブが取り除かれる (アウトデント) */
    @Test
    public void testBlockOutdentRemovesLeadingTab() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("\tfoo\n\tbar\n\tbaz\n");
            editor.setSelectionStart(0);
            editor.setSelectionEnd(editor.getDocument().getLength() - 1);
            editor.getActionMap().get("smartOutdent").actionPerformed(
                    new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "smartOutdent"));
        });
        assertEquals("foo\nbar\nbaz\n", editor.getText());
    }

    /** タブが無い行ではアウトデントしても変化しない */
    @Test
    public void testOutdentIsNoOpWhenNoLeadingTab() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("foo\nbar\n");
            editor.setSelectionStart(0);
            editor.setSelectionEnd(editor.getDocument().getLength() - 1);
            editor.getActionMap().get("smartOutdent").actionPerformed(
                    new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "smartOutdent"));
        });
        assertEquals("foo\nbar\n", editor.getText());
    }

    /** Find 検索: マッチした文字列が選択される */
    @Test
    public void testFindSelectsMatch() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("alpha bravo charlie bravo delta");
            editor.setCaretPosition(0);
            // reflection-free: setLastSearch 経由ではなく直接 findNext のみ呼ぶには
            // package-private が必要。ここでは showFindDialog の prompt を介さず
            // 直接 lastSearchText を反映するヘルパーを呼ぶ代わりに、
            // 反射でフィールドへ書き込む。
            try {
                java.lang.reflect.Field f = SPDEditor.class.getDeclaredField("lastSearchText");
                f.setAccessible(true);
                f.set(editor, "bravo");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            editor.findNext();
        });
        assertEquals(6, editor.getSelectionStart());
        assertEquals(11, editor.getSelectionEnd());
        assertEquals("bravo", editor.getSelectedText());
    }

    /** Find: 末尾でラップして先頭からまた検索する */
    @Test
    public void testFindWrapsAround() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("alpha bravo charlie bravo delta");
            try {
                java.lang.reflect.Field f = SPDEditor.class.getDeclaredField("lastSearchText");
                f.setAccessible(true);
                f.set(editor, "bravo");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            editor.setCaretPosition(editor.getDocument().getLength()); // 末尾
            editor.findNext();
        });
        // 先頭の "bravo" にラップして戻る
        assertEquals(6, editor.getSelectionStart());
    }

    /** Find: 大文字小文字を区別しない */
    @Test
    public void testFindIgnoresCase() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            editor.setText("alpha BRAVO charlie");
            try {
                java.lang.reflect.Field f = SPDEditor.class.getDeclaredField("lastSearchText");
                f.setAccessible(true);
                f.set(editor, "bravo");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            editor.setCaretPosition(0);
            editor.findNext();
        });
        assertEquals(6, editor.getSelectionStart());
        assertEquals("BRAVO", editor.getSelectedText());
    }

    /** Ctrl/Cmd+Z, Ctrl/Cmd+Y, Cmd+Shift+Z が ActionMap に紐づいている */
    @Test
    public void testUndoRedoKeysAreBound() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            // undo/redo アクションが登録されている
            assertNotNull(editor.getActionMap().get("undo"));
            assertNotNull(editor.getActionMap().get("redo"));
            // smartTab/smartOutdent アクションが登録されている
            assertNotNull(editor.getActionMap().get("smartTab"));
            assertNotNull(editor.getActionMap().get("smartOutdent"));
            // manualAutocomplete アクションが登録されている
            assertNotNull(editor.getActionMap().get("manualAutocomplete"));
        });
    }

    /** 検索ショートカット用の InputMap が KeyStroke でちゃんと引ける */
    @Test
    public void testShortcutInputMapHasBindings() throws Exception {
        SPDEditor editor = newEditor();
        SwingUtilities.invokeAndWait(() -> {
            int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

            // Tab → smartTab
            Object tabBinding = editor.getInputMap().get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
            assertEquals("smartTab", tabBinding);

            // Shift+Tab → smartOutdent
            Object shiftTab = editor.getInputMap().get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
            assertEquals("smartOutdent", shiftTab);

            // Cmd/Ctrl+Z → undo
            Object undoBinding = editor.getInputMap().get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
            assertEquals("undo", undoBinding);

            // Cmd/Ctrl+Space → manualAutocomplete
            Object autoBinding = editor.getInputMap().get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menuMask));
            assertEquals("manualAutocomplete", autoBinding);
        });
    }
}
