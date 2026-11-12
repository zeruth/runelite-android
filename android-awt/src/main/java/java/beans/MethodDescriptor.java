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

import java.lang.reflect.Method;

/**
 * A MethodDescriptor describes a particular method that a Java Bean
 * supports for external access from other components.
 *
 * @since 1.1
 */
public class MethodDescriptor extends FeatureDescriptor {
    private final Method method;
    private ParameterDescriptor[] parameterDescriptors;

    /**
     * Constructs a {@code MethodDescriptor} from a
     * {@code Method}.
     *
     * @param method    The low-level method information.
     */
    public MethodDescriptor(Method method) {
        this(method, null);
    }

    /**
     * Constructs a {@code MethodDescriptor} from a
     * {@code Method} providing descriptive information for each
     * of the method's parameters.
     *
     * @param method    The low-level method information.
     * @param parameterDescriptors  Descriptive information for each of the
     *                          method's parameters.
     */
    public MethodDescriptor(Method method, ParameterDescriptor[] parameterDescriptors) {
        setName(method.getName());
        this.method = method;
        this.parameterDescriptors = parameterDescriptors != null ? parameterDescriptors.clone() : null;
    }

    /**
     * Gets the method that this MethodDescriptor encapsulates.
     *
     * @return The low-level description of the method
     */
    public synchronized Method getMethod() {
        return method;
    }

    /**
     * Gets the ParameterDescriptor for each of this MethodDescriptor's
     * method's parameters.
     *
     * @return The locale-independent names of the parameters.  May return
     *          a null array if the parameter names aren't known.
     */
    public ParameterDescriptor[] getParameterDescriptors() {
        return parameterDescriptors != null ? parameterDescriptors.clone() : null;
    }

    void appendTo(StringBuilder sb) {
        appendTo(sb, "method", method);
        if (parameterDescriptors != null) {
            sb.append("; parameterDescriptors={");
            for (int i = 0; i < parameterDescriptors.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameterDescriptors[i]);
            }
            sb.append('}');
        }
    }
}
