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

public class JSeparator extends JComponent implements SwingConstants, javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJSeparator();
        }
        return accessibleContext;
    }

    /** Accessibility support for separators: role SEPARATOR. */
    protected class AccessibleJSeparator extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.SEPARATOR;
        }
    }
    private static final long serialVersionUID = 1L;
    private int orientation;

    public JSeparator() { this(HORIZONTAL); }
    public JSeparator(int orientation) { this.orientation = orientation; }

    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) { if(orientation!=HORIZONTAL&&orientation!=VERTICAL)throw new IllegalArgumentException("orientation");int old=this.orientation;this.orientation=orientation;firePropertyChange("orientation",old,orientation);revalidate();repaint(); }
    public javax.swing.plaf.SeparatorUI getUI(){return (javax.swing.plaf.SeparatorUI)ui;}
    public void setUI(javax.swing.plaf.SeparatorUI value){super.setUI(value);}
    public String getUIClassID(){return "SeparatorUI";}
    public java.awt.Dimension getPreferredSize(){if(isPreferredSizeSet())return super.getPreferredSize();return ui==null?new javax.swing.plaf.basic.BasicSeparatorUI().getPreferredSize(this):ui.getPreferredSize(this);}
    protected void paintComponent(java.awt.Graphics graphics){if(paintDelegate(graphics))return;new javax.swing.plaf.basic.BasicSeparatorUI().paint(graphics,this);}
}
