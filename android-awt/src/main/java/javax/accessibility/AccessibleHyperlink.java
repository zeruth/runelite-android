package javax.accessibility;

/**
 * A link or set of links in a hypertext document.
 */
public abstract class AccessibleHyperlink implements AccessibleAction {

    public AccessibleHyperlink() {
    }

    public abstract boolean isValid();

    public abstract int getAccessibleActionCount();

    public abstract boolean doAccessibleAction(int i);

    public abstract String getAccessibleActionDescription(int i);

    public abstract Object getAccessibleActionObject(int i);

    public abstract Object getAccessibleActionAnchor(int i);

    public abstract int getStartIndex();

    public abstract int getEndIndex();
}
