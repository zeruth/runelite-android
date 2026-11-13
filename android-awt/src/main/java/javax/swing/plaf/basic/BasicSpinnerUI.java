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
package javax.swing.plaf.basic;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.SpinnerUI;
import javax.swing.plaf.UIResource;

/**
 * The Basic spinner delegate as the JDK builds it: three children (the next and previous
 * {@link BasicArrowButton}s and the spinner's editor) placed by a layout that stacks the buttons
 * at the trailing edge and gives the editor the rest; a press on an arrow commits the pending edit
 * and steps, and holding it repeats.  The one divergence is the arrow column, at least
 * {@value #MIN_ARROW_WIDTH}px wide so a fingertip hits it.
 */
public class BasicSpinnerUI extends SpinnerUI {
    /** Touch floor for the arrow column; the JDK's arrow buttons prefer 16px. */
    private static final int MIN_ARROW_WIDTH = 24;
    private static final Dimension zeroSize = new Dimension(0, 0);

    protected JSpinner spinner;
    private Handler handler;
    private PropertyChangeListener propertyChangeListener;
    private final ArrowButtonHandler nextButtonHandler = new ArrowButtonHandler(true);
    private final ArrowButtonHandler previousButtonHandler = new ArrowButtonHandler(false);

    public BasicSpinnerUI() {}

    public static ComponentUI createUI(JComponent c) { return new BasicSpinnerUI(); }

    private void maybeAdd(Component c, String s) {
        if (c != null) spinner.add(c, s);
    }

    @Override public void installUI(JComponent c) {
        this.spinner = (JSpinner) c;
        installDefaults();
        installListeners();
        maybeAdd(createNextButton(), "Next");
        maybeAdd(createPreviousButton(), "Previous");
        maybeAdd(createEditor(), "Editor");
        updateEnabledState();
        installKeyboardActions();
    }

    @Override public void uninstallUI(JComponent c) {
        uninstallDefaults();
        uninstallListeners();
        this.spinner = null;
        c.removeAll();
    }

    protected void installListeners() {
        propertyChangeListener = createPropertyChangeListener();
        spinner.addPropertyChangeListener(propertyChangeListener);
        if (UIManager.getBoolean("Spinner.disableOnBoundaryValues")) spinner.addChangeListener(getHandler());
    }

    protected void uninstallListeners() {
        spinner.removePropertyChangeListener(propertyChangeListener);
        spinner.removeChangeListener(handler);
        handler = null;
    }

    protected void installDefaults() {
        spinner.setLayout(createLayout());
        LookAndFeel.installBorder(spinner, "Spinner.border");
        LookAndFeel.installColorsAndFont(spinner, "Spinner.background", "Spinner.foreground", "Spinner.font");
        LookAndFeel.installProperty(spinner, "opaque", Boolean.TRUE);
    }

    protected void uninstallDefaults() {
        spinner.setLayout(null);
    }

    private Handler getHandler() {
        if (handler == null) handler = new Handler();
        return handler;
    }

    protected void installNextButtonListeners(Component c) { installButtonListeners(c, nextButtonHandler); }

    protected void installPreviousButtonListeners(Component c) { installButtonListeners(c, previousButtonHandler); }

    private void installButtonListeners(Component c, ArrowButtonHandler handler) {
        if (c instanceof JButton) ((JButton) c).addActionListener(handler);
        c.addMouseListener(handler);
    }

    protected LayoutManager createLayout() { return getHandler(); }

    protected PropertyChangeListener createPropertyChangeListener() { return getHandler(); }

    protected Component createPreviousButton() {
        Component c = createArrowButton(SwingConstants.SOUTH);
        c.setName("Spinner.previousButton");
        installPreviousButtonListeners(c);
        return c;
    }

    protected Component createNextButton() {
        Component c = createArrowButton(SwingConstants.NORTH);
        c.setName("Spinner.nextButton");
        installNextButtonListeners(c);
        return c;
    }

    private Component createArrowButton(int direction) {
        JButton b = new BasicArrowButton(direction);
        Border buttonBorder = UIManager.getBorder("Spinner.arrowButtonBorder");
        if (buttonBorder instanceof UIResource) {
            // A shared UIResource border would be uninstalled with the button's UI; wrap it.
            b.setBorder(new CompoundBorder(buttonBorder, null));
        } else if (buttonBorder != null) {
            b.setBorder(buttonBorder);
        }
        b.setInheritsPopupMenu(true);
        return b;
    }

    /** The spinner's own editor, installed as the "Editor" child. */
    protected JComponent createEditor() {
        JComponent editor = spinner.getEditor();
        maybeRemoveEditorBorder(editor);
        editor.setInheritsPopupMenu(true);
        updateEditorAlignment(editor);
        return editor;
    }

    /** Swaps the "Editor" child when the spinner's editor property changes. */
    protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
        if (oldEditor != null) spinner.remove(oldEditor);
        maybeRemoveEditorBorder(newEditor);
        newEditor.setInheritsPopupMenu(true);
        spinner.add(newEditor, "Editor");
    }

    private void updateEditorAlignment(JComponent editor) {
        if (editor instanceof JSpinner.DefaultEditor) {
            Object alignment = UIManager.get("Spinner.editorAlignment");
            if (alignment instanceof Integer) {
                ((JSpinner.DefaultEditor) editor).getTextField().setHorizontalAlignment(((Integer) alignment).intValue());
            }
        }
    }

    /** The JDK drops the editor's UIResource border when the look and feel asks for it. */
    private void maybeRemoveEditorBorder(JComponent c) {
        if (!UIManager.getBoolean("Spinner.editorBorderPainted")) {
            if (c instanceof JSpinner.DefaultEditor && c.getBorder() instanceof UIResource) c.setBorder(null);
        }
    }

    private void updateEnabledState() {
        updateEnabledState(spinner, spinner.isEnabled());
    }

    private void updateEnabledState(Container c, boolean enabled) {
        for (int counter = c.getComponentCount() - 1; counter >= 0; counter--) {
            Component child = c.getComponent(counter);
            if (UIManager.getBoolean("Spinner.disableOnBoundaryValues")) {
                Object name = child.getName();
                if ("Spinner.nextButton".equals(name)) {
                    child.setEnabled(enabled && spinner.getNextValue() != null);
                    continue;
                } else if ("Spinner.previousButton".equals(name)) {
                    child.setEnabled(enabled && spinner.getPreviousValue() != null);
                    continue;
                }
            }
            child.setEnabled(enabled);
            if (child instanceof Container) updateEnabledState((Container) child, enabled);
        }
    }

    /**
     * The JDK binds the arrow keys through an InputMap; key focus on the spinner is the editor's
     * field here, which steps on its own key bindings, so there is nothing to register.
     */
    protected void installKeyboardActions() {}

    /** Press-and-hold stepping: one step on the button's action, then repeats while it stays pressed. */
    private final class ArrowButtonHandler extends MouseAdapter implements ActionListener {
        private final boolean isNext;
        private final Timer autoRepeatTimer;
        private JButton arrowButton;

        ArrowButtonHandler(boolean isNext) {
            this.isNext = isNext;
            autoRepeatTimer = new Timer(60, this);
            autoRepeatTimer.setInitialDelay(300);
        }

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == autoRepeatTimer) {
                if (arrowButton == null || !arrowButton.getModel().isPressed()) {
                    autoRepeatTimer.stop();
                    arrowButton = null;
                    return;
                }
            }
            JSpinner target = spinner;
            if (target == null || !target.isEnabled()) return;
            try {
                target.commitEdit();
                Object value = isNext ? target.getNextValue() : target.getPreviousValue();
                if (value != null) target.setValue(value);
            } catch (IllegalArgumentException | ParseException rejected) {
                UIManager.getLookAndFeel().provideErrorFeedback(target);
            }
        }

        @Override public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && e.getComponent().isEnabled() && e.getSource() instanceof JButton) {
                arrowButton = (JButton) e.getSource();
                autoRepeatTimer.start();
            }
        }

        @Override public void mouseReleased(MouseEvent e) {
            autoRepeatTimer.stop();
            arrowButton = null;
        }
    }

    /** The layout, the "editor"/"enabled" listener and the boundary-value listener in one, as in the JDK. */
    private class Handler implements LayoutManager, PropertyChangeListener, ChangeListener {
        private Component nextButton = null;
        private Component previousButton = null;
        private Component editor = null;

        public void addLayoutComponent(String name, Component c) {
            if ("Next".equals(name)) {
                nextButton = c;
            } else if ("Previous".equals(name)) {
                previousButton = c;
            } else if ("Editor".equals(name)) {
                editor = c;
            }
        }

        public void removeLayoutComponent(Component c) {
            if (c == nextButton) {
                nextButton = null;
            } else if (c == previousButton) {
                previousButton = null;
            } else if (c == editor) {
                editor = null;
            }
        }

        private Dimension preferredSize(Component c) {
            return c == null ? zeroSize : c.getPreferredSize();
        }

        private int arrowWidth() {
            return Math.max(MIN_ARROW_WIDTH, Math.max(preferredSize(nextButton).width, preferredSize(previousButton).width));
        }

        public Dimension preferredLayoutSize(Container parent) {
            Dimension editorD = preferredSize(editor);
            // Force the editor's height to be a multiple of 2, so the arrows split it evenly.
            editorD.height = ((editorD.height + 1) / 2) * 2;
            Dimension size = new Dimension(editorD.width, editorD.height);
            size.width += arrowWidth();
            Insets insets = parent.getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }

        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        private void setBounds(Component c, int x, int y, int width, int height) {
            if (c != null) c.setBounds(x, y, width, height);
        }

        public void layoutContainer(Container parent) {
            int width = parent.getWidth();
            int height = parent.getHeight();
            Insets insets = parent.getInsets();
            if (nextButton == null && previousButton == null) {
                setBounds(editor, insets.left, insets.top, width - insets.left - insets.right,
                        height - insets.top - insets.bottom);
                return;
            }
            int buttonsWidth = arrowWidth();
            int editorHeight = height - (insets.top + insets.bottom);
            Insets buttonInsets = UIManager.getInsets("Spinner.arrowButtonInsets");
            if (buttonInsets == null) buttonInsets = insets;
            int editorX, editorWidth, buttonsX;
            if (parent.getComponentOrientation().isLeftToRight()) {
                editorX = insets.left;
                editorWidth = width - insets.left - buttonsWidth - buttonInsets.right;
                buttonsX = width - buttonsWidth - buttonInsets.right;
            } else {
                buttonsX = buttonInsets.left;
                editorX = buttonsX + buttonsWidth;
                editorWidth = width - buttonInsets.left - buttonsWidth - insets.right;
            }
            int nextY = buttonInsets.top;
            int nextHeight = (height / 2) + (height % 2) - nextY;
            int previousY = buttonInsets.top + nextHeight;
            int previousHeight = height - previousY - buttonInsets.bottom;
            setBounds(editor, editorX, insets.top, editorWidth, editorHeight);
            setBounds(nextButton, buttonsX, nextY, buttonsWidth, nextHeight);
            setBounds(previousButton, buttonsX, previousY, buttonsWidth, previousHeight);
        }

        public void propertyChange(PropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if (!(e.getSource() instanceof JSpinner) || spinner == null) return;
            if ("editor".equals(propertyName)) {
                JComponent oldEditor = (JComponent) e.getOldValue();
                JComponent newEditor = (JComponent) e.getNewValue();
                replaceEditor(oldEditor, newEditor);
                updateEnabledState();
            } else if ("enabled".equals(propertyName) || "model".equals(propertyName)) {
                updateEnabledState();
            } else if ("componentOrientation".equals(propertyName)) {
                spinner.revalidate();
            }
        }

        public void stateChanged(ChangeEvent e) {
            if (e.getSource() instanceof JSpinner && spinner != null) updateEnabledState();
        }
    }
}
