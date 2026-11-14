package javax.accessibility;

/**
 * An icon of an accessible object.
 */
public interface AccessibleIcon {

    public String getAccessibleIconDescription();

    public void setAccessibleIconDescription(String description);

    public int getAccessibleIconWidth();

    public int getAccessibleIconHeight();
}
