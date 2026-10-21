package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitorHistoryFreshPresentationTest {
    @Test void newActivationReadFailureCannotKeepPreviouslyNew(){var state=new VisitorHistoryPresentationState();state.update(new VisitorHistoryStore.Observation(VisitorHistoryStore.Newness.NEW,"old session roster"));state.clear();assertEquals(VisitorHistoryStore.Newness.UNKNOWN,state.current().newness());assertFalse(state.current().provenance().contains("old session"));}
    @Test void disableCannotKeepPreviouslySeen(){var state=new VisitorHistoryPresentationState();state.update(new VisitorHistoryStore.Observation(VisitorHistoryStore.Newness.SEEN,"old served NPC"));state.clear();assertEquals(VisitorHistoryStore.Newness.UNKNOWN,state.current().newness());}
    @Test void missingProducerDoesNotRestoreKnownState(){var state=new VisitorHistoryPresentationState();state.update(new VisitorHistoryStore.Observation(VisitorHistoryStore.Newness.NEW,"old"));assertEquals(VisitorHistoryStore.Newness.UNKNOWN,state.update(null).newness());}
    @Test void nestedNewerOpenPublicationWinsOverOlderOuterCallback(){assertTrue(HistoryObservationOrder.newer(-1,2));assertFalse(HistoryObservationOrder.newer(2,1));assertFalse(HistoryObservationOrder.newer(2,2));}
    @Test void nestedNewerClosePublicationCannotBeOverwrittenOrCountedTwice(){assertTrue(HistoryObservationOrder.newer(2,3));assertFalse(HistoryObservationOrder.newer(3,2));assertFalse(HistoryObservationOrder.newer(3,3));}
    @Test void exhaustedOrUnissuedSequenceCannotPublish(){assertFalse(HistoryObservationOrder.newer(-1,0));assertFalse(HistoryObservationOrder.newer(Long.MAX_VALUE,Long.MAX_VALUE));}
}
