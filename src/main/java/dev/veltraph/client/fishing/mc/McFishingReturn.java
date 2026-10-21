package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.fishing.FishingReturnController;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McInputService;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Local geometry detection and vanilla movement to the session's saved cast position. */
final class McFishingReturn implements FishingReturnController.Actions {
    private final Minecraft client;
    private final RotationService rotation;
    private final McNavigator geometry;private final McFishingTravel navigator;
    private final FishingReturnController controller=new FishingReturnController();
    private Vec3 anchor;private Object sessionPlayer,sessionWorld,sessionConnection;private int savedRodSlot=-1;private net.minecraft.world.item.ItemStack savedRod=net.minecraft.world.item.ItemStack.EMPTY;private dev.veltraph.client.world.Vec3 normalReturnGoal;
    private BlockPos block;
    private float yaw,pitch;
    private boolean navigationOwned,rotationOwned;
    McFishingReturn(Minecraft client,ConfigService config,RotationService rotation,dev.veltraph.client.service.FarmServices services) {
        this.client=client;this.rotation=rotation;
        geometry=new McNavigator(client,new McInputService(client),config);navigator=new McFishingTravel(client,config,services,this);
        if(client.player!=null) {
            anchor=client.player.position();sessionPlayer=client.player;sessionWorld=client.level;sessionConnection=client.getConnection();savedRodSlot=client.player.getInventory().getSelectedSlot();savedRod=stableRod(client.player.getMainHandItem());var p=client.player.blockPosition();
            block=new BlockPos(p.getX(),p.getY(),p.getZ());yaw=client.player.getYRot();pitch=client.player.getXRot();
        }
    }
    boolean tick(boolean allowStart) {
        boolean blocked=allowStart&&anchor!=null&&client.player.position().distanceTo(anchor)>1.0
                &&client.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof ArmorStand;
        return controller.tick(blocked,System.currentTimeMillis(),this);
    }
    boolean active() { return controller.active(); }
    String failure() { return controller.failure(); }
    String status() { return controller.state().name(); }
    void stop() { controller.stop(this); }
    @Override public boolean startWalking() {
        navigationOwned=true;
        normalReturnGoal=block==null?null:geometry.position(block);return normalReturnGoal!=null&&navigator.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.OBSTRUCTION_RETURN);
    }
    @Override public int walk() {
        var state=navigator.tick();
        return state==WalkController.State.BLOCKED?-1:state==WalkController.State.ARRIVED?1:0;
    }
    @Override public boolean restoreAim() {
        rotationOwned=true;rotation.track(yaw,pitch);
        return Math.abs(WalkController.wrap(yaw-client.player.getYRot()))<1
                &&Math.abs(pitch-client.player.getXRot())<1;
    }
    @Override public void release() {
        if(navigationOwned) { navigator.stop();navigationOwned=false; }
        if(rotationOwned) { rotation.cancelRotation();rotationOwned=false; }
    }
    boolean rebindIdle(){return !active()&&!navigationOwned&&!rotationOwned&&failure().isEmpty();}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active()&&navigator.consumeExpectedTeleport(position,now);}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return navigator.captureNativeCleanupSource(exact);}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return navigator.captureNativeReturnSource(exact);}
    private static net.minecraft.world.item.ItemStack stableRod(net.minecraft.world.item.ItemStack value){var copy=value.copy();copy.remove(net.minecraft.core.component.DataComponents.DAMAGE);return copy;}
    Object nativeReturnProfileIdentity(){return controller;}
    Object nativeReturnRuntimeIdentity(){return controller;}
    int nativeReturnRodSlot(){return savedRodSlot;}
    Object nativeReturnCustodyIdentity(){return savedRod;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||savedRodSlot<0||savedRodSlot>8)return false;
        boolean semantic=net.minecraft.world.item.ItemStack.matches(savedRod,stableRod(client.player.getInventory().getItem(savedRodSlot)));
        return semantic&&nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        return exact==navigator&&intent==dev.veltraph.client.fishing.FishingTravelIntent.OBSTRUCTION_RETURN&&goal==normalReturnGoal&&normalReturnGoal!=null
            &&navigationOwned&&(controller.state()==FishingReturnController.State.WALKING||starting&&controller.state()==FishingReturnController.State.IDLE)
            &&controller.failure().isEmpty()&&client.player==sessionPlayer&&client.level==sessionWorld&&client.getConnection()==sessionConnection
            &&savedRodSlot>=0&&savedRodSlot<9&&savedRod.is(net.minecraft.world.item.Items.FISHING_ROD);
    }
}
