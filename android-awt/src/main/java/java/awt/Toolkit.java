/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.awt;

import java.awt.datatransfer.Clipboard;
import java.awt.event.AWTEventListener;
import java.awt.event.AWTEventListenerProxy;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The AWT toolkit. Members follow the OpenJDK 8 {@code java.awt.Toolkit} contract; the members the
 * JDK leaves abstract are implemented here over the Android backend, and the default toolkit is
 * {@link net.runelite.awt.impl.HostToolkit}, which feeds in the host window state (insets, lock keys,
 * desktop properties, the beeper).
 *
 * <p>Fixed values: {@link #getBestCursorSize} returns the size asked for and
 * {@link #getMaximumCursorColors} is 0xFFFFFF (custom cursors become ARGB PointerIcon bitmaps of
 * any size); because Android has no equivalent, {@link #getSystemSelection} is
 * {@code null} (no primary selection), {@link #getPrintJob} returns {@code null} as if the user
 * cancelled the dialog, {@link #sync} is a no-op (frames are presented by the host view),
 * {@link #isFrameStateSupported} accepts the Windows toolkit's states (NORMAL, ICONIFIED,
 * MAXIMIZED_BOTH), {@link #isAlwaysOnTopSupported}
 * is {@code false} (the activity has no z-order over other apps), and
 * {@link #getMenuShortcutKeyMask} is CTRL as on Windows and Linux.
 */
public abstract class Toolkit {
    /** VK_KANA_LOCK; the shadow KeyEvent does not declare it. */
    private static final int VK_KANA_LOCK = 0x0106;

    private static Toolkit defaultToolkit;
    private final EventQueue eventQueue = new EventQueue();
    private final Clipboard clipboard = new Clipboard("system");
    private final Map<Object, SoftReference<Image>> imageCache = new HashMap<>();
    private boolean dynamicLayout = true;

    /** Desktop property values by name, as in the JDK. */
    protected final Map<String, Object> desktopProperties = new HashMap<>();
    /** Listeners of desktop property changes; the JDK fires them on the event dispatch thread. */
    protected final PropertyChangeSupport desktopPropsSupport = new PropertyChangeSupport(this);

    protected Toolkit() {
    }

    public static synchronized Toolkit getDefaultToolkit() {
        if (defaultToolkit == null) {
            defaultToolkit = new net.runelite.awt.impl.HostToolkit();
        }
        return defaultToolkit;
    }

    /** JDK classes call this from their static initializers; the backend loads its own library in AwtNative. */
    static void loadLibraries() {
    }

    static EventQueue getEventQueue() {
        return getDefaultToolkit().getSystemEventQueueImpl();
    }

    public Clipboard getSystemClipboard() {
        return clipboard;
    }

    /** Android has no primary (middle-click) selection; the JDK returns {@code null} there too. */
    public Clipboard getSystemSelection() {
        return null;
    }

    public final EventQueue getSystemEventQueue() {
        return getSystemEventQueueImpl();
    }

    protected EventQueue getSystemEventQueueImpl() {
        return eventQueue;
    }

    public Dimension getScreenSize() {
        Rectangle bounds = net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getBounds();
        return new Dimension(bounds.width, bounds.height);
    }

    public int getScreenResolution() {
        return net.runelite.awt.impl.HeadlessGraphicsConfiguration.getResolution();
    }

    /** The JDK default: no insets. The host toolkit reports the system bars and the display cutout. */
    public Insets getScreenInsets(GraphicsConfiguration gc) {
        return new Insets(0, 0, 0, 0);
    }

    public ColorModel getColorModel() {
        return new java.awt.image.DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
    }

    @Deprecated
    public String[] getFontList() {
        return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
    }

    /**
     * The same shared, Paint-backed metrics Component and Graphics hand out, so a width
     * measured here matches one measured on the Graphics that paints the text.
     *
     * @deprecated As of JDK version 1.2, replaced by the Font method getLineMetrics.
     */
    @Deprecated
    public FontMetrics getFontMetrics(Font font) {
        return net.runelite.awt.impl.BufferedImageFontMetrics.forFont(font);
    }

    public Image createImage(int[] pixels, int width, int height) {
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
            width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, width, height, pixels, 0, width);
        return bi;
    }

    /**
     * Like the JDK, repeated calls with the same name share one image; unlike the JDK the image is
     * decoded synchronously through ImageIO, so it is complete on return. An unreadable file gives
     * {@code null} where the JDK gives an image that reports ERROR to its observers.
     */
    public Image getImage(String filename) {
        if (filename == null) return null;
        return cached(filename, () -> javax.imageio.ImageIO.read(new java.io.File(filename)));
    }

    public Image getImage(java.net.URL url) {
        if (url == null) return null;
        return cached(url.toString(), () -> javax.imageio.ImageIO.read(url));
    }

    private interface Decoder {
        Image decode() throws java.io.IOException;
    }

    private Image cached(Object key, Decoder decoder) {
        synchronized (imageCache) {
            SoftReference<Image> ref = imageCache.get(key);
            Image image = ref == null ? null : ref.get();
            if (image != null) return image;
        }
        Image image = decode(decoder);
        if (image != null) {
            synchronized (imageCache) { imageCache.put(key, new SoftReference<>(image)); }
        }
        return image;
    }

    private static Image decode(Decoder decoder) {
        try {
            return decoder.decode();
        } catch (java.io.IOException | RuntimeException unreadable) {
            return null;
        }
    }

    public Image createImage(ImageProducer producer) {
        return new java.awt.image.ProducerImage(producer);
    }

    /** Not cached, as in the JDK; decoded synchronously like {@link #getImage(String)}. */
    public Image createImage(String filename) {
        if (filename == null) return null;
        return decode(() -> javax.imageio.ImageIO.read(new java.io.File(filename)));
    }

    public Image createImage(java.net.URL url) {
        if (url == null) return null;
        return decode(() -> javax.imageio.ImageIO.read(url));
    }

    public Image createImage(byte[] data) {
        if (data == null) return null;
        return createImage(data, 0, data.length);
    }

    public Image createImage(byte[] data, int off, int len) {
        if (data == null) return null;
        return decode(() -> javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data, off, len)));
    }

    /** Creates a cursor from an image; the host maps it to a bitmap
     *  PointerIcon for mice and styluses. Not headless-checked: the shadow
     *  GraphicsEnvironment always reports headless, and CustomCursorPlugin
     *  must start. The hotspot check is sun.awt.CustomCursor's. */
    public Cursor createCustomCursor(Image cursor, Point hotSpot, String name)
        throws IndexOutOfBoundsException, HeadlessException {
        int width = cursor.getWidth(null);
        int height = cursor.getHeight(null);
        // A not-yet-loaded image reports -1; its size is unknown, as is the hotspot's validity.
        if (width >= 0 && height >= 0
                && (hotSpot.x >= width || hotSpot.y >= height || hotSpot.x < 0 || hotSpot.y < 0)) {
            throw new IndexOutOfBoundsException("invalid hotSpot");
        }
        return new net.runelite.awt.impl.ImageCursor(cursor, hotSpot, name);
    }

    /** Returns the size asked for: PointerIcon takes a bitmap of any size. */
    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight) throws HeadlessException {
        return new Dimension(preferredWidth, preferredHeight);
    }

    /** Custom cursor bitmaps are full ARGB on Android. */
    public int getMaximumCursorColors() throws HeadlessException {
        return 0xFFFFFF;
    }

    /**
     * Fills {@code systemColors} with the platform's colors, indexed as the
     * {@link SystemColor} constants. Android has no native widget palette to read, so
     * this is a deliberate no-op that leaves the OpenJDK defaults in place.
     */
    protected void loadSystemColors(int[] systemColors) {
    }

    /**
     * Gets a {@code PrintJob} with the given job and page attributes; delegates to the
     * {@code Properties} form, as 8u's default does.
     */
    public PrintJob getPrintJob(Frame frame, String jobtitle,
                                JobAttributes jobAttributes,
                                PageAttributes pageAttributes) {
        if (this != Toolkit.getDefaultToolkit()) {
            return Toolkit.getDefaultToolkit().getPrintJob(frame, jobtitle,
                                                           jobAttributes,
                                                           pageAttributes);
        } else {
            return getPrintJob(frame, jobtitle, null);
        }
    }

    /** The states the Windows toolkit supports; a lone horizontal or vertical maximize is not one. */
    public boolean isFrameStateSupported(int state) {
        return state == Frame.NORMAL || state == Frame.ICONIFIED || state == Frame.MAXIMIZED_BOTH;
    }

    /** Every modality is honoured: the host shows one window at a time over the game. */
    public boolean isModalityTypeSupported(Dialog.ModalityType modalityType) { return modalityType != null; }
    public boolean isModalExclusionTypeSupported(Dialog.ModalExclusionType modalExclusionType) {
        return modalExclusionType != null;
    }
    /** Android windows are stacked by the activity; none can float above other apps. */
    public boolean isAlwaysOnTopSupported() { return false; }

    // ---- AWTEventListeners ---------------------------------------------------------------
    // Global taps on every event dispatched to a component (RuneLite's and plugins' idle and
    // activity trackers use them). Registration is rare and dispatch is per pointer sample, so
    // dispatch reads a copy-on-write snapshot without locking.

    private static final class SelectiveListener {
        final AWTEventListener listener;
        long eventMask;
        SelectiveListener(AWTEventListener listener, long eventMask) {
            this.listener = listener;
            this.eventMask = eventMask;
        }
    }

    private static final SelectiveListener[] NO_LISTENERS = new SelectiveListener[0];
    private volatile SelectiveListener[] awtEventListeners = NO_LISTENERS;
    private static volatile long enabledOnToolkitMask;

    /**
     * Adds a listener for every dispatched event whose type is in {@code eventMask}. Adding
     * a listener again widens its mask; a null listener or a zero mask is ignored.
     */
    public void addAWTEventListener(AWTEventListener listener, long eventMask) {
        if (listener == null || eventMask == 0) return;
        synchronized (this) {
            SelectiveListener[] current = awtEventListeners;
            for (SelectiveListener selective : current) {
                if (selective.listener == listener) {
                    selective.eventMask |= eventMask;
                    updateEnabledMask(current);
                    return;
                }
            }
            SelectiveListener[] next = java.util.Arrays.copyOf(current, current.length + 1);
            next[current.length] = new SelectiveListener(listener, eventMask);
            awtEventListeners = next;
            updateEnabledMask(next);
        }
    }

    /** Removes every registration of the listener, whatever masks it was added with. */
    public void removeAWTEventListener(AWTEventListener listener) {
        if (listener == null) return;
        synchronized (this) {
            SelectiveListener[] current = awtEventListeners;
            for (int i = 0; i < current.length; i++) {
                if (current[i].listener == listener) {
                    SelectiveListener[] next = new SelectiveListener[current.length - 1];
                    System.arraycopy(current, 0, next, 0, i);
                    System.arraycopy(current, i + 1, next, i, current.length - i - 1);
                    awtEventListeners = next.length == 0 ? NO_LISTENERS : next;
                    updateEnabledMask(next);
                    return;
                }
            }
        }
    }

    /** Every registered listener, each wrapped in an {@link AWTEventListenerProxy} carrying its mask. */
    public AWTEventListener[] getAWTEventListeners() {
        SelectiveListener[] current = awtEventListeners;
        AWTEventListener[] result = new AWTEventListener[current.length];
        for (int i = 0; i < current.length; i++) {
            result[i] = new AWTEventListenerProxy(current[i].eventMask, current[i].listener);
        }
        return result;
    }

    /** The listeners whose mask includes every bit of {@code eventMask}, as proxies. */
    public AWTEventListener[] getAWTEventListeners(long eventMask) {
        java.util.List<AWTEventListener> result = new java.util.ArrayList<>();
        for (SelectiveListener selective : awtEventListeners) {
            if ((selective.eventMask & eventMask) == eventMask) {
                result.add(new AWTEventListenerProxy(selective.eventMask, selective.listener));
            }
        }
        return result.toArray(new AWTEventListener[0]);
    }

    private static void updateEnabledMask(SelectiveListener[] listeners) {
        long mask = 0;
        for (SelectiveListener selective : listeners) mask |= selective.eventMask;
        enabledOnToolkitMask = mask;
    }

    /** True when some AWTEventListener wants events of this mask (Container uses it for ContainerEvents). */
    static boolean enabledOnToolkit(long eventMask) {
        return (enabledOnToolkitMask & eventMask) != 0;
    }

    /** Called by {@link Component#dispatchEvent} for every event dispatched to a component. */
    void notifyAWTEventListeners(AWTEvent event) {
        SelectiveListener[] current = awtEventListeners;
        if (current.length == 0) return;
        long eventBit = maskOf(event.getID());
        if (eventBit == 0) return;
        for (SelectiveListener selective : current) {
            if ((selective.eventMask & eventBit) != 0) selective.listener.eventDispatched(event);
        }
    }

    /** The AWTEvent mask an event id belongs to, as the JDK's selective listener tests it; 0 for none. */
    private static long maskOf(int id) {
        if (id >= java.awt.event.ComponentEvent.COMPONENT_FIRST && id <= java.awt.event.ComponentEvent.COMPONENT_LAST)
            return AWTEvent.COMPONENT_EVENT_MASK;
        if (id >= java.awt.event.ContainerEvent.CONTAINER_FIRST && id <= java.awt.event.ContainerEvent.CONTAINER_LAST)
            return AWTEvent.CONTAINER_EVENT_MASK;
        if (id >= java.awt.event.FocusEvent.FOCUS_FIRST && id <= java.awt.event.FocusEvent.FOCUS_LAST)
            return AWTEvent.FOCUS_EVENT_MASK;
        if (id >= java.awt.event.KeyEvent.KEY_FIRST && id <= java.awt.event.KeyEvent.KEY_LAST)
            return AWTEvent.KEY_EVENT_MASK;
        if (id == java.awt.event.MouseEvent.MOUSE_WHEEL)
            return AWTEvent.MOUSE_WHEEL_EVENT_MASK;
        if (id == java.awt.event.MouseEvent.MOUSE_MOVED || id == java.awt.event.MouseEvent.MOUSE_DRAGGED)
            return AWTEvent.MOUSE_MOTION_EVENT_MASK;
        if (id >= java.awt.event.MouseEvent.MOUSE_FIRST && id <= java.awt.event.MouseEvent.MOUSE_LAST)
            return AWTEvent.MOUSE_EVENT_MASK;
        if (id == java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS || id == java.awt.event.WindowEvent.WINDOW_LOST_FOCUS)
            return AWTEvent.WINDOW_FOCUS_EVENT_MASK;
        if (id == java.awt.event.WindowEvent.WINDOW_STATE_CHANGED)
            return AWTEvent.WINDOW_STATE_EVENT_MASK;
        if (id >= java.awt.event.WindowEvent.WINDOW_FIRST && id <= java.awt.event.WindowEvent.WINDOW_LAST)
            return AWTEvent.WINDOW_EVENT_MASK;
        if (id >= java.awt.event.ActionEvent.ACTION_FIRST && id <= java.awt.event.ActionEvent.ACTION_LAST)
            return AWTEvent.ACTION_EVENT_MASK;
        if (id >= java.awt.event.AdjustmentEvent.ADJUSTMENT_FIRST && id <= java.awt.event.AdjustmentEvent.ADJUSTMENT_LAST)
            return AWTEvent.ADJUSTMENT_EVENT_MASK;
        if (id >= java.awt.event.ItemEvent.ITEM_FIRST && id <= java.awt.event.ItemEvent.ITEM_LAST)
            return AWTEvent.ITEM_EVENT_MASK;
        if (id >= java.awt.event.TextEvent.TEXT_FIRST && id <= java.awt.event.TextEvent.TEXT_LAST)
            return AWTEvent.TEXT_EVENT_MASK;
        if (id >= java.awt.event.InputMethodEvent.INPUT_METHOD_FIRST && id <= java.awt.event.InputMethodEvent.INPUT_METHOD_LAST)
            return AWTEvent.INPUT_METHOD_EVENT_MASK;
        if (id >= java.awt.event.PaintEvent.PAINT_FIRST && id <= java.awt.event.PaintEvent.PAINT_LAST)
            return AWTEvent.PAINT_EVENT_MASK;
        if (id >= java.awt.event.InvocationEvent.INVOCATION_FIRST && id <= java.awt.event.InvocationEvent.INVOCATION_LAST)
            return AWTEvent.INVOCATION_EVENT_MASK;
        if (id == java.awt.event.HierarchyEvent.HIERARCHY_CHANGED)
            return AWTEvent.HIERARCHY_EVENT_MASK;
        if (id == java.awt.event.HierarchyEvent.ANCESTOR_MOVED || id == java.awt.event.HierarchyEvent.ANCESTOR_RESIZED)
            return AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK;
        return 0;
    }

    /**
     * As SunToolkit: a zero-sized request, or an image that is not producer-backed
     * (BufferedImage, VolatileImage), is ready; a produced image starts production and
     * reports ALLBITS, notifying {@code observer} of ERROR|ABORT when it failed.
     */
    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
        if (width == 0 || height == 0) {
            return true;
        }
        if (!(image instanceof java.awt.image.ProducerImage)) {
            return true;
        }
        return ((java.awt.image.ProducerImage) image).prepare(observer);
    }

    /** As SunToolkit: the ImageObserver flags of a produced image, ALLBITS for any other. */
    public int checkImage(Image image, int width, int height, ImageObserver observer) {
        if (!(image instanceof java.awt.image.ProducerImage)) {
            return ImageObserver.ALLBITS;
        }
        int flags = ((java.awt.image.ProducerImage) image).check(observer);
        return (width == 0 || height == 0) ? flags | ImageObserver.ALLBITS : flags;
    }

    /** Deliberate no-op: there is no window system command buffer; the host view presents every frame. */
    public void sync() {
    }

    /** No-op on the base class; the host toolkit plays a tone. */
    public void beep() {
    }

    @Deprecated
    public int getMenuShortcutKeyMask() {
        return InputEvent.CTRL_MASK;
    }

    // JDK 10
    public int getMenuShortcutKeyMaskEx() {
        return InputEvent.CTRL_DOWN_MASK;
    }

    /** {@code null}, the JDK's result when the user cancels the print dialog: Android has no AWT printing. */
    public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
        if (frame == null) throw new NullPointerException("frame must not be null");
        return null;
    }

    public void setDynamicLayout(boolean dynamic) {
        synchronized (this) { dynamicLayout = dynamic; }
    }

    protected boolean isDynamicLayoutSet() {
        synchronized (this) { return dynamicLayout; }
    }

    public boolean isDynamicLayoutActive() {
        return isDynamicLayoutSet() && Boolean.TRUE.equals(getDesktopProperty("awt.dynamicLayoutSupported"));
    }

    /** The JDK default: the lock keys are validated and their state is not available. */
    public boolean getLockingKeyState(int keyCode) {
        checkLockingKey(keyCode, "Toolkit.getLockingKeyState");
        throw new UnsupportedOperationException("Toolkit.getLockingKeyState");
    }

    public void setLockingKeyState(int keyCode, boolean on) {
        checkLockingKey(keyCode, "Toolkit.setLockingKeyState");
        throw new UnsupportedOperationException("Toolkit.setLockingKeyState");
    }

    protected static void checkLockingKey(int keyCode, String member) {
        if (keyCode != KeyEvent.VK_CAPS_LOCK && keyCode != KeyEvent.VK_NUM_LOCK
                && keyCode != KeyEvent.VK_SCROLL_LOCK && keyCode != VK_KANA_LOCK) {
            throw new IllegalArgumentException("invalid key for " + member);
        }
    }

    /** Android mice report back and forward; honours the JDK's sun.awt.enableExtraMouseButtons switch. */
    public boolean areExtraMouseButtonsEnabled() {
        return Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons", "true"));
    }

    public final synchronized Object getDesktopProperty(String propertyName) {
        if (desktopProperties.isEmpty()) {
            initializeDesktopProperties();
        }
        if ("awt.dynamicLayoutSupported".equals(propertyName)) {
            return getDefaultToolkit().lazilyLoadDesktopProperty(propertyName);
        }
        Object value = desktopProperties.get(propertyName);
        if (value == null) {
            value = lazilyLoadDesktopProperty(propertyName);
            if (value != null) {
                setDesktopProperty(propertyName, value);
            }
        }
        if (value instanceof RenderingHints) {
            value = ((RenderingHints) value).clone();
        }
        return value;
    }

    /**
     * Stores the value and, when it changed, notifies the listeners on the event dispatch thread,
     * as the JDK's DesktopPropertyChangeSupport does.
     */
    protected final void setDesktopProperty(String name, Object newValue) {
        Object oldValue;
        synchronized (this) {
            oldValue = desktopProperties.get(name);
            desktopProperties.put(name, newValue);
        }
        if (oldValue == null && newValue == null || oldValue != null && oldValue.equals(newValue)) {
            return;
        }
        PropertyChangeEvent event = new PropertyChangeEvent(this, name, oldValue, newValue);
        EventQueue.invokeLater(() -> desktopPropsSupport.firePropertyChange(event));
    }

    protected Object lazilyLoadDesktopProperty(String name) {
        return null;
    }

    protected void initializeDesktopProperties() {
    }

    public void addPropertyChangeListener(String name, PropertyChangeListener pcl) {
        desktopPropsSupport.addPropertyChangeListener(name, pcl);
    }

    public void removePropertyChangeListener(String name, PropertyChangeListener pcl) {
        desktopPropsSupport.removePropertyChangeListener(name, pcl);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return desktopPropsSupport.getPropertyChangeListeners();
    }

    public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return desktopPropsSupport.getPropertyChangeListeners(propertyName);
    }
}
