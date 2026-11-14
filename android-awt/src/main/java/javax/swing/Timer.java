/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.EventListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.event.EventListenerList;

/**
 * Fires one or more {@code ActionEvent}s at specified intervals. The
 * action event handlers of every {@code Timer} execute on the event-dispatching
 * thread, so they can safely manipulate components.
 *
 * <p>
 * Ported from OpenJDK 8u. The JDK's {@code TimerQueue} thread is a single
 * daemon scheduled executor named "TimerQueue"; it only wakes timers, and a
 * wakeup posts to the EDT through the same {@code notify} flag the JDK uses,
 * so a coalescing timer never queues more than one pending event.
 *
 * @since 1.2
 */
@SuppressWarnings("serial")
public class Timer implements Serializable
{
    /*
     * NOTE: all fields need to be handled in readResolve
     */

    protected EventListenerList listenerList = new EventListenerList();

    // The following field strives to maintain the following:
    //    If coalesce is true, only allow one Runnable to be queued on the
    //    EventQueue and be pending (ie in the process of notifying the
    //    ActionListener). If we didn't do this it would allow for a
    //    situation where the app is taking too long to process the
    //    actionPerformed, and thus we'ld end up queing a bunch of Runnables
    //    and the app would never return: not good. This of course implies
    //    you can get dropped events, but such is life.
    // notify is used to indicate if the ActionListener can be notified, when
    // the Runnable is processed if this is true it will notify the listeners.
    // notify is set to true when the Timer fires and the Runnable is queued.
    // It will be set to false after notifying the listeners (if coalesce is
    // true) or if the developer invokes stop.
    private transient final AtomicBoolean notify = new AtomicBoolean(false);

    private volatile int     initialDelay, delay;
    private volatile boolean repeats = true, coalesce = true;

    private transient final Runnable doPostEvent;

    private static volatile boolean logTimers;

    // This field is maintained by the timer queue: the pending wakeup, and
    // the identity of the run it belongs to so a stale wakeup is ignored
    // after stop() or restart().
    private transient ScheduledFuture<?> wakeup;
    private transient Object run;

    /**
     * The action command to deliver with each event.
     */
    private volatile String actionCommand;

    /**
     * Creates a {@code Timer} and initializes both the initial delay and
     * between-event delay to {@code delay} milliseconds. If {@code delay}
     * is less than or equal to zero, the timer fires as soon as it
     * is started. If <code>listener</code> is not <code>null</code>,
     * it's registered as an action listener on the timer.
     *
     * @param delay milliseconds for the initial and between-event delay
     * @param listener  an initial listener; can be <code>null</code>
     *
     * @see #addActionListener
     * @see #setInitialDelay
     * @see #setRepeats
     */
    public Timer(int delay, ActionListener listener) {
        super();
        this.delay = delay;
        this.initialDelay = delay;

        doPostEvent = new DoPostEvent();

        if (listener != null) {
            addActionListener(listener);
        }
    }

    /**
     * DoPostEvent is a runnable class that fires actionEvents to
     * the listeners on the EventDispatchThread, via invokeLater.
     * @see Timer#post
     */
    class DoPostEvent implements Runnable
    {
        public void run() {
            if (logTimers) {
                System.out.println("Timer ringing: " + Timer.this);
            }
            if(notify.get()) {
                fireActionPerformed(new ActionEvent(Timer.this, 0, getActionCommand(),
                                                    System.currentTimeMillis(),
                                                    0));
                if (coalesce) {
                    cancelEvent();
                }
            }
        }

        Timer getTimer() {
            return Timer.this;
        }
    }

    /**
     * Adds an action listener to the <code>Timer</code>.
     *
     * @param listener the listener to add
     *
     * @see #Timer
     */
    public void addActionListener(ActionListener listener) {
        listenerList.add(ActionListener.class, listener);
    }

    /**
     * Removes the specified action listener from the <code>Timer</code>.
     *
     * @param listener the listener to remove
     */
    public void removeActionListener(ActionListener listener) {
        listenerList.remove(ActionListener.class, listener);
    }

    /**
     * Returns an array of all the action listeners registered
     * on this timer.
     *
     * @return all of the timer's <code>ActionListener</code>s or an empty
     *         array if no action listeners are currently registered
     *
     * @see #addActionListener
     * @see #removeActionListener
     *
     * @since 1.4
     */
    public ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.
     *
     * @param e the action event to fire
     * @see EventListenerList
     */
    protected void fireActionPerformed(ActionEvent e) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();

        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i=listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==ActionListener.class) {
                ((ActionListener)listeners[i+1]).actionPerformed(e);
            }
        }
    }

    /**
     * Returns an array of all the objects currently registered as
     * <code><em>Foo</em>Listener</code>s
     * upon this <code>Timer</code>.
     * <code><em>Foo</em>Listener</code>s
     * are registered using the <code>add<em>Foo</em>Listener</code> method.
     *
     * @param <T> the type of {@code EventListener} class being requested
     * @param listenerType the type of listeners requested;
     *          this parameter should specify an interface
     *          that descends from <code>java.util.EventListener</code>
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s
     *          on this timer,
     *          or an empty array if no such
     *          listeners have been added
     * @exception ClassCastException if <code>listenerType</code> doesn't
     *          specify a class or interface that implements
     *          <code>java.util.EventListener</code>
     *
     * @see #getActionListeners
     * @see #addActionListener
     * @see #removeActionListener
     *
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    /**
     * Enables or disables the timer log. When enabled, a message
     * is posted to <code>System.out</code> whenever the timer goes off.
     *
     * @param flag  <code>true</code> to enable logging
     * @see #getLogTimers
     */
    public static void setLogTimers(boolean flag) {
        logTimers = flag;
    }

    /**
     * Returns <code>true</code> if logging is enabled.
     *
     * @return <code>true</code> if logging is enabled; otherwise, false
     * @see #setLogTimers
     */
    public static boolean getLogTimers() {
        return logTimers;
    }

    /**
     * Sets the <code>Timer</code>'s between-event delay, the number of milliseconds
     * between successive action events. This does not affect the initial delay
     * property, which can be set by the {@code setInitialDelay} method.
     *
     * @param delay the delay in milliseconds
     * @see #setInitialDelay
     */
    public void setDelay(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Invalid delay: " + delay);
        }
        else {
            this.delay = delay;
        }
    }

    /**
     * Returns the delay, in milliseconds,
     * between firings of action events.
     *
     * @return the delay, in milliseconds, between firings of action events
     * @see #setDelay
     * @see #getInitialDelay
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the <code>Timer</code>'s initial delay, the time
     * in milliseconds to wait after the timer is started
     * before firing the first event. Upon construction, this
     * is set to be the same as the between-event delay,
     * but then its value is independent and remains unaffected
     * by changes to the between-event delay.
     *
     * @param initialDelay the initial delay, in milliseconds
     * @see #setDelay
     */
    public void setInitialDelay(int initialDelay) {
        if (initialDelay < 0) {
            throw new IllegalArgumentException("Invalid initial delay: " +
                                               initialDelay);
        }
        else {
            this.initialDelay = initialDelay;
        }
    }

    /**
     * Returns the {@code Timer}'s initial delay.
     *
     * @return the {@code Timer}'s intial delay, in milliseconds
     * @see #setInitialDelay
     * @see #setDelay
     */
    public int getInitialDelay() {
        return initialDelay;
    }

    /**
     * If <code>flag</code> is <code>false</code>,
     * instructs the <code>Timer</code> to send only one
     * action event to its listeners.
     *
     * @param flag specify <code>false</code> to make the timer
     *             stop after sending its first action event
     */
    public void setRepeats(boolean flag) {
        repeats = flag;
    }

    /**
     * Returns <code>true</code> (the default)
     * if the <code>Timer</code> will send
     * an action event
     * to its listeners multiple times.
     *
     * @return true if the {@code Timer} will send an action event to its
     *              listeners multiple times
     * @see #setRepeats
     */
    public boolean isRepeats() {
        return repeats;
    }

    /**
     * Sets whether the <code>Timer</code> coalesces multiple pending
     * <code>ActionEvent</code> firings.
     * A busy application may not be able
     * to keep up with a <code>Timer</code>'s event generation,
     * causing multiple
     * action events to be queued.  When processed,
     * the application sends these events one after the other, causing the
     * <code>Timer</code>'s listeners to receive a sequence of
     * events with no delay between them. Coalescing avoids this situation
     * by reducing multiple pending events to a single event.
     * <code>Timer</code>s
     * coalesce events by default.
     *
     * @param flag specify <code>false</code> to turn off coalescing
     */
    public void setCoalesce(boolean flag) {
        boolean old = coalesce;
        coalesce = flag;
        if (!old && coalesce) {
            // We must do this as otherwise if the Timer once notified
            // in !coalese mode notify will be stuck to true and never
            // become false.
            cancelEvent();
        }
    }

    /**
     * Returns <code>true</code> if the <code>Timer</code> coalesces
     * multiple pending action events.
     *
     * @return true if the {@code Timer} coalesces multiple pending
     *              action events
     * @see #setCoalesce
     */
    public boolean isCoalesce() {
        return coalesce;
    }

    /**
     * Sets the string that will be delivered as the action command
     * in <code>ActionEvent</code>s fired by this timer.
     * <code>null</code> is an acceptable value.
     *
     * @param command the action command
     * @since 1.6
     */
    public void setActionCommand(String command) {
        this.actionCommand = command;
    }

    /**
     * Returns the string that will be delivered as the action command
     * in <code>ActionEvent</code>s fired by this timer. May be
     * <code>null</code>, which is also the default.
     *
     * @return the action command used in firing events
     * @since 1.6
     */
    public String getActionCommand() {
        return actionCommand;
    }

    /**
     * Starts the <code>Timer</code>,
     * causing it to start sending action events
     * to its listeners.
     *
     * @see #stop
     */
    public void start() {
        synchronized (this) {
            if (run == null) {
                schedule(new Object(), getInitialDelay());
            }
        }
    }

    /**
     * Returns <code>true</code> if the <code>Timer</code> is running.
     *
     * @return true if the {@code Timer} is running, false otherwise
     * @see #start
     */
    public boolean isRunning() {
        synchronized (this) {
            return run != null;
        }
    }

    /**
     * Stops the <code>Timer</code>,
     * causing it to stop sending action events
     * to its listeners.
     *
     * @see #start
     */
    public void stop() {
        synchronized (this) {
            cancelEvent();
            if (wakeup != null) {
                wakeup.cancel(false);
            }
            wakeup = null;
            run = null;
        }
    }

    /**
     * Restarts the <code>Timer</code>,
     * canceling any pending firings and causing
     * it to fire with its initial delay.
     */
    public void restart() {
        synchronized (this) {
            stop();
            start();
        }
    }

    /**
     * Resets the internal state to indicate this Timer shouldn't notify
     * any of its listeners. This does not stop a repeatable Timer from
     * firing again, use <code>stop</code> for that.
     */
    void cancelEvent() {
        notify.set(false);
    }

    void post() {
         if (notify.compareAndSet(false, true) || !coalesce) {
             SwingUtilities.invokeLater(doPostEvent);
         }
    }

    // Guarded by this: schedules the next wakeup of the run identified by token.
    private void schedule(Object token, int wait) {
        run = token;
        wakeup = TimerQueue.EXECUTOR.schedule(new Runnable() {
            public void run() {
                ring(token);
            }
        }, wait, TimeUnit.MILLISECONDS);
    }

    // The TimerQueue's run loop for one timer: post, then requeue a repeating
    // timer delay ms after this wakeup (the JDK measures from the wakeup too).
    private void ring(Object token) {
        synchronized (this) {
            if (run != token) {
                return;
            }
            post();
            if (isRepeats()) {
                schedule(token, getDelay());
            } else {
                wakeup = null;
                run = null;
            }
        }
    }

    /*
     * We have to use readResolve because we can not initialize final
     * fields for deserialized object otherwise
     */
    private Object readResolve() {
        Timer timer = new Timer(getDelay(), null);
        timer.listenerList = listenerList;
        timer.initialDelay = initialDelay;
        timer.delay = delay;
        timer.repeats = repeats;
        timer.coalesce = coalesce;
        timer.actionCommand = actionCommand;
        return timer;
    }

    private void readObject(ObjectInputStream in)
        throws ClassNotFoundException, IOException
    {
        in.defaultReadObject();
    }

    /** The JDK's TimerQueue thread, reduced to the wakeups it schedules. */
    private static final class TimerQueue {
        static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "TimerQueue");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    }
}
