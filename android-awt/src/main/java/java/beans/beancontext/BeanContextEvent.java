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

import java.util.EventObject;

/**
 * {@code BeanContextEvent} is the abstract root event class for all events
 * emitted from, and pertaining to the semantics of, a {@code BeanContext}.
 *
 * @since 1.2
 */
public abstract class BeanContextEvent extends EventObject {
    private static final long serialVersionUID = 7267998073569045052L;

    /**
     * The {@code BeanContext} from which this event was propagated
     */
    protected BeanContext propagatedFrom;

    /**
     * Contruct a BeanContextEvent
     *
     * @param bc        The BeanContext source
     */
    protected BeanContextEvent(BeanContext bc) {
        super(bc);
    }

    /**
     * Gets the {@code BeanContext} associated with this event.
     * @return the {@code BeanContext} associated with this event.
     */
    public BeanContext getBeanContext() {
        return (BeanContext) getSource();
    }

    /**
     * Sets the {@code BeanContext} from which this event was propagated.
     * @param bc the {@code BeanContext} from which this event was propagated
     */
    public synchronized void setPropagatedFrom(BeanContext bc) {
        propagatedFrom = bc;
    }

    /**
     * Gets the {@code BeanContext} from which this event was propagated.
     * @return the {@code BeanContext} from which this event was propagated
     */
    public synchronized BeanContext getPropagatedFrom() {
        return propagatedFrom;
    }

    /**
     * Reports whether or not this event is propagated from some other
     * {@code BeanContext}.
     * @return {@code true} if propagated, {@code false} if not
     */
    public synchronized boolean isPropagated() {
        return propagatedFrom != null;
    }
}
