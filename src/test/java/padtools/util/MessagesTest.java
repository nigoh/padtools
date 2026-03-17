package padtools.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Messages i18nユーティリティのテスト。
 */
public class MessagesTest {

    @Test
    public void testGetExistingKey() {
        String result = Messages.get("menu.file");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertNotEquals("menu.file", result); // 実際の値を返すべき
    }

    @Test
    public void testGetMissingKeyReturnsFallback() {
        String result = Messages.get("this.key.does.not.exist.xyz");
        assertEquals("this.key.does.not.exist.xyz", result);
    }

    @Test
    public void testMenuKeys() {
        // 基本メニューキーの存在確認
        assertNotEquals("menu.file.new", Messages.get("menu.file.new"));
        assertNotEquals("menu.file.open", Messages.get("menu.file.open"));
        assertNotEquals("menu.file.save", Messages.get("menu.file.save"));
        assertNotEquals("menu.output.png", Messages.get("menu.output.png"));
        assertNotEquals("menu.output.svg", Messages.get("menu.output.svg"));
        assertNotEquals("menu.help.about", Messages.get("menu.help.about"));
    }

    @Test
    public void testNewFeatureKeys() {
        // Phase 6-8 で追加したキー
        assertNotEquals("menu.help.syntaxRef", Messages.get("menu.help.syntaxRef"));
        assertNotEquals("menu.file.newFromTemplate", Messages.get("menu.file.newFromTemplate"));
        assertNotEquals("menu.file.print", Messages.get("menu.file.print"));
        assertNotEquals("menu.edit.copyDiagram", Messages.get("menu.edit.copyDiagram"));
        assertNotEquals("menu.output.pdf", Messages.get("menu.output.pdf"));
        assertNotEquals("menu.view.zoomIn", Messages.get("menu.view.zoomIn"));
        assertNotEquals("menu.view.zoomOut", Messages.get("menu.view.zoomOut"));
        assertNotEquals("menu.view.zoomFit", Messages.get("menu.view.zoomFit"));
        assertNotEquals("menu.view.zoomReset", Messages.get("menu.view.zoomReset"));
    }

    @Test
    public void testTemplateKeys() {
        assertNotEquals("template.basic.name", Messages.get("template.basic.name"));
        assertNotEquals("template.ifelse.name", Messages.get("template.ifelse.name"));
        assertNotEquals("template.loop.name", Messages.get("template.loop.name"));
        assertNotEquals("template.switch.name", Messages.get("template.switch.name"));
    }

    @Test
    public void testTemplateContent() {
        // テンプレート内容にSPDコマンドが含まれることを確認
        String basic = Messages.get("template.basic");
        assertTrue(basic.contains(":terminal"));
        assertTrue(basic.contains(":if"));

        String loop = Messages.get("template.loop");
        assertTrue(loop.contains(":while"));
        assertTrue(loop.contains(":dowhile"));

        String sw = Messages.get("template.switch");
        assertTrue(sw.contains(":switch"));
        assertTrue(sw.contains(":case"));
    }

    @Test
    public void testHelpContent() {
        String help = Messages.get("help.spd.content");
        assertNotNull(help);
        assertTrue(help.contains("<html>"));
        assertTrue(help.contains(":if"));
        assertTrue(help.contains(":while"));
        assertTrue(help.contains(":switch"));
    }

    @Test
    public void testStatusKeys() {
        assertNotEquals("status.line", Messages.get("status.line"));
        assertNotEquals("status.column", Messages.get("status.column"));
        assertNotEquals("status.parseOk", Messages.get("status.parseOk"));
        assertNotEquals("status.zoom", Messages.get("status.zoom"));
    }
}
