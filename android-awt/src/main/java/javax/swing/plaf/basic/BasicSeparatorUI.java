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
package javax.swing.plaf.basic;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
public class BasicSeparatorUI extends SeparatorUI {
    protected Color shadow,highlight;
    public static ComponentUI createUI(JComponent component){return new BasicSeparatorUI();}
    public void installUI(JComponent component){installDefaults((JSeparator)component);installListeners((JSeparator)component);}
    public void uninstallUI(JComponent component){uninstallDefaults((JSeparator)component);uninstallListeners((JSeparator)component);}
    protected void installDefaults(JSeparator separator){LookAndFeel.installColors(separator,"Separator.background","Separator.foreground");shadow=UIManager.getColor("Separator.shadow");highlight=UIManager.getColor("Separator.highlight");}
    protected void uninstallDefaults(JSeparator separator){shadow=highlight=null;}
    // Subclasses install their listeners; the basic separator has no interactive state.
    protected void installListeners(JSeparator separator){}
    protected void uninstallListeners(JSeparator separator){}
    public void paint(Graphics graphics,JComponent component){
        boolean vertical=((JSeparator)component).getOrientation()==SwingConstants.VERTICAL;
        Color dark=shadow==null?component.getForeground():shadow,light=highlight==null?component.getBackground():highlight;
        graphics.setColor(dark==null?Color.GRAY:dark);
        if(vertical)graphics.drawLine(0,0,0,component.getHeight());else graphics.drawLine(0,0,component.getWidth(),0);
        if(light!=null){graphics.setColor(light);if(vertical)graphics.drawLine(1,0,1,component.getHeight());else graphics.drawLine(0,1,component.getWidth(),1);}
    }
    public Dimension getPreferredSize(JComponent component){return ((JSeparator)component).getOrientation()==SwingConstants.VERTICAL?new Dimension(2,0):new Dimension(0,2);}
}
