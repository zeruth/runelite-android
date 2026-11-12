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

import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Canvas shadow. The patched RS client paints its game viewport into a {@link Canvas},
 * so we back every instance with a {@link BufferedImage} backbuffer. {@link #getGraphics()}
 * returns a {@code Graphics2D} writing into that buffer; the most recently active canvas
 * is exposed via {@link #latest()} so the Compose host can read pixels and draw them.
 */
public class Canvas extends Component implements javax.accessibility.Accessible {
    private static final long serialVersionUID = -2284879212465893870L;

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleAWTCanvas();
        }
        return accessibleContext;
    }

    /** Accessibility support for canvases: role CANVAS. */
    protected class AccessibleAWTCanvas extends AccessibleAWTComponent {
        private static final long serialVersionUID = -6325592262103146699L;

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.CANVAS;
        }
    }
    private static final AtomicReference<Canvas> LATEST = new AtomicReference<>();

    /**
     * Set when GpuGlesPlugin has taken over rendering. The Compose host then layers an
     * EGL-backed SurfaceView at this Canvas's absolute window rect and the host paint
     * walk skips the Canvas's CPU backbuffer — instead clearing its rect to alpha=0 so
     * the SurfaceView shows through the chrome bitmap.
     */
    private static volatile boolean RENDERED_BY_GLES = false;
    public static void setRenderedByGles(boolean v) { RENDERED_BY_GLES = v; }
    public static boolean isRenderedByGles() { return RENDERED_BY_GLES; }

    // volatile: getGraphics() hands out a graphics that reads this live (see below), and
    // ensureBackbuffer() may reallocate it from a different thread (Compose layout) than the
    // one drawing (the client/game thread).
    private volatile BufferedImage backbuffer;

    public Canvas() {
        LATEST.set(this);
    }

    public Canvas(GraphicsConfiguration config) {
        LATEST.set(this);
    }

    /** Most-recently-instantiated canvas — the Compose host pulls pixels from here. */
    public static Canvas latest() {
        return LATEST.get();
    }

    /** Absolute origin + size of this Canvas within its top-level Window. Returns null
     *  if not currently parented. Used by the GLES host to position the SurfaceView. */
    public Rectangle getBoundsInWindow() {
        int ax = this.x, ay = this.y;
        Component p = getParent();
        while (p != null && !(p instanceof Window)) {
            ax += p.getX();
            ay += p.getY();
            p = p.getParent();
        }
        if (p == null) return null;
        return new Rectangle(ax, ay, width, height);
    }

    public BufferedImage getBackbuffer() {
        return backbuffer;
    }

    private void ensureBackbuffer() {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (backbuffer == null || backbuffer.getWidth() != w || backbuffer.getHeight() != h) {
            backbuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
    }

    @Override
    public void setSize(int w, int h) {
        super.setSize(w, h);
        ensureBackbuffer();
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        ensureBackbuffer();
    }

    @Override
    public Graphics getGraphics() {
        ensureBackbuffer();
        // Bind the graphics to the LIVE backbuffer, not a snapshot. The OSRS client caches
        // this graphics (MainBufferProvider.ln_fld) and reuses it across frames; if it
        // snapshotted the current backbuffer, a resize that reallocates the backbuffer would
        // orphan it — the client would keep painting into the dead buffer while the visible
        // one stays black. The supplier re-resolves the current backbuffer on each draw.
        return new net.runelite.awt.impl.BufferedImageGraphics2D(() -> backbuffer);
    }

    private volatile BufferStrategy bufferStrategy;

    /**
     * Creates a new strategy for multi-buffering on this component. Page flipping is
     * unavailable, so this is the desktop fallback chain's last step: a blitted,
     * unaccelerated strategy (a single-buffered one for {@code numBuffers == 1}).
     *
     * @param numBuffers number of buffers to create, including the front buffer
     * @exception IllegalArgumentException if numBuffers is less than 1.
     * @since 1.4
     */
    public void createBufferStrategy(int numBuffers) {
        try {
            createBufferStrategy(numBuffers, getDefaultBufferCapabilities());
        } catch (AWTException e) {
            // Unflipped, unaccelerated capabilities are always satisfiable.
            throw new InternalError("Could not create a buffer strategy", e);
        }
    }

    /**
     * Creates a new strategy for multi-buffering on this component with the required
     * buffer capabilities.
     *
     * @param numBuffers number of buffers to create
     * @param caps the required capabilities for creating the buffer strategy;
     * cannot be <code>null</code>
     * @exception AWTException if the capabilities supplied could not be supported or met
     * (page flipping is never supported here)
     * @exception IllegalArgumentException if numBuffers is less than 1, or if caps is
     * <code>null</code>
     * @since 1.4
     */
    public void createBufferStrategy(int numBuffers, BufferCapabilities caps) throws AWTException {
        if (numBuffers < 1) {
            throw new IllegalArgumentException("Number of buffers must be at least 1");
        }
        if (caps == null) {
            throw new IllegalArgumentException("No capabilities specified");
        }
        if (caps.isPageFlipping()) {
            throw new AWTException("Page flipping is not supported");
        }
        BufferStrategy previous = bufferStrategy;
        if (previous != null) {
            previous.dispose();
        }
        bufferStrategy = numBuffers == 1
            ? new SingleBufferStrategy(caps)
            : new BltBufferStrategy(caps);
    }

    /**
     * Returns the <code>BufferStrategy</code> used by this component.
     *
     * @return the buffer strategy used by this component, or null if none has been
     * created
     * @since 1.4
     */
    public BufferStrategy getBufferStrategy() {
        return bufferStrategy;
    }

    private static BufferCapabilities getDefaultBufferCapabilities() {
        ImageCapabilities caps = new ImageCapabilities(false);
        return new BufferCapabilities(caps, caps, null);
    }

    /** Draws straight into the backbuffer the host displays; {@code show()} has nothing to do. */
    private final class SingleBufferStrategy extends BufferStrategy {
        private final BufferCapabilities caps;

        SingleBufferStrategy(BufferCapabilities caps) {
            this.caps = caps;
        }

        @Override public BufferCapabilities getCapabilities() { return caps; }
        @Override public Graphics getDrawGraphics() { return Canvas.this.getGraphics(); }
        @Override public boolean contentsLost() { return false; }
        @Override public boolean contentsRestored() { return false; }
        @Override public void show() { }

        @Override
        public void dispose() {
            if (bufferStrategy == this) {
                bufferStrategy = null;
            }
        }
    }

    /**
     * Renders into a volatile back buffer sized to the canvas; {@code show()} copies it
     * into the backbuffer the host displays, so a frame becomes visible whole, as with
     * desktop's BltBufferStrategy.
     */
    private final class BltBufferStrategy extends BufferStrategy {
        private final BufferCapabilities caps;
        private java.awt.image.VolatileImage back;
        private boolean restored;

        BltBufferStrategy(BufferCapabilities caps) {
            this.caps = caps;
            validateBack();
        }

        /**
         * Recreates the back buffer on a resize. As on desktop, a recreated or restored
         * buffer is what {@code contentsRestored()} reports until the next
         * {@code getDrawGraphics()}; the first buffer is not "restored".
         */
        private synchronized void validateBack() {
            int w = Math.max(1, width);
            int h = Math.max(1, height);
            boolean first = back == null;
            restored = false;
            if (first || back.getWidth() != w || back.getHeight() != h) {
                if (!first) {
                    back.flush();
                }
                back = new net.runelite.awt.impl.BufferedBackedVolatileImage(w, h, Transparency.OPAQUE);
                restored = !first;
            } else if (back.validate(null) == java.awt.image.VolatileImage.IMAGE_RESTORED) {
                restored = true;
            }
        }

        @Override public BufferCapabilities getCapabilities() { return caps; }

        @Override
        public synchronized Graphics getDrawGraphics() {
            validateBack();
            return back.getGraphics();
        }

        @Override
        public synchronized boolean contentsLost() {
            return back == null || back.contentsLost();
        }

        @Override
        public synchronized boolean contentsRestored() {
            return restored;
        }

        @Override
        public void show() {
            java.awt.image.VolatileImage image;
            synchronized (this) {
                image = back;
            }
            if (image == null) {
                return;
            }
            ensureBackbuffer();
            Graphics2D g = backbuffer.createGraphics();
            try {
                // SRC: the copy replaces the front buffer, as a blit does on desktop.
                g.setComposite(AlphaComposite.Src);
                // The live raster: drawing the VolatileImage itself could fall back to a
                // snapshot copy per frame on a pipeline that does not special-case it.
                g.drawImage(((net.runelite.awt.impl.BufferedBackedVolatileImage) image).backing(), 0, 0, null);
            } finally {
                g.dispose();
            }
        }

        @Override
        public synchronized void dispose() {
            if (back != null) {
                back.flush();
                back = null;
            }
            if (bufferStrategy == this) {
                bufferStrategy = null;
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        ensureBackbuffer();
        if (RENDERED_BY_GLES) {
            // GpuGlesPlugin owns this canvas — clear our rect to fully transparent
            // so the EGL-backed SurfaceView underneath the Compose chrome image
            // shows through. Same logic as Window.hostPaint's top-level Canvas
            // branch, repeated here in case JComponent.paintChildren reaches us
            // via the normal recursion.
            if (g instanceof java.awt.Graphics2D) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                java.awt.Composite saved = g2.getComposite();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(saved);
            }
            return;
        }
        // Blit our backbuffer into whatever graphics our parent gave us. Force
        // SRC composite — OSRS writes RGB into the backbuffer with alpha=0, and
        // default SRC_OVER would treat those as fully transparent and skip.
        if (g instanceof java.awt.Graphics2D) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
            java.awt.Composite saved = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.Src);
            g2.drawImage(backbuffer, 0, 0, null);
            g2.setComposite(saved);
        } else {
            g.drawImage(backbuffer, 0, 0, null);
        }
    }

    @Override
    public void update(Graphics g) { paint(g); }
}
