// host-contract: patched-args=shadow
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicFileChooserUI;

/**
 * JColorChooser model and panel events, JFileChooser approve/cancel and its bound properties, and
 * filter acceptance. The "shadow" run adds the host bridges (the Android pickers stand behind them)
 * and the shadow's synchronous directory model.
 */
public class TestChooserContracts {
    private static int checks;

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }

    /** A chooser panel that counts the delegate's calls into it. */
    static class CountingPanel extends AbstractColorChooserPanel {
        int built, updated;
        @Override public void updateChooser() { updated++; }
        @Override protected void buildChooser() { built++; }
        @Override public String getDisplayName() { return "Counting"; }
        @Override public Icon getSmallDisplayIcon() { return null; }
        @Override public Icon getLargeDisplayIcon() { return null; }
    }

    public static void main(String[] args) throws Exception {
        boolean shadow = args.length > 0 && args[0].equals("shadow");
        File dir = Files.createTempDirectory("chooser").toFile();
        File json = new File(dir, "a.json"), text = new File(dir, "b.txt"), sub = new File(dir, "sub");
        check(json.createNewFile() && text.createNewFile() && sub.mkdir(), "fixture");
        try {
            SwingUtilities.invokeAndWait(() -> {
                colorChooser();
                fileChooser(dir, json, text, sub, shadow);
                if (shadow) {
                    Shadow.bridges(dir, json);
                }
            });
        } finally {
            json.delete(); text.delete(); sub.delete(); dir.delete();
        }
        System.out.println("PASS " + checks + " chooser contracts" + (shadow ? " (with bridges)" : ""));
        // The packed color dialog leaves AWT's shared owner frame alive on a desktop host.
        System.exit(0);
    }

    private static void colorChooser() {
        JColorChooser chooser = new JColorChooser(Color.RED);
        check(Color.RED.equals(chooser.getColor()), "initial color");
        check(chooser.getChooserPanels().length > 0, "default panels");
        check(chooser.getUI() != null && "ColorChooserUI".equals(chooser.getUIClassID()), "color ui");

        AtomicInteger changes = new AtomicInteger();
        chooser.getSelectionModel().addChangeListener(e -> changes.incrementAndGet());
        chooser.setColor(Color.BLUE);
        chooser.setColor(Color.BLUE);
        check(changes.get() == 1 && Color.BLUE.equals(chooser.getColor()), "model fires once per change");
        chooser.setColor(0x00FF00);
        check(changes.get() == 2 && chooser.getColor().getRGB() == 0xFF00FF00, "setColor(int) is opaque");

        List<String> properties = new ArrayList<>();
        chooser.addPropertyChangeListener(e -> properties.add(e.getPropertyName()));
        int before = chooser.getChooserPanels().length;
        CountingPanel panel = new CountingPanel();
        chooser.addChooserPanel(panel);
        check(properties.contains(JColorChooser.CHOOSER_PANELS_PROPERTY)
                && chooser.getChooserPanels().length == before + 1, "add panel event");
        check(panel.built > 0 && panel.getColorSelectionModel() == chooser.getSelectionModel(), "panel installed");
        int updates = panel.updated;
        chooser.setColor(Color.YELLOW);
        check(panel.updated > updates, "panel follows the model");
        check(chooser.removeChooserPanel(panel) == panel && chooser.getChooserPanels().length == before,
                "remove panel");
        boolean missing = false;
        try {
            chooser.removeChooserPanel(panel);
        } catch (IllegalArgumentException expected) {
            missing = true;
        }
        check(missing, "remove unknown panel");

        AbstractColorChooserPanel[] only = {new CountingPanel()};
        chooser.setChooserPanels(only);
        check(chooser.getChooserPanels().length == 1 && chooser.getChooserPanels()[0] == only[0], "set panels");

        properties.clear();
        JPanel preview = new JPanel();
        preview.add(new JLabel("preview"));
        chooser.setPreviewPanel(preview);
        check(properties.contains(JColorChooser.PREVIEW_PANEL_PROPERTY) && chooser.getPreviewPanel() == preview,
                "preview panel event");

        properties.clear();
        javax.swing.colorchooser.DefaultColorSelectionModel model =
                new javax.swing.colorchooser.DefaultColorSelectionModel(Color.CYAN);
        chooser.setSelectionModel(model);
        check(properties.contains(JColorChooser.SELECTION_MODEL_PROPERTY) && Color.CYAN.equals(chooser.getColor()),
                "selection model event");

        try {
            AtomicInteger ok = new AtomicInteger(), cancel = new AtomicInteger();
            JDialog dialog = JColorChooser.createDialog(null, "Pick", true, chooser,
                    e -> ok.incrementAndGet(), e -> cancel.incrementAndGet());
            check(dialog.isModal() && "Pick".equals(dialog.getTitle()), "color dialog");
            button(dialog.getContentPane(), "OK").doClick();
            check(ok.get() == 1 && cancel.get() == 0 && !dialog.isVisible(), "ok listener");
            button(dialog.getContentPane(), "cancel").doClick();
            check(cancel.get() == 1, "cancel listener");
            dialog.dispose();
        } catch (HeadlessException headless) {
            // A headless host cannot build the dialog; the shadow never reports headless.
        }
    }

    private static AbstractButton button(Container container, String command) {
        AbstractButton found = find(container, command);
        if (found == null) {
            throw new AssertionError("no button " + command);
        }
        return found;
    }

    private static AbstractButton find(Container container, String command) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton && command.equals(((AbstractButton) child).getActionCommand())) {
                return (AbstractButton) child;
            }
            if (child instanceof Container) {
                AbstractButton found = find((Container) child, command);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void fileChooser(File dir, File json, File text, File sub, boolean shadow) {
        JFileChooser chooser = new JFileChooser(dir);
        check(dir.getAbsoluteFile().equals(chooser.getCurrentDirectory().getAbsoluteFile()), "current directory");
        check(chooser.getUI() != null && "FileChooserUI".equals(chooser.getUIClassID()), "file ui");

        List<String> commands = new ArrayList<>();
        chooser.addActionListener(e -> commands.add(e.getActionCommand()));
        chooser.setSelectedFile(json);
        chooser.approveSelection();
        check(commands.equals(java.util.Arrays.asList(JFileChooser.APPROVE_SELECTION))
                && json.equals(chooser.getSelectedFile()), "approve fires with the selection");
        chooser.cancelSelection();
        check(commands.size() == 2 && JFileChooser.CANCEL_SELECTION.equals(commands.get(1)), "cancel fires");

        chooser.setFileHidingEnabled(true);
        List<String> properties = new ArrayList<>();
        chooser.addPropertyChangeListener(e -> properties.add(e.getPropertyName()));
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        chooser.setApproveButtonText("Export");
        chooser.setControlButtonsAreShown(false);
        chooser.setAccessory(new JLabel("accessory"));
        chooser.setFileHidingEnabled(false);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Export profile");
        check(properties.containsAll(java.util.Arrays.asList(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY,
                JFileChooser.APPROVE_BUTTON_TEXT_CHANGED_PROPERTY,
                JFileChooser.CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY,
                JFileChooser.ACCESSORY_CHANGED_PROPERTY,
                JFileChooser.FILE_HIDING_CHANGED_PROPERTY,
                JFileChooser.MULTI_SELECTION_ENABLED_CHANGED_PROPERTY,
                JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY,
                JFileChooser.DIALOG_TITLE_CHANGED_PROPERTY)), "bound properties: " + properties);
        check("Export".equals(chooser.getUI().getApproveButtonText(chooser))
                && "Export profile".equals(chooser.getUI().getDialogTitle(chooser)), "approve text and title");
        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        check(chooser.getApproveButtonText() == null, "open dialog resets approve text");
        boolean invalid = false;
        try {
            chooser.setDialogType(7);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid, "invalid dialog type");

        // Filters.
        FileFilter all = chooser.getAcceptAllFileFilter();
        check(all != null && all.accept(text) && all.accept(sub) && all.getDescription() != null, "accept all");
        FileNameExtensionFilter jsonOnly = new FileNameExtensionFilter("JSON files", "JSON");
        check(jsonOnly.accept(json) && !jsonOnly.accept(text) && jsonOnly.accept(sub), "extension filter");
        FileFilter custom = new FileFilter() {
            @Override public boolean accept(File f) { return f.getName().startsWith("b"); }
            @Override public String getDescription() { return "b files"; }
        };
        chooser.setMultiSelectionEnabled(false);
        chooser.addChoosableFileFilter(jsonOnly);
        check(chooser.getFileFilter() == all, "adding a filter preserves the active filter");
        chooser.setFileFilter(jsonOnly);
        check(chooser.getFileFilter() == jsonOnly && chooser.accept(json) && !chooser.accept(text), "active filter");
        chooser.setSelectedFile(text);
        chooser.setFileFilter(custom);
        check(text.equals(chooser.getSelectedFile()) && chooser.accept(text), "custom filter keeps accepted file");
        chooser.setFileFilter(jsonOnly);
        check(chooser.getSelectedFile() == null, "filter drops a rejected selection");
        check(chooser.getChoosableFileFilters().length == 3, "choosable filters include an explicitly selected custom filter");
        chooser.setAcceptAllFileFilterUsed(false);
        check(chooser.getChoosableFileFilters().length == 2 && !chooser.isAcceptAllFileFilterUsed(), "drop accept all");

        if (shadow && chooser.getUI() instanceof BasicFileChooserUI) {
            BasicFileChooserUI ui = (BasicFileChooserUI) chooser.getUI();
            chooser.rescanCurrentDirectory();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < ui.getModel().getSize(); i++) {
                names.add(((File) ui.getModel().getElementAt(i)).getName());
            }
            check(names.contains("a.json") && names.contains("sub") && !names.contains("b.txt"), "directory model filters");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            commands.clear();
            ui.setFileName("a.json");
            ui.getApproveSelectionAction().actionPerformed(
                    new ActionEvent(chooser, ActionEvent.ACTION_PERFORMED, "approve"));
            check(commands.equals(java.util.Arrays.asList(JFileChooser.APPROVE_SELECTION))
                    && json.getName().equals(chooser.getSelectedFile().getName()), "typed name approves");
        }
    }

    /** Shadow-only API: the host bridges the Android pickers install. */
    static class Shadow {
        static void bridges(File dir, File json) {
            AtomicBoolean transparency = new AtomicBoolean(true);
            JColorChooser.setDialogHandler(new JColorChooser.DialogHandler() {
                @Override public Color show(Component parent, String title, Color initial) {
                    return show(parent, title, initial, true);
                }
                @Override public Color show(Component parent, String title, Color initial, boolean alpha) {
                    transparency.set(alpha);
                    return "Cancel".equals(title) ? null : initial.darker();
                }
            });
            try {
                check(Color.RED.darker().equals(JColorChooser.showDialog(null, "Pick", Color.RED, false))
                        && !transparency.get(), "color bridge with transparency flag");
                check(JColorChooser.showDialog(null, "Cancel", Color.RED) == null && transparency.get(),
                        "color bridge cancel");
            } finally {
                JColorChooser.setDialogHandler(null);
            }

            JFileChooser chooser = new JFileChooser(dir);
            List<String> commands = new ArrayList<>();
            chooser.addActionListener(e -> commands.add(e.getActionCommand()));
            AtomicInteger asked = new AtomicInteger();
            JFileChooser.setDialogHandler((picker, save) -> {
                asked.incrementAndGet();
                if (save) {
                    return JFileChooser.CANCEL_OPTION;
                }
                picker.setSelectedFile(json);
                return JFileChooser.APPROVE_OPTION;
            });
            try {
                check(chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                        && json.equals(chooser.getSelectedFile())
                        && commands.equals(java.util.Arrays.asList(JFileChooser.APPROVE_SELECTION)), "file bridge approve");
                check(chooser.showSaveDialog(null) == JFileChooser.CANCEL_OPTION
                        && JFileChooser.CANCEL_SELECTION.equals(commands.get(1)), "file bridge cancel");
                check(chooser.showDialog(null, "Import") == JFileChooser.APPROVE_OPTION
                        && chooser.getDialogType() == JFileChooser.CUSTOM_DIALOG
                        && "Import".equals(chooser.getApproveButtonText()) && asked.get() == 3, "custom dialog");
            } finally {
                JFileChooser.setDialogHandler(null);
            }
        }
    }
}
