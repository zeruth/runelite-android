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
import java.awt.Component;
import java.util.EventObject;
import javax.swing.table.TableCellEditor;
import javax.swing.tree.TreeCellEditor;
public class DefaultCellEditor extends AbstractCellEditor implements TableCellEditor, TreeCellEditor {
    private static final long serialVersionUID=1L;
    protected JComponent editorComponent;
    protected int clickCountToStart=1;
    public DefaultCellEditor(JTextField field){editorComponent=field;clickCountToStart=2;field.addActionListener(e->stopCellEditing());}
    public DefaultCellEditor(JCheckBox box){editorComponent=box;box.addActionListener(e->stopCellEditing());}
    public DefaultCellEditor(JComboBox<?> combo){editorComponent=combo;combo.addActionListener(e->stopCellEditing());}
    public Component getComponent(){return editorComponent;}
    public void setClickCountToStart(int count){clickCountToStart=count;}
    public int getClickCountToStart(){return clickCountToStart;}
    public boolean isCellEditable(EventObject event){return !(event instanceof java.awt.event.MouseEvent)||((java.awt.event.MouseEvent)event).getClickCount()>=clickCountToStart;}
    public Object getCellEditorValue(){if(editorComponent instanceof JTextField)return ((JTextField)editorComponent).getText();if(editorComponent instanceof JCheckBox)return ((JCheckBox)editorComponent).isSelected();return ((JComboBox<?>)editorComponent).getSelectedItem();}
    protected void setValue(Object value){if(editorComponent instanceof JTextField)((JTextField)editorComponent).setText(value==null?"":value.toString());else if(editorComponent instanceof JCheckBox)((JCheckBox)editorComponent).setSelected(Boolean.TRUE.equals(value));else ((JComboBox<?>)editorComponent).setSelectedItem(value);}
    public Component getTableCellEditorComponent(JTable table,Object value,boolean selected,int row,int column){setValue(value);return editorComponent;}
    public Component getTreeCellEditorComponent(JTree tree,Object value,boolean selected,boolean expanded,boolean leaf,int row){setValue(value);return editorComponent;}
}
