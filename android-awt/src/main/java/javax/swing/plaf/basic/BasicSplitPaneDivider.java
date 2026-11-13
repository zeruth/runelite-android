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

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.border.Border;

/**
 * The divider a {@link BasicSplitPaneUI} adds to its pane.  A press anywhere on it starts a drag that
 * follows the finger (the pane relayouts as it moves, clamped to the JDK's minimum/maximum divider
 * locations); with {@code oneTouchExpandable} it carries the two collapse buttons, each 20px along
 * the divider so a finger can hit them.
 */
public class BasicSplitPaneDivider extends Container implements PropertyChangeListener {
    private static final long serialVersionUID = 1L;
    protected static final int ONE_TOUCH_SIZE = 6;
    protected static final int ONE_TOUCH_OFFSET = 2;
    /** The touch length of a one-touch button along the divider. */
    private static final int ONE_TOUCH_LENGTH = 20;

    protected BasicSplitPaneUI splitPaneUI;
    protected int dividerSize;
    protected JSplitPane splitPane;
    protected int orientation;
    protected JButton leftButton;
    protected JButton rightButton;

    private Border border;
    private boolean mouseOver;
    private boolean dragging;
    private int pressOffset;

    public BasicSplitPaneDivider(BasicSplitPaneUI ui) {
        setBasicSplitPaneUI(ui);
        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (splitPane == null || !splitPane.isEnabled() || e.getButton() != MouseEvent.BUTTON1) return;
                dragging = true;
                pressOffset = horizontal() ? e.getX() : e.getY();
                if (splitPaneUI != null) splitPaneUI.beginDragDividerLocation = splitPane.getDividerLocation();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging || splitPane == null) return;
                boolean horizontal = horizontal();
                int absolute = (horizontal ? getX() + e.getX() : getY() + e.getY()) - pressOffset;
                if (splitPaneUI != null) {
                    absolute = Math.max(splitPaneUI.getMinimumDividerLocation(splitPane),
                            Math.min(splitPaneUI.getMaximumDividerLocation(splitPane), absolute));
                }
                // The JDK's location is in pane coordinates, the leading inset included.
                splitPane.setDividerLocation(absolute);
            }
            @Override public void mouseReleased(MouseEvent e) { dragging = false; }
            @Override public void mouseEntered(MouseEvent e) { mouseOver = true; }
            @Override public void mouseExited(MouseEvent e) { mouseOver = false; }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private boolean horizontal() { return orientation == JSplitPane.HORIZONTAL_SPLIT; }

    public void setBasicSplitPaneUI(BasicSplitPaneUI newUI) {
        if (splitPane != null) splitPane.removePropertyChangeListener(this);
        splitPaneUI = newUI;
        splitPane = newUI == null ? null : newUI.getSplitPane();
        if (splitPane != null) {
            splitPane.addPropertyChangeListener(this);
            orientation = splitPane.getOrientation();
        }
        oneTouchExpandableChanged();
    }

    public BasicSplitPaneUI getBasicSplitPaneUI() { return splitPaneUI; }

    public void setDividerSize(int newSize) { dividerSize = newSize; }

    public int getDividerSize() { return dividerSize; }

    public void setBorder(Border border) { this.border = border; }

    public Border getBorder() { return border; }

    @Override public Insets getInsets() {
        return border != null && splitPane != null ? border.getBorderInsets(this) : super.getInsets();
    }

    protected void setMouseOver(boolean mouseOver) { this.mouseOver = mouseOver; }

    public boolean isMouseOver() { return mouseOver; }

    @Override public void propertyChange(PropertyChangeEvent e) {
        if (e.getSource() == splitPane && JSplitPane.ORIENTATION_PROPERTY.equals(e.getPropertyName())) {
            orientation = splitPane.getOrientation();
            invalidate();
            validate();
        }
    }

    /** Adds or removes the collapse buttons to follow the pane's {@code oneTouchExpandable}. */
    protected void oneTouchExpandableChanged() {
        boolean wanted = splitPane != null && splitPane.isOneTouchExpandable();
        if (wanted && leftButton == null) {
            leftButton = createLeftOneTouchButton();
            rightButton = createRightOneTouchButton();
            add(leftButton);
            add(rightButton);
        } else if (!wanted && leftButton != null) {
            remove(leftButton);
            remove(rightButton);
            leftButton = rightButton = null;
        }
        doLayout();
        repaint();
    }

    protected JButton createLeftOneTouchButton() { return oneTouchButton(true); }

    protected JButton createRightOneTouchButton() { return oneTouchButton(false); }

    /**
     * The JDK's one-touch rule: collapse towards the button's side, or, when the divider already sits
     * on the far side, return to the last location.
     */
    private JButton oneTouchButton(final boolean towardsLeft) {
        JButton button = new JButton() {
            private static final long serialVersionUID = 1L;
            @Override public void paint(Graphics g) {
                g.setColor(Color.DARK_GRAY);
                int w = getWidth(), h = getHeight();
                boolean horizontal = horizontal();
                int along = (horizontal ? h : w) / 2;
                int tip = towardsLeft ? 1 : Math.max(1, (horizontal ? w : h) - 2);
                int back = towardsLeft ? Math.max(1, (horizontal ? w : h) - 2) : 1;
                if (horizontal) {
                    g.drawLine(back, along - 4, tip, along);
                    g.drawLine(tip, along, back, along + 4);
                } else {
                    g.drawLine(along - 4, back, along, tip);
                    g.drawLine(along, tip, along + 4, back);
                }
            }
        };
        button.setBorder(null);
        button.setFocusPainted(false);
        button.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (splitPane == null || splitPaneUI == null) return;
                Insets in = splitPane.getInsets();
                int lead = horizontal() ? in.left : in.top;
                int location = splitPane.getDividerLocation();
                int max = splitPaneUI.getMaximumDividerLocation(splitPane);
                int min = splitPaneUI.getMinimumDividerLocation(splitPane);
                int target;
                if (towardsLeft) {
                    target = location >= max ? splitPane.getLastDividerLocation() : lead;
                } else {
                    target = location <= min || location <= lead ? splitPane.getLastDividerLocation()
                            : (horizontal() ? splitPane.getWidth() - in.right : splitPane.getHeight() - in.bottom)
                                - splitPane.getDividerSize();
                }
                splitPane.setDividerLocation(Math.max(lead, target));
            }
        });
        return button;
    }

    @Override public void doLayout() {
        if (leftButton == null) return;
        int thickness = Math.max(1, horizontal() ? getWidth() : getHeight());
        if (horizontal()) {
            leftButton.setBounds(0, ONE_TOUCH_OFFSET, thickness, ONE_TOUCH_LENGTH);
            rightButton.setBounds(0, ONE_TOUCH_OFFSET + ONE_TOUCH_LENGTH, thickness, ONE_TOUCH_LENGTH);
        } else {
            leftButton.setBounds(ONE_TOUCH_OFFSET, 0, ONE_TOUCH_LENGTH, thickness);
            rightButton.setBounds(ONE_TOUCH_OFFSET + ONE_TOUCH_LENGTH, 0, ONE_TOUCH_LENGTH, thickness);
        }
    }

    @Override public void paint(Graphics g) {
        Color background = splitPane != null ? splitPane.getBackground() : null;
        g.setColor(background != null ? background.darker() : Color.GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (border != null) border.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        super.paint(g);
    }
}
