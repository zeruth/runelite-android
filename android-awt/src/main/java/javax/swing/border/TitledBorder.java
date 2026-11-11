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
package javax.swing.border;
import java.awt.*;
import javax.swing.*;
/** A border with a measured title and reserved title insets. */
public class TitledBorder extends AbstractBorder {
    public static final int DEFAULT_POSITION=0,ABOVE_TOP=1,TOP=2,BELOW_TOP=3,ABOVE_BOTTOM=4,BOTTOM=5,BELOW_BOTTOM=6;
    public static final int DEFAULT_JUSTIFICATION=0,LEFT=1,CENTER=2,RIGHT=3,LEADING=4,TRAILING=5;
    protected String title;protected Border border;protected int titlePosition,titleJustification;protected Font titleFont;protected Color titleColor;
    public TitledBorder(String title){this(null,title);}
    public TitledBorder(Border border){this(border,"");}
    public TitledBorder(Border border,String title){this(border,title,LEADING,TOP);}
    public TitledBorder(Border border,String title,int justification,int position){this(border,title,justification,position,null,null);}
    public TitledBorder(Border border,String title,int justification,int position,Font font){this(border,title,justification,position,font,null);}
    public TitledBorder(Border border,String title,int justification,int position,Font font,Color color){this.border=border;this.title=title;setTitleJustification(justification);setTitlePosition(position);titleFont=font;titleColor=color;}
    public String getTitle(){return title;}public void setTitle(String value){title=value;}
    public Border getBorder(){return border==null?new EtchedBorder():border;}public void setBorder(Border value){border=value;}
    public int getTitlePosition(){return titlePosition;}public void setTitlePosition(int value){if(value<0||value>6)throw new IllegalArgumentException("position");titlePosition=value;}
    public int getTitleJustification(){return titleJustification;}public void setTitleJustification(int value){if(value<0||value>5)throw new IllegalArgumentException("justification");titleJustification=value;}
    public Font getTitleFont(){return titleFont;}public void setTitleFont(Font value){titleFont=value;}
    public Color getTitleColor(){return titleColor;}public void setTitleColor(Color value){titleColor=value;}
    private Font font(Component c){return titleFont==null?c.getFont():titleFont;}
    private boolean lower(){return titlePosition>=ABOVE_BOTTOM;}
    public Insets getBorderInsets(Component c){Insets in=getBorder().getBorderInsets(c);Insets result=new Insets(in.top+2,in.left+4,in.bottom+2,in.right+4);if(title!=null&&!title.isEmpty()){int height=c.getFontMetrics(font(c)).getHeight();if(lower())result.bottom+=height;else result.top+=height;}return result;}
    public Insets getBorderInsets(Component c,Insets in){Insets value=getBorderInsets(c);in.set(value.top,value.left,value.bottom,value.right);return in;}
    public boolean isBorderOpaque(){return false;}
    public void paintBorder(Component c,Graphics g,int x,int y,int width,int height){
        if(title==null||title.isEmpty()){getBorder().paintBorder(c,g,x,y,width,height);return;}
        FontMetrics metrics=c.getFontMetrics(font(c));int textHeight=metrics.getHeight(),textWidth=metrics.stringWidth(title);int position=titlePosition==DEFAULT_POSITION?TOP:titlePosition;
        int inset=(position==ABOVE_TOP||position==BELOW_BOTTOM)?textHeight:(position==TOP||position==BOTTOM)?textHeight/2:0;
        int by=lower()?y:y+inset,bh=height-inset;getBorder().paintBorder(c,g,x,by,width,bh);
        int tx=x+6;if(titleJustification==CENTER)tx=x+(width-textWidth)/2;else if(titleJustification==RIGHT||titleJustification==TRAILING)tx=x+width-textWidth-6;
        int baseline=lower()?y+height-metrics.getDescent()-2:y+metrics.getAscent()+2;
        Color old=g.getColor();Font oldFont=g.getFont();g.setColor(c.getBackground());g.fillRect(tx-2,baseline-metrics.getAscent(),textWidth+4,textHeight);g.setFont(font(c));g.setColor(titleColor==null?c.getForeground():titleColor);g.drawString(title,tx,baseline);g.setColor(old);g.setFont(oldFont);
    }
    public Dimension getMinimumSize(Component c){Insets in=getBorderInsets(c);return new Dimension(in.left+in.right+(title==null?0:c.getFontMetrics(font(c)).stringWidth(title)),in.top+in.bottom);}
}
