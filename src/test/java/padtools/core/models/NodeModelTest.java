package padtools.core.models;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ノードモデルクラスのユニットテスト。
 */
public class NodeModelTest {

    // --- ProcessNode ---

    @Test
    public void testProcessNodeValidation() throws ModelValidationException {
        ProcessNode node = new ProcessNode();
        node.setText("処理");
        node.validation(); // should not throw
    }

    @Test(expected = NullFieldException.class)
    public void testProcessNodeValidationFails() throws ModelValidationException {
        ProcessNode node = new ProcessNode();
        node.validation();
    }

    @Test
    public void testProcessNodeToFromMap() {
        ProcessNode node = new ProcessNode();
        node.setText("テスト");

        Map<String, String> map = new HashMap<>();
        node.toMap(map);
        assertEquals("テスト", map.get("text"));

        ProcessNode restored = new ProcessNode();
        restored.fromMap(map);
        assertEquals("テスト", restored.getText());
    }

    // --- TerminalNode ---

    @Test
    public void testTerminalNodeValidation() throws ModelValidationException {
        TerminalNode node = new TerminalNode();
        node.setText("START");
        node.validation();
    }

    @Test(expected = NullFieldException.class)
    public void testTerminalNodeValidationFails() throws ModelValidationException {
        new TerminalNode().validation();
    }

    // --- CallNode ---

    @Test
    public void testCallNodeToFromMap() {
        CallNode node = new CallNode();
        node.setText("サブルーチン");

        Map<String, String> map = new HashMap<>();
        node.toMap(map);
        assertEquals("サブルーチン", map.get("text"));

        CallNode restored = new CallNode();
        restored.fromMap(map);
        assertEquals("サブルーチン", restored.getText());
    }

    // --- IfNode ---

    @Test
    public void testIfNodeValidation() throws ModelValidationException {
        IfNode node = new IfNode();
        node.setText("条件");
        node.validation();
    }

    @Test(expected = NullFieldException.class)
    public void testIfNodeValidationFails() throws ModelValidationException {
        new IfNode().validation();
    }

    @Test
    public void testIfNodeChildren() {
        IfNode node = new IfNode();
        node.setText("条件");

        ProcessNode trueNode = new ProcessNode();
        trueNode.setText("true処理");
        node.setTrueNode(trueNode);

        ProcessNode falseNode = new ProcessNode();
        falseNode.setText("false処理");
        node.setFalseNode(falseNode);

        assertEquals("true処理", ((ProcessNode) node.getTrueNode()).getText());
        assertEquals("false処理", ((ProcessNode) node.getFalseNode()).getText());
    }

    // --- LoopNode ---

    @Test
    public void testLoopNodeWhile() {
        LoopNode node = new LoopNode();
        node.setText("条件");
        node.setWhile(true);
        assertTrue(node.isWhile());
    }

    @Test
    public void testLoopNodeDoWhile() {
        LoopNode node = new LoopNode();
        node.setText("条件");
        node.setWhile(false);
        assertFalse(node.isWhile());
    }

    // --- SwitchNode ---

    @Test
    public void testSwitchNodeCases() {
        SwitchNode node = new SwitchNode();
        node.setText("変数");

        ProcessNode caseA = new ProcessNode();
        caseA.setText("A処理");
        node.getCases().put("ケースA", caseA);

        ProcessNode caseB = new ProcessNode();
        caseB.setText("B処理");
        node.getCases().put("ケースB", caseB);

        assertEquals(2, node.getCases().size());
        assertTrue(node.getCases().containsKey("ケースA"));
    }

    // --- NodeListNode ---

    @Test
    public void testNodeListNode() {
        NodeListNode list = new NodeListNode();

        ProcessNode a = new ProcessNode();
        a.setText("A");
        list.getChildren().add(a);

        ProcessNode b = new ProcessNode();
        b.setText("B");
        list.getChildren().add(b);

        assertEquals(2, list.getChildren().size());
    }

    // --- PADModel ---

    @Test
    public void testPADModel() {
        PADModel model = new PADModel();
        assertNull(model.getTopNode());

        ProcessNode node = new ProcessNode();
        node.setText("テスト");
        model.setTopNode(node);

        assertNotNull(model.getTopNode());
        assertEquals("テスト", ((ProcessNode) model.getTopNode()).getText());
    }

    // --- NodeCatalog ---

    @Test
    public void testNodeCatalogLookup() {
        assertNotNull(NodeCatalog.getByTypeName("process"));
        assertNotNull(NodeCatalog.getByClass(ProcessNode.class));
    }
}
