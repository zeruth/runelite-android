/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.swing;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import javax.swing.event.ChangeListener;

public abstract class AbstractButton extends JComponent {
    private static final long serialVersionUID = -937921912697029109L;

    /**
     * Accessibility support shared by buttons and menu items: named by their text, one
     * "click" action that runs {@link #doClick()}, and a 0/1 value for the selected state.
     */
    protected abstract class AccessibleAbstractButton extends AccessibleJComponent
            implements javax.accessibility.AccessibleAction, javax.accessibility.AccessibleValue {

        public String getAccessibleName() {
            String name = accessibleName;
            if (name == null) {
                name = (String) getClientProperty(javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
            }
            if (name == null) {
                name = AbstractButton.this.getText();
            }
            if (name == null) {
                name = super.getAccessibleName();
            }
            return name;
        }

        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            if (getModel().isArmed()) {
                states.add(javax.accessibility.AccessibleState.ARMED);
            }
            if (isFocusOwner()) {
                states.add(javax.accessibility.AccessibleState.FOCUSED);
            }
            if (getModel().isPressed()) {
                states.add(javax.accessibility.AccessibleState.PRESSED);
            }
            if (isSelected()) {
                states.add(javax.accessibility.AccessibleState.CHECKED);
            }
            return states;
        }

        public javax.accessibility.AccessibleAction getAccessibleAction() {
            return this;
        }

        public javax.accessibility.AccessibleValue getAccessibleValue() {
            return this;
        }

        public int getAccessibleActionCount() {
            return 1;
        }

        public String getAccessibleActionDescription(int i) {
            // UIManager "AbstractButton.clickText" in every desktop look and feel.
            return i == 0 ? "click" : null;
        }

        public boolean doAccessibleAction(int i) {
            if (i == 0) {
                doClick();
                return true;
            }
            return false;
        }

        public Number getCurrentAccessibleValue() {
            return Integer.valueOf(isSelected() ? 1 : 0);
        }

        public boolean setCurrentAccessibleValue(Number n) {
            if (n == null) {
                return false;
            }
            setSelected(n.intValue() != 0);
            return true;
        }

        public Number getMinimumAccessibleValue() {
            return Integer.valueOf(0);
        }

        public Number getMaximumAccessibleValue() {
            return Integer.valueOf(1);
        }
    }

    public static final String TEXT_CHANGED_PROPERTY = "text";
    public static final String ICON_CHANGED_PROPERTY = "icon";
    public static final String SELECTED_CHANGED_PROPERTY = "selected";
    public static final String MODEL_CHANGED_PROPERTY = "model";
    public static final String MNEMONIC_CHANGED_PROPERTY = "mnemonic";
    public static final String MARGIN_CHANGED_PROPERTY = "margin";
    public static final String VERTICAL_ALIGNMENT_CHANGED_PROPERTY = "verticalAlignment";
    public static final String HORIZONTAL_ALIGNMENT_CHANGED_PROPERTY = "horizontalAlignment";
    public static final String VERTICAL_TEXT_POSITION_CHANGED_PROPERTY = "verticalTextPosition";
    public static final String HORIZONTAL_TEXT_POSITION_CHANGED_PROPERTY = "horizontalTextPosition";
    public static final String BORDER_PAINTED_CHANGED_PROPERTY = "borderPainted";
    public static final String FOCUS_PAINTED_CHANGED_PROPERTY = "focusPainted";
    public static final String ROLLOVER_ENABLED_CHANGED_PROPERTY = "rolloverEnabled";
    public static final String CONTENT_AREA_FILLED_CHANGED_PROPERTY = "contentAreaFilled";
    public static final String PRESSED_ICON_CHANGED_PROPERTY = "pressedIcon";
    public static final String SELECTED_ICON_CHANGED_PROPERTY = "selectedIcon";
    public static final String ROLLOVER_ICON_CHANGED_PROPERTY = "rolloverIcon";
    public static final String ROLLOVER_SELECTED_ICON_CHANGED_PROPERTY = "rolloverSelectedIcon";
    public static final String DISABLED_ICON_CHANGED_PROPERTY = "disabledIcon";
    public static final String DISABLED_SELECTED_ICON_CHANGED_PROPERTY = "disabledSelectedIcon";

    public static final int HORIZONTAL = SwingConstants.HORIZONTAL;
    public static final int VERTICAL = SwingConstants.VERTICAL;
    public static final int CENTER = SwingConstants.CENTER;
    public static final int LEFT = SwingConstants.LEFT;
    public static final int RIGHT = SwingConstants.RIGHT;
    public static final int TOP = SwingConstants.TOP;
    public static final int BOTTOM = SwingConstants.BOTTOM;

    protected ButtonModel model = new DefaultButtonModel();
    protected String text = "";
    protected Icon icon;
    private Icon pressedIcon, selectedIcon, rolloverIcon, disabledIcon, rolloverSelectedIcon, disabledSelectedIcon;
    private int horizontalAlignment = CENTER;
    private int horizontalTextPosition = TRAILING;
    private int verticalAlignment = CENTER;
    private int verticalTextPosition = CENTER;
    private static final int TRAILING = SwingConstants.TRAILING;
    private boolean borderPainted = true;
    private boolean contentAreaFilled = true;
    private boolean focusPainted = true;
    private boolean rolloverEnabled;
    private Action action;

    // Swing's AbstractButton.init(): buttons default to LEFT_ALIGNMENT in BoxLayouts.
    public AbstractButton() { setAlignmentX(0f); setModel(model); }

    // Unlike 8u the text is never null: the panel host and the painters read it unguarded.
    public String getText() { return text; }
    public void setText(String text) {
        String old = this.text;
        this.text = text == null ? "" : text;
        firePropertyChange(TEXT_CHANGED_PROPERTY, old, this.text);
        updateDisplayedMnemonicIndex(this.text, getMnemonic());
        if (!this.text.equals(old)) { revalidate(); repaint(); }
        invalidate();
    }
    public Icon getIcon() { return icon; }
    public void setIcon(Icon icon) {
        Icon old = this.icon;
        this.icon = icon;
        // 8u drops a disabled icon it derived itself from the old default icon.
        if (icon != old && disabledIcon instanceof javax.swing.plaf.UIResource) disabledIcon = null;
        firePropertyChange(ICON_CHANGED_PROPERTY, old, icon);
        if (icon != old) { revalidate(); repaint(); }
        invalidate();
    }
    public Icon getPressedIcon() { return pressedIcon; }
    public void setPressedIcon(Icon icon) { Icon old = pressedIcon; pressedIcon = icon; firePropertyChange(PRESSED_ICON_CHANGED_PROPERTY, old, icon); if (icon != old && getModel().isPressed()) repaint(); }
    public Icon getSelectedIcon() { return selectedIcon; }
    public void setSelectedIcon(Icon icon) {
        Icon old = selectedIcon;
        selectedIcon = icon;
        if (icon != old && disabledSelectedIcon instanceof javax.swing.plaf.UIResource) disabledSelectedIcon = null;
        firePropertyChange(SELECTED_ICON_CHANGED_PROPERTY, old, icon);
        if (icon != old && isSelected()) repaint();
    }
    public Icon getRolloverIcon() { return rolloverIcon; }
    public void setRolloverIcon(Icon icon) {
        Icon old = rolloverIcon;
        rolloverIcon = icon;
        firePropertyChange(ROLLOVER_ICON_CHANGED_PROPERTY, old, icon);
        // As in 8u, giving a rollover icon turns rollover on.
        setRolloverEnabled(true);
        if (icon != old) repaint();
    }
    // Shown when a button is both rolled-over AND selected. RuneLite's
    // SwingUtil.removeButtonDecorations() sets all icon variants including this one;
    // without it external plugin panels (quest-helper) crash at startUp with
    // NoSuchMethodError before any UI is built.
    public Icon getRolloverSelectedIcon() { return rolloverSelectedIcon; }
    public void setRolloverSelectedIcon(Icon icon) {
        Icon old = rolloverSelectedIcon;
        rolloverSelectedIcon = icon;
        firePropertyChange(ROLLOVER_SELECTED_ICON_CHANGED_PROPERTY, old, icon);
        setRolloverEnabled(true);
        if (icon != old && isSelected()) repaint();
    }
    /** The disabled icon, derived from the default icon by the look and feel when none is set. */
    public Icon getDisabledIcon() {
        if (disabledIcon == null) {
            disabledIcon = UIManager.getLookAndFeel() == null ? null : UIManager.getLookAndFeel().getDisabledIcon(this, getIcon());
            if (disabledIcon != null) firePropertyChange(DISABLED_ICON_CHANGED_PROPERTY, null, disabledIcon);
        }
        return disabledIcon;
    }
    public void setDisabledIcon(Icon icon) { Icon old = disabledIcon; disabledIcon = icon; firePropertyChange(DISABLED_ICON_CHANGED_PROPERTY, old, icon); if (icon != old && !isEnabled()) repaint(); }
    /** As in 8u: falls back to a disabled form of the selected icon, then to the disabled icon. */
    public Icon getDisabledSelectedIcon() {
        if (disabledSelectedIcon == null) {
            if (selectedIcon == null) return getDisabledIcon();
            disabledSelectedIcon = UIManager.getLookAndFeel() == null ? null
                : UIManager.getLookAndFeel().getDisabledSelectedIcon(this, selectedIcon);
        }
        return disabledSelectedIcon;
    }
    public void setDisabledSelectedIcon(Icon icon) {
        Icon old = disabledSelectedIcon;
        disabledSelectedIcon = icon;
        firePropertyChange(DISABLED_SELECTED_ICON_CHANGED_PROPERTY, old, icon);
        if (icon != old && !isEnabled() && isSelected()) repaint();
    }

    public int getHorizontalAlignment() { return horizontalAlignment; }
    public void setHorizontalAlignment(int alignment) {
        if (alignment == horizontalAlignment) return;
        int old = horizontalAlignment;
        horizontalAlignment = checkHorizontalKey(alignment, "horizontalAlignment");
        firePropertyChange(HORIZONTAL_ALIGNMENT_CHANGED_PROPERTY, old, horizontalAlignment);
        repaint();
    }
    public int getHorizontalTextPosition() { return horizontalTextPosition; }
    public void setHorizontalTextPosition(int textPosition) {
        if (textPosition == horizontalTextPosition) return;
        int old = horizontalTextPosition;
        horizontalTextPosition = checkHorizontalKey(textPosition, "horizontalTextPosition");
        firePropertyChange(HORIZONTAL_TEXT_POSITION_CHANGED_PROPERTY, old, horizontalTextPosition);
        revalidate(); repaint();
    }
    public int getVerticalAlignment() { return verticalAlignment; }
    public void setVerticalAlignment(int alignment) {
        if (alignment == verticalAlignment) return;
        int old = verticalAlignment;
        verticalAlignment = checkVerticalKey(alignment, "verticalAlignment");
        firePropertyChange(VERTICAL_ALIGNMENT_CHANGED_PROPERTY, old, verticalAlignment);
        repaint();
    }
    public int getVerticalTextPosition() { return verticalTextPosition; }
    public void setVerticalTextPosition(int textPosition) {
        if (textPosition == verticalTextPosition) return;
        int old = verticalTextPosition;
        verticalTextPosition = checkVerticalKey(textPosition, "verticalTextPosition");
        firePropertyChange(VERTICAL_TEXT_POSITION_CHANGED_PROPERTY, old, verticalTextPosition);
        revalidate(); repaint();
    }

    protected int checkHorizontalKey(int key, String exception) {
        if (key == LEFT || key == CENTER || key == RIGHT || key == SwingConstants.LEADING || key == TRAILING) return key;
        throw new IllegalArgumentException(exception);
    }

    protected int checkVerticalKey(int key, String exception) {
        if (key == TOP || key == CENTER || key == BOTTOM) return key;
        throw new IllegalArgumentException(exception);
    }

    public boolean isBorderPainted() { return borderPainted; }
    public void setBorderPainted(boolean b) { boolean old = borderPainted; borderPainted = b; firePropertyChange(BORDER_PAINTED_CHANGED_PROPERTY, old, b); if (b != old) { revalidate(); repaint(); } }
    public boolean isContentAreaFilled() { return contentAreaFilled; }
    public void setContentAreaFilled(boolean b) { boolean old = contentAreaFilled; contentAreaFilled = b; firePropertyChange(CONTENT_AREA_FILLED_CHANGED_PROPERTY, old, b); if (b != old) repaint(); }
    public boolean isFocusPainted() { return focusPainted; }
    public void setFocusPainted(boolean b) { boolean old = focusPainted; focusPainted = b; firePropertyChange(FOCUS_PAINTED_CHANGED_PROPERTY, old, b); if (b != old && isFocusOwner()) { revalidate(); repaint(); } }
    public boolean isRolloverEnabled() { return rolloverEnabled; }
    public void setRolloverEnabled(boolean b) { boolean old = rolloverEnabled; rolloverEnabled = b; firePropertyChange(ROLLOVER_ENABLED_CHANGED_PROPERTY, old, b); if (b != old) repaint(); }

    private long multiClickThreshhold;
    /** Clicks closer together than this many milliseconds produce one action event. */
    public void setMultiClickThreshhold(long threshhold) {
        if (threshhold < 0) throw new IllegalArgumentException("threshhold must be >= 0");
        multiClickThreshhold = threshhold;
    }
    public long getMultiClickThreshhold() { return multiClickThreshhold; }

    public ButtonModel getModel() { return model; }
    public void setModel(ButtonModel newModel) {
        if (newModel == null) throw new IllegalArgumentException("model");
        ButtonModel old = model;
        if (old != null) {
            old.removeActionListener(modelActionListener);
            old.removeItemListener(modelItemListener);
            old.removeChangeListener(modelChangeListener);
        }
        model = newModel;
        model.addActionListener(modelActionListener);
        model.addItemListener(modelItemListener);
        model.addChangeListener(modelChangeListener);
        updateMnemonicProperties();
        firePropertyChange(MODEL_CHANGED_PROPERTY, old, newModel);
        repaint();
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (model != null) model.setEnabled(enabled);
    }
    private final javax.swing.event.EventListenerList buttonListeners = new javax.swing.event.EventListenerList();
    private final ActionListener modelActionListener = e -> {
        java.awt.event.ActionEvent event = new java.awt.event.ActionEvent(this,
            java.awt.event.ActionEvent.ACTION_PERFORMED, getActionCommand(), e.getWhen(), e.getModifiers());
        for (ActionListener listener : getActionListeners()) listener.actionPerformed(event);
    };
    private final ItemListener modelItemListener = e -> {
        java.awt.event.ItemEvent event = new java.awt.event.ItemEvent(this,
            java.awt.event.ItemEvent.ITEM_STATE_CHANGED, this, e.getStateChange());
        for (ItemListener listener : getItemListeners()) listener.itemStateChanged(event);
        Action current = action;
        if (current != null && shouldUpdateSelectedStateFromAction() && current.getValue(Action.SELECTED_KEY) != null
            && Boolean.TRUE.equals(current.getValue(Action.SELECTED_KEY)) != isSelected()) current.putValue(Action.SELECTED_KEY, isSelected());
        repaint();
    };
    private final ChangeListener modelChangeListener = e -> {
        updateMnemonicProperties();
        javax.swing.event.ChangeEvent event = new javax.swing.event.ChangeEvent(this);
        for (ChangeListener listener : getChangeListeners()) listener.stateChanged(event);
        repaint();
    };

    // Typed UI-delegate accessors. javac resolves `button.setUI(new BasicButtonUI())`
    // (and BasicToggleButtonUI) to AbstractButton.setUI(ButtonUI) — the most specific
    // overload — so without this typed method present, every plugin/RuneLite call that
    // strips a button's L&F decorations dies with NoSuchMethodError at panel build (this
    // hit quest-helper plus internal SkillCalculator/Info/LootTracker panels). The
    // delegate isn't used for rendering here (we custom-paint), we just store it. JLabel
    // and JProgressBar already carry the equivalent typed overloads.
    public void setUI(javax.swing.plaf.ButtonUI ui) { super.setUI(ui); }
    public javax.swing.plaf.ButtonUI getUI() { return (javax.swing.plaf.ButtonUI) super.getUI(); }

    /** Padding between the icon and the text when both are shown. Default 4 mirrors Swing.
     *  External plugins (quest-helper / loot-tracker) call this on JButton, JToggleButton,
     *  etc. — without it Dalvik throws NoSuchMethodError at plugin startup. */
    private int iconTextGap = 4;
    public int getIconTextGap() { return iconTextGap; }
    public void setIconTextGap(int gap) { this.iconTextGap = gap; invalidate(); }

    /** Insets between the border and the button's content. Stored but not honored by
     *  paint (we draw text/icon centered) — plugins set it as a layout hint, that's all
     *  we need to satisfy. Without the stub Dalvik throws NoSuchMethodError. */
    private java.awt.Insets margin;
    public java.awt.Insets getMargin() { return margin; }
    public void setMargin(java.awt.Insets m) { java.awt.Insets old=margin;margin=m==null?null:new java.awt.Insets(m.top,m.left,m.bottom,m.right);firePropertyChange("margin",old,margin);revalidate();repaint(); }
    @Override public java.awt.Insets getInsets(){java.awt.Insets in=super.getInsets();return margin==null?in:new java.awt.Insets(in.top+margin.top,in.left+margin.left,in.bottom+margin.bottom,in.right+margin.right);}

    /**
     * Compute a sensible preferred size from text + icon + padding. JComponent's default
     * delegates to Component.getSize() which is (0,0) before layout — DynamicGridLayout
     * (and any other layout that consults preferred size) then placed buttons with
     * height=0, so Reset/Back in the plugin config panel never appeared. Real Swing
     * derives this from BasicButtonUI; we just measure directly.
     */
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int textW = 0, textH = 0;
        String t = text;
        if (t != null && !t.isEmpty()) {
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            if (fm != null) {
                textW = fm.stringWidth(t);
                textH = fm.getHeight();
            }
        }
        int iconW = icon != null ? icon.getIconWidth() : 0;
        int iconH = icon != null ? icon.getIconHeight() : 0;
        int gap = (textW > 0 && iconW > 0) ? iconTextGap : 0;
        int padX = 16, padY = 6;
        int w = Math.max(textW + iconW + gap + padX * 2, 24);
        int h = Math.max(Math.max(textH, iconH) + padY * 2, 24);
        return new java.awt.Dimension(w, h);
    }

    // BasicButtonUI reports min = max = preferred, so a BoxLayout neither stretches nor
    // squeezes a button unless the caller sets explicit bounds.
    @Override
    public java.awt.Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
    }

    public boolean isSelected() { return model.isSelected(); }
    public void setSelected(boolean b) { model.setSelected(b); }

    private final java.util.List<ActionListener> actionListeners = new java.util.ArrayList<>();
    private final java.util.List<ItemListener> itemListeners = new java.util.ArrayList<>();

    public void doClick() { doClick(68); }
    /**
     * Presses and releases the button through its model. Deliberately does not sleep
     * {@code pressTime} as 8u does: 8u paints the pressed state in between, the panel host
     * repaints only after the EDT task, so the sleep would stall the EDT for nothing.
     */
    public void doClick(int pressTime) {
        if (!isEnabled()) return;
        model.setArmed(true);
        model.setPressed(true);
        model.setPressed(false);
        model.setArmed(false);
    }

    // The mnemonic last published; the model's is the source of truth (8u updateMnemonicProperties).
    private int mnemonic;

    public void setMnemonic(int mnemonic) {
        model.setMnemonic(mnemonic);
        updateMnemonicProperties();
    }
    public int getMnemonic() { return mnemonic; }
    /** As in 8u: a lower-case letter is taken as its VK_ code. */
    public void setMnemonic(char mnemonic) {
        int vk = mnemonic;
        if (vk >= 'a' && vk <= 'z') vk -= ('a' - 'A');
        setMnemonic(vk);
    }

    private void updateMnemonicProperties() {
        int newMnemonic = model.getMnemonic();
        if (mnemonic != newMnemonic) {
            int old = mnemonic;
            mnemonic = newMnemonic;
            firePropertyChange(MNEMONIC_CHANGED_PROPERTY, old, mnemonic);
            updateDisplayedMnemonicIndex(getText(), mnemonic);
            revalidate();
            repaint();
        }
    }

    private int displayedMnemonicIndex = -1;
    public void setDisplayedMnemonicIndex(int index) {
        int old = displayedMnemonicIndex;
        if (index != -1) {
            String t = getText();
            int length = t == null ? 0 : t.length();
            if (index < -1 || index >= length) throw new IllegalArgumentException("index == " + index);
        }
        displayedMnemonicIndex = index;
        firePropertyChange("displayedMnemonicIndex", old, index);
        if (index != old) { revalidate(); repaint(); }
    }
    public int getDisplayedMnemonicIndex() { return displayedMnemonicIndex; }

    private void updateDisplayedMnemonicIndex(String t, int key) {
        setDisplayedMnemonicIndex(findDisplayedMnemonicIndex(t, key));
    }

    /** 8u SwingUtilities.findDisplayedMnemonicIndex: first case-insensitive match of the key. */
    static int findDisplayedMnemonicIndex(String t, int key) {
        if (t == null || key == '\0') return -1;
        char upper = Character.toUpperCase((char) key);
        char lower = Character.toLowerCase((char) key);
        int upperIndex = t.indexOf(upper);
        int lowerIndex = t.indexOf(lower);
        if (upperIndex == -1) return lowerIndex;
        if (lowerIndex == -1) return upperIndex;
        return lowerIndex < upperIndex ? lowerIndex : upperIndex;
    }

    public void setActionCommand(String s) { model.setActionCommand(s); }
    public String getActionCommand() { return model.getActionCommand() != null ? model.getActionCommand() : text; }

    /**
     * Swing's Action binding: the button takes its text/icon/tooltip/mnemonic/command/
     * enabled (and, for toggles, selected) state from the Action, follows later edits to
     * it, and fires it as an ActionListener. Storing it alone left menu items built as
     * `new JMenuItem(new AbstractAction("Go to wiki..") {...})` blank and inert.
     */
    public void setAction(Action a) {
        Action old = action;
        if (old != null && old.equals(a)) return;
        action = a;
        if (old != null) {
            removeActionListener(old);
            if (actionPropertyListener != null) old.removePropertyChangeListener(actionPropertyListener);
            actionPropertyListener = null;
        }
        configurePropertiesFromAction(a);
        if (a != null) {
            if (!actionListeners.contains(a)) addActionListener(a);
            actionPropertyListener = createActionPropertyChangeListener(a);
            a.addPropertyChangeListener(actionPropertyListener);
        }
    }
    public Action getAction() { return action; }

    private java.beans.PropertyChangeListener actionPropertyListener;
    private boolean hideActionText;

    public boolean getHideActionText() { return hideActionText; }
    public void setHideActionText(boolean hide) {
        if (hide == hideActionText) return;
        hideActionText = hide;
        if (action != null) setTextFromAction(action, false);
        firePropertyChange("hideActionText", !hide, hide);
    }

    protected void configurePropertiesFromAction(Action a) {
        setMnemonicFromAction(a);
        setTextFromAction(a, false);
        setToolTipText(a != null ? (String) a.getValue(Action.SHORT_DESCRIPTION) : null);
        setIconFromAction(a);
        setActionCommand(a != null ? (String) a.getValue(Action.ACTION_COMMAND_KEY) : null);
        setEnabled(a == null || a.isEnabled());
        if (a != null && a.getValue(Action.SELECTED_KEY) != null && shouldUpdateSelectedStateFromAction()) {
            setSelectedFromAction(a);
        }
        setDisplayedMnemonicIndexFromAction(a, false);
    }

    private void setDisplayedMnemonicIndexFromAction(Action a, boolean fromPropertyChange) {
        Object value = a == null ? null : a.getValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY);
        if (fromPropertyChange || value instanceof Integer) {
            int index = value instanceof Integer ? (Integer) value : -1;
            String t = getText();
            if (t == null || index >= t.length()) index = -1;
            setDisplayedMnemonicIndex(index);
        }
    }

    /** Follows the Action's PropertyChangeEvents. Weakly held, like Swing, so a long-lived
     *  shared Action doesn't pin every button ever built from it. */
    protected java.beans.PropertyChangeListener createActionPropertyChangeListener(Action a) {
        final java.lang.ref.WeakReference<AbstractButton> ref = new java.lang.ref.WeakReference<>(this);
        return new java.beans.PropertyChangeListener() {
            @Override public void propertyChange(java.beans.PropertyChangeEvent e) {
                AbstractButton b = ref.get();
                Action source = (Action) e.getSource();
                if (b == null) { source.removePropertyChangeListener(this); return; }
                if (b.getAction() != source) return;
                b.actionPropertyChanged(source, e.getPropertyName());
            }
        };
    }

    protected void actionPropertyChanged(Action a, String propertyName) {
        if (propertyName == null) { configurePropertiesFromAction(a); return; }
        switch (propertyName) {
            case Action.NAME: setTextFromAction(a, true); break;
            case "enabled": setEnabled(a.isEnabled()); break;
            case Action.SHORT_DESCRIPTION: setToolTipText((String) a.getValue(Action.SHORT_DESCRIPTION)); break;
            case Action.SMALL_ICON:
            case Action.LARGE_ICON_KEY: setIconFromAction(a); break;
            case Action.MNEMONIC_KEY: setMnemonicFromAction(a); break;
            case Action.ACTION_COMMAND_KEY: setActionCommand((String) a.getValue(Action.ACTION_COMMAND_KEY)); break;
            case Action.SELECTED_KEY:
                if (a.getValue(Action.SELECTED_KEY) != null && shouldUpdateSelectedStateFromAction()) setSelectedFromAction(a);
                break;
            case Action.DISPLAYED_MNEMONIC_INDEX_KEY: setDisplayedMnemonicIndexFromAction(a, true); break;
            default: break;
        }
    }

    private void setTextFromAction(Action a, boolean propertyChange) {
        if (!propertyChange) setText(a != null && !hideActionText ? (String) a.getValue(Action.NAME) : null);
        else if (!hideActionText) setText((String) a.getValue(Action.NAME));
    }

    /** LARGE_ICON_KEY wins over SMALL_ICON for buttons; JMenuItem only uses SMALL_ICON. */
    void setIconFromAction(Action a) {
        Icon ic = null;
        if (a != null) {
            ic = (Icon) a.getValue(Action.LARGE_ICON_KEY);
            if (ic == null) ic = (Icon) a.getValue(Action.SMALL_ICON);
        }
        setIcon(ic);
    }

    private void setMnemonicFromAction(Action a) {
        Object n = a == null ? null : a.getValue(Action.MNEMONIC_KEY);
        setMnemonic(n instanceof Integer ? (Integer) n : '\0');
    }

    private void setSelectedFromAction(Action a) {
        boolean selected = a != null && Boolean.TRUE.equals(a.getValue(Action.SELECTED_KEY));
        if (selected != isSelected()) setSelected(selected);
    }

    /** Only toggle-style buttons mirror Action.SELECTED_KEY. */
    boolean shouldUpdateSelectedStateFromAction() { return false; }

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }
    public ItemListener[] getItemListeners() { return itemListeners.toArray(new ItemListener[0]); }
    public ChangeListener[] getChangeListeners() { return buttonListeners.getListeners(ChangeListener.class); }
    public void addChangeListener(ChangeListener l) { buttonListeners.add(ChangeListener.class, l); }
    public void removeChangeListener(ChangeListener l) { buttonListeners.remove(ChangeListener.class, l); }

    /**
     * Render the button's icon and (very simplified) text. RuneLite's toolbar nav buttons
     * are 23×23 with just an icon — that's the case we care about most.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        Icon ic = (model != null && model.isSelected() && selectedIcon != null) ? selectedIcon : icon;
        if (ic == null || ic.getIconWidth() <= 0 || ic.getIconHeight() <= 0) return;

        int iw = ic.getIconWidth();
        int ih = ic.getIconHeight();
        // Scale-to-fit when the icon overflows the button. For ImageIcon we can drawImage
        // at a target size — for anything else, just clip via the existing graphics clip.
        if (iw > getWidth() || ih > getHeight()) {
            if (ic instanceof ImageIcon) {
                java.awt.Image img = ((ImageIcon) ic).getImage();
                if (img != null) {
                    float s = Math.min(getWidth() / (float) iw, getHeight() / (float) ih);
                    int dw = Math.max(1, Math.round(iw * s));
                    int dh = Math.max(1, Math.round(ih * s));
                    int ix = (getWidth() - dw) / 2;
                    int iy = (getHeight() - dh) / 2;
                    try { g.drawImage(img, ix, iy, dw, dh, this); } catch (Throwable ignored) {}
                    return;
                }
            }
        }
        int ix = (getWidth() - iw) / 2;
        int iy = (getHeight() - ih) / 2;
        try { ic.paintIcon(this, g, ix, iy); } catch (Throwable ignored) {}
    }
}
