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

    private transient javax.accessibility.AccessibleContext accessibleContext;
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new javax.accessibility.AccessibleContext() {};
        return accessibleContext;
    }

    private Border border;
    private String toolTipText;
    private boolean doubleBuffered = true;
    private boolean opaque;
    private float alignmentX = 0.5f;
    private float alignmentY = 0.5f;

    public JComponent() {
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & (java.awt.event.HierarchyEvent.PARENT_CHANGED | java.awt.event.HierarchyEvent.SHOWING_CHANGED)) == 0) return;
            boolean attached = getParent() != null && isVisible();
            int id = attached ? javax.swing.event.AncestorEvent.ANCESTOR_ADDED : javax.swing.event.AncestorEvent.ANCESTOR_REMOVED;
            javax.swing.event.AncestorEvent event = new javax.swing.event.AncestorEvent(this, id,
                e.getChanged() instanceof Container ? (Container)e.getChanged() : getParent(), e.getChangedParent());
            for (AncestorListener listener : ancestorListeners.getListeners(AncestorListener.class)) {
                if (attached) listener.ancestorAdded(event); else listener.ancestorRemoved(event);
            }
        });
    }
    private final javax.swing.event.EventListenerList ancestorListeners = new javax.swing.event.EventListenerList();
    public Border getBorder() { return border; }
    // Border drives getInsets(), which every layout manager reads — a border change is a
    // layout change.
    public void setBorder(Border border) { Border old = this.border; this.border = border; firePropertyChange("border", old, border); revalidate(); repaint(); }

    public String getToolTipText() { return toolTipText; }
    public void setToolTipText(String text) { String old = this.toolTipText; this.toolTipText = text; firePropertyChange(TOOL_TIP_TEXT_KEY, old, text); }

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

    public void addAncestorListener(AncestorListener l) { ancestorListeners.add(AncestorListener.class, l); }
    public void removeAncestorListener(AncestorListener l) { ancestorListeners.remove(AncestorListener.class, l); }
    public AncestorListener[] getAncestorListeners() { return ancestorListeners.getListeners(AncestorListener.class); }

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

    public void repaint(java.awt.Rectangle r) {
        repaint(0, r.x, r.y, r.width, r.height);
    }

    private ActionMap actionMap;
    private final InputMap[] inputMaps = new InputMap[3];
    public ActionMap getActionMap() { if (actionMap == null) actionMap = new ActionMap(); return actionMap; }
    public void setActionMap(ActionMap map) { actionMap = map; }
    public InputMap getInputMap() { return getInputMap(WHEN_FOCUSED); }
    public InputMap getInputMap(int condition) {
        checkCondition(condition);
        if (inputMaps[condition] == null) inputMaps[condition] = new InputMap();
        return inputMaps[condition];
    }
    public void setInputMap(int condition, InputMap map) { checkCondition(condition); inputMaps[condition] = map; }
    /** The JDK's package-private accessors: SwingUtilities walks the maps without creating them. */
    final InputMap getInputMap(int condition, boolean create) { return create ? getInputMap(condition) : (condition < 0 || condition > 2 ? null : inputMaps[condition]); }
    final ActionMap getActionMap(boolean create) { return create ? getActionMap() : actionMap; }
    private void checkCondition(int condition) { if (condition < 0 || condition > 2) throw new IllegalArgumentException("condition"); }
    public void registerKeyboardAction(ActionListener listener, KeyStroke stroke, int condition) { registerKeyboardAction(listener, null, stroke, condition); }
    public void registerKeyboardAction(ActionListener listener, String command, KeyStroke stroke, int condition) {
        InputMap inputMap = getInputMap(condition, true);
        ActionStandin action = new ActionStandin(listener, command);
        inputMap.put(stroke, action);
        getActionMap(true).put(action, action);
    }

    /**
     * The JDK's wrapper for registerKeyboardAction listeners. SwingUtilities.notifyAction
     * recognizes it to pass a null action command when none was registered.
     */
    static final class ActionStandin implements Action {
        final ActionListener actionListener;
        private final String command;
        private final Action action;

        ActionStandin(ActionListener actionListener, String command) {
            this.actionListener = actionListener;
            this.action = actionListener instanceof Action ? (Action) actionListener : null;
            this.command = command;
        }

        public Object getValue(String key) {
            if (key != null) {
                if (key.equals(Action.ACTION_COMMAND_KEY)) return command;
                if (action != null) return action.getValue(key);
                if (key.equals(NAME)) return "ActionStandin";
            }
            return null;
        }

        public boolean isEnabled() {
            if (actionListener == null) return false;
            return action == null || action.isEnabled();
        }

        public void actionPerformed(java.awt.event.ActionEvent ae) {
            if (actionListener != null) actionListener.actionPerformed(ae);
        }

        // As in the JDK, the standin is immutable; the wrapped Action keeps its own state.
        public void putValue(String key, Object value) {}
        public void setEnabled(boolean b) {}
        public void addPropertyChangeListener(PropertyChangeListener listener) {}
        public void removePropertyChangeListener(PropertyChangeListener listener) {}
    }
    public void unregisterKeyboardAction(KeyStroke stroke) {
        for (InputMap map : inputMaps) if (map != null) { Object key = map.get(stroke); map.remove(stroke); if (key != null && actionMap != null) actionMap.remove(key); }
    }
    public KeyStroke[] getRegisteredKeyStrokes() {
        java.util.Set<KeyStroke> result = new java.util.LinkedHashSet<>();
        for (InputMap map : inputMaps) if (map != null && map.allKeys() != null) java.util.Collections.addAll(result, map.allKeys());
        return result.toArray(new KeyStroke[0]);
    }
    public int getConditionForKeyStroke(KeyStroke stroke) { for (int i=0;i<3;i++) if (inputMaps[i] != null && inputMaps[i].get(stroke) != null) return i; return UNDEFINED_CONDITION; }
    public ActionListener getActionForKeyStroke(KeyStroke stroke) {
        int condition = getConditionForKeyStroke(stroke);
        if (condition < 0 || actionMap == null) return null;
        Action action = actionMap.get(inputMaps[condition].get(stroke));
        return action instanceof ActionStandin ? ((ActionStandin) action).actionListener : action;
    }
    protected boolean processKeyBinding(KeyStroke stroke, java.awt.event.KeyEvent event, int condition, boolean pressed) {
        InputMap map = getInputMap(condition, false);
        ActionMap am = getActionMap(false);
        if (map != null && am != null && isEnabled()) {
            Object binding = map.get(stroke);
            Action action = binding == null ? null : am.get(binding);
            if (action != null) return SwingUtilities.notifyAction(action, stroke, event, this, event.getModifiers());
        }
        return false;
    }
    /** Host entry point after normal KeyListeners and before game-wide fallback. */
    public boolean hostProcessKeyBinding(java.awt.event.KeyEvent event) {
        if (event.isConsumed()) return false;
        // JComponent.processKeyEvent consumes an event a binding took.
        if (processKeyBindings(event, event.getID() == java.awt.event.KeyEvent.KEY_PRESSED)) {
            event.consume();
            return true;
        }
        return false;
    }
    /** The JDK's focused -> ancestors -> window walk; the window pass runs on the topmost ancestor. */
    boolean processKeyBindings(java.awt.event.KeyEvent event, boolean pressed) {
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
        if (processKeyBinding(stroke, event, WHEN_FOCUSED, pressed)) return true;
        Container parent = this, root = this;
        while (parent != null && !(parent instanceof java.awt.Window) && !(parent instanceof java.applet.Applet)) {
            if (parent instanceof JComponent && ((JComponent) parent).processKeyBinding(stroke, event, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, pressed)) return true;
            root = parent;
            parent = parent.getParent();
        }
        // The sidebar host roots panels outside any Window; there the topmost ancestor
        // stands in for the window the JDK would search.
        return processKeyBindingsForAllComponents(event, parent != null ? parent : root, pressed);
    }
    static boolean processKeyBindingsForAllComponents(java.awt.event.KeyEvent event, Container container, boolean pressed) {
        return windowBinding(container, KeyStroke.getKeyStrokeForEvent(event), event, pressed);
    }
    private static boolean windowBinding(java.awt.Component component, KeyStroke stroke, java.awt.event.KeyEvent event, boolean pressed) {
        if (!component.isVisible() || !component.isEnabled()) return false;
        if (component instanceof JComponent && ((JComponent)component).processKeyBinding(stroke, event, WHEN_IN_FOCUSED_WINDOW, pressed)) return true;
        if (component instanceof Container) for (java.awt.Component child : ((Container)component).getComponents()) if (windowBinding(child, stroke, event, pressed)) return true;
        return false;
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
    public void resetKeyboardActions() { for (int i=0;i<3;i++) inputMaps[i]=null; actionMap=null; }

    private boolean inheritsPopupMenu;
    public boolean getInheritsPopupMenu() { return inheritsPopupMenu; }
    public void setInheritsPopupMenu(boolean value) { inheritsPopupMenu = value; }

    public java.awt.Insets getInsets() { return border != null ? border.getBorderInsets(this) : super.getInsets(); }

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
        paintComponent(g);
        paintBorder(g);
        paintChildren(g);
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
