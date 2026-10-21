package dev.veltraph.client.navigation.mc;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ordering evidence only; real Root/native scene and transition authenticity still require whole native tests. */
final class PassiveTravelLifecycleSlotTest {
    @Test void captureDoesNotReplaceOriginal(){var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertSame(old,slot.value());assertSame(old,ticket.before());assertTrue(ticket.rawCurrent());assertFalse(ticket.ready());}
    @Test void preparationPreservesOriginalUntilCommit(){var old=new Object();var next=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertTrue(ticket.prepare(next));assertSame(old,slot.value());assertTrue(ticket.ready());assertTrue(ticket.commit());assertSame(next,slot.value());}
    @Test void rawCommitWithoutPreparationRefuses(){var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertFalse(ticket.commit());assertSame(old,slot.value());assertFalse(ticket.prepare(new Object()));}
    @Test void nullSuccessorRevokesWithoutReplacement(){var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertFalse(ticket.prepare(null));assertFalse(ticket.prepare(new Object()));assertFalse(ticket.commit());assertSame(old,slot.value());}
    @Test void duplicatePrepareCannotOverwritePreparedSuccessor(){var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertTrue(ticket.prepare(new Object()));assertFalse(ticket.prepare(new Object()));assertFalse(ticket.commit());assertSame(old,slot.value());}
    @Test void duplicateCommitCannotReplay(){var old=new Object();var next=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertTrue(ticket.prepare(next));assertTrue(ticket.commit());assertFalse(ticket.commit());assertFalse(ticket.rawCurrent());assertSame(next,slot.value());}
    @Test void foreignCompetingContinuationCannotOverwriteSuccessor(){var old=new Object();var next=new Object();var foreign=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var first=slot.capture();var stale=slot.capture();assertTrue(first.prepare(next));assertTrue(stale.prepare(foreign));assertTrue(first.commit());assertFalse(stale.rawCurrent());assertFalse(stale.commit());assertSame(next,slot.value());}
    @Test void sameReferenceNoChangeStillConsumesOriginalRevision(){var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var first=slot.capture();var stale=slot.capture();assertTrue(first.prepare(old));assertTrue(stale.prepare(new Object()));assertTrue(first.commit());assertSame(old,slot.value());assertFalse(stale.rawCurrent());assertFalse(stale.commit());}
    @Test void nextGenuineStepStartsFromPreviouslyCommittedValue(){var old=new Object();var middle=new Object();var last=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var first=slot.capture();assertTrue(first.prepare(middle));assertTrue(first.commit());var second=slot.capture();assertSame(middle,second.before());assertTrue(second.prepare(last));assertTrue(second.commit());assertSame(last,slot.value());}
    @Test void foreignThreadCannotCapturePrepareOrCommit() throws Exception {
        var old=new Object();var slot=new PassiveTravelLifecycleSlot<>(old);var ticket=slot.capture();assertTrue(ticket.prepare(new Object()));
        var executor=Executors.newSingleThreadExecutor();try{var result=executor.submit(()->new boolean[]{slot.capture()==null,ticket.rawCurrent(),ticket.prepare(new Object()),ticket.commit()}).get(5,TimeUnit.SECONDS);
            assertTrue(result[0]);assertFalse(result[1]);assertFalse(result[2]);assertFalse(result[3]);assertSame(old,slot.value());assertFalse(ticket.commit());
        }finally{executor.shutdownNow();}
    }
}
