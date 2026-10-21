package dev.veltraph.client.navigation.mc;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Negative authority/type tests only; genuine normal-return native cases remain required. */
final class NormalReturnLegNativeCapsuleBoundaryTest {
    @Test void publicMetadataAndOpaqueObjectsDoNotBecomeNormalSource(){
        var parent=new Object();var store=new Object();var context=new DeviceUseReceipt.Context(parent,new Object(),new Object(),new Object(),store,0,1,0);
        var goal=new Vec3(8,70,0);var issue=new DeviceUseReceipt.Issue(context,new Object(),new Object(),0,10,1000,goal,.3);
        for(var opaque:new Object[]{Boolean.TRUE,new Object(),context,issue})assertFalse(LegacyTravelPublicationLease.ownedUseSnapshotCurrent(null,opaque,parent,parent,store,0,0,1,goal));
    }
    @Test void actualNormalCapsuleAndLoanArePrivateConstructedTypes(){
        for(var name:new String[]{"NormalReturnOwnedUseSnapshot","NormalReturnSelectedSlotLoan"}){
            var type=Arrays.stream(LegacyTravelPublicationLease.Leg.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals(name)).findFirst().orElseThrow();
            assertTrue(Modifier.isPrivate(type.getModifiers()));assertTrue(Modifier.isFinal(type.getModifiers()));
            assertEquals(1,type.getDeclaredConstructors().length);assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));
        }
    }
    @Test void normalNativeFactoryAndLoanUseDistinctActualAdmissionType() throws Exception {
        var type=McSharedMovementNavigator.NormalReturnDeviceAdmission.class;
        assertEquals(Object.class,LegacyTravelPublicationLease.Leg.class.getMethod("ownedNormalReturnUseSnapshot",type).getReturnType());
        assertEquals(Object.class,LegacyTravelPublicationLease.Leg.class.getMethod("selectNormalReturnDeviceSlotLoanOwned",type,int.class,int.class,
            java.util.function.BooleanSupplier.class,java.util.function.BooleanSupplier.class).getReturnType());
        assertEquals(boolean.class,LegacyTravelPublicationLease.Leg.class.getMethod("restoreNormalReturnDeviceSlotLoanOwned",type,Object.class).getReturnType());
        assertEquals(dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory.class,
            LegacyTravelPublicationLease.Leg.class.getMethod("normalReturnRestoredSelectionHistory",type,Object.class).getReturnType());
    }
    @Test void originalHistoryAndTypedPrivateLoanRemainSeparateFromPublicIssue() throws Exception {
        var type=McSharedMovementNavigator.NormalReturnDeviceAdmission.class;
        assertEquals(dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory.class,type.getMethod("originalSelectionHistory").getReturnType());
        assertTrue(Modifier.isPrivate(LegacyTravelPublicationLease.Leg.class.getDeclaredField("normalReturnSlotLoan").getModifiers()));
        assertTrue(Modifier.isPrivate(LegacyTravelPublicationLease.Leg.class.getDeclaredField("normalReturnUse").getModifiers()));
        assertEquals(boolean.class,LegacyTravelPublicationLease.Leg.class.getMethod("normalReturnDeviceSlotCurrent",type).getReturnType());
    }
}
