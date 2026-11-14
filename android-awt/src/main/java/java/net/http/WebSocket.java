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
package java.net.http;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * JDK 11 WebSocket client. Messages arrive whole ({@code last} is always {@code true});
 * on Android the wire is okhttp3, elsewhere an RFC 6455 socket client.
 */
public interface WebSocket {

    int NORMAL_CLOSURE = 1000;

    interface Builder {
        Builder header(String name, String value);

        Builder connectTimeout(Duration timeout);

        Builder subprotocols(String mostPreferred, String... lesserPreferred);

        CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener);
    }

    interface Listener {
        default void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        default CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            return null;
        }

        default CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        default CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        default CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        default CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return null;
        }

        default void onError(WebSocket webSocket, Throwable error) {
        }
    }

    CompletableFuture<WebSocket> sendText(CharSequence data, boolean last);

    CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last);

    CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    void request(long n);

    String getSubprotocol();

    boolean isOutputClosed();

    boolean isInputClosed();

    void abort();
}
