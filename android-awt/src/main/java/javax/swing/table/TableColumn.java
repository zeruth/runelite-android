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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class TableColumn implements java.io.Serializable {
    private TableCellEditor cellEditor;
    public TableCellEditor getCellEditor(){return cellEditor;}
    public void setCellEditor(TableCellEditor editor){cellEditor=editor;}

    private int modelIndex, width, preferredWidth, minWidth = 15, maxWidth = Integer.MAX_VALUE;
    private Object identifier, headerValue;
    private TableCellRenderer headerRenderer, cellRenderer;
    private boolean resizable = true;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    public TableColumn() { this(0); }
    public TableColumn(int index) { this(index, 75); }
    public TableColumn(int index, int width) { modelIndex = index; this.width = preferredWidth = width; }
    public int getModelIndex() { return modelIndex; }
    public void setModelIndex(int index) { modelIndex = index; }
    public Object getIdentifier() { return identifier == null ? headerValue : identifier; }
    public void setIdentifier(Object id) { identifier = id; }
    public Object getHeaderValue() { return headerValue; }
    public void setHeaderValue(Object value) { headerValue = value; }
    public int getWidth() { return width; }
    public void setWidth(int value) { int old = width; width = clamp(value); changes.firePropertyChange("width", old, width); }
    public int getPreferredWidth() { return preferredWidth; }
    public void setPreferredWidth(int value) { int old = preferredWidth; preferredWidth = clamp(value); changes.firePropertyChange("preferredWidth", old, preferredWidth); }
    public int getMinWidth() { return minWidth; }
    public void setMinWidth(int value) { minWidth = Math.max(0, value); maxWidth = Math.max(maxWidth, minWidth); setWidth(width); setPreferredWidth(preferredWidth); }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int value) { maxWidth = Math.max(minWidth, value); setWidth(width); setPreferredWidth(preferredWidth); }
    private int clamp(int value) { return Math.max(minWidth, Math.min(maxWidth, value)); }
    public boolean getResizable() { return resizable; }
    public void setResizable(boolean value) { resizable = value; }
    public TableCellRenderer getHeaderRenderer() { return headerRenderer; }
    public void setHeaderRenderer(TableCellRenderer renderer) { headerRenderer = renderer; }
    public TableCellRenderer getCellRenderer() { return cellRenderer; }
    public void setCellRenderer(TableCellRenderer renderer) { cellRenderer = renderer; }
    public void addPropertyChangeListener(PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { changes.removePropertyChangeListener(listener); }
}
