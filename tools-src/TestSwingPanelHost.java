import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.*;

public class TestSwingPanelHost {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void field(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static Object value(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static final class ReloadPanel extends net.runelite.client.ui.PluginPanel {
        int activated, deactivated, clicks;
        final JButton action = new JButton("Current action");
        ReloadPanel() {
            super(false); setLayout(null); setBounds(0, 0, 240, 300);
            action.setBounds(20, 20, 170, 35); action.addActionListener(e -> clicks++); add(action);
        }
        @Override public void onActivate() { check(EventQueue.isDispatchThread(), "mount activation uses EDT"); activated++; }
        @Override public void onDeactivate() { check(EventQueue.isDispatchThread(), "mount deactivation uses EDT"); deactivated++; }
    }
    private static final class DismissProbe extends android.app.AlertDialog {
        int dismissals;
        private DismissProbe(android.content.Context context) { super(context); }
        @Override public void dismiss() { dismissals++; }
    }
    private static DismissProbe dismissProbe() throws Exception {
        // The shell system context has no Settings provider for a real PhoneWindow.
        // Test the host's dismissal and reference cleanup without constructing a window.
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return (DismissProbe) unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), DismissProbe.class);
    }
    private static void onEdt(Method method, Object target, Object... args) throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { method.invoke(target, args); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("EDT fixture failed", failure.get());
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", "/data/local/tmp/swing-host-test");
        // app_process bypasses Zygote's initialization on recent Android releases.
        try {
            Class<?> shared = Class.forName("com.android.internal.os.ApplicationSharedMemory");
            Object memory = shared.getMethod("create").invoke(null);
            shared.getMethod("setInstance", shared).invoke(null, memory);
        } catch (ClassNotFoundException olderAndroid) { /* Not present on older Android. */ }
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        android.content.Context context = (android.content.Context) at.getMethod("getSystemContext").invoke(thread);
        Class<?> hostClass = Class.forName("net.runelite.mp.ui.panels.SwingPanelView");
        Object host = hostClass.getConstructor(android.content.Context.class, String.class).newInstance(context, "test");
        ((android.view.View) host).layout(0, 0, 100, 100);
        android.graphics.Bitmap surface = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888);
        surface.eraseColor(android.graphics.Color.GREEN);
        android.graphics.Canvas canvas = new android.graphics.Canvas(surface);
        canvas.translate(150, 0);
        Method draw = hostClass.getDeclaredMethod("onDraw", android.graphics.Canvas.class); draw.setAccessible(true);
        draw.invoke(host, canvas);
        check(surface.getPixel(10, 10) == android.graphics.Color.GREEN, "panel paint must not cover game");
        check(surface.getPixel(160, 10) != android.graphics.Color.GREEN, "panel paints within its bounds");
        System.out.println("PASS: panel paint stays inside its translated bounds");
        JPanel root = new JPanel(null);
        root.setBounds(0, 0, 240, 300);
        JButton button = new JButton("Test action");
        button.setBounds(20, 20, 170, 35);
        root.add(button);
        int[] count = {0};
        button.addActionListener(e -> count[0]++);
        field(host, "root", root);
        field(host, "running", true);
        field(host, "mountedGeneration", 0L);
        Method tap = hostClass.getDeclaredMethod("tap", int.class, int.class); tap.setAccessible(true);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "button must fire exactly once");
        button.setEnabled(false);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "disabled button must not fire");
        button.setEnabled(true);
        // Profile rows use disabled read-only text as a clickable expansion label.
        javax.swing.JTextField profileName = new javax.swing.JTextField("profile");
        profileName.setBounds(10, 105, 100, 20);
        profileName.setEditable(false); profileName.setEnabled(false);
        int[] expanded = {0};
        profileName.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent event) { expanded[0]++; }
        });
        root.add(profileName);
        tap.invoke(host, 20, 110);
        check(expanded[0] == 1, "disabled profile label must receive its mouse listener");
        root.setEnabled(false);
        tap.invoke(host, 20, 110);
        check(expanded[0] == 1, "disabled ancestor must still block profile label input");
        root.setEnabled(true); root.remove(profileName);
        // Real shadow Swing popup contract used by XP and Loot Tracker cards.
        JPanel card = new JPanel(null); card.setBounds(0, 160, 200, 100); root.add(card);
        JLabel cardText = new JLabel("Loot entry"); cardText.setBounds(10, 10, 150, 20); card.add(cardText);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem hide = new JMenuItem("Hide loot"); menu.add(hide);
        // The standalone process lacks Zygote's font map; input probes use fixed sizes.
        menu.setPreferredSize(new Dimension(150, 30)); hide.setPreferredSize(new Dimension(150, 30));
        int[] opened = {0}, closed = {0}, right = {0}, actions = {0};
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { opened[0]++; hide.setText("Hide this loot"); }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { closed[0]++; }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
        hide.addActionListener(e -> actions[0]++);
        card.setComponentPopupMenu(menu);
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                check(e.getButton() == java.awt.event.MouseEvent.BUTTON3 && e.isPopupTrigger(), "long click must be a right-click popup trigger");
                check(e.getX() == 15 && e.getY() == 15, "right-click coordinates must be relative to the recipient");
                right[0]++;
            }
        });
        Method contextMenu = hostClass.getDeclaredMethod("contextMenu", int.class, int.class); contextMenu.setAccessible(true);
        contextMenu.invoke(host, 15, 175);
        check(right[0] == 1 && opened[0] == 1 && menu.isVisible(), "nested label opens its nearest ancestor's popup once");
        check("Hide this loot".equals(hide.getText()) && actions[0] == 0 && count[0] == 1, "opening updates menu labels without executing a menu or left-click action");
        menu.setVisible(false); menu.setVisible(false);
        check(closed[0] == 1, "popup dismissal fires visibility listeners exactly once");
        card.setEnabled(false); contextMenu.invoke(host, 15, 175);
        check(opened[0] == 1 && right[0] == 1, "disabled ancestor blocks context actions");
        card.setEnabled(true);
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) { if (e.isPopupTrigger()) menu.show(card, e.getX(), e.getY()); }
        });
        contextMenu.invoke(host, 15, 175);
        check(opened[0] == 2 && JPopupMenu.takeShownPopupMenu() == null, "listener-opened menu is consumed once without reopening its component popup");
        menu.setVisible(false);
        // Feed the actual View touch handler; run its timeout callback without a real wait.
        long now = android.os.SystemClock.uptimeMillis();
        android.view.View hostView = (android.view.View) host;
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 6.25f, 175f / 2.4f, 0));
        Field hold = hostClass.getDeclaredField("longPress"); hold.setAccessible(true);
        ((Runnable) hold.get(host)).run();
        SwingUtilities.invokeAndWait(() -> {});
        check(opened[0] == 2, "hold latches without opening a new window while the finger is down");
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 600, android.view.MotionEvent.ACTION_UP, 6.25f, 175f / 2.4f, 0));
        SwingUtilities.invokeAndWait(() -> {});
        check(opened[0] == 3 && actions[0] == 0 && count[0] == 1, "long hold opens one menu and releasing does not trigger a left click or menu action");
        menu.setVisible(false);
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 6.25f, 175f / 2.4f, 0));
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 100, android.view.MotionEvent.ACTION_MOVE, 100f, 175f / 2.4f, 0));
        ((Runnable) hold.get(host)).run();
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 600, android.view.MotionEvent.ACTION_UP, 100f, 175f / 2.4f, 0));
        SwingUtilities.invokeAndWait(() -> {});
        check(opened[0] == 3 && count[0] == 1, "horizontal drag cancels long press and release tap");
        float jitter = android.view.ViewConfiguration.get(context).getScaledTouchSlop() * 1.25f;
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now, 0, 6.25f, 175f / 2.4f, 0));
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 100, 2, 6.25f + jitter, 175f / 2.4f, 0));
        ((Runnable) hold.get(host)).run();
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 600, 1, 6.25f + jitter, 175f / 2.4f, 0));
        SwingUtilities.invokeAndWait(() -> {});
        check(opened[0] == 4 && actions[0] == 0, "minor finger jitter preserves the held target without selecting an action");
        menu.setVisible(false);
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now, 0, 6.25f, 175f / 2.4f, 0));
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 100, 3, 6.25f, 175f / 2.4f, 0));
        ((Runnable) hold.get(host)).run();
        SwingUtilities.invokeAndWait(() -> {});
        check(opened[0] == 4, "canceled touch cannot later open a menu");
        System.out.println("PASS: actual touch hold, release suppression and drag cancellation");
        // Exercise the actual RuneLite redraw path that failed after Hide loot.
        JPanel removable = new JPanel(); removable.add(new JPanel()); removable.add(new JPanel());
        int[] drained = {0};
        Canvas eventTarget = new Canvas();
        eventTarget.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                check(EventQueue.isDispatchThread(), "pending events must stay on the EDT");
                drained[0]++;
            }
        });
        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        eventQueue.postEvent(new java.awt.event.MouseEvent(eventTarget, 501, 1L, 0, 1, 1, 1, false));
        java.util.concurrent.atomic.AtomicReference<Throwable> redrawError = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class.forName("net.runelite.client.util.SwingUtil").getMethod("fastRemoveAll", Container.class).invoke(null, removable);
            } catch (Throwable error) { redrawError.set(error); }
        });
        if (redrawError.get() != null) redrawError.get().printStackTrace(System.out);
        System.out.println("Redraw children=" + removable.getComponentCount() + " drained=" + drained[0] + " pending=" + eventQueue.peekEvent());
        check(redrawError.get() == null && removable.getComponentCount() == 0 && drained[0] == 1 && eventQueue.peekEvent() == null,
            "real fastRemoveAll drains pending events and rebuilds children without a missing method or deadlock");
        SwingUtilities.invokeAndWait(() -> {
            JPanel child = new JPanel(); removable.add(child);
            JMenuItem rebuild = new JMenuItem("Hide fixture loot");
            rebuild.addActionListener(e -> {
                try { Class.forName("net.runelite.client.util.SwingUtil").getMethod("fastRemoveAll", Container.class).invoke(null, removable); }
                catch (Throwable error) { redrawError.set(error); }
            });
            eventQueue.postEvent(new java.awt.event.MouseEvent(eventTarget, 501, 1L, 0, 1, 1, 1, false));
            rebuild.doClick();
        });
        check(redrawError.get() == null && removable.getComponentCount() == 0 && drained[0] == 2,
            "menu action completes the real RuneLite redraw with pending events");
        System.out.println("PASS: menu action through real RuneLite fastRemoveAll and secondary EDT loop");
        System.out.println("PASS: panel right-click targets, inherited menus, popup lifecycle and no accidental left/menu action");
        JTextField text = new JTextField("read only"); text.setEditable(false);
        check(!text.isEditable(), "read-only field respected");
        JComboBox<String> combo = new JComboBox<>(new String[]{"One", "Two"});
        combo.setSelectedIndex(1);
        check("Two".equals(combo.getModel().getSelectedItem()), "selection updates model");

        combo.setBounds(20, 70, 170, 30); root.add(combo);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));

        spinner.setBounds(20, 110, 170, 30); root.add(spinner);
        field(host, "running", false);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "detached host ignores input");
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0; constraints.weightx = 1; constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < 3; i++) {
            JPanel row = new JPanel(); row.setPreferredSize(new Dimension(100, 30));
            constraints.gridy = i; grid.add(row, constraints);
        }
        check(grid.getPreferredSize().height == 90, "grid must measure three rows");
        grid.setBounds(0, 0, 240, 90); grid.validate();
        for (int i = 0; i < 3; i++) {
            check(grid.getComponent(i).getWidth() == 240, "weighted row fills width");
            check(grid.getComponent(i).getY() == i * 30, "grid rows do not overlap");
        }
        System.out.println("PASS: GridBag preferred size, weighted width, nonoverlapping rows");
        Class<?> html = Class.forName("javax.swing.HtmlTextRenderer");
        Method parse = html.getDeclaredMethod("parse", String.class); parse.setAccessible(true);
        parse.invoke(null, "<html><body><span style='color:#ff0000'>Gained:</span> 100</body></html>");
        parse.invoke(null, "<html><body style='text-align:center'>You have not gained experience yet.</body></html>");
        JPanel border = new JPanel(new BorderLayout());
        border.setBorder(new javax.swing.border.EmptyBorder(4, 4, 4, 4));
        JPanel center = new JPanel(); center.setPreferredSize(new Dimension(20, 30)); border.add(center);
        JPanel hidden = new JPanel(); hidden.setPreferredSize(new Dimension(200, 300)); hidden.setVisible(false);
        border.add(hidden, BorderLayout.NORTH);
        check(border.getPreferredSize().equals(new Dimension(28, 38)), "border respects hidden children and insets");
        border.setBounds(0, 0, 100, 100); border.validate();
        check(center.getBounds().equals(new Rectangle(4, 4, 92, 92)), "default add registers center");
        JPanel box = new JPanel(); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JPanel row = new JPanel(); row.setPreferredSize(new Dimension(20, 30)); box.add(row);
        JPanel collapsed = new JPanel(); collapsed.setPreferredSize(new Dimension(200, 300)); collapsed.setVisible(false); box.add(collapsed);
        check(box.getPreferredSize().equals(new Dimension(20, 30)), "box excludes collapsed rows");
        System.out.println("PASS: stock XP HTML labels, border center/insets, collapsed rows");
        JPanel painted = new JPanel(null);
        int[] childPaints = {0};
        Component child = new Component() { public void paint(Graphics graphics) { childPaints[0]++; } };
        child.setBounds(0, 0, 10, 10); painted.add(child);
        BufferedImage testImage = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics testGraphics = testImage.createGraphics();
        painted.paintComponents(testGraphics); testGraphics.dispose();
        check(childPaints[0] == 1, "stock progress bars can repaint their child labels");
        System.out.println("PASS: stock progress bar child painting entry point");
        System.out.println("PASS: button single dispatch, disabled/detached controls, read-only text, combo model");
        // Exercise the production remount and input cleanup, without a loaded plugin manager.
        field(host, "running", true);
        Method mount = hostClass.getDeclaredMethod("mountPanel", net.runelite.client.ui.PluginPanel.class, long.class); mount.setAccessible(true);
        Method clear = hostClass.getDeclaredMethod("clearAndroidState", long.class); clear.setAccessible(true);
        ReloadPanel first = new ReloadPanel(), replacement = new ReloadPanel();
        onEdt(mount, host, first, 11L);
        long firstSerial = (Long) value(host, "mountSerial");
        check(value(host, "panel") == first && value(host, "root") == first.getWrappedPanel() && first.activated == 1,
            "mount binds and activates the current panel on EDT");
        onEdt(mount, host, first, 11L);
        check((Long) value(host, "mountSerial") == firstSerial && first.activated == 1 && first.deactivated == 0,
            "normal repaint does not remount an unchanged instance and generation");
        JPopupMenu obsoleteMenu = new JPopupMenu(); JMenuItem obsoleteAction = new JMenuItem("Old action"); obsoleteMenu.add(obsoleteAction);
        obsoleteMenu.setPreferredSize(new Dimension(150, 30)); obsoleteAction.setPreferredSize(new Dimension(150, 30));
        obsoleteMenu.setVisible(true); field(host, "swingPopup", obsoleteMenu);
        field(host, "touchActive", true); field(host, "touchMount", firstSerial); field(host, "longPressed", true);
        field(host, "frame", surface);
        DismissProbe contextProbe = dismissProbe(), inputProbe = dismissProbe();
        field(host, "contextPopup", contextProbe);
        field(host, "inputPopup", inputProbe);
        onEdt(mount, host, replacement, 12L);
        long replacementSerial = (Long) value(host, "mountSerial");
        check(replacementSerial != firstSerial && first.deactivated == 1 && replacement.activated == 1 &&
                value(host, "root") == replacement.getWrappedPanel() && !obsoleteMenu.isVisible() && value(host, "swingPopup") == null,
            "replacement invalidates the old mount, deactivates its panel and closes its real Swing popup");
        clear.invoke(host, firstSerial);
        check(value(host, "frame") == surface && (Boolean) value(host, "touchActive") &&
                contextProbe.dismissals == 0 && inputProbe.dismissals == 0,
            "a queued old-mount cleanup cannot clear the replacement's current Android state");
        clear.invoke(host, replacementSerial);
        check(value(host, "frame") == null && value(host, "contextPopup") == null && value(host, "inputPopup") == null &&
                !(Boolean) value(host, "touchActive") && !(Boolean) value(host, "longPressed") && (Long) value(host, "touchMount") == -1L &&
                contextProbe.dismissals == 1 && inputProbe.dismissals == 1,
            "current remount drops the old bitmap, Android dialogs and held-touch references");
        ((Runnable) hold.get(host)).run();
        hostView.onTouchEvent(android.view.MotionEvent.obtain(now, now + 600, 1, 10f, 10f, 0));
        SwingUtilities.invokeAndWait(() -> {});
        check(first.clicks == 0 && replacement.clicks == 0 && !(Boolean) value(host, "longPressed"),
            "a held finger released after reload cannot click either the old or replacement panel");
        onEdt(tap, host, 25, 25);
        check(replacement.clicks == 1 && first.clicks == 0, "the host's control dispatch now targets only the replacement tree");
        onEdt(mount, host, replacement, 13L);
        check((Long) value(host, "mountSerial") != replacementSerial && replacement.activated == 2 && replacement.deactivated == 1,
            "a new loader generation invalidates a mount even if the panel identity is reused");
        onEdt(mount, host, null, 14L);
        check(value(host, "root") == null && value(host, "panel") == null && replacement.deactivated == 2,
            "a temporarily removed navigation panel releases the entire Swing tree instead of retaining stale controls");
        System.out.println("PASS: panel replacement, generation remount, popup/touch/frame cleanup and current-tree dispatch");
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Profile import");
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("RuneLite properties", "properties"));
        chooser.setAcceptAllFileFilterUsed(false);
        JFileChooser.setDialogHandler((selected, save) -> {
            check(!selected.isAcceptAllFileFilterUsed(), "profile chooser filter preference");
            selected.setSelectedFile(new java.io.File("/data/local/tmp/test.properties"));
            return save ? JFileChooser.CANCEL_OPTION : JFileChooser.APPROVE_OPTION;
        });
        check(chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().getName().equals("test.properties"), "open chooser delegates result");
        check(chooser.showSaveDialog(null) == JFileChooser.CANCEL_OPTION, "save cancellation");
        JFileChooser.setDialogHandler(null);
        check(chooser.showOpenDialog(null) == JFileChooser.CANCEL_OPTION, "missing picker cancels");
        JOptionPane.setDialogHandler((message, title, type, options) -> JOptionPane.CANCEL_OPTION);
        check(JOptionPane.showConfirmDialog(null, "Delete?", "Warning", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION, "confirmation must preserve cancellation");
        JOptionPane.setDialogHandler(null);
        check(JOptionPane.showConfirmDialog(null, "Delete?") == JOptionPane.CLOSED_OPTION, "no automatic approval without host");
        Class<?> completionClass = Class.forName("net.runelite.mp.ui.bridge.SwingDialogs$Completion");
        Constructor<?> completionCtor = completionClass.getDeclaredConstructor(); completionCtor.setAccessible(true);
        Method finishCompletion = completionClass.getDeclaredMethod("finish"); finishCompletion.setAccessible(true);
        Method awaitCompletion = completionClass.getDeclaredMethod("await"); awaitCompletion.setAccessible(true);
        java.util.concurrent.atomic.AtomicReference<Throwable> modalFailure = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object completion = completionCtor.newInstance();
                boolean[] pumped = {false};
                SwingUtilities.invokeLater(() -> {
                    pumped[0] = true;
                    try { finishCompletion.invoke(completion); } catch (Exception e) { throw new RuntimeException(e); }
                });
                awaitCompletion.invoke(completion);
                check(pumped[0], "modal wait must pump sidebar EDT work");
                Object early = completionCtor.newInstance();
                finishCompletion.invoke(early); awaitCompletion.invoke(early);
            } catch (Throwable e) { modalFailure.set(e); }
        });
        if (modalFailure.get() != null) throw new AssertionError("modal completion", modalFailure.get());
        System.out.println("PASS: profile chooser contract, cancel-safe confirmations, modal event pumping and early completion");
        System.exit(0);
    }
}
