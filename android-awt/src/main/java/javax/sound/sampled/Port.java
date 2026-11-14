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

/**
 * A physical audio connector. Android routes audio itself and exposes no per-connector
 * mixer, so the Android mixer reports no ports; the type exists for callers that query
 * {@link AudioSystem#isLineSupported} with the {@link Info} constants.
 */
public interface Port extends Line {

    class Info extends Line.Info {

        public static final Info MICROPHONE = new Info(Port.class, "MICROPHONE", true);
        public static final Info LINE_IN = new Info(Port.class, "LINE_IN", true);
        public static final Info COMPACT_DISC = new Info(Port.class, "COMPACT_DISC", true);
        public static final Info SPEAKER = new Info(Port.class, "SPEAKER", false);
        public static final Info HEADPHONE = new Info(Port.class, "HEADPHONE", false);
        public static final Info LINE_OUT = new Info(Port.class, "LINE_OUT", false);

        private final String name;
        private final boolean isSource;

        public Info(Class<?> lineClass, String name, boolean isSource) {
            super(lineClass);
            this.name = name;
            this.isSource = isSource;
        }

        public String getName() {
            return name;
        }

        public boolean isSource() {
            return isSource;
        }

        @Override
        public boolean matches(Line.Info info) {
            if (!super.matches(info)) {
                return false;
            }
            Info other = (Info) info;
            return name.equals(other.getName()) && isSource == other.isSource();
        }

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
            return name + (isSource ? " source" : " target") + " port";
        }
    }
}
