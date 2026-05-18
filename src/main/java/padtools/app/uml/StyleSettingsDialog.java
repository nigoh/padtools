package padtools.app.uml;

import padtools.core.formats.uml.DiagramStyle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * UML 描画スタイル詳細設定ダイアログ。
 *
 * <p>{@link DiagramStyle} の各フィールドを編集する Swing モーダルダイアログ。
 * {@link #showDialog(Component, DiagramStyle)} を呼ぶと OK 押下時は編集後の
 * {@code DiagramStyle} を、キャンセル時は {@code null} を返す。</p>
 */
public final class StyleSettingsDialog extends JDialog {

    /** ダイアログ・メニュー双方で共有する組み込みテーマ一覧 (先頭 "" は未指定)。 */
    public static final String[] THEMES = new String[] {
            "", "plain", "cerulean", "sketchy", "mono", "vibrant",
            "materia", "hacker", "cyborg", "mars", "amiga", "spacelab"
    };

    private final JComboBox<String> themeCombo = new JComboBox<>(THEMES);
    private final JButton bgColorButton = new JButton();
    private final JTextField bgColorField = new JTextField(10);
    private final JTextField fontField = new JTextField(12);
    private final JSpinner fontSizeSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 48, 1));
    private final JRadioButton dirDefault = new JRadioButton("Default (top-to-bottom)");
    private final JRadioButton dirLeftRight = new JRadioButton("Left to right");
    private final JRadioButton dirTopBottom = new JRadioButton("Top to bottom (explicit)");
    private final JTextArea customSkinparamArea = new JTextArea(6, 32);

    private DiagramStyle result;

    private StyleSettingsDialog(Window owner, DiagramStyle initial) {
        super(owner, "UML Style Settings", Dialog.ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        add(buildForm(initial), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm(DiagramStyle initial) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // テーマ
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Theme:"), c);
        themeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String s = (value == null || value.toString().isEmpty())
                        ? "(None)" : value.toString();
                return super.getListCellRendererComponent(list, s, index, isSelected, cellHasFocus);
            }
        });
        themeCombo.setSelectedItem(initial.getTheme());
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(themeCombo, c);
        c.gridwidth = 1;
        row++;

        // 背景色
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Background:"), c);
        bgColorField.setText(initial.getBackgroundColor());
        bgColorField.setToolTipText("PlantUML color value, e.g. #FFFFFF, white, transparent");
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        form.add(bgColorField, c);
        bgColorButton.setText("Pick...");
        bgColorButton.addActionListener(e -> pickBackgroundColor());
        c.gridx = 2; c.gridy = row; c.weightx = 0;
        form.add(bgColorButton, c);
        row++;

        // フォント名
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Font name:"), c);
        fontField.setText(initial.getFontName());
        fontField.setToolTipText("e.g. Helvetica, Noto Sans CJK JP. Empty = PlantUML default");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(fontField, c);
        c.gridwidth = 1;
        row++;

        // フォントサイズ
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Font size:"), c);
        fontSizeSpinner.setValue(initial.getFontSize());
        ((JSpinner.DefaultEditor) fontSizeSpinner.getEditor()).getTextField()
                .setToolTipText("0 = use PlantUML default");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(fontSizeSpinner, c);
        c.gridwidth = 1;
        row++;

        // 方向
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Direction:"), c);
        ButtonGroup dirGroup = new ButtonGroup();
        dirGroup.add(dirDefault);
        dirGroup.add(dirLeftRight);
        dirGroup.add(dirTopBottom);
        switch (initial.getDirection()) {
            case LEFT_TO_RIGHT: dirLeftRight.setSelected(true); break;
            case TOP_TO_BOTTOM: dirTopBottom.setSelected(true); break;
            default: dirDefault.setSelected(true); break;
        }
        JPanel dirPanel = new JPanel();
        dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.Y_AXIS));
        dirPanel.add(dirDefault);
        dirPanel.add(dirLeftRight);
        dirPanel.add(dirTopBottom);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(dirPanel, c);
        c.gridwidth = 1;
        row++;

        // カスタム skinparam
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Custom skinparam:"), c);
        customSkinparamArea.setText(initial.getCustomSkinparam());
        customSkinparamArea.setLineWrap(false);
        customSkinparamArea.setToolTipText(
                "Raw PlantUML lines, e.g.\n"
                + "skinparam shadowing false\n"
                + "skinparam classBackgroundColor #EEEEEE");
        JScrollPane skinScroll = new JScrollPane(customSkinparamArea);
        skinScroll.setPreferredSize(new Dimension(400, 120));
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.weighty = 1;
        c.gridwidth = 2; c.fill = GridBagConstraints.BOTH;
        form.add(skinScroll, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weighty = 0;

        return form;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("Reset to Defaults");
        reset.addActionListener(e -> resetToDefaults());
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            result = collect();
            setVisible(false);
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        buttons.add(reset);
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(cancel);
        buttons.add(ok);
        return buttons;
    }

    private void resetToDefaults() {
        DiagramStyle d = DiagramStyle.defaults();
        themeCombo.setSelectedItem(d.getTheme());
        bgColorField.setText(d.getBackgroundColor());
        fontField.setText(d.getFontName());
        fontSizeSpinner.setValue(d.getFontSize());
        dirDefault.setSelected(true);
        customSkinparamArea.setText(d.getCustomSkinparam());
    }

    private void pickBackgroundColor() {
        Color initial = parseColor(bgColorField.getText());
        Color chosen = JColorChooser.showDialog(this, "Background Color",
                initial != null ? initial : Color.WHITE);
        if (chosen != null) {
            bgColorField.setText(String.format("#%02X%02X%02X",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
        }
    }

    private static Color parseColor(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String s = value.trim();
        if (!s.startsWith("#")) {
            return null;
        }
        try {
            return Color.decode(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DiagramStyle collect() {
        DiagramStyle s = new DiagramStyle();
        Object themeSel = themeCombo.getSelectedItem();
        s.setTheme(themeSel != null ? themeSel.toString() : "");
        s.setBackgroundColor(bgColorField.getText().trim());
        s.setFontName(fontField.getText().trim());
        s.setFontSize((Integer) fontSizeSpinner.getValue());
        if (dirLeftRight.isSelected()) {
            s.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        } else if (dirTopBottom.isSelected()) {
            s.setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        } else {
            s.setDirection(DiagramStyle.Direction.DEFAULT);
        }
        s.setCustomSkinparam(customSkinparamArea.getText());
        return s;
    }

    /**
     * モーダルダイアログを開き、編集された {@link DiagramStyle} を返す。
     * キャンセル時は null を返す。
     */
    public static DiagramStyle showDialog(Component parent, DiagramStyle current) {
        Window owner = (parent instanceof Window)
                ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent);
        DiagramStyle initial = current != null ? current.copy() : DiagramStyle.defaults();
        StyleSettingsDialog dlg = new StyleSettingsDialog(owner, initial);
        dlg.setVisible(true);
        return dlg.result;
    }
}
