/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.io.File;
import java.io.FilenameFilter;

import javax.swing.JFileChooser;

/**
 * The <code>FileDialog</code> class displays a dialog window
 * from which the user can select a file.
 * <p>
 * Since it is a modal dialog, when the application calls
 * its <code>show</code> method to display the dialog,
 * it blocks the rest of the application until the user has
 * chosen a file.
 * <p>
 * On Android there is no native file dialog peer. {@link #setVisible(boolean)
 * setVisible(true)} hands the request to the host's system picker through the
 * {@link JFileChooser} dialog bridge (the app installs a
 * {@code JFileChooser.DialogHandler} that opens the Storage Access Framework
 * picker and blocks the caller until it returns); the result is written back to
 * {@link #getDirectory()}, {@link #getFile()} and {@link #getFiles()} exactly as
 * the desktop peer does. Without an installed handler (host tests, or a call
 * the bridge refuses) the dialog behaves as cancelled.
 *
 * @see Window#show
 *
 * @author      Sami Shaio
 * @author      Arthur van Hoff
 * @since       JDK1.0
 */
public class FileDialog extends Dialog {

    /**
     * This constant value indicates that the purpose of the file
     * dialog window is to locate a file from which to read.
     */
    public static final int LOAD = 0;

    /**
     * This constant value indicates that the purpose of the file
     * dialog window is to locate a file to which to write.
     */
    public static final int SAVE = 1;

    /*
     * There are two <code>FileDialog</code> modes: <code>LOAD</code> and
     * <code>SAVE</code>.
     * This integer will represent one or the other.
     * If the mode is not specified it will default to <code>LOAD</code>.
     *
     * @serial
     * @see getMode()
     * @see setMode()
     * @see java.awt.FileDialog#LOAD
     * @see java.awt.FileDialog#SAVE
     */
    int mode;

    /*
     * The string specifying the directory to display
     * in the file dialog.  This variable may be <code>null</code>.
     *
     * @serial
     * @see getDirectory()
     * @see setDirectory()
     */
    String dir;

    /*
     * The string specifying the initial value of the
     * filename text field in the file dialog.
     * This variable may be <code>null</code>.
     *
     * @serial
     * @see getFile()
     * @see setFile()
     */
    String file;

    /**
     * Contains the File instances for all the files that the user selects.
     *
     * @serial
     * @see #getFiles
     * @since 1.7
     */
    private File[] files;

    /**
     * Represents whether the file dialog allows the multiple file selection.
     *
     * @serial
     * @see #setMultipleMode
     * @see #isMultipleMode
     * @since 1.7
     */
    private boolean multipleMode = false;

    /*
     * The filter used as the file dialog's filename filter.
     * The file dialog will only be displaying files whose
     * names are accepted by this filter.
     * This variable may be <code>null</code>.
     *
     * @serial
     * @see #getFilenameFilter()
     * @see #setFilenameFilter()
     * @see FileNameFilter
     */
    FilenameFilter filter;

    private static final String base = "filedlg";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
     private static final long serialVersionUID = 5035145889651310422L;

    /**
     * Creates a file dialog for loading a file.  The title of the
     * file dialog is initially empty.  This is a convenience method for
     * <code>FileDialog(parent, "", LOAD)</code>.
     *
     * @param parent the owner of the dialog
     * @since JDK1.1
     */
    public FileDialog(Frame parent) {
        this(parent, "", LOAD);
    }

    /**
     * Creates a file dialog window with the specified title for loading
     * a file. The files shown are those in the current directory.
     * This is a convenience method for
     * <code>FileDialog(parent, title, LOAD)</code>.
     *
     * @param     parent   the owner of the dialog
     * @param     title    the title of the dialog
     */
    public FileDialog(Frame parent, String title) {
        this(parent, title, LOAD);
    }

    /**
     * Creates a file dialog window with the specified title for loading
     * or saving a file.
     *
     * @param     parent   the owner of the dialog
     * @param     title   the title of the dialog
     * @param     mode   the mode of the dialog; either
     *            <code>FileDialog.LOAD</code> or <code>FileDialog.SAVE</code>
     * @exception  IllegalArgumentException if an illegal file
     *                 dialog mode is supplied
     */
    public FileDialog(Frame parent, String title, int mode) {
        super(parent, title, true);
        this.setMode(mode);
        setLayout(null);
    }

    /**
     * Creates a file dialog for loading a file.  The title of the
     * file dialog is initially empty.  This is a convenience method for
     * <code>FileDialog(parent, "", LOAD)</code>.
     *
     * @param     parent   the owner of the dialog
     * @since 1.5
     */
    public FileDialog(Dialog parent) {
        this(parent, "", LOAD);
    }

    /**
     * Creates a file dialog window with the specified title for loading
     * a file. The files shown are those in the current directory.
     * This is a convenience method for
     * <code>FileDialog(parent, title, LOAD)</code>.
     *
     * @param     parent   the owner of the dialog
     * @param     title    the title of the dialog; a <code>null</code> value
     *                     will be accepted without causing a
     *                     <code>NullPointerException</code> to be thrown
     * @since     1.5
     */
    public FileDialog(Dialog parent, String title) {
        this(parent, title, LOAD);
    }

    /**
     * Creates a file dialog window with the specified title for loading
     * or saving a file.
     *
     * @param     parent   the owner of the dialog
     * @param     title    the title of the dialog; a <code>null</code> value
     *                     will be accepted without causing a
     *                     <code>NullPointerException</code> to be thrown
     * @param     mode     the mode of the dialog; either
     *                     <code>FileDialog.LOAD</code> or <code>FileDialog.SAVE</code>
     * @exception  IllegalArgumentException if an illegal
     *            file dialog mode is supplied
     * @since     1.5
     */
    public FileDialog(Dialog parent, String title, int mode) {
        super(parent, title, true);
        this.setMode(mode);
        setLayout(null);
    }

    /**
     * Constructs a name for this component. Called by <code>getName()</code>
     * when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (FileDialog.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Indicates whether this file dialog box is for loading from a file
     * or for saving to a file.
     *
     * @return   the mode of this file dialog window, either
     *               <code>FileDialog.LOAD</code> or
     *               <code>FileDialog.SAVE</code>
     * @see      java.awt.FileDialog#LOAD
     * @see      java.awt.FileDialog#SAVE
     * @see      java.awt.FileDialog#setMode
     */
    public int getMode() {
        return mode;
    }

    /**
     * Sets the mode of the file dialog.  If <code>mode</code> is not
     * a legal value, an exception will be thrown and <code>mode</code>
     * will not be set.
     *
     * @param      mode  the mode for this file dialog, either
     *                 <code>FileDialog.LOAD</code> or
     *                 <code>FileDialog.SAVE</code>
     * @see        java.awt.FileDialog#LOAD
     * @see        java.awt.FileDialog#SAVE
     * @see        java.awt.FileDialog#getMode
     * @exception  IllegalArgumentException if an illegal file
     *                 dialog mode is supplied
     * @since      JDK1.1
     */
    public void setMode(int mode) {
        switch (mode) {
          case LOAD:
          case SAVE:
              this.mode = mode;
              break;
          default:
              throw new IllegalArgumentException("illegal file dialog mode");
        }
    }

    /**
     * Gets the directory of this file dialog.
     *
     * @return  the (potentially <code>null</code> or invalid)
     *          directory of this <code>FileDialog</code>
     * @see       java.awt.FileDialog#setDirectory
     */
    public String getDirectory() {
        return dir;
    }

    /**
     * Sets the directory of this file dialog window to be the
     * specified directory. Specifying a <code>null</code> or an
     * invalid directory implies an implementation-defined default.
     * Specifying an empty string as the directory
     * is equivalent to specifying <code>null</code> as the directory.
     *
     * @param     dir   the specified directory
     * @see       java.awt.FileDialog#getDirectory
     */
    public void setDirectory(String dir) {
        this.dir = (dir != null && dir.equals("")) ? null : dir;
    }

    /**
     * Gets the selected file of this file dialog.  If the user
     * selected <code>CANCEL</code>, the returned file is <code>null</code>.
     *
     * @return    the currently selected file of this file dialog window,
     *                or <code>null</code> if none is selected
     * @see       java.awt.FileDialog#setFile
     */
    public String getFile() {
        return file;
    }

    /**
     * Returns files that the user selects.
     * <p>
     * If the user cancels the file dialog,
     * then the method returns an empty array.
     *
     * @return    files that the user selects or an empty array
     *            if the user cancels the file dialog.
     * @see       #setFile(String)
     * @see       #getFile
     * @since 1.7
     */
    public File[] getFiles() {
        synchronized (getTreeLock()) {
            if (files != null) {
                return files.clone();
            } else {
                return new File[0];
            }
        }
    }

    /**
     * Stores the names of all the files that the user selects.
     *
     * Note that the method is private and it's intended to be used
     * by the peers through the AWTAccessor API.
     *
     * @param files     the array that contains the short names of
     *                  all the files that the user selects.
     *
     * @see #getFiles
     * @since 1.7
     */
    private void setFiles(File files[]) {
        synchronized (getTreeLock()) {
            this.files = files;
        }
    }

    /**
     * Sets the selected file for this file dialog window to be the
     * specified file. This file becomes the default file if it is set
     * before the file dialog window is first shown.
     * <p>
     * When the dialog is shown, the specified file is selected. The kind of
     * selection depends on the file existence, the dialog type, and the native
     * platform. E.g., the file could be highlighted in the file list, or a
     * file name editbox could be populated with the file name.
     * <p>
     * Specifying an empty string as the file is exactly equivalent to
     * specifying <code>null</code> as the file.
     *
     * @param    file   the file being set
     * @see      #getFile
     * @see      #getFiles
     */
    public void setFile(String file) {
        this.file = (file != null && file.equals("")) ? null : file;
    }

    /**
     * Enables or disables multiple file selection for the file dialog.
     *
     * @param enable    if {@code true}, multiple file selection is enabled;
     *                  {@code false} - disabled.
     * @see #isMultipleMode
     * @since 1.7
     */
    public void setMultipleMode(boolean enable) {
        synchronized (getTreeLock()) {
            this.multipleMode = enable;
        }
    }

    /**
     * Returns whether the file dialog allows the multiple file selection.
     *
     * @return          {@code true} if the file dialog allows the multiple
     *                  file selection; {@code false} otherwise.
     * @see #setMultipleMode
     * @since 1.7
     */
    public boolean isMultipleMode() {
        synchronized (getTreeLock()) {
            return multipleMode;
        }
    }

    /**
     * Determines this file dialog's filename filter. A filename filter
     * allows the user to specify which files appear in the file dialog
     * window.
     *
     * @return    this file dialog's filename filter
     * @see       java.io.FilenameFilter
     * @see       java.awt.FileDialog#setFilenameFilter
     */
    public FilenameFilter getFilenameFilter() {
        return filter;
    }

    /**
     * Sets the filename filter for this file dialog window to the
     * specified filter.
     * The system picker cannot filter by an arbitrary predicate, so the
     * filter is applied to what the user picks: a pick it rejects is
     * treated as a cancel by the dialog bridge.
     *
     * @param   filter   the specified filter
     * @see     java.io.FilenameFilter
     * @see     java.awt.FileDialog#getFilenameFilter
     */
    public synchronized void setFilenameFilter(FilenameFilter filter) {
        this.filter = filter;
    }

    /**
     * Shows or hides the dialog. Showing it runs the host picker and blocks
     * until the user picks or cancels, as the modal desktop dialog does; the
     * dialog itself is never made visible, so there is nothing for a later
     * {@code setVisible(false)} to undo beyond the default.
     *
     * @param b {@code true} to show the picker, {@code false} to hide
     */
    @Override
    public void setVisible(boolean b) {
        if (!b) {
            super.setVisible(false);
            return;
        }
        // Not super.setVisible(true): a modal Dialog blocks until hidden, and there is no
        // peer to hide it. The picker call is the blocking part.
        JFileChooser chooser = new JFileChooser();
        if (dir != null) {
            chooser.setCurrentDirectory(new File(dir));
        }
        if (file != null) {
            chooser.setSelectedFile(dir != null ? new File(dir, file) : new File(file));
        }
        chooser.setMultiSelectionEnabled(isMultipleMode());
        chooser.setDialogTitle(getTitle());
        chooser.setDialogType(mode == SAVE ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
        final FilenameFilter nameFilter = filter;
        if (nameFilter != null) {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || nameFilter.accept(f.getParentFile(), f.getName());
                }

                @Override
                public String getDescription() {
                    return "";
                }
            });
        }
        int result = chooser.showDialog(getParent(), null);
        File[] picked = null;
        if (result == JFileChooser.APPROVE_OPTION) {
            picked = chooser.getSelectedFiles();
            if (picked.length == 0 && chooser.getSelectedFile() != null) {
                picked = new File[] { chooser.getSelectedFile() };
            }
        }
        if (picked != null && picked.length > 0) {
            File first = picked[0];
            String parent = first.getParent();
            this.file = first.getName();
            this.dir = parent == null ? null : parent + File.separator;
            setFiles(picked);
        } else {
            this.file = null;
            this.dir = null;
            setFiles(null);
        }
    }

    /**
     * Returns a string representing the state of this <code>FileDialog</code>
     * window. This method is intended to be used only for debugging
     * purposes, and the content and format of the returned string may vary
     * between implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return  the parameter string of this file dialog window
     */
    protected String paramString() {
        // 8u prefixes Dialog.paramString(); the shadow's Component chain has no paramString,
        // so the FileDialog part stands alone.
        String str = "title=" + getTitle();
        str += ",dir= " + dir;
        str += ",file= " + file;
        return str + ((mode == LOAD) ? ",load" : ",save");
    }

    boolean postsOldMouseEvents() {
        return false;
    }
}
