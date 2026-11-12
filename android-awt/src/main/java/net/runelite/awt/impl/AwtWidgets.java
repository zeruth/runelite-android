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
package net.runelite.awt.impl;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

/**
 * What the native peers of the heavyweight AWT widgets supplied on desktop: metrics, colours and the
 * drawing of bevels, arrows and text. The widgets paint themselves through this class (there is no
 * peer to do it); the look is flat and follows the component's own colours, which on Android default
 * to RuneLite's dark theme.
 */
public final class AwtWidgets {
    /** The font a peer falls back to when the component tree sets none. */
    public static final Font DEFAULT_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    /** Width of a vertical / height of a horizontal scrollbar, the peer's system metric. */
    public static final int SCROLLBAR_THICKNESS = 16;

    private static final Color DEFAULT_BACKGROUND = new Color(0x28, 0x28, 0x28);
    private static final Color DEFAULT_FOREGROUND = new Color(0xC8, 0xC8, 0xC8);

    private AwtWidgets() {
    }

    public static Font font(Component c) {
        Font font = c.getFont();
        return font != null ? font : DEFAULT_FONT;
    }

    public static FontMetrics metrics(Component c) {
        return c.getFontMetrics(font(c));
    }

    public static Color background(Component c) {
        Color color = c.getBackground();
        return color != null ? color : DEFAULT_BACKGROUND;
    }

    public static Color foreground(Component c) {
        Color color = c.getForeground();
        if (color == null) color = DEFAULT_FOREGROUND;
        return c.isEnabled() ? color : mix(color, background(c), 0.5f);
    }

    /** The face of a raised control: a step lighter than the background on dark themes, darker on light ones. */
    public static Color face(Component c) {
        return shade(background(c), 0.12f);
    }

    /** The well of a text or list control. */
    public static Color well(Component c) {
        return shade(background(c), -0.08f);
    }

    public static Color shade(Color color, float amount) {
        float luminance = (0.299f * color.getRed() + 0.587f * color.getGreen() + 0.114f * color.getBlue()) / 255f;
        Color target = luminance < 0.5f == amount > 0 ? Color.WHITE : Color.BLACK;
        return mix(color, target, Math.abs(amount));
    }

    public static Color mix(Color a, Color b, float t) {
        return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** A one-pixel bevel inside (x, y, w, h); raised draws the light edge top-left. */
    public static void bevel(Graphics g, Color base, int x, int y, int w, int h, boolean raised) {
        if (w <= 0 || h <= 0) return;
        Color light = shade(base, 0.25f), dark = shade(base, -0.35f);
        g.setColor(raised ? light : dark);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(raised ? dark : light);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    /** A filled triangle centred in (x, y, size, size) pointing up, down, left or right (0..3). */
    public static void arrow(Graphics g, int x, int y, int size, int direction) {
        int half = Math.max(2, size / 4), cx = x + size / 2, cy = y + size / 2;
        int[] xs, ys;
        switch (direction) {
            case 0: xs = new int[] {cx - half, cx + half, cx}; ys = new int[] {cy + half / 2, cy + half / 2, cy - half / 2 - 1}; break;
            case 1: xs = new int[] {cx - half, cx + half, cx}; ys = new int[] {cy - half / 2, cy - half / 2, cy + half / 2 + 1}; break;
            case 2: xs = new int[] {cx + half / 2, cx + half / 2, cx - half / 2 - 1}; ys = new int[] {cy - half, cy + half, cy}; break;
            default: xs = new int[] {cx - half / 2, cx - half / 2, cx + half / 2 + 1}; ys = new int[] {cy - half, cy + half, cy}; break;
        }
        g.fillPolygon(xs, ys, 3);
    }

    /** {@code text} on one line, vertically centred in a box of {@code height} starting at {@code top}. */
    public static void text(Graphics g, FontMetrics fm, String text, int x, int top, int height) {
        if (text == null || text.isEmpty()) return;
        g.drawString(text, x, top + (height - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** The ActionEvent/ItemEvent modifiers of a mouse gesture: the old-style masks of its extended ones. */
    public static int actionModifiers(java.awt.event.InputEvent e) {
        int modifiers = 0;
        if (e.isShiftDown()) modifiers |= java.awt.event.ActionEvent.SHIFT_MASK;
        if (e.isControlDown()) modifiers |= java.awt.event.ActionEvent.CTRL_MASK;
        if (e.isMetaDown()) modifiers |= java.awt.event.ActionEvent.META_MASK;
        if (e.isAltDown()) modifiers |= java.awt.event.ActionEvent.ALT_MASK;
        return modifiers;
    }
}
