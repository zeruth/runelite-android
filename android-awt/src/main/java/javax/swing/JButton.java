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

public class JButton extends AbstractButton implements javax.accessibility.Accessible {
    private static final String uiClassID = "ButtonUI";
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJButton();
        }
        return accessibleContext;
    }

    /** Accessibility support for buttons: role PUSH_BUTTON. */
    protected class AccessibleJButton extends AccessibleAbstractButton {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.PUSH_BUTTON;
        }
    }
    private static final long serialVersionUID = -1602050342864801480L;

    public JButton() { this(null, null); }
    public JButton(Icon icon) { this(null, icon); }
    public JButton(String text) { this(text, null); }
    public JButton(Action a) { this(); setAction(a); }
    public JButton(String text, Icon icon) {
        // 8u sets a fresh DefaultButtonModel here; AbstractButton's field already holds one.
        init(text, icon);
    }

    /**
     * Resets the UI property to a value from the current look and
     * feel.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        setUI((javax.swing.plaf.ButtonUI) UIManager.getUI(this));
    }

    private boolean defaultCapable=true;
    public boolean isDefaultButton() { JRootPane root=SwingUtilities.getRootPane(this);return root!=null&&root.getDefaultButton()==this; }
    public boolean isDefaultCapable() { return defaultCapable; }
    public void setDefaultCapable(boolean value) { boolean old=defaultCapable;defaultCapable=value;firePropertyChange("defaultCapable",old,value); }

    /** Typed overload — plugins (e.g. via SwingUtil.removeButtonDecorations) call this. */
    public void setUI(javax.swing.plaf.ButtonUI ui) { super.setUI(ui); }
}
