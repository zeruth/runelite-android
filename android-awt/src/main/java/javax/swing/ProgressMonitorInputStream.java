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
package javax.swing;

import java.awt.Component;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * Monitors the progress of reading from some InputStream. It creates a
 * {@link ProgressMonitor} over the stream's {@code available()} size at
 * construction; reading advances it, and once the user cancels, the next read
 * throws {@code InterruptedIOException}.
 */
public class ProgressMonitorInputStream extends FilterInputStream {
    private ProgressMonitor monitor;
    private int nread = 0;
    private int size = 0;

    /**
     * Constructs an object to monitor the progress of an input stream.
     *
     * @param message descriptive text to put in the dialog box if one is popped up
     * @param parentComponent the trigger for the dialog box
     * @param in the input stream to be monitored
     */
    public ProgressMonitorInputStream(Component parentComponent,
                                      Object message,
                                      InputStream in) {
        super(in);
        try {
            size = in.available();
        } catch (IOException ioe) {
            size = 0;
        }
        monitor = new ProgressMonitor(parentComponent, message, null, 0, size);
    }

    /**
     * Get the ProgressMonitor object being used by this stream. Normally this
     * isn't needed unless you want to do something like change the descriptive
     * text partway through reading the file.
     */
    public ProgressMonitor getProgressMonitor() {
        return monitor;
    }

    /** Overrides {@code FilterInputStream.read} to update the progress monitor after the read. */
    @Override
    public int read() throws IOException {
        int c = in.read();
        if (c >= 0) {
            monitor.setProgress(++nread);
        }
        checkCanceled();
        return c;
    }

    /** Overrides {@code FilterInputStream.read} to update the progress monitor after the read. */
    @Override
    public int read(byte[] b) throws IOException {
        int nr = in.read(b);
        if (nr > 0) {
            monitor.setProgress(nread += nr);
        }
        checkCanceled();
        return nr;
    }

    /** Overrides {@code FilterInputStream.read} to update the progress monitor after the read. */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int nr = in.read(b, off, len);
        if (nr > 0) {
            monitor.setProgress(nread += nr);
        }
        checkCanceled();
        return nr;
    }

    /** Overrides {@code FilterInputStream.skip} to update the progress monitor after the skip. */
    @Override
    public long skip(long n) throws IOException {
        long nr = in.skip(n);
        if (nr > 0) {
            monitor.setProgress(nread += nr);
        }
        return nr;
    }

    /** Overrides {@code FilterInputStream.close} to close the progress monitor as well as the stream. */
    @Override
    public void close() throws IOException {
        in.close();
        monitor.close();
    }

    /** Overrides {@code FilterInputStream.reset} to reset the progress monitor as well as the stream. */
    @Override
    public synchronized void reset() throws IOException {
        in.reset();
        nread = size - in.available();
        monitor.setProgress(nread);
    }

    private void checkCanceled() throws InterruptedIOException {
        if (monitor.isCanceled()) {
            InterruptedIOException exc = new InterruptedIOException("progress");
            exc.bytesTransferred = nread;
            throw exc;
        }
    }
}
