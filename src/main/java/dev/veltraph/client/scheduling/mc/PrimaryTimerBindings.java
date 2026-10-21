package dev.veltraph.client.scheduling.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator;
import dev.veltraph.client.scheduling.ScheduledSkill;
import java.util.*;
import java.util.function.Supplier;

/** Actual primary module timer metadata. This registers no rest adapters or transport exemptions. */
public final class PrimaryTimerBindings {
    private PrimaryTimerBindings(){}
    public static void registerAll(McScriptScheduler scheduler,ModuleManager manager,ConfigService config){
        for(var module:manager.all()){
            if(module.isPassiveObserver()||scheduler.registeredOwner(module))continue;
            Supplier<ScheduledSkill> skill;Class<?>[] holders;Supplier<String> profile;
            if(module instanceof FarmModule farm){skill=()->ScheduledSkill.FARMING;holders=new Class<?>[]{FarmingConfig.class};profile=()->McFarmCycleCoordinator.profileId(farm);}
            else if(module instanceof dev.veltraph.client.fishing.module.FishingModule){skill=()->config.get(FishingConfig.FISHING_WORM_HUNTING)?ScheduledSkill.HUNTING:ScheduledSkill.FISHING;holders=new Class<?>[]{FishingConfig.class};profile=()->module.id()+"|"+skill.get();}
            else if(module instanceof dev.veltraph.client.mining.module.MiningModule){skill=()->ScheduledSkill.MINING;holders=new Class<?>[]{MiningConfig.class};profile=()->module.id();}
            else if(module instanceof dev.veltraph.client.foraging.module.TreeChopModule||module instanceof dev.veltraph.client.foraging.module.AutoForagingModule||module instanceof dev.veltraph.client.galatea.GalateaForagingModule||module instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule){skill=()->ScheduledSkill.FORAGING;holders=new Class<?>[]{ForagingConfig.class,GalateaConfig.class,ForagingReplantConfig.class};profile=()->module.id();}
            else if(module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule){skill=()->ScheduledSkill.COMBAT;holders=new Class<?>[]{GhostConfig.class,GhostGrindConfig.class};profile=()->module.id();}
            else if(module instanceof dev.veltraph.client.combat.grind.ZealotRangedModule){skill=()->ScheduledSkill.COMBAT;holders=new Class<?>[]{ZealotConfig.class,CombatConfig.class};profile=()->module.id();}
            else if(module instanceof dev.veltraph.client.combat.sven.SvenParentModule){skill=()->ScheduledSkill.COMBAT;holders=new Class<?>[]{dev.veltraph.client.combat.sven.SvenConfig.class,CombatConfig.class,TravelConfig.class,RotationConfig.class};profile=()->module.id()+"|"+config.get(dev.veltraph.client.combat.sven.SvenConfig.SVEN_CAVE_TYPE).name();}
            else if(module instanceof dev.veltraph.client.combat.grind.MeleeGrindModule||module instanceof dev.veltraph.client.combat.slayer.SlayerModule){skill=()->ScheduledSkill.COMBAT;holders=module instanceof dev.veltraph.client.combat.slayer.SlayerModule?new Class<?>[]{CombatConfig.class,SlayerConfig.class,TaraConfig.class}:new Class<?>[]{CombatConfig.class,SlayerConfig.class};profile=()->module.id();}
            else if(module instanceof dev.veltraph.client.hunting.ShardWorkflowModule){skill=()->ScheduledSkill.HUNTING;holders=new Class<?>[]{ShardConfig.class};profile=()->module.id();}
            else continue;
            scheduler.registerTimer(module,skill,profile,()->signature(config,holders));
        }
    }
    private static String signature(ConfigService config,Class<?>[] holders){
        var values=new TreeMap<String,String>();
        for(var key:PersistableKeys.collect(List.of(holders))){
            // This observation is synchronized from native mouse preferences every tick.
            if(key.name().equals("ROTATION_MOUSE_SENSITIVITY"))continue;
            values.put(key.name(),String.valueOf(config.get(key)));
        }
        values.put(SchedulingConfig.TIMER_ENABLED.name(),String.valueOf(config.get(SchedulingConfig.TIMER_ENABLED)));
        values.put(SchedulingConfig.TIMER_DURATION.name(),String.valueOf(config.get(SchedulingConfig.TIMER_DURATION)));
        values.put(SchedulingConfig.TIMER_UNIT.name(),String.valueOf(config.get(SchedulingConfig.TIMER_UNIT)));
        return values.toString();
    }
}
