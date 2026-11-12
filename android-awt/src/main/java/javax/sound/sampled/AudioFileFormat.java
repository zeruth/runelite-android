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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The container type, byte length, data format and frame length of an audio file. */
public class AudioFileFormat {

    private final Type type;
    private final int byteLength;
    private final AudioFormat format;
    private final int frameLength;
    private HashMap<String, Object> properties;

    protected AudioFileFormat(Type type, int byteLength, AudioFormat format, int frameLength) {
        this.type = type;
        this.byteLength = byteLength;
        this.format = format;
        this.frameLength = frameLength;
        this.properties = null;
    }

    public AudioFileFormat(Type type, AudioFormat format, int frameLength) {
        this(type, AudioSystem.NOT_SPECIFIED, format, frameLength);
    }

    public AudioFileFormat(Type type, AudioFormat format, int frameLength, Map<String, Object> properties) {
        this(type, AudioSystem.NOT_SPECIFIED, format, frameLength);
        this.properties = new HashMap<String, Object>(properties);
    }

    public Type getType() {
        return type;
    }

    public int getByteLength() {
        return byteLength;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public int getFrameLength() {
        return frameLength;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> properties() {
        Map<String, Object> ret;
        if (properties == null) {
            ret = new HashMap<String, Object>(0);
        } else {
            ret = (Map<String, Object>) properties.clone();
        }
        return Collections.unmodifiableMap(ret);
    }

    public Object getProperty(String key) {
        if (properties == null) {
            return null;
        }
        return properties.get(key);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (type != null) {
            buf.append(type).append(" (.").append(type.getExtension()).append(") file");
        } else {
            buf.append("unknown file format");
        }
        if (byteLength != AudioSystem.NOT_SPECIFIED) {
            buf.append(", byte length: ").append(byteLength);
        }
        buf.append(", data format: ").append(format);
        if (frameLength != AudioSystem.NOT_SPECIFIED) {
            buf.append(", frame length: ").append(frameLength);
        }
        return buf.toString();
    }

    /** A file container; two types are equal when their names are. */
    public static class Type {

        public static final Type WAVE = new Type("WAVE", "wav");
        public static final Type AU = new Type("AU", "au");
        public static final Type AIFF = new Type("AIFF", "aif");
        public static final Type AIFC = new Type("AIFF-C", "aifc");
        public static final Type SND = new Type("SND", "snd");

        private final String name;
        private final String extension;

        public Type(String name, String extension) {
            this.name = name;
            this.extension = extension;
        }

        @Override
        public final boolean equals(Object obj) {
            if (toString() == null) {
                return obj != null && obj.toString() == null;
            }
            if (obj instanceof Type) {
                return toString().equals(obj.toString());
            }
            return false;
        }

        @Override
        public final int hashCode() {
            if (toString() == null) {
                return 0;
            }
            return toString().hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }

        public String getExtension() {
            return extension;
        }
    }
}
