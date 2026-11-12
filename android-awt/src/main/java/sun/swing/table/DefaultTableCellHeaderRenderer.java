/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.swing.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serializable;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * The default header renderer of {@code JTableHeader}: the header value,
 * the look and feel's sort icon for the column the table's sorter sorts
 * first, and the look and feel's header cell border.
 * <p>
 * The JDK reads its defaults through {@code sun.swing.DefaultLookup}; the
 * module reads them from {@code UIManager}, which is what DefaultLookup
 * answers for a component without a Synth/Nimbus UI.
 */
public class DefaultTableCellHeaderRenderer extends DefaultTableCellRenderer
        implements UIResource {
    private boolean horizontalTextPositionSet;
    private Icon sortArrow;
    private EmptyIcon emptyIcon = new EmptyIcon();

    public DefaultTableCellHeaderRenderer() {
        setHorizontalAlignment(JLabel.CENTER);
    }

    public void setHorizontalTextPosition(int textPosition) {
        horizontalTextPositionSet = true;
        super.setHorizontalTextPosition(textPosition);
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        Icon sortIcon = null;

        boolean isPaintingForPrint = false;

        if (table != null) {
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                Color fgColor = null;
                Color bgColor = null;
                if (hasFocus) {
                    fgColor = UIManager.getColor("TableHeader.focusCellForeground");
                    bgColor = UIManager.getColor("TableHeader.focusCellBackground");
                }
                if (fgColor == null) {
                    fgColor = header.getForeground();
                }
                if (bgColor == null) {
                    bgColor = header.getBackground();
                }
                setForeground(fgColor);
                setBackground(bgColor);

                setFont(header.getFont());

                isPaintingForPrint = header.isPaintingForPrint();
            }

            if (!isPaintingForPrint && table.getRowSorter() != null) {
                if (!horizontalTextPositionSet) {
                    // There is a row sorter, and the developer hasn't
                    // set a text position, change to leading.
                    setHorizontalTextPosition(JLabel.LEADING);
                }
                SortOrder sortOrder = getColumnSortOrder(table, column);
                if (sortOrder != null) {
                    switch(sortOrder) {
                    case ASCENDING:
                        sortIcon = sortIcon("Table.ascendingSortIcon", true);
                        break;
                    case DESCENDING:
                        sortIcon = sortIcon("Table.descendingSortIcon", false);
                        break;
                    case UNSORTED:
                        sortIcon = UIManager.getIcon("Table.naturalSortIcon");
                        break;
                    }
                }
            }
        }

        setText(value == null ? "" : value.toString());
        setIcon(sortIcon);
        sortArrow = sortIcon;

        Border border = null;
        if (hasFocus) {
            border = UIManager.getBorder("TableHeader.focusCellBorder");
        }
        if (border == null) {
            border = UIManager.getBorder("TableHeader.cellBorder");
        }
        if (border == null && table != null) {
            // The module's BasicLookAndFeel registers no header border; without
            // one the header cells would run together, so edge them in the grid
            // colour as Basic's bevelled cell border visually does.
            border = new MatteBorder(0, 0, 1, 1, table.getGridColor());
        }
        setBorder(border);

        return this;
    }

    /**
     * The look and feel's arrow, or the module's own when the look and feel
     * registers none (BasicLookAndFeel registers sun.swing.icon.SortArrowIcon
     * on the JDK; the module's BasicLookAndFeel carries no Table defaults).
     */
    private static Icon sortIcon(String key, boolean ascending) {
        Icon icon = UIManager.getIcon(key);
        return icon != null ? icon : new SortArrowIcon(ascending);
    }

    public static SortOrder getColumnSortOrder(JTable table, int column) {
        SortOrder rv = null;
        if (table == null || table.getRowSorter() == null) {
            return rv;
        }
        java.util.List<? extends RowSorter.SortKey> sortKeys =
            table.getRowSorter().getSortKeys();
        if (sortKeys.size() > 0 && sortKeys.get(0).getColumn() ==
            table.convertColumnIndexToModel(column)) {
            rv = sortKeys.get(0).getSortOrder();
        }
        return rv;
    }

    @Override
    public void paintComponent(Graphics g) {
        boolean b = UIManager.getBoolean("TableHeader.rightAlignSortArrow");
        if (b && sortArrow != null) {
            //emptyIcon is used so that if the text in the header is right
            //aligned, or if the column is too narrow, then the text will
            //be sized appropriately to make room for the icon that is about
            //to be painted manually here.
            emptyIcon.width = sortArrow.getIconWidth();
            emptyIcon.height = sortArrow.getIconHeight();
            setIcon(emptyIcon);
            super.paintComponent(g);
            Point position = computeIconPosition(g);
            sortArrow.paintIcon(this, g, position.x, position.y);
        } else {
            super.paintComponent(g);
        }
    }

    private Point computeIconPosition(Graphics g) {
        FontMetrics fontMetrics = g.getFontMetrics();
        Rectangle viewR = new Rectangle();
        Rectangle textR = new Rectangle();
        Rectangle iconR = new Rectangle();
        Insets i = getInsets();
        viewR.x = i.left;
        viewR.y = i.top;
        viewR.width = getWidth() - (i.left + i.right);
        viewR.height = getHeight() - (i.top + i.bottom);
        SwingUtilities.layoutCompoundLabel(
            this,
            fontMetrics,
            getText(),
            sortArrow,
            getVerticalAlignment(),
            getHorizontalAlignment(),
            getVerticalTextPosition(),
            getHorizontalTextPosition(),
            viewR,
            iconR,
            textR,
            getIconTextGap());
        int x = getWidth() - i.right - sortArrow.getIconWidth();
        int y = iconR.y;
        return new Point(x, y);
    }

    private class EmptyIcon implements Icon, Serializable {
        int width = 0;
        int height = 0;
        public void paintIcon(Component c, Graphics g, int x, int y) {}
        public int getIconWidth() { return width; }
        public int getIconHeight() { return height; }
    }

    /** sun.swing.icon.SortArrowIcon's shape: a filled triangle in Table.sortIconColor. */
    private static final class SortArrowIcon implements Icon, UIResource, Serializable {
        private static final int ARROW_HEIGHT = 5;
        private static final int X_PADDING = 7;
        private final boolean ascending;

        SortArrowIcon(boolean ascending) {
            this.ascending = ascending;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color color = UIManager.getColor("Table.sortIconColor");
            g.setColor(color != null ? color : c == null || c.getForeground() == null ? Color.BLACK : c.getForeground());
            int startX = x + X_PADDING / 2;
            int[] xs = {startX, startX + ARROW_HEIGHT * 2, startX + ARROW_HEIGHT};
            int[] ys = ascending
                ? new int[] {y + ARROW_HEIGHT, y + ARROW_HEIGHT, y}
                : new int[] {y, y, y + ARROW_HEIGHT};
            g.fillPolygon(xs, ys, 3);
        }

        public int getIconWidth() {
            return X_PADDING + ARROW_HEIGHT * 2;
        }

        public int getIconHeight() {
            return ARROW_HEIGHT;
        }
    }
}
