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
import javax.swing.text.*;
import java.awt.*;
public class JTextPane extends JEditorPane {
    public JTextPane() { setDocument(new DefaultStyledDocument()); }
    public JTextPane(Document doc) { this(); if(doc!=null)setDocument(doc); }
    public JTextPane(StyledDocument doc) { this((Document)doc); }
    public StyledDocument getStyledDocument() { return (StyledDocument)getDocument(); }
    public void setStyledDocument(StyledDocument doc) { setDocument(doc); }
    private int layoutStyled(Graphics g) {
        String text=getText(); Insets in=getInsets();int width=Math.max(1,getWidth()-in.left-in.right);int x=0,y=in.top;
        Font base=getFont();FontMetrics metrics=getFontMetrics(base);int line=metrics.getHeight();
        DefaultStyledDocument doc=(DefaultStyledDocument)getDocument();
        for(int i=0;i<text.length();i++){
            AttributeSet a=doc.attributesAt(i);Font font=base.deriveFont((StyleConstants.isBold(a)?Font.BOLD:0)|(StyleConstants.isItalic(a)?Font.ITALIC:0));
            FontMetrics fm=getFontMetrics(font);char ch=text.charAt(i);int cw=fm.charWidth(ch);
            if(ch=='\n'){y+=line;x=0;continue;}
            if(x>0 && x+cw>width){y+=line;x=0;}
            if(g!=null){g.setFont(font);Object color=a.getAttribute(StyleConstants.Foreground);g.setColor(color instanceof Color?(Color)color:getForeground());g.drawString(String.valueOf(ch),in.left+x,y+fm.getAscent());}
            x+=cw;
        }
        return y+line+in.bottom;
    }
    @Override public Dimension getPreferredSize(){
        if(isPreferredSizeSet() || !(getDocument() instanceof DefaultStyledDocument))return super.getPreferredSize();
        Dimension d=super.getPreferredSize();return new Dimension(d.width,layoutStyled(null));
    }
    @Override protected void paintComponent(Graphics g){
        if(!(getDocument() instanceof DefaultStyledDocument)){super.paintComponent(g);return;}
        if(isOpaque()){g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight());}
        layoutStyled(g);
    }
}
