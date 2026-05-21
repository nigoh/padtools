package padtools.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * 全クラスの関数使用マップ（署名・利用側・実行条件・リスナー）をリードオンリー表示するパネル。
 *
 * <p>{@link padtools.core.formats.uml.MethodUsageReport#render} の Markdown 結果をそのまま貼り付ける。
 * 等幅フォント / 行ラップなしで表示し、選択コピー可能（CLI の {@code --function-list} の GUI 版）。</p>
 */
public class MethodListPanel extends JPanel {

    private final JTextArea textArea;

    public MethodListPanel() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    public void setText(String markdown) {
        textArea.setText(markdown == null ? "" : markdown);
        textArea.setCaretPosition(0);
    }

    public String getText() {
        return textArea.getText();
    }
}
