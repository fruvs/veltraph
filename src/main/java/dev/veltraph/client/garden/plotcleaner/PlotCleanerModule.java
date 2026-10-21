package dev.veltraph.client.garden.plotcleaner;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.GardenTerminalView;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Native guarded desk transaction and explicit vanilla counterpart for the unrecovered bulk tool. */
public final class PlotCleanerModule extends AbstractModule {
    public static final String ID="plot_cleaner_workflow";
    public enum RowPhase { FLY_TO_CORNER1,ROTATE_DOWN,MINE_DOWN,FLY_TO_CORNER2,ROTATE_AROUND_ADJACENT,DELETE_ROW }
    private RowPhase rowPhase=RowPhase.FLY_TO_CORNER1;private BlockPos firstColumn;
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final McMenuAccess menus;private final McTravelNavigator navigator;
    private PlotCleanerPlan plan;private PlotCleanerBindings bindings;private PlotCleanerWorkflow flow;
    private ClientLevel world;private LocalPlayer owner;private Object connection,ownedMenu;
    private List<BlockPos> scan=List.of(),targets=List.of();private final Map<BlockPos,String> initial=new HashMap<>();
    private int scanIndex,targetIndex,originalSlot,toolSlot=-1,airTicks;private BlockPos target;private String expected;
    private boolean originalFlying,walking,returning,landing,restored,leaseHeld;private float originalYaw,originalPitch;
    private net.minecraft.core.BlockPos returnPad;private String returnPadState;
    private dev.veltraph.client.world.Vec3 originalPose;private ItemStack toolTemplate=ItemStack.EMPTY;
    private long deadline,clickAt;private boolean pendingClick;private ItemStack predictedCursor=ItemStack.EMPTY;private int rowRemoved,cleanupRemoved;private String last="Idle";
    private long timeout;private int budget;private double lift;
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
        if(!(client.player==owner&&client.level==world&&client.getConnection()==connection))return false;
        if(terminalView==null||terminalView.state()==GardenTerminalView.State.IDLE)
            return !McOwnedRotation.installed(client)||(McOwnedRotation.authority(client)==returnAuthority&&McOwnedRotation.epoch(client)==returnEpoch);
        // Issued queued rotation may publish its own new epoch. The helper proves that transition
        // with the exact service request receipt; fixed preissue epoch equality would reject it.
        return McOwnedRotation.authority(client)==returnAuthority&&interactionRotation.currentRequest();
    }
    public PlotCleanerModule(Minecraft client,FarmServices services,ConfigService config){
        super(ID,"Guarded plot cleaner",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,()->this);menus=new McMenuAccess(client);navigator=new McTravelNavigator(client,services,config);
    }
    /** The source cleaner uses explicit flight legs; no generic device substitution is admitted. */
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&leaseHeld&&walking&&client.isSameThread()&&owner!=null&&connection!=null
                &&client.player==owner&&client.level==world&&client.getConnection()==connection
                &&navigator.consumeExpectedTeleport(position,now);
    }
    public void request(PlotCleanerPlan p){if(isEnabled())throw new IllegalStateException("Cannot replace an active cleaner request");plan=Objects.requireNonNull(p);}
    public Optional<PlotCleanerPlan> preparedPlan(){return Optional.ofNullable(plan);}
    public void clearRequest(){if(isEnabled())throw new IllegalStateException("Cannot clear an active cleaner request");plan=null;}
    /** Read-only current-world validation for the explicit preparation command. No lease/input/menu changes. */
    public Optional<String> preparationCheck(PlotCleanerPlan p){
        if(isEnabled())return Optional.of("Stop the active cleaner before replacing its prepared bounds");
        if(client.level==null||client.player==null)return Optional.of("A loaded world is required to inspect the requested bounds");
        int limit=config.get(PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS);if(limit<1||p.volume()>limit)return Optional.of("Requested cells exceed the configured bounded scan budget");
        for(var cell:p.scanOrder()){
            if(!client.level.getChunkSource().hasChunk(cell.x()>>4,cell.z()>>4))return Optional.of("Requested chunk is unloaded: "+cell);
            var state=client.level.getBlockState(nativePos(cell));if(state.hasBlockEntity()||!state.isAir()&&!PlotCleanerPlan.natural(id(state)))return Optional.of("Requested bounds contain protected material: "+cell);
        }
        return Optional.empty();
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public Optional<String> preStartCheck(){
        try{
            if(plan==null)return Optional.of("Explicit plot/bounds request required");
            if(!config.get(PlotCleanerConfig.PLOT_CLEANER_ENABLED)||!config.get(PlotCleanerConfig.PLOT_CLEANER_BINDINGS_VERIFIED)||!config.get(PlotCleanerConfig.PLOT_CLEANER_VANILLA_ROWS))return Optional.of("Opt in, verify menu bindings and explicitly select the vanilla row counterpart");
            PlotCleanerBindings.from(config);
            if(client.player==null||client.level==null||client.gameMode==null||!client.player.isAlive()||!client.player.onGround()||!client.player.getAbilities().mayfly||client.gui.screen()!=null)return Optional.of("Live grounded Garden player, closed screen and existing flight permission required");
            var under=client.player.blockPosition().below();var state=client.level.getBlockState(under);
            if(!(state.is(net.minecraft.world.level.block.Blocks.DIRT)||state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.STONE)))return Optional.of("Source starting surface must be grass, dirt or stone");
            if(plan.contains(new BlockPos(under.getX(),under.getY(),under.getZ())))return Optional.of("Start on an unchanged return pad outside deletion bounds");
            if(!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Empty cursor required");
            if(config.get(PlotCleanerConfig.PLOT_CLEANER_TIMEOUT_MS)<1000||config.get(PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS)<1||plan.volume()>config.get(PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS))return Optional.of("Invalid cleaner deadline/cell budget");
            double l=config.get(PlotCleanerConfig.PLOT_CLEANER_AIR_LIFT);if(!Double.isFinite(l)||l<1||l>32)return Optional.of("Air lift must be finite in1..32");
            return findShovel()<0?Optional.of("Actual diamond shovel required; native bulk-row tool is not reconstructed"):Optional.empty();
        }catch(RuntimeException invalid){return Optional.of(invalid.getMessage()==null?"Invalid cleaner bindings":invalid.getMessage());}
    }
    private int findShovel(){if(client.player==null)return -1;for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).is(Items.DIAMOND_SHOVEL))return i;return -1;}
    private static ItemStack identity(ItemStack s){var c=s.copy();c.remove(DataComponents.DAMAGE);return c;}
    @Override public void onEnable(){
        terminalView=null;flow=null;owner=null;ownedMenu=null;restored=false;var invalid=preStartCheck();if(invalid.isPresent()){last=invalid.get();setEnabled(false);return;}
        owner=client.player;world=client.level;connection=client.getConnection();bindings=PlotCleanerBindings.from(config);flow=new PlotCleanerWorkflow(plan,bindings);
        timeout=config.get(PlotCleanerConfig.PLOT_CLEANER_TIMEOUT_MS);budget=config.get(PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS);lift=config.get(PlotCleanerConfig.PLOT_CLEANER_AIR_LIFT);deadline=now()+timeout;
        originalSlot=services.inventory().selectedSlot();originalFlying=owner.getAbilities().flying;originalYaw=owner.getYRot();originalPitch=owner.getXRot();originalPose=new dev.veltraph.client.world.Vec3(owner.getX(),owner.getY(),owner.getZ());
        returnPad=owner.blockPosition().below();returnPadState=world.getBlockState(returnPad).toString();
        toolSlot=findShovel();toolTemplate=identity(owner.getInventory().getItem(toolSlot));scan=plan.scanOrder();scanIndex=targetIndex=rowRemoved=cleanupRemoved=airTicks=0;initial.clear();target=null;walking=returning=landing=restored=false;ownedMenu=null;pendingClick=false;predictedCursor=ItemStack.EMPTY;
        leaseHeld=true;services.input().releaseAllMovement();services.breaking().setBreaking(false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);services.breaking().installTargetGuard(this,this::mayBreak);last="Scanning every requested cell before any menu mutation";
    }
    private long now(){return services.clock().nowMillis();}
    private net.minecraft.core.BlockPos nativePos(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BlockPos p){return world!=null&&world.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private String id(BlockState s){return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();}
    private boolean validScopeCell(BlockPos p){if(!loaded(p))return false;var s=world.getBlockState(nativePos(p));return !s.hasBlockEntity()&&(s.isAir()||PlotCleanerPlan.natural(id(s)));}
    private boolean mayBreak(BlockPos p){
        if(!isEnabled()||flow==null||!flow.active()||client.level!=world||client.player!=owner||owner==null||!owner.isAlive()||client.gui.screen()!=null||target==null||!p.equals(target)||!plan.contains(p)||!loaded(p))return false;
        var s=world.getBlockState(nativePos(p));return !s.hasBlockEntity()&&id(s).equals(expected)&&PlotCleanerPlan.natural(id(s))
                &&owner.getInventory().getSelectedSlot()==toolSlot&&ItemStack.isSameItemSameComponents(identity(owner.getMainHandItem()),toolTemplate);
    }
    @Override public boolean allowsScreen(String title){if(bindings==null||!isEnabled())return false;String t=MenuSnapshot.clean(title);return List.of(bindings.desk(),bindings.plots(),bindings.plotTitle(),bindings.confirmTitle()).stream().anyMatch(s->bindings.bind(s,plan).equals(t));}
    private void fail(String reason){last=reason;if(flow!=null)flow.fail(reason);setEnabled(false);}
    private boolean unchanged(){try{return config.get(PlotCleanerConfig.PLOT_CLEANER_ENABLED)&&config.get(PlotCleanerConfig.PLOT_CLEANER_BINDINGS_VERIFIED)&&config.get(PlotCleanerConfig.PLOT_CLEANER_VANILLA_ROWS)
                &&bindings.equals(PlotCleanerBindings.from(config))&&timeout==config.get(PlotCleanerConfig.PLOT_CLEANER_TIMEOUT_MS)&&budget==config.get(PlotCleanerConfig.PLOT_CLEANER_MAX_BLOCKS)&&Double.compare(lift,config.get(PlotCleanerConfig.PLOT_CLEANER_AIR_LIFT))==0;}catch(RuntimeException invalid){return false;}}
    @Override public void onTick(){
        if(!isEnabled())return;services.breaking().setBreaking(false);
        if(flow==null||client.player!=owner||client.level!=world||client.getConnection()!=connection||owner==null||!owner.isAlive()||!owner.getAbilities().mayfly||!unchanged()){fail("Cleaner ownership, world/death, flight permission or configuration changed");return;}
        if(!owner.getInventory().getItem(toolSlot).is(Items.DIAMOND_SHOVEL)||!ItemStack.isSameItemSameComponents(identity(owner.getInventory().getItem(toolSlot)),toolTemplate)){fail("Equipment changed before workflow completion");return;}
        if(now()>=deadline){fail("Cleaner deadline expired; explicit new run required");return;}
        if(!owner.containerMenu.getCarried().isEmpty()){
            var carried=owner.containerMenu.getCarried();
            if(pendingClick&&now()-clickAt<5000&&carried.getCount()==predictedCursor.getCount()&&ItemStack.isSameItemSameComponents(carried,predictedCursor)){
                services.input().releaseAllMovement();return; // Only our exact button prediction, no next click/mining before restitution.
            }
            fail("Cursor became occupied outside the owned button prediction");return;
        }
        pendingClick=false;
        if(client.gui.screen()!=null&&!allowsScreen(client.gui.screen().getTitle().getString())){fail("Unexpected screen; no confirmation or mining");return;}
        if(terminalView!=null){
            McOwnedRotation.validate(client);
            var state=terminalView.tick();
            if(state==GardenTerminalView.State.FAILED){fail("Return view custody changed");return;}
            if(state==GardenTerminalView.State.COMPLETE){flow.returned();last=flow.reason();setEnabled(false);}
            return;
        }
        var phase=flow.phase();
        if(phase==PlotCleanerWorkflow.Phase.PREFLIGHT){
            for(int i=0;i<256&&scanIndex<scan.size();i++){var p=scan.get(scanIndex++);if(!validScopeCell(p)){fail("Unloaded/protected/block-entity cell before confirmation: "+p);return;}initial.put(p,world.getBlockState(nativePos(p)).toString());}
            if(scanIndex==scan.size()){flow.preflightComplete();var goal=new dev.veltraph.client.world.Vec3(originalPose.x(),Math.max(originalPose.y()+lift,plan.maxY()+2.5),originalPose.z());if(!startFlight(goal))fail("No loaded safe source air approach: "+navigator.status());else walking=true;}return;
        }
        if(phase==PlotCleanerWorkflow.Phase.FLY_INTO_AIR){var st=navigator.tick();if(st==McTravelNavigator.State.FAILED){fail("Air approach failed: "+navigator.status()+" flying="+owner.getAbilities().flying+" permission="+owner.getAbilities().mayfly+" pose="+owner.position());return;}if(st==McTravelNavigator.State.ARRIVED){navigator.stop();walking=false;flow.arrivedInAir();}return;}
        if(flow.needDeskCommand()){services.input().releaseAllMovement();if(!new McServerCommandService(client).sendCommand(bindings.command())){fail("Desk command rejected");return;}flow.commandSent();}
        if(phase.ordinal()>=PlotCleanerWorkflow.Phase.OPEN_DESK.ordinal()&&phase.ordinal()<=PlotCleanerWorkflow.Phase.CONFIRM_SUPERFLAT.ordinal()){
            var menu=menus.read();if(menu!=null&&allowsScreen(menu.title()))ownedMenu=owner.containerMenu;var action=flow.menu(menu);
            if(!flow.active()){fail(flow.reason());return;}
            if(action.isPresent()){
                if(flow.phase()==PlotCleanerWorkflow.Phase.CONFIRM_SUPERFLAT){for(var p:scan)if(!validScopeCell(p)||!world.getBlockState(nativePos(p)).toString().equals(initial.get(p))){fail("Scope changed before destructive confirmation: "+p);return;}}
                ownedMenu=owner.containerMenu;predictedCursor=owner.containerMenu.getSlot(action.get().slot()).getItem().copy();if(!menus.click(action.get().menu(),action.get().slot())){fail("Stale/changed native menu click rejected");return;}pendingClick=true;clickAt=now();flow.clicked(action.get(),now(),scoreboardRows());
            }return;
        }
        if(phase==PlotCleanerWorkflow.Phase.WAIT){
            var rows=scoreboardRows();if(flow.progress(rows,client.gui.screen()==null,now())){navigator.stop();captureTargets(false);}return;
        }
        if(phase==PlotCleanerWorkflow.Phase.ROW_DELETE||phase==PlotCleanerWorkflow.Phase.BREAK_DIRT){mine();return;}
        if(phase==PlotCleanerWorkflow.Phase.RETURN){
            if(!world.getChunkSource().hasChunk(returnPad.getX()>>4,returnPad.getZ()>>4)||!world.getBlockState(returnPad).toString().equals(returnPadState)){fail("Original return pad changed or unloaded");return;}
            if(!returning){if(!startFlight(originalPose)){fail("Original pad no longer safely reachable");return;}returning=true;}
            if(!landing&&safeLandingColumn()){navigator.stop();owner.getAbilities().flying=false;owner.onUpdateAbilities();landing=true;}
            if(landing){services.input().releaseAllMovement();if(owner.onGround()&&owner.position().distanceTo(new Vec3(originalPose.x(),originalPose.y(),originalPose.z()))<.35){beginReturnView();}return;}
            var st=navigator.tick();if(st==McTravelNavigator.State.FAILED){fail("Physical return failed");return;}if(st==McTravelNavigator.State.ARRIVED){navigator.stop();if(!owner.onGround()||owner.position().distanceTo(new Vec3(originalPose.x(),originalPose.y(),originalPose.z()))>=.35){fail("Grounded physical return not proven");return;}beginReturnView();}return;
        }
    }
    private boolean startFlight(dev.veltraph.client.world.Vec3 point){if(!navigator.start(new LocalRoute.Node(LocalRoute.Kind.FLY,point,0,0)))return false;return navigator.tick()!=McTravelNavigator.State.FAILED;}
    private boolean safeLandingColumn(){
        double dy=owner.getY()-originalPose.y();var velocity=owner.getDeltaMovement();
        if(Math.hypot(owner.getX()-originalPose.x(),owner.getZ()-originalPose.z())>.25||dy<0||dy>1.5||Math.hypot(velocity.x,velocity.z)>.04)return false;
        var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input());
        var feet=new dev.veltraph.client.world.Vec3(owner.getX(),originalPose.y(),owner.getZ());if(!terrain.standingClear(feet))return false;
        for(double y=originalPose.y();y<owner.getY();y+=.125)if(!terrain.bodyClear(new dev.veltraph.client.world.Vec3(owner.getX(),y,owner.getZ()),false))return false;
        return terrain.bodyClear(new dev.veltraph.client.world.Vec3(owner.getX(),owner.getY(),owner.getZ()),false);
    }
    private List<String> scoreboardRows(){
        var board=world.getScoreboard();var objective=board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);if(objective==null)return List.of();
        var r=new ArrayList<String>();for(var s:board.listPlayerScores(objective))r.add(MenuSnapshot.clean(s.owner()));return List.copyOf(r);
    }
    private void captureTargets(boolean cleanup){
        var r=new ArrayList<BlockPos>();for(var p:scan){if(!validScopeCell(p)){fail("Unloaded/protected scope after flattening: "+p);return;}if(plan.cleanup(p)==cleanup&&PlotCleanerPlan.natural(id(world.getBlockState(nativePos(p)))))r.add(p);}
        r.sort(plan.rowOrder(owner.getX(),owner.getZ()));targets=List.copyOf(r);targetIndex=airTicks=0;target=null;if(!cleanup){firstColumn=targets.isEmpty()?null:targets.getFirst();rowPhase=RowPhase.FLY_TO_CORNER1;}
    }
    private Vec3 visibleFace(net.minecraft.core.BlockPos pos){
        Vec3 best=Vec3.atCenterOf(pos);double closest=Double.POSITIVE_INFINITY;
        for(var face:Direction.values()){var v=Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);var q=v.add(-face.getStepX()*.001,-face.getStepY()*.001,-face.getStepZ()*.001);
            var h=world.clip(new ClipContext(owner.getEyePosition(),q,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,owner));double distance=owner.getEyePosition().distanceTo(q);if(h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(pos)&&distance<closest){best=q;closest=distance;}}return best;
    }
    private boolean inMiningReach(BlockPos p){
        if(!loaded(p))return false;var state=world.getBlockState(nativePos(p));if(!PlotCleanerPlan.natural(id(state))||state.hasBlockEntity())return false;
        var aim=visibleFace(nativePos(p));var h=world.clip(new ClipContext(owner.getEyePosition(),aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,owner));
        return owner.getEyePosition().distanceTo(aim)<3.5&&h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(nativePos(p));
    }
    private void mine(){
        if(client.gui.screen()!=null){fail("Menu remains open before row deletion");return;}
        if(walking){
            // A shovel approach is complete at verified source reach; a floating target need not be landed upon.
            if(targetIndex<targets.size()&&loaded(targets.get(targetIndex))&&(world.getBlockState(nativePos(targets.get(targetIndex))).isAir()||inMiningReach(targets.get(targetIndex)))){navigator.stop();walking=false;}
            else{var st=navigator.tick();if(st==McTravelNavigator.State.FAILED){var p=targets.get(targetIndex);var aim=visibleFace(nativePos(p));var h=world.clip(new ClipContext(owner.getEyePosition(),aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,owner));fail("Loaded row approach obstructed: "+navigator.status()+" flying="+owner.getAbilities().flying+" pose="+owner.position()+" cell="+p+" state="+world.getBlockState(nativePos(p))+" aim="+aim+" reach="+owner.getEyePosition().distanceTo(aim)+" ray="+h.getBlockPos());return;}if(st==McTravelNavigator.State.ACTIVE)return;navigator.stop();walking=false;}
        }
        if(targetIndex==targets.size()){
            for(var p:scan)if(!validScopeCell(p)){fail("Scope protected/unloaded before completion");return;}
            if(flow.phase()==PlotCleanerWorkflow.Phase.ROW_DELETE){flow.rowsComplete();captureTargets(true);}else{
                if(scan.stream().anyMatch(p->PlotCleanerPlan.natural(id(world.getBlockState(nativePos(p)))))){fail("Unconfirmed natural cells remain; stopped instead of silently completing");return;}flow.dirtComplete();}
            return;
        }
        var p=targets.get(targetIndex);if(!loaded(p)){fail("Target chunk unloaded");return;}var state=world.getBlockState(nativePos(p));
        if(state.isAir()){if(++airTicks>=2){if(target!=null){if(flow.phase()==PlotCleanerWorkflow.Phase.ROW_DELETE)rowRemoved++;else cleanupRemoved++;}targetIndex++;target=null;airTicks=0;}return;}
        airTicks=0;if(!PlotCleanerPlan.natural(id(state))||state.hasBlockEntity()){fail("Target changed to protected material");return;}
        if(!owner.getInventory().getItem(toolSlot).is(Items.DIAMOND_SHOVEL)||!ItemStack.isSameItemSameComponents(identity(owner.getInventory().getItem(toolSlot)),toolTemplate)){fail("Shovel changed or exhausted");return;}
        target=p;expected=id(state);services.inventory().selectSlot(toolSlot);boolean descent=firstColumn!=null&&p.x()==firstColumn.x()&&p.z()==firstColumn.z();if(flow.phase()==PlotCleanerWorkflow.Phase.ROW_DELETE)rowPhase=descent?RowPhase.ROTATE_DOWN:RowPhase.ROTATE_AROUND_ADJACENT;
        var aim=visibleFace(nativePos(p));var h=world.clip(new ClipContext(owner.getEyePosition(),aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,owner));
        if(owner.getEyePosition().distanceTo(aim)>=3.5||h.getType()!=HitResult.Type.BLOCK||!h.getBlockPos().equals(nativePos(p))){
            boolean found=false;for(int[] d:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}})if(!found){var v=new dev.veltraph.client.world.Vec3(p.x()+.5+d[0],p.y()+1.0,p.z()+.5+d[1]);found=startFlight(v);}
            if(!found){fail("No loaded visible approach to row target");return;}if(flow.phase()==PlotCleanerWorkflow.Phase.ROW_DELETE)rowPhase=descent?RowPhase.FLY_TO_CORNER1:RowPhase.FLY_TO_CORNER2;walking=true;return;
        }
        var delta=aim.subtract(owner.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(nativePos(p))&&mayBreak(p)){if(flow.phase()==PlotCleanerWorkflow.Phase.ROW_DELETE)rowPhase=descent?RowPhase.MINE_DOWN:RowPhase.DELETE_ROW;services.breaking().setBreaking(true);}
    }
    private void beginReturnView(){
        navigator.stop();walking=false;services.input().releaseAllMovement();services.breaking().setBreaking(false);
        returnEquipment=equipment();returnSlot=services.inventory().selectedSlot();
        McOwnedRotation.validate(client);
        if(McOwnedRotation.installed(client)&&McOwnedRotation.authority(client)!=this){fail("Return view authority unavailable");return;}
        // End the prior mining/placement heading before binding the terminal request, under our live authority.
        interactionRotation.cancelRotation();returnAuthority=McOwnedRotation.authority(client);returnEpoch=McOwnedRotation.epoch(client);
        terminalView=new GardenTerminalView(interactionRotation,this::returnCurrent,this::ownsReturnCamera,originalYaw,originalPitch);
        last="RETURN_VIEW grounded original pad confirmed";
        if(!terminalView.start())fail("Return view could not retain cleaner authority");
    }
    private boolean returnCurrent(){
        return isEnabled()&&leaseHeld&&client.player==owner&&client.level==world&&client.getConnection()==connection&&owner!=null&&owner.isAlive()
                &&owner.getAbilities().mayfly&&client.gui.screen()==null&&owner.containerMenu.getCarried().isEmpty()&&unchanged()
                &&services.inventory().selectedSlot()==returnSlot&&sameEquipment()&&now()<deadline&&owner.onGround()
                &&owner.position().distanceTo(new Vec3(originalPose.x(),originalPose.y(),originalPose.z()))<.35
                &&world.getChunkSource().hasChunk(returnPad.getX()>>4,returnPad.getZ()>>4)&&world.getBlockState(returnPad).toString().equals(returnPadState);
    }
    @Override public void onDisable(){
        interactionRotation.release(navigator);services.input().releaseAllMovement();services.breaking().setBreaking(false);services.breaking().clearTargetGuard(this);if(terminalView!=null)terminalView.cancel();
        interactionRotation.cancelRotation();
        if(leaseHeld&&owner!=null&&client.player==owner&&client.level==world&&client.getConnection()==connection){
            if(ownedMenu!=null&&owner.containerMenu==ownedMenu)owner.closeContainer();
            if(services.inventory().selectedSlot()==toolSlot||terminalView!=null&&services.inventory().selectedSlot()==returnSlot)services.inventory().selectSlot(originalSlot);owner.getAbilities().flying=originalFlying&&owner.getAbilities().mayfly;if(owner.isAlive())owner.onUpdateAbilities();restored=flow!=null&&flow.phase()==PlotCleanerWorkflow.Phase.COMPLETE;
        }
        dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);leaseHeld=false;menus.clear();target=null;walking=false;
        if(flow!=null&&flow.active()){flow.cancel("Explicit cancel; inputs/equipment/pose released");last=flow.reason();}
    }
    @Override public String diagnostics(){return (flow==null?"IDLE":flow.phase())+": "+last+" | rowState="+rowPhase+" row="+rowRemoved+" cleanup="+cleanupRemoved+" | vanilla row counterpart; bindings are owner-verified";}
    public PlotCleanerWorkflow.Phase workflowPhase(){return flow==null?PlotCleanerWorkflow.Phase.FAILED:flow.phase();}
    public int rowRemoved(){return rowRemoved;}public int cleanupRemoved(){return cleanupRemoved;}public boolean restored(){return restored;}
}
