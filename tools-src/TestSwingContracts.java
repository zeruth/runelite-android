// host-contract: patched-args=shadow jvm-args=--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import javax.swing.undo.*;

/** Same test runs against desktop JDK and isolated Android shim sources. */
public final class TestSwingContracts {
    private static int failures;
    private static int total;
    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }
    /**
     * java.awt.Font is not patchable (its JNI layout differs), so the patched run uses the JDK's
     * Font, whose static initializer calls Toolkit.loadLibraries() and then the native initIDs.
     * The patched Toolkit's loadLibraries() is empty because the Android backend loads its own
     * library, so the JDK's awt library is loaded here into the boot loader, as the JDK's
     * Toolkit does, before anything reaches Font.
     */
    private static void loadJdkAwtLibrary() throws ReflectiveOperationException {
        Class.forName("jdk.internal.loader.BootLoader").getMethod("loadLibrary", String.class).invoke(null, "awt");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "shadow".equals(args[0])) {
            loadJdkAwtLibrary();
        }
        DefaultButtonModel button = new DefaultButtonModel();
        AtomicInteger changes = new AtomicInteger(), items = new AtomicInteger(), actions = new AtomicInteger();
        button.addChangeListener(e -> changes.incrementAndGet());
        button.addItemListener(e -> items.incrementAndGet());
        button.addActionListener(e -> actions.incrementAndGet());
        button.setSelected(true);
        check("button-model selected mutation emits change", changes.get() > 0);
        check("button-model selected mutation emits item", items.get() == 1);
        button.setArmed(true); button.setPressed(true); button.setPressed(false);
        check("button-model armed press/release emits action", actions.get() == 1);

        ButtonGroup group = new ButtonGroup();
        DefaultButtonModel one = new DefaultButtonModel(), two = new DefaultButtonModel();
        group.setSelected(one, true);
        check("button-group selecting model updates model state", one.isSelected());
        group.setSelected(two, true);
        check("button-group switching selection selects new model and deselects old", !one.isSelected() && two.isSelected());

        PlainDocument document = new PlainDocument();
        AtomicInteger inserts = new AtomicInteger(), undoEvents = new AtomicInteger();
        document.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { inserts.incrementAndGet(); }
            public void removeUpdate(DocumentEvent e) {}
            public void changedUpdate(DocumentEvent e) {}
        });
        document.addUndoableEditListener(e -> undoEvents.incrementAndGet());
        document.insertString(0, "abc", null);
        Position position = document.createPosition(2);
        document.insertString(0, "xx", null);
        check("document insert delivers ordinary document events", inserts.get() == 2);
        check("document insert delivers undoable edits", undoEvents.get() == 2);
        check("document position follows inserted text", position.getOffset() == 4);
        check("document supplies default line root", document.getDefaultRootElement() != null);
        check("document end position includes implicit trailing newline", document.getEndPosition().getOffset() == document.getLength() + 1);
        UndoManager undo = new UndoManager();
        undo.addEdit(new AbstractUndoableEdit());
        check("undo manager accepts an undoable edit", undo.canUndo());
        undo.setLimit(7);
        check("undo manager retains supplied limit", undo.getLimit() == 7);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode child = new DefaultMutableTreeNode("child");
        DefaultTreeModel tree = new DefaultTreeModel(root);
        AtomicInteger inserted = new AtomicInteger(), renamed = new AtomicInteger(), reloaded = new AtomicInteger();
        tree.addTreeModelListener(new TreeModelListener() {
            public void treeNodesChanged(TreeModelEvent e) { renamed.incrementAndGet(); }
            public void treeNodesInserted(TreeModelEvent e) { inserted.incrementAndGet(); }
            public void treeNodesRemoved(TreeModelEvent e) {}
            public void treeStructureChanged(TreeModelEvent e) { reloaded.incrementAndGet(); }
        });
        tree.insertNodeInto(child, root, 0);
        check("tree mutation emits insert event", inserted.get() == 1);
        tree.valueForPathChanged(new TreePath(new Object[]{root, child}), "renamed");
        check("tree path edit updates node", "renamed".equals(child.getUserObject()));
        check("tree path edit emits changed event", renamed.get() == 1);
        tree.reload();
        check("tree reload emits structure event", reloaded.get() == 1);
        DefaultMutableTreeNode secondParent = new DefaultMutableTreeNode("second");
        secondParent.add(child);
        check("tree reparent removes child from prior parent", root.getChildCount() == 0 && child.getParent() == secondParent);

        DefaultComboBoxModel<String> combo = new DefaultComboBoxModel<>(new String[]{"one", "two"});
        check("combo first item selected", "one".equals(combo.getSelectedItem()));
        combo.removeElementAt(0);
        check("combo removal selects remaining neighbor", "two".equals(combo.getSelectedItem()));

        DefaultListModel<String> list = new DefaultListModel<>();
        AtomicInteger listAdded = new AtomicInteger(), listChanged = new AtomicInteger(), listRemoved = new AtomicInteger();
        list.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) { listAdded.incrementAndGet(); }
            public void intervalRemoved(ListDataEvent e) { listRemoved.incrementAndGet(); }
            public void contentsChanged(ListDataEvent e) { listChanged.incrementAndGet(); }
        });
        list.addElement("one"); list.set(0, "two"); list.remove(0);
        check("list mutation emits add event", listAdded.get() == 1);
        check("list mutation emits change event", listChanged.get() == 1);
        check("list mutation emits remove event", listRemoved.get() == 1);
        SpinnerNumberModel spinner = new SpinnerNumberModel(1, 0, 1, 1);
        check("number spinner stops at upper bound", spinner.getNextValue() == null);
        check("number spinner preserves Integer numeric type", spinner.getPreviousValue() instanceof Integer);
        spinner.setValue(0);
        check("number spinner stops at lower bound", spinner.getPreviousValue() == null);
        java.util.concurrent.CountDownLatch fired = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean eventThread = new java.util.concurrent.atomic.AtomicBoolean();
        Timer timer = new Timer(1, e -> { eventThread.set(SwingUtilities.isEventDispatchThread()); fired.countDown(); });
        timer.setRepeats(false); timer.start();
        boolean happened = fired.await(3, java.util.concurrent.TimeUnit.SECONDS);
        timer.stop();
        check("Swing timer invokes listener on EDT", happened && eventThread.get());

        DefaultListSelectionModel selection = new DefaultListSelectionModel();
        java.util.List<ListSelectionEvent> selectionEvents = new java.util.ArrayList<>();
        selection.addListSelectionListener(selectionEvents::add);
        selection.setSelectionInterval(2, 4);
        check("selection range includes interior", selection.isSelectedIndex(3));
        check("selection range excludes outside", !selection.isSelectedIndex(1));
        selection.addSelectionInterval(7, 7);
        check("selection default permits disjoint interval", selection.isSelectedIndex(2) && selection.isSelectedIndex(7));
        selection.removeSelectionInterval(3, 3);
        check("selection removal splits interval", selection.isSelectedIndex(2) && !selection.isSelectedIndex(3) && selection.isSelectedIndex(4));
        selection.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selection.setSelectionInterval(1, 5);
        check("single selection retains only endpoint", selection.getMinSelectionIndex() == 5 && selection.getMaxSelectionIndex() == 5);
        selection.setValueIsAdjusting(true); selection.setSelectionInterval(6, 6); selection.setValueIsAdjusting(false);
        check("selection final event clears adjusting", !selectionEvents.get(selectionEvents.size()-1).getValueIsAdjusting());
        selection.insertIndexInterval(2, 2, true);
        check("selection tracks insertion before selected row", selection.getMinSelectionIndex() == 8);
        selection.removeIndexInterval(2, 3);
        check("selection tracks removal before selected row", selection.getMinSelectionIndex() == 6);
        selection.clearSelection();
        check("selection clears indices", selection.isSelectionEmpty() && selection.getMinSelectionIndex() == -1);

        DefaultTreeSelectionModel treeSelection = new DefaultTreeSelectionModel();
        TreePath p1=new TreePath(new Object[]{root,"one"}),p2=new TreePath(new Object[]{root,"two"});
        AtomicInteger treeSelectionEvents=new AtomicInteger();treeSelection.addTreeSelectionListener(e->treeSelectionEvents.incrementAndGet());
        treeSelection.setSelectionPaths(new TreePath[]{p1,p2});
        check("tree selection accepts paths",treeSelection.getSelectionCount()==2);
        check("tree selection publishes events",treeSelectionEvents.get()>0);
        treeSelection.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        treeSelection.setSelectionPaths(new TreePath[]{p1,p2});
        check("tree single selection restricts paths",treeSelection.getSelectionCount()==1);
        check("tree path ancestry",new TreePath(root).isDescendant(p1));
        TreeModelEvent modelEvent=new TreeModelEvent(tree,new Object[]{root},new int[]{0},new Object[]{child});
        Object[] copiedChildren=modelEvent.getChildren();copiedChildren[0]=null;
        check("tree event defensively exposes children",modelEvent.getChildren()[0]==child);

        PlainDocument edits=new PlainDocument();UndoManager history=new UndoManager();edits.addUndoableEditListener(history);
        edits.insertString(0,"abc",null);history.undo();
        check("document undo removes insertion",edits.getLength()==0);
        history.redo();check("document redo restores insertion","abc".equals(edits.getText(0,edits.getLength())));
        Position collapsed=edits.createPosition(2);edits.remove(1,2);
        check("document position collapses into removed range",collapsed.getOffset()==1);
        history.undo();check("document undo restores deletion","abc".equals(edits.getText(0,edits.getLength())));
        edits.insertString(edits.getLength(),"\nnext\n",null);
        check("document root counts newline paragraphs",edits.getDefaultRootElement().getElementCount()==3);
        check("document root selects trailing paragraph",edits.getDefaultRootElement().getElementIndex(edits.getLength())==2);
        edits.setDocumentFilter(new DocumentFilter(){public void insertString(FilterBypass bypass,int offset,String text,AttributeSet attrs)throws BadLocationException{bypass.insertString(offset,text.toUpperCase(java.util.Locale.ROOT),attrs);}});
        edits.insertString(0,"hi",null);check("document filter controls inserted text",edits.getText(0,2).equals("HI"));
        history.setLimit(0);check("zero undo limit drops all history",!history.canUndo());
        AtomicInteger rendered=new AtomicInteger();edits.render(rendered::incrementAndGet);check("document render callback runs",rendered.get()==1);
        java.util.concurrent.CountDownLatch locked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> lockFailure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader=new Thread(()->{edits.readLock();try{locked.countDown();release.await();edits.getText(0,1);}catch(Throwable t){lockFailure.set(t);}finally{edits.readUnlock();}});
        Thread writer=new Thread(()->{try{edits.insertString(0,"x",null);}catch(Throwable t){lockFailure.set(t);}});
        reader.setDaemon(true);writer.setDaemon(true);reader.start();locked.await();writer.start();release.countDown();reader.join(2000);writer.join(2000);
        check("document read and write locks avoid monitor deadlock",!reader.isAlive()&&!writer.isAlive()&&lockFailure.get()==null);

        StyleContext styles=new StyleContext();Style parent=styles.addStyle("parent",null);StyleConstants.setBold(parent,true);Style childStyle=styles.addStyle("child",parent);
        check("style lookup returns registered style",styles.getStyle("child")==childStyle);
        check("styles inherit parent attributes",StyleConstants.isBold(childStyle));
        SimpleAttributeSet original=new SimpleAttributeSet();AttributeSet added=styles.addAttribute(original,StyleConstants.Foreground,java.awt.Color.RED);
        check("style context returns new attributes",!original.isDefined(StyleConstants.Foreground)&&java.awt.Color.RED.equals(added.getAttribute(StyleConstants.Foreground)));
        DefaultStyledDocument styled=new DefaultStyledDocument();styled.insertString(0,"abc",added);
        check("styled insertion retains attributes",java.awt.Color.RED.equals(styled.getCharacterElement(1).getAttributes().getAttribute(StyleConstants.Foreground)));
        styled.setDocumentFilter(new DocumentFilter(){public void insertString(FilterBypass bypass,int offset,String text,AttributeSet attrs){}});
        styled.insertString(0,"blocked",null);
        check("rejected styled insertion preserves text and runs",styled.getLength()==3&&java.awt.Color.RED.equals(styled.getCharacterElement(0).getAttributes().getAttribute(StyleConstants.Foreground)));
        AtomicInteger styleChanges=new AtomicInteger();styled.addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){}public void removeUpdate(DocumentEvent e){}public void changedUpdate(DocumentEvent e){styleChanges.incrementAndGet();}});
        SimpleAttributeSet bold=new SimpleAttributeSet();StyleConstants.setBold(bold,true);styled.setCharacterAttributes(0,2,bold,false);
        check("styled updates publish CHANGE event",styleChanges.get()==1);
        check("styled merge retains prior attribute",StyleConstants.isBold(styled.getCharacterElement(0).getAttributes())&&java.awt.Color.RED.equals(styled.getCharacterElement(0).getAttributes().getAttribute(StyleConstants.Foreground)));

        UIDefaults defaults=new UIDefaults();AtomicInteger lazy=new AtomicInteger(),active=new AtomicInteger(),property=new AtomicInteger();defaults.addPropertyChangeListener(e->property.incrementAndGet());
        defaults.put("lazy",(UIDefaults.LazyValue)t->lazy.incrementAndGet());defaults.get("lazy");defaults.get("lazy");
        check("UIDefaults lazy value computes once",lazy.get()==1);
        defaults.put("active",(UIDefaults.ActiveValue)t->active.incrementAndGet());defaults.get("active");defaults.get("active");
        check("UIDefaults active value computes per read",active.get()==2);
        defaults.put("removed",1);defaults.put("removed",null);check("UIDefaults null removes value",defaults.get("removed")==null);
        check("UIDefaults mutations publish properties",property.get()>=4);

        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);AtomicInteger published=new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean background=new java.util.concurrent.atomic.AtomicBoolean(),processedOnEdt=new java.util.concurrent.atomic.AtomicBoolean(),doneOnEdt=new java.util.concurrent.atomic.AtomicBoolean();
        SwingWorker<Integer,Integer> worker=new SwingWorker<Integer,Integer>(){
            protected Integer doInBackground(){background.set(!SwingUtilities.isEventDispatchThread());publish(1,2,3);setProgress(100);return 42;}
            protected void process(java.util.List<Integer> values){processedOnEdt.set(SwingUtilities.isEventDispatchThread());for(int value:values)published.addAndGet(value);}
            protected void done(){doneOnEdt.set(SwingUtilities.isEventDispatchThread());done.countDown();}
        };worker.execute();check("SwingWorker returns background result",worker.get(3,java.util.concurrent.TimeUnit.SECONDS)==42);
        check("SwingWorker background runs off EDT",background.get());
        boolean completed=done.await(3,java.util.concurrent.TimeUnit.SECONDS);SwingUtilities.invokeAndWait(()->{});
        check("SwingWorker done callback on EDT",completed&&doneOnEdt.get());
        check("SwingWorker publication retained and on EDT",published.get()==6&&processedOnEdt.get());
        check("SwingWorker state reaches DONE",worker.getState()==SwingWorker.StateValue.DONE);
        check("SwingWorker progress retained",worker.getProgress()==100);

        styled.setLogicalStyle(0,childStyle);
        check("styled logical style preserves parent",styled.getLogicalStyle(0)==childStyle);
        DefaultStyledDocument styledUndo=new DefaultStyledDocument();
        SimpleAttributeSet red=new SimpleAttributeSet(),blue=new SimpleAttributeSet();
        StyleConstants.setForeground(red,java.awt.Color.RED);StyleConstants.setForeground(blue,java.awt.Color.BLUE);
        styledUndo.insertString(0,"red",red);styledUndo.insertString(3,"blue",blue);
        UndoManager styledHistory=new UndoManager();styledUndo.addUndoableEditListener(styledHistory);
        styledUndo.remove(1,5);styledHistory.undo();
        check("styled deletion undo restores mixed text",styledUndo.getText(0,styledUndo.getLength()).equals("redblue"));
        check("styled deletion undo restores distinct runs",StyleConstants.getForeground(styledUndo.getCharacterElement(1).getAttributes()).equals(java.awt.Color.RED)&&StyleConstants.getForeground(styledUndo.getCharacterElement(4).getAttributes()).equals(java.awt.Color.BLUE));
        styledHistory.redo();styledHistory.undo();
        check("styled deletion redo remains reversible",StyleConstants.getForeground(styledUndo.getCharacterElement(4).getAttributes()).equals(java.awt.Color.BLUE));
        styledUndo.setCharacterAttributes(0,2,bold,false);styledHistory.undo();
        check("character formatting is undoable",!StyleConstants.isBold(styledUndo.getCharacterElement(0).getAttributes())&&StyleConstants.getForeground(styledUndo.getCharacterElement(0).getAttributes()).equals(java.awt.Color.RED));
        styledHistory.redo();check("character formatting redo retains merge",StyleConstants.isBold(styledUndo.getCharacterElement(0).getAttributes())&&StyleConstants.getForeground(styledUndo.getCharacterElement(0).getAttributes()).equals(java.awt.Color.RED));
        SimpleAttributeSet centered=new SimpleAttributeSet();StyleConstants.setAlignment(centered,StyleConstants.ALIGN_CENTER);
        styledUndo.setParagraphAttributes(0,0,centered,false);styledHistory.undo();
        check("paragraph formatting is undoable",StyleConstants.getAlignment(styledUndo.getParagraphElement(0).getAttributes())==StyleConstants.ALIGN_LEFT);
        styledHistory.redo();check("paragraph formatting redo",StyleConstants.getAlignment(styledUndo.getParagraphElement(0).getAttributes())==StyleConstants.ALIGN_CENTER);
        DefaultSingleSelectionModel single=new DefaultSingleSelectionModel();AtomicInteger singleEvents=new AtomicInteger();single.addChangeListener(e->singleEvents.incrementAndGet());
        single.setSelectedIndex(3);single.setSelectedIndex(3);check("single selection retains index without duplicate event",single.getSelectedIndex()==3&&singleEvents.get()==1);
        single.clearSelection();check("single selection clears and notifies",!single.isSelected()&&singleEvents.get()==2);
        check("ColorUIResource keeps base color contract",new javax.swing.plaf.ColorUIResource(10,20,30).getBlue()==30);
        check("DimensionUIResource keeps base dimensions",new javax.swing.plaf.DimensionUIResource(23,42).height==42);
        check("InsetsUIResource keeps base insets",new javax.swing.plaf.InsetsUIResource(1,2,3,4).right==4);
        check("FontUIResource keeps base font",new javax.swing.plaf.FontUIResource("Dialog",java.awt.Font.BOLD,17).getSize()==17);

        String crossPlatform = UIManager.getCrossPlatformLookAndFeelClassName();
        check("cross-platform look and feel has a loadable class name", !crossPlatform.isEmpty() &&
            LookAndFeel.class.isAssignableFrom(Class.forName(crossPlatform)));
        UIManager.setLookAndFeel(crossPlatform);
        check("cross-platform look and feel installs for splash startup", UIManager.getLookAndFeel() != null &&
            UIManager.getLookAndFeel().isSupportedLookAndFeel());
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        check("system look and feel installs", UIManager.getLookAndFeel() != null);

        System.out.println("SUMMARY " + total + " checks, " + failures + " failures");
        if(failures!=0)throw new AssertionError(failures+" Swing contract checks failed");
        // The patched run leaves a non-daemon AWT thread (SwingWorker/EDT of the JDK/shadow
        // mix) that never shuts down; every check above has completed, so exit explicitly.
        System.exit(0);
    }
}
