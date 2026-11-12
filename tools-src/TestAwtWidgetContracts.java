// host-contract: runs=patched
// The heavyweight AWT widgets as android-awt paints them: the JDK's events for the user gestures, preferred
// sizes from FontMetrics, and List's selection model. Patched only: under -Djava.awt.headless=true the JDK's
// own widgets throw HeadlessException from their constructors, while the shadow's never need a peer.

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Label;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.util.ArrayList;
import java.util.Arrays;

public class TestAwtWidgetContracts {
    private static int failures;

    public static void main(String[] args) {
        preferredSizes();
        buttonAction();
        checkboxItems();
        listSelection();
        listGestures();
        scrollbarAdjustment();
        textEditing();
        menus();
        if (failures > 0) {
            System.out.println(failures + " widget contract(s) failed");
            System.exit(1);
        }
        System.out.println("widget contracts passed");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static void preferredSizes() {
        Choice choice = new Choice();
        choice.add("first");
        choice.add("a longer second");
        Component[] widgets = {
            new Label("Hello"), new Button("OK"), new Checkbox("Check"), choice, new List(4),
            new Scrollbar(), new Scrollbar(Scrollbar.HORIZONTAL), new TextField(12), new TextField("text"),
            new TextArea(3, 20), new TextArea("one\ntwo")
        };
        for (Component widget : widgets) {
            Dimension size = widget.getPreferredSize();
            check(size.width > 0 && size.height > 0, widget.getClass().getSimpleName() + " preferred size " + size);
            Dimension min = widget.getMinimumSize();
            check(min.width > 0 && min.height > 0, widget.getClass().getSimpleName() + " minimum size " + min);
        }
        Button narrow = new Button("OK"), wide = new Button("A much longer label");
        check(wide.getPreferredSize().width > narrow.getPreferredSize().width, "button width follows its label");
        check(new TextField(20).getPreferredSize().width > new TextField(5).getPreferredSize().width,
              "text field width follows its columns");
        check(new TextArea(6, 10).getPreferredSize().height > new TextArea(2, 10).getPreferredSize().height,
              "text area height follows its rows");

        Panel panel = new Panel();
        panel.add(new Label("in a panel"));
        check(panel.getComponentCount() == 1, "panel holds its widget");
    }

    private static MouseEvent mouse(Component c, int id, int x, int y, int clicks) {
        return new MouseEvent(c, id, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, x, y, clicks, false,
                              MouseEvent.BUTTON1);
    }

    private static void click(Component c, int x, int y) {
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_PRESSED, x, y, 1));
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_RELEASED, x, y, 1));
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_CLICKED, x, y, 1));
    }

    private static void buttonAction() {
        Button button = new Button("Go");
        button.setSize(button.getPreferredSize());
        ArrayList<ActionEvent> actions = new ArrayList<>();
        button.addActionListener(actions::add);
        click(button, 2, 2);
        check(actions.size() == 1, "button click fires one ActionEvent, got " + actions.size());
        check(!actions.isEmpty() && "Go".equals(actions.get(0).getActionCommand()), "button command defaults to its label");
        button.setActionCommand("cmd");
        click(button, 2, 2);
        check(actions.size() == 2 && "cmd".equals(actions.get(1).getActionCommand()), "button uses its action command");

        // Released outside the button: no action, as the desktop peers.
        button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 2, 2, 1));
        button.dispatchEvent(mouse(button, MouseEvent.MOUSE_RELEASED, -5, 2, 1));
        check(actions.size() == 2, "button released outside fires nothing");

        ActionEvent[] second = new ActionEvent[1];
        java.awt.event.ActionListener extra = e -> second[0] = e;
        button.addActionListener(extra);
        check(button.getActionListeners().length == 2, "button keeps two listeners");
        button.removeActionListener(extra);
        check(button.getActionListeners().length == 1, "button removes a listener");
    }

    private static void checkboxItems() {
        Checkbox box = new Checkbox("Box");
        box.setSize(box.getPreferredSize());
        ArrayList<ItemEvent> items = new ArrayList<>();
        box.addItemListener(items::add);
        click(box, 2, 2);
        check(box.getState(), "checkbox click selects it");
        check(items.size() == 1 && items.get(0).getStateChange() == ItemEvent.SELECTED, "checkbox fires SELECTED");
        click(box, 2, 2);
        check(!box.getState() && items.size() == 2 && items.get(1).getStateChange() == ItemEvent.DESELECTED,
              "checkbox second click fires DESELECTED");
        box.setState(true);
        check(items.size() == 2, "checkbox setState fires nothing");

        CheckboxGroup group = new CheckboxGroup();
        Checkbox a = new Checkbox("A", group, true), b = new Checkbox("B", group, false);
        b.setSize(b.getPreferredSize());
        click(b, 2, 2);
        check(group.getSelectedCheckbox() == b && b.getState() && !a.getState(), "radio click moves the group selection");
        click(b, 2, 2);
        check(b.getState(), "a selected radio stays selected");

        Choice choice = new Choice();
        choice.add("x");
        choice.add("y");
        check(choice.getSelectedIndex() == 0, "choice selects its first item");
        choice.select(1);
        check(choice.getSelectedIndex() == 1 && "y".equals(choice.getSelectedItem()), "choice select(int)");
        choice.select("x");
        check(choice.getSelectedIndex() == 0, "choice select(String)");
    }

    private static void listSelection() {
        List list = new List(4, false);
        list.add("a");
        list.add("b");
        list.add("c");
        check(list.getItemCount() == 3 && list.getSelectedIndex() == -1, "list starts unselected");
        list.select(1);
        check(list.getSelectedIndex() == 1 && "b".equals(list.getSelectedItem()), "list select(1)");
        list.select(2);
        check(Arrays.equals(list.getSelectedIndexes(), new int[] {2}), "single-mode select replaces the selection");
        check(!list.isIndexSelected(1), "single-mode list deselected the old index");

        list.setMultipleMode(true);
        check(list.isMultipleMode(), "list multiple mode");
        list.select(0);
        int[] both = list.getSelectedIndexes();
        Arrays.sort(both);
        check(Arrays.equals(both, new int[] {0, 2}), "multiple-mode select adds, got " + Arrays.toString(both));
        check(list.getSelectedIndex() == -1, "getSelectedIndex is -1 with two selected");
        check(list.getSelectedItems().length == 2, "getSelectedItems has both");
        list.deselect(0);
        check(Arrays.equals(list.getSelectedIndexes(), new int[] {2}), "deselect removes one index");
        check(list.getRows() == 4, "list keeps its rows");
    }

    private static void listGestures() {
        List list = new List(5, false);
        list.add("zero");
        list.add("one");
        list.add("two");
        list.setSize(120, 200);
        ArrayList<ItemEvent> items = new ArrayList<>();
        ArrayList<ActionEvent> actions = new ArrayList<>();
        list.addItemListener(items::add);
        list.addActionListener(actions::add);
        // The list has no font of its own here; the widgets then measure with the peers' Dialog 12.
        list.setFont(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12));
        int rowHeight = list.getFontMetrics(list.getFont()).getHeight() + 2;
        int y = 3 + rowHeight + rowHeight / 2;
        click(list, 10, y);
        check(list.getSelectedIndex() == 1, "list press selects the row under it, got " + list.getSelectedIndex());
        check(items.size() == 1 && items.get(0).getStateChange() == ItemEvent.SELECTED
              && Integer.valueOf(1).equals(items.get(0).getItem()), "list fires SELECTED with the row index");
        list.dispatchEvent(mouse(list, MouseEvent.MOUSE_CLICKED, 10, y, 2));
        check(actions.size() == 1 && "one".equals(actions.get(0).getActionCommand()), "list double click fires the item");
    }

    private static void scrollbarAdjustment() {
        Scrollbar bar = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        bar.setSize(200, 16);
        ArrayList<AdjustmentEvent> adjustments = new ArrayList<>();
        bar.addAdjustmentListener(adjustments::add);
        bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, 198, 8, 1));
        bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_RELEASED, 198, 8, 1));
        check(bar.getValue() == 1, "scrollbar arrow steps one unit, got " + bar.getValue());
        check(!adjustments.isEmpty() && adjustments.get(0).getAdjustmentType() == AdjustmentEvent.UNIT_INCREMENT
              && adjustments.get(0).getValue() == 1, "scrollbar fires UNIT_INCREMENT");
        bar.setValue(500);
        check(bar.getValue() == 90, "scrollbar clamps to maximum - visible, got " + bar.getValue());
    }

    private static KeyEvent key(Component c, int id, int code, char ch) {
        return new KeyEvent(c, id, System.currentTimeMillis(), 0, code, ch);
    }

    private static void type(Component c, String text) {
        for (char ch : text.toCharArray()) c.dispatchEvent(key(c, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ch));
    }

    private static void textEditing() {
        TextField field = new TextField(10);
        ArrayList<TextEvent> texts = new ArrayList<>();
        ArrayList<ActionEvent> actions = new ArrayList<>();
        field.addTextListener(texts::add);
        field.addActionListener(actions::add);
        type(field, "ab");
        check("ab".equals(field.getText()), "typing edits the field, got " + field.getText());
        check(texts.size() == 2, "each typed character fires a TextEvent, got " + texts.size());
        field.dispatchEvent(key(field, KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, '\b'));
        check("a".equals(field.getText()), "backspace deletes before the caret");
        field.dispatchEvent(key(field, KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n'));
        check(actions.size() == 1 && "a".equals(actions.get(0).getActionCommand()), "return fires the field's text");
        field.setText("xyz");
        check(texts.size() == 4 && "xyz".equals(field.getText()), "setText fires a TextEvent");
        field.select(1, 3);
        check("yz".equals(field.getSelectedText()), "field selection");
        type(field, "Q");
        check("xQ".equals(field.getText()), "typing replaces the selection, got " + field.getText());
        field.setEditable(false);
        type(field, "n");
        check("xQ".equals(field.getText()), "a non-editable field ignores typing");
        field.setEchoChar('*');
        check(field.echoCharIsSet() && field.getEchoChar() == '*', "echo char");

        TextArea area = new TextArea("ab", 3, 10);
        ArrayList<TextEvent> areaTexts = new ArrayList<>();
        area.addTextListener(areaTexts::add);
        area.append("\ncd");
        check("ab\ncd".equals(area.getText()) && areaTexts.size() == 1, "append edits and fires");
        area.insert("X", 0);
        area.replaceRange("Y", 1, 3);
        check("XY\ncd".equals(area.getText()), "insert/replaceRange, got " + area.getText());
        area.setCaretPosition(5);
        area.dispatchEvent(key(area, KeyEvent.KEY_PRESSED, KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED));
        check(area.getCaretPosition() == 3, "home goes to the line start, got " + area.getCaretPosition());
        area.dispatchEvent(key(area, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n'));
        check("XY\n\ncd".equals(area.getText()), "a text area takes newlines");
        check(area.getScrollbarVisibility() == TextArea.SCROLLBARS_BOTH, "text area scrollbar policy");
        check(new TextArea("", 1, 1, 42).getScrollbarVisibility() == TextArea.SCROLLBARS_BOTH, "bad policy is BOTH");
    }

    private static void menus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("File");
        MenuItem open = new MenuItem("Open", new MenuShortcut(KeyEvent.VK_O));
        CheckboxMenuItem wrap = new CheckboxMenuItem("Wrap", true);
        file.add(open);
        file.addSeparator();
        file.add(wrap);
        bar.add(file);
        check(bar.getMenuCount() == 1 && bar.getMenu(0) == file && file.getParent() == bar, "menu bar holds its menu");
        check(file.getItemCount() == 3 && open.getParent() == file, "menu holds its items");
        check(bar.getShortcutMenuItem(new MenuShortcut(KeyEvent.VK_O)) == open, "shortcut lookup");
        check(bar.shortcuts().hasMoreElements(), "shortcuts enumerates");
        bar.setHelpMenu(new Menu("Help"));
        check(bar.getMenuCount() == 2 && bar.getHelpMenu() != null, "help menu is added");
        check(wrap.getState(), "checkbox menu item state");

        ActionEvent[] fired = new ActionEvent[1];
        open.addActionListener(e -> fired[0] = e);
        open.dispatchEvent(new ActionEvent(open, ActionEvent.ACTION_PERFORMED, open.getActionCommand()));
        check(fired[0] != null && "Open".equals(fired[0].getActionCommand()), "menu item delivers its action");
        file.remove(open);
        check(open.getParent() == null && file.getItemCount() == 2, "menu removes an item");
    }
}
