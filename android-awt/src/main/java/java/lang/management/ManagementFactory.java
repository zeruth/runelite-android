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

public class ManagementFactory {
    private ManagementFactory() {}

    /** In-process management registry; Android does not expose a remote JMX connector. */
    public static javax.management.MBeanServer getPlatformMBeanServer() {
        return net.runelite.awt.impl.StubMBeanServer.INSTANCE;
    }

    public static RuntimeMXBean getRuntimeMXBean() {
        return new StubRuntimeMXBean();
    }

    public static java.util.List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        // ART exposes total heap usage, but no managed pool with peak/reset semantics.
        return java.util.Collections.emptyList();
    }

    public static java.util.List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return java.util.Collections.singletonList(GC);
    }

    public static MemoryMXBean getMemoryMXBean() {
        return new StubMemoryMXBean();
    }

    public static ThreadMXBean getThreadMXBean() {
        return THREADS;
    }

    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return new StubOperatingSystemMXBean();
    }

    public static ClassLoadingMXBean getClassLoadingMXBean() {
        return new StubClassLoadingMXBean();
    }

    public static java.util.List<java.lang.management.MemoryManagerMXBean> getMemoryManagerMXBeans() {
        return java.util.Collections.singletonList(GC);
    }

    private static final long START_TIME = System.currentTimeMillis();
    private static final long START_NANOS = System.nanoTime();
    private static final ThreadMXBean THREADS = new StubThreadMXBean();
    private static final GarbageCollectorMXBean GC = new ArtGarbageCollector();

    private static final class ArtGarbageCollector implements GarbageCollectorMXBean {
        @Override public String getName() { return "ART"; }
        @Override public boolean isValid() { return true; }
        @Override public String[] getMemoryPoolNames() { return new String[0]; }
        private long statistic(String name) {
            String value = android.os.Debug.getRuntimeStat(name);
            if (value == null) return -1;
            try { return Long.parseLong(value); } catch (NumberFormatException unavailable) { return -1; }
        }
        @Override public long getCollectionCount() { return statistic("art.gc.gc-count"); }
        @Override public long getCollectionTime() { return statistic("art.gc.gc-time"); }
    }

    private static final class StubRuntimeMXBean implements RuntimeMXBean {
        @Override public String getName() { return android.os.Process.myPid() + "@android"; }
        @Override public String getVmName() { return "Dalvik"; }
        @Override public String getVmVendor() { return "Android"; }
        @Override public String getVmVersion() { return android.os.Build.VERSION.RELEASE; }
        @Override public String getSpecName() { return "Java Virtual Machine Specification"; }
        @Override public String getSpecVendor() { return "Oracle"; }
        @Override public String getSpecVersion() { return "1.8"; }
        @Override public String getManagementSpecVersion() { return "2.0"; }
        @Override public String getClassPath() { return System.getProperty("java.class.path", ""); }
        @Override public String getLibraryPath() { return System.getProperty("java.library.path", ""); }
        @Override public boolean isBootClassPathSupported() { return false; }
        @Override public String getBootClassPath() { throw new UnsupportedOperationException(); }
        @Override public java.util.List<String> getInputArguments() { return java.util.Collections.emptyList(); }
        @Override public long getUptime() { return (System.nanoTime() - START_NANOS) / 1_000_000; }
        @Override public long getStartTime() { return START_TIME; }
        @Override public java.util.Map<String, String> getSystemProperties() {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            for (java.util.Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
                m.put(e.getKey().toString(), e.getValue() == null ? "" : e.getValue().toString());
            }
            return m;
        }
        @Override public java.util.Map<String, String> getKeyMap() { return getSystemProperties(); }
    }

    private static final class StubMemoryMXBean implements MemoryMXBean {
        @Override public MemoryUsage getHeapMemoryUsage() {
            Runtime runtime = Runtime.getRuntime(); long committed = runtime.totalMemory();
            return new MemoryUsage(-1, committed - runtime.freeMemory(), committed, runtime.maxMemory());
        }
        @Override public MemoryUsage getNonHeapMemoryUsage() {
            return new MemoryUsage(-1, android.os.Debug.getNativeHeapAllocatedSize(), android.os.Debug.getNativeHeapSize(), -1);
        }
        @Override public int getObjectPendingFinalizationCount() { return 0; }
        @Override public boolean isVerbose() { return false; }
        @Override public void setVerbose(boolean value) {}
        @Override public void gc() { System.gc(); }
    }

    private static final class StubThreadMXBean implements ThreadMXBean {
        private int peak;
        private synchronized java.util.Set<Thread> snapshot() {
            java.util.Set<Thread> threads = Thread.getAllStackTraces().keySet(); peak = Math.max(peak, threads.size()); return threads;
        }
        @Override public int getThreadCount() { return snapshot().size(); }
        @Override public int getPeakThreadCount() { snapshot(); return peak; }
        @Override public long getTotalStartedThreadCount() { throw new UnsupportedOperationException("ART does not expose total started thread count"); }
        @Override public int getDaemonThreadCount() { int count = 0; for (Thread thread : snapshot()) if (thread.isDaemon()) count++; return count; }
        @Override public long[] getAllThreadIds() { return snapshot().stream().mapToLong(Thread::getId).toArray(); }
        @Override public long getCurrentThreadCpuTime() { return android.os.Debug.threadCpuTimeNanos(); }
        @Override public long getCurrentThreadUserTime() { return -1; }
        @Override public long getThreadCpuTime(long id) { if (id <= 0) throw new IllegalArgumentException("invalid thread id"); return id == Thread.currentThread().getId() ? getCurrentThreadCpuTime() : -1; }
        @Override public long getThreadUserTime(long id) { return -1; }
        @Override public boolean isThreadCpuTimeSupported() { return false; }
        @Override public boolean isThreadCpuTimeEnabled() { return false; }
        @Override public void setThreadCpuTimeEnabled(boolean enable) {}
    }

    private static final class StubOperatingSystemMXBean implements OperatingSystemMXBean {
        @Override public String getName() { return "Android"; }
        @Override public String getArch() { return android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : "unknown"; }
        @Override public String getVersion() { return android.os.Build.VERSION.RELEASE; }
        @Override public int getAvailableProcessors() { return Runtime.getRuntime().availableProcessors(); }
        @Override public double getSystemLoadAverage() { return -1.0; }
    }

    private static final class StubClassLoadingMXBean implements ClassLoadingMXBean {
        @Override public long getTotalLoadedClassCount() { throw new UnsupportedOperationException("ART does not expose total loaded class count"); }
        @Override public int getLoadedClassCount() { return android.os.Debug.getLoadedClassCount(); }
        @Override public long getUnloadedClassCount() { throw new UnsupportedOperationException("ART does not expose unloaded class count"); }
        @Override public boolean isVerbose() { return false; }
        @Override public void setVerbose(boolean value) {}
    }
}
