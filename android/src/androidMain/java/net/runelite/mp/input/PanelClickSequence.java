package net.runelite.mp.input;

/** EDT-owned click pairing. Coordinates and monotonic timestamps come from Android input. */
public final class PanelClickSequence {
    private Object target;
    private long releasedAt;
    private double x, y;
    private int device, tool;

    public void reset() { target = null; }

    public int click(Object target, long press, long release, double x, double y,
                     int device, int tool, long timeout, double slop) {
        long gap = press - releasedAt;
        boolean pair = this.target == target && target != null && this.device == device && this.tool == tool
            && gap >= 0 && gap <= timeout && release >= press && release - press <= timeout
            && Math.hypot(x - this.x, y - this.y) <= slop;
        if (pair) {
            reset(); // A triple click must not trigger a second world hop.
            return 2;
        }
        this.target = release >= press && release - press <= timeout ? target : null;
        this.releasedAt = release;
        this.x = x; this.y = y; this.device = device; this.tool = tool;
        return 1;
    }
}
