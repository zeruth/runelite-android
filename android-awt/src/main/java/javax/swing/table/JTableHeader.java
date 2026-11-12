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
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;

public class JTableHeader extends JComponent implements TableColumnModelListener {
    private static final long serialVersionUID = -4041758989289564599L;
    /** Half the width BasicTableHeaderUI treats as the resize zone around a column edge. */
    private static final int RESIZE_SLOP = 3;

    protected JTable table;
    protected TableColumnModel columnModel;
    protected boolean reorderingAllowed = true;
    protected boolean resizingAllowed = true;
    protected boolean updateTableInRealTime = true;
    protected transient TableColumn resizingColumn;
    protected transient TableColumn draggedColumn;
    protected transient int draggedDistance;
    private TableCellRenderer defaultRenderer;

    public JTableHeader() { this(null); }
    public JTableHeader(TableColumnModel model) {
        setColumnModel(model == null ? createDefaultColumnModel() : model);
        setOpaque(true);
        defaultRenderer = createDefaultRenderer();
        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    protected TableColumnModel createDefaultColumnModel() { return new DefaultTableColumnModel(); }
    protected TableCellRenderer createDefaultRenderer() { return new sun.swing.table.DefaultTableCellHeaderRenderer(); }

    public void setTable(JTable table) { JTable old = this.table; this.table = table; firePropertyChange("table", old, table); }
    public JTable getTable() { return table; }
    public void setReorderingAllowed(boolean value) { boolean old = reorderingAllowed; reorderingAllowed = value; firePropertyChange("reorderingAllowed", old, value); }
    public boolean getReorderingAllowed() { return reorderingAllowed; }
    public void setResizingAllowed(boolean value) { boolean old = resizingAllowed; resizingAllowed = value; firePropertyChange("resizingAllowed", old, value); }
    public boolean getResizingAllowed() { return resizingAllowed; }
    public TableColumn getDraggedColumn() { return draggedColumn; }
    public void setDraggedColumn(TableColumn column) { draggedColumn = column; }
    public int getDraggedDistance() { return draggedDistance; }
    public void setDraggedDistance(int distance) { draggedDistance = distance; }
    public TableColumn getResizingColumn() { return resizingColumn; }
    public void setResizingColumn(TableColumn column) { resizingColumn = column; }
    public void setUpdateTableInRealTime(boolean flag) { updateTableInRealTime = flag; }
    public boolean getUpdateTableInRealTime() { return updateTableInRealTime; }
    public TableCellRenderer getDefaultRenderer() { return defaultRenderer; }
    public void setDefaultRenderer(TableCellRenderer renderer) { defaultRenderer = renderer; }

    public TableColumnModel getColumnModel() { return columnModel; }
    public void setColumnModel(TableColumnModel model) {
        if (model == null) throw new IllegalArgumentException("Cannot set a null ColumnModel");
        TableColumnModel old = columnModel;
        if (model != old) {
            if (old != null) old.removeColumnModelListener(this);
            columnModel = model;
            model.addColumnModelListener(this);
            firePropertyChange("columnModel", old, model);
            resizeAndRepaint();
        }
    }

    public int columnAtPoint(Point p) {
        if (p.x < 0) return -1;
        int x = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            x += columnModel.getColumn(i).getWidth();
            if (p.x < x) return i;
        }
        return -1;
    }

    public Rectangle getHeaderRect(int column) {
        Rectangle r = new Rectangle(0, 0, 0, getHeight());
        if (column < 0) return r;
        int count = columnModel.getColumnCount();
        for (int i = 0; i < Math.min(column, count); i++) r.x += columnModel.getColumn(i).getWidth();
        if (column < count) r.width = columnModel.getColumn(column).getWidth();
        return r;
    }

    /** The header renderer's tooltip for the column under the event, as the JDK does. */
    @Override public String getToolTipText(MouseEvent event) {
        int column = columnAtPoint(event.getPoint());
        String tip = null;
        if (column != -1) {
            Component component = renderer(column).getTableCellRendererComponent(table,
                columnModel.getColumn(column).getHeaderValue(), false, false, -1, column);
            if (component instanceof JComponent) {
                Rectangle cell = getHeaderRect(column);
                MouseEvent translated = new MouseEvent(component, event.getID(), event.getWhen(), event.getModifiers(),
                    event.getX() - cell.x, event.getY() - cell.y, event.getXOnScreen(), event.getYOnScreen(),
                    event.getClickCount(), event.isPopupTrigger(), MouseEvent.NOBUTTON);
                tip = ((JComponent) component).getToolTipText(translated);
            }
        }
        return tip == null ? getToolTipText() : tip;
    }

    public void resizeAndRepaint() { revalidate(); repaint(); }

    public void columnAdded(TableColumnModelEvent e) { resizeAndRepaint(); }
    public void columnRemoved(TableColumnModelEvent e) { resizeAndRepaint(); }
    public void columnMoved(TableColumnModelEvent e) { repaint(); }
    public void columnMarginChanged(ChangeEvent e) { resizeAndRepaint(); }
    public void columnSelectionChanged(ListSelectionEvent e) { }

    private TableCellRenderer renderer(int column) {
        TableCellRenderer renderer = columnModel.getColumn(column).getHeaderRenderer();
        return renderer == null ? defaultRenderer : renderer;
    }

    @Override public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        Font font = getFont();
        int text = font == null ? 16 : getFontMetrics(font).getHeight();
        return new Dimension(columnModel.getTotalColumnWidth(), Math.max(20, text + 4));
    }

    @Override protected void paintComponent(Graphics g) {
        if (paintDelegate(g)) return;
        super.paintComponent(g);
        int x = 0, draggedX = 0, dragged = -1;
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            // BasicTableHeaderUI paints the dragged column last, offset by the drag distance.
            if (column == draggedColumn) { dragged = i; draggedX = x; }
            else paintCell(g, i, x);
            x += column.getWidth();
        }
        if (dragged >= 0) {
            Rectangle r = getHeaderRect(dragged);
            g.setColor(getParent() == null || getParent().getBackground() == null ? Color.GRAY : getParent().getBackground());
            g.fillRect(r.x, r.y, r.width, r.height);
            paintCell(g, dragged, draggedX + draggedDistance);
        }
    }

    private void paintCell(Graphics g, int index, int x) {
        TableColumn column = columnModel.getColumn(index);
        Component cell = renderer(index).getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, index);
        Graphics cg = g.create(x, 0, column.getWidth(), getHeight());
        cell.setBounds(0, 0, column.getWidth(), getHeight()); cell.paint(cg); cg.dispose();
    }

    private void tableChanged() {
        if (table != null) { table.revalidate(); table.repaint(); }
    }

    /**
     * BasicTableHeaderUI.MouseInputHandler: a press near a column edge resizes it, a press
     * elsewhere drags the column past half its neighbour to swap them, and a click without
     * a drag toggles the sort. A touch drag reaches here as the same mouse events.
     */
    private final class MouseHandler extends MouseInputAdapter {
        private int pressX, startWidth;
        private boolean moved;

        @Override public void mousePressed(MouseEvent e) {
            if (!isEnabled()) return;
            pressX = e.getX(); moved = false;
            draggedColumn = null; resizingColumn = null; draggedDistance = 0;
            TableColumn resize = resizingAllowed ? resizeColumnAt(e.getX()) : null;
            if (resize != null) { resizingColumn = resize; startWidth = resize.getWidth(); return; }
            int index = columnAtPoint(e.getPoint());
            if (reorderingAllowed && index != -1) draggedColumn = columnModel.getColumn(index);
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (!isEnabled()) return;
            int dx = e.getX() - pressX;
            if (dx != 0) moved = true;
            if (resizingColumn != null) {
                resizingColumn.setPreferredWidth(startWidth + dx);
                resizingColumn.setWidth(resizingColumn.getPreferredWidth());
                resizeAndRepaint(); tableChanged();
                return;
            }
            if (draggedColumn == null) return;
            int index = indexOf(draggedColumn);
            if (index < 0) return;
            if (dx < 0 && index > 0) {
                int width = columnModel.getColumn(index - 1).getWidth();
                if (-dx > width / 2) { columnModel.moveColumn(index, index - 1); pressX -= width; }
            } else if (dx > 0 && index < columnModel.getColumnCount() - 1) {
                int width = columnModel.getColumn(index + 1).getWidth();
                if (dx > width / 2) { columnModel.moveColumn(index, index + 1); pressX += width; }
            }
            draggedDistance = e.getX() - pressX;
            repaint(); tableChanged();
        }

        @Override public void mouseReleased(MouseEvent e) {
            if (!isEnabled()) return;
            boolean wasDragging = draggedColumn != null || resizingColumn != null;
            draggedColumn = null; resizingColumn = null; draggedDistance = 0;
            if (wasDragging) { repaint(); tableChanged(); }
        }

        @Override public void mouseClicked(MouseEvent e) {
            if (moved || !isEnabled() || e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() % 2 != 1) return;
            if (table == null || table.getRowSorter() == null || resizeColumnAt(e.getX()) != null) return;
            int index = columnAtPoint(e.getPoint());
            if (index != -1) table.getRowSorter().toggleSortOrder(table.convertColumnIndexToModel(index));
        }

        private int indexOf(TableColumn column) {
            for (int i = 0; i < columnModel.getColumnCount(); i++) if (columnModel.getColumn(i) == column) return i;
            return -1;
        }

        private TableColumn resizeColumnAt(int px) {
            int x = 0;
            for (int i = 0; i < columnModel.getColumnCount(); i++) {
                TableColumn column = columnModel.getColumn(i);
                x += column.getWidth();
                if (Math.abs(px - x) <= RESIZE_SLOP) return column.getResizable() ? column : null;
            }
            return null;
        }
    }
}
