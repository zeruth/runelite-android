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
import java.util.*;
import java.util.function.BiPredicate;

public final class HttpHeaders {
    private final Map<String, List<String>> values;
    private HttpHeaders(Map<String, List<String>> source, BiPredicate<String, String> filter) {
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((key, list) -> {
            if (key != null) for (String value : list) if (filter.test(key, value)) copy.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        });
        copy.replaceAll((key, list) -> Collections.unmodifiableList(list)); values = Collections.unmodifiableMap(copy);
    }
    public static HttpHeaders of(Map<String, List<String>> values, BiPredicate<String, String> filter) { return new HttpHeaders(values, filter); }
    public Optional<String> firstValue(String name) { List<String> list = values.get(name); return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)); }
    public OptionalLong firstValueAsLong(String name) { Optional<String> value = firstValue(name); return value.isPresent() ? OptionalLong.of(Long.parseLong(value.get())) : OptionalLong.empty(); }
    public List<String> allValues(String name) { return values.getOrDefault(name, Collections.emptyList()); }
    public Map<String, List<String>> map() { return values; }
    @Override public boolean equals(Object other) { return other instanceof HttpHeaders && values.equals(((HttpHeaders) other).values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return values.toString(); }
}
