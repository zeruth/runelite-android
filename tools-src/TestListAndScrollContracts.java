// host-contract
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxEditor;

/**
 * JList cell geometry in every layout orientation, the selection API, combo editor commit,
 * key selection and item/action event order, scroll pane corners and headers, and the
 * JViewport scrollRectToVisible arithmetic. Every expectation is the JDK's own closed form
 * (BasicListUI with fixed cells, JViewport.positionAdjustment), so both runs must agree.
 */
public class TestListAndScrollContracts {
    static final int CELL_W = 30, CELL_H = 10, ITEMS = 7;

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    /** A list sized before its orientation is set, so the UI lays out against that size. */
    static JList<String> list(int orientation, int visibleRows, int width, int height) {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 0; i < ITEMS; i++) model.addElement("item " + i);
        JList<String> list = new JList<>(model);
        list.setBorder(null);
        list.setSize(width, height);
        list.setFixedCellWidth(CELL_W);
        list.setFixedCellHeight(CELL_H);
        list.setVisibleRowCount(visibleRows);
        list.setLayoutOrientation(orientation);
        return list;
    }

    static void vertical() {
        JList<String> list = list(JList.VERTICAL, 8, 100, 70);
        for (int i = 0; i < ITEMS; i++) {
            eq(new Rectangle(0, i * CELL_H, 100, CELL_H), list.getCellBounds(i, i), "vertical cell " + i);
            eq(new Point(0, i * CELL_H), list.indexToLocation(i), "vertical location " + i);
        }
        eq(new Rectangle(0, 10, 100, 30), list.getCellBounds(1, 3), "vertical range");
        eq(2, list.locationToIndex(new Point(5, 25)), "vertical hit");
        eq(ITEMS - 1, list.locationToIndex(new Point(5, 500)), "vertical closest below");
        check(list.getCellBounds(ITEMS, ITEMS) == null, "vertical out of range");
        eq(new Dimension(CELL_W, 8 * CELL_H), list.getPreferredScrollableViewportSize(), "viewport size");
        eq(new Dimension(CELL_W, ITEMS * CELL_H), list.getPreferredSize(), "vertical preferred");
    }

    static void verticalWrap(int visibleRows, int height) {
        JList<String> list = list(JList.VERTICAL_WRAP, visibleRows, 100, height);
        for (int i = 0; i < ITEMS; i++) {
            Rectangle expected = new Rectangle(i / 3 * CELL_W, i % 3 * CELL_H, CELL_W, CELL_H);
            eq(expected, list.getCellBounds(i, i), "vertical wrap cell " + i + " rows=" + visibleRows);
            eq(expected.getLocation(), list.indexToLocation(i), "vertical wrap location " + i);
        }
        eq(4, list.locationToIndex(new Point(35, 15)), "vertical wrap hit");
        eq(6, list.locationToIndex(new Point(65, 5)), "vertical wrap last column");
    }

    static void horizontalWrap(int visibleRows, int width) {
        JList<String> list = list(JList.HORIZONTAL_WRAP, visibleRows, width, 35);
        for (int i = 0; i < ITEMS; i++) {
            Rectangle expected = new Rectangle(i % 3 * CELL_W, i / 3 * CELL_H, CELL_W, CELL_H);
            eq(expected, list.getCellBounds(i, i), "horizontal wrap cell " + i + " rows=" + visibleRows);
            eq(expected.getLocation(), list.indexToLocation(i), "horizontal wrap location " + i);
        }
        eq(4, list.locationToIndex(new Point(35, 15)), "horizontal wrap hit");
        eq(2, list.locationToIndex(new Point(65, 5)), "horizontal wrap last column");
    }

    static void selection() {
        JList<String> list = list(JList.VERTICAL, 8, 100, 70);
        List<Boolean> adjusting = new ArrayList<>();
        list.addListSelectionListener(e -> adjusting.add(e.getValueIsAdjusting()));
        list.setSelectedIndices(new int[]{1, 3, 5});
        eq(Arrays.asList("item 1", "item 3", "item 5"), list.getSelectedValuesList(), "selected values");
        check(Arrays.equals(new int[]{1, 3, 5}, list.getSelectedIndices()), "selected indices");
        eq(1, list.getMinSelectionIndex(), "min selection");
        eq(5, list.getMaxSelectionIndex(), "max selection");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionInterval(2, 4);
        check(Arrays.equals(new int[]{4}, list.getSelectedIndices()), "single selection keeps the lead");
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectionInterval(2, 4);
        eq(2, list.getAnchorSelectionIndex(), "anchor");
        eq(4, list.getLeadSelectionIndex(), "lead");
        adjusting.clear();
        list.setValueIsAdjusting(true);
        list.addSelectionInterval(6, 6);
        check(list.getValueIsAdjusting(), "adjusting");
        list.setValueIsAdjusting(false);
        eq(Arrays.asList(true, false), adjusting, "adjusting events");
        list.clearSelection();
        check(list.isSelectionEmpty() && list.getSelectedIndex() == -1, "cleared");

        // The JDK's DefaultListSelectionModel ignores -1, so neither call clears the selection.
        list.setSelectedIndex(2);
        list.setSelectedIndex(-1);
        eq(2, list.getSelectedIndex(), "setSelectedIndex(-1) keeps the selection");
        list.setSelectedIndex(ITEMS);
        eq(2, list.getSelectedIndex(), "an index past the model is ignored");
        list.setSelectedValue(null, false);
        eq(2, list.getSelectedIndex(), "setSelectedValue(null) keeps the selection");
        list.setSelectedValue("absent", false);
        eq(2, list.getSelectedIndex(), "setSelectedValue(absent) keeps the selection");
        list.setSelectedValue("item 4", false);
        eq(4, list.getSelectedIndex(), "setSelectedValue selects the match");
    }

    /** A renderer whose cells all have one preferred size, so no font metrics enter the geometry. */
    static ListCellRenderer<Object> sized(int width, int height) {
        JLabel cell = new JLabel();
        cell.setPreferredSize(new Dimension(width, height));
        return (list, value, index, selected, focused) -> cell;
    }

    /** Geometry follows a model, renderer, data and orientation change made after the first measure. */
    static void remeasure() {
        JList<String> list = new JList<>(new String[]{"a", "b"});
        list.setBorder(null);
        list.setSize(100, 100);
        list.setCellRenderer(sized(20, 12));
        eq(new Rectangle(0, 12, 100, 12), list.getCellBounds(1, 1), "first measure");
        eq(new Dimension(20, 24), list.getPreferredSize(), "first preferred");

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String s : new String[]{"c", "d", "e"}) model.addElement(s);
        list.setModel(model);
        eq(new Rectangle(0, 24, 100, 12), list.getCellBounds(2, 2), "after the model change");
        eq(new Dimension(20, 36), list.getPreferredSize(), "preferred after the model change");

        list.setCellRenderer(sized(25, 15));
        eq(new Rectangle(0, 30, 100, 15), list.getCellBounds(2, 2), "after the renderer change");
        eq(new Dimension(25, 45), list.getPreferredSize(), "preferred after the renderer change");

        model.addElement("f");
        eq(new Rectangle(0, 45, 100, 15), list.getCellBounds(3, 3), "after an element is added");
        model.remove(0);
        check(list.getCellBounds(3, 3) == null, "after an element is removed");

        list.setVisibleRowCount(2);
        list.setLayoutOrientation(JList.VERTICAL_WRAP);
        eq(new Rectangle(25, 0, 25, 15), list.getCellBounds(2, 2), "after the orientation change");
    }

    static void viewport() {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 0; i < 20; i++) model.addElement("row " + i);
        JList<String> list = new JList<>(model);
        list.setBorder(null);
        list.setFixedCellWidth(CELL_W);
        list.setFixedCellHeight(CELL_H);
        JViewport viewport = new JViewport();
        viewport.setView(list);
        list.setSize(CELL_W, 20 * CELL_H);
        viewport.setSize(CELL_W, 30);
        int[] events = {0};
        viewport.addChangeListener(e -> events[0]++);
        list.ensureIndexIsVisible(10);
        eq(new Point(0, 80), viewport.getViewPosition(), "scrolled down to the bottom edge of row 10");
        check(events[0] > 0, "position change fires");
        list.ensureIndexIsVisible(9);
        eq(new Point(0, 80), viewport.getViewPosition(), "visible row does not scroll");
        list.ensureIndexIsVisible(2);
        eq(new Point(0, 20), viewport.getViewPosition(), "scrolled up to the top edge of row 2");
        eq(new Point(5, 7), viewport.toViewCoordinates(new Point(5, 7)), "toViewCoordinates");
        viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        eq(JViewport.SIMPLE_SCROLL_MODE, viewport.getScrollMode(), "scroll mode");
    }

    static void combo() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"apple", "banana", "blueberry", "cherry"});
        List<String> events = new ArrayList<>();
        combo.addItemListener(e -> events.add((e.getStateChange() == ItemEvent.SELECTED ? "S:" : "D:") + e.getItem()));
        combo.addActionListener(e -> events.add("A:" + e.getActionCommand()));
        combo.setSelectedItem("banana");
        eq(Arrays.asList("D:apple", "S:banana", "A:comboBoxChanged"), events, "item events precede the action");

        combo.setSelectedIndex(0);
        check(combo.selectWithKeyChar('b'), "key b");
        eq("banana", combo.getSelectedItem(), "first b");
        check(combo.selectWithKeyChar('B'), "key B");
        eq("blueberry", combo.getSelectedItem(), "next b, ignoring case");
        check(combo.selectWithKeyChar('b'), "key b wraps");
        eq("banana", combo.getSelectedItem(), "wrapped b");
        check(!combo.selectWithKeyChar('z'), "no z");
        check(combo.getKeySelectionManager() != null, "default manager installed");

        List<String> properties = new ArrayList<>();
        combo.addPropertyChangeListener(e -> properties.add(e.getPropertyName()));
        combo.setMaximumRowCount(3);
        combo.setPrototypeDisplayValue("prototype");
        check(properties.contains("maximumRowCount"), "maximumRowCount fires");
        check(properties.contains("prototypeDisplayValue"), "prototypeDisplayValue fires");
        eq(3, combo.getMaximumRowCount(), "maximum rows");

        combo.setEditable(true);
        BasicComboBoxEditor editor = new BasicComboBoxEditor();
        combo.setEditor(editor);
        JTextField field = (JTextField) editor.getEditorComponent();
        events.clear();
        field.setText("durian");
        field.postActionEvent();
        eq("durian", combo.getSelectedItem(), "editor commit");
        check(events.contains("S:durian"), "commit selects: " + events);
        eq("A:comboBoxEdited", events.get(events.size() - 1), "edited action last: " + events);
        eq("comboBoxChanged", combo.getActionCommand(), "command restored");
    }

    static class CustomBar extends JScrollBar {
        CustomBar(int orientation) { super(orientation); }
    }

    static void scrollPane() {
        JScrollPane pane = new JScrollPane(new JPanel()) {
            @Override public JScrollBar createVerticalScrollBar() { return new CustomBar(JScrollBar.VERTICAL); }
        };
        check(pane.getVerticalScrollBar() instanceof CustomBar, "createVerticalScrollBar override");
        check(!(pane.getHorizontalScrollBar() instanceof CustomBar), "horizontal bar is the default");
        JLabel corner = new JLabel("c");
        pane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, corner);
        check(pane.getCorner(ScrollPaneConstants.UPPER_LEFT_CORNER) == corner, "corner");
        check(pane.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER) == corner, "leading corner is left");
        try {
            pane.setCorner("middle", new JLabel());
            throw new AssertionError("invalid corner accepted");
        } catch (IllegalArgumentException expected) {
            // as the JDK
        }
        JLabel header = new JLabel("header");
        pane.setColumnHeaderView(header);
        check(pane.getColumnHeader() != null && pane.getColumnHeader().getView() == header, "column header");
        JLabel rowHeader = new JLabel("row");
        pane.setRowHeaderView(rowHeader);
        check(pane.getRowHeader() != null && pane.getRowHeader().getView() == rowHeader, "row header");
        List<String> properties = new ArrayList<>();
        pane.addPropertyChangeListener(e -> properties.add(e.getPropertyName()));
        EmptyBorder border = new EmptyBorder(1, 2, 3, 4);
        pane.setViewportBorder(border);
        check(pane.getViewportBorder() == border, "viewport border");
        check(properties.contains("viewportBorder"), "viewportBorder fires");
        pane.setWheelScrollingEnabled(false);
        check(!pane.isWheelScrollingEnabled(), "wheel scrolling");
    }

    public static void main(String[] args) {
        vertical();
        verticalWrap(3, 70);
        verticalWrap(0, 35);
        horizontalWrap(3, 100);
        horizontalWrap(0, 100);
        selection();
        remeasure();
        viewport();
        combo();
        scrollPane();
        System.out.println("PASS: list and scroll contracts");
    }
}
