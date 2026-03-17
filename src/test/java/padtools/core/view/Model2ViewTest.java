package padtools.core.view;

import org.junit.Test;
import padtools.core.models.*;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

/**
 * Model2View のユニットテスト。
 */
public class Model2ViewTest {

    private Graphics2D createTestGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR).createGraphics();
    }

    @Test
    public void testNullTopNode() {
        PADModel model = new PADModel();
        model.setTopNode(null);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
        // 空モデルは <EMPTY> テキストを表示
        assertTrue(view instanceof TextView);
    }

    @Test
    public void testProcessNode() {
        PADModel model = new PADModel();
        ProcessNode node = new ProcessNode();
        node.setText("テスト処理");
        model.setTopNode(node);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
        Point2D.Double size = view.getSize(createTestGraphics());
        assertTrue(size.x > 0);
        assertTrue(size.y > 0);
    }

    @Test
    public void testTerminalNode() {
        PADModel model = new PADModel();
        TerminalNode node = new TerminalNode();
        node.setText("START");
        model.setTopNode(node);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }

    @Test
    public void testIfNode() {
        PADModel model = new PADModel();
        IfNode ifNode = new IfNode();
        ifNode.setText("x > 0");
        ProcessNode trueNode = new ProcessNode();
        trueNode.setText("positive");
        ifNode.setTrueNode(trueNode);
        ProcessNode falseNode = new ProcessNode();
        falseNode.setText("negative");
        ifNode.setFalseNode(falseNode);
        model.setTopNode(ifNode);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
        Point2D.Double size = view.getSize(createTestGraphics());
        assertTrue(size.x > 0);
        assertTrue(size.y > 0);
    }

    @Test
    public void testLoopNode() {
        PADModel model = new PADModel();
        LoopNode loop = new LoopNode();
        loop.setText("i < 10");
        loop.setWhile(true);
        ProcessNode child = new ProcessNode();
        child.setText("i++");
        loop.setChildNode(child);
        model.setTopNode(loop);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }

    @Test
    public void testSwitchNode() {
        PADModel model = new PADModel();
        SwitchNode sw = new SwitchNode();
        sw.setText("value");
        ProcessNode a = new ProcessNode(); a.setText("A");
        ProcessNode b = new ProcessNode(); b.setText("B");
        sw.getCases().put("caseA", a);
        sw.getCases().put("caseB", b);
        model.setTopNode(sw);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }

    @Test
    public void testNodeListNode() {
        PADModel model = new PADModel();
        NodeListNode list = new NodeListNode();
        ProcessNode p1 = new ProcessNode(); p1.setText("A");
        ProcessNode p2 = new ProcessNode(); p2.setText("B");
        list.getChildren().add(p1);
        list.getChildren().add(p2);
        model.setTopNode(list);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }

    @Test
    public void testCallNode() {
        PADModel model = new PADModel();
        CallNode call = new CallNode();
        call.setText("subroutine()");
        model.setTopNode(call);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }

    @Test
    public void testCommentNode() {
        PADModel model = new PADModel();
        CommentNode comment = new CommentNode();
        comment.setText("This is a note");
        model.setTopNode(comment);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        assertNotNull(view);
    }
}
