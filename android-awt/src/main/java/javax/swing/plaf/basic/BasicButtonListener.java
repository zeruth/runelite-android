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
package javax.swing.plaf.basic;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ButtonModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentInputMapUIResource;

/**
 * Button Listener
 *
 * @author Jeff Dinkins
 * @author Arnaud Weber (keyboard UI support)
 */
public class BasicButtonListener implements MouseListener, MouseMotionListener,
                                   FocusListener, ChangeListener, PropertyChangeListener
{
    private long lastPressedTimestamp = -1;
    private boolean shouldDiscardRelease = false;

    /** The Basic L&amp;F focus bindings, used when the look and feel defines none. */
    private static final Object[] DEFAULT_FOCUS_BINDINGS = {
        "SPACE", "pressed",
        "released SPACE", "released",
    };

    /**
     * Populates Buttons actions.
     */
    static void loadActionMap(ActionMap map) {
        map.put(Actions.PRESS, new Actions(Actions.PRESS));
        map.put(Actions.RELEASE, new Actions(Actions.RELEASE));
    }

    public BasicButtonListener(AbstractButton b) {
    }

    public void propertyChange(PropertyChangeEvent e) {
        String prop = e.getPropertyName();
        if (AbstractButton.MNEMONIC_CHANGED_PROPERTY.equals(prop)) {
            updateMnemonicBinding((AbstractButton) e.getSource());
        } else if (AbstractButton.CONTENT_AREA_FILLED_CHANGED_PROPERTY.equals(prop)) {
            checkOpacity((AbstractButton) e.getSource());
        }
    }

    protected void checkOpacity(AbstractButton b) {
        b.setOpaque(b.isContentAreaFilled());
    }

    /**
     * Register default key actions: pressing space to "click" a
     * button and registering the keyboard mnemonic (if any).
     */
    public void installKeyboardActions(JComponent c) {
        AbstractButton b = (AbstractButton) c;
        // Update the mnemonic binding.
        updateMnemonicBinding(b);

        ActionMap map = new ActionMapUIResource();
        loadActionMap(map);
        SwingUtilities.replaceUIActionMap(c, map);

        InputMap km = getInputMap(JComponent.WHEN_FOCUSED, c);

        SwingUtilities.replaceUIInputMap(c, JComponent.WHEN_FOCUSED, km);
    }

    /**
     * Unregister's default key actions
     */
    public void uninstallKeyboardActions(JComponent c) {
        SwingUtilities.replaceUIInputMap(c, JComponent.
                                         WHEN_IN_FOCUSED_WINDOW, null);
        SwingUtilities.replaceUIInputMap(c, JComponent.WHEN_FOCUSED, null);
        SwingUtilities.replaceUIActionMap(c, null);
    }

    /**
     * Returns the InputMap for condition <code>condition</code>. Called as
     * part of <code>installKeyboardActions</code>.
     */
    InputMap getInputMap(int condition, JComponent c) {
        if (condition == JComponent.WHEN_FOCUSED) {
            String prefix = "Button.";
            if (c instanceof AbstractButton
                    && ((AbstractButton) c).getUI() instanceof BasicButtonUI) {
                prefix = ((BasicButtonUI) ((AbstractButton) c).getUI()).getPropertyPrefix();
            }
            Object map = UIManager.get(prefix + "focusInputMap");
            if (map instanceof InputMap) {
                return (InputMap) map;
            }
            return LookAndFeel.makeInputMap(map instanceof Object[]
                    ? (Object[]) map : DEFAULT_FOCUS_BINDINGS);
        }
        return null;
    }

    /**
     * Resets the binding for the mnemonic in the WHEN_IN_FOCUSED_WINDOW
     * UI InputMap.
     */
    void updateMnemonicBinding(AbstractButton b) {
        int m = b.getMnemonic();
        if (m != 0) {
            InputMap map = SwingUtilities.getUIInputMap(
                                b, JComponent.WHEN_IN_FOCUSED_WINDOW);

            if (map == null) {
                map = new ComponentInputMapUIResource(b);
                SwingUtilities.replaceUIInputMap(b,
                               JComponent.WHEN_IN_FOCUSED_WINDOW, map);
            }
            map.clear();
            map.put(KeyStroke.getKeyStroke(m, InputEvent.ALT_MASK, false),
                    "pressed");
            map.put(KeyStroke.getKeyStroke(m, InputEvent.ALT_MASK, true),
                    "released");
            map.put(KeyStroke.getKeyStroke(m, 0, true), "released");
        } else {
            InputMap map = SwingUtilities.getUIInputMap(b, JComponent.
                                             WHEN_IN_FOCUSED_WINDOW);
            if (map != null) {
                map.clear();
            }
        }
    }

    public void stateChanged(ChangeEvent e) {
        AbstractButton b = (AbstractButton) e.getSource();
        b.repaint();
    }

    public void focusGained(FocusEvent e) {
        AbstractButton b = (AbstractButton) e.getSource();
        if (b instanceof JButton && ((JButton) b).isDefaultCapable()) {
            JRootPane root = SwingUtilities.getRootPane(b);
            if (root != null && defaultButtonFollowsFocus(b)) {
                root.putClientProperty("temporaryDefaultButton", b);
                root.setDefaultButton((JButton) b);
                root.putClientProperty("temporaryDefaultButton", null);
            }
        }
        b.repaint();
    }

    public void focusLost(FocusEvent e) {
        AbstractButton b = (AbstractButton) e.getSource();
        JRootPane root = SwingUtilities.getRootPane(b);
        if (root != null) {
            Object initialDefault = root.getClientProperty("initialDefaultButton");
            if (b != initialDefault && defaultButtonFollowsFocus(b)) {
                root.setDefaultButton(initialDefault instanceof JButton
                        ? (JButton) initialDefault : null);
            }
        }

        ButtonModel model = b.getModel();
        model.setPressed(false);
        model.setArmed(false);
        b.repaint();
    }

    /** DefaultLookup.getBoolean(b, ui, prefix + "defaultButtonFollowsFocus", true) in 8u. */
    private static boolean defaultButtonFollowsFocus(AbstractButton b) {
        if (!(b.getUI() instanceof BasicButtonUI)) {
            return false;
        }
        Object value = UIManager.get(((BasicButtonUI) b.getUI()).getPropertyPrefix()
                + "defaultButtonFollowsFocus");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
       if (SwingUtilities.isLeftMouseButton(e)) {
          AbstractButton b = (AbstractButton) e.getSource();

          if (b.contains(e.getX(), e.getY())) {
              long multiClickThreshhold = b.getMultiClickThreshhold();
              long lastTime = lastPressedTimestamp;
              long currentTime = lastPressedTimestamp = e.getWhen();
              if (lastTime != -1 && currentTime - lastTime < multiClickThreshhold) {
                  shouldDiscardRelease = true;
                  return;
              }

             ButtonModel model = b.getModel();
             if (!model.isEnabled()) {
                // Disabled buttons ignore all input...
                return;
             }
             if (!model.isArmed()) {
                // button not armed, it should be
                model.setArmed(true);
             }
             model.setPressed(true);
             if (!b.hasFocus() && b.isFocusable()) {
                b.requestFocus();
             }
          }
       }
    }

    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            // Support for multiClickThreshhold
            if (shouldDiscardRelease) {
                shouldDiscardRelease = false;
                return;
            }
            AbstractButton b = (AbstractButton) e.getSource();
            ButtonModel model = b.getModel();
            model.setPressed(false);
            model.setArmed(false);
        }
    }

    public void mouseEntered(MouseEvent e) {
        AbstractButton b = (AbstractButton) e.getSource();
        ButtonModel model = b.getModel();
        if (b.isRolloverEnabled() && !SwingUtilities.isLeftMouseButton(e)) {
            model.setRollover(true);
        }
        if (model.isPressed()) {
            model.setArmed(true);
        }
    }

    public void mouseExited(MouseEvent e) {
        AbstractButton b = (AbstractButton) e.getSource();
        ButtonModel model = b.getModel();
        if (b.isRolloverEnabled()) {
            model.setRollover(false);
        }
        model.setArmed(false);
    }


    /**
     * Actions for Buttons. Two types of action are supported:
     * pressed: Moves the button to a pressed state
     * released: Disarms the button.
     */
    private static class Actions extends AbstractAction {
        private static final String PRESS = "pressed";
        private static final String RELEASE = "released";

        Actions(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent e) {
            AbstractButton b = (AbstractButton) e.getSource();
            String key = (String) getValue(Action.NAME);
            if (key == PRESS) {
                ButtonModel model = b.getModel();
                model.setArmed(true);
                model.setPressed(true);
                if (!b.hasFocus()) {
                    b.requestFocus();
                }
            } else if (key == RELEASE) {
                ButtonModel model = b.getModel();
                model.setPressed(false);
                model.setArmed(false);
            }
        }
    }
}
