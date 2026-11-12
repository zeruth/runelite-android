// host-contract: patched-args=shadow
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * On-the-spot input: composed text sits in the document between the committed text, the caret
 * follows the input method's caret inside it, and a commit replaces it with typed text. The
 * underline is checked on the shadow only: desktop draws it from the event's
 * INPUT_METHOD_HIGHLIGHT attribute through the toolkit, which a headless JDK cannot map.
 */
public class TestInputMethodContracts {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    static void compose(JTextComponent c, String text, int committed, int caret) {
        AttributedString value = text.isEmpty() ? null : new AttributedString(text);
        TextHitInfo hit = text.length() > committed ? TextHitInfo.leading(caret) : null;
        c.dispatchEvent(new InputMethodEvent(c, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            value == null ? null : value.getIterator(), committed, hit, null));
    }

    static void semantics(JTextComponent c) {
        StringBuilder typed = new StringBuilder();
        c.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { typed.append(e.getKeyChar()); }
        });
        c.setText("ab");
        c.setCaretPosition(2);
        InputMethodRequests requests = c.getInputMethodRequests();
        check(requests != null, c.getClass().getSimpleName() + " supports on-the-spot input");

        compose(c, "cd", 0, 1);
        check("abcd".equals(c.getText()), "composed text is in the document: " + c.getText());
        check(c.getCaretPosition() == 3, "caret follows the composition caret: " + c.getCaretPosition());
        check(requests.getCommittedTextLength() == 2, "committed length excludes composed text");
        check(requests.getInsertPositionOffset() == 2, "insert position is the composition start");
        check(typed.length() == 0, "composition types nothing");

        compose(c, "xyz", 0, 3);
        check("abxyz".equals(c.getText()), "new composition replaces the old one: " + c.getText());
        check(c.getCaretPosition() == 5, "caret at the end of the composition");

        compose(c, "XY", 2, 0);
        check("abXY".equals(c.getText()), "commit replaces the composition: " + c.getText());
        check(c.getCaretPosition() == 4, "caret after the committed text: " + c.getCaretPosition());
        check("XY".equals(typed.toString()), "committed text reaches key listeners as KEY_TYPED: " + typed);
        check(requests.getCommittedTextLength() == 4, "committed text counts once committed");

        compose(c, "q", 0, 1);
        compose(c, "", 0, 0);
        check("abXY".equals(c.getText()), "an empty event withdraws the composition: " + c.getText());
        check(c.getCaretPosition() == 4, "caret back at the insertion point");

        compose(c, "Zw", 1, 1);
        check("abXYZw".equals(c.getText()), "committed and composed text in one event: " + c.getText());
        check(requests.getCommittedTextLength() == 5, "only the committed part counts");
        compose(c, "w", 1, 0);
        check("abXYZw".equals(c.getText()), "committing the composed rest: " + c.getText());
    }

    static BufferedImage paint(JTextComponent c) {
        BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { c.paint(g); } finally { g.dispose(); }
        return image;
    }

    /** The pixels the composition adds over the same text committed lie on one underline row. */
    static void underline(JTextComponent composing, JTextComponent plain, String text) {
        for (JTextComponent c : new JTextComponent[] {composing, plain}) {
            c.setSize(240, 60);
            c.setBackground(Color.BLACK);
            c.setForeground(Color.WHITE);
            c.setCaretColor(Color.BLACK);
            c.setText("");
        }
        compose(composing, text, 0, text.length());
        compose(plain, text, text.length(), 0);
        check(text.equals(composing.getText()) && text.equals(plain.getText()), "same document text");
        BufferedImage a = paint(composing), b = paint(plain);
        int row = -1, count = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) == b.getRGB(x, y)) continue;
                check(row < 0 || row == y, composing.getClass().getSimpleName() + " composition differs off one row: " + row + ", " + y);
                row = y;
                count++;
            }
        }
        check(count > 0, composing.getClass().getSimpleName() + " composed text is underlined");
        compose(composing, text, text.length(), 0);
        BufferedImage committed = paint(composing);
        for (int y = 0; y < committed.getHeight(); y++)
            for (int x = 0; x < committed.getWidth(); x++)
                check(committed.getRGB(x, y) == b.getRGB(x, y), "the underline goes with the commit");
    }

    public static void main(String[] args) throws Exception {
        boolean shadow = args.length > 0 && "shadow".equals(args[0]);
        Runnable verify = () -> {
            semantics(new JTextField());
            semantics(new JTextArea());
            if (shadow) {
                underline(new JTextField(), new JTextField(), "abc");
                underline(new JTextArea(), new JTextArea(), "abc");
            }
        };
        if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
            Thread.currentThread().setName("AWT-InvokeProbe");
            verify.run();
        } else SwingUtilities.invokeAndWait(verify);
        System.out.println("PASS: composed text inline, caret inside it, commit replaces it"
            + (shadow ? ", underline painted until commit" : ""));
        System.exit(0); // The Android shim owns a persistent EDT worker.
    }
}
