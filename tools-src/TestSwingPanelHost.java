import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.*;

public class TestSwingPanelHost {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void field(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        android.content.Context context = (android.content.Context) at.getMethod("getSystemContext").invoke(thread);
        Class<?> hostClass = Class.forName("net.runelite.mp.ui.panels.SwingPanelView");
        Object host = hostClass.getConstructor(android.content.Context.class, String.class).newInstance(context, "test");
        ((android.view.View) host).layout(0, 0, 100, 100);
        android.graphics.Bitmap surface = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888);
        surface.eraseColor(android.graphics.Color.GREEN);
        android.graphics.Canvas canvas = new android.graphics.Canvas(surface);
        canvas.translate(150, 0);
        Method draw = hostClass.getDeclaredMethod("onDraw", android.graphics.Canvas.class); draw.setAccessible(true);
        draw.invoke(host, canvas);
        check(surface.getPixel(10, 10) == android.graphics.Color.GREEN, "panel paint must not cover game");
        check(surface.getPixel(160, 10) != android.graphics.Color.GREEN, "panel paints within its bounds");
        System.out.println("PASS: panel paint stays inside its translated bounds");
        JPanel root = new JPanel(null);
        root.setBounds(0, 0, 240, 300);
        JButton button = new JButton("Test action");
        button.setBounds(20, 20, 170, 35);
        root.add(button);
        int[] count = {0};
        button.addActionListener(e -> count[0]++);
        field(host, "root", root);
        field(host, "running", true);
        Method tap = hostClass.getDeclaredMethod("tap", int.class, int.class); tap.setAccessible(true);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "button must fire exactly once");
        button.setEnabled(false);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "disabled button must not fire");
        button.setEnabled(true);
        JTextField text = new JTextField("read only"); text.setEditable(false);
        check(!text.isEditable(), "read-only field respected");
        JComboBox<String> combo = new JComboBox<>(new String[]{"One", "Two"});
        combo.setSelectedIndex(1);
        check("Two".equals(combo.getModel().getSelectedItem()), "selection updates model");

        combo.setBounds(20, 70, 170, 30); root.add(combo);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));

        spinner.setBounds(20, 110, 170, 30); root.add(spinner);
        field(host, "running", false);
        tap.invoke(host, 25, 25);
        check(count[0] == 1, "detached host ignores input");
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0; constraints.weightx = 1; constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < 3; i++) {
            JPanel row = new JPanel(); row.setPreferredSize(new Dimension(100, 30));
            constraints.gridy = i; grid.add(row, constraints);
        }
        check(grid.getPreferredSize().height == 90, "grid must measure three rows");
        grid.setBounds(0, 0, 240, 90); grid.validate();
        for (int i = 0; i < 3; i++) {
            check(grid.getComponent(i).getWidth() == 240, "weighted row fills width");
            check(grid.getComponent(i).getY() == i * 30, "grid rows do not overlap");
        }
        System.out.println("PASS: GridBag preferred size, weighted width, nonoverlapping rows");
        Class<?> html = Class.forName("javax.swing.HtmlTextRenderer");
        Method parse = html.getDeclaredMethod("parse", String.class); parse.setAccessible(true);
        parse.invoke(null, "<html><body><span style='color:#ff0000'>Gained:</span> 100</body></html>");
        parse.invoke(null, "<html><body style='text-align:center'>You have not gained experience yet.</body></html>");
        JPanel border = new JPanel(new BorderLayout());
        border.setBorder(new javax.swing.border.EmptyBorder(4, 4, 4, 4));
        JPanel center = new JPanel(); center.setPreferredSize(new Dimension(20, 30)); border.add(center);
        JPanel hidden = new JPanel(); hidden.setPreferredSize(new Dimension(200, 300)); hidden.setVisible(false);
        border.add(hidden, BorderLayout.NORTH);
        check(border.getPreferredSize().equals(new Dimension(28, 38)), "border respects hidden children and insets");
        border.setBounds(0, 0, 100, 100); border.validate();
        check(center.getBounds().equals(new Rectangle(4, 4, 92, 92)), "default add registers center");
        JPanel box = new JPanel(); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JPanel row = new JPanel(); row.setPreferredSize(new Dimension(20, 30)); box.add(row);
        JPanel collapsed = new JPanel(); collapsed.setPreferredSize(new Dimension(200, 300)); collapsed.setVisible(false); box.add(collapsed);
        check(box.getPreferredSize().equals(new Dimension(20, 30)), "box excludes collapsed rows");
        System.out.println("PASS: stock XP HTML labels, border center/insets, collapsed rows");
        JPanel painted = new JPanel(null);
        int[] childPaints = {0};
        Component child = new Component() { public void paint(Graphics graphics) { childPaints[0]++; } };
        child.setBounds(0, 0, 10, 10); painted.add(child);
        BufferedImage testImage = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics testGraphics = testImage.createGraphics();
        painted.paintComponents(testGraphics); testGraphics.dispose();
        check(childPaints[0] == 1, "stock progress bars can repaint their child labels");
        System.out.println("PASS: stock progress bar child painting entry point");
        System.out.println("PASS: button single dispatch, disabled/detached controls, read-only text, combo model");
        System.exit(0);
    }
}
