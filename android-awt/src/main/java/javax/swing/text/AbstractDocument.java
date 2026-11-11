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

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.event.*;
import javax.swing.undo.AbstractUndoableEdit;

/** Shared in-memory document: editing, filters, positions, line elements and undo events. */
public abstract class AbstractDocument implements Document, Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ElementNameAttribute="$ename";
    private final StringBuilder content = new StringBuilder();
    private final Map<Object,Object> properties = new HashMap<>();
    protected final EventListenerList listenerList = new EventListenerList();
    private final List<WeakReference<Mark>> positions = new ArrayList<>();
    private DocumentFilter filter;
    private boolean notifying;
    private final java.util.concurrent.locks.ReentrantReadWriteLock documentLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
    private final Element root = new LineRoot();
    protected AbstractDocument() {}
    public abstract static class AbstractElement implements Element {}
    public abstract static class BranchElement extends AbstractElement {}
    public abstract static class LeafElement extends AbstractElement {}
    public abstract static class ElementEdit {}
    public abstract static class Content {
        public abstract Position createPosition(int offset) throws BadLocationException;
        public abstract int length();
        public abstract String getString(int where,int len) throws BadLocationException;
        public abstract void getChars(int where,int len,Segment txt) throws BadLocationException;
    }
    public void setDocumentFilter(DocumentFilter filter) { this.filter=filter; }
    public DocumentFilter getDocumentFilter() { return filter; }
    public int getLength() { readLock(); try { return content.length(); } finally { readUnlock(); } }
    public void addDocumentListener(DocumentListener l) { listenerList.add(DocumentListener.class,l); }
    public void removeDocumentListener(DocumentListener l) { listenerList.remove(DocumentListener.class,l); }
    public DocumentListener[] getDocumentListeners() { return listenerList.getListeners(DocumentListener.class); }
    public void addUndoableEditListener(UndoableEditListener l) { listenerList.add(UndoableEditListener.class,l); }
    public void removeUndoableEditListener(UndoableEditListener l) { listenerList.remove(UndoableEditListener.class,l); }
    public UndoableEditListener[] getUndoableEditListeners() { return listenerList.getListeners(UndoableEditListener.class); }
    public Object getProperty(Object key) { synchronized(properties) { return properties.get(key); } }
    public void putProperty(Object key,Object value) { synchronized(properties) { if(value==null)properties.remove(key);else properties.put(key,value); } }
    private final DocumentFilter.FilterBypass bypass = new DocumentFilter.FilterBypass() {
        public Document getDocument() { return AbstractDocument.this; }
        public void remove(int offset,int length) throws BadLocationException { change(offset,length,"",null,true); }
        public void insertString(int offset,String text,AttributeSet a) throws BadLocationException { change(offset,0,text,a,true); }
        public void replace(int offset,int length,String text,AttributeSet a) throws BadLocationException {
            range(offset,length); if(length>0)remove(offset,length); if(text!=null&&!text.isEmpty())insertString(offset,text,a);
        }
    };
    public void remove(int offset,int length) throws BadLocationException { writeLock(); try { if(filter==null)bypass.remove(offset,length);else filter.remove(bypass,offset,length); } finally { writeUnlock(); } }
    public void insertString(int offset,String text,AttributeSet a) throws BadLocationException {
        if(text==null||text.isEmpty())return;
        if(Boolean.TRUE.equals(getProperty("filterNewlines")))text=text.replace('\n',' ');
        writeLock(); try { if(filter==null)bypass.insertString(offset,text,a);else filter.insertString(bypass,offset,text,a); } finally { writeUnlock(); }
    }
    public void replace(int offset,int length,String text,AttributeSet a) throws BadLocationException { writeLock(); try { if(filter==null)bypass.replace(offset,length,text,a);else filter.replace(bypass,offset,length,text,a); } finally { writeUnlock(); } }
    private void range(int offset,int length) throws BadLocationException { if(offset<0||length<0||offset>content.length()-length)throw new BadLocationException("Invalid document range",offset); }
    protected void inserted(int offset,String text,AttributeSet attrs) {}
    protected void removed(int offset,int length) {}
    /** Subclasses preserve edit-local formatting along with removed/inserted text. */
    protected Object captureEditState(int offset,int length) { return null; }
    protected void restoreEditState(int offset,int length,Object state) {}
    private void change(int offset,int length,String value,AttributeSet attrs,boolean undoable) throws BadLocationException {
        change(offset,length,value,attrs,undoable,null);
    }
    private void change(int offset,int length,String value,AttributeSet attrs,boolean undoable,Object restoredState) throws BadLocationException {
        documentLock.writeLock().lock();
        try {
        if(notifying)throw new IllegalStateException("Cannot mutate document during notification");
        range(offset,length); String added=value==null?"":value; if(length==0&&added.isEmpty())return;
        String removedText=content.substring(offset,offset+length);
        Object beforeState=undoable?captureEditState(offset,length):null;
        if(length>0){removed(offset,length);content.delete(offset,offset+length);movePositions(offset,length,0);}
        if(!added.isEmpty()){content.insert(offset,added);movePositions(offset,0,added.length());inserted(offset,added,attrs);}
        if(restoredState!=null)restoreEditState(offset,added.length(),restoredState);
        Object afterState=undoable?captureEditState(offset,added.length()):null;
        Change event=new Change(offset,length>0?length:added.length(),length>0?DocumentEvent.EventType.REMOVE:DocumentEvent.EventType.INSERT,removedText,added,attrs,beforeState,afterState);
        notifying=true;
        try {
            for(DocumentListener listener:getDocumentListeners()) {
                if(length>0)listener.removeUpdate(event);else listener.insertUpdate(event);
            }
        } finally { notifying=false; }
        if(undoable)fireUndoableEditUpdate(new UndoableEditEvent(this,event));
        } finally { documentLock.writeLock().unlock(); }
    }
    private void movePositions(int offset,int removed,int added) {
        for(Iterator<WeakReference<Mark>> iterator=positions.iterator();iterator.hasNext();) {
            Mark mark=iterator.next().get();if(mark==null){iterator.remove();continue;}
            if(removed>0&&mark.offset>offset)mark.offset=Math.max(offset,mark.offset-removed);
            if(added>0&&mark.offset>=offset&&mark.offset!=0)mark.offset+=added;
        }
    }
    public String getText(int offset,int length) throws BadLocationException { readLock(); try { range(offset,length);return content.substring(offset,offset+length); } finally { readUnlock(); } }
    public void getText(int offset,int length,Segment segment) throws BadLocationException { String value=getText(offset,length);segment.array=value.toCharArray();segment.offset=0;segment.count=value.length(); }
    public Position getStartPosition() { return () -> 0; }
    public Position getEndPosition() { return () -> getLength()+1; }
    private static final class Mark implements Position { int offset;Mark(int offset){this.offset=offset;}public int getOffset(){return offset;} }
    public Position createPosition(int offset) throws BadLocationException { writeLock(); try { if(offset<0)throw new BadLocationException("Negative position",offset);Mark mark=new Mark(offset);positions.add(new WeakReference<>(mark));return mark; } finally { writeUnlock(); } }
    public Element[] getRootElements() { return new Element[]{root}; }
    public Element getDefaultRootElement() { return root; }
    public void render(Runnable task) { readLock(); try { task.run(); } finally { readUnlock(); } }
    protected void writeLock() { documentLock.writeLock().lock(); }
    protected void writeUnlock() { documentLock.writeLock().unlock(); }
    public void readLock() { documentLock.readLock().lock(); }
    public void readUnlock() { documentLock.readLock().unlock(); }
    protected void fireChangedUpdate(DocumentEvent event) { for(DocumentListener listener:getDocumentListeners())listener.changedUpdate(event); }
    protected void fireUndoableEditUpdate(UndoableEditEvent event) { for(UndoableEditListener listener:getUndoableEditListeners())listener.undoableEditHappened(event); }
    private int[] lineStarts() { readLock(); try { List<Integer> starts=new ArrayList<>();starts.add(0);for(int i=0;i<content.length();i++)if(content.charAt(i)=='\n')starts.add(i+1);int[] result=new int[starts.size()];for(int i=0;i<result.length;i++)result[i]=starts.get(i);return result; } finally { readUnlock(); } }
    private final class LineRoot implements Element {
        public Document getDocument(){return AbstractDocument.this;}public Element getParentElement(){return null;}
        public String getName(){return "paragraph";}public AttributeSet getAttributes(){return SimpleAttributeSet.EMPTY;}
        public int getStartOffset(){return 0;}public int getEndOffset(){return getLength()+1;}
        public int getElementIndex(int offset){int[] starts=lineStarts();for(int i=starts.length-1;i>=0;i--)if(offset>=starts[i])return i;return 0;}
        public int getElementCount(){return lineStarts().length;}
        public Element getElement(int index){int[] starts=lineStarts();return index<0||index>=starts.length?null:new Line(starts[index],index+1<starts.length?starts[index+1]:getLength()+1);}
        public boolean isLeaf(){return false;}
    }
    private final class Line implements Element {
        private final int start,end;Line(int start,int end){this.start=start;this.end=end;}
        public Document getDocument(){return AbstractDocument.this;}public Element getParentElement(){return root;}
        public String getName(){return "content";}public AttributeSet getAttributes(){return SimpleAttributeSet.EMPTY;}
        public int getStartOffset(){return start;}public int getEndOffset(){return end;}
        public int getElementIndex(int offset){return -1;}public int getElementCount(){return 0;}public Element getElement(int index){return null;}public boolean isLeaf(){return true;}
    }
    private final class Change extends AbstractUndoableEdit implements DocumentEvent {
        private final int offset,length;private final EventType type;private final String before,after;private final AttributeSet attrs;
        private final Object beforeState,afterState;
        Change(int offset,int length,EventType type,String before,String after,AttributeSet attrs,Object beforeState,Object afterState){this.offset=offset;this.length=length;this.type=type;this.before=before;this.after=after;this.attrs=attrs;this.beforeState=beforeState;this.afterState=afterState;}
        public int getOffset(){return offset;}public int getLength(){return length;}public Document getDocument(){return AbstractDocument.this;}public EventType getType(){return type;}
        public ElementChange getChange(Element element){return null;}
        public void undo(){super.undo();try{change(offset,after.length(),before,attrs,false,beforeState);}catch(BadLocationException e){throw new javax.swing.undo.CannotUndoException();}}
        public void redo(){super.redo();try{change(offset,before.length(),after,attrs,false,afterState);}catch(BadLocationException e){throw new javax.swing.undo.CannotRedoException();}}
    }
}
