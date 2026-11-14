/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing.text;

import java.awt.font.TextAttribute;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.tree.TreeNode;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

/**
 * An implementation of the document interface to serve as a
 * basis for implementing various kinds of documents.  At this
 * level there is very little policy, so there is a corresponding
 * increase in difficulty of use.
 * <p>
 * This class implements a locking mechanism for the document.  It
 * allows multiple readers or one writer, and writers must wait until
 * all observers of the document have been notified of a previous
 * change before beginning another mutation to the document.  The
 * read lock is acquired and released using the <code>render</code>
 * method.  A write lock is aquired by the methods that mutate the
 * document, and are held for the duration of the method call.
 * Notification is done on the thread that produced the mutation,
 * and the thread has full read access to the document for the
 * duration of the notification, but other readers are kept out
 * until the notification has finished.  The notification is a
 * beans event notification which does not allow any further
 * mutations until all listeners have been notified.
 * <p>
 * Shadow note: until {@code PlainDocument} is ported, a subclass that does not
 * declare {@code getDefaultRootElement} gets OpenJDK's {@code PlainDocument}
 * line map from this class (one {@code BranchElement} of line leaves, kept by
 * {@code insertUpdate}/{@code removeUpdate}), which is what OpenJDK's
 * {@code PlainDocument} builds; {@code DefaultStyledDocument} declares its own
 * root and gets the OpenJDK behaviour of this class only.
 *
 * @author  Timothy Prinzing
 */
@SuppressWarnings("serial")
public abstract class AbstractDocument implements Document, Serializable {

    /**
     * Constructs a new <code>AbstractDocument</code>, wrapped around some
     * specified content storage mechanism.
     *
     * @param data the content
     */
    protected AbstractDocument(Content data) {
        this(data, StyleContext.getDefaultStyleContext());
    }

    /**
     * Constructs a new <code>AbstractDocument</code>, wrapped around some
     * specified content storage mechanism.
     *
     * @param data the content
     * @param context the attribute context
     */
    protected AbstractDocument(Content data, AttributeContext context) {
        this.data = data;
        this.context = context;
        bidiRoot = new BidiRootElement();

        if (defaultI18NProperty == null) {
            String o = System.getProperty(I18NProperty);
            defaultI18NProperty = Boolean.valueOf(o);
        }
        putProperty(I18NProperty, defaultI18NProperty);

        //REMIND(bcb) This creates an initial bidi element to account for
        //the \n that exists by default in the content.  Doing it this way
        //seems to expose a little too much knowledge of the content given
        //to us by the sub-class.  Consider having the sub-class' constructor
        //make an initial call to insertUpdate.
        writeLock();
        try {
            Element[] p = new Element[1];
            p[0] = new BidiElement(bidiRoot, 0, 1, 0);
            bidiRoot.replace(0, 0, p);
        } finally {
            writeUnlock();
        }

        boolean ownRoot;
        try {
            ownRoot = getClass().getMethod("getDefaultRootElement").getDeclaringClass() != AbstractDocument.class;
        } catch (NoSuchMethodException impossible) {
            ownRoot = true;
        }
        if (!ownRoot) {
            lineRoot = createLineRoot();
        }
    }

    /**
     * Shadow seam for the not yet ported {@code PlainDocument} and
     * {@code HTMLDocument}, which construct without content; OpenJDK's
     * {@code PlainDocument()} uses a {@code GapContent} the same way.
     */
    protected AbstractDocument() {
        this(new GapContent());
    }

    /**
     * Supports managing a set of properties. Callers
     * can use the <code>documentProperties</code> dictionary
     * to annotate the document with document-wide properties.
     *
     * @return a non-<code>null</code> <code>Dictionary</code>
     */
    public Dictionary<Object, Object> getDocumentProperties() {
        if (documentProperties == null) {
            documentProperties = new Hashtable<Object, Object>(2);
        }
        return documentProperties;
    }

    /**
     * Replaces the document properties dictionary for this document.
     *
     * @param x the new dictionary
     */
    public void setDocumentProperties(Dictionary<Object, Object> x) {
        documentProperties = x;
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.  The event instance
     * is lazily created using the parameters passed into
     * the fire method.
     *
     * @param e the event
     */
    protected void fireInsertUpdate(DocumentEvent e) {
        notifyingListeners = true;
        try {
            // Guaranteed to return a non-null array
            Object[] listeners = listenerList.getListenerList();
            // Process the listeners last to first, notifying
            // those that are interested in this event
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == DocumentListener.class) {
                    ((DocumentListener) listeners[i + 1]).insertUpdate(e);
                }
            }
        } finally {
            notifyingListeners = false;
        }
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.
     *
     * @param e the event
     */
    protected void fireChangedUpdate(DocumentEvent e) {
        notifyingListeners = true;
        try {
            Object[] listeners = listenerList.getListenerList();
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == DocumentListener.class) {
                    ((DocumentListener) listeners[i + 1]).changedUpdate(e);
                }
            }
        } finally {
            notifyingListeners = false;
        }
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.
     *
     * @param e the event
     */
    protected void fireRemoveUpdate(DocumentEvent e) {
        notifyingListeners = true;
        try {
            Object[] listeners = listenerList.getListenerList();
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == DocumentListener.class) {
                    ((DocumentListener) listeners[i + 1]).removeUpdate(e);
                }
            }
        } finally {
            notifyingListeners = false;
        }
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.
     *
     * @param e the event
     */
    protected void fireUndoableEditUpdate(UndoableEditEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == UndoableEditListener.class) {
                ((UndoableEditListener) listeners[i + 1]).undoableEditHappened(e);
            }
        }
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this document.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as listeners
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    /**
     * Gets the asynchronous loading priority.  If less than zero,
     * the document should not be loaded asynchronously.
     *
     * @return the asynchronous loading priority, or <code>-1</code>
     *   if the document should not be loaded asynchronously
     */
    public int getAsynchronousLoadPriority() {
        Integer loadPriority = (Integer) getProperty(AbstractDocument.AsyncLoadPriority);
        if (loadPriority != null) {
            return loadPriority.intValue();
        }
        return -1;
    }

    /**
     * Sets the asynchronous loading priority.
     *
     * @param p the new asynchronous loading priority; a value
     *   less than zero indicates that the document should not be
     *   loaded asynchronously
     */
    public void setAsynchronousLoadPriority(int p) {
        Integer loadPriority = (p >= 0) ? Integer.valueOf(p) : null;
        putProperty(AbstractDocument.AsyncLoadPriority, loadPriority);
    }

    /**
     * Sets the <code>DocumentFilter</code>. The <code>DocumentFilter</code>
     * is passed <code>insert</code> and <code>remove</code> to conditionally
     * allow inserting/deleting of the text.  A <code>null</code> value
     * indicates that no filtering will occur.
     *
     * @param filter the <code>DocumentFilter</code> used to constrain text
     */
    public void setDocumentFilter(DocumentFilter filter) {
        documentFilter = filter;
    }

    /**
     * Returns the <code>DocumentFilter</code> that is responsible for
     * filtering of insertion/removal.
     *
     * @return the DocumentFilter
     */
    public DocumentFilter getDocumentFilter() {
        return documentFilter;
    }

    // --- Document methods -----------------------------------------

    /**
     * This allows the model to be safely rendered in the presence
     * of currency, if the model supports being updated asynchronously.
     * The given runnable will be executed in a way that allows it
     * to safely read the model with no changes while the runnable
     * is being executed.  The runnable itself may <em>not</em>
     * make any mutations.
     *
     * @param r the renderer to execute
     */
    public void render(Runnable r) {
        readLock();
        try {
            r.run();
        } finally {
            readUnlock();
        }
    }

    /**
     * Returns the length of the data.  This is the number of
     * characters of content that represents the users data.
     *
     * @return the length &gt;= 0
     */
    public int getLength() {
        return data.length() - 1;
    }

    /**
     * Adds a document listener for notification of any changes.
     *
     * @param listener the <code>DocumentListener</code> to add
     */
    public void addDocumentListener(DocumentListener listener) {
        listenerList.add(DocumentListener.class, listener);
    }

    /**
     * Removes a document listener.
     *
     * @param listener the <code>DocumentListener</code> to remove
     */
    public void removeDocumentListener(DocumentListener listener) {
        listenerList.remove(DocumentListener.class, listener);
    }

    /**
     * Returns an array of all the document listeners
     * registered on this document.
     *
     * @return all of this document's <code>DocumentListener</code>s
     *         or an empty array if no document listeners are
     *         currently registered
     */
    public DocumentListener[] getDocumentListeners() {
        return listenerList.getListeners(DocumentListener.class);
    }

    /**
     * Adds an undo listener for notification of any changes.
     * Undo/Redo operations performed on the <code>UndoableEdit</code>
     * will cause the appropriate DocumentEvent to be fired to keep
     * the view(s) in sync with the model.
     *
     * @param listener the <code>UndoableEditListener</code> to add
     */
    public void addUndoableEditListener(UndoableEditListener listener) {
        listenerList.add(UndoableEditListener.class, listener);
    }

    /**
     * Removes an undo listener.
     *
     * @param listener the <code>UndoableEditListener</code> to remove
     */
    public void removeUndoableEditListener(UndoableEditListener listener) {
        listenerList.remove(UndoableEditListener.class, listener);
    }

    /**
     * Returns an array of all the undoable edit listeners
     * registered on this document.
     *
     * @return all of this document's <code>UndoableEditListener</code>s
     *         or an empty array if no undoable edit listeners are
     *         currently registered
     */
    public UndoableEditListener[] getUndoableEditListeners() {
        return listenerList.getListeners(UndoableEditListener.class);
    }

    /**
     * A convenience method for looking up a property value. It is
     * equivalent to:
     * <pre>
     * getDocumentProperties().get(key);
     * </pre>
     *
     * @param key the non-<code>null</code> property key
     * @return the value of this property or <code>null</code>
     */
    public final Object getProperty(Object key) {
        return getDocumentProperties().get(key);
    }

    /**
     * A convenience method for storing up a property value.  It is
     * equivalent to:
     * <pre>
     * getDocumentProperties().put(key, value);
     * </pre>
     * If <code>value</code> is <code>null</code> this method will
     * remove the property.
     *
     * @param key the non-<code>null</code> key
     * @param value the property value
     */
    public final void putProperty(Object key, Object value) {
        if (value != null) {
            getDocumentProperties().put(key, value);
        } else {
            getDocumentProperties().remove(key);
        }
        if (key == TextAttribute.RUN_DIRECTION && Boolean.TRUE.equals(getProperty(I18NProperty))) {
            //REMIND - this needs to flip on the i18n property if run dir
            //is rtl and the i18n property is not already on.
            writeLock();
            try {
                DefaultDocumentEvent e = new DefaultDocumentEvent(0, getLength(), DocumentEvent.EventType.INSERT);
                updateBidi(e);
            } finally {
                writeUnlock();
            }
        }
    }

    /**
     * Removes some content from the document.
     * Removing content causes a write lock to be held while the
     * actual changes are taking place.  Observers are notified
     * of the change on the thread that called this method.
     *
     * @param offs the starting offset &gt;= 0
     * @param len the number of characters to remove &gt;= 0
     * @exception BadLocationException  the given remove position is not a valid
     *   position within the document
     */
    public void remove(int offs, int len) throws BadLocationException {
        DocumentFilter filter = getDocumentFilter();

        writeLock();
        try {
            if (filter != null) {
                filter.remove(getFilterBypass(), offs, len);
            } else {
                handleRemove(offs, len);
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Performs the actual work of the remove. It is assumed the caller
     * will have obtained a <code>writeLock</code> before invoking this.
     */
    void handleRemove(int offs, int len) throws BadLocationException {
        if (len > 0) {
            if (offs < 0 || (offs + len) > getLength()) {
                throw new BadLocationException("Invalid remove", getLength() + 1);
            }
            DefaultDocumentEvent chng = new DefaultDocumentEvent(offs, len, DocumentEvent.EventType.REMOVE);

            boolean isComposedTextElement = isComposedTextElement(offs);

            removeUpdate(chng);
            UndoableEdit u = data.remove(offs, len);
            if (u != null) {
                chng.addEdit(u);
            }
            postRemoveUpdate(chng);
            // Mark the edit as done.
            chng.end();
            fireRemoveUpdate(chng);
            // only fire undo if Content implementation supports it
            // undo for the composed text is not supported for now
            if ((u != null) && !isComposedTextElement) {
                fireUndoableEditUpdate(new UndoableEditEvent(this, chng));
            }
        }
    }

    /**
     * Deletes the region of text from <code>offset</code> to
     * <code>offset + length</code>, and replaces it with <code>text</code>.
     * It is up to the implementation as to how this is implemented, some
     * implementations may treat this as two distinct operations: a remove
     * followed by an insert, others may treat the replace as one atomic
     * operation.
     *
     * @param offset index of child element
     * @param length length of text to delete, may be 0 indicating don't
     *               delete anything
     * @param text text to insert, <code>null</code> indicates no text to insert
     * @param attrs AttributeSet indicating attributes of inserted text,
     *              <code>null</code>
     *              is legal, and typically treated as an empty attributeset,
     *              but exact interpretation is left to the subclass
     * @exception BadLocationException the given position is not a valid
     *            position within the document
     * @since 1.4
     */
    public void replace(int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        if (length == 0 && (text == null || text.length() == 0)) {
            return;
        }
        DocumentFilter filter = getDocumentFilter();

        writeLock();
        try {
            if (filter != null) {
                filter.replace(getFilterBypass(), offset, length, text, attrs);
            } else {
                if (length > 0) {
                    remove(offset, length);
                }
                if (text != null && text.length() > 0) {
                    insertString(offset, text, attrs);
                }
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Inserts some content into the document.
     * Inserting content causes a write lock to be held while the
     * actual changes are taking place, followed by notification
     * to the observers on the thread that grabbed the write lock.
     *
     * @param offs the starting offset &gt;= 0
     * @param str the string to insert; does nothing with null/empty strings
     * @param a the attributes for the inserted content
     * @exception BadLocationException  the given insert position is not a valid
     *   position within the document
     */
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if ((str == null) || (str.length() == 0)) {
            return;
        }
        if (offs > getLength()) {
            throw new BadLocationException("Invalid insert", getLength());
        }
        if (lineRoot != null) {
            // OpenJDK PlainDocument.insertString: JTextField sets this so a paste stays on one line.
            Object filterNewlines = getProperty("filterNewlines");
            if ((filterNewlines instanceof Boolean) && filterNewlines.equals(Boolean.TRUE)
                && str.indexOf('\n') >= 0) {
                str = str.replace('\n', ' ');
            }
        }
        DocumentFilter filter = getDocumentFilter();

        writeLock();
        try {
            if (filter != null) {
                filter.insertString(getFilterBypass(), offs, str, a);
            } else {
                handleInsertString(offs, str, a);
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Performs the actual work of inserting the text; it is assumed the
     * caller has obtained a write lock before invoking this.
     */
    private void handleInsertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if ((str == null) || (str.length() == 0)) {
            return;
        }
        UndoableEdit u = data.insertString(offs, str);
        DefaultDocumentEvent e = new DefaultDocumentEvent(offs, str.length(), DocumentEvent.EventType.INSERT);
        if (u != null) {
            e.addEdit(u);
        }

        // see if complex glyph layout support is needed
        if (getProperty(I18NProperty).equals(Boolean.FALSE)) {
            // if a default direction of right-to-left has been specified,
            // we want complex layout even if the text is all left to right.
            Object d = getProperty(TextAttribute.RUN_DIRECTION);
            if ((d != null) && (d.equals(TextAttribute.RUN_DIRECTION_RTL))) {
                putProperty(I18NProperty, Boolean.TRUE);
            } else {
                char[] chars = str.toCharArray();
                if (Bidi.requiresBidi(chars, 0, chars.length)) {
                    putProperty(I18NProperty, Boolean.TRUE);
                }
            }
        }

        insertUpdate(e, a);
        // Mark the edit as done.
        e.end();
        fireInsertUpdate(e);
        // only fire undo if Content implementation supports it
        // undo for the composed text is not supported for now
        if (u != null && (a == null || !a.isDefined(StyleConstants.ComposedTextAttribute))) {
            fireUndoableEditUpdate(new UndoableEditEvent(this, e));
        }
    }

    /**
     * Gets a sequence of text from the document.
     *
     * @param offset the starting offset &gt;= 0
     * @param length the number of characters to retrieve &gt;= 0
     * @return the text
     * @exception BadLocationException  the range given includes a position
     *   that is not a valid position within the document
     */
    public String getText(int offset, int length) throws BadLocationException {
        if (length < 0) {
            throw new BadLocationException("Length must be positive", length);
        }
        String str = data.getString(offset, length);
        return str;
    }

    /**
     * Fetches the text contained within the given portion
     * of the document.
     *
     * @param offset the starting offset &gt;= 0
     * @param length the number of characters to retrieve &gt;= 0
     * @param txt the Segment object to retrieve the text into
     * @exception BadLocationException  the range given includes a position
     *   that is not a valid position within the document
     */
    public void getText(int offset, int length, Segment txt) throws BadLocationException {
        if (length < 0) {
            throw new BadLocationException("Length must be positive", length);
        }
        data.getChars(offset, length, txt);
    }

    /**
     * Returns a position that will track change as the document
     * is altered.
     *
     * @param offs the position in the model &gt;= 0
     * @return the position
     * @exception BadLocationException  if the given position does not
     *   represent a valid location in the associated document
     */
    public synchronized Position createPosition(int offs) throws BadLocationException {
        return data.createPosition(offs);
    }

    /**
     * Returns a position that represents the start of the document.  The
     * position returned can be counted on to track change and stay
     * located at the beginning of the document.
     *
     * @return the position
     */
    public final Position getStartPosition() {
        Position p;
        try {
            p = createPosition(0);
        } catch (BadLocationException bl) {
            p = null;
        }
        return p;
    }

    /**
     * Returns a position that represents the end of the document.  The
     * position returned can be counted on to track change and stay
     * located at the end of the document.
     *
     * @return the position
     */
    public final Position getEndPosition() {
        Position p;
        try {
            p = createPosition(data.length());
        } catch (BadLocationException bl) {
            p = null;
        }
        return p;
    }

    /**
     * Gets all root elements defined.  Typically, there
     * will only be one so the default implementation
     * is to return the default root element.
     *
     * @return the root element
     */
    public Element[] getRootElements() {
        Element[] elems = new Element[2];
        elems[0] = getDefaultRootElement();
        elems[1] = getBidiRootElement();
        return elems;
    }

    /**
     * Returns the root element that views should be based upon
     * unless some other mechanism for assigning views to element
     * structures is provided.  Abstract in OpenJDK; see the class note for
     * the line map a subclass that does not declare it gets.
     *
     * @return the root element
     */
    public Element getDefaultRootElement() {
        return lineRoot;
    }

    // ---- local methods -----------------------------------------

    /**
     * Returns the <code>FilterBypass</code>. This will create one if one
     * does not yet exist.
     */
    private DocumentFilter.FilterBypass getFilterBypass() {
        if (filterBypass == null) {
            filterBypass = new DefaultFilterBypass();
        }
        return filterBypass;
    }

    /**
     * Returns the root element of the bidirectional structure for this
     * document.  Its children represent character runs with a given
     * Unicode bidi level.
     */
    public Element getBidiRootElement() {
        return bidiRoot;
    }

    /**
     * Returns true if the text in the range <code>p0</code> to
     * <code>p1</code> is left to right.
     */
    static boolean isLeftToRight(Document doc, int p0, int p1) {
        if (Boolean.TRUE.equals(doc.getProperty(I18NProperty))) {
            if (doc instanceof AbstractDocument) {
                AbstractDocument adoc = (AbstractDocument) doc;
                Element bidiRoot = adoc.getBidiRootElement();
                int index = bidiRoot.getElementIndex(p0);
                Element bidiElem = bidiRoot.getElement(index);
                if (bidiElem.getEndOffset() >= p1) {
                    AttributeSet bidiAttrs = bidiElem.getAttributes();
                    return ((StyleConstants.getBidiLevel(bidiAttrs) % 2) == 0);
                }
            }
        }
        return true;
    }

    /**
     * Get the paragraph element containing the given position.  Sub-classes
     * must define for themselves what exactly constitutes a paragraph.  They
     * should keep in mind however that a paragraph should at least be the
     * unit of text over which to run the Unicode bidirectional algorithm.
     * Abstract in OpenJDK; this body is OpenJDK {@code PlainDocument}'s.
     *
     * @param pos the starting offset &gt;= 0
     * @return the element
     */
    public Element getParagraphElement(int pos) {
        Element lineMap = getDefaultRootElement();
        return lineMap.getElement(lineMap.getElementIndex(pos));
    }

    /**
     * Fetches the context for managing attributes.  This
     * method effectively establishes the strategy used
     * for compressing AttributeSet information.
     *
     * @return the context
     */
    protected final AttributeContext getAttributeContext() {
        return context;
    }

    /**
     * Updates document structure as a result of text insertion.  This
     * will happen within a write lock.  If a subclass of
     * this class reimplements this method, it should delegate to the
     * superclass as well.
     *
     * @param chng a description of the change
     * @param attr the attributes for the change
     */
    protected void insertUpdate(DefaultDocumentEvent chng, AttributeSet attr) {
        if (lineRoot != null) {
            insertLines(chng);
        }
        if (getProperty(I18NProperty).equals(Boolean.TRUE)) {
            updateBidi(chng);
        }

        // Check if a multi byte is encountered in the inserted text.
        if (chng.type == DocumentEvent.EventType.INSERT && chng.getLength() > 0
            && !Boolean.TRUE.equals(getProperty(MultiByteProperty))) {
            Segment segment = new Segment();
            try {
                getText(chng.getOffset(), chng.getLength(), segment);
                for (int i = 0; i < segment.count; i++) {
                    if ((int) segment.array[segment.offset + i] > 255) {
                        putProperty(MultiByteProperty, Boolean.TRUE);
                        break;
                    }
                }
            } catch (BadLocationException ble) {
                // Should never happen
            }
        }
    }

    /**
     * Updates any document structure as a result of text removal.  This
     * method is called before the text is actually removed from the Content.
     * This will happen within a write lock. If a subclass
     * of this class reimplements this method, it should delegate to the
     * superclass as well.
     *
     * @param chng a description of the change
     */
    protected void removeUpdate(DefaultDocumentEvent chng) {
        if (lineRoot != null) {
            removeLines(chng);
        }
    }

    /**
     * Updates any document structure as a result of text removal.  This
     * method is called after the text has been removed from the Content.
     * This will happen within a write lock. If a subclass
     * of this class reimplements this method, it should delegate to the
     * superclass as well.
     *
     * @param chng a description of the change
     */
    protected void postRemoveUpdate(DefaultDocumentEvent chng) {
        if (getProperty(I18NProperty).equals(Boolean.TRUE)) {
            updateBidi(chng);
        }
    }

    /**
     * Update the bidi element structure as a result of the given change
     * to the document.  The given change will be updated to reflect the
     * changes made to the bidi structure.
     * <p>
     * OpenJDK re-runs the bidi algorithm over the paragraphs the change
     * touches and splices the runs into the neighbouring elements; this port
     * re-runs it over the whole document and replaces every bidi element.
     * The resulting runs are the same (adjacent equal levels merge); only the
     * reported {@code ElementEdit} covers all of the bidi root's children.
     */
    void updateBidi(DefaultDocumentEvent chng) {
        int end = data.length();
        List<int[]> runs = new ArrayList<int[]>();
        Segment seg = new Segment();
        int pos = 0;
        while (pos < end) {
            Element paragraph = getParagraphElement(pos);
            int p0 = Math.min(paragraph.getStartOffset(), pos);
            int p1 = Math.min(paragraph.getEndOffset(), end);
            if (p1 <= pos) {
                p1 = end;
            }
            char[] text;
            try {
                data.getChars(p0, p1 - p0, seg);
                text = new char[seg.count];
                System.arraycopy(seg.array, seg.offset, text, 0, seg.count);
            } catch (BadLocationException e) {
                throw new Error("Internal error: " + e.toString());
            }
            Bidi bidi = new Bidi(text, 0, null, 0, text.length, bidiDirection(paragraph));
            for (int r = 0; r < bidi.getRunCount(); r++) {
                int start = p0 + bidi.getRunStart(r);
                int limit = p0 + bidi.getRunLimit(r);
                int level = bidi.getRunLevel(r);
                int[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                if (last != null && last[2] == level) {
                    last[1] = limit;
                } else {
                    runs.add(new int[] {start, limit, level});
                }
            }
            pos = p1;
        }
        Element[] removed = new Element[bidiRoot.getElementCount()];
        for (int i = 0; i < removed.length; i++) {
            removed[i] = bidiRoot.getElement(i);
        }
        Element[] added = new Element[runs.size()];
        for (int i = 0; i < added.length; i++) {
            int[] run = runs.get(i);
            added[i] = new BidiElement(bidiRoot, run[0], run[1], run[2]);
        }
        bidiRoot.replace(0, removed.length, added);
        chng.addEdit(new ElementEdit(bidiRoot, 0, removed, added));
    }

    private int bidiDirection(Element paragraph) {
        Object d = paragraph.getAttributes().getAttribute(TextAttribute.RUN_DIRECTION);
        if (d == null) {
            d = getProperty(TextAttribute.RUN_DIRECTION);
        }
        if (TextAttribute.RUN_DIRECTION_RTL.equals(d)) {
            return Bidi.DIRECTION_RIGHT_TO_LEFT;
        }
        if (TextAttribute.RUN_DIRECTION_LTR.equals(d)) {
            return Bidi.DIRECTION_LEFT_TO_RIGHT;
        }
        return Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
    }

    private boolean isComposedTextElement(int offset) {
        Element elem = getDefaultRootElement();
        while (elem != null && !elem.isLeaf()) {
            elem = elem.getElement(elem.getElementIndex(offset));
        }
        return elem != null && elem.getAttributes().isDefined(StyleConstants.ComposedTextAttribute);
    }

    // --- OpenJDK PlainDocument line map (see the class note) ----------

    private AbstractElement createLineRoot() {
        BranchElement map = (BranchElement) createBranchElement(null, null);
        Element line = createLeafElement(map, null, 0, 1);
        Element[] lines = new Element[1];
        lines[0] = line;
        map.replace(0, 0, lines);
        return map;
    }

    private void insertLines(DefaultDocumentEvent chng) {
        Vector<Element> removed = new Vector<Element>();
        Vector<Element> added = new Vector<Element>();
        BranchElement lineMap = (BranchElement) getDefaultRootElement();
        int offset = chng.getOffset();
        int length = chng.getLength();
        if (offset > 0) {
            offset -= 1;
            length += 1;
        }
        int index = lineMap.getElementIndex(offset);
        Element rmCandidate = lineMap.getElement(index);
        int rmOffs0 = rmCandidate.getStartOffset();
        int rmOffs1 = rmCandidate.getEndOffset();
        int lastOffset = rmOffs0;
        try {
            Segment s = new Segment();
            getContent().getChars(offset, length, s);
            boolean hasBreaks = false;
            for (int i = 0; i < length; i++) {
                char c = s.array[s.offset + i];
                if (c == '\n') {
                    int breakOffset = offset + i + 1;
                    added.addElement(createLeafElement(lineMap, null, lastOffset, breakOffset));
                    lastOffset = breakOffset;
                    hasBreaks = true;
                }
            }
            if (hasBreaks) {
                removed.addElement(rmCandidate);
                if ((offset + length == rmOffs1) && (lastOffset != rmOffs1)
                    && ((index + 1) < lineMap.getElementCount())) {
                    Element e = lineMap.getElement(index + 1);
                    removed.addElement(e);
                    rmOffs1 = e.getEndOffset();
                }
                if (lastOffset < rmOffs1) {
                    added.addElement(createLeafElement(lineMap, null, lastOffset, rmOffs1));
                }

                Element[] aelems = new Element[added.size()];
                added.copyInto(aelems);
                Element[] relems = new Element[removed.size()];
                removed.copyInto(relems);
                ElementEdit ee = new ElementEdit(lineMap, index, relems, aelems);
                chng.addEdit(ee);
                lineMap.replace(index, relems.length, aelems);
            }
        } catch (BadLocationException e) {
            throw new Error("Internal error: " + e.toString());
        }
    }

    private void removeLines(DefaultDocumentEvent chng) {
        BranchElement map = (BranchElement) getDefaultRootElement();
        int offset = chng.getOffset();
        int length = chng.getLength();
        int line0 = map.getElementIndex(offset);
        int line1 = map.getElementIndex(offset + length);
        if (line0 != line1) {
            // a line was removed
            Element[] relem = new Element[line1 - line0 + 1];
            for (int i = line0; i <= line1; i++) {
                relem[i - line0] = map.getElement(i);
            }
            int p0 = map.getElement(line0).getStartOffset();
            int p1 = map.getElement(line1).getEndOffset();
            Element[] aelem = new Element[1];
            aelem[0] = createLeafElement(map, null, p0, p1);
            ElementEdit ee = new ElementEdit(map, line0, relem, aelem);
            chng.addEdit(ee);
            map.replace(line0, relem.length, aelem);
        }
    }

    /**
     * Gives a diagnostic dump.
     *
     * @param out the output stream
     */
    public void dump(PrintStream out) {
        Element root = getDefaultRootElement();
        if (root instanceof AbstractElement) {
            ((AbstractElement) root).dump(out, 0);
        }
        bidiRoot.dump(out, 0);
    }

    /**
     * Gets the content for the document.
     *
     * @return the content
     */
    protected final Content getContent() {
        return data;
    }

    /**
     * Creates a document leaf element.
     * Hook through which elements are created to represent the
     * document structure.  Because this implementation keeps
     * structure and content separate, elements grow automatically
     * when content is extended so splits of existing elements
     * follow.  The document itself gets to decide how to generate
     * elements to give flexibility in the type of elements used.
     *
     * @param parent the parent element
     * @param a the attributes for the element
     * @param p0 the beginning of the range &gt;= 0
     * @param p1 the end of the range &gt;= p0
     * @return the new element
     */
    protected Element createLeafElement(Element parent, AttributeSet a, int p0, int p1) {
        return new LeafElement(parent, a, p0, p1);
    }

    /**
     * Creates a document branch element, that can contain other elements.
     *
     * @param parent the parent element
     * @param a the attributes
     * @return the element
     */
    protected Element createBranchElement(Element parent, AttributeSet a) {
        return new BranchElement(parent, a);
    }

    // --- Document locking ----------------------------------

    /**
     * Fetches the current writing thread if there is one.
     * This can be used to distinguish whether a method is
     * being called as part of an existing modification or
     * if a lock needs to be acquired and a new transaction
     * started.
     *
     * @return the thread actively modifying the document
     *  or <code>null</code> if there are no modifications in progress
     */
    protected final synchronized Thread getCurrentWriter() {
        return currWriter;
    }

    /**
     * Acquires a lock to begin mutating the document this lock
     * protects.  There can be no writing, notification of changes, or
     * reading going on in order to gain the lock.  Additionally a thread is
     * allowed to gain more than one <code>writeLock</code>,
     * as long as it doesn't attempt to gain additional <code>writeLock</code>s
     * from within document notification.  Attempting to gain a
     * <code>writeLock</code> from within a DocumentListener notification will
     * result in an <code>IllegalStateException</code>.
     *
     * @exception IllegalStateException thrown on illegal lock
     *  attempt.  If the document is implemented properly, this can
     *  only happen if a document listener attempts to mutate the
     *  document.  This situation violates the bean event model
     *  where order of delivery is not guaranteed and all listeners
     *  should be notified before further mutations are allowed.
     */
    protected final synchronized void writeLock() {
        try {
            while ((numReaders > 0) || (currWriter != null)) {
                if (Thread.currentThread() == currWriter) {
                    if (notifyingListeners) {
                        // Assuming one doesn't do something wrong in a
                        // subclass this should only happen if a
                        // DocumentListener tries to mutate the document.
                        throw new IllegalStateException("Attempt to mutate in notification");
                    }
                    numWriters++;
                    return;
                }
                wait();
            }
            currWriter = Thread.currentThread();
            numWriters = 1;
        } catch (InterruptedException e) {
            throw new Error("Interrupted attempt to acquire write lock");
        }
    }

    /**
     * Releases a write lock previously obtained via <code>writeLock</code>.
     * After decrementing the lock count if there are no outstanding locks
     * this will allow a new writer, or readers.
     */
    protected final synchronized void writeUnlock() {
        if (--numWriters <= 0) {
            numWriters = 0;
            currWriter = null;
            notifyAll();
        }
    }

    /**
     * Acquires a lock to begin reading some state from the
     * document.  There can be multiple readers at the same time.
     * Writing blocks the readers until notification of the change
     * to the listeners has been completed.  This method should
     * be used very carefully to avoid unintended compromise
     * of the document.  It should always be balanced with a
     * <code>readUnlock</code>.
     */
    public final synchronized void readLock() {
        try {
            while (currWriter != null) {
                if (currWriter == Thread.currentThread()) {
                    // writer has full read access.... may try to acquire
                    // lock in notification
                    return;
                }
                wait();
            }
            numReaders += 1;
        } catch (InterruptedException e) {
            throw new Error("Interrupted attempt to acquire read lock");
        }
    }

    /**
     * Does a read unlock.  This signals that one
     * of the readers is done.  If there are no more readers
     * then writing can begin again.  This should be balanced
     * with a readLock, and should occur in a finally statement
     * so that the balance is guaranteed.
     */
    public final synchronized void readUnlock() {
        if (currWriter == Thread.currentThread()) {
            // writer has full read access.... may try to acquire
            // lock in notification
            return;
        }
        if (numReaders <= 0) {
            throw new StateInvariantError(BAD_LOCK_STATE);
        }
        numReaders -= 1;
        notify();
    }

    // ----- member variables ------------------------------------------

    private transient int numReaders;
    private transient Thread currWriter;
    /**
     * The number of writers, all obtained from <code>currWriter</code>.
     */
    private transient int numWriters;
    /**
     * True will notifying listeners.
     */
    private transient boolean notifyingListeners;

    private static Boolean defaultI18NProperty;

    /**
     * Storage for document-wide properties.
     */
    private Dictionary<Object, Object> documentProperties = null;

    /**
     * The event listener list for the document.
     */
    protected EventListenerList listenerList = new EventListenerList();

    /**
     * Where the text is actually stored, and a set of marks
     * that track change as the document is edited are managed.
     */
    private Content data;

    /**
     * Factory for the attributes.  This is the strategy for
     * attribute compression and control of the lifetime of
     * a set of attributes as a collection.  This may be shared
     * with other documents.
     */
    private AttributeContext context;

    /**
     * The root of the bidirectional structure for this document.  Its children
     * represent character runs with the same Unicode bidi level.
     */
    private transient BranchElement bidiRoot;

    /** OpenJDK PlainDocument's line map, or null when a subclass declares its root. */
    private transient AbstractElement lineRoot;

    /**
     * Filter for inserting/removing of text.
     */
    private DocumentFilter documentFilter;

    /**
     * Used by DocumentFilter to do actual insert/remove.
     */
    private transient DocumentFilter.FilterBypass filterBypass;

    private static final String BAD_LOCK_STATE = "document lock failure";

    /**
     * Error message to indicate a bad location.
     */
    protected static final String BAD_LOCATION = "document location failure";

    /**
     * Name of elements used to represent paragraphs
     */
    public static final String ParagraphElementName = "paragraph";

    /**
     * Name of elements used to represent content
     */
    public static final String ContentElementName = "content";

    /**
     * Name of elements used to hold sections (lines/paragraphs).
     */
    public static final String SectionElementName = "section";

    /**
     * Name of elements used to hold a unidirectional run
     */
    public static final String BidiElementName = "bidi level";

    /**
     * Name of the attribute used to specify element
     * names.
     */
    public static final String ElementNameAttribute = "$ename";

    /**
     * Document property that indicates whether internationalization
     * functions such as text reordering or reshaping should be
     * performed.
     */
    static final String I18NProperty = "i18n";

    /**
     * Document property that indicates if a character has been inserted
     * into the document that is more than one byte long.
     */
    static final Object MultiByteProperty = "multiByte";

    /**
     * Document property that indicates asynchronous loading is
     * desired, with the thread priority given as the value.
     */
    static final String AsyncLoadPriority = "load priority";

    /**
     * Interface to describe a sequence of character content that
     * can be edited.  Implementations may or may not support a
     * history mechanism which will be reflected by whether or not
     * mutations return an UndoableEdit implementation.
     */
    public interface Content {

        /**
         * Creates a position within the content that will
         * track change as the content is mutated.
         */
        public Position createPosition(int offset) throws BadLocationException;

        /**
         * Current length of the sequence of character content.
         */
        public int length();

        /**
         * Inserts a string of characters into the sequence.
         */
        public UndoableEdit insertString(int where, String str) throws BadLocationException;

        /**
         * Removes some portion of the sequence.
         */
        public UndoableEdit remove(int where, int nitems) throws BadLocationException;

        /**
         * Fetches a string of characters contained in the sequence.
         */
        public String getString(int where, int len) throws BadLocationException;

        /**
         * Gets a sequence of characters and copies them into a Segment.
         */
        public void getChars(int where, int len, Segment txt) throws BadLocationException;
    }

    /**
     * An interface that can be used to allow MutableAttributeSet
     * implementations to use pluggable attribute compression
     * techniques.  Each mutation of the attribute set can be
     * used to exchange a previous AttributeSet instance with
     * another, preserving the possibility of the AttributeSet
     * remaining immutable.
     */
    public interface AttributeContext {

        public AttributeSet addAttribute(AttributeSet old, Object name, Object value);

        public AttributeSet addAttributes(AttributeSet old, AttributeSet attr);

        public AttributeSet removeAttribute(AttributeSet old, Object name);

        public AttributeSet removeAttributes(AttributeSet old, Enumeration<?> names);

        public AttributeSet removeAttributes(AttributeSet old, AttributeSet attrs);

        public AttributeSet getEmptySet();

        public void reclaim(AttributeSet a);
    }

    /**
     * Implements the abstract part of an element.  By default elements
     * support attributes by having a field that represents the immutable
     * part of the current attribute set for the element.  The element itself
     * implements MutableAttributeSet which can be used to modify the set
     * by fetching a new immutable set.  The immutable sets are provided
     * by the AttributeContext associated with the document.
     */
    public abstract class AbstractElement implements Element, MutableAttributeSet, Serializable, TreeNode {

        /**
         * Creates a new AbstractElement.
         *
         * @param parent the parent element
         * @param a the attributes for the element
         * @since 1.4
         */
        public AbstractElement(Element parent, AttributeSet a) {
            this.parent = parent;
            attributes = getAttributeContext().getEmptySet();
            if (a != null) {
                addAttributes(a);
            }
        }

        private final void indent(PrintWriter out, int n) {
            for (int i = 0; i < n; i++) {
                out.print("  ");
            }
        }

        /**
         * Dumps a debugging representation of the element hierarchy.
         *
         * @param psOut the output stream
         * @param indentAmount the indentation level &gt;= 0
         */
        public void dump(PrintStream psOut, int indentAmount) {
            PrintWriter out = new PrintWriter(psOut, true);
            indent(out, indentAmount);
            if (getName() == null) {
                out.print("<??");
            } else {
                out.print("<" + getName());
            }
            if (getAttributeCount() > 0) {
                out.println("");
                // dump the attributes
                Enumeration<?> names = attributes.getAttributeNames();
                while (names.hasMoreElements()) {
                    Object name = names.nextElement();
                    indent(out, indentAmount + 1);
                    out.println(name + "=" + getAttribute(name));
                }
                indent(out, indentAmount);
            }
            out.println(">");

            if (isLeaf()) {
                indent(out, indentAmount + 1);
                out.print("[" + getStartOffset() + "," + getEndOffset() + "]");
                Content c = getContent();
                try {
                    String contentStr = c.getString(getStartOffset(), getEndOffset() - getStartOffset());
                    if (contentStr.length() > 40) {
                        contentStr = contentStr.substring(0, 40) + "...";
                    }
                    out.println("[" + contentStr + "]");
                } catch (BadLocationException e) {
                    // the dump only shows what can be read
                }
            } else {
                int n = getElementCount();
                for (int i = 0; i < n; i++) {
                    AbstractElement e = (AbstractElement) getElement(i);
                    e.dump(psOut, indentAmount + 1);
                }
            }
        }

        // --- AttributeSet ----------------------------

        public int getAttributeCount() {
            return attributes.getAttributeCount();
        }

        public boolean isDefined(Object attrName) {
            return attributes.isDefined(attrName);
        }

        public boolean isEqual(AttributeSet attr) {
            return attributes.isEqual(attr);
        }

        public AttributeSet copyAttributes() {
            return attributes.copyAttributes();
        }

        public Object getAttribute(Object attrName) {
            Object value = attributes.getAttribute(attrName);
            if (value == null) {
                // The delegate nor it's resolvers had a match,
                // so we'll try to resolve through the parent
                // element.
                AttributeSet a = (parent != null) ? parent.getAttributes() : null;
                if (a != null) {
                    value = a.getAttribute(attrName);
                }
            }
            return value;
        }

        public Enumeration<?> getAttributeNames() {
            return attributes.getAttributeNames();
        }

        public boolean containsAttribute(Object name, Object value) {
            return attributes.containsAttribute(name, value);
        }

        public boolean containsAttributes(AttributeSet attrs) {
            return attributes.containsAttributes(attrs);
        }

        /**
         * Gets the resolving parent.
         * If not overridden, the resolving parent defaults to
         * the parent element.
         */
        public AttributeSet getResolveParent() {
            AttributeSet a = attributes.getResolveParent();
            if ((a == null) && (parent != null)) {
                a = parent.getAttributes();
            }
            return a;
        }

        // --- MutableAttributeSet ----------------------------------

        public void addAttribute(Object name, Object value) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            attributes = context.addAttribute(attributes, name, value);
        }

        public void addAttributes(AttributeSet attr) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            attributes = context.addAttributes(attributes, attr);
        }

        public void removeAttribute(Object name) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            attributes = context.removeAttribute(attributes, name);
        }

        public void removeAttributes(Enumeration<?> names) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            attributes = context.removeAttributes(attributes, names);
        }

        public void removeAttributes(AttributeSet attrs) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            if (attrs == this) {
                attributes = context.getEmptySet();
            } else {
                attributes = context.removeAttributes(attributes, attrs);
            }
        }

        public void setResolveParent(AttributeSet parent) {
            checkForIllegalCast();
            AttributeContext context = getAttributeContext();
            if (parent != null) {
                attributes = context.addAttribute(attributes, StyleConstants.ResolveAttribute, parent);
            } else {
                attributes = context.removeAttribute(attributes, StyleConstants.ResolveAttribute);
            }
        }

        private final void checkForIllegalCast() {
            Thread t = getCurrentWriter();
            if ((t == null) || (t != Thread.currentThread())) {
                throw new StateInvariantError("Illegal cast to MutableAttributeSet");
            }
        }

        // --- Element methods -------------------------------------

        public Document getDocument() {
            return AbstractDocument.this;
        }

        public Element getParentElement() {
            return parent;
        }

        public AttributeSet getAttributes() {
            return this;
        }

        public String getName() {
            if (attributes.isDefined(ElementNameAttribute)) {
                return (String) attributes.getAttribute(ElementNameAttribute);
            }
            return null;
        }

        public abstract int getStartOffset();

        public abstract int getEndOffset();

        public abstract Element getElement(int index);

        public abstract int getElementCount();

        public abstract int getElementIndex(int offset);

        public abstract boolean isLeaf();

        // --- TreeNode methods -------------------------------------

        public TreeNode getChildAt(int childIndex) {
            return (TreeNode) getElement(childIndex);
        }

        public int getChildCount() {
            return getElementCount();
        }

        public TreeNode getParent() {
            return (TreeNode) getParentElement();
        }

        public int getIndex(TreeNode node) {
            for (int counter = getChildCount() - 1; counter >= 0; counter--) {
                if (getChildAt(counter) == node) {
                    return counter;
                }
            }
            return -1;
        }

        public abstract boolean getAllowsChildren();

        // JDK 9: the element type of children() is TreeNode.
        public abstract Enumeration<TreeNode> children();

        private Element parent;
        private transient AttributeSet attributes;
    }

    /**
     * Implements a composite element that contains other elements.
     */
    public class BranchElement extends AbstractElement {

        /**
         * Constructs a composite element that initially contains
         * no children.
         *
         * @param parent  the parent element
         * @param a the attributes for the element
         * @since 1.4
         */
        public BranchElement(Element parent, AttributeSet a) {
            super(parent, a);
            children = new AbstractElement[1];
            nchildren = 0;
            lastIndex = -1;
        }

        /**
         * Gets the child element that contains
         * the given model position.
         *
         * @param pos the position &gt;= 0
         * @return the element, null if none
         */
        public Element positionToElement(int pos) {
            int index = getElementIndex(pos);
            Element child = children[index];
            int p0 = child.getStartOffset();
            int p1 = child.getEndOffset();
            if ((pos >= p0) && (pos < p1)) {
                return child;
            }
            return null;
        }

        /**
         * Replaces content with a new set of elements.
         *
         * @param offset the starting offset &gt;= 0
         * @param length the length to replace &gt;= 0
         * @param elems the new elements
         */
        public void replace(int offset, int length, Element[] elems) {
            int delta = elems.length - length;
            int src = offset + length;
            int nmove = nchildren - src;
            int dest = src + delta;
            if ((nchildren + delta) >= children.length) {
                // need to grow the array
                int newLength = Math.max(2 * children.length, nchildren + delta);
                AbstractElement[] newChildren = new AbstractElement[newLength];
                System.arraycopy(children, 0, newChildren, 0, offset);
                System.arraycopy(elems, 0, newChildren, offset, elems.length);
                System.arraycopy(children, src, newChildren, dest, nmove);
                children = newChildren;
            } else {
                // patch the existing array
                System.arraycopy(children, src, children, dest, nmove);
                System.arraycopy(elems, 0, children, offset, elems.length);
            }
            nchildren = nchildren + delta;
        }

        /**
         * Converts the element to a string.
         */
        public String toString() {
            return "BranchElement(" + getName() + ") " + getStartOffset() + "," + getEndOffset() + "\n";
        }

        // --- Element methods -----------------------------------

        public String getName() {
            String nm = super.getName();
            if (nm == null) {
                nm = ParagraphElementName;
            }
            return nm;
        }

        public int getStartOffset() {
            return children[0].getStartOffset();
        }

        public int getEndOffset() {
            Element child = (nchildren > 0) ? children[nchildren - 1] : children[0];
            return child.getEndOffset();
        }

        public Element getElement(int index) {
            if (index < nchildren) {
                return children[index];
            }
            return null;
        }

        public int getElementCount() {
            return nchildren;
        }

        /**
         * Gets the child element index closest to the given model offset.
         *
         * @param offset the offset &gt;= 0
         * @return the element index &gt;= 0
         */
        public int getElementIndex(int offset) {
            int index;
            int lower = 0;
            int upper = nchildren - 1;
            int mid = 0;
            int p0 = getStartOffset();
            int p1;

            if (nchildren == 0) {
                return 0;
            }
            if (offset >= getEndOffset()) {
                return nchildren - 1;
            }

            // see if the last index can be used.
            if ((lastIndex >= lower) && (lastIndex <= upper)) {
                Element lastHit = children[lastIndex];
                p0 = lastHit.getStartOffset();
                p1 = lastHit.getEndOffset();
                if ((offset >= p0) && (offset < p1)) {
                    return lastIndex;
                }

                // last index wasn't a hit, but it does give useful info about
                // where a hit (if any) would be.
                if (offset < p0) {
                    upper = lastIndex;
                } else {
                    lower = lastIndex;
                }
            }

            while (lower <= upper) {
                mid = lower + ((upper - lower) / 2);
                Element elem = children[mid];
                p0 = elem.getStartOffset();
                p1 = elem.getEndOffset();
                if ((offset >= p0) && (offset < p1)) {
                    // found the location
                    index = mid;
                    lastIndex = index;
                    return index;
                } else if (offset < p0) {
                    upper = mid - 1;
                } else {
                    lower = mid + 1;
                }
            }

            // didn't find it, but we indicate the index of where it would belong
            if (offset < p0) {
                index = mid;
            } else {
                index = mid + 1;
            }
            lastIndex = index;
            return index;
        }

        public boolean isLeaf() {
            return false;
        }

        // ------ TreeNode ----------------------------------------------

        public boolean getAllowsChildren() {
            return true;
        }

        public Enumeration<TreeNode> children() {
            if (nchildren == 0) {
                return null;
            }
            Vector<TreeNode> tempVector = new Vector<TreeNode>(nchildren);
            for (int counter = 0; counter < nchildren; counter++) {
                tempVector.addElement(children[counter]);
            }
            return tempVector.elements();
        }

        private AbstractElement[] children;
        private int nchildren;
        private int lastIndex;
    }

    /**
     * Implements an element that directly represents content of
     * some kind.
     */
    public class LeafElement extends AbstractElement {

        /**
         * Constructs an element that represents content within the
         * document (has no children).
         *
         * @param parent  the parent element
         * @param a       the element attributes
         * @param offs0   the start offset &gt;= 0
         * @param offs1   the end offset &gt;= offs0
         * @since 1.4
         */
        public LeafElement(Element parent, AttributeSet a, int offs0, int offs1) {
            super(parent, a);
            try {
                p0 = createPosition(offs0);
                p1 = createPosition(offs1);
            } catch (BadLocationException e) {
                p0 = null;
                p1 = null;
                throw new StateInvariantError("Can't create Position references");
            }
        }

        /**
         * Converts the element to a string.
         */
        public String toString() {
            return "LeafElement(" + getName() + ") " + p0 + "," + p1 + "\n";
        }

        // --- Element methods ---------------------------------------------

        public int getStartOffset() {
            return p0.getOffset();
        }

        public int getEndOffset() {
            return p1.getOffset();
        }

        public String getName() {
            String nm = super.getName();
            if (nm == null) {
                nm = ContentElementName;
            }
            return nm;
        }

        public int getElementIndex(int pos) {
            return -1;
        }

        public Element getElement(int index) {
            return null;
        }

        public int getElementCount() {
            return 0;
        }

        public boolean isLeaf() {
            return true;
        }

        // ------ TreeNode ----------------------------------------------

        public boolean getAllowsChildren() {
            return false;
        }

        public Enumeration<TreeNode> children() {
            return null;
        }

        private transient Position p0;
        private transient Position p1;
    }

    /**
     * Represents the root element of the bidirectional element structure.
     * The root element is the only element in the bidi element structure
     * which contains children.
     */
    class BidiRootElement extends BranchElement {

        BidiRootElement() {
            super(null, null);
        }

        public String getName() {
            return "bidi root";
        }
    }

    /**
     * Represents an element of the bidirectional element structure.
     */
    class BidiElement extends LeafElement {

        BidiElement(Element parent, int start, int end, int level) {
            super(parent, new SimpleAttributeSet(), start, end);
            addAttribute(StyleConstants.BidiLevel, Integer.valueOf(level));
        }

        public String getName() {
            return BidiElementName;
        }

        int getLevel() {
            Integer o = (Integer) getAttribute(StyleConstants.BidiLevel);
            if (o != null) {
                return o.intValue();
            }
            return 0;  // Level 0 is base level (non-embedded) left-to-right
        }

        boolean isLeftToRight() {
            return ((getLevel() % 2) == 0);
        }
    }

    /**
     * Stores document changes as the document is being
     * modified.  Can subsequently be used for change notification
     * when done with the document modification transaction.
     * This is used by the AbstractDocument class and its extensions
     * for broadcasting change information to the document listeners.
     */
    public class DefaultDocumentEvent extends CompoundEdit implements DocumentEvent {

        /**
         * Constructs a change record.
         *
         * @param offs the offset into the document of the change &gt;= 0
         * @param len  the length of the change &gt;= 0
         * @param type the type of event (DocumentEvent.EventType)
         * @since 1.4
         */
        public DefaultDocumentEvent(int offs, int len, DocumentEvent.EventType type) {
            super();
            offset = offs;
            length = len;
            this.type = type;
        }

        /**
         * Returns a string description of the change event.
         */
        public String toString() {
            return edits.toString();
        }

        // --- CompoundEdit methods --------------------------

        /**
         * Adds a document edit.  If the number of edits crosses
         * a threshold, this switches on a hashtable lookup for
         * ElementChange implementations since access of these
         * needs to be relatively quick.
         *
         * @param anEdit a document edit record
         * @return true if the edit was added
         */
        public boolean addEdit(UndoableEdit anEdit) {
            // if the number of changes gets too great, start using
            // a hashtable for to locate the change for a given element.
            if ((changeLookup == null) && (edits.size() > 10)) {
                changeLookup = new Hashtable<Element, ElementChange>();
                int n = edits.size();
                for (int i = 0; i < n; i++) {
                    Object o = edits.elementAt(i);
                    if (o instanceof DocumentEvent.ElementChange) {
                        DocumentEvent.ElementChange ec = (DocumentEvent.ElementChange) o;
                        changeLookup.put(ec.getElement(), ec);
                    }
                }
            }

            // if we have a hashtable... add the entry if it's
            // an ElementChange.
            if ((changeLookup != null) && (anEdit instanceof DocumentEvent.ElementChange)) {
                DocumentEvent.ElementChange ec = (DocumentEvent.ElementChange) anEdit;
                changeLookup.put(ec.getElement(), ec);
            }
            return super.addEdit(anEdit);
        }

        /**
         * Redoes a change.
         *
         * @exception CannotRedoException if the change cannot be redone
         */
        public void redo() throws CannotRedoException {
            writeLock();
            try {
                // change the state
                super.redo();
                // fire a DocumentEvent to notify the view(s)
                UndoRedoDocumentEvent ev = new UndoRedoDocumentEvent(this, false);
                if (type == DocumentEvent.EventType.INSERT) {
                    fireInsertUpdate(ev);
                } else if (type == DocumentEvent.EventType.REMOVE) {
                    fireRemoveUpdate(ev);
                } else {
                    fireChangedUpdate(ev);
                }
            } finally {
                writeUnlock();
            }
        }

        /**
         * Undoes a change.
         *
         * @exception CannotUndoException if the change cannot be undone
         */
        public void undo() throws CannotUndoException {
            writeLock();
            try {
                // change the state
                super.undo();
                // fire a DocumentEvent to notify the view(s)
                UndoRedoDocumentEvent ev = new UndoRedoDocumentEvent(this, true);
                if (type == DocumentEvent.EventType.REMOVE) {
                    fireInsertUpdate(ev);
                } else if (type == DocumentEvent.EventType.INSERT) {
                    fireRemoveUpdate(ev);
                } else {
                    fireChangedUpdate(ev);
                }
            } finally {
                writeUnlock();
            }
        }

        /**
         * DefaultDocument events are significant.  If you wish to aggregate
         * DefaultDocumentEvents to present them as a single edit to the user
         * place them into a CompoundEdit.
         *
         * @return whether the event is significant for edit undo purposes
         */
        public boolean isSignificant() {
            return true;
        }

        /**
         * Provides a localized, human readable description of this edit
         * suitable for use in, say, a change log.
         *
         * @return the description
         */
        public String getPresentationName() {
            DocumentEvent.EventType type = getType();
            if (type == DocumentEvent.EventType.INSERT) {
                return uiText("AbstractDocument.additionText", "addition");
            }
            if (type == DocumentEvent.EventType.REMOVE) {
                return uiText("AbstractDocument.deletionText", "deletion");
            }
            return uiText("AbstractDocument.styleChangeText", "style change");
        }

        /**
         * Provides a localized, human readable description of the undoable
         * form of this edit, e.g. for use as an Undo menu item.
         *
         * @return the description
         */
        public String getUndoPresentationName() {
            return uiText("AbstractDocument.undoText", "Undo") + " " + getPresentationName();
        }

        /**
         * Provides a localized, human readable description of the redoable
         * form of this edit, e.g. for use as a Redo menu item.
         *
         * @return the description
         */
        public String getRedoPresentationName() {
            return uiText("AbstractDocument.redoText", "Redo") + " " + getPresentationName();
        }

        // --- DocumentEvent methods --------------------------

        /**
         * Returns the type of event.
         *
         * @return the event type as a DocumentEvent.EventType
         */
        public DocumentEvent.EventType getType() {
            return type;
        }

        /**
         * Returns the offset within the document of the start of the change.
         *
         * @return the offset &gt;= 0
         */
        public int getOffset() {
            return offset;
        }

        /**
         * Returns the length of the change.
         *
         * @return the length &gt;= 0
         */
        public int getLength() {
            return length;
        }

        /**
         * Gets the document that sourced the change event.
         *
         * @return the document
         */
        public Document getDocument() {
            return AbstractDocument.this;
        }

        /**
         * Gets the changes for an element.
         *
         * @param elem the element
         * @return the changes
         */
        public DocumentEvent.ElementChange getChange(Element elem) {
            if (changeLookup != null) {
                return changeLookup.get(elem);
            }
            int n = edits.size();
            for (int i = 0; i < n; i++) {
                Object o = edits.elementAt(i);
                if (o instanceof DocumentEvent.ElementChange) {
                    DocumentEvent.ElementChange c = (DocumentEvent.ElementChange) o;
                    if (elem.equals(c.getElement())) {
                        return c;
                    }
                }
            }
            return null;
        }

        private int offset;
        private int length;
        private Hashtable<Element, ElementChange> changeLookup;
        private DocumentEvent.EventType type;
    }

    private static String uiText(String key, String fallback) {
        String text = UIManager.getString(key);
        return text != null ? text : fallback;
    }

    /**
     * This event used when firing document changes while Undo/Redo
     * operations. It just wraps DefaultDocumentEvent and delegates
     * all calls to it except getType() which depends on operation
     * (Undo or Redo).
     */
    class UndoRedoDocumentEvent implements DocumentEvent {
        private DefaultDocumentEvent src = null;
        private EventType type = null;

        public UndoRedoDocumentEvent(DefaultDocumentEvent src, boolean isUndo) {
            this.src = src;
            if (isUndo) {
                if (src.getType().equals(EventType.INSERT)) {
                    type = EventType.REMOVE;
                } else if (src.getType().equals(EventType.REMOVE)) {
                    type = EventType.INSERT;
                } else {
                    type = src.getType();
                }
            } else {
                type = src.getType();
            }
        }

        public DefaultDocumentEvent getSource() {
            return src;
        }

        // DocumentEvent methods delegated to DefaultDocumentEvent source
        // except getType() which depends on operation (Undo or Redo).
        public int getOffset() {
            return src.getOffset();
        }

        public int getLength() {
            return src.getLength();
        }

        public Document getDocument() {
            return src.getDocument();
        }

        public DocumentEvent.EventType getType() {
            return type;
        }

        public DocumentEvent.ElementChange getChange(Element elem) {
            return src.getChange(elem);
        }
    }

    /**
     * An implementation of ElementChange that can be added to the document
     * event.
     */
    public static class ElementEdit extends AbstractUndoableEdit implements DocumentEvent.ElementChange {

        /**
         * Constructs an edit record.  This does not modify the element
         * so it can safely be used to <em>catch up</em> a view to the
         * current model state for views that just attached to a model.
         *
         * @param e the element
         * @param index the index into the model &gt;= 0
         * @param removed a set of elements that were removed
         * @param added a set of elements that were added
         */
        public ElementEdit(Element e, int index, Element[] removed, Element[] added) {
            super();
            this.e = e;
            this.index = index;
            this.removed = removed;
            this.added = added;
        }

        /**
         * Returns the underlying element.
         *
         * @return the element
         */
        public Element getElement() {
            return e;
        }

        /**
         * Returns the index into the list of elements.
         *
         * @return the index &gt;= 0
         */
        public int getIndex() {
            return index;
        }

        /**
         * Gets a list of children that were removed.
         *
         * @return the list
         */
        public Element[] getChildrenRemoved() {
            return removed;
        }

        /**
         * Gets a list of children that were added.
         *
         * @return the list
         */
        public Element[] getChildrenAdded() {
            return added;
        }

        /**
         * Redoes a change.
         *
         * @exception CannotRedoException if the change cannot be redone
         */
        public void redo() throws CannotRedoException {
            super.redo();

            // Since this event will be reused, switch around added/removed.
            Element[] tmp = removed;
            removed = added;
            added = tmp;

            // PENDING(prinz) need MutableElement interface, canRedo() should check
            ((AbstractDocument.BranchElement) e).replace(index, removed.length, added);
        }

        /**
         * Undoes a change.
         *
         * @exception CannotUndoException if the change cannot be undone
         */
        public void undo() throws CannotUndoException {
            super.undo();
            // PENDING(prinz) need MutableElement interface, canUndo() should check
            ((AbstractDocument.BranchElement) e).replace(index, added.length, removed);

            // Since this event will be reused, switch around added/removed.
            Element[] tmp = removed;
            removed = added;
            added = tmp;
        }

        private Element e;
        private int index;
        private Element[] removed;
        private Element[] added;
    }

    private class DefaultFilterBypass extends DocumentFilter.FilterBypass {
        public Document getDocument() {
            return AbstractDocument.this;
        }

        public void remove(int offset, int length) throws BadLocationException {
            handleRemove(offset, length);
        }

        public void insertString(int offset, String string, AttributeSet attr) throws BadLocationException {
            handleInsertString(offset, string, attr);
        }

        public void replace(int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            handleRemove(offset, length);
            handleInsertString(offset, text, attrs);
        }
    }
}
