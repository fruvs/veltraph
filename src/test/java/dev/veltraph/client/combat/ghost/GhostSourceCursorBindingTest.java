package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GhostSourceCursorBindingTest {
    @Test void negativeCoordinatesAreFlooredAndStandingYIsNotCentered() {
        var point=new GhostSourcePointTransform.Point(-1.2,76.9,-3.8);
        assertEquals(new GhostSourcePointTransform.Point(-1.5,76,-3.5),GhostSourcePointTransform.standingCenter(point));
        assertEquals(new GhostSourcePointTransform.Point(-1.5,76.5,-3.5),GhostSourcePointTransform.blockCenter(point));
    }
    @Test void nonfiniteCoordinatesAreRejectedBeforeNativeGeometryReads() {
        assertThrows(IllegalArgumentException.class,()->GhostSourcePointTransform.standingCenter(new GhostSourcePointTransform.Point(Double.NaN,76,0)));
    }
    @Test void callbackMustBeCurrentAndDestinationRequiresVisibleTarget() {
        assertEquals(GhostSourcePathCompletion.Next.REJECT_STALE,GhostSourcePathCompletion.decide(false,"At destination",true));
        assertEquals(GhostSourcePathCompletion.Next.RETRY_SEARCH,GhostSourcePathCompletion.decide(true,"At destination",false));
        assertEquals(GhostSourcePathCompletion.Next.DISPATCH_MELEE,GhostSourcePathCompletion.decide(true,"At destination",true));
        assertEquals(GhostSourcePathCompletion.Next.DISPATCH_MELEE,GhostSourcePathCompletion.decide(true,"Other source result",false));
    }
}
