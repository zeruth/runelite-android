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
public class InternationalFormatter extends DefaultFormatter {
    private java.text.Format format;private Comparable minimum,maximum;
    public InternationalFormatter(){}
    public InternationalFormatter(java.text.Format format){this.format=format;}
    public void setFormat(java.text.Format value){format=value;}
    public java.text.Format getFormat(){return format;}
    public void setMinimum(Comparable value){minimum=value;}
    public Comparable getMinimum(){return minimum;}
    public void setMaximum(Comparable value){maximum=value;}
    public Comparable getMaximum(){return maximum;}
    public Object stringToValue(String text)throws java.text.ParseException {
        Object value;if(format==null)value=super.stringToValue(text);else{java.text.ParsePosition position=new java.text.ParsePosition(0);value=format.parseObject(text,position);if(value==null||position.getIndex()!=text.length())throw new java.text.ParseException("Invalid value",Math.max(0,position.getErrorIndex()));}
        if(value instanceof Number&&getValueClass()!=null){Number n=(Number)value;Class<?> c=getValueClass();if(c==Integer.class)value=n.intValue();else if(c==Long.class)value=n.longValue();else if(c==Float.class)value=n.floatValue();else if(c==Double.class)value=n.doubleValue();else if(c==Short.class)value=n.shortValue();else if(c==Byte.class)value=n.byteValue();}
        try{if(minimum!=null&&minimum.compareTo(value)>0||maximum!=null&&maximum.compareTo(value)<0)throw new java.text.ParseException("Value out of bounds",0);}catch(ClassCastException e){throw new java.text.ParseException("Invalid value type",0);}return value;
    }
    public String valueToString(Object value)throws java.text.ParseException{return value==null?"":format==null?super.valueToString(value):format.format(value);}
}
