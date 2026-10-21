package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
/** Pure complete standing body bounds, including its exact bottom strip. No native authority. */
public final class GroundStationaryBodyEnvelope {
    private GroundStationaryBodyEnvelope() {}
    public static Optional<GroundLandingBodyEnvelope.Bounds> bounds(Vec3 origin,double width,double height,double padding){
        if(!GroundLocomotionMotion.finite(origin)||!Double.isFinite(width)||width<.01D||width>4D
                ||!Double.isFinite(height)||height<.01D||height>4D||!Double.isFinite(padding)||padding<0D||padding>.025D)return Optional.empty();
        double radius=width/2D;var b=new GroundLandingBodyEnvelope.Bounds(origin.x()-radius-padding,origin.y(),origin.z()-radius-padding,
                origin.x()+radius+padding,origin.y()+height,origin.z()+radius+padding);
        return Double.isFinite(b.minX())&&Double.isFinite(b.minY())&&Double.isFinite(b.minZ())
                &&Double.isFinite(b.maxX())&&Double.isFinite(b.maxY())&&Double.isFinite(b.maxZ())?Optional.of(b):Optional.empty();
    }
}
