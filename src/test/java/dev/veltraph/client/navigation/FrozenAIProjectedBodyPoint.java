package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Exact private operand projection only; not SDK/native query or admission. */
final class FrozenAIProjectedBodyPoint {
    static Vec3 project(Vec3 p,double height){return new Vec3(p.x(),height,p.z());}
}
