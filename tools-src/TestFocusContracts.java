// host-contract
import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Focus contracts: tab order through a form under both AWT policies, the default traversal keys
 * and how processKeyEvent consumes them with and without focusTraversalKeys, the dispatcher and
 * post-processor chain order, vetoable/property changes of the focus owner, and focus events.
 *
 * Headless, so nothing here moves native focus. On the host the JDK's own Component, Container
 * and KeyboardFocusManager run in both modes (the shadow ones cannot be patched over the JDK);
 * the policies, java.beans' vetoable support and AWTKeyStroke are what the patched run swaps in.
 * Focus changes and their event order on the shadow are checked on the device.
 */
public final class TestFocusContracts {
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

    /** A lightweight leaf, named for readable failures. */
    static final class Leaf extends Component {
        Leaf(String name) { setName(name); }
        void deliverFocus(FocusEvent e) { processFocusEvent(e); }
        @Override public String toString() { return getName(); }
    }

    static final class Box extends Container {
        Box(String name) { setName(name); }
        @Override public String toString() { return getName(); }
    }

    static Leaf leaf(String name, boolean explicitFocusable) {
        Leaf leaf = new Leaf(name);
        if (explicitFocusable) leaf.setFocusable(true);
        return leaf;
    }

    public static void main(String[] args) {
        tabOrder();
        traversalKeys();
        dispatcherChain();
        focusOwnerChanges();
        focusEvents();
        System.out.println("TestFocusContracts: " + (total - failures) + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }

    // --- tab order through a form ------------------------------------------------------

    static Box root;
    static Leaf a, hidden, n1, n2, n3, off, b;
    static Box nested;

    static void buildForm() {
        root = new Box("root");
        root.setFocusCycleRoot(true);
        root.setFocusable(false);
        a = leaf("a", true);
        hidden = leaf("hidden", true);
        hidden.setVisible(false);
        nested = new Box("nested");
        nested.setFocusable(true);
        n1 = leaf("n1", true);
        n2 = leaf("n2", true);
        n2.setEnabled(false);
        n3 = leaf("n3", false); // never set: DefaultFocusTraversalPolicy asks the (lightweight) peer
        off = leaf("off", true);
        off.setFocusable(false);
        b = leaf("b", true);
        nested.add(n1);
        nested.add(n2);
        nested.add(n3);
        root.add(a);
        root.add(hidden);
        root.add(nested);
        root.add(off);
        root.add(b);
        root.addNotify(); // displayable: the policies only walk displayable trees
    }

    static List<Component> forward(FocusTraversalPolicy policy, Component from, int steps) {
        List<Component> out = new ArrayList<>();
        Component c = from;
        for (int i = 0; i < steps && c != null; i++) {
            c = policy.getComponentAfter(root, c);
            out.add(c);
        }
        return out;
    }

    static List<Component> backward(FocusTraversalPolicy policy, Component from, int steps) {
        List<Component> out = new ArrayList<>();
        Component c = from;
        for (int i = 0; i < steps && c != null; i++) {
            c = policy.getComponentBefore(root, c);
            out.add(c);
        }
        return out;
    }

    static void tabOrder() {
        buildForm();
        check("form root is displayable after addNotify", root.isDisplayable() && n1.isDisplayable());
        check("n1's focus cycle root is the form root", n1.getFocusCycleRootAncestor() == root && n1.isFocusCycleRoot(root));

        DefaultFocusTraversalPolicy def = new DefaultFocusTraversalPolicy();
        checkEquals("default policy: first", a, def.getFirstComponent(root));
        checkEquals("default policy: last", b, def.getLastComponent(root));
        checkEquals("default policy: default", a, def.getDefaultComponent(root));
        checkEquals("default policy: forward from a (skips hidden, disabled, unfocusable, unset lightweight; wraps)",
            java.util.Arrays.<Component>asList(nested, n1, b, a), forward(def, a, 4));
        checkEquals("default policy: backward from a",
            java.util.Arrays.<Component>asList(b, n1, nested, a), backward(def, a, 4));

        ContainerOrderFocusTraversalPolicy order = new ContainerOrderFocusTraversalPolicy();
        checkEquals("container order: forward from a (an unset lightweight is focusable)",
            java.util.Arrays.<Component>asList(nested, n1, n3, b, a), forward(order, a, 5));
        checkEquals("container order: backward from b",
            java.util.Arrays.<Component>asList(n3, n1, nested, a, b), backward(order, b, 5));
        check("container order: implicit down-cycle traversal defaults on", order.getImplicitDownCycleTraversal());

        root.setFocusTraversalPolicy(def);
        check("root uses the policy it was given", root.getFocusTraversalPolicy() == def && root.isFocusTraversalPolicySet());
        check("a non-root, non-provider container has no policy", nested.getFocusTraversalPolicy() == null);
        nested.setFocusTraversalPolicyProvider(true);
        check("a provider inherits its cycle root's policy", nested.getFocusTraversalPolicy() == def);
        nested.setFocusTraversalPolicyProvider(false);

        boolean thrown = false;
        try {
            def.getComponentAfter(nested, n1);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check("getComponentAfter rejects a container that is neither root nor provider", thrown);
    }

    // --- traversal keys -----------------------------------------------------------------

    static AWTKeyStroke stroke(int code, int modifiers) { return AWTKeyStroke.getAWTKeyStroke(code, modifiers); }

    static void traversalKeys() {
        KeyboardFocusManager kfm = new DefaultKeyboardFocusManager();
        Set<AWTKeyStroke> fwd = new HashSet<>(Collections.<AWTKeyStroke>emptySet());
        fwd.add(stroke(KeyEvent.VK_TAB, 0));
        fwd.add(stroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
        checkEquals("default forward keys: TAB, CTRL+TAB", fwd,
            new HashSet<>(kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)));
        Set<AWTKeyStroke> back = new HashSet<>();
        back.add(stroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
        back.add(stroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
        checkEquals("default backward keys: SHIFT+TAB, CTRL+SHIFT+TAB", back,
            new HashSet<>(kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)));
        check("default up/down-cycle keys are empty",
            kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS).isEmpty()
                && kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS).isEmpty());
        boolean typed = false;
        try {
            kfm.setDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Collections.singleton(AWTKeyStroke.getAWTKeyStroke('x')));
        } catch (IllegalArgumentException expected) {
            typed = true;
        }
        check("a KEY_TYPED stroke cannot be a traversal key", typed);
        boolean shared = false;
        try {
            kfm.setDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Collections.singleton(stroke(KeyEvent.VK_TAB, 0)));
        } catch (IllegalArgumentException expected) {
            shared = true;
        }
        check("a stroke cannot serve two traversal operations", shared);

        Leaf lone = new Leaf("lone");
        checkEquals("a parentless component inherits the manager's forward keys",
            KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS),
            lone.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        Box parent = new Box("parent");
        Leaf child = new Leaf("child");
        parent.add(child);
        List<String> props = new ArrayList<>();
        parent.addPropertyChangeListener(e -> props.add(e.getPropertyName()));
        parent.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.singleton(stroke(KeyEvent.VK_ENTER, 0)));
        check("a child inherits its parent's keys", child.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            .equals(Collections.singleton(stroke(KeyEvent.VK_ENTER, 0))));
        check("inherited keys are not 'set' on the child", !child.areFocusTraversalKeysSet(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            && parent.areFocusTraversalKeysSet(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        parent.setFocusTraversalKeysEnabled(false);
        checkEquals("traversal key setters fire their properties",
            java.util.Arrays.asList("forwardFocusTraversalKeys", "focusTraversalKeysEnabled"), props);
        boolean down = false;
        try {
            child.setFocusTraversalKeys(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS, Collections.<AWTKeyStroke>emptySet());
        } catch (IllegalArgumentException expected) {
            down = true;
        }
        check("a plain Component has no down-cycle keys", down);
        parent.setFocusTraversalKeys(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS, Collections.<AWTKeyStroke>emptySet());
        check("a Container has down-cycle keys", parent.areFocusTraversalKeysSet(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS));

        // processKeyEvent: TAB is consumed with traversal keys on, along with the KEY_TYPED and
        // KEY_RELEASED that pair with it; with them off the events go to the listeners untouched.
        DefaultKeyboardFocusManager dkfm = new DefaultKeyboardFocusManager();
        Leaf field = new Leaf("field");
        KeyEvent pressed = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_TAB, '\t');
        dkfm.processKeyEvent(field, pressed);
        KeyEvent typedTab = new KeyEvent(field, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '\t');
        dkfm.processKeyEvent(field, typedTab);
        KeyEvent released = new KeyEvent(field, KeyEvent.KEY_RELEASED, 0L, 0, KeyEvent.VK_TAB, '\t');
        dkfm.processKeyEvent(field, released);
        check("traversal keys on: TAB pressed/typed/released are consumed",
            pressed.isConsumed() && typedTab.isConsumed() && released.isConsumed());
        KeyEvent letter = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a');
        dkfm.processKeyEvent(field, letter);
        check("traversal keys on: a non-traversal key is left alone", !letter.isConsumed());
        KeyEvent typedA = new KeyEvent(field, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, 'a');
        dkfm.processKeyEvent(field, typedA);
        check("the KEY_TYPED after a non-traversal key is left alone", !typedA.isConsumed());

        field.setFocusTraversalKeysEnabled(false);
        KeyEvent pressedOff = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_TAB, '\t');
        dkfm.processKeyEvent(field, pressedOff);
        KeyEvent typedOff = new KeyEvent(field, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '\t');
        dkfm.processKeyEvent(field, typedOff);
        check("traversal keys off: TAB pressed/typed reach the component", !pressedOff.isConsumed() && !typedOff.isConsumed());
        field.setFocusTraversalKeysEnabled(true);
        KeyEvent shiftTab = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0L, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB, '\t');
        dkfm.processKeyEvent(field, shiftTab);
        check("SHIFT+TAB is a backward traversal key", shiftTab.isConsumed());
    }

    // --- dispatcher and post-processor chains ------------------------------------------

    static KeyEventDispatcher dispatcher(List<String> log, String name, boolean result) {
        return e -> { log.add(name); return result; };
    }

    static KeyEventPostProcessor postProcessor(List<String> log, String name, boolean result) {
        return e -> { log.add(name); return result; };
    }

    static void dispatcherChain() {
        // A parentless, undisplayable source: dispatchKeyEvent neither redispatches nor finds a peer.
        Leaf source = new Leaf("source");
        List<String> log = new ArrayList<>();
        DefaultKeyboardFocusManager dkfm = new DefaultKeyboardFocusManager();
        KeyEventDispatcher d1 = dispatcher(log, "d1", false);
        dkfm.addKeyEventDispatcher(d1);
        dkfm.addKeyEventDispatcher(dispatcher(log, "d2", false));
        dkfm.addKeyEventDispatcher(d1);
        dkfm.addKeyEventPostProcessor(postProcessor(log, "p1", false));
        dkfm.addKeyEventPostProcessor(postProcessor(log, "p2", true));
        dkfm.addKeyEventPostProcessor(postProcessor(log, "p3", false));
        boolean dispatched = dkfm.dispatchEvent(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a'));
        checkEquals("dispatchers in add order (duplicates run twice), then post-processors up to the first true",
            java.util.Arrays.asList("d1", "d2", "d1", "p1", "p2"), log);
        check("a fully dispatched KeyEvent reports true", dispatched);

        log.clear();
        dkfm.removeKeyEventDispatcher(d1);
        dkfm.dispatchEvent(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0L, 0, KeyEvent.VK_A, 'a'));
        checkEquals("removing a dispatcher drops one registration", java.util.Arrays.asList("d2", "d1", "p1", "p2"), log);

        log.clear();
        DefaultKeyboardFocusManager stopping = new DefaultKeyboardFocusManager();
        stopping.addKeyEventDispatcher(dispatcher(log, "d1", false));
        stopping.addKeyEventDispatcher(dispatcher(log, "stop", true));
        stopping.addKeyEventDispatcher(dispatcher(log, "d3", false));
        stopping.addKeyEventPostProcessor(postProcessor(log, "p1", false));
        boolean stopped = stopping.dispatchEvent(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_B, 'b'));
        checkEquals("a dispatcher returning true ends the chain and skips post-processing",
            java.util.Arrays.asList("d1", "stop"), log);
        check("a dispatcher returning true reports the event dispatched", stopped);
    }

    // --- focus owner property and vetoable changes --------------------------------------

    static final class ProbeManager extends DefaultKeyboardFocusManager {
        void owner(Component c) { setGlobalFocusOwner(c); }
    }

    static void focusOwnerChanges() {
        ProbeManager kfm = new ProbeManager();
        Leaf allowed = leaf("allowed", true);
        Leaf blocked = leaf("blocked", true);
        List<String> log = new ArrayList<>();
        kfm.addVetoableChangeListener("focusOwner", e -> log.add("veto? " + e.getOldValue() + "->" + e.getNewValue()));
        VetoableChangeListener vetoer = (PropertyChangeEvent e) -> {
            if (e.getNewValue() == blocked) throw new PropertyVetoException("no", e);
        };
        kfm.addVetoableChangeListener("focusOwner", vetoer);
        kfm.addPropertyChangeListener("focusOwner", e -> log.add("changed " + e.getOldValue() + "->" + e.getNewValue()));
        check("vetoable listeners are listed by property", kfm.getVetoableChangeListeners("focusOwner").length == 2
            && kfm.getVetoableChangeListeners().length == 2);

        kfm.owner(blocked);
        checkEquals("a veto reverts the listeners already asked and leaves the owner",
            java.util.Arrays.asList("veto? null->blocked", "veto? blocked->null"), log);
        check("the vetoed owner was not set", kfm.getFocusOwner() != blocked);

        log.clear();
        kfm.owner(allowed);
        checkEquals("an accepted change is vetoable first, then fired",
            java.util.Arrays.asList("veto? null->allowed", "changed null->allowed"), log);
        check("the accepted owner is the focus owner", kfm.getFocusOwner() == allowed);
        log.clear();
        kfm.removeVetoableChangeListener("focusOwner", vetoer);
        kfm.owner(null);
        checkEquals("clearing the owner fires too", java.util.Arrays.asList("veto? allowed->null", "changed allowed->null"), log);

        Leaf unfocusable = leaf("unfocusable", true);
        unfocusable.setFocusable(false);
        log.clear();
        kfm.owner(unfocusable);
        check("an unfocusable component never becomes the owner", log.isEmpty() && kfm.getFocusOwner() == null);
        check("the default policy is a DefaultFocusTraversalPolicy",
            kfm.getDefaultFocusTraversalPolicy() instanceof DefaultFocusTraversalPolicy);
    }

    // --- focus events -------------------------------------------------------------------

    static void focusEvents() {
        Leaf from = new Leaf("from");
        Leaf to = new Leaf("to");
        List<String> log = new ArrayList<>();
        FocusListener listener = new FocusListener() {
            public void focusGained(FocusEvent e) {
                log.add("gained " + e.getComponent() + " from " + e.getOppositeComponent() + " " + e.getCause() + (e.isTemporary() ? " temporary" : ""));
            }
            public void focusLost(FocusEvent e) {
                log.add("lost " + e.getComponent() + " to " + e.getOppositeComponent() + " " + e.getCause() + (e.isTemporary() ? " temporary" : ""));
            }
        };
        from.addFocusListener(listener);
        to.addFocusListener(listener);
        from.deliverFocus(new FocusEvent(from, FocusEvent.FOCUS_LOST, false, to, FocusEvent.Cause.TRAVERSAL_FORWARD));
        to.deliverFocus(new FocusEvent(to, FocusEvent.FOCUS_GAINED, false, from, FocusEvent.Cause.TRAVERSAL_FORWARD));
        checkEquals("a focusLost/focusGained pair carries the opposite component and the cause",
            java.util.Arrays.asList("lost from to to TRAVERSAL_FORWARD", "gained to from from TRAVERSAL_FORWARD"), log);
        FocusEvent plain = new FocusEvent(to, FocusEvent.FOCUS_GAINED, true);
        check("the 3-argument event is temporary with cause UNKNOWN",
            plain.isTemporary() && plain.getCause() == FocusEvent.Cause.UNKNOWN && plain.getOppositeComponent() == null);
        boolean nullCause = false;
        try {
            new FocusEvent(to, FocusEvent.FOCUS_GAINED, false, null, null);
        } catch (IllegalArgumentException expected) {
            nullCause = true;
        }
        check("a null cause is rejected", nullCause);
    }
}
