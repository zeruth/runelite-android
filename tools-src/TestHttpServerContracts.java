// host-contract: patched-args=shadow
// The shadow runs the authenticator before user filters, the JDK after them, so only the shadow run
// can assert that the rejected request never reached the filter ("exact dispatch count").
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestHttpServerContracts {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] bytes = new byte[1024];
        for (int n; (n = input.read(bytes)) >= 0;) out.write(bytes, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    static void rejectFraming(int port, String framing) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("POST /echo HTTP/1.1\r\nHost: localhost\r\n"
                + "Authorization: Basic dXNlcjpwYXNz\r\n" + framing + "\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            String response = read(socket.getInputStream());
            check(response.isEmpty() || response.startsWith("HTTP/1.1 4") || response.startsWith("HTTP/1.1 5"),
                "ambiguous framing must not dispatch: " + response);
        }
    }
    public static void main(String[] args) throws Exception {
        boolean shadow = args.length > 0 && args[0].equals("shadow");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        AtomicInteger calls = new AtomicInteger(), filtered = new AtomicInteger();
        HttpContext context = server.createContext("/echo", exchange -> {
            calls.incrementAndGet();
            check("user".equals(exchange.getPrincipal().getUsername()), "authenticated principal");
            byte[] body = read(exchange.getRequestBody()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        context.setAuthenticator(new BasicAuthenticator("test") {
            public boolean checkCredentials(String user, String password) { return user.equals("user") && password.equals("pass"); }
        });
        context.getFilters().add(new Filter() {
            public String description() { return "header"; }
            public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                filtered.incrementAndGet(); exchange.getResponseHeaders().set("X-Filtered", "yes"); chain.doFilter(exchange);
            }
        });
        server.start();
        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/echo");
            HttpURLConnection rejected = (HttpURLConnection) url.openConnection();
            rejected.setReadTimeout(3000);
            check(rejected.getResponseCode() == 401 && calls.get() == 0, "reject before handler"); rejected.disconnect();
            HttpURLConnection accepted = (HttpURLConnection) url.openConnection();
            accepted.setReadTimeout(3000); accepted.setDoOutput(true); accepted.setChunkedStreamingMode(3);
            accepted.setRequestProperty("Authorization", "Basic dXNlcjpwYXNz");
            try (OutputStream output = accepted.getOutputStream()) { output.write("chunked body".getBytes(StandardCharsets.UTF_8)); }
            check(accepted.getResponseCode() == 200, "success code");
            check("yes".equals(accepted.getHeaderField("X-Filtered")), "filter response header");
            check("chunked body".equals(read(accepted.getInputStream())), "chunked request and response body");
            check((shadow ? filtered.get() == 1 : filtered.get() >= 1) && calls.get() == 1, "exact dispatch count"); accepted.disconnect();
            rejectFraming(server.getAddress().getPort(), "Content-Length: 0\r\nTransfer-Encoding: chunked\r\n");
            rejectFraming(server.getAddress().getPort(), "Content-Length: 0\r\nContent-Length: 0\r\n");
            if (shadow) rejectFraming(server.getAddress().getPort(), "Content-Length: 1048577\r\n"); // the JDK waits for the body instead
            check(calls.get() == 1, "malformed requests never invoke authenticated handler");
            System.out.println("PASS: authentication, principal, filters, chunked request and response");
        } finally { server.stop(0); }
    }
}
