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
package javax.swing;

import java.awt.Dimension;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ToolTipUI;
import javax.swing.plaf.basic.BasicToolTipUI;

/**
 * Used to display a "Tip" for a Component. Typically components provide api
 * to automate the process of using <code>ToolTip</code>s.
 * For example, any Swing component can use the <code>JComponent</code>
 * <code>setToolTipText</code> method to specify the text
 * for a standard tooltip.
 *
 * <p>
 * Ported from OpenJDK 8u. When the look and feel has no {@code ToolTipUI}
 * registered the tip falls back to {@link BasicToolTipUI}, so a tip always
 * has a delegate to size and paint it.
 *
 * @see JComponent#setToolTipText
 * @see JComponent#createToolTip
 * @author Dave Moore
 * @author Rich Shiavi
 */
@SuppressWarnings("serial")
public class JToolTip extends JComponent {
    /**
     * @see #getUIClassID
     */
    private static final String uiClassID = "ToolTipUI";

    String tipText;
    JComponent component;

    /** Creates a tool tip. */
    public JToolTip() {
        setOpaque(true);
        updateUI();
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the <code>ToolTipUI</code> object that renders this component
     */
    public ToolTipUI getUI() {
        return (ToolTipUI)ui;
    }

    /**
     * Resets the UI property to a value from the current look and feel.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        ComponentUI delegate = UIManager.getUI(this);
        setUI(delegate instanceof ToolTipUI ? delegate : BasicToolTipUI.createUI(this));
    }

    /**
     * Returns the name of the L&amp;F class that renders this component.
     *
     * @return the string "ToolTipUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Sets the text to show when the tool tip is displayed.
     * The string <code>tipText</code> may be <code>null</code>.
     *
     * @param tipText the <code>String</code> to display
     */
    public void setTipText(String tipText) {
        String oldValue = this.tipText;
        this.tipText = tipText;
        firePropertyChange("tiptext", oldValue, tipText);

        if (!java.util.Objects.equals(oldValue, tipText)) {
            revalidate();
            repaint();
        }
    }

    /**
     * Returns the text that is shown when the tool tip is displayed.
     * The returned value may be <code>null</code>.
     *
     * @return the <code>String</code> that is displayed
     */
    public String getTipText() {
        return tipText;
    }

    /**
     * Specifies the component that the tooltip describes.
     * The component <code>c</code> may be <code>null</code>
     * and will have no effect.
     *
     * @param c the <code>JComponent</code> being described
     * @see JComponent#createToolTip
     */
    public void setComponent(JComponent c) {
        JComponent oldValue = this.component;

        component = c;
        firePropertyChange("component", oldValue, c);
    }

    /**
     * Returns the component the tooltip applies to.
     * The returned value may be <code>null</code>.
     *
     * @return the component that the tooltip describes
     *
     * @see JComponent#createToolTip
     */
    public JComponent getComponent() {
        return component;
    }

    /**
     * Always returns true since tooltips, by definition,
     * should always be on top of all other windows.
     */
    // package private
    boolean alwaysOnTop() {
        return true;
    }

    // The shadow JComponent does not ask its delegate for a size; the JDK's does,
    // and a tip is sized by nothing but its UI.
    @Override
    public Dimension getPreferredSize() {
        if (!isPreferredSizeSet() && ui != null) {
            Dimension d = ui.getPreferredSize(this);
            if (d != null) {
                return d;
            }
        }
        return super.getPreferredSize();
    }

    /**
     * Returns a string representation of this <code>JToolTip</code>.
     *
     * @return  a string representation of this <code>JToolTip</code>.
     */
    protected String paramString() {
        String tipTextString = (tipText != null ?
                                tipText : "");

        // The shadow Component has no paramString of its own to extend.
        return "tipText=" + tipTextString;
    }
}
