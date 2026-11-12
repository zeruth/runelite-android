/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * This class creates a labeled button. The application can cause some action
 * to happen when the button is pushed.
 * <p>
 * When the user releases the mouse over a pressed button (or the space bar
 * while it has focus), the button sends an instance of <code>ActionEvent</code>
 * to its listeners, after the mouse listeners have seen the gesture. There is
 * no native peer on Android: the button measures and paints itself, and the
 * constructors do not throw <code>HeadlessException</code>.
 *
 * @author      Sami Shaio
 * @see         java.awt.event.ActionEvent
 * @see         java.awt.event.ActionListener
 * @see         java.awt.Component#processMouseEvent
 * @see         java.awt.Component#addMouseListener
 * @since       JDK1.0
 */
public class Button extends Component {

    String label;

    /*
     * The action to be performed once a button has been
     * pressed.  This value may be null.
     * @serial
     * @see #getActionCommand()
     * @see #setActionCommand(String)
     */
    String actionCommand;

    transient ActionListener actionListener;

    private static final String base = "button";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -8774683716313001058L;

    /** Set between a primary press inside the button and its release; the pressed look. */
    private transient boolean armed;

    /**
     * Constructs a button with an empty string for its label.
     */
    public Button() {
        this("");
    }

    /**
     * Constructs a button with the specified label.
     *
     * @param label  a string label for the button, or
     *               <code>null</code> for no label
     */
    public Button(String label) {
        this.label = label;
        // The events the peer used to turn into actions; on the JDK they also make the
        // component's dispatch deliver them to processEvent without a listener.
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);
    }

    /**
     * Construct a name for this component.  Called by getName() when the
     * name is null.
     */
    String constructComponentName() {
        synchronized (Button.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Gets the label of this button.
     *
     * @return    the button's label, or <code>null</code>
     *                if the button has no label.
     * @see       java.awt.Button#setLabel
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the button's label to be the specified string.
     *
     * @param     label   the new label, or <code>null</code>
     *                if the button has no label.
     * @see       java.awt.Button#getLabel
     */
    public void setLabel(String label) {
        boolean testvalid = false;

        synchronized (this) {
            if (label != this.label && (this.label == null ||
                                        !this.label.equals(label))) {
                this.label = label;
                testvalid = true;
            }
        }

        // This could change the preferred size of the Component.
        if (testvalid) {
            invalidate();
        }
    }

    /**
     * Sets the command name for the action event fired
     * by this button. By default this action command is
     * set to match the label of the button.
     *
     * @param     command  a string used to set the button's
     *                  action command.
     *            If the string is <code>null</code> then the action command
     *            is set to match the label of the button.
     * @see       java.awt.event.ActionEvent
     * @since     JDK1.1
     */
    public void setActionCommand(String command) {
        actionCommand = command;
    }

    /**
     * Returns the command name of the action event fired by this button.
     * If the command name is <code>null</code> (default) then this method
     * returns the label of the button.
     */
    public String getActionCommand() {
        return (actionCommand == null? label : actionCommand);
    }

    /**
     * Adds the specified action listener to receive action events from
     * this button. Action events occur when a user presses or releases
     * the mouse over this button.
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param         l the action listener
     * @see           #removeActionListener
     * @see           #getActionListeners
     * @see           java.awt.event.ActionListener
     * @since         JDK1.1
     */
    public synchronized void addActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.add(actionListener, l);
    }

    /**
     * Removes the specified action listener so that it no longer
     * receives action events from this button. Action events occur
     * when a user presses or releases the mouse over this button.
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param           l     the action listener
     * @see             #addActionListener
     * @see             #getActionListeners
     * @see             java.awt.event.ActionListener
     * @since           JDK1.1
     */
    public synchronized void removeActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.remove(actionListener, l);
    }

    /**
     * Returns an array of all the action listeners
     * registered on this button.
     *
     * @return all of this button's <code>ActionListener</code>s
     *         or an empty array if no action
     *         listeners are currently registered
     *
     * @see             #addActionListener
     * @see             #removeActionListener
     * @see             java.awt.event.ActionListener
     * @since 1.4
     */
    public synchronized ActionListener[] getActionListeners() {
        return getListeners(ActionListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>Button</code>.
     * <code><em>Foo</em>Listener</code>s are registered using the
     * <code>add<em>Foo</em>Listener</code> method.
     *
     * @param listenerType the type of listeners requested; this parameter
     *          should specify an interface that descends from
     *          <code>java.util.EventListener</code>
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this button,
     *          or an empty array if no such
     *          listeners have been added
     * @exception ClassCastException if <code>listenerType</code>
     *          doesn't specify a class or interface that implements
     *          <code>java.util.EventListener</code>
     *
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ActionListener.class) {
            return ListenerChain.toArray(actionListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this button. If an event is
     * an instance of <code>ActionEvent</code>, this method invokes
     * the <code>processActionEvent</code> method. Otherwise,
     * it invokes <code>processEvent</code> on the superclass.
     *
     * @param        e the event
     * @see          java.awt.event.ActionEvent
     * @see          java.awt.Button#processActionEvent
     * @since        JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof ActionEvent) {
            processActionEvent((ActionEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes action events occurring on this button
     * by dispatching them to any registered
     * <code>ActionListener</code> objects.
     *
     * @param       e the action event
     * @see         java.awt.event.ActionListener
     * @see         java.awt.Button#addActionListener
     * @see         java.awt.Component#enableEvents
     * @since       JDK1.1
     */
    protected void processActionEvent(ActionEvent e) {
        ActionListener listener = actionListener;
        if (listener != null) {
            listener.actionPerformed(e);
        }
    }

    /** The peer's part: a primary press arms the button, its release inside fires the action. */
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getButton() != MouseEvent.BUTTON1 && e.getID() != MouseEvent.MOUSE_EXITED) return;
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                if (isEnabled()) {
                    armed = true;
                    repaint();
                }
                break;
            case MouseEvent.MOUSE_RELEASED:
                boolean fire = armed && isEnabled() && e.getX() >= 0 && e.getY() >= 0
                    && e.getX() < getWidth() && e.getY() < getHeight();
                armed = false;
                repaint();
                if (fire) fireAction(e.getWhen(), AwtWidgets.actionModifiers(e));
                break;
            case MouseEvent.MOUSE_EXITED:
                if (armed) {
                    armed = false;
                    repaint();
                }
                break;
        }
    }

    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (e.isConsumed() || e.getKeyCode() != KeyEvent.VK_SPACE || !isEnabled()) return;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            armed = true;
            repaint();
        } else if (e.getID() == KeyEvent.KEY_RELEASED && armed) {
            armed = false;
            repaint();
            fireAction(e.getWhen(), AwtWidgets.actionModifiers(e));
        }
    }

    private void fireAction(long when, int modifiers) {
        dispatchEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(), when, modifiers));
    }

    /** The peer's size: the label width plus 14 and the line height plus 8. */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = AwtWidgets.metrics(this);
        return new Dimension(fm.stringWidth(label == null ? "" : label) + 14, fm.getHeight() + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        Color face = AwtWidgets.face(this);
        g.setColor(armed ? AwtWidgets.shade(face, -0.1f) : face);
        g.fillRect(0, 0, w, h);
        AwtWidgets.bevel(g, face, 0, 0, w, h, !armed);
        if (label == null || label.isEmpty()) return;
        g.setFont(AwtWidgets.font(this));
        FontMetrics fm = g.getFontMetrics();
        int shift = armed ? 1 : 0;
        g.setColor(AwtWidgets.foreground(this));
        AwtWidgets.text(g, fm, label, (w - fm.stringWidth(label)) / 2 + shift, shift, h);
    }

    /**
     * Returns a string representing the state of this <code>Button</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return     the parameter string of this button
     */
    protected String paramString() {
        return super.paramString() + ",label=" + label;
    }
}
