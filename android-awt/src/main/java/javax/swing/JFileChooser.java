package javax.swing;

import java.awt.Component;
import java.io.File;
import javax.swing.filechooser.FileFilter;

public class JFileChooser extends JComponent {
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

    private File currentDirectory = new File(".");
    private File selectedFile;
    private int fileSelectionMode = FILES_ONLY;
    private boolean multiSelectionEnabled;
    private FileFilter fileFilter;
    private boolean acceptAllFileFilterUsed = true;
    public void setAcceptAllFileFilterUsed(boolean used) { acceptAllFileFilterUsed = used; }
    public boolean isAcceptAllFileFilterUsed() { return acceptAllFileFilterUsed; }
    private String dialogTitle;
    private int dialogType = OPEN_DIALOG;
    public interface DialogHandler { int show(JFileChooser chooser, boolean save); }
    private static volatile DialogHandler dialogHandler;
    public static void setDialogHandler(DialogHandler handler) { dialogHandler = handler; }

    public JFileChooser() {}
    public JFileChooser(String currentDirectoryPath) { currentDirectory = new File(currentDirectoryPath); }
    public JFileChooser(File currentDirectory) { this.currentDirectory = currentDirectory; }

    public File getSelectedFile() { return selectedFile; }
    public void setSelectedFile(File file) { this.selectedFile = file; }
    public File[] getSelectedFiles() { return selectedFile == null ? new File[0] : new File[]{selectedFile}; }
    public void setSelectedFiles(File[] selectedFiles) {}
    public File getCurrentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(File dir) { this.currentDirectory = dir; }
    public int getFileSelectionMode() { return fileSelectionMode; }
    public void setFileSelectionMode(int mode) { this.fileSelectionMode = mode; }
    public boolean isMultiSelectionEnabled() { return multiSelectionEnabled; }
    public void setMultiSelectionEnabled(boolean b) { this.multiSelectionEnabled = b; }
    public FileFilter getFileFilter() { return fileFilter; }
    public void setFileFilter(FileFilter filter) { this.fileFilter = filter; }
    public void addChoosableFileFilter(FileFilter filter) { fileFilter = filter; }
    public boolean removeChoosableFileFilter(FileFilter f) { return false; }
    public FileFilter[] getChoosableFileFilters() { return new FileFilter[0]; }
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
