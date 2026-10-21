package dev.veltraph.client.combat.ghost;

/** SEARCH helper llIl... bytecode: increment, >5 chooses parent recovery child, otherwise schedule entry in20 ticks. */
public final class GhostSourceSearchRetryCounter {
    public enum Next { SCHEDULE_SAME_HANDLER_ENTRY, SWITCH_PARENT_RECOVERY_CHILD }
    public record Failure(int attempts,Next next,int delayTicks) {}
    private int attempts;
    /** Source handler reset hook clears the counter; ordinary SEARCH entry does not reset it. */
    public void resetFromMappedHandlerReset(){attempts=0;}
    public Failure recordIssuedFailure(){attempts++;return attempts>5
            ?new Failure(attempts,Next.SWITCH_PARENT_RECOVERY_CHILD,0)
            :new Failure(attempts,Next.SCHEDULE_SAME_HANDLER_ENTRY,20);}
    public int attempts(){return attempts;}
}
