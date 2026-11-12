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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import javax.swing.border.Border;
import javax.swing.event.AncestorListener;

public class JComponent extends Container {
    private static final long serialVersionUID = 8202245103466131933L;

    public static final int WHEN_FOCUSED = 0;
    public static final int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
    public static final int WHEN_IN_FOCUSED_WINDOW = 2;
    public static final int UNDEFINED_CONDITION = -1;

    public static final String TOOL_TIP_TEXT_KEY = "ToolTipText";

    protected javax.swing.plaf.ComponentUI ui;
    /** A list of event listeners for this component. */
    protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();

    /**
     * A JComponent is not {@link javax.accessibility.Accessible} itself (the JDK leaves that to
     * each widget), but RuneLite panels subclass it directly and the Android host asks every
     * component it rasterizes, so a plain subclass answers a generic Swing context here.
     */
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                private static final long serialVersionUID = 1L;
            };
        }
        return accessibleContext;
    }

    /**
     * Accessibility support shared by every Swing component: the name falls back to a
     * titled border, the description to the tool tip, and the children are the container's
     * accessible components.
     */
    public abstract class AccessibleJComponent extends AccessibleAWTContainer
            implements javax.accessibility.AccessibleExtendedComponent {
        private static final long serialVersionUID = 1L;

        protected AccessibleJComponent() {
            super();
        }

        /** @deprecated This field is unused. */
        @Deprecated
        protected java.awt.event.FocusListener accessibleFocusHandler = null;

        public String getAccessibleName() {
            String name = accessibleName;
            if (name == null) {
                name = (String) getClientProperty(javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
            }
            if (name == null) {
                name = getBorderTitle(getBorder());
            }
            if (name == null) {
                // JLabel.LABELED_BY_PROPERTY: set by JLabel.setLabelFor on the labelled component.
                Object o = getClientProperty("labeledBy");
                if (o instanceof javax.accessibility.Accessible) {
                    javax.accessibility.AccessibleContext ac = ((javax.accessibility.Accessible) o).getAccessibleContext();
                    if (ac != null) name = ac.getAccessibleName();
                }
            }
            return name;
        }

        /** The title of the (possibly compound) titled border, the JDK's name fallback. */
        protected String getBorderTitle(Border b) {
            if (b instanceof javax.swing.border.TitledBorder) {
                return ((javax.swing.border.TitledBorder) b).getTitle();
            } else if (b instanceof javax.swing.border.CompoundBorder) {
                String s = getBorderTitle(((javax.swing.border.CompoundBorder) b).getInsideBorder());
                if (s == null) s = getBorderTitle(((javax.swing.border.CompoundBorder) b).getOutsideBorder());
                return s;
            }
            return null;
        }

        public String getAccessibleDescription() {
            String description = accessibleDescription;
            if (description == null) {
                description = (String) getClientProperty(javax.accessibility.AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY);
            }
            if (description == null) {
                description = getToolTipText();
            }
            return description;
        }

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.SWING_COMPONENT;
        }

        // State set (OPAQUE included) and children come from AccessibleAWTContainer.

        // AccessibleExtendedComponent

        public String getToolTipText() {
            return JComponent.this.getToolTipText();
        }

        public String getTitledBorderText() {
            return getBorderTitle(getBorder());
        }

        public javax.accessibility.AccessibleKeyBinding getAccessibleKeyBinding() {
            return null;
        }
    }

    private Border border;
    private boolean doubleBuffered = true;
    private boolean opaque;
    private float alignmentX = 0.5f;
    private float alignmentY = 0.5f;

    // The JDK keeps it per AppContext; Android has one application context.
    private static volatile java.util.Locale defaultLocale;

    /**
     * Returns the default locale used to initialize each JComponent's locale
     * property upon creation; {@code Locale.getDefault()} until one is set.
     *
     * @since 1.4
     */
    public static java.util.Locale getDefaultLocale() {
        java.util.Locale l = defaultLocale;
        if (l == null) {
            l = java.util.Locale.getDefault();
            defaultLocale = l;
        }
        return l;
    }

    /**
     * Sets the default locale used to initialize each JComponent's locale
     * property upon creation; {@code null} resets it to the VM's default.
     *
     * @since 1.4
     */
    public static void setDefaultLocale(java.util.Locale l) {
        defaultLocale = l;
    }

    public JComponent() {
        // As the JDK's JComponent(): key events reach processKeyEvent overrides.
        enableEvents(java.awt.AWTEvent.KEY_EVENT_MASK);
        // As the JDK: an unparented Swing component answers a locale instead of throwing
        // IllegalComponentStateException from Component.getLocale.
        super.setLocale(JComponent.getDefaultLocale());
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & (java.awt.event.HierarchyEvent.PARENT_CHANGED | java.awt.event.HierarchyEvent.SHOWING_CHANGED)) == 0) return;
            boolean attached = getParent() != null && isVisible();
            int id = attached ? javax.swing.event.AncestorEvent.ANCESTOR_ADDED : javax.swing.event.AncestorEvent.ANCESTOR_REMOVED;
            javax.swing.event.AncestorEvent event = new javax.swing.event.AncestorEvent(this, id,
                e.getChanged() instanceof Container ? (Container)e.getChanged() : getParent(), e.getChangedParent());
            for (AncestorListener listener : listenerList.getListeners(AncestorListener.class)) {
                if (attached) listener.ancestorAdded(event); else listener.ancestorRemoved(event);
            }
        });
    }
    private boolean requestFocusEnabled = true;
    /**
     * Whether a mouse press should give this component the focus. As on the JDK it is a hint
     * for the UI delegates' mouse handlers; programmatic requestFocus ignores it.
     */
    public void setRequestFocusEnabled(boolean requestFocusEnabled) { this.requestFocusEnabled = requestFocusEnabled; }
    public boolean isRequestFocusEnabled() { return requestFocusEnabled; }
    public Border getBorder() { return border; }
    // Border drives getInsets(), which every layout manager reads — a border change is a
    // layout change.
    public void setBorder(Border border) { Border old = this.border; this.border = border; firePropertyChange("border", old, border); revalidate(); repaint(); }

    /**
     * Registers the text to display in a tool tip. As in the JDK the text is
     * kept as the {@link #TOOL_TIP_TEXT_KEY} client property, and a component
     * with text is registered with the {@link ToolTipManager}.
     */
    public void setToolTipText(String text) {
        String oldText = getToolTipText();
        putClientProperty(TOOL_TIP_TEXT_KEY, text);
        firePropertyChange(TOOL_TIP_TEXT_KEY, oldText, text);
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        if (text != null) {
            if (oldText == null) {
                toolTipManager.registerComponent(this);
            }
        } else {
            toolTipManager.unregisterComponent(this);
        }
    }
    public String getToolTipText() { return (String) getClientProperty(TOOL_TIP_TEXT_KEY); }
    /** The tip for a mouse position; components with per-region tips (tabs, cells) override it. */
    public String getToolTipText(java.awt.event.MouseEvent event) { return getToolTipText(); }
    /** Where the tip goes in this component's coordinates, or {@code null} for under the pointer. */
    public java.awt.Point getToolTipLocation(java.awt.event.MouseEvent event) { return null; }
    /** Where a component popup menu opens, or {@code null} for the look and feel's choice. */
    public java.awt.Point getPopupLocation(java.awt.event.MouseEvent event) { return null; }
    /** The tip ToolTipManager shows for this component; override to customize it. */
    public JToolTip createToolTip() {
        JToolTip tip = new JToolTip();
        tip.setComponent(this);
        return tip;
    }

    // Sizing is inherited from Component — JComponent used to keep parallel `preferred` /
    // `minimum` / `maximum` fields, but that made `isPreferredSizeSet()` (which reads
    // Component.preferredSize) return false even after JComponent.setPreferredSize. That
    // caused AbstractButton / JLabel to skip the explicit override and fall through to
    // their icon-based fallback — e.g. config + toggle in PluginListItem reported 50px
    // wide despite both being set to 25, eating into the plugin name label. Removing the
    // shadows fixes that for every JComponent subclass.

    /**
     * Swing's resolution when nothing was set explicitly: a container answers from its
     * layout manager (even 0x0, as the JDK does), and a leaf from its preferred size — the
     * ComponentUI default that the label/list/combo delegates inherit. Component's "current
     * size" fallback made BoxLayout's min-driven shrinking feed on its own output.
     */
    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        java.awt.LayoutManager lm = getLayout();
        if (lm != null) {
            Dimension d = lm.minimumLayoutSize(this);
            if (d != null) return d;
        }
        return getPreferredSize();
    }

    /** Container.getMaximumSize: a LayoutManager2 answers, otherwise Component's
     *  Short.MAX_VALUE square. Labels and buttons override this with their preferred size. */
    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) return super.getMaximumSize();
        java.awt.LayoutManager lm = getLayout();
        if (lm instanceof java.awt.LayoutManager2) {
            Dimension d = ((java.awt.LayoutManager2) lm).maximumLayoutSize(this);
            if (d != null) return d;
        }
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    public boolean isDoubleBuffered() { return doubleBuffered; }
    public void setDoubleBuffered(boolean d) { this.doubleBuffered = d; }
    public boolean isOpaque() { return opaque; }
    public void setOpaque(boolean o) { boolean old = opaque; this.opaque = o; firePropertyChange("opaque", old, o); repaint(); }

    public float getAlignmentX() { return alignmentX; }
    public void setAlignmentX(float f) { this.alignmentX = f; }
    public float getAlignmentY() { return alignmentY; }
    public void setAlignmentY(float f) { this.alignmentY = f; }

    private java.beans.VetoableChangeSupport vetoableChangeSupport;

    public synchronized void addVetoableChangeListener(java.beans.VetoableChangeListener listener) {
        if (vetoableChangeSupport == null) vetoableChangeSupport = new java.beans.VetoableChangeSupport(this);
        vetoableChangeSupport.addVetoableChangeListener(listener);
    }

    public synchronized void removeVetoableChangeListener(java.beans.VetoableChangeListener listener) {
        if (vetoableChangeSupport == null) return;
        vetoableChangeSupport.removeVetoableChangeListener(listener);
    }

    public synchronized java.beans.VetoableChangeListener[] getVetoableChangeListeners() {
        if (vetoableChangeSupport == null) return new java.beans.VetoableChangeListener[0];
        return vetoableChangeSupport.getVetoableChangeListeners();
    }

    protected void fireVetoableChange(String propertyName, Object oldValue, Object newValue)
            throws java.beans.PropertyVetoException {
        if (vetoableChangeSupport == null) return;
        vetoableChangeSupport.fireVetoableChange(propertyName, oldValue, newValue);
    }

    public void addAncestorListener(AncestorListener l) { listenerList.add(AncestorListener.class, l); }
    public void removeAncestorListener(AncestorListener l) { listenerList.remove(AncestorListener.class, l); }
    public AncestorListener[] getAncestorListeners() { return listenerList.getListeners(AncestorListener.class); }

    /**
     * The listeners of the given type: AncestorListener, VetoableChangeListener and
     * PropertyChangeListener from their own registries, anything a subclass added to
     * {@link #listenerList}, else Component's.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        T[] result;
        if (listenerType == AncestorListener.class) {
            result = (T[]) getAncestorListeners();
        } else if (listenerType == java.beans.VetoableChangeListener.class) {
            result = (T[]) getVetoableChangeListeners();
        } else if (listenerType == PropertyChangeListener.class) {
            result = (T[]) getPropertyChangeListeners();
        } else {
            result = listenerList.getListeners(listenerType);
        }
        if (result.length == 0) {
            return super.getListeners(listenerType);
        }
        return result;
    }

    /** Delegates to the UI so a round button's corners are not hits, as in the JDK. */
    @Override
    public boolean contains(int x, int y) {
        return (ui != null) ? ui.contains(this, x, y) : super.contains(x, y);
    }

    /** @deprecated As of JDK 5, replaced by {@code Component.setBounds(int, int, int, int)}. */
    @Deprecated
    @Override
    public void reshape(int x, int y, int w, int h) {
        super.reshape(x, y, w, h);
    }

    @Override
    public int getBaseline(int width, int height) {
        // check size.
        super.getBaseline(width, height);
        if (ui != null) {
            return ui.getBaseline(this, width, height);
        }
        return -1;
    }

    @Override
    public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
        if (ui != null) {
            return ui.getBaselineResizeBehavior(this);
        }
        return java.awt.Component.BaselineResizeBehavior.OTHER;
    }

    private boolean printing;

    /**
     * Prints this component: paints it with the print hooks (printComponent, printBorder,
     * printChildren) in place of the paint ones, firing "paintingForPrint" around it.
     */
    @Override
    public void print(java.awt.Graphics g) {
        printing = true;
        firePropertyChange("paintingForPrint", false, true);
        try {
            paint(g);
        } finally {
            printing = false;
            firePropertyChange("paintingForPrint", true, false);
        }
    }

    /** Unlike Component.printAll this prints whether or not the component is showing, as in the JDK. */
    @Override
    public void printAll(java.awt.Graphics g) {
        print(g);
    }

    /** Whether a print operation is in progress on this component. */
    public final boolean isPaintingForPrint() { return printing; }

    protected void printComponent(java.awt.Graphics g) { paintComponent(g); }
    protected void printChildren(java.awt.Graphics g) { paintChildren(g); }
    protected void printBorder(java.awt.Graphics g) { paintBorder(g); }

    // JDK 9+ (9): jdk17u src/java.desktop/share/classes/javax/swing/JComponent.java
    public javax.swing.plaf.ComponentUI getUI() { return ui; }
    /** Protected as on the JDK: callers go through each component's typed setUI(FooUI). */
    protected void setUI(javax.swing.plaf.ComponentUI ui) { javax.swing.plaf.ComponentUI old=this.ui;if(old!=null)old.uninstallUI(this);this.ui=ui;if(ui!=null)ui.installUI(this);firePropertyChange("UI",old,ui);revalidate();repaint(); }
    public String getUIClassID() { return "ComponentUI"; }

    @Override
    public int getBaseline(int width, int height) {
        // check width and height are valid
        super.getBaseline(width, height);
        if (ui != null) {
            return ui.getBaseline(this, width, height);
        }
        return -1;
    }

    @Override
    public BaselineResizeBehavior getBaselineResizeBehavior() {
        if (ui != null) {
            return ui.getBaselineResizeBehavior(this);
        }
        return BaselineResizeBehavior.OTHER;
    }

    public void updateUI() { javax.swing.plaf.ComponentUI delegate=UIManager.getUI(this);if(delegate!=null)setUI(delegate); }

    /**
     * Adds the specified region to the dirty region list if the component is showing. The
     * component will be repainted after all of the currently pending events have been
     * dispatched.
     *
     * @param tm  this parameter is not used
     * @param x  the x value of the dirty region
     * @param y  the y value of the dirty region
     * @param width  the width of the dirty region
     * @param height  the height of the dirty region
     * @see RepaintManager#addDirtyRegion
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        RepaintManager.currentManager(this).addDirtyRegion(this, x, y, width, height);
    }

    public void repaint(java.awt.Rectangle r) {
        repaint(0, r.x, r.y, r.width, r.height);
    }

    /** Swing's default: painting of a component does not have to start at it. */
    protected boolean isPaintingOrigin() {
        return false;
    }

    /**
     * Supports deferred automatic layout: invalidates this component and its ancestors, and
     * queues the nearest validate root with the RepaintManager. Unlike the JDK, the
     * invalidation happens on the calling thread too, because the Android hosts validate
     * their trees on their own frame and must see the change on the next one.
     *
     * @see RepaintManager#addInvalidComponent
     */
    @Override
    public void revalidate() {
        invalidate();
        RepaintManager.currentManager(this).addInvalidComponent(this);
    }

    /**
     * If this method returns true, revalidate calls by descendants of this component will
     * cause the entire tree beginning with this root to be validated. Returns false by
     * default; JScrollPane and JRootPane override it to return true.
     *
     * @return whether this component is a validate root
     */
    @Override
    public boolean isValidateRoot() {
        return false;
    }

    /**
     * Paints the specified region in this component and all of its descendants that overlap
     * the region, immediately. There is no screen to paint to: the region, with the dirty
     * region pending for this component, is folded into its root's region right away, which
     * the host that rasterizes the root takes with {@code RepaintManager.hostTakeDirtyRegion}
     * and paints on its next frame. Other components' pending regions are left queued.
     *
     * @param x  the x value of the region to be painted
     * @param y  the y value of the region to be painted
     * @param w  the width of the region to be painted
     * @param h  the height of the region to be painted
     */
    public void paintImmediately(int x, int y, int w, int h) {
        RepaintManager.currentManager(this).paintImmediately(this, x, y, w, h);
    }

    /**
     * Paints the specified region now.
     *
     * @param r a <code>Rectangle</code> containing the region to be painted
     */
    public void paintImmediately(java.awt.Rectangle r) {
        paintImmediately(r.x, r.y, r.width, r.height);
    }

    // --- key bindings (OpenJDK 8u JComponent) -------------------------------

    private ActionMap actionMap;
    private InputMap focusInputMap;
    private InputMap ancestorInputMap;
    private ComponentInputMap windowInputMap;
    /** The WHEN_IN_FOCUSED_WINDOW strokes handed to the KeyboardManager (the JDK keeps them in a client property). */
    private transient java.util.Hashtable<KeyStroke, KeyStroke> registeredWindowStrokes;

    /**
     * Sets the <code>InputMap</code> to use under the condition
     * <code>condition</code> to <code>map</code>. A <code>null</code> value implies you
     * do not want any bindings to be used, even from the UI.
     *
     * @throws IllegalArgumentException if <code>condition</code> is
     *          <code>WHEN_IN_FOCUSED_WINDOW</code> and <code>map</code>
     *          is not an instance of <code>ComponentInputMap</code>; or
     *          if <code>condition</code> is not one of the legal values
     */
    public final void setInputMap(int condition, InputMap map) {
        switch (condition) {
        case WHEN_IN_FOCUSED_WINDOW:
            if (map != null && !(map instanceof ComponentInputMap)) {
                throw new IllegalArgumentException("WHEN_IN_FOCUSED_WINDOW InputMaps must be of type ComponentInputMap");
            }
            windowInputMap = (ComponentInputMap) map;
            windowInputMapSet = true;
            registerWithKeyboardManager(false);
            break;
        case WHEN_ANCESTOR_OF_FOCUSED_COMPONENT:
            ancestorInputMap = map;
            ancestorInputMapSet = true;
            break;
        case WHEN_FOCUSED:
            focusInputMap = map;
            focusInputMapSet = true;
            break;
        default:
            throw new IllegalArgumentException("condition must be one of JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_FOCUSED or JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT");
        }
    }
    // An explicit setInputMap(condition, null) must stick, so "set" is tracked apart from null.
    private boolean focusInputMapSet, ancestorInputMapSet, windowInputMapSet, actionMapSet;

    /** Returns the <code>InputMap</code> that is used during <code>condition</code>, creating it. */
    public final InputMap getInputMap(int condition) {
        return getInputMap(condition, true);
    }

    /** Returns the <code>InputMap</code> that is used when the component has focus. */
    public final InputMap getInputMap() {
        return getInputMap(WHEN_FOCUSED, true);
    }

    /** Sets the <code>ActionMap</code> to <code>am</code>. This does not set the parent of the am. */
    public final void setActionMap(ActionMap am) {
        actionMap = am;
        actionMapSet = true;
    }

    /** Returns the <code>ActionMap</code> used to determine what <code>Action</code> to fire for a binding. */
    public final ActionMap getActionMap() {
        return getActionMap(true);
    }

    /** Returns the InputMap for <code>condition</code>, creating it when <code>create</code> and none was set. */
    final InputMap getInputMap(int condition, boolean create) {
        switch (condition) {
        case WHEN_FOCUSED:
            if (focusInputMapSet) return focusInputMap;
            if (create) {
                InputMap km = new InputMap();
                setInputMap(condition, km);
                return km;
            }
            break;
        case WHEN_ANCESTOR_OF_FOCUSED_COMPONENT:
            if (ancestorInputMapSet) return ancestorInputMap;
            if (create) {
                InputMap km = new InputMap();
                setInputMap(condition, km);
                return km;
            }
            break;
        case WHEN_IN_FOCUSED_WINDOW:
            if (windowInputMapSet) return windowInputMap;
            if (create) {
                ComponentInputMap km = new ComponentInputMap(this);
                setInputMap(condition, km);
                return km;
            }
            break;
        default:
            throw new IllegalArgumentException("condition must be one of JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_FOCUSED or JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT");
        }
        return null;
    }

    final ActionMap getActionMap(boolean create) {
        if (actionMapSet) return actionMap;
        if (create) {
            ActionMap am = new ActionMap();
            setActionMap(am);
            return am;
        }
        return null;
    }

    /**
     * This method is now obsolete, please use a combination of
     * <code>getActionMap()</code> and <code>getInputMap()</code> for
     * similar behavior.
     */
    public void registerKeyboardAction(ActionListener anAction, String aCommand, KeyStroke aKeyStroke, int aCondition) {
        InputMap inputMap = getInputMap(aCondition, true);
        if (inputMap != null) {
            ActionMap actionMap = getActionMap(true);
            ActionStandin action = new ActionStandin(anAction, aCommand);
            inputMap.put(aKeyStroke, action);
            if (actionMap != null) {
                actionMap.put(action, action);
            }
        }
    }

    /** This method is now obsolete, please use a combination of getActionMap() and getInputMap(). */
    public void registerKeyboardAction(ActionListener anAction, KeyStroke aKeyStroke, int aCondition) {
        registerKeyboardAction(anAction, null, aKeyStroke, aCondition);
    }

    /**
     * This method is now obsolete. To unregister an existing binding
     * you can either remove the binding from the <code>ActionMap/InputMap</code>,
     * or place a dummy binding the <code>InputMap</code>.
     */
    public void unregisterKeyboardAction(KeyStroke aKeyStroke) {
        ActionMap am = getActionMap(false);
        for (int counter = 0; counter < 3; counter++) {
            InputMap km = getInputMap(counter, false);
            if (km != null) {
                Object actionID = km.get(aKeyStroke);
                if (am != null && actionID != null) {
                    am.remove(actionID);
                }
                km.remove(aKeyStroke);
            }
        }
    }

    /** Returns the <code>KeyStrokes</code> that will initiate registered actions. */
    public KeyStroke[] getRegisteredKeyStrokes() {
        int[] counts = new int[3];
        KeyStroke[][] strokes = new KeyStroke[3][];
        for (int counter = 0; counter < 3; counter++) {
            InputMap km = getInputMap(counter, false);
            strokes[counter] = (km != null) ? km.allKeys() : null;
            counts[counter] = (strokes[counter] != null) ? strokes[counter].length : 0;
        }
        KeyStroke[] retValue = new KeyStroke[counts[0] + counts[1] + counts[2]];
        for (int counter = 0, last = 0; counter < 3; counter++) {
            if (counts[counter] > 0) {
                System.arraycopy(strokes[counter], 0, retValue, last, counts[counter]);
                last += counts[counter];
            }
        }
        return retValue;
    }

    /** Returns the condition that determines whether a registered action occurs in response to the keystroke. */
    public int getConditionForKeyStroke(KeyStroke aKeyStroke) {
        for (int counter = 0; counter < 3; counter++) {
            InputMap inputMap = getInputMap(counter, false);
            if (inputMap != null && inputMap.get(aKeyStroke) != null) {
                return counter;
            }
        }
        return UNDEFINED_CONDITION;
    }

    /** Returns the object that will perform the action registered for a given keystroke. */
    public ActionListener getActionForKeyStroke(KeyStroke aKeyStroke) {
        ActionMap am = getActionMap(false);
        if (am == null) {
            return null;
        }
        for (int counter = 0; counter < 3; counter++) {
            InputMap inputMap = getInputMap(counter, false);
            if (inputMap != null) {
                Object actionBinding = inputMap.get(aKeyStroke);
                if (actionBinding != null) {
                    Action action = am.get(actionBinding);
                    if (action instanceof ActionStandin) {
                        return ((ActionStandin) action).actionListener;
                    }
                    return action;
                }
            }
        }
        return null;
    }

    /**
     * Unregisters all the bindings in the first tier <code>InputMaps</code>
     * and <code>ActionMap</code>. This has the effect of removing any
     * local bindings, and allowing the bindings defined in parent
     * <code>InputMap/ActionMaps</code> (the UI is usually defined in the second tier) to persist.
     */
    public void resetKeyboardActions() {
        // Keys
        for (int counter = 0; counter < 3; counter++) {
            InputMap inputMap = getInputMap(counter, false);
            if (inputMap != null) {
                inputMap.clear();
            }
        }
        // Actions
        ActionMap am = getActionMap(false);
        if (am != null) {
            am.clear();
        }
    }

    /**
     * Invoked to process the key bindings for <code>ks</code> as the result
     * of the <code>KeyEvent</code> <code>e</code>. This obtains
     * the appropriate <code>InputMap</code>,
     * gets the binding, gets the action from the <code>ActionMap</code>,
     * and then (if the action is found and the component
     * is enabled) invokes <code>notifyAction</code> to notify the action.
     *
     * @return true if there was a binding to an action, and the action was enabled
     */
    protected boolean processKeyBinding(KeyStroke ks, java.awt.event.KeyEvent e, int condition, boolean pressed) {
        InputMap map = getInputMap(condition, false);
        ActionMap am = getActionMap(false);

        if (map != null && am != null && isEnabled()) {
            Object binding = map.get(ks);
            Action action = (binding == null) ? null : am.get(binding);
            if (action != null) {
                return SwingUtilities.notifyAction(action, ks, e, this, e.getModifiers());
            }
        }
        return false;
    }

    /**
     * This is invoked as the result of a <code>KeyEvent</code>
     * that was not consumed by the <code>FocusManager</code>,
     * <code>KeyListeners</code>, or the component. It will first try
     * <code>WHEN_FOCUSED</code> bindings,
     * then <code>WHEN_ANCESTOR_OF_FOCUSED_COMPONENT</code> bindings,
     * and finally <code>WHEN_IN_FOCUSED_WINDOW</code> bindings.
     *
     * @return true if there is a key binding for <code>e</code>
     */
    boolean processKeyBindings(java.awt.event.KeyEvent e, boolean pressed) {
        if (!SwingUtilities.isValidKeyEventForKeyBindings(e)) {
            return false;
        }
        // Get the KeyStroke
        // There may be two keystrokes associated with a low-level key event;
        // in this case a keystroke made of an extended key code has a priority.
        KeyStroke ks;
        KeyStroke ksE = null;

        if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) {
            ks = KeyStroke.getKeyStroke(e.getKeyChar());
        } else {
            ks = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers(), !pressed);
            if (e.getKeyCode() != e.getExtendedKeyCode()) {
                ksE = KeyStroke.getKeyStroke(e.getExtendedKeyCode(), e.getModifiers(), !pressed);
            }
        }

        // Do we have a key binding for e?
        // If we have a binding by an extended code, use it.
        // If not, check for regular code binding.
        if (ksE != null && processKeyBinding(ksE, e, WHEN_FOCUSED, pressed)) {
            return true;
        }
        if (processKeyBinding(ks, e, WHEN_FOCUSED, pressed)) {
            return true;
        }

        // We have no key binding. Let's try the path from our parent to the
        // window excluded. We store the path components so we can avoid
        // asking the same component twice.
        Container parent = this;
        Container top = this;
        while (parent != null && !(parent instanceof java.awt.Window)
                && !KeyboardManager.isInstanceOf(parent, "java.applet.Applet")) {
            if (parent instanceof JComponent) {
                if (ksE != null && ((JComponent) parent).processKeyBinding(ksE, e,
                        WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, pressed)) {
                    return true;
                }
                if (((JComponent) parent).processKeyBinding(ks, e,
                        WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, pressed)) {
                    return true;
                }
            }
            // This is done so that the children of a JInternalFrame are
            // given precedence for WHEN_IN_FOCUSED_WINDOW bindings before
            // other components WHEN_IN_FOCUSED_WINDOW bindings. This also gives
            // more precedence to the WHEN_IN_FOCUSED_WINDOW bindings of the
            // JInternalFrame's children vs the
            // WHEN_ANCESTOR_OF_FOCUSED_COMPONENT bindings of the parents.
            if (KeyboardManager.isInstanceOf(parent, "javax.swing.JInternalFrame")
                    && JComponent.processKeyBindingsForAllComponents(e, parent, pressed)) {
                return true;
            }
            top = parent;
            parent = parent.getParent();
        }

        // No components between the focused component and the window is
        // actually interested by the key event. Let's try the other
        // JComponent in this window.
        if (parent != null) {
            return JComponent.processKeyBindingsForAllComponents(e, parent, pressed);
        }
        // Android: a host-mounted sidebar has no Window above it; its root holds the
        // window's bindings (KeyboardManager registers them there).
        if (KeyboardManager.isInstanceOf(top, "javax.swing.JInternalFrame")) {
            return false;
        }
        return JComponent.processKeyBindingsForAllComponents(e, top, pressed);
    }

    static boolean processKeyBindingsForAllComponents(java.awt.event.KeyEvent e, Container container, boolean pressed) {
        KeyboardManager manager = KeyboardManager.getCurrentManager();
        if (manager.fireKeyboardAction(e, pressed, container)) {
            return true;
        }
        // Android: a tree whose components never went through addNotify has nothing
        // registered; walk it instead so its window bindings still fire.
        if (!manager.containerMap.containsKey(container) && !e.isConsumed()) {
            // Same stroke order as KeyboardManager.fireKeyboardAction: the extended key code first.
            if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) {
                return windowBinding(container, KeyStroke.getKeyStroke(e.getKeyChar()), e, pressed);
            }
            if (e.getKeyCode() != e.getExtendedKeyCode()
                    && windowBinding(container, KeyStroke.getKeyStroke(e.getExtendedKeyCode(), e.getModifiers(), !pressed), e, pressed)) {
                return true;
            }
            return windowBinding(container, KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers(), !pressed), e, pressed);
        }
        return false;
    }

    private static boolean windowBinding(java.awt.Component component, KeyStroke stroke, java.awt.event.KeyEvent event, boolean pressed) {
        if (!component.isVisible() || !component.isEnabled()) return false;
        if (component instanceof JComponent && ((JComponent) component).processKeyBinding(stroke, event, WHEN_IN_FOCUSED_WINDOW, pressed)) return true;
        if (component instanceof Container) for (java.awt.Component child : ((Container) component).getComponents()) if (windowBinding(child, stroke, event, pressed)) return true;
        return false;
    }

    /**
     * Host entry point after normal KeyListeners and before game-wide fallback: the
     * JDK's processKeyEvent tail, which consumes the event when a binding took it.
     */
    public boolean hostProcessKeyBinding(java.awt.event.KeyEvent event) {
        if (event.isConsumed()) return false;
        // JComponent.processKeyEvent consumes an event a binding took.
        if (processKeyBindings(event, event.getID() == java.awt.event.KeyEvent.KEY_PRESSED)) {
            event.consume();
            return true;
        }
        return false;
    }

    /** Invoked from <code>ComponentInputMap</code> when its bindings change. */
    void componentInputMapChanged(ComponentInputMap inputMap) {
        InputMap km = getInputMap(WHEN_IN_FOCUSED_WINDOW, false);
        while (km != inputMap && km != null) {
            km = km.getParent();
        }
        if (km != null) {
            registerWithKeyboardManager(false);
        }
    }

    /**
     * Registers the WHEN_IN_FOCUSED_WINDOW bindings with the KeyboardManager. If
     * <code>onlyIfNew</code> is true only actions that haven't been registered are pushed;
     * strokes no longer bound are unregistered.
     */
    private void registerWithKeyboardManager(boolean onlyIfNew) {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW, false);
        KeyStroke[] strokes;
        java.util.Hashtable<KeyStroke, KeyStroke> registered = registeredWindowStrokes;

        if (inputMap != null) {
            // Push any new KeyStrokes to the KeyboardManager.
            strokes = inputMap.allKeys();
            if (strokes != null) {
                for (int counter = strokes.length - 1; counter >= 0; counter--) {
                    if (!onlyIfNew || registered == null || registered.get(strokes[counter]) == null) {
                        KeyboardManager.getCurrentManager().registerKeyStroke(strokes[counter], this);
                    }
                    if (registered != null) {
                        registered.remove(strokes[counter]);
                    }
                }
            }
        } else {
            strokes = null;
        }
        // Remove any old ones.
        if (registered != null && registered.size() > 0) {
            for (KeyStroke ks : new java.util.ArrayList<KeyStroke>(registered.keySet())) {
                KeyboardManager.getCurrentManager().unregisterKeyStroke(ks, this);
            }
            registered.clear();
        }
        // Updated the registered Hashtable.
        if (strokes != null && strokes.length > 0) {
            if (registered == null) {
                registered = new java.util.Hashtable<KeyStroke, KeyStroke>(strokes.length);
                registeredWindowStrokes = registered;
            }
            for (int counter = strokes.length - 1; counter >= 0; counter--) {
                registered.put(strokes[counter], strokes[counter]);
            }
        } else {
            registeredWindowStrokes = null;
        }
    }

    /** Unregisters all the previously registered WHEN_IN_FOCUSED_WINDOW KeyStroke bindings. */
    private void unregisterWithKeyboardManager() {
        java.util.Hashtable<KeyStroke, KeyStroke> registered = registeredWindowStrokes;
        if (registered != null && registered.size() > 0) {
            for (KeyStroke ks : new java.util.ArrayList<KeyStroke>(registered.keySet())) {
                KeyboardManager.getCurrentManager().unregisterKeyStroke(ks, this);
            }
        }
        registeredWindowStrokes = null;
    }

    /**
     * Notifies this component that it now has a parent component: the JDK re-registers
     * the window bindings with the KeyboardManager under the new top-level ancestor.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        registerWithKeyboardManager(false);
        registerNextFocusableComponent();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        unregisterWithKeyboardManager();
        deregisterNextFocusableComponent();
    }

    /**
     * Stands in for an <code>ActionListener</code> passed to
     * <code>registerKeyboardAction</code>; also the action-map key it is bound under.
     */
    final class ActionStandin implements Action {
        final ActionListener actionListener;
        private final String command;
        // This will be non-null if actionListener is an Action.
        private final Action action;

        ActionStandin(ActionListener actionListener, String command) {
            this.actionListener = actionListener;
            if (actionListener instanceof Action) {
                this.action = (Action) actionListener;
            } else {
                this.action = null;
            }
            this.command = command;
        }

        public Object getValue(String key) {
            if (key != null) {
                if (key.equals(Action.ACTION_COMMAND_KEY)) {
                    return command;
                }
                if (action != null) {
                    return action.getValue(key);
                }
                if (key.equals(NAME)) {
                    return "ActionStandin";
                }
            }
            return null;
        }

        public boolean isEnabled() {
            if (actionListener == null) {
                // This keeps the old semantics where
                // registerKeyboardAction(null) would essentialy remove
                // the binding. We don't remove the binding from the
                // InputMap as that would still allow parent InputMaps
                // bindings to be accessed.
                return false;
            }
            if (action == null) {
                return true;
            }
            return action.isEnabled();
        }

        public void actionPerformed(java.awt.event.ActionEvent ae) {
            if (actionListener != null) {
                actionListener.actionPerformed(ae);
            }
        }

        // We don't allow any values to be added.
        public void putValue(String key, Object value) {}

        // Does nothing, our enabledness is determiend from our asociated action.
        public void setEnabled(boolean b) { }

        public void addPropertyChangeListener(PropertyChangeListener listener) {}
        public void removePropertyChangeListener(PropertyChangeListener listener) {}
    }

    // --- focus (OpenJDK 8u JComponent, JDK 9 InputVerifier) ----------------

    static {
        net.runelite.awt.impl.FocusVeto.install(JComponent::acceptRequestFocus);
    }

    /** The component whose InputVerifier is running; EDT-confined, as the JDK's AppContext slot. */
    private static JComponent inputVerifierSource;

    private static boolean acceptRequestFocus(java.awt.Component from, java.awt.Component to, boolean temporary,
                                              boolean focusedWindowChangeAllowed, java.awt.event.FocusEvent.Cause cause) {
        if (!(to instanceof JComponent) || !(from instanceof JComponent)) {
            return true;
        }
        JComponent target = (JComponent) to;
        if (!target.getVerifyInputWhenFocusTarget()) {
            return true;
        }
        JComponent jFocusOwner = (JComponent) from;
        InputVerifier iv = jFocusOwner.getInputVerifier();
        if (iv == null) {
            return true;
        }
        JComponent currentSource = inputVerifierSource;
        if (currentSource == jFocusOwner) {
            // We're currently calling into the InputVerifier
            // for this component, so allow the focus change.
            return true;
        }
        inputVerifierSource = jFocusOwner;
        try {
            return iv.shouldYieldFocus(jFocusOwner, target);
        } finally {
            // Restoring the outer source keeps a nested requestFocus from its verifier
            // from running that verifier again.
            inputVerifierSource = currentSource;
        }
    }

    private InputVerifier inputVerifier;
    private boolean verifyInputWhenFocusTarget = true;

    /** Sets the input verifier for this component; it runs before focus leaves this component. */
    public void setInputVerifier(InputVerifier inputVerifier) {
        InputVerifier oldInputVerifier = this.inputVerifier;
        this.inputVerifier = inputVerifier;
        firePropertyChange("inputVerifier", oldInputVerifier, inputVerifier);
    }

    /** Returns the input verifier for this component. */
    public InputVerifier getInputVerifier() {
        return inputVerifier;
    }

    /**
     * Sets the value to indicate whether input verifier for the
     * current focus owner will be called before this component requests
     * focus. The default is true.
     */
    public void setVerifyInputWhenFocusTarget(boolean verifyInputWhenFocusTarget) {
        boolean oldVerifyInputWhenFocusTarget = this.verifyInputWhenFocusTarget;
        this.verifyInputWhenFocusTarget = verifyInputWhenFocusTarget;
        firePropertyChange("verifyInputWhenFocusTarget", oldVerifyInputWhenFocusTarget, verifyInputWhenFocusTarget);
    }

    /** Returns whether the focus owner's input verifier runs before this component takes focus. */
    public boolean getVerifyInputWhenFocusTarget() {
        return verifyInputWhenFocusTarget;
    }

    private boolean focusTraversalKeysForwardSet;
    private boolean focusTraversalKeysBackwardSet;

    /**
     * Sets the focus traversal keys for a given traversal operation for this
     * Component. Swing records which sets were given explicitly so a look and feel
     * does not overwrite them.
     */
    @Override
    public void setFocusTraversalKeys(int id, java.util.Set<? extends java.awt.AWTKeyStroke> keystrokes) {
        if (id == java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS) {
            focusTraversalKeysForwardSet = true;
        }
        if (id == java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS) {
            focusTraversalKeysBackwardSet = true;
        }
        super.setFocusTraversalKeys(id, keystrokes);
    }

    /** Whether the application, rather than a look and feel, set the keys for <code>id</code>. */
    boolean isFocusTraversalKeysSetByClient(int id) {
        return id == java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS ? focusTraversalKeysForwardSet
            : id == java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS && focusTraversalKeysBackwardSet;
    }

    private static java.util.Set<java.awt.AWTKeyStroke> managingFocusForwardTraversalKeys;
    private static java.util.Set<java.awt.AWTKeyStroke> managingFocusBackwardTraversalKeys;

    /**
     * The forward traversal keys of a component that manages focus itself
     * (JTable, JTextArea): ctrl TAB, so a plain TAB stays with the component.
     */
    static synchronized java.util.Set<java.awt.AWTKeyStroke> getManagingFocusForwardTraversalKeys() {
        if (managingFocusForwardTraversalKeys == null) {
            managingFocusForwardTraversalKeys = new java.util.HashSet<java.awt.AWTKeyStroke>(1);
            managingFocusForwardTraversalKeys.add(KeyStroke.getKeyStroke(
                    java.awt.event.KeyEvent.VK_TAB, java.awt.event.InputEvent.CTRL_MASK));
        }
        return managingFocusForwardTraversalKeys;
    }

    /** The backward counterpart of {@link #getManagingFocusForwardTraversalKeys}: ctrl shift TAB. */
    static synchronized java.util.Set<java.awt.AWTKeyStroke> getManagingFocusBackwardTraversalKeys() {
        if (managingFocusBackwardTraversalKeys == null) {
            managingFocusBackwardTraversalKeys = new java.util.HashSet<java.awt.AWTKeyStroke>(1);
            managingFocusBackwardTraversalKeys.add(KeyStroke.getKeyStroke(
                    java.awt.event.KeyEvent.VK_TAB,
                    java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        }
        return managingFocusBackwardTraversalKeys;
    }

    /**
     * Sets a property on behalf of a look and feel ({@link LookAndFeel#installProperty}).
     * As in the JDK, focus traversal keys go in only when the application has not set its
     * own, and do not count as set by the client.  The JDK's subclasses override this for
     * their own primitive properties; the shadow's mostly do not, so any other property
     * goes to its public setter, without the JDK's "set by the client" bookkeeping.
     */
    @SuppressWarnings("unchecked")
    void setUIProperty(String propertyName, Object value) {
        int id = "focusTraversalKeysForward".equals(propertyName)
            ? java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS
            : "focusTraversalKeysBackward".equals(propertyName)
            ? java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS : -1;
        if (id != -1) {
            if (!isFocusTraversalKeysSetByClient(id)) {
                super.setFocusTraversalKeys(id, (java.util.Set<java.awt.AWTKeyStroke>) value);
            }
            return;
        }
        String setter = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            for (java.lang.reflect.Method method : getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(setter) && parameters.length == 1) {
                    if (parameters[0].isPrimitive() && value == null) {
                        throw new NullPointerException(propertyName);
                    }
                    method.invoke(this, value);
                    return;
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("property \"" + propertyName + "\" cannot be set using this method", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalArgumentException("property \"" + propertyName + "\" cannot be set using this method", cause);
        } catch (IllegalArgumentException e) {
            // Method.invoke reports a value of the wrong type this way.
            throw new ClassCastException(e.getMessage());
        }
        throw new IllegalArgumentException("property \"" + propertyName + "\" cannot be set using this method");
    }

    /**
     * In release 1.4, the focus subsystem was rearchitected.
     * Changes this <code>JComponent</code>'s focus traversal keys to
     * CTRL+TAB and CTRL+SHIFT+TAB in the JDK; the shadow keeps the answer only.
     *
     * @deprecated As of 1.4, replaced by
     *   <code>Component.setFocusTraversalKeys(int, Set)</code> and
     *   <code>Container.setFocusCycleRoot(boolean)</code>.
     */
    @Deprecated
    public boolean isManagingFocus() {
        return false;
    }

    /**
     * Requests focus on this <code>JComponent</code>'s
     * <code>FocusTraversalPolicy</code>'s default <code>Component</code>.
     *
     * @deprecated As of 1.4, replaced by
     * <code>FocusTraversalPolicy().getDefaultComponent(Container).requestFocus()</code>
     */
    @Deprecated
    public boolean requestDefaultFocus() {
        Container nearestRoot = (isFocusCycleRoot()) ? this : getFocusCycleRootAncestor();
        if (nearestRoot == null) {
            return false;
        }
        java.awt.FocusTraversalPolicy policy = nearestRoot.getFocusTraversalPolicy();
        java.awt.Component comp = policy == null ? null : policy.getDefaultComponent(nearestRoot);
        if (comp != null) {
            comp.requestFocus();
            return true;
        } else {
            return false;
        }
    }

    private java.awt.Component nextFocusableComponent;

    /**
     * Overrides the default <code>FocusTraversalPolicy</code> for this
     * <code>JComponent</code>'s focus traversal cycle by unconditionally
     * setting the specified <code>Component</code> as the next
     * <code>Component</code> in the cycle, and this <code>JComponent</code>
     * as the specified <code>Component</code>'s previous
     * <code>Component</code> in the cycle.
     *
     * @deprecated As of 1.4, replaced by <code>FocusTraversalPolicy</code>
     */
    @Deprecated
    public void setNextFocusableComponent(java.awt.Component aComponent) {
        boolean displayable = isDisplayable();
        if (displayable) {
            deregisterNextFocusableComponent();
        }
        nextFocusableComponent = aComponent;
        if (displayable) {
            registerNextFocusableComponent(aComponent);
        }
    }

    /**
     * Returns the <code>Component</code> set by a prior call to
     * <code>setNextFocusableComponent(Component)</code> on this
     * <code>JComponent</code>.
     *
     * @deprecated As of 1.4, replaced by <code>FocusTraversalPolicy</code>.
     */
    @Deprecated
    public java.awt.Component getNextFocusableComponent() {
        return nextFocusableComponent;
    }

    private void registerNextFocusableComponent() {
        registerNextFocusableComponent(nextFocusableComponent);
    }

    private void registerNextFocusableComponent(java.awt.Component nextFocusableComponent) {
        if (nextFocusableComponent == null) {
            return;
        }
        Container nearestRoot = (isFocusCycleRoot()) ? this : getFocusCycleRootAncestor();
        if (nearestRoot == null) {
            return;
        }
        java.awt.FocusTraversalPolicy policy = nearestRoot.getFocusTraversalPolicy();
        if (!(policy instanceof LegacyGlueFocusTraversalPolicy)) {
            policy = new LegacyGlueFocusTraversalPolicy(policy);
            nearestRoot.setFocusTraversalPolicy(policy);
        }
        ((LegacyGlueFocusTraversalPolicy) policy).setNextFocusableComponent(this, nextFocusableComponent);
    }

    private void deregisterNextFocusableComponent() {
        java.awt.Component nextFocusableComponent = this.nextFocusableComponent;
        if (nextFocusableComponent == null) {
            return;
        }
        Container nearestRoot = (isFocusCycleRoot()) ? this : getFocusCycleRootAncestor();
        if (nearestRoot == null) {
            return;
        }
        java.awt.FocusTraversalPolicy policy = nearestRoot.getFocusTraversalPolicy();
        if (policy instanceof LegacyGlueFocusTraversalPolicy) {
            ((LegacyGlueFocusTraversalPolicy) policy).unsetNextFocusableComponent(this, nextFocusableComponent);
        }
    }

    private java.util.Map<Object, Object> clientProperties;
    public void putClientProperty(Object key, Object value) {
        if (clientProperties == null) clientProperties = new java.util.HashMap<>();
        if (value == null) clientProperties.remove(key);
        else clientProperties.put(key, value);
        onClientPropertySet(key, value);
    }
    public Object getClientProperty(Object key) {
        return clientProperties != null ? clientProperties.get(key) : null;
    }
    /** Subclass hook. Called whenever a client property is set, after the value is stored. */
    protected void onClientPropertySet(Object key, Object value) {}

    /**
     * Swing forwards to the nearest JComponent ancestor in its coordinates; JViewport
     * overrides this to do the actual scrolling.
     */
    public void scrollRectToVisible(java.awt.Rectangle aRect) {
        if (aRect == null) return;
        int dx = getX(), dy = getY();
        Container parent = getParent();
        while (parent != null && !(parent instanceof JComponent)) {
            dx += parent.getX();
            dy += parent.getY();
            parent = parent.getParent();
        }
        if (parent != null) {
            aRect.x += dx;
            aRect.y += dy;
            ((JComponent) parent).scrollRectToVisible(aRect);
            aRect.x -= dx;
            aRect.y -= dy;
        }
    }

    /** Swing's visible rect: our bounds intersected with each ancestor's, in our coordinates. */
    public java.awt.Rectangle getVisibleRect() {
        java.awt.Rectangle r = new java.awt.Rectangle(0, 0, getWidth(), getHeight());
        int ox = 0, oy = 0;
        java.awt.Component c = this;
        Container p = getParent();
        while (p != null && !(p instanceof java.awt.Window)) {
            ox -= c.getX();
            oy -= c.getY();
            r = r.intersection(new java.awt.Rectangle(ox, oy, p.getWidth(), p.getHeight()));
            c = p;
            p = p.getParent();
        }
        if (r.width < 0 || r.height < 0) r.setSize(0, 0);
        return r;
    }

    public boolean requestFocusInWindow() { return super.requestFocusInWindow(); }
    public void grabFocus() { requestFocus(); }

    private boolean inheritsPopupMenu;
    public boolean getInheritsPopupMenu() { return inheritsPopupMenu; }
    public void setInheritsPopupMenu(boolean value) { inheritsPopupMenu = value; }

    public java.awt.Insets getInsets() { return border != null ? border.getBorderInsets(this) : super.getInsets(); }

    private boolean autoscrolls;
    public void setAutoscrolls(boolean autoscrolls) { this.autoscrolls = autoscrolls; }
    public boolean getAutoscrolls() { return autoscrolls; }

    private TransferHandler transferHandler;

    /**
     * Sets the {@code TransferHandler}, which provides support for transfer
     * of data into and out of this component via cut/copy/paste and drag
     * and drop. As on desktop, a drop target is installed unless the
     * application set its own.
     *
     * @since 1.4
     */
    public void setTransferHandler(TransferHandler newHandler) {
        TransferHandler oldHandler = transferHandler;
        transferHandler = newHandler;
        TransferHandler.installSwingDropTargetAsNecessary(this, newHandler);
        firePropertyChange("transferHandler", oldHandler, newHandler);
    }

    public TransferHandler getTransferHandler() { return transferHandler; }

    /**
     * Calculates a custom drop location for this type of component,
     * representing where a drop at the given point should insert data.
     * {@code null} means the plain point-only location.
     */
    TransferHandler.DropLocation dropLocationForPoint(java.awt.Point p) {
        return null;
    }

    /**
     * Called to set or clear the drop location during a DnD operation;
     * returns the state the next call is handed back.
     */
    Object setDropLocation(TransferHandler.DropLocation location, Object state, boolean forDrop) {
        return null;
    }

    /**
     * Called to indicate to this component that DnD is done; JTree cancels
     * its expand-on-hover timer here.
     */
    void dndDone() {
    }

    private JPopupMenu componentPopupMenu;
    public void setComponentPopupMenu(JPopupMenu menu) { this.componentPopupMenu = menu; }
    public JPopupMenu getComponentPopupMenu() { if (componentPopupMenu == null && inheritsPopupMenu && getParent() instanceof JComponent) return ((JComponent)getParent()).getComponentPopupMenu(); return componentPopupMenu; }

    @Override
    public void paint(java.awt.Graphics g) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (opaque) {
            java.awt.Color saved = g.getColor();
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (saved != null) g.setColor(saved);
        }
        if (printing) {
            printComponent(g);
            printBorder(g);
            printChildren(g);
        } else {
            paintComponent(g);
            paintBorder(g);
            paintChildren(g);
        }
    }

    protected void paintComponent(java.awt.Graphics g) {
        if(!paintingDefault&&ui!=null)ui.update(g,this);
    }

    protected void paintBorder(java.awt.Graphics g) {
        if (border != null) {
            border.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        }
    }

    private boolean paintingDefault;
    protected final boolean paintDelegate(java.awt.Graphics g){if(ui==null||paintingDefault)return false;ui.update(g,this);return true;}
    public final void hostPaintDefaultComponent(java.awt.Graphics g){boolean old=paintingDefault;paintingDefault=true;try{paintComponent(g);}finally{paintingDefault=old;}}
    public final java.awt.Dimension hostDefaultPreferredSize(){return getPreferredSize();}

    /** Host-side bridges so the Compose paint walker can invoke our protected paint hooks. */
    public final void hostPaintComponent(java.awt.Graphics g) { paintComponent(g); }
    public final void hostPaintBorder(java.awt.Graphics g) { paintBorder(g); }
}
