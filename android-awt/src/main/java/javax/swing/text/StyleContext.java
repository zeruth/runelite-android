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
package javax.swing.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * A pool of styles and their associated resources.  This class determines
 * the lifetime of a group of resources by being a container that holds
 * caches for various resources such as font and color that get reused
 * by the various style definitions.  This can be shared by multiple
 * documents if desired to maximize the sharing of related resources.
 * <p>
 * This class also provides efficient support for small sets of attributes
 * and compresses them by recycling commonly used sets.  This is done by
 * {@link SmallAttributeSet}, immutable sets that are kept unique in a
 * weak pool, so equal small sets are the same object.
 * <p>
 * Port note: OpenJDK routes fonts that miss the default encoding through
 * {@code sun.font.FontUtilities.getCompositeFontUIResource}; the shadow's
 * fonts already fall back through the Android registry, so {@link #getFont}
 * returns the plain {@code Font}.
 *
 * @author  Timothy Prinzing
 */
@SuppressWarnings("serial")
public class StyleContext implements Serializable, AbstractDocument.AttributeContext {

    /**
     * Returns default AttributeContext shared by all documents that
     * don't bother to define/supply their own context.
     *
     * @return the context
     */
    public static final StyleContext getDefaultStyleContext() {
        if (defaultContext == null) {
            defaultContext = new StyleContext();
        }
        return defaultContext;
    }

    private static StyleContext defaultContext;

    /**
     * Creates a new StyleContext object.
     */
    public StyleContext() {
        styles = new NamedStyle(null);
        addStyle(DEFAULT_STYLE, null);
    }

    /**
     * Adds a new style into the style hierarchy.  Style attributes
     * resolve from bottom up so an attribute specified in a child
     * will override an attribute specified in the parent.
     *
     * @param nm   the name of the style (must be unique within the
     *   collection of named styles in the document).  The name may
     *   be null if the style is unnamed, but the caller is responsible
     *   for managing the reference returned as an unnamed style can't
     *   be fetched by name.  An unnamed style may be useful for things
     *   like character attribute overrides such as found in a style
     *   run.
     * @param parent the parent style.  This may be null if unspecified
     *   attributes need not be resolved in some other style.
     * @return the created style
     */
    public Style addStyle(String nm, Style parent) {
        Style style = new NamedStyle(nm, parent);
        if (nm != null) {
            // add a named style, a class of attributes
            styles.addAttribute(nm, style);
        }
        return style;
    }

    /**
     * Removes a named style previously added to the document.
     *
     * @param nm  the name of the style to remove
     */
    public void removeStyle(String nm) {
        styles.removeAttribute(nm);
    }

    /**
     * Fetches a named style previously added to the document
     *
     * @param nm  the name of the style
     * @return the style
     */
    public Style getStyle(String nm) {
        return (Style) styles.getAttribute(nm);
    }

    /**
     * Fetches the names of the styles defined.
     *
     * @return the list of names as an enumeration
     */
    public Enumeration<?> getStyleNames() {
        return styles.getAttributeNames();
    }

    /**
     * Adds a listener to track when styles are added
     * or removed.
     *
     * @param l the change listener
     */
    public void addChangeListener(ChangeListener l) {
        styles.addChangeListener(l);
    }

    /**
     * Removes a listener that was tracking styles being
     * added or removed.
     *
     * @param l the change listener
     */
    public void removeChangeListener(ChangeListener l) {
        styles.removeChangeListener(l);
    }

    /**
     * Returns an array of all the <code>ChangeListener</code>s added
     * to this StyleContext with addChangeListener().
     *
     * @return all of the <code>ChangeListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public ChangeListener[] getChangeListeners() {
        return ((NamedStyle) styles).getChangeListeners();
    }

    /**
     * Gets the font from an attribute set.  This is
     * implemented to try and fetch a cached font
     * for the given AttributeSet, and if that fails
     * the font features are resolved and the
     * font is fetched from the low-level font cache.
     *
     * @param attr the attribute set
     * @return the font
     */
    public Font getFont(AttributeSet attr) {
        // PENDING(prinz) add cache behavior
        int style = Font.PLAIN;
        if (StyleConstants.isBold(attr)) {
            style |= Font.BOLD;
        }
        if (StyleConstants.isItalic(attr)) {
            style |= Font.ITALIC;
        }
        String family = StyleConstants.getFontFamily(attr);
        int size = StyleConstants.getFontSize(attr);

        /**
         * if either superscript or subscript is
         * is set, we need to reduce the font size
         * by 2.
         */
        if (StyleConstants.isSuperscript(attr) || StyleConstants.isSubscript(attr)) {
            size -= 2;
        }

        return getFont(family, style, size);
    }

    /**
     * Takes a set of attributes and turn it into a foreground color
     * specification.  This might be used to specify things
     * like brighter, more hue, etc.  By default it simply returns
     * the value specified by the StyleConstants.Foreground attribute.
     *
     * @param attr the set of attributes
     * @return the color
     */
    public Color getForeground(AttributeSet attr) {
        return StyleConstants.getForeground(attr);
    }

    /**
     * Takes a set of attributes and turn it into a background color
     * specification.  This might be used to specify things
     * like brighter, more hue, etc.  By default it simply returns
     * the value specified by the StyleConstants.Background attribute.
     *
     * @param attr the set of attributes
     * @return the color
     */
    public Color getBackground(AttributeSet attr) {
        return StyleConstants.getBackground(attr);
    }

    /**
     * Gets a new font.  This returns a Font from a cache
     * if a cached font exists.  If not, a Font is added to
     * the cache.  This is basically a low-level cache for
     * 1.1 font features.
     *
     * @param family the font family (such as "Monospaced")
     * @param style the style of the font (such as Font.PLAIN)
     * @param size the point size &gt;= 1
     * @return the new font
     */
    public Font getFont(String family, int style, int size) {
        fontSearch.setValue(family, style, size);
        Font f = fontTable.get(fontSearch);
        if (f == null) {
            // haven't seen this one yet.
            Style defaultStyle = getStyle(StyleContext.DEFAULT_STYLE);
            if (defaultStyle != null) {
                final String FONT_ATTRIBUTE_KEY = "FONT_ATTRIBUTE_KEY";
                Font defaultFont = (Font) defaultStyle.getAttribute(FONT_ATTRIBUTE_KEY);
                if (defaultFont != null && defaultFont.getFamily().equalsIgnoreCase(family)) {
                    f = defaultFont.deriveFont(style, size);
                }
            }
            if (f == null) {
                f = new Font(family, style, size);
            }
            FontKey key = new FontKey(family, style, size);
            fontTable.put(key, f);
        }
        return f;
    }

    /**
     * Returns font metrics for a font.
     *
     * @param f the font
     * @return the metrics
     */
    public FontMetrics getFontMetrics(Font f) {
        // The Toolkit implementations cache, so we just forward
        // to the default toolkit.
        return Toolkit.getDefaultToolkit().getFontMetrics(f);
    }

    // --- AttributeContext methods --------------------

    /**
     * Adds an attribute to the given set, and returns
     * the new representative set.
     * <p>
     *
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param old the old attribute set
     * @param name the non-null attribute name
     * @param value the attribute value
     * @return the updated attribute set
     * @see MutableAttributeSet#addAttribute
     */
    public synchronized AttributeSet addAttribute(AttributeSet old, Object name, Object value) {
        if ((old.getAttributeCount() + 1) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.addAttribute(name, value);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.addAttribute(name, value);
        return ma;
    }

    /**
     * Adds a set of attributes to the element.
     * <p>
     *
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param old the old attribute set
     * @param attr the attributes to add
     * @return the updated attribute set
     * @see MutableAttributeSet#addAttribute
     */
    public synchronized AttributeSet addAttributes(AttributeSet old, AttributeSet attr) {
        if ((old.getAttributeCount() + attr.getAttributeCount()) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.addAttributes(attr);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.addAttributes(attr);
        return ma;
    }

    /**
     * Removes an attribute from the set.
     * <p>
     *
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param old the old set of attributes
     * @param name the non-null attribute name
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttribute
     */
    public synchronized AttributeSet removeAttribute(AttributeSet old, Object name) {
        if ((old.getAttributeCount() - 1) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttribute(name);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttribute(name);
        return ma;
    }

    /**
     * Removes a set of attributes for the element.
     * <p>
     *
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param old the old attribute set
     * @param names the attribute names
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttributes
     */
    public synchronized AttributeSet removeAttributes(AttributeSet old, Enumeration<?> names) {
        if (old.getAttributeCount() <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttributes(names);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttributes(names);
        return ma;
    }

    /**
     * Removes a set of attributes for the element.
     * <p>
     *
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param old the old attribute set
     * @param attrs the attributes
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttributes
     */
    public synchronized AttributeSet removeAttributes(AttributeSet old, AttributeSet attrs) {
        if (old.getAttributeCount() <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttributes(attrs);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttributes(attrs);
        return ma;
    }

    /**
     * Fetches an empty AttributeSet.
     *
     * @return the set
     */
    public AttributeSet getEmptySet() {
        return SimpleAttributeSet.EMPTY;
    }

    /**
     * Returns a set no longer needed by the MutableAttributeSet implementation.
     * This is useful for operation under 1.1 where there are no weak
     * references.  This would typically be called by the finalize method
     * of the MutableAttributeSet implementation.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not.
     *
     * @param a the set to reclaim
     */
    public void reclaim(AttributeSet a) {
        if (SwingUtilities.isEventDispatchThread()) {
            attributesPool.size(); // force WeakHashMap to expunge stale entries
        }
        // if current thread is not event dispatching thread
        // do not bother with expunging stale entries.
    }

    // --- local methods -----------------------------------------------

    /**
     * Returns the maximum number of key/value pairs to try and
     * compress into unique/immutable sets.  Any sets above this
     * limit will use hashtables and be a MutableAttributeSet.
     *
     * @return the threshold
     */
    protected int getCompressionThreshold() {
        return THRESHOLD;
    }

    /**
     * Create a compact set of attributes that might be shared.
     * This is a hook for subclasses that want to alter the
     * behavior of SmallAttributeSet.  This can be reimplemented
     * to return an AttributeSet that provides some sort of
     * attribute conversion.
     *
     * @param a The set of attributes to be represented in the
     *  the compact form.
     * @return the small set
     */
    protected SmallAttributeSet createSmallAttributeSet(AttributeSet a) {
        return new SmallAttributeSet(a);
    }

    /**
     * Create a large set of attributes that should trade off
     * space for time.  This set will not be shared.  This is
     * a hook for subclasses that want to alter the behavior
     * of the larger attribute storage format (which is
     * SimpleAttributeSet by default).   This can be reimplemented
     * to return a MutableAttributeSet that provides some sort of
     * attribute conversion.
     *
     * @param a The set of attributes to be represented in the
     *  the larger form.
     * @return the large set
     */
    protected MutableAttributeSet createLargeAttributeSet(AttributeSet a) {
        return new SimpleAttributeSet(a);
    }

    /**
     * Clean the unused immutable sets out of the hashtable.
     */
    synchronized void removeUnusedSets() {
        attributesPool.size(); // force WeakHashMap to expunge stale entries
    }

    /**
     * Search for an existing attribute set using the current search
     * parameters.  If a matching set is found, return it.  If a match
     * is not found, we create a new set and add it to the pool.
     */
    AttributeSet getImmutableUniqueSet() {
        // PENDING(prinz) should consider finding a alternative to
        // generating extra garbage on search key.
        SmallAttributeSet key = createSmallAttributeSet(search);
        WeakReference<SmallAttributeSet> reference = attributesPool.get(key);
        SmallAttributeSet a;
        if (reference == null || (a = reference.get()) == null) {
            a = key;
            attributesPool.put(a, new WeakReference<SmallAttributeSet>(a));
        }
        return a;
    }

    /**
     * Creates a mutable attribute set to hand out because the current
     * needs are too big to try and use a shared version.
     */
    MutableAttributeSet getMutableAttributeSet(AttributeSet a) {
        if (a instanceof MutableAttributeSet && a != SimpleAttributeSet.EMPTY) {
            return (MutableAttributeSet) a;
        }
        return createLargeAttributeSet(a);
    }

    /**
     * Converts a StyleContext to a String.
     *
     * @return the string
     */
    public String toString() {
        removeUnusedSets();
        String s = "";
        for (SmallAttributeSet set : attributesPool.keySet()) {
            s = s + set + "\n";
        }
        return s;
    }

    // --- serialization ---------------------------------------------

    /**
     * Context-specific handling of writing out attributes
     *
     * @param out the output stream
     * @param a the attribute set
     * @exception IOException on any I/O error
     */
    public void writeAttributes(ObjectOutputStream out, AttributeSet a) throws IOException {
        writeAttributeSet(out, a);
    }

    /**
     * Context-specific handling of reading in attributes
     *
     * @param in the object stream to read the attribute data from.
     * @param a  the attribute set to place the attribute
     *   definitions in.
     * @exception ClassNotFoundException passed upward if encountered
     *  when reading the object stream.
     * @exception IOException passed upward if encountered when
     *  reading the object stream.
     */
    public void readAttributes(ObjectInputStream in, MutableAttributeSet a)
        throws ClassNotFoundException, IOException {
        readAttributeSet(in, a);
    }

    /**
     * Writes a set of attributes to the given object stream
     * for the purpose of serialization.  This will take
     * special care to deal with static attribute keys that
     * have been registered wit the
     * <code>registerStaticAttributeKey</code> method.
     * Any attribute key not registered as a static key
     * will be serialized directly.  All values are expected
     * to be serializable.
     *
     * @param out the output stream
     * @param a the attribute set
     * @exception IOException on any I/O error
     */
    public static void writeAttributeSet(ObjectOutputStream out, AttributeSet a) throws IOException {
        int n = a.getAttributeCount();
        out.writeInt(n);
        Enumeration<?> keys = a.getAttributeNames();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key instanceof Serializable) {
                out.writeObject(key);
            } else {
                Object ioFmt = freezeKeyMap.get(key);
                if (ioFmt == null) {
                    throw new NotSerializableException(key.getClass().getName()
                        + " is not serializable as a key in an AttributeSet");
                }
                out.writeObject(ioFmt);
            }
            Object value = a.getAttribute(key);
            Object ioFmt = freezeKeyMap.get(value);
            if (value instanceof Serializable) {
                out.writeObject((ioFmt != null) ? ioFmt : value);
            } else {
                if (ioFmt == null) {
                    throw new NotSerializableException(value.getClass().getName()
                        + " is not serializable as a value in an AttributeSet");
                }
                out.writeObject(ioFmt);
            }
        }
    }

    /**
     * Reads a set of attributes from the given object input
     * stream that have been previously written out with
     * <code>writeAttributeSet</code>.  This will try to restore
     * keys that were static objects to the static objects in
     * the current virtual machine considering only those keys
     * that have been registered with the
     * <code>registerStaticAttributeKey</code> method.
     * The attributes retrieved from the stream will be placed
     * into the given mutable set.
     *
     * @param in the object stream to read the attribute data from.
     * @param a  the attribute set to place the attribute
     *   definitions in.
     * @exception ClassNotFoundException passed upward if encountered
     *  when reading the object stream.
     * @exception IOException passed upward if encountered when
     *  reading the object stream.
     */
    public static void readAttributeSet(ObjectInputStream in, MutableAttributeSet a)
        throws ClassNotFoundException, IOException {

        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            Object key = in.readObject();
            Object value = in.readObject();
            if (thawKeyMap != null) {
                Object staticKey = thawKeyMap.get(key);
                if (staticKey != null) {
                    key = staticKey;
                }
                Object staticValue = thawKeyMap.get(value);
                if (staticValue != null) {
                    value = staticValue;
                }
            }
            a.addAttribute(key, value);
        }
    }

    /**
     * Registers an object as a static object that is being
     * used as a key in attribute sets.  This allows the key
     * to be treated specially for serialization.
     * <p>
     * For operation under a 1.1 virtual machine, this
     * uses the value returned by <code>toString</code>
     * concatenated to the classname.  The value returned
     * by toString should not have any class reference
     * information embedded in it (ie it should be reimplemented
     * from the definition in Object) in order to be the
     * same when recomputed later.
     *
     * @param key the non-null object key
     */
    public static void registerStaticAttributeKey(Object key) {
        String ioFmt = key.getClass().getName() + "." + key.toString();
        if (freezeKeyMap == null) {
            freezeKeyMap = new Hashtable<Object, String>();
            thawKeyMap = new Hashtable<String, Object>();
        }
        freezeKeyMap.put(key, ioFmt);
        thawKeyMap.put(ioFmt, key);
    }

    /**
     * Returns the object previously registered with
     * <code>registerStaticAttributeKey</code>.
     *
     * @param key the object key
     * @return Returns the object previously registered with
     * {@code registerStaticAttributeKey}
     */
    public static Object getStaticAttribute(Object key) {
        if (thawKeyMap == null || key == null) {
            return null;
        }
        return thawKeyMap.get(key);
    }

    /**
     * Returns the String that <code>key</code> will be registered with.
     *
     * @see #getStaticAttribute
     * @see #registerStaticAttributeKey
     * @param key the object key
     * @return the String {@code key} will be registered with
     */
    public static Object getStaticAttributeKey(Object key) {
        return key.getClass().getName() + "." + key.toString();
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        // clean out unused sets before saving
        removeUnusedSets();

        s.defaultWriteObject();
    }

    private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
        fontSearch = new FontKey(null, 0, 0);
        fontTable = new Hashtable<FontKey, Font>();
        search = new SimpleAttributeSet();
        attributesPool = Collections.synchronizedMap(new WeakHashMap<SmallAttributeSet, WeakReference<SmallAttributeSet>>());
        s.defaultReadObject();
    }

    // --- variables ---------------------------------------------------

    /**
     * The name given to the default logical style attached
     * to paragraphs.
     */
    public static final String DEFAULT_STYLE = "default";

    private static Hashtable<Object, String> freezeKeyMap;
    private static Hashtable<String, Object> thawKeyMap;

    private Style styles;
    private transient FontKey fontSearch = new FontKey(null, 0, 0);
    private transient Hashtable<FontKey, Font> fontTable = new Hashtable<FontKey, Font>();

    private transient Map<SmallAttributeSet, WeakReference<SmallAttributeSet>> attributesPool =
        Collections.synchronizedMap(new WeakHashMap<SmallAttributeSet, WeakReference<SmallAttributeSet>>());
    private transient MutableAttributeSet search = new SimpleAttributeSet();

    /**
     * Number of immutable sets that are not currently
     * being used.  This helps indicate when the sets need
     * to be cleaned out of the hashtable they are stored
     * in.
     */
    private int unusedSets;

    /**
     * The threshold for no longer sharing the set of attributes
     * in an immutable table.
     */
    static final int THRESHOLD = 9;

    /**
     * This class holds a small number of attributes in an array.
     * The storage format is key, value, key, value, etc.  The size
     * of the set is the length of the array divided by two.  By
     * default, this is the class that will be used to store attributes
     * when held in the compact sharable form.
     */
    public class SmallAttributeSet implements AttributeSet {

        public SmallAttributeSet(Object[] attributes) {
            this.attributes = attributes;
            updateResolveParent();
        }

        public SmallAttributeSet(AttributeSet attrs) {
            int n = attrs.getAttributeCount();
            Object[] tbl = new Object[2 * n];
            Enumeration<?> names = attrs.getAttributeNames();
            int i = 0;
            while (names.hasMoreElements()) {
                tbl[i] = names.nextElement();
                tbl[i + 1] = attrs.getAttribute(tbl[i]);
                i += 2;
            }
            attributes = tbl;
            updateResolveParent();
        }

        private void updateResolveParent() {
            resolveParent = null;
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (tbl[i] == StyleConstants.ResolveAttribute) {
                    resolveParent = (AttributeSet) tbl[i + 1];
                    break;
                }
            }
        }

        Object getLocalAttribute(Object nm) {
            if (nm == StyleConstants.ResolveAttribute) {
                return resolveParent;
            }
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (nm.equals(tbl[i])) {
                    return tbl[i + 1];
                }
            }
            return null;
        }

        // --- Object methods -------------------------

        /**
         * Returns a string showing the key/value pairs
         */
        public String toString() {
            String s = "{";
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (tbl[i + 1] instanceof AttributeSet) {
                    // don't recurse
                    s = s + tbl[i] + "=" + "AttributeSet" + ",";
                } else {
                    s = s + tbl[i] + "=" + tbl[i + 1] + ",";
                }
            }
            s = s + "}";
            return s;
        }

        /**
         * Returns a hashcode for this set of attributes.
         * @return     a hashcode value for this set of attributes.
         */
        public int hashCode() {
            int code = 0;
            Object[] tbl = attributes;
            for (int i = 1; i < tbl.length; i += 2) {
                code ^= tbl[i].hashCode();
            }
            return code;
        }

        /**
         * Compares this object to the specified object.
         * The result is <code>true</code> if the object is an equivalent
         * set of attributes.
         * @param     obj   the object to compare with.
         * @return    <code>true</code> if the objects are equal;
         *            <code>false</code> otherwise.
         */
        public boolean equals(Object obj) {
            if (obj instanceof AttributeSet) {
                AttributeSet attrs = (AttributeSet) obj;
                return ((getAttributeCount() == attrs.getAttributeCount()) && containsAttributes(attrs));
            }
            return false;
        }

        /**
         * Clones a set of attributes.  Since the set is immutable, a
         * clone is basically the same set.
         *
         * @return the set of attributes
         */
        public Object clone() {
            return this;
        }

        //  --- AttributeSet methods ----------------------------

        public int getAttributeCount() {
            return attributes.length / 2;
        }

        public boolean isDefined(Object key) {
            Object[] a = attributes;
            int n = a.length;
            for (int i = 0; i < n; i += 2) {
                if (key.equals(a[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean isEqual(AttributeSet attr) {
            if (attr instanceof SmallAttributeSet) {
                return attr == this;
            }
            return ((getAttributeCount() == attr.getAttributeCount()) && containsAttributes(attr));
        }

        public AttributeSet copyAttributes() {
            return this;
        }

        public Object getAttribute(Object key) {
            Object value = getLocalAttribute(key);
            if (value == null) {
                AttributeSet parent = getResolveParent();
                if (parent != null) {
                    value = parent.getAttribute(key);
                }
            }
            return value;
        }

        public Enumeration<?> getAttributeNames() {
            return new KeyEnumeration(attributes);
        }

        public boolean containsAttribute(Object name, Object value) {
            return value.equals(getAttribute(name));
        }

        public boolean containsAttributes(AttributeSet attrs) {
            boolean result = true;

            Enumeration<?> names = attrs.getAttributeNames();
            while (result && names.hasMoreElements()) {
                Object name = names.nextElement();
                result = attrs.getAttribute(name).equals(getAttribute(name));
            }

            return result;
        }

        public AttributeSet getResolveParent() {
            return resolveParent;
        }

        // --- variables -----------------------------------------

        Object[] attributes;
        // This is also stored in attributes
        AttributeSet resolveParent;
    }

    /**
     * An enumeration of the keys in a SmallAttributeSet.
     */
    class KeyEnumeration implements Enumeration<Object> {

        KeyEnumeration(Object[] attr) {
            this.attr = attr;
            i = 0;
        }

        public boolean hasMoreElements() {
            return i < attr.length;
        }

        public Object nextElement() {
            if (i < attr.length) {
                Object o = attr[i];
                i += 2;
                return o;
            }
            throw new NoSuchElementException();
        }

        Object[] attr;
        int i;
    }

    /**
     * key for a font table
     */
    static class FontKey {

        private String family;
        private int style;
        private int size;

        /**
         * Constructs a font key.
         */
        public FontKey(String family, int style, int size) {
            setValue(family, style, size);
        }

        public void setValue(String family, int style, int size) {
            this.family = (family != null) ? family.intern() : null;
            this.style = style;
            this.size = size;
        }

        public int hashCode() {
            int fhash = (family != null) ? family.hashCode() : 0;
            return fhash ^ style ^ size;
        }

        public boolean equals(Object obj) {
            if (obj instanceof FontKey) {
                FontKey font = (FontKey) obj;
                return (size == font.size) && (style == font.style) && (family == font.family);
            }
            return false;
        }

    }

    /**
     * A collection of attributes, typically used to represent
     * character and paragraph styles.  This is an implementation
     * of MutableAttributeSet that can be observed if desired.
     * These styles will take advantage of immutability while
     * the sets are small enough, and may be substantially more
     * efficient than something like SimpleAttributeSet.
     */
    public class NamedStyle implements Style, Serializable {

        /**
         * Creates a new named style.
         *
         * @param name the style name, null for unnamed
         * @param parent the parent style, null if none
         * @since 1.4
         */
        public NamedStyle(String name, Style parent) {
            attributes = getEmptySet();
            if (name != null) {
                setName(name);
            }
            if (parent != null) {
                setResolveParent(parent);
            }
        }

        /**
         * Creates a new named style.
         *
         * @param parent the parent style, null if none
         * @since 1.4
         */
        public NamedStyle(Style parent) {
            this(null, parent);
        }

        /**
         * Creates a new named style, with a null name and parent.
         */
        public NamedStyle() {
            attributes = getEmptySet();
        }

        /**
         * Converts the style to a string.
         *
         * @return the string
         */
        public String toString() {
            return "NamedStyle:" + getName() + " " + attributes;
        }

        /**
         * Fetches the name of the style.   A style is not required to be named,
         * so null is returned if there is no name associated with the style.
         *
         * @return the name
         */
        public String getName() {
            if (isDefined(StyleConstants.NameAttribute)) {
                return getAttribute(StyleConstants.NameAttribute).toString();
            }
            return null;
        }

        /**
         * Changes the name of the style.  Does nothing with a null name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            if (name != null) {
                this.addAttribute(StyleConstants.NameAttribute, name);
            }
        }

        /**
         * Adds a change listener.
         *
         * @param l the change listener
         */
        public void addChangeListener(ChangeListener l) {
            listenerList.add(ChangeListener.class, l);
        }

        /**
         * Removes a change listener.
         *
         * @param l the change listener
         */
        public void removeChangeListener(ChangeListener l) {
            listenerList.remove(ChangeListener.class, l);
        }

        /**
         * Returns an array of all the <code>ChangeListener</code>s added
         * to this NamedStyle with addChangeListener().
         *
         * @return all of the <code>ChangeListener</code>s added or an empty
         *         array if no listeners have been added
         * @since 1.4
         */
        public ChangeListener[] getChangeListeners() {
            return listenerList.getListeners(ChangeListener.class);
        }

        /**
         * Notifies all listeners that have registered interest for
         * notification on this event type.  The event instance
         * is lazily created using the parameters passed into
         * the fire method.
         *
         * @see EventListenerList
         */
        protected void fireStateChanged() {
            // Guaranteed to return a non-null array
            Object[] listeners = listenerList.getListenerList();
            // Process the listeners last to first, notifying
            // those that are interested in this event
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == ChangeListener.class) {
                    // Lazily create the event:
                    if (changeEvent == null) {
                        changeEvent = new ChangeEvent(this);
                    }
                    ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
                }
            }
        }

        /**
         * Return an array of all the listeners of the given type that
         * were added to this model.
         *
         * @param <T> the listener type
         * @param listenerType the type of listeners to return
         * @return all of the objects receiving <em>listenerType</em> notifications
         *          from this model
         *
         * @since 1.3
         */
        public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
            return listenerList.getListeners(listenerType);
        }

        // --- AttributeSet ----------------------------
        // delegated to the immutable field "attributes"

        public int getAttributeCount() {
            return attributes.getAttributeCount();
        }

        public boolean isDefined(Object attrName) {
            return attributes.isDefined(attrName);
        }

        public boolean isEqual(AttributeSet attr) {
            return attributes.isEqual(attr);
        }

        public AttributeSet copyAttributes() {
            NamedStyle a = new NamedStyle();
            a.attributes = attributes.copyAttributes();
            return a;
        }

        public Object getAttribute(Object attrName) {
            return attributes.getAttribute(attrName);
        }

        public Enumeration<?> getAttributeNames() {
            return attributes.getAttributeNames();
        }

        public boolean containsAttribute(Object name, Object value) {
            return attributes.containsAttribute(name, value);
        }

        public boolean containsAttributes(AttributeSet attrs) {
            return attributes.containsAttributes(attrs);
        }

        public AttributeSet getResolveParent() {
            return attributes.getResolveParent();
        }

        // --- MutableAttributeSet ----------------------------------
        // should fetch a new immutable record for the field
        // "attributes".

        public void addAttribute(Object name, Object value) {
            StyleContext context = StyleContext.this;
            attributes = context.addAttribute(attributes, name, value);
            fireStateChanged();
        }

        public void addAttributes(AttributeSet attr) {
            StyleContext context = StyleContext.this;
            attributes = context.addAttributes(attributes, attr);
            fireStateChanged();
        }

        public void removeAttribute(Object name) {
            StyleContext context = StyleContext.this;
            attributes = context.removeAttribute(attributes, name);
            fireStateChanged();
        }

        public void removeAttributes(Enumeration<?> names) {
            StyleContext context = StyleContext.this;
            attributes = context.removeAttributes(attributes, names);
            fireStateChanged();
        }

        public void removeAttributes(AttributeSet attrs) {
            StyleContext context = StyleContext.this;
            if (attrs == this) {
                attributes = context.getEmptySet();
            } else {
                attributes = context.removeAttributes(attributes, attrs);
            }
            fireStateChanged();
        }

        public void setResolveParent(AttributeSet parent) {
            if (parent != null) {
                addAttribute(StyleConstants.ResolveAttribute, parent);
            } else {
                removeAttribute(StyleConstants.ResolveAttribute);
            }
        }

        // --- serialization ---------------------------------------------

        private void writeObject(ObjectOutputStream s) throws IOException {
            s.defaultWriteObject();
            writeAttributeSet(s, attributes);
        }

        private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
            s.defaultReadObject();
            attributes = SimpleAttributeSet.EMPTY;
            readAttributeSet(s, this);
        }

        // --- member variables -----------------------------------------------

        /**
         * The change listeners for the model.
         */
        protected EventListenerList listenerList = new EventListenerList();

        /**
         * Only one ChangeEvent is needed per model instance since the
         * event's only (read-only) state is the source property.  The source
         * of events generated here is always "this".
         */
        protected transient ChangeEvent changeEvent = null;

        /**
         * Inner AttributeSet implementation, which may be an
         * immutable unique set being shared.
         */
        private transient AttributeSet attributes;

    }

    static {
        // initialize the static key registry with the StyleConstants keys
        try {
            int n = StyleConstants.keys.length;
            for (int i = 0; i < n; i++) {
                registerStaticAttributeKey(StyleConstants.keys[i]);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}
