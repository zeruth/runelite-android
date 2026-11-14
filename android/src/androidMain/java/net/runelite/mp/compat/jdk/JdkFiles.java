package net.runelite.mp.compat.jdk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * JDK 11 {@code Files.readString}/{@code writeString}, which Android only has from API 36.1 and
 * desugar_jdk_libs does not supply. Like the JDK, malformed input and unmappable characters
 * are errors ({@link java.nio.charset.CharacterCodingException}, an {@link IOException}), never
 * silently replaced.
 */
public final class JdkFiles {
    private JdkFiles() {
    }

    public static String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset cs) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(cs);
        byte[] bytes = Files.readAllBytes(path);
        return cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    public static Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException {
        return writeString(path, csq, StandardCharsets.UTF_8, options);
    }

    public static Path writeString(Path path, CharSequence csq, Charset cs, OpenOption... options)
            throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(csq);
        Objects.requireNonNull(cs);
        ByteBuffer encoded = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(csq));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        Files.write(path, bytes, options);
        return path;
    }
}
