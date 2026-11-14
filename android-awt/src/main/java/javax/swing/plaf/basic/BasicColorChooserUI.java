/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.swing.plaf.basic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.LookAndFeel;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorChooserComponentFactory;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorChooserUI;
import javax.swing.plaf.ComponentUI;

/**
 * Provides the basic look and feel for a JColorChooser.
 *
 * @author Tom Santos
 * @author Steve Wilson
 */
public class BasicColorChooserUI extends ColorChooserUI
{
    /**
     * JColorChooser this BasicColorChooserUI is installed on.
     *
     * @since 1.5
     */
    protected JColorChooser chooser;

    JTabbedPane tabbedPane;
    JPanel singlePanel;

    JPanel previewPanelHolder;
    JComponent previewPanel;
    boolean isMultiPanel = false;

    protected AbstractColorChooserPanel[] defaultChoosers;

    protected ChangeListener previewListener;
    protected PropertyChangeListener propertyChangeListener;
    private Handler handler;

    public static ComponentUI createUI(JComponent c) {
        return new BasicColorChooserUI();
    }

    protected AbstractColorChooserPanel[] createDefaultChoosers() {
        AbstractColorChooserPanel[] panels = ColorChooserComponentFactory.getDefaultChooserPanels();
        return panels;
    }

    protected void uninstallDefaultChoosers() {
        AbstractColorChooserPanel[] choosers = chooser.getChooserPanels();
        for( int i = 0 ; i < choosers.length; i++) {
            chooser.removeChooserPanel( choosers[i] );
        }
    }

    public void installUI( JComponent c ) {
        chooser = (JColorChooser)c;

        super.installUI( c );

        installDefaults();
        installListeners();

        tabbedPane = new JTabbedPane();
        tabbedPane.setName("ColorChooser.tabPane");
        tabbedPane.setInheritsPopupMenu(true);
        singlePanel = new JPanel(new BorderLayout());
        singlePanel.setName("ColorChooser.panel");
        singlePanel.setInheritsPopupMenu(true);

        chooser.setLayout( new BorderLayout() );

        defaultChoosers = createDefaultChoosers();
        chooser.setChooserPanels(defaultChoosers);

        previewPanelHolder = new JPanel(new BorderLayout());
        previewPanelHolder.setName("ColorChooser.previewPanelHolder");
        previewPanelHolder.setInheritsPopupMenu(true);

        installPreviewPanel();
        chooser.applyComponentOrientation(c.getComponentOrientation());
    }

    public void uninstallUI( JComponent c ) {
        chooser.remove(tabbedPane);
        chooser.remove(singlePanel);
        chooser.remove(previewPanelHolder);

        uninstallDefaultChoosers();
        uninstallListeners();
        uninstallPreviewPanel();
        uninstallDefaults();

        previewPanelHolder = null;
        previewPanel = null;
        defaultChoosers = null;
        chooser = null;
        tabbedPane = null;

        handler = null;
    }

    protected void installPreviewPanel() {
        JComponent previewPanel = this.chooser.getPreviewPanel();
        if (previewPanel == null) {
            previewPanel = ColorChooserComponentFactory.getPreviewPanel();
        }
        else if (JPanel.class.equals(previewPanel.getClass()) && (0 == previewPanel.getComponentCount())) {
            // An empty plain JPanel is the documented way to remove the preview.
            previewPanel = null;
        }
        this.previewPanel = previewPanel;
        if (previewPanel != null) {
            chooser.add(previewPanelHolder, BorderLayout.SOUTH);
            previewPanel.setForeground(chooser.getColor());
            previewPanelHolder.add(previewPanel);
            previewPanel.setInheritsPopupMenu(true);
        }
    }

    /**
     * Removes installed preview panel from the UI delegate.
     *
     * @since 1.7
     */
    protected void uninstallPreviewPanel() {
        if (this.previewPanel != null) {
            this.previewPanelHolder.remove(this.previewPanel);
        }
        if (this.previewPanelHolder != null) {
            this.chooser.remove(this.previewPanelHolder);
        }
    }

    protected void installDefaults() {
        LookAndFeel.installColorsAndFont(chooser, "ColorChooser.background",
                                              "ColorChooser.foreground",
                                              "ColorChooser.font");
        LookAndFeel.installProperty(chooser, "opaque", Boolean.TRUE);
    }

    protected void uninstallDefaults() {
    }


    protected void installListeners() {
        propertyChangeListener = createPropertyChangeListener();
        chooser.addPropertyChangeListener( propertyChangeListener );

        previewListener = getHandler();
        chooser.getSelectionModel().addChangeListener(previewListener);
    }

    private Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    protected PropertyChangeListener createPropertyChangeListener() {
        return getHandler();
    }

    protected void uninstallListeners() {
        chooser.removePropertyChangeListener( propertyChangeListener );
        chooser.getSelectionModel().removeChangeListener(previewListener);
        previewListener = null;
    }

    private void selectionChanged(ColorSelectionModel model) {
        Color color = model.getSelectedColor();
        if (previewPanel != null) {
            previewPanel.setForeground(color);
            previewPanel.repaint();
        }
        // Every tab follows the model, whichever tab (or caller) changed it.
        AbstractColorChooserPanel[] panels = chooser.getChooserPanels();
        for (AbstractColorChooserPanel panel : panels) {
            if (panel != null) {
                panel.updateChooser();
            }
        }
    }

    private class Handler implements ChangeListener, PropertyChangeListener {
        public void stateChanged(ChangeEvent evt) {
            if (chooser != null) {
                selectionChanged((ColorSelectionModel) evt.getSource());
            }
        }

        public void propertyChange(PropertyChangeEvent evt) {
            String prop = evt.getPropertyName();

            if (prop == JColorChooser.CHOOSER_PANELS_PROPERTY) {
                AbstractColorChooserPanel[] oldPanels =
                    (AbstractColorChooserPanel[])evt.getOldValue();
                AbstractColorChooserPanel[] newPanels =
                    (AbstractColorChooserPanel[])evt.getNewValue();

                for (int i = 0; i < oldPanels.length; i++) {  // remove old panels
                   java.awt.Container wrapper = oldPanels[i].getParent();
                    if (wrapper != null) {
                      java.awt.Container parent = wrapper.getParent();
                      if (parent != null)
                          parent.remove(wrapper);  // remove from hierarchy
                      oldPanels[i].uninstallChooserPanel(chooser); // uninstall
                    }
                }

                int numNewPanels = newPanels.length;
                if (numNewPanels == 0) {  // removed all panels and added none
                    chooser.remove(tabbedPane);
                    chooser.remove(singlePanel);
                    isMultiPanel = false;
                    return;
                }
                else if (numNewPanels == 1) {  // one panel case
                    chooser.remove(tabbedPane);
                    JPanel centerWrapper = new JPanel(new BorderLayout());
                    centerWrapper.setInheritsPopupMenu(true);
                    centerWrapper.add(newPanels[0]);
                    singlePanel.removeAll();
                    singlePanel.add(centerWrapper, BorderLayout.CENTER);
                    chooser.add(singlePanel, BorderLayout.CENTER);
                    isMultiPanel = false;
                }
                else {   // multi-panel case
                    if ( oldPanels.length < 2 ) {// moving from single to multiple
                        chooser.remove(singlePanel);
                        chooser.add(tabbedPane, BorderLayout.CENTER);
                    }
                    tabbedPane.removeAll();

                    for (int i = 0; i < newPanels.length; i++) {
                        JPanel centerWrapper = new JPanel(new BorderLayout());
                        centerWrapper.setInheritsPopupMenu(true);
                        String name = newPanels[i].getDisplayName();
                        centerWrapper.add(newPanels[i]);
                        tabbedPane.addTab(name, centerWrapper);
                    }
                    isMultiPanel = true;
                }
                chooser.applyComponentOrientation(chooser.getComponentOrientation());
                for (int i = 0; i < newPanels.length; i++) {
                    newPanels[i].installChooserPanel(chooser);
                }
                chooser.revalidate();
                chooser.repaint();
            }
            else if (prop == JColorChooser.PREVIEW_PANEL_PROPERTY) {
                uninstallPreviewPanel();
                installPreviewPanel();
                chooser.revalidate();
                chooser.repaint();
            }
            else if (prop == JColorChooser.SELECTION_MODEL_PROPERTY) {
                ColorSelectionModel oldModel = (ColorSelectionModel) evt.getOldValue();
                oldModel.removeChangeListener(previewListener);
                ColorSelectionModel newModel = (ColorSelectionModel) evt.getNewValue();
                newModel.addChangeListener(previewListener);
                selectionChanged(newModel);
            }
            else if (prop == "componentOrientation") {
                chooser.applyComponentOrientation(chooser.getComponentOrientation());
            }
        }
    }

    /**
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of {@code BasicColorChooserUI}.
     */
    public class PropertyHandler implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            getHandler().propertyChange(e);
        }
    }
}
