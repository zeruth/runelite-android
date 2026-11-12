/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing;

/**
 * A <code>SizeSequence</code> object
 * efficiently maintains an ordered list
 * of sizes and corresponding positions.
 * One situation for which <code>SizeSequence</code>
 * might be appropriate is in a component
 * that displays multiple rows of unequal size.
 * In this case, a single <code>SizeSequence</code>
 * object could be used to track the heights
 * and Y positions of all rows.
 * <p>
 * Internally, the sizes are kept in an implicit binary tree: entry
 * <code>m = (from + to) / 2</code> of a range holds the size of its own
 * entry plus the sizes of every entry in the left half of the range, so
 * positions, lookups and single-entry updates take logarithmic time.
 *
 * @author Philip Milne
 * @since 1.3
 */

public class SizeSequence {

    private static int[] emptyArray = new int[0];
    private int a[];

    /**
     * Creates a new <code>SizeSequence</code> object
     * that contains no entries.  To add entries, you
     * can use <code>insertEntries</code> or <code>setSizes</code>.
     *
     * @see #insertEntries
     * @see #setSizes(int[])
     */
    public SizeSequence() {
        a = emptyArray;
    }

    /**
     * Creates a new <code>SizeSequence</code> object
     * that contains the specified number of entries,
     * all initialized to have size 0.
     *
     * @param numEntries  the number of sizes to track
     * @exception NegativeArraySizeException if
     *    <code>numEntries &lt; 0</code>
     */
    public SizeSequence(int numEntries) {
        this(numEntries, 0);
    }

    /**
     * Creates a new <code>SizeSequence</code> object
     * that contains the specified number of entries,
     * all initialized to have size <code>value</code>.
     *
     * @param numEntries  the number of sizes to track
     * @param value       the initial value of each size
     */
    public SizeSequence(int numEntries, int value) {
        this();
        insertEntries(0, numEntries, value);
    }

    /**
     * Creates a new <code>SizeSequence</code> object
     * that contains the specified sizes.
     *
     * @param sizes  the array of sizes to be contained in
     *               the <code>SizeSequence</code>
     */
    public SizeSequence(int[] sizes) {
        this();
        setSizes(sizes);
    }

    /**
     * Resets the size sequence to contain <code>length</code> items
     * all with a size of <code>size</code>.
     */
    void setSizes(int length, int size) {
        if (a.length != length) {
            a = new int[length];
        }
        setSizes(0, length, size);
    }

    private int setSizes(int from, int to, int size) {
        if (to <= from) {
            return 0;
        }
        int m = (from + to)/2;
        a[m] = size + setSizes(from, m, size);
        return a[m] + setSizes(m + 1, to, size);
    }

    /**
     * Resets this <code>SizeSequence</code> object,
     * using the data in the <code>sizes</code> argument.
     * This method reinitializes this object so that it
     * contains as many entries as the <code>sizes</code> array.
     * Each entry's size is initialized to the value of the
     * corresponding item in <code>sizes</code>.
     *
     * @param sizes  the array of sizes to be contained in
     *               this <code>SizeSequence</code>
     */
    public void setSizes(int[] sizes) {
        if (a.length != sizes.length) {
            a = new int[sizes.length];
        }
        setSizes(0, a.length, sizes);
    }

    private int setSizes(int from, int to, int[] sizes) {
        if (to <= from) {
            return 0;
        }
        int m = (from + to)/2;
        a[m] = sizes[m] + setSizes(from, m, sizes);
        return a[m] + setSizes(m + 1, to, sizes);
    }

    /**
     * Returns the size of all entries.
     *
     * @return  a new array containing the sizes in this object
     */
    public int[] getSizes() {
        int n = a.length;
        int[] sizes = new int[n];
        getSizes(0, n, sizes);
        return sizes;
    }

    private int getSizes(int from, int to, int[] sizes) {
        if (to <= from) {
            return 0;
        }
        int m = (from + to)/2;
        sizes[m] = a[m] - getSizes(from, m, sizes);
        return a[m] + getSizes(m + 1, to, sizes);
    }

    /**
     * Returns the start position for the specified entry.
     * For example, <code>getPosition(0)</code> returns 0,
     * <code>getPosition(1)</code> is equal to
     *   <code>getSize(0)</code>,
     * <code>getPosition(2)</code> is equal to
     *   <code>getSize(0)</code> + <code>getSize(1)</code>,
     * and so on.
     * <p>Note that if <code>index</code> is greater than
     * <code>length</code> the value returned may
     * be meaningless.
     *
     * @param index  the index of the entry whose position is desired
     * @return       the starting position of the specified entry
     */
    public int getPosition(int index) {
        return getPosition(0, a.length, index);
    }

    private int getPosition(int from, int to, int index) {
        if (to <= from) {
            return 0;
        }
        int m = (from + to)/2;
        if (index <= m) {
            return getPosition(from, m, index);
        }
        else {
            return a[m] + getPosition(m + 1, to, index);
        }
    }

    /**
     * Returns the index of the entry
     * that corresponds to the specified position.
     * For example, <code>getIndex(0)</code> is 0,
     * since the first entry always starts at position 0.
     *
     * @param position  the position of the entry
     * @return  the index of the entry that occupies the specified position
     */
    public int getIndex(int position) {
        return getIndex(0, a.length, position);
    }

    private int getIndex(int from, int to, int position) {
        if (to <= from) {
            return from;
        }
        int m = (from + to)/2;
        int pivot = a[m];
        if (position < pivot) {
           return getIndex(from, m, position);
        }
        else {
            return getIndex(m + 1, to, position - pivot);
        }
    }

    /**
     * Returns the size of the specified entry.
     * If <code>index</code> is out of the range
     * <code>(0 &lt;= index &lt; getSizes().length)</code>
     * the behavior is unspecified.
     *
     * @param index  the index corresponding to the entry
     * @return       the size of the entry
     */
    public int getSize(int index) {
        return getPosition(index + 1) - getPosition(index);
    }

    /**
     * Sets the size of the specified entry.
     * Note that if the value of <code>index</code>
     * does not fall in the range:
     * <code>(0 &lt;= index &lt; getSizes().length)</code>
     * the behavior is unspecified.
     *
     * @param index  the index corresponding to the entry
     * @param size   the size of the entry
     */
    public void setSize(int index, int size) {
        changeSize(0, a.length, index, size - getSize(index));
    }

    private void changeSize(int from, int to, int index, int delta) {
        if (to <= from) {
            return;
        }
        int m = (from + to)/2;
        if (index <= m) {
            a[m] += delta;
            changeSize(from, m, index, delta);
        }
        else {
            changeSize(m + 1, to, index, delta);
        }
    }

    /**
     * Adds a contiguous group of entries to this <code>SizeSequence</code>.
     * Note that the values of <code>start</code> and
     * <code>length</code> must satisfy the following
     * conditions:  <code>(0 &lt;= start &lt; getSizes().length)
     * AND (length &gt;= 0)</code>.  If these conditions are
     * not met, the behavior is unspecified and an exception
     * may be thrown.
     *
     * @param start   the index to be assigned to the first entry
     *                in the group
     * @param length  the number of entries in the group
     * @param value   the size to be assigned to each new entry
     * @exception ArrayIndexOutOfBoundsException if the parameters
     *   are outside of the range:
     *   (<code>0 &lt;= start &lt; (getSizes().length)) AND (length &gt;= 0)</code>
     */
    public void insertEntries(int start, int length, int value) {
        int sizes[] = getSizes();
        int end = start + length;
        int n = a.length + length;
        a = new int[n];
        for (int i = 0; i < start; i++) {
            a[i] = sizes[i] ;
        }
        for (int i = start; i < end; i++) {
            a[i] = value ;
        }
        for (int i = end; i < n; i++) {
            a[i] = sizes[i-length] ;
        }
        setSizes(a);
    }

    /**
     * Removes a contiguous group of entries
     * from this <code>SizeSequence</code>.
     * Note that the values of <code>start</code> and
     * <code>length</code> must satisfy the following
     * conditions:  <code>(0 &lt;= start &lt; getSizes().length)
     * AND (length &gt;= 0)</code>.  If these conditions are
     * not met, the behavior is unspecified and an exception
     * may be thrown.
     *
     * @param start   the index of the first entry to be removed
     * @param length  the number of entries to be removed
     */
    public void removeEntries(int start, int length) {
        int sizes[] = getSizes();
        int end = start + length;
        int n = a.length - length;
        a = new int[n];
        for (int i = 0; i < start; i++) {
            a[i] = sizes[i] ;
        }
        for (int i = start; i < n; i++) {
            a[i] = sizes[i+length] ;
        }
        setSizes(a);
    }
}
