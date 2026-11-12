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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stateful body subscribers behind {@link HttpResponse.BodySubscribers}. Kept out of the
 * interface so the shadow adds no public types to {@code java.net.http}. Buffer state changes go
 * through {@link Buffer} because the covariant {@code ByteBuffer} overrides are JDK 9+.
 */
final class ResponseSubscribers {

    private ResponseSubscribers() {
    }

    private static long add(long demand, long n) {
        return demand > Long.MAX_VALUE - n ? Long.MAX_VALUE : demand + n;
    }

    /**
     * Re-slices response bytes into items of another shape and hands them on at the
     * downstream's own demand, asking upstream for one more list whenever nothing is ready.
     */
    abstract static class Relay<I> implements Flow.Subscriber<List<ByteBuffer>> {
        private final Flow.Subscriber<? super I> downstream;
        private final ArrayDeque<I> ready = new ArrayDeque<>();
        private Flow.Subscription upstream;
        private long demand;
        private boolean asked, completed, terminated, draining;

        Relay(Flow.Subscriber<? super I> downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        /** Called under the relay's lock; {@link #emit} what became whole. */
        abstract void accept(ByteBuffer buffer) throws IOException;

        /** Called under the relay's lock at end of input. */
        abstract void finish() throws IOException;

        final void emit(I item) {
            ready.add(item);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            synchronized (this) {
                if (upstream != null) {
                    subscription.cancel();
                    return;
                }
                upstream = subscription;
            }
            downstream.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (n <= 0) {
                        cancel();
                        fail(new IllegalArgumentException("nonpositive demand"));
                        return;
                    }
                    synchronized (Relay.this) {
                        demand = add(demand, n);
                    }
                    drain();
                }

                @Override
                public void cancel() {
                    synchronized (Relay.this) {
                        terminated = true;
                        ready.clear();
                    }
                    upstream.cancel();
                }
            });
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            try {
                synchronized (this) {
                    asked = false;
                    for (ByteBuffer item : items) accept(item);
                }
            } catch (Throwable error) {
                upstream.cancel();
                fail(error);
                return;
            }
            drain();
        }

        @Override
        public void onError(Throwable error) {
            fail(error);
        }

        @Override
        public void onComplete() {
            try {
                synchronized (this) {
                    completed = true;
                    asked = false;
                    finish();
                }
            } catch (Throwable error) {
                fail(error);
                return;
            }
            drain();
        }

        private void fail(Throwable error) {
            synchronized (this) {
                if (terminated) return;
                terminated = true;
                ready.clear();
            }
            downstream.onError(error);
        }

        private void drain() {
            while (true) {
                I item = null;
                boolean finish = false, ask = false;
                synchronized (this) {
                    if (terminated || draining) return;
                    if (demand > 0 && !ready.isEmpty()) {
                        item = ready.poll();
                        if (demand != Long.MAX_VALUE) demand--;
                        draining = true;
                    } else if (ready.isEmpty() && completed) {
                        terminated = true;
                        finish = true;
                    } else if (demand > 0 && !asked && !completed) {
                        asked = true;
                        ask = true;
                    }
                }
                if (item != null) {
                    try {
                        downstream.onNext(item);
                    } finally {
                        synchronized (this) {
                            draining = false;
                        }
                    }
                    continue;
                }
                if (finish) downstream.onComplete();
                if (ask) upstream.request(1);
                return;
            }
        }
    }

    /** {@code BodySubscribers.buffering}: lists of exactly {@code size} bytes, the last one shorter. */
    static final class Buffering<T> extends Relay<List<ByteBuffer>> implements HttpResponse.BodySubscriber<T> {
        private final HttpResponse.BodySubscriber<T> target;
        private final int size;
        private ByteBuffer partial;

        Buffering(HttpResponse.BodySubscriber<T> target, int size) {
            super(target);
            this.target = target;
            this.size = size;
        }

        @Override
        void accept(ByteBuffer buffer) {
            while (buffer.hasRemaining()) {
                if (partial == null) partial = ByteBuffer.allocate(size);
                int n = Math.min(partial.remaining(), buffer.remaining());
                ByteBuffer slice = buffer.duplicate();
                ((Buffer) slice).limit(slice.position() + n);
                partial.put(slice);
                ((Buffer) buffer).position(buffer.position() + n);
                if (!partial.hasRemaining()) {
                    ((Buffer) partial).flip();
                    emit(Collections.singletonList(partial));
                    partial = null;
                }
            }
        }

        @Override
        void finish() {
            if (partial != null && partial.position() > 0) {
                ((Buffer) partial).flip();
                emit(Collections.singletonList(partial));
            }
            partial = null;
        }

        @Override
        public CompletionStage<T> getBody() {
            return target.getBody();
        }
    }

    /** Decodes and splits into lines; {@code separator == null} splits on CR, LF and CRLF. */
    static final class Lines extends Relay<String> {
        private final CharsetDecoder decoder;
        private final String separator;
        private final StringBuilder text = new StringBuilder();
        private ByteBuffer carry;

        Lines(Flow.Subscriber<? super String> downstream, Charset charset, String separator) {
            super(downstream);
            this.decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.separator = separator;
        }

        @Override
        void accept(ByteBuffer buffer) throws CharacterCodingException {
            decode(buffer, false);
            split(false);
        }

        @Override
        void finish() throws CharacterCodingException {
            decode(ByteBuffer.allocate(0), true);
            split(true);
        }

        private void decode(ByteBuffer input, boolean end) throws CharacterCodingException {
            ByteBuffer in = input;
            if (carry != null) {
                in = ByteBuffer.allocate(carry.remaining() + input.remaining());
                in.put(carry);
                in.put(input);
                ((Buffer) in).flip();
                carry = null;
            }
            CharBuffer out = CharBuffer.allocate(Math.max(64, (int) (in.remaining() * decoder.maxCharsPerByte()) + 1));
            while (true) {
                CoderResult result = decoder.decode(in, out, end);
                if (result.isError()) result.throwException();
                if (!result.isOverflow()) break;
                take(out);
            }
            if (end) while (decoder.flush(out).isOverflow()) take(out);
            take(out);
            if (in.hasRemaining()) {
                carry = ByteBuffer.allocate(in.remaining());
                carry.put(in);
                ((Buffer) carry).flip();
            }
        }

        private void take(CharBuffer out) {
            ((Buffer) out).flip();
            text.append(out);
            ((Buffer) out).clear();
        }

        private void split(boolean end) {
            int start = 0;
            if (separator == null) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\n') {
                        emit(text.substring(start, i));
                        start = i + 1;
                    } else if (c == '\r') {
                        // A trailing CR may still be the first half of a CRLF.
                        if (i + 1 >= text.length() && !end) break;
                        emit(text.substring(start, i));
                        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                        start = i + 1;
                    }
                }
            } else {
                for (int at = text.indexOf(separator, start); at >= 0; at = text.indexOf(separator, start)) {
                    emit(text.substring(start, at));
                    start = at + separator.length();
                }
            }
            text.delete(0, start);
            if (end && text.length() > 0) {
                emit(text.toString());
                text.setLength(0);
            }
        }
    }

    /** Forwards to {@code subscriber}; the body is {@code finisher(subscriber)} once it completed. */
    static <I, S extends Flow.Subscriber<? super I>, T> Flow.Subscriber<I> finishing(S subscriber, Function<? super S, ? extends T> finisher, CompletableFuture<T> body) {
        Objects.requireNonNull(subscriber);
        Objects.requireNonNull(finisher);
        return new Flow.Subscriber<I>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            @Override public void onNext(I item) { subscriber.onNext(item); }

            @Override
            public void onError(Throwable error) {
                try {
                    subscriber.onError(error);
                } finally {
                    body.completeExceptionally(error);
                }
            }

            @Override
            public void onComplete() {
                try {
                    subscriber.onComplete();
                    body.complete(finisher.apply(subscriber));
                } catch (Throwable error) {
                    body.completeExceptionally(error);
                }
            }
        };
    }

    static <T> HttpResponse.BodySubscriber<T> body(Flow.Subscriber<List<ByteBuffer>> delegate, CompletionStage<T> body) {
        return new HttpResponse.BodySubscriber<T>() {
            @Override public CompletionStage<T> getBody() { return body; }
            @Override public void onSubscribe(Flow.Subscription subscription) { delegate.onSubscribe(subscription); }
            @Override public void onNext(List<ByteBuffer> item) { delegate.onNext(item); }
            @Override public void onError(Throwable error) { delegate.onError(error); }
            @Override public void onComplete() { delegate.onComplete(); }
        };
    }

    /** {@code BodySubscribers.ofByteArrayConsumer}: one array per received list, then {@code Optional.empty()}. */
    static HttpResponse.BodySubscriber<Void> byteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
        Objects.requireNonNull(consumer);
        CompletableFuture<Void> body = new CompletableFuture<>();
        return body(new Flow.Subscriber<List<ByteBuffer>>() {
            Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                int total = 0;
                for (ByteBuffer item : items) total += item.remaining();
                byte[] bytes = new byte[total];
                int at = 0;
                for (ByteBuffer item : items) {
                    int n = item.remaining();
                    item.get(bytes, at, n);
                    at += n;
                }
                try {
                    consumer.accept(Optional.of(bytes));
                } catch (Throwable error) {
                    subscription.cancel();
                    body.completeExceptionally(error);
                    return;
                }
                subscription.request(1);
            }

            @Override public void onError(Throwable error) { body.completeExceptionally(error); }

            @Override
            public void onComplete() {
                try {
                    consumer.accept(Optional.<byte[]>empty());
                    body.complete(null);
                } catch (Throwable error) {
                    body.completeExceptionally(error);
                }
            }
        }, body);
    }

    /**
     * {@code BodySubscribers.ofPublisher}: the body is available at once and replays the
     * response to its single subscriber, passing that subscriber's demand upstream.
     */
    static final class Publishing implements HttpResponse.BodySubscriber<Flow.Publisher<List<ByteBuffer>>>, Flow.Publisher<List<ByteBuffer>> {
        private final CompletableFuture<Flow.Publisher<List<ByteBuffer>>> body = CompletableFuture.completedFuture(this);
        private Flow.Subscription upstream;
        private Flow.Subscriber<? super List<ByteBuffer>> downstream;
        private long pending;
        private boolean cancelled, completed, delivered;
        private Throwable failure;

        @Override public CompletionStage<Flow.Publisher<List<ByteBuffer>>> getBody() { return body; }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            Objects.requireNonNull(subscriber);
            synchronized (this) {
                if (downstream != null) {
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override public void request(long n) { }
                        @Override public void cancel() { }
                    });
                    subscriber.onError(new IllegalStateException("The body publisher accepts one subscriber"));
                    return;
                }
                downstream = subscriber;
            }
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (n <= 0) {
                        cancel();
                        terminate(new IllegalArgumentException("nonpositive demand"));
                        return;
                    }
                    Flow.Subscription target;
                    synchronized (Publishing.this) {
                        target = upstream;
                        if (target == null) pending = add(pending, n);
                    }
                    if (target != null) target.request(n);
                }

                @Override
                public void cancel() {
                    Flow.Subscription target;
                    synchronized (Publishing.this) {
                        cancelled = true;
                        target = upstream;
                    }
                    if (target != null) target.cancel();
                }
            });
            Throwable error;
            boolean done;
            synchronized (this) {
                error = failure;
                done = completed;
            }
            if (error != null || done) terminate(error);
        }

        private void terminate(Throwable error) {
            Flow.Subscriber<? super List<ByteBuffer>> target;
            synchronized (this) {
                if (delivered || downstream == null) return;
                delivered = true;
                target = downstream;
            }
            if (error != null) target.onError(error);
            else target.onComplete();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            long request;
            boolean cancel;
            synchronized (this) {
                upstream = subscription;
                request = pending;
                pending = 0;
                cancel = cancelled;
            }
            if (cancel) subscription.cancel();
            else if (request > 0) subscription.request(request);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            Flow.Subscriber<? super List<ByteBuffer>> target;
            synchronized (this) {
                target = downstream;
            }
            // Upstream only sends what downstream asked for, so a subscriber is always present here.
            if (target != null) target.onNext(item);
        }

        @Override
        public void onError(Throwable error) {
            synchronized (this) {
                failure = error;
            }
            terminate(error);
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                completed = true;
            }
            terminate(null);
        }
    }

    private static final Pattern FILENAME = Pattern.compile("(?i)(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))");

    /** The target of {@code BodyHandlers.ofFileDownload}: a plain file name from an attachment disposition. */
    static Path download(Path directory, HttpHeaders headers) {
        String disposition = headers.firstValue("Content-Disposition")
            .orElseThrow(() -> new UncheckedIOException(new IOException("No Content-Disposition header")));
        if (!disposition.trim().toLowerCase(Locale.ROOT).startsWith("attachment"))
            throw new UncheckedIOException(new IOException("Content-Disposition is not an attachment: " + disposition));
        Matcher matcher = FILENAME.matcher(disposition);
        if (!matcher.find()) throw new UncheckedIOException(new IOException("No filename in Content-Disposition: " + disposition));
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0)
            throw new UncheckedIOException(new IOException("Unsafe filename in Content-Disposition: " + name));
        Path base = directory.normalize();
        Path target = base.resolve(name).normalize();
        if (!base.equals(target.getParent()))
            throw new UncheckedIOException(new IOException("Filename leaves the download directory: " + name));
        return target;
    }

    static void checkDownloadDirectory(Path directory) {
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Not a directory: " + directory);
        if (!Files.isWritable(directory)) throw new IllegalArgumentException("Directory not writable: " + directory);
    }
}
