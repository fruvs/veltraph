package dev.veltraph.client.service.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SavedMaintenanceFailurePreservationTest {
    @Test void reusedNativeThrowableDoesNotCauseSelfSuppression(){var nativeFailure=new IllegalStateException("native");assertDoesNotThrow(()->SavedMaintenanceFailurePreservation.suppressWithoutReplacing(nativeFailure,nativeFailure));assertEquals(0,nativeFailure.getSuppressed().length);}
    @Test void distinctObserverFailurePreservesExactPrimaryAndSecondaryIdentities(){var nativeFailure=new AssertionError("native");var observerFailure=new RuntimeException("observer");SavedMaintenanceFailurePreservation.suppressWithoutReplacing(nativeFailure,observerFailure);assertSame(observerFailure,nativeFailure.getSuppressed()[0]);assertEquals("native",nativeFailure.getMessage());}
    @Test void suppressionDisabledNativeThrowableStillPreserved(){var nativeFailure=new SuppressionDisabled();assertDoesNotThrow(()->SavedMaintenanceFailurePreservation.suppressWithoutReplacing(nativeFailure,new Error("observer")));assertEquals(0,nativeFailure.getSuppressed().length);}
    @Test void missingSecondaryCannotReplaceActualNativeFailure(){var nativeFailure=new RuntimeException("native");assertDoesNotThrow(()->SavedMaintenanceFailurePreservation.suppressWithoutReplacing(nativeFailure,null));assertEquals(0,nativeFailure.getSuppressed().length);}
    private static final class SuppressionDisabled extends RuntimeException {private SuppressionDisabled(){super("native",null,false,true);}}
}
