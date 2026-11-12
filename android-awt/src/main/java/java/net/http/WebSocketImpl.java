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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import net.runelite.awt.SocketWebSocketTransport;
import net.runelite.awt.WebSocketTransport;

/**
 * The JDK WebSocket semantics over a {@link WebSocketTransport}: demand-counted, strictly
 * sequential listener calls, outbound fragments joined into one message, and the closing
 * handshake answered with the peer's status once {@code onClose}'s stage completes.
 */
final class WebSocketImpl implements WebSocket, WebSocketTransport.Events {

    private static final WebSocketTransport TRANSPORT = transport();

    private static WebSocketTransport transport() {
        try {
            return (WebSocketTransport) Class.forName(WebSocketTransport.ANDROID_TRANSPORT).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException absent) {
            return new SocketWebSocketTransport();
        }
    }

    static final class BuilderImpl implements WebSocket.Builder {
        final HttpClient client;
        final Map<String, List<String>> headers = new LinkedHashMap<>();
        final List<String> subprotocols = new ArrayList<>();
        Duration timeout;

        BuilderImpl(HttpClient client) { this.client = client; }

        @Override
        public Builder header(String name, String value) {
            HttpRequest.RequestBuilder.validateHeader(name, value);
            if (name.toLowerCase(Locale.ROOT).startsWith("sec-websocket-")) throw new IllegalArgumentException("Restricted header name: " + name);
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            return this;
        }

        @Override
        public Builder connectTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required");
            this.timeout = timeout;
            return this;
        }

        @Override
        public Builder subprotocols(String mostPreferred, String... lesserPreferred) {
            List<String> chosen = new ArrayList<>();
            chosen.add(Objects.requireNonNull(mostPreferred));
            for (String protocol : lesserPreferred) chosen.add(Objects.requireNonNull(protocol));
            Set<String> seen = new HashSet<>();
            for (String protocol : chosen) {
                if (!protocol.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new IllegalArgumentException("Bad subprotocol: " + protocol);
                if (!seen.add(protocol.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate subprotocol: " + protocol);
            }
            subprotocols.clear();
            subprotocols.addAll(chosen);
            return this;
        }

        @Override
        public CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener) {
            Objects.requireNonNull(uri);
            Objects.requireNonNull(listener);
            CompletableFuture<WebSocket> failed = new CompletableFuture<>();
            if (!("ws".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getRawFragment() != null) {
                failed.completeExceptionally(new IllegalArgumentException("ws or wss URI without a fragment required: " + uri));
                return failed;
            }
            Map<String, List<String>> sent = new LinkedHashMap<>();
            headers.forEach((key, values) -> sent.put(key, Collections.unmodifiableList(new ArrayList<>(values))));
            WebSocketImpl socket = new WebSocketImpl(uri, listener, new ArrayList<>(subprotocols), Collections.unmodifiableMap(sent));
            Duration connect = timeout != null ? timeout : client.connectTimeout().orElse(null);
            SSLSocketFactory ssl = null;
            try {
                if (client.sslContext() != SSLContext.getDefault()) ssl = client.sslContext().getSocketFactory();
            } catch (java.security.NoSuchAlgorithmException e) {
                ssl = client.sslContext().getSocketFactory();
            }
            try {
                WebSocketTransport.Connection pending = TRANSPORT.open(uri, socket.headers, socket.requested,
                    connect == null ? 0 : Math.max(1, connect.toMillis()), ssl, client.proxy().orElse(null), socket);
                socket.attach(pending);
            } catch (IOException | RuntimeException error) {
                socket.opening.completeExceptionally(error);
            }
            return socket.opening;
        }
    }

    private static final int OPEN = 0, TEXT = 1, BINARY = 2, PING = 3, PONG = 4, CLOSE = 5, ERROR = 6;

    private static final class Event {
        final int kind; final Object data; final int code;
        Event(int kind, Object data, int code) { this.kind = kind; this.data = data; this.code = code; }
        boolean counted() { return kind != OPEN && kind != ERROR; }
    }

    final URI uri; final Listener listener; final List<String> requested; final Map<String, List<String>> headers;
    final CompletableFuture<WebSocket> opening = new CompletableFuture<>();
    private final Object lock = new Object(), sendLock = new Object();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private volatile WebSocketTransport.Connection connection;
    private volatile String subprotocol = "";
    private volatile boolean inputClosed, outputClosed;
    private boolean opened, dispatching, terminated;
    private long demand;
    private StringBuilder textPart;
    private ByteArrayOutputStream binaryPart;

    private WebSocketImpl(URI uri, Listener listener, List<String> requested, Map<String, List<String>> headers) {
        this.uri = uri; this.listener = listener; this.requested = Collections.unmodifiableList(requested); this.headers = headers;
        // A cancelled or failed buildAsync must not leave a half-open socket behind.
        opening.whenComplete((value, error) -> {
            WebSocketTransport.Connection current = connection;
            if (error != null && current != null) current.abort();
        });
    }

    private void attach(WebSocketTransport.Connection pending) {
        synchronized (lock) {
            if (connection == null) connection = pending;
        }
        if (opening.isCompletedExceptionally()) pending.abort();
    }

    // ---- inbound, from the transport thread ----

    @Override
    public void opened(WebSocketTransport.Connection opened, String protocol) {
        synchronized (lock) { connection = opened; }
        if (protocol != null && !protocol.isEmpty() && !requested.contains(protocol)) {
            opened.abort();
            opening.completeExceptionally(new IOException("Server chose a subprotocol that was not offered: " + protocol));
            return;
        }
        if (opening.isDone()) {
            opened.abort();
            return;
        }
        subprotocol = protocol == null ? "" : protocol;
        synchronized (lock) { this.opened = true; }
        enqueue(new Event(OPEN, null, 0));
        opening.complete(this);
    }

    @Override public void text(String message) { enqueue(new Event(TEXT, message, 0)); }
    @Override public void binary(ByteBuffer message) { enqueue(new Event(BINARY, message, 0)); }
    @Override public void ping(ByteBuffer payload) { enqueue(new Event(PING, payload, 0)); }
    @Override public void pong(ByteBuffer payload) { enqueue(new Event(PONG, payload, 0)); }
    @Override public void closing(int code, String reason) { enqueue(new Event(CLOSE, reason, code)); }

    @Override
    public void failed(Throwable error, int status, Map<String, List<String>> fields) {
        boolean wasOpen;
        synchronized (lock) { wasOpen = opened; }
        if (!wasOpen) {
            opening.completeExceptionally(handshakeFailure(error, status, fields));
            return;
        }
        outputClosed = true;
        synchronized (lock) {
            if (terminated) return;
            // onError is not demand-counted: it overtakes whatever the listener has not asked for.
            Event open = events.peek() != null && events.peek().kind == OPEN ? events.poll() : null;
            events.clear();
            if (open != null) events.add(open);
            events.add(new Event(ERROR, error, 0));
        }
        drain();
    }

    private Throwable handshakeFailure(Throwable error, int status, Map<String, List<String>> fields) {
        if (status > 0) {
            HttpRequest request = null;
            URI http = uri;
            try {
                http = new URI("wss".equalsIgnoreCase(uri.getScheme()) ? "https" : "http", uri.getRawSchemeSpecificPart(), null);
                request = HttpRequest.newBuilder(http).build();
            } catch (URISyntaxException | IllegalArgumentException ignored) {
            }
            HttpHeaders received = HttpHeaders.of(fields == null ? Collections.<String, List<String>>emptyMap() : fields, (name, value) -> true);
            return new WebSocketHandshakeException(new HttpClient.Response<Void>(status, request, received, null, null, http, null)).initCause(error);
        }
        if (error instanceof SocketTimeoutException) {
            HttpConnectTimeoutException timeout = new HttpConnectTimeoutException("WebSocket connect timed out: " + uri);
            timeout.initCause(error);
            return timeout;
        }
        return error;
    }

    private void enqueue(Event event) {
        synchronized (lock) {
            if (terminated) return;
            events.add(event);
        }
        drain();
    }

    private void drain() {
        while (true) {
            Event event;
            synchronized (lock) {
                if (dispatching || terminated) return;
                event = events.peek();
                if (event == null || (event.counted() && demand == 0)) return;
                events.poll();
                if (event.counted() && demand != Long.MAX_VALUE) demand--;
                if (event.kind == CLOSE || event.kind == ERROR) terminated = true;
                dispatching = true;
            }
            try {
                deliver(event);
            } catch (Throwable thrown) {
                // A listener that throws gets onError and the connection is dropped, as on the JDK.
                abort();
                if (event.kind != ERROR) try { listener.onError(this, thrown); } catch (Throwable ignored) { }
            } finally {
                synchronized (lock) { dispatching = false; }
            }
        }
    }

    private void deliver(Event event) {
        switch (event.kind) {
            case OPEN: listener.onOpen(this); break;
            case TEXT: listener.onText(this, (String) event.data, true); break;
            case BINARY: listener.onBinary(this, (ByteBuffer) event.data, true); break;
            case PING: listener.onPing(this, (ByteBuffer) event.data); break;
            case PONG: listener.onPong(this, (ByteBuffer) event.data); break;
            case CLOSE: {
                inputClosed = true;
                CompletionStage<?> stage = listener.onClose(this, event.code, (String) event.data);
                int echo = event.code == 1000 || (event.code >= 3000 && event.code <= 4999) ? event.code : NORMAL_CLOSURE;
                if (stage == null) closeOutput(echo, "");
                else stage.whenComplete((value, error) -> closeOutput(echo, ""));
                break;
            }
            default: {
                inputClosed = true;
                WebSocketTransport.Connection current = connection;
                if (current != null) current.abort();
                listener.onError(this, (Throwable) event.data);
            }
        }
    }

    // ---- outbound ----

    private CompletableFuture<WebSocket> done() { return CompletableFuture.completedFuture(this); }

    private static CompletableFuture<WebSocket> failure(Throwable error) {
        CompletableFuture<WebSocket> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    private static byte[] drainBytes(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        return bytes;
    }

    private static boolean wellFormed(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        Objects.requireNonNull(data);
        synchronized (sendLock) {
            if (binaryPart != null) return failure(new IllegalStateException("A binary message is in progress"));
            if (outputClosed) return failure(new IOException("WebSocket output closed"));
            if (textPart == null) textPart = new StringBuilder();
            textPart.append(data);
            if (!last) return done();
            String message = textPart.toString();
            textPart = null;
            if (!wellFormed(message)) return failure(new IllegalArgumentException("Malformed UTF-16 text message"));
            try { connection.text(message); } catch (IOException | RuntimeException error) { return failure(error); }
            return done();
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        Objects.requireNonNull(data);
        synchronized (sendLock) {
            if (textPart != null) return failure(new IllegalStateException("A text message is in progress"));
            if (outputClosed) return failure(new IOException("WebSocket output closed"));
            if (binaryPart == null) binaryPart = new ByteArrayOutputStream();
            byte[] bytes = drainBytes(data);
            binaryPart.write(bytes, 0, bytes.length);
            if (!last) return done();
            byte[] message = binaryPart.toByteArray();
            binaryPart = null;
            try { connection.binary(ByteBuffer.wrap(message)); } catch (IOException | RuntimeException error) { return failure(error); }
            return done();
        }
    }

    @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return control(message, true); }
    @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return control(message, false); }

    private CompletableFuture<WebSocket> control(ByteBuffer message, boolean ping) {
        Objects.requireNonNull(message);
        if (message.remaining() > 125) return failure(new IllegalArgumentException("Control payload over 125 bytes"));
        synchronized (sendLock) {
            if (outputClosed) return failure(new IOException("WebSocket output closed"));
            ByteBuffer payload = ByteBuffer.wrap(drainBytes(message));
            try {
                if (!(ping ? connection.ping(payload) : connection.pong(payload)))
                    return failure(new UnsupportedOperationException("java.net.http.WebSocket." + (ping ? "sendPing" : "sendPong")
                        + "(ByteBuffer) is not supported by android-awt: the okhttp3 transport sends its own pings and pongs"));
            } catch (IOException | RuntimeException error) {
                return failure(error);
            }
            return done();
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        Objects.requireNonNull(reason);
        if (statusCode != NORMAL_CLOSURE && (statusCode < 3000 || statusCode > 4999))
            return failure(new IllegalArgumentException("Close status must be 1000 or 3000..4999: " + statusCode));
        if (reason.getBytes(StandardCharsets.UTF_8).length > 123 || !wellFormed(reason))
            return failure(new IllegalArgumentException("Close reason over 123 UTF-8 bytes or malformed"));
        try {
            closeOutput(statusCode, reason);
        } catch (RuntimeException error) {
            return failure(error);
        }
        return done();
    }

    private void closeOutput(int code, String reason) {
        synchronized (sendLock) {
            if (outputClosed) return;
            outputClosed = true;
            try {
                connection.close(code, reason);
            } catch (IOException error) {
                connection.abort();
            }
        }
    }

    @Override
    public void request(long n) {
        if (n <= 0) throw new IllegalArgumentException("Positive demand required: " + n);
        synchronized (lock) { demand = demand > Long.MAX_VALUE - n ? Long.MAX_VALUE : demand + n; }
        drain();
    }

    @Override public String getSubprotocol() { return subprotocol; }
    @Override public boolean isOutputClosed() { return outputClosed; }
    @Override public boolean isInputClosed() { return inputClosed; }

    @Override
    public void abort() {
        inputClosed = true;
        outputClosed = true;
        synchronized (lock) {
            terminated = true;
            events.clear();
        }
        WebSocketTransport.Connection current = connection;
        if (current != null) current.abort();
    }
}
