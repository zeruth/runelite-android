# AWT and Swing on Android: changelog

`awt-staging` replaces the desktop `java.awt`, `javax.swing` and JDK packages Android lacks with
`android-awt`, a shadow library ported from the OpenJDK 8 sources, so Plugin Hub plugins that draw,
lay out, listen and play sound through the desktop APIs run unchanged on Android.

The Plugin Hub compatibility scan behind issues #62 to #72 found these gaps as unresolved method
signatures across the 2,806 archived hub JARs. Each section below is one of those issues and what
this branch does about it. The checklists in the issues are ticked by re-running the scan
against this build (`python tools-src/hub-compat-scan.py`, then
`python tools-src/hub-compat-issues.py --issue <n>`); the report committed here predates most of
this work.

## Issues addressed

### #62 Component layout, input and painting

- `Component` and `Container` geometry, hit testing, orientation and printing at JDK parity.
- Input dispatch through the full `process*Event` chain, container and hierarchy events, and
  `AWTEventListener`.
- Focus traversal policies and `KeyboardFocusManager`, `FocusManager`, `KeyboardManager` and
  `InputVerifier`.
- `SwingUtilities` ported whole, including `windowForComponent`.
- `RepaintManager`, `CellRendererPane` and the repaint pipeline. `JComponent` paints through its UI delegate.
- The layout managers (`GridBagLayout`, `GroupLayout`, `SpringLayout`, `BoxLayout`, `OverlayLayout`).
- Borders and `BorderFactory`; `JLabel`, `JPanel`, `JProgressBar`, `JSeparator` and `Box`.
- `SwingWorker` and `Timer`.
- `java.beans`, minus the types `android.jar` already owns.
- `java.awt.im` input methods wired to the Android IME, and the Android bridge for keyboard,
  physical mouse and pointer icons.

### #63 Guava library internals

- The Guava flavour and version the host loads are aligned with what hub plugins compile
  against (`Multiset.forEachEntry`, `Multimap.forEach`). A fresh scan confirms which signatures now link.

### #64 Drawing, geometry, fonts and images

- **Geometry:**
  - `Line2D`, `Rectangle2D`, `Arc2D`, `Ellipse2D` and `AffineTransform` member parity;
  - `CubicCurve2D` and `QuadCurve2D`;
  - `Path2D`, `GeneralPath` and `FlatteningPathIterator`;
  - `Area` over a ported `sun.awt.geom`.
- **Images:**
  - `java.awt.image` complete: `Raster`, `SampleModel`, `ColorModel`, `DataBuffer`, `ConvolveOp`, `LookupOp` and `RescaleOp`;
  - the image producer and consumer pipeline, `MediaTracker`, `VolatileImage`, `BufferStrategy` and the renderable package;
  - `java.awt.color` complete with `ICC_Profile` and `ColorConvertOp`.
- **Fonts:**
  - real `FontMetrics` and `LineMetrics`;
  - `TextAttribute`, `NumericShaper`, the `GraphicAttribute` family and `FontRenderContext`;
  - `Font` family mapping;
  - `GlyphVector` with `Font.createGlyphVector`, `layoutGlyphVector` and `Graphics2D.drawGlyphVector`.
- **javax.imageio:**
  - core, stream, spi, metadata and event packages;
  - PNG, GIF, BMP and WBMP readers and writers ported, JPEG and WebP over the Android codecs (`ImageIO.getImageWritersByFormatName`).

### #65 Dialogs, windows and desktop integration

- `Window`, `Frame` and `Dialog` parity with translucency queries.
- `Toolkit` parity (`getScreenInsets`).
- `Cursor`, `Desktop`, `SystemTray`, `TrayIcon` and `Taskbar` on Android.
- `java.awt.print`, `FileDialog`, `Robot` and `SplashScreen`.
- `JOptionPane` ported with `BasicOptionPaneUI` (`createDialog`).
- `JColorChooser` and `JFileChooser` at full API over the Android pickers.
- `JDesktopPane` and `JInternalFrame` with `JDesktopIcon` and `DefaultDesktopManager`. Frame
  properties fire `InternalFrameEvent` and honour vetoes; iconify, maximize and frame order go
  through the desktop manager.

### #66 Widgets, menus and tables

- `JTable` with `BasicTableUI` and `BasicTableHeaderUI` (`setFillsViewportHeight`).
- `JTree` with `BasicTreeUI`.
- `JSlider`, `JSpinner`, `JSplitPane` and `JTabbedPane` with their Basic UIs (`JTabbedPane.setUI`).
- `JList`, `JComboBox`, `JScrollPane`, `JScrollBar` and `JViewport`.
- Buttons and the `JMenu` family with `MenuSelectionManager`.
- `JToolBar`, `JLayer` and `ProgressMonitor`.
- The heavyweight AWT widgets as lightweight ports.
- `UIManager`, `UIDefaults` and the Basic look and feel's defaults, borders and icons, which the delegates paint through.

### #68 Text editing and HTML (in part)

- The `javax.swing.text` document model: `AbstractDocument` with `GapContent`,
  `DefaultStyledDocument`'s element buffer, `StyleContext` and the attribute sets.
- The formatters behind `JFormattedTextField`.

### #69 Drag-and-drop and data transfer

- `java.awt.datatransfer` complete: the `DataFlavor` grammar, `SystemFlavorMap`, and the image and file flavours.
- `java.awt.dnd` complete with a touch drag gesture.
- `TransferHandler` and `DropMode` (`JList.setDragEnabled`, `setDropMode`).

### #70 Tooltips and accessibility

- `ToolTipManager` and `JToolTip` (`JComponent.createToolTip`, `getToolTipText`).
- `javax.accessibility` with an `AccessibleContext` on every component.
- `java.applet`.

### #71 Other libraries and the Java runtime

- `java.lang.management`, `javax.management` and `com.sun.management` on ART (`ThreadMXBean.dumpAllThreads`).
- `java.net.http` completed with WebSocket (`HttpClient.newWebSocketBuilder`), the builders, body publishers and handlers.
- `ProcessHandle` and the `java.lang` shims.
- A JDK 9-21 API gap audit with a committed baseline, and table-driven retargeting of those members in the on-device dexer.
- `invokedynamic` bootstrap coverage in the client and hub desugar passes.

### #72 Sampled audio (and #2 AWT audio)

- `javax.sound.sampled` completed (`AudioSystem.getMixer`, `getSourceDataLine`).
- Format conversion, the WAVE, AU and AIFF readers and writers, and `javax.sound.sampled.spi`.
- MP3, OGG, AAC and FLAC through `MediaExtractor` and `MediaCodec`.
- `TargetDataLine` capture over `AudioRecord` with the `RECORD_AUDIO` permission flow.
- `javax.sound.midi` with its file reader and writer, the sequencer and the Gervill software synthesizer.

## Tooling

- **Porting:** reference OpenJDK 8u and 17u checkouts, a port-from-OpenJDK helper
  (`android-awt/PORTING.md`), and license headers for ported code.
- **Coverage gate:** `awtSurfaceCheck` gates member coverage against a committed baseline, with a stub convention and `stubReport`.
- **Contract suite:** `tools-src/run-awt-contracts.py` runs every `Test*Contracts` class twice, against the real JDK (`[jdk]`) and against the shadow classes (`[patched]`).
- **Hub compatibility:** the scan, its issue tables and the CI baseline gate (`hub-compat-issues.py --check`).

## Not addressed here

- **#67 native graphics and memory:** the LWJGL and rlawt shim gaps are not started.
- **#74 TOA plugin:** not triaged yet.

## Known open work

- **Text layout (#64):** `TextLayout`, `TextHitInfo` and `LineBreakMeasurer`.
- **Internal frames (#65):** the Basic UIs that paint them (`BasicInternalFrameUI`, the title pane, `BasicDesktopPaneUI`, `BasicDesktopIconUI`).
- **Text editing and HTML (#68):**
  - the `javax.swing.text` View hierarchy and `BasicTextUI` with `DefaultCaret` and the editor kit actions;
  - full API parity for `JTextPane`, `JEditorPane` and `JPasswordField`;
  - the `javax.swing.text.html` port, which retires `HtmlTextRenderer`;
  - RTF.
- **Look and feel:** Metal, Synth, Nimbus, and FlatLaf painting the RuneLite sidebar end to end.
- **The contract suite:** the last GitHub CI run of `run-awt-contracts.py` had 28 failures and 5
  errors. `tools-src/awt-contracts-known-failures.txt` lists those 33 runs, so CI passes over them
  while any new failure still fails the build. A failure in `[jdk]` mode points at the contract or
  its runner; a `[patched]`-only failure is a gap in the shadow classes. The closing gate for the
  AWT set is `run-awt-contracts.py --strict` green in both modes, with the list empty.
