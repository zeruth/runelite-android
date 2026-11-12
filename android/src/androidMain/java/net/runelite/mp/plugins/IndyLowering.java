package net.runelite.mp.plugins;

import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import static org.objectweb.asm.Opcodes.*;

/**
 * Lowers the bootstraps D8 9.5 cannot desugar at min-api 26 in a hub jar, before D8 runs.
 *
 * <p>D8 desugars every invokedynamic javac emits (LambdaMetafactory, StringConcatFactory,
 * ObjectMethods, SwitchBootstraps), but refuses every {@code ldc} of a dynamic constant: those
 * bootstrapped by {@code java.lang.invoke.ConstantBootstraps} (a JDK 11 class Android does not
 * have) and those with a static bootstrap of their own (the fixture test found D8 rejects even
 * the holder-class shape). Each such
 * {@code ldc} becomes a call to a private static synchronized accessor added to the same class,
 * which evaluates the constant once and caches it: the JVM's resolve-once semantics, and the
 * class's own access to private bootstrap targets, both survive. The matrix of what is lowered
 * here and what D8 is trusted with is in android-awt/PORTING.md.
 */
public final class IndyLowering {
    private static final String CONSTANT_BOOTSTRAPS = "java/lang/invoke/ConstantBootstraps";
    private static final String ACCESSOR = "$condy$";
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");
    private static final Type STRING = Type.getObjectType("java/lang/String");
    private static final Type CLASS = Type.getObjectType("java/lang/Class");
    private static final Type LOOKUP = Type.getObjectType("java/lang/invoke/MethodHandles$Lookup");

    private IndyLowering() {}

    /** Returns {@code bytes} itself when the class has nothing to lower. */
    public static byte[] rewrite(String entry, byte[] bytes) {
        if (!entry.endsWith(".class")) return bytes;
        ClassReader reader = new ClassReader(bytes);
        // A fresh constant pool, not the reader's: the lowered dynamic constants must not
        // linger as unused pool entries for D8 to trip over.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Lowering lowering = new Lowering(writer);
        reader.accept(lowering, 0);
        return lowering.constants.isEmpty() ? bytes : writer.toByteArray();
    }

    static boolean lowered(ConstantDynamic constant) {
        return constant.getBootstrapMethod().getTag() == H_INVOKESTATIC;
    }

    private static final class Lowering extends ClassVisitor {
        final Map<ConstantDynamic, String> constants = new LinkedHashMap<>();
        private String owner;
        private boolean isInterface;

        Lowering(ClassVisitor next) {
            super(ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            owner = name;
            isInterface = (access & ACC_INTERFACE) != 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof ConstantDynamic && lowered((ConstantDynamic) value)) {
                        ConstantDynamic constant = (ConstantDynamic) value;
                        super.visitMethodInsn(INVOKESTATIC, owner, accessor(constant), "()" + constant.getDescriptor(), false);
                    } else {
                        super.visitLdcInsn(value);
                    }
                }
            };
        }

        private String accessor(ConstantDynamic constant) {
            // Interface fields are implicitly final, so there is nowhere to cache the value.
            if (isInterface) {
                throw new IllegalArgumentException("dynamic constant " + constant.getName() + " in interface " + owner + " is not supported");
            }
            String name = constants.get(constant);
            if (name == null) {
                name = ACCESSOR + constants.size();
                constants.put(constant, name);
            }
            return name;
        }

        @Override
        public void visitEnd() {
            for (Map.Entry<ConstantDynamic, String> entry : constants.entrySet()) {
                emitAccessor(entry.getKey(), entry.getValue());
            }
            super.visitEnd();
        }

        private void emitAccessor(ConstantDynamic constant, String name) {
            String desc = constant.getDescriptor();
            String set = name + "$set";
            int fieldAccess = ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
            super.visitField(fieldAccess, name, desc, null, null).visitEnd();
            super.visitField(fieldAccess, set, "Z", null, null).visitEnd();

            // Static synchronized locks the class, so racing first uses evaluate the bootstrap
            // once, as the JVM's constant resolution does.
            int access = ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC | ACC_SYNCHRONIZED;
            String methodDesc = "()" + desc;
            MethodVisitor raw = super.visitMethod(access, name, methodDesc, null, null);
            GeneratorAdapter g = new GeneratorAdapter(raw, access, name, methodDesc);
            g.visitCode();
            Label cached = new Label();
            g.visitFieldInsn(GETSTATIC, owner, set, "Z");
            g.visitJumpInsn(IFNE, cached);
            push(g, constant);
            g.visitFieldInsn(PUTSTATIC, owner, name, desc);
            g.visitInsn(ICONST_1);
            g.visitFieldInsn(PUTSTATIC, owner, set, "Z");
            g.visitLabel(cached);
            // Straight to the writer: GeneratorAdapter only takes expanded frames, and the
            // rest of the class keeps its compressed ones.
            raw.visitFrame(F_SAME, 0, null, 0, null);
            g.visitFieldInsn(GETSTATIC, owner, name, desc);
            g.returnValue();
            g.visitMaxs(0, 0);
            g.visitEnd();
        }

        /** Pushes the constant's value as its own descriptor type, the way its bootstrap computes it. */
        private void push(GeneratorAdapter g, ConstantDynamic constant) {
            Type type = Type.getType(constant.getDescriptor());
            String name = constant.getName();
            if (!constant.getBootstrapMethod().getOwner().equals(CONSTANT_BOOTSTRAPS)) {
                callBootstrap(g, constant, type);
                return;
            }
            String bootstrap = constant.getBootstrapMethod().getName();
            int argc = constant.getBootstrapMethodArgumentCount();
            switch (bootstrap) {
                case "nullConstant":
                    if (!isReference(type)) throw unsupported(constant, "nullConstant of a primitive type");
                    g.visitInsn(ACONST_NULL);
                    return;
                case "primitiveClass":
                    g.visitFieldInsn(GETSTATIC, wrapper(constant, name), "TYPE", "Ljava/lang/Class;");
                    adapt(g, Type.getType(Class.class), type);
                    return;
                case "enumConstant":
                    g.visitFieldInsn(GETSTATIC, type.getInternalName(), name, constant.getDescriptor());
                    return;
                case "getStaticFinal": {
                    Type declaring = argc > 0 ? (Type) constant.getBootstrapMethodArgument(0) : type;
                    g.visitFieldInsn(GETSTATIC, declaring.getInternalName(), name, constant.getDescriptor());
                    return;
                }
                case "invoke": {
                    Object target = argc > 0 ? constant.getBootstrapMethodArgument(0) : null;
                    if (!(target instanceof Handle)) throw unsupported(constant, "invoke without a method handle");
                    Type result = invoke(g, constant, (Handle) target);
                    adapt(g, result, type);
                    return;
                }
                case "explicitCast": {
                    if (argc != 1) throw unsupported(constant, "explicitCast with " + argc + " arguments");
                    Type value = pushArgument(g, constant, constant.getBootstrapMethodArgument(0));
                    adapt(g, value, type);
                    return;
                }
                case "fieldVarHandle":
                case "staticFieldVarHandle":
                case "arrayVarHandle":
                    throw unsupported(constant, "VarHandle (ConstantBootstraps." + bootstrap + "), which Android 8.0 does not have");
                default:
                    throw unsupported(constant, "ConstantBootstraps." + bootstrap);
            }
        }

        /**
         * Any other static bootstrap (the class's own, as JaCoCo and bytecode tools emit): called as
         * the JVM resolves the constant, with the lookup of the class holding the {@code ldc}.
         * D8 9.5 refuses these at every API level ("unsupported constant type").
         */
        private void callBootstrap(GeneratorAdapter g, ConstantDynamic constant, Type type) {
            Handle bsm = constant.getBootstrapMethod();
            Type[] parameters = Type.getArgumentTypes(bsm.getDesc());
            int argc = constant.getBootstrapMethodArgumentCount();
            // A varargs bootstrap would collect its trailing arguments; fixed arity is all that is lowered.
            if (parameters.length != 3 + argc) {
                throw unsupported(constant, bsm.getOwner() + "." + bsm.getName() + " with " + argc
                        + " static arguments for " + (parameters.length - 3) + " parameters");
            }
            // MethodHandles.lookup() is caller-sensitive on ART too (API 26), so this is the
            // holder's own full-privilege lookup, the one the JVM hands the bootstrap.
            g.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()" + LOOKUP.getDescriptor(), false);
            adapt(g, LOOKUP, parameters[0]);
            g.push(constant.getName());
            adapt(g, STRING, parameters[1]);
            g.push(type);
            adapt(g, CLASS, parameters[2]);
            for (int i = 0; i < argc; i++) {
                adapt(g, pushArgument(g, constant, constant.getBootstrapMethodArgument(i)), parameters[3 + i]);
            }
            g.visitMethodInsn(INVOKESTATIC, bsm.getOwner(), bsm.getName(), bsm.getDesc(), bsm.isInterface());
            adapt(g, Type.getReturnType(bsm.getDesc()), type);
        }

        /** ConstantBootstraps.invoke: the trailing static arguments, adapted to the handle's parameters. */
        private Type invoke(GeneratorAdapter g, ConstantDynamic constant, Handle handle) {
            int tag = handle.getTag();
            int argc = constant.getBootstrapMethodArgumentCount() - 1;
            Type ownerType = Type.getObjectType(handle.getOwner());
            switch (tag) {
                case H_GETSTATIC:
                    if (argc != 0) throw unsupported(constant, "getstatic handle with arguments");
                    g.visitFieldInsn(GETSTATIC, handle.getOwner(), handle.getName(), handle.getDesc());
                    return Type.getType(handle.getDesc());
                case H_GETFIELD:
                    if (argc != 1) throw unsupported(constant, "getfield handle without one receiver");
                    adapt(g, pushArgument(g, constant, constant.getBootstrapMethodArgument(1)), ownerType);
                    g.visitFieldInsn(GETFIELD, handle.getOwner(), handle.getName(), handle.getDesc());
                    return Type.getType(handle.getDesc());
                case H_INVOKESTATIC:
                case H_INVOKEVIRTUAL:
                case H_INVOKEINTERFACE:
                case H_INVOKESPECIAL:
                case H_NEWINVOKESPECIAL:
                    break;
                default:
                    throw unsupported(constant, "invoke of a field setter handle");
            }
            if (tag == H_INVOKESPECIAL && !handle.getOwner().equals(owner)) {
                throw unsupported(constant, "invokespecial handle on another class");
            }
            Type[] parameters = Type.getArgumentTypes(handle.getDesc());
            boolean receiver = tag == H_INVOKEVIRTUAL || tag == H_INVOKEINTERFACE || tag == H_INVOKESPECIAL;
            int expected = parameters.length + (receiver ? 1 : 0);
            // ConstantBootstraps.invoke spreads into a varargs handle; a fixed-arity call is all
            // javac-shaped and tool-shaped condy have needed.
            if (argc != expected) throw unsupported(constant, handle.getName() + " with " + argc + " arguments for " + expected + " parameters");
            if (tag == H_NEWINVOKESPECIAL) {
                g.visitTypeInsn(NEW, handle.getOwner());
                g.visitInsn(DUP);
            }
            for (int i = 0; i < argc; i++) {
                Type to = receiver ? (i == 0 ? ownerType : parameters[i - 1]) : parameters[i];
                adapt(g, pushArgument(g, constant, constant.getBootstrapMethodArgument(i + 1)), to);
            }
            switch (tag) {
                case H_INVOKESTATIC:
                    g.visitMethodInsn(INVOKESTATIC, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
                    return Type.getReturnType(handle.getDesc());
                case H_INVOKEVIRTUAL:
                    g.visitMethodInsn(INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), false);
                    return Type.getReturnType(handle.getDesc());
                case H_INVOKEINTERFACE:
                    g.visitMethodInsn(INVOKEINTERFACE, handle.getOwner(), handle.getName(), handle.getDesc(), true);
                    return Type.getReturnType(handle.getDesc());
                case H_INVOKESPECIAL:
                    g.visitMethodInsn(INVOKESPECIAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
                    return Type.getReturnType(handle.getDesc());
                default:
                    g.visitMethodInsn(INVOKESPECIAL, handle.getOwner(), "<init>", handle.getDesc(), false);
                    return ownerType;
            }
        }

        /** Pushes one static bootstrap argument and returns the type it lands on the stack as. */
        private Type pushArgument(GeneratorAdapter g, ConstantDynamic constant, Object argument) {
            if (argument instanceof Integer) {
                g.push((Integer) argument);
                return Type.INT_TYPE;
            } else if (argument instanceof Long) {
                g.push((Long) argument);
                return Type.LONG_TYPE;
            } else if (argument instanceof Float) {
                g.push((Float) argument);
                return Type.FLOAT_TYPE;
            } else if (argument instanceof Double) {
                g.push((Double) argument);
                return Type.DOUBLE_TYPE;
            } else if (argument instanceof String) {
                g.push((String) argument);
                return Type.getType(String.class);
            } else if (argument instanceof Type) {
                Type t = (Type) argument;
                if (t.getSort() == Type.METHOD) {
                    g.visitLdcInsn(t);
                    return Type.getObjectType("java/lang/invoke/MethodType");
                }
                g.push(t);
                return Type.getType(Class.class);
            } else if (argument instanceof ConstantDynamic && lowered((ConstantDynamic) argument)) {
                ConstantDynamic nested = (ConstantDynamic) argument;
                push(g, nested);
                return Type.getType(nested.getDescriptor());
            }
            // A MethodHandle value would need const-method-handle (API 28), and any other
            // dynamic constant has no lowering here.
            throw unsupported(constant, "bootstrap argument " + argument);
        }

        /** The conversion ConstantBootstraps applies when its result is cast to the constant's type. */
        private void adapt(GeneratorAdapter g, Type from, Type to) {
            if (from.equals(to)) return;
            boolean fromRef = isReference(from);
            boolean toRef = isReference(to);
            if (fromRef && toRef) {
                if (!to.equals(OBJECT)) g.checkCast(to);
            } else if (fromRef) {
                g.unbox(to);
            } else if (toRef) {
                g.valueOf(from);
                if (!to.equals(OBJECT)) g.checkCast(to);
            } else {
                if (from.getSort() == Type.BOOLEAN || to.getSort() == Type.BOOLEAN) {
                    throw new IllegalArgumentException("dynamic constant in " + owner + ": no conversion from " + from + " to " + to);
                }
                g.cast(from, to);
            }
        }

        private String wrapper(ConstantDynamic constant, String descriptor) {
            switch (descriptor) {
                case "Z": return "java/lang/Boolean";
                case "B": return "java/lang/Byte";
                case "C": return "java/lang/Character";
                case "S": return "java/lang/Short";
                case "I": return "java/lang/Integer";
                case "J": return "java/lang/Long";
                case "F": return "java/lang/Float";
                case "D": return "java/lang/Double";
                case "V": return "java/lang/Void";
                default: throw unsupported(constant, "primitiveClass " + descriptor);
            }
        }

        private IllegalArgumentException unsupported(ConstantDynamic constant, String what) {
            return new IllegalArgumentException("dynamic constant " + constant.getName() + ":" + constant.getDescriptor()
                    + " in " + owner + " needs " + what + ", which the hub dexer cannot lower");
        }
    }

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }
}
