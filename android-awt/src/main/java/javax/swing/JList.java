package javax.swing;

import java.util.ArrayList;
import java.util.Vector;
import javax.swing.event.ListSelectionListener;

public class JList<E> extends JComponent implements Scrollable {
    private static final long serialVersionUID = -1697842831055920921L;

    public static final int VERTICAL = 0;
    public static final int VERTICAL_WRAP = 1;
    public static final int HORIZONTAL_WRAP = 2;

    private ListModel<E> model = new DefaultListModel<>();
    private ListCellRenderer<? super E> renderer = new DefaultListCellRenderer();
    private ListSelectionModel selectionModel;
    private final javax.swing.event.ListDataListener dataListener = new javax.swing.event.ListDataListener() {
        public void intervalAdded(javax.swing.event.ListDataEvent e) { selectionModel.insertIndexInterval(e.getIndex0(), e.getIndex1()-e.getIndex0()+1, true); revalidate(); repaint(); }
        public void intervalRemoved(javax.swing.event.ListDataEvent e) { selectionModel.removeIndexInterval(e.getIndex0(), e.getIndex1()); revalidate(); repaint(); }
        public void contentsChanged(javax.swing.event.ListDataEvent e) { revalidate(); repaint(); }
    };
    private final ListSelectionListener selectionForwarder = e -> { fireSelectionChanged(e.getFirstIndex(), e.getLastIndex(), e.getValueIsAdjusting()); repaint(); };

    private int layoutOrientation = VERTICAL;
    private int visibleRowCount = 8;
    private int fixedCellWidth = -1, fixedCellHeight = -1;

    public JList() {
        setSelectionModel(new DefaultListSelectionModel());
        model.addListDataListener(dataListener);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!isEnabled() || e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                int index=locationToIndex(e.getPoint());
                if (index<0 || !getCellBounds(index,index).contains(e.getPoint())) return;
                if (e.isShiftDown() && selectionModel.getAnchorSelectionIndex()>=0) setSelectionInterval(selectionModel.getAnchorSelectionIndex(),index);
                else if (e.isControlDown()) { if(isSelectedIndex(index))removeSelectionInterval(index,index);else addSelectionInterval(index,index); }
                else setSelectedIndex(index);
                requestFocusInWindow();
            }
        });
    }
    public JList(ListModel<E> model) { this(); setModel(model); }
    public JList(E[] data) { this(); setListData(data); }
    public JList(Vector<? extends E> data) { this(); setListData(data); }

    public ListModel<E> getModel() { return model; }
    public void setModel(ListModel<E> model) {
        java.util.Objects.requireNonNull(model); ListModel<E> old=this.model;
        if(old!=null)old.removeListDataListener(dataListener);
        this.model=model; model.addListDataListener(dataListener); clearSelection();
        firePropertyChange("model",old,model); revalidate(); repaint();
    }
    public void setListData(E[] listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        setModel(m);
    }
    public void setListData(Vector<? extends E> listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        setModel(m);
    }

    public ListCellRenderer<? super E> getCellRenderer() { return renderer; }
    private java.awt.Color selectionBackground = new java.awt.Color(0x395777);
    private java.awt.Color selectionForeground = java.awt.Color.WHITE;
    public java.awt.Color getSelectionBackground() { return selectionBackground; }
    public java.awt.Color getSelectionForeground() { return selectionForeground; }
    public void setSelectionBackground(java.awt.Color color) {
        java.awt.Color old = selectionBackground;
        selectionBackground = color;
        firePropertyChange("selectionBackground", old, color);
        repaint();
    }
    public void setSelectionForeground(java.awt.Color color) {
        java.awt.Color old = selectionForeground;
        selectionForeground = color;
        firePropertyChange("selectionForeground", old, color);
        repaint();
    }
    public void setCellRenderer(ListCellRenderer<? super E> r) {
        ListCellRenderer<? super E> old = renderer;
        renderer = r;
        if (r != old) measurePrototype();
        firePropertyChange("cellRenderer", old, r);
        revalidate(); repaint();
    }
    private E prototypeCellValue;
    public E getPrototypeCellValue() { return prototypeCellValue; }
    public void setPrototypeCellValue(E value) {
        E old = prototypeCellValue;
        prototypeCellValue = value;
        if (value != null && !value.equals(old)) measurePrototype();
        firePropertyChange("prototypeCellValue", old, value);
        revalidate(); repaint();
    }
    private void measurePrototype() {
        if (prototypeCellValue == null || renderer == null) return;
        java.awt.Component cell = renderer.getListCellRendererComponent(this, prototypeCellValue, 0, false, false);
        java.awt.Font previous = cell.getFont();
        cell.setFont(getFont());
        java.awt.Dimension size = cell.getPreferredSize();
        cell.setFont(previous);
        fixedCellWidth = size.width;
        fixedCellHeight = size.height;
    }

    public ListSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(ListSelectionModel value) {
        java.util.Objects.requireNonNull(value); ListSelectionModel old=selectionModel;
        if(old!=null)old.removeListSelectionListener(selectionForwarder);
        selectionModel=value; value.addListSelectionListener(selectionForwarder);
        firePropertyChange("selectionModel",old,value); repaint();
    }
    public int getSelectedIndex() { return selectionModel.getMinSelectionIndex(); }
    public void setSelectedIndex(int index) { if(index>=model.getSize())return; if(index<0)clearSelection();else selectionModel.setSelectionInterval(index,index); }
    public void setSelectedIndices(int[] indices) {
        selectionModel.setValueIsAdjusting(true);
        try { clearSelection(); for(int i:indices)if(i>=0&&i<model.getSize())selectionModel.addSelectionInterval(i,i); }
        finally { selectionModel.setValueIsAdjusting(false); }
    }
    public int[] getSelectedIndices() { java.util.List<Integer> result=new ArrayList<>();for(int i=0;i<model.getSize();i++)if(isSelectedIndex(i))result.add(i);int[] indices=new int[result.size()];for(int i=0;i<indices.length;i++)indices[i]=result.get(i);return indices; }
    public E getSelectedValue() { int index=getSelectedIndex();return index<0||index>=model.getSize()?null:model.getElementAt(index); }
    public void setSelectedValue(Object value,boolean scroll) { for(int i=0;i<model.getSize();i++)if(java.util.Objects.equals(value,model.getElementAt(i))){setSelectedIndex(i);if(scroll)ensureIndexIsVisible(i);return;}clearSelection(); }
    public java.util.List<E> getSelectedValuesList() { ArrayList<E> result=new ArrayList<>();for(int index:getSelectedIndices())result.add(model.getElementAt(index));return result; }
    public Object[] getSelectedValues() { return getSelectedValuesList().toArray(); }
    public boolean isSelectedIndex(int index) { return selectionModel.isSelectedIndex(index); }
    public boolean isSelectionEmpty() { return selectionModel.isSelectionEmpty(); }
    public int getMinSelectionIndex() { return selectionModel.getMinSelectionIndex(); }
    public int getMaxSelectionIndex() { return selectionModel.getMaxSelectionIndex(); }
    public int getAnchorSelectionIndex() { return selectionModel.getAnchorSelectionIndex(); }
    public int getLeadSelectionIndex() { return selectionModel.getLeadSelectionIndex(); }
    public void setSelectionInterval(int start,int end) { selectionModel.setSelectionInterval(start,end); }
    public void addSelectionInterval(int start,int end) { selectionModel.addSelectionInterval(start,end); }
    public void removeSelectionInterval(int start,int end) { selectionModel.removeSelectionInterval(start,end); }
    public void setValueIsAdjusting(boolean value) { selectionModel.setValueIsAdjusting(value); }
    public boolean getValueIsAdjusting() { return selectionModel.getValueIsAdjusting(); }
    public int getSelectionMode() { return selectionModel.getSelectionMode(); }
    public void setSelectionMode(int mode) { selectionModel.setSelectionMode(mode); }
    public int getLayoutOrientation() { return layoutOrientation; }
    public void setLayoutOrientation(int o) { this.layoutOrientation = o; }
    public int getVisibleRowCount() { return visibleRowCount; }
    public void setVisibleRowCount(int n) { this.visibleRowCount = n; }
    public int getFixedCellWidth() { return fixedCellWidth; }
    public void setFixedCellWidth(int w) { this.fixedCellWidth = w; }
    public int getFixedCellHeight() { return fixedCellHeight; }
    public void setFixedCellHeight(int h) { this.fixedCellHeight = h; }

    private final java.util.List<ListSelectionListener> selectionListeners = new java.util.ArrayList<>();
    public void addListSelectionListener(ListSelectionListener l) { if (l != null) selectionListeners.add(l); }
    public void removeListSelectionListener(ListSelectionListener l) { selectionListeners.remove(l); }
    public ListSelectionListener[] getListSelectionListeners() { return selectionListeners.toArray(new ListSelectionListener[0]); }

    private void fireSelectionChanged(int firstIndex, int lastIndex, boolean adjusting) {
        javax.swing.event.ListSelectionEvent ev = new javax.swing.event.ListSelectionEvent(
            this, Math.max(0, firstIndex), Math.max(0, lastIndex), adjusting);
        for (ListSelectionListener l : new java.util.ArrayList<>(selectionListeners)) {
            l.valueChanged(ev);
        }
    }

    /** Geometry is shared by painting and hit testing, including variable-height renderers. */
    private java.awt.Rectangle[] cellBounds() {
        int count = model == null ? 0 : model.getSize();
        java.awt.Rectangle[] cells = new java.awt.Rectangle[count];
        java.awt.Insets in = getInsets();
        int cw = Math.max(1, fixedCellWidth), ch = Math.max(1, fixedCellHeight);
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            java.awt.Component c = renderer == null ? null : renderer.getListCellRendererComponent(
                this, model.getElementAt(i), i, isSelectedIndex(i), false);
            java.awt.Dimension d = c == null ? new java.awt.Dimension(0, 16) : c.getPreferredSize();
            if (fixedCellWidth < 0) cw = Math.max(cw, d.width);
            heights[i] = fixedCellHeight >= 0 ? fixedCellHeight : Math.max(1, d.height);
            ch = Math.max(ch, heights[i]);
        }
        if (layoutOrientation == VERTICAL) {
            int y = in.top;
            int w = Math.max(cw, getWidth() - in.left - in.right);
            for (int i = 0; i < count; i++) {
                cells[i] = new java.awt.Rectangle(in.left, y, w, heights[i]);
                y += heights[i];
            }
        } else {
            int rows, columns;
            if (visibleRowCount > 0) {
                rows = visibleRowCount;
                columns = Math.max(1, (count + rows - 1) / rows);
            } else if (layoutOrientation == HORIZONTAL_WRAP) {
                columns = Math.max(1, (getWidth() - in.left - in.right) / cw);
                rows = Math.max(1, (count + columns - 1) / columns);
            } else {
                rows = Math.max(1, (getHeight() - in.top - in.bottom) / ch);
                columns = Math.max(1, (count + rows - 1) / rows);
            }
            for (int i = 0; i < count; i++) {
                int col = layoutOrientation == HORIZONTAL_WRAP ? i % columns : i / rows;
                int row = layoutOrientation == HORIZONTAL_WRAP ? i / columns : i % rows;
                cells[i] = new java.awt.Rectangle(in.left + col * cw, in.top + row * ch, cw, ch);
            }
        }
        return cells;
    }

    public java.awt.Rectangle getCellBounds(int index0, int index1) {
        java.awt.Rectangle[] cells = cellBounds();
        int first = Math.min(index0, index1), last = Math.max(index0, index1);
        if (first < 0 || first >= cells.length) return null;
        java.awt.Rectangle bounds = new java.awt.Rectangle(cells[first]);
        for (int i = first + 1; i <= Math.min(last, cells.length - 1); i++) bounds = bounds.union(cells[i]);
        return bounds;
    }
    public int locationToIndex(java.awt.Point point) {
        java.util.Objects.requireNonNull(point);
        java.awt.Rectangle[] cells = cellBounds();
        int nearest = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cells.length; i++) {
            java.awt.Rectangle b = cells[i];
            double dx = Math.max(b.x - point.x, Math.max(0, point.x - (b.x + b.width - 1)));
            double dy = Math.max(b.y - point.y, Math.max(0, point.y - (b.y + b.height - 1)));
            double d = dx * dx + dy * dy;
            if (d < distance) { distance = d; nearest = i; }
        }
        return nearest;
    }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Rectangle[] cells = cellBounds();
        java.awt.Insets in = getInsets();
        int w = in.left, h = in.top;
        for (java.awt.Rectangle b : cells) { w = Math.max(w, b.x + b.width); h = Math.max(h, b.y + b.height); }
        return new java.awt.Dimension(w + in.right, h + in.bottom);
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        if (isOpaque()) { g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight()); }
        if (renderer == null) return;
        java.awt.Rectangle[] cells = cellBounds();
        java.awt.Rectangle clip = g.getClipBounds();
        for (int i = 0; i < cells.length; i++) {
            java.awt.Rectangle b = cells[i];
            if (clip != null && !clip.intersects(b)) continue;
            java.awt.Component c = renderer.getListCellRendererComponent(this, model.getElementAt(i), i, isSelectedIndex(i), false);
            if (c == null) continue;
            c.setBounds(0, 0, b.width, b.height);
            if (c instanceof java.awt.Container) ((java.awt.Container) c).validate();
            java.awt.Graphics child = g.create(b.x, b.y, b.width, b.height);
            try { c.paint(child); } finally { child.dispose(); }
        }
    }

    public void clearSelection() { selectionModel.clearSelection(); }
    public void ensureIndexIsVisible(int index) { java.awt.Rectangle bounds=getCellBounds(index,index);if(bounds!=null)scrollRectToVisible(bounds); }
    public java.awt.Point indexToLocation(int index) { java.awt.Rectangle bounds=getCellBounds(index,index);return bounds==null?null:bounds.getLocation(); }
    public java.awt.Dimension getPreferredScrollableViewportSize() { java.awt.Dimension size=getPreferredSize();return new java.awt.Dimension(size.width,Math.max(1,visibleRowCount)*(fixedCellHeight>0?fixedCellHeight:16)); }
    public int getScrollableUnitIncrement(java.awt.Rectangle visible,int orientation,int direction) { return fixedCellHeight>0?fixedCellHeight:16; }
    public int getScrollableBlockIncrement(java.awt.Rectangle visible,int orientation,int direction) { return orientation==SwingConstants.VERTICAL?visible.height:visible.width; }
    public boolean getScrollableTracksViewportWidth() { return layoutOrientation==VERTICAL; }
    public boolean getScrollableTracksViewportHeight() { return false; }
}
