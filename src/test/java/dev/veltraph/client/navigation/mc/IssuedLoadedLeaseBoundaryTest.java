package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.world.Vec3;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual public refusal seams on uninitialized Java owner shells. No Minecraft actor, selected source, request, Binding or healthy capsule is made. */
final class IssuedLoadedLeaseBoundaryTest {
    private static <T>T shell(Class<T> type)throws Exception{
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
    @Test void bareCallerAndNullCapsuleCannotAdmitSelectedMovement()throws Exception{
        var lease=shell(LegacyTravelPublicationLease.class);
        assertNull(lease.beginAfterIssuedLoadedSegment(null,null,null,1,null,SharedMovementPolicy.Purpose.EXACT_RETURN,new Vec3(16,70,0)));
        assertNull(lease.beginAfterIssuedLoadedSegment(null,null,null,1,null,SharedMovementPolicy.Purpose.EXACT_RETURN,new Vec3(90,70,0)));
    }
    @Test void bareContinuationCannotReplaceActualRetiredPredecessor()throws Exception{
        var lease=shell(LegacyTravelPublicationLease.class);
        assertNull(lease.beginAfterIssuedLoadedContinuation(null,null,null,1,SharedMovementPolicy.Purpose.EXACT_RETURN,new Vec3(16,70,0),null));
        assertNull(lease.beginAfterIssuedLoadedContinuation(null,null,null,2,SharedMovementPolicy.Purpose.EXACT_RETURN,new Vec3(16,70,0),null));
    }
    @Test void bareSharedEntryCannotCallSelectedProducerOrRetireForeignScope()throws Exception{
        var shared=shell(McSharedMovementNavigator.class);
        assertFalse(shared.startOriginalLoadedSegment(null));
        assertFalse(shared.stopOriginalLoadedSegment(null,null));
    }
    @Test void typedStoredCapsulesHaveNoPublicConstructionOrBooleanIssuer()throws Exception{
        var cap=McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment.class;
        assertEquals(0,cap.getConstructors().length);
        for(var constructor:cap.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        var stored=LegacyTravelPublicationLease.Leg.class.getDeclaredField("loadedIssuedSegment");
        assertTrue(Modifier.isPrivate(stored.getModifiers()));assertSame(cap,stored.getType());
        assertSame(cap,McSharedMovementNavigator.ReturnDeviceAdmission.class.getMethod("loadedSegmentIdentity").getReturnType());
    }
}
