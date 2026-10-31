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
public abstract class Component implements ImageObserver, Serializable, MenuContainer {
    private static final long serialVersionUID = -7644114512714619750L;
    private static final Object TREE_LOCK = new Object();
    private boolean ignoreRepaint;
    private final java.util.List<PopupMenu> popupMenus = new java.util.ArrayList<>();
    public void add(PopupMenu popup) {
        if (popup == null) throw new NullPointerException("popup");
        if (popup.parent != null) popup.parent.remove(popup);
        popupMenus.add(popup); popup.parent = this;
    }
    public void remove(MenuComponent menu) { if (popupMenus.remove(menu)) menu.parent = null; }
    private java.beans.PropertyChangeSupport propertyChanges;
    private synchronized java.beans.PropertyChangeSupport properties() {
        if (propertyChanges == null) propertyChanges = new java.beans.PropertyChangeSupport(this);
        return propertyChanges;
    }
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) { properties().addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) { properties().removePropertyChangeListener(listener); }
    public void addPropertyChangeListener(String name, java.beans.PropertyChangeListener listener) { properties().addPropertyChangeListener(name, listener); }
    public void removePropertyChangeListener(String name, java.beans.PropertyChangeListener listener) { properties().removePropertyChangeListener(name, listener); }
    public java.beans.PropertyChangeListener[] getPropertyChangeListeners() { return properties().getPropertyChangeListeners(); }
    public java.beans.PropertyChangeListener[] getPropertyChangeListeners(String name) { return properties().getPropertyChangeListeners(name); }
    public void firePropertyChange(String name, Object oldValue, Object newValue) { if (!java.util.Objects.equals(oldValue, newValue)) repaint(); if (propertyChanges != null) propertyChanges.firePropertyChange(name, oldValue, newValue); }
    public void firePropertyChange(String name, boolean oldValue, boolean newValue) { if (oldValue != newValue) firePropertyChange(name, Boolean.valueOf(oldValue), Boolean.valueOf(newValue)); }
    public void firePropertyChange(String name, int oldValue, int newValue) { if (oldValue != newValue) firePropertyChange(name, Integer.valueOf(oldValue), Integer.valueOf(newValue)); }
    public void firePropertyChange(String name, byte oldValue, byte newValue) { if (oldValue != newValue) firePropertyChange(name, Byte.valueOf(oldValue), Byte.valueOf(newValue)); }
    public void firePropertyChange(String name, char oldValue, char newValue) { if (oldValue != newValue) firePropertyChange(name, Character.valueOf(oldValue), Character.valueOf(newValue)); }
    public void firePropertyChange(String name, short oldValue, short newValue) { if (oldValue != newValue) firePropertyChange(name, Short.valueOf(oldValue), Short.valueOf(newValue)); }
    public void firePropertyChange(String name, long oldValue, long newValue) { if (oldValue != newValue) firePropertyChange(name, Long.valueOf(oldValue), Long.valueOf(newValue)); }
    public void firePropertyChange(String name, float oldValue, float newValue) { if (oldValue != newValue) firePropertyChange(name, Float.valueOf(oldValue), Float.valueOf(newValue)); }
    public void firePropertyChange(String name, double oldValue, double newValue) { if (oldValue != newValue) firePropertyChange(name, Double.valueOf(oldValue), Double.valueOf(newValue)); }

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
    private boolean hostShowing;
    /** Visibility of a detached component tree mounted directly by the Android host. */
    public void setHostShowing(boolean showing) {
        boolean previous = isShowing(); hostShowing = showing;
        if (!showing) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        if (previous != isShowing()) fireHierarchyChanged(this, parent, java.awt.event.HierarchyEvent.SHOWING_CHANGED);
    }
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
    void setParent(Container p) {
        Container previous = parent;
        if (previous == p) return;
        boolean showing = isShowing();
        this.parent = p;
        if (p == null) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        fireHierarchyChanged(this, p != null ? p : previous,
            java.awt.event.HierarchyEvent.PARENT_CHANGED
            | (showing != isShowing() ? java.awt.event.HierarchyEvent.SHOWING_CHANGED : 0));
    }

    private void fireHierarchyChanged(Component changed, Container changedParent, long flags) {
        dispatchEvent(new java.awt.event.HierarchyEvent(this,
            java.awt.event.HierarchyEvent.HIERARCHY_CHANGED, changed, changedParent, flags));
        if (this instanceof Container) {
            for (Component child : ((Container) this).getComponents())
                child.fireHierarchyChanged(changed, changedParent, flags);
        }
    }

    public int getX()             { return x; }
    public int getY()             { return y; }
    public int getWidth()         { return width; }
    public int getHeight()        { return height; }

    public Point getLocation()    { return new Point(x, y); }
    /**
     * "Screen" is the coordinate space of the topmost ancestor: every origin up the chain
     * is summed, including the root's own location (a Window's bounds are its screen
     * position on desktop). A JViewport scrolls its view via a negative location, so
     * scrolled content comes out right. The JDK throws when not showing; hosts here
     * mount and paint trees that are never "shown", so this always answers.
     */
    public Point getLocationOnScreen() {
        int sx = 0, sy = 0;
        for (Component c = this; c != null; c = c.parent) { sx += c.x; sy += c.y; }
        return new Point(sx, sy);
    }
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
        if (moved) { repaint(); fireComponentMoved(); }
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
        if (moved) { repaint(); fireComponentMoved(); }
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
    public void setForeground(Color c) { Color old = foreground; foreground = c; firePropertyChange("foreground", old, c); }
    public Color getBackground()  { return background; }
    public void setBackground(Color c) { Color old = background; background = c; firePropertyChange("background", old, c); }

    public Font getFont()         { return font; }
    public void setFont(Font f)   { if (f != null && !f.equals(font)) { Font old = font; font = f; invalidate(); firePropertyChange("font", old, f); } }

    public boolean isVisible()    { return visible; }
    public void setVisible(boolean v) {
        if (visible == v) return;
        boolean showing = isShowing();
        visible = v;
        if (!v) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        dispatchEvent(new java.awt.event.ComponentEvent(this, v
            ? java.awt.event.ComponentEvent.COMPONENT_SHOWN : java.awt.event.ComponentEvent.COMPONENT_HIDDEN));
        if (showing != isShowing()) fireHierarchyChanged(this, parent,
            java.awt.event.HierarchyEvent.SHOWING_CHANGED);
        // Layout managers skip invisible children, so this changes the parent's layout.
        invalidate();
    }
    public boolean isEnabled()    { return enabled; }
    public void setEnabled(boolean e) { boolean old = enabled; enabled = e; if (!e) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this); firePropertyChange("enabled", old, e); }
    public boolean isShowing()    { return visible && (hostShowing || parent != null && parent.isShowing()); }
    public boolean isDisplayable() { return true; }
    /** Android components have no native AWT peer to dispose; discard layout validity. */
    public void removeNotify() { invalidate(); }

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
        long generation = repaintSequence.incrementAndGet();
        for (Component c = this; c != null; c = c.parent) c.subtreePaintGeneration = generation;
    }

    private static final java.util.concurrent.atomic.AtomicLong repaintSequence = new java.util.concurrent.atomic.AtomicLong();
    private volatile long subtreePaintGeneration;
    /** Repaint requests within this tree, consumed by detached Android panel hosts. */
    public final long subtreePaintGeneration() { return subtreePaintGeneration; }

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
     * Propagate measurement changes even through already-invalid ancestors: their
     * layout managers may have cached sizes since the previous change.
     */
    public void invalidate() {
        repaint();
        // Bumped even when already invalid: a second change (new text on a label that is
        // still pending layout) must still discard measurements layouts have cached.
        layoutGeneration++;
        for (Component c = this; c != null; c = c.parent) {
            c.subtreeLayoutGeneration++;
            c.valid = false;
        }
    }

    /** Global invalidation counter retained for existing callers. */
    private static int layoutGeneration;
    private long subtreeLayoutGeneration;

    /** Changes confined to this component and its descendants, excluding detached renderers. */
    public final long subtreeLayoutGeneration() { return subtreeLayoutGeneration; }

    public static int layoutGeneration() { return layoutGeneration; }

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
        return new java.awt.image.ProducerImage(producer);
    }

    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        if ((infoflags & (SOMEBITS | FRAMEBITS | ALLBITS)) != 0) repaint();
        return (infoflags & (ALLBITS | ABORT)) == 0;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean requestFocus(boolean temporary) { return requestFocusInWindow(); }
    public void requestFocus() { requestFocusInWindow(); }
    public boolean requestFocusInWindow() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().requestFocus(this); }
    public boolean isFocusOwner() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == this; }
    public float getAlignmentX() { return 0.5f; }
    public float getAlignmentY() { return 0.5f; }
    public boolean hasFocus() { return isFocusOwner(); }
    private boolean focusable = true, focusTraversalKeysEnabled = true;
    private final java.util.Map<Integer, java.util.Set<AWTKeyStroke>> traversalKeys = new java.util.HashMap<>();
    public void setFocusTraversalKeysEnabled(boolean v) { focusTraversalKeysEnabled = v; }
    public boolean getFocusTraversalKeysEnabled() { return focusTraversalKeysEnabled; }
    public void setFocusable(boolean focusable) {
        boolean old = this.focusable;
        this.focusable = focusable;
        if (!focusable) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        firePropertyChange("focusable", old, focusable);
    }
    public boolean isFocusable() { return focusable; }
    public void setFocusTraversalKeys(int id, java.util.Set<? extends AWTKeyStroke> keys) {
        if (id < 0 || id > 3) throw new IllegalArgumentException("invalid traversal key id");
        if (keys == null) { traversalKeys.remove(id); return; }
        java.util.Set<AWTKeyStroke> copy = new java.util.HashSet<>(keys);
        for (AWTKeyStroke key : copy)
            if (key == null || key.getKeyEventType() == KeyEvent.KEY_TYPED)
                throw new IllegalArgumentException("invalid traversal key");
        traversalKeys.put(id, java.util.Collections.unmodifiableSet(copy));
    }
    public java.util.Set<AWTKeyStroke> getFocusTraversalKeys(int id) {
        if (id < 0 || id > 3) throw new IllegalArgumentException("invalid traversal key id");
        java.util.Set<AWTKeyStroke> keys = traversalKeys.get(id);
        return keys != null ? keys : parent != null ? parent.getFocusTraversalKeys(id) : java.util.Collections.emptySet();
    }
    public boolean areFocusTraversalKeysSet(int id) {
        if (id < 0 || id > 3) throw new IllegalArgumentException("invalid traversal key id");
        return traversalKeys.containsKey(id);
    }
    public void transferFocus() { transferFocus(false); }
    public void transferFocusUpCycle() { if (parent != null) parent.requestFocusInWindow(); }
    public void transferFocusBackward() { transferFocus(true); }
    private void transferFocus(boolean backwards) {
        Component root = this;
        while (root.parent != null) root = root.parent;
        java.util.List<Component> candidates = new java.util.ArrayList<>();
        collectFocusable(root, candidates);
        int size = candidates.size(), index = candidates.indexOf(this);
        for (int offset = 1; offset <= size; offset++) {
            Component next = candidates.get(Math.floorMod(index + (backwards ? -offset : offset), size));
            if (next.requestFocusInWindow()) return;
        }
    }
    private static void collectFocusable(Component component, java.util.List<Component> out) {
        if (!component.isVisible() || !component.isEnabled()) return;
        if (component.isFocusable()) out.add(component);
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) collectFocusable(child, out);
    }
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
    public void setName(String name) { String old = this.name; this.name = name; firePropertyChange("name", old, name); }

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

    /** Desktop entry point for plugins sending input to a component's listeners. */
    public final void dispatchEvent(AWTEvent event) {
        if (event == null) throw new NullPointerException("event");
        AWTEvent previous = EventQueue.beginDispatch(event);
        try { processEvent(event); } finally { EventQueue.endDispatch(previous); }
    }

    protected void processEvent(AWTEvent event) {
        if (event instanceof KeyEvent) {
            KeyEvent key = (KeyEvent) event;
            if (!key.isConsumed()
                    && !KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(key)
                    && !key.isConsumed()) {
                dispatchKeyEvent(key);
            }
        } else if (event instanceof MouseEvent) {
            dispatchMouseEvent((MouseEvent) event);
        } else if (event instanceof java.awt.event.FocusEvent) {
            processFocusEvent((java.awt.event.FocusEvent) event);
        } else if (event instanceof java.awt.event.HierarchyEvent) {
            processHierarchyEvent((java.awt.event.HierarchyEvent) event);
        } else if (event instanceof java.awt.event.ComponentEvent) {
            processComponentEvent((java.awt.event.ComponentEvent) event);
        }
    }

    protected void processFocusEvent(java.awt.event.FocusEvent event) {
        for (FocusListener listener : focusListeners) {
            if (event.getID() == java.awt.event.FocusEvent.FOCUS_GAINED) listener.focusGained(event);
            else if (event.getID() == java.awt.event.FocusEvent.FOCUS_LOST) listener.focusLost(event);
        }
    }
    protected void processHierarchyEvent(java.awt.event.HierarchyEvent event) {
        for (HierarchyListener listener : hierarchyListeners) listener.hierarchyChanged(event);
    }
    protected void processComponentEvent(java.awt.event.ComponentEvent event) {
        for (ComponentListener listener : componentListeners) switch (event.getID()) {
            case java.awt.event.ComponentEvent.COMPONENT_SHOWN: listener.componentShown(event); break;
            case java.awt.event.ComponentEvent.COMPONENT_HIDDEN: listener.componentHidden(event); break;
            case java.awt.event.ComponentEvent.COMPONENT_MOVED: listener.componentMoved(event); break;
            case java.awt.event.ComponentEvent.COMPONENT_RESIZED: listener.componentResized(event); break;
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
