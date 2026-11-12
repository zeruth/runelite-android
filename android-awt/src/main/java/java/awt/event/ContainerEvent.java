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
package java.awt.event;

import java.awt.Component;
import java.awt.Container;

/**
 * A container's contents changed because a component was added or removed.
 */
public class ContainerEvent extends ComponentEvent {

    private static final long serialVersionUID = -4114942250539772041L;

    /** The first number in the range of ids used for container events. */
    public static final int CONTAINER_FIRST = 300;

    /** The last number in the range of ids used for container events. */
    public static final int CONTAINER_LAST = 301;

    /** This event indicates that a component was added to the container. */
    public static final int COMPONENT_ADDED = CONTAINER_FIRST;

    /** This event indicates that a component was removed from the container. */
    public static final int COMPONENT_REMOVED = 1 + CONTAINER_FIRST;

    /** The non-null component that is being added or removed from the Container. */
    Component child;

    public ContainerEvent(Component source, int id, Component child) {
        super(source, id);
        this.child = child;
    }

    /** The originator of the event, or null when the source is not a Container. */
    public Container getContainer() {
        return (source instanceof Container) ? (Container) source : null;
    }

    public Component getChild() {
        return child;
    }

    @Override
    public String paramString() {
        String typeStr;
        switch (id) {
            case COMPONENT_ADDED:
                typeStr = "COMPONENT_ADDED";
                break;
            case COMPONENT_REMOVED:
                typeStr = "COMPONENT_REMOVED";
                break;
            default:
                typeStr = "unknown type";
        }
        return typeStr + ",child=" + (child == null ? null : child.getName());
    }
}
