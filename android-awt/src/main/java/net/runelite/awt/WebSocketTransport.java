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

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;

/**
 * The wire under {@code java.net.http.WebSocket}. The shadow keeps the JDK semantics (demand,
 * fragment assembly, the closing handshake, listener serialisation) in one place and asks a
 * transport only to move whole messages. The APK supplies an okhttp3-backed transport as
 * {@value #ANDROID_TRANSPORT}; without it (the host contract runs, a stripped APK) the
 * RFC 6455 client in {@link SocketWebSocketTransport} is used.
 *
 * <p>Only {@code java.base} types appear here on purpose: the host contract runner compiles
 * {@code net.runelite.awt} into {@code java.base}, which reads no other module.
 */
public interface WebSocketTransport {

    /** Class name the shadow loads reflectively before falling back to the socket transport. */
    String ANDROID_TRANSPORT = "net.runelite.mp.http.OkHttpWebSocketTransport";

    /**
     * Starts the opening handshake and returns at once; the outcome arrives as
     * {@link Events#opened} or {@link Events#failed}. {@code ssl} is {@code null} for the
     * platform default context, {@code proxy} {@code null} for the default selector, and
     * {@code connectTimeoutMillis} 0 for none.
     */
    Connection open(URI uri, Map<String, List<String>> headers, List<String> subprotocols,
                    long connectTimeoutMillis, SSLSocketFactory ssl, ProxySelector proxy,
                    Events events) throws IOException;

    /** Outbound side of an open socket. Calls are serialised by the caller. */
    interface Connection {
        void text(String message) throws IOException;

        void binary(ByteBuffer message) throws IOException;

        /** Returns {@code false} when the transport cannot send an application ping. */
        boolean ping(ByteBuffer payload) throws IOException;

        /** Returns {@code false} when the transport cannot send an unsolicited pong. */
        boolean pong(ByteBuffer payload) throws IOException;

        /** Sends the Close frame; the transport releases the socket once both sides closed. */
        void close(int code, String reason) throws IOException;

        /** Drops the connection without a closing handshake. */
        void abort();
    }

    /** Inbound side; called from one transport thread at a time, whole messages only. */
    interface Events {
        void opened(Connection connection, String subprotocol);

        void text(String message);

        void binary(ByteBuffer message);

        /** The transport has already answered with a Pong when it reports a Ping. */
        void ping(ByteBuffer payload);

        void pong(ByteBuffer payload);

        /** The peer's Close frame; 1005 when it carried no status. */
        void closing(int code, String reason);

        /**
         * The connection failed. Before {@link #opened} {@code status} is the handshake
         * response status (0 when no response arrived) and {@code headers} its fields.
         */
        void failed(Throwable error, int status, Map<String, List<String>> headers);
    }
}
