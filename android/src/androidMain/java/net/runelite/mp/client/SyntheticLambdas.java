package net.runelite.mp.client;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * The class a {@code LambdaMetafactory.metafactory} call site of the client becomes in
 * DesugarLambdasTask: implements the functional interface, holds the captures, and forwards the
 * SAM call to the implementation method. Java rather than Kotlin so the host regression test
 * (tools-src/TestClientLambdaLowering.java) runs this exact generator.
 */
public final class SyntheticLambdas {
    private SyntheticLambdas() {}

    public static byte[] generate(String synName, String invokedDescriptor, String samName, Type samMethodType,
                                  Type instantiatedMethodType, Handle impl, String captureDesc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String fiName = Type.getReturnType(invokedDescriptor).getInternalName();
        Type[] captureTypes = Type.getArgumentTypes(captureDesc);

        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, synName, null, "java/lang/Object", new String[] {fiName});

        for (int i = 0; i < captureTypes.length; i++) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, "arg$" + i, captureTypes[i].getDescriptor(), null, null).visitEnd();
        }

        // Constructor: store captures into fields.
        StringBuilder ctorDesc = new StringBuilder("(");
        for (Type t : captureTypes) ctorDesc.append(t.getDescriptor());
        ctorDesc.append(")V");
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", ctorDesc.toString(), null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        int slot = 1;
        for (int i = 0; i < captureTypes.length; i++) {
            Type t = captureTypes[i];
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(t.getOpcode(ILOAD), slot);
            ctor.visitFieldInsn(PUTFIELD, synName, "arg$" + i, t.getDescriptor());
            slot += t.getSize();
        }
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // SAM method body.
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, samName, samMethodType.getDescriptor(), null, null);
        mv.visitCode();
        Type[] samArgs = Type.getArgumentTypes(samMethodType.getDescriptor());
        Type[] instantiatedArgs = Type.getArgumentTypes(instantiatedMethodType.getDescriptor());

        // For NEWINVOKESPECIAL the impl is a constructor: emit NEW + DUP first.
        if (impl.getTag() == H_NEWINVOKESPECIAL) {
            mv.visitTypeInsn(NEW, impl.getOwner());
            mv.visitInsn(DUP);
        }

        // Captures first; for a non-static method reference the first capture is the receiver.
        for (int i = 0; i < captureTypes.length; i++) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, synName, "arg$" + i, captureTypes[i].getDescriptor());
        }

        // SAM args, cast to the instantiated method type.
        slot = 1;
        for (int i = 0; i < samArgs.length; i++) {
            Type samArg = samArgs[i];
            mv.visitVarInsn(samArg.getOpcode(ILOAD), slot);
            slot += samArg.getSize();
            Type instArg = i < instantiatedArgs.length ? instantiatedArgs[i] : null;
            if (instArg != null && !samArg.equals(instArg)) {
                adaptValue(mv, samArg, instArg);
            }
        }

        mv.visitMethodInsn(invokeOpcode(impl), impl.getOwner(),
                impl.getTag() == H_NEWINVOKESPECIAL ? "<init>" : impl.getName(),
                implDescriptor(impl), impl.isInterface());

        // Adapt return value to SAM return type.
        Type samReturn = Type.getReturnType(samMethodType.getDescriptor());
        Type implReturn = impl.getTag() == H_NEWINVOKESPECIAL ? Type.getObjectType(impl.getOwner()) : Type.getReturnType(impl.getDesc());
        if (!samReturn.equals(implReturn)) {
            if (samReturn.getSort() == Type.VOID) {
                if (implReturn.getSize() > 0) mv.visitInsn(implReturn.getSize() == 2 ? POP2 : POP);
            } else {
                adaptValue(mv, implReturn, samReturn);
            }
        }
        mv.visitInsn(samReturn.getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    static int invokeOpcode(Handle impl) {
        switch (impl.getTag()) {
            case H_INVOKESTATIC: return INVOKESTATIC;
            case H_INVOKEVIRTUAL: return INVOKEVIRTUAL;
            case H_INVOKEINTERFACE: return INVOKEINTERFACE;
            // A reference to a PRIVATE instance method compiles to H_INVOKESPECIAL. The synthetic
            // lambda class is NOT a subclass of impl.owner, so emitting a literal INVOKESPECIAL is
            // a VerifyError (invokespecial is only legal for the current class or a superclass —
            // on Android this throws at class-load and, because the gamepack wraps rendering in
            // try/catch, silently drops whatever that lambda was driving). The target has been
            // widened to public, so dispatch it virtually instead — the standard lambda-desugaring
            // lowering. NEWINVOKESPECIAL (constructor refs) genuinely needs INVOKESPECIAL.
            case H_INVOKESPECIAL: return impl.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
            case H_NEWINVOKESPECIAL: return INVOKESPECIAL;
            case H_GETFIELD: return GETFIELD;
            case H_GETSTATIC: return GETSTATIC;
            case H_PUTFIELD: return PUTFIELD;
            case H_PUTSTATIC: return PUTSTATIC;
            default: return INVOKESTATIC;
        }
    }

    private static String implDescriptor(Handle impl) {
        if (impl.getTag() != H_NEWINVOKESPECIAL) return impl.getDesc();
        // Constructors are invoked with a void descriptor whatever the handle's says.
        return Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(impl.getDesc()));
    }

    private static void adaptValue(MethodVisitor mv, Type from, Type to) {
        if (from.equals(to)) return;
        boolean fromRef = from.getSort() == Type.OBJECT || from.getSort() == Type.ARRAY;
        boolean toRef = to.getSort() == Type.OBJECT || to.getSort() == Type.ARRAY;
        if (fromRef && toRef && !to.getInternalName().equals("java/lang/Object")) {
            mv.visitTypeInsn(CHECKCAST, to.getInternalName());
        }
    }
}
