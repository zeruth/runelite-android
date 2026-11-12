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
package net.runelite.awt.impl;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.stream.ImageInputStream;

/**
 * Stands in for the {@code sun.java2d.Disposer} and {@code com.sun.imageio.stream}
 * helpers the javax.imageio.stream classes lean on: a phantom-reference disposer that
 * closes a stream's file once the stream is unreachable, the finalizer object used for
 * subclasses (which may override {@code finalize}), and the shutdown-hook queue that
 * deletes cache files left open at exit.
 */
public final class ImageIOStreamSupport {
    private ImageIOStreamSupport() {
    }

    /** What {@code sun.java2d.DisposerRecord} is: the cleanup run once its referent is gone. */
    public interface DisposerRecord {
        void dispose();
    }

    /** Closes one {@link Closeable} at most once. */
    public static final class CloseableDisposerRecord implements DisposerRecord {
        private Closeable closeable;

        public CloseableDisposerRecord(Closeable closeable) {
            this.closeable = closeable;
        }

        public synchronized void dispose() {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                } finally {
                    closeable = null;
                }
            }
        }
    }

    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<Object>();
    private static final Map<Reference<Object>, DisposerRecord> RECORDS =
        new ConcurrentHashMap<Reference<Object>, DisposerRecord>();
    private static Thread disposerThread;

    /** Runs {@code record} on a daemon thread once {@code referent} is phantom reachable. */
    public static void addRecord(Object referent, DisposerRecord record) {
        RECORDS.put(new PhantomReference<Object>(referent, QUEUE), record);
        startDisposer();
    }

    private static synchronized void startDisposer() {
        if (disposerThread != null) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Reference<?> ref = QUEUE.remove();
                        DisposerRecord rec = RECORDS.remove(ref);
                        ref.clear();
                        if (rec != null) {
                            rec.dispose();
                        }
                    } catch (InterruptedException e) {
                        return;
                    } catch (RuntimeException e) {
                        // A failing record must not kill the thread that disposes the others.
                    }
                }
            }
        }, "ImageIO-Disposer");
        t.setDaemon(true);
        t.start();
        disposerThread = t;
    }

    /**
     * {@code com.sun.imageio.stream.StreamFinalizer}: the referent a subclass of a cache or
     * file stream holds, so the stream is closed at finalization even when the subclass
     * overrides {@code finalize()} without calling super.
     */
    public static final class StreamFinalizer {
        private ImageInputStream stream;

        public StreamFinalizer(ImageInputStream stream) {
            this.stream = stream;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                stream.close();
            } catch (IOException e) {
            } finally {
                stream = null;
                super.finalize();
            }
        }
    }

    /** {@code com.sun.imageio.stream.StreamCloser.CloseAction}. */
    public static final class CloseAction {
        private ImageInputStream iis;

        private CloseAction(ImageInputStream iis) {
            this.iis = iis;
        }

        public void performAction() throws IOException {
            if (iis != null) {
                iis.close();
            }
        }
    }

    // Weak keys: a stream that is collected drops out without close() having been called,
    // its Disposer record has deleted the cache file by then.
    private static WeakHashMap<CloseAction, Object> closeQueue;
    private static Thread closeHook;

    public static CloseAction createCloseAction(ImageInputStream iis) {
        return new CloseAction(iis);
    }

    public static synchronized void addToQueue(CloseAction ca) {
        if (closeQueue == null) {
            closeQueue = new WeakHashMap<CloseAction, Object>();
        }
        closeQueue.put(ca, null);
        if (closeHook == null) {
            closeHook = new Thread(new Runnable() {
                public void run() {
                    Set<CloseAction> actions;
                    synchronized (ImageIOStreamSupport.class) {
                        actions = new HashSet<CloseAction>(closeQueue.keySet());
                    }
                    for (CloseAction ca : actions) {
                        if (ca != null) {
                            try {
                                ca.performAction();
                            } catch (IOException e) {
                            }
                        }
                    }
                }
            }, "ImageIO-StreamCloser");
            try {
                Runtime.getRuntime().addShutdownHook(closeHook);
            } catch (IllegalStateException e) {
                // Already shutting down: nothing will run the hook, the files stay behind.
            }
        }
    }

    public static synchronized void removeFromQueue(CloseAction ca) {
        if (closeQueue != null) {
            closeQueue.remove(ca);
        }
    }
}
