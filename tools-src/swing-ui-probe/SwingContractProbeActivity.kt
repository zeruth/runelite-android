package net.runelite.mp.compatprobe

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import net.runelite.mp.ui.bridge.SwingDialogs
import net.runelite.mp.ui.panels.SwingPanelView
import net.runelite.mp.plugins.PluginDexer
import net.runelite.mp.client.ClientPreparer
import dalvik.system.DexClassLoader
import java.security.MessageDigest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode

/** Diagnostic-only activity. Added through a test source set; never shipped by default. */
class SwingContractProbeActivity : ComponentActivity() {
    private lateinit var dialogs: SwingDialogs
    private lateinit var nativeRoot: LinearLayout
    private lateinit var combo: JComboBox<String>
    private var checks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("user.home", filesDir.absolutePath)
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        dialogs = SwingDialogs(this)
        File(filesDir, "probe-folder").mkdirs()
        File(filesDir, "probe-folder/example.txt").writeText("compatibility probe fixture")
        nativeRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(nativeRoot) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val commands = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("input", "choice", "form", "file", "multi", "directory", "save", "color", "combo", "hide combo", "dialog", "widgets", "graphics", "runtime", "audio", "dex", "client").chunked(5).forEach { row ->
            val bar = LinearLayout(this)
            row.forEach { command -> bar.addView(Button(this).apply { text = command; textSize = 11f; setOnClickListener { runProbe(command) } }, LinearLayout.LayoutParams(0, (48 * resources.displayMetrics.density).toInt(), 1f)) }
            commands.addView(bar)
        }
        nativeRoot.addView(commands)
        setContentView(nativeRoot)
        SwingUtilities.invokeLater {
            try {
                val root = widgets()
                runOnUiThread {
                    nativeRoot.addView(SwingPanelView(this, "contracts", root), LinearLayout.LayoutParams(-1, 0, 1f))
                    Log.i(TAG, "READY checks=$checks")
                    intent.getStringExtra("probe")?.let { command -> nativeRoot.postDelayed({ runProbe(command) }, 700) }
                }
            } catch (error: Throwable) { Log.e(TAG, "SETUP FAILED", error) }
        }
    }

    private fun check(name: String, actual: Boolean) {
        checks++
        if (!actual) throw AssertionError(name)
        Log.i(TAG, "PASS $name")
    }

    private fun runProbe(command: String) {
        val action = Runnable {
            try {
                Log.i(TAG, "BEGIN $command")
                when (command) {
                    "input" -> Log.i(TAG, "RESULT input=" + JOptionPane.showInputDialog(null, "Enter probe text", "Text input", JOptionPane.QUESTION_MESSAGE, null, null, "seed"))
                    "choice" -> Log.i(TAG, "RESULT choice=" + JOptionPane.showInputDialog(null, "Choose a value", "Choice", JOptionPane.QUESTION_MESSAGE, null, arrayOf("Alpha", "Beta", "Gamma"), "Beta"))
                    "form" -> {
                        val name = JTextField("seed", 12)
                        val selected = JCheckBox("Option", true)
                        val content = JPanel(GridLayout(3, 1)).apply { add(JLabel("Edit both fields")); add(name); add(selected) }
                        val answer = JOptionPane.showConfirmDialog(null, content, "Swing form", JOptionPane.OK_CANCEL_OPTION)
                        Log.i(TAG, "RESULT form=$answer text=${name.text} checked=${selected.isSelected}")
                    }
                    "file", "multi", "directory", "save" -> {
                        val chooser = JFileChooser(filesDir).apply {
                            dialogTitle = "Probe $command"
                            isMultiSelectionEnabled = command == "multi"
                            fileSelectionMode = if (command == "directory") JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                        }
                        if (command == "save") chooser.selectedFile = File(filesDir, "compat-export.json")
                        val answer = if (command == "save") chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
                        if (command == "save" && answer == JFileChooser.APPROVE_OPTION)
                            Thread { Thread.sleep(250); chooser.selectedFile.writeText("{\"compat\":true}") }.start()
                        val selected = if (chooser.isMultiSelectionEnabled) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
                        Log.i(TAG, "RESULT $command=$answer count=${selected.size} readable=${selected.all { it.exists() && it.canRead() }}")
                    }
                    "color" -> Log.i(TAG, "RESULT color=" + JColorChooser.showDialog(null, "Choose a color", Color.RED)?.rgb)
                    "dialog" -> {
                        val dialog = JDialog(null as java.awt.Frame?, "Modal Swing dialog", true)
                        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                        dialog.add(JLabel("Close this dialog to finish"))
                        dialog.setSize(400, 200)
                        dialog.isVisible = true
                        check("modal dialog close unblocks caller", !dialog.isVisible)
                        Log.i(TAG, "RESULT dialog passed")
                    }
                    "combo" -> combo.showPopup()
                    "hide combo" -> combo.hidePopup()
                    "keyboard-replay" -> {
                        val output = File(filesDir, "keyboard-replay-result.txt")
                        try {
                            java.awt.EventQueue.invokeAndWait {
                                Class.forName("TestKeyboardSession").getMethod("run", android.content.Context::class.java).invoke(null, this)
                            }
                            val result = Class.forName("TestImeCompositionReplay").getMethod("run", android.content.Context::class.java).invoke(null, this) as String
                            output.writeText("PASS existing keyboard session contracts\n$result")
                            Log.i(TAG, result)
                        } catch (error: Throwable) { output.writeText(error.stackTraceToString()); throw error }
                    }
                    "graphics-benchmark" -> {
                        val output = File(filesDir, "graphics-benchmark-result.txt")
                        try {
                            val result = Class.forName("TestGraphicsFallbackBenchmark").getMethod("run").invoke(null) as String
                            output.writeText(result)
                            Log.i(TAG, result)
                        } catch (error: Throwable) { output.writeText(error.stackTraceToString()); throw error }
                    }
                    "panel-rendering" -> Thread({ PanelRenderingProbe.run(this) }, "Panel-rendering-contracts").start()
                    "graphics" -> {
                        val output = File(filesDir, "graphics-result.txt")
                        try {
                            val gallery = File(filesDir, "graphics-gallery.png")
                            Class.forName("TestGraphicsCompatibility").getMethod("main", Array<String>::class.java).invoke(null, arrayOf(gallery.absolutePath))
                            output.writeText("PASS graphics contracts\n")
                            Log.i(TAG, "RESULT graphics passed")
                        } catch (error: Throwable) { output.writeText(error.stackTraceToString()); throw error }
                    }
                    "runtime", "audio" -> {
                        val className = if (command == "runtime") "TestRuntimeCoreContracts" else "TestAudioLineContracts"
                        val result = File(filesDir, "$command-result.txt")
                        try {
                            Class.forName(className).getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>())
                            result.writeText("PASS $command contracts\n")
                            Log.i(TAG, "RESULT $command passed")
                        } catch (error: Throwable) { result.writeText(error.stackTraceToString()); throw error }
                    }
                    "dex" -> Thread({
                        val result = File(filesDir, "dex-result.txt")
                        try {
                            val raw = File(filesDir, "plugin-http.jar")
                            assets.open("plugin-http.jar").use { input -> raw.outputStream().use { input.copyTo(it) } }
                            val hash = MessageDigest.getInstance("SHA-256").digest(raw.readBytes())
                                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                            val dex = PluginDexer(this).dex("compat-http", "Runtime HTTP fixture", hash, raw)
                            val loader = DexClassLoader(dex.absolutePath, codeCacheDir.absolutePath, null, javaClass.classLoader)
                            val fixture = loader.loadClass("TestRuntimeHttpPlugin")
                            check(fixture.classLoader === loader) { "Fixture was bundled instead of runtime converted" }
                            fixture.getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>())
                            result.writeText("PASS runtime PluginDexer HTTP/Flow contracts\n")
                            Log.i(TAG, "RESULT dex passed")
                        } catch (error: Throwable) { result.writeText(error.stackTraceToString()); Log.e(TAG, "FAILED dex", error) }
                    }, "Runtime-dex-contract").start()
                    "client" -> Thread({
                        val result = File(filesDir, "client-result.txt")
                        val progress = mutableListOf<String>()
                        try {
                            val version = net.runelite.client.RuneLiteProperties.getVersion()
                            ClientPreparer.prepare(this, version) { message ->
                                progress.add(message)
                                Log.i(TAG, "CLIENT $message")
                            }
                            // Construct the applet too: metadata-only checks miss lazily resolved runtime interfaces.
                            val client = Class.forName("client", false, ClientPreparer::class.java.classLoader)
                            check(net.runelite.api.Client::class.java.isAssignableFrom(client)) { "Injected client does not implement the host Client API" }
                            check(net.runelite.api.Client::class.java.isInstance(client.getDeclaredConstructor().newInstance())) { "Client construction failed" }
                            val count = client.declaredMethods.size
                            result.writeText("PASS actual ClientPreparer conversion/linkage/construction version=$version methods=$count\n" + progress.joinToString("\n"))
                            Log.i(TAG, "RESULT client passed")
                        } catch (error: Throwable) { result.writeText(progress.joinToString("\n") + "\n" + error.stackTraceToString()); Log.e(TAG, "FAILED client", error) }
                    }, "Runtime-client-contract").start()
                    "widgets" -> Log.i(TAG, "RESULT widgets list/table/tree actions are logged as they change")
                    else -> throw IllegalArgumentException(command)
                }
            } catch (error: Throwable) { Log.e(TAG, "FAILED $command", error) }
        }
        if (command == "keyboard-replay") runOnUiThread(action)
        else if (command in setOf("graphics", "graphics-benchmark", "runtime", "audio")) Thread(action, "Compat-$command").start()
        else SwingUtilities.invokeLater(action)
    }

    private fun widgets(): JPanel {
        check("ColorUIResource is Color", Color::class.java.isAssignableFrom(javax.swing.plaf.ColorUIResource::class.java))
        check("DimensionUIResource is Dimension", Dimension::class.java.isAssignableFrom(javax.swing.plaf.DimensionUIResource::class.java))
        check("InsetsUIResource is Insets", java.awt.Insets::class.java.isAssignableFrom(javax.swing.plaf.InsetsUIResource::class.java))
        check("checkbox UI has ComponentUI ancestry", javax.swing.plaf.ComponentUI::class.java.isAssignableFrom(javax.swing.plaf.basic.BasicCheckBoxUI::class.java))
        check("radio UI has ComponentUI ancestry", javax.swing.plaf.ComponentUI::class.java.isAssignableFrom(javax.swing.plaf.basic.BasicRadioButtonUI::class.java))
        followupContracts()
        val root = JPanel(BorderLayout())
        val content = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val text = JTextField("abcdef", 16)
        text.select(1, 4)
        check("text selection", text.selectedText == "bcd")
        text.replaceSelection("XY")
        check("text replacement", text.text == "aXYef" && text.caretPosition == 3)
        text.addCaretListener { Log.i(TAG, "EVENT caret ${it.dot}/${it.mark}") }
        content.add(text)
        val formatted = JFormattedTextField(12)
        formatted.text = "37"
        formatted.commitEdit()
        check("formatted value commit", formatted.value == 37)
        content.add(formatted)
        val spinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
        var spinnerEvents = 0
        spinner.addChangeListener { spinnerEvents++ }
        spinner.model = SpinnerNumberModel(4, 0, 10, 1)
        spinner.value = 5
        check("spinner listener survives model replacement", spinnerEvents >= 1)
        content.add(spinner)
        combo = JComboBox(arrayOf("Alpha", "Beta", "Gamma"))
        combo.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) { Log.i(TAG, "EVENT combo visible") }
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) { Log.i(TAG, "EVENT combo hidden") }
            override fun popupMenuCanceled(e: PopupMenuEvent) { Log.i(TAG, "EVENT combo canceled") }
        })
        combo.addActionListener { Log.i(TAG, "EVENT combo selected=${combo.selectedItem}") }
        content.add(combo)
        val list = JList(arrayOf("One", "Two", "Three", "Four"))
        list.selectedIndices = intArrayOf(0, 2)
        check("list multiple selection", list.selectedIndices.contentEquals(intArrayOf(0, 2)))
        list.addListSelectionListener { Log.i(TAG, "EVENT list selection=${list.selectedIndices.joinToString()}") }
        content.add(JScrollPane(list).apply { preferredSize = Dimension(580, 85) })
        val table = JTable(DefaultTableModel(arrayOf(arrayOf<Any>("A", 1), arrayOf<Any>("B", 2)), arrayOf<Any>("Name", "Count")))
        table.setRowHeight(0, 30)
        table.setRowSelectionInterval(1, 1)
        check("table row selection", table.selectedRow == 1)
        check("table variable row geometry", table.getCellRect(1, 0, true).y == 30)
        table.selectionModel.addListSelectionListener { Log.i(TAG, "EVENT table row=${table.selectedRow}") }
        content.add(JScrollPane(table).apply { preferredSize = Dimension(580, 100) })
        val treeRoot = DefaultMutableTreeNode("Root")
        val branch = DefaultMutableTreeNode("Branch")
        branch.add(DefaultMutableTreeNode("Leaf")); treeRoot.add(branch)
        val tree = JTree(treeRoot)
        check("tree root initially expanded", tree.rowCount == 2)
        tree.expandRow(1)
        check("tree expands child rows", tree.rowCount == 3)
        tree.addTreeSelectionListener { Log.i(TAG, "EVENT tree selection=${tree.selectionPath}") }
        content.add(JScrollPane(tree).apply { preferredSize = Dimension(580, 100) })
        val tabs = JTabbedPane()
        val first = JLabel("First content"); val second = JLabel("Second content")
        tabs.addTab("First", first); tabs.addTab("Second", second); tabs.selectedComponent = second
        check("tab selected visibility", !first.isVisible && second.isVisible)
        tabs.removeTabAt(0)
        check("tab removal unmounts old child", first.parent == null && tabs.selectedComponent === second)
        content.add(tabs.apply { preferredSize = Dimension(580, 90) })
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JLabel("Left"), JLabel("Right"))
        split.setSize(400, 50); split.setDividerLocation(.4); split.doLayout()
        check("split mounts children", split.leftComponent.parent === split && split.rightComponent.parent === split)
        check("split lays out both children", split.leftComponent.width > 0 && split.rightComponent.x > split.leftComponent.width)
        content.add(split.apply { preferredSize = Dimension(580, 50) })
        root.add(JScrollPane(content), BorderLayout.CENTER)
        root.setHostShowing(true)
        val game = java.awt.Canvas()
        var gameKeys = 0
        var focusedKeys = 0
        var shortcutCalls = 0
        game.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(event: java.awt.event.KeyEvent) { gameKeys++ }
        })
        text.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(event: java.awt.event.KeyEvent) {
                check("key event uses focused component", event.component === text)
                focusedKeys++
            }
        })
        text.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
            java.awt.event.InputEvent.CTRL_DOWN_MASK), "probeShortcut")
        text.actionMap.put("probeShortcut", object : AbstractAction() {
            override fun actionPerformed(event: java.awt.event.ActionEvent) { shortcutCalls++ }
        })
        check("hosted text accepts focus", text.requestFocusInWindow())
        net.runelite.mp.ui.bridge.KeyDispatch.dispatchAwtEvent(java.awt.event.KeyEvent(game,
            java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_K, 'k'))
        check("focused shortcut consumes once before game", shortcutCalls == 1 && focusedKeys == 1 && gameKeys == 0)
        root.setHostShowing(false)
        return root
    }

    private fun followupContracts() {
        check("panel UI has ComponentUI ancestry", javax.swing.plaf.ComponentUI::class.java.isAssignableFrom(javax.swing.plaf.basic.BasicPanelUI::class.java))
        check("separator UI has ComponentUI ancestry", javax.swing.plaf.ComponentUI::class.java.isAssignableFrom(javax.swing.plaf.basic.BasicSeparatorUI::class.java))
        check("toolbar separator UI has ComponentUI ancestry", javax.swing.plaf.ComponentUI::class.java.isAssignableFrom(javax.swing.plaf.basic.BasicToolBarSeparatorUI::class.java))
        val separator = JSeparator(SwingConstants.VERTICAL)
        separator.setUI(javax.swing.plaf.basic.BasicSeparatorUI())
        check("separator delegate supplies preferred thickness", separator.preferredSize.width == 2)
        val menu = JMenuBar()
        val first = JMenu("First"); val second = JMenu("Second")
        menu.add(first); menu.add(second); menu.setSelected(second)
        check("menu bar retains selected child", menu.isSelected && menu.selectionModel.selectedIndex == 1)
        menu.remove(first)
        check("menu bar adjusts selection after removal", menu.selectionModel.selectedIndex == 0)
        menu.remove(second)
        check("menu bar clears removed selection", !menu.isSelected)

        val tabs = JTabbedPane()
        repeat(7) { tabs.addTab("$it", JLabel("Content $it")) }
        tabs.setSize(150, 100); tabs.selectedIndex = 6; tabs.doLayout()
        val selected = tabs.getBoundsAt(6)
        check("overflow selected tab remains reachable", selected.width > 0 && tabs.indexAtLocation(selected.x + 2, selected.y + 2) == 6)
        press(tabs, 120, 10)
        check("overflow arrow exposes preceding tabs", tabs.getBoundsAt(3).width > 0 && tabs.indexAtLocation(120, 10) == -1)

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JLabel("Left"), JLabel("Right"))
        split.setSize(300, 80); split.dividerLocation = 100; split.isOneTouchExpandable = true; split.doLayout()
        press(split, 102, 12)
        check("split one touch collapses first child", split.dividerLocation == 0 && split.leftComponent.width == 0)
        press(split, 2, 32)
        check("split opposite one touch restores divider", split.dividerLocation == 100)

        val scroll = JScrollPane(JLabel("Content"))
        scroll.setRowHeaderView(JLabel("Rows").apply { preferredSize = Dimension(20, 100) })
        scroll.setColumnHeaderView(JLabel("Columns").apply { preferredSize = Dimension(100, 30) })
        val corners = listOf(ScrollPaneConstants.UPPER_LEFT_CORNER, ScrollPaneConstants.UPPER_RIGHT_CORNER, ScrollPaneConstants.LOWER_LEFT_CORNER, ScrollPaneConstants.LOWER_RIGHT_CORNER)
        corners.forEach { scroll.setCorner(it, JLabel().apply { preferredSize = Dimension(16, 16) }) }
        scroll.setSize(200, 150); scroll.doLayout()
        check("scroll pane lays out all four corner slots", corners.all { scroll.getCorner(it).width > 0 && scroll.getCorner(it).height > 0 })
        check("scroll corners remain outside viewport", !scroll.getCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER).bounds.intersects(scroll.viewport.bounds))

        val model = object : DefaultTableModel(arrayOf(arrayOf<Any>("A", 1), arrayOf<Any>("Z", 2)), arrayOf<Any>("Name", "Count")) {
            override fun getColumnClass(column: Int): Class<*> = if (column == 1) Integer::class.java else String::class.java
        }
        val table = JTable(model)
        val sorter = javax.swing.table.TableRowSorter<DefaultTableModel>(model)
        table.rowSorter = sorter; table.setRowSelectionInterval(0, 0)
        table.setRowHeight(0, 31)
        sorter.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.DESCENDING))
        check("table selection follows model row through sort", table.selectedRow == 1 && table.getValueAt(table.selectedRow, 0) == "A")
        check("table row height follows model row through sort", table.getRowHeight(1) == 31)
        check("typed table editor starts", table.editCellAt(1, 1))
        val field = table.editorComponent as JTextField
        field.text = "invalid"
        check("typed table editor rejects invalid value", !table.cellEditor.stopCellEditing() && table.isEditing)
        field.text = "7"
        sorter.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))
        check("table editor follows sorted model row", table.editingRow == 0)
        check("typed table editor commits", table.cellEditor.stopCellEditing())
        check("table edit commits typed value to original row", model.getValueAt(0, 1) == 7 && model.getValueAt(1, 1) == 2)
        sorter.rowFilter = javax.swing.RowFilter.regexFilter<DefaultTableModel, Int>("Z", 0)
        check("filtered selected row is hidden", table.selectedRow == -1)
        sorter.rowFilter = null
        check("removing filter restores selected model row", table.selectedRow == 0)
        model.insertRow(0, arrayOf<Any>("0", 3))
        check("table insertion shifts selected model identity", table.getValueAt(table.selectedRow, 0) == "A")
    }

    private fun press(component: java.awt.Component, x: Int, y: Int) {
        component.dispatchEvent(java.awt.event.MouseEvent(component, java.awt.event.MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1))
        component.dispatchEvent(java.awt.event.MouseEvent(component, java.awt.event.MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1))
    }

    override fun onDestroy() { dialogs.close(); super.onDestroy() }
    companion object { private const val TAG = "SwingContractProbe" }
}
