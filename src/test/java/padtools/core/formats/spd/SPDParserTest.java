package padtools.core.formats.spd;

import org.junit.Test;
import padtools.core.models.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * SPDParser のユニットテスト。
 */
public class SPDParserTest {

    private PADModel parse(String src) {
        return SPDParser.parse(src, null);
    }

    private PADModel parseWithErrors(String src, List<String> errors) {
        return SPDParser.parse(src, new ParseErrorReceiver() {
            @Override
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                errors.add("line " + (lineNo + 1) + ": " + err.getUserMessage());
                return true;
            }
        });
    }

    // --- 基本テスト ---

    @Test
    public void testEmptyInput() {
        PADModel model = parse("");
        assertNotNull(model);
        assertNull(model.getTopNode());
    }

    @Test
    public void testSingleProcess() {
        PADModel model = parse("処理A");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof ProcessNode);
        assertEquals("処理A", ((ProcessNode) model.getTopNode()).getText());
    }

    @Test
    public void testMultipleProcesses() {
        PADModel model = parse("処理A\n処理B\n処理C");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof NodeListNode);
        NodeListNode list = (NodeListNode) model.getTopNode();
        assertEquals(3, list.getChildren().size());
    }

    @Test
    public void testCommentLine() {
        PADModel model = parse("#コメント\n処理A");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof ProcessNode);
        assertEquals("処理A", ((ProcessNode) model.getTopNode()).getText());
    }

    @Test
    public void testBlankLines() {
        PADModel model = parse("\n\n処理A\n\n");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof ProcessNode);
    }

    // --- コマンドテスト ---

    @Test
    public void testTerminalCommand() {
        PADModel model = parse(":terminal START");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof TerminalNode);
        assertEquals("START", ((TerminalNode) model.getTopNode()).getText());
    }

    @Test
    public void testCallCommand() {
        PADModel model = parse(":call サブルーチン");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof CallNode);
        assertEquals("サブルーチン", ((CallNode) model.getTopNode()).getText());
    }

    @Test
    public void testCommentCommand() {
        PADModel model = parse(":comment これはコメント");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof CommentNode);
        assertEquals("これはコメント", ((CommentNode) model.getTopNode()).getText());
    }

    @Test
    public void testWhileLoop() {
        PADModel model = parse(":while 条件\n\t処理A");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof LoopNode);
        LoopNode loop = (LoopNode) model.getTopNode();
        assertEquals("条件", loop.getText());
        assertTrue(loop.isWhile());
        assertNotNull(loop.getChildNode());
    }

    @Test
    public void testDoWhileLoop() {
        PADModel model = parse(":dowhile 条件\n\t処理A");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof LoopNode);
        LoopNode loop = (LoopNode) model.getTopNode();
        assertFalse(loop.isWhile());
    }

    @Test
    public void testIfElse() {
        PADModel model = parse(":if 条件\n\t処理A\n:else\n\t処理B");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof IfNode);
        IfNode ifNode = (IfNode) model.getTopNode();
        assertEquals("条件", ifNode.getText());
        assertNotNull(ifNode.getTrueNode());
        assertNotNull(ifNode.getFalseNode());
    }

    @Test
    public void testIfWithoutElse() {
        PADModel model = parse(":if 条件\n\t処理A");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof IfNode);
        IfNode ifNode = (IfNode) model.getTopNode();
        assertNotNull(ifNode.getTrueNode());
        assertNull(ifNode.getFalseNode());
    }

    @Test
    public void testSwitch() {
        PADModel model = parse(":switch 変数\n:case ケースA\n\t処理A\n:case ケースB\n\t処理B");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof SwitchNode);
        SwitchNode sw = (SwitchNode) model.getTopNode();
        assertEquals("変数", sw.getText());
        assertEquals(2, sw.getCases().size());
        assertTrue(sw.getCases().containsKey("ケースA"));
        assertTrue(sw.getCases().containsKey("ケースB"));
    }

    // --- ネストテスト ---

    @Test
    public void testNestedIfInWhile() {
        String src = ":while 条件1\n\t:if 条件2\n\t\t処理A\n\t:else\n\t\t処理B";
        PADModel model = parse(src);
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof LoopNode);
        LoopNode loop = (LoopNode) model.getTopNode();
        assertTrue(loop.getChildNode() instanceof IfNode);
    }

    // --- マルチライン ---

    @Test
    public void testMultiLineWithAt() {
        PADModel model = parse("処理A@\n続き");
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof ProcessNode);
        String text = ((ProcessNode) model.getTopNode()).getText();
        assertTrue(text.contains("処理A"));
        assertTrue(text.contains("続き"));
    }

    // --- エラーケース ---

    @Test
    public void testUnknownCommand() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors(":unknown コマンド", errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testRequireArgument() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors(":call", errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testElseWithoutIf() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors(":else", errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testCaseWithoutSwitch() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors(":case ケース", errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testDuplicateCase() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors(":switch 変数\n:case A\n\t処理\n:case A\n\t処理", errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testIllegalIndent() {
        List<String> errors = new ArrayList<>();
        PADModel model = parseWithErrors("\t処理A", errors);
        assertFalse(errors.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        SPDParser.parse(null, null);
    }

    // --- 完全なプログラム ---

    @Test
    public void testFullProgram() {
        String src = ":terminal START\n" +
                ":while i < 10\n" +
                "\t:if i % 2 == 0\n" +
                "\t\t偶数処理\n" +
                "\t:else\n" +
                "\t\t奇数処理\n" +
                ":terminal END";
        PADModel model = parse(src);
        assertNotNull(model);
        assertTrue(model.getTopNode() instanceof NodeListNode);
        NodeListNode list = (NodeListNode) model.getTopNode();
        assertEquals(3, list.getChildren().size());
        assertTrue(list.getChildren().get(0) instanceof TerminalNode);
        assertTrue(list.getChildren().get(1) instanceof LoopNode);
        assertTrue(list.getChildren().get(2) instanceof TerminalNode);
    }
}
