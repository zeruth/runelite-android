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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code XMLEncoder} class is a complementary alternative to
 * the {@code ObjectOutputStream} and can used to generate
 * a textual representation of a <em>JavaBean</em> in the same
 * way that the {@code ObjectOutputStream} can
 * be used to create binary representation of {@code Serializable}
 * objects. The output is the JDK's archive format: a {@code <java>}
 * root holding {@code <object>}/{@code <void>} elements with
 * {@code class}, {@code id}/{@code idref}, {@code property},
 * {@code index} and {@code method} attributes, one space of
 * indentation per level.
 *
 * @see XMLDecoder
 * @since 1.4
 */
public class XMLEncoder extends Encoder implements AutoCloseable {

    private final CharsetEncoder encoder;
    private final String charset;
    private final boolean declaration;
    private final Writer out;
    private Object owner;
    private int indentation;
    private boolean internal;
    private boolean preambleWritten;
    private final Map<Object, ValueData> valueToExpression = new IdentityHashMap<>();
    private final Map<Object, List<Statement>> targetToStatementList = new IdentityHashMap<>();
    private final Map<String, Integer> nameToCount = new HashMap<>();

    private static final class ValueData {
        int refs;
        boolean marked;
        String name;
        Expression exp;
    }

    /**
     * Creates a new XML encoder to write out <em>JavaBeans</em>
     * to the stream {@code out} using an XML encoding.
     *
     * @param out  the stream to which the XML representation of
     *             the objects will be written
     * @throws IllegalArgumentException if {@code out} is {@code null}
     */
    public XMLEncoder(OutputStream out) {
        this(out, "UTF-8", true, 0);
    }

    /**
     * Creates a new XML encoder to write out <em>JavaBeans</em>
     * to the stream {@code out} using the given {@code charset}
     * starting from the given {@code indentation}.
     *
     * @param out          the stream to which the XML representation of
     *                     the objects will be written
     * @param charset      the name of the requested charset;
     *                     may be either a canonical name or an alias
     * @param declaration  whether the XML declaration should be generated;
     *                     set this to {@code false}
     *                     when embedding the contents in another XML document
     * @param indentation  the number of space characters to indent the entire XML document by
     * @throws IllegalArgumentException if {@code out} or {@code charset} is {@code null},
     *                                  or if {@code indentation} is less than 0
     * @since 1.7
     */
    public XMLEncoder(OutputStream out, String charset, boolean declaration, int indentation) {
        if (out == null) {
            throw new IllegalArgumentException("the output stream cannot be null");
        }
        if (indentation < 0) {
            throw new IllegalArgumentException("the indentation must be >= 0");
        }
        Charset cs = Charset.forName(charset);
        this.encoder = cs.newEncoder();
        this.charset = charset;
        this.declaration = declaration;
        this.indentation = indentation;
        this.out = new OutputStreamWriter(out, cs.newEncoder());
    }

    /**
     * Sets the owner of this encoder to {@code owner}.
     *
     * @param owner The owner of this encoder.
     */
    public void setOwner(Object owner) {
        this.owner = owner;
        writeExpression(new Expression(this, "getOwner", new Object[0]));
    }

    /**
     * Gets the owner of this encoder.
     *
     * @return The owner of this encoder.
     */
    public Object getOwner() {
        return owner;
    }

    /**
     * Write an XML representation of the specified object to the output.
     *
     * @param o The object to be written to the stream.
     */
    public void writeObject(Object o) {
        if (internal) {
            super.writeObject(o);
        } else {
            writeStatement(new Statement(this, "writeObject", new Object[] {o}));
        }
    }

    private List<Statement> statementList(Object target) {
        List<Statement> list = targetToStatementList.get(target);
        if (list == null) {
            list = new ArrayList<>();
            targetToStatementList.put(target, list);
        }
        return list;
    }

    private void mark(Object o, boolean isArgument) {
        if (o == null || o == this) {
            return;
        }
        ValueData d = getValueData(o);
        Expression exp = d.exp;
        // Literal strings are written in place; only marked when they came from an expression.
        if (o.getClass() == String.class && exp == null) {
            return;
        }
        if (isArgument) {
            d.refs++;
        }
        if (d.marked || exp == null) {
            return;
        }
        d.marked = true;
        Object target = exp.getTarget();
        mark(exp);
        if (!(target instanceof Class)) {
            statementList(target).add(exp);
            d.refs++;
        }
    }

    private void mark(Statement stm) {
        Object[] args = stm.getArguments();
        for (Object arg : args) {
            mark(arg, true);
        }
        mark(stm.getTarget(), stm instanceof Expression);
    }

    /**
     * Records the Statement so that the Encoder will
     * produce the actual output when the stream is flushed.
     *
     * @param oldStm The statement that will be written
     *               to the stream.
     */
    public void writeStatement(Statement oldStm) {
        boolean internal = this.internal;
        this.internal = true;
        try {
            super.writeStatement(oldStm);
            mark(oldStm);
            statementList(oldStm.getTarget()).add(oldStm);
        } catch (Exception e) {
            getExceptionListener().exceptionThrown(new Exception("XMLEncoder: discarding statement " + oldStm, e));
        }
        this.internal = internal;
    }

    /**
     * Records the Expression so that the Encoder will
     * produce the actual output when the stream is flushed.
     *
     * @param oldExp The expression that will be written
     *               to the stream.
     */
    public void writeExpression(Expression oldExp) {
        boolean oldInternal = internal;
        internal = true;
        Object oldValue = getValue(oldExp);
        if (get(oldValue) == null || (oldValue instanceof String && !oldInternal)) {
            getValueData(oldValue).exp = oldExp;
            super.writeExpression(oldExp);
        }
        internal = oldInternal;
    }

    /**
     * This method writes out the preamble associated with the
     * XML encoding if it has not been written already and
     * then writes out all of the values that been
     * written to the stream since the last time {@code flush}
     * was called. After flushing, all internal references to the
     * values that were written to this stream are cleared.
     */
    public void flush() {
        if (!preambleWritten) {
            if (declaration) {
                writeln("<?xml version=" + quote("1.0") + " encoding=" + quote(charset) + "?>");
            }
            writeln("<java version=" + quote(String.valueOf(System.getProperty("java.version")))
                + " class=" + quote(XMLDecoder.class.getName()) + ">");
            preambleWritten = true;
        }
        indentation++;
        List<Statement> statements = statementList(this);
        while (!statements.isEmpty()) {
            Statement s = statements.remove(0);
            if ("writeObject".equals(s.getMethodName())) {
                outputValue(s.getArguments()[0], this, true);
            } else {
                outputStatement(s, this, false);
            }
        }
        Statement statement = getMissedStatement();
        while (statement != null) {
            outputStatement(statement, this, false);
            statement = getMissedStatement();
        }
        indentation--;
        try {
            out.flush();
        } catch (IOException e) {
            getExceptionListener().exceptionThrown(e);
        }
        clear();
    }

    void clear() {
        super.clear();
        valueToExpression.clear();
        targetToStatementList.clear();
        nameToCount.clear();
    }

    private Statement getMissedStatement() {
        for (List<Statement> statements : targetToStatementList.values()) {
            for (int i = 0; i < statements.size(); i++) {
                if (Statement.class == statements.get(i).getClass()) {
                    return statements.remove(i);
                }
            }
        }
        return null;
    }

    /**
     * This method calls {@code flush}, writes the closing
     * postamble and then closes the output stream associated
     * with this stream.
     */
    public void close() {
        flush();
        writeln("</java>");
        try {
            out.close();
        } catch (IOException e) {
            getExceptionListener().exceptionThrown(e);
        }
    }

    private String quote(String s) {
        return "\"" + s + "\"";
    }

    private ValueData getValueData(Object o) {
        ValueData d = valueToExpression.get(o);
        if (d == null) {
            d = new ValueData();
            valueToExpression.put(o, d);
        }
        return d;
    }

    private String instanceName(Object instance) {
        Class<?> type = instance.getClass();
        String className = Statement.unqualifiedClassName(type);
        Integer size = nameToCount.get(className);
        int instanceNumber = (size == null) ? 0 : size.intValue() + 1;
        nameToCount.put(className, Integer.valueOf(instanceNumber));
        return className + instanceNumber;
    }

    private static boolean isValidCharCode(int code) {
        return (0x0020 <= code && code <= 0xD7FF)
            || (0x000A == code) || (0x0009 == code) || (0x000D == code)
            || (0xE000 <= code && code <= 0xFFFD)
            || (0x10000 <= code && code <= 0x10ffff);
    }

    private void writeln(String exp) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < indentation; i++) {
                sb.append(' ');
            }
            sb.append(exp);
            sb.append('\n');
            out.write(sb.toString());
        } catch (IOException e) {
            getExceptionListener().exceptionThrown(e);
        }
    }

    private void outputValue(Object value, Object outer, boolean isArgument) {
        if (value == null) {
            writeln("<null/>");
            return;
        }
        if (value instanceof Class) {
            writeln("<class>" + ((Class<?>) value).getName() + "</class>");
            return;
        }
        ValueData d = getValueData(value);
        if (d.exp != null) {
            Object target = d.exp.getTarget();
            String methodName = d.exp.getMethodName();
            if (target == null || methodName == null) {
                throw new NullPointerException((target == null ? "target" : "methodName")
                    + " should not be null");
            }
            Class<?> primitiveType = PersistenceDelegates.primitiveTypeFor(value.getClass());
            if (primitiveType != null && target == value.getClass() && methodName.equals("new")) {
                String primitiveTypeName = primitiveType.getName();
                if (primitiveType == Character.TYPE) {
                    char code = ((Character) value).charValue();
                    if (!isValidCharCode(code)) {
                        writeln(createString(code));
                        return;
                    }
                    value = quoteCharCode(code);
                    if (value == null) {
                        value = Character.valueOf(code);
                    }
                }
                writeln("<" + primitiveTypeName + ">" + value + "</" + primitiveTypeName + ">");
                return;
            }
        } else if (value instanceof String) {
            writeln(createString((String) value));
            return;
        }
        if (d.name != null) {
            if (isArgument) {
                writeln("<object idref=" + quote(d.name) + "/>");
            } else {
                outputXML("void", " idref=" + quote(d.name), value);
            }
        } else if (d.exp != null) {
            outputStatement(d.exp, outer, isArgument);
        }
    }

    private static String quoteCharCode(int code) {
        switch (code) {
            case '&': return "&amp;";
            case '<': return "&lt;";
            case '>': return "&gt;";
            case '"': return "&quot;";
            case '\'': return "&apos;";
            case '\r': return "&#13;";
            default: return null;
        }
    }

    private static String createString(int code) {
        return "<char code=\"#" + Integer.toString(code, 16) + "\"/>";
    }

    private String createString(String string) {
        StringBuilder sb = new StringBuilder();
        sb.append("<string>");
        int index = 0;
        while (index < string.length()) {
            int point = string.codePointAt(index);
            int count = Character.charCount(point);
            if (isValidCharCode(point) && encoder.canEncode(string.substring(index, index + count))) {
                String value = quoteCharCode(point);
                if (value != null) {
                    sb.append(value);
                } else {
                    sb.appendCodePoint(point);
                }
                index += count;
            } else {
                sb.append(createString(string.charAt(index)));
                index++;
            }
        }
        sb.append("</string>");
        return sb.toString();
    }

    private void outputStatement(Statement exp, Object outer, boolean isArgument) {
        Object target = exp.getTarget();
        String methodName = exp.getMethodName();
        if (target == null || methodName == null) {
            throw new NullPointerException((target == null ? "target" : "methodName")
                + " should not be null");
        }
        Object[] args = exp.getArguments();
        boolean expression = exp.getClass() == Expression.class;
        Object value = (expression) ? getValue((Expression) exp) : null;

        String tag = (expression && isArgument) ? "object" : "void";
        String attributes = "";
        ValueData d = getValueData(value);

        // Special cases for targets.
        if (target == outer) {
            // the enclosing element's value
        } else if (target == Array.class && methodName.equals("newInstance")) {
            tag = "array";
            attributes = attributes + " class=" + quote(((Class<?>) args[0]).getName());
            attributes = attributes + " length=" + quote(args[1].toString());
            args = new Object[]{};
        } else if (target.getClass() == Class.class) {
            attributes = attributes + " class=" + quote(((Class<?>) target).getName());
        } else {
            d.refs = 2;
            if (d.name == null) {
                getValueData(target).refs++;
                List<Statement> statements = statementList(target);
                if (!statements.contains(exp)) {
                    statements.add(exp);
                }
                outputValue(target, outer, false);
            }
            if (expression) {
                outputValue(value, outer, isArgument);
            }
            return;
        }
        if (expression && (d.refs > 1)) {
            String instanceName = instanceName(value);
            d.name = instanceName;
            attributes = attributes + " id=" + quote(instanceName);
        }

        // Special cases for methods.
        if ((!expression && methodName.equals("set") && args.length == 2 && args[0] instanceof Integer)
                || (expression && methodName.equals("get") && args.length == 1 && args[0] instanceof Integer)) {
            attributes = attributes + " index=" + quote(args[0].toString());
            args = (args.length == 1) ? new Object[]{} : new Object[] {args[1]};
        } else if ((!expression && methodName.startsWith("set") && args.length == 1)
                || (expression && methodName.startsWith("get") && args.length == 0)) {
            if (3 < methodName.length()) {
                attributes = attributes + " property="
                    + quote(Introspector.decapitalize(methodName.substring(3)));
            }
        } else if (!methodName.equals("new") && !methodName.equals("newInstance")) {
            attributes = attributes + " method=" + quote(methodName);
        }
        outputXML(tag, attributes, value, args);
    }

    private void outputXML(String tag, String attributes, Object value, Object... args) {
        List<Statement> statements = statementList(value);
        // Use XML's short form when there is no body.
        if (args.length == 0 && statements.size() == 0) {
            writeln("<" + tag + attributes + "/>");
            return;
        }
        writeln("<" + tag + attributes + ">");
        indentation++;
        for (Object arg : args) {
            outputValue(arg, null, true);
        }
        while (!statements.isEmpty()) {
            Statement s = statements.remove(0);
            outputStatement(s, value, false);
        }
        indentation--;
        writeln("</" + tag + ">");
    }
}
