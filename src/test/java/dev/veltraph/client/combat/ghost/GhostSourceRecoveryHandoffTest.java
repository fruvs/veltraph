package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceRecoveryHandoffTest {
    @Test void exactNamedChildTransitionCannotReturnToSearchOrChooseThirdChild(){var search=new Object();var leave=new Object();var g=new GhostSourceNamedChildHandoff(search);assertTrue(g.current(search));assertTrue(g.authorize(search,leave));assertTrue(g.current(search));assertTrue(g.current(leave));assertFalse(g.current(search));assertFalse(g.current(new Object()));assertFalse(g.authorize(leave,new Object()));}
    @Test void foreignSourceAndSelfHandoffCannotAuthorize(){var search=new Object();var g=new GhostSourceNamedChildHandoff(search);assertFalse(g.authorize(new Object(),new Object()));assertFalse(g.authorize(search,search));assertFalse(g.authorize(search,null));assertTrue(g.authorize(search,new Object()));}
    @Test void earlyPathCompletionRetainsOriginalToolDeadline(){var l=new GhostSourceLeavingLifecycle();l.begin(java.util.List.of(new GhostSourceWalkFrontier.Cell(1,2,3)),n->0);l.captured(true,GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,true,100,n->0);l.pathSucceeded();assertFalse(l.toolDue(104));assertTrue(l.toolDue(105));l.toolSelected();assertFalse(l.toolDue(1000));}
    @Test void ownedArrivalAllowsExactlyOneOwnCancelAndNoManualRebase(){assertTrue(GhostSourceOwnedArrivalEpoch.matches(7,7,8,8));assertFalse(GhostSourceOwnedArrivalEpoch.matches(7,8,9,9));assertFalse(GhostSourceOwnedArrivalEpoch.matches(7,7,9,9));assertFalse(GhostSourceOwnedArrivalEpoch.matches(7,7,8,9));assertFalse(GhostSourceOwnedArrivalEpoch.matches(-1,-1,0,0));assertFalse(GhostSourceOwnedArrivalEpoch.matches(Long.MAX_VALUE,Long.MAX_VALUE,Long.MIN_VALUE,Long.MIN_VALUE));}
}
