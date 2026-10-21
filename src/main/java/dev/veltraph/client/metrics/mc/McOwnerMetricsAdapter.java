package dev.veltraph.client.metrics.mc;

import dev.veltraph.client.metrics.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.foraging.module.AutoForagingModule;
import dev.veltraph.client.galatea.GalateaForagingModule;
import dev.veltraph.client.combat.slayer.SlayerModule;
import dev.veltraph.client.combat.ghost.GhostGrindModule;
import dev.veltraph.client.combat.grind.ZealotRangedModule;
import dev.veltraph.client.hunting.ActiveHuntingModule;
import java.util.Optional;
import java.util.function.*;

/** Only completed public owner totals. Canonical item-role pickups have their separate native producer. */
public final class McOwnerMetricsAdapter {
    private final OwnerMetricsBindings bindings;
    public McOwnerMetricsAdapter(ModuleManager manager,Supplier<McLocalMetricsCollector> collector,BooleanSupplier enabled){this(manager,collector,enabled,false);}
    /** Active Hunting is included only after root accepts its native outcome proof and registers that owner. */
    public McOwnerMetricsAdapter(ModuleManager manager,Supplier<McLocalMetricsCollector> collector,BooleanSupplier enabled,boolean activeHuntingNativeAccepted){
        bindings=new OwnerMetricsBindings(manager,module->classify(module,activeHuntingNativeAccepted),()->{
            var current=collector.get();if(current==null)return null;
            var nativeScope=current.ownerObservationIdentity();if(nativeScope==null)return null;
            return new OwnerMetricsBindings.Sink(){
                @Override public Object identity(){return nativeScope;}
                @Override public void freshStart(String id,OwnerMetricEpochs.OwnerCounter kind,long baseline,long now){current.ownerFreshStart(id,kind,baseline,now);}
                @Override public void total(String id,OwnerMetricEpochs.OwnerCounter kind,long cumulative,long now){current.ownerTotal(id,kind,cumulative,now);}
            };
        },enabled);
    }
    public void afterFreshStart(Module owner,long now){bindings.afterFreshStart(owner,now);}
    public void poll(long now){bindings.poll(now);}
    private static Optional<OwnerMetricsBindings.Binding> classify(Module owner,boolean activeHuntingAccepted){
        if(owner instanceof AutoForagingModule module)return binding(OwnerMetricEpochs.OwnerCounter.AUTO_FORAGING_TREES,module::brokenTrees);
        if(owner instanceof GalateaForagingModule module)return binding(OwnerMetricEpochs.OwnerCounter.GALATEA_TREES,module::completedTrees);
        if(owner instanceof SlayerModule module)return binding(OwnerMetricEpochs.OwnerCounter.SLAYER_QUESTS,module::completedQuests);
        if(owner instanceof GhostGrindModule module)return binding(OwnerMetricEpochs.OwnerCounter.GHOST_KILLS,module::kills);
        if(owner instanceof ZealotRangedModule module)return binding(OwnerMetricEpochs.OwnerCounter.ZEALOT_KILLS,module::observedKills);
        if(activeHuntingAccepted&&owner instanceof ActiveHuntingModule module)return binding(OwnerMetricEpochs.OwnerCounter.ACTIVE_HUNTING_FISH,module::confirmedOutcomes);
        return Optional.empty();
    }
    private static Optional<OwnerMetricsBindings.Binding> binding(OwnerMetricEpochs.OwnerCounter kind,LongSupplier total){return Optional.of(new OwnerMetricsBindings.Binding(kind,total));}
}
