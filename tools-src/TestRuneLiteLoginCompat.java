import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class TestRuneLiteLoginCompat {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 1);
        int port = server.getAddress().getPort();
        check(port > 0, "Assigned callback port");
        AtomicInteger callbacks = new AtomicInteger();
        server.createContext("/oauth", req -> {
            check("username=Test%20User&sessionId=test-session".equals(req.getRequestURI().getRawQuery()), "Query preserved");
            callbacks.incrementAndGet();
            req.getResponseHeaders().set("Location", "https://runelite.net/logged-in");
            req.sendResponseHeaders(302, 0);
            req.close();
        });
        server.createContext("/error", req -> {
            req.sendResponseHeaders(400, 0);
            req.getResponseBody().write("Invalid callback".getBytes(StandardCharsets.UTF_8));
            req.close();
        });
        server.start();
        try {
            HttpURLConnection connection = connect(port, "/oauth?username=Test%20User&sessionId=test-session");
            check(connection.getResponseCode() == 302, "OAuth redirect");
            check("https://runelite.net/logged-in".equals(connection.getHeaderField("Location")), "Redirect preserved");
            connection.disconnect();
            check(callbacks.get() == 1, "Callback delivered exactly once");
            connection = connect(port, "/missing");
            check(connection.getResponseCode() == 404, "Unknown path"); connection.disconnect();
            connection = connect(port, "/oauth"); connection.setRequestMethod("POST");
            check(connection.getResponseCode() == 405, "Unsupported method rejected"); connection.disconnect();
            connection = connect(port, "/error");
            check(connection.getResponseCode() == 400, "Error status");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getErrorStream()) { int b; while ((b = in.read()) != -1) out.write(b); }
            check("Invalid callback".equals(out.toString("UTF-8")), "Error response body"); connection.disconnect();
        } finally { server.stop(0); }
        try (Socket ignored = new Socket("localhost", port)) { throw new AssertionError("Listener survived stop"); }
        catch (IOException expected) { }
        try { HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 1); throw new AssertionError("Nonloopback allowed"); }
        catch (IOException expected) { }
        System.out.println("PASS: loopback callback, real ephemeral port, redirect, query, errors, method restriction, stop and bind restriction");
        Class<?> desktop = Class.forName("java.awt.Desktop");
        Class<?> handler = Class.forName("java.awt.Desktop$BrowseHandler");
        AtomicInteger browsed = new AtomicInteger();
        Object bridge = java.lang.reflect.Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (proxy, method, values) -> {
            check("https://runelite.net/".equals(values[0].toString()), "Browser URL preserved");
            browsed.incrementAndGet(); return null;
        });
        desktop.getMethod("setBrowseHandler", handler).invoke(null, bridge);
        Object instance = desktop.getMethod("getDesktop").invoke(null);
        check((Boolean) desktop.getMethod("isDesktopSupported").invoke(null), "Browser available");
        desktop.getMethod("browse", URI.class).invoke(instance, URI.create("https://runelite.net/"));
        check(browsed.get() == 1, "Browser dispatched");
        try { desktop.getMethod("browse", URI.class).invoke(instance, URI.create("file:///private")); throw new AssertionError("File URI accepted"); }
        catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "Unsupported scheme rejected"); }
        desktop.getMethod("setBrowseHandler", handler).invoke(null, new Object[]{null});
        check(!(Boolean) desktop.getMethod("isDesktopSupported").invoke(null), "Uninstalled bridge unavailable");
        System.out.println("PASS: browser bridge dispatch, scheme restriction and availability");
        if (args.length > 0) { testToolbar(); System.exit(0); }
    }
    private static final class ToolbarFixture {
        final java.util.Map<Object, Object> entries = new java.util.LinkedHashMap<>();
    }
    private static final class UiFixture {
        final java.util.List<Object> sidebarEntries = new java.util.ArrayList<>();
        final ToolbarFixture toolbarPanel = new ToolbarFixture();
    }
    private static void testToolbar() throws Exception {
        Class<?> navigation = Class.forName("net.runelite.client.ui.NavigationButton");
        Object builder = navigation.getMethod("builder").invoke(null);
        Class<?> builderType = builder.getClass();
        builderType.getMethod("tooltip", String.class).invoke(builder, "Sign in to RuneLite");
        AtomicInteger clicks = new AtomicInteger();
        builderType.getMethod("onClick", Runnable.class).invoke(builder, (Runnable) clicks::incrementAndGet);
        Object login = builderType.getMethod("build").invoke(builder);
        UiFixture ui = new UiFixture();
        ui.toolbarPanel.entries.put(login, new Object());
        Class<?> bridgeType = Class.forName("net.runelite.mp.ui.bridge.NavBarBridge");
        Object bridge = bridgeType.getField("INSTANCE").get(null);
        java.lang.reflect.Method buttons = bridgeType.getDeclaredMethod("buttons", Object.class);
        buttons.setAccessible(true);
        check(((java.util.List<?>) buttons.invoke(bridge, ui)).contains(login), "Action-only login button included");
        java.lang.reflect.Method invoke = bridgeType.getDeclaredMethod("invoke", Object.class, navigation);
        invoke.setAccessible(true);
        invoke.invoke(bridge, ui, login);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        check(clicks.get() == 1, "Original login callback invoked");
        ui.toolbarPanel.entries.clear();
        invoke.invoke(bridge, ui, login);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        check(clicks.get() == 1, "Stale login button not invoked");
        check(((java.util.List<?>) buttons.invoke(bridge, ui)).isEmpty(), "Removed login button absent");
        System.out.println("PASS: action-only toolbar discovery, callback and stale-button removal");
    }
    private static HttpURLConnection connect(int port, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
        return connection;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
