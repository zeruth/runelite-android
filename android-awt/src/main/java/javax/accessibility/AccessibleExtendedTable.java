package javax.accessibility;

/**
 * Index conversion between a table's child index and its row and column.
 */
public interface AccessibleExtendedTable extends AccessibleTable {

    public int getAccessibleRow(int index);

    public int getAccessibleColumn(int index);

    public int getAccessibleIndex(int r, int c);
}
