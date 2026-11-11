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

public class JCheckBox extends JToggleButton {
    private static final long serialVersionUID = 1L;

    public JCheckBox() { this(null, null, false); }
    public JCheckBox(Icon icon) { this(null, icon, false); }
    public JCheckBox(Icon icon, boolean selected) { this(null, icon, selected); }
    public JCheckBox(String text) { this(text, null, false); }
    public JCheckBox(String text, boolean selected) { this(text, null, selected); }
    public JCheckBox(Action a) { this(); setAction(a); }
    public JCheckBox(String text, Icon icon) { this(text, icon, false); }
    public JCheckBox(String text, Icon icon, boolean selected) { super(text, icon, selected); }

    private static final int BOX_SIZE = 14;

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        // Don't call AbstractButton.paintComponent because we draw our own box + text.
        if (getWidth() <= 0 || getHeight() <= 0) return;
        java.awt.Color saved = g.getColor();
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        // Checkbox indicator on the left, centered vertically.
        int boxY = (getHeight() - BOX_SIZE) / 2;
        int boxX = 2;
        g.setColor(new java.awt.Color(0x60636A));
        g.fillRect(boxX, boxY, BOX_SIZE, BOX_SIZE);
        g.setColor(new java.awt.Color(0x9095A0));
        g.drawRect(boxX, boxY, BOX_SIZE - 1, BOX_SIZE - 1);
        if (isSelected()) {
            g.setColor(new java.awt.Color(0xE0E0E0));
            // simple checkmark — two short diagonal strokes
            g.drawLine(boxX + 3, boxY + BOX_SIZE / 2,     boxX + BOX_SIZE / 2 - 1, boxY + BOX_SIZE - 4);
            g.drawLine(boxX + BOX_SIZE / 2 - 1, boxY + BOX_SIZE - 4, boxX + BOX_SIZE - 3, boxY + 3);
        }
        // Label text after the box.
        if (text != null && !text.isEmpty()) {
            g.setColor(getForeground());
            g.setFont(getFont());
            java.awt.FontMetrics fm = g.getFontMetrics(getFont());
            int textX = boxX + BOX_SIZE + 6;
            int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, textX, baseline);
        }
        if (saved != null) g.setColor(saved);
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int tw = text == null ? 0 : fm.stringWidth(text);
        int w = 2 + BOX_SIZE + 6 + tw + 2;
        int h = Math.max(fm.getHeight(), BOX_SIZE) + 4;
        return new java.awt.Dimension(w, h);
    }
}
