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
package javax.sound.sampled;

import java.io.IOException;
import java.io.InputStream;

public class AudioInputStream extends InputStream {
    private final InputStream stream;
    private final AudioFormat format;
    private final long length;
    private long pos;

    public AudioInputStream(InputStream stream, AudioFormat format, long length) {
        this.stream = stream;
        this.format = format;
        this.length = length;
    }

    public AudioInputStream(TargetDataLine line) {
        this.stream = null;
        this.format = line.getFormat();
        this.length = AudioSystem.NOT_SPECIFIED;
    }

    public AudioFormat getFormat() { return format; }
    public long getFrameLength()   { return length; }

    @Override public int read() throws IOException { pos++; return stream == null ? -1 : stream.read(); }
    @Override public int read(byte[] b, int off, int len) throws IOException {
        int n = stream == null ? -1 : stream.read(b, off, len);
        if (n > 0) pos += n;
        return n;
    }
    @Override public long skip(long n) throws IOException { return stream == null ? 0 : stream.skip(n); }
    @Override public int available() throws IOException { return stream == null ? 0 : stream.available(); }
    @Override public void close() throws IOException { if (stream != null) stream.close(); }
}
