package dev.veltraph.client.combat.ghost;

import java.util.EnumMap;
import java.util.Map;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.*;

/** Actual canonical Root/parent/settings/scheduler observations; never starts a helper in this read-only guard. */
public final class McGhostSourceGuardSnapshot {
    private final Minecraft client;private final GhostGrindModule parent;private final Object requestedOwner;
    private final GhostSourceOwnedParentArea.Stamp stamp;private final int tick;
    private final boolean autoSell,pests,visitors,traps;private final GameArea area;
    private final EnumMap<Role,Result> results=new EnumMap<>(Role.class);private final ConfigService config;
    private McGhostSourceGuardSnapshot(Minecraft client,GhostGrindModule parent,Object owner,GhostSourceOwnedParentArea.Stamp stamp){
        this.client=client;this.parent=parent;requestedOwner=owner;this.stamp=stamp;tick=client.player.tickCount;config=VeltraphBootstrap.config();
        autoSell=config.get(MiscConfig.AUTO_SELL_ENABLED);pests=config.get(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY);
        visitors=config.get(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_VISITOR_INSTANTLY);traps=config.get(PestConfig.COLLECT_PEST_TRAPS);
        var context=VeltraphBootstrap.gameContext();area=context==null?GameArea.UNKNOWN:context.current().area();
        for(var role:Role.values())results.put(role,GhostSourceGuardDeclines.ordinary(role,autoSell,pests,visitors,traps,area));
        var scheduler=VeltraphBootstrap.scriptScheduler();
        results.put(Role.SCHEDULED_REST,scheduler==null?Result.UNKNOWN:scheduler.sourceGhostSearchGuard(parent));
    }
    public static McGhostSourceGuardSnapshot observe(Minecraft client,GhostGrindModule parent,Object owner){
        if(client==null||!client.isSameThread()||parent==null||VeltraphBootstrap.config()==null)throw new IllegalStateException("Actual Root guard context required");
        var stamp=parent.sourceParentAreaStamp(owner).orElseThrow(()->new IllegalStateException("Actual canonical owned Ghost parent required"));
        return new McGhostSourceGuardSnapshot(client,parent,owner,stamp);
    }
    public boolean current(){var now=parent.sourceParentAreaStamp(requestedOwner).orElse(null);var context=VeltraphBootstrap.gameContext();
        return client.isSameThread()&&now!=null&&stamp.same(now)&&client.player.tickCount==tick
                &&autoSell==config.get(MiscConfig.AUTO_SELL_ENABLED)&&pests==config.get(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_PESTS_INSTANTLY)
                &&visitors==config.get(GhostGrindConfig.GHOST_SOURCE_ACTIVATE_VISITOR_INSTANTLY)&&traps==config.get(PestConfig.COLLECT_PEST_TRAPS)
                &&context!=null&&context.current().area()==area;}
    public Map<Role,Result> results(){if(!current())throw new IllegalStateException("Root guard observation changed");return Map.copyOf(results);}
    public Result beforeSearch(){
        if(!current())return Result.UNKNOWN;
        for(var role:Role.values()){
            Result value=results.get(role);
            if(role==Role.SCHEDULED_REST){var scheduler=VeltraphBootstrap.scriptScheduler();value=scheduler==null?Result.UNKNOWN:scheduler.sourceGhostSearchGuard(parent);}
            if(!current()||value==null||value==Result.UNKNOWN)return Result.UNKNOWN;
            if(value==Result.CLAIMED)return Result.CLAIMED;
        }
        return Result.UNHANDLED;
    }
}
