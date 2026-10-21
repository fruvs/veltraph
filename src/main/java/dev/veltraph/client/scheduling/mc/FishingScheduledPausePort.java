package dev.veltraph.client.scheduling.mc;
import dev.veltraph.client.fishing.FishingScheduledHooks;
import dev.veltraph.client.fishing.module.FishingModule;
import dev.veltraph.client.scheduling.ScheduledPausePort;
import java.util.Objects;
/** Exact fishing engine/token preservation; never disables/re-enables on a successful rest. */
public final class FishingScheduledPausePort implements ScheduledPausePort {
    private final FishingModule fishing;private final FishingScheduledHooks nativeHooks;
    public FishingScheduledPausePort(FishingModule fishing,FishingScheduledHooks nativeHooks){this.fishing=Objects.requireNonNull(fishing);this.nativeHooks=Objects.requireNonNull(nativeHooks);}
    public boolean ready(){return (fishing.isScheduledPaused()||fishing.safeScheduledPauseBoundary())&&nativeHooks.ready();}
    public boolean pause(Object ticket){return fishing.suspendForScheduledRest(ticket,nativeHooks);}
    public boolean owns(Object ticket){return fishing.scheduledPauseOwnedBy(ticket);}
    public boolean rebindAfterOwnedArrival(Object ticket){return fishing.rebindForScheduledRest(ticket);}
    public boolean resume(Object ticket){return fishing.resumeAfterScheduledRest(ticket);}
    public void stop(Object ticket){fishing.stopScheduledRest(ticket);}
}
