package padtools.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * 全クラスの「純粋なメンバー一覧」（フィールド・メソッド・enum 定数）を CSV でリードオンリー表示するパネル。
 *
 * <p>{@link padtools.core.formats.uml.ClassMemberReport#render} の CSV をそのまま貼り付ける。
 * 等幅フォント / 行ラップなしで表示し、選択コピーして表計算ソフトに取り込める。
 * クラスは単純名カラム、パッケージは別カラムに分離する。</p>
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
