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

import java.util.ArrayList;
import java.util.Vector;
import javax.swing.event.ListSelectionListener;

public class JList<E> extends JComponent implements Scrollable, javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJList();
        }
        return accessibleContext;
    }

    /**
     * Accessibility support for lists: role LIST with one child per model element, whatever
     * is scrolled into view; MULTISELECTABLE unless the selection mode is single.
     */
    protected class AccessibleJList extends AccessibleJComponent {
        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            if (getSelectionMode() != ListSelectionModel.SINGLE_SELECTION) {
                states.add(javax.accessibility.AccessibleState.MULTISELECTABLE);
            }
            return states;
        }

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LIST;
        }

        public int getAccessibleChildrenCount() {
            return getModel().getSize();
        }

        public javax.accessibility.Accessible getAccessibleChild(int i) {
            if (i < 0 || i >= getModel().getSize()) {
                return null;
            }
            return new net.runelite.awt.impl.AccessibleCell(JList.this, i, getModel().getElementAt(i), isSelectedIndex(i));
        }
    }
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
    private final ListSelectionListener selectionForwarder = e -> { fireSelectionValueChanged(e.getFirstIndex(), e.getLastIndex(), e.getValueIsAdjusting()); repaint(); };

    private int layoutOrientation = VERTICAL;
    private int visibleRowCount = 8;
    private int fixedCellWidth = -1, fixedCellHeight = -1;
    /**
     * The list's own BasicListUI, uninstalled, that measures and paints while no look and
     * feel installed a ListUI: the geometry is then the JDK's in every orientation.
     */
    private transient javax.swing.plaf.basic.BasicListUI ownUI;

    public JList() {
        setSelectionModel(createSelectionModel());
        model.addListDataListener(dataListener);
        setAutoscrolls(true);
        setOpaque(true);
        // As the JDK: per-cell tips come from the renderers through getToolTipText(MouseEvent).
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!isEnabled() || e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                int index=locationToIndex(e.getPoint());
                if (index<0 || !getCellBounds(index,index).contains(e.getPoint())) return;
                pendingSelect = -1;
                if (e.isShiftDown() && selectionModel.getAnchorSelectionIndex()>=0) setSelectionInterval(selectionModel.getAnchorSelectionIndex(),index);
                else if (e.isControlDown()) { if(isSelectedIndex(index))removeSelectionInterval(index,index);else addSelectionInterval(index,index); }
                // As BasicListUI: a press on a selected cell may start a drag of the whole
                // selection, so collapsing it to the cell waits for the release.
                else if (dragEnabled && isSelectedIndex(index)) pendingSelect = index;
                else setSelectedIndex(index);
                requestFocusInWindow();
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                int index = pendingSelect;
                pendingSelect = -1;
                if (index >= 0 && index < model.getSize() && dropLocation == null
                        && net.runelite.awt.impl.DragDropHost.current() == null) setSelectedIndex(index);
            }
        });
        setTransferHandler(new ListTransferHandler());
    }

    private int pendingSelect = -1;
    private boolean dragEnabled;
    private DropMode dropMode = DropMode.USE_SELECTION;
    private transient DropLocation dropLocation;
    private transient java.awt.dnd.DragGestureRecognizer dragRecognizer;

    /**
     * A subclass of <code>TransferHandler.DropLocation</code> representing
     * a drop location for a <code>JList</code>.
     *
     * @see #getDropLocation
     * @since 1.6
     */
    public static final class DropLocation extends TransferHandler.DropLocation {
        private final int index;
        private final boolean isInsert;

        private DropLocation(java.awt.Point p, int index, boolean isInsert) {
            super(p);
            this.index = index;
            this.isInsert = isInsert;
        }

        /**
         * Returns the index where dropped data should be placed in the
         * list. Interpretation of the value depends on the drop mode set on
         * the associated component. If the drop mode is either
         * <code>DropMode.USE_SELECTION</code> or <code>DropMode.ON</code>,
         * the return value is an index of a row in the list. If the drop mode is
         * <code>DropMode.INSERT</code>, the return value refers to the index
         * where the data should be inserted. If the drop mode is
         * <code>DropMode.ON_OR_INSERT</code>, the value of
         * <code>isInsert()</code> indicates whether the index is an index
         * of a row, or an insert index.
         * <p>
         * <code>-1</code> indicates that the drop occurred over empty space,
         * and no index could be calculated.
         *
         * @return the drop index
         */
        public int getIndex() {
            return index;
        }

        /**
         * Returns whether or not this location represents an insert
         * location.
         *
         * @return whether or not this is an insert location
         */
        public boolean isInsert() {
            return isInsert;
        }

        /**
         * Returns a string representation of this drop location.
         *
         * @return a string representation of this drop location
         */
        public String toString() {
            return getClass().getName()
                   + "[dropPoint=" + getDropPoint() + ","
                   + "index=" + index + ","
                   + "insert=" + isInsert + "]";
        }
    }

    /**
     * Turns on or off automatic drag handling. When enabled, a press on a
     * selected cell that moves past the drag threshold (on a touchscreen, after a
     * long press) starts a drag through the {@code TransferHandler}.
     *
     * @param b whether or not to enable automatic drag handling
     * @since 1.4
     */
    public void setDragEnabled(boolean b) {
        dragEnabled = b;
        dragRecognizer = net.runelite.awt.impl.SwingDragSupport.setDragEnabled(this, dragRecognizer, b);
    }

    public boolean getDragEnabled() { return dragEnabled; }

    /**
     * Sets the drop mode for this component. For backward compatibility,
     * the default for this property is <code>DropMode.USE_SELECTION</code>.
     *
     * @param dropMode the drop mode to use
     * @throws IllegalArgumentException if the drop mode is unsupported
     *         or <code>null</code>
     * @since 1.6
     */
    public final void setDropMode(DropMode dropMode) {
        if (dropMode != null) {
            switch (dropMode) {
                case USE_SELECTION:
                case ON:
                case INSERT:
                case ON_OR_INSERT:
                    this.dropMode = dropMode;
                    return;
                default:
                    break;
            }
        }

        throw new IllegalArgumentException(dropMode + ": Unsupported drop mode for list");
    }

    public final DropMode getDropMode() { return dropMode; }

    /**
     * Returns the location that this component should visually indicate
     * as the drop location during a DnD operation over the component,
     * or {@code null} if no location is to currently be shown.
     *
     * @return the drop location
     * @since 1.6
     */
    public final DropLocation getDropLocation() { return dropLocation; }

    @Override
    DropLocation dropLocationForPoint(java.awt.Point p) {
        int size = model.getSize();
        int index = locationToIndex(p);
        java.awt.Rectangle rect = index != -1 ? getCellBounds(index, index) : null;
        boolean horizontal = layoutOrientation == HORIZONTAL_WRAP;

        switch (dropMode) {
            case USE_SELECTION:
            case ON:
                return new DropLocation(p, (rect != null && rect.contains(p)) ? index : -1, false);
            case INSERT:
                if (index == -1) return new DropLocation(p, size, true);
                if (trailingHalf(rect, p, horizontal)) index++;
                return new DropLocation(p, index, true);
            case ON_OR_INSERT:
                if (index == -1) return new DropLocation(p, size, true);
                int section = section(rect, p, horizontal);
                if (section < 0) return new DropLocation(p, index, true);
                if (section > 0) return new DropLocation(p, index + 1, true);
                return new DropLocation(p, index, false);
            default:
                throw new AssertionError("Unexpected drop mode");
        }
    }

    private static boolean trailingHalf(java.awt.Rectangle r, java.awt.Point p, boolean horizontal) {
        return horizontal ? p.x >= r.x + r.width / 2 : p.y >= r.y + r.height / 2;
    }

    /** -1 for the leading quarter, 1 for the trailing quarter, 0 between, as SwingUtilities2.liesIn. */
    private static int section(java.awt.Rectangle r, java.awt.Point p, boolean horizontal) {
        int pos = horizontal ? p.x - r.x : p.y - r.y;
        int length = horizontal ? r.width : r.height;
        int edge = length / 4;
        if (pos < edge) return -1;
        if (pos >= length - edge) return 1;
        return 0;
    }

    @Override
    Object setDropLocation(TransferHandler.DropLocation location, Object state, boolean forDrop) {
        Object retVal = null;
        DropLocation listLocation = (DropLocation) location;

        if (dropMode == DropMode.USE_SELECTION) {
            if (listLocation == null) {
                if (!forDrop && state != null) {
                    // The drag left or was cancelled: the selection it borrowed comes back.
                    setSelectedIndices((int[]) state);
                }
            } else {
                retVal = dropLocation == null ? getSelectedIndices() : state;
                int index = listLocation.getIndex();
                if (index == -1) clearSelection();
                else setSelectionInterval(index, index);
            }
        }

        DropLocation old = dropLocation;
        dropLocation = listLocation;
        firePropertyChange("dropLocation", old, dropLocation);
        repaint();
        return retVal;
    }

    /** BasicListUI's handler: exports the selected values as text, imports nothing. */
    @SuppressWarnings("serial")
    static class ListTransferHandler extends TransferHandler implements javax.swing.plaf.UIResource {
        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            if (!(c instanceof JList)) return null;
            java.util.List<?> values = ((JList<?>) c).getSelectedValuesList();
            if (values.isEmpty()) return null;
            StringBuilder text = new StringBuilder();
            for (Object value : values) {
                if (text.length() > 0) text.append('\n');
                text.append(value == null ? "" : value.toString());
            }
            return new java.awt.datatransfer.StringSelection(text.toString());
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }
    public JList(ListModel<E> model) { this(); setModel(model); }
    public JList(E[] data) { this(); setListData(data); }
    public JList(Vector<? extends E> data) { this(); setListData(data); }

    /**
     * Returns the {@code ListUI}, the look and feel object that renders this
     * component.
     *
     * @return the {@code ListUI} object that renders this component, or
     *         {@code null} while no look and feel installed one (the list
     *         then measures and paints with its own {@code BasicListUI})
     */
    @Override
    public javax.swing.plaf.ListUI getUI() {
        javax.swing.plaf.ComponentUI delegate = super.getUI();
        return delegate instanceof javax.swing.plaf.ListUI ? (javax.swing.plaf.ListUI) delegate : null;
    }

    /**
     * Sets the {@code ListUI}, the look and feel object that renders this
     * component.
     *
     * @param ui  the {@code ListUI} object
     */
    public void setUI(javax.swing.plaf.ListUI ui) {
        super.setUI(ui);
    }

    /**
     * Returns {@code "ListUI"}, the {@code UIDefaults} key used to look up the
     * name of the {@code javax.swing.plaf.ListUI} class that defines the look
     * and feel for this component.
     *
     * @return the string "ListUI"
     */
    @Override
    public String getUIClassID() {
        return "ListUI";
    }

    /** The delegate geometry queries go to: the installed ListUI, or the list's own BasicListUI. */
    private javax.swing.plaf.ListUI layoutUI() {
        javax.swing.plaf.ListUI delegate = getUI();
        return delegate != null ? delegate : ownUI();
    }

    private javax.swing.plaf.basic.BasicListUI ownUI() {
        if (ownUI == null) ownUI = new javax.swing.plaf.basic.BasicListUI();
        return ownUI;
    }

    /**
     * Returns an instance of {@code DefaultListSelectionModel}; called
     * during construction to initialize the list's selection model
     * property.
     *
     * @return a {@code DefaultListSelecitonModel}, used to initialize
     *         the list's selection model property during construction
     */
    protected ListSelectionModel createSelectionModel() {
        return new DefaultListSelectionModel();
    }

    public ListModel<E> getModel() { return model; }
    public void setModel(ListModel<E> model) {
        if (model == null) throw new IllegalArgumentException("model must be non null");
        ListModel<E> old=this.model;
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
        // As the JDK: a renderer change re-measures the prototype into the fixed cell size.
        if (r != null && !r.equals(old)) updateFixedCellSize();
        firePropertyChange("cellRenderer", old, r);
        revalidate(); repaint();
    }
    private E prototypeCellValue;
    public E getPrototypeCellValue() { return prototypeCellValue; }
    public void setPrototypeCellValue(E value) {
        E old = prototypeCellValue;
        prototypeCellValue = value;
        if (value != null && !value.equals(old)) updateFixedCellSize();
        firePropertyChange("prototypeCellValue", old, value);
        revalidate(); repaint();
    }
    private void updateFixedCellSize() {
        ListCellRenderer<? super E> cr = renderer;
        E value = prototypeCellValue;
        if (cr == null || value == null) return;
        java.awt.Component cell = cr.getListCellRendererComponent(this, value, 0, false, false);
        // Renderers that use the list's font measure with it, then get their own back.
        java.awt.Font previous = cell.getFont();
        cell.setFont(getFont());
        java.awt.Dimension size = cell.getPreferredSize();
        fixedCellWidth = size.width;
        fixedCellHeight = size.height;
        cell.setFont(previous);
    }

    public ListSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(ListSelectionModel value) {
        if (value == null) throw new IllegalArgumentException("selectionModel must be non null");
        ListSelectionModel old=selectionModel;
        if(old!=null)old.removeListSelectionListener(selectionForwarder);
        selectionModel=value; value.addListSelectionListener(selectionForwarder);
        firePropertyChange("selectionModel",old,value); repaint();
    }
    public int getSelectedIndex() { return selectionModel.getMinSelectionIndex(); }

    /**
     * Selects a single cell. Does nothing if the given index is greater
     * than or equal to the model size; a negative index is handed to the
     * selection model, which ignores {@code -1} as the JDK's does.
     *
     * @param index the index of the cell to select
     */
    public void setSelectedIndex(int index) {
        if (index >= model.getSize()) return;
        selectionModel.setSelectionInterval(index, index);
    }

    /**
     * Changes the selection to be the set of indices specified by the given
     * array. Indices greater than or equal to the model size are ignored.
     *
     * @param indices an array of the indices of the cells to select,
     *                {@code non-null}
     * @throws NullPointerException if the given array is {@code null}
     */
    public void setSelectedIndices(int[] indices) {
        ListSelectionModel sm = selectionModel;
        sm.clearSelection();
        int size = model.getSize();
        for (int i : indices) {
            if (i < size) sm.addSelectionInterval(i, i);
        }
    }

    public int[] getSelectedIndices() {
        ListSelectionModel sm = selectionModel;
        int iMin = sm.getMinSelectionIndex();
        int iMax = sm.getMaxSelectionIndex();
        if (iMin < 0 || iMax < 0) return new int[0];
        int[] rvTmp = new int[1 + (iMax - iMin)];
        int n = 0;
        for (int i = iMin; i <= iMax; i++) {
            if (sm.isSelectedIndex(i)) rvTmp[n++] = i;
        }
        int[] rv = new int[n];
        System.arraycopy(rvTmp, 0, rv, 0, n);
        return rv;
    }
    public E getSelectedValue() { int index=getMinSelectionIndex();return index<0||index>=model.getSize()?null:model.getElementAt(index); }

    /**
     * Selects the specified object from the list, scrolling to it when
     * {@code shouldScroll}; a {@code null} or absent object leaves the
     * selection to {@code setSelectedIndex(-1)}, as the JDK.
     *
     * @param anObject     the object to select
     * @param shouldScroll {@code true} if the list should scroll to display
     *                     the selected object, if one exists; otherwise {@code false}
     */
    public void setSelectedValue(Object anObject, boolean shouldScroll) {
        if (anObject == null) {
            setSelectedIndex(-1);
        } else if (!anObject.equals(getSelectedValue())) {
            for (int i = 0, c = model.getSize(); i < c; i++) {
                if (anObject.equals(model.getElementAt(i))) {
                    setSelectedIndex(i);
                    if (shouldScroll) ensureIndexIsVisible(i);
                    repaint();
                    return;
                }
            }
            setSelectedIndex(-1);
        }
        repaint();
    }
    public java.util.List<E> getSelectedValuesList() {
        ListSelectionModel sm = selectionModel;
        int iMin = sm.getMinSelectionIndex();
        int iMax = sm.getMaxSelectionIndex();
        int size = model.getSize();
        if (iMin < 0 || iMax < 0 || iMin >= size) return java.util.Collections.emptyList();
        iMax = iMax < size ? iMax : size - 1;
        java.util.List<E> selectedItems = new ArrayList<>();
        for (int i = iMin; i <= iMax; i++) {
            if (sm.isSelectedIndex(i)) selectedItems.add(model.getElementAt(i));
        }
        return selectedItems;
    }
    /** @deprecated As of JDK 1.7, replaced by {@link #getSelectedValuesList()} */
    @Deprecated
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

    /**
     * Defines the way list cells are layed out: {@code VERTICAL} (one column),
     * {@code HORIZONTAL_WRAP} (cells flow across, then wrap to a new row) or
     * {@code VERTICAL_WRAP} (cells flow down, then wrap to a new column). The
     * wrapping orientations size the rows or columns from
     * {@code visibleRowCount}, or from the list's width or height when it is
     * {@code 0} or less.
     *
     * @param layoutOrientation the new layout orientation
     * @throws IllegalArgumentException if {@code layoutOrientation} isn't one of the
     *         allowable values
     * @since 1.4
     */
    public void setLayoutOrientation(int layoutOrientation) {
        int oldValue = this.layoutOrientation;
        switch (layoutOrientation) {
            case VERTICAL:
            case VERTICAL_WRAP:
            case HORIZONTAL_WRAP:
                this.layoutOrientation = layoutOrientation;
                firePropertyChange("layoutOrientation", oldValue, layoutOrientation);
                revalidate(); repaint();
                break;
            default:
                throw new IllegalArgumentException("layoutOrientation must be one of: VERTICAL, HORIZONTAL_WRAP or VERTICAL_WRAP");
        }
    }
    public int getVisibleRowCount() { return visibleRowCount; }

    /**
     * Sets the {@code visibleRowCount} property, which has different meanings
     * depending on the layout orientation; a negative value is stored as
     * {@code 0}. The default value of this property is {@code 8}.
     *
     * @param visibleRowCount an integer specifying the preferred number of
     *                        rows to display without requiring scrolling
     */
    public void setVisibleRowCount(int visibleRowCount) {
        int oldValue = this.visibleRowCount;
        this.visibleRowCount = Math.max(0, visibleRowCount);
        firePropertyChange("visibleRowCount", oldValue, visibleRowCount);
        revalidate(); repaint();
    }
    public int getFixedCellWidth() { return fixedCellWidth; }

    /**
     * Sets a fixed value to be used for the width of every cell in the list.
     * If {@code width} is -1, cell widths are computed from the renderer's
     * preferred width for each list element.
     *
     * @param width the width to be used for all cells in the list
     */
    public void setFixedCellWidth(int width) {
        int oldValue = fixedCellWidth;
        fixedCellWidth = width;
        firePropertyChange("fixedCellWidth", oldValue, fixedCellWidth);
        revalidate(); repaint();
    }
    public int getFixedCellHeight() { return fixedCellHeight; }

    /**
     * Sets a fixed value to be used for the height of every cell in the list.
     * If {@code height} is -1, cell heights are computed from the renderer's
     * preferred height for each list element.
     *
     * @param height the height to be used for for all cells in the list
     */
    public void setFixedCellHeight(int height) {
        int oldValue = fixedCellHeight;
        fixedCellHeight = height;
        firePropertyChange("fixedCellHeight", oldValue, fixedCellHeight);
        revalidate(); repaint();
    }

    private final java.util.List<ListSelectionListener> selectionListeners = new java.util.ArrayList<>();
    public void addListSelectionListener(ListSelectionListener l) { if (l != null) selectionListeners.add(l); }
    public void removeListSelectionListener(ListSelectionListener l) { selectionListeners.remove(l); }
    public ListSelectionListener[] getListSelectionListeners() { return selectionListeners.toArray(new ListSelectionListener[0]); }

    /**
     * Notifies {@code ListSelectionListener}s added directly to the list
     * of selection changes made to the selection model, last added first as
     * the JDK's {@code EventListenerList} does.
     *
     * @param firstIndex the first selected index
     * @param lastIndex the last selected index
     * @param isAdjusting true if multiple changes are being made
     */
    protected void fireSelectionValueChanged(int firstIndex, int lastIndex, boolean isAdjusting) {
        javax.swing.event.ListSelectionEvent e = null;
        ListSelectionListener[] listeners = getListSelectionListeners();
        for (int i = listeners.length - 1; i >= 0; i--) {
            if (e == null) e = new javax.swing.event.ListSelectionEvent(this, firstIndex, lastIndex, isAdjusting);
            listeners[i].valueChanged(e);
        }
    }

    /**
     * Returns the bounding rectangle, in the list's coordinate system,
     * for the range of cells specified by the two indices, as the UI
     * computes it. Returns {@code null} when the smaller index is outside the
     * list's range of cells.
     *
     * @param index0 the first index in the range
     * @param index1 the second index in the range
     * @return the bounding rectangle for the range of cells, or {@code null}
     */
    public java.awt.Rectangle getCellBounds(int index0, int index1) {
        return layoutUI().getCellBounds(this, index0, index1);
    }

    /**
     * Returns the cell index closest to the given location in the list's
     * coordinate system, or {@code -1} if the model is empty.
     *
     * @param location the coordinates of the point
     * @return the cell index closest to the given location, or {@code -1}
     */
    public int locationToIndex(java.awt.Point location) {
        return layoutUI().locationToIndex(this, location);
    }

    /**
     * Returns the origin of the specified item in the list's coordinate
     * system, or {@code null} if the index isn't valid.
     *
     * @param index the cell index
     * @return the origin of the cell, or {@code null}
     */
    public java.awt.Point indexToLocation(int index) {
        return layoutUI().indexToLocation(this, index);
    }

    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Dimension size = layoutUI().getPreferredSize(this);
        return size != null ? size : super.getPreferredSize();
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        if (isOpaque()) { g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight()); }
        ownUI().paint(g, this);
    }

    /**
     * Returns the tooltip text of the renderer under the event, so each cell
     * can have its own tip; falls back to the list's own tooltip text.
     *
     * @param event the {@code MouseEvent} to fetch the tooltip text for
     * @return the cell's or the list's tooltip text
     */
    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        if (event != null) {
            java.awt.Point p = event.getPoint();
            int index = locationToIndex(p);
            ListCellRenderer<? super E> r = getCellRenderer();
            java.awt.Rectangle cellBounds;

            if (index != -1 && r != null && (cellBounds = getCellBounds(index, index)) != null
                    && cellBounds.contains(p.x, p.y)) {
                ListSelectionModel lsm = getSelectionModel();
                java.awt.Component rComponent = r.getListCellRendererComponent(this, model.getElementAt(index), index,
                        lsm.isSelectedIndex(index), hasFocus() && lsm.getLeadSelectionIndex() == index);

                if (rComponent instanceof JComponent) {
                    p.translate(-cellBounds.x, -cellBounds.y);
                    java.awt.event.MouseEvent newEvent = new java.awt.event.MouseEvent(rComponent, event.getID(),
                            event.getWhen(), event.getModifiers(), p.x, p.y, event.getXOnScreen(),
                            event.getYOnScreen(), event.getClickCount(), event.isPopupTrigger(),
                            java.awt.event.MouseEvent.NOBUTTON);
                    String tip = ((JComponent) rComponent).getToolTipText(newEvent);
                    if (tip != null) return tip;
                }
            }
        }
        return super.getToolTipText();
    }

    /**
     * Returns the next list element whose {@code toString} value starts with
     * the given prefix, ignoring case.
     *
     * @param prefix the string to test for a match
     * @param startIndex the index for starting the search
     * @param bias the search direction, either Position.Bias.Forward or
     *             Position.Bias.Backward.
     * @return the index of the next list element that starts with the
     *         prefix; otherwise {@code -1}
     * @throws IllegalArgumentException if prefix is {@code null}
     *         or startIndex is out of bounds
     * @since 1.4
     */
    public int getNextMatch(String prefix, int startIndex, javax.swing.text.Position.Bias bias) {
        int max = model.getSize();
        if (prefix == null) throw new IllegalArgumentException();
        if (startIndex < 0 || startIndex >= max) throw new IllegalArgumentException();
        prefix = prefix.toUpperCase();

        // start search from the next element after the selected element
        int increment = (bias == javax.swing.text.Position.Bias.Forward) ? 1 : -1;
        int index = startIndex;
        do {
            E element = model.getElementAt(index);
            if (element != null) {
                String string = element instanceof String ? (String) element : element.toString();
                if (string != null && string.toUpperCase().startsWith(prefix)) return index;
            }
            index = (index + increment + max) % max;
        } while (index != startIndex);
        return -1;
    }

    /**
     * Returns the smallest list index that is currently visible, or
     * {@code -1} if nothing is visible or the list is empty.
     *
     * @return the index of the first visible cell
     */
    public int getFirstVisibleIndex() {
        java.awt.Rectangle r = getVisibleRect();
        int first;
        if (getComponentOrientation().isLeftToRight()) {
            first = locationToIndex(r.getLocation());
        } else {
            first = locationToIndex(new java.awt.Point((r.x + r.width) - 1, r.y));
        }
        if (first != -1) {
            java.awt.Rectangle bounds = getCellBounds(first, first);
            if (bounds != null) {
                SwingUtilities.computeIntersection(r.x, r.y, r.width, r.height, bounds);
                if (bounds.width == 0 || bounds.height == 0) first = -1;
            }
        }
        return first;
    }

    /**
     * Returns the largest list index that is currently visible, or
     * {@code -1} if nothing is visible or the list is empty.
     *
     * @return the index of the last visible cell
     */
    public int getLastVisibleIndex() {
        boolean leftToRight = getComponentOrientation().isLeftToRight();
        java.awt.Rectangle r = getVisibleRect();
        java.awt.Point lastPoint = leftToRight
                ? new java.awt.Point((r.x + r.width) - 1, (r.y + r.height) - 1)
                : new java.awt.Point(r.x, (r.y + r.height) - 1);
        int location = locationToIndex(lastPoint);

        if (location != -1) {
            java.awt.Rectangle bounds = getCellBounds(location, location);

            if (bounds != null) {
                SwingUtilities.computeIntersection(r.x, r.y, r.width, r.height, bounds);
                if (bounds.width == 0 || bounds.height == 0) {
                    // Try the top left (LTR) or top right (RTL) corner, and then go across
                    // checking each cell for HORIZONTAL_WRAP; the lower left corner, and then
                    // across, for the other orientations.
                    boolean isHorizontalWrap = layoutOrientation == HORIZONTAL_WRAP;
                    java.awt.Point visibleLocation = isHorizontalWrap
                            ? new java.awt.Point(lastPoint.x, r.y)
                            : new java.awt.Point(r.x, lastPoint.y);
                    int last;
                    int visIndex = -1;
                    int lIndex = location;
                    location = -1;

                    do {
                        last = visIndex;
                        visIndex = locationToIndex(visibleLocation);

                        if (visIndex != -1) {
                            bounds = getCellBounds(visIndex, visIndex);
                            if (visIndex != lIndex && bounds != null && bounds.contains(visibleLocation)) {
                                location = visIndex;
                                if (isHorizontalWrap) {
                                    visibleLocation.y = bounds.y + bounds.height;
                                    if (visibleLocation.y >= lastPoint.y) {
                                        // Past visible region, bail.
                                        last = visIndex;
                                    }
                                } else {
                                    visibleLocation.x = bounds.x + bounds.width;
                                    if (visibleLocation.x >= lastPoint.x) {
                                        // Past visible region, bail.
                                        last = visIndex;
                                    }
                                }
                            } else {
                                last = visIndex;
                            }
                        }
                    } while (visIndex != -1 && last != visIndex);
                }
            }
        }
        return location;
    }

    public void clearSelection() { selectionModel.clearSelection(); }

    /**
     * Scrolls the list within an enclosing viewport to make the specified
     * cell completely visible. For this method to work, the {@code JList}
     * must be within a {@code JViewport}; an invalid index does nothing.
     *
     * @param index  the index of the cell to make visible
     */
    public void ensureIndexIsVisible(int index) {
        java.awt.Rectangle cellBounds = getCellBounds(index, index);
        if (cellBounds != null) scrollRectToVisible(cellBounds);
    }

    /**
     * Computes the size of viewport needed to display {@code visibleRowCount}
     * rows, as the JDK: for {@code VERTICAL} the fixed cell size when both are
     * set, else the preferred width and {@code visibleRowCount} times the first
     * cell's height, else 256 by 16 per row; the wrapping orientations answer
     * the preferred size.
     *
     * @return the preferred size of a viewport showing {@code visibleRowCount} rows
     */
    public java.awt.Dimension getPreferredScrollableViewportSize() {
        if (getLayoutOrientation() != VERTICAL) return getPreferredSize();
        java.awt.Insets insets = getInsets();
        int dx = insets.left + insets.right;
        int dy = insets.top + insets.bottom;

        int visibleRowCount = getVisibleRowCount();
        int fixedCellWidth = getFixedCellWidth();
        int fixedCellHeight = getFixedCellHeight();

        if (fixedCellWidth > 0 && fixedCellHeight > 0) {
            int width = fixedCellWidth + dx;
            int height = (visibleRowCount * fixedCellHeight) + dy;
            return new java.awt.Dimension(width, height);
        } else if (getModel().getSize() > 0) {
            int width = getPreferredSize().width;
            int height;
            java.awt.Rectangle r = getCellBounds(0, 0);
            if (r != null) {
                height = (visibleRowCount * r.height) + dy;
            } else {
                // Shouldn't happen.
                height = 1;
            }
            return new java.awt.Dimension(width, height);
        } else {
            fixedCellWidth = (fixedCellWidth > 0) ? fixedCellWidth : 256;
            fixedCellHeight = (fixedCellHeight > 0) ? fixedCellHeight : 16;
            return new java.awt.Dimension(fixedCellWidth, fixedCellHeight * visibleRowCount);
        }
    }

    private void checkScrollableParameters(java.awt.Rectangle visibleRect, int orientation) {
        if (visibleRect == null) throw new IllegalArgumentException("visibleRect must be non-null");
        switch (orientation) {
            case SwingConstants.VERTICAL:
            case SwingConstants.HORIZONTAL:
                break;
            default:
                throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
    }

    /**
     * Returns the distance to scroll to expose the next or previous row (for
     * vertical scrolling) or column (for horizontal scrolling): the part of
     * the leading cell that is hidden, or the whole of the next one.
     *
     * @param visibleRect the view area visible within the viewport
     * @param orientation {@code SwingConstants.HORIZONTAL} or {@code SwingConstants.VERTICAL}
     * @param direction less or equal to zero to scroll up/back, greater than zero for down/forward
     * @return the "unit" increment for scrolling in the specified direction; always positive
     * @throws IllegalArgumentException if {@code visibleRect} is {@code null}, or
     *         {@code orientation} isn't one of {@code SwingConstants.VERTICAL} or
     *         {@code SwingConstants.HORIZONTAL}
     */
    public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        checkScrollableParameters(visibleRect, orientation);

        if (orientation == SwingConstants.VERTICAL) {
            int row = locationToIndex(visibleRect.getLocation());

            if (row == -1) {
                return 0;
            } else {
                // Scroll down
                if (direction > 0) {
                    java.awt.Rectangle r = getCellBounds(row, row);
                    return (r == null) ? 0 : r.height - (visibleRect.y - r.y);
                }
                // Scroll up
                else {
                    java.awt.Rectangle r = getCellBounds(row, row);

                    // The first row is completely visible and it's row 0.
                    if ((r.y == visibleRect.y) && (row == 0)) {
                        return 0;
                    }
                    // The first row is completely visible: the height of the previous row.
                    else if (r.y == visibleRect.y) {
                        java.awt.Point loc = r.getLocation();
                        loc.y--;
                        int prevIndex = locationToIndex(loc);
                        java.awt.Rectangle prevR = getCellBounds(prevIndex, prevIndex);

                        if (prevR == null || prevR.y >= r.y) {
                            return 0;
                        }
                        return prevR.height;
                    }
                    // The first row is partially visible: the height of the hidden part.
                    else {
                        return visibleRect.y - r.y;
                    }
                }
            }
        } else if (orientation == SwingConstants.HORIZONTAL && getLayoutOrientation() != VERTICAL) {
            boolean leftToRight = getComponentOrientation().isLeftToRight();
            java.awt.Point leadingPoint = leftToRight
                    ? visibleRect.getLocation()
                    : new java.awt.Point(visibleRect.x + visibleRect.width - 1, visibleRect.y);
            int index = locationToIndex(leadingPoint);

            if (index != -1) {
                java.awt.Rectangle cellBounds = getCellBounds(index, index);
                if (cellBounds != null && cellBounds.contains(leadingPoint)) {
                    int leadingVisibleEdge;
                    int leadingCellEdge;

                    if (leftToRight) {
                        leadingVisibleEdge = visibleRect.x;
                        leadingCellEdge = cellBounds.x;
                    } else {
                        leadingVisibleEdge = visibleRect.x + visibleRect.width;
                        leadingCellEdge = cellBounds.x + cellBounds.width;
                    }

                    if (leadingCellEdge != leadingVisibleEdge) {
                        if (direction < 0) {
                            // Show remainder of leading cell
                            return Math.abs(leadingVisibleEdge - leadingCellEdge);
                        } else if (leftToRight) {
                            // Hide rest of leading cell
                            return leadingCellEdge + cellBounds.width - leadingVisibleEdge;
                        } else {
                            // Hide rest of leading cell
                            return leadingVisibleEdge - cellBounds.x;
                        }
                    }
                    // ASSUME: All cells are the same width
                    return cellBounds.width;
                }
            }
        }
        java.awt.Font f = getFont();
        return (f != null) ? f.getSize() : 1;
    }

    /**
     * Returns the distance to scroll to expose the next or previous block, as
     * the JDK: vertically, the distance that brings the last visible row to the
     * top (or the first row a page up to the top); horizontally, by whole
     * columns for the wrapping orientations; otherwise the visible extent.
     *
     * @param visibleRect the view area visible within the viewport
     * @param orientation {@code SwingConstants.HORIZONTAL} or {@code SwingConstants.VERTICAL}
     * @param direction less or equal to zero to scroll up/back, greater than zero for down/forward
     * @return the "block" increment for scrolling in the specified direction; always positive
     * @throws IllegalArgumentException if {@code visibleRect} is {@code null}, or
     *         {@code orientation} isn't one of {@code SwingConstants.VERTICAL} or
     *         {@code SwingConstants.HORIZONTAL}
     */
    public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        checkScrollableParameters(visibleRect, orientation);
        if (orientation == SwingConstants.VERTICAL) {
            int inc = visibleRect.height;
            // Scroll down
            if (direction > 0) {
                // last cell is the lowest left cell
                int last = locationToIndex(new java.awt.Point(visibleRect.x, visibleRect.y + visibleRect.height - 1));
                if (last != -1) {
                    java.awt.Rectangle lastRect = getCellBounds(last, last);
                    if (lastRect != null) {
                        inc = lastRect.y - visibleRect.y;
                        if ((inc == 0) && (last < getModel().getSize() - 1)) {
                            inc = lastRect.height;
                        }
                    }
                }
            }
            // Scroll up
            else {
                int newFirst = locationToIndex(new java.awt.Point(visibleRect.x, visibleRect.y - visibleRect.height));
                int first = getFirstVisibleIndex();
                if (newFirst != -1) {
                    if (first == -1) {
                        first = locationToIndex(visibleRect.getLocation());
                    }
                    java.awt.Rectangle newFirstRect = getCellBounds(newFirst, newFirst);
                    java.awt.Rectangle firstRect = getCellBounds(first, first);
                    if ((newFirstRect != null) && (firstRect != null)) {
                        while ((newFirstRect.y + visibleRect.height < firstRect.y + firstRect.height)
                                && (newFirstRect.y < firstRect.y)) {
                            newFirst++;
                            newFirstRect = getCellBounds(newFirst, newFirst);
                        }
                        inc = visibleRect.y - newFirstRect.y;
                        if ((inc <= 0) && (newFirstRect.y > 0)) {
                            newFirst--;
                            newFirstRect = getCellBounds(newFirst, newFirst);
                            if (newFirstRect != null) {
                                inc = visibleRect.y - newFirstRect.y;
                            }
                        }
                    }
                }
            }
            return inc;
        } else if (orientation == SwingConstants.HORIZONTAL && getLayoutOrientation() != VERTICAL) {
            boolean leftToRight = getComponentOrientation().isLeftToRight();
            int inc = visibleRect.width;
            // Scroll right (in ltr mode) or left (in rtl mode)
            if (direction > 0) {
                // position is upper right if ltr, or upper left otherwise
                int x = visibleRect.x + (leftToRight ? (visibleRect.width - 1) : 0);
                int last = locationToIndex(new java.awt.Point(x, visibleRect.y));

                if (last != -1) {
                    java.awt.Rectangle lastRect = getCellBounds(last, last);
                    if (lastRect != null) {
                        if (leftToRight) {
                            inc = lastRect.x - visibleRect.x;
                        } else {
                            inc = visibleRect.x + visibleRect.width - (lastRect.x + lastRect.width);
                        }
                        if (inc < 0) {
                            inc += lastRect.width;
                        } else if ((inc == 0) && (last < getModel().getSize() - 1)) {
                            inc = lastRect.width;
                        }
                    }
                }
            }
            // Scroll left (in ltr mode) or right (in rtl mode)
            else {
                // upper left corner shifted left by the visible width if ltr, or the upper
                // right shifted right by it otherwise
                int x = visibleRect.x + (leftToRight
                        ? -visibleRect.width
                        : visibleRect.width - 1 + visibleRect.width);
                int first = locationToIndex(new java.awt.Point(x, visibleRect.y));

                if (first != -1) {
                    java.awt.Rectangle firstRect = getCellBounds(first, first);
                    if (firstRect != null) {
                        // the right of the first cell
                        int firstRight = firstRect.x + firstRect.width;

                        if (leftToRight) {
                            if ((firstRect.x < visibleRect.x - visibleRect.width)
                                    && (firstRight < visibleRect.x)) {
                                inc = visibleRect.x - firstRight;
                            } else {
                                inc = visibleRect.x - firstRect.x;
                            }
                        } else {
                            int visibleRight = visibleRect.x + visibleRect.width;

                            if ((firstRight > visibleRight + visibleRect.width)
                                    && (firstRect.x > visibleRight)) {
                                inc = firstRect.x - visibleRight;
                            } else {
                                inc = firstRight - visibleRight;
                            }
                        }
                    }
                }
            }
            return inc;
        }
        return visibleRect.width;
    }

    /**
     * Returns {@code true} if this {@code JList} is displayed in a
     * {@code JViewport} and the viewport is wider than the list's preferred
     * width, or if the layout orientation is {@code HORIZONTAL_WRAP} and
     * {@code visibleRowCount <= 0}; otherwise returns {@code false}.
     *
     * @return whether an enclosing viewport should force the list's width to match its own
     */
    public boolean getScrollableTracksViewportWidth() {
        if (getLayoutOrientation() == HORIZONTAL_WRAP && getVisibleRowCount() <= 0) return true;
        java.awt.Container parent = SwingUtilities.getUnwrappedParent(this);
        if (parent instanceof JViewport) return parent.getWidth() > getPreferredSize().width;
        return false;
    }

    /**
     * Returns {@code true} if this {@code JList} is displayed in a
     * {@code JViewport} and the viewport is taller than the list's preferred
     * height, or if the layout orientation is {@code VERTICAL_WRAP} and
     * {@code visibleRowCount <= 0}; otherwise returns {@code false}.
     *
     * @return whether an enclosing viewport should force the list's height to match its own
     */
    public boolean getScrollableTracksViewportHeight() {
        if (getLayoutOrientation() == VERTICAL_WRAP && getVisibleRowCount() <= 0) return true;
        java.awt.Container parent = SwingUtilities.getUnwrappedParent(this);
        if (parent instanceof JViewport) return parent.getHeight() > getPreferredSize().height;
        return false;
    }
}
