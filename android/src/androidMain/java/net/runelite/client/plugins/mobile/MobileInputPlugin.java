package net.runelite.client.plugins.mobile;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.mp.ui.bridge.SoftKeyboardController;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Snapshots the visible RS widget tree once per frame and publishes the resulting flat
 * list of opaque rectangles to {@link MobileHitTest}, so the Android touch driver can
 * tell whether a gesture starts on a UI widget (BUTTON1 — inv slot, scrollbar) or on
 * the 3D scene (BUTTON2 — camera rotate) without ever touching the client-thread-only
 * Widget API from the touch handler.
 *
 * <p>Safe to load on desktop too; it just produces an unused snapshot.</p>
 */
@Slf4j
@PluginDescriptor(
	name = "Mobile Input Bridge",
	description = "Provides interface-vs-scene hit-testing for the Android touch driver",
	hidden = true
)
public class MobileInputPlugin extends Plugin
{
	/** Height of the root menu's "Choose Option" header in real (unstretched) pixels;
	 *  the first option row starts below it. */
	private static final int MENU_HEADER_HEIGHT = 19;
	/** Widget content type the client uses for the 3D scene viewport. */
	private static final int CONTENT_TYPE_VIEWPORT = 1337;

	@Inject private Client client;
	@Inject private net.runelite.client.callback.ClientThread clientThread;

    @Inject private MobileInputConfig config;

    @Provides
    MobileInputConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(MobileInputConfig.class);
    }

    @Override
    protected void startUp() { updateKeyboardMode(); }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) { walkDirty = true; updateKeyboardMode(); }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (MobileInputConfig.GROUP.equals(event.getGroup())) updateKeyboardMode();
    }

    private void updateKeyboardMode()
    {
        SoftKeyboardController.INSTANCE.updatePasswordMode(
            client.getGameState() != GameState.LOGGED_IN || config.passwordProtectedInput());
    }

	private long lastLogMs;
	private int lastLoggedCount = -1;

	@Override
	protected void shutDown()
	{
        SoftKeyboardController.INSTANCE.updatePasswordMode(true);
		MobileHitTest.setInterfaceRects(null);
		MobileScrollbars.publish(new MobileScrollbars.Target[0]);
            MobileSwipeScroll.publish(new MobileSwipeScroll.Target[0]);
		MobileHitTest.setMenuRect(null);
		MobileHitTest.setMenuOptions(0, null);
		MobileHitTest.setFixedMode(false);
		MobileHitTest.setViewportRect(null);
	}

	/** Max age of the cached touch targets; scroll/drag/script moves fire no event. */
	private static final long WALK_SAFETY_MS = 200;
	private volatile boolean walkDirty = true;
	private long lastWalkMs;
	private int cachedCanvasW = -1, cachedCanvasH = -1;
	private double cachedSx, cachedSy;
	private Rectangle[] cachedRects;
	private MobileSwipeScroll.Target[] cachedSwipes;
	private MobileScrollbars.Target[] cachedScrollbars;

	@Subscribe
	public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded e) { walkDirty = true; }

	@Subscribe
	public void onWidgetClosed(net.runelite.api.events.WidgetClosed e) { walkDirty = true; }

	@Subscribe
	public void onBeforeRender(BeforeRender ev)
	{
		// The engine reports all rect/coord APIs (viewport, menu, widget bounds) in
		// "real" (internal) space — the same space TranslateMouseListener converts
		// incoming mouse events INTO. Our Android touch handler delivers canvas-local
		// pixels in "stretched" space (what the user actually sees on screen). Compute
		// the scale once and apply at every publish site so rect-in-rect checks against
		// touch coords just work. When stretched mode is off, the factor is 1 and the
		// scaleRect path becomes a no-op.
		double sx = 1.0;
		double sy = 1.0;
		if (client.isStretchedEnabled())
		{
			Dimension real = client.getRealDimensions();
			Dimension stretched = client.getStretchedDimensions();
			if (real != null && stretched != null
				&& real.width > 0 && real.height > 0)
			{
				sx = (double) stretched.width / real.width;
				sy = (double) stretched.height / real.height;
			}
		}

		// Mode + viewport: published every frame so MobileHitTest can short-circuit on
		// fixed mode (where the chrome layout is static and "is this on UI" reduces to
		// "is this outside the 3D viewport"). The widget walk below is still done so
		// resizable mode keeps working — and so fixed mode has a fallback for the first
		// frame after a mode-change before the engine has computed viewport bounds.
		boolean fixed = !client.isResized();
		MobileHitTest.setFixedMode(fixed);
		if (fixed)
		{
			int vw = client.getViewportWidth();
			int vh = client.getViewportHeight();
			if (vw > 0 && vh > 0)
			{
				MobileHitTest.setViewportRect(scaleRect(
					client.getViewportXOffset(), client.getViewportYOffset(), vw, vh, sx, sy));
			}
		}
		else
		{
			MobileHitTest.setViewportRect(null);
		}

		// Menu rect: published every frame so the touch driver knows whether a long-press
		// release lands on a menu entry (→ select that entry with a left-click) or in the
		// world (→ no click; the menu just stays open until dismissed).
		if (client.isMenuOpen())
		{
			MobileHitTest.setMenuRect(scaleRect(
				client.getMenuX(), client.getMenuY(),
				client.getMenuWidth(), client.getMenuHeight(),
				sx, sy));
			// Submenus open on hover over their parent row. Menu bounds are documented as
			// valid only while the menu is open, so a submenu with a size is taken as open.
			List<Rectangle> subs = new ArrayList<>(2);
			for (MenuEntry entry : client.getMenu().getMenuEntries())
			{
				Menu sub = entry.getSubMenu();
				if (sub != null && sub.getMenuWidth() > 0 && sub.getMenuHeight() > 0)
				{
					subs.add(scaleRect(sub.getMenuX(), sub.getMenuY(),
						sub.getMenuWidth(), sub.getMenuHeight(), sx, sy));
				}
			}
			MobileHitTest.setMenuOptions((int) Math.round(MENU_HEADER_HEIGHT * sy),
				subs.toArray(new Rectangle[0]));
		}
		else
		{
			MobileHitTest.setMenuRect(null);
			MobileHitTest.setMenuOptions(0, null);
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			MobileScrollbars.publish(new MobileScrollbars.Target[0]);
            MobileSwipeScroll.publish(new MobileSwipeScroll.Target[0]);
		}
		if (client.getGameState() == GameState.LOGGED_IN) {
			Widget[] roots = client.getWidgetRoots();
			if (roots == null)
			{
				MobileHitTest.setInterfaceRects(null);
				MobileScrollbars.publish(new MobileScrollbars.Target[0]);
            MobileSwipeScroll.publish(new MobileSwipeScroll.Target[0]);
				return;
			}
			// The widget walk is the dominant per-frame cost (~22% of the game thread on
			// a Fire HD 8), so the results are cached and only rebuilt when an interface
			// was loaded/closed, the game state/canvas/scale changed, or WALK_SAFETY_MS
			// has elapsed (covers scrolling, drags and script-driven moves that fire no
			// event). Menu-open gating below still runs every frame from the cache.
			int canvasW = client.getCanvasWidth();
			int canvasH = client.getCanvasHeight();
			long nowMs = System.currentTimeMillis();
			if (walkDirty || cachedRects == null || canvasW != cachedCanvasW || canvasH != cachedCanvasH
				|| sx != cachedSx || sy != cachedSy || nowMs - lastWalkMs >= WALK_SAFETY_MS)
			{
				walkDirty = false;
				lastWalkMs = nowMs;
				cachedCanvasW = canvasW;
				cachedCanvasH = canvasH;
				cachedSx = sx;
				cachedSy = sy;
				List<Rectangle> rects = new ArrayList<>(64);
				List<MobileScrollbars.Target> scrollbars = new ArrayList<>();
				List<MobileSwipeScroll.Target> swipes = new ArrayList<>();
				for (Widget root : roots)
				{
					collect(root, rects, scrollbars, swipes, canvasW, canvasH, sx, sy);
				}
				Rectangle[] built;
				if (sx == 1.0 && sy == 1.0)
				{
					built = rects.toArray(new Rectangle[0]);
				}
				else
				{
					built = new Rectangle[rects.size()];
					for (int i = 0; i < built.length; i++)
					{
						Rectangle r = rects.get(i);
						built[i] = scaleRect(r.x, r.y, r.width, r.height, sx, sy);
					}
				}
				cachedRects = built;
				cachedSwipes = swipes.toArray(new MobileSwipeScroll.Target[0]);
				cachedScrollbars = scrollbars.toArray(new MobileScrollbars.Target[0]);
			}
			Rectangle[] arr = cachedRects;
			MobileHitTest.setInterfaceRects(arr);
			MobileSwipeScroll.publish(client.isMenuOpen() ? new MobileSwipeScroll.Target[0] : cachedSwipes);
			MobileScrollbars.publish(client.isMenuOpen() ? new MobileScrollbars.Target[0] : cachedScrollbars);

			// Periodic visibility into what's being treated as "interface". Helps diagnose
			// when too many rects are flagged (scene-clicks getting BUTTON1 instead of
			// BUTTON2) vs too few (inv drag getting BUTTON2 instead of BUTTON1).
			long now = System.currentTimeMillis();
			if (arr.length != lastLoggedCount && now - lastLogMs > 3000)
			{
				lastLogMs = now;
				lastLoggedCount = arr.length;
				log.info("mobile hit-test snapshot: {} blocking rects", arr.length);
			}
		}
	}

	/** Multiply a rect's origin + size by the real→stretched scale factor. Identity when
	 *  the factor is 1 (stretched mode off). Allocates a fresh Rectangle either way. */
	private static Rectangle scaleRect(int x, int y, int w, int h, double sx, double sy)
	{
		if (sx == 1.0 && sy == 1.0) return new Rectangle(x, y, w, h);
		return new Rectangle(
			(int) Math.round(x * sx), (int) Math.round(y * sy),
			(int) Math.round(w * sx), (int) Math.round(h * sy));
	}

	/**
	 * Depth-first walk. A widget contributes to the click-blocking set when it
	 * intercepts clicks or drags (buttons, inventory slots, bank slider, scrollbars)
	 * while excluding full-screen transparent viewport containers so 3D scene camera
	 * rotation works smoothly.
	 */
	private void collect(Widget w, List<Rectangle> out, List<MobileScrollbars.Target> scrollbars, List<MobileSwipeScroll.Target> swipes,
		int canvasW, int canvasH, double sx, double sy)
	{
		// isHidden also covers a hidden ancestor, so a hidden subtree is skipped whole.
		if (w == null || w.isHidden())
		{
			return;
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn) collect(c, out, scrollbars, swipes, canvasW, canvasH, sx, sy);
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat) collect(c, out, scrollbars, swipes, canvasW, canvasH, sx, sy);
		}
		Widget[] nest = w.getNestedChildren();
		if (nest != null)
		{
			for (Widget c : nest) collect(c, out, scrollbars, swipes, canvasW, canvasH, sx, sy);
		}
		// Scrollbar arrows poll held mouse state, which instantaneous touch clicks miss, so a
		// tap on a scrollable list's bar scrolls it directly, and the whole bar blocks so a
		// thumb drag isn't taken for a camera drag.
		if (w.getType() == WidgetType.LAYER && w.getHeight() > 32 && w.getScrollHeight() > w.getHeight())
		{
			Widget scrollbar = scrollbarFor(w);
            Rectangle visible = new Rectangle(w.getBounds());
            for (Widget parent = w.getParent(); parent != null; parent = parent.getParent())
                visible = visible.intersection(parent.getBounds());
            visible = visible.intersection(new Rectangle(0, 0, canvasW, canvasH));
            if (!visible.isEmpty()) {
                Rectangle bounds = scaleRect(visible.x, visible.y, visible.width, visible.height, sx, sy);
                Rectangle barBounds = scrollbar == null || scrollbar.isHidden() ? null : scrollbar.getBounds();
                Rectangle scaledBar = barBounds == null ? null : scaleRect(barBounds.x, barBounds.y, barBounds.width, barBounds.height, sx, sy);
                List<Rectangle> items = new ArrayList<>();
                collectDragItems(w, items, sx, sy);
                int initialScroll = w.getScrollY();
                swipes.add(new MobileSwipeScroll.Target(w, bounds, scaledBar, items.toArray(new Rectangle[0]), sy,
                    offset -> clientThread.invokeLater(() -> {
                        if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen() || w.isHidden()
                            || !MobileSwipeScroll.current(w, bounds)) return;
                        int next = Math.max(0, Math.min(w.getScrollHeight() - w.getHeight(), initialScroll + offset));
                        if (scrollbar != null && !scrollbar.isHidden())
                            client.runScript(ScriptID.UPDATE_SCROLLBAR, scrollbar.getId(), w.getId(), next);
                        else { w.setScrollY(next); w.revalidateScroll(); }
                    })));
            }
			if (scrollbar != null && !scrollbar.isHidden())
			{
				Rectangle b = w.getBounds();
				Rectangle bar = scrollbar.getBounds();
				out.add(bar);
				scrollbars.add(new MobileScrollbars.Target(
					scaleRect(bar.x, bar.y, bar.width, bar.height, sx, sy), sy,
					y -> clientThread.invokeLater(() -> {
						if (client.getGameState() == GameState.LOGGED_IN && !w.isHidden()
							&& !client.isMenuOpen() && b.equals(w.getBounds()))
						{
							int scrollY = MobileScrollbars.scrollPosition(
								y, bar.height, w.getScrollHeight(), w.getScrollY());
							client.runScript(ScriptID.UPDATE_SCROLLBAR, scrollbar.getId(), w.getId(), scrollY);
						}
					})));
			}
		}
		if (isClickBlocking(w, canvasW, canvasH))
		{
			Rectangle b = w.getBounds();
			if (b != null && b.width > 0 && b.height > 0)
			{
				out.add(b);
			}
		}
	}

    private static void collectDragItems(Widget w, List<Rectangle> items, double sx, double sy) {
        if (w == null || w.isHidden()) return;
        if (w.getItemId() >= 0 || w.getDragParent() != null) {
            Rectangle b = w.getBounds();
            items.add(scaleRect(b.x, b.y, b.width, b.height, sx, sy));
        }
        Widget[][] children = {w.getDynamicChildren(), w.getStaticChildren(), w.getNestedChildren()};
        for (Widget[] group : children) if (group != null)
            for (Widget child : group) collectDragItems(child, items, sx, sy);
    }

	/** Most wide a scrollbar column gets; the game's are 16px. */
	private static final int SCROLLBAR_MAX_WIDTH = 24;
	/** Slack, in px, when matching a bar to its list's right edge and height. */
	private static final int SCROLLBAR_SLACK = 6;

	/**
	 * The scrollbar driving scrollable layer {@code list}: a known one, else a sibling laid
	 * out as its bar. The game builds a bar (scrollbar_vertical) as a narrow layer of
	 * arrow/track/thumb children right beside the list it scrolls, which is how the Grand
	 * Exchange's history, price checker and price list bars are found.
	 */
	private Widget scrollbarFor(Widget list)
	{
		int known = skillGuideScrollbar(list.getId());
		if (known != -1)
		{
			return client.getWidget(known);
		}
		Widget parent = list.getParent();
		Widget[] siblings = parent == null ? null : parent.getStaticChildren();
		if (siblings == null)
		{
			return null;
		}
		Rectangle lb = list.getBounds();
		for (Widget s : siblings)
		{
			if (s == null || s == list || s.isHidden())
			{
				continue;
			}
			Widget[] parts = s.getDynamicChildren();
			if (parts == null || parts.length < 3)
			{
				continue;
			}
			Rectangle sb = s.getBounds();
			boolean narrow = sb.width > 0 && sb.width <= SCROLLBAR_MAX_WIDTH;
			// Right beside the list, or tucked inside its right edge.
			boolean beside = Math.abs(sb.x - (lb.x + lb.width)) <= SCROLLBAR_SLACK
				|| Math.abs((sb.x + sb.width) - (lb.x + lb.width)) <= SCROLLBAR_SLACK;
			boolean spans = Math.abs(sb.y - lb.y) <= SCROLLBAR_SLACK
				&& Math.abs(sb.height - lb.height) <= SCROLLBAR_SLACK;
			if (narrow && beside && spans)
			{
				return s;
			}
		}
		return null;
	}

	private static int skillGuideScrollbar(int widgetId)
	{
		switch (widgetId)
		{
			case InterfaceID.SkillGuide.INFO:
				return InterfaceID.SkillGuide.SCROLLBAR;
			case InterfaceID.SkillGuideV2.OVERVIEW_CONTENT:
			case InterfaceID.SkillGuideV2.OVERVIEW_CONTENT_1:
			case InterfaceID.SkillGuideV2.OVERVIEW_CONTENT_2:
				return InterfaceID.SkillGuideV2.OVERVIEW_SCROLLBAR;
			case InterfaceID.SkillGuideV2.LIST:
				return InterfaceID.SkillGuideV2.LIST_SCROLLER;
			default:
				return -1;
		}
	}

	/**
	 * What counts as "interface that swallows a drag":
	 * <ul>
	 *   <li>Full-screen / viewport-sized transparent containers → never block
	 *       (preserves 3D scene touch-hold camera rotation in resizable mode).</li>
	 *   <li>LAYER widgets only block if they have {@code noClickThrough} or scrollable
	 *       content ({@code scrollHeight > height}).</li>
	 *   <li>Non-LAYER widgets with any interactive property → block: {@code actions},
	 *       {@code dragParent}, {@code hasListener}, {@code scrollHeight > height},
	 *       {@code noClickThrough}, {@code clickMask}.</li>
	 * </ul>
	 */
	private static boolean isClickBlocking(Widget w, int canvasW, int canvasH)
	{
		if (w == null)
		{
			return false;
		}

		int width = w.getWidth();
		int height = w.getHeight();
		if (width <= 0 || height <= 0)
		{
			return false;
		}

		boolean isFullScreen = canvasW > 0 && canvasH > 0
			&& width >= canvasW - 10 && height >= canvasH - 10;

		// Engine-drawn content (world map view, minimap): the client renders it itself, so
		// the widget has no sprite or text and is often a LAYER. The rules below would see
		// an empty container and let a drag through to the camera, so the world map turned
		// the camera instead of panning. The 3D viewport is the one engine-drawn area a
		// drag must reach as camera.
		int contentType = w.getContentType();
		if (contentType != 0)
		{
			return contentType != CONTENT_TYPE_VIEWPORT && !isFullScreen;
		}

		// LAYER widgets are RS's transparent group containers (HUD trim, viewport
		// background, etc.) and draw nothing themselves; whatever is visible in them is
		// a child, collected on its own. Only a genuinely scrollable one (e.g. the bank
		// item container) blocks, so a drag in it scrolls instead of turning the camera.
		// noClickThrough alone doesn't count: in resizable mode such layers span empty
		// screen, and a drag there is meant to turn the camera.
		if (w.getType() == WidgetType.LAYER)
		{
			return !isFullScreen && height > 0 && w.getScrollHeight() > height;
		}

		// Full-screen non-layer containers → never block (preserves camera)
		if (isFullScreen)
		{
			return false;
		}

		// Nothing drawn here → nothing the user can see to grab. Invisible hotspots
		// (listeners or actions on an empty widget) would otherwise turn a camera drag
		// over empty scene into a BUTTON1 click-drag.
		if (!drawsSomething(w) && !(height > 0 && w.getScrollHeight() > height))
		{
			return false;
		}

		// --- Non-LAYER, non-full-screen widgets below ---
		// These are the real interactive UI elements: buttons, inventory slots,
		// scrollbar thumbs, bank items, etc.

		if (w.getNoClickThrough())
		{
			return true;
		}

		String[] actions = w.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null && !a.isEmpty())
				{
					return true;
				}
			}
		}

		if (w.getDragParent() != null)
		{
			return true;
		}

		if (height > 0 && w.getScrollHeight() > height)
		{
			return true;
		}

		// hasListener() catches scrollbar thumbs, arrows, and similar small interactive
		// components. Guard with a size check: only block when at least one dimension is
		// smaller than half the canvas, so large viewport-covering widgets (which may
		// have listeners for scene rendering callbacks) don't suppress camera rotation.
		if (w.hasListener()
			&& (canvasW <= 0 || canvasH <= 0
				|| width < canvasW / 2 || height < canvasH / 2))
		{
			return true;
		}

		return w.getClickMask() != 0;
	}

	/** True if the widget itself puts pixels on screen: a sprite or item icon, non-empty
	 *  text, a rectangle that isn't fully transparent (opacity 255), a model or a line. */
	private static boolean drawsSomething(Widget w)
	{
		switch (w.getType())
		{
			case WidgetType.GRAPHIC:
				return (w.getSpriteId() >= 0 || w.getItemId() >= 0) && w.getOpacity() < 255;
			case WidgetType.TEXT:
				String text = w.getText();
				return text != null && !text.isEmpty() && w.getOpacity() < 255;
			case WidgetType.RECTANGLE:
				return w.getOpacity() < 255;
			case WidgetType.MODEL:
				return w.getModelId() >= 0 || w.getModelType() != 1;
			default:
				return true;
		}
	}
}
