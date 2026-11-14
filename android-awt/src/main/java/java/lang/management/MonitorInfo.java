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
package java.lang.management;

/** An object monitor locked by a thread, with the stack frame that locked it. */
public class MonitorInfo extends LockInfo {
    private final int stackDepth;
    private final StackTraceElement stackFrame;

    public MonitorInfo(String className, int identityHashCode, int stackDepth, StackTraceElement stackFrame) {
        super(className, identityHashCode);
        if (stackDepth >= 0 && stackFrame == null) {
            throw new IllegalArgumentException("Parameter stackDepth is " + stackDepth + " but stackFrame is null");
        }
        if (stackDepth < 0 && stackFrame != null) {
            throw new IllegalArgumentException("Parameter stackDepth is " + stackDepth + " but stackFrame is not null");
        }
        this.stackDepth = stackDepth;
        this.stackFrame = stackFrame;
    }

    /** The depth in the owning thread's stack trace, or a negative number when unknown. */
    public int getLockedStackDepth() {
        return stackDepth;
    }

    public StackTraceElement getLockedStackFrame() {
        return stackFrame;
    }
}
