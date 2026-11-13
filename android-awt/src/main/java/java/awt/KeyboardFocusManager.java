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

import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The KeyboardFocusManager is responsible for managing the active and focused
 * Windows, and the current focus owner, and is the central dispatcher for
 * KeyEvents: KeyEventDispatchers first, then the target component (with focus
 * traversal keys), then KeyEventPostProcessors.
 *
 * <p>Android has no native focus subsystem behind AWT, so focus changes here
 * are synchronous: a successful request fires FOCUS_LOST, the window events
 * and FOCUS_GAINED before it returns, in the order the desktop peers deliver
 * them. Desktop RuneLite runs in a single AppContext, so the per-thread-group
 * manager of the JDK collapses to one process-wide manager.
 *
 * @see DefaultKeyboardFocusManager
 * @since 1.4
 */
public abstract class KeyboardFocusManager implements KeyEventDispatcher, KeyEventPostProcessor {

    public static final int FORWARD_TRAVERSAL_KEYS = 0;
    public static final int BACKWARD_TRAVERSAL_KEYS = 1;
    public static final int UP_CYCLE_TRAVERSAL_KEYS = 2;
    public static final int DOWN_CYCLE_TRAVERSAL_KEYS = 3;
    static final int TRAVERSAL_KEY_LENGTH = DOWN_CYCLE_TRAVERSAL_KEYS + 1;

    private static final String[] DEFAULT_FOCUS_TRAVERSAL_KEY_PROPERTY_NAMES = {
        "forwardDefaultFocusTraversalKeys",
        "backwardDefaultFocusTraversalKeys",
        "upCycleDefaultFocusTraversalKeys",
        "downCycleDefaultFocusTraversalKeys"
    };

    private static KeyboardFocusManager current;

    /** KeyEvents the manager is handing to their target; see {@link Component#dispatchEvent}. */
    private static final Set<AWTEvent> REDISPATCHING =
        Collections.newSetFromMap(new IdentityHashMap<AWTEvent, Boolean>());

    /**
     * Returns the current KeyboardFocusManager instance. Desktop RuneLite has
     * one AppContext, so this is a single process-wide instance rather than
     * one per thread group.
     */
    public static synchronized KeyboardFocusManager getCurrentKeyboardFocusManager() {
        if (current == null) current = new DefaultKeyboardFocusManager();
        return current;
    }

    /**
     * Sets the current KeyboardFocusManager; {@code null} installs a new
     * DefaultKeyboardFocusManager. Fires "managingFocus" on both managers.
     */
    public static void setCurrentKeyboardFocusManager(KeyboardFocusManager newManager) throws SecurityException {
        KeyboardFocusManager oldManager;
        synchronized (KeyboardFocusManager.class) {
            oldManager = current;
            if (newManager == null) newManager = new DefaultKeyboardFocusManager();
            current = newManager;
            if (oldManager != null && oldManager != newManager) {
                // The focus state is global on the desktop; carry it over.
                newManager.focusOwner = oldManager.focusOwner;
                newManager.permanentFocusOwner = oldManager.permanentFocusOwner;
                newManager.focusedWindow = oldManager.focusedWindow;
                newManager.activeWindow = oldManager.activeWindow;
                newManager.currentFocusCycleRoot = oldManager.currentFocusCycleRoot;
            }
        }
        if (oldManager != null && oldManager != newManager) {
            oldManager.firePropertyChange("managingFocus", Boolean.TRUE, Boolean.FALSE);
        }
        if (oldManager != newManager) {
            newManager.firePropertyChange("managingFocus", Boolean.FALSE, Boolean.TRUE);
        }
    }

    private volatile Component focusOwner;
    private volatile Component permanentFocusOwner;
    private volatile Window focusedWindow;
    private volatile Window activeWindow;
    private volatile Container currentFocusCycleRoot;
    private FocusTraversalPolicy defaultPolicy = new DefaultFocusTraversalPolicy();
    @SuppressWarnings("unchecked")
    private final Set<AWTKeyStroke>[] defaultFocusTraversalKeys = new Set[TRAVERSAL_KEY_LENGTH];
    private PropertyChangeSupport changeSupport;
    private VetoableChangeSupport vetoableSupport;
    private List<KeyEventDispatcher> keyEventDispatchers;
    private List<KeyEventPostProcessor> keyEventPostProcessors;

    /** Last focus owner of each Window, for {@link Window#getMostRecentFocusOwner}. */
    private static final WeakHashMap<Window, java.lang.ref.WeakReference<Component>> MOST_RECENT =
        new WeakHashMap<Window, java.lang.ref.WeakReference<Component>>();

    /** Initializes a KeyboardFocusManager with the desktop default traversal keys. */
    public KeyboardFocusManager() {
        AWTKeyStroke[][] defaults = {
            {
                AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0, false),
                AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK, false),
            },
            {
                AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK | InputEvent.SHIFT_MASK, false),
                AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB,
                    InputEvent.SHIFT_DOWN_MASK | InputEvent.SHIFT_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK, false),
            },
            {},
            {},
        };
        for (int i = 0; i < TRAVERSAL_KEY_LENGTH; i++) {
            Set<AWTKeyStroke> work = new HashSet<AWTKeyStroke>();
            Collections.addAll(work, defaults[i]);
            defaultFocusTraversalKeys[i] = work.isEmpty()
                ? Collections.<AWTKeyStroke>emptySet() : Collections.unmodifiableSet(work);
        }
    }

    // --- focus state -------------------------------------------------------

    /** Returns the focus owner, or null. */
    public Component getFocusOwner() { return focusOwner; }

    /** Returns the focus owner; there is one AppContext, so this equals {@link #getFocusOwner}. */
    protected Component getGlobalFocusOwner() throws SecurityException { return focusOwner; }

    /**
     * Sets the focus owner without moving native focus. A vetoable change
     * "focusOwner" is fired first; a veto leaves the owner unchanged.
     */
    protected void setGlobalFocusOwner(Component focusOwner) throws SecurityException {
        Component oldFocusOwner;
        if (focusOwner != null && !focusOwner.isFocusable()) return;
        synchronized (this) {
            oldFocusOwner = this.focusOwner;
            try {
                fireVetoableChange("focusOwner", oldFocusOwner, focusOwner);
            } catch (PropertyVetoException e) {
                return;
            }
            this.focusOwner = focusOwner;
            Container root = currentFocusCycleRoot;
            if (focusOwner != null && (root == null || !focusOwner.isFocusCycleRoot(root))) {
                Container rootAncestor = focusOwner.getFocusCycleRootAncestor();
                if (rootAncestor == null && focusOwner instanceof Window) rootAncestor = (Container) focusOwner;
                if (rootAncestor != null) setGlobalCurrentFocusCycleRootPriv(rootAncestor);
            }
        }
        firePropertyChange("focusOwner", oldFocusOwner, focusOwner);
    }

    /**
     * Clears the focus owner if it is in this manager's context; the
     * owner receives a permanent FOCUS_LOST with cause CLEAR_GLOBAL_FOCUS_OWNER.
     *
     * @since 1.8
     */
    public void clearFocusOwner() {
        if (getFocusOwner() != null) clearGlobalFocusOwner();
    }

    /**
     * Clears the global focus owner. The focused Window stays focused, as on
     * the desktop.
     */
    public void clearGlobalFocusOwner() throws SecurityException {
        changeFocus(null, FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER, false);
    }

    /** Returns the permanent focus owner, or null. */
    public Component getPermanentFocusOwner() { return permanentFocusOwner; }

    protected Component getGlobalPermanentFocusOwner() throws SecurityException { return permanentFocusOwner; }

    protected void setGlobalPermanentFocusOwner(Component permanentFocusOwner) throws SecurityException {
        Component oldPermanentFocusOwner;
        if (permanentFocusOwner != null && !permanentFocusOwner.isFocusable()) return;
        synchronized (this) {
            oldPermanentFocusOwner = this.permanentFocusOwner;
            try {
                fireVetoableChange("permanentFocusOwner", oldPermanentFocusOwner, permanentFocusOwner);
            } catch (PropertyVetoException e) {
                return;
            }
            this.permanentFocusOwner = permanentFocusOwner;
            if (permanentFocusOwner != null) setMostRecentFocusOwner(permanentFocusOwner);
        }
        firePropertyChange("permanentFocusOwner", oldPermanentFocusOwner, permanentFocusOwner);
    }

    /** Returns the focused Window, or null. */
    public Window getFocusedWindow() { return focusedWindow; }

    protected Window getGlobalFocusedWindow() throws SecurityException { return focusedWindow; }

    protected void setGlobalFocusedWindow(Window focusedWindow) throws SecurityException {
        Window oldFocusedWindow;
        if (focusedWindow != null && !focusedWindow.isFocusableWindow()) return;
        synchronized (this) {
            oldFocusedWindow = this.focusedWindow;
            try {
                fireVetoableChange("focusedWindow", oldFocusedWindow, focusedWindow);
            } catch (PropertyVetoException e) {
                return;
            }
            this.focusedWindow = focusedWindow;
        }
        firePropertyChange("focusedWindow", oldFocusedWindow, focusedWindow);
    }

    /** Returns the active Window (the Frame or Dialog owning the focused Window), or null. */
    public Window getActiveWindow() { return activeWindow; }

    protected Window getGlobalActiveWindow() throws SecurityException { return activeWindow; }

    protected void setGlobalActiveWindow(Window activeWindow) throws SecurityException {
        Window oldActiveWindow;
        synchronized (this) {
            oldActiveWindow = this.activeWindow;
            try {
                fireVetoableChange("activeWindow", oldActiveWindow, activeWindow);
            } catch (PropertyVetoException e) {
                return;
            }
            this.activeWindow = activeWindow;
        }
        firePropertyChange("activeWindow", oldActiveWindow, activeWindow);
    }

    /** Returns the current focus cycle root, or null. */
    public Container getCurrentFocusCycleRoot() { return currentFocusCycleRoot; }

    protected Container getGlobalCurrentFocusCycleRoot() throws SecurityException { return currentFocusCycleRoot; }

    /** Sets the current focus cycle root; fires "currentFocusCycleRoot" (not vetoable). */
    public void setGlobalCurrentFocusCycleRoot(Container newFocusCycleRoot) throws SecurityException {
        setGlobalCurrentFocusCycleRootPriv(newFocusCycleRoot);
    }

    void setGlobalCurrentFocusCycleRootPriv(Container newFocusCycleRoot) {
        Container oldFocusCycleRoot;
        synchronized (this) {
            oldFocusCycleRoot = currentFocusCycleRoot;
            currentFocusCycleRoot = newFocusCycleRoot;
        }
        firePropertyChange("currentFocusCycleRoot", oldFocusCycleRoot, newFocusCycleRoot);
    }

    // --- policy and traversal keys -----------------------------------------

    /** Returns the policy top-level components use when they set none. */
    public synchronized FocusTraversalPolicy getDefaultFocusTraversalPolicy() { return defaultPolicy; }

    /**
     * Sets the default FocusTraversalPolicy; fires "defaultFocusTraversalPolicy".
     *
     * @throws IllegalArgumentException if defaultPolicy is null
     */
    public void setDefaultFocusTraversalPolicy(FocusTraversalPolicy defaultPolicy) {
        if (defaultPolicy == null) throw new IllegalArgumentException("default focus traversal policy cannot be null");
        FocusTraversalPolicy oldPolicy;
        synchronized (this) {
            oldPolicy = this.defaultPolicy;
            this.defaultPolicy = defaultPolicy;
        }
        firePropertyChange("defaultFocusTraversalPolicy", oldPolicy, defaultPolicy);
    }

    /**
     * Sets the default traversal keys for one traversal operation.
     *
     * @throws IllegalArgumentException if id is not a traversal key id, keystrokes is null,
     *         contains null or a KEY_TYPED stroke, or a stroke already bound to another operation
     */
    public void setDefaultFocusTraversalKeys(int id, Set<? extends AWTKeyStroke> keystrokes) {
        if (id < 0 || id >= TRAVERSAL_KEY_LENGTH) throw new IllegalArgumentException("invalid focus traversal key identifier");
        if (keystrokes == null) throw new IllegalArgumentException("cannot set null Set of default focus traversal keys");
        Set<AWTKeyStroke> oldKeys;
        synchronized (this) {
            for (AWTKeyStroke keystroke : keystrokes) {
                if (keystroke == null) throw new IllegalArgumentException("cannot set null focus traversal key");
                if (keystroke.getKeyEventType() == KeyEvent.KEY_TYPED)
                    throw new IllegalArgumentException("focus traversal keys cannot map to KEY_TYPED events");
                for (int i = 0; i < TRAVERSAL_KEY_LENGTH; i++) {
                    if (i != id && defaultFocusTraversalKeys[i].contains(keystroke))
                        throw new IllegalArgumentException("focus traversal keys must be unique for a Component");
                }
            }
            oldKeys = defaultFocusTraversalKeys[id];
            defaultFocusTraversalKeys[id] = Collections.unmodifiableSet(new HashSet<AWTKeyStroke>(keystrokes));
        }
        firePropertyChange(DEFAULT_FOCUS_TRAVERSAL_KEY_PROPERTY_NAMES[id], oldKeys, keystrokes);
    }

    /**
     * Returns the default traversal keys for one traversal operation, as an unmodifiable Set.
     *
     * @throws IllegalArgumentException if id is not a traversal key id
     */
    public Set<AWTKeyStroke> getDefaultFocusTraversalKeys(int id) {
        if (id < 0 || id >= TRAVERSAL_KEY_LENGTH) throw new IllegalArgumentException("invalid focus traversal key identifier");
        return defaultFocusTraversalKeys[id];
    }

    // --- property and vetoable change support ------------------------------

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null) properties().addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null && changeSupport != null) changeSupport.removePropertyChangeListener(listener);
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        return changeSupport == null ? new PropertyChangeListener[0] : changeSupport.getPropertyChangeListeners();
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (listener != null) properties().addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (listener != null && changeSupport != null) changeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return changeSupport == null ? new PropertyChangeListener[0] : changeSupport.getPropertyChangeListeners(propertyName);
    }

    private synchronized PropertyChangeSupport properties() {
        if (changeSupport == null) changeSupport = new PropertyChangeSupport(this);
        return changeSupport;
    }

    /** Fires a PropertyChangeEvent unless the values are equal and non-null. */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (oldValue == newValue) return;
        PropertyChangeSupport support = changeSupport;
        if (support != null) support.firePropertyChange(propertyName, oldValue, newValue);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        if (listener != null) vetoables().addVetoableChangeListener(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        if (listener != null && vetoableSupport != null) vetoableSupport.removeVetoableChangeListener(listener);
    }

    public synchronized VetoableChangeListener[] getVetoableChangeListeners() {
        return vetoableSupport == null ? new VetoableChangeListener[0] : vetoableSupport.getVetoableChangeListeners();
    }

    public void addVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener != null) vetoables().addVetoableChangeListener(propertyName, listener);
    }

    public void removeVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener != null && vetoableSupport != null) vetoableSupport.removeVetoableChangeListener(propertyName, listener);
    }

    public synchronized VetoableChangeListener[] getVetoableChangeListeners(String propertyName) {
        return vetoableSupport == null ? new VetoableChangeListener[0] : vetoableSupport.getVetoableChangeListeners(propertyName);
    }

    private synchronized VetoableChangeSupport vetoables() {
        if (vetoableSupport == null) vetoableSupport = new VetoableChangeSupport(this);
        return vetoableSupport;
    }

    /**
     * Fires a vetoable change; when a listener vetoes, the listeners already
     * notified are told the value reverted and the exception propagates.
     */
    protected void fireVetoableChange(String propertyName, Object oldValue, Object newValue) throws PropertyVetoException {
        if (oldValue == newValue) return;
        VetoableChangeSupport support = vetoableSupport;
        if (support != null) support.fireVetoableChange(propertyName, oldValue, newValue);
    }

    // --- dispatcher and post-processor chains ------------------------------

    /**
     * Adds a KeyEventDispatcher. Dispatchers run in the order added; one
     * added twice runs twice, as on the desktop.
     */
    public void addKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        if (dispatcher != null) {
            synchronized (this) {
                if (keyEventDispatchers == null) keyEventDispatchers = new LinkedList<KeyEventDispatcher>();
                keyEventDispatchers.add(dispatcher);
            }
        }
    }

    /** Removes one registration of the dispatcher. */
    public void removeKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        if (dispatcher != null) {
            synchronized (this) {
                if (keyEventDispatchers != null) keyEventDispatchers.remove(dispatcher);
            }
        }
    }

    /** Returns a copy of the dispatcher chain, or null when none was ever added. */
    @SuppressWarnings("unchecked")
    protected synchronized List<KeyEventDispatcher> getKeyEventDispatchers() {
        return keyEventDispatchers == null ? null : (List<KeyEventDispatcher>) ((LinkedList<KeyEventDispatcher>) keyEventDispatchers).clone();
    }

    /** Adds a KeyEventPostProcessor; post-processors run in the order added. */
    public void addKeyEventPostProcessor(KeyEventPostProcessor processor) {
        if (processor != null) {
            synchronized (this) {
                if (keyEventPostProcessors == null) keyEventPostProcessors = new LinkedList<KeyEventPostProcessor>();
                keyEventPostProcessors.add(processor);
            }
        }
    }

    /** Removes one registration of the post-processor. */
    public void removeKeyEventPostProcessor(KeyEventPostProcessor processor) {
        if (processor != null) {
            synchronized (this) {
                if (keyEventPostProcessors != null) keyEventPostProcessors.remove(processor);
            }
        }
    }

    /** Returns a copy of the post-processor chain, or null when none was ever added. */
    @SuppressWarnings("unchecked")
    protected synchronized List<KeyEventPostProcessor> getKeyEventPostProcessors() {
        return keyEventPostProcessors == null ? null : (List<KeyEventPostProcessor>) ((LinkedList<KeyEventPostProcessor>) keyEventPostProcessors).clone();
    }

    /**
     * Android host seam: runs only the KeyEventDispatcher chain, in order,
     * stopping at the first that returns true. The host delivers the event
     * to its own key target afterwards, so going through
     * {@link #dispatchEvent} would deliver it twice.
     */
    public boolean dispatchToKeyEventDispatchers(KeyEvent e) {
        List<KeyEventDispatcher> dispatchers = getKeyEventDispatchers();
        if (dispatchers != null) {
            for (KeyEventDispatcher dispatcher : dispatchers) {
                if (dispatcher.dispatchKeyEvent(e)) return true;
            }
        }
        return false;
    }

    /** Runs the post-processor chain; true when one of them stopped it. */
    boolean runKeyEventPostProcessors(KeyEvent e) {
        List<KeyEventPostProcessor> processors = getKeyEventPostProcessors();
        if (processors != null) {
            for (KeyEventPostProcessor processor : processors) {
                if (processor.postProcessKeyEvent(e)) return true;
            }
        }
        return false;
    }

    // --- dispatch ----------------------------------------------------------

    /** Called by the AWT event dispatcher to dispatch focus-related and key events. */
    public abstract boolean dispatchEvent(AWTEvent e);

    /**
     * Redispatches an event so the manager does not intercept it again:
     * {@code target.dispatchEvent(e)} goes straight to the component.
     */
    public final void redispatchEvent(Component target, AWTEvent e) {
        synchronized (REDISPATCHING) {
            REDISPATCHING.add(e);
        }
        try {
            target.dispatchEvent(e);
        } finally {
            synchronized (REDISPATCHING) {
                REDISPATCHING.remove(e);
            }
        }
    }

    static boolean isRedispatching(AWTEvent e) {
        synchronized (REDISPATCHING) {
            return REDISPATCHING.contains(e);
        }
    }

    public abstract boolean dispatchKeyEvent(KeyEvent e);

    public abstract boolean postProcessKeyEvent(KeyEvent e);

    public abstract void processKeyEvent(Component focusedComponent, KeyEvent e);

    protected abstract void enqueueKeyEvents(long after, Component untilFocused);

    protected abstract void dequeueKeyEvents(long after, Component untilFocused);

    protected abstract void discardKeyEvents(Component comp);

    public abstract void focusNextComponent(Component aComponent);

    public abstract void focusPreviousComponent(Component aComponent);

    public abstract void upFocusCycle(Component aComponent);

    public abstract void downFocusCycle(Container aContainer);

    /** Focuses the Component after the current focus owner. */
    public final void focusNextComponent() {
        Component focusOwner = getFocusOwner();
        if (focusOwner != null) focusNextComponent(focusOwner);
    }

    /** Focuses the Component before the current focus owner. */
    public final void focusPreviousComponent() {
        Component focusOwner = getFocusOwner();
        if (focusOwner != null) focusPreviousComponent(focusOwner);
    }

    /** Moves focus up one cycle from the current focus owner. */
    public final void upFocusCycle() {
        Component focusOwner = getFocusOwner();
        if (focusOwner != null) upFocusCycle(focusOwner);
    }

    /** Moves focus down one cycle when the focus owner is a focus cycle root. */
    public final void downFocusCycle() {
        Component focusOwner = getFocusOwner();
        if (focusOwner instanceof Container) downFocusCycle((Container) focusOwner);
    }

    // --- focus changes (shadow-only) ---------------------------------------

    /** Android host seam: requests focus for a component, cause UNKNOWN. */
    public boolean requestFocus(Component component) {
        return requestFocus(component, FocusEvent.Cause.UNKNOWN);
    }

    boolean requestFocus(Component component, FocusEvent.Cause cause) {
        return requestFocus(component, cause, false);
    }

    boolean requestFocus(Component component, FocusEvent.Cause cause, boolean temporary) {
        if (component == null || !component.isEnabled() || !component.isVisible() || !component.isFocusable()) return false;
        for (Component p = component.getParent(); p != null; p = p.getParent()) if (!p.isVisible()) return false;
        Window window = windowOf(component);
        if (window != null && !window.isFocusableWindow()) return false;
        if (component == focusOwner) return true;
        if (cause == null) cause = FocusEvent.Cause.UNKNOWN;
        // As Component.isRequestFocusAccepted: a window activation always lands, anything else
        // asks the controller (Swing's InputVerifier) whether the current owner lets go.
        if (!net.runelite.awt.impl.FocusVeto.accept(focusOwner, component, temporary, cause)) {
            return false;
        }
        return changeFocus(component, cause, temporary);
    }

    /** Android host seam: clears the focus owner, and the focused window, when they lie inside subtree. */
    public void clearFocusIn(Component subtree) {
        for (Component p = focusOwner; p != null; p = p.getParent()) {
            if (p == subtree) {
                changeFocus(null, FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER, false);
                break;
            }
        }
        Window window = focusedWindow;
        if (window != null && (window == subtree || isOwnedBy(window, subtree))) changeWindows(window, null, null, null);
    }

    private static boolean isOwnedBy(Window window, Component subtree) {
        for (Window w = window.getOwner(); w != null; w = w.getOwner()) if (w == subtree) return true;
        return false;
    }

    static Window windowOf(Component component) {
        while (component != null && !(component instanceof Window)) component = component.getParent();
        return (Window) component;
    }

    /** The Frame or Dialog a Window belongs to: the active window when it is focused. */
    private static Window activeOf(Window window) {
        Window w = window;
        while (w != null && !(w instanceof Frame || w instanceof Dialog)) w = w.getOwner();
        return w != null ? w : window;
    }

    static void setMostRecentFocusOwner(Component component) {
        Window window = windowOf(component);
        if (window == null) return;
        synchronized (MOST_RECENT) {
            MOST_RECENT.put(window, new java.lang.ref.WeakReference<Component>(component));
        }
    }

    static Component getMostRecentFocusOwner(Window window) {
        synchronized (MOST_RECENT) {
            java.lang.ref.WeakReference<Component> ref = MOST_RECENT.get(window);
            return ref != null ? ref.get() : null;
        }
    }

    static void clearMostRecentFocusOwner(Component component) {
        synchronized (MOST_RECENT) {
            java.util.Iterator<java.lang.ref.WeakReference<Component>> it = MOST_RECENT.values().iterator();
            while (it.hasNext()) {
                Component recent = it.next().get();
                for (Component p = recent; p != null; p = p.getParent()) {
                    if (p == component) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Moves the focus owner to next (null clears it), firing in the desktop
     * order: FOCUS_LOST on the old owner, WINDOW_LOST_FOCUS and
     * WINDOW_DEACTIVATED on the old windows, WINDOW_ACTIVATED and
     * WINDOW_GAINED_FOCUS on the new ones, FOCUS_GAINED on the new owner.
     * A component outside any Window (a host-mounted panel) keeps the
     * focused window, as the sidebar lives in the client frame on the desktop.
     */
    private boolean changeFocus(Component next, FocusEvent.Cause cause, boolean temporary) {
        Component old = focusOwner;
        if (old == next) return true;
        Window oldWindow = focusedWindow;
        Window nextWindow = next == null ? oldWindow : windowOf(next);
        if (nextWindow == null) nextWindow = oldWindow;
        boolean windowChange = oldWindow != nextWindow;
        boolean lostTemporarily = temporary || windowChange && next != null;

        if (old != null) {
            setGlobalFocusOwner(null);
            if (focusOwner != null) return false;
            if (!lostTemporarily) setGlobalPermanentFocusOwner(null);
            old.dispatchEvent(new FocusEvent(old, FocusEvent.FOCUS_LOST, lostTemporarily, next, cause));
        }
        if (windowChange) changeWindows(oldWindow, nextWindow, old, next);
        if (next == null) return true;
        setGlobalFocusOwner(next);
        if (focusOwner != next) return false;
        if (!temporary) setGlobalPermanentFocusOwner(next);
        next.dispatchEvent(new FocusEvent(next, FocusEvent.FOCUS_GAINED, temporary, old, cause));
        return true;
    }

    private void changeWindows(Window oldWindow, Window nextWindow, Component oldOwner, Component nextOwner) {
        Window oldActive = activeWindow;
        Window nextActive = nextWindow != null ? activeOf(nextWindow) : null;
        if (oldWindow != null && oldWindow == focusedWindow) {
            setGlobalFocusedWindow(null);
            oldWindow.dispatchEvent(new WindowEvent(oldWindow, WindowEvent.WINDOW_LOST_FOCUS, nextWindow));
        }
        if (oldActive != null && oldActive != nextActive) {
            setGlobalActiveWindow(null);
            oldActive.dispatchEvent(new WindowEvent(oldActive, WindowEvent.WINDOW_DEACTIVATED, nextActive));
        }
        if (nextActive != null && nextActive != oldActive) {
            setGlobalActiveWindow(nextActive);
            nextActive.dispatchEvent(new WindowEvent(nextActive, WindowEvent.WINDOW_ACTIVATED, oldActive));
        }
        if (nextWindow != null) {
            setGlobalFocusedWindow(nextWindow);
            nextWindow.dispatchEvent(new WindowEvent(nextWindow, WindowEvent.WINDOW_GAINED_FOCUS, oldWindow));
        }
    }
}
