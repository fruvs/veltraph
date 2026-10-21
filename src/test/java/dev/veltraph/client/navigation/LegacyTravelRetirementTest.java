package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.LegacyTravelRetirement.*;

/** Callback counterexamples account for mutations without pretending a stale fence succeeded. */
class LegacyTravelRetirementTest {
    static final class Effects implements LegacyTravelRetirement.Effects {
        boolean keys=true,camera=true,current=true,active=true,retired,cancelled,replaceDuringRetire,loseAfterCancel,cancelResult=true;
        int loseAfterRelease=-1,replaceAfterRelease=-1,throwAfterRelease=-1,failRelease=-1,loseAtFence=-1,fences;
        final List<Key> releases=new ArrayList<>();
        public boolean keysPublished(){return keys;}public boolean cameraPublishedOrQueued(){return camera;}
        public boolean activeLegMatches(){return active;}
        public boolean cleanupFence(){if(++fences==loseAtFence)current=false;return current;}
        public boolean release(Key key){
            releases.add(key);int n=releases.size();
            if(n==loseAfterRelease)current=false;if(n==replaceAfterRelease){current=false;active=false;}
            if(n==throwAfterRelease)throw new IllegalStateException("Callback refusal");
            return n!=failRelease;
        }
        public boolean cancelCamera(){cancelled=true;if(loseAfterCancel)current=false;return cancelResult;}
        public Removal retireLogical(){retired=true;if(replaceDuringRetire)active=false;return active?Removal.REMOVED_OWN:Removal.FOREIGN_PRESERVED;}
    }
    @Test void cleanRetirementRequiresAllNineActualReleasesCameraAndPostFence(){var e=new Effects();var r=retire(e);assertTrue(r.cleanSuccess());assertEquals(Status.CLEAN,r.status());assertEquals(List.of(Key.values()),e.releases);assertTrue(r.allNineReleased());assertTrue(r.cameraCancelled());assertTrue(r.postFence());assertFalse(r.requiresGlobalStop());assertTrue(e.retired);}
    @Test void generationLossBetweenIndividualReleasesStopsBeforeNextMutation(){var e=new Effects();e.loseAtFence=4;var r=retire(e);assertEquals(List.of(Key.FORWARD),e.releases);assertEquals(Result.CONFIRMED,r.keys().get(0));assertEquals(Result.NOT_ATTEMPTED,r.keys().get(1));assertFalse(e.cancelled);assertEquals(Status.MISMATCH,r.status());assertFalse(r.cleanSuccess());assertFalse(r.allNineReleased());assertTrue(r.requiresGlobalStop());assertTrue(e.retired);}
    @Test void generationLossAfterActualReleaseIsFailedCertification(){var e=new Effects();e.loseAfterRelease=3;var r=retire(e);assertEquals(3,e.releases.size());assertEquals(Result.FAILED,r.keys().get(2));assertEquals(Result.NOT_ATTEMPTED,r.keys().get(3));assertFalse(e.cancelled);assertFalse(r.postFence());assertFalse(r.cleanSuccess());assertTrue(r.requiresGlobalStop());assertEquals(Removal.REMOVED_OWN,r.removal());}
    @Test void foreignSupersessionDuringPartialReleasePreservesForeignLeg(){var e=new Effects();e.replaceAfterRelease=2;var r=retire(e);assertEquals(2,e.releases.size());assertEquals(Status.REVOKED,r.status());assertEquals(Removal.FOREIGN_PRESERVED,r.removal());assertFalse(e.cancelled);assertFalse(r.allNineReleased());assertTrue(r.requiresGlobalStop());assertTrue(e.retired);}
    @Test void lossAfterNineReleasesBeforeCameraCannotClaimAggregateKeySuccess(){var e=new Effects();e.loseAtFence=20;var r=retire(e);assertEquals(9,e.releases.size());assertTrue(r.keys().stream().allMatch(k->k==Result.CONFIRMED));assertFalse(e.cancelled);assertFalse(r.allNineReleased());assertFalse(r.cameraCancelled());assertFalse(r.cleanSuccess());assertEquals(Status.MISMATCH,r.status());assertTrue(r.requiresGlobalStop());}
    @Test void generationLossAfterActualCameraCancellationFailsFinalCertification(){var e=new Effects();e.loseAfterCancel=true;var r=retire(e);assertTrue(e.cancelled);assertEquals(Result.FAILED,r.camera());assertFalse(r.cameraCancelled());assertFalse(r.allNineReleased());assertFalse(r.cleanSuccess());assertEquals(Status.MISMATCH,r.status());assertTrue(e.retired);}
    @Test void sameParentNewRequestBeforeCleanupIsExplicitMismatchWithNoMutation(){var e=new Effects();e.current=false;var r=retire(e);assertTrue(e.releases.isEmpty());assertFalse(e.cancelled);assertEquals(Status.MISMATCH,r.status());assertFalse(r.initialFence());assertFalse(r.cleanSuccess());assertTrue(r.requiresGlobalStop());assertTrue(e.retired);}
    @Test void alreadyForeignLegIsRevokedWithoutAnyForeignMutation(){var e=new Effects();e.active=false;var r=retire(e);assertTrue(e.releases.isEmpty());assertFalse(e.cancelled);assertEquals(Status.REVOKED,r.status());assertEquals(Removal.FOREIGN_PRESERVED,r.removal());assertFalse(r.cleanSuccess());}
    @Test void foreignReplacementBetweenPostFenceAndLogicalRetireIsRevoked(){var e=new Effects();e.replaceDuringRetire=true;var r=retire(e);assertEquals(9,e.releases.size());assertTrue(e.cancelled);assertEquals(Status.REVOKED,r.status());assertEquals(Removal.FOREIGN_PRESERVED,r.removal());assertFalse(r.cleanSuccess());assertFalse(r.allNineReleased());assertFalse(r.cameraCancelled());}
    @Test void runtimeRefusalStillRetiresLogicalLegAndAccountsPartialKeys(){var e=new Effects();e.throwAfterRelease=2;var r=retire(e);assertEquals(2,e.releases.size());assertEquals(Result.FAILED,r.keys().get(1));assertTrue(e.retired);assertTrue(e.cancelled);assertFalse(r.cleanSuccess());assertEquals(Status.MISMATCH,r.status());assertTrue(r.requiresGlobalStop());}
    @Test void cameraRefusalNeverReturnsEarlierOwnedAsSuccess(){var e=new Effects();e.cancelResult=false;var r=retire(e);assertTrue(e.cancelled);assertEquals(Result.FAILED,r.camera());assertFalse(r.cameraCancelled());assertFalse(r.cleanSuccess());assertEquals(Status.MISMATCH,r.status());assertTrue(e.retired);}
    @Test void noKeyPublicationDoesNotClaimAllNineReleaseOrNeedGlobalStop(){var e=new Effects();e.keys=false;var r=retire(e);assertTrue(r.cleanSuccess());assertTrue(e.releases.isEmpty());assertFalse(r.allNineReleased());assertTrue(r.cameraCancelled());assertFalse(r.requiresGlobalStop());}
    @Test void noCameraPublicationDoesNotInventCameraCancellation(){var e=new Effects();e.camera=false;var r=retire(e);assertTrue(r.cleanSuccess());assertFalse(e.cancelled);assertEquals(Result.NOT_OWNED,r.camera());assertFalse(r.cameraCancelled());assertTrue(r.allNineReleased());}
    @Test void sameStorePolicyRevocationRetainsCleanupFenceButNeverNeedsGameplayPermission(){var l=LegacyTravelPublicationIdentity.Lease.capture(new LegacyTravelPublicationIdentity.State(this,this,this,this,this,this,this,this,1,1,this,1,1,1,this,true,true,true));assertNotNull(l);var initial=new LegacyTravelPublicationIdentity.State(this,this,this,this,this,this,this,this,1,1,this,1,1,1,this,true,true,true);assertTrue(l.inputPublished(initial,initial));var ownPlan=new LegacyTravelPublicationIdentity.State(this,this,this,this,this,this,this,this,1,1,this,1,1,2,this,true,true,true);assertTrue(l.published(new LegacyTravelPublicationIdentity.Publication(initial,ownPlan,2)));var revoked=new LegacyTravelPublicationIdentity.State(this,this,this,this,this,this,this,this,3,1,this,1,1,2,this,true,true,false);assertFalse(l.current(revoked));assertTrue(l.cleanupCurrent(revoked));var e=new Effects();e.keys=l.keysPublished();e.camera=l.cameraPublishedOrQueued();e.current=l.cleanupCurrent(revoked);assertTrue(retire(e).cleanSuccess());}
    @Test void offThreadOrReentrantNotRetiredOutcomeNeverClaimsSuccess(){var r=notRetired(true,true);assertFalse(r.logicalRetired());assertFalse(r.cleanSuccess());assertFalse(r.allNineReleased());assertFalse(r.cameraCancelled());assertTrue(r.requiresGlobalStop());assertEquals(Status.REVOKED,r.status());}
    @Test void caughtHelperFailureWithCleanOwnedRetirementRemainsDiagnosticOnly(){RuntimeException helperFailure=null;try{throw new IllegalStateException("Helper cleanup failed");}catch(RuntimeException failed){helperFailure=failed;}var e=new Effects();var outcome=retire(e);assertNotNull(helperFailure);assertTrue(outcome.cleanSuccess());assertFalse(outcome.localFailStopCandidate());assertEquals(Status.CLEAN,outcome.status());}
    @Test void localFailStopCandidateRequiresMismatchAndOwnLogicalRemoval(){var owned=new Effects();owned.failRelease=2;assertTrue(retire(owned).localFailStopCandidate());var foreign=new Effects();foreign.replaceAfterRelease=2;assertFalse(retire(foreign).localFailStopCandidate());assertFalse(notRetired(true,true).localFailStopCandidate());}
}

