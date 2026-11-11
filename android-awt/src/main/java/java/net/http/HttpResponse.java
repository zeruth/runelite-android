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
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import javax.net.ssl.SSLSession;

public interface HttpResponse<T> {
    int statusCode(); HttpRequest request(); Optional<HttpResponse<T>> previousResponse(); HttpHeaders headers();
    T body(); Optional<SSLSession> sslSession(); URI uri(); HttpClient.Version version();
    interface ResponseInfo { int statusCode(); HttpHeaders headers(); HttpClient.Version version(); }
    interface BodyHandler<T> { BodySubscriber<T> apply(ResponseInfo info); }
    interface BodySubscriber<T> extends Flow.Subscriber<List<ByteBuffer>> { CompletionStage<T> getBody(); }
    interface PushPromiseHandler<T> { void applyPushPromise(HttpRequest request, HttpRequest pushRequest, Function<BodyHandler<T>, CompletableFuture<HttpResponse<T>>> acceptor); }

    final class BodyHandlers {
        private BodyHandlers() {}
        public static BodyHandler<byte[]> ofByteArray() { return info -> BodySubscribers.ofByteArray(); }
        public static BodyHandler<String> ofString() { return info -> BodySubscribers.ofString(charset(info.headers())); }
        public static BodyHandler<String> ofString(Charset charset) { return info -> BodySubscribers.ofString(charset); }
        public static BodyHandler<Void> discarding() { return info -> BodySubscribers.discarding(); }
        public static <U> BodyHandler<U> replacing(U value) { return info -> BodySubscribers.replacing(value); }
        public static BodyHandler<InputStream> ofInputStream() { return info -> BodySubscribers.ofInputStream(); }
        public static BodyHandler<Path> ofFile(Path path, OpenOption... options) { return info -> BodySubscribers.ofFile(path, options); }
        public static BodyHandler<Path> ofFile(Path path) { return ofFile(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
        private static Charset charset(HttpHeaders headers) {
            String type = headers.firstValue("Content-Type").orElse("");
            for (String part : type.split(";")) if (part.trim().toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try { return Charset.forName(part.trim().substring(8).replace("\"", "")); } catch (IllegalArgumentException ignored) {}
            }
            return StandardCharsets.UTF_8;
        }
    }
    final class BodySubscribers {
        private BodySubscribers() {}
        public static BodySubscriber<byte[]> ofByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return output(out, () -> out.toByteArray());
        }
        public static BodySubscriber<String> ofString(Charset charset) { return mapping(ofByteArray(), bytes -> new String(bytes, charset)); }
        public static BodySubscriber<Void> discarding() { return replacing(null); }
        public static <U> BodySubscriber<U> replacing(U value) { return output(new OutputStream() { public void write(int b) {} public void write(byte[] b, int off, int len) {} }, () -> value); }
        public static BodySubscriber<Path> ofFile(Path path, OpenOption... options) {
            try { return output(Files.newOutputStream(path, options), () -> path); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }
        public static BodySubscriber<Path> ofFile(Path path) { return ofFile(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
        public static BodySubscriber<InputStream> ofInputStream() {
            try {
                PipedInputStream input = new PipedInputStream(32768);
                PipedOutputStream output = new PipedOutputStream(input);
                BodySubscriber<InputStream> sink = output(output, () -> input);
                return new BodySubscriber<InputStream>() {
                    private final CompletableFuture<InputStream> body = CompletableFuture.completedFuture(input);
                    public CompletionStage<InputStream> getBody() { return body; }
                    public void onSubscribe(Flow.Subscription subscription) { sink.onSubscribe(subscription); }
                    public void onNext(List<ByteBuffer> buffers) { sink.onNext(buffers); }
                    public void onError(Throwable failure) { sink.onError(failure); }
                    public void onComplete() { sink.onComplete(); }
                };
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
        public static <T,U> BodySubscriber<U> mapping(BodySubscriber<T> source, Function<? super T,? extends U> mapper) {
            return new BodySubscriber<U>() {
                public CompletionStage<U> getBody() { return source.getBody().thenApply(mapper); }
                public void onSubscribe(Flow.Subscription subscription) { source.onSubscribe(subscription); }
                public void onNext(List<ByteBuffer> values) { source.onNext(values); }
                public void onError(Throwable failure) { source.onError(failure); }
                public void onComplete() { source.onComplete(); }
            };
        }
        private static <T> BodySubscriber<T> output(OutputStream output, java.util.function.Supplier<T> result) {
            return new BodySubscriber<T>() {
                final CompletableFuture<T> body = new CompletableFuture<>(); Flow.Subscription subscription;
                public CompletionStage<T> getBody() { return body; }
                public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(Long.MAX_VALUE); }
                public void onNext(List<ByteBuffer> buffers) {
                    try {
                        for (ByteBuffer buffer : buffers) {
                            byte[] bytes=new byte[Math.min(8192,Math.max(1,buffer.remaining()))];
                            while(buffer.hasRemaining()) { int n=Math.min(bytes.length,buffer.remaining()); buffer.get(bytes,0,n); output.write(bytes,0,n); }
                        }
                    } catch (IOException error) { subscription.cancel(); onError(error); }
                }
                public void onError(Throwable failure) { try { output.close(); } catch(IOException ignored) {} body.completeExceptionally(failure); }
                public void onComplete() { try { output.close(); body.complete(result.get()); } catch(Throwable error) { body.completeExceptionally(error); } }
            };
        }
    }
}
