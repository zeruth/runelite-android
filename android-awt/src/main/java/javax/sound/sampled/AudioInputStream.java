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

/**
 * A stream of whole audio frames: reads, skips and {@link #available} are trimmed to frame
 * multiples and to {@link #getFrameLength}, and a partial frame from the source is held back
 * until the rest of it arrives, as the JDK's stream does.
 */
public class AudioInputStream extends InputStream {

    private final InputStream stream;

    protected AudioFormat format;
    protected long frameLength;
    protected int frameSize;
    protected long framePos;

    private long markpos;
    private byte[] pushBackBuffer;
    private int pushBackLen;
    private byte[] markPushBackBuffer;
    private int markPushBackLen;

    public AudioInputStream(InputStream stream, AudioFormat format, long length) {
        this.format = format;
        this.frameLength = length;
        this.frameSize = frameSizeOf(format);
        this.stream = stream;
        this.framePos = 0;
        this.markpos = 0;
    }

    public AudioInputStream(TargetDataLine line) {
        this.format = line.getFormat();
        this.frameLength = AudioSystem.NOT_SPECIFIED;
        this.frameSize = frameSizeOf(format);
        this.stream = new TargetDataLineInputStream(line);
        this.framePos = 0;
        this.markpos = 0;
    }

    private static int frameSizeOf(AudioFormat format) {
        int size = format.getFrameSize();
        return size == AudioSystem.NOT_SPECIFIED || size <= 0 ? 1 : size;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public long getFrameLength() {
        return frameLength;
    }

    @Override
    public int read() throws IOException {
        if (frameSize != 1) {
            throw new IOException("cannot read a single byte if frame size > 1");
        }
        byte[] data = new byte[1];
        int count = read(data);
        if (count <= 0) {
            return -1;
        }
        return data[0] & 0xFF;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if ((len % frameSize) != 0) {
            len -= (len % frameSize);
            if (len == 0) {
                return 0;
            }
        }
        if (frameLength != AudioSystem.NOT_SPECIFIED) {
            if (framePos >= frameLength) {
                return -1;
            }
            if ((len / frameSize) > (frameLength - framePos)) {
                len = (int) (frameLength - framePos) * frameSize;
            }
        }

        int bytesRead = 0;
        int thisOff = off;
        if (pushBackLen > 0 && len >= pushBackLen) {
            System.arraycopy(pushBackBuffer, 0, b, off, pushBackLen);
            thisOff += pushBackLen;
            len -= pushBackLen;
            bytesRead += pushBackLen;
            pushBackLen = 0;
        }

        int thisBytesRead = stream.read(b, thisOff, len);
        if (thisBytesRead == -1) {
            return -1;
        }
        if (thisBytesRead > 0) {
            bytesRead += thisBytesRead;
        }
        if (bytesRead > 0) {
            pushBackLen = bytesRead % frameSize;
            if (pushBackLen > 0) {
                if (pushBackBuffer == null) {
                    pushBackBuffer = new byte[frameSize];
                }
                System.arraycopy(b, off + bytesRead - pushBackLen, pushBackBuffer, 0, pushBackLen);
                bytesRead -= pushBackLen;
            }
            framePos += bytesRead / frameSize;
        }
        return bytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
        if ((n % frameSize) != 0) {
            n -= (n % frameSize);
        }
        if (frameLength != AudioSystem.NOT_SPECIFIED && (n / frameSize) > (frameLength - framePos)) {
            n = (frameLength - framePos) * frameSize;
        }
        long skipped = stream.skip(n);
        if (skipped % frameSize != 0) {
            throw new IOException("Could not skip an integer number of frames.");
        }
        if (skipped >= 0) {
            framePos += skipped / frameSize;
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        int count = stream.available();
        if (frameLength != AudioSystem.NOT_SPECIFIED && (count / frameSize) > (frameLength - framePos)) {
            return (int) (frameLength - framePos) * frameSize;
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public void mark(int readlimit) {
        stream.mark(readlimit);
        if (markSupported()) {
            markpos = framePos;
            markPushBackLen = pushBackLen;
            if (markPushBackLen > 0) {
                if (markPushBackBuffer == null) {
                    markPushBackBuffer = new byte[frameSize];
                }
                System.arraycopy(pushBackBuffer, 0, markPushBackBuffer, 0, markPushBackLen);
            }
        }
    }

    @Override
    public void reset() throws IOException {
        stream.reset();
        framePos = markpos;
        pushBackLen = markPushBackLen;
        if (pushBackLen > 0) {
            if (pushBackBuffer == null) {
                pushBackBuffer = new byte[frameSize];
            }
            System.arraycopy(markPushBackBuffer, 0, pushBackBuffer, 0, pushBackLen);
        }
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    /** Reads a capture line; closing the stream stops and closes the line. */
    private static final class TargetDataLineInputStream extends InputStream {
        private final TargetDataLine line;

        TargetDataLineInputStream(TargetDataLine line) {
            this.line = line;
        }

        @Override
        public int available() throws IOException {
            return line.available();
        }

        @Override
        public void close() throws IOException {
            if (line.isActive()) {
                line.flush();
                line.stop();
            }
            line.close();
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int count = read(b, 0, 1);
            if (count <= 0) {
                return -1;
            }
            return b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return line.read(b, off, len);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }
    }
}
