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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * An {@code Encoder} is a class which can be used to create
 * files or streams that encode the state of a collection of
 * JavaBeans in terms of their public APIs. The {@code Encoder},
 * in conjunction with its persistence delegates, is responsible for
 * breaking the object graph down into a series of {@code Statements}s
 * and {@code Expression}s which can be used to create it.
 * <p>
 * As on desktop, the encoder keeps a mirror of the graph: every expression
 * written is replayed on copies of its arguments, so a delegate can compare
 * the old instance against what the statements so far would rebuild and emit
 * only the differences.
 *
 * @see XMLEncoder
 * @since 1.4
 */
public class Encoder {
    private final Map<Object, Expression> bindings = new IdentityHashMap<>();
    private final Map<Class<?>, PersistenceDelegate> delegates = new HashMap<>();
    private ExceptionListener exceptionListener;
    boolean executeStatements = true;

    /**
     * Creates a new encoder.
     */
    public Encoder() {
    }

    /**
     * Write the specified object to the output stream.
     * The serialized form will denote a series of
     * expressions, the combined effect of which will create
     * an equivalent object when the input stream is read.
     *
     * @param o The object to be written to the stream.
     * @see XMLDecoder#readObject
     */
    protected void writeObject(Object o) {
        if (o == this) {
            return;
        }
        PersistenceDelegate info = getPersistenceDelegate(o == null ? null : o.getClass());
        info.writeObject(o, this);
    }

    /**
     * Sets the exception handler for this stream to {@code exceptionListener}.
     * The exception handler is notified when this stream catches recoverable
     * exceptions.
     *
     * @param exceptionListener The exception handler for this stream;
     *       if {@code null} the default exception listener will be used.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    /**
     * Gets the exception handler for this stream.
     *
     * @return The exception handler for this stream;
     *    Will return the default exception listener if this has not explicitly been set.
     */
    public ExceptionListener getExceptionListener() {
        return (exceptionListener != null) ? exceptionListener : Statement.defaultExceptionListener;
    }

    Object getValue(Expression exp) {
        try {
            return (exp == null) ? null : exp.getValue();
        } catch (Exception e) {
            getExceptionListener().exceptionThrown(e);
            throw new RuntimeException("failed to evaluate: " + exp.toString());
        }
    }

    /**
     * Returns the persistence delegate for the given type. A delegate set
     * with {@link #setPersistenceDelegate} wins; otherwise the built-in
     * delegates of the JDK apply (null, primitive wrappers, String, Class,
     * enums, arrays, collections, maps, the AWT value types), then a
     * {@code "persistenceDelegate"} attribute of the type's bean descriptor,
     * then a constructor annotated with {@link ConstructorProperties}, then
     * {@link DefaultPersistenceDelegate}.
     *
     * @param type  the class of the objects
     * @return the persistence delegate for the given type
     */
    public PersistenceDelegate getPersistenceDelegate(Class<?> type) {
        PersistenceDelegate pd;
        synchronized (delegates) {
            pd = delegates.get(type);
        }
        return pd != null ? pd : PersistenceDelegates.getPersistenceDelegate(type);
    }

    /**
     * Associates the specified persistence delegate with the given type.
     *
     * @param type        the class of objects that the specified persistence delegate applies to
     * @param delegate    the persistence delegate for instances of the given type
     * @since 1.4
     */
    public void setPersistenceDelegate(Class<?> type, PersistenceDelegate delegate) {
        synchronized (delegates) {
            if (delegate == null) {
                delegates.remove(type);
            } else {
                delegates.put(type, delegate);
            }
        }
    }

    /**
     * Removes the entry for this instance, returning the old entry.
     *
     * @param oldInstance The entry that should be removed.
     * @return The entry that was removed.
     * @see #get
     */
    public Object remove(Object oldInstance) {
        Expression exp = bindings.remove(oldInstance);
        return getValue(exp);
    }

    /**
     * Returns a tentative value for {@code oldInstance} in
     * the environment created by this stream. A persistence
     * delegate can use its {@code mutatesTo} method to
     * determine whether this value may be initialized to
     * form the equivalent object at the output or whether
     * a new object must be instantiated afresh.
     *
     * @param  oldInstance the instance to be looked up.
     * @return The object, null if the object has not been seen before.
     */
    public Object get(Object oldInstance) {
        if (oldInstance == null || oldInstance == this || oldInstance.getClass() == String.class) {
            return oldInstance;
        }
        Expression exp = bindings.get(oldInstance);
        return getValue(exp);
    }

    private Object writeObject1(Object oldInstance) {
        Object o = get(oldInstance);
        if (o == null) {
            writeObject(oldInstance);
            o = get(oldInstance);
        }
        return o;
    }

    private Statement cloneStatement(Statement oldExp) {
        Object oldTarget = oldExp.getTarget();
        Object newTarget = writeObject1(oldTarget);
        Object[] oldArgs = oldExp.getArguments();
        Object[] newArgs = new Object[oldArgs.length];
        for (int i = 0; i < oldArgs.length; i++) {
            newArgs[i] = writeObject1(oldArgs[i]);
        }
        Statement newExp = Statement.class.equals(oldExp.getClass())
            ? new Statement(newTarget, oldExp.getMethodName(), newArgs)
            : new Expression(newTarget, oldExp.getMethodName(), newArgs);
        newExp.loader = oldExp.loader;
        return newExp;
    }

    /**
     * Writes statement {@code oldStm} to the stream.
     * The {@code oldStm} should be written entirely
     * in terms of the callers environment, i.e. the
     * target and all arguments should be part of the
     * object graph being written. These expressions
     * represent a series of "what happened" expressions
     * which tell the output stream how to produce an
     * object graph like the original.
     *
     * @param oldStm The expression to be written to the stream.
     */
    public void writeStatement(Statement oldStm) {
        Statement newStm = cloneStatement(oldStm);
        if (oldStm.getTarget() != this && executeStatements) {
            try {
                newStm.execute();
            } catch (Exception e) {
                getExceptionListener().exceptionThrown(
                    new Exception("Encoder: discarding statement " + newStm, e));
            }
        }
    }

    /**
     * The implementation first checks to see if an
     * expression with this value has already been written.
     * If not, the expression is cloned, using
     * the same procedure as {@code writeStatement},
     * and the value of this expression is reconciled
     * with the value of the cloned expression
     * by calling {@code writeObject}.
     *
     * @param oldExp The expression to be written to the stream.
     */
    public void writeExpression(Expression oldExp) {
        Object oldValue = getValue(oldExp);
        if (get(oldValue) != null) {
            return;
        }
        bindings.put(oldValue, (Expression) cloneStatement(oldExp));
        writeObject(oldValue);
    }

    void clear() {
        bindings.clear();
    }
}
