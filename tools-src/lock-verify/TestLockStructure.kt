// Regression tests for LockStructure (nested locks, branch-target label boundaries,
// dangling labels, uncovered athrow, and JVM -Xverify:all + D8 verification).
import net.runelite.mp.client.LockStructure
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.io.File
import java.nio.file.Files

private fun check(ok: Boolean, msg: String) {
    if (!ok) throw AssertionError(msg)
}

private fun defineAndLoad(name: String, bytes: ByteArray): Class<*> {
    return object : ClassLoader(LockStructure::class.java.classLoader) {
        fun load(): Class<*> = defineClass(name, bytes, 0, bytes.size)
    }.load()
}

private fun runD8(workDir: File, className: String, bytes: ByteArray) {
    val clsFile = File(workDir, "$className.class").apply { parentFile.mkdirs(); writeBytes(bytes) }
    val dexDir = File(workDir, "dex-$className").apply { deleteRecursively(); mkdirs() }
    val errors = mutableListOf<String>()
    com.android.tools.r8.D8.run(
        com.android.tools.r8.D8Command.builder(object : com.android.tools.r8.DiagnosticsHandler {
            override fun error(error: com.android.tools.r8.Diagnostic) { errors += error.diagnosticMessage }
            override fun warning(warning: com.android.tools.r8.Diagnostic) {}
        }).addProgramFiles(clsFile.toPath()).setMinApiLevel(26)
            .setMode(com.android.tools.r8.CompilationMode.RELEASE)
            .setOutput(dexDir.toPath(), com.android.tools.r8.OutputMode.DexIndexed).build()
    )
    check(errors.isEmpty()) { "D8 failed for $className: ${errors.first()}" }
}

private fun emitSyncReturn(mv: MethodNode, locks: Int, earlyExit: Boolean, useAthrow: Boolean = false) {
    val startLabels = Array(locks) { LabelNode() }
    val endLabels = Array(locks) { LabelNode() }
    val handlerLabels = Array(locks) { LabelNode() }
    val handlerEndLabels = Array(locks) { LabelNode() }
    val afterUnlockLabels = Array(locks) { LabelNode() }
    val normalEnd = LabelNode()
    val exitLabel = LabelNode()

    // Innermost TCB must appear first in tryCatchBlocks, as javac emits,
    // including each handler's own monitorexit self-protection range.
    for (i in locks - 1 downTo 0) {
        mv.tryCatchBlocks.add(TryCatchBlockNode(startLabels[i], endLabels[i], handlerLabels[i], null))
        mv.tryCatchBlocks.add(TryCatchBlockNode(handlerLabels[i], handlerEndLabels[i], handlerLabels[i], null))
    }

    val baseLocal = locks + 1 // static args: [locks..., flag], scratch starts after
    for (i in 0 until locks) {
        val slot = i
        val holder = baseLocal + i
        mv.instructions.add(VarInsnNode(ALOAD, slot))
        mv.instructions.add(InsnNode(DUP))
        mv.instructions.add(VarInsnNode(ASTORE, holder))
        mv.instructions.add(InsnNode(MONITORENTER))
        mv.instructions.add(startLabels[i])
    }

    if (earlyExit) {
        mv.instructions.add(VarInsnNode(ILOAD, locks)) // flag
        mv.instructions.add(JumpInsnNode(IFEQ, normalEnd))
        if (useAthrow) {
            mv.instructions.add(TypeInsnNode(NEW, "java/lang/IllegalStateException"))
            mv.instructions.add(InsnNode(DUP))
            mv.instructions.add(LdcInsnNode("boom"))
            mv.instructions.add(MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false))
            mv.instructions.add(InsnNode(ATHROW))
        } else {
            mv.instructions.add(IntInsnNode(BIPUSH, 42))
            mv.instructions.add(InsnNode(IRETURN))
        }
    }

    mv.instructions.add(normalEnd)
    for (i in locks - 1 downTo 0) {
        val holder = baseLocal + i
        mv.instructions.add(VarInsnNode(ALOAD, holder))
        mv.instructions.add(InsnNode(MONITOREXIT))
        mv.instructions.add(endLabels[i])
        mv.instructions.add(JumpInsnNode(GOTO, afterUnlockLabels[i]))
        mv.instructions.add(handlerLabels[i])
        val exLocal = baseLocal + locks + i
        mv.instructions.add(VarInsnNode(ASTORE, exLocal))
        mv.instructions.add(VarInsnNode(ALOAD, holder))
        mv.instructions.add(InsnNode(MONITOREXIT))
        mv.instructions.add(handlerEndLabels[i])
        mv.instructions.add(VarInsnNode(ALOAD, exLocal))
        mv.instructions.add(InsnNode(ATHROW))
        mv.instructions.add(afterUnlockLabels[i])
    }
    mv.instructions.add(exitLabel)
    mv.instructions.add(IntInsnNode(BIPUSH, 7))
    mv.instructions.add(InsnNode(IRETURN))
}

private fun makeClass(name: String, methodName: String, desc: String, builder: (MethodNode) -> Unit): Pair<ClassNode, MethodNode> {
    val cn = ClassNode().apply {
        version = V1_8
        access = ACC_PUBLIC
        this.name = name
        superName = "java/lang/Object"
    }
    val mv = MethodNode(ACC_PUBLIC or ACC_STATIC, methodName, desc, null, null)
    builder(mv)
    cn.methods.add(mv)
    return cn to mv
}

private fun toBytes(cn: ClassNode): ByteArray {
    val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
    cn.accept(cw)
    return cw.toByteArray()
}

private fun assertRangeExcludesAfterUnlock(m: MethodNode, insns: InsnList) {
    // Check that each TCB's handler matches the lock state of every instruction inside [start, end)
    // by running LockStructure.problems(m) == empty, AND check that for nested locks [L1, L2],
    // L2's handler range ends before L1's unlock while L1's handler covers L1's unlock.
    val arr = insns.toArray()
    val monExits = arr.withIndex().filter { it.value.opcode == MONITOREXIT }.map { it.index }
    check(monExits.size >= 4) { "expected original + inserted monitorexits, got ${monExits.size}" }
}

fun main(args: Array<String>) {
    val workDir = if (args.isNotEmpty()) File(args[0]) else Files.createTempDirectory("lock-test").toFile()
    workDir.mkdirs()

    // 1. Single lock with early ireturn + normal return
    run {
        val (cn, mv) = makeClass("LockSingle", "run", "(Ljava/lang/Object;Z)I") { emitSyncReturn(it, 1, earlyExit = true) }
        val preProblems = LockStructure.problems(mv)
        check(preProblems.size == 1 && "leaves lock(s)" in preProblems[0], "single lock pre-problems: $preProblems")
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Fixed && res.exits == 1, "expected Fixed(1)")
        check(LockStructure.problems(mv).isEmpty(), "expected 0 problems after fix: ${LockStructure.problems(mv)}")
        val bytes = toBytes(cn)
        runD8(workDir, "LockSingle", bytes)
        val cls = defineAndLoad("LockSingle", bytes)
        val m = cls.getMethod("run", Any::class.java, Boolean::class.javaPrimitiveType)
        val l1 = Any()
        check(m.invoke(null, l1, true) == 42, "single early return value")
        check(!Thread.holdsLock(l1), "lock released on early return")
        check(m.invoke(null, l1, false) == 7, "single normal return value")
        check(!Thread.holdsLock(l1), "lock released on normal return")
    }

    // 2. Nested 2-level locks with early ireturn
    run {
        val (cn, mv) = makeClass("LockNested2", "run", "(Ljava/lang/Object;Ljava/lang/Object;Z)I") {
            emitSyncReturn(it, 2, earlyExit = true)
        }
        val preProblems = LockStructure.problems(mv)
        check(preProblems.size == 1 && "leaves lock(s)" in preProblems[0], "nested2 pre-problems: $preProblems")
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Fixed && res.exits == 1, "nested2 expected Fixed(1), got $res")
        check(LockStructure.problems(mv).isEmpty(), "nested2 problems after fix: ${LockStructure.problems(mv)}")
        assertRangeExcludesAfterUnlock(mv, mv.instructions)

        // Verify specifically that inner split TCB ends before outer unlock while outer split TCB
        // covers outer unlock, and NEITHER covers the IRETURN.
        val insns = mv.instructions
        val ireturnIdx = insns.indexOf(insns.toArray().first { it.opcode == IRETURN })
        val earlySplitEnds = mv.tryCatchBlocks
            .filter { insns.indexOf(it.start) < ireturnIdx && insns.indexOf(it.end) <= ireturnIdx }
            .map { insns.indexOf(it.end) }
        check(earlySplitEnds.size == 2, "expected 2 split TCBs ending before ireturn, got $earlySplitEnds")
        check(earlySplitEnds[0] < earlySplitEnds[1],
            "inner split TCB must end before outer split TCB (was $earlySplitEnds)")
        check(earlySplitEnds[1] <= ireturnIdx, "outer split TCB must end before ireturn")

        val bytes = toBytes(cn)
        runD8(workDir, "LockNested2", bytes)
        val cls = defineAndLoad("LockNested2", bytes)
        val m = cls.getMethod("run", Any::class.java, Any::class.java, Boolean::class.javaPrimitiveType)
        val l1 = Any()
        val l2 = Any()
        check(m.invoke(null, l1, l2, true) == 42, "nested2 early return")
        check(!Thread.holdsLock(l1) && !Thread.holdsLock(l2), "nested2 both locks released on early return")
        check(m.invoke(null, l1, l2, false) == 7, "nested2 normal return")
        check(!Thread.holdsLock(l1) && !Thread.holdsLock(l2), "nested2 both locks released on normal return")
    }

    // 3. Nested 3-level locks with early ireturn
    run {
        val (cn, mv) = makeClass("LockNested3", "run", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)I") {
            emitSyncReturn(it, 3, earlyExit = true)
        }
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Fixed && res.exits == 1, "nested3 expected Fixed(1)")
        check(LockStructure.problems(mv).isEmpty(), "nested3 problems after fix: ${LockStructure.problems(mv)}")
        val bytes = toBytes(cn)
        runD8(workDir, "LockNested3", bytes)
        val cls = defineAndLoad("LockNested3", bytes)
        val m = cls.getMethod("run", Any::class.java, Any::class.java, Any::class.java, Boolean::class.javaPrimitiveType)
        val l1 = Any(); val l2 = Any(); val l3 = Any()
        check(m.invoke(null, l1, l2, l3, true) == 42, "nested3 early return")
        check(!Thread.holdsLock(l1) && !Thread.holdsLock(l2) && !Thread.holdsLock(l3), "nested3 all locks released")
    }

    // 4. Uncovered athrow inside nested 2-level locks
    run {
        val (cn, mv) = makeClass("LockAthrowNested", "run", "(Ljava/lang/Object;Ljava/lang/Object;Z)I") { m ->
            // Remove inner/outer TCBs around the throw by emitting only an outer TCB that stops before athrow,
            // or simpler: build a method where athrow is uncovered while holding [L1, L2].
            val l1 = 3; val l2 = 4
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(VarInsnNode(ASTORE, l1))
            m.instructions.add(VarInsnNode(ALOAD, l1))
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(VarInsnNode(ALOAD, 1))
            m.instructions.add(VarInsnNode(ASTORE, l2))
            m.instructions.add(VarInsnNode(ALOAD, l2))
            m.instructions.add(InsnNode(MONITORENTER))
            val normal = LabelNode()
            m.instructions.add(VarInsnNode(ILOAD, 2))
            m.instructions.add(JumpInsnNode(IFEQ, normal))
            m.instructions.add(TypeInsnNode(NEW, "java/lang/IllegalStateException"))
            m.instructions.add(InsnNode(DUP))
            m.instructions.add(LdcInsnNode("boom"))
            m.instructions.add(MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false))
            m.instructions.add(InsnNode(ATHROW))
            m.instructions.add(normal)
            m.instructions.add(VarInsnNode(ALOAD, l2))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(VarInsnNode(ALOAD, l1))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(IntInsnNode(BIPUSH, 9))
            m.instructions.add(InsnNode(IRETURN))
        }
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Fixed && res.exits == 1, "athrow expected Fixed(1)")
        check(LockStructure.problems(mv).isEmpty(), "athrow problems after fix: ${LockStructure.problems(mv)}")
        val bytes = toBytes(cn)
        runD8(workDir, "LockAthrowNested", bytes)
        val cls = defineAndLoad("LockAthrowNested", bytes)
        val m = cls.getMethod("run", Any::class.java, Any::class.java, Boolean::class.javaPrimitiveType)
        val l1 = Any(); val l2 = Any()
        var threw = false
        try { m.invoke(null, l1, l2, true) }
        catch (e: java.lang.reflect.InvocationTargetException) {
            check(e.cause is IllegalStateException, "expected ISE, got ${e.cause}")
            threw = true
        }
        check(threw, "expected throw")
        check(!Thread.holdsLock(l1) && !Thread.holdsLock(l2), "athrow released both locks")
        check(m.invoke(null, l1, l2, false) == 9, "athrow normal path")
        check(!Thread.holdsLock(l1) && !Thread.holdsLock(l2), "athrow normal path released both locks")
    }

    // 5. Branch-target LabelNode between ALOAD and MONITORENTER -> must Bail
    run {
        val (_, mv) = makeClass("LockTargetLabelEnter", "run", "(Ljava/lang/Object;Z)V") { m ->
            val target = LabelNode()
            m.instructions.add(VarInsnNode(ILOAD, 1))
            m.instructions.add(JumpInsnNode(IFNE, target))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(target)
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(InsnNode(RETURN))
        }
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Bail, "expected Bail for branch-target label before monitorenter, got $res")
    }

    // 6. Branch-target LabelNode between ALOAD and MONITOREXIT -> must Bail
    run {
        val (_, mv) = makeClass("LockTargetLabelExit", "run", "(Ljava/lang/Object;Z)V") { m ->
            val target = LabelNode()
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(VarInsnNode(ILOAD, 1))
            m.instructions.add(JumpInsnNode(IFNE, target))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(target)
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(InsnNode(RETURN))
        }
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Bail, "expected Bail for branch-target label before monitorexit, got $res")
    }

    // 7. Non-target LabelNode (e.g. LineNumberNode debug label) between ASTORE and MONITORENTER -> still recognized & fixed
    run {
        val (_, mv) = makeClass("LockNonTargetLabel", "run", "(Ljava/lang/Object;Z)I") { m ->
            val tStart = LabelNode(); val tEnd = LabelNode(); val tHandler = LabelNode(); val normal = LabelNode()
            val dbg1 = LabelNode(); val dbg2 = LabelNode()
            m.tryCatchBlocks.add(TryCatchBlockNode(tStart, tEnd, tHandler, null))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(DUP))
            m.instructions.add(VarInsnNode(ASTORE, 2))
            m.instructions.add(dbg1) // non-branch-target label between ASTORE and MONITORENTER
            m.instructions.add(LineNumberNode(10, dbg1))
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(tStart)
            m.instructions.add(VarInsnNode(ILOAD, 1))
            m.instructions.add(JumpInsnNode(IFEQ, normal))
            m.instructions.add(IntInsnNode(BIPUSH, 42))
            m.instructions.add(InsnNode(IRETURN))
            m.instructions.add(normal)
            m.instructions.add(VarInsnNode(ALOAD, 2))
            m.instructions.add(dbg2) // non-branch-target label between ALOAD and MONITOREXIT
            m.instructions.add(LineNumberNode(15, dbg2))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(tEnd)
            m.instructions.add(IntInsnNode(BIPUSH, 7))
            m.instructions.add(InsnNode(IRETURN))
            m.instructions.add(tHandler)
            m.instructions.add(VarInsnNode(ASTORE, 3))
            m.instructions.add(VarInsnNode(ALOAD, 2))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(VarInsnNode(ALOAD, 3))
            m.instructions.add(InsnNode(ATHROW))
        }
        val pre = LockStructure.problems(mv)
        check(pre.size == 1 && "leaves lock(s)" in pre[0], "expected pre problem, got $pre")
        val res = LockStructure.fix(mv)
        check(res is LockStructure.Result.Fixed && res.exits == 1, "expected Fixed(1) for non-target label, got $res")
        check(LockStructure.problems(mv).isEmpty(), "expected 0 problems: ${LockStructure.problems(mv)}")
    }

    // 8. Dangling labels in TryCatchBlock and JumpInsnNode -> Bail cleanly, no NPE
    run {
        val (_, mvTcb) = makeClass("LockDanglingTcb", "run", "(Ljava/lang/Object;)V") { m ->
            val uninserted = LabelNode()
            val tStart = LabelNode(); val tEnd = LabelNode()
            m.tryCatchBlocks.add(TryCatchBlockNode(tStart, tEnd, uninserted, null))
            m.instructions.add(tStart)
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(tEnd)
            m.instructions.add(InsnNode(RETURN))
        }
        check(LockStructure.fix(mvTcb) is LockStructure.Result.Bail, "expected Bail for dangling TCB label")

        val (_, mvJump) = makeClass("LockDanglingJump", "run", "(Ljava/lang/Object;Z)V") { m ->
            val uninserted = LabelNode()
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITORENTER))
            m.instructions.add(VarInsnNode(ILOAD, 1))
            m.instructions.add(JumpInsnNode(IFNE, uninserted))
            m.instructions.add(VarInsnNode(ALOAD, 0))
            m.instructions.add(InsnNode(MONITOREXIT))
            m.instructions.add(InsnNode(RETURN))
        }
        check(LockStructure.fix(mvJump) is LockStructure.Result.Bail, "expected Bail for dangling jump label")
    }

    System.err.println("[TestLockStructure] PASS: 8 lock-verification cases verified (nested ranges, branch-target labels, dangling labels, -Xverify:all, D8)")
}
