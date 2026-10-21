package dev.veltraph.client.failsafe.response;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds deterministic, configurable {@link FailsafeResponsePlan}s for each {@link FailsafeResponseType} (and
 * the focused {@link FailsafeRoutine} dev tests). Pure — no Minecraft, no randomness — so every sequence is
 * unit-testable.
 *
 * <p><b>Clean-room.</b> The original per-trigger response script bodies are not recovered; these plans are
 * local, configurable routines assembled from the recovered primitives (queued/eased look + key/movement
 * synthesis), flavoured per trigger. They are never source-exact action sequences, and never network.
 */
public final class FailsafeResponsePlanner {

    private FailsafeResponsePlanner() {
    }

    /** The per-trigger plan, honouring the profile + per-action toggles in {@code s}. */
    public static FailsafeResponsePlan plan(FailsafeResponseType type, FailsafeResponseSettings s) {
        if (type == null || s == null) {
            return new FailsafeResponsePlan("none", List.of());
        }
        List<FailsafeResponseAction> a = new ArrayList<>();
        // 1) Always look around (the core "react like a human noticing something" behaviour).
        a.addAll(lookSweep(s));

        // 2) A per-trigger movement flavour (only when MOVE is allowed + profile is not MINIMAL).
        if (s.move() && s.profile() != FailsafeResponseProfile.MINIMAL) {
            int mt = Math.max(1, s.moveTicks());
            switch (type) {
                case TELEPORTED_INTO_BLOCK, BARRIER -> {
                    a.add(FailsafeResponseAction.move(false, true, false, false, mt));   // back off
                    a.add(FailsafeResponseAction.move(false, false, false, true, mt));   // strafe around
                }
                case FLOOR_CHANGE, INVALID_MOVEMENT -> a.add(FailsafeResponseAction.move(false, true, false, false, mt));
                case BOSS_TELEPORTED, REPEATED_SUSPICIOUS -> a.add(FailsafeResponseAction.move(true, false, false, false, mt));
                default -> a.add(FailsafeResponseAction.move(false, false, true, false, mt)); // small left shuffle
            }
        }

        // 3) Jump / sneak (LOCAL_SAFE + FULL), then clicks (FULL only).
        if (s.jump() && s.profile() != FailsafeResponseProfile.MINIMAL) {
            a.add(FailsafeResponseAction.jump(3));
        }
        if (s.profile() == FailsafeResponseProfile.FULL) {
            if (s.sneak()) {
                a.add(FailsafeResponseAction.sneak(3));
            }
            if (s.attack()) {
                a.add(FailsafeResponseAction.attackClick(2));
            }
            if (s.use()) {
                a.add(FailsafeResponseAction.useClick(2));
            }
        }
        // A short settle at the end.
        a.add(FailsafeResponseAction.wait(2));
        return new FailsafeResponsePlan(type.name(), a);
    }

    /** A focused dev routine that isolates one slice of the harness. */
    public static FailsafeResponsePlan routine(FailsafeRoutine routine, FailsafeResponseSettings s) {
        if (routine == null || s == null) {
            return new FailsafeResponsePlan("none", List.of());
        }
        int mt = Math.max(1, s.moveTicks());
        List<FailsafeResponseAction> a = new ArrayList<>();
        switch (routine) {
            case LOOK_SWEEP -> a.addAll(lookSweep(s));
            case JUMP_SNEAK -> {
                a.add(FailsafeResponseAction.jump(3));
                a.add(FailsafeResponseAction.sneak(3));
            }
            case CLICKS -> {
                a.add(FailsafeResponseAction.attackClick(2));
                a.add(FailsafeResponseAction.useClick(2));
            }
            case MOVEMENT_SHUFFLE -> {
                a.add(FailsafeResponseAction.move(true, false, false, false, mt));
                a.add(FailsafeResponseAction.move(false, true, false, false, mt));
                a.add(FailsafeResponseAction.move(false, false, true, false, mt));
                a.add(FailsafeResponseAction.move(false, false, false, true, mt));
            }
            case FULL_PLAN -> {
                a.addAll(lookSweep(s));
                a.add(FailsafeResponseAction.move(true, false, false, false, mt));
                a.add(FailsafeResponseAction.jump(3));
                a.add(FailsafeResponseAction.sneak(3));
                a.add(FailsafeResponseAction.attackClick(2));
                a.add(FailsafeResponseAction.useClick(2));
            }
        }
        return new FailsafeResponsePlan("routine:" + routine, a);
    }

    /**
     * The shared alternating look sweep: {@code lookSweeps} LOOK actions, alternating right/left by the
     * configured range with a small downward-then-up pitch wobble, each over {@code lookTicks}.
     */
    private static List<FailsafeResponseAction> lookSweep(FailsafeResponseSettings s) {
        List<FailsafeResponseAction> out = new ArrayList<>();
        int sweeps = Math.max(1, s.lookSweeps());
        int ticks = Math.max(1, s.lookTicks());
        float range = (float) Math.max(1.0, s.lookRangeDeg());
        float prev = 0f;
        for (int i = 0; i < sweeps; i++) {
            // Alternate absolute side, expressed as a relative delta from the previous sweep's offset.
            float side = (i % 2 == 0) ? range : -range;
            float dYaw = side - prev;
            float dPitch = (i % 2 == 0) ? 8f : -8f; // gentle look-down / look-up wobble
            out.add(FailsafeResponseAction.look(dYaw, dPitch, ticks));
            prev = side;
        }
        // Return roughly to the original heading.
        if (prev != 0f) {
            out.add(FailsafeResponseAction.look(-prev, 0f, ticks));
        }
        return out;
    }
}
