package javax.accessibility;

/**
 * A contiguous sequence of text and its bounds.
 */
public class AccessibleTextSequence {
    /** The start index of the text sequence. */
    public int startIndex;

    /** The end index of the text sequence. */
    public int endIndex;

    /** The text. */
    public String text;

    public AccessibleTextSequence(int start, int end, String txt) {
        startIndex = start;
        endIndex = end;
        text = txt;
    }
}
