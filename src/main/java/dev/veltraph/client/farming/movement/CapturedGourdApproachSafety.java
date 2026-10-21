package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Fresh full-footprint body/support checks; no edge-protection crouch or position mutation. */
public final class CapturedGourdApproachSafety {
    private CapturedGourdApproachSafety() {}

    public static boolean clear(WorldService world, Vec3 from, Vec3 goal, Direction direction) {
        if (world == null || from == null || goal == null || direction == null
                || !direction.isHorizontal()
                || !world.bodyClear(from, .3, 1.8)
                || !world.hasGroundSupport(from, .3)) {
            return false;
        }
        double signed = (goal.x() - from.x()) * direction.dx()
                + (goal.z() - from.z()) * direction.dz();
        if (signed < -.10) {
            return false;
        }
        double distance = from.horizontalDistanceTo(goal);
        if (!Double.isFinite(distance) || distance > 16 || Math.abs(from.y() - goal.y()) > .025) {
            return false;
        }
        int samples = Math.max(1, (int) Math.ceil(distance * 2));
        for (int i = 1; i <= samples; i++) {
            double fraction = (double) i / samples;
            var probe = new Vec3(from.x() + (goal.x() - from.x()) * fraction,
                    from.y(), from.z() + (goal.z() - from.z()) * fraction);
            if (!world.bodyClear(probe, .3, 1.8) || !world.hasGroundSupport(probe, .3)) {
                return false;
            }
        }
        return true;
    }
}
