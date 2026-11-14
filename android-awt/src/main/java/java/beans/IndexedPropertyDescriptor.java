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
import java.lang.reflect.Modifier;

/**
 * An IndexedPropertyDescriptor describes a property that acts like an
 * array and has an indexed read and/or indexed write method to access
 * specific elements of the array.
 * <p>
 * An indexed property may also provide simple non-indexed read and write
 * methods.  If these are present, they read and write arrays of the type
 * returned by the indexed read method.
 */
public class IndexedPropertyDescriptor extends PropertyDescriptor {
    private Class<?> indexedPropertyType;
    private Method indexedReadMethod;
    private Method indexedWriteMethod;

    /**
     * This constructor constructs an IndexedPropertyDescriptor for a property
     * that follows the standard Java conventions by having getFoo and setFoo
     * accessor methods, for both indexed access and array access.
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass The Class object for the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Class<?> beanClass)
                throws IntrospectionException {
        this(propertyName, beanClass,
             "get" + capitalize(propertyName),
             "set" + capitalize(propertyName),
             "get" + capitalize(propertyName),
             "set" + capitalize(propertyName));
    }

    /**
     * This constructor takes the name of a simple property, and method
     * names for reading and writing the property, both indexed
     * and non-indexed.
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass  The Class object for the target bean.
     * @param readMethodName The name of the method used for reading the property
     *           values as an array.  May be null if the property is write-only
     *           or must be indexed.
     * @param writeMethodName The name of the method used for writing the property
     *           values as an array.  May be null if the property is read-only
     *           or must be indexed.
     * @param indexedReadMethodName The name of the method used for reading
     *          an indexed property value.
     *          May be null if the property is write-only.
     * @param indexedWriteMethodName The name of the method used for writing
     *          an indexed property value.
     *          May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Class<?> beanClass,
                String readMethodName, String writeMethodName,
                String indexedReadMethodName, String indexedWriteMethodName)
                throws IntrospectionException {
        super(propertyName, beanClass, readMethodName, writeMethodName);
        if (indexedReadMethodName != null) {
            Method m = findIndexed(beanClass, indexedReadMethodName, 1);
            if (m == null) {
                throw new IntrospectionException("Method not found: " + indexedReadMethodName);
            }
            setIndexedReadMethod(m);
        }
        if (indexedWriteMethodName != null) {
            Method m = findIndexed(beanClass, indexedWriteMethodName, 2);
            if (m == null) {
                throw new IntrospectionException("Method not found: " + indexedWriteMethodName);
            }
            setIndexedWriteMethod(m);
        }
        checkTypes();
    }

    /**
     * This constructor takes the name of a simple property, and Method
     * objects for reading and writing the property.
     *
     * @param propertyName The programmatic name of the property.
     * @param readMethod The method used for reading the property values as an array.
     *          May be null if the property is write-only or must be indexed.
     * @param writeMethod The method used for writing the property values as an array.
     *          May be null if the property is read-only or must be indexed.
     * @param indexedReadMethod The method used for reading an indexed property value.
     *          May be null if the property is write-only.
     * @param indexedWriteMethod The method used for writing an indexed property value.
     *          May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Method readMethod, Method writeMethod,
                                     Method indexedReadMethod, Method indexedWriteMethod)
                throws IntrospectionException {
        super(propertyName, readMethod, writeMethod);
        setIndexedReadMethod(indexedReadMethod);
        setIndexedWriteMethod(indexedWriteMethod);
        checkTypes();
    }

    /** Package-private merge used by the Introspector: {@code y} wins. */
    IndexedPropertyDescriptor(PropertyDescriptor x, IndexedPropertyDescriptor y) {
        super(x, y);
        indexedReadMethod = y.indexedReadMethod;
        indexedWriteMethod = y.indexedWriteMethod;
        indexedPropertyType = y.indexedPropertyType;
        if (x instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor ix = (IndexedPropertyDescriptor) x;
            if (indexedReadMethod == null) indexedReadMethod = ix.indexedReadMethod;
            if (indexedWriteMethod == null) indexedWriteMethod = ix.indexedWriteMethod;
            if (indexedPropertyType == null) indexedPropertyType = ix.indexedPropertyType;
        }
    }

    private static Method findIndexed(Class<?> type, String name, int args) {
        for (Method m : type.getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (m.getName().equals(name) && params.length == args && params[0] == int.class
                    && !Modifier.isStatic(m.getModifiers()) && !m.isBridge()) {
                return m;
            }
        }
        return null;
    }

    private void checkTypes() throws IntrospectionException {
        Class<?> type = getPropertyType();
        if (type != null && indexedPropertyType != null
                && (!type.isArray() || type.getComponentType() != indexedPropertyType)) {
            throw new IntrospectionException("type mismatch between indexed and non-indexed methods: "
                + getName());
        }
    }

    /**
     * Gets the method that should be used to read an indexed
     * property value.
     *
     * @return The method that should be used to read an indexed
     * property value.
     * May return null if the property isn't indexed or is write-only.
     */
    public synchronized Method getIndexedReadMethod() {
        return indexedReadMethod;
    }

    /**
     * Sets the method that should be used to read an indexed property value.
     *
     * @param readMethod The new indexed read method.
     * @throws IntrospectionException if an exception occurs during
     * introspection.
     */
    public synchronized void setIndexedReadMethod(Method readMethod) throws IntrospectionException {
        indexedPropertyType = findIndexedPropertyType(readMethod, indexedWriteMethod);
        indexedReadMethod = readMethod;
    }

    /**
     * Gets the method that should be used to write an indexed property value.
     *
     * @return The method that should be used to write an indexed
     * property value.
     * May return null if the property isn't indexed or is read-only.
     */
    public synchronized Method getIndexedWriteMethod() {
        return indexedWriteMethod;
    }

    /**
     * Sets the method that should be used to write an indexed property value.
     *
     * @param writeMethod The new indexed write method.
     * @throws IntrospectionException if an exception occurs during
     * introspection.
     */
    public synchronized void setIndexedWriteMethod(Method writeMethod) throws IntrospectionException {
        indexedPropertyType = findIndexedPropertyType(indexedReadMethod, writeMethod);
        indexedWriteMethod = writeMethod;
    }

    /**
     * Returns the Java type info for the indexed property.
     * Note that the {@code Class} object may describe
     * primitive Java types such as {@code int}.
     * This type is returned by the indexed read method
     * or is used as the parameter type of the indexed write method.
     *
     * @return the {@code Class} object that represents the Java type info,
     *         or {@code null} if the type cannot be determined
     */
    public synchronized Class<?> getIndexedPropertyType() {
        return indexedPropertyType;
    }

    private static Class<?> findIndexedPropertyType(Method indexedReadMethod, Method indexedWriteMethod)
            throws IntrospectionException {
        Class<?> type = null;
        if (indexedReadMethod != null) {
            Class<?>[] params = indexedReadMethod.getParameterTypes();
            if (params.length != 1) {
                throw new IntrospectionException("bad indexed read method arg count");
            }
            if (params[0] != Integer.TYPE) {
                throw new IntrospectionException("non int index to indexed read method");
            }
            type = indexedReadMethod.getReturnType();
            if (type == Void.TYPE) {
                throw new IntrospectionException("indexed read method returns void");
            }
        }
        if (indexedWriteMethod != null) {
            Class<?>[] params = indexedWriteMethod.getParameterTypes();
            if (params.length != 2) {
                throw new IntrospectionException("bad indexed write method arg count");
            }
            if (params[0] != Integer.TYPE) {
                throw new IntrospectionException("non int index to indexed write method");
            }
            if (type != null && !params[1].isAssignableFrom(type)) {
                throw new IntrospectionException("type mismatch between indexed read and indexed write methods");
            }
            type = params[1];
        }
        return type;
    }

    /**
     * Compares this {@code PropertyDescriptor} against the specified object.
     * Returns true if the objects are the same. Two {@code PropertyDescriptor}s
     * are the same if the read, write, property types, property editor and
     * flags  are equivalent.
     *
     * @since 1.4
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && obj instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor other = (IndexedPropertyDescriptor) obj;
            return compareMethods(getIndexedReadMethod(), other.getIndexedReadMethod())
                && compareMethods(getIndexedWriteMethod(), other.getIndexedWriteMethod())
                && getIndexedPropertyType() == other.getIndexedPropertyType()
                && super.equals(obj);
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     * @since 1.5
     */
    public int hashCode() {
        int result = super.hashCode();
        result = 37 * result + ((indexedWriteMethod == null) ? 0 : indexedWriteMethod.hashCode());
        result = 37 * result + ((indexedReadMethod == null) ? 0 : indexedReadMethod.hashCode());
        result = 37 * result + ((indexedPropertyType == null) ? 0 : indexedPropertyType.hashCode());
        return result;
    }

    void appendTo(StringBuilder sb) {
        super.appendTo(sb);
        appendTo(sb, "indexedPropertyType", indexedPropertyType);
        appendTo(sb, "indexedReadMethod", indexedReadMethod);
        appendTo(sb, "indexedWriteMethod", indexedWriteMethod);
    }
}
