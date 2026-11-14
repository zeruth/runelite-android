/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing.plaf.basic;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;

/**
 * BasicButton implementation
 * <p>
 * Ported from OpenJDK 8u. The shadow has no {@code BasicHTML} View, so html button
 * text is laid out and drawn as its plain string; everything else (layout, pressed
 * icon and text shift, disabled etched text, mnemonic underline, sizing) is the JDK's.
 *
 * @author Jeff Dinkins
 */
public class BasicButtonUI extends ButtonUI{
    // Visual constants
    // NOTE: This is not used or set any where. Were we allowed to remove
    // fields, this would be removed.
    protected int defaultTextIconGap;

    // Amount to offset text, the value of this comes from
    // defaultTextShiftOffset once setTextShiftOffset has been invoked.
    private int shiftOffset = 0;
    // Value that is set in shiftOffset once setTextShiftOffset has been
    // invoked. The value of this comes from the defaults table.
    protected int defaultTextShiftOffset;

    private final static String propertyPrefix = "Button" + ".";

    // The JDK keeps the shared instance per AppContext; the shadow has one context.
    private static final BasicButtonUI buttonUI = new BasicButtonUI();

    // These rectangles/insets are allocated once for all
    // ButtonUI.paint() calls.  Re-using rectangles rather than
    // allocating them in each paint call substantially reduced the time
    // it took paint to run.  Obviously, this method can't be re-entered.
    private static Rectangle viewRect = new Rectangle();
    private static Rectangle textRect = new Rectangle();
    private static Rectangle iconRect = new Rectangle();

    // ********************************
    //          Create PLAF
    // ********************************
    public static ComponentUI createUI(JComponent c) {
        return buttonUI;
    }

    protected String getPropertyPrefix() {
        return propertyPrefix;
    }


    // ********************************
    //          Install PLAF
    // ********************************
    public void installUI(JComponent c) {
        installDefaults((AbstractButton) c);
        installListeners((AbstractButton) c);
        installKeyboardActions((AbstractButton) c);
    }

    protected void installDefaults(AbstractButton b) {
        // load shared instance defaults
        String pp = getPropertyPrefix();

        defaultTextShiftOffset = UIManager.getInt(pp + "textShiftOffset");

        // set the following defaults on the button
        if (b.isContentAreaFilled()) {
            LookAndFeel.installProperty(b, "opaque", Boolean.TRUE);
        } else {
            LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
        }

        if(b.getMargin() == null || (b.getMargin() instanceof UIResource)) {
            b.setMargin(UIManager.getInsets(pp + "margin"));
        }

        LookAndFeel.installColorsAndFont(b, pp + "background",
                                         pp + "foreground", pp + "font");
        LookAndFeel.installBorder(b, pp + "border");

        Object rollover = UIManager.get(pp + "rollover");
        if (rollover != null) {
            LookAndFeel.installProperty(b, "rolloverEnabled", rollover);
        }

        LookAndFeel.installProperty(b, "iconTextGap", Integer.valueOf(4));
    }

    // As 8u, except that the listener is not added as a mouse listener: the Android host
    // delivers a tap as press/release and then doClick(), so arming the model from the
    // mouse here would fire every button's action twice.
    protected void installListeners(AbstractButton b) {
        BasicButtonListener listener = createButtonListener(b);
        if(listener != null) {
            b.addFocusListener(listener);
            b.addPropertyChangeListener(listener);
            b.addChangeListener(listener);
        }
    }

    protected void installKeyboardActions(AbstractButton b){
        BasicButtonListener listener = getButtonListener(b);

        if(listener != null) {
            listener.installKeyboardActions(b);
        }
    }


    // ********************************
    //         Uninstall PLAF
    // ********************************
    public void uninstallUI(JComponent c) {
        uninstallKeyboardActions((AbstractButton) c);
        uninstallListeners((AbstractButton) c);
        uninstallDefaults((AbstractButton) c);
    }

    protected void uninstallKeyboardActions(AbstractButton b) {
        BasicButtonListener listener = getButtonListener(b);
        if(listener != null) {
            listener.uninstallKeyboardActions(b);
        }
    }

    protected void uninstallListeners(AbstractButton b) {
        BasicButtonListener listener = getButtonListener(b);
        if(listener != null) {
            b.removeFocusListener(listener);
            b.removeChangeListener(listener);
            b.removePropertyChangeListener(listener);
        }
    }

    protected void uninstallDefaults(AbstractButton b) {
        LookAndFeel.uninstallBorder(b);
    }

    // ********************************
    //        Create Listeners
    // ********************************
    protected BasicButtonListener createButtonListener(AbstractButton b) {
        return new BasicButtonListener(b);
    }

    public int getDefaultTextIconGap(AbstractButton b) {
        return defaultTextIconGap;
    }

    // ********************************
    //          Paint Methods
    // ********************************

    public void paint(Graphics g, JComponent c)
    {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();

        String text = layout(b, fontMetrics(b, g),
                               b.getWidth(), b.getHeight());

        clearTextShiftOffset();

        // perform UI specific press action, e.g. Windows L&F shifts text
        if (model.isArmed() && model.isPressed()) {
            paintButtonPressed(g,b);
        }

        // Paint the Icon
        if(b.getIcon() != null) {
            paintIcon(g,c,iconRect);
        }

        if (text != null && !text.equals("")){
            paintText(g, b, textRect, text);
        }

        if (b.isFocusPainted() && b.hasFocus()) {
            // paint UI specific focus
            paintFocus(g,b,viewRect,textRect,iconRect);
        }
    }

    protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect){
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();
            Icon icon = b.getIcon();
            Icon tmpIcon = null;

            if(icon == null) {
               return;
            }

            Icon selectedIcon = null;

            /* the fallback icon should be based on the selected state */
            if (model.isSelected()) {
                selectedIcon = b.getSelectedIcon();
                if (selectedIcon != null) {
                    icon = selectedIcon;
                }
            }

            if(!model.isEnabled()) {
                if(model.isSelected()) {
                   tmpIcon = b.getDisabledSelectedIcon();
                   if (tmpIcon == null) {
                       tmpIcon = selectedIcon;
                   }
                }

                if (tmpIcon == null) {
                    tmpIcon = b.getDisabledIcon();
                }
            } else if(model.isPressed() && model.isArmed()) {
                tmpIcon = b.getPressedIcon();
                if(tmpIcon != null) {
                    // revert back to 0 offset
                    clearTextShiftOffset();
                }
            } else if(b.isRolloverEnabled() && model.isRollover()) {
                if(model.isSelected()) {
                   tmpIcon = b.getRolloverSelectedIcon();
                   if (tmpIcon == null) {
                       tmpIcon = selectedIcon;
                   }
                }

                if (tmpIcon == null) {
                    tmpIcon = b.getRolloverIcon();
                }
            }

            if(tmpIcon != null) {
                icon = tmpIcon;
            }

            if(model.isPressed() && model.isArmed()) {
                icon.paintIcon(c, g, iconRect.x + getTextShiftOffset(),
                        iconRect.y + getTextShiftOffset());
            } else {
                icon.paintIcon(c, g, iconRect.x, iconRect.y);
            }

    }

    /**
     * As of Java 2 platform v 1.4 this method should not be used or overriden.
     * Use the paintText method which takes the AbstractButton argument.
     */
    protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();
        FontMetrics fm = fontMetrics(c, g);
        int mnemonicIndex = b.getDisplayedMnemonicIndex();

        /* Draw the Text */
        if(model.isEnabled()) {
            /*** paint the text normally */
            g.setColor(b.getForeground());
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex,
                                          textRect.x + getTextShiftOffset(),
                                          textRect.y + fm.getAscent() + getTextShiftOffset());
        }
        else {
            /*** paint the text disabled ***/
            g.setColor(b.getBackground().brighter());
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex,
                                          textRect.x, textRect.y + fm.getAscent());
            g.setColor(b.getBackground().darker());
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex,
                                          textRect.x - 1, textRect.y + fm.getAscent() - 1);
        }
    }

    /**
     * Method which renders the text of the current button.
     * <p>
     * @param g Graphics context
     * @param b Current button to render
     * @param textRect Bounding rectangle to render the text.
     * @param text String to render
     * @since 1.4
     */
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        paintText(g, (JComponent)b, textRect, text);
    }

    // Method signature defined here overriden in subclasses.
    // Perhaps this class should be abstract?
    protected void paintFocus(Graphics g, AbstractButton b,
                              Rectangle viewRect, Rectangle textRect, Rectangle iconRect){
    }



    protected void paintButtonPressed(Graphics g, AbstractButton b){
    }

    protected void clearTextShiftOffset(){
        this.shiftOffset = 0;
    }

    protected void setTextShiftOffset(){
        this.shiftOffset = defaultTextShiftOffset;
    }

    protected int getTextShiftOffset() {
        return shiftOffset;
    }

    // ********************************
    //          Layout Methods
    // ********************************
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public Dimension getPreferredSize(JComponent c) {
        AbstractButton b = (AbstractButton)c;
        return BasicGraphicsUtils.getPreferredButtonSize(b, b.getIconTextGap());
    }

    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    /**
     * Returns the baseline.
     *
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @see javax.swing.JComponent#getBaseline(int, int)
     * @since 1.6
     */
    public int getBaseline(JComponent c, int width, int height) {
        super.getBaseline(c, width, height);
        AbstractButton b = (AbstractButton)c;
        String text = b.getText();
        if (text == null || "".equals(text) || b.getFont() == null) {
            return -1;
        }
        FontMetrics fm = b.getFontMetrics(b.getFont());
        layout(b, fm, width, height);
        // BasicHTML.getBaseline without an html View: the first line's ascent.
        return textRect.y + fm.getAscent();
    }

    /**
     * Returns an enum indicating how the baseline of the component
     * changes as the size changes.
     *
     * @throws NullPointerException {@inheritDoc}
     * @see javax.swing.JComponent#getBaseline(int, int)
     * @since 1.6
     */
    public Component.BaselineResizeBehavior getBaselineResizeBehavior(
            JComponent c) {
        super.getBaselineResizeBehavior(c);
        switch(((AbstractButton)c).getVerticalAlignment()) {
        case SwingConstants.TOP:
            return Component.BaselineResizeBehavior.CONSTANT_ASCENT;
        case SwingConstants.BOTTOM:
            return Component.BaselineResizeBehavior.CONSTANT_DESCENT;
        case SwingConstants.CENTER:
            return Component.BaselineResizeBehavior.CENTER_OFFSET;
        }
        return Component.BaselineResizeBehavior.OTHER;
    }

    private String layout(AbstractButton b, FontMetrics fm,
                          int width, int height) {
        Insets i = b.getInsets();
        viewRect.x = i.left;
        viewRect.y = i.top;
        viewRect.width = width - (i.right + viewRect.x);
        viewRect.height = height - (i.bottom + viewRect.y);

        textRect.x = textRect.y = textRect.width = textRect.height = 0;
        iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;

        // layout the text and icon
        return SwingUtilities.layoutCompoundLabel(
            b, fm, b.getText(), b.getIcon(),
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewRect, iconRect, textRect,
            b.getText() == null ? 0 : b.getIconTextGap());
    }

    /**
     * {@code SwingUtilities2.getFontMetrics(c, g)}: the metrics of the component's font,
     * or of the graphics' font for a button that has none yet.
     */
    private static FontMetrics fontMetrics(JComponent c, Graphics g) {
        return c.getFont() != null ? c.getFontMetrics(c.getFont()) : g.getFontMetrics();
    }

    /**
     * Returns the ButtonListener for the passed in Button, or null if one
     * could not be found.
     */
    private BasicButtonListener getButtonListener(AbstractButton b) {
        java.awt.event.FocusListener[] listeners = b.getFocusListeners();
        if (listeners != null) {
            for (java.awt.event.FocusListener listener : listeners) {
                if (listener instanceof BasicButtonListener) {
                    return (BasicButtonListener) listener;
                }
            }
        }
        return null;
    }
}
