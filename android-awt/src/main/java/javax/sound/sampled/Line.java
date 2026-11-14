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

public interface Line extends AutoCloseable {
    Line.Info getLineInfo();

    void open() throws LineUnavailableException;
    @Override
    void close();

    boolean isOpen();
    Control[] getControls();
    boolean isControlSupported(Control.Type control);
    Control getControl(Control.Type control);
    void addLineListener(LineListener listener);
    void removeLineListener(LineListener listener);

    class Info {
        private final Class<?> lineClass;

        public Info(Class<?> lineClass) {
            this.lineClass = lineClass == null ? Line.class : lineClass;
        }

        public Class<?> getLineClass() {
            return lineClass;
        }

        /**
         * True when {@code info} is an instance of this info's class and describes a line class
         * assignable to this one: a request for a {@code DataLine} is met by a {@code Clip}.
         */
        public boolean matches(Info info) {
            if (!getClass().isInstance(info)) {
                return false;
            }
            return getLineClass().isAssignableFrom(info.getLineClass());
        }

        @Override
        public String toString() {
            String fullPackagePath = "javax.sound.sampled.";
            String initialString = String.valueOf(lineClass);
            int index = initialString.indexOf(fullPackagePath);
            if (index != -1) {
                return initialString.substring(0, index) + initialString.substring(index + fullPackagePath.length());
            }
            return initialString;
        }
    }
}
