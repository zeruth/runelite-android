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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.EventListener;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * A <code>TextField</code> object is a text component
 * that allows for the editing of a single line of text.
 * <p>
 * Every time the user types a key in the text field, one or
 * more key events are sent to the text field. Pressing Return
 * sends an <code>ActionEvent</code> whose command is the current
 * text to the registered action listeners.
 * <p>
 * There is no native peer on Android: the field measures itself from its
 * columns and {@link FontMetrics} and paints its own text, selection and
 * caret. The constructors do not throw <code>HeadlessException</code>.
 *
 * @author      Sami Shaio
 * @see         java.awt.event.ActionEvent
 * @see         java.awt.event.ActionListener
 * @since       JDK1.0
 */
public class TextField extends TextComponent {

    /**
     * The number of columns in the text field.
     * A column is an approximate average character
     * width that is platform-dependent.
     * Guaranteed to be non-negative.
     *
     * @serial
     * @see #setColumns(int)
     * @see #getColumns()
     */
    int columns;

    /**
     * The echo character, which is used when
     * the user wishes to disguise the characters
     * typed into the text field.
     * The disguises are removed if echoChar = <code>0</code>.
     *
     * @serial
     * @see #getEchoChar()
     * @see #setEchoChar(char)
     * @see #echoCharIsSet()
     */
    char echoChar;

    transient ActionListener actionListener;

    private static final String base = "textfield";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -2966288784432217853L;

    /** The x offset of the first character, so that the caret stays in view. */
    private transient int scroll;

    /**
     * Constructs a new text field.
     */
    public TextField() {
        this("", 0);
    }

    /**
     * Constructs a new text field initialized with the specified text.
     * @param      text       the text to be displayed. If
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed.
     */
    public TextField(String text) {
        this(text, (text != null) ? text.length() : 0);
    }

    /**
     * Constructs a new empty text field with the specified number
     * of columns.  A column is an approximate average character
     * width that is platform-dependent.
     * @param      columns     the number of columns.  If
     *             <code>columns</code> is less than <code>0</code>,
     *             <code>columns</code> is set to <code>0</code>.
     */
    public TextField(int columns) {
        this("", columns);
    }

    /**
     * Constructs a new text field initialized with the specified text
     * to be displayed, and wide enough to hold the specified
     * number of columns. A column is an approximate average character
     * width that is platform-dependent.
     * @param      text       the text to be displayed. If
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed.
     * @param      columns     the number of columns.  If
     *             <code>columns</code> is less than <code>0</code>,
     *             <code>columns</code> is set to <code>0</code>.
     */
    public TextField(String text, int columns) {
        super(text);
        this.columns = (columns >= 0) ? columns : 0;
        enableEvents(AWTEvent.ACTION_EVENT_MASK);
    }

    /**
     * Construct a name for this component.  Called by getName() when the
     * name is null.
     */
    String constructComponentName() {
        synchronized (TextField.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Gets the character that is to be used for echoing.
     * <p>
     * An echo character is useful for text fields where
     * user input should not be echoed to the screen, as in
     * the case of a text field for entering a password.
     * If <code>echoChar</code> = <code>0</code>, user
     * input is echoed to the screen unchanged.
     *
     * @return      the echo character for this text field.
     * @see         java.awt.TextField#echoCharIsSet
     * @see         java.awt.TextField#setEchoChar
     */
    public char getEchoChar() {
        return echoChar;
    }

    /**
     * Sets the echo character for this text field.
     * <p>
     * Setting <code>echoChar</code> = <code>0</code> allows
     * user input to be echoed to the screen again.
     *
     * @param       c   the echo character for this text field.
     * @see         java.awt.TextField#echoCharIsSet
     * @see         java.awt.TextField#getEchoChar
     * @since       JDK1.1
     */
    public void setEchoChar(char c) {
        setEchoCharacter(c);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setEchoChar(char)</code>.
     */
    @Deprecated
    public synchronized void setEchoCharacter(char c) {
        if (echoChar != c) {
            echoChar = c;
            repaint();
        }
    }

    /**
     * Sets the text that is presented by this
     * text component to be the specified text.
     * @param       t       the new text.
     * @see         java.awt.TextComponent#getText
     */
    public void setText(String t) {
        super.setText(t);
    }

    /** The preferred size of a field without columns follows its text, as the peers' does. */
    @Override
    void textChanged() {
        if (columns == 0) {
            invalidate();
        }
    }

    /**
     * Indicates whether or not this text field has a
     * character set for echoing.
     * <p>
     * An echo character is useful for text fields where
     * user input should not be echoed to the screen, as in
     * the case of a text field for entering a password.
     * @return     <code>true</code> if this text field has
     *                 a character set for echoing;
     *                 <code>false</code> otherwise.
     * @see        java.awt.TextField#setEchoChar
     * @see        java.awt.TextField#getEchoChar
     */
    public boolean echoCharIsSet() {
        return echoChar != 0;
    }

    /**
     * Gets the number of columns in this text field. A column is an
     * approximate average character width that is platform-dependent.
     * @return     the number of columns.
     * @see        java.awt.TextField#setColumns
     * @since      JDK1.1
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets the number of columns in this text field. A column is an
     * approximate average character width that is platform-dependent.
     * @param      columns   the number of columns.
     * @see        java.awt.TextField#getColumns
     * @exception  IllegalArgumentException   if the value
     *                 supplied for <code>columns</code>
     *                 is less than <code>0</code>.
     * @since      JDK1.1
     */
    public void setColumns(int columns) {
        int oldVal;
        synchronized (this) {
            oldVal = this.columns;
            if (columns < 0) {
                throw new IllegalArgumentException("columns less than zero.");
            }
            if (columns != oldVal) {
                this.columns = columns;
            }
        }

        if (columns != oldVal) {
            invalidate();
        }
    }

    /**
     * Gets the preferred size of this text field
     * with the specified number of columns.
     * @param     columns the number of columns
     *                 in this text field.
     * @return    the preferred dimensions for
     *                 displaying this text field.
     * @since     JDK1.1
     */
    public Dimension getPreferredSize(int columns) {
        return preferredSize(columns);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getPreferredSize(int)</code>.
     */
    @Deprecated
    public Dimension preferredSize(int columns) {
        FontMetrics fm = AwtWidgets.metrics(this);
        return new Dimension(fm.charWidth('0') * columns + 10, fm.getHeight() + 8);
    }

    /**
     * Gets the preferred size of this text field.
     * @return     the preferred dimensions for
     *                         displaying this text field.
     * @since      JDK1.1
     */
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (columns > 0) return preferredSize(columns);
        FontMetrics fm = AwtWidgets.metrics(this);
        return new Dimension(Math.max(fm.stringWidth(displayed()), fm.charWidth('0')) + 10, fm.getHeight() + 8);
    }

    /**
     * Gets the minimum dimensions for a text field with
     * the specified number of columns.
     * @param    columns   the number of columns in
     *                          this text field.
     * @since    JDK1.1
     */
    public Dimension getMinimumSize(int columns) {
        return minimumSize(columns);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getMinimumSize(int)</code>.
     */
    @Deprecated
    public Dimension minimumSize(int columns) {
        return preferredSize(columns);
    }

    /**
     * Gets the minimum dimensions for this text field.
     * @return     the minimum dimensions for
     *                  displaying this text field.
     * @since      JDK1.1
     */
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    /**
     * Adds the specified action listener to receive
     * action events from this text field.
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param      l the action listener.
     * @see        #removeActionListener
     * @see        #getActionListeners
     * @see        java.awt.event.ActionListener
     * @since      JDK1.1
     */
    public synchronized void addActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.add(actionListener, l);
    }

    /**
     * Removes the specified action listener so that it no longer
     * receives action events from this text field.
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param           l the action listener.
     * @see             #addActionListener
     * @see             #getActionListeners
     * @see             java.awt.event.ActionListener
     * @since           JDK1.1
     */
    public synchronized void removeActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.remove(actionListener, l);
    }

    /**
     * Returns an array of all the action listeners
     * registered on this textfield.
     *
     * @return all of this textfield's <code>ActionListener</code>s
     *         or an empty array if no action
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized ActionListener[] getActionListeners() {
        return getListeners(ActionListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>TextField</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this textfield,
     *          or an empty array if no such
     *          listeners have been added
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ActionListener.class) {
            return ListenerChain.toArray(actionListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this text field. If the event
     * is an instance of <code>ActionEvent</code>,
     * it invokes the <code>processActionEvent</code>
     * method. Otherwise, it invokes <code>processEvent</code>
     * on the superclass.
     *
     * @param      e the event
     * @since      JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof ActionEvent) {
            processActionEvent((ActionEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes action events occurring on this text field by
     * dispatching them to any registered
     * <code>ActionListener</code> objects.
     *
     * @param       e the action event
     * @since       JDK1.1
     */
    protected void processActionEvent(ActionEvent e) {
        ActionListener listener = actionListener;
        if (listener != null) {
            listener.actionPerformed(e);
        }
    }

    /** The peer's part: Return sends the text as an action. */
    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (e.isConsumed() || !isEnabled()) return;
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER) {
            dispatchEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getText(),
                                          e.getWhen(), AwtWidgets.actionModifiers(e)));
        }
    }

    private String displayed() {
        String text = getText();
        if (echoChar == 0) return text;
        char[] masked = new char[text.length()];
        java.util.Arrays.fill(masked, echoChar);
        return new String(masked);
    }

    @Override
    int offsetAt(int x, int y) {
        FontMetrics fm = AwtWidgets.metrics(this);
        String shown = displayed();
        int target = x - 5 + scroll;
        for (int i = 0; i < shown.length(); i++) {
            int left = fm.stringWidth(shown.substring(0, i));
            int right = fm.stringWidth(shown.substring(0, i + 1));
            if (target < (left + right) / 2) return i;
        }
        return shown.length();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        Color well = isEditable() ? AwtWidgets.well(this) : AwtWidgets.background(this);
        g.setColor(well);
        g.fillRect(0, 0, w, h);
        AwtWidgets.bevel(g, well, 0, 0, w, h, false);
        g.setFont(AwtWidgets.font(this));
        FontMetrics fm = g.getFontMetrics();
        String shown = displayed();
        int start, end, caretAt;
        synchronized (this) {
            start = Math.min(getSelectionStart(), shown.length());
            end = Math.min(getSelectionEnd(), shown.length());
            caretAt = getCaretPosition();
        }
        int caretX = fm.stringWidth(shown.substring(0, Math.min(caretAt, shown.length())));
        int inner = Math.max(1, w - 10);
        if (caretX - scroll > inner) scroll = caretX - inner;
        if (caretX - scroll < 0) scroll = caretX;
        Graphics clip = g.create(2, 2, Math.max(0, w - 4), Math.max(0, h - 4));
        try {
            int x = 3 - scroll;
            if (start < end) {
                clip.setColor(AwtWidgets.shade(well, 0.3f));
                int sx = fm.stringWidth(shown.substring(0, start));
                clip.fillRect(x + sx, (h - 4 - fm.getHeight()) / 2, fm.stringWidth(shown.substring(start, end)), fm.getHeight());
            }
            clip.setColor(AwtWidgets.foreground(this));
            AwtWidgets.text(clip, fm, shown, x, 0, h - 4);
            if (isFocusOwner() && isEditable()) {
                int top = (h - 4 - fm.getHeight()) / 2;
                clip.drawLine(x + caretX, top, x + caretX, top + fm.getHeight() - 1);
            }
        } finally {
            clip.dispose();
        }
    }

    /**
     * Returns a string representing the state of this <code>TextField</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return      the parameter string of this text field
     */
    protected String paramString() {
        String str = super.paramString();
        if (echoChar != 0) {
            str += ",echo=" + echoChar;
        }
        return str;
    }
}
