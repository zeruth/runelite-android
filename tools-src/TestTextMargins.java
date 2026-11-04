import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

/** Runs unchanged on desktop Swing and the Android compatibility classes. */
public class TestTextMargins {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static Point paintedText(JTextComponent text) {
        text.setSize(240, 100);
        text.setForeground(Color.RED);
        text.setOpaque(false);
        text.getCaret().setVisible(false);
        BufferedImage image = new BufferedImage(240, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { text.paint(graphics); } finally { graphics.dispose(); }
        int left = 240, top = 100;
        for (int y = 0; y < 100; y++) for (int x = 0; x < 240; x++) {
            int rgb = image.getRGB(x, y);
            if ((rgb >>> 24) != 0 && ((rgb >>> 16) & 255) > 128 && ((rgb >>> 8) & 255) < 64 && (rgb & 255) < 64) {
                left = Math.min(left, x);
                top = Math.min(top, y);
            }
        }
        check(left < 240, "text paints visible pixels");
        return new Point(left, top);
    }

    private static void paintShift(JTextComponent text, int x, int y) {
        text.setMargin(new Insets(0, 0, 0, 0));
        Point before = paintedText(text);
        text.setMargin(new Insets(3, 7, 5, 11));
        Point after = paintedText(text);
        check(after.x - before.x == x && after.y - before.y == y, "paint respects margin: " + text.getClass().getSimpleName());
    }

    public static void main(String[] args) throws Exception {
        for (JTextComponent text : new JTextComponent[] {
            new JTextArea("margin"), new JTextField("margin"), new JPasswordField("margin"),
            new JEditorPane("text/plain", "margin"), new JTextPane()
        }) {
            String kind = text.getClass().getSimpleName();
            text.setText("margin");
            text.setSize(240, 100);
            text.setMargin(new Insets(0, 0, 0, 0));
            Dimension base = text.getPreferredSize();
            Insets baseInsets = text.getInsets();
            List<PropertyChangeEvent> events = new ArrayList<>();
            text.addPropertyChangeListener("margin", events::add);
            Insets requested = new Insets(3, 7, 5, 11);
            text.setMargin(requested);
            check(text.getMargin().equals(requested), kind + " stores margin");
            check(events.size() == 1 && events.get(0).getNewValue().equals(requested), kind + " change event");
            Insets actual = text.getInsets();
            check(actual.top - baseInsets.top == 3 && actual.left - baseInsets.left == 7
                && actual.bottom - baseInsets.bottom == 5 && actual.right - baseInsets.right == 11,
                kind + " default border includes margin");
            Dimension padded = text.getPreferredSize();
            // Desktop plain JEditorPane may retain its viewport width rather than grow.
            check((padded.width - base.width == 18 || (text.getClass() == JEditorPane.class && padded.width == base.width))
                && padded.height - base.height == 8, kind + " preferred size includes margin");
            text.setMargin(new Insets(3, 7, 5, 11));
            check(events.size() == 1, kind + " equal value sends no change event");
            text.setMargin(null);
            check(text.getMargin() == null && text.getInsets().equals(baseInsets), kind + " null removes margin");
            check(text.getPreferredSize().equals(base), kind + " null invalidates cached size");
            Border custom = BorderFactory.createEmptyBorder(1, 2, 3, 4);
            text.setBorder(custom);
            text.setMargin(requested);
            check(text.getBorder() == custom && text.getInsets().equals(new Insets(1, 2, 3, 4)), kind + " custom border owns insets");
            text.setBorder(null);
            check(text.getInsets().equals(new Insets(0, 0, 0, 0)), kind + " explicit null border ignores margin");
        }
        JTextArea wrapped = new JTextArea("one two three four five six seven eight");
        wrapped.setLineWrap(true);
        wrapped.setWrapStyleWord(true);
        wrapped.setMargin(new Insets(0, 0, 0, 0));
        wrapped.setSize(180, 300);
        int height = wrapped.getPreferredSize().height;
        wrapped.setMargin(new Insets(3, 60, 5, 60));
        check(wrapped.getPreferredSize().height > height + 8, "margin reduces available wrapping width");
        paintShift(new JTextArea("margin"), 7, 3);
        JTextField field = new JTextField("margin");
        paintShift(field, 7, -1);
        field.setHorizontalAlignment(JTextField.CENTER);
        paintShift(field, -2, -1);
        field.setHorizontalAlignment(JTextField.RIGHT);
        paintShift(field, -11, -1);
        System.out.println("PASS: " + checks + " text margin contracts");
    }
}
