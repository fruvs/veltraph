package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryDurabilityTest extends VisitorServedHistoryTestSupport {
    VisitorServedHistoryJournal.Served served(){var j=journal();j.receiveInventory(consumed(11),context(),1);j.receiveMenu(ack(12),context(),1);return j.finish(true,context(),1).orElseThrow();}
    void makeDirectoryObstruction(Path path)throws Exception{Files.delete(path);Files.createDirectory(path);}
    @Test void failedServeInvalidatesPreviouslyFreshNewAndRetainsProvenance()throws Exception{
        var s=store();s.commitBook(book(false,0),context(),0);assertEquals(VisitorHistoryStore.Newness.NEW,s.observe(context(),"Beth",1,100).newness());
        Path p=temp.resolve("history.json");makeDirectoryObstruction(p);var proof=served();assertThrows(IOException.class,()->s.commitServe(proof,context(),1));
        assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());var pending=s.pendingStatus(scope).orElseThrow();assertTrue(pending.provenance().contains("request=10"));assertEquals(1,pending.originalObservedAt());
        Files.delete(p);assertTrue(s.retryPending(context(),2));assertEquals(VisitorHistoryStore.Newness.SEEN,s.observe(context(),"Beth",2,100).newness());assertTrue(s.pendingStatus(scope).isEmpty());assertFalse(s.commitServe(proof,context(),1));
        var loaded=store();loaded.load();assertEquals(VisitorHistoryStore.Newness.SEEN,loaded.observe(context(),"Beth",2,100).newness());
    }
    @Test void failedBookInvalidatesPriorCoverageAndRetryUsesOriginalTime()throws Exception{
        var s=store();s.commitBook(book(false,0),context(),0);Path p=temp.resolve("history.json");makeDirectoryObstruction(p);var proof=book(false,10);
        assertThrows(IOException.class,()->s.commitBook(proof,context(),10));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",11,100).newness());
        Files.delete(p);assertTrue(s.retryPending(context(),120));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",120,100).newness());assertFalse(s.commitBook(proof,context(),10));
    }
    @Test void cancelledReaderAfterAcceptedWriteFailureDoesNotLoseObservedFact()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();var r=reader();r.start(request(1,10,-1,true),10);r.observe(page(2,1,10,0,List.of(visitor(10,"Beth",true))),context(),10);var proof=r.completion().orElseThrow();
        assertThrows(IOException.class,()->s.commitBook(proof,context(),10));r.cancel();Files.delete(p);assertTrue(s.retryPending(context(),11));assertEquals(VisitorHistoryStore.Newness.SEEN,s.observe(context(),"Beth",11,100).newness());
    }
    @Test void pendingFactCannotBeConsumedByDifferentStore()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();var proof=book(true,10);assertThrows(IOException.class,()->s.commitBook(proof,context(),10));Files.delete(p);var other=store();assertFalse(other.commitBook(proof,context(),10));assertTrue(s.retryPending(context(),11));
    }
    @Test void foreignSessionCannotRetryOrClearUnknownLatch()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();assertThrows(IOException.class,()->s.commitBook(book(false,10),context(),10));Files.delete(p);
        var foreign=new VisitorHistoryContext(scope,player,new Object(),connection,1,1,"foreign world");assertFalse(s.retryPending(foreign,11));assertTrue(s.pendingStatus(scope).isPresent());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",11,100).newness());assertTrue(s.retryPending(context(),11));
    }
    @Test void failedRetryRemainsUnknownWithoutRenewingFact()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();assertThrows(IOException.class,()->s.commitBook(book(false,10),context(),10));assertThrows(IOException.class,()->s.retryPending(context(),11));assertEquals(10,s.pendingStatus(scope).orElseThrow().originalObservedAt());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",11,100).newness());
    }
    @Test void reloadCannotDiscardPendingObservedFact()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();assertThrows(IOException.class,()->s.commitBook(book(false,10),context(),10));Files.delete(p);assertThrows(IOException.class,s::load);assertTrue(s.pendingStatus(scope).isPresent());assertTrue(s.retryPending(context(),11));
    }
    @Test void repairOfUnreadableFilePermitsExactPendingRetry()throws Exception{
        Path p=temp.resolve("history.json");Files.writeString(p,"broken");var s=store();assertThrows(IOException.class,s::load);var proof=served();assertThrows(IOException.class,()->s.commitServe(proof,context(),1));assertEquals("broken",Files.readString(p));Files.writeString(p,"{\"version\":1,\"scopes\":[]}");assertTrue(s.retryPending(context(),2));assertEquals(VisitorHistoryStore.Newness.SEEN,s.observe(context(),"Beth",2,100).newness());
    }
    @Test void otherScopesDoNotBorrowFailureLatch()throws Exception{
        var s=store();s.commitBook(book(false,0),context(),0);Path p=temp.resolve("history.json");makeDirectoryObstruction(p);assertThrows(IOException.class,()->s.commitServe(served(),context(),1));var other=new VisitorHistoryContext(new VisitorHistoryScope(UUID.randomUUID(),"other-profile","other-garden"),player,world,connection,1,1,"other confirmation");assertTrue(s.pendingStatus(other.scope()).isEmpty());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(other,"Beth",2,100).newness());
    }
    @Test void foreignProfileCannotRetryOriginalProfileFact()throws Exception{
        Path p=temp.resolve("history.json");Files.createDirectory(p);var s=store();assertThrows(IOException.class,()->s.commitBook(book(false,10),context(),10));Files.delete(p);var other=new VisitorHistoryContext(new VisitorHistoryScope(scope.accountId(),"profileB","gardenB"),player,world,connection,1,1,"other native confirmation");assertFalse(s.retryPending(other,11));assertTrue(s.pendingStatus(scope).isPresent());assertTrue(s.retryPending(context(),11));
    }
    @Test void newProofCannotReplaceAnUnsavedConfirmedServe()throws Exception{
        var s=store();s.commitBook(book(false,0),context(),0);Path p=temp.resolve("history.json");makeDirectoryObstruction(p);assertThrows(IOException.class,()->s.commitServe(served(),context(),1));Files.delete(p);assertFalse(s.commitBook(book(false,2),context(),2));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",2,100).newness());assertTrue(s.retryPending(context(),3));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",3,100).newness());assertTrue(s.reconciliationStatus(scope).isPresent());
    }
}
