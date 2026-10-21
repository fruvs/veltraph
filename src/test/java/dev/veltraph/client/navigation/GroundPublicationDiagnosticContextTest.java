package dev.veltraph.client.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundPublicationDiagnosticContextTest {
    @Test void unboundDefaultSnapshotPreservesOriginalSitesReasonAndBits() {
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.directionalContext(false);
        assertFalse(GroundPublicationFailureCache.copy(25,false));
        GroundPublicationFailureCache.returned("selectedProof.originalFalseReturn");
        var actual=GroundPublicationFailureCache.finish("original condition false");
        assertEquals("selectedProof.originalFalseReturn",actual.firstReturnSite());
        assertEquals("selectedProof.originalFalseReturn",actual.outerReturnSite());
        assertEquals("original condition false",actual.originalReason());
        assertEquals(1L<<25,actual.visitedLow());assertEquals(0L,actual.trueLow());
    }
    @Test void directionalSnapshotDeclaresQualifiedContextWithoutAlteringResults() {
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.directionalContext(true);
        assertTrue(GroundPublicationFailureCache.copy(25,true));
        assertFalse(GroundPublicationFailureCache.copy(40,false));
        GroundPublicationFailureCache.returned("selectedProof.originalFalseReturn");
        var actual=GroundPublicationFailureCache.finish("selected physical condition false");
        assertEquals("DIRECTIONAL_QUALIFIED::selectedProof.originalFalseReturn",actual.firstReturnSite());
        assertEquals("DIRECTIONAL_QUALIFIED::selected physical condition false",actual.originalReason());
        assertEquals((1L<<25)|(1L<<40),actual.visitedLow());assertEquals(1L<<25,actual.trueLow());
    }
    @Test void nextDefaultCaptureCannotBorrowOldDirectionalContext() {
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.directionalContext(true);
        GroundPublicationFailureCache.success();
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.copy(55,false);
        GroundPublicationFailureCache.returned("original next failure");
        var actual=GroundPublicationFailureCache.finish("next original reason");
        assertEquals("original next failure",actual.firstReturnSite());
        assertEquals("next original reason",actual.originalReason());
    }
    @Test void contextReportingCannotActivateACaptureOrReplaceEarlierFailure() {
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.returned("original first");
        var first=GroundPublicationFailureCache.finish("original reason");
        GroundPublicationFailureCache.directionalContext(true);
        assertSame(first,GroundPublicationFailureCache.finish("unqueried context",first));
        assertNull(GroundPublicationFailureCache.finish("unqueried context"));
    }
}
