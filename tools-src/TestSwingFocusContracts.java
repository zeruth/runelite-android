// host-contract: patched-args=shadow
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentInputMapUIResource;
import javax.swing.plaf.InputMapUIResource;
import javax.swing.plaf.UIResource;

/**
 * Swing focus and keyboard contracts: the key-binding walk (WHEN_FOCUSED, then
 * WHEN_ANCESTOR_OF_FOCUSED_COMPONENT up the tree, then WHEN_IN_FOCUSED_WINDOW through the
 * KeyboardManager), InputMap/ActionMap parents and keys, InputVerifier's verdicts, and the
 * LayoutFocusTraversalPolicy order of a two-column form.
 *
 * Headless, so there is no Window: the JInternalFrame is the one top ancestor the JDK's
 * KeyboardManager accepts without one, and it is shown through addNotify + setVisible, which a
 * lightweight root allows. A focus transfer itself needs a Window on the JDK, and on the host the
 * JDK's own Component and KeyboardFocusManager run in both modes, so the veto a tab-out goes
 * through is reached directly in the patched run (argument {@code shadow}): FocusVeto.accept is
 * the call the shadow KeyboardFocusManager makes before every focus change, and it runs the
 * shadow JComponent's InputVerifier controller.
 */
public final class TestSwingFocusContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(name + (same ? "" : " (expected " + expected + ", got " + actual + ")"), same);
    }

    /** A named, focusable leaf. */
    static final class Field extends JComponent {
        Field(String name) {
            setName(name);
            setFocusable(true);
        }

        @Override public String toString() { return getName(); }
    }

    public static void main(String[] args) {
        boolean shadow = args.length > 0 && "shadow".equals(args[0]);
        bindingWalk();
        menuBarBinding();
        mapParity();
        inputVerifier();
        if (shadow) {
            ShadowVeto.run();
        }
        layoutOrder();
        System.out.println("TestSwingFocusContracts: " + (total - failures) + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }

    // --- key-binding walk -----------------------------------------------------------------

    static final List<String> fired = new ArrayList<>();

    static AbstractAction record(final String name) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent e) { fired.add(name + ":" + e.getActionCommand()); }
        };
    }

    static void bind(JComponent c, int condition, KeyStroke stroke, String name) {
        c.getInputMap(condition).put(stroke, name);
        c.getActionMap().put(name, record(name));
    }

    static boolean press(Component source, int keyCode) {
        fired.clear();
        KeyEvent event = new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        return SwingUtilities.processKeyBindings(event);
    }

    static void bindingWalk() {
        JInternalFrame frame = new JInternalFrame();
        JPanel form = new JPanel(null);
        Field focused = new Field("focused");
        Field other = new Field("other");
        Field hidden = new Field("hidden");
        hidden.setVisible(false);
        form.add(focused);
        form.add(other);
        form.add(hidden);
        frame.getContentPane().add(form);

        KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        KeyStroke f2 = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        KeyStroke f3 = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);
        KeyStroke f4 = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0);
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        bind(focused, JComponent.WHEN_FOCUSED, f1, "focusedF1");
        bind(form, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, f1, "ancestorF1");
        bind(other, JComponent.WHEN_IN_FOCUSED_WINDOW, f1, "windowF1");
        bind(form, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, f2, "ancestorF2");
        bind(other, JComponent.WHEN_IN_FOCUSED_WINDOW, f2, "windowF2");
        bind(other, JComponent.WHEN_IN_FOCUSED_WINDOW, f3, "windowF3");
        bind(hidden, JComponent.WHEN_IN_FOCUSED_WINDOW, f4, "hiddenF4");
        final List<String> commands = new ArrayList<>();
        other.registerKeyboardAction(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { commands.add(String.valueOf(e.getActionCommand())); }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        frame.addNotify();
        frame.setVisible(true);
        check("internal frame tree is showing", other.isShowing() && focused.isShowing());

        check("F1: a binding was processed", press(focused, KeyEvent.VK_F1));
        checkEquals("F1: WHEN_FOCUSED wins over ancestor and window bindings", Arrays.asList("focusedF1:null"), fired);
        check("F2: a binding was processed", press(focused, KeyEvent.VK_F2));
        checkEquals("F2: WHEN_ANCESTOR_OF_FOCUSED_COMPONENT wins over the window binding", Arrays.asList("ancestorF2:null"), fired);
        check("F3: WHEN_IN_FOCUSED_WINDOW binding fires from a non-focused child", press(focused, KeyEvent.VK_F3));
        checkEquals("F3: the sibling's window binding ran", Arrays.asList("windowF3:null"), fired);
        check("F4: a hidden component's window binding does not fire", !press(focused, KeyEvent.VK_F4) && fired.isEmpty());
        commands.clear();
        check("F5: registerKeyboardAction window binding fires", press(focused, KeyEvent.VK_F5));
        checkEquals("F5: registerKeyboardAction without a command passes a null command", Arrays.asList("null"), commands);
        checkEquals("condition of the registered stroke", JComponent.WHEN_IN_FOCUSED_WINDOW, other.getConditionForKeyStroke(f5));
        check("getActionForKeyStroke unwraps the registered listener",
            other.getActionForKeyStroke(f5) != null && !(other.getActionForKeyStroke(f5) instanceof javax.swing.Action));
        other.unregisterKeyboardAction(f5);
        commands.clear();
        check("F5: unregisterKeyboardAction removes the binding", !press(focused, KeyEvent.VK_F5) && commands.isEmpty());

        other.setEnabled(false);
        check("F3: a disabled component's window binding does not fire", !press(focused, KeyEvent.VK_F3));
        other.setEnabled(true);
        other.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(f3);
        check("F3: removing the stroke from the ComponentInputMap unregisters it", !press(focused, KeyEvent.VK_F3));
    }

    // --- JMenuBar: the KeyboardManager's last stop -----------------------------------------

    static void menuBarBinding() {
        JInternalFrame frame = new JInternalFrame();
        Field focused = new Field("menuFocused");
        frame.getContentPane().add(focused);
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        bar.add(menu);
        frame.setJMenuBar(bar);
        // How BasicMenuItemUI installs an accelerator: a window binding on an item whose popup is
        // not in the tree, so only the menu bar's walk down its menus can reach it.
        KeyStroke f6 = KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0);
        bind(item, JComponent.WHEN_IN_FOCUSED_WINDOW, f6, "itemF6");

        frame.addNotify();
        frame.setVisible(true);
        check("menu bar: shown with the frame", bar.isShowing() && focused.isShowing());
        check("menu bar: the popup's item is not showing", !item.isShowing());
        check("menu bar: F6 is processed", press(focused, KeyEvent.VK_F6));
        checkEquals("menu bar: a hidden popup item's window binding fires through the bar",
            Arrays.asList("itemF6:null"), fired);
        item.setEnabled(false);
        check("menu bar: a disabled item's binding does not fire", !press(focused, KeyEvent.VK_F6) && fired.isEmpty());
        item.setEnabled(true);
        bar.setEnabled(false);
        check("menu bar: a disabled bar is skipped", !press(focused, KeyEvent.VK_F6) && fired.isEmpty());
        bar.setEnabled(true);
        frame.setJMenuBar(null);
        check("menu bar: a removed bar no longer dispatches", !press(focused, KeyEvent.VK_F6) && fired.isEmpty());
    }

    // --- InputMap / ActionMap parity -----------------------------------------------------

    static void mapParity() {
        KeyStroke a = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
        KeyStroke b = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0);
        InputMap parent = new InputMapUIResource();
        parent.put(a, "parentA");
        parent.put(b, "parentB");
        InputMap child = new InputMap();
        child.setParent(parent);
        child.put(a, "childA");
        checkEquals("InputMap: child binding shadows the parent", "childA", child.get(a));
        checkEquals("InputMap: lookup falls through to the parent", "parentB", child.get(b));
        checkEquals("InputMap: size counts local bindings only", 1, child.size());
        checkEquals("InputMap: keys() are local", new HashSet<>(Arrays.asList(a)), new HashSet<>(Arrays.asList(child.keys())));
        checkEquals("InputMap: allKeys() merge the parents", new HashSet<>(Arrays.asList(a, b)), new HashSet<>(Arrays.asList(child.allKeys())));
        child.put(a, null);
        checkEquals("InputMap: put(key, null) removes the local binding", "parentA", child.get(a));
        check("InputMap: keys() after removing the last binding is empty, not null",
            child.keys() != null && child.keys().length == 0);
        check("InputMap: keys() of a map never bound is null", new InputMap().keys() == null);
        check("InputMapUIResource is a UIResource", parent instanceof UIResource);

        ActionMap actionParent = new ActionMapUIResource();
        actionParent.put("x", record("x"));
        ActionMap actions = new ActionMap();
        actions.setParent(actionParent);
        check("ActionMap: lookup falls through to the parent", actions.get("x") != null);
        check("ActionMap: keys() empty is null, allKeys() has the parent's", actions.keys() == null
            && Arrays.asList(actions.allKeys()).contains("x"));
        check("ActionMapUIResource is a UIResource", actionParent instanceof UIResource);

        JComponent owner = new Field("owner");
        JComponent stranger = new Field("stranger");
        ComponentInputMap window = new ComponentInputMap(owner);
        boolean refused = false;
        try {
            window.setParent(new InputMap());
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("ComponentInputMap: a plain InputMap parent is refused", refused);
        refused = false;
        try {
            window.setParent(new ComponentInputMapUIResource(stranger));
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("ComponentInputMap: a parent for another component is refused", refused);
        window.setParent(new ComponentInputMapUIResource(owner));
        check("ComponentInputMap: a parent for the same component is kept", window.getParent() instanceof ComponentInputMapUIResource);
        refused = false;
        try {
            owner.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, new InputMap());
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("setInputMap(WHEN_IN_FOCUSED_WINDOW) requires a ComponentInputMap", refused);
        owner.setInputMap(JComponent.WHEN_FOCUSED, null);
        check("setInputMap(WHEN_FOCUSED, null) sticks", owner.getInputMap(JComponent.WHEN_FOCUSED) == null);
        check("getInputMap(WHEN_IN_FOCUSED_WINDOW) creates a ComponentInputMap",
            owner.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW) instanceof ComponentInputMap);
        refused = false;
        try {
            owner.getInputMap(7);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("getInputMap(bad condition) throws", refused);
    }

    // --- InputVerifier ---------------------------------------------------------------------

    static void inputVerifier() {
        final JComponent field = new Field("field");
        JComponent next = new Field("next");
        final boolean[] valid = {false};
        InputVerifier verifier = new InputVerifier() {
            @Override public boolean verify(JComponent input) { return valid[0]; }
        };
        final List<String> changes = new ArrayList<>();
        field.addPropertyChangeListener(e -> changes.add(e.getPropertyName()));
        field.setInputVerifier(verifier);
        check("setInputVerifier stores the verifier", field.getInputVerifier() == verifier);
        check("setInputVerifier fires inputVerifier", changes.contains("inputVerifier"));
        check("an invalid field does not yield focus", !verifier.shouldYieldFocus(field));
        check("an invalid field blocks tab-out to the next field", !verifier.shouldYieldFocus(field, next));
        valid[0] = true;
        check("a valid field yields focus to the next field", verifier.shouldYieldFocus(field, next));
        check("verifyTarget defaults to true", verifier.verifyTarget(next));
        check("verifyInputWhenFocusTarget defaults to true", next.getVerifyInputWhenFocusTarget());
        next.setVerifyInputWhenFocusTarget(false);
        check("setVerifyInputWhenFocusTarget(false) sticks", !next.getVerifyInputWhenFocusTarget());
    }

    // --- LayoutFocusTraversalPolicy -------------------------------------------------------

    static void layoutOrder() {
        JPanel form = new JPanel(null);
        form.setFocusCycleRoot(true);
        Field[] left = new Field[3];
        Field[] right = new Field[3];
        // Added column by column; the right column sits a few pixels lower, inside the row tolerance.
        for (int row = 0; row < 3; row++) {
            left[row] = new Field("L" + row);
            left[row].setBounds(0, row * 30, 90, 20);
            form.add(left[row]);
        }
        for (int row = 0; row < 3; row++) {
            right[row] = new Field("R" + row);
            right[row].setBounds(100, row * 30 + 4, 90, 20);
            form.add(right[row]);
        }
        form.addNotify();
        LayoutFocusTraversalPolicy policy = new LayoutFocusTraversalPolicy();
        List<Component> forward = new ArrayList<>();
        Component c = policy.getFirstComponent(form);
        forward.add(c);
        for (int i = 0; i < 6 && c != null; i++) {
            c = policy.getComponentAfter(form, c);
            forward.add(c);
        }
        checkEquals("layout policy: row by row, left to right, wrapping",
            Arrays.<Component>asList(left[0], right[0], left[1], right[1], left[2], right[2], left[0]), forward);
        List<Component> backward = new ArrayList<>();
        c = policy.getLastComponent(form);
        backward.add(c);
        for (int i = 0; i < 6 && c != null; i++) {
            c = policy.getComponentBefore(form, c);
            backward.add(c);
        }
        checkEquals("layout policy: backward is the reverse",
            Arrays.<Component>asList(right[2], left[2], right[1], left[1], right[0], left[0], right[2]), backward);
        checkEquals("layout policy: default component is the first", left[0], policy.getDefaultComponent(form));
        right[0].setEnabled(false);
        checkEquals("layout policy: a disabled field is skipped", left[1], policy.getComponentAfter(form, left[0]));
    }

    // --- the focus veto a tab-out goes through (patched run only) -----------------------------

    /** Its own class, so the plain-JDK run never resolves net.runelite.awt.impl. */
    static final class ShadowVeto {
        static boolean accept(Component from, Component to, FocusEvent.Cause cause) {
            return net.runelite.awt.impl.FocusVeto.accept(from, to, false, cause);
        }

        static void run() {
            new JPanel(); // JComponent's static initializer installs the controller
            check("shadow: JComponent installed its focus veto", net.runelite.awt.impl.FocusVeto.installed() != null);

            final JComponent field = new Field("vetoField");
            final JComponent next = new Field("vetoNext");
            final JComponent third = new Field("vetoThird");
            final boolean[] valid = {false};
            final List<Boolean> nested = new ArrayList<>();
            final int[] calls = {0};
            field.setInputVerifier(new InputVerifier() {
                @Override public boolean verify(JComponent input) {
                    calls[0]++;
                    // A verifier that moves focus itself must not be asked again for that move.
                    nested.add(accept(field, third, FocusEvent.Cause.TRAVERSAL_FORWARD));
                    return valid[0];
                }
            });
            FocusEvent.Cause tab = FocusEvent.Cause.TRAVERSAL_FORWARD;
            check("shadow: an invalid field blocks tab-out", !accept(field, next, tab));
            checkEquals("shadow: the verifier ran once for the tab-out", 1, calls[0]);
            checkEquals("shadow: a focus request from inside the verifier is let through", Arrays.asList(Boolean.TRUE), nested);
            check("shadow: an invalid field blocks a backward tab too", !accept(field, next, FocusEvent.Cause.TRAVERSAL_BACKWARD));
            calls[0] = 0;
            check("shadow: a window activation skips the verifier", accept(field, next, FocusEvent.Cause.ACTIVATION) && calls[0] == 0);
            check("shadow: the owner asking for itself skips the verifier", accept(field, field, tab) && calls[0] == 0);
            check("shadow: no current owner skips the verifier", accept(null, next, tab) && calls[0] == 0);
            next.setVerifyInputWhenFocusTarget(false);
            check("shadow: a target with verifyInputWhenFocusTarget=false is let through",
                accept(field, next, tab) && calls[0] == 0);
            next.setVerifyInputWhenFocusTarget(true);
            check("shadow: a non-Swing target is let through",
                accept(field, new java.awt.Component() { }, tab) && calls[0] == 0);
            valid[0] = true;
            check("shadow: a valid field lets focus go", accept(field, next, tab) && calls[0] == 1);
            field.setInputVerifier(null);
            valid[0] = false;
            check("shadow: no verifier, no veto", accept(field, next, tab));
        }
    }
}
