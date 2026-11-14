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
// JDK 9+ (9): jdk17u src/java.desktop/share/classes/java/beans/JavaBean.java
package java.beans;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation used to specify some class-related information
 * for the automatically generated {@link BeanInfo} classes.
 * This annotation is not used if the annotated class
 * has a corresponding user-defined {@code BeanInfo} class,
 * which does not imply the automatic analysis.
 *
 * @see BeanInfo#getBeanDescriptor
 * @since 9
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface JavaBean {
    /**
     * The {@link BeanDescriptor#getShortDescription short description}
     * for the {@link BeanInfo#getBeanDescriptor bean descriptor}
     * of the annotated class.
     *
     * @return the bean description, or an empty string if the description is not set.
     */
    String description() default "";

    /**
     * The name of the default property is used to calculate its
     * {@link BeanInfo#getDefaultPropertyIndex index} in the
     * {@link BeanInfo#getPropertyDescriptors array} of properties
     * defined in the annotated class.
     *
     * @return the default property name, or an empty string if the name is not set.
     */
    String defaultProperty() default "";

    /**
     * The name of the default event set is used to calculate its
     * {@link BeanInfo#getDefaultEventIndex index} in the
     * {@link BeanInfo#getEventSetDescriptors array} of event sets
     * defined in the annotated class.
     *
     * @return the default event set name, or an empty string if the name is not set.
     */
    String defaultEventSet() default "";
}
