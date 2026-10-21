package dev.veltraph.client.farming.movement;

import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.farming.module.FarmMaintenanceState;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Stationary owned completion of native-reachable mature crops beside/behind a lane. */
public final class McReachableCropCompletion implements CropCompletionPort {
 private final Minecraft client;private final FarmModule owner;private final FarmServices services;private final BooleanSupplier toolReady,eligible;
 private dev.veltraph.client.world.BlockPos target;private boolean protectedForwardTarget;private final CropCompletionRetry retry=new CropCompletionRetry();private final CropCompletionBatch batch=new CropCompletionBatch();private Object player,world,connection;private long epoch,started,restoringStarted;private boolean restoring;private float resumeYaw,resumePitch;private ItemStack originalTool;private int toolSlot=-1;private dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding guard;
 public McReachableCropCompletion(Minecraft client,FarmModule owner,FarmServices services,BooleanSupplier toolReady,BooleanSupplier eligible){this.client=client;this.owner=owner;this.services=services;this.toolReady=toolReady;this.eligible=eligible;}
 private ReachableMatureCropSelector.Scope scope(){var p=client.player;return new ReachableMatureCropSelector.Scope(owner,p,client.level,client.getConnection(),McOwnedRotation.epoch(client),p==null?-1:p.tickCount);}
 private boolean ownsInputs(){return client.isSameThread()&&owner.isEnabled()&&VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==owner&&McOwnedRotation.authority(client)==owner;}
 private CropCompletionPermission permission(){var p=client.player;var w=client.level;boolean live=p!=null&&w!=null&&client.getConnection()!=null&&p.isAlive()&&p.level()==w;var binding=services.breaking().targetGuardBinding(owner);
  return new CropCompletionPermission(client.isSameThread(),live,!client.isPaused(),live&&!w.tickRateManager().isEntityFrozen(p),live&&client.getCameraEntity()==p,owner.isEnabled()&&eligible.getAsBoolean(),!owner.isSuspended(),live&&p.onGround(),client.gui.screen()==null,VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==owner,McOwnedRotation.authority(client)==owner,live&&toolReady.getAsBoolean(),binding.isPresent()&&services.breaking().targetGuardBoundToCurrentContext(owner,binding.get()));}
 private boolean custody(){return permission().allows();}

 private boolean same(){return custody()&&player==client.player&&world==client.level&&connection==client.getConnection()&&epoch==McOwnedRotation.epoch(client)&&toolSlot==client.player.getInventory().getSelectedSlot()&&originalTool!=null&&ItemStack.isSameItemSameComponents(originalTool,client.player.getMainHandItem())&&guard!=null&&services.breaking().targetGuardBoundToCurrentContext(owner,guard);}
 private ReachableMatureCropSelector.Crop observe(BlockPos cell){
  return observe(cell,false);
 }
 private ReachableMatureCropSelector.Crop observe(BlockPos cell,boolean allowProtectedForward){
  if(client.player==null||client.level==null)return null;
  if(!client.level.isLoaded(cell)||!client.level.isLoaded(cell.below()))return null;var state=client.level.getBlockState(cell);
  if(!state.is(Blocks.WHEAT)||!(state.getBlock() instanceof CropBlock crop)||!crop.isMaxAge(state)||!client.level.getBlockState(cell.below()).is(Blocks.FARMLAND))return null;
  var eye=client.player.getEyePosition();var point=new net.minecraft.world.phys.Vec3(cell.getX()+.5,cell.getY()+.65,cell.getZ()+.5);
  double range=client.player.blockInteractionRange();boolean visible=Double.isFinite(range)&&range>0&&eye.distanceTo(point)<=range;
  if(visible){var hit=client.level.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));visible=hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(cell);}
  if(allowProtectedForward){
   var primaryPoint=new net.minecraft.world.phys.Vec3(cell.getX()+.5,cell.getY()+.45,cell.getZ()+.5);
   var blocked=client.level.clip(new ClipContext(eye,primaryPoint,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
   if(blocked.getType()!=HitResult.Type.BLOCK||blocked.getBlockPos().equals(cell))return null;
   var near=blocked.getBlockPos();if(blocked.getLocation().distanceTo(eye)>=primaryPoint.distanceTo(eye))return null;
   if(!client.level.isLoaded(near)||!client.level.isLoaded(near.below())||near.getY()!=cell.getY())return null;
   var nearState=client.level.getBlockState(near);
   if(!nearState.is(Blocks.WHEAT)||!(nearState.getBlock() instanceof CropBlock nearCrop)||nearCrop.isMaxAge(nearState)||!client.level.getBlockState(near.below()).is(Blocks.FARMLAND))return null;
   visible=false;
   var shape=state.getShape(client.level,cell,net.minecraft.world.phys.shapes.CollisionContext.of(client.player));
   var boxes=new ArrayList<ProtectedCropFaceSelector.Box>();
   for(var box:shape.toAabbs())boxes.add(new ProtectedCropFaceSelector.Box(cell.getX()+box.minX,cell.getY()+box.minY,cell.getZ()+box.minZ,cell.getX()+box.maxX,cell.getY()+box.maxY,cell.getZ()+box.maxZ));
   var candidate=ProtectedCropFaceSelector.select(new Vec3(eye.x,eye.y,eye.z),range,true,true,boxes,probe->{
       var end=new net.minecraft.world.phys.Vec3(probe.x(),probe.y(),probe.z());
       var hit=client.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
       return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(cell);
   });
   if(candidate.isPresent()){point=new net.minecraft.world.phys.Vec3(candidate.get().x(),candidate.get().y(),candidate.get().z());visible=true;}

  }
  return new ReachableMatureCropSelector.Crop(new dev.veltraph.client.world.BlockPos(cell.getX(),cell.getY(),cell.getZ()),new Vec3(point.x,point.y,point.z),true,true,true,visible,allowProtectedForward&&visible);
 }
 private Optional<ReachableMatureCropSelector.Selection> nextMissed(float rowYaw){
  var eye=client.player.getEyePosition();var feet=client.player.blockPosition();var candidates=new ArrayList<ReachableMatureCropSelector.Crop>();
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=2;y++){
   var cell=feet.offset(x,y,z);var point=new Vec3(cell.getX()+.5,cell.getY()+.65,cell.getZ()+.5);
   boolean missed=ReachableMatureCropSelector.missedSector(new Vec3(eye.x,eye.y,eye.z),point,rowYaw);
   var c=observe(cell,!missed);
   if(c!=null&&c.unobstructed()&&(!batch.active()||!batch.visited().contains(c.cell()))){
    if(missed)candidates.add(c);
    else if(c.protectedForwardException())candidates.add(c); // Only a proved protected-occluder visible surface.
   }
  }
  return ReachableMatureCropSelector.selectMissed(scope(),new Vec3(eye.x,eye.y,eye.z),client.player.blockInteractionRange(),candidates,retry.avoided(services.clock().nowMillis()),rowYaw);
 }
 /** IDLE leaves primary row harvesting untouched; OWNED/DENIED consume the caller tick. */
 /** Fixed S-shape view does not offer behind-crop completion. The primary engine alone writes attack. */
 @Override public Result tickFixedRowView(boolean admitted,float rowYaw,float rowPitch){
  if(!client.isSameThread()||client.player==null||client.level==null)return Result.DENIED;
  var original=scope();var registry=VeltraphBootstrap.moduleManager();var lifecycle=registry.lifecycleSnapshot();
  int selectedSlot=client.player.getInventory().getSelectedSlot();var heldTool=client.player.getMainHandItem().copy();
  var originalGuard=services.breaking().targetGuardBinding(owner).orElse(null);
  if(active()||target!=null||restoring)cancel();
  return fixedViewCurrent(original,registry,lifecycle,selectedSlot,heldTool,originalGuard)?Result.IDLE:Result.DENIED;
 }
 private boolean fixedViewCurrent(ReachableMatureCropSelector.Scope original,dev.veltraph.client.core.ModuleManager registry,
     dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,int slot,ItemStack tool,
     dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding originalGuard){
  if(!custody())return false;
  return registry==VeltraphBootstrap.moduleManager()&&registry.lifecycleCurrent(lifecycle)
    &&original.owner()==owner&&original.player()==client.player&&original.world()==client.level&&original.connection()==client.getConnection()
    &&original.epoch()==McOwnedRotation.epoch(client)&&client.player!=null&&original.tick()==client.player.tickCount
    &&slot==client.player.getInventory().getSelectedSlot()&&ItemStack.isSameItemSameComponents(tool,client.player.getMainHandItem())
    &&originalGuard!=null&&services.breaking().targetGuardBoundToCurrentContext(owner,originalGuard)
    &&registry.lifecycleCurrent(lifecycle)&&original.player()==client.player&&original.world()==client.level&&original.connection()==client.getConnection()
    &&original.epoch()==McOwnedRotation.epoch(client);
 }
 public Result tick(boolean admitted,float rowYaw,float rowPitch){
  if(!client.isSameThread())return Result.DENIED;
  if(!custody()){cancel();return Result.DENIED;}
  long now=services.clock().nowMillis();var feet=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
  if(!batch.active()){
   if(!admitted||!batch.canStart(feet,now,new CropCompletionBatch.RowScope(owner,client.player,client.level,client.getConnection(),McOwnedRotation.epoch(client))))return Result.IDLE;
   var selected=nextMissed(rowYaw);if(!custody()){cancel();return Result.DENIED;}if(selected.isEmpty())return Result.IDLE;
   batch.start(now);target=selected.get().cell();protectedForwardTarget=!ReachableMatureCropSelector.missedSector(new Vec3(client.player.getEyePosition().x,client.player.getEyePosition().y,client.player.getEyePosition().z),selected.get().point(),rowYaw);started=now;player=client.player;world=client.level;connection=client.getConnection();epoch=McOwnedRotation.epoch(client);resumeYaw=rowYaw;resumePitch=rowPitch;toolSlot=client.player.getInventory().getSelectedSlot();originalTool=client.player.getMainHandItem().copy();guard=services.breaking().targetGuardBinding(owner).orElseThrow();
  }
  if(!same()){cancel();return Result.DENIED;}
  if(now<started||restoring&&(now<restoringStarted||now-restoringStarted>3000)){cancel();return Result.DENIED;}
  if(!restoring&&batch.expired(now)){batch.restore();restoring=true;restoringStarted=now;}
  services.input().releaseAllMovement();services.breaking().setBreaking(false);
  if(restoring){if(!CropCompletionCommit.rotateAfterObservation(()->true,this::same,()->services.rotation().track(resumeYaw,resumePitch))){cancel();return Result.DENIED;}if(Math.abs(Angles.signedDelta(services.rotation().yaw(),resumeYaw))<1&&Math.abs(services.rotation().pitch()-resumePitch)<1){batch.finish(feet,now);restoring=false;target=null;return Result.OWNED;}return Result.OWNED;}
  var cell=new BlockPos(target.x(),target.y(),target.z());var fresh=observe(cell,protectedForwardTarget);
  if(fresh==null||now-started>3000){batch.visited(target);if(fresh!=null)retry.exclude(target,now);var next=nextMissed(resumeYaw);if(!same()){cancel();return Result.DENIED;}if(next.isPresent()){target=next.get().cell();protectedForwardTarget=!ReachableMatureCropSelector.missedSector(new Vec3(client.player.getEyePosition().x,client.player.getEyePosition().y,client.player.getEyePosition().z),next.get().point(),resumeYaw);started=now;return Result.OWNED;}batch.restore();restoring=true;restoringStarted=now;return Result.OWNED;}
  var eye=client.player.getEyePosition();var selected=new ReachableMatureCropSelector.Selection(scope(),target,fresh.point(),new Vec3(eye.x,eye.y,eye.z).distanceTo(fresh.point()));
  if(!ReachableMatureCropSelector.current(selected,scope(),new Vec3(eye.x,eye.y,eye.z),client.player.blockInteractionRange(),fresh)){batch.visited(target);target=null;batch.restore();restoring=true;restoringStarted=now;return Result.OWNED;}
  double dx=fresh.point().x()-eye.x,dy=fresh.point().y()-eye.y,dz=fresh.point().z()-eye.z;float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
  if(!CropCompletionCommit.rotateAfterObservation(()->fresh.unobstructed()&&fresh.mature()&&fresh.legalSoil(),this::same,()->services.rotation().track(yaw,pitch))){cancel();return Result.DENIED;}
  var hit=services.breaking().freshTargetBlockPos();var finalCrop=observe(cell,protectedForwardTarget);
  if(client.player==null||client.level==null){cancel();return Result.DENIED;}
  var finalEye=client.player.getEyePosition();double finalRange=client.player.blockInteractionRange();boolean geometry=finalCrop!=null&&finalCrop.unobstructed()&&finalCrop.mature()&&finalCrop.legalSoil()&&Double.isFinite(finalRange)&&finalRange>0&&new Vec3(finalEye.x,finalEye.y,finalEye.z).distanceTo(finalCrop.point())<=finalRange;
  var late=permission();boolean scopeCurrent=same();if(CropCompletionCommit.mayAttack(hit.isPresent()&&hit.get().equals(target),geometry,late,scopeCurrent))services.breaking().setBreaking(true);
  return Result.OWNED;
 }
 public void observePrimaryRowTick(float rowYaw,TurnDirection side,boolean farmingSideways){
  if(!client.isSameThread())return;
  boolean valid=custody()&&!active()&&farmingSideways;
  var p=client.player;
  if(!valid||p==null){batch.observePrimary(null);return;}
  boolean left=client.options.keyLeft.isDown(),right=client.options.keyRight.isDown();
  if(client.options.keyUp.isDown()||client.options.keyDown.isDown()||client.options.keyJump.isDown()||client.options.keyShift.isDown()||client.options.keySprint.isDown()||left==right||(side==TurnDirection.LEFT)!=left){batch.observePrimary(null);return;}
  double yaw=Math.toRadians(rowYaw),sign=left?1:-1;
  if(Math.abs(Angles.signedDelta(p.getYRot(),rowYaw))>1){batch.observePrimary(null);return;}
  batch.observePrimary(new CropCompletionBatch.RowEvidence(new CropCompletionBatch.RowScope(owner,p,client.level,client.getConnection(),McOwnedRotation.epoch(client)),p.tickCount,new Vec3(p.getX(),p.getY(),p.getZ()),sign*Math.cos(yaw),sign*Math.sin(yaw),true));
 }
 public void cancel(){if((target!=null||restoring)&&ownsInputs()){services.breaking().setBreaking(false);services.input().releaseAllMovement();}target=null;protectedForwardTarget=false;restoring=false;retry.clear();batch.cancel();player=null;world=null;connection=null;originalTool=null;toolSlot=-1;guard=null;}
 public boolean active(){return batch.active();}
 public FarmMaintenanceState maintenanceState(){return FarmMaintenanceState.of(this,"target",target,"protectedForwardTarget",protectedForwardTarget,"restoring",restoring,"retry",retry.maintenanceState(),"batch",batch.maintenanceState(),"player",player,"world",world,"connection",connection,"epoch",epoch,"started",started,"restoringStarted",restoringStarted,"resumeYaw",resumeYaw,"resumePitch",resumePitch,"originalTool",originalTool,"toolSlot",toolSlot,"guard",guard);}

}
