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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.Locale;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.plaf.OptionPaneUI;

/**
 * <code>JOptionPane</code> makes it easy to pop up a standard dialog box that
 * prompts users for a value or informs them of something.
 * <p>
 * A pane built with one of the constructors is a real component: its
 * {@code OptionPaneUI} lays out the message, icon, input component and button
 * bar, a button press sets {@link #getValue value} (and, for an input pane,
 * {@link #getInputValue inputValue}), and {@link #createDialog} wraps it in a
 * modal {@code JDialog} that hides once a value is chosen, as on the JDK.
 * <p>
 * The static {@code show*Dialog} and {@code showInternal*Dialog} methods do
 * not build a Swing dialog on Android: they hand the message to the host
 * bridge installed with {@link #setDialogHandler} / {@link #setInputHandler},
 * which presents a native dialog and blocks the calling thread until it is
 * answered. Internal (desktop-pane) variants present the same way, because
 * the host has no desktop pane to place an internal frame in. Without a
 * host every dialog answers as if it were closed ({@code CLOSED_OPTION}, or
 * {@code null} for input), which is what a dismissed JDK dialog returns.
 *
 * @author James Gosling
 * @author Scott Violet
 */
public class JOptionPane extends JComponent implements javax.accessibility.Accessible {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJOptionPane();
        }
        return accessibleContext;
    }

    /** Accessibility support for option panes: role OPTION_PANE. */
    protected class AccessibleJOptionPane extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.OPTION_PANE;
        }
    }

    private static final long serialVersionUID = 1L;

    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "OptionPaneUI";

    /**
     * Indicates that the user has not yet selected a value.
     */
    public static final Object      UNINITIALIZED_VALUE = "uninitializedValue";

    //
    // Option types
    //

    /**
     * Type meaning Look and Feel should not supply any options -- only
     * use the options from the <code>JOptionPane</code>.
     */
    public static final int         DEFAULT_OPTION = -1;
    /** Type used for <code>showConfirmDialog</code>. */
    public static final int         YES_NO_OPTION = 0;
    /** Type used for <code>showConfirmDialog</code>. */
    public static final int         YES_NO_CANCEL_OPTION = 1;
    /** Type used for <code>showConfirmDialog</code>. */
    public static final int         OK_CANCEL_OPTION = 2;

    //
    // Return values.
    //
    /** Return value from class method if YES is chosen. */
    public static final int         YES_OPTION = 0;
    /** Return value from class method if NO is chosen. */
    public static final int         NO_OPTION = 1;
    /** Return value from class method if CANCEL is chosen. */
    public static final int         CANCEL_OPTION = 2;
    /** Return value form class method if OK is chosen. */
    public static final int         OK_OPTION = 0;
    /** Return value from class method if user closes window without selecting
     * anything, more than likely this should be treated as either a
     * <code>CANCEL_OPTION</code> or <code>NO_OPTION</code>. */
    public static final int         CLOSED_OPTION = -1;

    //
    // Message types. Used by the UI to determine what icon to display,
    // and possibly what behavior to give based on the type.
    //
    /** Used for error messages. */
    public static final int  ERROR_MESSAGE = 0;
    /** Used for information messages. */
    public static final int  INFORMATION_MESSAGE = 1;
    /** Used for warning messages. */
    public static final int  WARNING_MESSAGE = 2;
    /** Used for questions. */
    public static final int  QUESTION_MESSAGE = 3;
    /** No icon is used. */
    public static final int   PLAIN_MESSAGE = -1;

    /** Bound property name for <code>icon</code>. */
    public static final String      ICON_PROPERTY = "icon";
    /** Bound property name for <code>message</code>. */
    public static final String      MESSAGE_PROPERTY = "message";
    /** Bound property name for <code>value</code>. */
    public static final String      VALUE_PROPERTY = "value";
    /** Bound property name for <code>option</code>. */
    public static final String      OPTIONS_PROPERTY = "options";
    /** Bound property name for <code>initialValue</code>. */
    public static final String      INITIAL_VALUE_PROPERTY = "initialValue";
    /** Bound property name for <code>type</code>. */
    public static final String      MESSAGE_TYPE_PROPERTY = "messageType";
    /** Bound property name for <code>optionType</code>. */
    public static final String      OPTION_TYPE_PROPERTY = "optionType";
    /** Bound property name for <code>selectionValues</code>. */
    public static final String      SELECTION_VALUES_PROPERTY = "selectionValues";
    /** Bound property name for <code>initialSelectionValue</code>. */
    public static final String      INITIAL_SELECTION_VALUE_PROPERTY = "initialSelectionValue";
    /** Bound property name for <code>inputValue</code>. */
    public static final String      INPUT_VALUE_PROPERTY = "inputValue";
    /** Bound property name for <code>wantsInput</code>. */
    public static final String      WANTS_INPUT_PROPERTY = "wantsInput";

    /** Icon used in pane. */
    transient protected Icon                  icon;
    /** Message to display. */
    transient protected Object                message;
    /** Options to display to the user. */
    transient protected Object[]              options;
    /** Value that should be initially selected in <code>options</code>. */
    transient protected Object                initialValue;
    /** Message type. */
    protected int                   messageType;
    /**
     * Option type, one of <code>DEFAULT_OPTION</code>,
     * <code>YES_NO_OPTION</code>,
     * <code>YES_NO_CANCEL_OPTION</code> or
     * <code>OK_CANCEL_OPTION</code>.
     */
    protected int                   optionType;
    /** Currently selected value, will be a valid option, or
     * <code>UNINITIALIZED_VALUE</code> or <code>null</code>. */
    transient protected Object                value;
    /** Array of values the user can choose from. Look and feel will
     * provide the UI component to choose this from. */
    protected transient Object[]              selectionValues;
    /** Value the user has input. */
    protected transient Object                inputValue;
    /** Initial value to select in <code>selectionValues</code>. */
    protected transient Object                initialSelectionValue;
    /** If true, a UI widget will be provided to the user to get input. */
    protected boolean                         wantsInput;

    /**
     * The host bridge for confirm, message and option dialogs (shadow only).
     * {@code show} answers with the chosen option: {@code YES_OPTION}/{@code
     * OK_OPTION}, {@code NO_OPTION}, {@code CANCEL_OPTION}, the index into
     * {@code options} when they are given, or {@code CLOSED_OPTION}.
     */
    public interface DialogHandler { int show(Object message, String title, int optionType, Object[] options); }
    /**
     * The host bridge for input dialogs (shadow only). {@code choices} is a
     * copy of the caller's selection values or {@code null} for free text;
     * {@code show} answers with the entered text, the chosen element or
     * {@code null} when cancelled.
     */
    public interface InputHandler { Object show(Object message, String title, Object[] choices, Object initialValue); }
    private static volatile DialogHandler dialogHandler;
    private static volatile InputHandler inputHandler;
    /** Installs the host bridge for confirm, message and option dialogs (shadow only). */
    public static void setDialogHandler(DialogHandler handler) { dialogHandler = handler; }
    /** Installs the host bridge for input dialogs (shadow only). */
    public static void setInputHandler(InputHandler handler) { inputHandler = handler; }

    private static int show(Object message, String title, int optionType, Object[] options) {
        DialogHandler handler = dialogHandler;
        return handler == null ? CLOSED_OPTION : handler.show(message, title, optionType, options);
    }

    private static Object input(Object message, String title, Object[] selectionValues, Object initialSelectionValue) {
        InputHandler handler = inputHandler;
        return handler == null ? null : handler.show(message, title,
            selectionValues == null ? null : selectionValues.clone(), initialSelectionValue);
    }

    /** The look and feel's default dialog title, English when it has none. */
    private static String defaultTitle(String key, String fallback) {
        String title = UIManager.getString(key, Locale.getDefault());
        return title == null ? fallback : title;
    }

    private static String inputTitle() { return defaultTitle("OptionPane.inputDialogTitle", "Input"); }
    private static String messageTitle() { return defaultTitle("OptionPane.messageDialogTitle", "Message"); }
    private static String confirmTitle() { return defaultTitle("OptionPane.titleText", "Select an Option"); }

    /**
     * Shows a question-message dialog requesting input from the user. The
     * dialog uses the default frame, which usually means it is centered on
     * the screen.
     *
     * @param message the <code>Object</code> to display
     * @exception HeadlessException if
     *   <code>GraphicsEnvironment.isHeadless</code> returns
     *   <code>true</code>
     */
    public static String showInputDialog(Object message)
        throws HeadlessException {
        return showInputDialog(null, message);
    }

    /**
     * Shows a question-message dialog requesting input from the user, with
     * the input value initialized to <code>initialSelectionValue</code>.
     *
     * @param message the <code>Object</code> to display
     * @param initialSelectionValue the value used to initialize the input
     *                 field
     * @since 1.4
     */
    public static String showInputDialog(Object message, Object initialSelectionValue) {
        return showInputDialog(null, message, initialSelectionValue);
    }

    /**
     * Shows a question-message dialog requesting input from the user
     * parented to <code>parentComponent</code>.
     *
     * @param parentComponent  the parent <code>Component</code> for the
     *          dialog
     * @param message  the <code>Object</code> to display
     */
    public static String showInputDialog(Component parentComponent,
        Object message) throws HeadlessException {
        return showInputDialog(parentComponent, message, inputTitle(), QUESTION_MESSAGE);
    }

    /**
     * Shows a question-message dialog requesting input from the user and
     * parented to <code>parentComponent</code>. The input value will be
     * initialized to <code>initialSelectionValue</code>.
     *
     * @param parentComponent  the parent <code>Component</code> for the
     *          dialog
     * @param message the <code>Object</code> to display
     * @param initialSelectionValue the value used to initialize the input
     *                 field
     * @since 1.4
     */
    public static String showInputDialog(Component parentComponent, Object message,
                                         Object initialSelectionValue) {
        return (String)showInputDialog(parentComponent, message,
                      inputTitle(), QUESTION_MESSAGE, null, null,
                      initialSelectionValue);
    }

    /**
     * Shows a dialog requesting input from the user parented to
     * <code>parentComponent</code> with the dialog having the title
     * <code>title</code> and message type <code>messageType</code>.
     *
     * @param parentComponent  the parent <code>Component</code> for the
     *                  dialog
     * @param message  the <code>Object</code> to display
     * @param title    the <code>String</code> to display in the dialog
     *                  title bar
     * @param messageType the type of message that is to be displayed
     */
    public static String showInputDialog(Component parentComponent,
        Object message, String title, int messageType)
        throws HeadlessException {
        return (String)showInputDialog(parentComponent, message, title,
                                       messageType, null, null, null);
    }

    /**
     * Prompts the user for input in a blocking dialog where the
     * initial selection, possible selections, and all other options can
     * be specified. The user will able to choose from
     * <code>selectionValues</code>, where <code>null</code> implies the
     * user can input whatever they wish, usually by means of a
     * <code>JTextField</code>.
     *
     * @param parentComponent  the parent <code>Component</code> for the
     *                  dialog
     * @param message  the <code>Object</code> to display
     * @param title    the <code>String</code> to display in the
     *                  dialog title bar
     * @param messageType the type of message to be displayed
     * @param icon     the <code>Icon</code> image to display
     * @param selectionValues an array of <code>Object</code>s that
     *                  gives the possible selections
     * @param initialSelectionValue the value used to initialize the input
     *                 field
     * @return user's input, or <code>null</code> meaning the user
     *                  canceled the input
     */
    public static Object showInputDialog(Component parentComponent,
        Object message, String title, int messageType, Icon icon,
        Object[] selectionValues, Object initialSelectionValue)
        throws HeadlessException {
        Object value = input(message, title, selectionValues, initialSelectionValue);
        // As the JDK: free text comes back as the String the user typed.
        return selectionValues == null && value != null ? value.toString() : value;
    }

    /**
     * Brings up an information-message dialog titled "Message".
     *
     * @param parentComponent determines the <code>Frame</code> in
     *          which the dialog is displayed
     * @param message   the <code>Object</code> to display
     */
    public static void showMessageDialog(Component parentComponent,
        Object message) throws HeadlessException {
        showMessageDialog(parentComponent, message, messageTitle(), INFORMATION_MESSAGE);
    }

    /**
     * Brings up a dialog that displays a message using a default
     * icon determined by the <code>messageType</code> parameter.
     */
    public static void showMessageDialog(Component parentComponent,
        Object message, String title, int messageType)
        throws HeadlessException {
        showMessageDialog(parentComponent, message, title, messageType, null);
    }

    /**
     * Brings up a dialog displaying a message, specifying all parameters.
     */
    public static void showMessageDialog(Component parentComponent,
        Object message, String title, int messageType, Icon icon)
        throws HeadlessException {
        showOptionDialog(parentComponent, message, title, DEFAULT_OPTION,
                         messageType, icon, null, null);
    }

    /**
     * Brings up a dialog with the options <i>Yes</i>,
     * <i>No</i> and <i>Cancel</i>; with the
     * title, <b>Select an Option</b>.
     *
     * @return an integer indicating the option selected by the user
     */
    public static int showConfirmDialog(Component parentComponent,
        Object message) throws HeadlessException {
        return showConfirmDialog(parentComponent, message, confirmTitle(), YES_NO_CANCEL_OPTION);
    }

    /**
     * Brings up a dialog where the number of choices is determined
     * by the <code>optionType</code> parameter.
     *
     * @return an int indicating the option selected by the user
     */
    public static int showConfirmDialog(Component parentComponent,
        Object message, String title, int optionType)
        throws HeadlessException {
        return showConfirmDialog(parentComponent, message, title, optionType,
                                 QUESTION_MESSAGE);
    }

    /**
     * Brings up a dialog where the number of choices is determined
     * by the <code>optionType</code> parameter, where the
     * <code>messageType</code> parameter determines the icon to display.
     *
     * @return an integer indicating the option selected by the user
     */
    public static int showConfirmDialog(Component parentComponent,
        Object message, String title, int optionType, int messageType)
        throws HeadlessException {
        return showConfirmDialog(parentComponent, message, title, optionType,
                                messageType, null);
    }

    /**
     * Brings up a dialog with a specified icon, where the number of
     * choices is determined by the <code>optionType</code> parameter.
     *
     * @return an int indicating the option selected by the user
     */
    public static int showConfirmDialog(Component parentComponent,
        Object message, String title, int optionType,
        int messageType, Icon icon) throws HeadlessException {
        return showOptionDialog(parentComponent, message, title, optionType,
                                messageType, icon, null, null);
    }

    /**
     * Brings up a dialog with a specified icon, where the initial
     * choice is determined by the <code>initialValue</code> parameter and
     * the number of choices is determined by the <code>optionType</code>
     * parameter.
     *
     * @return an integer indicating the option chosen by the user,
     *                  or <code>CLOSED_OPTION</code> if the user closed
     *                  the dialog
     */
    public static int showOptionDialog(Component parentComponent,
        Object message, String title, int optionType, int messageType,
        Icon icon, Object[] options, Object initialValue)
        throws HeadlessException {
        return show(message, title, optionType, options);
    }

    /**
     * Creates and returns a new <code>JDialog</code> wrapping
     * <code>this</code> centered on the <code>parentComponent</code>
     * in the <code>parentComponent</code>'s frame.
     * <code>title</code> is the title of the returned dialog.
     * The returned <code>JDialog</code> will not be resizable by the
     * user, however programs can invoke <code>setResizable</code> on
     * the <code>JDialog</code> instance to change this property.
     * The returned <code>JDialog</code> will be set up such that
     * once it is closed, or the user clicks on one of the buttons,
     * the optionpane's value property will be set accordingly and
     * the dialog will be closed.  Each time the dialog is made visible,
     * it will reset the option pane's value property to
     * <code>JOptionPane.UNINITIALIZED_VALUE</code> to ensure the
     * user's subsequent action closes the dialog properly.
     *
     * @param parentComponent determines the frame in which the dialog
     *          is displayed; if the <code>parentComponent</code> has
     *          no <code>Frame</code>, a default <code>Frame</code> is used
     * @param title     the title string for the dialog
     * @return a new <code>JDialog</code> containing this instance
     */
    public JDialog createDialog(Component parentComponent, String title)
        throws HeadlessException {
        int style = styleFromMessageType(getMessageType());
        return createDialog(parentComponent, title, style);
    }

    /**
     * Creates and returns a new parentless <code>JDialog</code>
     * with the specified title.
     *
     * @param title     the title string for the dialog
     * @return a new <code>JDialog</code> containing this instance
     * @since 1.6
     */
    public JDialog createDialog(String title) throws HeadlessException {
        int style = styleFromMessageType(getMessageType());
        JDialog dialog = new JDialog((Dialog) null, title, true);
        initDialog(dialog, style, null);
        return dialog;
    }

    private JDialog createDialog(Component parentComponent, String title,
            int style)
            throws HeadlessException {

        final JDialog dialog;

        Window window = JOptionPane.getWindowForComponent(parentComponent);
        if (window instanceof Frame) {
            dialog = new JDialog((Frame)window, title, true);
        } else {
            dialog = new JDialog((Dialog)window, title, true);
        }
        initDialog(dialog, style, parentComponent);
        return dialog;
    }

    private void initDialog(final JDialog dialog, int style, Component parentComponent) {
        dialog.setComponentOrientation(this.getComponentOrientation());
        Container contentPane = dialog.getContentPane();

        contentPane.setLayout(new BorderLayout());
        contentPane.add(this, BorderLayout.CENTER);
        dialog.setResizable(false);
        if (JDialog.isDefaultLookAndFeelDecorated()) {
            boolean supportsWindowDecorations =
              UIManager.getLookAndFeel().getSupportsWindowDecorations();
            if (supportsWindowDecorations) {
                dialog.setUndecorated(true);
                dialog.getRootPane().setWindowDecorationStyle(style);
            }
        }
        dialog.pack();
        dialog.setLocationRelativeTo(parentComponent);

        final PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // Let the defaultCloseOperation handle the closing
                // if the user closed the window without selecting a button
                // (newValue = null in that case).  Otherwise, close the dialog.
                if (dialog.isVisible() && event.getSource() == JOptionPane.this &&
                        (event.getPropertyName().equals(VALUE_PROPERTY)) &&
                        event.getNewValue() != null &&
                        event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                    dialog.setVisible(false);
                }
            }
        };

        WindowAdapter adapter = new WindowAdapter() {
            private boolean gotFocus = false;
            public void windowClosing(WindowEvent we) {
                setValue(null);
            }

            public void windowClosed(WindowEvent e) {
                removePropertyChangeListener(listener);
                dialog.getContentPane().removeAll();
            }

            public void windowGainedFocus(WindowEvent we) {
                // Once window gets focus, set initial focus
                if (!gotFocus) {
                    selectInitialValue();
                    gotFocus = true;
                }
            }
        };
        dialog.addWindowListener(adapter);
        dialog.addWindowFocusListener(adapter);
        dialog.addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                // reset value to ensure closing works properly
                setValue(JOptionPane.UNINITIALIZED_VALUE);
            }
        });

        addPropertyChangeListener(listener);
    }

    /**
     * Brings up an internal confirmation dialog panel. The dialog
     * is a information-message dialog titled "Message".
     */
    public static void showInternalMessageDialog(Component parentComponent,
                                                 Object message) {
        showInternalMessageDialog(parentComponent, message, messageTitle(), INFORMATION_MESSAGE);
    }

    /**
     * Brings up an internal dialog panel that displays a message
     * using a default icon determined by the <code>messageType</code>
     * parameter.
     */
    public static void showInternalMessageDialog(Component parentComponent,
                                                 Object message, String title,
                                                 int messageType) {
        showInternalMessageDialog(parentComponent, message, title, messageType, null);
    }

    /**
     * Brings up an internal dialog panel displaying a message,
     * specifying all parameters.
     */
    public static void showInternalMessageDialog(Component parentComponent,
                                         Object message,
                                         String title, int messageType,
                                         Icon icon){
        showInternalOptionDialog(parentComponent, message, title, DEFAULT_OPTION,
                                 messageType, icon, null, null);
    }

    /**
     * Brings up an internal dialog panel with the options <i>Yes</i>, <i>No</i>
     * and <i>Cancel</i>; with the title, <b>Select an Option</b>.
     */
    public static int showInternalConfirmDialog(Component parentComponent,
                                                Object message) {
        return showInternalConfirmDialog(parentComponent, message, confirmTitle(), YES_NO_CANCEL_OPTION);
    }

    /**
     * Brings up a internal dialog panel where the number of choices
     * is determined by the <code>optionType</code> parameter.
     */
    public static int showInternalConfirmDialog(Component parentComponent,
                                                Object message, String title,
                                                int optionType) {
        return showInternalConfirmDialog(parentComponent, message, title, optionType,
                                         QUESTION_MESSAGE);
    }

    /**
     * Brings up an internal dialog panel where the number of choices
     * is determined by the <code>optionType</code> parameter, where
     * the <code>messageType</code> parameter determines the icon to display.
     */
    public static int showInternalConfirmDialog(Component parentComponent,
                                        Object message,
                                        String title, int optionType,
                                        int messageType) {
        return showInternalConfirmDialog(parentComponent, message, title, optionType,
                                         messageType, null);
    }

    /**
     * Brings up an internal dialog panel with a specified icon, where
     * the number of choices is determined by the <code>optionType</code>
     * parameter.
     */
    public static int showInternalConfirmDialog(Component parentComponent,
                                        Object message,
                                        String title, int optionType,
                                        int messageType, Icon icon) {
        return showInternalOptionDialog(parentComponent, message, title, optionType,
                                        messageType, icon, null, null);
    }

    /**
     * Brings up an internal dialog panel with a specified icon, where
     * the initial choice is determined by the <code>initialValue</code>
     * parameter and the number of choices is determined by the
     * <code>optionType</code> parameter. Presented by the host bridge,
     * like {@link #showOptionDialog}.
     */
    public static int showInternalOptionDialog(Component parentComponent,
                                       Object message,
                                       String title, int optionType,
                                       int messageType, Icon icon,
                                       Object[] options, Object initialValue) {
        return show(message, title, optionType, options);
    }

    /**
     * Shows an internal question-message dialog requesting input from
     * the user parented to <code>parentComponent</code>.
     */
    public static String showInternalInputDialog(Component parentComponent,
                                                 Object message) {
        return showInternalInputDialog(parentComponent, message, inputTitle(), QUESTION_MESSAGE);
    }

    /**
     * Shows an internal dialog requesting input from the user parented
     * to <code>parentComponent</code> with the dialog having the title
     * <code>title</code> and message type <code>messageType</code>.
     */
    public static String showInternalInputDialog(Component parentComponent,
                             Object message, String title, int messageType) {
        return (String)showInternalInputDialog(parentComponent, message, title,
                                       messageType, null, null, null);
    }

    /**
     * Prompts the user for input in a blocking internal dialog where
     * the initial selection, possible selections, and all other
     * options can be specified. Presented by the host bridge, like
     * {@link #showInputDialog(Component, Object, String, int, Icon, Object[], Object)}.
     */
    public static Object showInternalInputDialog(Component parentComponent,
                                     Object message, String title,
                                     int messageType, Icon icon,
                                     Object[] selectionValues,
                                     Object initialSelectionValue) {
        return showInputDialog(parentComponent, message, title, messageType, icon,
                               selectionValues, initialSelectionValue);
    }

    /**
     * Creates and returns an instance of <code>JInternalFrame</code>.
     * The internal frame is created with the specified title,
     * and wrapping the <code>JOptionPane</code>.
     * The returned <code>JInternalFrame</code> is
     * added to the <code>JDesktopPane</code> ancestor of
     * <code>parentComponent</code>, or components parent if one its
     * ancestors isn't a <code>JDesktopPane</code>,
     * or if <code>parentComponent</code>
     * doesn't have a parent then a <code>RuntimeException</code> is thrown.
     *
     * @param parentComponent  the parent <code>Component</code> for
     *          the internal frame
     * @param title    the <code>String</code> to display in the
     *          frame's title bar
     * @return a <code>JInternalFrame</code> containing a
     *          <code>JOptionPane</code>
     * @exception RuntimeException if <code>parentComponent</code> does
     *          not have a valid parent
     */
    public JInternalFrame createInternalFrame(Component parentComponent,
                                 String title) {
        Container parent =
                JOptionPane.getDesktopPaneForComponent(parentComponent);

        if (parent == null && (parentComponent == null ||
                (parent = parentComponent.getParent()) == null)) {
            throw new RuntimeException("JOptionPane: parentComponent does " +
                    "not have a valid parent");
        }

        // Option dialogs should be closable only
        final JInternalFrame  iFrame = new JInternalFrame(title, false, true,
                                                           false, false);

        iFrame.putClientProperty("JInternalFrame.frameType", "optionDialog");
        iFrame.putClientProperty("JInternalFrame.messageType",
                                 Integer.valueOf(getMessageType()));

        iFrame.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent e) {
                if (getValue() == UNINITIALIZED_VALUE) {
                    setValue(null);
                }
            }
        });
        addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // Let the defaultCloseOperation handle the closing
                // if the user closed the iframe without selecting a button
                // (newValue = null in that case).  Otherwise, close the dialog.
                if (iFrame.isVisible() &&
                        event.getSource() == JOptionPane.this &&
                        event.getPropertyName().equals(VALUE_PROPERTY)) {
                    try {
                        Object obj = iFrame.getClientProperty("JInternalFrame.previousSelection");
                        iFrame.setClosed(true);
                        iFrame.setVisible(false);
                        if (obj instanceof JInternalFrame) {
                            try {
                                ((JInternalFrame) obj).setSelected(true);
                            } catch (PropertyVetoException e) {
                            }
                        }
                    } catch (PropertyVetoException e) {
                    }
                }
            }
        });
        iFrame.getContentPane().add(this, BorderLayout.CENTER);
        if (parent instanceof JDesktopPane) {
            parent.add(iFrame, JLayeredPane.MODAL_LAYER);
        } else {
            parent.add(iFrame, BorderLayout.CENTER);
        }
        Dimension iFrameSize = iFrame.getPreferredSize();
        Dimension rootSize = parent.getSize();
        Dimension parentSize = parentComponent.getSize();

        iFrame.setBounds((rootSize.width - iFrameSize.width) / 2,
                         (rootSize.height - iFrameSize.height) / 2,
                         iFrameSize.width, iFrameSize.height);
        // We want dialog centered relative to its parent component
        Point iFrameCoord =
          SwingUtilities.convertPoint(parentComponent, 0, 0, parent);
        int x = (parentSize.width - iFrameSize.width) / 2 + iFrameCoord.x;
        int y = (parentSize.height - iFrameSize.height) / 2 + iFrameCoord.y;

        // If possible, dialog should be fully visible
        int ovrx = x + iFrameSize.width - rootSize.width;
        int ovry = y + iFrameSize.height - rootSize.height;
        x = Math.max((ovrx > 0? x - ovrx: x), 0);
        y = Math.max((ovry > 0? y - ovry: y), 0);
        iFrame.setBounds(x, y, iFrameSize.width, iFrameSize.height);

        parent.validate();
        try {
            iFrame.setSelected(true);
        } catch (java.beans.PropertyVetoException e) {}

        return iFrame;
    }

    /**
     * Returns the specified component's <code>Frame</code>.
     *
     * @param parentComponent the <code>Component</code> to check for a
     *          <code>Frame</code>
     * @return the <code>Frame</code> that contains the component,
     *          or <code>getRootFrame</code>
     *          if the component is <code>null</code>,
     *          or does not have a valid <code>Frame</code> parent
     */
    public static Frame getFrameForComponent(Component parentComponent)
        throws HeadlessException {
        if (parentComponent == null)
            return getRootFrame();
        if (parentComponent instanceof Frame)
            return (Frame)parentComponent;
        return JOptionPane.getFrameForComponent(parentComponent.getParent());
    }

    /**
     * Returns the specified component's toplevel <code>Frame</code> or
     * <code>Dialog</code>.
     */
    static Window getWindowForComponent(Component parentComponent)
        throws HeadlessException {
        if (parentComponent == null)
            return getRootFrame();
        if (parentComponent instanceof Frame || parentComponent instanceof Dialog)
            return (Window)parentComponent;
        return JOptionPane.getWindowForComponent(parentComponent.getParent());
    }

    /**
     * Returns the specified component's desktop pane.
     *
     * @param parentComponent the <code>Component</code> to check for a
     *          desktop
     * @return the <code>JDesktopPane</code> that contains the component,
     *          or <code>null</code> if the component is <code>null</code>
     *          or does not have an ancestor that is a
     *          <code>JInternalFrame</code>
     */
    public static JDesktopPane getDesktopPaneForComponent(Component parentComponent) {
        if(parentComponent == null)
            return null;
        if(parentComponent instanceof JDesktopPane)
            return (JDesktopPane)parentComponent;
        return getDesktopPaneForComponent(parentComponent.getParent());
    }

    // The JDK keeps these in the AppContext; the shadow has one application context.
    private static volatile Frame rootFrame;
    private static Frame sharedOwnerFrame;

    /**
     * Sets the frame to use for class methods in which a frame is
     * not provided. <code>null</code> restores the shared default frame.
     *
     * @param newRootFrame the default <code>Frame</code> to use
     */
    public static void setRootFrame(Frame newRootFrame) {
        rootFrame = newRootFrame;
    }

    /**
     * Returns the <code>Frame</code> to use for the class methods in
     * which a frame is not provided. Unless set, this is a shared frame
     * that is never shown, the counterpart of the JDK's shared owner frame.
     *
     * @return the default <code>Frame</code> to use
     */
    public static Frame getRootFrame() throws HeadlessException {
        Frame frame = rootFrame;
        if (frame != null) {
            return frame;
        }
        synchronized (JOptionPane.class) {
            if (sharedOwnerFrame == null) {
                sharedOwnerFrame = new Frame();
            }
            return sharedOwnerFrame;
        }
    }

    /**
     * Creates a <code>JOptionPane</code> with a test message.
     */
    public JOptionPane() {
        this("JOptionPane message");
    }

    /**
     * Creates a instance of <code>JOptionPane</code> to display a
     * message using the
     * plain-message message type and the default options delivered by
     * the UI.
     */
    public JOptionPane(Object message) {
        this(message, PLAIN_MESSAGE);
    }

    /**
     * Creates an instance of <code>JOptionPane</code> to display a message
     * with the specified message type and the default options,
     */
    public JOptionPane(Object message, int messageType) {
        this(message, messageType, DEFAULT_OPTION);
    }

    /**
     * Creates an instance of <code>JOptionPane</code> to display a message
     * with the specified message type and options.
     */
    public JOptionPane(Object message, int messageType, int optionType) {
        this(message, messageType, optionType, null);
    }

    /**
     * Creates an instance of <code>JOptionPane</code> to display a message
     * with the specified message type, options, and icon.
     */
    public JOptionPane(Object message, int messageType, int optionType,
                       Icon icon) {
        this(message, messageType, optionType, icon, null);
    }

    /**
     * Creates an instance of <code>JOptionPane</code> to display a message
     * with the specified message type, icon, and options.
     * None of the options is initially selected.
     */
    public JOptionPane(Object message, int messageType, int optionType,
                       Icon icon, Object[] options) {
        this(message, messageType, optionType, icon, options, null);
    }

    /**
     * Creates an instance of <code>JOptionPane</code> to display a message
     * with the specified message type, icon, and options, with the
     * initially-selected option specified.
     *
     * @exception RuntimeException if <code>messageType</code> or
     *  <code>optionType</code> is not one of the defined constants
     */
    public JOptionPane(Object message, int messageType, int optionType,
                       Icon icon, Object[] options, Object initialValue) {

        this.message = message;
        this.options = options;
        this.initialValue = initialValue;
        this.icon = icon;
        setMessageType(messageType);
        setOptionType(optionType);
        value = UNINITIALIZED_VALUE;
        inputValue = UNINITIALIZED_VALUE;
        updateUI();
    }

    /**
     * Sets the UI object which implements the {@literal L&F} for this component.
     *
     * @param ui  the <code>OptionPaneUI</code> {@literal L&F} object
     */
    public void setUI(OptionPaneUI ui) {
        if (this.ui != ui) {
            super.setUI(ui);
            invalidate();
        }
    }

    /**
     * Returns the UI object which implements the {@literal L&F} for this component.
     *
     * @return the <code>OptionPaneUI</code> object
     */
    public OptionPaneUI getUI() {
        return (OptionPaneUI)ui;
    }

    /**
     * Notification from the <code>UIManager</code> that the {@literal L&F} has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     */
    public void updateUI() {
        try {
            javax.swing.plaf.ComponentUI delegate = UIManager.getUI(this);
            setUI(delegate instanceof OptionPaneUI ? (OptionPaneUI) delegate : new javax.swing.plaf.basic.BasicOptionPaneUI());
        } catch (LinkageError incompatible) {
            ui = null;
            setUI(new javax.swing.plaf.basic.BasicOptionPaneUI());
        }
    }

    /**
     * Returns the name of the UI class that implements the
     * {@literal L&F} for this component.
     *
     * @return the string "OptionPaneUI"
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Sets the option pane's message-object.
     *
     * @param newMessage the <code>Object</code> to display
     */
    public void setMessage(Object newMessage) {
        Object           oldMessage = message;

        message = newMessage;
        firePropertyChange(MESSAGE_PROPERTY, oldMessage, message);
    }

    /**
     * Returns the message-object this pane displays.
     *
     * @return the <code>Object</code> that is displayed
     */
    public Object getMessage() {
        return message;
    }

    /**
     * Sets the icon to display. If non-<code>null</code>, the look and feel
     * does not provide an icon.
     *
     * @param newIcon the <code>Icon</code> to display
     */
    public void setIcon(Icon newIcon) {
        Object              oldIcon = icon;

        icon = newIcon;
        firePropertyChange(ICON_PROPERTY, oldIcon, icon);
    }

    /**
     * Returns the icon this pane displays.
     *
     * @return the <code>Icon</code> that is displayed
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Sets the value the user has chosen.
     *
     * @param newValue  the chosen value
     */
    public void setValue(Object newValue) {
        Object               oldValue = value;

        value = newValue;
        firePropertyChange(VALUE_PROPERTY, oldValue, value);
    }

    /**
     * Returns the value the user has selected. <code>UNINITIALIZED_VALUE</code>
     * implies the user has not yet made a choice, <code>null</code> means the
     * user closed the window with out choosing anything. Otherwise
     * the returned value will be one of the options defined in this
     * object.
     *
     * @return the <code>Object</code> chosen by the user,
     *         <code>UNINITIALIZED_VALUE</code>
     *         if the user has not yet made a choice, or <code>null</code> if
     *         the user closed the window without making a choice
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the options this pane displays. If an element in
     * <code>newOptions</code> is a <code>Component</code>
     * it is added directly to the pane,
     * otherwise a button is created for the element.
     *
     * @param newOptions an array of <code>Objects</code> that create the
     *          buttons the user can click on, or arbitrary
     *          <code>Components</code> to add to the pane
     */
    public void setOptions(Object[] newOptions) {
        Object[]           oldOptions = options;

        options = newOptions;
        firePropertyChange(OPTIONS_PROPERTY, oldOptions, options);
    }

    /**
     * Returns the choices the user can make.
     * @return the array of <code>Objects</code> that give the user's choices
     */
    public Object[] getOptions() {
        if(options != null) {
            int             optionCount = options.length;
            Object[]        retOptions = new Object[optionCount];

            System.arraycopy(options, 0, retOptions, 0, optionCount);
            return retOptions;
        }
        return options;
    }

    /**
     * Sets the initial value that is to be enabled -- the
     * <code>Component</code>
     * that has the focus when the pane is initially displayed.
     *
     * @param newInitialValue the <code>Object</code> that gets the initial
     *                         keyboard focus
     */
    public void setInitialValue(Object newInitialValue) {
        Object            oldIV = initialValue;

        initialValue = newInitialValue;
        firePropertyChange(INITIAL_VALUE_PROPERTY, oldIV, initialValue);
    }

    /**
     * Returns the initial value.
     *
     * @return the <code>Object</code> that gets the initial keyboard focus
     */
    public Object getInitialValue() {
        return initialValue;
    }

    /**
     * Sets the option pane's message type.
     * The message type is used by the Look and Feel to determine the
     * icon to display (if not supplied) as well as potentially how to
     * lay out the <code>parentComponent</code>.
     *
     * @param newType the kind of message to be displayed
     * @exception RuntimeException if <code>newType</code> is not one of the
     *          legal values listed above
     */
    public void setMessageType(int newType) {
        checkMessageType(newType);
        int           oldType = messageType;
        messageType = newType;
        firePropertyChange(MESSAGE_TYPE_PROPERTY, oldType, messageType);
    }

    private static void checkMessageType(int newType){
        if (newType != ERROR_MESSAGE && newType != INFORMATION_MESSAGE &&
            newType != WARNING_MESSAGE && newType != QUESTION_MESSAGE &&
            newType != PLAIN_MESSAGE)
            throw new RuntimeException("JOptionPane: type must be one of JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE or JOptionPane.PLAIN_MESSAGE");
    }

    /**
     * Returns the message type.
     *
     * @return an integer specifying the message type
     */
    public int getMessageType() {
        return messageType;
    }

    /**
     * Sets the options to display.
     * The option type is used by the Look and Feel to
     * determine what buttons to show (unless options are supplied).
     *
     * @param newType an integer specifying the options the {@literal L&F} is to display
     * @exception RuntimeException if <code>newType</code> is not one of
     *          the legal values listed above
     */
    public void setOptionType(int newType) {
        if(newType != DEFAULT_OPTION && newType != YES_NO_OPTION &&
           newType != YES_NO_CANCEL_OPTION && newType != OK_CANCEL_OPTION)
            throw new RuntimeException("JOptionPane: option type must be one of JOptionPane.DEFAULT_OPTION, JOptionPane.YES_NO_OPTION, JOptionPane.YES_NO_CANCEL_OPTION or JOptionPane.OK_CANCEL_OPTION");

        int            oldType = optionType;

        optionType = newType;
        firePropertyChange(OPTION_TYPE_PROPERTY, oldType, optionType);
    }

    /**
     * Returns the type of options that are displayed.
     *
     * @return an integer specifying the user-selectable options
     */
    public int getOptionType() {
        return optionType;
    }

    /**
     * Sets the input selection values for a pane that provides the user
     * with a list of items to choose from. (The UI provides a widget
     * for choosing one of the values.)  A <code>null</code> value
     * implies the user can input whatever they wish, usually by means
     * of a <code>JTextField</code>.
     * <p>
     * Sets <code>wantsInput</code> to true. Use
     * <code>setInitialSelectionValue</code> to specify the initially-chosen
     * value. After the pane as been enabled, <code>inputValue</code> is
     * set to the value the user has selected.
     *
     * @param newValues an array of <code>Objects</code> the user to be
     *                  displayed
     *                  (usually in a list or combo-box) from which
     *                  the user can make a selection
     */
    public void setSelectionValues(Object[] newValues) {
        Object[]           oldValues = selectionValues;

        selectionValues = newValues;
        firePropertyChange(SELECTION_VALUES_PROPERTY, oldValues, newValues);
        if(selectionValues != null)
            setWantsInput(true);
    }

    /**
     * Returns the input selection values.
     *
     * @return the array of <code>Objects</code> the user can select
     */
    public Object[] getSelectionValues() {
        return selectionValues;
    }

    /**
     * Sets the input value that is initially displayed as selected to the user.
     * Only used if <code>wantsInput</code> is true.
     *
     * @param newValue the initially selected value
     */
    public void setInitialSelectionValue(Object newValue) {
        Object          oldValue = initialSelectionValue;

        initialSelectionValue = newValue;
        firePropertyChange(INITIAL_SELECTION_VALUE_PROPERTY, oldValue,
                           newValue);
    }

    /**
     * Returns the input value that is displayed as initially selected to the user.
     *
     * @return the initially selected value
     */
    public Object getInitialSelectionValue() {
        return initialSelectionValue;
    }

    /**
     * Sets the input value that was selected or input by the user.
     * Only used if <code>wantsInput</code> is true.  Note that this method
     * is invoked internally by the option pane (in response to user action)
     * and should generally not be called by client programs.  To set the
     * input value initially displayed as selected to the user, use
     * <code>setInitialSelectionValue</code>.
     *
     * @param newValue the <code>Object</code> used to set the
     *          value that the user specified (usually in a text field)
     */
    public void setInputValue(Object newValue) {
        Object              oldValue = inputValue;

        inputValue = newValue;
        firePropertyChange(INPUT_VALUE_PROPERTY, oldValue, newValue);
    }

    /**
     * Returns the value the user has input, if <code>wantsInput</code>
     * is true.
     *
     * @return the <code>Object</code> the user specified,
     *          if it was one of the objects, or a
     *          <code>String</code> if it was a value typed into a
     *          field
     */
    public Object getInputValue() {
        return inputValue;
    }

    /**
     * Returns the maximum number of characters to place on a line in a
     * message. Default is to return <code>Integer.MAX_VALUE</code>.
     * The value can be
     * changed by overriding this method in a subclass.
     *
     * @return an integer giving the maximum number of characters on a line
     */
    public int getMaxCharactersPerLineCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * Sets the <code>wantsInput</code> property.
     * If <code>newValue</code> is true, an input component
     * (such as a text field or combo box) whose parent is
     * <code>parentComponent</code> is provided to
     * allow the user to input a value.
     *
     * @param newValue if true, an input component whose parent is {@code parentComponent}
     *                 is provided to allow the user to input a value.
     */
    public void setWantsInput(boolean newValue) {
        boolean            oldValue = wantsInput;

        wantsInput = newValue;
        firePropertyChange(WANTS_INPUT_PROPERTY, oldValue, newValue);
    }

    /**
     * Returns the value of the <code>wantsInput</code> property.
     *
     * @return true if an input component will be provided
     */
    public boolean getWantsInput() {
        return wantsInput;
    }

    /**
     * Requests that the initial value be selected, which will set
     * focus to the initial value. This method
     * should be invoked after the window containing the option pane
     * is made visible.
     */
    public void selectInitialValue() {
        OptionPaneUI         ui = getUI();
        if (ui != null) {
            ui.selectInitialValue(this);
        }
    }

    private static int styleFromMessageType(int messageType) {
        switch (messageType) {
        case ERROR_MESSAGE:
            return JRootPane.ERROR_DIALOG;
        case QUESTION_MESSAGE:
            return JRootPane.QUESTION_DIALOG;
        case WARNING_MESSAGE:
            return JRootPane.WARNING_DIALOG;
        case INFORMATION_MESSAGE:
            return JRootPane.INFORMATION_DIALOG;
        case PLAIN_MESSAGE:
        default:
            return JRootPane.PLAIN_DIALOG;
        }
    }

    /**
     * Returns a string representation of this <code>JOptionPane</code>.
     * This method
     * is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not
     * be <code>null</code>.
     *
     * @return  a string representation of this <code>JOptionPane</code>
     */
    protected String paramString() {
        String iconString = (icon != null ?
                             icon.toString() : "");
        String initialValueString = (initialValue != null ?
                                     initialValue.toString() : "");
        String messageString = (message != null ?
                                message.toString() : "");
        String messageTypeString;
        if (messageType == ERROR_MESSAGE) {
            messageTypeString = "ERROR_MESSAGE";
        } else if (messageType == INFORMATION_MESSAGE) {
            messageTypeString = "INFORMATION_MESSAGE";
        } else if (messageType == WARNING_MESSAGE) {
            messageTypeString = "WARNING_MESSAGE";
        } else if (messageType == QUESTION_MESSAGE) {
            messageTypeString = "QUESTION_MESSAGE";
        } else if (messageType == PLAIN_MESSAGE)  {
            messageTypeString = "PLAIN_MESSAGE";
        } else messageTypeString = "";
        String optionTypeString;
        if (optionType == DEFAULT_OPTION) {
            optionTypeString = "DEFAULT_OPTION";
        } else if (optionType == YES_NO_OPTION) {
            optionTypeString = "YES_NO_OPTION";
        } else if (optionType == YES_NO_CANCEL_OPTION) {
            optionTypeString = "YES_NO_CANCEL_OPTION";
        } else if (optionType == OK_CANCEL_OPTION) {
            optionTypeString = "OK_CANCEL_OPTION";
        } else optionTypeString = "";
        String wantsInputString = (wantsInput ?
                                   "true" : "false");

        return super.paramString() +
        ",icon=" + iconString +
        ",initialValue=" + initialValueString +
        ",message=" + messageString +
        ",messageType=" + messageTypeString +
        ",optionType=" + optionTypeString +
        ",wantsInput=" + wantsInputString;
    }
}
