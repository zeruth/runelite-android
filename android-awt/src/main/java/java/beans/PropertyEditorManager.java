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

import java.util.HashMap;
import java.util.Map;

/**
 * The PropertyEditorManager can be used to locate a property editor for
 * any given type name. Lookup order: an editor registered with
 * {@link #registerEditor}, the built-in editors for the primitive types,
 * their wrappers, {@code String} and enums (the JDK's {@code sun.beans.editors}
 * set, minus the AWT-component Color/Font editors), a class named
 * {@code <type>Editor} next to the type, and finally {@code <Type>Editor}
 * in each package of the editor search path.
 */
public class PropertyEditorManager {
    private static final Map<Class<?>, Class<?>> registry = new HashMap<>();
    private static String[] searchPath = {"sun.beans.editors"};

    /**
     * Registers an editor class to edit values of the given target class.
     * If the editor class is {@code null}, then any existing definition
     * will be removed.
     *
     * @param targetType   the class object of the type to be edited
     * @param editorClass  the class object of the editor class
     */
    public static void registerEditor(Class<?> targetType, Class<?> editorClass) {
        synchronized (registry) {
            if (editorClass == null) {
                registry.remove(targetType);
            } else {
                registry.put(targetType, editorClass);
            }
        }
    }

    /**
     * Locate a value editor for a given target type.
     *
     * @param targetType  the Class object for the type to be edited
     * @return An editor object for the given target class.
     * The result is null if no suitable editor can be found.
     */
    public static PropertyEditor findEditor(Class<?> targetType) {
        Class<?> editorClass;
        synchronized (registry) {
            editorClass = registry.get(targetType);
        }
        if (editorClass != null) {
            PropertyEditor editor = instantiate(editorClass);
            if (editor != null) {
                return editor;
            }
        }
        PropertyEditor builtin = Builtin.forType(targetType);
        if (builtin != null) {
            return builtin;
        }
        String name = targetType.getName() + "Editor";
        PropertyEditor editor = load(name, targetType.getClassLoader());
        if (editor != null) {
            return editor;
        }
        String simple = name.substring(name.lastIndexOf('.') + 1);
        for (String path : getEditorSearchPath()) {
            editor = load(path + '.' + simple, targetType.getClassLoader());
            if (editor != null) {
                return editor;
            }
        }
        return null;
    }

    private static PropertyEditor load(String name, ClassLoader loader) {
        try {
            return instantiate(Class.forName(name, true,
                loader != null ? loader : PropertyEditorManager.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static PropertyEditor instantiate(Class<?> type) {
        if (!PropertyEditor.class.isAssignableFrom(type)) {
            return null;
        }
        try {
            return (PropertyEditor) type.getConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the package names that will be searched for property editors.
     *
     * @return  The array of package names that will be searched in
     *          order to find property editors.
     */
    public static synchronized String[] getEditorSearchPath() {
        return searchPath.clone();
    }

    /**
     * Change the list of package names that will be used for
     * finding property editors.
     *
     * @param path  Array of package names.
     */
    public static synchronized void setEditorSearchPath(String[] path) {
        searchPath = path == null ? new String[0] : path.clone();
    }

    /** The JDK's sun.beans.editors behaviour for the non-AWT types. */
    private static final class Builtin extends PropertyEditorSupport {
        private final Class<?> type;

        private Builtin(Class<?> type) {
            this.type = type;
        }

        static PropertyEditor forType(Class<?> type) {
            if (type.isEnum()) {
                return new Builtin(type);
            }
            if (type == String.class || box(type) != null) {
                return new Builtin(box(type) != null ? box(type) : type);
            }
            return null;
        }

        private static Class<?> box(Class<?> type) {
            if (type == Boolean.class || type == Boolean.TYPE) return Boolean.class;
            if (type == Byte.class || type == Byte.TYPE) return Byte.class;
            if (type == Short.class || type == Short.TYPE) return Short.class;
            if (type == Integer.class || type == Integer.TYPE) return Integer.class;
            if (type == Long.class || type == Long.TYPE) return Long.class;
            if (type == Float.class || type == Float.TYPE) return Float.class;
            if (type == Double.class || type == Double.TYPE) return Double.class;
            return null;
        }

        @Override
        public void setValue(Object value) {
            if (value != null && !type.isInstance(value)) {
                throw new IllegalArgumentException("Unsupported value: " + value);
            }
            super.setValue(value);
        }

        @Override
        public String getJavaInitializationString() {
            Object value = getValue();
            if (value == null) {
                return "null";
            }
            if (type.isEnum()) {
                return type.getName().replace('$', '.') + '.' + ((Enum<?>) value).name();
            }
            if (type == String.class) {
                StringBuilder sb = new StringBuilder("\"");
                for (char c : ((String) value).toCharArray()) {
                    switch (c) {
                        case '\b': sb.append("\\b"); break;
                        case '\t': sb.append("\\t"); break;
                        case '\n': sb.append("\\n"); break;
                        case '\f': sb.append("\\f"); break;
                        case '\r': sb.append("\\r"); break;
                        case '\"': sb.append("\\\""); break;
                        case '\\': sb.append("\\\\"); break;
                        default:
                            if (c < ' ' || c > '~') {
                                sb.append(String.format("\\u%04X", (int) c));
                            } else {
                                sb.append(c);
                            }
                    }
                }
                return sb.append('"').toString();
            }
            if (type == Boolean.class) return ((Boolean) value) ? "Boolean.TRUE" : "Boolean.FALSE";
            if (type == Byte.class) return "((byte)" + value + ")";
            if (type == Short.class) return "((short)" + value + ")";
            if (type == Long.class) return value + "L";
            if (type == Float.class) return value + "F";
            return value.toString();
        }

        @Override
        public String getAsText() {
            Object value = getValue();
            if (value == null) {
                return null;
            }
            if (type == Boolean.class) {
                return ((Boolean) value) ? "True" : "False";
            }
            if (type.isEnum()) {
                return ((Enum<?>) value).name();
            }
            return value.toString();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void setAsText(String text) throws IllegalArgumentException {
            if (text == null) {
                setValue(null);
            } else if (type == String.class) {
                setValue(text);
            } else if (type == Boolean.class) {
                if ("true".equalsIgnoreCase(text)) setValue(Boolean.TRUE);
                else if ("false".equalsIgnoreCase(text)) setValue(Boolean.FALSE);
                else throw new IllegalArgumentException(text);
            } else if (type.isEnum()) {
                setValue(Enum.valueOf((Class) type, text));
            } else if (type == Byte.class) {
                setValue(Byte.decode(text));
            } else if (type == Short.class) {
                setValue(Short.decode(text));
            } else if (type == Integer.class) {
                setValue(Integer.decode(text));
            } else if (type == Long.class) {
                setValue(Long.decode(text));
            } else if (type == Float.class) {
                setValue(Float.valueOf(text));
            } else {
                setValue(Double.valueOf(text));
            }
        }

        @Override
        public String[] getTags() {
            if (type == Boolean.class) {
                return new String[] {"True", "False"};
            }
            if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                String[] tags = new String[constants.length];
                for (int i = 0; i < constants.length; i++) {
                    tags[i] = ((Enum<?>) constants[i]).name();
                }
                return tags;
            }
            return null;
        }
    }
}
