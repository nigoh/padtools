package padtools.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;

import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.PADModel;
import padtools.core.view.BufferedView;
import padtools.core.view.Model2View;

/**
 * PAD図のプレビューを表示するパネル。
 */
public class PreviewPanel extends JPanel {
    private BufferedView view = null;
    private final Model2View model2View;

    public PreviewPanel(Model2View model2View) {
        this.model2View = model2View;
    }

    @Override
    protected void paintComponent(Graphics grphcs) {
        Graphics2D g = (Graphics2D) grphcs;
        Dimension s = getSize();
        g.setPaint(new GradientPaint(new Point(), Color.white, new Point(s.width, s.height), Color.lightGray));
        g.fillRect(0, 0, s.width, s.height);
        if (view != null) {
            view.draw(g, new Point2D.Double());
        }
    }

    /**
     * SPDテキストを解析し、プレビューを更新する。
     */
    public void refresh(String spdText, SPDEditor editor, JList<String> messageList, Graphics2D graphics) {
        ((DefaultListModel<String>) messageList.getModel()).removeAllElements();
        editor.refreshHighlight();

        final PADModel model = SPDParser.parse(spdText, new ParseErrorReceiver() {
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                ((DefaultListModel<String>) messageList.getModel()).addElement(String.format("line %d, %s", lineNo + 1, err.getUserMessage()));
                editor.setErrorLine(lineNo);
                return true;
            }
        });

        view = new BufferedView(model2View.toView(model), true);
        editor.setEdited(false);

        Point2D.Double s = view.getSize(graphics);
        Dimension d = new Dimension((int) s.x, (int) s.y);
        setSize(d);
        setPreferredSize(d);
        updateUI();
    }

    public BufferedView getBufferedView() {
        return view;
    }

    public Rectangle getViewBounds() {
        return getBounds();
    }
}
