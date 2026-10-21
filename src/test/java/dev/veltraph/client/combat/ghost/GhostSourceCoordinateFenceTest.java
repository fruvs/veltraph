package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceCoordinateFenceTest {
    @Test void stopNewOwnerOrSettingsChangeDuringReadDeniesTheNativeValue(){for(String change:new String[]{"stop","new owner","settings"}){var scope=new AtomicBoolean(true);var reads=new AtomicInteger();
        assertFalse(GhostSourceCoordinateFence.read(scope::get,()->true,()->{reads.incrementAndGet();scope.set(false);return true;}),change);assertEquals(1,reads.get());}}
    @Test void directNativeComponentDriftDuringValidationDeniesPublication(){var frame=new AtomicBoolean(true);
        assertFalse(GhostSourceCoordinateFence.read(()->true,frame::get,()->{frame.set(false);return true;}));assertFalse(GhostSourceCoordinateFence.publication(()->true,frame::get,()->true,()->true));}
    @Test void callbackMutationCannotTurnAStaleSealIntoReady(){var scope=new AtomicBoolean(true);var frame=new AtomicBoolean(true);var geometry=new AtomicBoolean(true);
        assertFalse(GhostSourceCoordinateFence.publication(scope::get,frame::get,geometry::get,()->{scope.set(false);return true;}));
        scope.set(true);assertFalse(GhostSourceCoordinateFence.publication(scope::get,frame::get,geometry::get,()->{geometry.set(false);return true;}));
        geometry.set(true);assertFalse(GhostSourceCoordinateFence.publication(scope::get,frame::get,geometry::get,()->{frame.set(false);return true;}));}
    @Test void finalFootprintCallbackCannotMutateOwnerOrNativeFrameAfterTheLastGuard(){var scope=new AtomicBoolean(true);var frame=new AtomicBoolean(true);var checks=new AtomicInteger();
        assertFalse(GhostSourceCoordinateFence.publication(scope::get,frame::get,()->{if(checks.incrementAndGet()==2)scope.set(false);return true;},()->true));
        scope.set(true);checks.set(0);assertFalse(GhostSourceCoordinateFence.publication(scope::get,frame::get,()->{if(checks.incrementAndGet()==2)frame.set(false);return true;},()->true));}
    @Test void unavailableGeometryOrUnsealedCaptureNeverPublishesAndInvalidBeforeReadDoesNotQueryWorld(){var reads=new AtomicInteger();
        assertFalse(GhostSourceCoordinateFence.read(()->false,()->true,()->{reads.incrementAndGet();return true;}));assertEquals(0,reads.get());
        assertFalse(GhostSourceCoordinateFence.publication(()->true,()->true,()->false,()->true));assertFalse(GhostSourceCoordinateFence.publication(()->true,()->true,()->true,()->false));
        assertTrue(GhostSourceCoordinateFence.publication(()->true,()->true,()->true,()->true));}
    private static GhostSourceWalkDemandCapture<Integer,Integer> actualSealedCapture(){var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(java.util.List.of(1));capture.pump(1,()->0,()->true,key->key);capture.seal(()->true);return capture;}
    @Test void secondFootprintCallbackRevokesActualCaptureAndCannotPublish(){var capture=actualSealedCapture();var callbacks=new AtomicInteger();
        assertFalse(GhostSourceCoordinateFence.publication(()->true,()->true,()->{if(callbacks.incrementAndGet()==2)capture.revoke();return true;},()->capture.valid()&&capture.state()==GhostSourceWalkDemandCapture.State.SEALED));
        assertEquals(2,callbacks.get());assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());assertFalse(capture.valid());}
    @Test void finalCurrentCallbackRevokesActualCaptureAndCachedFinalSealRejectsIt(){var capture=actualSealedCapture();var currentCalls=new AtomicInteger();
        assertFalse(GhostSourceCoordinateFence.publication(()->{if(currentCalls.incrementAndGet()==3)capture.revoke();return true;},()->true,()->true,()->capture.valid()&&capture.state()==GhostSourceWalkDemandCapture.State.SEALED));
        assertEquals(3,currentCalls.get());assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());assertFalse(capture.valid());}

}
