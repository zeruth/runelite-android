package javax.accessibility;

/**
 * Text containing hyperlinks.
 */
public interface AccessibleHypertext extends AccessibleText {

    public abstract int getLinkCount();

    public abstract AccessibleHyperlink getLink(int linkIndex);

    public abstract int getLinkIndex(int charIndex);
}
