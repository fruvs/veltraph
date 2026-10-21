package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryTest extends VisitorHistoryTestSupport {
    @Test void unknownContextNeverMarksEveryoneNew(){assertEquals(VisitorHistoryStore.Newness.UNKNOWN,store().observe(null,"Beth",0,100).newness());}
    @Test void newEmptyStoreHasUnknownCoverage(){assertEquals(VisitorHistoryStore.Newness.UNKNOWN,store().observe(context(),"Beth",0,100).newness());}
    @Test void completeReceivedRosterDistinguishesSeenAndNew()throws Exception{var s=store();assertTrue(s.commitBook(book(false,10),context(),10));assertEquals(VisitorHistoryStore.Newness.NEW,s.observe(context(),"Beth",11,100).newness());}
    @Test void outsideCompleteRosterStaysUnknown()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Unlisted NPC",11,100).newness());}
    @Test void seenNameSurvivesReloadButAbsenceDoesNot()throws Exception{var s=store();s.commitBook(book(true,10),context(),10);var loaded=store();loaded.load();assertEquals(VisitorHistoryStore.Newness.SEEN,loaded.observe(context(),"Beth",11,100).newness());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,loaded.observe(context(),"Jacob",11,100).newness());}
    @Test void reloadOfUnseenRosterCannotAuthorizeNew()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);var loaded=store();loaded.load();assertEquals(VisitorHistoryStore.Newness.UNKNOWN,loaded.observe(context(),"Beth",11,100).newness());}
    @Test void foreignProfileNeverBorrowsNames()throws Exception{var s=store();s.commitBook(book(true,10),context(),10);var other=new VisitorHistoryContext(new VisitorHistoryScope(scope.accountId(),"profile-B","Garden-B"),player,world,connection,1,1,"test");assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(other,"Beth",11,100).newness());}
    @Test void foreignAccountNeverBorrowsNames()throws Exception{var s=store();s.commitBook(book(true,10),context(),10);var other=new VisitorHistoryContext(new VisitorHistoryScope(UUID.randomUUID(),scope.confirmedProfileId(),scope.confirmedGardenId()),player,world,connection,1,1,"test");assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(other,"Beth",11,100).newness());}
    @Test void profileConfirmationAbaRetiresAbsentEvidence()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);var changed=new VisitorHistoryContext(scope,player,world,connection,2,1,"new confirmation");assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(changed,"Beth",11,100).newness());}
    @Test void freshnessAndRollbackDenyAbsence()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",9,100).newness());assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",111,100).newness());}
    @Test void malformedFileIsPreservedAndCannotBeOverwritten()throws Exception{Path p=temp.resolve("history.json");Files.writeString(p,"broken");var s=store();assertThrows(IOException.class,s::load);assertThrows(IOException.class,()->s.commitBook(book(true,10),context(),10));assertEquals("broken",Files.readString(p));}
    @Test void failedAtomicSaveRollsBackSeenState()throws Exception{Path p=temp.resolve("directory");Files.createDirectory(p);var s=new VisitorHistoryStore(p);assertThrows(IOException.class,()->s.commitBook(book(true,10),context(),10));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,s.observe(context(),"Beth",10,100).newness());}
    @Test void completeBookCannotCommitInForeignSession()throws Exception{var other=new VisitorHistoryContext(scope,player,new Object(),connection,1,1,"test");assertFalse(store().commitBook(book(true,10),other,10));}
    @Test void completionCannotBeConsumedOnLaterTick()throws Exception{assertFalse(store().commitBook(book(true,10),context(),11));}
    @Test void formattingCanonicalizationPreservesCaseAndExactName(){assertEquals("Beth",VisitorHistoryStore.name("  §dBeth  "));assertNotEquals(VisitorHistoryStore.name("Beth"),VisitorHistoryStore.name("beth"));}
    @Test void symlinkDestinationPreservesTarget()throws Exception{Path real=temp.resolve("real");Files.writeString(real,"unchanged");Files.createSymbolicLink(temp.resolve("history.json"),real);assertThrows(IOException.class,()->store().commitBook(book(true,10),context(),10));assertEquals("unchanged",Files.readString(real));}
    @Test void defaultNativeResolverHasNoLiteralLoreFallback(){assertEquals(VisitorHistoryStore.Newness.UNKNOWN,VisitorHistoryResolver.UNKNOWN.observe("new visitor Beth").newness());}
    @Test void duplicateJsonMemberIsRejectedAndPreserved()throws Exception{var p=temp.resolve("history.json");var data="{\"version\":1,\"version\":1,\"scopes\":[]}";Files.writeString(p,data);assertThrows(IOException.class,store()::load);assertEquals(data,Files.readString(p));}
    @Test void invalidVersionTypeIsRejected()throws Exception{Files.writeString(temp.resolve("history.json"),"{\"version\":1.1,\"scopes\":[]}");assertThrows(IOException.class,store()::load);}
    @Test void completedBookProofIsOneShot()throws Exception{var s=store();var proof=book(true,10);assertTrue(s.commitBook(proof,context(),10));assertFalse(s.commitBook(proof,context(),10));}
    @Test void cancelledReaderRevokesHandedBookBeforeCommit()throws Exception{var r=reader();r.start(request(1,10,-1,true),10);r.observe(page(2,1,10,0,List.of(visitor(10,"Beth",true))),context(),10);var proof=r.completion().orElseThrow();r.cancel();assertFalse(store().commitBook(proof,context(),10));}
    @Test void restartedReaderRevokesPreviousCompleteBook()throws Exception{var r=reader();r.start(request(1,10,-1,true),10);r.observe(page(2,1,10,0,List.of(visitor(10,"Beth",true))),context(),10);var proof=r.completion().orElseThrow();r.start(request(3,10,-1,true),10);assertFalse(store().commitBook(proof,context(),10));}
    @Test void resolverRechecksScopeAfterClockCallback()throws Exception{var s=store();s.commitBook(book(false,10),context(),10);var live=new java.util.concurrent.atomic.AtomicReference<>(context());var h=VisitorHistoryResolver.scoped(s,live::get,()->{live.set(new VisitorHistoryContext(scope,player,world,connection,2,1,"changed native confirmation"));return 11;},100);assertEquals(VisitorHistoryStore.Newness.UNKNOWN,h.observe("Beth").newness());}
}
