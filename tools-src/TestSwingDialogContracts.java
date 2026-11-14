// host-contract: patched-args=bridge
import java.awt.Color;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.colorchooser.DefaultColorSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Isolated contracts: native presentation is covered separately on Android. */
public class TestSwingDialogContracts {
    private static int checks;
    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFileChooser chooser = new JFileChooser(new File(System.getProperty("java.io.tmpdir")));
            File first = new File("first.json"), second = new File("second.json");
            File[] selection = {first, second};
            chooser.setMultiSelectionEnabled(true);
            chooser.setSelectedFiles(selection);
            selection[0] = new File("changed");
            check(chooser.getSelectedFiles().length == 2 && chooser.getSelectedFile().equals(first), "multi selection");
            File[] copy = chooser.getSelectedFiles(); copy[0] = second;
            check(chooser.getSelectedFiles()[0].equals(first), "defensive selection");
            chooser.setSelectedFiles(null);
            check(chooser.getSelectedFile() == null && chooser.getSelectedFiles().length == 0, "clear selection");
            FileNameExtensionFilter json = new FileNameExtensionFilter("JSON", "json");
            FileNameExtensionFilter zip = new FileNameExtensionFilter("ZIP", "zip");
            chooser.setFileFilter(json); chooser.addChoosableFileFilter(zip);
            check(chooser.getFileFilter() == json && chooser.getChoosableFileFilters().length == 3, "preserve active filter");
            check(chooser.accept(first) && !chooser.accept(new File("other.txt")), "filename extension");
            check(chooser.removeChoosableFileFilter(zip) && !chooser.removeChoosableFileFilter(zip), "remove filter");
            chooser.setAcceptAllFileFilterUsed(false);
            check(chooser.getChoosableFileFilters().length == 1, "disable accept all");
            chooser.resetChoosableFileFilters();
            check(chooser.getChoosableFileFilters().length == 0, "reset filters");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            check(!chooser.isFileSelectionEnabled() && chooser.isDirectorySelectionEnabled(), "directory mode");
            boolean invalid = false;
            try { chooser.setFileSelectionMode(99); } catch (IllegalArgumentException expected) { invalid = true; }
            check(invalid, "invalid mode");
            DefaultColorSelectionModel model = new DefaultColorSelectionModel(Color.RED);
            AtomicInteger events = new AtomicInteger();
            model.addChangeListener(event -> events.incrementAndGet());
            model.setSelectedColor(Color.BLUE); model.setSelectedColor(Color.BLUE); model.setSelectedColor(null);
            check(events.get() == 1 && model.getSelectedColor().equals(Color.BLUE), "color event contract");

            if (args.length > 0 && args[0].equals("bridge")) {
                bridge(chooser);
            }
        });
        System.out.println("PASS " + checks + " dialog/model contracts");
    }
    private static void bridge(JFileChooser chooser) {
        Object choice = new Object();
        Object[] choices = {choice};
        JOptionPane.setInputHandler((message, title, values, initial) -> {
            check(values != choices && values[0] == choice && initial == choice, "choice identity");
            values[0] = null;
            return choice;
        });
        check(JOptionPane.showInputDialog(null, "Choose", "Title", JOptionPane.QUESTION_MESSAGE, null, choices, choice) == choice,
            "choice result");
        check(choices[0] == choice, "choice copy");
        JOptionPane.setInputHandler((message, title, values, initial) -> initial + " edited");
        check("start edited".equals(JOptionPane.showInputDialog(null, "Text", (Object) "start")), "text initial result");
        JOptionPane.setInputHandler((message, title, values, initial) -> null);
        check(JOptionPane.showInputDialog("Cancel") == null, "input cancel");
        JPanel form = new JPanel();
        Object[] message = {"Header", form};
        JOptionPane.setDialogHandler((value, title, options, buttons) -> {
            check(value == message, "form retained");
            return JOptionPane.NO_OPTION;
        });
        check(JOptionPane.showConfirmDialog(null, message) == JOptionPane.NO_OPTION, "confirm result");
        JFileChooser.setDialogHandler((value, save) -> {
            check(value == chooser && save, "save route"); return JFileChooser.APPROVE_OPTION;
        });
        check(chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION, "save result");
        JColorChooser color = new JColorChooser(Color.RED);
        color.setColor(Color.BLUE);
        check(color.getSelectionModel().getSelectedColor().equals(Color.BLUE), "embedded color chooser");
    }
}
