package dev.veltraph.client.failsafe;

import dev.veltraph.client.config.*;
import dev.veltraph.client.failsafe.response.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.hydra.*;
import dev.veltraph.client.hydra.mc.McHydraResponder;
import dev.veltraph.client.hydra.response.*;
import dev.veltraph.client.testutil.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailsafeProductionPolicyTest {
    private static final class Actions implements GeneralFailsafe.Actions {
        final List<String> calls = new ArrayList<>();
        final List<FailsafeResponseType> triggers = new ArrayList<>();
        final List<FailsafeResponseType> cancelled = new ArrayList<>();
        Runnable completion;
        boolean defer;
        public void alert(String reason) { calls.add("alert"); }
        public void stop() { calls.add("stop"); }
        public void returnToLocation() { calls.add("return"); }
        public void switchLobby() { calls.add("lobby"); }
        public boolean beginResponse(FailsafeResponseType type, Runnable done) {
            triggers.add(type);
            if (defer) completion = done;
            return defer;
        }
        public void cancelResponse(FailsafeResponseType type) { cancelled.add(type); }
    }
    private static final class Sink implements FailsafeActionSink {
        float yaw, pitch;
        int releases, heldActions, commands;
        public float currentYaw() { return yaw; }
        public float currentPitch() { return pitch; }
        public void applyLook(float y, float p) { yaw = y; pitch = p; }
        public void setMovement(boolean f, boolean b, boolean l, boolean r, boolean j, boolean s) {
            if (f || b || l || r || j || s) heldActions++;
        }
        public void setAttack(boolean held) { if (held) heldActions++; }
        public void setUse(boolean held) { if (held) heldActions++; }
        public void sendCommand(String command) { commands++; }
        public void releaseAll() { releases++; }
    }
    private static HydraTick tick(MapConfigService cfg, long now, boolean scripting, boolean nearby, double pitch) {
        return new HydraTick(HydraSettings.fromConfig(cfg), Whitelist.empty(), now,
                nearby ? List.of(new NearbyPlayer("visible-player", 4)) : List.of(), scripting, true,
                GameArea.GARDEN, 0, false, 0, false, GameModeKind.SURVIVAL, null, false, pitch, 0);
    }
    @Test void playerAndMentionPoliciesRemainIndependentAndRequireObservedIdentityPersistence() {
        var cfg = new MapConfigService(); var actions = new Actions(); long[] now = {1000};
        var general = new GeneralFailsafe(cfg, actions, () -> now[0], () -> 0);
        general.sourcePolicies(() -> FailsafeSourcePolicy.NOTIFY, () -> FailsafeSourcePolicy.SWITCH_LOBBY);
        var player = new GeneralFailsafe.Player(3, false, true, true, true, "same-uuid");
        general.tick(true, false, List.of(player));
        assertTrue(actions.calls.isEmpty(), "first observation is only a baseline");
        general.tick(true, false, List.of(player));
        assertEquals(List.of("alert"), actions.calls, "player NOTIFY does not inherit mention lobby policy");
        general.mentionObserved(true);
        now[0] = 3999; general.tick(true, false, List.of(player));
        assertEquals(1, actions.calls.size());
        now[0] = 4000; general.tick(true, false, List.of(player));
        assertEquals(List.of("alert", "alert", "stop", "lobby"), actions.calls);
    }
    @Test void invalidDistancesCannotFabricateNearbyPlayers() {
        var cfg = new MapConfigService();
        var detector = new HydraDetector();
        var result = detector.tick(new HydraTick(HydraSettings.fromConfig(cfg), Whitelist.empty(), 1000,
                List.of(new NearbyPlayer("nan", Double.NaN), new NearbyPlayer("negative", -1),
                        new NearbyPlayer("infinite", Double.POSITIVE_INFINITY)), true, true, GameArea.GARDEN, 0, false));
        assertTrue(result.event().isEmpty());
        var actions = new Actions(); var general = new GeneralFailsafe(cfg, actions);
        general.tick(true, false, List.of(new GeneralFailsafe.Player(-1, false, true, true, true)));
        assertTrue(actions.calls.isEmpty());
    }
    @Test void repeatedMentionDoesNotPostponeDeadlineAndDisableClearsPendingAction() {
        var cfg = new MapConfigService(); var actions = new Actions(); long[] now = {1000};
        var general = new GeneralFailsafe(cfg, actions, () -> now[0], () -> 1);
        general.sourcePolicies(() -> FailsafeSourcePolicy.NOTIFY, () -> FailsafeSourcePolicy.SWITCH_LOBBY);
        general.mentionObserved(true); now[0] = 2000; general.mentionObserved(true);
        now[0] = 10999; general.tick(true, false, List.of()); assertTrue(actions.calls.isEmpty());
        now[0] = 11000; general.tick(true, false, List.of()); assertTrue(actions.calls.contains("lobby"));
        actions.calls.clear(); general.mentionObserved(true);
        cfg.set(FailsafeConfig.FAILSAFE_MENTION_ENABLED, false);
        now[0] = 22000; general.tick(true, false, List.of()); assertTrue(actions.calls.isEmpty());
        cfg.set(FailsafeConfig.FAILSAFE_MENTION_ENABLED, true);
        general.tick(true, false, List.of()); assertTrue(actions.calls.isEmpty());
    }
    @Test void sourceNotifyIsAlertOnlyEvenWhenAutomatedMasterAndTriggerAreEnabled() {
        var cfg = new MapConfigService(); cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        var actions = new Actions(); actions.defer = true;
        var general = new GeneralFailsafe(cfg, actions);
        general.sourcePolicies(() -> FailsafeSourcePolicy.NOTIFY, () -> FailsafeSourcePolicy.NOTIFY);
        general.mentionObserved(true);
        var player = new GeneralFailsafe.Player(3, false, true, true, true, "same-uuid");
        general.tick(true, false, List.of(player)); general.tick(true, false, List.of(player));
        assertEquals(List.of("alert", "alert"), actions.calls);
        assertTrue(actions.triggers.isEmpty(), "NOTIFY cannot pause movement/camera through beginResponse");
        assertNull(actions.completion);
    }
    @Test void liveSourcePolicyChangeCancelsOwnedCameraAndCannotUpgradeOldEventToLobbyAction() {
        var cfg = new MapConfigService(); var actions = new Actions(); actions.defer = true;
        FailsafeSourcePolicy[] mention = {FailsafeSourcePolicy.LOCAL_POLICY};
        var general = new GeneralFailsafe(cfg, actions);
        general.sourcePolicies(() -> FailsafeSourcePolicy.LOCAL_POLICY, () -> mention[0]);
        general.mentionObserved(true); Runnable old = actions.completion;
        mention[0] = FailsafeSourcePolicy.NOTIFY; general.tick(true, false, List.of());
        assertEquals(List.of(FailsafeResponseType.MENTION), actions.cancelled);
        old.run(); assertEquals(List.of("alert"), actions.calls);
        mention[0] = FailsafeSourcePolicy.LOCAL_POLICY;
        general.mentionObserved(true); old = actions.completion;
        mention[0] = FailsafeSourcePolicy.SWITCH_LOBBY; old.run();
        assertFalse(actions.calls.contains("lobby"), "policy upgrade cannot borrow an old event without new deadline");
    }
    @Test void liveGeneralDetectionGateRevokesOwnedTriggerIncludingPostStopVoid() {
        for (var type : List.of(FailsafeResponseType.MENTION, FailsafeResponseType.VOID)) {
            var cfg = new MapConfigService(); var actions = new Actions(); actions.defer = true;
            var general = new GeneralFailsafe(cfg, actions);
            if (type == FailsafeResponseType.MENTION) general.mentionObserved(true);
            else general.tick(true, true, List.of());
            cfg.set(type == FailsafeResponseType.MENTION ? FailsafeConfig.FAILSAFE_MENTION_ENABLED : FailsafeConfig.FAILSAFE_CHECK_VOID, false);
            general.tick(type != FailsafeResponseType.VOID, false, List.of());
            assertEquals(List.of(type), actions.cancelled);
        }
    }
    @Test void externalHelperObservationRetainsVoidStopWithoutBorrowingRoutineAfterStopChangesOwner() {
        var cfg = new MapConfigService(); var actions = new Actions(); actions.defer = true;
        var general = new GeneralFailsafe(cfg, actions);
        general.tick(true, true, List.of(), true);
        assertEquals(List.of("alert", "stop"), actions.calls);
        assertTrue(actions.triggers.isEmpty(), "captured external veto survives the stop side effect");
        general.mentionObserved(true, true);
        assertEquals(List.of("alert", "stop", "alert", "stop"), actions.calls);
        assertTrue(actions.triggers.isEmpty());
    }
    @Test void deferredCompletionCannotCrossAWorldOrOwnerReset() {
        var cfg = new MapConfigService(); var actions = new Actions(); actions.defer = true;
        var general = new GeneralFailsafe(cfg, actions);
        general.mentionObserved(true); assertEquals(List.of("alert"), actions.calls);
        general.reset(); actions.completion.run(); assertEquals(List.of("alert"), actions.calls);
    }
    @Test void postStopVoidCompletionSurvivesOrdinaryInactiveTickButNotExplicitReset() {
        var cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_REACTION_POLICY, FailsafeReactionPolicy.STOP_AND_RETURN_TO_LOCATION);
        var actions = new Actions(); actions.defer = true;
        var general = new GeneralFailsafe(cfg, actions);
        general.tick(true, true, List.of()); general.tick(false, false, List.of());
        actions.completion.run();
        assertEquals(List.of("alert", "stop", "return"), actions.calls);
        actions.calls.clear(); general.tick(true, true, List.of()); general.reset(); actions.completion.run();
        assertEquals(List.of("alert", "stop"), actions.calls);
    }
    @Test void voidStopsBeforeResponseAndSuspiciousUsesExplicitAccumulatedThreshold() {
        var cfg = new MapConfigService(); var actions = new Actions(); actions.defer = true;
        var general = new GeneralFailsafe(cfg, actions);
        general.tick(true, true, List.of());
        assertEquals(List.of("alert", "stop"), actions.calls);
        assertEquals(List.of(FailsafeResponseType.VOID), actions.triggers);
        actions.completion.run(); assertEquals(List.of("alert", "stop"), actions.calls, "no duplicate stop");
        actions.defer = false; actions.calls.clear(); actions.triggers.clear();
        general.suspicious(true, 4); assertTrue(actions.calls.isEmpty());
        general.suspicious(true, 5); general.suspicious(true, 6);
        assertEquals(List.of(FailsafeResponseType.REPEATED_SUSPICIOUS), actions.triggers);
        general.suspicious(false, 6); general.suspicious(true, 5);
        assertEquals(2, actions.triggers.size());
    }
    @Test void lowerHydraQueuedRepliesAreRevokedWhenPolicyTurnsOff() {
        var cfg = new MapConfigService(); var sent = new ArrayList<String>();
        cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO, false);
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        cfg.set(HydraConfig.AUTO_REPLY_SECOND_CHANCE, 1.0);
        cfg.set(HydraConfig.AUTO_REPLY_THIRD_CHANCE, 0.0);
        cfg.set(HydraConfig.AUTO_REPLY_MIN_DELAY_MS, 1000L);
        cfg.set(HydraConfig.AUTO_REPLY_MAX_DELAY_MS, 1000L);
        var responder = new McHydraResponder(s -> { sent.add(s); return true; }, c -> true,
                new FakeRotationService(), new RecordingInputService());
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE, responder, () -> 0);
        hydra.tick(tick(cfg, 1000, true, true, 0)); assertEquals(1, sent.size());
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.OFF);
        hydra.tick(tick(cfg, 1500, true, false, 0)); hydra.tick(tick(cfg, 5000, true, false, 0));
        assertEquals(1, sent.size(), "queued second reply cannot survive revocation");
    }
    @Test void lowerProductionLookAndMovePermissionsRevokeActualServiceOwnership() {
        var cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO, false);
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        var rot = new FakeRotationService(0, 80); var input = new RecordingInputService();
        var responder = new McHydraResponder(s -> true, c -> true, rot, input);
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE, responder, () -> 0);
        hydra.tick(tick(cfg, 1000, true, true, 80)); assertTrue(responder.hasActiveLocalReaction());
        cfg.set(HydraConfig.FIX_ANGLE_ENABLED, false);
        int before = rot.aimCalls; hydra.tick(tick(cfg, 1001, true, false, 80));
        assertFalse(responder.hasActiveLocalReaction()); assertEquals(before, rot.aimCalls);
    }
    @Test void revokingAnyPlannedGameplayPermissionAbortsBeforeItCanFire() {
        for (var key : List.of(FailsafeConfig.FAILSAFE_RESPONSE_MOVE, FailsafeConfig.FAILSAFE_RESPONSE_JUMP,
                FailsafeConfig.FAILSAFE_RESPONSE_SNEAK, FailsafeConfig.FAILSAFE_RESPONSE_ATTACK, FailsafeConfig.FAILSAFE_RESPONSE_USE)) {
            var cfg = new MapConfigService(); var sink = new Sink();
            cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
            cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_PROFILE, FailsafeResponseProfile.FULL);
            cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_ATTACK, true); cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_USE, true);
            var controller = new FailsafeResponseController(cfg, sink, () -> 1000);
            assertTrue(controller.onTrigger(FailsafeResponseType.MENTION));
            cfg.set(key, false); controller.tick();
            assertFalse(controller.active(), key.name()); assertEquals(0, sink.heldActions);
        }
    }
    @Test void lowerMovementRevocationClearsHeldBackAndDoesNotReassert() {
        var cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO, false);
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        cfg.set(HydraConfig.MOVEMENT_REACTIONS_ENABLED, true);
        var input = new RecordingInputService();
        var responder = new McHydraResponder(s -> true, c -> true, new FakeRotationService(), input);
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE, responder, () -> 0);
        hydra.tick(new HydraTick(HydraSettings.fromConfig(cfg), Whitelist.empty(), 1000, List.of(), true,
                true, GameArea.GARDEN, 0, false, 0, false, GameModeKind.SURVIVAL, null, false, 0, 0, true));
        assertTrue(input.back);
        cfg.set(HydraConfig.MOVEMENT_REACTIONS_ENABLED, false);
        hydra.tick(tick(cfg, 1001, true, false, 0)); assertFalse(input.back);
        hydra.tick(tick(cfg, 1002, true, false, 0)); assertFalse(input.back);
    }
    @Test void generalResponseWorksWithWatcherOffAndCompletesPolicyOnceAfterRevocation() {
        var cfg = new MapConfigService(); cfg.set(HydraConfig.STAFF_ALERT_ENABLED, false);
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        var sink = new Sink(); int[] completions = {0};
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        hydra.setFailsafeResponseController(new FailsafeResponseController(cfg, sink, () -> 1000));
        assertTrue(hydra.beginGeneralResponse(true, FailsafeResponseType.MENTION, () -> completions[0]++));
        hydra.tick(tick(cfg, 1000, true, false, 0)); assertTrue(hydra.hasActiveGeneralResponse());
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, false);
        hydra.tick(tick(cfg, 1001, true, false, 0)); assertEquals(1, completions[0]);
        hydra.tick(tick(cfg, 1002, true, false, 0)); assertEquals(1, completions[0]);
    }
    @Test void generalCameraOwnershipCannotHideNewMovementAnomaly() {
        var cfg = new MapConfigService(); cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        var sink = new Sink(); int[] completions = {0}, stops = {0};
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> stops[0]++, () -> {}, HydraAlertSink.NONE);
        hydra.setFailsafeResponseController(new FailsafeResponseController(cfg, sink, () -> 1000));
        assertTrue(hydra.beginGeneralResponse(true, FailsafeResponseType.MENTION, () -> completions[0]++));
        var result = hydra.tick(new HydraTick(HydraSettings.fromConfig(cfg), Whitelist.empty(), 1001,
                List.of(), true, true, GameArea.GARDEN, 50, false));
        assertEquals(HydraEvent.Type.TELEPORT_ANOMALY, result.event().orElseThrow().type());
        assertFalse(hydra.hasActiveGeneralResponse()); assertEquals(1, stops[0]);
        assertEquals(0, completions[0], "aborted general ticket cannot apply stale policy");
    }
    @Test void emergencyVoidOnlyAdvancesViaExactOwnedPostStopPathAndNeverUsesGameplayActions() {
        var cfg = new MapConfigService(); cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_PROFILE, FailsafeResponseProfile.FULL);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_ATTACK, true); cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_USE, true);
        var sink = new Sink(); int[] completions = {0};
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        hydra.setFailsafeResponseController(new FailsafeResponseController(cfg, sink, () -> 1000));
        assertFalse(hydra.beginGeneralResponse(false, FailsafeResponseType.MENTION, () -> {}));
        assertTrue(hydra.beginGeneralResponse(false, FailsafeResponseType.VOID, () -> completions[0]++));
        assertTrue(hydra.runFailsafeRoutine(FailsafeRoutine.FULL_PLAN).contains("owns input"));
        assertTrue(hydra.runFailsafeResponse(FailsafeResponseType.MENTION).contains("owns input"));
        assertTrue(hydra.runReaction(HydraReaction.BACKUP).contains("owns input"));
        hydra.runFixAngle(180, 80);
        hydra.tickResponder();
        assertEquals(0, sink.yaw, "dev advancement cannot double-step the owned general response");
        hydra.tick(tick(cfg, 1000, false, false, 0)); assertEquals(0, sink.yaw);
        for (int i = 0; i < 100; i++) hydra.tickGeneralResponse();
        assertFalse(hydra.hasActiveGeneralResponse()); assertEquals(1, completions[0]);
        assertEquals(0, sink.heldActions); assertEquals(0, sink.commands);
    }
    @Test void externalOwnerPreservesObservationAndSuppressesAllSchedulingEvenAfterCriticalStopReleasesIt() {
        var cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        cfg.set(HydraConfig.MOVEMENT_REACTIONS_ENABLED, true);
        boolean[] external = {true}; int[] stops = {0}; var sent = new ArrayList<String>();
        var input = new RecordingInputService(); var rot = new FakeRotationService();
        var responder = new McHydraResponder(s -> { sent.add(s); return true; }, c -> { sent.add(c); return true; }, rot, input);
        var sink = new Sink();
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> { stops[0]++; external[0] = false; },
                () -> {}, HydraAlertSink.NONE, responder, () -> 0);
        hydra.externalControlOwned(() -> external[0]);
        hydra.setFailsafeResponseController(new FailsafeResponseController(cfg, sink, () -> 1000));
        assertFalse(hydra.beginGeneralResponse(false, FailsafeResponseType.VOID, () -> fail("must not schedule")));
        assertTrue(hydra.runFailsafeRoutine(FailsafeRoutine.FULL_PLAN).contains("owns input"));
        var result = hydra.tick(new HydraTick(HydraSettings.fromConfig(cfg), Whitelist.empty(), 1001,
                List.of(new NearbyPlayer("visible", 4)), false, true, GameArea.GARDEN, 50, false));
        assertEquals(HydraEvent.Type.TELEPORT_ANOMALY, result.event().orElseThrow().type());
        assertEquals(1, stops[0]); assertFalse(external[0]);
        assertFalse(hydra.hasActiveLocalReaction()); assertTrue(sent.isEmpty());
        assertEquals(0, sink.heldActions); assertEquals(0, rot.aimCalls);
    }
    @Test void externalTakeoverDropsQueuedAndHeldLowerReactionWithoutResumingItAfterRelease() {
        var cfg = new MapConfigService(); cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO, false);
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        cfg.set(HydraConfig.AUTO_REPLY_SECOND_CHANCE, 1.0);
        cfg.set(HydraConfig.AUTO_REPLY_MIN_DELAY_MS, 1000L); cfg.set(HydraConfig.AUTO_REPLY_MAX_DELAY_MS, 1000L);
        boolean[] external = {false}; var sent = new ArrayList<String>();
        var responder = new McHydraResponder(s -> { sent.add(s); return true; }, c -> true,
                new FakeRotationService(), new RecordingInputService());
        var hydra = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE, responder, () -> 0);
        hydra.externalControlOwned(() -> external[0]);
        hydra.tick(tick(cfg, 1000, true, true, 80));
        int delivered = sent.size(); assertTrue(responder.hasActiveLocalReaction());
        external[0] = true; hydra.tick(tick(cfg, 1001, false, false, 80));
        assertFalse(hydra.hasActiveLocalReaction());
        external[0] = false; hydra.tick(tick(cfg, 5000, false, false, 0));
        assertFalse(hydra.hasActiveLocalReaction()); assertEquals(delivered, sent.size());
    }
}
