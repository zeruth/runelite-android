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
 * The PersistenceDelegate class takes the responsibility
 * for expressing the state of an instance of a given class
 * in terms of the methods in the class's public API. Instead
 * of associating the responsibility of persistence with
 * the class itself as is done, for example, by the
 * {@code readObject} and {@code writeObject}
 * methods used by the {@code ObjectOutputStream}, streams like
 * the {@code XMLEncoder} which
 * use this delegation model can have their behavior controlled
 * independently of the classes themselves.
 *
 * @see XMLEncoder
 * @since 1.4
 */
public abstract class PersistenceDelegate {

    /**
     * Constructor for subclasses to call.
     */
    public PersistenceDelegate() {
    }

    /**
     * The {@code writeObject} is a single entry point to the persistence
     * and is used by an {@code Encoder} in the traditional
     * mode of delegation.
     *
     * @param oldInstance The instance that will be created by this expression.
     * @param out The stream to which this expression will be written.
     * @throws NullPointerException if {@code out} is {@code null}
     */
    public void writeObject(Object oldInstance, Encoder out) {
        Object newInstance = out.get(oldInstance);
        if (!mutatesTo(oldInstance, newInstance)) {
            out.remove(oldInstance);
            out.writeExpression(instantiate(oldInstance, out));
        } else {
            initialize(oldInstance.getClass(), oldInstance, newInstance, out);
        }
    }

    /**
     * Returns true if an <em>equivalent</em> copy of {@code oldInstance} may be
     * created by applying a series of statements to {@code newInstance}.
     * The default returns {@code true} if the classes of the two instances are the same.
     *
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @return True if an equivalent copy of {@code newInstance} may be
     *         created by applying a series of mutations to {@code oldInstance}.
     */
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        return (newInstance != null && oldInstance != null
                && oldInstance.getClass() == newInstance.getClass());
    }

    /**
     * Returns an expression whose value is {@code oldInstance}.
     *
     * @param oldInstance The instance that will be created by this expression.
     * @param out The stream to which this expression will be written.
     * @return An expression whose value is {@code oldInstance}.
     * @throws NullPointerException if {@code out} is {@code null}
     *                              and this value is used in the method
     */
    protected abstract Expression instantiate(Object oldInstance, Encoder out);

    /**
     * Produce a series of statements with side effects on {@code newInstance}
     * so that the new instance becomes <em>equivalent</em> to {@code oldInstance}.
     * The default delegates initialization to the superclass's persistence delegate.
     *
     * @param type the type of the instances
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @param out The stream to which any initialization statements should be written.
     * @throws NullPointerException if {@code out} is {@code null}
     */
    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        Class<?> superType = type.getSuperclass();
        PersistenceDelegate info = out.getPersistenceDelegate(superType);
        info.initialize(superType, oldInstance, newInstance, out);
    }
}
