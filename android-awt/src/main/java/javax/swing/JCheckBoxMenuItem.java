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
public class JCheckBoxMenuItem extends JMenuItem implements javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJCheckBoxMenuItem();
        }
        return accessibleContext;
    }

    /** Accessibility support for check box menu items: role CHECK_BOX. */
    protected class AccessibleJCheckBoxMenuItem extends AccessibleJMenuItem {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.CHECK_BOX;
        }
    }
    private static final long serialVersionUID=1L;
    public JCheckBoxMenuItem() { setModel(new JToggleButton.ToggleButtonModel()); }
    public JCheckBoxMenuItem(String text) { this(text,null,false); }
    public JCheckBoxMenuItem(Icon icon) { this(null,icon,false); }
    public JCheckBoxMenuItem(String text,Icon icon) { this(text,icon,false); }
    public JCheckBoxMenuItem(String text,boolean selected) { this(text,null,selected); }
    public JCheckBoxMenuItem(String text,Icon icon,boolean selected) { this();setText(text);setIcon(icon);setSelected(selected); }
    public JCheckBoxMenuItem(Action action) { this();setAction(action); }
    protected boolean shouldUpdateSelectedStateFromAction() { return true; }
    public boolean getState() { return isSelected(); }
    public void setState(boolean value) { setSelected(value); }
}
