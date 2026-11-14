// host-contract
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TreeSet;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * Range controls against the JDK: slider value/extent clamping and standard label tables,
 * spinner editors committing and rejecting text, split pane divider arithmetic and layout,
 * and tabbed pane selection, per-tab state and the index/bounds relationship. Tab geometry
 * depends on the look and feel's fonts, so the bounds checks are the invariants the JDK keeps
 * (hit testing a tab's centre finds it, a row of tabs shares its y and does not overlap).
 */
public final class TestRangeControlsContracts {
    public static void main(String[] args) throws Exception {
        slider();
        spinner();
        splitPane();
        tabbedPane();
        System.out.println("PASS: slider, spinner, split pane and tabbed pane contracts");
    }

    private static void slider() {
        JSlider s = new JSlider(0, 100, 50);
        check(s.getOrientation() == SwingConstants.HORIZONTAL, "default orientation");
        check(s.getPaintTrack() && !s.getPaintTicks() && !s.getPaintLabels(), "paint defaults");
        check(s.getExtent() == 0, "default extent");
        final Object[] source = new Object[1];
        s.addChangeListener(e -> source[0] = e.getSource());
        s.setExtent(10);
        check(s.getExtent() == 10, "extent set");
        s.setValue(95);
        check(s.getValue() == 90, "value clamps to maximum - extent: " + s.getValue());
        check(source[0] == s, "change event source is the slider");
        s.setValue(-5);
        check(s.getValue() == 0, "value clamps to minimum");
        s.setExtent(200);
        check(s.getExtent() == 100, "extent clamps to the range: " + s.getExtent());
        s.setExtent(0);
        s.setSnapToTicks(true);
        s.setMinorTickSpacing(10);
        s.setValue(33);
        // The installed BasicSliderUI snaps a model change to the nearest tick (calculateThumbLocation).
        check(s.getValue() == 30, "the slider's UI snaps the value to a tick: " + s.getValue());
        check(s.getUI() instanceof javax.swing.plaf.basic.BasicSliderUI, "a slider gets its UI at construction");
        s.setInverted(true);
        check(s.getValue() == 30 && s.getInverted(), "inverted leaves the value");

        Hashtable<?, ?> labels = s.createStandardLabels(25);
        check(new TreeSet<Object>(labels.keySet()).equals(new TreeSet<Object>(Arrays.asList(0, 25, 50, 75, 100))),
            "standard label keys: " + labels.keySet());
        for (Object key : labels.keySet()) {
            JLabel label = (JLabel) labels.get(key);
            check(label.getText().equals(String.valueOf(key)), "label text for " + key);
            check(label.getHorizontalAlignment() == SwingConstants.CENTER, "label alignment");
        }
        check(new TreeSet<Object>(s.createStandardLabels(25, 10).keySet())
            .equals(new TreeSet<Object>(Arrays.asList(10, 35, 60, 85))), "labels from a start");
        s.setLabelTable(labels);
        check(s.getLabelTable() == labels, "label table kept");
        for (Object key : labels.keySet()) {
            JComponent label = (JComponent) labels.get(key);
            check(label.getSize().equals(label.getPreferredSize()), "a table label is sized to its preferred size");
        }
        expect(IllegalArgumentException.class, () -> s.createStandardLabels(0));
        expect(IllegalArgumentException.class, () -> s.createStandardLabels(10, 101));
        expect(IllegalArgumentException.class, () -> new JSlider(5));
        expect(IllegalArgumentException.class, () -> s.setOrientation(5));

        JSlider ticks = new JSlider(0, 100, 0);
        ticks.setMajorTickSpacing(50);
        ticks.setPaintLabels(true);
        Dictionary<?, ?> lazy = ticks.getLabelTable();
        check(lazy != null && lazy.size() == 3 && lazy.get(50) instanceof JLabel, "paintLabels builds labels at the major ticks");
    }

    private static void spinner() throws Exception {
        SpinnerNumberModel model = new SpinnerNumberModel(5, 0, 10, 1);
        JSpinner numbers = new JSpinner(model);
        check(numbers.getEditor() instanceof JSpinner.NumberEditor, "number editor");
        JFormattedTextField field = ((JSpinner.DefaultEditor) numbers.getEditor()).getTextField();
        check(field.isEditable(), "number field is editable");
        field.setText("8");
        numbers.commitEdit();
        check(Integer.valueOf(8).equals(numbers.getValue()), "commit pushes the parsed value: " + numbers.getValue());
        field.setText("12");
        boolean rejected = false;
        try {
            numbers.commitEdit();
        } catch (ParseException expected) {
            rejected = true;
        }
        check(rejected, "a value above the model maximum does not commit");
        check(Integer.valueOf(8).equals(numbers.getValue()), "rejected commit keeps the value");
        model.setValue(3);
        check(Integer.valueOf(3).equals(field.getValue()), "model changes reach the field");
        model.setValue(10);
        check(numbers.getNextValue() == null, "no next value at the maximum");
        model.setValue(0);
        check(numbers.getPreviousValue() == null, "no previous value at the minimum");

        JSpinner list = new JSpinner(new SpinnerListModel(Arrays.asList("first", "second", "third")));
        check(list.getEditor() instanceof JSpinner.ListEditor, "list editor");
        JFormattedTextField listField = ((JSpinner.DefaultEditor) list.getEditor()).getTextField();
        listField.setText("second");
        list.commitEdit();
        check("second".equals(list.getValue()), "list commit: " + list.getValue());
        list.setValue("third");
        check("third".equals(listField.getValue()), "list value reaches the field");
        check(list.getNextValue() == null && "second".equals(list.getPreviousValue()), "list neighbours");
        listField.setText("bogus");
        try {
            list.commitEdit();
        } catch (ParseException ignored) {
            // Either rejection is the JDK's: the formatter's, or the model's caught by the editor.
        }
        check("third".equals(list.getValue()), "an unknown element does not reach the model");
        expect(IllegalArgumentException.class, () -> list.setValue("bogus"));
        expect(IllegalArgumentException.class, () -> new SpinnerListModel(Arrays.asList()));

        expect(IllegalArgumentException.class, () -> new JSpinner.DateEditor(new JSpinner()));
        expect(IllegalArgumentException.class, () -> new JSpinner.NumberEditor(list));
        expect(IllegalArgumentException.class, () -> new JSpinner.ListEditor(numbers));
        expect(IllegalArgumentException.class, () -> numbers.setEditor(null));
        expect(IllegalArgumentException.class, () -> numbers.setModel(null));
    }

    private static void splitPane() {
        JPanel left = new JPanel(null), right = new JPanel(null);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setBorder(null);
        split.setDividerSize(10);
        split.setSize(410, 200);
        check(split.getDividerLocation() == -1, "no divider location before one is set");
        check(split.getLeftComponent() == left && split.getTopComponent() == left, "left is top");
        split.setDividerLocation(0.5);
        check(split.getDividerLocation() == 200, "proportional location excludes the divider: " + split.getDividerLocation());
        split.setDividerLocation(120);
        check(split.getDividerLocation() == 120, "absolute location");
        check(split.getLastDividerLocation() == 200, "last location is the previous one: " + split.getLastDividerLocation());
        split.doLayout();
        check(left.getBounds().equals(new Rectangle(0, 0, 120, 200)), "left bounds " + left.getBounds());
        check(right.getBounds().equals(new Rectangle(130, 0, 280, 200)), "right bounds " + right.getBounds());
        left.setMinimumSize(new java.awt.Dimension(30, 0));
        right.setMinimumSize(new java.awt.Dimension(50, 0));
        check(split.getMinimumDividerLocation() == 30, "minimum divider location " + split.getMinimumDividerLocation());
        check(split.getMaximumDividerLocation() == 350, "maximum divider location " + split.getMaximumDividerLocation());
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI());
        split.setBorder(null); // the JDK's installDefaults puts the SplitPane.border back
        check(split.getMinimumDividerLocation() == 30 && split.getMaximumDividerLocation() == 350,
                "Basic UI divider bounds " + split.getMinimumDividerLocation() + ".." + split.getMaximumDividerLocation());
        check(((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider() != null, "Basic UI has a divider");

        JSplitPane placeholder = new JSplitPane();
        check(placeholder.getOrientation() == JSplitPane.HORIZONTAL_SPLIT, "default orientation");
        check(placeholder.getLeftComponent() instanceof javax.swing.JButton
                && placeholder.getRightComponent() instanceof javax.swing.JButton, "default pane holds two buttons");

        JPanel top = new JPanel(null), bottom = new JPanel(null);
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        vertical.setBorder(null);
        vertical.setDividerSize(10);
        vertical.setSize(200, 310);
        vertical.setDividerLocation(0.25);
        check(vertical.getDividerLocation() == 75, "vertical proportional location: " + vertical.getDividerLocation());
        vertical.doLayout();
        check(top.getBounds().equals(new Rectangle(0, 0, 200, 75)), "top bounds " + top.getBounds());
        check(bottom.getBounds().equals(new Rectangle(0, 85, 200, 225)), "bottom bounds " + bottom.getBounds());

        expect(IllegalArgumentException.class, () -> split.setDividerLocation(1.5));
        expect(IllegalArgumentException.class, () -> split.setResizeWeight(-0.1));
        expect(IllegalArgumentException.class, () -> split.setOrientation(7));
        split.setResizeWeight(0.75);
        check(split.getResizeWeight() == 0.75, "resize weight");
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(false);
        check(split.isOneTouchExpandable() && !split.isContinuousLayout(), "flags");
    }

    private static void tabbedPane() {
        JTabbedPane tabs = new JTabbedPane();
        check(tabs.getSelectedIndex() == -1 && tabs.getTabCount() == 0, "empty pane");
        check(tabs.indexAtLocation(5, 5) == -1, "no tab in an empty pane");
        tabs.setSize(400, 300);
        tabs.addTab("Alpha", new JPanel());
        check(tabs.getSelectedIndex() == 0, "the first tab selects itself");
        tabs.addTab("Beta", null, new JPanel(), "beta tip");
        tabs.addTab("Gamma", new JPanel());
        tabs.insertTab("Zero", null, new JPanel(), null, 0);
        check(tabs.getSelectedIndex() == 1, "inserting before the selection shifts it");
        tabs.removeTabAt(0);
        check(tabs.getSelectedIndex() == 0 && "Alpha".equals(tabs.getTitleAt(0)), "removing before the selection shifts it back");
        check(tabs.indexOfTab("Beta") == 1 && tabs.getToolTipTextAt(1).equals("beta tip"), "titles and tips");
        check(tabs.getTabRunCount() == 1, "one run");

        Rectangle previous = null;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Rectangle r = tabs.getBoundsAt(i);
            check(r.width > 0 && r.height > 0, "tab " + i + " has bounds " + r);
            check(tabs.indexAtLocation(r.x + r.width / 2, r.y + r.height / 2) == i, "tab " + i + " is hit at its centre");
            // Basic grows the selected tab by selectedTabPadInsets and Metal does not, so the run
            // invariants hold between unselected neighbours under either look and feel.
            if (previous != null && i - 1 != tabs.getSelectedIndex()) {
                check(r.y == previous.y && r.height == previous.height, "a run shares y and height");
                check(r.x >= previous.x + previous.width, "tabs do not overlap");
            }
            previous = r;
        }
        check(tabs.indexAtLocation(previous.x + previous.width + 5, previous.y + previous.height / 2) == -1,
            "past the last tab is no tab");
        check(tabs.indexAtLocation(previous.x + previous.width / 2, 295) == -1, "the content area is no tab");
        expect(IndexOutOfBoundsException.class, () -> tabs.getBoundsAt(3));
        expect(IndexOutOfBoundsException.class, () -> tabs.getBoundsAt(-1));

        Rectangle beta = tabs.getBoundsAt(1);
        MouseEvent over = new MouseEvent(tabs, MouseEvent.MOUSE_MOVED, 0, 0,
            beta.x + beta.width / 2, beta.y + beta.height / 2, 0, false);
        check("beta tip".equals(tabs.getToolTipText(over)), "per-tab tooltip at the pointer");
        tabs.setEnabledAt(1, false);
        check(!tabs.isEnabledAt(1), "disabled");
        check(tabs.indexAtLocation(beta.x + beta.width / 2, beta.y + beta.height / 2) == 1, "a disabled tab is still located");
        tabs.setSelectedIndex(1);
        check(tabs.getSelectedIndex() == 1, "a disabled tab can be selected programmatically");
        tabs.setEnabledAt(1, true);

        tabs.setMnemonicAt(0, KeyEvent.VK_L);
        check(tabs.getMnemonicAt(0) == KeyEvent.VK_L, "mnemonic");
        check(tabs.getDisplayedMnemonicIndexAt(0) == 1, "displayed mnemonic index follows the title: " + tabs.getDisplayedMnemonicIndexAt(0));
        check(tabs.getDisplayedMnemonicIndexAt(2) == -1, "no displayed mnemonic by default");
        expect(IllegalArgumentException.class, () -> tabs.setDisplayedMnemonicIndexAt(0, 10));

        tabs.setBackgroundAt(0, Color.RED);
        tabs.setForegroundAt(0, Color.BLUE);
        check(Color.RED.equals(tabs.getBackgroundAt(0)) && Color.BLUE.equals(tabs.getForegroundAt(0)), "per-tab colors");
        JLabel header = new JLabel("G");
        tabs.setTabComponentAt(2, header);
        check(tabs.getTabComponentAt(2) == header && tabs.indexOfTabComponent(header) == 2, "tab component");

        tabs.setSelectedIndex(2);
        tabs.removeTabAt(2);
        check(tabs.getSelectedIndex() == 1, "removing the selected last tab selects the new last: " + tabs.getSelectedIndex());
        expect(IllegalArgumentException.class, () -> tabs.setTabPlacement(42));
        expect(IllegalArgumentException.class, () -> tabs.setTabLayoutPolicy(42));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        check(tabs.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT, "scroll layout policy");
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void expect(Class<? extends Throwable> type, Action action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type.getSimpleName() + ", got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
