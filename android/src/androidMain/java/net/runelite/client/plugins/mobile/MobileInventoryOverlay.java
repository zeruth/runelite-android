package net.runelite.client.plugins.mobile;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.BufferProvider;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Composites independently sized panels after widget overlays and before menus. */
public final class MobileInventoryOverlay extends Overlay
{
    private static final String[] KEYS = {"inventoryScale", "chatScale", "minimapScale", "staticTabsScale", "movableTabsScale"};
    private static final int[][] IDS = {
        {InterfaceID.ToplevelOsrsStretch.SIDE_MENU, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER},
        {InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER},
        {InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER},
        {InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER},
        {InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER}
    };
    private final Client client;
    private final MobileConfig config;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final int[][] scratch = {new int[0], new int[0], new int[0], new int[0], new int[0]};
    private final int[] scales = new int[IDS.length];
    private final boolean[] active = new boolean[IDS.length];
    private static volatile List<Panel> displayed = java.util.Collections.emptyList();
    private static volatile Resize gesture;

    private static final class Panel
    {
        final int id, percent, bufferIndex;
        final InventoryScaleGeometry geometry;
        final MobileInventoryOverlay owner;
        Panel(int id, int percent, InventoryScaleGeometry geometry, MobileInventoryOverlay owner, int bufferIndex)
        { this.id = id; this.percent = percent; this.geometry = geometry; this.owner = owner; this.bufferIndex = bufferIndex; }
    }

    /** A gesture latches one panel; moving fingers never switches the target. */
    public static final class Resize
    {
        private final Panel panel;
        private volatile int percent;
        private boolean ended;
        private Resize(Panel panel) { this.panel = panel; percent = panel.percent; }
        public void update(float ratio)
        {
            if (!ended && gesture == this && Float.isFinite(ratio))
                percent = Math.max(100, Math.min(200, Math.round(panel.percent * ratio)));
        }
        public void end()
        {
            if (ended) return;
            ended = true;
            if (gesture != this) return;
            final int saved = percent;
            panel.owner.clientThread.invokeLater(() -> {
                panel.owner.configManager.setConfiguration(MobileConfig.GROUP, KEYS[panel.id], saved);
                if (gesture == this) gesture = null;
            });
        }
    }

    @Inject
    public MobileInventoryOverlay(Client client, MobileConfig config, ClientThread clientThread, ConfigManager configManager)
    {
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
        this.configManager = configManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(10000f);
    }
    public static void clear() { displayed = java.util.Collections.emptyList(); gesture = null; }
    public void validateVisibility()
    {
        if (!client.isGpu() || !client.isResized()) { clear(); return; }
        // Remove hidden panels before input can use the preceding frame's geometry.
        List<Panel> prev = displayed;
        if (!prev.isEmpty())
        {
            List<Panel> visible = null;
            int n = prev.size();
            for (int i = 0; i < n; i++)
            {
                Panel panel = prev.get(i);
                if (widget(panel.id) != null)
                {
                    if (visible != null) visible.add(panel);
                }
                else if (visible == null)
                {
                    visible = new ArrayList<>(n - 1);
                    for (int j = 0; j < i; j++) visible.add(prev.get(j));
                }
            }
            if (visible != null) displayed = visible;
        }
        Resize current = gesture;
        if (current != null && widget(current.panel.id) == null) current.end();
    }
    @Override
    public Dimension render(Graphics2D graphics)
    {
        validateVisibility();
        if (!client.isGpu() || !client.isResized()) return null;
        BufferProvider buffer = client.getBufferProvider();
        if (buffer == null) return null;
        int w = buffer.getWidth(), h = buffer.getHeight();
        scales[0] = config.inventoryScale();
        scales[1] = config.chatScale();
        scales[2] = config.minimapScale();
        scales[3] = config.staticTabsScale();
        scales[4] = config.movableTabsScale();
        Resize current = gesture;
        if (current != null) scales[current.panel.id] = current.percent;

        List<Panel> prev = displayed;
        int matchIdx = 0;
        boolean matches = true;
        List<Panel> panels = null;
        for (int id = 0; id < IDS.length; id++)
        {
            Widget widget = widget(id);
            if (widget == null) continue;
            Rectangle bounds = widget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0
                || bounds.x < 0 || bounds.y < 0
                || bounds.x + bounds.width > w || bounds.y + bounds.height > h)
            {
                continue;
            }
            if (matches)
            {
                if (matchIdx < prev.size())
                {
                    Panel p = prev.get(matchIdx);
                    if (p.id == id && p.percent == scales[id] && p.owner == this
                        && p.geometry.width == w && p.geometry.height == h
                        && p.geometry.source.equals(bounds))
                    {
                        matchIdx++;
                        continue;
                    }
                }
                matches = false;
                panels = new ArrayList<>(IDS.length);
                for (int j = 0; j < matchIdx; j++) panels.add(prev.get(j));
            }
            panels.add(new Panel(id, scales[id], new InventoryScaleGeometry(bounds, scales[id], w, h), this, id));
        }
        if (matches)
        {
            if (matchIdx == prev.size())
            {
                panels = prev;
            }
            else
            {
                panels = new ArrayList<>(matchIdx);
                for (int j = 0; j < matchIdx; j++) panels.add(prev.get(j));
            }
        }
        if (panels != prev) displayed = panels;

        int panelCount = panels.size();
        boolean anyScaled = false;
        for (int i = 0; i < panelCount; i++)
        {
            if (!panels.get(i).geometry.isIdentity())
            {
                anyScaled = true;
                break;
            }
        }
        if (!anyScaled) return null;

        // Only panels that are scaled, or whose unscaled source lies underneath an earlier
        // scaled panel's destination, need pixel capture + repaint.
        for (int i = 0; i < panelCount; i++)
        {
            InventoryScaleGeometry gi = panels.get(i).geometry;
            boolean need = !gi.isIdentity();
            if (!need)
            {
                for (int j = 0; j < i; j++)
                {
                    if (active[j] && gi.source.intersects(panels.get(j).geometry.destination))
                    {
                        need = true;
                        break;
                    }
                }
            }
            active[i] = need;
        }
        int[] pixels = buffer.getPixels();
        // Capture every active piece before clearing any source, including detached tab rows.
        for (int i = 0; i < panelCount; i++)
        {
            if (!active[i]) continue;
            Panel panel = panels.get(i);
            int count = panel.geometry.source.width * panel.geometry.source.height;
            if (scratch[panel.bufferIndex].length < count) scratch[panel.bufferIndex] = new int[count];
            panel.geometry.capture(pixels, scratch[panel.bufferIndex]);
        }
        for (int i = 0; i < panelCount; i++)
        {
            if (!active[i]) continue;
            InventoryScaleGeometry gi = panels.get(i).geometry;
            if (!gi.isIdentity()) gi.clearSource(pixels);
        }
        for (int i = 0; i < panelCount; i++)
        {
            if (!active[i]) continue;
            Panel panel = panels.get(i);
            panel.geometry.paint(pixels, scratch[panel.bufferIndex]);
        }
        return null;
    }
    private Widget widget(int id)
    {
        // Each piece has its own visibility. Tab rows remain scalable when the inventory is closed.
        for (int widgetId : IDS[id])
        {
            Widget panel = client.getWidget(widgetId);
            if (panel != null && !panel.isHidden()) return panel;
        }
        return null;
    }
    private static Panel hit(int x, int y, int canvasWidth, int canvasHeight)
    {
        if (canvasWidth <= 0 || canvasHeight <= 0 || MobileHitTest.isMenuAt(x, y)
            || MobileHitTest.isMenuOptionAt(x, y)) return null;
        List<Panel> panels = displayed;
        for (int i = panels.size() - 1; i >= 0; i--)
        {
            Panel panel = panels.get(i);
            InventoryScaleGeometry g = panel.geometry;
            if (g.destination.contains((int)((long)x * g.width / canvasWidth),
                (int)((long)y * g.height / canvasHeight))) return panel;
        }
        return null;
    }
    public static Resize beginResize(int x, int y, int canvasWidth, int canvasHeight)
    {
        Panel panel = hit(x, y, canvasWidth, canvasHeight);
        if (panel == null) return null;
        Resize resize = new Resize(panel);
        gesture = resize;
        return resize;
    }
    /** Canvas/stretched coordinates in, original canvas coordinates out. */
    public static int[] mapInput(int x, int y, int canvasWidth, int canvasHeight)
    {
        Panel panel = hit(x, y, canvasWidth, canvasHeight);
        if (panel == null)
        {
            // Relocated pieces must not leave invisible clickable tabs at the old position.
            if (canvasWidth > 0 && canvasHeight > 0 && !MobileHitTest.isMenuAt(x, y) && !MobileHitTest.isMenuOptionAt(x, y))
                for (Panel old : displayed)
                    if (old.geometry.source.contains((int)((long)x * old.geometry.width / canvasWidth),
                        (int)((long)y * old.geometry.height / canvasHeight))) return new int[]{-1, -1};
            return new int[]{x, y};
        }
        InventoryScaleGeometry g = panel.geometry;
        int px = (int)((long)x * g.width / canvasWidth), py = (int)((long)y * g.height / canvasHeight);
        return new int[]{(int)((long)g.sourceX(px) * canvasWidth / g.width),
            (int)((long)g.sourceY(py) * canvasHeight / g.height)};
    }
}
