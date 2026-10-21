package dev.veltraph.client.navigation.mc;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator;
import dev.veltraph.client.rewarp.mc.McIssuedReturnInventory;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real opaque-type refusal tests and API boundaries; native transit remains a separate required acceptance. */
final class IssuedReturnLegNativeCapsuleBoundaryTest {
    @Test void nullAndForeignOpaqueCannotConstructNativeAuthority(){
        for(var opaque:new Object[]{null,new Object(),Boolean.TRUE,new Object[]{new Object()}})
            assertFalse(LegacyTravelPublicationLease.ownedUseSnapshotCurrent(null,opaque,new Object(),new Object(),new Object(),0,0,1,new Vec3(8,70,0)));
    }
    @Test void fullyPopulatedPublicIssueRecordCannotAuthenticateSource(){
        var parent=new Object();var camera=new Object();var store=new Object();var goal=new Vec3(8,70,0);
        var context=new DeviceUseReceipt.Context(parent,new Object(),new Object(),new Object(),store,0,1,0);
        var issue=new DeviceUseReceipt.Issue(context,new Object(),new Object(),0,10,1000,goal,.3);
        assertFalse(LegacyTravelPublicationLease.ownedUseSnapshotCurrent(null,issue,parent,camera,store,0,0,1,goal));
        assertFalse(LegacyTravelPublicationLease.ownedUseSnapshotCurrent(null,context,parent,camera,store,0,0,1,goal));
    }
    @Test void privateIssuedCapsuleIsNotPubliclyConstructible(){
        var type=Arrays.stream(LegacyTravelPublicationLease.Leg.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("OwnedIssuedUseSnapshot")).findFirst().orElseThrow();
        assertTrue(Modifier.isPrivate(type.getModifiers()));assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(1,type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));
    }
    @Test void issuerAndSelectorRequireTheActualPrivateAdmissionType() throws Exception {
        var admission=McSharedMovementNavigator.ReturnDeviceAdmission.class;
        assertEquals(Object.class,LegacyTravelPublicationLease.Leg.class.getMethod("ownedIssuedUseSnapshot",admission).getReturnType());
        assertEquals(DeviceUseReceipt.Context.class,LegacyTravelPublicationLease.Leg.class.getMethod("deviceContextIssued",admission).getReturnType());
        assertEquals(boolean.class,LegacyTravelPublicationLease.Leg.class.getMethod("selectIssuedDeviceSlotOwned",admission,
            McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall.class,
            McIssuedReturnInventory.Snapshot.DeviceSlotCustody.SelectionCall.class,int.class,int.class,
            java.util.function.BooleanSupplier.class,java.util.function.BooleanSupplier.class).getReturnType());
    }
    @Test void finalEntryAndFailedCustodyRequireTheGenuineTypedContinuation() throws Exception {
        var cap=McSuspendedFarmReturnCoordinator.FinalEntryContinuation.class;var request=McSavedAnchorRecovery.IssuedReturnTravelRequest.class;
        assertEquals(LegacyTravelPublicationLease.Leg.class,LegacyTravelPublicationLease.class.getMethod("beginAfterIssuedReturnEntry",
            cap,request,McSharedMovementNavigator.class,long.class,Vec3.class,double.class,LegacyTravelPublicationLease.Leg.class).getReturnType());
        assertEquals(LegacyTravelPublicationLease.Leg.class,LegacyTravelPublicationLease.class.getMethod("consumeFailedIssuedReturnEntry",cap,request,McSharedMovementNavigator.class).getReturnType());
        assertTrue(Modifier.isPrivate(LegacyTravelPublicationLease.class.getDeclaredField("failedIssuedEntry").getModifiers()));
    }
    @Test void unchangedOrdinaryAndTypedIssuedFactoriesStayDistinct() throws Exception {
        assertEquals(0,LegacyTravelPublicationLease.Leg.class.getMethod("ownedUseSnapshot").getParameterCount());
        assertEquals(1,LegacyTravelPublicationLease.Leg.class.getMethod("ownedIssuedUseSnapshot",McSharedMovementNavigator.ReturnDeviceAdmission.class).getParameterCount());
        assertEquals(0,LegacyTravelPublicationLease.Leg.class.getMethod("deviceContext").getParameterCount());
        assertEquals(1,LegacyTravelPublicationLease.Leg.class.getMethod("inventoryCleanupCurrentIssued",McSharedMovementNavigator.ReturnDeviceAdmission.class).getParameterCount());
    }
}
