// host-contract
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Panel;
import java.util.concurrent.Callable;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleValue;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Every Swing component answers a non-null AccessibleContext with the JDK's role, and a
 * fixture tree reports the JDK's accessible child counts, order, parents and indices.
 */
public final class TestAccessibilityContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static AccessibleContext context(Object c) {
        return c instanceof Accessible ? ((Accessible) c).getAccessibleContext() : null;
    }

    private static void role(String name, Component c, AccessibleRole expected) {
        AccessibleContext ac = context(c);
        check(name + " is Accessible with a context", ac != null);
        if (ac != null) {
            check(name + " role " + expected, expected.equals(ac.getAccessibleRole()));
            check(name + " context is cached", ac == context(c));
        }
    }

    /** Top-level windows refuse to exist on a headless JDK; both runs then skip the check. */
    private static void windowRole(String name, Callable<Component> create, AccessibleRole expected) throws Exception {
        Component c;
        try {
            c = create.call();
        } catch (HeadlessException e) {
            System.out.println("SKIP " + name + " (headless)");
            return;
        }
        role(name, c, expected);
    }

    private static void children(String name, Component c, int expected) {
        AccessibleContext ac = context(c);
        int count = ac == null ? -1 : ac.getAccessibleChildrenCount();
        check(name + " has " + expected + " accessible children (got " + count + ")", count == expected);
    }

    public static void main(String[] args) throws Exception {
        role("JPanel", new JPanel(), AccessibleRole.PANEL);
        role("JButton", new JButton(), AccessibleRole.PUSH_BUTTON);
        role("JToggleButton", new JToggleButton(), AccessibleRole.TOGGLE_BUTTON);
        role("JCheckBox", new JCheckBox(), AccessibleRole.CHECK_BOX);
        role("JRadioButton", new JRadioButton(), AccessibleRole.RADIO_BUTTON);
        role("JMenuItem", new JMenuItem(), AccessibleRole.MENU_ITEM);
        role("JMenu", new JMenu(), AccessibleRole.MENU);
        role("JCheckBoxMenuItem", new JCheckBoxMenuItem(), AccessibleRole.CHECK_BOX);
        role("JRadioButtonMenuItem", new JRadioButtonMenuItem(), AccessibleRole.RADIO_BUTTON);
        role("JLabel", new JLabel(), AccessibleRole.LABEL);
        role("JTextField", new JTextField(), AccessibleRole.TEXT);
        role("JPasswordField", new JPasswordField(), AccessibleRole.PASSWORD_TEXT);
        role("JTextArea", new JTextArea(), AccessibleRole.TEXT);
        role("JEditorPane", new JEditorPane(), AccessibleRole.TEXT);
        role("JTextPane", new JTextPane(), AccessibleRole.TEXT);
        role("JFormattedTextField", new JFormattedTextField(), AccessibleRole.TEXT);
        role("JList", new JList<String>(), AccessibleRole.LIST);
        role("JTable", new JTable(), AccessibleRole.TABLE);
        role("JTableHeader", new JTableHeader(), AccessibleRole.PANEL);
        role("JTree", new JTree(), AccessibleRole.TREE);
        role("JComboBox", new JComboBox<String>(), AccessibleRole.COMBO_BOX);
        role("JTabbedPane", new JTabbedPane(), AccessibleRole.PAGE_TAB_LIST);
        role("JMenuBar", new JMenuBar(), AccessibleRole.MENU_BAR);
        role("JPopupMenu", new JPopupMenu(), AccessibleRole.POPUP_MENU);
        role("JSeparator", new JSeparator(), AccessibleRole.SEPARATOR);
        role("JSlider", new JSlider(), AccessibleRole.SLIDER);
        role("JProgressBar", new JProgressBar(), AccessibleRole.PROGRESS_BAR);
        role("JScrollBar", new JScrollBar(), AccessibleRole.SCROLL_BAR);
        role("JSpinner", new JSpinner(), AccessibleRole.SPIN_BOX);
        role("JSplitPane", new JSplitPane(), AccessibleRole.SPLIT_PANE);
        role("JScrollPane", new JScrollPane(), AccessibleRole.SCROLL_PANE);
        role("JViewport", new JViewport(), AccessibleRole.VIEWPORT);
        role("JLayeredPane", new JLayeredPane(), AccessibleRole.LAYERED_PANE);
        role("JRootPane", new JRootPane(), AccessibleRole.ROOT_PANE);
        role("JOptionPane", new JOptionPane(), AccessibleRole.OPTION_PANE);
        role("JColorChooser", new JColorChooser(), AccessibleRole.COLOR_CHOOSER);
        role("JFileChooser", new JFileChooser(), AccessibleRole.FILE_CHOOSER);
        role("Box", Box.createHorizontalBox(), AccessibleRole.FILLER);
        role("Box.Filler", Box.createHorizontalGlue(), AccessibleRole.FILLER);
        role("Panel", new Panel(), AccessibleRole.PANEL);
        role("Canvas", new Canvas(), AccessibleRole.CANVAS);
        windowRole("JFrame", JFrame::new, AccessibleRole.FRAME);
        windowRole("JDialog", JDialog::new, AccessibleRole.DIALOG);
        windowRole("JWindow", JWindow::new, AccessibleRole.WINDOW);

        // Fixture tree: one non-accessible component sits among the accessible ones.
        JPanel root = new JPanel();
        JButton button = new JButton("OK");
        JLabel label = new JLabel("Name");
        JTextField field = new JTextField("text");
        JComponent plain = new JComponent() {};
        JCheckBox checkBox = new JCheckBox("check");
        JProgressBar progress = new JProgressBar();
        JSeparator separator = new JSeparator();
        JList<String> list = new JList<>(new String[]{"a", "b", "c"});
        JTable table = new JTable(2, 3);
        DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("root");
        treeRoot.add(new DefaultMutableTreeNode("one"));
        treeRoot.add(new DefaultMutableTreeNode("two"));
        JTree tree = new JTree(treeRoot);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("First", new JPanel());
        tabs.addTab("Second", new JPanel());
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        menu.add(new JMenuItem("Close"));
        bar.add(menu);
        Component[] accessible = {button, label, field, checkBox, progress, separator, list, table, tree, tabs, bar};
        root.add(button);
        root.add(label);
        root.add(field);
        root.add(plain);
        for (int i = 3; i < accessible.length; i++) root.add(accessible[i]);

        children("fixture root", root, accessible.length);
        AccessibleContext rootContext = context(root);
        boolean order = true, parents = true, indices = true;
        for (int i = 0; i < accessible.length; i++) {
            Accessible child = rootContext.getAccessibleChild(i);
            order &= child == accessible[i];
            AccessibleContext ac = child == null ? null : child.getAccessibleContext();
            parents &= ac != null && ac.getAccessibleParent() == root;
            indices &= ac != null && ac.getAccessibleIndexInParent() == i;
        }
        check("fixture children come in component order, skipping non-accessible ones", order);
        check("fixture children report the root as accessible parent", parents);
        check("fixture children report their index among accessible siblings", indices);
        check("fixture root has no accessible parent", rootContext.getAccessibleParent() == null);
        check("child past the end is null", rootContext.getAccessibleChild(accessible.length) == null);

        children("JButton", button, 0);
        children("JLabel", label, 0);
        children("JTextField", field, 0);
        children("JCheckBox", checkBox, 0);
        children("JProgressBar", progress, 0);
        children("JSeparator", separator, 0);
        children("JList of 3", list, 3);
        children("JTable of 2x3", table, 6);
        children("JTree with visible root", tree, 1);
        tree.setRootVisible(false);
        children("JTree with hidden root", tree, 2);
        children("JTabbedPane of 2", tabs, 2);
        children("JMenuBar", bar, 1);
        children("JMenu of 2 items", menu, 2);

        AccessibleContext page = context(tabs).getAccessibleChild(1).getAccessibleContext();
        check("tab page role PAGE_TAB", AccessibleRole.PAGE_TAB.equals(page.getAccessibleRole()));
        check("tab page named by its title", "Second".equals(page.getAccessibleName()));
        check("tab page index", page.getAccessibleIndexInParent() == 1);
        check("tab page holds its component", page.getAccessibleChildrenCount() == 1);

        check("button named by its text", "OK".equals(context(button).getAccessibleName()));
        check("label named by its text", "Name".equals(context(label).getAccessibleName()));
        button.setToolTipText("tip");
        check("description falls back to the tool tip", "tip".equals(context(button).getAccessibleDescription()));
        context(label).setAccessibleName("explicit");
        check("explicit name wins", "explicit".equals(context(label).getAccessibleName()));

        AccessibleAction action = context(checkBox).getAccessibleAction();
        check("button exposes one action", action != null && action.getAccessibleActionCount() == 1);
        if (action != null) action.doAccessibleAction(0);
        check("button action clicks", checkBox.isSelected());
        AccessibleValue value = context(checkBox).getAccessibleValue();
        check("selected button value is 1", value != null && value.getCurrentAccessibleValue().intValue() == 1);
        progress.setValue(40);
        AccessibleValue progressValue = context(progress).getAccessibleValue();
        check("progress value follows the model", progressValue != null
            && progressValue.getCurrentAccessibleValue().intValue() == 40
            && progressValue.getMaximumAccessibleValue().intValue() == 100);

        JPanel holder = new JPanel();
        holder.setPreferredSize(new Dimension(10, 10));
        root.add(holder);
        children("fixture root after add", root, accessible.length + 1);
        root.remove(holder);
        children("fixture root after remove", root, accessible.length);

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }
}
