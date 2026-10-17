package javax.swing.table;

public interface TableColumnModel {
    void addColumn(TableColumn column);
    void removeColumn(TableColumn column);
    void moveColumn(int from, int to);
    int getColumnCount();
    java.util.Enumeration<TableColumn> getColumns();
    int getColumnIndex(Object identifier);
    TableColumn getColumn(int index);
    int getColumnIndexAtX(int x);
    int getTotalColumnWidth();
    void setColumnMargin(int margin);
    int getColumnMargin();
}
