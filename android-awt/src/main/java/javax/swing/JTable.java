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
    public JTable(TableModel model) { tableHeader.setTable(this); setModel(model); setOpaque(true); }
    public JTable(Object[][] rows, Object[] columns) { this(new javax.swing.table.DefaultTableModel(rows, columns)); }
    public JTable(int rows, int columns) { this(new javax.swing.table.DefaultTableModel(rows, columns)); }
    public JTable(java.util.Vector<? extends java.util.Vector> rows, java.util.Vector<?> columns) {
        this(new javax.swing.table.DefaultTableModel(rows, columns));
    }
    public TableModel getModel() { return dataModel; }
    public void setModel(TableModel model) {
        if (model == null) throw new IllegalArgumentException("model");
        if (dataModel != null) dataModel.removeTableModelListener(this);
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
                revalidate(); repaint(); tableHeader.revalidate(); tableHeader.repaint();
            });
            columnModel.addColumn(column);
        }
        tableHeader.revalidate(); tableHeader.repaint();
    }
    @Override public void tableChanged(javax.swing.event.TableModelEvent event) {
        if ((event == null || event.getFirstRow() == javax.swing.event.TableModelEvent.HEADER_ROW)
                && autoCreateColumnsFromModel) createDefaultColumnsFromModel();
        revalidate(); repaint();
    }
    public javax.swing.table.TableCellRenderer getCellRenderer(int row, int column) {
        javax.swing.table.TableCellRenderer renderer = columnModel.getColumn(column).getCellRenderer();
        return renderer == null ? defaultRenderer : renderer;
    }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return new java.awt.Dimension(columnModel.getTotalColumnWidth(), getRowCount() * rowHeight);
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        int first = clip == null ? 0 : Math.max(0, clip.y / rowHeight);
        int last = clip == null ? getRowCount() : Math.min(getRowCount(), (clip.y + clip.height) / rowHeight + 1);
        for (int row = first; row < last; row++) for (int col = 0; col < getColumnCount(); col++) {
            Rectangle bounds = getCellRect(row, col, false);
            if (clip != null && !clip.intersects(bounds)) continue;
            Component cell = prepareRenderer(getCellRenderer(row, col), row, col);
            java.awt.Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
            cell.setBounds(0, 0, bounds.width, bounds.height); cell.paint(cg); cg.dispose();
        }
    }
    public int getRowHeight() { return rowHeight; }
    public void setRowHeight(int h) { if (h <= 0) throw new IllegalArgumentException("rowHeight"); this.rowHeight = h; revalidate(); repaint(); }
    public int getRowHeight(int row) { return rowHeight; }
    public void setRowHeight(int row, int h) {}
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
    public int getSelectedRow() { return -1; }
    public int[] getSelectedRows() { return new int[0]; }
    public int getSelectedRowCount() { return 0; }
    public int getSelectedColumn() { return -1; }
    public int[] getSelectedColumns() { return new int[0]; }
    public int getSelectedColumnCount() { return 0; }
    public boolean isRowSelected(int row) { return false; }
    public boolean isColumnSelected(int col) { return false; }
    public boolean isCellSelected(int row, int col) { return false; }
    public void clearSelection() {}
    public void selectAll() {}
    public Object getValueAt(int row, int col) { return dataModel.getValueAt(row, columnModel.getColumn(col).getModelIndex()); }
    public void setValueAt(Object value, int row, int col) { dataModel.setValueAt(value, row, columnModel.getColumn(col).getModelIndex()); }
    public int getRowCount() { return dataModel.getRowCount(); }
    public int getColumnCount() { return columnModel.getColumnCount(); }
    public String getColumnName(int col) { return dataModel.getColumnName(col); }
    public Class<?> getColumnClass(int col) { return dataModel.getColumnClass(col); }
    public boolean isCellEditable(int row, int col) { return dataModel.isCellEditable(row, col); }
    public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
        int x = 0;
        for (int i = 0; i < column; i++) x += columnModel.getColumn(i).getWidth();
        int gap = includeSpacing ? 0 : columnModel.getColumnMargin();
        return new Rectangle(x, row * rowHeight, Math.max(0, columnModel.getColumn(column).getWidth() - gap),
            Math.max(0, rowHeight - (includeSpacing ? 0 : rowMargin)));
    }
    public void addRowSelectionInterval(int index0, int index1) {}
    public void addColumnSelectionInterval(int index0, int index1) {}
    public void setRowSelectionInterval(int index0, int index1) {}
    public void setColumnSelectionInterval(int index0, int index1) {}
    public void removeRowSelectionInterval(int index0, int index1) {}
    public void removeColumnSelectionInterval(int index0, int index1) {}
    public void scrollRectToVisible(Rectangle aRect) {}
    public boolean editCellAt(int row, int column) { return false; }
    public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
        return renderer.getTableCellRendererComponent(this, getValueAt(row, column), isCellSelected(row, column), false, row, column);
    }

    @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return rowHeight; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 1; }
    @Override public boolean getScrollableTracksViewportWidth() { return false; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
