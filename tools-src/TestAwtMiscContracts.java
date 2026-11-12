// host-contract: patched-args=shadow
import java.awt.AWTException;
import java.awt.AWTKeyStroke;
import java.awt.AWTPermission;
import java.awt.Event;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.Robot;
import java.awt.SplashScreen;
import java.awt.SystemColor;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FilenameFilter;
import javax.swing.KeyStroke;

/**
 * Small java.awt classes and java.awt.print: the shared assertions run on the real JDK and on the
 * shadow; the {@code shadow} argument adds the headless answers (no printer, no splash, no robot,
 * default system colors) the host JDK gives differently.
 */
public final class TestAwtMiscContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /**
     * True when {@code c} is the patched shadow class. The runner demotes a shadow class to
     * compile-only when JDK neighbours need members it lacks, so the patched run can still load
     * the JDK's Toolkit or PrinterJob; the headless answers are only asserted on the shadow.
     */
    private static boolean patched(boolean shadow, Class<?> c) {
        java.net.URL url = c.getResource(c.getSimpleName() + ".class");
        return shadow && url != null && !"jrt".equals(url.getProtocol());
    }

    public static void main(String[] args) throws Exception {
        boolean shadow = args.length > 0 && "shadow".equals(args[0]);

        // AWTKeyStroke / KeyStroke
        AWTKeyStroke ctrlShiftA = AWTKeyStroke.getAWTKeyStroke("ctrl shift pressed A");
        check("keystroke parse code", ctrlShiftA.getKeyCode() == KeyEvent.VK_A);
        check("keystroke parse modifiers", (ctrlShiftA.getModifiers() & InputEvent.SHIFT_DOWN_MASK) != 0
            && (ctrlShiftA.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0
            && (ctrlShiftA.getModifiers() & InputEvent.CTRL_MASK) != 0);
        check("keystroke toString", "shift ctrl pressed A".equals(ctrlShiftA.toString()));
        check("keystroke event type", ctrlShiftA.getKeyEventType() == KeyEvent.KEY_PRESSED);
        check("keystroke cached", ctrlShiftA == AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_A,
            InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
        AWTKeyStroke released = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK, true);
        check("keystroke released", released.isOnKeyRelease()
            && released.getKeyEventType() == KeyEvent.KEY_RELEASED
            && "ctrl released A".equals(released.toString()));
        AWTKeyStroke typed = AWTKeyStroke.getAWTKeyStroke("typed a");
        check("keystroke typed", typed.getKeyEventType() == KeyEvent.KEY_TYPED
            && typed.getKeyChar() == 'a' && typed.getKeyCode() == KeyEvent.VK_UNDEFINED
            && "typed a".equals(typed.toString()));
        check("keystroke equals/hashCode", ctrlShiftA.equals(AWTKeyStroke.getAWTKeyStroke("shift ctrl A"))
            && ctrlShiftA.hashCode() == AWTKeyStroke.getAWTKeyStroke("shift ctrl A").hashCode()
            && !ctrlShiftA.equals(released));
        boolean rejected;
        try {
            AWTKeyStroke.getAWTKeyStroke("ctrl bogus A");
            rejected = false;
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("keystroke bad modifier rejected", rejected);
        KeyStroke ks = KeyStroke.getKeyStroke("control S");
        check("KeyStroke shared instance", ks != null && ks == KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        check("KeyStroke bad string is null", KeyStroke.getKeyStroke("ctrl NOT_A_KEY") == null);
        check("KeyStroke typed char", KeyStroke.getKeyStroke('x').getKeyEventType() == KeyEvent.KEY_TYPED);

        // SystemColor
        check("SystemColor NUM_COLORS", SystemColor.NUM_COLORS == 26 && SystemColor.INFO_TEXT == 25);
        check("SystemColor toString", SystemColor.text.toString().endsWith("[i=12]"));
        check("SystemColor opaque", SystemColor.desktop.getAlpha() == 255 && SystemColor.infoText.getAlpha() == 255);
        if (patched(shadow, SystemColor.class) && patched(shadow, java.awt.Toolkit.class)) {
            check("SystemColor 8u defaults", SystemColor.text.getRGB() == 0xFFC0C0C0
                && SystemColor.controlLtHighlight.getRGB() == 0xFFE0E0E0
                && SystemColor.info.getRGB() == 0xFFE0E000
                && SystemColor.desktop.getRed() == 0 && SystemColor.desktop.getGreen() == 92);
        }

        // Paper / PageFormat / Book
        Paper paper = new Paper();
        check("Paper letter", paper.getWidth() == 612 && paper.getHeight() == 792);
        check("Paper inch margins", paper.getImageableX() == 72 && paper.getImageableY() == 72
            && paper.getImageableWidth() == 468 && paper.getImageableHeight() == 648);
        paper.setImageableArea(36, 72, 500, 600);
        PageFormat pf = new PageFormat();
        pf.setPaper(paper);
        check("PageFormat portrait", pf.getOrientation() == PageFormat.PORTRAIT && pf.getImageableX() == 36
            && pf.getImageableY() == 72 && pf.getWidth() == 612);
        pf.setOrientation(PageFormat.LANDSCAPE);
        double[] m = pf.getMatrix();
        check("PageFormat landscape", pf.getWidth() == 792 && pf.getImageableX() == 120
            && pf.getImageableY() == 36 && pf.getImageableWidth() == 600);
        check("PageFormat landscape matrix", m[0] == 0 && m[1] == -1 && m[2] == 1 && m[3] == 0
            && m[4] == 0 && m[5] == 792);
        pf.setOrientation(PageFormat.REVERSE_LANDSCAPE);
        check("PageFormat reverse landscape", pf.getImageableX() == 72 && pf.getImageableY() == 76);
        try {
            pf.setOrientation(7);
            rejected = false;
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("PageFormat bad orientation", rejected);
        Printable painter = (g, format, index) -> Printable.NO_SUCH_PAGE;
        Book book = new Book();
        book.append(painter, new PageFormat(), 3);
        check("Book pages", book.getNumberOfPages() == 3 && book.getPrintable(2) == painter);
        try {
            book.getPageFormat(3);
            rejected = false;
        } catch (IndexOutOfBoundsException expected) {
            rejected = true;
        }
        check("Book index bound", rejected);

        // JobAttributes / PageAttributes
        JobAttributes job = new JobAttributes();
        check("JobAttributes defaults", job.getCopies() == 1
            && job.getDialog() == JobAttributes.DialogType.NATIVE
            && job.getDestination() == JobAttributes.DestinationType.PRINTER
            && job.getSides() == JobAttributes.SidesType.ONE_SIDED
            && job.getMinPage() == 1 && job.getMaxPage() == Integer.MAX_VALUE);
        PageAttributes page = new PageAttributes();
        check("PageAttributes defaults", page.getColor() == PageAttributes.ColorType.MONOCHROME
            && page.getOrientationRequested() == PageAttributes.OrientationRequestedType.PORTRAIT
            && page.getOrigin() == PageAttributes.OriginType.PHYSICAL
            && page.getPrintQuality() == PageAttributes.PrintQualityType.NORMAL);

        // Event (deprecated 1.0 model)
        check("Event constants", Event.KEY_PRESS == 401 && Event.MOUSE_DOWN == 501 && Event.WINDOW_DESTROY == 201
            && Event.HOME == 1000 && Event.F1 == 1008 && Event.SHIFT_MASK == 1);
        Event event = new Event(null, Event.KEY_PRESS, null);
        check("Event fields", event.id == Event.KEY_PRESS && event.target == null);

        check("AWTException message", "x".equals(new AWTException("x").getMessage()));
        check("AWTPermission name", "readDisplayPixels".equals(new AWTPermission("readDisplayPixels").getName()));

        // FileDialog state; setVisible is left out (it opens the host's real dialog).
        FileDialog dialog = null;
        try {
            dialog = new FileDialog((Frame) null, "Save", FileDialog.SAVE);
        } catch (java.awt.HeadlessException e) {
            // A headless host JDK (or a JDK Dialog over the shadow's always-headless
            // GraphicsEnvironment) refuses the window; the state checks need one.
            System.out.println("SKIP FileDialog: " + e);
        }
        if (dialog != null) {
            check("FileDialog mode", dialog.getMode() == FileDialog.SAVE && "Save".equals(dialog.getTitle()));
            try {
                dialog.setMode(5);
                rejected = false;
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check("FileDialog bad mode", rejected);
            dialog.setDirectory("dir");
            dialog.setFile("a.txt");
            check("FileDialog file", "dir".equals(dialog.getDirectory()) && "a.txt".equals(dialog.getFile()));
            check("FileDialog no files yet", dialog.getFiles().length == 0);
            dialog.setMultipleMode(true);
            FilenameFilter filter = (File dir, String name) -> name.endsWith(".txt");
            dialog.setFilenameFilter(filter);
            check("FileDialog multiple/filter", dialog.isMultipleMode() && dialog.getFilenameFilter() == filter);
            dialog.dispose();
        }

        if (patched(shadow, PrinterJob.class)) {
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            check("PrinterJob no services", PrinterJob.lookupPrintServices().length == 0
                && printerJob.getPrintService() == null);
            check("PrinterJob dialog cancelled", !printerJob.printDialog());
            check("PrinterJob default page", printerJob.defaultPage().getImageableX() == 72);
            try {
                printerJob.print();
                rejected = false;
            } catch (PrinterException expected) {
                rejected = true;
            }
            check("PrinterJob print fails", rejected);
        }
        if (patched(shadow, SplashScreen.class)) {
            check("SplashScreen absent", SplashScreen.getSplashScreen() == null);
        }
        if (patched(shadow, Robot.class)) {
            try {
                new Robot();
                rejected = false;
            } catch (AWTException expected) {
                rejected = true;
            }
            check("Robot unsupported", rejected);
        }

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }
}
