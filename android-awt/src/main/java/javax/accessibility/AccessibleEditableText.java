package javax.accessibility;

import javax.swing.text.AttributeSet;

/**
 * Text that an assistive technology can edit.
 */
public interface AccessibleEditableText extends AccessibleText {

    public void setTextContents(String s);

    public void insertTextAtIndex(int index, String s);

    public String getTextRange(int startIndex, int endIndex);

    public void delete(int startIndex, int endIndex);

    public void cut(int startIndex, int endIndex);

    public void paste(int startIndex);

    public void replaceText(int startIndex, int endIndex, String s);

    public void selectText(int startIndex, int endIndex);

    public void setAttributes(int startIndex, int endIndex, AttributeSet as);
}
