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
import javax.swing.event.*;
public class JSlider extends JComponent implements SwingConstants {
    private static final String uiClassID = "SliderUI";
    public javax.swing.plaf.SliderUI getUI() { return (javax.swing.plaf.SliderUI) ui; }
    public void setUI(javax.swing.plaf.SliderUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }
    private BoundedRangeModel model;
    private int orientation, majorTickSpacing, minorTickSpacing;
    private boolean paintTicks,paintLabels,snapToTicks,inverted;
    private final java.util.List<ChangeListener> listeners=new java.util.ArrayList<>();
    private final ChangeListener relay=e->{repaint();ChangeEvent event=new ChangeEvent(this);for(ChangeListener l:new java.util.ArrayList<>(listeners))l.stateChanged(event);};
    public JSlider(){this(HORIZONTAL,0,100,50);}
    public JSlider(int orientation){this(orientation,0,100,50);}
    public JSlider(int min,int max){this(HORIZONTAL,min,max,(min+max)/2);}
    public JSlider(int min,int max,int value){this(HORIZONTAL,min,max,value);}
    public JSlider(int orientation,int min,int max,int value){setOrientation(orientation);setModel(new DefaultBoundedRangeModel(value,0,min,max));}
    public JSlider(BoundedRangeModel model){setModel(model);}
    public void setModel(BoundedRangeModel value){if(model!=null)model.removeChangeListener(relay);model=value;model.addChangeListener(relay);repaint();}
    public BoundedRangeModel getModel(){return model;}
    public int getValue(){return model.getValue();} public void setValue(int v){model.setValue(v);}
    public int getMinimum(){return model.getMinimum();} public void setMinimum(int v){model.setMinimum(v);}
    public int getMaximum(){return model.getMaximum();} public void setMaximum(int v){model.setMaximum(v);}
    public boolean getValueIsAdjusting(){return model.getValueIsAdjusting();} public void setValueIsAdjusting(boolean v){model.setValueIsAdjusting(v);}
    public int getOrientation(){return orientation;} public void setOrientation(int v){if(v!=HORIZONTAL&&v!=VERTICAL)throw new IllegalArgumentException("orientation");orientation=v;revalidate();}
    public void addChangeListener(ChangeListener l){listeners.add(l);} public void removeChangeListener(ChangeListener l){listeners.remove(l);}
    public boolean getInverted(){return inverted;} public void setInverted(boolean b){inverted=b;repaint();}
    public void setPaintTicks(boolean b){paintTicks=b;repaint();} public boolean getPaintTicks(){return paintTicks;}
    public void setPaintLabels(boolean b){paintLabels=b;repaint();} public boolean getPaintLabels(){return paintLabels;}
    public void setSnapToTicks(boolean b){snapToTicks=b;} public boolean getSnapToTicks(){return snapToTicks;}
    public void setMajorTickSpacing(int n){majorTickSpacing=n;} public int getMajorTickSpacing(){return majorTickSpacing;}
    public void setMinorTickSpacing(int n){minorTickSpacing=n;} public int getMinorTickSpacing(){return minorTickSpacing;}
    @Override public Dimension getPreferredSize(){return isPreferredSizeSet()?super.getPreferredSize():orientation==HORIZONTAL?new Dimension(160,30):new Dimension(30,160);}
    @Override protected void paintComponent(Graphics g){
        if(isOpaque()){g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight());}
        double f=getMaximum()==getMinimum()?0:(getValue()-(double)getMinimum())/(getMaximum()-(double)getMinimum());if(inverted)f=1-f;
        g.setColor(getForeground());
        if(orientation==HORIZONTAL){int y=getHeight()/2,x=6+(int)(f*Math.max(0,getWidth()-12));g.drawLine(6,y,getWidth()-6,y);g.fillOval(x-5,y-5,10,10);}
        else{int x=getWidth()/2,y=getHeight()-6-(int)(f*Math.max(0,getHeight()-12));g.drawLine(x,6,x,getHeight()-6);g.fillOval(x-5,y-5,10,10);}
    }
}
