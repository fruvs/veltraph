package dev.veltraph.client.farming.module;
import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.farming.movement.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WheatCompletionCallerTest {
 static class Port implements CropCompletionPort {
  int ticks,cancels,rowObservations;boolean active=true;Result result=Result.DENIED;
  public Result tick(boolean allowed,float yaw,float pitch){throw new AssertionError("Legacy rotating tick must not be called by fixed S-shape");}
  public Result tickFixedRowView(boolean allowed,float yaw,float pitch){ticks++;return result;}
  public void observePrimaryRowTick(float yaw,TurnDirection side,boolean farming){rowObservations++;}
  public void cancel(){cancels++;active=false;}public boolean active(){return active;}
  public FarmMaintenanceState maintenanceState(){return FarmMaintenanceState.of(this,"active",active,"result",result);}
 }
 record Setup(WartCropsFarmModule module,Port port,RecordingInputService input,MapConfigService config) {}
 Setup setup(){var cfg=new MapConfigService();cfg.set(FarmingConfig.WHEAT_REACH_COMPLETION,true);var input=new RecordingInputService();var module=new WartCropsFarmModule(new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),input,new FakeRotationService(),new RecordingBlockBreaking(),new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());module.setVariety(WartVariety.WHEAT);var port=new Port();module.setReachCompletionPort(port);module.setEnabled(true);port.active=true;return new Setup(module,port,input,cfg);}
 @Test void deniedActualCallerTickNeverDrivesRowEngine(){var s=setup();var before=s.module.engine().maintenanceState();s.module.onTick();assertEquals(1,s.port.ticks);assertEquals(0,s.port.rowObservations);assertEquals(before,s.module.engine().maintenanceState());assertFalse(s.input.forward||s.input.left||s.input.right);}
 @Test void ownedActualCallerTickNeverDrivesRowEngine(){var s=setup();s.port.result=CropCompletionPort.Result.OWNED;var before=s.module.engine().maintenanceState();s.module.onTick();assertEquals(before,s.module.engine().maintenanceState());}
 @Test void activeCompletionDeniesMaintenanceAdmission(){var s=setup();assertTrue(s.module.maintenanceEngineReadinessError().orElseThrow().contains("reach completion"));}
 @Test void DirectMaintenanceSuspensionCancelsBeforeResume(){var s=setup();int before=s.port.cancels;var token=new Object();assertTrue(s.module.suspendForMaintenance(token));assertEquals(before+1,s.port.cancels);assertFalse(s.port.active);assertTrue(s.module.resumeAfterMaintenance(token));assertFalse(s.port.active);}
 @Test void cycleAndRewarpSuspendCancelRatherThanRestoreOldView(){var s=setup();int before=s.port.cancels;s.module.suspendForRewarp();assertEquals(before+1,s.port.cancels);assertFalse(s.port.active);s.module.resumeAfterRewarp();s.port.active=true;before=s.port.cancels;assertTrue(s.module.suspendForCycleReset());assertEquals(before+1,s.port.cancels);}
 @Test void lateDesignChangeInActualCallerRevokesRotationAttackAndRowFallthrough(){
  var s=setup();var rotations=new java.util.concurrent.atomic.AtomicInteger();var attacks=new java.util.concurrent.atomic.AtomicInteger();
  s.module.setEnabled(false);
  var port=new Port(){@Override public Result tickFixedRowView(boolean allowed,float yaw,float pitch){ticks++;
   boolean rotated=CropCompletionCommit.rotateAfterObservation(()->{s.config.set(FarmingConfig.WART_CROPS_DESIGN,WartCropsDesign.ACE_TWO_KEY_LOOP);return true;},s.module::reachCompletionEligible,()->{rotations.incrementAndGet();attacks.incrementAndGet();});
   return rotated?Result.OWNED:Result.DENIED;
  }};
  s.module.setReachCompletionPort(port);s.module.setEnabled(true);port.active=true;assertTrue(s.module.reachCompletionEligible());
  var before=s.module.engine().maintenanceState();s.module.onTick();assertEquals(1,port.ticks);assertEquals(0,rotations.get());assertEquals(0,attacks.get());assertEquals(before,s.module.engine().maintenanceState());assertFalse(s.input.forward||s.input.left||s.input.right);
 }

 @Test void forwardOnlyFieldReturnsIdleAndActualCallerRunsPrimaryRowTick(){
  var s=setup();s.module.setEnabled(false);var port=new Port(){@Override public Result tickFixedRowView(boolean allowed,float yaw,float pitch){ticks++;var eye=new dev.veltraph.client.world.Vec3(57.5,71.62,.5);var forward=new ReachableMatureCropSelector.Crop(new dev.veltraph.client.world.BlockPos(58,71,0),new dev.veltraph.client.world.Vec3(58.5,71.65,.5),true,true,true,true);return ReachableMatureCropSelector.selectMissed(new ReachableMatureCropSelector.Scope(this,this,this,this,1,1),eye,4.5,java.util.List.of(forward),null,-90).isEmpty()?Result.IDLE:Result.OWNED;}};
  s.module.setReachCompletionPort(port);s.module.setEnabled(true);port.active=true;var before=s.module.engine().maintenanceState();s.module.onTick();assertEquals(1,port.ticks);assertEquals(1,port.rowObservations);assertNotEquals(before,s.module.engine().maintenanceState());
 }

}
