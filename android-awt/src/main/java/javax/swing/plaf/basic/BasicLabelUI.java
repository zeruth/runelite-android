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

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;

/**
 * A Windows L&amp;F implementation of LabelUI.  This implementation
 * is completely static, i.e. there's only one UIView implementation
 * that's shared by all JLabel objects.
 * <p>
 * The shadow JLabel paints and sizes itself (its measurement is cached for the sidebar's
 * per-frame layout), so paint hands back to the component; the layout and baseline API is
 * the JDK's.
 *
 * @author Hans Muller
 */
public class BasicLabelUI extends LabelUI
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

    @Override
    public void paint(java.awt.Graphics g, JComponent c) {
        c.hostPaintDefaultComponent(g);
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
