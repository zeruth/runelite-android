import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.runelite.mp.compat.JdkRetargeter;

/** Transform a real hub JAR and verify unchanged resources and idempotent class rewrites. */
public class TestDesktopRegexTransform {
    private static void checkBoundaryRejection() {
        var writer=new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V17,org.objectweb.asm.Opcodes.ACC_PUBLIC,"ExternalBoundaryFixture",null,"java/lang/Object",null);
        var method=writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC|org.objectweb.asm.Opcodes.ACC_STATIC,"test","()V",null,null);
        method.visitCode();method.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        method.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,"host/UnchangedApi","accept","(Ljava/util/regex/Pattern;)V",false);
        method.visitInsn(org.objectweb.asm.Opcodes.RETURN);method.visitMaxs(1,0);method.visitEnd();writer.visitEnd();
        try {
            DesktopRegexTransformer.rewrite("ExternalBoundaryFixture.class",writer.toByteArray(),Set.of("ExternalBoundaryFixture"));
            throw new AssertionError("host API crossing was not rejected");
        } catch(IllegalArgumentException expected) {
            if(!expected.getMessage().contains("crosses host API"))throw expected;
        }
    }
    public static void main(String[] args) throws Exception {
        checkBoundaryRejection();
        try (ZipFile input=new ZipFile(args[0]); ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            Set<String> classes=new HashSet<>();
            input.stream().filter(e->e.getName().endsWith(".class")).forEach(e->classes.add(e.getName().replaceFirst("\\.class$","")));
            int changed=0;
            for(var entries=input.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();byte[] bytes;
                try(var in=input.getInputStream(entry)){bytes=in.readAllBytes();}
                byte[] result=DesktopRegexTransformer.rewrite(entry.getName(),bytes,classes);
                if(!Arrays.equals(bytes,result))changed++;
                if(!Arrays.equals(result,DesktopRegexTransformer.rewrite(entry.getName(),result,classes)))throw new AssertionError("not idempotent");
                output.putNextEntry(new ZipEntry(entry.getName()));output.write(result);output.closeEntry();
            }
            System.out.println("PASS "+Path.of(args[0]).getFileName()+" changedClasses="+changed);
        }
    }
}
