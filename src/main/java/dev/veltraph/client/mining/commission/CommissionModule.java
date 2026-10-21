package dev.veltraph.client.mining.commission;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.mining.vein.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Real installed Root owner. The existing native vein engine remains the break/wear/pickup authority. */
public final class CommissionModule extends VeinMiningModule {
    public static final String ID="commissions";
    private final Minecraft client;private final ConfigService config;private final McCommissionObservation observations;
    private final McNavigator terrain;private McSharedMovementNavigator movement;
    private final FarmServices services;
    private McCommissionObservation.Snapshot active;private CommissionWorkBinding.Binding work;
    private CommissionConfig.Stamp settings;private String reason="Idle; live claims and regional boundaries UNKNOWN";
    public CommissionModule(Minecraft client,FarmServices services,ConfigService config){
        super(ID,"Dwarven Commissions",client,services,config);this.client=client;this.config=config;this.services=services;
        observations=new McCommissionObservation(client);terrain=new McNavigator(client,services.input(),config);movement=new McSharedMovementNavigator(client,services,config);
    }
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.DWARVEN_MINES);}
    @Override public Optional<String> preStartCheck(){
        var ordinary=super.preStartCheck();if(ordinary.isPresent())return ordinary;
        try{
            if(!config.get(CommissionConfig.ENABLED))return Optional.of("Enable commissions explicitly");
            long maxAge=config.get(CommissionConfig.OBSERVATION_MAX_AGE_MS);if(maxAge<50||maxAge>5000)return Optional.of("Commission observation age must be 50–5000 ms");
            CommissionConfig.capture(config);var observed=observations.read();
            if(observed==null||!observed.actionable())return Optional.of("Fresh complete native tab commission section required; menu is readonly");
            if(CommissionWorkBinding.choose(observed.observation()).isEmpty())return Optional.of(CommissionWorkBinding.unavailable(observed.observation()));
            return Optional.empty();
        }catch(RuntimeException unavailable){return Optional.of("Native commission binding unavailable");}
    }
    @Override public void maintenanceTick(){if(!isEnabled())observations.read();}
    @Override public void onEnable(){
        settings=CommissionConfig.capture(config);active=observations.read();
        work=active==null||!active.actionable()?null:CommissionWorkBinding.choose(active.observation()).orElse(null);
        if(work==null)throw new IllegalStateException("Commission native admission changed during actual Root enable");
        observations.arm(work,active);movement=new McSharedMovementNavigator(client,services,config);super.onEnable();reason="Mining observed global ore commissions";
    }
    @Override public void onTick(){
        if(!isEnabled())return;
        try{
            if(!workCurrent()){reason=observations.latest()==null?"Native commission observation lost":CommissionWorkBinding.unavailable(observations.latest().observation());setEnabled(false);return;}
            super.onTick();
        }catch(RuntimeException unavailable){reason="Commission observation or configuration changed";setEnabled(false);}
    }
    @Override protected boolean workCurrent(){
        if(active==null||work==null||settings==null||!isEnabled()||!client.isSameThread())return false;
        if(!settings.equals(CommissionConfig.capture(config)))return false;
        var actual=observations.read();long now=System.nanoTime()/1_000_000;
        return actual!=null&&actual.actionable()&&actual.player()==active.player()&&actual.world()==active.world()
            &&actual.connection()==active.connection()&&actual.epoch()==active.epoch()
            &&actual.observation().context().equals(active.observation().context())
            &&now>=actual.observation().observedMillis()&&now-actual.observation().observedMillis()<=config.get(CommissionConfig.OBSERVATION_MAX_AGE_MS)
            &&observations.historyCurrent()&&work.current(actual.observation())&&settings.equals(CommissionConfig.capture(config))&&client.player==active.player()&&client.level==active.world()&&client.getConnection()==active.connection();
    }
    @Override protected Map<MiningMaterial,Integer> materialPriorities(){return work==null?Map.of():work.priorities();}
    @Override protected boolean taskAllows(net.minecraft.core.BlockPos cell,MiningMaterial material){return work!=null&&work.priorities().getOrDefault(material,0)>0&&workCurrent();}
    @Override protected boolean startApproach(dev.veltraph.client.world.BlockPos standing){
        return workCurrent()&&movement.startPreferLevel(terrain.position(standing),.30,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);
    }
    @Override protected WalkController.State tickApproach(){
        if(!workCurrent())return WalkController.State.BLOCKED;
        return switch(movement.tick()){case ACTIVE->WalkController.State.WALKING;case ARRIVED->WalkController.State.ARRIVED;default->WalkController.State.BLOCKED;};
    }
    @Override protected boolean approachArrived(dev.veltraph.client.world.BlockPos standing,WalkController.State state){
        if(state!=WalkController.State.ARRIVED||!workCurrent())return false;
        var goal=terrain.position(standing);var p=client.player;var motion=p.getDeltaMovement();
        return Math.hypot(p.getX()-goal.x(),p.getZ()-goal.z())<=.30&&Math.abs(p.getY()-goal.y())<.08
            &&p.onGround()&&Math.hypot(motion.x,motion.z)<.08&&terrain.standingClear(new dev.veltraph.client.world.Vec3(p.getX(),p.getY(),p.getZ()))&&workCurrent();
    }
    @Override protected boolean airborneApproachCurrent(){return movement.hasActiveLeg()&&movement.ownsCurrentGeneration();}
    @Override protected void stopApproach(){movement.stop();}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return movement.consumeExpectedTeleport(position,now);}
    @Override public void onDisable(){
        try{movement.stop();}finally{try{super.onDisable();}finally{active=null;work=null;settings=null;observations.clear();}}
    }
    /** Typed native refusal only; final store/native getters invoke no extensible provider. */
    public static boolean nativeTravelCurrent(Minecraft client,Object owner){
        if(!(owner instanceof CommissionModule actual))return true;
        try{return actual.client==client&&actual.workCurrent();}catch(RuntimeException unavailable){return false;}
    }
    public Optional<CommissionObservation> observedTasks(){return Optional.ofNullable(observations.latest()).map(McCommissionObservation.Snapshot::observation);}
    @Override public String diagnostics(){return "commissions "+reason+" | claims=UNKNOWN regionMembership=UNKNOWN profileIdentity=UNKNOWN | "+super.diagnostics();}
}
