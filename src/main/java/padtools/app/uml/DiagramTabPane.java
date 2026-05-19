package padtools.app.uml;

import padtools.app.uml.PlantUmlSvgRenderer.RenderedSvg;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 動的タブ機能 (機能 2) のラッパ。
 *
 * <p>マウス中クリックで開かれたツリーノードを「上位タブ」として並べる。
 * 各タブは内部に Preview / PlantUML Source の下位タブを持ち、独立した
 * {@link SvgPreviewPanel} と {@link PumlSourcePanel} を保有する。</p>
 *
 * <p>同じ {@link TreeNodeOpenRequest#tabKey()} のタブが既にある場合は
 * 新規作成せずフォーカスのみ移す (ブラウザの「同じ URL は同じタブ」と類似)。</p>
 *
 * <p>レンダリングは {@link PlantUmlSvgRenderer} を直接呼び出す
 * {@link SwingWorker} でバックグラウンド実行する。エラーは Source タブに退避する。</p>
 */
public final class DiagramTabPane extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, DiagramTab> openTabs = new HashMap<>();
    private final ProjectAnalysisCache cache;
    private final Consumer<String> statusReporter;

    public DiagramTabPane(ProjectAnalysisCache cache, Consumer<String> statusReporter,
                          SvgPreviewPanel mainPreview, PumlSourcePanel mainSource,
                          ActionListener saveAction) {
        super(new BorderLayout());
        this.cache = cache;
        this.statusReporter = statusReporter;
        add(tabs, BorderLayout.CENTER);

        // 永続的な "Main" タブ (閉じボタンなし) を先頭に追加する
        JPanel mainContent = new JPanel(new BorderLayout());
        JPanel saveBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton saveButton = new JButton("Save...");
        saveButton.setToolTipText("Save current diagram as SVG / PNG / PUML (Ctrl+S)");
        saveButton.setMargin(new Insets(2, 8, 2, 8));
        saveButton.setFocusable(false);
        if (saveAction != null) {
            saveButton.addActionListener(saveAction);
        }
        saveBar.add(saveButton);
        mainContent.add(saveBar, BorderLayout.NORTH);

        JTabbedPane mainInner = new JTabbedPane();
        mainInner.addTab("Preview", new JScrollPane(mainPreview));
        mainInner.addTab("PlantUML Source", mainSource);
        mainContent.add(mainInner, BorderLayout.CENTER);

        tabs.addTab("Main", mainContent);
    }

    /**
     * リクエストに対応するタブを開く。既存タブがあればフォーカスを移すだけ。
     */
    public void addOrFocusTab(TreeNodeOpenRequest req) {
        if (req == null || !cache.isLoaded()) {
            return;
        }
        String key = req.tabKey();
        DiagramTab existing = openTabs.get(key);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        DiagramTab tab = new DiagramTab(req);
        openTabs.put(key, tab);
        tabs.addTab(req.displayLabel(), tab);
        int index = tabs.indexOfComponent(tab);
        tabs.setTabComponentAt(index, buildHeader(req.displayLabel(), tab, key));
        tabs.setSelectedIndex(index);
        tab.startRender();
    }

    private JPanel buildHeader(String label, DiagramTab tab, String key) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        JLabel title = new JLabel(label);
        title.setFont(title.getFont().deriveFont(Font.PLAIN));
        JButton close = new JButton("×");  // multiplication sign
        close.setMargin(new java.awt.Insets(0, 4, 0, 4));
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(new Color(0x666666));
        close.setToolTipText("Close tab");
        close.addActionListener(e -> closeTab(tab, key));
        header.add(title);
        header.add(close);
        return header;
    }

    private void closeTab(DiagramTab tab, String key) {
        int index = tabs.indexOfComponent(tab);
        if (index >= 0) {
            tabs.remove(index);
        }
        openTabs.remove(key);
    }

    private void reportStatus(String msg) {
        if (statusReporter != null) {
            statusReporter.accept(msg);
        }
    }

    /** 1 タブ分の中身。Preview / Source の下位タブを持つ。 */
    private final class DiagramTab extends JPanel {
        private final TreeNodeOpenRequest req;
        private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
        private final PumlSourcePanel sourcePanel = new PumlSourcePanel();

        DiagramTab(TreeNodeOpenRequest req) {
            super(new BorderLayout());
            this.req = req;
            JTabbedPane inner = new JTabbedPane();
            inner.addTab("Preview", new JScrollPane(previewPanel));
            inner.addTab("PlantUML Source", sourcePanel);
            add(inner, BorderLayout.CENTER);
            // 上部に簡易タイトル
            JLabel title = new JLabel(req.displayLabel(), SwingConstants.LEFT);
            title.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            add(title, BorderLayout.NORTH);
            setPreferredSize(new Dimension(800, 600));
        }

        void startRender() {
            reportStatus("Rendering " + req.displayLabel() + " ...");
            final DiagramRequest dreq = toDiagramRequest(req);
            new SwingWorker<RenderResult, Void>() {
                private Throwable error;
                private String pumlOnError;

                @Override
                protected RenderResult doInBackground() {
                    try {
                        String puml = DiagramService.generatePuml(dreq, cache);
                        pumlOnError = puml;
                        RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
                        return new RenderResult(puml, svg);
                    } catch (Throwable ex) {
                        error = ex;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    if (error != null) {
                        if (pumlOnError != null) {
                            sourcePanel.setText(pumlOnError);
                        }
                        previewPanel.setSvgGraphicsNode(null, 0, 0);
                        reportStatus(req.displayLabel()
                                + ": rendering failed. See 'PlantUML Source'.");
                        return;
                    }
                    try {
                        RenderResult r = get();
                        if (r == null || r.svg == null) {
                            sourcePanel.setText(r != null ? r.puml : "");
                            return;
                        }
                        previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                                r.svg.getWidth(), r.svg.getHeight());
                        previewPanel.setLinkAreas(r.svg.getLinkAreas());
                        sourcePanel.setText(r.puml);
                        reportStatus(req.displayLabel() + " rendered.");
                    } catch (Exception ex) {
                        reportStatus(req.displayLabel() + ": " + ex.getMessage());
                    }
                }
            }.execute();
        }
    }

    /** {@link TreeNodeOpenRequest} を内部の {@link DiagramRequest} に変換する。 */
    private static DiagramRequest toDiagramRequest(TreeNodeOpenRequest req) {
        switch (req.target) {
            case METHOD:
                if (req.kind == DiagramKind.ACTIVITY) {
                    return DiagramRequest.forActivity(
                            req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
                }
                return new DiagramRequest(DiagramKind.SEQUENCE,
                        req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
            case CLASS:
                String fqn = req.classInfo.getQualifiedName();
                DiagramScope cs = DiagramScope.builder().seed(fqn).neighborHops(1).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, cs, false);
            case PACKAGE:
                DiagramScope ps = DiagramScope.builder().includePackage(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ps, false);
            case MODULE:
                DiagramScope ms = DiagramScope.builder().includeModule(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ms, false);
            default:
                return new DiagramRequest(DiagramKind.CLASS);
        }
    }

    private static final class RenderResult {
        final String puml;
        final RenderedSvg svg;

        RenderResult(String puml, RenderedSvg svg) {
            this.puml = puml;
            this.svg = svg;
        }
    }
}
