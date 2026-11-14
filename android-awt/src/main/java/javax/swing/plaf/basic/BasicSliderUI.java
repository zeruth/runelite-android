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
package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Dictionary;
import java.util.Enumeration;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.SliderUI;

/**
 * The Basic slider delegate with the JDK's geometry, painting and input: the focus, content,
 * track, tick and label rectangles laid out as {@code calculateGeometry} does, the track inset by
 * {@code trackBuffer} (half the thumb, or half the widest end label), value/pixel mapping over the
 * track's first to last pixel, a drag that moves the thumb and a press beside it that scrolls by
 * blocks while held.  The one divergence is the thumb, {@value #THUMB_ACROSS}x{@value #THUMB_ALONG}
 * pixels (the JDK's is 11x20) so a fingertip can hold it.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class BasicSliderUI extends SliderUI {
    public static final int POSITIVE_SCROLL = +1;
    public static final int NEGATIVE_SCROLL = -1;
    public static final int MIN_SCROLL = -2;
    public static final int MAX_SCROLL = +2;

    /** Thumb extent along the track and across it; the JDK's 11x20 is too small to grab by touch. */
    private static final int THUMB_ALONG = 18;
    private static final int THUMB_ACROSS = 24;

    protected Timer scrollTimer;
    protected JSlider slider;

    protected Insets focusInsets = null;
    protected Insets insetCache = null;
    protected boolean leftToRightCache = true;
    protected Rectangle focusRect = null;
    protected Rectangle contentRect = null;
    protected Rectangle labelRect = null;
    protected Rectangle tickRect = null;
    protected Rectangle trackRect = null;
    protected Rectangle thumbRect = null;

    /** The distance the track has from the side of the control. */
    protected int trackBuffer = 0;

    private transient boolean isDragging;

    protected TrackListener trackListener;
    protected ChangeListener changeListener;
    protected ComponentListener componentListener;
    protected FocusListener focusListener;
    protected ScrollListener scrollListener;
    protected PropertyChangeListener propertyChangeListener;
    private KeyListener keyListener;

    private Color shadowColor;
    private Color highlightColor;
    private Color focusColor;

    /** The value before the last change, so a value halfway between ticks snaps in the direction it came from. */
    private int lastValue;

    protected Color getShadowColor() { return shadowColor; }

    protected Color getHighlightColor() { return highlightColor; }

    protected Color getFocusColor() { return focusColor; }

    /** True while the user drags the thumb. */
    protected boolean isDragging() { return isDragging; }

    public static ComponentUI createUI(JComponent b) { return new BasicSliderUI((JSlider) b); }

    public BasicSliderUI(JSlider b) {}

    @Override public void installUI(JComponent c) {
        slider = (JSlider) c;
        slider.setEnabled(slider.isEnabled());
        LookAndFeel.installProperty(slider, "opaque", Boolean.TRUE);
        isDragging = false;
        trackListener = createTrackListener(slider);
        changeListener = createChangeListener(slider);
        componentListener = createComponentListener(slider);
        focusListener = createFocusListener(slider);
        scrollListener = createScrollListener(slider);
        propertyChangeListener = createPropertyChangeListener(slider);

        installDefaults(slider);
        installListeners(slider);
        installKeyboardActions(slider);

        scrollTimer = new Timer(100, scrollListener);
        scrollTimer.setInitialDelay(300);

        insetCache = slider.getInsets();
        leftToRightCache = slider.getComponentOrientation().isLeftToRight();
        focusRect = new Rectangle();
        contentRect = new Rectangle();
        labelRect = new Rectangle();
        tickRect = new Rectangle();
        trackRect = new Rectangle();
        thumbRect = new Rectangle();
        lastValue = slider.getValue();

        calculateGeometry();
    }

    @Override public void uninstallUI(JComponent c) {
        if (c != slider) {
            throw new IllegalComponentStateException(this + " was asked to deinstall() " + c
                    + " when it only knows about " + slider + ".");
        }
        scrollTimer.stop();
        scrollTimer = null;
        uninstallDefaults(slider);
        uninstallListeners(slider);
        uninstallKeyboardActions(slider);
        insetCache = null;
        leftToRightCache = true;
        focusRect = contentRect = labelRect = tickRect = trackRect = thumbRect = null;
        trackListener = null;
        changeListener = null;
        componentListener = null;
        focusListener = null;
        scrollListener = null;
        propertyChangeListener = null;
        slider = null;
    }

    protected void installDefaults(JSlider slider) {
        LookAndFeel.installBorder(slider, "Slider.border");
        LookAndFeel.installColorsAndFont(slider, "Slider.background", "Slider.foreground", "Slider.font");
        highlightColor = color("Slider.highlight", Color.WHITE);
        shadowColor = color("Slider.shadow", new Color(0x808080));
        focusColor = color("Slider.focus", Color.BLACK);
        focusInsets = (Insets) UIManager.get("Slider.focusInsets");
        // A look and feel without the key still gets the JDK's two-pixel focus band.
        if (focusInsets == null) focusInsets = new InsetsUIResource(2, 2, 2, 2);
    }

    protected void uninstallDefaults(JSlider slider) {
        LookAndFeel.uninstallBorder(slider);
        focusInsets = null;
    }

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    protected TrackListener createTrackListener(JSlider slider) { return new TrackListener(); }

    protected ChangeListener createChangeListener(JSlider slider) { return new ChangeHandler(); }

    protected ComponentListener createComponentListener(JSlider slider) { return new ComponentHandler(); }

    protected FocusListener createFocusListener(JSlider slider) { return new FocusHandler(); }

    protected ScrollListener createScrollListener(JSlider slider) { return new ScrollListener(); }

    protected PropertyChangeListener createPropertyChangeListener(JSlider slider) { return new PropertyChangeHandler(); }

    protected void installListeners(JSlider slider) {
        slider.addMouseListener(trackListener);
        slider.addMouseMotionListener(trackListener);
        slider.addFocusListener(focusListener);
        slider.addComponentListener(componentListener);
        slider.addPropertyChangeListener(propertyChangeListener);
        slider.getModel().addChangeListener(changeListener);
    }

    protected void uninstallListeners(JSlider slider) {
        slider.removeMouseListener(trackListener);
        slider.removeMouseMotionListener(trackListener);
        slider.removeFocusListener(focusListener);
        slider.removeComponentListener(componentListener);
        slider.removePropertyChangeListener(propertyChangeListener);
        slider.getModel().removeChangeListener(changeListener);
    }

    /**
     * The JDK's "SliderUI" input map, as a key listener: arrows step by a unit, page keys by a
     * block, home/end jump to the ends; inverted sliders swap the directions.
     */
    protected void installKeyboardActions(JSlider slider) {
        keyListener = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                JSlider target = BasicSliderUI.this.slider;
                if (target == null || !target.isEnabled()) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT: case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP:
                        scroll(POSITIVE_SCROLL, false); break;
                    case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT: case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN:
                        scroll(NEGATIVE_SCROLL, false); break;
                    case KeyEvent.VK_PAGE_UP: scroll(POSITIVE_SCROLL, true); break;
                    case KeyEvent.VK_PAGE_DOWN: scroll(NEGATIVE_SCROLL, true); break;
                    case KeyEvent.VK_HOME: scroll(MIN_SCROLL, false); break;
                    case KeyEvent.VK_END: scroll(MAX_SCROLL, false); break;
                    default: return;
                }
                e.consume();
            }
        };
        slider.addKeyListener(keyListener);
    }

    protected void uninstallKeyboardActions(JSlider slider) {
        if (keyListener != null) slider.removeKeyListener(keyListener);
        keyListener = null;
    }

    private void scroll(int direction, boolean isBlock) {
        boolean invert = slider.getInverted();
        if (direction == NEGATIVE_SCROLL || direction == POSITIVE_SCROLL) {
            if (invert) direction = direction == POSITIVE_SCROLL ? NEGATIVE_SCROLL : POSITIVE_SCROLL;
            if (isBlock) scrollByBlock(direction);
            else scrollByUnit(direction);
        } else {
            if (invert) direction = direction == MIN_SCROLL ? MAX_SCROLL : MIN_SCROLL;
            slider.setValue(direction == MIN_SCROLL ? slider.getMinimum() : slider.getMaximum());
        }
    }

    // --- sizes ----------------------------------------------------------------------------------

    public Dimension getPreferredHorizontalSize() { return dimension("Slider.horizontalSize", 200, 21); }

    public Dimension getPreferredVerticalSize() { return dimension("Slider.verticalSize", 21, 200); }

    public Dimension getMinimumHorizontalSize() { return dimension("Slider.minimumHorizontalSize", 36, 21); }

    public Dimension getMinimumVerticalSize() { return dimension("Slider.minimumVerticalSize", 21, 36); }

    private static Dimension dimension(String key, int width, int height) {
        Dimension d = UIManager.getDimension(key);
        return d != null ? d : new Dimension(width, height);
    }

    @Override public Dimension getPreferredSize(JComponent c) {
        recalculateIfInsetsChanged();
        Dimension d;
        if (slider.getOrientation() == JSlider.VERTICAL) {
            d = new Dimension(getPreferredVerticalSize());
            d.width = insetCache.left + insetCache.right + focusInsets.left + focusInsets.right
                    + trackRect.width + tickRect.width + labelRect.width;
        } else {
            d = new Dimension(getPreferredHorizontalSize());
            d.height = insetCache.top + insetCache.bottom + focusInsets.top + focusInsets.bottom
                    + trackRect.height + tickRect.height + labelRect.height;
        }
        return d;
    }

    @Override public Dimension getMinimumSize(JComponent c) {
        recalculateIfInsetsChanged();
        Dimension d;
        if (slider.getOrientation() == JSlider.VERTICAL) {
            d = new Dimension(getMinimumVerticalSize());
            d.width = insetCache.left + insetCache.right + focusInsets.left + focusInsets.right
                    + trackRect.width + tickRect.width + labelRect.width;
        } else {
            d = new Dimension(getMinimumHorizontalSize());
            d.height = insetCache.top + insetCache.bottom + focusInsets.top + focusInsets.bottom
                    + trackRect.height + tickRect.height + labelRect.height;
        }
        return d;
    }

    @Override public Dimension getMaximumSize(JComponent c) {
        Dimension d = getPreferredSize(c);
        if (slider.getOrientation() == JSlider.VERTICAL) d.height = Short.MAX_VALUE;
        else d.width = Short.MAX_VALUE;
        return d;
    }

    // --- geometry -------------------------------------------------------------------------------

    protected void calculateGeometry() {
        calculateFocusRect();
        calculateContentRect();
        calculateThumbSize();
        calculateTrackBuffer();
        calculateTrackRect();
        calculateTickRect();
        calculateLabelRect();
        calculateThumbLocation();
    }

    protected void calculateFocusRect() {
        focusRect.x = insetCache.left;
        focusRect.y = insetCache.top;
        focusRect.width = slider.getWidth() - (insetCache.left + insetCache.right);
        focusRect.height = slider.getHeight() - (insetCache.top + insetCache.bottom);
    }

    protected void calculateThumbSize() {
        Dimension size = getThumbSize();
        thumbRect.setSize(size.width, size.height);
    }

    protected void calculateContentRect() {
        contentRect.x = focusRect.x + focusInsets.left;
        contentRect.y = focusRect.y + focusInsets.top;
        contentRect.width = focusRect.width - (focusInsets.left + focusInsets.right);
        contentRect.height = focusRect.height - (focusInsets.top + focusInsets.bottom);
    }

    private int getTickSpacing() {
        int majorTickSpacing = slider.getMajorTickSpacing();
        int minorTickSpacing = slider.getMinorTickSpacing();
        if (minorTickSpacing > 0) return minorTickSpacing;
        if (majorTickSpacing > 0) return majorTickSpacing;
        return 0;
    }

    protected void calculateThumbLocation() {
        if (slider.getSnapToTicks()) {
            int sliderValue = slider.getValue();
            int snappedValue = sliderValue;
            int tickSpacing = getTickSpacing();
            if (tickSpacing != 0) {
                // If it's not on a tick, change the value.
                if ((sliderValue - slider.getMinimum()) % tickSpacing != 0) {
                    float temp = (float) (sliderValue - slider.getMinimum()) / (float) tickSpacing;
                    int whichTick = Math.round(temp);
                    // JDK-6401380: halfway between ticks, a value coming down snaps down.
                    if (temp - (int) temp == .5 && sliderValue < lastValue) whichTick--;
                    snappedValue = slider.getMinimum() + (whichTick * tickSpacing);
                }
                if (snappedValue != sliderValue) slider.setValue(snappedValue);
            }
        }
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            int valuePosition = xPositionForValue(slider.getValue());
            thumbRect.x = valuePosition - (thumbRect.width / 2);
            thumbRect.y = trackRect.y;
        } else {
            int valuePosition = yPositionForValue(slider.getValue());
            thumbRect.x = trackRect.x;
            thumbRect.y = valuePosition - (thumbRect.height / 2);
        }
    }

    protected void calculateTrackBuffer() {
        if (slider.getPaintLabels() && slider.getLabelTable() != null) {
            Component highLabel = getHighestValueLabel();
            Component lowLabel = getLowestValueLabel();
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                trackBuffer = Math.max(highLabel.getBounds().width, lowLabel.getBounds().width) / 2;
                trackBuffer = Math.max(trackBuffer, thumbRect.width / 2);
            } else {
                trackBuffer = Math.max(highLabel.getBounds().height, lowLabel.getBounds().height) / 2;
                trackBuffer = Math.max(trackBuffer, thumbRect.height / 2);
            }
        } else {
            trackBuffer = slider.getOrientation() == JSlider.HORIZONTAL ? thumbRect.width / 2 : thumbRect.height / 2;
        }
    }

    protected void calculateTrackRect() {
        int centerSpacing; // centres a slider stretched across, as in a BorderLayout CENTER (4275631)
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            centerSpacing = thumbRect.height;
            if (slider.getPaintTicks()) centerSpacing += getTickLength();
            if (slider.getPaintLabels()) centerSpacing += getHeightOfTallestLabel();
            trackRect.x = contentRect.x + trackBuffer;
            trackRect.y = contentRect.y + (contentRect.height - centerSpacing - 1) / 2;
            trackRect.width = contentRect.width - (trackBuffer * 2);
            trackRect.height = thumbRect.height;
        } else {
            centerSpacing = thumbRect.width;
            if (leftToRight()) {
                if (slider.getPaintTicks()) centerSpacing += getTickLength();
                if (slider.getPaintLabels()) centerSpacing += getWidthOfWidestLabel();
            } else {
                if (slider.getPaintTicks()) centerSpacing -= getTickLength();
                if (slider.getPaintLabels()) centerSpacing -= getWidthOfWidestLabel();
            }
            trackRect.x = contentRect.x + (contentRect.width - centerSpacing - 1) / 2;
            trackRect.y = contentRect.y + trackBuffer;
            trackRect.width = thumbRect.width;
            trackRect.height = contentRect.height - (trackBuffer * 2);
        }
    }

    /** The length of the major ticks; minor ticks are half of it. */
    protected int getTickLength() { return 8; }

    protected void calculateTickRect() {
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            tickRect.x = trackRect.x;
            tickRect.y = trackRect.y + trackRect.height;
            tickRect.width = trackRect.width;
            tickRect.height = slider.getPaintTicks() ? getTickLength() : 0;
        } else {
            tickRect.width = slider.getPaintTicks() ? getTickLength() : 0;
            tickRect.x = leftToRight() ? trackRect.x + trackRect.width : trackRect.x - tickRect.width;
            tickRect.y = trackRect.y;
            tickRect.height = trackRect.height;
        }
    }

    protected void calculateLabelRect() {
        boolean ltr = leftToRight();
        if (slider.getPaintLabels()) {
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                labelRect.x = tickRect.x - trackBuffer;
                labelRect.y = tickRect.y + tickRect.height;
                labelRect.width = tickRect.width + (trackBuffer * 2);
                labelRect.height = getHeightOfTallestLabel();
            } else {
                labelRect.width = getWidthOfWidestLabel();
                labelRect.x = ltr ? tickRect.x + tickRect.width : tickRect.x - labelRect.width;
                labelRect.y = tickRect.y - trackBuffer;
                labelRect.height = tickRect.height + (trackBuffer * 2);
            }
        } else if (slider.getOrientation() == JSlider.HORIZONTAL) {
            labelRect.x = tickRect.x;
            labelRect.y = tickRect.y + tickRect.height;
            labelRect.width = tickRect.width;
            labelRect.height = 0;
        } else {
            labelRect.x = ltr ? tickRect.x + tickRect.width : tickRect.x;
            labelRect.y = tickRect.y;
            labelRect.width = 0;
            labelRect.height = tickRect.height;
        }
    }

    protected Dimension getThumbSize() {
        return slider.getOrientation() == JSlider.VERTICAL
                ? new Dimension(THUMB_ACROSS, THUMB_ALONG) : new Dimension(THUMB_ALONG, THUMB_ACROSS);
    }

    protected int getWidthOfWidestLabel() {
        Dictionary dictionary = slider.getLabelTable();
        int widest = 0;
        if (dictionary != null) {
            Enumeration keys = dictionary.keys();
            while (keys.hasMoreElements()) {
                JComponent label = (JComponent) dictionary.get(keys.nextElement());
                widest = Math.max(label.getPreferredSize().width, widest);
            }
        }
        return widest;
    }

    protected int getHeightOfTallestLabel() {
        Dictionary dictionary = slider.getLabelTable();
        int tallest = 0;
        if (dictionary != null) {
            Enumeration keys = dictionary.keys();
            while (keys.hasMoreElements()) {
                JComponent label = (JComponent) dictionary.get(keys.nextElement());
                tallest = Math.max(label.getPreferredSize().height, tallest);
            }
        }
        return tallest;
    }

    protected int getWidthOfHighValueLabel() {
        Component label = getHighestValueLabel();
        return label != null ? label.getPreferredSize().width : 0;
    }

    protected int getWidthOfLowValueLabel() {
        Component label = getLowestValueLabel();
        return label != null ? label.getPreferredSize().width : 0;
    }

    protected int getHeightOfHighValueLabel() {
        Component label = getHighestValueLabel();
        return label != null ? label.getPreferredSize().height : 0;
    }

    protected int getHeightOfLowValueLabel() {
        Component label = getLowestValueLabel();
        return label != null ? label.getPreferredSize().height : 0;
    }

    protected boolean drawInverted() {
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            return leftToRight() ? slider.getInverted() : !slider.getInverted();
        }
        return slider.getInverted();
    }

    /** The largest key of the label table, or null without one. */
    protected Integer getHighestValue() {
        Dictionary dictionary = slider.getLabelTable();
        if (dictionary == null) return null;
        Enumeration keys = dictionary.keys();
        Integer max = null;
        while (keys.hasMoreElements()) {
            Integer i = (Integer) keys.nextElement();
            if (max == null || i.intValue() > max.intValue()) max = i;
        }
        return max;
    }

    /** The smallest key of the label table, or null without one. */
    protected Integer getLowestValue() {
        Dictionary dictionary = slider.getLabelTable();
        if (dictionary == null) return null;
        Enumeration keys = dictionary.keys();
        Integer min = null;
        while (keys.hasMoreElements()) {
            Integer i = (Integer) keys.nextElement();
            if (min == null || i.intValue() < min.intValue()) min = i;
        }
        return min;
    }

    protected Component getLowestValueLabel() {
        Integer min = getLowestValue();
        return min != null ? (Component) slider.getLabelTable().get(min) : null;
    }

    protected Component getHighestValueLabel() {
        Integer max = getHighestValue();
        return max != null ? (Component) slider.getLabelTable().get(max) : null;
    }

    private boolean leftToRight() { return slider.getComponentOrientation().isLeftToRight(); }

    protected void recalculateIfInsetsChanged() {
        Insets newInsets = slider.getInsets();
        if (!newInsets.equals(insetCache)) {
            insetCache = newInsets;
            calculateGeometry();
        }
    }

    protected void recalculateIfOrientationChanged() {
        boolean ltr = leftToRight();
        if (ltr != leftToRightCache) {
            leftToRightCache = ltr;
            calculateGeometry();
        }
    }

    /** The x of the track pixel for {@code value}, clamped to the track; inverted runs right to left. */
    public int xPositionForValue(int value) {
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        int trackLength = trackRect.width;
        double valueRange = (double) max - (double) min;
        double pixelsPerValue = (double) trackLength / valueRange;
        int trackLeft = trackRect.x;
        int trackRight = trackRect.x + (trackRect.width - 1);
        int xPosition;
        if (!drawInverted()) {
            xPosition = trackLeft;
            xPosition += Math.round(pixelsPerValue * ((double) value - min));
        } else {
            xPosition = trackRight;
            xPosition -= Math.round(pixelsPerValue * ((double) value - min));
        }
        xPosition = Math.max(trackLeft, xPosition);
        xPosition = Math.min(trackRight, xPosition);
        return xPosition;
    }

    public int yPositionForValue(int value) {
        return yPositionForValue(value, trackRect.y, trackRect.height);
    }

    /** The y of {@code value} on a track at {@code trackY}; the minimum sits at the bottom unless inverted. */
    protected int yPositionForValue(int value, int trackY, int trackHeight) {
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        double valueRange = (double) max - (double) min;
        double pixelsPerValue = (double) trackHeight / valueRange;
        int trackBottom = trackY + (trackHeight - 1);
        int yPosition = trackY;
        if (!drawInverted()) {
            yPosition += Math.round(pixelsPerValue * ((double) max - value));
        } else {
            yPosition += Math.round(pixelsPerValue * ((double) value - min));
        }
        yPosition = Math.max(trackY, yPosition);
        yPosition = Math.min(trackBottom, yPosition);
        return yPosition;
    }

    /** The value at {@code yPos}; past either end of the track it is the end's value. */
    public int valueForYPosition(int yPos) {
        int value;
        final int minValue = slider.getMinimum();
        final int maxValue = slider.getMaximum();
        final int trackLength = trackRect.height;
        final int trackTop = trackRect.y;
        final int trackBottom = trackRect.y + (trackRect.height - 1);
        if (yPos <= trackTop) {
            value = drawInverted() ? minValue : maxValue;
        } else if (yPos >= trackBottom) {
            value = drawInverted() ? maxValue : minValue;
        } else {
            int distanceFromTrackTop = yPos - trackTop;
            double valueRange = (double) maxValue - (double) minValue;
            double valuePerPixel = valueRange / (double) trackLength;
            int valueFromTrackTop = (int) Math.round(distanceFromTrackTop * valuePerPixel);
            value = drawInverted() ? minValue + valueFromTrackTop : maxValue - valueFromTrackTop;
        }
        return value;
    }

    /** The value at {@code xPos}; past either end of the track it is the end's value. */
    public int valueForXPosition(int xPos) {
        int value;
        final int minValue = slider.getMinimum();
        final int maxValue = slider.getMaximum();
        final int trackLength = trackRect.width;
        final int trackLeft = trackRect.x;
        final int trackRight = trackRect.x + (trackRect.width - 1);
        if (xPos <= trackLeft) {
            value = drawInverted() ? maxValue : minValue;
        } else if (xPos >= trackRight) {
            value = drawInverted() ? minValue : maxValue;
        } else {
            int distanceFromTrackLeft = xPos - trackLeft;
            double valueRange = (double) maxValue - (double) minValue;
            double valuePerPixel = valueRange / (double) trackLength;
            int valueFromTrackLeft = (int) Math.round(distanceFromTrackLeft * valuePerPixel);
            value = drawInverted() ? maxValue - valueFromTrackLeft : minValue + valueFromTrackLeft;
        }
        return value;
    }

    // --- painting -------------------------------------------------------------------------------

    @Override public void paint(Graphics g, JComponent c) {
        recalculateIfInsetsChanged();
        recalculateIfOrientationChanged();
        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = new Rectangle(0, 0, slider.getWidth(), slider.getHeight());
        if (!clip.intersects(trackRect) && slider.getPaintTrack()) calculateGeometry();
        if (slider.getPaintTrack() && clip.intersects(trackRect)) paintTrack(g);
        if (slider.getPaintTicks() && clip.intersects(tickRect)) paintTicks(g);
        if (slider.getPaintLabels() && clip.intersects(labelRect)) paintLabels(g);
        if (slider.hasFocus() && clip.intersects(focusRect)) paintFocus(g);
        if (clip.intersects(thumbRect)) paintThumb(g);
    }

    /** The JDK's dashed focus rectangle (BasicGraphicsUtils.drawDashedRect): every other pixel. */
    public void paintFocus(Graphics g) {
        g.setColor(getFocusColor());
        int x = focusRect.x, y = focusRect.y, width = focusRect.width, height = focusRect.height;
        for (int vx = x; vx < x + width; vx += 2) {
            g.fillRect(vx, y, 1, 1);
            g.fillRect(vx, y + height - 1, 1, 1);
        }
        for (int vy = y; vy < y + height; vy += 2) {
            g.fillRect(x, vy, 1, 1);
            g.fillRect(x + width - 1, vy, 1, 1);
        }
    }

    public void paintTrack(Graphics g) {
        Rectangle trackBounds = trackRect;
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            int cy = (trackBounds.height / 2) - 2;
            int cw = trackBounds.width;
            g.translate(trackBounds.x, trackBounds.y + cy);
            g.setColor(getShadowColor());
            g.drawLine(0, 0, cw - 1, 0);
            g.drawLine(0, 1, 0, 2);
            g.setColor(getHighlightColor());
            g.drawLine(0, 3, cw, 3);
            g.drawLine(cw, 0, cw, 3);
            g.setColor(Color.black);
            g.drawLine(1, 1, cw - 2, 1);
            g.translate(-trackBounds.x, -(trackBounds.y + cy));
        } else {
            int cx = (trackBounds.width / 2) - 2;
            int ch = trackBounds.height;
            g.translate(trackBounds.x + cx, trackBounds.y);
            g.setColor(getShadowColor());
            g.drawLine(0, 0, 0, ch - 1);
            g.drawLine(1, 0, 2, 0);
            g.setColor(getHighlightColor());
            g.drawLine(3, 0, 3, ch);
            g.drawLine(0, ch, 3, ch);
            g.setColor(Color.black);
            g.drawLine(1, 1, 1, ch - 2);
            g.translate(-(trackBounds.x + cx), -trackBounds.y);
        }
    }

    public void paintTicks(Graphics g) {
        Rectangle tickBounds = tickRect;
        g.setColor(color("Slider.tickColor", Color.black));
        int min = slider.getMinimum(), max = slider.getMaximum();
        int minor = slider.getMinorTickSpacing(), major = slider.getMajorTickSpacing();
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            g.translate(0, tickBounds.y);
            if (minor > 0) {
                for (int value = min; value <= max; value += minor) {
                    paintMinorTickForHorizSlider(g, tickBounds, xPositionForValue(value));
                    if (Integer.MAX_VALUE - minor < value) break;
                }
            }
            if (major > 0) {
                for (int value = min; value <= max; value += major) {
                    paintMajorTickForHorizSlider(g, tickBounds, xPositionForValue(value));
                    if (Integer.MAX_VALUE - major < value) break;
                }
            }
            g.translate(0, -tickBounds.y);
        } else {
            g.translate(tickBounds.x, 0);
            int offset = 0;
            if (!leftToRight()) {
                offset = tickBounds.width - getTickLength();
                g.translate(offset, 0);
            }
            if (minor > 0) {
                for (int value = min; value <= max; value += minor) {
                    paintMinorTickForVertSlider(g, tickBounds, yPositionForValue(value));
                    if (Integer.MAX_VALUE - minor < value) break;
                }
            }
            if (major > 0) {
                for (int value = min; value <= max; value += major) {
                    paintMajorTickForVertSlider(g, tickBounds, yPositionForValue(value));
                    if (Integer.MAX_VALUE - major < value) break;
                }
            }
            g.translate(-tickBounds.x - offset, 0);
        }
    }

    protected void paintMinorTickForHorizSlider(Graphics g, Rectangle tickBounds, int x) {
        g.drawLine(x, 0, x, tickBounds.height / 2 - 1);
    }

    protected void paintMajorTickForHorizSlider(Graphics g, Rectangle tickBounds, int x) {
        g.drawLine(x, 0, x, tickBounds.height - 2);
    }

    protected void paintMinorTickForVertSlider(Graphics g, Rectangle tickBounds, int y) {
        g.drawLine(0, y, tickBounds.width / 2 - 1, y);
    }

    protected void paintMajorTickForVertSlider(Graphics g, Rectangle tickBounds, int y) {
        g.drawLine(0, y, tickBounds.width - 2, y);
    }

    public void paintLabels(Graphics g) {
        Rectangle labelBounds = labelRect;
        Dictionary dictionary = slider.getLabelTable();
        if (dictionary == null) return;
        Enumeration keys = dictionary.keys();
        int minValue = slider.getMinimum();
        int maxValue = slider.getMaximum();
        boolean enabled = slider.isEnabled();
        while (keys.hasMoreElements()) {
            Integer key = (Integer) keys.nextElement();
            int value = key.intValue();
            if (value < minValue || value > maxValue) continue;
            JComponent label = (JComponent) dictionary.get(key);
            label.setEnabled(enabled);
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                g.translate(0, labelBounds.y);
                paintHorizontalLabel(g, value, label);
                g.translate(0, -labelBounds.y);
            } else {
                int offset = leftToRight() ? 0 : labelBounds.width - label.getPreferredSize().width;
                g.translate(labelBounds.x + offset, 0);
                paintVerticalLabel(g, value, label);
                g.translate(-labelBounds.x - offset, 0);
            }
        }
    }

    /** Paints a label centred on its value's x; the graphics is already at the label row. */
    protected void paintHorizontalLabel(Graphics g, int value, Component label) {
        int labelCenter = xPositionForValue(value);
        int labelLeft = labelCenter - (label.getPreferredSize().width / 2);
        g.translate(labelLeft, 0);
        label.paint(g);
        g.translate(-labelLeft, 0);
    }

    /** Paints a label centred on its value's y; the graphics is already at the label column. */
    protected void paintVerticalLabel(Graphics g, int value, Component label) {
        int labelCenter = yPositionForValue(value);
        int labelTop = labelCenter - (label.getPreferredSize().height / 2);
        g.translate(0, labelTop);
        label.paint(g);
        g.translate(0, -labelTop);
    }

    public void paintThumb(Graphics g) {
        Rectangle knobBounds = thumbRect;
        int w = knobBounds.width;
        int h = knobBounds.height;
        g.translate(knobBounds.x, knobBounds.y);
        g.setColor(slider.isEnabled() ? slider.getBackground() : slider.getBackground().darker());
        Boolean paintThumbArrowShape = (Boolean) slider.getClientProperty("Slider.paintThumbArrowShape");
        if ((!slider.getPaintTicks() && paintThumbArrowShape == null) || Boolean.FALSE.equals(paintThumbArrowShape)) {
            // "plain" version
            g.fillRect(0, 0, w, h);
            g.setColor(Color.black);
            g.drawLine(0, h - 1, w - 1, h - 1);
            g.drawLine(w - 1, 0, w - 1, h - 1);
            g.setColor(highlightColor);
            g.drawLine(0, 0, 0, h - 2);
            g.drawLine(1, 0, w - 2, 0);
            g.setColor(shadowColor);
            g.drawLine(1, h - 2, w - 2, h - 2);
            g.drawLine(w - 2, 1, w - 2, h - 3);
        } else if (slider.getOrientation() == JSlider.HORIZONTAL) {
            int cw = w / 2;
            g.fillRect(1, 1, w - 3, h - 1 - cw);
            Polygon p = new Polygon();
            p.addPoint(1, h - cw);
            p.addPoint(cw - 1, h - 1);
            p.addPoint(w - 2, h - 1 - cw);
            g.fillPolygon(p);
            g.setColor(highlightColor);
            g.drawLine(0, 0, w - 2, 0);
            g.drawLine(0, 1, 0, h - 1 - cw);
            g.drawLine(0, h - cw, cw - 1, h - 1);
            g.setColor(Color.black);
            g.drawLine(w - 1, 0, w - 1, h - 2 - cw);
            g.drawLine(w - 1, h - 1 - cw, w - 1 - cw, h - 1);
            g.setColor(shadowColor);
            g.drawLine(w - 2, 1, w - 2, h - 2 - cw);
            g.drawLine(w - 2, h - 1 - cw, w - 1 - cw, h - 2);
        } else if (leftToRight()) {
            int cw = h / 2;
            g.fillRect(1, 1, w - 1 - cw, h - 3);
            Polygon p = new Polygon();
            p.addPoint(w - cw - 1, 0);
            p.addPoint(w - 1, cw);
            p.addPoint(w - 1 - cw, h - 2);
            g.fillPolygon(p);
            g.setColor(highlightColor);
            g.drawLine(0, 0, 0, h - 2);
            g.drawLine(1, 0, w - 1 - cw, 0);
            g.drawLine(w - cw - 1, 0, w - 1, cw);
            g.setColor(Color.black);
            g.drawLine(0, h - 1, w - 2 - cw, h - 1);
            g.drawLine(w - 1 - cw, h - 1, w - 1, h - 1 - cw);
            g.setColor(shadowColor);
            g.drawLine(1, h - 2, w - 2 - cw, h - 2);
            g.drawLine(w - 1 - cw, h - 2, w - 2, h - cw - 1);
        } else {
            int cw = h / 2;
            g.fillRect(5, 1, w - 1 - cw, h - 3);
            Polygon p = new Polygon();
            p.addPoint(cw, 0);
            p.addPoint(0, cw);
            p.addPoint(cw, h - 2);
            g.fillPolygon(p);
            g.setColor(highlightColor);
            g.drawLine(cw - 1, 0, w - 2, 0);
            g.drawLine(0, cw, cw, 0);
            g.setColor(Color.black);
            g.drawLine(0, h - 1 - cw, cw, h - 1);
            g.drawLine(cw, h - 1, w - 1, h - 1);
            g.setColor(shadowColor);
            g.drawLine(cw, h - 2, w - 2, h - 2);
            g.drawLine(w - 1, 1, w - 1, h - 2);
        }
        g.translate(-knobBounds.x, -knobBounds.y);
    }

    /** Moves the thumb during a drag, repainting the old and new areas. */
    public void setThumbLocation(int x, int y) {
        Rectangle dirty = new Rectangle(thumbRect);
        thumbRect.setLocation(x, y);
        dirty = dirty.union(thumbRect);
        slider.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    // --- scrolling ------------------------------------------------------------------------------

    /** A tenth of the range (at least 1, at least the tick spacing when snapping), as the JDK steps. */
    public void scrollByBlock(int direction) {
        synchronized (slider) {
            int blockIncrement = (slider.getMaximum() - slider.getMinimum()) / 10;
            if (blockIncrement == 0) blockIncrement = 1;
            if (slider.getSnapToTicks()) {
                int tickSpacing = getTickSpacing();
                if (blockIncrement < tickSpacing) blockIncrement = tickSpacing;
            }
            int delta = blockIncrement * ((direction > 0) ? POSITIVE_SCROLL : NEGATIVE_SCROLL);
            slider.setValue(slider.getValue() + delta);
        }
    }

    public void scrollByUnit(int direction) {
        synchronized (slider) {
            int delta = (direction > 0) ? POSITIVE_SCROLL : NEGATIVE_SCROLL;
            if (slider.getSnapToTicks()) delta *= getTickSpacing();
            slider.setValue(slider.getValue() + delta);
        }
    }

    /** A press in the track beside the thumb scrolls one block towards the press. */
    protected void scrollDueToClickInTrack(int dir) {
        scrollByBlock(dir);
    }

    // --- listeners ------------------------------------------------------------------------------

    /** Outside a drag a model change re-places the thumb, which is where snapToTicks moves the value onto a tick. */
    public class ChangeHandler implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            if (slider == null) return;
            if (!isDragging) {
                calculateThumbLocation();
                slider.repaint();
            }
            lastValue = slider.getValue();
        }
    }

    /** Thumb drags and track presses. */
    public class TrackListener extends MouseAdapter {
        protected transient int offset;
        protected transient int currentMouseX;
        protected transient int currentMouseY;

        @Override public void mouseReleased(MouseEvent e) {
            if (!slider.isEnabled()) return;
            offset = 0;
            scrollTimer.stop();
            isDragging = false;
            slider.setValueIsAdjusting(false);
            slider.repaint();
        }

        @Override public void mousePressed(MouseEvent e) {
            if (!slider.isEnabled()) return;
            // The slider may have been resized since the last layout; the direction below reads the thumb.
            calculateGeometry();
            currentMouseX = e.getX();
            currentMouseY = e.getY();
            if (slider.isRequestFocusEnabled()) slider.requestFocus();

            if (thumbRect.contains(currentMouseX, currentMouseY)) {
                if (UIManager.getBoolean("Slider.onlyLeftMouseButtonDrag") && !SwingUtilities.isLeftMouseButton(e)) return;
                offset = slider.getOrientation() == JSlider.VERTICAL ? currentMouseY - thumbRect.y : currentMouseX - thumbRect.x;
                isDragging = true;
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(e)) return;

            isDragging = false;
            slider.setValueIsAdjusting(true);
            Dimension sbSize = slider.getSize();
            int direction;
            if (slider.getOrientation() == JSlider.VERTICAL) {
                boolean above = thumbRect.isEmpty() ? currentMouseY < sbSize.height / 2 : currentMouseY < thumbRect.y;
                direction = above != drawInverted() ? POSITIVE_SCROLL : NEGATIVE_SCROLL;
            } else {
                boolean before = thumbRect.isEmpty() ? currentMouseX < sbSize.width / 2 : currentMouseX < thumbRect.x;
                direction = before != drawInverted() ? NEGATIVE_SCROLL : POSITIVE_SCROLL;
            }
            if (shouldScroll(direction)) scrollDueToClickInTrack(direction);
            if (shouldScroll(direction)) {
                scrollTimer.stop();
                scrollListener.setDirection(direction);
                scrollTimer.start();
            }
        }

        /** Whether the thumb has not yet reached the press, and the value can still move that way. */
        public boolean shouldScroll(int direction) {
            Rectangle r = thumbRect;
            if (slider.getOrientation() == JSlider.VERTICAL) {
                if (drawInverted() ? direction < 0 : direction > 0) {
                    if (r.y <= currentMouseY) return false;
                } else if (r.y + r.height >= currentMouseY) {
                    return false;
                }
            } else {
                if (drawInverted() ? direction < 0 : direction > 0) {
                    if (r.x + r.width >= currentMouseX) return false;
                } else if (r.x <= currentMouseX) {
                    return false;
                }
            }
            if (direction > 0 && slider.getValue() + slider.getExtent() >= slider.getMaximum()) return false;
            if (direction < 0 && slider.getValue() <= slider.getMinimum()) return false;
            return true;
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (!slider.isEnabled()) return;
            currentMouseX = e.getX();
            currentMouseY = e.getY();
            if (!isDragging) return;
            slider.setValueIsAdjusting(true);
            if (slider.getOrientation() == JSlider.VERTICAL) {
                int halfThumbHeight = thumbRect.height / 2;
                int thumbTop = e.getY() - offset;
                int trackTop = trackRect.y;
                int trackBottom = trackRect.y + (trackRect.height - 1);
                int vMax = yPositionForValue(slider.getMaximum() - slider.getExtent());
                if (drawInverted()) trackBottom = vMax;
                else trackTop = vMax;
                thumbTop = Math.max(thumbTop, trackTop - halfThumbHeight);
                thumbTop = Math.min(thumbTop, trackBottom - halfThumbHeight);
                setThumbLocation(thumbRect.x, thumbTop);
                slider.setValue(valueForYPosition(thumbTop + halfThumbHeight));
            } else {
                int halfThumbWidth = thumbRect.width / 2;
                int thumbLeft = e.getX() - offset;
                int trackLeft = trackRect.x;
                int trackRight = trackRect.x + (trackRect.width - 1);
                int hMax = xPositionForValue(slider.getMaximum() - slider.getExtent());
                if (drawInverted()) trackLeft = hMax;
                else trackRight = hMax;
                thumbLeft = Math.max(thumbLeft, trackLeft - halfThumbWidth);
                thumbLeft = Math.min(thumbLeft, trackRight - halfThumbWidth);
                setThumbLocation(thumbLeft, thumbRect.y);
                slider.setValue(valueForXPosition(thumbLeft + halfThumbWidth));
            }
        }
    }

    /** Repeats the track-press scroll while the press is held. */
    public class ScrollListener implements ActionListener {
        boolean useBlockIncrement;
        int direction = POSITIVE_SCROLL;

        public ScrollListener() {
            this(POSITIVE_SCROLL, true);
        }

        public ScrollListener(int dir, boolean block) {
            direction = dir;
            useBlockIncrement = block;
        }

        public void setDirection(int direction) { this.direction = direction; }

        public void setScrollByBlock(boolean block) { this.useBlockIncrement = block; }

        public void actionPerformed(ActionEvent e) {
            if (useBlockIncrement) scrollByBlock(direction);
            else scrollByUnit(direction);
            if (!trackListener.shouldScroll(direction)) ((Timer) e.getSource()).stop();
        }
    }

    public class ComponentHandler extends ComponentAdapter {
        @Override public void componentResized(ComponentEvent e) {
            calculateGeometry();
            slider.repaint();
        }
    }

    public class FocusHandler implements FocusListener {
        public void focusGained(FocusEvent e) { slider.repaint(); }

        public void focusLost(FocusEvent e) { slider.repaint(); }
    }

    /** Geometry-affecting properties recalculate; a new model takes the change listener along. */
    public class PropertyChangeHandler implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if ("orientation".equals(propertyName) || "inverted".equals(propertyName)
                    || "labelTable".equals(propertyName) || "majorTickSpacing".equals(propertyName)
                    || "minorTickSpacing".equals(propertyName) || "paintTicks".equals(propertyName)
                    || "paintTrack".equals(propertyName) || "font".equals(propertyName)
                    || "paintLabels".equals(propertyName) || "Slider.paintThumbArrowShape".equals(propertyName)
                    || "componentOrientation".equals(propertyName)) {
                calculateGeometry();
                slider.repaint();
            } else if ("model".equals(propertyName)) {
                Object oldModel = e.getOldValue(), newModel = e.getNewValue();
                if (oldModel instanceof BoundedRangeModel) ((BoundedRangeModel) oldModel).removeChangeListener(changeListener);
                if (newModel instanceof BoundedRangeModel) ((BoundedRangeModel) newModel).addChangeListener(changeListener);
                calculateThumbLocation();
                slider.repaint();
            }
        }
    }
}
