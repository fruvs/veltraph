package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
/** Complete fixed-standing-body movement union, independent of native axis ordering. */
public final class GroundLandingBodyEnvelope {
    private GroundLandingBodyEnvelope(){}
    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        public boolean intersects(Bounds other){return minX<other.maxX&&maxX>other.minX&&minY<other.maxY&&maxY>other.minY&&minZ<other.maxZ&&maxZ>other.minZ;}
    }
    public static Optional<Bounds> bounds(Vec3 from,Vec3 to,double width,double height){
        if(!GroundLocomotionMotion.finite(from)||!GroundLocomotionMotion.finite(to)||!Double.isFinite(width)||width<.01||width>4
                ||!Double.isFinite(height)||height<.01||height>4)return Optional.empty();
        double radius=width/2;var b=new Bounds(Math.min(from.x(),to.x())-radius,Math.min(from.y(),to.y())+.001,Math.min(from.z(),to.z())-radius,
                Math.max(from.x(),to.x())+radius,Math.max(from.y(),to.y())+height,Math.max(from.z(),to.z())+radius);
        if(!Double.isFinite(b.minX)||!Double.isFinite(b.minY)||!Double.isFinite(b.minZ)||!Double.isFinite(b.maxX)||!Double.isFinite(b.maxY)||!Double.isFinite(b.maxZ))return Optional.empty();
        return Optional.of(b);
    }
}
