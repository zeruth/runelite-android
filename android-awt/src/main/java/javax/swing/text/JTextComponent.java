package javax.swing.text;

import javax.swing.JComponent;
import javax.swing.event.CaretListener;

public abstract class JTextComponent extends JComponent {
    private static final long serialVersionUID = -2911697971489974478L;

    public static final String DEFAULT_KEYMAP = "default";
    public static final String FOCUS_ACCELERATOR_KEY = "focusAcceleratorKey";

    protected Document doc;
    protected String text = "";
    private final javax.swing.event.DocumentListener documentListener = new DocumentRepaintListener();
    private final class DocumentRepaintListener implements javax.swing.event.DocumentListener, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public void insertUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
    }

    public JTextComponent() {
        this.doc = new PlainDocument();
        this.doc.addDocumentListener(documentListener);
        setCaret(new DefaultCaret());
        setHighlighter(new DefaultHighlighter());
    }

    public String getText() {
        if (doc != null) {
            try { return doc.getText(0, doc.getLength()); } catch (BadLocationException ignored) {}
        }
        return text;
    }
    public void setText(String t) {
        // Text is an input to every text component's preferred size.
        invalidate();
        this.text = t == null ? "" : t;
        if (doc != null) {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, this.text, null);
            } catch (BadLocationException ignored) {}
        }
    }

    public Document getDocument() { return doc; }
    public void setDocument(Document d) {
        if(d==null)throw new IllegalArgumentException("document");Document old=doc;
        if(old!=null)old.removeDocumentListener(documentListener);
        if(caret!=null&&old!=null)caret.deinstall(this);doc=d;doc.addDocumentListener(documentListener);if(caret!=null)caret.install(this);
        if(highlighter!=null)highlighter.install(this);firePropertyChange("document",old,d);revalidate();repaint();
    }
    public String getText(int offset,int length) throws BadLocationException { return doc.getText(offset,length); }

    /** Shared plain-text measurement for multiline Swing controls. */
    protected java.util.List<String> textLines(int width, boolean wrap, boolean words, int tabs) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.util.List<String> lines = new java.util.ArrayList<>();
        String value = getText().replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : value.split("\n", -1)) {
            StringBuilder expanded = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char ch = paragraph.charAt(i);
                if (ch == '\t') { int count = Math.max(1, tabs) - expanded.length() % Math.max(1, tabs); while (count-- > 0) expanded.append(' '); }
                else expanded.append(ch);
            }
            String remaining = expanded.toString();
            if (!wrap || width <= 0 || remaining.isEmpty()) { lines.add(remaining); continue; }
            while (!remaining.isEmpty()) {
                int end = 0, lastSpace = -1;
                while (end < remaining.length()) {
                    int next = end + Character.charCount(remaining.codePointAt(end));
                    if (end > 0 && fm.stringWidth(remaining.substring(0, next)) > width) break;
                    if (Character.isWhitespace(remaining.charAt(end))) lastSpace = next;
                    end = next;
                }
                if (words && end < remaining.length() && lastSpace > 0) end = lastSpace;
                lines.add(remaining.substring(0, end));
                remaining = remaining.substring(end);
            }
        }
        return lines;
    }
    protected int textWidth() {
        java.awt.Insets in = getInsets();
        return Math.max(0, getWidth() - in.left - in.right);
    }
    protected java.awt.Dimension plainPreferredSize(boolean wrap, boolean words, int tabs) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        java.util.List<String> lines = textLines(textWidth(), wrap, words, tabs);
        int width = 0;
        for (String line : lines) width = Math.max(width, fm.stringWidth(line));
        return new java.awt.Dimension(width + in.left + in.right, lines.size() * fm.getHeight() + in.top + in.bottom);
    }
    protected void paintPlainText(java.awt.Graphics g, boolean wrap, boolean words, int tabs) {
        java.awt.Graphics copy = g.create();
        try {
            java.awt.Insets in = getInsets();
            copy.clipRect(in.left, in.top, textWidth(), Math.max(0, getHeight() - in.top - in.bottom));
            if(highlighter!=null)highlighter.paint(copy);
            if(caret!=null&&caret.isSelectionVisible()&&getSelectionStart()!=getSelectionEnd())new DefaultHighlighter.DefaultHighlightPainter(getSelectionColor()).paint(copy,getSelectionStart(),getSelectionEnd(),new java.awt.Rectangle(0,0,getWidth(),getHeight()),this);
            copy.setFont(getFont()); copy.setColor(isEnabled()?getForeground():getDisabledTextColor());
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int y = in.top + fm.getAscent();
            for (String line : textLines(textWidth(), wrap, words, tabs)) {
                copy.drawString(line, in.left, y); y += fm.getHeight();
            }
            if(caret!=null)caret.paint(copy);
        } finally { copy.dispose(); }
    }

    private boolean editable = true;
    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; }

    private Caret caret;
    private Highlighter highlighter;
    private java.awt.Color selectedTextColor=java.awt.Color.WHITE,selectionColor=java.awt.Color.BLUE,disabledTextColor=java.awt.Color.GRAY,caretColor=java.awt.Color.BLACK;
    private final javax.swing.event.EventListenerList caretListeners=new javax.swing.event.EventListenerList();
    private final javax.swing.event.ChangeListener caretChanged=e -> {
        javax.swing.event.CaretEvent event=new javax.swing.event.CaretEvent(this){public int getDot(){return caret.getDot();}public int getMark(){return caret.getMark();}};
        for(CaretListener listener:caretListeners.getListeners(CaretListener.class))listener.caretUpdate(event);
        repaint();
    };
    public void selectAll(){select(0,doc.getLength());}
    public void select(int start,int end){start=Math.max(0,Math.min(start,doc.getLength()));end=Math.max(start,Math.min(end,doc.getLength()));caret.setDot(start);caret.moveDot(end);}
    public String getSelectedText(){int start=getSelectionStart(),end=getSelectionEnd();if(start==end)return null;try{return doc.getText(start,end-start);}catch(BadLocationException e){throw new IllegalStateException(e);}}
    public int getSelectionStart(){return Math.min(caret.getDot(),caret.getMark());}public int getSelectionEnd(){return Math.max(caret.getDot(),caret.getMark());}
    public void setSelectionStart(int value){select(value,Math.max(value,getSelectionEnd()));}public void setSelectionEnd(int value){select(Math.min(getSelectionStart(),value),value);}
    public void setCaretPosition(int position){if(position<0||position>doc.getLength())throw new IllegalArgumentException("caret position");caret.setDot(position);}
    public void moveCaretPosition(int position){if(position<0||position>doc.getLength())throw new IllegalArgumentException("caret position");caret.moveDot(position);}
    public int getCaretPosition(){return caret.getDot();}
    public void addCaretListener(CaretListener listener){caretListeners.add(CaretListener.class,listener);}public void removeCaretListener(CaretListener listener){caretListeners.remove(CaretListener.class,listener);}
    public CaretListener[] getCaretListeners(){return caretListeners.getListeners(CaretListener.class);}
    public Caret getCaret(){return caret;}public Highlighter getHighlighter(){return highlighter;}
    public void setHighlighter(Highlighter value){Highlighter old=highlighter;if(old!=null)old.deinstall(this);highlighter=value;if(value!=null)value.install(this);firePropertyChange("highlighter",old,value);repaint();}
    public void setCaret(Caret value){if(value==null)throw new IllegalArgumentException("caret");Caret old=caret;if(old!=null){old.removeChangeListener(caretChanged);old.deinstall(this);}caret=value;value.install(this);value.addChangeListener(caretChanged);firePropertyChange("caret",old,value);repaint();}
    public void setSelectedTextColor(java.awt.Color value){java.awt.Color old=selectedTextColor;selectedTextColor=value;firePropertyChange("selectedTextColor",old,value);repaint();}public java.awt.Color getSelectedTextColor(){return selectedTextColor;}
    public void setSelectionColor(java.awt.Color value){java.awt.Color old=selectionColor;selectionColor=value;firePropertyChange("selectionColor",old,value);repaint();}public java.awt.Color getSelectionColor(){return selectionColor;}
    public void setDisabledTextColor(java.awt.Color value){java.awt.Color old=disabledTextColor;disabledTextColor=value;firePropertyChange("disabledTextColor",old,value);repaint();}public java.awt.Color getDisabledTextColor(){return disabledTextColor;}
    public void setCaretColor(java.awt.Color value){java.awt.Color old=caretColor;caretColor=value;firePropertyChange("caretColor",old,value);repaint();}public java.awt.Color getCaretColor(){return caretColor;}
    public void replaceSelection(String value){if(!isEditable())return;int start=getSelectionStart(),length=getSelectionEnd()-start;try{if(doc instanceof AbstractDocument)((AbstractDocument)doc).replace(start,length,value,null);else{doc.remove(start,length);doc.insertString(start,value,null);}setCaretPosition(Math.min(doc.getLength(),start+(value==null?0:value.length())));}catch(BadLocationException e){throw new IllegalArgumentException(e);}}
    public void copy(){String value=getSelectedText();if(value!=null)getToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(value),null);}
    public void cut(){if(isEditable()){copy();replaceSelection("");}}
    public void paste(){if(!isEditable())return;try{java.awt.datatransfer.Transferable value=getToolkit().getSystemClipboard().getContents(this);if(value!=null&&value.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))replaceSelection((String)value.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));}catch(java.awt.datatransfer.UnsupportedFlavorException|java.io.IOException ignored){}}
    public java.awt.Rectangle modelToView(int offset)throws BadLocationException{if(offset<0||offset>doc.getLength())throw new BadLocationException("offset",offset);String before=doc.getText(0,offset);int newline=before.lastIndexOf('\n');int row=0;for(int i=0;i<before.length();i++)if(before.charAt(i)=='\n')row++;java.awt.FontMetrics fm=getFontMetrics(getFont());java.awt.Insets in=getInsets();return new java.awt.Rectangle(in.left+fm.stringWidth(before.substring(newline+1)),in.top+row*fm.getHeight(),1,fm.getHeight());}
    public int viewToModel(java.awt.Point point){java.awt.FontMetrics fm=getFontMetrics(getFont());java.awt.Insets in=getInsets();int row=Math.max(0,(point.y-in.top)/Math.max(1,fm.getHeight()));String value=getText();int start=0;while(row-->0){int n=value.indexOf('\n',start);if(n<0)return value.length();start=n+1;}int end=value.indexOf('\n',start);if(end<0)end=value.length();for(int i=start;i<end;i++)if(fm.stringWidth(value.substring(start,i+1))>point.x-in.left)return i;return end;}
}
