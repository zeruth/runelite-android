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
package java.awt;

/**
 * The pointer is the last host pointer event: every touch or mouse event the Android host
 * dispatches into a component tree goes through {@link Component#dispatchMouseEvent}, which
 * records its screen position (the {@link Component#getLocationOnScreen} space of that tree)
 * and the tree's root. Before the first event the pointer is at (0, 0) over no tree.
 *
 * The JDK throws HeadlessException here when the toolkit is headless; the shadow reports
 * itself headless (no native windows) yet always has a pointer, so it answers instead.
 */
public class MouseInfo {
    private MouseInfo() {}

    private static final class Pointer {
        // Weak: a closed panel's tree must not stay reachable through the last pointer event.
        final java.lang.ref.WeakReference<Component> root;
        final int x, y;
        Pointer(Component root, int x, int y) {
            this.root = new java.lang.ref.WeakReference<Component>(root);
            this.x = x;
            this.y = y;
        }
    }

    private static volatile Pointer pointer = new Pointer(null, 0, 0);

    /** Records a host pointer position, in the screen space of the tree rooted at {@code root}. */
    static void pointerAt(Component root, int x, int y) {
        Pointer last = pointer;
        if (last.root.get() != root || last.x != x || last.y != y) pointer = new Pointer(root, x, y);
    }

    /** Whether the last pointer event went into the tree rooted at {@code root}. */
    static boolean isUnderPointer(Component root) {
        return root != null && pointer.root.get() == root;
    }

    public static PointerInfo getPointerInfo() throws HeadlessException {
        Pointer last = pointer;
        // The device the shadow GraphicsEnvironment reports as its screen, taken directly so
        // the pointer never depends on which GraphicsEnvironment is loaded: a headless JDK one
        // would throw HeadlessException here.
        GraphicsDevice device = net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getDevice();
        return new PointerInfo(device, new Point(last.x, last.y));
    }

    public static int getNumberOfButtons() {
        return 3;
    }
}
