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

public class JSplitPane extends JComponent implements javax.accessibility.Accessible {
    private static final String uiClassID = "SplitPaneUI";

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJSplitPane();
        }
        return accessibleContext;
    }

    /** Accessibility support for split panes: role SPLIT_PANE. */
    protected class AccessibleJSplitPane extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.SPLIT_PANE;
        }
    }
    private static final long serialVersionUID = 1L;
    public static final int VERTICAL_SPLIT = 0;
    public static final int HORIZONTAL_SPLIT = 1;
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";
    public static final String DIVIDER = "divider";
    public static final String ORIENTATION_PROPERTY = "orientation";
    public static final String CONTINUOUS_LAYOUT_PROPERTY = "continuousLayout";
    public static final String DIVIDER_SIZE_PROPERTY = "dividerSize";
    public static final String ONE_TOUCH_EXPANDABLE_PROPERTY = "oneTouchExpandable";
    public static final String LAST_DIVIDER_LOCATION_PROPERTY = "lastDividerLocation";
    public static final String DIVIDER_LOCATION_PROPERTY = "dividerLocation";
    public static final String RESIZE_WEIGHT_PROPERTY = "resizeWeight";

    private int orientation = HORIZONTAL_SPLIT;
    private Component leftComponent, rightComponent;
    private int dividerSize = 8;
    private int dividerLocation = -1;
    private double resizeWeight;
    private boolean continuousLayout = true;

    private int lastDividerLocation=-1;
    private boolean oneTouchExpandable;
    private int previousExtent=-1;
    private int collapsedSide;
    private int restoreDividerLocation=-1;
    /** The JDK's no-argument pane: a horizontal split holding two placeholder buttons. */
    public JSplitPane() {
        this(HORIZONTAL_SPLIT, buttonPlaceholder("SplitPane.leftButtonText", "left button"),
                buttonPlaceholder("SplitPane.rightButtonText", "right button"));
    }
    private static JButton buttonPlaceholder(String key, String fallback) {
        String text = UIManager.getString(key);
        return new JButton(text != null ? text : fallback);
    }
    // Answers the divider bounds when no UI is installed; its geometry reads only the pane it is given.
    private static final javax.swing.plaf.basic.BasicSplitPaneUI DEFAULT_GEOMETRY = new javax.swing.plaf.basic.BasicSplitPaneUI();
    private javax.swing.plaf.SplitPaneUI geometry() { javax.swing.plaf.SplitPaneUI delegate=getUI();return delegate!=null?delegate:DEFAULT_GEOMETRY; }
    private javax.swing.plaf.basic.BasicSplitPaneDivider dividerChild() {
        for(Component c:getComponents())if(c instanceof javax.swing.plaf.basic.BasicSplitPaneDivider)return (javax.swing.plaf.basic.BasicSplitPaneDivider)c;
        return null;
    }
    private void installInput() {
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter(){ public void mouseDragged(java.awt.event.MouseEvent e){if(dragging)setDividerLocation(dividerPoint(e));} });
        addMouseListener(new java.awt.event.MouseAdapter(){public void mousePressed(java.awt.event.MouseEvent e){
            if(!isEnabled()||e.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;
            int point=dividerPoint(e);dragging=point>=dividerLocation&&point<dividerLocation+dividerSize;
            int cross=orientation==HORIZONTAL_SPLIT?e.getY()-getInsets().top:e.getX()-getInsets().left;
            if(dragging&&oneTouchExpandable&&cross>=2&&cross<42){oneTouch(cross<22?-1:1);dragging=false;e.consume();}
        }public void mouseReleased(java.awt.event.MouseEvent e){dragging=false;}});
    }
    // As in the JDK, the divider location is in pane coordinates: it includes the leading inset.
    private int dividerPoint(java.awt.event.MouseEvent e){return orientation==HORIZONTAL_SPLIT?e.getX():e.getY();}
    private int dividerLead(){java.awt.Insets in=getInsets();return orientation==HORIZONTAL_SPLIT?in.left:in.top;}
    private int dividerExtent(){java.awt.Insets in=getInsets();return Math.max(0,(orientation==HORIZONTAL_SPLIT?getWidth()-in.left-in.right:getHeight()-in.top-in.bottom)-dividerSize);}
    private void oneTouch(int direction){
        int lead=dividerLead(),extent=dividerExtent();
        if((direction<0&&dividerLocation==lead+extent)||(direction>0&&dividerLocation==lead)){
            setDividerLocation(restoreDividerLocation<0?lead+extent/2:restoreDividerLocation);
        }else{
            if(dividerLocation>lead&&dividerLocation<lead+extent)restoreDividerLocation=dividerLocation;
            setDividerLocation(lead+(direction<0?0:extent));collapsedSide=direction;
        }
        doLayout();
    }
    private boolean dragging;
    public JSplitPane(int newOrientation) { installInput(); setOrientation(newOrientation); updateUI(); }
    public JSplitPane(int newOrientation, boolean continuousLayout) { this(newOrientation); this.continuousLayout = continuousLayout; }
    public JSplitPane(int newOrientation, Component newLeftComponent, Component newRightComponent) {
        this(newOrientation); setLeftComponent(newLeftComponent); setRightComponent(newRightComponent);
    }
    public JSplitPane(int newOrientation, boolean continuousLayout, Component newLeftComponent, Component newRightComponent) {
        this(newOrientation, continuousLayout); setLeftComponent(newLeftComponent); setRightComponent(newRightComponent);
    }

    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) { if(orientation!=VERTICAL_SPLIT&&orientation!=HORIZONTAL_SPLIT)throw new IllegalArgumentException("orientation");int old=this.orientation;this.orientation=orientation;previousExtent=-1;firePropertyChange(ORIENTATION_PROPERTY,old,orientation);revalidate(); }
    public Component getLeftComponent() { return leftComponent; }
    public void setLeftComponent(Component c) { if(leftComponent!=null)super.remove(leftComponent);leftComponent=c;if(c!=null)super.add(c);revalidate();repaint(); }
    public Component getRightComponent() { return rightComponent; }
    public void setRightComponent(Component c) { if(rightComponent!=null)super.remove(rightComponent);rightComponent=c;if(c!=null)super.add(c);revalidate();repaint(); }
    public Component getTopComponent() { return leftComponent; }
    public void setTopComponent(Component c) { setLeftComponent(c); }
    public Component getBottomComponent() { return rightComponent; }
    public void setBottomComponent(Component c) { setRightComponent(c); }
    public javax.swing.plaf.SplitPaneUI getUI() { return (javax.swing.plaf.SplitPaneUI) ui; }
    public void setUI(javax.swing.plaf.SplitPaneUI ui) { if (this.ui != ui) { super.setUI(ui); revalidate(); } }
    /**
     * The JDK installs the look and feel's delegate. A look and feel compiled against
     * BasicSplitPaneUI members this shadow does not have yet fails to link while installing;
     * the pane then takes the Basic delegate instead of losing its divider.
     */
    public void updateUI() {
        try {
            javax.swing.plaf.ComponentUI delegate = UIManager.getUI(this);
            setUI(delegate instanceof javax.swing.plaf.SplitPaneUI ? (javax.swing.plaf.SplitPaneUI) delegate : new javax.swing.plaf.basic.BasicSplitPaneUI());
        } catch (LinkageError incompatible) {
            ui = null;
            setUI(new javax.swing.plaf.basic.BasicSplitPaneUI());
        }
        revalidate();
    }
    public String getUIClassID() { return uiClassID; }
    public int getDividerSize() { return dividerSize; }
    // The JDK takes any size, negative included; the layout clamps what it can use.
    public void setDividerSize(int newSize) { int old=dividerSize;dividerSize=newSize;firePropertyChange(DIVIDER_SIZE_PROPERTY,old,newSize);revalidate(); }
    public int getDividerLocation() { return dividerLocation; }
    /** The JDK order: store, tell the UI, fire, then record the previous value as the last location. */
    public void setDividerLocation(int location) {
        int old=dividerLocation;dividerLocation=location;collapsedSide=0;
        javax.swing.plaf.SplitPaneUI delegate=getUI();if(delegate!=null)delegate.setDividerLocation(this,location);
        firePropertyChange(DIVIDER_LOCATION_PROPERTY,old,location);setLastDividerLocation(old);revalidate();repaint();
    }
    public void setDividerLocation(double proportionalLocation) {
        if(proportionalLocation<0.0||proportionalLocation>1.0)throw new IllegalArgumentException("proportional location must be between 0.0 and 1.0.");
        setDividerLocation((int)((double)((orientation==VERTICAL_SPLIT?getHeight():getWidth())-getDividerSize())*proportionalLocation));
    }
    /** BasicSplitPaneUI's bounds: the left/top component's minimum size and the right/bottom one's, inside the insets. */
    public int getMinimumDividerLocation() { return geometry().getMinimumDividerLocation(this); }
    public int getMaximumDividerLocation() { return geometry().getMaximumDividerLocation(this); }
    public double getResizeWeight() { return resizeWeight; }
    public void setResizeWeight(double value) { if(value<0||value>1)throw new IllegalArgumentException("JSplitPane weight must be between 0 and 1");double old=resizeWeight;resizeWeight=value;firePropertyChange(RESIZE_WEIGHT_PROPERTY,old,value); }
    public boolean isContinuousLayout() { return continuousLayout; }
    public void setContinuousLayout(boolean newContinuousLayout) { boolean old=continuousLayout;continuousLayout=newContinuousLayout;firePropertyChange(CONTINUOUS_LAYOUT_PROPERTY,old,newContinuousLayout); }
    public boolean isOneTouchExpandable() { return oneTouchExpandable; }
    public void setOneTouchExpandable(boolean value) { boolean old=oneTouchExpandable;oneTouchExpandable=value;firePropertyChange(ONE_TOUCH_EXPANDABLE_PROPERTY,old,value);repaint(); }
    public int getLastDividerLocation() { return lastDividerLocation; }
    public void setLastDividerLocation(int location) { int old=lastDividerLocation;lastDividerLocation=location;firePropertyChange(LAST_DIVIDER_LOCATION_PROPERTY,old,location); }
    public void resetToPreferredSizes() {
        dividerLocation=-1;previousExtent=-1;
        javax.swing.plaf.SplitPaneUI delegate=getUI();if(delegate!=null)delegate.resetToPreferredSizes(this);
        revalidate();
    }
    /**
     * The JDK messages the UI once the children are painted (Basic draws its drag indicator there).
     * The JDK does it from paintChildren; the shadow's paintChildren lives on Container, which the
     * host contracts cannot compile against, so the message follows the whole paint instead.
     */
    public void paint(java.awt.Graphics g) {
        super.paint(g);
        javax.swing.plaf.SplitPaneUI delegate=getUI();
        if(delegate!=null){java.awt.Graphics scratch=g.create();try{delegate.finishedPaintingChildren(this,scratch);}finally{scratch.dispose();}}
    }
    public void remove(Component component) { if(component==leftComponent)leftComponent=null;if(component==rightComponent)rightComponent=null;super.remove(component); }
    public void removeAll() { leftComponent=rightComponent=null;super.removeAll(); }
    /**
     * BasicHorizontalLayoutManager's placement: the leading size is the location less the leading
     * inset, clamped to [0, available - divider] (the JDK clamps the layout there, not to the
     * minimum/maximum divider locations, which bound only the drag and one-touch).
     */
    public void doLayout() {
        java.awt.Insets in=getInsets();boolean horizontal=orientation==HORIZONTAL_SPLIT;
        int lead=horizontal?in.left:in.top;
        int extent=Math.max(0,(horizontal?getWidth()-in.left-in.right:getHeight()-in.top-in.bottom)-dividerSize);
        int cross=Math.max(0,horizontal?getHeight()-in.top-in.bottom:getWidth()-in.left-in.right);
        if(collapsedSide!=0)dividerLocation=lead+(collapsedSide<0?0:extent);
        else if(dividerLocation<0){java.awt.Dimension preferred=leftComponent==null?new java.awt.Dimension():leftComponent.getPreferredSize();dividerLocation=lead+(horizontal?preferred.width:preferred.height);}
        else if(previousExtent>=0&&extent!=previousExtent)dividerLocation+=(int)Math.round((extent-previousExtent)*resizeWeight);
        int size=Math.max(0,Math.min(extent,dividerLocation-lead));dividerLocation=lead+size;previousExtent=extent;
        if(leftComponent!=null)leftComponent.setBounds(in.left,in.top,horizontal?size:cross,horizontal?cross:size);
        if(rightComponent!=null)rightComponent.setBounds(in.left+(horizontal?size+dividerSize:0),in.top+(horizontal?0:size+dividerSize),horizontal?extent-size:cross,horizontal?cross:extent-size);
        javax.swing.plaf.basic.BasicSplitPaneDivider divider=dividerChild();
        if(divider!=null){
            int thickness=Math.max(0,dividerSize);
            if(horizontal)divider.setBounds(dividerLocation,in.top,thickness,cross);
            else divider.setBounds(in.left,dividerLocation,cross,thickness);
            divider.doLayout();
        }
    }
    public java.awt.Dimension getPreferredSize() { if(isPreferredSizeSet())return super.getPreferredSize();java.awt.Dimension a=leftComponent==null?new java.awt.Dimension():leftComponent.getPreferredSize(),b=rightComponent==null?new java.awt.Dimension():rightComponent.getPreferredSize();return orientation==HORIZONTAL_SPLIT?new java.awt.Dimension(a.width+b.width+dividerSize,Math.max(a.height,b.height)):new java.awt.Dimension(Math.max(a.width,b.width),a.height+b.height+dividerSize); }
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        if(dividerChild()!=null)return; // a Basic UI's divider paints itself as a child
        java.awt.Insets in=getInsets();boolean horizontal=orientation==HORIZONTAL_SPLIT;
        int x=horizontal?dividerLocation:in.left,y=horizontal?in.top:dividerLocation;
        g.setColor(java.awt.Color.GRAY);
        g.fillRect(x,y,horizontal?dividerSize:getWidth()-in.left-in.right,horizontal?getHeight()-in.top-in.bottom:dividerSize);
        if(oneTouchExpandable&&dividerSize>0){
            g.setColor(java.awt.Color.DARK_GRAY);
            for(int i=0;i<2;i++){
                int center=12+i*20,tip=i==0?1:Math.max(1,dividerSize-2),back=i==0?Math.max(1,dividerSize-2):1;
                if(horizontal){g.drawLine(x+back,y+center-4,x+tip,y+center);g.drawLine(x+tip,y+center,x+back,y+center+4);}
                else{g.drawLine(x+center-4,y+back,x+center,y+tip);g.drawLine(x+center,y+tip,x+center+4,y+back);}
            }
        }
    }

}
