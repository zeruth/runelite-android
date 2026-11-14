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

import net.runelite.awt.impl.AwtWidgets;

/**
 * A <code>TextArea</code> object is a multi-line region
 * that displays text. It can be set to allow editing or
 * to be read-only.
 * <p>
 * There is no native peer on Android: the area measures itself from its
 * rows, columns and {@link FontMetrics}, paints its own lines, selection and
 * caret, and scrolls to keep the caret in view. The scrollbar policy is kept
 * and reported, but the area draws no scrollbars of its own. The
 * constructors do not throw <code>HeadlessException</code>.
 *
 * @author      Sami Shaio
 * @since       JDK1.0
 */
public class TextArea extends TextComponent {

    /**
     * The number of rows in the <code>TextArea</code>.
     * This parameter will determine the text area's height.
     * Guaranteed to be non-negative.
     *
     * @serial
     * @see #getRows()
     * @see #setRows(int)
     */
    int rows;

    /**
     * The number of columns in the <code>TextArea</code>.
     * A column is an approximate average character
     * width that is platform-dependent.
     * This parameter will determine the text area's width.
     * Guaranteed to be non-negative.
     *
     * @serial
     * @see  #setColumns(int)
     * @see  #getColumns()
     */
    int columns;

    private static final String base = "text";
    private static int nameCounter = 0;

    /**
     * Create and display both vertical and horizontal scrollbars.
     * @since JDK1.1
     */
    public static final int SCROLLBARS_BOTH = 0;

    /**
     * Create and display vertical scrollbar only.
     * @since JDK1.1
     */
    public static final int SCROLLBARS_VERTICAL_ONLY = 1;

    /**
     * Create and display horizontal scrollbar only.
     * @since JDK1.1
     */
    public static final int SCROLLBARS_HORIZONTAL_ONLY = 2;

    /**
     * Do not create or display any scrollbars for the text area.
     * @since JDK1.1
     */
    public static final int SCROLLBARS_NONE = 3;

    /**
     * Determines which scrollbars are created for the
     * text area. It can be one of four values :
     * <code>SCROLLBARS_BOTH</code> = both scrollbars.<BR>
     * <code>SCROLLBARS_HORIZONTAL_ONLY</code> = Horizontal bar only.<BR>
     * <code>SCROLLBARS_VERTICAL_ONLY</code> = Vertical bar only.<BR>
     * <code>SCROLLBARS_NONE</code> = No scrollbars.<BR>
     *
     * @serial
     * @see #getScrollbarVisibility()
     */
    private int scrollbarVisibility;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 3692302836626095722L;

    /** The first visible line and the x offset of the text, so that the caret stays in view. */
    private transient int topLine, scrollX;

    /**
     * Constructs a new text area with the empty string as text.
     * This text area is created with scrollbar visibility equal to
     * {@link #SCROLLBARS_BOTH}, so both vertical and horizontal
     * scrollbars will be visible for this text area.
     */
    public TextArea() {
        this("", 0, 0, SCROLLBARS_BOTH);
    }

    /**
     * Constructs a new text area with the specified text.
     * This text area is created with scrollbar visibility equal to
     * {@link #SCROLLBARS_BOTH}, so both vertical and horizontal
     * scrollbars will be visible for this text area.
     * @param      text       the text to be displayed; if
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed
     */
    public TextArea(String text) {
        this(text, 0, 0, SCROLLBARS_BOTH);
    }

    /**
     * Constructs a new text area with the specified number of
     * rows and columns and the empty string as text.
     * @param rows the number of rows
     * @param columns the number of columns
     */
    public TextArea(int rows, int columns) {
        this("", rows, columns, SCROLLBARS_BOTH);
    }

    /**
     * Constructs a new text area with the specified text,
     * and with the specified number of rows and columns.
     * @param      text       the text to be displayed; if
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed
     * @param     rows      the number of rows
     * @param     columns   the number of columns
     */
    public TextArea(String text, int rows, int columns) {
        this(text, rows, columns, SCROLLBARS_BOTH);
    }

    /**
     * Constructs a new text area with the specified text,
     * and with the rows, columns, and scroll bar visibility
     * as specified.  All <code>TextArea</code> constructors defer to
     * this one.
     * <p>
     * The <code>TextArea</code> class defines several constants
     * that can be supplied as values for the
     * <code>scrollbars</code> argument; any other value is
     * treated as <code>SCROLLBARS_BOTH</code>. A negative
     * <code>rows</code> or <code>columns</code> is treated as zero.
     *
     * @param      text       the text to be displayed; if
     *             <code>text</code> is <code>null</code>, the empty
     *             string <code>""</code> will be displayed
     * @param      rows       the number of rows
     * @param      columns    the number of columns
     * @param      scrollbars  a constant that determines what
     *             scrollbars are created to view the text area
     * @since      JDK1.1
     */
    public TextArea(String text, int rows, int columns, int scrollbars) {
        super(text);

        this.rows = (rows >= 0) ? rows : 0;
        this.columns = (columns >= 0) ? columns : 0;

        if (scrollbars >= SCROLLBARS_BOTH && scrollbars <= SCROLLBARS_NONE) {
            this.scrollbarVisibility = scrollbars;
        } else {
            this.scrollbarVisibility = SCROLLBARS_BOTH;
        }
    }

    /**
     * Construct a name for this component.  Called by
     * <code>getName</code> when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (TextArea.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Inserts the specified text at the specified position
     * in this text area.
     * <p>Note that passing <code>null</code> or inconsistent
     * parameters is invalid and will result in unspecified
     * behavior.
     *
     * @param      str the non-<code>null</code> text to insert
     * @param      pos the position at which to insert
     * @see        java.awt.TextComponent#setText
     * @see        java.awt.TextArea#replaceRange
     * @see        java.awt.TextArea#append
     * @since      JDK1.1
     */
    public void insert(String str, int pos) {
        insertText(str, pos);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>insert(String, int)</code>.
     */
    @Deprecated
    public synchronized void insertText(String str, int pos) {
        String text = getText();
        pos = Math.max(0, Math.min(pos, text.length()));
        changeText(text.substring(0, pos) + str + text.substring(pos), -1);
    }

    /**
     * Appends the given text to the text area's current text.
     * <p>Note that passing <code>null</code> or inconsistent
     * parameters is invalid and will result in unspecified
     * behavior.
     *
     * @param     str the non-<code>null</code> text to append
     * @see       java.awt.TextArea#insert
     * @since     JDK1.1
     */
    public void append(String str) {
        appendText(str);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>append(String)</code>.
     */
    @Deprecated
    public synchronized void appendText(String str) {
        insertText(str, getText().length());
    }

    /**
     * Replaces text between the indicated start and end positions
     * with the specified replacement text.  The text at the end
     * position will not be replaced.  The text at the start
     * position will be replaced (unless the start position is the
     * same as the end position).
     * The text position is zero-based.  The inserted substring may be
     * of a different length than the text it replaces.
     * <p>Note that passing <code>null</code> or inconsistent
     * parameters is invalid and will result in unspecified
     * behavior.
     *
     * @param     str      the non-<code>null</code> text to use as
     *                     the replacement
     * @param     start    the start position
     * @param     end      the end position
     * @see       java.awt.TextArea#insert
     * @since     JDK1.1
     */
    public void replaceRange(String str, int start, int end) {
        replaceText(str, start, end);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>replaceRange(String, int, int)</code>.
     */
    @Deprecated
    public synchronized void replaceText(String str, int start, int end) {
        String text = getText();
        start = Math.max(0, Math.min(start, text.length()));
        end = Math.max(start, Math.min(end, text.length()));
        changeText(text.substring(0, start) + str + text.substring(end), -1);
    }

    /**
     * Returns the number of rows in the text area.
     * @return    the number of rows in the text area
     * @see       #setRows(int)
     * @see       #getColumns()
     * @since     JDK1
     */
    public int getRows() {
        return rows;
    }

    /**
     * Sets the number of rows for this text area.
     * @param       rows   the number of rows
     * @see         #getRows()
     * @see         #setColumns(int)
     * @exception   IllegalArgumentException   if the value
     *                 supplied for <code>rows</code>
     *                 is less than <code>0</code>
     * @since       JDK1.1
     */
    public void setRows(int rows) {
        int oldVal = this.rows;
        if (rows < 0) {
            throw new IllegalArgumentException("rows less than zero.");
        }
        if (rows != oldVal) {
            this.rows = rows;
            invalidate();
        }
    }

    /**
     * Returns the number of columns in this text area.
     * @return    the number of columns in the text area
     * @see       #setColumns(int)
     * @see       #getRows()
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets the number of columns for this text area.
     * @param       columns   the number of columns
     * @see         #getColumns()
     * @see         #setRows(int)
     * @exception   IllegalArgumentException   if the value
     *                 supplied for <code>columns</code>
     *                 is less than <code>0</code>
     * @since       JDK1.1
     */
    public void setColumns(int columns) {
        int oldVal = this.columns;
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        if (columns != oldVal) {
            this.columns = columns;
            invalidate();
        }
    }

    /**
     * Returns an enumerated value that indicates which scroll bars
     * the text area uses.
     *
     * @return     an integer that indicates which scroll bars are used
     * @see        java.awt.TextArea#SCROLLBARS_BOTH
     * @see        java.awt.TextArea#SCROLLBARS_VERTICAL_ONLY
     * @see        java.awt.TextArea#SCROLLBARS_HORIZONTAL_ONLY
     * @see        java.awt.TextArea#SCROLLBARS_NONE
     * @see        java.awt.TextArea#TextArea(java.lang.String, int, int, int)
     * @since      JDK1.1
     */
    public int getScrollbarVisibility() {
        return scrollbarVisibility;
    }

    /**
     * Determines the preferred size of a text area with the specified
     * number of rows and columns.
     * @param     rows   the number of rows
     * @param     columns   the number of columns
     * @return    the preferred dimensions required to display
     *                       the text area with the specified
     *                       number of rows and columns
     * @see       java.awt.Component#getPreferredSize
     * @since     JDK1.1
     */
    public Dimension getPreferredSize(int rows, int columns) {
        return preferredSize(rows, columns);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getPreferredSize(int, int)</code>.
     */
    @Deprecated
    public Dimension preferredSize(int rows, int columns) {
        FontMetrics fm = AwtWidgets.metrics(this);
        return new Dimension(fm.charWidth('0') * columns + 10, fm.getHeight() * rows + 8);
    }

    /**
     * Determines the preferred size of this text area.
     * @return    the preferred dimensions needed for this text area
     * @see       java.awt.Component#getPreferredSize
     * @since     JDK1.1
     */
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (rows > 0 && columns > 0) return preferredSize(rows, columns);
        // Without both dimensions the peers size to the text, at least one character cell.
        FontMetrics fm = AwtWidgets.metrics(this);
        String[] lines = getText().split("\n", -1);
        int widest = fm.charWidth('0');
        for (String line : lines) widest = Math.max(widest, fm.stringWidth(line));
        int w = columns > 0 ? fm.charWidth('0') * columns : widest;
        int h = fm.getHeight() * (rows > 0 ? rows : Math.max(1, lines.length));
        return new Dimension(w + 10, h + 8);
    }

    /**
     * Determines the minimum size of a text area with the specified
     * number of rows and columns.
     * @param       rows   the number of rows
     * @param       columns the number of columns
     * @return      the minimum dimensions required to display
     *                       the text area with the specified
     *                       number of rows and columns
     * @see         java.awt.Component#getMinimumSize
     * @since       JDK1.1
     */
    public Dimension getMinimumSize(int rows, int columns) {
        return minimumSize(rows, columns);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getMinimumSize(int, int)</code>.
     */
    @Deprecated
    public Dimension minimumSize(int rows, int columns) {
        return preferredSize(rows, columns);
    }

    /**
     * Determines the minimum size of this text area.
     * @return       the preferred dimensions needed for this text area
     * @see          java.awt.Component#getPreferredSize
     * @since        JDK1.1
     */
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    void textChanged() {
        if (rows == 0 || columns == 0) {
            invalidate();
        }
    }

    @Override
    boolean acceptsControlCharacters() {
        return true;
    }

    @Override
    int lineStart(int from) {
        String text = getText();
        from = Math.max(0, Math.min(from, text.length()));
        return text.lastIndexOf('\n', from - 1) + 1;
    }

    @Override
    int lineEnd(int from) {
        String text = getText();
        int end = text.indexOf('\n', Math.max(0, from));
        return end < 0 ? text.length() : end;
    }

    @Override
    int offsetAt(int x, int y) {
        FontMetrics fm = AwtWidgets.metrics(this);
        String text = getText();
        int line = Math.max(0, (y - 4) / Math.max(1, fm.getHeight())) + topLine;
        int start = 0;
        for (int i = 0; i < line; i++) {
            int next = text.indexOf('\n', start);
            if (next < 0) return text.length();
            start = next + 1;
        }
        int end = lineEnd(start);
        int target = x - 5 + scrollX;
        for (int i = start; i < end; i++) {
            int left = fm.stringWidth(text.substring(start, i));
            int right = fm.stringWidth(text.substring(start, i + 1));
            if (target < (left + right) / 2) return i;
        }
        return end;
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
        String text;
        int start, end, caretAt;
        synchronized (this) {
            text = getText();
            start = getSelectionStart();
            end = getSelectionEnd();
            caretAt = getCaretPosition();
        }
        int lineHeight = Math.max(1, fm.getHeight());
        int visible = Math.max(1, (h - 8) / lineHeight);
        int caretLine = 0;
        for (int i = text.indexOf('\n'); i >= 0 && i < caretAt; i = text.indexOf('\n', i + 1)) caretLine++;
        if (caretLine < topLine) topLine = caretLine;
        if (caretLine >= topLine + visible) topLine = caretLine - visible + 1;
        int caretLineStart = lineStart(caretAt);
        int caretX = fm.stringWidth(text.substring(caretLineStart, caretAt));
        int inner = Math.max(1, w - 10);
        if (caretX - scrollX > inner) scrollX = caretX - inner;
        if (caretX - scrollX < 0) scrollX = caretX;

        Graphics clip = g.create(2, 2, Math.max(0, w - 4), Math.max(0, h - 4));
        try {
            Color selection = AwtWidgets.shade(well, 0.3f);
            Color foreground = AwtWidgets.foreground(this);
            int lineIndex = 0, lineStartAt = 0, x = 3 - scrollX;
            while (lineStartAt <= text.length()) {
                int lineEndAt = lineEnd(lineStartAt);
                int row = lineIndex - topLine;
                if (row >= visible + 1) break;
                if (row >= 0) {
                    String line = text.substring(lineStartAt, lineEndAt);
                    int top = 2 + row * lineHeight;
                    int s = Math.max(start, lineStartAt), e = Math.min(end, lineEndAt);
                    if (s < e) {
                        clip.setColor(selection);
                        clip.fillRect(x + fm.stringWidth(text.substring(lineStartAt, s)), top,
                                      fm.stringWidth(text.substring(s, e)), lineHeight);
                    }
                    clip.setColor(foreground);
                    if (!line.isEmpty()) clip.drawString(line, x, top + fm.getAscent());
                    if (lineIndex == caretLine && isFocusOwner() && isEditable()) {
                        clip.drawLine(x + caretX, top, x + caretX, top + lineHeight - 1);
                    }
                }
                lineIndex++;
                lineStartAt = lineEndAt + 1;
            }
        } finally {
            clip.dispose();
        }
    }

    /**
     * Returns a string representing the state of this <code>TextArea</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return      the parameter string of this text area
     */
    protected String paramString() {
        String sbVisStr;
        switch (scrollbarVisibility) {
            case SCROLLBARS_BOTH:
                sbVisStr = "both";
                break;
            case SCROLLBARS_VERTICAL_ONLY:
                sbVisStr = "vertical-only";
                break;
            case SCROLLBARS_HORIZONTAL_ONLY:
                sbVisStr = "horizontal-only";
                break;
            case SCROLLBARS_NONE:
                sbVisStr = "none";
                break;
            default:
                sbVisStr = "invalid display policy";
        }

        return super.paramString() + ",rows=" + rows +
            ",columns=" + columns +
          ",scrollbarVisibility=" + sbVisStr;
    }
}
