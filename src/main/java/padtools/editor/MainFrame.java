package padtools.editor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.Element;

import padtools.Constants;
import padtools.Main;
import padtools.Setting;
import padtools.core.view.Model2View;
import padtools.core.view.ViewOption;
import padtools.util.Messages;
import say.swing.JFontChooser;

/**
 * メインウインドウ。
 * @author monaou
 */
public class MainFrame extends JFrame {

    //モデル変換
    private final Model2View model2View = new Model2View();

    //リアルタイムプレビュー用タイマー（500ms遅延）
    private final Timer refreshTimer = new Timer(500, e -> applyLogic());
    {
        refreshTimer.setRepeats(false);
    }

    //エディタ部コントロール
    private final SPDEditor editor;

    //エラー一覧部コントロール
    private final JList<String> messageList;

    //ファイル管理
    private final FileManager fileManager;

    //画像エクスポート
    private final ImageExporter imageExporter;

    //プレビューパネル
    private final PreviewPanel previewPanel;

    //ステータスバー
    private final JLabel statusLine = new JLabel("1");
    private final JLabel statusColumn = new JLabel("1");
    private final JLabel statusFile = new JLabel("NEW");
    private final JLabel statusParse = new JLabel("OK");
    private final JLabel statusZoom = new JLabel("100%");

    //スプリットペイン（ウィンドウサイズ記憶用）
    private JSplitPane mainSplit;
    private JSplitPane leftSplit;

    //アイコン
    private ImageIcon iconNew, iconOpen, iconSave, iconSavePad, iconRefresh, iconHelp;

    public MainFrame(final File file) {
        Setting setting = Main.getSetting();

        //各コンポーネントを生成
        editor = new SPDEditor();
        messageList = new JList<>(new DefaultListModel<>());
        fileManager = new FileManager(this);
        imageExporter = new ImageExporter(this);
        previewPanel = new PreviewPanel(model2View);

        //アイコンを読み込む
        loadIcons();

        //イベント設定
        initSPDEditorEvent();
        initErrorListEvent();

        //描画の設定
        ViewOption defOpt = model2View.getOptionMap().get(model2View.KEY_DEFAULT);
        defOpt.setPaint(setting.getViewColor());
        defOpt.setStroke(new BasicStroke(2.0f));
        defOpt.setFont(setting.getViewFont());

        //全体のパネル生成
        JPanel mainPanel = new JPanel(new BorderLayout());
        setLayout(new BorderLayout(10, 10));

        //ツールバー設定
        setJMenuBar(createMenuBar(setting));
        if (!setting.isDisableToolbar()) {
            mainPanel.add(createToolBar(setting), BorderLayout.NORTH);
        }

        //レイアウト及びスクロールバー生成
        JComponent editorWithScroll = editor.withScroll();
        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new TitledPanel(editorWithScroll, Messages.get("panel.editor")),
                new TitledPanel(messageList, Messages.get("panel.errors")));
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftSplit, new JScrollPane(previewPanel));
        mainPanel.add(mainSplit, BorderLayout.CENTER);

        //ステータスバー
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        //表示の調整
        mainPanel.setBorder(new EmptyBorder(2, 2, 2, 2));
        messageList.setBackground(new Color(0.9f, 0.9f, 0.9f));
        messageList.setBorder(new LineBorder(Color.gray));
        leftSplit.setResizeWeight(0.8);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setBorder(null);
        leftSplit.setBorder(null);

        //メインウインドウの動作を設定
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (releaseOK()) {
                    saveWindowState();
                    MainFrame.this.dispose();
                }
            }
        });

        //ウィンドウサイズの復元
        restoreWindowState(setting);

        SwingUtilities.invokeLater(() -> {
            //スプリットペイン位置の復元
            if (setting.getMainSplitLocation() > 0) {
                mainSplit.setDividerLocation(setting.getMainSplitLocation());
            }
            if (setting.getLeftSplitLocation() > 0) {
                leftSplit.setDividerLocation(setting.getLeftSplitLocation());
            }

            if (file == null) {
                initWithDefaultText();
            } else {
                open(file);
            }
        });

        updateTitle();
    }

    // --- ステータスバー ---

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        statusBar.setPreferredSize(new Dimension(0, 22));

        statusBar.add(new JLabel(Messages.get("status.line") + ":"));
        statusBar.add(statusLine);
        statusBar.add(new JLabel(Messages.get("status.column") + ":"));
        statusBar.add(statusColumn);
        statusBar.add(new JLabel("|"));
        statusBar.add(statusFile);
        statusBar.add(new JLabel("|"));
        statusBar.add(statusParse);
        statusBar.add(new JLabel("|"));
        statusBar.add(new JLabel(Messages.get("status.zoom") + ":"));
        statusBar.add(statusZoom);

        return statusBar;
    }

    private void updateStatusBar() {
        try {
            int caretPos = editor.getCaretPosition();
            Element root = editor.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(caretPos) + 1;
            int col = caretPos - root.getElement(line - 1).getStartOffset() + 1;
            statusLine.setText(String.valueOf(line));
            statusColumn.setText(String.valueOf(col));
        } catch (Exception e) {
            // ignore
        }

        File f = fileManager.getCurrentFile();
        statusFile.setText(f == null ? "NEW" : f.getName());

        int errorCount = ((DefaultListModel<String>) messageList.getModel()).size();
        if (errorCount == 0) {
            statusParse.setText(Messages.get("status.parseOk"));
            statusParse.setForeground(new Color(0, 128, 0));
        } else {
            statusParse.setText(errorCount + " " + Messages.get("status.parseErrors"));
            statusParse.setForeground(Color.RED);
        }
    }

    // --- ウィンドウ状態の保存・復元 ---

    private void restoreWindowState(Setting setting) {
        if (setting.getWindowX() >= 0 && setting.getWindowY() >= 0) {
            setLocation(setting.getWindowX(), setting.getWindowY());
        } else {
            setLocationRelativeTo(null);
        }
        setSize(setting.getWindowWidth(), setting.getWindowHeight());
    }

    private void saveWindowState() {
        Setting setting = Main.getSetting();
        setting.setWindowX(getX());
        setting.setWindowY(getY());
        setting.setWindowWidth(getWidth());
        setting.setWindowHeight(getHeight());
        setting.setMainSplitLocation(mainSplit.getDividerLocation());
        setting.setLeftSplitLocation(leftSplit.getDividerLocation());
        Main.saveSetting();
    }

    // --- アイコン読み込み ---

    private void loadIcons() {
        iconNew = loadIcon("images/new.png");
        iconOpen = loadIcon("images/open.png");
        iconSave = loadIcon("images/save.png");
        iconRefresh = loadIcon("images/refresh.png");
        iconSavePad = loadIcon("images/pictures.png");
        iconHelp = loadIcon("images/help.png");
    }

    private ImageIcon loadIcon(String path) {
        try {
            return new ImageIcon(ImageIO.read(ClassLoader.getSystemResourceAsStream(path)));
        } catch (IOException | NullPointerException e) {
            return null;
        }
    }

    // --- アクション定義 ---

    private final ActionListener actionNew = ae -> doNew();

    private final ActionListener actionOpen = ae -> {
        if (releaseOK()) {
            openWithDialog();
        }
    };

    private final ActionListener actionSave = ae -> save();

    private final ActionListener actionSaveAs = ae -> saveAs();

    private final ActionListener actionSavePadImageAsPng = ae -> doExportPng();

    private final ActionListener actionSavePadImageAsSvg = ae -> doExportSvg();

    private final ActionListener actionRefresh = ae -> applyLogic();

    private final ActionListener actionEditorFont = ae -> showEditorFontDialog();

    private final ActionListener actionViewFont = ae -> showViewFontDialog();

    private final ActionListener actionViewColor = ae -> showViewColorDialog();

    private final ActionListener actionVersion = ae ->
            JOptionPane.showMessageDialog(MainFrame.this,
                    Constants.APP_NAME + " " + Constants.APP_VERSION,
                    Messages.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);

    private final ActionListener actionClose = ae -> {
        if (releaseOK()) {
            saveWindowState();
            MainFrame.this.dispose();
        }
    };

    private void doNew() {
        if (releaseOK()) {
            initWithDefaultText();
            fileManager.setCurrentFile(null);
            updateTitle();
            updateStatusBar();
        }
    }

    private void doNewFromTemplate(String templateKey) {
        if (releaseOK()) {
            String template = Messages.get(templateKey).replace("\\n", "\n").replace("\\t", "\t");
            editor.requestFocusInWindow();
            editor.setText(template);
            editor.setEdited(false);
            editor.setRequireSave(false);
            fileManager.setCurrentFile(null);
            applyLogic();
            updateTitle();
            updateStatusBar();
        }
    }

    private void doExportPng() {
        applyLogic();
        imageExporter.exportAsPng(previewPanel.getBufferedView(), fileManager.getCurrentFile());
    }

    private void doExportSvg() {
        applyLogic();
        imageExporter.exportAsSvg(previewPanel.getBufferedView(), fileManager.getCurrentFile(), previewPanel.getViewBounds());
    }

    private void doExportPdf() {
        applyLogic();
        imageExporter.exportAsPdf(previewPanel.getBufferedView(), fileManager.getCurrentFile());
    }

    private void doCopyDiagram() {
        applyLogic();
        imageExporter.copyToClipboard(previewPanel.getBufferedView());
    }

    private void doPrint() {
        applyLogic();
        imageExporter.print(previewPanel.getBufferedView());
    }

    private void doShowHelp() {
        HelpDialog dialog = new HelpDialog(this);
        dialog.setVisible(true);
    }

    // --- ツールバー / メニューバー ---

    private JToolBar createToolBar(Setting setting) {
        JToolBar toolBar = new JToolBar();
        toolBar.setBorderPainted(false);
        toolBar.setFloatable(false);
        toolBar.setFocusCycleRoot(false);

        addToolButton(toolBar, Messages.get("toolbar.new"), iconNew, actionNew);
        addToolButton(toolBar, Messages.get("toolbar.open"), iconOpen, actionOpen);

        if (!setting.isDisableSaveMenu()) {
            addToolButton(toolBar, Messages.get("toolbar.save"), iconSave, actionSave);
        }

        toolBar.addSeparator();
        addToolButton(toolBar, Messages.get("toolbar.refresh"), iconRefresh, actionRefresh);
        toolBar.addSeparator();
        addToolButton(toolBar, Messages.get("toolbar.exportPng"), iconSavePad, actionSavePadImageAsPng);
        addToolButton(toolBar, Messages.get("toolbar.exportSvg"), iconSavePad, actionSavePadImageAsSvg);
        addToolButton(toolBar, Messages.get("toolbar.exportPdf"), iconSavePad, ae -> doExportPdf());
        toolBar.addSeparator();
        addToolButton(toolBar, Messages.get("toolbar.copyDiagram"), null, ae -> doCopyDiagram());
        toolBar.add(Box.createGlue());
        toolBar.addSeparator();
        addToolButton(toolBar, Messages.get("toolbar.about"), iconHelp, actionVersion);

        return toolBar;
    }

    private void addToolButton(JToolBar toolBar, String text, ImageIcon icon, ActionListener action) {
        JButton button = new JButton(text, icon);
        button.setBorderPainted(false);
        button.addActionListener(action);
        toolBar.add(button);
    }

    private JMenuBar createMenuBar(Setting setting) {
        JMenuBar menuBar = new JMenuBar();

        // ファイルメニュー
        JMenu fileMenu = new JMenu(Messages.get("menu.file"));
        menuBar.add(fileMenu);

        addMenuItem(fileMenu, Messages.get("menu.file.new"), iconNew, actionNew,
                KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));

        // テンプレートサブメニュー
        JMenu templateMenu = new JMenu(Messages.get("menu.file.newFromTemplate"));
        fileMenu.add(templateMenu);
        addMenuItem(templateMenu, Messages.get("template.basic.name"), null,
                ae -> doNewFromTemplate("template.basic"), null);
        addMenuItem(templateMenu, Messages.get("template.ifelse.name"), null,
                ae -> doNewFromTemplate("template.ifelse"), null);
        addMenuItem(templateMenu, Messages.get("template.loop.name"), null,
                ae -> doNewFromTemplate("template.loop"), null);
        addMenuItem(templateMenu, Messages.get("template.switch.name"), null,
                ae -> doNewFromTemplate("template.switch"), null);

        fileMenu.addSeparator();
        addMenuItem(fileMenu, Messages.get("menu.file.open"), iconOpen, actionOpen,
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        fileMenu.addSeparator();

        if (!setting.isDisableSaveMenu()) {
            addMenuItem(fileMenu, Messages.get("menu.file.save"), iconSave, actionSave,
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        }

        addMenuItem(fileMenu, Messages.get("menu.file.saveAs"), null, actionSaveAs,
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        fileMenu.addSeparator();
        addMenuItem(fileMenu, Messages.get("menu.file.print"), null, ae -> doPrint(),
                KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        fileMenu.addSeparator();
        addMenuItem(fileMenu, Messages.get("menu.file.close"), null, actionClose, null);

        // 編集メニュー
        JMenu editMenu = new JMenu(Messages.get("menu.edit"));
        menuBar.add(editMenu);
        addMenuItem(editMenu, Messages.get("menu.edit.copyDiagram"), null, ae -> doCopyDiagram(),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

        // 出力メニュー
        JMenu outputMenu = new JMenu(Messages.get("menu.output"));
        menuBar.add(outputMenu);
        addMenuItem(outputMenu, Messages.get("menu.output.png"), iconSavePad, actionSavePadImageAsPng,
                KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        addMenuItem(outputMenu, Messages.get("menu.output.svg"), iconSavePad, actionSavePadImageAsSvg, null);
        addMenuItem(outputMenu, Messages.get("menu.output.pdf"), iconSavePad, ae -> doExportPdf(), null);

        // 表示メニュー
        JMenu viewMenu = new JMenu(Messages.get("menu.view"));
        menuBar.add(viewMenu);
        addMenuItem(viewMenu, Messages.get("menu.view.refresh"), iconRefresh, actionRefresh,
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        viewMenu.addSeparator();
        addMenuItem(viewMenu, Messages.get("menu.view.zoomIn"), null, ae -> doZoom(0.25),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        addMenuItem(viewMenu, Messages.get("menu.view.zoomOut"), null, ae -> doZoom(-0.25),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        addMenuItem(viewMenu, Messages.get("menu.view.zoomFit"), null, ae -> doZoomFit(),
                KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        addMenuItem(viewMenu, Messages.get("menu.view.zoomReset"), null, ae -> doZoomReset(),
                KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
        viewMenu.addSeparator();
        addMenuItem(viewMenu, Messages.get("menu.view.editorFont"), null, actionEditorFont, null);
        addMenuItem(viewMenu, Messages.get("menu.view.padFont"), null, actionViewFont, null);
        addMenuItem(viewMenu, Messages.get("menu.view.padColor"), null, actionViewColor, null);

        // ヘルプメニュー
        JMenu helpMenu = new JMenu(Messages.get("menu.help"));
        menuBar.add(helpMenu);
        addMenuItem(helpMenu, Messages.get("menu.help.syntaxRef"), iconHelp, ae -> doShowHelp(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        addMenuItem(helpMenu, Messages.get("menu.help.about"), iconHelp, actionVersion, null);

        return menuBar;
    }

    private void addMenuItem(JMenu menu, String text, ImageIcon icon, ActionListener action, KeyStroke accelerator) {
        JMenuItem item = new JMenuItem(text, icon);
        item.addActionListener(action);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        menu.add(item);
    }

    // --- イベント設定 ---

    private void initSPDEditorEvent() {
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent ke) {
                refreshTimer.restart();
                updateTitle();
                updateStatusBar();
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent fe) {
                if (editor.isEdited()) {
                    applyLogic();
                }
            }
            @Override
            public void focusLost(FocusEvent fe) {
                if (editor.isEdited()) {
                    applyLogic();
                }
            }
        });
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent me) {
                if (editor.isEdited()) {
                    applyLogic();
                }
                updateStatusBar();
            }
        });
        editor.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                updateStatusBar();
            }
        });
    }

    private void initErrorListEvent() {
        messageList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = messageList.locationToIndex(e.getPoint());
                if (index < 0) return;
                String errorMsg = messageList.getModel().getElementAt(index);
                // "line N, ..." 形式のメッセージから行番号を抽出
                if (errorMsg.startsWith("line ")) {
                    try {
                        String numStr = errorMsg.substring(5, errorMsg.indexOf(','));
                        int lineNo = Integer.parseInt(numStr.trim()) - 1;
                        Element root = editor.getDocument().getDefaultRootElement();
                        if (lineNo >= 0 && lineNo < root.getElementCount()) {
                            int offset = root.getElement(lineNo).getStartOffset();
                            editor.setCaretPosition(offset);
                            editor.requestFocusInWindow();
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                        // ignore
                    }
                }
            }
        });
    }

    // --- 内部ロジック ---

    private void initWithDefaultText() {
        String header = ":terminal START\n\n";
        String comment = "#" + Messages.get("default.comment").substring(1) + "\n" + Messages.get("default.logic");
        String footer = "\n\n:terminal END";
        editor.requestFocusInWindow();
        editor.setText(header + comment + footer);
        editor.select(header.length(), header.length() + comment.length());
        editor.setEdited(false);
        editor.setRequireSave(false);
        applyLogic();
    }

    private void updateTitle() {
        File currentFile = fileManager.getCurrentFile();
        String fn = currentFile == null ? "NEW" : currentFile.getPath();
        String flag = editor.isRequireSave() ? "*" : "";
        this.setTitle(Constants.APP_NAME + " " + Constants.APP_VERSION + " " + "[" + flag + fn + "]");
    }

    private void applyLogic() {
        previewPanel.refresh(editor.getText(), editor, messageList, (Graphics2D) getGraphics());
        updateStatusBar();
    }

    // --- ファイル操作 ---

    private void open(File file) {
        String content = fileManager.openFile(file);
        if (content != null) {
            editor.setText(content);
            editor.setRequireSave(false);
        }
        applyLogic();
        updateTitle();
        updateStatusBar();
    }

    private void openWithDialog() {
        String content = fileManager.openWithDialog();
        if (content != null) {
            editor.setText(content);
            editor.setRequireSave(false);
            applyLogic();
            updateTitle();
            updateStatusBar();
        }
    }

    private boolean save() {
        boolean result = fileManager.save(editor.getText());
        if (result) {
            editor.setRequireSave(false);
            applyLogic();
            updateTitle();
        } else if (fileManager.getCurrentFile() != null) {
            return saveAs();
        } else {
            return false;
        }
        return result;
    }

    private boolean saveAs() {
        boolean result = fileManager.saveWithDialog(editor.getText());
        if (result) {
            editor.setRequireSave(false);
            applyLogic();
            updateTitle();
            updateStatusBar();
        }
        return result;
    }

    private boolean releaseOK() {
        if (!editor.isRequireSave()) {
            return true;
        }

        switch (JOptionPane.showConfirmDialog(
                this,
                Messages.get("dialog.unsaved.message"),
                Messages.get("dialog.unsaved.title"),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE)) {
            case JOptionPane.YES_OPTION:
                return save();
            case JOptionPane.NO_OPTION:
                return true;
            case JOptionPane.CANCEL_OPTION:
            default:
                return false;
        }
    }

    // --- フォント/カラーダイアログ ---

    private void showFontDialog(Font oldFont, java.util.function.Consumer<Font> setFont) {
        JFontChooser f = new JFontChooser();
        f.setSelectedFont(oldFont);
        if (f.showDialog(MainFrame.this) == JFontChooser.OK_OPTION) {
            Font newFont = f.getSelectedFont();
            if (!Objects.equals(newFont, oldFont)) {
                setFont.accept(newFont);
                Main.saveSetting();
            }
        }
    }

    private void showEditorFontDialog() {
        showFontDialog(Main.getSetting().getEditorFont(), font -> {
            editor.updateFont(font);
            Main.getSetting().setEditorFont(font);
        });
    }

    private void showViewFontDialog() {
        showFontDialog(Main.getSetting().getViewFont(), font -> {
            ViewOption defOpt = model2View.getOptionMap().get(model2View.KEY_DEFAULT);
            defOpt.setFont(font);
            Main.getSetting().setViewFont(font);
            applyLogic();
        });
    }

    private void showViewColorDialog() {
        Color oldColor = Main.getSetting().getViewColor();
        Color c = JColorChooser.showDialog(this, Messages.get("dialog.colorChooser"), oldColor);
        if (c != null && !Objects.equals(oldColor, c)) {
            ViewOption defOpt = model2View.getOptionMap().get(model2View.KEY_DEFAULT);
            defOpt.setPaint(c);
            Main.getSetting().setViewColor(c);
            applyLogic();
            Main.saveSetting();
        }
    }

    // --- ズーム操作 ---

    private void doZoom(double delta) {
        previewPanel.adjustZoom(delta);
        updateZoomStatus();
    }

    private void doZoomFit() {
        previewPanel.zoomToFit();
        updateZoomStatus();
    }

    private void doZoomReset() {
        previewPanel.setZoomLevel(1.0);
        updateZoomStatus();
    }

    private void updateZoomStatus() {
        statusZoom.setText((int) (previewPanel.getZoomLevel() * 100) + "%");
    }
}
