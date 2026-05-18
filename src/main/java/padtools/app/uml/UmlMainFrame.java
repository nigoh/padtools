package padtools.app.uml;

import padtools.Main;
import padtools.Setting;
import padtools.util.ErrorListener;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * UML 専用のメインウィンドウ。
 *
 * <p>レイアウトは {@link JSplitPane} の左ペインに「プロジェクト選択 + 図種選択」、
 * 右ペインに PlantUML レンダリング結果を表示するシンプルな構成。
 * PR1 ではスケルトン (最低限のクラス図表示のみ動く) で、PR2 以降で
 * 各図種の詳細な UI / ツリー / シーケンス図起点選択を充実させる。</p>
 */
public class UmlMainFrame extends JFrame {

    private static final String WINDOW_TITLE = "PadTools UML";

    private final ProjectAnalysisCache cache = new ProjectAnalysisCache();
    private final JComboBox<DiagramKind> diagramCombo = new JComboBox<>(DiagramKind.values());
    private final JLabel projectLabel = new JLabel("(no project loaded)");
    private final JLabel imageLabel = new JLabel();
    private final JLabel status = new JLabel(" ");

    public UmlMainFrame(File initialProject) {
        super(WINDOW_TITLE);
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        // 左ペイン: プロジェクト選択 + 図種選択
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton openBtn = new JButton("Open Project...");
        openBtn.addActionListener(e -> chooseProject());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshDiagram());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(openBtn);
        top.add(refreshBtn);

        JPanel labels = new JPanel(new BorderLayout());
        labels.add(new JLabel("Project:"), BorderLayout.WEST);
        labels.add(projectLabel, BorderLayout.CENTER);

        diagramCombo.addActionListener(e -> refreshDiagram());

        leftPanel.add(top, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(labels, BorderLayout.NORTH);
        JPanel form = new JPanel(new BorderLayout(4, 4));
        form.add(new JLabel("Diagram type:"), BorderLayout.NORTH);
        form.add(diagramCombo, BorderLayout.CENTER);
        center.add(form, BorderLayout.CENTER);
        leftPanel.add(center, BorderLayout.CENTER);

        // 右ペイン: 画像表示
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        JScrollPane previewScroll = new JScrollPane(imageLabel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, previewScroll);
        split.setResizeWeight(0.25);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(status, BorderLayout.SOUTH);

        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1100;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 700;
        setPreferredSize(new Dimension(w, h));
        pack();
        setLocationRelativeTo(null);

        if (initialProject != null && initialProject.isDirectory()) {
            loadProject(initialProject);
        }
    }

    private void chooseProject() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Open Android / Gradle project");
        int r = fc.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            loadProject(fc.getSelectedFile());
        }
    }

    private void loadProject(File root) {
        status.setText("Analyzing " + root.getName() + " ...");
        new SwingWorker<Void, Void>() {
            private Throwable error;

            @Override
            protected Void doInBackground() {
                try {
                    cache.load(root, ErrorListener.silent());
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to analyze project: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                    return;
                }
                projectLabel.setText(root.getAbsolutePath());
                status.setText("Analyzed " + cache.getClasses().size() + " class(es)");
                refreshDiagram();
            }
        }.execute();
    }

    private void refreshDiagram() {
        if (!cache.isLoaded()) {
            return;
        }
        DiagramKind kind = (DiagramKind) diagramCombo.getSelectedItem();
        if (kind == null) {
            return;
        }
        if (kind == DiagramKind.SEQUENCE) {
            // PR1 ではシーケンス図の起点選択 UI 未実装。詳細は PR4 で対応。
            imageLabel.setIcon(null);
            imageLabel.setText("Sequence diagram entry selection coming soon.");
            status.setText("Sequence diagram requires entry method (PR4)");
            return;
        }
        status.setText("Rendering " + kind.getDisplayName() + " ...");
        new SwingWorker<BufferedImage, Void>() {
            private Throwable error;

            @Override
            protected BufferedImage doInBackground() {
                try {
                    String puml = DiagramService.generatePuml(
                            new DiagramRequest(kind), cache);
                    return PlantUmlImageRenderer.toBufferedImage(puml);
                } catch (Throwable ex) {
                    error = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to render: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                    return;
                }
                try {
                    BufferedImage img = get();
                    if (img == null) {
                        imageLabel.setIcon(null);
                        imageLabel.setText("(no image)");
                        return;
                    }
                    imageLabel.setText(null);
                    imageLabel.setIcon(new ImageIcon(img));
                    status.setText(kind.getDisplayName() + " rendered ("
                            + img.getWidth() + "x" + img.getHeight() + ")");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            "Failed to display: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** GUI を EDT で起動する。 */
    public static void launch(File initialProject) {
        SwingUtilities.invokeLater(() -> {
            UmlMainFrame f = new UmlMainFrame(initialProject);
            f.setVisible(true);
        });
    }
}
