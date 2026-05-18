package padtools.app.uml;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;
import padtools.core.formats.uml.PlantUmlRenderer;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        return new RenderedSvg(root, w, h, svg);
    }

    /** レンダリング結果。GraphicsNode と SVG 由来の固有サイズ・元 XML を保持する。 */
    public static final class RenderedSvg {
        private final GraphicsNode root;
        private final double width;
        private final double height;
        private final String svgXml;

        RenderedSvg(GraphicsNode root, double width, double height, String svgXml) {
            this.root = root;
            this.width = width;
            this.height = height;
            this.svgXml = svgXml;
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
    }
}
