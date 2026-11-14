package javax.accessibility;

/**
 * Tool tip, titled border and key binding information of an accessible component.
 */
public interface AccessibleExtendedComponent extends AccessibleComponent {

    public String getToolTipText();

    public String getTitledBorderText();

    public AccessibleKeyBinding getAccessibleKeyBinding();
}
