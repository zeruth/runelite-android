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

import java.applet.Applet;
import java.beans.beancontext.BeanContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * This class provides some general purpose beans control methods.
 */
public class Beans {
    private static volatile boolean designTime;
    private static volatile Boolean guiAvailable;

    /**
     * Constructs a {@code Beans} instance.
     */
    public Beans() {
    }

    /**
     * Instantiate a JavaBean.
     *
     * @return a JavaBean
     * @param     cls         the class-loader from which we should create
     *                        the bean.  If this is null, then the system
     *                        class-loader is used.
     * @param     beanName    the name of the bean within the class-loader.
     *                        For example "sun.beanbox.foobah"
     * @exception ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @exception IOException if an I/O error occurs.
     */
    public static Object instantiate(ClassLoader cls, String beanName)
            throws IOException, ClassNotFoundException {
        return Beans.instantiate(cls, beanName, null, null);
    }

    /**
     * Instantiate a JavaBean.
     *
     * @return a JavaBean
     * @param     cls         the class-loader from which we should create
     *                        the bean.  If this is null, then the system
     *                        class-loader is used.
     * @param     beanName    the name of the bean within the class-loader.
     * @param     beanContext The BeanContext in which to nest the new bean
     * @exception ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @exception IOException if an I/O error occurs.
     * @since 1.2
     */
    public static Object instantiate(ClassLoader cls, String beanName, BeanContext beanContext)
            throws IOException, ClassNotFoundException {
        return Beans.instantiate(cls, beanName, beanContext, null);
    }

    /**
     * Instantiate a bean.
     * <p>
     * The bean is created based on a name relative to a class-loader.
     * This name should be a dot-separated name such as "a.b.c". A serialized
     * form ({@code a/b/c.ser}) is tried first; otherwise the class {@code a.b.c}
     * is loaded and its public nullary constructor called. A bean that is an
     * {@code Applet} is handed to the initializer, sized 100x100 and has its
     * {@code init} called unless it was deserialized, then is activated. No
     * applet stub is synthesized when the initializer is null: there is no
     * applet viewer on Android, and the shadow {@code Applet} answers the stub
     * queries with null.
     *
     * @param     cls         the class-loader from which we should create
     *                        the bean.  If this is null, then the system
     *                        class-loader is used.
     * @param     beanName    the name of the bean within the class-loader.
     * @param     beanContext The BeanContext in which to nest the new bean
     * @param     initializer The AppletInitializer for the new bean
     * @return a JavaBean
     * @exception ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @exception IOException if an I/O error occurs.
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
    public static Object instantiate(ClassLoader cls, String beanName, BeanContext beanContext,
                                     AppletInitializer initializer)
            throws IOException, ClassNotFoundException {
        if (cls == null) {
            cls = ClassLoader.getSystemClassLoader();
        }
        final ClassLoader loader = cls;
        Object result = null;
        boolean serialized = false;
        String serName = beanName.replace('.', '/').concat(".ser");
        InputStream ins = loader.getResourceAsStream(serName);
        if (ins != null) {
            try {
                ObjectInputStream oins = new ObjectInputStream(ins) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass desc)
                            throws IOException, ClassNotFoundException {
                        try {
                            return Class.forName(desc.getName(), false, loader);
                        } catch (ClassNotFoundException e) {
                            return super.resolveClass(desc);
                        }
                    }
                };
                try {
                    result = oins.readObject();
                    serialized = true;
                } finally {
                    oins.close();
                }
            } catch (IOException ex) {
                ins.close();
                // Drop through and try opening the class.  But remember the exception in case we can't find the class either.
            } catch (ClassNotFoundException ex) {
                ins.close();
                throw ex;
            }
        }
        if (result == null) {
            Class<?> cl = Class.forName(beanName, true, loader);
            try {
                result = cl.getConstructor().newInstance();
            } catch (Exception ex) {
                throw new ClassNotFoundException("" + cl + " : " + ex, ex);
            }
        }
        if (result instanceof Applet) {
            Applet applet = (Applet) result;
            if (initializer != null) {
                initializer.initialize(applet, beanContext);
            }
            if (beanContext != null) {
                beanContext.add(result);
            }
            if (!serialized) {
                applet.setSize(100, 100);
                applet.init();
            }
            if (initializer != null) {
                initializer.activate(applet);
            }
        } else if (beanContext != null) {
            beanContext.add(result);
        }
        return result;
    }

    /**
     * From a given bean, obtain an object representing a specified
     * type view of that source object.
     *
     * @return an object representing a specified type view of the
     * source object
     * @param bean        Object from which we want to obtain a view.
     * @param targetType  The type of view we'd like to get.
     */
    public static Object getInstanceOf(Object bean, Class<?> targetType) {
        return bean;
    }

    /**
     * Check if a bean can be viewed as a given target type.
     *
     * @return "true" if the given bean supports the given targetType.
     * @param bean  Bean from which we want to obtain a view.
     * @param targetType  The type of view we'd like to get.
     */
    public static boolean isInstanceOf(Object bean, Class<?> targetType) {
        return targetType.isAssignableFrom(bean.getClass());
    }

    /**
     * Test if we are in design-mode.
     *
     * @return  True if we are running in an application construction
     *          environment.
     */
    public static boolean isDesignTime() {
        return designTime;
    }

    /**
     * Determines whether beans can assume a GUI is available. Unless set
     * with {@link #setGuiAvailable}, this answers the inverse of
     * {@code GraphicsEnvironment.isHeadless()}, as on desktop.
     *
     * @return  True if we are running in an environment where beans
     *     can assume that an interactive GUI is available, so they
     *     can pop up dialog boxes, etc.
     */
    public static boolean isGuiAvailable() {
        Boolean available = guiAvailable;
        return available != null ? available.booleanValue() : !java.awt.GraphicsEnvironment.isHeadless();
    }

    /**
     * Used to indicate whether of not we are running in an application
     * builder environment.
     *
     * @param isDesignTime  True if we're in an application builder tool.
     */
    public static void setDesignTime(boolean isDesignTime) throws SecurityException {
        designTime = isDesignTime;
    }

    /**
     * Used to indicate whether of not we are running in an environment
     * where GUI interaction is available.
     *
     * @param isGuiAvailable  True if GUI interaction is available.
     */
    public static void setGuiAvailable(boolean isGuiAvailable) throws SecurityException {
        guiAvailable = Boolean.valueOf(isGuiAvailable);
    }
}
