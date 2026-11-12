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
        setText(text); setIcon(icon);
    }

    private boolean defaultCapable=true;
    public boolean isDefaultButton() { JRootPane root=SwingUtilities.getRootPane(this);return root!=null&&root.getDefaultButton()==this; }
    public boolean isDefaultCapable() { return defaultCapable; }
    public void setDefaultCapable(boolean value) { boolean old=defaultCapable;defaultCapable=value;firePropertyChange("defaultCapable",old,value); }

    /** Typed overload — plugins (e.g. via SwingUtil.removeButtonDecorations) call this. */
    public void setUI(javax.swing.plaf.ButtonUI ui) { super.setUI(ui); }

    /**
     * Paint a flat button: background fill (if opaque or content area filled), a 1-px
     * border (if borderPainted), then the icon and centered text. Without this the
     * Reset/Back buttons in plugin config panels existed in the layout but rendered as
     * empty rectangles — invisible because the parent's gray bg showed through.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // SwingUtil.removeButtonDecorations sets FlatLaf.styleClass = "iconButton ...".
        // Those buttons are meant to render as bare icons (pin star, config gear) with
        // no surrounding chrome — skip the bg fill and border so they don't render as
        // visible gray boxes inside dense rows like the plugin list.
        Object styleClass = getClientProperty("FlatLaf.styleClass");
        boolean iconOnly = styleClass != null && styleClass.toString().contains("iconButton");
        boolean pressed = getModel() != null && getModel().isPressed();
        java.awt.Color bg = getBackground();
        if (bg == null) bg = new java.awt.Color(0x3E, 0x3E, 0x3E);
        if (!iconOnly && isContentAreaFilled()) {
            g.setColor(pressed ? bg.darker() : bg);
            g.fillRect(0, 0, w, h);
        }
        if (!iconOnly && isBorderPainted()) {
            g.setColor(bg.darker());
            g.drawRect(0, 0, w - 1, h - 1);
        }
        Icon ic = getIcon();
        java.awt.FontMetrics fm = g.getFontMetrics(getFont());
        int textW = (text == null || text.isEmpty()) ? 0 : fm.stringWidth(text);
        int textH = (text == null || text.isEmpty()) ? 0 : fm.getHeight();
        int iconW = ic != null ? ic.getIconWidth() : 0;
        int iconH = ic != null ? ic.getIconHeight() : 0;
        int gap = (iconW > 0 && textW > 0) ? 4 : 0;
        int contentW = iconW + gap + textW;
        int x = (w - contentW) / 2;
        if (ic != null) {
            int iy = (h - iconH) / 2;
            ic.paintIcon(this, g, x, iy);
            x += iconW + gap;
        }
        if (textW > 0) {
            java.awt.Color fg = getForeground();
            if (fg == null) fg = java.awt.Color.WHITE;
            g.setColor(isEnabled() ? fg : fg.darker());
            g.setFont(getFont());
            int baseline = (h - textH) / 2 + fm.getAscent();
            g.drawString(text, x, baseline);
        }
    }
}
