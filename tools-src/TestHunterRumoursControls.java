import java.awt.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Exercises the original Hub JAR with real RuneLite panel, preferences and configuration classes. */
public class TestHunterRumoursControls {
    private static Class<?> type(String name) throws Exception { return Class.forName(name); }
    private static Object create(Class<?> type, Object... args) throws Exception {
        for (Constructor<?> ctor : type.getDeclaredConstructors()) if (ctor.getParameterCount() == args.length) {
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        }
        throw new NoSuchMethodException(type.getName());
    }
    private static Field field(Class<?> cls, String name) throws Exception {
        Field f = cls.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static <T> T find(Component root, Class<T> cls) {
        if (cls.isInstance(root)) return cls.cast(root);
        if (root instanceof Container) for (Component child : ((Container)root).getComponents()) {
            T value = find(child, cls); if (value != null) return value;
        }
        return null;
    }
    private interface Work { void run() throws Exception; }
    private static void edt(Work work) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { work.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private final Class<?> configType = type("net.runelite.client.config.ConfigManager");
    private final Class<?> dataType = type("net.runelite.client.config.ConfigData");
    private final Class<?> prefsType = type("com.geel.hunterrumours.PreferredLocationPreferences");
    private final Object client = Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{type("net.runelite.api.Client")}, (p,m,a) -> {
            if (m.getName().equals("isClientThread")) return false;
            throw new AssertionError(m.getName());
        });
    // Loading item sprites is a game-thread task; this panel test only needs their placeholders.
    private final ScheduledExecutorService executor = (ScheduledExecutorService)Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[]{ScheduledExecutorService.class}, (p,m,a) -> null);
    private final Object bus = create(type("net.runelite.client.eventbus.EventBus"));
    private final File file = File.createTempFile("hunter-controls", ".properties", new File(System.getProperty("user.home")));
    private Object config;
    private Object prefs;
    private JComponent panel;
    private TestHunterRumoursControls() throws Exception {}
    private Object configFromDisk() throws Exception {
        Object cm = create(configType, null, executor, bus, client, null, null, null, null);
        field(configType, "configProfile").set(cm, create(dataType, file));
        return cm;
    }
    private void verify() throws Exception {
        config = configFromDisk();
        Object thread = create(type("net.runelite.client.callback.ClientThread"));
        field(thread.getClass(), "client").set(thread, client);
        Object items = create(type("net.runelite.client.game.ItemManager"), client, executor, thread, bus, null, null);
        prefs = create(prefsType, config);
        prefsType.getMethod("initializeDefaults").invoke(prefs);
        create(type("com.geel.hunterrumours.HunterRumoursPlugin"));
        edt(() -> panel = (JComponent)create(type("com.geel.hunterrumours.PreferredLocationPreferencesPanel"), prefs, items));
        edt(() -> {
            Map<?,?> cards = (Map<?,?>)field(panel.getClass(), "creatureCards").get(panel);
            if (cards.isEmpty()) throw new AssertionError("No creature cards after deferred rebuild");
            JTextField search = find(panel, JTextField.class);
            if (!"Search creatures or locations".equals(search.getAccessibleContext().getAccessibleName()))
                throw new AssertionError("Search label");
            Method getPane = panel.getClass().getSuperclass().getDeclaredMethod("getScrollPane");
            getPane.setAccessible(true);
            JScrollPane pane = (JScrollPane)getPane.invoke(panel);
            pane.setSize(235, 160); pane.doLayout(); pane.getViewport().doLayout();
            pane.getVerticalScrollBar().setValue(48);
            if (pane.getViewport().getViewPosition().y != 48) throw new AssertionError("Panel did not scroll");
            for (Map.Entry<?,?> entry : cards.entrySet()) {
                JComboBox<?> combo = find((Component)entry.getValue(), JComboBox.class);
                if (combo == null || combo.getItemCount() < 2) continue;
                combo.showPopup();
                if (!combo.isPopupVisible()) throw new AssertionError("Location popup did not open");
                combo.setSelectedIndex(combo.getSelectedIndex() == 0 ? 1 : 0);
                combo.hidePopup();
                Object selected = combo.getSelectedItem(), creature = entry.getKey();
                Object data = field(configType, "configProfile").get(config);
                Method patch = dataType.getDeclaredMethod("patch", Map.class); patch.setAccessible(true);
                patch.invoke(data, Collections.emptyMap());
                Object restored = create(prefsType, configFromDisk());
                Object saved = prefsType.getMethod("getPreferredLocation", creature.getClass()).invoke(restored, creature);
                if (saved != selected) throw new AssertionError("Preference did not survive reload");
                System.out.println("PASS Hunter Rumours: " + cards.size() + " cards; scrolled and saved " + creature + " -> " + selected);
                return;
            }
            throw new AssertionError("No location chooser");
        });
    }
    public static void runChecks() throws Exception {
        TestHunterRumoursControls test = new TestHunterRumoursControls();
        try { test.verify(); } finally { test.file.delete(); }
    }
}
