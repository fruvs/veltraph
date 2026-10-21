package dev.veltraph.client.combat.ghost;
/** Source helper class owns a static long, default0, reset to wall-clock millis on actual helper entry. */
public final class GhostSourceJumpBoostClock {
    private static final GhostSourceJumpBoostClock RUNTIME=new GhostSourceJumpBoostClock();
    private volatile long lastEntry;
    public static GhostSourceJumpBoostClock runtime(){return RUNTIME;}
    GhostSourceJumpBoostClock() {}
    public long lastEntry(){return lastEntry;}
    void actualHelperEntry(long now){lastEntry=now;}
}
