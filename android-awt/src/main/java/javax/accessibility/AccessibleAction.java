package javax.accessibility;

/**
 * The actions an accessible object can perform, indexed from 0.
 */
public interface AccessibleAction {

    /** An action which causes a tree node to collapse if expanded and expand if collapsed. */
    public static final String TOGGLE_EXPAND = new String("toggleexpand");

    /** An action which increments a value. */
    public static final String INCREMENT = new String("increment");

    /** An action which decrements a value. */
    public static final String DECREMENT = new String("decrement");

    /** An action which causes a component to execute its default action. */
    public static final String CLICK = new String("click");

    /** An action which causes a popup to become visible if it is hidden and hidden if it is visible. */
    public static final String TOGGLE_POPUP = new String("toggle popup");

    public int getAccessibleActionCount();

    public String getAccessibleActionDescription(int i);

    public boolean doAccessibleAction(int i);
}
