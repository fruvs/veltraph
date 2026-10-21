package dev.veltraph.client.hydra.response;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure builder + selector for the local {@link HydraResponseCandidate} pool — the C2-free rebuild of the
 * original response-object pool + the handler's kind-filtered selection and threshold/"extreme" logic
 * (original handler {@code IIlIlIllIlIlIlIlII...}: {@code n = sentCount [-1 if rare&coin]; a9 = n [+5 if
 * avoidEvac]; extreme = pool.size() > a9} and the fix-pitch filter
 * {@code candidate.getKind() == LOOK_UP/LOOK_DOWN && !isBlank(value)}).
 *
 * <p>The pool is built from local YAML data only. The kind taxonomy is applied from the recovered stage
 * structure: the {@code first-message} lines are the up-front {@code GENERIC} replies (stage 1), the
 * {@code occasional-second/third} lines are the subtler follow-ups ({@code SUBTLER_GENERIC}, stage 2/3),
 * and each first-list line additionally seeds {@code LOOK_UP}/{@code LOOK_DOWN} candidates so the fix-angle
 * path has direction-matching responses to select. {@code REALIGNMENT} candidates come from an optional
 * local config list ({@code HydraConfig.REALIGNMENT_MESSAGES}), since the original's dedicated REALIGNMENT/
 * BARRIER/FLOOR_CHANGE responses came from the C2-fed pool, which is excluded — see docs.
 */
public final class ResponseCandidates {

    /**
     * Stage-1 reply kinds (the "current/primary" response may be any of these). Includes the local
     * {@code BARRIER}/{@code FLOOR_CHANGE} stand-ins (blank/absent by default) so a configured one is
     * selectable as a primary and counted by {@link #replyPoolSize}. {@code LOOK_UP}/{@code LOOK_DOWN} are
     * deliberately excluded — they accompany the fix-angle, not the escalation count.
     */
    private static final java.util.Set<HydraResponseKind> REPLY_KINDS = java.util.EnumSet.of(
            HydraResponseKind.GENERIC, HydraResponseKind.SUBTLER_GENERIC, HydraResponseKind.REALIGNMENT,
            HydraResponseKind.BARRIER, HydraResponseKind.FLOOR_CHANGE);

    private ResponseCandidates() {
    }

    /** Build the candidate pool from the template lists (no REALIGNMENT candidates, first-list look seed). */
    public static List<HydraResponseCandidate> pool(ResponseTemplates t) {
        return pool(t, null);
    }

    /** Build the candidate pool with optional REALIGNMENT lines (first-list look seed). */
    public static List<HydraResponseCandidate> pool(ResponseTemplates t, String realignmentCsv) {
        return pool(t, realignmentCsv, null);
    }

    /**
     * Build the candidate pool. {@code second}/{@code third} → {@code SUBTLER_GENERIC} stage 2/3;
     * {@code first} → {@code GENERIC} stage 1; {@code realignment} (a {@code |}-joined local list, may be
     * null/blank) → {@code REALIGNMENT} stage-1 candidates.
     *
     * <p>Look candidates ({@code LOOK_UP}/{@code LOOK_DOWN}, paired with the fix-angle correction) come from
     * the explicit local {@code lookCsv} list when non-blank — the closer stand-in for the original's
     * dedicated direction-matching pool entries. When {@code lookCsv} is blank the pool falls back to seeding
     * a look pair from each first-reply line (so the fix-angle path still has a chat line to accompany the
     * angle). Either way the original's <i>text</i> for these kinds came from the C2-fed pool (excluded), so
     * the seed/override are purely local.
     */
    public static List<HydraResponseCandidate> pool(ResponseTemplates t, String realignmentCsv,
                                                    String lookCsv) {
        return pool(t, realignmentCsv, lookCsv, null, null);
    }

    /**
     * Build the candidate pool with all optional local kind lists. {@code barrierCsv}/{@code floorChangeCsv}
     * (each a {@code |}-joined local list, may be null/blank) seed stage-1 {@code BARRIER}/{@code FLOOR_CHANGE}
     * candidates — the local stand-ins for the original C2-fed (EXCLUDE_NETWORK) movement-response classes.
     * Blank lists add nothing, so the default pool is unchanged.
     */
    public static List<HydraResponseCandidate> pool(ResponseTemplates t, String realignmentCsv,
                                                    String lookCsv, String barrierCsv, String floorChangeCsv) {
        List<HydraResponseCandidate> out = new ArrayList<>();
        for (String v : t.first()) {
            out.add(HydraResponseCandidate.generic(v, 1));
        }
        if (lookCsv != null && !lookCsv.isBlank()) {
            for (String v : split(lookCsv)) {
                out.add(HydraResponseCandidate.look(true, v));
                out.add(HydraResponseCandidate.look(false, v));
            }
        } else {
            for (String v : t.first()) {
                out.add(HydraResponseCandidate.look(true, v));
                out.add(HydraResponseCandidate.look(false, v));
            }
        }
        for (String v : t.second()) {
            out.add(HydraResponseCandidate.subtler(v, 2));
        }
        for (String v : t.third()) {
            out.add(HydraResponseCandidate.subtler(v, 3));
        }
        if (realignmentCsv != null && !realignmentCsv.isBlank()) {
            for (String v : split(realignmentCsv)) {
                // Stage 1 so a REALIGNMENT response is eligible as the primary reply.
                out.add(new HydraResponseCandidate(HydraResponseKind.REALIGNMENT, v, 1));
            }
        }
        addKind(out, barrierCsv, HydraResponseKind.BARRIER);
        addKind(out, floorChangeCsv, HydraResponseKind.FLOOR_CHANGE);
        return out;
    }

    /** Seed stage-1 candidates of {@code kind} from a {@code |}-joined local list (no-op when null/blank). */
    private static void addKind(List<HydraResponseCandidate> out, String csv, HydraResponseKind kind) {
        if (csv != null && !csv.isBlank()) {
            for (String v : split(csv)) {
                out.add(new HydraResponseCandidate(kind, v, 1));
            }
        }
    }

    /** Split a {@code |}-joined list, trimming and dropping empties. */
    private static List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        for (String part : csv.split("\\|")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Count of reply candidates (the {@code GENERIC}/{@code SUBTLER_GENERIC}/{@code REALIGNMENT} non-blank
     * entries across all stages) — the local equivalent of the original's reply-pool {@code size()} used in
     * the escalation decision ({@code IllllII...java} L555 {@code pool.size() > threshold}). Excludes the
     * locally-seeded {@code LOOK_UP}/{@code LOOK_DOWN} candidates so the look seed never inflates the
     * escalation threshold.
     */
    public static int replyPoolSize(List<HydraResponseCandidate> pool) {
        int n = 0;
        if (pool != null) {
            for (HydraResponseCandidate c : pool) {
                if (c != null && REPLY_KINDS.contains(c.kind()) && !c.isBlankValue()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Reply candidates ({@code GENERIC}/{@code SUBTLER_GENERIC}/{@code REALIGNMENT}) at a stage, non-blank. */
    public static List<HydraResponseCandidate> replyStage(List<HydraResponseCandidate> pool, int stage) {
        List<HydraResponseCandidate> out = new ArrayList<>();
        if (pool != null) {
            for (HydraResponseCandidate c : pool) {
                if (c != null && c.stage() == stage && REPLY_KINDS.contains(c.kind()) && !c.isBlankValue()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** A reply candidate (any reply kind) at the given stage chosen by {@code roll}; null if none. */
    public static HydraResponseCandidate selectReply(List<HydraResponseCandidate> pool, int stage, double roll) {
        return pick(replyStage(pool, stage), roll);
    }

    /** Candidates of a given kind with a non-blank value (mirrors the original filter). */
    public static List<HydraResponseCandidate> byKind(List<HydraResponseCandidate> pool, HydraResponseKind kind) {
        List<HydraResponseCandidate> out = new ArrayList<>();
        if (pool != null) {
            for (HydraResponseCandidate c : pool) {
                if (c != null && c.kind() == kind && !c.isBlankValue()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** {@code GENERIC} candidates at the given chain stage with a non-blank value. */
    public static List<HydraResponseCandidate> genericStage(List<HydraResponseCandidate> pool, int stage) {
        List<HydraResponseCandidate> out = new ArrayList<>();
        if (pool != null) {
            for (HydraResponseCandidate c : pool) {
                if (c != null && c.kind() == HydraResponseKind.GENERIC && c.stage() == stage
                        && !c.isBlankValue()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** Pick from {@code list} using {@code roll} in {@code [0,1)}; null if empty. */
    public static HydraResponseCandidate pick(List<HydraResponseCandidate> list, double roll) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        double r = Double.isNaN(roll) || roll < 0 ? 0 : (roll >= 1 ? 0.9999999 : roll);
        int idx = (int) (r * list.size());
        return list.get(Math.min(idx, list.size() - 1));
    }

    /** A {@code GENERIC} candidate at the given stage chosen by {@code roll}; null if none. */
    public static HydraResponseCandidate selectGeneric(List<HydraResponseCandidate> pool, int stage, double roll) {
        return pick(genericStage(pool, stage), roll);
    }

    /**
     * A direction-matching look candidate ({@code LOOK_UP} for {@code lookUp}, else {@code LOOK_DOWN}) with
     * a non-blank value, chosen by {@code roll}; null if none. Mirrors the original fix-pitch candidate
     * filter (by kind + non-empty value).
     */
    public static HydraResponseCandidate selectForDirection(List<HydraResponseCandidate> pool, boolean lookUp,
                                                            double roll) {
        return pick(byKind(pool, lookUp ? HydraResponseKind.LOOK_UP : HydraResponseKind.LOOK_DOWN), roll);
    }

    /**
     * The original "is extreme / keep escalating" decision: {@code availableCount > sentCount + (avoidEvac
     * ? 5 : 0) - (rareCoin ? 1 : 0)}. {@code rareCoin} is the rare-check 50% decrement (the caller flips a
     * coin only for rare checks).
     */
    public static boolean computeExtreme(int availableCount, int sentCount, boolean avoidEvac, boolean rareCoin) {
        int threshold = sentCount + (avoidEvac ? 5 : 0) - (rareCoin ? 1 : 0);
        return availableCount > threshold;
    }

    /**
     * The original fix-pitch gate (handler L573-L580): take the fix-angle path only when fix-angle is
     * allowed, the pitch is readable and out of the {@code [min,max]} band, and the selected/current
     * response kind is not {@link HydraResponseKind#REALIGNMENT} (a realignment response already corrects
     * the aim, so a separate fix is skipped). The look direction is {@code LOOK_UP} for {@code pitch > 0},
     * {@code LOOK_DOWN} for {@code pitch < 0}.
     */
    public static boolean shouldFixAngle(HydraResponseKind primaryKind, double pitch, boolean fixAngleAllowed,
                                         double min, double max) {
        if (!fixAngleAllowed || Double.isNaN(pitch)) {
            return false;
        }
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (pitch >= lo && pitch <= hi) {
            return false; // in band
        }
        return primaryKind != HydraResponseKind.REALIGNMENT;
    }
}
