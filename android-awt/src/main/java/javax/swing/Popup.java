/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Popups are used to display a <code>Component</code> to the user, typically
 * on top of all the other <code>Component</code>s in a particular containment
 * hierarchy. <code>Popup</code>s have a very small life cycle. Once you
 * have obtained a <code>Popup</code>, and hidden it (invoked the
 * <code>hide</code> method), you should no longer
 * invoke any methods on it.
 *
 * <p>
 * Ported from OpenJDK 8u. A {@code Popup} built with contents is the
 * heavy weight kind: on desktop it owns a window above the owner's. Here the
 * panel host draws it as an overlay above the sidebar (see
 * {@link PopupFactory#setHostLayer}), so it escapes the panel bounds like a
 * desktop window; with no host layer installed it falls back to a
 * {@link JWindow}.
 *
 * @see PopupFactory
 * @since 1.4
 */
public class Popup {
    private Component owner;
    private Component contents;
    private int x;
    private int y;
    private Object hostHandle;
    private JWindow window;

    /**
     * Creates a <code>Popup</code> for the Component <code>owner</code>
     * containing the Component <code>contents</code>. <code>owner</code>
     * is used to determine which <code>Window</code> the new
     * <code>Popup</code> will parent the <code>Component</code> the
     * <code>Popup</code> creates to.
     * A null <code>owner</code> implies there is no valid parent.
     * <code>x</code> and
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
     */
    protected Popup(Component owner, Component contents, int x, int y) {
        this();
        if (contents == null) {
            throw new IllegalArgumentException("Contents must be non-null");
        }
        reset(owner, contents, x, y);
    }

    /**
     * Creates a <code>Popup</code>. This is provided for subclasses.
     */
    protected Popup() {
    }

    /**
     * Makes the <code>Popup</code> visible. If the <code>Popup</code> is
     * currently visible, this has no effect.
     */
    public void show() {
        if (contents == null || hostHandle != null || window != null && window.isVisible()) {
            return;
        }
        PopupFactory.HostLayer layer = PopupFactory.hostLayer;
        if (layer != null) {
            // The popup window packs to the contents' preferred size.
            Dimension size = contents.getPreferredSize();
            contents.setBounds(0, 0, Math.max(1, size.width), Math.max(1, size.height));
            contents.validate();
            hostHandle = layer.show(owner, contents, x, y);
            return;
        }
        if (window == null) {
            window = new JWindow();
            window.getContentPane().add(contents);
            Dimension size = contents.getPreferredSize();
            window.setBounds(x, y, Math.max(1, size.width), Math.max(1, size.height));
        }
        window.setVisible(true);
    }

    /**
     * Hides and disposes of the <code>Popup</code>. Once a <code>Popup</code>
     * has been disposed you should no longer invoke methods on it. A
     * <code>dispose</code>d <code>Popup</code> may be reclaimed and later used
     * based on the <code>PopupFactory</code>. As such, if you invoke methods
     * on a <code>disposed</code> <code>Popup</code>, indeterminate
     * behavior will result.
     */
    public void hide() {
        if (hostHandle != null) {
            Object handle = hostHandle;
            hostHandle = null;
            PopupFactory.HostLayer layer = PopupFactory.hostLayer;
            if (layer != null) {
                layer.hide(handle);
            }
        }
        if (window != null) {
            window.getContentPane().remove(contents);
            window.dispose();
            window = null;
        }
        dispose();
    }

    /**
     * Frees any resources the <code>Popup</code> may be holding onto.
     */
    void dispose() {
        owner = null;
        contents = null;
    }

    /**
     * Resets the <code>Popup</code> to an initial state.
     */
    void reset(Component owner, Component contents, int ownerX, int ownerY) {
        this.owner = owner;
        this.contents = contents;
        this.x = ownerX;
        this.y = ownerY;
    }
}
