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
package java.awt.event;

import java.awt.AWTEvent;
import java.awt.ActiveEvent;

/**
 * Runs a {@link Runnable} when dispatched on the event dispatch thread;
 * {@link java.awt.EventQueue#invokeLater} posts one per task.
 */
public class InvocationEvent extends AWTEvent implements ActiveEvent {
    private static final long serialVersionUID = 436056344909459450L;

    public static final int INVOCATION_FIRST = 1200;
    public static final int INVOCATION_DEFAULT = INVOCATION_FIRST;
    public static final int INVOCATION_LAST = INVOCATION_DEFAULT;

    protected Runnable runnable;
    protected volatile Object notifier;
    private final Runnable listener;
    private volatile boolean dispatched;
    protected boolean catchExceptions;
    private Exception exception;
    private Throwable throwable;
    private final long when;

    public InvocationEvent(Object source, Runnable runnable) {
        this(source, INVOCATION_DEFAULT, runnable, null, null, false);
    }

    public InvocationEvent(Object source, Runnable runnable, Object notifier, boolean catchThrowables) {
        this(source, INVOCATION_DEFAULT, runnable, notifier, null, catchThrowables);
    }

    public InvocationEvent(Object source, Runnable runnable, Runnable listener, boolean catchThrowables) {
        this(source, INVOCATION_DEFAULT, runnable, null, listener, catchThrowables);
    }

    protected InvocationEvent(Object source, int id, Runnable runnable, Object notifier, boolean catchThrowables) {
        this(source, id, runnable, notifier, null, catchThrowables);
    }

    private InvocationEvent(Object source, int id, Runnable runnable, Object notifier, Runnable listener,
                            boolean catchThrowables) {
        super(source, id);
        this.runnable = runnable;
        this.notifier = notifier;
        this.listener = listener;
        this.catchExceptions = catchThrowables;
        this.when = System.currentTimeMillis();
    }

    /**
     * Runs the runnable, then wakes the notifier and the listener. With {@code catchExceptions}
     * a throwable is kept for {@link #getThrowable()}; without, it propagates to the dispatcher.
     */
    public void dispatch() {
        try {
            if (catchExceptions) {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    if (t instanceof Exception) {
                        exception = (Exception) t;
                    }
                    throwable = t;
                }
            } else {
                runnable.run();
            }
        } finally {
            finishedDispatching(true);
        }
    }

    public Exception getException() {
        return catchExceptions ? exception : null;
    }

    public Throwable getThrowable() {
        return catchExceptions ? throwable : null;
    }

    public long getWhen() {
        return when;
    }

    public boolean isDispatched() {
        return dispatched;
    }

    private void finishedDispatching(boolean dispatched) {
        this.dispatched = dispatched;
        Object lock = notifier;
        if (lock != null) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        if (listener != null) {
            listener.run();
        }
    }

    @Override
    public String paramString() {
        String typeStr = id == INVOCATION_DEFAULT ? "INVOCATION_DEFAULT" : "unknown type";
        return typeStr + ",runnable=" + runnable + ",notifier=" + notifier
            + ",catchExceptions=" + catchExceptions + ",when=" + when;
    }
}
