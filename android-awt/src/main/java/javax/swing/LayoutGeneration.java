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
import java.lang.reflect.Method;

/**
 * Reads {@code Component.subtreeLayoutGeneration()}, the shadow's change counter for a
 * subtree, so layout managers can expire cached measurements. The JDK expires them through
 * {@code LayoutManager2.invalidateLayout}, which the shadow {@code Container} does not call on
 * every invalidate. Looked up reflectively because the same sources compile and run against
 * the host JDK's {@code Component} in the contract tests, where the member does not exist and
 * {@link #NONE} leaves the JDK's own invalidateLayout path in charge.
 */
final class LayoutGeneration {
    static final long NONE = Long.MIN_VALUE;

    private static final Method SUBTREE = find();

    private LayoutGeneration() {
    }

    private static Method find() {
        try {
            return Component.class.getMethod("subtreeLayoutGeneration");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static long of(Component component) {
        if (SUBTREE == null || component == null) {
            return NONE;
        }
        try {
            return ((Long) SUBTREE.invoke(component)).longValue();
        } catch (Exception e) {
            return NONE;
        }
    }
}
