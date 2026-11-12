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
package java.awt;

/**
 * The default FocusTraversalPolicy for AWT: container order, with the
 * focusability of a Component whose focusability was never set explicitly
 * decided by its native peer. Unless set explicitly, heavyweight Canvas,
 * Panel, Label, ScrollPane, Scrollbar and Window, and all lightweight
 * Components, are not focusable; Button, Checkbox, Choice, List and the
 * text components are.
 *
 * @see ContainerOrderFocusTraversalPolicy
 * @since 1.4
 */
public class DefaultFocusTraversalPolicy extends ContainerOrderFocusTraversalPolicy {
    private static final long serialVersionUID = 8876966522510157497L;

    /**
     * Determines whether a Component may be a new focus owner: visible,
     * displayable, enabled, every heavyweight ancestor enabled, and either
     * explicitly focusable or of a class whose peer takes focus.
     */
    protected boolean accept(Component aComponent) {
        if (!(aComponent.isVisible() && aComponent.isDisplayable() && aComponent.isEnabled())) {
            return false;
        }
        // Disabling a heavyweight Container disables its children; a lightweight one does not.
        if (!(aComponent instanceof Window)) {
            for (Container enableTest = aComponent.getParent(); enableTest != null; enableTest = enableTest.getParent()) {
                if (!(enableTest.isEnabled() || enableTest.isLightweight())) {
                    return false;
                }
                if (enableTest instanceof Window) {
                    break;
                }
            }
        }
        boolean focusable = aComponent.isFocusable();
        if (aComponent.isFocusTraversableOverridden()) {
            return focusable;
        }
        return peerFocusable(aComponent);
    }

    /**
     * Stands in for {@code ComponentPeer.isFocusable()}: there are no native
     * peers on Android, so the answer comes from the nearest java.awt class
     * the component extends, as the desktop peers give it.
     */
    private static boolean peerFocusable(Component component) {
        if (component.isLightweight()) {
            return false;
        }
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (!name.startsWith("java.awt.") || name.indexOf('.', "java.awt.".length()) >= 0) {
                continue;
            }
            return name.equals("java.awt.Button") || name.equals("java.awt.Checkbox")
                || name.equals("java.awt.Choice") || name.equals("java.awt.List")
                || name.equals("java.awt.TextField") || name.equals("java.awt.TextArea")
                || name.equals("java.awt.TextComponent");
        }
        return false;
    }
}
