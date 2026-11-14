// host-contract: runs=patched
// Patched only: the JDK refuses drag and drop under java.awt.headless (DragSource and
// DropTarget throw HeadlessException), while the shadow's drag is in-process and needs no display.

import java.awt.EventQueue;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;
import net.runelite.awt.impl.DragDropHost;

/**
 * A scripted gesture on a {@code JList} with {@code setDragEnabled(true)} and a reordering
 * {@code TransferHandler}: a press on the selected row, moves past the drag threshold and a
 * release over another row move the item there; a drag cancelled midway leaves the model,
 * the selection and the drop location as they were; a finger swipe without a long press
 * never starts a drag.
 */
public class TestDragAndDropContracts {
    private static int failures;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static final class Reorder extends TransferHandler {
        final DefaultListModel<String> model;
        int from = -1;
        int exportedAction = -1;

        Reorder(DefaultListModel<String> model) { this.model = model; }

        @Override public int getSourceActions(JComponent c) { return MOVE; }

        @Override protected Transferable createTransferable(JComponent c) {
            JList<?> list = (JList<?>) c;
            from = list.getSelectedIndex();
            return new StringSelection(String.valueOf(list.getSelectedValue()));
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            int index = ((JList.DropLocation) support.getDropLocation()).getIndex();
            try {
                model.add(index, (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
            } catch (Exception e) {
                return false;
            }
            if (index <= from) from++;
            return true;
        }

        @Override protected void exportDone(JComponent c, Transferable data, int action) {
            exportedAction = action;
            if (action == MOVE && from >= 0) model.remove(from);
            from = -1;
        }
    }

    private static String contents(DefaultListModel<String> model) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < model.size(); i++) b.append(i == 0 ? "" : ",").append(model.get(i));
        return b.toString();
    }

    private static void mouse(JList<?> list, int id, int x, int y) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        list.dispatchEvent(new MouseEvent(list, id, System.currentTimeMillis(), mods, x, y, 1, false,
                id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
    }

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    runChecks();
                } catch (RuntimeException e) {
                    e.printStackTrace(System.out);
                    failures++;
                }
            }
        });
        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("all drag and drop contracts hold");
    }

    private static void runChecks() {
        DefaultListModel<String> model = new DefaultListModel<String>();
        for (String s : new String[] {"a", "b", "c", "d"}) model.addElement(s);
        JList<String> list = new JList<String>(model);
        list.setFixedCellHeight(20);
        list.setFixedCellWidth(100);
        list.setBounds(0, 0, 100, 80);
        Reorder handler = new Reorder(model);
        list.setTransferHandler(handler);
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setHostShowing(true);
        check(list.getDragEnabled(), "setDragEnabled(true) sticks");
        check(list.getDropTarget() != null, "setTransferHandler installs a drop target");

        final List<String> host = new ArrayList<String>();
        DragDropHost.Listener listener = new DragDropHost.Listener() {
            public void dragUpdated(DragDropHost.Session s) { if (host.isEmpty() || !"updated".equals(host.get(host.size() - 1))) host.add("updated"); }
            public void dragEnded(DragDropHost.Session s) { host.add("ended"); }
        };
        DragDropHost.addListener(listener);
        try {
            // Mouse: the threshold alone starts the drag; release in row 3's leading half inserts before "d".
            DragDropHost.setPointer(false, false);
            list.setSelectedIndex(0);
            mouse(list, MouseEvent.MOUSE_PRESSED, 10, 10);
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 30);
            check(DragDropHost.current() != null, "a mouse drag past the threshold starts a drag");
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 62);
            JList.DropLocation over = list.getDropLocation();
            check(over != null && over.isInsert() && over.getIndex() == 3,
                    "the drop location follows the pointer (insert at 3): " + (over == null ? null : over.getIndex()));
            check(DragDropHost.current() != null && DragDropHost.current().isDropAccepted(), "the list accepts its own items");
            mouse(list, MouseEvent.MOUSE_RELEASED, 10, 62);
            check("b,c,a,d".equals(contents(model)), "the drop reorders the items: " + contents(model));
            check(handler.exportedAction == TransferHandler.MOVE, "exportDone sees MOVE: " + handler.exportedAction);
            check(DragDropHost.current() == null, "the drag is over after the drop");
            check(list.getDropLocation() == null, "the drop location clears after the drop");
            check(host.contains("updated") && host.get(host.size() - 1).equals("ended"), "the host saw the drag: " + host);

            // Cancel midway: nothing moves, and what the drag borrowed comes back.
            host.clear();
            handler.exportedAction = -1;
            list.setSelectedIndex(0);
            mouse(list, MouseEvent.MOUSE_PRESSED, 10, 10);
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 30);
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 62);
            check(list.getDropLocation() != null, "a second drag shows a drop location");
            check(DragDropHost.cancel(), "DragDropHost.cancel() finds the drag");
            mouse(list, MouseEvent.MOUSE_RELEASED, 10, 62);
            check("b,c,a,d".equals(contents(model)), "cancel leaves the items in place: " + contents(model));
            check(handler.exportedAction == TransferHandler.NONE, "exportDone sees NONE after a cancel: " + handler.exportedAction);
            check(list.getDropLocation() == null, "cancel clears the drop location");
            check(list.getSelectedIndex() == 0, "cancel keeps the selection: " + list.getSelectedIndex());
            check(DragDropHost.current() == null && !host.isEmpty() && host.get(host.size() - 1).equals("ended"),
                    "the host saw the cancel end the drag");

            // A finger that swipes without a long press scrolls; it never drags.
            DragDropHost.setPointer(true, false);
            mouse(list, MouseEvent.MOUSE_PRESSED, 10, 10);
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 40);
            check(DragDropHost.current() == null, "a touch swipe without a long press starts no drag");
            mouse(list, MouseEvent.MOUSE_RELEASED, 10, 40);

            // After a long press the same finger drags.
            DragDropHost.setPointer(true, true);
            mouse(list, MouseEvent.MOUSE_PRESSED, 10, 10);
            mouse(list, MouseEvent.MOUSE_DRAGGED, 10, 30);
            check(DragDropHost.current() != null, "a long press then a move starts a drag");
            DragDropHost.cancel();
            mouse(list, MouseEvent.MOUSE_RELEASED, 10, 30);
            check("b,c,a,d".equals(contents(model)), "the cancelled touch drag moved nothing: " + contents(model));
        } finally {
            DragDropHost.setPointer(false, false);
            DragDropHost.removeListener(listener);
        }
    }
}
