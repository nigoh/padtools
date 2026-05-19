package padtools;

import padtools.core.formats.uml.DiagramStyle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * GUI 関連の永続化設定。
 *
 * <p>ウィンドウ位置・サイズと分割ペイン位置、PlantUML 描画スタイルを保持する。
 * フォーマットは Properties (XML) で、未知のキーは無視される。
 * 2.0 で PAD 用のフィールド (フォント / 色 / ツールバー / 保存メニュー設定) は削除された。
 * 旧バージョンが書き出した XML から読み込んだ場合、それらのキーは単に無視される。</p>
 */
public class Setting {

    /** ウィンドウ位置・サイズ */
    private int windowX = -1;
    private int windowY = -1;
    private int windowWidth = 1200;
    private int windowHeight = 800;
    /** 左/右ペインの分割位置 */
    private int mainSplitLocation = -1;
    private int leftSplitLocation = -1;
    /** PlantUML 描画スタイル (テーマ・色・フォント等)。 */
    private String styleTheme = "";
    private String styleBackgroundColor = "";
    private String styleFontName = "";
    private int styleFontSize = 0;
    private String styleDirection = DiagramStyle.Direction.DEFAULT.name();
    private String styleCustomSkinparam = "";
    /** シーケンス図に JavaDoc / コメントを note として表示するか。 */
    private boolean sequenceShowComments = true;
    /** シーケンス図のコメント表示スタイル ("INLINE" | "NOTE")。 */
    private String sequenceCommentStyle = "INLINE";
    /** コメント表示位置 ("AT_CALL_SITE" | "PARTICIPANT_TOP")。 */
    private String sequenceCommentPlacement = "AT_CALL_SITE";
    /** メソッド呼び出しラベルにクラス名を付けるか (例: "Foo.bar()" vs "bar()")。 */
    private boolean sequenceQualifyMethodNames = true;

    public int getWindowX() { return windowX; }
    public void setWindowX(int windowX) { this.windowX = windowX; }
    public int getWindowY() { return windowY; }
    public void setWindowY(int windowY) { this.windowY = windowY; }
    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }
    public int getMainSplitLocation() { return mainSplitLocation; }
    public void setMainSplitLocation(int mainSplitLocation) { this.mainSplitLocation = mainSplitLocation; }
    public int getLeftSplitLocation() { return leftSplitLocation; }
    public void setLeftSplitLocation(int leftSplitLocation) { this.leftSplitLocation = leftSplitLocation; }

    public boolean isSequenceShowComments() { return sequenceShowComments; }
    public void setSequenceShowComments(boolean v) { this.sequenceShowComments = v; }
    public String getSequenceCommentStyle() { return sequenceCommentStyle; }
    public void setSequenceCommentStyle(String v) {
        this.sequenceCommentStyle = (v == null || v.isEmpty()) ? "INLINE" : v;
    }
    public String getSequenceCommentPlacement() { return sequenceCommentPlacement; }
    public void setSequenceCommentPlacement(String v) {
        this.sequenceCommentPlacement = (v == null || v.isEmpty())
                ? "AT_CALL_SITE" : v;
    }
    public boolean isSequenceQualifyMethodNames() { return sequenceQualifyMethodNames; }
    public void setSequenceQualifyMethodNames(boolean v) {
        this.sequenceQualifyMethodNames = v;
    }

    /** 永続化済みの値から {@link DiagramStyle} を組み立てて返す。 */
    public DiagramStyle getStyle() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme(styleTheme);
        s.setBackgroundColor(styleBackgroundColor);
        s.setFontName(styleFontName);
        s.setFontSize(styleFontSize);
        s.setDirection(parseDirection(styleDirection));
        s.setCustomSkinparam(styleCustomSkinparam);
        return s;
    }

    /** GUI 等から受け取った {@link DiagramStyle} の値を永続化フィールドへ反映する。 */
    public void setStyle(DiagramStyle style) {
        DiagramStyle s = style != null ? style : DiagramStyle.defaults();
        styleTheme = s.getTheme();
        styleBackgroundColor = s.getBackgroundColor();
        styleFontName = s.getFontName();
        styleFontSize = s.getFontSize();
        styleDirection = s.getDirection().name();
        styleCustomSkinparam = s.getCustomSkinparam();
    }

    /**
     * Properties形式でファイルに保存する。
     * XMLEncoder/XMLDecoderはデシリアライズ攻撃の脆弱性があるため使用しない。
     */
    public void saveToFile(File f) throws IOException {
        if (f == null) {
            throw new IOException("File is null");
        }
        Properties props = new Properties();
        props.setProperty("windowX", Integer.toString(windowX));
        props.setProperty("windowY", Integer.toString(windowY));
        props.setProperty("windowWidth", Integer.toString(windowWidth));
        props.setProperty("windowHeight", Integer.toString(windowHeight));
        props.setProperty("mainSplitLocation", Integer.toString(mainSplitLocation));
        props.setProperty("leftSplitLocation", Integer.toString(leftSplitLocation));
        props.setProperty("style.theme", styleTheme);
        props.setProperty("style.backgroundColor", styleBackgroundColor);
        props.setProperty("style.fontName", styleFontName);
        props.setProperty("style.fontSize", Integer.toString(styleFontSize));
        props.setProperty("style.direction", styleDirection);
        props.setProperty("style.customSkinparam", styleCustomSkinparam);
        props.setProperty("sequence.showComments", Boolean.toString(sequenceShowComments));
        props.setProperty("sequence.commentStyle", sequenceCommentStyle);
        props.setProperty("sequence.commentPlacement", sequenceCommentPlacement);
        props.setProperty("sequence.qualifyMethodNames",
                Boolean.toString(sequenceQualifyMethodNames));

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f))) {
            props.storeToXML(bos, "PadTools Settings");
        }
    }

    /**
     * Properties形式でファイルから読み込む。
     * 不正な値は無視してデフォルト値を使用する。
     * 旧バージョンが書き出した PAD 関連キー (editorFont.* / viewFont.* / viewColor.rgb
     * / disableSaveMenu / disableToolbar) は読み込み時に無視される。
     * 新規追加された {@code style.*} キーが無い場合は既定スタイル ((None) テーマ) を用いる。
     */
    public static Setting loadFromFile(File f) throws IOException {
        Properties props = new Properties();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
            props.loadFromXML(bis);
        }

        Setting s = new Setting();
        s.windowX = parseIntSafe(props.getProperty("windowX"), -1);
        s.windowY = parseIntSafe(props.getProperty("windowY"), -1);
        s.windowWidth = parseIntSafe(props.getProperty("windowWidth"), 1200);
        s.windowHeight = parseIntSafe(props.getProperty("windowHeight"), 800);
        s.mainSplitLocation = parseIntSafe(props.getProperty("mainSplitLocation"), -1);
        s.leftSplitLocation = parseIntSafe(props.getProperty("leftSplitLocation"), -1);
        s.styleTheme = stringOrEmpty(props.getProperty("style.theme"));
        s.styleBackgroundColor = stringOrEmpty(props.getProperty("style.backgroundColor"));
        s.styleFontName = stringOrEmpty(props.getProperty("style.fontName"));
        s.styleFontSize = parseIntSafe(props.getProperty("style.fontSize"), 0);
        s.styleDirection = stringOrDefault(props.getProperty("style.direction"),
                DiagramStyle.Direction.DEFAULT.name());
        s.styleCustomSkinparam = stringOrEmpty(props.getProperty("style.customSkinparam"));
        s.sequenceShowComments = parseBooleanSafe(
                props.getProperty("sequence.showComments"), true);
        s.sequenceCommentStyle = stringOrDefault(
                props.getProperty("sequence.commentStyle"), "INLINE");
        s.sequenceCommentPlacement = stringOrDefault(
                props.getProperty("sequence.commentPlacement"), "AT_CALL_SITE");
        s.sequenceQualifyMethodNames = parseBooleanSafe(
                props.getProperty("sequence.qualifyMethodNames"), true);

        return s;
    }

    private static boolean parseBooleanSafe(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = value.trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return defaultValue;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static DiagramStyle.Direction parseDirection(String name) {
        if (name == null || name.isEmpty()) {
            return DiagramStyle.Direction.DEFAULT;
        }
        try {
            return DiagramStyle.Direction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DiagramStyle.Direction.DEFAULT;
        }
    }
}
