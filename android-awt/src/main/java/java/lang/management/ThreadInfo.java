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

/**
 * A snapshot of one thread, built from {@code Thread.getState()} and {@code getStackTrace()}.
 * ART exposes no monitor-ownership or contention data to Java code, so the lock fields are
 * empty ({@code null} lock, owner id -1, no locked monitors or synchronizers) and the
 * blocked/waited counts and times are -1, the JDK's "not available" value.
 */
public class ThreadInfo {
    private static final int MAX_FRAMES = 8;
    private static final MonitorInfo[] NO_MONITORS = new MonitorInfo[0];
    private static final LockInfo[] NO_SYNCHRONIZERS = new LockInfo[0];

    private final String threadName;
    private final long threadId;
    private final Thread.State threadState;
    private final boolean daemon;
    private final int priority;
    private final boolean inNative;
    private final StackTraceElement[] stackTrace;

    ThreadInfo(Thread thread, Thread.State state, StackTraceElement[] stackTrace) {
        this.threadName = thread.getName();
        this.threadId = thread.getId();
        this.threadState = state;
        this.daemon = thread.isDaemon();
        this.priority = thread.getPriority();
        this.stackTrace = stackTrace;
        this.inNative = stackTrace.length > 0 && stackTrace[0].isNativeMethod();
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public Thread.State getThreadState() {
        return threadState;
    }

    /** -1: contention monitoring is not supported on ART. */
    public long getBlockedTime() {
        return -1;
    }

    /** -1: ART does not count monitor contention per thread. */
    public long getBlockedCount() {
        return -1;
    }

    /** -1: contention monitoring is not supported on ART. */
    public long getWaitedTime() {
        return -1;
    }

    /** -1: ART does not count waits per thread. */
    public long getWaitedCount() {
        return -1;
    }

    public LockInfo getLockInfo() {
        return null;
    }

    public String getLockName() {
        return null;
    }

    public long getLockOwnerId() {
        return -1;
    }

    public String getLockOwnerName() {
        return null;
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace.clone();
    }

    public boolean isSuspended() {
        return false;
    }

    public boolean isInNative() {
        return inNative;
    }

    // JDK 9
    public boolean isDaemon() {
        return daemon;
    }

    // JDK 9
    public int getPriority() {
        return priority;
    }

    public MonitorInfo[] getLockedMonitors() {
        return NO_MONITORS;
    }

    public LockInfo[] getLockedSynchronizers() {
        return NO_SYNCHRONIZERS;
    }

    /** The JDK's thread-dump format: a header line, then at most eight frames. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("\"").append(threadName).append('"')
                .append(daemon ? " daemon" : "")
                .append(" prio=").append(priority)
                .append(" Id=").append(threadId).append(' ').append(threadState);
        if (inNative) sb.append(" (in native)");
        sb.append('\n');
        int i = 0;
        for (; i < stackTrace.length && i < MAX_FRAMES; i++) {
            sb.append("\tat ").append(stackTrace[i]).append('\n');
        }
        if (i < stackTrace.length) sb.append("\t...").append('\n');
        sb.append('\n');
        return sb.toString();
    }
}
