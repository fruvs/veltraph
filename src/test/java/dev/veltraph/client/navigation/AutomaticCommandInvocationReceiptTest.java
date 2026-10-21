package dev.veltraph.client.navigation;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure identity/callback models only. No Minecraft packet constructor, native receipt or runtime proof. */
class AutomaticCommandInvocationReceiptTest {
    private static final Object ROOT=new Object(),PARENT=new Object(),PLAYER=new Object(),WORLD=new Object(),CONNECTION=new Object(),CONFIG=new Object(),SERVICE=new Object(),LEG=new Object();
    private static final Object TRANSPORT=new Object(),SEND_PACKET=new Object(),LISTENER=new Object(),POSITION_PACKET=new Object();
    private static LegacyTravelPublicationIdentity.State state(){return new LegacyTravelPublicationIdentity.State(ROOT,PARENT,PARENT,PARENT,PLAYER,WORLD,CONNECTION,CONFIG,3,1,SERVICE,7,11,13,LEG,true,true,true);}
    private static final Vec3 ARRIVAL=new Vec3(20.5,70,.5);
    private static AutomaticCommandReceipt.Issue issue(){var shortcut=new TravelAtlas.Shortcut("ranked",TravelAtlas.Kind.FAST_WARP,GameArea.GARDEN,"minecraft:overworld",new Vec3(.5,70,.5),ARRIVAL,null,null,"express","rank:express","Express unlocked","Express rejected",1,0);return new AutomaticCommandReceipt.Issue(state(),shortcut,1,4,100,10100);}
    private static final class Fixture {
        final Object session=new Object();final AutomaticCommandReceipt.Issue issue=issue();final AutomaticCommandReceipt.Submission proof=new AutomaticCommandReceipt.Submission(issue);
        final AutomaticCommandInvocationReceipt observer=new AutomaticCommandInvocationReceipt(session,issue,proof);
        Fixture(){proof.open();proof.attempted();}
        AutomaticCommandInvocationReceipt.Invocation head(Object packet){return observer.sendEntry(session,TRANSPORT,packet,state(),"warp express",110);}
        void dispatch(Object packet){var invocation=head(packet);assertNotNull(invocation);assertTrue(observer.sendReturned(invocation,session,TRANSPORT,packet,state(),111));proof.returnedNormally();proof.close();assertTrue(proof.dispatched());}
    }
    @Test void genuineModeledSendReturnThenAppliedEntryCorrelates(){var f=new Fixture();f.dispatch(SEND_PACKET);var invocation=f.observer.positionEntry(f.session,LISTENER,POSITION_PACKET,state());assertNotNull(invocation);assertTrue(f.observer.positionReturned(invocation,f.session,LISTENER,POSITION_PACKET,state(),5,120,ARRIVAL));assertNotNull(f.proof.applied());}
    @Test void headAloneCannotBecomeSubmission(){var f=new Fixture();assertNotNull(f.head(SEND_PACKET));assertFalse(f.proof.dispatched());}
    @Test void returnWithoutPrivateEntryCannotSubmit(){var f=new Fixture();assertFalse(f.observer.sendReturned(null,f.session,TRANSPORT,SEND_PACKET,state(),111));assertFalse(f.proof.dispatched());}
    @Test void wrongPacketReturnRevokesOnlyCurrentIssue(){var f=new Fixture();var entry=f.head(SEND_PACKET);assertFalse(f.observer.sendReturned(entry,f.session,TRANSPORT,new Object(),state(),111));assertTrue(f.proof.revoked());}
    @Test void wrongTransportReturnCannotSubmit(){var f=new Fixture();var entry=f.head(SEND_PACKET);assertFalse(f.observer.sendReturned(entry,f.session,new Object(),SEND_PACKET,state(),111));assertTrue(f.proof.revoked());}
    @Test void duplicateCurrentEntryRevokesItsOwnPendingIssue(){var f=new Fixture();assertNotNull(f.head(SEND_PACKET));assertNull(f.head(new Object()));assertTrue(f.proof.revoked());}
    @Test void configuredWrongCommandCannotObtainEntry(){var f=new Fixture();assertNull(f.observer.sendEntry(f.session,TRANSPORT,SEND_PACKET,state(),"warp other",110));assertTrue(f.proof.revoked());}
    @Test void lateOldSendReturnCannotRevokeNewNestedSubmission(){var old=new Fixture();var oldEntry=old.head(SEND_PACKET);var next=new Fixture();var nextPacket=new Object();next.dispatch(nextPacket);assertFalse(old.observer.sendReturned(oldEntry,next.session,TRANSPORT,SEND_PACKET,state(),120));assertTrue(next.proof.dispatched());assertFalse(next.proof.revoked());}
    @Test void oldThrowAndObserverFailureCannotRevokeReplacement(){var old=new Fixture();var oldEntry=old.head(SEND_PACKET);var next=new Fixture();next.dispatch(new Object());old.observer.failed(oldEntry,next.session);assertTrue(next.proof.dispatched());assertFalse(next.proof.revoked());}
    @Test void currentNativeThrowRevokesOwnAttemptWithoutCreatingSubmission(){var f=new Fixture();var entry=f.head(SEND_PACKET);f.observer.failed(entry,f.session);assertTrue(f.proof.attemptedSend());assertTrue(f.proof.revoked());assertFalse(f.proof.dispatched());}
    @Test void oldApplyReturnCannotBorrowNewNestedSession(){var old=new Fixture();old.dispatch(SEND_PACKET);var entry=old.observer.positionEntry(old.session,LISTENER,POSITION_PACKET,state());var next=new Fixture();next.dispatch(new Object());assertFalse(old.observer.positionReturned(entry,next.session,LISTENER,POSITION_PACKET,state(),5,120,ARRIVAL));assertNull(next.proof.applied());assertTrue(next.proof.dispatched());}
    @Test void oldApplyFailureCannotRevokeReplacement(){var old=new Fixture();old.dispatch(SEND_PACKET);var entry=old.observer.positionEntry(old.session,LISTENER,POSITION_PACKET,state());var next=new Fixture();next.dispatch(new Object());old.observer.failed(entry,next.session);assertTrue(next.proof.dispatched());assertFalse(next.proof.revoked());}
    @Test void applyEnteredBeforeActualSubmissionCannotBecomeAckLater(){var f=new Fixture();var entry=f.observer.positionEntry(f.session,LISTENER,POSITION_PACKET,state());assertNull(entry);f.dispatch(SEND_PACKET);assertFalse(f.observer.positionReturned(entry,f.session,LISTENER,POSITION_PACKET,state(),5,120,ARRIVAL));assertNull(f.proof.applied());}
    @Test void differentApplyPacketCannotQualifyActualEntry(){var f=new Fixture();f.dispatch(SEND_PACKET);var entry=f.observer.positionEntry(f.session,LISTENER,POSITION_PACKET,state());assertFalse(f.observer.positionReturned(entry,f.session,LISTENER,new Object(),state(),5,120,ARRIVAL));assertTrue(f.proof.revoked());}
    @Test void differentApplyListenerCannotQualifyActualEntry(){var f=new Fixture();f.dispatch(SEND_PACKET);var entry=f.observer.positionEntry(f.session,LISTENER,POSITION_PACKET,state());assertFalse(f.observer.positionReturned(entry,f.session,new Object(),POSITION_PACKET,state(),5,120,ARRIVAL));assertTrue(f.proof.revoked());}
    @Test void reentrantSameEpochPublicationBetweenHeadAndReturnDeniesAck(){var f=new Fixture();f.dispatch(SEND_PACKET);var entry=f.observer.positionEntry(f.session,LISTENER,POSITION_PACKET,state());var a=state();var changed=new LegacyTravelPublicationIdentity.State(a.root(),a.parent(),a.inputOwner(),a.cameraAuthority(),a.player(),a.world(),a.connection(),a.config(),a.revision(),a.journey(),a.service(),a.requestGeneration(),a.epoch(),a.publicationSerial()+1,a.activeLeg(),true,true,true);assertFalse(f.observer.positionReturned(entry,f.session,LISTENER,POSITION_PACKET,changed,5,120,ARRIVAL));assertTrue(f.proof.revoked());}
    @Test void duplicateReturnCannotCertifyAnotherNativeInvocation(){var f=new Fixture();var entry=f.head(SEND_PACKET);assertTrue(f.observer.sendReturned(entry,f.session,TRANSPORT,SEND_PACKET,state(),111));assertFalse(f.observer.sendReturned(entry,f.session,TRANSPORT,SEND_PACKET,state(),112));assertTrue(f.proof.revoked());}
    @Test void deadlineAtActualExitCannotBeExtended(){var f=new Fixture();var entry=f.head(SEND_PACKET);assertFalse(f.observer.sendReturned(entry,f.session,TRANSPORT,SEND_PACKET,state(),10100));assertTrue(f.proof.revoked());}
    @Test void oldPacketEnteringAfterNewWindowClosedCannotRevokeNewIssue(){var next=new Fixture();next.dispatch(new Object());assertNull(next.observer.sendEntry(next.session,TRANSPORT,SEND_PACKET,state(),"warp express",120));assertTrue(next.proof.dispatched());assertFalse(next.proof.revoked());}
    @Test void unattemptedWindowDoesNotAdoptAnotherNativeCommand(){var issue=issue();var proof=new AutomaticCommandReceipt.Submission(issue);proof.open();var session=new Object();var observer=new AutomaticCommandInvocationReceipt(session,issue,proof);assertNull(observer.sendEntry(session,TRANSPORT,SEND_PACKET,state(),"warp express",110));assertFalse(proof.revoked());assertFalse(proof.dispatched());}
}
