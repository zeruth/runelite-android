package java.awt.event;

import java.util.EventListener;

/**
 * Receives container events: a component added to or removed from a container.
 */
public interface ContainerListener extends EventListener {

    public void componentAdded(ContainerEvent e);

    public void componentRemoved(ContainerEvent e);
}
