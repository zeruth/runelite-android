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
package javax.management;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates in-memory {@link MBeanServer}s. A bean is compliant when it implements a public interface
 * whose name ends in {@code MBean} or {@code MXBean} (the Standard MBean and MXBean conventions);
 * its attributes are that interface's getters and setters and its operations the interface methods.
 * Values are passed through as they are: there is no open-type mapping to {@code CompositeData},
 * and an exception thrown by a bean reaches the caller unwrapped rather than as an
 * {@code MBeanException}. The server registers no {@code MBeanServerDelegate}.
 */
public class MBeanServerFactory {
    private static final ArrayList<MBeanServer> SERVERS = new ArrayList<>();

    private MBeanServerFactory() {}

    public static MBeanServer createMBeanServer() {
        return createMBeanServer(null);
    }

    public static MBeanServer createMBeanServer(String domain) {
        MBeanServer server = newMBeanServer(domain);
        synchronized (SERVERS) {
            SERVERS.add(server);
        }
        return server;
    }

    public static MBeanServer newMBeanServer() {
        return newMBeanServer(null);
    }

    public static MBeanServer newMBeanServer(String domain) {
        return new Registry(domain == null ? "DefaultDomain" : domain);
    }

    public static void releaseMBeanServer(MBeanServer mbeanServer) {
        synchronized (SERVERS) {
            if (!SERVERS.remove(mbeanServer)) throw new IllegalArgumentException("MBeanServer was not in list!");
        }
    }

    /** The servers made by {@code createMBeanServer}; {@code agentId} null selects all of them. */
    public static ArrayList<MBeanServer> findMBeanServer(String agentId) {
        synchronized (SERVERS) {
            ArrayList<MBeanServer> found = new ArrayList<>();
            for (MBeanServer server : SERVERS) {
                if (agentId == null || (server instanceof Registry && ((Registry) server).agentId.equals(agentId))) found.add(server);
            }
            return found;
        }
    }

    private static final class Registry implements MBeanServer {
        private static int created;
        private final ConcurrentMap<ObjectName, Object> beans = new ConcurrentHashMap<>();
        private final String defaultDomain;
        final String agentId;

        Registry(String defaultDomain) {
            this.defaultDomain = defaultDomain;
            synchronized (Registry.class) {
                agentId = "android_" + System.currentTimeMillis() + "_" + (created++);
            }
        }

        /** A name without a domain lives in the default domain, as on the JDK. */
        private ObjectName resolve(ObjectName name) {
            if (name == null || !name.getDomain().isEmpty()) return name;
            try {
                return new ObjectName(defaultDomain + name.toString());
            } catch (MalformedObjectNameException impossible) {
                return name;
            }
        }

        private Object bean(ObjectName name) throws InstanceNotFoundException {
            Object bean = name == null ? null : beans.get(resolve(name));
            if (bean == null) throw new InstanceNotFoundException(String.valueOf(name));
            return bean;
        }

        @Override public ObjectInstance registerMBean(Object obj, ObjectName name) throws Exception {
            if (obj == null) throw new IllegalArgumentException("Object cannot be null");
            if (name == null) throw new IllegalArgumentException("No object name specified");
            name = resolve(name);
            if (name.isPattern()) throw new IllegalArgumentException("Invalid name->" + name);
            if (interfaces(obj.getClass()).isEmpty()) {
                throw new NotCompliantMBeanException("MBean class " + obj.getClass().getName()
                        + " does not implement a public *MBean or *MXBean interface");
            }
            if (beans.putIfAbsent(name, obj) != null) throw new InstanceAlreadyExistsException(name.toString());
            return new ObjectInstance(name, obj.getClass().getName());
        }

        @Override public void unregisterMBean(ObjectName name) throws Exception {
            if (name == null || beans.remove(resolve(name)) == null) throw new InstanceNotFoundException(String.valueOf(name));
        }

        @Override public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException {
            return new ObjectInstance(resolve(name), bean(name).getClass().getName());
        }

        @Override public boolean isRegistered(ObjectName name) {
            return name != null && beans.containsKey(resolve(name));
        }

        @Override public Object getAttribute(ObjectName name, String attribute) throws Exception {
            Object bean = bean(name);
            Method getter = getter(bean, attribute);
            if (getter == null) throw new AttributeNotFoundException("No such attribute: " + attribute);
            return call(getter, bean);
        }

        @Override public AttributeList getAttributes(ObjectName name, String[] attributes) throws Exception {
            Object bean = bean(name);
            AttributeList found = new AttributeList();
            for (String attribute : attributes) {
                Method getter = getter(bean, attribute);
                if (getter == null) continue;
                try {
                    found.add(new Attribute(attribute, call(getter, bean)));
                } catch (Exception unreadable) {
                    // getAttributes leaves out what it could not read instead of failing the whole list.
                }
            }
            return found;
        }

        @Override public void setAttribute(ObjectName name, Attribute attribute) throws Exception {
            Object bean = bean(name);
            String setter = "set" + attribute.getName();
            boolean exists = false;
            for (Class<?> type : interfaces(bean.getClass())) {
                for (Method method : type.getMethods()) {
                    if (!method.getName().equals(setter) || method.getParameterTypes().length != 1) continue;
                    exists = true;
                    if (!accepts(method.getParameterTypes()[0], attribute.getValue())) continue;
                    call(method, bean, new Object[] {attribute.getValue()});
                    return;
                }
            }
            if (exists) throw new IllegalArgumentException("Wrong value type for attribute " + attribute.getName());
            throw new AttributeNotFoundException("No such attribute: " + attribute.getName());
        }

        @Override public AttributeList setAttributes(ObjectName name, AttributeList attributes) throws Exception {
            bean(name);
            AttributeList written = new AttributeList();
            for (Attribute attribute : attributes.asList()) {
                try {
                    setAttribute(name, attribute);
                    written.add(attribute);
                } catch (Exception rejected) {
                    // setAttributes reports the attributes it could write; the rest are left out.
                }
            }
            return written;
        }

        @Override public Object invoke(ObjectName name, String op, Object[] params, String[] signature) throws Exception {
            Object bean = bean(name);
            String[] names = signature == null ? new String[0] : signature;
            ClassLoader loader = bean.getClass().getClassLoader();
            Class<?>[] types = new Class<?>[names.length];
            for (int i = 0; i < names.length; i++) types[i] = parameterType(names[i], loader);
            for (Class<?> type : interfaces(bean.getClass())) {
                Method method;
                try {
                    method = type.getMethod(op, types);
                } catch (NoSuchMethodException elsewhere) {
                    continue;
                }
                return call(method, bean, params == null ? new Object[0] : params);
            }
            throw new NoSuchMethodException(op);
        }

        @Override public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
            Set<ObjectName> found = new HashSet<>();
            if (query != null) query.setMBeanServer(this);
            ObjectName pattern = resolve(name);
            for (ObjectName candidate : beans.keySet()) {
                try {
                    if ((pattern == null || pattern.apply(candidate)) && (query == null || query.apply(candidate))) found.add(candidate);
                } catch (Exception ignored) {
                    // A query that fails on a bean does not select it, as on the JDK.
                }
            }
            return found;
        }

        @Override public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
            Set<ObjectInstance> found = new HashSet<>();
            for (ObjectName match : queryNames(name, query)) {
                Object bean = beans.get(match);
                if (bean != null) found.add(new ObjectInstance(match, bean.getClass().getName()));
            }
            return found;
        }

        @Override public Integer getMBeanCount() {
            return beans.size();
        }

        @Override public String getDefaultDomain() {
            return defaultDomain;
        }

        @Override public String[] getDomains() {
            Set<String> domains = new TreeSet<>();
            for (ObjectName name : beans.keySet()) domains.add(name.getDomain());
            return domains.toArray(new String[0]);
        }

        @Override public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException {
            Object bean = bean(name);
            for (Class<?> type = bean.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals(className) || implementsNamed(type, className)) return true;
            }
            return false;
        }

        private static boolean implementsNamed(Class<?> type, String className) {
            for (Class<?> parent : type.getInterfaces()) {
                if (parent.getName().equals(className) || implementsNamed(parent, className)) return true;
            }
            return false;
        }

        /** The public MBean/MXBean interfaces of a bean class, nearest first. */
        private static List<Class<?>> interfaces(Class<?> type) {
            List<Class<?>> found = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Class<?> parent : c.getInterfaces()) collect(parent, found);
            }
            return found;
        }

        private static void collect(Class<?> type, List<Class<?>> found) {
            String name = type.getName();
            if (!found.contains(type) && Modifier.isPublic(type.getModifiers()) && (name.endsWith("MBean") || name.endsWith("MXBean"))) {
                found.add(type);
            }
            for (Class<?> parent : type.getInterfaces()) collect(parent, found);
        }

        private static Method getter(Object bean, String attribute) {
            if (attribute == null || attribute.isEmpty()) return null;
            for (Class<?> type : interfaces(bean.getClass())) {
                try {
                    Method method = type.getMethod("get" + attribute);
                    if (method.getReturnType() != void.class) return method;
                } catch (NoSuchMethodException tryIs) {
                    // An "is" getter is the other accessor form, for boolean attributes only.
                }
                try {
                    Method method = type.getMethod("is" + attribute);
                    if (method.getReturnType() == boolean.class) return method;
                } catch (NoSuchMethodException next) {
                    // Not on this interface; try the next one.
                }
            }
            return null;
        }

        private static boolean accepts(Class<?> type, Object value) {
            if (value == null) return !type.isPrimitive();
            if (!type.isPrimitive()) return type.isInstance(value);
            Class<?> boxed = type == int.class ? Integer.class : type == long.class ? Long.class
                    : type == boolean.class ? Boolean.class : type == double.class ? Double.class
                    : type == float.class ? Float.class : type == short.class ? Short.class
                    : type == byte.class ? Byte.class : Character.class;
            return boxed.isInstance(value);
        }

        private static Object call(Method method, Object bean, Object... arguments) throws Exception {
            try {
                return method.invoke(bean, arguments);
            } catch (InvocationTargetException thrown) {
                Throwable cause = thrown.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw thrown;
            }
        }

        private static Class<?> parameterType(String name, ClassLoader loader) throws ClassNotFoundException {
            switch (name) {
                case "boolean": return boolean.class;
                case "byte": return byte.class;
                case "char": return char.class;
                case "short": return short.class;
                case "int": return int.class;
                case "long": return long.class;
                case "float": return float.class;
                case "double": return double.class;
                default: return Class.forName(name, false, loader);
            }
        }
    }
}
