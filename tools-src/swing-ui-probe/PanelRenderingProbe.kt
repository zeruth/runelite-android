package net.runelite.mp.compatprobe

import android.app.Activity
import java.awt.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import net.runelite.mp.ui.panels.SwingPanelView

/** Measures the actual Android host, including timer and model-driven repaint requests. */
object PanelRenderingProbe {
    fun run(activity: Activity) {
        val output = File(activity.filesDir, "panel-rendering-result.txt")
        val lines = mutableListOf<String>()
        fun ui(action: () -> Unit) {
            val done = CountDownLatch(1)
            activity.runOnUiThread { try { action() } finally { done.countDown() } }
            check(done.await(5, TimeUnit.SECONDS)) { "Android UI thread timed out" }
        }
        fun edt(action: () -> Unit) = EventQueue.invokeAndWait(action)
        fun awaitPaint(paints: AtomicInteger, previous: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (paints.get() <= previous && System.nanoTime() < deadline) Thread.sleep(30)
            check(paints.get() > previous) { "Repaint was not delivered" }
        }
        fun passed(name: String) { lines.add("PASS $name") }
        val paints = AtomicInteger()
        val layouts = AtomicInteger()
        lateinit var panel: JPanel
        lateinit var label: JLabel
        lateinit var field: JTextField
        lateinit var list: JList<String>
        val model = DefaultListModel<String>()
        var timer: Timer? = null
        try {
            edt {
                panel = object : JPanel() {
                    override fun paintComponent(g: Graphics) { super.paintComponent(g); paints.incrementAndGet() }
                    override fun doLayout() { layouts.incrementAndGet(); super.doLayout() }
                }
                panel.layout = GridLayout(4, 1)
                label = JLabel("seed")
                field = JTextField("seed")
                model.addElement("first")
                list = JList(model)
                panel.add(label); panel.add(field); panel.add(list); panel.add(JProgressBar())
            }
            ui { activity.setContentView(SwingPanelView(activity, "rendering-contracts", panel)) }
            awaitPaint(paints, 0)
            Thread.sleep(400)
            val idlePaints = paints.get(); val idleLayouts = layouts.get()
            Thread.sleep(1200)
            check(paints.get() == idlePaints && layouts.get() == idleLayouts) { "Idle host repainted or laid out: $idlePaints/${paints.get()} $idleLayouts/${layouts.get()}" }
            passed("idle panel performs no repeated paint or layout")
            var before = paints.get()
            edt { label.text = "A longer replacement label" }
            awaitPaint(paints, before); passed("label change redraws and lays out")
            before = paints.get(); val beforeLayout = layouts.get()
            edt { panel.repaint() }
            awaitPaint(paints, before)
            check(layouts.get() == beforeLayout) { "Paint-only request forced layout" }
            passed("repaint redraws without relayout")
            before = paints.get()
            edt { field.document.insertString(0, "document edit ", null) }
            awaitPaint(paints, before); passed("direct document edit redraws")
            before = paints.get()
            edt { model.addElement("second") }
            awaitPaint(paints, before); passed("list model update redraws")
            before = paints.get()
            edt { timer = Timer(150) { panel.repaint() }.apply { start() } }
            awaitPaint(paints, before + 2)
            edt { timer?.stop() }; passed("Swing timer animation keeps repainting")
            ui { activity.setContentView(android.widget.TextView(activity)) }
            Thread.sleep(300)
            before = paints.get()
            edt { panel.repaint() }; Thread.sleep(300)
            check(paints.get() == before) { "Detached panel still rendered" }
            passed("detached host stops rendering")
            lines.add("RESULT passed=7 failed=0")
        } catch (error: Throwable) { lines.add("FAIL ${error.stackTraceToString()}") }
        finally { edt { timer?.stop() }; output.writeText(lines.joinToString("\n")) }
    }
}
