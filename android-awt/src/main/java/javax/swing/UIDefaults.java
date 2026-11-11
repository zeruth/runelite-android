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

import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.util.Hashtable;
import java.util.Locale;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;

public class UIDefaults extends Hashtable<Object, Object> {
    private static final long serialVersionUID = 1L;

    public interface LazyValue { Object createValue(UIDefaults table); }
    public interface ActiveValue { Object createValue(UIDefaults table); }

    public UIDefaults() {}
    public UIDefaults(int initialCapacity, float loadFactor) { super(initialCapacity, loadFactor); }
    public UIDefaults(Object[] keyValueList) { for (int i = 0; i + 1 < keyValueList.length; i += 2) put(keyValueList[i], keyValueList[i + 1]); }

    public Color getColor(Object key) { Object v = get(key); return v instanceof Color ? (Color) v : null; }
    public Font getFont(Object key) { Object v = get(key); return v instanceof Font ? (Font) v : null; }
    public Border getBorder(Object key) { Object v = get(key); return v instanceof Border ? (Border) v : null; }
    public Icon getIcon(Object key) { Object v = get(key); return v instanceof Icon ? (Icon) v : null; }
    public int getInt(Object key) { Object v = get(key); return v instanceof Integer ? (Integer) v : 0; }
    public boolean getBoolean(Object key) { Object v = get(key); return v instanceof Boolean && (Boolean) v; }
    public String getString(Object key) { Object v = get(key); return v == null ? null : v.toString(); }

    public synchronized Object get(Object key,Locale locale) {
        Object value=super.get(key);
        if(value instanceof LazyValue){value=((LazyValue)value).createValue(this);if(value==null)super.remove(key);else super.put(key,value);}
        else if(value instanceof ActiveValue)value=((ActiveValue)value).createValue(this);
        if(value==null&&key instanceof String)for(int i=bundles.size()-1;i>=0;i--)try{return java.util.ResourceBundle.getBundle(bundles.get(i),locale==null?defaultLocale:locale).getObject((String)key);}catch(java.util.MissingResourceException missing){}
        return value;
    }
    public Color getColor(Object key, Locale l) { return getColor(key); }
    public Font getFont(Object key, Locale l) { return getFont(key); }

    private final java.beans.PropertyChangeSupport changes=new java.beans.PropertyChangeSupport(this);
    private final java.util.List<String> bundles=new java.util.ArrayList<>();
    private Locale defaultLocale=Locale.getDefault();
    @Override public Object get(Object key){return get(key,defaultLocale);}
    @Override public synchronized Object put(Object key,Object value){Object old=value==null?super.remove(key):super.put(key,value);if(key instanceof String)changes.firePropertyChange((String)key,old,value);return old;}
    public void putDefaults(Object[] values){for(int i=0;i+1<values.length;i+=2){if(values[i+1]==null)super.remove(values[i]);else super.put(values[i],values[i+1]);}changes.firePropertyChange("UIDefaults",null,null);}
    public void setDefaultLocale(Locale locale){defaultLocale=locale;}
    public Locale getDefaultLocale(){return defaultLocale;}
    public void addPropertyChangeListener(PropertyChangeListener l){changes.addPropertyChangeListener(l);}
    public void removePropertyChangeListener(PropertyChangeListener l){changes.removePropertyChangeListener(l);}
    public PropertyChangeListener[] getPropertyChangeListeners(){return changes.getPropertyChangeListeners();}
    public void addResourceBundle(String name){if(name!=null&&!bundles.contains(name))bundles.add(name);}
    public void removeResourceBundle(String name){bundles.remove(name);}
    public Class<? extends ComponentUI> getUIClass(String id){return getUIClass(id,Thread.currentThread().getContextClassLoader());}
    public Class<? extends ComponentUI> getUIClass(String id,ClassLoader loader){Object value=get(id);try{return value instanceof Class?((Class<?>)value).asSubclass(ComponentUI.class):value instanceof String?Class.forName((String)value,true,loader).asSubclass(ComponentUI.class):null;}catch(ReflectiveOperationException|ClassCastException e){return null;}}
    public ComponentUI getUI(JComponent target){Class<? extends ComponentUI> type=getUIClass(target.getUIClassID());if(type==null)return null;try{return (ComponentUI)type.getMethod("createUI",JComponent.class).invoke(null,target);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot create UI "+type.getName(),e);}}
}
