// host-contract
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultDesktopManager;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

/**
 * The internal frame state machine as the JDK runs it: the closed, icon and maximum properties
 * fire in order with their internal frame events, a vetoed setClosed leaves the frame open after
 * its closing event, DefaultDesktopManager swaps a frame and its desktop icon in the desktop, and
 * JDesktopPane.getAllFrames lists frames (iconified ones through their icon) in child order.
 * Nothing here is showing, so setSelected(true) is a no-op on both sides and no activation runs.
 */
public class TestInternalFrameContracts {
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> log = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        properties();
        vetoedClose();
        frameOrder();

        if (!failures.isEmpty()) {
            for (String failure : failures) System.out.println("FAIL " + failure);
            throw new AssertionError(failures.size() + " internal frame contract(s) failed");
        }
        System.out.println("TestInternalFrameContracts: OK");
    }

    private static JDesktopPane desktop() {
        JDesktopPane desktop = new JDesktopPane();
        desktop.setSize(400, 300);
        desktop.setDesktopManager(new DefaultDesktopManager());
        return desktop;
    }

    private static void properties() throws Exception {
        JDesktopPane desktop = desktop();
        JInternalFrame f = new JInternalFrame("one", true, true, true, true);
        f.setBounds(10, 20, 120, 90);
        desktop.add(f);
        record(f);

        f.setVisible(true);
        expectLog("show", "event opened");
        check("show: visible", f.isVisible());
        check("show: not selected while not showing", !f.isSelected());

        f.setMaximum(true);
        expectLog("setMaximum(true)", "prop maximum false->true");
        check("maximized bounds " + f.getBounds(), new Rectangle(0, 0, 400, 300).equals(f.getBounds()));
        check("normal bounds " + f.getNormalBounds(), new Rectangle(10, 20, 120, 90).equals(f.getNormalBounds()));
        check("not resizable while maximized", !f.isResizable());

        f.setMaximum(false);
        expectLog("setMaximum(false)", "prop maximum true->false");
        check("restored bounds " + f.getBounds(), new Rectangle(10, 20, 120, 90).equals(f.getBounds()));

        f.setIcon(true);
        expectLog("setIcon(true)", "prop icon false->true", "event iconified");
        JInternalFrame.JDesktopIcon icon = f.getDesktopIcon();
        check("iconified: frame left the desktop", f.getParent() == null);
        check("iconified: icon is on the desktop", icon.getParent() == desktop);
        check("iconified: getDesktopPane through the icon", f.getDesktopPane() == desktop);
        check("iconified: icon in the frame's layer", JLayeredPane.getLayer(icon) == f.getLayer());
        check("iconified: wasIconOnce", Boolean.TRUE.equals(f.getClientProperty("wasIconOnce")));
        check("iconified: getAllFrames keeps the frame", Arrays.asList(desktop.getAllFrames()).contains(f));

        f.setIcon(true);
        expectLog("setIcon(true) again");

        f.setIcon(false);
        expectLog("setIcon(false)", "prop icon true->false", "event deiconified");
        check("deiconified: frame is back", f.getParent() == desktop);
        check("deiconified: icon removed", icon.getParent() == null);
        check("deiconified: bounds kept " + f.getBounds(), new Rectangle(10, 20, 120, 90).equals(f.getBounds()));
    }

    private static void vetoedClose() throws Exception {
        JDesktopPane desktop = desktop();
        JInternalFrame g = new JInternalFrame("two", true, true);
        desktop.add(g);
        g.setVisible(true);
        record(g);
        log.clear();

        VetoableChangeListener veto = new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent e) throws PropertyVetoException {
                if (JInternalFrame.IS_CLOSED_PROPERTY.equals(e.getPropertyName())
                        && Boolean.TRUE.equals(e.getNewValue())) {
                    throw new PropertyVetoException("kept open", e);
                }
            }
        };
        g.addVetoableChangeListener(veto);
        boolean vetoed = false;
        try {
            g.setClosed(true);
        } catch (PropertyVetoException expected) {
            vetoed = true;
        }
        check("vetoed setClosed throws", vetoed);
        expectLog("vetoed setClosed", "event closing");
        check("vetoed: not closed", !g.isClosed());
        check("vetoed: still on the desktop", g.getParent() == desktop);
        check("vetoed: still visible", g.isVisible());

        g.removeVetoableChangeListener(veto);
        g.setClosed(true);
        expectLog("setClosed(true)", "event closing", "prop closed false->true", "event closed");
        check("closed", g.isClosed());
        check("closed: hidden", !g.isVisible());
        check("closed: removed by the desktop manager", g.getParent() == null);
        check("closed: no frames left", desktop.getAllFrames().length == 0);

        g.setClosed(true);
        expectLog("setClosed(true) again");
    }

    private static void frameOrder() throws Exception {
        JDesktopPane desktop = desktop();
        JInternalFrame a = new JInternalFrame("a", true, true, true, true);
        JInternalFrame b = new JInternalFrame("b", true, true, true, true);
        JInternalFrame c = new JInternalFrame("c", true, true, true, true);
        desktop.add(a);
        desktop.add(b);
        desktop.add(c, JLayeredPane.PALETTE_LAYER);

        expectFrames("added", desktop.getAllFrames(), c, a, b);
        expectFrames("layer 0", desktop.getAllFramesInLayer(0), a, b);
        expectFrames("palette layer", desktop.getAllFramesInLayer(JLayeredPane.PALETTE_LAYER), c);
        expectFrames("empty layer", desktop.getAllFramesInLayer(JLayeredPane.MODAL_LAYER));

        b.toFront();
        expectFrames("b to front", desktop.getAllFrames(), c, b, a);

        a.setIcon(true);
        expectFrames("a iconified", desktop.getAllFrames(), c, b, a);
        b.setIcon(true);
        expectFrames("b iconified", desktop.getAllFrames(), c, a, b);
        expectFrames("layer 0 iconified", desktop.getAllFramesInLayer(0), a, b);

        c.toBack();
        expectFrames("c alone in its layer", desktop.getAllFrames(), c, a, b);
        a.setIcon(false);
        expectFrames("a deiconified to the back", desktop.getAllFrames(), c, b, a);
    }

    private static void record(JInternalFrame frame) {
        frame.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                String name = e.getPropertyName();
                if (JInternalFrame.IS_CLOSED_PROPERTY.equals(name) || JInternalFrame.IS_ICON_PROPERTY.equals(name)
                        || JInternalFrame.IS_MAXIMUM_PROPERTY.equals(name)
                        || JInternalFrame.IS_SELECTED_PROPERTY.equals(name)) {
                    log.add("prop " + name + " " + e.getOldValue() + "->" + e.getNewValue());
                }
            }
        });
        frame.addInternalFrameListener(new InternalFrameListener() {
            @Override public void internalFrameOpened(InternalFrameEvent e) { event("opened", e); }
            @Override public void internalFrameClosing(InternalFrameEvent e) { event("closing", e); }
            @Override public void internalFrameClosed(InternalFrameEvent e) { event("closed", e); }
            @Override public void internalFrameIconified(InternalFrameEvent e) { event("iconified", e); }
            @Override public void internalFrameDeiconified(InternalFrameEvent e) { event("deiconified", e); }
            @Override public void internalFrameActivated(InternalFrameEvent e) { event("activated", e); }
            @Override public void internalFrameDeactivated(InternalFrameEvent e) { event("deactivated", e); }
        });
    }

    private static void event(String name, InternalFrameEvent e) {
        if (e.getInternalFrame() != e.getSource()) failures.add(name + ": getInternalFrame is not the source");
        log.add("event " + name);
    }

    private static void expectLog(String step, String... expected) {
        List<String> want = Arrays.asList(expected);
        if (!want.equals(log)) failures.add(step + ": events " + log + ", JDK " + want);
        log.clear();
    }

    private static void expectFrames(String step, JInternalFrame[] actual, JInternalFrame... expected) {
        if (!Arrays.equals(expected, actual)) {
            failures.add(step + ": getAllFrames " + titles(actual) + ", JDK " + titles(expected));
        }
    }

    private static List<String> titles(JInternalFrame[] frames) {
        List<String> titles = new ArrayList<>();
        for (JInternalFrame frame : frames) titles.add(frame.getTitle());
        return titles;
    }

    private static void check(String what, boolean ok) {
        if (!ok) failures.add(what);
    }
}
