// host-contract
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.IconUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.TreePath;

/**
 * Model behaviour of the Swing support types against OpenJDK: which events fire (and how often),
 * how bounds are clamped, and which exceptions a misuse throws. Runs on the desktop JDK and on the
 * shadow; nothing here creates a component, so the checks hold headless.
 */
public final class TestSwingModelContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static boolean throwsOf(Class<? extends Throwable> type, Runnable body) {
        try {
            body.run();
            return false;
        } catch (Throwable t) {
            return type.isInstance(t);
        }
    }

    public static void main(String[] args) {
        boundedRange();
        listSelection();
        listModel();
        comboBoxModel();
        singleSelectionAndButton();
        rowSorter();
        sizeAndSpinner();
        eventTypes();
        resourcesAndConstants();
        System.out.println("SUMMARY " + total + " checks, " + failures + " failures");
        if (failures != 0) throw new AssertionError(failures + " Swing model contract checks failed");
    }

    private static void boundedRange() {
        check("bounded-range constructor rejects value below minimum",
              throwsOf(IllegalArgumentException.class, () -> new DefaultBoundedRangeModel(0, 10, 5, 100)));
        DefaultBoundedRangeModel range = new DefaultBoundedRangeModel(0, 10, 0, 100);
        AtomicInteger changes = new AtomicInteger();
        range.addChangeListener(e -> changes.incrementAndGet());
        range.setValue(150);
        check("bounded-range setValue clamps to max - extent", range.getValue() == 90 && changes.get() == 1);
        range.setValue(90);
        check("bounded-range unchanged setValue fires nothing", changes.get() == 1);
        range.setExtent(-5);
        check("bounded-range negative extent clamps to zero", range.getExtent() == 0 && changes.get() == 2);
        range.setMaximum(50);
        check("bounded-range lowering maximum pulls value down", range.getValue() == 50 && range.getMaximum() == 50);
        range.setValueIsAdjusting(true);
        check("bounded-range adjusting flag is a change", changes.get() == 4 && range.getValueIsAdjusting());
        check("bounded-range lists its change listener", range.getChangeListeners().length == 1);
    }

    private static void listSelection() {
        DefaultListSelectionModel selection = new DefaultListSelectionModel();
        check("list-selection rejects an unknown mode",
              throwsOf(IllegalArgumentException.class, () -> selection.setSelectionMode(7)));
        List<ListSelectionEvent> events = new ArrayList<>();
        selection.addListSelectionListener(events::add);
        selection.setSelectionInterval(2, 3);
        check("list-selection interval sets min and max", selection.getMinSelectionIndex() == 2
              && selection.getMaxSelectionIndex() == 3 && selection.getAnchorSelectionIndex() == 2
              && selection.getLeadSelectionIndex() == 3);
        check("list-selection fires one event over the changed range", events.size() == 1
              && events.get(0).getFirstIndex() == 2 && events.get(0).getLastIndex() == 3);
        selection.insertIndexInterval(0, 2, true);
        check("list-selection insert shifts the selection", selection.isSelectedIndex(4)
              && selection.isSelectedIndex(5) && !selection.isSelectedIndex(2));
        selection.removeIndexInterval(0, 1);
        check("list-selection remove shifts it back", selection.isSelectedIndex(2) && selection.isSelectedIndex(3)
              && !selection.isSelectedIndex(4));
        events.clear();
        selection.setValueIsAdjusting(true);
        selection.setSelectionInterval(7, 7);
        selection.addSelectionInterval(9, 9);
        selection.setValueIsAdjusting(false);
        ListSelectionEvent last = events.isEmpty() ? null : events.get(events.size() - 1);
        check("list-selection adjusting events carry the flag", events.size() >= 2 && events.get(0).getValueIsAdjusting());
        check("list-selection end of adjusting fires a final settled event", last != null && !last.getValueIsAdjusting()
              && last.getFirstIndex() <= 2 && last.getLastIndex() >= 9);
        selection.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selection.setSelectionInterval(1, 4);
        check("list-selection single mode keeps only the second index", selection.getMinSelectionIndex() == 4
              && selection.getMaxSelectionIndex() == 4);
        selection.clearSelection();
        check("list-selection clear empties it", selection.isSelectionEmpty() && selection.getMinSelectionIndex() == -1);
    }

    private static void listModel() {
        DefaultListModel<String> list = new DefaultListModel<>();
        check("list-model get past the end throws ArrayIndexOutOfBounds",
              throwsOf(ArrayIndexOutOfBoundsException.class, () -> list.get(5)));
        List<String> log = new ArrayList<>();
        list.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) { log.add("add " + e.getIndex0() + "," + e.getIndex1()); }
            public void intervalRemoved(ListDataEvent e) { log.add("remove " + e.getIndex0() + "," + e.getIndex1()); }
            public void contentsChanged(ListDataEvent e) { log.add("change " + e.getIndex0() + "," + e.getIndex1()); }
        });
        list.addElement("a");
        list.addElement("b");
        list.add(0, "c");
        list.set(1, "d");
        list.removeRange(0, 1);
        check("list-model fires add/change/remove over the touched indices",
              log.equals(Arrays.asList("add 0,0", "add 1,1", "add 0,0", "change 1,1", "remove 0,1")));
        check("list-model removeRange keeps the rest", list.size() == 1 && "b".equals(list.getElementAt(0)));
        check("list-model removeRange rejects from > to",
              throwsOf(IllegalArgumentException.class, () -> list.removeRange(1, 0)));
    }

    private static void comboBoxModel() {
        DefaultComboBoxModel<String> combo = new DefaultComboBoxModel<>(new String[] {"a", "b", "c"});
        check("combo-model array constructor selects the first item", "a".equals(combo.getSelectedItem()));
        List<String> log = new ArrayList<>();
        combo.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) { log.add("add " + e.getIndex0()); }
            public void intervalRemoved(ListDataEvent e) { log.add("remove " + e.getIndex0()); }
            public void contentsChanged(ListDataEvent e) { log.add("change " + e.getIndex0() + "," + e.getIndex1()); }
        });
        combo.removeElementAt(0);
        check("combo-model removing the selection moves it to the next item", "b".equals(combo.getSelectedItem()));
        check("combo-model selection change fires contentsChanged(-1,-1) before the removal",
              log.equals(Arrays.asList("change -1,-1", "remove 0")));
        combo.setSelectedItem("b");
        check("combo-model reselecting the same item fires nothing", log.size() == 2);
        check("combo-model out-of-range getElementAt is null", combo.getElementAt(9) == null);
        DefaultComboBoxModel<String> empty = new DefaultComboBoxModel<>();
        empty.addElement("x");
        check("combo-model first add into an empty model selects it", "x".equals(empty.getSelectedItem()));
        empty.removeAllElements();
        check("combo-model removeAllElements clears the selection", empty.getSelectedItem() == null && empty.getSize() == 0);
    }

    private static void singleSelectionAndButton() {
        DefaultSingleSelectionModel single = new DefaultSingleSelectionModel();
        AtomicInteger changes = new AtomicInteger();
        single.addChangeListener(e -> changes.incrementAndGet());
        single.setSelectedIndex(2);
        single.setSelectedIndex(2);
        check("single-selection fires only on a change", changes.get() == 1 && single.isSelected());
        single.clearSelection();
        check("single-selection clear goes to -1", single.getSelectedIndex() == -1 && !single.isSelected() && changes.get() == 2);

        DefaultButtonModel button = new DefaultButtonModel();
        button.setArmed(true);
        button.setPressed(true);
        button.setEnabled(false);
        check("button-model disabling drops armed and pressed", !button.isArmed() && !button.isPressed());
        AtomicInteger actions = new AtomicInteger();
        button.addActionListener(e -> actions.incrementAndGet());
        button.setArmed(true);
        button.setPressed(true);
        button.setPressed(false);
        check("button-model ignores arm and press while disabled", !button.isArmed() && actions.get() == 0);
    }

    private static void rowSorter() {
        DefaultTableModel table = new DefaultTableModel(new Object[][] {{"b", 2}, {"a", 3}, {"c", 1}},
                                                        new Object[] {"s", "n"});
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(table);
        List<RowSorterEvent.Type> types = new ArrayList<>();
        sorter.addRowSorterListener(e -> types.add(e.getType()));
        sorter.toggleSortOrder(0);
        check("row-sorter toggle sorts ascending", sorter.convertRowIndexToModel(0) == 1
              && sorter.getSortKeys().get(0).getSortOrder() == SortOrder.ASCENDING);
        check("row-sorter toggle fires SORT_ORDER_CHANGED then SORTED",
              types.equals(Arrays.asList(RowSorterEvent.Type.SORT_ORDER_CHANGED, RowSorterEvent.Type.SORTED)));
        sorter.toggleSortOrder(0);
        check("row-sorter second toggle sorts descending", sorter.convertRowIndexToModel(0) == 2
              && sorter.convertRowIndexToView(2) == 0);
        check("row-sorter rejects a column outside the model",
              throwsOf(IndexOutOfBoundsException.class, () -> sorter.toggleSortOrder(-1)));
        sorter.setRowFilter(RowFilter.regexFilter("^a"));
        check("row-sorter regex filter hides non-matching rows", sorter.getViewRowCount() == 1
              && sorter.convertRowIndexToModel(0) == 1 && sorter.getModelRowCount() == 3);
        check("row-sorter filtered-out model row has no view index", sorter.convertRowIndexToView(0) == -1);
        check("row-sorter view index past the end throws",
              throwsOf(IndexOutOfBoundsException.class, () -> sorter.convertRowIndexToModel(5)));
        sorter.setRowFilter(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 1, 1));
        check("row-sorter number filter keeps the larger values", sorter.getViewRowCount() == 2);
        sorter.setRowFilter(null);
        check("row-sorter clearing the filter shows every row", sorter.getViewRowCount() == 3);
        check("sort-order enum order", Arrays.equals(SortOrder.values(),
              new SortOrder[] {SortOrder.ASCENDING, SortOrder.DESCENDING, SortOrder.UNSORTED}));
    }

    private static void sizeAndSpinner() {
        SizeSequence sizes = new SizeSequence(new int[] {10, 20, 30});
        check("size-sequence positions accumulate", sizes.getPosition(2) == 30 && sizes.getSize(1) == 20);
        check("size-sequence index of a position", sizes.getIndex(35) == 2 && sizes.getIndex(0) == 0);
        sizes.insertEntries(1, 2, 5);
        check("size-sequence insert shifts later entries", sizes.getSizes().length == 5 && sizes.getPosition(3) == 20);
        sizes.removeEntries(0, 2);
        check("size-sequence remove drops the leading entries", sizes.getSizes().length == 3 && sizes.getPosition(1) == 5);

        SizeRequirements[] children = {new SizeRequirements(10, 20, 30, 0.5f), new SizeRequirements(5, 5, 5, 0.5f)};
        SizeRequirements tiled = SizeRequirements.getTiledSizeRequirements(children);
        check("size-requirements tiled totals", tiled.minimum == 15 && tiled.preferred == 25 && tiled.maximum == 35);
        int[] offsets = new int[2], spans = new int[2];
        SizeRequirements.calculateTiledPositions(25, tiled, children, offsets, spans);
        check("size-requirements tiled positions at preferred size", offsets[0] == 0 && spans[0] == 20
              && offsets[1] == 20 && spans[1] == 5);

        check("spinner-list rejects an empty list",
              throwsOf(IllegalArgumentException.class, () -> new SpinnerListModel(new ArrayList<Object>())));
        SpinnerListModel spinner = new SpinnerListModel(Arrays.asList("a", "b", "c"));
        AtomicInteger changes = new AtomicInteger();
        spinner.addChangeListener(e -> changes.incrementAndGet());
        check("spinner-list steps through the list", "b".equals(spinner.getNextValue()) && spinner.getPreviousValue() == null);
        spinner.setValue("c");
        spinner.setValue("c");
        check("spinner-list fires once per real change", changes.get() == 1 && spinner.getNextValue() == null);
        check("spinner-list rejects a value outside the list",
              throwsOf(IllegalArgumentException.class, () -> spinner.setValue("z")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void eventTypes() {
        EventListenerList listeners = new EventListenerList();
        ChangeListener change = e -> {};
        listeners.add(ChangeListener.class, null);
        check("listener-list ignores a null listener", listeners.getListenerCount() == 0);
        check("listener-list rejects a listener of the wrong type", throwsOf(IllegalArgumentException.class,
              () -> listeners.add((Class) ListDataListener.class, (EventListener) change)));
        listeners.add(ChangeListener.class, change);
        listeners.add(ChangeListener.class, change);
        check("listener-list counts per type", listeners.getListenerCount(ChangeListener.class) == 2
              && listeners.getListenerCount(ListDataListener.class) == 0 && listeners.getListenerList().length == 4);
        listeners.remove(ChangeListener.class, change);
        check("listener-list remove drops one entry", listeners.getListeners(ChangeListener.class).length == 1);

        ListDataEvent data = new ListDataEvent("src", ListDataEvent.CONTENTS_CHANGED, 5, 2);
        check("list-data event orders its indices", data.getIndex0() == 2 && data.getIndex1() == 5);
        check("list-data event constants", ListDataEvent.CONTENTS_CHANGED == 0 && ListDataEvent.INTERVAL_ADDED == 1
              && ListDataEvent.INTERVAL_REMOVED == 2);
        check("document event types print their names", "INSERT".equals(DocumentEvent.EventType.INSERT.toString())
              && "REMOVE".equals(DocumentEvent.EventType.REMOVE.toString())
              && "CHANGE".equals(DocumentEvent.EventType.CHANGE.toString()));
        TreePath path = new TreePath(new Object[] {"root", "leaf"});
        check("tree-expansion event keeps its path", new TreeExpansionEvent("src", path).getPath() == path);
        check("menu event keeps its source", "src".equals(new MenuEvent("src").getSource()));
        CaretEvent caret = new CaretEvent("src") {
            public int getDot() { return 3; }
            public int getMark() { return 1; }
        };
        check("caret event reports dot and mark", caret.getDot() == 3 && caret.getMark() == 1 && "src".equals(caret.getSource()));
        TableColumnModelEvent moved = new TableColumnModelEvent(new DefaultTableColumnModel(), 1, 3);
        check("table-column-model event keeps from and to", moved.getFromIndex() == 1 && moved.getToIndex() == 3);
        check("internal-frame event ids", InternalFrameEvent.INTERNAL_FRAME_FIRST == 25549
              && InternalFrameEvent.INTERNAL_FRAME_OPENED == 25549 && InternalFrameEvent.INTERNAL_FRAME_CLOSING == 25550
              && InternalFrameEvent.INTERNAL_FRAME_CLOSED == 25551 && InternalFrameEvent.INTERNAL_FRAME_ICONIFIED == 25552
              && InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED == 25553 && InternalFrameEvent.INTERNAL_FRAME_ACTIVATED == 25554
              && InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED == 25555 && InternalFrameEvent.INTERNAL_FRAME_LAST == 25555);
        Object adapter = new MouseInputAdapter() {};
        check("mouse-input adapter is a mouse input and wheel listener", adapter instanceof MouseInputListener
              && adapter instanceof java.awt.event.MouseWheelListener);
        check("internal-frame adapter implements its listener", InternalFrameListener.class.isAssignableFrom(InternalFrameAdapter.class));
        check("menu key and drag listeners are event listeners", EventListener.class.isAssignableFrom(MenuKeyListener.class)
              && EventListener.class.isAssignableFrom(MenuDragMouseListener.class)
              && EventListener.class.isAssignableFrom(MenuListener.class));
    }

    private static void resourcesAndConstants() {
        java.awt.Insets insets = new BorderUIResource.EmptyBorderUIResource(1, 2, 3, 4).getBorderInsets(null);
        check("empty-border resource keeps its insets", insets.top == 1 && insets.left == 2 && insets.bottom == 3 && insets.right == 4);
        check("border resource nested types are UIResources",
              UIResource.class.isAssignableFrom(BorderUIResource.LineBorderUIResource.class)
              && UIResource.class.isAssignableFrom(BorderUIResource.CompoundBorderUIResource.class)
              && UIResource.class.isAssignableFrom(BorderUIResource.TitledBorderUIResource.class));
        check("icon resource rejects a null delegate",
              throwsOf(IllegalArgumentException.class, () -> new IconUIResource(null)));
        check("action-map resource is an ActionMap and a UIResource", new ActionMapUIResource() instanceof UIResource);
        check("list renderer resource is a UIResource", UIResource.class.isAssignableFrom(DefaultListCellRenderer.UIResource.class));
        check("popup and toolbar separators are JSeparators", JSeparator.class.isAssignableFrom(JPopupMenu.Separator.class)
              && JSeparator.class.isAssignableFrom(JToolBar.Separator.class));
        check("drop modes in JDK order", DropMode.values().length == 8 && DropMode.USE_SELECTION.ordinal() == 0
              && DropMode.ON_OR_INSERT_COLS.ordinal() == 7);
        check("menu selection manager is a singleton", MenuSelectionManager.defaultManager() == MenuSelectionManager.defaultManager()
              && MenuSelectionManager.defaultManager().getSelectedPath().length == 0);

        check("swing constants", SwingConstants.CENTER == 0 && SwingConstants.TOP == 1 && SwingConstants.LEFT == 2
              && SwingConstants.BOTTOM == 3 && SwingConstants.RIGHT == 4 && SwingConstants.NORTH == 1
              && SwingConstants.NORTH_EAST == 2 && SwingConstants.EAST == 3 && SwingConstants.SOUTH_EAST == 4
              && SwingConstants.SOUTH == 5 && SwingConstants.SOUTH_WEST == 6 && SwingConstants.WEST == 7
              && SwingConstants.NORTH_WEST == 8 && SwingConstants.HORIZONTAL == 0 && SwingConstants.VERTICAL == 1
              && SwingConstants.LEADING == 10 && SwingConstants.TRAILING == 11 && SwingConstants.NEXT == 12
              && SwingConstants.PREVIOUS == 13);
        check("window constants", WindowConstants.DO_NOTHING_ON_CLOSE == 0 && WindowConstants.HIDE_ON_CLOSE == 1
              && WindowConstants.DISPOSE_ON_CLOSE == 2 && WindowConstants.EXIT_ON_CLOSE == 3);
        check("list-selection constants", ListSelectionModel.SINGLE_SELECTION == 0
              && ListSelectionModel.SINGLE_INTERVAL_SELECTION == 1 && ListSelectionModel.MULTIPLE_INTERVAL_SELECTION == 2);
        check("scroll-pane constants", ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED == 20
              && ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER == 21 && ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS == 22
              && ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED == 30 && ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER == 31
              && ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS == 32 && "VIEWPORT".equals(ScrollPaneConstants.VIEWPORT)
              && "VERTICAL_SCROLLBAR_POLICY".equals(ScrollPaneConstants.VERTICAL_SCROLLBAR_POLICY)
              && "LOWER_LEADING_CORNER".equals(ScrollPaneConstants.LOWER_LEADING_CORNER)
              && "UPPER_TRAILING_CORNER".equals(ScrollPaneConstants.UPPER_TRAILING_CORNER));
    }
}
