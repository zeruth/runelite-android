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
package java.awt;

import java.io.Serializable;

/**
 * Real FlowLayout. Lays children left-to-right in a row, wraps to the next row when the
 * remaining width can't fit a child. Alignment shifts each row's children inside the row
 * width.
 */
public class FlowLayout implements LayoutManager, Serializable {
    private static final long serialVersionUID = -7262534655255619500L;

    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int LEADING = 3;
    public static final int TRAILING = 4;

    private int align;
    private int hgap;
    private int vgap;

    public FlowLayout() { this(CENTER, 5, 5); }
    public FlowLayout(int align) { this(align, 5, 5); }
    public FlowLayout(int align, int hgap, int vgap) {
        this.align = align; this.hgap = hgap; this.vgap = vgap;
    }

    public int getAlignment() { return align; }
    public void setAlignment(int align) { this.align = align; }
    public int getHgap() { return hgap; }
    public void setHgap(int hgap) { this.hgap = hgap; }
    public int getVgap() { return vgap; }
    public void setVgap(int vgap) { this.vgap = vgap; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container target) {
        Insets ins = target.getInsets();
        int n = target.getComponentCount();
        int w = 0, h = 0;
        boolean firstVisible = true;
        for (int i = 0; i < n; i++) {
            Component c = target.getComponent(i);
            if (!c.isVisible()) continue;
            Dimension d = c.getPreferredSize();
            // The gap goes between visible components only, as in the JDK.
            w += d.width + (firstVisible ? 0 : hgap);
            firstVisible = false;
            if (d.height > h) h = d.height;
        }
        return new Dimension(ins.left + ins.right + w + 2 * hgap, ins.top + ins.bottom + h + 2 * vgap);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) { return preferredLayoutSize(target); }

    /**
     * The JDK's algorithm: rows fill {@code width - insets - 2 * hgap} starting {@code hgap}
     * inside the left inset, a row's slack goes where the alignment says, and a
     * right-to-left container mirrors every row (LEFT and RIGHT swap, LEADING and
     * TRAILING follow the orientation).
     */
    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxwidth = target.getWidth() - (insets.left + insets.right + hgap * 2);
            int nmembers = target.getComponentCount();
            int x = 0, y = insets.top + vgap;
            int rowh = 0, start = 0;
            boolean ltr = target.getComponentOrientation().isLeftToRight();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    m.setSize(d.width, d.height);
                    if ((x == 0) || ((x + d.width) <= maxwidth)) {
                        if (x > 0) {
                            x += hgap;
                        }
                        x += d.width;
                        rowh = Math.max(rowh, d.height);
                    } else {
                        rowh = moveComponents(target, insets.left + hgap, y, maxwidth - x, rowh, start, i, ltr);
                        x = d.width;
                        y += vgap + rowh;
                        rowh = d.height;
                        start = i;
                    }
                }
            }
            moveComponents(target, insets.left + hgap, y, maxwidth - x, rowh, start, nmembers, ltr);
        }
    }

    private int moveComponents(Container target, int x, int y, int width, int height,
                               int rowStart, int rowEnd, boolean ltr) {
        switch (align) {
            case LEFT:
                x += ltr ? 0 : width;
                break;
            case CENTER:
                x += width / 2;
                break;
            case RIGHT:
                x += ltr ? width : 0;
                break;
            case LEADING:
                break;
            case TRAILING:
                x += width;
                break;
        }
        for (int i = rowStart; i < rowEnd; i++) {
            Component m = target.getComponent(i);
            if (m.isVisible()) {
                int cy = y + (height - m.getHeight()) / 2;
                if (ltr) {
                    m.setLocation(x, cy);
                } else {
                    m.setLocation(target.getWidth() - x - m.getWidth(), cy);
                }
                x += m.getWidth() + hgap;
            }
        }
        return height;
    }
}
