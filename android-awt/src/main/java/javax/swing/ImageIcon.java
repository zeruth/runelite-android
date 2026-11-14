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
package javax.swing;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.Serializable;
import java.net.URL;

public class ImageIcon implements Icon, Serializable {
    private static final long serialVersionUID = 532615968316031762L;

    private Image image;
    private String description;
    private int loadStatus = 0;
    private ImageObserver imageObserver;

    public ImageIcon() {}
    public ImageIcon(Image image) { loadImage(image); }
    public ImageIcon(Image image, String description) { loadImage(image); this.description = description; }
    public ImageIcon(String filename) { loadImage(loadFile(filename)); this.description = filename; }
    public ImageIcon(String filename, String description) { loadImage(loadFile(filename)); this.description = description; }
    public ImageIcon(URL location) { loadImage(loadUrl(location)); this.description = location == null ? null : location.toExternalForm(); }
    public ImageIcon(URL location, String description) { loadImage(loadUrl(location)); this.description = description; }
    public ImageIcon(byte[] imageData) { loadImage(loadBytes(imageData)); }
    public ImageIcon(byte[] imageData, String description) { loadImage(loadBytes(imageData)); this.description = description; }

    /** Loads producer-backed images through the same tracker used by desktop icons. */
    protected void loadImage(Image image) {
        this.image = image;
        if (image == null) {
            loadStatus = MediaTracker.ERRORED;
            return;
        }
        if (image instanceof BufferedImage) {
            loadStatus = MediaTracker.COMPLETE;
            return;
        }
        MediaTracker tracker = new MediaTracker(new Component() {});
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0);
            loadStatus = tracker.statusID(0, false);
        } catch (InterruptedException interrupted) {
            loadStatus = MediaTracker.ABORTED;
            Thread.currentThread().interrupt();
        } finally {
            tracker.removeImage(image, 0);
        }
    }

    /**
     * Returns the status of the image loading operation.
     * @return the loading status as defined by java.awt.MediaTracker
     */
    public int getImageLoadStatus() {
        return loadStatus;
    }

    /**
     * Sets the image observer for the image, used by {@link #paintIcon} in place of
     * the component it paints on.
     */
    public void setImageObserver(ImageObserver observer) {
        imageObserver = observer;
    }

    /** Returns the image observer for the image. */
    public ImageObserver getImageObserver() {
        return imageObserver;
    }

    /** Path / URL / byte-array decodes go through ImageIO. Real Swing also
     *  tolerates a missing file (returns an Icon whose dims are -1); we keep
     *  the same forgiving behaviour so callers that pass a bad path don't
     *  crash — the symptom is just a missing icon. Without these constructors
     *  actually decoding anything, every {@code new ImageIcon(path)} site (and
     *  RuneLite has plenty) silently produced an empty icon. */
    private static Image loadFile(String filename) {
        if (filename == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.File(filename)); }
        catch (Throwable ignored) { return null; }
    }

    private static Image loadUrl(URL location) {
        if (location == null) return null;
        try { return javax.imageio.ImageIO.read(location); }
        catch (Throwable ignored) { return null; }
    }

    private static Image loadBytes(byte[] data) {
        if (data == null || data.length == 0) return null;
        try { return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data)); }
        catch (Throwable ignored) { return null; }
    }

    public Image getImage() { return image; }
    public void setImage(Image image) { loadImage(image); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public int getIconWidth() {
        return image == null ? -1 : image.getWidth(null);
    }

    @Override
    public int getIconHeight() {
        return image == null ? -1 : image.getHeight(null);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (image != null) g.drawImage(image, x, y, imageObserver == null ? c : imageObserver);
    }

    @Override
    public String toString() {
        return description != null ? description : super.toString();
    }
}
