package dev.veltraph.client.hydra;

import dev.veltraph.client.game.GameArea;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the local anti-staff state machine + the teleport/world/limbo failsafes
 * ({@code HydraAntiStaffLocal.md} LOCAL_INCLUDE). No Minecraft, no clock — every input is passed in.
 */
class HydraDetectorTest {

    private static final long COOLDOWN = 5000L;

    private static HydraSettings settings(boolean autoStop) {
        // enabled, 20 blocks alert, 6 blocks dangerous, sound/title on, autoStop, 5s cooldown,
        // 8 block teleport, all new defensive triggers on (lagback >3.0).
        return new HydraSettings(true, 20.0, 6.0, true, true, autoStop, COOLDOWN, 8.0,
                /*detectLagback*/ true, /*lagbackDistanceBlocks*/ 3.0,
                /*detectTeleportIntoBlock*/ true, /*detectForcedAdventure*/ true,
                /*detectRoutingText*/ true, /*detectFloorChange*/ true,
                /*detectBarrier*/ true, /*barrierStationarySpeed*/ 0.15);
    }

    private static HydraTick proximityTick(long now, boolean autoStop, List<NearbyPlayer> players,
                                           Whitelist wl) {
        return new HydraTick(settings(autoStop), wl, now, players,
                /*scripting*/ true, /*worldPresent*/ true, GameArea.GARDEN,
                /*tickMove*/ 0.0, /*dimChanged*/ false);
    }

    @Test
    void nearbyNonWhitelistedWithinDistanceAlertsAndRequestsStop() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("StaffGuy", 10.0)), Whitelist.empty()));

        assertEquals(HydraState.ALERTED, r.state());
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.STAFF_NEARBY, r.event().get().type());
        assertEquals("StaffGuy", r.event().get().playerName());
        assertTrue(r.stopRequested());
        assertEquals("StaffGuy", r.nearestPlayerName());
    }

    @Test
    void whitelistedPlayerAtSameDistanceDoesNotAlert() {
        HydraDetector d = new HydraDetector();
        Whitelist wl = Whitelist.parse("Friend1, StaffGuy");
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("staffguy", 10.0)), wl)); // case-insensitive match

        assertEquals(HydraState.IDLE, r.state());
        assertTrue(r.event().isEmpty());
        assertNull(r.nearestPlayerName());
    }

    @Test
    void playerBeyondDistanceDoesNotAlert() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("FarAway", 25.0)), Whitelist.empty()));

        assertEquals(HydraState.IDLE, r.state());
        assertTrue(r.event().isEmpty());
    }

    @Test
    void alertReturnsToIdleOnlyAfterCooldownWithNoNearbyPlayer() {
        HydraDetector d = new HydraDetector();
        // t=0: alert
        d.tick(proximityTick(0L, true, List.of(new NearbyPlayer("StaffGuy", 5.0)), Whitelist.empty()));
        assertEquals(HydraState.ALERTED, d.state());

        // t=1000 (<cooldown): no nearby player → stays ALERTED
        HydraResult r1 = d.tick(proximityTick(1000L, true, List.of(), Whitelist.empty()));
        assertEquals(HydraState.ALERTED, r1.state());

        // t=4999 (still <cooldown since last-nearby at t=0): stays ALERTED
        HydraResult r2 = d.tick(proximityTick(4999L, true, List.of(), Whitelist.empty()));
        assertEquals(HydraState.ALERTED, r2.state());

        // t=5000 (== cooldown elapsed since last-nearby): returns to IDLE
        HydraResult r3 = d.tick(proximityTick(5000L, true, List.of(), Whitelist.empty()));
        assertEquals(HydraState.IDLE, r3.state());
    }

    @Test
    void repeatedAlertIsThrottledByCooldown() {
        HydraDetector d = new HydraDetector();
        List<NearbyPlayer> near = List.of(new NearbyPlayer("StaffGuy", 5.0));

        // First tick fires.
        assertTrue(d.tick(proximityTick(0L, true, near, Whitelist.empty())).event().isPresent());
        // Next tick within cooldown does NOT re-fire (but stays ALERTED).
        HydraResult r = d.tick(proximityTick(100L, true, near, Whitelist.empty()));
        assertEquals(HydraState.ALERTED, r.state());
        assertTrue(r.event().isEmpty());
        // After cooldown, with the player still nearby, it re-announces.
        assertTrue(d.tick(proximityTick(5000L, true, near, Whitelist.empty())).event().isPresent());
    }

    @Test
    void teleportJumpBeyondThresholdFiresAnomalyWithStop() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                /*scripting*/ true, /*worldPresent*/ true, GameArea.GARDEN,
                /*tickMove*/ 12.0, /*dimChanged*/ false);
        HydraResult r = d.tick(t);

        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.TELEPORT_ANOMALY, r.event().get().type());
        assertTrue(r.stopRequested());
    }

    @Test
    void normalSmallMovementDoesNotFireTeleport() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, /*tickMove*/ 0.4, false);
        HydraResult r = d.tick(t);
        assertTrue(r.event().isEmpty());
        assertEquals(HydraState.IDLE, r.state());
    }

    @Test
    void teleportDoesNotFireWhenNotScripting() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                /*scripting*/ false, true, GameArea.GARDEN, /*tickMove*/ 50.0, false);
        assertTrue(d.tick(t).event().isEmpty());
    }

    @Test
    void worldDimensionChangeWhileScriptingFiresWorldAnomaly() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, /*tickMove*/ 0.0, /*dimChanged*/ true);
        HydraResult r = d.tick(t);
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.WORLD_ANOMALY, r.event().get().type());
        assertTrue(r.stopRequested());
    }

    @Test
    void limboAreaWhileScriptingFiresRoutingEvent() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.LIMBO, 0.0, false);
        HydraResult r = d.tick(t);
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.LIMBO_ROUTING, r.event().get().type());
        assertTrue(r.stopRequested());
    }

    @Test
    void worldGoneWhileScriptingFiresRoutingEvent() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                /*scripting*/ true, /*worldPresent*/ false, GameArea.UNKNOWN, Double.NaN, false);
        HydraResult r = d.tick(t);
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.LIMBO_ROUTING, r.event().get().type());
    }

    @Test
    void autoStopOffFiresEventButDoesNotRequestStop() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(proximityTick(1000L, /*autoStop*/ false,
                List.of(new NearbyPlayer("StaffGuy", 5.0)), Whitelist.empty()));
        assertTrue(r.event().isPresent());
        assertFalse(r.event().get().stopRequested());
        assertFalse(r.stopRequested());
    }

    @Test
    void disabledWatcherNeverFires() {
        HydraDetector d = new HydraDetector();
        HydraSettings off = new HydraSettings(false, 20.0, 6.0, true, true, true, COOLDOWN, 8.0,
                true, 3.0, true, true, true, true, true, 0.15);
        HydraTick t = new HydraTick(off, Whitelist.empty(), 1000L,
                List.of(new NearbyPlayer("StaffGuy", 1.0)), true, true, GameArea.LIMBO, 50.0, true);
        HydraResult r = d.tick(t);
        assertEquals(HydraState.IDLE, r.state());
        assertTrue(r.event().isEmpty());
    }

    @Test
    void closestNonWhitelistedPlayerIsReportedWhenMultiplePresent() {
        HydraDetector d = new HydraDetector();
        Whitelist wl = Whitelist.parse("Buddy");
        HydraResult r = d.tick(proximityTick(1000L, true, List.of(
                new NearbyPlayer("Buddy", 2.0),    // whitelisted, ignored even though closest
                new NearbyPlayer("Far", 18.0),
                new NearbyPlayer("Near", 6.0)), wl));
        assertTrue(r.event().isPresent());
        assertEquals("Near", r.event().get().playerName());
    }

    // ====================================================================================
    // Batch 3 — new local defensive triggers (severity, lagback, teleport-into-block,
    // forced-adventure, routing-text) + their false-positive guards.
    // ====================================================================================

    /** Full-control tick builder for the new defensive-trigger inputs. */
    private static HydraTick tick(long now, boolean scripting, List<NearbyPlayer> players,
                                  double tickMove, double lagback, boolean enclosed,
                                  GameModeKind mode, String routingMatch) {
        return new HydraTick(settings(true), Whitelist.empty(), now, players,
                scripting, /*worldPresent*/ true, GameArea.GARDEN,
                tickMove, /*dimChanged*/ false, lagback, enclosed, mode, routingMatch);
    }

    // ---- severity bands ----

    @Test
    void proximityWithinDangerousBandIsDangerous() {
        HydraDetector d = new HydraDetector();
        // 6.0 is the dangerous boundary (<= 6.0 dangerous): a player at exactly 6.0 → DANGEROUS.
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("StaffGuy", 6.0)), Whitelist.empty()));
        assertTrue(r.event().isPresent());
        assertEquals(HydraSeverity.DANGEROUS, r.event().get().severity());
    }

    @Test
    void proximityJustOutsideDangerousBandIsPossible() {
        HydraDetector d = new HydraDetector();
        // 6.1 is just outside the dangerous band but still within the 20.0 alert distance → POSSIBLE.
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("StaffGuy", 6.1)), Whitelist.empty()));
        assertTrue(r.event().isPresent());
        assertEquals(HydraSeverity.POSSIBLE, r.event().get().severity());
    }

    @Test
    void proximityNearAlertBoundaryIsPossible() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(proximityTick(1000L, true,
                List.of(new NearbyPlayer("StaffGuy", 19.9)), Whitelist.empty()));
        assertTrue(r.event().isPresent());
        assertEquals(HydraSeverity.POSSIBLE, r.event().get().severity());
    }

    // ---- invalid movement / lagback ----

    @Test
    void lagbackBeyondThresholdFiresWithStopWhileScripting() {
        HydraDetector d = new HydraDetector();
        // backward correction 4.0 > 3.0 threshold; small forward move; scripting.
        HydraResult r = d.tick(tick(1000L, true, List.of(), 0.5, 4.0, false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.INVALID_MOVEMENT, r.event().get().type());
        assertTrue(r.stopRequested());
    }

    @Test
    void lagbackBelowThresholdDoesNotFire() {
        HydraDetector d = new HydraDetector();
        // 2.5 < 3.0 → a small correction is not a lagback.
        HydraResult r = d.tick(tick(1000L, true, List.of(), 0.5, 2.5, false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isEmpty());
    }

    @Test
    void normalForwardWalkingDoesNotFireLagback() {
        HydraDetector d = new HydraDetector();
        // Normal forward walking: meaningful tick move but ZERO backward component.
        HydraResult r = d.tick(tick(1000L, true, List.of(), 4.3, 0.0, false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isEmpty());
    }

    @Test
    void lagbackDoesNotFireWhenNotScripting() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(tick(1000L, /*scripting*/ false, List.of(), 0.5, 10.0, false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isEmpty());
    }

    // ---- teleport into block ----

    @Test
    void teleportIntoBlockFiresOnlyWhenJumpAndEnclosed() {
        HydraDetector d = new HydraDetector();
        // jump 12.0 > 8.0 AND enclosed → teleport-into-block (DANGEROUS, stop).
        HydraResult r = d.tick(tick(1000L, true, List.of(), 12.0, Double.NaN, /*enclosed*/ true,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.TELEPORT_INTO_BLOCK, r.event().get().type());
        assertEquals(HydraSeverity.DANGEROUS, r.event().get().severity());
        assertTrue(r.stopRequested());
    }

    @Test
    void bigJumpIntoOpenAirIsTeleportAnomalyNotIntoBlock() {
        HydraDetector d = new HydraDetector();
        // jump 12.0 > 8.0 but NOT enclosed → the existing teleport anomaly, never into-block.
        HydraResult r = d.tick(tick(1000L, true, List.of(), 12.0, Double.NaN, /*enclosed*/ false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.TELEPORT_ANOMALY, r.event().get().type());
    }

    @Test
    void enclosedWithoutBigJumpDoesNotFireIntoBlock() {
        HydraDetector d = new HydraDetector();
        // small move, enclosed flag set (e.g. player simply standing in a tight spot) → nothing fires.
        HydraResult r = d.tick(tick(1000L, true, List.of(), 0.3, Double.NaN, /*enclosed*/ true,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isEmpty());
    }

    // ---- forced adventure ----

    @Test
    void forcedAdventureFiresOnSurvivalToAdventureWhileScripting() {
        HydraDetector d = new HydraDetector();
        // t0: established SURVIVAL while scripting (no event).
        assertTrue(d.tick(tick(0L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, null)).event().isEmpty());
        // t1 (after cooldown): forced to ADVENTURE → fires once, DANGEROUS, stop.
        HydraResult r = d.tick(tick(6000L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.ADVENTURE, null));
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.FORCED_ADVENTURE, r.event().get().type());
        assertEquals(HydraSeverity.DANGEROUS, r.event().get().severity());
        assertTrue(r.stopRequested());
    }

    @Test
    void forcedAdventureDoesNotFireIfAlreadyAdventureBeforeStart() {
        HydraDetector d = new HydraDetector();
        // First observed mode is already ADVENTURE (e.g. a build/adventure server) → no transition.
        assertTrue(d.tick(tick(0L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.ADVENTURE, null)).event().isEmpty());
        // Staying in ADVENTURE never fires.
        assertTrue(d.tick(tick(6000L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.ADVENTURE, null)).event().isEmpty());
    }

    @Test
    void forcedAdventureDoesNotFireWhenIdle() {
        HydraDetector d = new HydraDetector();
        d.tick(tick(0L, /*scripting*/ false, List.of(), 0.0, Double.NaN, false, GameModeKind.SURVIVAL, null));
        HydraResult r = d.tick(tick(6000L, /*scripting*/ false, List.of(), 0.0, Double.NaN, false,
                GameModeKind.ADVENTURE, null));
        assertTrue(r.event().isEmpty());
    }

    @Test
    void forcedAdventureClearsOnRestoreAndCanFireAgain() {
        HydraDetector d = new HydraDetector();
        d.tick(tick(0L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.SURVIVAL, null));
        // First forced edge fires.
        assertEquals(HydraEvent.Type.FORCED_ADVENTURE,
                d.tick(tick(6000L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.ADVENTURE, null))
                        .event().get().type());
        // Restored to SURVIVAL (clears the latch); no event for the restore itself.
        assertTrue(d.tick(tick(12000L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.SURVIVAL, null))
                .event().isEmpty());
        // Forced to ADVENTURE again → fires again.
        assertEquals(HydraEvent.Type.FORCED_ADVENTURE,
                d.tick(tick(18000L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.ADVENTURE, null))
                        .event().get().type());
    }

    @Test
    void unknownGameModeDoesNotFabricateTransition() {
        HydraDetector d = new HydraDetector();
        d.tick(tick(0L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.SURVIVAL, null));
        // An unreadable (UNKNOWN) tick must not be treated as a SURVIVAL→ADVENTURE edge.
        assertTrue(d.tick(tick(6000L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.UNKNOWN, null))
                .event().isEmpty());
        // And a real edge after the unknown blip still fires.
        assertEquals(HydraEvent.Type.FORCED_ADVENTURE,
                d.tick(tick(12000L, true, List.of(), 0.0, Double.NaN, false, GameModeKind.ADVENTURE, null))
                        .event().get().type());
    }

    // ---- routing / disconnect text ----

    @Test
    void routingTextFiresOnMatchingSystemLineWhileScripting() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(tick(1000L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, "sending you to limbo"));
        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.ROUTING_TEXT, r.event().get().type());
        assertEquals(HydraSeverity.DANGEROUS, r.event().get().severity());
        assertTrue(r.stopRequested());
        assertTrue(r.event().get().message().contains("sending you to limbo"));
    }

    @Test
    void routingTextDoesNotFireWhenNoMatch() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(tick(1000L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, null));
        assertTrue(r.event().isEmpty());
    }

    @Test
    void routingTextDoesNotFireWhenNotScripting() {
        HydraDetector d = new HydraDetector();
        HydraResult r = d.tick(tick(1000L, /*scripting*/ false, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, "you were kicked"));
        assertTrue(r.event().isEmpty());
    }

    // ---- floor/block change ----

    @Test
    void floorChangeFiresWhileScripting() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, 0.0, false,
                Double.NaN, false, GameModeKind.SURVIVAL, null, /*floorChanged*/ true);

        HydraResult r = d.tick(t);

        assertTrue(r.event().isPresent());
        assertEquals(HydraEvent.Type.FLOOR_CHANGE, r.event().get().type());
        assertEquals(HydraSeverity.DANGEROUS, r.event().get().severity());
        assertTrue(r.stopRequested());
    }

    @Test
    void floorChangeDoesNotFireWhenNotScripting() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                false, true, GameArea.GARDEN, 0.0, false,
                Double.NaN, false, GameModeKind.SURVIVAL, null, /*floorChanged*/ true);

        assertTrue(d.tick(t).event().isEmpty());
    }

    @Test
    void floorChangeConfigGateSuppressesIt() {
        HydraDetector d = new HydraDetector();
        HydraSettings noFloor = new HydraSettings(true, 20.0, 6.0, true, true, true, COOLDOWN, 8.0,
                true, 3.0, true, true, true, /*detectFloorChange*/ false,
                /*detectBarrier*/ true, /*barrierStationarySpeed*/ 0.15);
        HydraTick t = new HydraTick(noFloor, Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, 0.0, false,
                Double.NaN, false, GameModeKind.SURVIVAL, null, /*floorChanged*/ true);

        assertTrue(d.tick(t).event().isEmpty());
    }

    @Test
    void barrierDetectedFiresBarrierEvent() {
        HydraDetector d = new HydraDetector();
        // settings(...) has detectBarrier=true. The canonical 17-arg tick with barrierDetected=true.
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                /*scripting*/ true, /*worldPresent*/ true, GameArea.GARDEN, /*move*/ 0.0,
                /*dimChanged*/ false, /*lagback*/ Double.NaN, /*enclosed*/ false,
                GameModeKind.SURVIVAL, /*routing*/ null, /*floorChanged*/ false,
                /*lookPitch*/ Double.NaN, /*lookYaw*/ Double.NaN, /*barrierDetected*/ true);
        HydraEvent ev = d.tick(t).event().orElseThrow();
        assertEquals(HydraEvent.Type.BARRIER, ev.type());
    }

    @Test
    void barrierConfigGateSuppressesIt() {
        HydraDetector d = new HydraDetector();
        HydraSettings noBarrier = new HydraSettings(true, 20.0, 6.0, true, true, true, COOLDOWN, 8.0,
                true, 3.0, true, true, true, true, /*detectBarrier*/ false, 0.15);
        HydraTick t = new HydraTick(noBarrier, Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, 0.0, false, Double.NaN, false,
                GameModeKind.SURVIVAL, null, false, Double.NaN, Double.NaN, /*barrierDetected*/ true);
        assertTrue(d.tick(t).event().isEmpty());
    }

    @Test
    void barrierNotFiredWhenNotScripting() {
        HydraDetector d = new HydraDetector();
        HydraTick t = new HydraTick(settings(true), Whitelist.empty(), 1000L, List.of(),
                /*scripting*/ false, true, GameArea.GARDEN, 0.0, false, Double.NaN, false,
                GameModeKind.SURVIVAL, null, false, Double.NaN, Double.NaN, /*barrierDetected*/ true);
        assertTrue(d.tick(t).event().isEmpty(), "anomalies (incl. barrier) only fire while scripting");
    }

    // ---- per-trigger config gates ----

    @Test
    void disablingATriggerSuppressesIt() {
        HydraDetector d = new HydraDetector();
        // lagback disabled in settings → a backward correction does NOT fire.
        HydraSettings noLag = new HydraSettings(true, 20.0, 6.0, true, true, true, COOLDOWN, 8.0,
                /*detectLagback*/ false, 3.0, true, true, true, true,
                /*detectBarrier*/ true, /*barrierStationarySpeed*/ 0.15);
        HydraTick t = new HydraTick(noLag, Whitelist.empty(), 1000L, List.of(),
                true, true, GameArea.GARDEN, 0.5, false, 10.0, false, GameModeKind.SURVIVAL, null);
        assertTrue(d.tick(t).event().isEmpty());
    }

    // ---- cooldown spacing across the new triggers ----

    @Test
    void anomalyTriggersAreCooldownSpacedAndDoNotRepeatEveryTick() {
        HydraDetector d = new HydraDetector();
        // First routing match fires.
        assertTrue(d.tick(tick(0L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, "you were kicked")).event().isPresent());
        // A second routing match within the cooldown does NOT re-fire.
        assertTrue(d.tick(tick(100L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, "you were kicked")).event().isEmpty());
        // After the cooldown elapses, a fresh match fires again.
        assertTrue(d.tick(tick(5000L, true, List.of(), 0.0, Double.NaN, false,
                GameModeKind.SURVIVAL, "you were kicked")).event().isPresent());
    }
}
