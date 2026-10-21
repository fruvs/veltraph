package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.game.GameArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;

/** Mapped world-transition/area resolution and every-third reached base-event pressure adapter.
 * Native callback cadence is qualified: actual owned modern base events, not original JNI scheduling. */
public final class McGhostSourcePressureLifecycle {
    // Original pressure scalar is process-global and is not reset on Ghost disable, world change or a query.
    private static final GhostSourceCrowdPressureCounter PROCESS_COUNTER=new GhostSourceCrowdPressureCounter();
    private final Minecraft client;private final GhostSourcePressureEventCadence cadence=new GhostSourcePressureEventCadence(PROCESS_COUNTER);
    private Object player,world,connection;private int lastTick=Integer.MIN_VALUE;private boolean transitioning;
    public McGhostSourcePressureLifecycle(Minecraft client){this.client=java.util.Objects.requireNonNull(client);}
    GhostSourceCrowdPressureCounter.Observation reachedOwnedBaseEvent(GhostGrindModule parent){
        if(parent==null||!client.isSameThread()||parent.sourceParentAreaStamp(parent).isEmpty())
            throw new IllegalStateException("Actual successful owned Ghost common event required");
        // Distinct query callers cannot advance the modern callback cadence repeatedly within one tick.
        if(player==client.player&&world==client.level&&connection==client.getConnection()&&lastTick==client.player.tickCount)
            return PROCESS_COUNTER.snapshot();
        if(player!=client.player||world!=client.level||connection!=client.getConnection())transitioning=true;
        player=client.player;world=client.level;connection=client.getConnection();lastTick=client.player.tickCount;
        var service=VeltraphBootstrap.gameContext();var observed=service==null?null:service.current();
        // Source area updater clears its flag even when the parsed area equals the previous area.
        if(observed!=null&&observed.area()!=GameArea.UNKNOWN)transitioning=false;
        int near=(int)client.level.players().stream().filter(p->p instanceof RemotePlayer)
                .filter(p->p.distanceToSqr(client.player.position())<=25.0).count();
        // Actual Ghost source type is COMBAT, so FARMING+GARDEN is statically inapplicable.
        return cadence.reachedBaseEvent(false,near,transitioning?GhostSourceCrowdPressureCounter.SkipUpdate.YES:GhostSourceCrowdPressureCounter.SkipUpdate.NO);
    }
    GhostSourceCrowdPressureCounter.Observation snapshot(){return PROCESS_COUNTER.snapshot();}
}
