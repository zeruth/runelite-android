// host-contract: patched-args=shadow
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * ToolTipManager, JToolTip and the JComponent tooltip members against the desktop JDK and the
 * shadow: registration through setToolTipText, the delay and enabled properties, createToolTip.
 * With {@code shadow} it also drives the timed show/dismiss and the touch host hooks through the
 * heavy weight host layer; a headless JDK cannot show a tip (no screen for its popup), so those
 * checks run on the shadow only.
 */
public final class TestToolTipContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** Showing without a window: a headless host has no peer, so the test says where it is. */
    private static final class Tipped extends JComponent {
        volatile Point tipLocation;

        @Override public boolean isShowing() { return true; }
        @Override public Point getLocationOnScreen() { return new Point(100, 200); }
        @Override public Point getToolTipLocation(MouseEvent event) { return tipLocation; }
    }

    /** What the host layer was asked to do, in order. */
    private static final class Shown {
        final Component owner;
        final Component contents;
        final int x;
        final int y;
        final long at = System.nanoTime();
        volatile long hiddenAt;

        Shown(Component owner, Component contents, int x, int y) {
            this.owner = owner;
            this.contents = contents;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean shadow = args.length > 0 && args[0].equals("shadow");
        ToolTipManager manager = ToolTipManager.sharedInstance();
        check("shared instance", manager == ToolTipManager.sharedInstance());
        check("default delays 750/4000/500", manager.getInitialDelay() == 750
            && manager.getDismissDelay() == 4000 && manager.getReshowDelay() == 500);
        check("enabled by default", manager.isEnabled());
        check("light weight popups by default", manager.isLightWeightPopupEnabled());
        manager.setLightWeightPopupEnabled(false);
        check("setLightWeightPopupEnabled", !manager.isLightWeightPopupEnabled());
        manager.setLightWeightPopupEnabled(true);
        manager.setReshowDelay(250);
        check("setReshowDelay", manager.getReshowDelay() == 250);
        manager.setReshowDelay(500);

        Tipped component = new Tipped();
        component.setBounds(0, 0, 50, 20);
        List<String> changes = Collections.synchronizedList(new ArrayList<String>());
        component.addPropertyChangeListener(JComponent.TOOL_TIP_TEXT_KEY,
            e -> changes.add(e.getOldValue() + "->" + e.getNewValue()));
        check("not registered before a tip", !listens(component, manager));
        component.setToolTipText("hello");
        check("setToolTipText registers the component", listens(component, manager));
        check("getToolTipText", "hello".equals(component.getToolTipText()));
        check("text kept as the client property", "hello".equals(component.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY)));
        check("one ToolTipText property event", changes.equals(Collections.singletonList("null->hello")));
        MouseEvent at = event(component, MouseEvent.MOUSE_MOVED, 10, 5);
        check("getToolTipText(MouseEvent) defaults to the text", "hello".equals(component.getToolTipText(at)));
        JToolTip tip = component.createToolTip();
        check("createToolTip belongs to the component", tip != null && tip.getComponent() == component);
        check("JToolTip UI class", "ToolTipUI".equals(tip.getUIClassID()));
        List<String> tipChanges = new ArrayList<>();
        tip.addPropertyChangeListener("tiptext", e -> tipChanges.add(String.valueOf(e.getNewValue())));
        tip.setTipText("text");
        check("setTipText fires tiptext", "text".equals(tip.getTipText()) && tipChanges.equals(Collections.singletonList("text")));
        component.setToolTipText(null);
        check("setToolTipText(null) unregisters", !listens(component, manager));
        manager.registerComponent(component);
        check("registerComponent", listens(component, manager));
        manager.unregisterComponent(component);
        check("unregisterComponent", !listens(component, manager));

        if (shadow) {
            shadow(manager);
        }

        System.out.println((failures == 0 ? "PASS " : "FAIL ") + (total - failures) + "/" + total + " tooltip contracts");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void shadow(ToolTipManager manager) throws Exception {
        List<Shown> shown = Collections.synchronizedList(new ArrayList<Shown>());
        // HostLayer is shadow-only API; reflection keeps the test compiling against the plain JDK.
        Class<?> layerType = Class.forName("javax.swing.PopupFactory$HostLayer");
        InvocationHandler handler = (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "show":
                    Shown entry = new Shown((Component) arguments[0], (Component) arguments[1],
                        (Integer) arguments[2], (Integer) arguments[3]);
                    shown.add(entry);
                    return entry;
                case "hide":
                    ((Shown) arguments[0]).hiddenAt = System.nanoTime();
                    return null;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == arguments[0];
                default:
                    return "TestHostLayer";
            }
        };
        Object layer = Proxy.newProxyInstance(layerType.getClassLoader(), new Class<?>[] {layerType}, handler);
        PopupFactory.class.getMethod("setHostLayer", layerType).invoke(null, layer);
        Method hostShow = ToolTipManager.class.getMethod("hostShowToolTip", JComponent.class, int.class, int.class);
        Method hostHide = ToolTipManager.class.getMethod("hostHideToolTip");

        Tipped component = new Tipped();
        component.setBounds(0, 0, 50, 20);
        component.tipLocation = new Point(7, 9);
        component.setToolTipText("hello");
        manager.setInitialDelay(150);
        manager.setDismissDelay(300);
        check("setInitialDelay/setDismissDelay", manager.getInitialDelay() == 150 && manager.getDismissDelay() == 300);

        // Hover: enter then move, as the dispatcher delivers them; the tip waits for initialDelay.
        long start = System.nanoTime();
        onEdt(() -> {
            manager.mouseEntered(event(component, MouseEvent.MOUSE_ENTERED, 10, 5));
            manager.mouseMoved(event(component, MouseEvent.MOUSE_MOVED, 10, 5));
        });
        check("hover shows the tip", waitFor(() -> !shown.isEmpty()));
        Shown hover = shown.isEmpty() ? null : shown.get(0);
        if (hover != null) {
            long after = TimeUnit.NANOSECONDS.toMillis(hover.at - start);
            System.out.println("  shown after " + after + " ms at " + hover.x + "," + hover.y);
            check("tip waits for the initial delay", after >= 130);
            check("tip at getToolTipLocation on screen", hover.x == 107 && hover.y == 209);
            check("tip owned by the component", hover.owner == component);
            check("tip contents are the component's JToolTip", hover.contents instanceof JToolTip
                && "hello".equals(((JToolTip) hover.contents).getTipText())
                && ((JToolTip) hover.contents).getComponent() == component);
            check("tip dismisses", waitFor(() -> hover.hiddenAt != 0));
            long kept = TimeUnit.NANOSECONDS.toMillis(hover.hiddenAt - hover.at);
            System.out.println("  dismissed after " + kept + " ms");
            check("tip stays for the dismiss delay", hover.hiddenAt != 0 && kept >= 280);
        }
        onEdt(() -> manager.mouseExited(event(component, MouseEvent.MOUSE_EXITED, 60, 5)));

        // Touch: a long press shows at once, under the finger when the component has no location.
        component.tipLocation = null;
        shown.clear();
        AtomicReference<Object> result = new AtomicReference<>();
        onEdt(() -> result.set(invoke(hostShow, manager, component, 3, 4)));
        check("long press shows at once", Boolean.TRUE.equals(result.get()) && shown.size() == 1);
        Shown press = shown.isEmpty() ? null : shown.get(0);
        check("long press tip under the finger", press != null && press.x == 103 && press.y == 224);
        onEdt(() -> invoke(hostHide, manager));
        check("tap dismisses", press != null && press.hiddenAt != 0);

        shown.clear();
        onEdt(() -> invoke(hostShow, manager, component, 3, 4));
        onEdt(() -> manager.mousePressed(event(component, MouseEvent.MOUSE_PRESSED, 3, 4)));
        check("mousePressed hides the tip", shown.size() == 1 && shown.get(0).hiddenAt != 0);

        shown.clear();
        manager.setEnabled(false);
        onEdt(() -> result.set(invoke(hostShow, manager, component, 3, 4)));
        check("disabled manager shows nothing", Boolean.FALSE.equals(result.get()) && shown.isEmpty());
        manager.setEnabled(true);
        component.setToolTipText(null);
        onEdt(() -> result.set(invoke(hostShow, manager, component, 3, 4)));
        check("no text shows nothing", Boolean.FALSE.equals(result.get()) && shown.isEmpty());

        PopupFactory.class.getMethod("setHostLayer", layerType).invoke(null, (Object) null);
        manager.setInitialDelay(750);
        manager.setDismissDelay(4000);
    }

    private static boolean listens(JComponent component, ToolTipManager manager) {
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener == manager) return true;
        }
        return false;
    }

    private static MouseEvent event(Component source, int id, int x, int y) {
        return new MouseEvent(source, id, System.currentTimeMillis(), 0, x, y, 0, false);
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(method.getName(), e);
        }
    }

    private static void onEdt(Runnable body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) throw new AssertionError("EDT step failed", failure.get());
    }

    private interface Condition { boolean holds(); }

    private static boolean waitFor(Condition condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (condition.holds()) return true;
            Thread.sleep(5);
        }
        return condition.holds();
    }
}
