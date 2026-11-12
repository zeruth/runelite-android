/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * <code>PopupFactory</code>, as the name implies, is used to obtain
 * instances of <code>Popup</code>s. <code>Popup</code>s are used to
 * display a <code>Component</code> above all other <code>Component</code>s
 * in a particular containment hierarchy. The general contract is that
 * once you have obtained a <code>Popup</code> from a
 * <code>PopupFactory</code>, you must invoke <code>hide</code> on the
 * <code>Popup</code>. The typical usage is:
 * <pre>
 *   PopupFactory factory = PopupFactory.getSharedInstance();
 *   Popup popup = factory.getPopup(owner, contents, x, y);
 *   popup.show();
 *   ...
 *   popup.hide();
 * </pre>
 *
 * <p>
 * Ported from OpenJDK 8u, with the JDK 11 {@code getPopup(..., boolean)}.
 * A light weight popup is added to the owner's root pane in
 * {@link JLayeredPane#POPUP_LAYER} when it fits there. There are no native
 * AWT peers on Android, so the medium weight kind (an AWT {@code Panel} in the
 * same layer on desktop) is light weight too. Anything else, including every
 * popup of a sidebar panel (those have no root pane), is heavy weight: the
 * panel host's overlay window.
 *
 * @see Popup
 * @since 1.4
 */
public class PopupFactory {
    /**
     * The shared instanceof <code>PopupFactory</code>.
     */
    private static PopupFactory sharedInstance;

    /**
     * Used to indicate a light weight popup should be used.
     */
    static final int LIGHT_WEIGHT_POPUP   = 0;

    /**
     * Used to indicate a medium weight Popup should be used.
     */
    static final int MEDIUM_WEIGHT_POPUP  = 1;

    /*
     * Used to indicate a heavy weight Popup should be used.
     */
    static final int HEAVY_WEIGHT_POPUP   = 2;

    /**
     * Default type of Popup to create.
     */
    private int popupType = LIGHT_WEIGHT_POPUP;

    /** The client property the JDK reads to force a heavy weight popup under a component. */
    static final Object forceHeavyWeightPopupKey = new StringBuffer("PopupFactory.forceHeavyWeightPopupKey");

    /**
     * Host side of heavy weight popups (not JDK API). The panel host installs
     * one to draw popup contents in an overlay window above the sidebar.
     * Both methods are called on the event dispatch thread.
     */
    public interface HostLayer {
        /**
         * Shows {@code contents}, already sized to its preferred size, with its
         * top left corner at the AWT screen point ({@code x}, {@code y}).
         *
         * @return a handle passed back to {@link #hide}, or {@code null} when
         *         no host shows {@code owner}
         */
        Object show(Component owner, Component contents, int x, int y);

        /** Removes the overlay {@link #show} returned. */
        void hide(Object handle);
    }

    static volatile HostLayer hostLayer;

    /**
     * Installs the host's heavy weight popup layer (not JDK API).
     *
     * @param layer the layer, or {@code null} to fall back to {@link JWindow}s
     */
    public static void setHostLayer(HostLayer layer) {
        hostLayer = layer;
    }

    /**
     * Sets the <code>PopupFactory</code> that will be used to obtain
     * <code>Popup</code>s.
     * This will throw an <code>IllegalArgumentException</code> if
     * <code>factory</code> is null.
     *
     * @param factory Shared PopupFactory
     * @exception IllegalArgumentException if <code>factory</code> is null
     * @see #getPopup
     */
    public static void setSharedInstance(PopupFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("PopupFactory can not be null");
        }
        synchronized (PopupFactory.class) {
            sharedInstance = factory;
        }
    }

    /**
     * Returns the shared <code>PopupFactory</code> which can be used
     * to obtain <code>Popup</code>s.
     *
     * @return Shared PopupFactory
     */
    public static PopupFactory getSharedInstance() {
        synchronized (PopupFactory.class) {
            if (sharedInstance == null) {
                sharedInstance = new PopupFactory();
            }
            return sharedInstance;
        }
    }

    /**
     * Provides a hint as to the type of <code>Popup</code> that should
     * be created.
     */
    void setPopupType(int type) {
        popupType = type;
    }

    /**
     * Returns the preferred type of Popup to create.
     */
    int getPopupType() {
        return popupType;
    }

    /**
     * Creates a <code>Popup</code> for the Component <code>owner</code>
     * containing the Component <code>contents</code>. <code>owner</code>
     * is used to determine which <code>Window</code> the new
     * <code>Popup</code> will parent the <code>Component</code> the
     * <code>Popup</code> creates to. A null <code>owner</code> implies there
     * is no valid parent. <code>x</code> and
     * <code>y</code> specify the preferred initial location to place
     * the <code>Popup</code> at. Based on screen size, or other paramaters,
     * the <code>Popup</code> may not display at <code>x</code> and
     * <code>y</code>.
     *
     * @param owner    Component mouse coordinates are relative to, may be null
     * @param contents Contents of the Popup
     * @param x        Initial x screen coordinate
     * @param y        Initial y screen coordinate
     * @exception IllegalArgumentException if contents is null
     * @return Popup containing Contents
     */
    public Popup getPopup(Component owner, Component contents,
                          int x, int y) throws IllegalArgumentException {
        return getPopup(owner, contents, x, y, false);
    }

    /**
     * Creates a {@code Popup} for the Component {@code owner} containing the
     * Component {@code contents}, heavy weight when {@code isHeavyWeightPopup}
     * is {@code true}.
     *
     * @param owner Component mouse coordinates are relative to, may be null
     * @param contents Contents of the Popup
     * @param x Initial x screen coordinate
     * @param y Initial y screen coordinate
     * @param isHeavyWeightPopup true if Popup should be heavy weight,
     * otherwise popup type will be selected by popup factory.
     * @throws IllegalArgumentException if contents is null
     * @return Popup containing Contents
     * @since 11
     */
    protected Popup getPopup(Component owner, Component contents, int x, int y,
            boolean isHeavyWeightPopup) throws IllegalArgumentException { // JDK 11
        if (contents == null) {
            throw new IllegalArgumentException(
                    "Popup.getPopup must be passed non-null contents");
        }
        if (isHeavyWeightPopup) {
            return getPopup(owner, contents, x, y, HEAVY_WEIGHT_POPUP);
        }
        int popupType = getPopupType(owner, contents, x, y);
        Popup popup = getPopup(owner, contents, x, y, popupType);

        if (popup == null) {
            // Didn't fit, force to heavy.
            popup = getPopup(owner, contents, x, y, HEAVY_WEIGHT_POPUP);
        }
        return popup;
    }

    /**
     * Returns the popup type to use for the specified parameters.
     */
    private int getPopupType(Component owner, Component contents,
                             int ownerX, int ownerY) {
        int popupType = getPopupType();

        // The JDK also goes heavy for an owner inside a heavy weight popup's
        // window; host overlays parent their contents to nothing, so such an
        // owner has no root pane and the light weight attempt below fails.
        if (owner == null) {
            popupType = HEAVY_WEIGHT_POPUP;
        }
        else if (popupType == LIGHT_WEIGHT_POPUP &&
                 !(contents instanceof JToolTip) &&
                 !(contents instanceof JPopupMenu)) {
            popupType = MEDIUM_WEIGHT_POPUP;
        }

        // Check if the parent component is an option pane.  If so we need to
        // force a heavy weight popup in order to have event dispatching work
        // correctly.
        Component c = owner;
        while (c != null) {
            if (c instanceof JComponent) {
                if (((JComponent)c).getClientProperty(
                            forceHeavyWeightPopupKey) == Boolean.TRUE) {
                    popupType = HEAVY_WEIGHT_POPUP;
                    break;
                }
            }
            c = c.getParent();
        }

        return popupType;
    }

    /**
     * Obtains the appropriate <code>Popup</code> based on
     * <code>popupType</code>.
     */
    private Popup getPopup(Component owner, Component contents,
                           int ownerX, int ownerY, int popupType) {
        switch(popupType) {
        case LIGHT_WEIGHT_POPUP:
        case MEDIUM_WEIGHT_POPUP:
            return getLightWeightPopup(owner, contents, ownerX, ownerY);
        case HEAVY_WEIGHT_POPUP:
            return new Popup(owner, contents, ownerX, ownerY);
        }
        return null;
    }

    /**
     * Returns true if the Component <code>i</code> inside a heavy weight
     * <code>Popup</code>.
     */
    private boolean invokerInHeavyWeightPopup(Component i) {
        if (i != null) {
            Container parent;
            for (parent = i.getParent() ; parent != null ; parent =
                    parent.getParent()) {
                if (parent instanceof JWindow && ((JWindow) parent).getRootPane().getClientProperty(HEAVY_WEIGHT_KEY) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Object HEAVY_WEIGHT_KEY = new StringBuffer("PopupFactory.heavyWeight");

    /**
     * Returns a light weight <code>Popup</code> in the owner's root pane, or
     * null when the owner has none or the contents do not fit in it.
     */
    private Popup getLightWeightPopup(Component owner, Component contents,
                                      int ownerX, int ownerY) {
        JRootPane rootPane = SwingUtilities.getRootPane(owner);
        if (rootPane == null || !owner.isShowing()) {
            return null;
        }
        JLayeredPane layeredPane = rootPane.getLayeredPane();
        Point location = new Point(ownerX, ownerY);
        SwingUtilities.convertPointFromScreen(location, layeredPane);
        Dimension size = contents.getPreferredSize();
        Rectangle bounds = new Rectangle(location.x, location.y, size.width, size.height);
        if (!new Rectangle(0, 0, layeredPane.getWidth(), layeredPane.getHeight()).contains(bounds)) {
            return null;
        }
        return new LightWeightPopup(layeredPane, contents, bounds);
    }

    private static final class LightWeightPopup extends Popup {
        private JLayeredPane layeredPane;
        private Component contents;
        private final Rectangle bounds;

        LightWeightPopup(JLayeredPane layeredPane, Component contents, Rectangle bounds) {
            this.layeredPane = layeredPane;
            this.contents = contents;
            this.bounds = bounds;
        }

        @Override
        public void show() {
            if (contents == null || contents.getParent() == layeredPane) {
                return;
            }
            contents.setBounds(bounds);
            layeredPane.add(contents, JLayeredPane.POPUP_LAYER, 0);
            contents.validate();
            layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void hide() {
            if (contents != null && contents.getParent() == layeredPane) {
                layeredPane.remove(contents);
                layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            contents = null;
            layeredPane = null;
        }
    }
}
