package dev.veltraph.client.hunting;

import dev.veltraph.client.config.*;
import net.minecraft.core.component.DataComponents;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.Pattern;

/** Active source fish hunting: native movement/aim/right-use, Cod tactic and authoritative outcomes. */
public final class ActiveHuntingModule extends AbstractModule {
    public static final String ID="hunting_active";
    public enum Phase { IDLE, ROUTE, LOCATE, APPROACH, ACTION, WAIT_DAMAGE, WAIT_OUTCOME, RETURN, RESTORE, COMPLETE, FAILED, CANCELLED }
    private final Minecraft client;private final ConfigService config;private final FarmServices services;private final McNavigator navigator;private final dev.veltraph.client.navigation.mc.McScopedWaterTravel waterTravel;private final HuntingPathCost pathCost;private final McSharedMovementNavigator transit;private boolean sharedTransit;
    private final double aimUpper=java.util.concurrent.ThreadLocalRandom.current().nextDouble(.78,.84),aimLower=java.util.concurrent.ThreadLocalRandom.current().nextDouble(.18,.26);
    private HuntingProfile profile;private String signature,reason="Idle",runFailure="";private Phase phase=Phase.IDLE;
    private Object player,world,connection;private Vec3 anchor;private float savedYaw,savedPitch;private int savedSlot;
    private NativeStackIdentity.Snapshot originalTool;private final Map<Integer,NativeStackIdentity.Snapshot> tools=new HashMap<>();
    private final Map<UUID,Long> blacklist=new HashMap<>();private final Map<String,Integer> spent=new HashMap<>();
    private final Set<UUID> priorDrops=new HashSet<>();
    private LivingEntity target;private HuntingProfile.Target spec;private UUID targetUuid;private final HuntingTactics.CodSpin spin=new HuntingTactics.CodSpin();private final HuntingMotion motion=new HuntingMotion();
    private int routeIndex,actions,outcomes,failures,hits,dropBefore,costBefore,sampledTargetTick;private long started,targetStarted,phaseStarted,lastUse,nextUse,nextPath,lastTick;
    private float healthBefore;private boolean pathActive,receipt,removed,sawDrop,paidRecorded;private Vec3 pathGoal,deathPosition,lastPredictedAim;
    private final HuntingAimHold aimHold=new HuntingAimHold();private boolean holdReapproachPending;
    private int flightDenials,sourcePoseWaits;private String flightReason="";private boolean flightRepositioning;
    private final HuntingDispatchFreshness dispatchFreshness=new HuntingDispatchFreshness();private int dispatchTimingWaits;
    public enum FlightRole { CURRENT_CAMERA, CURRENT_SOURCE_AIM, PLANNED_SOURCE_AIM }
    public record FlightDispatchEvidence(FlightRole directionRole,long clock,long observedAtMillis,long playerTick,int targetTick,UUID targetUuid,String pose,float eyeHeight,Vec3 eye,float yaw,float pitch,float cameraYaw,float cameraPitch,
        Vec3 shooterMotion,Vec3 clientResidualMotion,Vec3 previousPlayerPosition,Vec3 launchDirection,int observationLagBudget,Vec3 renderedPosition,HuntingFlight.Box renderedBox,Vec3 sourceAim,Vec3 renderedMotion,
        Vec3 receivedPosition,HuntingFlight.Box receivedBox,Vec3 receivedMotion,String obstruction,String renderedObstruction,String receivedObstruction,HuntingFlight.Check renderedGate,HuntingFlight.Check receivedGate) {}
    private volatile FlightDispatchEvidence lastFlightDispatch;
    private volatile FlightDispatchEvidence checkedFlight,checkedSourceFlight;
    private boolean castCheckedThisFrame;
    public record DecisionEvidence(long serial,long clock,String decision,Vec3 player,String pose,Vec3 sourceAim,Vec3 goal,String navigation,String candidates,FlightDispatchEvidence flight) {}
    private volatile List<DecisionEvidence> decisions=List.of();private long decisionSerial,lastDecisionAt;private String candidateDiagnostics="";
    private Vec3 flightGoalTarget;
    public ActiveHuntingModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Active named fish hunting",ModuleCategory.HUNTING);this.client=client;this.config=config;this.services=services;navigator=new McNavigator(client,services.input(),config);waterTravel=new dev.veltraph.client.navigation.mc.McScopedWaterTravel(client,services,config);pathCost=new HuntingPathCost(client,navigator);transit=new McSharedMovementNavigator(client,services,config);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){try{return Optional.of(HuntingProfile.parse(config.get(HuntingConfig.HUNTING_ACTIVE_PROFILE)).area());}catch(RuntimeException invalid){return Optional.empty();}}
    @Override public Optional<String> preStartCheck(){
        try{
            if(!config.get(HuntingConfig.HUNTING_ACTIVE_ENABLED))return Optional.of("Active hunting requires explicit opt-in");
            var parsed=HuntingProfile.parse(config.get(HuntingConfig.HUNTING_ACTIVE_PROFILE));
            if(client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||!client.player.isAlive()||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Closed healthy native hunting context required");
            if(client.player.isPassenger())return Optional.of("Declared hunting flight requires an unmounted native player");
            if(!client.level.dimension().identifier().toString().equals(parsed.dimension())||!parsed.inside(point(client.player.position())))return Optional.of("Hunting start is outside configured source region/dimension");
            NativeStackIdentity.capture(client.player.getInventory().getItem(services.inventory().selectedSlot()),client.level.registryAccess());
            for(var entry:parsed.targets()){
                if(entry.flight()==null)return Optional.of("Configure explicit hunting flight protocol; live Net physics are unknown: "+entry.id());
                if(entry.flight().maxObservationLagTicks()<0)return Optional.of("Configure explicit hunting flight protocol observation-lag budget: "+entry.id());
                if(!item(client.player.getInventory().getItem(entry.slot()),entry.tool()))return Optional.of("Exact configured hunting tool absent: "+entry.id());
                NativeStackIdentity.capture(client.player.getInventory().getItem(entry.slot()),client.level.registryAccess());
            }
            return Optional.empty();
        }catch(RuntimeException invalid){return Optional.of("Configure active hunting profile: "+invalid.getMessage());}
    }
    @Override public void onEnable(){
        var error=preStartCheck();if(error.isPresent()){player=world=connection=null;phase=Phase.FAILED;reason=error.get();setEnabled(false);return;}
        profile=HuntingProfile.parse(config.get(HuntingConfig.HUNTING_ACTIVE_PROFILE));signature=config.get(HuntingConfig.HUNTING_ACTIVE_PROFILE);
        player=client.player;world=client.level;connection=client.getConnection();anchor=position();savedYaw=client.player.getYRot();savedPitch=client.player.getXRot();savedSlot=services.inventory().selectedSlot();
        originalTool=NativeStackIdentity.capture(client.player.getInventory().getItem(savedSlot),client.level.registryAccess());tools.clear();for(var entry:profile.targets())tools.put(entry.slot(),NativeStackIdentity.capture(client.player.getInventory().getItem(entry.slot()),client.level.registryAccess()));
        spent.clear();blacklist.clear();actions=outcomes=failures=routeIndex=0;target=null;spec=null;runFailure="";started=lastTick=services.clock().nowMillis();lastUse=nextUse=nextPath=0;lastPredictedAim=null;spin.reset();release();go(profile.route().isEmpty()?Phase.LOCATE:Phase.ROUTE);
        flightDenials=dispatchTimingWaits=0;flightReason="";flightRepositioning=false;dispatchFreshness.reset();lastFlightDispatch=checkedFlight=null;
        decisions=List.of();decisionSerial=lastDecisionAt=0;candidateDiagnostics="";sourcePoseWaits=0;aimHold.reset();holdReapproachPending=false;checkedSourceFlight=null;
    }
    @Override public void onDisable(){boolean waterClean=!waterTravel.lifecycleRefused()||waterTravel.parentLifecycleCurrent(this,false);try{if(waterClean)release();}catch(dev.veltraph.client.navigation.WaterPublicationRevokedException refused){waterClean=false;}if(phase!=Phase.COMPLETE&&phase!=Phase.FAILED){phase=Phase.CANCELLED;reason="Manual/foreign owner stopped";}if(waterClean)restoreSelection();target=null;spec=null;}
    @Override public void onTick(){try{tickWaterPublicationFence();}catch(dev.veltraph.client.navigation.WaterPublicationRevokedException refused){pathActive=false;waterTravel.refuseLifecycle();reason="WATER publication custody refused; newer actor preserved";}}
    private void tickWaterPublicationFence(){
        if(!isEnabled())return;
        if(waterTravel.lifecycleRefused()){if(waterTravel.parentLifecycleCurrent(this,false))finish(Phase.FAILED,"Original WATER cleanup refused");return;}
        long now=services.clock().nowMillis();
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.player==null||!client.player.isAlive()||client.player.isPassenger()||!config.get(HuntingConfig.HUNTING_ACTIVE_ENABLED)||!Objects.equals(signature,config.get(HuntingConfig.HUNTING_ACTIVE_PROFILE))||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty()){
            finish(Phase.CANCELLED,"Native hunting owner/config/menu changed");return;
        }
        if(now<lastTick){finish(Phase.FAILED,"Hunting clock moved backwards");return;}lastTick=now;
        if(now-started>profile.runMs()&&phase!=Phase.RETURN&&phase!=Phase.RESTORE)beginReturn("Run deadline reached");
        if(!profile.inside(point(client.player.position()))){finish(Phase.FAILED,"Native movement left hunting region");return;}
        if(phase!=Phase.RETURN&&phase!=Phase.RESTORE&&client.player.isUnderWater()&&10d*client.player.getAirSupply()/Math.max(1,client.player.getMaxAirSupply())<=profile.airReserve())beginReturn("Source breathing reserve reached");
        if(target!=null&&target.isAlive()&&target.getUUID().equals(targetUuid)&&target.tickCount!=sampledTargetTick){
            motion.sample(new Vec3(target.getX()-target.xo,target.getY()-target.yo,target.getZ()-target.zo));sampledTargetTick=target.tickCount;
        }
        switch(phase){
            case ROUTE -> route();
            case LOCATE -> locate();
            case APPROACH, ACTION -> engage(now);
            case WAIT_DAMAGE -> damage(now);
            case WAIT_OUTCOME -> outcome(now);
            case RETURN -> returning();
            case RESTORE -> restoreView();
            default -> {}
        }
    }
    private void route(){
        if(routeIndex>=profile.route().size()){release();go(Phase.LOCATE);return;}
        var node=profile.route().get(routeIndex);var dest=new Vec3(node.position().x(),node.position().y(),node.position().z());
        if(!pathActive){pathActive=node.medium()==HuntingProfile.Medium.SWIM?waterTravel.startWater(this,dest,SharedMovementPolicy.Purpose.GENERAL_TRAVEL):startExactTransit(dest);if(!pathActive){beginReturn("Source route is unreachable");return;}}
        var state=transitTick();if(state==WalkController.State.BLOCKED)beginReturn("Source route became blocked");else if(state==WalkController.State.ARRIVED){stopNavigation();pathActive=false;routeIndex++;}
    }
    private void locate(){
        long now=services.clock().nowMillis();blacklist.entrySet().removeIf(e->e.getValue()<=now);target=null;spec=null;
        double best=Double.POSITIVE_INFINITY;
        for(var entity:client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(profile.radius()))){
            var entry=matching(entity);if(entry==null||!eligible(entity))continue;
            double route=pathCost.cost(position(),approachPosition(entity));
            double score=HuntingTactics.score(client.player.distanceTo(entity),angle(entity),route,entity.getY(),client.player.hasLineOfSight(entity),profile.minimumY());
            if(score<best){target=entity;spec=entry;best=score;}
        }
        if(target==null){if(now-phaseStarted>profile.targetMs())beginReturn("No eligible source fish found");return;}
        targetUuid=target.getUUID();targetStarted=now;sampledTargetTick=-1;lastUse=0;lastPredictedAim=null;hits=0;spin.reset();motion.reset();receipt=removed=sawDrop=false;deathPosition=null;dropBefore=count(spec.drop());go(Phase.APPROACH);
        aimHold.reset();holdReapproachPending=false;dispatchFreshness.reset();var endpoint=target.getPositionCodec().getBase();dispatchFreshness.observe(client.player.tickCount,client.player.getPose().name(),client.player.getEyeHeight(),new Vec3(endpoint.x,endpoint.y,endpoint.z));
        priorDrops.clear();for(var e:client.level.getEntitiesOfClass(ItemEntity.class,client.player.getBoundingBox().inflate(profile.radius()+4)))priorDrops.add(e.getUUID());
    }
    private HuntingProfile.Target matching(LivingEntity entity){
        if(!(entity instanceof Cod||entity instanceof Salmon||entity instanceof TropicalFish))return null;
        String type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),name=ShardCatalog.clean(entity.getName().getString());
        var matches=profile.targets().stream().filter(t->t.entity().equals(type)&&t.name().matcher(name).matches()).toList();return matches.size()==1?matches.getFirst():null;
    }
    private boolean eligible(LivingEntity entity){
        return hardEligible(entity);
    }
    private boolean hardEligible(LivingEntity entity){
        return entity.isAlive()&&!entity.isRemoved()&&!blacklist.containsKey(entity.getUUID())&&profile.inside(point(entity.position()))
            &&client.level.getChunkSource().hasChunk(entity.blockPosition().getX()>>4,entity.blockPosition().getZ()>>4)
            &&client.level.getEntitiesOfClass(Player.class,entity.getBoundingBox().inflate(5),p->p!=client.player&&p.distanceTo(entity)<5).isEmpty()
            &&(!(entity instanceof net.minecraft.world.entity.Mob mob)||!mob.isLeashed());
    }
    private void engage(long now){
        if(target==null||!target.getUUID().equals(targetUuid)||matching(target)!=spec||!hardEligible(target)){failTarget("Source target disappeared or lost eligibility"+(target==null?"":" at "+target.position()+" alive="+target.isAlive()+" water="+target.isInWater()+" bounds="+profile.inside(point(target.position()))));return;}
        if(now-targetStarted>profile.targetMs()){failTarget("Target timeout");return;}
        boolean cachedWater=target.isInWater(),bodyWater=bodyWater(target);
        if(!cachedWater||!bodyWater)recordDecision("Source fish fluid observation (not terminal): cached="+cachedWater+" actualBody="+bodyWater+" box="+target.getBoundingBox()+" rendered="+target.position()+" previousNative="+new Vec3(target.xo,target.yo,target.zo)+" received="+target.getPositionCodec().getBase()+" motion="+target.getDeltaMovement());
        var endpoint=target.getPositionCodec().getBase();var freshness=dispatchFreshness.observe(client.player.tickCount,client.player.getPose().name(),client.player.getEyeHeight(),new Vec3(endpoint.x,endpoint.y,endpoint.z));
        if(!tools.get(spec.slot()).matches(client.player.getInventory().getItem(spec.slot()),client.level.registryAccess())){beginReturn("Protected hunting tool changed");return;}
        // A known terminal budget does not depend on whether another shot would be feasible.
        // WAIT_DAMAGE still owns uncertain-use acknowledgment and its physical debit exactly once.
        if(actions>=profile.maxActions()||spec.costPerUse()>0&&(count(spec.cost())<spec.costPerUse()||spent.getOrDefault(spec.id(),0)+spec.costPerUse()>spec.maxCost())){beginReturn("Known hunting action/resource cap reached");return;}
        services.inventory().selectSlot(spec.slot());
        double pull=numberLore(client.player.getInventory().getItem(spec.slot()),spec.pull()),progress=progress(target,spec.progress());
        // Source computes the Cod gate/callback counter before its final reach/angular action gate.
        boolean blocked=spin.block(target instanceof Cod,pull,progress,hits==1,now-lastUse,spec.singleHitGraceMs());
        var eye=client.player.getEyePosition();var base=sourceBaseAim(target,eye.y);var prediction=motion.predict(base);lastPredictedAim=prediction;var center=new net.minecraft.world.phys.Vec3(prediction.x(),prediction.y(),prediction.z());double distance=Math.max(eye.distanceTo(center),eye.distanceTo(new net.minecraft.world.phys.Vec3(base.x(),base.y(),base.z())));
        if(holdReapproachPending){reapproach(now,"Rejected hold pose requires actual physical reposition");if(!flightRepositioning)return;holdReapproachPending=false;}
        if(flightRepositioning){
            // A moving target may become physically interceptable before an older waypoint.
            // This uses current native pose/motion and all actual dispatch guards, not the plan.
            if(!blocked&&distance<=profile.reach()-.15&&client.player.hasLineOfSight(target)&&tryNativeCast(now,eye,center,freshness.ready()))return;
            if(flightGoalTarget!=null&&new Vec3(target.getX(),target.getY(),target.getZ()).distanceTo(flightGoalTarget)>.75){release();reapproach(now,"Pinned native target moved beyond certified goal");return;}
            if(pathActive){var state=waterTravel.tick();if(state==WalkController.State.BLOCKED){release();recordDecision("Certified approach blocked");return;}if(state!=WalkController.State.ARRIVED)return;stopNavigation();pathActive=false;recordDecision("Coarse approach arrived; actual flight pose still required");}
            if(pathGoal!=null&&huntingBodyClear(pathGoal)&&navigator.walkClear(position(),pathGoal,true)){
                aim(center);
                var v=client.player.getDeltaMovement();var fine=HuntingApproachPrecision.steer(position(),pathGoal,new Vec3(v.x,v.y,v.z),client.player.getYRot(),client.player.isInWater());
                services.input().releaseAllMovement();
                if(!fine.arrived()){services.input().setForwardHeld(fine.forward());services.input().setBackHeld(fine.back());services.input().setLeftHeld(fine.left());services.input().setRightHeld(fine.right());services.input().setJumpHeld(fine.jump());services.input().setSneakHeld(fine.sneak());recordDecision("Native fine approach to certified pose");return;}
            }
            flightRepositioning=false;services.input().releaseAllMovement();recordDecision("Fine approach complete; rechecking actual source aim/flight");
        }
        if(distance>profile.reach()-.15||!client.player.hasLineOfSight(target)){
            if(phase==Phase.ACTION)services.rotation().cancelRotation();
            goIf(Phase.APPROACH);
            if(now>=nextPath){
                Vec3 at=approachPosition(target);if(at==null){failTarget("No body-clear source approach pose near moving target");return;}
                if(!pathActive||pathGoal==null||pathGoal.distanceTo(at)>.75){stopNavigation();pathActive=waterTravel.startWater(this,at,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);pathGoal=at;nextPath=now+350;if(!pathActive){failTarget("No source approach path: "+waterTravel.status()+" goal="+at);return;}}
            }
            if(pathActive&&waterTravel.tick()==WalkController.State.BLOCKED)failTarget("Moving source approach blocked: "+waterTravel.status()+" goal="+pathGoal+" player="+client.player.position()+" fish="+target.position());return;
        }
        stopNavigation();pathActive=false;services.input().releaseAllMovement();goIf(Phase.ACTION);aim(center);
        if(blocked){reason="Source Cod spin suppressed ("+spin.blockedCallbacks()+")";return;}
        if(tryNativeCast(now,eye,center,freshness.ready()))return;
        if(castCheckedThisFrame&&checkedFlight!=null&&(!checkedFlight.renderedGate().feasible()||!checkedFlight.receivedGate().feasible()))reapproach(now,flightReason);
    }
    /** True means this frame is handled by a cast or ordinary source-aim convergence. */
    private boolean tryNativeCast(long now,net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 center,boolean fresh){
        castCheckedThisFrame=false;
        double aimAngle=Math.toDegrees(Math.acos(Math.clamp(client.player.getLookAngle().normalize().dot(center.subtract(eye).normalize()),-1,1)));
        if(now<nextUse||client.gameMode==null)return false;
        if(aimAngle>3)return holdSourceFeasiblePose(eye,center,"Native source rotation still converging");
        if(!fresh){dispatchTimingWaits++;reason="Waiting for current native packet/pose dispatch observation";return holdSourceFeasiblePose(eye,center,reason);}
        var flight=flightCheck(eye,client.player.getLookAngle(),target,true);
        castCheckedThisFrame=true;
        if(!flight.feasible()){
            flightDenials++;flightReason=flight.reason();reason="Hunting flight withheld: "+flightReason;
            recordDecision("Actual native intercept withheld: "+flightReason);
            // Within the source angle gate is not the same as having finished native rotation.
            // Keep a source-feasible physical pose rather than canceling aim and chasing a new one.
            return holdSourceFeasiblePose(eye,center,"Actual native flight still differs from source aim");
        }
        // Prediction may lie behind a wall after the native target body. The complete declared
        // trajectory already checks every loaded collider up to the spread-safe target contact.
        // Do not replace that physical ordering with an extra ray-to-prediction veto beyond contact.
        var predictedRay=client.level.clip(new net.minecraft.world.level.ClipContext(eye,center,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
        if(predictedRay.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)recordDecision("Source prediction beyond native intercept meets "+predictedRay.getBlockPos());
        aimHold.reset();holdReapproachPending=false;release();
        paidRecorded=false;healthBefore=target.getHealth();costBefore=spec.cost()==null?0:count(spec.cost());lastUse=now;nextUse=now+profile.actionDelayMs();actions++;
        go(Phase.WAIT_DAMAGE);
        lastFlightDispatch=checkedFlight;
        // Stable controller calls native RIGHT_CLICK. Never invent lasso use or write target damage.
        client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
        return true;
    }
    /** Read-only source-flight viability chooses ordinary holding; it never permits dispatch. */
    private boolean holdSourceFeasiblePose(net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 center,String why){
        var sourceFlight=flightCheck(eye,center.subtract(eye),target,FlightRole.CURRENT_SOURCE_AIM);
        if(!sourceFlight.feasible()){
            if(aimHold.active()){aimHold.reject(position());requestHoldReapproach("Current native motion/source flight lost viability");return true;}
            return false;
        }
        var direction=center.subtract(eye).normalize();double angle=Math.toDegrees(Math.acos(Math.clamp(client.player.getLookAngle().normalize().dot(direction),-1,1)));
        var decision=aimHold.observe(targetUuid,hits,client.player.getPose().name(),client.player.getEyeHeight(),position(),new Vec3(target.getX(),target.getY(),target.getZ()),angle,client.player.tickCount);
        if(decision==HuntingAimHold.Decision.MOVE_BEFORE_HOLD)return false;
        if(decision==HuntingAimHold.Decision.REAPPROACH){requestHoldReapproach("Native target/pose/hit context changed or source rotation stopped improving");return true;}
        if(pathActive)stopNavigation();pathActive=false;flightRepositioning=false;services.input().releaseAllMovement();
        goIf(Phase.ACTION);aim(center);sourcePoseWaits++;reason="Holding current source-feasible native pose: "+why;
        recordDecision(reason+" nativeYaw="+client.player.getYRot()+" nativePitch="+client.player.getXRot()+" angle="+angle+" sourceFlight="+sourceFlight);
        return true;
    }
    private void requestHoldReapproach(String why){
        stopNavigation();pathActive=false;flightRepositioning=false;services.input().releaseAllMovement();services.rotation().cancelRotation();
        holdReapproachPending=true;nextPath=Math.min(nextPath,services.clock().nowMillis());recordDecision("Releasing source pose hold: "+why);reapproach(services.clock().nowMillis(),why);
        if(flightRepositioning)holdReapproachPending=false;
    }
    /** Native water volume at the actual rendered fish body; loaded queries only, no fluid writes. */
    private boolean bodyWater(LivingEntity fish){
        var b=fish.getBoundingBox().deflate(.001);boolean wet=false;
        for(int x=(int)Math.floor(b.minX);x<=(int)Math.floor(b.maxX);x++)for(int z=(int)Math.floor(b.minZ);z<=(int)Math.floor(b.maxZ);z++)for(int y=(int)Math.floor(b.minY);y<=(int)Math.floor(b.maxY);y++){
            if(!client.level.getChunkSource().hasChunk(x>>4,z>>4))return false;
            var at=new net.minecraft.core.BlockPos(x,y,z);var fluid=client.level.getFluidState(at);
            if(fluid.is(net.minecraft.tags.FluidTags.WATER)&&y+fluid.getHeight(client.level,at)>b.minY&&y<b.maxY)wet=true;
        }return wet;
    }
    private void damage(long now){
        if(target==null){failTarget("Lost pinned action target");return;}
        int cost=spec.cost()==null?0:count(spec.cost());boolean paid=spec.costPerUse()==0||cost==costBefore-spec.costPerUse();
        if(cost>costBefore||cost<costBefore-spec.costPerUse()){beginReturn("Unexpected hunting resource delta");return;}
        if(paid&&!paidRecorded){spent.merge(spec.id(),spec.costPerUse(),Integer::sum);paidRecorded=true;}
        boolean hurt=target.getHealth()<healthBefore||spec.outcome()==HuntingProfile.Outcome.ATTRIBUTED_RECEIPT&&receipt&&!target.isAlive();
        if(paid&&hurt){hits++;if(!target.isAlive()||target.isRemoved()){removed=true;deathPosition=new Vec3(target.getX(),target.getY(),target.getZ());go(Phase.WAIT_OUTCOME);}else go(Phase.ACTION);return;}
        if(now-phaseStarted>profile.actionAckMs())failAction("No native target damage/resource acknowledgment; no retry of uncertain use");
    }
    private void outcome(long now){
        if(target!=null&&target.isAlive()){failTarget("Removed target became live");return;}
        var drops=client.level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(deathPosition.x()-4,deathPosition.y()-4,deathPosition.z()-4,deathPosition.x()+4,deathPosition.y()+4,deathPosition.z()+4),e->e.isAlive()&&!priorDrops.contains(e.getUUID())&&item(e.getItem(),spec.drop()));
        if(!drops.isEmpty())sawDrop=true;
        else if(pathActive){stopNavigation();pathActive=false;services.input().releaseAllMovement();}
        boolean sourceOutcome=spec.outcome()==HuntingProfile.Outcome.DEATH_DROP?sawDrop:receipt;
        if(removed&&sourceOutcome&&count(spec.drop())==dropBefore+spec.quantity()){
            outcomes++;target=null;spec=null;beginReturn("");return;
        }
        if(!drops.isEmpty()){
            var drop=drops.stream().min(Comparator.comparingDouble(client.player::distanceToSqr)).orElseThrow();
            var at=pickupPosition(drop);if(at==null){beginReturn("Observed source drop has no body-clear pickup pose");return;}
            if(!client.player.getBoundingBox().inflate(1,.5,1).intersects(drop.getBoundingBox())&&(!pathActive||pathGoal==null||pathGoal.distanceTo(at)>.75)){
                stopNavigation();pathActive=waterTravel.startWater(this,at,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);pathGoal=at;
                if(!pathActive){beginReturn("Observed source drop has no safe pickup path");return;}
            }
            if(pathActive&&waterTravel.tick()==WalkController.State.BLOCKED){beginReturn("Observed source drop pickup blocked");return;}
        }
        if(now-phaseStarted>profile.actionAckMs())beginReturn("Source death/drop or configured attributed receipt not acknowledged (receipt="+receipt+" drop="+sawDrop+" inventory="+count(spec.drop())+" expected="+(dropBefore+spec.quantity())+" player="+client.player.position()+" drops="+drops.stream().map(e->e.position().toString()).toList()+")");
    }
    @Override public void onServerMessage(String text){
        if(!isEnabled()||client.player!=player||client.level!=world||client.level==null||spec==null||spec.outcome()!=HuntingProfile.Outcome.ATTRIBUTED_RECEIPT||lastUse==0||spec.receipt()==null||(phase!=Phase.WAIT_DAMAGE&&phase!=Phase.WAIT_OUTCOME))return;
        var match=spec.receipt().matcher(ShardCatalog.clean(text));if(!match.matches()||!match.group(1).equals(spec.id()))return;
        // A name-only source receipt cannot claim one of two indistinguishable nearby catalog targets.
        long duplicates=client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(profile.radius())).stream().filter(e->e.isAlive()&&matching(e)==spec&&e!=target).count();
        if(duplicates==0)receipt=true;
    }
    private void failAction(String message){if(targetUuid!=null)blacklist.put(targetUuid,services.clock().nowMillis()+profile.blacklistMs());failures++;beginReturn(message);}
    private void failTarget(String message){if(targetUuid!=null)blacklist.put(targetUuid,services.clock().nowMillis()+profile.blacklistMs());failures++;target=null;spec=null;release();if(failures>=profile.maxFailures())beginReturn(message);else{reason=message;go(Phase.LOCATE);}}
    private void beginReturn(String failure){if(phase==Phase.RETURN||phase==Phase.RESTORE)return;runFailure=failure;release();target=null;spec=null;go(Phase.RETURN);}
    private void returning(){
        if(!pathActive){pathActive=client.player.isInWater()?waterTravel.startWater(this,anchor,SharedMovementPolicy.Purpose.EXACT_RETURN):startExactTransit(anchor);if(!pathActive){finish(Phase.FAILED,"Saved source return path unavailable: "+runFailure);return;}}
        var state=transitTick();if(state==WalkController.State.BLOCKED)finish(Phase.FAILED,"Saved return blocked");else if(state==WalkController.State.ARRIVED){release();restoreSelection();go(Phase.RESTORE);}
    }
    private void restoreView(){
        if(!originalTool.matches(client.player.getInventory().getItem(savedSlot),client.level.registryAccess())){finish(Phase.FAILED,"Original selected tool changed");return;}
        services.rotation().track(savedYaw,savedPitch);
        if(Math.abs(WalkController.wrap(client.player.getYRot()-savedYaw))>1||Math.abs(client.player.getXRot()-savedPitch)>1)return;
        if(!runFailure.isEmpty())finish(Phase.FAILED,runFailure);else if(outcomes>=profile.maxOutcomes())finish(Phase.COMPLETE,"Actual fish outcome and saved native return observed");else{target=null;spec=null;go(Phase.LOCATE);}
    }
    private void finish(Phase end,String message){if(waterTravel.lifecycleRefused()&&!waterTravel.parentLifecycleCurrent(this,false)){waterTravel.refuseLifecycle();reason="Foreign WATER retirement cannot finish the newer parent";return;}phase=end;reason=message;if(waterTravel.lifecycleRefused()&&!waterTravel.parentLifecycleCurrent(this,false)){waterTravel.refuseLifecycle();return;}setEnabled(false);}
    /** Region-certified varied corridors are not yet available; exact dry transit retains source bounds. */
    private boolean startExactTransit(Vec3 goal){
        waterTravel.stopChecked();transit.stop();sharedTransit=transit.startGround(goal,2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN);return sharedTransit;
    }
    private WalkController.State transitTick(){
        if(!sharedTransit)return waterTravel.tick();
        return switch(transit.tick()){case ACTIVE->WalkController.State.WALKING;case ARRIVED->WalkController.State.ARRIVED;case FAILED->WalkController.State.BLOCKED;};
    }
    private void stopNavigation(){waterTravel.stopChecked();transit.stop();sharedTransit=false;}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){
        return isEnabled()&&sharedTransit&&(phase==Phase.ROUTE||phase==Phase.RETURN)&&transit.consumeExpectedTeleport(position,now);
    }
    private void release(){stopNavigation();pathActive=false;flightRepositioning=false;if(!waterTravel.handledWaterCleanup()){services.input().releaseAllMovement();services.rotation().cancelRotation();}}
    private void restoreSelection(){if(waterTravel.lifecycleRefused()&&!waterTravel.parentLifecycleCurrent(this,true))return;if(client.player!=null&&client.player==player&&client.level!=null&&client.level==world&&client.getConnection()==connection&&originalTool!=null&&originalTool.matches(client.player.getInventory().getItem(savedSlot),client.level.registryAccess()))services.inventory().selectSlot(savedSlot);}
    private void aim(net.minecraft.world.phys.Vec3 center){var eye=client.player.getEyePosition();HuntingAim.request(services.rotation(),new Vec3(eye.x,eye.y,eye.z),new Vec3(center.x,center.y,center.z));}
    private double angle(LivingEntity e){var d=e.getEyePosition().subtract(client.player.getEyePosition());return Math.abs(WalkController.wrap((float)Math.toDegrees(Math.atan2(-d.x,d.z))-client.player.getYRot()));}
    private int count(HuntingProfile.Item value){if(value==null)return 0;int n=0;for(int i=0;i<36;i++)if(item(client.player.getInventory().getItem(i),value))n+=client.player.getInventory().getItem(i).getCount();return n;}
    private static boolean item(ItemStack s,HuntingProfile.Item value){return !s.isEmpty()&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(value.id())&&ShardCatalog.clean(s.getHoverName().getString()).equals(value.name());}
    private static double numberLore(ItemStack s,Pattern pattern){var lore=s.get(DataComponents.LORE);if(pattern==null||lore==null)return Double.NaN;double v=Double.NaN;for(var line:lore.lines()){double parsed=number(line.getString(),pattern);if(Double.isFinite(parsed)){if(Double.isFinite(v)&&v!=parsed)return Double.NaN;v=parsed;}}return v;}
    private double progress(LivingEntity fish,Pattern pattern){
        if(pattern==null)return Double.NaN;double value=Double.NaN;
        // Stable nameplate helper: armor stands inside inflated(.5,.5,.5), moved up one block.
        for(var stand:client.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,fish.getBoundingBox().inflate(.5,.5,.5).move(0,1,0),e->e.hasCustomName())){
            String text=ShardCatalog.clean(stand.getCustomName().getString());if(!text.endsWith("%"))continue;
            double next=number(text,pattern);if(Double.isFinite(next)){if(Double.isFinite(value)&&value!=next)return Double.NaN;value=next;}
        }return value;
    }
    private static double number(String value,Pattern pattern){if(pattern==null)return Double.NaN;var m=pattern.matcher(ShardCatalog.clean(value));if(!m.matches())return Double.NaN;try{return Double.parseDouble(m.group(1));}catch(RuntimeException invalid){return Double.NaN;}}
    private boolean loadedRay(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to){int n=Math.max(1,(int)Math.ceil(from.distanceTo(to)*4));for(int i=0;i<=n;i++){var p=from.lerp(to,(double)i/n);if(!client.level.getChunkSource().hasChunk((int)Math.floor(p.x)>>4,(int)Math.floor(p.z)>>4))return false;}return true;}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private Vec3 approachPosition(LivingEntity fish){
        return approachPosition(fish,false);
    }
    private Vec3 approachPosition(LivingEntity fish,boolean requireFlight){
        double y=huntingFeetY(fish.getY()-.7);
        Vec3 best=null;double cost=Double.POSITIVE_INFINITY;int bestContactTick=Integer.MAX_VALUE;
        int bodyRejected=0,rangeRejected=0,flightRejected=0,accepted=0;String bestPlan="";var rejected=new LinkedHashMap<String,Integer>();
        var centres=new ArrayList<net.minecraft.world.phys.Vec3>();centres.add(fish.position());
        if(requireFlight){var packet=fish.getPositionCodec().getBase();centres.add(packet);var entry=matching(fish);if(entry!=null&&entry.flight()!=null)centres.add(packet.add(fish.getDeltaMovement().scale((entry.flight().maxObservationLagTicks()+1)/2d)));}
        // Fish fit against edges where the native player's wider/taller body cannot. Approach a
        // real occupiable pose in reach rather than trying to stand at the fish's exact center.
        for(var centre:centres)for(double dx:new double[]{0,-.75,.75,-1.5,1.5})for(double dz:new double[]{0,-.75,.75,-1.5,1.5}){
            var candidate=new Vec3(centre.x+dx,y,centre.z+dz);
            var supported=navigator.surfacePosition(new Vec3(candidate.x(),Math.ceil(y),candidate.z()));
            if(navigator.standingClear(supported,true))candidate=supported;
            if(!profile.inside(new HuntingProfile.Point(candidate.x(),candidate.y(),candidate.z()))||!huntingBodyClear(candidate)||requireFlight&&!aimHold.candidateAllowed(candidate)){bodyRejected++;continue;}
            var eyes=new net.minecraft.world.phys.Vec3(candidate.x(),candidate.y()+client.player.getEyeHeight(),candidate.z());
            if(eyes.distanceTo(fish.getBoundingBox().getCenter())>profile.reach()-.2){rangeRejected++;continue;}
            String plan="";int contactTick=Integer.MAX_VALUE;
            if(requireFlight){
                var plannedAim=sourceAim(fish,eyes.y);var direction=new net.minecraft.world.phys.Vec3(plannedAim.x(),plannedAim.y(),plannedAim.z()).subtract(eyes);
                var check=flightCheck(eyes,direction,fish,false);if(!check.feasible()){flightRejected++;rejected.merge(check.reason(),1,Integer::sum);continue;}
                contactTick=check.contactTick();plan="eyes="+eyes+" sourceAim="+plannedAim+" flight="+check;
            }
            accepted++;
            double score=position().distanceTo(candidate)+Math.hypot(dx,dz)*.15;
            // Prefer a certified shorter native flight, then existing ordinary travel cost.
            // This changes the body approach goal, never the source aim, reach or collision oracle.
            if(contactTick<bestContactTick||contactTick==bestContactTick&&score<cost){best=candidate;cost=score;bestContactTick=contactTick;bestPlan=plan;}
        }candidateDiagnostics="body="+bodyRejected+" range="+rangeRejected+" flight="+flightRejected+" rejected="+rejected+" accepted="+accepted+" best="+best+" "+bestPlan;return best;
    }
    private Vec3 sourceBaseAim(LivingEntity fish,double eyeY){var passenger=fish.getFirstPassenger();float height=HuntingTactics.nativeAimHeight(fish.getBbHeight(),fish.getAgeScale(),passenger!=null&&passenger.getFirstPassenger()==null);double y=fish.getY()+HuntingTactics.aimHeight(height,eyeY-fish.getY(),aimUpper,aimLower);return new Vec3(fish.getX(),y,fish.getZ());}
    private Vec3 sourceAim(LivingEntity fish,double eyeY){return motion.predict(sourceBaseAim(fish,eyeY));}
    private void reapproach(long now,String why){
        if(now<nextPath)return;var at=approachPosition(target,true);nextPath=now+350;recordDecision("Flight approach search: "+why);
        if(at==null)return;pathGoal=at;flightGoalTarget=new Vec3(target.getX(),target.getY(),target.getZ());services.rotation().cancelRotation();flightRepositioning=true;goIf(Phase.APPROACH);
        if(position().distanceTo(at)>.4){pathActive=waterTravel.startWater(this,at,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);if(!pathActive){flightRepositioning=false;recordDecision("Certified pose has no native path");}}
        else pathActive=false;
    }
    private void recordDecision(String text){
        long now=services.clock().nowMillis();if(now-lastDecisionAt<350&&!decisions.isEmpty()&&decisions.getLast().decision().equals(text))return;lastDecisionAt=now;
        var next=new ArrayList<>(decisions);next.add(new DecisionEvidence(++decisionSerial,now,text,position(),client.player.getPose().name(),lastPredictedAim,pathGoal,waterTravel.status(),candidateDiagnostics,checkedFlight));if(next.size()>16)next.removeFirst();decisions=List.copyOf(next);
    }
    private Vec3 pickupPosition(ItemEntity drop){
        double y=huntingFeetY(drop.getY()-1);Vec3 best=null;double cost=Double.POSITIVE_INFINITY;
        for(double dx:new double[]{0,-.75,.75})for(double dz:new double[]{0,-.75,.75}){
            var candidate=new Vec3(drop.getX()+dx,y,drop.getZ()+dz);var supported=navigator.surfacePosition(new Vec3(candidate.x(),Math.ceil(y),candidate.z()));
            if(navigator.standingClear(supported,true))candidate=supported;
            if(!profile.inside(new HuntingProfile.Point(candidate.x(),candidate.y(),candidate.z()))||!huntingBodyClear(candidate))continue;
            var box=new net.minecraft.world.phys.AABB(candidate.x()-.3,candidate.y(),candidate.z()-.3,candidate.x()+.3,candidate.y()+client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height(),candidate.z()+.3).inflate(1,.5,1);
            if(!box.intersects(drop.getBoundingBox()))continue;double score=position().distanceTo(candidate);if(score<cost){best=candidate;cost=score;}
        }return best;
    }
    // Native swimming arrival has inertia and a wider standing body than the current pose.
    // Choose a loaded standing-clear target with real headroom; never relax actual collisions.
    private double huntingFeetY(double desired){return Math.max(profile.min().y(),Math.min(profile.max().y()-client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height()-.4,desired));}
    private boolean huntingBodyClear(Vec3 at){
        for(double dx:new double[]{-.35,0,.35})for(double dz:new double[]{-.35,0,.35})
            if(!navigator.bodyClear(new Vec3(at.x()+dx,at.y(),at.z()+dz),true)||!navigator.bodyClear(new Vec3(at.x()+dx,at.y()+.4,at.z()+dz),true))return false;
        return true;
    }
    private static HuntingProfile.Point point(net.minecraft.world.phys.Vec3 p){return new HuntingProfile.Point(p.x,p.y,p.z);}
    private HuntingFlight.Check flightCheck(net.minecraft.world.phys.Vec3 eyes,net.minecraft.world.phys.Vec3 look,LivingEntity fish,boolean currentPose){return flightCheck(eyes,look,fish,currentPose?FlightRole.CURRENT_CAMERA:FlightRole.PLANNED_SOURCE_AIM);}
    private HuntingFlight.Check flightCheck(net.minecraft.world.phys.Vec3 eyes,net.minecraft.world.phys.Vec3 look,LivingEntity fish,FlightRole role){
        boolean currentPose=role==FlightRole.CURRENT_CAMERA,currentPhysical=role!=FlightRole.PLANNED_SOURCE_AIM;
        if(!currentPose&&(!Double.isFinite(look.x)||!Double.isFinite(look.y)||!Double.isFinite(look.z)||look.lengthSqr()<1e-18))return new HuntingFlight.Check(false,"Missing finite native source flight direction",0,Double.NaN);
        var entry=matching(fish);var b=fish.getBoundingBox();var nativeMotion=client.player.getKnownMovement();
        // ServerPlayer adds accepted movement-packet position delta, not LocalPlayer's residual
        // post-friction deltaMovement. Observe the native position displacement for that protocol.
        var shooter=currentPhysical?new Vec3(client.player.getX()-client.player.xo,client.player.onGround()?0:client.player.getY()-client.player.yo,client.player.getZ()-client.player.zo):new Vec3(0,0,0);
        // Current native displacement forecasts the body; the recovered 40-sample/five-step aim is untouched.
        var displacement=new Vec3(fish.getX()-fish.xo,fish.getY()-fish.yo,fish.getZ()-fish.zo);
        var rendered=new HuntingFlight.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
        var received=fish.getPositionCodec().getBase();var nativeVelocity=fish.getDeltaMovement();
        var receivedPosition=new Vec3(received.x,received.y,received.z);var receivedMotion=new Vec3(nativeVelocity.x,nativeVelocity.y,nativeVelocity.z);
        var packetBody=rendered.move(new Vec3(received.x-fish.getX(),received.y-fish.getY(),received.z-fish.getZ()));
        final String[] obstruction={""};
        // Every declared launch uses native float yaw/pitch and Mth shootFromRotation.
        // Current source aim retains actual inherited movement; only a future candidate is stationary.
        float r=.017453292f,yaw=net.minecraft.util.Mth.wrapDegrees(currentPose?client.player.getYRot():(float)Math.toDegrees(Math.atan2(-look.x,look.z))),pitch=net.minecraft.util.Mth.wrapDegrees(currentPose?client.player.getXRot():(float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z))));
        look=new net.minecraft.world.phys.Vec3(-net.minecraft.util.Mth.sin(yaw*r)*net.minecraft.util.Mth.cos(pitch*r),-net.minecraft.util.Mth.sin(pitch*r),net.minecraft.util.Mth.cos(yaw*r)*net.minecraft.util.Mth.cos(pitch*r));
        HuntingFlight.Geometry geometry=new HuntingFlight.Geometry(){
                public boolean clear(Vec3 from,Vec3 to){
                    var a=new net.minecraft.world.phys.Vec3(from.x(),from.y(),from.z());var end=new net.minecraft.world.phys.Vec3(to.x(),to.y(),to.z());
                    if(!loadedRay(a,end)||!client.level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(a))||!client.level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(end)))return false;
                    var hit=client.level.clipIncludingBorder(new net.minecraft.world.level.ClipContext(a,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
                    if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){if(obstruction[0].isEmpty())obstruction[0]="segment from="+from+" to="+to+" block="+hit.getBlockPos()+" hit="+hit.getLocation();return false;}
                    for(var entity:client.level.getEntities(client.player,new net.minecraft.world.phys.AABB(a,end).inflate(.01),e->e!=fish&&e.isPickable()&&!e.isSpectator()))
                        if(entity.getBoundingBox().contains(a)||entity.getBoundingBox().clip(a,end).isPresent())return false;
                    return true;
                }
                public boolean clearEnvelope(HuntingFlight.Box bounds){
                    var volume=new net.minecraft.world.phys.AABB(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());
                    for(int x=(int)Math.floor(bounds.minX())>>4;x<=((int)Math.floor(bounds.maxX())>>4);x++)for(int z=(int)Math.floor(bounds.minZ())>>4;z<=((int)Math.floor(bounds.maxZ())>>4);z++)if(!client.level.getChunkSource().hasChunk(x,z))return false;
                    if(!client.level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(bounds.minX(),bounds.minY(),bounds.minZ()))||!client.level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(bounds.maxX(),bounds.maxY(),bounds.maxZ())))return false;
                    var blocks=client.level.getBlockCollisions(client.player,volume).iterator();if(blocks.hasNext()){if(obstruction[0].isEmpty())obstruction[0]="spread corridor="+bounds+" collider="+blocks.next().bounds();return false;}
                    var foreign=client.level.getEntities(client.player,volume,e->e!=fish&&e.isPickable()&&!e.isSpectator());
                    if(!foreign.isEmpty()&&obstruction[0].isEmpty()){var other=foreign.getFirst();obstruction[0]="spread corridor="+bounds+" foreign="+other.getUUID()+" type="+other.getType()+" body="+other.getBoundingBox();}
                    return foreign.isEmpty();
                }
                private HuntingFluidVolume.Cells fluidCells(){return (x,y,z)->{
                    var pos=new net.minecraft.core.BlockPos(x,y,z);boolean loaded=client.level.getChunkSource().hasChunk(x>>4,z>>4);
                    if(!loaded)return new HuntingFluidVolume.Cell(false,false,false,y,"unloaded");
                    var state=client.level.getBlockState(pos);var fluid=client.level.getFluidState(pos);boolean water=fluid.is(net.minecraft.tags.FluidTags.WATER);
                    boolean supported=!state.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)&&(fluid.isEmpty()||water&&fluid.getFlow(client.level,pos).lengthSqr()<1e-12);
                    return new HuntingFluidVolume.Cell(true,supported,water,y+fluid.getHeight(client.level,pos),state+" fluid="+fluid);
                };}
                private HuntingFluidVolume.Observation inspect(HuntingFlight.Box body){var result=HuntingFluidVolume.inspect(body,fluidCells());if(!result.supported()&&obstruction[0].isEmpty())obstruction[0]="native medium "+result.detail();return result;}
                private HuntingFlight.Box fluidBox(HuntingFlight.Box positions,boolean common){
                    var dimensions=net.minecraft.world.entity.EntityTypes.SNOWBALL.getDimensions();double h=dimensions.width()/2,top=dimensions.height()-.001;
                    return common?new HuntingFlight.Box(positions.maxX()-h+.001,positions.maxY()+.001,positions.maxZ()-h+.001,positions.minX()+h-.001,positions.minY()+top,positions.minZ()+h-.001)
                        :new HuntingFlight.Box(positions.minX()-h+.001,positions.minY()+.001,positions.minZ()-h+.001,positions.maxX()+h-.001,positions.maxY()+top,positions.maxZ()+h-.001);
                }
                public HuntingFlight.Medium medium(Vec3 at){
                    var point=new HuntingFlight.Box(at.x(),at.y(),at.z(),at.x(),at.y(),at.z());var state=inspect(fluidBox(point,false));
                    return !state.supported()?HuntingFlight.Medium.UNSUPPORTED:state.water()?HuntingFlight.Medium.WATER:HuntingFlight.Medium.AIR;
                }
                public HuntingFlight.Medium media(HuntingFlight.Box positions){
                    var possible=inspect(fluidBox(positions,false));if(!possible.supported())return HuntingFlight.Medium.UNSUPPORTED;
                    if(!possible.water())return HuntingFlight.Medium.AIR;
                    var dimensions=net.minecraft.world.entity.EntityTypes.SNOWBALL.getDimensions();
                    if(positions.maxX()-positions.minX()<dimensions.width()-.002&&positions.maxY()-positions.minY()<dimensions.height()-.002&&positions.maxZ()-positions.minZ()<dimensions.width()-.002){
                        var guaranteed=inspect(fluidBox(positions,true));if(!guaranteed.supported())return HuntingFlight.Medium.UNSUPPORTED;if(guaranteed.water())return HuntingFlight.Medium.WATER;
                    }return HuntingFlight.Medium.BOTH;
                }
            };
        var eyeVector=new Vec3(eyes.x,eyes.y,eyes.z);var lookVector=new Vec3(look.x,look.y,look.z);
        var renderedGate=HuntingFlight.check(entry==null?null:entry.flight(),eyeVector,lookVector,shooter,rendered,displacement,profile.reach(),geometry);
        String renderedObstruction=obstruction[0];obstruction[0]="";
        var receivedGate=profile.inside(new HuntingProfile.Point(received.x,received.y,received.z))
            ?HuntingFlight.check(entry==null?null:entry.flight(),eyeVector,lookVector,shooter,packetBody,receivedMotion,profile.reach(),geometry)
            :new HuntingFlight.Check(false,"Received native target endpoint leaves configured region",0,Double.NaN);
        String receivedObstruction=obstruction[0];String selectedObstruction=!renderedGate.feasible()?renderedObstruction:receivedObstruction;
        var observation=new FlightDispatchEvidence(role,services.clock().nowMillis(),System.currentTimeMillis(),client.player.tickCount,fish.tickCount,fish.getUUID(),client.player.getPose().name(),client.player.getEyeHeight(),eyeVector,yaw,pitch,client.player.getYRot(),client.player.getXRot(),shooter,
            new Vec3(nativeMotion.x,nativeMotion.y,nativeMotion.z),new Vec3(client.player.xo,client.player.yo,client.player.zo),lookVector,entry==null||entry.flight()==null?-1:entry.flight().maxObservationLagTicks(),
            new Vec3(fish.getX(),fish.getY(),fish.getZ()),rendered,lastPredictedAim,displacement,receivedPosition,packetBody,receivedMotion,selectedObstruction,renderedObstruction,receivedObstruction,renderedGate,receivedGate);
        if(currentPose)checkedFlight=observation;else if(role==FlightRole.CURRENT_SOURCE_AIM)checkedSourceFlight=observation;
        return !renderedGate.feasible()?renderedGate:receivedGate;
    }
    private void go(Phase value){phase=value;phaseStarted=services.clock().nowMillis();reason=value.name();}
    private void goIf(Phase value){if(phase!=value)go(value);}
    public Phase huntingPhase(){return phase;}
    public int confirmedOutcomes(){return outcomes;}
    public int requestedActions(){return actions;}
    public Map<String,Integer> observedCost(){return Map.copyOf(spent);}
    /** Read-only diagnostics for the native source motion prediction. */
    public Vec3 predictedAim(){return lastPredictedAim;}
    public int motionSampleCount(){return motion.sampleCount();}
    public double aimUpperFraction(){return aimUpper;}
    public double aimLowerFraction(){return aimLower;}
    public int codBlockedCallbacks(){return spin.blockedCallbacks();}
    public boolean codBypass(){return spin.bypass();}
    public int flightDenials(){return flightDenials;}
    public FlightDispatchEvidence lastFlightDispatch(){return lastFlightDispatch;}
    public FlightDispatchEvidence latestFlightObservation(){return checkedFlight;}
    public FlightDispatchEvidence latestSourceFlightObservation(){return checkedSourceFlight;}
    public List<DecisionEvidence> decisionEvidence(){return decisions;}
    @Override public String diagnostics(){return phase+": "+reason+" target="+targetUuid+" actions="+actions+" outcomes="+outcomes+" cost="+spent+" failures="+failures+" returnReason="+runFailure+" receipt="+receipt+" flightDenials="+flightDenials+" flightReason="+flightReason+" dispatchTimingWaits="+dispatchTimingWaits+" sourcePoseWaits="+sourcePoseWaits;}
}
