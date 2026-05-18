package padtools.app.uml;

import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.PlantUmlSequenceDiagram;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * シーケンス図の起点となる {@code Class.method} を一覧から選択するモーダルダイアログ。
 *
 * <p>{@link PlantUmlSequenceDiagram#listCandidates(java.util.List)} で取得した
 * 候補メソッドをリスト表示し、テキストフィールドで前方一致絞り込みできる。
 * OK 押下で選択結果を {@link #getSelectedEntry()} から取り出せる
 * ({@code "Class.method"} 形式)。キャンセル時は null。</p>
 */
public class SequenceEntryDialog extends JDialog {

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JTextField filter = new JTextField();
    private final List<String> allEntries = new ArrayList<>();
    private String selectedEntry;

    public SequenceEntryDialog(Frame owner, List<JavaClassInfo> classes) {
        super(owner, "Select sequence diagram entry", true);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (PlantUmlSequenceDiagram.Candidate c
                : PlantUmlSequenceDiagram.listCandidates(classes)) {
            allEntries.add(c.getEntry());
        }
        rebuildList("");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(20);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(new JLabel("Filter (Class.method substring):"), BorderLayout.NORTH);
        north.add(filter, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        south.add(ok);
        south.add(cancel);
        add(south, BorderLayout.SOUTH);

        ok.addActionListener(e -> commit());
        cancel.addActionListener(e -> dispose());
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commit();
                }
            }
        });
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }
        });

        setPreferredSize(new Dimension(500, 500));
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
    }

    private void rebuildList(String f) {
        model.clear();
        String q = f == null ? "" : f.toLowerCase();
        int max = 500; // 候補が多すぎるときの保護
        int n = 0;
        for (String e : allEntries) {
            if (q.isEmpty() || e.toLowerCase().contains(q)) {
                model.addElement(e);
                if (++n >= max) {
                    break;
                }
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void commit() {
        selectedEntry = list.getSelectedValue();
        dispose();
    }

    /** モーダル終了後、選択された {@code Class.method} を返す。キャンセル時は null。 */
    public String getSelectedEntry() {
        return selectedEntry;
    }

    public int getCandidateCount() {
        return allEntries.size();
    }
}
