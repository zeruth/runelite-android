/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.ImageObserver;
import java.io.PrintStream;

/**
 * Graphics subclass supporting graphics debugging.
 *
 * <p>A pass-through on Android: every call goes to the wrapped Graphics unchanged. The debug
 * options are stored and answered, but logging, flashing and the buffered-paint window are
 * deliberate no-ops: they slow painting down so that a person watching a desktop screen can
 * follow it, and the panel hosts here rasterize off screen and show the finished frame.
 */
public class DebugGraphics extends Graphics {

    /** Log graphics operations. */
    public static final int LOG_OPTION = 1 << 0;
    /** Flash graphics operations. */
    public static final int FLASH_OPTION = 1 << 1;
    /** Show buffered operations in a separate <code>Frame</code>. */
    public static final int BUFFERED_OPTION = 1 << 2;
    /** Don't debug graphics operations. */
    public static final int NONE_OPTION = -1;

    private static Color flashColor = Color.red;
    private static int flashTime = 100;
    private static int flashCount = 2;
    private static PrintStream logStream = System.out;

    private final Graphics graphics;
    private final JComponent component;
    private int debugOptions;
    private int xOffset;
    private int yOffset;

    /**
     * Constructs a new debug graphics context that supports slowed down drawing.
     */
    public DebugGraphics() {
        this(null, null);
    }

    /**
     * Constructs a debug graphics context from an existing graphics context that slows down
     * drawing for the specified component.
     *
     * @param graphics  the Graphics context to slow down
     * @param component the JComponent to draw slowly
     */
    public DebugGraphics(Graphics graphics, JComponent component) {
        this.graphics = graphics;
        this.component = component;
    }

    /**
     * Constructs a debug graphics context from an existing graphics context that supports
     * slowed down drawing.
     *
     * @param graphics  the Graphics context to slow down
     */
    public DebugGraphics(Graphics graphics) {
        this(graphics, null);
    }

    private DebugGraphics(Graphics graphics, JComponent component, int debugOptions, int xOffset, int yOffset) {
        this(graphics, component);
        this.debugOptions = debugOptions;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    /**
     * Overrides <code>Graphics.create</code> to return a DebugGraphics object.
     */
    public Graphics create() {
        return graphics == null ? null
            : new DebugGraphics(graphics.create(), component, debugOptions, xOffset, yOffset);
    }

    /**
     * Overrides <code>Graphics.create</code> to return a DebugGraphics object.
     */
    public Graphics create(int x, int y, int width, int height) {
        return graphics == null ? null : new DebugGraphics(graphics.create(x, y, width, height),
            component, debugOptions, xOffset + x, yOffset + y);
    }

    /** Sets the Color used to flash drawing operations. */
    public static void setFlashColor(Color flashColor) {
        DebugGraphics.flashColor = flashColor;
    }

    /** Returns the Color used to flash drawing operations. */
    public static Color flashColor() {
        return flashColor;
    }

    /** Sets the time delay of drawing operation flashing. */
    public static void setFlashTime(int flashTime) {
        DebugGraphics.flashTime = flashTime;
    }

    /** Returns the time delay of drawing operation flashing. */
    public static int flashTime() {
        return flashTime;
    }

    /** Sets the number of times that drawing operations will flash. */
    public static void setFlashCount(int flashCount) {
        DebugGraphics.flashCount = flashCount;
    }

    /** Returns the number of times that drawing operations will flash. */
    public static int flashCount() {
        return flashCount;
    }

    /** Sets the stream to which the DebugGraphics logs drawing operations. */
    public static void setLogStream(PrintStream stream) {
        logStream = stream;
    }

    /** Returns the stream to which the DebugGraphics logs drawing operations. */
    public static PrintStream logStream() {
        return logStream;
    }

    /**
     * Returns the drawingBuffer value.
     *
     * @return true if this object is drawing from a Buffer
     */
    public boolean isDrawingBuffer() {
        return false;
    }

    /**
     * Enables/disables diagnostic information about every graphics operation. The value of
     * <b>options</b> indicates how this information should be displayed. LOG_OPTION causes a
     * text message to be printed. FLASH_OPTION causes the drawing to flash several times.
     * BUFFERED_OPTION creates a new Frame that shows each operation on an offscreen buffer.
     * NONE_OPTION disables debugging. A value of 0 causes no changes to the debugging options.
     *
     * @param options the debug options; recorded, see the class comment
     */
    public void setDebugOptions(int options) {
        if (options != 0) {
            if (options == NONE_OPTION) {
                debugOptions = 0;
            } else {
                debugOptions |= options;
            }
        }
    }

    /**
     * Returns the current debugging options for this DebugGraphics.
     *
     * @see #setDebugOptions
     */
    public int getDebugOptions() {
        return debugOptions;
    }

    public void setFont(Font aFont) { graphics.setFont(aFont); }
    public Font getFont() { return graphics.getFont(); }
    public void setColor(Color aColor) { graphics.setColor(aColor); }
    public Color getColor() { return graphics.getColor(); }
    public FontMetrics getFontMetrics() { return graphics.getFontMetrics(); }
    public FontMetrics getFontMetrics(Font f) { return graphics.getFontMetrics(f); }

    public void translate(int x, int y) {
        xOffset += x;
        yOffset += y;
        graphics.translate(x, y);
    }

    public void setPaintMode() { graphics.setPaintMode(); }
    public void setXORMode(Color aColor) { graphics.setXORMode(aColor); }
    public Rectangle getClipBounds() { return graphics.getClipBounds(); }
    public void clipRect(int x, int y, int width, int height) { graphics.clipRect(x, y, width, height); }
    public void setClip(int x, int y, int width, int height) { graphics.setClip(x, y, width, height); }
    public Shape getClip() { return graphics.getClip(); }
    public void setClip(Shape clip) { graphics.setClip(clip); }
    public boolean hitClip(int x, int y, int width, int height) { return graphics.hitClip(x, y, width, height); }

    public void drawRect(int x, int y, int width, int height) { graphics.drawRect(x, y, width, height); }
    public void fillRect(int x, int y, int width, int height) { graphics.fillRect(x, y, width, height); }
    public void clearRect(int x, int y, int width, int height) { graphics.clearRect(x, y, width, height); }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        graphics.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        graphics.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void drawLine(int x1, int y1, int x2, int y2) { graphics.drawLine(x1, y1, x2, y2); }
    public void drawOval(int x, int y, int width, int height) { graphics.drawOval(x, y, width, height); }
    public void fillOval(int x, int y, int width, int height) { graphics.fillOval(x, y, width, height); }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        graphics.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        graphics.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) { graphics.drawPolyline(xPoints, yPoints, nPoints); }
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) { graphics.drawPolygon(xPoints, yPoints, nPoints); }
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) { graphics.fillPolygon(xPoints, yPoints, nPoints); }
    public void drawString(String aString, int x, int y) { graphics.drawString(aString, x, y); }

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return graphics.drawImage(img, x, y, observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return graphics.drawImage(img, x, y, width, height, observer);
    }

    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return graphics.drawImage(img, x, y, bgcolor, observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        return graphics.drawImage(img, x, y, width, height, bgcolor, observer);
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        return graphics.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        return graphics.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    public void copyArea(int x, int y, int width, int height, int destX, int destY) {
        graphics.copyArea(x, y, width, height, destX, destY);
    }

    public void dispose() {
        if (graphics != null) graphics.dispose();
    }
}
