package javax.accessibility;

import javax.swing.text.AttributeSet;

/**
 * A contiguous sequence of text that has a common set of attributes.
 */
public class AccessibleAttributeSequence {
    /** The start index of the text sequence. */
    public int startIndex;

    /** The end index of the text sequence. */
    public int endIndex;

    /** The text attributes. */
    public AttributeSet attributes;

    public AccessibleAttributeSequence(int start, int end, AttributeSet attr) {
        startIndex = start;
        endIndex = end;
        attributes = attr;
    }
}
