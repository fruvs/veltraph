package dev.veltraph.client.navigation;

import java.util.List;
import java.util.Optional;

/** Exact native attribute/effect family, not a speed threshold or permission to move.
 * Replay evidence selects profiles; a new journey must still capture its live native values. */
public final class OriginalSortedGroundNativePhysicsProfile {
    public static final double CAPTURED_360 = (double).36F;
    public static final double CAPTURED_400 = (double).4F;
    public static final double CAPTURED_SPRINT_AMOUNT = (double).3F;
    public record Modifier(String id, double amount, int operation) {}
    public record State(double baseSpeed, double effectiveSpeed, float travelSpeed,
            List<Modifier> movementModifiers, double jumpStrength, double gravity,
            double frictionModifier, double airDragModifier, int jumpAmplifier,
            List<GroundCapturedEffects.Effect> effects, double movementEfficiency, float blockSpeedFactor) {
        public State {
            movementModifiers = movementModifiers == null ? null : List.copyOf(movementModifiers);
            effects = effects == null ? null : effects.stream()
                    .sorted(java.util.Comparator.comparing(GroundCapturedEffects.Effect::id)).toList();
        }
        public State(double baseSpeed, double effectiveSpeed, float travelSpeed,
                List<Modifier> movementModifiers, double jumpStrength, double gravity,
                double frictionModifier, double airDragModifier, int jumpAmplifier,
                List<GroundCapturedEffects.Effect> effects, double movementEfficiency) {
            this(baseSpeed, effectiveSpeed, travelSpeed, movementModifiers, jumpStrength, gravity,
                    frictionModifier, airDragModifier, jumpAmplifier, effects, movementEfficiency, 1F);
        }
        public State(double baseSpeed, double effectiveSpeed, float travelSpeed,
                List<Modifier> movementModifiers, double jumpStrength, double gravity,
                double frictionModifier, double airDragModifier, int jumpAmplifier,
                List<GroundCapturedEffects.Effect> effects) {
            this(baseSpeed, effectiveSpeed, travelSpeed, movementModifiers, jumpStrength, gravity,
                    frictionModifier, airDragModifier, jumpAmplifier, effects, 0D, 1F);
        }
        public State(double baseSpeed, double effectiveSpeed, float travelSpeed,
                List<Modifier> movementModifiers, double jumpStrength, double gravity,
                double frictionModifier, double airDragModifier, int jumpAmplifier) {
            this(baseSpeed, effectiveSpeed, travelSpeed, movementModifiers, jumpStrength, gravity,
                    frictionModifier, airDragModifier, jumpAmplifier, jumpAmplifier < 0 ? List.of()
                    : List.of(new GroundCapturedEffects.Effect("minecraft:jump_boost", jumpAmplifier, false, true, true)), 0D, 1F);
        }
    }
    private final State state;
    private OriginalSortedGroundNativePhysicsProfile(State state) { this.state = state; }
    private static boolean same(double a, double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }
    /** Only source-proved ordinary native traits and captured base speeds are supported.
     * No item name, GUI Speed percentage, or save file grants admission. */
    public static Optional<OriginalSortedGroundNativePhysicsProfile> classify(State s) {
        if (s == null || s.movementModifiers() == null || s.movementModifiers().size() > 1
                || !(same(s.baseSpeed(), .1D) || same(s.baseSpeed(), (double).1F)
                    || same(s.baseSpeed(), CAPTURED_360) || same(s.baseSpeed(), CAPTURED_400))
                || !(same(s.jumpStrength(), .42D) || same(s.jumpStrength(), (double).42F))
                || !same(s.gravity(), .08D) || !same(s.frictionModifier(), 1D)
                || !same(s.airDragModifier(), 1D) || !(s.jumpAmplifier() == -1 || s.jumpAmplifier() == 5)
                || !GroundCapturedEffects.supported(s.effects(), s.jumpAmplifier())
                || !Double.isFinite(s.movementEfficiency()) || s.movementEfficiency() < 0 || s.movementEfficiency() > 1
                || Float.floatToIntBits(s.blockSpeedFactor()) != Float.floatToIntBits(1F))
            return Optional.empty();
        double expected = s.baseSpeed();
        if (!s.movementModifiers().isEmpty()) {
            var m = s.movementModifiers().getFirst();
            if (!"minecraft:sprinting".equals(m.id()) || m.operation() != 2
                    || !(same(m.amount(), .3D) || same(m.amount(), CAPTURED_SPRINT_AMOUNT)))
                return Optional.empty();
            expected *= 1D + m.amount();
        }
        if (!same(s.effectiveSpeed(), expected) || Float.floatToIntBits(s.travelSpeed())
                != Float.floatToIntBits((float)expected)) return Optional.empty();
        return Optional.of(new OriginalSortedGroundNativePhysicsProfile(s));
    }
    public State state() { return state; }
    public boolean sprinting() { return !state.movementModifiers().isEmpty(); }
    /** Native float arithmetic: float strength * block jump factor + .1F * (amplifier + 1). */
    public float jumpImpulse(float blockJumpFactor) {
        if (Float.floatToIntBits(blockJumpFactor) != Float.floatToIntBits(1F)) return Float.NaN;
        return (float)state.jumpStrength() * blockJumpFactor
                + (state.jumpAmplifier() < 0 ? 0F : .1F * (state.jumpAmplifier() + 1F));
    }
    /** Actual getFrictionInfluencedSpeed branch, including native float rounding at .6F.
     * A supplied supported surface still needs fresh full-body geometry outside this model. */
    public float groundAcceleration(float blockFriction) {
        if (!Float.isFinite(blockFriction) || blockFriction <= 0 || blockFriction > .600001F)
            return Float.NaN;
        return (double)blockFriction > .6D
                ? state.travelSpeed() * (.21600002F / (blockFriction * blockFriction * blockFriction))
                : state.travelSpeed();
    }
    public boolean matches(State current) { return current != null && state.equals(current); }
}
