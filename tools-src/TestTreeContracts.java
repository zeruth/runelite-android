// host-contract
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.DefaultCellEditor;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * JTree + BasicTreeUI contracts, held identical on the host JDK and on the shadow:
 * row/path mapping under expansion, TreeWillExpandListener vetoes, editing that
 * commits through the model, and the parameters the cell renderer is called with.
 */
public class TestTreeContracts {
    private static final int ROW_HEIGHT = 20;

    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static DefaultMutableTreeNode node(String name, DefaultMutableTreeNode... children) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(name);
        for (DefaultMutableTreeNode child : children) node.add(child);
        return node;
    }

    private static TreePath path(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
    }

    private static String rows(JTree tree) {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < tree.getRowCount(); row++) {
            if (row > 0) out.append(',');
            out.append(tree.getPathForRow(row).getLastPathComponent());
        }
        return out.toString();
    }

    private static JTree basicTree(DefaultMutableTreeNode root) {
        JTree tree = new JTree(root);
        tree.setUI(new BasicTreeUI());
        tree.setRowHeight(ROW_HEIGHT);
        tree.setSize(300, 400);
        return tree;
    }

    // root ─ a ─ a1, a2 │ b ─ b1 ─ b1x │ c
    private static void rowMapping() {
        DefaultMutableTreeNode a1 = node("a1"), a2 = node("a2"), b1x = node("b1x");
        DefaultMutableTreeNode b1 = node("b1", b1x);
        DefaultMutableTreeNode a = node("a", a1, a2), b = node("b", b1), c = node("c");
        DefaultMutableTreeNode root = node("root", a, b, c);
        JTree tree = basicTree(root);

        equal("root,a,b,c", rows(tree), "the root starts expanded");
        tree.expandRow(1);
        equal("root,a,a1,a2,b,c", rows(tree), "expandRow(1) opens a");
        check(tree.isExpanded(path(a)) && tree.isExpanded(1), "a reports expanded");

        tree.expandPath(path(b1));
        equal("root,a,a1,a2,b,b1,b1x,c", rows(tree), "expandPath opens the collapsed ancestors too");
        equal(6, tree.getRowForPath(path(b1x)), "getRowForPath of a deep node");
        equal(path(b1x), tree.getPathForRow(6), "getPathForRow inverts getRowForPath");

        Rectangle first = tree.getRowBounds(0);
        for (int row = 0; row < tree.getRowCount(); row++) {
            Rectangle bounds = tree.getRowBounds(row);
            equal(first.y + row * ROW_HEIGHT, bounds.y, "row " + row + " y");
            equal(ROW_HEIGHT, bounds.height, "row " + row + " height");
            equal(tree.getPathForRow(row), tree.getPathForLocation(bounds.x + 1, bounds.y + 1),
                    "getPathForLocation inside row " + row + " (bounds " + bounds + ", closest "
                    + tree.getClosestPathForLocation(bounds.x + 1, bounds.y + 1) + ", insets " + tree.getInsets() + ")");
            equal(tree.getPathForRow(row), tree.getClosestPathForLocation(0, bounds.y + ROW_HEIGHT / 2),
                    "getClosestPathForLocation on row " + row);
        }
        check(tree.getRowBounds(5).x > tree.getRowBounds(4).x, "a child is indented past its parent");
        equal(null, tree.getPathForLocation(299, first.y + 1), "no path right of the row's cell");
        equal(tree.getPathForRow(tree.getRowCount() - 1), tree.getClosestPathForLocation(0, 399),
                "below the last row the closest path is the last row");

        tree.collapseRow(1);
        equal("root,a,b,b1,b1x,c", rows(tree), "collapseRow(1) closes a and keeps b open");
        equal(-1, tree.getRowForPath(path(a1)), "a hidden node has no row");
        check(tree.hasBeenExpanded(path(a)) && tree.isCollapsed(1), "a is collapsed after having been expanded");

        tree.setSelectionPath(path(a2));
        equal("root,a,a1,a2,b,b1,b1x,c", rows(tree), "expandsSelectedPaths opens the selected node's parent");
        equal(a2, tree.getLastSelectedPathComponent(), "getLastSelectedPathComponent");
        equal(path(a2), tree.getLeadSelectionPath(), "selection moves the lead");
        equal(3, tree.getLeadSelectionRow(), "lead row follows the mapping");

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
        tree.setSelectionInterval(1, 3);
        equal(3, tree.getSelectionCount(), "setSelectionInterval selects the row range");
        tree.addSelectionRow(6);
        equal(1, tree.getSelectionCount(), "a non-contiguous add resets a contiguous selection");

        tree.setRootVisible(false);
        equal("a,a1,a2,b,b1,b1x,c", rows(tree), "a hidden root drops row 0");
        equal(-1, tree.getRowForPath(path(root)), "the hidden root has no row");
        equal(0, tree.getRowForPath(path(a)), "rows renumber without the root");

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.insertNodeInto(node("a3"), a, 2);
        equal("a,a1,a2,a3,b,b1,b1x,c", rows(tree), "a model insert under an expanded node adds its row");
        model.removeNodeFromParent(b);
        equal("a,a1,a2,a3,c", rows(tree), "a model removal drops the subtree's rows");
    }

    private static void veto() {
        DefaultMutableTreeNode a = node("a", node("a1"));
        DefaultMutableTreeNode root = node("root", a, node("b"));
        JTree tree = basicTree(root);
        final List<String> events = new ArrayList<String>();
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                events.add("willExpand " + event.getPath().getLastPathComponent());
                throw new ExpandVetoException(event, "vetoed");
            }
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                events.add("willCollapse " + event.getPath().getLastPathComponent());
                if ("root".equals(event.getPath().getLastPathComponent().toString())) {
                    throw new ExpandVetoException(event, "vetoed");
                }
            }
        });
        tree.expandPath(path(a));
        check(!tree.isExpanded(path(a)), "a vetoed expansion leaves the node collapsed");
        equal(3, tree.getRowCount(), "a vetoed expansion adds no rows");
        tree.collapseRow(0);
        check(tree.isExpanded(0), "a vetoed collapse leaves the node expanded");
        equal(3, tree.getRowCount(), "a vetoed collapse keeps the rows");
        equal("[willExpand a, willCollapse root]", events.toString(), "the will-expand listener sees both");
    }

    private static void editing() {
        final DefaultMutableTreeNode leaf = node("leaf");
        DefaultMutableTreeNode root = node("root", leaf);
        JTree tree = basicTree(root);
        final List<TreeModelEvent> changes = new ArrayList<TreeModelEvent>();
        tree.getModel().addTreeModelListener(new TreeModelListener() {
            public void treeNodesChanged(TreeModelEvent e) { changes.add(e); }
            public void treeNodesInserted(TreeModelEvent e) { }
            public void treeNodesRemoved(TreeModelEvent e) { }
            public void treeStructureChanged(TreeModelEvent e) { }
        });
        JTextField field = new JTextField();
        tree.setCellEditor(new DefaultTreeCellEditor(tree, new DefaultTreeCellRenderer(), new DefaultCellEditor(field)));

        tree.startEditingAtPath(path(leaf));
        check(!tree.isEditing(), "a tree that is not editable does not start editing");

        tree.setEditable(true);
        tree.startEditingAtPath(path(leaf));
        check(tree.isEditing(), "startEditingAtPath starts the editor");
        equal(path(leaf), tree.getEditingPath(), "getEditingPath");
        equal("leaf", field.getText(), "the editor starts from the node's value");
        field.setText("renamed");
        check(tree.stopEditing(), "stopEditing reports that it stopped");
        check(!tree.isEditing(), "stopEditing ends the edit");
        equal("renamed", leaf.getUserObject(), "the edit commits through DefaultTreeModel.valueForPathChanged");
        equal(1, changes.size(), "the model fires one treeNodesChanged");
        equal(path(root), changes.get(0).getTreePath(), "treeNodesChanged names the parent");
        equal(0, changes.get(0).getChildIndices()[0], "treeNodesChanged names the child index");

        tree.startEditingAtPath(path(leaf));
        field.setText("discarded");
        tree.cancelEditing();
        check(!tree.isEditing(), "cancelEditing ends the edit");
        equal("renamed", leaf.getUserObject(), "a cancelled edit leaves the model alone");

        tree.setInvokesStopCellEditing(true);
        tree.startEditingAtPath(path(leaf));
        field.setText("by selection");
        tree.setSelectionPath(path(root));
        check(!tree.isEditing(), "with invokesStopCellEditing a selection change stops the edit");
        equal("by selection", leaf.getUserObject(), "and commits it");
    }

    private static final class Call {
        final Object value;
        final boolean selected, expanded, leaf, hasFocus;
        final int row;

        Call(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            this.value = value;
            this.selected = selected;
            this.expanded = expanded;
            this.leaf = leaf;
            this.row = row;
            this.hasFocus = hasFocus;
        }
    }

    private static void renderer() {
        DefaultMutableTreeNode a = node("a", node("a1"), node("a2"));
        DefaultMutableTreeNode root = node("root", a, node("b", node("b1")), node("c"));
        JTree tree = basicTree(root);
        final List<Call> calls = new ArrayList<Call>();
        final JLabel label = new JLabel();
        tree.setCellRenderer(new TreeCellRenderer() {
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                calls.add(new Call(value, selected, expanded, leaf, row, hasFocus));
                label.setText(String.valueOf(value));
                return label;
            }
        });
        tree.expandRow(1);
        tree.setSelectionRow(2);

        BufferedImage image = new BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setClip(0, 0, 300, 400);
            // The first paint may also size rows through the renderer; the second
            // sees a valid layout cache, so it renders each row exactly once.
            tree.getUI().paint(g, tree);
            calls.clear();
            tree.getUI().paint(g, tree);
        } finally {
            g.dispose();
        }

        boolean[] painted = new boolean[tree.getRowCount()];
        for (Call call : calls) {
            check(call.row >= 0 && call.row < painted.length, "paint renders visible rows only, got " + call.row);
            TreePath rowPath = tree.getPathForRow(call.row);
            equal(rowPath.getLastPathComponent(), call.value, "row " + call.row + " value");
            equal(tree.isRowSelected(call.row), call.selected, "row " + call.row + " selected");
            equal(tree.isExpanded(call.row), call.expanded, "row " + call.row + " expanded");
            equal(tree.getModel().isLeaf(call.value), call.leaf, "row " + call.row + " leaf");
            equal(false, call.hasFocus, "row " + call.row + " hasFocus without focus");
            painted[call.row] = true;
        }
        for (int row = 0; row < painted.length; row++) {
            check(painted[row], "row " + row + " is rendered");
        }
        equal("root,a,a1,a2,b,c", rows(tree), "the painted tree");
        check(calls.size() == painted.length, "each row is rendered once, got " + calls.size());
    }

    public static void main(String[] args) {
        rowMapping();
        veto();
        editing();
        renderer();
        System.out.println("TestTreeContracts: ok");
    }
}
