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
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;

public abstract class Toolkit {
    private static Toolkit defaultToolkit;
    private final EventQueue eventQueue = new EventQueue();
    private final Clipboard clipboard = new Clipboard("system");

    protected Toolkit() {
    }

    public static synchronized Toolkit getDefaultToolkit() {
        if (defaultToolkit == null) {
            defaultToolkit = new HeadlessToolkit();
        }
        return defaultToolkit;
    }

    public Clipboard getSystemClipboard() {
        return clipboard;
    }

    public EventQueue getSystemEventQueue() {
        return eventQueue;
    }

    public Dimension getScreenSize() {
        Rectangle bounds = net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getBounds();
        return new Dimension(bounds.width, bounds.height);
    }

    public int getScreenResolution() {
        return net.runelite.awt.impl.HeadlessGraphicsConfiguration.getResolution();
    }

    public ColorModel getColorModel() {
        return new java.awt.image.DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
    }

    public String[] getFontList() {
        return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
    }

    public Image createImage(int[] pixels, int width, int height) {
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
            width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, width, height, pixels, 0, width);
        return bi;
    }

    /** Decode via ImageIO so callers that go through Toolkit (rather than
     *  ImageIO directly) actually get a populated image. Real Swing/Toolkit
     *  caches by filename + does async loading via ImageObserver; we just
     *  return a fully-decoded BufferedImage synchronously. */
    public Image getImage(String filename) {
        if (filename == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.File(filename)); }
        catch (Throwable ignored) { return null; }
    }

    public Image getImage(java.net.URL url) {
        if (url == null) return null;
        try { return javax.imageio.ImageIO.read(url); }
        catch (Throwable ignored) { return null; }
    }

    public Image createImage(java.awt.image.ImageProducer producer) { return new java.awt.image.ProducerImage(producer); }

    public Image createImage(String filename) { return getImage(filename); }
    public Image createImage(java.net.URL url) { return getImage(url); }
    public Image createImage(byte[] data) {
        if (data == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data)); }
        catch (Throwable ignored) { return null; }
    }
    public Image createImage(byte[] data, int off, int len) {
        if (data == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data, off, len)); }
        catch (Throwable ignored) { return null; }
    }

    /** Android can't host an OS-level cursor — just hand back a Cursor object
     *  so callers that expect a non-null don't NPE. CustomCursorPlugin etc.
     *  used to hit a NoSuchMethodError here and abort plugin startup. */
    public Cursor createCustomCursor(Image image, Point hotspot, String name) {
        return new Cursor(Cursor.CUSTOM_CURSOR);
    }

    public void sync() {}
    public void beep() {}

    public boolean isFrameStateSupported(int state) { return state == Frame.NORMAL; }

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

    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
        return true;
    }

    public int checkImage(Image image, int width, int height, ImageObserver observer) {
        return ImageObserver.ALLBITS;
    }

    private static final class HeadlessToolkit extends Toolkit {
    }
}
