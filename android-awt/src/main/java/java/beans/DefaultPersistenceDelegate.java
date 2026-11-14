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

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * The {@code DefaultPersistenceDelegate} is a concrete implementation of
 * the abstract {@code PersistenceDelegate} class and
 * is the delegate used by default for classes about
 * which no information is available. The {@code DefaultPersistenceDelegate}
 * provides, version resilient, public API-based persistence for
 * classes that follow the JavaBeans conventions without any class specific
 * configuration.
 * <p>
 * Event listeners are not persisted: the desktop delegate re-adds listeners
 * through the event set descriptors, which for every component means
 * serializing the look and feel's own anonymous handlers. Only properties are
 * written here, which is what a round trip of plain beans and Swing components
 * needs.
 *
 * @see #DefaultPersistenceDelegate(String[])
 * @since 1.4
 */
public class DefaultPersistenceDelegate extends PersistenceDelegate {
    private static final String[] EMPTY = {};
    private final String[] constructor;

    /**
     * Creates a persistence delegate for a class with a nullary constructor.
     */
    public DefaultPersistenceDelegate() {
        this.constructor = EMPTY;
    }

    /**
     * Creates a default persistence delegate for a class with a
     * constructor whose arguments are the values of the property
     * names as specified by {@code constructorPropertyNames}.
     *
     * @param  constructorPropertyNames The property names for the arguments of this constructor.
     */
    public DefaultPersistenceDelegate(String[] constructorPropertyNames) {
        this.constructor = (constructorPropertyNames == null) ? EMPTY : constructorPropertyNames.clone();
    }

    private static boolean definesEquals(Class<?> type) {
        try {
            return type == type.getMethod("equals", Object.class).getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean definesEquals(Object instance) {
        return definesEquals(instance.getClass());
    }

    /**
     * If the number of arguments in the specified constructor is non-zero and
     * the class of {@code oldInstance} explicitly declares an "equals" method
     * this method returns the value of {@code oldInstance.equals(newInstance)}.
     * Otherwise, this method uses the superclass's definition which returns true if the
     * classes of the two instances are equal.
     *
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @return True if an equivalent copy of {@code newInstance} may be
     *         created by applying a series of mutations to {@code oldInstance}.
     */
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        return (constructor.length == 0) || !definesEquals(oldInstance)
            ? super.mutatesTo(oldInstance, newInstance)
            : oldInstance.equals(newInstance);
    }

    /**
     * This default implementation of the {@code instantiate} method returns
     * an expression containing the predefined method name "new" which denotes a
     * call to a constructor with the arguments as specified in
     * the {@code DefaultPersistenceDelegate}'s constructor.
     *
     * @param oldInstance The instance to be instantiated.
     * @param out The code output stream.
     * @return An expression whose value is {@code oldInstance}.
     * @throws NullPointerException if {@code out} is {@code null}
     *                              and this value is used in the method
     */
    protected Expression instantiate(Object oldInstance, Encoder out) {
        int nArgs = constructor.length;
        Class<?> type = oldInstance.getClass();
        Object[] constructorArgs = new Object[nArgs];
        for (int i = 0; i < nArgs; i++) {
            try {
                Method method = findMethod(type, this.constructor[i]);
                constructorArgs[i] = method.invoke(oldInstance, new Object[0]);
            } catch (Exception e) {
                out.getExceptionListener().exceptionThrown(e);
            }
        }
        return new Expression(oldInstance, oldInstance.getClass(), "new", constructorArgs);
    }

    private static Method findMethod(Class<?> type, String property) throws IntrospectionException {
        if (property == null) {
            throw new IllegalArgumentException("Property name is null");
        }
        PropertyDescriptor pd = null;
        for (PropertyDescriptor d : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
            if (property.equals(d.getName())) {
                pd = d;
                break;
            }
        }
        if (pd == null) {
            throw new IllegalStateException("Could not find property by the name " + property);
        }
        Method method = pd.getReadMethod();
        if (method == null) {
            throw new IllegalStateException("Could not find getter for the property " + property);
        }
        return method;
    }

    /**
     * This default implementation of the {@code initialize} method assumes
     * all state held in objects of this type is exposed via the
     * matching pairs of "setter" and "getter" methods in the order
     * they are returned by the Introspector. If a property descriptor
     * defines a "transient" attribute with a value equal to
     * {@code Boolean.TRUE} the property is ignored by this default
     * implementation.
     *
     * @param type the type of the instances
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @param out The stream to which any initialization statements should be written.
     * @throws NullPointerException if {@code out} is {@code null}
     */
    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
        if (oldInstance.getClass() == type) {
            initBean(type, oldInstance, newInstance, out);
        }
    }

    private void initBean(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        BeanInfo info;
        try {
            info = Introspector.getBeanInfo(type);
        } catch (IntrospectionException exception) {
            out.getExceptionListener().exceptionThrown(exception);
            return;
        }
        for (PropertyDescriptor d : info.getPropertyDescriptors()) {
            if (PersistenceDelegates.isTransient(type, d)) {
                continue;
            }
            try {
                doProperty(d, oldInstance, newInstance, out);
            } catch (Exception e) {
                out.getExceptionListener().exceptionThrown(e);
            }
        }
    }

    private void doProperty(PropertyDescriptor pd, Object oldInstance, Object newInstance, Encoder out)
            throws Exception {
        Method getter = pd.getReadMethod();
        Method setter = pd.getWriteMethod();
        if (getter == null || setter == null) {
            return;
        }
        Expression oldGetExp = new Expression(oldInstance, getter.getName(), new Object[]{});
        Expression newGetExp = new Expression(newInstance, getter.getName(), new Object[]{});
        Object oldValue = oldGetExp.getValue();
        Object newValue = newGetExp.getValue();
        // Values that already compare equal need no statement; skipping them up front
        // keeps the encoder from walking (and mirroring) shared default graphs.
        if (oldValue == newValue || (oldValue != null && oldValue.getClass() == String.class
                && oldValue.equals(newValue))) {
            return;
        }
        out.writeExpression(oldGetExp);
        if (!Objects.equals(newValue, out.get(oldValue))) {
            out.writeStatement(new Statement(oldInstance, setter.getName(), new Object[]{oldValue}));
        }
    }
}
