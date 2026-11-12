// host-contract
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * java.lang.ProcessHandle against the JDK: current() is the VM's own pid, info() names its
 * executable, the current process cannot be destroyed or waited on, and, where the host has
 * /proc and a sleep(1) binary, a child shows up in children()/descendants(), names its parent,
 * and completes onExit() after destroy().
 */
public final class TestProcessHandleContracts {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String vm = ManagementFactory.getRuntimeMXBean().getName();
        long jvmPid = Long.parseLong(vm.substring(0, vm.indexOf('@')));
        ProcessHandle self = ProcessHandle.current();
        check(self.pid() == jvmPid, "current().pid() " + self.pid() + " != JVM pid " + jvmPid);
        check(self.isAlive(), "current process alive");
        check(self.equals(ProcessHandle.current()) && self.hashCode() == ProcessHandle.current().hashCode(), "current() equality");
        check(self.compareTo(ProcessHandle.current()) == 0, "compareTo of the same process");
        check(String.valueOf(jvmPid).equals(self.toString()), "toString is the pid: " + self);
        Optional<ProcessHandle> byPid = ProcessHandle.of(jvmPid);
        check(byPid.isPresent() && byPid.get().pid() == jvmPid, "of(current pid)");
        check(ProcessHandle.allProcesses().anyMatch(p -> p.pid() == jvmPid), "allProcesses() includes the current process");

        ProcessHandle.Info info = self.info();
        check(info != null, "info() non-null");
        check(info.command().isPresent(), "info().command() present: " + info);
        String command = info.command().get();
        check(!command.isEmpty() && command.indexOf('\0') < 0, "command is a clean path: " + command);
        info.startInstant().ifPresent(start -> check(!start.isAfter(java.time.Instant.now()), "start time in the past"));
        info.totalCpuDuration().ifPresent(cpu -> check(!cpu.isNegative(), "cpu time non-negative"));

        try {
            self.onExit();
            throw new AssertionError("onExit() of the current process accepted");
        } catch (IllegalStateException expected) {
        }
        try {
            self.destroy();
            throw new AssertionError("destroy() of the current process accepted");
        } catch (IllegalStateException expected) {
        }
        try {
            self.destroyForcibly();
            throw new AssertionError("destroyForcibly() of the current process accepted");
        } catch (IllegalStateException expected) {
        }

        File sleep = new File("/bin/sleep");
        if (!new File("/proc/self/stat").exists() || !sleep.canExecute()) {
            System.out.println("PASS: current() pid/info/equality/guards (no /proc here: child checks skipped)");
            return;
        }
        Process child = new ProcessBuilder(sleep.getPath(), "30").start();
        long childPid = child.pid();
        ProcessHandle handle = ProcessHandle.of(childPid).orElseThrow(() -> new AssertionError("of(child pid)"));
        check(handle.isAlive(), "child alive");
        check(self.children().anyMatch(p -> p.pid() == childPid), "children() lists the child");
        check(self.descendants().anyMatch(p -> p.pid() == childPid), "descendants() lists the child");
        check(handle.parent().map(p -> p.pid() == jvmPid).orElse(false), "child's parent() is the current process");
        check(handle.info().command().isPresent(), "child info().command()");
        check(handle.supportsNormalTermination(), "SIGTERM on Linux");
        java.util.concurrent.CompletableFuture<ProcessHandle> exit = handle.onExit();
        check(handle.destroy(), "destroy() of an own child requested");
        check(child.waitFor(10, TimeUnit.SECONDS), "child terminated");
        check(exit.get(10, TimeUnit.SECONDS).pid() == childPid, "onExit() completes with the handle");
        check(!handle.isAlive(), "child no longer alive");
        System.out.println("PASS: current() pid/info/equality/guards and child children/descendants/parent/destroy/onExit");
    }
}
