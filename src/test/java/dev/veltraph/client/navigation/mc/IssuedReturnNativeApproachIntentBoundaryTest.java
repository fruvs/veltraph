package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.OwnedUseIntentDirection;
import dev.veltraph.client.world.Vec3;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Preserves general direction math; these tests never manufacture native approach permission. */
final class IssuedReturnNativeApproachIntentBoundaryTest {
    @Test void levelOperationalEndpointIsNotGeneralThreeDimensionalCollinearity(){
        var origin=new Vec3(0,72,0);var savedEntry=new Vec3(24,70,0);var nativeLevelEndpoint=new Vec3(8,72,0);
        assertFalse(OwnedUseIntentDirection.forward(origin,savedEntry,nativeLevelEndpoint));
    }
    @Test void originalGeneralCollinearIntentRemainsRequired(){
        assertTrue(OwnedUseIntentDirection.forward(new Vec3(0,72,0),new Vec3(24,72,0),new Vec3(8,72,0)));
        assertFalse(OwnedUseIntentDirection.forward(new Vec3(0,72,0),new Vec3(24,72,0),new Vec3(-8,72,0)));
        assertFalse(OwnedUseIntentDirection.forward(new Vec3(0,72,0),new Vec3(24,72,0),new Vec3(8,72,1)));
    }
    @Test void nativeApproachCanOnlyBeRequestedThroughActualPrivateAdmission() throws Exception {
        var type=McSharedMovementNavigator.ReturnDeviceAdmission.class;
        assertEquals(boolean.class,type.getMethod("qualifiedApproachCurrent",Vec3.class,Vec3.class).getReturnType());
        assertEquals(1,type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));
    }
}
