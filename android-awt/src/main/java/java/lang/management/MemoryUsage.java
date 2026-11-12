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

public class MemoryUsage {
    private final long init, used, committed, max;

    public MemoryUsage(long init, long used, long committed, long max) {
        if (init < -1) throw new IllegalArgumentException("init parameter = " + init + " is negative but not -1.");
        if (max < -1) throw new IllegalArgumentException("max parameter = " + max + " is negative but not -1.");
        if (used < 0) throw new IllegalArgumentException("used parameter = " + used + " is negative.");
        if (committed < 0) throw new IllegalArgumentException("committed parameter = " + committed + " is negative.");
        if (used > committed) {
            throw new IllegalArgumentException("used = " + used + " should be <= committed = " + committed);
        }
        if (max >= 0 && committed > max) {
            throw new IllegalArgumentException("committed = " + committed + " should be < max = " + max);
        }
        this.init = init; this.used = used; this.committed = committed; this.max = max;
    }

    public long getInit() { return init; }
    public long getUsed() { return used; }
    public long getCommitted() { return committed; }
    public long getMax() { return max; }

    @Override
    public String toString() {
        return "init = " + init + "(" + (init >> 10) + "K) "
                + "used = " + used + "(" + (used >> 10) + "K) "
                + "committed = " + committed + "(" + (committed >> 10) + "K) "
                + "max = " + max + "(" + (max >> 10) + "K)";
    }
}
