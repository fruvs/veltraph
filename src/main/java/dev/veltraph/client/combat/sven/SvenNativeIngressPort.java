package dev.veltraph.client.combat.sven;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;

/** Ordinary native ground path/rotation/use adapter. It never writes blocks or invents resource physics. */
public final class SvenNativeIngressPort implements SvenIngressPort {
    private final Minecraft client;
    private final FarmServices services;
    private final ConfigService config;
    private final SvenSourceBindings source;
    private McSharedMovementNavigator navigator;
    private final McNavigator terrain;
    private Request active;
    private Consumer<Result> callback;
    private InventoryScreen ownedInventory;
    private boolean walking;
    private Object player,world,connection;
    private long epoch;
    private int preparationDelay;
    private Result preparationResult;
    private net.minecraft.world.item.ItemStack resource;
    public SvenNativeIngressPort(Minecraft client,FarmServices services,ConfigService config,SvenSourceBindings source){
        this.client=client;this.services=services;this.config=config;this.source=source;navigator=new McSharedMovementNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);
    }
    public void bind(){cancel();player=client.player;world=client.level;connection=client.getConnection();navigator=new McSharedMovementNavigator(client,services,config);}
    public boolean contextValid(){
        if(client.player!=null&&client.level!=null&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player.isAlive())return true;
        return false;
    }
    /** Root must explicitly authorize this exact observed permit after its lifecycle guards. */
    public boolean commitNativeWarp(SvenWarpPermit permit){
        if(!source.commitNativeWarp(permit))return false;
        player=client.player;world=client.level;connection=client.getConnection();navigator=new McSharedMovementNavigator(client,services,config);return true;
    }
    private static net.minecraft.core.BlockPos nativePos(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    @Override public Optional<Boolean> gatePassable(BlockPos gate){
        if(!contextValid()||!client.level.isLoaded(nativePos(gate)))return Optional.empty();
        var state=client.level.getBlockState(nativePos(gate));
        if(state.getBlock() instanceof FenceGateBlock)return Optional.of(state.getValue(FenceGateBlock.OPEN));
        if(state.getBlock() instanceof SnowLayerBlock)return Optional.of(state.getValue(SnowLayerBlock.LAYERS)==1);
        // Source fills its registry set from DEFAULT-state empty collision, excluding snow.
        // IllIlIll... predicate uses EmptyBlockGetter.INSTANCE / BlockPos.ZERO and catches failure false.
        if(state.is(Blocks.AIR))return Optional.of(true);
        try{return Optional.of(state.getBlock()!=Blocks.SNOW&&state.getBlock().defaultBlockState()
                .getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).isEmpty());}
        catch(RuntimeException failure){return Optional.of(false);}
    }
    @Override public Optional<String> resourceReadiness(){
        var first=config.get(SvenConfig.SVEN_RESOURCE_NAME);var second=config.get(SvenConfig.SVEN_RESOURCE_ALTERNATIVE_NAME);
        if(first.isBlank())return Optional.of("Current primary salted resource lookup is unbound (SVEN_RESOURCE_NAME)");
        if(namedSlots(first).isEmpty()&&second.isBlank())return Optional.of("Primary resource absent; current alternate salted lookup is unbound (SVEN_RESOURCE_ALTERNATIVE_NAME)");
        return Optional.empty();
    }
    private List<Integer> namedSlots(String name){
        if(name==null||name.isBlank()||!contextValid())return List.of();
        List<Integer> matches=new ArrayList<>();for(int slot=0;slot<9;slot++){
            var stack=client.player.getInventory().getItem(slot);
            if(!stack.isEmpty()&&clean(stack.getHoverName().getString()).contains(name))matches.add(slot);
        }return matches;
    }
    @Override public Optional<Integer> resourceHotbarSlot(){
        for(var name:List.of(config.get(SvenConfig.SVEN_RESOURCE_NAME),config.get(SvenConfig.SVEN_RESOURCE_ALTERNATIVE_NAME))){
            var slots=namedSlots(name);if(!slots.isEmpty())return Optional.of(slots.getFirst());
        }return Optional.empty();
    }
    @Override public Optional<Boolean> hasEmptyHotbarSlot(){
        if(!contextValid())return Optional.empty();for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).isEmpty())return Optional.of(true);
        return Optional.of(false);
    }
    @Override public List<BlockPos> crackedGateCandidates(BlockPos gate){
        if(!contextValid())return List.of();List<BlockPos> candidates=new ArrayList<>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=0;y++)for(int z=-1;z<=1;z++){
            var p=gate.add(x,y,z);if(client.level.isLoaded(nativePos(p))&&client.level.getBlockState(nativePos(p)).is(Blocks.CRACKED_STONE_BRICKS))candidates.add(p);
        }return List.copyOf(candidates);
    }
    @Override public Vec3 eyePosition(){return !contextValid()?null:new Vec3(client.player.getX(),client.player.getEyeY(),client.player.getZ());}
    @Override public int selectedSlot(){return services.inventory().selectedSlot();}
    @Override public void selectSlot(int slot){if(contextValid())services.inventory().selectSlot(slot);}
    @Override public void begin(Request request,Consumer<Result> completed){
        if(!contextValid()){completed.accept(Result.failed("Native player/world/connection ownership unavailable"));return;}
        if(active!=null){completed.accept(Result.failed("Another owned Sven operation is pending"));return;}
        active=request;callback=completed;long captured=epoch;
        switch(request.operation()){
            case PREPARE -> {
                var observation=source.preparationObservation();
                if(observation.isEmpty()){source.begin(request,result->{if(captured==epoch&&active==request&&contextValid())finish(result);});return;}
                if(client.gui.screen()!=null){finish(Result.blocked("Preparation screen-close ownership/helper is unbound"));return;}
                var observed=observation.get();
                var decision=SvenPreparationPolicy.evaluate(new SvenPreparationPolicy.Observation(observed.prerequisite(),observed.correctArea(),observed.usableLocation(),
                        new Vec3(client.player.getX(),client.player.getY(),client.player.getZ())));
                if(decision.result().outcome()!=Outcome.COMPLETE){finish(decision.result());return;}
                preparationResult=source.preparationContinuation(decision.result());preparationDelay=decision.delayTicks();
            }
            case WALK_ENTRANCE, WALK_INSIDE -> {
                if(client.gui.screen()!=null){finish(Result.failed("Screen opened before physical walking"));return;}
                if(request.operation()==Operation.WALK_INSIDE&&!gatePassable(request.profile().gate()).orElse(false)){
                    finish(Result.failed("Observed gate is no longer passable"));return;
                }
                // Both source paths use precisely this same profile target. Native adapter checks real support.
                if(!navigator.startGround(terrain.position(request.profile().pathTarget()),2500,32,.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){finish(Result.failed("Physical path unavailable: "+navigator.status()));return;}
                walking=true;
            }
            case ROTATE -> {
                if(request.aimTarget()==null||request.resourceSlot()<0||!resourceHotbarSlot().filter(s->s==request.resourceSlot()).isPresent()){
                    finish(Result.failed("Selected resource/candidate lost its observed binding"));return;
                }
                resource=client.player.getInventory().getItem(request.resourceSlot()).copy();
                Vec3 eye=eyePosition(),target=request.aimTarget();double dx=target.x()-eye.x(),dy=target.y()-eye.y(),dz=target.z()-eye.z();
                float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
                services.rotation().rotateTo(yaw,pitch,()->{if(captured==epoch&&active==request&&contextValid())finish(Result.complete());});
            }
            case USE_RESOURCE -> {
                if(client.gui.screen()!=null||client.gameMode==null||selectedSlot()!=request.resourceSlot()||resource==null
                        ||!net.minecraft.world.item.ItemStack.matches(resource,client.player.getInventory().getItem(request.resourceSlot()))
                        ||!(client.hitResult instanceof BlockHitResult hit)||request.aimTarget()==null
                        ||hit.getLocation().distanceTo(client.player.getEyePosition())>4.0
                        ||hit.getLocation().distanceTo(new net.minecraft.world.phys.Vec3(request.aimTarget().x(),request.aimTarget().y(),request.aimTarget().z()))>1.0){
                    finish(Result.blocked("Guarded right-click lacks exact held-stack / reachable source candidate hit proof"));return;
                }
                // This sends ordinary use to the server. Only later gate observation permits inside walking.
                client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);finish(Result.complete());
            }
            case OPEN_INVENTORY -> {
                if(client.gui.screen()!=null){finish(Result.failed("Unowned screen already open"));return;}
                ownedInventory=new InventoryScreen(client.player);client.gui.setScreen(ownedInventory);
                // Completion is acknowledged in tick after observing the actual screen, not at request time.
            }
            case CLOSE_INVENTORY -> {
                if(ownedInventory==null||client.gui.screen()!=ownedInventory){finish(Result.failed("Owned inventory screen unavailable"));return;}
                client.gui.setScreen(null);ownedInventory=null;
            }
            case TRAVEL_PREREQUISITE -> {
                source.begin(request,result->{if(captured==epoch&&active==request&&contextValid())finish(result);});
            }
            default -> source.begin(request,result->{if(captured==epoch&&active==request&&contextValid())finish(result);});
        }
    }
    @Override public void tick(){
        if(active==null){source.tick();return;}
        if(!contextValid()){finish(Result.failed("Native context changed during owned operation"));return;}
        source.tick();if(active==null)return;
        if(active.operation()==Operation.PREPARE&&preparationResult!=null){
            if(--preparationDelay<=0){var result=preparationResult;preparationResult=null;finish(result);}
        }else if(walking){
            var state=navigator.tick();
            if(state==McTravelNavigator.State.ARRIVED){walking=false;navigator.stop();finish(Result.complete());}
            else if(state==McTravelNavigator.State.FAILED){walking=false;navigator.stop();finish(Result.failed("Physical walk failed: "+navigator.status()));}
        }else if(active.operation()==Operation.OPEN_INVENTORY){
            if(client.gui.screen()==ownedInventory)finish(Result.complete());else finish(Result.failed("Inventory open acknowledgment lost"));
        }else if(active.operation()==Operation.CLOSE_INVENTORY){
            if(client.gui.screen()==null)finish(Result.complete());else finish(Result.failed("Inventory close acknowledgment lost"));
        }
    }
    private void finish(Result result){var done=callback;active=null;callback=null;if(done!=null)done.accept(result);}
    @Override public void cancel(){
        epoch++;active=null;callback=null;walking=false;source.cancel();navigator.stop();services.rotation().cancelRotation();
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(contextValid()&&ownedInventory!=null&&client.gui.screen()==ownedInventory)client.gui.setScreen(null);
        ownedInventory=null;resource=null;preparationResult=null;preparationDelay=0;
    }
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        if(contextValid()&&walking&&navigator.consumeExpectedTeleport(position,now))return true;
        // Command warp arrivals may replace P/W before Root commits the unchanged issued permit.
        return source.consumeExpectedTeleport(position,now);
    }
    public boolean allowsScreen(String title){return ownedInventory!=null&&client.gui.screen()==ownedInventory||source.allowsScreen(title);}
    private static String clean(String value){return value.replaceAll("§.","").strip();}
}
