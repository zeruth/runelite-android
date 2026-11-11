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
package net.runelite.awt.impl;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import java.util.Collections;
import java.util.Set;

/**
 * In-process bean registry. No remote connector or platform management agent is exposed.
 */
public final class StubMBeanServer implements MBeanServer {
    public static final StubMBeanServer INSTANCE = new StubMBeanServer();

    private StubMBeanServer() {}
    private final java.util.concurrent.ConcurrentMap<ObjectName, Object> beans = new java.util.concurrent.ConcurrentHashMap<>();
    @Override public javax.management.ObjectInstance registerMBean(Object obj, ObjectName name) throws Exception {
        if (obj == null || name == null || name.isPattern()) throw new IllegalArgumentException("Concrete name and bean required");
        if (beans.putIfAbsent(name, obj) != null) throw new javax.management.InstanceAlreadyExistsException(name.toString());
        return new javax.management.ObjectInstance(name, obj.getClass().getName());
    }
    @Override public void unregisterMBean(ObjectName name) throws Exception {
        if (beans.remove(name) == null) throw new javax.management.InstanceNotFoundException(name.toString());
    }
    @Override public boolean isRegistered(ObjectName name) { return beans.containsKey(name); }
    private Object bean(ObjectName name) throws javax.management.InstanceNotFoundException {
        Object bean = beans.get(name);
        if (bean == null) throw new javax.management.InstanceNotFoundException(name.toString());
        return bean;
    }
    @Override public Object getAttribute(ObjectName name, String attribute) throws Exception {
        Object bean = bean(name);
        try { return bean.getClass().getMethod("get" + attribute).invoke(bean); }
        catch (NoSuchMethodException missing) { return bean.getClass().getMethod("is" + attribute).invoke(bean); }
    }
    @Override public void setAttribute(ObjectName name, Attribute attribute) throws Exception {
        Object bean = bean(name);
        for (java.lang.reflect.Method method : bean.getClass().getMethods()) {
            if (method.getName().equals("set" + attribute.getName()) && method.getParameterTypes().length == 1) {
                try { method.invoke(bean, attribute.getValue()); return; }
                catch (IllegalArgumentException wrongType) { /* Try another overloaded setter. */ }
            }
        }
        throw new NoSuchMethodException("set" + attribute.getName());
    }
    @Override public Object invoke(ObjectName name, String op, Object[] params, String[] signature) throws Exception {
        Object bean = bean(name);
        String[] names = signature == null ? new String[0] : signature;
        Class<?>[] types = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) types[i] = parameterType(names[i], bean.getClass().getClassLoader());
        return bean.getClass().getMethod(op, types).invoke(bean, params == null ? new Object[0] : params);
    }
    private static Class<?> parameterType(String name, ClassLoader loader) throws ClassNotFoundException {
        switch (name) {
            case "boolean": return boolean.class; case "byte": return byte.class; case "char": return char.class;
            case "short": return short.class; case "int": return int.class; case "long": return long.class;
            case "float": return float.class; case "double": return double.class; default: return Class.forName(name, false, loader);
        }
    }
    @Override public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        Set<ObjectName> found = new java.util.HashSet<>();
        if (query != null) query.setMBeanServer(this);
        for (ObjectName candidate : beans.keySet()) {
            try { if ((name == null || name.apply(candidate)) && (query == null || query.apply(candidate))) found.add(candidate); }
            catch (Exception ignored) { /* An invalid query does not match this bean. */ }
        }
        return found;
    }
    @Override public Integer getMBeanCount() { return beans.size(); }
}
