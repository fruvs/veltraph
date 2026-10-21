package dev.veltraph.client.garden.admission.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.admission.*;
import dev.veltraph.client.garden.jacob.JacobContestObservation;
import dev.veltraph.client.garden.pests.PestAutoAdmissionSignals;
import dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule;
import dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.mc.*;
import dev.veltraph.client.scheduling.SourceScheduleRange;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Tick-owned source admission, using existing registered helpers and their guarded deferred lease. */
public final class McGardenThresholdAdmission {
    private final Minecraft client;private final Supplier<ConfigService> config;private final ModuleManager modules;
    private final FarmMaintenanceCoordinator maintenance;private final GameContextService context;
    private final java.util.SplittableRandom random=new java.util.SplittableRandom();
    private Object player,world,connection;private FarmModule farm;private String profile;
    private FarmMaintenanceCoordinator.Participant selected;private FarmMaintenanceLease.Token token;
    private GardenAdmissionPolicy policy;private String status="Automatic Garden admission has no authentic row mapping";
    public McGardenThresholdAdmission(Minecraft client,Supplier<ConfigService> config,ModuleManager modules,FarmMaintenanceCoordinator maintenance,GameContextService context){
        this.client=Objects.requireNonNull(client);this.config=Objects.requireNonNull(config);this.modules=Objects.requireNonNull(modules);this.maintenance=Objects.requireNonNull(maintenance);this.context=Objects.requireNonNull(context);
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Garden admission requires client thread");}
    /** Called before actual deferred activation as well as after higher-priority dispatch opportunities. */
    public void tick(long tick,boolean exclusive,List<String> tab,List<String> sidebar,Function<Module,Boolean> guardedStart){
        thread();ConfigService store=config.get();
        if(token!=null){
            if(!ownedContext()||exclusive||policy==null||!policy.current(store)){
                maintenance.cancel(token,"Automatic Garden admission policy or context revoked");clear();return;
            }
            if(maintenance.tokenFor(selected)!=token){clear();return;}
            if(maintenance.state()!=FarmMaintenanceLease.State.PREPARED){status="Actual Garden maintenance lease committed";return;}
            if(!maintenance.preparedActivationValid(selected,token)){maintenance.cancel(token,"Automatic Garden prepared reservation changed");clear();}
            return;
        }
        if(exclusive||tick<0||maintenance.active()||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return;
        var game=context.current();var controllers=modules.enabledControllers();
        if(!game.onHypixel()||!game.inSkyBlock()||game.area()!=GameArea.GARDEN||controllers.size()!=1||!(controllers.getFirst() instanceof FarmModule active)||active.isSuspended())return;
        var stamp=GardenAdmissionPolicy.capture(store).orElse(null);if(stamp==null){status="Configuration mutation evidence is unavailable";return;}
        GardenAdmissionBindings bindings;
        try{bindings=GardenAdmissionBindings.read(store.get(GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS));}
        catch(IllegalArgumentException invalid){status="Unsupported automatic admission: authentic native row/capability mapping is missing";return;}
        var decision=decision(store,bindings,tab,sidebar);
        status="Garden admission: "+decision.family()+" / "+decision.state()+" / "+decision.predicate();
        if(decision.state()!=GardenAdmissionSelection.State.ADMIT)return;
        Module wanted=switch(decision.family()){
            case VISITOR->modules.get(McVisitorCycleModule.ID).orElse(null);
            case PEST->modules.get(McPestTrackedMaintenanceModule.ID).orElse(null);
            case NONE->null;
        };
        if(wanted==null||wanted.isEnabled()||!(wanted instanceof FarmMaintenanceCoordinator.Participant participant)
                ||!(wanted instanceof McVisitorCycleModule||wanted instanceof McPestTrackedMaintenanceModule)||maintenance.startEligibility(participant).isPresent())return;
        player=client.player;world=client.level;connection=client.getConnection();farm=active;profile=McFarmCycleCoordinator.profileId(active);policy=stamp;selected=participant;
        if(!policy.current(config.get())||!ownedContext()){clear();return;}
        boolean reserved=guardedStart.apply(wanted);token=maintenance.tokenFor(participant);
        if(!reserved||token==null||!policy.current(config.get())||!ownedContext()
                ||maintenance.state()!=FarmMaintenanceLease.State.PREPARED||!maintenance.preparedActivationValid(participant,token)){
            if(token!=null)maintenance.cancel(token,"Automatic Garden admission did not reserve its exact registered parent");clear();return;
        }
        status="Automatic Garden admission reserved; waiting for actual deferred lease commit";
    }
    private boolean ownedContext(){return farm!=null&&farm.isEnabled()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.player!=null&&client.player.isAlive()&&context.current().onHypixel()&&context.current().inSkyBlock()&&context.current().area()==GameArea.GARDEN
            &&Objects.equals(profile,McFarmCycleCoordinator.profileId(farm))&&modules.get(selected.id()).orElse(null)==selected;}
    /** Exact automatic PREPARED pair is rechecked inside the final existing guarded activation callback. */
    public boolean permitsDeferred(Module module,List<String> tab,List<String> sidebar){
        thread();if(module!=selected||token==null)return true;
        ConfigService store=config.get();
        if(policy==null||!policy.current(store)||!ownedContext()||maintenance.tokenFor(selected)!=token||!maintenance.preparedActivationValid(selected,token))return false;
        try{
            var bindings=GardenAdmissionBindings.read(store.get(GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS));
            var fresh=decision(store,bindings,tab,sidebar);
            var family=module instanceof McVisitorCycleModule?GardenAdmissionSelection.Family.VISITOR:GardenAdmissionSelection.Family.PEST;
            return GardenAdmissionSelection.sameAdmittedFamily(family,fresh);
        }catch(IllegalArgumentException invalid){return false;}
    }
    /** Native operands and RNG below the first admitted family are never evaluated. */
    private GardenAdmissionSelection.Decision decision(ConfigService store,GardenAdmissionBindings bindings,List<String> tab,List<String> sidebar){
        boolean contest=JacobContestObservation.read(sidebar).active();
        return GardenAdmissionSelection.choose(store.get(GardenAdmissionConfig.AUTOSTART_VISITOR_DURING_FARMING),
                ()->GardenThresholdAdmission.visitorLazy(bindings.visitorCount(sidebar),bindings.visitorReady(sidebar),
                    ()->SourceScheduleRange.parse(store.get(GardenAdmissionConfig.ACTIVATE_VISITOR_THRESHOLD)).sample(random),
                    true,()->bindings.visitorCapability(sidebar,tab),contest,store.get(GardenAdmissionConfig.DO_VISITORS_IN_CONTEST_2)),
                store.get(GardenAdmissionConfig.ACTIVATE_VISITOR_INSTANTLY),
                ()->{var count=bindings.pests()==null?java.util.OptionalInt.empty():PestAutoAdmissionSignals.read(bindings.pests(),tab,sidebar);
                    return GardenThresholdAdmission.pestLazy(count.isPresent()?count.getAsInt():null,
                        ()->SourceScheduleRange.parse(store.get(GardenAdmissionConfig.ACTIVATE_PEST_THRESHOLD)).sample(random),true,
                        store.get(GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING),()->client.player.getAbilities().mayfly,contest,
                        store.get(GardenAdmissionConfig.DO_PESTS_IN_CONTEST_2));},
                store.get(GardenAdmissionConfig.ACTIVATE_PESTS_INSTANTLY));
    }
    private void clear(){player=world=connection=null;farm=null;profile=null;selected=null;token=null;policy=null;}
    public String status(){return status;}
}
