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
package javax.swing;

import java.util.Collection;
import java.util.Vector;

/**
 * Swing's semantics: an array/Vector-built model starts with its first item selected, the
 * first addElement into an empty model selects it, removing the selected item moves the
 * selection to a neighbour, and every change fires a ListDataEvent (selection changes as
 * contentsChanged(-1, -1)) — JComboBox derives its ItemEvents/ActionEvents from those.
 */
public class DefaultComboBoxModel<E> extends AbstractListModel<E> implements MutableComboBoxModel<E> {
    private static final long serialVersionUID = 1L;
    // A Vector, and the caller's own Vector for the Vector constructor: the JDK model wraps
    // it without copying, so a caller that keeps mutating the vector sees it in the model.
    private final Vector<E> objects;
    private Object selectedObject;

    public DefaultComboBoxModel() { objects = new Vector<E>(); }
    public DefaultComboBoxModel(E[] items) {
        objects = new Vector<E>(items.length);
        for (E e : items) objects.addElement(e);
        if (getSize() > 0) selectedObject = getElementAt(0);
    }
    public DefaultComboBoxModel(Vector<E> v) {
        objects = v;
        if (getSize() > 0) selectedObject = getElementAt(0);
    }

    @Override public int getSize() { return objects.size(); }
    @Override public E getElementAt(int index) {
        return index >= 0 && index < objects.size() ? objects.get(index) : null;
    }
    @Override public Object getSelectedItem() { return selectedObject; }
    @Override public void setSelectedItem(Object anObject) {
        if ((selectedObject != null && !selectedObject.equals(anObject)) || (selectedObject == null && anObject != null)) {
            selectedObject = anObject;
            fireContentsChanged(this, -1, -1);
        }
    }
    public int getIndexOf(Object anObject) { return objects.indexOf(anObject); }

    @Override public void addElement(E anObject) {
        objects.add(anObject);
        fireIntervalAdded(this, objects.size() - 1, objects.size() - 1);
        if (objects.size() == 1 && selectedObject == null && anObject != null) setSelectedItem(anObject);
    }
    @Override public void insertElementAt(E anObject, int index) {
        objects.add(index, anObject);
        fireIntervalAdded(this, index, index);
    }
    @Override public void removeElementAt(int index) {
        if (getElementAt(index) == selectedObject) {
            if (index == 0) setSelectedItem(getSize() == 1 ? null : getElementAt(index + 1));
            else setSelectedItem(getElementAt(index - 1));
        }
        objects.remove(index);
        fireIntervalRemoved(this, index, index);
    }
    @Override public void removeElement(Object anObject) {
        int index = objects.indexOf(anObject);
        if (index != -1) removeElementAt(index);
    }
    public void removeAllElements() {
        if (!objects.isEmpty()) {
            int lastIndex = objects.size() - 1;
            objects.clear();
            selectedObject = null;
            fireIntervalRemoved(this, 0, lastIndex);
        } else {
            selectedObject = null;
        }
    }
    // JDK 9+ (11): jdk17u src/java.desktop/share/classes/javax/swing/DefaultComboBoxModel.java
    public void addAll(Collection<? extends E> c) { addAll(objects.size(), c); }
    // JDK 9+ (11): jdk17u src/java.desktop/share/classes/javax/swing/DefaultComboBoxModel.java
    public void addAll(int index, Collection<? extends E> c) {
        if (index < 0 || index > getSize()) throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        if (c.isEmpty()) return;
        objects.addAll(index, c);
        fireIntervalAdded(this, index, index + c.size() - 1);
    }
}
