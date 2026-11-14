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
package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;

/**
 * A Windows L&amp;F implementation of LabelUI.  This implementation
 * is completely static, i.e. there's only one UIView implementation
 * that's shared by all JLabel objects.
 * <p>
 * Painting is the JDK's. The shadow has no BasicHTML View: an html label is painted by
 * {@link JLabel#hostPaintHtml}, the renderer its measurement used, and is measured by the
 * label for the same reason. JLabel.getPreferredSize asks a delegate that overrides
 * getPreferredSize, as the JDK does; one that inherits this measurement is answered from
 * the label's cached layoutCompoundLabel measurement, which the sidebar's per-frame layout
 * needs. Plain text measured here is the JDK's layoutCL measurement. The property change
 * listener has no BasicHTML View or labelFor mnemonic binding to refresh (see
 * {@link #propertyChange}).
 *
 * @author Hans Muller
 */
public class BasicLabelUI extends LabelUI implements PropertyChangeListener
{
   /**
    * The default <code>BasicLabelUI</code> instance. This field might
    * not be used. To change the default instance use a subclass which
    * overrides the <code>createUI</code> method, and place that class
    * name in defaults table under the key "LabelUI".
    */
    protected static BasicLabelUI labelUI = new BasicLabelUI();

    private Rectangle paintIconR = new Rectangle();
    private Rectangle paintTextR = new Rectangle();

    public BasicLabelUI() {
    }

    public static ComponentUI createUI(JComponent c) {
        return labelUI;
    }

    /**
     * Forwards the call to SwingUtilities.layoutCompoundLabel().
     * This method is here so that a subclass could do Label specific
     * layout and to shorten the method name a little.
     *
     * @see SwingUtilities#layoutCompoundLabel
     */
    protected String layoutCL(
        JLabel label,
        FontMetrics fontMetrics,
        String text,
        Icon icon,
        Rectangle viewR,
        Rectangle iconR,
        Rectangle textR)
    {
        return SwingUtilities.layoutCompoundLabel(
            (JComponent) label,
            fontMetrics,
            text,
            icon,
            label.getVerticalAlignment(),
            label.getHorizontalAlignment(),
            label.getVerticalTextPosition(),
            label.getHorizontalTextPosition(),
            viewR,
            iconR,
            textR,
            label.getIconTextGap());
    }

    /**
     * Paint clippedText at textX, textY with the labels foreground color.
     *
     * @see #paint
     * @see #paintDisabledText
     */
    protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY)
    {
        int mnemIndex = l.getDisplayedMnemonicIndex();
        g.setColor(l.getForeground());
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, s, mnemIndex,
                                                     textX, textY);
    }


    /**
     * Paint clippedText at textX, textY with background.lighter() and then
     * shifted down and to the right by one pixel with background.darker().
     *
     * @see #paint
     * @see #paintEnabledText
     */
    protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY)
    {
        int accChar = l.getDisplayedMnemonicIndex();
        Color background = l.getBackground();
        g.setColor(background.brighter());
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, s, accChar,
                                                     textX + 1, textY + 1);
        g.setColor(background.darker());
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, s, accChar,
                                                     textX, textY);
    }

    /**
     * Paints the label text with the foreground color, if the label is opaque
     * then paints the entire background with the background color. The Label
     * text is drawn by {@link #paintEnabledText} or {@link #paintDisabledText}.
     * The locations of the label parts are computed by {@link #layoutCL}.
     *
     * @see #paintEnabledText
     * @see #paintDisabledText
     * @see #layoutCL
     */
    public void paint(Graphics g, JComponent c)
    {
        JLabel label = (JLabel)c;
        String text = label.getText();
        Icon icon = (label.isEnabled()) ? label.getIcon() : label.getDisabledIcon();

        if ((icon == null) && (text == null)) {
            return;
        }

        // Where the JDK paints the BasicHTML View in the text rectangle.
        if (label.hostIsHtml()) {
            label.hostPaintHtml(g);
            return;
        }

        FontMetrics fm = label.getFont() != null ? label.getFontMetrics(label.getFont())
                                                 : g.getFontMetrics();
        String clippedText = layout(label, fm, c.getWidth(), c.getHeight());

        if (icon != null) {
            icon.paintIcon(c, g, paintIconR.x, paintIconR.y);
        }

        if (text != null) {
            int textX = paintTextR.x;
            int textY = paintTextR.y + fm.getAscent();

            if (label.isEnabled()) {
                paintEnabledText(label, g, clippedText, textX, textY);
            }
            else {
                paintDisabledText(label, g, clippedText, textX, textY);
            }
        }
    }

    public void installUI(JComponent c) {
        installDefaults((JLabel)c);
        installComponents((JLabel)c);
        installListeners((JLabel)c);
        installKeyboardActions((JLabel)c);
    }


    public void uninstallUI(JComponent c) {
        uninstallDefaults((JLabel) c);
        uninstallComponents((JLabel) c);
        uninstallListeners((JLabel) c);
        uninstallKeyboardActions((JLabel) c);
    }

     protected void installDefaults(JLabel c){
         LookAndFeel.installColorsAndFont(c, "Label.background", "Label.foreground", "Label.font");
         LookAndFeel.installProperty(c, "opaque", Boolean.FALSE);
      }

    protected void installListeners(JLabel c){
        c.addPropertyChangeListener(this);
    }

    /** As 8u, less BasicHTML.updateRenderer (see the class comment). */
    protected void installComponents(JLabel c){
        c.setInheritsPopupMenu(true);
    }

    /**
     * 8u binds the displayed mnemonic to a focus action on the labelFor component; the
     * shadow has no mnemonic key dispatch, so there is nothing to bind.
     */
    protected void installKeyboardActions(JLabel l) {
    }

    protected void uninstallDefaults(JLabel c){
    }

    protected void uninstallListeners(JLabel c){
        c.removePropertyChangeListener(this);
    }

    protected void uninstallComponents(JLabel c){
    }

    protected void uninstallKeyboardActions(JLabel c) {
    }

    /**
     * The JDK's measurement for icon and plain text; html text is measured by the label's
     * own renderer, the one {@link JLabel#hostPaintHtml} paints with.
     */
    public Dimension getPreferredSize(JComponent c)
    {
        JLabel label = (JLabel)c;
        String text = label.getText();
        Icon icon = (label.isEnabled()) ? label.getIcon() :
                                          label.getDisabledIcon();
        Insets insets = label.getInsets(null);
        Font font = label.getFont();

        int dx = insets.left + insets.right;
        int dy = insets.top + insets.bottom;

        if ((icon == null) &&
            ((text == null) ||
             ((text != null) && (font == null)))) {
            return new Dimension(dx, dy);
        }
        else if ((text != null) && label.hostIsHtml()) {
            return label.hostLabelPreferredSize();
        }
        else if ((text == null) || ((icon != null) && (font == null))) {
            return new Dimension(icon.getIconWidth() + dx,
                                 icon.getIconHeight() + dy);
        }
        else {
            FontMetrics fm = label.getFontMetrics(font);
            Rectangle iconR = new Rectangle();
            Rectangle textR = new Rectangle();
            Rectangle viewR = new Rectangle();

            iconR.x = iconR.y = iconR.width = iconR.height = 0;
            textR.x = textR.y = textR.width = textR.height = 0;
            viewR.x = dx;
            viewR.y = dy;
            viewR.width = viewR.height = Short.MAX_VALUE;

            layoutCL(label, fm, text, icon, viewR, iconR, textR);
            int x1 = Math.min(iconR.x, textR.x);
            int x2 = Math.max(iconR.x + iconR.width, textR.x + textR.width);
            int y1 = Math.min(iconR.y, textR.y);
            int y2 = Math.max(iconR.y + iconR.height, textR.y + textR.height);
            Dimension rv = new Dimension(x2 - x1, y2 - y1);

            rv.width += dx;
            rv.height += dy;
            return rv;
        }
    }

    /**
     * 8u refreshes the BasicHTML View on text, font and foreground changes and rebinds the
     * labelFor mnemonic on labelFor and displayedMnemonic changes. Deliberate no-op: the
     * shadow label re-lays out its html itself on those setters, and has no mnemonic key
     * dispatch to bind.
     */
    public void propertyChange(PropertyChangeEvent e) {
    }

    /**
     * @return getPreferredSize(c)
     */
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    /**
     * @return getPreferredSize(c)
     */
    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    private String layout(JLabel label, FontMetrics fm,
                          int width, int height) {
        Insets insets = label.getInsets(null);
        String text = label.getText();
        Icon icon = (label.isEnabled()) ? label.getIcon() :
                                          label.getDisabledIcon();
        Rectangle paintViewR = new Rectangle();
        paintViewR.x = insets.left;
        paintViewR.y = insets.top;
        paintViewR.width = width - (insets.left + insets.right);
        paintViewR.height = height - (insets.top + insets.bottom);
        paintIconR.x = paintIconR.y = paintIconR.width = paintIconR.height = 0;
        paintTextR.x = paintTextR.y = paintTextR.width = paintTextR.height = 0;
        return layoutCL(label, fm, text, icon, paintViewR, paintIconR,
                        paintTextR);
    }

    /**
     * Returns the baseline.
     *
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @see javax.swing.JComponent#getBaseline(int, int)
     * @since 1.6
     */
    public int getBaseline(JComponent c, int width, int height) {
        super.getBaseline(c, width, height);
        JLabel label = (JLabel)c;
        String text = label.getText();
        if (text == null || "".equals(text) || label.getFont() == null) {
            return -1;
        }
        FontMetrics fm = label.getFontMetrics(label.getFont());
        layout(label, fm, width, height);
        // BasicHTML.getBaseline without an html View: the first line's ascent.
        return paintTextR.y + fm.getAscent();
    }

    /**
     * Returns an enum indicating how the baseline of the component
     * changes as the size changes.
     *
     * @throws NullPointerException {@inheritDoc}
     * @see javax.swing.JComponent#getBaseline(int, int)
     * @since 1.6
     */
    public Component.BaselineResizeBehavior getBaselineResizeBehavior(
            JComponent c) {
        super.getBaselineResizeBehavior(c);
        if (isHtml((JLabel) c)) {
            return Component.BaselineResizeBehavior.OTHER;
        }
        switch(((JLabel)c).getVerticalAlignment()) {
        case JLabel.TOP:
            return Component.BaselineResizeBehavior.CONSTANT_ASCENT;
        case JLabel.BOTTOM:
            return Component.BaselineResizeBehavior.CONSTANT_DESCENT;
        case JLabel.CENTER:
            return Component.BaselineResizeBehavior.CENTER_OFFSET;
        }
        return Component.BaselineResizeBehavior.OTHER;
    }

    /** Where the JDK checks for the BasicHTML View the label would carry. */
    private static boolean isHtml(JLabel label) {
        String s = label.getText();
        return s != null && s.length() >= 6 && s.charAt(0) == '<'
                && s.substring(0, 6).equalsIgnoreCase("<html>")
                && !Boolean.TRUE.equals(label.getClientProperty("html.disable"));
    }
}
