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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

public class CompoundBorder extends AbstractBorder {
    private static final long serialVersionUID = 9054540377030555103L;
    protected Border outsideBorder;
    protected Border insideBorder;

    public CompoundBorder() {}
    public CompoundBorder(Border outsideBorder, Border insideBorder) {
        this.outsideBorder = outsideBorder;
        this.insideBorder = insideBorder;
    }

    public Border getOutsideBorder() { return outsideBorder; }
    public Border getInsideBorder()  { return insideBorder; }

    @Override
    public Insets getBorderInsets(Component c) {
        Insets a = outsideBorder != null ? outsideBorder.getBorderInsets(c) : new Insets(0, 0, 0, 0);
        Insets b = insideBorder != null ? insideBorder.getBorderInsets(c) : new Insets(0, 0, 0, 0);
        return new Insets(a.top + b.top, a.left + b.left, a.bottom + b.bottom, a.right + b.right);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (outsideBorder != null) outsideBorder.paintBorder(c, g, x, y, width, height);
        if (insideBorder != null) {
            Insets o = outsideBorder != null ? outsideBorder.getBorderInsets(c) : new Insets(0, 0, 0, 0);
            insideBorder.paintBorder(c, g,
                x + o.left, y + o.top,
                width - o.left - o.right, height - o.top - o.bottom);
        }
    }
}
