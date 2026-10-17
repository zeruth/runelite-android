package java.awt;

import java.util.ArrayDeque;
import java.util.Deque;

public class EventQueue {
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
        try {
            r.run();
        } catch (Throwable e) {
            android.util.Log.w("AWT-EDT", "Uncaught exception on " + Thread.currentThread().getName(), e);
        }
    }
}
