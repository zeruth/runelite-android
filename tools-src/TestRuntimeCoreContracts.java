import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.*;
import javax.sound.sampled.*;

public final class TestRuntimeCoreContracts {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public interface CounterMBean { int getValue(); void setValue(int value); int add(int amount); }
    public static final class Counter implements CounterMBean {
        private int value;
        public int getValue(){return value;} public void setValue(int value){this.value=value;} public int add(int amount){return value+=amount;}
    }
    public static void main(String[]args)throws Exception{
        java.lang.management.MemoryUsage heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        check(heap.getUsed()>0&&heap.getCommitted()>=heap.getUsed(),"actual ART heap usage");
        java.lang.management.GarbageCollectorMXBean gc=java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().get(0);
        check(gc.getCollectionCount()>=-1&&gc.getCollectionTime()>=-1,"ART GC counters or explicit unavailable");
        check(java.lang.management.ManagementFactory.getMemoryPoolMXBeans().size()==2,"ART heap and native heap pools");
        check(gc.getCollectionCount()>=0,"ART reports art.gc.gc-count");
        com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        check(os.getTotalMemorySize()>0&&os.getFreeMemorySize()<=os.getTotalMemorySize()&&os.getProcessCpuTime()>0,"OS memory and process CPU time on Android");
        double load=os.getProcessCpuLoad();check(load==-1||(load>=0&&load<=1),"process CPU load in range");
        long self=Thread.currentThread().getId();boolean dumped=false;
        for(java.lang.management.ThreadInfo info:java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(false,false))dumped|=info.getThreadId()==self&&info.getStackTrace().length>0;
        check(dumped,"dumpAllThreads includes the current thread with a stack");
        ObjectName first=new ObjectName("test:z=2,a=1"),second=new ObjectName("test:a=1,z=2");
        check(first.equals(second)&&first.getKeyProperty("z").equals("2"),"canonical object names");
        check(new ObjectName("test:a=*,*").apply(first),"object name wildcard");
        check(ObjectName.unquote(ObjectName.quote("a\nb*\"\\")).equals("a\nb*\"\\"),"quoted object names");
        MBeanServer server=MBeanServerFactory.newMBeanServer();server.registerMBean(new Counter(),first);
        server.setAttribute(second,new Attribute("Value",7));check(server.getAttribute(first,"Value").equals(7),"bean property");
        check(server.invoke(first,"add",new Object[]{3},new String[]{"int"}).equals(10),"bean invoke");
        check(server.queryNames(new ObjectName("test:*"),null).contains(first),"bean query");server.unregisterMBean(first);
        Frame frame=new Frame();Component field=new Component(){};frame.add(field);
        AtomicInteger opened=new AtomicInteger(),closed=new AtomicInteger(),focused=new AtomicInteger();
        frame.addWindowListener(new WindowAdapter(){public void windowOpened(WindowEvent e){opened.incrementAndGet();}public void windowClosed(WindowEvent e){closed.incrementAndGet();}});
        field.addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){focused.incrementAndGet();}});
        EventQueue.invokeAndWait(()->{frame.setVisible(true);check(field.requestFocusInWindow(),"focus accepted");check(field.isFocusOwner(),"focus owner");field.setVisible(false);check(!field.isFocusOwner(),"hide releases focus");frame.dispose();});
        check(opened.get()==1&&closed.get()==1&&focused.get()==1,"window/focus events");
        Component hosted=new Component(){};hosted.setHostShowing(true);check(hosted.isShowing(),"detached host visibility");hosted.setHostShowing(false);check(!hosted.isShowing(),"detached host hidden");
        Clipboard clipboard=new Clipboard("test");AtomicInteger lost=new AtomicInteger(),flavors=new AtomicInteger();
        clipboard.addFlavorListener(event->flavors.incrementAndGet());clipboard.setContents(new StringSelection("one"),(c,t)->lost.incrementAndGet());
        clipboard.setContents(new StringSelection("two"),null);EventQueue.invokeAndWait(()->{});
        check(clipboard.getData(DataFlavor.stringFlavor).equals("two")&&lost.get()==1&&flavors.get()==1,"clipboard data, ownership and flavor changes");
        String[] nativeText={"native"};Clipboard.Bridge bridge=new Clipboard.Bridge(){public String readText(){return nativeText[0];}public void writeText(String text){nativeText[0]=text;}};
        clipboard.setBridge(bridge);check(clipboard.getData(DataFlavor.stringFlavor).equals("native"),"native clipboard read");
        clipboard.setContents(new StringSelection("export"),null);check(nativeText[0].equals("export"),"native clipboard write");clipboard.clearBridge(bridge);
        FloatControl control=new FloatControl(FloatControl.Type.VOLUME,0,1,.02f,25,.5f,"units","low","mid","high"){};
        check(control.getPrecision()==.02f&&control.getUpdatePeriod()==25&&control.getMinLabel().equals("low"),"float control metadata");
        PopupMenu popup=new PopupMenu("test");popup.add(new MenuItem("one"));check(popup.getItemCount()==1,"AWT menu model");
        javax.swing.JPanel parent=new javax.swing.JPanel();
        try{parent.add(parent);throw new AssertionError("self cycle accepted");}catch(IllegalArgumentException expected){}
        javax.swing.JPanel child=new javax.swing.JPanel();parent.add(child);
        try{child.add(parent,"Center");throw new AssertionError("ancestor cycle accepted");}catch(IllegalArgumentException expected){}
        javax.swing.JLayeredPane layers=new javax.swing.JLayeredPane();layers.setSize(20,20);
        javax.swing.JPanel lower=new javax.swing.JPanel();lower.setBackground(Color.RED);lower.setBounds(0,0,20,20);
        javax.swing.JPanel upper=new javax.swing.JPanel();upper.setBackground(Color.BLUE);upper.setBounds(0,0,20,20);
        layers.add(lower,Integer.valueOf(0));layers.add(upper,Integer.valueOf(100));
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics=image.createGraphics();Window.hostPaint(graphics,layers);graphics.dispose();
        check(image.getRGB(10,10)==Color.BLUE.getRGB(),"higher layer paints above lower layer");
        System.out.println("PASS: management, focus/lifecycle, host visibility, clipboard, control metadata and AWT menu model");
    }
}
