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

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.LayoutManager;

/**
 * An extended version of {@code java.applet.Applet} that adds support for the
 * Swing component architecture: a single {@link JRootPane} child, with
 * children, layout and removal forwarded to its content pane while root pane
 * checking is enabled.
 */
public class JApplet extends Applet implements RootPaneContainer {
    private static final long serialVersionUID = 1L;

    /**
     * @see #getRootPane
     * @see #setRootPane
     */
    protected JRootPane rootPane;

    /**
     * If true then calls to {@code add} and {@code setLayout} will be forwarded
     * to the {@code contentPane}. This is initially false, but is set to true
     * when the {@code JApplet} is constructed.
     */
    protected boolean rootPaneCheckingEnabled = false;

    /**
     * Creates a swing applet instance, with a {@code BorderLayout} holding its
     * root pane.
     */
    public JApplet() {
        super();
        setForeground(Color.black);
        setBackground(Color.white);
        setLayout(new BorderLayout());
        setRootPane(createRootPane());
        setRootPaneCheckingEnabled(true);
    }

    /**
     * Called by the constructor methods to create the default rootPane.
     *
     * @return the new root pane
     */
    protected JRootPane createRootPane() {
        JRootPane rp = new JRootPane();
        rp.setOpaque(true);
        return rp;
    }

    /**
     * Just calls {@code paint(g)}. This method was overridden to prevent an
     * unnecessary call to clear the background.
     */
    @Override
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Sets the menubar for this applet.
     *
     * @param menuBar the menubar being placed in the applet
     */
    public void setJMenuBar(JMenuBar menuBar) {
        getRootPane().setJMenuBar(menuBar);
    }

    /**
     * Returns the menubar set on this applet.
     *
     * @return the menubar
     */
    public JMenuBar getJMenuBar() {
        return getRootPane().getJMenuBar();
    }

    /**
     * Returns whether calls to {@code add} and {@code setLayout} are forwarded
     * to the {@code contentPane}.
     *
     * @return true if {@code add} and {@code setLayout} are forwarded
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    /**
     * Sets whether calls to {@code add} and {@code setLayout} are forwarded to
     * the {@code contentPane}.
     *
     * @param enabled true if {@code add} and {@code setLayout} are forwarded
     */
    protected void setRootPaneCheckingEnabled(boolean enabled) {
        rootPaneCheckingEnabled = enabled;
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
     *
     * @param comp the component to be removed
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
     *
     * @param manager the {@code LayoutManager}
     */
    @Override
    public void setLayout(LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    /**
     * Returns the rootPane object for this applet.
     *
     * @return the root pane
     */
    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    /**
     * Sets the rootPane property. This method is called by the constructor.
     *
     * @param root the {@code rootPane} object for this applet
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
}
