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
package javax.swing.plaf.basic;

import java.awt.*;
import javax.swing.*;

public class BasicArrowButton extends JButton implements SwingConstants {
    protected int direction;
    private final Color shadow, darkShadow, highlight;
    public BasicArrowButton(int direction) {
        this(direction, new Color(0x60636a), Color.GRAY, Color.BLACK, Color.WHITE);
    }
    public BasicArrowButton(int direction, Color background, Color shadow, Color darkShadow, Color highlight) {
        this.direction = direction;
        this.shadow = shadow; this.darkShadow = darkShadow; this.highlight = highlight;
        setBackground(background);
    }
    public int getDirection() { return direction; }
    public void setDirection(int direction) { this.direction = direction; repaint(); }
    @Override public Dimension getPreferredSize() { return new Dimension(16, 16); }
    @Override public Dimension getMinimumSize() { return new Dimension(5, 5); }
    @Override protected void paintComponent(Graphics g) {
        g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
        int size = Math.max(2, Math.min(getWidth(), getHeight()) / 3);
        paintTriangle(g, (getWidth() - size) / 2, (getHeight() - size) / 2, size, direction, isEnabled());
    }
    public void paintTriangle(Graphics g, int x, int y, int size, int direction, boolean enabled) {
        Color old = g.getColor();
        g.setColor(enabled ? darkShadow : shadow);
        int mid = size / 2;
        int[] xs, ys;
        switch (direction) {
            case NORTH: xs = new int[]{x, x+size, x+mid}; ys = new int[]{y+size, y+size, y}; break;
            case WEST: xs = new int[]{x+size, x+size, x}; ys = new int[]{y, y+size, y+mid}; break;
            case EAST: xs = new int[]{x, x, x+size}; ys = new int[]{y, y+size, y+mid}; break;
            default: xs = new int[]{x, x+size, x+mid}; ys = new int[]{y, y, y+size}; break;
        }
        g.fillPolygon(xs, ys, 3);
        g.setColor(old);
    }
}
