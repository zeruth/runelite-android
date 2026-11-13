/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * An implementation of the AbstractDocument.Content interface
 * implemented using a gapped buffer similar to that used by emacs.
 * The underlying storage is a array of unicode characters with
 * a gap somewhere.  The gap is moved to the location of changes
 * to take advantage of common behavior where most changes are
 * in the same location.  Changes that occur at a gap boundary are
 * generally cheap and moving the gap is generally cheaper than
 * moving the array contents directly to accommodate the change.
 * <p>
 * The positions tracking change are also generally cheap to
 * maintain.  The Position implementations (marks) store the array
 * index and can easily calculate the sequential position from
 * the current gap location.  Changes only require update to the
 * the marks between the old and new gap boundaries when the gap
 * is moved, so generally updating the marks is pretty cheap.
 * The marks are stored sorted so they can be located quickly
 * with a binary search.  This increases the cost of adding a
 * mark, and decreases the cost of keeping the mark updated.
 * <p>
 * The package-private {@code GapVector} superclass of OpenJDK is folded into
 * this class; its protected members are declared here.
 *
 * @author  Timothy Prinzing
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GapContent implements AbstractDocument.Content, Serializable {

    /** The array of items; the gap lies between g0 and g1. */
    private Object array;
    private int g0;
    private int g1;

    /** Sorted by array index; marks at index 0 belong to offset 0 forever. */
    private transient List<MarkData> marks = new ArrayList<MarkData>();
    private transient MarkData search = new MarkData(0);
    private transient ReferenceQueue<StickyPosition> queue = new ReferenceQueue<StickyPosition>();
    private transient int unusedMarks = 0;

    /**
     * Creates a new GapContent object.  Initial size defaults to 10.
     */
    public GapContent() {
        this(10);
    }

    /**
     * Creates a new GapContent object, with the initial
     * size specified.  The initial size will not be allowed
     * to go below 2, to give room for the implied break and
     * the gap.
     *
     * @param initialLength the initial size
     */
    public GapContent(int initialLength) {
        int length = Math.max(initialLength, 2);
        array = allocateArray(length);
        g0 = 0;
        g1 = length;
        char[] implied = new char[1];
        implied[0] = '\n';
        replace(0, 0, implied, implied.length);
    }

    /**
     * Allocate an array to store items of the type
     * appropriate (which is determined by the subclass).
     */
    protected Object allocateArray(int len) {
        return new char[len];
    }

    /**
     * Get the length of the allocated array.
     */
    protected int getArrayLength() {
        char[] carray = (char[]) array;
        return carray.length;
    }

    /** Access to the array; the actual type is determined by the subclass. */
    protected final Object getArray() {
        return array;
    }

    /** Access to the start of the gap. */
    protected final int getGapStart() {
        return g0;
    }

    /** Access to the end of the gap. */
    protected final int getGapEnd() {
        return g1;
    }

    // --- AbstractDocument.Content methods -------------------------

    /**
     * Returns the length of the content.
     *
     * @return the length &gt;= 1
     */
    public int length() {
        int len = getArrayLength() - (getGapEnd() - getGapStart());
        return len;
    }

    /**
     * Inserts a string into the content.
     *
     * @param where the starting position &gt;= 0, &lt; length()
     * @param str the non-null string to insert
     * @return an UndoableEdit object for undoing
     * @exception BadLocationException if the specified position is invalid
     */
    public UndoableEdit insertString(int where, String str) throws BadLocationException {
        if (where > length() || where < 0) {
            throw new BadLocationException("Invalid insert", length());
        }
        char[] chars = str.toCharArray();
        replace(where, 0, chars, chars.length);
        return new InsertUndo(where, str.length());
    }

    /**
     * Removes part of the content.
     *
     * @param where the starting position &gt;= 0, where + nitems &lt; length()
     * @param nitems the number of characters to remove &gt;= 0
     * @return an UndoableEdit object for undoing
     * @exception BadLocationException if the specified position is invalid
     */
    public UndoableEdit remove(int where, int nitems) throws BadLocationException {
        if (where + nitems >= length()) {
            throw new BadLocationException("Invalid remove", length() + 1);
        }
        String removedString = getString(where, nitems);
        UndoableEdit edit = new RemoveUndo(where, removedString);
        replace(where, nitems, null, 0);
        return edit;
    }

    /**
     * Retrieves a portion of the content.
     *
     * @param where the starting position &gt;= 0
     * @param len the length to retrieve &gt;= 0
     * @return a string representing the content
     * @exception BadLocationException if the specified position is invalid
     */
    public String getString(int where, int len) throws BadLocationException {
        Segment s = new Segment();
        getChars(where, len, s);
        return new String(s.array, s.offset, s.count);
    }

    /**
     * Retrieves a portion of the content.  If the desired content spans
     * the gap, we copy the content.  If the desired content does not
     * span the gap, the actual store is returned to avoid the copy since
     * it is contiguous.
     *
     * @param where the starting position &gt;= 0, where + len &lt;= length()
     * @param len the number of characters to retrieve &gt;= 0
     * @param chars the Segment object to return the characters in
     * @exception BadLocationException if the specified position is invalid
     */
    public void getChars(int where, int len, Segment chars) throws BadLocationException {
        int end = where + len;
        if (where < 0 || end < 0) {
            throw new BadLocationException("Invalid location", -1);
        }
        if (end > length() || where > length()) {
            throw new BadLocationException("Invalid location", length() + 1);
        }
        int g0 = getGapStart();
        int g1 = getGapEnd();
        char[] array = (char[]) getArray();
        if ((where + len) <= g0) {
            // below gap
            chars.array = array;
            chars.offset = where;
        } else if (where >= g0) {
            // above gap
            chars.array = array;
            chars.offset = g1 + where - g0;
        } else {
            // spans the gap
            int before = g0 - where;
            if (chars.isPartialReturn()) {
                // partial return allowed, return amount before the gap
                chars.array = array;
                chars.offset = where;
                chars.count = before;
                return;
            }
            // partial return not allowed, must copy
            chars.array = new char[len];
            chars.offset = 0;
            System.arraycopy(array, where, chars.array, 0, before);
            System.arraycopy(array, g1, chars.array, before, len - before);
        }
        chars.count = len;
    }

    /**
     * Creates a position within the content that will
     * track change as the content is mutated.
     *
     * @param offset the offset to track &gt;= 0
     * @return the position
     * @exception BadLocationException if the specified position is invalid
     */
    public Position createPosition(int offset) throws BadLocationException {
        while (queue.poll() != null) {
            unusedMarks++;
        }
        if (unusedMarks > Math.max(5, (marks.size() / 10))) {
            removeUnusedMarks();
        }
        int g0 = getGapStart();
        int g1 = getGapEnd();
        int index = (offset < g0) ? offset : offset + (g1 - g0);
        search.index = index;
        int sortIndex = findSortIndex(search);
        MarkData m;
        StickyPosition position;
        if (sortIndex < marks.size()
            && (m = marks.get(sortIndex)).index == index
            && (position = m.getPosition()) != null) {
            // position references the correct StickyPostition
        } else {
            position = new StickyPosition();
            m = new MarkData(index, position, queue);
            position.setMark(m);
            marks.add(sortIndex, m);
        }
        return position;
    }

    /**
     * Holds the data for a mark... separately from
     * the real mark so that the real mark (Position
     * that the caller of createPosition holds) can be
     * collected if there are no more references to
     * it.  The update table holds only a reference
     * to this data.
     */
    final class MarkData extends WeakReference<StickyPosition> {

        MarkData(int index) {
            super(null);
            this.index = index;
        }

        MarkData(int index, StickyPosition position, ReferenceQueue<? super StickyPosition> queue) {
            super(position, queue);
            this.index = index;
        }

        /**
         * Fetch the location in the contiguous sequence
         * being modeled.  The index in the gap array
         * is held by the mark, so it is adjusted according
         * to it's relationship to the gap.
         */
        public final int getOffset() {
            int g0 = getGapStart();
            int g1 = getGapEnd();
            int offs = (index < g0) ? index : index - (g1 - g0);
            return Math.max(offs, 0);
        }

        StickyPosition getPosition() {
            return get();
        }

        int index;
    }

    final class StickyPosition implements Position {

        StickyPosition() {
        }

        void setMark(MarkData mark) {
            this.mark = mark;
        }

        public final int getOffset() {
            return mark.getOffset();
        }

        public String toString() {
            return Integer.toString(getOffset());
        }

        MarkData mark;
    }

    // --- gap management -------------------------------

    /**
     * Replace the given logical position in the storage with
     * the given new items.  This will move the gap to the area
     * being changed if the gap is not currently located at the
     * change location.
     *
     * @param position the location to make the replacement.  This
     *  is not the location in the underlying storage array, but
     *  the location in the contiguous space being modeled.
     * @param rmSize the number of items to remove
     * @param addItems the new items to place in storage.
     * @param addSize the number of items to add
     */
    protected void replace(int position, int rmSize, Object addItems, int addSize) {
        int addOffset = 0;
        if (addSize == 0) {
            close(position, rmSize);
            return;
        } else if (rmSize > addSize) {
            /* Shrink the end. */
            close(position + addSize, rmSize - addSize);
        } else {
            /* Grow the end, do two chunks. */
            int endSize = addSize - rmSize;
            int end = open(position + rmSize, endSize);
            System.arraycopy(addItems, rmSize, array, end, endSize);
            addSize = rmSize;
        }
        System.arraycopy(addItems, addOffset, array, position, addSize);
    }

    /**
     * Delete nItems at position.  Squeezes any marks
     * within the deleted area to position.  This moves
     * the gap to the best place by minimizing it's
     * overall movement.  The gap must intersect the
     * target block.
     */
    void close(int position, int nItems) {
        if (nItems == 0) {
            return;
        }
        int end = position + nItems;
        int newGs = (g1 - g0) + nItems;
        if (end <= g0) {
            // Move gap to end of block.
            if (g0 != end) {
                shiftGap(end);
            }
            // Adjust g0.
            shiftGapStartDown(g0 - nItems);
        } else if (position >= g0) {
            // Move gap to beginning of block.
            if (g0 != position) {
                shiftGap(position);
            }
            // Adjust g1.
            shiftGapEndUp(g0 + newGs);
        } else {
            // The gap is properly inside the target block.
            // No data movement necessary, simply move both gap pointers.
            shiftGapStartDown(position);
            shiftGapEndUp(g0 + newGs);
        }
    }

    /**
     * Make space for the given number of items at the given
     * location.
     *
     * @return the location that the caller should fill in
     */
    int open(int position, int nItems) {
        int gapSize = g1 - g0;
        if (nItems == 0) {
            if (position > g0) {
                position += gapSize;
            }
            return position;
        }

        // Expand the array if the gap is too small.
        shiftGap(position);
        if (nItems >= gapSize) {
            // Pre-shift the gap, to reduce total movement.
            shiftEnd(getArrayLength() - gapSize + nItems);
        }

        g0 = g0 + nItems;
        return position;
    }

    /**
     * resize the underlying storage array to the
     * given new size
     */
    void resize(int nsize) {
        Object narray = allocateArray(nsize);
        System.arraycopy(array, 0, narray, 0, Math.min(nsize, getArrayLength()));
        array = narray;
    }

    /**
     * Calculates a new size of the storage array depending on required
     * capacity.
     */
    int getNewArraySize(int reqSize) {
        return (reqSize + 1) * 2;
    }

    /**
     * Make the gap bigger, moving any necessary data and updating
     * the appropriate marks
     */
    protected void shiftEnd(int newSize) {
        int oldGapEnd = getGapEnd();

        int oldSize = getArrayLength();
        int upperSize = oldSize - oldGapEnd;
        int arrayLength = getNewArraySize(newSize);
        int newGapEnd = arrayLength - upperSize;
        resize(arrayLength);
        g1 = newGapEnd;

        if (upperSize != 0) {
            // Copy array items to new end of array.
            System.arraycopy(array, oldGapEnd, array, newGapEnd, upperSize);
        }

        // Adjust marks.
        int dg = getGapEnd() - oldGapEnd;
        int adjustIndex = findMarkAdjustIndex(oldGapEnd);
        int n = marks.size();
        for (int i = adjustIndex; i < n; i++) {
            MarkData mark = marks.get(i);
            mark.index += dg;
        }
    }

    /**
     * Move the start of the gap to a new location,
     * without changing the size of the gap.  This
     * moves the data in the array and updates the
     * marks accordingly.
     */
    protected void shiftGap(int newGapStart) {
        int oldGapStart = getGapStart();
        int dg = newGapStart - oldGapStart;
        int oldGapEnd = getGapEnd();
        int newGapEnd = oldGapEnd + dg;
        int gapSize = oldGapEnd - oldGapStart;

        // shift gap in the character array
        if (dg != 0) {
            g0 = newGapStart;
            g1 = newGapEnd;
            if (dg > 0) {
                // Move gap up, move data down.
                System.arraycopy(array, oldGapEnd, array, oldGapStart, dg);
            } else {
                // Move gap down, move data up.
                System.arraycopy(array, newGapStart, array, newGapEnd, -dg);
            }
        }

        // update the marks
        if (dg > 0) {
            // Move gap up, move marks down.
            int adjustIndex = findMarkAdjustIndex(oldGapStart);
            int n = marks.size();
            for (int i = adjustIndex; i < n; i++) {
                MarkData mark = marks.get(i);
                if (mark.index >= newGapEnd) {
                    break;
                }
                mark.index -= gapSize;
            }
        } else if (dg < 0) {
            // Move gap down, move marks up.
            int adjustIndex = findMarkAdjustIndex(newGapStart);
            int n = marks.size();
            for (int i = adjustIndex; i < n; i++) {
                MarkData mark = marks.get(i);
                if (mark.index >= oldGapEnd) {
                    break;
                }
                mark.index += gapSize;
            }
        }
        resetMarksAtZero();
    }

    /**
     * Resets all the marks that have an offset of 0 to have an index of
     * zero as well.
     */
    protected void resetMarksAtZero() {
        if (marks != null && getGapStart() == 0) {
            int g1 = getGapEnd();
            for (int counter = 0, maxCounter = marks.size(); counter < maxCounter; counter++) {
                MarkData mark = marks.get(counter);
                if (mark.index <= g1) {
                    mark.index = 0;
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Adjust the gap end downward.  This doesn't move
     * any data, but it does update any marks affected
     * by the boundary change.  All marks from the old
     * gap start down to the new gap start are squeezed
     * to the end of the gap (their location has been
     * removed).
     */
    protected void shiftGapStartDown(int newGapStart) {
        // Push aside all marks from oldGapStart down to newGapStart.
        int oldGapStart = getGapStart();
        int adjustIndex = findMarkAdjustIndex(newGapStart);
        int n = marks.size();
        int g1 = getGapEnd();
        for (int i = adjustIndex; i < n; i++) {
            MarkData mark = marks.get(i);
            if (mark.index >= oldGapStart) {
                break;
            }
            mark.index = g1;
        }

        // shift the gap in the character array
        g0 = newGapStart;

        resetMarksAtZero();
    }

    /**
     * Adjust the gap end upward.  This doesn't move
     * any data, but it does update any marks affected
     * by the boundary change. All marks from the old
     * gap end up to the new gap end are squeezed
     * to the end of the gap (their location has been
     * removed).
     */
    protected void shiftGapEndUp(int newGapEnd) {
        int adjustIndex = findMarkAdjustIndex(getGapEnd());
        int n = marks.size();
        for (int i = adjustIndex; i < n; i++) {
            MarkData mark = marks.get(i);
            if (mark.index >= newGapEnd) {
                break;
            }
            mark.index = newGapEnd;
        }

        // shift the gap in the character array
        g1 = newGapEnd;

        resetMarksAtZero();
    }

    /**
     * Compares two marks.
     */
    final int compare(MarkData o1, MarkData o2) {
        if (o1.index < o2.index) {
            return -1;
        } else if (o1.index > o2.index) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Finds the index to start mark adjustments given
     * some search index.
     */
    final int findMarkAdjustIndex(int searchIndex) {
        search.index = Math.max(searchIndex, 1);
        int index = findSortIndex(search);

        // return the first in the series
        // (ie. there may be duplicates).
        for (int i = index - 1; i >= 0; i--) {
            MarkData d = marks.get(i);
            if (d.index != search.index) {
                break;
            }
            index -= 1;
        }
        return index;
    }

    /**
     * Finds the index of where to insert a new mark.
     *
     * @param o the mark to insert
     * @return the index
     */
    final int findSortIndex(MarkData o) {
        int lower = 0;
        int upper = marks.size() - 1;
        int mid = 0;

        if (upper == -1) {
            return 0;
        }

        int cmp;
        MarkData last = marks.get(upper);
        cmp = compare(o, last);
        if (cmp > 0) {
            return upper + 1;
        }

        while (lower <= upper) {
            mid = lower + ((upper - lower) / 2);
            MarkData entry = marks.get(mid);
            cmp = compare(o, entry);

            if (cmp == 0) {
                // found a match
                return mid;
            } else if (cmp < 0) {
                upper = mid - 1;
            } else {
                lower = mid + 1;
            }
        }

        // didn't find it, but we indicate the index of where it would belong.
        return (cmp < 0) ? mid : mid + 1;
    }

    /**
     * Remove all unused marks out of the sorted collection
     * of marks.
     */
    final void removeUnusedMarks() {
        int n = marks.size();
        List<MarkData> cleaned = new ArrayList<MarkData>(n);
        for (int i = 0; i < n; i++) {
            MarkData mark = marks.get(i);
            if (mark.get() != null) {
                cleaned.add(mark);
            }
        }
        marks = cleaned;
        unusedMarks = 0;
    }

    private void readObject(java.io.ObjectInputStream s) throws ClassNotFoundException, java.io.IOException {
        s.defaultReadObject();
        marks = new ArrayList<MarkData>();
        search = new MarkData(0);
        queue = new ReferenceQueue<StickyPosition>();
    }

    // --- undo support --------------------------------------

    /**
     * Returns a Vector containing instances of UndoPosRef for the
     * Positions in the range
     * <code>offset</code> to <code>offset</code> + <code>length</code>.
     * If <code>v</code> is not null the matching Positions are placed in
     * there. The vector with the resulting Positions are returned.
     *
     * @param v the Vector to use, with a new one created on null
     * @param offset the starting offset &gt;= 0
     * @param length the length &gt;= 0
     * @return the set of instances
     */
    protected Vector getPositionsInRange(Vector v, int offset, int length) {
        int endOffset = offset + length;
        int startIndex;
        int endIndex;
        int g0 = getGapStart();
        int g1 = getGapEnd();

        // Find the index of the marks.
        if (offset < g0) {
            if (offset == 0) {
                // findMarkAdjustIndex start at 1!
                startIndex = 0;
            } else {
                startIndex = findMarkAdjustIndex(offset);
            }
            if (endOffset >= g0) {
                endIndex = findMarkAdjustIndex(endOffset + (g1 - g0) + 1);
            } else {
                endIndex = findMarkAdjustIndex(endOffset + 1);
            }
        } else {
            startIndex = findMarkAdjustIndex(offset + (g1 - g0));
            endIndex = findMarkAdjustIndex(endOffset + (g1 - g0) + 1);
        }

        Vector placeIn = (v == null) ? new Vector(Math.max(1, endIndex - startIndex)) : v;

        for (int counter = startIndex; counter < endIndex; counter++) {
            placeIn.addElement(new UndoPosRef(marks.get(counter)));
        }
        return placeIn;
    }

    /**
     * Resets the location for all the UndoPosRef instances
     * in <code>positions</code>.
     * <p>
     * This is meant for internal usage, and is generally not of interest
     * to subclasses.
     *
     * @param positions the UndoPosRef instances to reset
     */
    protected void updateUndoPositions(Vector positions, int offset, int length) {
        // Find the indexs of the end points.
        int endOffset = offset + length;
        int g1 = getGapEnd();
        int startIndex;
        int endIndex = findMarkAdjustIndex(g1 + 1);

        if (offset != 0) {
            startIndex = findMarkAdjustIndex(g1);
        } else {
            startIndex = 0;
        }

        // Reset the location of the refenences.
        for (int counter = positions.size() - 1; counter >= 0; counter--) {
            UndoPosRef ref = (UndoPosRef) positions.elementAt(counter);
            ref.resetLocation(endOffset, g1);
        }
        // We have to resort the marks in the range startIndex to endIndex.
        // We can take advantage of the fact that it will be in
        // increasing order, accept there will be a bunch of MarkData's with
        // the index g1 (or 0 if offset == 0) interspersed throughout.
        if (startIndex < endIndex) {
            MarkData[] sorted = new MarkData[endIndex - startIndex];
            int addIndex = 0;
            int counter;
            if (offset == 0) {
                // If the offset is 0, the positions won't have incremented,
                // have to do the reverse thing.
                // Find the elements in startIndex whose index is 0
                for (counter = startIndex; counter < endIndex; counter++) {
                    MarkData mark = marks.get(counter);
                    if (mark.index == 0) {
                        sorted[addIndex++] = mark;
                    }
                }
                for (counter = startIndex; counter < endIndex; counter++) {
                    MarkData mark = marks.get(counter);
                    if (mark.index != 0) {
                        sorted[addIndex++] = mark;
                    }
                }
            } else {
                for (counter = startIndex; counter < endIndex; counter++) {
                    MarkData mark = marks.get(counter);
                    if (mark.index != g1) {
                        sorted[addIndex++] = mark;
                    }
                }
                for (counter = startIndex; counter < endIndex; counter++) {
                    MarkData mark = marks.get(counter);
                    if (mark.index == g1) {
                        sorted[addIndex++] = mark;
                    }
                }
            }
            // And replace
            for (counter = 0; counter < sorted.length; counter++) {
                marks.set(startIndex + counter, sorted[counter]);
            }
        }
    }

    /**
     * Used to hold a reference to a Mark that is being reset as the
     * result of removing from the content.
     */
    final class UndoPosRef {
        UndoPosRef(MarkData rec) {
            this.rec = rec;
            this.undoLocation = rec.getOffset();
        }

        /**
         * Resets the location of the Position to the offset when the
         * receiver was instantiated.
         *
         * @param endOffset end location of inserted string.
         * @param g1 resulting end of gap.
         */
        protected void resetLocation(int endOffset, int g1) {
            if (undoLocation != endOffset) {
                this.rec.index = undoLocation;
            } else {
                this.rec.index = g1;
            }
        }

        /** Previous Offset of rec. */
        protected int undoLocation;
        /** Mark to reset offset. */
        protected MarkData rec;
    }

    /**
     * UnoableEdit created for inserts.
     */
    class InsertUndo extends AbstractUndoableEdit {
        protected InsertUndo(int offset, int length) {
            super();
            this.offset = offset;
            this.length = length;
        }

        public void undo() throws CannotUndoException {
            super.undo();
            try {
                // Get the Positions in the range being removed.
                posRefs = getPositionsInRange(null, offset, length);
                string = getString(offset, length);
                remove(offset, length);
            } catch (BadLocationException bl) {
                throw new CannotUndoException();
            }
        }

        public void redo() throws CannotRedoException {
            super.redo();
            try {
                insertString(offset, string);
                string = null;
                // Update the Positions that were in the range removed.
                if (posRefs != null) {
                    updateUndoPositions(posRefs, offset, length);
                    posRefs = null;
                }
            } catch (BadLocationException bl) {
                throw new CannotRedoException();
            }
        }

        /** Where string was inserted. */
        protected int offset;
        /** Length of string inserted. */
        protected int length;
        /** The string that was inserted. This will only be valid after an undo. */
        protected String string;
        /** An array of instances of UndoPosRef for the Positions in the
         * range that was removed, valid after undo. */
        protected Vector posRefs;
    }

    /**
     * UndoableEdit created for removes.
     */
    class RemoveUndo extends AbstractUndoableEdit {
        protected RemoveUndo(int offset, String string) {
            super();
            this.offset = offset;
            this.string = string;
            this.length = string.length();
            posRefs = getPositionsInRange(null, offset, length);
        }

        public void undo() throws CannotUndoException {
            super.undo();
            try {
                insertString(offset, string);
                // Update the Positions that were in the range removed.
                if (posRefs != null) {
                    updateUndoPositions(posRefs, offset, length);
                    posRefs = null;
                }
                string = null;
            } catch (BadLocationException bl) {
                throw new CannotUndoException();
            }
        }

        public void redo() throws CannotRedoException {
            super.redo();
            try {
                string = getString(offset, length);
                // Get the Positions in the range being removed.
                posRefs = getPositionsInRange(null, offset, length);
                remove(offset, length);
            } catch (BadLocationException bl) {
                throw new CannotRedoException();
            }
        }

        /** Where the string was removed from. */
        protected int offset;
        /** Length of string removed. */
        protected int length;
        /** The string that was removed. This is valid when redo is valid. */
        protected String string;
        /** An array of instances of UndoPosRef for the Positions in the
         * range that was removed, valid before undo. */
        protected Vector posRefs;
    }
}
