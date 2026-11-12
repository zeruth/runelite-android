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

import java.awt.event.ComponentListener;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
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
    /** The AWT tree lock; package-private as on the JDK so MenuComponent.getTreeLock shares it. */
    static final Object LOCK = new Object();
    private boolean ignoreRepaint;
    private final java.util.List<PopupMenu> popupMenus = new java.util.ArrayList<>();
    public void add(PopupMenu popup) {
        if (popup == null) throw new NullPointerException("popup");
        if (popup.parent != null) popup.parent.remove(popup);
        popupMenus.add(popup); popup.parent = this;
    }
    public void remove(MenuComponent menu) { if (popupMenus.remove(menu)) menu.parent = null; }

    /** The JDK's baseline resize behaviours, as returned by {@link #getBaselineResizeBehavior}. */
    public enum BaselineResizeBehavior {
        CONSTANT_ASCENT,
        CONSTANT_DESCENT,
        CENTER_OFFSET,
        OTHER
    }

    /**
     * Returns the baseline for {@code width} x {@code height}, or a value
     * less than 0 when the component has none; the JDK's Component has none.
     */
    public int getBaseline(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be >= 0");
        }
        return -1;
    }

    public BaselineResizeBehavior getBaselineResizeBehavior() {
        return BaselineResizeBehavior.OTHER;
    }

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

    public final Object getTreeLock() { return LOCK; }
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
        if (showing != notified) { if (showing) addNotify(); else removeNotify(); }
        if (!showing) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(this);
        if (previous != isShowing()) fireHierarchyChanged(this, parent, java.awt.event.HierarchyEvent.SHOWING_CHANGED);
    }
    protected Container parent;
    /** Mouse cursor — Android can't actually show one but RuneLite plugins
     *  (CustomCursorPlugin, drag handlers) call setCursor/getCursor a lot and
     *  hit NoSuchMethodError if these aren't here. Null until set, so an unset
     *  component inherits its parent's cursor as on the JDK. */
    protected Cursor cursor;

    public Cursor getCursor() {
        Cursor c = cursor;
        for (Component comp = this; c == null; c = comp.cursor) {
            if (comp instanceof Window) return Cursor.getDefaultCursor();
            comp = comp.parent;
            if (comp == null) return Cursor.getDefaultCursor();
        }
        return c;
    }
    public void setCursor(Cursor cursor) { this.cursor = cursor; }
    /** Whether a cursor was set on this component itself. */
    public boolean isCursorSet() { return cursor != null; }

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
    private final List<InputMethodListener> inputMethodListeners = new CopyOnWriteArrayList<>();
    private boolean inputMethodsEnabled = true;
    // The JDK gives every Window one input context shared by its tree. Android panel trees are
    // mounted without a Window (setHostShowing), so their root holds the shared context instead.
    private transient java.awt.im.InputContext rootInputContext;

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
        if (p == null) { if (notified) removeNotify(); }
        else if (p.notified && !notified) addNotify();
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

    // As in the JDK, moving and resizing are setBounds, so a subclass overriding setBounds
    // or reshape sees them; reshape fires the moved/resized and ancestor events. Not through
    // move/resize: Applet.resize calls setSize.
    public void setLocation(int x, int y)       { setBounds(x, y, width, height); }
    public void setLocation(Point p)            { setLocation(p.x, p.y); }
    public void setSize(int w, int h)           { setBounds(x, y, w, h); }
    public void setSize(Dimension d)            { setSize(d.width, d.height); }
    /** @deprecated As of JDK version 1.1, replaced by {@code setLocation(int, int)}. */
    @Deprecated
    public void move(int x, int y) { setLocation(x, y); }
    /** @deprecated As of JDK version 1.1, replaced by {@code setSize(int, int)}. */
    @Deprecated
    public void resize(int width, int height) { setSize(width, height); }
    /** @deprecated As of JDK version 1.1, replaced by {@code setSize(Dimension)}. */
    @Deprecated
    public void resize(Dimension d) { setSize(d.width, d.height); }

    /** The JDK's setBounds is reshape, so a subclass overriding reshape (JViewport-style) sees every setBounds. */
    public void setBounds(int x, int y, int w, int h) { reshape(x, y, w, h); }

    /** @deprecated As of JDK version 1.1, replaced by {@code setBounds(int, int, int, int)}. */
    @Deprecated
    public void reshape(int x, int y, int w, int h) {
        boolean moved = this.x != x || this.y != y;
        boolean resized = this.width != w || this.height != h;
        this.x = x; this.y = y; this.width = w; this.height = h;
        // Only a resize invalidates. Position is not an input to any layout manager in this
        // shim (they all read child preferred/min/max sizes, visibility, and the container's
        // own bounds + insets — never a child's origin), whereas size is: an unset preferred
        // size falls back to getSize(). The JDK invalidates on move too; doing that here
        // would just make every layout pass re-dirty the tree it had just laid out.
        if (resized) invalidate();
        if (moved) { repaint(); fireComponentMoved(); fireAncestorBounds(java.awt.event.HierarchyEvent.ANCESTOR_MOVED); }
        if (resized) { fireComponentResized(); fireAncestorBounds(java.awt.event.HierarchyEvent.ANCESTOR_RESIZED); }
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
    /** Set while this tree is mounted, so subclasses get the same addNotify/removeNotify calls as on the JDK. */
    boolean notified;
    /** Android components have no native AWT peer to create; discard layout validity. */
    public void addNotify() { notified = true; invalidate(); }
    /** Android components have no native AWT peer to dispose; discard layout validity. */
    public void removeNotify() { notified = false; invalidate(); }

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
    /** Paints this component and its subcomponents, when showing. */
    public void paintAll(Graphics g) {
        if (isShowing()) {
            paint(g);
        }
    }
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

    // Unscaled images only: the JDK's -1/-1 request is the image's own size, which is the only
    // size a produced image has here. MediaTracker goes through these.
    public boolean prepareImage(Image image, java.awt.image.ImageObserver observer) {
        return prepareImage(image, -1, -1, observer);
    }

    public boolean prepareImage(Image image, int width, int height, java.awt.image.ImageObserver observer) {
        return Toolkit.getDefaultToolkit().prepareImage(image, width, height, observer);
    }

    public int checkImage(Image image, java.awt.image.ImageObserver observer) {
        return checkImage(image, -1, -1, observer);
    }

    public int checkImage(Image image, int width, int height, java.awt.image.ImageObserver observer) {
        return Toolkit.getDefaultToolkit().checkImage(image, width, height, observer);
    }

    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        if ((infoflags & (SOMEBITS | FRAMEBITS | ALLBITS)) != 0) repaint();
        return (infoflags & (ALLBITS | ABORT)) == 0;
    }

    /**
     * Checks whether this component "contains" the specified point, where {@code x} and
     * {@code y} are relative to this component's coordinate system.
     */
    public boolean contains(int x, int y) {
        return inside(x, y);
    }

    /** @deprecated As of JDK version 1.1, replaced by contains(int, int). */
    @Deprecated
    public boolean inside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean contains(Point p) {
        return contains(p.x, p.y);
    }

    /** Returns this component when it contains the point, otherwise {@code null}. */
    public Component getComponentAt(int x, int y) {
        return locate(x, y);
    }

    /** @deprecated As of JDK version 1.1, replaced by getComponentAt(int, int). */
    @Deprecated
    public Component locate(int x, int y) {
        return contains(x, y) ? this : null;
    }

    public Component getComponentAt(Point p) {
        return getComponentAt(p.x, p.y);
    }

    /**
     * Opaque means "paints every pixel of its bounds": true for a native widget once it has
     * its (here notional) peer, i.e. after addNotify, false before that and for lightweight
     * components, which JComponent refines with setOpaque.
     */
    public boolean isOpaque() {
        return notified && !isLightweight();
    }

    boolean isRecursivelyVisible() {
        return visible && (parent == null || parent.isRecursivelyVisible());
    }

    /** Component's version ignores children: only the component itself counts. */
    boolean isSameOrAncestorOf(Component comp, boolean allowChildren) {
        return comp == this;
    }

    /** The topmost ancestor: a Window on desktop, a host-mounted panel root on Android. */
    final Component getRoot() {
        Component root = this;
        while (root.parent != null) root = root.parent;
        return root;
    }

    /**
     * The JDK asks the native pointer peer whether this component's window is under the
     * mouse, then searches that window. Here the "window" is the tree the last host pointer
     * event was dispatched into ({@link MouseInfo}): another tree, or no pointer yet, means
     * the pointer is somewhere else.
     */
    final Component findUnderMouseInWindow(PointerInfo pi) {
        if (!isShowing()) {
            return null;
        }
        Component root = getRoot();
        if (!MouseInfo.isUnderPointer(root)) {
            return null;
        }
        Point p = root.pointRelativeToComponent(pi.getLocation());
        if (root instanceof Container) {
            return ((Container) root).findComponentAt(p.x, p.y, true);
        }
        return root.contains(p.x, p.y) ? root : null;
    }

    final Point pointRelativeToComponent(Point point) {
        Point compCoords = getLocationOnScreen();
        return new Point(point.x - compCoords.x, point.y - compCoords.y);
    }

    /**
     * Returns the position of the pointer in this component's coordinates when the
     * component is directly under it (not obscured by a child or sibling), else
     * {@code null}. The JDK throws HeadlessException in a headless toolkit; the Android
     * host always has a pointer, so this answers from the last host pointer event.
     */
    public Point getMousePosition() throws HeadlessException {
        PointerInfo pi = MouseInfo.getPointerInfo();
        synchronized (getTreeLock()) {
            Component inTheSameWindow = findUnderMouseInWindow(pi);
            if (!isSameOrAncestorOf(inTheSameWindow, true)) {
                return null;
            }
            return pointRelativeToComponent(pi.getLocation());
        }
    }

    private ComponentOrientation componentOrientation = ComponentOrientation.UNKNOWN;

    /**
     * Sets the language-sensitive orientation used to order elements or text within this
     * component; layout managers such as FlowLayout read it. Fires a
     * "componentOrientation" property change.
     */
    public void setComponentOrientation(ComponentOrientation o) {
        ComponentOrientation oldValue = componentOrientation;
        componentOrientation = o;
        firePropertyChange("componentOrientation", oldValue, o);
        invalidate();
    }

    public ComponentOrientation getComponentOrientation() {
        return componentOrientation;
    }

    /** Sets the orientation of this component and, for a container, of every descendant. */
    public void applyComponentOrientation(ComponentOrientation orientation) {
        if (orientation == null) {
            throw new NullPointerException();
        }
        setComponentOrientation(orientation);
    }

    /** Enumeration of the common ways the baseline of a component can change as the size changes. */
    public enum BaselineResizeBehavior {
        CONSTANT_ASCENT,
        CONSTANT_DESCENT,
        CENTER_OFFSET,
        OTHER
    }

    /**
     * Returns the baseline, measured from the top, or a value &lt; 0 if this component has
     * none; the default has none.
     *
     * @throws IllegalArgumentException if width or height is &lt; 0
     */
    public int getBaseline(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be >= 0");
        }
        return -1;
    }

    public BaselineResizeBehavior getBaselineResizeBehavior() {
        return BaselineResizeBehavior.OTHER;
    }

    /** Prints this component; by default it paints. */
    public void print(Graphics g) {
        paint(g);
    }

    /** Prints this component and its subcomponents, when showing. */
    public void printAll(Graphics g) {
        if (isShowing()) {
            print(g);
        }
    }

    /**
     * Returns the listeners of the given type registered on this component, an empty array
     * of that type when there are none.
     *
     * @throws ClassCastException if listenerType is not a java.util.EventListener
     */
    @SuppressWarnings("unchecked")
    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        List<? extends java.util.EventListener> l = null;
        if (listenerType == ComponentListener.class) {
            l = componentListeners;
        } else if (listenerType == FocusListener.class) {
            l = focusListeners;
        } else if (listenerType == HierarchyListener.class) {
            l = hierarchyListeners;
        } else if (listenerType == KeyListener.class) {
            l = keyListeners;
        } else if (listenerType == MouseListener.class) {
            l = mouseListeners;
        } else if (listenerType == MouseMotionListener.class) {
            l = mouseMotionListeners;
        } else if (listenerType == MouseWheelListener.class) {
            l = mouseWheelListeners;
        } else if (listenerType == InputMethodListener.class) {
            l = inputMethodListeners;
        } else if (listenerType == java.beans.PropertyChangeListener.class) {
            return (T[]) getPropertyChangeListeners();
        }
        if (!java.util.EventListener.class.isAssignableFrom(listenerType)) {
            throw new ClassCastException(listenerType.getName());
        }
        T[] result = (T[]) java.lang.reflect.Array.newInstance(listenerType, 0);
        return l == null ? result : l.toArray(result);
    }

    // --- focus (after OpenJDK 8u Component; requests complete synchronously, see KeyboardFocusManager) ---

    public void requestFocus() { requestFocusHelper(false, java.awt.event.FocusEvent.Cause.UNKNOWN); }
    // JDK 9+ (9)
    public void requestFocus(java.awt.event.FocusEvent.Cause cause) { requestFocusHelper(false, cause); }
    /** Protected on the JDK; public here because host code calls it on plain Components. */
    public boolean requestFocus(boolean temporary) { return requestFocusHelper(temporary, java.awt.event.FocusEvent.Cause.UNKNOWN); }
    // JDK 9+ (9)
    protected boolean requestFocus(boolean temporary, java.awt.event.FocusEvent.Cause cause) { return requestFocusHelper(temporary, cause); }
    public boolean requestFocusInWindow() { return requestFocusHelper(false, java.awt.event.FocusEvent.Cause.UNKNOWN); }
    // JDK 9+ (9)
    public boolean requestFocusInWindow(java.awt.event.FocusEvent.Cause cause) { return requestFocusHelper(false, cause); }
    protected boolean requestFocusInWindow(boolean temporary) { return requestFocusHelper(temporary, java.awt.event.FocusEvent.Cause.UNKNOWN); }
    // There is one native window on Android, so a request may always change the focused Window:
    // requestFocus and requestFocusInWindow behave alike.
    private boolean requestFocusHelper(boolean temporary, java.awt.event.FocusEvent.Cause cause) {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().requestFocus(this, cause, temporary);
    }
    public boolean isFocusOwner() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == this; }
    public float getAlignmentX() { return 0.5f; }
    public float getAlignmentY() { return 0.5f; }
    public boolean hasFocus() { return isFocusOwner(); }

    private static final int FOCUS_TRAVERSABLE_UNKNOWN = 0, FOCUS_TRAVERSABLE_DEFAULT = 1, FOCUS_TRAVERSABLE_SET = 2;
    private static final String[] FOCUS_TRAVERSAL_KEY_PROPERTY_NAMES = {
        "forwardFocusTraversalKeys", "backwardFocusTraversalKeys", "upCycleFocusTraversalKeys", "downCycleFocusTraversalKeys"
    };
    private boolean focusable = true, focusTraversalKeysEnabled = true;
    // UNKNOWN until isFocusTraversable runs un-overridden: a subclass overriding the deprecated
    // isFocusTraversable keeps it UNKNOWN, which DefaultFocusTraversalPolicy reads as explicit.
    private int isFocusTraversableOverridden = FOCUS_TRAVERSABLE_UNKNOWN;
    @SuppressWarnings("unchecked")
    private final java.util.Set<AWTKeyStroke>[] focusTraversalKeys = new java.util.Set[KeyboardFocusManager.TRAVERSAL_KEY_LENGTH];

    public void setFocusTraversalKeysEnabled(boolean focusTraversalKeysEnabled) {
        boolean old = this.focusTraversalKeysEnabled;
        this.focusTraversalKeysEnabled = focusTraversalKeysEnabled;
        firePropertyChange("focusTraversalKeysEnabled", old, focusTraversalKeysEnabled);
    }
    public boolean getFocusTraversalKeysEnabled() { return focusTraversalKeysEnabled; }
    public void setFocusable(boolean focusable) {
        boolean old;
        synchronized (this) {
            old = this.focusable;
            this.focusable = focusable;
        }
        isFocusTraversableOverridden = FOCUS_TRAVERSABLE_SET;
        firePropertyChange("focusable", old, focusable);
        if (old && !focusable) {
            if (isFocusOwner()) transferFocus(true);
            KeyboardFocusManager.clearMostRecentFocusOwner(this);
        }
    }
    /** @deprecated As of 1.4, replaced by {@link #isFocusable()}. */
    @Deprecated
    public boolean isFocusTraversable() {
        if (isFocusTraversableOverridden == FOCUS_TRAVERSABLE_UNKNOWN) isFocusTraversableOverridden = FOCUS_TRAVERSABLE_DEFAULT;
        return focusable;
    }
    public boolean isFocusable() { return isFocusTraversable(); }
    final boolean isFocusTraversableOverridden() { return isFocusTraversableOverridden != FOCUS_TRAVERSABLE_DEFAULT; }

    /**
     * Without peers this answers by class, as the desktop peers would: the heavyweight
     * java.awt components and their subclasses are not lightweight, everything else is.
     */
    public boolean isLightweight() {
        if (this instanceof Window || this instanceof Canvas || this instanceof Panel) return false;
        // By name: the remaining heavyweights are not all in the shadow.
        for (Class<?> type = getClass(); type != Component.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("java.awt.Button") || name.equals("java.awt.Label") || name.equals("java.awt.Checkbox")
                || name.equals("java.awt.Choice") || name.equals("java.awt.List") || name.equals("java.awt.Scrollbar")
                || name.equals("java.awt.ScrollPane") || name.equals("java.awt.TextComponent")) return false;
        }
        return true;
    }
    final boolean canBeFocusOwner() { return isEnabled() && isDisplayable() && isVisible() && isFocusable(); }

    /** Number of traversal key ids this component takes: a Component has no down-cycle keys. */
    int focusTraversalKeyLength() { return KeyboardFocusManager.TRAVERSAL_KEY_LENGTH - 1; }

    public void setFocusTraversalKeys(int id, java.util.Set<? extends AWTKeyStroke> keystrokes) {
        if (id < 0 || id >= focusTraversalKeyLength()) throw new IllegalArgumentException("invalid focus traversal key identifier");
        setFocusTraversalKeys_NoIDCheck(id, keystrokes);
    }
    public java.util.Set<AWTKeyStroke> getFocusTraversalKeys(int id) {
        if (id < 0 || id >= focusTraversalKeyLength()) throw new IllegalArgumentException("invalid focus traversal key identifier");
        return getFocusTraversalKeys_NoIDCheck(id);
    }
    public boolean areFocusTraversalKeysSet(int id) {
        if (id < 0 || id >= focusTraversalKeyLength()) throw new IllegalArgumentException("invalid focus traversal key identifier");
        return focusTraversalKeys[id] != null;
    }
    final void setFocusTraversalKeys_NoIDCheck(int id, java.util.Set<? extends AWTKeyStroke> keystrokes) {
        java.util.Set<AWTKeyStroke> oldKeys;
        synchronized (this) {
            if (keystrokes != null) {
                for (AWTKeyStroke keystroke : keystrokes) {
                    if (keystroke == null) throw new IllegalArgumentException("cannot set null focus traversal key");
                    if (keystroke.getKeyEventType() == KeyEvent.KEY_TYPED)
                        throw new IllegalArgumentException("focus traversal keys cannot map to KEY_TYPED events");
                    for (int i = 0; i < focusTraversalKeyLength(); i++) {
                        if (i == id) continue;
                        if (getFocusTraversalKeys_NoIDCheck(i).contains(keystroke))
                            throw new IllegalArgumentException("focus traversal keys must be unique for a Component");
                    }
                }
            }
            oldKeys = focusTraversalKeys[id];
            focusTraversalKeys[id] = keystrokes != null
                ? java.util.Collections.unmodifiableSet(new java.util.HashSet<AWTKeyStroke>(keystrokes)) : null;
        }
        firePropertyChange(FOCUS_TRAVERSAL_KEY_PROPERTY_NAMES[id], oldKeys, keystrokes);
    }
    final java.util.Set<AWTKeyStroke> getFocusTraversalKeys_NoIDCheck(int id) {
        java.util.Set<AWTKeyStroke> keystrokes = focusTraversalKeys[id];
        if (keystrokes != null) return keystrokes;
        Container parent = this.parent;
        return parent != null ? parent.getFocusTraversalKeys(id)
            : KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id);
    }

    public void transferFocus() { transferFocus(false); }
    boolean transferFocus(boolean clearOnFailure) {
        Component toFocus = getNextFocusCandidate();
        boolean res = false;
        if (toFocus != null && !toFocus.isFocusOwner() && toFocus != this) {
            res = toFocus.requestFocusInWindow(java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
        }
        if (clearOnFailure && !res) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        return res;
    }
    final Component getNextFocusCandidate() {
        Component[] comp = {this};
        Container rootAncestor = traversalRoot(comp);
        Component toFocus = null;
        if (rootAncestor != null) {
            FocusTraversalPolicy policy = rootAncestor.getFocusTraversalPolicy();
            toFocus = policy.getComponentAfter(rootAncestor, comp[0]);
            if (toFocus == null) toFocus = policy.getDefaultComponent(rootAncestor);
        }
        return toFocus;
    }
    public void transferFocusBackward() { transferFocusBackward(false); }
    boolean transferFocusBackward(boolean clearOnFailure) {
        Component[] comp = {this};
        Container rootAncestor = traversalRoot(comp);
        boolean res = false;
        if (rootAncestor != null) {
            FocusTraversalPolicy policy = rootAncestor.getFocusTraversalPolicy();
            Component toFocus = policy.getComponentBefore(rootAncestor, comp[0]);
            if (toFocus == null) toFocus = policy.getDefaultComponent(rootAncestor);
            if (toFocus != null) res = toFocus.requestFocusInWindow(java.awt.event.FocusEvent.Cause.TRAVERSAL_BACKWARD);
        }
        if (clearOnFailure && !res) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        return res;
    }
    /** The first showing, focusable traversal root above comp[0]; comp[0] becomes the component below it. */
    private Container traversalRoot(Component[] comp) {
        Container rootAncestor = getTraversalRoot();
        while (rootAncestor != null && !(rootAncestor.isShowing() && rootAncestor.canBeFocusOwner())) {
            comp[0] = rootAncestor;
            rootAncestor = rootAncestor.getFocusCycleRootAncestor();
        }
        return rootAncestor;
    }
    Container getTraversalRoot() { return getFocusCycleRootAncestor(); }
    /**
     * A tree the Android host mounts directly (the sidebar panels) stands in for the client
     * frame it sits in on the desktop, so its root is a focus cycle root like that Window.
     */
    final boolean isHostMountedRoot() { return parent == null && hostShowing; }
    public void transferFocusUpCycle() {
        Container rootAncestor = getFocusCycleRootAncestor();
        while (rootAncestor != null && !(rootAncestor.isShowing() && rootAncestor.isFocusable() && rootAncestor.isEnabled())) {
            rootAncestor = rootAncestor.getFocusCycleRootAncestor();
        }
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (rootAncestor != null) {
            Container rootAncestorRootAncestor = rootAncestor.getFocusCycleRootAncestor();
            manager.setGlobalCurrentFocusCycleRootPriv(rootAncestorRootAncestor != null ? rootAncestorRootAncestor : rootAncestor);
            rootAncestor.requestFocus(java.awt.event.FocusEvent.Cause.TRAVERSAL_UP);
        } else {
            Window window = KeyboardFocusManager.windowOf(this);
            if (window != null) {
                Component toFocus = window.getFocusTraversalPolicy().getDefaultComponent(window);
                if (toFocus != null) {
                    manager.setGlobalCurrentFocusCycleRootPriv(window);
                    toFocus.requestFocus(java.awt.event.FocusEvent.Cause.TRAVERSAL_UP);
                }
            }
        }
    }
    public Container getFocusCycleRootAncestor() {
        Container rootAncestor = this.parent;
        while (rootAncestor != null && !rootAncestor.isFocusCycleRoot()) rootAncestor = rootAncestor.parent;
        return rootAncestor;
    }
    public boolean isFocusCycleRoot(Container container) { return getFocusCycleRootAncestor() == container; }

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
    public void addInputMethodListener(InputMethodListener l)   { if (l != null) inputMethodListeners.add(l); }
    public void removeInputMethodListener(InputMethodListener l) { inputMethodListeners.remove(l); }
    public InputMethodListener[] getInputMethodListeners() { return inputMethodListeners.toArray(new InputMethodListener[0]); }

    /**
     * Gets the input method request handler which supports requests from input methods
     * for this component. A component that supports on-the-spot text input must override
     * this method to return an {@code InputMethodRequests} instance. By default this
     * method returns {@code null}.
     *
     * @since 1.2
     */
    public java.awt.im.InputMethodRequests getInputMethodRequests() { return null; }

    /**
     * Gets the input context used by this component for handling the communication with
     * input methods when text is entered in this component. By default, the input context
     * used for the parent component is returned; the root of the tree (a Window, or the root
     * of a host-mounted Android panel) owns one context shared by the tree.
     *
     * @since 1.2
     */
    public java.awt.im.InputContext getInputContext() {
        Container p = parent;
        if (p != null) return p.getInputContext();
        if (!(this instanceof Window) && !hostShowing) return null;
        synchronized (getTreeLock()) {
            if (rootInputContext == null) rootInputContext = java.awt.im.InputContext.getInstance();
            return rootInputContext;
        }
    }

    /**
     * Enables or disables input method support for this component. If input method support
     * is enabled and the component also processes key events, incoming events are offered
     * to the current input method. Disabling it ends any composition in progress.
     *
     * @since 1.2
     */
    public void enableInputMethods(boolean enable) {
        if (!enable && inputMethodsEnabled) {
            java.awt.im.InputContext context = getInputContext();
            if (context != null) {
                context.endComposition();
                context.removeNotify(this);
            }
        }
        inputMethodsEnabled = enable;
    }

    /**
     * Not in the JDK: whether this component is an active input method client, which the
     * desktop input context decides from {@link #enableInputMethods} and
     * {@link #getInputMethodRequests}. The Android keyboard bridge sends its composition to
     * such a component as {@link InputMethodEvent}s and types into any other one with keys.
     */
    public boolean isInputMethodClient() {
        return inputMethodsEnabled && getInputMethodRequests() != null;
    }

    private java.awt.dnd.DropTarget dropTarget;

    /**
     * Associate a {@code DropTarget} with this component, as the JDK does: the old
     * target is detached and the new one is made reachable by the in-process drag.
     */
    public synchronized void setDropTarget(java.awt.dnd.DropTarget dt) {
        if (dt == dropTarget || (dropTarget != null && dropTarget.equals(dt))) return;
        java.awt.dnd.DropTarget old;
        if ((old = dropTarget) != null) {
            dropTarget.removeNotify();
            java.awt.dnd.DropTarget t = dropTarget;
            dropTarget = null;
            try { t.setComponent(null); } catch (IllegalArgumentException iae) { /* the JDK ignores it too */ }
        }
        if ((dropTarget = dt) != null) {
            try {
                dropTarget.setComponent(this);
                dropTarget.addNotify();
            } catch (IllegalArgumentException iae) {
                if (old != null) {
                    try { old.setComponent(this); old.addNotify(); } catch (IllegalArgumentException iae1) { /* as the JDK */ }
                }
            }
        }
    }

    public synchronized java.awt.dnd.DropTarget getDropTarget() { return dropTarget; }

    private String name = "";
    private boolean nameExplicitlySet, nameConstructed;
    /**
     * The heavyweight widgets answer a generated name ({@code button0}, {@code label3}) until one is
     * set, as on the JDK; every other component keeps this shadow's historical empty default.
     */
    public String getName() {
        if (!nameExplicitlySet && !nameConstructed) {
            synchronized (this) {
                if (!nameExplicitlySet && !nameConstructed) {
                    String constructed = constructComponentName();
                    if (constructed != null) name = constructed;
                    nameConstructed = true;
                }
            }
        }
        return name;
    }
    public void setName(String name) { String old = getName(); this.name = name; nameExplicitlySet = true; firePropertyChange("name", old, name); }
    /** The generated name of an unnamed component, or null for none; the widgets override it. */
    String constructComponentName() { return null; }

    /** The state string the JDK's subclasses extend in their own paramString. */
    protected String paramString() {
        String thisName = getName() == null ? "" : getName();
        return thisName + ',' + x + ',' + y + ',' + width + 'x' + height
            + (isValid() ? "" : ",invalid") + (visible ? "" : ",hidden") + (enabled ? "" : ",disabled");
    }

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
        // Every host pointer event passes through here; it is where the pointer "is" for
        // MouseInfo and getMousePosition.
        MouseInfo.pointerAt(getRoot(), e.getXOnScreen(), e.getYOnScreen());
        // Through the JDK's per-kind hooks, so AWT widgets and plugin subclasses that override
        // processMouseEvent see host-injected input as they would on desktop.
        switch (e.getID()) {
            case MouseEvent.MOUSE_MOVED:
            case MouseEvent.MOUSE_DRAGGED:
                processMouseMotionEvent(e);
                break;
            case MouseEvent.MOUSE_WHEEL:
                if (e instanceof MouseWheelEvent) processMouseWheelEvent((MouseWheelEvent) e);
                break;
            default:
                processMouseEvent(e);
                break;
        }
    }

    /**
     * The JDK pipeline: the Toolkit's AWTEventListeners see the event first, a key event then
     * goes to the KeyboardFocusManager's dispatchers, and what is left reaches
     * {@link #processEvent} only when {@link #eventEnabled} says a listener or an
     * {@link #enableEvents} mask wants it, so subclasses overriding processMouseEvent and
     * friends see exactly what they would on the desktop.
     */
    public final void dispatchEvent(AWTEvent event) {
        if (event == null) throw new NullPointerException("event");
        AWTEvent previous = EventQueue.beginDispatch(event);
        try { dispatchEventImpl(event); } finally { EventQueue.endDispatch(previous); }
    }

    void dispatchEventImpl(AWTEvent event) {
        if (event instanceof KeyEvent) {
            // As Component.dispatchEventImpl: a KeyEvent first goes to the focus manager (dispatcher
            // chain, then redispatchEvent back here); on the way back traversal keys are handled
            // before the listeners see it. The AWTEventListeners see it on that second pass, once.
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            if (!KeyboardFocusManager.isRedispatching(event)) {
                if (manager.dispatchEvent(event)) return;
            } else {
                Toolkit.getDefaultToolkit().notifyAWTEventListeners(event);
                manager.processKeyEvent(this, (KeyEvent) event);
                if (((KeyEvent) event).isConsumed()) return;
                if (eventEnabled(event)) processEvent(event);
                return;
            }
        }
        Toolkit.getDefaultToolkit().notifyAWTEventListeners(event);
        if (eventEnabled(event)) processEvent(event);
    }

    /** Event types {@link #enableEvents} turned on, independent of registered listeners. */
    long eventMask;

    protected final void enableEvents(long eventsToEnable) {
        synchronized (this) { eventMask |= eventsToEnable; }
    }

    protected final void disableEvents(long eventsToDisable) {
        synchronized (this) { eventMask &= ~eventsToDisable; }
    }

    /**
     * Whether processEvent should see the event, as in the JDK. Ids outside the component,
     * focus, key, mouse, input method and hierarchy ranges (an ActionEvent sent to a
     * Button, an id above {@link AWTEvent#RESERVED_ID_MAX}) are passed on: the shadow's
     * Button, TextField and friends do not override this the way the JDK's do.
     */
    boolean eventEnabled(AWTEvent event) {
        int id = event.getID();
        switch (id) {
            case java.awt.event.ComponentEvent.COMPONENT_MOVED:
            case java.awt.event.ComponentEvent.COMPONENT_RESIZED:
            case java.awt.event.ComponentEvent.COMPONENT_SHOWN:
            case java.awt.event.ComponentEvent.COMPONENT_HIDDEN:
                return (eventMask & AWTEvent.COMPONENT_EVENT_MASK) != 0 || !componentListeners.isEmpty();
            case java.awt.event.FocusEvent.FOCUS_GAINED:
            case java.awt.event.FocusEvent.FOCUS_LOST:
                return (eventMask & AWTEvent.FOCUS_EVENT_MASK) != 0 || !focusListeners.isEmpty();
            case KeyEvent.KEY_PRESSED:
            case KeyEvent.KEY_RELEASED:
            case KeyEvent.KEY_TYPED:
                return (eventMask & AWTEvent.KEY_EVENT_MASK) != 0 || !keyListeners.isEmpty();
            case MouseEvent.MOUSE_PRESSED:
            case MouseEvent.MOUSE_RELEASED:
            case MouseEvent.MOUSE_ENTERED:
            case MouseEvent.MOUSE_EXITED:
            case MouseEvent.MOUSE_CLICKED:
                return (eventMask & AWTEvent.MOUSE_EVENT_MASK) != 0 || !mouseListeners.isEmpty();
            case MouseEvent.MOUSE_MOVED:
            case MouseEvent.MOUSE_DRAGGED:
                return (eventMask & AWTEvent.MOUSE_MOTION_EVENT_MASK) != 0 || !mouseMotionListeners.isEmpty();
            case MouseEvent.MOUSE_WHEEL:
                return (eventMask & AWTEvent.MOUSE_WHEEL_EVENT_MASK) != 0 || !mouseWheelListeners.isEmpty();
            case java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
            case java.awt.event.InputMethodEvent.CARET_POSITION_CHANGED:
                return (eventMask & AWTEvent.INPUT_METHOD_EVENT_MASK) != 0 || !inputMethodListeners.isEmpty();
            case java.awt.event.HierarchyEvent.HIERARCHY_CHANGED:
                return (eventMask & AWTEvent.HIERARCHY_EVENT_MASK) != 0 || !hierarchyListeners.isEmpty();
            case java.awt.event.HierarchyEvent.ANCESTOR_MOVED:
            case java.awt.event.HierarchyEvent.ANCESTOR_RESIZED:
                return (eventMask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0 || !hierarchyBoundsListeners.isEmpty();
            default:
                return true;
        }
    }

    protected void processEvent(AWTEvent event) {
        if (event instanceof java.awt.event.FocusEvent) {
            processFocusEvent((java.awt.event.FocusEvent) event);
        } else if (event instanceof MouseEvent) {
            switch (event.getID()) {
                case MouseEvent.MOUSE_PRESSED:
                case MouseEvent.MOUSE_RELEASED:
                case MouseEvent.MOUSE_CLICKED:
                case MouseEvent.MOUSE_ENTERED:
                case MouseEvent.MOUSE_EXITED:
                    processMouseEvent((MouseEvent) event);
                    break;
                case MouseEvent.MOUSE_MOVED:
                case MouseEvent.MOUSE_DRAGGED:
                    processMouseMotionEvent((MouseEvent) event);
                    break;
                case MouseEvent.MOUSE_WHEEL:
                    if (event instanceof MouseWheelEvent) processMouseWheelEvent((MouseWheelEvent) event);
                    break;
            }
        } else if (event instanceof KeyEvent) {
            processKeyEvent((KeyEvent) event);
        } else if (event instanceof java.awt.event.ComponentEvent
                && !(event instanceof java.awt.event.ContainerEvent)
                && !(event instanceof java.awt.event.WindowEvent)
                && !(event instanceof java.awt.event.InputEvent)
                && !(event instanceof java.awt.event.PaintEvent)) {
            processComponentEvent((java.awt.event.ComponentEvent) event);
        } else if (event instanceof java.awt.event.InputMethodEvent) {
            processInputMethodEvent((java.awt.event.InputMethodEvent) event);
        } else if (event instanceof java.awt.event.HierarchyEvent) {
            switch (event.getID()) {
                case java.awt.event.HierarchyEvent.HIERARCHY_CHANGED:
                    processHierarchyEvent((java.awt.event.HierarchyEvent) event);
                    break;
                case java.awt.event.HierarchyEvent.ANCESTOR_MOVED:
                case java.awt.event.HierarchyEvent.ANCESTOR_RESIZED:
                    processHierarchyBoundsEvent((java.awt.event.HierarchyEvent) event);
                    break;
            }
        }
    }

    protected void processMouseEvent(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED: for (MouseListener l : mouseListeners) l.mousePressed(e); break;
            case MouseEvent.MOUSE_RELEASED: for (MouseListener l : mouseListeners) l.mouseReleased(e); break;
            case MouseEvent.MOUSE_CLICKED: for (MouseListener l : mouseListeners) l.mouseClicked(e); break;
            case MouseEvent.MOUSE_ENTERED: for (MouseListener l : mouseListeners) l.mouseEntered(e); break;
            case MouseEvent.MOUSE_EXITED: for (MouseListener l : mouseListeners) l.mouseExited(e); break;
        }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_MOVED: for (MouseMotionListener l : mouseMotionListeners) l.mouseMoved(e); break;
            case MouseEvent.MOUSE_DRAGGED: for (MouseMotionListener l : mouseMotionListeners) l.mouseDragged(e); break;
        }
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
        for (MouseWheelListener l : mouseWheelListeners) l.mouseWheelMoved(e);
    }

    protected void processHierarchyBoundsEvent(java.awt.event.HierarchyEvent e) {
        for (java.awt.event.HierarchyBoundsListener l : hierarchyBoundsListeners) {
            switch (e.getID()) {
                case java.awt.event.HierarchyEvent.ANCESTOR_MOVED: l.ancestorMoved(e); break;
                case java.awt.event.HierarchyEvent.ANCESTOR_RESIZED: l.ancestorResized(e); break;
            }
        }
    }

    private final List<java.awt.event.HierarchyBoundsListener> hierarchyBoundsListeners = new CopyOnWriteArrayList<>();
    /** Components with a HierarchyBoundsListener, so a move or resize skips the subtree walk when none exist. */
    private static final java.util.concurrent.atomic.AtomicInteger boundsListenerCount = new java.util.concurrent.atomic.AtomicInteger();

    public void addHierarchyBoundsListener(java.awt.event.HierarchyBoundsListener l) {
        if (l != null) { hierarchyBoundsListeners.add(l); boundsListenerCount.incrementAndGet(); }
    }
    public void removeHierarchyBoundsListener(java.awt.event.HierarchyBoundsListener l) {
        if (hierarchyBoundsListeners.remove(l)) boundsListenerCount.decrementAndGet();
    }
    public java.awt.event.HierarchyBoundsListener[] getHierarchyBoundsListeners() { return hierarchyBoundsListeners.toArray(new java.awt.event.HierarchyBoundsListener[0]); }

    /** ANCESTOR_MOVED/ANCESTOR_RESIZED to every descendant that wants it, after this one moved or resized. */
    private void fireAncestorBounds(int id) {
        if (!(this instanceof Container)) return;
        if (boundsListenerCount.get() == 0 && !Toolkit.enabledOnToolkit(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK)) return;
        for (Component child : ((Container) this).getComponents()) child.ancestorBoundsChanged(this, id);
    }
    private void ancestorBoundsChanged(Component changed, int id) {
        dispatchEvent(new java.awt.event.HierarchyEvent(this, id, changed, changed.getParent()));
        if (this instanceof Container) {
            for (Component child : ((Container) this).getComponents()) child.ancestorBoundsChanged(changed, id);
        }
    }

    protected void processFocusEvent(java.awt.event.FocusEvent event) {
        for (FocusListener listener : focusListeners) {
            if (event.getID() == java.awt.event.FocusEvent.FOCUS_GAINED) listener.focusGained(event);
            else if (event.getID() == java.awt.event.FocusEvent.FOCUS_LOST) listener.focusLost(event);
        }
    }
    /**
     * Processes input method events occurring on this component by dispatching them to
     * any registered {@code InputMethodListener} objects.
     *
     * @since 1.2
     */
    protected void processInputMethodEvent(InputMethodEvent event) {
        for (InputMethodListener listener : inputMethodListeners) {
            if (event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) listener.inputMethodTextChanged(event);
            else if (event.getID() == InputMethodEvent.CARET_POSITION_CHANGED) listener.caretPositionChanged(event);
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

    /**
     * Dispatches a {@link KeyEvent} to the registered {@link KeyListener}s, through
     * {@link #processKeyEvent} so the AWT widgets and plugin subclasses that override it see
     * host-injected keys as they would on desktop.
     */
    public void dispatchKeyEvent(KeyEvent e) {
        if (e != null) processKeyEvent(e);
    }

    protected void processKeyEvent(KeyEvent e) {
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
