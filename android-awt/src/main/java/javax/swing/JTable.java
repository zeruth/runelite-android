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

public class JTable extends JComponent implements Scrollable, javax.swing.event.TableModelListener {
    private static final long serialVersionUID = 1L;
    public static final int AUTO_RESIZE_OFF = 0;
    public static final int AUTO_RESIZE_NEXT_COLUMN = 1;
    public static final int AUTO_RESIZE_SUBSEQUENT_COLUMNS = 2;
    public static final int AUTO_RESIZE_LAST_COLUMN = 3;
    public static final int AUTO_RESIZE_ALL_COLUMNS = 4;

    private RowSorter<? extends TableModel> sorter;
    private ListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final ListSelectionModel columnSelection = new DefaultListSelectionModel();
    private final java.util.Map<Integer,Integer> rowHeights = new java.util.HashMap<>();
    private final java.util.Map<Class<?>,javax.swing.table.TableCellEditor> defaultEditors = new java.util.HashMap<>();
    private final java.util.Map<Class<?>,javax.swing.table.TableCellRenderer> defaultRenderers = new java.util.HashMap<>();
    private javax.swing.table.TableCellEditor cellEditor;
    private Component editorComponent;
    private int editingRow=-1,editingColumn=-1,editingModelRow=-1,editingModelColumn=-1;
    private final java.util.Set<Integer> selectedModelRows=new java.util.LinkedHashSet<>();
    private boolean restoringSelection;
    private int modelAnchor=-1,modelLead=-1;
    private final ListSelectionListener selectionListener=e->{rememberSelection();repaint();};
    private final javax.swing.event.CellEditorListener editorListener = new javax.swing.event.CellEditorListener() {
        public void editingStopped(javax.swing.event.ChangeEvent e) { if(cellEditor!=null){Object value=cellEditor.getCellEditorValue();int row=editingModelRow,col=editingModelColumn;removeEditor();if(row>=0&&row<dataModel.getRowCount()&&col>=0&&col<dataModel.getColumnCount())dataModel.setValueAt(value,row,col);} }
        public void editingCanceled(javax.swing.event.ChangeEvent e) { removeEditor(); }
    };

    private java.awt.Color selectionBackground = new java.awt.Color(0x3875D6), selectionForeground = java.awt.Color.WHITE, gridColor = java.awt.Color.GRAY;
    private boolean showGrid = true;
    private final javax.swing.event.RowSorterListener sortListener = e -> {
        if(e.getType()==javax.swing.event.RowSorterEvent.Type.SORTED){restoreSelection();relocateEditor();}
        revalidate(); repaint();
    };
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
        if (sorter != null) sorter.removeRowSorterListener(sortListener);
        sorter = value;
        if (sorter != null) sorter.addRowSorterListener(sortListener);
        restoreSelection();relocateEditor();revalidate(); repaint();
    }
    public int convertRowIndexToModel(int row) { return sorter == null ? row : sorter.convertRowIndexToModel(row); }
    public int convertRowIndexToView(int row) { return sorter == null ? row : sorter.convertRowIndexToView(row); }
    public int rowAtPoint(java.awt.Point p) { if(p.y<0)return -1;int y=0;for(int row=0;row<getRowCount();row++){y+=getRowHeight(row);if(p.y<y)return row;}return -1; }
    public int columnAtPoint(java.awt.Point p) { return columnModel.getColumnIndexAtX(p.x); }

    private boolean dragEnabled;
    private DropMode dropMode = DropMode.USE_SELECTION;
    private transient DropLocation dropLocation;
    private transient java.awt.dnd.DragGestureRecognizer dragRecognizer;

    /**
     * A subclass of <code>TransferHandler.DropLocation</code> representing
     * a drop location for a <code>JTable</code>.
     *
     * @see #getDropLocation
     * @since 1.6
     */
    public static final class DropLocation extends TransferHandler.DropLocation {
        private final int row;
        private final int col;
        private final boolean isInsertRow;
        private final boolean isInsertCol;

        private DropLocation(java.awt.Point p, int row, int col, boolean isInsertRow, boolean isInsertCol) {
            super(p);
            this.row = row;
            this.col = col;
            this.isInsertRow = isInsertRow;
            this.isInsertCol = isInsertCol;
        }

        /** Returns the row index where a dropped item should be placed, or -1 over empty space. */
        public int getRow() { return row; }

        /** Returns the column index where a dropped item should be placed, or -1 over empty space. */
        public int getColumn() { return col; }

        /** Whether this location represents insertion of rows. */
        public boolean isInsertRow() { return isInsertRow; }

        /** Whether this location represents insertion of columns. */
        public boolean isInsertColumn() { return isInsertCol; }

        public String toString() {
            return getClass().getName()
                   + "[dropPoint=" + getDropPoint() + ","
                   + "row=" + row + ","
                   + "column=" + col + ","
                   + "insertRow=" + isInsertRow + ","
                   + "insertColumn=" + isInsertCol + "]";
        }
    }

    /**
     * Turns on or off automatic drag handling; a press on a cell that moves past
     * the drag threshold (after a long press on a touchscreen) starts a drag
     * through the {@code TransferHandler}.
     *
     * @since 1.4
     */
    public void setDragEnabled(boolean b) {
        dragEnabled = b;
        dragRecognizer = net.runelite.awt.impl.SwingDragSupport.setDragEnabled(this, dragRecognizer, b);
    }

    public boolean getDragEnabled() { return dragEnabled; }

    /**
     * Sets the drop mode for this component; every {@code DropMode} is supported.
     *
     * @throws IllegalArgumentException if the drop mode is <code>null</code>
     * @since 1.6
     */
    public final void setDropMode(DropMode dropMode) {
        if (dropMode == null) throw new IllegalArgumentException(dropMode + ": Unsupported drop mode for table");
        this.dropMode = dropMode;
    }

    public final DropMode getDropMode() { return dropMode; }

    /**
     * Returns the location that this component should visually indicate
     * as the drop location during a DnD operation over the component,
     * or {@code null} if no location is to currently be shown.
     *
     * @since 1.6
     */
    public final DropLocation getDropLocation() { return dropLocation; }

    @Override
    DropLocation dropLocationForPoint(java.awt.Point p) {
        int row = rowAtPoint(p);
        int col = columnAtPoint(p);
        Rectangle rect = row >= 0 && col >= 0 ? getCellRect(row, col, true) : null;
        boolean rows = dropMode == DropMode.INSERT || dropMode == DropMode.INSERT_ROWS
                || dropMode == DropMode.ON_OR_INSERT || dropMode == DropMode.ON_OR_INSERT_ROWS;
        boolean cols = dropMode == DropMode.INSERT || dropMode == DropMode.INSERT_COLS
                || dropMode == DropMode.ON_OR_INSERT || dropMode == DropMode.ON_OR_INSERT_COLS;
        boolean on = dropMode == DropMode.ON_OR_INSERT || dropMode == DropMode.ON_OR_INSERT_ROWS
                || dropMode == DropMode.ON_OR_INSERT_COLS;
        if (!rows && !cols) return new DropLocation(p, row, col, false, false);

        boolean insertRow = false, insertCol = false;
        if (rows) {
            if (row < 0) { row = getRowCount(); insertRow = true; }
            else if (rect != null) {
                int offset = p.y - rect.y, edge = on ? rect.height / 4 : rect.height / 2;
                if (offset < edge) insertRow = true;
                else if (offset >= rect.height - edge) { insertRow = true; row++; }
            }
        }
        if (cols && !insertRow) {
            if (col < 0) { col = getColumnCount(); insertCol = true; }
            else if (rect != null) {
                int offset = p.x - rect.x, edge = on ? rect.width / 4 : rect.width / 2;
                if (offset < edge) insertCol = true;
                else if (offset >= rect.width - edge) { insertCol = true; col++; }
            }
        }
        return new DropLocation(p, row, col, insertRow, insertCol);
    }

    @Override
    Object setDropLocation(TransferHandler.DropLocation location, Object state, boolean forDrop) {
        DropLocation old = dropLocation;
        dropLocation = (DropLocation) location;
        firePropertyChange("dropLocation", old, dropLocation);
        repaint();
        return null;
    }
    public int convertColumnIndexToModel(int column) { return column<0?column:columnModel.getColumn(column).getModelIndex(); }
    public int convertColumnIndexToView(int column) { for(int i=0;i<getColumnCount();i++)if(convertColumnIndexToModel(i)==column)return i;return -1; }
    public void setGridColor(java.awt.Color c) { gridColor = c; repaint(); }
    public java.awt.Color getGridColor() { return gridColor; }
    public void setShowGrid(boolean value) { showGrid = value; repaint(); }
    public void setSelectionBackground(java.awt.Color c) { selectionBackground = c; repaint(); }
    public java.awt.Color getSelectionBackground() { return selectionBackground; }
    public void setSelectionForeground(java.awt.Color c) { selectionForeground = c; repaint(); }
    public java.awt.Color getSelectionForeground() { return selectionForeground; }
    public void setTableHeader(javax.swing.table.JTableHeader header) { tableHeader = header; if (header != null) header.setTable(this); revalidate(); }
    protected TableModel dataModel;
    private javax.swing.table.TableColumnModel columnModel = new javax.swing.table.DefaultTableColumnModel();
    private javax.swing.table.JTableHeader tableHeader = new javax.swing.table.JTableHeader(columnModel);
    private final javax.swing.table.TableCellRenderer defaultRenderer = (table, value, selected, focus, row, col) -> {
        JLabel label = new JLabel(value == null ? "" : value.toString());
        label.setFont(getFont()); label.setForeground(getForeground());
        return label;
    };
    protected int rowHeight = 16;
    protected int rowMargin = 1;
    protected boolean rowSelectionAllowed = true;
    protected boolean columnSelectionAllowed = false;
    protected int autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
    protected boolean autoCreateColumnsFromModel = true;

    public JTable() { this(new javax.swing.table.DefaultTableModel()); }
    public JTable(TableModel model) {
        tableHeader.setTable(this); setModel(model); setOpaque(true);
        selectionModel.addListSelectionListener(selectionListener); columnSelection.addListSelectionListener(e->repaint());
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
        if(sorter!=null){sorter.removeRowSorterListener(sortListener);sorter=null;}
        selectedModelRows.clear();rowHeights.clear();modelAnchor=modelLead=-1;
        selectionModel.clearSelection();columnSelection.clearSelection();
        dataModel = model;
        dataModel.addTableModelListener(this);
        if (autoCreateColumnsFromModel) createDefaultColumnsFromModel();
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
        int first = 0;
        int last = getRowCount();
        for (int row = first; row < last; row++) for (int col = 0; col < getColumnCount(); col++) {
            Rectangle bounds = getCellRect(row, col, false);
            if (clip != null && !clip.intersects(bounds)) continue;
            Component cell = prepareRenderer(getCellRenderer(row, col), row, col);
            java.awt.Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
            cell.setBounds(0, 0, bounds.width, bounds.height); cell.paint(cg); cg.dispose();
            if (showGrid) { g.setColor(gridColor); g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height); }
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
    public void setSelectionModel(ListSelectionModel value) { java.util.Objects.requireNonNull(value);ListSelectionModel old=selectionModel;old.removeListSelectionListener(selectionListener);selectionModel=value;value.addListSelectionListener(selectionListener);rememberSelection();firePropertyChange("selectionModel",old,value); }
    public void setSelectionMode(int mode) { selectionModel.setSelectionMode(mode);columnSelection.setSelectionMode(mode); }
    private int[] selected(ListSelectionModel model,int count) { java.util.List<Integer> list=new java.util.ArrayList<>();for(int i=0;i<count;i++)if(model.isSelectedIndex(i))list.add(i);int[] result=new int[list.size()];for(int i=0;i<result.length;i++)result[i]=list.get(i);return result; }
    public int getSelectedRow() { return selectionModel.getMinSelectionIndex(); }
    public int[] getSelectedRows() { return selected(selectionModel,getRowCount()); }
    public int getSelectedRowCount() { return getSelectedRows().length; }
    public int getSelectedColumn() { return columnSelection.getMinSelectionIndex(); }
    public int[] getSelectedColumns() { return selected(columnSelection,getColumnCount()); }
    public int getSelectedColumnCount() { return getSelectedColumns().length; }
    public boolean isRowSelected(int row) { return selectionModel.isSelectedIndex(row); }
    public boolean isColumnSelected(int col) { return columnSelection.isSelectedIndex(col); }
    public boolean isCellSelected(int row,int col) { return rowSelectionAllowed&&columnSelectionAllowed?isRowSelected(row)&&isColumnSelected(col):rowSelectionAllowed?isRowSelected(row):columnSelectionAllowed&&isColumnSelected(col); }
    public boolean getCellSelectionEnabled() { return rowSelectionAllowed&&columnSelectionAllowed; }
    public void setCellSelectionEnabled(boolean value) { rowSelectionAllowed=columnSelectionAllowed=value;repaint(); }
    public void clearSelection() { selectionModel.clearSelection();columnSelection.clearSelection(); }
    public void selectAll() { if(getRowCount()>0)selectionModel.setSelectionInterval(0,getRowCount()-1);if(getColumnCount()>0)columnSelection.setSelectionInterval(0,getColumnCount()-1); }
    public void changeSelection(int row,int column,boolean toggle,boolean extend) { select(selectionModel,row,toggle,extend);select(columnSelection,column,toggle,extend);scrollRectToVisible(getCellRect(row,column,true)); }
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
    public void addColumnSelectionInterval(int index0, int index1) { columnSelection.addSelectionInterval(index0,index1); }
    public void setRowSelectionInterval(int index0, int index1) { selectionModel.setSelectionInterval(index0,index1); }
    public void setColumnSelectionInterval(int index0, int index1) { columnSelection.setSelectionInterval(index0,index1); }
    public void removeRowSelectionInterval(int index0, int index1) { selectionModel.removeSelectionInterval(index0,index1); }
    public void removeColumnSelectionInterval(int index0, int index1) { columnSelection.removeSelectionInterval(index0,index1); }
    public boolean editCellAt(int row,int column) { return editCellAt(row,column,null); }
    public boolean editCellAt(int row,int column,java.util.EventObject event) {
        if(row<0||column<0||row>=getRowCount()||column>=getColumnCount()||!isCellEditable(row,column))return false;
        if(cellEditor!=null&&!cellEditor.stopCellEditing())return false;
        javax.swing.table.TableCellEditor editor=getCellEditor(row,column);if(editor==null||!editor.isCellEditable(event))return false;
        Component component=editor.getTableCellEditorComponent(this,getValueAt(row,column),isCellSelected(row,column),row,column);
        if(component==null)return false;cellEditor=editor;editingRow=row;editingColumn=column;editingModelRow=convertRowIndexToModel(row);editingModelColumn=convertColumnIndexToModel(column);editorComponent=component;editor.addCellEditorListener(editorListener);add(component);component.setBounds(getCellRect(row,column,false));component.requestFocus();repaint();return true;
    }
    public javax.swing.table.TableCellEditor getCellEditor() { return cellEditor; }
    public javax.swing.table.TableCellEditor getCellEditor(int row,int column) { javax.swing.table.TableCellEditor editor=columnModel.getColumn(column).getCellEditor();return editor!=null?editor:getDefaultEditor(getColumnClass(column)); }
    public void setDefaultEditor(Class<?> type,javax.swing.table.TableCellEditor editor) { if(editor==null)defaultEditors.remove(type);else defaultEditors.put(type,editor); }
    public javax.swing.table.TableCellEditor getDefaultEditor(Class<?> type) {
        if(type==Boolean.class&&!defaultEditors.containsKey(Boolean.class))defaultEditors.put(Boolean.class,new DefaultCellEditor(new JCheckBox()));
        for(Class<?> c=type;c!=null;c=c.getSuperclass())if(defaultEditors.containsKey(c))return defaultEditors.get(c);
        return defaultEditors.computeIfAbsent(Object.class,key->new GenericEditor());
    }
    public void setDefaultRenderer(Class<?> type,javax.swing.table.TableCellRenderer renderer) { if(renderer==null)defaultRenderers.remove(type);else defaultRenderers.put(type,renderer);repaint(); }
    public javax.swing.table.TableCellRenderer getDefaultRenderer(Class<?> type) { for(Class<?> c=type;c!=null;c=c.getSuperclass())if(defaultRenderers.containsKey(c))return defaultRenderers.get(c);return defaultRenderer; }
    public boolean isEditing() { return cellEditor!=null; }
    public int getEditingRow() { return editingRow; }
    public int getEditingColumn() { return editingColumn; }
    public Component getEditorComponent() { return editorComponent; }
    public void removeEditor() { if(cellEditor!=null)cellEditor.removeCellEditorListener(editorListener);if(editorComponent!=null)remove(editorComponent);cellEditor=null;editorComponent=null;editingRow=editingColumn=editingModelRow=editingModelColumn=-1;repaint(); }

    private static final class GenericEditor extends DefaultCellEditor {
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

    public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
        return renderer.getTableCellRendererComponent(this, getValueAt(row, column), isCellSelected(row, column), false, row, column);
    }

    @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return rowHeight; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return orientation==SwingConstants.VERTICAL?visibleRect.height:visibleRect.width; }
    @Override public boolean getScrollableTracksViewportWidth() { return false; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
