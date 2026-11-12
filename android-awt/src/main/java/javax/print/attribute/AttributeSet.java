/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.print.attribute;

/**
 * Interface AttributeSet specifies the interface for a set of printing
 * attributes. A printing attribute is an object whose class implements
 * interface {@link Attribute Attribute}.
 * <P>
 * An attribute set contains a group of <I>attribute values,</I>
 * where duplicate values are not allowed in the set.
 * Furthermore, each value in an attribute set is
 * a member of some <I>category,</I> and at most one value in any particular
 * category is allowed in the set.
 */
public interface AttributeSet {


    /**
     * Returns the attribute value which this attribute set contains in the
     * given attribute category. Returns <tt>null</tt> if this attribute set
     * does not contain any attribute value in the given attribute category.
     *
     * @param  category  Attribute category whose associated attribute value
     *                   is to be returned. It must be a
     *                   {@link java.lang.Class Class}
     *                   that implements interface {@link Attribute
     *                   Attribute}.
     *
     * @return  The attribute value in the given attribute category contained
     *          in this attribute set, or <tt>null</tt> if this attribute set
     *          does not contain any attribute value in the given attribute
     *          category.
     *
     * @throws  NullPointerException
     *     (unchecked exception) Thrown if the <CODE>category</CODE> is null.
     * @throws  ClassCastException
     *     (unchecked exception) Thrown if the <CODE>category</CODE> is not a
     *     {@link java.lang.Class Class} that implements interface {@link
     *     Attribute Attribute}.
     */
    public Attribute get(Class<?> category);

    /**
     * Adds the specified attribute to this attribute set if it is not
     * already present, first removing any existing value in the same
     * attribute category as the specified attribute value.
     *
     * @param  attribute  Attribute value to be added to this attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *          call, i.e., the given attribute value was not already a member
     *          of this attribute set.
     *
     * @throws  NullPointerException
     *     (unchecked exception) Thrown if the <CODE>attribute</CODE> is null.
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>add()</CODE> operation.
     */
    public boolean add(Attribute attribute);


    /**
     * Removes any attribute for this category from this attribute set if
     * present. If <CODE>category</CODE> is null, then
     * <CODE>remove()</CODE> does nothing and returns <tt>false</tt>.
     *
     * @param  category Attribute category to be removed from this
     *                  attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *         call, i.e., the given attribute value had been a member of this
     *          attribute set.
     *
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>remove()</CODE> operation.
     */
    public boolean remove(Class<?> category);

    /**
     * Removes the specified attribute from this attribute set if
     * present. If <CODE>attribute</CODE> is null, then
     * <CODE>remove()</CODE> does nothing and returns <tt>false</tt>.
     *
     * @param  attribute Attribute value to be removed from this attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *         call, i.e., the given attribute value had been a member of this
     *          attribute set.
     *
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>remove()</CODE> operation.
     */
    public boolean remove(Attribute attribute);

    /**
     * Returns <tt>true</tt> if this attribute set contains an
     * attribute for the specified category.
     *
     * @param  category whose presence in this attribute set is
     *            to be tested.
     *
     * @return  <tt>true</tt> if this attribute set contains an attribute
     *         value for the specified category.
     */
    public boolean containsKey(Class<?> category);

    /**
     * Returns <tt>true</tt> if this attribute set contains the given
     * attribute value.
     *
     * @param  attribute  Attribute value whose presence in this
     * attribute set is to be tested.
     *
     * @return  <tt>true</tt> if this attribute set contains the given
     *      attribute  value.
     */
    public boolean containsValue(Attribute attribute);

    /**
     * Adds all of the elements in the specified set to this attribute.
     * The outcome is the same as if the
     * {@link #add(Attribute) add(Attribute)}
     * operation had been applied to this attribute set successively with each
     * element from the specified set.
     *
     * @param  attributes  whose elements are to be added to this attribute
     *            set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *          call.
     *
     * @throws  UnmodifiableSetException
     *     (Unchecked exception) Thrown if this attribute set does not support
     *     the <tt>addAll()</tt> method.
     * @throws  NullPointerException
     *     (Unchecked exception) Thrown if some element in the specified
     *     set is null, or the set is null.
     *
     * @see #add(Attribute)
     */
    public boolean addAll(AttributeSet attributes);

    /**
     * Returns the number of attributes in this attribute set. If this
     * attribute set contains more than <tt>Integer.MAX_VALUE</tt> elements,
     * returns  <tt>Integer.MAX_VALUE</tt>.
     *
     * @return  The number of attributes in this attribute set.
     */
    public int size();

    /**
     * Returns an array of the attributes contained in this set.
     * @return the Attributes contained in this set as an array, zero length
     * if the AttributeSet is empty.
     */
    public Attribute[] toArray();


    /**
     * Removes all attributes from this attribute set.
     *
     * @throws  UnmodifiableSetException
     *   (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>clear()</CODE> operation.
     */
    public void clear();

    /**
     * Returns true if this attribute set contains no attributes.
     *
     * @return true if this attribute set contains no attributes.
     */
    public boolean isEmpty();

    /**
     * Compares the specified object with this attribute set for equality.
     * Returns <tt>true</tt> if the given object is also an attribute set and
     * the two attribute sets contain the same attribute category-attribute
     * value mappings.
     *
     * @param  object to be compared for equality with this attribute set.
     *
     * @return  <tt>true</tt> if the specified object is equal to this
     *       attribute   set.
     */
    public boolean equals(Object object);

    /**
     * Returns the hash code value for this attribute set. The hash code of an
     * attribute set is defined to be the sum of the hash codes of each entry
     * in the AttributeSet.
     *
     * @return  The hash code value for this attribute set.
     */
    public int hashCode();

}
