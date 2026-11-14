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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import net.runelite.awt.AwtUnsupported;

/**
 * The platform MXBeans on ART. Values come from {@code java.lang.Runtime}, {@code Thread},
 * procfs and {@code android.os.Debug}/{@code Process}/{@code SystemClock}; the android.os
 * calls go through reflection so that the same classes run on a desktop JDK for the host
 * contracts, where they fall back to procfs or report the JDK's "not available" values.
 */
final class ArtManagement {
    private ArtManagement() {}

    /** USER_HZ, the unit of the procfs tick counters: 100 on every Linux and Android ABI. */
    private static final long NANOS_PER_TICK = 10_000_000L;
    private static final StackTraceElement[] NO_FRAMES = new StackTraceElement[0];
    private static final Map<String, Object> METHODS = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    static final long PID = computePid();
    static final long START_TIME;
    private static final long START_NANOS;
    static {
        long now = System.currentTimeMillis(), nanos = System.nanoTime();
        long start = processStartMillis(now);
        if (start <= 0 || start > now) start = now;
        START_TIME = start;
        START_NANOS = nanos - (now - start) * 1_000_000L;
    }
    private static final List<String> INPUT_ARGUMENTS = inputArguments();

    static final RuntimeBean RUNTIME = new RuntimeBean();
    static final ThreadBean THREADS = new ThreadBean();
    static final MemoryBean MEMORY = new MemoryBean();
    static final OperatingSystemBean OPERATING_SYSTEM = new OperatingSystemBean();
    static final ClassLoadingBean CLASS_LOADING = new ClassLoadingBean();
    static final CompilationBean COMPILATION = new CompilationBean();
    static final List<GarbageCollectorMXBean> COLLECTORS =
            Collections.<GarbageCollectorMXBean>singletonList(new CollectorBean());
    static final List<MemoryPoolMXBean> POOLS = Collections.unmodifiableList(Arrays.<MemoryPoolMXBean>asList(
            new PoolBean(PoolBean.HEAP_POOL, MemoryType.HEAP), new PoolBean(PoolBean.NATIVE_POOL, MemoryType.NON_HEAP)));

    /** Every platform bean, singletons first, in registration order. */
    static List<PlatformManagedObject> all() {
        List<PlatformManagedObject> beans = new ArrayList<>();
        Collections.addAll(beans, CLASS_LOADING, COMPILATION, MEMORY, OPERATING_SYSTEM, RUNTIME, THREADS);
        beans.addAll(COLLECTORS);
        beans.addAll(POOLS);
        return beans;
    }

    static long pid() {
        return PID;
    }

    static ObjectName name(String text) {
        try {
            return new ObjectName(text);
        } catch (MalformedObjectNameException malformed) {
            throw new IllegalArgumentException(text, malformed);
        }
    }

    // ---- sources ----

    /** A static android.os method taking String arguments, or null where it does not exist (a desktop JDK). */
    private static Object android(String owner, String name, Object... arguments) {
        String key = owner + '#' + name + '#' + arguments.length;
        Object method = METHODS.get(key);
        if (method == null) {
            Class<?>[] types = new Class<?>[arguments.length];
            Arrays.fill(types, String.class);
            try {
                method = Class.forName(owner).getMethod(name, types);
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError | SecurityException absent) {
                method = MISSING;
            }
            METHODS.put(key, method);
        }
        if (method == MISSING) return null;
        try {
            return ((Method) method).invoke(null, arguments);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return null;
        }
    }

    private static long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException notNumeric) {
                return -1;
            }
        }
        return -1;
    }

    static String read(String path) {
        try (Reader in = new FileReader(path)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = in.read(buffer)) > 0) text.append(buffer, 0, count);
            return text.toString();
        } catch (IOException | SecurityException unreadable) {
            // Absent off Linux; SELinux denies some of procfs (/proc/stat) to Android apps.
            return null;
        }
    }

    /** The rest of the first line of {@code path} that starts with {@code prefix}. */
    private static String line(String path, String prefix) {
        String text = read(path);
        if (text == null) return null;
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length());
        }
        return null;
    }

    /** A "Key:   123 kB" entry of /proc/meminfo or /proc/self/status, in bytes, or -1. */
    private static long kilobytes(String path, String key) {
        String value = line(path, key + ":");
        if (value == null) return -1;
        try {
            return Long.parseLong(value.trim().split("\\s+")[0]) * 1024L;
        } catch (NumberFormatException notNumeric) {
            return -1;
        }
    }

    /**
     * The numeric fields of a /proc/.../stat file after the command name, so index i is field
     * i + 3 of proc(5): 11 utime, 12 stime, 19 starttime. Null when the file is unreadable.
     */
    private static long[] stat(String path) {
        String text = read(path);
        int close = text == null ? -1 : text.lastIndexOf(')');
        if (close < 0) return null;
        String[] fields = text.substring(close + 1).trim().split("\\s+");
        long[] values = new long[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try {
                values[i] = Long.parseLong(fields[i]);
            } catch (NumberFormatException notNumeric) {
                values[i] = 0;
            }
        }
        return values;
    }

    /** {total, idle} jiffies of the "cpu" line of /proc/stat, or null when it cannot be read. */
    private static long[] systemTicks() {
        String values = line("/proc/stat", "cpu ");
        if (values == null) return null;
        String[] fields = values.trim().split("\\s+");
        long total = 0, idle = 0;
        // user nice system idle iowait irq softirq steal; guest time is already inside user.
        for (int i = 0; i < fields.length && i < 8; i++) {
            long ticks;
            try {
                ticks = Long.parseLong(fields[i]);
            } catch (NumberFormatException notNumeric) {
                return null;
            }
            total += ticks;
            if (i == 3 || i == 4) idle += ticks;
        }
        return new long[] {total, idle};
    }

    private static long computePid() {
        try {
            return Long.parseLong(new File("/proc/self").getCanonicalFile().getName());
        } catch (IOException | NumberFormatException | SecurityException noProcfs) {
            // Not Linux: ask the platform instead.
        }
        Object pid = android("android.os.Process", "myPid");
        if (pid instanceof Integer) return (Integer) pid;
        try {
            Class<?> handle = Class.forName("java.lang.ProcessHandle");
            Object current = handle.getMethod("current").invoke(null);
            return ((Number) handle.getMethod("pid").invoke(current)).longValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return -1;
        }
    }

    private static long processStartMillis(long now) {
        long started = number(android("android.os.Process", "getStartElapsedRealtime"));
        long elapsed = number(android("android.os.SystemClock", "elapsedRealtime"));
        if (started > 0 && elapsed >= started) return now - (elapsed - started);
        long[] self = stat("/proc/self/stat");
        long boot = number(line("/proc/stat", "btime "));
        if (self != null && self.length > 19 && boot > 0) return boot * 1000L + self[19] * (NANOS_PER_TICK / 1_000_000L);
        return now;
    }

    /** The JVM options of /proc/self/cmdline; an app process forked from zygote has none. */
    private static List<String> inputArguments() {
        String commandLine = read("/proc/self/cmdline");
        List<String> arguments = new ArrayList<>();
        if (commandLine != null) {
            String[] parts = commandLine.split("\0");
            for (int i = 1; i < parts.length; i++) {
                String argument = parts[i];
                if (argument.equals("-cp") || argument.equals("-classpath") || argument.equals("--class-path")) {
                    i++;
                    continue;
                }
                if (!argument.startsWith("-") || argument.equals("-jar") || argument.equals("-m") || argument.equals("--module")) break;
                arguments.add(argument);
            }
        }
        return Collections.unmodifiableList(arguments);
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null ? fallback : value;
    }

    static long threadCpuNanos() {
        long nanos = number(android("android.os.Debug", "threadCpuTimeNanos"));
        if (nanos >= 0) return nanos;
        long[] self = stat("/proc/thread-self/stat");
        return self != null && self.length > 12 ? (self[11] + self[12]) * NANOS_PER_TICK : -1;
    }

    static long threadUserNanos() {
        long[] self = stat("/proc/thread-self/stat");
        return self != null && self.length > 11 ? self[11] * NANOS_PER_TICK : -1;
    }

    static long processCpuNanos() {
        long[] self = stat("/proc/self/stat");
        if (self != null && self.length > 12) return (self[11] + self[12]) * NANOS_PER_TICK;
        long millis = number(android("android.os.Process", "getElapsedCpuTime"));
        return millis >= 0 ? millis * 1_000_000L : -1;
    }

    static MemoryUsage heapUsage() {
        Runtime runtime = Runtime.getRuntime();
        long committed = runtime.totalMemory();
        long used = Math.max(0, Math.min(committed, committed - runtime.freeMemory()));
        long max = runtime.maxMemory();
        if (max == Long.MAX_VALUE || max < committed) max = -1;
        return new MemoryUsage(-1, used, committed, max);
    }

    static MemoryUsage nativeUsage() {
        long used = number(android("android.os.Debug", "getNativeHeapAllocatedSize"));
        long committed = number(android("android.os.Debug", "getNativeHeapSize"));
        if (used < 0 || committed < 0) return new MemoryUsage(-1, 0, 0, -1);
        return new MemoryUsage(-1, used, Math.max(used, committed), -1);
    }

    // ---- threads ----

    static Thread[] liveThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) root = root.getParent();
        Thread[] threads = new Thread[root.activeCount() + 8];
        int count;
        while ((count = root.enumerate(threads, true)) >= threads.length) threads = new Thread[threads.length * 2];
        return Arrays.copyOf(threads, count);
    }

    /** Drops the frames of the dump itself from the current thread's trace, then keeps maxDepth frames. */
    private static StackTraceElement[] trace(Thread thread, StackTraceElement[] full, int maxDepth) {
        int from = 0;
        if (thread == Thread.currentThread()) {
            while (from < full.length && internal(full[from].getClassName())) from++;
        }
        int length = (int) Math.min((long) full.length - from, maxDepth);
        return Arrays.copyOfRange(full, from, from + length);
    }

    private static boolean internal(String className) {
        return className.equals("java.lang.Thread") || className.equals("dalvik.system.VMStack")
                || className.startsWith("java.lang.management.");
    }

    private static ThreadInfo info(Thread thread, StackTraceElement[] full, int maxDepth) {
        Thread.State state = thread.getState();
        if (state == Thread.State.NEW || state == Thread.State.TERMINATED) return null;
        return new ThreadInfo(thread, state, trace(thread, full, maxDepth));
    }

    private static UnsupportedOperationException unsupported(String member, String reason) {
        return AwtUnsupported.fail("java.lang.management." + member, reason);
    }

    static final class RuntimeBean implements RuntimeMXBean {
        private final ObjectName objectName = name(ManagementFactory.RUNTIME_MXBEAN_NAME);
        private volatile String name;

        @Override public ObjectName getObjectName() { return objectName; }

        /** {@code pid@hostname}, as on the JDK. */
        @Override public String getName() {
            String result = name;
            if (result == null) {
                String host;
                try {
                    host = java.net.InetAddress.getLocalHost().getHostName();
                } catch (IOException | RuntimeException unresolved) {
                    host = "localhost";
                }
                name = result = PID + "@" + host;
            }
            return result;
        }

        @Override public long getPid() { return PID; }
        @Override public String getVmName() { return property("java.vm.name", "Dalvik"); }
        @Override public String getVmVendor() { return property("java.vm.vendor", "The Android Project"); }
        @Override public String getVmVersion() { return property("java.vm.version", ""); }
        @Override public String getSpecName() { return property("java.vm.specification.name", "Java Virtual Machine Specification"); }
        @Override public String getSpecVendor() { return property("java.vm.specification.vendor", "Oracle Corporation"); }
        @Override public String getSpecVersion() { return property("java.vm.specification.version", "1.8"); }
        @Override public String getManagementSpecVersion() { return "2.0"; }
        @Override public String getClassPath() { return property("java.class.path", ""); }
        @Override public String getLibraryPath() { return property("java.library.path", ""); }
        @Override public boolean isBootClassPathSupported() { return false; }
        @Override public String getBootClassPath() {
            throw unsupported("RuntimeMXBean.getBootClassPath()", "isBootClassPathSupported() is false on ART");
        }
        @Override public List<String> getInputArguments() { return INPUT_ARGUMENTS; }
        @Override public long getUptime() { return (System.nanoTime() - START_NANOS) / 1_000_000L; }
        @Override public long getStartTime() { return START_TIME; }

        /** The String-valued system properties, as the JDK reports them. */
        @Override public Map<String, String> getSystemProperties() {
            Map<String, String> properties = new HashMap<>();
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    properties.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
            return properties;
        }
    }

    /**
     * Threads from the root thread group and {@code Thread.getAllStackTraces}. ART exposes no
     * monitor ownership, so lock data is empty and {@code lockedMonitors}/{@code lockedSynchronizers}
     * requests are answered without it rather than refused, which keeps desktop thread-dump code
     * working; the deadlock finders return null ("none found") for the same reason.
     */
    static final class ThreadBean implements ThreadMXBean {
        private final ObjectName objectName = name(ManagementFactory.THREAD_MXBEAN_NAME);
        private int peak;
        private volatile boolean cpuTimeEnabled = true;

        @Override public ObjectName getObjectName() { return objectName; }

        private Thread[] snapshot() {
            Thread[] threads = liveThreads();
            synchronized (this) {
                peak = Math.max(peak, threads.length);
            }
            return threads;
        }

        @Override public int getThreadCount() { return snapshot().length; }

        @Override public int getPeakThreadCount() {
            snapshot();
            synchronized (this) {
                return peak;
            }
        }

        @Override public void resetPeakThreadCount() {
            Thread[] threads = liveThreads();
            synchronized (this) {
                peak = threads.length;
            }
        }

        /** The highest thread id handed out so far: ART numbers threads sequentially from 1. */
        @Override public long getTotalStartedThreadCount() {
            long highest = 0;
            for (Thread thread : snapshot()) highest = Math.max(highest, thread.getId());
            return highest;
        }

        @Override public int getDaemonThreadCount() {
            int count = 0;
            for (Thread thread : snapshot()) if (thread.isDaemon()) count++;
            return count;
        }

        @Override public long[] getAllThreadIds() {
            Thread[] threads = snapshot();
            long[] ids = new long[threads.length];
            for (int i = 0; i < threads.length; i++) ids[i] = threads[i].getId();
            return ids;
        }

        @Override public ThreadInfo getThreadInfo(long id) { return getThreadInfo(id, 0); }
        @Override public ThreadInfo[] getThreadInfo(long[] ids) { return getThreadInfo(ids, 0); }
        @Override public ThreadInfo getThreadInfo(long id, int maxDepth) { return getThreadInfo(new long[] {id}, maxDepth)[0]; }

        @Override public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) {
            if (maxDepth < 0) throw new IllegalArgumentException("Invalid maxDepth parameter: " + maxDepth);
            for (long id : ids) {
                if (id <= 0) throw new IllegalArgumentException("Invalid thread ID parameter: " + id);
            }
            Map<Long, Thread> byId = new HashMap<>();
            for (Thread thread : snapshot()) byId.put(thread.getId(), thread);
            ThreadInfo[] infos = new ThreadInfo[ids.length];
            for (int i = 0; i < ids.length; i++) {
                Thread thread = byId.get(ids[i]);
                if (thread != null) infos[i] = info(thread, maxDepth == 0 ? NO_FRAMES : thread.getStackTrace(), maxDepth);
            }
            return infos;
        }

        @Override public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers) {
            return getThreadInfo(ids, Integer.MAX_VALUE);
        }

        @Override public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers, int maxDepth) {
            return getThreadInfo(ids, maxDepth);
        }

        @Override public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers) {
            return dumpAllThreads(lockedMonitors, lockedSynchronizers, Integer.MAX_VALUE);
        }

        @Override public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers, int maxDepth) {
            if (maxDepth < 0) throw new IllegalArgumentException("Invalid maxDepth parameter: " + maxDepth);
            Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
            synchronized (this) {
                peak = Math.max(peak, all.size());
            }
            List<ThreadInfo> infos = new ArrayList<>(all.size());
            for (Map.Entry<Thread, StackTraceElement[]> entry : all.entrySet()) {
                ThreadInfo info = info(entry.getKey(), entry.getValue(), maxDepth);
                if (info != null) infos.add(info);
            }
            return infos.toArray(new ThreadInfo[0]);
        }

        @Override public boolean isThreadContentionMonitoringSupported() { return false; }
        @Override public boolean isThreadContentionMonitoringEnabled() { return false; }
        @Override public void setThreadContentionMonitoringEnabled(boolean enable) {
            throw unsupported("ThreadMXBean.setThreadContentionMonitoringEnabled(boolean)", "ART keeps no per-thread contention statistics");
        }

        /** Other threads' CPU time is not measurable: Java thread ids do not map to kernel tids. */
        @Override public boolean isThreadCpuTimeSupported() { return false; }
        @Override public boolean isCurrentThreadCpuTimeSupported() { return threadCpuNanos() >= 0; }
        @Override public boolean isThreadCpuTimeEnabled() { return cpuTimeEnabled; }

        @Override public void setThreadCpuTimeEnabled(boolean enable) {
            if (!isCurrentThreadCpuTimeSupported()) {
                throw unsupported("ThreadMXBean.setThreadCpuTimeEnabled(boolean)", "no thread CPU clock is readable");
            }
            cpuTimeEnabled = enable;
        }

        @Override public long getCurrentThreadCpuTime() {
            long nanos = threadCpuNanos();
            if (nanos < 0) throw unsupported("ThreadMXBean.getCurrentThreadCpuTime()", "no thread CPU clock is readable");
            return cpuTimeEnabled ? nanos : -1;
        }

        @Override public long getCurrentThreadUserTime() {
            if (!isCurrentThreadCpuTimeSupported()) {
                throw unsupported("ThreadMXBean.getCurrentThreadUserTime()", "no thread CPU clock is readable");
            }
            return cpuTimeEnabled ? threadUserNanos() : -1;
        }

        @Override public long getThreadCpuTime(long id) {
            if (id <= 0) throw new IllegalArgumentException("Invalid thread ID parameter: " + id);
            if (id == Thread.currentThread().getId()) return getCurrentThreadCpuTime();
            throw unsupported("ThreadMXBean.getThreadCpuTime(long)", "ART measures CPU time of the current thread only");
        }

        @Override public long getThreadUserTime(long id) {
            if (id <= 0) throw new IllegalArgumentException("Invalid thread ID parameter: " + id);
            if (id == Thread.currentThread().getId()) return getCurrentThreadUserTime();
            throw unsupported("ThreadMXBean.getThreadUserTime(long)", "ART measures CPU time of the current thread only");
        }

        @Override public long[] findMonitorDeadlockedThreads() { return null; }
        @Override public long[] findDeadlockedThreads() { return null; }
        @Override public boolean isObjectMonitorUsageSupported() { return false; }
        @Override public boolean isSynchronizerUsageSupported() { return false; }
    }

    static final class MemoryBean implements MemoryMXBean {
        private final ObjectName objectName = name(ManagementFactory.MEMORY_MXBEAN_NAME);
        private volatile boolean verbose;

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public MemoryUsage getHeapMemoryUsage() { return heapUsage(); }
        /** The native (malloc) heap, ART's counterpart of the JDK's non-heap memory. */
        @Override public MemoryUsage getNonHeapMemoryUsage() { return nativeUsage(); }
        /** 0: ART does not expose the length of its finalizer queue. */
        @Override public int getObjectPendingFinalizationCount() { return 0; }
        @Override public boolean isVerbose() { return verbose; }
        /** Recorded only: ART has no run-time switch for GC logging. */
        @Override public void setVerbose(boolean value) { verbose = value; }
        @Override public void gc() { System.gc(); }
    }

    static final class PoolBean implements MemoryPoolMXBean {
        static final String HEAP_POOL = "ART heap", NATIVE_POOL = "Native heap";
        private final String name;
        private final MemoryType type;
        private final ObjectName objectName;
        private MemoryUsage peak;

        PoolBean(String name, MemoryType type) {
            this.name = name;
            this.type = type;
            this.objectName = name(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",name=" + name);
        }

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public String getName() { return name; }
        @Override public MemoryType getType() { return type; }
        @Override public boolean isValid() { return true; }

        @Override public String[] getMemoryManagerNames() {
            return type == MemoryType.HEAP ? new String[] {CollectorBean.NAME} : new String[0];
        }

        @Override public MemoryUsage getUsage() {
            MemoryUsage usage = type == MemoryType.HEAP ? heapUsage() : nativeUsage();
            synchronized (this) {
                if (peak == null || usage.getUsed() > peak.getUsed()) peak = usage;
            }
            return usage;
        }

        /** The highest usage this pool was sampled at: ART tracks no peak itself. */
        @Override public MemoryUsage getPeakUsage() {
            getUsage();
            synchronized (this) {
                return peak;
            }
        }

        @Override public void resetPeakUsage() {
            MemoryUsage usage = type == MemoryType.HEAP ? heapUsage() : nativeUsage();
            synchronized (this) {
                peak = usage;
            }
        }

        /** null: ART reports no per-pool usage after a collection. */
        @Override public MemoryUsage getCollectionUsage() { return null; }

        @Override public boolean isUsageThresholdSupported() { return false; }
        @Override public long getUsageThreshold() { throw threshold("getUsageThreshold()"); }
        @Override public void setUsageThreshold(long threshold) { throw threshold("setUsageThreshold(long)"); }
        @Override public boolean isUsageThresholdExceeded() { throw threshold("isUsageThresholdExceeded()"); }
        @Override public long getUsageThresholdCount() { throw threshold("getUsageThresholdCount()"); }
        @Override public boolean isCollectionUsageThresholdSupported() { return false; }
        @Override public long getCollectionUsageThreshold() { throw threshold("getCollectionUsageThreshold()"); }
        @Override public void setCollectionUsageThreshold(long threshold) { throw threshold("setCollectionUsageThreshold(long)"); }
        @Override public boolean isCollectionUsageThresholdExceeded() { throw threshold("isCollectionUsageThresholdExceeded()"); }
        @Override public long getCollectionUsageThresholdCount() { throw threshold("getCollectionUsageThresholdCount()"); }

        private static UnsupportedOperationException threshold(String member) {
            return unsupported("MemoryPoolMXBean." + member, "ART memory pools have no usage thresholds");
        }
    }

    static final class CollectorBean implements GarbageCollectorMXBean {
        static final String NAME = "ART";
        private final ObjectName objectName = name(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=" + NAME);

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public String getName() { return NAME; }
        @Override public boolean isValid() { return true; }
        @Override public String[] getMemoryPoolNames() { return new String[] {PoolBean.HEAP_POOL}; }
        /** {@code art.gc.gc-count} of {@code Debug.getRuntimeStat}, -1 where it is not reported. */
        @Override public long getCollectionCount() { return number(android("android.os.Debug", "getRuntimeStat", "art.gc.gc-count")); }
        /** {@code art.gc.gc-time} (milliseconds), -1 where it is not reported. */
        @Override public long getCollectionTime() { return number(android("android.os.Debug", "getRuntimeStat", "art.gc.gc-time")); }
    }

    /**
     * ART counts the classes loaded right now only: the total equals the loaded count and the
     * unloaded count is 0. The verbose flag is recorded; ART has no run-time class-load logging.
     */
    static final class ClassLoadingBean implements ClassLoadingMXBean {
        private final ObjectName objectName = name(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
        private volatile boolean verbose;

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public int getLoadedClassCount() { return (int) Math.max(0, number(android("android.os.Debug", "getLoadedClassCount"))); }
        @Override public long getTotalLoadedClassCount() { return getLoadedClassCount(); }
        @Override public long getUnloadedClassCount() { return 0; }
        @Override public boolean isVerbose() { return verbose; }
        @Override public void setVerbose(boolean value) { verbose = value; }
    }

    /** ART's JIT; it keeps no compilation-time total, so time monitoring is unsupported. */
    static final class CompilationBean implements CompilationMXBean {
        private final ObjectName objectName = name(ManagementFactory.COMPILATION_MXBEAN_NAME);

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public String getName() { return "ART JIT"; }
        @Override public boolean isCompilationTimeMonitoringSupported() { return false; }
        @Override public long getTotalCompilationTime() {
            throw unsupported("CompilationMXBean.getTotalCompilationTime()", "ART keeps no JIT time total");
        }
    }

    /**
     * Linux procfs: /proc/loadavg, /proc/stat, /proc/meminfo, /proc/self/stat and
     * /proc/self/status. The CPU loads are the share of CPU time since the previous call
     * (since boot or process start on the first), as the JDK computes them on Linux.
     */
    static final class OperatingSystemBean implements com.sun.management.OperatingSystemMXBean {
        /** Calls closer together than this reuse the previous load: one tick is 10 ms. */
        private static final long MIN_INTERVAL_NANOS = 20_000_000L;
        private final ObjectName objectName = name(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
        private long systemTotal, systemIdle;
        private double systemLoad = -1;
        private long processCpu, processWall = START_NANOS;
        private double processLoad = -1;

        @Override public ObjectName getObjectName() { return objectName; }
        @Override public String getName() { return property("os.name", "Linux"); }
        @Override public String getArch() { return property("os.arch", ""); }
        @Override public String getVersion() { return property("os.version", ""); }
        @Override public int getAvailableProcessors() { return Runtime.getRuntime().availableProcessors(); }

        @Override public double getSystemLoadAverage() {
            String text = read("/proc/loadavg");
            if (text == null) return -1.0;
            try {
                return Double.parseDouble(text.trim().split("\\s+")[0]);
            } catch (NumberFormatException notNumeric) {
                return -1.0;
            }
        }

        @Override public long getCommittedVirtualMemorySize() { return kilobytes("/proc/self/status", "VmSize"); }
        @Override public long getTotalSwapSpaceSize() { return kilobytes("/proc/meminfo", "SwapTotal"); }
        @Override public long getFreeSwapSpaceSize() { return kilobytes("/proc/meminfo", "SwapFree"); }
        @Override public long getProcessCpuTime() { return processCpuNanos(); }
        @Override public long getFreePhysicalMemorySize() { return kilobytes("/proc/meminfo", "MemFree"); }
        @Override public long getTotalPhysicalMemorySize() { return kilobytes("/proc/meminfo", "MemTotal"); }
        public long getFreeMemorySize() { return getFreePhysicalMemorySize(); }
        public long getTotalMemorySize() { return getTotalPhysicalMemorySize(); }
        public double getCpuLoad() { return getSystemCpuLoad(); }

        @Override public synchronized double getSystemCpuLoad() {
            long[] ticks = systemTicks();
            if (ticks == null) return -1.0;
            long total = ticks[0] - systemTotal, idle = ticks[1] - systemIdle;
            if (total * NANOS_PER_TICK < MIN_INTERVAL_NANOS && systemLoad >= 0) return systemLoad;
            if (total <= 0) return systemLoad >= 0 ? systemLoad : 0.0;
            systemTotal = ticks[0];
            systemIdle = ticks[1];
            systemLoad = clamp(1.0 - (double) idle / total);
            return systemLoad;
        }

        @Override public synchronized double getProcessCpuLoad() {
            long cpu = processCpuNanos();
            if (cpu < 0) return -1.0;
            long wall = System.nanoTime(), elapsed = wall - processWall;
            if (elapsed < MIN_INTERVAL_NANOS && processLoad >= 0) return processLoad;
            if (elapsed <= 0) return processLoad >= 0 ? processLoad : 0.0;
            processLoad = clamp((double) (cpu - processCpu) / ((double) elapsed * getAvailableProcessors()));
            processCpu = cpu;
            processWall = wall;
            return processLoad;
        }

        private static double clamp(double load) {
            return load < 0 ? 0.0 : load > 1 ? 1.0 : load;
        }
    }
}
