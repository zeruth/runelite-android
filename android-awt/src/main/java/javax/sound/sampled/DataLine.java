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

public interface DataLine extends Line {
    void drain();
    void flush();
    void start();
    void stop();
    boolean isRunning();
    boolean isActive();
    AudioFormat getFormat();
    int getBufferSize();
    int available();
    int getFramePosition();
    long getLongFramePosition();
    long getMicrosecondPosition();
    float getLevel();

    class Info extends Line.Info {
        private final AudioFormat[] formats;
        private final int minBufferSize;
        private final int maxBufferSize;

        public Info(Class<?> lineClass, AudioFormat format) {
            this(lineClass, new AudioFormat[]{format}, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED);
        }

        public Info(Class<?> lineClass, AudioFormat format, int bufferSize) {
            this(lineClass, new AudioFormat[]{format}, bufferSize, bufferSize);
        }

        public Info(Class<?> lineClass, AudioFormat[] formats, int minBufferSize, int maxBufferSize) {
            super(lineClass);
            this.formats = formats == null ? new AudioFormat[0] : formats.clone();
            this.minBufferSize = minBufferSize;
            this.maxBufferSize = maxBufferSize;
        }

        public AudioFormat[] getFormats() {
            return formats.clone();
        }

        public boolean isFormatSupported(AudioFormat format) {
            for (AudioFormat f : formats) {
                if (format.matches(f)) return true;
            }
            return false;
        }

        public int getMinBufferSize() { return minBufferSize; }
        public int getMaxBufferSize() { return maxBufferSize; }
    }
}
