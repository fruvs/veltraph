package dev.veltraph.client.rewarp.mc;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.movement.device.McInstantTransmission;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Typed ownership boundaries and actual unchanged lease states; genuine loaded native interaction is separate. */
class LoadedReturnMaintenanceAuthorityBoundaryTest {
 @Test void restoredAuthorityCannotBeCreatedFromPublicValuesOrCopiedRecord(){
  var type=FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority.class;assertFalse(type.isRecord());
  assertTrue(Modifier.isFinal(type.getModifiers()));assertEquals(0,type.getConstructors().length);
  for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
 }
 @Test void startupAndBridgeCapsulesKeepPrivateConstruction()throws Exception{
  for(var type:new Class<?>[]{Class.forName(FarmMaintenanceCoordinator.class.getName()+"$LoadedReturnStartup"),
      Class.forName(McFarmMaintenanceAuthorityBridge.class.getName()+"$LoadedReturnOwnedUseSnapshot"),
      Class.forName(McFarmMaintenanceAuthorityBridge.class.getName()+"$LoadedReturnCleanupSnapshot")}){
   assertTrue(Modifier.isPrivate(type.getModifiers()));assertEquals(0,type.getConstructors().length);
   for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
  }
 }
 @Test void retirementConsumesConcreteNativeWitnessTypes()throws Exception{
  var type=FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority.class;
  assertEquals(boolean.class,type.getMethod("originalNormalSlotRestorationReturned",LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness.class).getReturnType());
  assertEquals(boolean.class,type.getMethod("originalNoLoanTerminalReturned",McInstantTransmission.TerminalCleanupReceipt.class).getReturnType());
 }
 @Test void actualLeaseStartupAndReadyRemainDistinctPhases(){
  var lease=new FarmMaintenanceLease();var token=lease.prepare(new Object(),new Object(),100,1000);
  assertTrue(lease.commit(token));assertFalse(lease.beginReturn(token));assertTrue(lease.beginRestore(token));
  assertEquals(FarmMaintenanceLease.State.RESTORING,lease.state());assertTrue(lease.beginReturn(token));
  assertEquals(FarmMaintenanceLease.State.RETURNING,lease.state());assertTrue(lease.ready(token));
  assertEquals(FarmMaintenanceLease.State.READY,lease.state());assertFalse(lease.beginReturn(token));
 }
 @Test void canceledLeaseCannotBeRearmedByPhaseOrReturnedTrue(){
  var lease=new FarmMaintenanceLease();var token=lease.prepare(new Object(),new Object(),100,1000);
  lease.commit(token);lease.beginRestore(token);lease.beginReturn(token);lease.cancel(token,"original cancel");
  assertFalse(lease.owns(token));assertFalse(lease.ready(token));assertFalse(lease.beginReturn(token));assertFalse(lease.deadlineCurrent(token,101));
 }
 @Test void missingTypedAuthorityCannotSeedBridgePositiveOrCleanupSnapshot(){
  assertNull(McFarmMaintenanceAuthorityBridge.captureLoadedReturnOwnedUseSnapshot(null,null,null,null));
  assertFalse(McFarmMaintenanceAuthorityBridge.loadedReturnOwnedUseSnapshotCurrent(null,null,null,new Object()));
  assertNull(McFarmMaintenanceAuthorityBridge.loadedReturnCleanupSnapshot(null,null,null,new Object()));
  assertFalse(McFarmMaintenanceAuthorityBridge.loadedReturnCleanupSnapshotCurrent(null,null,null,new Object(),new Object()));
 }
}
