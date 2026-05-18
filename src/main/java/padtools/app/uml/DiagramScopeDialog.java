package padtools.app.uml;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * クラス図 / パッケージ図の表示範囲 ({@link DiagramScope}) を編集するダイアログ。
 *
 * <p>大規模プロジェクトで全クラスを描くと PlantUML が完走しないため、
 * パッケージ・モジュール・正規表現・最大クラス数で表示を絞る。</p>
 */
public final class DiagramScopeDialog extends JDialog {

    private final JList<String> packageList;
    private final JList<String> moduleList;
    private final JTextField regexField;
    private final JSpinner maxClassesSpinner;
    private final JSpinner neighborHopsSpinner;
    private DiagramScope result;

    public DiagramScopeDialog(Window owner, List<String> packages, List<String> modules,
                              DiagramScope initial) {
        super(owner, "Diagram Scope", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        packageList = new JList<>(packages.toArray(new String[0]));
        packageList.setVisibleRowCount(10);
        moduleList = new JList<>(modules.toArray(new String[0]));
        moduleList.setVisibleRowCount(6);
        regexField = new JTextField(20);
        maxClassesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 50));
        neighborHopsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));

        if (initial != null) {
            selectAll(packageList, initial.getIncludedPackages());
            selectAll(moduleList, initial.getIncludedModules());
            if (initial.getClassNameRegex() != null) {
                regexField.setText(initial.getClassNameRegex().pattern());
            }
            maxClassesSpinner.setValue(initial.getMaxClasses());
            neighborHopsSpinner.setValue(initial.getNeighborHops());
        }

        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(520, 480));
        pack();
        setLocationRelativeTo(owner);
    }

    public DiagramScope getResult() {
        return result;
    }

    private JPanel buildForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;

        c.gridx = 0; c.gridy = 0; c.weighty = 0;
        p.add(new JLabel("Packages (multi-select):"), c);
        c.gridy = 1; c.weighty = 1;
        p.add(new JScrollPane(packageList), c);

        c.gridy = 2; c.weighty = 0;
        p.add(new JLabel("Modules (multi-select):"), c);
        c.gridy = 3; c.weighty = 0.5;
        p.add(new JScrollPane(moduleList), c);

        c.gridy = 4; c.weighty = 0;
        p.add(new JLabel("Class name regex (matches simple or qualified name):"), c);
        c.gridy = 5;
        p.add(regexField, c);

        c.gridy = 6;
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.add(new JLabel("Max classes (0 = unlimited):"));
        row.add(Box.createHorizontalStrut(4));
        row.add(maxClassesSpinner);
        row.add(Box.createHorizontalStrut(12));
        row.add(new JLabel("Seed neighbor hops:"));
        row.add(Box.createHorizontalStrut(4));
        row.add(neighborHopsSpinner);
        p.add(row, c);

        return p;
    }

    private JPanel buildButtons() {
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            result = buildScope();
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        bar.add(ok);
        bar.add(cancel);
        return bar;
    }

    private DiagramScope buildScope() {
        DiagramScope.Builder b = DiagramScope.builder();
        Set<String> pkgs = new LinkedHashSet<>(packageList.getSelectedValuesList());
        b.includePackages(pkgs);
        Set<String> mods = new LinkedHashSet<>(moduleList.getSelectedValuesList());
        b.includeModules(mods);
        String regex = regexField.getText().trim();
        if (!regex.isEmpty()) {
            try {
                b.classNameRegex(regex);
            } catch (java.util.regex.PatternSyntaxException ex) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "Invalid regex: " + ex.getMessage(),
                        "Scope", javax.swing.JOptionPane.WARNING_MESSAGE);
            }
        }
        b.maxClasses(((Number) maxClassesSpinner.getValue()).intValue());
        b.neighborHops(((Number) neighborHopsSpinner.getValue()).intValue());
        return b.build();
    }

    private static void selectAll(JList<String> list, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int[] idx = new int[values.size()];
        int n = 0;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (values.contains(list.getModel().getElementAt(i))) {
                idx[n++] = i;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(idx, 0, trimmed, 0, n);
        list.setSelectedIndices(trimmed);
    }
}
