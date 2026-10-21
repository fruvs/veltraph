package dev.veltraph.client.fishing;

/** Rebuilt from FishingConstants.md: opt-in, bounded ascent to a local fluid/air boundary. */
public final class FishingSurfaceController {
    private boolean active;
    private long started, lastFinished;
    public boolean tick(boolean enabled, boolean submerged, double bubbles, boolean reachable,
                        double threshold, long debounce, long now) {
        if (!enabled || !submerged || !Double.isFinite(bubbles)) {
            if (active) lastFinished=now;
            active=false; return false;
        }
        if (active && (!reachable || now-started>10_000)) {
            active=false; lastFinished=now; return false;
        }
        if (!active && reachable && bubbles<=Math.max(0,Math.min(9,threshold))
                && now-lastFinished>=Math.max(500,debounce)) {
            active=true; started=now;
        }
        return active;
    }
    public void reset() { active=false; started=0; lastFinished=0; }
}
