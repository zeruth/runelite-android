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

import java.awt.Container;
import java.awt.Insets;

/**
 * <code>LayoutStyle</code> provides information about how to position
 * components. As on the JDK, {@link #getInstance} answers the instance set
 * with {@link #setInstance}, or else the current look and feel's
 * {@link LookAndFeel#getLayoutStyle}; the look and feel default is the
 * JDK's <code>sun.swing.DefaultLayoutStyle</code> (UNRELATED 12, RELATED 6,
 * container gap 6, check box and radio button indents), which is what
 * BasicLookAndFeel, and with it FlatLaf, answers.
 *
 * @since 1.6
 */
public abstract class LayoutStyle {

    private static LayoutStyle instance;

    /**
     * Sets the shared instance of <code>LayoutStyle</code>. Specifying
     * <code>null</code> results in using the <code>LayoutStyle</code> from
     * the current <code>LookAndFeel</code>.
     */
    public static void setInstance(LayoutStyle style) {
        synchronized (LayoutStyle.class) {
            instance = style;
        }
    }

    /**
     * Returns the shared instance of <code>LayoutStyle</code>. If an instance
     * has not been specified in <code>setInstance</code>, this will return
     * the <code>LayoutStyle</code> from the current <code>LookAndFeel</code>.
     */
    public static LayoutStyle getInstance() {
        LayoutStyle style;
        synchronized (LayoutStyle.class) {
            style = instance;
        }
        if (style == null) {
            LookAndFeel laf = UIManager.getLookAndFeel();
            style = laf == null ? null : laf.getLayoutStyle();
        }
        return style == null ? defaultStyle() : style;
    }

    /**
     * <code>ComponentPlacement</code> is an enumeration of the
     * possible ways two components can be placed relative to each
     * other.
     */
    public enum ComponentPlacement {
        /** Two components are visually related and will be placed in the same parent. */
        RELATED,
        /** Two components are visually unrelated and will be placed in the same parent. */
        UNRELATED,
        /** The distance to indent a component is being requested. */
        INDENT;
    }

    /** Creates a new <code>LayoutStyle</code>. */
    public LayoutStyle() {
    }

    /**
     * Returns the amount of space to use between two components.
     *
     * @throws IllegalArgumentException if <code>position</code> is not one
     *         of <code>SwingConstants.NORTH</code>, <code>SOUTH</code>,
     *         <code>EAST</code> or <code>WEST</code>
     * @throws NullPointerException if <code>component1</code>,
     *         <code>component2</code> or <code>type</code> is
     *         <code>null</code>
     */
    public abstract int getPreferredGap(JComponent component1, JComponent component2,
                                        ComponentPlacement type, int position,
                                        Container parent);

    /**
     * Returns the amount of space to place between the component and specified
     * edge of its parent.
     */
    public abstract int getContainerGap(JComponent component, int position, Container parent);

    /** The look and feel default, used by {@link LookAndFeel#getLayoutStyle}. */
    static LayoutStyle defaultStyle() {
        return DefaultLayoutStyle.INSTANCE;
    }

    /** The JDK's sun.swing.DefaultLayoutStyle. */
    private static final class DefaultLayoutStyle extends LayoutStyle {
        static final DefaultLayoutStyle INSTANCE = new DefaultLayoutStyle();

        @Override
        public int getPreferredGap(JComponent component1, JComponent component2,
                                   ComponentPlacement type, int position, Container parent) {
            if (component1 == null || component2 == null || type == null) {
                throw new NullPointerException();
            }
            checkPosition(position);
            if (type == ComponentPlacement.INDENT
                    && (position == SwingConstants.EAST || position == SwingConstants.WEST)) {
                int indent = getIndent(component1, position);
                if (indent > 0) {
                    return indent;
                }
            }
            return (type == ComponentPlacement.UNRELATED) ? 12 : 6;
        }

        @Override
        public int getContainerGap(JComponent component, int position, Container parent) {
            if (component == null) {
                throw new NullPointerException();
            }
            checkPosition(position);
            return 6;
        }

        private static void checkPosition(int position) {
            if (position != SwingConstants.NORTH && position != SwingConstants.SOUTH
                    && position != SwingConstants.WEST && position != SwingConstants.EAST) {
                throw new IllegalArgumentException();
            }
        }

        /** Check boxes and radio buttons indent past their icon and gap; the rest do not. */
        private static int getIndent(JComponent c, int position) {
            String classID = c.getUIClassID();
            boolean checkBox = "CheckBoxUI".equals(classID);
            if ((checkBox || "RadioButtonUI".equals(classID)) && c instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) c;
                Insets insets = c.getInsets();
                Icon icon = button.getIcon();
                if (icon == null) {
                    icon = UIManager.getIcon(checkBox ? "CheckBox.icon" : "RadioButton.icon");
                }
                if (icon == null) {
                    return 0;
                }
                int gap = button.getIconTextGap();
                int hAlign = button.getHorizontalAlignment();
                // Left-to-right only: there is no ComponentOrientation in the shadow.
                if (position == SwingConstants.WEST
                        && (hAlign == SwingConstants.LEFT || hAlign == SwingConstants.LEADING)) {
                    return insets.left + icon.getIconWidth() + gap;
                }
                if (position == SwingConstants.EAST
                        && (hAlign == SwingConstants.RIGHT || hAlign == SwingConstants.TRAILING)) {
                    return insets.right + icon.getIconWidth() + gap;
                }
            }
            return 0;
        }
    }
}
