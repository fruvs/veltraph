package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/** Decoded geometry gate: exact block/face, horizontal speed <= 0.2, rough vector and allowed Y. */
public final class EtherwarpGate {
    public record Observation(BlockPos hitBlock,Direction hitFace,double hitY,double horizontalSpeed,
                              boolean roughVector,boolean landingClear,double distance) {}
    private EtherwarpGate() {}
    public static String rejection(BlockPos target,Direction face,Observation in,double minY,double maxY) {
        if(in==null||in.hitBlock()==null) return "No object mouse over";
        if(!target.equals(in.hitBlock())||face!=in.hitFace()) return "No look block/facing";
        if(!Double.isFinite(in.horizontalSpeed())||in.horizontalSpeed()>.2) return "Moving or flicking";
        if(!in.roughVector()) return "No rough vector";
        if(!Double.isFinite(in.hitY())||!Double.isFinite(minY)||!Double.isFinite(maxY)||minY>maxY||in.hitY()<minY||in.hitY()>maxY)
            return "Not in allowed region";
        if(!in.landingClear()) return "Landing is obstructed or unloaded";
        if(!Double.isFinite(in.distance())||in.distance()<0||in.distance()>100) return "Target outside acquisition range";
        return "";
    }
}
