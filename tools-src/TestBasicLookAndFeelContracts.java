// host-contract
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIDefaults;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.PanelUI;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.SeparatorUI;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.plaf.basic.BasicLookAndFeel;

/**
 * BasicLookAndFeel's defaults table and the J* paint path, against the JDK and the shadow.
 * <p>
 * The key set is compared with the one the host JDK's own BasicLookAndFeel builds: the test
 * starts a second JVM without the patch flags ({@code --dump-keys}) and reads its keys back,
 * so the patched run compares the shadow table with the real one. Resource bundle entries
 * are not in the table's key set on either side.
 */
public final class TestBasicLookAndFeelContracts {
    private static int failures;
    private static int total;
    private static int paints;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** The abstract BasicLookAndFeel with only its identity filled in. */
    static final class Plain extends BasicLookAndFeel {
        public String getName() { return "Plain"; }
        public String getID() { return "Plain"; }
        public String getDescription() { return "BasicLookAndFeel defaults"; }
        public boolean isNativeLookAndFeel() { return false; }
        public boolean isSupportedLookAndFeel() { return true; }
    }

    private static Set<String> keys() {
        UIDefaults defaults = new Plain().getDefaults();
        Set<String> keys = new TreeSet<String>();
        for (Object key : defaults.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    private static Set<String> hostJdkKeys() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(java, "-Djava.awt.headless=true",
                "-cp", System.getProperty("java.class.path"),
                TestBasicLookAndFeelContracts.class.getName(), "--dump-keys");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        Set<String> keys = new TreeSet<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                keys.add(line);
            }
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("--dump-keys exited " + exit);
        return keys;
    }

    private static void defaultsKeySet() throws Exception {
        Set<String> expected = hostJdkKeys();
        Set<String> actual = keys();
        List<String> missing = new ArrayList<String>();
        for (String key : expected) if (!actual.contains(key)) missing.add(key);
        List<String> extra = new ArrayList<String>();
        for (String key : actual) if (!expected.contains(key)) extra.add(key);
        if (!missing.isEmpty()) System.out.println("missing " + missing.size() + ": " + missing);
        if (!extra.isEmpty()) System.out.println("extra " + extra.size() + ": " + extra);
        check("host JDK BasicLookAndFeel key set is non-trivial (" + expected.size() + " keys)", expected.size() > 500);
        check("defaults table keys equal the host JDK BasicLookAndFeel key set", missing.isEmpty() && extra.isEmpty());

        UIDefaults defaults = new Plain().getDefaults();
        check("ButtonUI maps to BasicButtonUI",
                "javax.swing.plaf.basic.BasicButtonUI".equals(defaults.get("ButtonUI")));
        check("RootPaneUI maps to BasicRootPaneUI",
                "javax.swing.plaf.basic.BasicRootPaneUI".equals(defaults.get("RootPaneUI")));
        check("system color control is #C0C0C0",
                defaults.getColor("control") != null && defaults.getColor("control").getRGB() == 0xFFC0C0C0);
        check("Button.textShiftOffset is 0", Integer.valueOf(0).equals(defaults.get("Button.textShiftOffset")));
        check("Button.focusInputMap is resolved to an InputMap",
                defaults.get("Button.focusInputMap") instanceof javax.swing.InputMap);
        check("AuditoryCues.cueList lists the cues", defaults.get("AuditoryCues.cueList") instanceof Object[]
                && ((Object[]) defaults.get("AuditoryCues.cueList")).length > 0);
    }

    private static void paintsThrough(String name, JComponent component) {
        component.setSize(60, 24);
        BufferedImage image = new BufferedImage(60, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int before = paints;
        try {
            component.paint(g);
        } finally {
            g.dispose();
        }
        check(name + " paints once through its UI delegate (" + (paints - before) + ")", paints == before + 1);
    }

    private static ButtonUI countingButtonUI() {
        return new ButtonUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        };
    }

    private static void delegatePainting() {
        JButton button = new JButton("Reset");
        button.setUI(countingButtonUI());
        paintsThrough("JButton", button);

        JToggleButton toggle = new JToggleButton("Toggle");
        toggle.setUI(countingButtonUI());
        paintsThrough("JToggleButton", toggle);

        JCheckBox checkBox = new JCheckBox("Check");
        checkBox.setUI(countingButtonUI());
        paintsThrough("JCheckBox", checkBox);

        JRadioButton radio = new JRadioButton("Radio");
        radio.setUI(countingButtonUI());
        paintsThrough("JRadioButton", radio);

        JLabel label = new JLabel("Label");
        label.setUI(new LabelUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        });
        paintsThrough("JLabel", label);

        JLabel measured = new JLabel("Label");
        measured.setUI(new LabelUI() {
            public Dimension getPreferredSize(JComponent c) { return new Dimension(33, 11); }
        });
        check("JLabel asks a measuring LabelUI for its preferred size",
                new Dimension(33, 11).equals(measured.getPreferredSize()));

        // A delegate that defers html measurement to BasicLabelUI must not recurse into the label.
        JLabel html = new JLabel("<html>one<br>two</html>");
        html.setUI(new BasicLabelUI() {
            public Dimension getPreferredSize(JComponent c) { return super.getPreferredSize(c); }
        });
        Dimension htmlSize = html.getPreferredSize();
        check("BasicLabelUI measures an html label (" + htmlSize + ")", htmlSize.width > 0 && htmlSize.height > 0);

        JPanel panel = new JPanel();
        panel.setUI(new PanelUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        });
        paintsThrough("JPanel", panel);

        JProgressBar progress = new JProgressBar();
        progress.setUI(new ProgressBarUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        });
        paintsThrough("JProgressBar", progress);

        JSeparator separator = new JSeparator();
        separator.setUI(new SeparatorUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        });
        paintsThrough("JSeparator", separator);

        JScrollBar scrollBar = new JScrollBar();
        scrollBar.setUI(new ScrollBarUI() {
            public void paint(Graphics g, JComponent c) { paints++; }
        });
        paintsThrough("JScrollBar", scrollBar);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--dump-keys")) {
            for (String key : keys()) System.out.println(key);
            return;
        }
        defaultsKeySet();
        delegatePainting();
        System.out.println("Tests run: " + total + ", Failures: " + failures);
        if (failures != 0) System.exit(1);
    }
}
