/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 * {@code JLayeredPane} adds depth to a JFC/Swing container, allowing components to overlap
 * each other when needed. An {@code Integer} object specifies each component's depth in the
 * container, where higher-numbered components sit "on top" of other components; within a
 * layer, position 0 is the topmost and -1 the bottommost. Index 0 of the child list is the
 * frontmost child, which is what hit testing and painting order follow.
 */
public class JLayeredPane extends JComponent implements javax.accessibility.Accessible {
    private static final long serialVersionUID = 1L;

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

    /** Convenience object defining the Default layer. Equivalent to new Integer(0).*/
    public static final Integer DEFAULT_LAYER = 0;
    /** Convenience object defining the Palette layer. Equivalent to new Integer(100).*/
    public static final Integer PALETTE_LAYER = 100;
    /** Convenience object defining the Modal layer. Equivalent to new Integer(200).*/
    public static final Integer MODAL_LAYER = 200;
    /** Convenience object defining the Popup layer. Equivalent to new Integer(300).*/
    public static final Integer POPUP_LAYER = 300;
    /** Convenience object defining the Drag layer. Equivalent to new Integer(400).*/
    public static final Integer DRAG_LAYER = 400;
    /** Convenience object defining the Frame Content layer, used by JRootPane. */
    public static final Integer FRAME_CONTENT_LAYER = -30000;

    /** Bound property */
    public static final String LAYER_PROPERTY = "layeredContainerLayer";

    // Hashtable to store layer values for non-JComponent components
    private Hashtable<Component, Integer> componentToLayer;

    /** Create a new JLayeredPane */
    public JLayeredPane() {
        setLayout(null);
    }

    /**
     * Places the component by its layer: an Integer constraint sets the layer first, and the
     * index is a position within that layer.
     */
    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        int layer;
        int pos;

        if (constraints instanceof Integer) {
            layer = ((Integer) constraints).intValue();
            setLayer(comp, layer);
        } else {
            layer = getLayer(comp);
        }

        pos = insertIndexForLayer(layer, index);
        super.addImpl(comp, constraints, pos);
        comp.validate();
        comp.repaint();
    }

    /** Remove the indexed component from this pane. */
    @Override
    public void remove(int index) {
        Component c = getComponent(index);
        super.remove(index);
        if (c != null && !(c instanceof JComponent)) {
            getComponentToLayer().remove(c);
        }
    }

    @Override
    public void remove(Component c) {
        boolean child = c != null && c.getParent() == this;
        super.remove(c);
        if (child && !(c instanceof JComponent)) {
            getComponentToLayer().remove(c);
        }
    }

    /** Removes all the components from this container. */
    @Override
    public void removeAll() {
        Component[] children = getComponents();
        Hashtable<Component, Integer> cToL = getComponentToLayer();
        for (int counter = children.length - 1; counter >= 0; counter--) {
            Component c = children[counter];
            if (c != null && !(c instanceof JComponent)) {
                cToL.remove(c);
            }
        }
        super.removeAll();
    }

    /** Sets the layer property on a JComponent. Does not cause any side effects like setLayer(). */
    public static void putLayer(JComponent c, int layer) {
        c.putClientProperty(LAYER_PROPERTY, Integer.valueOf(layer));
    }

    /** Gets the layer property for a JComponent, DEFAULT_LAYER when none is set. */
    public static int getLayer(JComponent c) {
        Integer i;
        if ((i = (Integer) c.getClientProperty(LAYER_PROPERTY)) != null) {
            return i.intValue();
        }
        return DEFAULT_LAYER.intValue();
    }

    /** The first JLayeredPane which contains the specified component, or null. */
    public static JLayeredPane getLayeredPaneAbove(Component c) {
        if (c == null) {
            return null;
        }
        Component parent = c.getParent();
        while (parent != null && !(parent instanceof JLayeredPane)) {
            parent = parent.getParent();
        }
        return (JLayeredPane) parent;
    }

    /** Sets the layer attribute on the specified component, making it the bottommost in that layer. */
    public void setLayer(Component c, int layer) {
        setLayer(c, layer, -1);
    }

    /** Sets the layer attribute for the specified component and also sets its position within that layer. */
    public void setLayer(Component c, int layer, int position) {
        Integer layerObj = getObjectForLayer(layer);

        if (layer == getLayer(c) && position == getPosition(c)) {
            repaint(c.getBounds());
            return;
        }

        if (c instanceof JComponent) {
            ((JComponent) c).putClientProperty(LAYER_PROPERTY, layerObj);
        } else {
            getComponentToLayer().put(c, layerObj);
        }

        if (c.getParent() == null || c.getParent() != this) {
            repaint(c.getBounds());
            return;
        }

        int index = insertIndexForLayer(c, layer, position);

        setComponentZOrder(c, index);
        repaint(c.getBounds());
    }

    /** Returns the layer attribute for the specified Component. */
    public int getLayer(Component c) {
        Integer i;
        if (c instanceof JComponent) {
            i = (Integer) ((JComponent) c).getClientProperty(LAYER_PROPERTY);
        } else {
            i = getComponentToLayer().get(c);
        }

        if (i == null) {
            return DEFAULT_LAYER.intValue();
        }
        return i.intValue();
    }

    /** Returns the index of the specified Component, ignoring layers; -1 when not a child. */
    public int getIndexOf(Component c) {
        int i, count;

        count = getComponentCount();
        for (i = 0; i < count; i++) {
            if (c == getComponent(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Moves the component to the top of the components in its current layer (position 0). */
    public void moveToFront(Component c) {
        setPosition(c, 0);
    }

    /** Moves the component to the bottom of the components in its current layer (position -1). */
    public void moveToBack(Component c) {
        setPosition(c, -1);
    }

    /** Moves the component to {@code position} within its current layer. */
    public void setPosition(Component c, int position) {
        setLayer(c, getLayer(c), position);
    }

    /** Get the relative position of the component within its layer; -1 when not a child. */
    public int getPosition(Component c) {
        int i, startLayer, curLayer, startLocation, pos = 0;

        getComponentCount();
        startLocation = getIndexOf(c);

        if (startLocation == -1) {
            return -1;
        }

        startLayer = getLayer(c);
        for (i = startLocation - 1; i >= 0; i--) {
            curLayer = getLayer(getComponent(i));
            if (curLayer == startLayer) {
                pos++;
            } else {
                return pos;
            }
        }
        return pos;
    }

    /** Returns the highest layer value from all current children, or 0 if there are none. */
    public int highestLayer() {
        if (getComponentCount() > 0) {
            return getLayer(getComponent(0));
        }
        return 0;
    }

    /** Returns the lowest layer value from all current children, or 0 if there are none. */
    public int lowestLayer() {
        int count = getComponentCount();
        if (count > 0) {
            return getLayer(getComponent(count - 1));
        }
        return 0;
    }

    /** Returns the number of children currently in the specified layer. */
    public int getComponentCountInLayer(int layer) {
        int i, count, curLayer;
        int layerCount = 0;

        count = getComponentCount();
        for (i = 0; i < count; i++) {
            curLayer = getLayer(getComponent(i));
            if (curLayer == layer) {
                layerCount++;
            } else if (layerCount > 0 || curLayer < layer) {
                break;
            }
        }

        return layerCount;
    }

    /** Returns an array of the components in the specified layer. */
    public Component[] getComponentsInLayer(int layer) {
        int i, count, curLayer;
        int layerCount = 0;
        Component[] results;

        results = new Component[getComponentCountInLayer(layer)];
        count = getComponentCount();
        for (i = 0; i < count; i++) {
            curLayer = getLayer(getComponent(i));
            if (curLayer == layer) {
                results[layerCount++] = getComponent(i);
            } else if (layerCount > 0 || curLayer < layer) {
                break;
            }
        }

        return results;
    }

    /** Returns a hashtable that maps components to layers. */
    protected Hashtable<Component, Integer> getComponentToLayer() {
        if (componentToLayer == null) {
            componentToLayer = new Hashtable<Component, Integer>(4);
        }
        return componentToLayer;
    }

    /** Returns the Integer object associated with a specified layer. */
    protected Integer getObjectForLayer(int layer) {
        return Integer.valueOf(layer);
    }

    /** Primitive method that determines the proper location to insert a new child based on layer and position requests. */
    protected int insertIndexForLayer(int layer, int position) {
        return insertIndexForLayer(null, layer, position);
    }

    /**
     * This method is an extended version of insertIndexForLayer() to support setLayer which
     * uses Container.setZOrder which does not remove the component from the containment
     * hierarchy though we need to ignore it when calculating the insertion index.
     */
    private int insertIndexForLayer(Component comp, int layer, int position) {
        int i, count, curLayer;
        int layerStart = -1;
        int layerEnd = -1;
        int componentCount = getComponentCount();

        ArrayList<Component> compList = new ArrayList<Component>(componentCount);
        for (int index = 0; index < componentCount; index++) {
            if (getComponent(index) != comp) {
                compList.add(getComponent(index));
            }
        }

        count = compList.size();
        for (i = 0; i < count; i++) {
            curLayer = getLayer(compList.get(i));
            if (layerStart == -1 && curLayer == layer) {
                layerStart = i;
            }
            if (curLayer < layer) {
                if (i == 0) {
                    // layer is greater than any current layer
                    // [ ASSERT(layer > highestLayer()) ]
                    layerStart = 0;
                    layerEnd = 0;
                } else {
                    layerEnd = i;
                }
                break;
            }
        }

        // layer requested is lower than any current layer
        // [ ASSERT(layer < lowestLayer()) ]
        // put it on the bottom of the stack
        if (layerStart == -1 && layerEnd == -1) {
            return count;
        }

        // In the case of a single layer entry handle the degenerative cases
        if (layerStart != -1 && layerEnd == -1) {
            layerEnd = count;
        }

        if (layerEnd != -1 && layerStart == -1) {
            layerStart = layerEnd;
        }

        // If we are adding to the bottom, return the last element
        if (position == -1) {
            return layerEnd;
        }

        // Otherwise make sure the requested position falls in the
        // proper range
        if (position > -1 && layerStart + position <= layerEnd) {
            return layerStart + position;
        }

        // Otherwise return the end of the layer
        return layerEnd;
    }
}
