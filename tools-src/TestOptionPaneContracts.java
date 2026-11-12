// host-contract
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * JOptionPane as a component: the buttons its UI delegate builds set value and inputValue,
 * createDialog wraps the pane in a modal JDialog, and the message layout splits lines, wraps
 * at getMaxCharactersPerLineCount and keeps nested components. Every check is JDK-true; the
 * runner passes -Djava.awt.headless=true, under which the JDK refuses a JDialog, so main turns
 * headless off before the first AWT class reads it (no dialog is shown).
 */
public class TestOptionPaneContracts {
    private static int checks;
    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        SwingUtilities.invokeAndWait(() -> {
            properties();
            buttons();
            input();
            layout();
            dialog();
        });
        System.out.println("PASS " + checks + " option pane contracts");
        System.exit(0);
    }

    private static <T extends Component> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container) found.addAll(find((Container) child, type));
        }
        return found;
    }

    /** The option buttons: the button area is the pane's last child (a combo box arrow is a JButton too). */
    private static List<JButton> buttons(JOptionPane pane) {
        return find((Container) pane.getComponent(pane.getComponentCount() - 1), JButton.class);
    }

    private static void properties() {
        JOptionPane pane = new JOptionPane();
        check("JOptionPane message".equals(pane.getMessage()), "default message");
        check(pane.getMessageType() == JOptionPane.PLAIN_MESSAGE && pane.getOptionType() == JOptionPane.DEFAULT_OPTION,
            "default types");
        check(pane.getValue() == JOptionPane.UNINITIALIZED_VALUE && pane.getInputValue() == JOptionPane.UNINITIALIZED_VALUE,
            "uninitialized value");
        check("OptionPaneUI".equals(pane.getUIClassID()) && pane.getUI() != null, "ui delegate");
        check(pane.getMaxCharactersPerLineCount() == Integer.MAX_VALUE, "default line length");
        List<String> events = new ArrayList<>();
        pane.addPropertyChangeListener(event -> events.add(event.getPropertyName()));
        pane.setMessageType(JOptionPane.WARNING_MESSAGE);
        pane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        pane.setInputValue("in");
        check(events.contains(JOptionPane.MESSAGE_TYPE_PROPERTY) && events.contains(JOptionPane.OPTION_TYPE_PROPERTY)
            && events.contains(JOptionPane.INPUT_VALUE_PROPERTY) && "in".equals(pane.getInputValue()), "bound properties");
        boolean threw = false;
        try { pane.setMessageType(99); } catch (RuntimeException expected) { threw = true; }
        check(threw && pane.getMessageType() == JOptionPane.WARNING_MESSAGE, "invalid message type");
        threw = false;
        try { pane.setOptionType(99); } catch (RuntimeException expected) { threw = true; }
        check(threw, "invalid option type");
        Object[] options = {"A", "B"};
        pane.setOptions(options);
        Object[] copy = pane.getOptions();
        copy[0] = "changed";
        check(copy != options && pane.getOptions()[0] == options[0], "options copied out");
        check(!pane.getWantsInput(), "no input by default");
        pane.setSelectionValues(new Object[] {"x"});
        check(pane.getWantsInput(), "selection values want input");

        JDesktopPane desktop = new JDesktopPane();
        JPanel inner = new JPanel();
        desktop.add(inner);
        check(JOptionPane.getDesktopPaneForComponent(inner) == desktop, "desktop ancestor");
        check(JOptionPane.getDesktopPaneForComponent(new JPanel()) == null
            && JOptionPane.getDesktopPaneForComponent(null) == null, "no desktop ancestor");
        check(JOptionPane.getFrameForComponent(null) == JOptionPane.getRootFrame()
            && JOptionPane.getRootFrame() == JOptionPane.getRootFrame(), "shared root frame");
    }

    private static void buttons() {
        JOptionPane yesNo = new JOptionPane("Delete?", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
        List<JButton> buttons = find(yesNo, JButton.class);
        check(buttons.size() == 2, "yes/no buttons");
        List<Object> values = new ArrayList<>();
        yesNo.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, event -> values.add(event.getNewValue()));
        buttons.get(1).doClick();
        check(Integer.valueOf(JOptionPane.NO_OPTION).equals(yesNo.getValue()) && values.size() == 1, "no pressed");

        JOptionPane okCancel = new JOptionPane("Save?", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        buttons = find(okCancel, JButton.class);
        check(buttons.size() == 2, "ok/cancel buttons");
        buttons.get(1).doClick();
        check(Integer.valueOf(JOptionPane.CANCEL_OPTION).equals(okCancel.getValue()), "cancel maps to CANCEL_OPTION");
        buttons.get(0).doClick();
        check(Integer.valueOf(JOptionPane.OK_OPTION).equals(okCancel.getValue()), "ok pressed");

        check(find(new JOptionPane("m", JOptionPane.ERROR_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION), JButton.class).size() == 3,
            "yes/no/cancel buttons");
        check(find(new JOptionPane("m"), JButton.class).size() == 1, "default option has one button");

        Object third = new Object() { public String toString() { return "Third"; } };
        Object[] options = {"First", "Second", third};
        JOptionPane custom = new JOptionPane("Pick", JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION,
            null, options, "Second");
        buttons = find(custom, JButton.class);
        check(buttons.size() == 3 && "Third".equals(buttons.get(2).getText()), "one button per option");
        buttons.get(2).doClick();
        check(custom.getValue() == third, "custom option value is the option");
        check(!custom.getUI().containsCustomComponents(custom), "string options are not custom");
    }

    private static void input() {
        JOptionPane text = new JOptionPane("Name", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        text.setWantsInput(true);
        text.setInitialSelectionValue("start");
        List<JTextField> fields = find(text, JTextField.class);
        check(fields.size() == 1 && "start".equals(fields.get(0).getText()), "initial selection in text field");
        fields.get(0).setText("typed");
        List<JButton> buttons = find(text, JButton.class);
        buttons.get(1).doClick();
        check(text.getInputValue() == JOptionPane.UNINITIALIZED_VALUE, "cancel keeps input value");
        buttons.get(0).doClick();
        check("typed".equals(text.getInputValue()) && Integer.valueOf(JOptionPane.OK_OPTION).equals(text.getValue()),
            "ok commits typed input");

        JOptionPane choice = new JOptionPane("Pick", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        Object[] values = {"x", "y", "z"};
        choice.setSelectionValues(values);
        choice.setInitialSelectionValue("y");
        List<JComboBox> boxes = find(choice, JComboBox.class);
        check(boxes.size() == 1 && "y".equals(boxes.get(0).getSelectedItem()), "combo box for few choices");
        boxes.get(0).setSelectedItem("z");
        find(choice, JButton.class).get(0).doClick();
        check(choice.getInputValue() == values[2], "ok commits chosen value");
    }

    private static List<String> labels(Container root) {
        List<String> texts = new ArrayList<>();
        for (JLabel label : find(root, JLabel.class)) {
            if ("OptionPane.label".equals(label.getName())) texts.add(label.getText());
        }
        return texts;
    }

    private static void layout() {
        check(labels(new JOptionPane("first\nsecond")).equals(java.util.Arrays.asList("first", "second")), "newline splits");
        JOptionPane wrapped = new JOptionPane("one two three four five") {
            @Override public int getMaxCharactersPerLineCount() { return 10; }
        };
        check(labels(wrapped).equals(java.util.Arrays.asList("one two", "three four", "five")), "wraps at max line length");
        JPanel form = new JPanel();
        JOptionPane nested = new JOptionPane(new Object[] {"Header", form}, JOptionPane.PLAIN_MESSAGE);
        check(SwingUtilities.isDescendingFrom(form, nested) && labels(nested).contains("Header"), "nested message components");
        check(nested.getUI().containsCustomComponents(nested), "custom component reported");
        nested.setMessage("Plain");
        check(form.getParent() == null && labels(nested).contains("Plain"), "message change rebuilds");
        check(!nested.getUI().containsCustomComponents(nested), "custom flag reset");
    }

    private static void dialog() {
        JOptionPane pane = new JOptionPane("Continue?", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = pane.createDialog(null, "Title");
        check("Title".equals(dialog.getTitle()) && dialog.isModal(), "modal titled dialog");
        check(dialog.getContentPane().getComponent(0) == pane && SwingUtilities.getWindowAncestor(pane) == dialog,
            "dialog contains the pane");
        check(dialog.getOwner() == JOptionPane.getRootFrame(), "parentless dialog owned by the root frame");
        check(!dialog.isResizable(), "dialog not resizable");
        find(pane, JButton.class).get(0).doClick();
        check(Integer.valueOf(JOptionPane.YES_OPTION).equals(pane.getValue()), "value readable after createDialog");
        dialog.dispose();

        JOptionPane second = new JOptionPane("Standalone");
        JDialog standalone = second.createDialog("Alone");
        check("Alone".equals(standalone.getTitle()) && standalone.getContentPane().getComponent(0) == second,
            "parentless createDialog");
        standalone.dispose();
    }
}
