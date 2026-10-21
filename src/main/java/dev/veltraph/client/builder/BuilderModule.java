package dev.veltraph.client.builder;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Guarded local layout executor and owner of source generated subprocess workflows. */
public final class BuilderModule extends AbstractModule {
    public static final String ID="farm_builder";
    private final Minecraft client;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final java.util.function.Supplier<dev.veltraph.client.core.Module> movementParent;
    private final FarmServices services;
    private final dev.veltraph.client.config.ConfigService config;
    private final McTravelNavigator navigation;
    private final BuildRuntime runtime=new BuildRuntime();
    private BuildLayout layout;
    private BuildSetupExecutor setup;
    private GeneratedBuildExecutor generated;
    private Direction placementFace;
    private boolean walking;
    private Object travelPlayer,travelWorld,travelConnection;
    private String failure="";
    private int originalSlot;
    public BuilderModule(Minecraft client,FarmServices services) {
        this(client,services,new dev.veltraph.client.config.MapConfigService());
    }
    public BuilderModule(Minecraft client,FarmServices services,dev.veltraph.client.config.ConfigService config) {this(client,services,config,null);}
    BuilderModule(Minecraft client,FarmServices services,dev.veltraph.client.config.ConfigService config,java.util.function.Supplier<dev.veltraph.client.core.Module> parent) {
        super(ID,"Farm Builder",ModuleCategory.BUILDERS);this.client=client;this.services=services;this.config=config;movementParent=parent==null?()->this:parent;interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,movementParent);navigation=new McTravelNavigator(client,services,config);
    }
    public void load(BuildLayout layout) { if(isEnabled()) throw new IllegalStateException("Stop the builder before loading");this.layout=layout;setup=null;generated=null;placementFace=null;failure="";runtime.reset(); }
    void loadDirected(BuildLayout layout,BlockPos direction){load(layout);if(direction!=null)for(var face:Direction.values())if(face.getStepX()==direction.x()&&face.getStepY()==direction.y()&&face.getStepZ()==direction.z())placementFace=face;}
    public void loadGenerated(GeneratedBuildPlan plan) {
        if(isEnabled())throw new IllegalStateException("Stop the builder before loading");
        for(var process:plan.processes())for(var p:List.of(process.first(),process.last()))
            if(p.x()<-240||p.x()>239||p.z()<-240||p.z()>239||p.y()<0||p.y()>255)throw new IllegalArgumentException("Generated target outside Garden bounds: "+p);
        generated=new GeneratedBuildExecutor(client,services,config,plan,this);setup=null;layout=null;failure="";
    }
    public void loadGeneratedSource(int source,BlockPos player,int turns) {
        var plan=switch(source){case 95->SourceGeneratedPlans.source95(config);case 92->SourceGeneratedPlans.source92(config);default->throw new IllegalArgumentException("Unknown generated source graph");};
        loadGenerated(plan.transformed(SourceBuilderTransform.centered(plan.width(),plan.height(),plan.depth(),player,turns)));
    }
    public void bindGeneratedResource(String binding,String id) {
        if(isEnabled())throw new IllegalStateException("Stop the builder before changing bindings");
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Use a registry item/block id");
        var key=switch(binding) {
            case "temporary"->dev.veltraph.client.config.GeneratedBuilderConfig.TEMPORARY_BLOCK;
            case "clear"->dev.veltraph.client.config.GeneratedBuilderConfig.CLEAR_BLOCK;
            case "hoe"->dev.veltraph.client.config.GeneratedBuilderConfig.TILL_ITEM;
            case "plant-item"->dev.veltraph.client.config.GeneratedBuilderConfig.PLANT_ITEM;
            case "plant-block"->dev.veltraph.client.config.GeneratedBuilderConfig.PLANT_BLOCK;
            case "marker"->dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_MARKER_BLOCK;
            case "tool-block"->dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_TOOL_BLOCK;
            default->throw new IllegalArgumentException("Binding must be temporary, clear, hoe, plant-item, plant-block or marker");
        };
        config.set(key,id);
    }
    public void bindGeneratedContract(String binding,String json) {
        if(isEnabled())throw new IllegalStateException("Stop the builder before changing bindings");
        var key=switch(binding) {
            case "palette"->{GeneratedResourceBinding.palette(json);yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE92_PALETTE;}
            case "resource"->{GeneratedResourceBinding.decode(json);yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING;}
            case "plant-resource"->{GeneratedResourceBinding.decode(json);yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING;}
            case "acquisition"->{GeneratedMenuFlow.decode(json);yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_ACQUISITION_FLOW;}
            case "sell"->{GeneratedMenuFlow.decode(json);yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_SELL_FLOW;}
            case "crop-acquisition"->{Source95AcquisitionBinding.decode(json,config.get(dev.veltraph.client.config.BuilderConfig.BUILDER_CROP_TYPE));yield dev.veltraph.client.config.GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING;}
            default->throw new IllegalArgumentException("Contract must be palette, resource, plant-resource, acquisition, crop-acquisition or sell");
        };config.set(key,json);
    }
    public void loadRecoveredEndcaps(BlockPos player,int turns) {
        var source=BuildLayouts.recovered95Endcaps(config.get(dev.veltraph.client.config.BuilderConfig.BUILDER_BUILDING_BLOCK));
        load(SourceBuilderTransform.centered(BuildLayouts.SOURCE_WIDTH,BuildLayouts.SOURCE_HEIGHT,
                BuildLayouts.SOURCE_DEPTH,player,turns).apply(source));
    }
    public void loadSetup(BuildSetupPlan plan) {
        if(isEnabled())throw new IllegalStateException("Stop the builder before loading");
        for(var step:plan.steps()){var p=step.pos();
            if(p.x()< -240||p.x()>239||p.z()< -240||p.z()>239||p.y()<0||p.y()>255)
                throw new IllegalArgumentException("Setup target outside Garden bounds: "+p);
        }
        setup=new BuildSetupExecutor(client,services,config,movementParent);setup.load(plan);layout=null;generated=null;failure="";
    }
    public void loadRecoveredIrrigation(BlockPos player,int turns) {
        loadSetup(BuildSetupPlan.recovered95Irrigation(config.get(dev.veltraph.client.config.BuilderConfig.BUILDER_BUILDING_BLOCK))
                .transformed(SourceBuilderTransform.centered(95,1,287,player,turns)));
    }
    public List<dev.veltraph.client.metrics.FinancialActionReceipt> acknowledgedSource95Acquisitions(){return generated==null?List.of():generated.acquisitionReceipts();}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        if(!isEnabled()||!client.isSameThread()||travelPlayer==null||travelConnection==null
                ||client.player!=travelPlayer||client.level!=travelWorld||client.getConnection()!=travelConnection)return false;
        if(generated!=null)return generated.consumeExpectedTeleport(position,now);
        if(setup!=null)return setup.consumeExpectedTeleport(position,now);
        return walking&&navigation.consumeExpectedTeleport(position,now);
    }
    @Override public void onServerMessage(String line){if(isEnabled()&&generated!=null)generated.onServerMessage(line);}
    Map<String,Object> source95ToolDiagnostics(){return generated==null?Map.of():generated.toolDiagnostics();}
    SourceRecoveryTrace sourceRecoveryTrace(){return generated==null?null:generated.sourceRecoveryTrace();}
    public String preview() {
        if(generated!=null)return generated.preview();
        if(setup!=null)return setup.preview();
        if(layout==null) return "Load a layout with /vbuild load <name> [quarter-turns]";
        long place=layout.entries().stream().filter(e->e.operation()==BuildLayout.Operation.PLACE).count();
        return layout.name()+": "+place+" place, "+(layout.entries().size()-place)+" destroy. Material budget: "+layout.requiredMaterials()+". Anchor is fixed at load time.";
    }
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public Optional<GameArea> requiredArea() { return Optional.of(GameArea.GARDEN); }
    @Override public boolean allowsScreen(String title){return generated!=null&&generated.allowsScreen(title);}
    @Override public Optional<String> preStartCheck() { return layout==null&&setup==null&&generated==null?Optional.of(preview()):Optional.empty(); }
    @Override public void onEnable() { travelPlayer=client.player;travelWorld=client.level;travelConnection=client.getConnection();if(generated!=null)generated.resume();else if(setup!=null)setup.resume();else {runtime.reset();services.breaking().installTargetGuard(this,this::allowedDestroyTarget);}failure="";walking=false;originalSlot=services.inventory().selectedSlot(); }
    @Override public void onDisable() { if(generated!=null)generated.stop();if(setup!=null)setup.stop();services.breaking().clearTargetGuard(this);services.input().releaseAllMovement();interactionRotation.release(navigation);services.breaking().setBreaking(false);services.inventory().selectSlot(originalSlot);walking=false; }
    private boolean allowedDestroyTarget(BlockPos target) {
        if(layout==null||runtime.index()>=layout.entries().size()||client.level==null)return false;
        var entry=layout.entries().get(runtime.index());
        if(entry.operation()!=BuildLayout.Operation.DESTROY||!target.equals(entry.pos()))return false;
        var state=client.level.getBlockState(mc(target));
        return !state.hasBlockEntity()&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(entry.block());
    }
    private static net.minecraft.core.BlockPos mc(BlockPos p) { return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()); }
    @Override public void onTick() {
        if(generated!=null) {
            var state=generated.tick();
            if(state==GeneratedBuildRuntime.State.BLOCKED)fail(generated.status());
            else if(state==GeneratedBuildRuntime.State.COMPLETE)setEnabled(false);
            return;
        }
        if(setup!=null) {
            var action=setup.tick();
            if(action==BuildSetupRuntime.Action.STOP)fail(setup.status());
            else if(action==BuildSetupRuntime.Action.COMPLETE)setEnabled(false);
            return;
        }
        if(client.player==null||client.level==null||client.gui.screen()!=null||layout==null) {
            fail("Builder unavailable: "+(client.gui.screen()!=null?client.gui.screen().getClass().getSimpleName():"missing world/player/layout"));return;
        }
        services.breaking().setBreaking(false);
        if(walking) {
            var progress=navigation.tick();
            if(progress==McTravelNavigator.State.FAILED) { fail("Approach failed: "+navigation.status());return; }
            if(progress==McTravelNavigator.State.ACTIVE) return;
            walking=false;
        }
        if(runtime.index()>=layout.entries().size()) { setEnabled(false);return; }
        var entry=layout.entries().get(runtime.index());var pos=mc(entry.pos());
        var state=client.level.getBlockState(pos);
        if(state.hasBlockEntity()) { fail("Refusing to alter a block containing inventory/data");return; }
        int slot=-1;
        for(int i=0;i<9;i++) {
            var item=client.player.getInventory().getItem(i);
            if(entry.operation()==BuildLayout.Operation.PLACE) {
                if(item.getItem() instanceof BlockItem block && BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals(entry.block())) { slot=i;break; }
            } else if(!item.isEmpty()&&item.isCorrectToolForDrops(state)) { slot=i;break; }
        }
        if(entry.operation()==BuildLayout.Operation.DESTROY&&!state.requiresCorrectToolForDrops()) slot=originalSlot;
        BlockHitResult placement=null;
        if(entry.operation()==BuildLayout.Operation.PLACE) for(Direction face:Direction.values()) {
            if(placementFace!=null&&face!=placementFace)continue;
            var support=pos.relative(face.getOpposite());var supportState=client.level.getBlockState(support);
            if(supportState.hasBlockEntity()||supportState.getCollisionShape(client.level,support).isEmpty()) continue;
            var point=net.minecraft.world.phys.Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);
            // Raycast endpoints exactly on a face can count as MISS. Probe just inside the support,
            // while retaining the actual face point for the placement packet.
            var probe=point.add(-face.getStepX()*.001,-face.getStepY()*.001,-face.getStepZ()*.001);
            var trace=client.level.clip(new net.minecraft.world.level.ClipContext(client.player.getEyePosition(),probe,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
            if(trace.getType()==HitResult.Type.BLOCK && trace.getBlockPos().equals(support) && trace.getDirection()==face) {
                placement=new BlockHitResult(point,face,support,false);break;
            }
        }
        var aim=placement==null?net.minecraft.world.phys.Vec3.atCenterOf(pos):placement.getLocation();
        boolean inReach=client.player.getEyePosition().distanceTo(aim)<=4.5;
        if(entry.operation()==BuildLayout.Operation.PLACE) inReach=inReach&&placement!=null;
        else {
            var trace=client.level.clip(new net.minecraft.world.level.ClipContext(client.player.getEyePosition(),aim,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
            inReach=inReach&&trace.getType()==HitResult.Type.BLOCK&&trace.getBlockPos().equals(pos);
        }
        boolean aligned=client.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                && (entry.operation()==BuildLayout.Operation.DESTROY?hit.getBlockPos().equals(pos):placement!=null&&hit.getBlockPos().equals(placement.getBlockPos())&&hit.getDirection()==placement.getDirection());
        // Sneak only after aiming: holding it throughout an airborne approach would descend away
        // from the placement face. Require the observed key state before use, not the crouch pose.
        boolean placementAimReady=entry.operation()==BuildLayout.Operation.PLACE&&aligned;
        if(entry.operation()==BuildLayout.Operation.PLACE) aligned=aligned&&client.player.isShiftKeyDown();
        var action=runtime.tick(layout,new BuildRuntime.Facts(client.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),inReach,slot>=0,slot==services.inventory().selectedSlot(),aligned,System.currentTimeMillis()));
        if(action==BuildRuntime.Action.STOP) { fail(runtime.status());return; }
        if(action==BuildRuntime.Action.COMPLETE) { setEnabled(false);return; }
        if(action==BuildRuntime.Action.MOVE) {
            if(!walking) {
                boolean found=false;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dy:new int[]{0,-1,1}) {
                    if(!found&&navigation.startApproach(entry.pos().add(d[0],dy,d[1]),dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL)) found=true;
                }
                if(!found) { fail("No permitted approach to "+entry.pos()+": "+navigation.status());return; }walking=true;
            }
            var nav=navigation.tick();
            if(nav==McTravelNavigator.State.FAILED) fail("Approach blocked: "+navigation.status());
            if(nav==McTravelNavigator.State.ARRIVED) walking=false;
            return;
        }
        navigation.stop();walking=false;
        if(placementAimReady) services.input().setSneakHeld(true);
        if(action==BuildRuntime.Action.SELECT) { services.inventory().selectSlot(slot);return; }
        var delta=aim.subtract(client.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        interactionRotation.track(yaw,pitch);
        if(action==BuildRuntime.Action.BREAK) services.breaking().setBreaking(true);
        if(action==BuildRuntime.Action.PLACE&&placement!=null&&client.gameMode!=null) {
            client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,placement);client.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }
    private void fail(String reason) { failure=reason;setEnabled(false);dev.veltraph.client.log.VeltraphLog.basic("Builder",reason); }
    @Override public String diagnostics() { return failure.isEmpty()?(generated!=null?generated.status():setup!=null?setup.status():runtime.status()+" | "+preview()):failure; }
}
