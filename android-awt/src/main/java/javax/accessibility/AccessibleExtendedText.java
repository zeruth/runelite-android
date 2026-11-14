package javax.accessibility;

import java.awt.Rectangle;

/**
 * Text information beyond {@link AccessibleText}: line and attribute-run sequences.
 */
public interface AccessibleExtendedText {

    /** Constant used to indicate that the part of the text that should be retrieved is a line of text. */
    public static final int LINE = 4;

    /** Constant used to indicate that the part of the text that should be retrieved is contiguous text with the same attributes. */
    public static final int ATTRIBUTE_RUN = 5;

    public String getTextRange(int startIndex, int endIndex);

    public AccessibleTextSequence getTextSequenceAt(int part, int index);

    public AccessibleTextSequence getTextSequenceAfter(int part, int index);

    public AccessibleTextSequence getTextSequenceBefore(int part, int index);

    public Rectangle getTextBounds(int startIndex, int endIndex);
}
