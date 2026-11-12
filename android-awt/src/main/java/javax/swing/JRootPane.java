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
import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;

/**
 * A lightweight container used behind the scenes by {@code JFrame},
 * {@code JDialog}, {@code JWindow}, {@code JApplet} and
 * {@code JInternalFrame}: a glass pane over a layered pane that holds the
 * optional menu bar and the content pane, laid out by {@link RootLayout}.
 *
 * <p>Unlike the JDK there is no {@code RootPaneUI} delegate: the default
 * button is pressed by an ENTER binding in the {@code WHEN_IN_FOCUSED_WINDOW}
 * input map that the root pane installs itself, which is what
 * {@code BasicRootPaneUI} installs on the desktop.
 */
public class JRootPane extends JComponent {
    private static final long serialVersionUID = 1L;

    private static final String uiClassID = "RootPaneUI";

    /** Constant used for the windowDecorationStyle property: no decorations. */
    public static final int NONE = 0;
    /** Constant for the windowDecorationStyle property: a Frame. */
    public static final int FRAME = 1;
    /** Constant for the windowDecorationStyle property: a plain Dialog. */
    public static final int PLAIN_DIALOG = 2;
    /** Constant for the windowDecorationStyle property: an information Dialog. */
    public static final int INFORMATION_DIALOG = 3;
    /** Constant for the windowDecorationStyle property: an error Dialog. */
    public static final int ERROR_DIALOG = 4;
    /** Constant for the windowDecorationStyle property: a JColorChooser Dialog. */
    public static final int COLOR_CHOOSER_DIALOG = 5;
    /** Constant for the windowDecorationStyle property: a JFileChooser Dialog. */
    public static final int FILE_CHOOSER_DIALOG = 6;
    /** Constant for the windowDecorationStyle property: a question Dialog. */
    public static final int QUESTION_DIALOG = 7;
    /** Constant for the windowDecorationStyle property: a warning Dialog. */
    public static final int WARNING_DIALOG = 8;

    private int windowDecorationStyle;

    /** The menu bar. */
    protected JMenuBar menuBar;

    /** The content pane. */
    protected Container contentPane;

    /** The layered pane that manages the menu bar and content pane. */
    protected JLayeredPane layeredPane;

    /**
     * The glass pane that overlays the menu bar and content pane, so it can
     * intercept mouse movements and such.
     */
    protected Component glassPane;

    /** The button that gets activated when the pane has the focus and ENTER is pressed. */
    protected JButton defaultButton;

    /**
     * Creates a {@code JRootPane}, setting up its {@code glassPane},
     * {@code layeredPane}, and {@code contentPane}.
     */
    public JRootPane() {
        setGlassPane(createGlassPane());
        setLayeredPane(createLayeredPane());
        setContentPane(createContentPane());
        setLayout(createRootLayout());
        setDoubleBuffered(true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "press");
        getActionMap().put("press", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                JButton button = defaultButton;
                if (button != null && button.isEnabled()) {
                    button.doClick();
                }
            }
        });
    }

    /**
     * Returns a constant identifying the type of Window decorations the
     * {@code JRootPane} is providing.
     */
    public int getWindowDecorationStyle() {
        return windowDecorationStyle;
    }

    /**
     * Sets the type of Window decorations (such as borders, widgets for
     * closing a Window, title ...) the {@code JRootPane} should provide.
     *
     * @throws IllegalArgumentException if {@code windowDecorationStyle} is not
     *         one of the decoration constants
     */
    public void setWindowDecorationStyle(int windowDecorationStyle) {
        if (windowDecorationStyle < 0 || windowDecorationStyle > WARNING_DIALOG) {
            throw new IllegalArgumentException("Invalid decoration style");
        }
        int oldWindowDecorationStyle = getWindowDecorationStyle();
        this.windowDecorationStyle = windowDecorationStyle;
        firePropertyChange("windowDecorationStyle", oldWindowDecorationStyle,
                windowDecorationStyle);
    }

    /** Returns a string that specifies the name of the L&amp;F class that renders this component. */
    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Called by the constructor methods to create the default
     * {@code layeredPane}.
     */
    protected JLayeredPane createLayeredPane() {
        JLayeredPane p = new JLayeredPane();
        p.setName(this.getName() + ".layeredPane");
        return p;
    }

    /**
     * Called by the constructor methods to create the default
     * {@code contentPane}: a {@code JPanel} with a {@code BorderLayout} that
     * puts unconstrained children in the center.
     */
    protected Container createContentPane() {
        JComponent c = new JPanel();
        c.setName(this.getName() + ".contentPane");
        c.setLayout(new BorderLayout() {
            private static final long serialVersionUID = 1L;

            @Override
            public void addLayoutComponent(Component comp, Object constraints) {
                if (constraints == null) {
                    constraints = BorderLayout.CENTER;
                }
                super.addLayoutComponent(comp, constraints);
            }
        });
        return c;
    }

    /**
     * Called by the constructor methods to create the default
     * {@code glassPane}: a hidden, non-opaque {@code JPanel}.
     */
    protected Component createGlassPane() {
        JComponent c = new JPanel();
        c.setName(this.getName() + ".glassPane");
        c.setVisible(false);
        c.setOpaque(false);
        return c;
    }

    /**
     * Called by the constructor methods to create the default
     * {@code layoutManager}.
     */
    protected LayoutManager createRootLayout() {
        return new RootLayout();
    }

    /** Adds or changes the menu bar used in the layered pane. */
    public void setJMenuBar(JMenuBar menu) {
        if (menuBar != null && menuBar.getParent() == layeredPane) {
            layeredPane.remove(menuBar);
        }
        menuBar = menu;
        if (menuBar != null) {
            layeredPane.add(menuBar, JLayeredPane.FRAME_CONTENT_LAYER);
        }
        revalidate();
    }

    /**
     * Specifies the menu bar value.
     *
     * @deprecated As of Swing version 1.0.3 replaced by
     *             {@code setJMenuBar(JMenuBar menu)}.
     */
    @Deprecated
    public void setMenuBar(JMenuBar menu) {
        setJMenuBar(menu);
    }

    /** Returns the menu bar from the layered pane. */
    public JMenuBar getJMenuBar() {
        return menuBar;
    }

    /**
     * Returns the menu bar value.
     *
     * @deprecated As of Swing version 1.0.3 replaced by {@code getJMenuBar()}.
     */
    @Deprecated
    public JMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Sets the content pane -- the container that holds the components
     * parented by the root pane.
     *
     * @throws IllegalComponentStateException if the content pane parameter is
     *         {@code null}
     */
    public void setContentPane(Container content) {
        if (content == null) {
            throw new IllegalComponentStateException("contentPane cannot be set to null.");
        }
        if (contentPane != null && contentPane.getParent() == layeredPane) {
            layeredPane.remove(contentPane);
        }
        contentPane = content;
        layeredPane.add(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        revalidate();
    }

    /** Returns the content pane -- the container that holds the components parented by the root pane. */
    public Container getContentPane() {
        return contentPane;
    }

    /**
     * Sets the layered pane for the root pane. The layered pane typically
     * holds a content pane and an optional {@code JMenuBar}.
     *
     * @throws IllegalComponentStateException if the layered pane parameter is
     *         {@code null}
     */
    public void setLayeredPane(JLayeredPane layered) {
        if (layered == null) {
            throw new IllegalComponentStateException("layeredPane cannot be set to null.");
        }
        if (layeredPane != null && layeredPane.getParent() == this) {
            this.remove(layeredPane);
        }
        layeredPane = layered;
        // The JDK's add(layeredPane, -1) reaches addImpl; the shadow Container
        // routes the three-argument add the same way without an addImpl hook.
        super.add(layeredPane, null, -1);
        // The menu bar and content pane live in the layered pane, so they move
        // with it; the JDK leaves that to the next setContentPane/setJMenuBar,
        // which never comes after construction.
        if (contentPane != null && contentPane.getParent() != layeredPane) {
            layeredPane.add(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        }
        if (menuBar != null && menuBar.getParent() != layeredPane) {
            layeredPane.add(menuBar, JLayeredPane.FRAME_CONTENT_LAYER);
        }
        revalidate();
    }

    /** Gets the layered pane used by the root pane. */
    public JLayeredPane getLayeredPane() {
        return layeredPane;
    }

    /**
     * Sets a specified {@code Component} to be the glass pane for this root
     * pane. The glass pane keeps the visibility of the one it replaces and is
     * always the first child, so it paints over everything else.
     *
     * @throws NullPointerException if the {@code glass} parameter is {@code null}
     */
    public void setGlassPane(Component glass) {
        if (glass == null) {
            throw new NullPointerException("glassPane cannot be set to null.");
        }
        boolean visible = false;
        if (glassPane != null && glassPane.getParent() == this) {
            this.remove(glassPane);
            visible = glassPane.isVisible();
        }
        glass.setVisible(visible);
        glassPane = glass;
        super.add(glassPane, null, 0);
        if (visible) {
            repaint();
        }
    }

    /** Returns the current glass pane for this {@code JRootPane}. */
    public Component getGlassPane() {
        return glassPane;
    }

    /**
     * If a descendant of this {@code JRootPane} calls {@code revalidate},
     * validate from here on down.
     *
     * @return true
     */
    public boolean isValidateRoot() {
        return true;
    }

    /**
     * The {@code glassPane} and {@code contentPane} have the same bounds, so
     * children may overlap when the glass pane is visible.
     *
     * @return true if the glass pane is not visible
     */
    public boolean isOptimizedDrawingEnabled() {
        return !glassPane.isVisible();
    }

    /**
     * Sets the {@code defaultButton} property, which determines the current
     * default button for this {@code JRootPane}. The default button is the
     * button which will be activated when a UI-defined activation event
     * (typically the ENTER key) occurs in the root pane regardless of whether
     * or not the button has keyboard focus.
     */
    public void setDefaultButton(JButton defaultButton) {
        JButton oldDefault = this.defaultButton;
        if (oldDefault != defaultButton) {
            this.defaultButton = defaultButton;
            if (oldDefault != null) {
                oldDefault.repaint();
            }
            if (defaultButton != null) {
                defaultButton.repaint();
            }
        }
        firePropertyChange("defaultButton", oldDefault, defaultButton);
    }

    /** Returns the value of the {@code defaultButton} property. */
    public JButton getDefaultButton() {
        return defaultButton;
    }

    // The shadow Container does not lay out on resize (see Container), and
    // frames and dialogs size their root pane without a validate pass after
    // it, so the root pane lays itself out as soon as its size changes.
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        doLayout();
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        doLayout();
    }

    /**
     * A custom layout manager that is responsible for the layout of
     * layeredPane, glassPane, and menuBar.
     */
    protected class RootLayout implements LayoutManager2, Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Returns the amount of space the layout would like to have: the
         * content pane's preferred size under the menu bar's, plus the insets.
         */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension rd, mbd;
            Insets i = getInsets();

            if (contentPane != null) {
                rd = contentPane.getPreferredSize();
            } else {
                rd = parent.getSize();
            }
            if (menuBar != null && menuBar.isVisible()) {
                mbd = menuBar.getPreferredSize();
            } else {
                mbd = new Dimension(0, 0);
            }
            return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                    rd.height + mbd.height + i.top + i.bottom);
        }

        /** Returns the minimum amount of space the layout needs. */
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension rd, mbd;
            Insets i = getInsets();
            if (contentPane != null) {
                rd = contentPane.getMinimumSize();
            } else {
                rd = parent.getSize();
            }
            if (menuBar != null && menuBar.isVisible()) {
                mbd = menuBar.getMinimumSize();
            } else {
                mbd = new Dimension(0, 0);
            }
            return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                    rd.height + mbd.height + i.top + i.bottom);
        }

        /**
         * Returns the maximum amount of space the layout can use. As in the
         * JDK the width is the smaller of the content pane's and the (possibly
         * absent, so zero) menu bar's.
         */
        @Override
        public Dimension maximumLayoutSize(Container target) {
            Dimension rd, mbd;
            Insets i = getInsets();
            if (menuBar != null && menuBar.isVisible()) {
                mbd = menuBar.getMaximumSize();
            } else {
                mbd = new Dimension(0, 0);
            }
            if (contentPane != null) {
                rd = contentPane.getMaximumSize();
            } else {
                // This is silly, but should stop an overflow error
                rd = new Dimension(Integer.MAX_VALUE,
                        Integer.MAX_VALUE - i.top - i.bottom - mbd.height - 1);
            }
            return new Dimension(Math.min(rd.width, mbd.width) + i.left + i.right,
                    rd.height + mbd.height + i.top + i.bottom);
        }

        /**
         * Instructs the layout manager to perform the layout: the layered and
         * glass panes fill the insets, the menu bar takes its preferred height
         * at the top and the content pane the rest.
         */
        @Override
        public void layoutContainer(Container parent) {
            Rectangle b = parent.getBounds();
            Insets i = getInsets();
            int contentY = 0;
            int w = b.width - i.right - i.left;
            int h = b.height - i.top - i.bottom;

            if (layeredPane != null) {
                layeredPane.setBounds(i.left, i.top, w, h);
            }
            if (glassPane != null) {
                glassPane.setBounds(i.left, i.top, w, h);
            }
            // Note: This is laying out the children in the layeredPane,
            // technically, these are not our children.
            if (menuBar != null && menuBar.isVisible()) {
                Dimension mbd = menuBar.getPreferredSize();
                menuBar.setBounds(0, 0, w, mbd.height);
                contentY += mbd.height;
            }
            if (contentPane != null) {
                contentPane.setBounds(0, contentY, w, h - contentY);
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public void addLayoutComponent(Component comp, Object constraints) {
        }

        @Override
        public float getLayoutAlignmentX(Container target) {
            return 0.0f;
        }

        @Override
        public float getLayoutAlignmentY(Container target) {
            return 0.0f;
        }

        @Override
        public void invalidateLayout(Container target) {
        }
    }
}
