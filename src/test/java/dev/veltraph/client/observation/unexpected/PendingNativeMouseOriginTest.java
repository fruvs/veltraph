package dev.veltraph.client.observation.unexpected;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
final class PendingNativeMouseOriginTest {
    final Context original=new Context(new Activation("farm",4,true),1,2,3);
    @Test void actualMatchingNativeOutcomeExplainsOnlyOnce(){
        var origin=new PendingNativeMouseOrigin();origin.record(original,1000);
        assertTrue(origin.consume(original,1050));assertFalse(origin.consume(original,1050));
    }
    @Test void maximumExistingWindowStillAcceptsButNextMillisecondExpires(){
        var origin=new PendingNativeMouseOrigin();origin.record(original,1000);assertTrue(origin.consume(original,1150));
        origin.record(original,1000);assertFalse(origin.consume(original,1151));assertFalse(origin.consume(original,1150));
    }
    @Test void nativeOriginCannotCrossOwnerGenerationOrAnyNativeEpoch(){
        for(var next:new Context[]{new Context(new Activation("farm",5,true),1,2,3),
            new Context(new Activation("slayer",4,true),1,2,3),new Context(new Activation("farm",4,false),1,2,3),
            new Context(original.activation(),2,2,3),new Context(original.activation(),1,3,3),new Context(original.activation(),1,2,4)}){
            var origin=new PendingNativeMouseOrigin();origin.record(original,1000);assertFalse(origin.consume(next,1001));
            assertFalse(origin.consume(original,1002),"Rejected context cannot be retried after returning to the old owner");
        }
    }
    @Test void clockRollbackDoesNotMintANewNativeReceipt(){
        var origin=new PendingNativeMouseOrigin();origin.record(original,1000);assertFalse(origin.consume(original,999));
        assertFalse(origin.consume(original,1000));
    }
    @Test void overflowingElapsedTimeDoesNotBecomeARecentMouseEvent(){
        var origin=new PendingNativeMouseOrigin();origin.record(original,Long.MIN_VALUE);assertFalse(origin.consume(original,Long.MAX_VALUE));
    }
    @Test void clearingOnContextOrDisconnectAlsoDiscardsThePendingOrigin(){
        var origin=new PendingNativeMouseOrigin();origin.record(original,1000);origin.clear();assertFalse(origin.consume(original,1001));
    }
    @Test void newestEligibleMoveReplacesEarlierPendingContext(){
        var next=new Context(new Activation("slayer",8,true),1,2,3);var origin=new PendingNativeMouseOrigin();
        origin.record(original,1000);origin.record(next,1010);assertTrue(origin.consume(next,1011));assertFalse(origin.consume(original,1012));
    }
    @Test void rejectedOriginLeavesSubstantialOutsideRotationUnexpected(){
        var changed=new Context(new Activation("farm",5,true),1,2,3);var origin=new PendingNativeMouseOrigin();
        var monitor=new UnexpectedBehaviorMonitor(f->{});monitor.context(changed,1001);origin.record(original,1000);
        if(origin.consume(changed,1001))monitor.nativeReceipt(changed,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(60,0),.01,1001,150);
        var finding=monitor.observe(new Observation(changed,Kind.ROTATION,Value.rotation(0,0),Value.rotation(60,0),1002));
        assertEquals(Classification.UNEXPECTED,finding.classification());assertFalse(finding.evidence().contains(Cause.NATIVE_MOUSE_EVENT));
    }
    @Test void freshOriginPreservesNativeUiClassificationWithoutClaimingHumanIdentity(){
        var origin=new PendingNativeMouseOrigin();var monitor=new UnexpectedBehaviorMonitor(f->{});monitor.context(original,1000);
        origin.record(original,1000);assertTrue(origin.consume(original,1001));
        monitor.nativeReceipt(original,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(60,0),.01,1001,150);
        var finding=monitor.observe(new Observation(original,Kind.ROTATION,Value.rotation(0,0),Value.rotation(60,0),1002));
        assertEquals(Classification.EXPLAINED,finding.classification());assertEquals("Native mouse UI event observed; human identity is unknown",finding.detail());
    }
}
