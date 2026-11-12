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
import java.awt.*;
public class JColorChooser extends JComponent implements javax.accessibility.Accessible {
    private static final String uiClassID = "ColorChooserUI";
    public javax.swing.plaf.ColorChooserUI getUI() { return (javax.swing.plaf.ColorChooserUI) ui; }
    public void setUI(javax.swing.plaf.ColorChooserUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJColorChooser();
        }
        return accessibleContext;
    }

    /** Accessibility support for colour choosers: role COLOR_CHOOSER. */
    protected class AccessibleJColorChooser extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.COLOR_CHOOSER;
        }
    }
    public static final String SELECTION_MODEL_PROPERTY = "selectionModel";
    public static final String PREVIEW_PANEL_PROPERTY = "previewPanel";
    private javax.swing.colorchooser.ColorSelectionModel selectionModel;
    private JComponent previewPanel;
    private boolean dragEnabled;
    private final javax.swing.event.ChangeListener changeListener = event -> repaint();
    public JColorChooser() { this(Color.WHITE); }
    public JColorChooser(Color initialColor) { this(new javax.swing.colorchooser.DefaultColorSelectionModel(initialColor)); }
    public JColorChooser(javax.swing.colorchooser.ColorSelectionModel model) { setSelectionModel(model); }
    public Color getColor() { return selectionModel.getSelectedColor(); }
    public void setColor(Color color) { selectionModel.setSelectedColor(color); }
    public void setColor(int rgb) { setColor(new Color(rgb)); }
    public void setColor(int red, int green, int blue) { setColor(new Color(red, green, blue)); }
    public javax.swing.colorchooser.ColorSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(javax.swing.colorchooser.ColorSelectionModel model) {
        if (model == null) throw new IllegalArgumentException("selection model cannot be null");
        javax.swing.colorchooser.ColorSelectionModel old = selectionModel;
        if (old != null) old.removeChangeListener(changeListener);
        selectionModel = model; model.addChangeListener(changeListener);
        firePropertyChange(SELECTION_MODEL_PROPERTY, old, model); repaint();
    }
    public JComponent getPreviewPanel() { return previewPanel; }
    public void setPreviewPanel(JComponent preview) {
        JComponent old = previewPanel;
        if (old != null) remove(old);
        previewPanel = preview;
        if (preview != null) add(preview);
        firePropertyChange(PREVIEW_PANEL_PROPERTY, old, preview); revalidate(); repaint();
    }
    public boolean getDragEnabled() { return dragEnabled; }
    public void setDragEnabled(boolean enabled) { dragEnabled = enabled; }
    @Override public Dimension getPreferredSize() { return isPreferredSizeSet() ? super.getPreferredSize() : new Dimension(220, 60); }
    @Override public void doLayout() { if (previewPanel != null) previewPanel.setBounds(0, 32, getWidth(), Math.max(0, getHeight() - 32)); }
    @Override protected void paintComponent(Graphics graphics) {
        Color color = getColor();
        graphics.setColor(color == null ? Color.WHITE : color); graphics.fillRect(4, 4, 24, 24);
        graphics.setColor(getForeground()); graphics.drawRect(4, 4, 24, 24);
        graphics.drawString("Choose color" + (color == null ? "" : String.format(" #%06X", color.getRGB() & 0xffffff)), 36, 22);
    }
    public interface DialogHandler { Color show(Component parent,String title,Color initial); }
    private static volatile DialogHandler handler;
    public static void setDialogHandler(DialogHandler h){handler=h;}
    public static Color showDialog(Component parent,String title,Color initial){DialogHandler h=handler;return h==null?null:h.show(parent,title,initial==null?Color.WHITE:initial);}
}
