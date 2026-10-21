package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.garden.pests.tracker.PestTrackedBindings;
import dev.veltraph.client.garden.pests.tracker.PestPlotSignals;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;

/** Actual Root read-only nearby observation; no native input, camera, packets or cleanup producers. */
public final class McNearbyPestAdmission {
    private final Minecraft client;private final ConfigService config;private final NearbyPestTracker tracker=new NearbyPestTracker();
    public McNearbyPestAdmission(Minecraft client,ConfigService config){this.client=Objects.requireNonNull(client);this.config=Objects.requireNonNull(config);}
    private record Policy(String bindings,Map<String,Object> values,Map<String,Long> epochs,String profile) {}
    private NearbyPestTracker.Settings settings(){return new NearbyPestTracker.Settings(config.get(PestAutoAdmissionConfig.PEST_NEARBY_FOV_DEGREES),
        config.get(PestAutoAdmissionConfig.PEST_NEARBY_DWELL_TICKS),config.get(PestAutoAdmissionConfig.PEST_NEARBY_MAXIMUM_STEP));}
    private Policy policy(FarmModule farm){var values=new TreeMap<String,Object>();var epochs=new TreeMap<String,Long>();
        for(var holder:List.of(PestAutoAdmissionConfig.class,PestTrackerConfig.class,PestConfig.class,PestLoadoutConfig.class))try{for(var field:holder.getFields())if(field.get(null) instanceof ConfigKey<?> key){
            values.put(key.name(),config.get(key));long epoch=config.revision(key).orElse(-1);if(epoch<0)return null;epochs.put(key.name(),epoch);}}
        catch(IllegalAccessException invalid){return null;}
        return new Policy(config.get(PestTrackerConfig.PEST_TRACKED_BINDINGS),Map.copyOf(values),Map.copyOf(epochs),dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farm));}
    private PestTrackedBindings bindings(){return PestBindings.read(config.get(PestTrackerConfig.PEST_TRACKED_BINDINGS),PestTrackedBindings.class);}
    private PestPlotSignals.Census census(PestTrackedBindings bindings){var context=VeltraphBootstrap.gameContext();if(context==null)return null;context.resolution();var s=context.signals();return PestPlotSignals.parse(bindings.signals(),s.tabLinesRaw(),s.sidebarRowsRaw(),bindings.maxTotalPests()).orElse(null);}
    private Entity candidate(PestTrackedBindings b,int plot,FarmModule farm,long tick,Policy policy,NearbyPestTracker.Settings settings){Entity selected=null;double nearest=Double.POSITIVE_INFINITY;
        for(var e:client.level.entitiesForRendering())if(e instanceof ArmorStand stand&&stand.getHealth()>0&&e.getY()>=62&&e.getY()<92&&client.level.getEntity(e.getId())==e
            &&client.level.getChunkSource().hasChunk(e.blockPosition().getX()>>4,e.blockPosition().getZ()>>4)
            &&PestItems.entityMatches(e,b.cleaner().pest())&&GardenPlotBounds.contains(plot,e.getX(),e.getZ())&&client.player.hasLineOfSight(e)){
            double distance=client.player.getEyePosition().distanceTo(e.getEyePosition());if(distance<=b.cleaner().reach()-.5&&distance<nearest&&NearbyPestTracker.eligible(observe(farm,tick,e,policy,b,plot),settings)){selected=e;nearest=distance;}}
        return selected;
    }
    private NearbyPestTracker.Observation observe(FarmModule farm,long tick,Entity selected,Policy policy,PestTrackedBindings b,int plot){
        if(policy==null||selected==null||client.player==null||client.level==null||client.getConnection()==null)return null;
        var eye=client.player.getEyePosition();var targetEye=selected.getEyePosition();
        return new NearbyPestTracker.Observation(new NearbyPestTracker.Scope(client.player,client.level,client.getConnection(),farm,config,McOwnedRotation.authority(client),policy,plot),tick,
            new Vec3(eye.x,eye.y,eye.z),client.player.getYRot(),new NearbyPestTracker.Target(selected,selected.getId(),selected.getUUID(),b.cleaner().pest().toString(),new Vec3(targetEye.x,targetEye.y,targetEye.z)),
            client.level.getEntity(selected.getId())==selected&&selected.isAlive()&&PestItems.entityMatches(selected,b.cleaner().pest())&&client.player.hasLineOfSight(selected),b.cleaner().reach());
    }
    /** Opaque intent comes only from an actual configured native observation and consumed dwell claim. */
    static final class Admission {
        private final McNearbyPestAdmission producer;private final FarmModule farm;private final Entity entity;private final NearbyPestTracker.Claim claim;
        private final Policy policy;private final PestPlotSignals.Census census;private final PestTrackedBindings bindings;private final int plot;private final Object player,world,connection;private final long issuedNativeTick;private boolean attempted;
        private Admission(McNearbyPestAdmission producer,FarmModule farm,Entity entity,NearbyPestTracker.Claim claim,Policy policy,PestTrackedBindings bindings,int plot,PestPlotSignals.Census census){
            this.producer=producer;this.farm=farm;this.entity=entity;this.claim=claim;this.policy=policy;this.census=census;this.bindings=bindings;this.plot=plot;player=producer.client.player;world=producer.client.level;connection=producer.client.getConnection();issuedNativeTick=producer.client.player.tickCount;}
        boolean validSource(){var c=producer.client;return c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.player!=null&&c.player.isAlive()
            &&c.getCameraEntity()==c.player&&!c.isPaused()&&!c.player.isSpectator()&&!c.player.isScoping()&&!c.player.isPassenger()&&!c.player.isInLava()
            &&!c.level.tickRateManager().isEntityFrozen(c.player)&&c.player.tickCount>=issuedNativeTick&&c.player.tickCount-issuedNativeTick<=bindings.requestTimeoutTicks()
            &&c.gui.screen()==null&&c.player.containerMenu==c.player.inventoryMenu&&c.player.containerMenu.getCarried().isEmpty()
            &&VeltraphBootstrap.config()==producer.config&&Objects.equals(policy,producer.policy(farm))
            &&GardenPlotBounds.contains(plot,c.player.getX(),c.player.getZ());}
        private boolean live(){var c=producer.client;return validSource()&&Objects.equals(census,producer.census(bindings))&&c.level.getEntity(entity.getId())==entity&&entity.isAlive()
            &&entity.getId()==claim.observation().target().nativeId()&&entity.getUUID().equals(claim.observation().target().uuid())
            &&c.level.getChunkSource().hasChunk(entity.blockPosition().getX()>>4,entity.blockPosition().getZ()>>4)
            &&PestItems.entityMatches(entity,bindings.cleaner().pest())&&GardenPlotBounds.contains(plot,entity.getX(),entity.getZ())
            &&c.player.hasLineOfSight(entity)&&c.player.getEyePosition().distanceTo(entity.getEyePosition())<=bindings.cleaner().reach()-.5&&validSource();}
        private boolean freshFov(){var c=producer.client;var a=c.player.getEyePosition();var b=entity.getEyePosition();
            return NearbyPestTracker.visibleWithin(new Vec3(a.x,a.y,a.z),c.player.getYRot(),new Vec3(b.x,b.y,b.z),bindings.cleaner().reach(),c.player.hasLineOfSight(entity),producer.settings());}
        boolean preflight(McPestTrackedMaintenanceModule helper){var root=VeltraphBootstrap.moduleManager();var maintenance=VeltraphBootstrap.farmMaintenance();
            if(!live()||!freshFov()||root==null||root.get(helper.id()).orElse(null)!=helper||root.get(farm.id()).orElse(null)!=farm||!farm.isEnabled())return false;
            if(!attempted)return root.inputOwner().orElse(null)==farm&&!farm.isSuspended();
            var token=maintenance==null?null:maintenance.tokenFor(helper);
            return token!=null&&maintenance.state()==FarmMaintenanceLease.State.PREPARED&&maintenance.preparedActivationValid(helper,token)&&farm.maintenanceOwnedBy(token);
        }
        /** Capture begins only under the real helper lease; eye/range/LOS is re-read after gear/relocation. */
        Entity target(PestTrackedBindings expected){return attempted&&expected.equals(bindings)&&live()?entity:null;}
        boolean attempted(){return attempted;}
        int plot(){return plot;}
    }
    /** Called only by the normal Root dispatch boundary after all higher-priority families. */
    public boolean tick(FarmModule farm,long tick,int plot,ModuleActivationGuard guard){
        if(!config.get(PestAutoAdmissionConfig.PEST_NEARBY_ENABLED)){reset();return false;}
        boolean attemptedThisTick=false;
        try {
            var root=VeltraphBootstrap.moduleManager();var maintenance=VeltraphBootstrap.farmMaintenance();
            if(client.player==null||client.level==null||client.getConnection()==null||client.isPaused()||!client.player.isAlive()||client.player.isSpectator()
                ||client.player.isPassenger()||client.player.isInLava()||client.player.isScoping()||client.getCameraEntity()!=client.player
                ||client.level.tickRateManager().isEntityFrozen(client.player)||root==null||maintenance==null||maintenance.active()||root.get(farm.id()).orElse(null)!=farm||root.inputOwner().orElse(null)!=farm
                ||!farm.isEnabled()||farm.isSuspended()||McOwnedRotation.authority(client)!=farm){reset();return false;}
            // Retention semantics belong to the existing source parent; this full-batch nearby branch never guesses its seed.
            if(config.get(PestConfig.LEAVE_ONE_PEST)){reset();return false;}
            var b=bindings();var census=census(b);if(census==null||census.total()<1||census.plots().stream().noneMatch(p->p.plotId()==plot)){reset();return false;}var p=policy(farm);var wanted=settings();var e=candidate(b,plot,farm,tick,p,wanted);var o=observe(farm,tick,e,p,b,plot);
            var claim=tracker.observe(o,wanted).orElse(null);if(claim==null)return false;
            var selected=root.get(McPestTrackedMaintenanceModule.ID).orElse(null);if(!(selected instanceof McPestTrackedMaintenanceModule helper)||helper.isEnabled()){reset();return false;}
            var fresh=observe(farm,tick,e,policy(farm),b,plot);if(!tracker.current(claim,fresh,settings())){reset();return false;}
            var intent=new Admission(this,farm,e,claim,p,b,plot,census);if(!helper.prepareNearby(intent)){reset();return false;}
            boolean started;
            try{attemptedThisTick=true;started=guard.start(helper);}finally{intent.attempted=true;tracker.attempted(claim);}
            var token=maintenance.tokenFor(helper);
            boolean accepted=started&&token!=null&&maintenance.state()==FarmMaintenanceLease.State.PREPARED&&maintenance.preparedActivationValid(helper,token);
            if(!accepted){if(token!=null)maintenance.cancel(token,"Nearby native target reservation not proven");helper.discardNearby(intent);}
            return true; // A real attempt has priority over the other automatic pest parent this tick.
        }catch(RuntimeException invalid){reset();return attemptedThisTick;}
    }
    public void reset(){tracker.reset();}
    public String status(){return tracker.status();}
}
