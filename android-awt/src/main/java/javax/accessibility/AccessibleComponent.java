package javax.accessibility;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusListener;

/**
 * The graphical representation of an accessible object.
 */
public interface AccessibleComponent {

    public Color getBackground();

    public void setBackground(Color c);

    public Color getForeground();

    public void setForeground(Color c);

    public Cursor getCursor();

    public void setCursor(Cursor cursor);

    public Font getFont();

    public void setFont(Font f);

    public FontMetrics getFontMetrics(Font f);

    public boolean isEnabled();

    public void setEnabled(boolean b);

    public boolean isVisible();

    public void setVisible(boolean b);

    public boolean isShowing();

    public boolean contains(Point p);

    public Point getLocationOnScreen();

    public Point getLocation();

    public void setLocation(Point p);

    public Rectangle getBounds();

    public void setBounds(Rectangle r);

    public Dimension getSize();

    public void setSize(Dimension d);

    public Accessible getAccessibleAt(Point p);

    public boolean isFocusTraversable();

    public void requestFocus();

    public void addFocusListener(FocusListener l);

    public void removeFocusListener(FocusListener l);
}
