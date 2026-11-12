// host-contract: patched-args=shadow
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * java.awt.Toolkit over the Android host. Against the plain (headless) JDK only the members that
 * work headless run, and the screen queries must throw HeadlessException; the "shadow" run checks
 * the host-backed values, desktop property events and the documented fixed values.
 */
public final class TestToolkitContracts {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    interface Action { void run() throws Exception; }

    static void expect(Class<? extends Throwable> type, String message, Action action) throws Exception {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return;
            throw new AssertionError(message + ": threw " + thrown, thrown);
        }
        throw new AssertionError(message + ": nothing thrown");
    }

    /** A 2x1 truecolor PNG: one red pixel, one blue pixel. */
    static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream ihdr = new DataOutputStream(header);
        ihdr.writeInt(2); ihdr.writeInt(1); ihdr.write(new byte[]{8, 2, 0, 0, 0});
        chunk(data, "IHDR", header.toByteArray());
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[]{0, (byte) 0xff, 0, 0, 0, 0, (byte) 0xff});
        deflater.finish();
        byte[] buffer = new byte[64];
        int length = deflater.deflate(buffer);
        chunk(data, "IDAT", Arrays.copyOf(buffer, length));
        chunk(data, "IEND", new byte[0]);
        return out.toByteArray();
    }

    static void chunk(DataOutputStream out, String type, byte[] body) throws Exception {
        byte[] name = type.getBytes("US-ASCII");
        CRC32 crc = new CRC32();
        crc.update(name); crc.update(body);
        out.writeInt(body.length); out.write(name); out.write(body); out.writeInt((int) crc.getValue());
    }

    /** Draws the image into an ARGB buffer, waiting for the JDK's asynchronous toolkit images. */
    static BufferedImage pixels(Image image, int width, int height) throws Exception {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        long deadline = System.currentTimeMillis() + 10000;
        while (true) {
            Graphics2D g = target.createGraphics();
            boolean done = g.drawImage(image, 0, 0, null);
            g.dispose();
            if (done) return target;
            check(System.currentTimeMillis() < deadline, "image never finished loading");
            Thread.sleep(10);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean shadow = Arrays.asList(args).contains("shadow");
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        check(toolkit != null && toolkit == Toolkit.getDefaultToolkit(), "getDefaultToolkit is a singleton");

        Image decoded = toolkit.createImage(png());
        check(decoded != null, "createImage(byte[]) returns an image");
        BufferedImage drawn = pixels(decoded, 2, 1);
        check(decoded.getWidth(null) == 2 && decoded.getHeight(null) == 1, "decoded PNG size");
        check(drawn.getRGB(0, 0) == 0xffff0000 && drawn.getRGB(1, 0) == 0xff0000ff, "decoded PNG pixels");
        byte[] padded = png();
        byte[] framed = new byte[padded.length + 4];
        System.arraycopy(padded, 0, framed, 2, padded.length);
        check(pixels(toolkit.createImage(framed, 2, padded.length), 2, 1).getRGB(1, 0) == 0xff0000ff, "createImage(byte[], off, len)");

        Image produced = toolkit.createImage(new MemoryImageSource(1, 1, new int[]{0xff00ff00}, 0, 1));
        check(pixels(produced, 1, 1).getRGB(0, 0) == 0xff00ff00, "createImage(ImageProducer)");

        if (!shadow) {
            if (GraphicsEnvironment.isHeadless()) {
                expect(HeadlessException.class, "headless getScreenSize", toolkit::getScreenSize);
                expect(HeadlessException.class, "headless getScreenInsets", () -> toolkit.getScreenInsets(null));
            }
            System.out.println("PASS: Toolkit singleton and image decoding (headless JDK)");
            return;
        }
        Shadow.run(toolkit, decoded, produced);
    }

    /** Names backend classes; a class of its own so the plain-JDK run never loads (or verifies) it. */
    static final class Shadow {
    static void run(Toolkit toolkit, Image decoded, Image produced) throws Exception {
        check(toolkit instanceof net.runelite.awt.impl.HostToolkit, "the default toolkit is the host toolkit, got " + toolkit.getClass());
        check(toolkit.prepareImage(decoded, -1, -1, null)
            && (toolkit.checkImage(decoded, -1, -1, null) & ImageObserver.ALLBITS) != 0, "decoded images are complete");
        check((toolkit.checkImage(produced, -1, -1, null) & ImageObserver.ALLBITS) != 0, "produced images are complete");

        // Screen metrics and insets come from the host window.
        net.runelite.awt.impl.HeadlessGraphicsConfiguration.updateDisplay(800, 480, 160);
        net.runelite.awt.impl.HeadlessGraphicsConfiguration.updateInsets(24, 0, 48, 10);
        check(toolkit.getScreenSize().equals(new Dimension(800, 480)), "screen size from the host viewport");
        check(toolkit.getScreenResolution() == 160, "screen resolution from the host density");
        GraphicsConfiguration gc = net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE;
        Insets insets = toolkit.getScreenInsets(gc);
        check(insets != null && insets.equals(new Insets(24, 0, 48, 10)), "screen insets from the host window: " + insets);
        check(toolkit.getScreenInsets(null) != null, "getScreenInsets is never null");
        expect(IllegalArgumentException.class, "negative insets", () -> net.runelite.awt.impl.HeadlessGraphicsConfiguration.updateInsets(-1, 0, 0, 0));

        // Desktop properties: defaults, events on the dispatch thread, no event for an equal value.
        check(toolkit.getDesktopProperty("awt.multiClickInterval") instanceof Integer, "awt.multiClickInterval");
        check(toolkit.getDesktopProperty("DnD.gestureMotionThreshold") instanceof Integer, "DnD.gestureMotionThreshold");
        check(Integer.valueOf(1).equals(toolkit.getDesktopProperty("win.text.fontSmoothingType")), "grayscale font smoothing");
        check(toolkit.getDesktopProperty("awt.font.desktophints") instanceof RenderingHints, "desktop font hints");
        check(toolkit.getDesktopProperty("no.such.property") == null, "unknown desktop property");
        List<PropertyChangeEvent> events = new ArrayList<>();
        AtomicInteger offThread = new AtomicInteger();
        PropertyChangeListener listener = event -> {
            if (!EventQueue.isDispatchThread()) offThread.incrementAndGet();
            synchronized (events) { events.add(event); }
        };
        toolkit.addPropertyChangeListener("awt.multiClickInterval", listener);
        check(Arrays.asList(toolkit.getPropertyChangeListeners("awt.multiClickInterval")).contains(listener), "listener registered");
        Object before = toolkit.getDesktopProperty("awt.multiClickInterval");
        net.runelite.awt.impl.HostToolkit.updateDesktopProperty("awt.multiClickInterval", 321);
        net.runelite.awt.impl.HostToolkit.updateDesktopProperty("awt.multiClickInterval", 321);
        net.runelite.awt.impl.HostToolkit.updateDesktopProperty("DnD.gestureMotionThreshold", 9);
        EventQueue.invokeAndWait(() -> { });
        synchronized (events) {
            check(events.size() == 1, "one event for one change of the listened property, got " + events.size());
            PropertyChangeEvent event = events.get(0);
            check(event.getSource() == toolkit && "awt.multiClickInterval".equals(event.getPropertyName())
                && before.equals(event.getOldValue()) && Integer.valueOf(321).equals(event.getNewValue()), "event contents");
        }
        check(offThread.get() == 0, "desktop property listeners run on the event dispatch thread");
        check(Integer.valueOf(321).equals(toolkit.getDesktopProperty("awt.multiClickInterval")), "updated value");
        toolkit.removePropertyChangeListener("awt.multiClickInterval", listener);
        check(toolkit.getPropertyChangeListeners("awt.multiClickInterval").length == 0, "listener removed");

        // Input and window-system queries.
        check(toolkit.getMenuShortcutKeyMaskEx() == InputEvent.CTRL_DOWN_MASK, "menu shortcut is CTRL");
        check(toolkit.areExtraMouseButtonsEnabled(), "extra mouse buttons");
        net.runelite.awt.impl.HostToolkit.updateLockingKeys(true, false, true);
        check(toolkit.getLockingKeyState(KeyEvent.VK_CAPS_LOCK) && !toolkit.getLockingKeyState(KeyEvent.VK_NUM_LOCK)
            && toolkit.getLockingKeyState(KeyEvent.VK_SCROLL_LOCK), "lock keys from the host meta state");
        expect(IllegalArgumentException.class, "non-locking key", () -> toolkit.getLockingKeyState(KeyEvent.VK_A));
        expect(UnsupportedOperationException.class, "set lock key", () -> toolkit.setLockingKeyState(KeyEvent.VK_CAPS_LOCK, false));
        check(toolkit.isFrameStateSupported(Frame.NORMAL) && !toolkit.isAlwaysOnTopSupported(), "frame state and always-on-top");
        check(toolkit.isDynamicLayoutActive(), "dynamic layout active by default");
        toolkit.setDynamicLayout(false);
        check(!toolkit.isDynamicLayoutActive(), "dynamic layout switched off");
        toolkit.setDynamicLayout(true);
        check(toolkit.getSystemEventQueue() != null && toolkit.getSystemEventQueue() == toolkit.getSystemEventQueue(), "system event queue");
        check(toolkit.getSystemClipboard() != null && toolkit.getSystemClipboard() == toolkit.getSystemClipboard(), "system clipboard");
        check(toolkit.getSystemSelection() == null, "no system selection on Android");
        expect(NullPointerException.class, "print job without a frame", () -> toolkit.getPrintJob(null, "job", null));

        // Cursors: any size, full ARGB (PointerIcon bitmaps), named custom cursors, JDK hotspot check.
        check(toolkit.getBestCursorSize(32, 32).equals(new Dimension(32, 32)) && toolkit.getMaximumCursorColors() == 0xFFFFFF, "cursor limits");
        Image cursorImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor cursor = toolkit.createCustomCursor(cursorImage, new Point(3, 4), "pointer");
        check(cursor.getType() == Cursor.CUSTOM_CURSOR && "pointer".equals(cursor.getName()), "custom cursor");
        expect(IndexOutOfBoundsException.class, "hotspot outside the image", () -> toolkit.createCustomCursor(cursorImage, new Point(16, 0), "bad"));

        AtomicInteger beeps = new AtomicInteger();
        net.runelite.awt.impl.HostToolkit.setBeeper(beeps::incrementAndGet);
        toolkit.beep();
        toolkit.sync();
        net.runelite.awt.impl.HostToolkit.setBeeper(null);
        toolkit.beep();
        check(beeps.get() == 1, "beep goes to the host beeper");
        System.out.println("PASS: Toolkit host metrics, desktop properties, images, input state and fixed values");
    }
    }
}
