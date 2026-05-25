// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * 生成された PlantUML テキストをリードオンリーで表示するパネル。
 *
 * <p>デバッグ目的、もしくはユーザーが PlantUML テキストを別ツールに
 * コピーしたい場合の参照用。等幅フォントで表示する。</p>
 */
public class PumlSourcePanel extends JPanel {

    private final JTextArea textArea;

    public PumlSourcePanel() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    public void setText(String puml) {
        textArea.setText(puml == null ? "" : puml);
        textArea.setCaretPosition(0);
    }

    public String getText() {
        return textArea.getText();
    }
}
