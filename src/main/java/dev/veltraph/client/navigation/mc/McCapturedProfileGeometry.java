package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.GroundInitialDirectionalDiagnostics;
import dev.veltraph.client.navigation.GroundPublicationFailureCache;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import java.util.Optional;

/** Actual native captured-profile geometry, always guarded by the privately issued Journey.
 * The boolean results are physical facts only; original scoped key methods retain authority. */
public final class McCapturedProfileGeometry {
    private McCapturedProfileGeometry() {}
    private static McGroundNativePhysicsProfile.Journey proofJourney;
    private static int proofTick;
    private static long stationaryProofsCreated,stationaryFinalValidations;
    public record StationaryProofCounts(long created,long finalNativeValidations) {}
    /** Passive counts only. Creation/validation alone is not an accepted movement receipt. */
    public static StationaryProofCounts cachedStationaryProofCounts(){return new StationaryProofCounts(stationaryProofsCreated,stationaryFinalValidations);}
    private static final java.util.IdentityHashMap<CapturedProfileTrajectory.Trace,SelectedProof> selectedProofs =
            new java.util.IdentityHashMap<>();
    /** A private native journal from the EXACT selected full trace, never an input capability. */
    public static final class SelectedProof {
        private final Minecraft client;private final McGroundNativePhysicsProfile.Journey journey;
        private final CapturedProfileTrajectory.Trace trace;private final Context selected;
        private final McGroundProfileGeometry.Pass pass;private final DynamicRouteQueryFence originalBudget;
        private boolean retired;private final int fullHorizon;private final boolean stationary;
        private CapturedProfileGroundYawEnvelope.Envelope groundYawEnvelope;
        private GroundYawHalo groundYawHalo;
        private final GroundProfileMotionCarrier.ObservedStep ownedLatch;
        private SelectedProof(Minecraft client,McGroundNativePhysicsProfile.Journey journey,
                CapturedProfileTrajectory.Trace trace,Context selected,McGroundProfileGeometry.Pass pass,
                DynamicRouteQueryFence budget,GroundProfileMotionCarrier.ObservedStep ownedLatch,int fullHorizon){this.fullHorizon=fullHorizon;this.stationary=pass.hasStationaryProof();if(stationary)stationaryProofsCreated++;this.client=client;this.journey=journey;this.trace=trace;this.ownedLatch=ownedLatch;
            this.selected=selected;this.pass=pass;originalBudget=budget;budget.copyOriginalProofCreation();}
        public void copyOriginalClockPhasesToPublicationFailure(){originalBudget.copyOriginalClockPhasesToPublicationFailure();}
        /** NO current(), role, config, provider, or public callback. Called after final publication callback. */
        public boolean callbackFreeCurrent(Context now,LegacyTravelPublicationLease.Leg.ProfileInputFrame frame){
            originalBudget.beginOriginalFinalPublicationObservation();
            if(GroundPublicationFailureCache.copy(0,retired)||GroundPublicationFailureCache.copy(1,now==null)||GroundPublicationFailureCache.copy(2,frame==null)||GroundPublicationFailureCache.copy(3,!client.isSameThread())||GroundPublicationFailureCache.copy(4,proofJourney!=journey
                    )||GroundPublicationFailureCache.copy(5,selectedProofs.get(trace)!=this)){originalBudget.copyOriginalAvailableToPublicationFailure(); GroundPublicationFailureCache.returned("selectedProof.originalFalseReturn0"); return false;}
            boolean same=GroundPublicationFailureCache.copy(18,now.profile().state().equals(selected.profile().state())
                    )&&GroundPublicationFailureCache.copy(19,now.pose().nativeTick()==selected.pose().nativeTick()
                    )&&GroundPublicationFailureCache.copy(20,now.pose().position().equals(selected.pose().position())
                    )&&GroundPublicationFailureCache.copy(21,now.pose().velocity().equals(selected.pose().velocity())
                    )&&GroundPublicationFailureCache.copy(22,now.pose().onGround()==selected.pose().onGround()
                    )&&GroundPublicationFailureCache.copy(23,now.pose().sprinting()==selected.pose().sprinting()
                    )&&GroundPublicationFailureCache.copy(24,Float.floatToIntBits(now.pose().travelSpeed())==Float.floatToIntBits(selected.pose().travelSpeed())
                    )&&GroundPublicationFailureCache.copy(25,(groundYawEnvelope!=null?Float.isFinite(now.pose().yaw())
                        :CapturedProfileMotionMath.yawDifference(now.pose().yaw(),selected.pose().yaw())<=.5D)
                    )&&GroundPublicationFailureCache.copy(26,Float.floatToIntBits(now.friction())==Float.floatToIntBits(selected.friction())
                    )&&GroundPublicationFailureCache.copy(27,Double.doubleToLongBits(now.bodyHeight())==Double.doubleToLongBits(selected.bodyHeight())
                    )&&GroundPublicationFailureCache.copy(28,frame.issuedTick()==now.pose().nativeTick());
            if(GroundPublicationFailureCache.copy(6,!same)||GroundPublicationFailureCache.copy(7,!latchNativeIdentity(client,ownedLatch,now))||GroundPublicationFailureCache.copy(8,!frame.nativeCurrent())||GroundPublicationFailureCache.copy(9,!nativeStart(client,now))||GroundPublicationFailureCache.copy(10,!originalBudget.copyOriginalFirstFinalPublicationAvailable(originalBudget.available())
                    )||GroundPublicationFailureCache.copy(11,!journey.callbackFreeForecastCurrent(trace.profileState(),fullHorizon)
                    )||GroundPublicationFailureCache.copy(12,!pass.revalidate(originalBudget)
                    )||groundYawHalo!=null&&!groundYawHalo.revalidate(originalBudget)
                    ||GroundPublicationFailureCache.copy(13,!journey.callbackFreeForecastCurrent(trace.profileState(),fullHorizon)
                    )||GroundPublicationFailureCache.copy(14,!nativeStart(client,now))||GroundPublicationFailureCache.copy(15,!latchNativeIdentity(client,ownedLatch,now))||GroundPublicationFailureCache.copy(16,!frame.nativeCurrent())||GroundPublicationFailureCache.copy(17,!originalBudget.available())){originalBudget.copyOriginalAvailableToPublicationFailure(); GroundPublicationFailureCache.returned("selectedProof.originalFalseReturn1"); 
                retired=true;return false;
            }
            originalBudget.copyOriginalAvailableToPublicationFailure();
            if(stationary)stationaryFinalValidations++;
            return true;
        }
    }
    /** Only retrieves an already privately issued proof; never invokes any actor/role callback. */
    public static SelectedProof selectedProof(Minecraft c,McGroundNativePhysicsProfile.Journey j,
            CapturedProfileTrajectory.Trace exactTrace){
        if(c==null||!c.isSameThread()||c.player==null||proofJourney!=j||c.player.tickCount!=proofTick)return null;
        return selectedProofs.get(exactTrace);
    }
    private static void remember(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Context context,McGroundProfileGeometry.Pass pass,DynamicRouteQueryFence fence,GroundProfileMotionCarrier.ObservedStep ownedLatch){
        if(proofJourney!=j||proofTick!=context.pose().nativeTick()){
            selectedProofs.clear();proofJourney=j;proofTick=context.pose().nativeTick();
        }
        // One decision considers at most nine controls. Additional callers can only exhaust/refuse this bounded journal.
        remember(c,j,trace,context,pass,fence,ownedLatch,trace.samples().size());
    }
    private static void remember(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Context context,McGroundProfileGeometry.Pass pass,DynamicRouteQueryFence fence,GroundProfileMotionCarrier.ObservedStep ownedLatch,int fullHorizon){
        if(proofJourney!=j||proofTick!=context.pose().nativeTick()){
            selectedProofs.clear();proofJourney=j;proofTick=context.pose().nativeTick();
        }
        if(selectedProofs.size()<16)selectedProofs.put(trace,new SelectedProof(c,j,trace,context,pass,fence,ownedLatch,fullHorizon));
    }
    /** Native collision iterator dependencies for the complete ALL-heading rectangle.
     * This is private geometry evidence, not an actor/input capability. Reads consume the SAME
     * original Pass.remaining counter; no new cell allowance or clock is minted here.
     */
    private static final class GroundYawHalo {
        private final Minecraft client;private final net.minecraft.client.player.LocalPlayer player;
        private final net.minecraft.client.multiplayer.ClientLevel level;private final McGroundProfileGeometry.Pass pass;
        private final java.util.LinkedHashMap<GroundGeometryCellJournal.Cell,HaloObservation> cells=new java.util.LinkedHashMap<>();
        private record HaloObservation(Object state,java.util.List<GroundGeometryCellJournal.Box> collision){
            private HaloObservation {collision=java.util.List.copyOf(collision);}
            private boolean same(HaloObservation actual){return actual!=null&&state==actual.state&&collision.equals(actual.collision);}
        }
        private GroundYawHalo(Minecraft c,McGroundProfileGeometry.Pass pass){client=c;player=c.player;level=c.level;this.pass=pass;}
        private HaloObservation read(GroundGeometryCellJournal.Cell cell){
            if(--pass.remaining[0]<0||!client.isSameThread()||client.player!=player||client.level!=level||level==null
                    ||cell.y()<level.getMinY()||cell.y()>=level.getMaxY()
                    ||!level.getChunkSource().hasChunk(cell.x()>>4,cell.z()>>4))return null;
            var pos=new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z());var state=level.getBlockState(pos);
            var shape=state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player)).toAabbs();
            if(shape.size()>128)return null;
            return new HaloObservation(state,shape.stream().map(b->new GroundGeometryCellJournal.Box(
                    b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList());
        }
        private boolean capture(CapturedProfileGroundYawEnvelope.Envelope envelope,DynamicRouteQueryFence fence){
            return captureRectangle(envelope.minimum(),envelope.maximum(),fence);
        }
        private boolean captureRectangle(Vec3 min,Vec3 max,DynamicRouteQueryFence fence){
            if(player==null||level==null||!fence.available())return false;
            var dimensions=player.getDimensions(Pose.STANDING);double half=dimensions.width()/2D;
            // Native supported-ground move attempts down to -.081, before its support clamp.
            // BlockCollisions iterates a complete one-block halo around that swept body.
            double loX=Math.floor(min.x()-half-.025D-1E-7D)-1D,hiX=Math.floor(max.x()+half+.025D+1E-7D)+1D;
            double loY=Math.floor(min.y()-.081D-1E-7D)-1D,hiY=Math.floor(max.y()+dimensions.height()+1E-7D)+1D;
            double loZ=Math.floor(min.z()-half-.025D-1E-7D)-1D,hiZ=Math.floor(max.z()+half+.025D+1E-7D)+1D;
            double volume=(hiX-loX+1D)*(hiY-loY+1D)*(hiZ-loZ+1D);
            if(!Double.isFinite(volume)||volume<1D||volume>pass.remaining[0]
                    ||loX<Integer.MIN_VALUE||hiX>=Integer.MAX_VALUE||loZ<Integer.MIN_VALUE||hiZ>=Integer.MAX_VALUE
                    ||loY<level.getMinY()||hiY>=level.getMaxY())return false;
            for(int x=(int)loX;x<=(int)hiX;x++)for(int y=(int)loY;y<=(int)hiY;y++)for(int z=(int)loZ;z<=(int)hiZ;z++){
                if(!fence.available())return false;
                var cell=new GroundGeometryCellJournal.Cell(x,y,z);var observed=read(cell);
                if(observed==null)return false;cells.put(cell,observed);
            }
            return !cells.isEmpty()&&fence.available();
        }
        /** Raw native-only leaves after the last role/provider callback. */
        private boolean revalidate(DynamicRouteQueryFence fence){
            if(cells.isEmpty()||client.player!=player||client.level!=level||!client.isSameThread())return false;
            for(var entry:cells.entrySet())if(!fence.available()||!entry.getValue().same(read(entry.getKey())))return false;
            return pass.remaining[0]>=0&&fence.available()&&client.player==player&&client.level==level;
        }
    }
    public static final class Context {
        private final GroundNativePhysicsProfile profile; private final GroundProfileMotionCarrier.NativePose pose;
        private final float friction;private final int remaining;private final double bodyHeight;
        private Context(GroundNativePhysicsProfile p,GroundProfileMotionCarrier.NativePose pose,float friction,int remaining,double height){
            profile=p;this.pose=pose;this.friction=friction;this.remaining=remaining;bodyHeight=height;}
        public GroundNativePhysicsProfile profile(){return profile;}
        public GroundProfileMotionCarrier.NativePose pose(){return pose;}
        public float friction(){return friction;}public int remainingEffectTicks(){return remaining;}public double bodyHeight(){return bodyHeight;}
    }
    public static Optional<Context> current(Minecraft c,McGroundNativePhysicsProfile.Journey j){
        if(c==null||j==null||!j.capturedClient(c)||c.player==null||c.level==null)return Optional.empty();
        var p=j.current();if(p.isEmpty())return Optional.empty();var actor=c.player;
        var support=actor.getBlockPosBelowThatAffectsMyMovement();
        if(!c.level.getChunkSource().hasChunk(support.getX()>>4,support.getZ()>>4))return Optional.empty();
        float friction=c.level.getBlockState(support).getBlock().getFriction();
        if(actor.onGround()&&!Float.isFinite(p.get().groundAcceleration(friction)))return Optional.empty();
        int remaining=-1;for(var effect:actor.getActiveEffects()){int d=effect.getDuration();if(d!=-1)remaining=remaining==-1?d:Math.min(remaining,d);}
        if(actor.getActiveEffects().isEmpty())remaining=0;
        var v=actor.getDeltaMovement();var pose=new GroundProfileMotionCarrier.NativePose(new Vec3(actor.getX(),actor.getY(),actor.getZ()),
                new Vec3(v.x,v.y,v.z),actor.getYRot(),actor.onGround(),actor.isSprinting(),actor.tickCount,actor.getSpeed());
        double height=actor.getDimensions(Pose.STANDING).height();
        if(!j.callbackFreeForecastCurrent(p.get().state(),1))return Optional.empty();
        return Optional.of(new Context(p.get(),pose,friction,remaining,height));
    }
    private static boolean nativeStart(Minecraft c,Context context){return c.player!=null
            &&McGroundProfileGeometry.sameStart(c,context.pose().position(),context.pose().velocity(),context.pose().nativeTick(),context.pose().onGround())
            &&Float.floatToIntBits(c.player.getYRot())==Float.floatToIntBits(context.pose().yaw());}
    private static boolean traceStart(Context ctx,CapturedProfileTrajectory.Trace t){return t!=null&&!t.samples().isEmpty()
            &&t.samples().size()<=64&&ctx.profile().state().equals(t.profileState())&&ctx.pose().position().equals(t.origin())
            &&ctx.pose().velocity().equals(t.observedVelocity())&&Float.floatToIntBits(ctx.pose().yaw())==Float.floatToIntBits(t.yaw());}
    private static boolean supported(Minecraft c,Vec3 p,float friction,McGroundProfileGeometry.Pass pass){
        return McGroundProfileGeometry.groundFriction(c,p,p,friction,pass);}
    public static boolean sweptTrace(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Vec3 landing,boolean allowFinalContact){return trace(c,j,trace,landing,allowFinalContact,null,null);}
    /** Additional ALL-heading first pulse and infinite neutral-coast proof. It augments the
     * privately selected nominal trace journal; neither the pure envelope nor an endpoint is a permit.
     * Full rectangle body/support/speed/friction checks conservatively reject unsafe narrow paths.
     */
    public static boolean sweptGroundYawEnvelope(Minecraft c,McGroundNativePhysicsProfile.Journey j,
            CapturedProfileTrajectory.Trace trace,CapturedProfileGroundYawEnvelope.Envelope envelope){
        if(envelope==null||trace==null||trace.samples().size()!=33
                ||trace.samples().getFirst().phase()!=CapturedProfileTrajectory.Phase.SUPPORTED_GROUND
                ||trace.samples().getFirst().input()!=envelope.input()
                ||!trace.profileState().equals(envelope.profileState())||!trace.origin().equals(envelope.origin())
                ||!trace.observedVelocity().equals(envelope.observedVelocity())
                ||Float.floatToIntBits(trace.yaw())!=Float.floatToIntBits(envelope.yaw())
                ||Float.floatToIntBits(trace.friction())!=Float.floatToIntBits(envelope.friction()))return false;
        for(int i=1;i<trace.samples().size();i++)if(trace.samples().get(i).input()!=CapturedProfileTrajectory.Input.NEUTRAL
                ||trace.samples().get(i).phase()!=CapturedProfileTrajectory.Phase.SUPPORTED_GROUND)return false;
        if(!sweptTrace(c,j,trace,null,false))return false;
        var proof=selectedProof(c,j,trace);var context=current(c,j).orElse(null);
        if(proof==null||context==null||proof.groundYawEnvelope!=null||!traceStart(context,trace)
                ||!context.pose().onGround())return false;
        var pass=proof.pass;var fence=proof.originalBudget;
        var min=envelope.minimum();var max=envelope.maximum();
        // Compensate actual native float half-width against the existing helper's .3 constant.
        var footing=envelope.footing(c.player.getDimensions(Pose.STANDING).width()).orElse(null);
        if(footing==null)return false;
        var feetMin=footing.minimum();var feetMax=footing.maximum();
        if(!fence.query(()->McGroundProfileGeometry.union(c,min,max,pass,.025D))
                ||!fence.query(()->McGroundProfileGeometry.groundFriction(c,feetMin,feetMax,envelope.friction(),pass)))return false;
        var halo=new GroundYawHalo(c,pass);
        if(!halo.capture(envelope,fence))return false;
        // The last Journey/role callback precedes every final native geometry/source validation.
        if(!j.matchesForecast(trace.profileState(),trace.samples().size())||!nativeStart(c,context)
                ||!pass.revalidate(fence)||!halo.revalidate(fence)
                ||!j.callbackFreeForecastCurrent(trace.profileState(),trace.samples().size())
                ||!nativeStart(c,context)||!fence.available())return false;
        proof.groundYawEnvelope=envelope;proof.groundYawHalo=halo;return true;
    }
    /** Requires the same privately selected journal and envelope object; not a new geometry query. */
    public static boolean hasGroundYawEnvelope(Minecraft c,McGroundNativePhysicsProfile.Journey j,
            CapturedProfileTrajectory.Trace trace,CapturedProfileGroundYawEnvelope.Envelope envelope){
        var proof=selectedProof(c,j,trace);
        return envelope!=null&&proof!=null&&!proof.retired&&proof.groundYawEnvelope==envelope&&proof.groundYawHalo!=null;
    }
    /** Coherent native eligibility group: one lookup per unique cell on each side of the last
     * callbacks. Actor-context collision and physical/default support facts remain DISTINCT.
     * Only this NEW initial producer deduplicates; all existing Pass/read/group APIs are unchanged.
     */
    private static final class InitialDomainProof {
        private final Minecraft client;private final net.minecraft.client.player.LocalPlayer player;
        private final net.minecraft.client.multiplayer.ClientLevel level;private final McGroundProfileGeometry.Pass pass;
        private final java.util.LinkedHashMap<GroundGeometryCellJournal.Cell,CellPlan> plan=new java.util.LinkedHashMap<>();
        private final java.util.LinkedHashMap<GroundGeometryCellJournal.Cell,CellFact> facts=new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<net.minecraft.world.phys.AABB> bodies=new java.util.ArrayList<>();
        private final java.util.ArrayList<Boolean> bodyClear=new java.util.ArrayList<>();
        private final Context context;private boolean unsupported;
        private static final class CellPlan {boolean physical,support;}
        private record CellFact(Object state,java.util.List<GroundGeometryCellJournal.Box> actorShape,
                GroundGeometryCellJournal.Observation physical){
            private CellFact {actorShape=java.util.List.copyOf(actorShape);}
            private boolean same(CellFact other){
                if(GroundInitialDirectionalDiagnostics.copy(16,other==null)||GroundInitialDirectionalDiagnostics.copy(17,state!=other.state)||GroundInitialDirectionalDiagnostics.copy(18,!actorShape.equals(other.actorShape))){GroundInitialDirectionalDiagnostics.returned("initialCellFact.same.originalRefusal0"); return false;}
                if(physical==null)return other.physical==null;
                var b=other.physical;boolean originalFinal=GroundInitialDirectionalDiagnostics.copy(19,b!=null)&&GroundInitialDirectionalDiagnostics.copy(20,physical.nativeState()==b.nativeState()
                        )&&GroundInitialDirectionalDiagnostics.copy(21,physical.shape().equals(b.shape()))&&GroundInitialDirectionalDiagnostics.copy(22,physical.supportShape().equals(b.supportShape())
                        )&&GroundInitialDirectionalDiagnostics.copy(23,physical.sturdyUp()==b.sturdyUp()
                        )&&GroundInitialDirectionalDiagnostics.copy(24,Float.floatToIntBits(physical.friction())==Float.floatToIntBits(b.friction())
                        )&&GroundInitialDirectionalDiagnostics.copy(25,Float.floatToIntBits(physical.speedFactor())==Float.floatToIntBits(b.speedFactor())); if(!originalFinal)GroundInitialDirectionalDiagnostics.returned("initialCellFact.same.originalFinalReturn");return originalFinal;
            }
        }
        private InitialDomainProof(Minecraft c,McGroundProfileGeometry.Pass pass,Context context){
            client=c;player=c.player;level=c.level;this.pass=pass;this.context=context;
        }
        private boolean add(int x,int y,int z,boolean physical,boolean support){
            var key=new GroundGeometryCellJournal.Cell(x,y,z);var entry=plan.get(key);
            if(entry==null){if(GroundInitialDirectionalDiagnostics.copy(26,plan.size()>=2048)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.add.originalRefusal0"); return false;}entry=new CellPlan();plan.put(key,entry);}
            entry.physical|=physical;entry.support|=support;return true;
        }
        /** Pure finite-domain capacity check BEFORE native cell reads. Exceeding this new model's
         * supported domain is initial ineligibility, distinct from an exhausted/dirty running proof.
         */
        private boolean prepare(CapturedDirectionalPlaneCourse.LeadDomain domain,DynamicRouteQueryFence fence){
            var dimensions=player.getDimensions(Pose.STANDING);int supportY=(int)Math.floor(context.pose().position().y()-.001D);
            for(var rectangle:domain.rectangles()){GroundInitialDirectionalDiagnostics.rectangle();
                if(GroundInitialDirectionalDiagnostics.copy(27,!fence.available())||GroundInitialDirectionalDiagnostics.copy(28,bodies.size()>=65)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalRefusal0"); return false;}
                var min=rectangle.minimum();var max=rectangle.maximum();
                var bounds=GroundLandingBodyEnvelope.bounds(min,max,dimensions.width(),dimensions.height()).orElse(null);
                if(GroundInitialDirectionalDiagnostics.copy(29,bounds==null)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalRefusal1"); return false;}
                var body=new net.minecraft.world.phys.AABB(bounds.minX()-.025D,bounds.minY(),bounds.minZ()-.025D,
                        bounds.maxX()+.025D,bounds.maxY(),bounds.maxZ()+.025D);
                double loX=Math.floor(body.minX-1E-7D)-1D,hiX=Math.floor(body.maxX+1E-7D)+1D;
                double loY=Math.floor(body.minY-.081D-1E-7D)-1D,hiY=Math.floor(body.maxY+1E-7D)+1D;
                double loZ=Math.floor(body.minZ-1E-7D)-1D,hiZ=Math.floor(body.maxZ+1E-7D)+1D;
                double volume=(hiX-loX+1D)*(hiY-loY+1D)*(hiZ-loZ+1D);
                if(GroundInitialDirectionalDiagnostics.copy(30,!Double.isFinite(volume))||GroundInitialDirectionalDiagnostics.copy(31,volume<1D)||GroundInitialDirectionalDiagnostics.copy(32,volume>2048D)||GroundInitialDirectionalDiagnostics.copy(33,loX<Integer.MIN_VALUE)||GroundInitialDirectionalDiagnostics.copy(34,hiX>=Integer.MAX_VALUE
                        )||GroundInitialDirectionalDiagnostics.copy(35,loZ<Integer.MIN_VALUE)||GroundInitialDirectionalDiagnostics.copy(36,hiZ>=Integer.MAX_VALUE)||GroundInitialDirectionalDiagnostics.copy(37,loY<level.getMinY())||GroundInitialDirectionalDiagnostics.copy(38,hiY>=level.getMaxY())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalRefusal2"); return false;}
                for(int x=(int)loX;x<=(int)hiX;x++)for(int y=(int)loY;y<=(int)hiY;y++)for(int z=(int)loZ;z<=(int)hiZ;z++){
                    if(GroundInitialDirectionalDiagnostics.copy(39,!fence.available())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalRefusal3"); return false;}
                    boolean physical=x>=Math.floor(body.minX)&&x<=Math.floor(body.maxX)
                            &&z>=Math.floor(body.minZ)&&z<=Math.floor(body.maxZ)
                            &&y>=Math.floor(body.minY-1D)&&y<=Math.floor(body.maxY);
                    if(GroundInitialDirectionalDiagnostics.copy(40,!add(x,y,z,physical,physical&&y==supportY))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalRefusal4"); return false;}
                }
                bodies.add(body);
            }
            boolean originalFinal=GroundInitialDirectionalDiagnostics.copy(41,!plan.isEmpty())&&GroundInitialDirectionalDiagnostics.copy(42,plan.size()*2<=pass.remaining[0])&&GroundInitialDirectionalDiagnostics.copy(43,fence.available()); GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); if(!originalFinal)GroundInitialDirectionalDiagnostics.returned("initialDomain.prepare.originalFinalReturn");return originalFinal;
        }
        private CellFact read(GroundGeometryCellJournal.Cell cell,CellPlan entry){
            GroundInitialDirectionalDiagnostics.cell(cell.x(),cell.y(),cell.z());
            if(GroundInitialDirectionalDiagnostics.copy(44,--pass.remaining[0]<0)||GroundInitialDirectionalDiagnostics.copy(45,!client.isSameThread())||GroundInitialDirectionalDiagnostics.copy(46,client.player!=player)||GroundInitialDirectionalDiagnostics.copy(47,client.level!=level
                    )||GroundInitialDirectionalDiagnostics.copy(48,!level.getChunkSource().hasChunk(cell.x()>>4,cell.z()>>4))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.read.originalRefusal0"); return null;}
            var pos=new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z());var state=level.getBlockState(pos);
            var actor=state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player)).toAabbs();
            if(GroundInitialDirectionalDiagnostics.copy(49,actor.size()>128)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.read.originalRefusal1"); return null;}
            var actorBoxes=actor.stream().map(b->new GroundGeometryCellJournal.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
            GroundGeometryCellJournal.Observation physical=null;
            if(entry.physical){
                var shape=state.getCollisionShape(level,pos).toAabbs();var support=state.getBlockSupportShape(level,pos).toAabbs();
                if(GroundInitialDirectionalDiagnostics.copy(50,shape.size()>128)||GroundInitialDirectionalDiagnostics.copy(51,support.size()>128)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.read.originalRefusal2"); return null;}
                physical=new GroundGeometryCellJournal.Observation(state,
                        shape.stream().map(b->new GroundGeometryCellJournal.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList(),
                        state.getBlock().getFriction(),state.getBlock().getSpeedFactor(),
                        support.stream().map(b->new GroundGeometryCellJournal.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList(),
                        state.isFaceSturdy(level,pos,net.minecraft.core.Direction.UP));
            }
            return new CellFact(state,actorBoxes,physical);
        }
        private boolean capture(DynamicRouteQueryFence fence){
            // No role/provider callback may occur inside this coherent unique native group.
            for(var entry:plan.entrySet()){
                if(GroundInitialDirectionalDiagnostics.copy(52,!fence.available())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalRefusal0"); return false;}var actual=read(entry.getKey(),entry.getValue());
                if(GroundInitialDirectionalDiagnostics.copy(53,actual==null)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalRefusal1"); return false;}facts.put(entry.getKey(),actual);
                if(GroundInitialDirectionalDiagnostics.copy(54,entry.getValue().physical)&&GroundInitialDirectionalDiagnostics.copy(55,!pass.cells.observe(entry.getKey(),actual.physical()))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalRefusal2"); return false;}
                if(entry.getValue().support&&!CapturedDirectionalPlaneCourse.supported(actual.physical(),entry.getKey().y(),context.pose().position().y(),context.friction()))unsupported=true;
            }
            for(var entry:plan.entrySet())if(entry.getValue().physical){
                var key=entry.getKey();var fact=facts.get(key);var state=(net.minecraft.world.level.block.state.BlockState)fact.state();
                if(Float.floatToIntBits(fact.physical().speedFactor())!=Float.floatToIntBits(1F)
                        ||!state.getFluidState().isEmpty()||state.is(net.minecraft.world.level.block.Blocks.FIRE)
                        ||state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||state.is(net.minecraft.world.level.block.Blocks.COBWEB)
                        ||state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        ||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                        ||state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                        ||state.is(net.minecraft.tags.BlockTags.CLIMBABLE))unsupported=true;
                if(state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                        &&state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)){
                    var below=facts.get(new GroundGeometryCellJournal.Cell(key.x(),key.y()-1,key.z()));
                    if(GroundInitialDirectionalDiagnostics.copy(56,below==null)){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalRefusal3"); return false;}
                    var lower=(net.minecraft.world.level.block.state.BlockState)below.state();
                    if(lower.is(net.minecraft.world.level.block.Blocks.LADDER)
                            &&lower.getValue(net.minecraft.world.level.block.LadderBlock.FACING)
                                ==state.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING))unsupported=true;
                }
            }
            for(var body:bodies){GroundInitialDirectionalDiagnostics.body();if(GroundInitialDirectionalDiagnostics.copy(57,!fence.available())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalRefusal4"); return false;}boolean clear=level.noCollision(player,body);bodyClear.add(clear);if(!clear)unsupported=true;}
            boolean originalFinal=GroundInitialDirectionalDiagnostics.copy(58,fence.available()); GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); if(!originalFinal)GroundInitialDirectionalDiagnostics.returned("initialDomain.capture.originalFinalReturn");return originalFinal;
        }
        private boolean revalidate(DynamicRouteQueryFence fence){
            if(GroundInitialDirectionalDiagnostics.copy(97,client.player!=player)||GroundInitialDirectionalDiagnostics.copy(98,client.level!=level)||GroundInitialDirectionalDiagnostics.copy(99,!client.isSameThread())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalRefusal0");return false;}
            for(var entry:plan.entrySet()){
                if(GroundInitialDirectionalDiagnostics.copy(100,!fence.available())){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalRefusal1");return false;}var actual=read(entry.getKey(),entry.getValue());
                if(GroundInitialDirectionalDiagnostics.copy(101,!facts.get(entry.getKey()).same(actual))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalRefusal2");return false;}
                if(GroundInitialDirectionalDiagnostics.copy(102,entry.getValue().physical)&&GroundInitialDirectionalDiagnostics.copy(103,!pass.cells.observe(entry.getKey(),actual.physical()))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalRefusal3");return false;}
            }
            for(int i=0;i<bodies.size();i++)if(GroundInitialDirectionalDiagnostics.copy(104,!fence.available())||GroundInitialDirectionalDiagnostics.copy(105,level.noCollision(player,bodies.get(i))!=bodyClear.get(i))){GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalRefusal4");return false;}
            boolean originalFinal=GroundInitialDirectionalDiagnostics.copy(106,pass.remaining[0]>=0)&&GroundInitialDirectionalDiagnostics.copy(107,fence.available())&&GroundInitialDirectionalDiagnostics.copy(108,client.player==player)&&GroundInitialDirectionalDiagnostics.copy(109,client.level==level);GroundInitialDirectionalDiagnostics.counts(plan.size(),facts.size(),bodies.size(),pass.remaining[0]); if(!originalFinal)GroundInitialDirectionalDiagnostics.returned("initialDomain.revalidate.originalFinalReturn");return originalFinal;
        }
    }
    public enum InitialDirectionalPlaneKind { INITIAL_SUPPORTED, UNSUPPORTED_INITIAL_GEOMETRY, CUSTODY_REFUSED }
    /** Initial vanilla plane selection only. The actual eager prior-edge/lead domain is checked
     * under the ORIGINAL navigator fence; every later pulse still requires its own fresh proof.
     */
    public static InitialDirectionalPlaneKind initialDirectionalPlane(Minecraft c,McGroundNativePhysicsProfile.Journey j,
            java.util.List<Vec3> exactEagerCourse,DynamicRouteQueryFence originalFence){
        if(GroundInitialDirectionalDiagnostics.copy(69,originalFence==null)||GroundInitialDirectionalDiagnostics.copy(70,!originalFence.available())){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal0"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        var context=current(c,j).orElse(null);
        if(GroundInitialDirectionalDiagnostics.copy(71,context==null)||GroundInitialDirectionalDiagnostics.copy(72,!context.pose().onGround()
                )||GroundInitialDirectionalDiagnostics.copy(73,!GroundOwnedSprintTransition.directionalVanillaProfileSupported(context.profile().state()))){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal1"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        if(GroundInitialDirectionalDiagnostics.copy(74,exactEagerCourse==null)||GroundInitialDirectionalDiagnostics.copy(75,exactEagerCourse.isEmpty())||GroundInitialDirectionalDiagnostics.copy(76,GroundInitialDirectionalDiagnostics.courseSize(exactEagerCourse.size())>6001)){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal2"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        var immutableCourse=java.util.List.copyOf(exactEagerCourse);
        if(GroundInitialDirectionalDiagnostics.copy(77,!immutableCourse.getFirst().equals(context.pose().position()))){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal3"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        var domain=CapturedDirectionalPlaneCourse.leadDomain(immutableCourse).orElse(null);
        var pass=new McGroundProfileGeometry.Pass(c);var proof=new InitialDomainProof(c,pass,context);
        if(GroundInitialDirectionalDiagnostics.copy(91,domain==null)||GroundInitialDirectionalDiagnostics.copy(92,!proof.prepare(domain,originalFence))){
            // Deterministically unsupported initial finite model domain is separate from expiry.
            boolean originalFallback=GroundInitialDirectionalDiagnostics.copy(93,originalFence.query(()->GroundInitialDirectionalDiagnostics.copy(87,j.matchesForecast(context.profile().state(),1))&&GroundInitialDirectionalDiagnostics.copy(88,nativeStart(c,context)))
                    )&&GroundInitialDirectionalDiagnostics.copy(94,j.callbackFreeForecastCurrent(context.profile().state(),1))&&GroundInitialDirectionalDiagnostics.copy(95,nativeStart(c,context))&&GroundInitialDirectionalDiagnostics.copy(96,originalFence.available());if(!originalFallback)GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalFallbackCustody");
            return originalFallback?InitialDirectionalPlaneKind.UNSUPPORTED_INITIAL_GEOMETRY:InitialDirectionalPlaneKind.CUSTODY_REFUSED;
        }
        if(GroundInitialDirectionalDiagnostics.copy(78,!originalFence.query(()->GroundInitialDirectionalDiagnostics.copy(89,j.matchesForecast(context.profile().state(),1))&&GroundInitialDirectionalDiagnostics.copy(90,nativeStart(c,context)))
                )||GroundInitialDirectionalDiagnostics.copy(79,!proof.capture(originalFence))){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal4"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        // Last real Root/Journey callbacks precede every exact unique native reread and outcome.
        if(GroundInitialDirectionalDiagnostics.copy(80,!originalFence.query(()->true))||GroundInitialDirectionalDiagnostics.copy(81,!j.matchesForecast(context.profile().state(),1))||GroundInitialDirectionalDiagnostics.copy(82,!nativeStart(c,context)
                )||GroundInitialDirectionalDiagnostics.copy(83,!proof.revalidate(originalFence))||GroundInitialDirectionalDiagnostics.copy(84,!j.callbackFreeForecastCurrent(context.profile().state(),1)
                )||GroundInitialDirectionalDiagnostics.copy(85,!nativeStart(c,context))||GroundInitialDirectionalDiagnostics.copy(86,!originalFence.available())){GroundInitialDirectionalDiagnostics.returned("initialDirectionalPlane.originalRefusal5"); return InitialDirectionalPlaneKind.CUSTODY_REFUSED;}
        return proof.unsupported?InitialDirectionalPlaneKind.UNSUPPORTED_INITIAL_GEOMETRY:InitialDirectionalPlaneKind.INITIAL_SUPPORTED;
    }
    public static boolean groundExitTrace(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Vec3 origin,Vec3 landing){
        if(!GroundLocomotionMotion.finite(origin)||!GroundLocomotionMotion.finite(landing)
                ||origin.y()-landing.y()<=.6||origin.y()-landing.y()>1.001
                ||Math.abs(GroundPathfinder.node(origin).x()-GroundPathfinder.node(landing).x())
                    +Math.abs(GroundPathfinder.node(origin).z()-GroundPathfinder.node(landing).z())!=1)return false;
        return trace(c,j,trace,landing,true,origin,null);
    }
    public static boolean groundExitTrace(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Vec3 origin,Vec3 landing,GroundProfileMotionCarrier.ObservedStep ownedLatch){
        if(!CapturedProfileMotionMath.originalDrop(origin,landing))return false;
        return trace(c,j,trace,landing,true,origin,ownedLatch);
    }
    private static boolean latchNativeIdentity(Minecraft c,GroundProfileMotionCarrier.ObservedStep latch,Context context){
        if(latch==null)return true;
        if(c.player==null||!((Object)c.player instanceof GroundProfileMotionCarrier carrier)||!carrier.ownsProfileStep(latch)
                ||carrier.profileNativeAfter(latch.nativeIdentity())!=latch||latch.before()==null||latch.after()==null
                ||latch.issuedInputs()==null||latch.actualJumpCalls()!=0)return false;
        var after=latch.after();var now=context.pose();
        return after.position().equals(now.position())&&after.velocity().equals(now.velocity())
                &&after.nativeTick()==now.nativeTick()&&after.onGround()==now.onGround()&&after.sprinting()==now.sprinting()
                &&Float.floatToIntBits(after.yaw())==Float.floatToIntBits(now.yaw())
                &&Float.floatToIntBits(after.travelSpeed())==Float.floatToIntBits(now.travelSpeed())
                &&after.nativeTick()==latch.before().nativeTick()
                &&(long)after.nativeTick()-latch.issuedInputs().issuedTick()==1;
    }
    /** Actual old Y-clamped step, current unsupported onGround latch; no caller pose/flag is trusted. */
    private static boolean ownedUnsupportedLatch(Minecraft c,Context context,GroundProfileMotionCarrier.ObservedStep latch,
            Vec3 original,Vec3 landing,McGroundProfileGeometry.Pass pass){
        if(latch==null||!latchNativeIdentity(c,latch,context)||!latch.issuedInputs().nativeCurrent()
                ||!CapturedProfileMotionMath.originalDrop(original,landing)||!context.pose().onGround()
                ||context.profile().sprinting()||!latch.before().onGround()||!latch.after().onGround()
                ||latch.before().sprinting()||latch.after().sprinting()
                ||Math.abs(latch.before().position().y()-original.y())>1E-6
                ||Math.abs(latch.after().position().y()-original.y())>1E-6
                ||latch.before().position().horizontalDistanceTo(original)>2
                ||latch.after().position().horizontalDistanceTo(original)>2
                ||latch.before().velocity().y()>=0D
                ||Float.floatToIntBits(latch.before().travelSpeed())!=Float.floatToIntBits(context.profile().state().travelSpeed()))return false;
        CapturedProfileTrajectory.Input actualInput=null;
        for(var input:CapturedProfileTrajectory.Input.values()){
            // Compare the real observed key pattern; mathematical Controls remain privately constructed.
            boolean same=true;for(var key:LegacyTravelRetirement.Key.values()){
                boolean expected=switch(key){
                    case FORWARD -> input.forward()>0;
                    case BACK -> input.forward()<0;
                    case LEFT -> input.left()>0;
                    case RIGHT -> input.left()<0;
                    default -> false;
                };
                if(latch.issuedInputs().held(key)!=expected)same=false;
            }
            if(same){actualInput=input;break;}
        }
        if(actualInput==null||CapturedProfileMotionMath.inputAndJump(actualInput,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,
                latch.travelInput(),latch.actualJumpCalls())!=CapturedProfileMotionMath.Match.MATCHED)return false;
        var previous=CapturedProfileTrajectory.frame(context.profile(),latch.before().position(),latch.before().velocity(),
                context.friction(),latch.before().yaw(),actualInput,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,
                CapturedProfileTrajectory.JumpIntent.RELEASE,CapturedProfileTrajectory.SprintIntent.RELEASE).orElse(null);
        var here=context.pose().position();
        return previous!=null&&CapturedProfileMotionMath.freeMotion(previous,latch.after())==CapturedProfileMotionMath.Match.MATCHED
                &&collisionMatches(c,latch.before().position(),latch.after().position(),previous.intendedMove())
                &&collisionMatches(c,latch.before().position(),latch.before().position(),new Vec3(0,-.001,0))
                &&collisionMatches(c,here,new Vec3(here.x(),here.y()-.001,here.z()),new Vec3(0,-.001,0))
                &&McGroundProfileGeometry.union(c,latch.before().position(),latch.after().position(),pass,.025);
    }
    private static boolean trace(Minecraft c,McGroundNativePhysicsProfile.Journey j,CapturedProfileTrajectory.Trace trace,
            Vec3 landing,boolean allowContact,Vec3 dropOrigin,GroundProfileMotionCarrier.ObservedStep ownedLatch){
        var context=current(c,j).orElse(null);if(context==null||!traceStart(context,trace))return false;
        var fence=new DynamicRouteQueryFence(System::nanoTime,()->j.matchesForecast(trace.profileState(),trace.samples().size())&&nativeStart(c,context)
                &&(ownedLatch==null||latchNativeIdentity(c,ownedLatch,context)&&ownedLatch.issuedInputs().nativeCurrent()),50_000_000L);
        var pass=new McGroundProfileGeometry.Pass(c);Vec3 previous=trace.origin();
        if(context.pose().onGround()&&dropOrigin==null&&!fence.query(()->supported(c,trace.origin(),trace.friction(),pass)))return false;
        if(dropOrigin!=null){
            if(!fence.query(()->supported(c,dropOrigin,trace.friction(),pass))
                    ||trace.origin().y()>dropOrigin.y()+.081||trace.origin().y()<landing.y()-.081
                    ||trace.origin().horizontalDistanceTo(dropOrigin)>2)return false;
            if(context.pose().onGround()){
                boolean supportedNow=fence.query(()->collisionMatches(c,trace.origin(),trace.origin(),new Vec3(0,-.001,0)));
                if(!supportedNow){
                    if(trace.samples().getFirst().phase()!=CapturedProfileTrajectory.Phase.GROUNDED_EXIT
                            ||!fence.query(()->ownedUnsupportedLatch(c,context,ownedLatch,dropOrigin,landing,pass)))return false;
                }else if(ownedLatch!=null)return false;
            }else if(ownedLatch!=null)return false;
        }
        for(int i=0;i<trace.samples().size();i++){
            var sample=trace.samples().get(i);Vec3 next=sample.position();
            boolean last=i==trace.samples().size()-1;
            if(allowContact&&last&&landing!=null&&previous.y()>=landing.y()&&next.y()<=landing.y()
                    &&sample.intendedMove().y()<0){
                if(next.horizontalDistanceTo(landing)>.075||Math.hypot(sample.intendedMove().x(),sample.intendedMove().z())>.05)return false;
                next=new Vec3(next.x(),landing.y(),next.z());var contact=next;
                if(!fence.query(()->supported(c,contact,trace.friction(),pass)))return false;
            }else if(dropOrigin==null&&sample.condition()==CapturedProfileTrajectory.Condition.FRESH_FLAT_SUPPORT_CLAMP){
                var point=next;if(!fence.query(()->supported(c,point,trace.friction(),pass)))return false;
            }
            var from=previous;var to=next;var intended=sample.intendedMove();
            // Vanilla collision is read-only here; compare exact axis-clamped displacement.
            // No player pose/velocity/ground flag is written to make the preview fit.
            if(!fence.query(()->collisionMatches(c,from,to,intended)))return false;
            if(!fence.query(()->McGroundProfileGeometry.union(c,from,to,pass,.025)))return false;
            previous=next;
        }
        // Last real role callback precedes ALL coherent final native world revalidation.
        boolean valid=j.matchesForecast(trace.profileState(),trace.samples().size())&&nativeStart(c,context)&&pass.revalidate(fence)
                &&j.callbackFreeForecastCurrent(trace.profileState(),trace.samples().size())&&nativeStart(c,context)
                &&(ownedLatch==null||latchNativeIdentity(c,ownedLatch,context)&&ownedLatch.issuedInputs().nativeCurrent())&&fence.available();
        if(valid)remember(c,j,trace,context,pass,fence,ownedLatch);
        return valid;
    }
    private static boolean collisionMatches(Minecraft c,Vec3 from,Vec3 to,Vec3 intended){
        if(c.player==null||c.level==null)return false;
        var p=c.player;var box=p.getBoundingBox().move(from.x()-p.getX(),from.y()-p.getY(),from.z()-p.getZ());
        var moved=net.minecraft.world.entity.Entity.collideBoundingBox(p,new net.minecraft.world.phys.Vec3(
                intended.x(),intended.y(),intended.z()),box,c.level,java.util.List.of());
        return Math.abs(from.x()+moved.x-to.x())<=.000001&&Math.abs(from.y()+moved.y-to.y())<=.000001
                &&Math.abs(from.z()+moved.z-to.z())<=.000001;
    }
    /** Exact full-horizon stationary math selects this native producer; never a Boolean permit. */
    public static boolean stationaryNeutral(Minecraft c,McGroundNativePhysicsProfile.Journey j,
            CapturedProfileTrajectory.Trace trace,GroundProfileForecast.Brake brake){
        var context=current(c,j).orElse(null);
        if(context==null||!context.pose().onGround()||context.pose().sprinting()||!traceStart(context,trace))return false;
        var descriptor=GroundStationaryNeutralProof.describe(context.profile(),trace,brake).orElse(null);
        if(!StationaryNeutralHorizon.covered(descriptor)||!descriptor.profileState().equals(context.profile().state())
                ||Float.floatToIntBits(descriptor.friction())!=Float.floatToIntBits(context.friction()))return false;
        int horizon=descriptor.maxHorizon();
        var fence=new DynamicRouteQueryFence(System::nanoTime,()->j.matchesForecast(descriptor.profileState(),horizon)&&nativeStart(c,context),50_000_000L);
        var pass=new McGroundProfileGeometry.Pass(c);
        // No role callback occurs inside this one bounded unique native geometry group.
        if(!fence.query(()->pass.stationaryNativeGroup(descriptor,context.pose())))return false;
        // The last real role callback precedes complete native-only revalidation of ALL dependencies.
        boolean valid=j.matchesForecast(descriptor.profileState(),horizon)&&nativeStart(c,context)
                &&fence.copyOriginalInitialJournalReturn(pass.revalidate(fence))&&j.callbackFreeForecastCurrent(descriptor.profileState(),horizon)
                &&nativeStart(c,context)&&fence.available();
        if(valid)remember(c,j,trace,context,pass,fence,null,horizon);
        return valid&&selectedProofs.containsKey(trace);
    }
    public static boolean groundCoast(Minecraft c,McGroundNativePhysicsProfile.Journey j,GroundProfileForecast.Brake brake){
        return brake!=null&&McGroundProfileGeometry.groundBrake(c,j,brake.origin(),brake);}
    public static boolean standingContact(Minecraft c,McGroundNativePhysicsProfile.Journey j,GroundProfileMotionCarrier.NativePose observed,
            Vec3 landing,float friction){
        var ctx=current(c,j).orElse(null);if(ctx==null||observed==null||!observed.equals(ctx.pose())||!observed.onGround()
                ||observed.position().horizontalDistanceTo(landing)>.075||Math.abs(observed.position().y()-landing.y())>.08)return false;
        var brake=GroundProfileForecast.groundBrake(ctx.profile(),observed.position(),observed.velocity(),friction,8).orElse(null);
        return brake!=null&&groundCoast(c,j,brake);
    }
}
