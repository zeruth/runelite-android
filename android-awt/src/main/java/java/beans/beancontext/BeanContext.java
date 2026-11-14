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
package java.beans.beancontext;

import java.beans.DesignMode;
import java.beans.Visibility;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

/**
 * The BeanContext acts a logical hierarchical container for JavaBeans.
 * Only the interface is shadowed (Beans.instantiate and AppletInitializer
 * name it); the JDK's BeanContextSupport implementation is not part of
 * the surface RuneLite or hub plugins reach.
 *
 * @since 1.2
 */
@SuppressWarnings("rawtypes")
public interface BeanContext extends BeanContextChild, Collection, DesignMode, Visibility {
    /**
     * This global lock is used by both BeanContext and BeanContextServices
     * implementors to serialize changes in a BeanContext hierarchy and any
     * service requests etc.
     */
    public static final Object globalHierarchyLock = new Object();

    /**
     * Instantiate the javaBean named as a child of this BeanContext.
     *
     * @param beanName The name of the JavaBean to instantiate as a child of this BeanContext
     * @return a javaBean named as a child of this BeanContext
     * @throws IOException if an IO problem occurs
     * @throws ClassNotFoundException if the class identified by the beanName parameter is not found
     */
    Object instantiateChild(String beanName) throws IOException, ClassNotFoundException;

    /**
     * Analagous to {@code java.lang.ClassLoader.getResourceAsStream()}.
     *
     * @param name the resource name
     * @param bcc the specified child
     * @return an {@code InputStream} for reading the resource, or {@code null}
     * @throws IllegalArgumentException if the resource is not valid
     */
    InputStream getResourceAsStream(String name, BeanContextChild bcc) throws IllegalArgumentException;

    /**
     * Analagous to {@code java.lang.ClassLoader.getResource()}.
     *
     * @param name the resource name
     * @param bcc the specified child
     * @return a {@code URL} for the named resource for the specified child
     * @throws IllegalArgumentException if the resource is not valid
     */
    URL getResource(String name, BeanContextChild bcc) throws IllegalArgumentException;

    /**
     * Adds the specified {@code BeanContextMembershipListener}.
     * @param bcml the BeanContextMembershipListener to add
     */
    void addBeanContextMembershipListener(BeanContextMembershipListener bcml);

    /**
     * Removes the specified {@code BeanContextMembershipListener}.
     * @param bcml the BeanContextMembershipListener to remove
     */
    void removeBeanContextMembershipListener(BeanContextMembershipListener bcml);
}
