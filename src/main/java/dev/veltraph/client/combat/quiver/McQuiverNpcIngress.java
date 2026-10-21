package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

/** Shared ground ingress preserves the refill tool lease before one exact native NPC interaction. */
final class McQuiverNpcIngress {
    private final Minecraft client;private final FarmServices services;private final McNavigator navigator;private final McSharedMovementNavigator travel;
    private final QuiverNpcIngressRuntime runtime;private final Entity target;private boolean walking;private int paths;
    static QuiverNpcIngressRuntime.Contract contract(ConfigService config,String title,int timeout){
        String type=config.get(QuiverIngressConfig.QUIVER_NPC_TYPE);var id=Identifier.tryParse(type);
        if(id==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IllegalArgumentException("Configure exact quiver NPC entity registry ID");
        return new QuiverNpcIngressRuntime.Contract(config.get(QuiverIngressConfig.QUIVER_NPC_NAME),type,config.get(QuiverIngressConfig.QUIVER_NPC_MAX_DISTANCE),timeout,title);
    }
    McQuiverNpcIngress(Minecraft client,FarmServices services,ConfigService config,QuiverNpcIngressRuntime.Contract contract,long tick){
        this.client=client;this.services=services;navigator=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);
        runtime=new QuiverNpcIngressRuntime(contract,candidates(),tick);Entity selected=null;
        if(runtime.target()!=null)for(var e:client.level.entitiesForRendering())if(e.getUUID().toString().equals(runtime.target()))selected=e;
        target=selected;
    }
    private List<QuiverNpcIngressRuntime.Candidate> candidates(){
        List<QuiverNpcIngressRuntime.Candidate> observed=new ArrayList<>();
        for(var e:client.level.entitiesForRendering())if(e!=client.player&&!e.isRemoved())observed.add(new QuiverNpcIngressRuntime.Candidate(e.getUUID().toString(),e.getName().getString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),e.hasCustomName(),e.isAlive(),e.distanceTo(client.player)));
        return observed;
    }
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return walking&&runtime.state()==QuiverNpcIngressRuntime.State.APPROACH&&travel.consumeExpectedTeleport(position,now);}
    boolean open(){return runtime.state()==QuiverNpcIngressRuntime.State.OPEN;}boolean terminal(){return runtime.terminal();}String status(){return runtime.reason();}
    void tick(MenuSnapshot menu,boolean unchanged,long tick){
        double reach=client.player.entityInteractionRange();
        boolean owned=target!=null&&client.level.getEntity(target.getId())==target&&!target.isRemoved();
        boolean visible=owned&&client.player.hasLineOfSight(target);
        boolean reachable=owned&&Double.isFinite(reach)&&reach>0&&target.getBoundingBox().getCenter().distanceTo(client.player.getEyePosition())<=Math.min(2.8,reach);
        boolean hit=owned&&client.hitResult instanceof EntityHitResult h&&h.getEntity()==target&&h.getLocation().distanceTo(client.player.getEyePosition())<=reach;
        String title=client.gui.screen()==null?null:client.gui.screen().getTitle().getString();
        var action=runtime.tick(new QuiverNpcIngressRuntime.Observation(candidates(),client.player.containerMenu.getCarried().isEmpty(),unchanged,title,menu!=null,reachable,visible,hit),tick);
        if(action.isEmpty()){stop();return;}
        switch(action.get()){
            case APPROACH -> {
                if(!owned){runtime.cancel("Quiver NPC object replaced");stop();return;}
                if(!walking){
                    if(++paths>3){runtime.cancel("Quiver NPC approach exhausted bounded paths");stop();return;}
                    var p=target.blockPosition();
                    for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{1,0},{-1,0},{0,1},{0,-1}})
                        if(travel.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+offset[0],p.getY(),p.getZ()+offset[1])),.35,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL)){walking=true;break;}
                    if(!walking){runtime.cancel("No loaded supported safe path to exact quiver NPC");stop();return;}
                }
                var state=travel.tick();if(state==McTravelNavigator.State.FAILED){runtime.cancel("Quiver NPC path blocked");stop();}
                else if(state==McTravelNavigator.State.ARRIVED){travel.stop();walking=false;}
            }
            case AIM,INTERACT -> {
                travel.stop();walking=false;var delta=target.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
                services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
                if(action.get()==QuiverNpcIngressRuntime.Action.INTERACT){
                    // Repeat the object/raycast/cursor gates immediately at the native action boundary.
                    boolean accepted=owned&&visible&&reachable&&client.gameMode!=null&&client.player.containerMenu.getCarried().isEmpty()
                            &&client.gui.screen()==null&&client.hitResult instanceof EntityHitResult fresh&&fresh.getEntity()==target&&fresh.getLocation().distanceTo(client.player.getEyePosition())<=reach;
                    if(accepted)client.gameMode.interact(client.player,target,(EntityHitResult)client.hitResult,InteractionHand.MAIN_HAND);
                    runtime.applied(accepted);services.rotation().cancelRotation();
                }
            }
        }
    }
    void stop(){travel.stop();walking=false;services.rotation().cancelRotation();}
}
