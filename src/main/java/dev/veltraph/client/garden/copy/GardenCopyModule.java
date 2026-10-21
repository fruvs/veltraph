package dev.veltraph.client.garden.copy;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.GardenTerminalView;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.*;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Additive exact-state local copy. No overwrite, destruction, original native transform or purchases. */
public final class GardenCopyModule extends AbstractModule {
    public static final String ID="garden_plot_copy";
    private static final Set<String> ORDINARY_MATERIALS=Set.of("minecraft:dirt","minecraft:grass_block","minecraft:coarse_dirt","minecraft:stone","minecraft:cobblestone","minecraft:stone_bricks","minecraft:smooth_stone","minecraft:oak_planks","minecraft:spruce_planks","minecraft:birch_planks","minecraft:jungle_planks","minecraft:dark_oak_planks","minecraft:oak_log","minecraft:spruce_log","minecraft:birch_log","minecraft:jungle_log");
    private record Cell(BlockPos pos,BlockState state){}
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final McTravelNavigator navigation;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private GardenBlueprint blueprint;private BlockPos anchor;private int turns;private List<Cell> cells=List.of();private GardenPasteRuntime runtime=new GardenPasteRuntime();
    private Object boundWorld,boundConnection,boundPlayer;private int originalSlot;private float originalYaw,originalPitch;private boolean lease,walking,originalFlying;private String last="No explicit copy plan";private long deadline;
    private List<Object> policy=List.of();
    private final Set<dev.veltraph.client.world.Vec3> attemptedApproaches=new HashSet<>();private int approachIndex=-1;
    private boolean activationCaptured;private int ownedSlot;
    private GardenTerminalView terminalView;
    private List<ItemStack> returnEquipment=List.of();
    private int returnSlot;
    private Object returnAuthority;
    private long returnEpoch;
    private List<ItemStack> equipment(){
        var out=new ArrayList<ItemStack>();
        for(int i=0;i<client.player.getInventory().getContainerSize();i++)out.add(client.player.getInventory().getItem(i).copy());
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())out.add(client.player.getItemBySlot(slot).copy());
        return out;
    }
    private boolean sameEquipment(){
        var actual=equipment();if(actual.size()!=returnEquipment.size())return false;
        for(int i=0;i<actual.size();i++)if(!ItemStack.matches(actual.get(i),returnEquipment.get(i)))return false;
        return true;
    }
    private boolean ownsReturnCamera(){
        if(!(client.player==boundPlayer&&client.level==boundWorld&&client.getConnection()==boundConnection))return false;
        if(terminalView==null||terminalView.state()==GardenTerminalView.State.IDLE)
            return !McOwnedRotation.installed(client)||(McOwnedRotation.authority(client)==returnAuthority&&McOwnedRotation.epoch(client)==returnEpoch);
        // Issued queued rotation may publish its own new epoch. The helper proves that transition
        // with the exact service request receipt; fixed preissue epoch equality would reject it.
        return McOwnedRotation.authority(client)==returnAuthority&&interactionRotation.currentRequest();
    }
    public GardenCopyModule(Minecraft c,FarmServices s,ConfigService cfg){super(ID,"Garden plot copy",ModuleCategory.INTERMEDIARY);client=c;services=s;config=cfg;interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(c,s,()->this);navigation=new McTravelNavigator(c,s,cfg);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&lease&&walking&&client.isSameThread()&&boundPlayer!=null&&boundConnection!=null
                &&client.player==boundPlayer&&client.level==boundWorld&&client.getConnection()==boundConnection
                &&navigation.consumeExpectedTeleport(position,now);
    }
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public void prepare(GardenBlueprint b,BlockPos anchor,int turns){
        if(isEnabled())throw new IllegalStateException("Stop active copy before replacing plan");if(turns<0||turns>3)throw new IllegalArgumentException("Rotation must be0..3");if(!b.blockEntities().isEmpty())throw new IllegalArgumentException("Source block-entity payload is not copied; explicit source must exclude these cells");
        b.destinationBounds(anchor,turns);if(client.level==null)throw new IllegalArgumentException("Loaded world required");
        var out=new ArrayList<Cell>();var rotation=Rotation.values()[turns];
        try{for(int i=0;i<b.states().size();i++){var state=BlockStateParser.parseForBlock(client.level.registryAccess().lookupOrThrow(Registries.BLOCK),b.states().get(i),false).blockState().rotate(rotation);out.add(new Cell(b.destination(i,anchor,turns),state));}}
        catch(Exception invalid){throw new IllegalArgumentException("Unsupported full state: "+invalid.getMessage(),invalid);}
        var invalid=validate(out);if(invalid.isPresent())throw new IllegalArgumentException(invalid.get());
        this.blueprint=b;this.anchor=anchor;this.turns=turns;cells=List.copyOf(out);runtime=new GardenPasteRuntime();activationCaptured=false;boundWorld=client.level;boundPlayer=client.player;boundConnection=client.getConnection();last="Prepared exact dense destination mask; "+budget();
    }
    private net.minecraft.core.BlockPos nativePos(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BlockPos p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private Optional<String> validate(List<Cell> plan){
        if(plan.size()>config.get(GardenCopyConfig.GARDEN_SCAN_MAX_CELLS))return Optional.of("Dense destination exceeds scan budget");int operations=0;
        for(var c:plan){if(!loaded(c.pos()))return Optional.of("Unloaded destination "+c.pos());var actual=client.level.getBlockState(nativePos(c.pos()));if(actual.hasBlockEntity())return Optional.of("Protected destination block entity "+c.pos());if(actual.equals(c.state()))continue;
            if(!actual.isAir()||c.state().isAir())return Optional.of("Destination differs from explicit AIR/exact mask; no overwrite "+c.pos());
            if(c.state().hasBlockEntity()||!c.state().getFluidState().isEmpty()||!(c.state().getBlock().asItem() instanceof BlockItem)||!ORDINARY_MATERIALS.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(c.state().getBlock()).toString()))return Optional.of("Missing state requires unrecovered special placement mechanics: "+c.state());
            operations++;
        }
        return operations>config.get(GardenCopyConfig.GARDEN_COPY_MAX_OPERATIONS)?Optional.of("Placement operation budget exceeded"):Optional.empty();
    }
    public Map<String,Long> budget(){var out=new TreeMap<String,Long>();if(client.level!=null&&client.level==boundWorld)for(int i=runtime.index();i<cells.size();i++){var c=cells.get(i);if(!loaded(c.pos())||!client.level.getBlockState(nativePos(c.pos())).equals(c.state()))if(!c.state().isAir())out.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.state().getBlock().asItem()).toString(),1L,Long::sum);}return Collections.unmodifiableMap(out);}
    public Map<BlockPos,String> destinationMask(){var out=new LinkedHashMap<BlockPos,String>();for(var c:cells)out.put(c.pos(),BlockStateParser.serialize(c.state()));return Collections.unmodifiableMap(out);}
    private List<Object> policy(){return List.of(config.get(GardenCopyConfig.GARDEN_COPY_ENABLED),config.get(GardenCopyConfig.GARDEN_SCAN_MAX_CELLS),config.get(GardenCopyConfig.GARDEN_COPY_MAX_OPERATIONS),config.get(GardenCopyConfig.GARDEN_COPY_ACK_MS),config.get(GardenCopyConfig.GARDEN_COPY_OPERATION_TIMEOUT_MS),config.get(GardenCopyConfig.GARDEN_COPY_TIMEOUT_MS),config.get(GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS),config.get(GardenCopyConfig.GARDEN_COPY_ALLOW_FLIGHT));}
    @Override public Optional<String> preStartCheck(){
        if(!config.get(GardenCopyConfig.GARDEN_COPY_ENABLED))return Optional.of("Explicit ordinary copy placement opt-in required");if(blueprint==null)return Optional.of("Prepare exact named blueprint and destination using /vgarden paste");
        if(client.level!=boundWorld||client.player!=boundPlayer||client.getConnection()!=boundConnection)return Optional.of("Copy belongs to another world/player/connection; prepare explicitly again");
        if(client.player==null||!client.player.isAlive()||client.gameMode==null||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Alive player, closed menu and empty cursor required");
        if(client.player.getAbilities().instabuild||client.player.isSpectator())return Optional.of("Ordinary survival resource accounting required");
        if(config.get(GardenCopyConfig.GARDEN_COPY_ACK_MS)<250||config.get(GardenCopyConfig.GARDEN_COPY_OPERATION_TIMEOUT_MS)<1000||config.get(GardenCopyConfig.GARDEN_COPY_TIMEOUT_MS)<1000||config.get(GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS)<1||config.get(GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS)>8)return Optional.of("Invalid acknowledgement/deadline/attempt policy");return validate(cells);
    }
    @Override public void onEnable(){terminalView=null;var invalid=preStartCheck();if(invalid.isPresent()){last=invalid.get();setEnabled(false);return;}runtime.resume();if(!activationCaptured){originalSlot=services.inventory().selectedSlot();originalYaw=client.player.getYRot();originalPitch=client.player.getXRot();originalFlying=client.player.getAbilities().flying;activationCaptured=true;}ownedSlot=services.inventory().selectedSlot();lease=true;walking=false;approachIndex=-1;attemptedApproaches.clear();deadline=now()+config.get(GardenCopyConfig.GARDEN_COPY_TIMEOUT_MS);policy=policy();services.input().releaseAllMovement();services.breaking().setBreaking(false);services.breaking().installTargetGuard(this,p->false);last="Copy lease; "+budget();}
    private long now(){return services.clock().nowMillis();}
    private void stop(String message){last=message;setEnabled(false);}
    private BlockHitResult hit(Cell c){return hit(c,client.player.getEyePosition());}
    private BlockHitResult hit(Cell c,Vec3 eye){
        BlockHitResult best=null;double distance=Double.POSITIVE_INFINITY;
        for(var face:Direction.values()){var support=nativePos(c.pos()).relative(face.getOpposite());var p=new BlockPos(support.getX(),support.getY(),support.getZ());if(!loaded(p))continue;var state=client.level.getBlockState(support);if(state.hasBlockEntity()||state.getCollisionShape(client.level,support).isEmpty())continue;var point=Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);var trace=client.level.clip(new ClipContext(eye,point.add(-face.getStepX()*.001,-face.getStepY()*.001,-face.getStepZ()*.001),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));double d=eye.distanceTo(point);if(trace.getType()==HitResult.Type.BLOCK&&trace.getBlockPos().equals(support)&&trace.getDirection()==face&&d<distance){var hit=new BlockHitResult(point,face,support,false);var context=new BlockPlaceContext(client.player,InteractionHand.MAIN_HAND,new ItemStack(c.state().getBlock().asItem()),hit);var predicted=c.state().getBlock().getStateForPlacement(context);if(predicted!=null&&predicted.equals(c.state())&&context.getClickedPos().equals(nativePos(c.pos()))){best=hit;distance=d;}}}
        return best;
    }
    @Override public void onTick(){
        if(!isEnabled())return;services.breaking().setBreaking(false);
        if(client.level!=boundWorld||client.player!=boundPlayer||client.getConnection()!=boundConnection||client.player==null||!client.player.isAlive()||client.player.getAbilities().instabuild||client.player.isSpectator()||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty()||!policy.equals(policy())){stop("World/player/death/resource-mode/menu/cursor/policy changed; checkpoint retained");return;}
        if(now()>=deadline){stop("Copy deadline expired; checkpoint retained");return;}
        if(terminalView!=null){
            McOwnedRotation.validate(client);
            var state=terminalView.tick();
            if(state==GardenTerminalView.State.FAILED){stop("Return view custody changed; checkpoint retained");return;}
            if(state==GardenTerminalView.State.COMPLETE)stop("COMPLETE exact destination mask; "+cells.size()+" cells checked");
            return;
        }
        // Retain/check dense AIR and existing states without spending one game tick per empty cell.
        // A newly issued operation still gets only one acknowledgement observation per game tick.
        for(int n=0;n<256&&runtime.index()<cells.size()&&!runtime.awaitingAcknowledgement();n++){
            var existing=cells.get(runtime.index());if(!loaded(existing.pos())){stop("Unknown destination chunk; checkpoint retained");return;}var state=client.level.getBlockState(nativePos(existing.pos()));if(state.hasBlockEntity()){stop("Protected destination changed; checkpoint retained");return;}if(!state.equals(existing.state()))break;
            runtime.tick(cells.size(),new GardenPasteRuntime.Facts(true,true,state.isAir(),false,false,false,false,now()),config.get(GardenCopyConfig.GARDEN_COPY_ACK_MS),config.get(GardenCopyConfig.GARDEN_COPY_OPERATION_TIMEOUT_MS),config.get(GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS));
        }
        if(runtime.index()>=cells.size()){complete();return;}var c=cells.get(runtime.index());if(!loaded(c.pos())){stop("Unknown destination chunk; checkpoint retained");return;}
        if(approachIndex!=runtime.index()){attemptedApproaches.clear();approachIndex=runtime.index();}
        var actual=client.level.getBlockState(nativePos(c.pos()));if(actual.hasBlockEntity()){stop("Protected destination changed; checkpoint retained");return;}
        var placement=hit(c);boolean reachable=placement!=null&&client.player.getEyePosition().distanceTo(placement.getLocation())<=4.5;int slot=-1;
        for(int i=0;i<9;i++){var s=client.player.getInventory().getItem(i);if(!s.isEmpty()&&s.is(c.state().getBlock().asItem())&&!s.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)&&!s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)){slot=i;break;}}
        boolean aligned=placement!=null&&client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(placement.getBlockPos())&&h.getDirection()==placement.getDirection();
        var action=runtime.tick(cells.size(),new GardenPasteRuntime.Facts(true,actual.equals(c.state()),actual.isAir(),slot>=0,slot==services.inventory().selectedSlot(),reachable,aligned&&client.player.isShiftKeyDown(),now()),config.get(GardenCopyConfig.GARDEN_COPY_ACK_MS),config.get(GardenCopyConfig.GARDEN_COPY_OPERATION_TIMEOUT_MS),config.get(GardenCopyConfig.GARDEN_COPY_MAX_ATTEMPTS));
        if(action==GardenPasteRuntime.Action.FAIL||action==GardenPasteRuntime.Action.PAUSE){stop(runtime.status()+" | remaining "+budget()+" | cell="+c.pos()+" pose="+client.player.position()+" hit="+client.hitResult+" placement="+placement+" aligned="+aligned);return;}if(action==GardenPasteRuntime.Action.COMPLETE){complete();return;}
        if(action==GardenPasteRuntime.Action.MOVE){
            if(!walking){boolean found=false;services.input().releaseAllMovement();var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input());for(int[] d:new int[][]{{0,-1},{-1,0},{1,0},{0,1},{0,-2},{0,2},{-2,0},{2,0}})for(int dy:new int[]{0,-1,1})if(!found){var p=new dev.veltraph.client.world.Vec3(c.pos().x()+.5+d[0],c.pos().y()+dy,c.pos().z()+.5+d[1]);var eye=new Vec3(p.x(),p.y()+1.62,p.z());var candidate=hit(c,eye);if(attemptedApproaches.contains(p)||candidate==null||eye.distanceTo(candidate.getLocation())>4.5||client.player.position().distanceTo(new Vec3(p.x(),p.y(),p.z()))<.35)continue;attemptedApproaches.add(p);if(terrain.standingClear(p))found=navigation.start(new LocalRoute.Node(LocalRoute.Kind.WALK,p,0,0),.35,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL);if(!found&&config.get(GardenCopyConfig.GARDEN_COPY_ALLOW_FLIGHT)&&client.player.getAbilities().mayfly)found=navigation.start(new LocalRoute.Node(LocalRoute.Kind.FLY,p,0,0));}if(!found){stop("No loaded visible/supporting placement approach; checkpoint retained");return;}walking=true;}
            var st=navigation.tick();if(st==McTravelNavigator.State.FAILED){last="Bounded alternate standing approach after "+navigation.status();navigation.stop();walking=false;return;}if(st==McTravelNavigator.State.ARRIVED){walking=false;navigation.stop();}return;
        }
        navigation.stop();walking=false;if(actual.equals(c.state())){services.input().releaseAllMovement();return;}if(action==GardenPasteRuntime.Action.SELECT){services.inventory().selectSlot(slot);ownedSlot=slot;return;}
        if(placement!=null){var delta=placement.getLocation().subtract(client.player.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));services.input().setSneakHeld(aligned);}
        if(action==GardenPasteRuntime.Action.PLACE){
            var current=client.level.getBlockState(nativePos(c.pos()));var stack=client.player.getMainHandItem();var context=new BlockPlaceContext(client.player,InteractionHand.MAIN_HAND,stack,placement);var predicted=c.state().getBlock().getStateForPlacement(context);
            if(!current.isAir()||!context.getClickedPos().equals(nativePos(c.pos()))||predicted==null||!predicted.equals(c.state())||!c.state().canSurvive(client.level,nativePos(c.pos()))||client.player.getEyePosition().distanceTo(placement.getLocation())>4.5||!stack.is(c.state().getBlock().asItem())){stop("Exact state cannot be produced by verified native placement; no simplification");return;}
            client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,placement);client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
        }
        last=runtime.status();
    }
    private void complete(){
        var invalid=validate(cells);if(invalid.isPresent()||cells.stream().anyMatch(c->!client.level.getBlockState(nativePos(c.pos())).equals(c.state()))){stop("Final dense mask not confirmed; no completion");return;}
        navigation.stop();walking=false;services.input().releaseAllMovement();services.breaking().setBreaking(false);
        returnEquipment=equipment();returnSlot=services.inventory().selectedSlot();
        McOwnedRotation.validate(client);
        if(McOwnedRotation.installed(client)&&McOwnedRotation.authority(client)!=this){stop("Return view authority unavailable; checkpoint retained");return;}
        // End the prior mining/placement heading before binding the terminal request, under our live authority.
        interactionRotation.cancelRotation();returnAuthority=McOwnedRotation.authority(client);returnEpoch=McOwnedRotation.epoch(client);
        terminalView=new GardenTerminalView(interactionRotation,this::returnCurrent,this::ownsReturnCamera,originalYaw,originalPitch);
        last="RETURN_VIEW exact destination mask confirmed";
        if(!terminalView.start())stop("Return view could not retain copy authority; checkpoint retained");
    }
    private boolean returnCurrent(){
        return isEnabled()&&lease&&client.player==boundPlayer&&client.level==boundWorld&&client.getConnection()==boundConnection
                &&client.player!=null&&client.player.isAlive()&&!client.player.getAbilities().instabuild&&!client.player.isSpectator()
                &&client.gui.screen()==null&&client.player.containerMenu.getCarried().isEmpty()&&policy.equals(policy())
                &&services.inventory().selectedSlot()==returnSlot&&sameEquipment()&&now()<deadline;
    }
    @Override public void onDisable(){
        interactionRotation.release(navigation);services.input().releaseAllMovement();services.breaking().setBreaking(false);services.breaking().clearTargetGuard(this);
        if(terminalView!=null)terminalView.cancel();
        interactionRotation.cancelRotation();
        if(lease&&client.player==boundPlayer&&client.level==boundWorld&&client.getConnection()==boundConnection){
            if(services.inventory().selectedSlot()==ownedSlot)services.inventory().selectSlot(originalSlot);client.player.getAbilities().flying=originalFlying&&client.player.getAbilities().mayfly;if(client.player.isAlive())client.player.onUpdateAbilities();
        }
        lease=false;walking=false;
    }
    public void clear(){if(isEnabled())throw new IllegalStateException("Cannot clear active copy");blueprint=null;cells=List.of();runtime=new GardenPasteRuntime();last="Explicit copy cleared";}
    public int checkpoint(){return runtime.index();}@Override public String diagnostics(){return last+" | checkpoint="+runtime.index()+"/"+cells.size()+(blueprint==null?"":" | "+blueprint.name()+" @"+anchor+" rotation="+turns);}
}
