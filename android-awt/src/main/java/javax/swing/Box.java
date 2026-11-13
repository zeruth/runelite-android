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

import java.awt.Component;
import java.awt.Dimension;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;

public class Box extends JComponent implements Accessible {
    private static final long serialVersionUID = 1L;

    public static class Filler extends JComponent implements Accessible {
        private static final long serialVersionUID = 1L;
        public Filler(Dimension min, Dimension pref, Dimension max) {
            setMinimumSize(min); setPreferredSize(pref); setMaximumSize(max);
        }
        public void changeShape(Dimension min, Dimension pref, Dimension max) {
            setMinimumSize(min); setPreferredSize(pref); setMaximumSize(max);
            revalidate();
        }

        /**
         * Paints this Filler: through the UI delegate when one is set,
         * otherwise fills the bounds with the background when opaque.
         */
        @Override protected void paintComponent(java.awt.Graphics g) {
            if (ui != null) {
                super.paintComponent(g);
            } else if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
        @Override public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleBoxFiller();
            }
            return accessibleContext;
        }

        /** Accessibility support for fillers: role FILLER. */
        protected class AccessibleBoxFiller extends AccessibleAWTComponent {
            public javax.accessibility.AccessibleRole getAccessibleRole() {
                return javax.accessibility.AccessibleRole.FILLER;
            }
        }
    }

    public Box(int axis) { super.setLayout(new BoxLayout(this, axis)); }

    /**
     * Throws an AWTError, since a Box can use only a BoxLayout.
     */
    @Override public void setLayout(java.awt.LayoutManager l) {
        throw new java.awt.AWTError("Illegal request");
    }

    /**
     * Paints this Box: through the UI delegate when one is set, otherwise
     * fills the bounds with the background when opaque.
     */
    @Override protected void paintComponent(java.awt.Graphics g) {
        if (ui != null) {
            super.paintComponent(g);
        } else if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    public static Component createHorizontalStrut(int width) { return new Filler(new Dimension(width, 0), new Dimension(width, 0), new Dimension(width, Short.MAX_VALUE)); }
    public static Component createVerticalStrut(int height) { return new Filler(new Dimension(0, height), new Dimension(0, height), new Dimension(Short.MAX_VALUE, height)); }
    public static Component createRigidArea(Dimension d) { return new Filler(d, d, d); }
    public static Component createHorizontalGlue() { return new Filler(new Dimension(), new Dimension(), new Dimension(Short.MAX_VALUE, 0)); }
    public static Component createVerticalGlue() { return new Filler(new Dimension(), new Dimension(), new Dimension(0, Short.MAX_VALUE)); }
    public static Component createGlue() { return new Filler(new Dimension(), new Dimension(), new Dimension(Short.MAX_VALUE, Short.MAX_VALUE)); }
    public static Box createHorizontalBox() { return new Box(BoxLayout.X_AXIS); }
    public static Box createVerticalBox() { return new Box(BoxLayout.Y_AXIS); }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleBox();
        }
        return accessibleContext;
    }

    /** Accessibility support for boxes: role FILLER, with the box's children. */
    protected class AccessibleBox extends AccessibleAWTContainer {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.FILLER;
        }
    }
}
