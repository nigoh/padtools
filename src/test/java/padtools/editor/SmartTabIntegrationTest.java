package padtools.editor;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;

import padtools.SettingManager;

import static org.junit.Assert.assertEquals;

/**
 * インデント機能を実際の SPDEditor 上で動かす結合テスト。
 * MainFrame と同じ初期テキスト(複数行/末尾改行なし) に対して
 * Ctrl+A + Tab を行ったときに全行が一回だけインデントされるか検証する。
 */
public class SmartTabIntegrationTest {

    @BeforeClass
    public static void initSettings() {
        SettingManager.initialize();
    }

    @Before
    public void skipIfHeadless() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
    }

    private static String DEFAULT_TEXT
            = ":terminal START\n\n#Describe your logic here\nLogic\n\n:terminal END";

    @Test
    public void testDefaultTextBlockIndentEachLineOnce() throws Exception {
        SPDEditor[] holder = new SPDEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new SPDEditor();
            holder[0].setText(DEFAULT_TEXT);
            holder[0].selectAll();
            holder[0].getActionMap().get("smartTab").actionPerformed(
                    new ActionEvent(holder[0], ActionEvent.ACTION_PERFORMED, "smartTab"));
        });
        String expected
                = "\t:terminal START\n\t\n\t#Describe your logic here\n\tLogic\n\t\n\t:terminal END";
        assertEquals(expected, holder[0].getText());
    }

    @Test
    public void testDefaultTextBlockOutdentReverses() throws Exception {
        SPDEditor[] holder = new SPDEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new SPDEditor();
            // 各行頭に tab を入れた状態からスタート
            holder[0].setText(
                    "\t:terminal START\n\t\n\t#Describe your logic here\n\tLogic\n\t\n\t:terminal END");
            holder[0].selectAll();
            holder[0].getActionMap().get("smartOutdent").actionPerformed(
                    new ActionEvent(holder[0], ActionEvent.ACTION_PERFORMED, "smartOutdent"));
        });
        assertEquals(DEFAULT_TEXT, holder[0].getText());
    }
}
