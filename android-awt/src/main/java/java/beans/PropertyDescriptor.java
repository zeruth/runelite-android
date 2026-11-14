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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A PropertyDescriptor describes one property that a Java Bean
 * exports via a pair of accessor methods.
 */
public class PropertyDescriptor extends FeatureDescriptor {
    private Class<?> propertyType;
    private Method readMethod;
    private Method writeMethod;
    private Class<?> propertyEditorClass;
    private boolean bound;
    private boolean constrained;

    /**
     * Constructs a PropertyDescriptor for a property that follows
     * the standard Java convention by having getFoo and setFoo
     * accessor methods. Thus if the argument name is "fred", it will
     * assume that the writer method is "setFred" and the reader method
     * is "getFred" (or "isFred" for a boolean property).
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass The Class object for the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public PropertyDescriptor(String propertyName, Class<?> beanClass) throws IntrospectionException {
        this(propertyName, beanClass,
             "is" + capitalize(propertyName),
             "set" + capitalize(propertyName));
    }

    /**
     * This constructor takes the name of a simple property, and method
     * names for reading and writing the property.
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass The Class object for the target bean.
     * @param readMethodName The name of the method used for reading the property
     *           value.  May be null if the property is write-only.
     * @param writeMethodName The name of the method used for writing the property
     *           value.  May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public PropertyDescriptor(String propertyName, Class<?> beanClass,
                String readMethodName, String writeMethodName) throws IntrospectionException {
        if (beanClass == null) {
            throw new IntrospectionException("Target Bean class is null");
        }
        if (propertyName == null || propertyName.length() == 0) {
            throw new IntrospectionException("bad property name");
        }
        if ("".equals(readMethodName) || "".equals(writeMethodName)) {
            throw new IntrospectionException("read or write method name should not be the empty string");
        }
        setName(propertyName);
        if (readMethodName != null) {
            Method read = findMethod(beanClass, readMethodName, 0, null);
            if (read == null && readMethodName.startsWith("is")) {
                read = findMethod(beanClass, "get" + readMethodName.substring(2), 0, null);
            }
            if (read == null) {
                throw new IntrospectionException("Method not found: " + readMethodName);
            }
            setReadMethod(read);
        }
        if (writeMethodName != null) {
            Method write = findMethod(beanClass, writeMethodName, 1,
                readMethod != null ? readMethod.getReturnType() : null);
            if (write == null) {
                throw new IntrospectionException("Method not found: " + writeMethodName);
            }
            setWriteMethod(write);
        }
    }

    /**
     * This constructor takes the name of a simple property, and Method
     * objects for reading and writing the property.
     *
     * @param propertyName The programmatic name of the property.
     * @param readMethod The method used for reading the property value.
     *          May be null if the property is write-only.
     * @param writeMethod The method used for writing the property value.
     *          May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public PropertyDescriptor(String propertyName, Method readMethod, Method writeMethod)
                throws IntrospectionException {
        if (propertyName == null || propertyName.length() == 0) {
            throw new IntrospectionException("bad property name");
        }
        setName(propertyName);
        setReadMethod(readMethod);
        setWriteMethod(writeMethod);
    }

    /** Package-private merge used by the Introspector: {@code y} wins. */
    PropertyDescriptor(PropertyDescriptor x, PropertyDescriptor y) {
        super(x, y);
        propertyEditorClass = y.propertyEditorClass != null ? y.propertyEditorClass : x.propertyEditorClass;
        bound = x.bound | y.bound;
        constrained = x.constrained | y.constrained;
        Method xr = x.readMethod, yr = y.readMethod;
        readMethod = yr != null ? yr : xr;
        // A covariant override in the subclass keeps the subclass getter; the
        // "is" getter wins over "get" for booleans, as in the JDK.
        if (xr != null && yr != null && xr.getDeclaringClass() == yr.getDeclaringClass()
                && xr.getReturnType() == boolean.class && xr.getName().startsWith("is")) {
            readMethod = xr;
        }
        Method xw = x.writeMethod, yw = y.writeMethod;
        writeMethod = yw != null ? yw : xw;
        Class<?> type = readMethod != null ? readMethod.getReturnType() : null;
        if (type != null && !accepts(writeMethod, type)) {
            // The subclass setter takes another type: fall back to the inherited one.
            writeMethod = accepts(xw, type) ? xw : null;
        }
        propertyType = writeMethod != null ? writeMethod.getParameterTypes()[0] : type;
        if (propertyType == null) {
            propertyType = y.propertyType != null ? y.propertyType : x.propertyType;
        }
    }

    /** Package-private copy constructor. */
    PropertyDescriptor(PropertyDescriptor old) {
        super(old);
        propertyType = old.propertyType;
        readMethod = old.readMethod;
        writeMethod = old.writeMethod;
        propertyEditorClass = old.propertyEditorClass;
        bound = old.bound;
        constrained = old.constrained;
    }

    /**
     * Returns the Java type info for the property.
     *
     * @return the {@code Class} object that represents the Java type info,
     *         or {@code null} if the type cannot be determined
     */
    public synchronized Class<?> getPropertyType() {
        return propertyType;
    }

    /**
     * Gets the method that should be used to read the property value.
     *
     * @return The method that should be used to read the property value.
     * May return null if the property can't be read.
     */
    public synchronized Method getReadMethod() {
        return readMethod;
    }

    /**
     * Sets the method that should be used to read the property value.
     *
     * @param readMethod The new read method.
     * @throws IntrospectionException if the read method is invalid
     */
    public synchronized void setReadMethod(Method readMethod) throws IntrospectionException {
        propertyType = findPropertyType(readMethod, this.writeMethod);
        this.readMethod = readMethod;
    }

    /**
     * Gets the method that should be used to write the property value.
     *
     * @return The method that should be used to write the property value.
     * May return null if the property can't be written.
     */
    public synchronized Method getWriteMethod() {
        return writeMethod;
    }

    /**
     * Sets the method that should be used to write the property value.
     *
     * @param writeMethod The new write method.
     * @throws IntrospectionException if the write method is invalid
     */
    public synchronized void setWriteMethod(Method writeMethod) throws IntrospectionException {
        propertyType = findPropertyType(this.readMethod, writeMethod);
        this.writeMethod = writeMethod;
    }

    /**
     * Updates to "bound" properties will cause a "PropertyChange" event to
     * get fired when the property is changed.
     *
     * @return True if this is a bound property.
     */
    public boolean isBound() {
        return bound;
    }

    /**
     * Updates to "bound" properties will cause a "PropertyChange" event to
     * get fired when the property is changed.
     *
     * @param bound True if this is a bound property.
     */
    public void setBound(boolean bound) {
        this.bound = bound;
    }

    /**
     * Attempted updates to "Constrained" properties will cause a "VetoableChange"
     * event to get fired when the property is changed.
     *
     * @return True if this is a constrained property.
     */
    public boolean isConstrained() {
        return constrained;
    }

    /**
     * Attempted updates to "Constrained" properties will cause a "VetoableChange"
     * event to get fired when the property is changed.
     *
     * @param constrained True if this is a constrained property.
     */
    public void setConstrained(boolean constrained) {
        this.constrained = constrained;
    }

    /**
     * Normally PropertyEditors will be found using the PropertyEditorManager.
     * However if for some reason you want to associate a particular
     * PropertyEditor with a given property, then you can do it with
     * this method.
     *
     * @param propertyEditorClass  The Class for the desired PropertyEditor.
     */
    public void setPropertyEditorClass(Class<?> propertyEditorClass) {
        this.propertyEditorClass = propertyEditorClass;
    }

    /**
     * Gets any explicit PropertyEditor Class that has been registered
     * for this property.
     *
     * @return Any explicit PropertyEditor Class that has been registered
     *          for this property.  Normally this will return "null",
     *          indicating that no special editor has been registered,
     *          so the PropertyEditorManager should be used to locate
     *          a suitable PropertyEditor.
     */
    public Class<?> getPropertyEditorClass() {
        return propertyEditorClass;
    }

    /**
     * Constructs an instance of a property editor using the current
     * property editor class. If the property editor class has a public
     * constructor that takes an Object argument then it will be invoked
     * using the bean parameter as the argument. Otherwise, the default
     * constructor will be invoked.
     *
     * @param bean the source object
     * @return a property editor instance or null if a property editor has
     *         not been defined or cannot be created
     * @since 1.5
     */
    public PropertyEditor createPropertyEditor(Object bean) {
        Class<?> cls = getPropertyEditorClass();
        if (cls == null || !PropertyEditor.class.isAssignableFrom(cls)) {
            return null;
        }
        Object editor = null;
        if (bean != null) {
            try {
                Constructor<?> ctor = cls.getConstructor(Object.class);
                editor = ctor.newInstance(bean);
            } catch (Exception ex) {
                // Fall through
            }
        }
        if (editor == null) {
            try {
                editor = cls.getConstructor().newInstance();
            } catch (Exception ex) {
                // Fall through
            }
        }
        return (PropertyEditor) editor;
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
        if (obj != null && obj instanceof PropertyDescriptor) {
            PropertyDescriptor other = (PropertyDescriptor) obj;
            return compareMethods(getReadMethod(), other.getReadMethod())
                && compareMethods(getWriteMethod(), other.getWriteMethod())
                && getPropertyType() == other.getPropertyType()
                && getPropertyEditorClass() == other.getPropertyEditorClass()
                && bound == other.isBound()
                && constrained == other.isConstrained();
        }
        return false;
    }

    static boolean compareMethods(Method a, Method b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     * @since 1.5
     */
    public int hashCode() {
        int result = 7;
        result = 37 * result + ((propertyType == null) ? 0 : propertyType.hashCode());
        result = 37 * result + ((readMethod == null) ? 0 : readMethod.hashCode());
        result = 37 * result + ((writeMethod == null) ? 0 : writeMethod.hashCode());
        result = 37 * result + ((propertyEditorClass == null) ? 0 : propertyEditorClass.hashCode());
        result = 37 * result + (bound ? 1 : 0);
        result = 37 * result + (constrained ? 1 : 0);
        return result;
    }

    void appendTo(StringBuilder sb) {
        appendTo(sb, "bound", bound);
        appendTo(sb, "constrained", constrained);
        appendTo(sb, "propertyEditorClass", propertyEditorClass);
        appendTo(sb, "propertyType", propertyType);
        appendTo(sb, "readMethod", readMethod);
        appendTo(sb, "writeMethod", writeMethod);
    }

    private static Class<?> findPropertyType(Method readMethod, Method writeMethod)
            throws IntrospectionException {
        Class<?> propertyType = null;
        if (readMethod != null) {
            if (readMethod.getParameterTypes().length != 0) {
                throw new IntrospectionException("bad read method arg count: " + readMethod);
            }
            propertyType = readMethod.getReturnType();
            if (propertyType == Void.TYPE) {
                throw new IntrospectionException("read method " + readMethod.getName() + " returns void");
            }
        }
        if (writeMethod != null) {
            Class<?>[] params = writeMethod.getParameterTypes();
            if (params.length != 1) {
                throw new IntrospectionException("bad write method arg count: " + writeMethod);
            }
            if (propertyType != null && !params[0].isAssignableFrom(propertyType)) {
                throw new IntrospectionException("type mismatch between read and write methods");
            }
            propertyType = params[0];
        }
        return propertyType;
    }

    /** First public non-static method with that name and arity, matching the first parameter if given. */
    static Method findMethod(Class<?> type, String name, int args, Class<?> firstArg) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args
                    && !Modifier.isStatic(m.getModifiers()) && !m.isBridge()
                    && (firstArg == null || m.getParameterTypes()[0] == firstArg)) {
                return m;
            }
        }
        return firstArg != null ? findMethod(type, name, args, null) : null;
    }

    private static boolean accepts(Method write, Class<?> type) {
        return write != null && write.getParameterTypes()[0].isAssignableFrom(type);
    }

    static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(java.util.Locale.ENGLISH) + name.substring(1);
    }
}
