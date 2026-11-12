/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.accessibility;

/**
 * Class AccessibleTable describes a user-interface component that
 * presents data in a two-dimensional table format.
 *
 * @author      Lynn Monsanto
 * @since 1.3
 */
public interface AccessibleTable {

    /** Returns the caption for the table. */
    public Accessible getAccessibleCaption();

    /** Sets the caption for the table. */
    public void setAccessibleCaption(Accessible a);

    /** Returns the summary description of the table. */
    public Accessible getAccessibleSummary();

    /** Sets the summary description of the table. */
    public void setAccessibleSummary(Accessible a);

    /** Returns the number of rows in the table. */
    public int getAccessibleRowCount();

    /** Returns the number of columns in the table. */
    public int getAccessibleColumnCount();

    /** Returns the Accessible at a specified row and column in the table. */
    public Accessible getAccessibleAt(int r, int c);

    /** Returns the number of rows occupied by the Accessible at a specified row and column. */
    public int getAccessibleRowExtentAt(int r, int c);

    /** Returns the number of columns occupied by the Accessible at a specified row and column. */
    public int getAccessibleColumnExtentAt(int r, int c);

    /** Returns the row headers as an AccessibleTable. */
    public AccessibleTable getAccessibleRowHeader();

    /** Sets the row headers. */
    public void setAccessibleRowHeader(AccessibleTable table);

    /** Returns the column headers as an AccessibleTable. */
    public AccessibleTable getAccessibleColumnHeader();

    /** Sets the column headers. */
    public void setAccessibleColumnHeader(AccessibleTable table);

    /** Returns the description of the specified row in the table. */
    public Accessible getAccessibleRowDescription(int r);

    /** Sets the description text of the specified row of the table. */
    public void setAccessibleRowDescription(int r, Accessible a);

    /** Returns the description text of the specified column in the table. */
    public Accessible getAccessibleColumnDescription(int c);

    /** Sets the description text of the specified column in the table. */
    public void setAccessibleColumnDescription(int c, Accessible a);

    /** Returns a boolean value indicating whether the accessible at a specified row and column is selected. */
    public boolean isAccessibleSelected(int r, int c);

    /** Returns a boolean value indicating whether the specified row is selected. */
    public boolean isAccessibleRowSelected(int r);

    /** Returns a boolean value indicating whether the specified column is selected. */
    public boolean isAccessibleColumnSelected(int c);

    /** Returns the selected rows in a table. */
    public int[] getSelectedAccessibleRows();

    /** Returns the selected columns in a table. */
    public int[] getSelectedAccessibleColumns();
}
