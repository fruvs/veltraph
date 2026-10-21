package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;

/** Explicit configured-plot transport; selection and helper work remain owned by the caller. */
public interface AssignedPlotTravelPort {
    record Target(int plotId,Vec3 spawn,float yaw,float pitch) {}
    boolean start(Target target,GameArea area);
    AssignedPlotTravelRuntime.State tick(Target target,GameArea area);
    boolean arrived(Target target,GameArea area);
    boolean consumeExpectedTeleport(Vec3 position,long now);
    void onServerMessage(String message);
    void cancel();
    String status();
}
