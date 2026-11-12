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
package java.beans;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The {@code XMLDecoder} class is used to read XML documents
 * created using the {@code XMLEncoder} and is used just like
 * the {@code ObjectInputStream}. Parsing runs on the platform SAX parser
 * ({@code javax.xml.parsers}, present on Android) the first time an object
 * is read; the JDK archive elements are understood: {@code java},
 * {@code object}, {@code void}, {@code array}, {@code null}, {@code string},
 * {@code char}, {@code boolean}, {@code true}, {@code false}, the numeric
 * primitives, {@code class}, and the short forms {@code new}, {@code var},
 * {@code method}, {@code property} and {@code field}.
 *
 * @see XMLEncoder
 * @since 1.4
 */
public class XMLDecoder implements AutoCloseable {
    private final InputSource input;
    private final Handler handler;
    private Object owner;
    private ExceptionListener listener = Statement.defaultExceptionListener;
    private Object[] array;
    private int index;

    /**
     * Creates a new input stream for reading archives
     * created by the {@code XMLEncoder} class.
     *
     * @param in The underlying stream.
     */
    public XMLDecoder(InputStream in) {
        this(in, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the {@code XMLEncoder} class.
     *
     * @param in The underlying stream.
     * @param owner The owner of this stream.
     */
    public XMLDecoder(InputStream in, Object owner) {
        this(in, owner, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the {@code XMLEncoder} class.
     *
     * @param in the underlying stream.
     * @param owner the owner of this stream.
     * @param exceptionListener the exception handler for the stream;
     *        if {@code null} the default exception listener will be used.
     */
    public XMLDecoder(InputStream in, Object owner, ExceptionListener exceptionListener) {
        this(in, owner, exceptionListener, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the {@code XMLEncoder} class.
     *
     * @param in the underlying stream.  {@code null} may be passed without
     *        error, though the resulting XMLDecoder will be useless
     * @param owner the owner of this stream.  {@code null} is a legal
     *        value
     * @param exceptionListener the exception handler for the stream, or
     *        {@code null} to use the default
     * @param cl the class loader used for instantiating objects.
     *        {@code null} indicates that the default class loader should
     *        be used
     * @since 1.5
     */
    public XMLDecoder(InputStream in, Object owner, ExceptionListener exceptionListener, ClassLoader cl) {
        this(in != null ? new InputSource(in) : null, owner, exceptionListener, cl);
    }

    /**
     * Creates a new decoder to parse XML archives
     * created by the {@code XMLEncoder} class.
     * If the input source {@code is} is {@code null},
     * no exception is thrown and no parsing is performed.
     *
     * @param is  the input source to parse
     * @since 1.7
     */
    public XMLDecoder(InputSource is) {
        this(is, null, null, null);
    }

    private XMLDecoder(InputSource is, Object owner, ExceptionListener el, ClassLoader cl) {
        this.input = is;
        this.owner = owner;
        setExceptionListener(el);
        this.handler = new Handler(this, null, null, cl);
    }

    /**
     * This method closes the input stream associated
     * with this stream.
     */
    public void close() {
        if (parsingComplete()) {
            close(input.getCharacterStream());
            close(input.getByteStream());
        }
    }

    private void close(Closeable in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                getExceptionListener().exceptionThrown(e);
            }
        }
    }

    private boolean parsingComplete() {
        if (input == null) {
            return false;
        }
        if (array == null) {
            try {
                SAXParserFactory.newInstance().newSAXParser().parse(input, handler);
            } catch (Exception e) {
                getExceptionListener().exceptionThrown(e);
            }
            array = handler.results.toArray();
        }
        return true;
    }

    /**
     * Sets the exception handler for this stream to {@code exceptionListener}.
     *
     * @param exceptionListener The exception handler for this stream;
     * if {@code null} the default exception listener will be used.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.listener = exceptionListener != null ? exceptionListener : Statement.defaultExceptionListener;
    }

    /**
     * Gets the exception handler for this stream.
     *
     * @return The exception handler for this stream.
     *     Will return the default exception listener if this has not explicitly been set.
     */
    public ExceptionListener getExceptionListener() {
        return listener;
    }

    /**
     * Reads the next object from the underlying input stream.
     *
     * @return the next object read
     * @throws ArrayIndexOutOfBoundsException if the stream contains no objects
     *         (or no more objects)
     */
    public Object readObject() {
        return (parsingComplete()) ? this.array[this.index++] : null;
    }

    /**
     * Sets the owner of this decoder to {@code owner}.
     *
     * @param owner The owner of this decoder.
     */
    public void setOwner(Object owner) {
        this.owner = owner;
    }

    /**
     * Gets the owner of this decoder.
     *
     * @return The owner of this decoder.
     */
    public Object getOwner() {
        return owner;
    }

    /**
     * Creates a new handler for SAX parser
     * that can be used to parse embedded XML archives
     * created by the {@code XMLEncoder} class.
     *
     * @param owner  the owner of the embedded XML archive
     * @param el     the exception handler for the parser,
     *               or {@code null} to use the default exception handler
     * @param cl     the class loader used for instantiating objects,
     *               or {@code null} to use the default class loader
     * @return an instance of {@code DefaultHandler} for SAX parser
     * @since 1.7
     */
    public static DefaultHandler createHandler(Object owner, ExceptionListener el, ClassLoader cl) {
        return new Handler(null, owner, el, cl);
    }

    /** SAX handler that evaluates the archive element by element. */
    private static final class Handler extends DefaultHandler {
        private final XMLDecoder decoder;
        private final Object fixedOwner;
        private final ExceptionListener fixedListener;
        private final ClassLoader loader;
        final List<Object> results = new ArrayList<>();
        final Map<String, Object> ids = new HashMap<>();
        private Elem current;

        Handler(XMLDecoder decoder, Object owner, ExceptionListener el, ClassLoader cl) {
            this.decoder = decoder;
            this.fixedOwner = owner;
            this.fixedListener = el != null ? el : Statement.defaultExceptionListener;
            this.loader = cl;
        }

        Object owner() {
            if (decoder == null) {
                return fixedOwner;
            }
            return decoder.getOwner() != null ? decoder.getOwner() : decoder;
        }

        ExceptionListener listener() {
            return decoder != null ? decoder.getExceptionListener() : fixedListener;
        }

        @Override
        public void startDocument() {
            current = null;
            ids.clear();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tag = qName != null && qName.length() > 0 ? qName : localName;
            Map<String, String> attrs = new HashMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                attrs.put(name != null && name.length() > 0 ? name : attributes.getLocalName(i),
                    attributes.getValue(i));
            }
            Elem parent = current;
            current = new Elem(this, parent, tag, attrs);
            // A statement runs against its enclosing value: create that value now, so
            // ids it declares are visible to idrefs inside the statement.
            if (parent != null && "void".equals(tag)) {
                parent.getValue();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (current != null) {
                current.text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            Elem e = current;
            if (e != null) {
                current = e.parent;
                e.end();
            }
        }

        Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> primitive = Statement.primitiveType(name);
            if (primitive != null) {
                return primitive;
            }
            ClassLoader cl = loader != null ? loader : Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                try {
                    return Class.forName(name, false, cl);
                } catch (ClassNotFoundException e) {
                    // fall back below
                }
            }
            return Class.forName(name);
        }
    }

    /** One element of the archive: its arguments, text and (lazily) its value. */
    private static final class Elem {
        final Handler handler;
        final Elem parent;
        final String tag;
        final Map<String, String> attrs;
        final List<Object> args = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        private boolean evaluated;
        private Object value;

        Elem(Handler handler, Elem parent, String tag, Map<String, String> attrs) {
            this.handler = handler;
            this.parent = parent;
            this.tag = tag;
            this.attrs = attrs;
            // The short forms are the long ones with the name moved into an attribute.
            String name = attrs.remove("name");
            if (name != null) {
                if (tag.equals("method")) attrs.put("method", name);
                else if (tag.equals("property")) attrs.put("property", name);
                else if (tag.equals("field")) attrs.put("field", name);
                else attrs.put("name", name);
            }
        }

        Object getValue() {
            if (!evaluated) {
                evaluated = true;
                try {
                    value = evaluate();
                } catch (Exception e) {
                    handler.listener().exceptionThrown(e);
                    value = null;
                }
                String id = attrs.get("id");
                if (id != null) {
                    handler.ids.put(id, value);
                }
            }
            return value;
        }

        void end() {
            Object v = getValue();
            if (parent == null) {
                return;
            }
            if (tag.equals("char") && parent.tag.equals("string")) {
                if (v != null) {
                    parent.text.append(((Character) v).charValue());
                }
                return;
            }
            if (tag.equals("void")) {
                return;
            }
            if (parent.tag.equals("java")) {
                handler.results.add(v);
            } else {
                parent.args.add(v);
            }
        }

        private String trimmed() {
            return text.toString().trim();
        }

        private Object lookup(String id) {
            if (!handler.ids.containsKey(id)) {
                throw new IllegalArgumentException("Unbound variable: " + id);
            }
            return handler.ids.get(id);
        }

        private Object contextBean() {
            return parent != null ? parent.getValue() : handler.owner();
        }

        private Object evaluate() throws Exception {
            switch (tag) {
                case "java": return handler.owner();
                case "null": return null;
                case "true": return Boolean.TRUE;
                case "false": return Boolean.FALSE;
                case "string": return text.toString();
                case "boolean": return Boolean.valueOf(trimmed());
                case "byte": return Byte.decode(trimmed());
                case "short": return Short.decode(trimmed());
                case "int": return Integer.decode(trimmed());
                case "long": return Long.decode(trimmed());
                case "float": return Float.valueOf(trimmed());
                case "double": return Double.valueOf(trimmed());
                case "class": return handler.findClass(trimmed());
                case "char": {
                    String code = attrs.get("code");
                    if (code != null) {
                        return Character.valueOf((char) Integer.parseInt(
                            code.startsWith("#") ? code.substring(1) : code, code.startsWith("#") ? 16 : 10));
                    }
                    return Character.valueOf(text.charAt(0));
                }
                case "array": return array();
                case "var": return lookup(attrs.get("idref"));
                default: return invoke();
            }
        }

        private Object array() throws Exception {
            String cls = attrs.get("class");
            Class<?> component = cls != null ? handler.findClass(cls) : Object.class;
            String length = attrs.get("length");
            if (length != null) {
                return Array.newInstance(component, Integer.parseInt(length));
            }
            Object result = Array.newInstance(component, args.size());
            for (int i = 0; i < args.size(); i++) {
                Array.set(result, i, args.get(i));
            }
            return result;
        }

        private Object invoke() throws Exception {
            String cls = attrs.get("class");
            String idref = attrs.get("idref");
            String property = attrs.get("property");
            String index = attrs.get("index");
            String method = attrs.get("method");
            String field = attrs.get("field");
            if (idref != null && property == null && index == null && method == null && field == null) {
                return lookup(idref);
            }
            Object target;
            if (cls != null) {
                target = handler.findClass(cls);
            } else if (idref != null) {
                target = lookup(idref);
            } else {
                target = contextBean();
            }
            Object[] a = args.toArray();
            if (field != null) {
                boolean isStatic = target instanceof Class;
                Field f = (isStatic ? (Class<?>) target : target.getClass()).getField(field);
                if (a.length == 0) {
                    return f.get(isStatic ? null : target);
                }
                f.set(isStatic ? null : target, a[0]);
                return null;
            }
            String name;
            if (index != null) {
                Object[] withIndex = new Object[a.length + 1];
                withIndex[0] = Integer.valueOf(index);
                System.arraycopy(a, 0, withIndex, 1, a.length);
                name = a.length == 0 ? "get" : "set";
                a = withIndex;
            } else if (property != null) {
                name = (a.length == 0 ? "get" : "set") + PropertyDescriptor.capitalize(property);
            } else if (method != null) {
                name = method;
            } else if (target instanceof Class && cls != null) {
                name = "new";
            } else {
                return target;
            }
            Expression exp = new Expression(target, name, a);
            exp.loader = handler.loader;
            try {
                return exp.getValue();
            } catch (NoSuchMethodException e) {
                if (property != null && a.length == 0) {
                    Expression is = new Expression(target, "is" + PropertyDescriptor.capitalize(property), a);
                    is.loader = handler.loader;
                    return is.getValue();
                }
                throw e;
            }
        }
    }
}
