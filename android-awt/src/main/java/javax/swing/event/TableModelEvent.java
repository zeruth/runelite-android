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
package javax.swing.event;

import java.util.EventObject;
import javax.swing.table.TableModel;

public class TableModelEvent extends EventObject {
    private static final long serialVersionUID = -7849672943560776795L;
    public static final int INSERT = 1;
    public static final int UPDATE = 0;
    public static final int DELETE = -1;
    public static final int HEADER_ROW = -1;
    public static final int ALL_COLUMNS = -1;

    protected int firstRow, lastRow, column, type;

    public TableModelEvent(TableModel source) { this(source, 0, Integer.MAX_VALUE, ALL_COLUMNS, UPDATE); }
    public TableModelEvent(TableModel source, int row) { this(source, row, row, ALL_COLUMNS, UPDATE); }
    public TableModelEvent(TableModel source, int first, int last) { this(source, first, last, ALL_COLUMNS, UPDATE); }
    public TableModelEvent(TableModel source, int first, int last, int col) { this(source, first, last, col, UPDATE); }
    public TableModelEvent(TableModel source, int first, int last, int col, int type) {
        super(source);
        this.firstRow = first; this.lastRow = last; this.column = col; this.type = type;
    }

    public int getFirstRow() { return firstRow; }
    public int getLastRow() { return lastRow; }
    public int getColumn() { return column; }
    public int getType() { return type; }
}
