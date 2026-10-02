package java.awt;

import java.awt.event.ComponentListener;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.ImageObserver;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Component shadow. Holds size, location, colour, font, listener lists — the data
 * AWT-using callers query. Painting and event dispatch are wired in at the host layer
 * (e.g. an Android View bridge), not here.
 */
public abstract class Component implements ImageObserver, Serializable {
    private static final long serialVersionUID = -7644114512714619750L;
    private static final Object TREE_LOCK = new Object();
    private boolean ignoreRepaint;

    public final Object getTreeLock() { return TREE_LOCK; }
    public void setIgnoreRepaint(boolean ignoreRepaint) { this.ignoreRepaint = ignoreRepaint; }
    public boolean getIgnoreRepaint() { return ignoreRepaint; }

    protected int x, y, width, height;
    // Defaults tuned for RuneLite's theme. On desktop FlatLaf populates
    // UIManager.getDefaults() so JPanel/JLabel inherit the dark theme; we don't run
    // FlatLaf's UI delegates, so a generic JPanel with no setBackground call would
    // render as WHITE (the Swing default) sitting next to OSRS's dark UI. Pick
    // ColorScheme.DARK_GRAY_COLOR (40,40,40) for bg and a near-white foreground so
    // unstyled labels stay readable on the inherited dark bg.
    protected Color foreground = new Color(0xC8, 0xC8, 0xC8);
    protected Color background = new Color(0x28, 0x28, 0x28);
    protected Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    protected boolean visible = true;
    protected boolean enabled = true;
    protected Container parent;
    /** Mouse cursor — Android can't actually show one but RuneLite plugins
     *  (CustomCursorPlugin, drag handlers) call setCursor/getCursor a lot and
     *  hit NoSuchMethodError if these aren't here. */
    protected Cursor cursor = Cursor.getDefaultCursor();

    public Cursor getCursor() { return cursor; }
    public void setCursor(Cursor cursor) { this.cursor = cursor; }

    // CopyOnWriteArrayList, so dispatch can iterate directly instead of taking a
    // defensive `new ArrayList<>(listeners)` snapshot on every event. Pointer moves
    // arrive at touch-sample rate and each one used to allocate a list plus its backing
    // array per listener type, per component in the dispatch chain. Registration is the
    // rare operation here and is the only thing that pays a copy now.
    private final List<ComponentListener> componentListeners = new CopyOnWriteArrayList<>();
    private final List<FocusListener> focusListeners = new CopyOnWriteArrayList<>();
    private final List<HierarchyListener> hierarchyListeners = new CopyOnWriteArrayList<>();
    private final List<KeyListener> keyListeners = new CopyOnWriteArrayList<>();
    private final List<MouseListener> mouseListeners = new CopyOnWriteArrayList<>();
    private final List<MouseMotionListener> mouseMotionListeners = new CopyOnWriteArrayList<>();
    private final List<MouseWheelListener> mouseWheelListeners = new CopyOnWriteArrayList<>();

    protected Component() {
    }

    public Container getParent()  { return parent; }
    void setParent(Container p)   { this.parent = p; }

    public int getX()             { return x; }
    public int getY()             { return y; }
    public int getWidth()         { return width; }
    public int getHeight()        { return height; }

    public Point getLocation()    { return new Point(x, y); }
    public Point getLocationOnScreen() { return getLocation(); }
    public Dimension getSize()    { return new Dimension(width, height); }

    private Dimension preferredSize;
    private Dimension minimumSize;
    private Dimension maximumSize;

    public Dimension getPreferredSize() { return preferredSize != null ? new Dimension(preferredSize) : getSize(); }
    public void setPreferredSize(Dimension d) { this.preferredSize = d == null ? null : new Dimension(d); invalidate(); }
    public Dimension getMinimumSize()   { return minimumSize != null ? new Dimension(minimumSize) : getSize(); }
    public void setMinimumSize(Dimension d) { this.minimumSize = d == null ? null : new Dimension(d); invalidate(); }
    public Dimension getMaximumSize()   { return maximumSize != null ? new Dimension(maximumSize) : new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    public void setMaximumSize(Dimension d) { this.maximumSize = d == null ? null : new Dimension(d); invalidate(); }
    public boolean isPreferredSizeSet() { return preferredSize != null; }
    public boolean isMinimumSizeSet() { return minimumSize != null; }
    public boolean isMaximumSizeSet() { return maximumSize != null; }
    public Rectangle getBounds()  { return new Rectangle(x, y, width, height); }

    public void setLocation(int x, int y) {
        boolean moved = this.x != x || this.y != y;
        this.x = x; this.y = y;
        if (moved) fireComponentMoved();
    }
    public void setLocation(Point p)            { setLocation(p.x, p.y); }
    public void setSize(int w, int h) {
        boolean resized = width != w || height != h;
        width = w; height = h;
        if (resized) { invalidate(); fireComponentResized(); }
    }
    public void setSize(Dimension d)            { setSize(d.width, d.height); }
    public void setBounds(int x, int y, int w, int h) {
        boolean moved = this.x != x || this.y != y;
        boolean resized = this.width != w || this.height != h;
        this.x = x; this.y = y; this.width = w; this.height = h;
        // Only a resize invalidates. Position is not an input to any layout manager in this
        // shim (they all read child preferred/min/max sizes, visibility, and the container's
        // own bounds + insets — never a child's origin), whereas size is: an unset preferred
        // size falls back to getSize(). The JDK invalidates on move too; doing that here
        // would just make every layout pass re-dirty the tree it had just laid out.
        if (resized) invalidate();
        if (moved) fireComponentMoved();
        if (resized) fireComponentResized();
    }
    public void setBounds(Rectangle r)          { setBounds(r.x, r.y, r.width, r.height); }

    /**
     * Notify registered {@link ComponentListener}s that this component just got bigger.
     * Real AWT fires this from its native peer when the OS reports a window resize; we
     * have no peer, so {@link #setSize}/{@link #setBounds} fire it manually. The OSRS
     * engine subscribes to this event on its Canvas to refresh its internal canvas-
     * dimension cache (`sd_fld`) — without the dispatch, `client.getRealDimensions()`
     * keeps returning the construction-time size, which clamps overlay-drag regions to
     * the left part of a wider mobile viewport.
     */
    private void fireComponentResized() {
        if (componentListeners.isEmpty()) return;
        java.awt.event.ComponentEvent e = new java.awt.event.ComponentEvent(
            this, java.awt.event.ComponentEvent.COMPONENT_RESIZED);
        for (java.awt.event.ComponentListener l : componentListeners) {
            try { l.componentResized(e); } catch (Throwable ignored) {}
        }
    }

    /** Companion to {@link #fireComponentResized} for setLocation/setBounds calls
     *  that change the (x, y) origin. The engine's resize listener doesn't act on
     *  this, but a few RL plugins do. */
    private void fireComponentMoved() {
        if (componentListeners.isEmpty()) return;
        java.awt.event.ComponentEvent e = new java.awt.event.ComponentEvent(
            this, java.awt.event.ComponentEvent.COMPONENT_MOVED);
        for (java.awt.event.ComponentListener l : componentListeners) {
            try { l.componentMoved(e); } catch (Throwable ignored) {}
        }
    }

    public Color getForeground()  { return foreground; }
    public void setForeground(Color c) { foreground = c; }
    public Color getBackground()  { return background; }
    public void setBackground(Color c) { background = c; }

    public Font getFont()         { return font; }
    public void setFont(Font f)   { if (f != null && !f.equals(font)) { font = f; invalidate(); } }

    public boolean isVisible()    { return visible; }
    public void setVisible(boolean v) {
        if (visible == v) return;
        visible = v;
        // Layout managers skip invisible children, so this changes the parent's layout.
        invalidate();
    }
    public boolean isEnabled()    { return enabled; }
    public void setEnabled(boolean e) { enabled = e; }
    public boolean isShowing()    { return visible && parent == null ? false : (parent != null ? parent.isShowing() : visible); }
    public boolean isDisplayable() { return true; }

    public Graphics getGraphics() {
        // Host-provided rendering target; none in the headless shadow.
        return null;
    }

    public Toolkit getToolkit() { return Toolkit.getDefaultToolkit(); }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE;
    }

    /** Process-wide FontMetrics cache keyed by Font. Creating a FontMetrics constructs an
     * android.graphics.Paint+Typeface and measures the ASCII width table; measureText then
     * crosses JNI into Minikin. With one cache miss per JLabel.getPreferredSize per frame
     * this was a ~40% hotspot.
     *
     * Deliberately the *same* cache BufferedImageGraphics2D.getFontMetrics uses. This class
     * used to keep its own parallel map, so every font in play was measured twice and held
     * two Paint + Typeface + width-table sets alive for the life of the process. */
    public FontMetrics getFontMetrics(Font f) {
        if (f == null) f = font;
        return net.runelite.awt.impl.BufferedImageFontMetrics.forFont(f);
    }

    public void repaint() { repaint(0, 0, 0, width, height); }
    public void repaint(long tm) { repaint(tm, 0, 0, width, height); }
    public void repaint(int x, int y, int width, int height) { repaint(0, x, y, width, height); }
    public void repaint(long tm, int x, int y, int width, int height) {
        // No-op until a host backend is plugged in.
    }

    public void paint(Graphics g) {
        // Default: fill with background if we have one. Subclasses extend.
        if (background != null && width > 0 && height > 0) {
            Color saved = g.getColor();
            g.setColor(background);
            g.fillRect(0, 0, width, height);
            if (saved != null) g.setColor(saved);
        }
    }
    public void update(Graphics g) { paint(g); }
    public void paintAll(Graphics g) { paint(g); }
    /**
     * Layout validity.
     *
     * {@link Window#renderToBackbuffer} used to call {@code validate()} every Compose frame,
     * which ran {@code doLayout()} over the whole component tree — every LayoutManager, every
     * frame, allocating an Insets plus a Dimension per child as it went. It did that because
     * {@code invalidate()} was a no-op stub and nothing tracked validity, so a brute-force
     * pass was the only way to be sure a mutation got picked up.
     *
     * Now the tree tracks validity the way the JDK does: a mutation marks the component and
     * every ancestor invalid, and {@code validate()} short-circuits on an already-valid
     * subtree. A steady-state frame does no layout work at all.
     *
     * Starts false so the very first pass always lays out.
     */
    private boolean valid;

    public boolean isValid() { return valid; }

    /** Package-private: {@link Container#validate()} marks the subtree valid at the end of
     *  a successful pass. */
    void setValid(boolean v) { valid = v; }

    /**
     * Mark this component — and, transitively, its ancestors — as needing layout.
     *
     * The early return relies on the invariant this method itself maintains: if a component
     * is invalid then so is every ancestor, so there is nothing left to propagate.
     */
    public void invalidate() {
        if (!valid) return;
        valid = false;
        invalidateParent();
    }

    /** Walk up marking ancestors invalid, stopping at the first already-invalid one — by the
     *  invariant above, everything above it is invalid too. */
    final void invalidateParent() {
        Container p = parent;
        while (p != null && p.isValid()) {
            p.setValid(false);
            p = p.getParent();
        }
    }

    /**
     * Swing's "please lay me out again". Real Swing hands this to the RepaintManager to run
     * on the EDT; here the host's per-frame {@link Container#validate()} is the pump, so all
     * this has to do is mark the tree dirty. RuneLite panels call it constantly after
     * mutating their contents — before validity tracking existed it was a no-op that happened
     * to work because every frame relaid everything.
     */
    public void revalidate() { invalidate(); }

    public void validate() { valid = true; }

    public void doLayout() {}

    public Image createImage(int w, int h) {
        // JDK Component.createImage returns an opaque image (TYPE_INT_RGB on most
        // platforms). The OSRS client allocates its main offscreen buffer this way
        // and writes raw RGB into it; if we returned TYPE_INT_ARGB the alpha bits
        // would be 0 and our SRC_OVER blits back to the canvas would skip every
        // pixel — invisible 3D scene + sprite atlases.
        return new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
    }

    public Image createImage(java.awt.image.ImageProducer producer) {
        return null;
    }

    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        return (infoflags & (ALLBITS | ABORT)) == 0;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean requestFocus(boolean temporary) { return false; }
    public void requestFocus() {}
    public boolean requestFocusInWindow() { return false; }
    public boolean isFocusOwner() { return false; }
    public void setFocusTraversalKeysEnabled(boolean v) {}
    public boolean getFocusTraversalKeysEnabled() { return true; }
    public void setFocusable(boolean focusable) {}
    public boolean isFocusable() { return true; }
    public void setFocusTraversalKeys(int id, java.util.Set<? extends java.awt.AWTKeyStroke> keys) {}
    public java.util.Set<java.awt.AWTKeyStroke> getFocusTraversalKeys(int id) { return java.util.Collections.emptySet(); }
    public boolean areFocusTraversalKeysSet(int id) { return false; }
    public void transferFocus() {}
    public void transferFocusUpCycle() {}
    public void transferFocusBackward() {}
    public java.awt.Container getFocusCycleRootAncestor() { return parent; }
    public boolean isFocusCycleRoot(java.awt.Container container) { return false; }

    public void addComponentListener(ComponentListener l)       { if (l != null) componentListeners.add(l); }
    public void removeComponentListener(ComponentListener l)    { componentListeners.remove(l); }
    public void addFocusListener(FocusListener l)               { if (l != null) focusListeners.add(l); }
    public void removeFocusListener(FocusListener l)            { focusListeners.remove(l); }
    public void addHierarchyListener(HierarchyListener l)       { if (l != null) hierarchyListeners.add(l); }
    public void removeHierarchyListener(HierarchyListener l)    { hierarchyListeners.remove(l); }
    public void addKeyListener(KeyListener l)                   { if (l != null) keyListeners.add(l); }
    public void removeKeyListener(KeyListener l)                { keyListeners.remove(l); }
    public void addMouseListener(MouseListener l)               { if (l != null) mouseListeners.add(l); }
    public void removeMouseListener(MouseListener l)            { mouseListeners.remove(l); }
    public void addMouseMotionListener(MouseMotionListener l)   { if (l != null) mouseMotionListeners.add(l); }
    public void removeMouseMotionListener(MouseMotionListener l) { mouseMotionListeners.remove(l); }
    public void addMouseWheelListener(MouseWheelListener l)     { if (l != null) mouseWheelListeners.add(l); }
    public void removeMouseWheelListener(MouseWheelListener l)  { mouseWheelListeners.remove(l); }

    private String name = "";
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name; }

    public ComponentListener[] getComponentListeners() { return componentListeners.toArray(new ComponentListener[0]); }
    public FocusListener[] getFocusListeners() { return focusListeners.toArray(new FocusListener[0]); }
    public HierarchyListener[] getHierarchyListeners() { return hierarchyListeners.toArray(new HierarchyListener[0]); }
    public KeyListener[] getKeyListeners() { return keyListeners.toArray(new KeyListener[0]); }
    public MouseListener[] getMouseListeners() { return mouseListeners.toArray(new MouseListener[0]); }
    public MouseMotionListener[] getMouseMotionListeners() { return mouseMotionListeners.toArray(new MouseMotionListener[0]); }
    public MouseWheelListener[] getMouseWheelListeners() { return mouseWheelListeners.toArray(new MouseWheelListener[0]); }

    /** True iff this component has at least one MouseListener/MotionListener/WheelListener
     *  registered. The Compose host uses this to walk up the parent chain from a tap target
     *  to find whichever component actually subscribes — the patched RS Applet registers
     *  its MouseListener on itself, but the deepest visible component at a click point is
     *  usually the inner Canvas which has no listeners of its own. */
    public boolean hasMouseListeners() {
        return !mouseListeners.isEmpty()
            || !mouseMotionListeners.isEmpty()
            || !mouseWheelListeners.isEmpty();
    }

    /**
     * Dispatches a {@link MouseEvent} to whichever listener list matches the event id.
     * Used by the Android host (Compose pointer-input bridge) to inject synthetic mouse
     * events into the obfuscated client, which subscribes to these via the standard
     * {@link #addMouseListener} / {@link #addMouseMotionListener} APIs.
     */
    public void dispatchMouseEvent(MouseEvent e) {
        if (e == null) return;
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                for (MouseListener l : mouseListeners) l.mousePressed(e);
                break;
            case MouseEvent.MOUSE_RELEASED:
                for (MouseListener l : mouseListeners) l.mouseReleased(e);
                break;
            case MouseEvent.MOUSE_CLICKED:
                for (MouseListener l : mouseListeners) l.mouseClicked(e);
                break;
            case MouseEvent.MOUSE_ENTERED:
                for (MouseListener l : mouseListeners) l.mouseEntered(e);
                break;
            case MouseEvent.MOUSE_EXITED:
                for (MouseListener l : mouseListeners) l.mouseExited(e);
                break;
            case MouseEvent.MOUSE_MOVED:
                for (MouseMotionListener l : mouseMotionListeners) l.mouseMoved(e);
                break;
            case MouseEvent.MOUSE_DRAGGED:
                for (MouseMotionListener l : mouseMotionListeners) l.mouseDragged(e);
                break;
            case MouseEvent.MOUSE_WHEEL:
                if (e instanceof MouseWheelEvent) {
                    MouseWheelEvent we = (MouseWheelEvent) e;
                    for (MouseWheelListener l : mouseWheelListeners) l.mouseWheelMoved(we);
                }
                break;
        }
    }

    /** Dispatches a {@link KeyEvent} to the registered {@link KeyListener}s. */
    public void dispatchKeyEvent(KeyEvent e) {
        if (e == null) return;
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED:
                for (KeyListener l : keyListeners) l.keyPressed(e);
                break;
            case KeyEvent.KEY_RELEASED:
                for (KeyListener l : keyListeners) l.keyReleased(e);
                break;
            case KeyEvent.KEY_TYPED:
                for (KeyListener l : keyListeners) l.keyTyped(e);
                break;
        }
    }
}
