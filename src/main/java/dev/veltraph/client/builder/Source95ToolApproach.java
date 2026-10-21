package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Bounded standing frontier for the explicitly bound source tool face. Never supplies output geometry. */
public final class Source95ToolApproach {
    private Source95ToolApproach() {}
    public record Stance(BlockPos node,Vec3 feet) {}
    public static Optional<Stance> closest(List<Stance> candidates,Vec3 actual) {
        if(actual==null||!Double.isFinite(actual.x())||!Double.isFinite(actual.y())||!Double.isFinite(actual.z()))throw new IllegalArgumentException("Actual player position required");
        return candidates.stream().min(Comparator.comparingDouble((Stance s)->s.feet().horizontalDistanceTo(actual)).thenComparingDouble(s->s.feet().distanceTo(actual)));
    }
    public interface Terrain {
        /** Loaded real support plus standing body clearance; an unsupported goal returns empty. */
        Optional<Vec3> supportedFeet(BlockPos node);
        /** Native ray must hit this exact anchor and required cardinal face, with no intervening block. */
        boolean requiredFace(Vec3 eye,Vec3 aim,BlockPos anchor,BlockPos direction);
    }
    public static List<Stance> candidates(BlockPos anchor,BlockPos direction,double eyeHeight,Terrain terrain) {
        if(direction==null||direction.y()!=0||Math.abs(direction.x())+Math.abs(direction.z())!=1
                ||!Double.isFinite(eyeHeight)||eyeHeight<=0||eyeHeight>4)throw new IllegalArgumentException("Horizontal tool face and actual eye height required");
        var aim=new Vec3(anchor.x()+.5+direction.x()*.499,anchor.y()+.5,anchor.z()+.5+direction.z()*.499);
        var out=new ArrayList<Stance>();
        // These are player feet nodes. Source anchor/down is a target block, not feet+1 by default.
        for(int distance:new int[]{2,3,1,4})for(int lateral:new int[]{0,-1,1})for(int rise:new int[]{0,-1,1}) {
            var node=anchor.add(direction.x()*distance+direction.z()*lateral,rise,direction.z()*distance-direction.x()*lateral);
            if(node.x()<-240||node.x()>239||node.z()<-240||node.z()>239||node.y()<0||node.y()>255)continue;
            var supported=terrain.supportedFeet(node);if(supported.isEmpty())continue;var feet=supported.get();
            if(!Double.isFinite(feet.x())||!Double.isFinite(feet.y())||!Double.isFinite(feet.z())||Math.abs(feet.x()-(node.x()+.5))>.001||Math.abs(feet.z()-(node.z()+.5))>.001||Math.abs(feet.y()-node.y())>1.001)continue;
            var eye=new Vec3(feet.x(),feet.y()+eyeHeight,feet.z());
            if(eye.distanceTo(aim)>4.5||!terrain.requiredFace(eye,aim,anchor,direction))continue;
            out.add(new Stance(node,feet));
        }
        return List.copyOf(out);
    }
}
