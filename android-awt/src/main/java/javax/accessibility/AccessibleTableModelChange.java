package javax.accessibility;

/**
 * A change to a table model, carried as the new value of an
 * {@link AccessibleContext#ACCESSIBLE_TABLE_MODEL_CHANGED} event.
 */
public interface AccessibleTableModelChange {

    /** Identifies the insertion of new rows and/or columns. */
    public static final int INSERT = 1;

    /** Identifies a change to existing data. */
    public static final int UPDATE = 0;

    /** Identifies the deletion of rows and/or columns. */
    public static final int DELETE = -1;

    public int getType();

    public int getFirstRow();

    public int getLastRow();

    public int getFirstColumn();

    public int getLastColumn();
}
