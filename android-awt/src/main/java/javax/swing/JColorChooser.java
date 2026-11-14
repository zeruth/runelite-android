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

package javax.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serializable;
import java.util.Locale;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorChooserComponentFactory;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.colorchooser.DefaultColorSelectionModel;
import javax.swing.plaf.ColorChooserUI;

/**
 * <code>JColorChooser</code> provides a pane of controls designed to allow
 * a user to manipulate and select a color.
 * <p>
 * This class provides three levels of API:
 * <ol>
 * <li>A static convenience method which shows a modal color-chooser
 * dialog and returns the color selected by the user.
 * <li>A static convenience method for creating a color-chooser dialog
 * where <code>ActionListeners</code> can be specified to be invoked when
 * the user presses one of the dialog buttons.
 * <li>The ability to create instances of <code>JColorChooser</code> panes
 * directly (within any container). <code>PropertyChange</code> listeners
 * can be added to detect when the current "color" property changes.
 * </ol>
 * <p>
 * On Android the pane is a real component painted by its UI delegate (it
 * renders inside the sidebar like any Swing panel), and {@link #createDialog}
 * returns a real {@code JDialog} the window bridge presents. The static
 * {@link #showDialog} is answered by the host's system color picker when one is
 * installed with {@link #setDialogHandler}, so a plugin's "pick a color" call
 * feels native; without a handler it shows the Swing dialog, as the JDK does.
 *
 * @author James Gosling
 * @author Amy Fowler
 * @author Steve Wilson
 */
public class JColorChooser extends JComponent implements Accessible {

    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "ColorChooserUI";

    private ColorSelectionModel selectionModel;

    private JComponent previewPanel = ColorChooserComponentFactory.getPreviewPanel();

    private AbstractColorChooserPanel[] chooserPanels = new AbstractColorChooserPanel[0];

    private boolean dragEnabled;

    /**
     * The selection model property name.
     */
    public static final String      SELECTION_MODEL_PROPERTY = "selectionModel";

    /**
     * The preview panel property name.
     */
    public static final String      PREVIEW_PANEL_PROPERTY = "previewPanel";

    /**
     * The chooserPanel array property name.
     */
    public static final String      CHOOSER_PANELS_PROPERTY = "chooserPanels";

    /**
     * The host bridge that answers {@link #showDialog} with a system color
     * picker. It is not JDK API; the Android app installs it at startup.
     */
    public interface DialogHandler {
        /** Shows the picker modally; returns the chosen color, or null when cancelled. */
        Color show(Component parent, String title, Color initial);

        /** As {@link #show(Component, String, Color)}, offering the alpha channel when asked. */
        default Color show(Component parent, String title, Color initial, boolean transparency) {
            return show(parent, title, initial);
        }
    }

    private static volatile DialogHandler dialogHandler;

    /** Installs the host's system color picker for {@link #showDialog}; null restores the Swing dialog. */
    public static void setDialogHandler(DialogHandler handler) {
        dialogHandler = handler;
    }

    /**
     * Shows a modal color-chooser dialog and blocks until the
     * dialog is hidden.  If the user presses the "OK" button, then
     * this method hides/disposes the dialog and returns the selected color.
     * If the user presses the "Cancel" button or closes the dialog without
     * pressing "OK", then this method hides/disposes the dialog and returns
     * <code>null</code>.
     *
     * @param component    the parent <code>Component</code> for the dialog
     * @param title        the String containing the dialog's title
     * @param initialColor the initial Color set when the color-chooser is shown
     * @return the selected color or <code>null</code> if the user opted out
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static Color showDialog(Component component,
        String title, Color initialColor) throws HeadlessException {
        return showDialog(component, title, initialColor, true);
    }

    // JDK 9+ (9): jdk17u src/java.desktop/share/classes/javax/swing/JColorChooser.java
    /**
     * Shows a modal color-chooser dialog and blocks until the dialog is
     * hidden, with the transparency controls shown when
     * {@code colorTransparencySelectionEnabled} is true.
     *
     * @param component    the parent {@code Component} for the dialog
     * @param title        the String containing the dialog's title
     * @param initialColor the initial Color set when the color-chooser is shown
     * @param colorTransparencySelectionEnabled true if the transparency of
     *            a color can be selected
     * @return the selected color or {@code null} if the user opted out
     * @since 9
     */
    public static Color showDialog(Component component, String title,
            Color initialColor, boolean colorTransparencySelectionEnabled)
            throws HeadlessException {
        Color initial = initialColor != null ? initialColor : Color.white;
        DialogHandler handler = dialogHandler;
        if (handler != null) {
            return handler.show(component, title, initial, colorTransparencySelectionEnabled);
        }

        final JColorChooser pane = new JColorChooser(initial);
        for (AbstractColorChooserPanel ccPanel : pane.getChooserPanels()) {
            ccPanel.setColorTransparencySelectionEnabled(colorTransparencySelectionEnabled);
        }

        ColorTracker ok = new ColorTracker(pane);
        JDialog dialog = createDialog(component, title, true, pane, ok, null);

        dialog.setVisible(true); // blocks until user brings dialog down...

        return ok.getColor();
    }

    /**
     * Creates and returns a new dialog containing the specified
     * <code>ColorChooser</code> pane along with "OK", "Cancel", and "Reset"
     * buttons. If the "OK" or "Cancel" buttons are pressed, the dialog is
     * automatically hidden (but not disposed).  If the "Reset"
     * button is pressed, the color-chooser's color will be reset to the
     * color which was set the last time <code>show</code> was invoked on the
     * dialog and the dialog will remain showing.
     *
     * @param c              the parent component for the dialog
     * @param title          the title for the dialog
     * @param modal          a boolean. When true, the remainder of the program
     *                       is inactive until the dialog is closed.
     * @param chooserPane    the color-chooser to be placed inside the dialog
     * @param okListener     the ActionListener invoked when "OK" is pressed
     * @param cancelListener the ActionListener invoked when "Cancel" is pressed
     * @return a new dialog containing the color-chooser pane
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     */
    public static JDialog createDialog(Component c, String title, boolean modal,
        JColorChooser chooserPane, ActionListener okListener,
        ActionListener cancelListener) throws HeadlessException {

        Window window = JOptionPane.getWindowForComponent(c);
        ColorChooserDialog dialog;
        if (window instanceof Frame) {
            dialog = new ColorChooserDialog((Frame)window, title, modal, c, chooserPane,
                                            okListener, cancelListener);
        } else {
            dialog = new ColorChooserDialog((Dialog)window, title, modal, c, chooserPane,
                                            okListener, cancelListener);
        }
        dialog.getAccessibleContext().setAccessibleDescription(title);
        return dialog;
    }

    /**
     * Creates a color chooser pane with an initial color of white.
     */
    public JColorChooser() {
        this(Color.white);
    }

    /**
     * Creates a color chooser pane with the specified initial color.
     *
     * @param initialColor the initial color set in the chooser
     */
    public JColorChooser(Color initialColor) {
        this( new DefaultColorSelectionModel(initialColor) );
    }

    /**
     * Creates a color chooser pane with the specified
     * <code>ColorSelectionModel</code>.
     *
     * @param model the <code>ColorSelectionModel</code> to be used
     */
    public JColorChooser(ColorSelectionModel model) {
        selectionModel = model;
        updateUI();
        dragEnabled = false;
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the <code>ColorChooserUI</code> object that renders
     *          this component
     */
    public ColorChooserUI getUI() {
        return (ColorChooserUI)ui;
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui  the <code>ColorChooserUI</code> L&amp;F object
     */
    public void setUI(ColorChooserUI ui) {
        super.setUI(ui);
    }

    /**
     * Notification from the <code>UIManager</code> that the L&amp;F has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     */
    public void updateUI() {
        ColorChooserUI delegate = null;
        try {
            javax.swing.plaf.ComponentUI candidate = UIManager.getUI(this);
            if (candidate instanceof ColorChooserUI) delegate = (ColorChooserUI) candidate;
        } catch (LinkageError e) {
            // A look and feel whose delegate extends a class the shadow lacks
            // must not make the chooser unconstructible; Basic stands in.
        }
        setUI(delegate != null ? delegate
              : (ColorChooserUI) javax.swing.plaf.basic.BasicColorChooserUI.createUI(this));
    }

    /**
     * Returns the name of the L&amp;F class that renders this component.
     *
     * @return the string "ColorChooserUI"
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Gets the current color value from the color chooser.
     * By default, this delegates to the model.
     *
     * @return the current color value of the color chooser
     */
    public Color getColor() {
        return selectionModel.getSelectedColor();
    }

    /**
     * Sets the current color of the color chooser to the specified color.
     * The <code>ColorSelectionModel</code> will fire a <code>ChangeEvent</code>
     *
     * @param color the color to be set in the color chooser
     */
    public void setColor(Color color) {
        selectionModel.setSelectedColor(color);
    }

    /**
     * Sets the current color of the color chooser to the
     * specified RGB color.  Note that the values of red, green,
     * and blue should be between the numbers 0 and 255, inclusive.
     *
     * @param r   an int specifying the amount of Red
     * @param g   an int specifying the amount of Green
     * @param b   an int specifying the amount of Blue
     * @exception IllegalArgumentException if r,g,b values are out of range
     */
    public void setColor(int r, int g, int b) {
        setColor(new Color(r,g,b));
    }

    /**
     * Sets the current color of the color chooser to the
     * specified color.
     *
     * @param c an integer value that sets the current color in the chooser
     *          where the low-order 8 bits specify the Blue value,
     *          the next 8 bits specify the Green value, and the 8 bits
     *          above that specify the Red value.
     */
    public void setColor(int c) {
        setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    /**
     * Sets the <code>dragEnabled</code> property,
     * which must be <code>true</code> to enable
     * automatic drag handling (the first part of drag and drop)
     * on this component.
     * The <code>transferHandler</code> property needs to be set
     * to a non-<code>null</code> value for the drag to do
     * anything.  The default value of the <code>dragEnabled</code>
     * property
     * is <code>false</code>.
     *
     * @param b the value to set the <code>dragEnabled</code> property to
     * @since 1.4
     */
    public void setDragEnabled(boolean b) {
        // No HeadlessException: the shadow's GraphicsEnvironment always reports
        // headless, yet the chooser is touch-driven (JTree and JList do the same).
        dragEnabled = b;
    }

    /**
     * Gets the value of the <code>dragEnabled</code> property.
     *
     * @return  the value of the <code>dragEnabled</code> property
     * @see #setDragEnabled
     * @since 1.4
     */
    public boolean getDragEnabled() {
        return dragEnabled;
    }

    /**
     * Sets the current preview panel.
     * This will fire a <code>PropertyChangeEvent</code> for the property
     * named "previewPanel".
     *
     * @param preview the <code>JComponent</code> which displays the current color
     */
    public void setPreviewPanel(JComponent preview) {

        if (previewPanel != preview) {
            JComponent oldPreview = previewPanel;
            previewPanel = preview;
            firePropertyChange(JColorChooser.PREVIEW_PANEL_PROPERTY, oldPreview, preview);
        }
    }

    /**
     * Returns the preview panel that shows a chosen color.
     *
     * @return a <code>JComponent</code> object -- the preview panel
     */
    public JComponent getPreviewPanel() {
         return previewPanel;
    }

    /**
     * Adds a color chooser panel to the color chooser.
     *
     * @param panel the <code>AbstractColorChooserPanel</code> to be added
     */
    public void addChooserPanel( AbstractColorChooserPanel panel ) {
        AbstractColorChooserPanel[] oldPanels = getChooserPanels();
        AbstractColorChooserPanel[] newPanels = new AbstractColorChooserPanel[oldPanels.length+1];
        System.arraycopy(oldPanels, 0, newPanels, 0, oldPanels.length);
        newPanels[newPanels.length-1] = panel;
        setChooserPanels(newPanels);
    }

    /**
     * Removes the Color Panel specified.
     *
     * @param panel   a string that specifies the panel to be removed
     * @return the color panel
     * @exception IllegalArgumentException if panel is not in list of
     *                  known chooser panels
     */
    public AbstractColorChooserPanel removeChooserPanel( AbstractColorChooserPanel panel ) {


        int containedAt = -1;

        for (int i = 0; i < chooserPanels.length; i++) {
            if (chooserPanels[i] == panel) {
                containedAt = i;
                break;
            }
        }
        if (containedAt == -1) {
            throw new IllegalArgumentException("chooser panel not in this chooser");
        }

        AbstractColorChooserPanel[] newArray = new AbstractColorChooserPanel[chooserPanels.length-1];

        if (containedAt == chooserPanels.length-1) {  // at end
            System.arraycopy(chooserPanels, 0, newArray, 0, newArray.length);
        }
        else if (containedAt == 0) {  // at start
            System.arraycopy(chooserPanels, 1, newArray, 0, newArray.length);
        }
        else {  // in middle
            System.arraycopy(chooserPanels, 0, newArray, 0, containedAt);
            System.arraycopy(chooserPanels, containedAt+1,
                             newArray, containedAt, (chooserPanels.length - containedAt - 1));
        }

        setChooserPanels(newArray);

        return panel;
    }


    /**
     * Specifies the Color Panels used to choose a color value.
     *
     * @param panels  an array of <code>AbstractColorChooserPanel</code>
     *          objects
     */
    public void setChooserPanels( AbstractColorChooserPanel[] panels) {
        AbstractColorChooserPanel[] oldValue = chooserPanels;
        chooserPanels = panels;
        firePropertyChange(CHOOSER_PANELS_PROPERTY, oldValue, panels);
    }

    /**
     * Returns the specified color panels.
     *
     * @return an array of <code>AbstractColorChooserPanel</code> objects
     */
    public AbstractColorChooserPanel[] getChooserPanels() {
        return chooserPanels;
    }

    /**
     * Returns the data model that handles color selections.
     *
     * @return a <code>ColorSelectionModel</code> object
     */
    public ColorSelectionModel getSelectionModel() {
        return selectionModel;
    }


    /**
     * Sets the model containing the selected color.
     *
     * @param newModel   the new <code>ColorSelectionModel</code> object
     */
    public void setSelectionModel(ColorSelectionModel newModel ) {
        ColorSelectionModel oldModel = selectionModel;
        selectionModel = newModel;
        firePropertyChange(JColorChooser.SELECTION_MODEL_PROPERTY, oldModel, newModel);
    }

    /**
     * Returns a string representation of this <code>JColorChooser</code>.
     * This method
     * is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not
     * be <code>null</code>.
     *
     * @return  a string representation of this <code>JColorChooser</code>
     */
    protected String paramString() {
        StringBuffer chooserPanelsString = new StringBuffer("");
        for (int i=0; i<chooserPanels.length; i++) {
            chooserPanelsString.append("[" + chooserPanels[i].toString()
                                       + "]");
        }
        String previewPanelString = (previewPanel != null ?
                                     previewPanel.toString() : "");

        return super.paramString() +
        ",chooserPanels=" + chooserPanelsString.toString() +
        ",previewPanel=" + previewPanelString;
    }

/////////////////
// Accessibility support
////////////////

    /**
     * Gets the AccessibleContext associated with this JColorChooser.
     * For color choosers, the AccessibleContext takes the form of an
     * AccessibleJColorChooser.
     * A new AccessibleJColorChooser instance is created if necessary.
     *
     * @return an AccessibleJColorChooser that serves as the
     *         AccessibleContext of this JColorChooser
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJColorChooser();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JColorChooser</code> class.  It provides an implementation of the
     * Java Accessibility API appropriate to color chooser user-interface
     * elements.
     */
    protected class AccessibleJColorChooser extends AccessibleJComponent {

        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.COLOR_CHOOSER;
        }

    } // inner class AccessibleJColorChooser
}


/*
 * Class which builds a color chooser dialog consisting of
 * a JColorChooser with "Ok", "Cancel", and "Reset" buttons.
 *
 * Note: This needs to be fixed to deal with localization!
 */
@SuppressWarnings("serial") // Superclass is not serializable across versions
class ColorChooserDialog extends JDialog {
    private Color initialColor;
    private JColorChooser chooserPane;
    private JButton cancelButton;

    public ColorChooserDialog(Dialog owner, String title, boolean modal,
        Component c, JColorChooser chooserPane,
        ActionListener okListener, ActionListener cancelListener)
        throws HeadlessException {
        super(owner, title, modal);
        initColorChooserDialog(c, chooserPane, okListener, cancelListener);
    }

    public ColorChooserDialog(Frame owner, String title, boolean modal,
        Component c, JColorChooser chooserPane,
        ActionListener okListener, ActionListener cancelListener)
        throws HeadlessException {
        super(owner, title, modal);
        initColorChooserDialog(c, chooserPane, okListener, cancelListener);
    }

    protected void initColorChooserDialog(Component c, JColorChooser chooserPane,
        ActionListener okListener, ActionListener cancelListener) {
        //setResizable(false);

        this.chooserPane = chooserPane;

        Locale locale = getLocale();
        String okString = text("ColorChooser.okText", locale, "OK");
        String cancelString = text("ColorChooser.cancelText", locale, "Cancel");
        String resetString = text("ColorChooser.resetText", locale, "Reset");

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(chooserPane, BorderLayout.CENTER);

        /*
         * Create Lower button panel
         */
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
        JButton okButton = new JButton(okString);
        getRootPane().setDefaultButton(okButton);
        okButton.getAccessibleContext().setAccessibleDescription(okString);
        okButton.setActionCommand("OK");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        if (okListener != null) {
            okButton.addActionListener(okListener);
        }
        buttonPane.add(okButton);

        cancelButton = new JButton(cancelString);
        cancelButton.getAccessibleContext().setAccessibleDescription(cancelString);

        // The following few lines are used to register esc to close the dialog
        @SuppressWarnings("serial") // anonymous class
        Action cancelKeyAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                ((AbstractButton)e.getSource()).fireActionPerformed(e);
            }
        };
        KeyStroke cancelKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        InputMap inputMap = cancelButton.getInputMap(JComponent.
                                                    WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = cancelButton.getActionMap();
        if (inputMap != null && actionMap != null) {
            inputMap.put(cancelKeyStroke, "cancel");
            actionMap.put("cancel", cancelKeyAction);
        }
        // end esc handling

        cancelButton.setActionCommand("cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        if (cancelListener != null) {
            cancelButton.addActionListener(cancelListener);
        }
        buttonPane.add(cancelButton);

        JButton resetButton = new JButton(resetString);
        resetButton.getAccessibleContext().setAccessibleDescription(resetString);
        resetButton.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               reset();
           }
        });
        int mnemonic = mnemonic("ColorChooser.resetMnemonic", locale);
        if (mnemonic != -1) {
            resetButton.setMnemonic(mnemonic);
        }
        buttonPane.add(resetButton);
        contentPane.add(buttonPane, BorderLayout.SOUTH);

        if (JDialog.isDefaultLookAndFeelDecorated()) {
            boolean supportsWindowDecorations =
                UIManager.getLookAndFeel().getSupportsWindowDecorations();
            if (supportsWindowDecorations) {
                getRootPane().setWindowDecorationStyle(JRootPane.COLOR_CHOOSER_DIALOG);
            }
        }
        applyComponentOrientation(((c == null) ? getRootPane() : c).getComponentOrientation());

        pack();
        setLocationRelativeTo(c);

        this.addWindowListener(new Closer());
    }

    private static String text(String key, Locale locale, String fallback) {
        String value = UIManager.getString(key, locale);
        // The JDK's basic.properties texts are not bundled with every look and feel.
        return value != null ? value : fallback;
    }

    private static int mnemonic(String key, Locale locale) {
        Object value = UIManager.get(key, locale);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
            }
        }
        return -1;
    }

    // The JDK overrides show(); the shadow's Window.show() delegates to
    // setVisible, so the initial color is remembered here for both entry points.
    @Override
    public void setVisible(boolean visible) {
        if (visible && !isVisible()) {
            initialColor = chooserPane.getColor();
        }
        super.setVisible(visible);
    }

    public void reset() {
        chooserPane.setColor(initialColor);
    }

    class Closer extends WindowAdapter implements Serializable{
        public void windowClosing(WindowEvent e) {
            cancelButton.doClick(0);
            Window w = e.getWindow();
            w.setVisible(false);
        }
    }

    static class DisposeOnClose extends java.awt.event.ComponentAdapter implements Serializable{
        public void componentHidden(java.awt.event.ComponentEvent e) {
            Window w = (Window)e.getComponent();
            w.dispose();
        }
    }

}

@SuppressWarnings("serial") // JDK-implementation class
class ColorTracker implements ActionListener, Serializable {
    JColorChooser chooser;
    Color color;

    public ColorTracker(JColorChooser c) {
        chooser = c;
    }

    public void actionPerformed(ActionEvent e) {
        color = chooser.getColor();
    }

    public Color getColor() {
        return color;
    }
}
