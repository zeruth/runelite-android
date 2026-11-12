// host-contract
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ColorChooserUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.DesktopIconUI;
import javax.swing.plaf.DesktopPaneUI;
import javax.swing.plaf.FileChooserUI;
import javax.swing.plaf.InternalFrameUI;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.LayerUI;
import javax.swing.plaf.ListUI;
import javax.swing.plaf.MenuBarUI;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.OptionPaneUI;
import javax.swing.plaf.PanelUI;
import javax.swing.plaf.PopupMenuUI;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.RootPaneUI;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.SeparatorUI;
import javax.swing.plaf.SliderUI;
import javax.swing.plaf.SpinnerUI;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.TableHeaderUI;
import javax.swing.plaf.TableUI;
import javax.swing.plaf.TextUI;
import javax.swing.plaf.ToolBarUI;
import javax.swing.plaf.ToolTipUI;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.ViewportUI;

/**
 * Every Swing component the shadow defines answers getUIClassID with the JDK's id (the key
 * UIManager looks a delegate up by, so a wrong id means no LookAndFeel paints it), and exposes the
 * typed getUI/setUI pair the JDK declares, so a hub plugin's delegate install links.
 */
public class TestUIClassIdContracts {
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        check("javax.swing.JButton", "ButtonUI", ButtonUI.class, ButtonUI.class);
        check("javax.swing.JToggleButton", "ToggleButtonUI", ButtonUI.class, ButtonUI.class);
        check("javax.swing.JCheckBox", "CheckBoxUI", ButtonUI.class, ButtonUI.class);
        check("javax.swing.JRadioButton", "RadioButtonUI", ButtonUI.class, ButtonUI.class);
        // Menu items declare setUI(MenuItemUI) but inherit AbstractButton's getUI, typed ButtonUI.
        check("javax.swing.JMenuItem", "MenuItemUI", ButtonUI.class, MenuItemUI.class);
        check("javax.swing.JMenu", "MenuUI", ButtonUI.class, MenuItemUI.class);
        check("javax.swing.JCheckBoxMenuItem", "CheckBoxMenuItemUI", ButtonUI.class, MenuItemUI.class);
        check("javax.swing.JRadioButtonMenuItem", "RadioButtonMenuItemUI", ButtonUI.class, MenuItemUI.class);
        check("javax.swing.JLabel", "LabelUI", LabelUI.class, LabelUI.class);
        check("javax.swing.JPanel", "PanelUI", PanelUI.class, PanelUI.class);
        check("javax.swing.JSeparator", "SeparatorUI", SeparatorUI.class, SeparatorUI.class);
        check("javax.swing.JPopupMenu$Separator", "PopupMenuSeparatorUI", SeparatorUI.class, SeparatorUI.class);
        check("javax.swing.JPopupMenu", "PopupMenuUI", PopupMenuUI.class, PopupMenuUI.class);
        check("javax.swing.JMenuBar", "MenuBarUI", MenuBarUI.class, MenuBarUI.class);
        check("javax.swing.JList", "ListUI", ListUI.class, ListUI.class);
        check("javax.swing.JComboBox", "ComboBoxUI", ComboBoxUI.class, ComboBoxUI.class);
        check("javax.swing.JProgressBar", "ProgressBarUI", ProgressBarUI.class, ProgressBarUI.class);
        check("javax.swing.JScrollBar", "ScrollBarUI", ScrollBarUI.class, ScrollBarUI.class);
        check("javax.swing.JScrollPane", "ScrollPaneUI", ScrollPaneUI.class, ScrollPaneUI.class);
        check("javax.swing.JViewport", "ViewportUI", ViewportUI.class, ViewportUI.class);
        check("javax.swing.JSlider", "SliderUI", SliderUI.class, SliderUI.class);
        check("javax.swing.JSpinner", "SpinnerUI", SpinnerUI.class, SpinnerUI.class);
        check("javax.swing.JSplitPane", "SplitPaneUI", SplitPaneUI.class, SplitPaneUI.class);
        check("javax.swing.JTabbedPane", "TabbedPaneUI", TabbedPaneUI.class, TabbedPaneUI.class);
        check("javax.swing.JTable", "TableUI", TableUI.class, TableUI.class);
        check("javax.swing.table.JTableHeader", "TableHeaderUI", TableHeaderUI.class, TableHeaderUI.class);
        check("javax.swing.JTree", "TreeUI", TreeUI.class, TreeUI.class);
        check("javax.swing.JRootPane", "RootPaneUI", RootPaneUI.class, RootPaneUI.class);
        check("javax.swing.JOptionPane", "OptionPaneUI", OptionPaneUI.class, OptionPaneUI.class);
        check("javax.swing.JColorChooser", "ColorChooserUI", ColorChooserUI.class, ColorChooserUI.class);
        check("javax.swing.JFileChooser", "FileChooserUI", FileChooserUI.class, null);
        check("javax.swing.JTextField", "TextFieldUI", TextUI.class, TextUI.class);
        check("javax.swing.JPasswordField", "PasswordFieldUI", TextUI.class, TextUI.class);
        check("javax.swing.JFormattedTextField", "FormattedTextFieldUI", TextUI.class, TextUI.class);
        check("javax.swing.JTextArea", "TextAreaUI", TextUI.class, TextUI.class);
        check("javax.swing.JEditorPane", "EditorPaneUI", TextUI.class, TextUI.class);
        check("javax.swing.JTextPane", "TextPaneUI", TextUI.class, TextUI.class);
        // JToolTip declares getUI only; setUI stays JComponent's protected one.
        check("javax.swing.JToolTip", "ToolTipUI", ToolTipUI.class, null);
        check("javax.swing.JToolBar", "ToolBarUI", ToolBarUI.class, ToolBarUI.class);
        check("javax.swing.JToolBar$Separator", "ToolBarSeparatorUI", SeparatorUI.class, SeparatorUI.class);
        check("javax.swing.JDesktopPane", "DesktopPaneUI", DesktopPaneUI.class, DesktopPaneUI.class);
        check("javax.swing.JInternalFrame", "InternalFrameUI", InternalFrameUI.class, InternalFrameUI.class);
        check(new JInternalFrame.JDesktopIcon(new JInternalFrame()), "DesktopIconUI",
                DesktopIconUI.class, DesktopIconUI.class);
        // JLayer keeps JComponent's id: its LayerUI is set per instance, never looked up.
        check("javax.swing.JLayer", "ComponentUI", LayerUI.class, LayerUI.class);

        String base = new JComponent() {}.getUIClassID();
        if (!"ComponentUI".equals(base)) failures.add("JComponent.getUIClassID = " + base);

        if (!failures.isEmpty()) {
            for (String failure : failures) System.out.println("FAIL " + failure);
            throw new AssertionError(failures.size() + " UI class id contract(s) failed");
        }
        System.out.println("TestUIClassIdContracts: OK");
    }

    private static void check(String className, String id, Class<?> getUIType, Class<?> setUIType) {
        JComponent component;
        try {
            component = (JComponent) Class.forName(className).getConstructor().newInstance();
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
            failures.add(className + ": " + cause);
            return;
        }
        check(component, id, getUIType, setUIType);
    }

    private static void check(JComponent component, String id, Class<?> getUIType, Class<?> setUIType) {
        Class<?> type = component.getClass();
        String className = type.getName();
        try {
            String actual = component.getUIClassID();
            if (!id.equals(actual)) failures.add(className + ".getUIClassID = " + actual + ", JDK " + id);
            if (getUIType != null) {
                // getMethod picks the most specific covariant return among public getUI() methods.
                Method getUI = type.getMethod("getUI");
                if (getUI.getReturnType() != getUIType) {
                    failures.add(className + ".getUI returns " + getUI.getReturnType().getName() + ", JDK " + getUIType.getName());
                }
            }
            if (setUIType != null) {
                Method setUI = type.getMethod("setUI", setUIType);
                if (!Modifier.isPublic(setUI.getModifiers())) failures.add(className + ".setUI(" + setUIType.getSimpleName() + ") is not public");
            }
        } catch (NoSuchMethodException e) {
            failures.add(className + ": missing " + e.getMessage());
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
            failures.add(className + ": " + cause);
        }
    }
}
