package dev.veltraph.client.combat.ghost;

import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;

/** Source ranking metric is first target hit sample index, never nearby-entity count. */
public final class GhostSourceLookRayIndex {
    private GhostSourceLookRayIndex() {}
    public static int sampleIndex(Point actualEye,Point actualLookAngle,Box observedDeflatedTarget,Point targetBlockCenter){
        Objects.requireNonNull(actualEye);Objects.requireNonNull(actualLookAngle);Objects.requireNonNull(observedDeflatedTarget);Objects.requireNonNull(targetBlockCenter);
        // Original computes this helper first, even though its result is unused in the fixed21 loop.
        int limit=(int)Math.ceil(actualEye.distance(targetBlockCenter)+3);
        if(limit>1000)throw new IllegalArgumentException("Source ray limit exceeds 1000");
        Point step=new Point(actualLookAngle.x()/2,actualLookAngle.y()/2,actualLookAngle.z()/2);
        for(int index=0;index<21;index++)if(observedDeflatedTarget.contains(actualEye.add(step,index),0))return index;
        return 0;
    }
    public static double score(double nativeSquaredDistance,int targetHitSampleIndex){
        if(!Double.isFinite(nativeSquaredDistance)||nativeSquaredDistance<0||targetHitSampleIndex<0||targetHitSampleIndex>20)
            throw new IllegalArgumentException("Observed source distance and sample index required");
        return nativeSquaredDistance+(targetHitSampleIndex>3?targetHitSampleIndex*2.0:targetHitSampleIndex);
    }
}
