package padtools;

import java.awt.Color;
import java.awt.Font;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;

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
    private Color viewColor=new Color(0.2f, 0.2f, 0.2f);

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
    public Font getEditorFont(){
        return editorFont;
    }
    public void setEditorFont(Font editorFont){
        this.editorFont = editorFont;
    }
    public Font getViewFont(){
        return viewFont;
    }
    public void setViewFont(Font viewFont){
        this.viewFont = viewFont;
    }
    public Color getViewColor(){
        return viewColor;
    }
    public void setViewColor(Color viewColor){
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

    public void saveToFile(File f) throws IOException {
        if (f == null) throw new IOException("File is null");
        final BufferedOutputStream bos =
                new BufferedOutputStream(new FileOutputStream(f));

        final XMLEncoder enc = new XMLEncoder(bos);
        enc.writeObject(this);
        enc.close();
        bos.close();
    }

    public static Setting loadFromFile(File f) throws IOException {
        final BufferedInputStream bis =
                new BufferedInputStream(new FileInputStream(f));
        final XMLDecoder dec = new XMLDecoder(bis);

        Object obj = dec.readObject();
        dec.close();
        bis.close();

        if(Setting.class.isInstance(obj)) {
            Setting s = (Setting) obj;
            return s;
        } else {
            throw new IOException("Unexpected type");
        }
    }
}
