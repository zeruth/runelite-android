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

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

public class Window extends Container {
    private static final long serialVersionUID = 4497834738069338734L;

    public interface VisibilityHandler { void changed(Window window, boolean visible); }
    private static volatile VisibilityHandler visibilityHandler;
    public static void setVisibilityHandler(VisibilityHandler h) { visibilityHandler = h; }
    private static final AtomicReference<Window> LATEST = new AtomicReference<>();
    private static final java.util.List<java.lang.ref.WeakReference<Window>> ALL_WINDOWS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    private java.util.List<Image> iconImages = new java.util.ArrayList<>();
    private BufferedImage backbuffer;
    private Color windowBackground = new Color(0x1E1F22);
    private final Window owner;
    private boolean opened, disposed, autoRequestFocus = true, focusableWindowState = true;
    /** Stands in for the JDK's peer: set by pack() and the first show, cleared by dispose(). */
    private boolean hostPeer;
    private volatile float opacity = 1.0f;
    private volatile java.awt.Shape shape;
    private boolean alwaysOnTop, locationByPlatform;
    private Dialog.ModalExclusionType modalExclusionType = Dialog.ModalExclusionType.NO_EXCLUDE;
    private final java.util.List<java.awt.event.WindowListener> windowListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<java.awt.event.WindowFocusListener> windowFocusListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<java.awt.event.WindowStateListener> windowStateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public Window() { this((Window) null); }
    public Window(Frame owner) { this((Window) owner); }
    public Window(Window owner) { this(owner, null); }
    public Window(Window owner, GraphicsConfiguration configuration) {
        this.owner = owner;
        visible = false;
        LATEST.set(this);
        ALL_WINDOWS.add(new java.lang.ref.WeakReference<>(this));
    }

    /** Most-recently-instantiated window. */
    public static Window latest() {
        return LATEST.get();
    }

    /** Every Window the shim has ever constructed (filtered by WeakReference, so
     *  garbage-collected entries drop out). The Compose-side input bridge uses this
     *  to walk for KeyListeners — the OSRS engine's listener can be registered on a
     *  Component inside a Window that {@link #primaryFrame} doesn't pick (the
     *  primary-frame heuristic looks for the biggest visible game-Canvas-containing
     *  Frame; non-visible utility Windows or windows the engine never paints into are
     *  excluded by that filter even though their Components still receive input). */
    public static java.util.List<Window> getAllWindows() {
        java.util.List<Window> out = new java.util.ArrayList<>(ALL_WINDOWS.size());
        for (java.lang.ref.WeakReference<Window> ref : ALL_WINDOWS) {
            Window w = ref.get();
            if (w != null) out.add(w);
        }
        return out;
    }

    /**
     * Pick the frame the host should render. Preference order:
     *   1. Walk up from the current game Canvas — if it's in a Window tree, use that Window.
     *   2. Biggest visible Frame whose subtree contains *any* Canvas (skips splash screens).
     *   3. Biggest visible Frame whose class name is not "SplashScreen" (covers blank frames).
     *   4. Latest window.
     */
    public static Window primaryFrame() {
        Canvas c = Canvas.latest();
        if (c != null && c.getParent() != null) {
            Container p = c.getParent();
            Container last = null;
            while (p != null) { last = p; p = p.getParent(); }
            if (last instanceof Window) return (Window) last;
        }

        Window withCanvas = null;
        long withCanvasArea = 0;
        Window anyFrame = null;
        long anyArea = 0;
        for (java.lang.ref.WeakReference<Window> ref : ALL_WINDOWS) {
            Window w = ref.get();
            if (w == null || !(w instanceof Frame) || !w.isVisible()) continue;
            String name = w.getClass().getSimpleName();
            if ("SplashScreen".equals(name)) continue;
            long a = (long) w.getWidth() * w.getHeight();
            if (containsCanvas(w) && a > withCanvasArea) { withCanvasArea = a; withCanvas = w; }
            if (a > anyArea) { anyArea = a; anyFrame = w; }
        }
        if (withCanvas != null) return withCanvas;
        if (anyFrame != null) return anyFrame;
        return LATEST.get();
    }

    /** Cached lookup: does this Class hierarchy override
     *  {@link Component#paint(Graphics)} below Component/Container/JComponent?
     *  RuneLite uses paint() as a side-effect hook in a few places
     *  (PluginIcon's async icon-load trigger), and our default paint walker
     *  bypasses paint() to recurse manually. When a subclass really did
     *  override paint(), we hand the JComponent its own paint() so those
     *  side-effects fire. */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> PAINT_OVERRIDE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean overridesPaint(Class<?> cls) {
        Boolean cached = PAINT_OVERRIDE.get(cls);
        if (cached != null) return cached;
        boolean result = false;
        try {
            java.lang.reflect.Method m = cls.getMethod("paint", Graphics.class);
            Class<?> dc = m.getDeclaringClass();
            // Anything outside the standard Component/Container/JComponent base
            // counts as an override. JLabel doesn't override paint() — it only
            // overrides paintComponent — so JLabel itself still takes the
            // manual decomposition path.
            String dcn = dc.getName();
            result = !"java.awt.Component".equals(dcn)
                && !"java.awt.Container".equals(dcn)
                && !"javax.swing.JComponent".equals(dcn);
        } catch (Throwable ignored) {}
        PAINT_OVERRIDE.put(cls, result);
        return result;
    }

    private static boolean containsCanvas(Component c) {
        if (c instanceof Canvas) return true;
        if (c instanceof Container) {
            Container cont = (Container) c;
            int count = cont.getComponentCount();
            for (int i = 0; i < count; i++) {
                if (containsCanvas(cont.getComponent(i))) return true;
            }
        }
        return false;
    }

    /** Debug helper: dump this window's component tree as a string. */
    public String dumpTree() {
        StringBuilder sb = new StringBuilder();
        dumpTree(sb, this, 0);
        return sb.toString();
    }

    private static void dumpTree(StringBuilder sb, Component c, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(c.getClass().getName())
          .append(" [").append(c.getX()).append(',').append(c.getY())
          .append(' ').append(c.getWidth()).append('x').append(c.getHeight())
          .append(" vis=").append(c.isVisible())
          .append("]\n");
        if (c instanceof Container) {
            Container cont = (Container) c;
            int count = cont.getComponentCount();
            for (int i = 0; i < count; i++) {
                dumpTree(sb, cont.getComponent(i), depth + 1);
            }
        }
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
        doLayout();
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        // The JDK clears it for SET_BOUNDS and SET_LOCATION, never SET_SIZE: setSize (and so
        // pack) does not come through here, so a platform-placed window keeps the flag.
        locationByPlatform = false;
        super.setBounds(x, y, w, h);
        ensureBackbuffer();
        doLayout();
    }

    @Override
    public void setLocation(int x, int y) {
        locationByPlatform = false;
        super.setLocation(x, y);
    }

    /**
     * How often to force a full relayout regardless of the validity flags, in ms.
     *
     * Layout is now driven by invalidation (see {@link Component#invalidate()}), so a
     * steady-state frame does no layout work at all. That is only as correct as the set of
     * mutators that remember to invalidate, and this shim is a stand-in for a toolkit whose
     * real implementation has decades of those hooks — plus it hosts third-party plugin
     * components we don't control. A periodic sweep turns "missed a hook, layout is wrong
     * forever" into "layout is at most a quarter second late", which is a far better failure
     * mode for something that can't be exhaustively tested here.
     *
     * It costs four full passes a second instead of sixty. Raise it (or drop the sweep) once
     * the invalidation coverage has been confirmed on-device.
     */
    static final long FORCED_VALIDATE_INTERVAL_MS = 250;
    private long nextForcedValidate;

    /**
     * Render the entire window tree into the backbuffer and return it.
     * Called by the Compose host once per display frame (vsync-driven).
     */
    public BufferedImage renderToBackbuffer() {
        ensureBackbuffer();
        // The game canvas changes every frame, so the whole buffer is repainted regardless of
        // what was marked; taking the region is what keeps it from growing between frames.
        // Repaints raised by the layout and paint below land in the next frame's region.
        lastDirtyRegion = javax.swing.RepaintManager.currentManager(this).hostTakeDirtyRegion(this);
        try {
            // Skip the sweep when the tree is already dirty — it's about to be relaid anyway.
            if (isValid()) {
                long now = System.currentTimeMillis();
                if (now >= nextForcedValidate) {
                    nextForcedValidate = now + FORCED_VALIDATE_INTERVAL_MS;
                    markTreeInvalid(this);
                }
            }
            validate();
        } catch (Throwable ignored) {}
        java.awt.Graphics2D g = backbuffer.createGraphics();
        try {
            // The backbuffer is reused: a shaped or non-opaque window must start from clear
            // pixels, or last frame's content would show through its transparent parts.
            java.awt.Shape windowShape = shape;
            if (windowShape != null || windowBackground.getAlpha() != 255) {
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fillRect(0, 0, backbuffer.getWidth(), backbuffer.getHeight());
                g.setComposite(java.awt.AlphaComposite.SrcOver);
            }
            if (windowShape != null) g.clip(windowShape);
            g.setColor(windowBackground);
            g.fillRect(0, 0, backbuffer.getWidth(), backbuffer.getHeight());
            hostPaint(g, this);
        } finally {
            g.dispose();
        }
        return backbuffer;
    }

    /**
     * Host-side paint walk. Bypasses Component.paint() for arbitrary user-defined Containers
     * (eg the patched RuneScape client applet) whose overridden paint draws into its own
     * private backbuffer and never recurses into our shadow tree. We fill backgrounds, call
     * JComponent paintComponent/paintBorder, blit Canvas backbuffers, and recurse manually.
     */
    /** Per-thread scratch state for the [#hostPaint] recursion. The save/restore at the
     *  top and bottom of each call needs to capture the current Graphics2D transform +
     *  clip; the JDK accessors allocate both an AffineTransform and a Rectangle every
     *  time, which adds up to a few MB/sec across the OSRS UI tree. We instead keep a
     *  pool of double[6]+int[4] pairs sized to the current recursion depth and rent
     *  one per call. Pool grows on demand; never shrinks (max depth in practice is
     *  ~30 for the OSRS applet hierarchy). */
    private static final class PaintScratch {
        double[][] txs = new double[16][];
        int[][] clips = new int[16][];
        int depth;
        double[] tx() {
            if (depth >= txs.length) {
                txs = java.util.Arrays.copyOf(txs, txs.length * 2);
                clips = java.util.Arrays.copyOf(clips, clips.length * 2);
            }
            double[] t = txs[depth];
            if (t == null) { t = new double[6]; txs[depth] = t; }
            return t;
        }
        int[] clip() {
            int[] c = clips[depth];
            if (c == null) { c = new int[4]; clips[depth] = c; }
            return c;
        }
    }
    private static final ThreadLocal<PaintScratch> SCRATCH = ThreadLocal.withInitial(PaintScratch::new);

    /** Render a component subtree for Android hosts. Call on the Swing event thread. */
    public static void hostPaint(java.awt.Graphics2D g, Component c) {
        if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) return;
        // Fast-path the common BufferedImageGraphics2D case with the zero-alloc
        // snapshot helpers. Fall back to the JDK-style save/restore when something
        // else (eg. some plugin's custom Graphics) is on the stack so semantics stay
        // identical for non-mainline callers.
        net.runelite.awt.impl.BufferedImageGraphics2D fast =
            g instanceof net.runelite.awt.impl.BufferedImageGraphics2D
                ? (net.runelite.awt.impl.BufferedImageGraphics2D) g : null;
        PaintScratch ps = fast != null ? SCRATCH.get() : null;
        double[] savedTxArr = null;
        int[] savedClipArr = null;
        java.awt.geom.AffineTransform savedTx = null;
        java.awt.Shape savedClip = null;
        if (fast != null) {
            savedTxArr = ps.tx();
            savedClipArr = ps.clip();
            fast.snapshotTransform(savedTxArr);
            fast.snapshotClip(savedClipArr);
            ps.depth++;
        } else {
            savedTx = g.getTransform();
            savedClip = g.getClip();
        }
        try {
            // Top-level bounds are desktop screen coordinates, not buffer coordinates.
            // Imported profiles may restore a window on a second monitor.
            if (!(c instanceof Window)) g.translate(c.getX(), c.getY());
            g.clipRect(0, 0, c.getWidth(), c.getHeight());

            if (c instanceof Canvas) {
                if (Canvas.isRenderedByGles()) {
                    // GpuGlesPlugin owns this canvas — clear its rect to fully transparent
                    // so the EGL-backed SurfaceView underneath shows through the chrome
                    // bitmap. AlphaComposite.Clear writes (0,0,0,0) regardless of source.
                    java.awt.Composite saved = g.getComposite();
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    g.setComposite(saved);
                    return;
                }
                BufferedImage bb = ((Canvas) c).getBackbuffer();
                if (bb != null) {
                    // The OSRS client writes raw RGB into its Canvas backbuffer with the
                    // alpha byte left as 0; on desktop the AWT Canvas is opaque so those
                    // bits are ignored at composite time. We use a BufferedImage with
                    // honest alpha semantics, so a default SRC_OVER blit would see
                    // source alpha=0 and skip every 3D-rasterizer pixel — leaving the
                    // 3D scene and any sprite-blitted region invisible. Force SRC so
                    // the canvas pixels overwrite the window backbuffer wholesale.
                    java.awt.Composite saved = g.getComposite();
                    g.setComposite(java.awt.AlphaComposite.Src);
                    g.drawImage(bb, 0, 0, null);
                    g.setComposite(saved);
                }
                return;
            }

            if (c instanceof javax.swing.JComponent) {
                javax.swing.JComponent jc = (javax.swing.JComponent) c;
                if (overridesPaint(jc.getClass()) && !containsCanvas(jc)) {
                    try { jc.paint(g); } catch (Throwable ignored) {}
                    return;
                }
                if (jc.isOpaque()) {
                    java.awt.Color saved = g.getColor();
                    g.setColor(jc.getBackground());
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    if (saved != null) g.setColor(saved);
                }
                try { jc.hostPaintComponent(g); } catch (Throwable ignored) {}
                try { jc.hostPaintBorder(g); } catch (Throwable ignored) {}
                if (jc instanceof Container) {
                    Container cont = (Container) jc;
                    int count = cont.getComponentCount();
                    for (int i = count - 1; i >= 0; i--) hostPaint(g, cont.getComponent(i));
                }
                return;
            }

            if (c instanceof Container) {
                Color bg = c.getBackground();
                if (bg != null) {
                    java.awt.Color saved = g.getColor();
                    g.setColor(bg);
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    if (saved != null) g.setColor(saved);
                }
                Container cont = (Container) c;
                int count = cont.getComponentCount();
                for (int i = count - 1; i >= 0; i--) hostPaint(g, cont.getComponent(i));
                return;
            }

            // Bare Component — fill bg.
            Color bg = c.getBackground();
            if (bg != null) {
                java.awt.Color saved = g.getColor();
                g.setColor(bg);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                if (saved != null) g.setColor(saved);
            }
        } finally {
            if (fast != null) {
                fast.restoreTransform(savedTxArr);
                fast.restoreClip(savedClipArr);
                ps.depth--;
            } else {
                g.setTransform(savedTx);
                g.setClip(savedClip);
            }
        }
    }

    /**
     * Force a relayout of this window's entire subtree on the next {@link #validate()}.
     *
     * Plain {@code invalidate()} deliberately follows the AWT contract: it marks this
     * component and its ancestors, and {@code validate()} then skips any child subtree that
     * is still valid. That is the right default and it is what makes the per-frame validate
     * nearly free — but it means {@code invalidate(); validate();} on a window is *not* the
     * "re-lay everything" hammer it used to be back when validity wasn't tracked. Callers
     * that really do want the hammer (the renderer toggle in the Compose host, where the
     * component tree is unchanged but every consumer of the resulting geometry needs a fresh
     * layout pass) should use this instead.
     */
    public void invalidateTree() {
        markTreeInvalid(this);
    }

    /** Clear the validity flag over a whole subtree, without invalidate()'s upward walk —
     *  we're starting from the root, so there is nothing above to mark. */
    private static void markTreeInvalid(Component c) {
        c.setValid(false);
        if (c instanceof Container) {
            java.awt.LayoutManager layout = ((Container) c).getLayout();
            if (layout instanceof java.awt.LayoutManager2)
                ((java.awt.LayoutManager2) layout).invalidateLayout((Container) c);
        }
        if (c instanceof Container) {
            Container cont = (Container) c;
            int count = cont.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component child = cont.getComponent(i);
                if (child != null) markTreeInvalid(child);
            }
        }
    }

    @Override
    public Color getBackground() { return windowBackground; }
    /**
     * Sets the background color of this window. As on the JDK, a background whose alpha is
     * below 255 makes the window per-pixel translucent: it needs
     * {@link GraphicsDevice.WindowTranslucency#PERPIXEL_TRANSLUCENT} and makes the root pane
     * layers of a Swing window non-opaque. A {@code null} color is ignored here, because the
     * host always needs a color to clear the backbuffer with.
     */
    @Override
    public void setBackground(Color bgColor) {
        if (bgColor == null) return;
        Color oldBg = windowBackground;
        if (oldBg.equals(bgColor)) return;
        int oldAlpha = oldBg.getAlpha();
        int alpha = bgColor.getAlpha();
        if (oldAlpha == 255 && alpha < 255) {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            GraphicsDevice gd = gc.getDevice();
            if (gd != null && gd.getFullScreenWindow() == this) {
                throw new IllegalComponentStateException("Making full-screen window non opaque is not supported.");
            }
            if (!gc.isTranslucencyCapable() && (gd == null || gd.getTranslucencyCapableGC() == null)) {
                throw new UnsupportedOperationException("PERPIXEL_TRANSLUCENT translucency is not supported");
            }
            windowBackground = bgColor;
            setLayersOpaque(this, false);
        } else {
            windowBackground = bgColor;
            if (oldAlpha < 255 && alpha == 255) setLayersOpaque(this, true);
        }
        repaint();
    }

    private static void setLayersOpaque(Component component, boolean isOpaque) {
        if (component instanceof javax.swing.RootPaneContainer) {
            javax.swing.JRootPane root = ((javax.swing.RootPaneContainer) component).getRootPane();
            if (root == null) return;
            javax.swing.JLayeredPane lp = root.getLayeredPane();
            Container c = root.getContentPane();
            javax.swing.JComponent content = (c instanceof javax.swing.JComponent) ? (javax.swing.JComponent) c : null;
            if (lp != null) lp.setOpaque(isOpaque);
            root.setOpaque(isOpaque);
            if (content != null) {
                content.setOpaque(isOpaque);
                // One level down for an embedded RootPaneContainer (a JApplet), as the JDK does.
                if (content.getComponentCount() > 0) {
                    Component child = content.getComponent(0);
                    if (child instanceof javax.swing.RootPaneContainer) setLayersOpaque(child, isOpaque);
                }
            }
        }
    }

    /**
     * A window is opaque unless its background color is translucent.
     *
     * @since 1.7
     */
    public boolean isOpaque() {
        Color bg = getBackground();
        return bg != null ? bg.getAlpha() == 255 : true;
    }

    public void pack() {
        // Walk our own tree for a preferred size. If it's positive, snap our bounds to it.
        // Window itself contains rootPane (for JFrame) — whose preferred size cascades down
        // through BorderLayout → contentPane → ClientUI.Layout → clientPanel/sidebar.
        hostPeer = true;
        Dimension d = getPreferredSize();
        if (d != null && d.width > 0 && d.height > 0) {
            setSize(d.width, d.height);
        }
        try { validate(); } catch (Throwable ignored) {}
    }
    public void show() { setVisible(true); }
    public void dispose() {
        setVisible(false);
        for (Window child : getOwnedWindows()) child.dispose();
        if (!disposed) {
            disposed = true;
            // The JDK posts WINDOW_CLOSED only for a window that was displayable.
            boolean wasDisplayable = hostPeer;
            hostPeer = false;
            if (wasDisplayable) dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSED));
        }
    }

    /** Whether the JDK would have a native peer for this window: packed or shown, not disposed. */
    boolean hasHostPeer() { return hostPeer && !disposed; }

    @Override
    public void setVisible(boolean v) {
        boolean wasVisible = isVisible();
        if (v) { disposed = false; hostPeer = true; }
        super.setVisible(v);
        // Every Swing top-level reaches the host, which picks what it presents: SwingDialogs skips
        // RuneLite's ContainableFrame and SplashScreen (the main activity draws those full-screen)
        // and gives plugin JFrames, JDialogs and JWindows a host window.
        VisibilityHandler handler = visibilityHandler;
        if (handler != null && this instanceof javax.swing.RootPaneContainer) handler.changed(this, v);
        if (v && !wasVisible) {
            // Real Swing validates the tree on first showing.
            try { validate(); } catch (Throwable ignored) {}
            if (!opened) {
                opened = true;
                dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_OPENED));
            }
            if (autoRequestFocus) activate();
        }
    }

    /**
     * What native activation does on the desktop: focus the most recent focus owner,
     * else the policy's initial component, cause ACTIVATION. With nothing focusable
     * the window itself takes focus, since there is no native activation to make it
     * the focused Window on its own.
     */
    private void activate() {
        if (!isFocusableWindow()) return;
        Component toFocus = getMostRecentFocusOwner();
        if (toFocus != null && toFocus != this && toFocus.requestFocusInWindow(java.awt.event.FocusEvent.Cause.ACTIVATION)) return;
        requestFocusInWindow(java.awt.event.FocusEvent.Cause.ACTIVATION);
    }

    /** Returns the focus owner when this Window is focused, else null. */
    public Component getFocusOwner() {
        return isFocused() ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() : null;
    }

    /**
     * Returns the component that gets focus when this Window is focused: the focus
     * owner, the last one it had, or the policy's initial component.
     */
    public Component getMostRecentFocusOwner() {
        if (isFocused()) return getFocusOwner();
        Component mostRecent = KeyboardFocusManager.getMostRecentFocusOwner(this);
        if (mostRecent != null) return mostRecent;
        return isFocusableWindow() ? getFocusTraversalPolicy().getInitialComponent(this) : null;
    }

    /** Windows are always focus cycle roots. */
    @Override public final boolean isFocusCycleRoot() { return true; }
    /** Does nothing: Windows are always focus cycle roots. */
    @Override public final void setFocusCycleRoot(boolean focusCycleRoot) {}
    /** Always null: a Window has no focus cycle root ancestor. */
    @Override public final Container getFocusCycleRootAncestor() { return null; }
    @Override public boolean isShowing() { return isVisible() && !disposed; }
    /** As on the JDK: displayable from {@code pack()} or the first show until {@code dispose()}. */
    @Override public boolean isDisplayable() { return hasHostPeer(); }

    /**
     * Indicates if this container is a validate root. {@code Window} objects are the validate
     * roots, and, therefore, they override this method to return {@code true}.
     *
     * @return {@code true}
     * @since 1.7
     */
    @Override
    public boolean isValidateRoot() {
        return true;
    }

    private Rectangle lastDirtyRegion;

    /**
     * The RepaintManager region the last {@link #renderToBackbuffer} took, in window
     * coordinates, or {@code null} when nothing under the window asked for a repaint.
     */
    public Rectangle hostLastDirtyRegion() {
        return lastDirtyRegion == null ? null : new Rectangle(lastDirtyRegion);
    }
    public boolean isActive() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == this; }
    public boolean isFocused() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == this; }

    public void setIconImage(Image image) {
        iconImages.clear();
        if (image != null) iconImages.add(image);
    }
    public Image getIconImage() { return iconImages.isEmpty() ? null : iconImages.get(0); }

    public void setIconImages(java.util.List<? extends Image> icons) {
        iconImages = (icons == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(icons);
    }
    public java.util.List<Image> getIconImages() { return new java.util.ArrayList<>(iconImages); }

    public Window getOwner() { return owner; }
    public Window[] getOwnedWindows() { return getAllWindows().stream().filter(w -> w.owner == this).toArray(Window[]::new); }
    public static Window[] getWindows() { return getAllWindows().toArray(new Window[0]); }
    public static Window[] getOwnerlessWindows() { return getAllWindows().stream().filter(w -> w.owner == null).toArray(Window[]::new); }
    public void toFront() { if (isVisible() && autoRequestFocus) activate(); }
    /** Deliberate no-op: the activity stacks host windows in show order and cannot lower one. */
    public void toBack() {}

    public void addWindowListener(java.awt.event.WindowListener l) { if (l != null) windowListeners.add(l); }
    public void removeWindowListener(java.awt.event.WindowListener l) { windowListeners.remove(l); }
    public java.awt.event.WindowListener[] getWindowListeners() { return windowListeners.toArray(new java.awt.event.WindowListener[0]); }
    public void addWindowFocusListener(java.awt.event.WindowFocusListener l) { if (l != null) windowFocusListeners.add(l); }
    public void removeWindowFocusListener(java.awt.event.WindowFocusListener l) { windowFocusListeners.remove(l); }
    public java.awt.event.WindowFocusListener[] getWindowFocusListeners() { return windowFocusListeners.toArray(new java.awt.event.WindowFocusListener[0]); }
    public void addWindowStateListener(java.awt.event.WindowStateListener l) { if (l != null) windowStateListeners.add(l); }
    public void removeWindowStateListener(java.awt.event.WindowStateListener l) { windowStateListeners.remove(l); }
    public java.awt.event.WindowStateListener[] getWindowStateListeners() { return windowStateListeners.toArray(new java.awt.event.WindowStateListener[0]); }
    @Override boolean eventEnabled(AWTEvent event) {
        switch (event.getID()) {
            case java.awt.event.WindowEvent.WINDOW_OPENED:
            case java.awt.event.WindowEvent.WINDOW_CLOSING:
            case java.awt.event.WindowEvent.WINDOW_CLOSED:
            case java.awt.event.WindowEvent.WINDOW_ICONIFIED:
            case java.awt.event.WindowEvent.WINDOW_DEICONIFIED:
            case java.awt.event.WindowEvent.WINDOW_ACTIVATED:
            case java.awt.event.WindowEvent.WINDOW_DEACTIVATED:
                if ((eventMask & AWTEvent.WINDOW_EVENT_MASK) != 0 || !windowListeners.isEmpty()) return true;
                return false;
            case java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS:
            case java.awt.event.WindowEvent.WINDOW_LOST_FOCUS:
                if ((eventMask & AWTEvent.WINDOW_FOCUS_EVENT_MASK) != 0 || !windowFocusListeners.isEmpty()) return true;
                return false;
            case java.awt.event.WindowEvent.WINDOW_STATE_CHANGED:
                if ((eventMask & AWTEvent.WINDOW_STATE_EVENT_MASK) != 0 || !windowStateListeners.isEmpty()) return true;
                return false;
            default:
                return super.eventEnabled(event);
        }
    }
    @Override protected void processEvent(AWTEvent event) {
        if (event instanceof java.awt.event.WindowEvent) {
            java.awt.event.WindowEvent windowEvent = (java.awt.event.WindowEvent) event;
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS || event.getID() == java.awt.event.WindowEvent.WINDOW_LOST_FOCUS)
                processWindowFocusEvent(windowEvent);
            else if (event.getID() == java.awt.event.WindowEvent.WINDOW_STATE_CHANGED) processWindowStateEvent(windowEvent);
            else processWindowEvent(windowEvent);
        } else super.processEvent(event);
    }
    protected void processWindowEvent(java.awt.event.WindowEvent event) {
        for (java.awt.event.WindowListener l : windowListeners) switch (event.getID()) {
            case java.awt.event.WindowEvent.WINDOW_OPENED: l.windowOpened(event); break;
            case java.awt.event.WindowEvent.WINDOW_CLOSING: l.windowClosing(event); break;
            case java.awt.event.WindowEvent.WINDOW_CLOSED: l.windowClosed(event); break;
            case java.awt.event.WindowEvent.WINDOW_ICONIFIED: l.windowIconified(event); break;
            case java.awt.event.WindowEvent.WINDOW_DEICONIFIED: l.windowDeiconified(event); break;
            case java.awt.event.WindowEvent.WINDOW_ACTIVATED: l.windowActivated(event); break;
            case java.awt.event.WindowEvent.WINDOW_DEACTIVATED: l.windowDeactivated(event); break;
        }
    }
    protected void processWindowFocusEvent(java.awt.event.WindowEvent event) {
        for (java.awt.event.WindowFocusListener l : windowFocusListeners)
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS) l.windowGainedFocus(event); else l.windowLostFocus(event);
    }
    protected void processWindowStateEvent(java.awt.event.WindowEvent event) {
        for (java.awt.event.WindowStateListener l : windowStateListeners) l.windowStateChanged(event);
    }
    /**
     * Sets the opacity of the window, with the JDK's checks: the value must lie in
     * [0, 1], and a value below 1 needs {@link GraphicsDevice.WindowTranslucency#TRANSLUCENT}
     * and a window that is not full-screen. Windows the host presents through its dialog
     * bridge are drawn at this opacity; the client frame is drawn by the activity at full
     * opacity (RuneLite does not apply its window opacity setting on Android).
     *
     * @since 1.7
     */
    public void setOpacity(float opacity) {
        synchronized (getTreeLock()) {
            if (opacity < 0.0f || opacity > 1.0f) {
                throw new IllegalArgumentException("The value of opacity should be in the range [0.0f .. 1.0f].");
            }
            if (opacity < 1.0f) {
                GraphicsDevice gd = getGraphicsConfiguration().getDevice();
                if (gd != null && gd.getFullScreenWindow() == this) {
                    throw new IllegalComponentStateException("Setting opacity for full-screen window is not supported.");
                }
                if (gd == null || !gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                    throw new UnsupportedOperationException("TRANSLUCENT translucency is not supported.");
                }
            }
            this.opacity = opacity;
        }
        repaint();
    }

    /** @since 1.7 */
    public float getOpacity() { return opacity; }

    /**
     * Sets the shape of the window; the host backbuffer is cleared outside it. A non-null
     * shape needs {@link GraphicsDevice.WindowTranslucency#PERPIXEL_TRANSPARENT}.
     *
     * @since 1.7
     */
    public void setShape(java.awt.Shape shape) {
        synchronized (getTreeLock()) {
            if (shape != null) {
                GraphicsDevice gd = getGraphicsConfiguration().getDevice();
                if (gd != null && gd.getFullScreenWindow() == this) {
                    throw new IllegalComponentStateException("Setting shape for full-screen window is not supported.");
                }
                if (gd == null || !gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                    throw new UnsupportedOperationException("PERPIXEL_TRANSPARENT translucency is not supported.");
                }
            }
            this.shape = (shape == null) ? null : new java.awt.geom.Path2D.Float(shape);
        }
        repaint();
    }

    /** Returns a copy of the window shape, or {@code null}. @since 1.7 */
    public java.awt.Shape getShape() {
        java.awt.Shape current = shape;
        return current == null ? null : new java.awt.geom.Path2D.Float(current);
    }

    public void setAutoRequestFocus(boolean autoRequestFocus) { this.autoRequestFocus = autoRequestFocus; }
    public boolean isAutoRequestFocus() { return autoRequestFocus; }

    /**
     * Stores the always-on-top state and propagates it to owned windows, firing the
     * {@code "alwaysOnTop"} property change as the JDK does. The toolkit reports the mode as
     * unsupported, so, as the JDK specifies for that case, the host stacking does not change.
     */
    public final void setAlwaysOnTop(boolean alwaysOnTop) throws SecurityException {
        boolean oldAlwaysOnTop;
        synchronized (this) {
            oldAlwaysOnTop = this.alwaysOnTop;
            this.alwaysOnTop = alwaysOnTop;
        }
        if (oldAlwaysOnTop != alwaysOnTop) firePropertyChange("alwaysOnTop", oldAlwaysOnTop, alwaysOnTop);
        for (Window owned : getOwnedWindows()) owned.setAlwaysOnTop(alwaysOnTop);
    }
    public final boolean isAlwaysOnTop() { return alwaysOnTop; }
    public boolean isAlwaysOnTopSupported() { return Toolkit.getDefaultToolkit().isAlwaysOnTopSupported(); }

    public final boolean isFocusableWindow() { return focusableWindowState && isFocusable(); }
    public void setFocusableWindowState(boolean focusableWindowState) {
        boolean oldFocusableWindowState = this.focusableWindowState;
        this.focusableWindowState = focusableWindowState;
        if (!focusableWindowState) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        firePropertyChange("focusableWindowState", oldFocusableWindowState, focusableWindowState);
    }
    public boolean getFocusableWindowState() { return focusableWindowState; }

    /**
     * Whether the host should choose the location of this window when it is next shown.
     * Any later {@code setLocation} or {@code setBounds} clears it, as on the JDK.
     *
     * @since 1.5
     */
    public void setLocationByPlatform(boolean locationByPlatform) {
        synchronized (getTreeLock()) {
            if (locationByPlatform && isShowing()) {
                throw new IllegalComponentStateException("The window is showing on screen.");
            }
            this.locationByPlatform = locationByPlatform;
        }
    }
    /** @since 1.5 */
    public boolean isLocationByPlatform() {
        synchronized (getTreeLock()) { return locationByPlatform; }
    }

    /**
     * Sets the modal exclusion type; a {@code null} or unsupported type becomes
     * {@link Dialog.ModalExclusionType#NO_EXCLUDE}.
     *
     * @since 1.6
     */
    public void setModalExclusionType(Dialog.ModalExclusionType exclusionType) {
        if (exclusionType == null || !Toolkit.getDefaultToolkit().isModalExclusionTypeSupported(exclusionType)) {
            exclusionType = Dialog.ModalExclusionType.NO_EXCLUDE;
        }
        modalExclusionType = exclusionType;
    }
    /** @since 1.6 */
    public Dialog.ModalExclusionType getModalExclusionType() { return modalExclusionType; }

    @Override public Toolkit getToolkit() { return Toolkit.getDefaultToolkit(); }

    /**
     * Deliberate no-op after the JDK's argument checks: component orientation is not
     * modelled by this toolkit (every layout runs left-to-right), so the orientation the
     * bundle names has nothing to apply to.
     *
     * @deprecated As of J2SE 1.4, replaced by {@code Component.applyComponentOrientation}.
     */
    @Deprecated
    public void applyResourceBundle(java.util.ResourceBundle rb) {
        java.util.Objects.requireNonNull(rb, "rb");
    }

    /** @deprecated As of J2SE 1.4, replaced by {@code Component.applyComponentOrientation}. */
    @Deprecated
    public void applyResourceBundle(String rbName) {
        applyResourceBundle(java.util.ResourceBundle.getBundle(rbName));
    }
    public void setLocationRelativeTo(Component c) {}
    public Type getType() { return Type.NORMAL; }
    public void setType(Type type) {}

    public enum Type { NORMAL, UTILITY, POPUP }
}
