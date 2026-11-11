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

public class JToggleButton extends AbstractButton {
    private static final long serialVersionUID = -3128140058214808885L;

    public static class ToggleButtonModel extends DefaultButtonModel {
        private static final long serialVersionUID = 1L;
        @Override public void setSelected(boolean selected) {
            ButtonGroup group = getGroup();
            if (group != null) { group.setSelected(this, selected); selected = group.isSelected(this); }
            super.setSelected(selected);
        }
        @Override public void setPressed(boolean pressed) {
            if (isPressed() == pressed || !isEnabled()) return;
            if (!pressed && isArmed()) setSelected(!isSelected());
            super.setPressed(pressed);
        }
    }

    public JToggleButton() { this(null, null, false); }
    public JToggleButton(Icon icon) { this(null, icon, false); }
    public JToggleButton(Icon icon, boolean selected) { this(null, icon, selected); }
    public JToggleButton(String text) { this(text, null, false); }
    public JToggleButton(String text, boolean selected) { this(text, null, selected); }
    public JToggleButton(Action a) { this(); setAction(a); }
    public JToggleButton(String text, Icon icon) { this(text, icon, false); }
    public JToggleButton(String text, Icon icon, boolean selected) {
        setModel(new ToggleButtonModel());
        setText(text); setIcon(icon); setSelected(selected);
    }

    @Override boolean shouldUpdateSelectedStateFromAction() { return true; }
}
