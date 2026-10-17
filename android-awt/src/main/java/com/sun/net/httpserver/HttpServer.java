package com.sun.net.httpserver;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;

/** Loopback-only HTTP callback server for desktop browser sign-in on Android.
 * Supports bounded GET requests and buffered responses; not a general web server. */
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
        private volatile boolean running;
        private boolean started;
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
                    Runnable task = () -> serve(socket);
                    try { if (executor == null) task.run(); else executor.execute(task); }
                    catch (RuntimeException e) { socket.close(); }
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
                    if (!"GET".equals(request[0])) exchange.sendResponseHeaders(405, -1);
                    else if (context == null || context.handler == null) exchange.sendResponseHeaders(404, -1);
                    else context.handler.handle(exchange);
                } catch (Exception e) {
                    if (exchange.code == -1) exchange.sendResponseHeaders(500, -1);
                } finally { exchange.close(); }
            } catch (IOException | IllegalArgumentException ignored) { /* Disconnect/malformed callback; no secrets logged. */ }
        }
        public synchronized void stop(int delay) {
            if (delay < 0) throw new IllegalArgumentException("Negative delay");
            running = false;
            if (listener != null) try { listener.close(); } catch (IOException ignored) {}
        }
    }
    private static final class LocalContext extends HttpContext {
        final LocalServer server;
        final String path;
        volatile HttpHandler handler;
        final Map<String, Object> attributes = new HashMap<>();
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
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
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
        public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }
        public OutputStream getResponseBody() { return body; }
        public void sendResponseHeaders(int code, long length) throws IOException {
            if (this.code != -1) throw new IOException("Headers already sent");
            this.code = code;
        }
        public void close() {
            if (closed) return;
            closed = true;
            try {
                StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(code == -1 ? 500 : code).append(" Response\r\n");
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    String key = entry.getKey();
                    if (key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Connection") || key.equalsIgnoreCase("Transfer-Encoding")) continue;
                    if (!key.matches("[A-Za-z0-9-]+")) throw new IOException("Invalid header");
                    for (String value : entry.getValue()) {
                        if (value.contains("\r") || value.contains("\n")) throw new IOException("Invalid header");
                        headers.append(key).append(": ").append(value).append("\r\n");
                    }
                }
                headers.append("Content-Length: ").append(body.size()).append("\r\nConnection: close\r\n\r\n");
                OutputStream output = socket.getOutputStream();
                output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
                body.writeTo(output);
                output.flush();
            } catch (IOException ignored) { }
            finally { try { socket.close(); } catch (IOException ignored) {} }
        }
        public InetSocketAddress getRemoteAddress() { return (InetSocketAddress) socket.getRemoteSocketAddress(); }
        public InetSocketAddress getLocalAddress() { return (InetSocketAddress) socket.getLocalSocketAddress(); }
        public int getResponseCode() { return code; }
        public String getProtocol() { return protocol; }
        public Object getAttribute(String name) { return attributes.containsKey(name) ? attributes.get(name) : context.getAttributes().get(name); }
        public void setAttribute(String name, Object value) { attributes.put(name, value); }
        public void setStreams(InputStream input, OutputStream output) { throw new UnsupportedOperationException("Stream replacement"); }
    }
}
