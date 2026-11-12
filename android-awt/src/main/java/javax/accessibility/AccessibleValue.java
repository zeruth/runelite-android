package javax.accessibility;

/**
 * The numerical value of an accessible object.
 */
public interface AccessibleValue {

    public Number getCurrentAccessibleValue();

    public boolean setCurrentAccessibleValue(Number n);

    public Number getMinimumAccessibleValue();

    public Number getMaximumAccessibleValue();
}
