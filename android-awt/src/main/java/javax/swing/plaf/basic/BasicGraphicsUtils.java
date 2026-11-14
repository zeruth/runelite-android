/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Convenient util class.
 * <p>
 * The text helpers draw with the component's {@code FontMetrics} directly:
 * the shadow has no {@code SwingUtilities2} text antialiasing state to apply.
 *
 * @author Hans Muller
 */
public class BasicGraphicsUtils
{

    private static final Insets GROOVE_INSETS = new Insets(2, 2, 2, 2);
    private static final Insets ETCHED_INSETS = new Insets(2, 2, 2, 2);

    public static void drawEtchedRect(Graphics g, int x, int y, int w, int h,
                                      Color shadow, Color darkShadow,
                                      Color highlight, Color lightHighlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        g.setColor(shadow);
        g.drawLine(0, 0, w-1, 0);      // outer border, top
        g.drawLine(0, 1, 0, h-2);      // outer border, left

        g.setColor(darkShadow);
        g.drawLine(1, 1, w-3, 1);      // inner border, top
        g.drawLine(1, 2, 1, h-3);      // inner border, left

        g.setColor(lightHighlight);
        g.drawLine(w-1, 0, w-1, h-1);  // outer border, bottom
        g.drawLine(0, h-1, w-1, h-1);  // outer border, right

        g.setColor(highlight);
        g.drawLine(w-2, 1, w-2, h-3);  // inner border, right
        g.drawLine(1, h-2, w-2, h-2);  // inner border, bottom

        g.translate(-x, -y);
        g.setColor(oldColor);
    }


    /**
     * Returns the amount of space taken up by a border drawn by
     * <code>drawEtchedRect()</code>
     *
     * @return  the inset of an etched rect
     */
    public static Insets getEtchedInsets() {
        return ETCHED_INSETS;
    }


    public static void drawGroove(Graphics g, int x, int y, int w, int h,
                                  Color shadow, Color highlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        g.setColor(shadow);
        g.drawRect(0, 0, w-2, h-2);

        g.setColor(highlight);
        g.drawLine(1, h-3, 1, 1);
        g.drawLine(1, 1, w-3, 1);

        g.drawLine(0, h-1, w-1, h-1);
        g.drawLine(w-1, h-1, w-1, 0);

        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    /**
     * Returns the amount of space taken up by a border drawn by
     * <code>drawGroove()</code>
     *
     * @return  the inset of a groove border
     */
    public static Insets getGrooveInsets() {
        return GROOVE_INSETS;
    }


    public static void drawBezel(Graphics g, int x, int y, int w, int h,
                                 boolean isPressed, boolean isDefault,
                                 Color shadow, Color darkShadow,
                                 Color highlight, Color lightHighlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        if (isPressed && isDefault) {
            g.setColor(darkShadow);
            g.drawRect(0, 0, w - 1, h - 1);
            g.setColor(shadow);
            g.drawRect(1, 1, w - 3, h - 3);
        } else if (isPressed) {
            // Already translated: the lowered bezel draws at the origin.
            drawLoweredBezel(g, 0, 0, w, h,
                             shadow, darkShadow, highlight, lightHighlight);
        } else if (isDefault) {
            g.setColor(darkShadow);
            g.drawRect(0, 0, w-1, h-1);

            g.setColor(lightHighlight);
            g.drawLine(1, 1, 1, h-3);
            g.drawLine(2, 1, w-3, 1);

            g.setColor(highlight);
            g.drawLine(2, 2, 2, h-4);
            g.drawLine(3, 2, w-4, 2);

            g.setColor(shadow);
            g.drawLine(2, h-3, w-3, h-3);
            g.drawLine(w-3, 2, w-3, h-4);

            g.setColor(darkShadow);
            g.drawLine(1, h-2, w-2, h-2);
            g.drawLine(w-2, h-2, w-2, 1);
        } else {
            g.setColor(lightHighlight);
            g.drawLine(0, 0, 0, h-1);
            g.drawLine(1, 0, w-2, 0);

            g.setColor(highlight);
            g.drawLine(1, 1, 1, h-3);
            g.drawLine(2, 1, w-3, 1);

            g.setColor(shadow);
            g.drawLine(1, h-2, w-2, h-2);
            g.drawLine(w-2, 1, w-2, h-3);

            g.setColor(darkShadow);
            g.drawLine(0, h-1, w-1, h-1);
            g.drawLine(w-1, h-1, w-1, 0);
        }
        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    public static void drawLoweredBezel(Graphics g, int x, int y, int w, int h,
                                        Color shadow, Color darkShadow,
                                        Color highlight, Color lightHighlight)  {
        g.translate(x, y);

        g.setColor(darkShadow);
        g.drawLine(0, 0, 0, h-1);
        g.drawLine(1, 0, w-2, 0);

        g.setColor(shadow);
        g.drawLine(1, 1, 1, h-2);
        g.drawLine(1, 1, w-3, 1);

        g.setColor(lightHighlight);
        g.drawLine(0, h-1, w-1, h-1);
        g.drawLine(w-1, h-1, w-1, 0);

        g.setColor(highlight);
        g.drawLine(1, h-2, w-2, h-2);
        g.drawLine(w-2, h-2, w-2, 1);

        g.translate(-x, -y);
    }


    /**
     * Draw a string with the graphics {@code g} at location (x,y)
     * just like {@code g.drawString} would. The first occurrence
     * of {@code underlineChar} in text will be underlined.
     * The matching algorithm is not case sensitive.
     *
     * @param g the graphics context
     * @param text the text to draw
     * @param underlinedChar the character to underline, or 0 for none
     * @param x the x coordinate of the text
     * @param y the y coordinate of the baseline
     */
    public static void drawString(Graphics g,String text,int underlinedChar,int x,int y) {
        int index=-1;

        if (underlinedChar != '\0') {
            char uc = Character.toUpperCase((char)underlinedChar);
            char lc = Character.toLowerCase((char)underlinedChar);
            int uci = text.indexOf(uc);
            int lci = text.indexOf(lc);

            if(uci == -1) {
                index = lci;
            }
            else if(lci == -1) {
                index = uci;
            }
            else {
                index = (lci < uci) ? lci : uci;
            }
        }
        drawStringUnderlineCharAt(g, text, index, x, y);
    }

    /**
     * Draw a string with the graphics <code>g</code> at location
     * (<code>x</code>, <code>y</code>)
     * just like <code>g.drawString</code> would.
     * The character at index <code>underlinedIndex</code>
     * in text will be underlined. If <code>index</code> is beyond the
     * bounds of <code>text</code> (including &lt; 0), nothing will be
     * underlined.
     *
     * @param g Graphics to draw with
     * @param text String to draw
     * @param underlinedIndex Index of character in text to underline
     * @param x x coordinate to draw at
     * @param y y coordinate to draw at
     * @since 1.4
     */
    public static void drawStringUnderlineCharAt(Graphics g, String text,
                           int underlinedIndex, int x,int y) {
        if (text == null || text.length() <= 0) {
            return;
        }
        g.drawString(text, x, y);
        if (underlinedIndex >= 0 && underlinedIndex < text.length()) {
            FontMetrics fm = g.getFontMetrics();
            int underlineRectX = x + fm.stringWidth(text.substring(0, underlinedIndex));
            int underlineRectWidth = fm.charWidth(text.charAt(underlinedIndex));
            g.fillRect(underlineRectX, y + 1, underlineRectWidth, 1);
        }
    }

    /**
     * Draws dashed rectangle.
     *
     * @param g an instance of {@code Graphics}
     * @param x an X coordinate
     * @param y an Y coordinate
     * @param width a width of rectangle
     * @param height a height of rectangle
     */
    public static void drawDashedRect(Graphics g,int x,int y,int width,int height) {
        int vx,vy;

        // draw upper and lower horizontal dashes
        for (vx = x; vx < (x + width); vx+=2) {
            g.fillRect(vx, y, 1, 1);
            g.fillRect(vx, y + height-1, 1, 1);
        }

        // draw left and right vertical dashes
        for (vy = y; vy < (y + height); vy+=2) {
            g.fillRect(x, vy, 1, 1);
            g.fillRect(x+width-1, vy, 1, 1);
        }
    }

    public static Dimension getPreferredButtonSize(AbstractButton b, int textIconGap)
    {
        if(b.getComponentCount() > 0) {
            return null;
        }

        Icon icon = b.getIcon();
        String text = b.getText();

        Font font = b.getFont();
        FontMetrics fm = b.getFontMetrics(font);

        Rectangle iconR = new Rectangle();
        Rectangle textR = new Rectangle();
        Rectangle viewR = new Rectangle(Short.MAX_VALUE, Short.MAX_VALUE);

        SwingUtilities.layoutCompoundLabel(
            b, fm, text, icon,
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewR, iconR, textR, (text == null ? 0 : textIconGap)
        );

        /* The preferred size of the button is the size of
         * the text and icon rectangles plus the buttons insets.
         */

        Rectangle r = iconR.union(textR);

        Insets insets = b.getInsets();
        r.width += insets.left + insets.right;
        r.height += insets.top + insets.bottom;

        return r.getSize();
    }

    /**
     * Draws the given string at the specified location.
     *
     * @param c the component that will display the string, may be null
     * @param g the graphics context, must not be null
     * @param string the string to display, may be null
     * @param x the x coordinate to draw the text at
     * @param y the y coordinate to draw the text at
     * @throws NullPointerException if the specified {@code g} is {@code null}
     *
     * @since 9
     */
    // JDK 9+ (9)
    public static void drawString(JComponent c, Graphics2D g, String string,
                                  float x, float y) {
        if (g == null) {
            throw new NullPointerException("g");
        }
        if (string == null || string.length() <= 0) {
            return;
        }
        g.drawString(string, x, y);
    }

    /**
     * Draws the given string at the specified location underlining
     * the specified character.
     *
     * @param c the component that will display the string, may be null
     * @param g the graphics context, must not be null
     * @param string the string to display, may be null
     * @param underlinedIndex index of a a character in the string to underline
     * @param x the x coordinate to draw the text at
     * @param y the y coordinate to draw the text at
     * @throws NullPointerException if the specified {@code g} is {@code null}
     *
     * @since 9
     */
    // JDK 9+ (9)
    public static void drawStringUnderlineCharAt(JComponent c, Graphics2D g,
            String string, int underlinedIndex, float x, float y) {
        if (g == null) {
            throw new NullPointerException("g");
        }
        if (string == null || string.length() <= 0) {
            return;
        }
        g.drawString(string, x, y);
        if (underlinedIndex >= 0 && underlinedIndex < string.length()) {
            FontMetrics fm = g.getFontMetrics();
            float underlineX = x + getStringWidth(c, fm, string.substring(0, underlinedIndex));
            int underlineWidth = fm.charWidth(string.charAt(underlinedIndex));
            g.fillRect(Math.round(underlineX), Math.round(y) + 1, underlineWidth, 1);
        }
    }

    /**
     * Clips the passed in string to the space provided.
     *
     * @param c the component, may be null
     * @param fm the FontMetrics used to measure the string width, must be
     *           obtained from the correct font and graphics. Must not be null.
     * @param string the string to clip, may be null
     * @param availTextWidth the amount of space that the string can be drawn in
     * @return the clipped string that fits in the provided space, an empty
     *         string if the given string argument is {@code null} or empty
     * @throws NullPointerException if the specified {@code fm} is {@code null}
     *
     * @since 9
     */
    // JDK 9+ (9)
    public static String getClippedString(JComponent c, FontMetrics fm,
                                          String string, int availTextWidth) {
        if (fm == null) {
            throw new NullPointerException("fm");
        }
        if (string == null || string.length() <= 0) {
            return "";
        }
        if (fm.stringWidth(string) <= availTextWidth) {
            return string;
        }
        String clipString = "...";
        availTextWidth -= fm.stringWidth(clipString);
        if (availTextWidth <= 0) {
            return clipString;
        }
        int width = 0;
        int nChars;
        for (nChars = 0; nChars < string.length(); nChars++) {
            width += fm.charWidth(string.charAt(nChars));
            if (width > availTextWidth) {
                break;
            }
        }
        return string.substring(0, nChars) + clipString;
    }

    /**
     * Returns the width of the passed in string.
     *
     * @param c the component, may be null
     * @param fm the FontMetrics used to compute the string width,
     *           must be obtained from the correct font and graphics.
     *           Must not be null.
     * @param string the string to compute the width for, may be null
     * @return the width of the given string, 0 if the string is
     *         {@code null} or empty
     * @throws NullPointerException if the specified {@code fm} is {@code null}
     *
     * @since 9
     */
    // JDK 9+ (9)
    public static float getStringWidth(JComponent c, FontMetrics fm, String string) {
        if (fm == null) {
            throw new NullPointerException("fm");
        }
        if (string == null || string.length() <= 0) {
            return 0;
        }
        return fm.stringWidth(string);
    }

    static boolean isLeftToRight( Component c ) {
        return c.getComponentOrientation().isLeftToRight();
    }

    /*
     * Returns true if the given mouse event's modifiers include the
     * menu shortcut key (Ctrl on Windows and Linux).
     */
    static boolean isMenuShortcutKeyDown(InputEvent event) {
        return (event.getModifiers() &
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0;
    }
}
