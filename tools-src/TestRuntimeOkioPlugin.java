import java.nio.file.Files;
import java.util.Iterator;
import kotlin.jvm.functions.Function0;
import okio.AsyncTimeout;
import okio.FileSystem;
import okio.Path;

/** Compile against the unshrunk libraries, then D8 and run with the release APK first. */
public final class TestRuntimeOkioPlugin {
    public static void main(String[] args) {
        try {
            check();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void check() throws Exception {
        java.nio.file.Path directory = Files.createTempDirectory("okio-plugin-");
        java.nio.file.Path child = directory.resolve("item.txt");
        try {
            Files.write(child, new byte[]{1});
            Iterator<Path> items = FileSystem.SYSTEM.listRecursively(Path.get(directory.toString())).iterator();
            if (!items.hasNext() || !items.next().name().equals("item.txt") || items.hasNext()) {
                throw new AssertionError("Okio Sequence return type or iteration");
            }
            Function0<String> callback = new Function0<String>() {
                @Override public String invoke() { return "callback"; }
            };
            Object value = AsyncTimeout.class.getMethod("withTimeout", Function0.class)
                .invoke(new AsyncTimeout(), callback);
            if (!"callback".equals(value)) {
                throw new AssertionError("Okio Kotlin callback descriptor");
            }
            System.out.println("PASS: release Okio Kotlin return types and callback descriptors");
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        }
    }
}
