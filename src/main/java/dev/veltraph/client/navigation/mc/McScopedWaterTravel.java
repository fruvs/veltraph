package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.core.Module;
import java.util.Optional;
/** Actual registered Root WATER namespace. Opaque farm roles require their separate native bridge. */
public final class McScopedWaterTravel {
 private final Minecraft client;private final FarmServices services;private final ConfigService config;private final LegacyTravelPublicationLease leases;
 private McNavigator navigator;private LegacyTravelPublicationLease.Leg leg;private long journey;private boolean external,attempted,helperCleanupFailed;private Throwable retirementFailure;
 private LegacyTravelRetirement.RetirementOutcome retirement;private LegacyTravelPublicationLease.Leg retiredLeg;private boolean lifecycleRefused;private String reason="Idle";
 public McScopedWaterTravel(Minecraft client,FarmServices services,ConfigService config){this.client=java.util.Objects.requireNonNull(client);this.services=java.util.Objects.requireNonNull(services);this.config=java.util.Objects.requireNonNull(config);leases=new LegacyTravelPublicationLease(client,services,config,()->config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1L));}
 public boolean startWater(Vec3 goal){return start(null,goal,0,Double.NaN,null,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);}
 public boolean startWater(Vec3 goal,double radius){return start(null,goal,0,radius,null,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);}
 public boolean startWater(Module expectedParent,Vec3 goal,double radius){return start(expectedParent,goal,0,radius,null,SharedMovementPolicy.Purpose.EXACT_RETURN);}
 public boolean startWater(Module expectedParent,Vec3 goal,SharedMovementPolicy.Purpose purpose){return start(expectedParent,goal,0,Double.NaN,null,purpose);}
 public boolean startDrop(Vec3 goal,int maxDrop){return maxDrop>=1&&maxDrop<=32&&start(null,goal,maxDrop,Double.NaN,null,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);}
 /** Native fixture/enclosing Root owner supplies its actual concrete leg and owns final retirement. */
 public boolean startBound(LegacyTravelPublicationLease.Leg supplied,Vec3 goal,int maxDrop,double radius){return supplied!=null&&supplied.parent() instanceof Module parent&&start(parent,goal,maxDrop,radius,supplied,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);}
 private boolean start(Module expectedParent,Vec3 goal,int drop,double radius,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose){
  if(lifecycleRefused||purpose==null||!client.isSameThread()||goal==null||!Double.isFinite(goal.x())||!Double.isFinite(goal.y())||!Double.isFinite(goal.z())||drop<0||drop>32
      ||!Double.isNaN(radius)&&(!Double.isFinite(radius)||radius<.05||radius>.3))return false;
  var predecessor=leg;stop();if(helperCleanupFailed||retirementFailure!=null||retirement!=null&&!retirement.cleanSuccess()){reason="Prior actual WATER cleanup unconfirmed";return false;}
  external=supplied!=null;leg=external?supplied:predecessor!=null?leases.beginAfter(++journey,purpose,goal,predecessor):expectedParent==null?leases.begin(++journey,purpose,goal):leases.beginExpected(++journey,expectedParent,purpose,goal);
  if(leg==null||!leg.current()||expectedParent!=null&&leg.parent()!=expectedParent){reason="Actual registered Root WATER custody or opaque-role bridge required";if(leg!=null&&!external)retirement=leg.retireOwned();leg=null;return false;}
  attempted=true;var input=new LeaseWaterAdapter(leg);navigator=new McNavigator(client,input,config,input);
  boolean accepted=drop>0?navigator.startDrop(goal,drop):Double.isFinite(radius)?navigator.startWater(goal,radius):navigator.startWater(goal);
  reason=navigator.diagnostics();if(!accepted)stop();return accepted;
 }
 public WalkController.State tick(){if(navigator==null||leg==null||!leg.current()){stop();return WalkController.State.BLOCKED;}var state=navigator.tick();if(state==WalkController.State.BLOCKED)stop();return state;}
 public Optional<McNavigator.WaterTerminalHold> retainWaterTerminalHold(Object owner,long originalDeadline){return navigator!=null&&leg!=null&&owner==leg.parent()&&leg.current()?navigator.retainWaterTerminalHold(owner,originalDeadline):Optional.empty();}
 public WalkController.State tickWaterTerminalHold(McNavigator.WaterTerminalHold hold,Object owner){if(navigator==null||leg==null||owner!=leg.parent()||!leg.current()){stop();return WalkController.State.BLOCKED;}return navigator.tickWaterTerminalHold(hold,owner);}
 /** Source-bounded saved view stays in WATER's actual own publication lineage during terminal hold. */
 public boolean trackWaterTerminalView(McNavigator.WaterTerminalHold hold,Object owner,float yaw,float pitch){return navigator!=null&&leg!=null&&owner==leg.parent()&&leg.current()&&navigator.trackWaterTerminalView(hold,owner,yaw,pitch);}
 /** Account actual required nine keys/camera FIRST, then reset private native helper under closed leg. */
 public void stopEffects(){
  var own=leg;try{if(own!=null)retirement=own.retireOwned();}
  catch(RuntimeException|Error failure){retirementFailure=failure;lifecycleRefused=true;}
  finally{if(own!=null)retiredLeg=own;if(retirement!=null&&!retirement.cleanSuccess())lifecycleRefused=true;try{if(navigator!=null)navigator.stop();}catch(RuntimeException|Error failure){helperCleanupFailed=true;lifecycleRefused=true;}}
 }
 public void stop(){try{stopEffects();}finally{navigator=null;leg=null;}}
 public void stopChecked(){stop();if(attempted&&!wholeCleanupSucceeded()){lifecycleRefused=true;throw new WaterPublicationRevokedException();}}
 /** Actual native lifecycle proof; no caller Boolean or raw epoch can authorize parent completion. */
 public boolean parentLifecycleCurrent(Module parent,boolean inventory){
  if(!attempted)return true;var own=leg!=null?leg:retiredLeg;if(own==null||own.parent()!=parent)return false;
  if(leg!=null&&leg.retirementOutcome()==null)return inventory?leg.inventoryCleanupCurrent():leg.cleanupCurrent();
  var context=own.retiredCleanupParentContext();if(context==null&&!inventory)context=own.postRetirementOwnParentContext();
  return context!=null&&context.parent()==parent&&context.player()==client.player&&context.world()==client.level&&context.connection()==client.getConnection()&&context.config()==config;
 }
 public boolean lifecycleRefused(){return lifecycleRefused;}public void refuseLifecycle(){lifecycleRefused=true;}
 public boolean hasLeg(){return leg!=null;}public boolean handledWaterCleanup(){return attempted;}
 public LegacyTravelRetirement.RetirementOutcome actualRetirementOutcome(){return retirement;}
 public boolean wholeCleanupSucceeded(){return retirement!=null&&retirement.cleanSuccess()&&!helperCleanupFailed&&retirementFailure==null;}
 public String diagnostics(){return navigator==null?reason:navigator.diagnostics();}public String status(){return diagnostics();}
}
