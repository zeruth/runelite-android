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
package net.runelite.awt;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stub convention of the shadow tree. A shadow member that cannot work on Android
 * either {@linkplain #fail(String) fails} with the fully qualified member in the message,
 * or is a {@linkplain #noop(String, String) documented no-op} that says so once at debug
 * level; a member that works only partially carries {@link ShadowStub}. The
 * {@code :android-awt:stubReport} task lists every call site and annotation from the
 * compiled classes, so a stub is never silent and never lost.
 *
 * <p>This class is plain Java with no {@code android.*} reference and no dependency
 * outside {@code java.base} on purpose: the host contract runner compiles it next to the
 * shadow classes of every module that call it.
 */
public final class AwtUnsupported {

    /** Receives each no-op's first call; {@code AwtCompat.ensureReady} routes it to logcat. */
    public interface Sink {
        void log(String member, String reason);
    }

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    // Silent until the Android side installs the logcat sink: the host JDK has no debug
    // channel worth writing to, and a no-op is still counted once per member.
    private static volatile Sink sink = new Sink() {
        @Override
        public void log(String member, String reason) {
        }
    };

    private AwtUnsupported() {
    }

    /**
     * Throws {@code UnsupportedOperationException} naming {@code member}, e.g.
     * {@code "java.awt.Desktop.mail(URI)"}. The return type only exists so a call site in a
     * value-returning method can write {@code throw AwtUnsupported.fail(...)} and satisfy
     * javac's flow analysis; this method never returns.
     */
    public static UnsupportedOperationException fail(String member) {
        throw new UnsupportedOperationException(member + " is not supported by android-awt");
    }

    /**
     * {@link #fail(String)} for a member that works except for one input or mode, which
     * {@code detail} names, e.g. {@code "TYPE_FLOAT and TYPE_DOUBLE transfer types"}.
     */
    public static UnsupportedOperationException fail(String member, String detail) {
        throw new UnsupportedOperationException(member + " is not supported by android-awt: " + detail);
    }

    /**
     * Records that {@code member} deliberately does nothing on Android because of
     * {@code reason}. Logged once per member, at debug level, through the installed
     * {@link Sink}; cheap enough for members called per frame.
     */
    public static void noop(String member, String reason) {
        if (REPORTED.add(member)) {
            sink.log(member, reason);
        }
    }

    /** Installs the debug-level logger; a {@code null} argument is ignored. */
    public static void setSink(Sink replacement) {
        if (replacement != null) {
            sink = replacement;
        }
    }
}
