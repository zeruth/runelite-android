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

import java.util.EventObject;

public class LineEvent extends EventObject {
    private static final long serialVersionUID = -1274246333366502301L;

    public static class Type {
        public static final Type OPEN = new Type("Open");
        public static final Type CLOSE = new Type("Close");
        public static final Type START = new Type("Start");
        public static final Type STOP = new Type("Stop");

        private final String name;
        public Type(String name) { this.name = name; }
        @Override public final boolean equals(Object o) { return super.equals(o); }
        @Override public final int hashCode() { return super.hashCode(); }
        @Override public String toString() { return name; }
    }

    private final transient Type type;
    private final long position;

    public LineEvent(Line line, Type type, long position) {
        super(line);
        this.type = type;
        this.position = position;
    }

    public final Line getLine() { return (Line) getSource(); }
    public final Type getType() { return type; }
    public final long getFramePosition() { return position; }

    @Override
    public String toString() {
        String sType = type == null ? "" : type + " ";
        String sLine = getLine() == null ? "null" : getLine().toString();
        return sType + "event from line " + sLine;
    }
}
