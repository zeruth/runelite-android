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

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;

public class SwingUtilities implements SwingConstants {
    private SwingUtilities() {}

    public static boolean isEventDispatchThread() { return EventQueue.isDispatchThread(); }
    public static void invokeLater(Runnable doRun) { EventQueue.invokeLater(doRun); }
    public static void invokeAndWait(Runnable doRun) throws InterruptedException, java.lang.reflect.InvocationTargetException {
        EventQueue.invokeAndWait(doRun);
    }

    public static Window getWindowAncestor(Component c) {
        Container p = c == null ? null : c.getParent();
        while (p != null && !(p instanceof Window)) p = p.getParent();
        return (Window) p;
    }

    public static <T extends Container> T getAncestorOfClass(Class<T> c, Component comp) {
        if (comp == null || c == null) return null;
        Container p = comp.getParent();
        while (p != null && !c.isInstance(p)) p = p.getParent();
        @SuppressWarnings("unchecked")
        T result = (T) p;
        return result;
    }

    public static JRootPane getRootPane(Component c) { return getAncestorOfClass(JRootPane.class, c); }
    public static Container getRoot(Component c) {
        Container p = c instanceof Container ? (Container) c : (c == null ? null : c.getParent());
        while (p != null && !(p instanceof Window) && !(p instanceof java.applet.Applet)) p = p.getParent();
        return p;
    }

    public static boolean isLeftMouseButton(MouseEvent e) { return e.getButton() == MouseEvent.BUTTON1; }
    public static boolean isMiddleMouseButton(MouseEvent e) { return e.getButton() == MouseEvent.BUTTON2; }
    public static boolean isRightMouseButton(MouseEvent e) { return e.getButton() == MouseEvent.BUTTON3; }

    // Conversions go through Component.getLocationOnScreen, whose "screen" is the topmost
    // ancestor's space, so they agree with MouseEvent.getLocationOnScreen. A null end is
    // the other end's root, as the JDK falls back to the window ancestor.
    public static Point convertPoint(Component src, Point pt, Component dest) {
        return convertPoint(src, pt.x, pt.y, dest);
    }

    public static Point convertPoint(Component src, int x, int y, Component dest) {
        Point p = new Point(x, y);
        if (src == dest || (src == null && dest == null)) return p;
        convertPointToScreen(p, src != null ? src : topmost(dest));
        convertPointFromScreen(p, dest != null ? dest : topmost(src));
        return p;
    }

    public static Rectangle convertRectangle(Component src, Rectangle r, Component dest) {
        Point p = convertPoint(src, r.x, r.y, dest);
        return new Rectangle(p.x, p.y, r.width, r.height);
    }

    public static MouseEvent convertMouseEvent(Component src, MouseEvent ev, Component dest) {
        Point p = convertPoint(src, ev.getX(), ev.getY(), dest);
        Component source = dest != null ? dest : src;
        if (ev instanceof java.awt.event.MouseWheelEvent) {
            java.awt.event.MouseWheelEvent we = (java.awt.event.MouseWheelEvent) ev;
            return new java.awt.event.MouseWheelEvent(source, we.getID(), we.getWhen(), we.getModifiers(),
                p.x, p.y, we.getXOnScreen(), we.getYOnScreen(), we.getClickCount(), we.isPopupTrigger(),
                we.getScrollType(), we.getScrollAmount(), we.getWheelRotation(), we.getPreciseWheelRotation());
        }
        return new MouseEvent(source, ev.getID(), ev.getWhen(), ev.getModifiers(), p.x, p.y,
            ev.getXOnScreen(), ev.getYOnScreen(), ev.getClickCount(), ev.isPopupTrigger(), ev.getButton());
    }

    public static void convertPointFromScreen(Point p, Component c) {
        if (c == null) return;
        Point origin = c.getLocationOnScreen();
        p.x -= origin.x;
        p.y -= origin.y;
    }

    public static void convertPointToScreen(Point p, Component c) {
        if (c == null) return;
        Point origin = c.getLocationOnScreen();
        p.x += origin.x;
        p.y += origin.y;
    }

    private static Component topmost(Component c) {
        while (c != null && c.getParent() != null) c = c.getParent();
        return c;
    }

    public static Rectangle computeUnion(int x, int y, int w, int h, Rectangle dest) {
        if (dest == null) return new Rectangle(x, y, w, h);
        dest.add(new Rectangle(x, y, w, h));
        return dest;
    }
    public static Rectangle[] computeDifference(Rectangle rectA, Rectangle rectB) { return new Rectangle[0]; }
    public static Rectangle computeIntersection(int x, int y, int width, int height, Rectangle dest) {
        Rectangle r = dest == null ? new Rectangle() : dest;
        Rectangle other = new Rectangle(x, y, width, height);
        Rectangle intersect = r.intersection(other);
        r.setBounds(intersect.x, intersect.y, intersect.width, intersect.height);
        return r;
    }

    public static String layoutCompoundLabel(java.awt.FontMetrics fm, String text, Icon icon, int verticalAlignment,
                                              int horizontalAlignment, int verticalTextPosition, int horizontalTextPosition,
                                              Rectangle viewR, Rectangle iconR, Rectangle textR, int textIconGap) {
        return text == null ? "" : text;
    }

    // --- key bindings (OpenJDK 8u SwingUtilities) ---------------------------

    /**
     * Process the key bindings for the <code>Component</code> associated with
     * <code>event</code>. This method is only useful if
     * <code>event.getComponent()</code> does not descend from
     * <code>JComponent</code>, or your are not invoking
     * <code>super.processKeyEvent</code> from within your
     * <code>JComponent</code> subclass.
     *
     * @return true if a binding has found and processed
     */
    public static boolean processKeyBindings(java.awt.event.KeyEvent event) {
        if (event != null) {
            if (event.isConsumed()) {
                return false;
            }

            Component component = event.getComponent();
            boolean pressed = (event.getID() == java.awt.event.KeyEvent.KEY_PRESSED);

            if (!isValidKeyEventForKeyBindings(event)) {
                return false;
            }
            // Find the first JComponent in the ancestor hierarchy, and
            // invoke processKeyBindings on it
            while (component != null) {
                if (component instanceof JComponent) {
                    return ((JComponent) component).processKeyBindings(event, pressed);
                }
                if ((component instanceof java.applet.Applet) || (component instanceof Window)) {
                    // No JComponents, if Window or Applet parent, process
                    // WHEN_IN_FOCUSED_WINDOW bindings.
                    return JComponent.processKeyBindingsForAllComponents(event, (Container) component, pressed);
                }
                component = component.getParent();
            }
        }
        return false;
    }

    /**
     * Returns true if the <code>e</code> is a valid KeyEvent to use in
     * processing the key bindings associated with JComponents.
     */
    static boolean isValidKeyEventForKeyBindings(java.awt.event.KeyEvent e) {
        if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) {
            int mod = e.getModifiers();
            if (((mod & java.awt.event.ActionEvent.ALT_MASK) != 0)
                    && ((mod & java.awt.event.ActionEvent.CTRL_MASK) == 0)) {
                // filter out ALT-KEY_TYPED events
                return false;
            }
        }
        return true;
    }

    /**
     * Invokes <code>actionPerformed</code> on <code>action</code> if
     * <code>action</code> is enabled (and non-{@code null}). The command for the
     * ActionEvent is determined by the action's ACTION_COMMAND_KEY, then the
     * KeyEvent's character, except for registerKeyboardAction stand-ins.
     *
     * @return true if <code>action</code> is non-{@code null} and
     *         actionPerformed is invoked on it.
     */
    public static boolean notifyAction(Action action, KeyStroke ks, java.awt.event.KeyEvent event,
                                       Object sender, int modifiers) {
        if (action == null) {
            return false;
        }
        if (!action.isEnabled()) {
            return false;
        }
        Object commandO;
        boolean stayNull;

        // Get the command object.
        commandO = action.getValue(Action.ACTION_COMMAND_KEY);
        if (commandO == null && (action instanceof JComponent.ActionStandin)) {
            // ActionStandin is used for historical reasons to support
            // registerKeyboardAction with a null value.
            stayNull = true;
        } else {
            stayNull = false;
        }

        // Convert it to a string.
        String command;

        if (commandO != null) {
            command = commandO.toString();
        } else if (!stayNull && event.getKeyChar() != java.awt.event.KeyEvent.CHAR_UNDEFINED) {
            command = String.valueOf(event.getKeyChar());
        } else {
            // Do null for undefined chars, or if registerKeyboardAction
            // was called with a null.
            command = null;
        }
        action.actionPerformed(new java.awt.event.ActionEvent(sender,
            java.awt.event.ActionEvent.ACTION_PERFORMED, command, event.getWhen(), modifiers));
        return true;
    }
}
