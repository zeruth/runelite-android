package javax.swing.text;

import java.io.Serializable;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditListener;

public abstract class AbstractDocument implements Document, Serializable {
    private static final long serialVersionUID = -6841412929076416620L;

    private DocumentFilter documentFilter;
    public void setDocumentFilter(DocumentFilter filter) { documentFilter = filter; }
    public DocumentFilter getDocumentFilter() { return documentFilter; }
    public void replace(int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        remove(offset, length);
        insertString(offset, text, attrs);
    }

    public abstract static class AbstractElement implements Element {}
    public abstract static class BranchElement extends AbstractElement {}
    public abstract static class LeafElement extends AbstractElement {}
    public abstract static class ElementEdit {}

    public abstract static class Content {
        public abstract Position createPosition(int offset) throws BadLocationException;
        public abstract int length();
        public abstract String getString(int where, int len) throws BadLocationException;
        public abstract void getChars(int where, int len, Segment txt) throws BadLocationException;
    }

    @Override public int getLength() { return 0; }
    @Override public void addDocumentListener(DocumentListener l) {}
    @Override public void removeDocumentListener(DocumentListener l) {}
    @Override public void addUndoableEditListener(UndoableEditListener l) {}
    @Override public void removeUndoableEditListener(UndoableEditListener l) {}
    @Override public Object getProperty(Object key) { return null; }
    @Override public void putProperty(Object key, Object value) {}
    @Override public void remove(int offs, int len) throws BadLocationException {}
    @Override public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {}
    @Override public String getText(int offset, int length) throws BadLocationException { return ""; }
    @Override public void getText(int offset, int length, Segment txt) throws BadLocationException {}
    @Override public Position getStartPosition() { return () -> 0; }
    @Override public Position getEndPosition() { return () -> 0; }
    @Override public Position createPosition(int offs) throws BadLocationException { return () -> offs; }
    @Override public Element[] getRootElements() { return new Element[0]; }
    @Override public Element getDefaultRootElement() { return null; }
    @Override public void render(Runnable r) { r.run(); }
}
