package dev.veltraph.client.builder;

import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.Predicate;

/** Source-graph owner feeding existing native BuilderModule and BuildSetupExecutor adapters. */
final class GeneratedBuildExecutor {
    private final Minecraft client;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final BuilderModule facade;
    private McSource95Acquisition acquisition;
    private final List<dev.veltraph.client.metrics.FinancialActionReceipt> acquisitionReceipts=new ArrayList<>();
    List<dev.veltraph.client.metrics.FinancialActionReceipt> acquisitionReceipts(){return List.copyOf(acquisitionReceipts);}
    void onServerMessage(String line){if(acquisition!=null)acquisition.message(line);}
    private final FarmServices services;
    private final ConfigService config;
    private final GeneratedBuildPlan plan;
    private final GeneratedBuildRuntime runtime=new GeneratedBuildRuntime();
    private final GeneratedInventoryRestock restock;
    private final McTravelNavigator navigation;
    private BuilderModule operation;
    private GeneratedUseRuntime use;
    private Source95ToolRuntime nativeTool;
    private Map<String,Object> toolDiagnostics=Map.of();
    private BlockPos toolGoal;
    private List<Source95ToolApproach.Stance> toolCandidates=List.of();
    Map<String,Object> toolDiagnostics(){if(toolDiagnostics.isEmpty())return toolDiagnostics;var snapshot=new LinkedHashMap<>(toolDiagnostics);snapshot.put("liveNavigatorStatus",navigation.status());snapshot.put("liveNavigatorDiagnostics",navigation.diagnostics());snapshot.put("liveWalking",walking);if(client.player!=null){snapshot.put("livePose",client.player.position().toString());snapshot.put("liveEye",client.player.getEyePosition().toString());snapshot.put("liveOnGround",client.player.onGround());snapshot.put("liveSlot",services.inventory().selectedSlot());}return Collections.unmodifiableMap(snapshot);}
    private boolean walking;
    private long restockStarted;
    private boolean pendingConfirmation;
    private long confirmationStarted;
    private Object owner,world;
    private Object connection;
    private GeneratedResourceRuntime resource;
    private GeneratedMenuFlow menuFlow;
    private boolean acquiring;
    private ItemStack ownedCursor=ItemStack.EMPTY;
    private Object cursorMenu;
    private int cursorReturnSlot=-1;
    private Object resourceMenu;
    private McSourceResourcePolicy sourceResource;
    private String sourceFlow="";
    private final dev.veltraph.client.gui.McMenuAccess menus;
    GeneratedBuildExecutor(Minecraft c,FarmServices s,ConfigService config,GeneratedBuildPlan plan,BuilderModule facade){this.facade=facade;interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(c,s,()->facade);client=c;services=s;this.config=config;this.plan=plan;restock=new GeneratedInventoryRestock(c);navigation=new McTravelNavigator(c,s,config);menus=new dev.veltraph.client.gui.McMenuAccess(c);}
    void resume(){owner=client.player;world=client.level;connection=client.getConnection();runtime.resume();operation=null;use=null;nativeTool=null;toolGoal=null;toolCandidates=List.of();walking=false;pendingConfirmation=false;services.breaking().installTargetGuard(this,p->false);}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        if(!client.isSameThread()||owner==null||connection==null||client.player!=owner||client.level!=world||client.getConnection()!=connection)return false;
        if(operation!=null&&operation.isEnabled())return operation.consumeExpectedTeleport(position,now);
        return walking&&navigation.consumeExpectedTeleport(position,now);
    }
    SourceRecoveryTrace sourceRecoveryTrace(){return sourceResource==null?null:sourceResource.recoveryTrace();}
    void stop(){if(acquisition!=null)acquisition.cancel();BuilderToolOutputReceipts.detach(connection,this);nativeTool=null;if(operation!=null)operation.setEnabled(false);if(sourceResource!=null)sourceResource.stop();sourceResource=null;sourceFlow="";operation=null;use=null;resource=null;resourceMenu=null;menuFlow=null;acquiring=false;restoreCursor();menus.clear();navigation.stop();restock.release();services.breaking().clearTargetGuard(this);services.input().releaseAllMovement();services.breaking().setBreaking(false);interactionRotation.cancelRotation();walking=false;}
    String preview(){return plan.name()+": "+plan.processes().size()+" subprocesses; materials "+plan.requiredMaterials()+"; unresolved contracts "+plan.unresolvedContracts().size()+"; checkpoint "+runtime.process()+":"+runtime.cell()+". Fixed anchor.";}
    String status(){return runtime.status()+" | "+preview();}
    private GeneratedBuildRuntime.State blocked(String why){runtime.state(GeneratedBuildRuntime.State.BLOCKED,why);stop();return runtime.state();}
    GeneratedBuildRuntime.State tick() {
        if(runtime.complete(plan))return GeneratedBuildRuntime.State.COMPLETE;
        var process=plan.processes().get(runtime.process());var pos=process.cells().get(runtime.cell());
        boolean resourceStage=process.operation()==GeneratedBuildPlan.Operation.RESOURCE_SETUP,menuStage=process.operation()==GeneratedBuildPlan.Operation.MENU_FLOW||process.operation()==GeneratedBuildPlan.Operation.PURCHASE;
        if(client.player==null||!client.player.isAlive()||client.level==null||client.player!=owner||client.level!=world||connection==null||client.getConnection()!=connection||client.gameMode==null||!client.player.containerMenu.getCarried().isEmpty()&&(!(resourceStage||menuStage)||!ownsCursor()&&(sourceResource==null||!sourceResource.ownsCursor())&&(acquisition==null||!acquisition.ownsCursor()))||!resourceStage&&!menuStage&&(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu))return blocked("World/player/menu/cursor context changed; checkpoint retained");
        var unresolved=process.unresolved();if(unresolved.isPresent())return blocked(process.source()+": "+unresolved.get());
        if(process.operation()==GeneratedBuildPlan.Operation.PURCHASE)return acquisition(process);
        if(resourceStage)return resource(process);
        if(menuStage)return menuFlow(false);
        if(process.operation()==GeneratedBuildPlan.Operation.TOOL_USE)return nativeTool(process,pos);
        var target=new net.minecraft.core.BlockPos(pos.x(),pos.y(),pos.z());
        if(!client.level.getChunkSource().hasChunk(target.getX()>>4,target.getZ()>>4))return blocked("Target chunk unloaded");
        var state=client.level.getBlockState(target);if(state.hasBlockEntity())return blocked("Target contains inventory/data; refused");
        if(restock.pending()) {
            if(!restock.ready())return System.currentTimeMillis()-restockStarted>3000?blocked("Inventory restock correction not observed"):GeneratedBuildRuntime.State.RESTOCK;
            restock.keepTransfer();runtime.state(GeneratedBuildRuntime.State.READY,"Owned inventory stack observed in hotbar");return runtime.state();
        }
        Predicate<ItemStack> matches=stack->!stack.isEmpty()&&BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(process.item());
        if(process.operation()==GeneratedBuildPlan.Operation.BREAK)matches=stack->!stack.isEmpty()&&stack.isCorrectToolForDrops(state);
        boolean complete=process.operation()==GeneratedBuildPlan.Operation.BREAK?(state.isAir()||state.is(Blocks.WATER)):
                process.operation()!=GeneratedBuildPlan.Operation.RESTOCK&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(process.after())&&(process.operation()!=GeneratedBuildPlan.Operation.WATER||state.getFluidState().isSource());
        if(pendingConfirmation) {
            var pending=services.breaking().pendingBlockChanges(this);
            if(pending.isEmpty())return blocked("Native world acknowledgement observation unavailable; checkpoint retained");
            if(pending.get().contains(pos))return System.currentTimeMillis()-confirmationStarted>3000?blocked("World prediction remained pending; checkpoint retained"):GeneratedBuildRuntime.State.AWAIT_WORLD;
            pendingConfirmation=false;
            if(!complete)return blocked("World correction rejected the completed operation; checkpoint retained");
            runtime.confirmed(plan);return runtime.state();
        }
        boolean lever=process.operation()==GeneratedBuildPlan.Operation.LEVER;
        int slot=findHotbar(matches);
        if(lever)for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).isEmpty()){slot=i;break;}
        if(!complete&&slot<0) {
            if(process.operation()==GeneratedBuildPlan.Operation.BREAK&&!state.requiresCorrectToolForDrops()) {
                for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).isEmpty()){slot=i;break;}
            }
            if(slot<0) {
                if(restock.begin(matches,config.get(GeneratedBuilderConfig.RESTOCK_SLOT))){restockStarted=System.currentTimeMillis();runtime.state(GeneratedBuildRuntime.State.RESTOCK,"Transfer owned material/tool for "+process.source());return runtime.state();}
                return blocked("Material/tool missing or no empty hotbar slot for "+process.source()+"; checkpoint retained");
            }
        }
        if(process.operation()==GeneratedBuildPlan.Operation.RESTOCK){runtime.confirmed(plan);return runtime.state();}
        if(process.operation()==GeneratedBuildPlan.Operation.TILL||process.operation()==GeneratedBuildPlan.Operation.PLANT||lever)return use(process,pos,target,slot);
        if(process.requireSupport()&&(client.level.getBlockState(target.below()).getCollisionShape(client.level,target.below()).isEmpty()||client.level.getBlockState(target.below()).hasBlockEntity()))return blocked("Source placement predicate requires a solid, data-free block below");
        if(operation==null) {
            if(slot>=0)services.inventory().selectSlot(slot);
            operation=new BuilderModule(client,services,config,()->facade);
            if(process.operation()==GeneratedBuildPlan.Operation.PLACE)operation.loadDirected(new BuildLayout(process.source(),List.of(new BuildLayout.Entry(BuildLayout.Operation.PLACE,pos,process.after()))),process.direction());
            else operation.loadSetup(new BuildSetupPlan(process.source(),List.of(new BuildSetupPlan.Step(process.source(),process.operation()==GeneratedBuildPlan.Operation.WATER?BuildSetupPlan.Operation.WATER:BuildSetupPlan.Operation.CLEAR,pos,process.operation()==GeneratedBuildPlan.Operation.WATER?"minecraft:water":process.before()))));
            operation.setEnabled(true);
        }
        operation.onTick();
        if(!operation.isEnabled()) {
            if(operation.diagnostics().startsWith("Confirmed 1/1")) {operation=null;services.breaking().installTargetGuard(this,p->false);pendingConfirmation=true;confirmationStarted=System.currentTimeMillis();runtime.state(GeneratedBuildRuntime.State.AWAIT_WORLD,"Await native prediction settlement for "+process.source());return runtime.state();}
            return blocked(operation.diagnostics());
        }
        runtime.state(GeneratedBuildRuntime.State.EXECUTE,process.source()+" | "+operation.diagnostics());return runtime.state();
    }
    private GeneratedBuildRuntime.State nativeTool(GeneratedBuildPlan.Process process,BlockPos pos) {
        services.breaking().setBreaking(false);
        Source95ToolBinding binding;try{binding=Source95ToolBinding.decode(process.contract());}catch(RuntimeException invalid){return blocked(invalid.getMessage());}
        var outputs=binding.at(pos,process.direction());
        var target=new net.minecraft.core.BlockPos(pos.x(),pos.y(),pos.z());
        boolean valid=true,initial=true,complete=true;
        for(var output:outputs) {
            var point=output.offset();if(point.x()<-240||point.x()>239||point.z()<-240||point.z()>239||point.y()<0||point.y()>255)return blocked("Declared tool output outside Garden bounds");
            var cell=new net.minecraft.core.BlockPos(point.x(),point.y(),point.z());
            if(!client.level.getChunkSource().hasChunk(cell.getX()>>4,cell.getZ()>>4))return blocked("Declared native tool output chunk unloaded");
            var state=client.level.getBlockState(cell);valid&=!state.hasBlockEntity();var id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();initial&=id.equals(output.before());complete&=id.equals(output.after());
        }
        if(!client.level.getChunkSource().hasChunk(target.getX()>>4,target.getZ()>>4))return blocked("Tool anchor chunk unloaded");
        var state=client.level.getBlockState(target);valid&=!state.hasBlockEntity();initial&=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(binding.targetBlock());
        var pending=services.breaking().pendingBlockChanges(this);if(pending.isEmpty())return blocked("Native prediction observation unavailable for tool output");
        boolean settled=outputs.stream().noneMatch(o->pending.get().contains(o.offset()));
        Predicate<ItemStack> matches=stack->!stack.isEmpty()&&BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.toolItem())&&dev.veltraph.client.gui.MenuSnapshot.clean(stack.getHoverName().getString()).equals(binding.toolName());
        ItemStack identity=ItemStack.EMPTY;for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(matches.test(stack)){if(!identity.isEmpty()&&!ItemStack.isSameItemSameComponents(identity,stack))return blocked("Ambiguous named tool variants; checkpoint retained");identity=stack;}}
        int slot=findHotbar(matches);
        if(restock.pending()){if(!restock.ready())return System.currentTimeMillis()-restockStarted>3000?blocked("Named tool restock not acknowledged"):GeneratedBuildRuntime.State.RESTOCK;restock.keepTransfer();return GeneratedBuildRuntime.State.READY;}
        if(slot<0&&(nativeTool==null||!nativeTool.issued())&&restock.begin(matches,config.get(GeneratedBuilderConfig.RESTOCK_SLOT))){restockStarted=System.currentTimeMillis();return GeneratedBuildRuntime.State.RESTOCK;}
        if(nativeTool==null)nativeTool=new Source95ToolRuntime();
        if(walking){var progress=navigation.tick();if(progress==McTravelNavigator.State.FAILED)return blocked(navigation.status());if(progress==McTravelNavigator.State.ACTIVE)return runtime.state();walking=false;}
        var face=process.direction().x()<0?Direction.WEST:process.direction().x()>0?Direction.EAST:process.direction().z()<0?Direction.NORTH:Direction.SOUTH;
        var shape=state.getShape(client.level,target);if(shape.isEmpty()&&!nativeTool.issued())return blocked("Tool anchor has no outline");
        var aim=Vec3.atCenterOf(target).add(process.direction().x()*.499,0,process.direction().z()*.499);
        var hit=client.level.clip(new ClipContext(client.player.getEyePosition(),aim.subtract(process.direction().x()*.01,0,process.direction().z()*.01),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
        boolean reachable=client.player.getEyePosition().distanceTo(aim)<=4.5&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(target)&&hit.getDirection()==face;
        boolean aligned=client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(target)&&h.getDirection()==face;
        var action=nativeTool.tick(binding,new Source95ToolRuntime.Facts(valid,initial,complete,BuilderToolOutputReceipts.matches(connection,this,outputs),settled,slot>=0,slot==services.inventory().selectedSlot(),reachable,aligned,client.player.isShiftKeyDown(),System.currentTimeMillis()));
        toolDiagnostics=toolDiagnosticFrame(process,pos,slot,valid,initial,complete,settled,reachable,aligned,hit,aim,action.name(),null);
        if(action==Source95ToolRuntime.Action.STOP)return blocked(nativeTool.status());
        if(action==Source95ToolRuntime.Action.COMPLETE){BuilderToolOutputReceipts.detach(connection,this);nativeTool=null;toolGoal=null;toolCandidates=List.of();navigation.stop();services.input().releaseAllMovement();interactionRotation.cancelRotation();runtime.confirmed(plan);return runtime.state();}
        if(action==Source95ToolRuntime.Action.MOVE){
            if(config.get(TravelConfig.TRAVEL_APPROACH_MODE)==dev.veltraph.client.navigation.ApproachMode.WALK_ONLY&&!client.player.onGround()){runtime.state(GeneratedBuildRuntime.State.APPROACH,"Await actual grounded source tool face approach");return runtime.state();}
            var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config);
            toolCandidates=Source95ToolApproach.candidates(pos,process.direction(),client.player.getEyeHeight(),new Source95ToolApproach.Terrain(){
                public Optional<dev.veltraph.client.world.Vec3> supportedFeet(BlockPos node){if(!terrain.standable(node))return Optional.empty();var feet=terrain.position(node);return terrain.standingClear(feet)?Optional.of(feet):Optional.empty();}
                public boolean requiredFace(dev.veltraph.client.world.Vec3 eye,dev.veltraph.client.world.Vec3 point,BlockPos anchor,BlockPos direction){var from=new Vec3(eye.x(),eye.y(),eye.z());var probe=new Vec3(point.x()-direction.x()*.01,point.y(),point.z()-direction.z()*.01);var ray=client.level.clip(new ClipContext(from,probe,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));return ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(target)&&ray.getDirection()==face;}
            });
            var closest=Source95ToolApproach.closest(toolCandidates,new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));
            if(closest.isPresent()){toolGoal=closest.get().node();boolean started=navigation.startApproach(toolGoal,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL);toolDiagnostics=toolDiagnosticFrame(process,pos,slot,valid,initial,complete,settled,reachable,aligned,hit,aim,action.name(),started);if(started){walking=true;runtime.state(GeneratedBuildRuntime.State.APPROACH,process.source());return runtime.state();}}
            return blocked("No permitted source tool face approach");
        }
        navigation.stop();walking=false;
        if(action==Source95ToolRuntime.Action.SELECT)services.inventory().selectSlot(slot);
        if(action==Source95ToolRuntime.Action.SHIFT)services.input().setSneakHeld(true);
        var delta=aim.subtract(client.player.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(action==Source95ToolRuntime.Action.USE){BuilderToolOutputReceipts.attach(connection,this,outputs.stream().map(Source95ToolBinding.Output::offset).collect(java.util.stream.Collectors.toSet()));client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(aim,face,target,false));services.input().setSneakHeld(false);}
        runtime.state(GeneratedBuildRuntime.State.AWAIT_WORLD,process.source()+" | "+nativeTool.status());return runtime.state();
    }
    private Map<String,Object> toolDiagnosticFrame(GeneratedBuildPlan.Process process,BlockPos pos,int slot,boolean valid,boolean initial,boolean complete,boolean settled,boolean reachable,boolean aligned,BlockHitResult hit,Vec3 aim,String action,Boolean started) {
        var out=new LinkedHashMap<String,Object>();var goal=toolGoal==null?pos.add(process.direction().x()*2,1,process.direction().z()*2):toolGoal;out.put("supportedFaceCandidates",toolCandidates);
        var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config);var point=terrain.position(goal);
        out.put("process",runtime.process());out.put("cell",runtime.cell());out.put("source",process.source());out.put("anchor",pos);out.put("sourceDirection",process.direction());
        out.put("samePlayer",client.player==owner);out.put("sameWorld",client.level==world);out.put("sameConnection",client.getConnection()==connection);out.put("gameMode",client.gameMode.getPlayerMode().toString());
        out.put("pose",client.player.position().toString());out.put("eye",client.player.getEyePosition().toString());out.put("yaw",client.player.getYRot());out.put("pitch",client.player.getXRot());out.put("onGround",client.player.onGround());out.put("mayfly",client.player.getAbilities().mayfly);out.put("shifting",client.player.isShiftKeyDown());
        out.put("requestedGoal",goal);out.put("requestedFeet",point);out.put("goalStandable",terrain.standable(goal));out.put("goalStandingClear",terrain.standingClear(point));out.put("goalBodyClear",terrain.bodyClear(point,false));
        var support=new net.minecraft.core.BlockPos(goal.x(),goal.y()-1,goal.z());out.put("goalSupportBlock",BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(support).getBlock()).toString());out.put("goalChunkLoaded",client.level.getChunkSource().hasChunk(goal.x()>>4,goal.z()>>4));
        out.put("nativeStartResult",started==null?"not requested":started);out.put("sourceNavigatorStatus",navigation.status());out.put("sourceNavigatorDiagnostics",navigation.diagnostics());out.put("walking",walking);
        out.put("aim",aim.toString());out.put("eyeAimDistance",client.player.getEyePosition().distanceTo(aim));out.put("rayType",hit.getType().name());out.put("rayBlock",hit.getBlockPos().toString());out.put("rayFace",hit.getDirection().name());out.put("nativeHit",String.valueOf(client.hitResult));
        out.put("reachable",reachable);out.put("aligned",aligned);out.put("valid",valid);out.put("initial",initial);out.put("outputComplete",complete);out.put("predictionSettled",settled);out.put("toolSlot",slot);out.put("selectedSlot",services.inventory().selectedSlot());out.put("restockPending",restock.pending());out.put("toolIssued",nativeTool.issued());out.put("action",action);
        out.put("toolOutputReceipt",BuilderToolOutputReceipts.snapshot(connection,this));
        var blocks=new LinkedHashMap<String,String>();for(int x=0;x<=2;x++)for(int y=0;y<=2;y++){var p=new net.minecraft.core.BlockPos(pos.x()+x,pos.y()+y,pos.z());blocks.put(p.toString(),BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(p).getBlock()).toString());}out.put("anchorAndOriginalClearance",Map.copyOf(blocks));
        return Collections.unmodifiableMap(out);
    }
    private GeneratedBuildRuntime.State acquisition(GeneratedBuildPlan.Process process){
        services.breaking().setBreaking(false);services.input().releaseAllMovement();interactionRotation.cancelRotation();
        try{if(acquisition==null)acquisition=new McSource95Acquisition(client,facade,config,Source95AcquisitionBinding.decode(process.contract(),Source95CropResource.crop(process.item())),services.clock());var result=acquisition.tick();for(var receipt:acquisition.receipts())if(acquisitionReceipts.stream().noneMatch(old->old.id().equals(receipt.id())))acquisitionReceipts.add(receipt);if(result==McSource95Acquisition.Result.BLOCKED)return blocked(acquisition.status());if(result==McSource95Acquisition.Result.COMPLETE){if(!acquisition.authorizedCompletion())return blocked("Source128 completion authorization revoked; checkpoint retained");acquisition=null;runtime.confirmed(plan);return runtime.state();}runtime.state(GeneratedBuildRuntime.State.RESTOCK,acquisition.status());return runtime.state();}catch(RuntimeException invalid){return blocked("Source95 acquisition authority: "+invalid.getMessage());}
    }
    private int findHotbar(Predicate<ItemStack> match){for(int i=0;i<9;i++)if(match.test(client.player.getInventory().getItem(i)))return i;return -1;}
    private GeneratedBuildRuntime.State menuFlow(boolean forAcquisition) {
        return menuFlow(forAcquisition,null,false);
    }
    private GeneratedBuildRuntime.State menuFlow(boolean forAcquisition,String provided,boolean sourcePolicyFlow) {
        services.breaking().setBreaking(false);services.input().releaseAllMovement();
        if(menuFlow==null)try{menuFlow=GeneratedMenuFlow.decode(provided==null?config.get(forAcquisition?GeneratedBuilderConfig.SOURCE95_ACQUISITION_FLOW:GeneratedBuilderConfig.SOURCE95_SELL_FLOW):provided);}catch(RuntimeException invalid){return blocked(invalid.getMessage());}
        String receipt=menuFlow.receiptItem();int count=0;
        for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(!stack.isEmpty()&&BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(receipt))count+=stack.getCount();}
        var snapshot=menus.read();if(snapshot==null&&client.gui.screen()!=null)return blocked("Source menu workflow interrupted by another screen");
        var carried=client.player.containerMenu.getCarried();var step=menuFlow.current();
        String cursorId=carried.isEmpty()?"":BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        var decision=menuFlow.tick(snapshot,count,cursorId,carried.getCount(),System.currentTimeMillis());
        if(decision.action()==GeneratedMenuFlow.Action.STOP)return blocked(decision.reason());
        if(decision.action()==GeneratedMenuFlow.Action.OPEN_INVENTORY)client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
        if(decision.action()==GeneratedMenuFlow.Action.OPEN_BAZAAR){var port=new dev.veltraph.client.inventory.equipment.McEquipmentWorkflow(client);if(!port.command(port.read(),"bazaar"))return blocked("Explicit source Bazaar route refused stale inventory/context");}
        if(decision.action()==GeneratedMenuFlow.Action.CLOSE&&(snapshot==null||!menus.close(snapshot.id())))return blocked("Bound source menu close refused");
        if(decision.action()==GeneratedMenuFlow.Action.CLICK) {
            var fresh=menus.read();
            if(fresh==null||snapshot==null||fresh.id()!=snapshot.id()||fresh.revision()!=snapshot.revision()||!fresh.title().equals(snapshot.title())||!fresh.slots().equals(snapshot.slots()))return blocked("Source menu snapshot changed before click");
            if((step.playerSlot()||step.sourceState().equals("PUT_IN_CRAFTING_TABLE")||step.sourceState().equals("CRAFT_ITEM"))&&(client.player.containerMenu!=client.player.inventoryMenu||!(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)))return blocked("Source dissolution requires the actual vanilla inventory/crafting menu");
            if(!carried.isEmpty()&&!ownsCursor())return blocked("Source menu action refused unowned cursor");
            var nativeSlot=client.player.containerMenu.getSlot(decision.slot());
            if(carried.isEmpty()) {
                ownedCursor=nativeSlot.getItem().copy();cursorMenu=client.player.containerMenu;cursorReturnSlot=decision.slot();
                if(step.sourceState().equals("CRAFT_ITEM"))cursorReturnSlot=client.player.containerMenu.slots.stream().filter(s->s.container==client.player.getInventory()&&s.getItem().isEmpty()).mapToInt(s->s.index).findFirst().orElse(-1);
            }else if(!nativeSlot.getItem().isEmpty())return blocked("Source crafting/cursor destination occupied; refused");
            client.gameMode.handleContainerInput(snapshot.id(),decision.slot(),0,net.minecraft.world.inventory.ContainerInput.PICKUP,client.player);
        }
        if(decision.action()==GeneratedMenuFlow.Action.COMPLETE){if(!carried.isEmpty())return blocked("Source flow receipt arrived with carried item; retained safely");ownedCursor=ItemStack.EMPTY;cursorMenu=null;cursorReturnSlot=-1;menuFlow=null;if(sourcePolicyFlow)return GeneratedBuildRuntime.State.COMPLETE;if(forAcquisition){acquiring=false;resource=new GeneratedResourceRuntime();resourceMenu=null;if(snapshot!=null)resource.inheritObservedMenuReceipt();return GeneratedBuildRuntime.State.RESTOCK;}runtime.confirmed(plan);return runtime.state();}
        runtime.state(GeneratedBuildRuntime.State.RESTOCK,decision.reason());return runtime.state();
    }
    private boolean ownsCursor(){return client.player!=null&&client.player==owner&&client.level==world&&cursorMenu==client.player.containerMenu&&!ownedCursor.isEmpty()&&ItemStack.matches(ownedCursor,client.player.containerMenu.getCarried());}
    private void restoreCursor(){
        if(ownsCursor()&&client.gameMode!=null&&client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen&&screen.getMenu()==client.player.containerMenu&&cursorReturnSlot>=0&&cursorReturnSlot<client.player.containerMenu.slots.size()) {
            var slot=client.player.containerMenu.getSlot(cursorReturnSlot);
            if(slot.getItem().isEmpty()&&slot.mayPlace(ownedCursor))client.gameMode.handleContainerInput(client.player.containerMenu.containerId,cursorReturnSlot,0,net.minecraft.world.inventory.ContainerInput.PICKUP,client.player);
        }
        ownedCursor=ItemStack.EMPTY;cursorMenu=null;cursorReturnSlot=-1;
    }
    private GeneratedBuildRuntime.State resource(GeneratedBuildPlan.Process process) {
        services.breaking().setBreaking(false);services.input().releaseAllMovement();
        if(acquiring)return menuFlow(true);
        var key=process.source().startsWith("source95 group4")?GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING:GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING;
        GeneratedResourceBinding binding;try{binding=GeneratedResourceBinding.decode(config.get(key));}catch(RuntimeException invalid){return blocked("Resource contract: "+invalid.getMessage());}
        int required=Integer.parseInt(process.source().substring(process.source().lastIndexOf(' ')+1));
        try {
            var policy=SourceResourceProfile.decode(config.get(key));
            if(policy.isPresent()) {
                if(sourceResource==null)sourceResource=new McSourceResourcePolicy(client,services,config,binding,policy.get(),required,provided->{
                    if(!sourceFlow.isEmpty()&&!sourceFlow.equals(provided))return -1;sourceFlow=provided;
                    var state=menuFlow(false,provided,true);
                    if(state==GeneratedBuildRuntime.State.COMPLETE){sourceFlow="";return 1;}
                    return state==GeneratedBuildRuntime.State.BLOCKED?-1:0;
                },()->facade);
                var controller=sourceResource;var state=controller.tick();
                if(state==McSourceResourcePolicy.Result.BLOCKED)return blocked(controller.status());
                if(state==McSourceResourcePolicy.Result.COMPLETE){sourceResource=null;runtime.confirmed(plan);return runtime.state();}
                runtime.state(GeneratedBuildRuntime.State.RESTOCK,sourceResource.status());return runtime.state();
            }
        }catch(RuntimeException invalid){return blocked("Source helper policy: "+invalid.getMessage());}
        Predicate<ItemStack> toolMatch=s->!s.isEmpty()&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(binding.toolItem())&&(binding.toolName().isBlank()||dev.veltraph.client.gui.MenuSnapshot.clean(s.getHoverName().getString()).equals(binding.toolName()));
        ItemStack toolIdentity=ItemStack.EMPTY;
        for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(toolMatch.test(stack)){if(!toolIdentity.isEmpty()&&!ItemStack.isSameItemSameComponents(toolIdentity,stack))return blocked("Ambiguous source tool variants; bind exact toolName");toolIdentity=stack;}}
        var menu=menus.read();int slot=findHotbar(toolMatch);
        if(menu==null&&slot<0) {
            if(restock.pending()){if(restock.ready()){restock.keepTransfer();return GeneratedBuildRuntime.State.READY;}else return System.currentTimeMillis()-restockStarted>3000?blocked("Tool restock not observed"):GeneratedBuildRuntime.State.RESTOCK;}
            if(restock.begin(toolMatch,config.get(GeneratedBuilderConfig.RESTOCK_SLOT))){restockStarted=System.currentTimeMillis();return GeneratedBuildRuntime.State.RESTOCK;}
            return blocked("Bound source tool missing or hotbar full");
        }
        if(resource==null)resource=new GeneratedResourceRuntime();
        if(resourceMenu!=null&&menu!=null&&client.player.containerMenu!=resourceMenu)return blocked("Source tool menu identity changed; refused same-title replacement");
        if(resourceMenu!=null&&menu==null&&resource.state()!=GeneratedResourceRuntime.State.CLOSE_GUI)return blocked("Source tool menu closed before a material receipt");
        var sources=menu==null?List.<dev.veltraph.client.gui.MenuSnapshot.Slot>of():menu.slots().stream().filter(s->s.playerInventory()&&s.count()>0&&s.itemId().equals(binding.resourceItem())&&(binding.resourceName().isBlank()||s.name().equals(binding.resourceName()))).toList();
        if(sources.size()>1){var prototype=client.player.containerMenu.getSlot(sources.getFirst().id()).getItem();for(var s:sources)if(!ItemStack.isSameItemSameComponents(prototype,client.player.containerMenu.getSlot(s.id()).getItem()))return blocked("Ambiguous resource variants; bind exact resourceName");}
        var sourceSlot=sources.stream().findFirst();
        boolean away=!(client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK);
        int gauge=binding.storedCount(menu).orElse(-1);
        var action=resource.tick(required,new GeneratedResourceRuntime.Facts(slot>=0&&slot==services.inventory().selectedSlot(),away,client.gui.screen()!=null,menu!=null&&menu.title().equals(binding.menuTitle()),gauge,sourceSlot.isPresent(),menu==null?-1:menu.revision(),System.currentTimeMillis()));
        if(menu!=null&&action!=GeneratedResourceRuntime.Action.STOP)resourceMenu=client.player.containerMenu;
        if(action==GeneratedResourceRuntime.Action.STOP) {
            if(resource.state()==GeneratedResourceRuntime.State.BUY_RESOURCE&&!config.get(GeneratedBuilderConfig.SOURCE95_ACQUISITION_FLOW).isBlank()){acquiring=true;return menuFlow(true);}
            return blocked(resource.status());
        }
        if(action==GeneratedResourceRuntime.Action.COMPLETE){resource=null;resourceMenu=null;menus.clear();runtime.confirmed(plan);return runtime.state();}
        if(action==GeneratedResourceRuntime.Action.SELECT&&slot>=0)services.inventory().selectSlot(slot);
        if(action==GeneratedResourceRuntime.Action.AIM_AWAY)interactionRotation.track(client.player.getYRot(),-90);
        if(action==GeneratedResourceRuntime.Action.OPEN)client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
        if(action==GeneratedResourceRuntime.Action.CLOSE&&menu!=null&&!menus.close(menu.id()))return blocked("Tool menu close refused");
        if(action==GeneratedResourceRuntime.Action.TRANSFER) {
            var fresh=menus.read();var source=sourceSlot.orElseThrow();
            if(fresh==null||fresh.id()!=menu.id()||fresh.revision()!=menu.revision()||!fresh.title().equals(menu.title())||!fresh.slots().contains(source)||client.player.containerMenu!=((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)client.gui.screen()).getMenu())return blocked("Source tool inventory snapshot changed before transfer");
            client.gameMode.handleContainerInput(menu.id(),source.id(),0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,client.player);
        }
        runtime.state(GeneratedBuildRuntime.State.RESTOCK,resource.status());return runtime.state();
    }
    boolean allowsScreen(String title){if(client.player!=owner||client.level!=world)return false;if(acquisition!=null&&acquisition.allowsScreen(title))return true;if(menuFlow!=null&&menuFlow.allowsTitle(title))return true;if(sourceResource!=null)return sourceResource.allowsScreen(title);if(resource!=null){var stage=plan.processes().get(runtime.process());var key=stage.source().startsWith("source95 group4")?GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING:GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING;try{return GeneratedResourceBinding.decode(config.get(key)).menuTitle().equals(title);}catch(RuntimeException invalid){return false;}}return false;}
    private GeneratedBuildRuntime.State use(GeneratedBuildPlan.Process process,BlockPos pos,net.minecraft.core.BlockPos target,int slot) {
        services.breaking().setBreaking(false);
        if(use==null)use=new GeneratedUseRuntime();
        if(walking){var progress=navigation.tick();if(progress==McTravelNavigator.State.FAILED)return blocked(navigation.status());if(progress==McTravelNavigator.State.ACTIVE)return runtime.state();walking=false;}
        boolean till=process.operation()==GeneratedBuildPlan.Operation.TILL,lever=process.operation()==GeneratedBuildPlan.Operation.LEVER;
        var support=till||lever?target:target.below();var supportState=client.level.getBlockState(support);
        boolean supportReady=lever?supportState.is(Blocks.LEVER):till?client.level.getBlockState(target.above()).isAir():supportState.is(Blocks.FARMLAND);
        if(supportState.hasBlockEntity())return blocked("Use support contains data");
        if(slot>=0&&till&&!client.player.getInventory().getItem(slot).is(net.minecraft.tags.ItemTags.HOES))return blocked("Local till adapter requires a vanilla hoe");
        if(till&&(!process.after().equals("minecraft:farmland")||!Set.of("minecraft:dirt","minecraft:grass_block").contains(process.before())))return blocked("Unsupported local till contract");
        if(slot>=0&&!till&&!lever&&(!(client.player.getInventory().getItem(slot).getItem() instanceof BlockItem item)||!BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString().equals(process.after())))return blocked("Plant item does not match the configured observed block");
        var shape=supportState.getShape(client.level,support);if(shape.isEmpty())return blocked("Tilling/planting support outline is empty");
        var bounds=shape.bounds();var top=BuildSupportPoint.top(support.getX(),support.getY(),support.getZ(),bounds.minX,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ);
        var aim=lever?bounds.getCenter().add(support.getX(),support.getY(),support.getZ()):new Vec3(top.x(),top.y(),top.z());
        var hit=client.level.clip(new ClipContext(client.player.getEyePosition(),aim.add(0,-.001,0),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
        boolean reachable=client.player.getEyePosition().distanceTo(aim)<=4.5&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(support)&&(lever||hit.getDirection()==Direction.UP);
        boolean aligned=client.hitResult instanceof BlockHitResult h&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(support)&&(lever?h.getDirection()==hit.getDirection():h.getDirection()==Direction.UP);
        var state=client.level.getBlockState(target);
        var pending=services.breaking().pendingBlockChanges(this);
        if(pending.isEmpty())return blocked("Native world acknowledgement observation unavailable; checkpoint retained");
        boolean output=lever?state.is(Blocks.LEVER)&&state.getValue(net.minecraft.world.level.block.LeverBlock.POWERED):BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(process.after());
        var action=use.tick(process,new GeneratedUseRuntime.Facts(true,state.hasBlockEntity(),BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),!pending.get().contains(pos)&&!pending.get().contains(new BlockPos(support.getX(),support.getY(),support.getZ())),supportReady,slot>=0,slot==services.inventory().selectedSlot(),reachable,aligned,System.currentTimeMillis()),output);
        if(action==GeneratedUseRuntime.Action.COMPLETE){use=null;navigation.stop();runtime.confirmed(plan);return runtime.state();}
        if(action==GeneratedUseRuntime.Action.STOP)return blocked(use.status());
        if(action==GeneratedUseRuntime.Action.MOVE){
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(navigation.startApproach(pos.add(d[0],till?1:0,d[1]),dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL)){walking=true;runtime.state(GeneratedBuildRuntime.State.APPROACH,process.source());return runtime.state();}
            return blocked("No permitted approach for "+process.source());
        }
        navigation.stop();walking=false;
        if(action==GeneratedUseRuntime.Action.SELECT){services.inventory().selectSlot(slot);runtime.state(GeneratedBuildRuntime.State.SELECT_TOOL,process.source());return runtime.state();}
        var delta=aim.subtract(client.player.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(action==GeneratedUseRuntime.Action.USE)client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(aim,lever?hit.getDirection():Direction.UP,support,false));
        runtime.state(GeneratedBuildRuntime.State.AWAIT_WORLD,process.source()+" | "+use.status());return runtime.state();
    }
}
