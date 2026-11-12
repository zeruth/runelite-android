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
// JDK 9+ (9): the JDK 17 java.base API of java.lang.ProcessHandle (java.base is not in the
// sparse jdk17u checkout); the members and their exceptions follow it, the bodies read /proc.
package java.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * ProcessHandle identifies and provides control of native processes (JDK 9+). Android's
 * {@code android.jar} has no such class, so this shadow supplies it over {@code /proc}: the
 * current process is {@code android.os.Process.myPid()}, other processes are the numeric
 * entries of {@code /proc} the sandbox lets the app see (on Android, the processes of the
 * app's own uid).
 *
 * <p>The Android APIs are reached reflectively so the class also compiles, and is patched
 * over {@code java.base}, in the host contract run; there the pid falls back to
 * {@code /proc/self} and then to the {@code RuntimeMXBean} name.
 */
public interface ProcessHandle extends Comparable<ProcessHandle> {

    /**
     * Returns the native process ID of the process.
     *
     * @return the native process ID of the process
     */
    long pid();

    /**
     * Returns an {@code Optional<ProcessHandle>} for an existing native process.
     *
     * @param pid a native process ID
     * @return an {@code Optional<ProcessHandle>} of the PID for the process;
     *         the {@code Optional} is empty if the process does not exist
     */
    static Optional<ProcessHandle> of(long pid) {
        return Handle.get(pid);
    }

    /**
     * Returns a ProcessHandle for the current process.
     *
     * @return a ProcessHandle for the current process
     */
    static ProcessHandle current() {
        return Handle.CURRENT;
    }

    /**
     * Returns an {@code Optional<ProcessHandle>} for the parent process; empty when the
     * parent is unknown, gone, or started after this process (its pid was reused).
     *
     * @return an {@code Optional<ProcessHandle>} of the parent process
     */
    Optional<ProcessHandle> parent();

    /**
     * Returns a snapshot of the current direct children of the process.
     *
     * @return a sequential Stream of ProcessHandles for processes that are direct children
     */
    Stream<ProcessHandle> children();

    /**
     * Returns a snapshot of the descendants of the process, children first.
     *
     * @return a sequential Stream of ProcessHandles for processes that are descendants
     */
    Stream<ProcessHandle> descendants();

    /**
     * Returns a snapshot of all processes visible to the current process.
     *
     * @return a Stream of ProcessHandles for all processes
     */
    static Stream<ProcessHandle> allProcesses() {
        return Handle.all();
    }

    /**
     * Returns a snapshot of information about the process.
     *
     * @return a snapshot of information about the process, always non-null
     */
    Info info();

    /**
     * Information snapshot about the process. Each attribute is empty when it is not
     * available, as on the JDK.
     */
    interface Info {
        /**
         * Returns the executable pathname of the process.
         *
         * @return an {@code Optional<String>} of the executable pathname of the process
         */
        Optional<String> command();

        /**
         * Returns the command line of the process.
         *
         * @return an {@code Optional<String>} of the command line of the process
         */
        Optional<String> commandLine();

        /**
         * Returns an array of Strings of the arguments of the process.
         *
         * @return an {@code Optional<String[]>} of the arguments of the process
         */
        Optional<String[]> arguments();

        /**
         * Returns the start time of the process.
         *
         * @return an {@code Optional<Instant>} of the start time of the process
         */
        Optional<Instant> startInstant();

        /**
         * Returns the total cputime accumulated of the process.
         *
         * @return an {@code Optional<Duration>} for the accumulated total cputime
         */
        Optional<Duration> totalCpuDuration();

        /**
         * Return the user of the process.
         *
         * @return an {@code Optional<String>} for the user of the process
         */
        Optional<String> user();
    }

    /**
     * Returns a {@code CompletableFuture<ProcessHandle>} for the termination of the process.
     *
     * @return a new {@code CompletableFuture<ProcessHandle>} for the ProcessHandle
     * @throws IllegalStateException if the process is the current process
     */
    CompletableFuture<ProcessHandle> onExit();

    /**
     * Returns {@code true} if the implementation of {@link #destroy} normally terminates the
     * process; true on Linux and Android, where it sends {@code SIGTERM}.
     *
     * @return {@code true} if the implementation of {@link #destroy} normally terminates the process
     */
    boolean supportsNormalTermination();

    /**
     * Requests the process to be killed ({@code SIGTERM}). The kernel only lets an app
     * signal processes of its own uid, which on Android are the app's own child processes.
     *
     * @return {@code true} if termination was successfully requested, otherwise {@code false}
     * @throws IllegalStateException if the process is the current process
     */
    boolean destroy();

    /**
     * Requests the process to be killed forcibly ({@code SIGKILL}).
     *
     * @return {@code true} if termination was successfully requested, otherwise {@code false}
     * @throws IllegalStateException if the process is the current process
     */
    boolean destroyForcibly();

    /**
     * Tests whether the process represented by this {@code ProcessHandle} is alive.
     *
     * @return {@code true} if the process represented by this {@code ProcessHandle} object
     *         has not yet terminated
     */
    boolean isAlive();

    /**
     * Returns a hash code value for this ProcessHandle.
     *
     * @return a hash code value for this object
     */
    @Override
    int hashCode();

    /**
     * Returns {@code true} if {@code other} is a non-null ProcessHandle of the same
     * process: the same pid and, where both are known, the same start time.
     *
     * @param other another object
     * @return {@code true} if the {@code other} object is non-null, is of the same
     *         implementation, and represents the same system process
     */
    @Override
    boolean equals(Object other);

    /**
     * Compares this ProcessHandle with the specified ProcessHandle for order by pid.
     *
     * @param other the ProcessHandle to be compared
     * @return a negative integer, zero, or a positive integer as this object is less than,
     *         equal, or greater than the specified object
     * @throws NullPointerException if the specified object is null
     */
    @Override
    int compareTo(ProcessHandle other);

    /**
     * The one implementation, over {@code /proc/<pid>/stat}, {@code status}, {@code cmdline}
     * and {@code exe}. Not part of the JDK API; an interface can only nest public classes.
     */
    final class Handle implements ProcessHandle {
        // /proc/<pid>/stat fields kept by stat(), counted from 0 after the ")" of comm.
        static final int STATE = 0, PPID = 1, UTIME = 11, STIME = 12, START = 19;

        static final long CURRENT_PID = currentPid();
        static final Handle CURRENT = new Handle(CURRENT_PID, startTicks(CURRENT_PID));

        private static volatile long bootMillis = Long.MIN_VALUE;
        private static volatile long clockTicks;

        private final long pid;
        // Clock ticks since boot; 0 when /proc does not tell, which matches any start time.
        private final long start;

        Handle(long pid, long start) {
            this.pid = pid;
            this.start = start;
        }

        static Optional<ProcessHandle> get(long pid) {
            if (pid == CURRENT_PID) {
                return Optional.<ProcessHandle>of(CURRENT);
            }
            long[] stat = pid > 0 ? stat(pid) : null;
            return stat == null ? Optional.<ProcessHandle>empty()
                : Optional.<ProcessHandle>of(new Handle(pid, stat[START]));
        }

        static Stream<ProcessHandle> all() {
            List<ProcessHandle> all = new ArrayList<ProcessHandle>();
            for (Map.Entry<Long, long[]> entry : table().entrySet()) {
                all.add(handle(entry.getKey(), entry.getValue()));
            }
            if (all.isEmpty()) {
                all.add(CURRENT);
            }
            return all.stream();
        }

        private static ProcessHandle handle(long pid, long[] stat) {
            return pid == CURRENT_PID ? CURRENT : new Handle(pid, stat[START]);
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public Optional<ProcessHandle> parent() {
            long[] stat = stat(pid);
            if (stat == null || stat[PPID] <= 0) {
                return Optional.empty();
            }
            long[] parent = stat(stat[PPID]);
            // A parent that started after its child is a reused pid, as the JDK reports it.
            if (parent == null || parent[START] > stat[START]) {
                return Optional.empty();
            }
            return Optional.of(handle(stat[PPID], parent));
        }

        @Override
        public Stream<ProcessHandle> children() {
            return related(false);
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return related(true);
        }

        private Stream<ProcessHandle> related(boolean transitive) {
            Map<Long, long[]> table = table();
            List<ProcessHandle> found = new ArrayList<ProcessHandle>();
            Deque<Long> pending = new ArrayDeque<Long>();
            pending.add(pid);
            while (!pending.isEmpty()) {
                long next = pending.poll();
                for (Map.Entry<Long, long[]> entry : table.entrySet()) {
                    long child = entry.getKey();
                    if (entry.getValue()[PPID] == next && child != next) {
                        found.add(handle(child, entry.getValue()));
                        if (transitive) {
                            pending.add(child);
                        }
                    }
                }
            }
            return found.stream();
        }

        @Override
        public Info info() {
            return new ProcessInfo(pid);
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            if (pid == CURRENT_PID) {
                throw new IllegalStateException("onExit for current process not allowed");
            }
            final CompletableFuture<ProcessHandle> result = new CompletableFuture<ProcessHandle>();
            // No waitpid for a process java.lang.Process did not start: poll its /proc entry,
            // which disappears once the process is reaped.
            Thread waiter = new Thread(() -> {
                try {
                    while (!result.isDone() && isAlive()) {
                        Thread.sleep(100);
                    }
                    result.complete(this);
                } catch (InterruptedException interrupted) {
                    result.completeExceptionally(interrupted);
                }
            }, "process reaper");
            waiter.setDaemon(true);
            waiter.start();
            return result;
        }

        @Override
        public boolean supportsNormalTermination() {
            return File.separatorChar == '/';
        }

        @Override
        public boolean destroy() {
            return signal(false);
        }

        @Override
        public boolean destroyForcibly() {
            return signal(true);
        }

        private boolean signal(boolean force) {
            if (pid == CURRENT_PID) {
                throw new IllegalStateException("destroy of current process not allowed");
            }
            if (!isAlive()) {
                return false;
            }
            Object constant = constant("android.system.OsConstants", force ? "SIGKILL" : "SIGTERM");
            int signal = constant instanceof Integer ? (Integer) constant : force ? 9 : 15;
            try {
                Class.forName("android.system.Os").getMethod("kill", int.class, int.class)
                    .invoke(null, (int) pid, signal);
                return true;
            } catch (java.lang.reflect.InvocationTargetException refused) {
                return false; // ErrnoException: EPERM for another uid, ESRCH when already gone
            } catch (ReflectiveOperationException | LinkageError notAndroid) {
                // Host JVM: the kill(1) binary does the same.
            }
            try {
                java.lang.Process kill = new ProcessBuilder("kill", "-" + signal, Long.toString(pid))
                    .redirectErrorStream(true).start();
                kill.getInputStream().close();
                return kill.waitFor() == 0;
            } catch (IOException noKill) {
                return false;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public boolean isAlive() {
            if (pid == CURRENT_PID) {
                return true;
            }
            long[] stat = stat(pid);
            return stat != null && (start == 0 || stat[START] == 0 || stat[START] == start);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pid);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Handle)) {
                return false;
            }
            Handle that = (Handle) other;
            return pid == that.pid && (start == 0 || that.start == 0 || start == that.start);
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }

        @Override
        public String toString() {
            return String.valueOf(pid);
        }

        // -- /proc and the platform, all failure-tolerant ----------------------------------

        private static long currentPid() {
            Object pid = call("android.os.Process", "myPid", null);
            if (pid instanceof Integer) {
                return (Integer) pid;
            }
            try {
                return Long.parseLong(Files.readSymbolicLink(Paths.get("/proc/self")).toString());
            } catch (IOException | RuntimeException noProc) {
                // not Linux: ask the management bean, "pid@host" on every JDK
            }
            Object name = call("java.lang.management.RuntimeMXBean", "getName", runtimeBean());
            if (name instanceof String) {
                String text = (String) name;
                int at = text.indexOf('@');
                try {
                    return Long.parseLong(at < 0 ? text : text.substring(0, at));
                } catch (NumberFormatException unknown) {
                    // fall through
                }
            }
            return -1;
        }

        static Object runtimeBean() {
            return call("java.lang.management.ManagementFactory", "getRuntimeMXBean", null);
        }

        /** Invokes a public no-argument method by name; null when the class or call is unavailable. */
        static Object call(String owner, String method, Object target) {
            try {
                Method found = Class.forName(owner).getMethod(method);
                return found.invoke(target);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }

        static Object constant(String owner, String field) {
            try {
                return Class.forName(owner).getField(field).get(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }

        static byte[] read(String path) {
            try (InputStream in = new FileInputStream(path)) {
                // /proc files report size 0: read to EOF.
                ByteArrayOutputStream out = new ByteArrayOutputStream(512);
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            } catch (IOException | RuntimeException unreadable) {
                return null;
            }
        }

        /** The STATE..START fields of /proc/pid/stat as longs (STATE is the state char), or null. */
        static long[] stat(long pid) {
            byte[] bytes = read("/proc/" + pid + "/stat");
            if (bytes == null) {
                return null;
            }
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            int close = text.lastIndexOf(')'); // comm may contain spaces and parentheses
            if (close < 0 || close + 2 > text.length()) {
                return null;
            }
            String[] fields = text.substring(close + 2).trim().split(" ");
            if (fields.length <= START) {
                return null;
            }
            long[] stat = new long[START + 1];
            try {
                stat[STATE] = fields[STATE].isEmpty() ? '?' : fields[STATE].charAt(0);
                for (int i = PPID; i <= START; i++) {
                    stat[i] = Long.parseLong(fields[i]);
                }
            } catch (NumberFormatException malformed) {
                return null;
            }
            return stat;
        }

        static long startTicks(long pid) {
            long[] stat = stat(pid);
            return stat == null ? 0 : stat[START];
        }

        /** pid -> stat of every process /proc shows, sorted by pid. */
        static Map<Long, long[]> table() {
            Map<Long, long[]> table = new TreeMap<Long, long[]>();
            String[] names = new File("/proc").list();
            if (names == null) {
                return table;
            }
            for (String name : names) {
                if (name.isEmpty() || !isDigits(name)) {
                    continue;
                }
                long pid = Long.parseLong(name);
                long[] stat = stat(pid);
                if (stat != null) {
                    table.put(pid, stat);
                }
            }
            return table;
        }

        private static boolean isDigits(String text) {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                    return false;
                }
            }
            return text.length() < 19;
        }

        static long clockTicks() {
            long ticks = clockTicks;
            if (ticks <= 0) {
                Object name = constant("android.system.OsConstants", "_SC_CLK_TCK");
                Object value = null;
                if (name instanceof Integer) {
                    try {
                        value = Class.forName("android.system.Os").getMethod("sysconf", int.class).invoke(null, name);
                    } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                        value = null;
                    }
                }
                // USER_HZ is 100 on every Linux ABI Android ships.
                ticks = value instanceof Long && (Long) value > 0 ? (Long) value : 100;
                clockTicks = ticks;
            }
            return ticks;
        }

        /** Wall-clock time of boot in ms, which /proc start times count from; MIN_VALUE when unknown. */
        static long bootMillis() {
            long boot = bootMillis;
            if (boot != Long.MIN_VALUE) {
                return boot;
            }
            byte[] stat = read("/proc/stat"); // SELinux denies it to apps on recent Android
            if (stat != null) {
                for (String line : new String(stat, StandardCharsets.ISO_8859_1).split("\n")) {
                    if (line.startsWith("btime ")) {
                        try {
                            boot = Long.parseLong(line.substring(6).trim()) * 1000;
                        } catch (NumberFormatException malformed) {
                            boot = Long.MIN_VALUE;
                        }
                    }
                }
            }
            if (boot == Long.MIN_VALUE) {
                // CLOCK_BOOTTIME, the clock /proc/<pid>/stat start times are counted on.
                Object elapsed = call("android.os.SystemClock", "elapsedRealtime", null);
                if (elapsed instanceof Long) {
                    boot = System.currentTimeMillis() - (Long) elapsed;
                }
            }
            if (boot == Long.MIN_VALUE) {
                byte[] uptime = read("/proc/uptime");
                if (uptime != null) {
                    String first = new String(uptime, StandardCharsets.ISO_8859_1).trim().split(" ")[0];
                    try {
                        boot = System.currentTimeMillis() - (long) (Double.parseDouble(first) * 1000);
                    } catch (NumberFormatException malformed) {
                        boot = Long.MIN_VALUE;
                    }
                }
            }
            bootMillis = boot;
            return boot;
        }

        static String userName(long uid) {
            try {
                Object passwd = Class.forName("android.system.Os").getMethod("getpwuid", int.class).invoke(null, (int) uid);
                Object name = passwd == null ? null : passwd.getClass().getField("pw_name").get(passwd);
                if (name instanceof String && !((String) name).isEmpty()) {
                    return (String) name;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                // host JVM, or a uid without a passwd entry
            }
            return Long.toString(uid);
        }
    }

    /** The {@link Info} snapshot taken by {@link Handle#info()}. Not part of the JDK API. */
    final class ProcessInfo implements Info {
        private String command;
        private String commandLine;
        private String[] arguments;
        private long startMillis = -1;
        private long cpuNanos = -1;
        private String user;

        ProcessInfo(long pid) {
            String proc = "/proc/" + pid;
            try {
                command = Files.readSymbolicLink(Paths.get(proc + "/exe")).toString();
            } catch (IOException | RuntimeException unreadable) {
                command = null; // another uid's process, or no /proc
            }
            byte[] cmdline = Handle.read(proc + "/cmdline");
            if (cmdline != null && cmdline.length > 0) {
                // NUL-separated argv, padded with NULs where Android renamed the process.
                List<String> argv = new ArrayList<String>();
                int from = 0;
                for (int i = 0; i <= cmdline.length; i++) {
                    if (i == cmdline.length || cmdline[i] == 0) {
                        if (i > from) {
                            argv.add(new String(cmdline, from, i - from, StandardCharsets.UTF_8));
                        }
                        from = i + 1;
                    }
                }
                if (!argv.isEmpty()) {
                    arguments = argv.subList(1, argv.size()).toArray(new String[0]);
                    commandLine = String.join(" ", argv);
                }
            }
            long[] stat = Handle.stat(pid);
            if (stat != null) {
                long ticks = Handle.clockTicks();
                cpuNanos = (stat[Handle.UTIME] + stat[Handle.STIME]) * (1_000_000_000L / ticks);
                long boot = Handle.bootMillis();
                if (boot != Long.MIN_VALUE) {
                    startMillis = boot + stat[Handle.START] * 1000 / ticks;
                }
            }
            byte[] status = Handle.read(proc + "/status");
            if (status != null) {
                for (String line : new String(status, StandardCharsets.UTF_8).split("\n")) {
                    if (line.startsWith("Uid:")) {
                        String[] ids = line.substring(4).trim().split("\\s+");
                        try {
                            user = Handle.userName(Long.parseLong(ids[0]));
                        } catch (NumberFormatException malformed) {
                            user = null;
                        }
                        break;
                    }
                }
            }
            if (pid == Handle.CURRENT_PID) {
                fillCurrentWithoutProc();
            }
        }

        /** Where /proc says nothing about this VM (a non-Linux host), the VM itself does. */
        private void fillCurrentWithoutProc() {
            if (command == null) {
                command = launcher();
            }
            Object bean = Handle.runtimeBean();
            if (startMillis <= 0) {
                Object started = Handle.call("java.lang.management.RuntimeMXBean", "getStartTime", bean);
                if (started instanceof Long && (Long) started > 0) {
                    startMillis = (Long) started;
                }
            }
            if (cpuNanos < 0) {
                Object system = Handle.call("java.lang.management.ManagementFactory", "getOperatingSystemMXBean", null);
                Object cpu = Handle.call("com.sun.management.OperatingSystemMXBean", "getProcessCpuTime", system);
                if (cpu instanceof Long && (Long) cpu >= 0) {
                    cpuNanos = (Long) cpu;
                }
            }
            if (user == null) {
                String name = System.getProperty("user.name");
                user = name == null || name.isEmpty() ? null : name;
            }
        }

        /** The VM launcher under java.home: /system/bin/app_process64 on Android, bin/java elsewhere. */
        private static String launcher() {
            String home = System.getProperty("java.home");
            if (home == null) {
                return null;
            }
            String arch = System.getProperty("os.arch", "");
            List<String> names = new ArrayList<String>();
            if (arch.contains("64")) {
                names.add("bin/app_process64");
            }
            names.addAll(Arrays.asList("bin/app_process32", "bin/app_process", "bin/java", "bin/java.exe"));
            for (String name : names) {
                File file = new File(home, name);
                if (file.isFile()) {
                    return file.getAbsolutePath();
                }
            }
            return null;
        }

        @Override
        public Optional<String> command() {
            return Optional.ofNullable(command);
        }

        @Override
        public Optional<String> commandLine() {
            if (command != null && arguments != null) {
                return Optional.of(arguments.length == 0 ? command : command + " " + String.join(" ", arguments));
            }
            return Optional.ofNullable(commandLine);
        }

        @Override
        public Optional<String[]> arguments() {
            return Optional.ofNullable(arguments == null ? null : arguments.clone());
        }

        @Override
        public Optional<Instant> startInstant() {
            return startMillis > 0 ? Optional.of(Instant.ofEpochMilli(startMillis)) : Optional.<Instant>empty();
        }

        @Override
        public Optional<Duration> totalCpuDuration() {
            return cpuNanos >= 0 ? Optional.of(Duration.ofNanos(cpuNanos)) : Optional.<Duration>empty();
        }

        @Override
        public Optional<String> user() {
            return Optional.ofNullable(user);
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder(60).append('[');
            if (user != null) {
                text.append("user: ").append(user());
            }
            if (command != null) {
                text.append(text.length() > 1 ? ", " : "").append("cmd: ").append(command);
            }
            if (arguments != null && arguments.length > 0) {
                text.append(text.length() > 1 ? ", " : "").append("args: ").append(Arrays.toString(arguments));
            }
            if (commandLine != null) {
                text.append(text.length() > 1 ? ", " : "").append("cmdLine: ").append(commandLine);
            }
            if (startMillis > 0) {
                text.append(text.length() > 1 ? ", " : "").append("startTime: ").append(startInstant());
            }
            if (cpuNanos >= 0) {
                text.append(text.length() > 1 ? ", " : "").append("totalTime: ").append(totalCpuDuration());
            }
            return text.append(']').toString();
        }
    }
}
