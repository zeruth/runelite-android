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
package java.awt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CardLayout — shows exactly one of its children at a time. Each card is keyed by a name
 * (passed to {@code container.add(component, name)}). {@link #show(Container, String)}
 * selects the visible card; layoutContainer sizes the selected card to fill the parent.
 */
public class CardLayout implements LayoutManager2, Serializable {
    private static final long serialVersionUID = -4328196481005934313L;

    private int hgap, vgap;
    private final List<Card> cards = new ArrayList<>();
    private int active = 0;

    private static final class Card {
        final String name;
        final Component comp;
        Card(String name, Component comp) { this.name = name; this.comp = comp; }
    }

    public CardLayout() { this(0, 0); }
    public CardLayout(int hgap, int vgap) { this.hgap = hgap; this.vgap = vgap; }

    public int getHgap() { return hgap; }
    public void setHgap(int hgap) { this.hgap = hgap; }
    public int getVgap() { return vgap; }
    public void setVgap(int vgap) { this.vgap = vgap; }

    public void first(Container parent) { setActive(0, parent); }
    public void last(Container parent)  { setActive(cards.size() - 1, parent); }
    public void next(Container parent)  { setActive((active + 1) % Math.max(1, cards.size()), parent); }
    public void previous(Container parent) {
        setActive((active - 1 + cards.size()) % Math.max(1, cards.size()), parent);
    }
    public void show(Container parent, String name) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).name.equals(name)) { setActive(i, parent); return; }
        }
    }

    private void setActive(int idx, Container parent) {
        if (idx < 0 || idx >= cards.size()) return;
        active = idx;
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).comp.setVisible(i == active);
        }
        if (parent != null && parent.getWidth() > 0) layoutContainer(parent);
    }

    @Override public void addLayoutComponent(String name, Component comp) {
        addLayoutComponent(comp, name);
    }
    @Override public void addLayoutComponent(Component comp, Object constraints) {
        String name = constraints == null ? "" : constraints.toString();
        cards.add(new Card(name, comp));
        comp.setVisible(cards.size() == 1);  // first card visible by default
    }
    @Override public void removeLayoutComponent(Component comp) {
        cards.removeIf(c -> c.comp == comp);
        if (active >= cards.size()) active = Math.max(0, cards.size() - 1);
    }
    @Override public Dimension preferredLayoutSize(Container parent) {
        int w = 0, h = 0;
        for (Card c : cards) {
            Dimension d = c.comp.getPreferredSize();
            if (d.width > w) w = d.width;
            if (d.height > h) h = d.height;
        }
        Insets ins = parent.getInsets();
        return new Dimension(ins.left + ins.right + w + 2 * hgap, ins.top + ins.bottom + h + 2 * vgap);
    }
    @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}
    @Override public void layoutContainer(Container target) {
        Insets ins = target.getInsets();
        int x = ins.left + hgap;
        int y = ins.top + vgap;
        int w = target.getWidth() - ins.left - ins.right - 2 * hgap;
        int h = target.getHeight() - ins.top - ins.bottom - 2 * vgap;
        for (Card c : cards) {
            c.comp.setBounds(x, y, Math.max(0, w), Math.max(0, h));
        }
    }
}
