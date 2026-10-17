package javax.swing.table;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class TableColumn implements java.io.Serializable {
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
