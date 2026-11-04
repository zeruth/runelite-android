import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Exercises the unmodified Hub JAR on the host classpath, without a game account. */
public class TestBoatyGuide {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static int countText(Component component) {
        int count = component instanceof JTextArea ? 1 : 0;
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) count += countText(child);
        return count;
    }
    private static void run() throws Exception {
        Class<?> wrappedClass = Class.forName("com.b0atyguide.ui.WrappedText");
        Constructor<?> constructor = wrappedClass.getDeclaredConstructor(String.class, Color.class);
        constructor.setAccessible(true);
        JTextArea wrapped = (JTextArea) constructor.newInstance("Test guide step", Color.WHITE);
        check(wrapped.getMargin().equals(new Insets(0, 0, 0, 0)), "exact reported WrappedText constructor");
        Method highlight = wrappedClass.getDeclaredMethod("highlightMatches", String.class);
        highlight.setAccessible(true);
        highlight.invoke(wrapped, "guide");
        check(wrapped.getHighlighter().getHighlights().length == 1, "guide text highlighting");
        Class.forName("com.b0atyguide.B0atyGuidePlugin").getConstructor().newInstance();
        check(true, "plugin object construction");
        Class<?> gson = Class.forName("com.google.gson.Gson");
        Class<?> loader = Class.forName("com.b0atyguide.data.GuideLoader");
        Object guide = loader.getMethod("loadBundled").invoke(loader.getConstructor(gson).newInstance(gson.getConstructor().newInstance()));
        Object progress = Class.forName("com.b0atyguide.progress.Progress").getConstructor().newInstance();
        Class<?> panelClass = Class.forName("com.b0atyguide.ui.GuidePanel");
        Object panel = panelClass.getConstructor().newInstance();
        Method init = null;
        for (Method method : panelClass.getMethods()) if (method.getName().equals("init")) init = method;
        if (init == null) throw new AssertionError("GuidePanel.init missing");
        BiConsumer<Object, Boolean> toggle = (step, selected) -> {};
        Consumer<Object> select = step -> {};
        Consumer<Boolean> dismiss = shown -> {};
        init.invoke(panel, guide, progress, toggle, select, toggle, (IntSupplier) () -> 14,
            (BooleanSupplier) () -> false, null, (BooleanSupplier) () -> false,
            (BooleanSupplier) () -> false, dismiss);
        List<?> sections = (List<?>) field(panel, "sectionPanels");
        int sectionCount = sections.size();
        check(sectionCount > 200, "bundled guide loaded and sections constructed");
        for (int i = 0; i < sectionCount; i++) panelClass.getMethod("expandForTest", int.class).invoke(panel, i);
        int textAreas = countText((Component) panel);
        check(textAreas > 2000, "all guide sections expand into text rows");
        JTextField search = (JTextField) field(panel, "searchField");
        search.setText("bank");
        Method applyFilter = panelClass.getDeclaredMethod("applyFilter");
        applyFilter.setAccessible(true);
        applyFilter.invoke(panel);
        check(field(panel, "filter").equals("bank"), "guide search rebuild");
        search.setText("no-guide-step-matches-this-fixture-987654321");
        applyFilter.invoke(panel);
        check(sections.stream().noneMatch(section -> ((Component) section).isVisible()), "unmatched search hides sections");
        search.setText("");
        applyFilter.invoke(panel);
        check(sections.stream().allMatch(section -> ((Component) section).isVisible()), "clearing search restores sections");
        panelClass.getMethod("deinit").invoke(panel);
        check(sections.isEmpty() && !((javax.swing.Timer) field(panel, "searchTimer")).isRunning(), "guide teardown");
        System.out.println("PASS: " + checks + " Boaty checks; " + sectionCount + " sections; " + textAreas + " text areas");
    }
    public static void runChecks() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Boaty UI failed", failure.get());
        if (checks == 0) throw new AssertionError("Boaty UI did not execute");
    }
    public static void main(String[] args) throws Exception {
        try {
            runChecks();
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
