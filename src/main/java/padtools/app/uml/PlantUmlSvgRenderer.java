package padtools.app.uml;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;
import padtools.core.formats.uml.PlantUmlRenderer;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PlantUML テキストをベクター SVG としてレンダリングし、Batik
 * {@link GraphicsNode} に変換するレンダラ。
 *
 * <p>PNG 経由の {@link PlantUmlImageRenderer} は PlantUML 既定の 4096x4096
 * キャンバス上限に縛られて巨大なクラス図が切り詰められるが、SVG 経由なら
 * その制約を受けない。GraphicsNode は {@code paint(Graphics2D)} で任意倍率に
 * 描画できるため、ズーム時もアンチエイリアスを保てる。</p>
 */
public final class PlantUmlSvgRenderer {

    private PlantUmlSvgRenderer() {
    }

    /**
     * PlantUML テキストをレンダリングし、表示用の {@link RenderedSvg} を返す。
     *
     * <p>PlantUML が空出力 (構文エラー等) を返した場合は {@code null}。</p>
     */
    public static RenderedSvg render(String puml) throws IOException {
        if (puml == null) {
            throw new IllegalArgumentException("puml is null");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, baos);
        byte[] bytes = baos.toByteArray();
        if (bytes.length == 0) {
            return null;
        }
        String parserClass = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory df = new SAXSVGDocumentFactory(parserClass);
        df.setValidating(false);
        SVGDocument doc;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            doc = (SVGDocument) df.createSVGDocument(null, bais);
        }
        UserAgent ua = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(ua);
        BridgeContext ctx = new BridgeContext(ua, loader);
        ctx.setDynamicState(BridgeContext.STATIC);
        GVTBuilder builder = new GVTBuilder();
        GraphicsNode root = builder.build(ctx, doc);
        SVGSVGElement el = doc.getRootElement();
        double w = el.getWidth().getBaseVal().getValue();
        double h = el.getHeight().getBaseVal().getValue();
        if (w <= 0 || h <= 0) {
            Rectangle2D b = root.getPrimitiveBounds();
            if (b != null) {
                w = Math.max(w, b.getX() + b.getWidth());
                h = Math.max(h, b.getY() + b.getHeight());
            }
        }
        String svg = new String(bytes, StandardCharsets.UTF_8);
        List<LinkArea> links = extractLinkAreas(doc);
        return new RenderedSvg(root, w, h, svg, links);
    }

    /**
     * SVG DOM から {@code <a href=...>} 要素を走査し、その配下の最初の
     * {@code <rect>} を境界矩形としてリンク領域を組み立てる。
     *
     * <p>PlantUML が {@code [[url]]} で生成する {@code <a>} ラッパは
     * クラス枠ごとに複数 (枠 / セパレータ / テキスト) 出力されるが、
     * 最初のラッパだけが {@code <rect>} を含むため、href ごとに最初の 1 件だけ採用する。</p>
     */
    private static List<LinkArea> extractLinkAreas(SVGDocument doc) {
        if (doc == null) {
            return Collections.emptyList();
        }
        NodeList aNodes = doc.getElementsByTagNameNS("*", "a");
        if (aNodes == null || aNodes.getLength() == 0) {
            return Collections.emptyList();
        }
        List<LinkArea> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < aNodes.getLength(); i++) {
            Node n = aNodes.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element a = (Element) n;
            String href = a.getAttributeNS("http://www.w3.org/1999/xlink", "href");
            if (href == null || href.isEmpty()) {
                href = a.getAttribute("href");
            }
            if (href == null || href.isEmpty()) {
                continue;
            }
            if (!seen.add(href)) {
                continue;
            }
            Rectangle2D box = findFirstRectBounds(a);
            if (box == null) {
                seen.remove(href);
                continue;
            }
            out.add(new LinkArea(href, box.getX(), box.getY(), box.getWidth(), box.getHeight()));
        }
        return out;
    }

    private static Rectangle2D findFirstRectBounds(Element a) {
        NodeList rects = a.getElementsByTagNameNS("*", "rect");
        if (rects == null) {
            return null;
        }
        for (int i = 0; i < rects.getLength(); i++) {
            Node n = rects.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element rect = (Element) n;
            try {
                double x = Double.parseDouble(rect.getAttribute("x"));
                double y = Double.parseDouble(rect.getAttribute("y"));
                double w = Double.parseDouble(rect.getAttribute("width"));
                double h = Double.parseDouble(rect.getAttribute("height"));
                if (w > 0 && h > 0) {
                    return new Rectangle2D.Double(x, y, w, h);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * SVG 内の {@code <a href="...">} 領域 (PlantUML の {@code [[url]]} 由来) を表す。
     * 座標は SVG 固有座標系 (スケール 1.0、左上原点) で表現する。
     */
    public static final class LinkArea {
        private final String href;
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        public LinkArea(String href, double x, double y, double width, double height) {
            this.href = href;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public String getHref() {
            return href;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        /** SVG 座標 {@code (sx, sy)} がこの矩形に含まれるか。 */
        public boolean contains(double sx, double sy) {
            return sx >= x && sx <= x + width && sy >= y && sy <= y + height;
        }
    }

    /** レンダリング結果。GraphicsNode と SVG 由来の固有サイズ・元 XML を保持する。 */
    public static final class RenderedSvg {
        private final GraphicsNode root;
        private final double width;
        private final double height;
        private final String svgXml;
        private final List<LinkArea> linkAreas;

        RenderedSvg(GraphicsNode root, double width, double height, String svgXml,
                    List<LinkArea> linkAreas) {
            this.root = root;
            this.width = width;
            this.height = height;
            this.svgXml = svgXml;
            this.linkAreas = linkAreas != null
                    ? Collections.unmodifiableList(linkAreas)
                    : Collections.emptyList();
        }

        public GraphicsNode getRoot() {
            return root;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public String getSvgXml() {
            return svgXml;
        }

        /**
         * SVG 内に埋め込まれた {@code <a href="...">} 領域の一覧。
         * 該当が無い場合は空リスト (never null)。
         */
        public List<LinkArea> getLinkAreas() {
            return linkAreas;
        }
    }
}
