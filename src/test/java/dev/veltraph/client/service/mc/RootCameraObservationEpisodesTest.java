package dev.veltraph.client.service.mc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RootCameraObservationEpisodesTest {
    @Test void ordinaryEpisodeIsCurrentUntilItCloses(){var journal=new RootCameraObservationEpisodes();var call=journal.enter();assertTrue(call.current());call.close();assertFalse(call.current());}
    @Test void nestedEntryRevokesBothEvenAfterItReturns(){var journal=new RootCameraObservationEpisodes();var outer=journal.enter();var nested=journal.enter();assertFalse(outer.current());assertFalse(nested.current());nested.close();assertFalse(outer.current());outer.close();}
    @Test void subsequentSequentialCallDoesNotInheritAmbiguity(){var journal=new RootCameraObservationEpisodes();var first=journal.enter();var nested=journal.enter();nested.close();first.close();try(var second=journal.enter()){assertTrue(second.current());}}
    @Test void ThreeDeepEntriesNeverRenewOrigin(){var journal=new RootCameraObservationEpisodes();var a=journal.enter();var b=journal.enter();var c=journal.enter();assertFalse(a.current());assertFalse(b.current());assertFalse(c.current());c.close();b.close();assertFalse(a.current());a.close();}
    @Test void outOfOrderCloseCannotRestoreCurrentAncestor(){var journal=new RootCameraObservationEpisodes();var a=journal.enter();var b=journal.enter();a.close();assertFalse(b.current());b.close();assertFalse(a.current());}
    @Test void duplicateCloseCannotEraseNewerCall(){var journal=new RootCameraObservationEpisodes();var a=journal.enter();a.close();var b=journal.enter();a.close();assertTrue(b.current());b.close();}
    @Test void explicitRevocationIsPermanent(){var journal=new RootCameraObservationEpisodes();try(var a=journal.enter()){a.revoke();assertFalse(a.current());a.revoke();assertFalse(a.current());}}
    @Test void exceptionUnwindClosesWithoutAuthority(){var journal=new RootCameraObservationEpisodes();var a=journal.enter();try{try(a){throw new IllegalStateException("original");}}catch(IllegalStateException expected){assertEquals("original",expected.getMessage());}assertFalse(a.current());try(var b=journal.enter()){assertTrue(b.current());}}
    @Test void closedDeclarationNeverBecomesAnActiveIssuingEpisodeAgain(){
        var journal=new RootCameraObservationEpisodes();var declaration=journal.enter();declaration.close();
        assertTrue(declaration.unambiguous());assertFalse(declaration.current());
        try(var laterAdvance=journal.enter()){assertTrue(laterAdvance.current());assertFalse(declaration.current());}
        assertFalse(declaration.current());
    }
    @Test void retainedDeclarationDoesNotQualifyAnAmbiguousLaterAdvance(){
        var journal=new RootCameraObservationEpisodes();var declaration=journal.enter();declaration.close();
        var advance=journal.enter();var reentrant=journal.enter();
        assertTrue(declaration.unambiguous()); // Retention alone is not current Root advance authority.
        assertFalse(advance.current());assertFalse(reentrant.current());
        reentrant.close();assertFalse(advance.current());advance.close();
    }
    @Test void failedDeclarationCannotBecomeRetainedByClosing(){
        var journal=new RootCameraObservationEpisodes();var declaration=journal.enter();declaration.revoke();declaration.close();
        assertFalse(declaration.unambiguous());assertFalse(declaration.current());
        try(var laterAdvance=journal.enter()){assertTrue(laterAdvance.current());assertFalse(declaration.unambiguous());}
    }
}
