package java.awt.event;

import java.awt.Component;
import java.awt.Container;

/**
 * A container's contents changed because a component was added or removed.
 */
public class ContainerEvent extends ComponentEvent {

    private static final long serialVersionUID = -4114942250539772041L;

    /** The first number in the range of ids used for container events. */
    public static final int CONTAINER_FIRST = 300;

    /** The last number in the range of ids used for container events. */
    public static final int CONTAINER_LAST = 301;

    /** This event indicates that a component was added to the container. */
    public static final int COMPONENT_ADDED = CONTAINER_FIRST;

    /** This event indicates that a component was removed from the container. */
    public static final int COMPONENT_REMOVED = 1 + CONTAINER_FIRST;

    /** The non-null component that is being added or removed from the Container. */
    Component child;

    public ContainerEvent(Component source, int id, Component child) {
        super(source, id);
        this.child = child;
    }

    /** The originator of the event, or null when the source is not a Container. */
    public Container getContainer() {
        return (source instanceof Container) ? (Container) source : null;
    }

    public Component getChild() {
        return child;
    }

    public String paramString() {
        String typeStr;
        switch (id) {
            case COMPONENT_ADDED:
                typeStr = "COMPONENT_ADDED";
                break;
            case COMPONENT_REMOVED:
                typeStr = "COMPONENT_REMOVED";
                break;
            default:
                typeStr = "unknown type";
        }
        return typeStr + ",child=" + child.getName();
    }
}
