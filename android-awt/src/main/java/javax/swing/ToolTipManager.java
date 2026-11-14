/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;

/**
 * Manages all the <code>ToolTips</code> in the system.
 * <p>
 * ToolTipManager contains numerous properties for configuring how long it
 * will take for the tooltips to become visible, and how long till they
 * hide. Consider a component that has a different tooltip based on where
 * the mouse is, such as JTree. When the mouse moves into the JTree and
 * over a region that has a valid tooltip, the tooltip will become
 * visible after <code>initialDelay</code> milliseconds. After
 * <code>dismissDelay</code> milliseconds the tooltip will be hidden. If
 * the mouse is over a region that has a valid tooltip, and the tooltip
 * is currently visible, when the mouse moves to a region that doesn't have
 * a valid tooltip the tooltip will be hidden. If the mouse then moves back
 * into a region that has a valid tooltip within <code>reshowDelay</code>
 * milliseconds, the tooltip will immediately be shown, otherwise the
 * tooltip will be shown again after <code>initialDelay</code> milliseconds.
 *
 * <p>
 * Ported from OpenJDK 8u. There is one manager per process instead of per
 * AppContext. The tip is not clamped to a screen's bounds here: a sidebar
 * tip is a heavy weight popup and the panel host places it on the display.
 * A finger cannot hover, so the host shows a tip on a long press through
 * {@link #hostShowToolTip} and a tap dismisses it through
 * {@link #hostHideToolTip}. The keyboard toggle (ctrl+F1) is not bound.
 *
 * @see JComponent#createToolTip
 * @author Dave Moore
 * @author Rich Schiavi
 */
public class ToolTipManager extends MouseAdapter implements MouseMotionListener  {
    Timer enterTimer, exitTimer, insideTimer;
    String toolTipText;
    Point  preferredLocation;
    JComponent insideComponent;
    MouseEvent mouseEvent;
    boolean showImmediately;
    private static ToolTipManager sharedInstance;
    transient Popup tipWindow;
    JToolTip tip;

    boolean enabled = true;
    private boolean tipShowing = false;

    private MouseMotionListener moveBeforeEnterListener = null;

    // PENDING(ges)
    protected boolean lightWeightPopupEnabled = true;
    protected boolean heavyWeightPopupEnabled = false;

    ToolTipManager() {
        enterTimer = new Timer(750, new insideTimerAction());
        enterTimer.setRepeats(false);
        exitTimer = new Timer(500, new outsideTimerAction());
        exitTimer.setRepeats(false);
        insideTimer = new Timer(4000, new stillInsideTimerAction());
        insideTimer.setRepeats(false);

        moveBeforeEnterListener = new MoveBeforeEnterListener();
    }

    /**
     * Enables or disables the tooltip.
     *
     * @param flag  true to enable the tip, false otherwise
     */
    public void setEnabled(boolean flag) {
        enabled = flag;
        if (!flag) {
            hideTipWindow();
        }
    }

    /**
     * Returns true if this object is enabled.
     *
     * @return true if this object is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * When displaying the <code>JToolTip</code>, the
     * <code>ToolTipManager</code> chooses to use a lightweight
     * <code>JPanel</code> if it fits. This method allows you to
     * disable this feature. You have to do disable it if your
     * application mixes light weight and heavy weights components.
     *
     * @param aFlag true if a lightweight panel is desired, false otherwise
     *
     */
    public void setLightWeightPopupEnabled(boolean aFlag){
        lightWeightPopupEnabled = aFlag;
    }

    /**
     * Returns true if lightweight (all-Java) <code>Tooltips</code>
     * are in use, or false if heavyweight (native peer)
     * <code>Tooltips</code> are being used.
     *
     * @return true if lightweight <code>ToolTips</code> are in use
     */
    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopupEnabled;
    }


    /**
     * Specifies the initial delay value.
     *
     * @param milliseconds  the number of milliseconds to delay
     *        (after the cursor has paused) before displaying the
     *        tooltip
     * @see #getInitialDelay
     */
    public void setInitialDelay(int milliseconds) {
        enterTimer.setInitialDelay(milliseconds);
    }

    /**
     * Returns the initial delay value.
     *
     * @return an integer representing the initial delay value,
     *          in milliseconds
     * @see #setInitialDelay
     */
    public int getInitialDelay() {
        return enterTimer.getInitialDelay();
    }

    /**
     * Specifies the dismissal delay value.
     *
     * @param milliseconds  the number of milliseconds to delay
     *        before taking away the tooltip
     * @see #getDismissDelay
     */
    public void setDismissDelay(int milliseconds) {
        insideTimer.setInitialDelay(milliseconds);
    }

    /**
     * Returns the dismissal delay value.
     *
     * @return an integer representing the dismissal delay value,
     *          in milliseconds
     * @see #setDismissDelay
     */
    public int getDismissDelay() {
        return insideTimer.getInitialDelay();
    }

    /**
     * Used to specify the amount of time before the user has to wait
     * <code>initialDelay</code> milliseconds before a tooltip will be
     * shown. That is, if the tooltip is hidden, and the user moves into
     * a region of the same Component that has a valid tooltip within
     * <code>milliseconds</code> milliseconds the tooltip will immediately
     * be shown. Otherwise, if the user moves into a region with a valid
     * tooltip after <code>milliseconds</code> milliseconds, the user
     * will have to wait an additional <code>initialDelay</code>
     * milliseconds before the tooltip is shown again.
     *
     * @param milliseconds time in milliseconds
     * @see #getReshowDelay
     */
    public void setReshowDelay(int milliseconds) {
        exitTimer.setInitialDelay(milliseconds);
    }

    /**
     * Returns the reshow delay property.
     *
     * @return reshown delay property
     * @see #setReshowDelay
     */
    public int getReshowDelay() {
        return exitTimer.getInitialDelay();
    }

    void showTipWindow() {
        if(insideComponent == null || !insideComponent.isShowing())
            return;
        if (enabled) {
            Point screenLocation = insideComponent.getLocationOnScreen();
            Point location;

            // Just to be paranoid
            hideTipWindow();

            tip = insideComponent.createToolTip();
            tip.setTipText(toolTipText);

            if (preferredLocation != null) {
                location = new Point(screenLocation.x + preferredLocation.x,
                                     screenLocation.y + preferredLocation.y);
            } else {
                location = new Point(screenLocation.x + mouseEvent.getX(),
                                     screenLocation.y + mouseEvent.getY() + 20);
                // The shadow has no ComponentOrientation; every component is left to right.
            }

            PopupFactory popupFactory = PopupFactory.getSharedInstance();

            popupFactory.setPopupType(lightWeightPopupEnabled
                    ? PopupFactory.LIGHT_WEIGHT_POPUP : PopupFactory.MEDIUM_WEIGHT_POPUP);
            tipWindow = popupFactory.getPopup(insideComponent, tip,
                                              location.x,
                                              location.y);
            popupFactory.setPopupType(PopupFactory.LIGHT_WEIGHT_POPUP);

            tipWindow.show();
            tipShowing = true;
            insideTimer.start();
        }
    }

    void hideTipWindow() {
        if (tipWindow != null) {
            tipWindow.hide();
            tipWindow = null;
            tipShowing = false;
            tip = null;
            insideTimer.stop();
        }
    }

    /**
     * Returns a shared <code>ToolTipManager</code> instance.
     *
     * @return a shared <code>ToolTipManager</code> object
     */
    public static ToolTipManager sharedInstance() {
        synchronized (ToolTipManager.class) {
            if (sharedInstance == null) {
                sharedInstance = new ToolTipManager();
            }
            return sharedInstance;
        }
    }

    /**
     * Registers a component for tooltip management.
     * <p>
     * This will register key bindings to show and hide the tooltip text
     * only if <code>component</code> has focus bindings. This is done
     * so that components that are not normally focus traversable, such
     * as <code>JLabel</code>, are not made focus traversable as a result
     * of invoking this method.
     *
     * @param component  a <code>JComponent</code> object to add
     * @see JComponent#isFocusTraversable
     */
    public void registerComponent(JComponent component) {
        component.removeMouseListener(this);
        component.addMouseListener(this);
        component.removeMouseMotionListener(moveBeforeEnterListener);
        component.addMouseMotionListener(moveBeforeEnterListener);
    }

    /**
     * Removes a component from tooltip control.
     *
     * @param component  a <code>JComponent</code> object to remove
     */
    public void unregisterComponent(JComponent component) {
        component.removeMouseListener(this);
        component.removeMouseMotionListener(moveBeforeEnterListener);
    }

    /**
     * Host hook (not JDK API): whether {@link #registerComponent} holds
     * this component. The panel host treats such a component as a mouse
     * target even when no plugin listens on it.
     *
     * @param component the component to test
     * @return whether the component is registered
     */
    public boolean isRegistered(JComponent component) {
        if (component == null) {
            return false;
        }
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener == this) {
                return true;
            }
        }
        return false;
    }

    /**
     * Host hook (not JDK API): shows the tip of {@code component} for a
     * point in its coordinates at once, as a touch long press does; it is
     * dismissed after the dismiss delay or by {@link #hostHideToolTip}.
     * Call on the event dispatch thread.
     *
     * @param component the component under the finger
     * @param x the x coordinate in {@code component}
     * @param y the y coordinate in {@code component}
     * @return whether a tip is showing
     */
    public boolean hostShowToolTip(JComponent component, int x, int y) {
        hostHideToolTip();
        if (!enabled || component == null) {
            return false;
        }
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false);
        String text = component.getToolTipText(event);
        if (text == null || text.isEmpty()) {
            return false;
        }
        exitTimer.stop();
        insideComponent = component;
        mouseEvent = event;
        toolTipText = text;
        preferredLocation = component.getToolTipLocation(event);
        showTipWindow();
        return tipShowing;
    }

    /**
     * Host hook (not JDK API): hides the showing tip, as a tap does, and
     * forgets the component it belonged to. Call on the event dispatch
     * thread.
     */
    public void hostHideToolTip() {
        hideTipWindow();
        enterTimer.stop();
        showImmediately = false;
        insideComponent = null;
        toolTipText = null;
        preferredLocation = null;
        mouseEvent = null;
    }

    // implements java.awt.event.MouseListener
    /**
     *  Called when the mouse enters the region of a component.
     *  This determines whether the tool tip should be shown.
     *
     *  @param event  the event in question
     */
    public void mouseEntered(MouseEvent event) {
        initiateToolTip(event);
    }

    private void initiateToolTip(MouseEvent event) {
        JComponent component = (JComponent)event.getSource();
        component.removeMouseMotionListener(moveBeforeEnterListener);

        exitTimer.stop();

        Point location = event.getPoint();
        // ensure tooltip shows only in proper place
        if (location.x < 0 ||
            location.x >=component.getWidth() ||
            location.y < 0 ||
            location.y >= component.getHeight()) {
            return;
        }

        if (insideComponent != null) {
            enterTimer.stop();
        }
        // A component in an unactive internal frame is sent two
        // mouseEntered events, make sure we don't end up adding
        // ourselves an extra time.
        component.removeMouseMotionListener(this);
        component.addMouseMotionListener(this);

        boolean sameComponent = (insideComponent == component);

        insideComponent = component;
        if (tipWindow != null){
            mouseEvent = event;
            if (showImmediately) {
                String newToolTipText = component.getToolTipText(event);
                Point newPreferredLocation = component.getToolTipLocation(
                                                         event);
                boolean sameLoc = (preferredLocation != null) ?
                            preferredLocation.equals(newPreferredLocation) :
                            (newPreferredLocation == null);

                if (!sameComponent || toolTipText == null
                        || !toolTipText.equals(newToolTipText) || !sameLoc) {
                    toolTipText = newToolTipText;
                    preferredLocation = newPreferredLocation;
                    showTipWindow();
                }
            } else {
                enterTimer.start();
            }
        }
    }

    // implements java.awt.event.MouseListener
    /**
     *  Called when the mouse exits the region of a component.
     *  Any tool tip showing should be hidden.
     *
     *  @param event  the event in question
     */
    public void mouseExited(MouseEvent event) {
        // The JDK keeps a tip whose window the pointer moved into; a host
        // overlay takes no pointer, so leaving the component always hides it.
        enterTimer.stop();
        if (insideComponent != null) {
            insideComponent.removeMouseMotionListener(this);
        }
        insideComponent = null;
        toolTipText = null;
        mouseEvent = null;
        hideTipWindow();
        exitTimer.restart();
    }

    // implements java.awt.event.MouseListener
    /**
     *  Called when the mouse is pressed.
     *  Any tool tip showing should be hidden.
     *
     *  @param event  the event in question
     */
    public void mousePressed(MouseEvent event) {
        hideTipWindow();
        enterTimer.stop();
        showImmediately = false;
        insideComponent = null;
        mouseEvent = null;
    }

    // implements java.awt.event.MouseMotionListener
    /**
     *  Called when the mouse is pressed and dragged.
     *  Does nothing.
     *
     *  @param event  the event in question
     */
    public void mouseDragged(MouseEvent event) {
    }

    // implements java.awt.event.MouseMotionListener
    /**
     *  Called when the mouse is moved.
     *  Determines whether the tool tip should be displayed.
     *
     *  @param event  the event in question
     */
    public void mouseMoved(MouseEvent event) {
        if (tipShowing) {
            checkForTipChange(event);
        }
        else if (showImmediately) {
            JComponent component = (JComponent)event.getSource();
            toolTipText = component.getToolTipText(event);
            if (toolTipText != null) {
                preferredLocation = component.getToolTipLocation(event);
                mouseEvent = event;
                insideComponent = component;
                exitTimer.stop();
                showTipWindow();
            }
        }
        else {
            // Lazily lookup the values from within insideTimerAction
            insideComponent = (JComponent)event.getSource();
            mouseEvent = event;
            toolTipText = null;
            enterTimer.restart();
        }
    }

    /**
     * Checks to see if the tooltip needs to be changed in response to
     * the MouseMoved event <code>event</code>.
     */
    private void checkForTipChange(MouseEvent event) {
        JComponent component = (JComponent)event.getSource();
        String newText = component.getToolTipText(event);
        Point  newPreferredLocation = component.getToolTipLocation(event);

        if (newText != null || newPreferredLocation != null) {
            mouseEvent = event;
            if (((newText != null && newText.equals(toolTipText)) || newText == null) &&
                ((newPreferredLocation != null && newPreferredLocation.equals(preferredLocation))
                 || newPreferredLocation == null)) {
                if (tipWindow != null) {
                    insideTimer.restart();
                } else {
                    enterTimer.restart();
                }
            } else {
                toolTipText = newText;
                preferredLocation = newPreferredLocation;
                if (showImmediately) {
                    hideTipWindow();
                    showTipWindow();
                    exitTimer.stop();
                } else {
                    enterTimer.restart();
                }
            }
        } else {
            toolTipText = null;
            preferredLocation = null;
            mouseEvent = null;
            insideComponent = null;
            hideTipWindow();
            enterTimer.stop();
            exitTimer.restart();
        }
    }

    protected class insideTimerAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if(insideComponent != null && insideComponent.isShowing()) {
                // Lazy lookup
                if (toolTipText == null && mouseEvent != null) {
                    toolTipText = insideComponent.getToolTipText(mouseEvent);
                    preferredLocation = insideComponent.getToolTipLocation(
                                              mouseEvent);
                }
                if(toolTipText != null) {
                    showImmediately = true;
                    showTipWindow();
                }
                else {
                    insideComponent = null;
                    toolTipText = null;
                    preferredLocation = null;
                    mouseEvent = null;
                    hideTipWindow();
                }
            }
        }
    }

    protected class outsideTimerAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            showImmediately = false;
        }
    }

    protected class stillInsideTimerAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            hideTipWindow();
            enterTimer.stop();
            showImmediately = false;
            insideComponent = null;
            mouseEvent = null;
        }
    }

  /* This listener is registered when the tooltip is first registered
   * on a component in order to catch the situation where the tooltip
   * was turned on while the mouse was already within the bounds of
   * the component.  This way, the tooltip will be initiated on a
   * mouse-entered or mouse-moved, whichever occurs first.  Once the
   * tooltip has been initiated, we can remove this listener and rely
   * solely on mouse-entered to initiate the tooltip.
   */
    private class MoveBeforeEnterListener extends MouseMotionAdapter {
        public void mouseMoved(MouseEvent e) {
            initiateToolTip(e);
        }
    }
}
