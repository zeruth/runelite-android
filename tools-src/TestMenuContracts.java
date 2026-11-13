// host-contract: patched-args=shadow
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * Buttons and menus (#60), on the JDK and on the shadow: a MenuSelectionManager path selects the
 * menus and arms the items on it, an Action keeps the item or button in sync both ways, and
 * alt+mnemonic opens a menu through its bar's key binding pass. With {@code shadow} it also
 * checks popup placement against the host screen bounds: a headless JDK has no screen to place
 * a popup on, so those checks run on the shadow only.
 */
public class TestMenuContracts {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    /** The bar's key binding pass, which the KeyboardManager runs for an unclaimed stroke. */
    static final class Bar extends JMenuBar {
        boolean press(KeyStroke ks, KeyEvent e) {
            return processKeyBinding(ks, e, WHEN_IN_FOCUSED_WINDOW, true);
        }
    }

    /**
     * A 400x300 screen. The placement checks supply it, and the screen positions below, themselves:
     * the patched run keeps the JDK's Component, which has no screen without a native peer.
     */
    static final class Screen extends GraphicsConfiguration {
        public GraphicsDevice getDevice() { return null; }
        public ColorModel getColorModel() { return null; }
        public ColorModel getColorModel(int transparency) { return null; }
        public AffineTransform getDefaultTransform() { return new AffineTransform(); }
        public AffineTransform getNormalizingTransform() { return new AffineTransform(); }
        public Rectangle getBounds() { return new Rectangle(0, 0, 400, 300); }
    }

    static final Screen SCREEN = new Screen();

    /** The screen position the shadow gives a host-mounted tree: the sum of the parent offsets. */
    static Point onScreen(Component c) {
        Point p = new Point();
        for (Component at = c; at != null; at = at.getParent()) p.translate(at.getX(), at.getY());
        return p;
    }

    static final class Menu extends JMenu {
        Menu(String text) { super(text); }
        Point origin() { return getPopupMenuOrigin(); }
        @Override public Point getLocationOnScreen() { return onScreen(this); }
        @Override public GraphicsConfiguration getGraphicsConfiguration() { return SCREEN; }
    }

    static final class Invoker extends JPanel {
        @Override public Point getLocationOnScreen() { return onScreen(this); }
        @Override public GraphicsConfiguration getGraphicsConfiguration() { return SCREEN; }
    }

    public static void main(String[] args) throws Exception {
        final boolean shadow = args.length > 0 && args[0].equals("shadow");
        final Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                paths();
                actions();
                mnemonic();
                if (shadow) {
                    try {
                        placement();
                    } catch (Exception e) {
                        error[0] = e;
                    }
                }
            }
        });
        if (error[0] != null) throw error[0];
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("TestMenuContracts: ok" + (shadow ? " (shadow placement included)" : ""));
        System.exit(0);
    }

    private static void paths() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open");
        JMenuItem save = new JMenuItem("Save");
        file.add(open);
        file.add(save);
        bar.add(file);
        final int[] menuEvents = new int[2];
        file.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) { menuEvents[0]++; }
            @Override public void menuDeselected(MenuEvent e) { menuEvents[1]++; }
            @Override public void menuCanceled(MenuEvent e) {}
        });
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        final int[] changes = new int[1];
        ChangeListener counter = e -> changes[0]++;
        manager.addChangeListener(counter);

        manager.setSelectedPath(new MenuElement[] {bar, file, file.getPopupMenu(), open});
        MenuElement[] path = manager.getSelectedPath();
        check(path.length == 4 && path[3] == open, "the path is kept as set");
        check(file.isSelected(), "a menu on the path is selected");
        check(open.isArmed(), "an item on the path is armed");
        check(!save.isArmed(), "an item off the path is not armed");
        check(menuEvents[0] == 1, "menuSelected fires once");
        check(bar.getSelectionModel().getSelectedIndex() == 0, "the bar selects its open menu");

        manager.setSelectedPath(new MenuElement[] {bar, file, file.getPopupMenu(), save});
        check(!open.isArmed() && save.isArmed(), "moving along the popup moves the armed item");
        check(file.isSelected() && menuEvents[1] == 0, "the menu stays selected while its popup is on the path");
        check(manager.isComponentPartOfCurrentMenu(save), "an item under the bar is part of the current menu");
        check(!manager.isComponentPartOfCurrentMenu(new JMenuItem("elsewhere")), "a stray item is not");
        check(changes[0] == 2, "one change event per path change");

        manager.setSelectedPath(new MenuElement[] {bar, file});
        check(!save.isArmed() && file.isSelected(), "dropping the popup disarms its items only");

        manager.clearSelectedPath();
        check(manager.getSelectedPath().length == 0, "clearSelectedPath empties the path");
        check(!file.isSelected() && menuEvents[1] == 1, "clearing deselects the menu once");
        manager.removeChangeListener(counter);
    }

    private static void actions() {
        final int[] fired = new int[1];
        AbstractAction go = new AbstractAction("Go") {
            @Override public void actionPerformed(ActionEvent e) { fired[0]++; }
        };
        go.putValue(Action.SHORT_DESCRIPTION, "go somewhere");
        go.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_O);
        KeyStroke ctrlG = KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK);
        go.putValue(Action.ACCELERATOR_KEY, ctrlG);

        JMenuItem item = new JMenuItem(go);
        check("Go".equals(item.getText()), "text from Action.NAME");
        check("go somewhere".equals(item.getToolTipText()), "tooltip from SHORT_DESCRIPTION");
        check(item.getMnemonic() == KeyEvent.VK_O, "mnemonic from MNEMONIC_KEY");
        check(item.getDisplayedMnemonicIndex() == 1, "the mnemonic marks its first match in the text");
        check(ctrlG.equals(item.getAccelerator()), "accelerator from ACCELERATOR_KEY");

        go.putValue(Action.NAME, "Go now");
        check("Go now".equals(item.getText()), "a NAME edit reaches the item");
        go.setEnabled(false);
        check(!item.isEnabled(), "disabling the Action disables the item");
        go.setEnabled(true);
        check(item.isEnabled(), "enabling the Action enables the item");
        go.putValue(Action.SHORT_DESCRIPTION, "elsewhere");
        check("elsewhere".equals(item.getToolTipText()), "a SHORT_DESCRIPTION edit reaches the item");
        go.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, 3);
        check(item.getDisplayedMnemonicIndex() == 3, "DISPLAYED_MNEMONIC_INDEX_KEY moves the mark");
        item.doClick();
        check(fired[0] == 1, "clicking the item performs its Action");

        AbstractAction bold = new AbstractAction("Bold") {
            @Override public void actionPerformed(ActionEvent e) {}
        };
        bold.putValue(Action.SELECTED_KEY, Boolean.TRUE);
        JCheckBoxMenuItem toggle = new JCheckBoxMenuItem(bold);
        check(toggle.isSelected(), "SELECTED_KEY selects the check item");
        bold.putValue(Action.SELECTED_KEY, Boolean.FALSE);
        check(!toggle.isSelected(), "a SELECTED_KEY edit reaches the check item");
        toggle.setSelected(true);
        check(Boolean.TRUE.equals(bold.getValue(Action.SELECTED_KEY)), "selecting the check item writes SELECTED_KEY back");

        JButton button = new JButton(go);
        button.setHideActionText(true);
        check(button.getText() == null || button.getText().isEmpty(), "hideActionText hides the name");
        go.putValue(Action.NAME, "Again");
        check(button.getText() == null || button.getText().isEmpty(), "a hidden name stays hidden after an edit");
        check("Again".equals(item.getText()), "the item sharing the Action still follows it");
        button.setHideActionText(false);
        check("Again".equals(button.getText()), "showing the name again takes the current NAME");
    }

    private static void mnemonic() {
        Bar bar = new Bar();
        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        edit.add(new JMenuItem("Undo"));
        bar.add(edit);
        check(edit.getDisplayedMnemonicIndex() == 0, "the menu marks its mnemonic");

        KeyStroke altE = KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.ALT_DOWN_MASK);
        KeyEvent press = new KeyEvent(bar, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                InputEvent.ALT_DOWN_MASK, KeyEvent.VK_E, KeyEvent.CHAR_UNDEFINED);
        check(bar.press(altE, press), "alt+mnemonic is bound through the bar");
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        check(path.length >= 2 && path[0] == bar && path[1] == edit, "the mnemonic puts the menu on the selected path");
        check(edit.isSelected(), "the mnemonic selects the menu");
        check(bar.getSelectionModel().getSelectedIndex() == 0, "the bar selects the opened menu");

        KeyStroke altX = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK);
        KeyEvent other = new KeyEvent(bar, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                InputEvent.ALT_DOWN_MASK, KeyEvent.VK_X, KeyEvent.CHAR_UNDEFINED);
        check(!bar.press(altX, other), "an unbound stroke is not consumed");
        MenuSelectionManager.defaultManager().clearSelectedPath();
        check(!edit.isSelected(), "clearing the path closes the menu");
    }

    /**
     * Shadow only: the JDK's JMenu asks its headless toolkit for the screen size even when a
     * configuration supplies the screen, and that throws HeadlessException.
     */
    private static void placement() throws Exception {
        {
            JMenuBar bar = new JMenuBar();
            Menu menu = new Menu("View");
            bar.add(menu);
            bar.setBounds(0, 0, 400, 20);
            menu.setBounds(10, 0, 50, 20);
            menu.getPopupMenu().setPopupSize(100, 80);
            check(new Point(0, 20).equals(menu.origin()), "room below: the popup drops down, got " + menu.origin());
            bar.setLocation(0, 260);
            check(new Point(0, -80).equals(menu.origin()), "no room below: the popup flips above, got " + menu.origin());
            menu.setLocation(350, 0);
            check(new Point(-50, -80).equals(menu.origin()), "no room right: the popup aligns to the menu's right edge, got " + menu.origin());

            JPopupMenu context = new JPopupMenu();
            Menu sub = new Menu("More");
            context.add(sub);
            context.setBounds(300, 100, 100, 60);
            sub.setBounds(0, 0, 100, 20);
            sub.getPopupMenu().setPopupSize(100, 80);
            check(new Point(-100, 0).equals(sub.origin()), "no room right: a submenu opens to the left, got " + sub.origin());

            JPopupMenu popup = new JPopupMenu();
            popup.add(new JMenuItem("A"));
            popup.setPopupSize(100, 80);
            JPanel invoker = new Invoker();
            invoker.setBounds(350, 250, 50, 50);
            popup.show(invoker, 30, 30);
            check(popup.getX() == 300 && popup.getY() == 220,
                "show() moves the popup inside the screen, got " + popup.getX() + "," + popup.getY());
            check(popup.getInvoker() == invoker, "show() records the invoker");
            check(MenuSelectionManager.defaultManager().isComponentPartOfCurrentMenu(popup),
                "a shown context popup roots the selected path");
            popup.setVisible(false);
            check(MenuSelectionManager.defaultManager().getSelectedPath().length == 0,
                "hiding a context popup clears the selected path");
            JPopupMenu.class.getMethod("takeShownPopupMenu").invoke(null);
        }
    }
}
