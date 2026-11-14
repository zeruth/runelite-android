/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An abstract class to perform lengthy GUI-interaction tasks in a
 * background thread. {@code doInBackground} runs on a worker thread;
 * {@code process}, {@code done} and every property change notification run
 * on the <i>Event Dispatch Thread</i>. Publications and progress updates
 * are coalesced on a timer, as the JDK does, so a worker that publishes in a
 * tight loop costs the EDT one {@code process} call per tick.
 *
 * <p>
 * Ported from OpenJDK 8u. The workers executor is process-wide instead of
 * per-AppContext; Android has a single application context.
 *
 * @param <T> the result type returned by {@code doInBackground} and {@code get}
 * @param <V> the type of the intermediate results published to {@code process}
 * @since 1.6
 */
public abstract class SwingWorker<T, V> implements RunnableFuture<T> {
    /**
     * number of worker threads.
     */
    private static final int MAX_WORKER_THREADS = 10;

    /**
     * current progress.
     */
    private volatile int progress;

    /**
     * current state.
     */
    private volatile StateValue state;

    /**
     * everything is run inside this FutureTask. Also it is used as
     * a delegatee for the Future API.
     */
    private final FutureTask<T> future;

    /**
     * all propertyChangeSupport goes through this.
     */
    private final PropertyChangeSupport propertyChangeSupport;

    /**
     * handler for {@code process} mehtod.
     */
    private AccumulativeRunnable<V> doProcess;

    /**
     * handler for progress property change notifications.
     */
    private AccumulativeRunnable<Integer> doNotifyProgressChange;

    private final AccumulativeRunnable<Runnable> doSubmit = getDoSubmit();

    private static ExecutorService executorService;

    private static DoSubmitAccumulativeRunnable doSubmitShared;

    /**
     * Values for the {@code state} bound property.
     * @since 1.6
     */
    public enum StateValue {
        /**
         * Initial {@code SwingWorker} state.
         */
        PENDING,
        /**
         * {@code SwingWorker} is {@code STARTED}
         * before invoking {@code doInBackground}.
         */
        STARTED,

        /**
         * {@code SwingWorker} is {@code DONE}
         * after {@code doInBackground} method
         * is finished.
         */
        DONE
    }

    /**
     * Constructs this {@code SwingWorker}.
     */
    public SwingWorker() {
        Callable<T> callable =
                new Callable<T>() {
                    public T call() throws Exception {
                        setState(StateValue.STARTED);
                        return doInBackground();
                    }
                };

        future = new FutureTask<T>(callable) {
                       @Override
                       protected void done() {
                           doneEDT();
                           setState(StateValue.DONE);
                       }
                   };

       state = StateValue.PENDING;
       propertyChangeSupport = new SwingWorkerPropertyChangeSupport(this);
       doProcess = null;
       doNotifyProgressChange = null;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * <p>
     * Note that this method is executed only once.
     *
     * <p>
     * Note: this method is executed in a background thread.
     *
     * @return the computed result
     * @throws Exception if unable to compute a result
     */
    protected abstract T doInBackground() throws Exception ;

    /**
     * Sets this {@code Future} to the result of computation unless
     * it has been cancelled.
     */
    public final void run() {
        future.run();
    }

    /**
     * Sends data chunks to the {@link #process} method. This method is to be
     * used from inside the {@code doInBackground} method to deliver
     * intermediate results for processing on the <i>Event Dispatch
     * Thread</i> inside the {@code process} method.
     *
     * <p>
     * Because the {@code process} method is invoked asynchronously on
     * the <i>Event Dispatch Thread</i>
     * multiple invocations to the {@code publish} method
     * might occur before the {@code process} method is executed. For
     * performance purposes all these invocations are coalesced into one
     * invocation with concatenated arguments.
     *
     * @param chunks intermediate results to process
     *
     * @see #process
     */
    @SafeVarargs
    @SuppressWarnings("varargs") // Passing chunks to add is safe
    protected final void publish(V... chunks) {
        synchronized (this) {
            if (doProcess == null) {
                doProcess = new AccumulativeRunnable<V>() {
                    @Override
                    public void run(List<V> args) {
                        process(args);
                    }
                    @Override
                    protected void submit() {
                        doSubmit.add(this);
                    }
                };
            }
        }
        doProcess.add(chunks);
    }

    /**
     * Receives data chunks from the {@code publish} method asynchronously on the
     * <i>Event Dispatch Thread</i>.
     *
     * @param chunks intermediate results to process
     *
     * @see #publish
     *
     */
    protected void process(List<V> chunks) {
    }

    /**
     * Executed on the <i>Event Dispatch Thread</i> after the {@code doInBackground}
     * method is finished. The default
     * implementation does nothing. Subclasses may override this method to
     * perform completion actions on the <i>Event Dispatch Thread</i>. Note
     * that you can query status inside the implementation of this method to
     * determine the result of this task or whether this task has been cancelled.
     *
     * @see #doInBackground
     * @see #isCancelled()
     * @see #get
     */
    protected void done() {
    }

    /**
     * Sets the {@code progress} bound property.
     * The value should be from 0 to 100.
     *
     * <p>
     * Because {@code PropertyChangeListener}s are notified asynchronously on
     * the <i>Event Dispatch Thread</i> multiple invocations to the
     * {@code setProgress} method might occur before any
     * {@code PropertyChangeListeners} are invoked. For performance purposes
     * all these invocations are coalesced into one invocation with the last
     * invocation argument only.
     *
     * @param progress the progress value to set
     * @throws IllegalArgumentException is value not from 0 to 100
     */
    protected final void setProgress(int progress) {
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("the value should be from 0 to 100");
        }
        if (this.progress == progress) {
            return;
        }
        int oldProgress = this.progress;
        this.progress = progress;
        if (! getPropertyChangeSupport().hasListeners("progress")) {
            return;
        }
        synchronized (this) {
            if (doNotifyProgressChange == null) {
                doNotifyProgressChange =
                    new AccumulativeRunnable<Integer>() {
                        @Override
                        public void run(List<Integer> args) {
                            firePropertyChange("progress",
                               args.get(0),
                               args.get(args.size() - 1));
                        }
                        @Override
                        protected void submit() {
                            doSubmit.add(this);
                        }
                    };
            }
        }
        doNotifyProgressChange.add(oldProgress, progress);
    }

    /**
     * Returns the {@code progress} bound property.
     *
     * @return the progress bound property.
     */
    public final int getProgress() {
        return progress;
    }

    /**
     * Schedules this {@code SwingWorker} for execution on a <i>worker</i>
     * thread. There are a number of <i>worker</i> threads available. In the
     * event all <i>worker</i> threads are busy handling other
     * {@code SwingWorkers} this {@code SwingWorker} is placed in a waiting
     * queue.
     *
     * <p>
     * Note:
     * {@code SwingWorker} is only designed to be executed once.  Executing a
     * {@code SwingWorker} more than once will not result in invoking the
     * {@code doInBackground} method twice.
     */
    public final void execute() {
        getWorkersExecutorService().execute(this);
    }

    // Future methods START
    /**
     * {@inheritDoc}
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isDone() {
        return future.isDone();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note: calling {@code get} on the <i>Event Dispatch Thread</i> blocks
     * <i>all</i> events, including repaints, from being processed until this
     * {@code SwingWorker} is complete.
     */
    public final T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Please refer to {@link #get} for more details.
     */
    public final T get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    // Future methods END

    // PropertyChangeSupports methods START
    /**
     * Adds a {@code PropertyChangeListener} to the listener list. The listener
     * is registered for all properties. The same listener object may be added
     * more than once, and will be called as many times as it is added. If
     * {@code listener} is {@code null}, no exception is thrown and no action is taken.
     *
     * @param listener the {@code PropertyChangeListener} to be added
     */
    public final void addPropertyChangeListener(PropertyChangeListener listener) {
        getPropertyChangeSupport().addPropertyChangeListener(listener);
    }

    /**
     * Removes a {@code PropertyChangeListener} from the listener list. This
     * removes a {@code PropertyChangeListener} that was registered for all
     * properties. If {@code listener} was added more than once to the same
     * event source, it will be notified one less time after being removed. If
     * {@code listener} is {@code null}, or was never added, no exception is
     * thrown and no action is taken.
     *
     * @param listener the {@code PropertyChangeListener} to be removed
     */
    public final void removePropertyChangeListener(PropertyChangeListener listener) {
        getPropertyChangeSupport().removePropertyChangeListener(listener);
    }

    /**
     * Reports a bound property update to any registered listeners. No event is
     * fired if {@code old} and {@code new} are equal and non-null.
     *
     * <p>
     * This {@code SwingWorker} will be the source for
     * any generated events.
     *
     * <p>
     * When called off the <i>Event Dispatch Thread</i>
     * {@code PropertyChangeListeners} are notified asynchronously on
     * the <i>Event Dispatch Thread</i>.
     *
     * @param propertyName the programmatic name of the property that was changed
     * @param oldValue the old value of the property
     * @param newValue the new value of the property
     */
    public final void firePropertyChange(String propertyName, Object oldValue,
            Object newValue) {
        getPropertyChangeSupport().firePropertyChange(propertyName,
            oldValue, newValue);
    }

    /**
     * Returns the {@code PropertyChangeSupport} for this {@code SwingWorker}.
     * This method is used when flexible access to bound properties support is
     * needed.
     * <p>
     * Note: The returned {@code PropertyChangeSupport} notifies any
     * {@code PropertyChangeListener}s asynchronously on the <i>Event Dispatch
     * Thread</i> in the event that {@code firePropertyChange} or
     * {@code fireIndexedPropertyChange} are called off the <i>Event Dispatch
     * Thread</i>.
     *
     * @return {@code PropertyChangeSupport} for this {@code SwingWorker}
     */
    public final PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    // PropertyChangeSupports methods END

    /**
     * Returns the {@code SwingWorker} state bound property.
     *
     * @return the current state
     */
    public final StateValue getState() {
        /*
         * DONE is a speacial case
         * to keep getState and isDone is sync
         */
        if (isDone()) {
            return StateValue.DONE;
        } else {
            return state;
        }
    }

    /**
     * Sets this {@code SwingWorker} state bound property.
     * @param state the state to set
     */
    private void setState(StateValue state) {
        StateValue old = this.state;
        this.state = state;
        firePropertyChange("state", old, state);
    }

    /**
     * Invokes {@code done} on the EDT.
     */
    private void doneEDT() {
        Runnable doDone =
            new Runnable() {
                public void run() {
                    done();
                }
            };
        if (SwingUtilities.isEventDispatchThread()) {
            doDone.run();
        } else {
            doSubmit.add(doDone);
        }
    }


    /**
     * returns workersExecutorService.
     *
     * returns the service stored in the appContext or creates it if
     * necessary.
     *
     * @return ExecutorService for the {@code SwingWorkers}
     */
    private static synchronized ExecutorService getWorkersExecutorService() {
        if (executorService == null) {
            //this creates daemon threads.
            ThreadFactory threadFactory =
                new ThreadFactory() {
                    final ThreadFactory defaultFactory =
                        Executors.defaultThreadFactory();
                    public Thread newThread(final Runnable r) {
                        Thread thread =
                            defaultFactory.newThread(r);
                        thread.setName("SwingWorker-"
                            + thread.getName());
                        thread.setDaemon(true);
                        return thread;
                    }
                };

            executorService =
                new ThreadPoolExecutor(MAX_WORKER_THREADS, MAX_WORKER_THREADS,
                                       10L, TimeUnit.MINUTES,
                                       new LinkedBlockingQueue<Runnable>(),
                                       threadFactory);
        }
        return executorService;
    }

    private static synchronized AccumulativeRunnable<Runnable> getDoSubmit() {
        if (doSubmitShared == null) {
            doSubmitShared = new DoSubmitAccumulativeRunnable();
        }
        return doSubmitShared;
    }

    /**
     * sun.swing.AccumulativeRunnable: collects the arguments of every
     * {@code add} until the one submitted run drains them.
     */
    private abstract static class AccumulativeRunnable<T> implements Runnable {
        private List<T> arguments = null;

        protected abstract void run(List<T> args);

        public final void run() {
            run(flush());
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        public final synchronized void add(T... args) {
            boolean isSubmitted = true;
            if (arguments == null) {
                isSubmitted = false;
                arguments = new ArrayList<T>();
            }
            Collections.addAll(arguments, args);
            if (!isSubmitted) {
                submit();
            }
        }

        protected void submit() {
            SwingUtilities.invokeLater(this);
        }

        private synchronized List<T> flush() {
            List<T> list = arguments;
            arguments = null;
            return list;
        }
    }

    private static class DoSubmitAccumulativeRunnable
          extends AccumulativeRunnable<Runnable> implements ActionListener {
        private final static int DELAY = 1000 / 30;
        @Override
        protected void run(List<Runnable> args) {
            for (Runnable runnable : args) {
                runnable.run();
            }
        }
        @Override
        protected void submit() {
            Timer timer = new Timer(DELAY, this);
            timer.setRepeats(false);
            timer.start();
        }
        public void actionPerformed(ActionEvent event) {
            run();
        }
    }

    @SuppressWarnings("serial") // JDK-implementation class
    private class SwingWorkerPropertyChangeSupport
            extends PropertyChangeSupport {
        SwingWorkerPropertyChangeSupport(Object source) {
            super(source);
        }
        @Override
        public void firePropertyChange(final PropertyChangeEvent evt) {
            if (SwingUtilities.isEventDispatchThread()) {
                super.firePropertyChange(evt);
            } else {
                doSubmit.add(
                    new Runnable() {
                        public void run() {
                            SwingWorkerPropertyChangeSupport.this
                                .firePropertyChange(evt);
                        }
                    });
            }
        }
    }
}
