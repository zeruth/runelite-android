/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Vector;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.FileChooserUI;

/**
 * <code>JFileChooser</code> provides a simple mechanism for the user to
 * choose a file.
 * <p>
 * On Android the chooser is a real component painted by its UI delegate, so a
 * plugin that embeds one gets a working file list. The modal
 * {@link #showDialog} family is answered by the host's Storage Access
 * Framework bridge when one is installed with {@link #setDialogHandler}: the
 * picked documents come back as files in app storage that read (and, for save
 * and folder picks, write back) through the content resolver. Without a
 * handler the Swing dialog is shown, as on the JDK. Either way the outcome
 * goes through {@link #approveSelection} or {@link #cancelSelection}, so
 * action listeners see the JDK's events.
 *
 * @author Jeff Dinkins
 */
public class JFileChooser extends JComponent implements Accessible {

    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "FileChooserUI";

    // ************************
    // ***** Dialog Types *****
    // ************************

    /**
     * Type value indicating that the <code>JFileChooser</code> supports an
     * "Open" file operation.
     */
    public static final int OPEN_DIALOG = 0;

    /**
     * Type value indicating that the <code>JFileChooser</code> supports a
     * "Save" file operation.
     */
    public static final int SAVE_DIALOG = 1;

    /**
     * Type value indicating that the <code>JFileChooser</code> supports a
     * developer-specified file operation.
     */
    public static final int CUSTOM_DIALOG = 2;


    // ********************************
    // ***** Dialog Return Values *****
    // ********************************

    /**
     * Return value if cancel is chosen.
     */
    public static final int CANCEL_OPTION = 1;

    /**
     * Return value if approve (yes, ok) is chosen.
     */
    public static final int APPROVE_OPTION = 0;

    /**
     * Return value if an error occurred.
     */
    public static final int ERROR_OPTION = -1;


    // **********************************
    // ***** JFileChooser properties *****
    // **********************************


    /** Instruction to display only files. */
    public static final int FILES_ONLY = 0;

    /** Instruction to display only directories. */
    public static final int DIRECTORIES_ONLY = 1;

    /** Instruction to display both files and directories. */
    public static final int FILES_AND_DIRECTORIES = 2;

    /** Instruction to cancel the current selection. */
    public static final String CANCEL_SELECTION = "CancelSelection";

    /**
     * Instruction to approve the current selection
     * (same as pressing yes or ok).
     */
    public static final String APPROVE_SELECTION = "ApproveSelection";

    /** Identifies change in the text on the approve (yes, ok) button. */
    public static final String APPROVE_BUTTON_TEXT_CHANGED_PROPERTY = "ApproveButtonTextChangedProperty";

    /**
     * Identifies change in the tooltip text for the approve (yes, ok)
     * button.
     */
    public static final String APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY = "ApproveButtonToolTipTextChangedProperty";

    /** Identifies change in the mnemonic for the approve (yes, ok) button. */
    public static final String APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY = "ApproveButtonMnemonicChangedProperty";

    /** Instruction to display the control buttons. */
    public static final String CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY = "ControlButtonsAreShownChangedProperty";

    /** Identifies user's directory change. */
    public static final String DIRECTORY_CHANGED_PROPERTY = "directoryChanged";

    /** Identifies change in user's single-file selection. */
    public static final String SELECTED_FILE_CHANGED_PROPERTY = "SelectedFileChangedProperty";

    /** Identifies change in user's multiple-file selection. */
    public static final String SELECTED_FILES_CHANGED_PROPERTY = "SelectedFilesChangedProperty";

    /** Enables multiple-file selections. */
    public static final String MULTI_SELECTION_ENABLED_CHANGED_PROPERTY = "MultiSelectionEnabledChangedProperty";

    /**
     * Says that a different object is being used to find available drives
     * on the system.
     */
    public static final String FILE_SYSTEM_VIEW_CHANGED_PROPERTY = "FileSystemViewChanged";

    /**
     * Says that a different object is being used to retrieve file
     * information.
     */
    public static final String FILE_VIEW_CHANGED_PROPERTY = "fileViewChanged";

    /** Identifies a change in the display-hidden-files property. */
    public static final String FILE_HIDING_CHANGED_PROPERTY = "FileHidingChanged";

    /** User changed the kind of files to display. */
    public static final String FILE_FILTER_CHANGED_PROPERTY = "fileFilterChanged";

    /**
     * Identifies a change in the kind of selection (single,
     * multiple, etc.).
     */
    public static final String FILE_SELECTION_MODE_CHANGED_PROPERTY = "fileSelectionChanged";

    /** Says that a different accessory component is in use. */
    public static final String ACCESSORY_CHANGED_PROPERTY = "AccessoryChangedProperty";

    /** Identifies whether a the AcceptAllFileFilter is used or not. */
    public static final String ACCEPT_ALL_FILE_FILTER_USED_CHANGED_PROPERTY = "acceptAllFileFilterUsedChanged";

    /** Identifies a change in the dialog title. */
    public static final String DIALOG_TITLE_CHANGED_PROPERTY = "DialogTitleChangedProperty";

    /**
     * Identifies a change in the type of files displayed (files only,
     * directories only, or both files and directories).
     */
    public static final String DIALOG_TYPE_CHANGED_PROPERTY = "DialogTypeChangedProperty";

    /**
     * Identifies a change in the list of predefined file filters
     * the user can choose from.
     */
    public static final String CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY = "ChoosableFileFilterChangedProperty";

    // ******************************
    // ***** instance variables *****
    // ******************************

    private String dialogTitle = null;
    private String approveButtonText = null;
    private String approveButtonToolTipText = null;
    private int approveButtonMnemonic = 0;

    private Vector<FileFilter> filters = new Vector<FileFilter>(5);
    private JDialog dialog = null;
    private transient boolean dialogShowing;
    private int dialogType = OPEN_DIALOG;
    private int returnValue = ERROR_OPTION;
    private JComponent accessory = null;

    private FileView fileView = null;

    private boolean controlsShown = true;

    private boolean useFileHiding = true;

    private int fileSelectionMode = FILES_ONLY;

    private boolean multiSelectionEnabled = false;

    private boolean useAcceptAllFileFilter = true;

    private boolean dragEnabled = false;

    private FileFilter fileFilter = null;

    private FileSystemView fileSystemView = null;

    private File currentDirectory = null;
    private File selectedFile = null;
    private File[] selectedFiles;

    private final javax.swing.event.EventListenerList actionListeners =
            new javax.swing.event.EventListenerList();

    /**
     * The host bridge that answers {@link #showDialog} with the system picker.
     * It is not JDK API; the Android app installs it at startup. It runs on
     * the caller's thread, fills the chooser's selection (and current
     * directory) and returns one of the option constants.
     */
    public interface DialogHandler {
        int show(JFileChooser chooser, boolean save);
    }

    private static volatile DialogHandler dialogHandler;

    /** Installs the host's system picker for {@link #showDialog}; null restores the Swing dialog. */
    public static void setDialogHandler(DialogHandler handler) {
        dialogHandler = handler;
    }

    // *************************************
    // ***** JFileChooser Constructors *****
    // *************************************

    /**
     * Constructs a <code>JFileChooser</code> pointing to the user's
     * default directory.
     */
    public JFileChooser() {
        this((File) null, (FileSystemView) null);
    }

    /**
     * Constructs a <code>JFileChooser</code> using the given path.
     * Passing in a <code>null</code>
     * string causes the file chooser to point to the user's default directory.
     *
     * @param currentDirectoryPath  a <code>String</code> giving the path
     *                          to a file or directory
     */
    public JFileChooser(String currentDirectoryPath) {
        this(currentDirectoryPath, (FileSystemView) null);
    }

    /**
     * Constructs a <code>JFileChooser</code> using the given <code>File</code>
     * as the path. Passing in a <code>null</code> file
     * causes the file chooser to point to the user's default directory.
     *
     * @param currentDirectory  a <code>File</code> object specifying
     *                          the path to a file or directory
     */
    public JFileChooser(File currentDirectory) {
        this(currentDirectory, (FileSystemView) null);
    }

    /**
     * Constructs a <code>JFileChooser</code> using the given
     * <code>FileSystemView</code>.
     */
    public JFileChooser(FileSystemView fsv) {
        this((File) null, fsv);
    }


    /**
     * Constructs a <code>JFileChooser</code> using the given current directory
     * and <code>FileSystemView</code>.
     */
    public JFileChooser(File currentDirectory, FileSystemView fsv) {
        setup(fsv);
        setCurrentDirectory(currentDirectory);
    }

    /**
     * Constructs a <code>JFileChooser</code> using the given current directory
     * path and <code>FileSystemView</code>.
     */
    public JFileChooser(String currentDirectoryPath, FileSystemView fsv) {
        setup(fsv);
        if(currentDirectoryPath == null) {
            setCurrentDirectory(null);
        } else {
            setCurrentDirectory(fileSystemView.createFileObject(currentDirectoryPath));
        }
    }

    /**
     * Performs common constructor initialization and setup.
     */
    protected void setup(FileSystemView view) {
        if(view == null) {
            view = FileSystemView.getFileSystemView();
        }
        setFileSystemView(view);
        updateUI();
        if(isAcceptAllFileFilterUsed()) {
            setFileFilter(getAcceptAllFileFilter());
        }
    }

    /**
     * Sets the <code>dragEnabled</code> property, which must be
     * <code>true</code> to enable automatic drag handling.
     *
     * @param b the value to set the <code>dragEnabled</code> property to
     * @since 1.4
     */
    public void setDragEnabled(boolean b) {
        // No HeadlessException: the shadow's GraphicsEnvironment reports
        // headless, yet the chooser is touch-driven (as JColorChooser).
        dragEnabled = b;
    }

    /**
     * Gets the value of the <code>dragEnabled</code> property.
     *
     * @return  the value of the <code>dragEnabled</code> property
     * @since 1.4
     */
    public boolean getDragEnabled() {
        return dragEnabled;
    }

    // *****************************
    // ****** File Operations ******
    // *****************************

    /**
     * Returns the selected file. This can be set either by the
     * programmer via <code>setSelectedFile</code> or by a user action, such as
     * either typing the filename into the UI or selecting the
     * file from a list in the UI.
     *
     * @return the selected file
     */
    public File getSelectedFile() {
        return selectedFile;
    }

    /**
     * Sets the selected file. If the file's parent directory is
     * not the current directory, changes the current directory
     * to be the file's parent directory.
     *
     * @param file the selected file
     */
    public void setSelectedFile(File file) {
        File oldValue = selectedFile;
        selectedFile = file;
        if(selectedFile != null) {
            if (file.isAbsolute() && getFileSystemView().isParent(getCurrentDirectory(), selectedFile)) {
                setCurrentDirectory(selectedFile.getParentFile());
            }
            if (!isMultiSelectionEnabled() || selectedFiles == null || selectedFiles.length == 1) {
                ensureFileIsVisible(selectedFile);
            }
        }
        firePropertyChange(SELECTED_FILE_CHANGED_PROPERTY, oldValue, selectedFile);
    }

    /**
     * Returns a list of selected files if the file chooser is
     * set to allow multiple selection.
     */
    public File[] getSelectedFiles() {
        if(selectedFiles == null) {
            return new File[0];
        } else {
            return selectedFiles.clone();
        }
    }

    /**
     * Sets the list of selected files if the file chooser is
     * set to allow multiple selection.
     */
    public void setSelectedFiles(File[] selectedFiles) {
        File[] oldValue = this.selectedFiles;
        if (selectedFiles == null || selectedFiles.length == 0) {
            selectedFiles = null;
            this.selectedFiles = null;
            setSelectedFile(null);
        } else {
            this.selectedFiles = selectedFiles.clone();
            setSelectedFile(this.selectedFiles[0]);
        }
        firePropertyChange(SELECTED_FILES_CHANGED_PROPERTY, oldValue, selectedFiles);
    }

    /**
     * Returns the current directory.
     *
     * @return the current directory
     * @see #setCurrentDirectory
     */
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    /**
     * Sets the current directory. Passing in <code>null</code> sets the
     * file chooser to point to the user's default directory.
     * If the file passed in as <code>currentDirectory</code> is not a
     * directory, the parent of the file will be used as the currentDirectory.
     * If the parent is not traversable, then it will walk up the parent tree
     * until it finds a traversable directory, or hits the root of the
     * file system.
     *
     * @param dir the current directory to point to
     * @see #getCurrentDirectory
     */
    public void setCurrentDirectory(File dir) {
        File oldValue = currentDirectory;

        if (dir != null && !dir.exists()) {
            dir = currentDirectory;
        }
        if (dir == null) {
            dir = getFileSystemView().getDefaultDirectory();
        }
        if (currentDirectory != null) {
            /* Verify the toString of object */
            if (this.currentDirectory.equals(dir)) {
                return;
            }
        }

        File prev = null;
        while (!isTraversable(dir) && prev != dir) {
            prev = dir;
            dir = getFileSystemView().getParentDirectory(dir);
        }
        currentDirectory = dir;

        firePropertyChange(DIRECTORY_CHANGED_PROPERTY, oldValue, currentDirectory);
    }

    /**
     * Changes the directory to be set to the parent of the
     * current directory.
     *
     * @see #getCurrentDirectory
     */
    public void changeToParentDirectory() {
        selectedFile = null;
        File oldValue = getCurrentDirectory();
        setCurrentDirectory(getFileSystemView().getParentDirectory(oldValue));
    }

    /**
     * Tells the UI to rescan its files list from the current directory.
     */
    public void rescanCurrentDirectory() {
        getUI().rescanCurrentDirectory(this);
    }

    /**
     * Makes sure that the specified file is viewable, and
     * not hidden.
     *
     * @param f  a File object
     */
    public void ensureFileIsVisible(File f) {
        getUI().ensureFileIsVisible(this, f);
    }

    // **************************************
    // ***** JFileChooser Dialog methods *****
    // **************************************

    /**
     * Pops up an "Open File" file chooser dialog. Note that the
     * text that appears in the approve button is determined by
     * the L&amp;F.
     *
     * @param    parent  the parent component of the dialog,
     *                  can be <code>null</code>;
     *                  see <code>showDialog</code> for details
     * @return   the return state of the file chooser on popdown:
     * <ul>
     * <li>JFileChooser.CANCEL_OPTION
     * <li>JFileChooser.APPROVE_OPTION
     * <li>JFileChooser.ERROR_OPTION if an error occurs or the
     *                  dialog is dismissed
     * </ul>
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     */
    public int showOpenDialog(Component parent) throws HeadlessException {
        setDialogType(OPEN_DIALOG);
        return showDialog(parent, null);
    }

    /**
     * Pops up a "Save File" file chooser dialog. Note that the
     * text that appears in the approve button is determined by
     * the L&amp;F.
     *
     * @param    parent  the parent component of the dialog,
     *                  can be <code>null</code>;
     *                  see <code>showDialog</code> for details
     * @return   the return state of the file chooser on popdown
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     */
    public int showSaveDialog(Component parent) throws HeadlessException {
        setDialogType(SAVE_DIALOG);
        return showDialog(parent, null);
    }

    /**
     * Pops a custom file chooser dialog with a custom approve button.
     *
     * @param   parent  the parent component of the dialog;
     *                  can be <code>null</code>
     * @param   approveButtonText the text of the <code>ApproveButton</code>
     * @return  the return state of the file chooser on popdown
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     */
    public int showDialog(Component parent, String approveButtonText)
        throws HeadlessException {
        synchronized (this) {
            if (dialogShowing) return ERROR_OPTION;
            dialogShowing = true;
        }
        try {
            return showDialogImpl(parent, approveButtonText);
        } finally {
            synchronized (this) { dialogShowing = false; }
        }
    }

    private int showDialogImpl(Component parent, String approveButtonText)
        throws HeadlessException {
        if (dialog != null) {
            // Prevent to show second instance of dialog if the previous one still exists
            return JFileChooser.ERROR_OPTION;
        }

        if(approveButtonText != null) {
            setApproveButtonText(approveButtonText);
            setDialogType(CUSTOM_DIALOG);
        }

        DialogHandler handler = dialogHandler;
        // The Android system picker cannot host a Swing accessory panel.
        if (handler != null && getAccessory() == null) {
            returnValue = ERROR_OPTION;
            rescanCurrentDirectory();
            int result = handler.show(this, getDialogType() == SAVE_DIALOG);
            // The system picker stands in for the approve and cancel buttons.
            if (result == APPROVE_OPTION) {
                approveSelection();
            } else if (result == CANCEL_OPTION) {
                cancelSelection();
            }
            return returnValue;
        }

        dialog = createDialog(parent);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                returnValue = CANCEL_OPTION;
            }
        });
        returnValue = ERROR_OPTION;
        rescanCurrentDirectory();

        try {
            dialog.setVisible(true);
            firePropertyChange("JFileChooserDialogIsClosingProperty", dialog, null);
        } finally {
            JDialog showing = dialog;
            dialog = null;
            try { showing.getContentPane().removeAll(); }
            finally { showing.dispose(); }
        }
        return returnValue;
    }

    /**
     * Creates and returns a new <code>JDialog</code> wrapping
     * <code>this</code> centered on the <code>parent</code>
     * in the <code>parent</code>'s frame.
     *
     * @param   parent  the parent component of the dialog;
     *                  can be <code>null</code>
     * @return a new <code>JDialog</code> containing this instance
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @since 1.4
     */
    protected JDialog createDialog(Component parent) throws HeadlessException {
        FileChooserUI ui = getUI();
        String title = ui.getDialogTitle(this);
        putClientProperty(AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY,
                          title);

        JDialog dialog;
        Window window = JOptionPane.getWindowForComponent(parent);
        if (window instanceof Frame) {
            dialog = new JDialog((Frame)window, title, true);
        } else {
            dialog = new JDialog((Dialog)window, title, true);
        }
        dialog.setComponentOrientation(this.getComponentOrientation());

        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(this, BorderLayout.CENTER);

        if (JDialog.isDefaultLookAndFeelDecorated()) {
            boolean supportsWindowDecorations =
            UIManager.getLookAndFeel().getSupportsWindowDecorations();
            if (supportsWindowDecorations) {
                dialog.getRootPane().setWindowDecorationStyle(JRootPane.FILE_CHOOSER_DIALOG);
            }
        }
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        return dialog;
    }

    // **************************
    // ***** Dialog Options *****
    // **************************

    /**
     * Returns the value of the <code>controlButtonsAreShown</code>
     * property.
     *
     * @return   the value of the <code>controlButtonsAreShown</code>
     *     property
     * @since 1.3
     */
    public boolean getControlButtonsAreShown() {
        return controlsShown;
    }


    /**
     * Sets the property
     * that indicates whether the <i>approve</i> and <i>cancel</i>
     * buttons are shown in the file chooser.
     *
     * @param b <code>false</code> if control buttons should not be
     *    shown; otherwise, <code>true</code>
     * @since 1.3
     */
    public void setControlButtonsAreShown(boolean b) {
        if(controlsShown == b) {
            return;
        }
        boolean oldValue = controlsShown;
        controlsShown = b;
        firePropertyChange(CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY, oldValue, controlsShown);
    }

    /**
     * Returns the type of this dialog.  The default is
     * <code>JFileChooser.OPEN_DIALOG</code>.
     *
     * @return   the type of dialog to be displayed
     */
    public int getDialogType() {
        return dialogType;
    }

    /**
     * Sets the type of this dialog.
     *
     * @param dialogType the type of dialog to be displayed
     * @exception IllegalArgumentException if <code>dialogType</code> is
     *                          not legal
     */
    public void setDialogType(int dialogType) {
        if(this.dialogType == dialogType) {
            return;
        }
        checkDialogType(dialogType);
        int oldValue = this.dialogType;
        this.dialogType = dialogType;
        if(dialogType == OPEN_DIALOG || dialogType == SAVE_DIALOG) {
            setApproveButtonText(null);
        }
        firePropertyChange(DIALOG_TYPE_CHANGED_PROPERTY, oldValue, dialogType);
    }

    private static void checkDialogType(int dialogType) {
        if (!(dialogType == OPEN_DIALOG || dialogType == SAVE_DIALOG
                || dialogType == CUSTOM_DIALOG)) {
            throw new IllegalArgumentException(
                    "Incorrect Dialog Type: " + dialogType);
        }
    }

    /**
     * Sets the string that goes in the <code>JFileChooser</code> window's
     * title bar.
     *
     * @param dialogTitle the new <code>String</code> for the title bar
     */
    public void setDialogTitle(String dialogTitle) {
        String oldValue = this.dialogTitle;
        this.dialogTitle = dialogTitle;
        if(dialog != null) {
            dialog.setTitle(dialogTitle);
        }
        firePropertyChange(DIALOG_TITLE_CHANGED_PROPERTY, oldValue, dialogTitle);
    }

    /**
     * Gets the string that goes in the <code>JFileChooser</code>'s titlebar.
     *
     * @return the string from the <code>JFileChooser</code> window's title bar
     */
    public String getDialogTitle() {
        return dialogTitle;
    }

    // ************************************
    // ***** JFileChooser View Options *****
    // ************************************



    /**
     * Sets the tooltip text used in the <code>ApproveButton</code>.
     * If <code>null</code>, the UI object will determine the button's text.
     *
     * @param toolTipText the tooltip text for the approve button
     */
    public void setApproveButtonToolTipText(String toolTipText) {
        if(approveButtonToolTipText == toolTipText) {
            return;
        }
        String oldValue = approveButtonToolTipText;
        approveButtonToolTipText = toolTipText;
        firePropertyChange(APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY, oldValue, approveButtonToolTipText);
    }


    /**
     * Returns the tooltip text used in the <code>ApproveButton</code>.
     * If <code>null</code>, the UI object will determine the button's text.
     *
     * @return the tooltip text used for the approve button
     */
    public String getApproveButtonToolTipText() {
        return approveButtonToolTipText;
    }

    /**
     * Returns the approve button's mnemonic.
     * @return an integer value for the mnemonic key
     */
    public int getApproveButtonMnemonic() {
        return approveButtonMnemonic;
    }

    /**
     * Sets the approve button's mnemonic using a numeric keycode.
     *
     * @param mnemonic  an integer value for the mnemonic key
     */
    public void setApproveButtonMnemonic(int mnemonic) {
        if(approveButtonMnemonic == mnemonic) {
           return;
        }
        int oldValue = approveButtonMnemonic;
        approveButtonMnemonic = mnemonic;
        firePropertyChange(APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY, oldValue, approveButtonMnemonic);
    }

    /**
     * Sets the approve button's mnemonic using a character.
     * @param mnemonic  a character value for the mnemonic key
     */
    public void setApproveButtonMnemonic(char mnemonic) {
        int vk = (int) mnemonic;
        if(vk >= 'a' && vk <='z') {
            vk -= ('a' - 'A');
        }
        setApproveButtonMnemonic(vk);
    }


    /**
     * Sets the text used in the <code>ApproveButton</code> in the
     * <code>FileChooserUI</code>.
     *
     * @param approveButtonText the text used in the <code>ApproveButton</code>
     */
    public void setApproveButtonText(String approveButtonText) {
        if(this.approveButtonText == approveButtonText) {
            return;
        }
        String oldValue = this.approveButtonText;
        this.approveButtonText = approveButtonText;
        firePropertyChange(APPROVE_BUTTON_TEXT_CHANGED_PROPERTY, oldValue, approveButtonText);
    }

    /**
     * Returns the text used in the <code>ApproveButton</code> in the
     * <code>FileChooserUI</code>.
     * If <code>null</code>, the UI object will determine the button's text.
     *
     * @return the text used in the <code>ApproveButton</code>
     */
    public String getApproveButtonText() {
        return approveButtonText;
    }

    /**
     * Gets the list of user choosable file filters.
     *
     * @return a <code>FileFilter</code> array containing all the choosable
     *         file filters
     */
    public FileFilter[] getChoosableFileFilters() {
        FileFilter[] filterArray = new FileFilter[filters.size()];
        filters.copyInto(filterArray);
        return filterArray;
    }

    /**
     * Adds a filter to the list of user choosable file filters, and
     * makes it the current file filter.
     *
     * @param filter the <code>FileFilter</code> to add to the choosable file
     *               filter list
     */
    public void addChoosableFileFilter(FileFilter filter) {
        if(filter != null && !filters.contains(filter)) {
            FileFilter[] oldValue = getChoosableFileFilters();
            filters.addElement(filter);
            firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
            if (fileFilter == null && filters.size() == 1) {
                setFileFilter(filter);
            }
        }
    }

    /**
     * Removes a filter from the list of user choosable file filters. Returns
     * true if the file filter was removed.
     */
    public boolean removeChoosableFileFilter(FileFilter f) {
        int index = filters.indexOf(f);
        if (index >= 0) {
            if(getFileFilter() == f) {
                FileFilter aaff = getAcceptAllFileFilter();
                if (isAcceptAllFileFilterUsed() && (aaff != f)) {
                    // choose default filter if it is used
                    setFileFilter(aaff);
                }
                else if (index > 0) {
                    // choose the first filter, because it is not removed
                    setFileFilter(filters.get(0));
                }
                else if (filters.size() > 1) {
                    // choose the second filter, because the first one is removed
                    setFileFilter(filters.get(1));
                }
                else {
                    // no more filters
                    setFileFilter(null);
                }
            }
            FileFilter[] oldValue = getChoosableFileFilters();
            filters.removeElement(f);
            firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Resets the choosable file filter list to its starting state. Normally,
     * this removes all added file filters while leaving the
     * <code>AcceptAll</code> file filter.
     */
    public void resetChoosableFileFilters() {
        FileFilter[] oldValue = getChoosableFileFilters();
        setFileFilter(null);
        filters.removeAllElements();
        if(isAcceptAllFileFilterUsed()) {
           addChoosableFileFilter(getAcceptAllFileFilter());
        }
        firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
    }

    /**
     * Returns the <code>AcceptAll</code> file filter.
     * For example, on Microsoft Windows this would be All Files (*.*).
     */
    public FileFilter getAcceptAllFileFilter() {
        FileFilter filter = null;
        if(getUI() != null) {
            filter = getUI().getAcceptAllFileFilter(this);
        }
        return filter;
    }

   /**
    * Returns whether the <code>AcceptAll FileFilter</code> is used.
    * @return true if the <code>AcceptAll FileFilter</code> is used
    * @since 1.3
    */
    public boolean isAcceptAllFileFilterUsed() {
        return useAcceptAllFileFilter;
    }

   /**
    * Determines whether the <code>AcceptAll FileFilter</code> is used
    * as an available choice in the choosable filter list.
    *
    * @param b a <code>boolean</code> which determines whether the <code>AcceptAll</code>
    *          file filter is an available choice in the choosable filter list
    * @since 1.3
    */
    public void setAcceptAllFileFilterUsed(boolean b) {
        boolean oldValue = useAcceptAllFileFilter;
        useAcceptAllFileFilter = b;
        if(!b) {
            removeChoosableFileFilter(getAcceptAllFileFilter());
        } else {
            removeChoosableFileFilter(getAcceptAllFileFilter());
            addChoosableFileFilter(getAcceptAllFileFilter());
        }
        firePropertyChange(ACCEPT_ALL_FILE_FILTER_USED_CHANGED_PROPERTY, oldValue, useAcceptAllFileFilter);
    }

    /**
     * Returns the accessory component.
     *
     * @return this JFileChooser's accessory component, or null
     * @see #setAccessory
     */
    public JComponent getAccessory() {
        return accessory;
    }

    /**
     * Sets the accessory component. An accessory is often used to show a
     * preview image of the selected file; however, it can be used for anything
     * that the programmer wishes, such as extra custom file chooser controls.
     *
     * @param newAccessory the accessory component to be set
     */
    public void setAccessory(JComponent newAccessory) {
        JComponent oldValue = accessory;
        accessory = newAccessory;
        firePropertyChange(ACCESSORY_CHANGED_PROPERTY, oldValue, accessory);
    }

    /**
     * Sets the <code>JFileChooser</code> to allow the user to just
     * select files, just select
     * directories, or select both files and directories.  The default is
     * <code>JFilesChooser.FILES_ONLY</code>.
     *
     * @param mode the type of files to be displayed
     * @exception IllegalArgumentException  if <code>mode</code> is an
     *                          illegal file selection mode
     */
    public void setFileSelectionMode(int mode) {
        if(fileSelectionMode == mode) {
            return;
        }

        if ((mode == FILES_ONLY) || (mode == DIRECTORIES_ONLY) || (mode == FILES_AND_DIRECTORIES)) {
           int oldValue = fileSelectionMode;
           fileSelectionMode = mode;
           firePropertyChange(FILE_SELECTION_MODE_CHANGED_PROPERTY, oldValue, fileSelectionMode);
        } else {
           throw new IllegalArgumentException("Incorrect Mode for file selection: " + mode);
        }
    }

    /**
     * Returns the current file-selection mode.  The default is
     * <code>JFilesChooser.FILES_ONLY</code>.
     *
     * @return the type of files to be displayed
     */
    public int getFileSelectionMode() {
        return fileSelectionMode;
    }

    /**
     * Convenience call that determines if files are selectable based on the
     * current file selection mode.
     */
    public boolean isFileSelectionEnabled() {
        return ((fileSelectionMode == FILES_ONLY) || (fileSelectionMode == FILES_AND_DIRECTORIES));
    }

    /**
     * Convenience call that determines if directories are selectable based
     * on the current file selection mode.
     */
    public boolean isDirectorySelectionEnabled() {
        return ((fileSelectionMode == DIRECTORIES_ONLY) || (fileSelectionMode == FILES_AND_DIRECTORIES));
    }

    /**
     * Sets the file chooser to allow multiple file selections.
     *
     * @param b true if multiple files may be selected
     */
    public void setMultiSelectionEnabled(boolean b) {
        if(multiSelectionEnabled == b) {
            return;
        }
        boolean oldValue = multiSelectionEnabled;
        multiSelectionEnabled = b;
        firePropertyChange(MULTI_SELECTION_ENABLED_CHANGED_PROPERTY, oldValue, multiSelectionEnabled);
    }

    /**
     * Returns true if multiple files can be selected.
     * @return true if multiple files can be selected
     */
    public boolean isMultiSelectionEnabled() {
        return multiSelectionEnabled;
    }


    /**
     * Returns true if hidden files are not shown in the file chooser;
     * otherwise, returns false.
     *
     * @return the status of the file hiding property
     */
    public boolean isFileHidingEnabled() {
        return useFileHiding;
    }

    /**
     * Sets file hiding on or off. If true, hidden files are not shown
     * in the file chooser. The job of determining which files are
     * shown is done by the <code>FileView</code>.
     *
     * @param b the boolean value that determines whether file hiding is
     *          turned on
     */
    public void setFileHidingEnabled(boolean b) {
        boolean oldValue = useFileHiding;
        useFileHiding = b;
        firePropertyChange(FILE_HIDING_CHANGED_PROPERTY, oldValue, useFileHiding);
    }

    /**
     * Sets the current file filter. The file filter is used by the
     * file chooser to filter out files from the user's view.
     *
     * @param filter the new current file filter to use
     */
    public void setFileFilter(FileFilter filter) {
        FileFilter oldValue = fileFilter;
        fileFilter = filter;
        if (filter != null) {
            if (isMultiSelectionEnabled() && selectedFiles != null && selectedFiles.length > 0) {
                Vector<File> fList = new Vector<File>();
                boolean failed = false;
                for (File file : selectedFiles) {
                    if (filter.accept(file)) {
                        fList.add(file);
                    } else {
                        failed = true;
                    }
                }
                if (failed) {
                    setSelectedFiles((fList.size() == 0) ? null : fList.toArray(new File[fList.size()]));
                }
            } else if (selectedFile != null && !filter.accept(selectedFile)) {
                setSelectedFile(null);
            }
        }
        if (filter != null && !filters.contains(filter)) addChoosableFileFilter(filter);
        firePropertyChange(FILE_FILTER_CHANGED_PROPERTY, oldValue, fileFilter);
    }


    /**
     * Returns the currently selected file filter.
     *
     * @return the current file filter
     */
    public FileFilter getFileFilter() {
        return fileFilter;
    }

    /**
     * Sets the file view to used to retrieve UI information, such as
     * the icon that represents a file or the type description of a file.
     *
     * @param fileView a <code>FileView</code> to be used to retrieve UI information
     */
    public void setFileView(FileView fileView) {
        FileView oldValue = this.fileView;
        this.fileView = fileView;
        firePropertyChange(FILE_VIEW_CHANGED_PROPERTY, oldValue, fileView);
    }

    /**
     * Returns the current file view.
     *
     * @return the current file view
     */
    public FileView getFileView() {
        return fileView;
    }

    // ******************************
    // *****FileView delegation *****
    // ******************************

    // NOTE: all of the following methods attempt to delegate
    // first to the client set fileView, and if <code>null</code> is returned
    // (or there is now client defined fileView) then calls the
    // UI's default fileView.

    /**
     * Returns the filename.
     * @param f the <code>File</code>
     * @return the <code>String</code> containing the filename for
     *          <code>f</code>
     */
    public String getName(File f) {
        String filename = null;
        if(f != null) {
            if(getFileView() != null) {
                filename = getFileView().getName(f);
            }

            FileView uiFileView = getUI().getFileView(this);

            if(filename == null && uiFileView != null) {
                filename = uiFileView.getName(f);
            }
        }
        return filename;
    }

    /**
     * Returns the file description.
     * @param f the <code>File</code>
     * @return the <code>String</code> containing the file description for
     *          <code>f</code>
     */
    public String getDescription(File f) {
        String description = null;
        if(f != null) {
            if(getFileView() != null) {
                description = getFileView().getDescription(f);
            }

            FileView uiFileView = getUI().getFileView(this);

            if(description == null && uiFileView != null) {
                description = uiFileView.getDescription(f);
            }
        }
        return description;
    }

    /**
     * Returns the file type.
     * @param f the <code>File</code>
     * @return the <code>String</code> containing the file type description for
     *          <code>f</code>
     */
    public String getTypeDescription(File f) {
        String typeDescription = null;
        if(f != null) {
            if(getFileView() != null) {
                typeDescription = getFileView().getTypeDescription(f);
            }

            FileView uiFileView = getUI().getFileView(this);

            if(typeDescription == null && uiFileView != null) {
                typeDescription = uiFileView.getTypeDescription(f);
            }
        }
        return typeDescription;
    }

    /**
     * Returns the icon for this file or type of file, depending
     * on the system.
     * @param f the <code>File</code>
     * @return the <code>Icon</code> for this file, or type of file
     */
    public Icon getIcon(File f) {
        Icon icon = null;
        if (f != null) {
            if(getFileView() != null) {
                icon = getFileView().getIcon(f);
            }

            FileView uiFileView = getUI().getFileView(this);

            if(icon == null && uiFileView != null) {
                icon = uiFileView.getIcon(f);
            }
        }
        return icon;
    }

    /**
     * Returns true if the file (directory) can be visited.
     * Returns false if the directory cannot be traversed.
     * @param f the <code>File</code>
     * @return true if the file/directory can be traversed, otherwise false
     */
    public boolean isTraversable(File f) {

        Boolean traversable = null;
        if (f != null) {
            if (getFileView() != null) {
                traversable = getFileView().isTraversable(f);
            }

            FileView uiFileView = getUI().getFileView(this);

            if (traversable == null && uiFileView != null) {
                traversable = uiFileView.isTraversable(f);
            }
            if (traversable == null) {
                traversable = getFileSystemView().isTraversable(f);
            }
        }
        return (traversable != null && traversable.booleanValue());
    }

    /**
     * Returns true if the file should be displayed.
     * @param f the <code>File</code>
     * @return true if the file should be displayed, otherwise false
     */
    public boolean accept(File f) {
        boolean shown = true;
        if(f != null && fileFilter != null) {
            shown = fileFilter.accept(f);
        }
        return shown;
    }

    /**
     * Sets the file system view that the <code>JFileChooser</code> uses for
     * accessing and creating file system resources.
     *
     * @param fsv        the new <code>FileSystemView</code>
     */
    public void setFileSystemView(FileSystemView fsv) {
        FileSystemView oldValue = fileSystemView;
        fileSystemView = fsv;
        firePropertyChange(FILE_SYSTEM_VIEW_CHANGED_PROPERTY, oldValue, fileSystemView);
    }

    /**
     * Returns the file system view.
     * @return the <code>FileSystemView</code> object
     * @see #setFileSystemView
     */
    public FileSystemView getFileSystemView() {
        return fileSystemView;
    }

    // **************************
    // ***** Event Handling *****
    // **************************

    /**
     * Called by the UI when the user hits the Approve button
     * (labeled "Open" or "Save", by default). This can also be
     * called by the programmer.
     * This method causes an action event to fire
     * with the command string equal to
     * <code>APPROVE_SELECTION</code>.
     *
     * @see #APPROVE_SELECTION
     */
    public void approveSelection() {
        returnValue = APPROVE_OPTION;
        if(dialog != null) {
            dialog.setVisible(false);
        }
        fireActionPerformed(APPROVE_SELECTION);
    }

    /**
     * Called by the UI when the user chooses the Cancel button.
     * This can also be called by the programmer.
     * This method causes an action event to fire
     * with the command string equal to
     * <code>CANCEL_SELECTION</code>.
     *
     * @see #CANCEL_SELECTION
     */
    public void cancelSelection() {
        returnValue = CANCEL_OPTION;
        if(dialog != null) {
            dialog.setVisible(false);
        }
        fireActionPerformed(CANCEL_SELECTION);
    }

    /**
     * Adds an <code>ActionListener</code> to the file chooser.
     *
     * @param l  the listener to be added
     */
    public void addActionListener(ActionListener l) {
        actionListeners.add(ActionListener.class, l);
    }

    /**
     * Removes an <code>ActionListener</code> from the file chooser.
     *
     * @param l  the listener to be removed
     */
    public void removeActionListener(ActionListener l) {
        actionListeners.remove(ActionListener.class, l);
    }

    /**
     * Returns an array of all the action listeners
     * registered on this file chooser.
     *
     * @return all of this file chooser's <code>ActionListener</code>s
     *         or an empty
     *         array if no action listeners are currently registered
     * @since 1.4
     */
    public ActionListener[] getActionListeners() {
        return actionListeners.getListeners(ActionListener.class);
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type. The event instance
     * is lazily created using the <code>command</code> parameter.
     */
    protected void fireActionPerformed(String command) {
        // Guaranteed to return a non-null array
        Object[] listeners = actionListeners.getListenerList();
        long mostRecentEventTime = System.currentTimeMillis();
        ActionEvent e = null;
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==ActionListener.class) {
                if (e == null) {
                    e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                                        command, mostRecentEventTime, 0);
                }
                ((ActionListener)listeners[i+1]).actionPerformed(e);
            }
        }
    }

    // *********************************
    // ***** Pluggable L&F methods *****
    // *********************************

    /**
     * Resets the UI property to a value from the current look and feel.
     */
    public void updateUI() {
        if (isAcceptAllFileFilterUsed()) {
            removeChoosableFileFilter(getAcceptAllFileFilter());
        }
        FileChooserUI ui = null;
        try {
            javax.swing.plaf.ComponentUI candidate = UIManager.getUI(this);
            if (candidate instanceof FileChooserUI) ui = (FileChooserUI) candidate;
        } catch (LinkageError e) {
            // A look and feel whose delegate extends a class the shadow lacks
            // (FlatLaf's Metal-based chooser) must not make the chooser
            // unconstructible; Basic stands in.
        }
        if (ui == null) {
            ui = (FileChooserUI) javax.swing.plaf.basic.BasicFileChooserUI.createUI(this);
        }
        if (fileSystemView == null) {
            // We were probably deserialized
            setFileSystemView(FileSystemView.getFileSystemView());
        }
        setUI(ui);

        if(isAcceptAllFileFilterUsed()) {
            addChoosableFileFilter(getAcceptAllFileFilter());
        }
    }

    /**
     * Returns a string that specifies the name of the L&amp;F class
     * that renders this component.
     *
     * @return the string "FileChooserUI"
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Gets the UI object which implements the L&amp;F for this component.
     *
     * @return the FileChooserUI object that implements the FileChooserUI L&amp;F
     */
    public FileChooserUI getUI() {
        return (FileChooserUI) ui;
    }

    /**
     * Returns a string representation of this <code>JFileChooser</code>.
     *
     * @return  a string representation of this <code>JFileChooser</code>
     */
    protected String paramString() {
        String approveButtonTextString = (approveButtonText != null ?
                                          approveButtonText: "");
        String dialogTitleString = (dialogTitle != null ?
                                    dialogTitle: "");
        String dialogTypeString;
        if (dialogType == OPEN_DIALOG) {
            dialogTypeString = "OPEN_DIALOG";
        } else if (dialogType == SAVE_DIALOG) {
            dialogTypeString = "SAVE_DIALOG";
        } else if (dialogType == CUSTOM_DIALOG) {
            dialogTypeString = "CUSTOM_DIALOG";
        } else dialogTypeString = "";
        String returnValueString;
        if (returnValue == CANCEL_OPTION) {
            returnValueString = "CANCEL_OPTION";
        } else if (returnValue == APPROVE_OPTION) {
            returnValueString = "APPROVE_OPTION";
        } else if (returnValue == ERROR_OPTION) {
            returnValueString = "ERROR_OPTION";
        } else returnValueString = "";
        String useFileHidingString = (useFileHiding ?
                                    "true" : "false");
        String fileSelectionModeString;
        if (fileSelectionMode == FILES_ONLY) {
            fileSelectionModeString = "FILES_ONLY";
        } else if (fileSelectionMode == DIRECTORIES_ONLY) {
            fileSelectionModeString = "DIRECTORIES_ONLY";
        } else if (fileSelectionMode == FILES_AND_DIRECTORIES) {
            fileSelectionModeString = "FILES_AND_DIRECTORIES";
        } else fileSelectionModeString = "";
        String currentDirectoryString = (currentDirectory != null ?
                                         currentDirectory.toString() : "");
        String selectedFileString = (selectedFile != null ?
                                     selectedFile.toString() : "");

        return super.paramString() +
        ",approveButtonText=" + approveButtonTextString +
        ",currentDirectory=" + currentDirectoryString +
        ",dialogTitle=" + dialogTitleString +
        ",dialogType=" + dialogTypeString +
        ",fileSelectionMode=" + fileSelectionModeString +
        ",returnValue=" + returnValueString +
        ",selectedFile=" + selectedFileString +
        ",useFileHiding=" + useFileHidingString;
    }

/////////////////
// Accessibility support
////////////////

    /**
     * Gets the AccessibleContext associated with this JFileChooser.
     *
     * @return an AccessibleJFileChooser that serves as the
     *         AccessibleContext of this JFileChooser
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJFileChooser();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JFileChooser</code> class.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class AccessibleJFileChooser extends AccessibleJComponent {

        /**
         * Gets the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.FILE_CHOOSER;
        }

    } // inner class AccessibleJFileChooser

}
