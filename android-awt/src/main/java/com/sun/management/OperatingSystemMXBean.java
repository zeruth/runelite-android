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
package com.sun.management;

/**
 * The extended operating system MXBean. The platform bean
 * ({@code ManagementFactory.getOperatingSystemMXBean()}) reads /proc/self/stat,
 * /proc/self/status, /proc/meminfo, /proc/stat and /proc/loadavg; a value whose source the
 * process may not read (Android 8+ denies apps /proc/stat) is -1, the JDK's "not available".
 */
public interface OperatingSystemMXBean extends java.lang.management.OperatingSystemMXBean {
    long getCommittedVirtualMemorySize();
    long getTotalSwapSpaceSize();
    long getFreeSwapSpaceSize();
    long getProcessCpuTime();

    @Deprecated
    long getFreePhysicalMemorySize();

    // JDK 14
    default long getFreeMemorySize() { return getFreePhysicalMemorySize(); }

    @Deprecated
    long getTotalPhysicalMemorySize();

    // JDK 14
    default long getTotalMemorySize() { return getTotalPhysicalMemorySize(); }

    @Deprecated
    double getSystemCpuLoad();

    // JDK 14
    default double getCpuLoad() { return getSystemCpuLoad(); }

    double getProcessCpuLoad();
}
