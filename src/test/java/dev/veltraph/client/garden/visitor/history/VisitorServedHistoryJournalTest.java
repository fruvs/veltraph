package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorServedHistoryJournalTest extends VisitorServedHistoryTestSupport {
    @Test void localCompleteAndSubmissionAloneCannotRecord(){assertTrue(journal().finish(true,context(),1).isEmpty());}
    @Test void actualInventoryAndMenuAckMintSingleUseScopedServed()throws Exception{var j=journal();j.receiveInventory(consumed(11),context(),1);j.receiveMenu(ack(12),context(),1);var proof=j.finish(true,context(),1).orElseThrow();assertTrue(j.finish(true,context(),1).isEmpty());var s=store();assertTrue(s.commitServe(proof,context(),1));assertFalse(s.commitServe(proof,context(),1));assertEquals(VisitorHistoryStore.Newness.SEEN,s.observe(context(),"Beth",2,100).newness());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Jacob",2,100).newness());}
    @Test void menuFirstAndInventoryLaterStillCompletes(){var j=journal();j.receiveMenu(ack(11),context(),1);j.receiveInventory(consumed(12),context(),1);assertTrue(j.finish(true,context(),1).isPresent());}
    @Test void runtimeFailureCannotBecomeSeen(){var j=journal();j.receiveInventory(consumed(11),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(false,context(),1).isEmpty());j.cancel();assertTrue(j.finish(true,context(),2).isEmpty());}
    @Test void predictedInventoryWithoutNativeHandlerIsRejected(){var j=journal();j.receiveInventory(new VisitorServedHistoryJournal.InventoryReceipt(context(),11,10,1,inventory(5),false,true,true),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void partialServerInventoryCannotProveHistory(){var j=journal();j.receiveInventory(new VisitorServedHistoryJournal.InventoryReceipt(context(),11,10,1,inventory(5),true,false,true),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void foreignMenuCannotAcknowledgeServe(){var j=journal();j.receiveInventory(consumed(11),context(),1);j.receiveMenu(new VisitorServedHistoryJournal.MenuReceipt(context(),new Object(),4,6,12,10,1,true,false,true,true),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void noAckTimesOutWithoutSeen(){var j=journal();j.receiveInventory(consumed(11),context(),1);assertTrue(j.finish(true,context(),1000).isEmpty());}
    @Test void foreignProfileCancelsOutcome(){var j=journal();var other=new VisitorHistoryContext(new VisitorHistoryScope(scope.accountId(),"profileB","gardenB"),player,world,connection,1,1,"test");j.receiveInventory(consumed(11),other,1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void cancellationClearsPendingConsumption(){var j=journal();j.receiveInventory(consumed(11),context(),1);j.cancel();j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void wrongConsumptionQuantityIsNotServed(){var j=journal();j.receiveInventory(new VisitorServedHistoryJournal.InventoryReceipt(context(),11,10,1,inventory(4),true,true,true),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void unrelatedToolMutationIsNotServed(){var j=journal();var changed=inventory(5);changed.set(1,new VisitorServedHistoryJournal.Stack("changed sword components",1));j.receiveInventory(new VisitorServedHistoryJournal.InventoryReceipt(context(),11,10,1,changed,true,true,true),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void olderInventoryReceiptCannotAcknowledgeRequest(){var j=journal();j.receiveInventory(consumed(9),context(),1);j.receiveMenu(ack(12),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
    @Test void wrongNativeRequestAssociationIsNotServed(){var j=journal();j.receiveInventory(new VisitorServedHistoryJournal.InventoryReceipt(context(),11,9,1,inventory(5),true,true,true),context(),1);assertTrue(j.finish(true,context(),1).isEmpty());}
}
