/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ListUI;
import javax.swing.plaf.UIResource;

/**
 * An extensible implementation of {@code ListUI}.
 * <p>
 * {@code BasicListUI} instances cannot be shared between multiple
 * lists.
 * <p>
 * The cell geometry (layout state, {@code getCellBounds},
 * {@code locationToIndex}, {@code getPreferredSize}) is the JDK's. Two things
 * differ from the desktop delegate: the list itself performs press/release
 * selection and keeps its selection model in step with the data model (so a
 * list behaves the same with or without a delegate), which leaves the mouse
 * handler and the data listener of this class to repaint and re-measure only;
 * and no keyboard bindings are installed, the device has no list navigation
 * keys.
 *
 * @author Hans Muller
 * @author Philip Milne
 * @author Shannon Hickey (drag and drop)
 */
public class BasicListUI extends ListUI
{
    protected JList list = null;
    protected CellRendererPane rendererPane;

    // Listeners that this UI attaches to the JList
    protected FocusListener focusListener;
    protected MouseInputListener mouseInputListener;
    protected ListSelectionListener listSelectionListener;
    protected ListDataListener listDataListener;
    protected PropertyChangeListener propertyChangeListener;

    protected int[] cellHeights = null;
    protected int cellHeight = -1;
    protected int cellWidth = -1;
    protected int updateLayoutStateNeeded = modelChanged;
    /**
     * Height of the list. When asked to paint, if the current size of
     * the list differs, this will update the layout state.
     */
    private int listHeight;

    /**
     * Width of the list. When asked to paint, if the current size of
     * the list differs, this will update the layout state.
     */
    private int listWidth;

    /**
     * The layout orientation of the list.
     */
    private int layoutOrientation;

    // Following ivars are used if the list is laying out horizontally

    /**
     * Number of columns to create.
     */
    private int columnCount;
    /**
     * Preferred height to make the list, this is only used if the
     * the list is layed out horizontally.
     */
    private int preferredHeight;
    /**
     * Number of rows per column. This is only used if the row height is
     * fixed.
     */
    private int rowsPerColumn;

    /**
     * Local cache of JList's client property "List.isFileList"
     */
    private boolean isFileList = false;

    /**
     * Local cache of JList's component orientation property
     */
    private boolean isLeftToRight = true;

    /** Whether installUI bound this delegate to {@link #list} and its listeners keep the state current. */
    private boolean installed;

    /* The bits below define JList property changes that affect layout.
     * When one of these properties changes we set a bit in
     * updateLayoutStateNeeded.  The change is dealt with lazily, see
     * maybeUpdateLayoutState.  Changes to the JLists model, e.g. the
     * models length changed, are handled similarly, see DataListener.
     */

    protected final static int modelChanged = 1 << 0;
    protected final static int selectionModelChanged = 1 << 1;
    protected final static int fontChanged = 1 << 2;
    protected final static int fixedCellWidthChanged = 1 << 3;
    protected final static int fixedCellHeightChanged = 1 << 4;
    protected final static int prototypeCellValueChanged = 1 << 5;
    protected final static int cellRendererChanged = 1 << 6;
    private final static int layoutOrientationChanged = 1 << 7;
    private final static int heightChanged = 1 << 8;
    private final static int widthChanged = 1 << 9;
    private final static int componentOrientationChanged = 1 << 10;

    /**
     * Paint one List cell: compute the relevant state, get the "rubber stamp"
     * cell renderer component, and then use the CellRendererPane to paint it.
     * Subclasses may want to override this method rather than paint().
     *
     * @see #paint
     */
    protected void paintCell(
        Graphics g,
        int row,
        Rectangle rowBounds,
        ListCellRenderer cellRenderer,
        ListModel dataModel,
        ListSelectionModel selModel,
        int leadIndex)
    {
        Object value = dataModel.getElementAt(row);
        boolean cellHasFocus = list.hasFocus() && (row == leadIndex);
        boolean isSelected = selModel.isSelectedIndex(row);

        @SuppressWarnings("unchecked")
        Component rendererComponent =
            cellRenderer.getListCellRendererComponent(list, value, row, isSelected, cellHasFocus);

        int cx = rowBounds.x;
        int cy = rowBounds.y;
        int cw = rowBounds.width;
        int ch = rowBounds.height;

        if (isFileList) {
            // Shrink renderer to preferred size. This is mostly used on Windows
            // where selection is only shown around the file name, instead of
            // across the whole list cell.
            int w = Math.min(cw, rendererComponent.getPreferredSize().width + 4);
            if (!isLeftToRight) {
                cx += (cw - w);
            }
            cw = w;
        }

        if (rendererPane != null) {
            rendererPane.paintComponent(g, rendererComponent, list, cx, cy, cw, ch, true);
        } else if (rendererComponent != null) {
            // The delegate a list keeps for itself is not installed, so it has no renderer
            // pane to parent the stamp; the stamp paints in place, as the list always did.
            rendererComponent.setBounds(0, 0, cw, ch);
            rendererComponent.validate();
            Graphics cg = g.create(cx, cy, cw, ch);
            try {
                rendererComponent.paint(cg);
            } finally {
                cg.dispose();
            }
        }
    }


    /**
     * Paint the rows that intersect the Graphics objects clipRect.  This
     * method calls paintCell as necessary.  Subclasses
     * may want to override these methods.
     *
     * @see #paintCell
     */
    public void paint(Graphics g, JComponent c) {
        Shape clip = g.getClip();
        paintImpl(g, c);
        g.setClip(clip);
    }

    private void paintImpl(Graphics g, JComponent c)
    {
        prepare((JList) c);
        switch (layoutOrientation) {
        case JList.VERTICAL_WRAP:
            if (list.getHeight() != listHeight) {
                updateLayoutStateNeeded |= heightChanged;
                redrawList();
            }
            break;
        case JList.HORIZONTAL_WRAP:
            if (list.getWidth() != listWidth) {
                updateLayoutStateNeeded |= widthChanged;
                redrawList();
            }
            break;
        default:
            break;
        }
        maybeUpdateLayoutState();

        ListCellRenderer renderer = list.getCellRenderer();
        ListModel dataModel = list.getModel();
        ListSelectionModel selModel = list.getSelectionModel();
        int size;

        if ((renderer == null) || (size = dataModel.getSize()) == 0) {
            return;
        }

        Rectangle paintBounds = g.getClipBounds();
        if (paintBounds == null) {
            paintBounds = new Rectangle(0, 0, list.getWidth(), list.getHeight());
        }
        int leadIndex = adjustIndex(list.getLeadSelectionIndex(), list);
        int maxY = paintBounds.y + paintBounds.height;

        for (int index = 0; index < size; index++) {
            Rectangle rowBounds = getCellBounds(list, index);
            if (rowBounds == null) {
                return;
            }
            if (layoutOrientation == JList.VERTICAL && rowBounds.y >= maxY) {
                break;
            }
            if (!rowBounds.intersects(paintBounds)) {
                continue;
            }
            g.setClip(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            g.clipRect(paintBounds.x, paintBounds.y, paintBounds.width, paintBounds.height);
            paintCell(g, index, rowBounds, renderer, dataModel, selModel, leadIndex);
        }
        if (rendererPane != null) {
            // Empty out the renderer pane, allowing renderers to be gc'ed.
            rendererPane.removeAll();
        }
    }

    /**
     * The preferred size of the list, as the JDK computes it: for
     * {@code VERTICAL} the width of the widest cell and the bottom of the last
     * cell; for the wrapping orientations the cell width times the column
     * count and the row count times the cell height.
     *
     * @param c the list
     * @return the preferred size
     */
    public Dimension getPreferredSize(JComponent c) {
        prepare((JList) c);

        int lastRow = list.getModel().getSize() - 1;
        if (lastRow < 0) {
            return new Dimension(0, 0);
        }

        Insets insets = list.getInsets();
        int width = cellWidth * columnCount + insets.left + insets.right;
        int height;

        if (layoutOrientation != JList.VERTICAL) {
            height = preferredHeight;
        }
        else {
            Rectangle bounds = getCellBounds(list, lastRow);

            if (bounds != null) {
                height = bounds.y + bounds.height + insets.bottom;
            }
            else {
                height = 0;
            }
        }
        return new Dimension(width, height);
    }


    /**
     * Selected the previous row and force it to be visible.
     *
     * @see JList#ensureIndexIsVisible
     */
    protected void selectPreviousIndex() {
        int s = list.getSelectedIndex();
        if(s > 0) {
            s -= 1;
            list.setSelectedIndex(s);
            list.ensureIndexIsVisible(s);
        }
    }


    /**
     * Selected the previous row and force it to be visible.
     *
     * @see JList#ensureIndexIsVisible
     */
    protected void selectNextIndex()
    {
        int s = list.getSelectedIndex();
        if((s + 1) < list.getModel().getSize()) {
            s += 1;
            list.setSelectedIndex(s);
            list.ensureIndexIsVisible(s);
        }
    }


    /**
     * Registers the keyboard bindings on the <code>JList</code>. A deliberate
     * no-op in this port: the device has no list navigation keys, and the
     * bindings' actions would live in the {@code ActionMap} nothing invokes.
     *
     * @see #installUI
     */
    protected void installKeyboardActions() {
    }


    /**
     * Unregisters keyboard actions installed from
     * <code>installKeyboardActions</code>; nothing was installed.
     *
     * @see #uninstallUI
     * @see #installKeyboardActions
     */
    protected void uninstallKeyboardActions() {
    }


    /**
     * Creates and installs the listeners for the JList, its model, and its
     * selectionModel.  This method is called at installUI() time.
     *
     * @see #installUI
     * @see #uninstallListeners
     */
    protected void installListeners()
    {
        focusListener = createFocusListener();
        mouseInputListener = createMouseInputListener();
        propertyChangeListener = createPropertyChangeListener();
        listSelectionListener = createListSelectionListener();
        listDataListener = createListDataListener();

        list.addFocusListener(focusListener);
        list.addMouseListener(mouseInputListener);
        list.addMouseMotionListener(mouseInputListener);
        list.addPropertyChangeListener(propertyChangeListener);

        ListModel model = list.getModel();
        if (model != null) {
            model.addListDataListener(listDataListener);
        }

        ListSelectionModel selectionModel = list.getSelectionModel();
        if (selectionModel != null) {
            selectionModel.addListSelectionListener(listSelectionListener);
        }
    }


    /**
     * Removes the listeners from the JList, its model, and its
     * selectionModel.  All of the listener fields, are reset to
     * null here.  This method is called at uninstallUI() time,
     * it should be kept in sync with installListeners.
     *
     * @see #uninstallUI
     * @see #installListeners
     */
    protected void uninstallListeners()
    {
        list.removeFocusListener(focusListener);
        list.removeMouseListener(mouseInputListener);
        list.removeMouseMotionListener(mouseInputListener);
        list.removePropertyChangeListener(propertyChangeListener);

        ListModel model = list.getModel();
        if (model != null) {
            model.removeListDataListener(listDataListener);
        }

        ListSelectionModel selectionModel = list.getSelectionModel();
        if (selectionModel != null) {
            selectionModel.removeListSelectionListener(listSelectionListener);
        }

        focusListener = null;
        mouseInputListener  = null;
        listSelectionListener = null;
        listDataListener = null;
        propertyChangeListener = null;
    }


    /**
     * Initializes list properties such as font, foreground, and background,
     * and adds the CellRendererPane. The font, foreground, and background
     * properties are only set if their current value is either null
     * or a UIResource, other properties are set if the current
     * value is null.
     *
     * @see #uninstallDefaults
     * @see #installUI
     * @see CellRendererPane
     */
    protected void installDefaults()
    {
        list.setLayout(null);

        LookAndFeel.installBorder(list, "List.border");

        LookAndFeel.installColorsAndFont(list, "List.background", "List.foreground", "List.font");

        LookAndFeel.installProperty(list, "opaque", Boolean.TRUE);

        // A look and feel without a "List.cellRenderer" default keeps the list's renderer:
        // a null renderer would paint nothing.
        Object defaultRenderer = UIManager.get("List.cellRenderer");
        if ((list.getCellRenderer() == null || list.getCellRenderer() instanceof UIResource)
                && defaultRenderer instanceof ListCellRenderer) {
            list.setCellRenderer((ListCellRenderer) defaultRenderer);
        }

        Color sbg = list.getSelectionBackground();
        Color defaultSbg = UIManager.getColor("List.selectionBackground");
        if ((sbg == null || sbg instanceof UIResource) && defaultSbg != null) {
            list.setSelectionBackground(defaultSbg);
        }

        Color sfg = list.getSelectionForeground();
        Color defaultSfg = UIManager.getColor("List.selectionForeground");
        if ((sfg == null || sfg instanceof UIResource) && defaultSfg != null) {
            list.setSelectionForeground(defaultSfg);
        }

        updateIsFileList();
    }

    private void updateIsFileList() {
        boolean b = Boolean.TRUE.equals(list.getClientProperty("List.isFileList"));
        if (b != isFileList) {
            isFileList = b;
        }
    }


    /**
     * Sets the list properties that have not been explicitly overridden to
     * {@code null}. A property is considered overridden if its current value
     * is not a {@code UIResource}.
     *
     * @see #installDefaults
     * @see #uninstallUI
     * @see CellRendererPane
     */
    protected void uninstallDefaults()
    {
        LookAndFeel.uninstallBorder(list);
        if (list.getFont() instanceof UIResource) {
            list.setFont(null);
        }
        if (list.getForeground() instanceof UIResource) {
            list.setForeground(null);
        }
        if (list.getBackground() instanceof UIResource) {
            list.setBackground(null);
        }
        if (list.getSelectionBackground() instanceof UIResource) {
            list.setSelectionBackground(null);
        }
        if (list.getSelectionForeground() instanceof UIResource) {
            list.setSelectionForeground(null);
        }
    }


    /**
     * Initializes <code>this.list</code> by calling <code>installDefaults()</code>,
     * <code>installListeners()</code>, and <code>installKeyboardActions()</code>
     * in order.
     *
     * @see #installDefaults
     * @see #installListeners
     * @see #installKeyboardActions
     */
    public void installUI(JComponent c)
    {
        list = (JList)c;

        layoutOrientation = list.getLayoutOrientation();

        rendererPane = new CellRendererPane();
        list.add(rendererPane);

        columnCount = 1;

        updateLayoutStateNeeded = modelChanged;
        isLeftToRight = list.getComponentOrientation().isLeftToRight();
        installed = true;

        installDefaults();
        installListeners();
        installKeyboardActions();
    }


    /**
     * Uninitializes <code>this.list</code> by calling <code>uninstallListeners()</code>,
     * <code>uninstallKeyboardActions()</code>, and <code>uninstallDefaults()</code>
     * in order.  Sets this.list to null.
     *
     * @see #uninstallListeners
     * @see #uninstallKeyboardActions
     * @see #uninstallDefaults
     */
    public void uninstallUI(JComponent c)
    {
        uninstallListeners();
        uninstallDefaults();
        uninstallKeyboardActions();

        cellWidth = cellHeight = -1;
        cellHeights = null;

        listWidth = listHeight = -1;

        list.remove(rendererPane);
        rendererPane = null;
        installed = false;
        list = null;
    }


    /**
     * Returns a new instance of BasicListUI.  BasicListUI delegates are
     * allocated one per JList.
     *
     * @return A new ListUI implementation for the Windows look and feel.
     */
    public static ComponentUI createUI(JComponent list) {
        return new BasicListUI();
    }


    /**
     * {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int locationToIndex(JList list, Point location) {
        prepare(list);
        return convertLocationToModel(location.x, location.y);
    }


    /**
     * {@inheritDoc}
     */
    public Point indexToLocation(JList list, int index) {
        prepare(list);
        Rectangle rect = getCellBounds(list, index, index);

        if (rect != null) {
            return new Point(rect.x, rect.y);
        }
        return null;
    }


    /**
     * {@inheritDoc}
     */
    public Rectangle getCellBounds(JList list, int index1, int index2) {
        prepare(list);

        int minIndex = Math.min(index1, index2);
        int maxIndex = Math.max(index1, index2);

        if (minIndex >= list.getModel().getSize()) {
            return null;
        }

        Rectangle minBounds = getCellBounds(list, minIndex);

        if (minBounds == null) {
            return null;
        }
        if (minIndex == maxIndex) {
            return minBounds;
        }
        Rectangle maxBounds = getCellBounds(list, maxIndex);

        if (maxBounds != null) {
            if (layoutOrientation == JList.HORIZONTAL_WRAP) {
                int minRow = convertModelToRow(minIndex);
                int maxRow = convertModelToRow(maxIndex);

                if (minRow != maxRow) {
                    minBounds.x = 0;
                    minBounds.width = list.getWidth();
                }
            }
            else if (minBounds.x != maxBounds.x) {
                // Different columns
                minBounds.y = 0;
                minBounds.height = list.getHeight();
            }
            minBounds.add(maxBounds);
        }
        return minBounds;
    }

    /**
     * Gets the bounds of the specified model index, returning the resulting
     * bounds, or null if <code>index</code> is not valid.
     */
    private Rectangle getCellBounds(JList list, int index) {
        int row = convertModelToRow(index);
        int column = convertModelToColumn(index);

        if (row == -1 || column == -1) {
            return null;
        }

        Insets insets = list.getInsets();
        int x;
        int w = cellWidth;
        int y = insets.top;
        int h;
        switch (layoutOrientation) {
        case JList.VERTICAL_WRAP:
        case JList.HORIZONTAL_WRAP:
            if (isLeftToRight) {
                x = insets.left + column * cellWidth;
            } else {
                x = list.getWidth() - insets.right - (column+1) * cellWidth;
            }
            y += cellHeight * row;
            h = cellHeight;
            break;
        default:
            x = insets.left;
            if (cellHeights == null) {
                y += (cellHeight * row);
            }
            else if (row >= cellHeights.length) {
                y = 0;
            }
            else {
                for(int i = 0; i < row; i++) {
                    y += cellHeights[i];
                }
            }
            w = list.getWidth() - (insets.left + insets.right);
            h = getRowHeight(index);
            break;
        }
        return new Rectangle(x, y, w, h);
    }

    /**
     * Returns the height of the specified row based on the current layout.
     *
     * @return The specified row height or -1 if row isn't valid.
     * @see #convertYToRow
     * @see #convertRowToY
     * @see #updateLayoutState
     */
    protected int getRowHeight(int row)
    {
        return getHeight(0, row);
    }


    /**
     * Convert the JList relative coordinate to the row that contains it,
     * based on the current layout.  If y0 doesn't fall within any row,
     * return -1.
     *
     * @return The row that contains y0, or -1.
     * @see #getRowHeight
     * @see #updateLayoutState
     */
    protected int convertYToRow(int y0)
    {
        return convertLocationToRow(0, y0, false);
    }


    /**
     * Return the JList relative Y coordinate of the origin of the specified
     * row or -1 if row isn't valid.
     *
     * @return The Y coordinate of the origin of row, or -1.
     * @see #getRowHeight
     * @see #updateLayoutState
     */
    protected int convertRowToY(int row)
    {
        if (row >= getRowCount(0) || row < 0) {
            return -1;
        }
        Rectangle bounds = getCellBounds(list, row, row);
        return bounds.y;
    }

    /**
     * Returns the height of the cell at the passed in location.
     */
    private int getHeight(int column, int row) {
        if (column < 0 || column > columnCount || row < 0) {
            return -1;
        }
        if (layoutOrientation != JList.VERTICAL) {
            return cellHeight;
        }
        if (row >= list.getModel().getSize()) {
            return -1;
        }
        return (cellHeights == null) ? cellHeight :
                           ((row < cellHeights.length) ? cellHeights[row] : -1);
    }

    /**
     * Returns the row at location x/y.
     *
     * @param closest If true and the location doesn't exactly match a
     *                particular location, this will return the closest row.
     */
    private int convertLocationToRow(int x, int y0, boolean closest) {
        int size = list.getModel().getSize();

        if (size <= 0) {
            return -1;
        }
        Insets insets = list.getInsets();
        if (cellHeights == null) {
            int row = (cellHeight == 0) ? 0 :
                           ((y0 - insets.top) / cellHeight);
            if (closest) {
                if (row < 0) {
                    row = 0;
                }
                else if (row >= size) {
                    row = size - 1;
                }
            }
            return row;
        }
        else if (size > cellHeights.length) {
            return -1;
        }
        else {
            int y = insets.top;
            int row = 0;

            if (closest && y0 < y) {
                return 0;
            }
            int i;
            for (i = 0; i < size; i++) {
                if ((y0 >= y) && (y0 < y + cellHeights[i])) {
                    return row;
                }
                y += cellHeights[i];
                row += 1;
            }
            return i - 1;
        }
    }

    /**
     * Returns the closest column to the passed in location.
     */
    private int convertLocationToColumn(int x, int y) {
        if (cellWidth > 0) {
            if (layoutOrientation == JList.VERTICAL) {
                return 0;
            }
            Insets insets = list.getInsets();
            int col;
            if (isLeftToRight) {
                col = (x - insets.left) / cellWidth;
            } else {
                col = (list.getWidth() - x - insets.right - 1) / cellWidth;
            }
            if (col < 0) {
                return 0;
            }
            else if (col >= columnCount) {
                return columnCount - 1;
            }
            return col;
        }
        return 0;
    }

    /**
     * Returns the model index for the specified display location.
     * If <code>column</code>x<code>row</code> is beyond the length of the
     * model, this will return the model size - 1.
     */
    private int getModelIndex(int column, int row) {
        switch (layoutOrientation) {
        case JList.VERTICAL_WRAP:
            return Math.min(list.getModel().getSize() - 1, rowsPerColumn *
                            column + Math.min(row, rowsPerColumn-1));
        case JList.HORIZONTAL_WRAP:
            return Math.min(list.getModel().getSize() - 1, row * columnCount +
                            column);
        default:
            return row;
        }
    }

    /**
     * Returns the closest location to the model index of the passed in
     * location.
     */
    private int convertLocationToModel(int x, int y) {
        int row = convertLocationToRow(x, y, true);
        int column = convertLocationToColumn(x, y);

        if (row >= 0 && column >= 0) {
            return getModelIndex(column, row);
        }
        return -1;
    }

    /**
     * Returns the number of rows in the given column.
     */
    private int getRowCount(int column) {
        if (column < 0 || column >= columnCount) {
            return -1;
        }
        if (layoutOrientation == JList.VERTICAL ||
                  (column == 0 && columnCount == 1)) {
            return list.getModel().getSize();
        }
        if (column >= columnCount) {
            return -1;
        }
        if (layoutOrientation == JList.VERTICAL_WRAP) {
            if (column < (columnCount - 1)) {
                return rowsPerColumn;
            }
            return list.getModel().getSize() - (columnCount - 1) *
                        rowsPerColumn;
        }
        // JList.HORIZONTAL_WRAP
        int diff = columnCount - (columnCount * rowsPerColumn -
                                  list.getModel().getSize());

        if (column >= diff) {
            return Math.max(0, rowsPerColumn - 1);
        }
        return rowsPerColumn;
    }

    /**
     * Returns the row that the model index <code>index</code> will be
     * displayed in..
     */
    private int convertModelToRow(int index) {
        int size = list.getModel().getSize();

        if ((index < 0) || (index >= size)) {
            return -1;
        }

        if (layoutOrientation != JList.VERTICAL && columnCount > 1 &&
                                                   rowsPerColumn > 0) {
            if (layoutOrientation == JList.VERTICAL_WRAP) {
                return index % rowsPerColumn;
            }
            return index / columnCount;
        }
        return index;
    }

    /**
     * Returns the column that the model index <code>index</code> will be
     * displayed in.
     */
    private int convertModelToColumn(int index) {
        int size = list.getModel().getSize();

        if ((index < 0) || (index >= size)) {
            return -1;
        }

        if (layoutOrientation != JList.VERTICAL && rowsPerColumn > 0 &&
                                                   columnCount > 1) {
            if (layoutOrientation == JList.VERTICAL_WRAP) {
                return index / rowsPerColumn;
            }
            return index % columnCount;
        }
        return 0;
    }

    /**
     * Binds the delegate to the list a query is about. A delegate that
     * installUI bound keeps its layout state current through its listeners;
     * the one a list keeps for itself while no look and feel installed one
     * listens to nothing, so it measures afresh on every query.
     */
    private void prepare(JList target) {
        if (!installed) {
            list = target;
            layoutOrientation = target.getLayoutOrientation();
            isLeftToRight = target.getComponentOrientation().isLeftToRight();
            updateLayoutStateNeeded |= modelChanged;
        }
        maybeUpdateLayoutState();
    }

    /**
     * If updateLayoutStateNeeded is non zero, call updateLayoutState() and reset
     * updateLayoutStateNeeded.  This method should be called by methods
     * before doing any computation based on the geometry of the list.
     * For example it's the first call in paint() and getPreferredSize().
     *
     * @see #updateLayoutState
     */
    protected void maybeUpdateLayoutState()
    {
        if (updateLayoutStateNeeded != 0) {
            updateLayoutState();
            updateLayoutStateNeeded = 0;
        }
    }


    /**
     * Recompute the value of cellHeight or cellHeights based
     * and cellWidth, based on the current font and the current
     * values of fixedCellWidth, fixedCellHeight, and prototypeCellValue.
     *
     * @see #maybeUpdateLayoutState
     */
    protected void updateLayoutState()
    {
        /* If both JList fixedCellWidth and fixedCellHeight have been
         * set, then initialize cellWidth and cellHeight, and set
         * cellHeights to null.
         */

        int fixedCellHeight = list.getFixedCellHeight();
        int fixedCellWidth = list.getFixedCellWidth();

        cellWidth = (fixedCellWidth != -1) ? fixedCellWidth : -1;

        if (fixedCellHeight != -1) {
            cellHeight = fixedCellHeight;
            cellHeights = null;
        }
        else {
            cellHeight = -1;
            cellHeights = new int[list.getModel().getSize()];
        }

        /* If either of  JList fixedCellWidth and fixedCellHeight haven't
         * been set, then initialize cellWidth and cellHeights by
         * scanning through the entire model.  Note: if the renderer is
         * null, we just set cellWidth and cellHeights[*] to zero,
         * if they're not set already.
         */

        if ((fixedCellWidth == -1) || (fixedCellHeight == -1)) {

            ListModel dataModel = list.getModel();
            int dataModelSize = dataModel.getSize();
            ListCellRenderer renderer = list.getCellRenderer();

            if (renderer != null) {
                for(int index = 0; index < dataModelSize; index++) {
                    Object value = dataModel.getElementAt(index);
                    @SuppressWarnings("unchecked")
                    Component c = renderer.getListCellRendererComponent(list, value, index, false, false);
                    if (rendererPane != null) {
                        rendererPane.add(c);
                    }
                    Dimension cellSize = c.getPreferredSize();
                    if (fixedCellWidth == -1) {
                        cellWidth = Math.max(cellSize.width, cellWidth);
                    }
                    if (fixedCellHeight == -1) {
                        cellHeights[index] = cellSize.height;
                    }
                }
            }
            else {
                if (cellWidth == -1) {
                    cellWidth = 0;
                }
                if (cellHeights == null) {
                    cellHeights = new int[dataModelSize];
                }
                for(int index = 0; index < dataModelSize; index++) {
                    cellHeights[index] = 0;
                }
            }
        }

        columnCount = 1;
        if (layoutOrientation != JList.VERTICAL) {
            updateHorizontalLayoutState(fixedCellWidth, fixedCellHeight);
        }
    }

    /**
     * Invoked when the list is layed out horizontally to determine how
     * many columns to create.
     * <p>
     * This updates the <code>rowsPerColumn, </code><code>columnCount</code>,
     * <code>preferredHeight</code> and potentially <code>cellHeight</code>
     * instance variables.
     */
    private void updateHorizontalLayoutState(int fixedCellWidth,
                                             int fixedCellHeight) {
        int visRows = list.getVisibleRowCount();
        int dataModelSize = list.getModel().getSize();
        Insets insets = list.getInsets();

        listHeight = list.getHeight();
        listWidth = list.getWidth();

        if (dataModelSize == 0) {
            rowsPerColumn = columnCount = 0;
            preferredHeight = insets.top + insets.bottom;
            return;
        }

        int height;

        if (fixedCellHeight != -1) {
            height = fixedCellHeight;
        }
        else {
            // Determine the max of the renderer heights.
            int maxHeight = 0;
            if (cellHeights.length > 0) {
                maxHeight = cellHeights[cellHeights.length - 1];
                for (int counter = cellHeights.length - 2;
                     counter >= 0; counter--) {
                    maxHeight = Math.max(maxHeight, cellHeights[counter]);
                }
            }
            height = cellHeight = maxHeight;
            cellHeights = null;
        }
        // The number of rows is either determined by the visible row
        // count, or by the height of the list.
        rowsPerColumn = dataModelSize;
        if (visRows > 0) {
            rowsPerColumn = visRows;
            columnCount = Math.max(1, dataModelSize / rowsPerColumn);
            if (dataModelSize > 0 && dataModelSize > rowsPerColumn &&
                dataModelSize % rowsPerColumn != 0) {
                columnCount++;
            }
            if (layoutOrientation == JList.HORIZONTAL_WRAP) {
                // Because HORIZONTAL_WRAP flows differently, the
                // rowsPerColumn needs to be adjusted.
                rowsPerColumn = (dataModelSize / columnCount);
                if (dataModelSize % columnCount > 0) {
                    rowsPerColumn++;
                }
            }
        }
        else if (layoutOrientation == JList.VERTICAL_WRAP && height != 0) {
            rowsPerColumn = Math.max(1, (listHeight - insets.top -
                                         insets.bottom) / height);
            columnCount = Math.max(1, dataModelSize / rowsPerColumn);
            if (dataModelSize > 0 && dataModelSize > rowsPerColumn &&
                dataModelSize % rowsPerColumn != 0) {
                columnCount++;
            }
        }
        else if (layoutOrientation == JList.HORIZONTAL_WRAP && cellWidth > 0 &&
                 listWidth > 0) {
            columnCount = Math.max(1, (listWidth - insets.left -
                                       insets.right) / cellWidth);
            rowsPerColumn = dataModelSize / columnCount;
            if (dataModelSize % columnCount > 0) {
                rowsPerColumn++;
            }
        }
        preferredHeight = rowsPerColumn * cellHeight + insets.top +
                          insets.bottom;
    }

    private void redrawList() {
        list.revalidate();
        list.repaint();
    }

    private static int adjustIndex(int index, JList list) {
        return index < list.getModel().getSize() ? index : -1;
    }

    /**
     * Creates a delegate that implements MouseInputListener.
     * The delegate is added to the corresponding java.awt.Component listener
     * lists at installUI() time.
     *
     * @return The listener that the {@code BasicListUI} installs.
     * @see MouseInputHandler
     * @see #installUI
     */
    protected MouseInputListener createMouseInputListener() {
        return new MouseInputHandler();
    }

    protected FocusListener createFocusListener() {
        return new FocusHandler();
    }

    /**
     * Creates an instance of ListSelectionHandler that's added to
     * the JLists by selectionModel as needed.
     *
     * @return The listener that the {@code BasicListUI} installs.
     * @see ListSelectionHandler
     * @see #installUI
     */
    protected ListSelectionListener createListSelectionListener() {
        return new ListSelectionHandler();
    }

    /**
     * Creates an instance of ListDataListener that's added to
     * the JLists by model as needed.
     *
     * @return The listener that the {@code BasicListUI} installs.
     * @see ListDataHandler
     * @see #installUI
     */
    protected ListDataListener createListDataListener() {
        return new ListDataHandler();
    }

    /**
     * Creates an instance of PropertyChangeHandler that's added to
     * the JList by installUI().
     *
     * @return The listener that the {@code BasicListUI} installs.
     * @see PropertyChangeHandler
     * @see #installUI
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return new PropertyChangeHandler();
    }

    /**
     * Mouse input for the list. The list itself selects on press and
     * release in this port (see the class comment), so this handler only
     * exists for subclasses that extend it and call {@code super}; its
     * methods are deliberate no-ops.
     */
    public class MouseInputHandler extends MouseInputAdapter
    {
    }

    /**
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of {@code BasicListUI}.
     */
    public class FocusHandler implements FocusListener
    {
        protected void repaintCellFocus()
        {
            int leadIndex = adjustIndex(list.getLeadSelectionIndex(), list);
            if (leadIndex != -1) {
                Rectangle r = getCellBounds(list, leadIndex, leadIndex);
                if (r != null) {
                    list.repaint(r.x, r.y, r.width, r.height);
                }
            }
        }

        /* The focusGained() focusLost() methods run when the JList
         * focus changes.
         */

        public void focusGained(FocusEvent e) {
            repaintCellFocus();
        }

        public void focusLost(FocusEvent e) {
            repaintCellFocus();
        }
    }

    /**
     * The ListSelectionListener that's added to the JLists selection
     * model at installUI time, and whenever the JList.selectionModel property
     * changes.  When the selection changes we repaint the affected rows.
     *
     * @see #createListSelectionListener
     * @see #getCellBounds
     * @see #installUI
     */
    public class ListSelectionHandler implements ListSelectionListener
    {
        public void valueChanged(ListSelectionEvent e)
        {
            maybeUpdateLayoutState();

            int size = list.getModel().getSize();
            int firstIndex = Math.min(size - 1, Math.max(e.getFirstIndex(), 0));
            int lastIndex = Math.min(size - 1, Math.max(e.getLastIndex(), 0));

            Rectangle bounds = getCellBounds(list, firstIndex, lastIndex);

            if (bounds != null) {
                list.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
    }

    /**
     * The ListDataListener that's added to the JLists model at
     * installUI time, and whenever the JList.model property changes.
     * The list adjusts its selection model itself in this port, so this
     * only re-measures and repaints.
     *
     * @see JList#getModel
     * @see #maybeUpdateLayoutState
     * @see #createListDataListener
     * @see #installUI
     */
    public class ListDataHandler implements ListDataListener
    {
        public void intervalAdded(ListDataEvent e) {
            updateLayoutStateNeeded = modelChanged;
            redrawList();
        }

        public void intervalRemoved(ListDataEvent e) {
            updateLayoutStateNeeded = modelChanged;
            redrawList();
        }

        public void contentsChanged(ListDataEvent e) {
            updateLayoutStateNeeded = modelChanged;
            redrawList();
        }
    }

    /**
     * The PropertyChangeListener that's added to the JList at
     * installUI time.  When the value of a JList property that
     * affects layout changes, we set a bit in updateLayoutStateNeeded.
     * If the JLists model changes we additionally remove our listeners
     * from the old model.  Likewise for the JList selectionModel.
     *
     * @see #maybeUpdateLayoutState
     * @see #createPropertyChangeListener
     * @see #installUI
     */
    public class PropertyChangeHandler implements PropertyChangeListener
    {
        public void propertyChange(PropertyChangeEvent e)
        {
            String propertyName = e.getPropertyName();

            /* If the JList.model property changes, remove our listener,
             * listDataListener from the old model and add it to the new one.
             */
            if ("model".equals(propertyName)) {
                ListModel oldModel = (ListModel) e.getOldValue();
                ListModel newModel = (ListModel) e.getNewValue();
                if (oldModel != null) {
                    oldModel.removeListDataListener(listDataListener);
                }
                if (newModel != null) {
                    newModel.addListDataListener(listDataListener);
                }
                updateLayoutStateNeeded |= modelChanged;
                redrawList();
            }

            /* If the JList.selectionModel property changes, remove our listener,
             * listSelectionListener from the old selectionModel and add it to the new one.
             */
            else if ("selectionModel".equals(propertyName)) {
                ListSelectionModel oldModel = (ListSelectionModel) e.getOldValue();
                ListSelectionModel newModel = (ListSelectionModel) e.getNewValue();
                if (oldModel != null) {
                    oldModel.removeListSelectionListener(listSelectionListener);
                }
                if (newModel != null) {
                    newModel.addListSelectionListener(listSelectionListener);
                }
                updateLayoutStateNeeded |= modelChanged;
                redrawList();
            }
            else if ("cellRenderer".equals(propertyName)) {
                updateLayoutStateNeeded |= cellRendererChanged;
                redrawList();
            }
            else if ("font".equals(propertyName)) {
                updateLayoutStateNeeded |= fontChanged;
                redrawList();
            }
            else if ("prototypeCellValue".equals(propertyName)) {
                updateLayoutStateNeeded |= prototypeCellValueChanged;
                redrawList();
            }
            else if ("fixedCellHeight".equals(propertyName)) {
                updateLayoutStateNeeded |= fixedCellHeightChanged;
                redrawList();
            }
            else if ("fixedCellWidth".equals(propertyName)) {
                updateLayoutStateNeeded |= fixedCellWidthChanged;
                redrawList();
            }
            else if ("selectionForeground".equals(propertyName)) {
                list.repaint();
            }
            else if ("selectionBackground".equals(propertyName)) {
                list.repaint();
            }
            else if ("layoutOrientation".equals(propertyName)) {
                updateLayoutStateNeeded |= layoutOrientationChanged;
                layoutOrientation = list.getLayoutOrientation();
                redrawList();
            }
            else if ("visibleRowCount".equals(propertyName)) {
                if (layoutOrientation != JList.VERTICAL) {
                    updateLayoutStateNeeded |= layoutOrientationChanged;
                    redrawList();
                }
            }
            else if ("componentOrientation".equals(propertyName)) {
                isLeftToRight = list.getComponentOrientation().isLeftToRight();
                updateLayoutStateNeeded |= componentOrientationChanged;
                redrawList();
            }
            else if ("List.isFileList".equals(propertyName)) {
                updateIsFileList();
                redrawList();
            }
            else if ("dropLocation".equals(propertyName)) {
                list.repaint();
            }
        }
    }
}
