package padtools.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * 全クラスの「純粋なメンバー一覧」（フィールド・メソッド）をリードオンリー表示するパネル。
 *
 * <p>{@link padtools.core.formats.uml.ClassMemberReport#render} のテキストをそのまま貼り付ける。
 * 等幅フォント / 行ラップなしで表示し、選択コピー可能。クラスは単純名で見出し表示する。</p>
 */
public class MemberListPanel extends JPanel {

    private final JTextArea textArea;

    public MemberListPanel() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    public void setText(String text) {
        textArea.setText(text == null ? "" : text);
        textArea.setCaretPosition(0);
    }

    public String getText() {
        return textArea.getText();
    }
}
