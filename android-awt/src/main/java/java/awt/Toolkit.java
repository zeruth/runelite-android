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

    public void sync() {}
    public void beep() {}

    public boolean isFrameStateSupported(int state) { return state == Frame.NORMAL; }

    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
        return true;
    }

    public int checkImage(Image image, int width, int height, ImageObserver observer) {
        return ImageObserver.ALLBITS;
    }

    private static final class HeadlessToolkit extends Toolkit {
    }
}
