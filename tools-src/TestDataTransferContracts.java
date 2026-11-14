// host-contract
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.FlavorTable;
import java.awt.datatransfer.MimeTypeParseException;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * java.awt.datatransfer: the MIME grammar DataFlavor accepts, the flavour predicates, text flavour
 * ranking, reader decoding, StringSelection and the Clipboard queries. Every expectation is what the
 * JDK does, so the plain run proves the table and the patched run proves the port.
 */
public final class TestDataTransferContracts {
    private static int checks;

    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static void equal(Object actual, Object expected, String message) {
        check(expected == null ? actual == null : expected.equals(actual), message + ": expected " + expected + " but was " + actual);
    }

    /** One accepted MIME string: what the parser makes of it. {@code mime} null skips the full-string compare. */
    static void grammar(String input, String primary, String sub, Class<?> representation, String mime, String... parameters) throws Exception {
        DataFlavor flavor = new DataFlavor(input);
        equal(flavor.getPrimaryType(), primary, input + " primary type");
        equal(flavor.getSubType(), sub, input + " subtype");
        equal(flavor.getRepresentationClass(), representation, input + " representation class");
        equal(flavor.getParameter("class"), representation.getName(), input + " class parameter");
        if (mime != null) equal(flavor.getMimeType(), mime, input + " mime type");
        for (int i = 0; i < parameters.length; i += 2) {
            equal(flavor.getParameter(parameters[i]), parameters[i + 1], input + " parameter " + parameters[i]);
        }
    }

    static void rejected(String input, Class<? extends Throwable> expected) {
        try {
            new DataFlavor(input);
        } catch (Throwable thrown) {
            check(expected.isInstance(thrown), input + " threw " + thrown + ", expected " + expected.getSimpleName());
            return;
        }
        throw new AssertionError(input + " accepted, expected " + expected.getSimpleName());
    }

    static String read(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[64];
        for (int n; (n = reader.read(buffer)) > 0; ) out.append(buffer, 0, n);
        reader.close();
        return out.toString();
    }

    static final class Single implements Transferable {
        private final DataFlavor flavor;
        private final Object value;

        Single(DataFlavor flavor, Object value) { this.flavor = flavor; this.value = value; }

        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {flavor}; }
        public boolean isDataFlavorSupported(DataFlavor candidate) { return flavor.equals(candidate); }
        public Object getTransferData(DataFlavor candidate) throws UnsupportedFlavorException {
            if (!flavor.equals(candidate)) throw new UnsupportedFlavorException(candidate);
            return value;
        }
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        // Grammar: case folding of type and parameter names, quoted values, whitespace, defaults.
        grammar("text/plain", "text", "plain", InputStream.class, "text/plain; class=java.io.InputStream");
        grammar("Text/HTML; Charset=\"utf-8\"; class=java.lang.String", "text", "html", String.class, null, "charset", "utf-8", "CHARSET", "utf-8");
        grammar("  image/png ;class=java.io.InputStream", "image", "png", InputStream.class, "image/png; class=java.io.InputStream");
        grammar("application/x-java-serialized-object; class=java.lang.Integer", "application", "x-java-serialized-object", Integer.class,
                "application/x-java-serialized-object; class=java.lang.Integer");
        grammar("application/x-java-file-list; class=java.util.List", "application", "x-java-file-list", List.class, null);
        grammar("text/plain; note=\"a;b\"; class=java.io.Reader", "text", "plain", Reader.class, null, "note", "a;b", "missing", null);
        grammar("x-test/test; humanPresentableName=Shown; class=java.lang.Object", "x-test", "test", Object.class,
                "x-test/test; class=java.lang.Object", "humanPresentableName", "Shown");
        rejected("nonsense", IllegalArgumentException.class);
        rejected("text/", IllegalArgumentException.class);
        rejected("text/plain; charset", IllegalArgumentException.class);
        rejected("application/x-java-serialized-object", IllegalArgumentException.class);
        rejected("text/plain; class=does.not.Exist", ClassNotFoundException.class);
        try {
            new DataFlavor("text/plain; class=does.not.Exist", "shown");
            throw new AssertionError("two-argument constructor accepted a missing class");
        } catch (IllegalArgumentException expected) {
        }
        check(new MimeTypeParseException("why").getMessage().equals("why"), "MimeTypeParseException message");

        // Constructors and human presentable names.
        DataFlavor integers = new DataFlavor(Integer.class, null);
        equal(integers.getMimeType(), "application/x-java-serialized-object; class=java.lang.Integer", "class constructor mime");
        equal(integers.getHumanPresentableName(), "application/x-java-serialized-object", "class constructor default name");
        check(integers.isFlavorSerializedObjectType() && integers.isRepresentationClassSerializable(), "serialized object flavour");
        equal(new DataFlavor("text/plain").getHumanPresentableName(), "text/plain", "mime constructor default name");
        equal(new DataFlavor("text/plain", "Shown").getHumanPresentableName(), "Shown", "explicit name");
        DataFlavor loaded = new DataFlavor("text/plain; class=java.lang.String", null, TestDataTransferContracts.class.getClassLoader());
        equal(loaded.getRepresentationClass(), String.class, "class loader constructor");
        DataFlavor renamed = (DataFlavor) loaded.clone();
        renamed.setHumanPresentableName("Renamed");
        check(renamed.equals(loaded) && !"Renamed".equals(loaded.getHumanPresentableName()), "clone is independent and equal");

        // Predefined flavours.
        equal(DataFlavor.stringFlavor.getMimeType(), "application/x-java-serialized-object; class=java.lang.String", "stringFlavor");
        equal(DataFlavor.stringFlavor.getHumanPresentableName(), "Unicode String", "stringFlavor name");
        equal(DataFlavor.imageFlavor.getMimeType(), "image/x-java-image; class=java.awt.Image", "imageFlavor");
        equal(DataFlavor.imageFlavor.getHumanPresentableName(), "Image", "imageFlavor name");
        equal(DataFlavor.plainTextFlavor.getRepresentationClass(), InputStream.class, "plainTextFlavor class");
        equal(DataFlavor.plainTextFlavor.getParameter("charset"), "unicode", "plainTextFlavor charset");
        check(DataFlavor.javaFileListFlavor.isFlavorJavaFileListType(), "javaFileListFlavor predicate");
        check(!DataFlavor.stringFlavor.isFlavorJavaFileListType(), "stringFlavor is not a file list");
        equal(DataFlavor.allHtmlFlavor.getParameter("document"), "all", "allHtmlFlavor document");
        equal(DataFlavor.fragmentHtmlFlavor.getParameter("document"), "fragment", "fragmentHtmlFlavor document");
        equal(DataFlavor.selectionHtmlFlavor.getRepresentationClass(), String.class, "selectionHtmlFlavor class");
        equal(DataFlavor.getTextPlainUnicodeFlavor().getRepresentationClass(), InputStream.class, "text/plain unicode flavour class");
        check(DataFlavor.getTextPlainUnicodeFlavor().isMimeTypeEqual("text/plain"), "text/plain unicode flavour type");

        // Equality and MIME comparison ignore parameters other than the class.
        DataFlavor foo = new DataFlavor("x-test/test; class=java.lang.Object; foo=bar");
        DataFlavor bar = new DataFlavor("x-test/test; class=java.lang.Object; x=y");
        check(foo.equals(bar) && foo.hashCode() == bar.hashCode(), "parameters other than class ignored by equals");
        check(foo.equals((Object) bar) && foo.match(bar), "equals(Object) and match agree");
        check(!foo.equals(new DataFlavor("x-test/test; class=java.lang.String")), "representation class part of equality");
        check(DataFlavor.stringFlavor.isMimeTypeEqual("application/x-java-serialized-object; class=java.lang.Integer"), "isMimeTypeEqual ignores parameters");
        check(DataFlavor.stringFlavor.isMimeTypeEqual("APPLICATION/X-Java-Serialized-Object"), "isMimeTypeEqual folds case");
        check(!DataFlavor.stringFlavor.isMimeTypeEqual("text/plain"), "isMimeTypeEqual compares the base type");
        check(DataFlavor.stringFlavor.isMimeTypeEqual(integers), "isMimeTypeEqual(DataFlavor)");
        check(!DataFlavor.stringFlavor.equals((DataFlavor) null) && !DataFlavor.stringFlavor.equals("text"), "equals with null and a String");

        // Text predicates.
        check(DataFlavor.stringFlavor.isFlavorTextType(), "stringFlavor is text");
        check(new DataFlavor("text/plain; class=java.io.Reader").isRepresentationClassReader(), "Reader representation");
        check(new DataFlavor("text/plain; class=java.nio.CharBuffer").isRepresentationClassCharBuffer(), "CharBuffer representation");
        check(new DataFlavor("text/plain; class=java.nio.ByteBuffer").isRepresentationClassByteBuffer(), "ByteBuffer representation");
        check(new DataFlavor("text/plain").isRepresentationClassInputStream(), "InputStream representation");
        check(!DataFlavor.imageFlavor.isFlavorTextType(), "imageFlavor is not text");
        check(!new DataFlavor("text/plain; class=java.lang.Integer").isFlavorTextType(), "text type with a non-text class");

        // selectBestTextFlavor: markup beats plain, Reader beats String beats InputStream, UTF-8 beats US-ASCII.
        DataFlavor plainString = new DataFlavor("text/plain; class=java.lang.String");
        DataFlavor html = new DataFlavor("text/html; class=java.lang.String");
        DataFlavor reader = new DataFlavor("text/plain; class=java.io.Reader");
        DataFlavor utf8 = new DataFlavor("text/plain; charset=UTF-8; class=java.io.InputStream");
        DataFlavor ascii = new DataFlavor("text/plain; charset=US-ASCII; class=java.io.InputStream");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {plainString, DataFlavor.stringFlavor, html}), html, "markup preferred");
        // The JDK ranks stringFlavor's serialized-object type above text/plain.
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {plainString, DataFlavor.stringFlavor}), DataFlavor.stringFlavor, "stringFlavor over text/plain");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {utf8, plainString, reader}), reader, "decoded Reader preferred");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {utf8, plainString}), plainString, "decoded over encoded");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {ascii, utf8}), utf8, "UTF-8 over US-ASCII");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {DataFlavor.imageFlavor, integers, utf8}), utf8, "non-text flavours ignored");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[] {DataFlavor.imageFlavor}), null, "no text flavour");
        equal(DataFlavor.selectBestTextFlavor(new DataFlavor[0]), null, "empty array");
        equal(DataFlavor.selectBestTextFlavor(null), null, "null array");

        // getReaderForText over the three representation families.
        String text = "héllo, 世界";
        StringSelection selection = new StringSelection(text);
        equal(read(DataFlavor.stringFlavor.getReaderForText(selection)), text, "reader over String");
        equal(read(DataFlavor.plainTextFlavor.getReaderForText(selection)), text, "reader over StringSelection plain text");
        equal(read(utf8.getReaderForText(new Single(utf8, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))))), text,
                "reader decodes the charset parameter");
        try {
            DataFlavor.imageFlavor.getReaderForText(selection);
            throw new AssertionError("reader for a non-text flavour");
        } catch (IllegalArgumentException | UnsupportedFlavorException expected) {
        }

        // StringSelection round trip.
        DataFlavor[] offered = selection.getTransferDataFlavors();
        check(Arrays.equals(offered, new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.plainTextFlavor}), "StringSelection flavours");
        offered[0] = null;
        check(selection.getTransferDataFlavors()[0] != null, "StringSelection flavours are a copy");
        check(selection.isDataFlavorSupported(DataFlavor.stringFlavor) && !selection.isDataFlavorSupported(DataFlavor.imageFlavor), "StringSelection support");
        check(selection.getTransferData(DataFlavor.stringFlavor) == text, "StringSelection returns the same string");
        try {
            selection.getTransferData(DataFlavor.imageFlavor);
            throw new AssertionError("StringSelection served imageFlavor");
        } catch (UnsupportedFlavorException expected) {
        }

        // Clipboard queries on an in-process clipboard.
        Clipboard clipboard = new Clipboard("contracts");
        equal(clipboard.getName(), "contracts", "clipboard name");
        check(clipboard.getAvailableDataFlavors().length == 0 && !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor), "empty clipboard");
        try {
            clipboard.getData(DataFlavor.stringFlavor);
            throw new AssertionError("empty clipboard served data");
        } catch (UnsupportedFlavorException expected) {
        }
        clipboard.setContents(selection, null);
        check(clipboard.getContents(null) == selection, "clipboard contents");
        check(clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) && !clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor), "clipboard flavour queries");
        equal(clipboard.getData(DataFlavor.stringFlavor), text, "clipboard getData");
        check(Arrays.asList(clipboard.getAvailableDataFlavors()).contains(DataFlavor.plainTextFlavor), "clipboard available flavours");

        // SystemFlavorMap encoding, and the mapping an encoded native establishes both ways.
        String encoded = SystemFlavorMap.encodeJavaMIMEType("text/plain");
        check(SystemFlavorMap.isJavaMIMETypeEncoded(encoded) && !SystemFlavorMap.isJavaMIMETypeEncoded("text/plain"), "encoded native recognised");
        equal(SystemFlavorMap.decodeJavaMIMEType(encoded), "text/plain", "decode");
        equal(SystemFlavorMap.decodeJavaMIMEType("STRING"), null, "decode of a plain native");
        equal(SystemFlavorMap.encodeJavaMIMEType(null), null, "encode null");
        equal(SystemFlavorMap.decodeDataFlavor(SystemFlavorMap.encodeDataFlavor(DataFlavor.stringFlavor)), DataFlavor.stringFlavor, "flavour round trip");
        FlavorMap map = SystemFlavorMap.getDefaultFlavorMap();
        check(map == SystemFlavorMap.getDefaultFlavorMap() && map instanceof FlavorTable, "default flavour map");
        DataFlavor custom = new DataFlavor("application/x-runelite-contract; class=java.lang.String");
        String customNative = SystemFlavorMap.encodeDataFlavor(custom);
        check(((FlavorTable) map).getNativesForFlavor(custom).contains(customNative), "unknown flavour maps to its encoded native");
        check(((FlavorTable) map).getFlavorsForNative(customNative).contains(custom), "encoded native maps back to the flavour");
        Map<DataFlavor, String> natives = map.getNativesForFlavors(new DataFlavor[] {custom});
        equal(natives.get(custom), customNative, "getNativesForFlavors");

        System.out.println("PASS: TestDataTransferContracts (" + checks + " checks)");
    }
}
