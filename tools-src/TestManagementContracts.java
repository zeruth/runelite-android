// host-contract: runs=both
import java.io.File;
import java.lang.management.*;
import java.util.*;
import javax.management.*;

/**
 * java.lang.management, com.sun.management and javax.management as the JDK behaves; the same checks
 * run against the host JDK and the shadow. A value the platform cannot provide is -1 (the JDK's
 * "not available"), which is only accepted where procfs is absent: on Linux and Android the
 * shadow must read real values. Android 8+ denies apps /proc/stat, so the system CPU load may
 * stay -1 there; the device run is TestRuntimeCoreContracts.
 */
public final class TestManagementContracts {
    public interface CounterMBean { int getValue(); void setValue(int value); boolean isEven(); int add(int delta); }
    public static final class Counter implements CounterMBean {
        private int value;
        public int getValue(){return value;}public void setValue(int value){this.value=value;}public boolean isEven(){return value%2==0;}public int add(int delta){return value+=delta;}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final boolean PROCFS=new File("/proc/meminfo").canRead();
    /** -1 off procfs, otherwise within [min, max]. */
    static void range(String what,double value,double min,double max){
        if(value==-1&&!PROCFS)return;
        check(value>=min&&value<=max,what+" = "+value+" outside ["+min+", "+max+"]");
    }
    static void load(String what,double value){if(value!=-1)check(value>=0&&value<=1,what+" = "+value);}
    static long burn(){long x=0;long end=System.nanoTime()+60_000_000L;while(System.nanoTime()<end)x+=Long.numberOfTrailingZeros(x+1)+1;return x;}

    public static void main(String[]args)throws Exception{
        objectNames();registry();threads();operatingSystem();runtimeAndMemory();platform();
        System.out.println("PASS: ObjectName grammar, MBean registry, thread dumps, OS/runtime/memory beans and the platform MBean server");
    }

    static void objectNames()throws Exception{
        ObjectName name=new ObjectName("test:z=2,a=1"),same=new ObjectName("test:a=1,z=2");
        check(name.equals(same),"canonical equality");check(name.getCanonicalName().equals("test:a=1,z=2"),"canonical order");
        check(name.getKeyPropertyListString().equals("z=2,a=1"),"written key order");check(name.getKeyProperty("z").equals("2"),"key property");
        check(new ObjectName("test:a=*,*").apply(name),"property wildcard");check(!new ObjectName("test:a=*").apply(name),"exact property count");
        check(new ObjectName("te?t:*").apply(name)&&new ObjectName("te?t:*").isDomainPattern(),"domain wildcard");
        check(!name.isPattern()&&new ObjectName("test:a=1,*").isPropertyListPattern(),"pattern kinds");
        check(ObjectName.WILDCARD.apply(name),"WILDCARD matches every name");
        String text="a\nb*\"\\";check(ObjectName.unquote(ObjectName.quote(text)).equals(text),"quote roundtrip");
        check(new ObjectName("test:k="+ObjectName.quote("x,y=z")).getKeyProperty("k").equals(ObjectName.quote("x,y=z")),"quoted value keeps separators");
        for(String bad:new String[]{"test:a=1,a=2","nocolon","test:","test:a","test:a=1,","test:a=\"open"}){
            try{new ObjectName(bad);throw new AssertionError("malformed name accepted: "+bad);}catch(MalformedObjectNameException expected){}
        }
        check(new ObjectName("a:type=x").compareTo(new ObjectName("b:type=a"))<0,"domain orders first");
    }

    static void registry()throws Exception{
        MBeanServer server=MBeanServerFactory.newMBeanServer();
        ObjectName name=new ObjectName("test:z=2,a=1"),same=new ObjectName("test:a=1,z=2");
        ObjectInstance instance=server.registerMBean(new Counter(),name);
        check(instance.getObjectName().equals(name)&&instance.getClassName().equals(Counter.class.getName()),"object instance");
        check(server.isRegistered(same),"registration");server.setAttribute(name,new Attribute("Value",7));
        check(server.getAttribute(name,"Value").equals(7),"attribute mutation");check(server.getAttribute(name,"Even").equals(false),"is-getter attribute");
        check(server.invoke(name,"add",new Object[]{5},new String[]{"int"}).equals(12),"typed method invocation");
        AttributeList values=server.getAttributes(name,new String[]{"Value","Missing"});
        check(values.size()==1&&((Attribute)values.get(0)).getValue().equals(12),"getAttributes skips unknown names");
        try{server.getAttribute(name,"Missing");throw new AssertionError("unknown attribute read");}catch(AttributeNotFoundException expected){}
        check(server.queryNames(new ObjectName("test:*"),null).contains(name),"name query");
        check(server.queryNames(new ObjectName("other:*"),null).isEmpty(),"query by domain");
        check(server.queryMBeans(new ObjectName("test:a=1,*"),null).size()==1,"bean query");
        check(server.isInstanceOf(name,CounterMBean.class.getName()),"isInstanceOf the MBean interface");
        check(Arrays.asList(server.getDomains()).contains("test"),"domains");
        try{server.registerMBean(new Counter(),same);throw new AssertionError("duplicate bean accepted");}catch(InstanceAlreadyExistsException expected){}
        try{server.registerMBean(new Object(),new ObjectName("test:type=plain"));throw new AssertionError("non-compliant bean accepted");}catch(NotCompliantMBeanException expected){}
        try{server.registerMBean(new Counter(),new ObjectName("test:*"));throw new AssertionError("pattern name accepted");}catch(RuntimeException expected){/* JDK: RuntimeOperationsException wrapping the IllegalArgumentException the shadow throws */}
        server.unregisterMBean(name);check(!server.isRegistered(name),"unregister");
        try{server.getAttribute(name,"Value");throw new AssertionError("unregistered bean read");}catch(InstanceNotFoundException expected){}
    }

    static void threads(){
        ThreadMXBean threads=ManagementFactory.getThreadMXBean();long self=Thread.currentThread().getId();
        ThreadInfo mine=null;
        for(ThreadInfo info:threads.dumpAllThreads(false,false))if(info.getThreadId()==self)mine=info;
        check(mine!=null,"dumpAllThreads includes the current thread");
        check(mine.getThreadState()==Thread.State.RUNNABLE&&mine.getThreadName().equals(Thread.currentThread().getName()),"current thread state and name");
        boolean caller=false;for(StackTraceElement frame:mine.getStackTrace())caller|=frame.getClassName().equals(TestManagementContracts.class.getName());
        check(caller,"the current thread's dump has a stack reaching the caller");
        check(threads.dumpAllThreads(false,false,1).length>0,"bounded dump");
        for(ThreadInfo info:threads.dumpAllThreads(false,false,1))check(info.getStackTrace().length<=1,"maxDepth bounds every trace");
        check(Arrays.stream(threads.getAllThreadIds()).anyMatch(id->id==self),"getAllThreadIds includes the current thread");
        check(threads.getThreadInfo(self).getStackTrace().length==0,"getThreadInfo(id) carries no stack");
        check(threads.getThreadInfo(self,3).getStackTrace().length<=3&&threads.getThreadInfo(self,3).getStackTrace().length>0,"getThreadInfo(id, depth)");
        check(threads.getThreadInfo(new long[]{self},false,false)[0].getStackTrace().length>0,"getThreadInfo(ids, false, false) carries the full stack");
        check(threads.getThreadInfo(new long[]{self},false,false,2)[0].getStackTrace().length<=2,"getThreadInfo(ids, false, false, depth)");
        check(threads.getThreadInfo(new long[]{self,Long.MAX_VALUE},5)[1]==null,"an unknown id maps to null");
        try{threads.getThreadInfo(0);throw new AssertionError("id 0 accepted");}catch(IllegalArgumentException expected){}
        try{threads.getThreadInfo(self,-1);throw new AssertionError("negative depth accepted");}catch(IllegalArgumentException expected){}
        check(threads.findDeadlockedThreads()==null&&threads.findMonitorDeadlockedThreads()==null,"no deadlock found");
        int count=threads.getThreadCount();check(count>=1&&threads.getPeakThreadCount()>=count,"thread and peak counts");
        check(threads.getTotalStartedThreadCount()>=count&&threads.getDaemonThreadCount()>=0,"started and daemon counts");
        if(threads.isCurrentThreadCpuTimeSupported()&&threads.isThreadCpuTimeEnabled()){
            long before=threads.getCurrentThreadCpuTime();burn();
            check(threads.getCurrentThreadCpuTime()>before&&threads.getCurrentThreadUserTime()>=0,"current thread CPU time advances");
        }
    }

    static void operatingSystem(){
        OperatingSystemMXBean base=ManagementFactory.getOperatingSystemMXBean();
        check(base instanceof com.sun.management.OperatingSystemMXBean,"the platform OS bean is the com.sun extension");
        com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)base;
        check(os.getAvailableProcessors()==Runtime.getRuntime().availableProcessors(),"available processors");
        check(os.getName()!=null&&os.getArch()!=null&&os.getVersion()!=null,"os name, arch and version");
        double average=os.getSystemLoadAverage();
        if(new File("/proc/loadavg").canRead())check(average>=0,"load average from /proc/loadavg");else check(average==-1||average>=0,"load average");
        load("cpu load",os.getCpuLoad());load("process cpu load",os.getProcessCpuLoad());burn();
        load("cpu load after work",os.getSystemCpuLoad());load("process cpu load after work",os.getProcessCpuLoad());
        long total=os.getTotalMemorySize(),free=os.getFreeMemorySize();
        range("total physical memory",total,1<<20,Long.MAX_VALUE);range("free physical memory",free,0,total==-1?Long.MAX_VALUE:total);
        check(os.getTotalPhysicalMemorySize()==total,"deprecated total alias");
        long swap=os.getTotalSwapSpaceSize();range("total swap",swap,0,Long.MAX_VALUE);range("free swap",os.getFreeSwapSpaceSize(),0,swap==-1?Long.MAX_VALUE:swap);
        range("committed virtual memory",os.getCommittedVirtualMemorySize(),1<<20,Long.MAX_VALUE);
        range("process cpu time",os.getProcessCpuTime(),1,Long.MAX_VALUE);
    }

    static void runtimeAndMemory(){
        RuntimeMXBean runtime=ManagementFactory.getRuntimeMXBean();long now=System.currentTimeMillis();
        // Off procfs and off Android the shadow has no pid source (its ProcessHandle asks this bean back).
        range("pid",runtime.getPid(),1,Long.MAX_VALUE);check(runtime.getName().startsWith(runtime.getPid()+"@"),"pid@host name");
        check(runtime.getStartTime()>0&&runtime.getStartTime()<=now&&runtime.getUptime()>=0&&runtime.getUptime()<=now-runtime.getStartTime()+1000,"start time and uptime");
        check(runtime.getInputArguments()!=null&&runtime.getSystemProperties().get("java.vm.name").equals(System.getProperty("java.vm.name")),"input arguments and system properties");
        check(runtime.getClassPath().equals(System.getProperty("java.class.path","")),"class path");
        MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        check(heap.getUsed()>0&&heap.getCommitted()>=heap.getUsed()&&(heap.getMax()==-1||heap.getMax()>=heap.getCommitted()),"heap usage");
        check(!ManagementFactory.getMemoryPoolMXBeans().isEmpty(),"memory pools");
        for(MemoryPoolMXBean pool:ManagementFactory.getMemoryPoolMXBeans())if(pool.isValid())check(pool.getUsage()==null||pool.getUsage().getUsed()>=0,"pool usage "+pool.getName());
        List<GarbageCollectorMXBean> collectors=ManagementFactory.getGarbageCollectorMXBeans();check(!collectors.isEmpty(),"collectors");
        for(GarbageCollectorMXBean gc:collectors)check(gc.getCollectionCount()>=-1&&gc.getCollectionTime()>=-1&&gc.getMemoryPoolNames()!=null,"collector "+gc.getName());
        check(ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount()>=ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount(),"class loading counts");
        check(ManagementFactory.getCompilationMXBean()==null||ManagementFactory.getCompilationMXBean().getName()!=null,"compilation bean");
    }

    static void platform()throws Exception{
        check(ManagementFactory.getPlatformMXBean(ThreadMXBean.class).getObjectName().equals(new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME)),"getPlatformMXBean(ThreadMXBean)");
        check(ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class)!=null,"getPlatformMXBean(com.sun OS bean)");
        try{ManagementFactory.getPlatformMXBean(GarbageCollectorMXBean.class);throw new AssertionError("multi-instance interface accepted");}catch(IllegalArgumentException expected){}
        check(ManagementFactory.getPlatformMXBeans(GarbageCollectorMXBean.class).size()==ManagementFactory.getGarbageCollectorMXBeans().size(),"getPlatformMXBeans");
        check(ManagementFactory.getPlatformManagementInterfaces().contains(MemoryPoolMXBean.class),"platform interfaces");
        MBeanServer server=ManagementFactory.getPlatformMBeanServer();check(server==ManagementFactory.getPlatformMBeanServer(),"one platform server");
        ObjectName runtime=new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
        check(server.isRegistered(runtime)&&server.isRegistered(new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)),"platform beans registered");
        check(((Long)server.getAttribute(runtime,"Uptime"))>=0,"platform attribute read");
        check(!server.queryNames(new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE+",*"),null).isEmpty(),"collectors queryable");
        check(server.queryNames(new ObjectName("java.lang:type=Threading"),null).size()==1,"exact platform query");
    }
}
