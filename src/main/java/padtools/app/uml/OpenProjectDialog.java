package padtools.app.uml;

import padtools.ProjectRecord;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

/**
 * 「Open Project」ボタン押下時に表示するダイアログ。
 *
 * <p>保存済みプロジェクト一覧から選択するか、
 * ファイルチューザで新規パスを指定するかを選べる。</p>
 */
final class OpenProjectDialog extends JDialog {

    /** ダイアログが返す結果種別。 */
    enum Action { SELECTED, BROWSE, CANCEL }

    private Action action = Action.CANCEL;
    private File chosenRoot;

    private OpenProjectDialog(Frame owner, List<ProjectRecord> records) {
        super(owner, "Open Project", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // ── ヘッダ ──────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(60, 63, 65));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("Open Project");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.NORTH);
        JLabel sub = new JLabel("保存済みプロジェクトを選択するか、新しいフォルダを開いてください");
        sub.setFont(sub.getFont().deriveFont(12f));
        sub.setForeground(new Color(200, 200, 200));
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // ── 中央: プロジェクト一覧 ──────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 6, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;

        if (records.isEmpty()) {
            center.add(new JLabel("最近開いたプロジェクトはありません"), gbc);
        } else {
            center.add(new JLabel("最近開いたプロジェクト:"), gbc);

            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 1;

            JList<ProjectRecord> list = new JList<>(records.toArray(new ProjectRecord[0]));
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new ProjectListRenderer());
            list.setVisibleRowCount(8);

            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(520, 220));
            center.add(scroll, gbc);

            // ── ボタン行 ──────────────────────────────────
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 0;
            gbc.insets = new Insets(10, 0, 0, 0);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

            JButton openSelected = new JButton("開く");
            openSelected.setEnabled(false);
            openSelected.addActionListener(e -> {
                ProjectRecord sel = list.getSelectedValue();
                if (sel != null && sel.root().isDirectory()) {
                    action = Action.SELECTED;
                    chosenRoot = sel.root();
                    dispose();
                }
            });

            JButton browse = new JButton("新しいフォルダを参照...");
            browse.addActionListener(e -> {
                action = Action.BROWSE;
                dispose();
            });

            JButton cancel = new JButton("キャンセル");
            cancel.addActionListener(e -> dispose());

            list.addListSelectionListener(ev -> {
                ProjectRecord sel = list.getSelectedValue();
                openSelected.setEnabled(sel != null && sel.root().isDirectory());
            });
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openSelected.doClick();
                    }
                }
            });

            buttons.add(openSelected);
            buttons.add(browse);
            buttons.add(cancel);
            center.add(buttons, gbc);
        }

        if (records.isEmpty()) {
            // 一覧なし → Browse / Cancel のみ
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(10, 0, 0, 0);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

            JButton browse = new JButton("フォルダを参照...");
            browse.addActionListener(e -> {
                action = Action.BROWSE;
                dispose();
            });
            JButton cancel = new JButton("キャンセル");
            cancel.addActionListener(e -> dispose());
            buttons.add(browse);
            buttons.add(cancel);
            center.add(buttons, gbc);
        }

        add(center, BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(540, 320));
        setLocationRelativeTo(owner);
    }

    /**
     * ダイアログを表示し、ユーザーが選んだプロジェクトルートを返す。
     *
     * <ul>
     *   <li>保存済みプロジェクトを選んだ → そのパスを返す</li>
     *   <li>「新しいフォルダを参照」を選んだ → {@link JFileChooser} を開き選択パスを返す</li>
     *   <li>キャンセル → {@code null}</li>
     * </ul>
     */
    static File show(Frame owner, List<ProjectRecord> records) {
        OpenProjectDialog d = new OpenProjectDialog(owner, records);
        d.setVisible(true);

        if (d.action == Action.SELECTED) {
            return d.chosenRoot;
        }
        if (d.action == Action.BROWSE) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Open Android / Gradle project");
            int r = fc.showOpenDialog(owner);
            return r == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
        }
        return null;
    }

    // ---- セルレンダラ ----

    private static final class ProjectListRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ProjectRecord) {
                ProjectRecord rec = (ProjectRecord) value;
                boolean exists = rec.root().isDirectory();
                setText("<html><b>" + escapeHtml(rec.getName()) + "</b>"
                        + "&nbsp;&nbsp;<font color='" + (isSelected ? "#cccccc" : "#888888") + "'>"
                        + escapeHtml(rec.getPath()) + "</font></html>");
                if (!exists) {
                    setForeground(isSelected ? Color.LIGHT_GRAY : Color.GRAY);
                }
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
            return this;
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
