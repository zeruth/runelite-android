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

import java.util.Vector;

/** Mutable table data used by plugin search/result panels. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultTableModel extends AbstractTableModel {
    protected Vector<Vector> dataVector;
    protected Vector columnIdentifiers;

    public DefaultTableModel() { this(0, 0); }
    public DefaultTableModel(int rows, int columns) {
        this(new Vector(), new Vector());
        setColumnCount(columns);
        setRowCount(rows);
    }
    public DefaultTableModel(Object[] columns, int rows) { this(convertToVector(columns), rows); }
    public DefaultTableModel(Vector columns, int rows) {
        this(new Vector(), columns);
        setRowCount(rows);
    }
    public DefaultTableModel(Object[][] data, Object[] columns) {
        this(convertToVector(data), convertToVector(columns));
    }
    public DefaultTableModel(Vector<? extends Vector> data, Vector columns) { setDataVector(data, columns); }

    public Vector<Vector> getDataVector() { return dataVector; }
    public void setDataVector(Object[][] data, Object[] columns) {
        setDataVector(convertToVector(data), convertToVector(columns));
    }
    public void setDataVector(Vector<? extends Vector> data, Vector columns) {
        dataVector = data == null ? new Vector<>() : (Vector<Vector>) data;
        columnIdentifiers = columns == null ? new Vector() : columns;
        normalizeRows();
        fireTableStructureChanged();
    }
    private void normalizeRows() {
        for (int row = 0; row < dataVector.size(); row++) {
            if (dataVector.get(row) == null) dataVector.set(row, new Vector());
            dataVector.get(row).setSize(getColumnCount());
        }
    }
    @Override public int getRowCount() { return dataVector.size(); }
    @Override public int getColumnCount() { return columnIdentifiers.size(); }
    @Override public String getColumnName(int column) {
        Object name = column >= 0 && column < getColumnCount() ? columnIdentifiers.get(column) : null;
        return name == null ? super.getColumnName(column) : name.toString();
    }
    @Override public int findColumn(String name) {
        for (int i = 0; i < getColumnCount(); i++) if (getColumnName(i).equals(name)) return i;
        return -1;
    }
    @Override public boolean isCellEditable(int row, int column) { return true; }
    @Override public Object getValueAt(int row, int column) { return dataVector.get(row).get(column); }
    @Override public void setValueAt(Object value, int row, int column) {
        dataVector.get(row).set(column, value);
        fireTableCellUpdated(row, column);
    }
    public void setNumRows(int rows) { setRowCount(rows); }
    public void setRowCount(int rows) {
        int old = getRowCount();
        if (rows == old) return;
        dataVector.setSize(rows);
        normalizeRows();
        if (rows > old) fireTableRowsInserted(old, rows - 1);
        else fireTableRowsDeleted(rows, old - 1);
    }
    public void setColumnCount(int columns) {
        columnIdentifiers.setSize(columns);
        normalizeRows();
        fireTableStructureChanged();
    }
    public void setColumnIdentifiers(Object[] columns) { setColumnIdentifiers(convertToVector(columns)); }
    public void setColumnIdentifiers(Vector columns) { setDataVector(dataVector, columns); }
    public void addRow(Object[] row) { addRow(convertToVector(row)); }
    public void addRow(Vector row) { insertRow(getRowCount(), row); }
    public void insertRow(int index, Object[] row) { insertRow(index, convertToVector(row)); }
    public void insertRow(int index, Vector row) {
        dataVector.add(index, row == null ? new Vector() : row);
        normalizeRows();
        fireTableRowsInserted(index, index);
    }
    public void removeRow(int row) { dataVector.remove(row); fireTableRowsDeleted(row, row); }
    public void addColumn(Object name) { addColumn(name, (Vector) null); }
    public void addColumn(Object name, Object[] values) { addColumn(name, convertToVector(values)); }
    public void addColumn(Object name, Vector values) {
        columnIdentifiers.add(name);
        if (values != null && values.size() > getRowCount()) dataVector.setSize(values.size());
        normalizeRows();
        if (values != null) for (int i = 0; i < values.size(); i++)
            dataVector.get(i).set(getColumnCount() - 1, values.get(i));
        fireTableStructureChanged();
    }
    public void moveRow(int start, int end, int to) {
        int first = Math.min(start, to), last = Math.max(end, to + end - start);
        java.util.Collections.rotate(dataVector.subList(first, last + 1), to - start);
        fireTableRowsUpdated(first, last);
    }
    protected static Vector convertToVector(Object[] values) {
        if (values == null) return null;
        Vector result = new Vector(values.length);
        java.util.Collections.addAll(result, values);
        return result;
    }
    protected static Vector<Vector> convertToVector(Object[][] values) {
        if (values == null) return null;
        Vector<Vector> result = new Vector<>();
        for (Object[] row : values) result.add(convertToVector(row));
        return result;
    }
}
