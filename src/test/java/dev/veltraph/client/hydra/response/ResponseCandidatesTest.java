package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseCandidatesTest {

    private static List<HydraResponseCandidate> pool() {
        return ResponseCandidates.pool(ResponseTemplates.build("hi", "yo", "bye", false, false));
    }

    @Test
    void poolHasGenericSubtlerStagesAndDirectionalCandidates() {
        List<HydraResponseCandidate> pool = pool();
        // stage 1 = GENERIC (first list); stages 2/3 = SUBTLER_GENERIC (occasional follow-ups).
        assertEquals(1, ResponseCandidates.genericStage(pool, 1).size());
        assertEquals(0, ResponseCandidates.genericStage(pool, 2).size()); // stage 2 is SUBTLER, not GENERIC
        assertEquals(HydraResponseKind.GENERIC, ResponseCandidates.replyStage(pool, 1).get(0).kind());
        assertEquals(HydraResponseKind.SUBTLER_GENERIC, ResponseCandidates.replyStage(pool, 2).get(0).kind());
        assertEquals(HydraResponseKind.SUBTLER_GENERIC, ResponseCandidates.replyStage(pool, 3).get(0).kind());
        // first-list line seeds both look directions.
        assertEquals(1, ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_UP).size());
        assertEquals(1, ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_DOWN).size());
    }

    @Test
    void realignmentCandidatesEnterStageOneReplyPool() {
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("a", "b", "c", false, false), "R1|R2");
        List<HydraResponseCandidate> stage1 = ResponseCandidates.replyStage(pool, 1);
        // GENERIC("a") + REALIGNMENT("R1","R2") = 3 reply candidates at stage 1.
        assertEquals(3, stage1.size());
        assertEquals(2, ResponseCandidates.byKind(pool, HydraResponseKind.REALIGNMENT).size());
    }

    @Test
    void byKindFiltersOutBlankValues() {
        List<HydraResponseCandidate> pool = List.of(
                HydraResponseCandidate.look(true, "look"),
                HydraResponseCandidate.look(true, "  "), // blank → excluded
                HydraResponseCandidate.generic("g", 1));
        List<HydraResponseCandidate> up = ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_UP);
        assertEquals(1, up.size());
        assertEquals("look", up.get(0).value());
    }

    @Test
    void selectForDirectionPicksMatchingKind() {
        List<HydraResponseCandidate> pool = pool();
        assertEquals(HydraResponseKind.LOOK_UP,
                ResponseCandidates.selectForDirection(pool, true, 0.0).kind());
        assertEquals(HydraResponseKind.LOOK_DOWN,
                ResponseCandidates.selectForDirection(pool, false, 0.0).kind());
        assertNull(ResponseCandidates.selectForDirection(List.of(), true, 0.0));
    }

    @Test
    void selectReplyRespectsStage() {
        List<HydraResponseCandidate> pool = pool();
        assertEquals("hi", ResponseCandidates.selectReply(pool, 1, 0.0).value());
        assertEquals("yo", ResponseCandidates.selectReply(pool, 2, 0.0).value());
        assertEquals("bye", ResponseCandidates.selectReply(pool, 3, 0.0).value());
        assertNull(ResponseCandidates.selectReply(pool, 9, 0.0));
    }

    @Test
    void computeExtremeAddsFiveForAvoidEvacuation() {
        // base: 5 available, 0 sent → extreme true.
        assertTrue(ResponseCandidates.computeExtreme(5, 0, false, false));
        // +5 avoid-evacuation raises the threshold → 5 > 5 is false.
        assertFalse(ResponseCandidates.computeExtreme(5, 0, true, false));
        // rare 50% decrement (-1) flips it back: 5 > (5-1)=4 → true.
        assertTrue(ResponseCandidates.computeExtreme(5, 0, true, true));
    }

    @Test
    void computeExtremeAccountsForSentCount() {
        assertTrue(ResponseCandidates.computeExtreme(3, 2, false, false));  // 3 > 2
        assertFalse(ResponseCandidates.computeExtreme(3, 3, false, false)); // 3 > 3 false
    }

    @Test
    void shouldFixAngleHonoursBandRealignmentAndFlags() {
        // in band → no fix
        assertFalse(ResponseCandidates.shouldFixAngle(HydraResponseKind.GENERIC, 10, true, -38, 38));
        // out of band, generic → fix
        assertTrue(ResponseCandidates.shouldFixAngle(HydraResponseKind.GENERIC, 80, true, -38, 38));
        assertTrue(ResponseCandidates.shouldFixAngle(HydraResponseKind.GENERIC, -80, true, -38, 38));
        // REALIGNMENT response is exempt even out of band
        assertFalse(ResponseCandidates.shouldFixAngle(HydraResponseKind.REALIGNMENT, 80, true, -38, 38));
        // fix-angle disabled → never
        assertFalse(ResponseCandidates.shouldFixAngle(HydraResponseKind.GENERIC, 80, false, -38, 38));
        // unreadable pitch → never
        assertFalse(ResponseCandidates.shouldFixAngle(HydraResponseKind.GENERIC, Double.NaN, true, -38, 38));
    }

    @Test
    void explicitLookListOverridesFirstLineSeed() {
        // With a dedicated LOOK list the look candidates come from it (not from the first-reply lines).
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("hi", "yo", "bye", false, false), /*realignment*/ null,
                /*lookCsv*/ "up there|over here");
        List<HydraResponseCandidate> up = ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_UP);
        List<HydraResponseCandidate> down = ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_DOWN);
        assertEquals(2, up.size());
        assertEquals(2, down.size());
        assertTrue(up.stream().anyMatch(c -> c.value().equals("up there")));
        // the generic first line "hi" is NOT reused as a look value when an explicit list is present.
        assertFalse(up.stream().anyMatch(c -> c.value().equals("hi")));
    }

    @Test
    void replyPoolSizeExcludesSeededLookDuplicates() {
        // 3 first + 2 second + 1 third lines → 3+2+1 = 6 reply candidates; the seeded LOOK_UP/LOOK_DOWN
        // duplicates (2 per first line = 6) must NOT inflate the escalation count.
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("a|b|c", "d|e", "f", false, false));
        assertEquals(6, ResponseCandidates.replyPoolSize(pool));
        // sanity: the full pool is larger (it carries the look seed).
        assertTrue(pool.size() > ResponseCandidates.replyPoolSize(pool));
    }

    @Test
    void replyPoolSizeCountsRealignmentButNotLook() {
        // explicit single-line lists so no built-in defaults fill the blanks.
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("a", "b", "c", false, false), "R1|R2", "lk");
        // GENERIC("a") + SUBTLER("b","c") + REALIGNMENT("R1","R2") = 5 reply candidates; look "lk" excluded.
        assertEquals(5, ResponseCandidates.replyPoolSize(pool));
        assertEquals(0, ResponseCandidates.byKind(pool, HydraResponseKind.LOOK_UP).stream()
                .filter(c -> c.value().equals("a")).count()); // first line not recycled as look value
    }

    @Test
    void barrierAndFloorChangeCandidatesEnterReplyPool() {
        // Explicit single-line lists so no defaults fill blanks: GENERIC("a") + SUBTLER("b","c")
        // + BARRIER("blocked") + FLOOR_CHANGE("floor") = 5 reply candidates; look pair from "lk" excluded.
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("a", "b", "c", false, false),
                /*realignment*/ null, /*lookCsv*/ "lk", /*barrier*/ "blocked", /*floorChange*/ "floor");
        assertEquals(1, ResponseCandidates.byKind(pool, HydraResponseKind.BARRIER).size());
        assertEquals(1, ResponseCandidates.byKind(pool, HydraResponseKind.FLOOR_CHANGE).size());
        // GENERIC(1) + SUBTLER(2) + BARRIER(1) + FLOOR_CHANGE(1) = 5, LOOK excluded.
        assertEquals(5, ResponseCandidates.replyPoolSize(pool));
        // a BARRIER/FLOOR_CHANGE candidate is selectable as a stage-1 primary reply.
        assertTrue(ResponseCandidates.replyStage(pool, 1).stream()
                .anyMatch(c -> c.kind() == HydraResponseKind.BARRIER));
        assertTrue(ResponseCandidates.replyStage(pool, 1).stream()
                .anyMatch(c -> c.kind() == HydraResponseKind.FLOOR_CHANGE));
    }

    @Test
    void blankBarrierAndFloorChangeAddNothing() {
        List<HydraResponseCandidate> withBlank = ResponseCandidates.pool(
                ResponseTemplates.build("a", "b", "c", false, false), null, null, "", "  ");
        List<HydraResponseCandidate> baseline = ResponseCandidates.pool(
                ResponseTemplates.build("a", "b", "c", false, false));
        assertEquals(baseline.size(), withBlank.size());
        assertEquals(0, ResponseCandidates.byKind(withBlank, HydraResponseKind.BARRIER).size());
        assertEquals(0, ResponseCandidates.byKind(withBlank, HydraResponseKind.FLOOR_CHANGE).size());
    }

    @Test
    void parityPoolIncludesCommandShapedFirstLines() {
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(
                ResponseTemplates.build("", "", "", false, true));
        assertNotNull(pool);
        assertTrue(ResponseCandidates.genericStage(pool, 1).stream()
                .anyMatch(c -> c.value().equals("/chat a")));
    }
}
