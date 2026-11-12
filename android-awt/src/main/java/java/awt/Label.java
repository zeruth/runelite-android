/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * A <code>Label</code> object is a component for placing text in a
 * container. A label displays a single line of read-only text.
 * The text can be changed by the application, but a user cannot edit it
 * directly.
 * <p>
 * There is no native peer on Android: the label measures itself with its
 * {@link FontMetrics} the way the desktop peers do and paints its own text.
 * The constructors do not throw <code>HeadlessException</code>, since the
 * Android graphics environment reports headless while still rendering.
 *
 * @author      Sami Shaio
 * @since       JDK1.0
 */
public class Label extends Component {

    /**
     * Indicates that the label should be left justified.
     */
    public static final int LEFT        = 0;

    /**
     * Indicates that the label should be centered.
     */
    public static final int CENTER      = 1;

    /**
     * Indicates that the label should be right justified.
     * @since   JDK1.0t.
     */
    public static final int RIGHT       = 2;

    /**
     * The text of this label.
     * This text can be modified by the program
     * but never by the user.
     *
     * @serial
     * @see #getText()
     * @see #setText(String)
     */
    String text;

    /**
     * The label's alignment.  The default alignment is set
     * to be left justified.
     *
     * @serial
     * @see #getAlignment()
     * @see #setAlignment(int)
     */
    int    alignment = LEFT;

    private static final String base = "label";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
     private static final long serialVersionUID = 3094126758329070636L;

    /**
     * Constructs an empty label.
     * The text of the label is the empty string <code>""</code>.
     */
    public Label() {
        this("", LEFT);
    }

    /**
     * Constructs a new label with the specified string of text,
     * left justified.
     * @param text the string that the label presents.
     *        A <code>null</code> value
     *        will be accepted without causing a NullPointerException
     *        to be thrown.
     */
    public Label(String text) {
        this(text, LEFT);
    }

    /**
     * Constructs a new label that presents the specified string of
     * text with the specified alignment.
     * Possible values for <code>alignment</code> are <code>Label.LEFT</code>,
     * <code>Label.RIGHT</code>, and <code>Label.CENTER</code>.
     * @param text the string that the label presents.
     *        A <code>null</code> value
     *        will be accepted without causing a NullPointerException
     *        to be thrown.
     * @param     alignment   the alignment value.
     */
    public Label(String text, int alignment) {
        this.text = text;
        setAlignment(alignment);
    }

    /**
     * Construct a name for this component.  Called by getName() when the
     * name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (Label.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Gets the current alignment of this label. Possible values are
     * <code>Label.LEFT</code>, <code>Label.RIGHT</code>, and
     * <code>Label.CENTER</code>.
     * @see        java.awt.Label#setAlignment
     */
    public int getAlignment() {
        return alignment;
    }

    /**
     * Sets the alignment for this label to the specified alignment.
     * Possible values are <code>Label.LEFT</code>,
     * <code>Label.RIGHT</code>, and <code>Label.CENTER</code>.
     * @param      alignment    the alignment to be set.
     * @exception  IllegalArgumentException if an improper value for
     *                          <code>alignment</code> is given.
     * @see        java.awt.Label#getAlignment
     */
    public synchronized void setAlignment(int alignment) {
        switch (alignment) {
          case LEFT:
          case CENTER:
          case RIGHT:
            this.alignment = alignment;
            repaint();
            return;
        }
        throw new IllegalArgumentException("improper alignment: " + alignment);
    }

    /**
     * Gets the text of this label.
     * @return     the text of this label, or <code>null</code> if
     *             the text has been set to <code>null</code>.
     * @see        java.awt.Label#setText
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the text for this label to the specified text.
     * @param      text the text that this label displays. If
     *             <code>text</code> is <code>null</code>, it is
     *             treated for display purposes like an empty
     *             string <code>""</code>.
     * @see        java.awt.Label#getText
     */
    public void setText(String text) {
        boolean testvalid = false;
        synchronized (this) {
            if (text != this.text && (this.text == null ||
                                      !this.text.equals(text))) {
                this.text = text;
                testvalid = true;
            }
        }

        // This could change the preferred size of the Component.
        if (testvalid) {
            invalidate();
        }
    }

    /**
     * The peer's size: the text width plus the 14-pixel inset and the line
     * height plus 8, as the desktop label peers report it.
     */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = AwtWidgets.metrics(this);
        String label = text == null ? "" : text;
        return new Dimension(fm.stringWidth(label) + 14, fm.getHeight() + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(AwtWidgets.background(this));
        g.fillRect(0, 0, w, h);
        String label = text;
        if (label == null || label.isEmpty()) return;
        g.setFont(AwtWidgets.font(this));
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(label), x;
        switch (alignment) {
            case CENTER: x = (w - width) / 2; break;
            case RIGHT: x = w - width - 7; break;
            default: x = 7; break;
        }
        g.setColor(AwtWidgets.foreground(this));
        AwtWidgets.text(g, fm, label, x, 0, h);
    }

    /**
     * Returns a string representing the state of this <code>Label</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return     the parameter string of this label
     */
    protected String paramString() {
        String align = "";
        switch (alignment) {
            case LEFT:   align = "left"; break;
            case CENTER: align = "center"; break;
            case RIGHT:  align = "right"; break;
        }
        return super.paramString() + ",align=" + align + ",text=" + text;
    }
}
