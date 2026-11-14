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
package javax.management;

import java.util.ArrayList;
import java.util.List;

/** A list of {@link Attribute} values, as read or written by {@code getAttributes}/{@code setAttributes}. */
public class AttributeList extends ArrayList<Object> {
    private static final long serialVersionUID = -4077085769279709076L;

    public AttributeList() {}

    public AttributeList(int initialCapacity) {
        super(initialCapacity);
    }

    public AttributeList(AttributeList list) {
        super(list);
    }

    public AttributeList(List<Attribute> list) {
        if (list == null) throw new IllegalArgumentException("Null parameter");
        for (Object element : list) {
            if (!(element instanceof Attribute)) throw new IllegalArgumentException("Not an Attribute: " + element);
        }
        addAll(list);
    }

    /** A view of this list typed as attributes; every element must be an {@link Attribute}. */
    @SuppressWarnings("unchecked")
    public List<Attribute> asList() {
        for (Object element : this) {
            if (!(element instanceof Attribute)) throw new IllegalArgumentException("Not an Attribute: " + element);
        }
        return (List<Attribute>) (List<?>) this;
    }

    public void add(Attribute object) {
        super.add(object);
    }

    public void add(int index, Attribute object) {
        super.add(index, object);
    }

    public void set(int index, Attribute object) {
        super.set(index, object);
    }

    public boolean addAll(AttributeList list) {
        return super.addAll(list);
    }

    public boolean addAll(int index, AttributeList list) {
        return super.addAll(index, list);
    }
}
