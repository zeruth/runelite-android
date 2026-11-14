// host-contract
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * SwingWorker and Timer against the desktop JDK and the shadow: the EDT order of state events,
 * process, progress and done; the Future API; and Timer's repeats/coalesce/actionCommand.
 */
public final class TestSwingWorkerContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** Records every EDT callback of a worker, in the order the EDT ran them. */
    private static class Recording extends SwingWorker<String, Integer> {
        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        final List<Integer> chunks = Collections.synchronizedList(new ArrayList<Integer>());
        final AtomicBoolean offEdt = new AtomicBoolean();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean backgroundOnEdt = true;
        volatile Thread backgroundThread;

        Recording() {
            addPropertyChangeListener((PropertyChangeEvent e) -> {
                if (!EventQueue.isDispatchThread()) offEdt.set(true);
                events.add(e.getPropertyName() + "=" + e.getNewValue());
                if ("state".equals(e.getPropertyName()) && e.getNewValue() == StateValue.DONE) finished.countDown();
            });
        }

        @Override protected String doInBackground() throws Exception {
            backgroundOnEdt = EventQueue.isDispatchThread();
            backgroundThread = Thread.currentThread();
            for (int i = 1; i <= 5; i++) publish(i);
            setProgress(50);
            setProgress(100);
            return "result";
        }

        @Override protected void process(List<Integer> list) {
            if (!EventQueue.isDispatchThread()) offEdt.set(true);
            events.add("process");
            chunks.addAll(list);
        }

        @Override protected void done() {
            if (!EventQueue.isDispatchThread()) offEdt.set(true);
            events.add("done");
        }
    }

    private static final class Blocking extends SwingWorker<String, Void> {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch doneCalled = new CountDownLatch(1);
        volatile boolean interrupted;
        volatile boolean doneOnEdt;

        @Override protected String doInBackground() throws Exception {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted = true;
                throw e;
            }
            return "late";
        }

        @Override protected void done() {
            doneOnEdt = EventQueue.isDispatchThread();
            doneCalled.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        Recording worker = new Recording();
        check("starts PENDING", worker.getState() == SwingWorker.StateValue.PENDING);
        check("initial progress 0", worker.getProgress() == 0);
        check("property change support", worker.getPropertyChangeSupport() != null);
        worker.execute();
        check("get returns the result", "result".equals(worker.get(10, TimeUnit.SECONDS)));
        check("DONE state event arrives", worker.finished.await(10, TimeUnit.SECONDS));
        flushEdt();
        List<String> events = new ArrayList<>(worker.events);
        System.out.println("  events " + events);
        check("doInBackground off the EDT", !worker.backgroundOnEdt && worker.backgroundThread != null);
        check("callbacks on the EDT", !worker.offEdt.get());
        check("first event is state=STARTED", !events.isEmpty() && events.get(0).equals("state=STARTED"));
        check("last event is state=DONE", !events.isEmpty() && events.get(events.size() - 1).equals("state=DONE"));
        check("done right before state=DONE", events.size() >= 2 && events.get(events.size() - 2).equals("done"));
        int done = events.indexOf("done");
        int lastProcess = events.lastIndexOf("process");
        check("publications processed before done", lastProcess >= 0 && lastProcess < done);
        check("publications coalesced in order", worker.chunks.equals(java.util.Arrays.asList(1, 2, 3, 4, 5)));
        check("process coalesced into fewer calls", Collections.frequency(events, "process") < 5);
        int lastProgress = -1;
        for (int i = 0; i < events.size(); i++) if (events.get(i).startsWith("progress=")) lastProgress = i;
        check("progress reaches 100 before done", lastProgress >= 0 && lastProgress < done
            && events.get(lastProgress).equals("progress=100"));
        check("getProgress", worker.getProgress() == 100);
        check("isDone after completion", worker.isDone() && !worker.isCancelled());
        check("state DONE", worker.getState() == SwingWorker.StateValue.DONE);

        boolean range = false;
        try {
            new Recording() {
                { setProgress(101); }
            };
        } catch (IllegalArgumentException expected) {
            range = true;
        }
        check("setProgress rejects 101", range);

        Blocking blocking = new Blocking();
        blocking.execute();
        check("blocking worker starts", blocking.started.await(10, TimeUnit.SECONDS));
        boolean timedOut = false;
        try {
            blocking.get(50, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
            timedOut = true;
        }
        check("get(timeout) times out", timedOut);
        check("cancel(true) succeeds", blocking.cancel(true));
        check("cancelled and done", blocking.isCancelled() && blocking.isDone());
        boolean cancellation = false;
        try {
            blocking.get();
        } catch (CancellationException expected) {
            cancellation = true;
        }
        check("get after cancel throws CancellationException", cancellation);
        check("done after cancel", blocking.doneCalled.await(10, TimeUnit.SECONDS));
        check("done after cancel on the EDT", blocking.doneOnEdt);
        check("cancel interrupts doInBackground", waitFor(() -> blocking.interrupted));

        SwingWorker<Void, Void> failing = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { throw new IllegalStateException("boom"); }
        };
        failing.execute();
        boolean wrapped = false;
        try {
            failing.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            wrapped = expected.getCause() instanceof IllegalStateException;
        }
        check("exception wrapped in ExecutionException", wrapped);

        timer();

        System.out.println((failures == 0 ? "PASS " : "FAIL ") + (total - failures) + "/" + total + " SwingWorker/Timer contracts");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void timer() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        AtomicBoolean onEdt = new AtomicBoolean(true);
        List<String> commands = Collections.synchronizedList(new ArrayList<String>());
        CountDownLatch once = new CountDownLatch(1);
        Timer single = new Timer(20, e -> {
            if (!EventQueue.isDispatchThread()) onEdt.set(false);
            commands.add(e.getActionCommand());
            fired.incrementAndGet();
            once.countDown();
        });
        check("timer defaults", single.isRepeats() && single.isCoalesce() && single.getDelay() == 20
            && single.getInitialDelay() == 20 && !single.isRunning());
        single.setRepeats(false);
        single.setActionCommand("tick");
        single.start();
        check("timer running", single.isRunning());
        check("timer fires", once.await(5, TimeUnit.SECONDS));
        Thread.sleep(150);
        flushEdt();
        check("non-repeating timer fires once", fired.get() == 1);
        check("non-repeating timer stops", !single.isRunning());
        check("timer fires on the EDT", onEdt.get());
        check("timer action command", commands.size() == 1 && "tick".equals(commands.get(0)));
        check("timer getListeners", single.getListeners(java.awt.event.ActionListener.class).length == 1
            && single.getActionListeners().length == 1);

        AtomicInteger repeats = new AtomicInteger();
        Timer repeating = new Timer(10, e -> repeats.incrementAndGet());
        repeating.setInitialDelay(0);
        repeating.start();
        check("repeating timer repeats", waitFor(() -> repeats.get() >= 3));
        repeating.stop();
        check("stopped timer not running", !repeating.isRunning());
        flushEdt();
        int stoppedAt = repeats.get();
        Thread.sleep(100);
        flushEdt();
        check("stopped timer stays quiet", repeats.get() == stoppedAt);

        // A coalescing timer queues at most one event while the EDT is busy.
        AtomicInteger coalesced = new AtomicInteger();
        Timer busy = new Timer(5, e -> coalesced.incrementAndGet());
        SwingUtilities.invokeAndWait(() -> {
            busy.start();
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            // Queued behind the one event the ~40 ticks above posted; stop() cancels any later one.
            SwingUtilities.invokeLater(busy::stop);
        });
        flushEdt();
        check("coalescing timer queues one event while the EDT is busy", coalesced.get() == 1);
    }

    private interface Condition { boolean holds(); }

    private static boolean waitFor(Condition condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            if (condition.holds()) return true;
            Thread.sleep(10);
        }
        return condition.holds();
    }

    private static void flushEdt() throws Exception {
        // The worker's coalescing timer (1000/30 ms) may still hold a batch.
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> { });
    }
}
