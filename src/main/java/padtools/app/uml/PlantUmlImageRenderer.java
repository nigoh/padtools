package padtools.app.uml;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import padtools.core.formats.uml.PlantUmlRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PlantUML テキストを画面プレビュー用の {@link BufferedImage} に変換する。
 *
 * <p>レンダリングは同梱の PlantUML jar が提供する PNG 直接出力経由で実施するため、
 * SVG → 画像化用に Apache Batik を経由する必要がない。SVG / PDF へのエクスポート時のみ
 * {@link PlantUmlRenderer#renderSvg(String, java.io.OutputStream)} 等を使用する。</p>
 */
public final class PlantUmlImageRenderer {

    private PlantUmlImageRenderer() {
    }

    /**
     * PlantUML テキストをレンダリングして {@link BufferedImage} を返す。
     *
     * <p>{@code @startuml} 直後に Smetana レイアウトを自動注入するため、Graphviz/dot の
     * インストールは不要。{@code @startuml} を含まない文字列が渡された場合は
     * 何もせずそのまま PlantUML に解釈させる。</p>
     */
    public static BufferedImage toBufferedImage(String puml) throws IOException {
        if (puml == null) {
            throw new IllegalArgumentException("puml is null");
        }
        SourceStringReader reader = new SourceStringReader(PlantUmlRenderer.injectLayout(puml));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            reader.outputImage(baos, new FileFormatOption(FileFormat.PNG));
            byte[] bytes = baos.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(bais);
            }
        }
    }
}
