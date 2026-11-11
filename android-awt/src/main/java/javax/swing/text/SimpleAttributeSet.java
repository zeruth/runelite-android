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
package javax.swing.text;
import java.util.*;
public class SimpleAttributeSet implements MutableAttributeSet, Cloneable, java.io.Serializable {
    public static final AttributeSet EMPTY = new SimpleAttributeSet();
    private final Map<Object,Object> values = new LinkedHashMap<>();
    private AttributeSet parent;
    public SimpleAttributeSet() {}
    public SimpleAttributeSet(AttributeSet a) { addAttributes(a); }
    public int getAttributeCount() { return values.size(); }
    public boolean isDefined(Object key) { return values.containsKey(key); }
    public Object getAttribute(Object key) { return values.containsKey(key) ? values.get(key) : parent == null ? null : parent.getAttribute(key); }
    public Enumeration<?> getAttributeNames() { return Collections.enumeration(values.keySet()); }
    public boolean containsAttribute(Object key,Object value) { return Objects.equals(getAttribute(key),value); }
    public boolean containsAttributes(AttributeSet a) { Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();if(!containsAttribute(k,a.getAttribute(k)))return false;}return true; }
    public boolean isEqual(AttributeSet a) { return a.getAttributeCount()==getAttributeCount() && containsAttributes(a); }
    public AttributeSet copyAttributes() { SimpleAttributeSet a=new SimpleAttributeSet(this);a.parent=parent;return a; }
    public Object clone() { return copyAttributes(); }
    public void addAttribute(Object k,Object v) { values.put(k,v); }
    public void addAttributes(AttributeSet a) { if(a==null)return;if(a.getResolveParent()!=null)parent=a.getResolveParent();Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();values.put(k,a.getAttribute(k));} }
    public void removeAttribute(Object k) { values.remove(k); }
    public void removeAttributes(Enumeration<?> e) { while(e.hasMoreElements())values.remove(e.nextElement()); }
    public void removeAttributes(AttributeSet a) { List<Object> keys=new ArrayList<>();Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();if(containsAttribute(k,a.getAttribute(k)))keys.add(k);}for(Object k:keys)values.remove(k); }
    public AttributeSet getResolveParent() { return parent; }
    public void setResolveParent(AttributeSet a) { parent=a; }
}
