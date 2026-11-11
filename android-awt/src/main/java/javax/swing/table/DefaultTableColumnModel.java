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

public class DefaultTableColumnModel implements TableColumnModel, java.io.Serializable {
    protected final java.util.Vector<TableColumn> tableColumns = new java.util.Vector<>();
    protected int columnMargin = 1;
    public void addColumn(TableColumn column) { if (column == null) throw new IllegalArgumentException("column"); tableColumns.add(column); }
    public void removeColumn(TableColumn column) { tableColumns.remove(column); }
    public void moveColumn(int from, int to) {
        if (from < 0 || to < 0 || from >= getColumnCount() || to >= getColumnCount()) throw new IllegalArgumentException("column index");
        tableColumns.add(to, tableColumns.remove(from));
    }
    public int getColumnCount() { return tableColumns.size(); }
    public java.util.Enumeration<TableColumn> getColumns() { return tableColumns.elements(); }
    public TableColumn getColumn(int index) { return tableColumns.get(index); }
    public int getColumnIndex(Object id) {
        if (id == null) throw new IllegalArgumentException("identifier");
        for (int i = 0; i < getColumnCount(); i++) if (id.equals(getColumn(i).getIdentifier())) return i;
        throw new IllegalArgumentException("Unknown column: " + id);
    }
    public int getColumnIndexAtX(int x) {
        if (x < 0) return -1;
        for (int i = 0; i < getColumnCount(); i++) { x -= getColumn(i).getWidth(); if (x < 0) return i; }
        return -1;
    }
    public int getTotalColumnWidth() { int width = 0; for (TableColumn column : tableColumns) width += column.getWidth(); return width; }
    public void setColumnMargin(int margin) { columnMargin = margin; }
    public int getColumnMargin() { return columnMargin; }
}
