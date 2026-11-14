/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.awt;

import java.awt.image.BufferedImage;

import net.runelite.awt.AwtUnsupported;

/**
 * This class is used to generate native system input events
 * for the purposes of test automation, self-running demos, and
 * other applications where control of the mouse and keyboard
 * is needed.
 * <p>
 * On Android no process may inject input into other windows or read the
 * screen, so, as the specification allows for platforms where low-level input
 * control is not available, every constructor throws {@link AWTException}
 * (the message is 8u's headless one) and no instance can exist. The instance
 * methods are kept for linkage; the ones that would touch the device throw
 * {@code UnsupportedOperationException}.
 *
 * @author      Robi Khan
 * @since       1.3
 */
public class Robot {
    private static final int MAX_DELAY = 60000;
    private boolean isAutoWaitForIdle = false;
    private int autoDelay = 0;

    /**
     * Constructs a Robot object in the coordinate system of the primary screen.
     *
     * @throws  AWTException always on Android, as on any platform whose
     *          configuration does not allow low-level input control
     */
    public Robot() throws AWTException {
        throw new AWTException("headless environment");
    }

    /**
     * Creates a Robot for the given screen device.
     *
     * @param screen    A screen GraphicsDevice indicating the coordinate
     *                  system the Robot will operate in.
     * @throws  AWTException always on Android, after the argument checks
     * @throws  IllegalArgumentException if <code>screen</code> is not a screen
     *          GraphicsDevice.
     */
    public Robot(GraphicsDevice screen) throws AWTException {
        checkIsScreenDevice(screen);
        throw new AWTException("headless environment");
    }

    /* determine if the given device is a screen device */
    private static void checkIsScreenDevice(GraphicsDevice device) {
        if (device == null || device.getType() != GraphicsDevice.TYPE_RASTER_SCREEN) {
            throw new IllegalArgumentException("not a valid screen device");
        }
    }

    /**
     * Moves mouse pointer to given screen coordinates.
     * @param x         X position
     * @param y         Y position
     */
    public synchronized void mouseMove(int x, int y) {
        throw AwtUnsupported.fail("java.awt.Robot.mouseMove(int, int)");
    }

    /**
     * Presses one or more mouse buttons.
     * @param buttons the Button mask; a combination of one or more
     * mouse button masks.
     */
    public synchronized void mousePress(int buttons) {
        throw AwtUnsupported.fail("java.awt.Robot.mousePress(int)");
    }

    /**
     * Releases one or more mouse buttons.
     * @param buttons the Button mask; a combination of one or more
     * mouse button masks.
     */
    public synchronized void mouseRelease(int buttons) {
        throw AwtUnsupported.fail("java.awt.Robot.mouseRelease(int)");
    }

    /**
     * Rotates the scroll wheel on wheel-equipped mice.
     *
     * @param wheelAmt  number of "notches" to move the mouse wheel
     * @since 1.4
     */
    public synchronized void mouseWheel(int wheelAmt) {
        throw AwtUnsupported.fail("java.awt.Robot.mouseWheel(int)");
    }

    /**
     * Presses a given key.
     * @param   keycode Key to press (e.g. <code>KeyEvent.VK_A</code>)
     */
    public synchronized void keyPress(int keycode) {
        throw AwtUnsupported.fail("java.awt.Robot.keyPress(int)");
    }

    /**
     * Releases a given key.
     * @param   keycode Key to release (e.g. <code>KeyEvent.VK_A</code>)
     */
    public synchronized void keyRelease(int keycode) {
        throw AwtUnsupported.fail("java.awt.Robot.keyRelease(int)");
    }

    /**
     * Returns the color of a pixel at the given screen coordinates.
     * @param   x       X position of pixel
     * @param   y       Y position of pixel
     * @return  Color of the pixel
     */
    public synchronized Color getPixelColor(int x, int y) {
        throw AwtUnsupported.fail("java.awt.Robot.getPixelColor(int, int)");
    }

    /**
     * Creates an image containing pixels read from the screen.
     * @param   screenRect      Rect to capture in screen coordinates
     * @return  The captured image
     * @throws  IllegalArgumentException if <code>screenRect</code> width and height are not greater than zero
     */
    public synchronized BufferedImage createScreenCapture(Rectangle screenRect) {
        checkScreenCaptureAllowed(screenRect);
        throw AwtUnsupported.fail("java.awt.Robot.createScreenCapture(Rectangle)");
    }

    private static void checkScreenCaptureAllowed(Rectangle rect) {
        if (rect.width <= 0 || rect.height <= 0) {
            throw new IllegalArgumentException("Rectangle width and height must be > 0");
        }
    }

    /**
     * Returns whether this Robot automatically invokes <code>waitForIdle</code>
     * after generating an event.
     * @return Whether <code>waitForIdle</code> is automatically called
     */
    public synchronized boolean isAutoWaitForIdle() {
        return isAutoWaitForIdle;
    }

    /**
     * Sets whether this Robot automatically invokes <code>waitForIdle</code>
     * after generating an event.
     * @param   isOn    Whether <code>waitForIdle</code> is automatically invoked
     */
    public synchronized void setAutoWaitForIdle(boolean isOn) {
        isAutoWaitForIdle = isOn;
    }

    /**
     * Returns the number of milliseconds this Robot sleeps after generating an event.
     * @return the delay duration in milliseconds
     */
    public synchronized int getAutoDelay() {
        return autoDelay;
    }

    /**
     * Sets the number of milliseconds this Robot sleeps after generating an event.
     * @param  ms the delay duration in milliseconds
     * @throws IllegalArgumentException If <code>ms</code> is not between 0 and 60,000 milliseconds inclusive
     */
    public synchronized void setAutoDelay(int ms) {
        checkDelayArgument(ms);
        autoDelay = ms;
    }

    /**
     * Sleeps for the specified time.
     * To catch any <code>InterruptedException</code>s that occur,
     * <code>Thread.sleep()</code> may be used instead.
     * @param   ms      time to sleep in milliseconds
     * @throws  IllegalArgumentException if <code>ms</code> is not between 0 and 60,000 milliseconds inclusive
     * @see     java.lang.Thread#sleep
     */
    public synchronized void delay(int ms) {
        checkDelayArgument(ms);
        try {
            Thread.sleep(ms);
        } catch(InterruptedException ite) {
            ite.printStackTrace();
        }
    }

    private void checkDelayArgument(int ms) {
        if (ms < 0 || ms > MAX_DELAY) {
            throw new IllegalArgumentException("Delay must be to 0 to 60,000ms");
        }
    }

    /**
     * Waits until all events currently on the event queue have been processed.
     * @throws  IllegalThreadStateException if called on the AWT event dispatching thread
     */
    public synchronized void waitForIdle() {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalThreadStateException("Cannot call method from the event dispatcher thread");
        }
        throw AwtUnsupported.fail("java.awt.Robot.waitForIdle()");
    }

    /**
     * Returns a string representation of this Robot.
     *
     * @return  the string representation.
     */
    public synchronized String toString() {
        String params = "autoDelay = "+getAutoDelay()+", "+"autoWaitForIdle = "+isAutoWaitForIdle();
        return getClass().getName() + "[ " + params + " ]";
    }
}
