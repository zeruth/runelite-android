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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public abstract class HttpRequest {
    protected HttpRequest() {}
    public abstract Optional<BodyPublisher> bodyPublisher();
    public abstract String method();
    public abstract Optional<Duration> timeout();
    public abstract boolean expectContinue();
    public abstract URI uri();
    public abstract Optional<HttpClient.Version> version();
    public abstract HttpHeaders headers();
    public static Builder newBuilder() { return new RequestBuilder(); }
    public static Builder newBuilder(URI uri) { return newBuilder().uri(uri); }
    // JDK 16
    public static Builder newBuilder(HttpRequest request, BiPredicate<String, String> filter) {
        Objects.requireNonNull(request); Objects.requireNonNull(filter);
        Builder builder = newBuilder(request.uri()).expectContinue(request.expectContinue());
        request.headers().map().forEach((name, values) -> { for (String value : values) if (filter.test(name, value)) builder.header(name, value); });
        request.version().ifPresent(builder::version);
        request.timeout().ifPresent(builder::timeout);
        String method = request.method();
        Optional<BodyPublisher> body = request.bodyPublisher();
        if (method.equals("GET") && !body.isPresent()) builder.GET();
        else if (method.equals("DELETE") && !body.isPresent()) builder.DELETE();
        else builder.method(method, body.orElse(BodyPublishers.noBody()));
        return builder;
    }
    public interface Builder {
        Builder uri(URI uri); Builder expectContinue(boolean enabled); Builder version(HttpClient.Version version);
        Builder header(String name, String value); Builder headers(String... headers); Builder setHeader(String name, String value);
        Builder timeout(Duration duration); Builder GET(); Builder DELETE(); Builder POST(BodyPublisher body); Builder PUT(BodyPublisher body);
        // JDK 18
        default Builder HEAD() { return method("HEAD", BodyPublishers.noBody()); }
        Builder method(String method, BodyPublisher body); HttpRequest build(); Builder copy();
    }
    public interface BodyPublisher extends Flow.Publisher<ByteBuffer> { long contentLength(); }
    public static final class BodyPublishers {
        private BodyPublishers() {}
        public static BodyPublisher noBody() { return ofByteArray(new byte[0]); }
        public static BodyPublisher ofString(String body) { return ofString(body, StandardCharsets.UTF_8); }
        public static BodyPublisher ofString(String body, Charset charset) { return ofByteArray(body.getBytes(charset)); }
        public static BodyPublisher ofByteArray(byte[] body) { return ofByteArray(body, 0, body.length); }
        public static BodyPublisher ofByteArray(byte[] body, int offset, int length) {
            if (offset < 0 || length < 0 || offset > body.length - length) throw new IndexOutOfBoundsException();
            byte[] snapshot = Arrays.copyOfRange(body, offset, offset + length);
            return input(() -> new ByteArrayInputStream(snapshot), length);
        }
        public static BodyPublisher ofInputStream(Supplier<? extends InputStream> source) { return input(source, -1); }
        public static BodyPublisher ofByteArrays(Iterable<byte[]> iter) {
            Objects.requireNonNull(iter);
            return input(() -> {
                Iterator<byte[]> arrays = iter.iterator();
                return new SequenceInputStream(new Enumeration<InputStream>() {
                    public boolean hasMoreElements() { return arrays.hasNext(); }
                    public InputStream nextElement() { return new ByteArrayInputStream(arrays.next()); }
                });
            }, -1);
        }
        // JDK 16
        public static BodyPublisher concat(BodyPublisher... publishers) {
            List<BodyPublisher> parts = new ArrayList<>();
            for (BodyPublisher publisher : publishers) parts.add(Objects.requireNonNull(publisher));
            if (parts.isEmpty()) return noBody();
            if (parts.size() == 1) return parts.get(0);
            long length = 0;
            for (BodyPublisher part : parts) {
                long size = part.contentLength();
                length = size < 0 || length < 0 || length > Long.MAX_VALUE - size ? -1 : length + size;
            }
            final long total = length;
            return new BodyPublisher() {
                public long contentLength() { return total; }
                public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) { new Concatenation(parts.iterator(), Objects.requireNonNull(subscriber)).start(); }
            };
        }
        /** Subscribes to each part in turn, carrying the unmet downstream demand over. */
        private static final class Concatenation implements Flow.Subscription {
            final Iterator<BodyPublisher> remaining; final Flow.Subscriber<? super ByteBuffer> downstream;
            long demand; Flow.Subscription current; boolean cancelled, finished;
            Concatenation(Iterator<BodyPublisher> remaining, Flow.Subscriber<? super ByteBuffer> downstream) { this.remaining = remaining; this.downstream = downstream; }
            void start() { downstream.onSubscribe(this); next(); }
            private void next() {
                boolean done;
                synchronized (this) { if (cancelled || finished) return; current = null; done = !remaining.hasNext(); if (done) finished = true; }
                if (done) { downstream.onComplete(); return; }
                remaining.next().subscribe(new Flow.Subscriber<ByteBuffer>() {
                    public void onSubscribe(Flow.Subscription subscription) {
                        long request; boolean cancel;
                        synchronized (Concatenation.this) { current = subscription; request = demand; cancel = cancelled; }
                        if (cancel) subscription.cancel(); else if (request > 0) subscription.request(request);
                    }
                    public void onNext(ByteBuffer item) { synchronized (Concatenation.this) { if (demand != Long.MAX_VALUE) demand--; } downstream.onNext(item); }
                    public void onError(Throwable error) { synchronized (Concatenation.this) { if (finished) return; finished = true; } downstream.onError(error); }
                    public void onComplete() { next(); }
                });
            }
            public void request(long n) {
                if (n <= 0) { cancel(); synchronized (this) { if (finished) return; finished = true; } downstream.onError(new IllegalArgumentException("nonpositive demand")); return; }
                Flow.Subscription target;
                synchronized (this) { demand = demand > Long.MAX_VALUE - n ? Long.MAX_VALUE : demand + n; target = current; }
                if (target != null) target.request(n);
            }
            public void cancel() {
                Flow.Subscription target;
                synchronized (this) { cancelled = true; target = current; }
                if (target != null) target.cancel();
            }
        }
        public static BodyPublisher ofFile(Path path) throws FileNotFoundException {
            if (!Files.isReadable(path)) throw new FileNotFoundException(path.toString());
            long length; try { length = Files.size(path); } catch (IOException e) { throw new FileNotFoundException(path.toString()); }
            return input(() -> { try { return Files.newInputStream(path); } catch (IOException e) { throw new java.io.UncheckedIOException(e); } }, length);
        }
        public static BodyPublisher fromPublisher(Flow.Publisher<? extends ByteBuffer> source) { return publisher(source, -1); }
        public static BodyPublisher fromPublisher(Flow.Publisher<? extends ByteBuffer> source, long length) {
            if (length <= 0) throw new IllegalArgumentException("Positive content length required");
            return publisher(source, length);
        }
        private static BodyPublisher publisher(Flow.Publisher<? extends ByteBuffer> source, long length) {
            Objects.requireNonNull(source);
            return new BodyPublisher() { public long contentLength() { return length; } public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) { source.subscribe(subscriber); } };
        }
        private static BodyPublisher input(Supplier<? extends InputStream> source, long length) {
            Objects.requireNonNull(source);
            return new BodyPublisher() {
                public long contentLength() { return length; }
                public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                    Objects.requireNonNull(subscriber);
                    subscriber.onSubscribe(new Flow.Subscription() {
                        InputStream input; boolean finished, emitting; long demand;
                        public synchronized void request(long count) {
                            if (finished) return;
                            if (count <= 0) { finished = true; close(); subscriber.onError(new IllegalArgumentException("nonpositive demand")); return; }
                            demand = demand > Long.MAX_VALUE - count ? Long.MAX_VALUE : demand + count;
                            if (emitting) return;
                            emitting = true;
                            try {
                                if (input == null) input = Objects.requireNonNull(source.get());
                                while (demand > 0 && !finished) {
                                    byte[] buffer = new byte[8192]; int n = input.read(buffer);
                                    if (n < 0) { finished = true; close(); subscriber.onComplete(); break; }
                                    demand--; subscriber.onNext(ByteBuffer.wrap(buffer, 0, n));
                                }
                            } catch (Throwable error) { finished = true; close(); subscriber.onError(error); }
                            finally { emitting = false; }
                        }
                        private void close() { if (input != null) try { input.close(); } catch (IOException ignored) {} }
                        public synchronized void cancel() { finished = true; close(); }
                    });
                }
            };
        }
    }
    static final class RequestBuilder implements Builder {
        URI uri; Duration timeout; HttpClient.Version version; boolean expect; String method = "GET"; BodyPublisher body;
        final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("HTTP URI required");
            this.uri = uri; return this;
        }
        public Builder expectContinue(boolean enabled) { expect = enabled; return this; }
        public Builder version(HttpClient.Version version) { this.version = Objects.requireNonNull(version); return this; }
        static void validateHeader(String name, String value) {
            if (name == null || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) throw new IllegalArgumentException("Invalid header");
            if (Arrays.asList("connection", "content-length", "expect", "host", "upgrade", "transfer-encoding").contains(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Restricted header");
        }
        public Builder header(String name, String value) { validateHeader(name, value); headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value); return this; }
        public Builder setHeader(String name, String value) { validateHeader(name, value); headers.put(name, new ArrayList<>(Collections.singletonList(value))); return this; }
        public Builder headers(String... values) { if (values.length % 2 != 0) throw new IllegalArgumentException("Header pairs required"); for (int i=0;i<values.length;i+=2) header(values[i],values[i+1]); return this; }
        public Builder timeout(Duration timeout) { if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required"); this.timeout=timeout; return this; }
        public Builder GET() { method="GET"; body=null; return this; }
        public Builder DELETE() { method="DELETE"; body=null; return this; }
        public Builder POST(BodyPublisher body) { return method("POST",body); }
        public Builder PUT(BodyPublisher body) { return method("PUT",body); }
        public Builder method(String method, BodyPublisher body) { if (method == null || !method.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || method.equalsIgnoreCase("CONNECT")) throw new IllegalArgumentException("Invalid method"); this.method=method; this.body=Objects.requireNonNull(body); return this; }
        public Builder copy() { RequestBuilder copy=new RequestBuilder(); copy.uri=uri; copy.timeout=timeout; copy.version=version; copy.expect=expect; copy.method=method; copy.body=body; headers.forEach((key,value)->copy.headers.put(key,new ArrayList<>(value))); return copy; }
        public HttpRequest build() {
            if (uri == null) throw new IllegalStateException("URI required");
            RequestBuilder copy=(RequestBuilder)copy(); HttpHeaders values=HttpHeaders.of(headers,(k,v)->true);
            return new HttpRequest() {
                public Optional<BodyPublisher> bodyPublisher(){return Optional.ofNullable(copy.body);} public String method(){return copy.method;}
                public Optional<Duration> timeout(){return Optional.ofNullable(copy.timeout);} public boolean expectContinue(){return copy.expect;}
                public URI uri(){return copy.uri;} public Optional<HttpClient.Version> version(){return Optional.ofNullable(copy.version);} public HttpHeaders headers(){return values;}
            };
        }
    }
}
