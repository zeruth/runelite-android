// host-contract: patched-args=shadow
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Desktop, SystemTray, TrayIcon, Taskbar and Cursor. Both runs (the JDK runs headless) check the enum
 * tables, the unsupported tray/taskbar and the predefined cursors; the shadow run adds the host
 * bridges: the isSupported table, argument validation, the JDK 9 members and the TrayIcon API.
 */
public class TestDesktopContracts {
    static final List<String> ACTIONS = Arrays.asList(
            "OPEN", "EDIT", "PRINT", "MAIL", "BROWSE",
            "APP_EVENT_FOREGROUND", "APP_EVENT_HIDDEN", "APP_EVENT_REOPENED", "APP_EVENT_SCREEN_SLEEP",
            "APP_EVENT_SYSTEM_SLEEP", "APP_EVENT_USER_SESSION", "APP_ABOUT", "APP_PREFERENCES",
            "APP_OPEN_FILE", "APP_PRINT_FILE", "APP_OPEN_URI", "APP_QUIT_HANDLER", "APP_QUIT_STRATEGY",
            "APP_SUDDEN_TERMINATION", "APP_REQUEST_FOREGROUND", "APP_HELP_VIEWER", "APP_MENU_BAR",
            "BROWSE_FILE_DIR", "MOVE_TO_TRASH");
    static final List<String> FEATURES = Arrays.asList(
            "ICON_BADGE_TEXT", "ICON_BADGE_NUMBER", "ICON_BADGE_IMAGE_WINDOW", "ICON_IMAGE", "MENU",
            "PROGRESS_STATE_WINDOW", "PROGRESS_VALUE", "PROGRESS_VALUE_WINDOW", "USER_ATTENTION",
            "USER_ATTENTION_WINDOW");
    static final List<String> STATES = Arrays.asList("OFF", "NORMAL", "PAUSED", "INDETERMINATE", "ERROR");

    public static void main(String[] args) throws Exception {
        check(ACTIONS.equals(names(Desktop.Action.values())), "Desktop.Action order: " + names(Desktop.Action.values()));
        check(FEATURES.equals(names(Taskbar.Feature.values())), "Taskbar.Feature order");
        check(STATES.equals(names(Taskbar.State.values())), "Taskbar.State order");
        check(Arrays.asList("ERROR", "WARNING", "INFO", "NONE").equals(names(TrayIcon.MessageType.values())),
                "TrayIcon.MessageType order");

        check(!SystemTray.isSupported(), "no system tray");
        check(!Taskbar.isTaskbarSupported(), "no taskbar");
        check(!Desktop.isDesktopSupported(), "no desktop without a bridge");
        // HeadlessException, the JDK's answer here, is an UnsupportedOperationException too.
        expect(UnsupportedOperationException.class, new Body() { public void run() { Desktop.getDesktop(); } });
        expect(UnsupportedOperationException.class, new Body() { public void run() { SystemTray.getSystemTray(); } });
        expect(UnsupportedOperationException.class, new Body() { public void run() { Taskbar.getTaskbar(); } });

        for (int type = Cursor.DEFAULT_CURSOR; type <= Cursor.MOVE_CURSOR; type++) {
            Cursor cursor = Cursor.getPredefinedCursor(type);
            check(cursor == Cursor.getPredefinedCursor(type), "predefined cursor " + type + " is cached");
            check(cursor.getType() == type, "predefined cursor type " + type);
            check(cursor.getName() != null, "predefined cursor name " + type);
            check(cursor.toString().equals("java.awt.Cursor[" + cursor.getName() + "]"), "cursor toString " + cursor);
        }
        check("Hand Cursor".equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR).getName()), "hand cursor name");
        check(Cursor.getDefaultCursor() == Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR), "default cursor");
        expect(IllegalArgumentException.class, new Body() { public void run() { new Cursor(14); } });
        expect(IllegalArgumentException.class, new Body() { public void run() { Cursor.getPredefinedCursor(-1); } });
        System.out.println("PASS: Desktop.Action/Taskbar/MessageType tables, unsupported tray and taskbar, predefined cursors");

        if (args.length > 0 && args[0].equals("shadow")) {
            ShadowChecks.run();
        }
    }

    /** Shadow-only API; never loaded in the JDK run, where its bridge types do not exist. */
    static final class ShadowChecks {
        static void run() throws Exception {
            desktop();
            trayIcon();
            systemTray();
            cursors();
        }

        static void desktop() throws Exception {
            final Recorder recorder = new Recorder();
            Desktop.setBridge(recorder);
            try {
                check(Desktop.isDesktopSupported(), "desktop with a bridge");
                final Desktop desktop = Desktop.getDesktop();
                List<String> supported = Arrays.asList("OPEN", "EDIT", "PRINT", "MAIL", "BROWSE");
                for (Desktop.Action action : Desktop.Action.values()) {
                    check(desktop.isSupported(action) == supported.contains(action.name()),
                            "isSupported(" + action + ")");
                }

                desktop.browse(URI.create("https://runelite.net/"));
                desktop.browse(URI.create("market://details?id=x"));
                desktop.mail();
                desktop.mail(URI.create("mailto:someone@example.com"));
                final File file = File.createTempFile("desktop", ".txt");
                file.deleteOnExit();
                desktop.open(file);
                desktop.edit(file);
                desktop.print(file);
                check(Arrays.asList("browse https://runelite.net/", "browse market://details?id=x", "mail mailto:?",
                        "mail mailto:someone@example.com", "open " + file.getPath(), "edit " + file.getPath(),
                        "print " + file.getPath()).equals(recorder.calls), "bridge calls: " + recorder.calls);

                final File missing = new File(file.getPath() + ".missing");
                final File directory = file.getParentFile();
                expect(NullPointerException.class, new Body() { public void run() throws Exception { desktop.browse(null); } });
                expect(NullPointerException.class, new Body() { public void run() throws Exception { desktop.open(null); } });
                expect(NullPointerException.class, new Body() { public void run() throws Exception { desktop.mail(null); } });
                expect(IllegalArgumentException.class, new Body() { public void run() throws Exception { desktop.open(missing); } });
                expect(IllegalArgumentException.class, new Body() { public void run() throws Exception { desktop.edit(missing); } });
                expect(IOException.class, new Body() { public void run() throws Exception { desktop.edit(directory); } });
                expect(IOException.class, new Body() { public void run() throws Exception { desktop.print(directory); } });
                expect(IllegalArgumentException.class, new Body() { public void run() throws Exception {
                    desktop.mail(URI.create("https://runelite.net/")); } });

                expect(UnsupportedOperationException.class, new Body() { public void run() { desktop.setAboutHandler(null); } });
                expect(UnsupportedOperationException.class, new Body() { public void run() { desktop.setQuitHandler(null); } });
                expect(UnsupportedOperationException.class, new Body() { public void run() { desktop.setDefaultMenuBar(null); } });
                expect(UnsupportedOperationException.class, new Body() { public void run() { desktop.browseFileDirectory(file); } });
                expect(UnsupportedOperationException.class, new Body() { public void run() { desktop.moveToTrash(file); } });
                try {
                    desktop.setPreferencesHandler(null);
                    throw new AssertionError("setPreferencesHandler accepted");
                } catch (UnsupportedOperationException expected) {
                    check("The APP_PREFERENCES action is not supported on the current platform!".equals(expected.getMessage()),
                            "JDK message: " + expected.getMessage());
                }
                desktop.addAppEventListener(null);
                desktop.removeAppEventListener(null);
                check(recorder.calls.size() == 7, "rejected calls never reach the bridge");

                Desktop.clearBridge(new Recorder());
                check(Desktop.isDesktopSupported(), "clearBridge keeps a bridge it did not install");
            } finally {
                Desktop.clearBridge(recorder);
            }
            check(!Desktop.isDesktopSupported(), "clearBridge uninstalls");
            System.out.println("PASS: Desktop isSupported table, bridge dispatch, JDK validation, JDK 9 members");
        }

        static void trayIcon() {
            expect(IllegalArgumentException.class, new Body() { public void run() { new TrayIcon(null); } });
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            final TrayIcon icon = new TrayIcon(image, "tip");
            check(icon.getImage() == image && "tip".equals(icon.getToolTip()), "image and tooltip");
            expect(NullPointerException.class, new Body() { public void run() { icon.setImage(null); } });
            check(!icon.isImageAutoSize(), "auto-size off by default");
            icon.setImageAutoSize(true);
            check(icon.isImageAutoSize(), "auto-size round-trips");
            icon.setActionCommand("open");
            check("open".equals(icon.getActionCommand()), "action command");
            check(icon.getPopupMenu() == null, "no popup by default");

            MouseListener mouse = new MouseAdapter() { };
            MouseMotionListener motion = new MouseAdapter() { };
            ActionListener action = new ActionListener() { public void actionPerformed(ActionEvent e) { } };
            icon.addMouseListener(mouse);
            icon.addMouseListener(null);
            icon.addMouseMotionListener(motion);
            icon.addMouseMotionListener(null);
            icon.addActionListener(action);
            icon.addActionListener(null);
            check(Arrays.equals(icon.getMouseListeners(), new MouseListener[]{mouse}), "mouse listeners");
            check(Arrays.equals(icon.getMouseMotionListeners(), new MouseMotionListener[]{motion}), "motion listeners");
            check(Arrays.equals(icon.getActionListeners(), new ActionListener[]{action}), "action listeners");
            icon.removeMouseListener(mouse);
            icon.removeMouseMotionListener(motion);
            icon.removeActionListener(action);
            icon.removeActionListener(null);
            check(icon.getMouseListeners().length == 0 && icon.getMouseMotionListeners().length == 0
                    && icon.getActionListeners().length == 0, "listeners removed");

            expect(NullPointerException.class, new Body() { public void run() {
                icon.displayMessage(null, null, TrayIcon.MessageType.INFO); } });
            icon.displayMessage("no bridge", "is a documented no-op", TrayIcon.MessageType.INFO);

            final List<String> messages = new ArrayList<String>();
            TrayIcon.MessageBridge bridge = new TrayIcon.MessageBridge() {
                public void displayMessage(String caption, String text, TrayIcon.MessageType type) {
                    messages.add(caption + "|" + text + "|" + type);
                }
            };
            TrayIcon.setMessageBridge(bridge);
            try {
                icon.displayMessage("Caption", "Text", TrayIcon.MessageType.WARNING);
                icon.displayMessage(null, "only text", TrayIcon.MessageType.ERROR);
                icon.displayMessage("untyped", null, null);
            } finally {
                TrayIcon.clearMessageBridge(bridge);
            }
            icon.displayMessage("after", "clear", TrayIcon.MessageType.INFO);
            check(Arrays.asList("Caption|Text|WARNING", "null|only text|ERROR", "untyped|null|NONE").equals(messages),
                    "messages: " + messages);
            System.out.println("PASS: TrayIcon listeners, auto-size, validation and message bridge");
        }

        static void systemTray() {
            check(!SystemTray.isSupported(), "tray stays unsupported");
            System.out.println("PASS: SystemTray unsupported");
        }

        static void cursors() throws Exception {
            // Cursor and Toolkit carry JNI-visible layouts and may be demoted to the JDK class in the
            // patched run (--list says why); the shadow divergences are only checkable when patched.
            if (!declares(Cursor.class, "systemCustomCursorNames") || !declares(java.awt.Toolkit.class, "defaultToolkit")) {
                System.out.println("SKIP: Cursor/Toolkit are JDK-backed in this run; system and image cursors unchecked");
                return;
            }
            check(Cursor.getSystemCustomCursor("Invalid.32x32") != null, "Invalid.32x32 system cursor");
            check(Cursor.getSystemCustomCursor("CopyDrop.32x32") == Cursor.getSystemCustomCursor("CopyDrop.32x32"),
                    "system cursors are cached");
            check(Cursor.getSystemCustomCursor("CopyDrop.32x32").getType() == Cursor.CUSTOM_CURSOR, "system cursor type");
            check(Cursor.getSystemCustomCursor("NoSuch.32x32") == null, "unknown system cursor");
            final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Cursor custom = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(image, new java.awt.Point(2, 3), "c");
            check(custom instanceof net.runelite.awt.impl.ImageCursor, "custom cursor carries its image");
            net.runelite.awt.impl.ImageCursor imageCursor = (net.runelite.awt.impl.ImageCursor) custom;
            check(imageCursor.getImage() == image && imageCursor.getHotSpotX() == 2 && imageCursor.getHotSpotY() == 3
                    && custom.getType() == Cursor.CUSTOM_CURSOR && "c".equals(custom.getName()), "custom cursor fields");
            expect(IndexOutOfBoundsException.class, new Body() { public void run() {
                java.awt.Toolkit.getDefaultToolkit().createCustomCursor(image, new java.awt.Point(8, 0), "bad"); } });
            System.out.println("PASS: system custom cursors and image cursors");
        }

        static boolean declares(Class<?> type, String field) {
            try {
                type.getDeclaredField(field);
                return true;
            } catch (NoSuchFieldException e) {
                return false;
            }
        }
    }

    /** Shadow-only too: Desktop.Bridge does not exist in the JDK run. */
    static final class Recorder implements Desktop.Bridge {
        final List<String> calls = new ArrayList<String>();

        public void browse(URI uri) { calls.add("browse " + uri); }

        public void open(File file) { calls.add("open " + file.getPath()); }

        public void edit(File file) { calls.add("edit " + file.getPath()); }

        public void print(File file) { calls.add("print " + file.getPath()); }

        public void mail(URI mailtoURI) { calls.add("mail " + mailtoURI); }
    }

    interface Body {
        void run() throws Exception;
    }

    static void expect(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                return;
            }
            throw new AssertionError("expected " + type.getSimpleName() + ", got " + thrown, thrown);
        }
        throw new AssertionError("expected " + type.getSimpleName() + ", nothing thrown");
    }

    static List<String> names(Enum<?>[] values) {
        List<String> names = new ArrayList<String>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return names;
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
