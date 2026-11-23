// fixLockVerification: makes methods that use `synchronized` blocks pass ART's structured-locking
// verifier. ART refuses to compile (AOT and JIT) any method whose monitors it cannot prove are
// released on every exit, so such methods stay in the interpreter ("failed lock verification and
// will run slower"). The OSRS obfuscator plants opaque-predicate dead code (`if (static > k)
// return;`) inside synchronized regions; HotSpot ignores that, ART's verifier rejects it.
//
// This transform adds `aload lock; monitorexit` in front of every return / uncaught athrow that
// is reachable while a lock is held: exactly what javac emits for a `return` inside a synchronized
// block. It is generic (no obfuscated names, only monitorenter/monitorexit shapes) and only
// touches methods containing monitorenter. Run by ClientPreparer, after DesugarApisTask.
@file:Suppress("NAME_SHADOWING")

package net.runelite.mp.client

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

internal class FixLockVerificationTask {
    lateinit var inputJar: java.io.File
    lateinit var outputJar: java.io.File
    var fixedMethods = 0
    var fixedExits = 0
    var skippedMethods = 0

    fun run() {
        JarFile(inputJar).use { srcJar ->
            JarOutputStream(outputJar.outputStream().buffered()).use { outJar ->
                val entries = srcJar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    srcJar.getInputStream(entry).use { stream ->
                        outJar.putNextEntry(JarEntry(entry.name))
                        if (entry.name.endsWith(".class")) outJar.write(transformClass(stream.readBytes()))
                        else stream.copyTo(outJar)
                        outJar.closeEntry()
                    }
                }
            }
        }
        android.util.Log.i("ClientPreparer", "fixLockVerification: $fixedExits exit(s) in $fixedMethods method(s), skipped $skippedMethods")
    }

    private fun transformClass(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        // Cheap pre-scan: most classes never synchronize; leave their bytes untouched.
        var hasMonitor = false
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (hasMonitor) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(opcode: Int) { if (opcode == Opcodes.MONITORENTER) hasMonitor = true }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        if (!hasMonitor) return bytes

        val node = ClassNode()
        reader.accept(node, ClassReader.SKIP_DEBUG)
        var changed = false
        for (m in node.methods) {
            if (m.instructions.toArray().none { it.opcode == Opcodes.MONITORENTER }) continue
            when (val r = LockStructure.fix(m)) {
                is LockStructure.Result.Fixed -> { fixedMethods++; fixedExits += r.exits; changed = true }
                is LockStructure.Result.Unchanged -> {}
                is LockStructure.Result.Bail -> {
                    skippedMethods++
                    android.util.Log.w("ClientPreparer", "fixLockVerification: left ${node.name}.${m.name}${m.desc} alone: ${r.reason}")
                }
            }
        }
        if (!changed) return bytes
        // Frames are kept as read: the inserted aload/monitorexit pairs sit in straight-line code
        // and change neither locals nor branch targets. Only max stack needs recomputing.
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        return writer.toByteArray()
    }
}

/**
 * Tracks which `synchronized` locks are held at every instruction of a method and releases them
 * before exits that would leave them held. Locks are recognised in the two shapes javac and the
 * obfuscator produce, `dup; astore N; monitorenter` and `aload N; monitorenter`, and released by
 * `aload N; monitorexit`. Anything else makes the method a no-op (Bail) rather than a guess.
 */
internal object LockStructure {
    sealed class Result {
        object Unchanged : Result()
        class Fixed(val exits: Int) : Result()
        class Bail(val reason: String) : Result()
    }

    private sealed class Analysis {
        class Ok(
            val exits: List<Pair<AbstractInsnNode, List<Int>>>,
            val handlerLocks: Map<LabelNode, List<Int>>,
        ) : Analysis()
        class Err(val reason: String) : Analysis()
    }

    /** What ART's lock verifier would reject (read-only; empty means verifiable). */
    fun problems(m: MethodNode): List<String> = when (val a = analyse(m)) {
        is Analysis.Err -> listOf(a.reason)
        is Analysis.Ok -> a.exits.map { (insn, locks) ->
            "${if (insn.opcode == Opcodes.ATHROW) "athrow" else "return"} at ${m.instructions.indexOf(insn)} leaves lock(s) $locks held"
        }
    }

    fun fix(m: MethodNode): Result {
        val analysis = when (val a = analyse(m)) {
            is Analysis.Err -> return Result.Bail(a.reason)
            is Analysis.Ok -> a
        }
        val exits = analysis.exits
        if (exits.isEmpty()) return Result.Unchanged
        val handlerLocks = analysis.handlerLocks
        val insns = m.instructions
        for ((insn, locks) in exits) {
            val firstInserted = LabelNode()
            insns.insertBefore(insn, firstInserted)
            val afterUnlock = HashMap<Int, LabelNode>(locks.size * 2)
            // Innermost lock first, like nested synchronized blocks unwinding.
            for (n in locks.asReversed()) {
                insns.insertBefore(insn, VarInsnNode(Opcodes.ALOAD, n))
                insns.insertBefore(insn, InsnNode(Opcodes.MONITOREXIT))
                val lbl = LabelNode()
                insns.insertBefore(insn, lbl)
                afterUnlock[n] = lbl
            }
            if (insn.opcode == Opcodes.ATHROW) continue // only uncovered throws are fixed: no handler to adjust
            // The return itself must fall outside every handler range, as in javac output: ART
            // treats a return as able to throw, and a catch-all handler (which releases the lock)
            // reached from after the release would see the lock in two different states.
            // Each nested lock's handler ends right after its own monitorexit so inner handlers do
            // not cover outer unlocks with fewer locks held.
            val after = LabelNode()
            insns.insert(insn, after)
            val ii = insns.indexOf(insn)
            val ai = insns.indexOf(after)
            var k = 0
            while (k < m.tryCatchBlocks.size) {
                val t = m.tryCatchBlocks[k]
                if (insns.indexOf(t.start) <= ii && insns.indexOf(t.end) >= ai) {
                    val hLocks = handlerLocks[t.handler]
                    val splitBefore = if (hLocks == null || hLocks.isEmpty()) {
                        firstInserted
                    } else {
                        var match: LabelNode? = null
                        for (n in locks.asReversed()) {
                            if (n in hLocks) { match = afterUnlock[n]; break }
                        }
                        match ?: firstInserted
                    }
                    val parts = ArrayList<TryCatchBlockNode>(2)
                    if (hasRealInsn(t.start, splitBefore)) parts += TryCatchBlockNode(t.start, splitBefore, t.handler, t.type)
                    if (hasRealInsn(after, t.end)) parts += TryCatchBlockNode(after, t.end, t.handler, t.type)
                    m.tryCatchBlocks.removeAt(k)
                    m.tryCatchBlocks.addAll(k, parts)
                    k += parts.size
                } else k++
            }
        }
        return Result.Fixed(exits.size)
    }

    private fun hasRealInsn(from: LabelNode, to: LabelNode): Boolean {
        var n: AbstractInsnNode? = from
        while (n != null && n !== to) { if (n.opcode >= 0) return true; n = n.next }
        return false
    }

    private fun real(i: AbstractInsnNode?, branchTargets: Set<LabelNode>): AbstractInsnNode? {
        var n = i
        while (n != null && n.opcode < 0) {
            if (n is LabelNode && n in branchTargets) return null
            n = n.previous
        }
        return n
    }

    private fun analyse(m: MethodNode): Analysis {
        val arr = m.instructions.toArray()
        val n = arr.size
        if (n == 0) return Analysis.Ok(emptyList(), emptyMap())
        val index = HashMap<AbstractInsnNode, Int>(n * 2)
        for (i in 0 until n) index[arr[i]] = i
        fun idx(l: LabelNode): Int = index[l] ?: -1

        val branchTargets = HashSet<LabelNode>()
        for (insn in arr) {
            when (insn) {
                is JumpInsnNode -> branchTargets.add(insn.label)
                is TableSwitchInsnNode -> {
                    branchTargets.add(insn.dflt)
                    branchTargets.addAll(insn.labels)
                }
                is LookupSwitchInsnNode -> {
                    branchTargets.add(insn.dflt)
                    branchTargets.addAll(insn.labels)
                }
            }
        }
        for (t in m.tryCatchBlocks) branchTargets.add(t.handler)

        val tcbs = ArrayList<Triple<Int, Int, TryCatchBlockNode>>(m.tryCatchBlocks.size)
        for (t in m.tryCatchBlocks) {
            val st = idx(t.start)
            val en = idx(t.end)
            val hd = idx(t.handler)
            if (st < 0 || en < 0 || hd < 0 || st > en) {
                return Analysis.Err("dangling or invalid try-catch label")
            }
            tcbs.add(Triple(st, en, t))
        }

        val state = arrayOfNulls<List<Int>>(n)
        val work = ArrayDeque<Int>()
        fun push(to: Int, s: List<Int>): String? {
            if (to < 0 || to >= n) return "dangling label target"
            val old = state[to]
            if (old == null) { state[to] = s; work.addLast(to); return null }
            return if (old != s) "inconsistent lock state at insn $to: $old vs $s" else null
        }

        state[0] = emptyList(); work.addLast(0)
        val exits = ArrayList<Pair<AbstractInsnNode, List<Int>>>()
        while (work.isNotEmpty()) {
            val i = work.removeFirst()
            val insn = arr[i]
            val s = state[i]!!
            // A handler starts with the locks held *before* the throwing instruction.
            // Table order matters: the first catch-any covering the insn shadows every later entry
            // (e.g. the outer RuntimeException wrapper never sees exceptions from a locked region).
            if (insn.opcode >= 0) for ((st, en, t) in tcbs) {
                if (i in st until en) {
                    push(idx(t.handler), s)?.let { return Analysis.Err(it) }
                    if (t.type == null) break
                }
            }
            var out = s
            when (insn.opcode) {
                Opcodes.MONITORENTER -> {
                    val prev = real(insn.previous, branchTargets)
                    val lock = when {
                        prev is VarInsnNode && prev.opcode == Opcodes.ASTORE && real(prev.previous, branchTargets)?.opcode == Opcodes.DUP -> prev.`var`
                        prev is VarInsnNode && prev.opcode == Opcodes.ALOAD -> prev.`var`
                        else -> return Analysis.Err("unrecognised monitorenter operand at insn $i")
                    }
                    if (s.contains(lock)) return Analysis.Err("lock local $lock re-entered at insn $i")
                    out = s + lock
                }
                Opcodes.MONITOREXIT -> {
                    val prev = real(insn.previous, branchTargets)
                    if (prev !is VarInsnNode || prev.opcode != Opcodes.ALOAD)
                        return Analysis.Err("unrecognised monitorexit operand at insn $i")
                    if (s.isEmpty() || s.last() != prev.`var`)
                        return Analysis.Err("unbalanced monitorexit of local ${prev.`var`} at insn $i (held $s)")
                    out = s.dropLast(1)
                }
                Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE -> {
                    val v = (insn as VarInsnNode).`var`
                    val wide = insn.opcode == Opcodes.LSTORE || insn.opcode == Opcodes.DSTORE
                    if (s.contains(v) || (wide && s.contains(v + 1)))
                        return Analysis.Err("lock local $v overwritten at insn $i")
                }
                Opcodes.IINC -> if (s.contains((insn as IincInsnNode).`var`)) return Analysis.Err("lock local overwritten at insn $i")
                Opcodes.JSR, Opcodes.RET -> return Analysis.Err("jsr/ret not supported")
            }
            when (insn.opcode) {
                Opcodes.RETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN ->
                    if (s.isNotEmpty()) exits += insn to s
                Opcodes.ATHROW -> if (s.isNotEmpty()) {
                    var catchAny = false
                    var typed = false
                    for ((st, en, t) in tcbs) if (i in st until en) {
                        if (t.type == null) { catchAny = true; break } else typed = true
                    }
                    if (!catchAny) {
                        if (typed) return Analysis.Err("athrow at insn $i under a typed handler while holding $s")
                        exits += insn to s
                    }
                }
                Opcodes.GOTO -> push(idx((insn as JumpInsnNode).label), out)?.let { return Analysis.Err(it) }
                Opcodes.TABLESWITCH -> {
                    val sw = insn as TableSwitchInsnNode
                    for (l in sw.labels + sw.dflt) push(idx(l), out)?.let { return Analysis.Err(it) }
                }
                Opcodes.LOOKUPSWITCH -> {
                    val sw = insn as LookupSwitchInsnNode
                    for (l in sw.labels + sw.dflt) push(idx(l), out)?.let { return Analysis.Err(it) }
                }
                else -> {
                    if (insn is JumpInsnNode) push(idx(insn.label), out)?.let { return Analysis.Err(it) }
                    if (i + 1 < n) push(i + 1, out)?.let { return Analysis.Err(it) }
                }
            }
        }
        val handlerLocks = HashMap<LabelNode, List<Int>>(tcbs.size * 2)
        for ((_, _, t) in tcbs) {
            val hs = state[idx(t.handler)]
            if (hs != null) handlerLocks[t.handler] = hs
        }
        return Analysis.Ok(exits, handlerLocks)
    }
}
