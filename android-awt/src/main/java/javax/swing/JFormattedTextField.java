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

import java.io.Serializable;
import java.text.ParseException;

public class JFormattedTextField extends JTextField {
    private static final String uiClassID = "FormattedTextFieldUI";
    public String getUIClassID() { return uiClassID; }
    private static final long serialVersionUID = 1L;

    public abstract static class AbstractFormatter implements Serializable, Cloneable {
        private JFormattedTextField textField;
        public abstract Object stringToValue(String text) throws ParseException;
        public abstract String valueToString(Object value) throws ParseException;
        public void install(JFormattedTextField ftf) { this.textField = ftf; try{ftf.setText(valueToString(ftf.getValue()));ftf.editValid=true;}catch(ParseException e){ftf.editValid=false;} }
        protected void setEditValid(boolean valid){if(textField!=null)textField.editValid=valid;}
        protected void invalidEdit(){java.awt.Toolkit.getDefaultToolkit().beep();}
        public void uninstall() { this.textField = null; }
        protected JFormattedTextField getFormattedTextField() { return textField; }
        @Override public Object clone() {
            try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); }
        }
    }

    public abstract static class AbstractFormatterFactory {
        public abstract AbstractFormatter getFormatter(JFormattedTextField tf);
    }

    public static final int COMMIT = 0;
    public static final int COMMIT_OR_REVERT = 1;
    public static final int REVERT = 2;
    public static final int PERSIST = 3;

    private Object value;
    private AbstractFormatterFactory formatterFactory;
    private AbstractFormatter formatter;
    private boolean editValid=true;
    private int focusLostBehavior=COMMIT_OR_REVERT;
    public JFormattedTextField(){
        addActionListener(e->{try{commitEdit();}catch(ParseException invalid){editValid=false;}});
        addFocusListener(new java.awt.event.FocusAdapter(){public void focusLost(java.awt.event.FocusEvent e){if(focusLostBehavior==PERSIST)return;if(focusLostBehavior!=REVERT)try{commitEdit();return;}catch(ParseException invalid){if(focusLostBehavior==COMMIT)return;}updateFormatter();}});
    }
    public JFormattedTextField(Object value){this();setValue(value);}
    public JFormattedTextField(java.text.Format format){this(new javax.swing.text.InternationalFormatter(format));}
    public JFormattedTextField(AbstractFormatter formatter){this(new javax.swing.text.DefaultFormatterFactory(formatter));}
    public JFormattedTextField(AbstractFormatterFactory factory){this();setFormatterFactory(factory);}
    public JFormattedTextField(AbstractFormatterFactory factory,Object value){this(factory);setValue(value);}
    public void setValue(Object value){Object old=this.value;this.value=value;if(formatterFactory==null){javax.swing.text.DefaultFormatter auto=value instanceof Number?new javax.swing.text.NumberFormatter(java.text.NumberFormat.getNumberInstance()):value instanceof java.util.Date?new javax.swing.text.DateFormatter():new javax.swing.text.DefaultFormatter();if(value!=null)auto.setValueClass(value.getClass());formatterFactory=new javax.swing.text.DefaultFormatterFactory(auto);}updateFormatter();firePropertyChange("value",old,value);}
    public Object getValue(){return value;}
    private void updateFormatter(){if(formatter!=null)formatter.uninstall();formatter=formatterFactory==null?null:formatterFactory.getFormatter(this);if(formatter!=null)formatter.install(this);else setText(value==null?"":value.toString());}
    public void setFormatterFactory(AbstractFormatterFactory factory){AbstractFormatterFactory old=formatterFactory;formatterFactory=factory;updateFormatter();firePropertyChange("formatterFactory",old,factory);}
    public AbstractFormatterFactory getFormatterFactory(){return formatterFactory;}
    public AbstractFormatter getFormatter(){return formatter;}
    public void commitEdit() throws ParseException {try{Object parsed=formatter==null?getText():formatter.stringToValue(getText());Object old=value;value=parsed;editValid=true;firePropertyChange("value",old,parsed);}catch(ParseException e){editValid=false;throw e;}}
    public boolean isEditValid(){if(formatter==null)return true;try{formatter.stringToValue(getText());return true;}catch(ParseException invalid){return false;}}
    public void setFocusLostBehavior(int behavior){if(behavior<COMMIT||behavior>PERSIST)throw new IllegalArgumentException("focus lost behavior");focusLostBehavior=behavior;}
    public int getFocusLostBehavior(){return focusLostBehavior;}
}
