package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.FishingTotemController;
import dev.veltraph.client.service.RotationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Source-derived dry-ground totem placement. All observations/actions stay in the game client. */
final class McFishingTotem {
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Runnable useRod;
    private final FishingTotemController controller=new FishingTotemController(100,4000);
    private final Map<BlockPos,Long> recent=new HashMap<>();
    private final Set<UUID> existing=new HashSet<>();
    private int mana=-1,rodSlot=-1,itemSlot=-1;private long manaAt,lastPlacement,pendingAt,reelAt;
    private float yaw,pitch;private Vec3 anchor;private BlockHitResult placement;
    private ItemStack item=ItemStack.EMPTY,rod=ItemStack.EMPTY;private boolean rotationOwned,slotOwned,prepared,restored,pendingRestore,pendingReel;
    McFishingTotem(Minecraft client,ConfigService config,RotationService rotation,Runnable useRod){
        this.client=client;this.config=config;this.rotation=rotation;this.useRod=useRod;
        if(client.player!=null){anchor=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();}
    }
    void actionBar(String text){
        var parsed=dev.veltraph.client.fishing.FishingManaParser.parse(text);
        if(parsed.isPresent()){mana=parsed.getAsInt();manaAt=System.currentTimeMillis();}
    }
    boolean active(){return controller.active()||pendingRestore||pendingReel;}
    String status(){return pendingReel?"WAIT_REEL_AFTER_CANCEL":pendingRestore?"RESTORING_AFTER_CANCEL":controller.state().name();}
    String failure(){return controller.failure();}
    private boolean enabled(){return config.get(FishingConfig.FISHING_AUTO_TOTEM);}
    boolean tick(boolean allowStart){
        long now=System.currentTimeMillis();
        if(client.player==null||client.level==null||client.gameMode==null)return false;
        if(pendingReel){
            if(client.player.fishing!=null){
                if(now-reelAt>4000){controller.fail("Server did not acknowledge interrupted totem reel");pendingReel=false;release();}
                return true;
            }
            pendingReel=false;
            return true;
        }
        if(pendingRestore){
            if(now-pendingAt>4000){controller.fail("Cannot restore fishing aim after cancel");pendingRestore=false;release();return true;}
            rotation.track(yaw,pitch);rotationOwned=true;
            if(Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1){
                pendingRestore=false;release();
            }
            return true;
        }
        if(!enabled()){
            if(active()){controller.fail("Totem disabled during placement");release();return true;}
            return false;
        }
        boolean was=active();
        boolean start=!was&&allowStart&&anchor!=null&&mana>=210&&now-manaAt<2000
                &&now-lastPlacement>=Math.max(1000,config.get(FishingConfig.FISHING_TOTEM_REFRESH_MS))&&findItem()>=0;
        if(start){lastPlacement=now;rodSlot=client.player.getInventory().getSelectedSlot();prepared=restored=false;existing.clear();
            for(var e:client.level.entitiesForRendering())if(e instanceof ArmorStand)existing.add(e.getUUID());}
        if(was&&slotOwned&&(client.player.getInventory().getSelectedSlot()!=itemSlot||!ItemStack.isSameItemSameComponents(item,client.player.getMainHandItem()))) {
            controller.fail("Totem item or selected slot changed during placement");release();return true;
        }
        boolean aligned=prepared&&validRay();
        if(prepared&&(controller.state()==FishingTotemController.State.AIM||controller.state()==FishingTotemController.State.BUFFER)) {
            Vec3 d=placement.getLocation().subtract(client.player.getEyePosition());
            float y=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),p=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
            rotation.track(y,p);rotationOwned=true;
            aligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(y-client.player.getYRot()),p-client.player.getXRot())<1&&validRay();
        }
        boolean ack=prepared&&acknowledged();
        var action=controller.tick(new FishingTotemController.Input(start,client.player.fishing!=null,prepared,aligned,ack,restored,now));
        switch(action){
            case REEL -> {reelAt=now;useRod.run();}
            case PREPARE -> prepare();
            case USE -> {
                if(!validRay()){controller.fail("Totem placement surface changed");break;}
                client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,placement);
                client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
            }
            case RESTORE -> restore();
            default -> {}
        }
        if(controller.state()==FishingTotemController.State.COMPLETE){recent.put(placement.getBlockPos().above(),now);release();}
        if(controller.state()==FishingTotemController.State.FAILED)release();
        return was||start||active();
    }
    private int findItem(){
        String name=config.get(FishingConfig.FISHING_TOTEM_ITEM_NAME).strip();if(name.isEmpty())return -1;
        for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).getHoverName().getString().replaceAll("§.","").equals(name))return i;
        return -1;
    }
    private void prepare(){
        itemSlot=findItem();placement=findPlacement();
        if(itemSlot<0||placement==null){controller.fail("Cannot find a clear reachable totem placement");return;}
        recent.put(placement.getBlockPos().above(),System.currentTimeMillis());
        rod=client.player.getMainHandItem().copy();item=client.player.getInventory().getItem(itemSlot).copy();
        client.player.getInventory().setSelectedSlot(itemSlot);slotOwned=true;prepared=true;
    }
    private BlockHitResult findPlacement(){
        var eyes=client.player.getEyePosition();var origin=BlockPos.containing(eyes);double best=Double.MAX_VALUE;BlockHitResult result=null;
        recent.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue()>Math.max(1000,config.get(FishingConfig.FISHING_TOTEM_REFRESH_MS)));
        for(int dx=-3;dx<4;dx++)for(int dy=-2;dy<1;dy++)for(int dz=-3;dz<4;dz++){
            var pos=origin.offset(dx,dy,dz);var support=pos.below();var state=client.level.getBlockState(support);
            if(!client.level.getBlockState(pos).isAir()||!client.level.getFluidState(pos).isEmpty()||!client.level.getFluidState(support).isEmpty()||state.getCollisionShape(client.level,support).isEmpty())continue;
            var shape=state.getCollisionShape(client.level,support);double top=shape.max(Direction.Axis.Y);
            var point=new Vec3(pos.getX()+.5,support.getY()+top-.001,pos.getZ()+.5);
            double distance=point.distanceTo(eyes),horizontal=Math.hypot(point.x-eyes.x,point.z-eyes.z);
            if(distance>4||horizontal<1.5||Math.hypot(point.x-anchor.x,point.z-anchor.z)<1.5)continue;
            if(recent.keySet().stream().anyMatch(p->Math.hypot(p.getX()+.5-point.x,p.getZ()+.5-point.z)<1.5))continue;
            if(!client.level.getEntitiesOfClass(LivingEntity.class,client.player.getBoundingBox().inflate(5),e->Math.hypot(e.getX()-point.x,e.getZ()-point.z)<1.5).isEmpty())continue;
            var delta=point.subtract(eyes);float targetYaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90);
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(targetYaw-yaw))<60)continue;
            var hit=client.level.clip(new ClipContext(eyes,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
            if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(support)||hit.getDirection()!=Direction.UP)continue;
            float targetPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            double score=Math.abs(3-horizontal)+Math.hypot(net.minecraft.util.Mth.wrapDegrees(targetYaw-client.player.getYRot()),targetPitch-client.player.getXRot())/80;
            if(Math.hypot(point.x-anchor.x,point.z-anchor.z)<2.5)score+=2;
            if(score<best){best=score;result=hit;}
        }
        return result;
    }
    private boolean validRay(){
        if(placement==null)return false;var eyes=client.player.getEyePosition();var end=eyes.add(client.player.getLookAngle().scale(4.5));
        var hit=client.level.clip(new ClipContext(eyes,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(placement.getBlockPos())&&hit.getDirection()==Direction.UP
                &&client.level.getBlockState(placement.getBlockPos().above()).isAir();
    }
    private boolean acknowledged(){
        String name=config.get(FishingConfig.FISHING_TOTEM_MARKER_NAME).strip();if(name.isEmpty())return false;
        for(var e:client.level.entitiesForRendering())if(e instanceof ArmorStand&&e.isAlive()&&!existing.contains(e.getUUID())&&e.getName().getString().replaceAll("§.","").equals(name)&&e.position().distanceTo(placement.getLocation())<1.5)return true;
        return false;
    }
    private void restore(){
        if(slotOwned){if(client.player.getInventory().getSelectedSlot()!=itemSlot||!ItemStack.isSameItemSameComponents(rod,client.player.getInventory().getItem(rodSlot))){controller.fail("Fishing rod or selected slot changed during totem placement");return;}
            client.player.getInventory().setSelectedSlot(rodSlot);slotOwned=false;}
        rotation.track(yaw,pitch);rotationOwned=true;
        restored=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1;
    }
    private void release(){
        if(slotOwned&&client.player!=null&&client.player.getInventory().getSelectedSlot()==itemSlot&&ItemStack.isSameItemSameComponents(item,client.player.getMainHandItem())&&ItemStack.isSameItemSameComponents(rod,client.player.getInventory().getItem(rodSlot)))client.player.getInventory().setSelectedSlot(rodSlot);
        slotOwned=false;if(rotationOwned)rotation.cancelRotation();rotationOwned=false;
    }
    void stop(){pendingReel|=controller.state()==FishingTotemController.State.WAIT_REEL;if(rotationOwned&&!pendingRestore)pendingAt=System.currentTimeMillis();pendingRestore|=rotationOwned;release();controller.reset();prepared=restored=false;}
    boolean rebindIdle(){if(active()||slotOwned||rotationOwned||!failure().isEmpty())return false;existing.clear();placement=null;item=rod=ItemStack.EMPTY;mana=-1;manaAt=0;prepared=restored=false;return true;}
}
