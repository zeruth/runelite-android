// host-contract: patched-args=shadow
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Iterator;
import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageOutputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileCacheImageOutputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.IIOByteBuffer;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * javax.imageio contracts: the stream classes' byte order, bit-offset, mark/reset and
 * flush rules, the stream SPIs the registry always carries, registry lookups that no
 * provider answers, and the param/metadata/type-specifier data classes. Reader/writer
 * lookups for real formats are not asserted: the JDK ships PNG/JPEG plugins, the shadow
 * gains them with the plugin card, and only an unknown format is empty on both.
 */
public final class TestImageIOContracts {
    private static int failures;
    private static int total;
    /** Set by the runner's {@code patched-args=shadow} on the run against the shadow classes. */
    private static boolean shadow;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    /** The fixture every output stream writes, so each backing gives the same bytes. */
    private static void writeFixture(ImageOutputStream out) throws Exception {
        out.writeInt(0x01020304);
        out.writeShort(0xABCD);
        out.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        out.writeInt(0x01020304);
        out.writeShort(0xABCD);
        out.writeLong(0x1122334455667788L);
        out.setByteOrder(ByteOrder.BIG_ENDIAN);
        out.writeBits(5, 3);
        check("output bit offset after writeBits(5,3)", out.getBitOffset() == 3);
        out.writeByte(0xFF);
        check("output write after bits aligns", out.getBitOffset() == 0);
        out.writeBits(0x3FF, 10);
        // writeBits leaves the stream on the partial byte it wrote.
        check("output position after writeBits(10)", out.getStreamPosition() == 23);
        check("output bit offset after writeBits(10)", out.getBitOffset() == 2);
    }

    private static final String FIXTURE_HEX =
        "01020304" + "abcd" + "04030201" + "cdab" + "8877665544332211" + "a0" + "ff" + "ffc0";

    private static void readFixture(String label, ImageInputStream in) throws Exception {
        check(label + " default byte order", in.getByteOrder() == ByteOrder.BIG_ENDIAN);
        check(label + " readInt big", in.readInt() == 0x01020304);
        check(label + " readUnsignedShort big", in.readUnsignedShort() == 0xABCD);
        in.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        check(label + " readInt little", in.readInt() == 0x01020304);
        check(label + " readShort little", in.readShort() == (short) 0xABCD);
        check(label + " readLong little", in.readLong() == 0x1122334455667788L);
        in.setByteOrder(ByteOrder.BIG_ENDIAN);
        check(label + " position before bits", in.getStreamPosition() == 20);
        in.mark();
        check(label + " readBits(3)", in.readBits(3) == 5);
        check(label + " input bit offset", in.getBitOffset() == 3);
        check(label + " readBits stays on the partial byte", in.getStreamPosition() == 20);
        in.mark();
        // A byte read drops the bit offset and rereads the partial byte whole.
        check(label + " byte read after bits", in.readUnsignedByte() == 0xA0);
        check(label + " bit offset reset by byte read", in.getBitOffset() == 0);
        in.reset();
        check(label + " reset restores bit offset", in.getBitOffset() == 3
            && in.getStreamPosition() == 20);
        in.reset();
        check(label + " outer reset", in.getBitOffset() == 0 && in.getStreamPosition() == 20);
        check(label + " readBit", in.readBit() == 1 && in.readBit() == 0 && in.readBit() == 1);
        in.skipBytes(2);
        check(label + " skipBytes clears bit offset", in.getBitOffset() == 0
            && in.getStreamPosition() == 22);
        check(label + " readBits(10)", in.readBits(10) == 0x3FF);
        check(label + " readBits(6) of padding", in.readBits(6) == 0);
        check(label + " read at end", in.read() == -1);

        in.seek(4);
        short[] shorts = new short[3];
        in.readFully(shorts, 0, 1);
        check(label + " readFully short[] big", shorts[0] == (short) 0xABCD);
        in.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        int[] ints = new int[1];
        in.readFully(ints, 0, 1);
        check(label + " readFully int[] little", ints[0] == 0x01020304);
        in.setByteOrder(ByteOrder.BIG_ENDIAN);

        IIOByteBuffer buf = new IIOByteBuffer(null, 0, 0);
        in.seek(0);
        in.readBytes(buf, 4);
        check(label + " readBytes length", buf.getLength() == 4);
        check(label + " readBytes data", buf.getData()[buf.getOffset()] == 1
            && buf.getData()[buf.getOffset() + 3] == 4);

        // flushBefore may not pass the current position; the JDK throws before flushing.
        boolean threw = false;
        try {
            in.flushBefore(8);
        } catch (IndexOutOfBoundsException e) {
            threw = true;
        }
        check(label + " flushBefore past position throws", threw
            && in.getFlushedPosition() == 0);
        in.seek(8);
        in.flushBefore(8);
        check(label + " flushed position", in.getFlushedPosition() == 8);
        threw = false;
        try {
            in.seek(4);
        } catch (IndexOutOfBoundsException e) {
            threw = true;
        }
        check(label + " seek before flushed position throws", threw);
        in.close();
        threw = false;
        try {
            in.read();
        } catch (java.io.IOException e) {
            threw = true;
        }
        check(label + " read after close throws IOException", threw);
    }

    private static void streams() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream mem = new MemoryCacheImageOutputStream(sink);
        writeFixture(mem);
        mem.close();
        byte[] bytes = sink.toByteArray();
        check("memory output bytes " + hex(bytes), hex(bytes).equals(FIXTURE_HEX));
        check("memory output is not cached on disk", !mem.isCachedFile() && mem.isCachedMemory());

        File dir = new File(System.getProperty("java.io.tmpdir"));
        sink = new ByteArrayOutputStream();
        FileCacheImageOutputStream fileCache = new FileCacheImageOutputStream(sink, dir);
        writeFixture(fileCache);
        fileCache.close();
        check("file-cache output bytes", hex(sink.toByteArray()).equals(FIXTURE_HEX));
        check("file-cache output is cached on disk", fileCache.isCachedFile());

        File tmp = File.createTempFile("imageio-contract", ".bin");
        tmp.deleteOnExit();
        FileImageOutputStream fileOut = new FileImageOutputStream(tmp);
        writeFixture(fileOut);
        check("file output length", fileOut.length() == 24);
        fileOut.close();
        check("file output on disk", tmp.length() == 24);

        readFixture("memory", new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)));
        readFixture("file-cache", new FileCacheImageInputStream(new ByteArrayInputStream(bytes), dir));
        FileImageInputStream fileIn = new FileImageInputStream(tmp);
        check("file input length", fileIn.length() == 24);
        readFixture("file", fileIn);

        MemoryCacheImageInputStream unknown =
            new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));
        check("memory input length unknown", unknown.length() == -1L);
        check("memory input cached in memory", unknown.isCached() && unknown.isCachedMemory()
            && !unknown.isCachedFile());
        unknown.close();

        boolean threw = false;
        try {
            new MemoryCacheImageInputStream(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("memory input rejects null", threw);
        threw = false;
        try {
            new FileCacheImageInputStream(new ByteArrayInputStream(bytes), tmp);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("file-cache input rejects a non-directory cache", threw);
    }

    private static void registry() throws Exception {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        check("default registry is a singleton", registry == IIORegistry.getDefaultInstance());

        boolean inputStreamSpi = false;
        Iterator<ImageInputStreamSpi> in = registry.getServiceProviders(ImageInputStreamSpi.class, true);
        while (in.hasNext()) {
            if (in.next().getInputClass() == InputStream.class) inputStreamSpi = true;
        }
        check("registry has an InputStream ImageInputStreamSpi", inputStreamSpi);
        boolean fileSpi = false;
        Iterator<ImageOutputStreamSpi> out = registry.getServiceProviders(ImageOutputStreamSpi.class, true);
        while (out.hasNext()) {
            if (out.next().getOutputClass() == File.class) fileSpi = true;
        }
        check("registry has a File ImageOutputStreamSpi", fileSpi);

        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(new byte[] {1, 2}));
        check("createImageInputStream(InputStream)", iis != null && iis.read() == 1);
        iis.close();
        ImageOutputStream ios = ImageIO.createImageOutputStream(new ByteArrayOutputStream());
        check("createImageOutputStream(OutputStream)", ios != null);
        ios.close();
        check("createImageInputStream of an unhandled type is null",
            ImageIO.createImageInputStream("not a stream") == null);

        check("no reader for an unknown format",
            !ImageIO.getImageReadersByFormatName("no-such-format").hasNext());
        check("no reader for an unknown suffix",
            !ImageIO.getImageReadersBySuffix("nosuchsuffix").hasNext());
        check("no reader for an unknown MIME type",
            !ImageIO.getImageReadersByMIMEType("image/x-no-such").hasNext());
        check("no writer for an unknown format",
            !ImageIO.getImageWritersByFormatName("no-such-format").hasNext());
        check("no reader decodes zero bytes", !ImageIO.getImageReaders(
            new MemoryCacheImageInputStream(new ByteArrayInputStream(new byte[0]))).hasNext());

        boolean threw = false;
        try {
            ImageIO.getImageReadersByFormatName(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("getImageReadersByFormatName(null) throws", threw);
        threw = false;
        try {
            ImageIO.read(new File("does-not-exist-imageio-contract.png"));
        } catch (IIOException e) {
            threw = true;
        }
        check("read(missing File) throws IIOException", threw);

        boolean useCache = ImageIO.getUseCache();
        ImageIO.setUseCache(false);
        check("setUseCache(false)", !ImageIO.getUseCache());
        ImageIO.setUseCache(useCache);
        check("cache directory defaults to null", ImageIO.getCacheDirectory() == null);
        threw = false;
        File notDir = File.createTempFile("imageio-contract", ".bin");
        notDir.deleteOnExit();
        try {
            ImageIO.setCacheDirectory(notDir);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("setCacheDirectory(non-directory) throws", threw);

        String[] names = ImageIO.getReaderFormatNames();
        check("getReaderFormatNames is non-null", names != null);
        Iterator<ImageReaderSpi> readers = registry.getServiceProviders(ImageReaderSpi.class, false);
        check("reader names match the registry", readers.hasNext() == (names.length > 0));
    }

    private static void dataClasses() {
        ImageReadParam read = new ImageReadParam();
        read.setSourceRegion(new Rectangle(1, 2, 3, 4));
        check("source region", new Rectangle(1, 2, 3, 4).equals(read.getSourceRegion()));
        read.setSourceSubsampling(2, 3, 1, 0);
        check("subsampling", read.getSourceXSubsampling() == 2 && read.getSourceYSubsampling() == 3
            && read.getSubsamplingXOffset() == 1);
        boolean threw = false;
        try {
            read.setSourceSubsampling(0, 1, 0, 0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("zero subsampling throws", threw);

        ImageWriteParam write = new ImageWriteParam(null);
        check("default write param cannot compress", !write.canWriteCompressed());
        threw = false;
        try {
            write.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        check("setCompressionMode on a non-compressing param throws", threw);

        ImageTypeSpecifier argb =
            ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
        check("type specifier bands", argb.getNumBands() == 4 && argb.getNumComponents() == 4);
        BufferedImage made = argb.createBufferedImage(3, 2);
        check("type specifier creates an image", made.getWidth() == 3 && made.getHeight() == 2);
        if (shadow) {
            // The host patch keeps the JDK BufferedImage (the shadow one needs the native
            // backend) over the shadow Raster, and the JDK only types its own
            // sun.awt.image rasters, so the image comes back TYPE_CUSTOM here; assert the
            // model the shadow BufferedImage types as TYPE_INT_ARGB on Android instead.
            java.awt.image.ColorModel cm = argb.getColorModel();
            check("type specifier model is packed ARGB",
                cm instanceof java.awt.image.DirectColorModel
                && ((java.awt.image.DirectColorModel) cm).getAlphaMask() == 0xff000000
                && ((java.awt.image.DirectColorModel) cm).getRedMask() == 0xff0000
                && !cm.isAlphaPremultiplied() && cm.getColorSpace().isCS_sRGB()
                && made.getColorModel().equals(cm));
        } else {
            check("type specifier round-trips its type",
                argb.getBufferedImageType() == BufferedImage.TYPE_INT_ARGB);
            check("type specifier image type", made.getType() == BufferedImage.TYPE_INT_ARGB);
        }

        IIOImage image = new IIOImage(made, null, null);
        check("IIOImage holds the rendered image", image.getRenderedImage() == made
            && !image.hasRaster() && image.getNumThumbnails() == 0);

        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
        IIOMetadataNode child = new IIOMetadataNode("Chroma");
        child.setAttribute("name", "RGB");
        root.appendChild(child);
        check("metadata node name", "javax_imageio_1.0".equals(root.getNodeName()));
        check("metadata child", root.getFirstChild() == child && child.getParentNode() == root);
        check("metadata attribute", "RGB".equals(child.getAttribute("name")));
        check("metadata missing attribute is empty", "".equals(child.getAttribute("missing")));
        child.setUserObject("payload");
        check("metadata user object", "payload".equals(child.getUserObject()));
    }

    public static void main(String[] args) throws Exception {
        shadow = args.length > 0 && "shadow".equals(args[0]);
        streams();
        registry();
        dataClasses();
        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
