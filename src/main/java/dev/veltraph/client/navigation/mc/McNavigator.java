package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** Client-thread world adapter. Searches only loaded blocks and never alters terrain. */
public final class McNavigator implements GroundPathfinder.Terrain {
    private final Minecraft client;
    private final InputService input;
    private final WalkController walker;
    private final GroundPublicationActor publicationActor;
    private WaterPublicationActor waterPublicationActor;
    private boolean actorPublished;
    private final dev.veltraph.client.config.ConfigService config;
    private String reason="Idle";
    private net.minecraft.client.player.LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    private McWaterNavigator waterNavigator;
    private boolean waterMode;
    private String lastDiagnostic="idle";
    private String lastStopOperands="";
    public String lastStopOperands(){return lastStopOperands;}
    private final long dynamicSeed=java.util.concurrent.ThreadLocalRandom.current().nextLong();
    private long dynamicJourney;
    private String dynamicDiagnostic="none";
    private net.minecraft.client.multiplayer.ClientPacketListener dynamicConnection;
    private Object dynamicAuthority;
    private long dynamicEpoch;
    private boolean dynamicMode;
    private final GroundLocomotionPolicy locomotion=new GroundLocomotionPolicy();
    private boolean sprintPublished;
    private final McGroundJumpApproach jumpApproach=new McGroundJumpApproach();
    private GroundJumpObservedLanding.AirSample acceptedAir;
    private GroundDropBrakeForecast.Episode acceptedDropBrake;
    private final GroundLocomotionEvidence.Trace groundEvidence=new GroundLocomotionEvidence.Trace();
    private McCapturedProfileLocomotion capturedLocomotion;
    private boolean directionalGround;
    private String directionalGroundDisposition="ORIGINAL_POLICY";
    /** Cached source-scope observation only; no query, controller tick or publication. */
    public String cachedDirectionalGroundDisposition(){return directionalGroundDisposition;}
    private GroundInitialDirectionalFailureSnapshot initialDirectionalPlaneFailure;
    /** Retained scalar-only original initial-plane refusal; no current/clock/native query. */
    public GroundInitialDirectionalFailureSnapshot cachedInitialDirectionalPlaneFailure(){return initialDirectionalPlaneFailure;}
    private GroundDynamicPlanningFailureSnapshot dynamicPlanningFirstFailure,dynamicPlanningLatestFailure;
    public GroundDynamicPlanningFailureSnapshot cachedDynamicPlanningFirstFailure(){return dynamicPlanningFirstFailure;}
    public GroundDynamicPlanningFailureSnapshot cachedDynamicPlanningLatestFailure(){return dynamicPlanningLatestFailure;}
    private GroundDynamicStageCostSnapshot dynamicStageCostFailure;
    public GroundDynamicStageCostSnapshot cachedDynamicStageCostFailure(){return dynamicStageCostFailure;}
    private boolean dynamicStageCostUnavailable;
    public boolean cachedDynamicStageCostUnavailable(){return dynamicStageCostUnavailable;}
    private double capturedTerminalRadius=.35D;
    private CapturedProfileDiagnostics profileDiagnostics=CapturedProfileDiagnostics.inactive();
    public CapturedProfileDiagnostics profileDiagnostics(){return profileDiagnostics;}
    private GroundPublicationFailureSnapshot originalAfterPublicationFailure,originalFinalAfterPublicationFailure;
    public GroundPublicationFailureSnapshot cachedOriginalAfterPublicationFailure(){return originalAfterPublicationFailure;}
    public GroundPublicationFailureSnapshot cachedOriginalFinalAfterPublicationFailure(){return originalFinalAfterPublicationFailure;}
    /** Retains first and latest cached original false-return snapshots before navigator cleanup. */
    private boolean captureOriginalAfterPublication(McCapturedProfileLocomotion exact,boolean original){
        if(!original){originalFinalAfterPublicationFailure=exact.cachedOriginalAfterPublicationFailure();
            if(originalAfterPublicationFailure==null)originalAfterPublicationFailure=originalFinalAfterPublicationFailure;}return original;
    }
    public McCapturedProfileGeometry.StationaryProofCounts cachedNativeStationaryProofCounts(){return McCapturedProfileGeometry.cachedStationaryProofCounts();}
    private DynamicRouteConfigStamp dynamicConfig;
    // Receipt custody only. Never part of route progress and never consulted by ordinary ticks.
    private final java.util.concurrent.atomic.AtomicReference<MaintenanceBinding> maintenanceReceipt=new java.util.concurrent.atomic.AtomicReference<>();
    public McNavigator(Minecraft client,InputService input) { this(client,input,new dev.veltraph.client.config.MapConfigService()); }
    public McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config) { this(client,input,config,new WalkController()); }
    /** Reproducible per-route geometry; identical collision and ownership gates. */
    public McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config,long routeSeed) { this(client,input,config,new WalkController(routeSeed)); }
    /** Dry ground only. Input must be the same actor's individually guarded lease adapter. */
    public McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config,GroundPublicationActor actor){
        this(client,GroundPublicationInputs.require(input,actor),config,new WalkController(),actor);
    }
    /** WATER-only construction uses the exact issued actor/input pair; dry actor keeps denying water. */
    public McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config,WaterPublicationActor actor){
        this(client,WaterPublicationInputs.require(input,actor),config,new WalkController(),null);waterPublicationActor=actor;
    }
    private McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config,WalkController walker){this(client,input,config,walker,null);}
    private McNavigator(Minecraft client,InputService input,dev.veltraph.client.config.ConfigService config,WalkController walker,GroundPublicationActor actor){
        this.client=client;this.input=input;this.config=config;this.walker=walker;this.publicationActor=actor;
    }
    private static net.minecraft.core.BlockPos mc(BlockPos p) {
        return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());
    }
    private boolean loaded(BlockPos p) { return client.level!=null && client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4); }
    public boolean clear(BlockPos p) {
        if (!loaded(p)) return false;
        var pos=mc(p); var state=client.level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(client.level,pos).isEmpty()
                && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
                && !state.is(Blocks.COBWEB) && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.POWDER_SNOW);
    }
    public boolean floor(BlockPos p) {
        if (!loaded(p)) return false;
        var pos=mc(p); var state=client.level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isFaceSturdy(client.level,pos,Direction.UP)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS)
                && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
    }
    private boolean dangerous(net.minecraft.world.level.block.state.BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }
    @Override public Vec3 position(BlockPos node) {
        double height=supportHeight(node.x()+.5,node.y(),node.z()+.5);
        return new Vec3(node.x()+.5,Double.isFinite(height)?height:node.y(),node.z()+.5);
    }
    @Override public Vec3 surfacePosition(Vec3 p) {
        double height=supportHeight(p.x(),GroundPathfinder.node(p).y(),p.z());
        return Double.isFinite(height)?new Vec3(p.x(),height,p.z()):p;
    }
    /** All four body corners require support; normal auto-step height differences are permitted. */
    private double supportHeight(double x,int ceiling,double z) {
        double top=Double.NaN;
        GroundSupportCornerAccumulator cornerBoxes=null;
        BlockPos previousCornerNode=null;
        for(int cornerX=0;cornerX<2;cornerX++) for(int cornerZ=0;cornerZ<2;cornerZ++) {
            double dx=cornerX==0?-.3:.3,dz=cornerZ==0?-.3:.3;
            int bx=(int)Math.floor(x+dx),bz=(int)Math.floor(z+dz);
            var node=previousCornerNode!=null&&previousCornerNode.x()==bx&&previousCornerNode.y()==ceiling-1&&previousCornerNode.z()==bz
                    ?previousCornerNode:new BlockPos(bx,ceiling-1,bz);
            previousCornerNode=node;
            if(!loaded(node)) return Double.NaN;
            var pos=mc(node);var state=client.level.getBlockState(pos);
            if(dangerous(state)) return Double.NaN;
            double corner;
            var nativeCornerShape=state.getCollisionShape(client.level,pos);
            double unitCorner=McGroundUnitCubeSupport.tryCornerHeight(nativeCornerShape,x+dx-bx,z+dz-bz,ceiling-1);
            if(unitCorner!=Double.NEGATIVE_INFINITY){
                corner=unitCorner;
            }else if(nativeCornerShape.getClass()==net.minecraft.world.phys.shapes.CubeVoxelShape.class
                    ||nativeCornerShape.getClass()==net.minecraft.world.phys.shapes.ArrayVoxelShape.class
                    ||nativeCornerShape.getClass()==net.minecraft.world.phys.shapes.SliceShape.class){
                if(cornerBoxes==null)cornerBoxes=new GroundSupportCornerAccumulator();
                cornerBoxes.reset(x+dx-bx,z+dz-bz,ceiling-1);
                nativeCornerShape.forAllBoxes(cornerBoxes::accept);
                corner=cornerBoxes.height();
            }else{
                corner=Double.NaN;
                for(var box:nativeCornerShape.toAabbs()){
                    double px=x+dx-bx,pz=z+dz-bz;
                    if(px>=box.minX && px<=box.maxX && pz>=box.minZ && pz<=box.maxZ)
                        corner=Double.isNaN(corner)?pos.getY()+box.maxY:Math.max(corner,pos.getY()+box.maxY);
                }
            }
            if(!Double.isFinite(corner) || corner>ceiling+.001 || corner<ceiling-1+.001) return Double.NaN;
            if(Double.isFinite(top)&&Math.abs(top-corner)>.6) return Double.NaN;
            top=Double.isFinite(top)?Math.max(top,corner):corner;
        }
        return top;
    }
    private boolean bodyClear(Vec3 p) {
        return spaceClear(p,.3);
    }
    /** Read-only exact collision/hazard query; water is opt-in, lava is never admitted. */
    public boolean bodyClear(Vec3 p,boolean allowWater) { return spaceClear(p,.3,allowWater); }
    /** Actual fractional body clearance shared with flight; partial blocks are tested by shape. */
    boolean spaceClear(Vec3 p,double radius) {
        return spaceClear(p,radius,false);
    }
    private boolean spaceClear(Vec3 p,double radius,boolean allowWater) {
        if(client.player==null||client.level==null)return false;
        if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))return false;
        // getDimensions(STANDING) already includes vanilla LivingEntity scale. Keep a hypothetical
        // standing body even while crouched; using decimal double 1.8 creates false ceiling overlap
        // where the authoritative float height 1.7999999523162842 ends exactly on the roof plane.
        double height=client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height();
        if(!Double.isFinite(height)||height<.01||height>4)return false;
        var box=new net.minecraft.world.phys.AABB(p.x()-radius,p.y()+.001,p.z()-radius,p.x()+radius,p.y()+height,p.z()+radius);
        double cellMinX=Math.floor(box.minX),cellMaxX=Math.floor(box.maxX);
        double cellMinY=Math.floor(box.minY),cellMaxY=Math.floor(box.maxY);
        double cellMinZ=Math.floor(box.minZ),cellMaxZ=Math.floor(box.maxZ);
        for(int x=(int)cellMinX;x<=cellMaxX;x++)
            for(int y=(int)cellMinY;y<=cellMaxY;y++)
                for(int z=(int)cellMinZ;z<=cellMaxZ;z++) {
                    var block=new BlockPos(x,y,z);
                    if(!loaded(block))return false;
                    var state=client.level.getBlockState(mc(block));
                    if(dangerous(state)&&!(allowWater&&state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                            &&!state.is(Blocks.MAGMA_BLOCK)&&!state.is(Blocks.CAMPFIRE)&&!state.is(Blocks.SOUL_CAMPFIRE)
                            &&!state.is(Blocks.CACTUS)&&!state.is(Blocks.SWEET_BERRY_BUSH)&&!state.is(Blocks.POWDER_SNOW)
                            &&!state.is(Blocks.COBWEB)&&!state.is(Blocks.FIRE)&&!state.is(Blocks.SOUL_FIRE)))return false;
                }
        return client.level.noCollision(client.player,box);
    }
    private boolean sweptClear(Vec3 a,Vec3 b) {
        int count=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));
        for(int i=0;i<=count;i++)if(!bodyClear(TravelPath.lerp(a,b,(double)i/count)))return false;
        return true;
    }
    /** Fresh descending body sweep and supported ground coast for a predicted landing; no grounding write. */
    public boolean airborneLandingClear(Vec3 actual,Vec3 landing,Vec3 coast){
        if(!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(landing)||!GroundLocomotionMotion.finite(coast)
                ||actual.distanceTo(landing)>.5||landing.horizontalDistanceTo(coast)>.320001
                ||actual.y()<landing.y()||actual.y()-landing.y()>.5)return false;
        var player=client.player;
        if(player==null||player.shouldDiscardFriction()
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.AIR_DRAG_MODIFIER)-1)<=.000001)
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FRICTION_MODIFIER)-1)<=.000001))return false;
        return standingClear(landing)&&landingBodyEnvelopeClear(actual,landing)&&walkClear(landing,coast)&&brakingFrictionClear(landing,coast);
    }
    /** Read-only delegation to the existing full movement union; grants no movement authority. */
    public boolean readOnlyLandingBodyEnvelopeClear(Vec3 from,Vec3 to){return landingBodyEnvelopeClear(from,to);}
    /** Full movement union, including all native axis orders and intermediate body elevations. */
    private boolean landingBodyEnvelopeClear(Vec3 from,Vec3 to){
        if(client.player==null||client.level==null)return false;
        var dimensions=client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        var bounds=GroundLandingBodyEnvelope.bounds(from,to,dimensions.width(),dimensions.height()).orElse(null);
        if(bounds==null)return false;
        var box=new net.minecraft.world.phys.AABB(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)
            for(int y=(int)Math.floor(box.minY);y<=Math.floor(box.maxY);y++)
                for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
                    var cell=new BlockPos(x,y,z);
                    if(!loaded(cell)||dangerous(client.level.getBlockState(mc(cell))))return false;
                }
        return client.level.noCollision(client.player,box);
    }
    /** Observed contact receives a fresh full normal-friction ground stopping corridor. */
    public boolean observedJumpLandingClear(Vec3 actual,Vec3 coast){
        if(!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(coast)
                ||actual.horizontalDistanceTo(coast)>GroundJumpObservedLanding.maximumCoastDistance()+.000001
                ||Math.abs(actual.y()-coast.y())>.000001)return false;
        var player=client.player;if(player==null||!player.onGround()||player.shouldDiscardFriction()
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.AIR_DRAG_MODIFIER)-1)<=.000001)
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FRICTION_MODIFIER)-1)<=.000001))return false;
        return standingClear(actual)&&walkClear(actual,coast)&&brakingFrictionClear(actual,coast);
    }
    /** Fresh whole remaining no-input fall and ground stopping corridor, not a wider forward-drop range. */
    public boolean dropBrakingClear(GroundDropBrakeForecast.Episode episode){
        if(episode==null||client.player==null||client.level==null||episode.actual().distanceTo(episode.landing())>2
                ||episode.actual().horizontalDistanceTo(episode.coast())>2)return false;
        var player=client.player;
        if(player.isSprinting()||player.shouldDiscardFriction()||player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                ||player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)-.08)<=.000001)
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.AIR_DRAG_MODIFIER)-1)<=.000001)
                ||!(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FRICTION_MODIFIER)-1)<=.000001))return false;
        return standingClear(episode.landing())&&landingBodyEnvelopeClear(episode.actual(),episode.landing())
                &&walkClear(episode.landing(),episode.coast())&&landingBodyEnvelopeClear(episode.landing(),episode.coast())
                &&brakingFrictionClear(episode.landing(),episode.coast());
    }
    private void applyApproachKeys(GroundJumpApproach.Key key){
        input.setForwardHeld(false);input.setJumpHeld(false);input.setSprintHeld(false);input.setSneakHeld(false);
        input.setLeftHeld(key==GroundJumpApproach.Key.LEFT||key==GroundJumpApproach.Key.BACK_LEFT);
        input.setRightHeld(key==GroundJumpApproach.Key.RIGHT||key==GroundJumpApproach.Key.BACK_RIGHT);
        input.setBackHeld(key==GroundJumpApproach.Key.BACK||key==GroundJumpApproach.Key.BACK_LEFT||key==GroundJumpApproach.Key.BACK_RIGHT);
    }
    /** Full native interval around one side/back step and its no-input stopping coast. */
    public boolean jumpApproachGroundClear(Vec3 actual,Vec3 next,Vec3 coast,Vec3 target,BlockPos sourceCell){return jumpApproachGroundClear(actual,next,coast,target,sourceCell,new GroundApproachStepWitness());}
    public boolean jumpApproachGroundClear(Vec3 actual,Vec3 next,Vec3 coast,Vec3 target,BlockPos sourceCell,GroundApproachStepWitness witness){
        if(!witness.observe("ACTUAL_FINITE",GroundLocomotionMotion.finite(actual))||!witness.observe("NEXT_FINITE",GroundLocomotionMotion.finite(next))||!witness.observe("COAST_FINITE",GroundLocomotionMotion.finite(coast))
                ||!witness.observe("NEXT_DISTANCE",!(actual.horizontalDistanceTo(next)>.35))||!witness.observe("COAST_DISTANCE",!(actual.horizontalDistanceTo(coast)>.8))||!witness.observe("NEXT_HEIGHT",!(Math.abs(next.y()-actual.y())>.001))
                ||!witness.observe("ACTUAL_CELL",GroundJumpApproach.sameSourceCell(sourceCell,actual))||!witness.observe("NEXT_CELL",GroundJumpApproach.sameSourceCell(sourceCell,next))||!witness.observe("COAST_CELL",GroundJumpApproach.sameSourceCell(sourceCell,coast)))return false;
        var p=client.player;if(!witness.observe("PLAYER",p!=null)||!witness.observe("FRICTION_ACTIVE",!p.shouldDiscardFriction())||!witness.observe("FRICTION_MODIFIER",Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FRICTION_MODIFIER)-1)<=.000001)
                ||!witness.observe("AIR_DRAG",Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.AIR_DRAG_MODIFIER)-1)<=.000001))return false;
        double uncertainty=GroundJumpApproach.coastUncertainty();
        for(double dx:new double[]{-uncertainty,0,uncertainty})for(double dz:new double[]{-uncertainty,0,uncertainty}){
            var stepEnd=new Vec3(next.x()+dx,next.y(),next.z()+dz);
            var end=new Vec3(coast.x()+dx,coast.y(),coast.z()+dz);
            witness.endpoints(stepEnd,end,dx,dz);
            if(!witness.observe("PADDED_NEXT_CELL",GroundJumpApproach.sameSourceCell(sourceCell,stepEnd))||!witness.observe("PADDED_COAST_CELL",GroundJumpApproach.sameSourceCell(sourceCell,end))
                    ||!witness.observe("PADDED_NEXT_SUPPORT",standingClear(stepEnd))||!witness.observe("PADDED_COAST_SUPPORT",standingClear(end))||!witness.observe("NEXT_WALK",walkClear(actual,stepEnd))||!witness.observe("COAST_WALK",walkClear(stepEnd,end))
                    ||!witness.observe("NEXT_BODY_UNION",landingBodyEnvelopeClear(actual,stepEnd))||!witness.observe("COAST_BODY_UNION",landingBodyEnvelopeClear(stepEnd,end))
                    ||!witness.observe("NEXT_FRICTION",brakingFrictionClear(actual,stepEnd))||!witness.observe("COAST_FRICTION",brakingFrictionClear(stepEnd,end)))return false;
        }
        return witness.observe("NOMINAL_NEXT_SUPPORT",standingClear(next));
    }
    private boolean brakingFrictionClear(Vec3 from,Vec3 to){
        int count=Math.max(1,(int)Math.ceil(from.distanceTo(to)*10));
        for(int i=0;i<=count;i++){
            Vec3 p=TravelPath.lerp(from,to,(double)i/count);int floorY=GroundPathfinder.node(p).y()-1;
            for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3}){
                // Fractional support can choose either the shape block or the lower movement-
                // affecting block. Both must be normal; a snow/slab cover cannot hide underlying ice.
                for(int y:new int[]{floorY,floorY-1}){
                    var cell=new BlockPos((int)Math.floor(p.x()+dx),y,(int)Math.floor(p.z()+dz));
                    if(!loaded(cell)||!GroundJumpLandingForecast.supportedBrakingFriction(client.level.getBlockState(mc(cell)).getBlock().getFriction()))return false;
                }
            }
        }
        return true;
    }
    @Override public boolean stepClear(Vec3 a,Vec3 b) {
        if(walkClear(a,b))return true;
        double dy=b.y()-a.y();
        if(Math.abs(dy)>1.001||!bodyClear(a)||!bodyClear(b))return false;
        if(dy>.6) {
            // A vanilla jump peaks about 1.25 blocks above takeoff. Require headroom over both
            // the takeoff and landing, rather than admitting a jump into a fractional ceiling.
            double apex=a.y()+1.25;
            Vec3 raisedA=new Vec3(a.x(),apex,a.z()),raisedB=new Vec3(b.x(),apex,b.z());
            return sweptClear(a,raisedA)&&sweptClear(raisedA,raisedB)&&sweptClear(raisedB,b);
        }
        if(dy<-.6) {
            Vec3 ledge=new Vec3(b.x(),a.y(),b.z());
            return sweptClear(a,ledge)&&sweptClear(ledge,b);
        }
        return false;
    }
    @Override public boolean standable(BlockPos node) {
        double height=supportHeight(node.x()+.5,node.y(),node.z()+.5);
        return Double.isFinite(height)&&bodyClear(new Vec3(node.x()+.5,height,node.z()+.5));
    }
    /** Read-only loaded/hazard-free support and collision for the full actual native standing footprint. */
    public boolean readOnlyNativeStandingFootprintCurrent(Vec3 point){return McNativeFlightGeometry.standing(client,this,point,new McNativeFlightGeometry.Budget());}
    @Override public boolean standingClear(Vec3 p) {
        return standingClear(p,false);
    }
    /** Same exact support-height proof, with water-body occupancy only for the opt-in traversal. */
    public boolean standingClear(Vec3 p,boolean allowWater) {
        double height=supportHeight(p.x(),GroundPathfinder.nodeCeiling(p.x(),p.y(),p.z()),p.z());
        boolean original=GroundDynamicPlanningDiagnostics.copy(64,Double.isFinite(height))&&GroundDynamicPlanningDiagnostics.copy(65,Math.abs(height-p.y())<.08)&&GroundDynamicPlanningDiagnostics.copy(66,bodyClear(p,allowWater));if(!original)GroundDynamicPlanningDiagnostics.returned("Nav.standingClear.originalFalseReturn");return original;
    }
    @Override public boolean walkClear(Vec3 a,Vec3 b) {
        return walkClear(a,b,false);
    }
    /** Project feet onto unchanged real collision tops, including supported shallow-water steps. */
    public boolean walkClear(Vec3 a,Vec3 b,boolean allowWater) {
        if(GroundDynamicPlanningDiagnostics.copy(68,Math.abs(a.y()-b.y())>.6)){GroundDynamicPlanningDiagnostics.returned("Nav.walkClear.originalHeightDifference");return false;}
        int count=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));
        double previous=a.y();
        for(int i=0;i<=count;i++) {
            Vec3 p=TravelPath.lerp(a,b,(double)i/count);
            double height=supportHeight(p.x(),GroundPathfinder.nodeCeiling(p.x(),p.y(),p.z()),p.z());
            // Permit normal auto-steps, while one-block jumps retain discrete path edges.
            if(GroundDynamicPlanningDiagnostics.copy(69,!Double.isFinite(height))||GroundDynamicPlanningDiagnostics.copy(70,Math.abs(height-previous)>.6)
                    || GroundDynamicPlanningDiagnostics.copy(71,Math.abs(height-p.y())>.6) || GroundDynamicPlanningDiagnostics.copy(72,!bodyClear(Double.doubleToRawLongBits(p.y())==Double.doubleToRawLongBits(height)?p:new Vec3(p.x(),height,p.z()),allowWater))){GroundDynamicPlanningDiagnostics.returned("Nav.walkClear.originalSampleRefused");return false;}
            previous=height;
        }
        boolean original=GroundDynamicPlanningDiagnostics.copy(73,Math.abs(previous-b.y())<.08);if(!original)GroundDynamicPlanningDiagnostics.returned("Nav.walkClear.originalFinalHeight");return original;
    }
    public boolean start(BlockPos target) {
        if(target==null) { stop();reason="Destination unavailable";return false; }
        return start(position(target));
    }
    /** Preserve exact world coordinates; same-cell returns must still move to the requested pose. */
    public boolean start(Vec3 target) {
        return start(target,2500,32);
    }
    /** Caller-scoped ground search bounds, including the recovered 5,000-node cliff search. */
    public boolean start(Vec3 target,int maxNodes,int radius) {
        return start(target,maxNodes,radius,.35);
    }
    /** Optional caller-scoped dry level-first plan; ordinary start remains unchanged. */
    public boolean startPreferLevel(BlockPos target){if(target==null){stop();reason="Destination unavailable";return false;}return startPreferLevel(position(target));}
    public boolean startPreferLevel(Vec3 target){return start(target,2500,32,.35,true);}
    /** A stricter final pose keeps the same loaded/collision and caller search bounds. */
    public boolean start(Vec3 target,int maxNodes,int radius,double terminalRadius) {return start(target,maxNodes,radius,terminalRadius,false);}
    private boolean start(Vec3 target,int maxNodes,int radius,double terminalRadius,boolean preferLevel) {
        if(waterPublicationActor!=null){stop();reason="Issued WATER actor cannot publish dry ground";return false;}
        stop();groundEvidence.reset();
        if(publicationActor!=null&&!publicationActor.current()){reason="Ground publication actor unavailable";return false;}
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35){reason="Invalid final walk tolerance";return false;}
        if(maxNodes<1||maxNodes>5000||radius<1||radius>50) {
            reason="Ground search bounds exceed supported limits";return false;
        }
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null
                ||target==null||!Double.isFinite(target.x())||!Double.isFinite(target.y())||!Double.isFinite(target.z())) {
            reason="World, player or destination unavailable";return false;
        }
        var p=client.player;
        var start=GroundPathfinder.node(new Vec3(p.getX(),p.getY(),p.getZ()));
        var goal=GroundPathfinder.node(target);
        double height=supportHeight(target.x(),goal.y(),target.z());
        if(!Double.isFinite(height)||Math.abs(height-target.y())>.08 || !bodyClear(target)) {
            reason="Destination has no supported clear collision surface";return false;
        }
        var result=preferLevel?GroundPathfinder.findLevelFirst(this,start,goal,maxNodes,radius):GroundPathfinder.find(this,start,goal,maxNodes,radius);
        reason=result.found()?"Walking":result.reason();
        if(result.found()) {
            if(publicationActor!=null&&!publicationActor.current()){reason="Ground publication actor changed during route geometry";return false;}
            owner=p;ownerLevel=client.level;
            walker.start(result.steps(),target,System.currentTimeMillis(),terminalRadius);
            capturedTerminalRadius=terminalRadius;
            if(geometryOnly()){
                profileDiagnostics=new CapturedProfileDiagnostics("GEOMETRY_ONLY",0,0,0,"Dormant preparation has no captured admission or publication");
            }else if(capturedNativeCandidate()&&input instanceof LeaseGroundAdapter adapter&&publicationActor==adapter) {
                Object original=adapter.observationalSnapshot();
                if(original==null){stop();reason="Original ground publication custody unavailable before captured observation";return false;}
                var admission=McCapturedProfileLocomotion.captureQualified(client,adapter);
                if(admission.kind()==McGroundNativePhysicsProfile.AdmissionKind.CAPTURED){
                    capturedLocomotion=admission.controller().orElseThrow();
                    capturedLocomotion.originalTerminalRadius(capturedTerminalRadius);
                    capturedLocomotion.bindOriginalWalker(walker);
                    profileDiagnostics=capturedLocomotion.cachedDiagnostics();
                }else if(admission.kind()==McGroundNativePhysicsProfile.AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS
                        &&adapter.current()&&adapter.observationalSnapshotCurrent(original)&&client.player==owner&&client.level==ownerLevel){
                    // No captured exemption: all original flat/sprint/jump/drop proofs and original publishers remain in force.
                    profileDiagnostics=new CapturedProfileDiagnostics("ORIGINAL_UNSUPPORTED_INITIAL",0,0,0,"Initial physics unsupported; original guarded walking unchanged");
                }else{stop();reason="Captured observation lost original custody; no fallback permission";return false;}
            }else if(capturedNativeCandidate()){
                // This construction never had a captured producer; retain its original guards and behavior.
                profileDiagnostics=new CapturedProfileDiagnostics("ORIGINAL_UNCERTIFIED",0,0,0,"Original walking path; no captured actor/input admission");
            }
        }
        return result.found();
    }
    /** General player journey extension; original source-specific starts retain their exact policy. */
    public boolean startDynamic(Vec3 target,int maxNodes,int radius,double terminalRadius) {
        return startDynamic(target,maxNodes,radius,terminalRadius,false);
    }
    /** Same caller bounds and dynamic custody, preferring the original supported same-level graph. */
    public boolean startDynamicPreferLevel(Vec3 target,int maxNodes,int radius,double terminalRadius){
        return startDynamic(target,maxNodes,radius,terminalRadius,true);
    }
    /** Exact return retains the original terminal run under the same guarded dynamic start. */
    public boolean startDynamicExactReturn(Vec3 target,int maxNodes,int radius,double terminalRadius){
        return startDynamic(target,maxNodes,radius,terminalRadius,false,true);
    }
    public boolean startDynamicExactReturnPreferLevel(Vec3 target,int maxNodes,int radius,double terminalRadius){
        return startDynamic(target,maxNodes,radius,terminalRadius,true,true);
    }
    private boolean startDynamic(Vec3 target,int maxNodes,int radius,double terminalRadius,boolean preferLevel) {
        return startDynamic(target,maxNodes,radius,terminalRadius,preferLevel,false);
    }
    private boolean startDynamic(Vec3 target,int maxNodes,int radius,double terminalRadius,boolean preferLevel,boolean exactReturn) {
        initialDirectionalPlaneFailure=null;dynamicPlanningFirstFailure=dynamicPlanningLatestFailure=null;dynamicStageCostFailure=null;dynamicStageCostUnavailable=false;
        if(!start(target,maxNodes,radius,terminalRadius,preferLevel))return false;
        var expectedPlayer=owner;var expectedLevel=ownerLevel;var expectedConnection=client.getConnection();
        var expectedCamera=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        long expectedEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        var initial=new Vec3(expectedPlayer.getX(),expectedPlayer.getY(),expectedPlayer.getZ());
        // Reuse the just admitted graph. No extra A* search or expanded original node budget.
        var points=walker.admittedPath().stream().map(this::position).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if(!points.isEmpty()&&points.getLast().distanceTo(target)<.001)points.removeLast();points.add(target);
        var expectedConfig=DynamicRouteConfigStamp.capture(config).orElse(null);
        if(expectedConfig==null){stop();reason="Dynamic configuration revisions unavailable";return false;}
        final java.util.function.BooleanSupplier contextCurrent=()->client.isSameThread()&&client.player==expectedPlayer&&client.level==expectedLevel
                &&client.getConnection()==expectedConnection&&expectedConnection!=null&&expectedPlayer.isAlive()
                &&client.gui.screen()==null&&!client.isPaused()&&client.getCameraEntity()==expectedPlayer
                &&expectedPlayer.containerMenu==expectedPlayer.inventoryMenu&&!expectedPlayer.isScoping()
                &&!expectedPlayer.getAbilities().flying&&!expectedPlayer.isInWater()&&!expectedPlayer.isInLava()
                &&(expectedCamera!=null||!dev.veltraph.client.service.mc.McOwnedRotation.installed(client))
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==expectedCamera
                &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==expectedEpoch
                &&expectedConfig.current(config)&&(publicationActor==null||publicationActor.current())
                &&Vec3.distanceFromCoordinates(expectedPlayer.getX(),expectedPlayer.getY(),expectedPlayer.getZ(),initial)<.001;
        var queryFence=new DynamicRouteQueryFence(System::nanoTime,contextCurrent,50_000_000L);
        var geometry=new DynamicGroundRoutePlanner.Geometry() {
            public boolean current() {GroundDynamicPlanningDiagnostics.noteCurrent("Nav.geometry.current");return GroundDynamicPlanningDiagnostics.copy(0,contextCurrent.getAsBoolean());}
            private boolean bounded(Vec3 point){return point!=null&&Math.abs(point.x()-initial.x())<=radius+.5
                    &&Math.abs(point.z()-initial.z())<=radius+.5&&Math.abs(point.y()-initial.y())<=radius;}
            public boolean standing(Vec3 point){GroundDynamicPlanningDiagnostics.geometryCall("Nav.geometry.standing",point,point);return GroundDynamicPlanningDiagnostics.copy(1,bounded(point))&&GroundDynamicPlanningDiagnostics.copy(2,queryFence.query(()->standingClear(point)));}
            public boolean sweep(Vec3 a,Vec3 b){GroundDynamicPlanningDiagnostics.geometryCall("Nav.geometry.sweep",a,b);return GroundDynamicPlanningDiagnostics.copy(3,bounded(a))&&GroundDynamicPlanningDiagnostics.copy(4,bounded(b))&&GroundDynamicPlanningDiagnostics.copy(5,queryFence.query(()->walkClear(a,b)));}
        };
        DynamicGroundRoutePlanner.Plan plan;
        GroundDynamicStageCost stageCost=null;
        try{stageCost=new GroundDynamicStageCost();}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
        try{if(stageCost!=null)stageCost.mark(0);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
        GroundDynamicPlanningDiagnostics.begin(queryFence);
        try{
        plan=exactReturn?DynamicGroundRoutePlanner.planExactReturnWindows(initial,points,dynamicSeed,dynamicJourney++,geometry)
            :DynamicGroundRoutePlanner.planWindows(initial,points,dynamicSeed,dynamicJourney++,geometry);try{if(stageCost!=null)stageCost.mark(1);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
        if(GroundDynamicPlanningDiagnostics.copy(6,plan.outcome()==DynamicGroundRoutePlanner.Outcome.VARIED)&&GroundDynamicPlanningDiagnostics.copy(7,!queryFence.available())&&GroundDynamicPlanningDiagnostics.copy(8,geometry.current()))
            plan=new DynamicGroundRoutePlanner.Plan(DynamicGroundRoutePlanner.Outcome.ORIGINAL,points,"Whole dynamic proof completed beyond budget");
        dynamicDiagnostic=plan.outcome()+": "+plan.reason();
        if(GroundDynamicPlanningDiagnostics.copy(9,plan.outcome()==DynamicGroundRoutePlanner.Outcome.REVOKED)){GroundDynamicPlanningDiagnostics.returned("Nav.afterPlan.originalRevoked");GroundDynamicPlanningDiagnostics.completeOriginal(plan.outcome().name(),plan.reason(),true);dynamicPlanningFirstFailure=GroundDynamicPlanningDiagnostics.cachedFirstFailure();dynamicPlanningLatestFailure=GroundDynamicPlanningDiagnostics.cachedLatestFailure();try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}stop();reason="Dynamic planning ownership or budget revoked";return false;}
        if(GroundDynamicPlanningDiagnostics.copy(10,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("Nav.afterPlan.originalContextLost");GroundDynamicPlanningDiagnostics.completeOriginal(plan.outcome().name(),plan.reason(),true);dynamicPlanningFirstFailure=GroundDynamicPlanningDiagnostics.cachedFirstFailure();dynamicPlanningLatestFailure=GroundDynamicPlanningDiagnostics.cachedLatestFailure();try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}stop();reason="Dynamic planning context changed";return false;}
        GroundDynamicPlanningDiagnostics.completeOriginal(plan.outcome().name(),plan.reason(),plan.outcome()!=DynamicGroundRoutePlanner.Outcome.VARIED);dynamicPlanningFirstFailure=GroundDynamicPlanningDiagnostics.cachedFirstFailure();dynamicPlanningLatestFailure=GroundDynamicPlanningDiagnostics.cachedLatestFailure();
        }finally{GroundDynamicPlanningDiagnostics.abandon();}
        try{if(stageCost!=null)stageCost.mark(2);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
        dynamicConnection=expectedConnection;dynamicAuthority=expectedCamera;dynamicEpoch=expectedEpoch;dynamicConfig=expectedConfig;dynamicMode=true;
        if(plan.outcome()==DynamicGroundRoutePlanner.Outcome.VARIED)walker.startCertifiedDynamicCourse(plan.course(),initial,System.currentTimeMillis(),terminalRadius,this,plan.broadEdges());
        // Crop work retains start(); exact returns retain the original terminal run.
        // Dormant geometry cursors must never capture native publication.
        if(!geometryOnly()&&input instanceof LeaseGroundAdapter adapter&&publicationActor==adapter
                &&(capturedNativeCandidate()||plan.outcome()==DynamicGroundRoutePlanner.Outcome.VARIED
                    &&CameraRelativeGroundSteering.flatRoute(initial,points))){
            try{if(stageCost!=null)stageCost.mark(3);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}Object original=adapter.observationalSnapshot();try{if(stageCost!=null)stageCost.mark(4);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
            if(original==null){try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}stop();reason="Directional ground custody unavailable before native capture";return false;}
            try{if(stageCost!=null)stageCost.mark(5);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}var admission=McCapturedProfileLocomotion.captureDirectionalQualified(client,adapter);try{if(stageCost!=null)stageCost.mark(6);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
            if(admission.kind()==McGroundNativePhysicsProfile.AdmissionKind.CAPTURED){
                var directional=admission.controller().orElseThrow();
                try{if(stageCost!=null)stageCost.mark(7);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
                if(!geometry.current()||!adapter.observationalSnapshotCurrent(original)){
                    try{if(stageCost!=null)stageCost.mark(8);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}directional.stop();stop();reason="Directional native capture changed original route custody";return false;
                }
                try{if(stageCost!=null)stageCost.mark(8);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
                var eagerCourse=new java.util.ArrayList<Vec3>();eagerCourse.add(initial);
                eagerCourse.addAll(plan.outcome()==DynamicGroundRoutePlanner.Outcome.VARIED?plan.course():points);
                McCapturedProfileGeometry.InitialDirectionalPlaneKind initialGeometry;
                GroundInitialDirectionalDiagnostics.begin(queryFence);
                try{
                try{if(stageCost!=null)stageCost.mark(9);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}initialGeometry=directional.initialDirectionalGroundEligibility(java.util.List.copyOf(eagerCourse),queryFence);try{if(stageCost!=null)stageCost.mark(10);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
                try{if(stageCost!=null)stageCost.mark(11);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}
                if(GroundInitialDirectionalDiagnostics.copy(0,!geometry.current())
                        ||GroundInitialDirectionalDiagnostics.copy(1,!adapter.observationalSnapshotCurrent(original))
                        ||GroundInitialDirectionalDiagnostics.copy(2,!queryFence.available())
                        ||GroundInitialDirectionalDiagnostics.copy(3,initialGeometry==McCapturedProfileGeometry.InitialDirectionalPlaneKind.CUSTODY_REFUSED)){
                    try{if(stageCost!=null)stageCost.mark(12);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}GroundInitialDirectionalDiagnostics.returned("McNavigator.initialDirectionalPlane.outerOriginalRefusal");
                    GroundInitialDirectionalDiagnostics.completeOriginal(initialGeometry.name(),true);
                    initialDirectionalPlaneFailure=GroundInitialDirectionalDiagnostics.cachedFailure();
                    try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}
                    directional.stop();stop();directionalGroundDisposition="CUSTODY_REFUSED";
                    reason="Directional original plane proof lost custody or budget";return false;
                }
                try{if(stageCost!=null)stageCost.mark(12);}catch(Throwable observerUnavailable){dynamicStageCostUnavailable=true;}GroundInitialDirectionalDiagnostics.completeOriginal(initialGeometry.name(),false);
                }finally{GroundInitialDirectionalDiagnostics.abandon();}
                if(initialGeometry==McCapturedProfileGeometry.InitialDirectionalPlaneKind.UNSUPPORTED_INITIAL_GEOMETRY){
                    directional.stop();directionalGroundDisposition="UNSUPPORTED_INITIAL_GEOMETRY_ORIGINAL_WALK";
                }else{
                    if(capturedLocomotion!=null)capturedLocomotion.stop();
                    capturedLocomotion=directional;directionalGround=true;directionalGroundDisposition="DIRECTIONAL_SUPPORTED";
                    capturedLocomotion.originalTerminalRadius(capturedTerminalRadius);
                    capturedLocomotion.bindOriginalWalker(walker);
                    profileDiagnostics=capturedLocomotion.cachedDiagnostics();
                }
            }else if(admission.kind()!=McGroundNativePhysicsProfile.AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS
                    ||!geometry.current()||!adapter.observationalSnapshotCurrent(original)){
                try{dynamicStageCostFailure=stageCost==null?null:stageCost.freeze();if(dynamicStageCostFailure==null||dynamicStageCostFailure.failedDiagnosticClockMask()!=0)dynamicStageCostUnavailable=true;}catch(Throwable observerUnavailable){dynamicStageCostFailure=null;dynamicStageCostUnavailable=true;}stop();reason="Directional native capture revoked; no original-walk fallback";return false;
            }
            // An unsupported initial physics family retains the original guarded branch;
            // it never acquires directional input, a profile exemption or renewed authority.
            if(admission.kind()==McGroundNativePhysicsProfile.AdmissionKind.UNSUPPORTED_INITIAL_PHYSICS)
                directionalGroundDisposition="UNSUPPORTED_INITIAL_PHYSICS_ORIGINAL_WALK";
        }else if(!geometryOnly()&&!capturedNativeCandidate()){
            directionalGroundDisposition="ORIGINAL_COURSE_OR_NONFLAT_ORIGINAL_WALK";
        }
        return true;
    }
    /** Opt-in source water travel, retaining the dry-only behavior of start(Vec3). */
    public boolean startWater(Vec3 target) { return startTraversal(target,0); }
    /** Explicit caller may tighten water arrival; existing dry/water signatures keep their bounds. */
    public boolean startWater(Vec3 target,double terminalRadius) {
        if(publicationActor!=null){stop();reason="Bound publication actor supports dry ground only";return false;}
        if(!Double.isFinite(terminalRadius)||terminalRadius<.05||terminalRadius>.3){stop();reason="Invalid strict water terminal radius";return false;}
        stop();if(waterNavigator==null)waterNavigator=new McWaterNavigator(client,input,config,this,waterPublicationActor);
        waterMode=waterNavigator.start(target,0,terminalRadius);reason=waterNavigator.status();return waterMode;
    }
    /** Caller-authorized intentional drop. Dry drops remain capped at normal no-damage height. */
    public boolean startDrop(Vec3 target,int maxDropBlocks) {
        if(maxDropBlocks<1||maxDropBlocks>32) { stop();reason="Invalid intentional-drop bound";return false; }
        return startTraversal(target,maxDropBlocks);
    }
    private boolean startTraversal(Vec3 target,int maxDrop) {
        stop();if(publicationActor!=null){reason="Bound publication actor supports dry ground only";return false;}if(waterNavigator==null)waterNavigator=new McWaterNavigator(client,input,config,this,waterPublicationActor);
        waterMode=waterNavigator.start(target,maxDrop);reason=waterNavigator.status();return waterMode;
    }
    private boolean geometryOnly(){return publicationActor instanceof DormantGroundPublication&&input==publicationActor;}
    public WalkController.State tick() {
        if(geometryOnly()){reason="Dormant geometry cursor cannot tick or publish";return WalkController.State.BLOCKED;}
        if(publicationActor!=null&&!publicationActor.current()){stop();reason="Ground publication actor revoked";return WalkController.State.BLOCKED;}
        if(waterMode) { var state=waterNavigator.tick();reason=waterNavigator.status();return state; }
        if(waterPublicationActor!=null){reason="Issued WATER actor has no active water plan";return WalkController.State.BLOCKED;}
        if (owner==null||client.player!=owner||client.level!=ownerLevel||!owner.isAlive()||client.gui.screen()!=null) {
            stop();reason="Travel ownership or world unavailable";return WalkController.State.BLOCKED;
        }
        if(dynamicMode&&!dynamicCustodyCurrent()) {
            stop();reason="Dynamic journey custody revoked";return WalkController.State.BLOCKED;
        }
        var p=client.player;
        var frozen=capturedLocomotion==null?null:capturedLocomotion.airborneOutput();
        if(frozen!=null&&!walker.locomotionProgressCurrent(System.currentTimeMillis(),4000)){
            stop();reason="Captured original walker progress deadline";return WalkController.State.BLOCKED;
        }
        var out=frozen!=null?frozen:walker.tick(new WalkController.Input(new Vec3(p.getX(),p.getY(),p.getZ()),p.getYRot(),p.onGround(),System.currentTimeMillis()),this,4000,
                client.options.sensitivity().get(),config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER));
        if(dynamicMode&&!dynamicCustodyCurrent()) {
            stop();reason="Dynamic journey custody changed during geometry";return WalkController.State.BLOCKED;
        }
        if(capturedLocomotion!=null)return tickCaptured(out);
        GroundLocomotionPolicy.Decision locomotionOut=null;
        GroundJumpObservedLanding.AirSample pendingAir=null;
        GroundDropBrakeForecast.Episode pendingDropBrake=null;
        GroundDropBrakeForecast.Episode completedDrop=null;
        McGroundJumpApproach.Result approachResult=null;
        if(dynamicMode&&out.state()==WalkController.State.WALKING){
            Vec3 actual=new Vec3(p.getX(),p.getY(),p.getZ());
            Vec3 locomotionTarget=(locomotion.phase()==GroundLocomotionPolicy.Phase.TAKEOFF||locomotion.phase()==GroundLocomotionPolicy.Phase.AIRBORNE||locomotion.phase()==GroundLocomotionPolicy.Phase.LANDING_BRAKE||locomotion.phase()==GroundLocomotionPolicy.Phase.DROPPING)
                    ?locomotion.landing():walker.locomotionTarget();
            Vec3 dropOrigin=locomotion.phase()==GroundLocomotionPolicy.Phase.DROPPING
                    ?walker.locomotionDropCertified(locomotion.takeoff(),locomotion.landing())?locomotion.takeoff():null
                    :walker.locomotionDropOrigin();
            Vec3 sprintTarget=GroundSprintContinuation.hint(sprintPublished,p.isSprinting(),actual,p.getYRot(),out.yaw(),walker.locomotionContinuationBasis())
                    .orElseGet(()->GroundSprintWindow.select(actual,p.getYRot(),walker.locomotionSprintCandidates(actual)).orElseGet(()->walker.locomotionSprintTarget(actual)));
            var proof=McGroundLocomotionProof.capture(client,this,actual,locomotionTarget,sprintTarget,dropOrigin,p.getYRot(),locomotion,acceptedAir,acceptedDropBrake,this::dynamicCustodyCurrent);
            var locomotionFrame=new GroundLocomotionPolicy.Frame(actual,p.onGround(),p.isSprinting(),System.currentTimeMillis(),
                    dynamicCustodyCurrent(),McGroundLocomotionProof.sprintAvailable(client),p.tickCount);
            approachResult=jumpApproach.tick(client,this,out,proof,locomotion.phase(),walker.locomotionTarget(),this::dynamicCustodyCurrent);
            if(approachResult.blocked()){String why=approachResult.reason();var witness=jumpApproach.failureWitness();var guardWitness=jumpApproach.guardWitness();var stepWitness=jumpApproach.stepWitness();stop();reason=why;
                try{reason=why+" | "+witness+" | approachGuard="+guardWitness+" | approachStep="+stepWitness;}catch(Throwable diagnosticUnavailable){/* Original release already completed. */}
                return WalkController.State.BLOCKED;}
            locomotionOut=approachResult.handled()?new GroundLocomotionPolicy.Decision(false,false,false,false,approachResult.reason()):locomotion.tick(locomotionFrame,out,proof);
            pendingDropBrake=proof.dropEpisode();
            if(proof.dropEpisode()!=null&&locomotionFrame.onGround()&&locomotion.phase()==GroundLocomotionPolicy.Phase.LANDED){completedDrop=proof.dropEpisode();}
            if(!locomotionFrame.onGround()&&!locomotionOut.blocked()&&proof.jumpClear()&&proof.evidence()!=null
                    &&(locomotion.phase()==GroundLocomotionPolicy.Phase.AIRBORNE||locomotion.phase()==GroundLocomotionPolicy.Phase.LANDING_BRAKE))
                pendingAir=new GroundJumpObservedLanding.AirSample(locomotion.jumpIssuedTick(),locomotionFrame.nativeTick(),locomotion.takeoff(),locomotion.landing(),actual,
                        proof.evidence().velocity(),proof.evidence().speed(),locomotionOut.forward(),locomotionFrame.sprinting());
            try{groundEvidence.record(proof.evidence(),proof.sprintClear(),locomotionFrame.sprintAvailable(),locomotionOut.sprint());}
            catch(Throwable diagnosticUnavailable){/* Cached recording has no permission or effect authority. */}
            if(locomotionOut.blocked()){String blocked=locomotionOut.reason();var operands=proof.evidence();
                stop();reason=blocked;
                try{reason=blocked+" | "+(operands==null?"groundOperands unavailable":operands.describe());}
                catch(Throwable diagnosticUnavailable){/* Diagnostic failure cannot prevent or undo original release. */}
                return WalkController.State.BLOCKED;}
        }
        final GroundLocomotionPolicy.Decision movement=locomotionOut;
        final McGroundJumpApproach.Result approach=approachResult;
        lastDiagnostic="locomotion="+(movement==null?"legacy":movement.reason())+" | dynamic="+dynamicDiagnostic+" | "+walker.diagnostics()+" actual="+p.position()+" yaw="+p.getYRot()+" onGround="+p.onGround()
                +" support="+supportHeight(p.getX(),GroundPathfinder.node(new Vec3(p.getX(),p.getY(),p.getZ())).y(),p.getZ())
                +" bodyClear="+bodyClear(new Vec3(p.getX(),p.getY(),p.getZ()));
        Runnable publishCamera=()->{
            if(out.state()==WalkController.State.WALKING){
                dev.veltraph.client.service.mc.McOwnedRotation.planYaw(client,out.yaw());
                if(dynamicMode&&client.player==owner&&client.level==ownerLevel&&client.getConnection()==dynamicConnection
                        &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==dynamicAuthority)
                    dynamicEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
            }
        };
        if(publicationActor!=null){
            boolean published=GroundPublicationFence.publish(publicationActor,()->!dynamicMode||dynamicCustodyCurrent(),
                    out.state()==WalkController.State.WALKING,out.yaw(),()->{
                        actorPublished=true;
                        if(dynamicMode&&client.player==owner&&client.level==ownerLevel&&client.getConnection()==dynamicConnection
                                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==dynamicAuthority)
                            dynamicEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
                    },()->{actorPublished=true;if(movement==null)GroundWalkInputs.apply(input,out);else if(approach!=null&&approach.handled())applyApproachKeys(approach.key());else GroundLocomotionInputs.apply(input,movement);});
            if(!published){stop();reason="Ground publication actor changed before output";return WalkController.State.BLOCKED;}
        }else if(dynamicMode){
            if(!DynamicRouteCustody.publish(this::dynamicCustodyCurrent,publishCamera,()->{if(movement==null)GroundWalkInputs.apply(input,out);else if(approach!=null&&approach.handled())applyApproachKeys(approach.key());else GroundLocomotionInputs.apply(input,movement);})){
                stop();reason="Dynamic output custody revoked before publication";return WalkController.State.BLOCKED;
            }
        }else {publishCamera.run();GroundWalkInputs.apply(input,out);}
        if(dynamicMode){
            if(!dynamicCustodyCurrent()){stop();reason="Owned air publication custody revoked";return WalkController.State.BLOCKED;}
            acceptedAir=pendingAir;acceptedDropBrake=pendingDropBrake;
            if(movement!=null&&movement.sprint())sprintPublished=true;else if(!p.isSprinting())sprintPublished=false;
            jumpApproach.committed(approach,p.tickCount);
            if(completedDrop!=null){
                walker.acknowledgeDropLanding(completedDrop,System.currentTimeMillis(),this,this::dynamicCustodyCurrent);
                if(!dynamicCustodyCurrent()){stop();reason="Certified drop landing custody revoked";return WalkController.State.BLOCKED;}
                acceptedDropBrake=null;
            }
        }
        reason=out.state().name();
        return out.state();
    }
    /** Read-only observation hint only. Unsupported initial traits retain the original guarded
     * branch; this hint grants no sprint/jump/drop exemption or native publication authority. */
    private boolean capturedNativeCandidate(){
        var p=client.player;if(p==null)return false;
        double base=p.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return p.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST)
                ||!(Double.doubleToLongBits(base)==Double.doubleToLongBits(.1D)
                    ||Double.doubleToLongBits(base)==Double.doubleToLongBits((double).1F));
    }
    private boolean publishCaptured(McCapturedProfileLocomotion.Decision decision,boolean camera){
        if(decision==null||decision.blocked()||decision.controls()==null)return false;
        Runnable keys=()->{
            actorPublished=true;
            var c=decision.controls();
            input.setForwardHeld(CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.FORWARD));
            input.setBackHeld(CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.BACK));
            input.setLeftHeld(CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.LEFT));
            input.setRightHeld(CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.RIGHT));
            input.setJumpHeld(CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.JUMP));
            input.setSneakHeld(false);
            boolean sprint=CapturedProfileMotionMath.held(c,LegacyTravelRetirement.Key.SPRINT);
            if(sprint&&(capturedLocomotion==null||!capturedLocomotion.sprintPressCurrent(decision)))
                throw new GroundPublicationRevokedException("Captured native sprint eligibility changed before original PRESS");
            input.setSprintHeld(sprint);
        };
        if(publicationActor!=null) {
            return GroundPublicationFence.publish(publicationActor,()->!dynamicMode||dynamicCustodyCurrent(),camera,
                    decision.yaw(),()->{
                        actorPublished=true;
                        if(dynamicMode&&client.player==owner&&client.level==ownerLevel&&client.getConnection()==dynamicConnection
                                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==dynamicAuthority)
                            dynamicEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
                    },keys);
        }
        if(!(input instanceof LeaseGroundAdapter))return false;
        Runnable pose=()->{
            if(camera) {
                dev.veltraph.client.service.mc.McOwnedRotation.planYaw(client,decision.yaw());
                if(dynamicMode&&client.player==owner&&client.level==ownerLevel&&client.getConnection()==dynamicConnection
                        &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==dynamicAuthority)
                    dynamicEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
            }
        };
        if(dynamicMode)return DynamicRouteCustody.publish(this::dynamicCustodyCurrent,pose,keys);
        // The original adapter still guards each of the seven actual setters individually.
        if(!((LeaseGroundAdapter)input).current())return false;pose.run();keys.run();return ((LeaseGroundAdapter)input).current();
    }
    private WalkController.State tickCaptured(WalkController.Output requested){
        originalAfterPublicationFailure=originalFinalAfterPublicationFailure=null;
        var exact=capturedLocomotion;
        Vec3 target=walker.locomotionTarget();
        Vec3 sprintTarget=walker.locomotionSprintTarget(new Vec3(owner.getX(),owner.getY(),owner.getZ()));
        Vec3 originalDrop=walker.locomotionDropOrigin();
        var decision=directionalGround?exact.tickDirectional(requested,target,sprintTarget,originalDrop,walker.locomotionSteeringTarget())
                :exact.tick(requested,target,sprintTarget,originalDrop);
        profileDiagnostics=exact.cachedDiagnostics();
        if(decision.blocked()){String why=decision.reason();stop();reason=why;return WalkController.State.BLOCKED;}
        if(!publishCaptured(decision,decision.state()==WalkController.State.WALKING)) {
            stop();reason="Captured original input publisher refused";return WalkController.State.BLOCKED;
        }
        if(!captureOriginalAfterPublication(exact,exact.afterPublished())) {
            if(exact.requiresNeutralReplan()) {
                var neutral=exact.neutralReplanDecision();
                if(neutral.blocked()||!publishCaptured(neutral,false)||!captureOriginalAfterPublication(exact,exact.afterPublished())) {
                    stop();reason="Captured guarded queued-camera neutral replan refused";return WalkController.State.BLOCKED;
                }
                decision=neutral;
            } else {stop();reason="Captured original frame/native observer arm refused";return WalkController.State.BLOCKED;}
        }
        profileDiagnostics=exact.cachedDiagnostics();
        lastDiagnostic="captured="+decision.reason()+" | dynamic="+dynamicDiagnostic+" | "+walker.diagnostics();
        reason=decision.reason();return decision.state();
    }
    private boolean dynamicCustodyCurrent(){
        return owner!=null&&client.player==owner&&client.level==ownerLevel&&owner.isAlive()
                &&dynamicConfig!=null&&dynamicConfig.current(config)&&client.getConnection()==dynamicConnection
                &&client.gui.screen()==null&&!client.isPaused()&&client.getCameraEntity()==owner
                &&owner.containerMenu==owner.inventoryMenu&&owner.containerMenu.getCarried().isEmpty()&&!owner.isScoping()&&!owner.isUsingItem()&&!owner.isPassenger()&&!owner.isSpectator()&&!owner.getAbilities().flying
                &&!owner.isInWater()&&!owner.isInLava()&&!client.level.tickRateManager().isEntityFrozen(owner)
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==dynamicAuthority
                &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==dynamicEpoch;
    }
    /** Opaque receipt from an actual strict same-plan ARRIVED tick; no substitute authority. */
    public static final class WaterTerminalHold {
        final McWaterNavigator driver;final dev.veltraph.client.navigation.WaterTerminalLease lease;
        WaterTerminalHold(McWaterNavigator driver,dev.veltraph.client.navigation.WaterTerminalLease lease){this.driver=driver;this.lease=lease;}
    }
    public java.util.Optional<WaterTerminalHold> retainWaterTerminalHold(Object exactAuthority,long expiresAt){
        return waterMode&&waterNavigator!=null?waterNavigator.retainTerminalHold(exactAuthority,expiresAt):java.util.Optional.empty();
    }
    /** Uses only ordinary native movement keys projected from actual yaw; publishes no camera plan. */
    public WalkController.State tickWaterTerminalHold(WaterTerminalHold receipt,Object exactAuthority){
        if(!waterMode||waterNavigator==null||receipt==null||receipt.driver!=waterNavigator)return WalkController.State.BLOCKED;
        var state=waterNavigator.tickTerminalHold(receipt,exactAuthority);reason=waterNavigator.status();return state;
    }
    /** Saved view remains in the same actual WATER publication lineage while holding native footing. */
    public boolean trackWaterTerminalView(WaterTerminalHold receipt,Object authority,float yaw,float pitch){
        return waterMode&&waterNavigator!=null&&receipt!=null&&receipt.driver==waterNavigator&&waterNavigator.trackTerminalView(receipt,authority,yaw,pitch);
    }
    /** Private actual logical reset receipt, captured before any input observer can replace the route. */
    public final class IssuedResumeStopReceipt {
        private final dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.RouteStopCall source;
        private final dev.veltraph.client.farming.module.FarmMaintenanceState after;
        private IssuedResumeStopReceipt(dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.RouteStopCall source){this.source=source;after=maintenanceState();}
        public boolean belongs(dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.RouteStopCall exact,McNavigator actual){return source==exact&&actual==McNavigator.this;}
        public boolean current(){return after.equals(maintenanceState());}
    }
    /** Typed original farm checkpoint only. Native camera cleanup remains in its actual service's own ticket path. */
    public boolean stopIssuedFarmResume(dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.RouteStopCall call){
        if(call==null||publicationActor!=null||waterPublicationActor!=null||!call.beforeNativeCurrent(this)
                ||waterNavigator!=null&&!waterNavigator.issuedFarmResumeLogicalAvailable())return false;
        captureStopOperands();
        if(!call.beforeNativeCurrent(this))return false;
        maintenanceReceipt.set(null);
        // These are the original callback-free local retirements. The actual native camera ticket is not guessed from dynamicEpoch.
        if(capturedLocomotion!=null){capturedLocomotion.stop();profileDiagnostics=capturedLocomotion.cachedDiagnostics();capturedLocomotion=null;}
        directionalGround=false;directionalGroundDisposition="ORIGINAL_POLICY";acceptedAir=null;acceptedDropBrake=null;jumpApproach.reset();sprintPublished=false;locomotion.reset();dynamicMode=false;dynamicConnection=null;dynamicAuthority=null;dynamicEpoch=0;dynamicConfig=null;walker.reset();owner=null;ownerLevel=null;waterMode=false;
        if(waterNavigator!=null&&!waterNavigator.stopIssuedFarmResumeLogical())return false;
        var actual=new IssuedResumeStopReceipt(call);
        if(!call.acceptOwnLogicalRetirement(this,actual))return false;
        return call.releaseMovementAfterOwnRetirement(this)&&actual.current();
    }
    public void stop() { captureStopOperands();maintenanceReceipt.set(null);
        if(publicationActor==null&&dynamicMode&&client.isSameThread())new DynamicRouteCustody(owner,ownerLevel,dynamicConnection,dynamicAuthority,dynamicEpoch)
                .cancelIfMatching(client.player,client.level,client.getConnection(),dev.veltraph.client.service.mc.McOwnedRotation.authority(client),
                        dev.veltraph.client.service.mc.McOwnedRotation.epoch(client),()->dev.veltraph.client.service.mc.McOwnedRotation.cancel(client));
        if(capturedLocomotion!=null){capturedLocomotion.stop();profileDiagnostics=capturedLocomotion.cachedDiagnostics();capturedLocomotion=null;}
        directionalGround=false;directionalGroundDisposition="ORIGINAL_POLICY";acceptedAir=null;acceptedDropBrake=null;jumpApproach.reset();sprintPublished=false;locomotion.reset();dynamicMode=false;dynamicConnection=null;dynamicAuthority=null;dynamicEpoch=0;dynamicConfig=null;walker.reset();owner=null;ownerLevel=null;waterMode=false;if(waterNavigator!=null)waterNavigator.stop(); if(waterPublicationActor!=null){/* scoped water helper performs only exact own cleanup */}else if(publicationActor!=null){boolean cleanup=actorPublished;actorPublished=false;if(cleanup)publicationActor.cleanupOwned();}else input.releaseAllMovement(); }
    /** Narrow farm-drop custody receipt; source fields and original native owners are immutable. */
    public record MaintenanceBinding(McNavigator navigator,
            dev.veltraph.client.farming.module.FarmMaintenanceState progress,
            net.minecraft.client.player.LocalPlayer player,net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.client.multiplayer.ClientPacketListener connection,java.util.UUID uuid,
            String dimension,String profile,Vec3 anchor,McWaterNavigator.NativeBinding water,
            net.minecraft.client.player.LocalPlayer groundOwner,net.minecraft.client.multiplayer.ClientLevel groundLevel,boolean rebound) {}
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "client",client,"input",input,"publicationActor",publicationActor,"actorPublished",actorPublished,"walker",walker.maintenanceState(),"config",config,"reason",reason,
                "waterNavigator",waterNavigator==null?null:waterNavigator.maintenanceState(),"waterMode",waterMode,
                "capturedLocomotion",capturedLocomotion==null?null:capturedLocomotion.maintenanceState(),"capturedTerminalRadius",capturedTerminalRadius,"profileDiagnostics",profileDiagnostics,
                "directionalGround",directionalGround,"directionalGroundDisposition",directionalGroundDisposition,"locomotionPhase",locomotion.phase(),"acceptedAir",acceptedAir,"acceptedDropBrake",acceptedDropBrake,"lastDiagnostic",lastDiagnostic,"dynamicSeed",dynamicSeed,"dynamicJourney",dynamicJourney,"dynamicDiagnostic",dynamicDiagnostic,"dynamicConnection",dynamicConnection,"dynamicAuthority",dynamicAuthority,"dynamicEpoch",dynamicEpoch,"dynamicMode",dynamicMode,"dynamicConfig",dynamicConfig);
    }
    private boolean maintenanceNativeClear(){
        if(client!=Minecraft.getInstance()||!client.isSameThread()||client.player==null||client.level==null
                ||client.getConnection()==null||client.gameMode==null||client.options==null||!client.player.isAlive()
                ||client.player.level()!=client.level||client.gui.screen()!=null
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()
                ||client.player.isInLava()||client.player.getAbilities().flying||client.player.isSprinting()||client.player.isSwimming()
                ||client.options.keyUp.isDown()||client.options.keyDown.isDown()||client.options.keyLeft.isDown()
                ||client.options.keyRight.isDown()||client.options.keyJump.isDown()||client.options.keyShift.isDown()
                ||client.options.keySprint.isDown()||client.options.keyAttack.isDown()||client.options.keyUse.isDown())return false;
        var v=client.player.getDeltaMovement();
        if(v.horizontalDistanceSqr()>.000001||Math.abs(v.y)>(client.player.onGround()?.08:.006)
                ||!client.player.onGround()&&!client.player.isInWater())return false;
        if(!(client.level instanceof dev.veltraph.client.mixin.ClientLevelPredictionAccessor access))return false;
        var handler=access.veltraph$blockStatePredictionHandler();
        return handler instanceof dev.veltraph.client.mixin.BlockStatePredictionHandlerAccessor predictions
                &&predictions.veltraph$serverVerifiedStates()!=null&&predictions.veltraph$serverVerifiedStates().isEmpty();
    }
    public java.util.Optional<MaintenanceBinding> captureMaintenanceBinding(String profile){
        try {
            if(profile==null||profile.isBlank()||!maintenanceNativeClear()||!waterMode||waterNavigator==null
                    ||!waterNavigator.maintenanceActive())return java.util.Optional.empty();
            var nativeBinding=waterNavigator.maintenanceBinding();
            if(nativeBinding==null||nativeBinding.player()!=client.player||nativeBinding.level()!=client.level)return java.util.Optional.empty();
            var anchor=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
            if(!waterNavigator.maintenanceGeometryClear(anchor))return java.util.Optional.empty();
            var binding=new MaintenanceBinding(this,maintenanceState(),client.player,client.level,
                    client.getConnection(),client.player.getUUID(),client.level.dimension().identifier().toString(),profile,anchor,nativeBinding,owner,ownerLevel,false);
            var previous=maintenanceReceipt.get();
            if(previous!=null&&!previous.rebound())return java.util.Optional.empty();
            return maintenanceReceipt.compareAndSet(previous,binding)?java.util.Optional.of(binding):java.util.Optional.empty();
        }catch(Throwable unavailable){return java.util.Optional.empty();}
    }
    public boolean maintenanceBindingUnchanged(MaintenanceBinding expected,String profile){
        return expected!=null&&expected.navigator()==this&&maintenanceReceipt.get()==expected&&expected.profile().equals(profile)
                &&owner==expected.groundOwner()&&ownerLevel==expected.groundLevel()
                &&waterMode&&waterNavigator!=null&&waterNavigator.maintenanceOwns(expected.water())
                &&maintenanceState().equals(expected.progress());
    }
    private boolean maintenanceContextCompatible(MaintenanceBinding expected,String profile){
        try {
            if(!maintenanceBindingUnchanged(expected,profile)||!maintenanceNativeClear()
                    ||!expected.uuid().equals(client.player.getUUID())
                    ||!expected.dimension().equals(client.level.dimension().identifier().toString()))return false;
            var actual=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
            return actual.distanceTo(expected.anchor())<.08&&waterNavigator.maintenanceGeometryClear(actual);
        }catch(Throwable unavailable){return false;}
    }
    public boolean canRebindMaintenance(MaintenanceBinding expected,String profile){
        return expected!=null&&!expected.rebound()&&maintenanceContextCompatible(expected,profile);
    }
    public java.util.Optional<MaintenanceBinding> rebindMaintenance(MaintenanceBinding expected,String profile){
        if(!canRebindMaintenance(expected,profile))return java.util.Optional.empty();
        // Client-thread preflight and exact receipt CAS prevent a foreign/replayed checkpoint.
        var rebound=new MaintenanceBinding(this,expected.progress(),client.player,client.level,client.getConnection(),expected.uuid(),
                expected.dimension(),expected.profile(),expected.anchor(),
                new McWaterNavigator.NativeBinding(client.player,client.level),owner,ownerLevel,true);
        if(!maintenanceReceipt.compareAndSet(expected,rebound)||!waterNavigator.maintenanceRebind(expected.water()))return java.util.Optional.empty();
        return maintenanceBoundToCurrentContext(rebound,profile)?java.util.Optional.of(rebound):java.util.Optional.empty();
    }
    public boolean maintenanceBoundToCurrentContext(MaintenanceBinding binding,String profile){
        return binding!=null&&binding.rebound()&&maintenanceContextCompatible(binding,profile)&&binding.player()==client.player&&binding.level()==client.level
                &&binding.connection()==client.getConnection();
    }
    public String status() { return reason; }
    /** Cached proof/policy samples only; never performs a native query or action. */
    public String groundEvidence(){return groundEvidence.describe();}
    public String diagnostics() { return reason+" | "+lastDiagnostic; }
    /** Passive operands only; never evaluates custody, terrain, actor current or receipt refresh. */
    private void captureStopOperands(){
        if(owner==null)return;
        try{var p=client.player;lastStopOperands="reason="+reason+" last="+lastDiagnostic
            +" walker="+walker.diagnostics()+" dynamic="+dynamicDiagnostic+" phase="+locomotion.phase()
            +" actorPublished="+actorPublished+" dynamicMode="+dynamicMode+" expectedEpoch="+dynamicEpoch
            +" actualEpoch="+dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)
            +" actualSerial="+dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)
            +" playerSame="+(p==owner)+" worldSame="+(client.level==ownerLevel)+" connectionSame="+(client.getConnection()==dynamicConnection)
            +" pose="+(p==null?"none":p.position())+" velocity="+(p==null?"none":p.getDeltaMovement())
            +" onGround="+(p!=null&&p.onGround())+" sprint="+(p!=null&&p.isSprinting())+" water="+(p!=null&&p.isInWater())
            +" lava="+(p!=null&&p.isInLava())+" using="+(p!=null&&p.isUsingItem())+" scope="+(p!=null&&p.isScoping())
            +" passenger="+(p!=null&&p.isPassenger())+" flying="+(p!=null&&p.getAbilities().flying)
            +" gui="+(client.gui.screen()==null?"none":client.gui.screen().getClass().getName());
        }catch(Throwable unavailable){lastStopOperands="diagnostic unavailable="+unavailable.getClass().getName();}
    }
}
