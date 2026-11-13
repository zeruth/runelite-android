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
package com.sun.swing.internal.plaf.basic.resources;

import java.util.ListResourceBundle;

/**
 * The English entries of the JDK's {@code basic.properties}, which BasicLookAndFeel
 * registers with {@code UIDefaults.addResourceBundle}. The JDK generates this class
 * from the properties file at build time; the shadow carries it as a class so R8 keeps
 * it with the rest of {@code com.sun.**}. Keys use the JDK's {@code .textAndMnemonic}
 * form: UIDefaults' TextAndMnemonicHashMap derives the {@code xxxText} and
 * {@code xxxMnemonic} keys the delegates read (OptionPane.yesButtonText, ...).
 */
public final class basic extends ListResourceBundle {
    @Override
    protected final Object[][] getContents() {
        return new Object[][] {
            { "AbstractButton.click.textAndMnemonic", "click" },
            { "AbstractDocument.addition.textAndMnemonic", "addition" },
            { "AbstractDocument.deletion.textAndMnemonic", "deletion" },
            { "AbstractDocument.redo.textAndMnemonic", "Redo" },
            { "AbstractDocument.styleChange.textAndMnemonic", "style change" },
            { "AbstractDocument.undo.textAndMnemonic", "Undo" },
            { "AbstractUndoableEdit.redo.textAndMnemonic", "Redo" },
            { "AbstractUndoableEdit.undo.textAndMnemonic", "Undo" },
            { "ColorChooser.cancel.textAndMnemonic", "Cancel" },
            { "ColorChooser.hsb.textAndMnemonic", "&HSB" },
            { "ColorChooser.hsbBlue.textAndMnemonic", "B" },
            { "ColorChooser.hsbBrightness.textAndMnemonic", "B" },
            { "ColorChooser.hsbGreen.textAndMnemonic", "G" },
            { "ColorChooser.hsbHue.textAndMnemonic", "H" },
            { "ColorChooser.hsbRed.textAndMnemonic", "R" },
            { "ColorChooser.hsbSaturation.textAndMnemonic", "S" },
            { "ColorChooser.ok.textAndMnemonic", "OK" },
            { "ColorChooser.preview.textAndMnemonic", "Preview" },
            { "ColorChooser.reset.textAndMnemonic", "&Reset" },
            { "ColorChooser.rgb.textAndMnemonic", "R&GB" },
            { "ColorChooser.rgbBlue.textAndMnemonic", "&Blue" },
            { "ColorChooser.rgbGreen.textAndMnemonic", "Gree&n" },
            { "ColorChooser.rgbRed.textAndMnemonic", "Re&d" },
            { "ColorChooser.sample.textAndMnemonic", "Sample Text  Sample Text" },
            { "ColorChooser.swatches.textAndMnemonic", "&Swatches" },
            { "ColorChooser.swatchesRecent.textAndMnemonic", "Recent:" },
            { "FileChooser.acceptAllFileFilter.textAndMnemonic", "All Files" },
            { "FileChooser.cancelButton.textAndMnemonic", "Cancel" },
            { "FileChooser.cancelButtonToolTip.textAndMnemonic", "Abort file chooser dialog" },
            { "FileChooser.directoryDescription.textAndMnemonic", "Directory" },
            { "FileChooser.directoryOpenButton.textAndMnemonic", "Open" },
            { "FileChooser.directoryOpenButtonToolTip.textAndMnemonic", "Open selected directory" },
            { "FileChooser.fileDescription.textAndMnemonic", "Generic File" },
            { "FileChooser.helpButton.textAndMnemonic", "Help" },
            { "FileChooser.helpButtonToolTip.textAndMnemonic", "FileChooser help" },
            { "FileChooser.newFolderError.textAndMnemonic", "Error creating new folder" },
            { "FileChooser.newFolderErrorSeparator", ": " },
            { "FileChooser.openButton.textAndMnemonic", "Open" },
            { "FileChooser.openButtonToolTip.textAndMnemonic", "Open selected file" },
            { "FileChooser.openDialogTitle.textAndMnemonic", "Open" },
            { "FileChooser.other.newFolder", "NewFolder" },
            { "FileChooser.other.newFolder.subsequent", "NewFolder.{0}" },
            { "FileChooser.saveButton.textAndMnemonic", "Save" },
            { "FileChooser.saveButtonToolTip.textAndMnemonic", "Save selected file" },
            { "FileChooser.saveDialogTitle.textAndMnemonic", "Save" },
            { "FileChooser.updateButton.textAndMnemonic", "Update" },
            { "FileChooser.updateButtonToolTip.textAndMnemonic", "Update directory listing" },
            { "FileChooser.win32.newFolder", "New Folder" },
            { "FileChooser.win32.newFolder.subsequent", "New Folder ({0})" },
            { "FormView.browseFileButton.textAndMnemonic", "Browse..." },
            { "FormView.resetButton.textAndMnemonic", "Reset" },
            { "FormView.submitButton.textAndMnemonic", "Submit Query" },
            { "InternalFrame.closeButtonToolTip", "Close" },
            { "InternalFrame.iconButtonToolTip", "Minimize" },
            { "InternalFrame.maxButtonToolTip", "Maximize" },
            { "InternalFrame.restoreButtonToolTip", "Restore" },
            { "InternalFrameTitlePane.closeButton.textAndMnemonic", "Close" },
            { "InternalFrameTitlePane.maximizeButton.textAndMnemonic", "Maximize" },
            { "InternalFrameTitlePane.minimizeButton.textAndMnemonic", "Minimize" },
            { "InternalFrameTitlePane.moveButton.textAndMnemonic", "Move" },
            { "InternalFrameTitlePane.restoreButton.textAndMnemonic", "Restore" },
            { "InternalFrameTitlePane.sizeButton.textAndMnemonic", "Size" },
            { "IsindexView.prompt", "This is a searchable index.  Enter search keywords:" },
            { "OptionPane.cancelButton.textAndMnemonic", "Cancel" },
            { "OptionPane.inputDialog.titleAndMnemonic", "Input" },
            { "OptionPane.messageDialog.titleAndMnemonic", "Message" },
            { "OptionPane.noButton.textAndMnemonic", "&No" },
            { "OptionPane.okButton.textAndMnemonic", "OK" },
            { "OptionPane.title.textAndMnemonic", "Select an Option" },
            { "OptionPane.yesButton.textAndMnemonic", "&Yes" },
            { "PrintingDialog.abortButton.textAndMnemonic", "&Abort" },
            { "PrintingDialog.abortButtonToolTip.textAndMnemonic", "Abort Printing" },
            { "PrintingDialog.contentAborting.textAndMnemonic", "Printing aborting..." },
            { "PrintingDialog.contentInitial.textAndMnemonic", "Printing in progress..." },
            { "PrintingDialog.contentProgress.textAndMnemonic", "Printed page {0}..." },
            { "PrintingDialog.titleAborting.textAndMnemonic", "Printing (Aborting)" },
            { "PrintingDialog.titleProgress.textAndMnemonic", "Printing" },
            { "ProgressMonitor.progress.textAndMnemonic", "Progress..." },
            { "SplitPane.leftButton.textAndMnemonic", "left button" },
            { "SplitPane.rightButton.textAndMnemonic", "right button" },
        };
    }
}
