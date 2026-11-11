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
package java.awt.datatransfer;

import java.io.Serializable;

public class DataFlavor implements Cloneable, Serializable {
    private static final long serialVersionUID = 8367026044764648243L;

    public static final DataFlavor stringFlavor = new DataFlavor("application/x-java-serialized-object; class=java.lang.String", "Unicode String", String.class);
    public static final DataFlavor plainTextFlavor = new DataFlavor("text/plain; charset=unicode; class=java.io.InputStream", "Plain Text");
    public static final DataFlavor javaFileListFlavor = new DataFlavor("application/x-java-file-list", "Java File List");

    private final String mimeType;
    private final String humanName;
    private final Class<?> representationClass;

    public DataFlavor() {
        this("application/x-java-serialized-object", "(no description)");
    }

    public DataFlavor(String mimeType, String humanName) {
        this(mimeType, humanName, Object.class);
    }

    public DataFlavor(String mimeType, String humanName, Class<?> repClass) {
        this.mimeType = mimeType;
        this.humanName = humanName;
        this.representationClass = repClass;
    }

    public DataFlavor(Class<?> representationClass, String humanName) {
        this("application/x-java-serialized-object; class=" + representationClass.getName(), humanName, representationClass);
    }

    public String getMimeType() { return mimeType; }
    public String getHumanPresentableName() { return humanName; }
    public Class<?> getRepresentationClass() { return representationClass; }
    public boolean isMimeTypeEqual(String mt) { return mimeType.equalsIgnoreCase(mt); }
    public boolean isMimeTypeEqual(DataFlavor flavor) { return flavor != null && isMimeTypeEqual(flavor.mimeType); }
    public boolean equals(DataFlavor f) { return f != null && isMimeTypeEqual(f) && representationClass.equals(f.representationClass); }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DataFlavor && equals((DataFlavor) obj);
    }

    @Override
    public int hashCode() {
        return mimeType.hashCode() ^ representationClass.hashCode();
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
