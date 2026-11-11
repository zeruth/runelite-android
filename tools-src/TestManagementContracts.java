// host-contract: runs=patched
import javax.management.*;
public final class TestManagementContracts {
    public static final class Counter {
        private int value;
        public int getValue(){return value;}public void setValue(int value){this.value=value;}public int add(int delta){return value+=delta;}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        ObjectName name=new ObjectName("test:z=2,a=1"),same=new ObjectName("test:a=1,z=2");
        check(name.equals(same),"canonical equality");check(name.getCanonicalName().equals("test:a=1,z=2"),"canonical order");
        check(new ObjectName("test:a=*,*").apply(name),"property wildcard");check(!new ObjectName("test:a=*").apply(name),"exact property count");
        String text="a\nb*\"\\";check(ObjectName.unquote(ObjectName.quote(text)).equals(text),"quote roundtrip");
        try{new ObjectName("test:a=1,a=2");throw new AssertionError("duplicate property accepted");}catch(MalformedObjectNameException expected){}
        MBeanServer server=(MBeanServer)Class.forName("net.runelite.awt.impl.StubMBeanServer").getField("INSTANCE").get(null);server.registerMBean(new Counter(),name);
        check(server.isRegistered(same),"registration");server.setAttribute(name,new Attribute("Value",7));
        check(server.getAttribute(name,"Value").equals(7),"attribute mutation");
        check(server.invoke(name,"add",new Object[]{5},new String[]{"int"}).equals(12),"typed method invocation");
        check(server.queryNames(new ObjectName("test:*"),null).contains(name),"name query");
        try{server.registerMBean(new Counter(),same);throw new AssertionError("duplicate bean accepted");}catch(InstanceAlreadyExistsException expected){}
        server.unregisterMBean(name);check(!server.isRegistered(name),"unregister");
        System.out.println("PASS: ObjectName parsing/canonicalization/patterns and bean registry/query/read/write/invoke");
    }
}
