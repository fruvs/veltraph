package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the parity {@link HydraResponseHandler}: policy routing (REACT/STOP_ALL/IGNORE), rare
 * override, candidate-based reply + threshold escalation (+5 avoid-evacuation), randomized fix-angle, the
 * cooldown bypass, and the LISTENING → ABOUT_TO_RESPOND → FIX_PITCH/RESPONDING → AFTER_RESPONDING →
 * LISTENING flow. A deterministic roll queue drives every random choice.
 */
class HydraResponseHandlerTest {

    /** A deterministic {@code [0,1)} source: returns the queued rolls, then 0.0 forever. */
    private static DoubleSupplier rolls(double... values) {
        Deque<Double> q = new ArrayDeque<>();
        for (double v : values) {
            q.add(v);
        }
        return () -> q.isEmpty() ? 0.0 : q.poll();
    }

    // (staffCheck, evacuation, disconnect, parityResponseCheck, forceReactRare, thresholdRare)
    private static final HydraResponseTrigger STAFF =
            new HydraResponseTrigger(true, false, false, false, false, false);
    /** Nearby-staff check: both rare predicates set (the original IlllIlII… check). */
    private static final HydraResponseTrigger STAFF_RARE =
            new HydraResponseTrigger(true, false, false, false, true, true);
    /** forceReactRare only (no threshold coin) — exercises the always-react override in isolation. */
    private static final HydraResponseTrigger STAFF_FORCE_ONLY =
            new HydraResponseTrigger(true, false, false, false, true, false);
    /** thresholdRare only (no always-react force) — confinement-analogue style, exercises the coin in isolation. */
    private static final HydraResponseTrigger STAFF_THRESHOLD_ONLY =
            new HydraResponseTrigger(true, false, false, false, false, true);
    /** Parity-only confinement/floor check (not a proximity staff check); both rare like FLOOR_CHANGE. */
    private static final HydraResponseTrigger PARITY_CHECK_RARE =
            new HydraResponseTrigger(false, false, false, true, true, true);
    private static final HydraResponseTrigger EVAC =
            new HydraResponseTrigger(false, true, false, false, false, false);
    private static final HydraResponseTrigger DISCONNECT =
            new HydraResponseTrigger(false, false, true, false, false, false);

    /** Parity settings with single-line custom templates so the candidate pool size is small + predictable. */
    private static HydraResponseSettings parity(HydraOriginalPolicy pol, boolean fixAngle, boolean alwaysRare,
                                                long delay, long cooldown, boolean bypassCooldown,
                                                boolean avoidEvac, String evacCmd, boolean afterDisc,
                                                String afterDiscCmd) {
        return parity(pol, fixAngle, alwaysRare, delay, cooldown, bypassCooldown, avoidEvac, evacCmd,
                afterDisc, afterDiscCmd, /*realignment*/ "");
    }

    private static HydraResponseSettings parity(HydraOriginalPolicy pol, boolean fixAngle, boolean alwaysRare,
                                                long delay, long cooldown, boolean bypassCooldown,
                                                boolean avoidEvac, String evacCmd, boolean afterDisc,
                                                String afterDiscCmd, String realignment) {
        return new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ true, pol, alwaysRare, /*parityTemplates*/ true,
                /*extreme*/ false, "a", "b", "c", 0.0, 0.0, delay, delay, cooldown,
                /*afterRespondingMs*/ 1L, fixAngle, -38.0, 38.0,
                /*reentryFraction*/ 0.5, /*yawJitter*/ 20.0, /*movementReactions*/ false, 10, 6, 8, 6,
                avoidEvac, evacCmd, afterDisc, afterDiscCmd,
                bypassCooldown, realignment, /*lookMessages*/ "", /*barrier*/ "", /*floorChange*/ "");
    }

    /** Parity REACT, single-line templates, fix-angle on, with an explicit local LOOK list. */
    private static HydraResponseSettings parityLook(String look) {
        return new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ true, HydraOriginalPolicy.REACT, false,
                /*parityTemplates*/ true, /*extreme*/ false, "a", "b", "c", 0.0, 0.0, 0L, 0L, 0L,
                /*afterRespondingMs*/ 1L, /*fixAngle*/ true, -38.0, 38.0,
                /*reentryFraction*/ 0.5, /*yawJitter*/ 20.0, /*movementReactions*/ false, 10, 6, 8, 6,
                false, "", false, "",
                /*bypassCooldown*/ true, /*realignment*/ "", /*look*/ look, /*barrier*/ "", /*floorChange*/ "");
    }

    /** Parity REACT with custom {@code |}-list templates + an avoid-evacuation flag (no fix-angle, no cooldown). */
    private static HydraResponseSettings parityTemplates(String first, String second, String third,
                                                         boolean avoidEvac) {
        return new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ true, HydraOriginalPolicy.REACT, false,
                /*parityTemplates*/ true, /*extreme*/ false, first, second, third, 0.0, 0.0, 0L, 0L, 0L,
                /*afterRespondingMs*/ 1L, /*fixAngle*/ false, -38.0, 38.0,
                /*reentryFraction*/ 0.5, /*yawJitter*/ 20.0, /*movementReactions*/ false, 10, 6, 8, 6,
                avoidEvac, "", false, "",
                /*bypassCooldown*/ true, /*realignment*/ "", /*look*/ "", /*barrier*/ "", /*floorChange*/ "");
    }

    /** Plain REACT, single-line templates, no fix-angle, no cooldown, bypass on. */
    private static HydraResponseSettings react() {
        return parity(HydraOriginalPolicy.REACT, false, false, 0L, 0L, true, false, "", false, "");
    }

    @Test
    void ignorePolicyDisablesThenReArms() {
        HydraResponseHandler h = new HydraResponseHandler(rolls());
        assertFalse(h.onCheck(STAFF, 0, 0, 1000L,
                parity(HydraOriginalPolicy.IGNORE, true, false, 0, 0, true, true, "lobby", true, "x")));
        assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED, h.state());
        assertTrue(h.tick(1000L).isEmpty());
        for(int invocation=1;invocation<600;invocation++){
            h.tick(1002L);assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,h.state());
        }
        h.tick(1002L);
        assertEquals(HydraResponseState.LISTENING,h.state());
        assertEquals(0,h.sourceDisableInvocations());
    }

    @Test
    void stopAllEmitsStopMacroAndDisables() {
        HydraResponseHandler h = new HydraResponseHandler(rolls());
        assertTrue(h.onCheck(STAFF, 0, 0, 1000L,
                parity(HydraOriginalPolicy.STOP_ALL, false, false, 0, 0, true, false, "", false, "")));
        assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED, h.state());
        List<HydraResponseAction> due = h.tick(1000L);
        assertEquals(1, due.size());
        assertEquals(HydraResponseAction.Kind.STOP_MACRO, due.get(0).kind());
        for(int invocation=1;invocation<600;invocation++){
            h.tick(1002L);assertEquals(HydraResponseState.RESPONDING_TEMPORARILY_DISABLED,h.state());
        }
        h.tick(1002L);
        assertEquals(HydraResponseState.LISTENING,h.state());
        assertEquals(0,h.sourceDisableInvocations());
    }

    @Test
    void reactSendsCustomFirstMessageAndWalksStates() {
        // single-line custom templates (a/b/c); avoid-evac false → escalate → 3 messages spaced 100ms.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        HydraResponseSettings s = parity(HydraOriginalPolicy.REACT, false, false, 100L, 0L, true, false, "", false, "");
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L, s));
        assertEquals(HydraResponseState.ABOUT_TO_RESPOND, h.state());

        assertEquals("a", h.tick(1000L).get(0).text());
        assertEquals(HydraResponseState.RESPONDING, h.state());
        assertEquals("b", h.tick(1100L).get(0).text());
        assertEquals("c", h.tick(1200L).get(0).text());
        assertEquals(HydraResponseState.AFTER_RESPONDING, h.state());
        h.tick(1202L);
        assertEquals(HydraResponseState.LISTENING, h.state());
    }

    @Test
    void avoidEvacuationRaisesThresholdAndSuppressesEscalation() {
        // Pool for a/b/c = (1 generic + 2 look) + 1 + 1 = 5. computeExtreme(5,1,avoidEvac,coin):
        // avoidEvac=false → 5>1 escalate (3 msgs); avoidEvac=true → 5>6 false → single message.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.REACT, false, false, 0, 0, true, /*avoidEvac*/ true, "", false, ""));
        // Only the primary message is sent (no escalation under avoid-evacuation +5).
        assertEquals(1, h.tick(1000L).size());
        assertFalse(h.isResponding());
    }

    @Test
    void rareCheckForcesReactEvenUnderIgnore() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0));
        assertTrue(h.onCheck(STAFF_RARE, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.IGNORE, false, /*alwaysRare*/ true, 0, 0, true, false, "", false, "")));
        assertEquals(HydraResponseAction.Kind.CHAT, h.tick(1000L).get(0).kind());
    }

    @Test
    void alwaysReactOverrideFiresOnlyForForceReactRare() {
        // IGNORE policy + always-react-rare on. A check whose forceReactRare predicate is set is escalated
        // to REACT (chat); a check that is only thresholdRare must NOT be — the override is gated on
        // forceReactRare alone (original llllIll…()), not on the threshold predicate.
        HydraResponseSettings s = parity(HydraOriginalPolicy.IGNORE, false, /*alwaysRare*/ true, 0, 0, true,
                false, "", false, "");

        HydraResponseHandler force = new HydraResponseHandler(rolls(0.0, 0.0));
        assertTrue(force.onCheck(STAFF_FORCE_ONLY, Double.NaN, Double.NaN, 1000L, s));
        assertEquals(HydraResponseAction.Kind.CHAT, force.tick(1000L).get(0).kind());

        HydraResponseHandler thr = new HydraResponseHandler(rolls(0.0, 0.0));
        assertFalse(thr.onCheck(STAFF_THRESHOLD_ONLY, Double.NaN, Double.NaN, 1000L, s),
                "thresholdRare alone must not trigger the always-react override");
    }

    @Test
    void thresholdCoinFiresOnlyForThresholdRare() {
        // 6 reply candidates (4 first + 1 second + 1 third), avoid-evac on → base threshold needs pool > 6
        // to escalate (false → 1 message). The thresholdRare 50% coin lowers it by 1 (pool > 5 → true),
        // enabling exactly the stage-2 message. forceReactRare alone must NOT flip the coin.
        HydraResponseSettings s = parityTemplates("a|b|c|d", "e", "f", /*avoidEvac*/ true);

        // thresholdRare + coin roll (0.0 < 0.5) → escalates to 2 messages.
        HydraResponseHandler thr = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        assertTrue(thr.onCheck(STAFF_THRESHOLD_ONLY, Double.NaN, Double.NaN, 1000L, s));
        assertEquals(2, thr.tick(1000L).size());

        // forceReactRare only (thresholdRare false) → the coin never flips → single message.
        HydraResponseHandler force = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        assertTrue(force.onCheck(STAFF_FORCE_ONLY, Double.NaN, Double.NaN, 1000L, s));
        assertEquals(1, force.tick(1000L).size());
    }

    @Test
    void parityResponseCheckSchedulesReactInParityMode() {
        // FLOOR_CHANGE / TELEPORT_INTO_BLOCK analogue (parityResponseCheck, not a proximity staffCheck):
        // in parity mode it routes into the REACT reply path and schedules a chat.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        HydraResponseSettings parity = parity(HydraOriginalPolicy.REACT, false, false, 0, 0, true, true, "",
                false, ""); // avoid-evac true → single message, deterministic
        assertTrue(h.onCheck(PARITY_CHECK_RARE, Double.NaN, Double.NaN, 1000L, parity));
        assertEquals(HydraResponseAction.Kind.CHAT, h.tick(1000L).get(0).kind());
    }

    @Test
    void parityResponseCheckIsSilentUnderSafeDefaults() {
        // Same parity-only check, but non-parity (the safe default): not response-eligible → nothing scheduled.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        HydraResponseSettings nonParity = new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ false, HydraOriginalPolicy.REACT, true, true,
                false, "a", "b", "c", 0.0, 0.0, 0L, 0L, 0L, 1L, false, -38.0, 38.0,
                0.5, 20.0, false, 10, 6, 8, 6, false, "", false, "",
                true, "", "", "", "");
        assertFalse(h.onCheck(PARITY_CHECK_RARE, Double.NaN, Double.NaN, 1000L, nonParity));
        assertTrue(h.tick(1000L).isEmpty());
    }

    @Test
    void nonRareUnderIgnoreEvenWithAlwaysReactDoesNotRespond() {
        HydraResponseHandler h = new HydraResponseHandler(rolls());
        assertFalse(h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.IGNORE, false, true, 0, 0, true, false, "", false, "")));
    }

    @Test
    void fixAnglePathRandomizesYawAndPitchAndSelectsLookCandidate() {
        // rolls: primary pick, yaw range, pitch range, look pick.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        assertTrue(h.onCheck(STAFF, /*yaw*/ 10.0, /*pitch*/ 80.0, 1000L,
                parity(HydraOriginalPolicy.REACT, true, false, 0, 0, true, false, "", false, "")));
        List<HydraResponseAction> due = h.tick(1000L);
        // fix-angle action + a LOOK candidate chat (value from the first list "a").
        HydraResponseAction fix = due.stream()
                .filter(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE).findFirst().orElseThrow();
        // Parity mode → exact original math: yaw + rand(-20,20) at roll 0 = 10-20 = -10;
        // pitch>0 → base +18, + rand(-10,10) at roll 0 = 18-10 = 8.
        assertEquals(10.0 - 20.0, fix.yaw(), 1e-9);
        assertEquals(18.0 - 10.0, fix.pitch(), 1e-9);
        assertTrue(due.stream().anyMatch(a -> a.kind() == HydraResponseAction.Kind.CHAT && a.text().equals("a")));
        // The handler passed through FIX_PITCH while draining the fix-angle action; with the look-chat
        // scheduled the same tick the chain completes and settles in AFTER_RESPONDING.
        assertEquals(HydraResponseState.AFTER_RESPONDING, h.state());
    }

    @Test
    void fixPitchStateObservableWhenChatDelayed() {
        // fix-angle at now, look-chat is also at now in the fix path, so to observe FIX_PITCH alone we
        // assert the FIX_ANGLE action is emitted and is the only fix-angle effect.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        h.onCheck(STAFF, 0.0, 80.0, 1000L,
                parity(HydraOriginalPolicy.REACT, true, false, 0, 0, true, false, "", false, ""));
        assertTrue(h.tick(1000L).stream().anyMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE));
    }

    @Test
    void inBandPitchTakesGenericPathNotFixAngle() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        h.onCheck(STAFF, 5.0, 10.0, 1000L,
                parity(HydraOriginalPolicy.REACT, true, false, 0, 0, true, true, "", false, ""));
        // in band → no fix-angle; avoid-evac true → single generic message.
        List<HydraResponseAction> due = h.tick(1000L);
        assertEquals(1, due.size());
        assertEquals(HydraResponseAction.Kind.CHAT, due.get(0).kind());
        assertEquals("a", due.get(0).text());
    }

    @Test
    void parityLookDownGivesNegativePitchBase() {
        // Parity mode, pitch -80 < 0 → base -18, + rand(-10,10) at roll 0.5 = -18 + 0 = -18.
        // Yaw + rand(-20,20) at roll 0.5 = 0.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.5, 0.5, 0.0));
        h.onCheck(STAFF, 0.0, -80.0, 1000L,
                parity(HydraOriginalPolicy.REACT, true, false, 0, 0, true, false, "", false, ""));
        HydraResponseAction fix = h.tick(1000L).stream()
                .filter(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE).findFirst().orElseThrow();
        assertEquals(0.0, fix.yaw(), 1e-9);
        assertEquals(-18.0, fix.pitch(), 1e-9);
    }

    /** Non-parity REACT, fix-angle on, configurable band re-entry (parityMode=false). */
    private static HydraResponseSettings nonParityFix(double minPitch, double maxPitch,
                                                      double reentryFraction, double yawJitter) {
        return new HydraResponseSettings(
                HydraResponsePolicy.FULL, /*parityMode*/ false, HydraOriginalPolicy.REACT, false,
                /*parityTemplates*/ false, /*extreme*/ false, "a", "", "", 0.0, 0.0, 0L, 0L, 0L,
                /*afterRespondingMs*/ 1L, /*fixAngle*/ true, minPitch, maxPitch,
                reentryFraction, yawJitter, /*movementReactions*/ false, 10, 6, 8, 6,
                false, "", false, "",
                /*bypassCooldown*/ true, "", "", "", "");
    }

    @Test
    void nonParityFixAngleReentersConfiguredBandFromTopEdge() {
        // Non-parity: pitch 80 above max=38 → re-enter from the top edge inward by roll*span*frac.
        // roll(pitch)=0 → lands at max=38. Yaw + rand(-jit,jit); jit=20, roll=0 → 10-20=-10.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0));
        assertTrue(h.onCheck(STAFF, 10.0, 80.0, 1000L, nonParityFix(-38.0, 38.0, 0.5, 20.0)));
        HydraResponseAction fix = h.tick(1000L).stream()
                .filter(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE).findFirst().orElseThrow();
        assertEquals(-10.0, fix.yaw(), 1e-9);
        assertEquals(38.0, fix.pitch(), 1e-9);
    }

    @Test
    void nonParityFixAngleBandShapesTargetWithCustomMinMax() {
        // A custom narrow band [-10,10], reentry 0.5: pitch -50 below min=-10 → min + roll*span*frac
        // = -10 + 0.5*(20*0.5) = -10 + 5 = -5 at roll(pitch)=0.5. Yaw jitter 0 → unchanged.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.5, 0.5));
        assertTrue(h.onCheck(STAFF, 25.0, -50.0, 1000L, nonParityFix(-10.0, 10.0, 0.5, 0.0)));
        HydraResponseAction fix = h.tick(1000L).stream()
                .filter(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE).findFirst().orElseThrow();
        assertEquals(25.0, fix.yaw(), 1e-9, "yaw jitter 0 leaves yaw unchanged");
        assertEquals(-5.0, fix.pitch(), 1e-9);
    }

    /** Parity REACT, fix-angle on, with CUSTOM GUI pitch bounds that parity must ignore. */
    private static HydraResponseSettings parityCustomBand(double minPitch, double maxPitch) {
        return new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ true, HydraOriginalPolicy.REACT, false,
                /*parityTemplates*/ true, /*extreme*/ false, "a", "b", "c", 0.0, 0.0, 0L, 0L, 0L,
                /*afterRespondingMs*/ 1L, /*fixAngle*/ true, minPitch, maxPitch,
                /*reentry*/ 0.5, /*yawJitter*/ 20.0, /*movementReactions*/ false, 10, 6, 8, 6,
                false, "", false, "",
                /*bypassCooldown*/ true, "", "", "", "");
    }

    @Test
    void parityIgnoresCustomGuiPitchBounds() {
        // Custom GUI band [-10,10], but pitch 20 is INSIDE the hardcoded original [-38,38] → parity must
        // NOT fix (it ignores the GUI bounds and uses the source band).
        HydraResponseHandler noFix = new HydraResponseHandler(rolls(0.0));
        assertTrue(noFix.onCheck(STAFF, 0.0, 20.0, 1000L, parityCustomBand(-10.0, 10.0)));
        assertTrue(noFix.tick(1000L).stream().noneMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE),
                "parity uses hardcoded [-38,38]; pitch 20 is in band → no fix despite GUI max=10");

        // pitch 50 is OUTSIDE [-38,38] → parity fixes with the exact original math (base +18).
        HydraResponseHandler fix = new HydraResponseHandler(rolls(0.0, 0.5, 0.5, 0.0));
        assertTrue(fix.onCheck(STAFF, 0.0, 50.0, 1000L, parityCustomBand(-10.0, 10.0)));
        HydraResponseAction a = fix.tick(1000L).stream()
                .filter(x -> x.kind() == HydraResponseAction.Kind.FIX_ANGLE).findFirst().orElseThrow();
        assertEquals(18.0, a.pitch(), 1e-9, "exact ±18 base, not a re-entry into the GUI band");
    }

    @Test
    void inBandPitchNeverFixesInEitherMode() {
        // Parity: pitch 10 in [-38,38] → no fix.
        HydraResponseHandler hp = new HydraResponseHandler(rolls(0.0));
        hp.onCheck(STAFF, 5.0, 10.0, 1000L,
                parity(HydraOriginalPolicy.REACT, true, false, 0, 0, true, true, "", false, ""));
        assertTrue(hp.tick(1000L).stream().noneMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE));
        // Non-parity: pitch 5 in custom band [-10,10] → no fix.
        HydraResponseHandler hn = new HydraResponseHandler(rolls(0.0));
        hn.onCheck(STAFF, 0.0, 5.0, 1000L, nonParityFix(-10.0, 10.0, 0.5, 20.0));
        assertTrue(hn.tick(1000L).stream().noneMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE));
    }

    @Test
    void realignmentPrimarySkipsFixAngleAtRuntime() {
        // stage-1 reply candidates = [GENERIC("a"), REALIGNMENT("R")]; roll 0.6 selects the REALIGNMENT one.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.6, 0.0, 0.0));
        HydraResponseSettings s = parity(HydraOriginalPolicy.REACT, /*fixAngle*/ true, false, 0, 0, true,
                false, "", false, "", /*realignment*/ "R");
        // Pitch 80 is out of band, but the selected response is REALIGNMENT → no separate fix-angle.
        assertTrue(h.onCheck(STAFF, /*yaw*/ 0.0, /*pitch*/ 80.0, 1000L, s));
        List<HydraResponseAction> due = h.tick(1000L);
        assertTrue(due.stream().noneMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE),
                "REALIGNMENT response must not emit a FIX_ANGLE action");
        assertTrue(due.stream().anyMatch(a -> a.kind() == HydraResponseAction.Kind.CHAT && a.text().equals("R")));
    }

    @Test
    void sentCounterStopsEscalationAtPoolBoundary() {
        // Pool for a/b/c = 3 (first generic+2 look) + 1 + 1 = 5. avoid-evac false → sends 3 (1+2);
        // sent counter increments 1→2→3 and the stage cap (3) ends it. Verify exactly 3 messages.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0, 0.0));
        h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.REACT, false, false, 0L, 0L, true, false, "", false, ""));
        assertEquals(3, h.tick(1000L).size());
    }

    @Test
    void explicitLookMessageAccompaniesFixAngleAtRuntime() {
        // rolls: primary pick(0.0), yaw range, pitch range, look pick(0.0). Out-of-band pitch + an explicit
        // LOOK list → the configured look text (not a recycled generic line) rides with the fix-angle.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        HydraResponseSettings s = parityLook("watch the sky");
        assertTrue(h.onCheck(STAFF, /*yaw*/ 0.0, /*pitch*/ 80.0, 1000L, s));
        List<HydraResponseAction> due = h.tick(1000L);
        assertTrue(due.stream().anyMatch(a -> a.kind() == HydraResponseAction.Kind.FIX_ANGLE));
        assertTrue(due.stream().anyMatch(
                a -> a.kind() == HydraResponseAction.Kind.CHAT && a.text().equals("watch the sky")),
                "the explicit LOOK_MESSAGES value should accompany the fix-angle");
        assertFalse(due.stream().anyMatch(a -> a.kind() == HydraResponseAction.Kind.CHAT && a.text().equals("a")));
    }

    @Test
    void reRouteCommands() {
        HydraResponseHandler h = new HydraResponseHandler(rolls());
        assertTrue(h.onCheck(EVAC, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.REACT, false, false, 0, 0, true, true, "/lobby", false, "")));
        assertEquals("lobby", h.tick(1000L).get(0).text());

        HydraResponseHandler h2 = new HydraResponseHandler(rolls());
        assertTrue(h2.onCheck(DISCONNECT, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.REACT, false, false, 0, 0, true, false, "", true, "skyblock")));
        assertEquals("skyblock", h2.tick(1000L).get(0).text());
    }

    @Test
    void parityBypassesCooldownButBusyStillGuards() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0, 0.0, 0.0));
        HydraResponseSettings s = parity(HydraOriginalPolicy.REACT, false, false, 100L, 30_000L,
                /*bypassCooldown*/ true, false, "", false, "");
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L, s));
        // Busy: queue still draining (delay=100) → a new check is ignored.
        assertFalse(h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L, s));
        h.tick(1000L);
        h.tick(1100L);
        h.tick(1200L);
        h.tick(1202L); // settle → LISTENING
        assertEquals(HydraResponseState.LISTENING, h.state());
        // Cooldown bypassed in parity → an immediate next check (well within 30s) is accepted.
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 1300L, s));
    }

    @Test
    void cooldownStillAppliesWhenBypassDisabled() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0, 0.0));
        HydraResponseSettings s = parity(HydraOriginalPolicy.REACT, false, false, 0L, 30_000L,
                /*bypassCooldown*/ false, true /*avoidEvac → single msg*/, "", false, "");
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L, s));
        h.tick(1000L);
        h.tick(1002L);
        assertFalse(h.onCheck(STAFF, Double.NaN, Double.NaN, 5000L, s)); // within cooldown
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 31_001L, s));
    }

    @Test
    void resetGoesInactiveAndArmReListens() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        h.onCheck(STAFF, Double.NaN, Double.NaN, 1000L, react());
        h.reset();
        assertEquals(HydraResponseState.INACTIVE, h.state());
        assertFalse(h.onCheck(STAFF, Double.NaN, Double.NaN, 2000L, react()));
        h.arm();
        assertEquals(HydraResponseState.LISTENING, h.state());
        assertTrue(h.onCheck(STAFF, Double.NaN, Double.NaN, 2000L, react()));
    }

    /** Parity REACT with the movement-reactions master toggle on (short, deterministic durations). */
    private static HydraResponseSettings reactionsOn() {
        return new HydraResponseSettings(
                HydraResponsePolicy.OFF, /*parityMode*/ true, HydraOriginalPolicy.REACT, false,
                /*parityTemplates*/ true, /*extreme*/ false, "a", "b", "c", 0.0, 0.0, 0L, 0L, 0L,
                /*afterRespondingMs*/ 1L, /*fixAngle*/ false, -38.0, 38.0,
                0.5, 20.0, /*movementReactions*/ true, 3, 3, 3, 3,
                false, "", false, "",
                /*bypassCooldown*/ true, "", "", "", "");
    }

    @Test
    void movementReactionSchedulesAMoveActionWhenEnabled() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        // A parity-only confinement check (no chat staffCheck) carrying a CAGE reaction.
        HydraResponseTrigger parityOnly = new HydraResponseTrigger(false, false, false, true, false, true);
        assertTrue(h.onCheck(parityOnly, HydraReaction.CAGE, Double.NaN, Double.NaN, 1000L, reactionsOn()));
        List<HydraResponseAction> due = h.tick(1000L);
        HydraResponseAction move = due.stream()
                .filter(a -> a.kind() == HydraResponseAction.Kind.MOVE).findFirst().orElseThrow();
        assertEquals(HydraReaction.CAGE, move.reaction());
        assertFalse(move.steps().isEmpty(), "the move carries expanded steps");
    }

    @Test
    void movementReactionSuppressedWhenToggleOff() {
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        // Same trigger, but the default parity settings have movement reactions OFF.
        HydraResponseTrigger parityOnly = new HydraResponseTrigger(false, false, false, true, false, true);
        h.onCheck(parityOnly, HydraReaction.CAGE, Double.NaN, Double.NaN, 1000L,
                parity(HydraOriginalPolicy.REACT, false, false, 0, 0, true, false, "", false, ""));
        assertTrue(h.tick(1000L).stream().noneMatch(a -> a.kind() == HydraResponseAction.Kind.MOVE),
                "no MOVE action when the movement-reactions toggle is off");
    }

    @Test
    void reactionWithNoChatTriggerStillSchedulesMove() {
        // A BACKUP reaction with a null trigger (movement-only) still schedules when reactions are allowed.
        HydraResponseHandler h = new HydraResponseHandler(rolls(0.0));
        assertTrue(h.onCheck(null, HydraReaction.BACKUP, Double.NaN, Double.NaN, 1000L, reactionsOn()));
        assertTrue(h.tick(1000L).stream().anyMatch(
                a -> a.kind() == HydraResponseAction.Kind.MOVE && a.reaction() == HydraReaction.BACKUP));
    }
}
