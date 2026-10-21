package dev.veltraph.client.scheduling;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ScheduledLoadingScreenProofTest {
    private final Object original=new Object(),issued=new Object(),tracker=new Object();
    @Test void onlyIssuedNewNativeConnectionAndActualListenerTrackerProveRemoteLoad(){
        assertTrue(ScheduledLoadingScreenProof.permits(true,true,true,true,original,issued,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,true,original,issued,new Object(),tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,true,issued,issued,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,true,original,issued,issued,tracker,new Object()));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,true,original,issued,issued,null,null));
    }
    @Test void foreignProgressGenericOrManualLoadingScreensCannotBorrowClassAuthority(){
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,false,original,issued,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(false,true,true,true,original,issued,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,false,true,original,issued,issued,tracker,tracker));
    }
    @Test void loadingStopsBeingPermittedWhenSavedReturnStartsOrConnectorIsCancelled(){
        assertFalse(ScheduledLoadingScreenProof.permits(true,false,true,true,original,issued,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(true,true,true,true,original,null,issued,tracker,tracker));
        assertFalse(ScheduledLoadingScreenProof.permits(false,true,true,true,original,issued,issued,tracker,tracker));
    }
}
