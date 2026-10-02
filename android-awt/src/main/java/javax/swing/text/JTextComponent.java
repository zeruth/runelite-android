package javax.swing.text;

import javax.swing.JComponent;
import javax.swing.event.CaretListener;

public abstract class JTextComponent extends JComponent {
    private static final long serialVersionUID = -2911697971489974478L;

    public static final String DEFAULT_KEYMAP = "default";
    public static final String FOCUS_ACCELERATOR_KEY = "focusAcceleratorKey";

    protected Document doc;
    protected String text = "";

    public JTextComponent() {
        this.doc = new PlainDocument();
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
    public void setDocument(Document d) { this.doc = d; }

    private boolean editable = true;
    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; }

    public void selectAll() {}
    public String getSelectedText() { return null; }
    public int getSelectionStart() { return 0; }
    public int getSelectionEnd() { return 0; }

    public void setCaretPosition(int position) {}
    public int getCaretPosition() { return 0; }

    public void addCaretListener(CaretListener l) {}
    public void removeCaretListener(CaretListener l) {}

    public Caret getCaret() { return null; }
    public Highlighter getHighlighter() { return null; }
    public void setHighlighter(Highlighter h) {}
    public void setCaret(Caret c) {}

    public void setSelectedTextColor(java.awt.Color c) {}
    public java.awt.Color getSelectedTextColor() { return java.awt.Color.WHITE; }
    public void setSelectionColor(java.awt.Color c) {}
    public java.awt.Color getSelectionColor() { return java.awt.Color.BLUE; }
    public void setDisabledTextColor(java.awt.Color c) {}
    public java.awt.Color getDisabledTextColor() { return java.awt.Color.GRAY; }
    public void setCaretColor(java.awt.Color c) {}
    public java.awt.Color getCaretColor() { return java.awt.Color.BLACK; }

    public void copy() {}
    public void cut() {}
    public void paste() {}
}
