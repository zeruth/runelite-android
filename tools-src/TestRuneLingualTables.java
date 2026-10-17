import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.*;

/** Run against Android's shadow Swing classes, not the desktop JDK implementation. */
public class TestRuneLingualTables {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Class.forName("net.runelite.awt.AwtCompat").getMethod("ensureReady").invoke(null);
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        model.addColumn("Search"); model.addColumn("Result"); model.addColumn("Type");
        check(table.getColumnModel().getColumnCount() == 3, "Columns added after JTable construction");
        ArrayList<TableModelEvent> events = new ArrayList<>();
        model.addTableModelListener(events::add);
        model.addRow(new Object[]{"Sword", "\u5263"});
        check(model.getValueAt(0,2) == null, "Short rows padded");
        check(events.get(0).getType() == TableModelEvent.INSERT, "Insert event");
        model.setValueAt("item", 0, 2);
        check(events.get(1).getColumn() == 2, "Cell event");
        TableColumn column = table.getColumnModel().getColumn(0);
        column.setPreferredWidth(120);
        model.fireTableDataChanged();
        check(table.getColumnModel().getColumn(0) == column, "Data changes preserve columns and widths");
        check(table.getCellRect(0,1,true).x == 120, "Column sizing reaches cell geometry");
        check(table.getPreferredSize().height == table.getRowHeight(), "Rows reach layout");
        table.getColumnModel().moveColumn(0,1);
        check("\u5263".equals(table.getValueAt(0,0)), "View-to-model column mapping");
        table.getColumnModel().moveColumn(1,0);
        final int[] painted = {0};
        TableCellRenderer renderer = (t,v,s,f,r,c) -> new JComponent() {
            protected void paintComponent(Graphics g) { painted[0]++; g.setColor(Color.RED); g.fillRect(0,0,getWidth(),getHeight()); }
        };
        for (int i=0;i<3;i++) table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        BufferedImage image = new BufferedImage(300,60,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        table.setSize(300,60); table.paint(graphics); graphics.dispose();
        check(painted[0] == 3 && image.getRGB(1,1) == Color.RED.getRGB(), "Result cells render");
        JScrollPane pane = new JScrollPane(table);
        check(pane.getColumnHeader().getView() == table.getTableHeader(), "Table header mounted");
        model.removeRow(0);
        check(model.getRowCount()==0 && events.get(events.size()-1).getType()==TableModelEvent.DELETE, "Clear results");
        model.addRow(new Object[]{"next", "row", "npc"});
        check(model.getRowCount()==1, "Second search can repopulate");
        DefaultTableModel replacement = new DefaultTableModel(new Object[]{"New"}, 0);
        table.setModel(replacement); model.addColumn("old");
        check(table.getColumnCount()==1, "Old model listener detached");
        System.out.println("PASS: RuneLingual table mutations, events, column sizing, rendering, header, repeated searches");
        System.exit(0);
    }
}
