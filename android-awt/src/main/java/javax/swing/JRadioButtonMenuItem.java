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
public class JRadioButtonMenuItem extends JMenuItem implements javax.accessibility.Accessible {
    private static final String uiClassID = "RadioButtonMenuItemUI";
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJRadioButtonMenuItem();
        }
        return accessibleContext;
    }

    /** Accessibility support for radio button menu items: role RADIO_BUTTON. */
    protected class AccessibleJRadioButtonMenuItem extends AccessibleJMenuItem {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.RADIO_BUTTON;
        }
    }
    private static final long serialVersionUID=1L;
    public JRadioButtonMenuItem() { setModel(new JToggleButton.ToggleButtonModel()); }
    public JRadioButtonMenuItem(String text) { this(text,null,false); }
    public JRadioButtonMenuItem(Icon icon) { this(null,icon,false); }
    public JRadioButtonMenuItem(String text,Icon icon) { this(text,icon,false); }
    public JRadioButtonMenuItem(String text,boolean selected) { this(text,null,selected); }
    public JRadioButtonMenuItem(String text,Icon icon,boolean selected) { this();setText(text);setIcon(icon);setSelected(selected); }
    public JRadioButtonMenuItem(Action action) { this();setAction(action); }
    protected boolean shouldUpdateSelectedStateFromAction() { return true; }
    public boolean getState() { return isSelected(); }
    public void setState(boolean value) { setSelected(value); }
}
