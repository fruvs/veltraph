package dev.veltraph.client.rewarp.mc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IssuedFarmCommandOrderTest {
    private final Object packet=new Object();
    private IssuedFarmCommandOrder entered() {
        var o=new IssuedFarmCommandOrder(); assertTrue(o.commandEntered()); assertTrue(o.bind(packet));
        assertTrue(o.packetEntered(packet)); assertTrue(o.transportEntered(packet)); return o;
    }
    private void body(IssuedFarmCommandOrder o){assertTrue(o.bodyEntered(packet));assertTrue(o.directWriteReturned(packet,true));o.bodyExited(packet,true,false);}
    private void finish(IssuedFarmCommandOrder o) { o.bodyEntered(packet);o.directWriteReturned(packet,true);o.bodyExited(packet,true,false);o.dispatchExited(packet,true); o.transportExited(packet,true); o.packetExited(packet,true); o.commandExited(true); }
    @Test void normalizedConfiguredCommandAcceptedWithoutFixedPlotRestriction() {
        assertTrue(IssuedFarmCommandOrder.validCommand("warp garden")); assertTrue(IssuedFarmCommandOrder.validCommand("tptoplot 2"));
        assertTrue(IssuedFarmCommandOrder.validCommand("x".repeat(256)));
    }
    @Test void oversizedAndUnnormalizedOrControlCommandsRefused() {
        assertFalse(IssuedFarmCommandOrder.validCommand(null)); assertFalse(IssuedFarmCommandOrder.validCommand("  "));
        assertFalse(IssuedFarmCommandOrder.validCommand("/warp garden")); assertFalse(IssuedFarmCommandOrder.validCommand("x".repeat(257)));
        for (int c=0; c<32; c++) assertFalse(IssuedFarmCommandOrder.validCommand("warp"+(char)c+"garden"));
        assertFalse(IssuedFarmCommandOrder.validCommand("warp"+(char)127+"garden"));
    }
    @Test void completeOnlyAfterAllOriginalMethodsReturn() {
        var o=entered(); assertFalse(o.complete()); assertTrue(o.leaf(packet)); assertFalse(o.complete());
        body(o);o.dispatchExited(packet,true); o.transportExited(packet,true); assertFalse(o.complete()); o.packetExited(packet,true); assertFalse(o.complete());
        o.commandExited(true); assertTrue(o.complete());
    }
    @Test void queueOrAnyOtherRefusalCannotBeAdoptedByLateNormalReturns() {var o=entered(); o.refuse(); assertFalse(o.leaf(packet)); finish(o); assertFalse(o.complete());}
    @Test void cancelledBeforeRealDispatchCannotBorrowConnectedLeaf() {var o=entered();o.leaf(packet);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertFalse(o.complete());}
    @Test void throwingRealDispatchCannotProveSubmission() {var o=entered();o.leaf(packet);o.dispatchExited(packet,false);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertFalse(o.complete());}
    @Test void replayedDispatchReturnRevokesTheObservation() {var o=entered();o.leaf(packet);o.dispatchExited(packet,true);o.dispatchExited(packet,true);finish(o);assertFalse(o.complete());}
    @Test void noLeafCannotProveSubmission() {var o=entered(); finish(o); assertFalse(o.complete());}
    @Test void throwingTransportCannotProveSubmission() {var o=entered(); o.leaf(packet); o.dispatchExited(packet,true); o.transportExited(packet,false); o.packetExited(packet,true); o.commandExited(true); assertFalse(o.complete());}
    @Test void throwingPacketCallerCannotProveSubmission() {var o=entered(); o.leaf(packet); o.dispatchExited(packet,true); o.transportExited(packet,true); o.packetExited(packet,false); o.commandExited(true); assertFalse(o.complete());}
    @Test void throwingOuterCommandCannotProveSubmission() {var o=entered(); o.leaf(packet); o.dispatchExited(packet,true); o.transportExited(packet,true); o.packetExited(packet,true); o.commandExited(false); assertFalse(o.complete());}
    @Test void sameTextDifferentPacketIdentityCannotBorrowLeaf() {var o=entered(); assertFalse(o.leaf(new Object())); finish(o); assertFalse(o.complete());}
    @Test void recursivelyMatchingCommandInvalidatesOuterRequest() {var o=entered(); assertFalse(o.commandEntered()); o.commandExited(true); o.leaf(packet); finish(o); assertFalse(o.complete());}
    @Test void recursivelyMatchingPacketCannotBorrowReceipt() {var o=entered(); assertFalse(o.packetEntered(packet)); o.packetExited(packet,true); o.leaf(packet); finish(o); assertFalse(o.complete());}
    @Test void recursivelyMatchingTransportCannotBorrowReceipt() {var o=entered(); assertFalse(o.transportEntered(packet)); o.dispatchExited(packet,true); o.transportExited(packet,true); o.leaf(packet); finish(o); assertFalse(o.complete());}
    @Test void repeatedLeafIsRefused() {var o=entered(); assertTrue(o.leaf(packet)); assertFalse(o.leaf(packet)); finish(o); assertFalse(o.complete());}
    @Test void secondPacketBindingIsIrreversibleEvenIfIdentityMatches() {var o=new IssuedFarmCommandOrder();o.commandEntered();o.bind(packet);assertFalse(o.bind(packet));o.packetEntered(packet);o.transportEntered(packet);o.leaf(packet);finish(o);assertFalse(o.complete());}
    @Test void transportBeforePacketCannotEstablishNativeOrder() {var o=new IssuedFarmCommandOrder();o.commandEntered();o.bind(packet);assertFalse(o.transportEntered(packet));assertFalse(o.complete());}
    @Test void returnReplayRevokesCompletedObservation() {var o=entered();o.leaf(packet);finish(o);assertTrue(o.complete());o.commandExited(true);assertFalse(o.complete());}
    @Test void innerCancelledSendPacketNormalReturnCannotComplete(){var o=entered();o.leaf(packet);o.dispatchExited(packet,true);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertFalse(o.complete());}
    @Test void innerCancelledDoSendPacketWithoutChannelWriteCannotComplete(){var o=entered();o.leaf(packet);o.bodyEntered(packet);o.bodyExited(packet,true,false);o.dispatchExited(packet,true);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertFalse(o.complete());}
    @Test void actualScheduledOperationNormalReturnSupportsSubmissionOnly(){var o=entered();o.leaf(packet);assertTrue(o.bodyEntered(packet));o.bodyExited(packet,true,true);o.dispatchExited(packet,true);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertTrue(o.complete());}
    @Test void throwingScheduledOperationCannotComplete(){var o=entered();o.leaf(packet);o.bodyEntered(packet);o.bodyExited(packet,false,true);o.dispatchExited(packet,true);o.transportExited(packet,true);o.packetExited(packet,true);o.commandExited(true);assertFalse(o.complete());}
    @Test void foreignBodyPacketAndRepeatedBodyAreIrreversible(){var o=entered();o.leaf(packet);assertFalse(o.bodyEntered(new Object()));assertFalse(o.bodyEntered(packet));assertFalse(o.complete());var p=entered();p.leaf(packet);assertTrue(p.bodyEntered(packet));assertFalse(p.bodyEntered(packet));assertFalse(p.complete());}
    @Test void directWriteThrowAndReturnReplayRefuse(){var o=entered();o.leaf(packet);o.bodyEntered(packet);assertFalse(o.directWriteReturned(packet,false));o.bodyExited(packet,true,false);assertFalse(o.complete());var p=entered();p.leaf(packet);p.bodyEntered(packet);assertTrue(p.directWriteReturned(packet,true));assertFalse(p.directWriteReturned(packet,true));assertFalse(p.complete());}
}
