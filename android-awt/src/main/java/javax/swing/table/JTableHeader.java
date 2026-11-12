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
package javax.swing.table;

import java.awt.*;
import javax.swing.*;

public class JTableHeader extends JComponent implements javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJTableHeader();
        }
        return accessibleContext;
    }

    /** Accessibility support for table headers: role PANEL, as on the JDK. */
    protected class AccessibleJTableHeader extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.PANEL;
        }
    }
    private TableColumnModel columnModel;
    private JTable table;
    private TableCellRenderer defaultRenderer = (t, value, selected, focus, row, col) -> {
        JLabel label = new JLabel(value == null ? "" : value.toString());
        label.setFont(getFont()); label.setForeground(getForeground());
        return label;
    };
    private boolean reorderingAllowed = true, resizingAllowed = true;
    public void setReorderingAllowed(boolean value) { reorderingAllowed = value; }
    public boolean getReorderingAllowed() { return reorderingAllowed; }
    public void setResizingAllowed(boolean value) { resizingAllowed = value; }
    public boolean getResizingAllowed() { return resizingAllowed; }
    public int columnAtPoint(Point p) {
        if (p.x < 0) return -1;
        int x = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            x += columnModel.getColumn(i).getWidth();
            if (p.x < x) return i;
        }
        return -1;
    }
    public JTableHeader() { this(new DefaultTableColumnModel()); }
    public JTableHeader(TableColumnModel model) { columnModel = model; setOpaque(true); }
    public TableColumnModel getColumnModel() { return columnModel; }
    public void setColumnModel(TableColumnModel model) { columnModel = model; revalidate(); repaint(); }
    public JTable getTable() { return table; }
    public void setTable(JTable table) { this.table = table; }
    public TableCellRenderer getDefaultRenderer() { return defaultRenderer; }
    public void setDefaultRenderer(TableCellRenderer renderer) { defaultRenderer = renderer; }
    @Override public Dimension getPreferredSize() {
        return new Dimension(columnModel.getTotalColumnWidth(), Math.max(20, getFontMetrics(getFont()).getHeight() + 4));
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int x = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            TableCellRenderer renderer = column.getHeaderRenderer();
            if (renderer == null) renderer = defaultRenderer;
            Component cell = renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, i);
            Graphics cg = g.create(x, 0, column.getWidth(), getHeight());
            cell.setBounds(0, 0, column.getWidth(), getHeight()); cell.paint(cg); cg.dispose();
            x += column.getWidth();
        }
    }
}
