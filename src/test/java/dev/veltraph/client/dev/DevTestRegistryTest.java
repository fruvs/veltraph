package dev.veltraph.client.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry metadata, duplicate-id prevention, and Dev-Mode / risk gating. */
class DevTestRegistryTest {

    private static DevTestAction local(String id) {
        return DevTestAction.of(id, id, "", DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY,
                ctx -> DevTestResult.local("ran " + id));
    }

    private static DevTestAction serverBound(String id) {
        return DevTestAction.of(id, id, "", DevTestCategory.CHAT, DevTestRisk.SERVER_BOUND,
                ctx -> DevTestResult.executed("sent " + id));
    }

    @Test
    void registerRejectsDuplicateIds() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(local("a"));
        assertThrows(IllegalArgumentException.class, () -> r.register(local("a")));
    }

    @Test
    void lookupAndCategoryFilter() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(local("g1"));
        r.register(serverBound("c1"));
        assertEquals(2, r.size());
        assertNotNull(r.byId("g1"));
        assertEquals(1, r.byCategory(DevTestCategory.GENERAL).size());
        assertEquals(1, r.byCategory(DevTestCategory.CHAT).size());
    }

    @Test
    void blockedWhenDevModeOff() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(local("a"));
        DevTestResult res = r.run("a", new RecordingDevTestContext(), false, false);
        assertEquals(DevTestResult.Status.BLOCKED, res.status());
    }

    @Test
    void unknownActionIsUnavailable() {
        DevTestRegistry r = new DevTestRegistry();
        DevTestResult res = r.run("nope", new RecordingDevTestContext(), true, true);
        assertEquals(DevTestResult.Status.UNAVAILABLE, res.status());
    }

    @Test
    void localActionRunsWhenDevModeOn() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(local("a"));
        DevTestResult res = r.run("a", new RecordingDevTestContext(), true, false);
        assertEquals(DevTestResult.Status.LOCAL, res.status());
    }

    @Test
    void serverBoundBlockedUnlessExplicitlyAllowed() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(serverBound("c1"));
        // dev mode on but server-bound NOT allowed -> blocked
        assertEquals(DevTestResult.Status.BLOCKED,
                r.run("c1", new RecordingDevTestContext(), true, false).status());
        // dev mode on AND explicitly allowed -> executes
        assertEquals(DevTestResult.Status.EXECUTED,
                r.run("c1", new RecordingDevTestContext(), true, true).status());
    }

    @Test
    void contextPredicateCanBlock() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(new DevTestAction("p", "p", "", DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY,
                ctx -> false, ctx -> DevTestResult.local("x")));
        assertEquals(DevTestResult.Status.BLOCKED,
                r.run("p", new RecordingDevTestContext(), true, true).status());
    }

    @Test
    void throwingActionIsUnavailableNotFatal() {
        DevTestRegistry r = new DevTestRegistry();
        r.register(DevTestAction.of("boom", "boom", "", DevTestCategory.GENERAL, DevTestRisk.LOCAL_ONLY,
                ctx -> { throw new RuntimeException("kaboom"); }));
        assertEquals(DevTestResult.Status.UNAVAILABLE,
                r.run("boom", new RecordingDevTestContext(), true, true).status());
    }

    @Test
    void standardRegistryHasNoDuplicateIdsAndCoversEveryCategory() {
        DevTestRegistry r = DevTestActions.standard();
        assertTrue(r.size() >= 20, "expected the full standard action set, got " + r.size());
        for (DevTestCategory c : DevTestCategory.values()) {
            assertTrue(r.byCategory(c).size() > 0, "category has no actions: " + c);
        }
    }
}
