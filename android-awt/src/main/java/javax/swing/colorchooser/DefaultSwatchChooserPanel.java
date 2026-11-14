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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The "Swatches" tab: a hue/brightness palette and a strip of recently chosen
 * colors, as the JDK's package-private DefaultSwatchChooserPanel. A tap on a
 * swatch selects its color.
 */
@SuppressWarnings("serial")
final class DefaultSwatchChooserPanel extends AbstractColorChooserPanel {

    static final int COLUMNS = 31;
    static final int ROWS = 9;
    static final int RECENT = 10;

    private final List<Color> recent = new ArrayList<Color>();
    private Swatches main;
    private Swatches recents;

    DefaultSwatchChooserPanel() {
        setInheritsPopupMenu(true);
    }

    @Override
    public String getDisplayName() {
        return "Swatches";
    }

    @Override
    public Icon getSmallDisplayIcon() {
        return null;
    }

    @Override
    public Icon getLargeDisplayIcon() {
        return null;
    }

    @Override
    protected void buildChooser() {
        if (main != null) {
            return;
        }
        setLayout(new BorderLayout(8, 0));
        main = new Swatches(COLUMNS, ROWS, false);
        recents = new Swatches(RECENT / 2, 2, true);
        JPanel side = new JPanel(new BorderLayout());
        side.add(new JLabel("Recent:"), BorderLayout.NORTH);
        side.add(recents, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
    }

    @Override
    public void updateChooser() {
        repaint();
    }

    /** The palette entry at the cell: hues across, brightness then saturation down. */
    static Color paletteColor(int column, int row) {
        if (column == COLUMNS - 1) {
            int gray = Math.round(255f * (ROWS - 1 - row) / (ROWS - 1));
            return new Color(gray, gray, gray);
        }
        float hue = column / (float) (COLUMNS - 1);
        int middle = ROWS / 2;
        float saturation = row <= middle ? (row + 1f) / (middle + 1f) : 1f;
        float brightness = row <= middle ? 1f : 1f - (row - middle) / (float) (ROWS - middle);
        return Color.getHSBColor(hue, saturation, brightness);
    }

    private void choose(Color color) {
        recent.remove(color);
        recent.add(0, color);
        while (recent.size() > RECENT) {
            recent.remove(recent.size() - 1);
        }
        setSelectedColor(color);
        repaint();
    }

    private final class Swatches extends JComponent {
        private final int columns;
        private final int rows;
        private final boolean recentStrip;

        Swatches(int columns, int rows, boolean recentStrip) {
            this.columns = columns;
            this.rows = rows;
            this.recentStrip = recentStrip;
            setPreferredSize(new Dimension(columns * 10, rows * 10));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!DefaultSwatchChooserPanel.this.isEnabled()) {
                        return;
                    }
                    Color color = colorAt(e.getX(), e.getY());
                    if (color != null) {
                        choose(color);
                    }
                }
            });
        }

        Color colorAt(int x, int y) {
            int width = Math.max(1, getWidth()), height = Math.max(1, getHeight());
            int column = x * columns / width, row = y * rows / height;
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                return null;
            }
            if (recentStrip) {
                int index = row * columns + column;
                return index < recent.size() ? recent.get(index) : null;
            }
            return paletteColor(column, row);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int width = getWidth(), height = getHeight();
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    int x0 = column * width / columns, x1 = (column + 1) * width / columns;
                    int y0 = row * height / rows, y1 = (row + 1) * height / rows;
                    Color color = recentStrip ? colorAt(x0, y0) : paletteColor(column, row);
                    g.setColor(color != null ? color : getBackground());
                    g.fillRect(x0, y0, x1 - x0, y1 - y0);
                    g.setColor(Color.black);
                    g.drawRect(x0, y0, x1 - x0 - 1, y1 - y0 - 1);
                }
            }
        }
    }
}
