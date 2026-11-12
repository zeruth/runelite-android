/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.awt.image;

import java.awt.BufferCapabilities;
import java.awt.Graphics;

/**
 * The <code>BufferStrategy</code> class represents the mechanism with which
 * to organize complex memory on a particular <code>Canvas</code> or
 * <code>Window</code>.  Hardware and software limitations determine whether and
 * how a particular buffer strategy can be implemented.  These limitations
 * are detectable through the capabilities of the
 * <code>GraphicsConfiguration</code> used when creating the
 * <code>Canvas</code> or <code>Window</code>.
 * <p>
 * Alternatively, rendering may be performed in a loop that checks
 * {@link #contentsLost()} and {@link #contentsRestored()} after each
 * {@link #show()}, re-rendering the frame when either reports a change.
 *
 * @see java.awt.Window
 * @see java.awt.Canvas
 * @see java.awt.GraphicsConfiguration
 * @see VolatileImage
 * @author Michael Martak
 * @since 1.4
 */
public abstract class BufferStrategy {

    /**
     * Returns the <code>BufferCapabilities</code> for this
     * <code>BufferStrategy</code>.
     *
     * @return the buffering capabilities of this strategy
     */
    public abstract BufferCapabilities getCapabilities();

    /**
     * Creates a graphics context for the drawing buffer.  This method may not
     * be synchronized for performance reasons; use of this method by multiple
     * threads should be handled at the application level.  Disposal of the
     * graphics object obtained must be handled by the application.
     *
     * @return a graphics context for the drawing buffer
     */
    public abstract Graphics getDrawGraphics();

    /**
     * Returns whether the drawing buffer was lost since the last call to
     * <code>getDrawGraphics</code>.  Since the buffers in a buffer strategy
     * are usually type <code>VolatileImage</code>, they may become lost.
     * For a discussion on lost buffers, see <code>VolatileImage</code>.
     *
     * @return Whether or not the drawing buffer was lost since the last call
     * to <code>getDrawGraphics</code>.
     * @see java.awt.image.VolatileImage
     */
    public abstract boolean contentsLost();

    /**
     * Returns whether the drawing buffer was recently restored from a lost
     * state and reinitialized to the default background color (white).
     * Since the buffers in a buffer strategy are usually type
     * <code>VolatileImage</code>, they may become lost.  If a surface has
     * been recently restored from a lost state since the last call to
     * <code>getDrawGraphics</code>, it may require repainting.
     * For a discussion on lost buffers, see <code>VolatileImage</code>.
     *
     * @return Whether or not the drawing buffer was restored since the last
     *         call to <code>getDrawGraphics</code>.
     * @see java.awt.image.VolatileImage
     */
    public abstract boolean contentsRestored();

    /**
     * Makes the next available buffer visible by either copying the memory
     * (blitting) or changing the display pointer (flipping).
     */
    public abstract void show();

    /**
     * Releases system resources currently consumed by this
     * <code>BufferStrategy</code> and
     * removes it from the associated Component.  After invoking this
     * method, <code>getBufferStrategy</code> will return null.  Trying
     * to use a <code>BufferStrategy</code> after it has been disposed will
     * result in undefined behavior.
     *
     * @see java.awt.Window#createBufferStrategy
     * @see java.awt.Canvas#createBufferStrategy
     * @see java.awt.Window#getBufferStrategy
     * @see java.awt.Canvas#getBufferStrategy
     * @since 1.6
     */
    public void dispose() {
    }
}
