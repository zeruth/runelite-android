package javax.accessibility;

/**
 * The key bindings that can be used to select an accessible component.
 */
public interface AccessibleKeyBinding {

    public int getAccessibleKeyBindingCount();

    public Object getAccessibleKeyBinding(int i);
}
