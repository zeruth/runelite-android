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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A {@code Statement} object represents a primitive statement
 * in which a single method is applied to a target and
 * a set of arguments - as in {@code "a.setFoo(b)"}.
 * <p>
 * Resolution follows the JDK: a {@code Class} target with the method name
 * {@code "new"} calls the most specific public constructor; any other name on
 * a {@code Class} target is a static method of that class, falling back to a
 * method of {@code Class} itself; {@code "get"}/{@code "set"} on an array
 * target index the array; {@code Class.forName} resolves primitive names and
 * uses the statement's class loader.
 *
 * @since 1.4
 */
public class Statement {

    private static final Object[] emptyArray = new Object[]{};

    static ExceptionListener defaultExceptionListener = new ExceptionListener() {
        public void exceptionThrown(Exception e) {
            System.err.println(e);
            System.err.println("Continuing ...");
        }
    };

    private final Object target;
    private final String methodName;
    private final Object[] arguments;
    ClassLoader loader;

    /**
     * Creates a new {@link Statement} object
     * for the specified target object to invoke the method
     * specified by the name and by the array of arguments.
     *
     * @param target      the target object of this statement
     * @param methodName  the name of the method to invoke on the specified target
     * @param arguments   the array of arguments to invoke the specified method
     */
    @ConstructorProperties({"target", "methodName", "arguments"})
    public Statement(Object target, String methodName, Object[] arguments) {
        this.target = target;
        this.methodName = methodName;
        this.arguments = (arguments == null) ? emptyArray : arguments.clone();
    }

    /**
     * Returns the target object of this statement.
     *
     * @return the target object of this statement
     */
    public Object getTarget() {
        return target;
    }

    /**
     * Returns the name of the method to invoke.
     *
     * @return the name of the method
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns the arguments for the method to invoke.
     *
     * @return the array of arguments
     */
    public Object[] getArguments() {
        return this.arguments.clone();
    }

    /**
     * The {@code execute} method finds a method whose name is the same
     * as the {@code methodName} property, and invokes the method on
     * the target.
     *
     * @throws NullPointerException if the value of the {@code target} or
     *                              {@code methodName} property is {@code null}
     * @throws NoSuchMethodException if a matching method is not found
     * @throws Exception that is thrown by the invoked method
     */
    public void execute() throws Exception {
        invoke();
    }

    Object invoke() throws Exception {
        Object target = getTarget();
        String methodName = getMethodName();
        if (target == null || methodName == null) {
            throw new NullPointerException((target == null ? "target" : "methodName")
                + " should not be null");
        }
        Object[] arguments = this.arguments;
        Class<?>[] argClasses = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            argClasses[i] = (arguments[i] == null) ? null : arguments[i].getClass();
        }
        try {
            if (target == Class.class && methodName.equals("forName")) {
                return forName((String) arguments[0], loader);
            }
            if (target instanceof Class) {
                Class<?> type = (Class<?>) target;
                if (methodName.equals("new")) {
                    methodName = "newInstance";
                }
                // There is no Character(String); the encoder writes characters that way.
                if (type == Character.class && methodName.equals("newInstance")
                        && arguments.length == 1 && argClasses[0] == String.class) {
                    return Character.valueOf(((String) arguments[0]).charAt(0));
                }
                if (methodName.equals("newInstance") && type.isArray()) {
                    Object result = Array.newInstance(type.getComponentType(), arguments.length);
                    for (int i = 0; i < arguments.length; i++) {
                        Array.set(result, i, arguments[i]);
                    }
                    return result;
                }
                if (methodName.equals("newInstance")) {
                    Constructor<?> ctor = findConstructor(type, argClasses);
                    if (ctor != null) {
                        return ctor.newInstance(arguments);
                    }
                    if (arguments.length != 0) {
                        throw new NoSuchMethodException(toString());
                    }
                }
                Method m = findMethod(type, methodName, argClasses, true);
                if (m == null) {
                    m = findMethod(Class.class, methodName, argClasses, false);
                }
                if (m == null) {
                    throw new NoSuchMethodException(toString());
                }
                return m.invoke(Modifier.isStatic(m.getModifiers()) ? null : target, arguments);
            }
            if (target.getClass().isArray() && (methodName.equals("set") || methodName.equals("get"))) {
                int index = ((Integer) arguments[0]).intValue();
                if (methodName.equals("get")) {
                    return Array.get(target, index);
                }
                Array.set(target, index, arguments[1]);
                return null;
            }
            Method m = findMethod(target.getClass(), methodName, argClasses, false);
            if (m == null) {
                throw new NoSuchMethodException(toString());
            }
            return m.invoke(target, arguments);
        } catch (InvocationTargetException ite) {
            Throwable te = ite.getTargetException();
            if (te instanceof Exception) {
                throw (Exception) te;
            }
            throw ite;
        }
    }

    private static Class<?> forName(String name, ClassLoader loader) throws ClassNotFoundException {
        Class<?> primitive = primitiveType(name);
        if (primitive != null) {
            return primitive;
        }
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader != null) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | SecurityException e) {
                // fall back to this class's loader
            }
        }
        return Class.forName(name);
    }

    static Class<?> primitiveType(String name) {
        switch (name) {
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            case "void": return void.class;
            default: return null;
        }
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return Void.class;
    }

    private static boolean applicable(Class<?>[] params, Class<?>[] args) {
        if (params.length != args.length) {
            return false;
        }
        for (int i = 0; i < params.length; i++) {
            if (args[i] == null ? params[i].isPrimitive() : !box(params[i]).isAssignableFrom(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean moreSpecific(Class<?>[] a, Class<?>[] b) {
        for (int i = 0; i < a.length; i++) {
            if (!box(b[i]).isAssignableFrom(box(a[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Constructor<?> findConstructor(Class<?> type, Class<?>[] args) {
        Constructor<?> best = null;
        for (Constructor<?> c : type.getConstructors()) {
            if (applicable(c.getParameterTypes(), args)
                    && (best == null || moreSpecific(c.getParameterTypes(), best.getParameterTypes()))) {
                best = c;
            }
        }
        return best;
    }

    static Method findMethod(Class<?> type, String name, Class<?>[] args, boolean wantStatic) {
        Method best = null;
        for (Method m : type.getMethods()) {
            // getMethods() also lists the covariant override, so bridges only add ambiguity.
            if (!m.getName().equals(name) || m.isBridge()
                    || (wantStatic && !Modifier.isStatic(m.getModifiers()))
                    || !applicable(m.getParameterTypes(), args)) {
                continue;
            }
            if (best == null || moreSpecific(m.getParameterTypes(), best.getParameterTypes())) {
                best = m;
            }
        }
        return best == null ? null : accessible(best);
    }

    /**
     * A public method declared by a non-public class (an anonymous listener,
     * a package-private implementation) is reached through the public type that
     * declares it, as the JDK's MethodUtil does.
     */
    private static Method accessible(Method m) {
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
            return m;
        }
        if (!Modifier.isStatic(m.getModifiers())) {
            Method found = publicDeclaration(m.getDeclaringClass(), m);
            if (found != null) {
                return found;
            }
        }
        try {
            m.setAccessible(true);
        } catch (RuntimeException ignored) {
            // invoke reports the IllegalAccessException
        }
        return m;
    }

    private static Method publicDeclaration(Class<?> type, Method m) {
        if (type == null) {
            return null;
        }
        if (Modifier.isPublic(type.getModifiers())) {
            try {
                return type.getMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // not declared here
            }
        }
        for (Class<?> i : type.getInterfaces()) {
            Method found = publicDeclaration(i, m);
            if (found != null) {
                return found;
            }
        }
        return publicDeclaration(type.getSuperclass(), m);
    }

    /**
     * Returns a string representation of this object
     * in the form of Java source code.
     *
     * @return the string representation of this object
     */
    public String toString() {
        StringBuilder result = new StringBuilder(instanceName(target) + "." + methodName + "(");
        int n = arguments.length;
        for (int i = 0; i < n; i++) {
            result.append(instanceName(arguments[i]));
            if (i != n - 1) {
                result.append(", ");
            }
        }
        result.append(");");
        return result.toString();
    }

    static String instanceName(Object instance) {
        if (instance == null) {
            return "null";
        } else if (instance.getClass() == String.class) {
            return "\"" + instance + "\"";
        } else if (instance instanceof Class) {
            return unqualifiedClassName((Class<?>) instance);
        } else if (instance instanceof Number || instance instanceof Boolean
                || instance instanceof Character) {
            return instance.toString();
        }
        return unqualifiedClassName(instance.getClass())
            + Integer.toHexString(System.identityHashCode(instance));
    }

    static String unqualifiedClassName(Class<?> type) {
        if (type == null) {
            return "null";
        }
        if (type.isArray()) {
            return unqualifiedClassName(type.getComponentType()) + "Array";
        }
        String name = type.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
