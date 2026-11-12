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
package javax.swing;

import java.awt.Component;
import java.awt.Rectangle;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class JTable extends JComponent implements javax.swing.event.TableModelListener, Scrollable,
        javax.swing.event.TableColumnModelListener, ListSelectionListener, javax.swing.event.CellEditorListener,
        javax.swing.event.RowSorterListener {
    private static final long serialVersionUID = 1L;
    public static final int AUTO_RESIZE_OFF = 0;
    public static final int AUTO_RESIZE_NEXT_COLUMN = 1;
    public static final int AUTO_RESIZE_SUBSEQUENT_COLUMNS = 2;
    public static final int AUTO_RESIZE_LAST_COLUMN = 3;
    public static final int AUTO_RESIZE_ALL_COLUMNS = 4;

    private RowSorter<? extends TableModel> sorter;
    private ListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final java.util.Map<Integer,Integer> rowHeights = new java.util.HashMap<>();
    private final java.util.Map<Class<?>,javax.swing.table.TableCellEditor> defaultEditors = new java.util.HashMap<>();
    private final java.util.Map<Class<?>,javax.swing.table.TableCellRenderer> defaultRenderers = new java.util.HashMap<>();
    private javax.swing.table.TableCellEditor cellEditor;
    private Component editorComponent;
    private int editingRow=-1,editingColumn=-1,editingModelRow=-1,editingModelColumn=-1;
    private final java.util.Set<Integer> selectedModelRows=new java.util.LinkedHashSet<>();
    private boolean restoringSelection;
    private int modelAnchor=-1,modelLead=-1;
    /** Selection changes are remembered in model rows so a re-sort can map them back to view rows. */
    public void valueChanged(javax.swing.event.ListSelectionEvent e) { rememberSelection(); repaint(); }
    /** The edited cell is tracked in model coordinates: a sort during editing must not commit to another row. */
    public void editingStopped(javax.swing.event.ChangeEvent e) { if(cellEditor!=null){Object value=cellEditor.getCellEditorValue();int row=editingModelRow,col=editingModelColumn;removeEditor();if(row>=0&&row<dataModel.getRowCount()&&col>=0&&col<dataModel.getColumnCount())dataModel.setValueAt(value,row,col);} }
    public void editingCanceled(javax.swing.event.ChangeEvent e) { removeEditor(); }
    public void sorterChanged(javax.swing.event.RowSorterEvent e) {
        if(e.getType()==javax.swing.event.RowSorterEvent.Type.SORTED){restoreSelection();relocateEditor();}
        revalidate(); repaint();
    }

    public void columnAdded(javax.swing.event.TableColumnModelEvent e) { if(isEditing())removeEditor(); resizeAndRepaint(); }
    public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { if(isEditing())removeEditor(); resizeAndRepaint(); }
    public void columnMoved(javax.swing.event.TableColumnModelEvent e) {
        // The editor follows its model column, as BasicTableUI does after a header drag.
        if(isEditing()){editingColumn=convertColumnIndexToView(editingModelColumn);if(editingColumn<0)removeEditor();else editorComponent.setBounds(getCellRect(editingRow,editingColumn,false));}
        repaint();
    }
    public void columnMarginChanged(javax.swing.event.ChangeEvent e) { if(isEditing()&&!cellEditor.stopCellEditing())cellEditor.cancelCellEditing(); resizeAndRepaint(); }
    public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { repaint(); }
    protected void resizeAndRepaint() { revalidate(); repaint(); }

    private java.awt.Color selectionBackground = new java.awt.Color(0x3875D6), selectionForeground = java.awt.Color.WHITE, gridColor = java.awt.Color.GRAY;
    protected boolean showHorizontalLines = true, showVerticalLines = true;
    protected boolean cellSelectionEnabled;
    private boolean fillsViewportHeight, autoCreateRowSorter, dragEnabled, surrendersFocusOnKeystroke, updateSelectionOnSort = true;
    private DropMode dropMode = DropMode.USE_SELECTION;
    protected java.awt.Dimension preferredViewportSize = new java.awt.Dimension(450, 400);
    private int selectedModelIndex(int view){return view<0||view>=getRowCount()?-1:convertRowIndexToModel(view);}
    private void rememberSelection(){
        if(restoringSelection||dataModel==null)return;
        selectedModelRows.clear();
        for(int row:getSelectedRows())selectedModelRows.add(convertRowIndexToModel(row));
        modelAnchor=selectedModelIndex(selectionModel.getAnchorSelectionIndex());modelLead=selectedModelIndex(selectionModel.getLeadSelectionIndex());
    }
    private int visibleModelIndex(int model){return model<0||model>=dataModel.getRowCount()?-1:convertRowIndexToView(model);}
    private void restoreSelection(){
        restoringSelection=true;boolean adjusting=selectionModel.getValueIsAdjusting();
        try{
            selectionModel.setValueIsAdjusting(true);selectionModel.clearSelection();
            for(int model:selectedModelRows){int view=visibleModelIndex(model);if(view>=0)selectionModel.addSelectionInterval(view,view);}
            selectionModel.setAnchorSelectionIndex(visibleModelIndex(modelAnchor));
            if(selectionModel instanceof DefaultListSelectionModel)((DefaultListSelectionModel)selectionModel).moveLeadSelectionIndex(visibleModelIndex(modelLead));
        }finally{selectionModel.setValueIsAdjusting(adjusting);restoringSelection=false;}
        // A row the filter hid is deselected for good, as on the JDK; it must not come back selected.
        rememberSelection();
    }
    private void relocateEditor(){
        if(cellEditor==null)return;
        editingRow=visibleModelIndex(editingModelRow);editingColumn=convertColumnIndexToView(editingModelColumn);
        if(editingRow<0||editingColumn<0){cellEditor.cancelCellEditing();return;}
        editorComponent.setBounds(getCellRect(editingRow,editingColumn,false));
    }
    public RowSorter<? extends TableModel> getRowSorter() { return sorter; }
    public void setRowSorter(RowSorter<? extends TableModel> value) {
        rememberSelection();
        if (sorter != null) sorter.removeRowSorterListener(this);
        sorter = value;
        if (sorter != null) sorter.addRowSorterListener(this);
        restoreSelection();relocateEditor();revalidate(); repaint();
    }
    public int convertRowIndexToModel(int row) { return sorter == null ? row : sorter.convertRowIndexToModel(row); }
    public int convertRowIndexToView(int row) { return sorter == null ? row : sorter.convertRowIndexToView(row); }
    public int rowAtPoint(java.awt.Point p) { if(p.y<0)return -1;int y=0;for(int row=0;row<getRowCount();row++){y+=getRowHeight(row);if(p.y<y)return row;}return -1; }
    public int columnAtPoint(java.awt.Point p) { return columnModel.getColumnIndexAtX(p.x); }
    public int convertColumnIndexToModel(int column) { return column<0?column:columnModel.getColumn(column).getModelIndex(); }
    public int convertColumnIndexToView(int column) { for(int i=0;i<getColumnCount();i++)if(convertColumnIndexToModel(i)==column)return i;return -1; }
    public void setGridColor(java.awt.Color c) { gridColor = c; repaint(); }
    public java.awt.Color getGridColor() { return gridColor; }
    public void setShowGrid(boolean value) { setShowHorizontalLines(value); setShowVerticalLines(value); }
    public void setShowHorizontalLines(boolean value) { boolean old=showHorizontalLines;showHorizontalLines=value;firePropertyChange("showHorizontalLines",old,value);repaint(); }
    public boolean getShowHorizontalLines() { return showHorizontalLines; }
    public void setShowVerticalLines(boolean value) { boolean old=showVerticalLines;showVerticalLines=value;firePropertyChange("showVerticalLines",old,value);repaint(); }
    public boolean getShowVerticalLines() { return showVerticalLines; }
    public void setIntercellSpacing(java.awt.Dimension spacing) { setRowMargin(spacing.height); columnModel.setColumnMargin(spacing.width); resizeAndRepaint(); }
    public java.awt.Dimension getIntercellSpacing() { return new java.awt.Dimension(columnModel.getColumnMargin(), rowMargin); }
    public void setFillsViewportHeight(boolean value) { boolean old=fillsViewportHeight;fillsViewportHeight=value;resizeAndRepaint();firePropertyChange("fillsViewportHeight",old,value); }
    public boolean getFillsViewportHeight() { return fillsViewportHeight; }
    public void setAutoCreateRowSorter(boolean value) {
        boolean old=autoCreateRowSorter;autoCreateRowSorter=value;
        if(value)setRowSorter(new javax.swing.table.TableRowSorter<TableModel>(dataModel));
        firePropertyChange("autoCreateRowSorter",old,value);
    }
    public boolean getAutoCreateRowSorter() { return autoCreateRowSorter; }
    public void setUpdateSelectionOnSort(boolean update) { boolean old=updateSelectionOnSort;updateSelectionOnSort=update;firePropertyChange("updateSelectionOnSort",old,update); }
    public boolean getUpdateSelectionOnSort() { return updateSelectionOnSort; }
    public void setDragEnabled(boolean value) { dragEnabled = value; }
    public boolean getDragEnabled() { return dragEnabled; }
    public final void setDropMode(DropMode mode) { if(mode==null)throw new IllegalArgumentException("null is not a supported drop mode for JTable"); dropMode = mode; }
    public final DropMode getDropMode() { return dropMode; }
    public void setSurrendersFocusOnKeystroke(boolean value) { surrendersFocusOnKeystroke = value; }
    public boolean getSurrendersFocusOnKeystroke() { return surrendersFocusOnKeystroke; }
    public void setPreferredScrollableViewportSize(java.awt.Dimension size) { preferredViewportSize = size; }

    public TableColumn getColumn(Object identifier) { return columnModel.getColumn(columnModel.getColumnIndex(identifier)); }
    public void addColumn(TableColumn column) {
        if (column.getHeaderValue() == null) column.setHeaderValue(dataModel.getColumnName(column.getModelIndex()));
        columnModel.addColumn(column);
    }
    public void removeColumn(TableColumn column) { columnModel.removeColumn(column); }
    public void moveColumn(int column, int target) { columnModel.moveColumn(column, target); }
    public void setColumnModel(javax.swing.table.TableColumnModel model) {
        if (model == null) throw new IllegalArgumentException("Cannot set a null ColumnModel");
        javax.swing.table.TableColumnModel old = columnModel;
        if (model != old) {
            if (old != null) old.removeColumnModelListener(this);
            columnModel = model;
            model.addColumnModelListener(this);
            if (tableHeader != null) tableHeader.setColumnModel(model);
            firePropertyChange("columnModel", old, model);
            resizeAndRepaint();
        }
    }
    public void setCellEditor(javax.swing.table.TableCellEditor editor) { javax.swing.table.TableCellEditor old=cellEditor;cellEditor=editor;firePropertyChange("tableCellEditor",old,editor); }
    public void setEditingRow(int row) { editingRow = row; }
    public void setEditingColumn(int column) { editingColumn = column; }
    public Component prepareEditor(javax.swing.table.TableCellEditor editor, int row, int column) {
        return editor.getTableCellEditorComponent(this, getValueAt(row, column), isCellSelected(row, column), row, column);
    }

    /** The cell renderer's own tooltip, with the event translated into the cell, as the JDK does. */
    @Override public String getToolTipText(java.awt.event.MouseEvent event) {
        java.awt.Point p = event.getPoint();
        int row = rowAtPoint(p), column = columnAtPoint(p);
        String tip = null;
        if (row != -1 && column != -1) {
            Component component = prepareRenderer(getCellRenderer(row, column), row, column);
            if (component instanceof JComponent) {
                Rectangle cell = getCellRect(row, column, false);
                java.awt.event.MouseEvent translated = new java.awt.event.MouseEvent(component, event.getID(), event.getWhen(),
                    event.getModifiers(), p.x - cell.x, p.y - cell.y, event.getXOnScreen(), event.getYOnScreen(),
                    event.getClickCount(), event.isPopupTrigger(), java.awt.event.MouseEvent.NOBUTTON);
                tip = ((JComponent) component).getToolTipText(translated);
            }
        }
        return tip == null ? getToolTipText() : tip;
    }
    public void setSelectionBackground(java.awt.Color c) { selectionBackground = c; repaint(); }
    public java.awt.Color getSelectionBackground() { return selectionBackground; }
    public void setSelectionForeground(java.awt.Color c) { selectionForeground = c; repaint(); }
    public java.awt.Color getSelectionForeground() { return selectionForeground; }
    public void setTableHeader(javax.swing.table.JTableHeader header) { tableHeader = header; if (header != null) header.setTable(this); revalidate(); }
    protected TableModel dataModel;
    private javax.swing.table.TableColumnModel columnModel = new javax.swing.table.DefaultTableColumnModel();
    private javax.swing.table.JTableHeader tableHeader = new javax.swing.table.JTableHeader(columnModel);
    /** JTable.createDefaultRenderers/createDefaultEditors: the class-keyed defaults every JDK table starts with. */
    private void initializeDefaults() {
        defaultRenderers.put(Object.class, new DefaultTableCellRenderer.UIResource());
        defaultRenderers.put(Number.class, new NumberRenderer());
        defaultRenderers.put(Float.class, new DoubleRenderer());
        defaultRenderers.put(Double.class, new DoubleRenderer());
        defaultRenderers.put(java.util.Date.class, new DateRenderer());
        defaultRenderers.put(Icon.class, new IconRenderer());
        defaultRenderers.put(ImageIcon.class, new IconRenderer());
        defaultRenderers.put(Boolean.class, new BooleanRenderer());
        defaultEditors.put(Object.class, new GenericEditor());
        defaultEditors.put(Number.class, new NumberEditor());
        defaultEditors.put(Boolean.class, new BooleanEditor());
    }

    static class NumberRenderer extends DefaultTableCellRenderer.UIResource {
        NumberRenderer() { setHorizontalAlignment(JLabel.RIGHT); }
    }
    static class DoubleRenderer extends NumberRenderer {
        private java.text.NumberFormat formatter;
        @Override protected void setValue(Object value) {
            if (formatter == null) formatter = java.text.NumberFormat.getInstance();
            setText(value == null ? "" : formatter.format(value));
        }
    }
    static class DateRenderer extends DefaultTableCellRenderer.UIResource {
        private java.text.DateFormat formatter;
        @Override protected void setValue(Object value) {
            if (formatter == null) formatter = java.text.DateFormat.getDateInstance();
            setText(value == null ? "" : formatter.format(value));
        }
    }
    static class IconRenderer extends DefaultTableCellRenderer.UIResource {
        IconRenderer() { setHorizontalAlignment(JLabel.CENTER); }
        @Override protected void setValue(Object value) { setIcon(value instanceof Icon ? (Icon) value : null); }
    }
    static class BooleanRenderer extends JCheckBox implements TableCellRenderer, javax.swing.plaf.UIResource {
        BooleanRenderer() { setHorizontalAlignment(JLabel.CENTER); setBorderPainted(true); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setSelected(value != null && ((Boolean) value).booleanValue());
            return this;
        }
    }
    protected int rowHeight = 16;
    protected int rowMargin = 1;
    protected boolean rowSelectionAllowed = true;
    protected boolean columnSelectionAllowed = false;
    protected int autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
    protected boolean autoCreateColumnsFromModel = true;

    public JTable() { this(new javax.swing.table.DefaultTableModel()); }
    public JTable(TableModel model) {
        tableHeader.setTable(this); setModel(model); setOpaque(true);
        selectionModel.addListSelectionListener(this); columnModel.addColumnModelListener(this);
        initializeDefaults();
        addMouseListener(new java.awt.event.MouseAdapter(){public void mousePressed(java.awt.event.MouseEvent e){if(!isEnabled()||e.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;int row=rowAtPoint(e.getPoint()),col=columnAtPoint(e.getPoint());if(row>=0&&col>=0){changeSelection(row,col,e.isControlDown(),e.isShiftDown());if(e.getClickCount()>=2)editCellAt(row,col,e);requestFocusInWindow();}}});
    }
    public JTable(Object[][] rows, Object[] columns) { this(new javax.swing.table.DefaultTableModel(rows, columns)); }
    public JTable(int rows, int columns) { this(new javax.swing.table.DefaultTableModel(rows, columns)); }
    public JTable(java.util.Vector<? extends java.util.Vector> rows, java.util.Vector<?> columns) {
        this(new javax.swing.table.DefaultTableModel(rows, columns));
    }
    public TableModel getModel() { return dataModel; }
    public void setModel(TableModel model) {
        if (model == null) throw new IllegalArgumentException("model");
        if (dataModel != null) dataModel.removeTableModelListener(this);
        if(cellEditor!=null)cellEditor.cancelCellEditing();
        if(sorter!=null){sorter.removeRowSorterListener(this);sorter=null;}
        selectedModelRows.clear();rowHeights.clear();modelAnchor=modelLead=-1;
        selectionModel.clearSelection();columnModel.getSelectionModel().clearSelection();
        dataModel = model;
        dataModel.addTableModelListener(this);
        if (autoCreateColumnsFromModel) createDefaultColumnsFromModel();
        if (autoCreateRowSorter) setRowSorter(new javax.swing.table.TableRowSorter<TableModel>(dataModel));
        revalidate(); repaint();
    }
    public javax.swing.table.TableColumnModel getColumnModel() { return columnModel; }
    public javax.swing.table.JTableHeader getTableHeader() { return tableHeader; }
    public void createDefaultColumnsFromModel() {
        while (columnModel.getColumnCount() > 0) columnModel.removeColumn(columnModel.getColumn(0));
        for (int i = 0; i < dataModel.getColumnCount(); i++) {
            javax.swing.table.TableColumn column = new javax.swing.table.TableColumn(i);
            column.setHeaderValue(dataModel.getColumnName(i));
            column.addPropertyChangeListener(event -> {
                if ("preferredWidth".equals(event.getPropertyName())) column.setWidth(column.getPreferredWidth());
                revalidate(); repaint(); if (tableHeader != null) { tableHeader.revalidate(); tableHeader.repaint(); }
            });
            columnModel.addColumn(column);
        }
        if (tableHeader != null) { tableHeader.revalidate(); tableHeader.repaint(); }
    }
    @Override public void tableChanged(javax.swing.event.TableModelEvent event) {
        boolean structure=event==null||event.getFirstRow()==javax.swing.event.TableModelEvent.HEADER_ROW;
        if(structure){
            selectedModelRows.clear();modelAnchor=modelLead=-1;rowHeights.clear();
            if(cellEditor!=null)cellEditor.cancelCellEditing();
            if(autoCreateColumnsFromModel)createDefaultColumnsFromModel();
        }else if(event.getType()!=javax.swing.event.TableModelEvent.UPDATE){
            int first=event.getFirstRow(),last=event.getLastRow(),delta=last-first+1;
            boolean insert=event.getType()==javax.swing.event.TableModelEvent.INSERT;
            java.util.Set<Integer> shifted=new java.util.LinkedHashSet<>();
            for(int row:selectedModelRows){int mapped=shiftRow(row,first,last,delta,insert);if(mapped>=0)shifted.add(mapped);}
            selectedModelRows.clear();selectedModelRows.addAll(shifted);
            modelAnchor=shiftRow(modelAnchor,first,last,delta,insert);modelLead=shiftRow(modelLead,first,last,delta,insert);
            java.util.Map<Integer,Integer> heights=new java.util.HashMap<>();
            for(java.util.Map.Entry<Integer,Integer> entry:rowHeights.entrySet()){int mapped=shiftRow(entry.getKey(),first,last,delta,insert);if(mapped>=0)heights.put(mapped,entry.getValue());}
            rowHeights.clear();rowHeights.putAll(heights);
            if(cellEditor!=null){editingModelRow=shiftRow(editingModelRow,first,last,delta,insert);if(editingModelRow<0)cellEditor.cancelCellEditing();}
        }
        if(sorter!=null){
            if(structure)sorter.modelStructureChanged();
            else if(event.getType()==javax.swing.event.TableModelEvent.INSERT)sorter.rowsInserted(event.getFirstRow(),event.getLastRow());
            else if(event.getType()==javax.swing.event.TableModelEvent.DELETE)sorter.rowsDeleted(event.getFirstRow(),event.getLastRow());
            else if(event.getLastRow()==Integer.MAX_VALUE)sorter.allRowsChanged();
            else if(event.getColumn()==javax.swing.event.TableModelEvent.ALL_COLUMNS)sorter.rowsUpdated(event.getFirstRow(),event.getLastRow());
            else sorter.rowsUpdated(event.getFirstRow(),event.getLastRow(),event.getColumn());
        }
        restoreSelection();relocateEditor();revalidate();repaint();
    }
    private int shiftRow(int row,int first,int last,int delta,boolean insert){return row<0?row:insert?(row>=first?row+delta:row):(row>=first&&row<=last?-1:row>last?row-delta:row);}
    public javax.swing.table.TableCellRenderer getCellRenderer(int row, int column) {
        javax.swing.table.TableCellRenderer renderer = columnModel.getColumn(column).getCellRenderer();
        return renderer == null ? getDefaultRenderer(getColumnClass(column)) : renderer;
    }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return new java.awt.Dimension(columnModel.getTotalColumnWidth(), totalRowHeight());
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        int rows = getRowCount(), columns = getColumnCount();
        if (rows <= 0 || columns <= 0) return;
        for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
            // BasicTableUI leaves the edited cell to the editor component painted as a child.
            if (row == editingRow && col == editingColumn && editorComponent != null) continue;
            Rectangle bounds = getCellRect(row, col, false);
            if (clip != null && !clip.intersects(bounds)) continue;
            Component cell = prepareRenderer(getCellRenderer(row, col), row, col);
            java.awt.Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
            cell.setBounds(0, 0, bounds.width, bounds.height); cell.paint(cg); cg.dispose();
        }
        // BasicTableUI.paintGrid: one line at the bottom of each row and the right of each column,
        // drawn in the intercell spacing over the painted rows and columns only.
        Rectangle last = getCellRect(rows - 1, columns - 1, true);
        int width = last.x + last.width, height = last.y + last.height;
        g.setColor(gridColor);
        if (showHorizontalLines) {
            int y = 0;
            for (int row = 0; row < rows; row++) { y += getRowHeight(row); g.drawLine(0, y - 1, width - 1, y - 1); }
        }
        if (showVerticalLines) {
            int x = 0;
            for (int col = 0; col < columns; col++) { x += columnModel.getColumn(col).getWidth(); g.drawLine(x - 1, 0, x - 1, height - 1); }
        }
    }
    public int getRowHeight() { return rowHeight; }
    public void setRowHeight(int h) { if (h <= 0) throw new IllegalArgumentException("rowHeight"); this.rowHeight = h; rowHeights.clear(); revalidate(); repaint(); }
    public int getRowHeight(int row) { return rowHeights.getOrDefault(convertRowIndexToModel(row),rowHeight); }
    public void setRowHeight(int row, int h) { if(h<=0||row<0||row>=getRowCount())throw new IllegalArgumentException("row height");rowHeights.put(convertRowIndexToModel(row),h);revalidate();repaint(); }
    private int rowY(int row) { int y=0;for(int i=0;i<row;i++)y+=getRowHeight(i);return y; }
    private int totalRowHeight() { return rowY(getRowCount()); }
    public int getRowMargin() { return rowMargin; }
    public void setRowMargin(int m) { this.rowMargin = m; }
    public boolean getRowSelectionAllowed() { return rowSelectionAllowed; }
    public void setRowSelectionAllowed(boolean b) { this.rowSelectionAllowed = b; }
    public boolean getColumnSelectionAllowed() { return columnSelectionAllowed; }
    public void setColumnSelectionAllowed(boolean b) { this.columnSelectionAllowed = b; }
    public int getAutoResizeMode() { return autoResizeMode; }
    public void setAutoResizeMode(int mode) { this.autoResizeMode = mode; }
    public boolean getAutoCreateColumnsFromModel() { return autoCreateColumnsFromModel; }
    public void setAutoCreateColumnsFromModel(boolean v) { this.autoCreateColumnsFromModel = v; if (v) createDefaultColumnsFromModel(); }
    public ListSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(ListSelectionModel value) { java.util.Objects.requireNonNull(value);ListSelectionModel old=selectionModel;old.removeListSelectionListener(this);selectionModel=value;value.addListSelectionListener(this);rememberSelection();firePropertyChange("selectionModel",old,value); }
    public void setSelectionMode(int mode) { selectionModel.setSelectionMode(mode);columnModel.getSelectionModel().setSelectionMode(mode); }
    private int[] selected(ListSelectionModel model,int count) { java.util.List<Integer> list=new java.util.ArrayList<>();for(int i=0;i<count;i++)if(model.isSelectedIndex(i))list.add(i);int[] result=new int[list.size()];for(int i=0;i<result.length;i++)result[i]=list.get(i);return result; }
    public int getSelectedRow() { return selectionModel.getMinSelectionIndex(); }
    public int[] getSelectedRows() { return selected(selectionModel,getRowCount()); }
    public int getSelectedRowCount() { return getSelectedRows().length; }
    public int getSelectedColumn() { return columnModel.getSelectionModel().getMinSelectionIndex(); }
    public int[] getSelectedColumns() { return selected(columnModel.getSelectionModel(),getColumnCount()); }
    public int getSelectedColumnCount() { return getSelectedColumns().length; }
    public boolean isRowSelected(int row) { return selectionModel.isSelectedIndex(row); }
    public boolean isColumnSelected(int col) { return columnModel.getSelectionModel().isSelectedIndex(col); }
    public boolean isCellSelected(int row,int col) { return rowSelectionAllowed&&columnSelectionAllowed?isRowSelected(row)&&isColumnSelected(col):rowSelectionAllowed?isRowSelected(row):columnSelectionAllowed&&isColumnSelected(col); }
    public boolean getCellSelectionEnabled() { return rowSelectionAllowed&&columnSelectionAllowed; }
    public void setCellSelectionEnabled(boolean value) { rowSelectionAllowed=columnSelectionAllowed=value;repaint(); }
    public void clearSelection() { selectionModel.clearSelection();columnModel.getSelectionModel().clearSelection(); }
    public void selectAll() { if(getRowCount()>0)selectionModel.setSelectionInterval(0,getRowCount()-1);if(getColumnCount()>0)columnModel.getSelectionModel().setSelectionInterval(0,getColumnCount()-1); }
    public void changeSelection(int row,int column,boolean toggle,boolean extend) { select(selectionModel,row,toggle,extend);select(columnModel.getSelectionModel(),column,toggle,extend);scrollRectToVisible(getCellRect(row,column,true)); }
    private void select(ListSelectionModel model,int index,boolean toggle,boolean extend) { if(extend&&model.getAnchorSelectionIndex()>=0)model.setSelectionInterval(model.getAnchorSelectionIndex(),index);else if(toggle){if(model.isSelectedIndex(index))model.removeSelectionInterval(index,index);else model.addSelectionInterval(index,index);}else model.setSelectionInterval(index,index); }
    public Object getValueAt(int row, int col) { return dataModel.getValueAt(convertRowIndexToModel(row), columnModel.getColumn(col).getModelIndex()); }
    public void setValueAt(Object value, int row, int col) { dataModel.setValueAt(value, convertRowIndexToModel(row), columnModel.getColumn(col).getModelIndex()); }
    public int getRowCount() { return sorter == null ? dataModel.getRowCount() : sorter.getViewRowCount(); }
    public int getColumnCount() { return columnModel.getColumnCount(); }
    public String getColumnName(int col) { return dataModel.getColumnName(convertColumnIndexToModel(col)); }
    public Class<?> getColumnClass(int col) { return dataModel.getColumnClass(convertColumnIndexToModel(col)); }
    public boolean isCellEditable(int row, int col) { return dataModel.isCellEditable(convertRowIndexToModel(row), convertColumnIndexToModel(col)); }
    public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
        int x = 0;
        for (int i = 0; i < column; i++) x += columnModel.getColumn(i).getWidth();
        int gap = includeSpacing ? 0 : columnModel.getColumnMargin();
        return new Rectangle(x, rowY(row), Math.max(0, columnModel.getColumn(column).getWidth() - gap),
            Math.max(0, getRowHeight(row) - (includeSpacing ? 0 : rowMargin)));
    }
    public void addRowSelectionInterval(int index0, int index1) { selectionModel.addSelectionInterval(index0,index1); }
    public void addColumnSelectionInterval(int index0, int index1) { columnModel.getSelectionModel().addSelectionInterval(index0,index1); }
    public void setRowSelectionInterval(int index0, int index1) { selectionModel.setSelectionInterval(index0,index1); }
    public void setColumnSelectionInterval(int index0, int index1) { columnModel.getSelectionModel().setSelectionInterval(index0,index1); }
    public void removeRowSelectionInterval(int index0, int index1) { selectionModel.removeSelectionInterval(index0,index1); }
    public void removeColumnSelectionInterval(int index0, int index1) { columnModel.getSelectionModel().removeSelectionInterval(index0,index1); }
    public boolean editCellAt(int row,int column) { return editCellAt(row,column,null); }
    public boolean editCellAt(int row,int column,java.util.EventObject event) {
        if(row<0||column<0||row>=getRowCount()||column>=getColumnCount()||!isCellEditable(row,column))return false;
        if(cellEditor!=null&&!cellEditor.stopCellEditing())return false;
        javax.swing.table.TableCellEditor editor=getCellEditor(row,column);if(editor==null||!editor.isCellEditable(event))return false;
        Component component=editor.getTableCellEditorComponent(this,getValueAt(row,column),isCellSelected(row,column),row,column);
        if(component==null)return false;cellEditor=editor;editingRow=row;editingColumn=column;editingModelRow=convertRowIndexToModel(row);editingModelColumn=convertColumnIndexToModel(column);editorComponent=component;editor.addCellEditorListener(this);add(component);component.setBounds(getCellRect(row,column,false));component.requestFocus();repaint();return true;
    }
    public javax.swing.table.TableCellEditor getCellEditor() { return cellEditor; }
    public javax.swing.table.TableCellEditor getCellEditor(int row,int column) { javax.swing.table.TableCellEditor editor=columnModel.getColumn(column).getCellEditor();return editor!=null?editor:getDefaultEditor(getColumnClass(column)); }
    public void setDefaultEditor(Class<?> type,javax.swing.table.TableCellEditor editor) { if(editor==null)defaultEditors.remove(type);else defaultEditors.put(type,editor); }
    /** The JDK's lookup: the class, then its superclasses, with interfaces falling back to Object. */
    public javax.swing.table.TableCellEditor getDefaultEditor(Class<?> type) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()==null&&c!=Object.class?Object.class:c.getSuperclass()){javax.swing.table.TableCellEditor editor=defaultEditors.get(c);if(editor!=null)return editor;}
        return null;
    }
    public void setDefaultRenderer(Class<?> type,javax.swing.table.TableCellRenderer renderer) { if(renderer==null)defaultRenderers.remove(type);else defaultRenderers.put(type,renderer);repaint(); }
    public javax.swing.table.TableCellRenderer getDefaultRenderer(Class<?> type) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()==null&&c!=Object.class?Object.class:c.getSuperclass()){TableCellRenderer renderer=defaultRenderers.get(c);if(renderer!=null)return renderer;}
        return null;
    }
    public boolean isEditing() { return cellEditor!=null; }
    public int getEditingRow() { return editingRow; }
    public int getEditingColumn() { return editingColumn; }
    public Component getEditorComponent() { return editorComponent; }
    public void removeEditor() { if(cellEditor!=null)cellEditor.removeCellEditorListener(this);if(editorComponent!=null)remove(editorComponent);cellEditor=null;editorComponent=null;editingRow=editingColumn=editingModelRow=editingModelColumn=-1;repaint(); }

    static class GenericEditor extends DefaultCellEditor {
        private Class<?> valueType;
        private Object parsedValue;
        GenericEditor(){super(new JTextField());}
        @Override public Component getTableCellEditorComponent(JTable table,Object value,boolean selected,int row,int column){valueType=table.getColumnClass(column);return super.getTableCellEditorComponent(table,value,selected,row,column);}
        @Override public boolean stopCellEditing(){
            String text=((JTextField)editorComponent).getText();
            try{
                if(valueType==Object.class||valueType==String.class)parsedValue=text;
                else if(text.isEmpty())parsedValue=null;
                else if(valueType==Character.class){if(text.length()!=1)return false;parsedValue=text.charAt(0);}
                else if(valueType==Number.class)parsedValue=new java.math.BigDecimal(text);
                else parsedValue=valueType.getConstructor(String.class).newInstance(text);
            }catch(ReflectiveOperationException|IllegalArgumentException invalid){return false;}
            return super.stopCellEditing();
        }
        @Override public Object getCellEditorValue(){return parsedValue;}
    }
    static class NumberEditor extends GenericEditor {
        NumberEditor(){((JTextField)getComponent()).setHorizontalAlignment(SwingConstants.RIGHT);}
    }
    static class BooleanEditor extends DefaultCellEditor {
        BooleanEditor(){super(new JCheckBox());((JCheckBox)getComponent()).setHorizontalAlignment(SwingConstants.CENTER);}
    }

    public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
        return renderer.getTableCellRendererComponent(this, getValueAt(row, column), isCellSelected(row, column), false, row, column);
    }

    @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return preferredViewportSize; }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return rowHeight; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return orientation==SwingConstants.VERTICAL?visibleRect.height:visibleRect.width; }
    @Override public boolean getScrollableTracksViewportWidth() { return false; }
    /** A filling table takes the viewport's height only while its rows are shorter, so the background paints below them. */
    @Override public boolean getScrollableTracksViewportHeight() {
        java.awt.Container parent = getParent();
        return fillsViewportHeight && parent instanceof JViewport && parent.getHeight() > getPreferredSize().height;
    }
}
