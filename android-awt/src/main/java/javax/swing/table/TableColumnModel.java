/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.table;

import java.util.Enumeration;
import javax.swing.event.TableColumnModelListener;
import javax.swing.ListSelectionModel;

/**
 * Defines the requirements for a table column model object suitable for
 * use with <code>JTable</code>.
 *
 * @author Alan Chung
 * @author Philip Milne
 * @see DefaultTableColumnModel
 */
public interface TableColumnModel
{
    /** Appends <code>aColumn</code> to the end of the <code>tableColumns</code> array. */
    public void addColumn(TableColumn aColumn);

    /** Deletes the <code>TableColumn</code> <code>column</code> from the <code>tableColumns</code> array. */
    public void removeColumn(TableColumn column);

    /**
     * Moves the column and its header at <code>columnIndex</code> to
     * <code>newIndex</code>.
     *
     * @exception IllegalArgumentException if <code>columnIndex</code> or
     *                <code>newIndex</code> are not in the valid range
     */
    public void moveColumn(int columnIndex, int newIndex);

    /** Sets the <code>TableColumn</code>'s column margin to <code>newMargin</code>. */
    public void setColumnMargin(int newMargin);

    /** Returns the number of columns in the model. */
    public int getColumnCount();

    /** Returns an <code>Enumeration</code> of all the columns in the model. */
    public Enumeration<TableColumn> getColumns();

    /**
     * Returns the index of the first column in the table
     * whose identifier is equal to <code>identifier</code>,
     * when compared using <code>equals</code>.
     *
     * @exception IllegalArgumentException if <code>identifier</code>
     *                          is <code>null</code>, or no
     *                          <code>TableColumn</code> has this
     *                          <code>identifier</code>
     */
    public int getColumnIndex(Object columnIdentifier);

    /** Returns the <code>TableColumn</code> object for the column at <code>columnIndex</code>. */
    public TableColumn getColumn(int columnIndex);

    /** Returns the width between the cells in each column. */
    public int getColumnMargin();

    /**
     * Returns the index of the column that lies on the
     * horizontal point, <code>xPosition</code>;
     * or -1 if it lies outside the any of the column's bounds.
     */
    public int getColumnIndexAtX(int xPosition);

    /** Returns the total width of all the columns. */
    public int getTotalColumnWidth();

    /** Sets whether the columns in this model may be selected. */
    public void setColumnSelectionAllowed(boolean flag);

    /** Returns true if columns may be selected. */
    public boolean getColumnSelectionAllowed();

    /** Returns an array of indicies of all selected columns. */
    public int[] getSelectedColumns();

    /** Returns the number of selected columns. */
    public int getSelectedColumnCount();

    /**
     * Sets the selection model.
     *
     * @param newModel  a <code>ListSelectionModel</code> object
     */
    public void setSelectionModel(ListSelectionModel newModel);

    /** Returns the current selection model. */
    public ListSelectionModel getSelectionModel();

    /** Adds a listener for table column model events. */
    public void addColumnModelListener(TableColumnModelListener x);

    /** Removes a listener for table column model events. */
    public void removeColumnModelListener(TableColumnModelListener x);
}
