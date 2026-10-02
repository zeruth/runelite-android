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

    public Component getComponent(int n) { return children.get(n); }

    public Component[] getComponents() { return children.toArray(new Component[0]); }

    public Component add(Component comp) {
        return add(comp, -1);
    }

    public Component add(Component comp, int index) {
        if (comp == null) return null;
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
        if (children.contains(comp)) {
            // add(comp, -1) already added; just register constraint
        } else {
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
            Dimension d = layout.preferredLayoutSize(this);
            if (d != null && (d.width > 0 || d.height > 0)) return d;
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

    public void setFocusCycleRoot(boolean focusCycleRoot) {}
    public boolean isFocusCycleRoot() { return false; }
    public void setFocusTraversalPolicyProvider(boolean provider) {}
    public boolean isFocusTraversalPolicyProvider() { return false; }
    public void setFocusTraversalPolicy(Object policy) {}
    public Object getFocusTraversalPolicy() { return null; }

    public Insets getInsets() { return new Insets(0, 0, 0, 0); }

    private static final Object TREE_LOCK = new Object();
    public final Object getTreeLock() { return TREE_LOCK; }

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

    public void paintComponents(Graphics g) { paintChildren(g); }

    protected void paintChildren(Graphics g) {
        int count = children.size();
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            if (i >= children.size()) break;
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
