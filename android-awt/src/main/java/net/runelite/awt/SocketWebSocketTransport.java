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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * RFC 6455 client over a plain or TLS socket: the fallback {@link WebSocketTransport} when the
 * APK's okhttp transport is absent. Connects directly; a proxy selector is not consulted.
 * Received fragments are assembled into whole messages, and a Ping is answered at once.
 */
public final class SocketWebSocketTransport implements WebSocketTransport {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Connection open(URI uri, Map<String, List<String>> headers, List<String> subprotocols,
                           long connectTimeoutMillis, SSLSocketFactory ssl, ProxySelector proxy,
                           Events events) throws IOException {
        SocketConnection connection = new SocketConnection(uri, headers, subprotocols, connectTimeoutMillis, ssl, events);
        Thread reader = new Thread(connection::run, "WebSocket-reader");
        reader.setDaemon(true);
        reader.start();
        return connection;
    }

    static final class SocketConnection implements Connection {
        final URI uri; final Map<String, List<String>> headers; final List<String> subprotocols;
        final long timeout; final SSLSocketFactory ssl; final Events events;
        volatile Socket socket; OutputStream output;
        boolean inputClosed, outputClosed, aborted;

        SocketConnection(URI uri, Map<String, List<String>> headers, List<String> subprotocols, long timeout,
                         SSLSocketFactory ssl, Events events) {
            this.uri = uri; this.headers = headers; this.subprotocols = subprotocols; this.timeout = timeout;
            this.ssl = ssl; this.events = events;
        }

        void run() {
            boolean open = false;
            int[] status = {0};
            Map<String, List<String>> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try {
                DataInputStream input = handshake(status, fields);
                open = true;
                events.opened(this, fields.containsKey("Sec-WebSocket-Protocol") ? fields.get("Sec-WebSocket-Protocol").get(0).trim() : null);
                read(input);
            } catch (Throwable error) {
                synchronized (this) {
                    if (aborted) return;
                    aborted = true;
                }
                closeSocket();
                if (open) events.failed(error, 0, null);
                else events.failed(error, status[0], status[0] == 0 ? null : fields);
            }
        }

        private DataInputStream handshake(int[] status, Map<String, List<String>> fields) throws Exception {
            boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            int port = uri.getPort() >= 0 ? uri.getPort() : secure ? 443 : 80;
            Socket plain = new Socket();
            socket = plain;
            plain.connect(new InetSocketAddress(host, port), (int) Math.min(Integer.MAX_VALUE, timeout));
            Socket connected = plain;
            if (secure) {
                SSLSocket tls = (SSLSocket) (ssl == null ? (SSLSocketFactory) SSLSocketFactory.getDefault() : ssl)
                    .createSocket(plain, host, port, true);
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                socket = tls;
                tls.startHandshake();
                connected = tls;
            }
            synchronized (this) {
                if (aborted) throw new IOException("WebSocket aborted");
            }
            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            boolean defaultPort = uri.getPort() < 0 || uri.getPort() == (secure ? 443 : 80);
            String authority = (host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host) + (defaultPort ? "" : ":" + port);
            StringBuilder request = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(authority).append("\r\n")
                .append("Upgrade: websocket\r\nConnection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: ").append(key).append("\r\nSec-WebSocket-Version: 13\r\n");
            if (!subprotocols.isEmpty()) request.append("Sec-WebSocket-Protocol: ").append(String.join(", ", subprotocols)).append("\r\n");
            for (Map.Entry<String, List<String>> header : headers.entrySet())
                for (String value : header.getValue()) request.append(header.getKey()).append(": ").append(value).append("\r\n");
            request.append("\r\n");
            output = connected.getOutputStream();
            output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            DataInputStream input = new DataInputStream(new BufferedInputStream(connected.getInputStream()));
            String line = line(input);
            String[] parts = line.split(" ", 3);
            if (parts.length < 2 || !parts[0].startsWith("HTTP/")) throw new ProtocolException("Malformed status line: " + line);
            int code = Integer.parseInt(parts[1]);
            for (String header = line(input); !header.isEmpty(); header = line(input)) {
                int colon = header.indexOf(':');
                if (colon > 0) fields.computeIfAbsent(header.substring(0, colon).trim(), ignored -> new ArrayList<>()).add(header.substring(colon + 1).trim());
            }
            status[0] = code;
            if (code != 101) throw new ProtocolException("Unexpected HTTP response status code " + code);
            if (!token(fields, "Upgrade", "websocket") || !token(fields, "Connection", "upgrade"))
                throw new ProtocolException("Missing WebSocket upgrade in the handshake response");
            String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1)));
            List<String> accept = fields.get("Sec-WebSocket-Accept");
            if (accept == null || accept.size() != 1 || !expected.equals(accept.get(0).trim()))
                throw new ProtocolException("Bad Sec-WebSocket-Accept in the handshake response");
            return input;
        }

        private static boolean token(Map<String, List<String>> fields, String name, String token) {
            List<String> values = fields.get(name);
            if (values != null) for (String value : values) for (String part : value.split(","))
                if (part.trim().equalsIgnoreCase(token)) return true;
            return false;
        }

        private static String line(InputStream input) throws IOException {
            StringBuilder line = new StringBuilder();
            for (int c = input.read(); ; c = input.read()) {
                if (c < 0) throw new EOFException("Handshake response ended early");
                if (c == '\n') break;
                if (c != '\r') line.append((char) c);
                if (line.length() > 16384) throw new ProtocolException("Handshake header line too long");
            }
            return line.toString();
        }

        private void read(DataInputStream input) throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            int messageType = -1;
            while (true) {
                int first = input.readUnsignedByte(), second = input.readUnsignedByte();
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0F;
                if ((first & 0x70) != 0) throw new ProtocolException("Reserved WebSocket frame bits set");
                if ((second & 0x80) != 0) throw new ProtocolException("Masked frame from the server");
                long length = second & 0x7F;
                if (length == 126) length = input.readUnsignedShort();
                else if (length == 127) length = input.readLong();
                if (length < 0 || length > Integer.MAX_VALUE - 8) throw new ProtocolException("WebSocket frame too large");
                if (opcode >= 8 && (!fin || length > 125)) throw new ProtocolException("Malformed control frame");
                byte[] payload = new byte[(int) length];
                input.readFully(payload);
                switch (opcode) {
                    case 0x0:
                        if (messageType < 0) throw new ProtocolException("Continuation without a message");
                        message.write(payload, 0, payload.length);
                        break;
                    case 0x1: case 0x2:
                        if (messageType >= 0) throw new ProtocolException("New message inside a fragmented one");
                        messageType = opcode;
                        message.write(payload, 0, payload.length);
                        break;
                    case 0x8: {
                        int code = 1005;
                        String reason = "";
                        if (payload.length == 1) throw new ProtocolException("One-byte Close payload");
                        if (payload.length >= 2) {
                            code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                            reason = utf8(payload, 2, payload.length - 2);
                        }
                        boolean release;
                        synchronized (this) {
                            inputClosed = true;
                            release = outputClosed;
                        }
                        if (release) closeSocket();
                        events.closing(code, reason);
                        return;
                    }
                    case 0x9:
                        synchronized (this) {
                            if (!outputClosed) frame(0xA, payload, 0, payload.length);
                        }
                        events.ping(ByteBuffer.wrap(payload));
                        break;
                    case 0xA:
                        events.pong(ByteBuffer.wrap(payload));
                        break;
                    default:
                        throw new ProtocolException("Unknown WebSocket opcode " + opcode);
                }
                if (opcode < 8 && fin) {
                    byte[] whole = message.toByteArray();
                    message.reset();
                    int type = messageType;
                    messageType = -1;
                    if (type == 0x1) events.text(utf8(whole, 0, whole.length));
                    else events.binary(ByteBuffer.wrap(whole));
                }
            }
        }

        private static String utf8(byte[] bytes, int offset, int length) throws IOException {
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, length)).toString();
            } catch (CharacterCodingException e) {
                throw new ProtocolException("Invalid UTF-8 in a text frame");
            }
        }

        private void frame(int opcode, byte[] payload, int offset, int length) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream(length + 14);
            frame.write(0x80 | opcode);
            if (length < 126) frame.write(0x80 | length);
            else if (length <= 0xFFFF) { frame.write(0x80 | 126); frame.write(length >>> 8); frame.write(length); }
            else { frame.write(0x80 | 127); for (int shift = 56; shift >= 0; shift -= 8) frame.write((int) ((long) length >>> shift)); }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            frame.write(mask, 0, 4);
            for (int i = 0; i < length; i++) frame.write(payload[offset + i] ^ mask[i & 3]);
            output.write(frame.toByteArray());
            output.flush();
        }

        private static byte[] bytes(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }

        private synchronized void send(int opcode, byte[] payload) throws IOException {
            if (outputClosed || aborted) throw new IOException("WebSocket output closed");
            frame(opcode, payload, 0, payload.length);
        }

        @Override public void text(String message) throws IOException { send(0x1, message.getBytes(StandardCharsets.UTF_8)); }
        @Override public void binary(ByteBuffer message) throws IOException { send(0x2, bytes(message)); }
        @Override public boolean ping(ByteBuffer payload) throws IOException { send(0x9, bytes(payload)); return true; }
        @Override public boolean pong(ByteBuffer payload) throws IOException { send(0xA, bytes(payload)); return true; }

        @Override
        public void close(int code, String reason) throws IOException {
            byte[] text = reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[text.length + 2];
            payload[0] = (byte) (code >>> 8);
            payload[1] = (byte) code;
            System.arraycopy(text, 0, payload, 2, text.length);
            boolean release;
            synchronized (this) {
                send(0x8, payload);
                outputClosed = true;
                release = inputClosed;
            }
            if (release) closeSocket();
        }

        @Override
        public void abort() {
            synchronized (this) {
                aborted = true;
                outputClosed = true;
                inputClosed = true;
            }
            closeSocket();
        }

        private void closeSocket() {
            Socket current = socket;
            if (current != null) try { current.close(); } catch (IOException ignored) { }
        }
    }
}
