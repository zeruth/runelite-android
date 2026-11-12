/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ToolTipUI;

/**
 * Standard tool tip L&amp;F.
 *
 * <p>
 * Ported from OpenJDK 8u. The shadow has no {@code BasicHTML} view tree, so
 * an {@code <html>} tip is laid out as plain lines: tags are dropped and
 * {@code <br>}, {@code <p>}, {@code <div>} and {@code <li>} start a new line.
 * Without look and feel defaults the tip uses the JDK's Basic colours.
 *
 * @author Dave Moore
 */
public class BasicToolTipUI extends ToolTipUI
{
    static BasicToolTipUI sharedInstance = new BasicToolTipUI();

    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<\\s*(br|p|div|li)\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private PropertyChangeListener propertyChangeListener;

    public static ComponentUI createUI(JComponent c) {
        return sharedInstance;
    }

    public BasicToolTipUI() {
        super();
    }

    public void installUI(JComponent c) {
        installDefaults(c);
        installComponents(c);
        installListeners(c);
    }

    public void uninstallUI(JComponent c) {
        // REMIND: this is NOT getting called
        uninstallDefaults(c);
        uninstallComponents(c);
        uninstallListeners(c);
    }

    protected void installDefaults(JComponent c){
        LookAndFeel.installColorsAndFont(c, "ToolTip.background",
                                         "ToolTip.foreground",
                                         "ToolTip.font");
        // The JDK's BasicLookAndFeel always supplies these; a bare shadow may not.
        if (c.getBackground() == null) {
            c.setBackground(new ColorUIResource(255, 255, 225));
        }
        if (c.getForeground() == null) {
            c.setForeground(new ColorUIResource(Color.BLACK));
        }
        LookAndFeel.installProperty(c, "opaque", Boolean.TRUE);
        componentChanged(c);
    }

   protected void uninstallDefaults(JComponent c){
        LookAndFeel.uninstallBorder(c);
    }

    /* Unfortunately this has to remain private until we can make API additions.
     */
    private void installComponents(JComponent c){
    }

    /* Unfortunately this has to remain private until we can make API additions.
     */
    private void uninstallComponents(JComponent c){
    }

    protected void installListeners(JComponent c) {
        propertyChangeListener = createPropertyChangeListener(c);

        c.addPropertyChangeListener(propertyChangeListener);
    }

    protected void uninstallListeners(JComponent c) {
        c.removePropertyChangeListener(propertyChangeListener);

        propertyChangeListener = null;
    }

    /* Unfortunately this has to remain private until we can make API additions.
     */
    private PropertyChangeListener createPropertyChangeListener(JComponent c) {
        if (sharedPropertyChangedListener == null) {
            sharedPropertyChangedListener = new PropertyChangeHandler();
        }
        return sharedPropertyChangedListener;
    }

    private static PropertyChangeListener sharedPropertyChangedListener;

    public void paint(Graphics g, JComponent c) {
        Font font = font(c);
        FontMetrics metrics = c.getFontMetrics(font);
        Dimension size = c.getSize();

        g.setColor(c.getForeground());
        g.setFont(font);
        Insets insets = c.getInsets();
        int x = insets.left + 3;
        int y = insets.top + metrics.getAscent();
        for (String line : lines(((JToolTip)c).getTipText())) {
            g.drawString(line, x, y);
            y += metrics.getHeight();
        }
    }

    public Dimension getPreferredSize(JComponent c) {
        Font font = font(c);
        FontMetrics fm = c.getFontMetrics(font);
        Insets insets = c.getInsets();

        Dimension prefSize = new Dimension(insets.left+insets.right,
                                           insets.top+insets.bottom);
        List<String> lines = lines(((JToolTip)c).getTipText());
        if (!lines.isEmpty()) {
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, fm.stringWidth(line));
            }
            prefSize.width += width + 6;
            prefSize.height += fm.getHeight() * lines.size();
        }
        return prefSize;
    }

    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    /**
     * Invoked when the <code>JCompoment</code> associated with the
     * <code>JToolTip</code> has changed, or at initialization time. This
     * should update any state dependant upon the <code>JComponent</code>.
     *
     * @param c the JToolTip the JComponent has changed on.
     */
    private void componentChanged(JComponent c) {
        JComponent comp = ((JToolTip)c).getComponent();

        if (comp != null && !(comp.isEnabled())) {
            // For better backward compatibility, only install inactive
            // properties if they are defined.
            if (javax.swing.UIManager.getBorder("ToolTip.borderInactive") != null) {
                LookAndFeel.installBorder(c, "ToolTip.borderInactive");
            }
            else {
                LookAndFeel.installBorder(c, "ToolTip.border");
            }
            if (javax.swing.UIManager.getColor("ToolTip.backgroundInactive") != null) {
                LookAndFeel.installColors(c,"ToolTip.backgroundInactive",
                                          "ToolTip.foregroundInactive");
            }
            else {
                LookAndFeel.installColors(c,"ToolTip.background",
                                          "ToolTip.foreground");
            }
        } else {
            LookAndFeel.installBorder(c, "ToolTip.border");
            LookAndFeel.installColors(c, "ToolTip.background",
                                      "ToolTip.foreground");
        }
    }

    private static Font font(JComponent c) {
        Font font = c.getFont();
        return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /** The tip's text lines, empty when there is nothing to show. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (text.regionMatches(true, 0, "<html", 0, 5)) {
            String broken = LINE_BREAK.matcher(text).replaceAll("\n");
            String plain = TAG.matcher(broken).replaceAll("")
                    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&amp;", "&");
            for (String line : plain.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } else {
            lines.add(text);
        }
        return lines;
    }

    private static class PropertyChangeHandler implements
                                 PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            String name = e.getPropertyName();
            if (name.equals("tiptext") || "font".equals(name) ||
                "foreground".equals(name)) {
                JToolTip tip = ((JToolTip) e.getSource());
                tip.revalidate();
                tip.repaint();
            }
            else if ("component".equals(name)) {
                JToolTip tip = ((JToolTip) e.getSource());

                if (tip.getUI() instanceof BasicToolTipUI) {
                    ((BasicToolTipUI)tip.getUI()).componentChanged(tip);
                }
            }
        }
    }
}
