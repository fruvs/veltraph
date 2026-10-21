package dev.veltraph.client.hydra.response;

import dev.veltraph.client.hydra.HydraEvent;

/**
 * Classifies a detected {@link HydraEvent} into the kind(s) of local staff-response it warrants, so the
 * pure {@link HydraResponseHandler} never has to import the event taxonomy directly. Pure value type.
 *
 * <ul>
 *   <li>{@link #staffCheck()} — a player is checking you (proximity): drives the chat reply chain +
 *       fix-angle.</li>
 *   <li>{@link #evacuation()} — a routing/kick/evacuation line was received while still in-world: drives
 *       the avoid-evacuation re-route command (escape before staff acts).</li>
 *   <li>{@link #disconnect()} — routed to limbo / the world is gone: drives the afterdisconnect re-route
 *       command.</li>
 *   <li>{@link #forceReactRare()} / {@link #thresholdRare()} — the two recovered, independent "rare" signals
 *       (see below). They are derived per check-class, never from generic severity.</li>
 * </ul>
 *
 * <p><b>Two rare signals, check-class based (not severity based).</b> The original handler reads two
 * <i>separate</i> boolean predicates off the response/check object — they are not one broad "rare" flag:
 * <ul>
 *   <li><b>{@code forceReactRare}</b> — the original {@code llllIll…()} predicate. It is the second half of
 *       the always-react gate: the original forces {@code REACT} only when
 *       {@code HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS} <i>and</i> {@code check.llllIll…()} are both true
 *       (handler L468 {@code if (!ALWAYS_REACT || !check.llllIll…()) break}).</li>
 *   <li><b>{@code thresholdRare}</b> — the original {@code IIllll…()} predicate. It gates the 50 % rare coin
 *       that lowers the escalation threshold by 1: the original takes {@code n = sentCount() - 1} only when
 *       {@code check.IIllll…()} <i>and</i> {@code Random.nextBoolean()} are both true (handler L531
 *       {@code if (!check.IIllll…() || !nextBoolean()) skip}); otherwise {@code n = sentCount()}.</li>
 * </ul>
 * Each concrete check class overrides these predicates, so rarity is a property of the <i>check class</i>,
 * not of any severity band. The three recovered checks:
 * <ul>
 *   <li><b>nearby suspicious/player/staff check</b> (orig {@code IlllIlII…}, 20-block player scan): both
 *       predicates {@code true} (L189 {@code IIllll…()→true}, L194 {@code llllIll…()→true}).</li>
 *   <li><b>floor-change / block-change check</b> (orig {@code llIIIIIl…}, kind {@code FLOOR_CHANGE}): both
 *       predicates {@code true} (L181/L186 {@code →true}).</li>
 *   <li><b>block/environment confinement check</b> (orig {@code lllIllll…}, bedrock-encasement scan):
 *       {@code thresholdRare} {@code true} (L145 {@code IIllll…()→true}) but {@code forceReactRare}
 *       {@code false} (it does <i>not</i> override {@code llllIll…()}, so it inherits the base default
 *       {@code false}).</li>
 * </ul>
 * The original check-type → response-feed table lives behind the C2-driven detection (EXCLUDE_NETWORK); the
 * per-event-type mapping below reproduces the recovered predicate values for the locally-detected analogues.
 *
 * @param staffCheck          whether to run the reply chain / fix-angle in every enabled mode (proximity check)
 * @param evacuation          whether to run the avoid-evacuation re-route
 * @param disconnect          whether to run the afterdisconnect re-route
 * @param parityResponseCheck a non-proximity check that drives the REACT reply path <b>only in parity mode</b>
 *                            (the confinement / floor-change analogues; passive-alert only outside parity)
 * @param forceReactRare      check-class {@code llllIll…()} — half of the always-react override gate
 * @param thresholdRare       check-class {@code IIllll…()} — gates the 50 % threshold-lowering coin
 */
public record HydraResponseTrigger(boolean staffCheck, boolean evacuation, boolean disconnect,
                                   boolean parityResponseCheck, boolean forceReactRare, boolean thresholdRare) {

    /** True when this trigger asks for at least one response behaviour (incl. the parity-only checks). */
    public boolean any() {
        return staffCheck || evacuation || disconnect || parityResponseCheck;
    }

    /**
     * Map a fired event to a response intent + its check-class rare predicates. Response eligibility is
     * unchanged from the local design ({@code STAFF_NEARBY}→reply, {@code ROUTING_TEXT}→avoid-evacuation,
     * {@code LIMBO_ROUTING}→afterdisconnect; other anomalies are passive-alert only). The two rare predicates
     * are derived from {@link #forceReactRareType}/{@link #thresholdRareType} — never from severity:
     * <ul>
     *   <li>{@code STAFF_NEARBY} → staffCheck (response-eligible in every mode), forceReactRare=true,
     *       thresholdRare=true (nearby staff check).</li>
     *   <li>{@code FLOOR_CHANGE} → parityResponseCheck (REACT in parity mode), forceReactRare=true,
     *       thresholdRare=true (floor/block-change check analogue).</li>
     *   <li>{@code TELEPORT_INTO_BLOCK} → parityResponseCheck (REACT in parity mode), forceReactRare=false,
     *       thresholdRare=true (block/environment confinement analogue). Passive-alert outside parity.</li>
     *   <li>{@code ROUTING_TEXT}/{@code LIMBO_ROUTING} → reroute only; neither rare predicate.</li>
     *   <li>{@code FORCED_ADVENTURE}/{@code WORLD_ANOMALY} → not rare, not response-eligible (no
     *       severity-based rarity).</li>
     * </ul>
     */
    public static HydraResponseTrigger fromEvent(HydraEvent event) {
        if (event == null) {
            return new HydraResponseTrigger(false, false, false, false, false, false);
        }
        boolean forceReactRare = forceReactRareType(event.type());
        boolean thresholdRare = thresholdRareType(event.type());
        return switch (event.type()) {
            case STAFF_NEARBY ->
                    new HydraResponseTrigger(true, false, false, false, forceReactRare, thresholdRare);
            case ROUTING_TEXT ->
                    new HydraResponseTrigger(false, true, false, false, forceReactRare, thresholdRare);
            case LIMBO_ROUTING ->
                    new HydraResponseTrigger(false, false, true, false, forceReactRare, thresholdRare);
            // confinement + floor-change analogues: parity-only REACT checks (passive-alert outside parity).
            case TELEPORT_INTO_BLOCK, FLOOR_CHANGE ->
                    new HydraResponseTrigger(false, false, false, true, forceReactRare, thresholdRare);
            default -> new HydraResponseTrigger(false, false, false, false, forceReactRare, thresholdRare);
        };
    }

    /**
     * The original {@code llllIll…()} predicate, per check class: true for the nearby-staff check and the
     * floor/block-change check. {@code false} for the confinement check (it inherits the base default) and
     * for every routing/anomaly type — i.e. {@code DANGEROUS} severity never sets this.
     */
    private static boolean forceReactRareType(HydraEvent.Type type) {
        return switch (type) {
            case STAFF_NEARBY, FLOOR_CHANGE -> true;
            default -> false;
        };
    }

    /**
     * The original {@code IIllll…()} predicate, per check class: true for the nearby-staff check, the
     * floor/block-change check, and the block/environment confinement check ({@code TELEPORT_INTO_BLOCK}
     * analogue). {@code false} for routing/limbo and the generic anomalies — never set by severity.
     */
    private static boolean thresholdRareType(HydraEvent.Type type) {
        return switch (type) {
            case STAFF_NEARBY, TELEPORT_INTO_BLOCK, FLOOR_CHANGE -> true;
            default -> false;
        };
    }
}
