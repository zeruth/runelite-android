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
package javax.swing.colorchooser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JColorChooser;
import javax.swing.JPanel;

/**
 * The default preview (the JDK's package-private DefaultPreviewPanel): the
 * chosen color as a filled swatch beside sample text drawn in it, over a
 * checkerboard so transparency shows. It reads the color of the enclosing
 * chooser, and its foreground when it is used on its own.
 */
@SuppressWarnings("serial")
final class DefaultPreviewPanel extends JPanel {

    DefaultPreviewPanel() {
        setPreferredSize(new Dimension(240, 48));
    }

    private Color previewColor() {
        for (Component c = getParent(); c != null; c = c.getParent()) {
            if (c instanceof JColorChooser) {
                Color color = ((JColorChooser) c).getColor();
                if (color != null) {
                    return color;
                }
            }
        }
        return getForeground();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth(), height = getHeight();
        int swatch = Math.min(width / 2, height * 2);
        int cell = 6;
        for (int y = 0; y < height; y += cell) {
            for (int x = 0; x < swatch; x += cell) {
                g.setColor(((x + y) / cell) % 2 == 0 ? Color.lightGray : Color.white);
                g.fillRect(x, y, Math.min(cell, swatch - x), Math.min(cell, height - y));
            }
        }
        Color color = previewColor();
        g.setColor(color);
        g.fillRect(0, 0, swatch, height);
        g.drawString("Sample Text  Sample Text", swatch + 8, height / 2 + 5);
    }
}
