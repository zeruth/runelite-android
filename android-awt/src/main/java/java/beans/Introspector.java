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

import java.awt.Image;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * The Introspector class provides a standard way for tools to learn about
 * the properties, events, and methods supported by a target Java Bean.
 * <p>
 * For each of those three kinds of information, the Introspector will
 * separately analyze the bean's class and superclasses looking for
 * either explicit or implicit information and use that information to
 * build a BeanInfo object that comprehensively describes the target bean.
 * <p>
 * The JDK keeps this analysis in {@code com.sun.beans.introspect}; here it is
 * inlined: each class level contributes the public methods it declares (and the
 * default methods of interfaces it introduces), an explicit {@code <Bean>BeanInfo}
 * replaces the reflective analysis of its level, and the levels are merged from
 * the root down so that a subclass accessor wins over the inherited one. The
 * JDK 9 {@link BeanProperty}, {@link JavaBean} and {@link Transient}
 * annotations are honoured.
 *
 * @since 1.1
 */
public class Introspector {

    /** Flag to indicate to use of all beaninfo. */
    public final static int USE_ALL_BEANINFO           = 1;
    /** Flag to indicate to ignore immediate beaninfo. */
    public final static int IGNORE_IMMEDIATE_BEANINFO  = 2;
    /** Flag to indicate to ignore all beaninfo. */
    public final static int IGNORE_ALL_BEANINFO        = 3;

    // Soft values: a BeanInfo holds Methods, which pin the key Class strongly.
    private static final Map<Class<?>, SoftReference<BeanInfo>> CACHE = new WeakHashMap<>();
    private static String[] searchPath = {"sun.beans.infos"};

    private Introspector() {
    }

    /**
     * Introspect on a Java Bean and learn about all its properties, exposed
     * methods, and events.
     * <p>
     * If the BeanInfo class for a Java Bean has been previously Introspected
     * then the BeanInfo class is retrieved from the BeanInfo cache.
     *
     * @param beanClass  The bean class to be analyzed.
     * @return  A BeanInfo object describing the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
        synchronized (CACHE) {
            SoftReference<BeanInfo> ref = CACHE.get(beanClass);
            BeanInfo cached = ref != null ? ref.get() : null;
            if (cached != null) {
                return cached;
            }
        }
        BeanInfo info = getBeanInfo(beanClass, null, USE_ALL_BEANINFO);
        synchronized (CACHE) {
            CACHE.put(beanClass, new SoftReference<>(info));
        }
        return info;
    }

    /**
     * Introspect on a Java bean and learn about all its properties, exposed
     * methods, and events, subject to some control flags.
     *
     * @param beanClass  The bean class to be analyzed.
     * @param flags  Flags to control the introspection.
     * @return  A BeanInfo object describing the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     * @since 1.2
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, int flags) throws IntrospectionException {
        return getBeanInfo(beanClass, null, flags);
    }

    /**
     * Introspect on a Java bean and learn all about its properties, exposed
     * methods, below a given "stop" point.
     *
     * @param beanClass The bean class to be analyzed.
     * @param stopClass The baseclass at which to stop the analysis.  Any
     *    methods/properties/events in the stopClass or in its baseclasses
     *    will be ignored in the analysis.
     * @return  A BeanInfo object describing the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, Class<?> stopClass)
            throws IntrospectionException {
        return getBeanInfo(beanClass, stopClass, USE_ALL_BEANINFO);
    }

    /**
     * Introspect on a Java Bean and learn about all its properties,
     * exposed methods and events, below a given {@code stopClass} point
     * subject to some control {@code flags}.
     *
     * @param beanClass  the bean class to be analyzed
     * @param stopClass  the parent class at which to stop the analysis
     * @param flags      flags to control the introspection
     * @return a BeanInfo object describing the target bean
     * @exception IntrospectionException if an exception occurs during introspection
     * @since 1.7
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, Class<?> stopClass, int flags)
            throws IntrospectionException {
        if (stopClass != null && !stopClass.isAssignableFrom(beanClass)) {
            throw new IntrospectionException(stopClass.getName() + " not superclass of "
                + beanClass.getName());
        }
        return introspect(beanClass, stopClass, flags).toBeanInfo(beanClass);
    }

    /**
     * Utility method to take a string and convert it to normal Java variable
     * name capitalization.  This normally means converting the first
     * character from upper case to lower case, but in the (unusual) special
     * case when there is more than one character and both the first and
     * second characters are upper case, we leave it alone.
     * <p>
     * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays
     * as "URL".
     *
     * @param  name The string to be decapitalized.
     * @return  The decapitalized version of the string.
     */
    public static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))
                && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Gets the list of package names that will be used for
     * finding BeanInfo classes.
     *
     * @return  The array of package names that will be searched in
     *          order to find BeanInfo classes. The default value
     *          for this array is implementation-dependent.
     */
    public static synchronized String[] getBeanInfoSearchPath() {
        return searchPath.clone();
    }

    /**
     * Change the list of package names that will be used for
     * finding BeanInfo classes.  The behaviour of
     * this method is undefined if parameter path
     * is null.
     *
     * @param path  Array of package names.
     */
    public static synchronized void setBeanInfoSearchPath(String[] path) {
        searchPath = path == null ? new String[0] : path.clone();
    }

    /**
     * Flush all of the Introspector's internal caches.  This method is
     * not normally required.  It is normally only needed by advanced
     * tools that update existing "Class" objects in-place and need
     * to make the Introspector re-analyze existing Class objects.
     *
     * @since 1.2
     */
    public static void flushCaches() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /**
     * Flush the Introspector's internal cached information for a given class.
     *
     * @param clz  Class object to be flushed.
     * @throws NullPointerException If the Class object is null.
     * @since 1.2
     */
    public static void flushFromCaches(Class<?> clz) {
        if (clz == null) {
            throw new NullPointerException();
        }
        synchronized (CACHE) {
            CACHE.remove(clz);
        }
    }

    // ------------------------------------------------------------------ analysis

    private static Level introspect(Class<?> type, Class<?> stop, int flags) {
        BeanInfo explicit = flags == USE_ALL_BEANINFO ? findExplicitBeanInfo(type) : null;
        Class<?> sup = type.getSuperclass();
        Level level = sup != null && sup != stop
            ? introspect(sup, stop, flags == IGNORE_IMMEDIATE_BEANINFO ? USE_ALL_BEANINFO : flags)
            : new Level();
        if (explicit != null) {
            BeanInfo[] extra = explicit.getAdditionalBeanInfo();
            if (extra != null) {
                for (BeanInfo info : extra) {
                    level.addExplicit(info);
                }
            }
        }
        List<Method> methods = levelMethods(type, sup);
        EventSetDescriptor[] events = explicit != null ? explicit.getEventSetDescriptors() : null;
        if (events != null) {
            for (EventSetDescriptor e : events) level.addEvent(e);
        } else {
            addEvents(level, methods);
        }
        PropertyDescriptor[] props = explicit != null ? explicit.getPropertyDescriptors() : null;
        if (props != null) {
            for (PropertyDescriptor p : props) level.addProperty(p);
        } else {
            addProperties(level, methods, level.events.containsKey("propertyChange"));
        }
        MethodDescriptor[] mds = explicit != null ? explicit.getMethodDescriptors() : null;
        if (mds != null) {
            for (MethodDescriptor m : mds) level.addMethod(m);
        } else {
            for (Method m : methods) level.addMethod(new MethodDescriptor(m));
        }
        // The bean descriptor belongs to the class asked about; the defaults are
        // inherited until a level names its own.
        level.beanDescriptor = explicit != null ? explicit.getBeanDescriptor() : null;
        level.explicit = explicit;
        level.javaBean = explicit == null ? type.getAnnotation(JavaBean.class) : null;
        String defaultProperty = null;
        String defaultEvent = null;
        if (explicit != null) {
            defaultProperty = nameAt(props, explicit.getDefaultPropertyIndex());
            defaultEvent = nameAt(events, explicit.getDefaultEventIndex());
        } else if (level.javaBean != null) {
            defaultProperty = level.javaBean.defaultProperty().isEmpty() ? null : level.javaBean.defaultProperty();
            defaultEvent = level.javaBean.defaultEventSet().isEmpty() ? null : level.javaBean.defaultEventSet();
        }
        if (defaultProperty != null) level.defaultProperty = defaultProperty;
        if (defaultEvent != null) level.defaultEvent = defaultEvent;
        return level;
    }

    private static String nameAt(FeatureDescriptor[] array, int index) {
        return array != null && index >= 0 && index < array.length ? array[index].getName() : null;
    }

    /** The public instance methods this class level contributes. */
    private static List<Method> levelMethods(Class<?> type, Class<?> sup) {
        List<Method> result = new ArrayList<>();
        Method[] all;
        try {
            all = type.getMethods();
        } catch (LinkageError e) {
            return result;
        }
        for (Method m : all) {
            if (Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) {
                continue;
            }
            Class<?> declaring = m.getDeclaringClass();
            if (declaring == type
                    || (declaring.isInterface() && (sup == null || !declaring.isAssignableFrom(sup)))) {
                result.add(m);
            }
        }
        // getMethods() order is unspecified; sort so ties resolve the same way every run.
        java.util.Collections.sort(result, new java.util.Comparator<Method>() {
            public int compare(Method a, Method b) {
                return a.toString().compareTo(b.toString());
            }
        });
        return result;
    }

    private static void addEvents(Level level, List<Method> methods) {
        Map<String, Method> adds = new HashMap<>();
        Map<String, Method> removes = new HashMap<>();
        Map<String, Method> gets = new HashMap<>();
        for (Method m : methods) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && m.getReturnType() == void.class
                    && EventListener.class.isAssignableFrom(params[0])) {
                if (name.startsWith("add")) adds.put(name.substring(3), m);
                else if (name.startsWith("remove")) removes.put(name.substring(6), m);
            } else if (params.length == 0 && name.startsWith("get") && name.endsWith("s")
                    && m.getReturnType().isArray()) {
                gets.put(name.substring(3, name.length() - 1), m);
            }
        }
        for (Map.Entry<String, Method> e : adds.entrySet()) {
            String listenerName = e.getKey();
            Method add = e.getValue();
            Method remove = removes.get(listenerName);
            Class<?> listenerType = add.getParameterTypes()[0];
            if (remove == null || remove.getParameterTypes()[0] != listenerType
                    || !listenerName.endsWith("Listener") || listenerName.length() == 8) {
                continue;
            }
            Method get = gets.get(listenerName);
            if (get != null && get.getReturnType().getComponentType() != listenerType) {
                get = null;
            }
            List<Method> handlers = new ArrayList<>();
            for (Method lm : listenerType.getMethods()) {
                Class<?>[] lp = lm.getParameterTypes();
                if (lp.length == 1 && EventObject.class.isAssignableFrom(lp[0])
                        && !Modifier.isStatic(lm.getModifiers())) {
                    handlers.add(lm);
                }
            }
            try {
                EventSetDescriptor esd = new EventSetDescriptor(
                    decapitalize(listenerName.substring(0, listenerName.length() - 8)),
                    listenerType, handlers.toArray(new Method[0]), add, remove, get);
                level.addEvent(esd);
            } catch (IntrospectionException ignored) {
                // Not an event set after all.
            }
        }
    }

    private static void addProperties(Level level, List<Method> methods, boolean bound) {
        Map<String, Method> getters = new HashMap<>();
        Map<String, Method> isGetters = new HashMap<>();
        Map<String, List<Method>> setters = new HashMap<>();
        Map<String, Method> indexedGetters = new HashMap<>();
        Map<String, List<Method>> indexedSetters = new HashMap<>();
        TreeSet<String> bases = new TreeSet<>();
        for (Method m : methods) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();
            Class<?> ret = m.getReturnType();
            if (name.startsWith("get") && name.length() > 3 && ret != void.class) {
                if (params.length == 0) {
                    getters.put(name.substring(3), m);
                } else if (params.length == 1 && params[0] == int.class) {
                    indexedGetters.put(name.substring(3), m);
                } else {
                    continue;
                }
                bases.add(name.substring(3));
            } else if (name.startsWith("is") && name.length() > 2 && params.length == 0
                    && ret == boolean.class) {
                isGetters.put(name.substring(2), m);
                bases.add(name.substring(2));
            } else if (name.startsWith("set") && name.length() > 3 && ret == void.class) {
                if (params.length == 1) {
                    listFor(setters, name.substring(3)).add(m);
                } else if (params.length == 2 && params[0] == int.class) {
                    listFor(indexedSetters, name.substring(3)).add(m);
                } else {
                    continue;
                }
                bases.add(name.substring(3));
            }
        }
        for (String base : bases) {
            Method read = getters.get(base);
            Method is = isGetters.get(base);
            if (is != null && (read == null || read.getReturnType() == boolean.class)) {
                read = is;
            }
            Class<?> type = read != null ? read.getReturnType() : null;
            Method write = pickSetter(setters.get(base), type, 0);
            if (type == null && write != null) {
                type = write.getParameterTypes()[0];
            }
            Method iread = indexedGetters.get(base);
            Class<?> itype = iread != null ? iread.getReturnType() : null;
            Method iwrite = pickSetter(indexedSetters.get(base), itype, 1);
            if (itype == null && iwrite != null) {
                itype = iwrite.getParameterTypes()[1];
            }
            if (itype != null && type != null && !(type.isArray() && type.getComponentType() == itype)) {
                // getFoo() and getFoo(int) disagree: the plain accessors win, as in the JDK.
                iread = null;
                iwrite = null;
                itype = null;
            }
            PropertyDescriptor pd;
            try {
                String name = decapitalize(base);
                pd = itype != null
                    ? new IndexedPropertyDescriptor(name, read, write, iread, iwrite)
                    : new PropertyDescriptor(name, read, write);
            } catch (IntrospectionException e) {
                continue;
            }
            Method annotated = read != null ? read : (iread != null ? iread : (write != null ? write : iwrite));
            BeanProperty bp = annotation(BeanProperty.class, read, iread, write, iwrite);
            pd.setBound(bound && (bp == null || bp.bound()));
            pd.setConstrained(throwsVeto(write) || throwsVeto(iwrite));
            if (bp != null) {
                pd.setExpert(bp.expert());
                pd.setHidden(bp.hidden());
                pd.setPreferred(bp.preferred());
                pd.setValue("required", bp.required());
                pd.setValue("visualUpdate", bp.visualUpdate());
                if (!bp.description().isEmpty()) {
                    pd.setShortDescription(bp.description());
                }
                if (bp.enumerationValues().length > 0) {
                    Object[] values = enumerationValues(annotated.getDeclaringClass(), bp.enumerationValues());
                    if (values != null) {
                        pd.setValue("enumerationValues", values);
                    }
                }
            }
            Transient t = annotation(Transient.class, read, iread, write, iwrite);
            if (t != null) {
                pd.setValue("transient", t.value());
            }
            level.addProperty(pd);
        }
    }

    private static List<Method> listFor(Map<String, List<Method>> map, String key) {
        List<Method> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        return list;
    }

    /** The setter whose value parameter is the getter type; with no getter, the first one. */
    private static Method pickSetter(List<Method> candidates, Class<?> type, int valueIndex) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (type == null) {
            return candidates.get(0);
        }
        for (Method m : candidates) {
            if (m.getParameterTypes()[valueIndex] == type) return m;
        }
        for (Method m : candidates) {
            if (m.getParameterTypes()[valueIndex].isAssignableFrom(type)) return m;
        }
        return null;
    }

    private static boolean throwsVeto(Method m) {
        if (m != null) {
            for (Class<?> ex : m.getExceptionTypes()) {
                if (PropertyVetoException.class.isAssignableFrom(ex)) return true;
            }
        }
        return false;
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(Class<A> type, Method... methods) {
        for (Method m : methods) {
            A a = m != null ? m.getAnnotation(type) : null;
            if (a != null) return a;
        }
        return null;
    }

    /** {@code {name, value, javaInitializationString}} triples, as the JDK builds them. */
    private static Object[] enumerationValues(Class<?> owner, String[] names) {
        List<Object> values = new ArrayList<>();
        for (String full : names) {
            int dot = full.lastIndexOf('.');
            String fieldName = full.substring(dot + 1);
            Class<?> holder = owner;
            if (dot > 0) {
                String cls = full.substring(0, dot);
                try {
                    holder = Class.forName(cls, false, owner.getClassLoader());
                } catch (ClassNotFoundException e) {
                    holder = owner;
                }
            }
            try {
                Field f = holder.getField(fieldName);
                values.add(fieldName);
                values.add(f.get(null));
                values.add(full);
            } catch (Exception e) {
                return null;
            }
        }
        return values.toArray();
    }

    private static BeanInfo findExplicitBeanInfo(Class<?> type) {
        String name = type.getName() + "BeanInfo";
        BeanInfo info = instantiateBeanInfo(type, name);
        if (info != null) {
            return info;
        }
        String simple = name.substring(name.lastIndexOf('.') + 1);
        for (String path : getBeanInfoSearchPath()) {
            info = instantiateBeanInfo(type, path + '.' + simple);
            if (info != null) {
                BeanDescriptor bd = info.getBeanDescriptor();
                if (bd == null || bd.getBeanClass() == type) {
                    return info;
                }
            }
        }
        return null;
    }

    private static BeanInfo instantiateBeanInfo(Class<?> type, String name) {
        Object o = instantiate(type, name);
        return o instanceof BeanInfo ? (BeanInfo) o : null;
    }

    private static Object instantiate(Class<?> sibling, String name) {
        ClassLoader loader = sibling.getClassLoader();
        try {
            Class<?> c = Class.forName(name, true,
                loader != null ? loader : ClassLoader.getSystemClassLoader());
            return c.getConstructor().newInstance();
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static Class<?> findCustomizerClass(Class<?> type) {
        String name = type.getName() + "Customizer";
        ClassLoader loader = type.getClassLoader();
        try {
            Class<?> c = Class.forName(name, false,
                loader != null ? loader : ClassLoader.getSystemClassLoader());
            if (java.awt.Component.class.isAssignableFrom(c) && Customizer.class.isAssignableFrom(c)) {
                return c;
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    /** The descriptors accumulated from the root class down to one level. */
    private static final class Level {
        final Map<String, PropertyDescriptor> properties = new TreeMap<>();
        final Map<String, EventSetDescriptor> events = new TreeMap<>();
        final Map<String, MethodDescriptor> methods = new LinkedHashMap<>();
        BeanDescriptor beanDescriptor;
        BeanInfo explicit;
        JavaBean javaBean;
        String defaultProperty;
        String defaultEvent;

        void addExplicit(BeanInfo info) {
            EventSetDescriptor[] e = info.getEventSetDescriptors();
            if (e != null) for (EventSetDescriptor d : e) addEvent(d);
            PropertyDescriptor[] p = info.getPropertyDescriptors();
            if (p != null) for (PropertyDescriptor d : p) addProperty(d);
            MethodDescriptor[] m = info.getMethodDescriptors();
            if (m != null) for (MethodDescriptor d : m) addMethod(d);
        }

        void addProperty(PropertyDescriptor pd) {
            if (pd == null || pd.getName() == null) {
                return;
            }
            PropertyDescriptor old = properties.get(pd.getName());
            if (old == null) {
                properties.put(pd.getName(), pd);
            } else if (pd instanceof IndexedPropertyDescriptor) {
                properties.put(pd.getName(), new IndexedPropertyDescriptor(old, (IndexedPropertyDescriptor) pd));
            } else {
                properties.put(pd.getName(), new PropertyDescriptor(old, pd));
            }
        }

        void addEvent(EventSetDescriptor esd) {
            if (esd == null || esd.getName() == null) {
                return;
            }
            EventSetDescriptor old = events.get(esd.getName());
            events.put(esd.getName(), old == null ? esd : new EventSetDescriptor(old, esd));
        }

        void addMethod(MethodDescriptor md) {
            Method m = md != null ? md.getMethod() : null;
            if (m == null) {
                return;
            }
            methods.put(m.getName() + Arrays.toString(m.getParameterTypes()), md);
        }

        BeanInfo toBeanInfo(Class<?> beanClass) {
            BeanDescriptor bd = beanDescriptor != null ? beanDescriptor
                : new BeanDescriptor(beanClass, findCustomizerClass(beanClass));
            if (javaBean != null && !javaBean.description().isEmpty()) {
                bd.setShortDescription(javaBean.description());
            }
            PropertyDescriptor[] props = properties.values().toArray(new PropertyDescriptor[0]);
            EventSetDescriptor[] evts = events.values().toArray(new EventSetDescriptor[0]);
            MethodDescriptor[] mds = methods.values().toArray(new MethodDescriptor[0]);
            return new GenericBeanInfo(bd, evts, indexOf(evts, defaultEvent),
                props, indexOf(props, defaultProperty), mds, explicit);
        }

        private static int indexOf(FeatureDescriptor[] array, String name) {
            if (name != null) {
                for (int i = 0; i < array.length; i++) {
                    if (name.equals(array[i].getName())) return i;
                }
            }
            return -1;
        }
    }

    /** The BeanInfo the Introspector hands out; icons come from the explicit BeanInfo, if any. */
    private static final class GenericBeanInfo extends SimpleBeanInfo {
        private final BeanDescriptor beanDescriptor;
        private final EventSetDescriptor[] events;
        private final int defaultEvent;
        private final PropertyDescriptor[] properties;
        private final int defaultProperty;
        private final MethodDescriptor[] methods;
        private final BeanInfo target;

        GenericBeanInfo(BeanDescriptor beanDescriptor, EventSetDescriptor[] events, int defaultEvent,
                        PropertyDescriptor[] properties, int defaultProperty,
                        MethodDescriptor[] methods, BeanInfo target) {
            this.beanDescriptor = beanDescriptor;
            this.events = events;
            this.defaultEvent = defaultEvent;
            this.properties = properties;
            this.defaultProperty = defaultProperty;
            this.methods = methods;
            this.target = target;
        }

        public PropertyDescriptor[] getPropertyDescriptors() {
            return properties.clone();
        }

        public int getDefaultPropertyIndex() {
            return defaultProperty;
        }

        public EventSetDescriptor[] getEventSetDescriptors() {
            return events.clone();
        }

        public int getDefaultEventIndex() {
            return defaultEvent;
        }

        public MethodDescriptor[] getMethodDescriptors() {
            return methods.clone();
        }

        public BeanDescriptor getBeanDescriptor() {
            return beanDescriptor;
        }

        public Image getIcon(int iconKind) {
            return target != null ? target.getIcon(iconKind) : super.getIcon(iconKind);
        }
    }
}
