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

public class JLabel extends JComponent implements SwingConstants, javax.accessibility.Accessible {
    private static final String uiClassID = "LabelUI";
    public javax.swing.plaf.LabelUI getUI() { return (javax.swing.plaf.LabelUI) ui; }
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJLabel();
        }
        return accessibleContext;
    }

    /** Accessibility support for labels: role LABEL, named by the label text. */
    protected class AccessibleJLabel extends AccessibleJComponent {
        public String getAccessibleName() {
            String name = accessibleName;
            if (name == null) {
                name = (String) getClientProperty(javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
            }
            if (name == null) {
                name = JLabel.this.getText();
            }
            if (name == null) {
                name = super.getAccessibleName();
            }
            return name;
        }

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LABEL;
        }
    }
    private static final long serialVersionUID = 5496508283662221534L;

    private String text = "";
    private Icon icon;
    private int horizontalAlignment = LEADING;
    private int verticalAlignment = CENTER;
    private int horizontalTextPosition = TRAILING;
    private int verticalTextPosition = CENTER;
    private int iconTextGap = 4;

    public JLabel() {}
    public JLabel(Icon image) { this.icon = image; }
    public JLabel(Icon image, int horizontalAlignment) { this.icon = image; this.horizontalAlignment = horizontalAlignment; }
    public JLabel(String text) { this.text = text == null ? "" : text; }
    public JLabel(String text, int horizontalAlignment) { this.text = text == null ? "" : text; this.horizontalAlignment = horizontalAlignment; }
    public JLabel(String text, Icon icon, int horizontalAlignment) {
        this.text = text == null ? "" : text; this.icon = icon; this.horizontalAlignment = horizontalAlignment;
    }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text == null ? "" : text; cachedPref = null; invalidate();
        // 8u: setText re-derives the underline position from the mnemonic.
        if (displayedMnemonic != 0) {
            setDisplayedMnemonicIndex(findDisplayedMnemonicIndex(this.text, displayedMnemonic));
        }
    }
    public Icon getIcon() { return icon; }
    public void setIcon(Icon icon) {
        // 8u: a derived (not explicitly set) disabled icon is dropped so it tracks the new icon.
        if (icon != this.icon && !disabledIconSet) {
            disabledIcon = null;
        }
        this.icon = icon; cachedPref = null; invalidate();
    }
    @Override
    public void setFont(java.awt.Font f) { super.setFont(f); cachedPref = null; invalidate(); }
    private Icon disabledIcon;
    private java.awt.Component labelFor;
    private boolean disabledIconSet;
    private int displayedMnemonic,displayedMnemonicIndex=-1;

    /** Client property key set on the labelled component by {@link #setLabelFor}. */
    static final String LABELED_BY_PROPERTY = "labeledBy";

    /**
     * Returns the icon used by the label when it's disabled. As in 8u, when no
     * disabled icon was set one is derived once from the default icon through
     * the look and feel, and {@code null} is returned when none can be made.
     */
    public Icon getDisabledIcon() {
        if (!disabledIconSet && disabledIcon == null && icon != null) {
            LookAndFeel laf = UIManager.getLookAndFeel();
            disabledIcon = laf != null ? laf.getDisabledIcon(this, icon)
                    : icon instanceof ImageIcon
                            ? new ImageIcon(GrayFilter.createDisabledImage(((ImageIcon) icon).getImage()))
                            : null;
            if (disabledIcon != null) {
                firePropertyChange("disabledIcon", null, disabledIcon);
            }
        }
        return disabledIcon;
    }

    public void setDisabledIcon(Icon disabledIcon) {
        Icon oldValue = this.disabledIcon;
        this.disabledIcon = disabledIcon;
        disabledIconSet = (disabledIcon != null);
        firePropertyChange("disabledIcon", oldValue, disabledIcon);
        if (disabledIcon != oldValue) {
            if (disabledIcon == null || oldValue == null
                    || disabledIcon.getIconWidth() != oldValue.getIconWidth()
                    || disabledIcon.getIconHeight() != oldValue.getIconHeight()) {
                revalidate();
            }
            if (!isEnabled()) {
                repaint();
            }
        }
    }
    public int getHorizontalAlignment() { return horizontalAlignment; }
    public void setHorizontalAlignment(int a) { this.horizontalAlignment = a; }
    public int getVerticalAlignment() { return verticalAlignment; }
    public void setVerticalAlignment(int a) { this.verticalAlignment = a; }
    public int getHorizontalTextPosition() { return horizontalTextPosition; }
    public void setHorizontalTextPosition(int p) { this.horizontalTextPosition = p; cachedPref = null; invalidate(); }
    public int getVerticalTextPosition() { return verticalTextPosition; }
    public void setVerticalTextPosition(int p) { this.verticalTextPosition = p; cachedPref = null; invalidate(); }
    public int getIconTextGap() { return iconTextGap; }
    public void setIconTextGap(int g) { this.iconTextGap = g; cachedPref = null; invalidate(); }
    /**
     * Sets the component this label labels; as in 8u the labelled
     * {@code JComponent} gets the {@code labeledBy} client property, which its
     * accessible name falls back to.
     */
    public void setLabelFor(java.awt.Component c) {
        java.awt.Component oldC = labelFor;
        labelFor = c;
        firePropertyChange("labelFor", oldC, c);
        if (oldC instanceof JComponent) {
            ((JComponent) oldC).putClientProperty(LABELED_BY_PROPERTY, null);
        }
        if (c instanceof JComponent) {
            ((JComponent) c).putClientProperty(LABELED_BY_PROPERTY, this);
        }
    }
    public java.awt.Component getLabelFor() { return labelFor; }

    /*
     * 8u leaves the Alt+key binding to BasicLabelUI's input map; the shadow label
     * usually has no delegate, so it registers the focus-transfer action itself.
     */
    public void setDisplayedMnemonic(int key) {
        int oldKey = displayedMnemonic;
        if (oldKey != 0) {
            unregisterKeyboardAction(KeyStroke.getKeyStroke(oldKey, java.awt.event.InputEvent.ALT_DOWN_MASK));
        }
        displayedMnemonic = key;
        if (key != 0) {
            registerKeyboardAction(e -> {
                if (labelFor != null) {
                    labelFor.requestFocusInWindow();
                }
            }, KeyStroke.getKeyStroke(key, java.awt.event.InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        }
        firePropertyChange("displayedMnemonic", oldKey, key);
        setDisplayedMnemonicIndex(findDisplayedMnemonicIndex(getText(), key));
        if (key != oldKey) {
            revalidate();
            repaint();
        }
    }
    public void setDisplayedMnemonic(char aChar) {
        int vk = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(aChar);
        if (vk != java.awt.event.KeyEvent.VK_UNDEFINED) {
            setDisplayedMnemonic(vk);
        }
    }
    public int getDisplayedMnemonic() { return displayedMnemonic; }

    public void setDisplayedMnemonicIndex(int index) throws IllegalArgumentException {
        int oldValue = displayedMnemonicIndex;
        if (index != -1) {
            int textLength = text == null ? 0 : text.length();
            if (index < -1 || index >= textLength) {
                throw new IllegalArgumentException("index == " + index);
            }
        }
        displayedMnemonicIndex = index;
        firePropertyChange("displayedMnemonicIndex", oldValue, index);
        if (index != oldValue) {
            revalidate();
            repaint();
        }
    }
    public int getDisplayedMnemonicIndex() { return displayedMnemonicIndex; }

    /** SwingUtilities.findDisplayedMnemonicIndex of 8u: first case-insensitive hit. */
    private static int findDisplayedMnemonicIndex(String text, int mnemonic) {
        if (text == null || mnemonic == '\0') {
            return -1;
        }
        char uc = Character.toUpperCase((char) mnemonic);
        char lc = Character.toLowerCase((char) mnemonic);
        int uci = text.indexOf(uc);
        int lci = text.indexOf(lc);
        if (uci == -1) {
            return lci;
        } else if (lci == -1) {
            return uci;
        }
        return (lci < uci) ? lci : uci;
    }

    /** JShadowedLabel and friends call the typed overload — accept and discard. */
    public void setUI(javax.swing.plaf.LabelUI ui) { super.setUI(ui); }

    /*
     * The shadow JLabel paints itself and installs no delegate unless one is set, while a
     * JDK JLabel always has one; without it the shared Basic delegate answers, so the
     * baseline (GroupLayout baseline groups, TitledBorder) matches the JDK's for plain text.
     */
    @Override
    public int getBaseline(int width, int height) {
        if (ui != null) {
            return super.getBaseline(width, height);
        }
        return javax.swing.plaf.basic.BasicLabelUI.createUI(this).getBaseline(this, width, height);
    }

    @Override
    public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
        if (ui != null) {
            return super.getBaselineResizeBehavior();
        }
        return javax.swing.plaf.basic.BasicLabelUI.createUI(this).getBaselineResizeBehavior(this);
    }

    /** Cached content size (text + icon, no insets) — invalidated when text/icon/font
     * changes. Without this every BorderLayout layout pass remeasures the text via
     * JNI/Minikin, which dominated profile. Insets are added per call so a border change
     * needs no extra invalidation. */
    private java.awt.Dimension cachedPref;

    // Swing's JLabel constructor ends with setAlignmentX(LEFT_ALIGNMENT).
    { setAlignmentX(0f); }

    /** BasicHTML honours the "html.disable" client property: such labels show their
     *  markup as plain text (quest-helper's JGenerator relies on it). */
    private boolean isHtmlText() {
        return HtmlTextRenderer.isHtml(text) && !Boolean.TRUE.equals(getClientProperty("html.disable"));
    }

    @Override
    protected void onClientPropertySet(Object key, Object value) {
        super.onClientPropertySet(key, value);
        if ("html.disable".equals(key)) { cachedPref = null; invalidate(); }
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Insets in = getInsets();
        boolean html = isHtmlText();
        java.awt.Dimension c = cachedPref;
        if (c == null || html) {
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int textW = 0, textH = 0;
            if (html) {
                HtmlTextRenderer.Layout layout = HtmlTextRenderer.measure(text, fm, getWidth() - in.left - in.right - 2);
                textW = layout.width;
                textH = layout.height;
            } else if (text != null && !text.isEmpty()) {
                textW = fm.stringWidth(text);
                textH = fm.getHeight();
            }
            int w, h;
            if (icon == null) {
                w = textW; h = textH;
            } else if (textW == 0 && textH == 0) {
                w = icon.getIconWidth(); h = icon.getIconHeight();
            } else {
                // Same compound layout as paint, in an unbounded view, so text above/below
                // the icon measures correctly too.
                java.awt.Rectangle iconR = new java.awt.Rectangle(), textR = new java.awt.Rectangle();
                layoutLabel(fm, textW, textH, false, new java.awt.Rectangle(0, 0, Short.MAX_VALUE, Short.MAX_VALUE), iconR, textR);
                int x0 = Math.min(iconR.x, textR.x), y0 = Math.min(iconR.y, textR.y);
                w = Math.max(iconR.x + iconR.width, textR.x + textR.width) - x0;
                h = Math.max(iconR.y + iconR.height, textR.y + textR.height) - y0;
            }
            // Like BasicLabelUI an empty label is 0x0 (plus insets); the +2 slack on real
            // content absorbs measurement rounding so labels at preferred width never clip.
            c = w == 0 && h == 0 ? new java.awt.Dimension() : new java.awt.Dimension(w + 2, h + 2);
            if (!html) cachedPref = c;
        }
        return new java.awt.Dimension(c.width + in.left + in.right, c.height + in.top + in.bottom);
    }

    // BasicLabelUI reports min = max = preferred.
    @Override
    public java.awt.Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
    }

    /**
     * SwingUtilities.layoutCompoundLabel for a left-to-right label: positions icon and text
     * inside viewR by alignment / text position / gap, and (when `clip`) truncates plain
     * text that doesn't fit, returning the possibly shortened "..." string. textW/textH
     * describe the unclipped text block.
     */
    private String layoutLabel(java.awt.FontMetrics fm, int textW, int textH, boolean clip,
                               java.awt.Rectangle viewR, java.awt.Rectangle iconR, java.awt.Rectangle textR) {
        int hAlign = horizontalAlignment == LEADING ? LEFT : horizontalAlignment == TRAILING ? RIGHT : horizontalAlignment;
        int hTextPos = horizontalTextPosition == LEADING ? LEFT : horizontalTextPosition == TRAILING ? RIGHT : horizontalTextPosition;
        int vAlign = verticalAlignment, vTextPos = verticalTextPosition;
        iconR.x = iconR.y = 0;
        iconR.width = icon == null ? 0 : icon.getIconWidth();
        iconR.height = icon == null ? 0 : icon.getIconHeight();
        String shown = text == null ? "" : text;
        int gap;
        if (shown.isEmpty()) {
            textR.width = textR.height = 0;
            gap = 0;
        } else {
            gap = icon == null ? 0 : iconTextGap;
            int availTextWidth = hTextPos == CENTER ? viewR.width : viewR.width - (iconR.width + gap);
            textR.width = textW;
            textR.height = textH;
            if (clip && textW > availTextWidth) {
                shown = clipString(fm, shown, availTextWidth);
                textR.width = fm.stringWidth(shown);
            }
        }
        if (vTextPos == TOP) textR.y = hTextPos != CENTER ? 0 : -(textR.height + gap);
        else if (vTextPos == CENTER) textR.y = (iconR.height / 2) - (textR.height / 2);
        else textR.y = hTextPos != CENTER ? iconR.height - textR.height : iconR.height + gap;

        if (hTextPos == LEFT) textR.x = -(textR.width + gap);
        else if (hTextPos == CENTER) textR.x = (iconR.width / 2) - (textR.width / 2);
        else textR.x = iconR.width + gap;

        int labelX = Math.min(iconR.x, textR.x);
        int labelW = Math.max(iconR.x + iconR.width, textR.x + textR.width) - labelX;
        int labelY = Math.min(iconR.y, textR.y);
        int labelH = Math.max(iconR.y + iconR.height, textR.y + textR.height) - labelY;

        int dy;
        if (vAlign == TOP) dy = viewR.y - labelY;
        else if (vAlign == CENTER) dy = (viewR.y + viewR.height / 2) - (labelY + labelH / 2);
        else dy = (viewR.y + viewR.height) - (labelY + labelH);
        int dx;
        if (hAlign == LEFT) dx = viewR.x - labelX;
        else if (hAlign == RIGHT) dx = (viewR.x + viewR.width) - (labelX + labelW);
        else dx = (viewR.x + viewR.width / 2) - (labelX + labelW / 2);

        textR.x += dx; textR.y += dy;
        iconR.x += dx; iconR.y += dy;
        return shown;
    }

    /** SwingUtilities2.clipString: the longest prefix that fits with "..." appended. */
    private static String clipString(java.awt.FontMetrics fm, String s, int availTextWidth) {
        String clip = "...";
        availTextWidth -= fm.stringWidth(clip);
        if (availTextWidth <= 0) return clip;
        int width = 0, n = 0;
        for (int len = s.length(); n < len; n++) {
            int cp = s.codePointAt(n);
            int cw = fm.stringWidth(new String(Character.toChars(cp)));
            if (width + cw > availTextWidth) break;
            width += cw;
            if (Character.charCount(cp) == 2) n++;
        }
        // Per-char widths ignore kerning; settle on the real measured prefix.
        while (n > 0 && fm.stringWidth(s.substring(0, n)) > availTextWidth) n--;
        return s.substring(0, n) + clip;
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        super.paintComponent(g);
        java.awt.FontMetrics fm = g.getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        java.awt.Rectangle viewR = new java.awt.Rectangle(in.left, in.top,
            getWidth() - in.left - in.right, getHeight() - in.top - in.bottom);
        boolean html = isHtmlText();
        HtmlTextRenderer.Layout htmlLayout = html ? HtmlTextRenderer.measure(text, fm, viewR.width - 2) : null;
        boolean hasText = text != null && !text.isEmpty();
        int textW = htmlLayout != null ? htmlLayout.width : (hasText ? fm.stringWidth(text) : 0);
        int textH = htmlLayout != null ? htmlLayout.height : (hasText ? fm.getHeight() : 0);
        java.awt.Rectangle iconR = new java.awt.Rectangle(), textR = new java.awt.Rectangle();
        // HTML wraps/clips inside its own layout; only plain text gets the "..." treatment.
        String shown = layoutLabel(fm, textW, textH, htmlLayout == null, viewR, iconR, textR);
        if (icon != null) {
            try { icon.paintIcon(this, g, iconR.x, iconR.y); } catch (Throwable ignored) {}
        }
        if (hasText) {
            java.awt.Color saved = g.getColor();
            g.setFont(getFont());
            if (htmlLayout != null) {
                // HTML: each run paints with its own color (or label foreground when
                // no span override). y is the top of the text block; HtmlTextRenderer
                // applies the per-line baseline shift internally.
                HtmlTextRenderer.paint(g, htmlLayout, fm, textR.x, textR.y, getForeground());
            } else {
                g.setColor(getForeground());
                g.drawString(shown, textR.x, textR.y + fm.getAscent());
            }
            if (saved != null) g.setColor(saved);
        }
    }
}
