/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.swing;

import java.awt.FlowLayout;
import java.awt.LayoutManager;

public class JPanel extends JComponent implements javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel();
        }
        return accessibleContext;
    }

    /** Accessibility support for panels: role PANEL. */
    protected class AccessibleJPanel extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.PANEL;
        }
    }
    private static final long serialVersionUID = 1L;

    public JPanel() { this(new FlowLayout(), true); }
    public JPanel(LayoutManager layout) { this(layout, true); }
    public JPanel(boolean isDoubleBuffered) { this(new FlowLayout(), isDoubleBuffered); }
    public JPanel(LayoutManager layout, boolean isDoubleBuffered) {
        setLayout(layout);
        setDoubleBuffered(isDoubleBuffered);
        // As 8u: a UI property, so a look and feel may still install its own opacity,
        // and the panel takes its colors and font from the PanelUI like on desktop.
        setUIProperty("opaque", Boolean.TRUE);
        updateUI();
    }

    /** Resets the UI property with a value from the current look and feel. */
    public void updateUI() {
        setUI((javax.swing.plaf.PanelUI) UIManager.getUI(this));
    }
    // JComponent's sizing of the JDK: the delegate answers first and a null answer (every
    // BasicPanelUI) falls back to the layout manager. The shadow JComponent skips the
    // delegate, so the panel asks it here.
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (!isPreferredSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getPreferredSize(this);
            if (d != null) return d;
        }
        return super.getPreferredSize();
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        if (!isMinimumSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getMinimumSize(this);
            if (d != null) return d;
        }
        return super.getMinimumSize();
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        if (!isMaximumSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getMaximumSize(this);
            if (d != null) return d;
        }
        return super.getMaximumSize();
    }

    public javax.swing.plaf.PanelUI getUI(){return (javax.swing.plaf.PanelUI)ui;}
    public void setUI(javax.swing.plaf.PanelUI value){super.setUI(value);}
    public String getUIClassID(){return "PanelUI";}
}
