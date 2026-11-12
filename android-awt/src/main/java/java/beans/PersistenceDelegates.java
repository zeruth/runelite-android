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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The built-in persistence delegates the {@link Encoder} falls back to, the
 * counterpart of the JDK's package-private {@code java.beans.MetaData}: null,
 * the primitive wrappers, strings, classes, enums, arrays, collections and
 * maps, the AWT value types, {@link ConstructorProperties} constructors, a
 * {@code "persistenceDelegate"} attribute of the bean descriptor, and
 * {@link DefaultPersistenceDelegate} for everything else. Named apart from
 * the JDK class so the host contracts never mix the two.
 */
final class PersistenceDelegates {
    private PersistenceDelegates() {
    }

    private static final PersistenceDelegate NULL = new NullPersistenceDelegate();
    private static final PersistenceDelegate PRIMITIVE = new PrimitivePersistenceDelegate();
    private static final PersistenceDelegate STRING = new StringPersistenceDelegate();
    private static final PersistenceDelegate CLASS = new ClassPersistenceDelegate();
    private static final PersistenceDelegate ENUM = new EnumPersistenceDelegate();
    private static final PersistenceDelegate ARRAY = new ArrayPersistenceDelegate();
    private static final PersistenceDelegate COLLECTION = new CollectionPersistenceDelegate();
    private static final PersistenceDelegate MAP = new MapPersistenceDelegate();
    private static final PersistenceDelegate DEFAULT = new DefaultPersistenceDelegate();
    private static final Map<Class<?>, PersistenceDelegate> CACHE = new WeakHashMap<>();

    /**
     * Properties the JDK's MetaData removes from the default bean persistence:
     * they are views of state that other properties or constructor arguments
     * already carry (a Rectangle's bounds, a Component's size triplet).
     */
    private static final Set<String> TRANSIENT = new HashSet<>(Arrays.asList(
        "java.awt.geom.RectangularShape.frame",
        "java.awt.Rectangle.bounds",
        "java.awt.Dimension.size",
        "java.awt.Point.location",
        "java.awt.Component.foreground",
        "java.awt.Component.background",
        "java.awt.Component.font",
        "java.awt.Component.visible",
        "java.awt.ScrollPane.scrollPosition",
        "java.awt.im.InputContext.compositionEnabled",
        "javax.swing.JComponent.minimumSize",
        "javax.swing.JComponent.preferredSize",
        "javax.swing.JComponent.maximumSize",
        "javax.swing.ImageIcon.image",
        "javax.swing.ImageIcon.imageObserver",
        "javax.swing.JMenuBar.helpMenu",
        "javax.swing.JScrollBar.maximum",
        "javax.swing.JScrollBar.minimum"));

    static boolean isTransient(Class<?> type, PropertyDescriptor pd) {
        if (Boolean.TRUE.equals(pd.getValue("transient"))) {
            return true;
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (TRANSIENT.contains(c.getName() + '.' + pd.getName())) {
                return true;
            }
        }
        return false;
    }

    static PersistenceDelegate getPersistenceDelegate(Class<?> type) {
        if (type == null) {
            return NULL;
        }
        if (Enum.class.isAssignableFrom(type)) {
            return ENUM;
        }
        if (primitiveTypeFor(type) != null) {
            return PRIMITIVE;
        }
        if (type == String.class) {
            return STRING;
        }
        if (type == Class.class) {
            return CLASS;
        }
        if (type.isArray()) {
            return ARRAY;
        }
        synchronized (CACHE) {
            PersistenceDelegate cached = CACHE.get(type);
            if (cached != null) {
                return cached;
            }
        }
        PersistenceDelegate pd = find(type);
        synchronized (CACHE) {
            CACHE.put(type, pd);
        }
        return pd;
    }

    private static PersistenceDelegate find(Class<?> type) {
        switch (type.getName()) {
            case "java.awt.Point":
            case "java.awt.Dimension":
            case "java.awt.Rectangle":
            case "java.awt.Insets":
                return new FieldConstructorPersistenceDelegate(fieldsOf(type.getName()));
            case "java.awt.Color":
                return new DefaultPersistenceDelegate(new String[] {"red", "green", "blue", "alpha"});
            case "java.awt.Font":
                return new DefaultPersistenceDelegate(new String[] {"name", "style", "size"});
            default:
                break;
        }
        try {
            BeanDescriptor bd = Introspector.getBeanInfo(type).getBeanDescriptor();
            Object pd = bd != null ? bd.getValue("persistenceDelegate") : null;
            if (pd instanceof PersistenceDelegate) {
                return (PersistenceDelegate) pd;
            }
        } catch (IntrospectionException ignored) {
            // fall through to the defaults
        }
        if (hasNullaryConstructor(type)) {
            if (Collection.class.isAssignableFrom(type)) return COLLECTION;
            if (Map.class.isAssignableFrom(type)) return MAP;
        }
        String[] names = null;
        for (Constructor<?> c : type.getConstructors()) {
            ConstructorProperties cp = c.getAnnotation(ConstructorProperties.class);
            if (cp != null && (names == null || cp.value().length > names.length)) {
                names = cp.value();
            }
        }
        return names != null ? new DefaultPersistenceDelegate(names) : DEFAULT;
    }

    private static String[] fieldsOf(String awtType) {
        switch (awtType) {
            case "java.awt.Point": return new String[] {"x", "y"};
            case "java.awt.Dimension": return new String[] {"width", "height"};
            case "java.awt.Rectangle": return new String[] {"x", "y", "width", "height"};
            default: return new String[] {"top", "left", "bottom", "right"};
        }
    }

    private static boolean hasNullaryConstructor(Class<?> type) {
        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static Class<?> primitiveTypeFor(Class<?> wrapper) {
        if (wrapper == Boolean.class) return Boolean.TYPE;
        if (wrapper == Byte.class) return Byte.TYPE;
        if (wrapper == Character.class) return Character.TYPE;
        if (wrapper == Short.class) return Short.TYPE;
        if (wrapper == Integer.class) return Integer.TYPE;
        if (wrapper == Long.class) return Long.TYPE;
        if (wrapper == Float.class) return Float.TYPE;
        if (wrapper == Double.class) return Double.TYPE;
        return null;
    }

    static final class NullPersistenceDelegate extends PersistenceDelegate {
        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }

        protected Expression instantiate(Object oldInstance, Encoder out) {
            return null;
        }

        public void writeObject(Object oldInstance, Encoder out) {
        }
    }

    static final class PrimitivePersistenceDelegate extends PersistenceDelegate {
        protected boolean mutatesTo(Object oldInstance, Object newInstance) {
            return oldInstance.equals(newInstance);
        }

        // The argument is the string form: strings are their own mirror, so the clone of
        // this expression never needs the value it is creating.
        protected Expression instantiate(Object oldInstance, Encoder out) {
            return new Expression(oldInstance, oldInstance.getClass(), "new",
                new Object[] {oldInstance.toString()});
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }
    }

    static final class StringPersistenceDelegate extends PersistenceDelegate {
        protected Expression instantiate(Object oldInstance, Encoder out) {
            return new Expression(oldInstance, String.class, "new",
                new Object[] {((String) oldInstance).toCharArray()});
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }
    }

    static final class ClassPersistenceDelegate extends PersistenceDelegate {
        protected boolean mutatesTo(Object oldInstance, Object newInstance) {
            return oldInstance == newInstance;
        }

        // String and Class are reached without forName so the mirror of Class.class,
        // the target of every forName, does not need itself.
        protected Expression instantiate(Object oldInstance, Encoder out) {
            Class<?> c = (Class<?>) oldInstance;
            if (c == String.class) {
                return new Expression(oldInstance, "", "getClass", new Object[]{});
            }
            if (c == Class.class) {
                return new Expression(oldInstance, String.class, "getClass", new Object[]{});
            }
            Expression e = new Expression(oldInstance, Class.class, "forName", new Object[] {c.getName()});
            e.loader = c.getClassLoader();
            return e;
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }
    }

    static final class EnumPersistenceDelegate extends PersistenceDelegate {
        protected boolean mutatesTo(Object oldInstance, Object newInstance) {
            return oldInstance == newInstance;
        }

        protected Expression instantiate(Object oldInstance, Encoder out) {
            Enum<?> e = (Enum<?>) oldInstance;
            return new Expression(e, Enum.class, "valueOf", new Object[] {e.getDeclaringClass(), e.name()});
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }
    }

    static final class ArrayPersistenceDelegate extends PersistenceDelegate {
        protected boolean mutatesTo(Object oldInstance, Object newInstance) {
            return newInstance != null && oldInstance.getClass() == newInstance.getClass()
                && Array.getLength(oldInstance) == Array.getLength(newInstance);
        }

        protected Expression instantiate(Object oldInstance, Encoder out) {
            Class<?> oldClass = oldInstance.getClass();
            return new Expression(oldInstance, Array.class, "newInstance",
                new Object[] {oldClass.getComponentType(), Integer.valueOf(Array.getLength(oldInstance))});
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
            int n = Array.getLength(oldInstance);
            for (int i = 0; i < n; i++) {
                Object index = Integer.valueOf(i);
                Expression oldGetExp = new Expression(oldInstance, "get", new Object[] {index});
                Expression newGetExp = new Expression(newInstance, "get", new Object[] {index});
                try {
                    Object oldValue = oldGetExp.getValue();
                    Object newValue = newGetExp.getValue();
                    out.writeExpression(oldGetExp);
                    if (!Objects.equals(newValue, out.get(oldValue))) {
                        out.writeStatement(new Statement(oldInstance, "set", new Object[] {index, oldValue}));
                    }
                } catch (Exception e) {
                    out.getExceptionListener().exceptionThrown(e);
                }
            }
        }
    }

    static final class CollectionPersistenceDelegate extends DefaultPersistenceDelegate {
        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
            Collection<?> oldO = (Collection<?>) oldInstance;
            Collection<?> newO = (Collection<?>) newInstance;
            if (newO.size() != 0) {
                out.writeStatement(new Statement(oldInstance, "clear", new Object[]{}));
            }
            for (Iterator<?> i = oldO.iterator(); i.hasNext();) {
                out.writeStatement(new Statement(oldInstance, "add", new Object[] {i.next()}));
            }
        }
    }

    static final class MapPersistenceDelegate extends DefaultPersistenceDelegate {
        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
            Map<?, ?> oldMap = (Map<?, ?>) oldInstance;
            Map<?, ?> newMap = (Map<?, ?>) newInstance;
            if (newMap.size() != 0) {
                out.writeStatement(new Statement(oldInstance, "clear", new Object[]{}));
            }
            for (Map.Entry<?, ?> e : oldMap.entrySet()) {
                out.writeStatement(new Statement(oldInstance, "put", new Object[] {e.getKey(), e.getValue()}));
            }
        }
    }

    /** The AWT value types: constructed from their public int fields. */
    static final class FieldConstructorPersistenceDelegate extends PersistenceDelegate {
        private final String[] fields;

        FieldConstructorPersistenceDelegate(String[] fields) {
            this.fields = fields;
        }

        protected boolean mutatesTo(Object oldInstance, Object newInstance) {
            return oldInstance.equals(newInstance);
        }

        protected Expression instantiate(Object oldInstance, Encoder out) {
            Object[] args = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                try {
                    args[i] = oldInstance.getClass().getField(fields[i]).get(oldInstance);
                } catch (Exception e) {
                    out.getExceptionListener().exceptionThrown(e);
                }
            }
            return new Expression(oldInstance, oldInstance.getClass(), "new", args);
        }

        protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        }
    }
}
