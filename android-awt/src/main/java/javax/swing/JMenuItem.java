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

import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;

public class JMenuItem extends AbstractButton implements javax.accessibility.Accessible, MenuElement {
    private static final long serialVersionUID = 1L;

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJMenuItem();
        }
        return accessibleContext;
    }

    /** Accessibility support for menu items: role MENU_ITEM. */
    protected class AccessibleJMenuItem extends AccessibleAbstractButton {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.MENU_ITEM;
        }
    }


    public JMenuItem() {}
    public JMenuItem(String text) { setText(text); }
    public JMenuItem(Icon icon) { setIcon(icon); }
    public JMenuItem(Action a) { this(); setAction(a); }
    public JMenuItem(String text, Icon icon) { setText(text); setIcon(icon); }
    public JMenuItem(String text, int mnemonic) { setText(text); setMnemonic(mnemonic); }

    private KeyStroke accelerator;
    public KeyStroke getAccelerator() { return accelerator; }
    public void setAccelerator(KeyStroke keyStroke) {
        KeyStroke old = accelerator;
        accelerator = keyStroke;
        repaint();
        revalidate();
        firePropertyChange("accelerator", old, keyStroke);
    }

    // Swing's JMenuItem takes only SMALL_ICON from its Action (never LARGE_ICON_KEY) and
    // also picks up ACCELERATOR_KEY.
    @Override
    void setIconFromAction(Action a) {
        setIcon(a == null ? null : (Icon) a.getValue(Action.SMALL_ICON));
    }

    @Override
    protected void configurePropertiesFromAction(Action a) {
        super.configurePropertiesFromAction(a);
        configureAcceleratorFromAction(a);
    }

    /** JMenu overrides this to nothing: a menu has no accelerator (8u). */
    void configureAcceleratorFromAction(Action a) {
        Object ks = a == null ? null : a.getValue(Action.ACCELERATOR_KEY);
        setAccelerator(ks instanceof KeyStroke ? (KeyStroke) ks : null);
    }

    @Override
    protected void actionPropertyChanged(Action a, String propertyName) {
        if (Action.LARGE_ICON_KEY.equals(propertyName)) return;
        if (Action.ACCELERATOR_KEY.equals(propertyName)) {
            configureAcceleratorFromAction(a);
            return;
        }
        super.actionPropertyChanged(a, propertyName);
    }
    public boolean isArmed() { return getModel().isArmed(); }
    public void setArmed(boolean b) { getModel().setArmed(b); }

    // MenuElement, as JMenuItem in 8u: the manager's raw events are rewrapped with the
    // path and handed to the MenuDragMouse/MenuKey listeners.
    private boolean isMouseDragged = false;

    public void processMouseEvent(java.awt.event.MouseEvent e, MenuElement path[], MenuSelectionManager manager) {
        processMenuDragMouseEvent(
                new MenuDragMouseEvent(e.getComponent(), e.getID(),
                                       e.getWhen(),
                                       e.getModifiers(), e.getX(), e.getY(),
                                       e.getXOnScreen(), e.getYOnScreen(),
                                       e.getClickCount(), e.isPopupTrigger(),
                                       path, manager));
    }

    public void processKeyEvent(java.awt.event.KeyEvent e, MenuElement path[], MenuSelectionManager manager) {
        MenuKeyEvent mke = new MenuKeyEvent(e.getComponent(), e.getID(),
                                            e.getWhen(), e.getModifiers(),
                                            e.getKeyCode(), e.getKeyChar(),
                                            path, manager);
        processMenuKeyEvent(mke);
        if (mke.isConsumed()) {
            e.consume();
        }
    }

    public void processMenuDragMouseEvent(MenuDragMouseEvent e) {
        switch (e.getID()) {
        case java.awt.event.MouseEvent.MOUSE_ENTERED:
            isMouseDragged = false; fireMenuDragMouseEntered(e); break;
        case java.awt.event.MouseEvent.MOUSE_EXITED:
            isMouseDragged = false; fireMenuDragMouseExited(e); break;
        case java.awt.event.MouseEvent.MOUSE_DRAGGED:
            isMouseDragged = true; fireMenuDragMouseDragged(e); break;
        case java.awt.event.MouseEvent.MOUSE_RELEASED:
            if (isMouseDragged) fireMenuDragMouseReleased(e); break;
        default:
            break;
        }
    }

    public void processMenuKeyEvent(MenuKeyEvent e) {
        switch (e.getID()) {
        case java.awt.event.KeyEvent.KEY_PRESSED:
            fireMenuKeyPressed(e); break;
        case java.awt.event.KeyEvent.KEY_RELEASED:
            fireMenuKeyReleased(e); break;
        case java.awt.event.KeyEvent.KEY_TYPED:
            fireMenuKeyTyped(e); break;
        default:
            break;
        }
    }

    protected void fireMenuDragMouseEntered(MenuDragMouseEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuDragMouseListener.class) {
                ((MenuDragMouseListener) listeners[i + 1]).menuDragMouseEntered(event);
            }
        }
    }

    protected void fireMenuDragMouseExited(MenuDragMouseEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuDragMouseListener.class) {
                ((MenuDragMouseListener) listeners[i + 1]).menuDragMouseExited(event);
            }
        }
    }

    protected void fireMenuDragMouseDragged(MenuDragMouseEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuDragMouseListener.class) {
                ((MenuDragMouseListener) listeners[i + 1]).menuDragMouseDragged(event);
            }
        }
    }

    protected void fireMenuDragMouseReleased(MenuDragMouseEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuDragMouseListener.class) {
                ((MenuDragMouseListener) listeners[i + 1]).menuDragMouseReleased(event);
            }
        }
    }

    protected void fireMenuKeyPressed(MenuKeyEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuKeyListener.class) {
                ((MenuKeyListener) listeners[i + 1]).menuKeyPressed(event);
            }
        }
    }

    protected void fireMenuKeyReleased(MenuKeyEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuKeyListener.class) {
                ((MenuKeyListener) listeners[i + 1]).menuKeyReleased(event);
            }
        }
    }

    protected void fireMenuKeyTyped(MenuKeyEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuKeyListener.class) {
                ((MenuKeyListener) listeners[i + 1]).menuKeyTyped(event);
            }
        }
    }

    public void addMenuDragMouseListener(MenuDragMouseListener l) {
        listenerList.add(MenuDragMouseListener.class, l);
    }

    public void removeMenuDragMouseListener(MenuDragMouseListener l) {
        listenerList.remove(MenuDragMouseListener.class, l);
    }

    public MenuDragMouseListener[] getMenuDragMouseListeners() {
        return listenerList.getListeners(MenuDragMouseListener.class);
    }

    public void addMenuKeyListener(MenuKeyListener l) {
        listenerList.add(MenuKeyListener.class, l);
    }

    public void removeMenuKeyListener(MenuKeyListener l) {
        listenerList.remove(MenuKeyListener.class, l);
    }

    public MenuKeyListener[] getMenuKeyListeners() {
        return listenerList.getListeners(MenuKeyListener.class);
    }

    // A leaf item has no sub-elements; JMenu overrides getSubElements().
    /** As in 8u: an item on the selected path is armed, so releasing over it fires it. */
    @Override public void menuSelectionChanged(boolean isIncluded) { setArmed(isIncluded); }
    @Override public MenuElement[] getSubElements() { return new MenuElement[0]; }
    @Override public java.awt.Component getComponent() { return this; }
}
