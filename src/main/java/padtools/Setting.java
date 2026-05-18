package padtools;

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
 * <p>ウィンドウ位置・サイズと分割ペイン位置を保持する。
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

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f))) {
            props.storeToXML(bos, "PadTools Settings");
        }
    }

    /**
     * Properties形式でファイルから読み込む。
     * 不正な値は無視してデフォルト値を使用する。
     * 旧バージョンが書き出した PAD 関連キー (editorFont.* / viewFont.* / viewColor.rgb
     * / disableSaveMenu / disableToolbar) は読み込み時に無視される。
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

        return s;
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
}
