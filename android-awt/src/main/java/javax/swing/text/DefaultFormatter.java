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
public class DefaultFormatter extends javax.swing.JFormattedTextField.AbstractFormatter {
    private Class<?> valueClass;
    private boolean commitsOnValidEdit,overwriteMode=true,allowsInvalid=true;
    private transient AbstractDocument document;
    private transient DocumentFilter previousFilter;
    public void install(javax.swing.JFormattedTextField field){
        super.install(field);
        if(field.getDocument() instanceof AbstractDocument){document=(AbstractDocument)field.getDocument();previousFilter=document.getDocumentFilter();document.setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass bypass,int offset,String text,AttributeSet attrs)throws BadLocationException{replace(bypass,offset,0,text,attrs);}
            public void remove(FilterBypass bypass,int offset,int length)throws BadLocationException{replace(bypass,offset,length,"",null);}
            public void replace(FilterBypass bypass,int offset,int length,String text,AttributeSet attrs)throws BadLocationException{
                String inserted=text==null?"":text;int count=length;
                if(overwriteMode&&count==0&&!inserted.isEmpty())count=Math.min(inserted.length(),bypass.getDocument().getLength()-offset);
                String current=bypass.getDocument().getText(0,bypass.getDocument().getLength());
                String candidate=current.substring(0,offset)+inserted+current.substring(offset+count);boolean valid;
                try{stringToValue(candidate);valid=true;}catch(java.text.ParseException invalid){valid=false;}
                if(!valid&&!allowsInvalid){invalidEdit();return;}
                bypass.replace(offset,count,inserted,attrs);setEditValid(valid);
                if(valid&&commitsOnValidEdit)try{field.commitEdit();}catch(java.text.ParseException invalid){setEditValid(false);}
            }
        });}
    }
    public void uninstall(){if(document!=null)document.setDocumentFilter(previousFilter);document=null;previousFilter=null;super.uninstall();}
    public Object stringToValue(String text)throws java.text.ParseException {Class<?> type=valueClass;if(type==null&&getFormattedTextField()!=null&&getFormattedTextField().getValue()!=null)type=getFormattedTextField().getValue().getClass();if(type==null||type==String.class)return text;try{return type.getConstructor(String.class).newInstance(text);}catch(ReflectiveOperationException e){throw new java.text.ParseException("Invalid "+type.getSimpleName(),0);}}
    public String valueToString(Object value)throws java.text.ParseException{return value==null?"":value.toString();}
    public void setValueClass(Class<?> type){valueClass=type;}
    public Class<?> getValueClass(){return valueClass;}
    public void setCommitsOnValidEdit(boolean value){commitsOnValidEdit=value;}
    public boolean getCommitsOnValidEdit(){return commitsOnValidEdit;}
    public void setOverwriteMode(boolean value){overwriteMode=value;}
    public boolean getOverwriteMode(){return overwriteMode;}
    public void setAllowsInvalid(boolean value){allowsInvalid=value;}
    public boolean getAllowsInvalid(){return allowsInvalid;}
}
