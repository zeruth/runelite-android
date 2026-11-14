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

import java.io.Serializable;
import java.util.EventListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public abstract class AbstractListModel<E> implements ListModel<E>, Serializable {
    private static final long serialVersionUID = -3285184064379168730L;
    protected EventListenerList listenerList = new EventListenerList();

    @Override public void addListDataListener(ListDataListener l) { listenerList.add(ListDataListener.class, l); }
    @Override public void removeListDataListener(ListDataListener l) { listenerList.remove(ListDataListener.class, l); }
    public ListDataListener[] getListDataListeners() { return listenerList.getListeners(ListDataListener.class); }
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) { return listenerList.getListeners(listenerType); }

    // JComboBox learns about selection and item changes only through these, so they have
    // to really fire (back to front, as Swing does).
    protected void fireContentsChanged(Object source, int index0, int index1) {
        fire(source, ListDataEvent.CONTENTS_CHANGED, index0, index1);
    }
    protected void fireIntervalAdded(Object source, int index0, int index1) {
        fire(source, ListDataEvent.INTERVAL_ADDED, index0, index1);
    }
    protected void fireIntervalRemoved(Object source, int index0, int index1) {
        fire(source, ListDataEvent.INTERVAL_REMOVED, index0, index1);
    }

    private void fire(Object source, int type, int index0, int index1) {
        Object[] listeners = listenerList.getListenerList();
        ListDataEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] != ListDataListener.class) continue;
            if (e == null) e = new ListDataEvent(source, type, index0, index1);
            ListDataListener l = (ListDataListener) listeners[i + 1];
            if (type == ListDataEvent.CONTENTS_CHANGED) l.contentsChanged(e);
            else if (type == ListDataEvent.INTERVAL_ADDED) l.intervalAdded(e);
            else l.intervalRemoved(e);
        }
    }
}
