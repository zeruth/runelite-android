package net.runelite.awt.impl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.TexturePaint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;

import net.runelite.awt.AwtNative;

/**
 * Graphics2D backed by a {@link BufferedImage} {@code int[]} pixel buffer. Bulk fills /
 * blits route through the {@link AwtNative} Rust bridge; small / single-pixel ops stay
 * in Java since the JNI hop costs more than the work.
 */
public class BufferedImageGraphics2D extends Graphics2D {
    private BufferedImage image;
    private int[] pixels;
    private int width;
    private int height;
    /** When non-null, this graphics targets a Canvas's LIVE backbuffer: {@link #syncTarget()}
     *  re-resolves the backing at the start of each draw call. This keeps a cached graphics
     *  (notably the OSRS client's MainBufferProvider {@code ln_fld}, captured once via
     *  Canvas.getGraphics()) painting into the CURRENT backbuffer after the Canvas
     *  reallocates it on a resize — otherwise the cached graphics is orphaned to the old
     *  int[] and the visible backbuffer stays black. Null for ordinary image-backed graphics
     *  (icons, glyph caches, sub-buffers), which are unaffected by this whole mechanism. */
    private final java.util.function.Supplier<BufferedImage> backbufferSource;

    /** Font and Stroke are immutable, and every graphics starts on the same pair — sharing
     *  them keeps two allocations out of each Graphics2D construction. Graphics2D objects
     *  are created per Window frame and again by {@link #create()} for Swing paint calls,
     *  so this is on the paint path rather than a one-off. */
    private static final Font DEFAULT_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    private static final Stroke DEFAULT_STROKE = new BasicStroke();

    private Color foreground = Color.BLACK;
    private Color background = Color.WHITE;
    private Font font = DEFAULT_FONT;
    /** Device-space clip. For a non-rectangular clip this is the bounds of {@link #clipRects}. */
    private Rectangle clip;
    /** Null for a plain rectangular clip (the zero-cost common case). Otherwise the clip as
     *  disjoint device rects, (x0, y0, x1, y1) quads, exactly sized — see {@link ClipRegion}.
     *  Never mutated once assigned, so {@link #create()} and the snapshot stack share it. */
    private int[] clipRects;
    /** Single-rect clip handed to natives that take a rect list; see {@link #prepareClip()}. */
    private final int[] clipScratch = new int[4];
    private int[] activeClip;
    /** Rect lists saved by {@link #snapshotClip}, restored LIFO by {@link #restoreClip}. */
    private int[][] clipStack = new int[8][];
    private int clipStackDepth;
    private final AffineTransform transform = new AffineTransform();
    private Composite composite = AlphaComposite.SrcOver;
    private Stroke stroke = DEFAULT_STROKE;
    private Paint paint = Color.BLACK;
    private Color xorColor;
    private final RenderingHints renderingHints = new RenderingHints(null);
    /** Uniform user→device multiplier applied on top of {@link #transform}. Lets a host lay a
     *  Swing tree out in logical pixels but rasterize it at the display's real density
     *  (text, fills, borders and shapes are all drawn at device resolution instead of
     *  being painted small and stretched). 1 for every graphics that doesn't opt in, and
     *  every scaled branch below is gated on that so the default paths are unchanged. */
    private float deviceScale = 1f;

    public BufferedImageGraphics2D(BufferedImage image) {
        this(image, null);
    }

    /** Live-backbuffer variant: the graphics re-resolves its target from {@code source}
     *  each draw call (see {@link #syncTarget()}). Used by Canvas.getGraphics(). */
    public BufferedImageGraphics2D(java.util.function.Supplier<BufferedImage> source) {
        this(source.get(), source);
    }

    private BufferedImageGraphics2D(BufferedImage image, java.util.function.Supplier<BufferedImage> backbufferSource) {
        this.image = image;
        this.pixels = image.backingArray();
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.clip = new Rectangle(0, 0, width, height);
        this.backbufferSource = backbufferSource;
    }

    /** Re-resolve the backing from the live Canvas backbuffer (no-op for plain image-backed
     *  graphics). Single-threaded per instance — a handed-out graphics is only ever drawn on
     *  by its owning thread — so reading the volatile backbuffer and updating our own fields
     *  needs no extra locking. Called at the head of every native draw. */
    private void syncTarget() {
        if (backbufferSource == null) return;
        BufferedImage bb = backbufferSource.get();
        if (bb == null || bb == image) return;
        image = bb;
        pixels = bb.backingArray();
        width = bb.getWidth();
        height = bb.getHeight();
        // Clamp the clip into the new bounds so a shrink can't let a native write run past
        // the (now smaller) array; the client re-sets its own clip each frame anyway.
        int nx = Math.min(clip.x, width);
        int ny = Math.min(clip.y, height);
        int nw = Math.min(clip.width, width - nx);
        int nh = Math.min(clip.height, height - ny);
        clip = new Rectangle(nx, ny, Math.max(0, nw), Math.max(0, nh));
        if (clipRects != null) {
            setClipRects(ClipRegion.intersect(clipRects, clipRects.length / 4, new int[] {0, 0, width, height}, 1));
        }
    }

    private int clipX1() { return (int) Math.min(Integer.MAX_VALUE, (long) clip.x + clip.width); }
    private int clipY1() { return (int) Math.min(Integer.MAX_VALUE, (long) clip.y + clip.height); }

    /** Install a rect-list clip, collapsing to the rectangular fast path when it's one rect. */
    private void setClipRects(int[] rects) {
        int n = rects.length / 4;
        if (n <= 1) {
            clip = n == 0 ? new Rectangle(0, 0, 0, 0)
                : new Rectangle(rects[0], rects[1], rects[2] - rects[0], rects[3] - rects[1]);
            clipRects = null;
            return;
        }
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = 0; i < rects.length; i += 4) {
            x0 = Math.min(x0, rects[i]); y0 = Math.min(y0, rects[i + 1]);
            x1 = Math.max(x1, rects[i + 2]); y1 = Math.max(y1, rects[i + 3]);
        }
        clip = new Rectangle(x0, y0, x1 - x0, y1 - y0);
        clipRects = rects;
    }

    /** Points {@link #activeClip} at the clip as a native rect list (clipped to the image)
     *  and returns its rect count; 0 means nothing is drawable. */
    private int prepareClip() {
        if (clipRects != null) {
            activeClip = clipRects;
            return clipRects.length / 4;
        }
        int x0 = Math.max(clip.x, 0), y0 = Math.max(clip.y, 0);
        int x1 = Math.min(clipX1(), width), y1 = Math.min(clipY1(), height);
        if (x0 >= x1 || y0 >= y1) return 0;
        clipScratch[0] = x0; clipScratch[1] = y0; clipScratch[2] = x1; clipScratch[3] = y1;
        activeClip = clipScratch;
        return 1;
    }

    private Rectangle clipped(int x, int y, int w, int h) {
        int x0 = Math.max(x, clip.x);
        int y0 = Math.max(y, clip.y);
        int x1 = Math.min(x + w, clip.x + clip.width);
        int y1 = Math.min(y + h, clip.y + clip.height);
        int x2 = Math.max(0, Math.min(width, x1));
        int y2 = Math.max(0, Math.min(height, y1));
        int x3 = Math.max(0, x0);
        int y3 = Math.max(0, y0);
        return new Rectangle(x3, y3, Math.max(0, x2 - x3), Math.max(0, y2 - y3));
    }

    @Override
    public Graphics create() {
        BufferedImageGraphics2D copy = new BufferedImageGraphics2D(image, backbufferSource);
        copy.foreground = foreground;
        copy.background = background;
        copy.font = font;
        copy.clip = new Rectangle(clip);
        copy.clipRects = clipRects;
        copy.transform.setTransform(transform);
        copy.composite = composite;
        copy.stroke = stroke;
        copy.paint = paint;
        copy.xorColor = xorColor;
        copy.renderingHints.add(renderingHints);
        copy.deviceScale = deviceScale;
        return copy;
    }

    /** See {@link #deviceScale}. Set before painting; the clip stays in device pixels. */
    public void setDeviceScale(float scale) {
        deviceScale = scale > 0f ? scale : 1f;
    }

    public float getDeviceScale() { return deviceScale; }

    /** Logical x (translate-only user transform) → device pixel edge. Rect edges go through
     *  this individually so adjacent rects still tile without gaps or overlap. */
    private int devX(double x) {
        return (int) Math.floor((x + transform.getTranslateX()) * deviceScale + 0.5);
    }
    private int devY(double y) {
        return (int) Math.floor((y + transform.getTranslateY()) * deviceScale + 0.5);
    }

    /** Device-space rect for a logical rect, clipped. Only for deviceScale != 1. */
    private Rectangle clippedDevice(int x, int y, int w, int h) {
        int x0 = devX(x), y0 = devY(y);
        return clipped(x0, y0, devX(x + (double) w) - x0, devY(y + (double) h) - y0);
    }

    @Override public void translate(int x, int y) { transform.translate(x, y); }
    @Override public void translate(double tx, double ty) { transform.translate(tx, ty); }

    /** Current accumulated x translation as device-space integer. */
    private int tx() { return (int) Math.round(transform.getTranslateX()); }
    /** Current accumulated y translation as device-space integer. */
    private int ty() { return (int) Math.round(transform.getTranslateY()); }

    /** True iff the current AffineTransform has only translation (m00=m11=1, m01=m10=0).
     *  Lets the polygon / line paths skip the per-vertex full-matrix multiply when the
     *  caller hasn't actually rotated/scaled anything (the common case). */
    private boolean transformIsTranslateOnly() {
        return transform.getScaleX() == 1.0 && transform.getScaleY() == 1.0
            && transform.getShearX() == 0.0 && transform.getShearY() == 0.0;
    }

    /** Apply the full AffineTransform to a user-space (x,y), returning device-space x.
     *  Used by drawLine / fillPolygon / drawPolygon to honor rotation+scale+shear in
     *  addition to translation — the previous tx()/ty() shortcut dropped rotation
     *  entirely, which was visible as Quest Helper's minimap arrow head sitting
     *  unrotated at the line endpoint regardless of the line's direction. */
    private int transformX(double x, double y) {
        return (int) Math.round((transform.getScaleX() * x + transform.getShearX() * y
            + transform.getTranslateX()) * deviceScale);
    }
    private int transformY(double x, double y) {
        return (int) Math.round((transform.getShearY() * x + transform.getScaleY() * y
            + transform.getTranslateY()) * deviceScale);
    }

    @Override public Color getColor() { return foreground; }
    @Override public void setColor(Color c) { if (c != null) { foreground = c; paint = c; } }
    @Override public Color getBackground() { return background; }
    @Override public void setBackground(Color c) { if (c != null) background = c; }

    @Override public void setPaintMode() {xorColor=null;}
    @Override public void setXORMode(Color c1) {xorColor=java.util.Objects.requireNonNull(c1);}

    @Override public Font getFont() { return font; }
    @Override public void setFont(Font font) { if (font != null) this.font = font; }
    @Override public FontMetrics getFontMetrics() { return BufferedImageFontMetrics.forFont(font); }
    @Override public FontMetrics getFontMetrics(Font f) { return BufferedImageFontMetrics.forFont(f); }

    @Override public Rectangle getClipBounds() {
        if (!transformIsTranslateOnly()) return getClip().getBounds();
        // Return clip in user space (subtract current translation).
        if (deviceScale != 1f) {
            // Round outward so a component's damage test never drops a partially covered
            // logical pixel.
            double s = deviceScale;
            int x0 = (int) Math.floor(clip.x / s - transform.getTranslateX());
            int y0 = (int) Math.floor(clip.y / s - transform.getTranslateY());
            int x1 = (int) Math.ceil((clip.x + clip.width) / s - transform.getTranslateX());
            int y1 = (int) Math.ceil((clip.y + clip.height) / s - transform.getTranslateY());
            return new Rectangle(x0, y0, x1 - x0, y1 - y0);
        }
        return new Rectangle(clip.x - tx(), clip.y - ty(), clip.width, clip.height);
    }
    @Override public void clipRect(int x, int y, int w, int h) {
        if(!transformIsTranslateOnly()){clip(new java.awt.geom.Rectangle2D.Double(x,y,w,h));return;}
        // Inlined `clip.intersection(new Rectangle(...))` — the JDK version allocates
        // two Rectangles per call (the operand and the result). hostPaint hits this for
        // every component in the tree, so killing both allocations is a meaningful per-
        // frame win when the AWT path is alive (logcat showed 40MB/s of AllocSpace churn
        // dominated by tree-walk overhead).
        int ax, ay, ax2, ay2;
        if (deviceScale == 1f) {
            ax = x + tx(); ay = y + ty(); ax2 = ax + w; ay2 = ay + h;
        } else {
            ax = devX(x); ay = devY(y); ax2 = devX(x + (double) w); ay2 = devY(y + (double) h);
        }
        if (clipRects != null) {
            setClipRects(ClipRegion.intersect(clipRects, clipRects.length / 4, new int[] {ax, ay, ax2, ay2}, 1));
            return;
        }
        int x0 = Math.max(clip.x, ax);
        int y0 = Math.max(clip.y, ay);
        int x1 = Math.min(clip.x + clip.width, ax2);
        int y1 = Math.min(clip.y + clip.height, ay2);
        clip.setBounds(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    // -------------------------------------------------------------------------
    // Zero-allocation state save/restore for [java.awt.Window#hostPaint]. The
    // standard getTransform()/getClip()/setTransform()/setClip() cycle the JDK
    // requires us to expose allocates a fresh AffineTransform AND a fresh
    // Rectangle per component visited. With ~hundreds of components painted per
    // frame on the AWT-rendered chrome, that's a few MB/sec of garbage we can
    // sidestep entirely by snapshotting into caller-owned scratch arrays.
    // -------------------------------------------------------------------------

    /** Captures the current transform's six matrix elements into out[0..5]. */
    public void snapshotTransform(double[] out) {
        out[0] = transform.getScaleX();
        out[1] = transform.getShearY();
        out[2] = transform.getShearX();
        out[3] = transform.getScaleY();
        out[4] = transform.getTranslateX();
        out[5] = transform.getTranslateY();
    }

    /** Restores transform from a previous snapshotTransform() result. */
    public void restoreTransform(double[] in) {
        transform.setTransform(in[0], in[1], in[2], in[3], in[4], in[5]);
    }

    /** Captures clip into out[0..3] = (x, y, w, h). A non-rectangular clip's rect list
     *  can't fit in four ints, so it goes on a LIFO stack that {@link #restoreClip} pops —
     *  a plugin component painted by hostPaint may leave a shape clip behind. Pushing the
     *  (shared, immutable) array reference allocates nothing. */
    public void snapshotClip(int[] out) {
        out[0] = clip.x;
        out[1] = clip.y;
        out[2] = clip.width;
        out[3] = clip.height;
        if (clipStackDepth == clipStack.length) clipStack = java.util.Arrays.copyOf(clipStack, clipStack.length * 2);
        clipStack[clipStackDepth++] = clipRects;
    }

    /** Restores clip from a previous snapshotClip() result. Reuses the existing
     *  Rectangle field via setBounds so no new instance is created. */
    public void restoreClip(int[] in) {
        clip.setBounds(in[0], in[1], in[2], in[3]);
        if (clipStackDepth > 0) {
            clipRects = clipStack[--clipStackDepth];
            clipStack[clipStackDepth] = null;
        } else {
            clipRects = null;
        }
    }
    @Override public void setClip(int x, int y, int w, int h) {
        if(!transformIsTranslateOnly()){setClip(new java.awt.geom.Rectangle2D.Double(x,y,w,h));return;}
        clip = deviceRect(x, y, w, h);
        clipRects = null;
    }
    @Override public Shape getClip() {
        if (!transformIsTranslateOnly()) {
            java.awt.geom.Path2D.Double devicePath = new java.awt.geom.Path2D.Double();
            if (clipRects == null) devicePath.append(clip, false);
            else for (int i = 0; i < clipRects.length; i += 4)
                devicePath.append(new Rectangle(clipRects[i], clipRects[i + 1], clipRects[i + 2] - clipRects[i], clipRects[i + 3] - clipRects[i + 1]), false);
            try {
                AffineTransform inverse = transform.createInverse();
                inverse.concatenate(AffineTransform.getScaleInstance(1 / deviceScale, 1 / deviceScale));
                return inverse.createTransformedShape(devicePath);
            } catch (java.awt.geom.NoninvertibleTransformException e) { return new java.awt.geom.Path2D.Double(); }
        }
        if (clipRects == null) return getClipBounds();
        // Back to user space; the rects are disjoint, so the Area just merges their seams.
        java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
        double s = deviceScale, tx = transform.getTranslateX(), ty = transform.getTranslateY();
        for (int i = 0; i < clipRects.length; i += 4) {
            double x0 = clipRects[i] / s - tx, y0 = clipRects[i + 1] / s - ty;
            double x1 = clipRects[i + 2] / s - tx, y1 = clipRects[i + 3] / s - ty;
            p.moveTo(x0, y0); p.lineTo(x1, y0); p.lineTo(x1, y1); p.lineTo(x0, y1); p.closePath();
        }
        return new java.awt.geom.Area(p);
    }
    @Override public void setClip(Shape shape) {
        syncTarget();
        clipRects = null;
        if (shape == null) {
            clip = new Rectangle(0, 0, width, height);
            return;
        }
        if (shape instanceof Rectangle && transformIsTranslateOnly()) {
            Rectangle b = (Rectangle) shape;
            clip = deviceRect(b.x, b.y, b.width, b.height);
            return;
        }
        int[] r = deviceClipFor(shape, 0, 0, width, height);
        if (r == null) {
            Rectangle b = shape.getBounds();
            clip = deviceRect(b.x, b.y, b.width, b.height);
            return;
        }
        setClipRects(r);
    }

    @Override public void clip(Shape s) {
        if (s == null) {
            setClip(null);
            return;
        }
        if (s instanceof Rectangle && transformIsTranslateOnly()) {
            Rectangle r = (Rectangle) s;
            clipRect(r.x, r.y, r.width, r.height);
            return;
        }
        syncTarget();
        int bx0 = Math.max(clip.x, 0), by0 = Math.max(clip.y, 0);
        int bx1 = Math.min(clipX1(), width), by1 = Math.min(clipY1(), height);
        int[] r = deviceClipFor(s, bx0, by0, bx1, by1);
        if (r == null) {
            Rectangle b = s.getBounds();
            clipRect(b.x, b.y, b.width, b.height);
            return;
        }
        if (clipRects != null) r = ClipRegion.intersect(r, r.length / 4, clipRects, clipRects.length / 4);
        setClipRects(r);
    }

    /** Rasterize a clip shape (user space) to device rects within the given bounds, with
     *  the fill pixel-centre rule; null when the shape has no path to walk. Rectilinear
     *  shapes with integer edges (Rectangle2D, a rect-minus-rects Area) come out exact. */
    private int[] deviceClipFor(Shape shape, int bx0, int by0, int bx1, int by1) {
        java.awt.geom.PathIterator it;
        try { it = shape.getPathIterator(null); } catch (Throwable t) { it = null; }
        if (it == null) return null;
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        c.appendFlattened(it, transform, deviceScale, 0.25);
        return ClipRegion.rasterize(c, c.isEvenOdd(), bx0, by0, bx1, by1);
    }

    /** Unclipped device rect for a logical rect. */
    private Rectangle deviceRect(int x, int y, int w, int h) {
        if (deviceScale == 1f) return new Rectangle(x + tx(), y + ty(), w, h);
        int x0 = devX(x), y0 = devY(y);
        return new Rectangle(x0, y0, devX(x + (double) w) - x0, devY(y + (double) h) - y0);
    }

    @Override
    public void copyArea(int x, int y, int w, int h, int dx, int dy) {
        if (!image.isCanonicalBuffer() || !transformIsTranslateOnly()) {
            syncTarget();
            java.awt.geom.AffineTransform device = new java.awt.geom.AffineTransform(transform);
            device.preConcatenate(java.awt.geom.AffineTransform.getScaleInstance(deviceScale, deviceScale));
            Shape source = device.createTransformedShape(new Rectangle(x, y, w, h));
            java.awt.geom.Point2D delta = device.deltaTransform(new java.awt.geom.Point2D.Double(dx, dy), null);
            int moveX = (int)Math.round(delta.getX()), moveY = (int)Math.round(delta.getY());
            Rectangle capture = source.getBounds().intersection(new Rectangle(0, 0, width, height));
            Rectangle destination = new Rectangle(capture);
            destination.translate(moveX, moveY);
            destination = destination.intersection(clip).intersection(new Rectangle(0, 0, width, height));
            if (destination.isEmpty()) return;
            capture = new Rectangle(destination.x-moveX,destination.y-moveY,destination.width,destination.height);
            GeneralScratch cached = GENERAL_SCRATCH.get();
            boolean retained = !cached.busy && (long)capture.width*capture.height <= MAX_RETAINED_PIXELS;
            GeneralScratch scratch = retained ? cached : new GeneralScratch();
            scratch.busy = true;
            try {
                scratch.preparePixels(capture.width,capture.height);
                int[] original = image.getRGB(capture.x,capture.y,capture.width,capture.height,scratch.destination,0,capture.width);
                for (int row = destination.y; row < destination.y+destination.height; row++) for (int col = destination.x; col < destination.x+destination.width; col++) {
                    int sx = col - moveX, sy = row - moveY;
                    if (sx < 0 || sy < 0 || sx >= width || sy >= height || !source.contains(sx + .5, sy + .5)) continue;
                    boolean inside = clip.contains(col, row);
                    if (inside && clipRects != null) {
                        inside = false;
                        for (int i = 0; i < clipRects.length; i += 4) if (col >= clipRects[i] && col < clipRects[i + 2] && row >= clipRects[i + 1] && row < clipRects[i + 3]) { inside = true; break; }
                    }
                    if (inside) image.setRGB(col, row, original[(sy-capture.y)*capture.width+sx-capture.x]);
                }
            } finally { scratch.busy=false; if(!retained)scratch.recycle(); }
            return;
        }

        syncTarget();
        Rectangle src = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        if (src.width == 0 || src.height == 0) return;
        int ddx = deviceScale == 1f ? dx : Math.round(dx * deviceScale);
        int ddy = deviceScale == 1f ? dy : Math.round(dy * deviceScale);
        AwtNative.copyArea(pixels, width, height,
            src.x, src.y, src.width, src.height, src.x + ddx, src.y + ddy);
    }

    /**
     * A 1px Swing line at deviceScale: keep it one LOGICAL pixel thick, otherwise every
     * border and separator turns into a hairline. Axis-aligned lines (nearly all of Swing)
     * fill the covered logical pixel cells as a device rect; anything else becomes a quad
     * of the same thickness through the pixel centres.
     */
    private void drawScaledLine(int x1, int y1, int x2, int y2) {
        if (transformIsTranslateOnly() && (x1 == x2 || y1 == y2)) {
            int lx = Math.min(x1, x2), ly = Math.min(y1, y2);
            fillDevice(clippedDevice(lx, ly, Math.max(x1, x2) - lx + 1, Math.max(y1, y2) - ly + 1),
                foreground.getRGB(), compositeRule());
            return;
        }
        double s = deviceScale;
        double ax = (transform.getScaleX() * (x1 + 0.5) + transform.getShearX() * (y1 + 0.5) + transform.getTranslateX()) * s;
        double ay = (transform.getShearY() * (x1 + 0.5) + transform.getScaleY() * (y1 + 0.5) + transform.getTranslateY()) * s;
        double bx = (transform.getScaleX() * (x2 + 0.5) + transform.getShearX() * (y2 + 0.5) + transform.getTranslateX()) * s;
        double by = (transform.getShearY() * (x2 + 0.5) + transform.getScaleY() * (y2 + 0.5) + transform.getTranslateY()) * s;
        double dx = bx - ax, dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3) return;
        double hw = s * 0.5;
        double nx = -dy / len * hw, ny = dx / len * hw;
        double ex = dx / len * hw, ey = dy / len * hw;
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        c.moveTo(ax - ex + nx, ay - ey + ny);
        c.lineTo(ax - ex - nx, ay - ey - ny);
        c.lineTo(bx + ex - nx, by + ey - ny);
        c.lineTo(bx + ex + nx, by + ey + ny);
        c.end(true);
        fillContours(c, false);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        if(generalRendering()){generalShape(new java.awt.geom.Line2D.Double(x1,y1,x2,y2),false);return;}
        syncTarget();
        if (!plainThinStroke()) {
            strokeUserPolyline(new double[] {x1, y1, x2, y2}, 2, false);
            return;
        }
        if (deviceScale != 1f) { drawScaledLine(x1, y1, x2, y2); return; }
        int sx1, sy1, sx2, sy2;
        if (transformIsTranslateOnly()) {
            int dx = tx(), dy = ty();
            sx1 = x1 + dx; sy1 = y1 + dy;
            sx2 = x2 + dx; sy2 = y2 + dy;
        } else {
            sx1 = transformX(x1, y1); sy1 = transformY(x1, y1);
            sx2 = transformX(x2, y2); sy2 = transformY(x2, y2);
        }
        drawLineDevice(sx1, sy1, sx2, sy2, 0);
    }

    /** 1px device-space line through the clip (every rect of a rect-list clip). The native
     *  solves the visible span against each rect, so far-off endpoints cost nothing. */
    private void drawLineDevice(int x0, int y0, int x1, int y1, int flags) {
        int argb = foreground.getRGB(), rule = compositeRule();
        if (clipRects == null) {
            AwtNative.drawLine(pixels, width, height, x0, y0, x1, y1, argb, rule,
                clip.x, clip.y, clipX1(), clipY1(), flags);
            return;
        }
        int[] cr = clipRects;
        for (int i = 0; i < cr.length; i += 4) {
            AwtNative.drawLine(pixels, width, height, x0, y0, x1, y1, argb, rule,
                cr[i], cr[i + 1], cr[i + 2], cr[i + 3], flags);
        }
    }

    /** Fill device-space contours through the clip in one pass. */
    private void fillContours(Contours c, boolean evenOdd) {
        if (c.n == 0) return;
        if (paint instanceof TexturePaint) {
            int[] rects = ClipRegion.rasterize(c, evenOdd, clip.x, clip.y, clipX1(), clipY1());
            for (int i = 0; i < rects.length; i += 4)
                fillTexture(new Rectangle(rects[i], rects[i + 1], rects[i + 2] - rects[i], rects[i + 3] - rects[i + 1]));
            return;
        }
        int n = prepareClip();
        if (n == 0) return;
        AwtNative.fillPath(pixels, width, height, c.xy, c.counts, c.n, evenOdd,
            activeClip, n, foreground.getRGB(), compositeRule());
    }

    /** Fill a device rect already intersected with the clip bounds and image. */
    private void fillDevice(Rectangle r, int argb, int rule) {
        if (r.width == 0 || r.height == 0) return;
        if (clipRects == null) {
            AwtNative.fillRect(pixels, width, height, r.x, r.y, r.width, r.height, argb, rule);
            return;
        }
        int x1 = r.x + r.width, y1 = r.y + r.height;
        int[] cr = clipRects;
        for (int i = 0; i < cr.length; i += 4) {
            int a = Math.max(r.x, cr[i]), b = Math.max(r.y, cr[i + 1]);
            int c = Math.min(x1, cr[i + 2]), d = Math.min(y1, cr[i + 3]);
            if (a < c && b < d) AwtNative.fillRect(pixels, width, height, a, b, c - a, d - b, argb, rule);
        }
    }

    @Override
    public void fillRect(int x, int y, int w, int h) {
        if(generalRendering()){generalShape(new Rectangle(x,y,w,h),true);return;}
        syncTarget();
        if (paint instanceof TexturePaint) {
            if (w > 0 && h > 0) fillShape(new Rectangle(x, y, w, h));
            return;
        }
        Rectangle r = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        fillDevice(r, foreground.getRGB(), compositeRule());
    }

    /** Sample visible strips and reuse the image compositor for alpha and shape clips. */
    private void fillTexture(Rectangle r) {
        if (r.width <= 0 || r.height <= 0) return;
        double s = deviceScale;
        AffineTransform device = new AffineTransform(transform.getScaleX() * s,
            transform.getShearY() * s, transform.getShearX() * s,
            transform.getScaleY() * s, transform.getTranslateX() * s, transform.getTranslateY() * s);
        PaintContext context = paint.createContext(null, r, r, device, renderingHints);
        try {
            for (int y = r.y; y < r.y + r.height; y += 32) {
                int h = Math.min(32, r.y + r.height - y);
                java.awt.image.Raster raster = context.getRaster(r.x, y, r.width, h);
                int[] data = ((java.awt.image.DataBufferInt) raster.getDataBuffer()).getData();
                blitDevice(data, r.width, h, 0, 0, r.width, h,
                    r.x, y, r.x + r.width, y + h, compositeRule(), compositeAlpha());
            }
        } finally { context.dispose(); }
    }

    @Override
    public void clearRect(int x,int y,int w,int h) {
        if(generalRendering()){Paint previous=paint;Composite comp=composite;Color xor=xorColor;try{paint=background;composite=AlphaComposite.Src;xorColor=null;generalShape(new Rectangle(x,y,w,h),true);}finally{paint=previous;composite=comp;xorColor=xor;}return;}
        syncTarget();
        Rectangle r = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        fillDevice(r, background.getRGB(), AlphaComposite.SRC);
    }

    @Override
    public void drawRect(int x, int y, int w, int h) {
        if(generalRendering()){generalShape(new Rectangle(x,y,w,h),false);return;}
        if (w < 0 || h < 0) return;
        if (plainThinStroke()) {
            super.drawRect(x, y, w, h);
            return;
        }
        syncTarget();
        strokeUserPolyline(new double[] {x, y, x + w, y, x + w, y + h, x, y + h}, 4, true);
    }

    private boolean strokeWidthIsOne() {
        return !(stroke instanceof BasicStroke) || ((BasicStroke) stroke).getLineWidth() <= 1f;
    }

    /** sqrt(|det|) of the user transform: how much a user-space stroke width grows. */
    private double userScale() {
        if (transformIsTranslateOnly()) return 1.0;
        return Math.sqrt(Math.abs(transform.getScaleX() * transform.getScaleY()
            - transform.getShearX() * transform.getShearY()));
    }

    /** JDK "thin" stroke: a solid BasicStroke no wider than one pixel after the user
     *  transform. Those draw as 1px Bresenham lines; everything else is stroked. */
    private boolean plainThinStroke() {
        if (paint instanceof TexturePaint) return false;
        if (!(stroke instanceof BasicStroke)) return false;
        BasicStroke bs = (BasicStroke) stroke;
        return bs.getLineWidth() * userScale() <= 1.0 && bs.getDashArray() == null;
    }

    /** Stroke a user-space polyline with the current stroke. */
    private void strokeUserPolyline(double[] pts, int n, boolean closed) {
        if (!(stroke instanceof BasicStroke)) {
            java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < n; i++) {
                if (i == 0) p.moveTo(pts[0], pts[1]); else p.lineTo(pts[i * 2], pts[i * 2 + 1]);
            }
            if (closed) p.closePath();
            strokeShape(p);
            return;
        }
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        double s = deviceScale;
        for (int i = 0; i < n; i++) {
            double x = pts[i * 2], y = pts[i * 2 + 1];
            double dx = (transform.getScaleX() * x + transform.getShearX() * y + transform.getTranslateX()) * s;
            double dy = (transform.getShearY() * x + transform.getScaleY() * y + transform.getTranslateY()) * s;
            if (i == 0) c.moveTo(dx, dy); else c.lineTo(dx, dy);
        }
        c.end(closed);
        strokeDevice(c);
    }

    /** draw(Shape) for everything that isn't a plain thin stroke fast path. */
    private void strokeShape(Shape s) {
        if (!(stroke instanceof BasicStroke)) {
            // Custom Stroke: its outline is what gets filled, as in the JDK.
            Shape outline = stroke.createStrokedShape(s);
            if (outline != null) fill(outline);
            return;
        }
        java.awt.geom.PathIterator it;
        try { it = s.getPathIterator(null); } catch (Throwable t) { it = null; }
        if (it == null) {
            Rectangle b = s.getBounds();
            super.drawRect(b.x, b.y, b.width, b.height);
            return;
        }
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        c.appendFlattened(it, transform, deviceScale, 0.25);
        strokeDevice(c);
    }

    /**
     * Stroke a device-space path with the current BasicStroke. The path is first snapped
     * the way Java2D's non-AA stroke normalization does (coordinates to the nearest
     * quarter-pixel, {@code floor(v + 0.25) + 0.25}), so a 2px line on integer
     * coordinates covers the same two pixel rows as on desktop. Thin strokes go to the
     * Bresenham path (one LOGICAL pixel wide at deviceScale); thick ones are emitted by
     * {@link Stroker} as overlapping pieces and filled in a single non-zero pass, so a
     * translucent outline blends once at its corners.
     */
    private void strokeDevice(Contours path) {
        BasicStroke bs = (BasicStroke) stroke;
        if (renderingHints.get(RenderingHints.KEY_STROKE_CONTROL) != RenderingHints.VALUE_STROKE_PURE) normalize(path);
        double scale = userScale() * deviceScale;
        Contours[] scratch = CONTOUR_SCRATCH.get();
        Contours src = path;
        float[] dash = bs.getDashArray();
        if (dash != null && dash.length > 0) {
            Contours dashed = scratch[1];
            dashed.reset();
            Stroker.dash(path, dash, scale, bs.getDashPhase() * scale, dashed);
            src = dashed;
        }
        double w = bs.getLineWidth() * scale;
        if (deviceScale != 1f) {
            w = Math.max(w, deviceScale);
        } else if (w <= 1.0) {
            drawThinDevice(src);
            return;
        }
        Contours pieces = scratch[2];
        pieces.reset();
        Stroker.stroke(src, w / 2, bs.getEndCap(), bs.getLineJoin(), bs.getMiterLimit(), pieces);
        fillContours(pieces, false);
    }

    private static void normalize(Contours c) {
        double[] xy = c.xy;
        for (int i = 0; i < c.nCoords; i++) xy[i] = Math.floor(xy[i] + 0.25) + 0.25;
    }

    /** Device-space polylines as 1px lines; shared vertices are plotted once. */
    private void drawThinDevice(Contours c) {
        int off = 0;
        for (int k = 0; k < c.n; k++) {
            int cnt = c.counts[k];
            if (cnt == 1) {
                int x = (int) Math.floor(c.xy[off]), y = (int) Math.floor(c.xy[off + 1]);
                drawLineDevice(x, y, x, y, 0);
            }
            int segs = c.closed[k] && cnt > 2 ? cnt : cnt - 1;
            for (int s = 0; s < segs; s++) {
                int j = (s + 1) % cnt;
                int flags = s == 0 ? 0 : LINE_SKIP_FIRST;
                if (c.closed[k] && cnt > 2 && s == segs - 1) flags |= LINE_SKIP_LAST;
                drawLineDevice((int) Math.floor(c.xy[off + s * 2]), (int) Math.floor(c.xy[off + s * 2 + 1]),
                    (int) Math.floor(c.xy[off + j * 2]), (int) Math.floor(c.xy[off + j * 2 + 1]), flags);
            }
            off += cnt * 2;
        }
    }

    private static final int LINE_SKIP_FIRST = 1;
    private static final int LINE_SKIP_LAST = 2;

    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        if(generalRendering()){generalShape(new java.awt.geom.RoundRectangle2D.Double(x,y,w,h,aw,ah),false);return;}
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawRoundRect(
            new android.graphics.RectF(0, 0, sw, sh), aw / 2f, ah / 2f, p));
    }
    /** The oval / round-rect natives only clamp to the image, so they're used only when the
     *  shape (plus one pixel of slack) lies inside a rectangular clip; otherwise the
     *  clipped rasterizeShape path draws it. */
    private boolean nativeShapeFits(int x, int y, int w, int h) {
        if (clipRects != null) return false;
        long dx = (long) x + tx(), dy = (long) y + ty();
        return dx - 1 >= clip.x && dy - 1 >= clip.y
            && dx + w + 1 <= clipX1() && dy + h + 1 <= clipY1();
    }

    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        if(generalRendering()){generalShape(new java.awt.geom.RoundRectangle2D.Double(x,y,w,h,aw,ah),true);return;}
        syncTarget();
        if (paint instanceof TexturePaint) {
            if (w <= 0 || h <= 0) return;
            double rx = Math.min(w, Math.abs((double) aw)) / 2;
            double ry = Math.min(h, Math.abs((double) ah)) / 2;
            double k = 0.5522847498307933;
            java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
            path.moveTo(x + rx, y);
            path.lineTo(x + w - rx, y);
            path.curveTo(x + w - rx + k * rx, y, x + w, y + ry - k * ry, x + w, y + ry);
            path.lineTo(x + w, y + h - ry);
            path.curveTo(x + w, y + h - ry + k * ry, x + w - rx + k * rx, y + h, x + w - rx, y + h);
            path.lineTo(x + rx, y + h);
            path.curveTo(x + rx - k * rx, y + h, x, y + h - ry + k * ry, x, y + h - ry);
            path.lineTo(x, y + ry);
            path.curveTo(x, y + ry - k * ry, x + rx - k * rx, y, x + rx, y);
            path.closePath();
            fillShape(path);
            return;
        }
        if (transformIsTranslateOnly() && deviceScale == 1f && nativeShapeFits(x, y, w, h)) {
            AwtNative.fillRoundRect(pixels, width, height, x + tx(), y + ty(), w, h, aw, ah,
                foreground.getRGB(), compositeRule());
            return;
        }
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawRoundRect(
            new android.graphics.RectF(0, 0, sw, sh), aw / 2f, ah / 2f, p));
    }
    @Override public void drawOval(int x, int y, int w, int h) {
        if(generalRendering()){generalShape(new java.awt.geom.Ellipse2D.Double(x,y,w,h),false);return;}
        syncTarget();
        if (transformIsTranslateOnly() && strokeWidthIsOne() && deviceScale == 1f && nativeShapeFits(x, y, w, h)) {
            AwtNative.drawOval(pixels, width, height, x + tx(), y + ty(), w, h, foreground.getRGB());
            return;
        }
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawOval(
            new android.graphics.RectF(0, 0, sw, sh), p));
    }
    @Override public void fillOval(int x, int y, int w, int h) {
        if(generalRendering()){generalShape(new java.awt.geom.Ellipse2D.Double(x,y,w,h),true);return;}
        syncTarget();
        if (paint instanceof TexturePaint) {
            fillShape(new java.awt.geom.Ellipse2D.Double(x, y, w, h));
            return;
        }
        if (transformIsTranslateOnly() && deviceScale == 1f && nativeShapeFits(x, y, w, h)) {
            AwtNative.fillOval(pixels, width, height, x + tx(), y + ty(), w, h,
                foreground.getRGB(), compositeRule());
            return;
        }
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawOval(
            new android.graphics.RectF(0, 0, sw, sh), p));
    }
    @Override public void drawArc(int x, int y, int w, int h, int sa, int aa) {
        if(generalRendering()){generalShape(new java.awt.geom.Arc2D.Double(x,y,w,h,sa,aa,java.awt.geom.Arc2D.OPEN),false);return;}
        // AWT angles: 0° = 3 o'clock, positive = counter-clockwise.
        // Android angles: same origin, but positive = clockwise → negate both.
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawArc(
            new android.graphics.RectF(0, 0, sw, sh), -sa, -aa, false, p));
    }
    @Override public void fillArc(int x, int y, int w, int h, int sa, int aa) {
        if(generalRendering()){generalShape(new java.awt.geom.Arc2D.Double(x,y,w,h,sa,aa,java.awt.geom.Arc2D.PIE),true);return;}
        // useCenter=true → pie slice (matches AWT fillArc semantics).
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawArc(
            new android.graphics.RectF(0, 0, sw, sh), -sa, -aa, true, p));
    }

    @FunctionalInterface
    private interface AndroidShapeDrawer {
        void draw(android.graphics.Canvas c, android.graphics.Paint p, int sw, int sh);
    }

    /**
     * Rasterize an Android-Canvas-drawn shape into an ARGB bitmap then blit onto the
     * backing buffer at {@code (x + tx(), y + ty())}, clipped against the active clip.
     * Used by drawArc/fillArc/drawOval/fillOval/drawRoundRect/fillRoundRect.
     */
    /**
     * Reusable rasterization target for {@link #rasterizeShape}. Every arc / oval /
     * round-rect that can't take a native fast path used to allocate a Bitmap, a Canvas,
     * a Paint and an {@code int[bw*bh]} per call — and RuneLite overlays draw arcs every
     * frame (cooldown pies, progress rings), so that is a steady stream of garbage in the
     * middle of the paint loop. One scratch per drawing thread, grown on demand, instead.
     */
    private static final class ShapeScratch {
        android.graphics.Bitmap bmp;
        android.graphics.Canvas canvas;
        int[] pixels;
        int w, h;
        final android.graphics.Paint paint = new android.graphics.Paint();
    }

    /** Above this the scratch is not retained — a one-off huge shape shouldn't leave a
     *  multi-megabyte bitmap parked on the thread forever. */
    private static final int MAX_SCRATCH_PIXELS = 1024 * 1024;

    private static final ThreadLocal<ShapeScratch> SHAPE_SCRATCH =
        ThreadLocal.withInitial(ShapeScratch::new);

    private void rasterizeShape(int x, int y, int w, int h, boolean filled, AndroidShapeDrawer drawer) {
        if (w <= 0 || h <= 0) return;
        syncTarget();
        float strokeW = filled ? 0f : ((stroke instanceof BasicStroke) ? ((BasicStroke) stroke).getLineWidth() : 1f);
        // At deviceScale the shape is drawn in logical units under a canvas scale, so the
        // bitmap, pad and stroke reach are all sized in device pixels.
        int adx, ady, dw, dh;
        if (deviceScale == 1f) {
            adx = x + tx(); ady = y + ty(); dw = w; dh = h;
        } else {
            adx = devX(x); ady = devY(y);
            dw = Math.max(1, devX(x + (double) w) - adx);
            dh = Math.max(1, devY(y + (double) h) - ady);
        }
        // +1 covers AA bleed; ceil(strokeW/2) covers the stroke's outward reach.
        int pad = (int) Math.ceil(strokeW * deviceScale / 2f) + 1;
        int bw = dw + 2 * pad;
        int bh = dh + 2 * pad;

        ShapeScratch sc = SHAPE_SCRATCH.get();
        boolean pooled = (long) bw * bh <= MAX_SCRATCH_PIXELS;

        android.graphics.Bitmap bmp;
        android.graphics.Canvas canvas;
        int[] sp;
        if (pooled) {
            if (sc.bmp == null || sc.w < bw || sc.h < bh) {
                int nw = Math.max(bw, sc.w);
                int nh = Math.max(bh, sc.h);
                // Growing both dimensions independently can compound (a wide shape then a
                // tall one), so fall back to exactly what's asked for if the union blows
                // the budget.
                if ((long) nw * nh > MAX_SCRATCH_PIXELS) { nw = bw; nh = bh; }
                if (sc.bmp != null) sc.bmp.recycle();
                sc.bmp = android.graphics.Bitmap.createBitmap(
                    nw, nh, android.graphics.Bitmap.Config.ARGB_8888);
                sc.canvas = new android.graphics.Canvas(sc.bmp);
                sc.w = nw;
                sc.h = nh;
            }
            if (sc.pixels == null || sc.pixels.length < bw * bh) {
                sc.pixels = new int[bw * bh];
            }
            bmp = sc.bmp;
            canvas = sc.canvas;
            sp = sc.pixels;
        } else {
            bmp = android.graphics.Bitmap.createBitmap(
                bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
            canvas = new android.graphics.Canvas(bmp);
            sp = new int[bw * bh];
        }

        canvas.save();
        try {
            // A reused bitmap still holds the previous shape; clear just the region we're
            // about to use rather than the whole (possibly much larger) scratch.
            canvas.clipRect(0, 0, bw, bh);
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            canvas.translate(pad, pad);
            if (deviceScale != 1f) canvas.scale(dw / (float) w, dh / (float) h);
            android.graphics.Paint p = sc.paint;
            p.reset();
            p.setAntiAlias(true);
            p.setColor(foreground.getRGB());
            p.setStyle(filled ? android.graphics.Paint.Style.FILL : android.graphics.Paint.Style.STROKE);
            if (!filled) {
                p.setStrokeWidth(Math.max(1f, strokeW));
            }
            drawer.draw(canvas, p, w, h);
        } catch (Throwable ignored) {
            return;
        } finally {
            canvas.restore();
        }
        // Reads a bw x bh sub-rect at stride bw, so a larger pooled bitmap is fine.
        bmp.getPixels(sp, 0, bw, 0, 0, bw, bh);
        if (!pooled) bmp.recycle();

        int dstX = adx - pad;
        int dstY = ady - pad;
        blitDevice(sp, bw, bh, 0, 0, bw, bh, dstX, dstY, dstX + bw, dstY + bh,
            compositeRule(), compositeAlpha());
    }

    /**
     * Blit {@code src[sx, sy, sw, sh]} onto the device rect [adx, adx2) x [ady, ady2)
     * through the clip — every rect of a rect-list clip. The source window is shifted
     * proportionally to what the clip cuts off, so the same source pixels land at the
     * same device positions whatever the clip.
     */
    private void blitDevice(int[] src, int srcW, int srcH, int sx, int sy, int sw, int sh,
                            int adx, int ady, int adx2, int ady2, int rule, float alpha) {
        if (clipRects == null) {
            blitInto(clip.x, clip.y, clipX1(), clipY1(), src, srcW, srcH, sx, sy, sw, sh, adx, ady, adx2, ady2, rule, alpha);
            return;
        }
        int[] cr = clipRects;
        for (int i = 0; i < cr.length; i += 4) {
            blitInto(cr[i], cr[i + 1], cr[i + 2], cr[i + 3], src, srcW, srcH, sx, sy, sw, sh, adx, ady, adx2, ady2, rule, alpha);
        }
    }

    private void blitInto(int cx0, int cy0, int cx1, int cy1, int[] src, int srcW, int srcH,
                          int sx, int sy, int sw, int sh, int adx, int ady, int adx2, int ady2,
                          int rule, float alpha) {
        int dw = adx2 - adx, dh = ady2 - ady;
        if (dw <= 0 || dh <= 0) return;
        int ndx = Math.max(adx, cx0);
        int ndy = Math.max(ady, cy0);
        int ndw = Math.min(adx2, cx1) - ndx;
        int ndh = Math.min(ady2, cy1) - ndy;
        if (ndw <= 0 || ndh <= 0) return;
        // Source shift in destination-pixel units. With non-1:1 scaling the source
        // advance per dest pixel is sw/dw, so multiply the dst clip offset by that ratio.
        // Use long to avoid intermediate overflow.
        int nsx = sx + (int) ((long) (ndx - adx) * sw / dw);
        int nsy = sy + (int) ((long) (ndy - ady) * sh / dh);
        int nsw = (int) ((long) ndw * sw / dw);
        int nsh = (int) ((long) ndh * sh / dh);
        if (nsw <= 0 || nsh <= 0) return;
        AwtNative.blit(src, srcW, srcH, nsx, nsy, nsw, nsh,
            pixels, width, height, ndx, ndy, ndw, ndh, rule, alpha);
    }

    @Override
    public void drawPolyline(int[] xs, int[] ys, int n) {
        if(generalRendering()){generalShape(polyline(xs,ys,n,false),false);return;}
        drawIntPath(xs, ys, n, false);
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n) {
        if(generalRendering()){generalShape(polyline(xs,ys,n,true),false);return;}
        drawIntPath(xs, ys, n, true);
    }

    /**
     * Outline an int polyline / polygon with the current stroke. Thin strokes are 1px
     * lines with each shared vertex plotted once (a translucent outline would otherwise
     * show darker dots at every corner); anything wider, dashed, or a custom Stroke goes
     * through the stroker as one path, so joins follow BasicStroke's join style.
     */
    private void drawIntPath(int[] xs, int[] ys, int n, boolean closed) {
        if (xs == null || ys == null) return;
        n = Math.min(n, Math.min(xs.length, ys.length));
        if (n <= 0 || (!closed && n < 2)) return;
        syncTarget();
        if (!plainThinStroke()) {
            double[] pts = new double[n * 2];
            for (int i = 0; i < n; i++) { pts[i * 2] = xs[i]; pts[i * 2 + 1] = ys[i]; }
            strokeUserPolyline(pts, n, closed);
            return;
        }
        if (deviceScale != 1f) {
            int segs = closed ? n : n - 1;
            for (int i = 0; i < segs; i++) {
                int j = (i + 1) % n;
                drawScaledLine(xs[i], ys[i], xs[j], ys[j]);
            }
            return;
        }
        boolean translateOnly = transformIsTranslateOnly();
        int dx = tx(), dy = ty();
        int segs = closed ? n : n - 1;
        int px = translateOnly ? xs[0] + dx : transformX(xs[0], ys[0]);
        int py = translateOnly ? ys[0] + dy : transformY(xs[0], ys[0]);
        if (segs == 0) {
            drawLineDevice(px, py, px, py, 0);
            return;
        }
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            int qx = translateOnly ? xs[j] + dx : transformX(xs[j], ys[j]);
            int qy = translateOnly ? ys[j] + dy : transformY(xs[j], ys[j]);
            int flags = i == 0 ? 0 : LINE_SKIP_FIRST;
            if (closed && i == segs - 1 && n > 1) flags |= LINE_SKIP_LAST;
            drawLineDevice(px, py, qx, qy, flags);
            px = qx;
            py = qy;
        }
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n) {
        if(generalRendering()){generalShape(polyline(xs,ys,n,true),true);return;}
        if (xs == null || ys == null) return;
        n = Math.min(n, Math.min(xs.length, ys.length));
        if (n < 3) return;
        syncTarget();
        // Full matrix per vertex (rotation included: quest-helper's minimap arrow head is
        // a Polygon filled under a translate+rotate transform), then the same quarter-pixel
        // normalization Java2D applies to non-AA fills; on integer vertices that's +0.25,
        // which leaves axis-aligned edges on the same pixels.
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        c.ensureCoords(n * 2);
        double m00 = transform.getScaleX(), m01 = transform.getShearX(), m02 = transform.getTranslateX();
        double m10 = transform.getShearY(), m11 = transform.getScaleY(), m12 = transform.getTranslateY();
        double s = deviceScale;
        boolean pure = renderingHints.get(RenderingHints.KEY_STROKE_CONTROL) == RenderingHints.VALUE_STROKE_PURE;
        double[] xy = c.xy;
        for (int i = 0; i < n; i++) {
            double x = xs[i], y = ys[i];
            double dx = (m00 * x + m01 * y + m02) * s, dy = (m10 * x + m11 * y + m12) * s;
            xy[i * 2] = pure ? dx : Math.floor(dx + 0.25) + 0.25;
            xy[i * 2 + 1] = pure ? dy : Math.floor(dy + 0.25) + 0.25;
        }
        c.nCoords = n * 2;
        c.counts[0] = n;
        c.closed[0] = true;
        c.n = 1;
        fillContours(c, true);
    }

    /** fill(Shape) for anything without a dedicated fast path: flatten in device space and
     *  fill all subpaths in one pass under the path's own winding rule (holes stay holes). */
    private void fillShape(Shape s) {
        java.awt.geom.PathIterator it;
        try { it = s.getPathIterator(null); } catch (Throwable t) { it = null; }
        if (it == null) {
            Rectangle b = s.getBounds();
            fillRect(b.x, b.y, b.width, b.height);
            return;
        }
        syncTarget();
        Contours c = CONTOUR_SCRATCH.get()[0];
        c.reset();
        c.appendFlattened(it, transform, deviceScale, 0.25);
        if (renderingHints.get(RenderingHints.KEY_STROKE_CONTROL) != RenderingHints.VALUE_STROKE_PURE) normalize(c);
        fillContours(c, c.isEvenOdd());
    }

    /** Per-thread path scratch: [0] path, [1] dashed path, [2] stroke pieces. */
    private static final ThreadLocal<Contours[]> CONTOUR_SCRATCH = new ThreadLocal<Contours[]>() {
        @Override protected Contours[] initialValue() {
            return new Contours[] { new Contours(), new Contours(), new Contours() };
        }
    };


    @Override
    public void drawString(String str, int x, int y) {
        if(str==null)throw new NullPointerException("text");
        if(str.isEmpty())return;
        if(generalRendering() || font.isTransformed() || font.getTracking() != 0){
            drawGeneralText(str,x,y);
            return;
        }
        syncTarget();
        int baseX, baseY;
        if (deviceScale == 1f) {
            baseX = x + tx(); baseY = y + ty();
        } else {
            baseX = devX(x); baseY = devY(y);
            // Rows scrolled out of a viewport still get painted; skip them before the
            // cache lookup so off-screen labels don't churn the (now larger) glyph runs.
            float size = BufferedImageFontMetrics.pxSize(font) * deviceScale;
            if (baseY + size < clip.y || baseY - 2 * size > clip.y + clip.height) return;
        }
        TextRender r = TextCache.get(str, font, foreground.getRGB(), textAaMode(), deviceScale);
        int dx = baseX + r.boundsLeft - TextCache.PADDING;
        int dy = baseY + r.boundsTop - TextCache.PADDING;
        // Honor the clip — text in a scrolled JViewport otherwise paints
        // outside the viewport bounds (same bug pattern as blit()). 1:1 scale.
        blitDevice(r.pixels, r.w, r.h, 0, 0, r.w, r.h, dx, dy, dx + r.w, dy + r.h,
            compositeRule(), compositeAlpha());
        if (font.isUnderlined() || font.isStrikethrough()) drawTextDecorations(str, baseX, baseY);
    }

    /**
     * TextAttribute.UNDERLINE / STRIKETHROUGH, drawn as rects over the string's advance:
     * the underline about a twelfth of the size below the baseline, the strikethrough a
     * third of the ascent above it, both size/12 thick (1px at UI sizes) — close to what
     * the JDK derives from the font's post/OS-2 tables. Quest-helper's hover links
     * (JTextArea / JButton text) reach this through drawString.
     */
    private void drawTextDecorations(String str, int baseX, int baseY) {
        BufferedImageFontMetrics fm = BufferedImageFontMetrics.forFont(font);
        float s = deviceScale;
        int w = Math.round(fm.stringWidth(str) * s);
        float size = BufferedImageFontMetrics.pxSize(font) * s;
        int thick = Math.max(1, Math.round(size / 12f));
        int argb = foreground.getRGB(), rule = compositeRule();
        if (font.isUnderlined()) {
            int y = baseY + Math.max(1, Math.round(size / 12f));
            fillDevice(clipped(baseX, y, w, thick), argb, rule);
        }
        if (font.isStrikethrough()) {
            int y = baseY - Math.max(1, Math.round(fm.getAscent() * s / 3f)) - thick / 2;
            fillDevice(clipped(baseX, y, w, thick), argb, rule);
        }
    }

    /**
     * Resolves the active text-AA mode from our RenderingHints map. Maps the JDK constants
     * onto a small enum so the TextCache key can include it (cached bitmaps depend on AA
     * mode + subpixel; without this in the key, switching hints leaks stale bitmaps).
     * Falls back to KEY_ANTIALIASING when the text-specific hint is left at DEFAULT.
     */
    private TextAa textAaMode() {
        Object v = renderingHints.get(RenderingHints.KEY_TEXT_ANTIALIASING);
        if (v == null || v == RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT) {
            // Fall back to the general antialiasing hint.
            Object g = renderingHints.get(RenderingHints.KEY_ANTIALIASING);
            if (g == RenderingHints.VALUE_ANTIALIAS_OFF) return TextAa.OFF;
            // Default to ON — RuneLite + FlatLaf both expect AA text by default.
            return TextAa.ON;
        }
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF) return TextAa.OFF;
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_GASP) return TextAa.ON;
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR) return TextAa.LCD;
        return TextAa.ON;
    }

    enum TextAa { OFF, ON, LCD }

    /**
     * Cached prerendered glyph runs. drawString used to allocate a bitmap + int[] per call
     * and JNI-copy pixels through Bitmap.getPixels every label paint; profile showed >30%
     * of GameViewport time there. With a per-(text,font,argb) cache + LRU eviction we
     * pay the cost once per string and blit cached pixels forever after.
     */
    private static final class TextRender {
        final int[] pixels;
        final int w, h;
        final int boundsLeft, boundsTop;
        TextRender(int[] pixels, int w, int h, int boundsLeft, int boundsTop) {
            this.pixels = pixels; this.w = w; this.h = h;
            this.boundsLeft = boundsLeft; this.boundsTop = boundsTop;
        }
    }
    private static final class TextCache {
        static final int PADDING = 2;
        private static final int MAX = 1024;
        /**
         * Byte budget for the cached glyph pixels, expressed in ints. The entry count
         * alone doesn't bound anything useful: entries are int[w*h] and a single wrapped
         * tooltip line at 16px is easily 600x20 = 48 KB, so 1024 of those is ~49 MB held
         * for the life of the process on a phone. Cap the pixels instead and evict LRU
         * until back under budget.
         */
        private static final int MAX_PIXELS = 2 * 1024 * 1024; // 8 MB
        /**
         * A render bigger than this never enters the cache at all. One oversized string
         * would otherwise evict most of the small labels that the cache actually exists
         * for, and it is the entry least likely to be asked for twice.
         */
        private static final int MAX_ENTRY_PIXELS = 96 * 1024; // 384 KB
        private static int cachedPixels;
        /**
         * Composite key for (string, font, color). Earlier we packed the three hashCodes
         * into a single long with XOR, but the bit ranges overlapped — different (str,
         * font, argb) tuples collided to the same key and the cache returned a stale
         * bitmap, painting "Account" pixels for "Agility" etc. (visible as "shadowed"
         * or corrupted text in the plugin list panel).
         */
        private static final class Key {
            String str;
            Font font;
            int argb;
            TextAa aa;
            float scale;
            int hash;
            Key() {}
            Key(String s, Font f, int a, TextAa aa, float scale) {
                set(s, f, a, aa, scale);
            }
            void set(String s, Font f, int a, TextAa aa, float scale) {
                this.str = s; this.font = f; this.argb = a; this.aa = aa; this.scale = scale;
                int h = s != null ? s.hashCode() : 0;
                h = 31 * h + (f != null ? f.hashCode() : 0);
                h = 31 * h + a;
                h = 31 * h + (aa != null ? aa.ordinal() : 0);
                h = 31 * h + Float.floatToIntBits(scale);
                this.hash = h;
            }
            void clear() { this.str = null; this.font = null; }
            @Override public int hashCode() { return hash; }
            @Override public boolean equals(Object o) {
                if (!(o instanceof Key)) return false;
                Key k = (Key) o;
                return argb == k.argb && aa == k.aa && scale == k.scale
                    && (font == k.font || (font != null && font.equals(k.font)))
                    && (str == k.str || (str != null && str.equals(k.str)));
            }
        }
        private static final ThreadLocal<Key> LOOKUP_KEY = new ThreadLocal<Key>() {
            @Override protected Key initialValue() { return new Key(); }
        };
        // access-order, so the iterator hands back least-recently-used first when we trim.
        private static final java.util.LinkedHashMap<Key, TextRender> cache =
            new java.util.LinkedHashMap<Key, TextRender>(256, 0.75f, true);

        static synchronized TextRender get(String str, Font font, int argb, TextAa aa, float scale) {
            Key lookup = LOOKUP_KEY.get();
            lookup.set(str, font, argb, aa, scale);
            TextRender r = cache.get(lookup);
            // Don't let the thread-local probe key pin the last string + font drawn on
            // this thread until the next drawString.
            lookup.clear();
            if (r != null) return r;

            r = render(str, font, argb, aa, scale);
            if (r.pixels.length > MAX_ENTRY_PIXELS) return r;

            cache.put(new Key(str, font, argb, aa, scale), r);
            cachedPixels += r.pixels.length;
            if (cache.size() > MAX || cachedPixels > MAX_PIXELS) {
                java.util.Iterator<java.util.Map.Entry<Key, TextRender>> it =
                    cache.entrySet().iterator();
                while (it.hasNext() && (cache.size() > MAX || cachedPixels > MAX_PIXELS)) {
                    cachedPixels -= it.next().getValue().pixels.length;
                    it.remove();
                }
            }
            return r;
        }
        private static TextRender render(String str, Font font, int argb, TextAa aa, float scale) {
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColor(argb);
            applyAa(p, aa);
            // Snap glyphs to the pixel grid. Without this Android's grayscale AA leaves
            // even glyph-interior pixels with alpha < 255 at small sizes — body pixels
            // then look "see-through" because src_over blends bg through them.
            p.setHinting(android.graphics.Paint.HINTING_ON);
            float textSize = BufferedImageFontMetrics.pxSize(font) * scale;
            p.setTextSize(textSize);
            p.setTypeface(BufferedImageFontMetrics.typefaceFor(font));
            android.graphics.Rect bounds = new android.graphics.Rect();
            p.getTextBounds(str, 0, str.length(), bounds);
            int textW = Math.max(1, bounds.width() + 2 * PADDING);
            int textH = Math.max(1, bounds.height() + 2 * PADDING);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                textW, textH, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            canvas.drawText(str, PADDING - bounds.left, PADDING - bounds.top, p);
            int[] pixels = new int[textW * textH];
            bmp.getPixels(pixels, 0, textW, 0, 0, textW, textH);
            bmp.recycle();
            // Alpha contrast boost. Android's Paint at small sizes (12px) doesn't give
            // glyph interiors alpha=255 even with HINTING_ON — it produces ~180–230
            // per the glyph's coverage rounding. Our src_over then renders the body as
            // `text·α + dst·(1-α)`, leaking the bg through "opaque" text (the red-bar
            // /white-text "see-through" symptom). Saturate any near-opaque pixel to
            // 255 while preserving the AA gradient on edges so the silhouette stays
            // smooth and blends cleanly with the destination. Device-scaled runs are large
            // enough that interiors are already opaque; boosting them would only harden
            // the AA edges we rendered at full resolution to get.
            if (scale == 1f || textSize < 20f) for (int i = 0; i < pixels.length; i++) {
                int px = pixels[i];
                int a = (px >>> 24) & 0xFF;
                if (a == 0 || a == 255) continue;
                int boosted = a + (a >> 1); // ×1.5
                if (boosted > 255) boosted = 255;
                pixels[i] = (boosted << 24) | (px & 0x00FFFFFF);
            }
            return new TextRender(pixels, textW, textH, bounds.left, bounds.top);
        }
    }

    /** Builds an Android Paint matching our current foreground + font. Not cached — fonts
     * are immutable on the AWT side so recreation is fine for occasional text. */
    private android.graphics.Paint androidPaint() {
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(foreground.getRGB());
        applyAa(p, textAaMode());
        p.setTextSize(BufferedImageFontMetrics.pxSize(font));
        p.setTypeface(BufferedImageFontMetrics.typefaceFor(font));
        return p;
    }

    private static void applyAa(android.graphics.Paint p, TextAa aa) {
        switch (aa) {
            case OFF:
                p.setAntiAlias(false);
                p.setSubpixelText(false);
                break;
            case LCD:
                p.setAntiAlias(true);
                p.setSubpixelText(true);
                break;
            default:
                p.setAntiAlias(true);
                p.setSubpixelText(false);
                break;
        }
    }

    @Override public void drawString(String str, float x, float y) {
        if(x==(int)x && y==(int)y)drawString(str,(int)x,(int)y);
        else drawGeneralText(str,x,y);
    }
    private void drawGeneralText(String str,float x,float y){
        if(str==null)throw new NullPointerException("text");
        if(str.isEmpty())return;
        android.graphics.Paint metrics = new android.graphics.Paint();
        configureText(metrics);
        android.graphics.Rect ink = new android.graphics.Rect();
        metrics.getTextBounds(str, 0, str.length(), ink);
        android.graphics.Paint.FontMetrics fm = metrics.getFontMetrics();
        // Include glyph overhangs, spacing and underline/strike-through before transforming.
        java.awt.geom.Rectangle2D bounds = new java.awt.geom.Rectangle2D.Double(
            Math.min(0, ink.left) - 2, Math.min(fm.top, ink.top) - 2,
            Math.max(metrics.measureText(str), ink.right) - Math.min(0, ink.left) + 4,
            Math.max(fm.bottom, ink.bottom) - Math.min(fm.top, ink.top) + 4);
        AffineTransform local = AffineTransform.getTranslateInstance(x, y);
        local.concatenate(font.getTransform());
        renderGeneral((canvas,p,mask)->{
            canvas.save();canvas.translate(x,y);
            AffineTransform ft=font.getTransform();android.graphics.Matrix matrix=new android.graphics.Matrix();
            matrix.setValues(new float[]{(float)ft.getScaleX(),(float)ft.getShearX(),(float)ft.getTranslateX(),(float)ft.getShearY(),(float)ft.getScaleY(),(float)ft.getTranslateY(),0,0,1});
            canvas.concat(matrix);canvas.drawText(str,0,0,p);canvas.restore();
        },true,local.createTransformedShape(bounds),true);
    }
    @Override public void drawString(AttributedCharacterIterator it,int x,int y){drawString(it,(float)x,(float)y);}
    @Override public void drawString(AttributedCharacterIterator it,float x,float y){
        java.util.Objects.requireNonNull(it);Font savedFont=font;Paint savedPaint=paint;Color savedColor=foreground;float cursor=x;
        try{
            int end=it.getEndIndex();
            for(int start=it.getBeginIndex();start<end;){
                it.setIndex(start);int limit=it.getRunLimit();java.util.Map<AttributedCharacterIterator.Attribute,Object> attrs=it.getAttributes();
                Object specified=attrs.get(java.awt.font.TextAttribute.FONT);font=specified instanceof Font?(Font)specified:savedFont.deriveFont(attrs);
                Object fg=attrs.get(java.awt.font.TextAttribute.FOREGROUND);setPaint(fg instanceof Paint?(Paint)fg:savedPaint);
                StringBuilder text=new StringBuilder();for(int i=start;i<limit;i++){it.setIndex(i);text.append(it.current());}
                String part=text.toString();float advance=BufferedImageFontMetrics.forFont(font).stringWidth(part);
                Object bg=attrs.get(java.awt.font.TextAttribute.BACKGROUND);if(bg instanceof Paint){Paint runPaint=paint;setPaint((Paint)bg);fillRect((int)cursor,(int)(y-getFontMetrics().getAscent()),(int)Math.ceil(advance),getFontMetrics().getHeight());setPaint(runPaint);}
                drawString(part,cursor,y);cursor+=advance;start=limit;
            }
        }finally{font=savedFont;paint=savedPaint;foreground=savedColor;}
    }

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, ImageObserver obs) {
        img=buffered(img);
        if(img==null)return false;
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, 0, 0, bi.getWidth(), bi.getHeight(), x, y, bi.getWidth(), bi.getHeight());
        }
        return true;
    }

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, int w, int h, ImageObserver obs) {
        img=buffered(img);
        if(img==null)return false;
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, 0, 0, bi.getWidth(), bi.getHeight(), x, y, w, h);
        }
        return true;
    }

    @Override public boolean drawImage(java.awt.Image img, int x, int y, Color bg, ImageObserver obs) { return drawImageBackground(img,x,y,-1,-1,bg); }
    @Override public boolean drawImage(java.awt.Image img, int x, int y, int w, int h, Color bg, ImageObserver obs) { return drawImageBackground(img,x,y,w,h,bg); }

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                              int sx1, int sy1, int sx2, int sy2, ImageObserver obs) {
        img=buffered(img);
        if(img==null)return false;
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, sx1, sy1, sx2 - sx1, sy2 - sy1, dx1, dy1, dx2 - dx1, dy2 - dy1);
        }
        return true;
    }

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                              int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver obs) {
        BufferedImage source=buffered(img);if(source==null)return false;
        if(sx1==sx2||sy1==sy2)return true;AffineTransform at=AffineTransform.getTranslateInstance(dx1,dy1);at.scale((double)(dx2-dx1)/(sx2-sx1),(double)(dy2-dy1)/(sy2-sy1));at.translate(-sx1,-sy1);
        generalImage(source,sx1,sy1,sx2-sx1,sy2-sy1,at,bg);return true;
    }

    @Override
    public boolean drawImage(java.awt.Image img, AffineTransform xform, ImageObserver obs) {
        BufferedImage source=buffered(img);if(source==null)return false;
        generalImage(source,0,0,source.getWidth(),source.getHeight(),new AffineTransform(xform),null);return true;
    }

    @Override
    public void drawImage(BufferedImage img, int x, int y) {
        blit(img, 0, 0, img.getWidth(), img.getHeight(), x, y, img.getWidth(), img.getHeight());
    }

    private void blit(BufferedImage src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
        if(generalRendering()||!src.isCanonicalBuffer()||sw<0||sh<0||dw<0||dh<0){
            if(sw==0||sh==0||dw==0||dh==0)return;
            AffineTransform at=AffineTransform.getTranslateInstance(dx,dy);at.scale((double)dw/sw,(double)dh/sh);at.translate(-sx,-sy);
            generalImage(src,sx,sy,sw,sh,at,null);return;
        }
        int[] srcPixels = src.backingArray();
        if (srcPixels == null) return;
        syncTarget();

        // Honor the active clip. Without this, components in a JViewport that
        // ever blit an image (icon, sub-buffer, glyph cache hit) overflow the
        // viewport bounds when scrolled — content visually "drags" over the
        // parent UI even though fillRect/drawLine clip correctly. We clip the
        // destination rect to the current clip, then shift the source rect
        // proportionally so the same source pixels map to the same world
        // positions.
        int adx, ady, adx2, ady2;
        if (deviceScale == 1f) {
            adx = dx + tx(); ady = dy + ty(); adx2 = adx + dw; ady2 = ady + dh;
        } else {
            adx = devX(dx); ady = devY(dy);
            adx2 = devX(dx + (double) dw); ady2 = devY(dy + (double) dh);
            dw = adx2 - adx; dh = ady2 - ady;
            if (dw <= 0 || dh <= 0) return;
        }
        int cx0 = clip.x, cy0 = clip.y;
        int cx1 = clip.x + clip.width, cy1 = clip.y + clip.height;
        int ndx = Math.max(adx, cx0);
        int ndy = Math.max(ady, cy0);
        int ndx2 = Math.min(adx2, cx1);
        int ndy2 = Math.min(ady2, cy1);
        int ndw = ndx2 - ndx;
        int ndh = ndy2 - ndy;
        if (ndw <= 0 || ndh <= 0) return;

        if (deviceScale != 1f && (dw != sw || dh != sh)) {
            blitFiltered(src, srcPixels, sx, sy, sw, sh, adx, ady, dw, dh, ndx, ndy, ndw, ndh);
            return;
        }

        int ruleDiag = compositeRuleFor(src);

        // TEMP DIAGNOSTIC: software-mode present into a Canvas backbuffer. Logs whether the
        // source image (the engine's rasterized frame) actually has content and what
        // composite rule we use — so we can tell "engine rendered nothing" from "blit dropped it".
        if (backbufferSource != null && !java.awt.Canvas.isRenderedByGles())
        {
            long now = System.currentTimeMillis();
            if (now - lastSwBlitLog > 1000)
            {
                lastSwBlitLog = now;
                int sw2 = src.getWidth(), sh2 = src.getHeight();
                int ci = (sh2 / 2) * sw2 + sw2 / 2;
                int srcCenter = (ci >= 0 && ci < srcPixels.length) ? srcPixels[ci] : -1;
                java.awt.image.ColorModel cm = src.getColorModel();
                android.util.Log.i("SwDiag", "PRESENT src=" + sw2 + "x" + sh2
                    + " srcCenter=0x" + Integer.toHexString(srcCenter)
                    + " srcType=" + src.getType() + " hasAlpha=" + (cm != null && cm.hasAlpha())
                    + " opaqueSrc=" + isOpaqueSource(src) + " rule=" + ruleDiag + "(SRC=" + AlphaComposite.SRC + ")"
                    + " dst=" + width + "x" + height + " dstRect=[" + ndx + "," + ndy + " " + ndw + "x" + ndh + "]");
            }
        }

        blitDevice(srcPixels, src.getWidth(), src.getHeight(), sx, sy, sw, sh,
            adx, ady, adx2, ady2, ruleDiag, compositeAlpha());
    }

    private static long lastSwBlitLog = 0;

    private static final ThreadLocal<int[]> FILTER_SCRATCH = new ThreadLocal<>();

    /**
     * Bilinear resample for device-scaled hosts. The native blit is nearest-neighbour, which
     * at 2–3x turns every 16px icon into visible blocks next to text rasterized at full
     * density. Interpolates premultiplied so transparent edge texels (usually 0x00000000)
     * don't bleed a dark fringe, then blits the visible part 1:1 with the usual composite.
     * Only the clipped destination region is computed.
     */
    private void blitFiltered(BufferedImage src, int[] srcPixels, int sx, int sy, int sw, int sh,
                              int adx, int ady, int dw, int dh, int ndx, int ndy, int ndw, int ndh) {
        int srcW = src.getWidth(), srcH = src.getHeight();
        int minX = Math.max(0, sx), maxX = Math.min(srcW, sx + sw) - 1;
        int minY = Math.max(0, sy), maxY = Math.min(srcH, sy + sh) - 1;
        if (maxX < minX || maxY < minY) return;
        boolean opaque = isOpaqueSource(src);

        int n = ndw * ndh;
        boolean pooled = n <= MAX_SCRATCH_PIXELS;
        int[] out = pooled ? FILTER_SCRATCH.get() : null;
        if (out == null || out.length < n) {
            out = new int[n];
            if (pooled) FILTER_SCRATCH.set(out);
        }

        double stepX = (double) sw / dw, stepY = (double) sh / dh;
        int o = 0;
        for (int j = 0; j < ndh; j++) {
            double fy = sy + (ndy - ady + j + 0.5) * stepY - 0.5;
            if (fy < minY) fy = minY;
            if (fy > maxY) fy = maxY;
            int y0 = (int) fy;
            int y1 = Math.min(y0 + 1, maxY);
            int wy = (int) ((fy - y0) * 256);
            int row0 = y0 * srcW, row1 = y1 * srcW;
            for (int i = 0; i < ndw; i++) {
                double fx = sx + (ndx - adx + i + 0.5) * stepX - 0.5;
                if (fx < minX) fx = minX;
                if (fx > maxX) fx = maxX;
                int x0 = (int) fx;
                int x1 = Math.min(x0 + 1, maxX);
                int wx = (int) ((fx - x0) * 256);
                int w00 = (256 - wx) * (256 - wy), w10 = wx * (256 - wy);
                int w01 = (256 - wx) * wy, w11 = wx * wy;
                int p00 = srcPixels[row0 + x0], p10 = srcPixels[row0 + x1];
                int p01 = srcPixels[row1 + x0], p11 = srcPixels[row1 + x1];
                int a00 = opaque ? 255 : p00 >>> 24, a10 = opaque ? 255 : p10 >>> 24;
                int a01 = opaque ? 255 : p01 >>> 24, a11 = opaque ? 255 : p11 >>> 24;
                // Weights sum to 65536; alpha-weighted so colour is effectively premultiplied.
                long ka00 = (long) w00 * a00, ka10 = (long) w10 * a10;
                long ka01 = (long) w01 * a01, ka11 = (long) w11 * a11;
                long asum = ka00 + ka10 + ka01 + ka11;
                if (asum == 0) { out[o++] = 0; continue; }
                long r = ka00 * ((p00 >> 16) & 0xFF) + ka10 * ((p10 >> 16) & 0xFF)
                    + ka01 * ((p01 >> 16) & 0xFF) + ka11 * ((p11 >> 16) & 0xFF);
                long g = ka00 * ((p00 >> 8) & 0xFF) + ka10 * ((p10 >> 8) & 0xFF)
                    + ka01 * ((p01 >> 8) & 0xFF) + ka11 * ((p11 >> 8) & 0xFF);
                long b = ka00 * (p00 & 0xFF) + ka10 * (p10 & 0xFF)
                    + ka01 * (p01 & 0xFF) + ka11 * (p11 & 0xFF);
                int a = (int) ((asum + 32768) >> 16);
                out[o++] = (a << 24) | ((int) (r / asum) << 16) | ((int) (g / asum) << 8) | (int) (b / asum);
            }
        }
        // The resampled run carries real alpha (opaque sources come out 0xFF), so the
        // opaque-source SRC promotion isn't needed; a SRC_OVER of an opaque pixel is a copy.
        blitDevice(out, ndw, ndh, 0, 0, ndw, ndh, ndx, ndy, ndx + ndw, ndy + ndh,
            compositeRule(), compositeAlpha());
    }

    /** The native blitter must normalize an opaque source before applying global alpha. */
    private int compositeRuleFor(BufferedImage src) {
        int rule = compositeRule();
        boolean opaque = isOpaqueSource(src)
            || (rule == AlphaComposite.SRC_OVER && backbufferSource != null && !java.awt.Canvas.isRenderedByGles());
        if (opaque && rule == AlphaComposite.SRC_OVER && compositeAlpha() == 1f) rule = AlphaComposite.SRC;
        return opaque ? rule | 0x10000 : rule;
    }

    private static boolean isOpaqueSource(BufferedImage src) {
        if (isOpaqueType(src.getType())) return true;
        java.awt.image.ColorModel cm = src.getColorModel();
        return cm != null && !cm.hasAlpha();
    }

    private static boolean isOpaqueType(int t) {
        return t == BufferedImage.TYPE_INT_RGB
            || t == BufferedImage.TYPE_INT_BGR
            || t == BufferedImage.TYPE_USHORT_565_RGB
            || t == BufferedImage.TYPE_USHORT_555_RGB
            || t == BufferedImage.TYPE_3BYTE_BGR
            || t == BufferedImage.TYPE_BYTE_GRAY
            || t == BufferedImage.TYPE_USHORT_GRAY;
    }

    private int compositeRule() {
        if (composite instanceof AlphaComposite) return ((AlphaComposite) composite).getRule();
        return AlphaComposite.SRC_OVER;
    }

    private float compositeAlpha() {
        if (composite instanceof AlphaComposite) return ((AlphaComposite) composite).getAlpha();
        return 1f;
    }

    @Override public void draw(Shape s) {
        if(s!=null&&generalRendering()){generalShape(s,false);return;}
        if (s == null) return;
        // Polygon / Rectangle honour the stroke inside drawPolygon / drawRect (thin fast
        // path, or the stroker for width > 1 — OverlayUtil.renderPolygon draws tile
        // outlines with BasicStroke(2)).
        if (s instanceof Polygon) {
            Polygon p = (Polygon) s;
            drawPolygon(p.xpoints, p.ypoints, p.npoints);
            return;
        }
        if (s instanceof Rectangle) {
            Rectangle r = (Rectangle) s;
            drawRect(r.x, r.y, r.width, r.height);
            return;
        }
        boolean solidBasic = stroke instanceof BasicStroke && ((BasicStroke) stroke).getDashArray() == null;
        if (solidBasic && s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            drawOval((int) e.getX(), (int) e.getY(), (int) e.getWidth(), (int) e.getHeight());
            return;
        }
        if (solidBasic && s instanceof java.awt.geom.Arc2D) {
            java.awt.geom.Arc2D a = (java.awt.geom.Arc2D) s;
            drawArc((int) a.getX(), (int) a.getY(), (int) a.getWidth(), (int) a.getHeight(),
                (int) Math.round(a.getAngleStart()), (int) Math.round(a.getAngleExtent()));
            return;
        }
        syncTarget();
        strokeShape(s);
    }

    @Override public void fill(Shape s) {
        if(s!=null&&generalRendering()){generalShape(s,true);return;}
        if (s == null) return;
        if (s instanceof Polygon) {
            Polygon p = (Polygon) s;
            fillPolygon(p.xpoints, p.ypoints, p.npoints);
            return;
        }
        if (s instanceof Rectangle) {
            Rectangle r = (Rectangle) s;
            fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        if (s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            fillOval((int) e.getX(), (int) e.getY(), (int) e.getWidth(), (int) e.getHeight());
            return;
        }
        if (s instanceof java.awt.geom.Arc2D) {
            java.awt.geom.Arc2D a = (java.awt.geom.Arc2D) s;
            fillArc((int) a.getX(), (int) a.getY(), (int) a.getWidth(), (int) a.getHeight(),
                (int) Math.round(a.getAngleStart()), (int) Math.round(a.getAngleExtent()));
            return;
        }
        // Everything else (paths with holes, Areas, Rectangle2D.Double, ...) is filled in
        // one pass under its own winding rule. Moveto/lineto pairs (NPC unaggro area
        // boundary lines) are open two-point subpaths here: nothing to fill, but draw()
        // keeps them as segments.
        fillShape(s);
    }

    @Override public Composite getComposite() { return composite; }
    @Override public void setComposite(Composite comp) { if (comp != null) composite = comp; }

    @Override public Paint getPaint() { return paint; }
    @Override public void setPaint(Paint paint) {
        if (paint != null) {
            this.paint = paint;
            if (paint instanceof Color) this.foreground = (Color) paint;
        }
    }

    @Override public Stroke getStroke() { return stroke; }
    @Override public void setStroke(Stroke s) { if (s != null) stroke = s; }

    @Override public AffineTransform getTransform() { return new AffineTransform(transform); }
    @Override public void setTransform(AffineTransform tx) { if (tx != null) transform.setTransform(tx); }
    @Override public void transform(AffineTransform tx) {
        transform.concatenate(tx);
    }
    @Override public void rotate(double theta) {transform.rotate(theta);}
    @Override public void rotate(double theta,double x,double y) {transform.rotate(theta,x,y);}
    @Override public void scale(double sx, double sy) { transform.scale(sx, sy); }
    @Override public void shear(double shx,double shy) {transform.shear(shx,shy);}

    @Override public void setRenderingHint(RenderingHints.Key key, Object val) { renderingHints.put(key, val); }
    @Override public Object getRenderingHint(RenderingHints.Key key) { return renderingHints.get(key); }
    @Override public void setRenderingHints(java.util.Map<?, ?> hints) { renderingHints.clear(); renderingHints.putAll(hints); }
    @Override public void addRenderingHints(java.util.Map<?, ?> hints) { renderingHints.putAll(hints); }
    @Override public RenderingHints getRenderingHints() { return (RenderingHints) renderingHints.clone(); }

    @Override public FontRenderContext getFontRenderContext() { return new FontRenderContext(transform, renderingHints.get(RenderingHints.KEY_TEXT_ANTIALIASING)==null?RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT:renderingHints.get(RenderingHints.KEY_TEXT_ANTIALIASING), renderingHints.get(RenderingHints.KEY_FRACTIONALMETRICS)==null?RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT:renderingHints.get(RenderingHints.KEY_FRACTIONALMETRICS)); }

    private boolean generalRendering(){
        int rule = compositeRule();
        boolean nativeRule = rule == AlphaComposite.SRC_OVER || rule == AlphaComposite.SRC || rule == AlphaComposite.CLEAR || rule == AlphaComposite.SRC_ATOP;
        return !nativeRule||!image.isCanonicalBuffer()||!transformIsTranslateOnly()||xorColor!=null||!(composite instanceof AlphaComposite)||(!(paint instanceof Color)&&!(paint instanceof TexturePaint));
    }
    private static BufferedImage buffered(java.awt.Image source){
        if(source instanceof BufferedImage)return (BufferedImage)source;
        if(source instanceof java.awt.image.VolatileImage)return ((java.awt.image.VolatileImage)source).getSnapshot();
        if(source instanceof java.awt.image.ProducerImage)return ((java.awt.image.ProducerImage)source).getBufferedImage();
        return source==null?null:new java.awt.image.ProducerImage(source.getSource()).getBufferedImage();
    }
    private static Shape polyline(int[]x,int[]y,int n,boolean closed){
        java.awt.geom.Path2D.Double path=new java.awt.geom.Path2D.Double();
        if(n>0){path.moveTo(x[0],y[0]);for(int i=1;i<n;i++)path.lineTo(x[i],y[i]);if(closed)path.closePath();}return path;
    }
    private static android.graphics.Path androidPath(Shape shape){
        android.graphics.Path path=new android.graphics.Path();java.awt.geom.PathIterator it=shape.getPathIterator(null);
        path.setFillType(it.getWindingRule()==java.awt.geom.PathIterator.WIND_EVEN_ODD?android.graphics.Path.FillType.EVEN_ODD:android.graphics.Path.FillType.WINDING);
        float[]c=new float[6];while(!it.isDone()){
            switch(it.currentSegment(c)){
                case 0:path.moveTo(c[0],c[1]);break;case 1:path.lineTo(c[0],c[1]);break;
                case 2:path.quadTo(c[0],c[1],c[2],c[3]);break;case 3:path.cubicTo(c[0],c[1],c[2],c[3],c[4],c[5]);break;case 4:path.close();break;
            }it.next();
        }return path;
    }
    private android.graphics.Matrix androidMatrix(AffineTransform at){
        android.graphics.Matrix matrix=new android.graphics.Matrix();
        matrix.setValues(new float[]{(float)(at.getScaleX()*deviceScale),(float)(at.getShearX()*deviceScale),(float)(at.getTranslateX()*deviceScale),(float)(at.getShearY()*deviceScale),(float)(at.getScaleY()*deviceScale),(float)(at.getTranslateY()*deviceScale),0,0,1});
        return matrix;
    }
    private interface CanvasOperation{void draw(android.graphics.Canvas canvas,android.graphics.Paint paint,boolean mask);}
    private void generalShape(Shape shape,boolean filled){
        Shape outline=!filled?stroke.createStrokedShape(shape):shape;android.graphics.Path path=androidPath(outline);
        renderGeneral((canvas,p,mask)->canvas.drawPath(path,p),false,outline,true);
    }
    private boolean drawImageBackground(java.awt.Image img,int x,int y,int w,int h,Color background){
        BufferedImage src=buffered(img);if(src==null)return false;
        if(w<0)w=src.getWidth();if(h<0)h=src.getHeight();
        AffineTransform local=AffineTransform.getTranslateInstance(x,y);local.scale((double)w/src.getWidth(),(double)h/src.getHeight());
        generalImage(src,0,0,src.getWidth(),src.getHeight(),local,background);return true;
    }
    private void generalImage(BufferedImage src,int x,int y,int w,int h,AffineTransform local,Color background){
        if(w==0||h==0)return;
        Shape destinationBounds = local.createTransformedShape(new java.awt.geom.Rectangle2D.Double(
            Math.min(x,x+w),Math.min(y,y+h),Math.abs((double)w),Math.abs((double)h)));
        syncTarget();
        if(generalBounds(destinationBounds).isEmpty())return;
        Rectangle source = new Rectangle(Math.min(x,x+w),Math.min(y,y+h),Math.abs(w),Math.abs(h));
        // Filtering at a cropped edge still needs its neighbouring source texel.
        if(renderingHints.get(RenderingHints.KEY_INTERPOLATION)!=RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            source.grow(1,1);
        source = source.intersection(new Rectangle(0,0,src.getWidth(),src.getHeight()));
        if(source.isEmpty())return;
        final Rectangle sourceRegion = source;
        GeneralScratch cached = IMAGE_SCRATCH.get();
        boolean retained = !cached.busy && (long)source.width * source.height <= MAX_RETAINED_PIXELS;
        GeneralScratch scratch = retained ? cached : new GeneralScratch();
        scratch.busy = true;
        try {
            scratch.prepare(source.width,source.height);
            int[] sourcePixels = src.getRGB(source.x, source.y, source.width, source.height, scratch.destination, 0, source.width);
            if (background != null) {
                int bg = background.getRGB();
                for (int i = 0; i < source.width * source.height; i++) {
                    int pixel = sourcePixels[i], alpha = pixel >>> 24, ba = bg >>> 24;
                    int outAlpha = alpha + (ba * (255 - alpha) + 127) / 255;
                    int composed = outAlpha << 24;
                    for (int shift = 0; shift < 24; shift += 8) {
                        int component = ((pixel >>> shift) & 255) * alpha + (((bg >>> shift) & 255) * ba * (255 - alpha) + 127) / 255;
                        if (outAlpha != 0) component = (component + outAlpha / 2) / outAlpha;
                        composed |= component << shift;
                    }
                    sourcePixels[i] = composed;
                }
            }
            android.graphics.Bitmap bitmap=scratch.bitmap;
            bitmap.setPixels(sourcePixels,0,source.width,0,0,source.width,source.height);
            renderGeneral((canvas,p,mask)->{
                android.graphics.Matrix m=new android.graphics.Matrix();m.setValues(new float[]{(float)local.getScaleX(),(float)local.getShearX(),(float)local.getTranslateX(),(float)local.getShearY(),(float)local.getScaleY(),(float)local.getTranslateY(),0,0,1});
                canvas.save();canvas.concat(m);canvas.clipRect(Math.min(x,x+w),Math.min(y,y+h),Math.max(x,x+w),Math.max(y,y+h));
                if(mask)canvas.drawRect(Math.min(x,x+w),Math.min(y,y+h),Math.max(x,x+w),Math.max(y,y+h),p);
                else{
                    p.setShader(null);p.setColor(android.graphics.Color.WHITE);
                    canvas.drawBitmap(bitmap,sourceRegion.x,sourceRegion.y,p);
                }
                canvas.restore();
            },false,destinationBounds,false);
        }finally{scratch.busy=false;if(!retained)scratch.recycle();}
    }
    /** Scratch storage is shared across short-lived Graphics copies, bounded per drawing thread.
     * A custom Paint/Composite can reenter drawing; nested calls must lease separate storage. */
    private static final int MAX_RETAINED_PIXELS = 256 * 1024;
    private static final ThreadLocal<GeneralScratch> GENERAL_SCRATCH = ThreadLocal.withInitial(GeneralScratch::new);
    private static final ThreadLocal<GeneralScratch> IMAGE_SCRATCH = ThreadLocal.withInitial(GeneralScratch::new);
    private static final class GeneralScratch {
        boolean busy;
        int[] destination = new int[0], result = new int[0], auxiliary = new int[0];
        android.graphics.Bitmap bitmap, paintBitmap, maskBitmap;
        void preparePixels(int w, int h) {
            int count = Math.multiplyExact(w, h);
            if (destination.length < count) {
                destination = new int[count];
            }
        }
        void prepare(int w, int h) {
            preparePixels(w,h);
            bitmap = resize(bitmap, w, h);
        }
        static android.graphics.Bitmap resize(android.graphics.Bitmap bitmap, int w, int h) {
            if (bitmap == null || (long)bitmap.getAllocationByteCount() < (long)w * h * 4) {
                if (bitmap != null) bitmap.recycle();
                return android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            }
            if (bitmap.getWidth() != w || bitmap.getHeight() != h)
                bitmap.reconfigure(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            return bitmap;
        }
        void recycle() {
            if (bitmap != null) bitmap.recycle();
            if (paintBitmap != null) paintBitmap.recycle();
            if (maskBitmap != null) maskBitmap.recycle();
        }
    }

    private Rectangle generalBounds(Shape userBounds) {
        AffineTransform device = new AffineTransform(transform);
        device.preConcatenate(AffineTransform.getScaleInstance(deviceScale, deviceScale));
        java.awt.geom.Rectangle2D bounds = device.createTransformedShape(userBounds).getBounds2D();
        // Round outward, including antialias/filter coverage at the shape boundary.
        double x0 = Math.max(Math.max(0, clip.x), Math.floor(bounds.getMinX()) - 2);
        double y0 = Math.max(Math.max(0, clip.y), Math.floor(bounds.getMinY()) - 2);
        double x1 = Math.min(Math.min(width, clipX1()), Math.ceil(bounds.getMaxX()) + 2);
        double y1 = Math.min(Math.min(height, clipY1()), Math.ceil(bounds.getMaxY()) + 2);
        if (!(x1 > x0 && y1 > y0)) return new Rectangle();
        return new Rectangle((int)x0, (int)y0, (int)(x1-x0), (int)(y1-y0));
    }

    /** Only the affected device rectangle crosses the Bitmap/ARGB boundary. */
    private void renderGeneral(CanvasOperation operation,boolean text,Shape userBounds,boolean usePaint){
        syncTarget();
        Rectangle region = generalBounds(userBounds);
        int w=region.width,h=region.height;if(w<=0||h<=0)return;
        boolean special=xorColor!=null||!(composite instanceof AlphaComposite);
        GeneralScratch cached = GENERAL_SCRATCH.get();
        boolean retained = !cached.busy && (long)w * h <= MAX_RETAINED_PIXELS;
        GeneralScratch scratch = retained ? cached : new GeneralScratch();
        scratch.busy = true;
        try{
            scratch.prepare(w,h);
            int count=w*h;
            if(scratch.result.length<count)scratch.result=new int[count];
            if(scratch.auxiliary.length<count)scratch.auxiliary=new int[count];
            int[] destination=image.getRGB(region.x,region.y,w,h,scratch.destination,0,w);
            android.graphics.Bitmap bitmap=scratch.bitmap;
            if(!special)bitmap.setPixels(destination,0,w,0,0,w,h);
            else bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);configureCanvas(canvas,region);
            android.graphics.Paint p=new android.graphics.Paint();
            p.setAntiAlias(text?renderingHints.get(RenderingHints.KEY_TEXT_ANTIALIASING)!=RenderingHints.VALUE_TEXT_ANTIALIAS_OFF:renderingHints.get(RenderingHints.KEY_ANTIALIASING)==RenderingHints.VALUE_ANTIALIAS_ON);
            p.setFilterBitmap(renderingHints.get(RenderingHints.KEY_INTERPOLATION)!=RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if(text)configureText(p);
            if(!usePaint)p.setColor(android.graphics.Color.WHITE);
            else if(paint instanceof Color)p.setColor(((Color)paint).getRGB());
            else{
                AffineTransform device=new AffineTransform(transform);device.preConcatenate(AffineTransform.getScaleInstance(deviceScale,deviceScale));
                PaintContext context=paint.createContext(java.awt.image.ColorModel.getRGBdefault(),region,userBounds.getBounds2D(),device,renderingHints);
                try{
                    java.awt.image.Raster raster=context.getRaster(region.x,region.y,w,h);int[] colors=scratch.auxiliary;java.awt.image.ColorModel cm=context.getColorModel();
                    if (raster instanceof java.awt.image.WritableRaster && raster.getMinX() == 0 && raster.getMinY() == 0) {
                        BufferedImage sampled = new BufferedImage(cm, (java.awt.image.WritableRaster)raster, cm.isAlphaPremultiplied(), null);
                        sampled.getRGB(0, 0, w, h, colors, 0, w);
                    } else {
                        Object pixel = null;
                        for(int row=0;row<h;row++)for(int col=0;col<w;col++){pixel=raster.getDataElements(raster.getMinX()+col,raster.getMinY()+row,pixel);colors[row*w+col]=cm.getRGB(pixel);}
                    }
                    scratch.paintBitmap=GeneralScratch.resize(scratch.paintBitmap,w,h);
                    scratch.paintBitmap.setPixels(colors,0,w,0,0,w,h);
                    android.graphics.BitmapShader shader=new android.graphics.BitmapShader(scratch.paintBitmap,android.graphics.Shader.TileMode.CLAMP,android.graphics.Shader.TileMode.CLAMP);
                    android.graphics.Matrix inverse=new android.graphics.Matrix();canvas.getMatrix().invert(inverse);shader.setLocalMatrix(inverse);p.setShader(shader);
                }finally{context.dispose();}
            }
            if(!special){
                AlphaComposite alpha=(AlphaComposite)composite;p.setAlpha(Math.round(p.getAlpha()*alpha.getAlpha()));p.setXfermode(new android.graphics.PorterDuffXfermode(porterDuff(alpha.getRule())));
            }
            operation.draw(canvas,p,false);
            int[] result=scratch.result;bitmap.getPixels(result,0,w,0,0,w,h);
            if(special){
                scratch.maskBitmap=GeneralScratch.resize(scratch.maskBitmap,w,h);
                android.graphics.Bitmap maskBitmap=scratch.maskBitmap;maskBitmap.eraseColor(android.graphics.Color.TRANSPARENT);
                android.graphics.Canvas maskCanvas=new android.graphics.Canvas(maskBitmap);configureCanvas(maskCanvas,region);
                android.graphics.Paint maskPaint=new android.graphics.Paint(p);maskPaint.setShader(null);maskPaint.setColor(android.graphics.Color.WHITE);maskPaint.setXfermode(null);
                operation.draw(maskCanvas,maskPaint,true);int[] coverage=scratch.auxiliary;maskBitmap.getPixels(coverage,0,w,0,0,w,h);
                if(xorColor!=null){for(int i=0;i<count;i++)result[i]=(coverage[i]>>>24)==0?destination[i]:(destination[i]&0xff000000)|((destination[i]^result[i]^xorColor.getRGB())&0xffffff);}
                else{
                    BufferedImage src=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB),dst=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB),out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                    src.setRGB(0,0,w,h,result,0,w);dst.setRGB(0,0,w,h,destination,0,w);
                    java.awt.CompositeContext context=composite.createContext(src.getColorModel(),dst.getColorModel(),renderingHints);
                    try{context.compose(src.getRaster(),dst.getRaster(),out.getRaster());}finally{context.dispose();}
                    out.getRGB(0,0,w,h,result,0,w);for(int i=0;i<count;i++)if((coverage[i]>>>24)==0)result[i]=destination[i];
                }
            }
            image.setRGB(region.x,region.y,w,h,result,0,w);
        }finally{scratch.busy=false;if(!retained)scratch.recycle();}
    }
    private void configureText(android.graphics.Paint p){
        p.setTypeface(BufferedImageFontMetrics.typefaceFor(font));p.setTextSize(font.getSize2D());p.setLetterSpacing(font.getTracking());p.setUnderlineText(font.isUnderlined());p.setStrikeThruText(font.isStrikethrough());
    }
    private void configureCanvas(android.graphics.Canvas canvas,Rectangle region){
        canvas.translate(-region.x,-region.y);
        if(clipRects==null)canvas.clipRect(clip.x,clip.y,clip.x+clip.width,clip.y+clip.height);
        else{android.graphics.Path clipping=new android.graphics.Path();for(int i=0;i<clipRects.length;i+=4)clipping.addRect(clipRects[i],clipRects[i+1],clipRects[i+2],clipRects[i+3],android.graphics.Path.Direction.CW);canvas.clipPath(clipping);}
        canvas.concat(androidMatrix(transform));
    }
    private static android.graphics.PorterDuff.Mode porterDuff(int rule){
        switch(rule){
            case AlphaComposite.CLEAR:return android.graphics.PorterDuff.Mode.CLEAR;
            case AlphaComposite.SRC:return android.graphics.PorterDuff.Mode.SRC;
            case AlphaComposite.DST:return android.graphics.PorterDuff.Mode.DST;
            case AlphaComposite.DST_OVER:return android.graphics.PorterDuff.Mode.DST_OVER;
            case AlphaComposite.SRC_IN:return android.graphics.PorterDuff.Mode.SRC_IN;
            case AlphaComposite.DST_IN:return android.graphics.PorterDuff.Mode.DST_IN;
            case AlphaComposite.SRC_OUT:return android.graphics.PorterDuff.Mode.SRC_OUT;
            case AlphaComposite.DST_OUT:return android.graphics.PorterDuff.Mode.DST_OUT;
            case AlphaComposite.SRC_ATOP:return android.graphics.PorterDuff.Mode.SRC_ATOP;
            case AlphaComposite.DST_ATOP:return android.graphics.PorterDuff.Mode.DST_ATOP;
            case AlphaComposite.XOR:return android.graphics.PorterDuff.Mode.XOR;
            default:return android.graphics.PorterDuff.Mode.SRC_OVER;
        }
    }

    @Override public void dispose() {}
}
