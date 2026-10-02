import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;

/** Bytecode audit avoids accidentally resolving Android AWT calls against the host JDK. */
public class VerifyHdAwtBindings {
    static final Map<String, Api> api = new HashMap<>();
    static final class Api {
        String parent;
        String[] interfaces;
        Set<String> members = new HashSet<>();
        Set<String> finalMethods = new HashSet<>();
    }
    static boolean local(String name) {
        return name.startsWith("java/awt/") || name.startsWith("javax/swing/") ||
            name.startsWith("java/lang/management/") || name.startsWith("com/sun/management/") || name.startsWith("net/runelite/rlawt/");
    }
    static void add(ClassReader reader) {
        Api entry = new Api();
        api.put(reader.getClassName(), entry);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            public void visit(int v, int a, String n, String s, String p, String[] i) { entry.parent=p; entry.interfaces=i; }
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                String member="M " + n + d;
                entry.members.add(member);
                if((a & Opcodes.ACC_FINAL) != 0 && (a & Opcodes.ACC_PRIVATE) == 0) entry.finalMethods.add(member);
                return null;
            }
            public FieldVisitor visitField(int a, String n, String d, String s, Object val) { entry.members.add("F " + n + d); return null; }
        }, ClassReader.SKIP_CODE);
    }
    static boolean exists(String owner, String member) {
        if(owner == null) return false;
        Api entry=api.get(owner);
        if(entry == null && !local(owner)) {
            try { add(new ClassReader(owner)); entry=api.get(owner); } catch(Exception e) { return false; }
        }
        if(entry == null) return false;
        if(entry.members.contains(member)) return true;
        if(member.startsWith("M <init>")) return false;
        if(exists(entry.parent, member)) return true;
        for(String i:entry.interfaces) if(exists(i,member)) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        for(int root=1;root<args.length;root++) try(var paths=Files.walk(Path.of(args[root]))) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".class"))::iterator) add(new ClassReader(Files.readAllBytes(p)));
        }
        SortedSet<String> missing=new TreeSet<>();
        for(var item:api.entrySet()) {
            for(String parent=item.getValue().parent; parent != null && api.containsKey(parent); parent=api.get(parent).parent) {
                for(String member:api.get(parent).finalMethods)
                    if(item.getValue().members.contains(member)) missing.add("Illegal final override: " + item.getKey() + " " + member);
            }
        }
        try(var paths=Files.walk(Path.of(args[0]))) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".class"))::iterator) {
                if(p.toString().contains("OpenCLManager") || p.toString().contains("LegacyRenderer")) continue;
                String caller=Path.of(args[0]).relativize(p).toString();
                new ClassReader(Files.readAllBytes(p)).accept(new ClassVisitor(Opcodes.ASM9) {
                    public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            void check(String owner,String member) {
                                if(local(owner) && !exists(owner,member)) missing.add(owner+" "+member+" <- "+caller+":"+n);
                            }
                            public void visitMethodInsn(int o,String c,String n,String d,boolean i) { check(c,"M "+n+d); }
                            public void visitFieldInsn(int o,String c,String n,String d) { check(c,"F "+n+d); }
                            public void visitInvokeDynamicInsn(String n,String d,Handle b,Object... args) {
                                for(Object arg:args) if(arg instanceof Handle) { Handle h=(Handle)arg; check(h.getOwner(),"M "+h.getName()+h.getDesc()); }
                            }
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        }
        missing.forEach(System.out::println);
        System.out.println("Missing Android AWT call sites: "+missing.size());
        if(!missing.isEmpty()) System.exit(1);
    }
}
