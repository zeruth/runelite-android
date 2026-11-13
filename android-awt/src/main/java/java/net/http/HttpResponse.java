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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;

public interface HttpResponse<T> {
    int statusCode(); HttpRequest request(); Optional<HttpResponse<T>> previousResponse(); HttpHeaders headers();
    T body(); Optional<SSLSession> sslSession(); URI uri(); HttpClient.Version version();
    interface ResponseInfo { int statusCode(); HttpHeaders headers(); HttpClient.Version version(); }
    @FunctionalInterface interface BodyHandler<T> { BodySubscriber<T> apply(ResponseInfo responseInfo); }
    interface BodySubscriber<T> extends Flow.Subscriber<List<ByteBuffer>> { CompletionStage<T> getBody(); }
    interface PushPromiseHandler<T> {
        void applyPushPromise(HttpRequest initiatingRequest, HttpRequest pushPromiseRequest, Function<BodyHandler<T>, CompletableFuture<HttpResponse<T>>> acceptor);
        static <T> PushPromiseHandler<T> of(Function<HttpRequest, BodyHandler<T>> pushPromiseHandler, ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<T>>> pushPromisesMap) {
            Objects.requireNonNull(pushPromiseHandler); Objects.requireNonNull(pushPromisesMap);
            return (initiating, push, acceptor) -> pushPromisesMap.put(push, acceptor.apply(pushPromiseHandler.apply(push)));
        }
    }

    final class BodyHandlers {
        private BodyHandlers() {}
        public static BodyHandler<Void> fromSubscriber(Flow.Subscriber<? super List<ByteBuffer>> subscriber) { Objects.requireNonNull(subscriber); return info -> BodySubscribers.fromSubscriber(subscriber); }
        public static <S extends Flow.Subscriber<? super List<ByteBuffer>>, T> BodyHandler<T> fromSubscriber(S subscriber, Function<? super S, ? extends T> finisher) {
            Objects.requireNonNull(subscriber); Objects.requireNonNull(finisher); return info -> BodySubscribers.fromSubscriber(subscriber, finisher);
        }
        public static BodyHandler<Void> fromLineSubscriber(Flow.Subscriber<? super String> subscriber) {
            Objects.requireNonNull(subscriber); return info -> BodySubscribers.fromLineSubscriber(subscriber, s -> null, charset(info.headers()), null);
        }
        public static <S extends Flow.Subscriber<? super String>, T> BodyHandler<T> fromLineSubscriber(S subscriber, Function<? super S, ? extends T> finisher, String lineSeparator) {
            Objects.requireNonNull(subscriber); Objects.requireNonNull(finisher);
            if (lineSeparator != null && lineSeparator.isEmpty()) throw new IllegalArgumentException("Empty line separator");
            return info -> BodySubscribers.fromLineSubscriber(subscriber, finisher, charset(info.headers()), lineSeparator);
        }
        public static BodyHandler<Void> discarding() { return info -> BodySubscribers.discarding(); }
        public static <U> BodyHandler<U> replacing(U value) { return info -> BodySubscribers.replacing(value); }
        public static BodyHandler<String> ofString() { return info -> BodySubscribers.ofString(charset(info.headers())); }
        public static BodyHandler<String> ofString(Charset charset) { Objects.requireNonNull(charset); return info -> BodySubscribers.ofString(charset); }
        public static BodyHandler<Path> ofFile(Path file, OpenOption... openOptions) {
            Objects.requireNonNull(file); OpenOption[] options = checkWriteOptions(openOptions);
            return info -> BodySubscribers.ofFile(file, options);
        }
        public static BodyHandler<Path> ofFile(Path file) { return ofFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
        public static BodyHandler<Path> ofFileDownload(Path directory, OpenOption... openOptions) {
            Objects.requireNonNull(directory); OpenOption[] options = checkWriteOptions(openOptions);
            ResponseSubscribers.checkDownloadDirectory(directory);
            return info -> BodySubscribers.ofFile(ResponseSubscribers.download(directory, info.headers()), options);
        }
        public static BodyHandler<InputStream> ofInputStream() { return info -> BodySubscribers.ofInputStream(); }
        public static BodyHandler<Stream<String>> ofLines() { return info -> BodySubscribers.ofLines(charset(info.headers())); }
        public static BodyHandler<Void> ofByteArrayConsumer(Consumer<Optional<byte[]>> consumer) { Objects.requireNonNull(consumer); return info -> BodySubscribers.ofByteArrayConsumer(consumer); }
        public static BodyHandler<byte[]> ofByteArray() { return info -> BodySubscribers.ofByteArray(); }
        public static BodyHandler<Flow.Publisher<List<ByteBuffer>>> ofPublisher() { return info -> BodySubscribers.ofPublisher(); }
        public static <T> BodyHandler<T> buffering(BodyHandler<T> downstreamHandler, int bufferSize) {
            Objects.requireNonNull(downstreamHandler);
            if (bufferSize <= 0) throw new IllegalArgumentException("Positive buffer size required: " + bufferSize);
            return info -> BodySubscribers.buffering(downstreamHandler.apply(info), bufferSize);
        }
        private static OpenOption[] checkWriteOptions(OpenOption[] options) {
            for (OpenOption option : options) {
                if (option == StandardOpenOption.READ || option == StandardOpenOption.DELETE_ON_CLOSE) throw new IllegalArgumentException("Invalid open option: " + option);
            }
            return options.clone();
        }
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
        public static BodySubscriber<Void> fromSubscriber(Flow.Subscriber<? super List<ByteBuffer>> subscriber) { return fromSubscriber(subscriber, s -> null); }
        public static <S extends Flow.Subscriber<? super List<ByteBuffer>>, T> BodySubscriber<T> fromSubscriber(S subscriber, Function<? super S, ? extends T> finisher) {
            CompletableFuture<T> body = new CompletableFuture<>();
            return ResponseSubscribers.body(ResponseSubscribers.<List<ByteBuffer>, S, T>finishing(subscriber, finisher, body), body);
        }
        public static BodySubscriber<Void> fromLineSubscriber(Flow.Subscriber<? super String> subscriber) { return fromLineSubscriber(subscriber, s -> null, StandardCharsets.UTF_8, null); }
        public static <S extends Flow.Subscriber<? super String>, T> BodySubscriber<T> fromLineSubscriber(S subscriber, Function<? super S, ? extends T> finisher, Charset charset, String lineSeparator) {
            Objects.requireNonNull(charset);
            if (lineSeparator != null && lineSeparator.isEmpty()) throw new IllegalArgumentException("Empty line separator");
            CompletableFuture<T> body = new CompletableFuture<>();
            return ResponseSubscribers.body(new ResponseSubscribers.Lines(ResponseSubscribers.<String, S, T>finishing(subscriber, finisher, body), charset, lineSeparator), body);
        }
        public static BodySubscriber<byte[]> ofByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return output(out, () -> out.toByteArray());
        }
        public static BodySubscriber<String> ofString(Charset charset) { Objects.requireNonNull(charset); return mapping(ofByteArray(), bytes -> new String(bytes, charset)); }
        public static BodySubscriber<Void> discarding() { return replacing(null); }
        public static <U> BodySubscriber<U> replacing(U value) { return output(new OutputStream() { public void write(int b) {} public void write(byte[] b, int off, int len) {} }, () -> value); }
        public static BodySubscriber<Path> ofFile(Path file, OpenOption... openOptions) {
            try { return output(Files.newOutputStream(file, openOptions), () -> file); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }
        public static BodySubscriber<Path> ofFile(Path file) { return ofFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE); }
        public static BodySubscriber<Void> ofByteArrayConsumer(Consumer<Optional<byte[]>> consumer) { return ResponseSubscribers.byteArrayConsumer(consumer); }
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
        public static BodySubscriber<Stream<String>> ofLines(Charset charset) {
            Objects.requireNonNull(charset);
            return mapping(ofInputStream(), input -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset));
                return reader.lines().onClose(() -> { try { reader.close(); } catch (IOException error) { throw new UncheckedIOException(error); } });
            });
        }
        public static BodySubscriber<Flow.Publisher<List<ByteBuffer>>> ofPublisher() { return new ResponseSubscribers.Publishing(); }
        public static <T> BodySubscriber<T> buffering(BodySubscriber<T> downstream, int bufferSize) {
            Objects.requireNonNull(downstream);
            if (bufferSize <= 0) throw new IllegalArgumentException("Positive buffer size required: " + bufferSize);
            return new ResponseSubscribers.Buffering<T>(downstream, bufferSize);
        }
        public static <T,U> BodySubscriber<U> mapping(BodySubscriber<T> upstream, Function<? super T,? extends U> mapper) {
            Objects.requireNonNull(upstream); Objects.requireNonNull(mapper);
            return new BodySubscriber<U>() {
                public CompletionStage<U> getBody() { return upstream.getBody().thenApply(mapper); }
                public void onSubscribe(Flow.Subscription subscription) { upstream.onSubscribe(subscription); }
                public void onNext(List<ByteBuffer> values) { upstream.onNext(values); }
                public void onError(Throwable failure) { upstream.onError(failure); }
                public void onComplete() { upstream.onComplete(); }
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
