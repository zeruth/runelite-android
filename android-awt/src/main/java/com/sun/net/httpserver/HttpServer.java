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
package com.sun.net.httpserver;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;

/** Loopback-only HTTP server with bounded requests for desktop plugin callbacks. */
public abstract class HttpServer {
    protected HttpServer() {}
    public static HttpServer create() throws IOException { return create(null, 0); }
    public static HttpServer create(InetSocketAddress address, int backlog) throws IOException {
        LocalServer server = new LocalServer();
        if (address != null) server.bind(address, backlog);
        return server;
    }
    public abstract void bind(InetSocketAddress address, int backlog) throws IOException;
    public abstract InetSocketAddress getAddress();
    public abstract HttpContext createContext(String path, HttpHandler handler);
    public abstract HttpContext createContext(String path);
    public abstract void removeContext(String path);
    public abstract void removeContext(HttpContext context);
    public abstract void setExecutor(Executor executor);
    public abstract Executor getExecutor();
    public abstract void start();
    public abstract void stop(int delay);

    private static final class LocalServer extends HttpServer {
        private final Map<String, LocalContext> contexts = new HashMap<>();
        private ServerSocket listener;
        private Executor executor;
        private final java.util.concurrent.ThreadPoolExecutor workers = new java.util.concurrent.ThreadPoolExecutor(
            4, 4, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(16), task -> {
                Thread thread = new Thread(task, "RuneLite-loopback-request"); thread.setDaemon(true); return thread;
            });
        private volatile boolean running;
        private boolean started;
        private final Set<Socket> active = new HashSet<>();
        public synchronized void bind(InetSocketAddress address, int backlog) throws IOException {
            if (listener != null) throw new IOException("Already bound");
            if (address == null || address.getAddress() == null || !address.getAddress().isLoopbackAddress())
                throw new IOException("Only loopback callbacks are supported");
            ServerSocket socket = new ServerSocket();
            try { socket.bind(address, Math.max(1, backlog)); listener = socket; }
            catch (IOException e) { socket.close(); throw e; }
        }
        public synchronized InetSocketAddress getAddress() {
            return listener == null ? null : (InetSocketAddress) listener.getLocalSocketAddress();
        }
        public synchronized HttpContext createContext(String path, HttpHandler handler) {
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("Invalid context path");
            if (contexts.containsKey(path)) throw new IllegalArgumentException("Duplicate context");
            LocalContext context = new LocalContext(this, path, handler);
            contexts.put(path, context);
            return context;
        }
        public HttpContext createContext(String path) { return createContext(path, null); }
        public synchronized void removeContext(String path) {
            if (contexts.remove(path) == null) throw new IllegalArgumentException("Unknown context");
        }
        public synchronized void removeContext(HttpContext context) {
            if (contexts.get(context.getPath()) != context) throw new IllegalArgumentException("Unknown context");
            contexts.remove(context.getPath());
        }
        public synchronized void setExecutor(Executor executor) {
            if (started) throw new IllegalStateException("Already started");
            this.executor = executor;
        }
        public Executor getExecutor() { return executor; }
        public synchronized void start() {
            if (listener == null || listener.isClosed() || started) throw new IllegalStateException("Not bound or already started");
            running = started = true;
            Thread thread = new Thread(this::accept, "RuneLite-loopback-login");
            thread.setDaemon(true);
            thread.start();
        }
        private void accept() {
            while (running) {
                try {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(5000);
                    synchronized (this) { if (!running) { socket.close(); continue; } active.add(socket); }
                    Runnable task = () -> { try { serve(socket); } finally { synchronized (this) { active.remove(socket); notifyAll(); } } };
                    try {
                        if (executor == null) workers.execute(task);
                        else executor.execute(task);
                    } catch (RuntimeException e) { synchronized (this) { active.remove(socket); notifyAll(); } socket.close(); }
                } catch (IOException e) { if (listener.isClosed()) break; }
            }
        }
        private synchronized LocalContext match(String path) {
            LocalContext best = null;
            for (LocalContext context : contexts.values())
                if (path.startsWith(context.path) && (best == null || context.path.length() > best.path.length())) best = context;
            return best;
        }
        private void serve(Socket socket) {
            try (Socket connection = socket) {
                InputStream input = connection.getInputStream();
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                int tail = 0;
                while (header.size() < 16384) {
                    int value = input.read();
                    if (value < 0) return;
                    header.write(value);
                    tail = (tail << 8) | value;
                    if (tail == 0x0d0a0d0a) break;
                }
                if (tail != 0x0d0a0d0a) return;
                String[] lines = new String(header.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
                String[] request = lines[0].split(" ");
                if (request.length != 3 || !request[1].startsWith("/")) return;
                URI uri = URI.create(request[1]);
                LocalContext context = match(uri.getPath());
                Exchange exchange = new Exchange(connection, uri, request[0], request[2], context);
                for (int i = 1; i < lines.length; i++) {
                    int colon = lines[i].indexOf(':');
                    if (colon <= 0) return;
                    exchange.requestHeaders.add(lines[i].substring(0, colon), lines[i].substring(colon + 1).trim());
                }
                try {
                    exchange.readRequestBody(input);
                    if (context == null || context.handler == null) exchange.sendResponseHeaders(404, -1);
                    else {
                        Authenticator auth = context.getAuthenticator();
                        Authenticator.Result result = auth == null ? null : auth.authenticate(exchange);
                        if (result instanceof Authenticator.Failure) exchange.sendResponseHeaders(((Authenticator.Failure) result).getResponseCode(), -1);
                        else if (result instanceof Authenticator.Retry) exchange.sendResponseHeaders(((Authenticator.Retry) result).getResponseCode(), -1);
                        else if (auth != null && !(result instanceof Authenticator.Success)) exchange.sendResponseHeaders(500, -1);
                        else {
                            if (result instanceof Authenticator.Success) exchange.principal = ((Authenticator.Success) result).getPrincipal();
                            new Filter.Chain(context.getFilters(), context.handler).doFilter(exchange);
                        }
                    }
                } catch (Exception e) {
                    if (exchange.code == -1) exchange.sendResponseHeaders(500, -1);
                } finally { exchange.close(); }
            } catch (IOException | IllegalArgumentException ignored) { /* Disconnect/malformed callback; no secrets logged. */ }
        }
        public synchronized void stop(int delay) {
            if (delay < 0) throw new IllegalArgumentException("Negative delay");
            running = false;
            if (listener != null) try { listener.close(); } catch (IOException ignored) {}
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(delay);
            while (!active.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            for (Socket socket : active) try { socket.close(); } catch (IOException ignored) {}
            workers.shutdownNow();
            active.clear();
        }
    }
    private static final class LocalContext extends HttpContext {
        final LocalServer server;
        final String path;
        volatile HttpHandler handler;
        final Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();
        LocalContext(LocalServer server, String path, HttpHandler handler) { this.server = server; this.path = path; this.handler = handler; }
        public HttpHandler getHandler() { return handler; }
        public void setHandler(HttpHandler handler) {
            if (handler == null || this.handler != null) throw new IllegalArgumentException("Handler already set or null");
            this.handler = handler;
        }
        public String getPath() { return path; }
        public HttpServer getServer() { return server; }
        public Map<String, Object> getAttributes() { return attributes; }
    }
    private static final class Exchange extends HttpExchange {
        final Socket socket;
        final URI uri;
        final String method, protocol;
        final LocalContext context;
        final Headers requestHeaders = new Headers(), responseHeaders = new Headers();
        InputStream requestBody = new ByteArrayInputStream(new byte[0]);
        OutputStream responseBody;
        long remaining;
        boolean chunked;
        HttpPrincipal principal;
        final Map<String, Object> attributes = new HashMap<>();
        int code = -1;
        boolean closed;
        Exchange(Socket socket, URI uri, String method, String protocol, LocalContext context) {
            this.socket = socket; this.uri = uri; this.method = method; this.protocol = protocol; this.context = context;
        }
        public Headers getRequestHeaders() { return requestHeaders; }
        public Headers getResponseHeaders() { return responseHeaders; }
        public URI getRequestURI() { return uri; }
        public String getRequestMethod() { return method; }
        public HttpContext getHttpContext() { return context; }
        public InputStream getRequestBody() { return requestBody; }
        public OutputStream getResponseBody() {
            if (responseBody == null) responseBody = new OutputStream() {
                public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
                public void write(byte[] bytes, int off, int len) throws IOException {
                    if (code == -1 || closed) throw new IOException("Response headers not sent or exchange closed");
                    if (len == 0) return;
                    if (!chunked && len > remaining) throw new IOException("Response exceeds declared length");
                    OutputStream out = socket.getOutputStream();
                    if (chunked) out.write((Integer.toHexString(len) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes, off, len);
                    if (chunked) out.write(new byte[]{13, 10}); else remaining -= len;
                }
                public void flush() throws IOException { socket.getOutputStream().flush(); }
                public void close() { Exchange.this.close(); }
            };
            return responseBody;
        }
        private static String line(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (out.size() <= 16384) { int c = in.read(); if (c < 0) throw new EOFException(); if (c == 10) return new String(out.toByteArray(), StandardCharsets.US_ASCII).trim(); out.write(c); }
            throw new IOException("Request line too long");
        }
        void readRequestBody(InputStream input) throws IOException {
            final int max = 1024 * 1024;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String encoding = requestHeaders.getFirst("Transfer-Encoding"), length = requestHeaders.getFirst("Content-Length");
            if ((encoding != null && requestHeaders.get("Transfer-Encoding").size() != 1)
                || (length != null && requestHeaders.get("Content-Length").size() != 1)) throw new IOException("Duplicate request framing");
            if (encoding != null && (length != null || !"chunked".equalsIgnoreCase(encoding))) throw new IOException("Ambiguous request framing");
            if (encoding != null) {
                for (;;) {
                    String size = line(input).split(";", 2)[0];
                    int n; try { n = Integer.parseInt(size, 16); } catch (NumberFormatException e) { throw new IOException("Invalid chunk", e); }
                    if (n < 0 || n > max - body.size()) throw new IOException("Request body too large");
                    if (n == 0) {
                        int trailerBytes = 0; String trailer;
                        while (!(trailer = line(input)).isEmpty()) {
                            trailerBytes += trailer.length() + 2;
                            if (trailerBytes > 16384) throw new IOException("Request trailers too large");
                        }
                        break;
                    }
                    copy(input, body, n); if (!line(input).isEmpty()) throw new IOException("Invalid chunk ending");
                }
            } else if (length != null) {
                int n; try { n = Integer.parseInt(length); } catch (NumberFormatException e) { throw new IOException("Invalid length", e); }
                if (n < 0 || n > max) throw new IOException("Request body too large");
                copy(input, body, n);
            }
            requestBody = new ByteArrayInputStream(body.toByteArray());
        }
        private static void copy(InputStream in, OutputStream out, int n) throws IOException {
            byte[] bytes = new byte[Math.min(8192, Math.max(1,n))];
            while (n > 0) { int count = in.read(bytes, 0, Math.min(n, bytes.length)); if (count < 0) throw new EOFException(); out.write(bytes, 0, count); n -= count; }
        }
        public void sendResponseHeaders(int code, long length) throws IOException {
            if (this.code != -1) throw new IOException("Headers already sent");
            if (code < 100 || length < -1) throw new IllegalArgumentException("Invalid response");
            boolean noBody = "HEAD".equals(method) || code == 204 || code == 304 || code < 200;
            chunked = length == 0 && !noBody;
            remaining = length < 0 || noBody ? 0 : length;
            StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(code).append(" Response\r\n");
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Connection") || key.equalsIgnoreCase("Transfer-Encoding")) continue;
                if (!key.matches("[A-Za-z0-9-]+")) throw new IOException("Invalid header");
                for (String value : entry.getValue()) {
                    if (value.contains("\r") || value.contains("\n")) throw new IOException("Invalid header");
                    headers.append(key).append(": ").append(value).append("\r\n");
                }
            }
            if (chunked) headers.append("Transfer-Encoding: chunked\r\n");
            else if (!noBody) headers.append("Content-Length: ").append(remaining).append("\r\n");
            headers.append("Connection: close\r\n\r\n");
            socket.getOutputStream().write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            this.code = code;
        }
        public void close() {
            if (closed) return;
            try {
                if (code == -1) sendResponseHeaders(500, -1);
                if (chunked) socket.getOutputStream().write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            } catch (IOException ignored) { }
            finally { closed = true; try { socket.close(); } catch (IOException ignored) {} }
        }
        public InetSocketAddress getRemoteAddress() { return (InetSocketAddress) socket.getRemoteSocketAddress(); }
        public InetSocketAddress getLocalAddress() { return (InetSocketAddress) socket.getLocalSocketAddress(); }
        public int getResponseCode() { return code; }
        public String getProtocol() { return protocol; }
        public Object getAttribute(String name) { return attributes.containsKey(name) ? attributes.get(name) : context == null ? null : context.getAttributes().get(name); }
        public void setAttribute(String name, Object value) { attributes.put(name, value); }
        public void setStreams(InputStream input, OutputStream output) { if (input != null) requestBody = input; if (output != null) responseBody = output; }
        public HttpPrincipal getPrincipal() { return principal; }
    }
}
