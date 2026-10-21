package dev.veltraph.client.combat.slayer.tara;

import dev.veltraph.client.combat.grind.MeleeGrindModule;
import dev.veltraph.client.combat.slayer.*;
import dev.veltraph.client.config.*;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Bound to one existing enabled Slayer child. Both frozen ports retain their independent native custody. */
public final class McTaraParent implements MeleeGrindModule.OwnedMechanic {
    private final Minecraft client;private final ConfigService config;
    private final McTaraWaitingWhip waiting;private final McTaraSoulWhip eggs;
    private final TaraParentCycle cycle=new TaraParentCycle();
    private TaraConfig.Profiles profiles;private String signature="";private MeleeGrindModule child;private String failure="";
    private final Set<UUID> completedObjectives=new HashSet<>();private long killingStarted;private boolean awaitReadyReset;
    public McTaraParent(Minecraft client,ConfigService config,FarmServices services){this.client=client;this.config=config;waiting=new McTaraWaitingWhip(client,services);eggs=new McTaraSoulWhip(client,services);}
    public Optional<String> configure(MeleeGrindModule owner){
        stop();cycle.reset();completedObjectives.clear();awaitReadyReset=false;failure="";child=owner;signature=TaraConfig.signature(config);
        if(!config.get(TaraConfig.TARA_USE_SOUL_WHIP)){profiles=null;return Optional.empty();}
        var error=TaraConfig.validation(config);if(error.isPresent())return error;
        profiles=TaraConfig.profiles(config);waiting.reset();eggs.reset();waiting.profile(profiles.waiting());eggs.profile(profiles.eggs());eggs.preparedBy(waiting);
        return eggs.validation();
    }
    public boolean configured(){return profiles!=null;}
    public boolean active(){return cycle.pending();}
    public boolean borrowing(){return waiting.pending()||eggs.pending();}
    public boolean suppresses(SlayerObjectives.Task task){return configured()&&task!=null&&task.kind()==SlayerObjectives.Kind.EGGS;}
    public net.minecraft.world.entity.Entity aimTarget(LivingEntity boss,SlayerObjectives.Task task){return cycle.phase()==TaraParentCycle.Phase.KILLING&&task!=null&&task.target()!=null?task.target():boss;}
    public boolean failed(){return !failure.isEmpty()||cycle.phase()==TaraParentCycle.Phase.FAILED;}
    public String status(){return failed()?failure:cycle.phase()+" | "+(cycle.phase()==TaraParentCycle.Phase.KILLING?eggs.status():waiting.status());}
    public int completed(){return cycle.completed();}
    public double range(){return profiles==null?0:profiles.eggs().nativeRayLimit();}
    public boolean waitingForWhip(){return cycle.phase()!=TaraParentCycle.Phase.KILLING;}
    public boolean wants(LivingEntity boss,SlayerObjectives.Task task){
        if(!configured())return false;if(active())return true;
        if(task!=null&&!task.removed()&&completedObjectives.contains(task.identity()))return false;
        if(SlayerSignals.family(boss.getName().getString())!=SlayerSignals.Family.SPIDER&&SlayerEntityOwnership.name(client,boss,SlayerEntityOwnership.visible(client),name->SlayerSignals.family(name)==SlayerSignals.Family.SPIDER).isEmpty())return false;
        boolean ready=SlayerEntityOwnership.visible(client).stream().anyMatch(e->e instanceof ArmorStand&&e.isAlive()&&e.hasCustomName()
                &&e.getName().getString().equals(profiles.waiting().exactReadyPlate())&&Math.abs(e.getX()-boss.getX())<=5&&Math.abs(e.getZ()-boss.getZ())<=5&&e.getY()>=boss.getY()&&e.getY()<=boss.getY()+10);
        if(awaitReadyReset){if(!ready)awaitReadyReset=false;return false;}return ready;
    }
    public boolean tick(MeleeGrindModule owner,LivingEntity boss,SlayerObjectives.Task task,boolean safety,long now){
        if(owner!=child||!owner.isEnabled()||!signature.equals(TaraConfig.signature(config)))return fail("Tara parent child/config ownership changed");
        if(!active()&&!cycle.acquire(owner,boss))return fail("Tara parent phase acquisition failed");
        if(!cycle.owns(owner,boss))return fail("Tara selected native boss or enabled child changed");
        if(cycle.phase()==TaraParentCycle.Phase.WAITING){
            waiting.tick(boss,safety,now);if(waiting.failed())return fail(waiting.status());
            if(waiting.complete()){
                if(!cycle.waitingEffect(owner,boss,true))return fail("Tara WAITING effect did not belong to this phase");
                killingStarted=now;
            }
            return true;
        }
        if(safety)return fail("Tara native phase interrupted by safety priority");
        if(task==null||task.kind()!=SlayerObjectives.Kind.EGGS||!task.actionable()||task.removed()){
            if(now-killingStarted>=profiles.eggs().timeoutMs())return fail("Tara actual egg group admission timed out");
            // A removed task must still reach an already armed egg port to verify exact native removal.
            if(!eggs.pending())return true;
        }
        var result=eggs.tick(boss,task,safety,now);
        if(result.failed())return fail(result.status());
        if(result.completed()){
            if(!cycle.eggEffect(owner,boss,true))return fail("Tara exact egg effect did not belong to this child");
            completedObjectives.add(task.identity());awaitReadyReset=true;
            // Vanilla Bow damage/ammo changed the old snapshot. The next WAITING cycle starts fresh.
            waiting.reset();eggs.reset();waiting.profile(profiles.waiting());eggs.profile(profiles.eggs());eggs.preparedBy(waiting);
        }
        return true;
    }
    public void message(String line,long now){if(active())waiting.message(line,now);}
    private boolean fail(String reason){failure=reason;cycle.fail();waiting.stop();eggs.stop();return true;}
    public void stop(){waiting.stop();eggs.stop();cycle.fail();}
}
