package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production recovery kernel; no SDK permission or receipt is fabricated. */
final class SavedAnchorOriginalFailureTest {
    static final class Actions implements SavedAnchorRecovery.Actions {
        Throwable primary,cleanup;boolean walking;int releases;
        private void fail(Throwable actual){if(actual instanceof RuntimeException e)throw e;if(actual instanceof Error e)throw e;}
        public boolean startWalk(){if(!walking)fail(primary);return true;}
        public boolean startFlight(){return false;}
        public SavedAnchorRecovery.Travel travel(){fail(primary);return SavedAnchorRecovery.Travel.ACTIVE;}
        public void align(){}
        public void release(){releases++;fail(cleanup);}
    }
    @Test void originalStartFailureSurvivesOriginalCleanupFailure(){
        for(Throwable primary:new Throwable[]{new IllegalStateException("start")}){
            var a=new Actions();a.primary=primary;a.cleanup=new IllegalArgumentException("cleanup");var r=new SavedAnchorRecovery(a,SavedAnchorRecovery.Settings.defaults());
            assertSame(primary,assertThrows(primary.getClass(),()->r.start(true,false,0)));
            assertEquals(SavedAnchorRecovery.State.FAILED,r.state());assertFalse(r.active());assertEquals(1,a.releases);
            assertArrayEquals(new Throwable[]{a.cleanup},primary.getSuppressed());
        }
    }
    @Test void originalTravelFailureSurvivesOriginalCleanupFailureWithoutReleaseRetry(){
        var a=new Actions();a.walking=true;var r=new SavedAnchorRecovery(a,SavedAnchorRecovery.Settings.defaults());assertTrue(r.start(true,false,0));
        var primary=new IllegalStateException("travel");a.primary=primary;a.cleanup=new AssertionError("cleanup");
        assertSame(primary,assertThrows(IllegalStateException.class,()->r.tick(true,false,false,false,1)));
        assertEquals(SavedAnchorRecovery.State.FAILED,r.state());assertFalse(r.active());assertEquals(1,a.releases);
        assertArrayEquals(new Throwable[]{a.cleanup},primary.getSuppressed());r.cancel();assertEquals(1,a.releases);
    }
    @Test void originalErrorPathAddsNoReleaseAndPropagatesSameError(){
        var a=new Actions();var primary=new AssertionError("original Error");a.primary=primary;a.cleanup=new IllegalStateException("cleanup");
        var r=new SavedAnchorRecovery(a,SavedAnchorRecovery.Settings.defaults());
        assertSame(primary,assertThrows(AssertionError.class,()->r.start(true,false,0)));assertEquals(0,a.releases);assertEquals(0,primary.getSuppressed().length);
    }
}
