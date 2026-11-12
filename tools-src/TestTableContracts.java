// host-contract
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/** JTable editing, sorting, column moves and viewport filling, identical on the JDK and the shadow. */
public class TestTableContracts {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    static DefaultTableModel model() {
        return new DefaultTableModel(new Object[][] {{"c", 3}, {"a", 1}, {"b", 2}}, new Object[] {"name", "n"}) {
            @Override public Class<?> getColumnClass(int column) { return column == 1 ? Integer.class : Object.class; }
        };
    }

    public static void main(String[] args) throws Exception {
        editing();
        sortKeepsSelection();
        columnReorder();
        fillsViewport();
        System.out.println("PASS: editing via the model, selection across sorts, column moves, viewport fill");
    }

    static void editing() {
        DefaultTableModel model = model();
        JTable table = new JTable(model);
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        sorter.toggleSortOrder(0);
        check(table.editCellAt(0, 0), "editCellAt starts");
        check(table.isEditing() && table.getEditingRow() == 0 && table.getEditingColumn() == 0, "editing cell");
        check(table.getEditorComponent() instanceof JTextField, "generic editor is a text field");
        check("a".equals(((JTextField) table.getEditorComponent()).getText()), "editor shows the view row's value");
        ((JTextField) table.getEditorComponent()).setText("z");
        check(table.getCellEditor().stopCellEditing(), "stop commits");
        check(!table.isEditing() && table.getEditorComponent() == null, "editor removed");
        check("z".equals(model.getValueAt(1, 0)), "commit lands on the model row behind the view row");

        int view = table.convertRowIndexToView(0);
        check(table.editCellAt(view, 1), "number cell edits");
        check(((JTextField) table.getEditorComponent()).getHorizontalAlignment() == SwingConstants.RIGHT, "number editor aligns right");
        ((JTextField) table.getEditorComponent()).setText("abc");
        check(!table.getCellEditor().stopCellEditing() && table.isEditing(), "invalid number keeps editing");
        ((JTextField) table.getEditorComponent()).setText("42");
        check(table.getCellEditor().stopCellEditing(), "valid number commits");
        check(Integer.valueOf(42).equals(model.getValueAt(0, 1)), "number parsed to the column class");

        check(table.editCellAt(0, 0), "edit again");
        table.getCellEditor().cancelCellEditing();
        check(!table.isEditing() && "z".equals(model.getValueAt(1, 0)), "cancel leaves the model");

        check(table.getDefaultRenderer(Boolean.class) instanceof JCheckBox, "boolean renderer");
        check(table.getDefaultEditor(Boolean.class).getTableCellEditorComponent(table, Boolean.TRUE, false, 0, 0) instanceof JCheckBox, "boolean editor");
        check(table.getDefaultEditor(Integer.class) != table.getDefaultEditor(Object.class), "number editor by superclass");
        check(table.getDefaultRenderer(Comparable.class) == table.getDefaultRenderer(Object.class), "interfaces fall back to Object");
    }

    static void sortKeepsSelection() {
        DefaultTableModel model = model();
        JTable table = new JTable(model);
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setRowSelectionInterval(0, 0);
        sorter.toggleSortOrder(0);
        check(table.getSelectedRow() == 2 && "c".equals(table.getValueAt(table.getSelectedRow(), 0)), "selection follows its row through a sort");
        sorter.toggleSortOrder(0);
        check(table.getSelectedRow() == 0 && table.getSelectedRowCount() == 1, "and through the reverse sort");
        sorter.setRowFilter(RowFilter.regexFilter("[ab]", 0));
        check(table.getSelectedRowCount() == 0, "a filtered-out row leaves the selection");
        sorter.setRowFilter(null);
        check(table.getSelectedRowCount() == 0, "a row filtered out stays deselected when it returns");
        table.setRowSelectionInterval(1, 2);
        sorter.toggleSortOrder(0);
        check(table.getSelectedRowCount() == 2, "multiple rows survive");
    }

    static void columnReorder() {
        DefaultTableModel model = model();
        JTable table = new JTable(model);
        java.util.List<TableColumnModelEvent> moves = new ArrayList<>();
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            public void columnAdded(TableColumnModelEvent e) {}
            public void columnRemoved(TableColumnModelEvent e) {}
            public void columnMoved(TableColumnModelEvent e) { moves.add(e); }
            public void columnMarginChanged(ChangeEvent e) {}
            public void columnSelectionChanged(ListSelectionEvent e) {}
        });
        table.setColumnSelectionInterval(0, 0);
        table.moveColumn(0, 1);
        check(moves.size() == 1 && moves.get(0).getFromIndex() == 0 && moves.get(0).getToIndex() == 1, "column moved event");
        check(table.convertColumnIndexToModel(0) == 1 && table.convertColumnIndexToView(0) == 1, "index mapping");
        check("n".equals(table.getColumnName(0)) && "c".equals(table.getValueAt(0, 1)), "reads through the moved column");
        check(table.isColumnSelected(1) && !table.isColumnSelected(0), "column selection moves with the column");
        check(table.getColumn("name") == table.getColumnModel().getColumn(1), "getColumn by identifier");
        JTableHeader header = table.getTableHeader();
        check(header.getDraggedColumn() == null && header.getResizingColumn() == null, "no drag at rest");
        check(header.columnAtPoint(new Point(80, 2)) == 1 && header.getHeaderRect(1).x == 75, "header geometry follows the model");
        TableColumn removed = table.getColumnModel().getColumn(0);
        table.removeColumn(removed);
        check(table.getColumnCount() == 1 && "c".equals(table.getValueAt(0, 0)), "removeColumn keeps the model");
        table.addColumn(removed);
        check(table.getColumnCount() == 2 && table.convertColumnIndexToModel(1) == 1, "addColumn appends");
    }

    static void fillsViewport() {
        JTable table = new JTable(model());
        table.setBackground(Color.GREEN);
        table.setFillsViewportHeight(true);
        JViewport viewport = new JViewport();
        viewport.setView(table);
        viewport.setSize(300, 300);
        viewport.doLayout();
        check(table.getScrollableTracksViewportHeight(), "short table tracks the viewport");
        check(table.getHeight() == 300, "table stretched to the viewport: " + table.getHeight());
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        table.paint(g);
        g.dispose();
        check(image.getRGB(5, 290) == Color.GREEN.getRGB(), "background painted below the rows");
        table.setFillsViewportHeight(false);
        check(!table.getScrollableTracksViewportHeight(), "no tracking without the flag");
    }
}
