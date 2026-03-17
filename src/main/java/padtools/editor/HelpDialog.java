package padtools.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;

import padtools.util.Messages;

/**
 * SPD構文リファレンスを表示するヘルプダイアログ。
 */
public class HelpDialog extends JDialog {

    public HelpDialog(JFrame parent) {
        super(parent, Messages.get("dialog.syntaxRef.title"), false);

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(Messages.get("help.spd.content"));
        textPane.setEditable(false);
        textPane.setCaretPosition(0);
        textPane.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(textPane);
        add(scrollPane, BorderLayout.CENTER);

        setSize(new Dimension(600, 500));
        setLocationRelativeTo(parent);
    }
}
