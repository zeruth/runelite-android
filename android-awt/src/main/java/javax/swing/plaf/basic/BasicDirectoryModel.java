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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import javax.swing.AbstractListModel;
import javax.swing.JFileChooser;
import javax.swing.event.ListDataEvent;
import javax.swing.filechooser.FileSystemView;

/**
 * Basic implementation of a file list.
 * <p>
 * The JDK lists the directory on a background loader thread and publishes
 * the result on the EDT. The shadow lists synchronously when the cache is
 * validated: an Android chooser browses app storage, which is small, and a
 * synchronous list keeps the model consistent for code that reads it right
 * after changing directory. The "busy" property still fires around a load.
 *
 * @author Jeff Dinkins
 */
@SuppressWarnings("serial") // Superclass is not serializable across versions
public class BasicDirectoryModel extends AbstractListModel<Object> implements PropertyChangeListener {

    private JFileChooser filechooser = null;
    // PENDING(jeff) pick the size more sensibly
    private Vector<File> fileCache = new Vector<File>(50);

    // Takes care of property change listeners
    private PropertyChangeSupport changeSupport;

    private boolean busy = false;

    private Vector<File> files = null;
    private Vector<File> directories = null;

    public BasicDirectoryModel(JFileChooser filechooser) {
        this.filechooser = filechooser;
        validateFileCache();
    }

    public void propertyChange(PropertyChangeEvent e) {
        String prop = e.getPropertyName();
        if(prop == JFileChooser.DIRECTORY_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_VIEW_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_FILTER_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_HIDING_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY) {
            validateFileCache();
        } else if ("UI".equals(prop)) {
            Object old = e.getOldValue();
            if (old instanceof BasicFileChooserUI) {
                BasicFileChooserUI ui = (BasicFileChooserUI) old;
                BasicDirectoryModel model = ui.getModel();
                if (model != null) {
                    model.invalidateFileCache();
                }
            }
        } else if ("JFileChooserDialogIsClosingProperty".equals(prop)) {
            invalidateFileCache();
        }
    }

    /**
     * This method is used to interrupt file loading thread.
     */
    public void invalidateFileCache() {
        // Nothing is loading in the background; the next validate relists.
    }

    /**
     * Returns the directories of the current listing.
     */
    public Vector<File> getDirectories() {
        synchronized(fileCache) {
            if (directories != null) {
                return directories;
            }
            getFiles();
            return directories;
        }
    }

    /**
     * Returns the files (not directories) of the current listing.
     */
    public Vector<File> getFiles() {
        synchronized(fileCache) {
            if (files != null) {
                return files;
            }
            files = new Vector<File>();
            directories = new Vector<File>();
            directories.addElement(filechooser.getFileSystemView().createFileObject(
                filechooser.getCurrentDirectory(), "..")
            );

            for (int i = 0; i < getSize(); i++) {
                File f = fileCache.get(i);
                if (filechooser.isTraversable(f)) {
                    directories.add(f);
                } else {
                    files.add(f);
                }
            }
            return files;
        }
    }

    public void validateFileCache() {
        File currentDirectory = filechooser.getCurrentDirectory();
        if (currentDirectory == null) {
            return;
        }
        setBusy(true);
        FileSystemView fileSystem = filechooser.getFileSystemView();
        File[] list = fileSystem.getFiles(currentDirectory, filechooser.isFileHidingEnabled());

        Vector<File> newFileCache = new Vector<File>();
        Vector<File> newFiles = new Vector<File>();

        // run through the file list, add directories and selectable files to fileCache
        // Note that this block must be OUTSIDE of Invoker thread because of
        // deadlock possibility with custom synchronized FileSystemView
        for (File file : list) {
            if (filechooser.accept(file)) {
                boolean isTraversable = filechooser.isTraversable(file);

                if (isTraversable) {
                    newFileCache.addElement(file);
                } else if (filechooser.isFileSelectionEnabled()) {
                    newFiles.addElement(file);
                }
            }
        }

        // First sort alphabetically by filename
        sort(newFileCache);
        sort(newFiles);

        newFileCache.addAll(newFiles);

        synchronized (fileCache) {
            fileCache.clear();
            fileCache.addAll(newFileCache);
            files = null;
            directories = null;
        }
        fireContentsChanged();
        setBusy(false);
    }

    /**
     * Renames a file in the underlying file system.
     *
     * @param oldFile a <code>File</code> object representing
     *        the existing file
     * @param newFile a <code>File</code> object representing
     *        the desired new file name
     * @return <code>true</code> if rename succeeded,
     *        otherwise <code>false</code>
     * @since 1.4
     */
    public boolean renameFile(File oldFile, File newFile) {
        synchronized(fileCache) {
            if (oldFile.renameTo(newFile)) {
                validateFileCache();
                return true;
            }
            return false;
        }
    }


    public void fireContentsChanged() {
        // System.out.println("BasicDirectoryModel: firecontentschanged");
        fireContentsChanged(this, 0, getSize()-1);
    }

    public int getSize() {
        return fileCache.size();
    }

    public boolean contains(Object o) {
        return fileCache.contains(o);
    }

    public int indexOf(Object o) {
        return fileCache.indexOf(o);
    }

    public Object getElementAt(int index) {
        return fileCache.get(index);
    }

    /**
     * Obsolete - not used.
     */
    public void intervalAdded(ListDataEvent e) {
    }

    /**
     * Obsolete - not used.
     */
    public void intervalRemoved(ListDataEvent e) {
    }

    protected void sort(Vector<? extends File> v){
        Collections.sort(v, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
    }

    // Obsolete - not used
    protected boolean lt(File a, File b) {
        // First ignore case when comparing
        int diff = a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
        if (diff != 0) {
            return diff < 0;
        } else {
            // May differ in case (e.g. "mail" vs. "Mail")
            return a.getName().compareTo(b.getName()) < 0;
        }
    }

    /**
     * Adds a PropertyChangeListener to the listener list. The listener is
     * registered for all bound properties of this class.
     *
     * @param listener  the property change listener to be added
     * @since 1.6
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new PropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     * @since 1.6
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }

    /**
     * Returns an array of all the property change listeners
     * registered on this component.
     *
     * @return all of this component's <code>PropertyChangeListener</code>s
     *         or an empty array if no property change
     *         listeners are currently registered
     * @since 1.6
     */
    public PropertyChangeListener[] getPropertyChangeListeners() {
        if (changeSupport == null) {
            return new PropertyChangeListener[0];
        }
        return changeSupport.getPropertyChangeListeners();
    }

    /**
     * Support for reporting bound property changes for boolean properties.
     *
     * @param propertyName the property whose value has changed
     * @param oldValue the property's previous value
     * @param newValue the property's new value
     * @since 1.6
     */
    protected void firePropertyChange(String propertyName,
                                      Object oldValue, Object newValue) {
        if (changeSupport != null) {
            changeSupport.firePropertyChange(propertyName,
                                             oldValue, newValue);
        }
    }

    /**
     * Set the busy state for the model.
     */
    private synchronized void setBusy(final boolean busy) {
        if (busy != this.busy) {
            this.busy = busy;
            firePropertyChange("busy", !busy, busy);
        }
    }
}
