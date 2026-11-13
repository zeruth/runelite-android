// host-contract
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
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
import javax.swing.border.Border;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.PanelUI;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.SeparatorUI;

/**
 * JComponent's paint path for the nine components whose delegates FlatLaf and Metal replace:
 * paint hands a scratch copy of the component graphics to {@code ui.update}, which fills an
 * opaque component and calls {@code ui.paint}, and then paints the border on the component
 * graphics, which nothing the delegate did can reach. The components also ask the delegate
 * for their preferred size. Each component gets a recording ComponentUI; the same checks run
 * against the JDK and the shadow.
 */
public final class TestBasicPaintContracts {
    private static int failures;
    private static int total;

    private static final int W = 60;
    private static final int H = 24;
    private static final Color BACKGROUND = new Color(0x12, 0x34, 0x56);
    private static final Color FOREGROUND = new Color(0xAB, 0xCD, 0xEF);
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 13);
    private static final Dimension PREFERRED = new Dimension(41, 17);

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** What the delegate and the border saw during one paint. */
    static final class Recorder {
        final List<String> events = new ArrayList<String>();
        JComponent installed;
        JComponent updated;
        Graphics updateGraphics;
        Color updateColor;
        Font updateFont;
        Graphics borderGraphics;
        Color borderColor;
        double borderTranslateX = Double.NaN;
        double borderTranslateY = Double.NaN;
        Rectangle borderClip;

        void install(JComponent c) {
            installed = c;
        }

        void update(Graphics g, JComponent c) {
            events.add("update");
            updated = c;
            updateGraphics = g;
            updateColor = g.getColor();
            updateFont = g.getFont();
        }

        /** Disturbs the graphics the way a careless delegate does; the border must not see it. */
        void paint(Graphics g) {
            events.add("paint");
            g.translate(7, 5);
            g.setClip(0, 0, 1, 1);
            g.setColor(Color.MAGENTA);
        }

        void border(Graphics g) {
            events.add("border");
            borderGraphics = g;
            borderColor = g.getColor();
            AffineTransform t = ((Graphics2D) g).getTransform();
            borderTranslateX = t.getTranslateX();
            borderTranslateY = t.getTranslateY();
            borderClip = g.getClipBounds();
        }

        void reset() {
            events.clear();
            updated = null;
            updateGraphics = null;
            updateColor = null;
            updateFont = null;
            borderGraphics = null;
            borderColor = null;
            borderTranslateX = borderTranslateY = Double.NaN;
            borderClip = null;
        }
    }

    private static Border recordingBorder(final Recorder r) {
        return new Border() {
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                r.border(g);
            }

            public Insets getBorderInsets(Component c) {
                return new Insets(0, 0, 0, 0);
            }

            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    private static ButtonUI buttonUI(final Recorder r) {
        return new ButtonUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static LabelUI labelUI(final Recorder r) {
        return new LabelUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static PanelUI panelUI(final Recorder r) {
        return new PanelUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static ProgressBarUI progressBarUI(final Recorder r) {
        return new ProgressBarUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static SeparatorUI separatorUI(final Recorder r) {
        return new SeparatorUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static ScrollBarUI scrollBarUI(final Recorder r) {
        return new ScrollBarUI() {
            public void installUI(JComponent c) { r.install(c); }
            public void update(Graphics g, JComponent c) { r.update(g, c); super.update(g, c); }
            public void paint(Graphics g, JComponent c) { r.paint(g); }
            public Dimension getPreferredSize(JComponent c) { return new Dimension(PREFERRED); }
        };
    }

    private static int paintCenter(JComponent c) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
        return image.getRGB(W / 2, H / 2);
    }

    private static void contract(String name, JComponent c, Recorder r, ComponentUI ui) {
        check(name + ": setUI installs the delegate on the component", r.installed == c && c.getUI() == ui);

        c.setBorder(recordingBorder(r));
        if (c instanceof AbstractButton) {
            // JCheckBox and JRadioButton leave their border unpainted by default.
            ((AbstractButton) c).setBorderPainted(true);
        }
        c.setBackground(BACKGROUND);
        c.setForeground(FOREGROUND);
        c.setFont(FONT);
        c.setSize(W, H);

        c.setOpaque(true);
        r.reset();
        int opaquePixel = paintCenter(c);
        check(name + ": paint goes through ui.update, ui.paint, then the border " + r.events,
                r.events.size() == 3 && "update".equals(r.events.get(0))
                        && "paint".equals(r.events.get(1)) && "border".equals(r.events.get(2)));
        check(name + ": ui.update is handed this component", r.updated == c);
        check(name + ": ui.update gets the foreground as colour (" + r.updateColor + ")",
                FOREGROUND.equals(r.updateColor));
        check(name + ": ui.update gets the component font (" + r.updateFont + ")",
                r.updateFont != null && FONT.getName().equals(r.updateFont.getName())
                        && r.updateFont.getSize() == FONT.getSize() && r.updateFont.getStyle() == FONT.getStyle());
        check(name + ": ui.update gets a scratch copy, not the border's graphics",
                r.updateGraphics != null && r.borderGraphics != null && r.updateGraphics != r.borderGraphics);
        check(name + ": the delegate's translate does not reach the border ("
                        + r.borderTranslateX + "," + r.borderTranslateY + ")",
                r.borderTranslateX == 0 && r.borderTranslateY == 0);
        check(name + ": the delegate's clip does not reach the border (" + r.borderClip + ")",
                r.borderClip == null || (r.borderClip.width >= W && r.borderClip.height >= H));
        check(name + ": the delegate's colour does not reach the border (" + r.borderColor + ")",
                FOREGROUND.equals(r.borderColor));
        check(name + ": ComponentUI.update fills an opaque component (" + Integer.toHexString(opaquePixel) + ")",
                opaquePixel == BACKGROUND.getRGB());

        c.setOpaque(false);
        r.reset();
        int clearPixel = paintCenter(c);
        check(name + ": a non-opaque component still paints through ui.update " + r.events,
                r.events.size() == 3 && "update".equals(r.events.get(0)));
        check(name + ": nothing fills a non-opaque component (" + Integer.toHexString(clearPixel) + ")",
                (clearPixel >>> 24) == 0);

        check(name + ": the delegate answers the preferred size (" + c.getPreferredSize() + ")",
                PREFERRED.equals(c.getPreferredSize()));
        c.setPreferredSize(new Dimension(9, 8));
        check(name + ": an explicit preferred size wins over the delegate",
                new Dimension(9, 8).equals(c.getPreferredSize()));
    }

    public static void main(String[] args) {
        Recorder r;

        r = new Recorder();
        JButton button = new JButton("Reset");
        ButtonUI buttonUI = buttonUI(r);
        button.setUI(buttonUI);
        contract("JButton", button, r, buttonUI);

        r = new Recorder();
        JToggleButton toggle = new JToggleButton("Toggle");
        ButtonUI toggleUI = buttonUI(r);
        toggle.setUI(toggleUI);
        contract("JToggleButton", toggle, r, toggleUI);

        r = new Recorder();
        JCheckBox checkBox = new JCheckBox("Check");
        ButtonUI checkUI = buttonUI(r);
        checkBox.setUI(checkUI);
        contract("JCheckBox", checkBox, r, checkUI);

        r = new Recorder();
        JRadioButton radio = new JRadioButton("Radio");
        ButtonUI radioUI = buttonUI(r);
        radio.setUI(radioUI);
        contract("JRadioButton", radio, r, radioUI);

        r = new Recorder();
        JLabel label = new JLabel("Label");
        LabelUI labelUI = labelUI(r);
        label.setUI(labelUI);
        contract("JLabel", label, r, labelUI);

        r = new Recorder();
        JPanel panel = new JPanel();
        PanelUI panelUI = panelUI(r);
        panel.setUI(panelUI);
        contract("JPanel", panel, r, panelUI);

        r = new Recorder();
        JProgressBar progress = new JProgressBar();
        ProgressBarUI progressUI = progressBarUI(r);
        progress.setUI(progressUI);
        contract("JProgressBar", progress, r, progressUI);

        r = new Recorder();
        JSeparator separator = new JSeparator();
        SeparatorUI separatorUI = separatorUI(r);
        separator.setUI(separatorUI);
        contract("JSeparator", separator, r, separatorUI);

        r = new Recorder();
        JScrollBar scrollBar = new JScrollBar();
        ScrollBarUI scrollBarUI = scrollBarUI(r);
        scrollBar.setUI(scrollBarUI);
        contract("JScrollBar", scrollBar, r, scrollBarUI);

        System.out.println("Tests run: " + total + ", Failures: " + failures);
        if (failures != 0) System.exit(1);
    }
}
