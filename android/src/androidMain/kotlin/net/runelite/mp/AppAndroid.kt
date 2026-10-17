package net.runelite.mp

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.runelite.mp.ui.AccountManagerOverlay

// Fixed logical height; the width is derived from the device aspect ratio so the AWT
// window fills the viewport without horizontal letterbox. On a 16:9 device this lands
// at the classic 1280×720; on 19.5:9 it widens to ~1560×720.
private const val FRAME_HEIGHT = 720
private const val FRAME_WIDTH_FALLBACK = 1280

// OSRS classic fixed-mode canvas dimensions. We hold the AWT Window at this size
// during boot (splash → title → login screen) so the SW rasterizer never has to redraw
// at an off-default size; the engine treats 765×503 as the most settled config and the
// resize-to-aspect happens cleanly once GLES engages.
private const val FIXED_WIDTH = 765
private const val FIXED_HEIGHT = 503

/**
 * Smallest frame the client will hold still at. ClientPanel pins its minimum size to
 * Constants.GAME_FIXED_SIZE and ClientUI's Layout enforces it — every layout pass calls
 * ContainableFrame.containedSetSize with a preferred size that's at least this wide, so
 * a smaller setSize from us gets undone on the very next validate().
 */
private const val MIN_FRAME_WIDTH = FIXED_WIDTH
private const val MIN_FRAME_HEIGHT = FIXED_HEIGHT

/**
 * AWT frame size for the current viewport: the viewport's aspect ratio at [FRAME_HEIGHT]
 * logical pixels tall, scaled up if that lands under the client's minimum size.
 *
 * The upscale is what makes portrait work. A portrait viewport (the Fold's inner screen,
 * or any phone held upright) derives a width well under 765, which ClientUI's Layout
 * refuses: it resizes the frame back up, we shrink it again on the next frame, and the
 * window size oscillates. The bitmap on screen then belongs to one size while
 * [composeToWindow] maps taps against another, so every touch lands somewhere it
 * shouldn't. Asking for a size the layout accepts keeps the frame still in both
 * orientations.
 */
private fun frameSizeFor(boxSize: IntSize): IntSize {
    if (boxSize.width <= 0 || boxSize.height <= 0) {
        return IntSize(FRAME_WIDTH_FALLBACK, FRAME_HEIGHT)
    }
    val w = (FRAME_HEIGHT.toLong() * boxSize.width / boxSize.height).toInt().coerceAtLeast(1)
    val h = FRAME_HEIGHT
    val upscale = maxOf(
        1f,
        MIN_FRAME_WIDTH / w.toFloat(),
        MIN_FRAME_HEIGHT / h.toFloat(),
    )
    if (upscale <= 1f) return IntSize(w, h)
    return IntSize(
        kotlin.math.ceil(w * upscale).toInt(),
        kotlin.math.ceil(h * upscale).toInt(),
    )
}

@Composable
fun AndroidApp() {
    val context = LocalContext.current.applicationContext
    val launcher = remember { RuneLiteLauncher(context) }
    val scope = rememberCoroutineScope()
    // The client boots straight away, to its own login screen. It used to sit behind the
    // AccountPicker until an account was chosen; that pre-game screen is what Play review
    // rejected as unusable UI. The picker is now the account manager overlay on the login
    // screen (AccountManagerOverlay). Choosing an account there writes a one-shot
    // PendingLaunch and restarts the process; this is where the new process picks it up and
    // seeds the launcher env from it. With nothing pending the env stays unseeded and the
    // login screen is the plain one.
    //
    // remember() keeps a config change (rotation) from launching twice.
    remember {
        // Resolved on the launcher's IO thread: the last-used Jagex account gets its session
        // renewed first, which is network work.
        launcher.beforeSeed = {
            val selection = net.runelite.mp.account.PendingLaunch.resolveForBoot(context)
            launcher.credentials = selection?.credentials
            launcher.accountFile = selection?.credentialsFile
        }
        launcher.launch(scope)
        true
    }

    // Once booted, swallow hardware/gesture back so an accidental press at the edge of
    // the screen can't kill the activity (which would drop a logged-in session). The
    // reducer first closes whatever sidebar/config panel is open; with nothing open it's
    // a true no-op so the user has to explicitly leave through the OS task switcher.
    androidx.activity.compose.BackHandler(enabled = true) {
        net.runelite.mp.ui.WindowImpl.handleBack()
    }

    // Compose chrome wraps the game viewport. Sidebar / config panel render in the
    // right column; the game viewport is the single child of WindowImpl.Window so it
    // fills whatever space the sidebar isn't using.
    net.runelite.mp.ui.WindowImpl.Window {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Input bridge view for IME / SoftKeyboard handling
            AndroidView(
                factory = { ctx -> net.runelite.mp.ui.bridge.KeyboardInputView(ctx) },
                modifier = Modifier.size(1.dp)
            )
            GameViewport(Modifier.fillMaxSize())
            // Login-screen-only account manager, bottom-left, over the game.
            AccountManagerOverlay()
        }
    }
}

/**
 * Polls java.awt.Window.primaryFrame() each frame, copies its BufferedImage int[] backbuffer
 * into a stable Android Bitmap, and renders it. The AWT Window is resized each frame to
 * match the viewport's actual pixel size — that way the BufferedImage backing the canvas
 * is 1:1 with what Compose displays and text / overlays render crisply at native device
 * resolution instead of being bilinear-upscaled from a 1280×720 source.
 */
@Composable
private fun GameViewport(modifier: Modifier = Modifier) {
    var bitmap: Bitmap? by remember { mutableStateOf(null) }
    var imageBitmap: ImageBitmap? by remember { mutableStateOf(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Canvas rect in window-space; null until the first frame after the AWT Canvas attaches
    // to a Window. The Compose SurfaceView host re-maps this through ContentScale.Fit each
    // recomposition to track resizes and the canvas being moved around inside the frame.
    var canvasRect by remember { mutableStateOf<java.awt.Rectangle?>(null) }

    // Mirrors java.awt.Canvas.RENDERED_BY_GLES into Compose state. Flipped by
    // GpuGlesPlugin's startUp/shutDown; we read it inside the per-frame loop and recompose
    // both the SurfaceView block (gated below) and the bitmap recreation (so the alpha
    // channel mode matches the active renderer). Default off so a stale Compose snapshot
    // doesn't render the SurfaceView before the plugin has actually engaged.
    var glesActive by remember { mutableStateOf(false) }
    // Size of the AWT frame that produced the bitmap currently on screen. Everything that
    // maps between Compose pixels and window pixels — the letterbox math in
    // [composeToWindow] and the GLES SurfaceView's placement — has to agree with the
    // bitmap the user is actually looking at, not with whatever the frame happens to
    // measure at the instant a finger lands.
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        var lastChromePixels: IntArray? = null
        var nextChromePaintAt = 0L
        var nextDumpAt = 0L
        var nextSwDiagAt = 0L
        var lastWindow: java.awt.Window? = null
        while (true) {
            withFrameNanos { /* tie sampling to display vsync */ }
            // Sidebar/toolbar suppression now happens at the source via the
            // `runelite.headlessChrome` system property — see RuneLiteLauncher and the
            // HEADLESS_CHROME constant in ClientUI. No per-frame reflection needed.
            val window = java.awt.Window.primaryFrame()
            // Read the GLES toggle up front so it can gate both the target window size
            // (below) and the bitmap-recreation branch (further down). Before GpuGles
            // engages — i.e., during splash, title, login screen — hold the window at
            // OSRS classic fixed dimensions; the SW rasterizer paints a clean baseline
            // canvas, and we only flip to the resize-to-aspect mode once the engine has
            // actually settled in-game.
            val gles = java.awt.Canvas.isRenderedByGles()
            val targetW: Int
            val targetH: Int
            if (gles) {
                val target = frameSizeFor(boxSize)
                targetW = target.width
                targetH = target.height
            } else {
                targetW = FIXED_WIDTH
                targetH = FIXED_HEIGHT
            }
            // Don't force-resize the splash to the full client viewport — it's a
            // small dialog (~322×~360) that paints its children at fixed absolute
            // positions, so stretching its window to 1280×720 just leaves the
            // splash content sitting in the top-left of a gray expanse.
            // ContentScale.Fit on the Compose Image then naturally centers the
            // small bitmap in the viewport. Only force the resize once a real
            // client window (anything not named SplashScreen) becomes primary.
            val isSplash = window != null && window.javaClass.simpleName == "SplashScreen"
            if (!isSplash && window != null && (window.width != targetW || window.height != targetH)) {
                window.setSize(targetW, targetH)
            }
            val now = System.currentTimeMillis()
            if (net.runelite.mp.BuildConfig.DEBUG && window != null && window.width > 0 && (window !== lastWindow || now > nextDumpAt)) {
                android.util.Log.i("TreeDump", "tree (${window.javaClass.simpleName} ${window.width}x${window.height}):\n${window.dumpTree()}")
                lastWindow = window
                nextDumpAt = now + 8_000
            }
            // While a GLES renderer draws the game, this frame only carries the chrome around
            // it (sidebar, panels), and repainting the whole AWT tree every vsync (120 Hz) is
            // the main thread's largest cost. Repaint it at CHROME_GLES_INTERVAL_MS then, and
            // immediately on a renderer toggle. The software renderer repaints every frame.
            val paintChrome = !gles || gles != glesActive || now >= nextChromePaintAt
            if (gles && paintChrome) nextChromePaintAt = now + CHROME_GLES_INTERVAL_MS
            val src = if (!paintChrome) null else window?.renderToBackbuffer()
                ?: java.awt.Canvas.latest()?.backbuffer
            // `gles` was read at the top of the loop. A change forces the bitmap to be
            // recreated below (hasAlpha differs between renderers), and the Compose
            // recomposition then re-evaluates whether to mount the SurfaceView.
            if (gles != glesActive) {
                android.util.Log.i("SwDiag", "renderer toggle: gles=$gles src=${src?.width}x${src?.height} " +
                    "srcNull=${src == null} canvasRect=$canvasRect boxSize=$boxSize " +
                    "renderedByGles=${java.awt.Canvas.isRenderedByGles()}")
                glesActive = gles
                bitmap = null
                // Toggling the renderer doesn't change the AWT component tree, so the
                // per-frame layout yields identical Canvas bounds — canvasRect never
                // changes, the GLES SurfaceView (positioned from canvasRect) never gets a
                // fresh surfaceChanged, and GL stays blank until a manual panel resize
                // forces a layout pass (likewise the SW canvas linkage doesn't refresh).
                // Force that layout pass + a geometry refresh here so the toggle alone
                // makes the active renderer paint.
                window?.let {
                    // invalidateTree(), not invalidate(): java.awt.Component now tracks layout
                    // validity, so a plain invalidate() marks only the window itself and
                    // validate() would short-circuit every child subtree that is still valid —
                    // which is all of them here, since the toggle doesn't change the tree.
                    it.invalidateTree()
                    it.validate()
                }
                canvasRect = java.awt.Canvas.latest()?.boundsInWindow
            }
            if (!gles && src != null && now > nextSwDiagAt) {
                nextSwDiagAt = now + 1000
                val arr = src.backingArray()
                val idx = (src.height / 2) * src.width + src.width / 2
                val px = if (idx in arr.indices) arr[idx] else 0
                val cb = java.awt.Canvas.latest()?.backbuffer
                val cbArr = cb?.backingArray()
                val cbPx = if (cbArr != null && cb.width > 0) {
                    val i = (cb.height / 2) * cb.width + cb.width / 2
                    if (i in cbArr.indices) cbArr[i] else 0
                } else 0
                android.util.Log.i("SwDiag", "sw frame win=${src.width}x${src.height} winCenterPx=0x${Integer.toHexString(px)} " +
                    "canvasBB=${cb?.width}x${cb?.height} canvasCenterPx=0x${Integer.toHexString(cbPx)} " +
                    "renderedByGles=${java.awt.Canvas.isRenderedByGles()}")
            }
            if (src != null && src.width > 0 && src.height > 0) {
                val w = src.width
                val h = src.height
                var bmp = bitmap
                var recreated = false
                if (bmp == null || bmp.width != w || bmp.height != h) {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // OSRS rasterizer writes RGB ints with alpha=0. Telling the Bitmap
                    // "treat all pixels as opaque" lets the renderer skip alpha sampling
                    // entirely — ~10% of frame time on this hot path. Disabled when the
                    // GLES host owns the canvas, because we *need* an honest alpha channel
                    // so the SurfaceView shows through the punched hole.
                    if (!gles) bmp.setHasAlpha(false)
                    bitmap = bmp
                    recreated = true
                }
                val pixels = src.backingArray()
                // While a GLES renderer draws the game, this frame is only the chrome around
                // it (the game area is a transparent hole) and is usually identical to the
                // last one. Skip the bitmap conversion, the recomposition and RenderThread's
                // texture upload — which competes with the game for the GPU — when nothing
                // changed. The software renderer changes every frame, so it isn't compared.
                val prev = lastChromePixels
                val unchanged = gles && !recreated && prev != null && prev.size == pixels.size &&
                    java.util.Arrays.equals(prev, pixels)
                if (!unchanged) {
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                    imageBitmap = bmp.asImageBitmap()
                    lastChromePixels = if (gles) {
                        (if (prev != null && prev.size == pixels.size) prev else IntArray(pixels.size))
                            .also { System.arraycopy(pixels, 0, it, 0, pixels.size) }
                    } else null
                }
                if (frameSize.width != w || frameSize.height != h) {
                    frameSize = IntSize(w, h)
                }
                // Mirrored out of composition so the pointer helpers — plain top-level
                // functions called from gesture coroutines — can read it too.
                RenderedFrame.width = w
                RenderedFrame.height = h
            }
            canvasRect = java.awt.Canvas.latest()?.boundsInWindow
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { boxSize = it }
            // Pointer-tracking: forward every position update (hover OR while finger held
            // down) to MOUSE_MOVED so cursor-tracked UI (tooltips, hover highlights, the
            // OSRS "look at" reticle, world-map cursor coords) keeps tracking the finger
            // through a press → drag. AWT's strict contract is MOVED-only-when-unpressed
            // and DRAGGED-while-pressed, but on touch we have no concept of "hovering
            // while pressed elsewhere," and the reticle goes stale during long drags
            // otherwise. Runs on PointerEventPass.Initial so it observes events even
            // when the gesture machine downstream consumes them.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Skip multi-touch — pinch handles those events and we don't want
                        // the cursor jerking between two finger positions during zoom.
                        if (event.changes.count { it.pressed } > 1) continue
                        val change = event.changes.firstOrNull() ?: continue
                        // A mouse or touchpad is driven 1:1 by MouseBridge below.
                        if (pointerKind(event) != PointerKind.Touch) continue
                        when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter,
                            PointerEventType.Press -> {
                                dispatchMove(change.position, boxSize)
                            }
                        }
                    }
                }
            }
            // Physical mouse or touchpad: forwarded as the exact AWT stream desktop Java
            // produces (real buttons, hover, drags, wheel, enter/exit) instead of going
            // through the touch emulation below — no long-press right-click, no BUTTON2
            // camera drags. Touchpad pinch and two-finger swipe become wheel notches, as a
            // desktop trackpad's zoom and scroll do.
            .pointerInput(Unit) {
                val mouse = MouseBridge()
                val pinch = TouchpadPinch()
                val swipe = TouchpadSwipe()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val kind = pointerKind(event)
                        PointerLog.note(kind, event)
                        if (kind != PointerKind.TouchpadPinch) pinch.reset()
                        if (kind != PointerKind.TouchpadSwipe) swipe.reset()
                        when (kind) {
                            PointerKind.Touch -> continue
                            PointerKind.Mouse -> {
                                val change = event.changes.firstOrNull() ?: continue
                                mouse.handle(event, change.position, change.scrollDelta.y, boxSize)
                            }
                            PointerKind.TouchpadPinch -> pinch.update(event, boxSize)
                            PointerKind.TouchpadSwipe -> swipe.update(event, boxSize)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .pointerInput(Unit) {
                val longPressMs = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Mouse and touchpad presses belong to MouseBridge; awaitEachGesture then
                    // waits for every pointer to come up before looking for the next touch.
                    if (pointerKind(currentEvent) != PointerKind.Touch) return@awaitEachGesture
                    val downId = down.id
                    val downTime = System.currentTimeMillis()
                    var lastPos: Offset = down.position
                    var totalMoved = 0f
                    var session: DragSession? = null
                    var pinch: PinchSession? = null
                    var fired = false  // true once we've emitted tap / long-press / drag-start
                    var sawUp = false

                    // Park the cursor at the down location so the next press is at the right spot.
                    dispatchMove(down.position, boxSize)

                    while (true) {
                        // Until we've committed to a gesture, race the next pointer event
                        // against the long-press deadline. Compose only emits events on touch
                        // updates, so a perfectly still finger would otherwise never trigger
                        // the long-press path. Pinch and drag both count as committed.
                        val ev = if (!fired && pinch == null) {
                            val remaining = longPressMs - (System.currentTimeMillis() - downTime)
                            if (remaining > 0) {
                                withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Main) }
                            } else {
                                null
                            }
                        } else {
                            awaitPointerEvent(PointerEventPass.Main)
                        }

                        if (ev == null) {
                            // Long-press deadline expired without movement → right-click to
                            // open the OSRS menu. Then track finger motion so MOUSE_MOVED
                            // dispatches highlight the hovered option, and on release fire
                            // a left-click at the final position — that's how desktop OSRS
                            // selects a menu entry (right-click → move → left-click), and
                            // the touchscreen has to fold all three into one held gesture.
                            fired = true
                            dispatchClick(lastPos, boxSize, BUTTON3)
                            val openPos = lastPos
                            var menuPos = lastPos
                            var dragged = false
                            while (!sawUp) {
                                val tail = awaitPointerEvent(PointerEventPass.Main)
                                val tc = tail.changes.firstOrNull { it.id == downId } ?: break
                                tc.consume()
                                menuPos = tc.position
                                if ((menuPos - openPos).getDistance() > touchSlop) dragged = true
                                if (tc.changedToUpIgnoreConsumed() || !tc.pressed) {
                                    sawUp = true
                                } else {
                                    // Keep the OSRS cursor in sync so the menu row under the
                                    // finger highlights, and a submenu opens while its parent
                                    // row is hovered — as it does on desktop.
                                    dispatchMove(menuPos, boxSize)
                                }
                            }
                            // Commit only a drag that ends on an option (a root-menu row or
                            // a submenu row). A lift where the menu opened leaves it open —
                            // the finger is on its header then, and a click there closes it —
                            // and a lift in the world cancels without a stray walk/attack.
                            if (dragged && releaseHitsMenuOption(menuPos, boxSize)) {
                                dispatchClick(menuPos, boxSize, BUTTON1)
                            }
                            break
                        }

                        // Two-finger pinch: when a second pointer joins, abandon the
                        // pending tap/long-press and switch to wheel-event dispatch so
                        // the OSRS camera zooms (or the JScrollPane scrolls) under the
                        // finger centroid. Pinch keeps running until either finger lifts.
                        val pressed = ev.changes.filter { it.pressed }
                        if (pinch == null && pressed.size >= 2) {
                            // Cancel any in-progress single-finger drag — releasing the
                            // press first keeps the OSRS click-and-drag camera from
                            // sticking after we transition to pinch.
                            session?.end()
                            session = null
                            fired = true
                            pinch = PinchSession(pressed[0].position, pressed[1].position)
                            pressed.forEach { it.consume() }
                            continue
                        }

                        if (pinch != null) {
                            if (pressed.size >= 2) {
                                pinch!!.update(pressed[0].position, pressed[1].position, boxSize)
                            } else {
                                // Second finger lifted — release the BUTTON2 camera press
                                // now (PinchSession.end is idempotent) so the OSRS camera
                                // doesn't stay stuck mid-drag while the user reorganises
                                // fingers. Remaining single finger sits idle until lift.
                                pinch!!.end()
                            }
                            // When all fingers lift the gesture ends; if only one remains
                            // down we stay in pinch-finished state so the lifting finger's
                            // tap-up isn't re-interpreted as a click.
                            val anyDown = ev.changes.any { it.pressed }
                            ev.changes.forEach { it.consume() }
                            if (!anyDown) break
                            continue
                        }

                        val change = ev.changes.firstOrNull { it.id == downId } ?: break
                        totalMoved += change.positionChange().getDistance()
                        lastPos = change.position

                        if (!fired) {
                            if (totalMoved > touchSlop) {
                                // Begin a drag — pick BUTTON2 inside Canvas (camera) or
                                // BUTTON1 in any other UI surface (scroll/list/slider).
                                fired = true
                                session = beginDrag(down.position, change.position, boxSize)
                                if (session == null) break
                            }
                        } else if (session != null) {
                            session.update(change.position, boxSize)
                        }

                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            change.consume()
                            sawUp = true
                            break
                        }
                        change.consume()
                    }

                    if (pinch != null) {
                        pinch!!.end()
                    } else if (session != null) {
                        session.end()
                    } else if (!fired) {
                        // Quick tap with little movement → left click in normal mode.
                        // When Alt is engaged we're in OverlayRenderer's "managing"
                        // mode and there's no useful left-click action over the
                        // canvas (left-clicks just start an overlay-drag, which is
                        // already covered by the drag path). Make tap a right-click
                        // instead so it hits OverlayRenderer.mousePressed's
                        // `isRightMouseButton` branch and resets the hovered overlay
                        // — the desktop equivalent of "alt-right-click overlay to
                        // reset" without forcing the user to hold a perfectly still
                        // long-press through the touch-slop threshold.
                        val tapButton = if (net.runelite.mp.ui.bridge.ModifierState.altActive.value) BUTTON3 else BUTTON1
                        dispatchClick(down.position, boxSize, tapButton)
                    }
                }
            },
    ) {
        // GLES SurfaceView — sits underneath the chrome image, sized + offset to land
        // exactly where the AWT Canvas would be after the chrome Image's ContentScale.Fit
        // letterbox. Only mounted while GpuGlesPlugin is active; when the plugin is
        // disabled, the AWT canvas paints normally through the (now opaque) chrome image
        // and the SurfaceView's last EGL frame stays hidden.
        val rect = canvasRect
        val density = LocalDensity.current
        if (glesActive && rect != null && boxSize.width > 0 && boxSize.height > 0 &&
            frameSize.width > 0 && frameSize.height > 0
        ) {
            // Use the rendered frame's size, not the size we last *asked* the frame to
            // be: `rect` is expressed in that frame's coordinate space, and the Image
            // below is laid out from that same bitmap. Mixing the two puts the GL
            // surface somewhere the software chrome around it doesn't expect.
            val ww = frameSize.width.toFloat()
            val wh = frameSize.height.toFloat()
            val scale = minOf(boxSize.width / ww, boxSize.height / wh)
            val padX = ((boxSize.width - ww * scale) / 2f).toInt()
            val padY = ((boxSize.height - wh * scale) / 2f).toInt()
            val sx = padX + (rect.x * scale).toInt()
            val sy = padY + (rect.y * scale).toInt()
            val sw = (rect.width * scale).toInt().coerceAtLeast(1)
            val sh = (rect.height * scale).toInt().coerceAtLeast(1)
            val swDp = with(density) { sw.toDp() }
            val shDp = with(density) { sh.toDp() }
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { view ->
                        // Hand the surface over to the GLES host so GpuGlesPlugin can
                        // create its EGL window surface against it. The host caches the
                        // view internally and re-registers callbacks if reattached.
                        net.runelite.client.plugins.gpugles.GlesHost.get().attachSurfaceView(view)
                    }
                },
                modifier = Modifier
                    .offset { IntOffset(sx, sy) }
                    .size(swDp, shDp),
            )
        }
        val img = imageBitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = "RuneLite",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        // Compose splash overlays everything until the AWT splash dismisses. Replaces the
        // tiny shadow JFrame with a full-screen branded splash so the user sees the actual
        // boot stages on a phone-readable canvas.
        ComposeSplash()
    }
}

// RuneLite palette (mirrors ColorScheme.java).
private val BrandOrange = Color(0xFFDC8A00)
private val BrandOrangeBg = Color(0xFF553600)        // BRAND_ORANGE.darker().darker()
private val DarkerGray = Color(0xFF1E1E1E)            // DARKER_GRAY_COLOR

/**
 * Full-screen branded splash that mirrors the AWT [net.runelite.client.ui.SplashScreen]
 * state — same overallProgress / actionText / subActionText / progressText values, just
 * rendered in Compose at native device resolution. Stays up from app launch until the
 * AWT splash has opened AND then closed (`SplashScreen.isOpen()` was true once and is
 * now false). A 30s hard cap prevents getting stuck if the splash never opens at all
 * (eg. RuneLite.main aborted before reaching SplashScreen.init).
 */
@Composable
private fun ComposeSplash() {
    val logo = remember { loadSplashLogo() }
    var visible by remember { mutableStateOf(true) }
    var actionText by remember { mutableStateOf("Loading…") }
    var subActionText by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        var hasBeenOpen = false
        var lastSeen = ""
        while (visible) {
            withFrameNanos { }
            val open = try {
                net.runelite.client.ui.SplashScreen.isOpen()
            } catch (t: Throwable) {
                false
            }
            if (open) {
                hasBeenOpen = true
                actionText = net.runelite.client.ui.SplashScreen.currentActionText() ?: actionText
                subActionText = net.runelite.client.ui.SplashScreen.currentSubActionText() ?: ""
                progressText = net.runelite.client.ui.SplashScreen.currentProgressText()
                progress = net.runelite.client.ui.SplashScreen.currentProgress().toFloat()
                // Stages can fire less than a frame apart, so the on-screen text blinks
                // past some of them. The full progression still goes to logcat, which is
                // where you want it when a boot stalls.
                val combo = if (subActionText.isNotEmpty()) "$actionText — $subActionText" else actionText
                if (combo != lastSeen) {
                    lastSeen = combo
                    android.util.Log.i("Splash",
                        "stage: $combo ${(progress * 100).toInt()}%" +
                            (progressText?.let { " [$it]" } ?: ""))
                }
            }
            // Dismiss once the AWT splash has come and gone, or after a safety timeout.
            if (hasBeenOpen && !open) visible = false
            if (System.currentTimeMillis() - started > 30_000) visible = false
        }
        // Whichever path dismissed us, signal the Compose chrome it's safe to
        // render its sidebar/icon strip now.
        net.runelite.mp.ui.WindowImpl.bootComplete.value = true
    }

    if (!visible) return

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(DarkerGray),
        contentAlignment = Alignment.Center,
    ) {
        // Scale to whatever we're given rather than assuming a tall portrait window. The
        // game runs landscape, where height is the tight axis and a fixed 280.dp logo left
        // no room for the text and bar beneath it — so take a fraction of each axis and
        // use the smaller, which keeps the mark square and always on screen.
        val logoSize = minOf(maxWidth * 0.42f, maxHeight * 0.40f).coerceAtMost(280.dp)
        val gap = minOf(maxHeight * 0.04f, 28.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = "RuneLite",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(logoSize),
                )
                Spacer(Modifier.height(gap))
            }
            androidx.compose.material3.Text(
                text = actionText,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // Only takes space when there is something to say — an empty line here left a
            // visible gap between the action text and the bar.
            if (subActionText.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = subActionText,
                    color = Color(0xFFE0E0E0),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(gap))
            // Rounded orange progress bar, ~half the screen wide. Background is the darker
            // brand-orange shade (matches the AWT splash); foreground fills proportionally.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BrandOrangeBg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(BrandOrange),
                )
                progressText?.let {
                    androidx.compose.material3.Text(
                        text = it,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

/** Loads `runelite_splash.png` from the SplashScreen package on the classpath. Returns
 *  null and logs if decoding fails — the splash composable degrades to text-only. */
private fun loadSplashLogo(): ImageBitmap? {
    return try {
        val cls = Class.forName("net.runelite.client.ui.SplashScreen")
        cls.getResourceAsStream("runelite_splash.png")?.use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: return null
            bmp.asImageBitmap()
        }
    } catch (t: Throwable) {
        android.util.Log.w("ComposeSplash", "failed to load splash logo", t)
        null
    }
}

// ---------------------------------------------------------------------------------------
// Input bridge: Compose pointer events → shadow AWT MouseEvent dispatch.
//
// Three gestures supported:
//   - Tap (down → up with < touchSlop movement, < longPressTimeout): left click (BUTTON1).
//   - Long press (down → hold > longPressTimeout without moving): right click (BUTTON3).
//   - Drag (down → exceeds touchSlop): BUTTON2 (middle, camera) inside the game Canvas,
//     BUTTON1 elsewhere (scrollbars, list items, etc.).
//
// Walk-up dispatch: the patched RS Applet registers its MouseListener on itself, not on
// the inner Canvas. A tap hit-tests to the deepest Canvas which has zero listeners. We
// climb the parent chain until finding a component with mouseListeners, then translate
// the click coords into that component's local space.
// ---------------------------------------------------------------------------------------

/** Which input family a pointer event comes from. Compose's [PointerType] follows the
 *  tool type alone, and Android reports a touchpad's pointer with TOOL_TYPE_FINGER on a
 *  SOURCE_MOUSE event, so a touchpad looked like a touchscreen: no hover, no real
 *  right button, and its gestures fell into the touch emulation. The MotionEvent's
 *  source and (API 34+) classification tell the families apart. */
private enum class PointerKind { Touch, Mouse, TouchpadPinch, TouchpadSwipe }

private fun pointerKind(event: PointerEvent): PointerKind {
    val me = event.motionEvent
        ?: return if (event.changes.any { it.type == PointerType.Mouse }) PointerKind.Mouse else PointerKind.Touch
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        when (me.classification) {
            android.view.MotionEvent.CLASSIFICATION_PINCH -> return PointerKind.TouchpadPinch
            android.view.MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE -> return PointerKind.TouchpadSwipe
        }
    }
    if (me.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) return PointerKind.Mouse
    if (me.pointerCount > 0 && me.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_MOUSE) return PointerKind.Mouse
    return PointerKind.Touch
}

/** Logs each non-touchscreen press, release, scroll and gesture start, so a device's
 *  mouse/touchpad reporting can be checked in logcat (tag MobileInput). */
private object PointerLog {
    private var lastKind: PointerKind? = null

    fun note(kind: PointerKind, event: PointerEvent) {
        val me = event.motionEvent ?: return
        val action = me.actionMasked
        val interesting = kind != lastKind || event.type == PointerEventType.Press ||
            event.type == PointerEventType.Release || event.type == PointerEventType.Scroll
        lastKind = kind
        if (kind == PointerKind.Touch && me.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) return
        if (!interesting) return
        val cls = if (android.os.Build.VERSION.SDK_INT >= 34) me.classification else -1
        android.util.Log.i(
            "MobileInput",
            "pointer kind=$kind type=${event.type} action=${android.view.MotionEvent.actionToString(action)} " +
                "src=0x${Integer.toHexString(me.source)} tool=${me.getToolType(0)} cls=$cls " +
                "buttons=0x${Integer.toHexString(me.buttonState)} pointers=${me.pointerCount}",
        )
    }
}

/** Trackpad pinch → wheel notches, from Android's per-event scale factor. Spreading the
 *  fingers zooms in (wheel up), as pinch does on the touchscreen. */
private class TouchpadPinch {
    private var accumulated = 0f

    fun update(event: PointerEvent, boxSize: IntSize) {
        val me = event.motionEvent ?: return
        val scale = me.getAxisValue(android.view.MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
        if (scale <= 0f) return
        accumulated += -kotlin.math.ln(scale) / LN_SCALE_PER_NOTCH
        val notches = accumulated.toInt()
        if (notches == 0) return
        accumulated -= notches
        dispatchWheel(centroid(event), boxSize, notches)
    }

    fun reset() { accumulated = 0f }

    companion object {
        /** ln(scale) per wheel notch: about 8% spread or squeeze per notch. */
        private const val LN_SCALE_PER_NOTCH = 0.08f
    }
}

/** Trackpad two-finger swipe → wheel notches, as a desktop trackpad scroll does: over the
 *  scene it zooms the camera, over a plugin list it scrolls. Android moves the gesture's
 *  pointer with the fingers; fingers down scrolls up (natural direction), so it zooms in. */
private class TouchpadSwipe {
    private var accumulated = 0f
    private var lastY = Float.NaN

    fun update(event: PointerEvent, boxSize: IntSize) {
        val pos = centroid(event)
        if (event.type == PointerEventType.Press || lastY.isNaN()) {
            lastY = pos.y
            return
        }
        accumulated += -(pos.y - lastY) / SWIPE_PX_PER_NOTCH
        lastY = pos.y
        val notches = accumulated.toInt()
        if (notches != 0) {
            accumulated -= notches
            dispatchWheel(pos, boxSize, notches)
        }
        if (event.changes.none { it.pressed }) reset()
    }

    fun reset() {
        accumulated = 0f
        lastY = Float.NaN
    }

    companion object {
        /** Finger travel per wheel notch, in screen pixels. */
        private const val SWIPE_PX_PER_NOTCH = 40f
    }
}

private fun centroid(event: PointerEvent): Offset {
    val ps = event.changes
    if (ps.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (p in ps) { x += p.position.x; y += p.position.y }
    return Offset(x / ps.size, y / ps.size)
}

/** Chrome repaint interval while a GLES renderer owns the game area (~30 Hz). */
private const val CHROME_GLES_INTERVAL_MS = 33L

private const val BUTTON1 = java.awt.event.MouseEvent.BUTTON1
private const val BUTTON2 = java.awt.event.MouseEvent.BUTTON2
private const val BUTTON3 = java.awt.event.MouseEvent.BUTTON3

/** Dimensions of the AWT frame behind the bitmap currently on screen, published by
 *  [GameViewport]'s render loop. Zero until the first frame has been copied out. */
private object RenderedFrame {
    @Volatile var width = 0
    @Volatile var height = 0
}

/** Maps a Compose offset (Box-local pixels) into the AWT Window's coordinate space,
 *  undoing ContentScale.Fit's letterbox. Returns null when the tap lands outside the
 *  rendered bitmap (in the black letterbox margins).
 *
 *  The letterbox is undone against the size of the frame that produced the *displayed*
 *  bitmap, since that's what ContentScale.Fit laid out against. The live window is only
 *  consulted to rescale the result, which is a no-op in the steady state and keeps a tap
 *  roughly right if a resize lands between the last blit and the touch. */
private fun composeToWindow(
    offset: Offset,
    boxSize: IntSize,
    window: java.awt.Window,
    clamp: Boolean = false,
): Pair<Int, Int>? {
    if (boxSize.width <= 0 || boxSize.height <= 0) return null
    val winW = window.width
    val winH = window.height
    if (winW <= 0 || winH <= 0) return null
    val ww = (if (RenderedFrame.width > 0) RenderedFrame.width else winW).toFloat()
    val wh = (if (RenderedFrame.height > 0) RenderedFrame.height else winH).toFloat()
    val scale = minOf(boxSize.width / ww, boxSize.height / wh)
    if (scale <= 0f) return null
    val drawnW = ww * scale
    val drawnH = wh * scale
    val padX = (boxSize.width - drawnW) / 2f
    val padY = (boxSize.height - drawnH) / 2f
    val x = (((offset.x - padX) / scale) * (winW / ww)).toInt()
    val y = (((offset.y - padY) / scale) * (winH / wh)).toInt()
    // A held mouse button keeps reporting past the window edge, like a desktop drag does.
    if (clamp) return x.coerceIn(0, winW - 1) to y.coerceIn(0, winH - 1)
    if (x < 0 || y < 0 || x >= winW || y >= winH) return null
    return x to y
}

/** Result of hit-testing: the deepest visible Component, plus the (absX, absY) origin
 *  of that component within the window so we can translate later to whichever ancestor
 *  ends up being the dispatch target. */
private data class Hit(val comp: java.awt.Component, val absX: Int, val absY: Int)

private fun hitTest(window: java.awt.Window, winX: Int, winY: Int): Hit? {
    fun walk(c: java.awt.Component, tx: Int, ty: Int): Hit? {
        if (!c.isVisible || c.width <= 0 || c.height <= 0) return null
        val ax = tx + if (c is java.awt.Window) 0 else c.x
        val ay = ty + if (c is java.awt.Window) 0 else c.y
        if (winX < ax || winY < ay || winX >= ax + c.width || winY >= ay + c.height) return null
        var deepest: Hit? = Hit(c, ax, ay)
        if (c is java.awt.Container) {
            // Iterate children in reverse so later-added siblings (drawn on top) win.
            val kids = c.components
            for (i in kids.indices.reversed()) {
                val child = kids[i] ?: continue
                val sub = walk(child, ax, ay)
                if (sub != null) { deepest = sub; break }
            }
        }
        return deepest
    }
    return walk(window, 0, 0)
}

/** Fire MOUSE_MOVED on the topmost component-with-motion-listeners at the given point.
 *  RuneScape (and most AWT input code) caches the last mouse position from MOTION events
 *  and uses it when it sees the press; if we never send a MOVED, the next press lands at
 *  whatever stale coordinate the client last saw — which manifests as the click being
 *  applied to the previous click location, with the press cursor jumping in afterward.
 *  Call this immediately before any MOUSE_PRESSED so the client's tracked cursor is at
 *  the new spot first. */
private fun dispatchMove(offset: Offset, boxSize: IntSize) {
    val window = java.awt.Window.primaryFrame() ?: return
    val (winX, winY) = composeToWindow(offset, boxSize, window) ?: return
    val hit = hitTest(window, winX, winY) ?: return
    val target = walkUpToListener(hit.comp) ?: hit.comp
    val (lx, ly) = localCoords(target, winX, winY)
    fireMouse(target, java.awt.event.MouseEvent.MOUSE_MOVED, lx, ly, java.awt.event.MouseEvent.NOBUTTON, false)
}

/** Walk up from `hit` until we find a component with mouse listeners, then dispatch a
 *  MOVED → PRESSED → RELEASED → CLICKED sequence translated into that component's local
 *  space. Falls back to the hit target if nothing up the chain subscribes. */
private fun dispatchClick(offset: Offset, boxSize: IntSize, button: Int) {
    val window = java.awt.Window.primaryFrame() ?: return
    val (winX, winY) = composeToWindow(offset, boxSize, window) ?: return
    val hit = hitTest(window, winX, winY) ?: return
    val target = walkUpToListener(hit.comp) ?: hit.comp
    val (lx, ly) = localCoords(target, winX, winY)
    fireMouse(target, java.awt.event.MouseEvent.MOUSE_MOVED, lx, ly, java.awt.event.MouseEvent.NOBUTTON, false)
    fireMouse(target, java.awt.event.MouseEvent.MOUSE_PRESSED, lx, ly, button, true)
    fireMouse(target, java.awt.event.MouseEvent.MOUSE_RELEASED, lx, ly, button, false)
    fireMouse(target, java.awt.event.MouseEvent.MOUSE_CLICKED, lx, ly, button, false)
    clickDirectWidget(hit.comp, winX, winY, button)
}

/** Compose-side widget helpers — these aren't reachable via AWT MouseListener so we
 *  call their high-level click APIs directly when the hit lands on them. */
private fun clickDirectWidget(direct: java.awt.Component, winX: Int, winY: Int, button: Int) {
    when (direct) {
        is javax.swing.JTabbedPane -> {
            val (dx, dy) = localCoords(direct, winX, winY)
            val idx = direct.indexAtLocation(dx, dy)
            if (idx >= 0 && button == BUTTON1) {
                direct.selectedIndex = if (direct.selectedIndex == idx) -1 else idx
            }
        }
        is javax.swing.AbstractButton -> if (button == BUTTON1) direct.doClick()
        else -> { /* Mouse event dispatch covers everything else. */ }
    }
}

/** Common contract for "in-progress drag" handlers. The gesture state machine just
 *  feeds Compose pointer updates here; concrete implementations decide whether to
 *  dispatch MOUSE_DRAGGED, MOUSE_WHEEL, or something else. */
private interface DragSession {
    fun update(pos: Offset, boxSize: IntSize)
    fun end()
}

/** Live drag session — sends MOUSE_PRESSED to start, MOUSE_DRAGGED on each move, and
 *  MOUSE_RELEASED on end. button is BUTTON2 inside the game Canvas (camera-look) and
 *  BUTTON1 elsewhere (scrollbar, list, etc.). 1:1 position mapping in all cases so
 *  the dispatched drag coords stay where the finger actually is — earlier we amplified
 *  canvas-drag deltas 2.5× to make camera rotation feel faster, but that desyncs the
 *  client's tracked cursor from the real finger position and the OSRS tooltip ends up
 *  pointing somewhere off-screen. Native pace keeps tooltip / look-at sane. */
private class MouseDragSession(
    val target: java.awt.Component,
    val originAbsX: Int,
    val originAbsY: Int,
    val button: Int,
    originWinX: Int,
    originWinY: Int,
) : DragSession {
    private var lastX = originWinX - originAbsX
    private var lastY = originWinY - originAbsY

    fun start() {
        // MOVED to park the cursor at the press point, then the actual PRESSED.
        fireMouse(target, java.awt.event.MouseEvent.MOUSE_MOVED, lastX, lastY, java.awt.event.MouseEvent.NOBUTTON, false)
        fireMouse(target, java.awt.event.MouseEvent.MOUSE_PRESSED, lastX, lastY, button, true)
    }

    override fun update(pos: Offset, boxSize: IntSize) {
        val window = java.awt.Window.primaryFrame() ?: return
        val (wx, wy) = composeToWindow(pos, boxSize, window) ?: return
        lastX = wx - originAbsX
        lastY = wy - originAbsY
        fireMouse(target, java.awt.event.MouseEvent.MOUSE_DRAGGED, lastX, lastY, button, true)
    }

    override fun end() {
        fireMouse(target, java.awt.event.MouseEvent.MOUSE_RELEASED, lastX, lastY, button, false)
    }
}

/** Translates a finger drag inside a JScrollPane into MOUSE_WHEEL events targeted at
 *  the scrollpane — same code path real Swing takes when the user wheels. Accumulates
 *  drag deltas until a notch threshold is crossed, then dispatches one wheel-rotation
 *  event with the integer notch count. Natural-touch direction: drag down → scroll up
 *  (negative wheel rotation), like a phone list. */
private class ScrollDragSession(
    val scrollPane: javax.swing.JScrollPane,
    var lastWinX: Int,
    var lastWinY: Int,
) : DragSession {
    private var accumulated = 0f

    override fun update(pos: Offset, boxSize: IntSize) {
        val window = java.awt.Window.primaryFrame() ?: return
        val (wx, wy) = composeToWindow(pos, boxSize, window) ?: return
        val dy = wy - lastWinY
        lastWinX = wx
        lastWinY = wy
        // Negate so finger-down moves content UP (touchscreen convention).
        accumulated += -dy / NOTCH_PX
        val notches = accumulated.toInt()
        if (notches != 0) {
            accumulated -= notches.toFloat()
            val ev = java.awt.event.MouseWheelEvent(
                scrollPane,
                java.awt.event.MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                0,
                wx - scrollPaneAbsX(),
                wy - scrollPaneAbsY(),
                0,
                false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                notches,
            )
            scrollPane.dispatchMouseEvent(ev)
        }
    }

    override fun end() {
        // No-op; wheel events are independent.
    }

    private fun scrollPaneAbsX(): Int {
        var x = 0; var c: java.awt.Component? = scrollPane
        while (c != null && c !is java.awt.Window) { x += c.x; c = c.parent }
        return x
    }
    private fun scrollPaneAbsY(): Int {
        var y = 0; var c: java.awt.Component? = scrollPane
        while (c != null && c !is java.awt.Window) { y += c.y; c = c.parent }
        return y
    }

    companion object {
        /** Pixels of finger movement per wheel-notch. ~32 ≈ one list-row per ~32px,
         *  which matches the feel of stock Android scrolling. */
        private const val NOTCH_PX = 32f
    }
}

/** Two-finger pinch → mouse-wheel translation. Tracks the distance between the two
 *  fingers; every time the distance changes by PINCH_NOTCH_PX in either direction,
 *  fires one MOUSE_WHEEL notch on whatever Canvas/JScrollPane sits under the centroid.
 *  Single-finger drag now handles camera rotation via MobileHitTest, so pinch is just
 *  zoom and doesn't need a synthetic BUTTON2 press.
 *
 *  Sign convention: pinch OUT (fingers apart) → negative rotation = wheel-up = OSRS
 *  zoom in. Pinch IN (fingers together) → positive rotation = wheel-down = zoom out. */
private class PinchSession(initialA: Offset, initialB: Offset) {
    private var lastDistance = distance(initialA, initialB)
    private var accumulated = 0f
    private var ended = false

    fun update(a: Offset, b: Offset, boxSize: IntSize) {
        if (ended) return
        val newDist = distance(a, b)
        val delta = newDist - lastDistance
        lastDistance = newDist
        // pinch OUT = positive delta = wheel up (negative rotation).
        accumulated += -delta / PINCH_NOTCH_PX
        val notches = accumulated.toInt()
        if (notches != 0) {
            accumulated -= notches.toFloat()
            val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            dispatchWheel(centroid, boxSize, notches)
        }
    }

    fun end() { ended = true }

    companion object {
        /** Pixels of pinch-distance change per wheel notch. Tuned so a "comfortable"
         *  two-finger zoom (≈40% spread/contract on a phone screen) produces ~3 notches,
         *  matching how a desktop mouse wheel feels for the same intent. */
        private const val PINCH_NOTCH_PX = 40f

        private fun distance(a: Offset, b: Offset): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }
}

/** Send one MouseWheelEvent at the given Compose position. Targets the first Canvas in
 *  the hit ancestry (so pinching over the game scene zooms the camera) or the first
 *  JScrollPane (so a pinch over the plugin list scrolls it, matching the drag-scroll
 *  path). Notches < 0 → wheel up (zoom in); notches > 0 → wheel down (zoom out). */
private fun dispatchWheel(pos: Offset, boxSize: IntSize, notches: Int) {
    if (notches == 0) return
    val window = java.awt.Window.primaryFrame() ?: return
    val (winX, winY) = composeToWindow(pos, boxSize, window) ?: return
    val hit = hitTest(window, winX, winY) ?: return
    val target = ancestorChain(hit.comp).firstOrNull {
        it is java.awt.Canvas || it is javax.swing.JScrollPane
    } ?: hit.comp
    val (lx, ly) = localCoords(target, winX, winY)
    val ev = java.awt.event.MouseWheelEvent(
        target,
        java.awt.event.MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        0, lx, ly, 0, false,
        java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
        kotlin.math.abs(notches),
        notches,
    )
    target.dispatchMouseEvent(ev)
}

/** True iff the release position lies on an option of the open right-click menu (or an
 *  open submenu) published by MobileInputPlugin. Converts Compose-space → window-space →
 *  canvas-local using the same path beginDrag uses so the coord systems match. */
private fun releaseHitsMenuOption(releasePos: Offset, boxSize: IntSize): Boolean {
    val window = java.awt.Window.primaryFrame() ?: return false
    val (winX, winY) = composeToWindow(releasePos, boxSize, window) ?: return false
    val hit = hitTest(window, winX, winY) ?: return false
    val canvas = ancestorChain(hit.comp).firstOrNull { it is java.awt.Canvas } as? java.awt.Canvas
        ?: return false
    val cHit = hitTestExact(window, canvas) ?: return false
    return net.runelite.client.plugins.mobile.MobileHitTest.isMenuOptionAt(
        winX - cHit.absX, winY - cHit.absY)
}

private fun beginDrag(downPos: Offset, currentPos: Offset, boxSize: IntSize): DragSession? {
    val window = java.awt.Window.primaryFrame() ?: return null
    // Start the press at the original down location, not the slop-crossing point — that
    // way the press lands where the finger first landed and the subsequent drag deltas
    // align with the user's intent.
    val (winX, winY) = composeToWindow(downPos, boxSize, window) ?: return null
    val hit = hitTest(window, winX, winY) ?: return null
    val ancestry = ancestorChain(hit.comp).toList()
    val canvas = ancestry.firstOrNull { it is java.awt.Canvas } as? java.awt.Canvas
    // Outside the game Canvas: if there's a JScrollPane in the ancestry the drag
    // translates into MOUSE_WHEEL events on that scrollpane (same path real Swing
    // takes when you wheel-scroll the plugin list). Replicates the desktop UX
    // without us needing to draw a scrollbar thumb to grab.
    if (canvas == null) {
        val scrollPane = ancestry.firstOrNull { it is javax.swing.JScrollPane } as? javax.swing.JScrollPane
        if (scrollPane != null) {
            val session = ScrollDragSession(scrollPane, winX, winY)
            session.update(currentPos, boxSize)
            return session
        }
    }
    // Inside the game Canvas: ask MobileInputPlugin whether the touch landed on a
    // visible RS widget (inventory slot, button, scrollbar) vs. on the 3D scene. UI
    // widget → BUTTON1 drag so the user can drag inv slot-to-slot; scene → BUTTON2 so
    // the OSRS camera rotates. Outside the Canvas (Swing sidebar etc.) every drag is
    // BUTTON1 by default — those are click-based widgets anyway.
    //
    // Modifier override: when the user has the Alt chip engaged, OverlayRenderer is
    // in `inOverlayManagingMode` (RuneLite's `dragHotkey` default is Alt) and treats
    // a BUTTON1 press over the canvas as the start of an overlay drag. If we kept
    // emitting BUTTON2 the camera would rotate and the overlay never moves. Force
    // BUTTON1 over the scene while Alt is held so the drag reaches the overlay path.
    val altHeld = net.runelite.mp.ui.bridge.ModifierState.altActive.value
    val button = if (canvas != null) {
        val cHit = hitTestExact(window, canvas)
        val cx = if (cHit != null) winX - cHit.absX else -1
        val cy = if (cHit != null) winY - cHit.absY else -1
        val isInterface = cHit != null
            && net.runelite.client.plugins.mobile.MobileHitTest.isInterfaceAt(cx, cy)
        val pick = when {
            altHeld -> BUTTON1
            isInterface -> BUTTON1
            else -> BUTTON2
        }
        android.util.Log.i("MobileInput",
            "begin drag canvas=($cx,$cy) interface=$isInterface alt=$altHeld button=$pick")
        pick
    } else {
        BUTTON1
    }
    val target = walkUpToListener(hit.comp) ?: hit.comp
    // Compute target's absolute origin so update() can re-translate moves.
    val tHit = hitTestExact(window, target) ?: return null
    val session = MouseDragSession(target, tHit.absX, tHit.absY, button, winX, winY)
    session.start()
    // Process the current pos as the first drag step so we don't lose the slop-crossing motion.
    session.update(currentPos, boxSize)
    return session
}

/** Hit-test a known component to recover its window-local origin. */
private fun hitTestExact(window: java.awt.Window, target: java.awt.Component): Hit? {
    fun walk(c: java.awt.Component, tx: Int, ty: Int): Hit? {
        val ax = tx + if (c is java.awt.Window) 0 else c.x
        val ay = ty + if (c is java.awt.Window) 0 else c.y
        if (c === target) return Hit(c, ax, ay)
        if (c is java.awt.Container) {
            for (child in c.components) {
                if (child == null) continue
                val r = walk(child, ax, ay)
                if (r != null) return r
            }
        }
        return null
    }
    return walk(window, 0, 0)
}

private fun ancestorChain(c: java.awt.Component): Sequence<java.awt.Component> = sequence {
    var cur: java.awt.Component? = c
    while (cur != null) { yield(cur); cur = cur.parent }
}

/** From a hit, find the closest ancestor (inclusive) that has any mouse listeners. */
private fun walkUpToListener(c: java.awt.Component): java.awt.Component? {
    var cur: java.awt.Component? = c
    while (cur != null) {
        if (cur.hasMouseListeners()) return cur
        cur = cur.parent
    }
    return null
}

/** Translate window-absolute (wx, wy) into target-local coords. */
private fun localCoords(target: java.awt.Component, wx: Int, wy: Int): Pair<Int, Int> {
    var dx = 0
    var dy = 0
    var cur: java.awt.Component? = target
    while (cur != null && cur !is java.awt.Window) {
        dx += cur.x
        dy += cur.y
        cur = cur.parent
    }
    return (wx - dx) to (wy - dy)
}

private fun fireMouse(target: java.awt.Component, id: Int, x: Int, y: Int, button: Int, pressed: Boolean) {
    val mods = if (pressed) when (button) {
        BUTTON1 -> java.awt.event.InputEvent.BUTTON1_DOWN_MASK
        BUTTON2 -> java.awt.event.InputEvent.BUTTON2_DOWN_MASK
        BUTTON3 -> java.awt.event.InputEvent.BUTTON3_DOWN_MASK
        else -> 0
    } else 0
    // Intentionally NOT ORing in ModifierState.modifierMask() here. The OSRS engine
    // resolves shift/alt menu state from its internal pressed-keys array (mutated by
    // the synthetic KeyEvent fired in ModifierState.dispatchModifier) — NOT from the
    // MouseEvent.modifiers field. Setting ALT_DOWN_MASK on a MouseEvent actually
    // caused the engine's mouse-handler to ignore clicks (its dispatch path treats
    // alt-modified clicks as a no-op), which broke in-game tapping while the Alt
    // chip was engaged.
    target.dispatchMouseEvent(
        java.awt.event.MouseEvent(
            target, id, System.currentTimeMillis(),
            mods, x, y, 1, button == BUTTON3, button,
        ),
    )
}

private fun downMaskFor(button: Int): Int = when (button) {
    BUTTON1 -> java.awt.event.InputEvent.BUTTON1_DOWN_MASK
    BUTTON2 -> java.awt.event.InputEvent.BUTTON2_DOWN_MASK
    BUTTON3 -> java.awt.event.InputEvent.BUTTON3_DOWN_MASK
    else -> 0
}

/**
 * Physical mouse → AWT, reproducing what desktop Java delivers for the same hand motion:
 *
 *  - MOVED while no button is held, with ENTERED/EXITED as the pointer crosses between
 *    components (and EXITED when it leaves the game area, so the client drops its cursor).
 *  - PRESSED on the component under the pointer, which then owns the mouse until the last
 *    button comes up: DRAGGED and RELEASED go to it even off its bounds (implicit grab).
 *  - CLICKED after RELEASED unless the press turned into a drag, with multi-click counts.
 *  - Each button reported as itself — primary BUTTON1, middle BUTTON2, secondary BUTTON3 —
 *    with getModifiersEx carrying every button still down.
 *  - MOUSE_WHEEL on the deepest component under the pointer that listens for it.
 *
 * Keyboard modifiers stay off the mouse events for the same reason as [fireMouse]: the
 * client reads them from its own key state, which hardware keys already drive.
 */
private class MouseBridge {
    private var held = 0
    private var swallowed = 0
    private var grab: java.awt.Component? = null
    private var hover: java.awt.Component? = null
    private var pressX = 0
    private var pressY = 0
    private var dragged = false
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var wheel = 0f

    private var clickCount = 0
    private var lastPressAt = 0L
    private var lastPressButton = 0
    private var lastPressX = 0
    private var lastPressY = 0

    fun handle(event: PointerEvent, position: Offset, scrollY: Float, boxSize: IntSize) {
        val window = java.awt.Window.primaryFrame() ?: return
        if (event.type == PointerEventType.Exit && grab == null) {
            setHover(null, 0, 0)
            return
        }
        val buttons = event.buttons
        var now = (if (buttons.isPrimaryPressed) java.awt.event.InputEvent.BUTTON1_DOWN_MASK else 0) or
            (if (buttons.isTertiaryPressed) java.awt.event.InputEvent.BUTTON2_DOWN_MASK else 0) or
            (if (buttons.isSecondaryPressed) java.awt.event.InputEvent.BUTTON3_DOWN_MASK else 0)
        // Some mice/touchpads report a press with an empty button state; that's a left click.
        if (now == 0 && event.type == PointerEventType.Press) now = java.awt.event.InputEvent.BUTTON1_DOWN_MASK
        if (event.changes.none { it.pressed }) now = 0

        val pos = composeToWindow(position, boxSize, window, clamp = grab != null)
        if (pos == null) {
            // Over the letterbox: nothing to hover, and there's no grab to keep feeding.
            // Buttons pressed out here never reach the game, even if dragged onto it —
            // the same as pressing outside a desktop window.
            swallowed = (swallowed or now) and held.inv()
            setHover(null, 0, 0)
            return
        }
        swallowed = swallowed and now
        val (wx, wy) = pos

        // Movement first, under the old button state — a release event's position is the
        // drag's last position, and a press event's is where the press happens.
        if (wx != lastX || wy != lastY) {
            lastX = wx
            lastY = wy
            val g = grab
            if (g != null && held != 0) {
                if (kotlin.math.abs(wx - pressX) > DRAG_SLOP || kotlin.math.abs(wy - pressY) > DRAG_SLOP) {
                    dragged = true
                }
                val (lx, ly) = localCoords(g, wx, wy)
                fire(g, java.awt.event.MouseEvent.MOUSE_DRAGGED, lx, ly, held, java.awt.event.MouseEvent.NOBUTTON, 0)
            } else {
                val target = targetAt(window, wx, wy)
                setHover(target, wx, wy)
                if (target != null) {
                    val (lx, ly) = localCoords(target, wx, wy)
                    fire(target, java.awt.event.MouseEvent.MOUSE_MOVED, lx, ly, 0, java.awt.event.MouseEvent.NOBUTTON, 0)
                }
            }
        }

        for (button in intArrayOf(BUTTON1, BUTTON2, BUTTON3)) {
            val mask = downMaskFor(button)
            if ((now and mask) != 0 && (held and mask) == 0 && (swallowed and mask) == 0) {
                press(window, button, mask, wx, wy)
            }
        }
        for (button in intArrayOf(BUTTON1, BUTTON2, BUTTON3)) {
            val mask = downMaskFor(button)
            if ((now and mask) == 0 && (held and mask) != 0) release(window, button, mask, wx, wy)
        }

        if (event.type == PointerEventType.Scroll && scrollY != 0f) scroll(window, wx, wy, scrollY)
    }

    private fun press(window: java.awt.Window, button: Int, mask: Int, wx: Int, wy: Int) {
        if (grab == null) {
            grab = targetAt(window, wx, wy) ?: return
            pressX = wx
            pressY = wy
            dragged = false
        }
        val now = System.currentTimeMillis()
        clickCount = if (button == lastPressButton && now - lastPressAt <= MULTI_CLICK_MS &&
            kotlin.math.abs(wx - lastPressX) <= DRAG_SLOP && kotlin.math.abs(wy - lastPressY) <= DRAG_SLOP
        ) clickCount + 1 else 1
        lastPressAt = now
        lastPressButton = button
        lastPressX = wx
        lastPressY = wy

        held = held or mask
        val g = grab ?: return
        val (lx, ly) = localCoords(g, wx, wy)
        fire(g, java.awt.event.MouseEvent.MOUSE_PRESSED, lx, ly, held, button, clickCount)
    }

    private fun release(window: java.awt.Window, button: Int, mask: Int, wx: Int, wy: Int) {
        held = held and mask.inv()
        val g = grab ?: return
        val (lx, ly) = localCoords(g, wx, wy)
        fire(g, java.awt.event.MouseEvent.MOUSE_RELEASED, lx, ly, held, button, clickCount)
        if (!dragged) {
            fire(g, java.awt.event.MouseEvent.MOUSE_CLICKED, lx, ly, held, button, clickCount)
            val hit = hitTest(window, wx, wy)
            if (hit != null) clickDirectWidget(hit.comp, wx, wy, button)
        }
        if (held == 0) {
            grab = null
            // Desktop defers the enter/exit for a drag that ended over another component
            // until the button comes up.
            setHover(targetAt(window, wx, wy), wx, wy)
        }
    }

    private fun scroll(window: java.awt.Window, wx: Int, wy: Int, delta: Float) {
        // Compose reports wheel-down as positive, the same sign as AWT's wheelRotation.
        // Smooth/high-resolution wheels arrive in fractions; whole notches are emitted
        // as they accumulate, with the fraction carried in preciseWheelRotation.
        wheel += delta
        val notches = wheel.toInt()
        wheel -= notches
        val hit = hitTest(window, wx, wy) ?: return
        val target = ancestorChain(hit.comp).firstOrNull { it.mouseWheelListeners.isNotEmpty() }
            ?: ancestorChain(hit.comp).firstOrNull { it is java.awt.Canvas || it is javax.swing.JScrollPane }
            ?: return
        val (lx, ly) = localCoords(target, wx, wy)
        target.dispatchMouseEvent(
            java.awt.event.MouseWheelEvent(
                target, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                held, lx, ly, 0, false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches, delta.toDouble(),
            ),
        )
    }

    private fun setHover(target: java.awt.Component?, wx: Int, wy: Int) {
        val old = hover
        if (old === target) return
        hover = target
        if (old != null) {
            val (lx, ly) = if (target != null) localCoords(old, wx, wy) else (-1 to -1)
            fire(old, java.awt.event.MouseEvent.MOUSE_EXITED, lx, ly, held, java.awt.event.MouseEvent.NOBUTTON, 0)
        }
        if (target != null) {
            val (lx, ly) = localCoords(target, wx, wy)
            fire(target, java.awt.event.MouseEvent.MOUSE_ENTERED, lx, ly, held, java.awt.event.MouseEvent.NOBUTTON, 0)
        }
    }

    private fun targetAt(window: java.awt.Window, wx: Int, wy: Int): java.awt.Component? {
        val hit = hitTest(window, wx, wy) ?: return null
        return walkUpToListener(hit.comp) ?: hit.comp
    }

    private fun fire(target: java.awt.Component, id: Int, x: Int, y: Int, mods: Int, button: Int, clicks: Int) {
        // Popup trigger on the secondary press, as the Linux/macOS toolkits report it.
        val popup = id == java.awt.event.MouseEvent.MOUSE_PRESSED && button == BUTTON3
        target.dispatchMouseEvent(
            java.awt.event.MouseEvent(target, id, System.currentTimeMillis(), mods, x, y, clicks, popup, button),
        )
    }

    companion object {
        /** Window pixels a held button may wander before the press counts as a drag. */
        private const val DRAG_SLOP = 2
        /** awt.multiClickInterval's usual desktop value. */
        private const val MULTI_CLICK_MS = 500L
    }
}
