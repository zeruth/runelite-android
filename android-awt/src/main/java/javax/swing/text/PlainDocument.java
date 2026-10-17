package javax.swing.text;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditListener;

/**
 * Minimal PlainDocument shadow. Real Swing's PlainDocument backs JTextField/JTextArea —
 * RuneLite's search bar listens on getDocument() for typing. We store a single string,
 * fire change events to listeners, and return early for the (rare) ranged operations.
 */
public class PlainDocument extends AbstractDocument {
    private final StringBuilder text = new StringBuilder();
    private final List<DocumentListener> listeners = new ArrayList<>();
    private final Map<Object, Object> properties = new HashMap<>();

    @Override public int getLength() { return text.length(); }
    @Override public void addDocumentListener(DocumentListener l) { if (l != null) listeners.add(l); }
    @Override public void removeDocumentListener(DocumentListener l) { listeners.remove(l); }
    @Override public void addUndoableEditListener(UndoableEditListener l) {}
    @Override public void removeUndoableEditListener(UndoableEditListener l) {}
    @Override public Object getProperty(Object key) { return properties.get(key); }
    @Override public void putProperty(Object key, Object value) {
        if (value == null) properties.remove(key); else properties.put(key, value);
    }
    private final DocumentFilter.FilterBypass bypass = new DocumentFilter.FilterBypass() {
        public Document getDocument() { return PlainDocument.this; }
        public void remove(int offset, int length) throws BadLocationException { removeRaw(offset, length); }
        public void insertString(int offset, String value, AttributeSet attrs) throws BadLocationException { insertRaw(offset, value, attrs); }
        public void replace(int offset, int length, String value, AttributeSet attrs) throws BadLocationException {
            removeRaw(offset, length);
            insertRaw(offset, value, attrs);
        }
    };
    @Override public void remove(int offs, int len) throws BadLocationException {
        if (getDocumentFilter() != null) getDocumentFilter().remove(bypass, offs, len);
        else removeRaw(offs, len);
    }
    @Override public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
        if (getDocumentFilter() != null) getDocumentFilter().insertString(bypass, offset, str, a);
        else insertRaw(offset, str, a);
    }
    @Override public void replace(int offset, int length, String value, AttributeSet attrs) throws BadLocationException {
        if (getDocumentFilter() != null) getDocumentFilter().replace(bypass, offset, length, value, attrs);
        else bypass.replace(offset, length, value, attrs);
    }
    private void removeRaw(int offs, int len) throws BadLocationException {
        if (offs < 0 || offs + len > text.length()) throw new BadLocationException("remove", offs);
        text.delete(offs, offs + len);
        fire(DocumentEvent.EventType.REMOVE, offs, len);
    }
    private void insertRaw(int offset, String str, AttributeSet a) throws BadLocationException {
        if (offset < 0 || offset > text.length()) throw new BadLocationException("insert", offset);
        if (str != null) text.insert(offset, str);
        fire(DocumentEvent.EventType.INSERT, offset, str == null ? 0 : str.length());
    }
    @Override public String getText(int offset, int length) throws BadLocationException {
        if (offset < 0 || offset + length > text.length()) throw new BadLocationException("getText", offset);
        return text.substring(offset, offset + length);
    }
    @Override public void getText(int offset, int length, Segment txt) throws BadLocationException {
        String s = getText(offset, length);
        if (txt != null) { txt.array = s.toCharArray(); txt.offset = 0; txt.count = s.length(); }
    }
    @Override public Position getStartPosition() { return () -> 0; }
    @Override public Position getEndPosition()   { return text::length; }
    @Override public Position createPosition(int offs) { return () -> offs; }
    @Override public Element[] getRootElements()  { return new Element[0]; }
    @Override public Element getDefaultRootElement() { return null; }
    @Override public void render(Runnable r) { if (r != null) r.run(); }

    private void fire(DocumentEvent.EventType type, int offset, int length) {
        DocumentEvent ev = new DocumentEvent() {
            @Override public int getOffset() { return offset; }
            @Override public int getLength() { return length; }
            @Override public Document getDocument() { return PlainDocument.this; }
            @Override public EventType getType() { return type; }
            @Override public ElementChange getChange(Element elem) { return null; }
        };
        for (DocumentListener l : new ArrayList<>(listeners)) {
            try {
                if (type == DocumentEvent.EventType.INSERT) l.insertUpdate(ev);
                else if (type == DocumentEvent.EventType.REMOVE) l.removeUpdate(ev);
                else l.changedUpdate(ev);
            } catch (Throwable ignored) {}
        }
    }
}
