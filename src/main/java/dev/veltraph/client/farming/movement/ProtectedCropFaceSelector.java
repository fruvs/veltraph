package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Qualified forward exception only for native-proved protected immature wheat occlusion. */
public final class ProtectedCropFaceSelector {
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    private ProtectedCropFaceSelector() {}

    public static Optional<Vec3> select(Vec3 eye,double reach,boolean targetMature,
            boolean primaryHitProtectedImmatureSameBed,List<Box> nativeOutlineBoxes,
            Predicate<Vec3> nativeTargetVisible) {
        if(eye==null||!Double.isFinite(reach)||reach<=0||reach>64||!targetMature
                ||!primaryHitProtectedImmatureSameBed)return Optional.empty();
        int checked=0;
        for(var box:nativeOutlineBoxes){
            if(++checked>16)break;
            if(!finite(box)||box.maxX<=box.minX||box.maxY<=box.minY||box.maxZ<=box.minZ)continue;
            double inset=Math.min(.01,(box.maxY-box.minY)/4);
            for(double y:new double[]{box.maxY-inset,(box.minY+box.maxY)/2}){
                var point=new Vec3((box.minX+box.maxX)/2,y,(box.minZ+box.maxZ)/2);
                if(eye.distanceTo(point)<=reach&&nativeTargetVisible.test(point))return Optional.of(point);
            }
        }
        return Optional.empty();
    }
    private static boolean finite(Box b){return Double.isFinite(b.minX)&&Double.isFinite(b.minY)
            &&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);}
}
