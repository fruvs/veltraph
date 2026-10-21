package dev.veltraph.client.garden.jacob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JacobRewardJournalFileTest {
    @TempDir Path directory;
    private static final String HASH="b".repeat(64);
    @Test void verifiedWriteIsActualFileBackedAndSurvivesNewStore()throws Exception {var file=directory.resolve("claim.json");var store=new JacobRewardJournalFile(file);var before=store.read();var id=UUID.randomUUID();var saved=store.write(before,before.value().prepare(id,"configured-local-profile",HASH));assertTrue(store.current(saved));var fresh=new JacobRewardJournalFile(file);assertEquals(id,fresh.read().value().pending().orElseThrow().id());assertFalse(fresh.read().value().canPrepare());}
    @Test void staleDifferentWriterCannotReplacePending()throws Exception {var file=directory.resolve("claim.json");var a=new JacobRewardJournalFile(file);var b=new JacobRewardJournalFile(file);var oldA=a.read();var oldB=b.read();var first=UUID.randomUUID();a.write(oldA,oldA.value().prepare(first,"local",HASH));assertThrows(java.io.IOException.class,()->b.write(oldB,oldB.value().prepare(UUID.randomUUID(),"other",HASH)));assertEquals(first,new JacobRewardJournalFile(file).read().value().pending().orElseThrow().id());}
    @Test void busyLockRefusesWriteWithoutVerifiedGate()throws Exception {var file=directory.resolve("claim.json");var store=new JacobRewardJournalFile(file);var before=store.read();try(var channel=FileChannel.open(directory.resolve("claim.json.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.lock()){assertThrows(java.io.IOException.class,()->store.write(before,before.value().prepare(UUID.randomUUID(),"local",HASH)));assertFalse(Files.exists(file));}}
    @Test void ioFailureProducesNoVerifiedWriteOrSilentReset()throws Exception {var file=directory.resolve("as-directory");Files.createDirectory(file);var store=new JacobRewardJournalFile(file);assertThrows(java.io.IOException.class,store::read);assertTrue(Files.isDirectory(file));}
    @Test void corruptFileRemainsRefusedAcrossNewStore()throws Exception {var file=directory.resolve("claim.json");Files.writeString(file,"{invalid");var store=new JacobRewardJournalFile(file);assertThrows(java.io.IOException.class,store::read);assertThrows(java.io.IOException.class,()->new JacobRewardJournalFile(file).read());assertEquals("{invalid",Files.readString(file));}
    @Test void disappearedPreviouslyObservedJournalRefusesReset()throws Exception {var file=directory.resolve("claim.json");var store=new JacobRewardJournalFile(file);var before=store.read();store.write(before,before.value().prepare(UUID.randomUUID(),"local",HASH));Files.delete(file);assertThrows(java.io.IOException.class,store::read);}
    @Test void knownReceiptResolutionChangesSavedTokenCurrentness()throws Exception {var file=directory.resolve("claim.json");var store=new JacobRewardJournalFile(file);var before=store.read();var id=UUID.randomUUID();var pending=store.write(before,before.value().prepare(id,"local",HASH));var actual=UUID.randomUUID();var acknowledged=store.write(pending.after(),pending.after().value().acknowledge(id,actual));assertFalse(store.current(pending));assertTrue(store.current(acknowledged));assertEquals(actual,new JacobRewardJournalFile(file).read().value().entries().getFirst().receiptId());}
    @Test void failedOutcomeWriteCannotReleaseSeparateIntent()throws Exception {
        var intentFile=new JacobRewardJournalFile(directory.resolve("intent.json"));var empty=intentFile.read();var id=UUID.randomUUID();var pending=intentFile.write(empty,empty.value().prepare(id,"local",HASH));
        var outcomes=new JacobRewardJournalFile(directory.resolve("outcome.json"));var before=outcomes.read();
        try(var channel=FileChannel.open(directory.resolve("outcome.json.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.lock()){
            assertThrows(java.io.IOException.class,()->outcomes.write(before,before.value().recordQualifiedOutcome(pending.after().value().pending().orElseThrow(),UUID.randomUUID())));
        }
        assertEquals(id,new JacobRewardJournalFile(directory.resolve("intent.json")).read().value().pending().orElseThrow().id());
    }
    @Test void visibleUnverifiedOutcomeDoesNotAlterWriteAheadIntent()throws Exception {
        var intents=new JacobRewardJournalFile(directory.resolve("intent.json"));var empty=intents.read();var id=UUID.randomUUID();var pending=intents.write(empty,empty.value().prepare(id,"local",HASH));
        var candidate=JacobRewardJournal.empty().recordQualifiedOutcome(pending.after().value().pending().orElseThrow(),UUID.randomUUID());
        Files.writeString(directory.resolve("outcome.json"),candidate.encode());
        assertTrue(new JacobRewardJournalFile(directory.resolve("intent.json")).read().value().pending().isPresent());assertFalse(intents.read().value().canPrepare());
    }
    @Test void verifiedOutcomePrecedesIntentReleaseAndMustMatchExactly()throws Exception {
        var intents=new JacobRewardJournalFile(directory.resolve("intent.json"));var empty=intents.read();var id=UUID.randomUUID();var pending=intents.write(empty,empty.value().prepare(id,"local",HASH));
        var outcomes=new JacobRewardJournalFile(directory.resolve("outcome.json"));var first=outcomes.read();var receipt=UUID.randomUUID();var saved=outcomes.write(first,first.value().recordQualifiedOutcome(pending.after().value().pending().orElseThrow(),receipt));
        assertTrue(outcomes.current(saved));assertTrue(intents.read().value().pending().isPresent());
        var released=intents.write(pending.after(),pending.after().value().acknowledge(id,receipt));var record=released.after().value().entries().getFirst();assertTrue(saved.after().value().hasQualifiedOutcome(record));
        assertFalse(saved.after().value().hasQualifiedOutcome(new JacobRewardJournal.Entry(id,"local",HASH,JacobRewardJournal.Phase.ACKNOWLEDGED,UUID.randomUUID())));
    }
    @Test void explicitReconciliationHasNoReceiptIdAndKeepsHistory()throws Exception {var file=directory.resolve("claim.json");var store=new JacobRewardJournalFile(file);var before=store.read();var id=UUID.randomUUID();var prepared=store.write(before,before.value().prepare(id,"local",HASH));var resolved=store.write(prepared.after(),prepared.after().value().reconcile(id,HASH,JacobRewardJournal.Decision.APPLIED));assertTrue(store.current(resolved));var e=new JacobRewardJournalFile(file).read().value().entries().getFirst();assertNull(e.receiptId());assertEquals(JacobRewardJournal.Phase.RECONCILED_APPLIED,e.phase());}
}
