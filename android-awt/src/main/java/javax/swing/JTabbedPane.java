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

import java.awt.Color;
import java.awt.Component;
import javax.swing.event.ChangeListener;

public class JTabbedPane extends JComponent implements SwingConstants {
    private static final long serialVersionUID = 1L;
    public static final int WRAP_TAB_LAYOUT = 0;
    public static final int SCROLL_TAB_LAYOUT = 1;


    private int tabPlacement = TOP;
    private int tabLayoutPolicy = WRAP_TAB_LAYOUT;
    private int selectedIndex = -1;
    private int firstVisibleTab;
    private static final int OVERFLOW_CONTROL_SIZE = 16;
    private final java.util.List<Component> tabs = new java.util.ArrayList<>();
    private final java.util.List<String> titles = new java.util.ArrayList<>();
    private final java.util.List<Icon> icons = new java.util.ArrayList<>();

    private static class Page {
        String tip; Icon disabledIcon; Color foreground,background; boolean enabled=true;
        int mnemonic,displayedMnemonic=-1; Component tabComponent;
    }
    private final java.util.List<Page> pages=new java.util.ArrayList<>();
    public JTabbedPane() { init(); }
    public JTabbedPane(int tabPlacement) { this.tabPlacement = tabPlacement; init(); }
    public JTabbedPane(int tabPlacement, int tabLayoutPolicy) {
        this.tabPlacement = tabPlacement; this.tabLayoutPolicy = tabLayoutPolicy; init();
    }

    private Component trailingComponent;

    private static final int TAB_SLOT_PX = 30;
    private static final int STRIP_THICKNESS = 36;
    /** When a tab is selected, the panel area takes this many pixels alongside the strip. */
    private static final int PANEL_EXTENT = 200;

    private void init() {
        setOpaque(true);
        setBackground(new java.awt.Color(0x28292D));
        setForeground(new java.awt.Color(0xC0C0C0));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent event) {
                if(!isEnabled() || event.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;
                int direction=overflowDirection(event.getX(),event.getY());
                if(direction!=0){scrollTabs(direction);event.consume();}
            }
        });
        addMouseWheelListener(event->{if(inStrip(event.getX(),event.getY())&&hasOverflow()){scrollTabs(event.getWheelRotation());event.consume();}});
    }

    /**
     * Render the tab strip ourselves since android-awt has no UI delegate. For tabPlacement
     * RIGHT/LEFT, tabs stack vertically; for TOP/BOTTOM, horizontally. Each tab is a slot of
     * fixed size with the icon centered. Selected tab gets a brighter background.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        if (tabs.isEmpty()) return;

        boolean vertical = (tabPlacement == LEFT || tabPlacement == RIGHT);
        int trailing = trailingExtent();
        int slotW = vertical ? STRIP_THICKNESS : TAB_SLOT_PX;
        int slotH = vertical ? TAB_SLOT_PX     : STRIP_THICKNESS;

        // Available tab area excludes the trailing component region.
        int axisLen = vertical ? (getHeight() - trailing) : (getWidth() - trailing);
        int maxTabs = visibleTabCount();
        clampTabOffset();

        // Strip origin: at right edge for RIGHT placement, etc.
        int baseX = stripX();
        int baseY = stripY();

        java.awt.Color saved = g.getColor();
        java.awt.Color selBg = new java.awt.Color(0x44464D);
        int limit = Math.min(tabs.size(), firstVisibleTab + maxTabs);
        for (int i = firstVisibleTab; i < limit; i++) {
            int slot = i - firstVisibleTab;
            int x = baseX + (vertical ? 0 : slot * slotW);
            int y = baseY + (vertical ? slot * slotH : 0);
            if (i == selectedIndex) {
                g.setColor(selBg);
                g.fillRect(x, y, slotW, slotH);
            }
            if(pages.get(i).tabComponent!=null)continue;
            Icon icon = !pages.get(i).enabled&&pages.get(i).disabledIcon!=null?pages.get(i).disabledIcon:icons.get(i);
            if(icon==null&&!titles.get(i).isEmpty()){g.setColor(pages.get(i).foreground==null?getForeground():pages.get(i).foreground);g.drawString(titles.get(i),x+2,y+slotH/2+5);}
            if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                int iw = icon.getIconWidth();
                int ih = icon.getIconHeight();
                // Tab icons that arrive un-resized (RuneLite calls ImageUtil.resizeImage which
                // may no-op in our shadow) often blow past the slot. Scale-to-fit when needed.
                int targetW = iw, targetH = ih;
                if (iw > slotW - 4 || ih > slotH - 4) {
                    float s = Math.min((slotW - 4) / (float) iw, (slotH - 4) / (float) ih);
                    targetW = Math.max(1, Math.round(iw * s));
                    targetH = Math.max(1, Math.round(ih * s));
                }
                int ix = x + (slotW - targetW) / 2;
                int iy = y + (slotH - targetH) / 2;
                if (targetW != iw && icon instanceof ImageIcon) {
                    java.awt.Image img = ((ImageIcon) icon).getImage();
                    if (img != null) {
                        try { g.drawImage(img, ix, iy, targetW, targetH, this); } catch (Throwable ignored) {}
                        continue;
                    }
                }
                try { icon.paintIcon(this, g, ix, iy); } catch (Throwable ignored) {}
            }
        }
        if(hasOverflow()) {
            int start=Math.max(0,axisLen-2*OVERFLOW_CONTROL_SIZE);
            g.setColor(getForeground());
            for(int i=0;i<2;i++) {
                int x=baseX+(vertical?0:start+i*OVERFLOW_CONTROL_SIZE);
                int y=baseY+(vertical?start+i*OVERFLOW_CONTROL_SIZE:0);
                g.drawString(vertical?(i==0?"^":"v"):(i==0?"<":">"),x+4,y+(vertical?12:22));
            }
        }
        if (saved != null) g.setColor(saved);
    }

    /**
     * FlatLaf-style trailing component: RuneLite hangs its top toolbar off the sidebar
     * via {@code FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT}. We mount it as a
     * direct child and lay it out at the top (or bottom) of the tab strip.
     */
    @Override
    protected void onClientPropertySet(Object key, Object value) {
        if ("JTabbedPane.trailingComponent".equals(key)) {
            if (trailingComponent != null) {
                super.remove(trailingComponent);
                trailingComponent = null;
            }
            if (value instanceof Component) {
                trailingComponent = (Component) value;
                super.add(trailingComponent);
                if (getWidth() > 0 && getHeight() > 0) doLayout();
            }
        }
    }

    /**
     * For tabPlacement=RIGHT/LEFT, FlatLaf places the trailing component at the bottom of
     * the strip and stacks tabs from the top. We mirror that — tabs occupy the upper part
     * of the strip, the trailing component (RuneLite's toolbar buttons wrap) takes its
     * preferred height at the bottom.
     */
    private int trailingExtent() {
        if (trailingComponent == null) return 0;
        java.awt.Dimension pref = trailingComponent.getPreferredSize();
        boolean vertical = (tabPlacement == LEFT || tabPlacement == RIGHT);
        int want = vertical ? (pref.height > 0 ? pref.height : 40) : (pref.width > 0 ? pref.width : 40);
        int avail = vertical ? getHeight() : getWidth();
        return Math.max(0, Math.min(want, avail));
    }

    /** X offset of the tab strip within the pane (for tabPlacement RIGHT only — strip on right edge). */
    private int stripX() {
        return tabPlacement == RIGHT ? Math.max(0, getWidth() - STRIP_THICKNESS) : 0;
    }
    private int stripY() {
        return tabPlacement == BOTTOM ? Math.max(0, getHeight() - STRIP_THICKNESS) : 0;
    }
    /** Selected tab's panel occupies the area NOT taken by the strip. */
    private java.awt.Rectangle panelRect() {
        boolean vertical = (tabPlacement == LEFT || tabPlacement == RIGHT);
        if (vertical) {
            int px = tabPlacement == LEFT ? STRIP_THICKNESS : 0;
            return new java.awt.Rectangle(px, 0, Math.max(0, getWidth() - STRIP_THICKNESS), getHeight());
        }
        int py = tabPlacement == TOP ? STRIP_THICKNESS : 0;
        return new java.awt.Rectangle(0, py, getWidth(), Math.max(0, getHeight() - STRIP_THICKNESS));
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        boolean vertical = (tabPlacement == LEFT || tabPlacement == RIGHT);
        int trailing = trailingExtent();

        // Trailing component sits at the END of the strip (bottom for RIGHT, etc.).
        if (trailingComponent != null) {
            if (vertical) {
                trailingComponent.setBounds(stripX(), getHeight() - trailing, STRIP_THICKNESS, trailing);
            } else {
                trailingComponent.setBounds(getWidth() - trailing, stripY(), trailing, STRIP_THICKNESS);
            }
        }

        clampTabOffset();
        // Position every tab's panel to fill the non-strip area; only the selected one is visible.
        java.awt.Rectangle pr = panelRect();
        for (int i = 0; i < tabs.size(); i++) {
            Component c = tabs.get(i);
            if (c == null) continue;
            c.setBounds(pr.x, pr.y, pr.width, pr.height);
            Component header=pages.get(i).tabComponent;if(header!=null){java.awt.Rectangle bounds=getBoundsAt(i);header.setBounds(bounds);header.setVisible(bounds.width>0&&bounds.height>0);}
        }
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        // Don't delegate to super — Container.getPreferredSize sums child widths via the
        // layout manager, and our hidden tab panels are 225px wide each, so we'd report
        // a huge preferred size and the sidebar would stay expanded even when closed.
        // The tab strip is the only "always visible" part; growing happens only when a
        // tab is selected.
        boolean vertical = (tabPlacement == LEFT || tabPlacement == RIGHT);
        boolean expanded = selectedIndex >= 0 && selectedIndex < tabs.size();
        int extent = STRIP_THICKNESS + (expanded ? PANEL_EXTENT : 0);
        if (vertical) return new java.awt.Dimension(extent, 0);
        else          return new java.awt.Dimension(0, extent);
    }

    @Override
    public java.awt.Dimension getMinimumSize() { return getPreferredSize(); }

    public int getTabPlacement() { return tabPlacement; }
    public void setTabPlacement(int p) { this.tabPlacement = p; }
    public int getTabLayoutPolicy() { return tabLayoutPolicy; }
    public void setTabLayoutPolicy(int policy) { this.tabLayoutPolicy = policy; }
    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int index) {
        if(index < -1 || index >= tabs.size())throw new IndexOutOfBoundsException("tab index");
        if (index == this.selectedIndex) return;
        this.selectedIndex = index;
        int visible=visibleTabCount();
        if(index>=0&&visible>0){if(index<firstVisibleTab)firstVisibleTab=index;else if(index>=firstVisibleTab+visible)firstVisibleTab=index-visible+1;}
        clampTabOffset();
        // Only the selected tab's component is visible; everything else hidden so paint
        // walker skips it.
        for (int i = 0; i < tabs.size(); i++) {
            Component c = tabs.get(i);
            if (c != null) c.setVisible(i == index);
        }
        javax.swing.event.ChangeEvent ev = new javax.swing.event.ChangeEvent(this);
        for (javax.swing.event.ChangeListener l : new java.util.ArrayList<>(changeListeners)) {
            l.stateChanged(ev);
        }
        // Don't call doLayout directly — the next frame's validate() cascade will pick
        // up the new preferred size and re-flow the parent (avoids re-entrancy with
        // ClientUI.Layout which itself calls setSize on us).
        repaint();
    }
    public Component getSelectedComponent() { return selectedIndex < 0 || selectedIndex >= tabs.size() ? null : tabs.get(selectedIndex); }
    public void setSelectedComponent(Component c) { int index=tabs.indexOf(c);if(index<0)throw new IllegalArgumentException("component is not a tab");setSelectedIndex(index); }
    public void addTab(String title, Component component) { addTab(title, null, component, null); }
    public void addTab(String title, Icon icon, Component component) { addTab(title, icon, component, null); }
    public void addTab(String title, Icon icon, Component component, String tip) { insertTab(title,icon,component,tip,tabs.size()); }
    public void insertTab(String title,Icon icon,Component component,String tip,int index) {
        if(index<0||index>tabs.size())throw new IndexOutOfBoundsException("tab index");
        int existing=component==null?-1:tabs.indexOf(component);if(existing>=0){removeTabAt(existing);if(existing<index)index--;}
        Page page=new Page();page.tip=tip;if(tip!=null)ToolTipManager.sharedInstance().registerComponent(this);tabs.add(index,component);titles.add(index,title==null?"":title);icons.add(index,icon);pages.add(index,page);
        if(component!=null){super.add(component);component.setVisible(false);}
        if(tabs.size()==1)setSelectedIndex(0);else if(selectedIndex>=index)selectedIndex++;
        updateVisibleTabs();revalidate();repaint();
    }
    private void updateVisibleTabs(){for(int i=0;i<tabs.size();i++)if(tabs.get(i)!=null)tabs.get(i).setVisible(i==selectedIndex);}
    public Component add(Component component) { addTab(component==null?"":component.getName(),component);return component; }
    public Component add(String title,Component component) { addTab(title,component);return component; }
    public void remove(Component component) { int index=tabs.indexOf(component);if(index>=0)removeTabAt(index);else super.remove(component); }
    public void remove(int index) { removeTabAt(index); }
    public void removeTabAt(int index) {
        Component removed=tabs.remove(index);titles.remove(index);icons.remove(index);Page page=pages.remove(index);
        if(removed!=null)super.remove(removed);if(page.tabComponent!=null)super.remove(page.tabComponent);
        int previous=selectedIndex;
        if(previous==index){selectedIndex=-2;setSelectedIndex(tabs.isEmpty()?-1:Math.min(index,tabs.size()-1));}
        else if(previous>index)selectedIndex--;
        updateVisibleTabs();revalidate();repaint();
    }
    public void removeAll() { while(!tabs.isEmpty())removeTabAt(tabs.size()-1); }
    public int getTabCount() { return tabs.size(); }
    public int getTabRunCount() { return 1; }
    public String getTitleAt(int index) { return titles.get(index); }
    public void setTitleAt(int index, String title) { titles.set(index, title); invalidate(); }
    public Icon getIconAt(int index) { return icons.get(index); }
    public void setIconAt(int index, Icon icon) { icons.set(index,icon);revalidate();repaint(); }
    public Icon getDisabledIconAt(int index) { return pages.get(index).disabledIcon; }
    public void setDisabledIconAt(int index, Icon value) { pages.get(index).disabledIcon=value;repaint(); }
    public String getToolTipTextAt(int index) { return pages.get(index).tip; }
    public void setToolTipTextAt(int index, String value) { pages.get(index).tip=value;if(value!=null)ToolTipManager.sharedInstance().registerComponent(this);repaint(); }
    /** The JDK's per-tab tip: the tab under the pointer, else the pane's own. */
    @Override public String getToolTipText(java.awt.event.MouseEvent event) {
        int index=indexAtLocation(event.getX(),event.getY());
        if(index>=0&&index<pages.size()&&pages.get(index).tip!=null)return pages.get(index).tip;
        return super.getToolTipText(event);
    }
    public Color getForegroundAt(int index) { return pages.get(index).foreground; }
    public void setForegroundAt(int index, Color value) { pages.get(index).foreground=value;repaint(); }
    public Color getBackgroundAt(int index) { return pages.get(index).background; }
    public void setBackgroundAt(int index, Color value) { pages.get(index).background=value;repaint(); }
    public boolean isEnabledAt(int index) { return pages.get(index).enabled; }
    public void setEnabledAt(int index, boolean enabled) { pages.get(index).enabled=enabled;repaint(); }
    public Component getComponentAt(int index) { return tabs.get(index); }
    public void setComponentAt(int index, Component c) { Component old=tabs.set(index,c);if(old!=null)super.remove(old);if(c!=null){super.add(c);c.setVisible(index==selectedIndex);}revalidate();repaint(); }
    public int getMnemonicAt(int tabIndex) { return pages.get(tabIndex).mnemonic; }
    public void setMnemonicAt(int tabIndex, int mnemonic) { pages.get(tabIndex).mnemonic=mnemonic; }
    public int getDisplayedMnemonicIndexAt(int tabIndex) { return pages.get(tabIndex).displayedMnemonic; }
    public void setDisplayedMnemonicIndexAt(int tabIndex, int mnemonicIndex) { if(mnemonicIndex < -1 || mnemonicIndex>=titles.get(tabIndex).length())throw new IllegalArgumentException("mnemonic index");pages.get(tabIndex).displayedMnemonic=mnemonicIndex; }
    private boolean verticalTabs(){return tabPlacement==LEFT||tabPlacement==RIGHT;}
    private int tabAxisLength(){return Math.max(0,(verticalTabs()?getHeight():getWidth())-trailingExtent());}
    private boolean hasOverflow(){return tabs.size()*TAB_SLOT_PX>tabAxisLength();}
    private int visibleTabCount(){return Math.max(0,tabAxisLength()-(hasOverflow()?2*OVERFLOW_CONTROL_SIZE:0))/TAB_SLOT_PX;}
    private void clampTabOffset(){firstVisibleTab=Math.max(0,Math.min(firstVisibleTab,Math.max(0,tabs.size()-visibleTabCount())));}
    private boolean inStrip(int x,int y){return x>=stripX()&&y>=stripY()&&(verticalTabs()?x<stripX()+STRIP_THICKNESS&&y<tabAxisLength():y<stripY()+STRIP_THICKNESS&&x<tabAxisLength());}
    private int overflowDirection(int x,int y){
        if(!hasOverflow()||!inStrip(x,y))return 0;
        int axis=verticalTabs()?y:x,start=Math.max(0,tabAxisLength()-2*OVERFLOW_CONTROL_SIZE);
        return axis<start?0:axis<start+OVERFLOW_CONTROL_SIZE?-1:1;
    }
    private void scrollTabs(int amount){firstVisibleTab+=amount;clampTabOffset();doLayout();repaint();}
    public java.awt.Rectangle getBoundsAt(int index) {
        int slot=index-firstVisibleTab;
        if(index<0||index>=tabs.size()||slot<0||slot>=visibleTabCount())return new java.awt.Rectangle();
        return verticalTabs()?new java.awt.Rectangle(stripX(),slot*TAB_SLOT_PX,STRIP_THICKNESS,TAB_SLOT_PX):new java.awt.Rectangle(slot*TAB_SLOT_PX,stripY(),TAB_SLOT_PX,STRIP_THICKNESS);
    }
    public int indexOfTab(String title) { return titles.indexOf(title); }
    public int indexOfTab(Icon icon) { return icons.indexOf(icon); }
    public int indexOfComponent(Component c) { return tabs.indexOf(c); }
    public int indexAtLocation(int x,int y) {
        if(!inStrip(x,y)||overflowDirection(x,y)!=0)return -1;
        int slot=(verticalTabs()?y:x)/TAB_SLOT_PX;
        int index=firstVisibleTab+slot;
        return slot<visibleTabCount()&&index<tabs.size()&&pages.get(index).enabled?index:-1;
    }
    public Component getTabComponentAt(int index) { return pages.get(index).tabComponent; }
    public void setTabComponentAt(int index, Component component) { Page page=pages.get(index);if(page.tabComponent!=null)super.remove(page.tabComponent);page.tabComponent=component;if(component!=null)super.add(component);revalidate();repaint(); }
    public int indexOfTabComponent(Component component) { for(int i=0;i<pages.size();i++)if(pages.get(i).tabComponent==component)return i;return -1; }
    private final java.util.List<javax.swing.event.ChangeListener> changeListeners = new java.util.ArrayList<>();
    public void addChangeListener(ChangeListener l) { if (l != null) changeListeners.add(l); }
    public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }
}
