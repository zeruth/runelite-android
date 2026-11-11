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
import javax.swing.JFormattedTextField;
public class DefaultFormatterFactory extends JFormattedTextField.AbstractFormatterFactory {
    private JFormattedTextField.AbstractFormatter defaultFormat,displayFormat,editFormat,nullFormat;
    public DefaultFormatterFactory(){}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format){defaultFormat=format;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display){this(format);displayFormat=display;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display,JFormattedTextField.AbstractFormatter edit){this(format,display);editFormat=edit;}
    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter format,JFormattedTextField.AbstractFormatter display,JFormattedTextField.AbstractFormatter edit,JFormattedTextField.AbstractFormatter nil){this(format,display,edit);nullFormat=nil;}
    public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField field){if(field==null)return null;if(field.getValue()==null&&nullFormat!=null)return nullFormat;return field.isFocusOwner()&&editFormat!=null?editFormat:displayFormat!=null?displayFormat:defaultFormat;}
    public void setDefaultFormatter(JFormattedTextField.AbstractFormatter format){defaultFormat=format;}public JFormattedTextField.AbstractFormatter getDefaultFormatter(){return defaultFormat;}
    public void setDisplayFormatter(JFormattedTextField.AbstractFormatter format){displayFormat=format;}public JFormattedTextField.AbstractFormatter getDisplayFormatter(){return displayFormat;}
    public void setEditFormatter(JFormattedTextField.AbstractFormatter format){editFormat=format;}public JFormattedTextField.AbstractFormatter getEditFormatter(){return editFormat;}
    public void setNullFormatter(JFormattedTextField.AbstractFormatter format){nullFormat=format;}public JFormattedTextField.AbstractFormatter getNullFormatter(){return nullFormat;}
}
