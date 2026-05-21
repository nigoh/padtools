package padtools.core.formats.uml;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PlantUML 描画スタイル設定。
 *
 * <p>{@link PlantUmlRenderer} が {@code @startuml} 直後に挿入する
 * {@code !theme} / {@code skinparam} 行を生成する値オブジェクト。
 * 各フィールドは未指定 (null / 空文字 / 0 / {@link Direction#DEFAULT}) を取れて、
 * その場合は対応する行を出力しない。</p>
 *
 * <p>本オブジェクトは GUI / 設定永続化 (Properties XML) / レンダラの 3 者で共有される。</p>
 */
public final class DiagramStyle {

    /** PlantUML のファイル読み込み・プリプロセッサ系ディレクティブ（危険）を除去するパターン。 */
    private static final Pattern UNSAFE_DIRECTIVE =
            Pattern.compile("(?im)^[ \\t]*!(?:include|pragma|define|undef|ifdef|ifndef|else|endif|import|log|dump_memory|endprocedure|procedure|function|endfunction|return|call|startsub|endsub|stdlib)[^\\n]*", Pattern.MULTILINE);

    /** 図の描画方向。 */
    public enum Direction {
        /** PlantUML デフォルト (上から下)。指定行を出さない。 */
        DEFAULT,
        /** {@code left to right direction} を出力。 */
        LEFT_TO_RIGHT,
        /** {@code top to bottom direction} を出力 (明示)。 */
        TOP_TO_BOTTOM
    }

    private String theme = "";
    private String backgroundColor = "";
    private String fontName = "";
    private int fontSize = 0;
    private Direction direction = Direction.DEFAULT;
    private String customSkinparam = "";

    /** 全フィールド未指定の既定スタイル。 */
    public static DiagramStyle defaults() {
        return new DiagramStyle();
    }

    /** 値をすべてコピーした独立インスタンスを返す。 */
    public DiagramStyle copy() {
        DiagramStyle s = new DiagramStyle();
        s.theme = this.theme;
        s.backgroundColor = this.backgroundColor;
        s.fontName = this.fontName;
        s.fontSize = this.fontSize;
        s.direction = this.direction;
        s.customSkinparam = this.customSkinparam;
        return s;
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme == null ? "" : theme; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor == null ? "" : backgroundColor;
    }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName == null ? "" : fontName; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = Math.max(0, fontSize); }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) {
        this.direction = direction == null ? Direction.DEFAULT : direction;
    }

    public String getCustomSkinparam() { return customSkinparam; }
    public void setCustomSkinparam(String customSkinparam) {
        this.customSkinparam = customSkinparam == null ? "" : customSkinparam;
    }

    /**
     * PlantUML に挿入する {@code !theme} / {@code skinparam} 等のプレリュード行を返す。
     * フィールドが未指定の項目は対応する行を出力しない。
     * 戻り値は改行終端 (空でない場合)。空文字を返すこともある。
     */
    public String toPlantUmlPrelude() {
        StringBuilder sb = new StringBuilder();
        if (!theme.isEmpty()) {
            sb.append("!theme ").append(theme).append('\n');
        }
        if (!backgroundColor.isEmpty()) {
            sb.append("skinparam backgroundColor ").append(backgroundColor).append('\n');
        }
        if (!fontName.isEmpty()) {
            sb.append("skinparam defaultFontName ").append(fontName).append('\n');
        }
        if (fontSize > 0) {
            sb.append("skinparam defaultFontSize ").append(fontSize).append('\n');
        }
        switch (direction) {
            case LEFT_TO_RIGHT:
                sb.append("left to right direction\n");
                break;
            case TOP_TO_BOTTOM:
                sb.append("top to bottom direction\n");
                break;
            default:
                break;
        }
        if (!customSkinparam.isEmpty()) {
            // ユーザ入力の改行は LF に正規化し、危険なプリプロセッサ系ディレクティブを除去して埋め込む。
            String normalized = customSkinparam.replace("\r\n", "\n").replace('\r', '\n');
            String sanitized = UNSAFE_DIRECTIVE.matcher(normalized).replaceAll("");
            sb.append(sanitized);
            if (!sanitized.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagramStyle)) return false;
        DiagramStyle that = (DiagramStyle) o;
        return fontSize == that.fontSize
                && Objects.equals(theme, that.theme)
                && Objects.equals(backgroundColor, that.backgroundColor)
                && Objects.equals(fontName, that.fontName)
                && direction == that.direction
                && Objects.equals(customSkinparam, that.customSkinparam);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, backgroundColor, fontName, fontSize, direction, customSkinparam);
    }
}
