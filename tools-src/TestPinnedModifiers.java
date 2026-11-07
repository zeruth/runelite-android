import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JTextField;
import net.runelite.api.Client;
import net.runelite.client.plugins.mobile.MobileConfig;
import net.runelite.mp.ui.bridge.ModifierState;
import net.runelite.mp.ui.bridge.ModifierState.Key;
import net.runelite.mp.ui.bridge.PinnedKeys;

/** Run inside the Android host; drainMain waits for the Mobile config update to reach the UI thread. */
public class TestPinnedModifiers {
    private final ModifierState toolbar = ModifierState.INSTANCE;
    private final EnumSet<Key> down = EnumSet.noneOf(Key.class);
    private final List<Integer> presses = new ArrayList<>();
    private Canvas canvas = new Canvas();
    private boolean consume;
    private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
        (proxy, method, args) -> {
            if (method.getName().equals("getCanvas")) return canvas;
            if (method.getName().equals("isKeyPressed")) {
                for (Key key : down) if (key.getKc() == (int) args[0]) return true;
                return false;
            }
            throw new AssertionError(method.getName());
        });

    public static void run(Runnable drainMain) throws Exception {
        MobileConfig pins = (MobileConfig) Proxy.newProxyInstance(MobileConfig.class.getClassLoader(),
            new Class<?>[]{MobileConfig.class}, (proxy, method, args) -> method.getName().startsWith("pin"));
        PinnedKeys.INSTANCE.update(pins);
        drainMain.run();
        new TestPinnedModifiers().verify();
    }

    private void verify() throws Exception {
        toolbar.reset();
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                presses.add(event.getKeyCode());
                if (consume) event.consume();
                else for (Key key : Key.values()) if (key.getVk() == event.getKeyCode()) down.add(key);
            }
            @Override public void keyReleased(KeyEvent event) {
                for (Key key : Key.values()) if (key.getVk() == event.getKeyCode()) down.remove(key);
            }
        });
        canvas.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { down.clear(); toolbar.onCanvasFocusChanged(false); }
            @Override public void focusGained(FocusEvent event) { toolbar.onCanvasFocusChanged(true); }
        });
        canvas.requestFocusInWindow();
        tick();
        for (Key key : Key.values()) {
            tap(key);
            for (int i = 0; i < 200; i++) tick();
            check(toolbar.isHeld(key), "hold through input updates: " + key);
        }
        check(presses.size() == 3, "one press per modifier");
        tap(Key.SHIFT); tick();
        check(!toolbar.isHeld(Key.SHIFT) && toolbar.isHeld(Key.CTRL) && toolbar.isHeld(Key.ALT), "independent release");
        new JTextField().requestFocusInWindow();
        tick(); canvas.requestFocusInWindow(); tick();
        check(!toolbar.isHeld(Key.CTRL) && !toolbar.isHeld(Key.ALT) && presses.size() == 3, "no re-press after canvas blur");

        EventQueue.invokeAndWait(() -> {
            toolbar.toggle(Key.ALT);
            new JTextField().requestFocusInWindow();
        });
        EventQueue.invokeAndWait(() -> {}); tick();
        check(presses.size() == 3 && !toolbar.isHeld(Key.ALT), "blur cancels a queued tap");
        tap(Key.ALT); tick();
        check(canvas.hasFocus() && toolbar.isHeld(Key.ALT), "fresh tap focuses the canvas");
        toolbar.releaseUnpinned(java.util.Collections.emptyList());
        EventQueue.invokeAndWait(() -> {}); tick();
        check(!toolbar.isHeld(Key.ALT), "unpin releases toolbar-owned keys");

        consume = true;
        tap(Key.SHIFT); tick(); tick();
        check(!toolbar.isHeld(Key.SHIFT), "consumed press stays dark");
        consume = false;
        tap(Key.SHIFT); tick();
        check(toolbar.isHeld(Key.SHIFT), "retry after consumed press");
        toolbar.onWindowFocusChanged(false);
        EventQueue.invokeAndWait(() -> {});
        toolbar.onWindowFocusChanged(true); tick();
        check(!toolbar.isHeld(Key.SHIFT), "activity refocus leaves key released");

        tap(Key.CTRL); tap(Key.CTRL); tick();
        check(!toolbar.isHeld(Key.CTRL), "rapid double tap");
        down.add(Key.ALT); tick(); tick();
        check(toolbar.isHeld(Key.ALT), "physical key lights chip");
        tap(Key.ALT);
        toolbar.releaseUnpinned(java.util.Collections.emptyList());
        EventQueue.invokeAndWait(() -> {}); tick();
        check(toolbar.isHeld(Key.ALT), "toolbar cannot release a physically held key");
        down.clear(); tick();
        check(!toolbar.isHeld(Key.ALT), "physical release clears chip");

        tap(Key.SHIFT); tick();
        canvas = new Canvas();
        down.clear(); tick(); tick();
        check(!toolbar.isHeld(Key.SHIFT), "canvas replacement leaves chip off");
    }
    private void tap(Key key) throws Exception { toolbar.toggle(key); EventQueue.invokeAndWait(() -> {}); }
    private void tick() { toolbar.afterClientTick(client); }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
