package padtools.core.formats.uml;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * PlantUML テキストを SVG 等の画像形式に変換するレンダラ。
 *
 * <p>同梱の PlantUML jar を使って描画するため、外部 graphviz/dot は不要。
 * Graphviz が必要な diagram (クラス図 / コンポーネント図) では Smetana レイアウトを
 * 自動指定するため、追加インストールなしで動作する。</p>
 */
public final class PlantUmlRenderer {

    private PlantUmlRenderer() {
    }

    /** PlantUML テキストを SVG として OutputStream に書き出す。 */
    public static void renderSvg(String puml, OutputStream out) throws IOException {
        SourceStringReader reader = new SourceStringReader(injectLayout(puml));
        reader.outputImage(out, new FileFormatOption(FileFormat.SVG));
    }

    /** PlantUML テキストを SVG として指定ファイルに書き出す。 */
    public static void renderSvg(String puml, File outFile) throws IOException {
        try (OutputStream os = new FileOutputStream(outFile)) {
            renderSvg(puml, os);
        }
    }

    /**
     * {@code @startuml} の直後に {@code !pragma layout smetana} を挿入する。
     * Graphviz/dot 未インストール環境でもクラス図/コンポーネント図を描画できるようにする。
     * 既に {@code !pragma layout} 指定があれば変更しない。
     */
    static String injectLayout(String puml) {
        if (puml == null || puml.contains("!pragma layout")) {
            return puml;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0) {
            return puml;
        }
        int nl = puml.indexOf('\n', idx);
        if (nl < 0) {
            return puml + "\n!pragma layout smetana\n";
        }
        return puml.substring(0, nl + 1)
                + "!pragma layout smetana\n"
                + puml.substring(nl + 1);
    }
}
