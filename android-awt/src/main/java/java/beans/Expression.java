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

/**
 * An {@code Expression} object represents a primitive expression
 * in which a single method is applied to a target and a set of
 * arguments to return a result - as in {@code "a.getFoo()"}.
 * <p>
 * In addition to the properties of the super class, the
 * {@code Expression} object provides a <em>value</em> which
 * is the object returned when this expression is evaluated.
 * The return value is typically not provided by the caller and
 * is instead computed by dynamically finding the method and invoking
 * it when the first call to {@code getValue} is made.
 *
 * @since 1.4
 */
public class Expression extends Statement {

    private static final Object unbound = new Object();

    private Object value = unbound;

    /**
     * Creates a new {@link Expression} object
     * for the specified target object to invoke the method
     * specified by the name and by the array of arguments.
     *
     * @param target      the target object of this expression
     * @param methodName  the name of the method to invoke on the specified target
     * @param arguments   the array of arguments to invoke the specified method
     */
    @ConstructorProperties({"target", "methodName", "arguments"})
    public Expression(Object target, String methodName, Object[] arguments) {
        super(target, methodName, arguments);
    }

    /**
     * Creates a new {@link Expression} object with the specified value
     * for the specified target object to invoke the  method
     * specified by the name and by the array of arguments.
     *
     * @param value       the value of this expression
     * @param target      the target object of this expression
     * @param methodName  the name of the method to invoke on the specified target
     * @param arguments   the array of arguments to invoke the specified method
     */
    public Expression(Object value, Object target, String methodName, Object[] arguments) {
        this(target, methodName, arguments);
        setValue(value);
    }

    /**
     * Evaluates the expression and stores the result as its value.
     *
     * @throws Exception that is thrown by the invoked method
     * @since 1.7
     */
    @Override
    public void execute() throws Exception {
        setValue(invoke());
    }

    /**
     * If the value property of this instance is not already set,
     * this method dynamically finds the method with the specified
     * methodName on this target with these arguments and calls it.
     * The result of the method invocation is first copied
     * into the value property of this expression and then returned
     * as the result of {@code getValue}.
     *
     * @return The result of applying this method to these arguments.
     * @throws Exception if the method with the specified methodName
     * throws an exception
     */
    public Object getValue() throws Exception {
        if (value == unbound) {
            setValue(invoke());
        }
        return value;
    }

    /**
     * Sets the value of this expression to {@code value}.
     * This value will be returned by the getValue method
     * without calling the method associated with this
     * expression.
     *
     * @param value The value of this expression.
     */
    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * Prints the value of this expression using a Java-style syntax.
     */
    public String toString() {
        return (value == unbound ? "<unbound>" : instanceName(value)) + "=" + super.toString();
    }
}
