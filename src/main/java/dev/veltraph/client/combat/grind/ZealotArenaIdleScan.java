package dev.veltraph.client.combat.grind;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Ordinary no-target camera scan from the readable Arena branch, not a staff response. */
public final class ZealotArenaIdleScan {
    private ZealotArenaIdleScan() {}
    public record Target(float yaw,float pitch) {}
    public static boolean sampledDefault(double draw) {
        if(!Double.isFinite(draw)||draw<0||draw>=1)throw new IllegalArgumentException("Random draw outside [0,1)");
        return draw>0.2;
    }
    public static Optional<Target> target(boolean enabled,boolean selectedEntity,boolean entityAction,
                                          boolean rotationPending,boolean safelyOff,float yaw,float pitch,
                                          RandomGenerator random) {
        if(!enabled||selectedEntity||entityAction||rotationPending||!safelyOff)return Optional.empty();
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid current view");
        return Optional.of(new Target(yaw+random.nextInt(360)-180,
                Math.max(-90,Math.min(90,pitch+random.nextInt(25)-10))));
    }
}
