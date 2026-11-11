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
package java.awt;

import java.util.ArrayDeque;
import java.util.Deque;

public class EventQueue {
    private static final ThreadLocal<AWTEvent> CURRENT_EVENT = new ThreadLocal<>();
    private static volatile long mostRecentEventTime = System.currentTimeMillis();
    public static AWTEvent getCurrentEvent() { return isDispatchThread() ? CURRENT_EVENT.get() : null; }
    public static long getMostRecentEventTime() { return isDispatchThread() ? mostRecentEventTime : System.currentTimeMillis(); }
    static AWTEvent beginDispatch(AWTEvent event) {
        AWTEvent previous = CURRENT_EVENT.get();
        CURRENT_EVENT.set(event);
        if (event instanceof java.awt.event.InputEvent)
            mostRecentEventTime = Math.max(mostRecentEventTime, ((java.awt.event.InputEvent) event).getWhen());
        else if (event instanceof java.awt.event.ActionEvent)
            mostRecentEventTime = Math.max(mostRecentEventTime, ((java.awt.event.ActionEvent) event).getWhen());
        return previous;
    }
    static void endDispatch(AWTEvent previous) { if (previous == null) CURRENT_EVENT.remove(); else CURRENT_EVENT.set(previous); }
    private final Deque<AWTEvent> queue = new ArrayDeque<>();

    public EventQueue() {
    }

    public synchronized void postEvent(AWTEvent event) {
        if (event != null) { queue.addLast(event); notifyAll(); }
    }

    public synchronized AWTEvent getNextEvent() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        return queue.pollFirst();
    }

    public synchronized AWTEvent peekEvent() {
        return queue.peekFirst();
    }

    public synchronized AWTEvent peekEvent(int id) {
        for (AWTEvent e : queue) {
            if (e.getID() == id) return e;
        }
        return null;
    }

    public static boolean isDispatchThread() {
        // Treat only our synthetic AWT-Invoke* workers as the EDT. Returning true
        // unconditionally would make every thread look like the EDT to RuneLite's
        // HTTP interceptor (RuneLiteModule.provideHttpClient), which rejects blocking
        // calls from the EDT — that breaks jav_config loading, world fetch, item
        // prices, session ping, etc., and downstream causes login to use stale/wrong
        // config (manifesting as "RuneScape has been updated" at login).
        String name = Thread.currentThread().getName();
        return name != null && name.startsWith("AWT-Invoke");
    }

    /**
     * The single Event Dispatch Thread. Every {@link #invokeLater}/{@link #invokeAndWait}
     * task runs HERE, serialized in submission order — exactly like a real Swing EDT.
     *
     * <p>The previous implementation spawned a fresh {@code new Thread(r, "AWT-InvokeLater")}
     * per call, so multiple invokeLater tasks ran CONCURRENTLY. Any Swing component that
     * mutates its child list off one task while another reads/mutates it
     * (WorldSwitcherPanel.updateList → Container.removeAll() is the canonical case) then
     * threw ConcurrentModificationException. RuneLite is written to Swing's
     * single-thread-confinement contract — all UI mutation marshalled onto the EDT via
     * invokeLater — so honoring that contract with one serial thread is both correct and
     * the real fix.</p>
     *
     * <p>Named "AWT-InvokeLater" so {@link #isDispatchThread()} (prefix match on
     * "AWT-Invoke") still recognizes it. Single-thread executor replaces the worker if it
     * ever dies, but {@link #runGuarded} catches throwables per-task so it shouldn't.</p>
     */
    private static final Thread.UncaughtExceptionHandler EDT_UEH =
        new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                android.util.Log.w("AWT-EDT", "Uncaught exception on " + t.getName(), e);
            }
        };

    private static final java.util.concurrent.ThreadPoolExecutor EDT =
        new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>(), r -> {
            Thread t = new Thread(r, "AWT-InvokeLater");
            t.setUncaughtExceptionHandler(EDT_UEH);
            return t;
        });

    /** RuneLite drains pending removal events inside the existing EDT, not a second worker. */
    public SecondaryLoop createSecondaryLoop() {
        return new SecondaryLoop() {
            private volatile boolean active;
            @Override public boolean enter() {
                synchronized (this) {
                    if (active) return false;
                    active = true;
                }
                try {
                    if (isDispatchThread()) {
                        while (active) {
                            AWTEvent event;
                            synchronized (EventQueue.this) { event = queue.pollFirst(); }
                            if (event != null) {
                                if (event.getSource() instanceof Component)
                                    ((Component) event.getSource()).dispatchEvent(event);
                                continue;
                            }
                            // Includes SwingUtil's invokeLater(loop::exit), preserving
                            // serialization and allowing nested invokeAndWait to finish.
                            Runnable task = EDT.getQueue().poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                            if (task != null) runGuarded(task);
                        }
                    } else {
                        synchronized (this) { while (active) wait(); }
                    }
                    return true;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                } finally {
                    synchronized (this) { active = false; notifyAll(); }
                }
            }
            @Override public synchronized boolean exit() {
                if (!active) return false;
                active = false;
                notifyAll();
                return true;
            }
        };
    }

    public static void invokeLater(Runnable r) {
        if (r == null) return;
        EDT.execute(() -> runGuarded(r));
    }

    public static void invokeAndWait(Runnable r) throws InterruptedException {
        if (r == null) return;
        // Already on the EDT — run inline so we don't deadlock waiting on ourselves.
        if (isDispatchThread()) {
            runGuarded(r);
            return;
        }
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        EDT.execute(() -> {
            try {
                runGuarded(r);
            } finally {
                done.countDown();
            }
        });
        done.await();
    }

    /**
     * Real Swing wraps EDT callbacks in a try/catch and logs uncaught exceptions — a
     * misbehaving listener doesn't kill the process (or, here, the single EDT worker;
     * letting it propagate would tear down the executor's only thread and freeze all
     * future UI updates). Plugin startup is full of code that throws here (NoSuchMethod
     * on missing Swing API, NPE on half-constructed components, etc.).
     */
    private static void runGuarded(Runnable r) {
        boolean outermost = taskDepth++ == 0;
        if (outermost) {
            edtThread = Thread.currentThread();
            taskStart = android.os.SystemClock.uptimeMillis();
        }
        try {
            r.run();
        } catch (Throwable e) {
            android.util.Log.w("AWT-EDT", "Uncaught exception on " + Thread.currentThread().getName(), e);
        } finally {
            if (--taskDepth == 0) taskStart = 0;
        }
    }

    // ---- EDT watchdog -------------------------------------------------------------------
    // One stuck task freezes every Swing panel and anything that invokeAndWaits on the EDT,
    // and a release build can't be thread-dumped from adb. So when a single task runs past
    // STALL_MS, log the EDT's stack (again every REPEAT_MS while it stays stuck).

    private static final long STALL_MS = 4000, REPEAT_MS = 10_000;
    private static int taskDepth; // EDT only
    private static volatile Thread edtThread;
    private static volatile long taskStart; // uptime of the outermost running task, 0 when idle

    static {
        Thread watchdog = new Thread(() -> {
            long reportedFor = 0, lastReport = 0;
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                long start = taskStart;
                Thread edt = edtThread;
                if (start == 0 || edt == null) continue;
                long now = android.os.SystemClock.uptimeMillis();
                if (now - start < STALL_MS) continue;
                if (start == reportedFor && now - lastReport < REPEAT_MS) continue;
                reportedFor = start;
                lastReport = now;
                StringBuilder sb = new StringBuilder("EDT blocked for ").append(now - start).append(" ms in one task:");
                for (StackTraceElement e : edt.getStackTrace()) sb.append("\n\tat ").append(e);
                android.util.Log.w("AWT-EDT", sb.toString());
            }
        }, "AWT-EDT-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
