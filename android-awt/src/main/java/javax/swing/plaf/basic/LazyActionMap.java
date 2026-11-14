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
package javax.swing.plaf.basic;

import java.lang.reflect.Method;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ActionMapUIResource;

/**
 * The JDK's shared, lazily filled action map of a Basic delegate: the
 * delegate's static {@code loadActionMap(LazyActionMap)} runs on the first
 * lookup, and the map is cached in the defaults under the delegate's key so
 * every component of that type shares it.
 */
@SuppressWarnings("serial")
class LazyActionMap extends ActionMapUIResource {
    /** The class whose {@code loadActionMap} fills the map; null once loaded. */
    private transient Object _loader;

    /**
     * Installs an ActionMap that will be populated by invoking the
     * {@code loadActionMap} method on the specified Class when necessary.
     */
    static void installLazyActionMap(JComponent c, Class<?> loaderClass,
                                     String defaultsKey) {
        ActionMap map = (ActionMap) UIManager.get(defaultsKey);
        if (map == null) {
            map = new LazyActionMap(loaderClass);
            UIManager.getLookAndFeelDefaults().put(defaultsKey, map);
        }
        SwingUtilities.replaceUIActionMap(c, map);
    }

    /**
     * Returns the ActionMap stored under {@code defaultsKey}, creating a
     * lazily loaded one when there is none.
     */
    static ActionMap getActionMap(Class<?> loaderClass, String defaultsKey) {
        ActionMap map = (ActionMap) UIManager.get(defaultsKey);
        if (map == null) {
            map = new LazyActionMap(loaderClass);
            UIManager.getLookAndFeelDefaults().put(defaultsKey, map);
        }
        return map;
    }

    private LazyActionMap(Class<?> loader) {
        _loader = loader;
    }

    /** Puts {@code action} under its {@code Action.NAME}. */
    public void put(Action action) {
        put(action.getValue(Action.NAME), action);
    }

    @Override
    public void put(Object key, Action action) {
        loadIfNecessary();
        super.put(key, action);
    }

    @Override
    public Action get(Object key) {
        loadIfNecessary();
        return super.get(key);
    }

    @Override
    public void remove(Object key) {
        loadIfNecessary();
        super.remove(key);
    }

    @Override
    public void clear() {
        loadIfNecessary();
        super.clear();
    }

    @Override
    public Object[] keys() {
        loadIfNecessary();
        return super.keys();
    }

    @Override
    public int size() {
        loadIfNecessary();
        return super.size();
    }

    @Override
    public Object[] allKeys() {
        loadIfNecessary();
        return super.allKeys();
    }

    @Override
    public void setParent(ActionMap map) {
        loadIfNecessary();
        super.setParent(map);
    }

    private void loadIfNecessary() {
        if (_loader != null) {
            Object loader = _loader;

            _loader = null;
            Class<?> klass = (Class<?>) loader;
            try {
                Method method = klass.getDeclaredMethod("loadActionMap",
                                      new Class<?>[] { LazyActionMap.class });
                method.invoke(klass, new Object[] { this });
            } catch (NoSuchMethodException nsme) {
                assert false : "LazyActionMap unable to load actions " + klass;
            } catch (IllegalAccessException iae) {
                assert false : "LazyActionMap unable to load actions " + iae;
            } catch (IllegalArgumentException iae) {
                assert false : "LazyActionMap unable to load actions " + iae;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                assert false : "LazyActionMap unable to load actions " + ite;
            }
        }
    }
}
