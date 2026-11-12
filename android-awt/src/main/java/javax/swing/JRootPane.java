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

public class JRootPane extends JComponent {
    private static final String uiClassID = "RootPaneUI";
    public javax.swing.plaf.RootPaneUI getUI() { return (javax.swing.plaf.RootPaneUI) ui; }
    public void setUI(javax.swing.plaf.RootPaneUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }
    private static final long serialVersionUID = 1L;

    public static final int NONE = 0;
    public static final int FRAME = 1;
    public static final int PLAIN_DIALOG = 2;
    public static final int INFORMATION_DIALOG = 3;
    public static final int ERROR_DIALOG = 4;
    public static final int COLOR_CHOOSER_DIALOG = 5;
    public static final int FILE_CHOOSER_DIALOG = 6;
    public static final int QUESTION_DIALOG = 7;
    public static final int WARNING_DIALOG = 8;

    protected Container contentPane = new JPanel(new java.awt.BorderLayout());
    protected JLayeredPane layeredPane = new JLayeredPane();
    protected Component glassPane = new JPanel();
    protected JMenuBar menuBar;

    private JButton defaultButton;
    private int decorationStyle;
    public JRootPane() {
        setLayout(null);layeredPane.add(contentPane,JLayeredPane.FRAME_CONTENT_LAYER);
        super.add(layeredPane,null,-1);glassPane.setVisible(false);super.add(glassPane,null,0);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,0),"pressDefault");
        getActionMap().put("pressDefault",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(defaultButton!=null&&defaultButton.isEnabled())defaultButton.doClick();}});
    }
    public Container getContentPane(){return contentPane;}
    public void setContentPane(Container value){if(value==null)throw new java.awt.IllegalComponentStateException("content pane cannot be null");Container old=contentPane;layeredPane.remove(contentPane);contentPane=value;layeredPane.add(value,JLayeredPane.FRAME_CONTENT_LAYER);firePropertyChange("contentPane",old,value);revalidate();}
    public Component add(Component component){return contentPane.add(component);}
    public Component add(Component component,int index){return contentPane.add(component,index);}
    public void add(Component component,Object constraints){contentPane.add(component,constraints);}
    public void setBounds(int x,int y,int width,int height){super.setBounds(x,y,width,height);doLayout();}
    public void setSize(int width,int height){super.setSize(width,height);doLayout();}
    public void doLayout(){if(layeredPane==null)return;java.awt.Insets in=getInsets();int width=Math.max(0,getWidth()-in.left-in.right),height=Math.max(0,getHeight()-in.top-in.bottom);layeredPane.setBounds(in.left,in.top,width,height);glassPane.setBounds(in.left,in.top,width,height);int menuHeight=menuBar!=null&&menuBar.isVisible()?menuBar.getPreferredSize().height:0;if(menuBar!=null)menuBar.setBounds(0,0,width,menuHeight);contentPane.setBounds(0,menuHeight,width,Math.max(0,height-menuHeight));}
    public java.awt.Dimension getPreferredSize(){if(isPreferredSizeSet())return super.getPreferredSize();java.awt.Dimension content=contentPane.getPreferredSize(),menu=menuBar==null?new java.awt.Dimension():menuBar.getPreferredSize();return new java.awt.Dimension(Math.max(content.width,menu.width),content.height+menu.height);}
    public JLayeredPane getLayeredPane(){return layeredPane;}
    public void setLayeredPane(JLayeredPane value){java.util.Objects.requireNonNull(value);JLayeredPane old=layeredPane;super.remove(old);layeredPane=value;value.add(contentPane,JLayeredPane.FRAME_CONTENT_LAYER);if(menuBar!=null)value.add(menuBar,JLayeredPane.FRAME_CONTENT_LAYER);super.add(value,null,-1);firePropertyChange("layeredPane",old,value);revalidate();}
    public Component getGlassPane(){return glassPane;}
    public void setGlassPane(Component value){java.util.Objects.requireNonNull(value);Component old=glassPane;boolean visible=old.isVisible();super.remove(old);glassPane=value;value.setVisible(visible);super.add(value,null,0);firePropertyChange("glassPane",old,value);revalidate();}
    public JMenuBar getJMenuBar(){return menuBar;}
    public void setJMenuBar(JMenuBar value){JMenuBar old=menuBar;if(old!=null)layeredPane.remove(old);menuBar=value;if(value!=null)layeredPane.add(value,JLayeredPane.FRAME_CONTENT_LAYER);firePropertyChange("JMenuBar",old,value);revalidate();}
    public JButton getDefaultButton(){return defaultButton;}
    public void setDefaultButton(JButton value){JButton old=defaultButton;defaultButton=value;firePropertyChange("defaultButton",old,value);}
    public int getWindowDecorationStyle(){return decorationStyle;}
    public void setWindowDecorationStyle(int style){if(style<NONE||style>WARNING_DIALOG)throw new IllegalArgumentException("decoration style");int old=decorationStyle;decorationStyle=style;firePropertyChange("windowDecorationStyle",old,style);}
}
