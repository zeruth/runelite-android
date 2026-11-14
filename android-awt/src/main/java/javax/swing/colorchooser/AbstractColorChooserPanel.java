/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.colorchooser;

import java.awt.Color;
import java.awt.Graphics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * This is the abstract superclass for color choosers.  If you want to add
 * a new color chooser panel into a <code>JColorChooser</code>, subclass
 * this class.
 *
 * @author Tom Santos
 * @author Steve Wilson
 */
@SuppressWarnings("serial") // Same-version serialization only
public abstract class AbstractColorChooserPanel extends JPanel {

    private final PropertyChangeListener enabledListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent event) {
            Object value = event.getNewValue();
            if (value instanceof Boolean) {
                setEnabled((Boolean) value);
            }
        }
    };

    /**
     *
     */
    private JColorChooser chooser;

    private boolean colorTransparencySelectionEnabled = true;

    /**
     * Invoked automatically when the model's state changes.
     * It is also called by <code>installChooserPanel</code> to allow
     * you to set up the initial state of your chooser.
     * Override this method to update your <code>ChooserPanel</code>.
     */
    public abstract void updateChooser();

    /**
     * Builds a new chooser panel.
     */
    protected abstract void buildChooser();

    /**
     * Returns a string containing the display name of the panel.
     * @return the name of the display panel
     */
    public abstract String getDisplayName();

    /**
     * Provides a hint to the look and feel as to the
     * <code>KeyEvent.VK</code> constant that can be used as a mnemonic to
     * access the panel. A return value &lt;= 0 indicates there is no mnemonic.
     *
     * @return KeyEvent.VK constant identifying the mnemonic; &lt;= 0 for no
     *         mnemonic
     * @since 1.4
     */
    public int getMnemonic() {
        return 0;
    }

    /**
     * Provides a hint to the look and feel as to the index of the character in
     * <code>getDisplayName</code> that should be visually identified as the
     * mnemonic.
     *
     * @return Character index to render mnemonic for; -1 to provide no
     *                   visual identifier for this panel.
     * @since 1.4
     */
    public int getDisplayedMnemonicIndex() {
        return -1;
    }

    /**
     * Returns the small display icon for the panel.
     * @return the small display icon
     */
    public abstract Icon getSmallDisplayIcon();

    /**
     * Returns the large display icon for the panel.
     * @return the large display icon
     */
    public abstract Icon getLargeDisplayIcon();

    /**
     * Invoked when the panel is added to the chooser.
     * If you override this, be sure to call <code>super</code>.
     * @param enclosingChooser  the panel to be added
     * @exception RuntimeException  if the chooser panel has already been
     *                          installed
     */
    public void installChooserPanel(JColorChooser enclosingChooser) {
        if (chooser != null) {
            throw new RuntimeException ("This chooser panel is already installed");
        }
        chooser = enclosingChooser;
        chooser.addPropertyChangeListener("enabled", enabledListener);
        setEnabled(chooser.isEnabled());
        buildChooser();
        updateChooser();
    }

    /**
     * Invoked when the panel is removed from the chooser.
     * If override this, be sure to call <code>super</code>.
     * @param enclosingChooser the chooser from which the panel is being removed
     */
    public void uninstallChooserPanel(JColorChooser enclosingChooser) {
        chooser.removePropertyChangeListener("enabled", enabledListener);
        chooser = null;
    }

    /**
      * Returns the model that the chooser panel is editing.
      * @return the <code>ColorSelectionModel</code> model this panel
      *         is editing
      */
    public ColorSelectionModel getColorSelectionModel() {
        return (this.chooser != null)
                ? this.chooser.getSelectionModel()
                : null;
    }

    /**
     * Returns the color that is currently selected.
     * @return the <code>Color</code> that is selected
     */
    protected Color getColorFromModel() {
        ColorSelectionModel model = getColorSelectionModel();
        return (model != null)
                ? model.getSelectedColor()
                : null;
    }

    void setSelectedColor(Color color) {
        ColorSelectionModel model = getColorSelectionModel();
        if (model != null) {
            model.setSelectedColor(color);
        }
    }

    // JDK 9+ (9)
    /**
     * Sets whether color chooser panel allows to select the transparency
     * (alpha value) of a color.
     *
     * @param b true if the transparency of a color can be selected
     * @since 9
     */
    public void setColorTransparencySelectionEnabled(boolean b) {
        boolean oldValue = colorTransparencySelectionEnabled;
        if (b != oldValue) {
            colorTransparencySelectionEnabled = b;
            firePropertyChange("colorTransparencySelectionEnabled", oldValue, b);
        }
    }

    // JDK 9+ (9)
    /**
     * Gets whether color chooser panel allows to select the transparency
     * (alpha value) of a color.
     *
     * @return true if the transparency of a color can be selected
     * @since 9
     */
    public boolean isColorTransparencySelectionEnabled() {
        return colorTransparencySelectionEnabled;
    }

    /**
     * Draws the panel.
     * @param g  the <code>Graphics</code> object
     */
    public void paint(Graphics g) {
        super.paint(g);
    }

    /**
     * Returns an integer from the defaults table. If <code>key</code> does
     * not map to a valid <code>Integer</code>, <code>default</code> is
     * returned.
     *
     * @param key  an <code>Object</code> specifying the int
     * @param defaultValue Returned value if <code>key</code> is not available,
     *                     or is not an Integer
     * @return the int
     */
    int getInt(Object key, int defaultValue) {
        Object value = UIManager.get(key, getLocale());

        if (value instanceof Integer) {
            return ((Integer)value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String)value);
            } catch (NumberFormatException nfe) {}
        }
        return defaultValue;
    }
}
