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

public interface Mixer extends Line {
    Mixer.Info getMixerInfo();
    Line.Info[] getSourceLineInfo();
    Line.Info[] getTargetLineInfo();
    Line.Info[] getSourceLineInfo(Line.Info info);
    Line.Info[] getTargetLineInfo(Line.Info info);
    boolean isLineSupported(Line.Info info);
    Line getLine(Line.Info info) throws LineUnavailableException;

    /** How many lines matching {@code info} can be open at once; {@code NOT_SPECIFIED} for no limit. */
    int getMaxLines(Line.Info info);
    Line[] getSourceLines();
    Line[] getTargetLines();
    void synchronize(Line[] lines, boolean maintainSync);
    void unsynchronize(Line[] lines);
    boolean isSynchronizationSupported(Line[] lines, boolean maintainSync);

    class Info {
        private final String name;
        private final String vendor;
        private final String description;
        private final String version;

        protected Info(String name, String vendor, String description, String version) {
            this.name = name;
            this.vendor = vendor;
            this.description = description;
            this.version = version;
        }

        public final String getName()        { return name; }
        public final String getVendor()      { return vendor; }
        public final String getDescription() { return description; }
        public final String getVersion()     { return version; }

        @Override
        public final boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        public final int hashCode() {
            return super.hashCode();
        }

        @Override
        public final String toString() {
            return name + ", version " + version;
        }
    }
}
