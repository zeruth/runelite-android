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
package javax.management;

import java.io.Serializable;

public class ObjectName implements Comparable<ObjectName>, QueryExp, Serializable {
    private static final long serialVersionUID = 1L;
    /** {@code *:*}, the pattern that matches every name. */
    public static final ObjectName WILDCARD;
    static {
        try {
            WILDCARD = new ObjectName("*:*");
        } catch (MalformedObjectNameException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
    private final String name, domain, canonical, keyPropertyList;
    private final java.util.TreeMap<String, String> properties = new java.util.TreeMap<>();
    private final boolean listPattern;

    public ObjectName(String name) throws MalformedObjectNameException {
        if (name == null) throw new NullPointerException("name");
        if (name.isEmpty()) name = "*:*";
        this.name = name;
        int colon = name.indexOf(':');
        if (colon < 0 || name.indexOf('\n') >= 0) throw malformed();
        domain = name.substring(0, colon);
        String body = name.substring(colon + 1);
        boolean pattern = false;
        StringBuilder ordered = new StringBuilder();
        if (body.isEmpty()) throw malformed();
        for (int p = 0; p < body.length();) {
            if (body.charAt(p) == '*') {
                if (pattern) throw malformed();
                pattern = true; p++;
            } else {
                int equal = body.indexOf('=', p);
                if (equal < 0) throw malformed();
                String key = body.substring(p, equal);
                if (key.isEmpty() || key.matches(".*[,:=*?].*")) throw malformed();
                p = equal + 1; int start = p;
                if (p < body.length() && body.charAt(p) == '"') {
                    p++; boolean closed = false;
                    while (p < body.length()) {
                        char c = body.charAt(p++);
                        if (c == '\\') {
                            if (p == body.length() || "\\\"*?n".indexOf(body.charAt(p++)) < 0) throw malformed();
                        } else if (c == '"') { closed = true; break; }
                    }
                    if (!closed) throw malformed();
                } else {
                    while (p < body.length() && body.charAt(p) != ',') {
                        if (":=\"".indexOf(body.charAt(p)) >= 0) throw malformed();
                        p++;
                    }
                }
                if (properties.put(key, body.substring(start, p)) != null) throw malformed();
                if (ordered.length() != 0) ordered.append(',');
                ordered.append(key).append('=').append(body, start, p);
            }
            if (p < body.length() && (body.charAt(p++) != ',' || p == body.length())) throw malformed();
        }
        listPattern = pattern;
        keyPropertyList = ordered.toString();
        canonical = domain + ":" + join(properties) + (pattern ? (properties.isEmpty() ? "*" : ",*") : "");
    }

    public ObjectName(String domain, String key, String value) throws MalformedObjectNameException {
        this(domain + ":" + key + "=" + value);
    }

    public ObjectName(String domain, java.util.Hashtable<String, String> table) throws MalformedObjectNameException {
        this(domain + ":" + join(table));
    }

    private static MalformedObjectNameException malformed() { return new MalformedObjectNameException("Invalid object name"); }
    private static String join(java.util.Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() != 0) result.append(',');
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }
    public static ObjectName getInstance(String name) throws MalformedObjectNameException { return new ObjectName(name); }
    public static ObjectName getInstance(ObjectName name) { return name; }
    public String getCanonicalName() { return canonical; }
    public String getDomain() { return domain; }
    public String getKeyProperty(String property) { return properties.get(property); }
    public java.util.Hashtable<String, String> getKeyPropertyList() { return new java.util.Hashtable<>(properties); }
    public String getCanonicalKeyPropertyListString() { return join(properties); }
    /** The key properties in the order the name was written with. */
    public String getKeyPropertyListString() { return keyPropertyList; }
    private static boolean wildcard(String value) {
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped && (c == '*' || c == '?')) return true;
            if (!escaped && c == '\\') escaped = true; else escaped = false;
        }
        return false;
    }
    public boolean isDomainPattern() { return wildcard(domain); }
    public boolean isPropertyListPattern() { return listPattern; }
    public boolean isPropertyValuePattern() { for (String value : properties.values()) if (wildcard(value)) return true; return false; }
    public boolean isPropertyValuePattern(String key) { if (!properties.containsKey(key)) throw new IllegalArgumentException("Missing property"); return wildcard(properties.get(key)); }
    public boolean isPropertyPattern() { return listPattern || isPropertyValuePattern(); }
    public boolean isPattern() { return isDomainPattern() || isPropertyPattern(); }
    private static boolean matches(String pattern, String value) {
        StringBuilder regex = new StringBuilder(); boolean escaped = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            regex.append(!escaped && c == '*' ? ".*" : !escaped && c == '?' ? "." : java.util.regex.Pattern.quote(String.valueOf(c)));
            if (!escaped && c == '\\') escaped = true; else escaped = false;
        }
        return value.matches(regex.toString());
    }
    public boolean apply(ObjectName other) {
        if (other.isPattern() || !matches(domain, other.domain)) return false;
        if (!listPattern && properties.size() != other.properties.size()) return false;
        for (java.util.Map.Entry<String, String> entry : properties.entrySet()) {
            String value = other.properties.get(entry.getKey());
            if (value == null || !matches(entry.getValue(), value)) return false;
        }
        return true;
    }
    public static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n') result.append("\\n");
            else { if ("\\\"*?".indexOf(c) >= 0) result.append('\\'); result.append(c); }
        }
        return result.append('"').toString();
    }
    public static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length()-1) != '"') throw new IllegalArgumentException("Not quoted");
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < value.length()-1; i++) {
            char c = value.charAt(i);
            if (c == '\\') { if (++i >= value.length()-1) throw new IllegalArgumentException("Bad escape"); c = value.charAt(i); if (c == 'n') c = '\n'; else if ("\\\"*?".indexOf(c) < 0) throw new IllegalArgumentException("Bad escape"); }
            else if ("\n\"*?".indexOf(c) >= 0) throw new IllegalArgumentException("Unescaped character");
            result.append(c);
        }
        return result.toString();
    }

    /** An ObjectName used as a query selects nothing beyond the pattern itself. */
    @Override public void setMBeanServer(MBeanServer server) {}

    /** Domain first, then the {@code type} key, then the canonical name, as the JDK orders names. */
    @Override public int compareTo(ObjectName other) {
        if (other == this) return 0;
        int byDomain = domain.compareTo(other.domain);
        if (byDomain != 0) return byDomain;
        String type = properties.get("type"), otherType = other.properties.get("type");
        int byType = (type == null ? "" : type).compareTo(otherType == null ? "" : otherType);
        return byType != 0 ? byType : canonical.compareTo(other.canonical);
    }

    @Override public String toString() { return name; }
    @Override public boolean equals(Object obj) { return obj instanceof ObjectName && ((ObjectName) obj).canonical.equals(canonical); }
    @Override public int hashCode() { return canonical.hashCode(); }
}
