/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.awt.datatransfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The SystemFlavorMap is a configurable map between "natives" (Strings), which
 * correspond to platform-specific data formats, and "flavors" (DataFlavors),
 * which correspond to platform-independent MIME types. This mapping is used
 * by the data transfer subsystem to transfer data between Java and native
 * applications, and between Java applications in separate VMs.
 * <p>
 * On Android the natives are the MIME types a {@code ClipDescription}
 * carries: {@code text/plain}, {@code text/html}, {@code image/png} and
 * {@code text/uri-list}, mapped to the text, HTML, image and file-list
 * flavors the Android clipboard bridge transfers. OpenJDK reads its table
 * from {@code flavormap.properties} and the platform's DataTransferer; neither
 * exists here, so the table is built in code. Flavors and natives outside it
 * follow the JDK rules for unknown entries: a flavor maps to its encoded
 * MIME type and an encoded native decodes back to its flavor.
 *
 * @since 1.2
 */
public final class SystemFlavorMap implements FlavorMap, FlavorTable {

    /**
     * Constant prefix used to tag Java types converted to native platform
     * type.
     */
    private static String JavaMIME = "JAVA_DATAFLAVOR:";

    private static final Object FLAVOR_MAP_KEY = new Object();

    private static volatile SystemFlavorMap defaultMap;

    /**
     * Maps native Strings to Lists of DataFlavors (or base type Strings for
     * text DataFlavors).
     */
    private final Map<String, LinkedHashSet<DataFlavor>> nativeToFlavor = new LinkedHashMap<>();

    /**
     * Maps DataFlavors (or base type Strings for text DataFlavors) to Lists of
     * native Strings.
     */
    private final Map<DataFlavor, LinkedHashSet<String>> flavorToNative = new LinkedHashMap<>();

    /**
     * Flavors and natives whose mappings were replaced by
     * {@link #setNativesForFlavor} or {@link #setFlavorsForNative}; the
     * implicit text and encoded mappings are not generated for them.
     */
    private final Set<Object> disabledMappingGenerationKeys = new HashSet<>();

    /**
     * Returns the default FlavorMap for this thread's ClassLoader.
     *
     * @return the default FlavorMap for this thread's ClassLoader
     */
    public static FlavorMap getDefaultFlavorMap() {
        SystemFlavorMap map = defaultMap;
        if (map == null) {
            synchronized (FLAVOR_MAP_KEY) {
                map = defaultMap;
                if (map == null) {
                    // One map for the process: the APK has a single
                    // application loader, where the JDK keys by context loader.
                    defaultMap = map = new SystemFlavorMap();
                }
            }
        }
        return map;
    }

    private SystemFlavorMap() {
        store("text/plain", DataFlavor.stringFlavor);
        store("text/plain", DataFlavor.getTextPlainUnicodeFlavor());
        store("text/plain", DataFlavor.plainTextFlavor);
        store("text/html", DataFlavor.allHtmlFlavor);
        store("text/html", DataFlavor.fragmentHtmlFlavor);
        store("text/html", DataFlavor.selectionHtmlFlavor);
        store("image/png", DataFlavor.imageFlavor);
        store("text/uri-list", DataFlavor.javaFileListFlavor);
    }

    private void store(String nat, DataFlavor flav) {
        if (flav == null) {
            return;
        }
        addTo(nativeToFlavor, nat, flav);
        addTo(flavorToNative, flav, nat);
    }

    private static <K, V> void addTo(Map<K, LinkedHashSet<V>> map, K key, V value) {
        LinkedHashSet<V> values = map.get(key);
        if (values == null) {
            values = new LinkedHashSet<>(1);
            map.put(key, values);
        }
        values.add(value);
    }

    /**
     * Returns a <code>List</code> of <code>String</code> natives to which the
     * specified <code>DataFlavor</code> can be translated by the data transfer
     * subsystem. The <code>List</code> will be sorted from best native to
     * worst. That is, the first native will best reflect data in the specified
     * flavor to the underlying native platform.
     * <p>
     * If the specified <code>DataFlavor</code> is previously unknown to the
     * data transfer subsystem and the data transfer subsystem is unable to
     * translate this <code>DataFlavor</code> to any existing native, then
     * invoking this method will establish a
     * mapping in both directions between the specified <code>DataFlavor</code>
     * and an encoded version of its MIME type as its native.
     *
     * @param flav the <code>DataFlavor</code> whose corresponding natives
     *        should be returned. If <code>null</code> is specified, all
     *        natives currently known to the data transfer subsystem are
     *        returned in a non-deterministic order.
     * @return a <code>java.util.List</code> of <code>java.lang.String</code>
     *         objects which are platform-specific representations of platform-
     *         specific data formats
     *
     * @see #encodeDataFlavor
     * @since 1.4
     */
    @Override
    public synchronized List<String> getNativesForFlavor(DataFlavor flav) {
        if (flav == null) {
            return new ArrayList<>(nativeToFlavor.keySet());
        }
        LinkedHashSet<String> natives = flavorToNative.get(flav);
        if (natives != null) {
            return new ArrayList<>(natives);
        }
        if (disabledMappingGenerationKeys.contains(flav)) {
            return new ArrayList<>();
        }
        List<String> retval = new ArrayList<>();
        String baseType = flav.getPrimaryType() + "/" + flav.getSubType();
        if (flav.isFlavorTextType() && nativeToFlavor.containsKey(baseType)) {
            // A text flavor in any charset or text representation travels as
            // its base MIME type, the way the JDK maps every text/plain
            // flavor to the platform's text natives.
            retval.add(baseType);
        } else if (flav.isFlavorJavaFileListType()) {
            retval.add("text/uri-list");
        }
        String encoded = encodeDataFlavor(flav);
        if (!retval.contains(encoded)) {
            retval.add(encoded);
        }
        for (String nat : retval) {
            addTo(flavorToNative, flav, nat);
        }
        addTo(nativeToFlavor, encoded, flav);
        return new ArrayList<>(retval);
    }

    /**
     * Returns a <code>List</code> of <code>DataFlavor</code>s to which the
     * specified <code>String</code> native can be translated by the data
     * transfer subsystem. The <code>List</code> will be sorted from best
     * <code>DataFlavor</code> to worst. That is, the first
     * <code>DataFlavor</code> will best reflect data in the specified
     * native to a Java application.
     * <p>
     * If the specified native is previously unknown to the data transfer
     * subsystem, and that native has been properly encoded, then invoking this
     * method will establish a mapping in both directions between the specified
     * native and a <code>DataFlavor</code> whose MIME type is a decoded
     * version of the native.
     * <p>
     * If the specified native is not a properly encoded native and the
     * mappings for this native have not been altered with
     * <code>setFlavorsForNative</code>, then the contents of the
     * <code>List</code> is platform dependent, but <code>null</code>
     * cannot be returned.
     *
     * @param nat the native whose corresponding <code>DataFlavor</code>s
     *        should be returned. If <code>null</code> is specified, all
     *        <code>DataFlavor</code>s currently known to the data transfer
     *        subsystem are returned in a non-deterministic order.
     * @return a <code>java.util.List</code> of <code>DataFlavor</code>
     *         objects into which platform-specific data in the specified,
     *         platform-specific native can be translated
     *
     * @see #encodeJavaMIMEType
     * @since 1.4
     */
    @Override
    public synchronized List<DataFlavor> getFlavorsForNative(String nat) {
        if (nat == null) {
            LinkedHashSet<DataFlavor> all = new LinkedHashSet<>(flavorToNative.keySet());
            for (LinkedHashSet<DataFlavor> flavors : nativeToFlavor.values()) {
                all.addAll(flavors);
            }
            return new ArrayList<>(all);
        }
        LinkedHashSet<DataFlavor> flavors = nativeToFlavor.get(nat);
        if (flavors != null) {
            return new ArrayList<>(flavors);
        }
        List<DataFlavor> retval = new ArrayList<>();
        if (!disabledMappingGenerationKeys.contains(nat) && isJavaMIMETypeEncoded(nat)) {
            try {
                DataFlavor flav = decodeDataFlavor(nat);
                if (flav != null) {
                    retval.add(flav);
                    addTo(nativeToFlavor, nat, flav);
                    addTo(flavorToNative, flav, nat);
                }
            } catch (ClassNotFoundException | IllegalArgumentException e) {
                // a native naming a class this process cannot load maps to nothing
            }
        }
        return retval;
    }

    /**
     * Returns a <code>Map</code> of the specified <code>DataFlavor</code>s to
     * their most preferred <code>String</code> native. Each native value will
     * be the same as the first native in the List returned by
     * <code>getNativesForFlavor</code> for the specified flavor.
     * <p>
     * If a specified <code>DataFlavor</code> is previously unknown to the
     * data transfer subsystem, then invoking this method will establish a
     * mapping in both directions between the specified <code>DataFlavor</code>
     * and an encoded version of its MIME type as its native.
     *
     * @param flavors an array of <code>DataFlavor</code>s which will be the
     *        key set of the returned <code>Map</code>. If <code>null</code> is
     *        specified, a mapping of all <code>DataFlavor</code>s known to the
     *        data transfer subsystem to their most preferred
     *        <code>String</code> natives will be returned.
     * @return a <code>java.util.Map</code> of <code>DataFlavor</code>s to
     *         <code>String</code> natives
     *
     * @see #getNativesForFlavor
     * @see #encodeDataFlavor
     */
    @Override
    public synchronized Map<DataFlavor,String> getNativesForFlavors(DataFlavor[] flavors)
    {
        // Use getNativesForFlavor to generate extra natives for text flavors
        // and stringFlavor

        if (flavors == null) {
            List<DataFlavor> flavor_list = getFlavorsForNative(null);
            flavors = new DataFlavor[flavor_list.size()];
            flavor_list.toArray(flavors);
        }

        Map<DataFlavor, String> retval = new HashMap<>(flavors.length, 1.0f);
        for (DataFlavor flavor : flavors) {
            List<String> natives = getNativesForFlavor(flavor);
            String nat = (natives.isEmpty()) ? null : natives.get(0);
            retval.put(flavor, nat);
        }

        return retval;
    }

    /**
     * Returns a <code>Map</code> of the specified <code>String</code> natives
     * to their most preferred <code>DataFlavor</code>. Each
     * <code>DataFlavor</code> value will be the same as the first
     * <code>DataFlavor</code> in the List returned by
     * <code>getFlavorsForNative</code> for the specified native.
     * <p>
     * If a specified native is previously unknown to the data transfer
     * subsystem, and that native has been properly encoded, then invoking this
     * method will establish a mapping in both directions between the specified
     * native and a <code>DataFlavor</code> whose MIME type is a decoded
     * version of the native.
     *
     * @param natives an array of <code>String</code>s which will be the
     *        key set of the returned <code>Map</code>. If <code>null</code> is
     *        specified, a mapping of all supported <code>String</code> natives
     *        to their most preferred <code>DataFlavor</code>s will be
     *        returned.
     * @return a <code>java.util.Map</code> of <code>String</code> natives to
     *         <code>DataFlavor</code>s
     *
     * @see #getFlavorsForNative
     * @see #encodeJavaMIMEType
     */
    @Override
    public synchronized Map<String,DataFlavor> getFlavorsForNatives(String[] natives)
    {
        // Use getFlavorsForNative to generate extra flavors for text natives
        if (natives == null) {
            List<String> nativesList = getNativesForFlavor(null);
            natives = new String[nativesList.size()];
            nativesList.toArray(natives);
        }

        Map<String, DataFlavor> retval = new HashMap<>(natives.length, 1.0f);
        for (String aNative : natives) {
            List<DataFlavor> flavors = getFlavorsForNative(aNative);
            DataFlavor flav = (flavors.isEmpty())? null : flavors.get(0);
            retval.put(aNative, flav);
        }
        return retval;
    }

    /**
     * Adds a mapping from the specified <code>DataFlavor</code> (and all
     * <code>DataFlavor</code>s equal to the specified <code>DataFlavor</code>)
     * to the specified <code>String</code> native.
     * Unlike <code>getNativesForFlavor</code>, the mapping will only be
     * established in one direction, and the native will not be encoded. To
     * establish a two-way mapping, call
     * <code>addFlavorForUnencodedNative</code> as well. The new mapping will
     * be of lower priority than any existing mapping.
     * This method has no effect if a mapping from the specified or equal
     * <code>DataFlavor</code> to the specified <code>String</code> native
     * already exists.
     *
     * @param flav the <code>DataFlavor</code> key for the mapping
     * @param nat the <code>String</code> native value for the mapping
     * @throws NullPointerException if flav or nat is <code>null</code>
     *
     * @see #addFlavorForUnencodedNative
     * @since 1.4
     */
    public synchronized void addUnencodedNativeForFlavor(DataFlavor flav,
                                                         String nat) {
        Objects.requireNonNull(nat, "Null native not permitted");
        Objects.requireNonNull(flav, "Null flavor not permitted");

        addTo(flavorToNative, flav, nat);
    }

    /**
     * Discards the current mappings for the specified <code>DataFlavor</code>
     * and all <code>DataFlavor</code>s equal to the specified
     * <code>DataFlavor</code>, and creates new mappings to the
     * specified <code>String</code> natives.
     * Unlike <code>getNativesForFlavor</code>, the mappings will only be
     * established in one direction, and the natives will not be encoded. To
     * establish two-way mappings, call <code>setFlavorsForNative</code>
     * as well. The first native in the array will represent the highest
     * priority mapping. Subsequent natives will represent mappings of
     * decreasing priority.
     * <p>
     * If the array contains several elements that reference equal
     * <code>String</code> natives, this method will establish new mappings
     * for the first of those elements and ignore the rest of them.
     * <p>
     * It is recommended that client code not reset mappings established by the
     * data transfer subsystem. This method should only be used for
     * application-level mappings.
     *
     * @param flav the <code>DataFlavor</code> key for the mappings
     * @param natives the <code>String</code> native values for the mappings
     * @throws NullPointerException if flav or natives is <code>null</code>
     *         or if natives contains <code>null</code> elements
     *
     * @see #setFlavorsForNative
     * @since 1.4
     */
    public synchronized void setNativesForFlavor(DataFlavor flav,
                                                 String[] natives) {
        Objects.requireNonNull(natives, "Null natives not permitted");
        Objects.requireNonNull(flav, "Null flavors not permitted");

        flavorToNative.remove(flav);
        for (String aNative : natives) {
            addUnencodedNativeForFlavor(flav, aNative);
        }
        disabledMappingGenerationKeys.add(flav);
    }

    /**
     * Adds a mapping from a single <code>String</code> native to a single
     * <code>DataFlavor</code>. Unlike <code>getFlavorsForNative</code>, the
     * mapping will only be established in one direction, and the native will
     * not be encoded. To establish a two-way mapping, call
     * <code>addUnencodedNativeForFlavor</code> as well. The new mapping will
     * be of lower priority than any existing mapping.
     * This method has no effect if a mapping from the specified
     * <code>String</code> native to the specified or equal
     * <code>DataFlavor</code> already exists.
     *
     * @param nat the <code>String</code> native key for the mapping
     * @param flav the <code>DataFlavor</code> value for the mapping
     * @throws NullPointerException if nat or flav is <code>null</code>
     *
     * @see #addUnencodedNativeForFlavor
     * @since 1.4
     */
    public synchronized void addFlavorForUnencodedNative(String nat,
                                                         DataFlavor flav) {
        Objects.requireNonNull(nat, "Null native not permitted");
        Objects.requireNonNull(flav, "Null flavor not permitted");

        addTo(nativeToFlavor, nat, flav);
    }

    /**
     * Discards the current mappings for the specified <code>String</code>
     * native, and creates new mappings to the specified
     * <code>DataFlavor</code>s. Unlike <code>getFlavorsForNative</code>, the
     * mappings will only be established in one direction, and the natives need
     * not be encoded. To establish two-way mappings, call
     * <code>setNativesForFlavor</code> as well. The first
     * <code>DataFlavor</code> in the array will represent the highest priority
     * mapping. Subsequent <code>DataFlavor</code>s will represent mappings of
     * decreasing priority.
     * <p>
     * If the array contains several elements that reference equal
     * <code>DataFlavor</code>s, this method will establish new mappings
     * for the first of those elements and ignore the rest of them.
     * <p>
     * It is recommended that client code not reset mappings established by the
     * data transfer subsystem. This method should only be used for
     * application-level mappings.
     *
     * @param nat the <code>String</code> native key for the mappings
     * @param flavors the <code>DataFlavor</code> values for the mappings
     * @throws NullPointerException if nat or flavors is <code>null</code>
     *         or if flavors contains <code>null</code> elements
     *
     * @see #setNativesForFlavor
     * @since 1.4
     */
    public synchronized void setFlavorsForNative(String nat,
                                                 DataFlavor[] flavors) {
        Objects.requireNonNull(nat, "Null native not permitted");
        Objects.requireNonNull(flavors, "Null flavors not permitted");

        nativeToFlavor.remove(nat);
        for (DataFlavor flavor : flavors) {
            addFlavorForUnencodedNative(nat, flavor);
        }
        disabledMappingGenerationKeys.add(nat);
    }

    /**
     * Encodes a MIME type for use as a <code>String</code> native. The format
     * of an encoded representation of a MIME type is implementation-dependent.
     * The only restrictions are:
     * <ul>
     * <li>The encoded representation is <code>null</code> if and only if the
     * MIME type <code>String</code> is <code>null</code>.</li>
     * <li>The encoded representations for two non-<code>null</code> MIME type
     * <code>String</code>s are equal if and only if these <code>String</code>s
     * are equal according to <code>String.equals(Object)</code>.</li>
     * </ul>
     * <p>
     * The reference implementation of this method returns the specified MIME
     * type <code>String</code> prefixed with <code>JAVA_DATAFLAVOR:</code>.
     *
     * @param mimeType the MIME type to encode
     * @return the encoded <code>String</code>, or <code>null</code> if
     *         mimeType is <code>null</code>
     */
    public static String encodeJavaMIMEType(String mimeType) {
        return (mimeType != null)
            ? JavaMIME + mimeType
            : null;
    }

    /**
     * Encodes a <code>DataFlavor</code> for use as a <code>String</code>
     * native. The format of an encoded <code>DataFlavor</code> is
     * implementation-dependent. The only restrictions are:
     * <ul>
     * <li>The encoded representation is <code>null</code> if and only if the
     * specified <code>DataFlavor</code> is <code>null</code> or its MIME type
     * <code>String</code> is <code>null</code>.</li>
     * <li>The encoded representations for two non-<code>null</code>
     * <code>DataFlavor</code>s with non-<code>null</code> MIME type
     * <code>String</code>s are equal if and only if the MIME type
     * <code>String</code>s of these <code>DataFlavor</code>s are equal
     * according to <code>String.equals(Object)</code>.</li>
     * </ul>
     * <p>
     * The reference implementation of this method returns the MIME type
     * <code>String</code> of the specified <code>DataFlavor</code> prefixed
     * with <code>JAVA_DATAFLAVOR:</code>.
     *
     * @param flav the <code>DataFlavor</code> to encode
     * @return the encoded <code>String</code>, or <code>null</code> if
     *         flav is <code>null</code> or has a <code>null</code> MIME type
     */
    public static String encodeDataFlavor(DataFlavor flav) {
        return (flav != null)
            ? SystemFlavorMap.encodeJavaMIMEType(flav.getMimeType())
            : null;
    }

    /**
     * Returns whether the specified <code>String</code> is an encoded Java
     * MIME type.
     *
     * @param str the <code>String</code> to test
     * @return <code>true</code> if the <code>String</code> is encoded;
     *         <code>false</code> otherwise
     */
    public static boolean isJavaMIMETypeEncoded(String str) {
        return (str != null && str.startsWith(JavaMIME, 0));
    }

    /**
     * Decodes a <code>String</code> native for use as a Java MIME type.
     *
     * @param nat the <code>String</code> to decode
     * @return the decoded Java MIME type, or <code>null</code> if nat is not
     *         an encoded <code>String</code> native
     */
    public static String decodeJavaMIMEType(String nat) {
        return (isJavaMIMETypeEncoded(nat))
            ? nat.substring(JavaMIME.length(), nat.length()).trim()
            : null;
    }

    /**
     * Decodes a <code>String</code> native for use as a
     * <code>DataFlavor</code>.
     *
     * @param nat the <code>String</code> to decode
     * @return the decoded <code>DataFlavor</code>, or <code>null</code> if
     *         nat is not an encoded <code>String</code> native
     * @throws ClassNotFoundException if the class of the data flavor
     * is not loaded
     */
    public static DataFlavor decodeDataFlavor(String nat)
        throws ClassNotFoundException
    {
        String retval_str = SystemFlavorMap.decodeJavaMIMEType(nat);
        return (retval_str != null)
            ? new DataFlavor(retval_str)
            : null;
    }
}
