package javax.accessibility;

/**
 * Two-dimensional table information of an accessible object.
 */
public interface AccessibleTable {

    public Accessible getAccessibleCaption();

    public void setAccessibleCaption(Accessible a);

    public Accessible getAccessibleSummary();

    public void setAccessibleSummary(Accessible a);

    public int getAccessibleRowCount();

    public int getAccessibleColumnCount();

    public Accessible getAccessibleAt(int r, int c);

    public int getAccessibleRowExtentAt(int r, int c);

    public int getAccessibleColumnExtentAt(int r, int c);

    public AccessibleTable getAccessibleRowHeader();

    public void setAccessibleRowHeader(AccessibleTable table);

    public AccessibleTable getAccessibleColumnHeader();

    public void setAccessibleColumnHeader(AccessibleTable table);

    public Accessible getAccessibleRowDescription(int r);

    public void setAccessibleRowDescription(int r, Accessible a);

    public Accessible getAccessibleColumnDescription(int c);

    public void setAccessibleColumnDescription(int c, Accessible a);

    public boolean isAccessibleSelected(int r, int c);

    public boolean isAccessibleRowSelected(int r);

    public boolean isAccessibleColumnSelected(int c);

    public int[] getSelectedAccessibleRows();

    public int[] getSelectedAccessibleColumns();
}
