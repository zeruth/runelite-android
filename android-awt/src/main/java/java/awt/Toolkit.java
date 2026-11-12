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

    protected final java.util.Map<String, Object> desktopProperties = new java.util.HashMap<String, Object>();
    protected final java.beans.PropertyChangeSupport desktopPropsSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Obtains a value for the specified desktop property. There is no native
     * desktop behind the Android toolkit, so only values a subclass loads or
     * sets exist; any other name returns null, as an unknown name does on the JDK.
     */
    public final synchronized Object getDesktopProperty(String propertyName) {
        if (desktopProperties.isEmpty()) {
            initializeDesktopProperties();
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

    protected final void setDesktopProperty(String name, Object newValue) {
        Object oldValue;
        synchronized (this) {
            oldValue = desktopProperties.get(name);
            desktopProperties.put(name, newValue);
        }
        if (oldValue != null || newValue != null) {
            desktopPropsSupport.firePropertyChange(name, oldValue, newValue);
        }
    }

    protected Object lazilyLoadDesktopProperty(String name) {
        return null;
    }

    protected void initializeDesktopProperties() {
    }

    public void addPropertyChangeListener(String name, java.beans.PropertyChangeListener pcl) {
        desktopPropsSupport.addPropertyChangeListener(name, pcl);
    }

    public void removePropertyChangeListener(String name, java.beans.PropertyChangeListener pcl) {
        desktopPropsSupport.removePropertyChangeListener(name, pcl);
    }

    public java.beans.PropertyChangeListener[] getPropertyChangeListeners() {
        return desktopPropsSupport.getPropertyChangeListeners();
    }

    public java.beans.PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return desktopPropsSupport.getPropertyChangeListeners(propertyName);
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

    /**
     * Fills {@code systemColors} with the platform's colors, indexed as the
     * {@link SystemColor} constants. Android has no native widget palette to read, so
     * this is a deliberate no-op that leaves the OpenJDK defaults in place.
     */
    protected void loadSystemColors(int[] systemColors) {
    }

    /**
     * Returns the screen insets of {@code gc}. Android's system bars are handled by
     * the host activity around the game surface, so the AWT screen has none.
     */
    public Insets getScreenInsets(GraphicsConfiguration gc) {
        return new Insets(0, 0, 0, 0);
    }

    /**
     * Gets a {@code PrintJob} for printing on this platform. Android has no AWT print
     * service, so the dialog is answered as cancelled: the spec's {@code null}.
     *
     * @throws NullPointerException if {@code frame} is null
     */
    public PrintJob getPrintJob(Frame frame, String jobtitle, java.util.Properties props) {
        if (frame == null) {
            throw new NullPointerException("frame must not be null");
        }
        return null;
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

    public void sync() {}
    public void beep() {}

    public boolean isFrameStateSupported(int state) { return state == Frame.NORMAL; }

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

    private static final class HeadlessToolkit extends Toolkit {
    }
}
