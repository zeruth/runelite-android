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

/**
 * Use the {@code BeanInfo} interface to create a {@code BeanInfo} class
 * and provide explicit information about the methods, properties, events,
 * and other features of your beans.
 *
 * @see SimpleBeanInfo
 * @since 1.1
 */
public interface BeanInfo {
    /**
     * Returns the bean descriptor that provides overall information about the bean.
     * @return a {@link BeanDescriptor} object, or {@code null}
     */
    BeanDescriptor getBeanDescriptor();

    /**
     * Returns the event descriptors of the bean.
     * @return an array of {@link EventSetDescriptor} objects, or {@code null}
     */
    EventSetDescriptor[] getEventSetDescriptors();

    /**
     * A bean may have a default event typically applied when this bean is used.
     * @return index of the default event in the {@code EventSetDescriptor} array, or -1
     */
    int getDefaultEventIndex();

    /**
     * Returns descriptors for all properties of the bean.
     * @return an array of {@code PropertyDescriptor} objects, or {@code null}
     */
    PropertyDescriptor[] getPropertyDescriptors();

    /**
     * A bean may have a default property commonly updated when this bean is customized.
     * @return index of the default property in the {@code PropertyDescriptor} array, or -1
     */
    int getDefaultPropertyIndex();

    /**
     * Returns the method descriptors of the bean.
     * @return an array of {@link MethodDescriptor} objects, or {@code null}
     */
    MethodDescriptor[] getMethodDescriptors();

    /**
     * This method enables the current {@code BeanInfo} object to return
     * an arbitrary collection of other {@code BeanInfo} objects that provide
     * additional information about the current bean.
     * @return an array of {@code BeanInfo} objects, or {@code null}
     */
    BeanInfo[] getAdditionalBeanInfo();

    /**
     * Returns an image that can be used to represent the bean in toolboxes or toolbars.
     * @param iconKind  the kind of icon requested
     * @return an image object representing the requested icon, or {@code null}
     */
    Image getIcon(int iconKind);

    /** Constant to indicate a 16 x 16 color icon. */
    final static int ICON_COLOR_16x16 = 1;

    /** Constant to indicate a 32 x 32 color icon. */
    final static int ICON_COLOR_32x32 = 2;

    /** Constant to indicate a 16 x 16 monochrome icon. */
    final static int ICON_MONO_16x16 = 3;

    /** Constant to indicate a 32 x 32 monochrome icon. */
    final static int ICON_MONO_32x32 = 4;
}
