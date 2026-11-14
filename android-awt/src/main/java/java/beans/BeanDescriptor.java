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

/**
 * A BeanDescriptor provides global information about a "bean",
 * including its Java class, its displayName, etc.
 *
 * @since 1.1
 */
public class BeanDescriptor extends FeatureDescriptor {
    private final Class<?> beanClass;
    private final Class<?> customizerClass;

    /**
     * Create a BeanDescriptor for a bean that doesn't have a customizer.
     *
     * @param beanClass  The Class object of the Java class that implements the
     *          bean.  For example sun.beans.OurButton.class.
     */
    public BeanDescriptor(Class<?> beanClass) {
        this(beanClass, null);
    }

    /**
     * Create a BeanDescriptor for a bean that has a customizer.
     *
     * @param beanClass  The Class object of the Java class that implements the
     *          bean.  For example sun.beans.OurButton.class.
     * @param customizerClass  The Class object of the Java class that implements
     *          the bean's Customizer.
     */
    public BeanDescriptor(Class<?> beanClass, Class<?> customizerClass) {
        this.beanClass = beanClass;
        this.customizerClass = customizerClass;
        String name = beanClass.getName();
        while (name.indexOf('.') >= 0) {
            name = name.substring(name.indexOf('.') + 1);
        }
        setName(name);
    }

    /**
     * Gets the bean's Class object.
     *
     * @return The Class object for the bean.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * Gets the Class object for the bean's customizer.
     *
     * @return The Class object for the bean's customizer.  This may
     * be null if the bean doesn't have a customizer.
     */
    public Class<?> getCustomizerClass() {
        return customizerClass;
    }

    void appendTo(StringBuilder sb) {
        appendTo(sb, "beanClass", beanClass);
        appendTo(sb, "customizerClass", customizerClass);
    }
}
