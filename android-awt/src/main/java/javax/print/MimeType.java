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
package javax.print;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The package-private RFC 2045 media type behind {@link DocFlavor}, reimplemented to the
 * observable behaviour of the 8u helper: type, subtype and parameter names are lower-cased,
 * so is the {@code charset} value of a {@code text/*} type, parameters are kept sorted by
 * name, and the canonical form is {@code type/subtype; name="value"}. Comments in
 * parentheses are skipped; malformed input throws {@link IllegalArgumentException} and
 * {@code null} throws {@link NullPointerException}, as in 8u.
 */
final class MimeType implements Serializable, Cloneable {

    // DocFlavor keeps its MimeType transient and serializes the canonical string, so this
    // class's own serial form is never on the wire.
    private static final long serialVersionUID = 1L;

    private final String type;
    private final String subtype;
    private final Map<String, String> parameters;
    private transient String stringValue;

    MimeType(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        Parser p = new Parser(s);
        String t = p.token();
        p.expect('/');
        String st = p.token();
        TreeMap<String, String> params = new TreeMap<String, String>();
        while (p.more()) {
            p.expect(';');
            if (!p.more()) {
                break; // a trailing ';' is tolerated, as by the 8u lexer
            }
            String name = p.token().toLowerCase(Locale.ENGLISH);
            p.expect('=');
            String value = p.value();
            params.put(name, value);
        }
        type = t.toLowerCase(Locale.ENGLISH);
        subtype = st.toLowerCase(Locale.ENGLISH);
        if ("text".equals(type) && params.containsKey("charset")) {
            params.put("charset", params.get("charset").toLowerCase(Locale.ENGLISH));
        }
        parameters = Collections.unmodifiableMap(params);
    }

    String getMimeType() {
        return getStringValue();
    }

    String getMediaType() {
        return type;
    }

    String getMediaSubtype() {
        return subtype;
    }

    Map<String, String> getParameterMap() {
        return parameters;
    }

    public String toString() {
        return getStringValue();
    }

    public int hashCode() {
        return getStringValue().hashCode();
    }

    public boolean equals(Object obj) {
        return obj instanceof MimeType
            && getStringValue().equals(((MimeType) obj).getStringValue());
    }

    private String getStringValue() {
        if (stringValue == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(type).append('/').append(subtype);
            for (Map.Entry<String, String> e : parameters.entrySet()) {
                sb.append("; ").append(e.getKey()).append("=\"").append(e.getValue()).append('"');
            }
            stringValue = sb.toString();
        }
        return stringValue;
    }

    /** Lexer over RFC 2045 tokens, quoted strings and RFC 822 comments. */
    private static final class Parser {
        private static final String TSPECIALS = "()<>@,;:\\\"/[]?=";
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        private void skip() {
            for (;;) {
                while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                    pos++;
                }
                if (pos < s.length() && s.charAt(pos) == '(') {
                    int depth = 0;
                    do {
                        char c = s.charAt(pos++);
                        if (c == '\\') {
                            pos++;
                        } else if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            depth--;
                        }
                    } while (depth > 0 && pos < s.length());
                    if (depth > 0) {
                        throw new IllegalArgumentException("unterminated comment");
                    }
                } else {
                    return;
                }
            }
        }

        boolean more() {
            skip();
            return pos < s.length();
        }

        void expect(char c) {
            skip();
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("expected '" + c + "' in " + s);
            }
            pos++;
        }

        String token() {
            skip();
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c <= ' ' || c >= 0x7F || TSPECIALS.indexOf(c) >= 0) {
                    break;
                }
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("expected a token in " + s);
            }
            return s.substring(start, pos);
        }

        String value() {
            skip();
            if (pos < s.length() && s.charAt(pos) == '"') {
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < s.length()) {
                    char c = s.charAt(pos++);
                    if (c == '"') {
                        return sb.toString();
                    }
                    if (c == '\\' && pos < s.length()) {
                        c = s.charAt(pos++);
                    }
                    sb.append(c);
                }
                throw new IllegalArgumentException("unterminated quoted string in " + s);
            }
            return token();
        }
    }
}
