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
// JDK 9+ (9): jdk17u src/java.desktop/share/classes/java/beans/BeanProperty.java
package java.beans;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation used to specify some property-related information for the
 * automatically generated {@link BeanInfo} classes. This annotation is not used
 * if the annotated class has a corresponding user-defined {@code BeanInfo}
 * class, which does not imply the automatic analysis. If both the read and the
 * write methods of the property are annotated, then the read method annotation
 * will have more priority and replace the write method annotation.
 *
 * @see BeanInfo#getPropertyDescriptors
 * @since 9
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface BeanProperty {
    /**
     * The value that indicates whether the annotated property can be
     * a {@link PropertyDescriptor#isBound bound} property or not.
     *
     * @return {@code true} if the annotated property can be a bound property;
     *         {@code false} otherwise.
     */
    boolean bound() default true;

    /**
     * The value that indicates whether the annotated property is
     * an {@link PropertyDescriptor#isExpert expert} property or not.
     *
     * @return {@code true} if the annotated property is an expert property;
     *         {@code false} otherwise.
     */
    boolean expert() default false;

    /**
     * The value that indicates whether the annotated property is
     * a {@link PropertyDescriptor#isHidden hidden} property or not.
     *
     * @return {@code true} if the annotated property is a hidden property;
     *         {@code false} otherwise.
     */
    boolean hidden() default false;

    /**
     * The value that indicates whether the annotated property is
     * a {@link PropertyDescriptor#isPreferred preferred} property or not.
     *
     * @return {@code true} if the annotated property is a preferred property;
     *         {@code false} otherwise.
     */
    boolean preferred() default false;

    /**
     * The value that indicates whether the annotated property is
     * a required property or not.
     *
     * @return {@code true} if the annotated property is a required property;
     *         {@code false} otherwise.
     */
    boolean required() default false;

    /**
     * The value that indicates whether the corresponding component
     * is repainted after the annotated property got changed or not.
     *
     * @return {@code true} if the corresponding component is repainted;
     *         {@code false} otherwise.
     */
    boolean visualUpdate() default false;

    /**
     * The {@link PropertyDescriptor#getShortDescription short description}
     * for the {@link BeanInfo#getPropertyDescriptors descriptor}
     * of the annotated property.
     *
     * @return the property description,
     *         or an empty string if the description is not set.
     */
    String description() default "";

    /**
     * The array of names for the public static fields
     * that contains the valid values of the annotated property.
     * These names are used to generate the {@code enumerationValues}
     * {@link java.beans.BeanDescriptor#getValue feature attribute}
     * that must contain the following items per each property value:
     * a displayable name for the property value, the actual property value,
     * and a Java code piece used for the code generator.
     *
     * @return the names of the valid values of the annotated property,
     *         or an empty array if the names are not provided.
     */
    String[] enumerationValues() default {};
}
