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

import java.applet.Applet;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class manages repaint requests, allowing the number of repaints to be minimized, for
 * example by collapsing multiple requests into a single repaint for members of a component
 * tree.
 *
 * <p>The public API is the JDK's. What differs is who paints: there is no native window to
 * paint into, so a root's coalesced dirty region is not painted by {@link #paintDirtyRegions()}
 * itself but handed to the Android host that rasterizes that root (the game {@link Window}
 * walker, or the sidebar's panel host for a detached tree mounted with
 * {@code setHostShowing(true)}), which takes it with {@link #hostTakeDirtyRegion(Component)}
 * on its next frame. A detached, host-showing tree counts as a root where the JDK requires a
 * {@code Window} or {@code Applet}; a tree under neither records nothing, as on the JDK.
 */
public class RepaintManager {

    /** Swing's {@code swing.volatileImageBufferEnabled}; Android images are always heap rasters. */
    static final boolean volatileImageBufferEnabled =
        !"false".equals(System.getProperty("swing.volatileImageBufferEnabled"));

    private static volatile RepaintManager current;

    /** Per-component dirty rectangles in the component's own coordinates, as on the JDK. */
    private Map<Component, Rectangle> dirtyComponents = new IdentityHashMap<Component, Rectangle>();
    /** Coalesced regions per paint root, in root coordinates, waiting for the root's host. */
    private final Map<Component, Rectangle> rootRegions = new WeakHashMap<Component, Rectangle>();
    private List<Component> invalidComponents;
    private boolean processingScheduled;

    private boolean doubleBufferingEnabled = true;
    private Dimension doubleBufferMaxSize;
    private Image offscreen;
    private VolatileImage volatileOffscreen;

    private final Runnable processing = new Runnable() {
        public void run() {
            synchronized (RepaintManager.this) {
                processingScheduled = false;
            }
            validateInvalidComponents();
            paintDirtyRegions();
        }
    };

    /** Create a new RepaintManager instance. You rarely call this constructor. */
    public RepaintManager() {
    }

    /**
     * Return the RepaintManager for the calling thread given a Component.
     *
     * @param c a Component -- unused here; there is one manager per process
     * @return the RepaintManager object
     */
    public static RepaintManager currentManager(Component c) {
        RepaintManager manager = current;
        if (manager == null) {
            synchronized (RepaintManager.class) {
                manager = current;
                if (manager == null) {
                    manager = new RepaintManager();
                    current = manager;
                }
            }
        }
        return manager;
    }

    /**
     * Return the RepaintManager for the calling thread given a JComponent.
     *
     * @param c a JComponent -- unused
     * @return the RepaintManager object
     */
    public static RepaintManager currentManager(JComponent c) {
        return currentManager((Component) c);
    }

    /**
     * Set the RepaintManager that should be used for the calling thread. A {@code null}
     * argument discards the current manager; the next {@link #currentManager} call creates a
     * fresh default one.
     *
     * @param aRepaintManager the RepaintManager object to use
     */
    public static void setCurrentManager(RepaintManager aRepaintManager) {
        current = aRepaintManager;
    }

    /**
     * Mark the component as in need of layout and queue a runnable for the event dispatching
     * thread that will validate the component's first validate-root ancestor.
     *
     * @param invalidComponent a component
     * @see JComponent#isValidateRoot
     * @see #removeInvalidComponent
     */
    public void addInvalidComponent(JComponent invalidComponent) {
        Component validateRoot = validateRoot(invalidComponent);
        if (validateRoot == null) return;
        synchronized (this) {
            if (invalidComponents == null) {
                invalidComponents = new ArrayList<Component>();
            } else {
                for (Component c : invalidComponents) {
                    if (c == validateRoot) return;
                }
            }
            invalidComponents.add(validateRoot);
        }
        scheduleProcessing();
    }

    /**
     * Remove a component from the list of invalid components.
     *
     * @param component a component
     * @see #addInvalidComponent
     */
    public synchronized void removeInvalidComponent(JComponent component) {
        if (invalidComponents != null) {
            int index = invalidComponents.indexOf(component);
            if (index != -1) invalidComponents.remove(index);
        }
    }

    /**
     * SwingUtilities.getValidateRoot(c, true): the first ancestor that is a validate root,
     * provided the chain up to a showing root is visible. The top of a host-showing detached
     * tree is that tree's validate root when nothing below it claims the role, the way the
     * JDK's {@code Window} is.
     */
    private static Component validateRoot(Component c) {
        Component top = paintRoot(c);
        // The game window is validated by its host on the render thread every frame
        // (Window.renderToBackbuffer); laying a part of it out on the EDT as well would race
        // that pass. invalidate() has already marked the chain for it.
        if (top == null || top instanceof Window) return null;
        for (Container p = c instanceof Container ? (Container) c : c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof CellRendererPane) return null;
            if (p.isValidateRoot() || p == top) return p;
        }
        return null;
    }

    /**
     * Add a component in the list of components that should be refreshed. If <i>c</i> already
     * has a dirty region, the rectangle <i>(x,y,w,h)</i> will be unioned with the region that
     * should be redrawn.
     *
     * @param c Component to repaint, null results in nothing happening.
     * @param x X coordinate of the region to repaint
     * @param y Y coordinate of the region to repaint
     * @param w Width of the region to repaint
     * @param h Height of the region to repaint
     * @see JComponent#repaint
     */
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        addDirtyRegion0(c, x, y, w, h);
    }

    /**
     * Adds <code>window</code> to the list of <code>Component</code>s that need to be
     * repainted.
     *
     * @param window Window to repaint, null results in nothing happening.
     * @param x X coordinate of the region to repaint
     * @param y Y coordinate of the region to repaint
     * @param w Width of the region to repaint
     * @param h Height of the region to repaint
     * @since 1.6
     */
    public void addDirtyRegion(Window window, int x, int y, int w, int h) {
        addDirtyRegion0(window, x, y, w, h);
    }

    /**
     * Adds <code>applet</code> to the list of <code>Component</code>s that need to be
     * repainted.
     *
     * @param applet Applet to repaint, null results in nothing happening.
     * @param x X coordinate of the region to repaint
     * @param y Y coordinate of the region to repaint
     * @param w Width of the region to repaint
     * @param h Height of the region to repaint
     * @since 1.6
     */
    public void addDirtyRegion(Applet applet, int x, int y, int w, int h) {
        addDirtyRegion0(applet, x, y, w, h);
    }

    /**
     * Host entry point for a component that is neither a JComponent, a Window nor an Applet
     * (a Canvas, a plain AWT Container): there is no native peer to take its repaint, so it
     * is recorded like a JComponent's and reaches the same root.
     */
    public void hostAddDirtyRegion(Component c, int x, int y, int w, int h) {
        addDirtyRegion0(c, x, y, w, h);
    }

    private void addDirtyRegion0(Component c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || c == null) return;
        if (c.getWidth() <= 0 || c.getHeight() <= 0) return;
        if (extendDirtyRegion(c, x, y, w, h)) return;
        if (paintRoot(c) == null) return;
        synchronized (this) {
            if (extendDirtyRegion(c, x, y, w, h)) return;
            dirtyComponents.put(c, new Rectangle(x, y, w, h));
        }
        scheduleProcessing();
    }

    private synchronized boolean extendDirtyRegion(Component c, int x, int y, int w, int h) {
        Rectangle r = dirtyComponents.get(c);
        if (r == null) return false;
        union(r, x, y, w, h);
        return true;
    }

    /** SwingUtilities.computeUnion without the int overflow of Rectangle.union at MAX_VALUE. */
    private static void union(Rectangle r, int x, int y, int w, int h) {
        long x1 = Math.min(r.x, x), y1 = Math.min(r.y, y);
        long x2 = Math.max((long) r.x + r.width, (long) x + w);
        long y2 = Math.max((long) r.y + r.height, (long) y + h);
        r.x = (int) x1;
        r.y = (int) y1;
        r.width = (int) Math.min(Integer.MAX_VALUE, x2 - x1);
        r.height = (int) Math.min(Integer.MAX_VALUE, y2 - y1);
    }

    /**
     * The top of the visible chain above {@code c}: a Window or Applet, or the top of a
     * host-showing detached tree. {@code null} when an ancestor is hidden or the tree is not
     * on screen, which is where the JDK drops the request too.
     */
    private static Component paintRoot(Component c) {
        Component top = c;
        for (Component p = c; p != null; p = p.getParent()) {
            if (!p.isVisible()) return null;
            if (p instanceof Window) return p;
            top = p;
        }
        if (top instanceof Applet || top.isShowing()) return top;
        // A subtree the host mounted with setHostShowing(true) under a parent that is not on
        // screen: the shadow's isShowing() holds from that subtree's top down, and the top is
        // the root its host takes the region of. Never the case on the JDK, where a component
        // under an unshown parent is not showing.
        if (!c.isShowing()) return null;
        Component root = c;
        for (Container p = c.getParent(); p != null && p.isShowing(); p = p.getParent()) root = p;
        return root;
    }

    /**
     * Return the current dirty region for a component. Return an empty rectangle if the
     * component is not dirty.
     *
     * @param aComponent a component
     * @return the region
     */
    public Rectangle getDirtyRegion(JComponent aComponent) {
        Rectangle r;
        synchronized (this) {
            r = dirtyComponents.get(aComponent);
        }
        return r == null ? new Rectangle(0, 0, 0, 0) : new Rectangle(r);
    }

    /**
     * Mark a component completely dirty. <b>aComponent</b> will be completely painted during
     * the next paintDirtyRegions() call.
     *
     * @param aComponent a component
     */
    public void markCompletelyDirty(JComponent aComponent) {
        addDirtyRegion(aComponent, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Mark a component completely clean. <b>aComponent</b> will not get painted during the
     * next paintDirtyRegions() call.
     *
     * @param aComponent a component
     */
    public void markCompletelyClean(JComponent aComponent) {
        synchronized (this) {
            dirtyComponents.remove(aComponent);
        }
    }

    /**
     * Convenience method that returns true if <b>aComponent</b> will be completely painted
     * during the next paintDirtyRegions(). If computing dirty regions is expensive for your
     * component, use this method and avoid computing dirty region if it return true.
     *
     * @param aComponent a component
     * @return {@code true} if <b>aComponent</b> will be completely painted during the next
     *         paintDirtyRegions().
     */
    public boolean isCompletelyDirty(JComponent aComponent) {
        Rectangle r = getDirtyRegion(aComponent);
        return r.width == Integer.MAX_VALUE && r.height == Integer.MAX_VALUE;
    }

    /**
     * Validate all of the components that have been marked invalid.
     *
     * @see #addInvalidComponent
     */
    public void validateInvalidComponents() {
        List<Component> ic;
        synchronized (this) {
            if (invalidComponents == null) return;
            ic = invalidComponents;
            invalidComponents = null;
        }
        int n = ic.size();
        for (int i = 0; i < n; i++) {
            ic.get(i).validate();
        }
    }

    /**
     * Paint all of the components that have been marked dirty.
     *
     * <p>Each dirty component's region is clipped to the component and its ancestors and
     * unioned, in root coordinates, into the region of its paint root; the root's host
     * rasterizes that union on its next frame (see the class comment). Regions of components
     * that left the screen since they were marked are dropped, as the JDK's painter skips
     * them.
     *
     * @see #addDirtyRegion
     */
    public void paintDirtyRegions() {
        synchronized (this) {
            collectDirtyComponents();
        }
    }

    /** Fold every per-component rectangle into its root's region. Caller holds the lock. */
    private void collectDirtyComponents() {
        if (dirtyComponents.isEmpty()) return;
        Map<Component, Rectangle> pending = dirtyComponents;
        dirtyComponents = new IdentityHashMap<Component, Rectangle>();
        for (Map.Entry<Component, Rectangle> entry : pending.entrySet()) {
            foldIntoRoot(entry.getKey(), entry.getValue());
        }
    }

    /**
     * JComponent.paintImmediately: folds {@code c}'s region, with whatever {@code c} had
     * pending, into its root's region now, without flushing other components' requests the
     * way a full {@link #paintDirtyRegions()} would.
     */
    synchronized void paintImmediately(Component c, int x, int y, int w, int h) {
        if (c == null || w <= 0 || h <= 0) return;
        Rectangle r = dirtyComponents.remove(c);
        if (r == null) {
            r = new Rectangle(x, y, w, h);
        } else {
            union(r, x, y, w, h);
        }
        foldIntoRoot(c, r);
    }

    /**
     * Clip {@code r} (in {@code c}'s coordinates) to {@code c} and its ancestors and union it,
     * in root coordinates, into the region of {@code c}'s paint root. Caller holds the lock.
     */
    private void foldIntoRoot(Component c, Rectangle r) {
        long x0 = Math.max(0, r.x), y0 = Math.max(0, r.y);
        long x1 = Math.min(c.getWidth(), (long) r.x + r.width);
        long y1 = Math.min(c.getHeight(), (long) r.y + r.height);
        // An embedded Applet (the game client inside its frame) folds into the frame's
        // region: the frame's walker is the host that paints both.
        Component root = paintRoot(c);
        if (root == null) return;
        for (Component p = c; p != root && x0 < x1 && y0 < y1; ) {
            Container parent = p.getParent();
            x0 += p.getX(); x1 += p.getX();
            y0 += p.getY(); y1 += p.getY();
            x0 = Math.max(0, x0); y0 = Math.max(0, y0);
            x1 = Math.min(parent.getWidth(), x1); y1 = Math.min(parent.getHeight(), y1);
            p = parent;
        }
        if (x0 >= x1 || y0 >= y1) return;
        Rectangle region = rootRegions.get(root);
        if (region == null) {
            rootRegions.put(root, new Rectangle((int) x0, (int) y0, (int) (x1 - x0), (int) (y1 - y0)));
        } else {
            union(region, (int) x0, (int) y0, (int) (x1 - x0), (int) (y1 - y0));
        }
    }

    /**
     * Host entry point: the union, in {@code root} coordinates, of everything repainted under
     * {@code root} since the previous call, or {@code null} when nothing was. Collects pending
     * requests first, so a host frame never waits for the queued processing runnable; the
     * region is cleared, and repaints raised while the host paints land in the next one.
     */
    public synchronized Rectangle hostTakeDirtyRegion(Component root) {
        collectDirtyComponents();
        return rootRegions.remove(root);
    }

    private void scheduleProcessing() {
        synchronized (this) {
            if (processingScheduled) return;
            processingScheduled = true;
        }
        EventQueue.invokeLater(processing);
    }

    /**
     * Returns a string that displays and identifies this object's properties.
     *
     * @return a String representation of this object
     */
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RepaintManager dirty=").append(dirtyComponents).append(" roots=").append(rootRegions);
        if (invalidComponents != null) sb.append(" invalid=").append(invalidComponents);
        return sb.toString();
    }

    /**
     * Return the offscreen buffer that should be used as a double buffer with the component
     * <code>c</code>. By default there is a double buffer per RepaintManager. The buffer might
     * be smaller than <code>(proposedWidth,proposedHeight)</code>; this happens when the
     * maximum double buffer size has been set for the receiving repaint manager.
     *
     * @param c the component
     * @param proposedWidth the width of the buffer
     * @param proposedHeight the height of the buffer
     * @return the image
     */
    public Image getOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
        if (c == null) return null;
        Dimension max = getDoubleBufferMaximumSize();
        int width = Math.max(1, Math.min(proposedWidth, max.width));
        int height = Math.max(1, Math.min(proposedHeight, max.height));
        synchronized (this) {
            Image image = offscreen;
            if (image == null || image.getWidth(null) < width || image.getHeight(null) < height) {
                if (image != null) {
                    width = Math.max(width, image.getWidth(null));
                    height = Math.max(height, image.getHeight(null));
                    image.flush();
                }
                // Component.createImage is the opaque TYPE_INT_RGB raster the desktop returns.
                image = c.createImage(width, height);
                offscreen = image;
            }
            return image;
        }
    }

    /**
     * Return a volatile offscreen buffer that should be used as a double buffer with the
     * specified component <code>c</code>. The image returned will be an instance of
     * VolatileImage, or null if a VolatileImage object could not be instantiated. This buffer
     * might be smaller than <code>(proposedWidth,proposedHeight)</code>.
     *
     * @param c the component
     * @param proposedWidth the width of the buffer
     * @param proposedHeight the height of the buffer
     * @return the volatile image
     * @since 1.4
     */
    public Image getVolatileOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
        if (c == null) return null;
        Dimension max = getDoubleBufferMaximumSize();
        int width = Math.max(1, Math.min(proposedWidth, max.width));
        int height = Math.max(1, Math.min(proposedHeight, max.height));
        synchronized (this) {
            VolatileImage image = volatileOffscreen;
            if (image == null || image.getWidth() < width || image.getHeight() < height) {
                if (image != null) {
                    width = Math.max(width, image.getWidth());
                    height = Math.max(height, image.getHeight());
                    image.flush();
                }
                image = c.getGraphicsConfiguration().createCompatibleVolatileImage(width, height);
                volatileOffscreen = image;
            }
            return image;
        }
    }

    /**
     * Set the maximum double buffer size.
     *
     * @param d the dimension
     */
    public void setDoubleBufferMaximumSize(Dimension d) {
        synchronized (this) {
            doubleBufferMaxSize = d;
            if (d == null) return;
            if (offscreen != null && (offscreen.getWidth(null) > d.width || offscreen.getHeight(null) > d.height)) {
                offscreen.flush();
                offscreen = null;
            }
            if (volatileOffscreen != null && (volatileOffscreen.getWidth() > d.width || volatileOffscreen.getHeight() > d.height)) {
                volatileOffscreen.flush();
                volatileOffscreen = null;
            }
        }
    }

    /**
     * Returns the maximum double buffer size.
     *
     * @return a Dimension object representing the maximum size
     */
    public Dimension getDoubleBufferMaximumSize() {
        synchronized (this) {
            if (doubleBufferMaxSize == null) {
                // The JDK answers the virtual screen bounds, or MAX_VALUE square when headless;
                // there is no screen device list to union here.
                doubleBufferMaxSize = new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            return doubleBufferMaxSize;
        }
    }

    /**
     * Enables or disables double buffering in this RepaintManager. CAUTION: The default value
     * for this property is set for optimal paint performance on the given platform and it is
     * not recommended that programs modify this property directly.
     *
     * @param aFlag true to activate double buffering
     * @see #isDoubleBufferingEnabled
     */
    public void setDoubleBufferingEnabled(boolean aFlag) {
        doubleBufferingEnabled = aFlag;
        if (!aFlag) {
            synchronized (this) {
                if (offscreen != null) offscreen.flush();
                offscreen = null;
                if (volatileOffscreen != null) volatileOffscreen.flush();
                volatileOffscreen = null;
            }
        }
    }

    /**
     * Returns true if this RepaintManager is double buffered. The default value for this
     * property may vary from platform to platform.
     *
     * @return true if this object is double buffered
     */
    public boolean isDoubleBufferingEnabled() {
        return doubleBufferingEnabled;
    }
}
