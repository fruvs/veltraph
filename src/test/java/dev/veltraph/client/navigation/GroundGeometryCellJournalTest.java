package dev.veltraph.client.navigation;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundGeometryCellJournalTest {
    private static final GroundGeometryCellJournal.Cell A = new GroundGeometryCellJournal.Cell(1, 70, 1);
    private static final GroundGeometryCellJournal.Cell B = new GroundGeometryCellJournal.Cell(2, 70, 1);
    private static GroundGeometryCellJournal.Observation observation(Object state, double maxY, float speed) {
        return new GroundGeometryCellJournal.Observation(state,
                List.of(new GroundGeometryCellJournal.Box(0, 0, 0, 1, maxY, 1)), .6F, speed);
    }
    @Test void laterRoleMutationOfEarlierCellIsCaughtByFinalNativePass() {
        var state = new Object(); var old = observation(state, 0, 1);
        var world = new java.util.HashMap<>(Map.of(A, old, B, old));
        var journal = new GroundGeometryCellJournal(2);
        assertTrue(journal.observe(A, world.get(A))); assertTrue(journal.observe(B, world.get(B)));
        world.put(A, observation(new Object(), 1, 1));
        assertFalse(journal.revalidate(world::get, () -> true));
    }
    @Test void sameBlockStateButChangedNativeShapeDoesNotPass() {
        var state = new Object(); var journal = new GroundGeometryCellJournal(2);
        assertTrue(journal.observe(A, observation(state, 0, 1)));
        assertFalse(journal.revalidate(cell -> observation(state, 1, 1), () -> true));
    }
    @Test void changedSpeedFactorCannotHideBehindSameStateAndShape() {
        var state = new Object(); var journal = new GroundGeometryCellJournal(2);
        assertTrue(journal.observe(A, observation(state, 1, 1)));
        assertFalse(journal.revalidate(cell -> observation(state, 1, .4F), () -> true));
    }
    @Test void coherentUnchangedObservationsPassWithoutRunningRoleCallback() {
        var state = new Object(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(2);
        assertTrue(journal.observe(A, old)); assertTrue(journal.observe(B, old));
        int[] nativeReads = {0};
        assertTrue(journal.revalidate(cell -> { nativeReads[0]++; return old; }, () -> true));
        assertEquals(2, nativeReads[0]);
    }
    @Test void observedMutationDuringFirstPassIsStickyRefusal() {
        var journal = new GroundGeometryCellJournal(2);
        assertTrue(journal.observe(A, observation(new Object(), 1, 1)));
        assertFalse(journal.observe(A, observation(new Object(), 1, 1)));
        assertFalse(journal.revalidate(cell -> observation(new Object(), 1, 1), () -> true));
    }
    @Test void changedSupportShapeCannotHideBehindSameCollisionShape() {
        var state = new Object(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(1); assertTrue(journal.observe(A, old));
        var changed = new GroundGeometryCellJournal.Observation(state, old.shape(), .6F, 1F,
                List.of(new GroundGeometryCellJournal.Box(0, 0, 0, 1, .5, 1)), true);
        assertFalse(journal.revalidate(cell -> changed, () -> true));
    }
    @Test void changedSupportFaceRefusesEvenWhenBothShapesMatch() {
        var state = new Object(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(1); assertTrue(journal.observe(A, old));
        var changed = new GroundGeometryCellJournal.Observation(state, old.shape(), .6F, 1F, old.shape(), false);
        assertFalse(journal.revalidate(cell -> changed, () -> true));
    }
    @Test void finiteBudgetRefusalCannotBecomeLateSuccess() {
        var state = new Object(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(2); assertTrue(journal.observe(A, old));
        boolean[] currentBudget = {true};
        assertFalse(journal.revalidate(cell -> { currentBudget[0] = false; return old; }, () -> currentBudget[0]));
    }
    @Test void cellLimitAndMissingStateRefuseExplicitly() {
        var state = new Object(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(1); assertTrue(journal.observe(A, old));
        assertFalse(journal.observe(B, old)); assertFalse(journal.revalidate(cell -> old, () -> true));
        assertFalse(new GroundGeometryCellJournal(1).revalidate(cell -> old, () -> true));
    }
    @Test void nativeStateIdentityDoesNotInvokeUserEquals() {
        class Trap { @Override public boolean equals(Object o) { throw new AssertionError("state equality callback"); } }
        var state = new Trap(); var old = observation(state, 1, 1);
        var journal = new GroundGeometryCellJournal(1); assertTrue(journal.observe(A, old));
        assertTrue(journal.revalidate(cell -> observation(state, 1, 1), () -> true));
    }
}
