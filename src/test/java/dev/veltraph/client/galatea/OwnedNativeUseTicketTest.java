package dev.veltraph.client.galatea;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** STAGED, uncompiled/unrun: actual decrement, queue custody and submission fences are independent. */
class OwnedNativeUseTicketTest {
    private final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
    private final Object loadout=List.of("slot0:axe:1:componentsA","slot8:ability:1:componentsB","armor+offhand:exact");
    private OwnedNativeUseTicket.Frame frame(long now){return new OwnedNativeUseTicket.Frame(owner,4,player,world,connection,
            8,loadout,"frozen observed config",owner,owner,7,1000,now,true);}
    private OwnedNativeUseTicket queued(){var ticket=new OwnedNativeUseTicket(frame(100),1000);assertTrue(ticket.queued(0,1));return ticket;}
    private OwnedNativeUseTicket.Frame changed(long now,String field){var f=frame(now);return new OwnedNativeUseTicket.Frame(
            field.equals("owner")?new Object():f.owner(),field.equals("generation")?5:f.generation(),
            field.equals("player")?new Object():f.player(),field.equals("world")?new Object():f.world(),
            field.equals("connection")?new Object():f.connection(),field.equals("slot")?7:f.slot(),
            field.equals("loadout")?List.of("same names but different count/component"):f.loadout(),
            field.equals("config")?"changed config":f.config(),field.equals("root")?new Object():f.rootAuthority(),
            field.equals("camera")?new Object():f.cameraAuthority(),field.equals("epoch")?8:f.cameraEpoch(),
            field.equals("deadline")?2000:f.callerDeadlineMillis(),f.nowMillis(),!field.equals("health"));}
    @Test void queueIntentIsNotNativeConsumptionOrSubmission(){var t=queued();assertEquals(OwnedNativeUseTicket.Phase.QUEUED,t.phase());assertEquals(0,t.nativeConsumptions());assertEquals(0,t.outgoingSubmissions());}
    @Test void falseEmptyPollDoesNotRetireOwnedHead(){var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(101),0,0,false,true));assertTrue(t.headPending());assertEquals(0,t.nativeConsumptions());assertEquals(OwnedNativeUseTicket.Consumption.ALLOW_OWNED,t.consumed(frame(102),1,0,true,true));}
    @Test void falseNonemptyPollDoesNotRetireOrAdoptTail(){var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(101),2,2,false,true));assertTrue(t.headPending());assertEquals(0,t.nativeConsumptions());assertEquals(OwnedNativeUseTicket.Consumption.ALLOW_OWNED,t.consumed(frame(102),2,1,true,true));assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(103),1,0,true,true));}
    @Test void receiptNeedsActualOneEventDecrement(){var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(101),2,0,true,true));assertTrue(t.headPending());assertEquals(0,t.nativeConsumptions());}
    @Test void manualStopDropsOnlyOwnedOldestEventAndLeavesForeignTail(){var t=queued();int nativeQueue=2;t.revoke();
        int before=nativeQueue--;assertEquals(OwnedNativeUseTicket.Consumption.DROP_OWNED,t.consumed(frame(101),before,nativeQueue,true,true));
        assertEquals(1,nativeQueue,"Legitimate later manual event must remain queued");assertFalse(t.headPending());
        before=nativeQueue--;assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(102),before,nativeQueue,true,true));assertEquals(1,t.nativeConsumptions());assertEquals(0,t.outgoingSubmissions());}
    @Test void nativeReleaseForgetsQueueOwnershipBeforeLaterManualClick(){var t=queued();t.queueReleased();assertFalse(t.headPending());assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(101),1,0,true,true));assertEquals(0,t.nativeConsumptions());}
    @Test void drainWhileAlreadyUsingItemCannotArmOwnedInvocation(){var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.DROP_OWNED,t.consumed(frame(101),1,0,true,false));assertFalse(t.beginUse(frame(102)));assertEquals(1,t.nativeConsumptions());assertEquals(0,t.outgoingSubmissions());}
    @Test void contextAuthorityLoadoutConfigAndGenerationDriftDropOwnedEvent(){for(String field:List.of("owner","generation","player","world","connection","slot","loadout","config","root","camera","epoch","deadline","health")){
        var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.DROP_OWNED,t.consumed(changed(101,field),1,0,true,true),field);assertFalse(t.beginUse(frame(102)),field);assertEquals(0,t.outgoingSubmissions(),field);}}
    @Test void driftAfterConsumptionButBeforeUseVetoesInvocation(){for(String field:List.of("player","slot","loadout","config","root","epoch")){
        var t=queued();assertEquals(OwnedNativeUseTicket.Consumption.ALLOW_OWNED,t.consumed(frame(101),1,0,true,true));assertFalse(t.beginUse(changed(102,field)),field);assertEquals(0,t.outgoingSubmissions());}}
    @Test void manualStopBetweenConsumptionAndUseCannotSend(){var t=queued();t.consumed(frame(101),1,0,true,true);t.revoke();assertFalse(t.beginUse(frame(102)));assertEquals(0,t.outgoingSubmissions());}
    @Test void driftAtActualSubmissionVetoesUsePacket(){var t=queued();t.consumed(frame(101),1,0,true,true);assertTrue(t.beginUse(frame(102)));assertFalse(t.beforeOutgoing(changed(103,"loadout")));assertEquals(0,t.outgoingSubmissions());t.endUse();}
    @Test void clockRollbackCannotAuthorizeLatePacket(){var t=queued();t.consumed(frame(200),1,0,true,true);assertTrue(t.beginUse(frame(201)));assertFalse(t.beforeOutgoing(frame(199)));assertEquals(0,t.outgoingSubmissions());}
    @Test void originalDeadlineCannotBeExtendedByCallerRebinding(){var t=queued();assertEquals(1000,t.originalDeadlineMillis());assertEquals(OwnedNativeUseTicket.Consumption.DROP_OWNED,t.consumed(changed(999,"deadline"),1,0,true,true));assertEquals(1000,t.originalDeadlineMillis());}
    @Test void strictFiniteDeadlineAllowsBoundaryAndRejectsLateEvent(){var exact=queued();assertEquals(OwnedNativeUseTicket.Consumption.ALLOW_OWNED,exact.consumed(frame(1000),1,0,true,true));assertTrue(exact.beginUse(frame(1000)));assertTrue(exact.beforeOutgoing(frame(1000)));exact.outgoingSubmitted();assertEquals(1,exact.outgoingSubmissions());
        var late=queued();assertEquals(OwnedNativeUseTicket.Consumption.DROP_OWNED,late.consumed(frame(1001),1,0,true,true));assertEquals(0,late.outgoingSubmissions());}
    @Test void validConsumptionMayProduceNoOutgoingPacket(){var t=queued();t.consumed(frame(101),1,0,true,true);assertTrue(t.beginUse(frame(102)));t.endUse();assertEquals(1,t.nativeConsumptions());assertEquals(0,t.outgoingSubmissions());assertFalse(t.beforeOutgoing(frame(103)));}
    @Test void actualSubmissionDoesNotManufactureServerEffect(){var t=queued();t.consumed(frame(101),1,0,true,true);assertTrue(t.beginUse(frame(102)));assertTrue(t.beforeOutgoing(frame(103)));t.outgoingSubmitted();t.endUse();assertEquals(1,t.nativeConsumptions());assertEquals(1,t.outgoingSubmissions());assertEquals(OwnedNativeUseTicket.Phase.OUTGOING_SUBMITTED,t.phase());assertFalse(t.beginUse(frame(104)));}
    @Test void revocationAfterAlreadySubmittedPacketPreservesHonestNativeReceipt(){var t=queued();t.consumed(frame(101),1,0,true,true);assertTrue(t.beginUse(frame(102)));assertTrue(t.beforeOutgoing(frame(103)));t.revoke();t.outgoingSubmitted();assertEquals(1,t.outgoingSubmissions());assertEquals(OwnedNativeUseTicket.Phase.REVOKED,t.phase());assertFalse(t.beforeOutgoing(frame(104)));t.endUse();}
    @Test void synchronousReturnReceiptSurvivesReentrantEndWithoutPermittingNewPacket(){var t=queued();t.consumed(frame(101),1,0,true,true);assertTrue(t.beginUse(frame(102)));assertTrue(t.beforeOutgoing(frame(103)));t.endUse();t.revoke();t.outgoingSubmitted();assertEquals(1,t.outgoingSubmissions());assertThrows(IllegalStateException.class,t::outgoingSubmitted);assertFalse(t.beforeOutgoing(frame(104)));}
    @Test void issueRequiresHealthyExactOwnershipAndFiniteOriginalCallerDeadline(){assertThrows(IllegalArgumentException.class,()->new OwnedNativeUseTicket(frame(100),100));for(String field:List.of("root","camera","health","deadline"))assertThrows(IllegalArgumentException.class,()->new OwnedNativeUseTicket(changed(100,field),1000),field);}
    @Test void queueRefusalNeverClaimsNativeConsumption(){var t=new OwnedNativeUseTicket(frame(100),1000);assertFalse(t.queued(1,2));assertFalse(t.headPending());assertEquals(0,t.nativeConsumptions());assertEquals(0,t.outgoingSubmissions());}
    @Test void unqueuedForeignEventCannotBeAdopted(){var t=new OwnedNativeUseTicket(frame(100),1000);assertEquals(OwnedNativeUseTicket.Consumption.FOREIGN,t.consumed(frame(101),1,0,true,true));assertFalse(t.beginUse(frame(102)));assertEquals(0,t.nativeConsumptions());}
}
