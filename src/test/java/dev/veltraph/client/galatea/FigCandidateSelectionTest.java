package dev.veltraph.client.galatea;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FigCandidateSelectionTest {
    private final Comparator<Integer> distance = Comparator.naturalOrder();

    @Test void prefersSecondVisibleCandidateOverNearestOccludedCandidate() {
        assertEquals(2, FigCandidateSelection.select(List.of(3, 1, 2), p -> true,
                distance, p -> p == 2).orElseThrow());
    }

    @Test void doesNotExtendVisibilitySearchToSixthRankedCandidate() {
        List<Integer> rays = new ArrayList<>();
        assertEquals(1, FigCandidateSelection.select(List.of(6, 5, 4, 3, 2, 1), p -> true,
                distance, p -> { rays.add(p); return p == 6; }).orElseThrow());
        assertEquals(List.of(1, 2, 3, 4, 5), rays);
    }

    @Test void filteringPrecedesRankingAndFiveCandidateLimit() {
        List<Integer> rays = new ArrayList<>();
        assertEquals(6, FigCandidateSelection.select(List.of(1, 2, 3, 4, 5, 6), p -> p >= 5,
                distance, p -> { rays.add(p); return p == 6; }).orElseThrow());
        assertEquals(List.of(5, 6), rays);
    }

    @Test void tiesKeepOriginalModeListOrder() {
        assertEquals(3, FigCandidateSelection.select(List.of(3, 1, 2), p -> true,
                (a, b) -> 0, p -> false).orElseThrow());
    }

    @Test void firstAcceptedRayTerminatesFurtherRayEvaluation() {
        List<Integer> rays = new ArrayList<>();
        assertEquals(2, FigCandidateSelection.select(List.of(1, 2, 3), p -> true,
                distance, p -> { rays.add(p); return p >= 2; }).orElseThrow());
        assertEquals(List.of(1, 2), rays);
    }

    @Test void emptyEligibleListDoesNotRayOrInventCandidate() {
        assertTrue(FigCandidateSelection.select(List.of(1, 2), p -> false, distance,
                p -> { fail("No ray for an excluded candidate"); return true; }).isEmpty());
    }
}
