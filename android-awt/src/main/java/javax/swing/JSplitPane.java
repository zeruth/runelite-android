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
    public javax.swing.plaf.SplitPaneUI getUI() { return (javax.swing.plaf.SplitPaneUI) ui; }
    public void setUI(javax.swing.plaf.SplitPaneUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }

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
    public JSplitPane() {
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter(){ public void mouseDragged(java.awt.event.MouseEvent e){if(dragging)setDividerLocation(dividerPoint(e));} });
        addMouseListener(new java.awt.event.MouseAdapter(){public void mousePressed(java.awt.event.MouseEvent e){
            if(!isEnabled()||e.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;
            int point=dividerPoint(e);dragging=point>=dividerLocation&&point<dividerLocation+dividerSize;
            int cross=orientation==HORIZONTAL_SPLIT?e.getY()-getInsets().top:e.getX()-getInsets().left;
            if(dragging&&oneTouchExpandable&&cross>=2&&cross<42){oneTouch(cross<22?-1:1);dragging=false;e.consume();}
        }public void mouseReleased(java.awt.event.MouseEvent e){dragging=false;}});
    }
    private int dividerPoint(java.awt.event.MouseEvent e){return orientation==HORIZONTAL_SPLIT?e.getX()-getInsets().left:e.getY()-getInsets().top;}
    private int dividerExtent(){java.awt.Insets in=getInsets();return Math.max(0,(orientation==HORIZONTAL_SPLIT?getWidth()-in.left-in.right:getHeight()-in.top-in.bottom)-dividerSize);}
    private void oneTouch(int direction){
        int extent=dividerExtent();
        if((direction<0&&dividerLocation==extent)||(direction>0&&dividerLocation==0)){
            setDividerLocation(restoreDividerLocation<0?extent/2:restoreDividerLocation);
        }else{
            if(dividerLocation>0&&dividerLocation<extent)restoreDividerLocation=dividerLocation;
            setDividerLocation(direction<0?0:extent);collapsedSide=direction;
        }
        doLayout();
    }
    private boolean dragging;
    public JSplitPane(int newOrientation) { this(); setOrientation(newOrientation); }
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
    public int getDividerSize() { return dividerSize; }
    public void setDividerSize(int newSize) { if(newSize<0)throw new IllegalArgumentException("divider size");int old=dividerSize;dividerSize=newSize;firePropertyChange(DIVIDER_SIZE_PROPERTY,old,newSize);revalidate(); }
    public int getDividerLocation() { return dividerLocation; }
    public void setDividerLocation(int location) { int old=dividerLocation;setLastDividerLocation(old);dividerLocation=location;collapsedSide=0;firePropertyChange(DIVIDER_LOCATION_PROPERTY,old,location);revalidate();repaint(); }
    public void setDividerLocation(double proportion) { if(proportion<0||proportion>1)throw new IllegalArgumentException("proportion");setDividerLocation((int)((Math.max(0,(orientation==HORIZONTAL_SPLIT?getWidth():getHeight())-dividerSize))*proportion)); }
    public double getResizeWeight() { return resizeWeight; }
    public void setResizeWeight(double value) { if(value<0||value>1)throw new IllegalArgumentException("resize weight");double old=resizeWeight;resizeWeight=value;firePropertyChange(RESIZE_WEIGHT_PROPERTY,old,value); }
    public boolean isContinuousLayout() { return continuousLayout; }
    public void setContinuousLayout(boolean cl) { this.continuousLayout = cl; }
    public boolean isOneTouchExpandable() { return oneTouchExpandable; }
    public void setOneTouchExpandable(boolean value) { boolean old=oneTouchExpandable;oneTouchExpandable=value;firePropertyChange(ONE_TOUCH_EXPANDABLE_PROPERTY,old,value);repaint(); }
    public int getLastDividerLocation() { return lastDividerLocation; }
    public void setLastDividerLocation(int location) { int old=lastDividerLocation;lastDividerLocation=location;firePropertyChange(LAST_DIVIDER_LOCATION_PROPERTY,old,location); }
    public void resetToPreferredSizes() { dividerLocation=-1;previousExtent=-1;revalidate(); }
    public void remove(Component component) { if(component==leftComponent)leftComponent=null;if(component==rightComponent)rightComponent=null;super.remove(component); }
    public void removeAll() { leftComponent=rightComponent=null;super.removeAll(); }
    public void doLayout() {
        java.awt.Insets in=getInsets();boolean horizontal=orientation==HORIZONTAL_SPLIT;
        int extent=Math.max(0,(horizontal?getWidth()-in.left-in.right:getHeight()-in.top-in.bottom)-dividerSize);
        int cross=Math.max(0,horizontal?getHeight()-in.top-in.bottom:getWidth()-in.left-in.right);
        if(collapsedSide!=0)dividerLocation=collapsedSide<0?0:extent;
        else if(dividerLocation<0){java.awt.Dimension preferred=leftComponent==null?new java.awt.Dimension():leftComponent.getPreferredSize();dividerLocation=horizontal?preferred.width:preferred.height;}
        else if(previousExtent>=0&&extent!=previousExtent)dividerLocation+=(int)Math.round((extent-previousExtent)*resizeWeight);
        dividerLocation=Math.max(0,Math.min(extent,dividerLocation));previousExtent=extent;
        if(leftComponent!=null)leftComponent.setBounds(in.left,in.top,horizontal?dividerLocation:cross,horizontal?cross:dividerLocation);
        if(rightComponent!=null)rightComponent.setBounds(in.left+(horizontal?dividerLocation+dividerSize:0),in.top+(horizontal?0:dividerLocation+dividerSize),horizontal?extent-dividerLocation:cross,horizontal?cross:extent-dividerLocation);
    }
    public java.awt.Dimension getPreferredSize() { if(isPreferredSizeSet())return super.getPreferredSize();java.awt.Dimension a=leftComponent==null?new java.awt.Dimension():leftComponent.getPreferredSize(),b=rightComponent==null?new java.awt.Dimension():rightComponent.getPreferredSize();return orientation==HORIZONTAL_SPLIT?new java.awt.Dimension(a.width+b.width+dividerSize,Math.max(a.height,b.height)):new java.awt.Dimension(Math.max(a.width,b.width),a.height+b.height+dividerSize); }
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);java.awt.Insets in=getInsets();boolean horizontal=orientation==HORIZONTAL_SPLIT;
        int x=in.left+(horizontal?dividerLocation:0),y=in.top+(horizontal?0:dividerLocation);
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
