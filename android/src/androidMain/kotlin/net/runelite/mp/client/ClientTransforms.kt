// Ported verbatim from android/build.gradle.kts, where these ran at build time over the
// bundled injected client. They now run on the device (see ClientPreparer) over the
// injected-client jar downloaded from repo.runelite.net; only the Gradle scaffolding
// (task properties, logger) changed. Keep the transforms themselves in step with each
// other: they're order-dependent and load-bearing.
@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "NAME_SHADOWING")

package net.runelite.mp.client

import net.runelite.mp.compat.JdkRetargeter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Stands in for Gradle's task logger. */
private object logger {
    fun lifecycle(message: String) { android.util.Log.i("ClientPreparer", message) }
    fun warn(message: String) { android.util.Log.w("ClientPreparer", message) }
    fun info(message: String) { android.util.Log.i("ClientPreparer", message) }
}

// Implementation class for rewriteLauncherEnv — run by ClientPreparer.

internal class RewriteLauncherEnvTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File

    /** Jar entry to patch. Every other entry is copied through unchanged. */
    lateinit var classEntry: String

    /** Launcher credential names whose lookups get redirected to System.getProperty. */
    lateinit var launcherKeys: List<String>

    private companion object {
        const val GETTER_DESC = "(Ljava/lang/String;)Ljava/lang/String;"
        const val SYSTEM = "java/lang/System"
    }

    fun run() {
        val inFile = inputJar
        val outFile = outputJar
        val entryName = classEntry
        val keys = launcherKeys.toSet()
        outFile.parentFile.mkdirs()

        var patched = false
        val rewrittenKeys = sortedSetOf<String>()
        val touchedMethods = linkedSetOf<String>()

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name == entryName) {
                        patched = true
                        output.write(rewrite(bytes, keys, rewrittenKeys, touchedMethods))
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
            }
        }

        // Both checks below are hard failures rather than warnings: an unpatched jar boots
        // fine and only falls over at the login screen, which is a miserable thing to
        // debug. If a revision changes the shape of these call sites, the build is what
        // should tell you.
        require(patched) { "rewriteLauncherEnv: no '$entryName' entry in ${inFile.name}" }
        val missing = keys - rewrittenKeys
        require(missing.isEmpty()) {
            "rewriteLauncherEnv: nothing rewritten for ${missing.sorted()} in $entryName. " +
                "The client's credential reads changed shape this revision — disassemble " +
                "client.init and re-check the ldc + invokestatic pattern."
        }
        logger.lifecycle(
            "rewriteLauncherEnv: redirected launcher credential lookups to " +
                "System.getProperty in $entryName — keys ${rewrittenKeys.toList()}, " +
                "methods ${touchedMethods.toList()}"
        )
    }

    /**
     * Rewrites `ldc "<KEY>"` + `invokestatic <obf>(String)String` into
     * `ldc "<KEY>"` + `invokestatic java/lang/System.getProperty(String)String`.
     *
     * The two instructions have to be adjacent, so any instruction or label in between
     * clears the pending match — otherwise a stale key could latch onto an unrelated
     * `(String)String` static further down the method. Copying the reader's constant pool
     * (`ClassWriter(cr, 0)`) leaves the rest of the class alone and skips frame and
     * max-stack recomputation, which ASM couldn't do here anyway: the injected jar
     * references obfuscated types that aren't loadable from the buildscript classloader.
     * The substitution is stack-neutral, so there's nothing to recompute.
     */
    private fun rewrite(
        bytes: ByteArray,
        keys: Set<String>,
        rewrittenKeys: MutableSet<String>,
        touchedMethods: MutableSet<String>,
    ): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(cr, 0)
        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, delegate) {
                    private var pendingKey: String? = null

                    override fun visitLdcInsn(value: Any?) {
                        super.visitLdcInsn(value)
                        pendingKey = (value as? String)?.takeIf { it in keys }
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        mName: String,
                        mDescriptor: String,
                        isInterface: Boolean,
                    ) {
                        val key = pendingKey
                        pendingKey = null
                        if (key != null &&
                            opcode == Opcodes.INVOKESTATIC &&
                            mDescriptor == GETTER_DESC &&
                            owner != SYSTEM
                        ) {
                            rewrittenKeys.add(key)
                            touchedMethods.add(name)
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SYSTEM, "getProperty", GETTER_DESC, false)
                            return
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface)
                    }

                    // Anything else between the ldc and the call breaks the pattern.
                    override fun visitInsn(opcode: Int) {
                        pendingKey = null
                        super.visitInsn(opcode)
                    }

                    override fun visitIntInsn(opcode: Int, operand: Int) {
                        pendingKey = null
                        super.visitIntInsn(opcode, operand)
                    }

                    override fun visitVarInsn(opcode: Int, varIndex: Int) {
                        pendingKey = null
                        super.visitVarInsn(opcode, varIndex)
                    }

                    override fun visitTypeInsn(opcode: Int, type: String) {
                        pendingKey = null
                        super.visitTypeInsn(opcode, type)
                    }

                    override fun visitFieldInsn(opcode: Int, owner: String, fName: String, fDescriptor: String) {
                        pendingKey = null
                        super.visitFieldInsn(opcode, owner, fName, fDescriptor)
                    }

                    override fun visitInvokeDynamicInsn(
                        iName: String,
                        iDescriptor: String,
                        handle: Handle,
                        vararg args: Any?,
                    ) {
                        pendingKey = null
                        super.visitInvokeDynamicInsn(iName, iDescriptor, handle, *args)
                    }

                    override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
                        pendingKey = null
                        super.visitJumpInsn(opcode, label)
                    }

                    override fun visitLabel(label: org.objectweb.asm.Label) {
                        pendingKey = null
                        super.visitLabel(label)
                    }

                    override fun visitIincInsn(varIndex: Int, increment: Int) {
                        pendingKey = null
                        super.visitIincInsn(varIndex, increment)
                    }

                    override fun visitTableSwitchInsn(
                        min: Int,
                        max: Int,
                        dflt: org.objectweb.asm.Label,
                        vararg labels: org.objectweb.asm.Label,
                    ) {
                        pendingKey = null
                        super.visitTableSwitchInsn(min, max, dflt, *labels)
                    }

                    override fun visitLookupSwitchInsn(
                        dflt: org.objectweb.asm.Label,
                        switchKeys: IntArray?,
                        labels: Array<out org.objectweb.asm.Label>?,
                    ) {
                        pendingKey = null
                        super.visitLookupSwitchInsn(dflt, switchKeys, labels)
                    }

                    override fun visitMultiANewArrayInsn(arrayDescriptor: String, numDimensions: Int) {
                        pendingKey = null
                        super.visitMultiANewArrayInsn(arrayDescriptor, numDimensions)
                    }
                }
            }
        }, 0)
        return cw.toByteArray()
    }
}

// Implementation class for locateWindowMode — run by ClientPreparer.

internal class LocateWindowModeTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File

    private companion object {
        const val SETWINDOWMODE = 5307
        const val RESOURCE = "net/runelite/mp/windowmode.properties"
        // (int mode, <byte|short|int dummy>) -> void
        val SETTER_DESC = Regex("""\(I[BSI]\)V""")
    }

    private data class Setter(val owner: String, val name: String, val desc: String)

    fun run() {
        val inFile = inputJar
        val outFile = outputJar
        outFile.parentFile.mkdirs()

        val found = HashMap<Setter, MutableSet<Int>>()
        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    if (entry.name.endsWith(".class")) scan(bytes, found)
                    output.putNextEntry(JarEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }

                // A miss or an ambiguity fails the build: a wrong guess would call some
                // other (int, dummy) static on the watch with mode 2.
                require(found.size == 1) {
                    "locateWindowMode: expected one (I<dummy>)V setter behind opcode $SETWINDOWMODE, " +
                        "found ${found.keys}. The handler changed shape this revision — " +
                        "disassemble the methods comparing against $SETWINDOWMODE and re-check."
                }
                val (setter, dummies) = found.entries.single()
                val dummy = dummies.minOrNull()
                    ?: error("locateWindowMode: no constant dummy argument passed to $setter")
                output.putNextEntry(JarEntry(RESOURCE))
                output.write(
                    "owner=${setter.owner}\nname=${setter.name}\ndesc=${setter.desc}\ndummy=$dummy\n".toByteArray()
                )
                output.closeEntry()
                logger.lifecycle("locateWindowMode: window-mode setter is ${setter.owner}.${setter.name}${setter.desc} (dummy $dummy)")
            }
        }
    }

    /** Records `<const dummy>; invokestatic <setter>` pairs in methods that test for 5307. */
    private fun scan(bytes: ByteArray, found: MutableMap<Setter, MutableSet<Int>>) {
        val node = org.objectweb.asm.tree.ClassNode()
        ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        for (method in node.methods) {
            val insns = method.instructions.toArray()
            val handlesOpcode = insns.any {
                it is org.objectweb.asm.tree.IntInsnNode && it.opcode == Opcodes.SIPUSH && it.operand == SETWINDOWMODE
            }
            if (!handlesOpcode) continue
            for ((i, insn) in insns.withIndex()) {
                if (insn !is org.objectweb.asm.tree.MethodInsnNode || insn.opcode != Opcodes.INVOKESTATIC) continue
                if (!SETTER_DESC.matches(insn.desc)) continue
                val dummy = insns.getOrNull(i - 1)?.let { constant(it) } ?: continue
                found.getOrPut(Setter(insn.owner, insn.name, insn.desc)) { sortedSetOf() }.add(dummy)
            }
        }
    }

    private fun constant(insn: org.objectweb.asm.tree.AbstractInsnNode): Int? = when {
        insn is org.objectweb.asm.tree.IntInsnNode && (insn.opcode == Opcodes.BIPUSH || insn.opcode == Opcodes.SIPUSH) -> insn.operand
        insn.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> insn.opcode - Opcodes.ICONST_0
        insn is org.objectweb.asm.tree.LdcInsnNode -> insn.cst as? Int
        else -> null
    }
}

// Implementation classes for desugarStringConcat — run by ClientPreparer.

internal class DesugarStringConcatTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File
    lateinit var helperOwner: String

    fun run() {
        val inFile = inputJar
        val outFile = outputJar
        outFile.parentFile.mkdirs()
        var classesProcessed = 0
        var sitesRewritten = 0
        var classesSkipped = 0
        // runelite-api ships its own canonical OAuthApi; the injected client bundles
        // a duplicate that breaks dex merging. Only OAuthApi conflicts — other
        // com/jagex/oldscape/pub/ types (OtlTokenRequester, etc.) exist ONLY in the
        // injected jar, so don't drop the whole package.
        val skipExact = setOf("com/jagex/oldscape/pub/OAuthApi.class")
        // Direct `ldc` of a dynamic constant (CONSTANT_Dynamic, JVM 11). 1.12.39 is the first
        // revision to emit these outside a makeConcatWithConstants recipe -- String[], boolean[]
        // and Properties[] lookup tables built by ConstantBootstraps.invoke(client.xx()) -- and
        // D8 refuses const-dynamic at every API level. Each distinct constant becomes a holder
        // class whose <clinit> runs the bootstrap once; the ldc becomes a GETSTATIC on it. That
        // keeps the JVM's evaluate-once semantics, which an inline call would not.
        val condys = CondyRegistry("runelite/desugar/Condy")

        // The holder classes sit in their own package, but a dynamic constant's bootstrap target
        // is typically a private static in the obfuscated class -- the JVM resolves a condy with
        // the declaring class's own lookup, so it never needed access. The holder's <clinit> does:
        // first pass finds every target so the second can widen it (and its class) to public.
        // Same treatment the lambda pass gives its implementation methods.
        JarFile(inFile).use { input ->
            val entries = input.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class") || skipExact.contains(entry.name)) continue
                val bytes = input.getInputStream(entry).use { it.readBytes() }
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitLdcInsn(value: Any) {
                                if (value is org.objectweb.asm.ConstantDynamic) {
                                    condys.noteTarget(value)
                                }
                            }
                        }
                    }
                }, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
            }
        }

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    if (skipExact.contains(entry.name)) {
                        classesSkipped++
                        continue
                    }
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name.endsWith(".class")) {
                        classesProcessed++
                        val (rewritten, n) = rewriteClass(bytes, helperOwner, condys)
                        sitesRewritten += n
                        output.write(rewritten)
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
                for ((name, classBytes) in condys.holders()) {
                    output.putNextEntry(JarEntry("$name.class"))
                    output.write(classBytes)
                    output.closeEntry()
                }
            }
        }
        logger.lifecycle("desugarStringConcat: processed $classesProcessed classes, rewrote $sitesRewritten makeConcatWithConstants sites, skipped $classesSkipped (runelite-api duplicates), hoisted ${condys.count()} dynamic constants into holder classes, widened ${condys.targetCount()} bootstrap targets")
    }

    private fun rewriteClass(bytes: ByteArray, helperOwner: String, condys: CondyRegistry): Pair<ByteArray, Int> {
        val cr = ClassReader(bytes)
        // The injected jar references internal obfuscated types like `da` that aren't on
        // ASM's reflection classloader, so the default getCommonSuperClass blows up.
        // Fall back to java/lang/Object — over-broad, but valid.
        val cw = object : ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS) {
            override fun getCommonSuperClass(t1: String, t2: String): String {
                return try { super.getCommonSuperClass(t1, t2) } catch (_: Throwable) { "java/lang/Object" }
            }
        }
        val counter = intArrayOf(0)
        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            var className = ""

            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                className = name
                val widened = if (condys.isTargetOwner(name)) (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC else access
                super.visit(version, widened, name, signature, superName, interfaces)
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val widened = if (name != null && descriptor != null && condys.isTarget(className, name, descriptor))
                    (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC
                else access
                val mv = super.visitMethod(widened, name, descriptor, signature, exceptions)
                return ConcatRewriter(widened, name, descriptor, mv, helperOwner, counter, condys)
            }
        }, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray() to counter[0]
    }
}

// One holder class per distinct dynamic constant:
//
//     public final class runelite/desugar/Condy$N { public static final <desc> VALUE; static { ... } }
//
// <clinit> pushes the bootstrap's trailing arguments and invokes the handle, exactly what
// ConstantBootstraps.invoke does, then casts to the constant's own type. Only the
// ConstantBootstraps.invoke + H_INVOKESTATIC shape is supported; any other bootstrap fails the
// build with the constant named, since an unhandled one would fail D8 anyway.
private class CondyRegistry(private val prefix: String) {
    private val names = LinkedHashMap<org.objectweb.asm.ConstantDynamic, String>()

    // owner/name/desc of every bootstrap target seen in the scan pass; these get widened to public.
    private val targets = HashSet<Triple<String, String, String>>()
    private val targetOwners = HashSet<String>()

    fun noteTarget(c: org.objectweb.asm.ConstantDynamic) {
        val bsm = c.bootstrapMethod
        if (bsm.owner == "java/lang/invoke/ConstantBootstraps" && bsm.name == "invoke" && c.bootstrapMethodArgumentCount >= 1) {
            val h = c.getBootstrapMethodArgument(0) as? Handle ?: return
            targets.add(Triple(h.owner, h.name, h.desc))
            targetOwners.add(h.owner)
        }
    }

    fun isTarget(owner: String, name: String, desc: String): Boolean = targets.contains(Triple(owner, name, desc))
    fun isTargetOwner(owner: String): Boolean = targetOwners.contains(owner)
    fun targetCount(): Int = targets.size

    fun holderFor(c: org.objectweb.asm.ConstantDynamic): String =
        names.getOrPut(c) { "$prefix\$${names.size}" }

    fun count(): Int = names.size

    fun holders(): List<Pair<String, ByteArray>> = names.map { (c, name) -> name to generate(name, c) }

    private fun generate(name: String, c: org.objectweb.asm.ConstantDynamic): ByteArray {
        val bsm = c.bootstrapMethod
        val isInvoke = bsm.owner == "java/lang/invoke/ConstantBootstraps"
            && bsm.name == "invoke"
            && c.bootstrapMethodArgumentCount >= 1
        val implHandle = if (isInvoke) c.getBootstrapMethodArgument(0) as? Handle else null
        if (implHandle == null || implHandle.tag != Opcodes.H_INVOKESTATIC) {
            throw IllegalStateException(
                "desugarStringConcat: unsupported dynamic constant bootstrap ${bsm.owner}.${bsm.name} for ${c.name}:${c.descriptor}"
            )
        }

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "VALUE", c.descriptor, null, null).visitEnd()

        // The 5-arg constructor is protected; an anonymous subclass is how ConcatRewriter reaches it too.
        val clinit = object : GeneratorAdapter(Opcodes.ASM9, cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null), Opcodes.ACC_STATIC, "<clinit>", "()V") {}
        clinit.visitCode()

        val implArgTypes = Type.getArgumentTypes(implHandle.desc)
        for (i in implArgTypes.indices) {
            val argValue = if (i + 1 < c.bootstrapMethodArgumentCount) c.getBootstrapMethodArgument(i + 1) else null
            when (argValue) {
                is Int, is Long, is Float, is Double, is String, is Type, is Handle -> clinit.visitLdcInsn(argValue)
                null -> clinit.visitInsn(Opcodes.ACONST_NULL)
                else -> throw IllegalStateException(
                    "desugarStringConcat: unsupported bootstrap argument ${argValue.javaClass.name} for ${c.name}:${c.descriptor}"
                )
            }
        }
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, implHandle.owner, implHandle.name, implHandle.desc, implHandle.isInterface)

        // Coerce the impl's return to the constant's declared type, the way the JVM does after
        // ConstantBootstraps.invoke returns Object.
        val retType = Type.getReturnType(implHandle.desc)
        val valueType = Type.getType(c.descriptor)
        val retIsRef = retType.sort == Type.OBJECT || retType.sort == Type.ARRAY
        val valueIsRef = valueType.sort == Type.OBJECT || valueType.sort == Type.ARRAY
        when {
            retIsRef && valueIsRef -> if (retType != valueType) clinit.checkCast(valueType)
            retIsRef && !valueIsRef -> clinit.unbox(valueType)
            !retIsRef && valueIsRef -> clinit.box(retType)
            else -> if (retType != valueType) clinit.cast(retType, valueType)
        }

        clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "VALUE", c.descriptor)
        clinit.visitInsn(Opcodes.RETURN)
        clinit.visitMaxs(0, 0)
        clinit.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}

private class ConcatRewriter(
    access: Int,
    name: String?,
    private val descriptor: String?,
    delegate: MethodVisitor,
    private val helperOwner: String,
    private val counter: IntArray,
    private val condys: CondyRegistry,
) : GeneratorAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {

    override fun visitLdcInsn(value: Any) {
        if (value is org.objectweb.asm.ConstantDynamic) {
            val holder = condys.holderFor(value)
            mv.visitFieldInsn(Opcodes.GETSTATIC, holder, "VALUE", value.descriptor)
            return
        }
        super.visitLdcInsn(value)
    }

    // ART's verifier is stricter than the JVM's about narrow integer types. The 1.12.39
    // obfuscator emits opaque branches such as `ldc 2022116277; istore_1; ...; iload_1; ireturn`
    // inside a `byte` method: the JVM is happy to ireturn any int from a byte method, ART rejects
    // the class outright ("register v1 has type IntegerConstant but expected Byte"), and D8 does
    // not narrow constants for it. Putting the declared width back with i2b/i2s/i2c before every
    // narrow return, narrow field store and narrow array store is exactly what javac's own output
    // already looks like, so it changes nothing for real code and makes the junk verify.
    private val narrowReturn: Int = when (Type.getReturnType(descriptor ?: "()V").sort) {
        Type.BYTE -> Opcodes.I2B
        Type.SHORT -> Opcodes.I2S
        Type.CHAR -> Opcodes.I2C
        else -> 0
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.IRETURN -> if (narrowReturn != 0) super.visitInsn(narrowReturn)
            Opcodes.BASTORE -> super.visitInsn(Opcodes.I2B)
            Opcodes.SASTORE -> super.visitInsn(Opcodes.I2S)
            Opcodes.CASTORE -> super.visitInsn(Opcodes.I2C)
        }
        super.visitInsn(opcode)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            when (desc) {
                "B" -> super.visitInsn(Opcodes.I2B)
                "S" -> super.visitInsn(Opcodes.I2S)
                "C" -> super.visitInsn(Opcodes.I2C)
            }
        }
        super.visitFieldInsn(opcode, owner, name, desc)
    }

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any) {
        if (!(bsm.owner == "java/lang/invoke/StringConcatFactory" && bsm.name == "makeConcatWithConstants")) {
            super.visitInvokeDynamicInsn(name, descriptor, bsm, *bsmArgs)
            return
        }
        counter[0]++
        val argTypes = Type.getArgumentTypes(descriptor)
        // Save args off the stack into fresh locals (reverse order, since the top of stack is last arg).
        val locals = IntArray(argTypes.size)
        for (i in argTypes.size - 1 downTo 0) {
            val local = newLocal(argTypes[i])
            locals[i] = local
            storeLocal(local, argTypes[i])
        }
        // args[] = new Object[N]
        push(argTypes.size)
        newArray(Type.getObjectType("java/lang/Object"))
        for (i in argTypes.indices) {
            dup()
            push(i)
            loadLocal(locals[i], argTypes[i])
            box(argTypes[i])
            arrayStore(Type.getObjectType("java/lang/Object"))
        }
        val argsLocal = newLocal(Type.getObjectType("[Ljava/lang/Object;"))
        storeLocal(argsLocal, Type.getObjectType("[Ljava/lang/Object;"))

        // push recipe
        val recipe = bsmArgs[0] as String
        push(recipe)

        // push args[]
        loadLocal(argsLocal, Type.getObjectType("[Ljava/lang/Object;"))

        // consts[] = new Object[bsmArgs.length - 1]
        val constsLen = bsmArgs.size - 1
        push(constsLen)
        newArray(Type.getObjectType("java/lang/Object"))
        for (i in 0 until constsLen) {
            dup()
            push(i)
            pushConst(bsmArgs[i + 1])
            arrayStore(Type.getObjectType("java/lang/Object"))
        }

        // IndyConcat.concat(recipe, args, consts)
        invokeStatic(
            Type.getObjectType(helperOwner),
            org.objectweb.asm.commons.Method(
                "concat",
                "(Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/String;"
            )
        )
    }

    private fun resolveCondy(c: org.objectweb.asm.ConstantDynamic) {
        val bsm = c.bootstrapMethod
        val isInvoke = bsm.owner == "java/lang/invoke/ConstantBootstraps"
            && bsm.name == "invoke"
            && c.bootstrapMethodArgumentCount >= 1
        val implHandle = if (isInvoke) c.getBootstrapMethodArgument(0) as? Handle else null
        if (implHandle == null || implHandle.tag != Opcodes.H_INVOKESTATIC) {
            // Unknown CONDY shape — fall back to a safe placeholder. Strip NULs so the
            // result can't trip xi.cq's NUL-rejection downstream.
            push(c.toString().replace(' ', '_'))
            return
        }
        // Push the trailing bsm args (positions 1..N) as primitive constants matching
        // the impl method's argument types.
        val implArgTypes = Type.getArgumentTypes(implHandle.desc)
        for (i in implArgTypes.indices) {
            val argValue = if (i + 1 < c.bootstrapMethodArgumentCount) c.getBootstrapMethodArgument(i + 1) else null
            when (argValue) {
                is Int -> mv.visitLdcInsn(argValue)
                is Long -> mv.visitLdcInsn(argValue)
                is Float -> mv.visitLdcInsn(argValue)
                is Double -> mv.visitLdcInsn(argValue)
                is String -> mv.visitLdcInsn(argValue)
                is Type -> mv.visitLdcInsn(argValue)
                is Handle -> mv.visitLdcInsn(argValue)
                null -> mv.visitInsn(Opcodes.ACONST_NULL)
                else -> mv.visitLdcInsn(argValue.toString())
            }
        }
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            implHandle.owner,
            implHandle.name,
            implHandle.desc,
            implHandle.isInterface,
        )
        // The static call leaves the impl's return value on the stack. For the consts[]
        // array store we need an Object reference — box primitives if needed.
        val retType = Type.getReturnType(implHandle.desc)
        if (retType.sort != Type.OBJECT && retType.sort != Type.ARRAY && retType.sort != Type.VOID) {
            box(retType)
        } else if (retType.sort == Type.VOID) {
            // Shouldn't happen for a constant resolver, but keep the stack sane.
            mv.visitInsn(Opcodes.ACONST_NULL)
        }
    }

    private fun pushConst(c: Any?) {
        when (c) {
            is String -> push(c)
            is Int -> { push(c); box(Type.INT_TYPE) }
            is Long -> { push(c); box(Type.LONG_TYPE) }
            is Float -> { push(c); box(Type.FLOAT_TYPE) }
            is Double -> { push(c); box(Type.DOUBLE_TYPE) }
            is Type -> push(c)            // class literal — LDC Class
            is Handle -> push(c)          // method handle  — LDC MethodHandle
            // ConstantDynamic (Java 11+ CONDY). The patched OSRS client uses these as
            // makeConcatWithConstants bootstrap constants — the JVM resolves them at
            // first use by invoking the bootstrap (typically ConstantBootstraps.invoke
            // calling some `client.kt(J)String` lazy initializer). D8 rejects CONDY in
            // dex output (UnsupportedFeature(const-dynamic)) even at minSdk=26, so we
            // can't just emit LDC ConstantDynamic. Without this resolution path the
            // default `push(c.toString())` branch would run, baking ASM's debug repr
            // (which contains a NUL char from the CONDY's name) into a string literal;
            // that NUL then leaks through to xi.cq during login machine-info
            // serialization and the server replies "RuneScape has been updated".
            //
            // Resolution strategy: when the CONDY's bootstrap is
            // ConstantBootstraps.invoke + an H_INVOKESTATIC handle (the only shape we've
            // observed), emit the equivalent direct invocation inline. The JVM will run
            // it every time the consts[] array is built; for makeConcatWithConstants
            // that's once per call site which is the same as desktop's CONDY caching
            // semantics for purposes of the produced string.
            is org.objectweb.asm.ConstantDynamic -> resolveCondy(c)
            null -> visitInsn(Opcodes.ACONST_NULL)
            else -> push(c.toString())    // give up — best effort literal
        }
    }
}

// -- DesugarLambdasTask -----------------------------------------------------------------
// One pass over the input jar; every invokedynamic targeting LambdaMetafactory.metafactory
// becomes a `new SyntheticLambdaN(...)` + invokespecial, where SyntheticLambdaN is a
// generated class implementing the functional interface and delegating its SAM call to
// the original implMethod with the captured args prepended.
//
// Only handles the standard `metafactory` (3 BSM args). `altMetafactory` would need the
// flag-bits decoded; we haven't seen it in the patched client.
internal class DesugarLambdasTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File
    lateinit var syntheticPrefix: String

    fun run() {
        val inFile = inputJar
        val outFile = outputJar
        outFile.parentFile.mkdirs()

        // First pass: collect every implMethod we'll need to invoke from synthetic classes
        // so we can widen its access if it's private (cross-class private calls fail at
        // verify time). Also identify methods matching the xi.cq IAE-on-NUL pattern so
        // we can prepend a JdkStrings.stripNul prologue and stop login from crashing
        // with "error_game_crash" when a machine-info string picks up a NUL.
        val privatesToWiden = mutableSetOf<Triple<String, String, String>>()
        // Generated lambdas live in another package. Public methods alone are not
        // sufficient: their owner and any cast/signature types must be accessible too.
        val classesToWiden = mutableSetOf<String>()
        fun collectType(type: Type) {
            when (type.sort) {
                Type.OBJECT -> classesToWiden.add(type.internalName)
                Type.ARRAY -> collectType(type.elementType)
                Type.METHOD -> {
                    type.argumentTypes.forEach { collectType(it) }
                    collectType(type.returnType)
                }
            }
        }
        val nulRejectMethods = mutableSetOf<Triple<String, String, String>>()
        JarFile(inFile).use { input ->
            val entries = input.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class")) continue
                val bytes = input.getInputStream(entry).use { it.readBytes() }
                var currentOwner = ""
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, ifaces: Array<out String>?) {
                        currentOwner = name
                    }
                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        val methodOwner = currentOwner
                        val methodName = name
                        val methodDesc = descriptor
                        val isStringIntVoid = descriptor == "(Ljava/lang/String;I)V"
                        return object : MethodVisitor(Opcodes.ASM9) {
                            var sawIndexOfChar = false
                            var sawIaeNew = false
                            override fun visitInvokeDynamicInsn(n: String, d: String, bsm: Handle, vararg args: Any) {
                                if (bsm.owner == "java/lang/invoke/LambdaMetafactory" && bsm.name == "metafactory") {
                                    val impl = args[1] as Handle
                                    privatesToWiden.add(Triple(impl.owner, impl.name, impl.desc))
                                    classesToWiden.add(impl.owner)
                                    collectType(Type.getMethodType(impl.desc))
                                    collectType(Type.getMethodType(d))
                                    args.filterIsInstance<Type>().forEach { collectType(it) }
                                }
                            }
                            override fun visitMethodInsn(opc: Int, owner: String, n: String, d: String, itf: Boolean) {
                                if (isStringIntVoid && owner == "java/lang/String" && n == "indexOf" && d == "(I)I") {
                                    sawIndexOfChar = true
                                }
                            }
                            override fun visitTypeInsn(opc: Int, type: String) {
                                if (isStringIntVoid && opc == Opcodes.NEW && type == "java/lang/IllegalArgumentException") {
                                    sawIaeNew = true
                                }
                            }
                            override fun visitEnd() {
                                if (sawIndexOfChar && sawIaeNew && methodName != null && methodDesc != null) {
                                    nulRejectMethods.add(Triple(methodOwner, methodName, methodDesc))
                                }
                            }
                        }
                    }
                }, ClassReader.SKIP_FRAMES)
            }
        }

        var lambdaCount = 0
        val synthetics = mutableListOf<Pair<String, ByteArray>>()

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name.endsWith(".class")) {
                        val (rewritten, newSynthetics) = rewriteClass(bytes, privatesToWiden, classesToWiden, nulRejectMethods, lambdaCount)
                        lambdaCount += newSynthetics.size
                        synthetics.addAll(newSynthetics)
                        output.write(rewritten)
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
                for ((name, classBytes) in synthetics) {
                    output.putNextEntry(JarEntry("$name.class"))
                    output.write(classBytes)
                    output.closeEntry()
                }
            }
        }
        logger.lifecycle("desugarLambdas: produced ${synthetics.size} synthetic lambda classes; widened ${privatesToWiden.size} potential impl methods; patched ${nulRejectMethods.size} NUL-rejecting string writers")
        nulRejectMethods.forEach { (o, n, d) -> logger.lifecycle("  nulPatched: $o.$n$d") }
    }

    private fun rewriteClass(bytes: ByteArray, privatesToWiden: Set<Triple<String, String, String>>, classesToWiden: Set<String>, nulRejectMethods: Set<Triple<String, String, String>>, startCounter: Int): Pair<ByteArray, List<Pair<String, ByteArray>>> {
        val cr = ClassReader(bytes)
        val cw = object : ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS) {
            override fun getCommonSuperClass(t1: String, t2: String): String {
                return try { super.getCommonSuperClass(t1, t2) } catch (_: Throwable) { "java/lang/Object" }
            }
        }
        val syntheticsForThisClass = mutableListOf<Pair<String, ByteArray>>()
        val prefix = syntheticPrefix
        var counter = startCounter
        var ownerName = ""

        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, ifaces: Array<out String>?) {
                ownerName = name
                val effectiveAccess = if (name in classesToWiden)
                    (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC else access
                super.visit(version, effectiveAccess, name, sig, superName, ifaces)
            }

            override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
                val effectiveAccess = if (name in classesToWiden)
                    (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC else access
                super.visitInnerClass(name, outerName, innerName, effectiveAccess)
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                // Widen impl methods to PUBLIC so synthetic lambdas can call them. The
                // synthetic classes live in the runelite/desugar package — a DIFFERENT
                // package from the impl's owner (default package / obfuscated names) — so
                // merely stripping ACC_PRIVATE leaves them package-private, which still
                // fails cross-package access with IllegalAccessError at link time. Forcing
                // public is always verification-safe (it only widens access).
                val effAccess = if (privatesToWiden.any { it.first == ownerName && it.second == name && it.third == descriptor }) {
                    (access and Opcodes.ACC_PRIVATE.inv() and Opcodes.ACC_PROTECTED.inv()) or Opcodes.ACC_PUBLIC
                } else access
                val mv = super.visitMethod(effAccess, name, descriptor, signature, exceptions)
                val rewriter = LambdaRewriter(effAccess, name, descriptor, mv, prefix, ownerName) { sig, implHandle, captureDesc ->
                    val syntheticName = "$prefix$counter"
                    counter++
                    syntheticsForThisClass.add(syntheticName to generateSyntheticLambda(syntheticName, sig, implHandle, captureDesc))
                    syntheticName
                }
                // If this method's body matched the IAE-on-NUL pattern in the first pass,
                // inject `arg1 = JdkStrings.stripNul(arg1)` at method entry. The method
                // never sees a NUL, so the IAE never throws.
                val shouldPatchNul = nulRejectMethods.contains(Triple(ownerName, name, descriptor))
                return if (shouldPatchNul) {
                    object : MethodVisitor(Opcodes.ASM9, rewriter) {
                        override fun visitCode() {
                            super.visitCode()
                            super.visitVarInsn(Opcodes.ALOAD, 1)
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "net/runelite/mp/compat/jdk/JdkStrings",
                                "stripNul",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false,
                            )
                            super.visitVarInsn(Opcodes.ASTORE, 1)
                        }
                    }
                } else {
                    rewriter
                }
            }
        }, ClassReader.EXPAND_FRAMES)

        return cw.toByteArray() to syntheticsForThisClass
    }

    private fun generateSyntheticLambda(
        synName: String,
        spec: LambdaSpec,
        impl: Handle,
        captureDesc: String,
    ): ByteArray = SyntheticLambdas.generate(
        // The generator is Java so tools-src/TestClientLambdaLowering.java runs it on the host;
        // the private-method (H_INVOKESPECIAL) lowering lives there.
        synName, spec.invokedDescriptor, spec.samName, spec.samMethodType, spec.instantiatedMethodType, impl, captureDesc,
    )
}

data class LambdaSpec(
    val invokedDescriptor: String,
    val samName: String,
    val samMethodType: Type,
    val instantiatedMethodType: Type,
)

private class LambdaRewriter(
    access: Int,
    name: String?,
    descriptor: String?,
    delegate: MethodVisitor,
    private val prefix: String,
    private val ownerName: String,
    private val genSynthetic: (LambdaSpec, Handle, String) -> String,
) : GeneratorAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any) {
        if (!(bsm.owner == "java/lang/invoke/LambdaMetafactory" && bsm.name == "metafactory")) {
            super.visitInvokeDynamicInsn(name, descriptor, bsm, *bsmArgs)
            return
        }
        val samMethodType = bsmArgs[0] as Type
        val implMethod = bsmArgs[1] as Handle
        val instantiatedMethodType = bsmArgs[2] as Type
        val spec = LambdaSpec(descriptor, name, samMethodType, instantiatedMethodType)
        val captureDesc = Type.getMethodDescriptor(Type.VOID_TYPE, *Type.getArgumentTypes(descriptor))
        val synName = genSynthetic(spec, implMethod, captureDesc)

        // Save captures to locals (in reverse order so we can reload in original order).
        val captureTypes = Type.getArgumentTypes(descriptor)
        val captureLocals = IntArray(captureTypes.size)
        for (i in captureTypes.indices.reversed()) {
            captureLocals[i] = newLocal(captureTypes[i])
            storeLocal(captureLocals[i], captureTypes[i])
        }

        // NEW + DUP, then reload captures, then INVOKESPECIAL <init>.
        visitTypeInsn(Opcodes.NEW, synName)
        visitInsn(Opcodes.DUP)
        captureTypes.forEachIndexed { i, t -> loadLocal(captureLocals[i], t) }
        val ctorDesc = "(${captureTypes.joinToString("") { it.descriptor }})V"
        visitMethodInsn(Opcodes.INVOKESPECIAL, synName, "<init>", ctorDesc, false)
    }

    // Android's sun.misc.Unsafe is missing the public static constant fields like
    // ARRAY_BYTE_BASE_OFFSET, ARRAY_*_INDEX_SCALE, ADDRESS_SIZE. Rewrite GETSTATIC on
    // those into a read of net/runelite/mp/util/UnsafeShim, which seeds them from a
    // live Unsafe instance at <clinit>.
    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        if (opcode == Opcodes.GETSTATIC && owner == "sun/misc/Unsafe" && UNSAFE_CONSTS.contains(name)) {
            super.visitFieldInsn(Opcodes.GETSTATIC, "net/runelite/mp/util/UnsafeShim", name, descriptor)
            return
        }
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }

    // Reroute every off-heap-style INVOKEVIRTUAL on sun.misc.Unsafe into an INVOKESTATIC
    // on net/runelite/mp/util/UnsafeShim with the same descriptor. The shim emulates
    // off-heap memory in a userland Map<Integer, ByteBuffer> so we never depend on the
    // (Android-restricted) hidden allocateMemory / copyMemoryFromPrimitiveArray APIs.
    // JDK member retargets (ProcessHandle.Info.command() among them) are DesugarApisTask's,
    // through the JdkRetargets table; see android-awt/PORTING.md, section 10.
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (opcode == Opcodes.INVOKEVIRTUAL && owner == "sun/misc/Unsafe"
            && UNSAFE_REDIRECT.contains(name + descriptor)) {
            // Stack on entry: [Unsafe receiver, arg0, arg1, …]
            // We need to pop the receiver but it's underneath every arg. Save args into
            // locals (reverse order so reloads come out in argument order), POP the
            // receiver, reload args, and call the static.
            val argTypes = Type.getArgumentTypes(descriptor)
            val locals = IntArray(argTypes.size)
            for (i in argTypes.indices.reversed()) {
                locals[i] = newLocal(argTypes[i])
                storeLocal(locals[i], argTypes[i])
            }
            super.visitInsn(Opcodes.POP)
            for (i in argTypes.indices) {
                loadLocal(locals[i], argTypes[i])
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/runelite/mp/util/UnsafeShim",
                name, descriptor, false)
            return
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    companion object {
        private val UNSAFE_CONSTS: Set<String> = setOf(
            "ADDRESS_SIZE", "INVALID_FIELD_OFFSET",
            "ARRAY_BOOLEAN_BASE_OFFSET", "ARRAY_BYTE_BASE_OFFSET", "ARRAY_SHORT_BASE_OFFSET",
            "ARRAY_CHAR_BASE_OFFSET", "ARRAY_INT_BASE_OFFSET", "ARRAY_LONG_BASE_OFFSET",
            "ARRAY_FLOAT_BASE_OFFSET", "ARRAY_DOUBLE_BASE_OFFSET", "ARRAY_OBJECT_BASE_OFFSET",
            "ARRAY_BOOLEAN_INDEX_SCALE", "ARRAY_BYTE_INDEX_SCALE", "ARRAY_SHORT_INDEX_SCALE",
            "ARRAY_CHAR_INDEX_SCALE", "ARRAY_INT_INDEX_SCALE", "ARRAY_LONG_INDEX_SCALE",
            "ARRAY_FLOAT_INDEX_SCALE", "ARRAY_DOUBLE_INDEX_SCALE", "ARRAY_OBJECT_INDEX_SCALE",
        )

        // (name + descriptor) pairs we redirect from Unsafe to UnsafeShim. Off-heap-only
        // variants are listed; the heap-array INVOKEVIRTUAL forms (putByte(Object,J,B) etc)
        // go through Android's native Unsafe untouched since those aren't restricted.
        private val UNSAFE_REDIRECT: Set<String> = setOf(
            "allocateMemory" + "(J)J",
            "reallocateMemory" + "(JJ)J",
            "freeMemory" + "(J)V",
            "setMemory" + "(JJB)V",
            "setMemory" + "(Ljava/lang/Object;JJB)V",
            "copyMemory" + "(JJJ)V",
            "copyMemory" + "(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
            "getByte" + "(J)B",   "putByte"   + "(JB)V",
            "getShort" + "(J)S",  "putShort"  + "(JS)V",
            "getChar" + "(J)C",   "putChar"   + "(JC)V",
            "getInt" + "(J)I",    "putInt"    + "(JI)V",
            "getLong" + "(J)J",   "putLong"   + "(JJ)V",
            "getFloat" + "(J)F",  "putFloat"  + "(JF)V",
            "getDouble" + "(J)D", "putDouble" + "(JD)V",
        )
    }
}

// desugarApis: moves the client off the JDK members Android lacks at minSdk through the
// shared JdkRetargets table (the same pass PluginDexer runs over hub jars, minus the regex
// relocation: the client hands JDK regex types to runelite-api). Runs after desugarLambdas,
// so the calls the generated lambda classes make are retargeted too. Run by ClientPreparer.
internal class DesugarApisTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File

    fun run() {
        val inFile = inputJar
        val outFile = outputJar
        JarFile(inFile).use { srcJar ->
            val jarClasses = srcJar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class") }.toSet()
            JarOutputStream(outFile.outputStream().buffered()).use { outJar ->
                val entries = srcJar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    srcJar.getInputStream(entry).use { stream ->
                        outJar.putNextEntry(JarEntry(entry.name))
                        if (entry.name.endsWith(".class")) {
                            // SKIP_DEBUG drops the malformed debug tables that made D8 throw
                            // NullPointerExceptions, so every class is rewritten.
                            outJar.write(JdkRetargeter.rewrite(entry.name, stream.readBytes(), jarClasses, false,
                                ClassReader.SKIP_DEBUG))
                        } else {
                            stream.copyTo(outJar)
                        }
                    }
                }
            }
        }
    }
}
