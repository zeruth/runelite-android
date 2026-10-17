package javax.swing;

import java.awt.Component;

public class JOptionPane extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;

    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CANCEL_OPTION = 2;
    public static final int OK_OPTION = 0;
    public static final int CLOSED_OPTION = -1;

    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public interface DialogHandler { int show(String message, String title, int optionType, Object[] options); }
    private static volatile DialogHandler dialogHandler;
    public static void setDialogHandler(DialogHandler handler) { dialogHandler = handler; }
    private static int show(Object message, String title, int optionType, Object[] options) {
        DialogHandler handler = dialogHandler;
        return handler == null ? CLOSED_OPTION : handler.show(String.valueOf(message), title, optionType, options);
    }
    public static int showConfirmDialog(Component parent, Object message) { return show(message, "Confirm", YES_NO_CANCEL_OPTION, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) { return show(message, title, optionType, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType) { return show(message, title, optionType, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon) { return show(message, title, optionType, null); }
    public static String showInputDialog(Object message) { return null; }
    public static String showInputDialog(Component parent, Object message) { return null; }
    public static String showInputDialog(Component parent, Object message, Object initialSelectionValue) { return null; }
    public static String showInputDialog(Component parent, Object message, String title, int messageType) { return null; }
    public static Object showInputDialog(Component parent, Object message, String title, int messageType, Icon icon, Object[] selectionValues, Object initialSelectionValue) { return null; }

    public static void showMessageDialog(Component parent, Object message) { show(message, "RuneLite", DEFAULT_OPTION, null); }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType) { show(message, title, DEFAULT_OPTION, null); }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType, Icon icon) { show(message, title, DEFAULT_OPTION, null); }
    public static int showOptionDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue) {
        return show(message, title, optionType, options);
    }
}