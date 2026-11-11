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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

public abstract class AbstractTableModel implements TableModel, Serializable {
    private static final long serialVersionUID = -5798593159423650347L;
    protected final List<TableModelListener> listeners = new ArrayList<>();

    public AbstractTableModel() {}

    @Override public String getColumnName(int column) { return Character.toString((char) ('A' + column)); }
    public int findColumn(String columnName) { return -1; }
    @Override public Class<?> getColumnClass(int columnIndex) { return Object.class; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }
    @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {}

    @Override public void addTableModelListener(TableModelListener l) { if (l != null) listeners.add(l); }
    @Override public void removeTableModelListener(TableModelListener l) { listeners.remove(l); }

    public void fireTableDataChanged() { fire(new TableModelEvent(this)); }
    public void fireTableStructureChanged() { fire(new TableModelEvent(this, TableModelEvent.HEADER_ROW)); }
    public void fireTableRowsInserted(int firstRow, int lastRow) { fire(new TableModelEvent(this, firstRow, lastRow, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT)); }
    public void fireTableRowsUpdated(int firstRow, int lastRow) { fire(new TableModelEvent(this, firstRow, lastRow)); }
    public void fireTableRowsDeleted(int firstRow, int lastRow) { fire(new TableModelEvent(this, firstRow, lastRow, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE)); }
    public void fireTableCellUpdated(int row, int column) { fire(new TableModelEvent(this, row, row, column)); }
    public void fireTableChanged(TableModelEvent e) { fire(e); }

    private void fire(TableModelEvent e) {
        for (TableModelListener l : new ArrayList<>(listeners)) l.tableChanged(e);
    }
}
