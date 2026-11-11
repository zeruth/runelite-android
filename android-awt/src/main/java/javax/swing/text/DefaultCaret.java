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
package javax.swing.text;

import java.awt.*;
import javax.swing.event.*;

public class DefaultCaret implements Caret, DocumentListener, java.io.Serializable {
    public static final int UPDATE_WHEN_ON_EDT=0, NEVER_UPDATE=1, ALWAYS_UPDATE=2;
    private transient JTextComponent component;
    private int dot,mark,blinkRate=500,updatePolicy=UPDATE_WHEN_ON_EDT;
    private boolean visible,selectionVisible=true,blinkOn=true;
    private transient javax.swing.Timer blinkTimer;
    private final java.awt.event.FocusListener focusListener=new java.awt.event.FocusAdapter(){public void focusGained(java.awt.event.FocusEvent e){setVisible(true);}public void focusLost(java.awt.event.FocusEvent e){setVisible(false);}};
    private Point magic;
    private final EventListenerList listeners=new EventListenerList();
    public void install(JTextComponent c){component=c;dot=mark=0;c.getDocument().addDocumentListener(this);c.addFocusListener(focusListener);setVisible(c.isFocusOwner());}
    public void deinstall(JTextComponent c){c.getDocument().removeDocumentListener(this);c.removeFocusListener(focusListener);if(blinkTimer!=null)blinkTimer.stop();component=null;}
    public void paint(Graphics g){if(!visible||!blinkOn||component==null)return;try{Rectangle r=component.modelToView(dot);g.setColor(component.getCaretColor());g.drawLine(r.x,r.y,r.x,r.y+r.height-1);}catch(BadLocationException ignored){}}
    public void addChangeListener(ChangeListener l){listeners.add(ChangeListener.class,l);}
    public void removeChangeListener(ChangeListener l){listeners.remove(ChangeListener.class,l);}
    public ChangeListener[] getChangeListeners(){return listeners.getListeners(ChangeListener.class);}
    protected void fireStateChanged(){ChangeEvent e=new ChangeEvent(this);for(ChangeListener l:getChangeListeners())l.stateChanged(e);if(component!=null)component.repaint();}
    public boolean isVisible(){return visible;}public void setVisible(boolean value){visible=value;blinkOn=true;restartBlink();if(component!=null)component.repaint();}
    private void restartBlink(){if(blinkTimer!=null)blinkTimer.stop();if(visible&&blinkRate>0){blinkTimer=new javax.swing.Timer(blinkRate,e->{blinkOn=!blinkOn;if(component!=null)component.repaint();});blinkTimer.start();}}
    public boolean isSelectionVisible(){return selectionVisible;}public void setSelectionVisible(boolean value){selectionVisible=value;if(component!=null)component.repaint();}
    public void setMagicCaretPosition(Point value){magic=value==null?null:new Point(value);}public Point getMagicCaretPosition(){return magic==null?null:new Point(magic);}
    public void setBlinkRate(int value){if(value<0)throw new IllegalArgumentException("blink rate");blinkRate=value;restartBlink();}public int getBlinkRate(){return blinkRate;}
    public void setUpdatePolicy(int value){if(value<0||value>2)throw new IllegalArgumentException("policy");updatePolicy=value;}public int getUpdatePolicy(){return updatePolicy;}
    public int getDot(){return dot;}public int getMark(){return mark;}
    private int bound(int p){return Math.max(0,Math.min(component==null?0:component.getDocument().getLength(),p));}
    public void setDot(int p){p=bound(p);if(dot!=p||mark!=p){dot=mark=p;fireStateChanged();}}
    public void moveDot(int p){p=bound(p);if(dot!=p){dot=p;fireStateChanged();}}
    public void insertUpdate(DocumentEvent e){if(updatePolicy==NEVER_UPDATE)return;int at=e.getOffset(),n=e.getLength();if(dot>=at)dot+=n;if(mark>=at)mark+=n;dot=bound(dot);mark=bound(mark);fireStateChanged();}
    public void removeUpdate(DocumentEvent e){int at=e.getOffset(),end=at+e.getLength();dot=dot>=end?dot-e.getLength():dot>at?at:dot;mark=mark>=end?mark-e.getLength():mark>at?at:mark;dot=bound(dot);mark=bound(mark);fireStateChanged();}
    public void changedUpdate(DocumentEvent e){if(component!=null)component.repaint();}
}
