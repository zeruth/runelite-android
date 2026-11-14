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

public interface ThreadMXBean extends PlatformManagedObject {
    int getThreadCount();
    int getPeakThreadCount();
    long getTotalStartedThreadCount();
    int getDaemonThreadCount();
    long[] getAllThreadIds();
    ThreadInfo getThreadInfo(long id);
    ThreadInfo[] getThreadInfo(long[] ids);
    ThreadInfo getThreadInfo(long id, int maxDepth);
    ThreadInfo[] getThreadInfo(long[] ids, int maxDepth);
    boolean isThreadContentionMonitoringSupported();
    boolean isThreadContentionMonitoringEnabled();
    void setThreadContentionMonitoringEnabled(boolean enable);
    long getCurrentThreadCpuTime();
    long getCurrentThreadUserTime();
    long getThreadCpuTime(long id);
    long getThreadUserTime(long id);
    boolean isThreadCpuTimeSupported();
    boolean isCurrentThreadCpuTimeSupported();
    boolean isThreadCpuTimeEnabled();
    void setThreadCpuTimeEnabled(boolean enable);
    long[] findMonitorDeadlockedThreads();
    void resetPeakThreadCount();
    long[] findDeadlockedThreads();
    boolean isObjectMonitorUsageSupported();
    boolean isSynchronizerUsageSupported();
    ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers);

    /**
     * JDK 10. As on the JDK, the default throws for implementations that predate the method;
     * the platform bean ({@code ManagementFactory.getThreadMXBean()}) overrides it.
     */
    default ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers, int maxDepth) {
        throw net.runelite.awt.AwtUnsupported.fail("java.lang.management.ThreadMXBean.getThreadInfo(long[], boolean, boolean, int)",
                "the JDK default; implemented by the platform ThreadMXBean only");
    }

    ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers);

    /**
     * JDK 10. As on the JDK, the default throws for implementations that predate the method;
     * the platform bean ({@code ManagementFactory.getThreadMXBean()}) overrides it.
     */
    default ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers, int maxDepth) {
        throw net.runelite.awt.AwtUnsupported.fail("java.lang.management.ThreadMXBean.dumpAllThreads(boolean, boolean, int)",
                "the JDK default; implemented by the platform ThreadMXBean only");
    }
}
