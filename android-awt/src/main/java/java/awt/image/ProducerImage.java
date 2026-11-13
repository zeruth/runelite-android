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
package java.awt.image;

import java.awt.Graphics;
import java.awt.Image;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The shadow's stand-in for the JDK's {@code sun.awt.image.ToolkitImage} +
 * {@code ImageRepresentation}: the image {@code Toolkit.createImage(ImageProducer)} and
 * {@code Component.createImage(ImageProducer)} return. It consumes its producer into an
 * ARGB {@link BufferedImage} and keeps the {@link ImageObserver} flags the JDK reports
 * (WIDTH, HEIGHT, PROPERTIES, SOMEBITS, FRAMEBITS, ALLBITS, ERROR, ABORT), which is what
 * {@code Toolkit.checkImage}/{@code prepareImage} and {@code MediaTracker} read.
 *
 * <p>Production starts at construction rather than at first use: every producer the game and
 * plugins hand in is synchronous (memory, filtered, off-screen sources), and RuneLite reads
 * {@code getWidth(null)} right after {@code getScaledInstance} without an observer. After
 * {@link #flush()} the next request reproduces the image, as the JDK does.
 */
public final class ProducerImage extends Image implements ImageConsumer {
    private static final int DONE = ImageObserver.ALLBITS | ImageObserver.ERROR | ImageObserver.ABORT;

    private final ImageProducer producer;
    private final List<ImageObserver> observers = new CopyOnWriteArrayList<>();
    private BufferedImage image;
    private int width = -1;
    private int height = -1;
    private int availinfo;
    private boolean producing;
    private Hashtable<?, ?> properties;

    public ProducerImage(ImageProducer producer) {
        this.producer = java.util.Objects.requireNonNull(producer);
        load();
    }

    private void load() {
        synchronized (this) {
            if (producing || (availinfo & (ImageObserver.ALLBITS | ImageObserver.ERROR)) != 0) {
                return;
            }
            producing = true;
            availinfo &= ~ImageObserver.ABORT;
        }
        producer.startProduction(this);
    }

    /** The decoded pixels, or null until the producer has delivered its dimensions. */
    public BufferedImage getBufferedImage() {
        load();
        synchronized (this) {
            return image;
        }
    }

    public ImageProducer getSource() {
        return producer;
    }

    private void watch(ImageObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public int getWidth(ImageObserver observer) {
        synchronized (this) {
            if ((availinfo & ImageObserver.WIDTH) != 0) {
                return width;
            }
            watch(observer);
        }
        load();
        synchronized (this) {
            return (availinfo & ImageObserver.WIDTH) != 0 ? width : -1;
        }
    }

    public int getHeight(ImageObserver observer) {
        synchronized (this) {
            if ((availinfo & ImageObserver.HEIGHT) != 0) {
                return height;
            }
            watch(observer);
        }
        load();
        synchronized (this) {
            return (availinfo & ImageObserver.HEIGHT) != 0 ? height : -1;
        }
    }

    /** As the JDK's ToolkitImage: a produced image is not a drawing surface. */
    public Graphics getGraphics() {
        throw new UnsupportedOperationException("getGraphics() not valid for images "
            + "created with createImage(producer)");
    }

    @Override
    public Object getProperty(String name, ImageObserver observer) {
        if (name == null) {
            throw new NullPointerException("null property name is not allowed");
        }
        Hashtable<?, ?> props;
        synchronized (this) {
            props = properties;
            if (props == null) {
                watch(observer);
            }
        }
        if (props == null) {
            load();
            synchronized (this) {
                props = properties;
            }
            if (props == null) {
                return null;
            }
        }
        Object o = props.get(name);
        return o == null ? UndefinedProperty : o;
    }

    /** The ImageObserver flags known so far; registers the observer while loading. */
    public int check(ImageObserver observer) {
        synchronized (this) {
            if ((availinfo & DONE) == 0) {
                watch(observer);
            }
            return availinfo;
        }
    }

    /**
     * Starts production if needed and reports whether the image is complete, the contract
     * of {@code Toolkit.prepareImage}; an errored image notifies the observer of ERROR|ABORT.
     */
    public boolean prepare(ImageObserver observer) {
        boolean error;
        synchronized (this) {
            error = (availinfo & ImageObserver.ERROR) != 0;
            if (!error && (availinfo & ImageObserver.ALLBITS) == 0) {
                watch(observer);
            }
        }
        if (error) {
            if (observer != null) {
                observer.imageUpdate(this, ImageObserver.ERROR | ImageObserver.ABORT, -1, -1, -1, -1);
            }
            return false;
        }
        load();
        synchronized (this) {
            return (availinfo & ImageObserver.ALLBITS) != 0;
        }
    }

    public boolean hasError() {
        synchronized (this) {
            return (availinfo & ImageObserver.ERROR) != 0;
        }
    }

    public void setDimensions(int w, int h) {
        if (w <= 0 || h <= 0) {
            imageComplete(IMAGEERROR);
            return;
        }
        synchronized (this) {
            if (image == null || image.getWidth() != w || image.getHeight() != h) {
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
            width = w;
            height = h;
            availinfo |= ImageObserver.WIDTH | ImageObserver.HEIGHT;
        }
        notifyObservers(ImageObserver.WIDTH | ImageObserver.HEIGHT, 0, 0, w, h);
    }

    public void setColorModel(ColorModel model) {
        // Pixels are converted per call with the model setPixels names, as ImageRepresentation does.
    }

    public void setHints(int hints) {
        // Delivery order does not matter: each setPixels call lands at its own coordinates.
    }

    public void setProperties(Hashtable<?, ?> props) {
        synchronized (this) {
            properties = props == null ? new Hashtable<String, Object>() : props;
            availinfo |= ImageObserver.PROPERTIES;
        }
        notifyObservers(ImageObserver.PROPERTIES, 0, 0, width, height);
    }

    public void setPixels(int x, int y, int w, int h, ColorModel cm, int[] pixels, int off, int scan) {
        store(x, y, w, h, cm, pixels, null, off, scan);
    }

    public void setPixels(int x, int y, int w, int h, ColorModel cm, byte[] pixels, int off, int scan) {
        store(x, y, w, h, cm, null, pixels, off, scan);
    }

    private void store(int x, int y, int w, int h, ColorModel cm, int[] ints, byte[] bytes, int off, int scan) {
        int x0, y0, x1, y1;
        synchronized (this) {
            if (image == null) {
                return;
            }
            // Clip to the image, as ImageRepresentation.setPixels does.
            x0 = Math.max(x, 0);
            y0 = Math.max(y, 0);
            x1 = Math.min(x + w, width);
            y1 = Math.min(y + h, height);
            if (x0 >= x1 || y0 >= y1) {
                return;
            }
            int[] row = new int[x1 - x0];
            boolean argb = cm == ColorModel.getRGBdefault() && ints != null;
            for (int py = y0; py < y1; py++) {
                int index = off + (py - y) * scan + (x0 - x);
                if (argb) {
                    System.arraycopy(ints, index, row, 0, row.length);
                } else if (ints != null) {
                    for (int i = 0; i < row.length; i++) {
                        row[i] = cm.getRGB(ints[index + i]);
                    }
                } else {
                    for (int i = 0; i < row.length; i++) {
                        row[i] = cm.getRGB(bytes[index + i] & 0xff);
                    }
                }
                image.setRGB(x0, py, row.length, 1, row, 0, row.length);
            }
            availinfo |= ImageObserver.SOMEBITS;
        }
        notifyObservers(ImageObserver.SOMEBITS, x0, y0, x1 - x0, y1 - y0);
    }

    public void imageComplete(int status) {
        int flags;
        boolean done;
        switch (status) {
        case STATICIMAGEDONE:
            flags = ImageObserver.ALLBITS;
            done = true;
            break;
        case SINGLEFRAMEDONE:
            flags = ImageObserver.FRAMEBITS;
            done = false;
            break;
        case IMAGEABORTED:
            flags = ImageObserver.ABORT;
            done = true;
            break;
        default:
            flags = ImageObserver.ERROR | ImageObserver.ABORT;
            done = true;
            break;
        }
        synchronized (this) {
            availinfo |= flags;
            if (done) {
                producing = false;
            }
        }
        if (done) {
            // A memory source that still sees its consumer after STATICIMAGEDONE reports IMAGEERROR.
            producer.removeConsumer(this);
        }
        notifyObservers(flags, 0, 0, width, height);
    }

    private void notifyObservers(int flags, int x, int y, int w, int h) {
        boolean last = (flags & DONE) != 0;
        for (ImageObserver observer : observers) {
            if (!observer.imageUpdate(this, flags, x, y, w, h) || last) {
                observers.remove(observer);
            }
        }
    }

    /** Drops the pixels; the next request reproduces them from the source. */
    @Override
    public void flush() {
        synchronized (this) {
            image = null;
            width = -1;
            height = -1;
            availinfo = 0;
            properties = null;
            producing = false;
        }
        producer.removeConsumer(this);
    }
}
