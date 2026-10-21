package dev.veltraph.client.farming.module;
import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FarmSuspensionHookRevocationTest {
 static final class Farm extends FarmModule {
  boolean disableInHook=true,nesting;Object nested=new Object();
  Farm(){super("hook-test","Hook test",new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),new RecordingInputService(),new FakeRotationService(),new RecordingBlockBreaking(),new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());}
  public FarmLayoutProfile activeProfile(){return registry.byId(FarmLayoutRegistry.WART_CROPS_SSHAPE).orElseThrow();}public String debugState(){return "hook";}protected List<String> toolKeywords(){return List.of("hoe");}protected void onTickFarming(){}
  protected void onBeforeFarmSuspension(){if(disableInHook)setEnabled(false);else if(!nesting){nesting=true;assertTrue(suspendForMaintenance(nested));}}
 }
 @Test void disablingCancellationHookCannotLatchMaintenanceToken(){var f=new Farm();f.setEnabled(true);var token=new Object();assertFalse(f.suspendForMaintenance(token));assertFalse(f.isSuspendedForMaintenance());assertFalse(f.maintenanceOwnedBy(token));}
 @Test void disablingCancellationHookCannotLatchCycleOrRewarp(){var f=new Farm();f.setEnabled(true);assertFalse(f.suspendForCycleReset());assertFalse(f.isSuspendedForCycleReset());f.setEnabled(true);f.suspendForRewarp();assertFalse(f.isSuspendedForRewarp());}
 @Test void nestedHookLeaseCannotBeOverwrittenByOuterToken(){var f=new Farm();f.disableInHook=false;f.setEnabled(true);var outer=new Object();assertFalse(f.suspendForMaintenance(outer));assertTrue(f.maintenanceOwnedBy(f.nested));assertFalse(f.maintenanceOwnedBy(outer));}
}
