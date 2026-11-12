package java.awt.event;

/**
 * An abstract adapter class for receiving container events; both methods are empty.
 */
public abstract class ContainerAdapter implements ContainerListener {

    public void componentAdded(ContainerEvent e) {
    }

    public void componentRemoved(ContainerEvent e) {
    }
}
