package dev.veltraph.client.builder;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Actual survival input adapter for the recovered preparation branch, with no commands/purchases. */
final class BuildSetupExecutor {
    private final Minecraft client;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final FarmServices services;
    private final McTravelNavigator navigation;
    private final BuildSetupRuntime runtime=new BuildSetupRuntime();
    private BuildSetupPlan plan;
    private boolean walking;
    private Object travelPlayer,travelWorld,travelConnection;
    private String failure="";
    BuildSetupExecutor(Minecraft client,FarmServices services,ConfigService config){this(client,services,config,()->null);}
    BuildSetupExecutor(Minecraft client,FarmServices services,ConfigService config,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,parent);this.client=client;this.services=services;navigation=new McTravelNavigator(client,services,config);}
    void load(BuildSetupPlan plan){this.plan=plan;failure="";runtime.reset();}
    void resume(){travelPlayer=client.player;travelWorld=client.level;travelConnection=client.getConnection();runtime.resume();failure="";walking=false;services.breaking().installTargetGuard(this,this::allowedDestroyTarget);}
    private boolean allowedDestroyTarget(dev.veltraph.client.world.BlockPos target){
        if(plan==null||runtime.index()>=plan.steps().size()||client.level==null)return false;
        var step=plan.steps().get(runtime.index());
        if(step.operation()!=BuildSetupPlan.Operation.CLEAR||!target.equals(step.pos()))return false;
        var p=new net.minecraft.core.BlockPos(target.x(),target.y(),target.z());var state=client.level.getBlockState(p);
        return !state.hasBlockEntity()&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(step.expectedBlock());
    }
    void stop(){services.breaking().clearTargetGuard(this);interactionRotation.release(navigation);services.input().releaseAllMovement();services.breaking().setBreaking(false);walking=false;}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return walking&&client.isSameThread()&&travelPlayer!=null&&travelConnection!=null
                &&client.player==travelPlayer&&client.level==travelWorld&&client.getConnection()==travelConnection
                &&navigation.consumeExpectedTeleport(position,now);
    }
    String preview(){return plan.name()+": "+plan.steps().size()+" ordered operations. Material budget: "+plan.requiredMaterials()+". Checkpoint "+runtime.index()+". Fixed anchor.";}
    String status(){return (failure.isEmpty()?runtime.status():failure)+" | "+preview();}
    private BuildSetupRuntime.Action fail(String reason){failure=reason;stop();return BuildSetupRuntime.Action.STOP;}
    private Vec3 visibleDestroyFace(net.minecraft.core.BlockPos pos){
        // A center ray can intersect a neighboring cell at a shared corner. Select an exposed
        // face interior before considering movement, just as the placement adapter does.
        for(var face:Direction.values()){
            var point=Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);
            var probe=point.add(-face.getStepX()*.001,-face.getStepY()*.001,-face.getStepZ()*.001);
            var hit=client.level.clip(new ClipContext(client.player.getEyePosition(),probe,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&hit.getDirection()==face)return probe;
        }
        return Vec3.atCenterOf(pos);
    }
    BuildSetupRuntime.Action tick(){
        services.breaking().setBreaking(false);
        if(client.player==null||client.level==null||client.gui.screen()!=null)return fail("Setup unavailable: missing world/player or open screen; checkpoint retained");
        if(walking){var state=navigation.tick();if(state==McTravelNavigator.State.FAILED)return fail("Setup approach failed: "+navigation.status());if(state==McTravelNavigator.State.ACTIVE)return BuildSetupRuntime.Action.NONE;walking=false;}
        if(runtime.index()>=plan.steps().size())return BuildSetupRuntime.Action.COMPLETE;
        var step=plan.steps().get(runtime.index());var p=step.pos();var pos=new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());
        var state=client.level.getBlockState(pos);boolean water=step.operation()==BuildSetupPlan.Operation.WATER;
        int slot=-1;
        for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);if(water?stack.is(Items.WATER_BUCKET):!stack.isEmpty()&&stack.isCorrectToolForDrops(state)){slot=i;break;}}
        if(!water&&slot<0&&!state.requiresCorrectToolForDrops())slot=services.inventory().selectedSlot();
        // Bucket use raycasts independently. Aim at the top face of a solid floor below its pocket.
        var support=water?pos.below():pos;
        var aim=water?Vec3.atCenterOf(support).add(0,.5,0):visibleDestroyFace(pos);
        var probe=water?aim.add(0,-.001,0):aim;
        var trace=client.level.clip(new ClipContext(client.player.getEyePosition(),probe,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
        boolean reachable=client.player.getEyePosition().distanceTo(aim)<=4.5&&trace.getType()==HitResult.Type.BLOCK&&trace.getBlockPos().equals(support)&&(!water||trace.getDirection()==Direction.UP);
        if(water)reachable=reachable&&!client.level.getBlockState(support).hasBlockEntity();
        boolean aligned=client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(support)&&(!water||h.getDirection()==Direction.UP);
        var action=runtime.tick(plan,new BuildSetupRuntime.Facts(client.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4),BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),state.getFluidState().isSource()&&state.is(net.minecraft.world.level.block.Blocks.WATER),state.hasBlockEntity(),reachable,slot>=0,slot==services.inventory().selectedSlot(),aligned,System.currentTimeMillis()));
        if(action==BuildSetupRuntime.Action.MOVE){
            boolean found=false;
            for(int[] d:new int[][]{{0,2},{0,-2},{2,0},{-2,0}})for(int dy:new int[]{0,1,-1})if(!found&&navigation.startApproach(p.add(d[0],dy,d[1]),dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL))found=true;
            if(!found)return fail("No permitted setup approach: "+navigation.status());walking=true;return action;
        }
        if(action==BuildSetupRuntime.Action.STOP||action==BuildSetupRuntime.Action.COMPLETE){stop();return action;}
        navigation.stop();walking=false;
        if(action==BuildSetupRuntime.Action.SELECT){services.inventory().selectSlot(slot);return action;}
        // NONE includes unconfirmed ticks: rotation continues until the observed raycast is aligned.
        if(action==BuildSetupRuntime.Action.NONE&&runtime.index()>=plan.steps().size())return action;
        var delta=aim.subtract(client.player.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(action==BuildSetupRuntime.Action.BREAK)services.breaking().setBreaking(true);
        if(action==BuildSetupRuntime.Action.WATER&&client.gameMode!=null){services.input().releaseAllMovement();client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);}
        return action;
    }
}
