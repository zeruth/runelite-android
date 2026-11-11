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
import java.awt.*;
public class OverlayLayout implements LayoutManager2, java.io.Serializable {
    private final Container target;
    public OverlayLayout(Container target) { this.target = target; }
    private void check(Container c) { if (c != target) throw new Error("OverlayLayout cannot be shared"); }
    public void addLayoutComponent(String name, Component c) {}
    public void addLayoutComponent(Component c, Object constraints) {}
    public void removeLayoutComponent(Component c) {}
    public void invalidateLayout(Container c) { check(c); }
    public float getLayoutAlignmentX(Container c) { check(c); return .5f; }
    public float getLayoutAlignmentY(Container c) { check(c); return .5f; }
    private Dimension size(Container c, int kind) {
        check(c); int w=0,h=0; Insets in=c.getInsets();
        for(Component child:c.getComponents()) { Dimension d=kind==0?child.getPreferredSize():kind==1?child.getMinimumSize():child.getMaximumSize(); w=Math.max(w,d.width);h=Math.max(h,d.height); }
        return new Dimension(w+in.left+in.right,h+in.top+in.bottom);
    }
    public Dimension preferredLayoutSize(Container c) { return size(c,0); }
    public Dimension minimumLayoutSize(Container c) { return size(c,1); }
    public Dimension maximumLayoutSize(Container c) { return size(c,2); }
    public void layoutContainer(Container c) {
        check(c); Insets in=c.getInsets(); int w=Math.max(0,c.getWidth()-in.left-in.right),h=Math.max(0,c.getHeight()-in.top-in.bottom);
        for(Component child:c.getComponents()) { Dimension max=child.getMaximumSize();int cw=Math.min(w,max.width),ch=Math.min(h,max.height);child.setBounds(in.left+Math.round((w-cw)*child.getAlignmentX()),in.top+Math.round((h-ch)*child.getAlignmentY()),cw,ch); }
    }
}
