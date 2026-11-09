package javax.swing.plaf.basic;

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JList;

public interface ComboPopup {
    void show();
    void hide();
    boolean isVisible();
    JList<Object> getList();
    MouseListener getMouseListener();
    MouseMotionListener getMouseMotionListener();
    KeyListener getKeyListener();
    void uninstallingUI();
}
