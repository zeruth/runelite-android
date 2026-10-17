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
