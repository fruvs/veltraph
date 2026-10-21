package dev.veltraph.client.capture;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CaptureTargetProvenanceTest {
    private JsonObject context(long epoch) { var c = new JsonObject(); c.addProperty("worldEpoch", epoch); return c; }
    private JsonObject target(String kind) { var t = new JsonObject(); t.addProperty("kind", kind); t.addProperty("x", 12); t.addProperty("y", 65); t.addProperty("z", 34); return t; }
    private long action(CaptureTargetProvenance p, long time) { return p.begin("use_item_on_block", target("block"), context(1), time, true).get("actionSequence").getAsLong(); }
    private void assertUnknown(JsonObject e) { assertEquals("unknown", e.get("causalTargetBinding").getAsString()); assertFalse(e.has("boundTarget")); }
    @Test void exactActionBeforeMenuIsOnlyTemporalCandidateAndDefensiveCopiesPreserveIt() {
        var p = new CaptureTargetProvenance(); var t = target("block");
        var begin = p.begin("use_item_on_block", t, context(1), 1000, true); t.addProperty("x", 999);
        p.finish(begin.get("actionSequence").getAsLong(), true, 1050);
        var e = p.screenBoundary(true, context(1), 1200); assertUnknown(e);
        assertEquals("single_consumed_action_before_menu", e.get("temporalEvidence").getAsString());
        assertEquals(12, e.getAsJsonObject("candidateAction").getAsJsonObject("target").get("x").getAsInt());
        e.remove("candidateAction"); assertTrue(p.menuEvidence().has("candidateAction"));
    }
    @Test void crosshairAloneNoActionAndMenuNavigationNeverProvideTarget() {
        var p = new CaptureTargetProvenance(); var e = p.screenBoundary(true, context(1), 1000); assertUnknown(e); assertFalse(e.has("candidateAction"));
        long seq = action(p, 1100); p.finish(seq, true, 1150); assertTrue(p.screenBoundary(true, context(1), 1200).has("candidateAction"));
        e = p.screenBoundary(true, context(1), 1250); assertUnknown(e); assertFalse(e.has("candidateAction"));
    }
    @Test void multipleActionsIncludingUnrelatedActionInvalidateTarget() {
        var p = new CaptureTargetProvenance(); long seq = action(p, 1000); p.finish(seq, true, 1020);
        seq = p.begin("use_item", target("unknown"), context(1), 1100, true).get("actionSequence").getAsLong(); p.finish(seq, true, 1120);
        var e = p.screenBoundary(true, context(1), 1200); assertUnknown(e); assertFalse(e.has("candidateAction"));
        assertEquals("multiple_actions_before_menu", e.get("reason").getAsString());
    }
    @Test void contextChangesScreenCloseTimeoutAndClockRollbackInvalidateCandidate() {
        for (int kind = 0; kind < 5; kind++) {
            var p = new CaptureTargetProvenance(); long seq = action(p, 1000); p.finish(seq, true, 1050);
            if (kind == 0) p.contextChanged(); if (kind == 1) p.screenBoundary(false, context(1), 1100);
            var e = p.screenBoundary(true, context(kind == 2 ? 2 : 1), kind == 3 ? 4001 : kind == 4 ? 900 : 1200);
            assertUnknown(e); assertFalse(e.has("candidateAction"));
        }
    }
    @Test void failurePendingUnknownTargetAndActionWhileScreenOpenRemainUnknown() {
        for (int kind = 0; kind < 5; kind++) {
            var p = new CaptureTargetProvenance();
            var a = p.begin("use_item_on_block", target(kind == 3 ? "unknown" : "block"), context(1), 1000, kind != 4);
            if (kind != 0) p.finish(a.get("actionSequence").getAsLong(), kind == 1 ? Boolean.FALSE : kind == 2 ? null : Boolean.TRUE, 1050);
            var e = p.screenBoundary(true, context(1), 1200); assertUnknown(e); assertFalse(e.has("candidateAction"));
        }
    }
    @Test void containerInputRevokesExistingMenuEvidenceAndCannotBeUsedAsWorldTarget() {
        var p = new CaptureTargetProvenance(); long seq = action(p, 1000); p.finish(seq, true, 1050);
        assertTrue(p.screenBoundary(true, context(1), 1100).has("candidateAction"));
        p.menuInput(1200); assertUnknown(p.menuEvidence()); assertFalse(p.menuEvidence().has("candidateAction"));
        assertFalse(p.screenBoundary(true, context(1), 1300).has("candidateAction"));
    }
    @Test void expiredOldActionsDoNotAmbiguouslyAttachToLaterFocusedAction() {
        var p = new CaptureTargetProvenance(); long seq = action(p, 1000); p.finish(seq, true, 1050);
        seq = action(p, 5000); p.finish(seq, true, 5050);
        var e = p.screenBoundary(true, context(1), 5100); assertUnknown(e);
        assertEquals(seq, e.getAsJsonObject("candidateAction").get("actionSequence").getAsLong());
    }
}
