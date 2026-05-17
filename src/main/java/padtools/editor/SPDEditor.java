package padtools.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.undo.UndoManager;
import javax.swing.JOptionPane;

import padtools.Main;
import padtools.util.Messages;
/**
 * LPDを編集するペイン。
 * @author monaou
 */
class SPDEditor extends JTextPane {

    private class RowHeader extends JComponent {
        private int fontHeight = 0;
        private int lastDigitCount = 3;

        private RowHeader(){
            setPreferredSize(new Dimension(34, 0));
            setBorder(null);
            setBackground(new Color(0xF0, 0xF0, 0xF0));
            setOpaque(true);
            setFont(new Font("Dialog", Font.PLAIN, 10));
        }

        private void updateWidth() {
            int lines = SPDEditor.this.getDocument().getDefaultRootElement().getElementCount();
            int digits = Math.max(3, String.valueOf(Math.max(1, lines)).length());
            if (digits != lastDigitCount) {
                lastDigitCount = digits;
                FontMetrics fm = getFontMetrics(getFont());
                int w = fm.charWidth('0') * digits + 10;
                setPreferredSize(new Dimension(w, 0));
                revalidate();
            }
        }

        @Override
        protected void paintComponent(Graphics grphcs) {
            grphcs.setColor(getBackground());
            grphcs.fillRect(0, 0, getWidth(), getHeight());

            if( fontHeight <= 0 ){
                FontMetrics fm = getFontMetrics(getFont());
                fontHeight = fm.getHeight();
            }
            updateWidth();

            Element elm = SPDEditor.this.getDocument().getDefaultRootElement();
            Rectangle rect = grphcs.getClipBounds();
            int startno = elm.getElementIndex(SPDEditor.this.viewToModel2D(new Point2D.Double(0, rect.y)));
            int endno = elm.getElementIndex(SPDEditor.this.viewToModel2D(new Point2D.Double(0, rect.y + rect.height)));

            String fmt = "%0" + lastDigitCount + "d";
            for(int no=startno; no<=endno; ++no){
                try {
                    Rectangle2D re = SPDEditor.this.modelToView2D(elm.getElement(no).getStartOffset());

                    if( re != null){
                        grphcs.setColor(Color.DARK_GRAY);
                        grphcs.drawString(String.format(fmt, no + 1), 4, (int) (re.getY() + fontHeight - 2));
                    }
                } catch (BadLocationException ex) {
                    Logger.getLogger(SPDEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private class SimpleSyntaxDocument extends DefaultStyledDocument {

        private HashSet<String> keywords = new HashSet<String>();
        private MutableAttributeSet keywordMark = new SimpleAttributeSet();
        private MutableAttributeSet normal = new SimpleAttributeSet();
        private MutableAttributeSet comment = new SimpleAttributeSet();

        private SimpleSyntaxDocument() {
            super();
            StyleConstants.setForeground(normal, Color.BLACK);
            StyleConstants.setBold(normal, false);
            StyleConstants.setForeground(keywordMark, Color.BLUE);
            StyleConstants.setBold(keywordMark, true);
            StyleConstants.setForeground(comment, Color.GRAY);
            keywords.add(":if");
            keywords.add(":while");
            keywords.add(":else");
            keywords.add(":switch");
            keywords.add(":dowhile");
            keywords.add(":comment");
            keywords.add(":case");
            keywords.add(":call");
            keywords.add(":terminal");
        }
        
        @Override
        public Font getFont(AttributeSet attr){
            Font f = super.getFont(attr);
            Font base = SPDEditor.this.getFont();
            return base.deriveFont(f.getStyle());
        }

        @Override
        public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
            setEdited(true);
            setRequireSave(true);
            super.insertString(offset, str, a);
            applyHighlight(offset, str.length());
            
            if(str.equals("\n")){
                Element root = getDefaultRootElement();
                int startLine = root.getElementIndex(offset);
                int s = root.getElement(startLine).getStartOffset();
                int e = root.getElement(startLine).getEndOffset();
                String line = getText(s, e-s);
                String tab = "";
                for(int i=0; i<line.length() && line.charAt(i) == '\t'; ++i){
                    tab += "\t";
                }
                
                line = line.trim();
                if(     line.startsWith(":if") || 
                        line.startsWith(":while") ||
                        line.startsWith(":dowhile") ||
                        line.startsWith(":case") ||
                        line.startsWith(":else")){
                    tab += "\t";
                }

                insertString(offset + str.length(),tab, a);
            }
            
        }

        @Override
        public void remove(int offset, int length) throws BadLocationException {
            setEdited(true);
            setRequireSave(true);
            super.remove(offset, length);
            applyHighlight(offset, 0);
        }
        
        public void refreshHighlght(){
            try{
                applyHighlight(0, getLength());
            }catch(BadLocationException ex){}
        }

        private void applyHighlight(int offset, int length) throws BadLocationException {
            Element root = getDefaultRootElement();
            int startLine = root.getElementIndex(offset);
            int endLine = root.getElementIndex(offset + length);
            for (int i = startLine; i <= endLine; i++) {
                int s = root.getElement(i).getStartOffset();
                int e = root.getElement(i).getEndOffset();
                setCharacterAttributes(s, e-s, normal, true);
                String line = getText(s, e-s);
                if( line.trim().startsWith("#")){
                    setCharacterAttributes(s, e-s, comment, true);
                }
                else {
                    for(String key : keywords){
                        for(int n=0;;n++){
                            int pos = line.indexOf(key, n);
                            if( pos < 0)break;
                            if(
                                    (pos == 0 || Character.isWhitespace(line.charAt(pos-1))) &&
                                    (pos + key.length() + 1 == line.length() || Character.isWhitespace(line.charAt(pos + key.length())))){
                                setCharacterAttributes(s + pos, key.length(),keywordMark, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final String[] COMMANDS = {
        ":if", ":else", ":while", ":dowhile", ":switch", ":case", ":call", ":terminal", ":comment"
    };

    private static final int MENU_MASK = computeMenuShortcutMask();

    private static int computeMenuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ex) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    private final SimpleSyntaxDocument doc;
    private final RowHeader rowHeader;
    private boolean edited = false;
    private boolean reqSave = false;
    private UndoManager undoManager;

    // オートコンプリート
    private JPopupMenu autocompletePopup;
    private JList<String> autocompleteList;
    private int autocompleteStart = -1;

    // 検索
    private String lastSearchText = null;

    public SPDEditor() {
        setDocument(doc = (SimpleSyntaxDocument)new SimpleSyntaxDocument());
        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setSpaceAbove(attr, 2.0f);
        doc.setParagraphAttributes(0, doc.getLength(), attr, true);

        //タブを設定する。
        setFont(Main.getSetting().getEditorFont());
        FontMetrics fm = getFontMetrics(getFont());
        int charWidth = fm.charWidth('m');
        int tabLength = charWidth * 2;
        TabStop[] tabs = new TabStop[10];
        for (int j = 0; j < tabs.length; j++) {
            tabs[j] = new TabStop((j + 1) * tabLength);
        }
        TabSet tabSet = new TabSet(tabs);
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setTabSet(attrs, tabSet);
        int l = getDocument().getLength();
        getStyledDocument().setParagraphAttributes(0, l, attrs, false);

        rowHeader = new RowHeader();

        //右クリックメニューをつける
        new JTextEditPopupMenu(this).assignEvent();

        undoManager = new UndoManager();
        setupUndoRedo();
        setupAutocomplete();
        setupBlockIndent();
    }
    
    public void updateFont(Font font){
        if(!Objects.equals(font, getFont())){
            setFont(font);
            setText(getText()); //rerendering
        }
    }
    
    public void setErrorLine(int line){
        int s = doc.getDefaultRootElement().getElement(line).getStartOffset();
        int e = doc.getDefaultRootElement().getElement(line).getEndOffset();
        
        MutableAttributeSet error = new SimpleAttributeSet();
        StyleConstants.setForeground(error, Color.RED);
        StyleConstants.setUnderline(error, true);
        doc.setCharacterAttributes(s, e-s, error, true);
    }

    @Override
    public void paint(Graphics grphcs) {
        super.paint(grphcs);
        
        Rectangle rect = grphcs.getClipBounds();
        rowHeader.repaint(0, rect.y, rowHeader.getWidth(), rect.height);
    }
    
    public JScrollPane withScroll(){
        JPanel panel = new JPanel(new BorderLayout(0,0)){

            @Override
            public Dimension getPreferredSize() {
                Dimension dim = super.getPreferredSize();
                return new Dimension(1, dim.height);
            }
            
        };
        panel.add(rowHeader, BorderLayout.WEST);
        panel.add(this, BorderLayout.CENTER);
        this.setBorder(null);
        
        JScrollPane ret = new JScrollPane(panel);
        ret.setBorder(new LineBorder(Color.gray));
        

        return ret;
    }
    
    public void refreshHighlight(){
        doc.refreshHighlght();
    }
    
    
    /**
     * @return the edited
     */
    public boolean isEdited() {
        return edited;
    }

    /**
     * @param edited the edited to set
     */
    public void setEdited(boolean edited) {
        this.edited = edited;
    }
    
    /**
     * @return the saved
     */
    public boolean isRequireSave() {
        return reqSave;
    }

    /**
     * @param reqSave the saved to set
     */
    public void setRequireSave(boolean reqSave) {
        this.reqSave = reqSave;
    }
    
    /**
     * @return the undoManager
     */
    public void setupUndoRedo() {
        getDocument().addUndoableEditListener(e -> {
            if (e.getEdit() instanceof DocumentEvent) {
                DocumentEvent de = (DocumentEvent) e.getEdit();
                DocumentEvent.EventType type = de.getType();
    
                if (type == DocumentEvent.EventType.INSERT) {
                    undoManager.addEdit(e.getEdit());
                } else if (type == DocumentEvent.EventType.REMOVE) {
                    undoManager.addEdit(e.getEdit());
                }
            }
        });
    
        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();
    
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_MASK), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                    refreshHighlight();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, MENU_MASK), "redo");
        // macOS の標準は Cmd+Shift+Z
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_MASK | InputEvent.SHIFT_DOWN_MASK), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                    refreshHighlight();
                }
            }
        });
    }

    // --- ブロックインデント / アウトデント ---

    private void setupBlockIndent() {
        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();

        // Tab: 選択中なら全行インデント、それ以外は通常のタブ挿入
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "smartTab");
        actionMap.put("smartTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (hasMultiLineSelection()) {
                    indentSelection(true);
                } else {
                    replaceSelection("\t");
                }
            }
        });

        // Shift+Tab: 全行アウトデント
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), "smartOutdent");
        actionMap.put("smartOutdent", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                indentSelection(false);
            }
        });
    }

    private boolean hasMultiLineSelection() {
        int s = getSelectionStart();
        int e = getSelectionEnd();
        if (s == e) {
            return false;
        }
        Element root = doc.getDefaultRootElement();
        return root.getElementIndex(s) != root.getElementIndex(Math.max(s, e - 1));
    }

    private void indentSelection(boolean indent) {
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        Element root = doc.getDefaultRootElement();
        int firstLine = root.getElementIndex(selStart);
        int lastLine = root.getElementIndex(Math.max(selStart, selEnd - 1));

        try {
            // Swing の Element はミューテーション毎にオフセットを再計算してくれるので、
            // 各反復で root.getElement(i).getStartOffset() を素直に参照する。
            int totalDelta = 0;
            int firstLineDelta = 0;
            for (int i = firstLine; i <= lastLine; i++) {
                int lineStart = root.getElement(i).getStartOffset();
                if (indent) {
                    doc.insertString(lineStart, "\t", null);
                    totalDelta += 1;
                    if (i == firstLine) {
                        firstLineDelta = 1;
                    }
                } else {
                    int lineEnd = root.getElement(i).getEndOffset();
                    if (lineEnd > lineStart && doc.getText(lineStart, 1).equals("\t")) {
                        doc.remove(lineStart, 1);
                        totalDelta -= 1;
                        if (i == firstLine) {
                            firstLineDelta = -1;
                        }
                    }
                }
            }
            int newStart = Math.max(0, selStart + firstLineDelta);
            int newEnd = Math.max(newStart, selEnd + totalDelta);
            setSelectionStart(newStart);
            setSelectionEnd(newEnd);
        } catch (BadLocationException ex) {
            // ignore
        }
    }

    // --- 検索 ---

    public void showFindDialog() {
        String initial = lastSearchText != null ? lastSearchText : "";
        String input = JOptionPane.showInputDialog(this,
                Messages.get("dialog.find.prompt"),
                initial);
        if (input != null && !input.isEmpty()) {
            lastSearchText = input;
            findNext();
        }
    }

    public void findNext() {
        if (lastSearchText == null || lastSearchText.isEmpty()) {
            showFindDialog();
            return;
        }
        try {
            String text = doc.getText(0, doc.getLength());
            int from = getSelectionEnd();
            int idx = indexOfIgnoreCase(text, lastSearchText, from);
            if (idx < 0) {
                // wrap around
                idx = indexOfIgnoreCase(text, lastSearchText, 0);
            }
            if (idx >= 0) {
                requestFocusInWindow();
                setCaretPosition(idx);
                moveCaretPosition(idx + lastSearchText.length());
            } else {
                JOptionPane.showMessageDialog(this,
                        Messages.get("dialog.find.notFound") + ": " + lastSearchText,
                        Messages.get("dialog.find.title"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (BadLocationException ex) {
            // ignore
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int max = haystack.length() - needle.length();
        for (int i = from; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    // --- オートコンプリート ---

    private void setupAutocomplete() {
        autocompleteList = new JList<>();
        autocompleteList.setFont(getFont());
        autocompletePopup = new JPopupMenu();
        autocompletePopup.setBorder(new LineBorder(Color.GRAY));
        autocompletePopup.add(new JScrollPane(autocompleteList));

        autocompleteList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    applyAutocomplete();
                }
            }
        });

        // Ctrl/Cmd+Space で明示的に補完候補を呼び出す
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, MENU_MASK), "manualAutocomplete");
        am.put("manualAutocomplete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerAutocomplete();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (autocompletePopup.isVisible()) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_DOWN:
                            moveAutocompleteSelection(1);
                            e.consume();
                            break;
                        case KeyEvent.VK_UP:
                            moveAutocompleteSelection(-1);
                            e.consume();
                            break;
                        case KeyEvent.VK_ENTER:
                        case KeyEvent.VK_TAB:
                            applyAutocomplete();
                            e.consume();
                            break;
                        case KeyEvent.VK_ESCAPE:
                            hideAutocomplete();
                            e.consume();
                            break;
                        default:
                            break;
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP
                        || e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB
                        || e.getKeyCode() == KeyEvent.VK_ESCAPE
                        || e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL
                        || e.getKeyCode() == KeyEvent.VK_ALT) {
                    return;
                }
                SwingUtilities.invokeLater(() -> updateAutocomplete());
            }
        });
    }

    private void updateAutocomplete() {
        try {
            int caretPos = getCaretPosition();
            Element root = doc.getDefaultRootElement();
            int lineIdx = root.getElementIndex(caretPos);
            int lineStart = root.getElement(lineIdx).getStartOffset();
            String lineText = doc.getText(lineStart, caretPos - lineStart);

            // 行頭の空白を除去した位置から「:」で始まるプレフィックスを探す
            String trimmed = lineText.stripLeading();
            if (trimmed.startsWith(":") && !trimmed.contains(" ") && !trimmed.contains("\t")) {
                String prefix = trimmed;
                autocompleteStart = caretPos - prefix.length();

                List<String> matches = new ArrayList<>();
                for (String cmd : COMMANDS) {
                    if (cmd.startsWith(prefix) && !cmd.equals(prefix)) {
                        matches.add(cmd);
                    }
                }

                if (!matches.isEmpty()) {
                    showAutocomplete(matches);
                } else {
                    hideAutocomplete();
                }
            } else {
                hideAutocomplete();
            }
        } catch (BadLocationException e) {
            hideAutocomplete();
        }
    }

    /**
     * 明示的な補完呼び出し (Ctrl/Cmd+Space)。プレフィックスがなければ全コマンドを表示する。
     */
    private void triggerAutocomplete() {
        try {
            int caretPos = getCaretPosition();
            Element root = doc.getDefaultRootElement();
            int lineIdx = root.getElementIndex(caretPos);
            int lineStart = root.getElement(lineIdx).getStartOffset();
            String lineText = doc.getText(lineStart, caretPos - lineStart);
            String trimmed = lineText.stripLeading();

            String prefix;
            if (trimmed.startsWith(":") && !trimmed.contains(" ") && !trimmed.contains("\t")) {
                prefix = trimmed;
                autocompleteStart = caretPos - prefix.length();
            } else {
                prefix = "";
                autocompleteStart = caretPos;
            }

            List<String> matches = new ArrayList<>();
            for (String cmd : COMMANDS) {
                if (prefix.isEmpty() || (cmd.startsWith(prefix) && !cmd.equals(prefix))) {
                    matches.add(cmd);
                }
            }
            if (!matches.isEmpty()) {
                showAutocomplete(matches);
            }
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private void showAutocomplete(List<String> items) {
        autocompleteList.setListData(items.toArray(new String[0]));
        autocompleteList.setSelectedIndex(0);
        autocompleteList.setVisibleRowCount(Math.min(items.size(), 9));

        try {
            Rectangle2D r = modelToView2D(getCaretPosition());
            if (r != null) {
                autocompletePopup.setPopupSize(150, Math.min(items.size(), 9) * 20 + 4);
                autocompletePopup.show(this, (int) r.getX(), (int) (r.getY() + r.getHeight()));
            }
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private void hideAutocomplete() {
        autocompletePopup.setVisible(false);
        autocompleteStart = -1;
    }

    private void moveAutocompleteSelection(int delta) {
        int idx = autocompleteList.getSelectedIndex() + delta;
        int size = autocompleteList.getModel().getSize();
        if (idx >= 0 && idx < size) {
            autocompleteList.setSelectedIndex(idx);
            autocompleteList.ensureIndexIsVisible(idx);
        }
    }

    private void applyAutocomplete() {
        String selected = autocompleteList.getSelectedValue();
        if (selected != null && autocompleteStart >= 0) {
            try {
                int caretPos = getCaretPosition();
                doc.remove(autocompleteStart, caretPos - autocompleteStart);
                doc.insertString(autocompleteStart, selected + " ", null);
            } catch (BadLocationException e) {
                // ignore
            }
        }
        hideAutocomplete();
    }
}