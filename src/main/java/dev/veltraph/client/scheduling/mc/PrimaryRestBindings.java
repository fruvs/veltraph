package dev.veltraph.client.scheduling.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.fishing.module.FishingModule;
import dev.veltraph.client.fishing.FishingScheduledHooks;
import dev.veltraph.client.foraging.scheduling.ForagingScheduledPausePort;
import dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator;
import dev.veltraph.client.scheduling.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Install only the actual family ports whose native pause/rebind/resume contracts exist. */
public final class PrimaryRestBindings {
    private PrimaryRestBindings(){}
    public static void registerAll(McScriptScheduler scheduler,ModuleManager manager,ConfigService config,
                                   Minecraft client,FarmServices services,FishingModule fishing,FishingScheduledHooks fishingHooks,
                                   Predicate<FarmModule> sourceRewarpBoundary){
        for(Module owner:manager.all()){
            ScheduledSkill skill;ScheduledPausePort pause;Supplier<String> profile=owner::id;
            Supplier<String> settings;Supplier<Optional<String>> readiness;boolean boundaryCapable=false;
            BooleanSupplier boundary=()->false;
            if(owner instanceof FarmModule farm){
                skill=ScheduledSkill.FARMING;pause=new FarmScheduledPausePort(farm);
                profile=()->McFarmCycleCoordinator.profileId(farm);
                settings=()->signature(config,FarmingConfig.class);
                readiness=()->farmReadiness(farm,client);
                // Only explicit source END markers prove a source rewarp boundary.
                boundaryCapable=true;boundary=()->sourceRewarpBoundary.test(farm);
            }else if(owner==fishing){
                skill=ScheduledSkill.FISHING;var port=new FishingScheduledPausePort(fishing,fishingHooks);pause=port;
                settings=()->signature(config,FishingConfig.class,dev.veltraph.client.fishing.trash.FishingTrashConfig.class,dev.veltraph.client.fishing.offense.FishingOffenseConfig.class);
                readiness=()->port.ready()?Optional.empty():Optional.of("Fishing safe pause/native arrival boundary is not ready");
            }else if(owner instanceof dev.veltraph.client.mining.module.MiningModule mining){
                skill=ScheduledSkill.MINING;var port=new dev.veltraph.client.mining.MiningScheduledPausePort(mining);pause=port;
                profile=port::profile;settings=port::settings;readiness=port::readiness;
            }else if(owner instanceof dev.veltraph.client.foraging.module.TreeChopModule tree){
                skill=ScheduledSkill.FORAGING;var port=new ForagingScheduledPausePort(client,tree,()->scheduler.hasCapturedArrival(tree));pause=port;readiness=port::readiness;settings=()->foragingSettings(config);
            }else if(owner instanceof dev.veltraph.client.foraging.module.AutoForagingModule auto){
                skill=ScheduledSkill.FORAGING;var port=new ForagingScheduledPausePort(client,auto,()->scheduler.hasCapturedArrival(auto));pause=port;readiness=port::readiness;settings=()->foragingSettings(config);
            }else if(owner instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule replant){
                skill=ScheduledSkill.FORAGING;var port=new ForagingScheduledPausePort(client,replant,()->scheduler.hasCapturedArrival(replant));pause=port;readiness=port::readiness;settings=()->foragingSettings(config);
            }else if(owner instanceof dev.veltraph.client.galatea.GalateaForagingModule galatea){
                skill=ScheduledSkill.FORAGING;var port=new ForagingScheduledPausePort(client,galatea,()->scheduler.hasCapturedArrival(galatea));pause=port;readiness=port::readiness;settings=()->foragingSettings(config);
            }else if(owner instanceof dev.veltraph.client.combat.ghost.GhostGrindModule ghost){
                skill=ScheduledSkill.COMBAT;pause=ghost.scheduledPausePort();readiness=ghost::schedulingReadiness;settings=ghost::scheduledSignature;
            }else if(owner instanceof dev.veltraph.client.combat.grind.ZealotRangedModule ranged){
                skill=ScheduledSkill.COMBAT;pause=ranged.scheduledPausePort();readiness=ranged::schedulingReadiness;settings=ranged::scheduledSignature;
            }else if(owner instanceof dev.veltraph.client.combat.grind.MeleeGrindModule melee){
                skill=ScheduledSkill.COMBAT;pause=melee.scheduledPausePort();readiness=melee::schedulingReadiness;settings=melee::scheduledPauseSignature;
            }else if(owner instanceof dev.veltraph.client.combat.slayer.SlayerModule slayer){
                skill=ScheduledSkill.COMBAT;pause=slayer.scheduledPausePort();readiness=slayer::schedulingReadiness;settings=slayer::scheduledPauseSignature;
            }else if(owner instanceof dev.veltraph.client.hunting.ShardWorkflowModule shard){
                skill=ScheduledSkill.HUNTING;pause=new dev.veltraph.client.hunting.ShardScheduledPausePort(shard,ticket->scheduler.hasCapturedArrival(shard,ticket));
                readiness=shard::scheduledPauseReadiness;settings=()->signature(config,ShardConfig.class);
            }else continue;
            Supplier<String> familySettings=settings;
            Supplier<String> ownerSettings=()->familySettings.get()+"|"+signature(config,RotationConfig.class,TravelConfig.class);
            ScheduledSkill fixedSkill=skill;
            Supplier<ScheduledSkill> actualSkill=owner==fishing?()->config.get(FishingConfig.FISHING_WORM_HUNTING)?ScheduledSkill.HUNTING:ScheduledSkill.FISHING:()->fixedSkill;
            scheduler.register(owner,new McScriptScheduler.Binding(actualSkill,pause,profile,ownerSettings,readiness,boundaryCapable,boundary,
                    skill==ScheduledSkill.FORAGING?strictForagingReturn(client,services,config):McScriptScheduler.sameAreaPoseReturn(client,services,config)));
        }
    }
    private static McScriptScheduler.ArrivalRecovery strictForagingReturn(Minecraft client,FarmServices services,ConfigService config){
        var recovery=new dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery(client,services,config);
        return new McScriptScheduler.ArrivalRecovery(){
            public boolean begin(dev.veltraph.client.rewarp.FarmReturnPoint target,dev.veltraph.client.game.GameArea area){return recovery.start(target,area,.14);}
            public McScriptScheduler.ArrivalStatus tick(dev.veltraph.client.game.GameArea area){recovery.tick(area);return recovery.arrived(area)?McScriptScheduler.ArrivalStatus.READY:recovery.active()?McScriptScheduler.ArrivalStatus.ACTIVE:McScriptScheduler.ArrivalStatus.FAILED;}
            public boolean arrived(dev.veltraph.client.game.GameArea area){return recovery.arrived(area);}
            public void cancel(){recovery.cancel();}
            public void onServerMessage(String message){recovery.onServerMessage(message);}
            public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return recovery.consumeExpectedTeleport(position,now);}
            public String diagnostics(){return recovery.diagnostics();}
        };
    }
    private static Optional<String> farmReadiness(FarmModule farm,Minecraft client){
        if(!farm.isEnabled()||farm.isSuspendedForCycleReset()||farm.isSuspendedForRewarp())return Optional.of("Farm is held by another recovery");
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.gui.screen()!=null
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Farm native pose/menu unavailable");
        if(farm.isSuspendedForMaintenance())return farm.validatedFarmingToolSlot()==client.player.getInventory().getSelectedSlot()
                &&!client.player.getMainHandItem().isEmpty()?Optional.empty():Optional.of("Saved farming selected tool is not restored");
        return !farm.isSuspended()&&farm.selectedFarmingToolReady()&&farm.cycleEndReady()?Optional.empty():Optional.of("Farm selected tool/between-operation boundary is not ready");
    }
    private static String foragingSettings(ConfigService config){return signature(config,ForagingConfig.class,GalateaConfig.class,ForagingReplantConfig.class,RotationConfig.class,TravelConfig.class);}
    private static String signature(ConfigService config,Class<?>... holders){
        var values=new TreeMap<String,String>();for(var key:PersistableKeys.collect(List.of(holders)))
            if(!key.name().equals("ROTATION_MOUSE_SENSITIVITY"))values.put(key.name(),String.valueOf(config.get(key)));
        values.put(SchedulingConfig.TIMER_ENABLED.name(),String.valueOf(config.get(SchedulingConfig.TIMER_ENABLED)));
        values.put(SchedulingConfig.TIMER_DURATION.name(),String.valueOf(config.get(SchedulingConfig.TIMER_DURATION)));
        values.put(SchedulingConfig.TIMER_UNIT.name(),String.valueOf(config.get(SchedulingConfig.TIMER_UNIT)));
        return values.toString();
    }
}
