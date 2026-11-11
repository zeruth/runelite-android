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
package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;

public class MatteBorder extends EmptyBorder {
    private static final long serialVersionUID = 4422834609971747173L;
    protected Color color;
    protected Icon tileIcon;

    public MatteBorder(int top, int left, int bottom, int right, Color color) {
        super(top, left, bottom, right);
        this.color = color;
    }

    public MatteBorder(Insets borderInsets, Color color) {
        super(borderInsets);
        this.color = color;
    }

    public MatteBorder(int top, int left, int bottom, int right, Icon tileIcon) {
        super(top, left, bottom, right);
        this.tileIcon = tileIcon;
    }

    public MatteBorder(Icon tileIcon) {
        super(-1, -1, -1, -1);
        this.tileIcon = tileIcon;
    }

    public Color getMatteColor() { return color; }
    public Icon getTileIcon() { return tileIcon; }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (color == null) return;
        Color old = g.getColor();
        g.setColor(color);
        g.fillRect(x, y, width, top);
        g.fillRect(x, y + height - bottom, width, bottom);
        g.fillRect(x, y + top, left, height - top - bottom);
        g.fillRect(x + width - right, y + top, right, height - top - bottom);
        g.setColor(old);
    }

    @Override public boolean isBorderOpaque() { return color != null && tileIcon == null; }
}
