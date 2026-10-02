package java.awt;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

public class Window extends Container {
    private static final long serialVersionUID = 4497834738069338734L;

    private static final AtomicReference<Window> LATEST = new AtomicReference<>();
    private static final java.util.List<java.lang.ref.WeakReference<Window>> ALL_WINDOWS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    private java.util.List<Image> iconImages = new java.util.ArrayList<>();
    private BufferedImage backbuffer;
    private Color windowBackground = new Color(0x1E1F22);

    public Window() {
        LATEST.set(this);
        ALL_WINDOWS.add(new java.lang.ref.WeakReference<>(this));
    }

    public Window(Frame owner) {
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
        super.setBounds(x, y, w, h);
        ensureBackbuffer();
        doLayout();
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
            g.translate(c.getX(), c.getY());
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
                    for (int i = 0; i < count; i++) hostPaint(g, cont.getComponent(i));
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
                for (int i = 0; i < count; i++) hostPaint(g, cont.getComponent(i));
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
    @Override
    public void setBackground(Color c) { if (c != null) windowBackground = c; }

    public void pack() {
        // Walk our own tree for a preferred size. If it's positive, snap our bounds to it.
        // Window itself contains rootPane (for JFrame) — whose preferred size cascades down
        // through BorderLayout → contentPane → ClientUI.Layout → clientPanel/sidebar.
        Dimension d = getPreferredSize();
        if (d != null && d.width > 0 && d.height > 0) {
            setSize(d.width, d.height);
        }
    }
    public void show() { setVisible(true); }
    public void dispose() {
        // Mark invisible so picker logic stops choosing this window (e.g. splash after dispose).
        setVisible(false);
    }

    @Override
    public void setVisible(boolean v) {
        boolean wasVisible = isVisible();
        super.setVisible(v);
        if (v && !wasVisible) {
            // Real Swing validates the tree on first showing.
            try { validate(); } catch (Throwable ignored) {}
        }
    }
    public boolean isActive() { return false; }
    public boolean isFocused() { return false; }

    public void setIconImage(Image image) {
        iconImages.clear();
        if (image != null) iconImages.add(image);
    }
    public Image getIconImage() { return iconImages.isEmpty() ? null : iconImages.get(0); }

    public void setIconImages(java.util.List<? extends Image> icons) {
        iconImages = (icons == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(icons);
    }
    public java.util.List<Image> getIconImages() { return new java.util.ArrayList<>(iconImages); }

    public Window getOwner() { return null; }
    public Window[] getOwnedWindows() { return new Window[0]; }
    public static Window[] getWindows() { return new Window[0]; }
    public static Window[] getOwnerlessWindows() { return new Window[0]; }
    public void toFront() {}
    public void toBack() {}

    public void addWindowListener(java.awt.event.WindowListener l) {}
    public void removeWindowListener(java.awt.event.WindowListener l) {}
    public java.awt.event.WindowListener[] getWindowListeners() { return new java.awt.event.WindowListener[0]; }
    public void addWindowFocusListener(java.awt.event.WindowFocusListener l) {}
    public void removeWindowFocusListener(java.awt.event.WindowFocusListener l) {}
    public java.awt.event.WindowFocusListener[] getWindowFocusListeners() { return new java.awt.event.WindowFocusListener[0]; }
    public void addWindowStateListener(java.awt.event.WindowStateListener l) {}
    public void removeWindowStateListener(java.awt.event.WindowStateListener l) {}
    public java.awt.event.WindowStateListener[] getWindowStateListeners() { return new java.awt.event.WindowStateListener[0]; }
    public void setOpacity(float opacity) {}
    public float getOpacity() { return 1.0f; }
    public void setShape(java.awt.Shape shape) {}
    public java.awt.Shape getShape() { return null; }
    public void setAutoRequestFocus(boolean autoRequestFocus) {}
    public boolean isAutoRequestFocus() { return false; }
    public void setAlwaysOnTop(boolean alwaysOnTop) {}
    public boolean isAlwaysOnTop() { return false; }
    public boolean isAlwaysOnTopSupported() { return false; }
    public boolean isFocusableWindow() { return false; }
    public void setFocusableWindowState(boolean state) {}
    public boolean getFocusableWindowState() { return true; }
    public void setLocationRelativeTo(Component c) {}
    public Type getType() { return Type.NORMAL; }
    public void setType(Type type) {}

    public enum Type { NORMAL, UTILITY, POPUP }
}
