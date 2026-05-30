// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlSequenceDiagram;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
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

    /** フォント未指定（自動検出）を表す内部マーカ。実値は空文字。 */
    private static final String FONT_AUTO = "(Auto / 自動検出)";

    private final JComboBox<String> themeCombo = new JComboBox<>(THEMES);
    private final JButton bgColorButton = new JButton();
    private final JTextField bgColorField = new JTextField(10);
    private final JComboBox<String> fontCombo = new JComboBox<>();
    private final JLabel fontPreview = new JLabel();
    private final JSpinner fontSizeSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 48, 1));
    private final JRadioButton dirDefault = new JRadioButton("Default (top-to-bottom)");
    private final JRadioButton dirLeftRight = new JRadioButton("Left to right");
    private final JRadioButton dirTopBottom = new JRadioButton("Top to bottom (explicit)");
    private final JComboBox<String> lineTypeCombo =
            new JComboBox<>(new String[] { "Default", "Polyline", "Ortho", "Spline" });
    private final JComboBox<String> shadowingCombo =
            new JComboBox<>(new String[] { "Default", "On", "Off" });
    private final JSpinner nodeSepSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 200, 5));
    private final JSpinner rankSepSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 200, 5));
    private final JTextArea customSkinparamArea = new JTextArea(6, 32);
    private final JCheckBox sequenceShowCommentsCheckbox =
            new JCheckBox("Show JavaDoc / source comments as notes");
    private final JComboBox<String> sequenceCommentStyleCombo =
            new JComboBox<>(new String[] { "INLINE", "NOTE" });
    private final JComboBox<String> sequenceCommentPlacementCombo =
            new JComboBox<>(new String[] { "AT_CALL_SITE", "PARTICIPANT_TOP" });
    private final JCheckBox sequenceQualifyMethodsCheckbox =
            new JCheckBox("Qualify call labels with class name (e.g. Foo.bar())");

    private final JCheckBox classShowFieldsCheckbox = new JCheckBox("Show fields");
    private final JCheckBox classShowMethodsCheckbox = new JCheckBox("Show methods");
    private final JCheckBox classShowAnnotationsCheckbox = new JCheckBox("Show annotations");
    private final JCheckBox classPublicOnlyCheckbox =
            new JCheckBox("Public only (hide non-public classes and members)");
    private final JCheckBox classExcludeExternalCheckbox =
            new JCheckBox("Exclude external libraries (java.*, android.*, kotlin.*, ...)");
    private final JSpinner classCommentMaxLengthSpinner =
            new JSpinner(new SpinnerNumberModel(80, 0, 500, 10));
    private final JTextField classHiddenAnnotationsField = new JTextField(24);

    private final JSpinner callGraphMaxDepthSpinner =
            new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));

    private Result result;

    /** クラス図向け Setting 永続化用 DTO (不変)。 */
    public static final class ClassDiagramPrefs {
        public final boolean showFields;
        public final boolean showMethods;
        public final boolean showAnnotations;
        public final boolean publicOnly;
        public final boolean excludeExternal;
        public final int commentMaxLength;
        /** 表示しないアノテーション名集合 (大括弧なし、例: {"Override", "Nullable"})。 */
        public final java.util.Set<String> hiddenAnnotations;

        public ClassDiagramPrefs(boolean showFields, boolean showMethods,
                                  boolean showAnnotations, boolean publicOnly,
                                  boolean excludeExternal, int commentMaxLength,
                                  java.util.Set<String> hiddenAnnotations) {
            this.showFields = showFields;
            this.showMethods = showMethods;
            this.showAnnotations = showAnnotations;
            this.publicOnly = publicOnly;
            this.excludeExternal = excludeExternal;
            this.commentMaxLength = Math.max(0, commentMaxLength);
            this.hiddenAnnotations = (hiddenAnnotations == null)
                    ? java.util.Collections.emptySet()
                    : java.util.Collections.unmodifiableSet(
                            new java.util.LinkedHashSet<>(hiddenAnnotations));
        }

        /** カンマ区切り文字列に整形 (CSV)。 */
        public String hiddenAnnotationsCsv() {
            return String.join(",", hiddenAnnotations);
        }

        /** CSV からインスタンスを組み立てるユーティリティ。 */
        public static java.util.Set<String> parseCsv(String csv) {
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            if (csv == null || csv.isEmpty()) {
                return set;
            }
            for (String tok : csv.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
            return set;
        }

        /** 既定値 (PlantUmlClassDiagram.Options の既定 = BALANCED 相当)。 */
        public static ClassDiagramPrefs defaults() {
            java.util.Set<String> hidden = new java.util.LinkedHashSet<>();
            hidden.add("Override");
            hidden.add("SuppressWarnings");
            return new ClassDiagramPrefs(true, true, true, false, false, 80, hidden);
        }
    }

    /** ダイアログの戻り値 (Style + シーケンス図 + クラス図設定)。 */
    public static final class Result {
        public final DiagramStyle style;
        public final boolean sequenceShowComments;
        public final PlantUmlClassDiagram.CommentStyle sequenceCommentStyle;
        public final PlantUmlSequenceDiagram.CommentPlacement sequenceCommentPlacement;
        public final boolean sequenceQualifyMethodNames;
        public final ClassDiagramPrefs classDiagram;
        public final int callGraphMaxDepth;

        public Result(DiagramStyle style, boolean sequenceShowComments,
                      PlantUmlClassDiagram.CommentStyle sequenceCommentStyle,
                      PlantUmlSequenceDiagram.CommentPlacement sequenceCommentPlacement,
                      boolean sequenceQualifyMethodNames,
                      ClassDiagramPrefs classDiagram,
                      int callGraphMaxDepth) {
            this.style = style;
            this.sequenceShowComments = sequenceShowComments;
            this.sequenceCommentStyle = sequenceCommentStyle;
            this.sequenceCommentPlacement = sequenceCommentPlacement;
            this.sequenceQualifyMethodNames = sequenceQualifyMethodNames;
            this.classDiagram = classDiagram != null
                    ? classDiagram : ClassDiagramPrefs.defaults();
            this.callGraphMaxDepth = callGraphMaxDepth > 0 ? callGraphMaxDepth : 4;
        }
    }

    private StyleSettingsDialog(Window owner, DiagramStyle initial,
                                 boolean initialSeqShowComments,
                                 PlantUmlClassDiagram.CommentStyle initialSeqCommentStyle,
                                 PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement,
                                 boolean initialSeqQualify,
                                 ClassDiagramPrefs initialClassPrefs,
                                 int initialCallGraphMaxDepth) {
        super(owner, "UML Style Settings", Dialog.ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buildForm(initial, initialSeqShowComments,
                initialSeqCommentStyle, initialSeqPlacement, initialSeqQualify,
                initialClassPrefs, initialCallGraphMaxDepth));
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        // フォーム高がスクリーンを超えると pack でクリップされるので明示的に上限を入れる
        java.awt.Dimension pref = getPreferredSize();
        int maxH = (int) (java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds().height * 0.9);
        if (pref.height > maxH) {
            setSize(pref.width + 30, maxH);
        }
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm(DiagramStyle initial,
                              boolean initialSeqShowComments,
                              PlantUmlClassDiagram.CommentStyle initialSeqCommentStyle,
                              PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement,
                              boolean initialSeqQualify,
                              ClassDiagramPrefs initialClassPrefs,
                              int initialCallGraphMaxDepth) {
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

        // フォント名 (システムにインストールされたフォントから選択。任意入力も可)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Font name:"), c);
        initFontCombo(initial.getFontName());
        fontCombo.setToolTipText(
                "Pick an installed font. (Auto / 自動検出) = auto-detected Japanese font. "
                + "日本語対応フォントが先頭にまとまります。");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(fontCombo, c);
        c.gridwidth = 1;
        row++;

        // フォントプレビュー
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        fontPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        updateFontPreview();
        form.add(fontPreview, c);
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

        // ─── Readability (可読性) ──────────────────────────────────────────
        // 図が見づらい場合に効く設定群。線種・影・要素間隔を調整できる。
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel readHeading = new JLabel("Readability");
        readHeading.setFont(readHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(readHeading, c);
        c.gridwidth = 1;
        row++;

        // 線種 (linetype)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Line type:"), c);
        lineTypeCombo.setSelectedIndex(lineTypeIndex(initial.getLineType()));
        lineTypeCombo.setToolTipText(
                "Relation line routing. Ortho (直交線) reduces crossings on dense class diagrams.");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(lineTypeCombo, c);
        c.gridwidth = 1;
        row++;

        // 影 (shadowing)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Shadowing:"), c);
        shadowingCombo.setSelectedIndex(shadowingIndex(initial.getShadowing()));
        shadowingCombo.setToolTipText(
                "Off gives a flatter, cleaner look that is easier to read.");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(shadowingCombo, c);
        c.gridwidth = 1;
        row++;

        // 要素間隔 (nodesep / ranksep)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Spacing:"), c);
        nodeSepSpinner.setValue(initial.getNodeSep());
        rankSepSpinner.setValue(initial.getRankSep());
        ((JSpinner.DefaultEditor) nodeSepSpinner.getEditor()).getTextField()
                .setToolTipText("nodesep: horizontal gap between nodes. 0 = PlantUML default.");
        ((JSpinner.DefaultEditor) rankSepSpinner.getEditor()).getTextField()
                .setToolTipText("ranksep: vertical gap between ranks. 0 = PlantUML default.");
        JPanel spacingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        spacingPanel.add(new JLabel("node"));
        spacingPanel.add(nodeSepSpinner);
        spacingPanel.add(Box.createHorizontalStrut(8));
        spacingPanel.add(new JLabel("rank"));
        spacingPanel.add(rankSepSpinner);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(spacingPanel, c);
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
        c.anchor = GridBagConstraints.WEST;
        row++;

        // 区切り線 (Sequence Diagram セクション)
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        // セクション見出し
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel seqHeading = new JLabel("Sequence Diagram");
        seqHeading.setFont(seqHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(seqHeading, c);
        c.gridwidth = 1;
        row++;

        // コメント表示 ON/OFF
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Comments:"), c);
        sequenceShowCommentsCheckbox.setSelected(initialSeqShowComments);
        sequenceShowCommentsCheckbox.setToolTipText(
                "Render class / method JavaDoc and body comments as notes "
                        + "at the top of sequence diagrams.");
        sequenceShowCommentsCheckbox.addActionListener(
                e -> sequenceCommentStyleCombo.setEnabled(
                        sequenceShowCommentsCheckbox.isSelected()));
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(sequenceShowCommentsCheckbox, c);
        c.gridwidth = 1;
        row++;

        // コメントスタイル
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Comment style:"), c);
        sequenceCommentStyleCombo.setSelectedItem(
                initialSeqCommentStyle == PlantUmlClassDiagram.CommentStyle.NOTE
                        ? "NOTE" : "INLINE");
        sequenceCommentStyleCombo.setEnabled(initialSeqShowComments);
        sequenceCommentStyleCombo.setToolTipText(
                "INLINE: short single-line note per call. "
                        + "NOTE: multi-line note block grouping the called method's JavaDoc.");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(sequenceCommentStyleCombo, c);
        c.gridwidth = 1;
        row++;

        // コメント表示位置
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Comment placement:"), c);
        sequenceCommentPlacementCombo.setSelectedItem(
                initialSeqPlacement == PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        ? "PARTICIPANT_TOP" : "AT_CALL_SITE");
        sequenceCommentPlacementCombo.setEnabled(initialSeqShowComments);
        sequenceCommentPlacementCombo.setToolTipText(
                "AT_CALL_SITE: show each method's JavaDoc directly under its call arrow. "
                        + "PARTICIPANT_TOP: aggregate JavaDoc under participant declarations (legacy).");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(sequenceCommentPlacementCombo, c);
        c.gridwidth = 1;
        row++;

        // クラス名修飾
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Call labels:"), c);
        sequenceQualifyMethodsCheckbox.setSelected(initialSeqQualify);
        sequenceQualifyMethodsCheckbox.setToolTipText(
                "When checked: call arrows are labelled 'Class.method()'. "
                        + "Unchecked: just 'method()'.");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(sequenceQualifyMethodsCheckbox, c);
        c.gridwidth = 1;

        // showComments のトグルに連動して placement / qualify は別系統なので、
        // placement だけは show に依存させる (qualify は独立)。
        sequenceShowCommentsCheckbox.addActionListener(
                e -> sequenceCommentPlacementCombo.setEnabled(
                        sequenceShowCommentsCheckbox.isSelected()));

        // ---- Class Diagram セクション ----
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel classHeading = new JLabel("Class Diagram");
        classHeading.setFont(classHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(classHeading, c);
        c.gridwidth = 1;
        row++;

        ClassDiagramPrefs cp = initialClassPrefs != null
                ? initialClassPrefs : ClassDiagramPrefs.defaults();
        classShowFieldsCheckbox.setSelected(cp.showFields);
        classShowMethodsCheckbox.setSelected(cp.showMethods);
        classShowAnnotationsCheckbox.setSelected(cp.showAnnotations);
        classPublicOnlyCheckbox.setSelected(cp.publicOnly);
        classExcludeExternalCheckbox.setSelected(cp.excludeExternal);
        classCommentMaxLengthSpinner.setValue(cp.commentMaxLength);
        classHiddenAnnotationsField.setText(cp.hiddenAnnotationsCsv());
        classHiddenAnnotationsField.setToolTipText(
                "Comma-separated annotation names to hide (e.g. Override,Nullable,NonNull)");

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Members:"), c);
        JPanel membersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        membersPanel.add(classShowFieldsCheckbox);
        membersPanel.add(classShowMethodsCheckbox);
        membersPanel.add(classShowAnnotationsCheckbox);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(membersPanel, c);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Filters:"), c);
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filterPanel.add(classPublicOnlyCheckbox);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(filterPanel, c);
        c.gridwidth = 1;
        row++;

        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(classExcludeExternalCheckbox, c);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Comment max length:"), c);
        ((JSpinner.DefaultEditor) classCommentMaxLengthSpinner.getEditor()).getTextField()
                .setToolTipText("0 disables inline comments. 80 is the default.");
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(classCommentMaxLengthSpinner, c);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Hidden annotations:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(classHiddenAnnotationsField, c);
        c.gridwidth = 1;
        row++;

        // ─── Call Graph ────────────────────────────────────────────────────
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(12, 4, 2, 4);
        form.add(new JSeparator(), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 3;
        form.add(new JLabel("Call Graph"), c);
        c.gridwidth = 1;
        row++;

        callGraphMaxDepthSpinner.setValue(Math.max(1, Math.min(10, initialCallGraphMaxDepth)));
        ((JSpinner.DefaultEditor) callGraphMaxDepthSpinner.getEditor()).getTextField()
                .setToolTipText("Number of method-call levels to expand (1–10). Default: 4.");
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Max depth:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(callGraphMaxDepthSpinner, c);
        c.gridwidth = 1;

        return form;
    }

    /**
     * システムにインストールされたフォントファミリで {@link #fontCombo} を構築する。
     * 先頭に「自動検出」(空値) を置き、続けて日本語フォントを優先的に並べる。
     * {@code initialFont} が一覧に無くても編集可能コンボなので選択値として保持する。
     */
    private void initFontCombo(String initialFont) {
        fontCombo.setEditable(true);
        fontCombo.removeAllItems();
        fontCombo.addItem(FONT_AUTO);
        java.util.List<String> families;
        try {
            families = juml.util.SystemFonts.familiesJapaneseFirst();
        } catch (RuntimeException ex) {
            families = java.util.Collections.emptyList();
        }
        for (String fam : families) {
            fontCombo.addItem(fam);
        }
        String init = initialFont == null ? "" : initialFont.trim();
        if (init.isEmpty()) {
            fontCombo.setSelectedItem(FONT_AUTO);
        } else {
            fontCombo.setSelectedItem(init);
            // 一覧に無い任意フォント名でも編集フィールドに反映させる
            if (!init.equals(selectedFontName())) {
                fontCombo.getEditor().setItem(init);
            }
        }
        // 選択・入力が変わるたびにプレビューを更新
        fontCombo.addActionListener(e -> updateFontPreview());
        Component editor = fontCombo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            ((JTextField) editor).getDocument().addDocumentListener(
                    new javax.swing.event.DocumentListener() {
                        @Override public void insertUpdate(
                                javax.swing.event.DocumentEvent e) { updateFontPreview(); }
                        @Override public void removeUpdate(
                                javax.swing.event.DocumentEvent e) { updateFontPreview(); }
                        @Override public void changedUpdate(
                                javax.swing.event.DocumentEvent e) { updateFontPreview(); }
                    });
        }
    }

    /** コンボの現在値を {@link DiagramStyle#getFontName()} 用の実値 (自動検出は空文字) で返す。 */
    private String selectedFontName() {
        Object sel = fontCombo.isEditable()
                ? fontCombo.getEditor().getItem() : fontCombo.getSelectedItem();
        String s = sel == null ? "" : sel.toString().trim();
        return (s.isEmpty() || FONT_AUTO.equals(s)) ? "" : s;
    }

    /** 選択中フォントでサンプル文字列を描画してプレビューを更新する。 */
    private void updateFontPreview() {
        String fam = selectedFontName();
        String sample = "Aa Bb Cc  あいう 漢字 カタカナ  0123";
        fontPreview.setText("<html>" + sample + "</html>");
        int size = 14;
        if (fam.isEmpty()) {
            fontPreview.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                    java.awt.Font.PLAIN, size));
            fontPreview.setToolTipText("Auto-detected font is used at render time.");
        } else {
            fontPreview.setFont(new java.awt.Font(fam, java.awt.Font.PLAIN, size));
            fontPreview.setToolTipText(
                    juml.util.SystemFonts.canDisplayJapanese(fam)
                            ? "This font can display Japanese."
                            : "This font may not display Japanese (文字化けの可能性)。");
        }
    }

    private static int lineTypeIndex(DiagramStyle.LineType t) {
        switch (t) {
            case POLYLINE: return 1;
            case ORTHO: return 2;
            case SPLINE: return 3;
            default: return 0;
        }
    }

    private static DiagramStyle.LineType lineTypeFromIndex(int i) {
        switch (i) {
            case 1: return DiagramStyle.LineType.POLYLINE;
            case 2: return DiagramStyle.LineType.ORTHO;
            case 3: return DiagramStyle.LineType.SPLINE;
            default: return DiagramStyle.LineType.DEFAULT;
        }
    }

    private static int shadowingIndex(DiagramStyle.Shadowing s) {
        switch (s) {
            case ON: return 1;
            case OFF: return 2;
            default: return 0;
        }
    }

    private static DiagramStyle.Shadowing shadowingFromIndex(int i) {
        switch (i) {
            case 1: return DiagramStyle.Shadowing.ON;
            case 2: return DiagramStyle.Shadowing.OFF;
            default: return DiagramStyle.Shadowing.DEFAULT;
        }
    }

    /** 「可読性優先」: 影なし・直交線・余白広めの推奨スタイルを各コントロールへ適用する。 */
    private void applyReadabilityPreset() {
        DiagramStyle r = DiagramStyle.readable();
        themeCombo.setSelectedItem(r.getTheme());
        lineTypeCombo.setSelectedIndex(lineTypeIndex(r.getLineType()));
        shadowingCombo.setSelectedIndex(shadowingIndex(r.getShadowing()));
        nodeSepSpinner.setValue(r.getNodeSep());
        rankSepSpinner.setValue(r.getRankSep());
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("Reset to Defaults");
        reset.addActionListener(e -> resetToDefaults());
        JButton readable = new JButton("Optimize for Readability");
        readable.setToolTipText(
                "Apply readability-friendly defaults: plain theme, no shadows, "
                + "ortho lines, wider spacing. (フォント・背景はそのまま)");
        readable.addActionListener(e -> applyReadabilityPreset());
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
        buttons.add(readable);
        buttons.add(reset);
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(cancel);
        buttons.add(ok);
        DialogUtils.installEscapeAndDefault(this, ok);
        return buttons;
    }

    private void resetToDefaults() {
        DiagramStyle d = DiagramStyle.defaults();
        themeCombo.setSelectedItem(d.getTheme());
        bgColorField.setText(d.getBackgroundColor());
        fontCombo.setSelectedItem(FONT_AUTO);
        fontCombo.getEditor().setItem(FONT_AUTO);
        updateFontPreview();
        fontSizeSpinner.setValue(d.getFontSize());
        dirDefault.setSelected(true);
        lineTypeCombo.setSelectedIndex(lineTypeIndex(d.getLineType()));
        shadowingCombo.setSelectedIndex(shadowingIndex(d.getShadowing()));
        nodeSepSpinner.setValue(d.getNodeSep());
        rankSepSpinner.setValue(d.getRankSep());
        customSkinparamArea.setText(d.getCustomSkinparam());
        sequenceShowCommentsCheckbox.setSelected(true);
        sequenceCommentStyleCombo.setSelectedItem("INLINE");
        sequenceCommentStyleCombo.setEnabled(true);
        sequenceCommentPlacementCombo.setSelectedItem("AT_CALL_SITE");
        sequenceCommentPlacementCombo.setEnabled(true);
        sequenceQualifyMethodsCheckbox.setSelected(true);
        ClassDiagramPrefs cp = ClassDiagramPrefs.defaults();
        classShowFieldsCheckbox.setSelected(cp.showFields);
        classShowMethodsCheckbox.setSelected(cp.showMethods);
        classShowAnnotationsCheckbox.setSelected(cp.showAnnotations);
        classPublicOnlyCheckbox.setSelected(cp.publicOnly);
        classExcludeExternalCheckbox.setSelected(cp.excludeExternal);
        classCommentMaxLengthSpinner.setValue(cp.commentMaxLength);
        classHiddenAnnotationsField.setText(cp.hiddenAnnotationsCsv());
        callGraphMaxDepthSpinner.setValue(4);
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

    private Result collect() {
        DiagramStyle s = new DiagramStyle();
        Object themeSel = themeCombo.getSelectedItem();
        s.setTheme(themeSel != null ? themeSel.toString() : "");
        s.setBackgroundColor(bgColorField.getText().trim());
        s.setFontName(selectedFontName());
        s.setFontSize((Integer) fontSizeSpinner.getValue());
        if (dirLeftRight.isSelected()) {
            s.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        } else if (dirTopBottom.isSelected()) {
            s.setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        } else {
            s.setDirection(DiagramStyle.Direction.DEFAULT);
        }
        s.setLineType(lineTypeFromIndex(lineTypeCombo.getSelectedIndex()));
        s.setShadowing(shadowingFromIndex(shadowingCombo.getSelectedIndex()));
        s.setNodeSep(((Number) nodeSepSpinner.getValue()).intValue());
        s.setRankSep(((Number) rankSepSpinner.getValue()).intValue());
        s.setCustomSkinparam(customSkinparamArea.getText());
        Object styleSel = sequenceCommentStyleCombo.getSelectedItem();
        PlantUmlClassDiagram.CommentStyle cs = "NOTE".equals(styleSel)
                ? PlantUmlClassDiagram.CommentStyle.NOTE
                : PlantUmlClassDiagram.CommentStyle.INLINE;
        Object placeSel = sequenceCommentPlacementCombo.getSelectedItem();
        PlantUmlSequenceDiagram.CommentPlacement cp =
                "PARTICIPANT_TOP".equals(placeSel)
                        ? PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        : PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        ClassDiagramPrefs classPrefs = new ClassDiagramPrefs(
                classShowFieldsCheckbox.isSelected(),
                classShowMethodsCheckbox.isSelected(),
                classShowAnnotationsCheckbox.isSelected(),
                classPublicOnlyCheckbox.isSelected(),
                classExcludeExternalCheckbox.isSelected(),
                ((Number) classCommentMaxLengthSpinner.getValue()).intValue(),
                ClassDiagramPrefs.parseCsv(classHiddenAnnotationsField.getText()));
        int cgDepth = ((Number) callGraphMaxDepthSpinner.getValue()).intValue();
        return new Result(s, sequenceShowCommentsCheckbox.isSelected(), cs, cp,
                sequenceQualifyMethodsCheckbox.isSelected(), classPrefs, cgDepth);
    }

    /**
     * モーダルダイアログを開き、編集された {@link Result} (Style + シーケンス図 + クラス図設定) を返す。
     * キャンセル時は null を返す。
     */
    public static Result showDialog(Component parent, DiagramStyle currentStyle,
                                     boolean currentSeqShowComments,
                                     PlantUmlClassDiagram.CommentStyle currentSeqCommentStyle,
                                     PlantUmlSequenceDiagram.CommentPlacement currentSeqPlacement,
                                     boolean currentSeqQualify,
                                     ClassDiagramPrefs currentClassPrefs,
                                     int currentCallGraphMaxDepth) {
        Window owner = (parent instanceof Window)
                ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent);
        DiagramStyle initial = currentStyle != null
                ? currentStyle.copy() : DiagramStyle.defaults();
        PlantUmlClassDiagram.CommentStyle initialSeqStyle = currentSeqCommentStyle != null
                ? currentSeqCommentStyle : PlantUmlClassDiagram.CommentStyle.INLINE;
        PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement =
                currentSeqPlacement != null ? currentSeqPlacement
                        : PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        StyleSettingsDialog dlg = new StyleSettingsDialog(owner, initial,
                currentSeqShowComments, initialSeqStyle, initialSeqPlacement,
                currentSeqQualify, currentClassPrefs, currentCallGraphMaxDepth);
        dlg.setVisible(true);
        return dlg.result;
    }
}
