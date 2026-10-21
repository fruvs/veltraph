package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryCompetingFactTest extends VisitorServedHistoryTestSupport {
    VisitorLogbookReader.CompletedBook observedBook(long at,long sequence,boolean seen){
        var r=reader();r.start(request(sequence-1,at,-1,true),at);r.observe(page(sequence,sequence-1,at,0,List.of(visitor(10,"Beth",seen))),context(),at);return r.completion().orElseThrow();
    }
    VisitorServedHistoryJournal.Served observedServe(){var j=journal();j.receiveInventory(consumed(11),context(),1);j.receiveMenu(ack(12),context(),1);return j.finish(true,context(),1).orElseThrow();}
    VisitorHistoryStore failedBook()throws Exception{
        Files.createDirectory(temp.resolve("history.json"));var s=store();assertThrows(IOException.class,()->s.commitBook(observedBook(0,2,false),context(),0));return s;
    }
    @Test void oldBookRetryCannotRestoreNewAfterLaterConfirmedServe()throws Exception{
        var s=failedBook();var served=observedServe();assertFalse(s.commitServe(served,context(),1));assertEquals(Set.of("Beth"),s.reconciliationStatus(scope).orElseThrow().requiredSeenNames());
        Files.delete(temp.resolve("history.json"));assertTrue(s.retryPending(context(),2));assertTrue(s.pendingStatus(scope).isEmpty());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());assertTrue(s.reconciliationStatus(scope).isPresent());
    }
    @Test void rejectedNewBookInvalidatesCoverageEvenWithoutNewSeenNames()throws Exception{
        var s=failedBook();assertFalse(s.commitBook(observedBook(1,4,false),context(),1));Files.delete(temp.resolve("history.json"));assertTrue(s.retryPending(context(),2));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());assertEquals(4,s.reconciliationStatus(scope).orElseThrow().latestReceivedSequence());
    }
    @Test void repeatedCompetingProofCannotClearOrRenewLatch()throws Exception{
        var s=failedBook();var served=observedServe();assertFalse(s.commitServe(served,context(),1));assertFalse(s.commitServe(served,context(),1));Files.delete(temp.resolve("history.json"));assertTrue(s.retryPending(context(),2));assertEquals(1,s.reconciliationStatus(scope).orElseThrow().latestObservedAt());assertEquals(12,s.reconciliationStatus(scope).orElseThrow().latestReceivedSequence());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());
    }
    @Test void newerBookMissingObservedServedNameCannotReconcile()throws Exception{
        var s=failedBook();s.commitServe(observedServe(),context(),1);Files.delete(temp.resolve("history.json"));s.retryPending(context(),2);assertTrue(s.commitBook(observedBook(3,14,false),context(),3));assertTrue(s.reconciliationStatus(scope).isPresent());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",3,100).newness());
    }
    @Test void freshCompleteBookContainingAllRetainedSeenFactsReconciles()throws Exception{
        var s=failedBook();s.commitServe(observedServe(),context(),1);Files.delete(temp.resolve("history.json"));s.retryPending(context(),2);assertTrue(s.commitBook(observedBook(3,14,true),context(),3));assertTrue(s.reconciliationStatus(scope).isEmpty());assertEquals(VisitorHistoryStore.Newness.SEEN,s.observe(context(),"Beth",3,100).newness());
    }
    @Test void oldReceivedSequenceCannotReconcileDespiteNewClockTime()throws Exception{
        var s=failedBook();s.commitServe(observedServe(),context(),1);Files.delete(temp.resolve("history.json"));s.retryPending(context(),2);assertTrue(s.commitBook(observedBook(3,6,true),context(),3));assertTrue(s.reconciliationStatus(scope).isPresent());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",3,100).newness());
    }
    @Test void reloadKeepsReconciliationLatchAfterOriginalPendingIsSaved()throws Exception{
        var s=failedBook();s.commitServe(observedServe(),context(),1);Files.delete(temp.resolve("history.json"));s.retryPending(context(),2);s.load();assertTrue(s.reconciliationStatus(scope).isPresent());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());
    }
    @Test void cancelledCompetingBookIsNotAcceptedEvidence()throws Exception{
        var s=failedBook();var r=reader();r.start(request(3,1,-1,true),1);r.observe(page(4,3,1,0,List.of(visitor(10,"Beth",true))),context(),1);var proof=r.completion().orElseThrow();r.cancel();assertFalse(s.commitBook(proof,context(),1));assertTrue(s.reconciliationStatus(scope).isEmpty());Files.delete(temp.resolve("history.json"));assertTrue(s.retryPending(context(),2));assertEquals(VisitorHistoryStore.Newness.NEW,s.observe(context(),"Beth",2,100).newness());
    }
}
