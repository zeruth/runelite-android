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
import java.lang.reflect.Modifier;

/**
 * An EventSetDescriptor describes a group of events that a given Java
 * bean fires.
 * <P>
 * The given group of events are all delivered as method calls on a single
 * event listener interface, and an event listener object can be registered
 * via a call on a registration method supplied by the event source.
 */
public class EventSetDescriptor extends FeatureDescriptor {
    private MethodDescriptor[] listenerMethodDescriptors;
    private Method[] listenerMethods;
    private Method addMethod;
    private Method removeMethod;
    private Method getMethod;
    private Class<?> listenerType;
    private boolean unicast;
    private boolean inDefaultEventSet = true;

    /**
     * Creates an {@code EventSetDescriptor} assuming that you are
     * following the most simple standard design pattern where a named
     * event &quot;fred&quot; is (1) delivered as a call on the single method of
     * interface FredListener, (2) has a single argument of type FredEvent,
     * and (3) where the FredListener may be registered with a call on an
     * addFredListener method of the source component and removed with a
     * call on a removeFredListener method.
     *
     * @param sourceClass  The class firing the event.
     * @param eventSetName  The programmatic name of the event.  E.g. &quot;fred&quot;.
     * @param listenerType  The target interface that events will get delivered to.
     * @param listenerMethodName  The method that will get called when the event gets
     *          delivered to its target listener interface.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public EventSetDescriptor(Class<?> sourceClass, String eventSetName,
                Class<?> listenerType, String listenerMethodName)
                throws IntrospectionException {
        this(sourceClass, eventSetName, listenerType,
             new String[] { listenerMethodName },
             "add" + listenerClassName(listenerType),
             "remove" + listenerClassName(listenerType),
             "get" + listenerClassName(listenerType) + "s");
        String eventName = PropertyDescriptor.capitalize(eventSetName) + "Event";
        Method[] methods = getListenerMethods();
        if (methods.length > 0) {
            Class<?>[] args = methods[0].getParameterTypes();
            // Special case for vetoableChange, as in the JDK.
            if (!"vetoableChange".equals(eventSetName) && !args[0].getName().endsWith(eventName)) {
                throw new IntrospectionException("Method \"" + listenerMethodName
                    + "\" should have argument \"" + eventName + "\"");
            }
        }
    }

    private static String listenerClassName(Class<?> cls) {
        String className = cls.getName();
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Creates an {@code EventSetDescriptor} from scratch using
     * string names.
     *
     * @param sourceClass  The class firing the event.
     * @param eventSetName The programmatic name of the event set.
     * @param listenerType  The Class of the target interface that events
     *          will get delivered to.
     * @param listenerMethodNames The names of the methods that will get called
     *          when the event gets delivered to its target listener interface.
     * @param addListenerMethodName  The name of the method on the event source
     *          that can be used to register an event listener object.
     * @param removeListenerMethodName  The name of the method on the event source
     *          that can be used to de-register an event listener object.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public EventSetDescriptor(Class<?> sourceClass, String eventSetName,
                Class<?> listenerType, String listenerMethodNames[],
                String addListenerMethodName, String removeListenerMethodName)
                throws IntrospectionException {
        this(sourceClass, eventSetName, listenerType, listenerMethodNames,
             addListenerMethodName, removeListenerMethodName, null);
    }

    /**
     * This constructor creates an EventSetDescriptor from scratch using
     * string names.
     *
     * @param sourceClass  The class firing the event.
     * @param eventSetName The programmatic name of the event set.
     * @param listenerType  The Class of the target interface that events
     *          will get delivered to.
     * @param listenerMethodNames The names of the methods that will get called
     *          when the event gets delivered to its target listener interface.
     * @param addListenerMethodName  The name of the method on the event source
     *          that can be used to register an event listener object.
     * @param removeListenerMethodName  The name of the method on the event source
     *          that can be used to de-register an event listener object.
     * @param getListenerMethodName The method on the event source that
     *          can be used to access the array of event listener objects.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     * @since 1.4
     */
    public EventSetDescriptor(Class<?> sourceClass, String eventSetName,
                Class<?> listenerType, String listenerMethodNames[],
                String addListenerMethodName, String removeListenerMethodName,
                String getListenerMethodName)
                throws IntrospectionException {
        if (sourceClass == null || eventSetName == null || listenerType == null) {
            throw new NullPointerException();
        }
        setName(eventSetName);
        this.listenerType = listenerType;
        Method[] methods = new Method[listenerMethodNames.length];
        for (int i = 0; i < listenerMethodNames.length; i++) {
            if (listenerMethodNames[i] == null) {
                throw new NullPointerException();
            }
            methods[i] = find(listenerType, listenerMethodNames[i], 1, null);
            if (methods[i] == null) {
                throw new IntrospectionException("Method not found: " + listenerMethodNames[i]
                    + " on class " + listenerType.getName());
            }
        }
        this.listenerMethods = methods;
        addMethod = find(sourceClass, addListenerMethodName, 1, listenerType);
        if (addMethod == null) {
            throw new IntrospectionException("Method not found: " + addListenerMethodName);
        }
        removeMethod = find(sourceClass, removeListenerMethodName, 1, listenerType);
        if (removeMethod == null) {
            throw new IntrospectionException("Method not found: " + removeListenerMethodName);
        }
        if (getListenerMethodName != null) {
            getMethod = find(sourceClass, getListenerMethodName, 0, null);
        }
        unicast = throwsTooMany(addMethod);
    }

    private static Method find(Class<?> type, String name, int args, Class<?> arg) {
        if (name == null) {
            return null;
        }
        for (Method m : type.getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (m.getName().equals(name) && params.length == args && !m.isBridge()
                    && !Modifier.isStatic(m.getModifiers()) && (arg == null || params[0] == arg)) {
                return m;
            }
        }
        return null;
    }

    static boolean throwsTooMany(Method add) {
        for (Class<?> ex : add.getExceptionTypes()) {
            if (ex == java.util.TooManyListenersException.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates an {@code EventSetDescriptor} from scratch using
     * {@code java.lang.reflect.Method} and {@code java.lang.Class} objects.
     *
     * @param eventSetName The programmatic name of the event set.
     * @param listenerType The Class for the listener interface.
     * @param listenerMethods  An array of Method objects describing each
     *          of the event handling methods in the target listener.
     * @param addListenerMethod  The method on the event source
     *          that can be used to register an event listener object.
     * @param removeListenerMethod  The method on the event source
     *          that can be used to de-register an event listener object.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public EventSetDescriptor(String eventSetName, Class<?> listenerType, Method listenerMethods[],
                Method addListenerMethod, Method removeListenerMethod)
                throws IntrospectionException {
        this(eventSetName, listenerType, listenerMethods, addListenerMethod, removeListenerMethod, null);
    }

    /**
     * This constructor creates an EventSetDescriptor from scratch using
     * java.lang.reflect.Method and java.lang.Class objects.
     *
     * @param eventSetName The programmatic name of the event set.
     * @param listenerType The Class for the listener interface.
     * @param listenerMethods  An array of Method objects describing each
     *          of the event handling methods in the target listener.
     * @param addListenerMethod  The method on the event source
     *          that can be used to register an event listener object.
     * @param removeListenerMethod  The method on the event source
     *          that can be used to de-register an event listener object.
     * @param getListenerMethod The method on the event source
     *          that can be used to access the array of event listener objects.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     * @since 1.4
     */
    public EventSetDescriptor(String eventSetName, Class<?> listenerType, Method listenerMethods[],
                Method addListenerMethod, Method removeListenerMethod, Method getListenerMethod)
                throws IntrospectionException {
        setName(eventSetName);
        this.listenerType = listenerType;
        this.listenerMethods = listenerMethods != null ? listenerMethods.clone() : null;
        this.addMethod = addListenerMethod;
        this.removeMethod = removeListenerMethod;
        this.getMethod = getListenerMethod;
        this.unicast = addListenerMethod != null && throwsTooMany(addListenerMethod);
    }

    /**
     * Creates an {@code EventSetDescriptor} from scratch using
     * {@code java.lang.reflect.MethodDescriptor} and {@code java.lang.Class}
     * objects.
     *
     * @param eventSetName The programmatic name of the event set.
     * @param listenerType The Class for the listener interface.
     * @param listenerMethodDescriptors  An array of MethodDescriptor objects
     *           describing each of the event handling methods in the
     *           target listener.
     * @param addListenerMethod  The method on the event source
     *          that can be used to register an event listener object.
     * @param removeListenerMethod  The method on the event source
     *          that can be used to de-register an event listener object.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public EventSetDescriptor(String eventSetName, Class<?> listenerType,
                MethodDescriptor listenerMethodDescriptors[],
                Method addListenerMethod, Method removeListenerMethod)
                throws IntrospectionException {
        setName(eventSetName);
        this.listenerType = listenerType;
        this.listenerMethodDescriptors = listenerMethodDescriptors != null
            ? listenerMethodDescriptors.clone() : null;
        this.addMethod = addListenerMethod;
        this.removeMethod = removeListenerMethod;
        this.unicast = addListenerMethod != null && throwsTooMany(addListenerMethod);
    }

    /** Package-private merge used by the Introspector: {@code y} wins. */
    EventSetDescriptor(EventSetDescriptor x, EventSetDescriptor y) {
        super(x, y);
        listenerMethodDescriptors = y.listenerMethodDescriptors != null
            ? y.listenerMethodDescriptors : x.listenerMethodDescriptors;
        listenerMethods = y.listenerMethods != null ? y.listenerMethods : x.listenerMethods;
        listenerType = y.listenerType != null ? y.listenerType : x.listenerType;
        addMethod = y.addMethod != null ? y.addMethod : x.addMethod;
        removeMethod = y.removeMethod != null ? y.removeMethod : x.removeMethod;
        getMethod = y.getMethod != null ? y.getMethod : x.getMethod;
        unicast = x.unicast | y.unicast;
        inDefaultEventSet = x.inDefaultEventSet & y.inDefaultEventSet;
    }

    /**
     * Gets the {@code Class} object for the target interface.
     *
     * @return The Class object for the target interface that will
     * get invoked when the event is fired.
     */
    public Class<?> getListenerType() {
        return listenerType;
    }

    /**
     * Gets the methods of the target listener interface.
     *
     * @return An array of {@code Method} objects for the target methods
     * within the target listener interface that will get called when
     * events are fired.
     */
    public synchronized Method[] getListenerMethods() {
        if (listenerMethods == null && listenerMethodDescriptors != null) {
            Method[] methods = new Method[listenerMethodDescriptors.length];
            for (int i = 0; i < methods.length; i++) {
                methods[i] = listenerMethodDescriptors[i].getMethod();
            }
            listenerMethods = methods;
        }
        return listenerMethods != null ? listenerMethods.clone() : new Method[0];
    }

    /**
     * Gets the {@code MethodDescriptor}s of the target listener interface.
     *
     * @return An array of {@code MethodDescriptor} objects for the target methods
     * within the target listener interface that will get called when
     * events are fired.
     */
    public synchronized MethodDescriptor[] getListenerMethodDescriptors() {
        if (listenerMethodDescriptors == null && listenerMethods != null) {
            MethodDescriptor[] descriptors = new MethodDescriptor[listenerMethods.length];
            for (int i = 0; i < descriptors.length; i++) {
                descriptors[i] = new MethodDescriptor(listenerMethods[i]);
            }
            listenerMethodDescriptors = descriptors;
        }
        return listenerMethodDescriptors != null ? listenerMethodDescriptors.clone() : null;
    }

    /**
     * Gets the method used to add event listeners.
     *
     * @return The method used to register a listener at the event source.
     */
    public synchronized Method getAddListenerMethod() {
        return addMethod;
    }

    /**
     * Gets the method used to remove event listeners.
     *
     * @return The method used to remove a listener at the event source.
     */
    public synchronized Method getRemoveListenerMethod() {
        return removeMethod;
    }

    /**
     * Gets the method used to access the registered event listeners.
     *
     * @return The method used to access the array of listeners at the event
     *         source or null if it doesn't exist.
     * @since 1.4
     */
    public synchronized Method getGetListenerMethod() {
        return getMethod;
    }

    /**
     * Mark an event set as unicast (or not).
     *
     * @param unicast  True if the event set is unicast.
     */
    public void setUnicast(boolean unicast) {
        this.unicast = unicast;
    }

    /**
     * Normally event sources are multicast.  However there are some
     * exceptions that are strictly unicast.
     *
     * @return  {@code true} if the event set is unicast.
     *          Defaults to {@code false}.
     */
    public boolean isUnicast() {
        return unicast;
    }

    /**
     * Marks an event set as being in the &quot;default&quot; set (or not).
     * By default this is {@code true}.
     *
     * @param inDefaultEventSet {@code true} if the event set is in
     *                          the &quot;default&quot; set,
     *                          {@code false} if not
     */
    public void setInDefaultEventSet(boolean inDefaultEventSet) {
        this.inDefaultEventSet = inDefaultEventSet;
    }

    /**
     * Reports if an event set is in the &quot;default&quot; set.
     *
     * @return  {@code true} if the event set is in
     *          the &quot;default&quot; set.  Defaults to {@code true}.
     */
    public boolean isInDefaultEventSet() {
        return inDefaultEventSet;
    }

    void appendTo(StringBuilder sb) {
        appendTo(sb, "unicast", unicast);
        appendTo(sb, "inDefaultEventSet", inDefaultEventSet);
        appendTo(sb, "listenerType", listenerType);
        appendTo(sb, "getListenerMethod", getMethod);
        appendTo(sb, "addListenerMethod", addMethod);
        appendTo(sb, "removeListenerMethod", removeMethod);
    }
}
