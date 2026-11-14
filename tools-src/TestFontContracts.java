// host-contract: patched-args=shadow
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Font contracts that hold on the desktop JDK and on the android-awt shadow alike: the
 * attribute model behind deriveFont/getAttributes, the decode grammar and logical family
 * resolution. Nothing here touches glyphs or metrics, which need the Android backend.
 * With {@code shadow} the run also checks the logical-to-Android family table.
 */
public class TestFontContracts {
    private static int failures;

    public static void main(String[] args) throws Exception {
        boolean shadow = Arrays.asList(args).contains("shadow");
        constructors();
        deriveRoundTrips();
        decodeGrammar();
        logicalFamilies();
        textRequiresLayout();
        if (shadow) {
            androidMapping();
        }
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("TestFontContracts: ok");
    }

    private static void check(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void constructors() {
        Font f = new Font("Serif", Font.BOLD, 12);
        check("ctor name", "Serif", f.getName());
        check("ctor style", Font.BOLD, f.getStyle());
        check("ctor size", 12, f.getSize());
        check("ctor size2D", 12f, f.getSize2D());
        check("ctor bad style is plain", Font.PLAIN, new Font("Serif", 7, 12).getStyle());
        check("ctor null name", "Default", new Font(null, Font.PLAIN, 12).getName());
        check("ctor WEIGHT", 2f, f.getAttributes().get(TextAttribute.WEIGHT));
        check("ctor POSTURE", 0f, f.getAttributes().get(TextAttribute.POSTURE));
        check("ctor SIZE", 12f, f.getAttributes().get(TextAttribute.SIZE));
        check("ctor FAMILY", "Serif", f.getAttributes().get(TextAttribute.FAMILY));
        check("equals", new Font("Serif", Font.BOLD, 12), f);
        check("hashCode", new Font("Serif", Font.BOLD, 12).hashCode(), f.hashCode());
        check("missing glyph", 0, f.getMissingGlyphCode());
        check("uniform line metrics", false, f.hasUniformLineMetrics());
        check("plain not transformed", false, f.isTransformed());
        check("plain no layout attributes", false, f.hasLayoutAttributes());
        check("plain identity transform", new AffineTransform(), f.getTransform());

        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.FAMILY, "Monospaced");
        m.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        m.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
        m.put(TextAttribute.SIZE, 14);
        Font a = new Font(m);
        check("map name", "Monospaced", a.getName());
        check("map style", Font.BOLD | Font.ITALIC, a.getStyle());
        check("map size normalized to Float", 14f, a.getAttributes().get(TextAttribute.SIZE));
        check("map size", 14, a.getSize());

        m.put(TextAttribute.WEIGHT, "heavy");
        m.put(TextAttribute.SIZE, 15.5f);
        Font b = new Font(m);
        check("wrong-typed weight ignored", Font.ITALIC, b.getStyle());
        check("size rounds half up", 16, b.getSize());
        check("size2D keeps fraction", 15.5f, b.getSize2D());

        Map<TextAttribute, Object> semi = new HashMap<>();
        semi.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD);
        check("semibold is not bold", Font.PLAIN, new Font(semi).getStyle());
        check("empty map family", "Default", new Font(semi).getName());
        check("empty map size", 12, new Font(semi).getSize());
    }

    private static void deriveRoundTrips() {
        Font base = new Font("SansSerif", Font.PLAIN, 12);
        Font bold = base.deriveFont(Font.BOLD);
        check("derive(style) style", Font.BOLD, bold.getStyle());
        check("derive(style) size", 12f, bold.getSize2D());
        check("derive(style) weight", 2f, bold.getAttributes().get(TextAttribute.WEIGHT));
        check("derive(style) back to plain", base, bold.deriveFont(Font.PLAIN));

        Font big = base.deriveFont(20f);
        check("derive(size) size2D", 20f, big.getSize2D());
        check("derive(size) size", 20, big.getSize());
        check("derive(size) style kept", Font.PLAIN, big.getStyle());

        Font both = base.deriveFont(Font.ITALIC, 9.25f);
        check("derive(style,size) style", Font.ITALIC, both.getStyle());
        check("derive(style,size) size2D", 9.25f, both.getSize2D());
        check("derive(style,size) size", 9, both.getSize());

        AffineTransform scale = AffineTransform.getScaleInstance(2, 3);
        Font scaled = base.deriveFont(scale);
        check("derive(tx) transformed", true, scaled.isTransformed());
        check("derive(tx) transform", scale, scaled.getTransform());
        check("derive(tx) size kept", 12f, scaled.getSize2D());
        check("derive(tx) identity is untransformed", false,
            scaled.deriveFont(new AffineTransform()).isTransformed());
        Font styledScaled = base.deriveFont(Font.BOLD, scale);
        check("derive(style,tx) style", Font.BOLD, styledScaled.getStyle());
        check("derive(style,tx) transform", scale, styledScaled.getTransform());

        // quest-helper's Fonts.java: seed a map from getAttributes, add UNDERLINE, derive.
        Map<TextAttribute, Object> attrs = new HashMap<>(bold.getAttributes());
        attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        Font underlined = bold.deriveFont(attrs);
        check("derive(map) underline", TextAttribute.UNDERLINE_ON, underlined.getAttributes().get(TextAttribute.UNDERLINE));
        check("derive(map) layout attributes", true, underlined.hasLayoutAttributes());
        check("derive(map) name kept", "SansSerif", underlined.getName());
        check("derive(map) style kept", Font.BOLD, underlined.getStyle());
        check("derive(map) not equal to plain derive", false, underlined.equals(bold));
        check("derive(null map) is this", bold, bold.deriveFont((Map<TextAttribute, ?>) null));

        Map<TextAttribute, Object> sized = new HashMap<>();
        sized.put(TextAttribute.SIZE, 30.0);
        sized.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
        Font derived = base.deriveFont(sized);
        check("derive(map) Double size normalized", 30f, derived.getAttributes().get(TextAttribute.SIZE));
        check("derive(map) posture to italic", Font.ITALIC, derived.getStyle());

        Map<TextAttribute, Object> tracked = new HashMap<>();
        tracked.put(TextAttribute.TRACKING, TextAttribute.TRACKING_LOOSE);
        check("tracking is a layout attribute", true, base.deriveFont(tracked).hasLayoutAttributes());

        Map<TextAttribute, Object> sup = new HashMap<>();
        sup.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER);
        Font superscript = base.deriveFont(sup);
        check("superscript transformed", true, superscript.isTransformed());
        AffineTransform expected = AffineTransform.getTranslateInstance(0, -0.375 * 12);
        expected.scale(2. / 3., 2. / 3.);
        check("superscript transform", expected, superscript.getTransform());

        boolean hasUnderlineKey = false;
        boolean hasFontKey = false;
        for (Object key : base.getAvailableAttributes()) {
            hasUnderlineKey |= key == TextAttribute.UNDERLINE;
            hasFontKey |= key == TextAttribute.FONT;
        }
        check("available has UNDERLINE", true, hasUnderlineKey);
        check("available lacks FONT", false, hasFontKey);

        boolean immutable = false;
        try {
            @SuppressWarnings("unchecked")
            Map<TextAttribute, Object> view = (Map<TextAttribute, Object>) base.getAttributes();
            view.put(TextAttribute.SIZE, 99f);
        } catch (UnsupportedOperationException expectedFailure) {
            immutable = true;
        }
        check("getAttributes unmodifiable", true, immutable);
    }

    private static void decodeGrammar() {
        Object[][] table = {
            // input, name, style, size
            {"Dialog-BOLD-18", "Dialog", Font.BOLD, 18},
            {"Serif ITALIC 14", "Serif", Font.ITALIC, 14},
            {"Monospaced-bolditalic", "Monospaced", Font.BOLD | Font.ITALIC, 12},
            {"SansSerif-plain-9", "SansSerif", Font.PLAIN, 9},
            {"Arial-18", "Arial", Font.PLAIN, 18},
            {"Arial 18", "Arial", Font.PLAIN, 18},
            {"SansSerif", "SansSerif", Font.PLAIN, 12},
            {"SansSerif-0", "SansSerif", Font.PLAIN, 12},
            {"SansSerif--5", "SansSerif", Font.PLAIN, 5},
            {"Times New Roman-bold-10", "Times New Roman", Font.BOLD, 10},
            {"Times New Roman 10", "Times New Roman", Font.PLAIN, 10},
            {"Foo-Bar-14", "Foo-Bar", Font.PLAIN, 14},
            {"Foo-Bar", "Foo-Bar", Font.PLAIN, 12},
            {"Courier-ITALIC", "Courier", Font.ITALIC, 12},
            {"Dialog-", "Dialog", Font.PLAIN, 12},
        };
        for (Object[] row : table) {
            String in = (String) row[0];
            Font f = Font.decode(in);
            check("decode(" + in + ") name", row[1], f.getName());
            check("decode(" + in + ") style", row[2], f.getStyle());
            check("decode(" + in + ") size", row[3], f.getSize());
        }
        Font none = Font.decode(null);
        check("decode(null) name", "Dialog", none.getName());
        check("decode(null) style", Font.PLAIN, none.getStyle());
        check("decode(null) size", 12, none.getSize());
    }

    private static void logicalFamilies() {
        String[][] families = {
            {"Dialog", "Dialog"}, {"dialog", "Dialog"}, {"DIALOGINPUT", "DialogInput"},
            {"serif", "Serif"}, {"SansSerif", "SansSerif"}, {"sansserif", "SansSerif"},
            {"monospaced", "Monospaced"},
        };
        for (String[] row : families) {
            Font f = new Font(row[0], Font.PLAIN, 12);
            check("family(" + row[0] + ")", row[1], f.getFamily());
            check("family(" + row[0] + ", locale)", row[1], f.getFamily(Locale.ROOT));
            check("name(" + row[0] + ")", row[0], f.getName());
        }
        String[] suffix = {"plain", "bold", "italic", "bolditalic"};
        for (int style = 0; style < 4; style++) {
            Font f = new Font("Serif", style, 12);
            check("fontName(Serif, " + style + ")", "Serif." + suffix[style], f.getFontName());
            check("psName(Serif, " + style + ")", "Serif." + suffix[style], f.getPSName());
        }
        Font unknown = new Font("NoSuchFamilyXyzzy", Font.BOLD, 12);
        check("unknown family", "Dialog", unknown.getFamily());
        check("unknown name kept", "NoSuchFamilyXyzzy", unknown.getName());
        check("unknown fontName", "Dialog.bold", unknown.getFontName());

        boolean npe = false;
        try {
            unknown.getFamily(null);
        } catch (NullPointerException expected) {
            npe = true;
        }
        check("getFamily(null) throws", true, npe);

        List<String> available = Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String logical : new String[] {"Dialog", "DialogInput", "Serif", "SansSerif", "Monospaced"}) {
            check("available families has " + logical, true, available.contains(logical));
        }
        check("registerFont(logical)", false,
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(new Font("Dialog", Font.PLAIN, 1)));
    }

    private static void textRequiresLayout() {
        check("ascii", false, Font.textRequiresLayout("Hello".toCharArray(), 0, 5));
        check("latin-1", false, Font.textRequiresLayout("café".toCharArray(), 0, 4));
        check("combining acute", true, Font.textRequiresLayout("é".toCharArray(), 0, 2));
        check("hebrew", true, Font.textRequiresLayout("א".toCharArray(), 0, 1));
        check("arabic", true, Font.textRequiresLayout("ا".toCharArray(), 0, 1));
        check("devanagari", true, Font.textRequiresLayout("क".toCharArray(), 0, 1));
        check("range excludes complex", false, Font.textRequiresLayout("abא".toCharArray(), 0, 2));
        check("zwj", true, Font.textRequiresLayout("‍".toCharArray(), 0, 1));
        check("cjk", false, Font.textRequiresLayout("中".toCharArray(), 0, 1));
        boolean npe = false;
        try {
            Font.textRequiresLayout(null, 0, 0);
        } catch (NullPointerException expected) {
            npe = true;
        }
        check("null chars throws", true, npe);
    }

    /** The shadow-only table documented in AwtFontRegistry: logical family to Android family. */
    private static void androidMapping() throws Exception {
        Class<?> families;
        try {
            families = Class.forName("net.runelite.awt.impl.FontFamilies");
        } catch (ClassNotFoundException e) {
            failures++;
            System.out.println("FAIL FontFamilies not on the patched class path");
            return;
        }
        Method map = families.getMethod("androidFamilyFor", String.class);
        String[][] table = {
            {"Dialog", "sans-serif"}, {"SansSerif", "sans-serif"}, {"serif", "serif"},
            {"Monospaced", "monospace"}, {"DialogInput", "monospace"}, {"Arial", null},
        };
        for (String[] row : table) {
            check("android family(" + row[0] + ")", row[1], map.invoke(null, row[0]));
        }
    }
}
