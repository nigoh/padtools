package padtools.app.uml;

import padtools.app.uml.PlantUmlSvgRenderer.LinkArea;
import padtools.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ダブルクリック / 中クリックで開かれたダイアグラムタブを管理するクラス。
 *
 * <p>外部から渡された {@link JTabbedPane} に動的タブを挿入する。
 * 末尾 {@code fixedSuffix} 本はユーティリティタブ (Manifest / Impact / References) として
 * 予約されており、動的タブはその手前に挿入される。</p>
 *
 * <p>各タブには {@link SvgPreviewPanel} と {@link PumlSourcePanel} を
 * {@link JSplitPane} で上下に配置する (入れ子タブなし)。</p>
 *
 * <p>同じ {@link TreeNodeOpenRequest#tabKey()} のタブが既にある場合は
 * 新規作成せずフォーカスのみ移す。</p>
 */
public final class DiagramTabPane {

    private final JTabbedPane tabs;
    private final int fixedSuffix;
    private final Map<String, DiagramTab> openTabs = new LinkedHashMap<>();
    private final ProjectAnalysisCache cache;
    private final Consumer<String> statusReporter;
    /** 動的タブにフォーカスが移ったとき、その由来ノードを通知する (タブ↔ツリー同期用)。 */
    private Consumer<TreeNodeOpenRequest> onTabFocused;

    /**
     * @param tabs         ダイアグラムタブを追加する外部 JTabbedPane
     * @param fixedSuffix  末尾に固定されたユーティリティタブ数
     * @param cache        解析キャッシュ
     * @param statusReporter ステータスバー更新コールバック
     */
    public DiagramTabPane(JTabbedPane tabs, int fixedSuffix,
                          ProjectAnalysisCache cache, Consumer<String> statusReporter) {
        this.tabs = tabs;
        this.fixedSuffix = fixedSuffix;
        this.cache = cache;
        this.statusReporter = statusReporter;
        // タブ選択が動的タブに移ったら、左ツリー選択・ステータスを連動させる。
        tabs.addChangeListener(e -> handleTabSelectionChanged());
    }

    /**
     * 動的タブにフォーカスが移ったときに呼ぶコールバックを設定する。
     * 受け取り側は当該タブの由来ノードをツリーでハイライトする等に使う。
     */
    public void setOnTabFocused(Consumer<TreeNodeOpenRequest> listener) {
        this.onTabFocused = listener;
    }

    /** 現在選択中のタブが動的ダイアグラムタブなら、その由来ノードを通知しステータスを更新する。 */
    private void handleTabSelectionChanged() {
        java.awt.Component sel = tabs.getSelectedComponent();
        if (sel instanceof DiagramTab) {
            DiagramTab tab = (DiagramTab) sel;
            if (onTabFocused != null) {
                onTabFocused.accept(tab.req);
            }
            tab.reportFocusStatus();
        }
    }

    /** リクエストに対応するタブを開く。既存タブがあればフォーカスのみ移す。 */
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
        int insertAt = tabs.getTabCount() - fixedSuffix;
        tabs.insertTab(req.displayLabel(), null, tab, null, insertAt);
        tabs.setTabComponentAt(insertAt, buildHeader(req, tab, key));
        tabs.setSelectedIndex(insertAt);
        tab.startRender();
    }

    private JPanel buildHeader(TreeNodeOpenRequest req, DiagramTab tab, String key) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        header.setOpaque(false);

        header.add(new JLabel(iconFor(req)));

        JLabel title = new JLabel(req.displayLabel());
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 11f));
        header.add(title);

        JButton close = new JButton("×");
        close.setMargin(new java.awt.Insets(0, 3, 0, 3));
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(new Color(0x888888));
        close.setToolTipText("Close tab");
        close.addActionListener(e -> closeTab(tab, key));
        header.add(close);
        return header;
    }

    private static TreeNodeIcon iconFor(TreeNodeOpenRequest req) {
        if (req.target == TreeNodeOpenRequest.Target.METHOD) {
            return req.kind == DiagramKind.ACTIVITY ? TreeNodeIcon.ACTIVITY : TreeNodeIcon.SEQUENCE;
        }
        if (req.target == TreeNodeOpenRequest.Target.CLASS)   return TreeNodeIcon.CLASS;
        if (req.target == TreeNodeOpenRequest.Target.PACKAGE) return TreeNodeIcon.PACKAGE;
        return TreeNodeIcon.MODULE;
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

    /**
     * 1 タブ分の内容。SVG プレビューと PlantUML ソースを JSplitPane で上下に配置。
     * 入れ子タブなし。
     */
    private final class DiagramTab extends JPanel {
        private final TreeNodeOpenRequest req;
        private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
        private final PumlSourcePanel sourcePanel  = new PumlSourcePanel();
        private String renderedPuml;
        /** このタブの最新ステータス文言。タブ再フォーカス時にステータスバーへ復元する。 */
        private String lastStatus;

        DiagramTab(TreeNodeOpenRequest req) {
            super(new java.awt.BorderLayout());
            this.req = req;
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JScrollPane(previewPanel), sourcePanel);
            split.setResizeWeight(0.85);
            split.setDividerLocation(0.85);
            add(split, java.awt.BorderLayout.CENTER);
            previewPanel.setOnLinkClick(this::handleLinkClick);
            previewPanel.setOnLinkPopup(this::handleLinkPopup);
        }

        /** このタブのステータスを記録し、ステータスバーにも反映する。 */
        private void setStatus(String msg) {
            lastStatus = msg;
            reportStatus(msg);
        }

        /** タブにフォーカスが戻ったとき、ステータスバーをこのタブの最新状態に復元する。 */
        void reportFocusStatus() {
            reportStatus(lastStatus != null ? lastStatus
                    : req.displayLabel() + " (tab)");
        }

        void startRender() {
            setStatus("Rendering " + req.displayLabel() + " ...");
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
                        setStatus(req.displayLabel() + ": rendering failed.");
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
                        renderedPuml = r.puml;
                        setStatus(req.displayLabel() + " rendered.");
                    } catch (Exception ex) {
                        setStatus(req.displayLabel() + ": " + ex.getMessage());
                    }
                }
            }.execute();
        }

        private void handleLinkClick(LinkArea link, MouseEvent event) {
            if (link == null) return;
            String href = link.getHref();
            if (href == null) return;
            if (href.startsWith("padtools://method/")) {
                showMethodMenuInTab(href, event);
                return;
            }
            String fqn = parseClassFqnFromHref(href);
            if (fqn == null) return;
            cache.getIndex().header(fqn).ifPresent(
                    ci -> addOrFocusTab(TreeNodeOpenRequest.classNode(ci)));
        }

        private void showMethodMenuInTab(String href, MouseEvent event) {
            String path = href.substring("padtools://method/".length());
            int hash = path.lastIndexOf('#');
            if (hash < 0) return;
            String classFqn = path.substring(0, hash);
            String methodName = path.substring(hash + 1);
            if (classFqn.isEmpty() || methodName.isEmpty()) return;
            JavaClassInfo classInfo = cache.getIndex().header(classFqn).orElse(null);
            if (classInfo == null) {
                // ヘッダが無い場合はシンプル名だけ持つダミーで代用
                classInfo = new JavaClassInfo();
                classInfo.setSimpleName(extractSimpleClass(classFqn));
            }
            JavaMethodInfo methodInfo = new JavaMethodInfo();
            methodInfo.setName(methodName);
            final JavaClassInfo ci = classInfo;
            final JavaMethodInfo mi = methodInfo;
            JPopupMenu menu = new JPopupMenu();
            JMenuItem seqItem = new JMenuItem("Sequence Diagram");
            seqItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.SEQUENCE)));
            menu.add(seqItem);
            JMenuItem actItem = new JMenuItem("Activity Diagram");
            actItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.ACTIVITY)));
            menu.add(actItem);
            menu.show(event.getComponent(), event.getX(), event.getY());
        }

        private void handleLinkPopup(LinkArea link, MouseEvent event) {
            if (event == null) return;
            JPopupMenu popup = new JPopupMenu("Export");
            JMenuItem saveSvg = new JMenuItem("Save as SVG...");
            saveSvg.addActionListener(e -> exportTabAs(UmlExporter.Format.SVG));
            popup.add(saveSvg);
            JMenuItem savePng = new JMenuItem("Save as PNG...");
            savePng.addActionListener(e -> exportTabAs(UmlExporter.Format.PNG));
            popup.add(savePng);
            JMenuItem savePuml = new JMenuItem("Save as PlantUML...");
            savePuml.addActionListener(e -> exportTabAs(UmlExporter.Format.PUML));
            popup.add(savePuml);
            popup.show(event.getComponent(), event.getX(), event.getY());
        }

        private void exportTabAs(UmlExporter.Format fmt) {
            String puml = renderedPuml;
            if (puml == null || puml.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No diagram to export yet.", "Export",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String ext = fmt.getExtension();
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save diagram as " + ext.toUpperCase());
            fc.setAcceptAllFileFilterUsed(false);
            fc.setFileFilter(new FileNameExtensionFilter(
                    ext.toUpperCase() + " (*." + ext + ")", ext));
            Window owner = SwingUtilities.getWindowAncestor(this);
            int r = fc.showSaveDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) return;
            File chosen = fc.getSelectedFile();
            if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)) {
                chosen = new File(chosen.getAbsolutePath() + "." + ext);
            }
            try {
                java.awt.image.BufferedImage img = null;
                if (fmt == UmlExporter.Format.PNG) {
                    img = PlantUmlImageRenderer.toBufferedImage(puml);
                }
                UmlExporter.export(fmt, chosen, puml, img);
                reportStatus("Saved: " + chosen.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static DiagramRequest toDiagramRequest(TreeNodeOpenRequest req) {
        switch (req.target) {
            case METHOD:
                if (req.kind == DiagramKind.ACTIVITY) {
                    return DiagramRequest.forActivity(
                            req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
                }
                return new DiagramRequest(DiagramKind.SEQUENCE,
                        req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
            case CLASS: {
                String fqn = req.classInfo.getQualifiedName();
                DiagramScope cs = DiagramScope.builder().seed(fqn).neighborHops(1).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, cs, false);
            }
            case PACKAGE: {
                DiagramScope ps = DiagramScope.builder().includePackage(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ps, false);
            }
            case MODULE: {
                DiagramScope ms = DiagramScope.builder().includeModule(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ms, false);
            }
            default:
                return new DiagramRequest(DiagramKind.CLASS);
        }
    }

    private static final class RenderResult {
        final String puml;
        final RenderedSvg svg;
        RenderResult(String puml, RenderedSvg svg) { this.puml = puml; this.svg = svg; }
    }

    private static String parseClassFqnFromHref(String href) {
        if (href == null) return null;
        final String prefix = "padtools://class/";
        if (!href.startsWith(prefix)) return null;
        String s = href.substring(prefix.length()).trim();
        return s.isEmpty() ? null : s;
    }

    private static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) return "";
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }
}
