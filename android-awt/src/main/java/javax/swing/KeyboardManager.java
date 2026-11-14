/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Container;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Vector;

/**
 * The KeyboardManager class is used to help dispatch keyboard actions for the
 * WHEN_IN_FOCUSED_WINDOW style actions.  Actions with other conditions are handled
 * directly in JComponent.
 *
 * The way this works is that each top-level container (Window, Applet or
 * JInternalFrame) has a table mapping KeyStrokes to the components registered
 * for them; JMenuBars are listed under the key {@code JMenuBar.class} and get the
 * event after every registered component.
 *
 * Android: a sidebar panel is mounted by the host without a Window above it, so when
 * no Window, Applet or JInternalFrame is found the topmost container plays that part
 * (the JDK registers nothing there; on the device that tree is the whole window).
 *
 * @author Steve Wilson
 */
class KeyboardManager {

    static KeyboardManager currentManager = new KeyboardManager();

    /** maps top-level containers to a sub-hashtable full of keystrokes */
    Hashtable<Container, Hashtable<Object, Object>> containerMap = new Hashtable<>();

    /** Maps component/keystroke pairs to a topLevel container. */
    Hashtable<ComponentKeyStrokePair, Container> componentKeyStrokeMap = new Hashtable<>();

    public static KeyboardManager getCurrentManager() {
        return currentManager;
    }

    public static void setCurrentManager(KeyboardManager km) {
        currentManager = km;
    }

    /**
     * register keystrokes here which are for the WHEN_IN_FOCUSED_WINDOW
     * case.
     * Other types of keystrokes will be handled by walking the hierarchy
     * That simplifies some potentially hairy stuff.
     */
    public void registerKeyStroke(KeyStroke k, JComponent c) {
        Container topContainer = getTopAncestor(c);
        if (topContainer == null) {
            return;
        }
        ComponentKeyStrokePair pair = new ComponentKeyStrokePair(c, k);
        Container previous = componentKeyStrokeMap.get(pair);
        if (previous != null && previous != topContainer) {
            // Re-parented since it was registered: drop the stale entry first.
            unregisterKeyStroke(k, c);
        }
        Hashtable<Object, Object> keyMap = containerMap.get(topContainer);
        if (keyMap == null) {
            keyMap = registerNewTopContainer(topContainer);
        }

        Object tmp = keyMap.get(k);
        if (tmp == null) {
            keyMap.put(k, c);
        } else if (tmp instanceof Vector) {
            @SuppressWarnings("unchecked")
            Vector<Object> v = (Vector<Object>) tmp;
            if (!v.contains(c)) {
                v.addElement(c);
            }
        } else if (tmp instanceof JComponent) {
            // if a JComponent is there then remove it and replace it with a vector
            // Then add the old component and the new component to the vector
            // then insert the vector in the table
            if (tmp != c) {
                Vector<Object> v = new Vector<>();
                v.addElement(tmp);
                v.addElement(c);
                keyMap.put(k, v);
            }
        }
        componentKeyStrokeMap.put(pair, topContainer);
    }

    /**
     * Find the top focusable Window, Applet, or InternalFrame
     */
    private static Container getTopAncestor(JComponent c) {
        Container last = null;
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Window && ((Window) p).isFocusableWindow()
                    || isInstanceOf(p, "java.applet.Applet") || isInstanceOf(p, "javax.swing.JInternalFrame")) {
                return p;
            }
            last = p;
        }
        // Android: the host-mounted root of a windowless tree.
        return last instanceof Window ? null : last;
    }

    /** By name, so neither Applet nor JInternalFrame has to be loaded (or exist) to ask. */
    static boolean isInstanceOf(Object object, String className) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) return true;
        }
        return false;
    }

    public void unregisterKeyStroke(KeyStroke ks, JComponent c) {
        // component may have already been removed from the hierarchy, we
        // need to look up the container using the componentKeyStrokeMap.
        ComponentKeyStrokePair ckp = new ComponentKeyStrokePair(c, ks);
        Container topContainer = componentKeyStrokeMap.get(ckp);
        if (topContainer == null) {
            return;
        }
        Hashtable<Object, Object> keyMap = containerMap.get(topContainer);
        if (keyMap == null) {
            componentKeyStrokeMap.remove(ckp);
            return;
        }
        Object tmp = keyMap.get(ks);
        if (tmp instanceof JComponent && tmp == c) {
            keyMap.remove(ks);
        } else if (tmp instanceof Vector) {
            Vector<?> v = (Vector<?>) tmp;
            v.removeElement(c);
            if (v.isEmpty()) {
                keyMap.remove(ks);
            }
        }
        if (keyMap.isEmpty()) {
            containerMap.remove(topContainer);
        }
        componentKeyStrokeMap.remove(ckp);
    }

    /**
     * This method is called when the focused component (and none of
     * its ancestors) want the key event.  This will look up the keystroke
     * to see if any chidren (or subchildren) of the specified container
     * want a crack at the event.
     * If one of them wants it, then it will "DO-THE-RIGHT-THING"
     */
    public boolean fireKeyboardAction(KeyEvent e, boolean pressed, Container topAncestor) {
        if (e.isConsumed()) {
            return false;
        }
        // There may be two keystrokes associated with a low-level key event;
        // in this case a keystroke made of an extended key code has a priority.
        KeyStroke ks;
        KeyStroke ksE = null;

        if (e.getID() == KeyEvent.KEY_TYPED) {
            ks = KeyStroke.getKeyStroke(e.getKeyChar());
        } else {
            if (e.getKeyCode() != e.getExtendedKeyCode()) {
                ksE = KeyStroke.getKeyStroke(e.getExtendedKeyCode(), e.getModifiers(), !pressed);
            }
            ks = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers(), !pressed);
        }

        Hashtable<Object, Object> keyMap = containerMap.get(topAncestor);
        if (keyMap != null) { // this container isn't registered, so bail
            Object tmp = null;
            // Have to check both since we don't know which the user registered.
            if (ksE != null) {
                tmp = keyMap.get(ksE);
                if (tmp != null) {
                    ks = ksE;
                }
            }
            if (tmp == null) {
                tmp = keyMap.get(ks);
            }

            if (tmp instanceof JComponent) {
                JComponent c = (JComponent) tmp;
                if (c.isShowing() && c.isEnabled()) { // only give it out if enabled and visible
                    fireBinding(c, ks, e, pressed);
                }
            } else if (tmp instanceof Vector) { // more than one comp registered for this
                Vector<?> v = (Vector<?>) tmp;
                // There is no well defined order for WHEN_IN_FOCUSED_WINDOW
                // bindings, but we give precedence to those bindings just
                // added. This is done so that JMenus WHEN_IN_FOCUSED_WINDOW
                // bindings are accessed before those of the JRootPane (they
                // both have a WHEN_IN_FOCUSED_WINDOW binding for enter).
                for (int counter = v.size() - 1; counter >= 0; counter--) {
                    JComponent c = (JComponent) v.elementAt(counter);
                    if (c.isShowing() && c.isEnabled()) { // don't want to give these out
                        fireBinding(c, ks, e, pressed);
                        if (e.isConsumed()) {
                            return true;
                        }
                    }
                }
            }
        }

        if (e.isConsumed()) {
            return true;
        }
        // if no one else handled it, then give the menus a crack
        // The're handled differently.  The key is to let any JMenuBars
        // process the event
        if (keyMap != null) {
            Object menus = keyMap.get(JMenuBar.class);
            if (menus instanceof Vector) {
                Vector<?> v = (Vector<?>) menus;
                for (int i = 0; i < v.size(); i++) {
                    JMenuBar mb = (JMenuBar) v.elementAt(i);
                    if (mb.isShowing() && mb.isEnabled()) { // don't want to give these out
                        boolean extended = (ksE != null) && !ksE.equals(ks);
                        if (extended) {
                            fireBinding(mb, ksE, e, pressed);
                        }
                        if (!extended || !e.isConsumed()) {
                            fireBinding(mb, ks, e, pressed);
                        }
                        if (e.isConsumed()) {
                            return true;
                        }
                    }
                }
            }
        }

        return e.isConsumed();
    }

    void fireBinding(JComponent c, KeyStroke ks, KeyEvent e, boolean pressed) {
        if (c.processKeyBinding(ks, e, JComponent.WHEN_IN_FOCUSED_WINDOW, pressed)) {
            e.consume();
        }
    }

    public void registerMenuBar(JMenuBar mb) {
        Container top = getTopAncestor(mb);
        if (top == null) {
            return;
        }
        Hashtable<Object, Object> keyMap = containerMap.get(top);
        if (keyMap == null) {
            keyMap = registerNewTopContainer(top);
        }
        // use the menubar class as the key
        @SuppressWarnings("unchecked")
        Vector<Object> menuBars = (Vector<Object>) keyMap.get(JMenuBar.class);
        if (menuBars == null) { // if we don't have a list of menubars,
                                // then make one.
            menuBars = new Vector<>();
            keyMap.put(JMenuBar.class, menuBars);
        }
        if (!menuBars.contains(mb)) {
            menuBars.addElement(mb);
        }
    }

    public void unregisterMenuBar(JMenuBar mb) {
        // The menu bar may already be out of the hierarchy: look in every table.
        for (java.util.Iterator<Hashtable<Object, Object>> it = containerMap.values().iterator(); it.hasNext();) {
            Hashtable<Object, Object> keyMap = it.next();
            Object menuBars = keyMap.get(JMenuBar.class);
            if (menuBars instanceof Vector) {
                Vector<?> v = (Vector<?>) menuBars;
                v.removeElement(mb);
                if (v.isEmpty()) {
                    keyMap.remove(JMenuBar.class);
                    if (keyMap.isEmpty()) {
                        it.remove();
                    }
                }
            }
        }
    }

    protected Hashtable<Object, Object> registerNewTopContainer(Container topContainer) {
        Hashtable<Object, Object> keyMap = new Hashtable<>();
        containerMap.put(topContainer, keyMap);
        return keyMap;
    }

    /**
     * This class is used to create keys for a hashtable
     * which looks up topContainers based on component, keystroke pairs
     * This is used to make unregistering KeyStrokes fast
     */
    static class ComponentKeyStrokePair {
        final Object component;
        final Object keyStroke;

        ComponentKeyStrokePair(Object comp, Object key) {
            component = comp;
            keyStroke = key;
        }

        public boolean equals(Object o) {
            if (!(o instanceof ComponentKeyStrokePair)) {
                return false;
            }
            ComponentKeyStrokePair ckp = (ComponentKeyStrokePair) o;
            return component.equals(ckp.component) && keyStroke.equals(ckp.keyStroke);
        }

        public int hashCode() {
            return component.hashCode() * keyStroke.hashCode();
        }
    }
}
