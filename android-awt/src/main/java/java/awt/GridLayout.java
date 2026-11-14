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
 * Real GridLayout implementation. Splits the container into a fixed-rows × fixed-cols
 * grid (one of which can be 0 to mean "as many as needed") and places each child in its
 * cell. Hgap/vgap separate cells. DynamicGridLayout in RuneLite extends this and adapts
 * per-cell sizes — for now we use uniform cells which still positions everything.
 */
public class GridLayout implements LayoutManager, Serializable {
    private static final long serialVersionUID = -7411804673224730901L;

    private int rows, cols, hgap, vgap;

    public GridLayout() { this(1, 0, 0, 0); }
    public GridLayout(int rows, int cols) { this(rows, cols, 0, 0); }
    public GridLayout(int rows, int cols, int hgap, int vgap) {
        this.rows = rows; this.cols = cols; this.hgap = hgap; this.vgap = vgap;
    }

    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = rows; }
    public int getColumns() { return cols; }
    public void setColumns(int cols) { this.cols = cols; }
    public int getHgap() { return hgap; }
    public void setHgap(int hgap) { this.hgap = hgap; }
    public int getVgap() { return vgap; }
    public void setVgap(int vgap) { this.vgap = vgap; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    private int[] gridDims(int n) {
        int r = rows, c = cols;
        if (r > 0) c = (n + r - 1) / r;
        else if (c > 0) r = (n + c - 1) / c;
        else { r = n; c = 1; }
        return new int[] { Math.max(1, r), Math.max(1, c) };
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        int n = target.getComponentCount();
        if (n == 0) return new Dimension(0, 0);
        int[] rc = gridDims(n);
        int maxW = 0, maxH = 0;
        for (int i = 0; i < n; i++) {
            Dimension d = target.getComponent(i).getPreferredSize();
            if (d.width > maxW) maxW = d.width;
            if (d.height > maxH) maxH = d.height;
        }
        Insets ins = target.getInsets();
        return new Dimension(
            ins.left + ins.right + rc[1] * maxW + (rc[1] - 1) * hgap,
            ins.top + ins.bottom + rc[0] * maxH + (rc[0] - 1) * vgap);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        int n = target.getComponentCount();
        if (n == 0) return new Dimension(0, 0);
        int[] rc = gridDims(n);
        int maxW = 0, maxH = 0;
        for (int i = 0; i < n; i++) {
            Dimension d = target.getComponent(i).getMinimumSize();
            if (d.width > maxW) maxW = d.width;
            if (d.height > maxH) maxH = d.height;
        }
        Insets ins = target.getInsets();
        return new Dimension(
            ins.left + ins.right + rc[1] * maxW + (rc[1] - 1) * hgap,
            ins.top + ins.bottom + rc[0] * maxH + (rc[0] - 1) * vgap);
    }

    @Override
    public void layoutContainer(Container target) {
        int n = target.getComponentCount();
        if (n == 0) return;
        int[] rc = gridDims(n);
        int r = rc[0], c = rc[1];
        Insets ins = target.getInsets();
        int avW = target.getWidth() - ins.left - ins.right - (c - 1) * hgap;
        int avH = target.getHeight() - ins.top - ins.bottom - (r - 1) * vgap;
        int cellW = Math.max(0, avW / c);
        int cellH = Math.max(0, avH / r);
        for (int i = 0; i < n; i++) {
            int row = i / c, col = i % c;
            int x = ins.left + col * (cellW + hgap);
            int y = ins.top + row * (cellH + vgap);
            target.getComponent(i).setBounds(x, y, cellW, cellH);
        }
    }
}
