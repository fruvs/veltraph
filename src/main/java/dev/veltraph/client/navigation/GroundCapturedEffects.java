package dev.veltraph.client.navigation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Exact captured native effect families. Six affect combat/mining/health/rendering; Jump Boost
 * affects jump impulse and safe-fall distance. Unknown movement effects are never exempted. */
public final class GroundCapturedEffects {
    private GroundCapturedEffects() {}
    public record Effect(String id, int amplifier, boolean ambient, boolean visible, boolean icon) {}
    private static final Map<String,Integer> CAPTURED = Map.of(
            "minecraft:haste", 0, "minecraft:mining_fatigue", 255,
            "minecraft:strength", 7, "minecraft:night_vision", 0,
            "minecraft:regeneration", 8, "minecraft:absorption", 0, "minecraft:jump_boost", 5);
    public static boolean supported(List<Effect> effects, int jumpAmplifier) {
        if (effects == null || effects.size() > CAPTURED.size()) return false;
        // Exact bootstrap final ListN class identity; foreign/nonempty loops remain original.
        // This is the same empty-loop result, not a retained validation or native custody.
        if (effects.getClass() == List.<Effect>of().getClass() && effects.isEmpty())
            return jumpAmplifier == -1;
        var ids = new HashSet<String>(); int jump = -1;
        for (var e : effects) {
            if (e == null || e.id() == null || !ids.add(e.id())
                    || !CAPTURED.containsKey(e.id()) || CAPTURED.get(e.id()) != e.amplifier()) return false;
            if (e.id().equals("minecraft:jump_boost")) jump = e.amplifier();
        }
        return jump == jumpAmplifier;
    }
    /** Duration ticking is a native clock observation, not an effect replacement/refresh permit.
     * Native identity/revision and the full effect flags/amplifier must also remain exact. */
    public static boolean durationCurrent(int initialTick, int lastTick, int nowTick,
            int initialDuration, int currentDuration) {
        if (initialTick < 0 || lastTick < initialTick || nowTick < lastTick) return false;
        if (initialDuration == -1) return currentDuration == -1;
        if (initialDuration < 1 || currentDuration < 1 || currentDuration > initialDuration) return false;
        long elapsed = (long)nowTick - initialTick;
        return currentDuration >= Math.max(1L, (long)initialDuration - elapsed - 1L);
    }
}
