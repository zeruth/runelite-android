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

package javax.swing.plaf.basic;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FileChooserUI;

/**
 * Basic L&amp;F implementation of a FileChooser.
 * <p>
 * The JDK's Basic delegate leaves the component tree to its subclasses
 * (Metal builds the panel). The shadow has no Metal chooser and FlatLaf's
 * extends it, so this delegate builds the panel itself: a directory label
 * with an up button, the file list over {@link BasicDirectoryModel}, the file
 * name field, the filter combo, the accessory and the approve/cancel bar.
 *
 * @author Jeff Dinkins
 */
public class BasicFileChooserUI extends FileChooserUI {

    /* FileView icons */
    protected Icon directoryIcon = null;
    protected Icon fileIcon = null;
    protected Icon computerIcon = null;
    protected Icon hardDriveIcon = null;
    protected Icon floppyDriveIcon = null;

    protected Icon newFolderIcon = null;
    protected Icon upFolderIcon = null;
    protected Icon homeFolderIcon = null;
    protected Icon listViewIcon = null;
    protected Icon detailsViewIcon = null;
    protected Icon viewMenuIcon = null;

    protected int saveButtonMnemonic = 0;
    protected int openButtonMnemonic = 0;
    protected int cancelButtonMnemonic = 0;
    protected int updateButtonMnemonic = 0;
    protected int helpButtonMnemonic = 0;
    protected int directoryOpenButtonMnemonic = 0;

    protected String saveButtonText = null;
    protected String openButtonText = null;
    protected String cancelButtonText = null;
    protected String updateButtonText = null;
    protected String helpButtonText = null;
    protected String directoryOpenButtonText = null;

    private String openDialogTitleText = null;
    private String saveDialogTitleText = null;

    protected String saveButtonToolTipText = null;
    protected String openButtonToolTipText = null;
    protected String cancelButtonToolTipText = null;
    protected String updateButtonToolTipText = null;
    protected String helpButtonToolTipText = null;
    protected String directoryOpenButtonToolTipText = null;

    // Some generic FileChooser functions
    private Action approveSelectionAction = new ApproveSelectionAction();
    private Action cancelSelectionAction = new CancelSelectionAction();
    private Action updateAction = new UpdateAction();
    private Action newFolderAction;
    private Action goHomeAction = new GoHomeAction();
    private Action changeToParentDirectoryAction = new ChangeToParentDirectoryAction();

    private String newFolderErrorText = null;

    private JFileChooser filechooser = null;

    private boolean directorySelected = false;
    private File directory = null;

    private PropertyChangeListener propertyChangeListener = null;
    private AcceptAllFileFilter acceptAllFileFilter = new AcceptAllFileFilter();
    private FileFilter actualFileFilter = null;
    private BasicFileView fileView = new BasicFileView();

    private BasicDirectoryModel model = null;

    private JPanel accessoryPanel = null;
    private JList<Object> list;
    private JTextField fileNameField;
    private JLabel directoryLabel;
    private JComboBox<FileFilter> filterCombo;
    private JPanel buttonPanel;
    private JButton approveButton;
    private JButton cancelButton;
    // Guards the list/field/combo against echoing the chooser's own property changes back.
    private boolean syncing;

    private String fileNameLabelText = null;
    private String filesOfTypeLabelText = null;

    /**
     * Creates a {@code BasicFileChooserUI} implementation
     * for the specified component. By default
     * the {@code BasicLookAndFeel} class uses
     * {@code createUI} methods of all basic UIs classes
     * to instantiate UIs.
     *
     * @param c the {@code JFileChooser} which needs a UI
     * @return the {@code BasicFileChooserUI} object
     */
    public static ComponentUI createUI(JComponent c) {
        return new BasicFileChooserUI((JFileChooser) c);
    }

    public BasicFileChooserUI(JFileChooser b) {
    }

    public void installUI(JComponent c) {
        accessoryPanel = new JPanel(new BorderLayout());
        filechooser = (JFileChooser) c;

        createModel();

        clearIconCache();

        installDefaults(filechooser);
        installComponents(filechooser);
        installListeners(filechooser);
        filechooser.applyComponentOrientation(filechooser.getComponentOrientation());
    }

    public void uninstallUI(JComponent c) {
        uninstallListeners(filechooser);
        uninstallComponents(filechooser);
        uninstallDefaults(filechooser);

        if (accessoryPanel != null) {
            accessoryPanel.removeAll();
        }

        accessoryPanel = null;
        getFileChooser().removeAll();
    }

    public void installComponents(JFileChooser fc) {
        fc.setLayout(new BorderLayout(0, 6));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        directoryLabel = new JLabel();
        top.add(directoryLabel, BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton up = new JButton(getChangeToParentDirectoryAction());
        up.setText(upFolderIcon == null ? "Up" : null);
        up.setIcon(upFolderIcon);
        navigation.add(up);
        JButton home = new JButton(getGoHomeAction());
        home.setText(homeFolderIcon == null ? "Home" : null);
        home.setIcon(homeFolderIcon);
        navigation.add(home);
        top.add(navigation, BorderLayout.EAST);
        fc.add(top, BorderLayout.NORTH);

        list = new JList<Object>(getModel());
        list.setName("FileChooser.list");
        list.setCellRenderer(new FileRenderer());
        list.addListSelectionListener(createListSelectionListener(fc));
        list.addMouseListener(createDoubleClickListener(fc, list));
        fc.add(new JScrollPane(list), BorderLayout.CENTER);

        accessoryPanel.setName("FileChooser.accessoryPanel");
        if (fc.getAccessory() != null) {
            accessoryPanel.add(fc.getAccessory(), BorderLayout.CENTER);
        }
        fc.add(accessoryPanel, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        JPanel fields = new JPanel(new BorderLayout(6, 4));
        fields.add(new JLabel(fileNameLabelText), BorderLayout.WEST);
        fileNameField = new JTextField();
        fileNameField.addActionListener(getApproveSelectionAction());
        fields.add(fileNameField, BorderLayout.CENTER);
        JPanel types = new JPanel(new BorderLayout(6, 0));
        types.add(new JLabel(filesOfTypeLabelText), BorderLayout.WEST);
        filterCombo = new JComboBox<FileFilter>();
        filterCombo.setRenderer(new FilterRenderer());
        filterCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Object selected = filterCombo.getSelectedItem();
                if (!syncing && selected instanceof FileFilter && selected != getFileChooser().getFileFilter()) {
                    getFileChooser().setFileFilter((FileFilter) selected);
                }
            }
        });
        types.add(filterCombo, BorderLayout.CENTER);
        fields.add(types, BorderLayout.SOUTH);
        bottom.add(fields, BorderLayout.CENTER);

        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        approveButton = new JButton(getApproveSelectionAction());
        cancelButton = new JButton(getCancelSelectionAction());
        cancelButton.setText(cancelButtonText);
        cancelButton.setToolTipText(cancelButtonToolTipText);
        buttonPanel.add(approveButton);
        buttonPanel.add(cancelButton);
        bottom.add(buttonPanel, BorderLayout.SOUTH);
        fc.add(bottom, BorderLayout.SOUTH);

        updateApproveButton(fc);
        updateSelectionMode(fc);
        updateFilters(fc);
        updateDirectory(fc);
        buttonPanel.setVisible(fc.getControlButtonsAreShown());
    }

    public void uninstallComponents(JFileChooser fc) {
        fc.removeAll();
        list = null;
        fileNameField = null;
        directoryLabel = null;
        filterCombo = null;
        buttonPanel = null;
        approveButton = null;
        cancelButton = null;
    }

    protected void installListeners(JFileChooser fc) {
        propertyChangeListener = createPropertyChangeListener(fc);
        if (propertyChangeListener != null) {
            fc.addPropertyChangeListener(propertyChangeListener);
        }
        fc.addPropertyChangeListener(getModel());
    }

    protected void uninstallListeners(JFileChooser fc) {
        if (propertyChangeListener != null) {
            fc.removePropertyChangeListener(propertyChangeListener);
        }
        fc.removePropertyChangeListener(getModel());
    }

    protected void installDefaults(JFileChooser fc) {
        installIcons(fc);
        installStrings(fc);
        LookAndFeel.installProperty(fc, "opaque", Boolean.TRUE);
    }

    protected void installIcons(JFileChooser fc) {
        directoryIcon    = UIManager.getIcon("FileView.directoryIcon");
        fileIcon         = UIManager.getIcon("FileView.fileIcon");
        computerIcon     = UIManager.getIcon("FileView.computerIcon");
        hardDriveIcon    = UIManager.getIcon("FileView.hardDriveIcon");
        floppyDriveIcon  = UIManager.getIcon("FileView.floppyDriveIcon");

        newFolderIcon    = UIManager.getIcon("FileChooser.newFolderIcon");
        upFolderIcon     = UIManager.getIcon("FileChooser.upFolderIcon");
        homeFolderIcon   = UIManager.getIcon("FileChooser.homeFolderIcon");
        detailsViewIcon  = UIManager.getIcon("FileChooser.detailsViewIcon");
        listViewIcon     = UIManager.getIcon("FileChooser.listViewIcon");
        viewMenuIcon     = UIManager.getIcon("FileChooser.viewMenuIcon");
    }

    protected void installStrings(JFileChooser fc) {
        Locale l = fc.getLocale();
        // The JDK reads these from its bundled resources; the shadow has no
        // bundle, so the English JDK texts stand in when the look and feel
        // does not define them.
        newFolderErrorText = string("FileChooser.newFolderErrorText", l, "Error creating new folder");

        saveButtonText   = string("FileChooser.saveButtonText", l, "Save");
        openButtonText   = string("FileChooser.openButtonText", l, "Open");
        saveDialogTitleText = string("FileChooser.saveDialogTitleText", l, "Save");
        openDialogTitleText = string("FileChooser.openDialogTitleText", l, "Open");
        cancelButtonText = string("FileChooser.cancelButtonText", l, "Cancel");
        updateButtonText = string("FileChooser.updateButtonText", l, "Update");
        helpButtonText   = string("FileChooser.helpButtonText", l, "Help");
        directoryOpenButtonText = string("FileChooser.directoryOpenButtonText", l, "Open");

        saveButtonMnemonic   = getMnemonic("FileChooser.saveButtonMnemonic", l);
        openButtonMnemonic   = getMnemonic("FileChooser.openButtonMnemonic", l);
        cancelButtonMnemonic = getMnemonic("FileChooser.cancelButtonMnemonic", l);
        updateButtonMnemonic = getMnemonic("FileChooser.updateButtonMnemonic", l);
        helpButtonMnemonic   = getMnemonic("FileChooser.helpButtonMnemonic", l);
        directoryOpenButtonMnemonic = getMnemonic("FileChooser.directoryOpenButtonMnemonic", l);

        saveButtonToolTipText   = string("FileChooser.saveButtonToolTipText", l, "Save selected file");
        openButtonToolTipText   = string("FileChooser.openButtonToolTipText", l, "Open selected file");
        cancelButtonToolTipText = string("FileChooser.cancelButtonToolTipText", l, "Abort file chooser dialog");
        updateButtonToolTipText = string("FileChooser.updateButtonToolTipText", l, "Update directory listing");
        helpButtonToolTipText   = string("FileChooser.helpButtonToolTipText", l, "FileChooser help");
        directoryOpenButtonToolTipText = string("FileChooser.directoryOpenButtonToolTipText", l, "Open selected directory");

        fileNameLabelText = string("FileChooser.fileNameLabelText", l, "File Name:");
        filesOfTypeLabelText = string("FileChooser.filesOfTypeLabelText", l, "Files of Type:");
    }

    private static String string(String key, Locale l, String fallback) {
        String value = UIManager.getString(key, l);
        return value != null ? value : fallback;
    }

    private Integer getMnemonic(String key, Locale l) {
        Object value = UIManager.get(key, l);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException nfe) {
                // A malformed mnemonic means none, as in the JDK's SwingUtilities2.getUIDefaultsInt.
            }
        }
        return 0;
    }

    protected void uninstallDefaults(JFileChooser fc) {
        uninstallIcons(fc);
        uninstallStrings(fc);
    }

    protected void uninstallIcons(JFileChooser fc) {
        directoryIcon    = null;
        fileIcon         = null;
        computerIcon     = null;
        hardDriveIcon    = null;
        floppyDriveIcon  = null;

        newFolderIcon    = null;
        upFolderIcon     = null;
        homeFolderIcon   = null;
        detailsViewIcon  = null;
        listViewIcon     = null;
        viewMenuIcon     = null;
    }

    protected void uninstallStrings(JFileChooser fc) {
        saveButtonText   = null;
        openButtonText   = null;
        cancelButtonText = null;
        updateButtonText = null;
        helpButtonText   = null;
        directoryOpenButtonText = null;

        saveButtonToolTipText = null;
        openButtonToolTipText = null;
        cancelButtonToolTipText = null;
        updateButtonToolTipText = null;
        helpButtonToolTipText = null;
        directoryOpenButtonToolTipText = null;
    }

    protected void createModel() {
        if (model != null) {
            model.invalidateFileCache();
        }
        model = new BasicDirectoryModel(getFileChooser());
    }

    public BasicDirectoryModel getModel() {
        return model;
    }

    public PropertyChangeListener createPropertyChangeListener(JFileChooser fc) {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                BasicFileChooserUI.this.propertyChange(e);
            }
        };
    }

    private void propertyChange(PropertyChangeEvent e) {
        JFileChooser fc = getFileChooser();
        if (fc == null || list == null) {
            return;
        }
        String prop = e.getPropertyName();
        if (prop.equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.SELECTED_FILES_CHANGED_PROPERTY)) {
            updateFileName(fc);
        } else if (prop.equals(JFileChooser.DIRECTORY_CHANGED_PROPERTY)) {
            clearIconCache();
            updateDirectory(fc);
        } else if (prop.equals(JFileChooser.CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.FILE_FILTER_CHANGED_PROPERTY)) {
            updateFilters(fc);
        } else if (prop.equals(JFileChooser.MULTI_SELECTION_ENABLED_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY)) {
            updateSelectionMode(fc);
        } else if (prop.equals(JFileChooser.ACCESSORY_CHANGED_PROPERTY)) {
            accessoryPanel.removeAll();
            if (fc.getAccessory() != null) {
                accessoryPanel.add(fc.getAccessory(), BorderLayout.CENTER);
            }
            fc.revalidate();
            fc.repaint();
        } else if (prop.equals(JFileChooser.CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY)) {
            buttonPanel.setVisible(fc.getControlButtonsAreShown());
            fc.revalidate();
        } else if (prop.equals(JFileChooser.APPROVE_BUTTON_TEXT_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY)
                || prop.equals(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY)) {
            updateApproveButton(fc);
        } else if (prop.equals("componentOrientation")) {
            fc.applyComponentOrientation(fc.getComponentOrientation());
        }
    }

    private void updateApproveButton(JFileChooser fc) {
        approveButton.setText(getApproveButtonText(fc));
        approveButton.setToolTipText(getApproveButtonToolTipText(fc));
        approveButton.setMnemonic(getApproveButtonMnemonic(fc));
    }

    private void updateSelectionMode(JFileChooser fc) {
        list.setSelectionMode(fc.isMultiSelectionEnabled()
                ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                : ListSelectionModel.SINGLE_SELECTION);
    }

    private void updateDirectory(JFileChooser fc) {
        File dir = fc.getCurrentDirectory();
        directoryLabel.setText(dir == null ? "" : fc.getFileSystemView().getSystemDisplayName(dir));
        syncing = true;
        try {
            list.clearSelection();
        } finally {
            syncing = false;
        }
        fc.revalidate();
        fc.repaint();
    }

    private void updateFilters(JFileChooser fc) {
        syncing = true;
        try {
            filterCombo.removeAllItems();
            for (FileFilter filter : fc.getChoosableFileFilters()) {
                filterCombo.addItem(filter);
            }
            filterCombo.setSelectedItem(fc.getFileFilter());
        } finally {
            syncing = false;
        }
    }

    private void updateFileName(JFileChooser fc) {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            File[] files = fc.isMultiSelectionEnabled() ? fc.getSelectedFiles() : new File[0];
            if (files.length > 1) {
                StringBuilder names = new StringBuilder();
                for (File file : files) {
                    if (names.length() > 0) {
                        names.append(' ');
                    }
                    names.append('"').append(file.getName()).append('"');
                }
                setFileName(names.toString());
            } else {
                File file = fc.getSelectedFile();
                setFileName(file == null ? null : file.getName());
            }
        } finally {
            syncing = false;
        }
    }

    /**
     * Returns the file name typed into (or selected from) the UI.
     */
    public String getFileName() {
        return fileNameField == null ? null : fileNameField.getText();
    }

    public String getDirectoryName() {
        // PENDING(jeff) - get the name from the directory combobox
        return null;
    }

    public void setFileName(String filename) {
        if (fileNameField != null) {
            fileNameField.setText(filename == null ? "" : filename);
        }
    }

    public void setDirectoryName(String dirname) {
        // PENDING(jeff) - set the name in the directory combobox
    }

    /**
     * Property to remember whether a directory is currently selected in the UI.
     *
     * @return <code>true</code> iff a directory is currently selected.
     * @since 1.4
     */
    protected boolean isDirectorySelected() {
        return directorySelected;
    }

    protected void setDirectorySelected(boolean b) {
        directorySelected = b;
    }

    protected File getDirectory() {
        return directory;
    }

    protected void setDirectory(File f) {
        directory = f;
    }

    public JFileChooser getFileChooser() {
        return filechooser;
    }

    public JPanel getAccessoryPanel() {
        return accessoryPanel;
    }

    protected JButton getApproveButton(JFileChooser fc) {
        return approveButton;
    }

    public JButton getDefaultButton(JFileChooser fc) {
        return getApproveButton(fc);
    }

    public String getApproveButtonToolTipText(JFileChooser fc) {
        String tooltipText = fc.getApproveButtonToolTipText();
        if (tooltipText != null) {
            return tooltipText;
        }

        if (fc.getDialogType() == JFileChooser.OPEN_DIALOG) {
            return openButtonToolTipText;
        } else if (fc.getDialogType() == JFileChooser.SAVE_DIALOG) {
            return saveButtonToolTipText;
        }
        return null;
    }

    public int getApproveButtonMnemonic(JFileChooser fc) {
        int mnemonic = fc.getApproveButtonMnemonic();
        if (mnemonic > 0) {
            return mnemonic;
        } else if (fc.getDialogType() == JFileChooser.OPEN_DIALOG) {
            return openButtonMnemonic;
        } else if (fc.getDialogType() == JFileChooser.SAVE_DIALOG) {
            return saveButtonMnemonic;
        } else {
            return mnemonic;
        }
    }

    public String getApproveButtonText(JFileChooser fc) {
        String buttonText = fc.getApproveButtonText();
        if (buttonText != null) {
            return buttonText;
        } else if (fc.getDialogType() == JFileChooser.OPEN_DIALOG) {
            return openButtonText;
        } else if (fc.getDialogType() == JFileChooser.SAVE_DIALOG) {
            return saveButtonText;
        } else {
            return null;
        }
    }

    public String getDialogTitle(JFileChooser fc) {
        String dialogTitle = fc.getDialogTitle();
        if (dialogTitle != null) {
            return dialogTitle;
        } else if (fc.getDialogType() == JFileChooser.OPEN_DIALOG) {
            return openDialogTitleText;
        } else if (fc.getDialogType() == JFileChooser.SAVE_DIALOG) {
            return saveDialogTitleText;
        } else {
            return getApproveButtonText(fc);
        }
    }

    public FileFilter getAcceptAllFileFilter(JFileChooser fc) {
        return acceptAllFileFilter;
    }

    public FileView getFileView(JFileChooser fc) {
        return fileView;
    }

    public void rescanCurrentDirectory(JFileChooser fc) {
        getModel().validateFileCache();
    }

    public void ensureFileIsVisible(JFileChooser fc, File f) {
        if (list == null || f == null) {
            return;
        }
        int index = getModel().indexOf(f);
        if (index >= 0) {
            list.ensureIndexIsVisible(index);
        }
    }

    public void clearIconCache() {
        fileView.clearIconCache();
    }

    // ********************************************
    // ************ Create Listeners **************
    // ********************************************

    protected MouseListener createDoubleClickListener(JFileChooser fc, JList list) {
        return new Handler(list);
    }

    public ListSelectionListener createListSelectionListener(JFileChooser fc) {
        return new Handler(null);
    }

    private class Handler extends MouseAdapter implements ListSelectionListener {
        private final JList list;

        Handler(JList list) {
            this.list = list;
        }

        public void mouseClicked(MouseEvent evt) {
            if (list == null || evt.getClickCount() != 2) {
                return;
            }
            JFileChooser fc = getFileChooser();
            int index = list.locationToIndex(evt.getPoint());
            if (index < 0) {
                return;
            }
            File f = (File) list.getModel().getElementAt(index);
            if (fc.isTraversable(f)) {
                list.clearSelection();
                changeDirectory(f);
            } else {
                fc.setSelectedFile(f);
                fc.approveSelection();
            }
        }

        public void valueChanged(ListSelectionEvent evt) {
            if (syncing || evt.getValueIsAdjusting() || BasicFileChooserUI.this.list == null) {
                return;
            }
            JFileChooser fc = getFileChooser();
            List<Object> values = BasicFileChooserUI.this.list.getSelectedValuesList();
            List<File> selectable = new ArrayList<File>();
            File lastDirectory = null;
            for (Object value : values) {
                File f = (File) value;
                boolean isDir = fc.isTraversable(f);
                if (isDir) {
                    lastDirectory = f;
                }
                if (isDir ? fc.isDirectorySelectionEnabled() : fc.isFileSelectionEnabled()) {
                    selectable.add(f);
                }
            }
            setDirectorySelected(lastDirectory != null && selectable.isEmpty());
            setDirectory(lastDirectory);
            if (fc.isMultiSelectionEnabled()) {
                fc.setSelectedFiles(selectable.isEmpty() ? null : selectable.toArray(new File[selectable.size()]));
            } else if (!selectable.isEmpty()) {
                fc.setSelectedFile(selectable.get(0));
            } else if (lastDirectory == null) {
                fc.setSelectedFile(null);
            }
            updateFileName(fc);
            if (isDirectorySelected() && approveButton != null) {
                approveButton.setText(directoryOpenButtonText);
                approveButton.setToolTipText(directoryOpenButtonToolTipText);
            } else if (approveButton != null) {
                updateApproveButton(fc);
            }
        }
    }

    private void changeDirectory(File dir) {
        JFileChooser fc = getFileChooser();
        fc.setCurrentDirectory(dir);
        if (fc.getFileSelectionMode() == JFileChooser.FILES_AND_DIRECTORIES
                && fc.getFileSystemView().isFileSystem(dir)) {
            setFileName(dir.getAbsolutePath());
        }
    }

    // *****************************
    // ***** Directory Actions *****
    // *****************************

    public Action getNewFolderAction() {
        if (newFolderAction == null) {
            newFolderAction = new NewFolderAction();
            // Read-only: the new folder action appears enabled in this case.
            if (getFileChooser() != null && getFileChooser().getFileSystemView() == null) {
                newFolderAction.setEnabled(false);
            }
        }
        return newFolderAction;
    }

    public Action getGoHomeAction() {
        return goHomeAction;
    }

    public Action getChangeToParentDirectoryAction() {
        return changeToParentDirectoryAction;
    }

    public Action getApproveSelectionAction() {
        return approveSelectionAction;
    }

    public Action getCancelSelectionAction() {
        return cancelSelectionAction;
    }

    public Action getUpdateAction() {
        return updateAction;
    }

    /**
     * Creates a new folder.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class NewFolderAction extends AbstractAction {
        protected NewFolderAction() {
            super("New Folder");
        }

        public void actionPerformed(ActionEvent e) {
            JFileChooser fc = getFileChooser();
            File currentDirectory = fc.getCurrentDirectory();
            if (!currentDirectory.exists()) {
                // The JDK shows newFolderParentDoesntExistText in a message dialog.
                return;
            }
            File newFolder;
            try {
                newFolder = fc.getFileSystemView().createNewFolder(currentDirectory);
                if (fc.isMultiSelectionEnabled()) {
                    fc.setSelectedFiles(new File[] { newFolder });
                } else {
                    fc.setSelectedFile(newFolder);
                }
            } catch (IOException exc) {
                javax.swing.JOptionPane.showMessageDialog(fc, newFolderErrorText + ": " + exc.getMessage(),
                        newFolderErrorText, javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
            fc.rescanCurrentDirectory();
        }
    }

    /**
     * Acts on the "home" key event or equivalent event.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class GoHomeAction extends AbstractAction {
        protected GoHomeAction() {
            super("Go Home");
        }

        public void actionPerformed(ActionEvent e) {
            JFileChooser fc = getFileChooser();
            changeDirectory(fc.getFileSystemView().getHomeDirectory());
        }
    }

    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class ChangeToParentDirectoryAction extends AbstractAction {
        protected ChangeToParentDirectoryAction() {
            super("Go Up");
            putValue(Action.ACTION_COMMAND_KEY, "Go Up");
        }

        public void actionPerformed(ActionEvent e) {
            getFileChooser().changeToParentDirectory();
        }
    }

    /**
     * Responds to an Open or Save request.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class ApproveSelectionAction extends AbstractAction {
        protected ApproveSelectionAction() {
            super("approveSelection");
        }

        public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = getFileChooser();
            String filename = getFileName();
            FileSystemView fs = chooser.getFileSystemView();
            File dir = chooser.getCurrentDirectory();

            if (filename == null || filename.trim().isEmpty()) {
                // No typed name: a selected directory is entered, otherwise the
                // list selection (already in the chooser) is approved.
                if (isDirectorySelected() && getDirectory() != null) {
                    changeDirectory(getDirectory());
                    return;
                }
                if (chooser.getSelectedFile() != null) {
                    chooser.approveSelection();
                }
                return;
            }
            filename = filename.trim();

            if (chooser.isMultiSelectionEnabled() && filename.length() > 1
                    && filename.charAt(0) == '"' && filename.charAt(filename.length() - 1) == '"') {
                List<File> fList = new ArrayList<File>();
                String[] names = filename.substring(1, filename.length() - 1).split("\" \"");
                for (String name : names) {
                    File file = fs.createFileObject(name);
                    if (!file.isAbsolute()) {
                        file = fs.getChild(dir, name);
                    }
                    fList.add(file);
                }
                if (!fList.isEmpty()) {
                    chooser.setSelectedFiles(fList.toArray(new File[fList.size()]));
                    chooser.approveSelection();
                }
                return;
            }

            File selectedFile = fs.createFileObject(filename);
            if (!selectedFile.isAbsolute()) {
                selectedFile = fs.getChild(dir, filename);
            }

            // A typed directory is entered, unless directories are what is being chosen.
            if (selectedFile.isDirectory() && chooser.isTraversable(selectedFile)
                    && (!chooser.isDirectorySelectionEnabled() || isDirectorySelected())) {
                changeDirectory(selectedFile);
                return;
            }
            if (!selectedFile.isDirectory() && !chooser.isFileSelectionEnabled()) {
                return;
            }
            if (chooser.isMultiSelectionEnabled()) {
                chooser.setSelectedFiles(new File[] { selectedFile });
            } else {
                chooser.setSelectedFile(selectedFile);
            }
            chooser.approveSelection();
        }
    }

    /**
     * Responds to a cancel request.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class CancelSelectionAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            getFileChooser().cancelSelection();
        }
    }

    /**
     * Rescans the files in the current directory
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class UpdateAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            JFileChooser fc = getFileChooser();
            fc.setCurrentDirectory(fc.getFileSystemView().createFileObject(getDirectoryName()));
            fc.rescanCurrentDirectory();
        }
    }

    // *****************************************
    // ***** default AcceptAll file filter *****
    // *****************************************
    protected class AcceptAllFileFilter extends FileFilter {

        public AcceptAllFileFilter() {
        }

        public boolean accept(File f) {
            return true;
        }

        public String getDescription() {
            return string("FileChooser.acceptAllFileFilterText", Locale.getDefault(), "All Files");
        }
    }

    // ***********************
    // * FileView operations *
    // ***********************
    protected class BasicFileView extends FileView {
        /* FileView type descriptions */
        // PENDING(jeff) - pass in the icon cache size
        protected Map<File, Icon> iconCache = new HashMap<File, Icon>();

        public BasicFileView() {
        }

        public void clearIconCache() {
            iconCache = new HashMap<File, Icon>();
        }

        public String getName(File f) {
            // Note: Returns display name rather than file name
            String fileName = null;
            if (f != null) {
                fileName = getFileChooser().getFileSystemView().getSystemDisplayName(f);
            }
            return fileName;
        }

        public String getDescription(File f) {
            return f.getName();
        }

        public String getTypeDescription(File f) {
            String type = getFileChooser().getFileSystemView().getSystemTypeDescription(f);
            if (type == null) {
                if (f.isDirectory()) {
                    type = string("FileChooser.directoryDescriptionText", Locale.getDefault(), "Directory");
                } else {
                    type = string("FileChooser.fileDescriptionText", Locale.getDefault(), "Generic File");
                }
            }
            return type;
        }

        public Icon getCachedIcon(File f) {
            return iconCache.get(f);
        }

        public void cacheIcon(File f, Icon i) {
            if (f == null || i == null) {
                return;
            }
            iconCache.put(f, i);
        }

        public Icon getDefaultIcon(File f) {
            Icon icon = getFileChooser().getFileSystemView().getSystemIcon(f);
            if (icon == null) {
                icon = f.isDirectory() ? directoryIcon : fileIcon;
            }
            return icon;
        }

        public Icon getIcon(File f) {
            Icon icon = getCachedIcon(f);
            if (icon != null) {
                return icon;
            }
            icon = getDefaultIcon(f);
            cacheIcon(f, icon);
            return icon;
        }

        public boolean isHidden(File f) {
            String name = f.getName();
            return name != null && name.charAt(0) == '.';
        }
    }

    @SuppressWarnings("serial") // Superclass is not serializable across versions
    private class FileRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            JFileChooser fc = getFileChooser();
            if (value instanceof File && fc != null) {
                File file = (File) value;
                String name = fc.getName(file);
                setText(name != null ? name : file.getName());
                setIcon(fc.getIcon(file));
            }
            return this;
        }
    }

    @SuppressWarnings("serial") // Superclass is not serializable across versions
    private class FilterRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileFilter) {
                setText(((FileFilter) value).getDescription());
            }
            return this;
        }
    }
}
