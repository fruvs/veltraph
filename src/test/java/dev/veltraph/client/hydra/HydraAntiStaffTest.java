package dev.veltraph.client.hydra;

import dev.veltraph.client.config.FailsafeConfig;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.failsafe.response.FailsafeActionSink;
import dev.veltraph.client.failsafe.response.FailsafeResponseController;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.hydra.mc.McHydraResponder;
import dev.veltraph.client.hydra.response.HydraOriginalPolicy;
import dev.veltraph.client.hydra.response.HydraResponsePolicy;
import dev.veltraph.client.hydra.response.HydraResponseState;
import dev.veltraph.client.hydra.response.ResponseTemplates;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link HydraAntiStaff} orchestrator's local-only reaction wiring (chat / stop-macro /
 * release-input / title / sound) without Minecraft — using a {@link MapConfigService} and recording
 * collaborators.
 */
class HydraAntiStaffTest {

    private static final class RecordingSink implements HydraAlertSink {
        int titles;
        int pings;

        @Override
        public void showTitle(String title, String subtitle) {
            titles++;
        }

        @Override
        public void playPing() {
            pings++;
        }
    }

    private HydraTick proximity(MapConfigService cfg, long now) {
        HydraSettings s = HydraSettings.fromConfig(cfg);
        return new HydraTick(s, Whitelist.parse(cfg.get(HydraConfig.LOCAL_WHITELIST)), now,
                List.of(new NearbyPlayer("StaffGuy", 5.0)), true, true, GameArea.GARDEN, 0.0, false);
    }

    private HydraTick noPlayers(MapConfigService cfg, long now) {
        HydraSettings s = HydraSettings.fromConfig(cfg);
        return new HydraTick(s, Whitelist.parse(cfg.get(HydraConfig.LOCAL_WHITELIST)), now,
                List.of(), true, true, GameArea.GARDEN, 0.0, false);
    }

    @Test
    void firesLocalChatTitleSoundAndAutoStopOnProximity() {
        MapConfigService cfg = new MapConfigService();
        List<String> chat = new ArrayList<>();
        int[] stops = {0};
        int[] releases = {0};
        RecordingSink sink = new RecordingSink();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, chat::add,
                () -> stops[0]++, () -> releases[0]++, sink);

        HydraResult r = feature.tick(proximity(cfg, 1000L));

        assertEquals(HydraState.ALERTED, r.state());
        assertEquals(1, chat.size());
        assertTrue(chat.get(0).startsWith(HydraAntiStaff.CHAT_PREFIX));
        assertTrue(chat.get(0).contains("StaffGuy"));
        assertEquals(1, sink.titles);
        assertEquals(1, sink.pings);
        assertEquals(1, stops[0]);
        assertEquals(1, releases[0]);
        assertEquals("StaffGuy", feature.nearestPlayerName());
        assertTrue(feature.enabled());
    }

    @Test
    void autoStopDisabledStillAlertsButDoesNotStop() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO, false);
        List<String> chat = new ArrayList<>();
        int[] stops = {0};
        HydraAntiStaff feature = new HydraAntiStaff(cfg, chat::add,
                () -> stops[0]++, () -> {}, HydraAlertSink.NONE);

        feature.tick(proximity(cfg, 1000L));

        assertEquals(1, chat.size()); // chat alert still printed
        assertEquals(0, stops[0]);    // but no macro stop
    }

    @Test
    void titleAndSoundFlagsAreRespected() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_ALERT_TITLE, false);
        cfg.set(HydraConfig.STAFF_ALERT_SOUND, false);
        RecordingSink sink = new RecordingSink();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, sink);

        feature.tick(proximity(cfg, 1000L));

        assertEquals(0, sink.titles);
        assertEquals(0, sink.pings);
    }

    @Test
    void setEnabledTogglesConfigAndStatusReflectsIt() {
        MapConfigService cfg = new MapConfigService();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);

        assertTrue(feature.enabled());
        feature.setEnabled(false);
        assertFalse(feature.enabled());
        assertFalse(cfg.get(HydraConfig.STAFF_ALERT_ENABLED));
        assertTrue(feature.status().contains("OFF"));
    }

    @Test
    void statusAndHudLabelSurfaceLastTriggerAndSeverity() {
        MapConfigService cfg = new MapConfigService();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        // A close player (5.0 <= 6.0 default dangerous band) → DANGEROUS proximity event.
        feature.tick(proximity(cfg, 1000L));

        String status = feature.status();
        assertTrue(status.contains("last=STAFF_NEARBY/DANGEROUS"), status);
        assertEquals("STAFF_NEARBY/DANGEROUS", feature.lastEventLabel());
    }

    /** Records the local, in-game actions the response layer would execute. */
    private static final class RecordingResponder implements HydraResponder {
        final List<String> chats = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<double[]> angles = new ArrayList<>();
        final List<dev.veltraph.client.hydra.response.HydraReaction> moves = new ArrayList<>();
        int ticks;
        int resets;

        @Override
        public void sendChat(String message) {
            chats.add(message);
        }

        @Override
        public void sendCommand(String command) {
            commands.add(command);
        }

        @Override
        public void fixAngle(double targetYaw, double targetPitch) {
            angles.add(new double[]{targetYaw, targetPitch});
        }

        @Override
        public void move(dev.veltraph.client.hydra.response.HydraReaction reaction,
                         List<dev.veltraph.client.hydra.response.HydraMovementStep> steps) {
            moves.add(reaction);
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        public void reset() {
            resets++;
        }

        boolean anyAction() {
            return !chats.isEmpty() || !commands.isEmpty() || !angles.isEmpty() || !moves.isEmpty();
        }
    }

    private static final class RecordingFailsafeSink implements FailsafeActionSink {
        float yaw;
        float pitch;
        int releases;

        @Override public float currentYaw() { return yaw; }
        @Override public float currentPitch() { return pitch; }
        @Override public void applyLook(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        @Override public void setMovement(boolean forward, boolean back, boolean left, boolean right,
                                          boolean jump, boolean sneak) { }
        @Override public void setAttack(boolean held) { }
        @Override public void setUse(boolean held) { }
        @Override public void sendCommand(String command) { }
        @Override public void releaseAll() { releases++; }
    }

    /**
     * A {@link HydraTick} carrying <b>every</b> trigger signal at once (close staff player, enclosed big
     * jump, lagback, floor change, barrier, forced-adventure gamemode, routing text, limbo area, out-of-band
     * pitch), with the given {@code scripting} flag. Used to prove the global macro gate makes Hydra fully
     * inert when {@code scripting=false} — and that the same tick reacts when {@code scripting=true}.
     */
    private HydraTick everyTrigger(MapConfigService cfg, long now, boolean scripting) {
        HydraSettings s = HydraSettings.fromConfig(cfg);
        return new HydraTick(s, Whitelist.parse(cfg.get(HydraConfig.LOCAL_WHITELIST)), now,
                List.of(new NearbyPlayer("StaffGuy", 2.0)), scripting, /*worldPresent*/ true,
                GameArea.LIMBO, /*move*/ 50.0, /*dimChanged*/ true, /*lagback*/ 10.0,
                /*enclosed*/ true, GameModeKind.ADVENTURE, /*routing*/ "evacuat", /*floorChanged*/ true,
                /*lookPitch*/ 80.0, /*lookYaw*/ 0.0, /*barrierDetected*/ true);
    }

    /** Config that would make Hydra maximally reactive (policy FULL, reactions on, reroute on). */
    private static MapConfigService reactiveConfig() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.FULL);
        cfg.set(HydraConfig.AUTO_REPLY_SECOND_CHANCE, 0.0);
        cfg.set(HydraConfig.MOVEMENT_REACTIONS_ENABLED, true);
        cfg.set(HydraConfig.AVOID_EVACUATION_ENABLED, true);
        cfg.set(HydraConfig.AFTER_DISCONNECT_ENABLED, true);
        cfg.set(HydraConfig.AFTER_DISCONNECT_COMMAND, "skyblock");
        return cfg;
    }

    @Test
    void hydraFullyInertWhenNoMacroRunning() {
        MapConfigService cfg = reactiveConfig();
        List<String> chat = new ArrayList<>();
        int[] stops = {0};
        int[] releases = {0};
        RecordingSink sink = new RecordingSink();
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, chat::add,
                () -> stops[0]++, () -> releases[0]++, sink, responder, () -> 0.0);

        // scripting=false: every trigger present, every reactive setting on → nothing happens.
        HydraResult r = feature.tick(everyTrigger(cfg, 1000L, /*scripting*/ false));

        assertEquals(HydraState.IDLE, r.state(), "no detection without a macro");
        assertTrue(r.event().isEmpty(), "no event fires without a macro");
        assertTrue(chat.isEmpty(), "no local chat alert");
        assertEquals(0, stops[0], "no macro stop");
        assertEquals(0, releases[0], "no input release");
        assertEquals(0, sink.titles, "no title");
        assertEquals(0, sink.pings, "no sound");
        assertFalse(responder.anyAction(), "no chat/command/fix-angle/move sent");
        assertFalse(feature.macroActive());
        assertTrue(feature.status().contains("INACTIVE (no macro"));
    }

    @Test
    void sameTickReactsWhenMacroRunning() {
        // Contrast: the identical trigger-laden tick DOES fire once a macro is running — proving the gate
        // (not some other guard) is what suppresses it above.
        MapConfigService cfg = reactiveConfig();
        List<String> chat = new ArrayList<>();
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, chat::add, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        HydraResult r = feature.tick(everyTrigger(cfg, 1000L, /*scripting*/ true));

        assertTrue(r.event().isPresent(), "an event fires while a macro runs");
        assertFalse(chat.isEmpty(), "a local alert is printed while a macro runs");
        assertTrue(feature.macroActive());
        assertTrue(feature.status().contains("ACTIVE (macro running)"));
    }

    @Test
    void guiDisableWhileMacroActiveImmediatelyResetsInFlightResponse() {
        // Mirrors the Settings → Anti-Staff watcher toggle path: it calls HydraAntiStaff.setEnabled(false),
        // which must immediately tear down in-flight response work instead of relying on the adapter's
        // disabled early-return (which would skip cleanup while a macro runs).
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.REPLY);
        cfg.set(HydraConfig.AUTO_REPLY_EXTREME, true);           // force the 2nd/3rd messages
        cfg.set(HydraConfig.AUTO_REPLY_MIN_DELAY_MS, 10_000L);   // long delay → chain stays queued
        cfg.set(HydraConfig.AUTO_REPLY_MAX_DELAY_MS, 10_000L);
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        // Macro running: a proximity check starts a multi-message reply chain (later messages still queued).
        feature.tick(proximity(cfg, 1000L));
        assertTrue(feature.macroActive());
        assertTrue(feature.responseState() != HydraResponseState.INACTIVE
                && feature.responseState() != HydraResponseState.LISTENING,
                "an in-flight response chain is pending");
        assertEquals(0, responder.resets, "no reset yet");

        // GUI/config disable while the macro is still active.
        feature.setEnabled(false);

        assertEquals(HydraResponseState.INACTIVE, feature.responseState(), "response state reset to INACTIVE");
        assertEquals(1, responder.resets, "responder reset (stops smooth look + movement, releases input)");

        // Subsequent ticks while disabled (macro still running) must not spam further resets.
        feature.tick(proximity(cfg, 2000L));
        feature.tick(proximity(cfg, 3000L));
        assertEquals(1, responder.resets, "no repeated reset spam on later disabled ticks");
        assertEquals(HydraResponseState.INACTIVE, feature.responseState());
    }

    @Test
    void devTriggeredLookAdvancesWithNoMacroWhileDetectionStaysInert() {
        // Real McHydraResponder over fake rotation/input so we can observe the smooth ease advancing.
        MapConfigService cfg = reactiveConfig();
        FakeRotationService rot = new FakeRotationService(0f, 0f);
        RecordingInputService in = new RecordingInputService();
        McHydraResponder responder = new McHydraResponder(s -> true, c -> true, rot, in);
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        // Production tick with NO macro + every trigger present → fully inert (no detection-driven aim).
        HydraResult r = feature.tick(everyTrigger(cfg, 1000L, /*scripting*/ false));
        assertTrue(r.event().isEmpty(), "no detection without a macro");
        assertEquals(0, rot.aimCalls, "no fix-angle from detection without a macro");
        assertFalse(feature.hasActiveLocalReaction(), "nothing queued yet");

        // A dev button queues a fix-angle directly (out of band → real move).
        feature.runFixAngle(Double.NaN, 38.0);
        assertTrue(feature.hasActiveLocalReaction(), "dev-queued look is active");

        // The dev tick path advances it with no macro running; detection never runs here.
        for (int i = 0; i < 200 && feature.hasActiveLocalReaction(); i++) {
            feature.tickResponder();
        }
        assertTrue(rot.aimCalls > 1, "dev-triggered smooth look advanced over multiple ticks with no macro");
        assertEquals(38f, rot.pitch, 1.5f, "eased to the dev target");
        assertFalse(feature.hasActiveLocalReaction(), "advancement stops when the reaction completes");
    }

    @Test
    void disablingHydraStopsDevReactionAndReleasesInput() {
        MapConfigService cfg = reactiveConfig();
        FakeRotationService rot = new FakeRotationService(0f, 0f);
        RecordingInputService in = new RecordingInputService();
        McHydraResponder responder = new McHydraResponder(s -> true, c -> true, rot, in);
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        feature.runReaction(dev.veltraph.client.hydra.response.HydraReaction.BACKUP);
        feature.tickResponder(); // hold the first movement step
        assertTrue(in.back, "movement input held");
        assertTrue(feature.hasActiveLocalReaction());

        feature.setEnabled(false); // disabling Hydra must abort the dev reaction + release input
        assertFalse(feature.hasActiveLocalReaction(), "reaction aborted on disable");
        assertFalse(in.back, "held input released on disable");
        assertTrue(in.releaseAllCount >= 1);
    }

    @Test
    void macroStopResetsTransientResponseState() {
        // Begin a response while scripting (proximity + REPLY policy + out-of-band pitch → a queued chain),
        // then the macro stops: the gate must reset the responder (release input / stop look + movement).
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.REPLY_AND_FIX_ANGLE);
        cfg.set(HydraConfig.AUTO_REPLY_SECOND_CHANCE, 0.0);
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        feature.tick(proximity(cfg, 1000L)); // scripting=true → arms + reacts
        assertTrue(feature.macroActive());
        int resetsBefore = responder.resets;

        // Macro stops.
        HydraResult r = feature.tick(everyTrigger(cfg, 1100L, /*scripting*/ false));
        assertFalse(feature.macroActive());
        assertEquals(resetsBefore + 1, responder.resets, "responder reset exactly once on the stop edge");
        assertEquals(HydraResponseState.INACTIVE, feature.responseState(), "response state machine reset");
        assertTrue(r.event().isEmpty());

        // A further no-macro tick does not reset again (no repeated edge) and stays inert.
        feature.tick(everyTrigger(cfg, 1200L, /*scripting*/ false));
        assertEquals(resetsBefore + 1, responder.resets, "no repeated reset while still no-macro");
    }

    @Test
    void autoStopWaitsForEnabledFailsafeResponseToFinish() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_LOOK_SWEEPS, 1);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_LOOK_TICKS, 2);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_MOVE, false);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_JUMP, false);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_MAX_TICKS, 20);
        int[] stops = {0};
        int[] releases = {0};
        RecordingFailsafeSink fs = new RecordingFailsafeSink();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> stops[0]++, () -> releases[0]++,
                HydraAlertSink.NONE);
        feature.setFailsafeResponseController(new FailsafeResponseController(cfg, fs, () -> 1000L));

        feature.tick(proximity(cfg, 1000L));

        assertEquals(0, stops[0], "auto-stop is deferred while the failsafe routine is running");
        assertTrue(feature.hasActiveLocalReaction(), "failsafe response is in flight");

        int guard = 0;
        while (feature.hasActiveLocalReaction() && guard++ < 50) {
            feature.tick(noPlayers(cfg, 1001L + guard));
        }

        assertFalse(feature.hasActiveLocalReaction(), "failsafe response completed");
        assertEquals(1, stops[0], "macro stop fires after the response completes");
        assertEquals(1, releases[0], "input ownership released after the deferred stop");
        assertTrue(fs.releases >= 1, "failsafe sink released held local input");
    }

    @Test
    void defaultPolicyOffSendsNoServerChatOnProximity() {
        MapConfigService cfg = new MapConfigService();
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        feature.tick(proximity(cfg, 1000L)); // dangerous proximity, but policy defaults OFF

        assertTrue(responder.chats.isEmpty());
        assertTrue(responder.commands.isEmpty());
    }

    @Test
    void replyPolicySendsServerChatOnDangerousProximity() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.STAFF_RESPONSE_POLICY, HydraResponsePolicy.REPLY);
        cfg.set(HydraConfig.AUTO_REPLY_SECOND_CHANCE, 0.0); // keep the reply to a single message
        RecordingResponder responder = new RecordingResponder();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0.0);

        feature.tick(proximity(cfg, 1000L));

        assertEquals(1, responder.chats.size());
        assertEquals(ResponseTemplates.DEFAULT_FIRST.get(0), responder.chats.get(0));
        assertTrue(responder.commands.isEmpty());
        assertEquals(HydraResponsePolicy.REPLY, feature.responsePolicy());
        assertTrue(feature.responseStatus().contains("auto-reply=on"));
    }

    @Test
    void responseStatusShowsLookAndRealignmentMessageCounts() {
        MapConfigService cfg = new MapConfigService();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        // Blank by default → both shown as "off", and the full lists are never printed.
        assertTrue(feature.responseStatus().contains("realign-msgs=off"));
        assertTrue(feature.responseStatus().contains("look-msgs=off"));

        assertTrue(feature.responseStatus().contains("barrier-msgs=off"));
        assertTrue(feature.responseStatus().contains("floor-msgs=off"));

        cfg.set(HydraConfig.REALIGNMENT_MESSAGES, "R1|R2");
        cfg.set(HydraConfig.LOOK_MESSAGES, "up there");
        cfg.set(HydraConfig.BARRIER_MESSAGES, "blocked|nope");
        cfg.set(HydraConfig.FLOOR_CHANGE_MESSAGES, "floor");
        assertTrue(feature.responseStatus().contains("realign-msgs=2"));
        assertTrue(feature.responseStatus().contains("look-msgs=1"));
        assertTrue(feature.responseStatus().contains("barrier-msgs=2"));
        assertTrue(feature.responseStatus().contains("floor-msgs=1"));
        // counts only, never the list contents
        assertFalse(feature.responseStatus().contains("up there"));
        assertFalse(feature.responseStatus().contains("blocked"));
    }

    @Test
    void responseStatusShowsLastCheckRareClassification() {
        MapConfigService cfg = new MapConfigService();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        // no event yet
        assertTrue(feature.responseStatus().contains("last-check=none"));

        feature.tick(proximity(cfg, 1000L)); // fires a STAFF_NEARBY event (policy OFF — no reply sent)
        String status = feature.responseStatus();
        assertTrue(status.contains("last-check=STAFF_NEARBY"));
        assertTrue(status.contains("forceReactRare=true"));
        assertTrue(status.contains("thresholdRare=true"));
        // booleans only — no message contents leak into the classification.
        assertFalse(status.contains("LOL"));
    }

    @Test
    void setResponsePolicyUpdatesConfigAndStatus() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(HydraConfig.AVOID_EVACUATION_ENABLED, true); // default is now false (parity); opt in for this check
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        assertEquals(HydraResponsePolicy.OFF, feature.responsePolicy());
        feature.setResponsePolicy(HydraResponsePolicy.FULL);
        assertEquals(HydraResponsePolicy.FULL, cfg.get(HydraConfig.STAFF_RESPONSE_POLICY));
        assertTrue(feature.responseStatus().contains("policy=FULL"));
        assertTrue(feature.responseStatus().contains("avoid-evac=on"));
    }

    @Test
    void parityDefaultsMatchOriginalConfigEvidence() {
        MapConfigService cfg = new MapConfigService();
        // Safe top-level Veltraph defaults.
        assertEquals(HydraResponsePolicy.OFF, cfg.get(HydraConfig.STAFF_RESPONSE_POLICY));
        assertFalse(cfg.get(HydraConfig.HYDRA_PARITY_MODE));
        // Parity-effective defaults match the original config evidence.
        assertEquals(HydraOriginalPolicy.REACT, cfg.get(HydraConfig.HYDRA_ORIGINAL_POLICY));
        assertFalse(cfg.get(HydraConfig.HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS));
        assertFalse(cfg.get(HydraConfig.AVOID_EVACUATION_ENABLED));
    }

    @Test
    void triggerStatusListsTheDefensiveTriggers() {
        MapConfigService cfg = new MapConfigService();
        HydraAntiStaff feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {}, HydraAlertSink.NONE);
        String triggers = feature.triggerStatus();
        assertTrue(triggers.contains("lagback="), triggers);
        assertTrue(triggers.contains("teleport-into-block="), triggers);
        assertTrue(triggers.contains("forced-adventure="), triggers);
        assertTrue(triggers.contains("routing-text="), triggers);
    }

    // --- Parity-only check classification (debug/status output; booleans only, never message contents) ---

    @Test
    void floorChangeCheckClassificationShowsBothRarePredicatesAndParityGate() {
        HydraEvent floor = HydraEvent.floorChange(true);
        // Parity OFF → not response-eligible, reason notes it needs parity.
        String off = HydraAntiStaff.classifyCheck(floor, false);
        assertTrue(off.contains("FLOOR_CHANGE"), off);
        assertTrue(off.contains("forceReactRare=true"), off);
        assertTrue(off.contains("thresholdRare=true"), off);
        assertTrue(off.contains("response-eligible=false"), off);
        assertTrue(off.contains("parity-check(needs parity)"), off);
        // Parity ON → response-eligible via the parity-check.
        String on = HydraAntiStaff.classifyCheck(floor, true);
        assertTrue(on.contains("response-eligible=true"), on);
        assertTrue(on.contains("(parity-check)"), on);
        // Never leaks message-list contents.
        assertFalse(off.contains("|"), off);
    }

    @Test
    void teleportIntoBlockCheckClassificationIsThresholdRareOnlyAndParityGated() {
        HydraEvent confine = HydraEvent.teleportIntoBlock(12.0, true);
        String off = HydraAntiStaff.classifyCheck(confine, false);
        assertTrue(off.contains("TELEPORT_INTO_BLOCK"), off);
        assertTrue(off.contains("forceReactRare=false"), off); // confinement check does not force-react
        assertTrue(off.contains("thresholdRare=true"), off);
        assertTrue(off.contains("response-eligible=false"), off);
        assertTrue(off.contains("parity-check(needs parity)"), off);
        String on = HydraAntiStaff.classifyCheck(confine, true);
        assertTrue(on.contains("response-eligible=true"), on);
        assertTrue(on.contains("(parity-check)"), on);
    }

    @Test
    void dangerousSeverityAnomalyNeverBecomesRareOrResponseEligible() {
        // WORLD_ANOMALY is DANGEROUS severity but must NOT produce rare predicates or response eligibility.
        HydraEvent world = HydraEvent.worldAnomaly(true);
        String on = HydraAntiStaff.classifyCheck(world, true);
        assertTrue(on.contains("forceReactRare=false"), on);
        assertTrue(on.contains("thresholdRare=false"), on);
        assertTrue(on.contains("response-eligible=false"), on);
        assertTrue(on.contains("passive-alert"), on);
    }
    @Test
    void failsafePreemptsOtherReactionAndDoesNotResumeStaleMotion() {
        var cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED,true);
        cfg.set(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO,false);
        int[] advances={0}, resets={0};
        boolean[] active={true};
        HydraResponder responder = new HydraResponder() {
            public void sendChat(String text) { throw new AssertionError("unexpected chat"); }
            public void sendCommand(String text) { throw new AssertionError("unexpected command"); }
            public void fixAngle(double yaw,double pitch) { active[0]=true; }
            public void tick() { if(active[0]) advances[0]++; }
            public void reset() { resets[0]++;active[0]=false; }
            public boolean hasActiveLocalReaction() { return active[0]; }
        };
        var feature = new HydraAntiStaff(cfg,s->{},()->{},()->{},HydraAlertSink.NONE,responder,()->0);
        var fs = new RecordingFailsafeSink();
        feature.setFailsafeResponseController(new FailsafeResponseController(cfg,fs,()->1000L));
        feature.tick(proximity(cfg,1000));
        assertEquals(1,resets[0],"previous movement cancelled on failsafe takeover");
        assertEquals(0,advances[0],"two controllers must not rotate in one tick");
        for(int i=0;i<250;i++) feature.tick(noPlayers(cfg,1001+i));
        assertFalse(feature.hasActiveLocalReaction());
        assertEquals(0,advances[0],"cancelled reaction must not resume after failsafe");
        assertTrue(fs.releases>0);
    }

    @Test
    void disabledTickClearsActiveResponderEvenWhenHandlerWasAlreadyInactive() {
        var cfg = new MapConfigService();
        int[] resets = {0};
        boolean[] active = {false};
        HydraResponder responder = new HydraResponder() {
            public void sendChat(String text) { throw new AssertionError("unexpected chat"); }
            public void sendCommand(String text) { throw new AssertionError("unexpected command"); }
            public void fixAngle(double yaw, double pitch) { active[0] = true; }
            public void reset() { resets[0]++; active[0] = false; }
            public boolean hasActiveLocalReaction() { return active[0]; }
        };
        var feature = new HydraAntiStaff(cfg, s -> {}, () -> {}, () -> {},
                HydraAlertSink.NONE, responder, () -> 0);
        feature.tick(noPlayers(cfg, 1000));
        feature.resetResponses();
        feature.runFixAngle(10, 20);
        int before = resets[0];
        cfg.set(HydraConfig.STAFF_ALERT_ENABLED, false);
        feature.tick(noPlayers(cfg, 1001));
        assertFalse(feature.hasActiveLocalReaction());
        assertEquals(before + 1, resets[0]);
        feature.tick(noPlayers(cfg, 1002));
        assertEquals(before + 1, resets[0], "disabled cleanup must not repeat");
    }

}
