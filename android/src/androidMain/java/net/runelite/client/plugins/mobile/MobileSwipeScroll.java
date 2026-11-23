package net.runelite.client.plugins.mobile;

import java.awt.Rectangle;
import java.util.function.IntConsumer;

/** Client-thread snapshots; gesture code never reads live widgets. */
public final class MobileSwipeScroll {
    private static volatile Target[] targets = new Target[0];
    static final class Target {
        final Object identity;
        final Rectangle bounds, bar;
        final Rectangle[] items;
        final double scale;
        final IntConsumer scroll;
        Target(Object identity, Rectangle bounds, Rectangle bar, Rectangle[] items, double scale, IntConsumer scroll) {
            this.identity = identity; this.bounds = bounds; this.bar = bar;
            this.items = items; this.scale = scale; this.scroll = scroll;
        }
    }
    static void publish(Target[] snapshot) { targets = snapshot; }
    static boolean current(Object identity, Rectangle bounds) {
        for (Target t : targets) if (t.identity == identity && t.bounds.equals(bounds)) return true;
        return false;
    }
    public static Gesture begin(int x, int y, int dx, int dy, long heldMillis) {
        if (Math.abs(dy) <= Math.abs(dx)) return null;
        // Never steal a scrollbar thumb drag, including from an enclosing list.
        for (Target t : targets) if (t.bar != null && t.bar.contains(x, y)) return null;
        for (Target t : targets) {
            if (!t.bounds.contains(x, y)) continue; // children precede parents
            for (Rectangle item : t.items) if (item.contains(x, y) && heldMillis >= 180) return null;
            return new Gesture(t, y);
        }
        return null;
    }
    public static final class Gesture {
        private final Target target;
        private final int startY;
        private boolean ended;
        private int last;
        private Gesture(Target target, int y) { this.target = target; startY = y; }
        public void move(int y) {
            if (ended) return;
            if (!current(target.identity, target.bounds)) { ended = true; return; }
            int offset = (int)Math.round((startY - y) / target.scale);
            // Deliver the change since the last move, not the total since the gesture began: the
            // receiver adds it to the widget's live scroll position, so a Target cached across
            // gestures (MobileInputPlugin reuses them for up to 200 ms) never replays a stale base.
            if (offset != last) { int delta = offset - last; last = offset; target.scroll.accept(delta); }
        }
        public void end() { ended = true; }
    }
}
