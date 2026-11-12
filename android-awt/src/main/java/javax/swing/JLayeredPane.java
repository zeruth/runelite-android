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

import java.awt.Component;
import java.awt.Graphics;
import java.util.Map;
import java.util.WeakHashMap;

public class JLayeredPane extends JComponent implements javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJLayeredPane();
        }
        return accessibleContext;
    }

    /** Accessibility support for layered panes: role LAYERED_PANE. */
    protected class AccessibleJLayeredPane extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LAYERED_PANE;
        }
    }
    private static final long serialVersionUID = 1L;

    public static final Integer DEFAULT_LAYER = 0;
    public static final Integer PALETTE_LAYER = 100;
    public static final Integer MODAL_LAYER = 200;
    public static final Integer POPUP_LAYER = 300;
    public static final Integer DRAG_LAYER = 400;
    public static final Integer FRAME_CONTENT_LAYER = -30000;

    public static final String LAYER_PROPERTY = "layeredContainerLayer";

    private final Map<Component, Integer> layers = new WeakHashMap<>();

    @Override public Component add(Component c) {
        add(c, Integer.valueOf(getLayer(c)), -1);
        return c;
    }

    @Override public Component add(Component c, int position) {
        add(c, Integer.valueOf(getLayer(c)), position);
        return c;
    }

    @Override public void add(Component c, Object layer) { add(c, layer, -1); }

    public void add(Component c, Object layer, int position) {
        int value = layer instanceof Integer ? (Integer) layer : getLayer(c);
        layers.put(c, value);
        if (c instanceof JComponent) putLayer((JComponent) c, value);
        super.add(c, -1);
        setPosition(c, position);
    }

    public void setLayer(Component c, int layer) { setLayer(c, layer, -1); }
    public void setLayer(Component c, int layer, int position) {
        layers.put(c, layer);
        if (c instanceof JComponent) putLayer((JComponent) c, layer);
        if (c.getParent() == this) setPosition(c, position);
        repaint();
    }

    public int getLayer(Component c) {
        return c instanceof JComponent ? getLayer((JComponent) c) : layers.getOrDefault(c, 0);
    }

    public int getComponentCountInLayer(int layer) { return getComponentsInLayer(layer).length; }
    public Component[] getComponentsInLayer(int layer) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        for (Component child : getComponents()) if (getLayer(child) == layer) result.add(child);
        return result.toArray(new Component[0]);
    }

    public int getPosition(Component c) {
        if (c.getParent() != this) return -1;
        int position = 0;
        for (Component child : getComponents()) {
            if (child == c) return position;
            if (getLayer(child) == getLayer(c)) position++;
        }
        return -1;
    }

    public void setPosition(Component c, int position) {
        if (c.getParent() != this) return;
        int index = 0, sameLayer = 0, layer = getLayer(c);
        for (Component child : getComponents()) {
            if (child == c) continue;
            int other = getLayer(child);
            if (other > layer || (other == layer && (position < 0 || sameLayer < position))) {
                index++;
                if (other == layer) sameLayer++;
            } else break;
        }
        setComponentZOrder(c, index);
        repaint();
    }

    public void moveToFront(Component c) { setPosition(c, 0); }
    public void moveToBack(Component c) { setPosition(c, -1); }
    public static int getLayer(JComponent c) {
        Object layer = c.getClientProperty(LAYER_PROPERTY);
        return layer instanceof Integer ? (Integer) layer : 0;
    }
    public static JLayeredPane getLayeredPaneAbove(Component c) {
        for (Component current = c; current != null; current = current.getParent())
            if (current instanceof JLayeredPane) return (JLayeredPane) current;
        return null;
    }
    public static void putLayer(JComponent c, int layer) { c.putClientProperty(LAYER_PROPERTY, layer); }
    public int highestLayer() { return getComponentCount() == 0 ? 0 : getLayer(getComponent(0)); }
    public int lowestLayer() { return getComponentCount() == 0 ? 0 : getLayer(getComponent(getComponentCount() - 1)); }

    // Index zero is the frontmost child for hit testing. Paint from back to front.
    @Override protected void paintChildren(Graphics g) {
        Component[] children = getComponents();
        for (int i = children.length - 1; i >= 0; i--) {
            Component c = children[i];
            if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) continue;
            Graphics childGraphics = g.create(c.getX(), c.getY(), c.getWidth(), c.getHeight());
            try { c.paint(childGraphics); }
            finally { childGraphics.dispose(); }
        }
    }
}
