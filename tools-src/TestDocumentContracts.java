// host-contract
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 * The javax.swing.text document model against OpenJDK: the element tree a PlainDocument and a
 * DefaultStyledDocument build under insertString/remove/setCharacterAttributes/
 * setParagraphAttributes, the ElementChange each DocumentEvent reports, how Position marks move,
 * and StyleContext's shared small attribute sets. Views map offsets through exactly these trees,
 * so a wrong split here is a misplaced caret or line on the device.
 */
public final class TestDocumentContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static void same(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        total++;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : " expected <" + expected + "> got <" + actual + ">"));
    }

    public static void main(String[] args) throws Exception {
        plainDocument();
        documentFilter();
        styledDocument();
        styledUndo();
        styleContext();
        System.out.println("SUMMARY " + total + " checks, " + failures + " failures");
        if (failures != 0) throw new AssertionError(failures + " document contract checks failed");
    }

    /** Element offsets of a branch as "[s,e][s,e]". */
    private static String spans(Element branch) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < branch.getElementCount(); i++) {
            Element e = branch.getElement(i);
            out.append('[').append(e.getStartOffset()).append(',').append(e.getEndOffset()).append(']');
        }
        return out.toString();
    }

    /** index/removed/added of an ElementChange, or "none". */
    private static String change(DocumentEvent event, Element element) {
        DocumentEvent.ElementChange c = event.getChange(element);
        if (c == null) return "none";
        return c.getIndex() + "/-" + c.getChildrenRemoved().length + "/+" + c.getChildrenAdded().length;
    }

    private static final class Recorder implements DocumentListener {
        final List<DocumentEvent> events = new ArrayList<>();
        public void insertUpdate(DocumentEvent e) { events.add(e); }
        public void removeUpdate(DocumentEvent e) { events.add(e); }
        public void changedUpdate(DocumentEvent e) { events.add(e); }
        DocumentEvent last() { return events.get(events.size() - 1); }
    }

    private static void plainDocument() throws BadLocationException {
        PlainDocument doc = new PlainDocument();
        Element root = doc.getDefaultRootElement();
        same("plain root is a paragraph", AbstractDocument.ParagraphElementName, root.getName());
        same("empty plain document has the implied newline line", "[0,1]", spans(root));
        same("plain line is content", AbstractDocument.ContentElementName, root.getElement(0).getName());
        Recorder rec = new Recorder();
        doc.addDocumentListener(rec);

        doc.insertString(0, "ab\ncd", null);
        same("insert with a newline splits the line", "[0,3][3,6]", spans(root));
        DocumentEvent e = rec.last();
        same("insert event type", DocumentEvent.EventType.INSERT, e.getType());
        same("insert event offset/length", "0/5", e.getOffset() + "/" + e.getLength());
        same("insert event replaces line 0 by two", "0/-1/+2", change(e, root));

        Position d = doc.createPosition(4);
        Position mid = doc.createPosition(1);
        doc.insertString(1, "Z", null);
        same("mark after the insert shifts", 5, d.getOffset());
        same("mark at the insert point moves forward", 2, mid.getOffset());
        same("insert without a newline keeps the lines", "[0,4][4,7]", spans(root));
        same("insert without a newline reports no line change", "none", change(rec.last(), root));
        same("start position stays at 0", 0, doc.getStartPosition().getOffset());
        same("end position is length + 1", doc.getLength() + 1, doc.getEndPosition().getOffset());

        doc.remove(2, 3);
        same("text after removing across the break", "aZd", doc.getText(0, doc.getLength()));
        same("remove across the break joins the lines", "[0,4]", spans(root));
        DocumentEvent r = rec.last();
        same("remove event type", DocumentEvent.EventType.REMOVE, r.getType());
        same("remove event replaces two lines by one", "0/-2/+1", change(r, root));
        same("mark after the removed range shifts back", 2, d.getOffset());
        same("mark inside the removed range collapses to its start", 2, mid.getOffset());
        check("remove past the end throws", throwsBadLocation(() -> doc.remove(2, 10)));
        check("insert past the end throws", throwsBadLocation(() -> doc.insertString(10, "x", null)));
    }

    private interface Edit { void run() throws BadLocationException; }

    private static boolean throwsBadLocation(Edit edit) {
        try {
            edit.run();
            return false;
        } catch (BadLocationException expected) {
            return true;
        }
    }

    private static void documentFilter() throws BadLocationException {
        PlainDocument doc = new PlainDocument();
        AtomicInteger calls = new AtomicInteger();
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                calls.incrementAndGet();
                fb.insertString(offset, text.toUpperCase(), attr);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                calls.incrementAndGet();
                fb.replace(offset, length, text == null ? null : text.toUpperCase(), attrs);
            }
        });
        doc.insertString(0, "abc", null);
        same("filter rewrites through the bypass", "ABC", doc.getText(0, doc.getLength()));
        same("bypass does not re-enter the filter", 1, calls.get());
        doc.replace(1, 1, "x", null);
        same("replace goes through the filter's replace", "AXC", doc.getText(0, doc.getLength()));
    }

    private static Element paragraph(DefaultStyledDocument doc, int index) {
        return doc.getDefaultRootElement().getElement(index);
    }

    private static void styledDocument() throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        Element root = doc.getDefaultRootElement();
        same("styled root is a section", AbstractDocument.SectionElementName, root.getName());
        same("styled root has one paragraph", 1, root.getElementCount());
        same("paragraph name", AbstractDocument.ParagraphElementName, paragraph(doc, 0).getName());
        same("empty paragraph holds the implied newline run", "[0,1]", spans(paragraph(doc, 0)));
        Style logical = doc.getLogicalStyle(0);
        same("paragraph resolves through the default style", StyleContext.DEFAULT_STYLE,
             logical == null ? null : logical.getName());
        Recorder rec = new Recorder();
        doc.addDocumentListener(rec);

        doc.insertString(0, "hello world", null);
        same("plain insert joins the existing run", "[0,12]", spans(paragraph(doc, 0)));
        same("joined insert reports no run change", "none", change(rec.last(), paragraph(doc, 0)));

        MutableAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        doc.setCharacterAttributes(6, 5, bold, false);
        same("character attributes split the run three ways", "[0,6][6,11][11,12]", spans(paragraph(doc, 0)));
        DocumentEvent c = rec.last();
        same("character change event type", DocumentEvent.EventType.CHANGE, c.getType());
        same("character change event offset/length", "6/5", c.getOffset() + "/" + c.getLength());
        same("character change replaces one run by three", "0/-1/+3", change(c, paragraph(doc, 0)));
        check("bold run is bold", StyleConstants.isBold(doc.getCharacterElement(7).getAttributes()));
        check("run before is not bold", !StyleConstants.isBold(doc.getCharacterElement(2).getAttributes()));
        check("run after is not bold", !StyleConstants.isBold(doc.getCharacterElement(11).getAttributes()));
        check("character run resolves through its paragraph",
              doc.getCharacterElement(7).getAttributes().getResolveParent() == paragraph(doc, 0).getAttributes());

        doc.insertString(5, "\n", null);
        same("newline splits the paragraph", 2, root.getElementCount());
        same("first paragraph runs", "[0,6]", spans(paragraph(doc, 0)));
        same("second paragraph runs", "[6,7][7,12][12,13]", spans(paragraph(doc, 1)));
        check("bold run moved with the text", StyleConstants.isBold(doc.getCharacterElement(8).getAttributes()));
        same("new paragraph keeps the logical style", StyleContext.DEFAULT_STYLE, doc.getLogicalStyle(8).getName());
        same("paragraph split adds the fractured paragraph after the first", "1/-0/+1", change(rec.last(), root));
        same("paragraph split trims the first paragraph", "0/-3/+1", change(rec.last(), paragraph(doc, 0)));

        MutableAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, 1, center, false);
        same("paragraph attribute lands on the first paragraph", StyleConstants.ALIGN_CENTER,
             StyleConstants.getAlignment(paragraph(doc, 0).getAttributes()));
        same("second paragraph keeps its alignment", StyleConstants.ALIGN_LEFT,
             StyleConstants.getAlignment(paragraph(doc, 1).getAttributes()));
        same("merge keeps the logical style", StyleContext.DEFAULT_STYLE, doc.getLogicalStyle(0).getName());
        same("paragraph change reports no structure change", "none", change(rec.last(), root));
        same("paragraph change event type", DocumentEvent.EventType.CHANGE, rec.last().getType());

        Position w = doc.createPosition(8);
        doc.remove(0, 6);
        same("text after removing the first paragraph", " world", doc.getText(0, doc.getLength()));
        same("removing a whole paragraph joins into one", 1, root.getElementCount());
        same("joined paragraph keeps the right runs", "[0,1][1,6][6,7]", spans(paragraph(doc, 0)));
        same("joined paragraph takes the left paragraph's attributes", StyleConstants.ALIGN_CENTER,
             StyleConstants.getAlignment(paragraph(doc, 0).getAttributes()));
        same("join reports two paragraphs replaced by one", "0/-2/+1", change(rec.last(), root));
        same("mark follows the removal", 2, w.getOffset());
        check("bold survives the join", StyleConstants.isBold(doc.getCharacterElement(2).getAttributes()));

        doc.setCharacterAttributes(0, doc.getLength(), new SimpleAttributeSet(), true);
        check("replace clears the bold run", !StyleConstants.isBold(doc.getCharacterElement(2).getAttributes()));
    }

    private static void styledUndo() throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "abcdef", null);
        List<UndoableEditEvent> edits = new ArrayList<>();
        doc.addUndoableEditListener(new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) { edits.add(e); }
        });
        MutableAttributeSet italic = new SimpleAttributeSet();
        StyleConstants.setItalic(italic, true);
        doc.setCharacterAttributes(2, 2, italic, false);
        same("attribute change posts one undoable edit", 1, edits.size());
        check("italic applied", StyleConstants.isItalic(doc.getCharacterElement(2).getAttributes()));
        edits.get(0).getEdit().undo();
        check("undo restores the run attributes", !StyleConstants.isItalic(doc.getCharacterElement(2).getAttributes()));
        same("undo restores the run structure", "[0,7]", spans(doc.getDefaultRootElement().getElement(0)));
        edits.get(0).getEdit().redo();
        check("redo reapplies", StyleConstants.isItalic(doc.getCharacterElement(2).getAttributes()));

        edits.clear();
        doc.insertString(3, "XY", null);
        same("insert posts one undoable edit", 1, edits.size());
        edits.get(0).getEdit().undo();
        same("undo of an insert restores the text", "abcdef", doc.getText(0, doc.getLength()));
    }

    private static void styleContext() {
        StyleContext sc = new StyleContext();
        AttributeSet a = sc.addAttribute(sc.getEmptySet(), StyleConstants.Bold, Boolean.TRUE);
        AttributeSet b = sc.addAttribute(sc.getEmptySet(), StyleConstants.Bold, Boolean.TRUE);
        check("small sets are shared", a == b);
        check("small sets are SmallAttributeSet", a instanceof StyleContext.SmallAttributeSet);
        check("small set copy is itself", a.copyAttributes() == a);
        AttributeSet big = sc.getEmptySet();
        for (int i = 0; i < 10; i++) big = sc.addAttribute(big, "key" + i, i);
        check("sets past the threshold are mutable", big instanceof MutableAttributeSet);
        same("removing back under the threshold compresses", true,
             sc.removeAttribute(big, "key0") instanceof StyleContext.SmallAttributeSet);

        Style style = sc.addStyle("custom", null);
        same("style name", "custom", style.getName());
        same("a fresh named style holds only its name", 1, style.getAttributeCount());
        AtomicInteger changes = new AtomicInteger();
        style.addChangeListener(e -> changes.incrementAndGet());
        style.addAttribute(StyleConstants.Italic, Boolean.TRUE);
        same("named style fires on change", 1, changes.get());
        check("style is registered", sc.getStyle("custom") == style);
        Style child = sc.addStyle("child", style);
        check("child resolves through parent", StyleConstants.isItalic(child));
        check("child's resolve parent is the parent", child.getResolveParent() == style);
        sc.removeStyle("custom");
        check("removed style is gone", sc.getStyle("custom") == null);
        check("default style exists", sc.getStyle(StyleContext.DEFAULT_STYLE) != null);

        SimpleAttributeSet set = new SimpleAttributeSet();
        set.setResolveParent(style);
        same("resolve parent counts as an attribute", 1, set.getAttributeCount());
        check("resolve parent is stored under ResolveAttribute", set.isDefined(StyleConstants.ResolveAttribute));
        check("empty set equals an empty mutable set", SimpleAttributeSet.EMPTY.equals(new SimpleAttributeSet()));
    }
}
