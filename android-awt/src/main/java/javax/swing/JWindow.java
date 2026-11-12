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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.LayoutManager;
import java.awt.Window;

/**
 * A container that can be displayed anywhere on the user's desktop. It has no
 * title bar or window-management buttons; its single child is a
 * {@link JRootPane}, and while root pane checking is enabled children, layout
 * and removal are forwarded to the content pane, as with {@code JFrame}.
 */
public class JWindow extends Window implements javax.accessibility.Accessible, RootPaneContainer {
    private static final long serialVersionUID = 1L;

    /**
     * The {@code JRootPane} instance that manages the {@code contentPane}
     * and optional {@code menuBar} for this window, as well as the
     * {@code glassPane}.
     */
    protected JRootPane rootPane;

    /**
     * If true then calls to {@code add} and {@code setLayout} will be forwarded
     * to the {@code contentPane}. This is initially false, but is set to true
     * when the {@code JWindow} is constructed.
     */
    protected boolean rootPaneCheckingEnabled = false;

    private TransferHandler transferHandler;

    /** Creates a window with no specified owner. */
    public JWindow() {
        this((Window) null);
    }

    /**
     * Creates a window with the specified {@code GraphicsConfiguration}; the
     * Android host has a single screen device, so the configuration only
     * travels to {@code Window}.
     */
    public JWindow(GraphicsConfiguration gc) {
        this(null, gc);
    }

    /** Creates a window with the specified owner frame. */
    public JWindow(Frame owner) {
        this((Window) owner);
    }

    /** Creates a window with the specified owner window. */
    public JWindow(Window owner) {
        this(owner, null);
    }

    /**
     * Creates a window with the specified owner window and
     * {@code GraphicsConfiguration}.
     */
    public JWindow(Window owner, GraphicsConfiguration gc) {
        super(owner, gc);
        windowInit();
    }

    /** Called by the constructors to init the {@code JWindow} properly. */
    protected void windowInit() {
        setLayout(new BorderLayout());
        setRootPane(createRootPane());
        setRootPaneCheckingEnabled(true);
        // Window starts hidden on the shadow too; kept explicit because Popup
        // reuses a JWindow and checks isVisible before showing it.
        setVisible(false);
    }

    /**
     * Called by the constructor methods to create the default {@code rootPane}.
     *
     * @return the new root pane
     */
    protected JRootPane createRootPane() {
        JRootPane rp = new JRootPane();
        rp.setOpaque(true);
        return rp;
    }

    /**
     * Returns whether calls to {@code add} and {@code setLayout} are forwarded
     * to the {@code contentPane}.
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    /**
     * Sets whether calls to {@code add} and {@code setLayout} are forwarded to
     * the {@code contentPane}.
     */
    protected void setRootPaneCheckingEnabled(boolean enabled) {
        rootPaneCheckingEnabled = enabled;
    }

    /**
     * Sets the {@code transferHandler} property, a mechanism to support
     * transfer of data into this component.
     */
    public void setTransferHandler(TransferHandler newHandler) {
        TransferHandler oldHandler = transferHandler;
        transferHandler = newHandler;
        firePropertyChange("transferHandler", oldHandler, newHandler);
    }

    /** Gets the {@code transferHandler} property. */
    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    /**
     * Calls {@code paint(g)}. This method was overridden to prevent an
     * unnecessary call to clear the background.
     */
    @Override
    public void update(Graphics g) {
        paint(g);
    }

    // The shadow Container has no addImpl, so each add overload forwards itself.
    @Override
    public Component add(Component comp) {
        if (isRootPaneCheckingEnabled()) {
            return getContentPane().add(comp);
        }
        return super.add(comp);
    }

    @Override
    public Component add(Component comp, int index) {
        if (isRootPaneCheckingEnabled()) {
            return getContentPane().add(comp, index);
        }
        return super.add(comp, index);
    }

    @Override
    public Component add(String name, Component comp) {
        if (isRootPaneCheckingEnabled()) {
            return getContentPane().add(name, comp);
        }
        return super.add(name, comp);
    }

    @Override
    public void add(Component comp, Object constraints) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints);
        } else {
            super.add(comp, constraints);
        }
    }

    @Override
    public void add(Component comp, Object constraints, int index) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints, index);
        } else {
            super.add(comp, constraints, index);
        }
    }

    /**
     * Removes the specified component from the container. If {@code comp} is
     * not the {@code rootPane}, this will forward the call to the
     * {@code contentPane}.
     */
    @Override
    public void remove(Component comp) {
        if (comp == rootPane) {
            super.remove(comp);
        } else {
            getContentPane().remove(comp);
        }
    }

    /**
     * Sets the {@code LayoutManager}; forwarded to the {@code contentPane}
     * while root pane checking is enabled.
     */
    @Override
    public void setLayout(LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    /** Returns the {@code rootPane} object for this window. */
    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    /**
     * Sets the new {@code rootPane} object for this window. This method is
     * called by the constructor.
     */
    protected void setRootPane(JRootPane root) {
        if (rootPane != null) {
            super.remove(rootPane);
        }
        rootPane = root;
        if (rootPane != null) {
            boolean checkingEnabled = isRootPaneCheckingEnabled();
            try {
                setRootPaneCheckingEnabled(false);
                add(rootPane, BorderLayout.CENTER);
            } finally {
                setRootPaneCheckingEnabled(checkingEnabled);
            }
        }
    }

    @Override
    public Container getContentPane() {
        return getRootPane().getContentPane();
    }

    @Override
    public void setContentPane(Container contentPane) {
        getRootPane().setContentPane(contentPane);
    }

    @Override
    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    @Override
    public void setLayeredPane(JLayeredPane layeredPane) {
        getRootPane().setLayeredPane(layeredPane);
    }

    @Override
    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    @Override
    public void setGlassPane(Component glassPane) {
        getRootPane().setGlassPane(glassPane);
    }

    /**
     * Returns a string representation of this {@code JWindow}.
     */
    @Override
    protected String paramString() {
        return super.paramString() + ",rootPaneCheckingEnabled="
                + (rootPaneCheckingEnabled ? "true" : "false");
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJWindow();
        }
        return accessibleContext;
    }

    /** Accessibility support for Swing windows: the AWT WINDOW context. */
    protected class AccessibleJWindow extends AccessibleAWTWindow {
    }
}
