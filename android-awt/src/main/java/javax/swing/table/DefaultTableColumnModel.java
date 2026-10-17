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
