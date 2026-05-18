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

    /**
     * 現在のスタイル設定。{@link #injectLayout(String)} 経由で挿入される。
     * 起動時に設定ファイルから読み込んだ値で上書きされる想定。GUI から変更可能。
     */
    private static volatile DiagramStyle currentStyle = DiagramStyle.defaults();

    private PlantUmlRenderer() {
    }

    /** 現在のスタイルを取得する。null 安全 (常に非 null を返す)。 */
    public static DiagramStyle getStyle() {
        DiagramStyle s = currentStyle;
        return s != null ? s : DiagramStyle.defaults();
    }

    /**
     * 現在のスタイルを更新する。以降の {@link #renderSvg} 系および
     * {@link #injectLayout(String)} 呼び出しに即時反映される。
     * null を渡すと既定スタイルにリセットする。
     */
    public static void setStyle(DiagramStyle style) {
        currentStyle = style != null ? style.copy() : DiagramStyle.defaults();
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
     * {@code @startuml} の直後に {@code !pragma layout smetana} と、現在の
     * {@link #getStyle() スタイル} 由来の {@code !theme} / {@code skinparam} 行を挿入する。
     * Graphviz/dot 未インストール環境でもクラス図/コンポーネント図を描画できるようにする。
     * 既に {@code !pragma layout} 指定があれば layout 行は重複追加しない (スタイル行は追加する)。
     */
    public static String injectLayout(String puml) {
        return injectLayout(puml, getStyle());
    }

    /**
     * スタイルを明示指定する {@link #injectLayout(String)} のオーバーロード (テスト用に公開)。
     */
    public static String injectLayout(String puml, DiagramStyle style) {
        if (puml == null) {
            return null;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0) {
            return puml;
        }
        boolean hasLayoutPragma = puml.contains("!pragma layout");
        String prelude = style != null ? style.toPlantUmlPrelude() : "";
        if (hasLayoutPragma && prelude.isEmpty()) {
            return puml;
        }
        StringBuilder injected = new StringBuilder();
        if (!hasLayoutPragma) {
            injected.append("!pragma layout smetana\n");
        }
        injected.append(prelude);
        if (injected.length() == 0) {
            return puml;
        }
        int nl = puml.indexOf('\n', idx);
        if (nl < 0) {
            return puml + "\n" + injected.toString();
        }
        return puml.substring(0, nl + 1)
                + injected.toString()
                + puml.substring(nl + 1);
    }
}
