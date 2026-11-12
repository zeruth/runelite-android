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

import java.util.ArrayList;
import java.util.List;

public class Container extends Component {
    private static final long serialVersionUID = 4613797578919906343L;

    private final List<Component> children = new ArrayList<>();
    // Real java.awt.Container has NO default layout — null means "I'll position children
    // myself via setBounds". Only Panel/JPanel default to FlowLayout (set in their ctors).
    private LayoutManager layout = null;

    protected Container() {
    }

    public int getComponentCount() { return children.size(); }
    @Override public void addNotify() {
        super.addNotify();
        for (Component child : getComponents()) if (!child.notified) child.addNotify();
    }
    @Override public void removeNotify() {
        for (Component child : getComponents()) child.removeNotify();
        super.removeNotify();
    }

    public Component getComponent(int n) { return children.get(n); }

    public Component[] getComponents() { return children.toArray(new Component[0]); }

    public Component add(Component comp) {
        return add(comp, -1);
    }

    /**
     * A component has one parent: adding it elsewhere first takes it out of the old one, as
     * the JDK's addImpl does. Without this it stayed listed in both, and the old parent's
     * later removeAll() nulled its parent while the new one still painted it — quest-helper's
     * AssistLevelPanel adds its title to mainPanel, then to northPanel, then clears mainPanel.
     */
    private void detach(Component comp) {
        Container old = comp.getParent();
        if (old == null) return;
        if (old != this) old.remove(comp);
        else if (children.remove(comp) && layout != null) layout.removeLayoutComponent(comp);
    }
    private void checkCycle(Component comp) {
        for (Component ancestor = this; ancestor != null; ancestor = ancestor.getParent())
            if (ancestor == comp) throw new IllegalArgumentException("container cycle");
    }

    public Component add(Component comp, int index) {
        if (comp == null) return null;
        checkCycle(comp);
        int at = children.indexOf(comp);
        if (at >= 0 && at < index) index--;
        detach(comp);
        if (index < 0 || index >= children.size()) children.add(comp);
        else children.add(index, comp);
        comp.setParent(this);
        if (layout instanceof LayoutManager2) ((LayoutManager2) layout).addLayoutComponent(comp, null);
        else if (layout != null) layout.addLayoutComponent(null, comp);
        invalidate();
        return comp;
    }

    /** Legacy AWT add(name, comp) — delegates to the constraints-taking form. */
    public Component add(String name, Component comp) {
        add(comp, name);
        return comp;
    }

    public void add(Component comp, Object constraints) {
        if (comp == null) return;
        checkCycle(comp);
        if (children.contains(comp)) {
            // add(comp, -1) already added; just register constraint
        } else {
            detach(comp);
            children.add(comp);
            comp.setParent(this);
        }
        if (layout instanceof LayoutManager2) {
            ((LayoutManager2) layout).addLayoutComponent(comp, constraints);
        } else if (layout != null && constraints instanceof String) {
            layout.addLayoutComponent((String) constraints, comp);
        }
        invalidate();
    }

    public void add(Component comp, Object constraints, int index) {
        if (comp == null) throw new NullPointerException("component");
        if (index < -1 || index > children.size()) throw new IllegalArgumentException("invalid index");
        checkCycle(comp);
        detach(comp);
        if (index == -1 || index >= children.size()) children.add(comp); else children.add(index, comp);
        comp.setParent(this);
        if (layout instanceof LayoutManager2) ((LayoutManager2) layout).addLayoutComponent(comp, constraints);
        else if (layout != null && (constraints == null || constraints instanceof String)) layout.addLayoutComponent((String) constraints, comp);
        invalidate();
    }

    public void remove(int index) {
        Component c = children.remove(index);
        if (c != null) {
            if (layout != null) layout.removeLayoutComponent(c);
            c.setParent(null);
        }
        invalidate();
    }

    public void remove(Component comp) {
        if (children.remove(comp)) {
            if (layout != null) layout.removeLayoutComponent(comp);
            comp.setParent(null);
            invalidate();
        }
    }

    public void removeAll() {
        if (children.isEmpty()) return;
        for (Component c : children) {
            if (layout != null) layout.removeLayoutComponent(c);
            c.setParent(null);
        }
        children.clear();
        invalidate();
    }

    public int getComponentZOrder(Component comp) { return children.indexOf(comp); }
    public void setComponentZOrder(Component comp, int index) {
        int cur = children.indexOf(comp);
        if (cur < 0) return;
        children.remove(cur);
        int target = Math.min(Math.max(0, index), children.size());
        children.add(target, comp);
        invalidate();
    }

    public LayoutManager getLayout() { return layout; }
    public void setLayout(LayoutManager mgr) { this.layout = mgr; invalidate(); }

    @Override
    public Dimension getPreferredSize() {
        // Explicit setPreferredSize wins, mirroring real Swing — e.g. PluginListItem sets
        // its row to (PANEL_WIDTH, 20) and expects that even though its BorderLayout would
        // compute a smaller intrinsic size.
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (layout != null) {
            // The layout's answer stands even at 0×0 (an emptied or all-hidden panel), as in
            // the JDK. Falling back to the current size here made emptied panels keep their old
            // height forever: preferred = current = whatever the parent last gave it.
            Dimension d = layout.preferredLayoutSize(this);
            if (d != null) return d;
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        // Explicit setMinimumSize wins — ClientPanel sets GAME_FIXED_SIZE (765×503) as its
        // floor so the OSRS canvas can never shrink below the legacy game viewport. Without
        // this check, BorderLayout would sum the child Canvas's current bounds (often
        // smaller) and ClientUI.Layout.layout() would let the game area shrink to absorb
        // the sidebar instead of growing the frame.
        if (isMinimumSizeSet()) return super.getMinimumSize();
        if (layout != null) {
            Dimension d = layout.minimumLayoutSize(this);
            if (d != null && (d.width > 0 || d.height > 0)) return d;
        }
        return super.getMinimumSize();
    }

    // --- focus (after OpenJDK 8u Container) ---

    private boolean focusCycleRoot, focusTraversalPolicyProvider;
    private transient FocusTraversalPolicy focusTraversalPolicy;

    public void setFocusCycleRoot(boolean focusCycleRoot) {
        boolean old;
        synchronized (this) {
            old = this.focusCycleRoot;
            this.focusCycleRoot = focusCycleRoot;
        }
        firePropertyChange("focusCycleRoot", old, focusCycleRoot);
    }
    /** True when set, and for the root of a tree the Android host mounts (see {@link Component#isHostMountedRoot}). */
    public boolean isFocusCycleRoot() { return focusCycleRoot || isHostMountedRoot(); }
    public boolean isFocusCycleRoot(Container container) {
        if (isFocusCycleRoot() && container == this) return true;
        return super.isFocusCycleRoot(container);
    }
    public final void setFocusTraversalPolicyProvider(boolean provider) {
        boolean old;
        synchronized (this) {
            old = focusTraversalPolicyProvider;
            focusTraversalPolicyProvider = provider;
        }
        firePropertyChange("focusTraversalPolicyProvider", old, provider);
    }
    public final boolean isFocusTraversalPolicyProvider() { return focusTraversalPolicyProvider; }
    public void setFocusTraversalPolicy(FocusTraversalPolicy policy) {
        FocusTraversalPolicy old;
        synchronized (this) {
            old = focusTraversalPolicy;
            focusTraversalPolicy = policy;
        }
        firePropertyChange("focusTraversalPolicy", old, policy);
    }
    /**
     * Returns this container's policy, the cycle root's, or the manager's default;
     * null unless this is a focus cycle root or policy provider.
     */
    public FocusTraversalPolicy getFocusTraversalPolicy() {
        if (!isFocusTraversalPolicyProvider() && !isFocusCycleRoot()) return null;
        FocusTraversalPolicy policy = focusTraversalPolicy;
        if (policy != null) return policy;
        Container rootAncestor = getFocusCycleRootAncestor();
        return rootAncestor != null ? rootAncestor.getFocusTraversalPolicy()
            : KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalPolicy();
    }
    public boolean isFocusTraversalPolicySet() { return focusTraversalPolicy != null; }
    /** Moves focus down one cycle, to this root's default component. */
    public void transferFocusDownCycle() {
        if (isFocusCycleRoot()) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().setGlobalCurrentFocusCycleRootPriv(this);
            Component toFocus = getFocusTraversalPolicy().getDefaultComponent(this);
            if (toFocus != null) toFocus.requestFocus(java.awt.event.FocusEvent.Cause.TRAVERSAL_DOWN);
        }
    }
    @Override
    Container getTraversalRoot() {
        if (!isFocusCycleRoot()) return super.getTraversalRoot();
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Container currentFocusCycleRoot = manager.getCurrentFocusCycleRoot();
        Container root;
        if (currentFocusCycleRoot == this) {
            root = this;
        } else {
            root = getFocusCycleRootAncestor();
            if (root == null) root = this;
        }
        if (root != currentFocusCycleRoot) manager.setGlobalCurrentFocusCycleRootPriv(root);
        return root;
    }
    @Override
    int focusTraversalKeyLength() { return KeyboardFocusManager.TRAVERSAL_KEY_LENGTH; }

    public Insets getInsets() { return new Insets(0, 0, 0, 0); }

    @Override
    public void doLayout() {
        if (layout != null) layout.layoutContainer(this);
    }

    // Note: setBounds/setSize do NOT call doLayout — that would recurse since ClientUI.Layout
    // itself calls content.setSize. Layout cascades happen via Container.validate() once per
    // Compose frame in Window.renderToBackbuffer.

    /**
     * Lay this container out and recurse, unless the subtree is already valid.
     *
     * The short-circuit is the whole point: with validity tracked (see
     * {@link Component#invalidate()}) a steady-state frame reaches the root, finds it valid
     * and returns without touching a single LayoutManager. Before, the host called this every
     * Compose frame and it relaid the entire tree unconditionally.
     *
     * Ordering matters. doLayout() sizes children, and a size change invalidates the child
     * *and* re-marks this container and its ancestors — so `setValid(true)` has to come after
     * both the layout and the child recursion, which is also what the JDK does. That makes a
     * pass self-consistent: the transient invalidation raised by our own layout work is
     * absorbed before we declare the subtree clean. It settles because layout managers are
     * functions of their inputs and {@link Component#setBounds} only invalidates when a
     * dimension actually changed, so a second pass over unchanged inputs is a no-op.
     *
     * That last part is a real requirement, not a nicety. A layout manager whose output
     * depends on how many times it has run will not be re-run once a pass completes, and its
     * geometry will drift from what the old relay-everything loop produced — measured, not
     * assumed. The JDK behaves the same way for the same reason, and
     * {@link Window#FORCED_VALIDATE_INTERVAL_MS} bounds the drift by re-dirtying the tree a
     * few times a second regardless.
     */
    @Override
    public void validate() {
        if (isValid()) return;
        // Catch per-container so one bad container's layout NPE doesn't abort the cascade.
        try { doLayout(); } catch (Throwable ignored) {}
        int count = children.size();
        for (int i = 0; i < count; i++) {
            if (i < children.size()) {
                Component c = children.get(i);
                if (c != null) {
                    try { c.validate(); } catch (Throwable ignored) {}
                }
            }
        }
        setValid(true);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        paintChildren(g);
    }

    public void paintComponents(Graphics g) {
        paintChildren(g);
    }

    protected void paintChildren(Graphics g) {
        int count = children.size();
        if (count == 0) return;
        for (int i = count - 1; i >= 0; i--) {
            if (i >= children.size()) continue;
            Component c = children.get(i);
            if (c == null || !c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) continue;
            Graphics cg = g.create(c.x, c.y, c.width, c.height);
            try {
                c.paint(cg);
            } catch (Throwable t) {
                // Swing swallows paint errors per-component; mirror that to keep the tree drawing.
            } finally {
                cg.dispose();
            }
        }
    }
}
