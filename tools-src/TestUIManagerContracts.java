// host-contract
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ComponentUI;

/** UIDefaults value resolution and UIManager look-and-feel state, against the JDK and the shadow. */
public final class TestUIManagerContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** A delegate UIDefaults.getUI finds through its static createUI. */
    public static final class TestUI extends ComponentUI {
        static JComponent created;

        public static ComponentUI createUI(JComponent c) {
            created = c;
            return new TestUI();
        }
    }

    /** A look and feel that refuses to install, for the setLookAndFeel exception message. */
    public static final class Unsupported extends LookAndFeel {
        public String getName() { return "Unsupported"; }
        public String getID() { return "Unsupported"; }
        public String getDescription() { return "never supported"; }
        public boolean isNativeLookAndFeel() { return false; }
        public boolean isSupportedLookAndFeel() { return false; }
    }

    /** The JDK ignores an unsupported auxiliary look and feel, so the auxiliary list needs this one. */
    public static final class Auxiliary extends LookAndFeel {
        public String getName() { return "Auxiliary"; }
        public String getID() { return "Auxiliary"; }
        public String getDescription() { return "auxiliary"; }
        public boolean isNativeLookAndFeel() { return false; }
        public boolean isSupportedLookAndFeel() { return true; }
    }

    public static void main(String[] args) throws Exception {
        UIDefaults table = new UIDefaults();

        AtomicInteger lazyCalls = new AtomicInteger();
        table.put("lazy", (UIDefaults.LazyValue) t -> "lazy-" + lazyCalls.incrementAndGet());
        Object first = table.get("lazy");
        Object second = table.get("lazy");
        check("LazyValue is created on first get", "lazy-1".equals(first));
        check("LazyValue replaces its entry, so it is created once", lazyCalls.get() == 1 && first == second);

        AtomicInteger activeCalls = new AtomicInteger();
        table.put("active", (UIDefaults.ActiveValue) t -> "active-" + activeCalls.incrementAndGet());
        check("ActiveValue is created on every get",
            "active-1".equals(table.get("active")) && "active-2".equals(table.get("active")));

        table.put("proxy", new UIDefaults.ProxyLazyValue("java.awt.Dimension", new Object[] {3, 4}));
        check("ProxyLazyValue constructs its class with boxed int arguments", new Dimension(3, 4).equals(table.get("proxy")));

        List<PropertyChangeEvent> tableEvents = new ArrayList<PropertyChangeEvent>();
        table.addPropertyChangeListener(tableEvents::add);
        table.put("color", "red");
        table.put("color", "blue");
        check("UIDefaults.put fires a property change named by the key", tableEvents.size() == 2
            && "color".equals(tableEvents.get(1).getPropertyName())
            && "red".equals(tableEvents.get(1).getOldValue()) && "blue".equals(tableEvents.get(1).getNewValue()));
        table.put("color", null);
        check("UIDefaults.put with null removes the key", table.get("color") == null && !table.containsKey("color"));

        table.put("number", "not an int");
        check("typed getters return their empty value for a mismatched type", table.getInt("number") == 0
            && !table.getBoolean("number") && table.getColor("number") == null && table.getString("missing") == null);
        table.putDefaults(new Object[] {"a", 1, "b", Boolean.TRUE});
        check("putDefaults stores every pair", table.getInt("a") == 1 && table.getBoolean("b"));

        table.put("TestUI", TestUI.class.getName());
        check("getUIClass resolves the class named under the ui class id", table.getUIClass("TestUI") == TestUI.class);
        JComponent target = new JComponent() {
            public String getUIClassID() { return "TestUI"; }
        };
        ComponentUI ui = table.getUI(target);
        check("getUI calls the delegate's static createUI with the target", ui instanceof TestUI && TestUI.created == target);

        String crossPlatform = UIManager.getCrossPlatformLookAndFeelClassName();
        check("cross-platform look and feel is Metal", System.getProperty("swing.crossplatformlaf") != null
            || "javax.swing.plaf.metal.MetalLookAndFeel".equals(crossPlatform));

        List<String> managerEvents = new ArrayList<String>();
        PropertyChangeListener managerListener = e -> managerEvents.add(e.getPropertyName());
        UIManager.addPropertyChangeListener(managerListener);
        try {
            UIManager.setLookAndFeel(crossPlatform);
            check("setLookAndFeel by the cross-platform name installs Metal",
                UIManager.getLookAndFeel() != null && "Metal".equals(UIManager.getLookAndFeel().getID()));
            check("setLookAndFeel fires lookAndFeel", managerEvents.contains("lookAndFeel"));
        } catch (UnsupportedLookAndFeelException e) {
            // The shadow before Metal existed: the exception must carry the JDK's message.
            check("setLookAndFeel without Metal throws the JDK message",
                String.valueOf(e.getMessage()).endsWith(" not supported on this platform"));
        }
        check("UIManager listener is registered", UIManager.getPropertyChangeListeners().length >= 1);
        UIManager.removePropertyChangeListener(managerListener);

        Unsupported unsupported = new Unsupported();
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(unsupported);
            check("an unsupported look and feel is refused", false);
        } catch (UnsupportedLookAndFeelException e) {
            check("an unsupported look and feel is refused with the JDK message",
                (unsupported + " not supported on this platform").equals(e.getMessage()));
        }
        check("a refused look and feel leaves the current one installed", UIManager.getLookAndFeel() == before);

        UIManager.getLookAndFeelDefaults().put("Contract.key", "laf");
        check("a look and feel default is visible through UIManager", "laf".equals(UIManager.get("Contract.key")));
        UIManager.put("Contract.key", "developer");
        check("UIManager.put takes precedence over the look and feel default", "developer".equals(UIManager.get("Contract.key")));
        check("getLookAndFeelDefaults keeps the look and feel value", "laf".equals(UIManager.getLookAndFeelDefaults().get("Contract.key")));
        UIManager.put("Contract.key", null);
        check("removing the developer value falls back to the look and feel", "laf".equals(UIManager.get("Contract.key")));
        // JDK 8 seeds the system table with FocusManagerClassName; JDK 9+ leaves it empty, so the host
        // run may answer null, but nothing else may stand in for the system value.
        Object focusManager = UIManager.get("FocusManagerClassName");
        check("system defaults answer behind the look and feel",
            focusManager == null || "javax.swing.DefaultFocusManager".equals(focusManager));
        check("a key in no table answers null", UIManager.get("Contract.absent") == null);

        UIManager.put("Contract.survives", 7);
        UIManager.setLookAndFeel(crossPlatform);
        check("developer values survive a change of look and feel", UIManager.getInt("Contract.survives") == 7
            && UIManager.getLookAndFeelDefaults().get("Contract.key") == null);
        check("UIManager typed getters with a Locale resolve the same table",
            UIManager.getInt("Contract.survives", java.util.Locale.ROOT) == 7);

        UIManager.LookAndFeelInfo[] installed = UIManager.getInstalledLookAndFeels();
        boolean hasMetal = false;
        for (UIManager.LookAndFeelInfo info : installed) {
            hasMetal |= "javax.swing.plaf.metal.MetalLookAndFeel".equals(info.getClassName());
        }
        check("installed look and feels list Metal", hasMetal);
        UIManager.installLookAndFeel("Contract", Unsupported.class.getName());
        UIManager.LookAndFeelInfo[] grown = UIManager.getInstalledLookAndFeels();
        check("installLookAndFeel appends an entry", grown.length == installed.length + 1
            && "Contract".equals(grown[grown.length - 1].getName()));
        UIManager.setInstalledLookAndFeels(installed);
        check("setInstalledLookAndFeels replaces the list", UIManager.getInstalledLookAndFeels().length == installed.length);

        UIManager.addAuxiliaryLookAndFeel(unsupported);
        check("an unsupported auxiliary look and feel is ignored", UIManager.getAuxiliaryLookAndFeels() == null);
        Auxiliary aux = new Auxiliary();
        UIManager.addAuxiliaryLookAndFeel(aux);
        LookAndFeel[] auxiliary = UIManager.getAuxiliaryLookAndFeels();
        check("addAuxiliaryLookAndFeel records the look and feel", auxiliary != null && auxiliary.length == 1 && auxiliary[0] == aux);
        check("removeAuxiliaryLookAndFeel removes it",
            UIManager.removeAuxiliaryLookAndFeel(aux) && UIManager.getAuxiliaryLookAndFeels() == null);

        System.out.println("SUMMARY " + total + " checks, " + failures + " failures");
        if (failures != 0) throw new AssertionError(failures + " UIManager contract checks failed");
    }
}
