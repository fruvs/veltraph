package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Exact private operand projection only; not SDK/native query or admission. */
final class ActualAIProjectedBodyPoint {
    static Vec3 project(Vec3 p,double height){return Double.doubleToRawLongBits(p.y())==Double.doubleToRawLongBits(height)?p:new Vec3(p.x(),height,p.z());}
}
