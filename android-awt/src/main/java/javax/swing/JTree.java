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
import java.awt.event.*;
import java.util.*;
import javax.swing.event.*;
import javax.swing.tree.*;

/** Tree state and renderer shared by desktop-style plugins and the Android host. */
public class JTree extends JComponent implements Scrollable {
    private static final long serialVersionUID=1L;
    protected TreeModel treeModel;
    private TreeSelectionModel selectionModel;
    private final Set<TreePath> expanded=new HashSet<>();
    private final EventListenerList listeners=new EventListenerList();
    private boolean rootVisible=true, showsRootHandles;
    private int rowHeight=16, visibleRowCount=20;
    private TreeCellRenderer renderer=(tree,value,selected,open,leaf,row,focus)->{
        JLabel label=new JLabel(convertValueToText(value,selected,open,leaf,row,focus));
        label.setFont(getFont());label.setForeground(getForeground());
        if(selected){label.setOpaque(true);label.setBackground(new Color(0x3875D6));}
        return label;
    };
    private final TreeSelectionListener selectionListener=e->{TreeSelectionEvent forwarded=(TreeSelectionEvent)e.cloneWithSource(this);for(TreeSelectionListener l:getTreeSelectionListeners())l.valueChanged(forwarded);repaint();};
    private final TreeModelListener modelListener=new TreeModelListener(){
        private void changed(){selectionModel.resetRowSelection();revalidate();repaint();}
        public void treeNodesChanged(TreeModelEvent e){changed();}
        public void treeNodesInserted(TreeModelEvent e){changed();}
        public void treeNodesRemoved(TreeModelEvent e){Object[] children=e.getChildren();if(children!=null)for(Object child:children){TreePath path=e.getTreePath().pathByAddingChild(child);for(TreePath selected:getSelectionPaths()==null?new TreePath[0]:getSelectionPaths())if(path.isDescendant(selected))selectionModel.removeSelectionPath(selected);}changed();}
        public void treeStructureChanged(TreeModelEvent e){expanded.removeIf(path->e.getTreePath()!=null&&e.getTreePath().isDescendant(path)&&!e.getTreePath().equals(path));changed();}
    };
    public JTree(){this(new DefaultMutableTreeNode("root"));}
    public JTree(TreeNode root){this(new DefaultTreeModel(root));}
    public JTree(Object[] values){this(nodes(values));}
    private static TreeNode nodes(Object[] values){DefaultMutableTreeNode root=new DefaultMutableTreeNode("root");for(Object value:values)root.add(new DefaultMutableTreeNode(value));return root;}
    public JTree(TreeModel model){
        setSelectionModel(new DefaultTreeSelectionModel());setModel(model);
        addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(!isEnabled()||e.getButton()!=MouseEvent.BUTTON1)return;TreePath path=getPathForLocation(e.getX(),e.getY());if(path==null)return;Rectangle bounds=getPathBounds(path);if(e.getX()<bounds.x+14&&!treeModel.isLeaf(path.getLastPathComponent())){if(isExpanded(path))collapsePath(path);else expandPath(path);}else if(e.isControlDown()) {if(selectionModel.isPathSelected(path))selectionModel.removeSelectionPath(path);else selectionModel.addSelectionPath(path);}else setSelectionPath(path);requestFocusInWindow();}});
    }
    public TreeModel getModel(){return treeModel;}
    public void setModel(TreeModel model){TreeModel old=treeModel;if(old!=null)old.removeTreeModelListener(modelListener);treeModel=model;expanded.clear();clearSelection();if(model!=null){model.addTreeModelListener(modelListener);if(model.getRoot()!=null)expanded.add(new TreePath(model.getRoot()));}firePropertyChange("model",old,model);revalidate();repaint();}
    private java.util.List<TreePath> rows(){java.util.List<TreePath> rows=new ArrayList<>();if(treeModel!=null&&treeModel.getRoot()!=null)collect(new TreePath(treeModel.getRoot()),rows,true);return rows;}
    private void collect(TreePath path,java.util.List<TreePath> result,boolean root){if(!root||rootVisible)result.add(path);if(expanded.contains(path))for(int i=0;i<treeModel.getChildCount(path.getLastPathComponent());i++)collect(path.pathByAddingChild(treeModel.getChild(path.getLastPathComponent(),i)),result,false);}
    public TreeSelectionModel getSelectionModel(){return selectionModel;}
    public void setSelectionModel(TreeSelectionModel model){Objects.requireNonNull(model);TreeSelectionModel old=selectionModel;if(old!=null)old.removeTreeSelectionListener(selectionListener);selectionModel=model;model.setRowMapper(paths->{int[] result=new int[paths.length];for(int i=0;i<result.length;i++)result[i]=getRowForPath(paths[i]);return result;});model.addTreeSelectionListener(selectionListener);firePropertyChange("selectionModel",old,model);}
    public TreePath getSelectionPath(){return selectionModel.getSelectionPath();}
    public TreePath[] getSelectionPaths(){return selectionModel.getSelectionPaths();}
    public void setSelectionPath(TreePath path){selectionModel.setSelectionPath(path);}
    public void setSelectionPaths(TreePath[] paths){selectionModel.setSelectionPaths(paths);}
    public void addSelectionPath(TreePath path){selectionModel.addSelectionPath(path);}
    public void removeSelectionPath(TreePath path){selectionModel.removeSelectionPath(path);}
    public void clearSelection(){selectionModel.clearSelection();}
    public boolean isSelectionEmpty(){return selectionModel.isSelectionEmpty();}
    public int getSelectionCount(){return selectionModel.getSelectionCount();}
    public int[] getSelectionRows(){return selectionModel.getSelectionRows();}
    public void setSelectionRow(int row){setSelectionPath(getPathForRow(row));}
    public void setSelectionRows(int[] rows){if(rows==null){clearSelection();return;}java.util.List<TreePath> paths=new ArrayList<>();for(int row:rows){TreePath path=getPathForRow(row);if(path!=null)paths.add(path);}setSelectionPaths(paths.toArray(new TreePath[0]));}
    public boolean isPathSelected(TreePath path){return selectionModel.isPathSelected(path);}
    public boolean isRowSelected(int row){return selectionModel.isRowSelected(row);}
    public int getRowCount(){return rows().size();}
    public int getRowHeight(){return rowHeight;}
    public void setRowHeight(int height){int old=rowHeight;rowHeight=height;firePropertyChange("rowHeight",old,height);revalidate();repaint();}
    private int effectiveRowHeight(){return rowHeight>0?rowHeight:Math.max(16,getFontMetrics(getFont()).getHeight());}
    public void expandPath(TreePath path){if(path==null)return;java.util.List<TreePath> ancestors=new ArrayList<>();for(TreePath p=path;p!=null;p=p.getParentPath())ancestors.add(0,p);for(TreePath p:ancestors)setExpanded(p,true);}
    public void collapsePath(TreePath path){setExpanded(path,false);}
    private void setExpanded(TreePath path,boolean open){if(path==null||expanded.contains(path)==open)return;TreeExpansionEvent event=new TreeExpansionEvent(this,path);try{for(TreeWillExpandListener l:listeners.getListeners(TreeWillExpandListener.class)){if(open)l.treeWillExpand(event);else l.treeWillCollapse(event);}}catch(ExpandVetoException veto){return;}if(open)expanded.add(path);else expanded.remove(path);selectionModel.resetRowSelection();for(TreeExpansionListener l:listeners.getListeners(TreeExpansionListener.class)){if(open)l.treeExpanded(event);else l.treeCollapsed(event);}revalidate();repaint();}
    public void expandRow(int row){expandPath(getPathForRow(row));}
    public void collapseRow(int row){collapsePath(getPathForRow(row));}
    public boolean isExpanded(TreePath path){for(TreePath p=path;p!=null;p=p.getParentPath())if(!expanded.contains(p))return false;return path!=null;}
    public boolean isExpanded(int row){return isExpanded(getPathForRow(row));}
    public boolean isCollapsed(TreePath path){return !isExpanded(path);}
    public boolean isCollapsed(int row){return !isExpanded(row);}
    public boolean isRootVisible(){return rootVisible;}
    public void setRootVisible(boolean value){boolean old=rootVisible;rootVisible=value;selectionModel.resetRowSelection();firePropertyChange("rootVisible",old,value);revalidate();repaint();}
    public boolean getShowsRootHandles(){return showsRootHandles;}
    public void setShowsRootHandles(boolean value){showsRootHandles=value;repaint();}
    public Rectangle getRowBounds(int row){TreePath path=getPathForRow(row);if(path==null)return null;int x=(path.getPathCount()-(rootVisible?1:2))*16;return new Rectangle(Math.max(0,x),row*effectiveRowHeight(),Math.max(16,getWidth()-x),effectiveRowHeight());}
    public Rectangle getPathBounds(TreePath path){return getRowBounds(getRowForPath(path));}
    public int getRowForPath(TreePath path){return rows().indexOf(path);}
    public TreePath getPathForRow(int row){java.util.List<TreePath> rows=rows();return row<0||row>=rows.size()?null:rows.get(row);}
    public int getClosestRowForLocation(int x,int y){return getRowCount()==0?-1:Math.max(0,Math.min(getRowCount()-1,y/effectiveRowHeight()));}
    public TreePath getClosestPathForLocation(int x,int y){return getPathForRow(getClosestRowForLocation(x,y));}
    public TreePath getPathForLocation(int x,int y){int row=y/effectiveRowHeight();Rectangle bounds=getRowBounds(row);return y<0||bounds==null||!bounds.contains(x,y)?null:getPathForRow(row);}
    public int getRowForLocation(int x,int y){return getRowForPath(getPathForLocation(x,y));}

    private boolean dragEnabled;
    private DropMode dropMode = DropMode.USE_SELECTION;
    private transient DropLocation dropLocation;
    private transient java.awt.dnd.DragGestureRecognizer dragRecognizer;

    /**
     * A subclass of <code>TransferHandler.DropLocation</code> representing
     * a drop location for a <code>JTree</code>.
     *
     * @see #getDropLocation
     * @since 1.6
     */
    public static final class DropLocation extends TransferHandler.DropLocation {
        private final TreePath path;
        private final int index;

        private DropLocation(Point p, TreePath path, int index) {
            super(p);
            this.path = path;
            this.index = index;
        }

        /**
         * Returns the index where the dropped data should be inserted
         * with respect to the path returned by <code>getPath()</code>;
         * <code>-1</code> indicates a drop onto the path itself.
         *
         * @return the child index
         */
        public int getChildIndex() {
            return index;
        }

        /**
         * Returns the path where dropped data should be placed in the tree,
         * or <code>null</code> for a drop over empty space.
         *
         * @return the drop path
         */
        public TreePath getPath() {
            return path;
        }

        public String toString() {
            return getClass().getName()
                   + "[dropPoint=" + getDropPoint() + ","
                   + "path=" + path + ","
                   + "childIndex=" + index + "]";
        }
    }

    /**
     * Turns on or off automatic drag handling; a press on a row that moves past
     * the drag threshold (after a long press on a touchscreen) starts a drag
     * through the {@code TransferHandler}.
     *
     * @since 1.4
     */
    public void setDragEnabled(boolean b) {
        dragEnabled = b;
        dragRecognizer = net.runelite.awt.impl.SwingDragSupport.setDragEnabled(this, dragRecognizer, b);
    }

    public boolean getDragEnabled() { return dragEnabled; }

    /**
     * Sets the drop mode for this component: <code>USE_SELECTION</code> (the
     * default), <code>ON</code>, <code>INSERT</code> or <code>ON_OR_INSERT</code>.
     *
     * @throws IllegalArgumentException if the drop mode is unsupported
     *         or <code>null</code>
     * @since 1.6
     */
    public final void setDropMode(DropMode dropMode) {
        if (dropMode == DropMode.USE_SELECTION || dropMode == DropMode.ON
                || dropMode == DropMode.INSERT || dropMode == DropMode.ON_OR_INSERT) {
            this.dropMode = dropMode;
            return;
        }
        throw new IllegalArgumentException(dropMode + ": Unsupported drop mode for tree");
    }

    public final DropMode getDropMode() { return dropMode; }

    /**
     * Returns the location that this component should visually indicate
     * as the drop location during a DnD operation over the component,
     * or {@code null} if no location is to currently be shown.
     *
     * @since 1.6
     */
    public final DropLocation getDropLocation() { return dropLocation; }

    @Override
    DropLocation dropLocationForPoint(Point p) {
        if (dropMode == DropMode.USE_SELECTION || dropMode == DropMode.ON) {
            return new DropLocation(p, getPathForLocation(p.x, p.y), -1);
        }
        int row = getClosestRowForLocation(p.x, p.y);
        Rectangle bounds = row < 0 ? null : getRowBounds(row);
        TreePath path = row < 0 ? null : getPathForRow(row);
        if (bounds == null || path == null) {
            Object root = getModel() == null ? null : getModel().getRoot();
            return new DropLocation(p, root == null ? null : new TreePath(root),
                    root == null ? -1 : getModel().getChildCount(root));
        }
        int offset = p.y - bounds.y;
        if (dropMode == DropMode.ON_OR_INSERT && offset >= bounds.height / 4 && offset < bounds.height - bounds.height / 4) {
            return new DropLocation(p, path, -1);
        }
        TreePath parent = path.getParentPath();
        if (parent == null) {
            // The root has no siblings; an insert next to it goes inside it.
            return new DropLocation(p, path, offset < bounds.height / 2 ? 0 : getModel().getChildCount(path.getLastPathComponent()));
        }
        int index = getModel().getIndexOfChild(parent.getLastPathComponent(), path.getLastPathComponent());
        return new DropLocation(p, parent, offset < bounds.height / 2 ? index : index + 1);
    }

    @Override
    Object setDropLocation(TransferHandler.DropLocation location, Object state, boolean forDrop) {
        DropLocation old = dropLocation;
        dropLocation = (DropLocation) location;
        firePropertyChange("dropLocation", old, dropLocation);
        repaint();
        return null;
    }
    public void scrollPathToVisible(TreePath path){if(path==null)return;expandPath(path.getParentPath());Rectangle bounds=getPathBounds(path);if(bounds!=null)scrollRectToVisible(bounds);}
    public void scrollRowToVisible(int row){scrollPathToVisible(getPathForRow(row));}
    public void addTreeSelectionListener(TreeSelectionListener l){listeners.add(TreeSelectionListener.class,l);}
    public void removeTreeSelectionListener(TreeSelectionListener l){listeners.remove(TreeSelectionListener.class,l);}
    public TreeSelectionListener[] getTreeSelectionListeners(){return listeners.getListeners(TreeSelectionListener.class);}
    public void addTreeExpansionListener(TreeExpansionListener l){listeners.add(TreeExpansionListener.class,l);}
    public void removeTreeExpansionListener(TreeExpansionListener l){listeners.remove(TreeExpansionListener.class,l);}
    public void addTreeWillExpandListener(TreeWillExpandListener l){listeners.add(TreeWillExpandListener.class,l);}
    public void removeTreeWillExpandListener(TreeWillExpandListener l){listeners.remove(TreeWillExpandListener.class,l);}
    public TreeCellRenderer getCellRenderer(){return renderer;}
    public void setCellRenderer(TreeCellRenderer value){renderer=value;revalidate();repaint();}
    public String convertValueToText(Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus){return value==null?"":value.toString();}
    public int getVisibleRowCount(){return visibleRowCount;}
    public void setVisibleRowCount(int count){visibleRowCount=count;revalidate();}
    public Dimension getPreferredSize(){if(isPreferredSizeSet())return super.getPreferredSize();int width=0;for(TreePath path:rows())width=Math.max(width,(path.getPathCount()+1)*16+getFontMetrics(getFont()).stringWidth(String.valueOf(path.getLastPathComponent())));return new Dimension(width,getRowCount()*effectiveRowHeight());}
    protected void paintComponent(Graphics g){
        if(paintDelegate(g))return;super.paintComponent(g);if(renderer==null)return;for(int row=0;row<getRowCount();row++){TreePath path=getPathForRow(row);Rectangle bounds=getRowBounds(row);if(g.getClipBounds()!=null&&!g.getClipBounds().intersects(bounds))continue;boolean leaf=treeModel.isLeaf(path.getLastPathComponent());Component cell=renderer.getTreeCellRendererComponent(this,path.getLastPathComponent(),isPathSelected(path),isExpanded(path),leaf,row,false);if(cell!=null){cell.setBounds(0,0,Math.max(0,bounds.width-14),bounds.height);Graphics cg=g.create(bounds.x+14,bounds.y,Math.max(0,bounds.width-14),bounds.height);try{cell.paint(cg);}finally{cg.dispose();}}if(!leaf){g.setColor(getForeground());g.drawString(isExpanded(path)?"-":"+",bounds.x+2,bounds.y+effectiveRowHeight()-3);}}}
    public Dimension getPreferredScrollableViewportSize(){Dimension size=getPreferredSize();return new Dimension(size.width,visibleRowCount*effectiveRowHeight());}
    public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return effectiveRowHeight();}
    public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return orientation==SwingConstants.VERTICAL?visible.height:visible.width;}
    public boolean getScrollableTracksViewportWidth(){return false;}
    public boolean getScrollableTracksViewportHeight(){return false;}
}
