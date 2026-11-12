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
import java.io.File;
import javax.swing.filechooser.FileFilter;

public class JFileChooser extends JComponent implements javax.accessibility.Accessible {
    private static final String uiClassID = "FileChooserUI";
    public javax.swing.plaf.FileChooserUI getUI() { return (javax.swing.plaf.FileChooserUI) ui; }
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJFileChooser();
        }
        return accessibleContext;
    }

    /** Accessibility support for file choosers: role FILE_CHOOSER. */
    protected class AccessibleJFileChooser extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.FILE_CHOOSER;
        }
    }
    private static final long serialVersionUID = 1L;

    public static final int OPEN_DIALOG = 0;
    public static final int SAVE_DIALOG = 1;
    public static final int CUSTOM_DIALOG = 2;
    public static final int CANCEL_OPTION = 1;
    public static final int APPROVE_OPTION = 0;
    public static final int ERROR_OPTION = -1;
    public static final int FILES_ONLY = 0;
    public static final int DIRECTORIES_ONLY = 1;
    public static final int FILES_AND_DIRECTORIES = 2;

    private File currentDirectory = new File(System.getProperty("user.home", "."));
    private File selectedFile;
    private File[] selectedFiles = new File[0];
    private int fileSelectionMode = FILES_ONLY;
    private boolean multiSelectionEnabled;
    private FileFilter fileFilter;
    private final java.util.List<FileFilter> filters = new java.util.ArrayList<>();
    private final FileFilter acceptAll = new FileFilter() {
        @Override public boolean accept(File file) { return true; }
        @Override public String getDescription() { return "All files"; }
    };
    private boolean acceptAllFileFilterUsed = true;
    public void setAcceptAllFileFilterUsed(boolean used) {
        boolean previous = acceptAllFileFilterUsed; acceptAllFileFilterUsed = used;
        if (!used && fileFilter == acceptAll) fileFilter = filters.isEmpty() ? null : filters.get(0);
        firePropertyChange("acceptAllFileFilterUsedChanged", previous, used);
    }
    public boolean isAcceptAllFileFilterUsed() { return acceptAllFileFilterUsed; }
    // Kept for callers that toggle it; the SAF picker decides itself which entries it lists.
    private boolean useFileHiding = true;
    public boolean isFileHidingEnabled() { return useFileHiding; }
    public void setFileHidingEnabled(boolean b) {
        boolean previous = useFileHiding; useFileHiding = b;
        firePropertyChange("FileHidingChanged", previous, b);
    }
    private String dialogTitle;
    private int dialogType = OPEN_DIALOG;
    public interface DialogHandler { int show(JFileChooser chooser, boolean save); }
    private static volatile DialogHandler dialogHandler;
    public static void setDialogHandler(DialogHandler handler) { dialogHandler = handler; }

    public JFileChooser() { fileFilter = acceptAll; }
    public JFileChooser(String currentDirectoryPath) { this(); if (currentDirectoryPath != null) setCurrentDirectory(new File(currentDirectoryPath)); }
    public JFileChooser(File currentDirectory) { this(); setCurrentDirectory(currentDirectory); }

    public File getSelectedFile() { return selectedFile; }
    public void setSelectedFile(File file) {
        File previous = selectedFile; selectedFile = file;
        firePropertyChange("SelectedFileChangedProperty", previous, file);
    }
    public File[] getSelectedFiles() { return selectedFiles.clone(); }
    public void setSelectedFiles(File[] files) {
        File[] previous = selectedFiles;
        selectedFiles = files == null ? new File[0] : files.clone();
        setSelectedFile(selectedFiles.length == 0 ? null : selectedFiles[0]);
        firePropertyChange("SelectedFilesChangedProperty", previous, selectedFiles.clone());
    }
    public File getCurrentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(File dir) {
        if (dir == null) dir = new File(System.getProperty("user.home", "."));
        if (dir.isFile()) dir = dir.getParentFile();
        File previous = currentDirectory; currentDirectory = dir;
        firePropertyChange("directoryChanged", previous, dir);
    }
    public int getFileSelectionMode() { return fileSelectionMode; }
    public void setFileSelectionMode(int mode) {
        if (mode < FILES_ONLY || mode > FILES_AND_DIRECTORIES) throw new IllegalArgumentException("Invalid file selection mode");
        int previous = fileSelectionMode; fileSelectionMode = mode;
        firePropertyChange("fileSelectionChanged", previous, mode);
    }
    public boolean isFileSelectionEnabled() { return fileSelectionMode != DIRECTORIES_ONLY; }
    public boolean isDirectorySelectionEnabled() { return fileSelectionMode != FILES_ONLY; }
    public boolean isMultiSelectionEnabled() { return multiSelectionEnabled; }
    public void setMultiSelectionEnabled(boolean b) { boolean previous = multiSelectionEnabled; multiSelectionEnabled = b; firePropertyChange("MultiSelectionEnabledChangedProperty", previous, b); }
    public FileFilter getFileFilter() { return fileFilter; }
    public void setFileFilter(FileFilter filter) {
        if (filter != null && filter != acceptAll && !filters.contains(filter)) filters.add(filter);
        FileFilter previous = fileFilter; fileFilter = filter;
        firePropertyChange("fileFilterChanged", previous, filter);
    }
    public void addChoosableFileFilter(FileFilter filter) {
        if (filter != null && filter != acceptAll && !filters.contains(filter)) filters.add(filter);
        if (fileFilter == null) setFileFilter(filter);
    }
    public boolean removeChoosableFileFilter(FileFilter filter) {
        boolean removed = filters.remove(filter);
        if (removed && fileFilter == filter) setFileFilter(acceptAllFileFilterUsed ? acceptAll : filters.isEmpty() ? null : filters.get(0));
        return removed;
    }
    public FileFilter[] getChoosableFileFilters() {
        java.util.List<FileFilter> result = new java.util.ArrayList<>(filters);
        if (acceptAllFileFilterUsed) result.add(0, acceptAll);
        return result.toArray(new FileFilter[0]);
    }
    public FileFilter getAcceptAllFileFilter() { return acceptAll; }
    public void resetChoosableFileFilters() { filters.clear(); setFileFilter(acceptAllFileFilterUsed ? acceptAll : null); }
    public boolean accept(File file) { return fileFilter == null || fileFilter.accept(file); }
    public int showOpenDialog(Component parent) { dialogType = OPEN_DIALOG; return showDialog(parent, null); }
    public int showSaveDialog(Component parent) { dialogType = SAVE_DIALOG; return showDialog(parent, null); }
    public int showDialog(Component parent, String approveButtonText) {
        DialogHandler handler = dialogHandler;
        return handler == null ? CANCEL_OPTION : handler.show(this, dialogType == SAVE_DIALOG);
    }
    public void setDialogTitle(String title) { dialogTitle = title; }
    public String getDialogTitle() { return dialogTitle; }
    public void setDialogType(int type) { dialogType = type; }
    public int getDialogType() { return dialogType; }
}
