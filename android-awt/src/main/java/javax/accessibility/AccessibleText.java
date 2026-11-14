package javax.accessibility;

import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.text.AttributeSet;

/**
 * The textual content of an accessible object.
 */
public interface AccessibleText {

    /** Constant used to indicate that the part of the text that should be retrieved is a character. */
    public static final int CHARACTER = 1;

    /** Constant used to indicate that the part of the text that should be retrieved is a word. */
    public static final int WORD = 2;

    /** Constant used to indicate that the part of the text that should be retrieved is a sentence. */
    public static final int SENTENCE = 3;

    public int getIndexAtPoint(Point p);

    public Rectangle getCharacterBounds(int i);

    public int getCharCount();

    public int getCaretPosition();

    public String getAtIndex(int part, int index);

    public String getAfterIndex(int part, int index);

    public String getBeforeIndex(int part, int index);

    public AttributeSet getCharacterAttribute(int i);

    public int getSelectionStart();

    public int getSelectionEnd();

    public String getSelectedText();
}
