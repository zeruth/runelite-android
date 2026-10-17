import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
/** Checks cross-package class references in every generated client lambda. */
public class TestLambdaClassAccess {
 public static void main(String[] args) throws Exception {
  Map<String,byte[]> classes=new HashMap<>();
  try(JarFile jar=new JarFile(args[0])) {
   for(var entries=jar.entries();entries.hasMoreElements();) {
    var entry=entries.nextElement();
    if(entry.getName().endsWith(".class")) try(var in=jar.getInputStream(entry)) {
     classes.put(entry.getName().replace(".class",""),in.readAllBytes());
    }
   }
  }
  Set<String> references=new TreeSet<>(); int count=0;
  for(var entry:classes.entrySet()) {
   if(!entry.getKey().startsWith("runelite/desugar/Lambda")) continue;
   count++;
   new ClassReader(entry.getValue()).accept(new ClassVisitor(Opcodes.ASM9) {
    void type(Type t) {
     switch(t.getSort()) {
      case Type.OBJECT: references.add(t.getInternalName()); break;
      case Type.ARRAY: type(t.getElementType()); break;
      case Type.METHOD: for(Type a:t.getArgumentTypes())type(a); type(t.getReturnType()); break;
     }
    }
    public void visit(int v,int a,String n,String s,String parent,String[] interfaces) {
     references.add(parent); references.addAll(Arrays.asList(interfaces));
    }
    public FieldVisitor visitField(int a,String n,String d,String s,Object v) {type(Type.getType(d)); return null;}
    public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
     type(Type.getMethodType(d));
     return new MethodVisitor(Opcodes.ASM9) {
      public void visitTypeInsn(int op,String t) {if(t.startsWith("["))type(Type.getType(t));else references.add(t);}
      public void visitMethodInsn(int op,String owner,String n,String d,boolean itf) {references.add(owner); type(Type.getMethodType(d));}
      public void visitFieldInsn(int op,String owner,String n,String d) {references.add(owner); type(Type.getType(d));}
     };
    }
   },ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);
  }
  if(count==0)throw new AssertionError("No generated lambdas found");
  for(String reference:references) {
   byte[] bytes=classes.get(reference);
   if(bytes!=null && (new ClassReader(bytes).getAccess()&Opcodes.ACC_PUBLIC)==0)
    throw new AssertionError("Generated lambda references inaccessible class: "+reference);
  }
  System.out.println("PASS: class access checked for all "+count+" generated lambdas");
 }
}
