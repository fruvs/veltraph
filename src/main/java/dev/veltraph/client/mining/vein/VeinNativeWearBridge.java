package dev.veltraph.client.mining.vein;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;
import dev.veltraph.client.galatea.FigToolWearLedger.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.BlockBreakingService;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Separate vein submission/inventory observation. Existing foraging bridge is unchanged. */
public final class VeinNativeWearBridge {
 private static volatile VeinNativeWearBridge active;
 private String lastFailure="none";private volatile boolean observationFailure;private VeinCompletionFence completion;private boolean inventoryQualified;
 private final Minecraft client;private final VeinMiningModule owner;private final BlockBreakingService breaking;
 private final Scope scope;private final long generation,epoch,deadline;private final String settings;private final int slot;
 private final net.minecraft.core.BlockPos target;private final BlockState original;
 private final List<Cell> initial;private final VeinWearFence fence;
 private java.util.function.Consumer<Action> submissionListener;
 private AdoptedWear adoptedWear;
 // Passive, bounded original-boundary observations. No authority, clock or geometry queries.
 private final ArrayDeque<String> boundaryEvents=new ArrayDeque<>();
 private final Map<String,String> firstBoundaryFailures=new LinkedHashMap<>();
 private long diagnosticSerial;
 private final Map<String,String> firstNativeOperations=new LinkedHashMap<>(),lastNativeOperations=new LinkedHashMap<>();
 private static void diagnostic(Runnable observation){try{observation.run();}catch(Throwable ignored){/* Diagnostics never alter original gameplay transitions. */}}
 private String cachedBoundary(String event){return event+" action="+(action==null?"none":action.issueSerial()+"/"+action.stopSerial())+" nativeSequences="+(action==null?"none":action.startSequence()+"/"+action.stopSequence())+" ack="+(ack==null?"none":ack.serial())+" serial="+serial+" deliveries="+deliveries.size();}
 private void boundary(String event){diagnostic(()->{boundaryEvents.addLast((++diagnosticSerial)+":"+cachedBoundary(event));if(boundaryEvents.size()>64)boundaryEvents.removeFirst();});}
 private void nativeOperation(String kind,String event){diagnostic(()->{var snapshot="eventOrdinal="+(diagnosticSerial+1)+":"+cachedBoundary(event);firstNativeOperations.putIfAbsent(kind,snapshot);lastNativeOperations.put(kind,snapshot);boundary(event);});}
 private boolean operand(String name,boolean accepted){if(!accepted)diagnostic(()->{firstBoundaryFailures.putIfAbsent(name,"action="+(action==null?"none":action.issueSerial()+"/"+action.stopSerial())+" ack="+(ack==null?"none":ack.serial())+" serial="+serial);boundary("denied:"+name);});return accepted;}
 private Object guard;private long guardAt,serial;private Action action;private Ack ack;
 private final Map<Object,List<Cell>> inventoryBefore=new IdentityHashMap<>();private final List<Applied> deliveries=new ArrayList<>();
 private record Applied(long serial,long at,List<Cell> before,List<Cell> after,boolean coversTool){}
 private VeinNativeWearBridge(Minecraft c,VeinMiningModule owner,BlockBreakingService breaking,long generation,long deadline,int slot,String settings,net.minecraft.core.BlockPos target){
  client=c;this.owner=owner;this.breaking=breaking;this.generation=generation;this.deadline=deadline;this.slot=slot;this.settings=settings;this.target=target.immutable();original=c.level.getBlockState(target);epoch=McOwnedRotation.epoch(c);
  scope=new Scope(owner,c.player,c.level,c.getConnection(),generation,this,generation);initial=cells();fence=new VeinWearFence(scope,generation,epoch,settings,deadline,slot,initial.stream().map(Cell::full).toList());
 }
 public static VeinNativeWearBridge bind(Minecraft c,VeinMiningModule owner,BlockBreakingService breaking,long generation,long deadline,int slot,String settings,net.minecraft.core.BlockPos target){
  if(active!=null)active.close();return active=new VeinNativeWearBridge(c,owner,breaking,generation,deadline,slot,settings,target);
 }
 public void onSubmitted(java.util.function.Consumer<Action> listener){if(!current())throw new IllegalStateException("Wear binding unavailable");submissionListener=java.util.Objects.requireNonNull(listener);}
 public static void observeSafely(Runnable observation){var b=active;if(b==null||!b.client.isSameThread())return;try{observation.run();}catch(Throwable unsupported){b.observationFailure=true;if(active==b)b.close();}}
 private static long now(){return System.nanoTime()/1_000_000;}
 private boolean current(){return observed("active",active==this)&&observed("observation-ok",!observationFailure)&&observed("client-thread",client.isSameThread())&&observed("deadline",now()<=deadline)&&observed("owner-context",owner.wearContext(settings))&&observed("player",client.player==scope.player())&&observed("world",client.level==scope.world())&&observed("connection",client.getConnection()==scope.connection())&&observed("slot",client.player.getInventory().getSelectedSlot()==slot)&&observed("camera-epoch",McOwnedRotation.epoch(client)==epoch);}
 private boolean observed(String operand,boolean value){if(!value&&"none".equals(lastFailure))lastFailure=operand;return operand(operand,value);}
 public String cachedDiagnostic(){return "wear="+lastFailure+" generation="+generation+" epoch="+epoch+" serial="+serial+" slot="+slot+" action="+(action==null?"none":action.issueSerial()+"/"+action.stopSerial())+" ack="+(ack==null?"none":ack.serial())+" deliveries="+deliveries.size()+" firstBoundaryFailures="+firstBoundaryFailures+" boundaryEvents="+boundaryEvents+" firstNativeOperations="+firstNativeOperations+" lastNativeOperations="+lastNativeOperations;}
 private Cell cell(int index){var stack=client.player.getInventory().getItem(index);var normalized=stack.copy();normalized.remove(DataComponents.DAMAGE);var tool=stack.get(DataComponents.TOOL);
  return new Cell(index,stack.getCount(),stack.getDamageValue(),NativeStackIdentity.capture(stack,client.level.registryAccess()),NativeStackIdentity.capture(normalized,client.level.registryAccess()),tool,tool==null?0:tool.damagePerBlock());}
 private List<Cell> cells(){var cells=new ArrayList<Cell>();for(int i=0;i<36;i++)cells.add(cell(i));return List.copyOf(cells);}
 public static void guardReturned(net.minecraft.client.multiplayer.MultiPlayerGameMode mode,net.minecraft.core.BlockPos target,boolean allowed){var b=active;if(b==null||!b.operand("guard.allowed",allowed)||!b.operand("guard.current",b.current())||!b.operand("guard.mode",mode==b.client.gameMode)||!b.operand("guard.target",b.target.equals(target))||!b.operand("guard.breaking",b.breaking.isBreaking()))return;
  var binding=b.breaking.targetGuardBinding(b.owner).orElse(null);if(!b.operand("guard.binding",binding!=null)||!b.operand("guard.bound",b.breaking.targetGuardBoundToCurrentContext(b.owner,binding))||!b.operand("guard.initial-tool",b.initial.get(b.slot).full().equals(b.cell(b.slot).full())))return;b.guard=binding;b.guardAt=now();diagnostic(()->b.boundary("guard-returned at="+b.guardAt));
 }
 public static void submitted(Connection transport,Packet<?> packet){var b=active;if(b==null||!(packet instanceof ServerboundPlayerActionPacket p)||!b.operand("submission.current",b.current())||!b.operand("submission.transport",transport==b.client.getConnection().getConnection())||!b.operand("submission.target",b.target.equals(p.getPos())))return;diagnostic(()->b.boundary("native-submitted action="+p.getAction()+" sequence="+p.getSequence()));
  if(p.getAction()==ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK){diagnostic(()->b.nativeOperation("ABORT","abort-before-clear nativeSequence="+p.getSequence()));b.action=null;b.ack=null;b.guard=null;return;}
  if(p.getAction()!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK&&p.getAction()!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)return;
  if(!b.operand("submission.guard",b.guard!=null)||!b.operand("submission.guard-age",now()-b.guardAt<=100)||!b.operand("submission.guard-bound",b.breaking.targetGuardBoundToCurrentContext(b.owner,(BlockBreakingService.TargetGuardBinding)b.guard)))return;
  long serial=++b.serial;long at=now();
  if(p.getAction()==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK){if(b.action!=null){b.boundary("duplicate-start-before-close");b.close();return;}b.completion=new VeinCompletionFence(at,b.deadline);b.action=new Action(b.scope,b.generation,serial,new BlockPos(b.target.getX(),b.target.getY(),b.target.getZ()),b.original,b.guard,b.slot,b.initial.get(b.slot),b.epoch,b.settings,b.deadline,at,p.getSequence(),0,-1);diagnostic(()->b.nativeOperation("START","start-recorded at="+at));}
  else if(b.operand("stop.action",b.action!=null)&&b.operand("stop.first",b.action.stopSerial()==0)){var a=b.action;b.action=new Action(a.scope(),a.attemptGeneration(),a.issueSerial(),a.target(),a.originalLogState(),a.targetGuard(),a.slot(),a.before(),a.epoch(),a.settings(),a.originalDeadline(),a.issued(),a.startSequence(),serial,p.getSequence());diagnostic(()->b.nativeOperation("STOP","stop-recorded at="+at));if(b.submissionListener!=null)b.submissionListener.accept(b.action);}
 }
 public static void airApplied(Object listener,net.minecraft.core.BlockPos target,BlockState state){var b=active;if(b==null||!b.operand("air.current",b.current())||!b.operand("air.listener",listener==b.client.getConnection())||!b.operand("air.target",target.equals(b.target))||!b.operand("air.is-air",state.isAir())||!b.operand("air.action",b.action!=null)||!b.operand("air.applied-state",b.client.level.getBlockState(target).equals(state)))return;
  if(b.ack==null){b.ack=new Ack(++b.serial,now(),-1,true);b.completion.air(b.ack.at());diagnostic(()->b.boundary("air-recorded at="+b.ack.at()));}b.join();
 }
 public static void inventoryBefore(Object listener,Object packet){var b=active;if(b!=null&&b.operand("inventory-before.current",b.current())&&b.operand("inventory-before.listener",listener==b.client.getConnection())&&b.operand("inventory-before.capacity",b.inventoryBefore.size()<16)){b.inventoryBefore.put(packet,b.cells());diagnostic(()->b.boundary("inventory-before-recorded type="+packet.getClass().getSimpleName()));}}
 public static void inventoryAfter(Object listener,Object packet){var b=active;if(b==null||!b.operand("inventory-after.current",b.current())||!b.operand("inventory-after.listener",listener==b.client.getConnection()))return;var before=b.inventoryBefore.remove(packet);if(!b.operand("inventory-after.before",before!=null))return;
  if(b.deliveries.size()>=16){b.boundary("inventory-delivery-overflow");b.close();return;}b.deliveries.add(new Applied(++b.serial,now(),before,b.cells(),b.packetCoversTool(packet)));diagnostic(()->{var delivered=b.deliveries.getLast();b.boundary("inventory-applied type="+packet.getClass().getSimpleName()+" at="+delivered.at+" damage="+delivered.before.get(b.slot).damage()+"->"+delivered.after.get(b.slot).damage()+" coversTool="+delivered.coversTool);});b.join();
 }
 private boolean packetCoversTool(Object packet){
  if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket p)return p.slot()==slot;
  if(packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket p){int nativeSlot=p.getSlot();return p.getContainerId()==-2?nativeSlot==slot:p.getContainerId()==0&&nativeSlot==36+slot;}
  return false;
 }
 private void join(){if(!operand("join.action",action!=null)||!operand("join.completion",completion!=null))return;for(var d:deliveries){var before=d.before.get(slot);var after=d.after.get(slot);if(d.at<action.issued())continue;
  boolean unchangedOthers=true;for(int i=0;i<36;i++)if(i!=slot&&!initial.get(i).full().equals(d.after.get(i).full()))unchangedOthers=false;
  if(!unchangedOthers&&before.full().equals(after.full()))continue;
  if(before.full().equals(after.full())&&initial.get(slot).full().equals(after.full())){boolean knownZero=initial.get(slot).damagePerBlock()==0&&!client.player.getInventory().getItem(slot).isDamageableItem();if(completion.unchangedInventory(d.at,d.coversTool,knownZero))inventoryQualified=true;continue;}
  if(!operand("join.air",ack!=null))continue;
  if(operand("join.stage",fence.stage(new Receipt(Evidence.CORRELATED_NATIVE_ACTION,action,ack,d.serial,d.at,before,after)))){completion.inventory(d.at,true);inventoryQualified=true;diagnostic(()->boundary("wear-staged delivery="+d.serial+" damage="+before.damage()+"->"+after.damage()));}}}
 public boolean unchangedOtherCells(){if(!current())return false;var actual=cells();for(int i=0;i<36;i++)if(i!=slot&&!initial.get(i).full().equals(actual.get(i).full()))return false;return true;}
 public boolean exactInitialTool(){return current()&&initial.get(slot).full().equals(cell(slot).full());}
 public boolean pendingToolDamageOnly(){if(!operand("pending.current",current())||!operand("pending.action",action!=null))return false;var before=initial.get(slot);var after=cell(slot);return before.withoutDamage().equals(after.withoutDamage())&&before.count()==after.count()&&after.damage()>before.damage()&&before.damagePerBlock()>0&&(long)after.damage()-before.damage()<=before.damagePerBlock();}
 public boolean pendingOnlyDamage(){if(!current()||action==null||!unchangedOtherCells())return false;var before=initial.get(slot);var after=cell(slot);return before.withoutDamage().equals(after.withoutDamage())&&before.count()==after.count()&&after.damage()>before.damage()&&before.damagePerBlock()>0&&(long)after.damage()-before.damage()<=before.damagePerBlock();}
 public boolean completionReady(){return current()&&completion!=null&&inventoryQualified&&completion.result(now(),true)==VeinCompletionFence.Result.READY;}
 public boolean consumeCompletion(){return current()&&completion!=null&&completion.consume(now(),true);}
 public java.util.Optional<Action> submittedOperation(){return current()&&action!=null&&action.stopSerial()>0?java.util.Optional.of(action):java.util.Optional.empty();}
 public java.util.Optional<Ack> appliedAir(){return current()?java.util.Optional.ofNullable(ack):java.util.Optional.empty();}
 public static final class AdoptedWear {private final VeinNativeWearBridge bridge;private final java.util.List<?> after;private AdoptedWear(VeinNativeWearBridge b,java.util.List<?> after){bridge=b;this.after=java.util.List.copyOf(after);}public boolean fresh(Object owner,Object player,Object world,Object connection,long generation,long epoch,String settings,long deadline){return bridge.current()&&bridge.owner==owner&&bridge.client.player==player&&bridge.client.level==world&&bridge.client.getConnection()==connection&&bridge.generation==generation&&bridge.epoch==epoch&&bridge.settings.equals(settings)&&bridge.deadline==deadline&&java.util.Objects.equals(after.get(bridge.slot),bridge.cell(bridge.slot).full());}public java.util.List<?> after(){return after;}}
 public java.util.Optional<AdoptedWear> adoptedWear(){return java.util.Optional.ofNullable(adoptedWear);}
 public java.util.Optional<dev.veltraph.client.galatea.FigToolWearLedger.Permit> prepareWear(){if(!current())return java.util.Optional.empty();return fence.prepare(scope,cells().stream().map(Cell::full).toList(),now(),epoch,settings,deadline);}
 public java.util.Optional<VeinInventoryAdoption.Permission> wrapWear(dev.veltraph.client.galatea.FigToolWearLedger.Permit permit){
  if(!observed("wrap-current",current())||!observed("wrap-permit-present",permit!=null)||!observed("wrap-bound",permit.boundTo(generation,epoch,settings,deadline))||!observed("wrap-matches",permit.matches(owner,client.player,client.level,client.getConnection(),generation,initialInventory(),permit.after())))return java.util.Optional.empty();
  return java.util.Optional.of(new VeinInventoryAdoption.Permission(){public boolean fresh(){return current()&&permit.boundTo(generation,epoch,settings,deadline)&&permit.matches(owner,client.player,client.level,client.getConnection(),generation,initialInventory(),permit.after());}public java.util.List<?> before(){return initialInventory();}public java.util.List<?> after(){return permit.after();}public boolean commit(java.util.function.BooleanSupplier cas){boolean accepted=fresh()&&fence.commitPrepared(permit,scope,()->fresh()&&cas.getAsBoolean());if(accepted)adoptedWear=new AdoptedWear(VeinNativeWearBridge.this,permit.after());return accepted;}});
 }
 public java.util.Optional<VeinInventoryAdoption.Permission> wearPermission(){if(!current())return java.util.Optional.empty();return fence.permission(scope,cells().stream().map(Cell::full).toList(),now(),epoch,settings,deadline);}
 public java.util.List<?> initialInventory(){return initial.stream().map(Cell::full).toList();}
 public java.util.List<?> actualInventory(){return cells().stream().map(Cell::full).toList();}
 public Scope pickupScope(){return scope;}public long generation(){return generation;}public long deadline(){return deadline;}public long epoch(){return epoch;}public int slot(){return slot;}public String settings(){return settings;}public Object originalTool(){return initial.get(slot).full();}
 public boolean acceptWear(){if(!current())return false;return fence.accept(scope,cells().stream().map(Cell::full).toList(),now(),epoch,settings,deadline);}
 public void close(){if(!client.isSameThread()){observationFailure=true;return;}boundary("close-before-clear");fence.retire();inventoryBefore.clear();deliveries.clear();guard=null;action=null;ack=null;if(active==this)active=null;}
}
