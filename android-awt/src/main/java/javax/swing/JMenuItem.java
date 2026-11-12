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

public class JMenuItem extends AbstractButton implements javax.accessibility.Accessible, MenuElement {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJMenuItem();
        }
        return accessibleContext;
    }

    /** Accessibility support for menu items: role MENU_ITEM. */
    protected class AccessibleJMenuItem extends AccessibleAbstractButton {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.MENU_ITEM;
        }
    }
    private static final long serialVersionUID = 1L;

    public JMenuItem() {}
    public JMenuItem(String text) { setText(text); }
    public JMenuItem(Icon icon) { setIcon(icon); }
    public JMenuItem(Action a) { this(); setAction(a); }
    public JMenuItem(String text, Icon icon) { setText(text); setIcon(icon); }
    public JMenuItem(String text, int mnemonic) { setText(text); setMnemonic(mnemonic); }

    private KeyStroke accelerator;
    public KeyStroke getAccelerator() { return accelerator; }
    public void setAccelerator(KeyStroke keyStroke) { this.accelerator = keyStroke; }

    // Swing's JMenuItem takes only SMALL_ICON from its Action (never LARGE_ICON_KEY) and
    // also picks up ACCELERATOR_KEY.
    @Override
    void setIconFromAction(Action a) {
        setIcon(a == null ? null : (Icon) a.getValue(Action.SMALL_ICON));
    }

    @Override
    protected void configurePropertiesFromAction(Action a) {
        super.configurePropertiesFromAction(a);
        Object ks = a == null ? null : a.getValue(Action.ACCELERATOR_KEY);
        setAccelerator(ks instanceof KeyStroke ? (KeyStroke) ks : null);
    }

    @Override
    protected void actionPropertyChanged(Action a, String propertyName) {
        if (Action.LARGE_ICON_KEY.equals(propertyName)) return;
        if (Action.ACCELERATOR_KEY.equals(propertyName)) {
            Object ks = a.getValue(Action.ACCELERATOR_KEY);
            setAccelerator(ks instanceof KeyStroke ? (KeyStroke) ks : null);
            return;
        }
        super.actionPropertyChanged(a, propertyName);
    }
    public boolean isArmed() { return getModel().isArmed(); }
    public void setArmed(boolean b) { getModel().setArmed(b); }

    // MenuElement — a leaf item has no sub-elements; JMenu overrides getSubElements().
    @Override public void menuSelectionChanged(boolean isIncluded) {}
    @Override public MenuElement[] getSubElements() { return new MenuElement[0]; }
    @Override public java.awt.Component getComponent() { return this; }
}
