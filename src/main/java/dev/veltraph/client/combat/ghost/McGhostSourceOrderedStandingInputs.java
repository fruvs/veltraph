package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceOrderedStandingProvider.*;

/** Composed read-only native inputs. Requires an established owned search/selection receipt; starts no caller. */
public final class McGhostSourceOrderedStandingInputs implements NativeInputs {
    private final Minecraft client;private final Entity target;
    private final GhostSourceOwnedParentArea.Receipt area;
    private final GhostGrindModule parent;private final Object nativeInputOwner;
    private final GhostNativeSearchPhase.Receipt search;
    private final GhostSourceIssuedSearchJob.Receipt issuedJob;
    private final ToDoubleFunction<Cell> customPenalty;
    private final McGhostSourceRaySnapshot ray;private final McGhostSourceSupportRegistrySnapshot support;
    private final long cameraAuthorityEpoch;
    private final Vec3 playerPosition,targetEye;private final float playerYaw;private final boolean targetGrounded;
    private final Cell origin;private final Point targetStanding,playerPoint;
    private McGhostSourceOrderedStandingInputs(Minecraft client,Entity target,List<Entity> actors,
            GhostSourceOwnedParentArea.Receipt area,GhostGrindModule parent,Object nativeInputOwner,
            GhostNativeSearchPhase.Receipt search,GhostSourceIssuedSearchJob.Receipt issuedJob,ToDoubleFunction<Cell> customPenalty){
        this.client=client;this.target=target;this.area=area;this.parent=parent;this.nativeInputOwner=nativeInputOwner;
        this.search=search;this.issuedJob=issuedJob;this.customPenalty=customPenalty;
        cameraAuthorityEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        playerPosition=client.player.position();playerYaw=client.player.getYRot();
        if(!Float.isFinite(playerYaw))throw new IllegalStateException("Finite actual native player yaw required");
        targetEye=target.getEyePosition();targetGrounded=target.onGround();
        var targetBlock=target.blockPosition();origin=new Cell(targetBlock.getX(),targetBlock.getY(),targetBlock.getZ());
        targetStanding=new Point(origin.x()+.5,origin.y(),origin.z()+.5);
        playerPoint=new Point(playerPosition.x,playerPosition.y,playerPosition.z);
        new GhostSourceSampledRay.Point(targetEye.x,targetEye.y,targetEye.z); // Finite before registry/world observations.
        ray=McGhostSourceRaySnapshot.observe(client,target,List.copyOf(actors));support=McGhostSourceSupportRegistrySnapshot.observe(client);
    }
    public static McGhostSourceOrderedStandingInputs observe(Minecraft client,GhostGrindModule parent,Object actualNativeInputOwner,
            Entity target,List<Entity> observedActors,ToDoubleFunction<Cell> customPenalty){
        Objects.requireNonNull(parent);
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)
            throw new IllegalStateException("Actual native client-thread parent context required");
        var actualParent=parent.sourceParentAreaStamp(actualNativeInputOwner).orElse(null);
        var area=parent.sourceParentAreaReceipt(actualNativeInputOwner).orElse(null);
        var search=parent.sourceNativeSearchReceipt(actualNativeInputOwner).orElse(null);
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null
                ||target==null||observedActors==null||area==null||actualParent==null||search==null
                ||actualParent.parent()!=parent||actualParent.player()!=client.player||actualParent.world()!=client.level
                ||actualParent.connection()!=client.getConnection()||!area.current(actualParent)
                ||parent.sourceNativeSelectedTarget(actualNativeInputOwner)!=target||!search.current(actualParent,target))
            throw new IllegalStateException("Actual owned Ghost source-search child/selection and native parent frame required");
        return new McGhostSourceOrderedStandingInputs(client,target,observedActors,area,parent,actualNativeInputOwner,search,null,customPenalty);
    }
    /** Geometry capture for a candidate of an actual issued job. Does not publish selected-target or SEARCH state. */
    public static McGhostSourceOrderedStandingInputs observeForIssuedJob(Minecraft client,GhostGrindModule parent,
            GhostSourceIssuedSearchJob.Receipt actualIssuedJob,Entity target,List<Entity> observedActors,ToDoubleFunction<Cell> customPenalty){
        Objects.requireNonNull(parent);
        if(client==null||!client.isSameThread()||actualIssuedJob==null||target==null||observedActors==null
                ||!actualIssuedJob.candidateFrameCurrent(target))throw new IllegalStateException("Current issued candidate geometry frame required");
        var actualParent=parent.sourceParentAreaStamp(parent).orElse(null);var area=parent.sourceParentAreaReceipt(parent).orElse(null);
        if(actualParent==null||area==null||!area.current(actualParent)||actualIssuedJob.scope().parent().parent()!=parent)
            throw new IllegalStateException("Current issued parent/area observation required");
        var captured=new McGhostSourceOrderedStandingInputs(client,target,observedActors,area,parent,parent,null,actualIssuedJob,customPenalty);
        if(!captured.current())throw new IllegalStateException("Issued standing frame changed during capture");return captured;
    }
    public boolean current(){var actualParent=parent.sourceParentAreaStamp(nativeInputOwner).orElse(null);
        return actualParent!=null&&actualParent.player()==client.player&&actualParent.world()==client.level
            &&actualParent.connection()==client.getConnection()&&area.current(actualParent)
            &&(issuedJob==null?parent.sourceNativeSelectedTarget(nativeInputOwner)==target&&search.current(actualParent,target):issuedJob.candidateFrameCurrent(target))
            &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent
            &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==cameraAuthorityEpoch
            &&ray.passableRegistryObserved()&&support.current()&&client.player.position().equals(playerPosition)
            &&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(playerYaw)
            &&target.getEyePosition().equals(targetEye)&&target.onGround()==targetGrounded;}
    public ParentAreaObservation parentAreaObservation(){return area.observation();}
    public Cell targetBlock(){requireCurrent();return origin;}
    public Point targetStandingPoint(){requireCurrent();return targetStanding;}
    public Point actualPlayerPosition(){requireCurrent();return playerPoint;}
    public boolean targetOnGround(){requireCurrent();return targetGrounded;}
    public boolean sourceCellStandable(Cell cell){
        requireCurrent();var pos=pos(cell);var below=pos.below();
        var body=ray.block(rayCell(cell));var above=ray.block(new GhostSourceSampledRay.Cell(cell.x(),cell.y()+1,cell.z()));
        if(!body.available()||!above.available()||!client.level.isLoaded(below))return false;
        var state=client.level.getBlockState(below);var block=state.getBlock();
        boolean allowed=GhostSourceStandability.allowed(body.sourcePassable()&&above.sourcePassable(),client.level.isLoaded(pos),
                support.allows(block),block instanceof FenceBlock,block instanceof FenceGateBlock&&!state.getValue(FenceGateBlock.OPEN),
                block instanceof WallBlock,client.level.getBlockState(pos).getFluidState().getType()!=Fluids.EMPTY,
                state.getFluidState().getType()!=Fluids.EMPTY);
        return current()&&allowed;
    }
    public boolean sourceProtectedTargetRay(Cell cell){
        requireCurrent();var eye=McGhostSourceCandidateEye.observe(client,pos(cell));if(eye==null)return false;
        var aim=new GhostSourceSampledRay.Point(targetEye.x,targetEye.y,targetEye.z);
        boolean hit=GhostSourceSampledRay.hits(eye,aim,ray.targetBox(),GhostSourceFloatDirection.between(eye,aim,playerYaw),
                GhostSourceSampledRay.Mode.standing(),ray);
        return current()&&hit;
    }
    public double parentFieldValue(Cell cell){requireCurrent();return area.fieldValue(cell,parent.sourceParentAreaStamp(nativeInputOwner).orElse(null));}
    public boolean lavaAtOrBelow(Cell cell){
        requireCurrent();var pos=pos(cell);
        if(!client.level.isLoaded(pos)||!client.level.isLoaded(pos.below()))throw new IllegalStateException("Observed lava cells required");
        return client.level.getBlockState(pos).is(Blocks.LAVA)||client.level.getBlockState(pos.below()).is(Blocks.LAVA);
    }
    public double customPenalty(Cell cell){requireCurrent();double value=customPenalty==null?0:customPenalty.applyAsDouble(cell);
        return current()?value:Double.NaN;}
    private void requireCurrent(){if(!current())throw new IllegalStateException("Owned standing frame changed");}
    private static BlockPos pos(Cell c){return new BlockPos(c.x(),c.y(),c.z());}
    private static GhostSourceSampledRay.Cell rayCell(Cell c){return new GhostSourceSampledRay.Cell(c.x(),c.y(),c.z());}
}
