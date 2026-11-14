/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.EventListener;
import net.runelite.awt.impl.ListenerChain;

/**
 * The <code>TextComponent</code> class is the superclass of
 * any component that allows the editing of some text.
 * <p>
 * A text component embodies a string of text.  The
 * <code>TextComponent</code> class defines a set of methods
 * that determine whether or not this text is editable. If the
 * component is editable, it defines another set of methods
 * that supports a text insertion caret.
 * <p>
 * In addition, the class defines methods that are used
 * to maintain a current <em>selection</em> from the text.
 * <p>
 * There is no native peer on Android: the component edits its own text from
 * the key events the host delivers (typed characters, Backspace/Delete,
 * Left/Right/Home/End, Ctrl+A) and sends a <code>TextEvent</code> each time
 * the text changes, whether by the user or through <code>setText</code>,
 * <code>insert</code>, <code>append</code> or <code>replaceRange</code>, as
 * a displayed desktop component does.
 *
 * @author      Sami Shaio
 * @author      Arthur van Hoff
 * @since       JDK1.0
 */
public class TextComponent extends Component {

    /**
     * The value of the text.
     * A <code>null</code> value is the same as "".
     *
     * @serial
     * @see #setText(String)
     * @see #getText()
     */
    String text;

    /**
     * A boolean indicating whether or not this
     * <code>TextComponent</code> is editable.
     * It will be <code>true</code> if the text component
     * is editable and <code>false</code> if not.
     *
     * @serial
     * @see #isEditable()
     */
    boolean editable = true;

    /**
     * The selection refers to the selected text, and the
     * <code>selectionStart</code> is the start position
     * of the selected text.
     *
     * @serial
     * @see #getSelectionStart()
     * @see #setSelectionStart(int)
     */
    int selectionStart;

    /**
     * The selection refers to the selected text, and the
     * <code>selectionEnd</code>
     * is the end position of the selected text.
     *
     * @serial
     * @see #getSelectionEnd()
     * @see #setSelectionEnd(int)
     */
    int selectionEnd;

    // A flag used to tell whether the background has been set by
    // developer code (as opposed to AWT code).  Used to determine
    // the background color of non-editable TextComponents.
    boolean backgroundSetByClientCode = false;

    /** The caret, the peer's: the end of the last selection or edit. */
    transient int caret;

    protected transient TextListener textListener;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -2214773872412987419L;

    /**
     * Constructs a new text component initialized with the
     * specified text. Sets the value of the cursor to
     * <code>Cursor.TEXT_CURSOR</code>.
     * @param      text       the text to be displayed; if
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed
     * @see        java.awt.Cursor
     */
    TextComponent(String text) {
        this.text = (text != null) ? text : "";
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.TEXT_EVENT_MASK);
    }

    /**
     * Sets the text that is presented by this
     * text component to be the specified text.
     * @param       t   the new text;
     *                  if this parameter is <code>null</code> then
     *                  the text is set to the empty string ""
     * @see         java.awt.TextComponent#getText
     */
    public void setText(String t) {
        changeText(t != null ? t : "", -1);
    }

    /**
     * Returns the text that is presented by this text component.
     * By default, this is an empty string.
     *
     * @return the value of this <code>TextComponent</code>
     * @see     java.awt.TextComponent#setText
     */
    public synchronized String getText() {
        return text;
    }

    /**
     * Returns the selected text from the text that is
     * presented by this text component.
     * @return      the selected text of this text component
     * @see         java.awt.TextComponent#select
     */
    public synchronized String getSelectedText() {
        return getText().substring(getSelectionStart(), getSelectionEnd());
    }

    /**
     * Indicates whether or not this text component is editable.
     * @return     <code>true</code> if this text component is
     *                  editable; <code>false</code> otherwise.
     * @see        java.awt.TextComponent#setEditable
     * @since      JDK1.0
     */
    public boolean isEditable() {
        return editable;
    }

    /**
     * Sets the flag that determines whether or not this
     * text component is editable.
     * <p>
     * If the flag is set to <code>true</code>, this text component
     * becomes user editable. If the flag is set to <code>false</code>,
     * the user cannot change the text of this text component.
     *
     * @param     b   a flag indicating whether this text component
     *                      is user editable.
     * @see       java.awt.TextComponent#isEditable
     * @since     JDK1.0
     */
    public synchronized void setEditable(boolean b) {
        if (editable == b) {
            return;
        }
        editable = b;
        repaint();
    }

    /**
     * Sets the background color of this text component.
     *
     * @param c The color to become this text component's color.
     *        If this parameter is null then this text component
     *        will inherit the background color of its parent.
     * @see #getBackground()
     * @since 1.0
     */
    public void setBackground(Color c) {
        backgroundSetByClientCode = true;
        super.setBackground(c);
    }

    /**
     * Gets the start position of the selected text in
     * this text component.
     * @return      the start position of the selected text
     * @see         java.awt.TextComponent#setSelectionStart
     * @see         java.awt.TextComponent#getSelectionEnd
     */
    public synchronized int getSelectionStart() {
        return selectionStart;
    }

    /**
     * Sets the selection start for this text component to
     * the specified position. The new start point is constrained
     * to be at or before the current selection end. It also
     * cannot be set to less than zero, the beginning of the
     * component's text.
     * @param       selectionStart   the start position of the
     *                        selected text
     * @since       JDK1.1
     */
    public synchronized void setSelectionStart(int selectionStart) {
        /* Route through select method to enforce consistent policy
         * between selectionStart and selectionEnd.
         */
        select(selectionStart, getSelectionEnd());
    }

    /**
     * Gets the end position of the selected text in
     * this text component.
     * @return      the end position of the selected text
     * @see         java.awt.TextComponent#setSelectionEnd
     * @see         java.awt.TextComponent#getSelectionStart
     */
    public synchronized int getSelectionEnd() {
        return selectionEnd;
    }

    /**
     * Sets the selection end for this text component to
     * the specified position. The new end point is constrained
     * to be at or after the current selection start. It also
     * cannot be set beyond the end of the component's text.
     * @param       selectionEnd   the end position of the
     *                        selected text
     * @since       JDK1.1
     */
    public synchronized void setSelectionEnd(int selectionEnd) {
        /* Route through select method to enforce consistent policy
         * between selectionStart and selectionEnd.
         */
        select(getSelectionStart(), selectionEnd);
    }

    /**
     * Selects the text between the specified start and end positions.
     * <p>
     * This method sets the start and end positions of the
     * selected text, enforcing the restriction that the start position
     * must be greater than or equal to zero.  The end position must be
     * greater than or equal to the start position, and less than or
     * equal to the length of the text component's text.
     *
     * @param        selectionStart the zero-based index of the first
     *                   character (<code>char</code> value) to be selected
     * @param        selectionEnd the zero-based end position of the
     *                   text to be selected; the character (<code>char</code> value) at
     *                   <code>selectionEnd</code> is not selected
     * @see          java.awt.TextComponent#setSelectionStart
     * @see          java.awt.TextComponent#setSelectionEnd
     * @see          java.awt.TextComponent#selectAll
     */
    public synchronized void select(int selectionStart, int selectionEnd) {
        String text = getText();
        if (selectionStart < 0) {
            selectionStart = 0;
        }
        if (selectionStart > text.length()) {
            selectionStart = text.length();
        }
        if (selectionEnd > text.length()) {
            selectionEnd = text.length();
        }
        if (selectionEnd < selectionStart) {
            selectionEnd = selectionStart;
        }

        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
        this.caret = selectionEnd;
        repaint();
    }

    /**
     * Selects all the text in this text component.
     * @see        java.awt.TextComponent#select
     */
    public synchronized void selectAll() {
        select(0, getText().length());
    }

    /**
     * Sets the position of the text insertion caret.
     * The caret position is constrained to be between 0
     * and the last character of the text, inclusive.
     * If the passed-in value is greater than this range,
     * the value is set to the last character (or 0 if
     * the <code>TextComponent</code> contains no text)
     * and no error is returned.  If the passed-in value is
     * less than 0, an <code>IllegalArgumentException</code>
     * is thrown.
     *
     * @param        position the position of the text insertion caret
     * @exception    IllegalArgumentException if <code>position</code>
     *               is less than zero
     * @since        JDK1.1
     */
    public synchronized void setCaretPosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("position less than zero.");
        }

        int maxposition = getText().length();
        if (position > maxposition) {
            position = maxposition;
        }
        select(position, position);
    }

    /**
     * Returns the position of the text insertion caret.
     * The caret position is constrained to be between 0
     * and the last character of the text, inclusive.
     * If the text or caret have not been set, the default
     * caret position is 0.
     *
     * @return       the position of the text insertion caret
     * @see #setCaretPosition(int)
     * @since        JDK1.1
     */
    public synchronized int getCaretPosition() {
        return Math.max(0, Math.min(caret, getText().length()));
    }

    /**
     * Adds the specified text event listener to receive text events
     * from this text component.
     * If <code>l</code> is <code>null</code>, no exception is
     * thrown and no action is performed.
     *
     * @param l the text event listener
     * @see             #removeTextListener
     * @see             #getTextListeners
     * @see             java.awt.event.TextListener
     */
    public synchronized void addTextListener(TextListener l) {
        if (l == null) {
            return;
        }
        textListener = ListenerChain.add(textListener, l);
    }

    /**
     * Removes the specified text event listener so that it no longer
     * receives text events from this text component
     * If <code>l</code> is <code>null</code>, no exception is
     * thrown and no action is performed.
     *
     * @param           l     the text listener
     * @see             #addTextListener
     * @see             #getTextListeners
     * @see             java.awt.event.TextListener
     * @since           JDK1.1
     */
    public synchronized void removeTextListener(TextListener l) {
        if (l == null) {
            return;
        }
        textListener = ListenerChain.remove(textListener, l);
    }

    /**
     * Returns an array of all the text listeners
     * registered on this text component.
     *
     * @return all of this text component's <code>TextListener</code>s
     *         or an empty array if no text
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized TextListener[] getTextListeners() {
        return getListeners(TextListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>TextComponent</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this text component,
     *          or an empty array if no such
     *          listeners have been added
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == TextListener.class) {
            return ListenerChain.toArray(textListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this text component. If the event is a
     * <code>TextEvent</code>, it invokes the <code>processTextEvent</code>
     * method else it invokes its superclass's <code>processEvent</code>.
     *
     * @param e the event
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof TextEvent) {
            processTextEvent((TextEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes text events occurring on this text component by
     * dispatching them to any registered <code>TextListener</code> objects.
     *
     * @param e the text event
     */
    protected void processTextEvent(TextEvent e) {
        TextListener listener = textListener;
        if (listener != null) {
            int id = e.getID();
            switch (id) {
            case TextEvent.TEXT_VALUE_CHANGED:
                listener.textValueChanged(e);
                break;
            }
        }
    }

    /**
     * Returns a string representing the state of this
     * <code>TextComponent</code>. This
     * method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return      the parameter string of this text component
     */
    protected String paramString() {
        String str = super.paramString() + ",text=" + getText();
        if (editable) {
            str += ",editable";
        }
        return str + ",selection=" + getSelectionStart() + "-" + getSelectionEnd();
    }

    // ---- the peer's part: the edit model the widgets share ----

    /**
     * Replaces the whole text and reports the change; {@code caretAt} < 0 keeps the caret
     * where it was, clamped, as the peers do on setText.
     */
    void changeText(String next, int caretAt) {
        boolean changed;
        synchronized (this) {
            changed = !next.equals(text);
            text = next;
            int at = caretAt < 0 ? Math.min(caret, next.length()) : Math.min(caretAt, next.length());
            selectionStart = Math.min(selectionStart, next.length());
            selectionEnd = Math.min(selectionEnd, next.length());
            if (caretAt >= 0) {
                selectionStart = selectionEnd = at;
            }
            caret = at;
        }
        if (changed) {
            textChanged();
            dispatchEvent(new TextEvent(this, TextEvent.TEXT_VALUE_CHANGED));
        }
        repaint();
    }

    /** A hook for the subclasses whose preferred size follows the text. */
    void textChanged() {
    }

    /** Replaces the selection (or inserts at the caret) as typing does. */
    void replaceSelection(String with) {
        String next;
        int at;
        synchronized (this) {
            int start = Math.min(selectionStart, selectionEnd), end = Math.max(selectionStart, selectionEnd);
            if (start == end) {
                start = end = getCaretPosition();
            }
            next = text.substring(0, start) + with + text.substring(end);
            at = start + with.length();
        }
        changeText(next, at);
    }

    /** Whether the user's Return and Tab go into the text, as in a text area. */
    boolean acceptsControlCharacters() {
        return false;
    }

    /** The text offset nearest to a point inside the component; the subclasses lay the text out. */
    int offsetAt(int x, int y) {
        return getCaretPosition();
    }

    /** The offset Home/End go to from {@code from}: the line's ends in a text area. */
    int lineStart(int from) {
        return 0;
    }

    int lineEnd(int from) {
        return getText().length();
    }

    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1 && isEnabled()) {
            requestFocus();
            int at = offsetAt(e.getX(), e.getY());
            if (e.getClickCount() >= 2) {
                selectAll();
            } else {
                select(at, at);
            }
        }
    }

    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (e.isConsumed() || !isEnabled()) return;
        if (e.getID() == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            if (!editable || c == KeyEvent.CHAR_UNDEFINED || e.isControlDown()) return;
            boolean control = c < 0x20 || c == 0x7f;
            if (control && !(acceptsControlCharacters() && (c == '\n' || c == '\r' || c == '\t'))) return;
            replaceSelection(c == '\r' ? "\n" : String.valueOf(c));
            e.consume();
            return;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED) return;
        int start, end, caretNow, length;
        synchronized (this) {
            start = Math.min(selectionStart, selectionEnd);
            end = Math.max(selectionStart, selectionEnd);
            caretNow = getCaretPosition();
            length = text.length();
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE:
                if (!editable) return;
                if (start != end) replaceSelection("");
                else if (caretNow > 0) {
                    select(caretNow - 1, caretNow);
                    replaceSelection("");
                }
                break;
            case KeyEvent.VK_DELETE:
                if (!editable) return;
                if (start != end) replaceSelection("");
                else if (caretNow < length) {
                    select(caretNow, caretNow + 1);
                    replaceSelection("");
                }
                break;
            case KeyEvent.VK_LEFT:
                setCaretPosition(start != end ? start : Math.max(0, caretNow - 1));
                break;
            case KeyEvent.VK_RIGHT:
                setCaretPosition(start != end ? end : Math.min(length, caretNow + 1));
                break;
            case KeyEvent.VK_HOME:
                setCaretPosition(lineStart(caretNow));
                break;
            case KeyEvent.VK_END:
                setCaretPosition(lineEnd(caretNow));
                break;
            case KeyEvent.VK_A:
                if (e.isControlDown()) selectAll();
                break;
            default:
                break;
        }
    }
}
