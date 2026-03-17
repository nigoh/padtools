package padtools;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Setting {
    /** 保存メニューを非活性化するかどうか */
    private boolean disableSaveMenu = false;

    /** ツールバーを無効にする */
    private boolean disableToolbar = false;

    // fonts
    /** エディタのデフォルトフォント */
    private Font editorFont = new Font("Dialog", Font.PLAIN, 14);
    /** PAD図のフォント */
    private Font viewFont   = new Font("Dialog", Font.PLAIN, 14);
    /** PAD図の前景色 */
    private Color viewColor = new Color(0.2f, 0.2f, 0.2f);

    /** ウィンドウ位置・サイズ */
    private int windowX = -1;
    private int windowY = -1;
    private int windowWidth = 800;
    private int windowHeight = 600;
    private int mainSplitLocation = -1;
    private int leftSplitLocation = -1;

    public boolean isDisableSaveMenu() {
        return disableSaveMenu;
    }
    public void setDisableSaveMenu(boolean disableSaveMenu) {
        this.disableSaveMenu = disableSaveMenu;
    }
    public boolean isDisableToolbar() {
        return disableToolbar;
    }
    public void setDisableToolbar(boolean disableToolbar) {
        this.disableToolbar = disableToolbar;
    }
    public Font getEditorFont() {
        return editorFont;
    }
    public void setEditorFont(Font editorFont) {
        this.editorFont = editorFont;
    }
    public Font getViewFont() {
        return viewFont;
    }
    public void setViewFont(Font viewFont) {
        this.viewFont = viewFont;
    }
    public Color getViewColor() {
        return viewColor;
    }
    public void setViewColor(Color viewColor) {
        this.viewColor = viewColor;
    }

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
        if (f == null) throw new IOException("File is null");
        Properties props = new Properties();
        props.setProperty("disableSaveMenu", Boolean.toString(disableSaveMenu));
        props.setProperty("disableToolbar", Boolean.toString(disableToolbar));
        props.setProperty("editorFont.name", editorFont.getName());
        props.setProperty("editorFont.style", Integer.toString(editorFont.getStyle()));
        props.setProperty("editorFont.size", Integer.toString(editorFont.getSize()));
        props.setProperty("viewFont.name", viewFont.getName());
        props.setProperty("viewFont.style", Integer.toString(viewFont.getStyle()));
        props.setProperty("viewFont.size", Integer.toString(viewFont.getSize()));
        props.setProperty("viewColor.rgb", Integer.toString(viewColor.getRGB()));
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
     */
    public static Setting loadFromFile(File f) throws IOException {
        Properties props = new Properties();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
            props.loadFromXML(bis);
        }

        Setting s = new Setting();
        s.disableSaveMenu = Boolean.parseBoolean(props.getProperty("disableSaveMenu", "false"));
        s.disableToolbar = Boolean.parseBoolean(props.getProperty("disableToolbar", "false"));

        s.editorFont = new Font(
                props.getProperty("editorFont.name", "Dialog"),
                parseIntSafe(props.getProperty("editorFont.style"), Font.PLAIN),
                parseIntSafe(props.getProperty("editorFont.size"), 14));
        s.viewFont = new Font(
                props.getProperty("viewFont.name", "Dialog"),
                parseIntSafe(props.getProperty("viewFont.style"), Font.PLAIN),
                parseIntSafe(props.getProperty("viewFont.size"), 14));
        s.viewColor = new Color(parseIntSafe(props.getProperty("viewColor.rgb"),
                new Color(0.2f, 0.2f, 0.2f).getRGB()), true);

        s.windowX = parseIntSafe(props.getProperty("windowX"), -1);
        s.windowY = parseIntSafe(props.getProperty("windowY"), -1);
        s.windowWidth = parseIntSafe(props.getProperty("windowWidth"), 800);
        s.windowHeight = parseIntSafe(props.getProperty("windowHeight"), 600);
        s.mainSplitLocation = parseIntSafe(props.getProperty("mainSplitLocation"), -1);
        s.leftSplitLocation = parseIntSafe(props.getProperty("leftSplitLocation"), -1);

        return s;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
