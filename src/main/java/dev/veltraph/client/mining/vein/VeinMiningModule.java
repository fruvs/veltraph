package dev.veltraph.client.mining.vein;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.mining.mc.McMiningServices;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Independent reachable-vein engine. Ground approach uses existing Veltraph McMiningServices. */
public class VeinMiningModule extends AbstractModule {
 public static final String ID="vein_mining";
 private final Minecraft client;private final FarmServices farm;private final ConfigService config;
 private final McMiningServices mining;
 private final dev.veltraph.client.navigation.mc.McNavigator navigation;
 private dev.veltraph.client.world.BlockPos approach;
 private Object player,world,connection;private VeinMiningConfig.Stamp settings;
 private NativeStackIdentity.Snapshot tool;private int slot,activation,removed;private int originalSlot=-1;private CleanupInventory cleanupInventory;private CollectionActivation collectionActivation;
 private net.minecraft.core.BlockPos target;private net.minecraft.world.level.block.state.BlockState targetState;
 private Vec3 aim;private long acquiredAt,issuedAt=-1,issuedSerial,emptySince=-1,lastNow;
 private boolean breaking;private String reason="Ready";private String contextFailure="unobserved";private long contextChecks;
 private final VeinBreakSession session=new VeinBreakSession();
 private VeinNativeWearBridge wear;private VeinNativePickupAdapter pickup;private boolean settling;private java.util.List<?> inventoryBaseline;private boolean projectedWear;private NativeStackIdentity.Snapshot qualifiedWearTool;private long attempt;
 private long operationStarted=-1,queryStarted;private int scanIndex,candidateIndex;private VeinScanCursor<VeinTargetPlanner.Option> rankedScan,vantageScan;private Object scanScope;private VeinTargetPlanner.Option provisional,admissionCandidate;private net.minecraft.core.BlockPos scanOrigin;private Vec3 scanPose;private final java.util.List<net.minecraft.core.BlockPos> scannedCandidates=new java.util.ArrayList<>();private int queryCount;private boolean queryIncomplete;private String queryPhase="unobserved",queryFirstFailure="none";private int queryFirstFailureCount;private long queryFirstFailureElapsed;
 public VeinMiningModule(Minecraft client,FarmServices farm,ConfigService config){this(ID,"Vein Mining",client,farm,config);}
 protected VeinMiningModule(String id,String name,Minecraft client,FarmServices farm,ConfigService config){super(id,name,ModuleCategory.MINING);this.client=client;this.farm=farm;this.config=config;mining=new McMiningServices(client,farm,config);navigation=new dev.veltraph.client.navigation.mc.McNavigator(client,farm.input(),config);}
 /** Additional task scope can only refuse the existing native engine, never grant its custody. */
 protected boolean workCurrent(){return true;}
 protected boolean airborneApproachCurrent(){return false;}
 protected Map<MiningMaterial,Integer> materialPriorities(){return VeinMiningConfig.priorities(config.get(VeinMiningConfig.MATERIALS));}
 protected boolean taskAllows(net.minecraft.core.BlockPos cell,MiningMaterial material){return true;}
 protected boolean startApproach(dev.veltraph.client.world.BlockPos standing){
  if(!mining.collectionCleanupTyped())return navigation.start(standing);
  var source=collectionParentApproach(mining);return source!=null&&mining.startCollectionApproach(this,source);
 }
 protected dev.veltraph.client.navigation.WalkController.State tickApproach(){return mining.collectionCleanupTyped()?mining.tickCollectionApproach():navigation.tick();}
 protected void stopApproach(){if(mining.collectionCleanupTyped())mining.stopCollectionApproach();else navigation.stop();}
 protected boolean approachArrived(dev.veltraph.client.world.BlockPos standing,dev.veltraph.client.navigation.WalkController.State state){return farm.player().position().distanceTo(navigation.position(standing))<.28;}

 @Override public boolean requiresSkyBlock(){return true;}
 @Override public Optional<String> preStartCheck(){try{
  if(!mining.available()||!client.isSameThread())return Optional.of("Loaded player and closed screen required");
  if(!config.get(VeinMiningConfig.ENABLED)||!config.get(MiningConfig.AUTO_BREAK_MINING))return Optional.of("Enable vein mining and auto break");
  VeinMiningConfig.priorities(config.get(VeinMiningConfig.MATERIALS));VeinMiningConfig.stamp(config);
  int radius=config.get(VeinMiningConfig.SCAN_RADIUS);if(radius<1||radius>6)return Optional.of("Scan radius must be 1–6 blocks");
  if(config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)<250||config.get(VeinMiningConfig.RESPAWN_WAIT_MS)<0)return Optional.of("Invalid mining time limits");return Optional.empty();
 }catch(RuntimeException invalid){return Optional.of("Invalid vein mining settings: "+invalid.getMessage());}}
 @Override public void onEnable(){cleanupInventory=null;activation++;removed=0;originalSlot=-1;target=null;issuedAt=-1;emptySince=-1;lastNow=now();operationStarted=-1;player=client.player;world=client.level;connection=client.getConnection();settings=VeinMiningConfig.stamp(config);farm.breaking().installTargetGuard(this,this::allowsTarget);
  var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();var receipt=manager==null?null:manager.currentOwnEnableReceipt(this).orElse(null);
  collectionActivation=receipt==null?null:new CollectionActivation(this,receipt);
 }
 @Override public void onDisable(){
  farm.breaking().clearTargetGuard(this);
  boolean typed=mining.collectionCleanupTyped();
  if(typed){stopApproach();mining.cleanup();mining.restoreCollectionOriginalSlot(this);}
  else if(originalSlot>=0&&client.player==player&&client.level==world&&client.player.getInventory().getSelectedSlot()==slot)farm.inventory().selectSlot(originalSlot);
  if(pickup!=null)pickup.close();pickup=null;settling=false;inventoryBaseline=null;if(wear!=null)wear.close();wear=null;
  originalSlot=-1;mining.cleanup();if(!typed)stopApproach();approach=null;admissionCandidate=null;target=null;tool=null;qualifiedWearTool=null;resetScan();breaking=false;session.stop();
 }
 private static long now(){return System.nanoTime()/1_000_000;}
 private dev.veltraph.client.world.BlockPos value(net.minecraft.core.BlockPos p){return new dev.veltraph.client.world.BlockPos(p.getX(),p.getY(),p.getZ());}
 /** Diagnostics reuse only each predicate operand already evaluated, retaining original short circuit order. */
 private boolean context(){contextChecks++;contextFailure="none";
  return observed("enabled",isEnabled())&&observed("client-thread",client.isSameThread())
   &&observed("player-identity",client.player==player)&&observed("world-identity",client.level==world)
   &&observed("connection-identity",client.getConnection()==connection)&&observed("connection-present",connection!=null)
   &&observed("native-available",mining.available())&&observed("on-ground",client.player.onGround()||approach!=null&&airborneApproachCurrent())
   &&observed("inventory-menu",client.player.containerMenu==client.player.inventoryMenu)
   &&observed("carried-empty",client.player.containerMenu.getCarried().isEmpty())
   &&observed("manager-present",dev.veltraph.client.VeltraphBootstrap.moduleManager()!=null)
   &&observed("input-owner",dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==this)
   &&observed("camera-installed",McOwnedRotation.installed(client))&&observed("camera-authority",McOwnedRotation.authority(client)==this)
   &&observed("configuration-stamp",settings.equals(VeinMiningConfig.stamp(config)))
   &&observed("work-scope",workCurrent());
 }
 private boolean observed(String operand,boolean accepted){if(!accepted)contextFailure=operand;return accepted;}
 private boolean loaded(net.minecraft.core.BlockPos p){if(!query())return false;boolean loaded=client.level.isInWorldBounds(p)&&client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4);return query()&&loaded;}
 private static String id(net.minecraft.world.level.block.state.BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
 private Optional<Vec3> visiblePoint(net.minecraft.core.BlockPos cell){return visiblePoint(cell,client.player.getEyePosition());}
 private Optional<Vec3> visiblePoint(net.minecraft.core.BlockPos cell,net.minecraft.world.phys.Vec3 eye){
  if(!loaded(cell)||!query())return Optional.empty();double reach=client.player.blockInteractionRange();
  var shape=client.level.getBlockState(cell).getShape(client.level,cell);int samples=0;
  for(var box:shape.toAabbs()){
   double midX=(box.minX+box.maxX)/2,midY=(box.minY+box.maxY)/2,midZ=(box.minZ+box.maxZ)/2;
   double[][] points={{box.minX+.001,midY,midZ},{box.maxX-.001,midY,midZ},{midX,box.minY+.001,midZ},{midX,box.maxY-.001,midZ},{midX,midY,box.minZ+.001},{midX,midY,box.maxZ-.001}};
   for(var point:points){if(!query())return Optional.empty();if(++samples>96)return Optional.empty();var end=new net.minecraft.world.phys.Vec3(cell.getX()+point[0],cell.getY()+point[1],cell.getZ()+point[2]);
    if(eye.distanceTo(end)>reach)continue;var hit=client.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
    if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(cell))return Optional.of(new Vec3(end.x,end.y,end.z));}
  }return Optional.empty();
 }
 @Override public void onTick(){if(!isEnabled())return;try{
  long time=now();queryPhase="tick-context";queryStarted=System.nanoTime();queryCount=0;queryIncomplete=false;queryFirstFailure="none";queryFirstFailureCount=0;queryFirstFailureElapsed=0;if(time<lastNow||!context()){stop("Mining context or configuration changed ["+(time<lastNow?"clock-rollback":contextFailure)+"]");return;}lastNow=time;
  if(mining.collectionCleanupTyped()&&!mining.collectionActivationCurrent(this,collectionActivation)){
   if(collectionActivation==null||!mining.startCollectionActivation(this,collectionActivation)){stop("Mining fresh registered activation custody refused");return;}
  }
  if(operationStarted>=0&&time-operationStarted>=config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)){stop("Mining operation deadline reached");return;}
  if(mining.collectionSourceRevoked()){stop("Mining collection operation source replaced");return;}
  if(approach!=null){
   farm.breaking().setBreaking(false);breaking=false;
   if(!navigation.standable(approach)){stop("Mining approach support changed");return;}
   var state=tickApproach();if(state==dev.veltraph.client.navigation.WalkController.State.BLOCKED){stop("Mining approach blocked");return;}
   if(approachArrived(approach,state)){stopApproach();approach=null;target=null;}
   return;
  }
  if(settling){farm.breaking().setBreaking(false);breaking=false;mining.retireTakenCollection(pickup);if(!adoptInventory(time)){return;}if(pickup!=null&&pickup.settled(inventoryBaseline,wear.actualInventory())){stopOwnedDropWalking();pickup.close();pickup=null;wear.close();wear=null;target=null;tool=null;qualifiedWearTool=null;operationStarted=-1;settling=false;reason="Captured mining pickup settled";}else if(pickup!=null&&pickup.hasPendingItems()&&context()&&toolActionReady()){mining.collectDrops(value(target),pickup);if(mining.collectionFailed(pickup))stop("Mining attributed collection revoked");}return;}
  if(target==null){if(admissionCandidate!=null)prepareAdmission(time);else choose(time);return;}
  projectedWear=qualifiedWearTool!=null&&qualifiedWearTool.matches(client.player.getMainHandItem(),client.level.registryAccess());
  if(qualifiedWearTool!=null&&!projectedWear){stop("Projected mining wear identity changed");return;}
  if(wear==null){stop("Mining receipt binding unavailable");return;}
  queryPhase="inventory-adoption";if(!adoptInventory(time))return;
  if(client.player.getInventory().getSelectedSlot()!=slot){stop("Mining slot changed");return;}
  if(!projectedWear&&!tool.matches(client.player.getMainHandItem(),client.level.registryAccess())){
   farm.breaking().setBreaking(false);breaking=false;
   if(qualifiedWearTool!=null&&qualifiedWearTool.matches(client.player.getMainHandItem(),client.level.registryAccess())){projectedWear=true;}
   else if(wear!=null&&wear.pendingOnlyDamage()){reason="Waiting for correlated wear and block receipts";return;}
   else{stop("Mining components changed without correlated wear permit");return;}
  }
  // Inventory/custody serialization is not geometry. Discard all earlier geometry and sample
  // the one operation proof budget only after exact inventory adoption and fresh native custody.
  long geometryTime=now();if(geometryTime<lastNow||geometryTime>=wear.deadline()||!context()){stop("Mining custody changed after inventory processing");return;}
  time=geometryTime;queryPhase="target-geometry-after-inventory";queryStarted=System.nanoTime();queryCount=0;queryIncomplete=false;queryFirstFailure="none";queryFirstFailureCount=0;queryFirstFailureElapsed=0;
  if(!loaded(target)){stop(queryIncomplete?"Mining geometry proof incomplete":"Target chunk unloaded");return;}
  var current=client.level.getBlockState(target);var receipt=MiningBlockReceipts.latest(client,target).orElse(null);
  if(!current.equals(targetState)){
   farm.breaking().setBreaking(false);breaking=false;
   boolean acknowledged=issuedAt>=0&&wear!=null&&wear.completionReady()&&receipt!=null&&receipt.serial()>issuedSerial&&receipt.at()>=issuedAt&&receipt.state().equals(current);
   var receiptFrame=frame(time,current,acknowledged,receipt==null?issuedSerial:receipt.serial(),false);var state=session.tickGeometry(receiptFrame,time,!queryIncomplete);
   if(state==VeinBreakSession.State.WAITING_RECEIPT){reason="Waiting for server block receipt";return;}
   if(queryIncomplete){releaseOwnedAdmissionAttack();if(state==VeinBreakSession.State.TIMED_OUT||state==VeinBreakSession.State.STOPPED){stop("Mining receipt session expired while geometry incomplete");return;}reason="Mining receipt geometry continues next frame";return;}
   if(state==VeinBreakSession.State.REMOVED){if(!context()||queryIncomplete||wear==null||!wear.consumeCompletion()){stop("Mining completion custody revoked");return;}removed++;reason="Server confirmed removal "+removed+"; collecting attributed drops";settling=true;issuedAt=-1;return;}
   stop("Target changed without qualified session removal: "+state);return;
  }
  queryPhase="target-problem";String problem=targetProblem(target);if(!problem.isEmpty()||!mining.hasCapacity(value(target))||!mining.heldToolReady(value(target),config.get(MiningConfig.MINING_DURABILITY_RESERVE))){stop(problem.isEmpty()?"Tool or capacity unavailable":problem);return;}
  queryPhase="target-visible-point";var fresh=visiblePoint(target);if(fresh.isEmpty()){farm.breaking().setBreaking(false);breaking=false;reason="Waiting for a visible reachable face";return;}
  queryPhase="target-camera-plan";aim=fresh.get();if(queryIncomplete){farm.breaking().setBreaking(false);breaking=false;return;}if(mining.collectionCleanupTyped()){var cameraSource=collectionParentOperation(mining);if(cameraSource==null||!mining.publishCollectionParentCamera(aim,this,cameraSource)){stop("Mining current parent camera custody revoked");return;}}else farm.rotation().lookAt(aim);if(!mining.aimedAt(value(target))){var aimFrame=frame(time,current,false,MiningBlockReceipts.serial(),false);var aimPhase=session.tickGeometry(aimFrame,time,!queryIncomplete);farm.breaking().setBreaking(false);breaking=false;if(aimPhase==VeinBreakSession.State.TIMED_OUT||aimPhase==VeinBreakSession.State.STOPPED)stop("Mining aim session expired or revoked");return;}
  if(issuedAt<0){issuedAt=time;issuedSerial=MiningBlockReceipts.serial();}
  if(!context()){stop("Mining custody revoked before attack");return;}
  queryPhase="session-action-frame";var actionFrame=frame(time,current,false,MiningBlockReceipts.serial(),true);var phase=session.tickGeometry(actionFrame,time,!queryIncomplete);if(queryIncomplete){releaseOwnedAdmissionAttack();if(phase==VeinBreakSession.State.TIMED_OUT||phase==VeinBreakSession.State.STOPPED)stop("Mining action session expired while geometry incomplete");return;}if(phase!=VeinBreakSession.State.BREAKING){stop("Session attack rejected: "+phase);return;}breaking=session.attackRequested();farm.breaking().setBreaking(breaking);reason="Mining "+id(targetState);
 }catch(RuntimeException invalid){stop("Mining evidence unavailable: "+invalid.getClass().getSimpleName());}}
 private void choose(long time){queryPhase="choose-scan";
  if(operationStarted<0)operationStarted=time;
  var priorities=materialPriorities();var options=new ArrayList<VeinTargetPlanner.Option>();
  var origin=client.player.blockPosition();int radius=config.get(VeinMiningConfig.SCAN_RADIUS);int side=radius*2+1,total=side*side*8;
  var position=farm.player().position();var nativeEye=client.player.getEyePosition();var eye=new Vec3(nativeEye.x,nativeEye.y,nativeEye.z);
  if(scanOrigin==null){scanOrigin=origin.immutable();scanPose=position;scanIndex=0;candidateIndex=0;scannedCandidates.clear();scanScope=scanScopeNow();}
  if(!Objects.equals(scanScope,scanScopeNow())||!scanOrigin.equals(origin)||position.distanceTo(scanPose)>.02){resetScan();reason="Mining scan pose changed";return;}
  while(scanIndex<total){
   int index=scanIndex;int x=index/(side*8)-radius,y=(index/side)%8-3,z=index%side-radius;var cell=scanOrigin.offset(x,y,z);
   if(!query()){reason="Mining scan continues next frame";return;}
   if(loaded(cell)){var material=MiningMaterial.classify(id(client.level.getBlockState(cell)));if(material.isPresent()&&priorities.getOrDefault(material.get(),0)>0)scannedCandidates.add(cell.immutable());}
   if(queryIncomplete){reason="Mining scan continues next frame";return;}scanIndex++;
  }
  // Evaluate all candidate costs incrementally; cached best is never an action permission.
  if(rankedScan==null)rankedScan=new VeinScanCursor<>(scannedCandidates.size(),scanScope,operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS));
  while(!rankedScan.complete()){
   var cell=scannedCandidates.get(rankedScan.index());var proof=prove(cell,position,nativeEye,time,priorities);
   if(queryIncomplete){reason="Mining ranked proof continues next frame";return;}
   if(!rankedScan.advance(scanScope,time,true,proof==null?null:proof.option(),proof==null?0:proof.cost())){stop("Mining ranked scan scope retired");return;}
  }
  provisional=rankedScan.best(scanScope,time).map(VeinScanCursor.Ranked::value).orElse(null);
  var finalChoice=provisional==null?null:prove(new net.minecraft.core.BlockPos(provisional.cell().x(),provisional.cell().y(),provisional.cell().z()),position,nativeEye,time,priorities);
  if(queryIncomplete){reason="Mining fresh commitment continues next frame";return;}
  if(provisional!=null&&finalChoice==null){resetScan();reason="Mining provisional target changed";return;}
  var choice=Optional.ofNullable(finalChoice);
  if(choice.isEmpty()&&tryApproach(priorities,time))return;
  if(choice.isEmpty()&&vantageScan!=null){farm.breaking().setBreaking(false);return;}
  if(choice.isEmpty()){farm.breaking().setBreaking(false);if(emptySince<0)emptySince=time;reason="Waiting for selected materials in reach";if(time-emptySince>=config.get(VeinMiningConfig.RESPAWN_WAIT_MS))stop("No selected material in reach before wait deadline");return;}
  admissionCandidate=choice.get().option();resetScan();reason="Preparing selected mining target";
 }
 /** Separate ordinary preparation tick; no scan geometry may authorize this transaction. */
 private void prepareAdmission(long time){
  var selected=admissionCandidate;var cell=new net.minecraft.core.BlockPos(selected.cell().x(),selected.cell().y(),selected.cell().z());
  if(!context()||now()>=operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)){stop("Mining admission custody or deadline revoked");return;}
  if(!client.level.getChunkSource().hasChunk(cell.getX()>>4,cell.getZ()>>4)){admissionCandidate=null;reason="Selected mining chunk unloaded";return;}
  int nextSlot=-1;float speed=-1;var nativeState=client.level.getBlockState(cell);
  for(int index=0;index<9;index++){var item=client.player.getInventory().getItem(index);
   if(!item.is(net.minecraft.tags.ItemTags.PICKAXES)||nativeState.requiresCorrectToolForDrops()&&!item.isCorrectToolForDrops(nativeState)
      ||item.isDamageableItem()&&item.getMaxDamage()-item.getDamageValue()<=config.get(MiningConfig.MINING_DURABILITY_RESERVE))continue;
   float candidate=item.getDestroySpeed(nativeState);if(Float.isFinite(candidate)&&candidate>speed){speed=candidate;nextSlot=index;}}
  if(nextSlot<0){stop("No durable harvest-capable pickaxe");return;}
  // Serialize all identity before ONE new geometry phase. Original deadline includes this CPU time.
  var preparedNativeTool=client.player.getInventory().getItem(nextSlot).copy();queryPhase="admission-tool-codec";var nextTool=NativeStackIdentity.capture(client.player.getInventory().getItem(nextSlot),client.level.registryAccess());
  int selectedBefore=client.player.getInventory().getSelectedSlot();var poseBefore=farm.player().position();float yawBefore=client.player.getYRot(),pitchBefore=client.player.getXRot();
  if(!context()||!nextTool.matches(client.player.getInventory().getItem(nextSlot),client.level.registryAccess())){stop("Mining preparation identity revoked");return;}
  long geometryTime=now();if(geometryTime>=operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)){stop("Mining admission deadline reached");return;}
  var preparedScope=java.util.List.of(player,world,connection,settings,selectedBefore,preparedNativeTool.getComponents(),preparedNativeTool.getCount(),poseBefore,yawBefore,pitchBefore,McOwnedRotation.authority(client),McOwnedRotation.epoch(client));queryPhase="admission-fresh-geometry";queryStarted=System.nanoTime();var transaction=new VeinAdmissionTransaction(preparedScope,operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS),queryStarted);queryCount=0;queryIncomplete=false;queryFirstFailure="none";queryFirstFailureCount=0;queryFirstFailureElapsed=0;
  var proof=prove(cell,poseBefore,client.player.getEyePosition(),geometryTime,materialPriorities());
  boolean capacity=mining.hasCapacity(value(cell));if(!query()||queryIncomplete){releaseOwnedAdmissionAttack();reason="Mining admission geometry continues next frame";return;}
  if(proof==null||!capacity||!client.level.getBlockState(cell).equals(nativeState)){admissionCandidate=null;reason="Selected mining geometry changed";return;}
  // Cheap immutable native component comparison; exact serialized identity was captured above.
  if(!context()||!net.minecraft.world.item.ItemStack.isSameItemSameComponents(preparedNativeTool,client.player.getInventory().getItem(nextSlot))||preparedNativeTool.getCount()!=client.player.getInventory().getItem(nextSlot).getCount()||client.player.getInventory().getSelectedSlot()!=selectedBefore||!farm.player().position().equals(poseBefore)
      ||client.player.getYRot()!=yawBefore||client.player.getXRot()!=pitchBefore||now()>=operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)){stop("Mining admission publication custody revoked");return;}
  var commitScope=java.util.List.of(client.player,client.level,client.getConnection(),VeinMiningConfig.stamp(config),client.player.getInventory().getSelectedSlot(),client.player.getInventory().getItem(nextSlot).getComponents(),client.player.getInventory().getItem(nextSlot).getCount(),farm.player().position(),client.player.getYRot(),client.player.getXRot(),McOwnedRotation.authority(client),McOwnedRotation.epoch(client));
  boolean commitChunk=client.level.getChunkSource().hasChunk(cell.getX()>>4,cell.getZ()>>4);boolean commitState=commitChunk&&client.level.getBlockState(cell).equals(nativeState);
  boolean commitIdentity=net.minecraft.world.item.ItemStack.isSameItemSameComponents(preparedNativeTool,client.player.getInventory().getItem(nextSlot))&&preparedNativeTool.getCount()==client.player.getInventory().getItem(nextSlot).getCount();
  long commitNow=now();long finalGeometrySample=System.nanoTime();
  if(!transaction.commitFresh(commitScope,finalGeometrySample,queryCount,commitNow,proof!=null&&capacity&&commitState,commitIdentity)){releaseOwnedAdmissionAttack();reason="Mining fresh admission transaction incomplete";return;}
  var expectedPublication=new VeinAdmissionTransaction.Publication(player,world,connection,this,McOwnedRotation.authority(client),McOwnedRotation.epoch(client),poseBefore,yawBefore,pitchBefore,settings,preparedNativeTool.getComponents(),preparedNativeTool.getCount(),nextSlot);
  if(originalSlot<0)originalSlot=selectedBefore;slot=nextSlot;
  var selectionBefore=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
  if(mining.collectionCleanupTyped()&&cleanupInventory!=null&&!cleanupInventory.selectionCurrent()){stop("Mining original tool selection history revoked");return;}
  if(selectedBefore!=nextSlot){
   if(mining.collectionCleanupTyped()){
    if(!(farm.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService inventory)){stop("Mining scoped native selector unavailable");return;}
    var priorCleanup=cleanupInventory;
    final int admissionSelectedSlot=nextSlot;
    var slotSource=new AdmissionSlotSource(this,selectedBefore,nextSlot,selectionBefore);
    var selectedReceipt=inventory.selectSlotScoped(selectedBefore,nextSlot,
     ()->context()&&slotSource.current(selectedBefore,false),
     ()->context()&&slotSource.current(admissionSelectedSlot,true));
    // Preserve an actual own local setter even if a later packet/callback refuses success.
    // Its data baseline remains the last qualified inventory; no callback may refresh it.
    if(selectedReceipt.localSelected()&&selectedReceipt.nativeSelectionHistory()!=null&&selectionBefore!=null
      &&selectedReceipt.nativeSelectionHistory().followsExactlyOneSetter(selectionBefore)&&priorCleanup!=null)
     cleanupInventory=new CleanupInventory(this,selectedReceipt.nativeSelectionHistory(),priorCleanup.stacks);
    if(!selectedReceipt.confirmed()||selectedReceipt.nativeSelectionHistory()==null||!selectedReceipt.nativeSelectionHistory().followsExactlyOneSetter(selectionBefore)){stop("Mining current native slot selection refused");return;}
   }else farm.inventory().selectSlot(nextSlot);
  }
  var selectionAfter=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
  boolean ownSelection=selectionBefore!=null&&selectionAfter!=null&&(selectedBefore==nextSlot?selectionBefore.current():selectionAfter.followsExactlyOneSetter(selectionBefore));
  if(!ownSelection&&mining.collectionCleanupTyped()){stop("Mining own selector history unavailable");return;}
  if(client.player!=player||client.level!=world||client.getConnection()!=connection||!settings.equals(VeinMiningConfig.stamp(config))||client.player.getInventory().getSelectedSlot()!=nextSlot
      ||!net.minecraft.world.item.ItemStack.isSameItemSameComponents(preparedNativeTool,client.player.getMainHandItem())||preparedNativeTool.getCount()!=client.player.getMainHandItem().getCount()
      ||!client.level.getChunkSource().hasChunk(cell.getX()>>4,cell.getZ()>>4)||!client.level.getBlockState(cell).equals(nativeState)||now()>=operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)){releaseOwnedAdmissionAttack();stop("Mining slot publication changed native custody");return;}
  long publicationDeadline=operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS);
  var actualPublication=VeinAdmissionTransaction.capturePublication(new VeinAdmissionTransaction.PublicationPort(){
   public Object configuration(){return VeinMiningConfig.stamp(config);}
   public Object owner(){var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();return manager==null?null:manager.inputOwner().orElse(null);}
   public Object camera(){return McOwnedRotation.authority(client);}
   public VeinAdmissionTransaction.Publication nativeLeaves(Object configuration,Object owner,Object camera){
    // Selector callbacks have completed. These direct native fields/component reads cannot invoke config suppliers.
    var nativePlayer=client.player;var nativeWorld=client.level;var nativeConnection=client.getConnection();long epoch=McOwnedRotation.epoch(client);
    var position=new Vec3(nativePlayer.getX(),nativePlayer.getY(),nativePlayer.getZ());float yaw=nativePlayer.getYRot(),pitch=nativePlayer.getXRot();
    var held=nativePlayer.getMainHandItem();var components=held.getComponents();int count=held.getCount(),selected=nativePlayer.getInventory().getSelectedSlot();
    return new VeinAdmissionTransaction.Publication(nativePlayer,nativeWorld,nativeConnection,owner,camera,epoch,position,yaw,pitch,configuration,components,count,selected);
   }
  });
  long publicationNow=now();long publicationGeometrySample=System.nanoTime();
  boolean samePublication=VeinAdmissionTransaction.unchangedPublication(expectedPublication,actualPublication);
  var publicationResult=VeinAdmissionTransaction.publicationResult(samePublication,publicationNow,publicationDeadline,queryStarted,publicationGeometrySample,queryCount);
  if(publicationResult!=VeinAdmissionTransaction.PublicationResult.ADMIT){
   String captured;try{String failed=!samePublication?publicationDifference(expectedPublication,actualPublication):publicationNow>=publicationDeadline?"operation-deadline":publicationGeometrySample-queryStarted>4_000_000?"geometry-time":"geometry-query-count";
   captured="failure="+failed+" geometryNs="+(publicationGeometrySample-queryStarted)+" queries="+queryCount+" now="+publicationNow+" deadline="+publicationDeadline+" expectedEpoch="+expectedPublication.epoch()+" actualEpoch="+actualPublication.epoch()+" expectedPose="+expectedPublication.position()+" actualPose="+actualPublication.position()+" expectedYawPitch="+expectedPublication.yaw()+","+expectedPublication.pitch()+" actualYawPitch="+actualPublication.yaw()+","+actualPublication.pitch()+" expectedSlot="+expectedPublication.slot()+" actualSlot="+actualPublication.slot();}catch(Throwable diagnosticUnavailable){captured="diagnostic-unavailable:"+diagnosticUnavailable.getClass().getSimpleName();}
   releaseOwnedAdmissionAttack();
   if(publicationResult==VeinAdmissionTransaction.PublicationResult.YIELD){stopOwnedDropWalking();reason="Mining publication geometry yields ["+captured+"]";return;}
   stop("Mining publication pose/camera/owner changed or proof expired ["+captured+"]");return;
  }
  target=cell;targetState=nativeState;aim=proof.option().aim();tool=nextTool;acquiredAt=geometryTime;issuedAt=-1;emptySince=-1;breaking=false;projectedWear=false;qualifiedWearTool=null;
  var admission=new VeinBreakSession.Frame(VeinAdmissionTransaction.admissionScope(actualPublication,tool,activation),value(target),id(targetState),proof.option().loaded(),proof.option().exactRayHit(),false,nextSlot>=0,capacity,proof.option().supported()&&proof.option().bodyClear()&&proof.option().safeApproach(),client.gui.screen()==null,VeinAdmissionTransaction.unchangedPublication(expectedPublication,actualPublication),false,MiningBlockReceipts.serial(),geometryTime);
  if(queryIncomplete){releaseOwnedAdmissionAttack();target=null;tool=null;reason="Admission final fence incomplete";return;}if(!session.begin(admission,geometryTime,Math.max(1,publicationDeadline-geometryTime))){stop("Session rejected completed admission");return;}
  admissionCandidate=null;
  // Observation binding serializes inventory only after a complete admission; it never grants attack.
  queryPhase="choose-observer-codecs";wear=VeinNativeWearBridge.bind(client,this,farm.breaking(),++attempt,operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS),slot,settings.toString(),target);inventoryBaseline=wear.initialInventory();var pickupContext=new VeinPickupCorrelation.Context(wear.pickupScope(),wear.settings(),wear.originalTool(),slot,wear.epoch(),wear.deadline());pickup=VeinNativePickupAdapter.bind(client,()->new VeinNativePickupAdapter.Binding(pickupContext,()->pickupCustody()));wear.onSubmitted(pickup::issued);
  if(!context()||!tool.matches(client.player.getMainHandItem(),client.level.registryAccess())||!client.level.getChunkSource().hasChunk(target.getX()>>4,target.getZ()>>4)||!client.level.getBlockState(target).equals(targetState)||now()>=wear.deadline()){session.stop();stop("Admission custody changed during observation binding");return;}
  if(ownSelection&&selectionAfter.current())cleanupInventory=new CleanupInventory(this,selectionAfter);
  reason="Aiming at "+id(targetState);
 }
 /** Cached publication values only, called after unchangedPublication has already denied. */
 private static String publicationDifference(VeinAdmissionTransaction.Publication expected,VeinAdmissionTransaction.Publication actual){
  if(expected.player()!=actual.player())return "player";if(expected.world()!=actual.world())return "world";if(expected.connection()!=actual.connection())return "connection";
  if(expected.owner()!=actual.owner())return "input-owner";if(expected.camera()!=actual.camera())return "camera-authority";if(expected.epoch()!=actual.epoch())return "camera-epoch";
  if(!java.util.Objects.equals(expected.position(),actual.position()))return "position";if(Float.compare(expected.yaw(),actual.yaw())!=0)return "yaw";if(Float.compare(expected.pitch(),actual.pitch())!=0)return "pitch";
  if(!java.util.Objects.equals(expected.configuration(),actual.configuration()))return "configuration";if(!java.util.Objects.equals(expected.components(),actual.components()))return "tool-components";
  if(expected.count()!=actual.count())return "tool-count";if(expected.slot()!=actual.slot())return "tool-slot";return "unknown-publication-value";
 }
 private String targetProblem(net.minecraft.core.BlockPos cell){
  if(!query())return "Mining geometry budget incomplete";
  if(!loaded(cell)||supportContact(cell))return "Target unloaded or supports player footprint";
  var dimensions=client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING);
  if(!query()||!farm.world().bodyClear(farm.player().position(),dimensions.width()/2.0,dimensions.height())||!farm.world().hasGroundSupport(farm.player().position(),dimensions.width()/2.0)||!query())return "Player body or support unavailable";
  var state=client.level.getBlockState(cell);
  var taskMaterial=MiningMaterial.classify(id(state));if(taskMaterial.isPresent()&&!taskAllows(cell,taskMaterial.get()))return "Commission material or task scope changed";
  if(state.isAir()||state.getDestroySpeed(client.level,cell)<0||state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock||client.level.getBlockEntity(cell)!=null)return "Unsafe target";
  if(client.level.getBlockState(cell.above()).getBlock() instanceof net.minecraft.world.level.block.FallingBlock)return "Falling block above target";
  for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
   var neighbor=cell.offset(x,y,z);if(!loaded(neighbor))return "Target surroundings unloaded";var n=client.level.getBlockState(neighbor);if(!query())return "Mining geometry budget incomplete";
   if(!n.getFluidState().isEmpty()||n.is(net.minecraft.world.level.block.Blocks.FIRE)||n.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||n.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||n.is(net.minecraft.world.level.block.Blocks.CACTUS)||n.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||n.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)||n.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))return "Target exposes hazard";
  }
  return "";
 }
 /** Target vantage selection only; existing McNavigator supplies every actual path and input. */
 private boolean tryApproach(Map<MiningMaterial,Integer> priorities,long time){queryPhase="vantage-scan";
  var origin=client.player.blockPosition();var position=farm.player().position();int radius=config.get(VeinMiningConfig.SCAN_RADIUS),side=radius*2+1,cells=side*side*6;
  if(vantageScan==null)vantageScan=new VeinScanCursor<>(8*cells,scanScope,operationStarted+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS));
  while(!vantageScan.complete()){
   int i=vantageScan.index(),vantage=i/cells,index=i%cells;int[][] offsets={{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
   var standing=new dev.veltraph.client.world.BlockPos(origin.getX()+offsets[vantage][0],origin.getY(),origin.getZ()+offsets[vantage][1]);
   if(!query()){reason="Mining vantage scan continues next frame";return false;}
   VeinTargetPlanner.Choice proof=null;
   if(navigation.standable(standing)&&query()){
    var pose=navigation.position(standing);
    if(query()&&navigation.walkClear(position,pose)&&query()){
     var eye=new net.minecraft.world.phys.Vec3(pose.x(),pose.y()+client.player.getEyeHeight(),pose.z());
     int x=index/(side*6)-radius,y=(index/side)%6-2,z=index%side-radius;
     proof=prove(origin.offset(x,y,z),pose,eye,time,priorities);
    }
   }
   if(queryIncomplete){reason="Mining vantage proof continues next frame";return false;}
   if(!vantageScan.advance(scanScope,time,true,proof==null?null:proof.option(),proof==null?0:proof.cost())){stop("Mining vantage scan scope retired");return false;}
  }
  var cached=vantageScan.best(scanScope,time).map(VeinScanCursor.Ranked::value).orElse(null);if(cached==null){vantageScan=null;return false;}
  var pose=cached.standing();var standing=new dev.veltraph.client.world.BlockPos((int)Math.floor(pose.x()),(int)Math.ceil(pose.y()),(int)Math.floor(pose.z()));
  if(!query()||!navigation.standable(standing)||!query()||!navigation.walkClear(position,navigation.position(standing))||!query())return false;
  var eye=new net.minecraft.world.phys.Vec3(pose.x(),pose.y()+client.player.getEyeHeight(),pose.z());
  if(prove(new net.minecraft.core.BlockPos(cached.cell().x(),cached.cell().y(),cached.cell().z()),pose,eye,time,priorities)==null||queryIncomplete){vantageScan=null;return false;}
  if(!context()||!query())return false;approach=standing;if(!startApproach(approach)){approach=null;vantageScan=null;return false;}
  resetScan();reason="Approaching a visible mining face";farm.breaking().setBreaking(false);return true;
 }
 private VeinTargetPlanner.Choice prove(net.minecraft.core.BlockPos cell,Vec3 standing,net.minecraft.world.phys.Vec3 eye,long time,Map<MiningMaterial,Integer> priorities){
  if(!loaded(cell))return null;var state=client.level.getBlockState(cell);var material=MiningMaterial.classify(id(state));
  if(material.isEmpty()||priorities.getOrDefault(material.get(),0)<=0||!taskAllows(cell,material.get())||!targetProblem(cell).isEmpty())return null;
  var point=visiblePoint(cell,eye);if(point.isEmpty()||queryIncomplete)return null;
  var option=new VeinTargetPlanner.Option(value(cell),id(state),standing,new Vec3(eye.x,eye.y,eye.z),point.get(),true,true,true,true,true,Math.max(0,state.getDestroySpeed(client.level,cell))*1000,time);
  return VeinTargetPlanner.choose(List.of(option),priorities,farm.player().position(),client.player.getYRot(),client.player.getXRot(),client.player.blockInteractionRange(),time,250,new VeinTargetPlanner.Weights(1,5,100)).orElse(null);
 }
 private Object scanScopeNow(){return List.of(player,world,connection,settings,client.player.getInventory().getSelectedSlot(),NativeStackIdentity.capture(client.player.getMainHandItem(),client.level.registryAccess()),McOwnedRotation.authority(client),McOwnedRotation.epoch(client),client.player.getYRot(),client.player.getXRot());}
 private void resetScan(){scanScope=null;scanOrigin=null;scanIndex=candidateIndex=0;scannedCandidates.clear();rankedScan=null;vantageScan=null;provisional=null;}
 private boolean query(){int count=++queryCount;long elapsed=count>4096?-1:System.nanoTime()-queryStarted;if(count>4096||elapsed>4_000_000){if(!queryIncomplete){queryFirstFailure=queryPhase;queryFirstFailureCount=count;queryFirstFailureElapsed=elapsed;}queryIncomplete=true;return false;}return true;}
 private boolean supportContact(net.minecraft.core.BlockPos cell){
  if(!query())return true;
  var body=client.player.getBoundingBox();var support=new net.minecraft.world.phys.AABB(body.minX+.0001,body.minY-.025,body.minZ+.0001,body.maxX-.0001,body.minY+.0001,body.maxZ-.0001);
  for(var box:client.level.getBlockState(cell).getCollisionShape(client.level,cell).toAabbs()){if(!query())return true;if(box.move(cell.getX(),cell.getY(),cell.getZ()).intersects(support))return true;}
  return !query();
 }
 private boolean postRemovalSafe(){var dimensions=client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING);return query()&&farm.world().bodyClear(farm.player().position(),dimensions.width()/2.0,dimensions.height())&&query()&&farm.world().hasGroundSupport(farm.player().position(),dimensions.width()/2.0)&&query();}
 private VeinBreakSession.Scope sessionScope(){return new VeinBreakSession.Scope(player,world,connection,this,McOwnedRotation.authority(client),settings,tool,slot,activation);}
 private VeinBreakSession.Frame frame(long time,net.minecraft.world.level.block.state.BlockState state,boolean receipt,long serial,boolean ray){
  return new VeinBreakSession.Frame(sessionScope(),value(target),id(state),loaded(target),state.isAir()||visiblePoint(target).isPresent(),ray,tool!=null&&(projectedWear||tool.matches(client.player.getMainHandItem(),client.level.registryAccess())),state.isAir()||mining.hasCapacity(value(target)),state.isAir()?postRemovalSafe():targetProblem(target).isEmpty(),client.gui.screen()==null,context(),receipt,serial,time);
 }
 private boolean allowsTarget(dev.veltraph.client.world.BlockPos actual){try{queryPhase="interaction-frame";queryStarted=System.nanoTime();queryCount=0;queryIncomplete=false;queryFirstFailure="none";queryFirstFailureCount=0;queryFirstFailureElapsed=0;return breaking&&target!=null&&session.permits(actual,frame(now(),client.level.getBlockState(target),false,MiningBlockReceipts.serial(),mining.aimedAt(actual)),now())&&value(target).equals(actual)&&context()&&loaded(target)
   &&client.level.getBlockState(target).equals(targetState)&&client.player.getInventory().getSelectedSlot()==slot&&tool.matches(client.player.getMainHandItem(),client.level.registryAccess())
   &&visiblePoint(target).isPresent()&&mining.aimedAt(actual)&&targetProblem(target).isEmpty()&&mining.hasCapacity(actual)&&mining.heldToolReady(actual,config.get(MiningConfig.MINING_DURABILITY_RESERVE))&&!queryIncomplete&&workCurrent();}catch(RuntimeException unavailable){return false;}}
 /** Only this registered current generation may release the settle navigator's held movement. */
 private void stopOwnedDropWalking(){
  var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();
  if(isEnabled()&&manager!=null&&manager.inputOwner().orElse(null)==this&&client.player==player&&client.level==world&&client.getConnection()==connection)mining.stopWalking();
 }
 private void releaseOwnedAdmissionAttack(){var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();if(isEnabled()&&manager!=null&&manager.inputOwner().orElse(null)==this&&client.player==player&&client.level==world&&client.getConnection()==connection){farm.breaking().setBreaking(false);breaking=false;}}
 private boolean toolActionReady(){return client.player.getInventory().getSelectedSlot()==slot&&(tool.matches(client.player.getMainHandItem(),client.level.registryAccess())||qualifiedWearTool!=null&&qualifiedWearTool.matches(client.player.getMainHandItem(),client.level.registryAccess()));}
 private boolean pickupCustody(){try{return context()&&wear!=null&&target!=null&&(tool.matches(client.player.getMainHandItem(),client.level.registryAccess())||qualifiedWearTool!=null&&qualifiedWearTool.matches(client.player.getMainHandItem(),client.level.registryAccess())||wear.pendingToolDamageOnly());}catch(RuntimeException unavailable){return false;}}
 private boolean adoptInventory(long time){
  if(wear==null||pickup==null){stop("Mining inventory receipt adapters unavailable");return false;}
  var actual=wear.actualInventory();if(inventoryBaseline.equals(actual))return true;
  var concreteWear=wear.prepareWear().orElse(null);VeinInventoryAdoption.Permission wearPermission=null;java.util.List<?> projected=inventoryBaseline;
  if(!Objects.equals(inventoryBaseline.get(slot),actual.get(slot))){
   if(concreteWear==null){if(wear.pendingToolDamageOnly()){stopOwnedDropWalking();farm.breaking().setBreaking(false);reason="Waiting for actual mining wear receipt";return false;}stop("Unqualified mining tool change");return false;}
   wearPermission=wear.wrapWear(concreteWear).orElse(null);if(wearPermission==null||!pickup.projectWear(concreteWear,inventoryBaseline,concreteWear.after())){stop("Mining wear projection revoked ["+wear.cachedDiagnostic()+"; "+pickup.cachedDiagnostic()+"]");return false;}projected=concreteWear.after();
  }
  var pickupPermission=projected.equals(actual)?null:pickup.asPermission(projected,actual,attempt).orElse(null);
  if(!projected.equals(actual)&&pickupPermission==null){if(pickup.pendingExact(projected,actual)){stopOwnedDropWalking();farm.breaking().setBreaking(false);reason="Waiting for attributed mining pickup chain";return false;}stop("Inventory changed without exact mining pickup receipts");return false;}
  var old=inventoryBaseline;var finalActual=List.copyOf(actual);boolean accepted=VeinInventoryAdoption.adopt(old,actual,slot,wearPermission,pickupPermission,()->context()&&time<wear.deadline()&&wear.actualInventory().equals(finalActual),()->{if(inventoryBaseline!=old)return false;inventoryBaseline=finalActual;return true;});
  if(!accepted){stop("Joint mining wear/pickup inventory adoption revoked");return false;}
  if(!Objects.equals(old.get(slot),actual.get(slot))){qualifiedWearTool=NativeStackIdentity.capture(client.player.getMainHandItem(),client.level.registryAccess());projectedWear=true;if(!pickup.retainAdoptedWear(wear.adoptedWear().orElse(null))){stop("Adopted wear receipt retention failed");return false;}}
  if(cleanupInventory!=null&&cleanupInventory.selectionCurrent()&&context()&&wear.actualInventory().equals(finalActual))cleanupInventory=new CleanupInventory(this,cleanupInventory.selection);
  return true;
 }
 boolean wearContext(String exactSettings){try{return context()&&settings.toString().equals(exactSettings)&&target!=null;}catch(RuntimeException unavailable){return false;}}
 private void stop(String why){reason=why;if(mining.collectionCleanupTyped())mining.cleanup();setEnabled(false);}
 /** Exact native service identity only; no lifecycle/action authority is granted. */
 /** Native tool and all36 data follow actual original selection and accepted wear/pickup receipts. */
 static final class CleanupInventory {
  final VeinMiningModule module;final int activation,original,selected;final Object player,world,connection;
  final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection;
  final java.util.List<net.minecraft.world.item.ItemStack> stacks;
  private CleanupInventory(VeinMiningModule module,dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection){this(module,selection,null);}
  private CleanupInventory(VeinMiningModule module,dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection,java.util.List<net.minecraft.world.item.ItemStack> expected){
   this.module=module;this.selection=selection;activation=module.activation;original=module.originalSlot;selected=module.slot;player=module.player;world=module.world;connection=module.connection;
   var copies=new ArrayList<net.minecraft.world.item.ItemStack>(36);for(int i=0;i<36;i++)copies.add((expected==null?module.client.player.getInventory().getItem(i):expected.get(i)).copy());stacks=List.copyOf(copies);
  }
  boolean selectionCurrent(){return selection!=null&&selection.current();}
  boolean nativeCurrent(int expected,boolean ownSetterReturned){var c=module.client;
   if(module.cleanupInventory!=this||module.activation!=activation||module.originalSlot!=original||module.slot!=selected
    ||c.player!=player||c.level!=world||c.getConnection()!=connection||c.player==null||c.player.getInventory().getSelectedSlot()!=expected
    ||!ownSetterReturned&&!selectionCurrent())return false;
   for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(stacks.get(i),c.player.getInventory().getItem(i)))return false;
   return module.cleanupInventory==this&&module.activation==activation&&module.originalSlot==original&&module.slot==selected
    &&c.player==player&&c.level==world&&c.getConnection()==connection&&c.player.getInventory().getSelectedSlot()==expected
    &&(ownSetterReturned||selectionCurrent());
  }
 }
 /** Original registered enable handout; raw onEnable cannot mint or retain this receipt. */
 public static final class CollectionActivation {
  private final VeinMiningModule module;private final dev.veltraph.client.core.ModuleManager.OwnEnableReceipt receipt;
  private final int activation;private final Object player,world,connection,settings,options,gameMode;
  private final dev.veltraph.client.core.ModuleManager root;
  private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot config;
  private CollectionActivation(VeinMiningModule module,dev.veltraph.client.core.ModuleManager.OwnEnableReceipt receipt){this.module=module;this.receipt=receipt;activation=module.activation;player=module.player;world=module.world;connection=module.connection;settings=module.settings;options=module.client.options;gameMode=module.client.gameMode;root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
   var cfg=module.config;config=cfg instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot():cfg instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
  }
  public Object owner(){return module;}public Object receiptIdentity(){return receipt;}
  public boolean belongsTo(McMiningServices exact){return module.mining==exact&&module.collectionActivation==this;}
  /** Actual completed enable receipt is needed only for fresh admission, never inferred from activation. */
  public boolean registeredCurrent(McMiningServices exact){return belongsTo(exact)&&receipt.parent()==module&&receipt.current()&&module.isEnabled()&&config!=null&&config.current()&&cleanupCurrent();}
  public boolean cleanupNativeContext(){var c=module.client;return c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.options==options&&c.gameMode==gameMode
   &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&c.player!=null&&c.player.containerMenu==c.player.inventoryMenu&&c.player.inventoryMenu.getCarried().isEmpty();}
  public boolean cleanupCurrent(){var c=module.client;return module.collectionActivation==this&&module.activation==activation&&module.settings==settings
   &&module.player==player&&module.world==world&&module.connection==connection&&c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection
   &&c.options==options&&c.gameMode==gameMode&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&c.player!=null
   &&c.player.containerMenu==c.player.inventoryMenu&&c.player.inventoryMenu.getCarried().isEmpty();}
  public CollectionCleanupSlots cleanupSlots(){var actual=module.cleanupInventory;return cleanupCurrent()&&actual!=null&&actual.activation==activation&&actual.nativeCurrent(actual.selected,false)?new CollectionCleanupSlots(null,this,actual):null;}
 }
 public static final class CollectionCleanupSlots {
  private final CollectionSourceSnapshot source;private final CollectionActivation activation;private final CleanupInventory inventory;
  private CollectionCleanupSlots(CollectionSourceSnapshot source,CollectionActivation activation,CleanupInventory inventory){this.source=source;this.activation=activation;this.inventory=inventory;}
  public int selected(){return inventory.selected;}public int original(){return inventory.original;}
  public boolean current(int expected,boolean ownSetterReturned){return (source!=null?source.cleanupCurrent()&&source.cleanupInventory()==inventory:activation!=null&&activation.cleanupCurrent()&&activation.module.cleanupInventory==inventory)&&inventory.nativeCurrent(expected,ownSetterReturned);}
  public boolean restoredHistory(dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory actual){return actual!=null&&inventory.selection!=null&&actual.followsExactlyOneSetter(inventory.selection);}
 }
 /** Actual private admission/image captured before native selector callbacks; refusal only. */
 private static final class AdmissionSlotSource {
  final VeinMiningModule module;final int activation,original,slot,before,next;final long attempt,operation,deadline,floor;
  final Object candidate,target,wear,pickup,baseline,settings,player,world,connection;final CollectionActivation enable;final CleanupInventory cleanup;
  final ApproachInventoryImage image;final dev.veltraph.client.core.ModuleManager root;final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
  final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot config;final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot controls;
  final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
  final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection;
  private AdmissionSlotSource(VeinMiningModule module,int before,int next,dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection){
   this.module=module;this.before=before;this.next=next;this.selection=selection;activation=module.activation;attempt=module.attempt;operation=module.operationStarted;floor=module.lastNow;original=module.originalSlot;slot=module.slot;
   candidate=module.admissionCandidate;target=module.target;wear=module.wear;pickup=module.pickup;baseline=module.inventoryBaseline;settings=module.settings;player=module.player;world=module.world;connection=module.connection;enable=module.collectionActivation;cleanup=module.cleanupInventory;
   image=new ApproachInventoryImage(module.client);root=dev.veltraph.client.VeltraphBootstrap.moduleManager();lifecycle=root.lifecycleSnapshot();var cfg=module.config;
   config=cfg instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot():cfg instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
   controls=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();installation=McOwnedRotation.installationSnapshot(module.client);
   deadline=operation+(Long)module.settings.values().get(5); // Original immutable mining timeout entry.
  }
  private boolean fields(){var c=module.client;return module.isEnabled()&&module.activation==activation&&module.attempt==attempt&&module.operationStarted==operation
   &&module.originalSlot==original&&module.slot==slot&&module.admissionCandidate==candidate&&candidate!=null&&module.target==target&&module.wear==wear&&module.pickup==pickup
   &&module.inventoryBaseline==baseline&&module.settings==settings&&module.collectionActivation==enable&&module.cleanupInventory==cleanup
   &&module.player==player&&module.world==world&&module.connection==connection&&c.player==player&&c.level==world&&c.getConnection()==connection;}
  boolean current(int expected,boolean setterReturned){long at=now();var c=module.client;
   if(expected!=(setterReturned?next:before)||!fields()||!collectionNativeActionFlags(c)||root!=dev.veltraph.client.VeltraphBootstrap.moduleManager()
    ||lifecycle==null||!lifecycle.known()||!root.lifecycleCurrent(lifecycle)||config==null||!config.current()||controls==null||!controls.active()||!controls.current()
    ||installation==null||!installation.current()||selection==null||!image.nativeCurrent(expected,setterReturned))return false;
   var actual=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(c.player.getInventory());
   boolean selected=setterReturned?actual!=null&&actual.followsExactlyOneSetter(selection):selection.current();
   return selected&&fields()&&collectionNativeActionFlags(c)&&root.lifecycleCurrent(lifecycle)&&config.current()&&controls.current()&&installation.current()
    &&operation>=0&&at>=operation&&at>=floor&&at<deadline&&now()>=at&&now()<deadline;
  }
 }
 /** Direct SDK native flags only; this refusal is never used to release owned cleanup. */
 private static boolean collectionNativeActionFlags(Minecraft c){var p=c.player;return c.isSameThread()&&p!=null&&c.level!=null&&c.gameMode!=null
  &&p.level()==c.level&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&!p.getAbilities().flying&&!p.isScoping()
  &&p.getPose()==net.minecraft.world.entity.Pose.STANDING&&p.onGround()&&c.gui.screen()==null&&!c.isPaused()&&c.getCameraEntity()==p
  &&!c.level.tickRateManager().isEntityFrozen(p)&&p.inventoryMenu!=null&&p.containerMenu==p.inventoryMenu&&p.inventoryMenu.getCarried().isEmpty();}
 /** Read-only native image for movement before any actual source-owned tool selection. No gear grant. */
 private static final class ApproachInventoryImage {
  final Minecraft client;final Object player,world,connection,inventory,menu;final int selected;
  final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection;
  final java.util.List<net.minecraft.world.item.ItemStack> stacks;
  private ApproachInventoryImage(Minecraft client){this.client=client;player=client.player;world=client.level;connection=client.getConnection();inventory=client.player.getInventory();menu=client.player.inventoryMenu;selected=client.player.getInventory().getSelectedSlot();
   selection=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());var copies=new ArrayList<net.minecraft.world.item.ItemStack>(36);for(int i=0;i<36;i++)copies.add(client.player.getInventory().getItem(i).copy());stacks=List.copyOf(copies);
  }
  boolean nativeCurrent(){return nativeCurrent(selected,false);}
  boolean nativeCurrent(int expected,boolean setterReturned){if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.player.getInventory()!=inventory||client.player.inventoryMenu!=menu
   ||client.player.containerMenu!=menu||!client.player.inventoryMenu.getCarried().isEmpty()||client.player.getInventory().getSelectedSlot()!=expected||selection==null||!setterReturned&&!selection.current())return false;
   for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(stacks.get(i),client.player.getInventory().getItem(i)))return false;
   var actual=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
   return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player.getInventory().getSelectedSlot()==expected
    &&(setterReturned?actual!=null&&actual.followsExactlyOneSetter(selection):selection.current());
  }
 }
 /** Exact private default approach; original operation/deadline and search goals never rearm. */
 public static final class CollectionParentApproach {
  private final VeinMiningModule module;private final int activation;private final long operation,deadline,floor;
  private final Object player,world,connection,settings;private final dev.veltraph.client.world.BlockPos standing;private final CleanupInventory inventory;private final ApproachInventoryImage image;
  private final CollectionActivation enable;private final dev.veltraph.client.core.ModuleManager root;private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
  private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot config;private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot controls;
  private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
  private CollectionParentApproach(VeinMiningModule module){this.module=module;activation=module.activation;operation=module.operationStarted;floor=module.lastNow;
   standing=module.approach;player=module.player;world=module.world;connection=module.connection;settings=module.settings;inventory=module.cleanupInventory;image=new ApproachInventoryImage(module.client);enable=module.collectionActivation;
   root=dev.veltraph.client.VeltraphBootstrap.moduleManager();lifecycle=root.lifecycleSnapshot();var cfg=module.config;
   config=cfg instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot():cfg instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
   controls=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();installation=McOwnedRotation.installationSnapshot(module.client);
   deadline=operation+cfg.get(MiningConfig.MINING_BREAK_TIMEOUT_MS);
  }
  public dev.veltraph.client.world.BlockPos standing(){return standing;}public Object owner(){return module;}public CollectionActivation activation(){return enable;}
  public boolean current(){long at=now();return currentAt(at)&&now()>=at&&now()<deadline;}
  public boolean currentAt(long at){var c=module.client;return collectionNativeActionFlags(c)&&module.isEnabled()&&module.activation==activation&&module.operationStarted==operation
   &&operation>=0&&at>=operation&&at>=floor&&at<deadline&&module.approach==standing&&standing!=null&&module.player==player&&module.world==world&&module.connection==connection
   &&module.settings==settings&&module.collectionActivation==enable&&c.player==player&&c.level==world&&c.getConnection()==connection
   &&root==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&lifecycle!=null&&lifecycle.known()&&root.lifecycleCurrent(lifecycle)&&config!=null&&config.current()
   &&controls!=null&&controls.active()&&controls.current()&&installation!=null&&installation.current()&&image.nativeCurrent()&&(inventory==null||module.cleanupInventory==inventory&&inventory.nativeCurrent(inventory.selected,false))
   &&module.activation==activation&&module.operationStarted==operation&&module.approach==standing&&module.settings==settings&&c.player==player&&c.level==world&&c.getConnection()==connection
   &&root.lifecycleCurrent(lifecycle)&&config.current()&&controls.current()&&installation.current()&&collectionNativeActionFlags(c);}
 }
 private CollectionParentApproach collectionParentApproach(McMiningServices exact){
  if(exact!=mining||approach==null||operationStarted<0||collectionActivation==null||dev.veltraph.client.VeltraphBootstrap.moduleManager()==null)return null;
  var actual=new CollectionParentApproach(this);if(!context())return null;return actual.current()?actual:null;
 }
 /** Authentic private operation stamp; final actuator veto only, never an admission grant. */
 public static final class CollectionParentOperation {
  private final VeinMiningModule module;private final int activation;private final long attempt,operation,deadline,floor;
  private final Object target,wear,baseline,settings,player,world,connection,aim;private final CleanupInventory inventory;
  private final dev.veltraph.client.core.ModuleManager root;private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
  private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot config;
  private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot controls;
  private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
  private CollectionParentOperation(VeinMiningModule module){this.module=module;activation=module.activation;attempt=module.attempt;operation=module.operationStarted;deadline=module.wear.deadline();floor=module.lastNow;target=module.target;wear=module.wear;baseline=module.inventoryBaseline;settings=module.settings;player=module.player;world=module.world;connection=module.connection;aim=module.aim;inventory=module.cleanupInventory;
   root=dev.veltraph.client.VeltraphBootstrap.moduleManager();lifecycle=root.lifecycleSnapshot();var cfg=module.config;
   config=cfg instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot():cfg instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
   controls=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();installation=McOwnedRotation.installationSnapshot(module.client);
  }
  public boolean current(){long at=now();return currentAt(at)&&now()>=at&&now()<deadline;}
  public boolean currentAt(long at){var c=module.client;return collectionNativeActionFlags(c)&&module.isEnabled()&&module.activation==activation&&module.attempt==attempt
   &&module.operationStarted==operation&&operation>=0&&at>=operation&&at>=floor&&at<deadline&&module.target==target&&target!=null&&module.wear==wear
   &&module.inventoryBaseline==baseline&&module.settings==settings&&!module.settling&&module.player==player&&module.world==world&&module.connection==connection
   &&c.player==player&&c.level==world&&c.getConnection()==connection&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
   &&lifecycle!=null&&lifecycle.known()&&root.lifecycleCurrent(lifecycle)&&module.aim==aim
   &&config!=null&&config.current()&&controls!=null&&controls.active()&&controls.current()&&installation!=null&&installation.current()
   &&inventory!=null&&inventory.nativeCurrent(inventory.selected,false)&&root.lifecycleCurrent(lifecycle)&&config.current()&&controls.current()
   &&module.activation==activation&&module.attempt==attempt&&module.operationStarted==operation&&module.target==target&&module.wear==wear&&module.inventoryBaseline==baseline
   &&module.settings==settings&&module.aim==aim&&!module.settling&&module.isEnabled()
   &&c.player==player&&c.level==world&&c.getConnection()==connection&&installation.current()&&collectionNativeActionFlags(c);}
 }
 private CollectionParentOperation collectionParentOperation(McMiningServices exact){
  if(exact!=mining||target==null||wear==null||inventoryBaseline==null||settling||cleanupInventory==null||dev.veltraph.client.VeltraphBootstrap.moduleManager()==null)return null;
  // Save exact native/source stamps before the original provider-bearing context checks.
  var actual=new CollectionParentOperation(this);if(!context())return null;return actual.current()?actual:null;
 }
 /** Private actual operation stamp; raw enable/reset or a new source cannot reuse a collection token. */
 static final class CollectionSourceSnapshot {
  private final VeinMiningModule module;private final int activation;private final long attempt,operation,deadline;
  private final Object player,world,connection,settings,wear,baseline;private final net.minecraft.core.BlockPos target;
  private final VeinNativePickupAdapter pickup;private final int cleanupSlot,cleanupOriginalSlot;private final CollectionActivation registeredActivation;
  private CollectionSourceSnapshot(VeinMiningModule module,VeinNativePickupAdapter pickup,long deadline){this.module=module;this.pickup=pickup;this.deadline=deadline;
   cleanupSlot=module.slot;cleanupOriginalSlot=module.originalSlot;registeredActivation=module.collectionActivation;
   activation=module.activation;attempt=module.attempt;operation=module.operationStarted;player=module.player;world=module.world;connection=module.connection;
   settings=module.settings;wear=module.wear;baseline=module.inventoryBaseline;target=module.target;}
  /** Original workflow identity permits cleanup after settlement/teardown, never gameplay. */
  boolean cleanupCurrent(){return module.activation==activation&&module.player==player&&module.world==world&&module.connection==connection
   &&module.settings==settings&&module.originalSlot==cleanupOriginalSlot&&module.collectionActivation==registeredActivation;}
  CleanupInventory cleanupInventory(){var actual=module.cleanupInventory;return cleanupCurrent()&&actual!=null&&actual.original==cleanupOriginalSlot&&actual.activation==activation?actual:null;}
  boolean activationMatches(CollectionActivation exact){return exact!=null&&registeredActivation==exact&&cleanupCurrent();}
  CollectionCleanupSlots cleanupSlots(){var actual=cleanupInventory();return actual!=null&&actual.nativeCurrent(actual.selected,false)?new CollectionCleanupSlots(this,null,actual):null;}
  int cleanupSlot(){var actual=cleanupInventory();return actual==null?-1:actual.selected;}int cleanupOriginalSlot(){return cleanupOriginalSlot;}
  boolean current(){return module.activation==activation&&module.attempt==attempt&&module.operationStarted==operation&&operation>=0
   &&module.player==player&&module.world==world&&module.connection==connection&&module.settings==settings&&module.wear==wear&&wear!=null
   &&module.inventoryBaseline==baseline&&module.target==target&&target!=null&&module.pickup==pickup&&module.settling
   &&((VeinNativeWearBridge)wear).deadline()==deadline;}
 }
 CollectionSourceSnapshot collectionSourceSnapshot(VeinNativePickupAdapter exact,long deadline){
  if(pickup!=exact||exact==null||wear==null||target==null||!settling||operationStarted<0||wear.deadline()!=deadline)return null;
  var snapshot=new CollectionSourceSnapshot(this,exact,deadline);return snapshot.current()?snapshot:null;
 }
 private static final Object NON_COLLECTION_PARENT=new Object();
 /** Before callbacks: private actual collection association and Root-owned Leg, refusal only. */
 public static Object nativeCollectionTravelSnapshot(Minecraft client,Object parent,Object exactLeg){
  if(!(parent instanceof VeinMiningModule vein))return NON_COLLECTION_PARENT;
  return vein.client==client?vein.mining.collectionTravelSnapshot(exactLeg):null;
 }
 /** Field/history-only source leaf. Original Core independently samples actual monotonic time. */
 public static boolean nativeCollectionTravelSnapshotCurrent(Minecraft client,Object parent,Object exactLeg,Object exact,long actualNow){
  if(!(parent instanceof VeinMiningModule vein))return exact==NON_COLLECTION_PARENT;
  return vein.client==client&&vein.mining.collectionTravelSnapshotCurrent(exactLeg,exact,actualNow);
 }
 public boolean ownsCollectionService(dev.veltraph.client.mining.mc.McMiningServices exact){return mining==exact;}
 public int removed(){return removed;}
 @Override public String diagnostics(){return "vein_mining "+reason+" | removed="+removed+" activation="+activation+" contextChecks="+contextChecks+" contextFailure="+contextFailure+" geometryFailure="+queryFirstFailure+" geometryQueries="+queryFirstFailureCount+" geometryElapsedNs="+queryFirstFailureElapsed;}
}
