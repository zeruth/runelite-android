// host-contract: runs=patched
import java.net.URL;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;

/**
 * Runs through {@code python tools-src/run-awt-contracts.py --only TestAndroidSpinner}, patched
 * only: it asserts the shadow JSpinner is the one loaded. Checks editor initialization and
 * ownership, not Android rendering.
 */
public final class TestAndroidSpinner {
    public static void main(String[] args) {
        URL implementation = JSpinner.class.getResource("JSpinner.class");
        check(implementation != null && !"jrt".equals(implementation.getProtocol()),
            "Patch the Android spinner classes into java.desktop before running");

        verifyEditor(new JSpinner(), true, 0);
        verifyEditor(new JSpinner(new SpinnerNumberModel(7, 0, 20, 1)), true, 7);
        verifyEditor(new JSpinner(new SpinnerListModel(Arrays.asList("first", "second"))),
            false, "first");

        JSpinner spinner = new JSpinner();
        JPanel replacement = new JPanel();
        spinner.setEditor(replacement);
        check(spinner.getEditor() == replacement, "Explicit editor replacement was lost");
        System.out.println("PASS: Android spinner editor initialization, ownership, stable text field, initial value, and replacement");
    }

    private static void verifyEditor(JSpinner spinner, boolean numeric, Object value) {
        check(spinner.getEditor() instanceof JSpinner.DefaultEditor,
            "A newly constructed spinner has no default editor");
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        check((editor instanceof JSpinner.NumberEditor) == numeric, "Wrong editor type");
        check((editor instanceof JSpinner.ListEditor) == (spinner.getModel() instanceof SpinnerListModel),
            "A list model needs a ListEditor");
        check(editor.getSpinner() == spinner, "Editor does not retain its spinner");
        check(editor.getTextField() == editor.getTextField(), "Text field changes between calls");
        check(value.equals(editor.getTextField().getValue()), "Initial model value is missing");
        check(editor.getTextField().getParent() == editor, "Text field is not in its editor");
        editor.getTextField().setColumns(6);
        check(editor.getTextField().getColumns() == 6, "Text field configuration was discarded");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
