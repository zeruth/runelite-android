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

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;

public class JFrame extends Frame implements javax.accessibility.Accessible, RootPaneContainer {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJFrame();
        }
        return accessibleContext;
    }

    /** Accessibility support for Swing frames: a FRAME context named by the title. */
    protected class AccessibleJFrame extends AccessibleAWTFrame {
        public String getAccessibleName() {
            if (accessibleName != null) {
                return accessibleName;
            }
            String title = getTitle();
            return title == null ? super.getAccessibleName() : title;
        }
    }
    private static final long serialVersionUID = 1L;

    public static final int EXIT_ON_CLOSE = 3;
    public static final int DO_NOTHING_ON_CLOSE = 0;
    public static final int HIDE_ON_CLOSE = 1;
    public static final int DISPOSE_ON_CLOSE = 2;

    protected JRootPane rootPane = new JRootPane();
    protected int defaultCloseOperation = HIDE_ON_CLOSE;

    public JFrame() { initFrame(); }
    public JFrame(String title) { super(title); initFrame(); }
    public JFrame(java.awt.GraphicsConfiguration gc) { initFrame(); }
    public JFrame(String title, java.awt.GraphicsConfiguration gc) { super(title); initFrame(); }

    private void initFrame() {
        // Real Frame defaults to BorderLayout — ContainableFrame.revalidateMinimumSize()
        // calls frame.getLayout().minimumLayoutSize(this), which NPEs without one and
        // aborts the entire validate cascade (ClientUI.Layout → ContainableFrame.revalidate
        // → frame layout → null → throw). Without that cascade running to completion,
        // the JTabbedPane's trailing component never gets its inner BorderLayout invoked
        // so the bottom toolbar buttons stay at (0,0,0,0).
        setLayout(new java.awt.BorderLayout());
        super.add(rootPane, -1);
        rootPane.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public Component add(Component comp) {
        if (comp == rootPane) return super.add(comp);
        return getContentPane().add(comp);
    }
    @Override
    public Component add(Component comp, int index) {
        if (comp == rootPane) return super.add(comp, index);
        return getContentPane().add(comp, index);
    }
    @Override
    public void add(Component comp, Object constraints) {
        if (comp == rootPane) { super.add(comp, -1); return; }
        getContentPane().add(comp, constraints);
    }

    @Override
    public void setSize(int w, int h) {
        super.setSize(w, h);
        if (rootPane != null) rootPane.setBounds(0, 0, w, h);
    }
    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        if (rootPane != null) rootPane.setBounds(0, 0, w, h);
    }

    public int getDefaultCloseOperation() { return defaultCloseOperation; }
    public void setDefaultCloseOperation(int op) { if(op<DO_NOTHING_ON_CLOSE||op>EXIT_ON_CLOSE)throw new IllegalArgumentException("close operation");int old=defaultCloseOperation;defaultCloseOperation=op;firePropertyChange("defaultCloseOperation",old,op); }
    @Override protected void processWindowEvent(java.awt.event.WindowEvent event) {
        super.processWindowEvent(event);
        if(event.getID()==java.awt.event.WindowEvent.WINDOW_CLOSING){
            if(defaultCloseOperation==HIDE_ON_CLOSE)setVisible(false);
            else if(defaultCloseOperation==DISPOSE_ON_CLOSE)dispose();
            else if(defaultCloseOperation==EXIT_ON_CLOSE)System.exit(0);
        }
    }
    @Override public JRootPane getRootPane() { return rootPane; }
    @Override public Container getContentPane() { return rootPane.getContentPane(); }
    @Override public void setContentPane(Container c) { rootPane.setContentPane(c); }
    @Override public JLayeredPane getLayeredPane() { return rootPane.getLayeredPane(); }
    @Override public void setLayeredPane(JLayeredPane lp) { rootPane.setLayeredPane(lp); }
    @Override public Component getGlassPane() { return rootPane.getGlassPane(); }
    @Override public void setGlassPane(Component g) { rootPane.setGlassPane(g); }
    public JMenuBar getJMenuBar() { return rootPane.getJMenuBar(); }
    public void setJMenuBar(JMenuBar mb) { rootPane.setJMenuBar(mb); }
    public void setIconImage(java.awt.Image image) {}
    public java.awt.Image getIconImage() { return null; }
    public void setIconImages(java.util.List<? extends java.awt.Image> icons) {}
    public java.util.List<java.awt.Image> getIconImages() { return new java.util.ArrayList<>(); }
}
