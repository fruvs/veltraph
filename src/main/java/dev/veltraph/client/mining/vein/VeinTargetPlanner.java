package dev.veltraph.client.mining.vein;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Bounded ranking of independently observed standing poses and exact ray-visible aim points. */
public final class VeinTargetPlanner {
    public static final int MAX_OPTIONS = 2048;
    public record Option(BlockPos cell, String state, Vec3 standing, Vec3 eye, Vec3 aim,
            boolean loaded, boolean supported, boolean bodyClear, boolean safeApproach,
            boolean exactRayHit, double breakEstimateMillis, long observedAtMillis) {}
    public record Weights(double breaking, double turning, double walking) {
        public Weights {
            if (!finiteNonnegative(breaking) || !finiteNonnegative(turning) || !finiteNonnegative(walking)
                    || breaking + turning + walking == 0) throw new IllegalArgumentException("Invalid weights");
        }
    }
    public record Choice(Option option, MiningMaterial material, double cost) {}
    private VeinTargetPlanner() {}
    public static Optional<Choice> choose(List<Option> options, Map<MiningMaterial,Integer> priorities,
            Vec3 player, float yaw, float pitch, double reach, long now, long maxAge, Weights weights) {
        Objects.requireNonNull(options); Objects.requireNonNull(priorities); Objects.requireNonNull(weights);
        if (options.size() > MAX_OPTIONS) throw new IllegalArgumentException("Bounded scan exceeded");
        if (!finite(player) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !Double.isFinite(reach) || reach <= 0 || now < 0 || maxAge < 0) return Optional.empty();
        Choice best = null;
        // No visited-cell suppression: a failed pose must not hide a later valid vantage.
        for (Option option : options) {
            if (option == null || option.cell == null || option.state == null || !option.loaded
                    || !option.supported || !option.bodyClear || !option.safeApproach || !option.exactRayHit
                    || !finite(option.standing) || !finite(option.eye) || !finite(option.aim)
                    || !finiteNonnegative(option.breakEstimateMillis) || option.observedAtMillis < 0
                    || option.observedAtMillis > now || now - option.observedAtMillis > maxAge
                    || option.eye.distanceTo(option.aim) > reach) continue;
            var material = MiningMaterial.classify(option.state);
            if (material.isEmpty()) continue;
            int priority = priorities.getOrDefault(material.get(), 0);
            if (priority <= 0) continue;
            double dx = option.aim.x() - option.eye.x(), dy = option.aim.y() - option.eye.y();
            double dz = option.aim.z() - option.eye.z();
            double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double targetPitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            double turn = Math.hypot(wrap(targetYaw - yaw), targetPitch - pitch);
            double cost = (weights.breaking * option.breakEstimateMillis + weights.turning * turn
                    + weights.walking * player.distanceTo(option.standing)) / priority;
            if (!Double.isFinite(cost)) continue;
            Choice choice = new Choice(option, material.get(), cost);
            if (best == null || compare(choice,best) < 0) best = choice;
        }
        return Optional.ofNullable(best);
    }
    private static int compare(Choice a, Choice b) {
        int result = Double.compare(a.cost,b.cost);
        if (result != 0) return result;
        result = Integer.compare(a.option.cell.x(),b.option.cell.x()); if(result != 0)return result;
        result = Integer.compare(a.option.cell.y(),b.option.cell.y()); if(result != 0)return result;
        return Integer.compare(a.option.cell.z(),b.option.cell.z());
    }
    private static double wrap(double degrees) { return degrees - 360 * Math.floor((degrees + 180) / 360); }
    private static boolean finiteNonnegative(double value) { return Double.isFinite(value) && value >= 0; }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }
}
