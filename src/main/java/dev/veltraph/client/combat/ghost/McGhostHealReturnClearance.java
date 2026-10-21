package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Fresh native geometry for the paused heal owner; never borrows a source cursor/job. */
final class McGhostHealReturnClearance implements GhostHealDirectReturn.Geometry {
    private static final long MAX_READ_NANOS=4_000_000L;
    private final Minecraft client;private final GhostGrindModule parent;private final GhostHealDirectReturn.Scope scope;
    private final int tick,slot;private final long cameraEpoch,started;private final double halfWidth,height;private final float standingWidth,standingHeight;private final AABB body;private final Pose nativePose;
    private final Vec3 position,motion;private final float yaw,pitch;
    private enum Refusal { NONE, PLAYER_MISSING, BODY_CHANGED, PARENT_CUSTODY_OR_CONFIG, TICK_OR_SLOT_CHANGED, CAMERA_EPOCH_CHANGED, POSITION_OR_VIEW_CHANGED, MOTION_CHANGED, INVALID_ENVELOPE, READ_BUDGET, BODY_UNLOADED, FORBIDDEN_BODY, BODY_HAZARD, BODY_COLLISION, LIVING_ACTOR, SUPPORT_UNLOADED, SUPPORT_HAZARD, SUPPORT_TOP_COVERAGE }
    private enum Phase { FRAME, INITIAL, BODY_SCAN, COLLISION, ACTORS, SUPPORT_SCAN, SUPPORT_UNION, FINAL }
    private Refusal refusal=Refusal.NONE;private Phase phase=Phase.FRAME,refusalPhase=Phase.FRAME;
    private int queries,refusalQuery,currentChecks,bodyCells,supportCells,lastTopCount,blockX,blockY,blockZ;
    private long lastBudgetNanos=-1,refusalBudgetNanos=-1;
    private boolean refuse(Refusal why){if(refusal==Refusal.NONE){refusal=why;refusalPhase=phase;refusalQuery=queries;refusalBudgetNanos=lastBudgetNanos;}return false;}
    /** Cached bounded diagnosis only: no native permission/provider/time reads. */
    public String diagnostic(){return "clearance="+refusal+" query="+refusalQuery+" phase="+refusalPhase+" budgetLastNanos="+refusalBudgetNanos+" currentChecks="+currentChecks+" bodyCells="+bodyCells+" supportCells="+supportCells+" topsLast="+lastTopCount+" blockLast="+blockX+","+blockY+","+blockZ;}

    McGhostHealReturnClearance(Minecraft client,GhostGrindModule parent,GhostHealDirectReturn.Scope scope){
        this.client=client;this.parent=parent;this.scope=scope;
        var p=client.player;tick=p.tickCount;slot=p.getInventory().getSelectedSlot();
        cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        position=new Vec3(p.getX(),p.getY(),p.getZ());var v=p.getDeltaMovement();motion=new Vec3(v.x,v.y,v.z);yaw=p.getYRot();pitch=p.getXRot();
        var size=p.getDimensions(Pose.STANDING);standingWidth=size.width();standingHeight=size.height();halfWidth=standingWidth/2d;height=standingHeight;body=p.getBoundingBox();nativePose=p.getPose();started=System.nanoTime();
    }
    /** Cached incomplete proof classification. No new native query or budget sample. */
    public GhostHealDirectReturn.Failure failure(){
        return switch(refusal){
            case READ_BUDGET -> GhostHealDirectReturn.Failure.INCOMPLETE;
            case BODY_HAZARD,FORBIDDEN_BODY,BODY_COLLISION,LIVING_ACTOR,SUPPORT_HAZARD -> GhostHealDirectReturn.Failure.BLOCKED;
            case SUPPORT_TOP_COVERAGE -> lastTopCount>128?GhostHealDirectReturn.Failure.UNKNOWN:GhostHealDirectReturn.Failure.BLOCKED;
            default -> GhostHealDirectReturn.Failure.UNKNOWN;
        };
    }
    public boolean current(){
        currentChecks++;var p=client.player;if(p==null)return refuse(Refusal.PLAYER_MISSING);var size=p.getDimensions(Pose.STANDING);
        if(!sameBody(body,standingWidth,standingHeight,nativePose,p.getBoundingBox(),size.width(),size.height(),p.getPose()))return refuse(Refusal.BODY_CHANGED);
        if(!parent.healReturnNativeCurrent(scope))return refuse(Refusal.PARENT_CUSTODY_OR_CONFIG);
        if(p.tickCount!=tick||p.getInventory().getSelectedSlot()!=slot)return refuse(Refusal.TICK_OR_SLOT_CHANGED);
        if(dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)!=cameraEpoch)return refuse(Refusal.CAMERA_EPOCH_CHANGED);
        if(p.getX()!=position.x()||p.getY()!=position.y()||p.getZ()!=position.z()||p.getYRot()!=yaw||p.getXRot()!=pitch)return refuse(Refusal.POSITION_OR_VIEW_CHANGED);
        if(p.getDeltaMovement().x!=motion.x()||p.getDeltaMovement().y!=motion.y()||p.getDeltaMovement().z!=motion.z())return refuse(Refusal.MOTION_CHANGED);
        return true;
    }
    public boolean clearEnvelope(Vec3 from,Vec3 to){
        queries++;phase=Phase.INITIAL;if(!current())return false;
        if(from==null||to==null||!finite(from)||!finite(to)||Math.abs(from.y()-to.y())>=.02||from.distanceTo(to)>32
            ||!Double.isFinite(halfWidth)||halfWidth<.01||halfWidth>2||!Double.isFinite(height)||height<.01||height>4)return refuse(Refusal.INVALID_ENVELOPE);
        double minX=Math.min(from.x(),to.x())-halfWidth,maxX=Math.max(from.x(),to.x())+halfWidth;
        double minZ=Math.min(from.z(),to.z())-halfWidth,maxZ=Math.max(from.z(),to.z())+halfWidth;
        // The enclosing continuous AABB is deliberately more conservative than a
        // diagonal line. A wall/gap near it may retain the original path instead.
        var envelope=new AABB(minX,from.y()+.001,minZ,maxX,from.y()+height,maxZ);
        var level=client.level;
        phase=Phase.BODY_SCAN;for(int x=(int)Math.floor(minX);x<=Math.floor(maxX);x++)for(int y=(int)Math.floor(envelope.minY);y<=Math.floor(envelope.maxY);y++)for(int z=(int)Math.floor(minZ);z<=Math.floor(maxZ);z++){
            if(expired())return false;bodyCells++;blockX=x;blockY=y;blockZ=z;var pos=new net.minecraft.core.BlockPos(x,y,z);
            if(!level.getChunkSource().hasChunk(x>>4,z>>4))return refuse(Refusal.BODY_UNLOADED);
            if(parent.healReturnForbidden(x,y,z))return refuse(Refusal.FORBIDDEN_BODY);
            if(hazard(level.getBlockState(pos)))return refuse(Refusal.BODY_HAZARD);
        }
        phase=Phase.COLLISION;if(!level.noCollision(client.player,envelope))return refuse(Refusal.BODY_COLLISION);
        phase=Phase.ACTORS;for(var actor:level.entitiesForRendering())if(actor!=client.player&&actor instanceof LivingEntity living&&living.isAlive()&&actor.getBoundingBox().intersects(envelope))return refuse(Refusal.LIVING_ACTOR);
        // Require exact native top-face coverage of the whole swept foot rectangle,
        // including holes between collision pieces. No sampled/gap-inferred support.
        int y=(int)Math.floor(from.y()-.001);
        phase=Phase.SUPPORT_SCAN;for(int x=(int)Math.floor(minX);x<=Math.floor(maxX);x++)for(int z=(int)Math.floor(minZ);z<=Math.floor(maxZ);z++){
            if(expired())return false;supportCells++;blockX=x;blockY=y;blockZ=z;var pos=new net.minecraft.core.BlockPos(x,y,z);
            if(!level.getChunkSource().hasChunk(x>>4,z>>4))return refuse(Refusal.SUPPORT_UNLOADED);
            var state=level.getBlockState(pos);if(hazard(state))return refuse(Refusal.SUPPORT_HAZARD);
            double x0=Math.max(minX,x)-x,x1=Math.min(maxX,x+1)-x,z0=Math.max(minZ,z)-z,z1=Math.min(maxZ,z+1)-z;
            if(x1<=x0||z1<=z0)continue;
            var tops=state.getCollisionShape(level,pos).toAabbs().stream().filter(b->Math.abs(y+b.maxY-from.y())<.02).toList();
            phase=Phase.SUPPORT_UNION;lastTopCount=tops.size();if(!covers(tops,x0,x1,z0,z1))return refuse(Refusal.SUPPORT_TOP_COVERAGE);phase=Phase.SUPPORT_SCAN;
        }
        phase=Phase.FINAL;return !expired()&&current();
    }
    static boolean sameBody(AABB bound,float width,float height,Pose pose,AABB actual,float currentWidth,float currentHeight,Pose currentPose){
        return bound!=null&&actual!=null&&pose!=null&&pose==currentPose&&width==currentWidth&&height==currentHeight
            &&bound.minX==actual.minX&&bound.minY==actual.minY&&bound.minZ==actual.minZ
            &&bound.maxX==actual.maxX&&bound.maxY==actual.maxY&&bound.maxZ==actual.maxZ;
    }
    private boolean expired(){lastBudgetNanos=System.nanoTime()-started;boolean expired=lastBudgetNanos>=MAX_READ_NANOS;if(expired)refuse(Refusal.READ_BUDGET);return expired;}
    /** Exact finite rectangle-union coverage, independently testable with native boxes. */
    static boolean covers(List<AABB> tops,double x0,double x1,double z0,double z1){
        if(tops==null||tops.isEmpty()||tops.size()>128||!(x0<x1&&z0<z1))return false;
        var xs=new TreeSet<Double>();var zs=new TreeSet<Double>();xs.add(x0);xs.add(x1);zs.add(z0);zs.add(z1);
        for(var b:tops){if(b.minX>x0&&b.minX<x1)xs.add(b.minX);if(b.maxX>x0&&b.maxX<x1)xs.add(b.maxX);if(b.minZ>z0&&b.minZ<z1)zs.add(b.minZ);if(b.maxZ>z0&&b.maxZ<z1)zs.add(b.maxZ);}
        var xv=new ArrayList<>(xs);var zv=new ArrayList<>(zs);
        for(int i=1;i<xv.size();i++)for(int j=1;j<zv.size();j++){
            double x=(xv.get(i-1)+xv.get(i))/2,z=(zv.get(j-1)+zv.get(j))/2;
            if(tops.stream().noneMatch(b->x>=b.minX&&x<=b.maxX&&z>=b.minZ&&z<=b.maxZ))return false;
        }
        return true;
    }
    private static boolean finite(Vec3 p){return Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state){return !state.getFluidState().isEmpty()||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.COBWEB)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE);}
}
