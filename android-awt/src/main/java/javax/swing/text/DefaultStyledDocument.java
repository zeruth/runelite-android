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
import java.util.*;
public class DefaultStyledDocument extends PlainDocument implements StyledDocument {
    private final List<AttributeSet> attributes=new ArrayList<>();
    public AttributeSet attributesAt(int offset) { return offset>=0 && offset<attributes.size()?attributes.get(offset):SimpleAttributeSet.EMPTY; }
    private final StyleContext styles;
    private final java.util.Map<Position,AttributeSet> paragraphs=new java.util.LinkedHashMap<>();
    public DefaultStyledDocument(){this(new StyleContext());}
    public DefaultStyledDocument(StyleContext styles){this.styles=styles;}
    @Override protected void inserted(int offset,String text,AttributeSet attrs){AttributeSet copy=attrs==null?SimpleAttributeSet.EMPTY:attrs.copyAttributes();for(int i=0;i<text.length();i++)attributes.add(offset+i,copy);}
    @Override protected void removed(int offset,int length){for(int i=0;i<length;i++)attributes.remove(offset);}
    public Element getCharacterElement(int offset) {
        final int at=Math.max(0,Math.min(offset,getLength()));
        return new Element(){
            public Document getDocument(){return DefaultStyledDocument.this;}
            public Element getParentElement(){return null;}
            public String getName(){return "content";}
            public AttributeSet getAttributes(){return attributesAt(at);}
            public int getStartOffset(){return at;}
            public int getEndOffset(){return at+1;}
            public int getElementIndex(int pos){return -1;}
            public int getElementCount(){return 0;}
            public Element getElement(int i){return null;}
            public boolean isLeaf(){return true;}
        };
    }
    public void setCharacterAttributes(int offset,int length,AttributeSet a,boolean replace) {
        writeLock();
        try {
            if(offset<0 || length<0 || offset+length>getLength())throw new IllegalArgumentException("range");
            if(length==0)return;
            Object before=captureEditState(offset,length);
            for(int i=offset;i<offset+length;i++){SimpleAttributeSet v=new SimpleAttributeSet(replace?SimpleAttributeSet.EMPTY:attributes.get(i));v.addAttributes(a);attributes.set(i,v);}
            fireAttributesChanged(offset,length);
            publishAttributeEdit(offset,length,before,captureEditState(offset,length));
        } finally { writeUnlock(); }
    }
    private static final class FormatState {
        final List<AttributeSet> characters=new ArrayList<>();
        final Map<Integer,AttributeSet> paragraphs=new LinkedHashMap<>();
    }
    @Override protected Object captureEditState(int offset,int length) {
        FormatState state=new FormatState();
        for(int i=offset;i<offset+length;i++)state.characters.add(attributes.get(i).copyAttributes());
        for(Map.Entry<Position,AttributeSet> entry:paragraphs.entrySet())state.paragraphs.put(entry.getKey().getOffset(),entry.getValue().copyAttributes());
        return state;
    }
    @Override protected void restoreEditState(int offset,int length,Object value) {
        FormatState state=(FormatState)value;
        for(int i=0;i<length;i++)attributes.set(offset+i,state.characters.get(i));
        paragraphs.clear();
        for(Map.Entry<Integer,AttributeSet> entry:state.paragraphs.entrySet())try{paragraphs.put(createPosition(entry.getKey()),entry.getValue());}catch(BadLocationException invalid){throw new IllegalStateException(invalid);}
    }
    private void publishAttributeEdit(final int offset,final int length,final Object before,final Object after) {
        fireUndoableEditUpdate(new javax.swing.event.UndoableEditEvent(this,new javax.swing.undo.AbstractUndoableEdit(){
            public void undo(){super.undo();apply(before);}
            public void redo(){super.redo();apply(after);}
            private void apply(Object state){writeLock();try{restoreEditState(offset,length,state);fireAttributesChanged(offset,length);}finally{writeUnlock();}}
        }));
    }
    public Style addStyle(String name,Style parent){return styles.addStyle(name,parent);}
    public void removeStyle(String name){styles.removeStyle(name);}
    public Style getStyle(String name){return styles.getStyle(name);}
    public java.awt.Color getForeground(AttributeSet attrs){return styles.getForeground(attrs);}
    public java.awt.Color getBackground(AttributeSet attrs){return styles.getBackground(attrs);}
    public java.awt.Font getFont(AttributeSet attrs){return styles.getFont(attrs);}
    public void setLogicalStyle(int offset,Style style){SimpleAttributeSet attrs=new SimpleAttributeSet(getParagraphElement(offset).getAttributes());attrs.setResolveParent(style);setParagraphAttributes(offset,0,attrs,true);}
    public Style getLogicalStyle(int offset){AttributeSet parent=getParagraphElement(offset).getAttributes().getResolveParent();return parent instanceof Style?(Style)parent:null;}
    private AttributeSet paragraphAttributes(int offset){AttributeSet result=SimpleAttributeSet.EMPTY;int found=-1;for(java.util.Map.Entry<Position,AttributeSet> entry:paragraphs.entrySet())if(entry.getKey().getOffset()<=offset&&entry.getKey().getOffset()>=found){found=entry.getKey().getOffset();result=entry.getValue();}return result;}
    public Element getParagraphElement(int offset){Element line=getDefaultRootElement().getElement(getDefaultRootElement().getElementIndex(offset));return new Element(){public Document getDocument(){return DefaultStyledDocument.this;}public Element getParentElement(){return getDefaultRootElement();}public String getName(){return "paragraph";}public AttributeSet getAttributes(){return paragraphAttributes(line.getStartOffset());}public int getStartOffset(){return line.getStartOffset();}public int getEndOffset(){return line.getEndOffset();}public int getElementIndex(int offset){return offset-line.getStartOffset();}public int getElementCount(){return line.getEndOffset()-line.getStartOffset();}public Element getElement(int index){return getCharacterElement(line.getStartOffset()+index);}public boolean isLeaf(){return false;}};}
    public void setParagraphAttributes(int offset,int length,AttributeSet attrs,boolean replace) {
        writeLock();
        try {
            if(offset<0||length<0||offset+length>getLength())throw new IllegalArgumentException("range");
            Object before=captureEditState(offset,length);
            Element root=getDefaultRootElement();
            for(int i=root.getElementIndex(offset);i<=root.getElementIndex(length==0?offset:offset+length-1);i++) {
                Element line=root.getElement(i);
                SimpleAttributeSet value=new SimpleAttributeSet(replace?SimpleAttributeSet.EMPTY:paragraphAttributes(line.getStartOffset()));
                value.addAttributes(attrs);
                paragraphs.entrySet().removeIf(e->e.getKey().getOffset()==line.getStartOffset());
                try{paragraphs.put(createPosition(line.getStartOffset()),value);}catch(BadLocationException impossible){throw new IllegalStateException(impossible);}
            }
            fireAttributesChanged(offset,length);
            publishAttributeEdit(offset,length,before,captureEditState(offset,length));
        } finally { writeUnlock(); }
    }
    private void fireAttributesChanged(int offset,int length){fireChangedUpdate(new javax.swing.event.DocumentEvent(){public int getOffset(){return offset;}public int getLength(){return length;}public Document getDocument(){return DefaultStyledDocument.this;}public EventType getType(){return EventType.CHANGE;}public ElementChange getChange(Element element){return null;}});}
}
