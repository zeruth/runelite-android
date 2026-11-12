// host-contract: runs=patched args=build/compat-tests/awt/compile/classes/java.desktop
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * RepaintManager as the Android hosts consume it: repaints of descendants coalesce into one
 * region on the root, in root coordinates, and validateInvalidComponents validates the nearest
 * validate root only.
 *
 * <p>The shadow RepaintManager cannot be patched over the JDK's: the JDK's JComponent is always
 * loaded on the host (the shadow one needs the Android backend) and calls RepaintManager
 * internals typed with sun.awt.AppContext. So the contract loads the shadow class itself,
 * child-first, from the runner's compile output (the argument), over the JDK's component
 * classes, and drives it the way the shadow JComponent.repaint/revalidate do.
 */
public final class TestRepaintContracts {
    /** Nesting of validate() calls: only the outermost call is the manager's. */
    static int depth;

    /**
     * Hidden without setVisible(false): the JDK's Container.hide() goes through the keyboard
     * focus manager, which the patched run cannot initialise headless.
     */
    static class Hideable extends JPanel {
        boolean hidden;
        Hideable() { super(null); }
        @Override public boolean isVisible() { return !hidden && super.isVisible(); }
    }

    /** Stands in for a tree the Android host mounted with setHostShowing(true). */
    static final class Root extends Hideable {
        int validated;
        @Override public boolean isShowing() { return isVisible(); }
        @Override public void validate() {
            if (depth == 0) validated++;
            depth++;
            try { super.validate(); } finally { depth--; }
        }
    }

    static final class Node extends Hideable {
        final boolean validateRoot;
        int validated;
        Node(boolean validateRoot) { this.validateRoot = validateRoot; }
        @Override public boolean isValidateRoot() { return validateRoot; }
        @Override public void validate() {
            if (depth == 0) validated++;
            depth++;
            try { super.validate(); } finally { depth--; }
        }
    }

    /** Child-first for the shadow RepaintManager and its nested classes only. */
    static final class ShadowLoader extends URLClassLoader {
        ShadowLoader(File classes) throws Exception {
            super(new URL[] {classes.toURI().toURL()}, TestRepaintContracts.class.getClassLoader());
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals("javax.swing.RepaintManager") && !name.startsWith("javax.swing.RepaintManager$")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    static Class<?> manager;

    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = manager.getMethod(name, types);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw (Error) e.getCause();
        }
    }

    static final Class<?>[] COMPONENT = {Component.class};
    static final Class<?>[] JCOMPONENT = {JComponent.class};
    static final Class<?>[] REGION = {JComponent.class, int.class, int.class, int.class, int.class};

    static Object current(Component c) throws Exception { return call(null, "currentManager", COMPONENT, c); }
    static Rectangle take(Object m, Component root) throws Exception { return (Rectangle) call(m, "hostTakeDirtyRegion", COMPONENT, root); }
    static void dirty(Object m, JComponent c, int x, int y, int w, int h) throws Exception { call(m, "addDirtyRegion", REGION, c, x, y, w, h); }
    static Rectangle region(Object m, JComponent c) throws Exception { return (Rectangle) call(m, "getDirtyRegion", JCOMPONENT, c); }
    static void invalid(Object m, JComponent c) throws Exception { call(m, "addInvalidComponent", JCOMPONENT, c); }
    static void validateAll(Object m) throws Exception { call(m, "validateInvalidComponents", new Class<?>[0]); }

    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        File classes = new File(args[0]);
        check(new File(classes, "javax/swing/RepaintManager.class").isFile(), "shadow RepaintManager not compiled under " + classes);
        manager = new ShadowLoader(classes).loadClass("javax.swing.RepaintManager");
        check(manager.getMethod("hostTakeDirtyRegion", Component.class) != null, "shadow class loaded");
        final Throwable[] failure = new Throwable[1];
        // On the EDT, so the manager's queued processing cannot interleave with the checks.
        EventQueue.invokeAndWait(new Runnable() {
            public void run() {
                try { contracts(); } catch (Throwable t) { failure[0] = t; }
            }
        });
        if (failure[0] instanceof Error) throw (Error) failure[0];
        if (failure[0] != null) throw new AssertionError(failure[0]);
        System.out.println("PASS: child repaints coalesce on the root, clipped and hidden-dropped; "
            + "validateInvalidComponents validates the nearest validate root only");
    }

    static void contracts() throws Exception {
        call(null, "setCurrentManager", new Class<?>[] {manager}, (Object) null);
        Root root = new Root();
        Node mid = new Node(true);
        Node a = new Node(false);
        Node b = new Node(false);
        root.setBounds(0, 0, 200, 200);
        mid.setBounds(10, 20, 100, 100);
        a.setBounds(5, 5, 30, 30);
        b.setBounds(50, 60, 20, 20);
        root.add(mid);
        mid.add(a);
        mid.add(b);
        Object m = current(a);
        check(m == current(root), "one manager for the process");
        check(take(m, root) == null, "nothing dirty yet");

        dirty(m, a, 0, 0, 10, 10);
        dirty(m, b, 0, 0, 20, 20);
        check(region(m, a).equals(new Rectangle(0, 0, 10, 10)), "child dirty region in its own coordinates");
        dirty(m, a, 2, 2, 4, 4);
        check(region(m, a).equals(new Rectangle(0, 0, 10, 10)), "repeat repaint unions into the child's region");
        Rectangle r = take(m, root);
        check(new Rectangle(15, 25, 65, 75).equals(r), "union of child repaints on the root: " + r);
        check(take(m, root) == null, "taking clears the root region");
        check(region(m, a).isEmpty(), "taking consumes the per-component regions");

        dirty(m, a, 20, 20, 50, 50);
        r = take(m, root);
        check(new Rectangle(35, 45, 10, 10).equals(r), "region clipped to the child: " + r);

        call(m, "markCompletelyDirty", JCOMPONENT, a);
        check((Boolean) call(m, "isCompletelyDirty", JCOMPONENT, a), "markCompletelyDirty");
        r = take(m, root);
        check(new Rectangle(15, 25, 30, 30).equals(r), "completely dirty child clipped to its bounds: " + r);
        call(m, "markCompletelyDirty", JCOMPONENT, a);
        call(m, "markCompletelyClean", JCOMPONENT, a);
        check(!(Boolean) call(m, "isCompletelyDirty", JCOMPONENT, a) && take(m, root) == null,
            "markCompletelyClean drops the request");

        mid.hidden = true;
        dirty(m, a, 0, 0, 10, 10);
        check(take(m, root) == null, "repaint under a hidden ancestor is dropped");
        mid.hidden = false;

        Root offscreen = new Root();
        Node lost = new Node(false);
        offscreen.setBounds(0, 0, 50, 50);
        lost.setBounds(0, 0, 10, 10);
        offscreen.add(lost);
        offscreen.hidden = true;
        dirty(m, lost, 0, 0, 10, 10);
        check(take(m, offscreen) == null, "a tree no host shows records nothing");

        root.validated = mid.validated = 0;
        a.invalidate();
        invalid(m, a);
        invalid(m, b);
        validateAll(m);
        check(mid.validated == 1, "nearest validate root validated once: " + mid.validated);
        check(root.validated == 0, "validate root's ancestors not validated: " + root.validated);
        validateAll(m);
        check(mid.validated == 1, "validateInvalidComponents empties the list");

        Node direct = new Node(false);
        direct.setBounds(120, 120, 10, 10);
        root.add(direct);
        root.validated = mid.validated = 0;
        invalid(m, direct);
        validateAll(m);
        check(root.validated == 1 && mid.validated == 0, "top of the host tree is the validate root when nothing below claims it");

        invalid(m, mid);
        call(m, "removeInvalidComponent", JCOMPONENT, mid);
        mid.validated = 0;
        validateAll(m);
        check(mid.validated == 0, "removeInvalidComponent");

        offscreen.validated = 0;
        invalid(m, lost);
        validateAll(m);
        check(offscreen.validated == 0, "revalidate in a tree no host shows queues nothing");

        Object custom = manager.getConstructor().newInstance();
        call(null, "setCurrentManager", new Class<?>[] {manager}, custom);
        check(current(a) == custom, "setCurrentManager");
        call(null, "setCurrentManager", new Class<?>[] {manager}, (Object) null);
        check(current(a) != custom, "setCurrentManager(null) restores a default manager");
        check((Boolean) call(current(a), "isDoubleBufferingEnabled", new Class<?>[0]), "double buffering on by default");
    }
}
